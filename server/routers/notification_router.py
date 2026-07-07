import logging
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status

from auth import get_device_id_from_token, get_current_web_user
from schemas import (
    NotificationPushRequest, NotificationResponse,
    NotificationListResponse, NotificationDeleteResponse,
)
from services.notification_service import (
    store_notification, get_notifications, delete_notification,
)

logger = logging.getLogger("notification-hub.notification")
router = APIRouter()


@router.post("/push", response_model=NotificationResponse)
async def push_notification(
    req: NotificationPushRequest,
    request: Request,
):
    """Push a notification from a device."""
    from main import app
    secret = app.state.jwt_secret

    # Extract device ID from Authorization header
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing or invalid Authorization header")
    token = auth_header[7:]

    from auth import verify_token
    payload = verify_token(token, secret)
    device_id = payload.get("sub", "")

    result = await store_notification(
        device_id=device_id,
        title=req.title,
        content=req.content,
        app_package=req.app_package,
        app_name=req.app_name,
        notification_type=req.notification_type,
        is_sms=req.is_sms,
        verification_code=req.verification_code,
        category=req.category,
    )

    return NotificationResponse(**result)


@router.get("", response_model=NotificationListResponse)
async def list_notifications(
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    device_id: Optional[str] = Query(None),
    is_sms: Optional[bool] = Query(None),
    notification_type: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    _=Depends(get_current_web_user),
):
    """List notifications with optional filters."""
    items, total = await get_notifications(
        page=page,
        page_size=page_size,
        device_id=device_id,
        is_sms=is_sms,
        notification_type=notification_type,
        search=search,
    )
    return NotificationListResponse(
        notifications=[NotificationResponse(**item) for item in items],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.get("/sms", response_model=NotificationListResponse)
async def list_sms(
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    _=Depends(get_current_web_user),
):
    """Get SMS notifications (verification codes)."""
    items, total = await get_notifications(
        page=page,
        page_size=page_size,
        is_sms=True,
    )
    return NotificationListResponse(
        notifications=[NotificationResponse(**item) for item in items],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.delete("/{notification_id}", response_model=NotificationDeleteResponse)
async def remove_notification(
    notification_id: str,
    _=Depends(get_current_web_user),
):
    """Delete a notification."""
    success = await delete_notification(notification_id)
    if not success:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Notification not found")
    return NotificationDeleteResponse(success=True)


__all__ = ["router"]
