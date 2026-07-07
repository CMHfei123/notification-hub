package com.navigationhub

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.navigationhub.model.PushNotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifListener"
        private val smsApps = setOf(
            "com.android.mms", "com.android.messaging", "com.google.android.apps.messaging",
            "com.android.systemui", "com.miui.mms", "com.miui.smsextra",
            "com.android.incallui", "com.android.dialer"
        )
        private val smsCategories = setOf("sms", "text", "mms", "notification", "message")

        fun isEnabled(context: Context): Boolean {
            val enabledListeners =
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
            return enabledListeners?.contains(context.packageName) == true
        }
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
        val appName = getAppName(appPackage)
        val category = notification.category ?: ""

        // Skip our own notifications
        if (appPackage == packageName) return

        val isSms = appPackage in smsApps || category == "sms" || category == "text"
        var verificationCode = ""

        // Extract verification code from content
        if (isSms || text.contains("code", ignoreCase = true) || text.contains("验证码")) {
            verificationCode = extractVerificationCode(text)
            if (verificationCode.isEmpty()) verificationCode = extractVerificationCode(title)
        }

        val request = PushNotificationRequest(
            title = title,
            content = content.ifEmpty { text },
            appPackage = appPackage,
            appName = appName,
            notificationType = mapCategory(category),
            isSms = isSms || verificationCode.isNotEmpty(),
            verificationCode = verificationCode,
            category = category
        )

        Log.d(TAG, "Notification: [$appName] $title${if (verificationCode.isNotEmpty()) " [CODE: $verificationCode]" else ""}")

        scope.launch {
            try {
                val app = application as NotificationHubApp
                app.apiClient.pushNotification(request)
            } catch (e: Exception) {
                Log.w(TAG, "Push failed: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) { packageName }
    }

    private fun mapCategory(category: String): String = when {
        category in smsCategories -> "message"
        category == "call" -> "call"
        category == "email" -> "email"
        category == "alarm" -> "reminder"
        category == "err" || category == "error" -> "alert"
        category == "promo" || category == "recommendation" -> "promo"
        category == "social" -> "social"
        category == "service" || category == "update" -> "update"
        else -> "general"
    }

    private fun extractVerificationCode(text: String): String {
        if (text.isEmpty()) return ""
        val specific = Regex("""(验证码|动态码|校验码|一次性密码)[：:是为\s]*(\d{4,8})""").find(text)
        if (specific != null) return specific.groupValues[2]
        val codes = Regex("""(?<!\d)(\d{4,8})(?!\d)""").findAll(text).map { it.value }.toList()
        return if (codes.isNotEmpty()) codes.maxByOrNull { it.length } ?: "" else ""
    }

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Notification listener disconnected")
    }
}
