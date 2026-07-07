/**
 * Utility functions for NotificationHub frontend.
 */
const Utils = {
    timeAgo(isoString) {
        if (!isoString) return '';
        const date = new Date(isoString);
        const now = new Date();
        const diffMs = now - date;
        const diffSec = Math.floor(diffMs / 1000);
        const diffMin = Math.floor(diffSec / 60);
        const diffHour = Math.floor(diffMin / 60);
        const diffDay = Math.floor(diffHour / 24);
        if (diffSec < 10) return 'just now';
        if (diffSec < 60) return diffSec + 's ago';
        if (diffMin < 60) return diffMin + 'm ago';
        if (diffHour < 24) return diffHour + 'h ago';
        if (diffDay < 7) return diffDay + 'd ago';
        return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    },
    formatTime(isoString) {
        if (!isoString) return '';
        return new Date(isoString).toLocaleString('zh-CN', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit',
        });
    },
    getNotifIcon(type, isSms) {
        if (isSms) return '\uD83D\uDCAC';
        const icons = { call: '\uD83D\uDCDE', message: '\u2709\uFE0F', email: '\uD83D\uDCE7', reminder: '\u23F0', alert: '\uD83D\uDD14', update: '\uD83D\uDD04', social: '\uD83D\uDC65', promo: '\uD83C\uDFF7\uFE0F' };
        return icons[type] || '\uD83D\uDD14';
    },
    getDeviceIcon(deviceType) {
        const icons = { android: '\uD83D\uDCF1', ios: '\uD83D\uDCF1', harmonyos: '\uD83D\uDCF1', web: '\uD83D\uDDA5\uFE0F', windows: '\uD83D\uDCBB' };
        return icons[deviceType] || '\uD83D\uDCF1';
    },
    extractCode(text) {
        if (!text) return '';
        const specific = text.match(/(\u9A8C\u8BC1\u7801|\u52A8\u6001\u7801|\u6821\u9A8C\u7801|\u4E00\u6B21\u6027\u5BC6\u7801)[\uFF1A:\u662F\u4E3A\s]*(\d{4,8})/);
        if (specific) return specific[2];
        const codes = text.match(/(?<!\d)(\d{4,8})(?!\d)/g);
        if (codes) return codes.reduce((a, b) => a.length >= b.length ? a : b);
        return '';
    },
    escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    },
};
window.Utils = Utils;
