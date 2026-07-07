class ApiClient {
    constructor(baseUrl) {
        this.baseUrl = baseUrl || '';
        this.token = localStorage.getItem('nh_web_token') || '';
        this.onUnauthorized = null;
    }
    setToken(token) {
        this.token = token;
        if (token) {
            localStorage.setItem('nh_web_token', token);
        } else {
            localStorage.removeItem('nh_web_token');
        }
    }
    getHeaders() {
        const h = { 'Content-Type': 'application/json' };
        if (this.token) h['Authorization'] = 'Bearer ' + this.token;
        return h;
    }
    async request(method, path, body) {
        const url = this.baseUrl + path;
        const opts = { method, headers: this.getHeaders() };
        if (body) opts.body = JSON.stringify(body);
        try {
            const resp = await fetch(url, opts);
            if (resp.status === 401 && this.onUnauthorized) this.onUnauthorized();
            if (!resp.ok) {
                const err = await resp.json().catch(() => ({ detail: 'Request failed' }));
                throw new Error(err.detail || 'Request failed (status ' + resp.status + ')');
            }
            return await resp.json();
        } catch (err) {
            if (err.name === 'TypeError' && err.message.includes('fetch')) {
                throw new Error('Cannot connect to server. Is it running?');
            }
            throw err;
        }
    }
    async login(username, password) {
        const r = await this.request('POST', '/api/auth/login', { username, password });
        this.setToken(r.access_token);
        return r;
    }
    async getPairingQRCode() { return this.request('GET', '/api/pairing/qrcode'); }
    async verifyPairing(code) { return this.request('POST', '/api/pairing/verify', { pairing_code: code }); }
    async getDevices() { return this.request('GET', '/api/devices'); }
    async deleteDevice(id) { return this.request('DELETE', '/api/devices/' + encodeURIComponent(id)); }
    async getNotifications(params) {
        const q = new URLSearchParams();
        if (params) for (const [k, v] of Object.entries(params)) { if (v !== null && v !== undefined && v !== '') q.set(k, v); }
        const qs = q.toString();
        return this.request('GET', '/api/notifications' + (qs ? '?' + qs : ''));
    }
    async getSMSNotifications(page) { return this.request('GET', '/api/notifications/sms?page=' + (page || 1) + '&page_size=50'); }
    async deleteNotification(id) { return this.request('DELETE', '/api/notifications/' + encodeURIComponent(id)); }
    async getStatus() { return this.request('GET', '/api/system/status'); }
}
window.api = new ApiClient('');
window.api.onUnauthorized = () => { window.api.setToken(''); location.reload(); };
