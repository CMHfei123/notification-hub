package com.navigationhub

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.navigationhub.model.PushNotificationRequest
import com.navigationhub.network.ApiClient
import com.navigationhub.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NotificationHubApp : Application() {
    companion object {
        private const val TAG = "NHApp"
        private const val PREFS_NAME = "notification_hub_prefs"
        private const val KEY_DEVICE_UID = "device_uid"
        private const val KEY_PENDING_QUEUE = "pending_notifications"
        private const val RETRY_INTERVAL_MS = 30000L
        lateinit var instance: NotificationHubApp
            private set
    }

    lateinit var apiClient: ApiClient
        private set
    private var connectHost: String = ""
    private var connectPort: Int = 0
    private var webSocketClient: WebSocketClient? = null
    private var smsObserver: SmsObserver? = null
    private var prefs: SharedPreferences? = null
    private var retryJob: Job? = null
    private var onNotificationReceived: ((String) -> Unit)? = null

    val deviceUid: String
        get() {
            if (prefs == null) prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var uid = prefs!!.getString(KEY_DEVICE_UID, "")
            if (uid.isNullOrEmpty()) {
                uid = java.util.UUID.randomUUID().toString()
                prefs!!.edit().putString(KEY_DEVICE_UID, uid).apply()
            }
            return uid
        }

    fun setOnNotificationReceivedListener(listener: (String) -> Unit) {
        onNotificationReceived = listener
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiClient = ApiClient(this)
    }

    fun connect(host: String, port: Int, token: String) {
        apiClient.configure(host, port, token)
        connectHost = host
        connectPort = port
        startServices()
    }

    fun isConnected(): Boolean = apiClient.apiToken.isNotEmpty()
    fun getConnectHost(): String = connectHost
    fun getConnectPort(): Int = connectPort

    fun enqueueNotification(request: PushNotificationRequest) {
        try {
            val json = JSONObject()
            json.put("title", request.title)
            json.put("content", request.content)
            json.put("app_package", request.appPackage)
            json.put("app_name", request.appName)
            json.put("notification_type", request.notificationType)
            json.put("is_sms", request.isSms)
            json.put("verification_code", request.verificationCode)
            json.put("category", request.category)
            val queue = JSONArray(prefs?.getString(KEY_PENDING_QUEUE, "[]") ?: "[]")
            queue.put(json)
            prefs?.edit()?.putString(KEY_PENDING_QUEUE, queue.toString())?.apply()
            Log.d(TAG, "Notification queued (size: " + queue.length() + ")")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue: " + e.message)
        }
    }

    private fun flushPendingQueue() {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isConnected()) return@launch
            try {
                val queueStr = prefs?.getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
                val queue = JSONArray(queueStr)
                if (queue.length() == 0) return@launch
                Log.d(TAG, "Flushing " + queue.length() + " pending notifications")
                val remaining = JSONArray()
                for (i in 0 until queue.length()) {
                    val obj = queue.getJSONObject(i)
                    val req = PushNotificationRequest(
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        appPackage = obj.optString("app_package", ""),
                        appName = obj.optString("app_name", ""),
                        notificationType = obj.optString("notification_type", "general"),
                        isSms = obj.optBoolean("is_sms", false),
                        verificationCode = obj.optString("verification_code", ""),
                        category = obj.optString("category", "")
                    )
                    try {
                        apiClient.pushNotification(req)
                    } catch (e: Exception) {
                        remaining.put(obj)
                        Log.w(TAG, "Retry failed: " + e.message)
                    }
                }
                prefs?.edit()?.putString(KEY_PENDING_QUEUE, remaining.toString())?.apply()
            } catch (e: Exception) {
                Log.w(TAG, "Flush error: " + e.message)
            }
        }
    }

    private fun startRetryService() {
        retryJob?.cancel()
        retryJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                if (isConnected()) flushPendingQueue()
            }
        }
    }

    private fun startServices() {
        val intent = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        webSocketClient = WebSocketClient(
            apiClient = apiClient,
            onNotification = { msg ->
                onNotificationReceived?.invoke(msg)
                Log.d(TAG, "Notification received via WS")
            },
            onStatusChange = { connected ->
                Log.d(TAG, "WebSocket: " + if (connected) "connected" else "disconnected")
                if (connected) flushPendingQueue()
            }
        )
        webSocketClient?.connect()

        startSmsObserver()
        startRetryService()
        Log.i(TAG, "Services started: host=$connectHost, port=$connectPort")
    }

    private fun startSmsObserver() {
        try {
            smsObserver?.let { contentResolver.unregisterContentObserver(it) }
            val handler = Handler(Looper.getMainLooper())
            smsObserver = SmsObserver(this, handler)
            contentResolver.registerContentObserver(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                true,
                smsObserver!!
            )
            Log.d(TAG, "SMS observer registered")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot register SMS observer: " + e.message)
        }
    }

    override fun onTerminate() {
        webSocketClient?.disconnect()
        retryJob?.cancel()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        super.onTerminate()
    }
}
