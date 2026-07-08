import logging
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select

from auth import get_current_web_user
from database import Device, async_session_factory
from schemas import DeviceResponse, DeviceListResponse

logger = logging.getLogger("notification-hub.device")
router = APIRouter()


@router.get("", response_model=DeviceListResponse)
async def list_devices(_=Depends(get_current_web_user)):
    """List all paired devices."""
    async with async_session_factory() as session:
        result = await session.execute(select(Device).order_by(Device.paired_at.desc()))
        devices = result.scalars().all()
        return DeviceListResponse(
            devices=[
                DeviceResponse(
                    id=d.id,
                    name=d.name,
                    device_type=d.device_type,
                    platform=d.platform,
                    platform_version=d.platform_version,
                    last_seen=d.last_seen,
                    paired_at=d.paired_at,
                    is_active=d.is_active,
                )
                for d in devices
            ]
        )


@router.delete("/{device_id}")
async def delete_device(device_id: str, _=Depends(get_current_web_user)):
    """Unpair/remove a device."""
    async with async_session_factory() as session:
        result = await session.execute(select(Device).where(Device.id == device_id))
        device = result.scalar_one_or_none()
        if not device:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
        await session.delete(device)
        await session.commit()
    logger.info(f"Device deleted: {device_id}")
    return {"success": True}


__all__ = ["router"]
