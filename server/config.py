import os
import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent

# --- Server ---
HOST = os.getenv("NH_HOST", "0.0.0.0")
PORT = int(os.getenv("NH_PORT", "8856"))

# --- Database ---
DATABASE_DIR = BASE_DIR / "data"
DATABASE_DIR.mkdir(parents=True, exist_ok=True)
DATABASE_PATH = DATABASE_DIR / "notifications.db"
DATABASE_URL = f"sqlite+aiosqlite:///{DATABASE_PATH}"

# --- JWT ---
JWT_SECRET_KEY = os.getenv("NH_JWT_SECRET", "")
JWT_ALGORITHM = "HS256"
JWT_DEVICE_TOKEN_EXPIRE_DAYS = 365 * 10  # Device tokens: practically non-expiring
JWT_WEB_TOKEN_EXPIRE_HOURS = 24          # Web session: 24h

# --- SSL ---
SSL_DIR = BASE_DIR / "ssl"
SSL_DIR.mkdir(parents=True, exist_ok=True)
SSL_CERT = SSL_DIR / "cert.pem"
SSL_KEY = SSL_DIR / "key.pem"

# --- Web UI ---
WEB_USERNAME = os.getenv("NH_WEB_USERNAME", "admin")
WEB_PASSWORD_HASH = os.getenv("NH_WEB_PASSWORD_HASH", "")


def get_or_create_jwt_secret() -> str:
    """Load or generate a persistent JWT secret key."""
    secret_file = BASE_DIR / ".jwt_secret"
    if secret_file.exists():
        return secret_file.read_text().strip()
    import secrets
    secret = secrets.token_hex(32)
    secret_file.write_text(secret)
    return secret


__all__ = [
    "HOST", "PORT", "DATABASE_URL", "DATABASE_PATH",
    "JWT_SECRET_KEY", "JWT_ALGORITHM",
    "JWT_DEVICE_TOKEN_EXPIRE_DAYS", "JWT_WEB_TOKEN_EXPIRE_HOURS",
    "SSL_DIR", "SSL_CERT", "SSL_KEY",
    "WEB_USERNAME", "WEB_PASSWORD_HASH",
    "get_or_create_jwt_secret",
]
