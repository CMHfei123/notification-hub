package com.navigationhub

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.navigationhub.model.PushNotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifListener"
        private val smsApps = setOf("com.android.mms", "com.android.messaging", "com.google.android.apps.messaging", "com.miui.mms", "com.miui.smsextra")
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE, "") ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT, "") ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT, "") ?: ""
        val content = listOfNotNull(text, subText).filter { it.isNotBlank() }.joinToString(" - ")
        val appPackage = sbn.packageName
        val appName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(appPackage, 0)).toString() } catch (e: Exception) { appPackage }
        val category = notification.category ?: ""

        if (appPackage == packageName) return

        val isSms = appPackage in smsApps || category == "sms" || category == "text"
        var verificationCode = ""
        if (isSms || text.contains("code", ignoreCase = true)) {
            val codeMatch = Regex("""(?<!\d)(\d{4,8})(?!\d)""").find(text)
            if (codeMatch != null) verificationCode = codeMatch.value
        }

        val request = PushNotificationRequest(
            title = title, content = content.ifEmpty { text },
            appPackage = appPackage, appName = appName,
            notificationType = "general", isSms = isSms || verificationCode.isNotEmpty(),
            verificationCode = verificationCode, category = category
        )

        scope.launch {
            try {
                val app = application as NotificationHubApp
                app.apiClient.pushNotification(request)
            } catch (e: Exception) {
                Log.w(TAG, "Push failed, queuing: " + e.message)
                try {
                    val app = application as NotificationHubApp
                    app.enqueueNotification(request)
                } catch (e2: Exception) {
                    Log.w(TAG, "Queue also failed: " + e2.message)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onListenerConnected() { Log.i(TAG, "Connected") }
    override fun onListenerDisconnected() { Log.w(TAG, "Disconnected") }
}
