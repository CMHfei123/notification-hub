package com.navigationhub

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.navigationhub.network.ApiClient
import com.navigationhub.network.WebSocketClient

class NotificationHubApp : Application() {
    companion object {
        private const val TAG = "NHApp"
        lateinit var instance: NotificationHubApp
            private set
    }

   lateinit var apiClient: ApiClient
       private set
    private var connectHost: String = ""
    private var connectPort: Int = 0
   private var webSocketClient: WebSocketClient? = null
    private var smsObserver: SmsObserver? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        apiClient = ApiClient(this)
    }

    fun connect(host: String, port: Int, token: String) {
        apiClient.configure(host, port, token)
        connectHost = host
        connectPort = port
        startServices()
    }

    fun isConnected(): Boolean = apiClient.apiToken.isNotEmpty()

    private fun startServices() {
        // Start foreground sync service
        val intent = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Start WebSocket for real-time
        webSocketClient = WebSocketClient(
            apiClient = apiClient,
            onStatusChange = { connected ->
                Log.d(TAG, "WebSocket: ${if (connected) "connected" else "disconnected"}")
            }
        )
        webSocketClient?.connect()

        // Start SMS observer
        startSmsObserver()

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
            Log.w(TAG, "Cannot register SMS observer: ${e.message}")
        }
    }

    override fun onTerminate() {
        webSocketClient?.disconnect()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        super.onTerminate()
    }
}
