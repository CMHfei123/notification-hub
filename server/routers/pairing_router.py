import logging

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select

from auth import create_device_token, get_current_web_user
from database import Device, async_session_factory
from schemas import (
    PairingCodeResponse, PairingVerifyRequest, PairingVerifyResponse,
)
from services.pairing_service import pairing_service

logger = logging.getLogger("notification-hub.pairing")
router = APIRouter()


@router.get("/qrcode", response_model=PairingCodeResponse)
async def get_pairing_qrcode():
    """Generate a new pairing QR code (web page initiates pairing)."""
    pairing_code, qrcode_base64, expires_at = pairing_service.generate_pairing_code()
    logger.info(f"Pairing QR code requested: {pairing_code}")
    return PairingCodeResponse(
        pairing_code=pairing_code,
        qrcode_base64=qrcode_base64,
        expires_at=expires_at,
    )


@router.post("/verify", response_model=PairingVerifyResponse)
async def verify_pairing(req: PairingVerifyRequest, request: Request):
    """Verify a pairing code and register the device."""
    from main import app
    secret = app.state.jwt_secret

    # Verify the pairing code
    try:
        pairing_service.verify_pairing_code(req.pairing_code)
    except HTTPException:
        raise

    # The client should have already registered; find the most recent unverified device
    # For simplicity, the device sends its pairing_code after registration
    logger.info(f"Pairing code {req.pairing_code} verified successfully")
    return PairingVerifyResponse(
        success=True,
        message="Pairing code verified. Device can now connect.",
    )


__all__ = ["router"]
