import json
import logging
from datetime import datetime, timezone

from fastapi import WebSocket

from auth import verify_token

logger = logging.getLogger("notification-hub.ws")


class WebSocketManager:
    """Manages WebSocket connections for real-time notification streaming."""

    def __init__(self):
        self._connections: dict[str, list[WebSocket]] = {}  # device_id -> [websockets]
        self._web_connections: list[WebSocket] = []          # web dashboard connections

    async def connect(self, websocket: WebSocket, token: str):
        """Accept a WebSocket connection and register it based on token type."""
        from main import app
        secret = app.state.jwt_secret

        try:
            payload = verify_token(token, secret)
        except Exception:
            await websocket.close(code=4001, reason="Invalid token")
            return

        await websocket.accept()

        token_type = payload.get("type", "")
        if token_type == "device":
            device_id = payload["sub"]
            if device_id not in self._connections:
                self._connections[device_id] = []
            self._connections[device_id].append(websocket)
            logger.info(f"Device WebSocket connected: {device_id}")
        elif token_type == "web":
            self._web_connections.append(websocket)
            logger.info(f"Web WebSocket connected (total: {len(self._web_connections)})")
        else:
            await websocket.close(code=4003, reason="Unknown token type")

    def disconnect(self, websocket: WebSocket):
        """Remove a WebSocket connection."""
        for device_id, conns in self._connections.items():
            if websocket in conns:
                conns.remove(websocket)
                if not conns:
                    del self._connections[device_id]
                logger.info(f"Device WebSocket disconnected: {device_id}")
                return
        if websocket in self._web_connections:
            self._web_connections.remove(websocket)
            logger.info(f"Web WebSocket disconnected (remaining: {len(self._web_connections)})")

    async def broadcast_notification(self, notification: dict):
        """Broadcast a notification to all connected web and device clients."""
        message = json.dumps({
            "type": "notification",
            "data": {
                "id": notification.get("id", ""),
                "device_id": notification.get("device_id", ""),
                "device_name": notification.get("device_name", ""),
                "app_package": notification.get("app_package", ""),
                "app_name": notification.get("app_name", ""),
                "title": notification.get("title", ""),
                "content": notification.get("content", ""),
                "notification_type": notification.get("notification_type", "general"),
                "is_sms": notification.get("is_sms", False),
                "verification_code": notification.get("verification_code", ""),
                "category": notification.get("category", ""),
                "created_at": notification.get("created_at", datetime.now(timezone.utc).isoformat()),
            },
        })
        disconnected = []
        for ws in self._web_connections:
            try:
                await ws.send_text(message)
            except Exception:
                disconnected.append(ws)
        for ws in disconnected:
            self.disconnect(ws)
        # Also broadcast to all connected device clients so phones can see each other's notifications
        dev_disconnected = []
        for dev_id, dev_conns in self._connections.items():
            for ws in dev_conns:
                try:
                    await ws.send_text(message)
                except Exception:
                    dev_disconnected.append((dev_id, ws))
        for dev_id, ws in dev_disconnected:
            if ws in self._connections.get(dev_id, []):
                self._connections[dev_id].remove(ws)
        logger.debug(f"Broadcasted to {len(self._web_connections)} web + {sum(len(c) for c in self._connections.values())} device clients")

    async def send_device_event(self, event_type: str, data: dict):
        """Send an event (e.g., device connected/disconnected) to web clients."""
        message = json.dumps({"type": event_type, "data": data})
        disconnected = []
        for ws in self._web_connections:
            try:
                await ws.send_text(message)
            except Exception:
                disconnected.append(ws)
        for ws in disconnected:
            self.disconnect(ws)

    async def close_all(self):
        """Close all WebSocket connections."""
        for conns in self._connections.values():
            for ws in conns:
                try:
                    await ws.close(code=1001, reason="Server shutting down")
                except Exception:
                    pass
        for ws in self._web_connections:
            try:
                await ws.close(code=1001, reason="Server shutting down")
            except Exception:
                pass
        self._connections.clear()
        self._web_connections.clear()
        logger.info("All WebSocket connections closed")


ws_manager = WebSocketManager()


__all__ = ["ws_manager", "WebSocketManager"]
