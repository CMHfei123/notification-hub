package com.navigationhub.network

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val apiClient: ApiClient,
    private val onNotification: ((String) -> Unit)? = null,
    private val onStatusChange: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "WSClient"
        private const val PING_INTERVAL_MS = 30_000L
    }

    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var shouldReconnect = true

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            onStatusChange?.invoke(true)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "__pong__") return
            onNotification?.invoke(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            onStatusChange?.invoke(false)
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket failure: ${t.message}")
            onStatusChange?.invoke(false)
            scheduleReconnect()
        }
    }

    fun connect() {
        shouldReconnect = true
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        try {
            webSocket = apiClient.createWebSocket("/ws/notifications?token=${apiClient.apiToken}", listener)
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket create failed: ${e.message}")
            scheduleReconnect()
            return
        }

        // Heartbeat ping
        scope?.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                try {
                    webSocket?.send("__ping__")
                } catch (_: Exception) {}
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        scope?.cancel()
        scope = null
        try {
            webSocket?.close(1000, "Client closing")
        } catch (_: Exception) {}
        webSocket = null
    }

    private var reconnectJob: Job? = null

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            if (shouldReconnect) connect()
        }
    }
}
