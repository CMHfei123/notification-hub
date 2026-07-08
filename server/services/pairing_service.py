import asyncio
import io
import json
import logging
import uuid
from datetime import datetime, timedelta, timezone

import qrcode
from fastapi import HTTPException, status

logger = logging.getLogger("notification-hub.pairing")


class PairingService:
    """Manages device pairing codes and verification."""

    def __init__(self):
        self._pending_codes: dict[str, dict] = {}
        self._cleanup_task: asyncio.Task | None = None  # pairing_code -> {expires_at, device_id?, paired}

    def generate_pairing_code(self) -> tuple[str, str, datetime]:
        """
        Generate a 6-character pairing code, QR code data (base64), and expiration.
        Returns (pairing_code, qrcode_base64, expires_at).
        """
        import base64

        pairing_code = uuid.uuid4().hex[:8].upper()
        expires_at = datetime.now(timezone.utc) + timedelta(minutes=5)

        # QR content: JSON with pairing code and server info
        qr_data = json.dumps({
            "type": "notification_hub_pairing",
            "code": pairing_code,
            "version": 1,
        })

        # Generate QR code image
        qr = qrcode.QRCode(box_size=10, border=2)
        qr.add_data(qr_data)
        qr.make(fit=True)
        img = qr.make_image(fill_color="black", back_color="white")

        buf = io.BytesIO()
        img.save(buf, format="PNG")
        qrcode_base64 = base64.b64encode(buf.getvalue()).decode("utf-8")

        # Store pending code
        self._pending_codes[pairing_code] = {
            "expires_at": expires_at,
            "used": False,
        }

        logger.info(f"Pairing code generated: {pairing_code}, expires at {expires_at}")
        return pairing_code, qrcode_base64, expires_at

    def verify_pairing_code(self, code: str) -> bool:
        """
        Verify and mark a pairing code as used.
        Returns True if valid, raises HTTPException otherwise.
        """
        now = datetime.now(timezone.utc)

        if code not in self._pending_codes:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing code not found")

        entry = self._pending_codes[code]

        if entry["used"]:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Pairing code already used")

        if now > entry["expires_at"]:
            del self._pending_codes[code]
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Pairing code expired")

        entry["used"] = True
        logger.info(f"Pairing code verified: {code}")
        return True

    def remove_expired_codes(self):
        """Clean up expired pairing codes."""
        now = datetime.now(timezone.utc)
        expired = [code for code, entry in self._pending_codes.items() if now > entry["expires_at"]]
        for code in expired:
            del self._pending_codes[code]
        if expired:
            logger.debug(f"Removed {len(expired)} expired pairing codes")



    async def _cleanup_loop(self):
        while True:
            try:
                await asyncio.sleep(60)
                self.remove_expired_codes()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.warning(f"Pairing cleanup error: {e}")

    def start_cleanup(self) -> None:
        if self._cleanup_task is None or self._cleanup_task.done():
            self._cleanup_task = asyncio.create_task(self._cleanup_loop())

    async def stop_cleanup(self) -> None:
        if self._cleanup_task and not self._cleanup_task.done():
            self._cleanup_task.cancel()
            try: await self._cleanup_task
            except asyncio.CancelledError: pass
            self._cleanup_task = None

pairing_service = PairingService()


__all__ = ["pairing_service", "PairingService"]
