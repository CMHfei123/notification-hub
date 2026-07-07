import uuid
from datetime import datetime, timezone
from typing import AsyncGenerator

from sqlalchemy import String, Text, Boolean, DateTime, Integer, func, select
from sqlalchemy.ext.asyncio import AsyncAttrs, AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from config import DATABASE_URL


class Base(AsyncAttrs, DeclarativeBase):
    pass


# --- Models ---

class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    device_type: Mapped[str] = mapped_column(String(32), nullable=False, default="android")
    platform: Mapped[str] = mapped_column(String(32), nullable=False, default="android")
    platform_version: Mapped[str] = mapped_column(String(16), nullable=False, default="")
    api_token: Mapped[str] = mapped_column(String(512), nullable=False, default="")
    paired_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    last_seen: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)


class Notification(Base):
    __tablename__ = "notifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id: Mapped[str] = mapped_column(String(36), nullable=False, index=True)
    device_name: Mapped[str] = mapped_column(String(128), nullable=False, default="")
    app_package: Mapped[str] = mapped_column(String(256), nullable=False, default="")
    app_name: Mapped[str] = mapped_column(String(128), nullable=False, default="")
    title: Mapped[str] = mapped_column(Text, nullable=False, default="")
    content: Mapped[str] = mapped_column(Text, nullable=False, default="")
    notification_type: Mapped[str] = mapped_column(String(32), nullable=False, default="general")
    is_sms: Mapped[bool] = mapped_column(Boolean, default=False)
    verification_code: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    category: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


# --- Engine & Session ---

engine = create_async_engine(DATABASE_URL, echo=False)
async_session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def get_session() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_factory() as session:
        yield session


async def close_db():
    await engine.dispose()


__all__ = [
    "Base", "Device", "Notification",
    "engine", "async_session_factory", "init_db", "get_session", "close_db",
]
