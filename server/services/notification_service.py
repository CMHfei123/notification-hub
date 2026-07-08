import hashlib
import logging
import re
import time
import uuid
from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import select, func, delete as sa_delete, desc

from database import Device, Notification, async_session_factory

logger = logging.getLogger("notification-hub.notification")
_VERIFICATION_CODE_PATTERN = re.compile(r"(?<!\d)(\d{4,8})(?!\d)")
_dedup_cache: dict = {}
DEDUP_WINDOW_SECONDS = 30
MAX_DEDUP_CACHE_SIZE = 500

def _compute_notification_hash(device_id: str, title: str, content: str, app_package: str) -> str:
    return hashlib.sha256(f"{device_id}|{app_package}|{title}|{content}".encode()).hexdigest()


# Regex for Chinese SMS verification codes (4-8 digit codes)
VERIFICATION_CODE_PATTERN = re.compile(r"(?<!\d)(\d{4,8})(?!\d)")
# Specific patterns: "楠岃瘉鐮?, "楠岃瘉鐮佹槸", "鍔ㄦ€佺爜"
SPECIFIC_PATTERN = re.compile(r"(楠岃瘉鐮亅鍔ㄦ€佺爜|鏍￠獙鐮亅涓€娆℃€у瘑鐮?[锛?鏄负\s]*(\d{4,8})")


def extract_verification_code(text: str) -> str:
    """Extract verification code from notification text content."""
    if not text:
        return ""
    # First try specific patterns
    match = SPECIFIC_PATTERN.search(text)
    if match:
        return match.group(2)
    # Fallback: look for isolated 4-8 digit codes
    matches = VERIFICATION_CODE_PATTERN.findall(text)
    if matches:
        # Return the longest code (most likely a real verification code)
        return max(matches, key=len)
    return ""


async def store_notification(
    device_id: str,
    title: str,
    content: str,
    app_package: str = "",
    app_name: str = "",
    notification_type: str = "general",
    is_sms: bool = False,
    verification_code: str = "",
    category: str = "",
) -> dict:
    """Store a notification in the database and return the stored record."""
    # Auto-extract verification code if not provided
    if not verification_code and (is_sms or "楠岃瘉鐮? in content or "code" in content.lower()):
        extracted = extract_verification_code(content)
        if not extracted:
            extracted = extract_verification_code(title)
        verification_code = extracted

    # Get device name
    device_name = ""
    async with async_session_factory() as session:
        result = await session.execute(select(Device).where(Device.id == device_id))
        device = result.scalar_one_or_none()
        if device:
            device_name = device.name
            # Update last_seen
            device.last_seen = datetime.now(timezone.utc)
            await session.commit()

    notif_id = str(uuid.uuid4())
    created_at = datetime.now(timezone.utc)

    async with async_session_factory() as session:
        notif = Notification(
            id=notif_id,
            device_id=device_id,
            device_name=device_name,
            app_package=app_package,
            app_name=app_name,
            title=title,
            content=content,
            notification_type=notification_type,
            is_sms=is_sms,
            verification_code=verification_code,
            category=category,
            created_at=created_at,
        )
        session.add(notif)
        await session.commit()

    result = {
        "id": notif_id,
        "device_id": device_id,
        "device_name": device_name,
        "app_package": app_package,
        "app_name": app_name,
        "title": title,
        "content": content,
        "notification_type": notification_type,
        "is_sms": is_sms,
        "verification_code": verification_code,
        "category": category,
        "created_at": created_at.isoformat(),
    }

    # Broadcast via WebSocket
    from services.websocket_manager import ws_manager
    try:
        await ws_manager.broadcast_notification(result)
    except Exception as e:
        logger.warning(f"Failed to broadcast notification: {e}")

    logger.info(f"Notification stored: {notif_id} (device={device_name}, type={notification_type}, sms={is_sms})")
    return result


async def get_notifications(
    page: int = 1,
    page_size: int = 50,
    device_id: Optional[str] = None,
    is_sms: Optional[bool] = None,
    notification_type: Optional[str] = None,
    search: Optional[str] = None,
) -> tuple[list[dict], int]:
    """Get paginated notifications with optional filters."""
    async with async_session_factory() as session:
        query = select(Notification)
        count_query = select(func.count(Notification.id))

        if device_id:
            query = query.where(Notification.device_id == device_id)
            count_query = count_query.where(Notification.device_id == device_id)
        if is_sms is not None:
            query = query.where(Notification.is_sms == is_sms)
            count_query = count_query.where(Notification.is_sms == is_sms)
        if notification_type:
            query = query.where(Notification.notification_type == notification_type)
            count_query = count_query.where(Notification.notification_type == notification_type)
        if search:
            like_pattern = f"%{search}%"
            query = query.where(
                (Notification.title.ilike(like_pattern)) | (Notification.content.ilike(like_pattern))
            )
            count_query = count_query.where(
                (Notification.title.ilike(like_pattern)) | (Notification.content.ilike(like_pattern))
            )

        # Get total count
        total_result = await session.execute(count_query)
        total = total_result.scalar() or 0

        # Get paginated results, newest first
        query = query.order_by(desc(Notification.created_at))
        query = query.offset((page - 1) * page_size).limit(page_size)

        result = await session.execute(query)
        notifications = result.scalars().all()

        return [
            {
                "id": n.id,
                "device_id": n.device_id,
                "device_name": n.device_name,
                "app_package": n.app_package,
                "app_name": n.app_name,
                "title": n.title,
                "content": n.content,
                "notification_type": n.notification_type,
                "is_sms": n.is_sms,
                "verification_code": n.verification_code,
                "category": n.category,
                "created_at": n.created_at.isoformat() if n.created_at else "",
            }
            for n in notifications
        ], total


async def delete_notification(notification_id: str) -> bool:
    """Delete a notification by ID."""
    async with async_session_factory() as session:
        result = await session.execute(
            sa_delete(Notification).where(Notification.id == notification_id)
        )
        await session.commit()
        deleted = result.rowcount > 0
        if deleted:
            logger.info(f"Notification deleted: {notification_id}")
        return deleted


__all__ = [
    "store_notification", "get_notifications", "delete_notification",
    "extract_verification_code",
]
