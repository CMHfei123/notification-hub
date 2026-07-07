from datetime import datetime, timedelta, timezone
import bcrypt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt
from jwt import PyJWTError
import config

security = HTTPBearer(auto_error=False)

def create_device_token(device_id, device_name, secret):
    expire = datetime.now(timezone.utc) + timedelta(days=config.JWT_DEVICE_TOKEN_EXPIRE_DAYS)
    payload = {'sub': device_id, 'name': device_name, 'type': 'device', 'exp': expire, 'iat': datetime.now(timezone.utc)}
    return jwt.encode(payload, secret, algorithm=config.JWT_ALGORITHM)

def create_web_token(username, secret):
    expire = datetime.now(timezone.utc) + timedelta(hours=config.JWT_WEB_TOKEN_EXPIRE_HOURS)
    payload = {'sub': username, 'type': 'web', 'exp': expire, 'iat': datetime.now(timezone.utc)}
    return jwt.encode(payload, secret, algorithm=config.JWT_ALGORITHM)

def verify_token(token, secret):
    try:
        return jwt.decode(token, secret, algorithms=[config.JWT_ALGORITHM])
    except PyJWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail='Invalid or expired token')

def hash_password(password):
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def verify_password(password, hashed):
    return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))


def get_device_id_from_token(secret, credentials=None):
    from fastapi.security.http import HTTPAuthorizationCredentials
    from fastapi import Depends
    if credentials is None:
        raise HTTPException(status_code=401, detail='Missing authorization')
    payload = verify_token(credentials.credentials, secret)
    if payload.get('type') != 'device':
        raise HTTPException(status_code=403, detail='Invalid token type')
    return payload['sub']

async def get_current_web_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    from main import app
    secret = app.state.jwt_secret
    if credentials is None:
        raise HTTPException(status_code=401, detail='Missing authorization')
    payload = verify_token(credentials.credentials, secret)
    if payload.get('type') != 'web':
        raise HTTPException(status_code=403, detail='Invalid token type')
    return payload['sub']
