class NotificationWebSocket {
    constructor() {
        this.ws = null;
        this.reconnectTimer = null;
        this.isConnected = false;
        this.onNotification = null;
        this.onStatusChange = null;
        this._shouldReconnect = true;
    }

    connect() {
        if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return;
        const token = window.api ? window.api.token : localStorage.getItem('nh_web_token');
        if (!token) return;
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const url = protocol + '//' + location.host + '/ws/notifications?token=' + encodeURIComponent(token);
        try {
            this.ws = new WebSocket(url);
        } catch (e) {
            console.warn('WebSocket connection failed:', e);
            this._scheduleReconnect();
            return;
        }
        this.ws.onopen = () => {
            this.isConnected = true;
            if (this.onStatusChange) this.onStatusChange(true);
            if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
        };
        this.ws.onclose = () => {
            this.isConnected = false;
            if (this.onStatusChange) this.onStatusChange(false);
            this.ws = null;
            this._scheduleReconnect();
        };
        this.ws.onerror = () => {
            // onclose will fire after this
        };
        this.ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'notification' && this.onNotification) {
                    this.onNotification(data.data);
                } else if (data.type === 'device_event' && this.onStatusChange) {
                    // Trigger a refresh
                }
            } catch (e) {
                console.warn('WebSocket message parse error:', e);
            }
        };
    }

    disconnect() {
        this._shouldReconnect = false;
        if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
        if (this.ws) { try { this.ws.close(); } catch (e) {} this.ws = null; }
        this.isConnected = false;
    }

    _scheduleReconnect() {
        if (!this._shouldReconnect) return;
        if (this.reconnectTimer) return;
        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            if (this._shouldReconnect) this.connect();
        }, 3000);
    }
}
window.wsClient = new NotificationWebSocket();
