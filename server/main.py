import json, logging, os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from config import HOST, PORT, SSL_CERT, SSL_KEY, get_or_create_jwt_secret, WEB_USERNAME, WEB_PASSWORD_HASH
from database import init_db, close_db
from auth import hash_password

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger('notification-hub')

def ensure_ssl_certificates():
    if SSL_CERT.exists() and SSL_KEY.exists():
        logger.info('SSL certificates found')
        return
    logger.info('Generating self-signed SSL certificates...')
    from cryptography import x509
    from cryptography.hazmat.primitives import serialization, hashes
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.backends import default_backend
    from cryptography.x509.oid import NameOID
    import datetime as dt
    
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048, backend=default_backend())
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, 'CN'),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, 'NotificationHub'),
        x509.NameAttribute(NameOID.COMMON_NAME, 'NotificationHub Local'),
    ])
    cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(issuer)
        .public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(dt.datetime.now(dt.timezone.utc))
        .not_valid_after(dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        .sign(key, hashes.SHA256(), backend=default_backend()))
    SSL_CERT.write_bytes(cert.public_bytes(encoding=serialization.Encoding.PEM))
    SSL_KEY.write_bytes(key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption()))
    logger.info(f'SSL certificates generated')

@asynccontextmanager
async def lifespan(app):
    logger.info('Starting NotificationHub...')
    ensure_ssl_certificates()
    await init_db()
    app.state.jwt_secret = get_or_create_jwt_secret()
    if not WEB_PASSWORD_HASH:
        default_pw = os.getenv('NH_WEB_PASSWORD', 'admin123')
        pw_hash = hash_password(default_pw)
        app.state.web_password_hash = pw_hash
        app.state.web_username = WEB_USERNAME
        logger.info(f'Default password set: {WEB_USERNAME}/{default_pw}')
    else:
        app.state.web_password_hash = WEB_PASSWORD_HASH
        app.state.web_username = WEB_USERNAME
    from services.websocket_manager import ws_manager
    from services.pairing_service import pairing_service
    app.state.ws_manager = ws_manager
    pairing_service.start_cleanup()
    yield
    await pairing_service.stop_cleanup()
    await ws_manager.close_all()
    await close_db()

app = FastAPI(title='NotificationHub', version='1.0.0', lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=['*'], allow_credentials=True, allow_methods=['*'], allow_headers=['*'])

STATIC_DIR = Path(__file__).resolve().parent / 'static'
if STATIC_DIR.exists():
    app.mount('/static', StaticFiles(directory=str(STATIC_DIR)), name='static')

from routers.auth_router import router as auth_router
from routers.notification_router import router as notification_router
from routers.device_router import router as device_router
from routers.pairing_router import router as pairing_router

app.include_router(auth_router, prefix='/api/auth', tags=['Authentication'])
app.include_router(pairing_router, prefix='/api/pairing', tags=['Pairing'])
app.include_router(device_router, prefix='/api/devices', tags=['Devices'])
app.include_router(notification_router, prefix='/api/notifications', tags=['Notifications'])

@app.websocket('/ws/notifications')
async def notification_websocket(websocket: WebSocket):
    from services.websocket_manager import ws_manager
    token = websocket.query_params.get('token', '')
    if not token:
        await websocket.close(code=4001, reason='Missing token')
        return
    await ws_manager.connect(websocket, token)
    try:
        while True:
            data = await websocket.receive_text()
            if data == '__ping__':
                await websocket.send_text('__pong__')
    except (WebSocketDisconnect, Exception):
        ws_manager.disconnect(websocket)

@app.get('/api/system/status')
async def system_status():
    import datetime
    return {'status': 'running', 'version': '1.0.0', 'server_time': datetime.datetime.now().isoformat()}

@app.get('/')
async def root():
    html_path = STATIC_DIR / 'index.html'
    if html_path.exists():
        return HTMLResponse(html_path.read_text(encoding='utf-8'))
    return JSONResponse({'message': 'Frontend not found'})

def main():
    import uvicorn
    # Generate SSL certs BEFORE uvicorn starts (uvicorn needs cert files at startup)
    ensure_ssl_certificates()
    logger.info(f'Starting on https://{HOST}:{PORT}')
    uvicorn.run('main:app', host=HOST, port=PORT, ssl_certfile=str(SSL_CERT), ssl_keyfile=str(SSL_KEY), reload=False, log_level='info')

if __name__ == '__main__':
    main()
