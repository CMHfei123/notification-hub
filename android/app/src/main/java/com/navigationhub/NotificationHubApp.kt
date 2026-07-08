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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.LinkedHashMap

class NotificationHubApp : Application() {
    companion object {
        private const val TAG = "NHApp"
        private const val PREFS_NAME = "notification_hub_prefs"
        private const val KEY_DEVICE_UID = "device_uid"
        private const val KEY_PENDING_QUEUE = "pending_notifications"
        private const val KEY_CONNECT_HOST = "connect_host"
        private const val KEY_CONNECT_PORT = "connect_port"
        private const val KEY_API_TOKEN = "api_token"
        private const val RETRY_INTERVAL_MS = 30_000L
        private const val MAX_PENDING_QUEUE = 200
        private const val DEDUP_WINDOW_MS = 10_000L
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
    private var appScope: CoroutineScope? = null
    private var onNotificationReceived: ((String) -> Unit)? = null

    private val recentHashes = LinkedHashMap<String, Long>()

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
        appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        autoConnect()
    }

    private fun autoConnect() {
        val host = prefs?.getString(KEY_CONNECT_HOST, "") ?: ""
        val port = prefs?.getInt(KEY_CONNECT_PORT, 0) ?: 0
        val token = prefs?.getString(KEY_API_TOKEN, "") ?: ""
        if (host.isNotEmpty() && port > 0 && token.isNotEmpty()) {
            Log.i(TAG, "Auto-connecting to $host:$port")
            apiClient.configure(host, port, token)
            connectHost = host
            connectPort = port
            startServices()
        } else {
            Log.d(TAG, "No saved connection info")
        }
    }

    fun connect(host: String, port: Int, token: String) {
        apiClient.configure(host, port, token)
        connectHost = host
        connectPort = port
        prefs?.edit()?.apply {
            putString(KEY_CONNECT_HOST, host)
            putInt(KEY_CONNECT_PORT, port)
            putString(KEY_API_TOKEN, token)
            apply()
        }
        startServices()
    }

    fun disconnect() {
        webSocketClient?.disconnect()
        retryJob?.cancel()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        connectHost = ""
        connectPort = 0
        apiClient.apiToken = ""
        prefs?.edit()?.apply {
            remove(KEY_CONNECT_HOST)
            remove(KEY_CONNECT_PORT)
            remove(KEY_API_TOKEN)
            apply()
        }
    }

    fun isConnected(): Boolean = apiClient.apiToken.isNotEmpty() && connectHost.isNotEmpty()
    fun getConnectHost(): String = connectHost
    fun getConnectPort(): Int = connectPort

    fun tryPushNotification(request: PushNotificationRequest) {
        val hash = computeNotificationHash(request)
        val now = System.currentTimeMillis()
        synchronized(recentHashes) {
            val existing = recentHashes[hash]
            if (existing != null && (now - existing) < DEDUP_WINDOW_MS) {
                Log.d(TAG, "Skipping duplicate: ${request.title}")
                return
            }
            recentHashes[hash] = now
            if (recentHashes.size > 300) {
                val iter = recentHashes.iterator()
                repeat(50) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }
        }
        appScope?.launch {
            try {
                apiClient.pushNotification(request)
            } catch (e: Exception) {
                Log.w(TAG, "Push failed, queuing: ${e.message}")
                enqueueNotification(request)
            }
        }
    }

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
            val queueStr = prefs?.getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
            val queue = JSONArray(queueStr)
            if (queue.length() >= MAX_PENDING_QUEUE) {
                val trimmed = JSONArray()
                for (i in maxOf(0, queue.length() - MAX_PENDING_QUEUE + 1) until queue.length())
                    trimmed.put(queue.get(i))
                prefs?.edit()?.putString(KEY_PENDING_QUEUE, trimmed.toString())?.apply()
            }
            queue.put(json)
            prefs?.edit()?.putString(KEY_PENDING_QUEUE, queue.toString())?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue: ${e.message}")
        }
    }

    private fun computeNotificationHash(req: PushNotificationRequest): String {
        val raw = "${req.appPackage}|${req.title}|${req.content}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun flushPendingQueue() {
        appScope?.launch {
            if (!isConnected()) return@launch
            try {
                val queueStr = prefs?.getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
                val queue = JSONArray(queueStr)
                if (queue.length() == 0) return@launch
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
                    try { apiClient.pushNotification(req) } catch (e: Exception) { remaining.put(obj) }
                }
                prefs?.edit()?.putString(KEY_PENDING_QUEUE, remaining.toString())?.apply()
            } catch (e: Exception) { Log.w(TAG, "Flush error: ${e.message}") }
        }
    }

    private fun startRetryService() {
        retryJob?.cancel()
        retryJob = appScope?.launch {
            while (isActive) { delay(RETRY_INTERVAL_MS); if (isConnected()) flushPendingQueue() }
        }
    }

    private fun startServices() {
        val intent = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        webSocketClient?.disconnect()
        webSocketClient = WebSocketClient(apiClient,
            onNotification = { msg -> onNotificationReceived?.invoke(msg) },
            onStatusChange = { connected -> if (connected) flushPendingQueue() })
        webSocketClient?.connect()
        startSmsObserver()
        startRetryService()
    }

    private fun startSmsObserver() {
        try {
            smsObserver?.let { contentResolver.unregisterContentObserver(it) }
            smsObserver = SmsObserver(this, Handler(Looper.getMainLooper()))
            contentResolver.registerContentObserver(android.provider.Telephony.Sms.Inbox.CONTENT_URI, true, smsObserver!!)
        } catch (e: SecurityException) { Log.w(TAG, "SMS observer: ${e.message}") }
    }

    override fun onTerminate() {
        appScope?.cancel()
        webSocketClient?.disconnect()
        retryJob?.cancel()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        super.onTerminate()
    }
}