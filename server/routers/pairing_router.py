import logging

from fastapi import APIRouter
from schemas import (
    PairingCodeResponse, PairingVerifyRequest, PairingVerifyResponse,
)
from services.pairing_service import pairing_service

logger = logging.getLogger("notification-hub.pairing")
router = APIRouter()


@router.get("/qrcode", response_model=PairingCodeResponse)
async def get_pairing_qrcode(_=Depends(get_current_web_user)):
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
    pairing_service.verify_pairing_code(req.pairing_code)
    logger.info(f"Pairing code {req.pairing_code} verified successfully")
    return PairingVerifyResponse(
        success=True,
        message="Pairing code verified. Device can now connect.",
    )


__all__ = ["router"]
