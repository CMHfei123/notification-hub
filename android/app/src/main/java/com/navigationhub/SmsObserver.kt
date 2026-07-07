package com.navigationhub

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.navigationhub.model.PushNotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsObserver(context: Context, handler: Handler) : ContentObserver(handler) {
    companion object {
        private const val TAG = "SmsObserver"
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastProcessedId: Long = -1
    private val smsUri: Uri = Telephony.Sms.Inbox.CONTENT_URI

    private val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE_SENT
    )

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        readLatestSms()
    }

    private fun readLatestSms() {
        try {
            val cursor: Cursor? = appContext.contentResolver.query(
                smsUri, projection, null, null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID))
                    if (id == lastProcessedId) return
                    lastProcessedId = id

                    val address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""

                    if (body.isBlank()) return

                    val verificationCode = extractVerificationCode(body)

                    val request = PushNotificationRequest(
                        title = "SMS from $address",
                        content = body,
                        appPackage = "com.android.mms",
                        appName = "SMS",
                        notificationType = "message",
                        isSms = true,
                        verificationCode = verificationCode,
                        category = "sms"
                    )

                    scope.launch {
                        try {
                            val app = appContext as NotificationHubApp
                            app.apiClient.pushNotification(request)
                            Log.d(TAG, "SMS pushed: $address ${if (verificationCode.isNotEmpty()) "[CODE]" else ""}")
                        } catch (e: Exception) {
                            Log.w(TAG, "SMS push failed: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS permission not granted: ${e.message}")
        }
    }

    private fun extractVerificationCode(text: String): String {
        val specific = Regex("""(验证码|动态码|校验码|一次性密码)[：:是为\s]*(\d{4,8})""").find(text)
        if (specific != null) return specific.groupValues[2]
        val codes = Regex("""(?<!\d)(\d{4,8})(?!\d)""").findAll(text).map { it.value }.toList()
        return if (codes.isNotEmpty()) codes.maxByOrNull { it.length } ?: "" else ""
    }
}
