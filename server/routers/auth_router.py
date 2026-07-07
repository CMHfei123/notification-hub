import logging
from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select
from auth import create_device_token, create_web_token, verify_password
from database import Device, async_session_factory
from schemas import DeviceRegisterRequest, TokenResponse, WebLoginRequest, DeviceLoginRequest

logger = logging.getLogger('notification-hub.auth')
router = APIRouter()

@router.post('/register', response_model=TokenResponse)
async def register_device(req: DeviceRegisterRequest, request: Request):
    from main import app
    secret = app.state.jwt_secret
    async with async_session_factory() as session:
        device = Device(name=req.name, device_type=req.device_type, platform=req.platform, platform_version=req.platform_version)
        session.add(device)
        await session.flush()
        token = create_device_token(device.id, device.name, secret)
        device.api_token = token
        await session.commit()
    logger.info(f'Device registered: {device.id}')
    return TokenResponse(access_token=token, device_id=device.id)

@router.post('/login', response_model=TokenResponse)
async def web_login(req: WebLoginRequest, request: Request):
    from main import app
    secret = app.state.jwt_secret
    web_username = getattr(app.state, 'web_username', 'admin')
    web_pw_hash = getattr(app.state, 'web_password_hash', '')
    if req.username != web_username:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail='Invalid credentials')
    if not web_pw_hash:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail='Password not configured')
    if not verify_password(req.password, web_pw_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail='Invalid credentials')
    token = create_web_token(req.username, secret)
    return TokenResponse(access_token=token)

@router.post('/device-login', response_model=TokenResponse)
async def device_login(req: DeviceLoginRequest, request: Request):
    from main import app
    secret = app.state.jwt_secret
    async with async_session_factory() as session:
        result = await session.execute(select(Device).where(Device.id == req.device_id))
        device = result.scalar_one_or_none()
        if not device or device.api_token != req.api_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail='Invalid device credentials')
        new_token = create_device_token(device.id, device.name, secret)
        device.api_token = new_token
        await session.commit()
    return TokenResponse(access_token=new_token, device_id=device.id)
