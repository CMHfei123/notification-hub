const App = {
    currentView: 'all',
    notifications: [],
    devices: [],
    isLoading: false,
    hasMore: true,
    page: 1,
    lastCodeNotifId: null,

    async init() {
        const token = window.api.token;
        if (!token) { this.showLogin(); return; }
        try {
            await api.getStatus();
            this.showDashboard();
            this.connectWebSocket();
        } catch (e) {
            api.setToken('');
            this.showLogin();
        }
    },

    showLogin() {
        document.getElementById('login-screen').classList.remove('hidden');
        document.getElementById('dashboard').classList.add('hidden');
        document.getElementById('login-form').onsubmit = async (e) => {
            e.preventDefault();
            const btn = e.target.querySelector('button');
            btn.disabled = true;
            btn.textContent = 'Logging in...';
            const errEl = document.getElementById('login-error');
            errEl.classList.add('hidden');
            try {
                await api.login(
                    document.getElementById('login-username').value,
                    document.getElementById('login-password').value
                );
                this.showDashboard();
                this.connectWebSocket();
            } catch (err) {
                errEl.textContent = err.message;
                errEl.classList.remove('hidden');
                btn.disabled = false;
                btn.textContent = '登录';
            }
        };
    },

    showDashboard() {
        document.getElementById('login-screen').classList.add('hidden');
        document.getElementById('dashboard').classList.remove('hidden');
        this.loadData();
        this.bindEvents();
    },

    connectWebSocket() {
        wsClient.onNotification = (data) => {
            this.notifications.unshift(data);
            if (this.currentView === 'all' || (this.currentView === 'sms' && data.is_sms)) {
                this.renderNotificationItem(data, true);
            }
            this.updateStats();
            if (data.is_sms && data.verification_code) {
                this.showCodeToast(data);
            }
        };
        wsClient.onStatusChange = (connected) => {
            const dot = document.getElementById('ws-indicator');
            if (dot) {
                dot.className = 'ws-dot ' + (connected ? 'connected' : 'disconnected');
                dot.title = connected ? 'Real-time connected' : 'Reconnecting...';
            }
        };
        wsClient.connect();
    },

    async loadData() {
        this.isLoading = true;
        document.getElementById('notif-container').innerHTML = '<div class="loading-spinner"></div>';
        try {
            const [notifResp, deviceResp] = await Promise.all([
                api.getNotifications({ page: 1, page_size: 50 }),
                api.getDevices()
            ]);
            this.notifications = notifResp.notifications || [];
            this.devices = deviceResp.devices || [];
            this.hasMore = this.notifications.length < notifResp.total;
            this.page = 1;
            this.renderAll();
        } catch (err) {
            document.getElementById('notif-container').innerHTML =
                '<div class="empty-state"><div class="empty-icon">\u26A0\uFE0F</div><p>Failed to load: ' + Utils.escapeHtml(err.message) + '</p></div>';
        }
        this.isLoading = false;
    },

    renderAll() {
        this.renderStats();
        this.renderNotifications();
        this.renderDeviceList();
    },

    renderStats() {
        const total = this.notifications.length;
        const smsCount = this.notifications.filter(n => n.is_sms).length;
        const deviceCount = this.devices.length;
        document.getElementById('stat-total').textContent = total;
        document.getElementById('stat-sms').textContent = smsCount;
        document.getElementById('stat-devices').textContent = deviceCount;
    },

    renderNotifications() {
        const container = document.getElementById('notif-container');
        let list = this.notifications;
        if (this.currentView === 'sms') list = list.filter(n => n.is_sms);
        if (list.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-icon">\uD83D\uDCE2</div><p>No notifications yet. Pair a device to get started.</p></div>';
            return;
        }
        const frag = document.createDocumentFragment();
        for (const n of list) {
            frag.appendChild(this.createNotificationCard(n));
        }
        container.innerHTML = '';
        container.appendChild(frag);
    },

    createNotificationCard(n) {
        const card = document.createElement('div');
        card.className = 'notif-card' + (n.is_sms ? ' sms-card' : '');
        card.dataset.id = n.id;
        const code = n.verification_code || Utils.extractCode(n.content);
        const time = Utils.timeAgo(n.created_at);
        const icon = Utils.getNotifIcon(n.notification_type, n.is_sms);
        const deviceIcon = Utils.getDeviceIcon(n.device_type || 'android');
        card.innerHTML =
            '<div class="notif-header">' +
                '<span class="notif-icon">' + icon + '</span>' +
                '<span class="notif-device">' + Utils.escapeHtml(n.device_name || 'Unknown') + '</span>' +
                '<span class="notif-time">' + time + '</span>' +
                '<button class="notif-del" data-id="' + n.id + '" title="Delete">&times;</button>' +
            '</div>' +
            (n.app_name ? '<div class="notif-app">' + Utils.escapeHtml(n.app_name) + '</div>' : '') +
            (n.title ? '<div class="notif-title">' + Utils.escapeHtml(n.title) + '</div>' : '') +
            (n.content ? '<div class="notif-content">' + Utils.escapeHtml(n.content) + '</div>' : '') +
            (code ? '<div class="notif-code">\uD83D\uDD11 Verification Code: <strong>' + Utils.escapeHtml(code) + '</strong></div>' : '') +
            '<div class="notif-footer">' +
                '<span class="notif-type-badge ' + n.notification_type + '">' + n.notification_type + '</span>' +
                (n.is_sms ? '<span class="sms-badge">SMS</span>' : '') +
            '</div>';
        card.querySelector('.notif-del').addEventListener('click', async (e) => {
            e.stopPropagation();
            try {
                await api.deleteNotification(n.id);
                card.remove();
                this.notifications = this.notifications.filter(x => x.id !== n.id);
                this.updateStats();
            } catch (err) { console.warn('Delete failed:', err); }
        });
        return card;
    },

    renderNotificationItem(n, prepend) {
        const container = document.getElementById('notif-container');
        const card = this.createNotificationCard(n);
        if (prepend && container.firstChild && !container.firstChild.classList.contains('empty-state')) {
            container.insertBefore(card, container.firstChild);
        } else {
            container.prepend(card);
        }
        // Remove empty state if present
        const empty = container.querySelector('.empty-state');
        if (empty) empty.remove();
    },

    renderDeviceList() {
        const container = document.getElementById('device-list');
        if (!this.devices.length) {
            container.innerHTML = '<div class="empty-state small"><p>No devices paired yet.</p></div>';
            return;
        }
        container.innerHTML = this.devices.map(d => {
            const icon = Utils.getDeviceIcon(d.device_type);
            const lastSeen = d.last_seen ? Utils.timeAgo(d.last_seen) : 'Never';
            return '<div class="device-card">' +
                '<div class="device-icon">' + icon + '</div>' +
                '<div class="device-info">' +
                    '<div class="device-name">' + Utils.escapeHtml(d.name) + '</div>' +
                    '<div class="device-meta">' + Utils.escapeHtml(d.platform) + ' ' + Utils.escapeHtml(d.platform_version) + '</div>' +
                    '<div class="device-meta">Last seen: ' + lastSeen + '</div>' +
                '</div>' +
                '<span class="device-status ' + (d.is_active ? 'active' : 'inactive') + '"></span>' +
            '</div>';
        }).join('');
    },

    async updateStats() {
        const total = this.notifications.length;
        const smsCount = this.notifications.filter(n => n.is_sms).length;
        document.getElementById('stat-total').textContent = total;
        document.getElementById('stat-sms').textContent = smsCount;
    },

    showCodeToast(n) {
        const container = document.getElementById('code-toast-container');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = 'code-toast';
        toast.innerHTML =
            '<div class="code-toast-header">\uD83D\uDCAC New SMS Code</div>' +
            '<div class="code-toast-code">' + Utils.escapeHtml(n.verification_code) + '</div>' +
            '<div class="code-toast-body">' + Utils.escapeHtml(n.title || n.content || '').substring(0, 60) + '</div>';
        container.appendChild(toast);
        setTimeout(() => { toast.remove(); }, 6000);
    },

    async showPairingModal() {
        const overlay = document.getElementById('pairing-overlay');
        const qrImg = document.getElementById('pairing-qr-img');
        const codeEl = document.getElementById('pairing-code-display');
        const timerEl = document.getElementById('pairing-timer');
        qrImg.innerHTML = '<div class="loading-spinner"></div>';
        codeEl.textContent = 'Generating...';
        overlay.classList.remove('hidden');
        try {
            const resp = await api.getPairingQRCode();
            qrImg.innerHTML = '<img src="data:image/png;base64,' + resp.qrcode_base64 + '" alt="Pairing QR Code" style="width:200px;height:200px">';
            codeEl.textContent = 'Code: ' + resp.pairing_code;
            const expire = new Date(resp.expires_at);
            const updateTimer = () => {
                const left = Math.max(0, Math.round((expire - new Date()) / 1000));
                timerEl.textContent = 'Expires in ' + left + 's';
                if (left <= 0) { timerEl.textContent = 'Expired. Please regenerate.'; return; }
                setTimeout(updateTimer, 1000);
            };
            updateTimer();
        } catch (err) {
            qrImg.innerHTML = '<p style="color:var(--danger)">Failed: ' + Utils.escapeHtml(err.message) + '</p>';
        }
    },

    switchView(view) {
        this.currentView = view;
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.view === view));
        document.getElementById('notif-container').style.display = (view === 'devices') ? 'none' : '';
        document.getElementById('devices-section').style.display = (view === 'devices') ? '' : 'none';
        document.getElementById('sms-banner').style.display = (view === 'sms') ? '' : 'none';
        if (view !== 'devices') this.renderNotifications();
    },

    toggleTheme() {
        const current = document.documentElement.getAttribute('data-theme') || 'dark';
        const next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('nh_theme', next);
        document.getElementById('theme-toggle').textContent = next === 'dark' ? '\u2600\uFE0F' : '\uD83C\uDF19';
    },

    bindEvents() {
        // Theme toggle
        document.getElementById('theme-toggle').addEventListener('click', () => this.toggleTheme());
        // Tabs
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => this.switchView(btn.dataset.view));
        });
        // Pairing button
        document.getElementById('pairing-btn').addEventListener('click', () => this.showPairingModal());
        // Close pairing modal
        document.getElementById('pairing-close').addEventListener('click', () => {
            document.getElementById('pairing-overlay').classList.add('hidden');
        });
        // Logout
        document.getElementById('logout-btn').addEventListener('click', () => {
            api.setToken('');
            wsClient.disconnect();
            location.reload();
        });
        // Load more
        document.getElementById('load-more-btn').addEventListener('click', async () => {
            if (this.isLoading || !this.hasMore) return;
            this.page++;
            this.isLoading = true;
            try {
                const resp = await api.getNotifications({ page: this.page, page_size: 50 });
                const items = resp.notifications || [];
                this.notifications = this.notifications.concat(items);
                this.hasMore = items.length > 0;
                for (const n of items) this.renderNotificationItem(n, false);
                document.getElementById('load-more-btn').style.display = this.hasMore ? '' : 'none';
            } catch (e) { this.page--; }
            this.isLoading = false;
        });
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
