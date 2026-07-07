from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


# --- Auth ---

class DeviceRegisterRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=128, description="Device display name")
    device_type: str = Field(default="android", pattern="^(android|ios|harmonyos|web)$")
    platform: str = Field(default="android")
    platform_version: str = Field(default="")


class DeviceLoginRequest(BaseModel):
    device_id: str
    api_token: str


class WebLoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    device_id: Optional[str] = None


# --- Pairing ---

class PairingCodeResponse(BaseModel):
    pairing_code: str
    qrcode_base64: str
    expires_at: datetime


class PairingVerifyRequest(BaseModel):
    pairing_code: str


class PairingVerifyResponse(BaseModel):
    success: bool
    message: str
    device_id: Optional[str] = None
    api_token: Optional[str] = None


# --- Devices ---

class DeviceResponse(BaseModel):
    id: str
    name: str
    device_type: str
    platform: str
    platform_version: str
    last_seen: Optional[datetime] = None
    paired_at: Optional[datetime] = None
    is_active: bool = True


class DeviceListResponse(BaseModel):
    devices: list[DeviceResponse]


# --- Notifications ---

class NotificationPushRequest(BaseModel):
    title: str = Field(default="")
    content: str = Field(default="")
    app_package: str = Field(default="")
    app_name: str = Field(default="")
    notification_type: str = Field(default="general")
    is_sms: bool = Field(default=False)
    verification_code: str = Field(default="")
    category: str = Field(default="")


class NotificationResponse(BaseModel):
    id: str
    device_id: str
    device_name: str
    app_package: str
    app_name: str
    title: str
    content: str
    notification_type: str
    is_sms: bool
    verification_code: str
    category: str
    created_at: Optional[datetime] = None


class NotificationListResponse(BaseModel):
    notifications: list[NotificationResponse]
    total: int
    page: int
    page_size: int


class NotificationDeleteResponse(BaseModel):
    success: bool


# --- System ---

class SystemStatusResponse(BaseModel):
    status: str
    version: str
    server_time: str
    devices_online: int = 0
    total_notifications: int = 0
    uptime: str = "N/A"


__all__ = [
    "DeviceRegisterRequest", "DeviceLoginRequest", "WebLoginRequest", "TokenResponse",
    "PairingCodeResponse", "PairingVerifyRequest", "PairingVerifyResponse",
    "DeviceResponse", "DeviceListResponse",
    "NotificationPushRequest", "NotificationResponse", "NotificationListResponse", "NotificationDeleteResponse",
    "SystemStatusResponse",
]
