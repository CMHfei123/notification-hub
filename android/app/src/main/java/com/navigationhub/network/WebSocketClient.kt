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
        private const val INITIAL_RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_DELAY_MS = 120_000L
    }

    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var shouldReconnect = true
    private var reconnectAttempt = 0
    private var isConnecting = false

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            reconnectAttempt = 0; isConnecting = false; onStatusChange?.invoke(true)
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "__pong__") return
            onNotification?.invoke(text)
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closing: $code $reason"); isConnecting = false; webSocket.close(1000, null)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closed: $code $reason"); isConnecting = false; onStatusChange?.invoke(false); scheduleReconnect()
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Failure: ${t.message}"); isConnecting = false; onStatusChange?.invoke(false); scheduleReconnect()
        }
    }

    fun connect() {
        if (isConnecting || webSocket != null) return
        shouldReconnect = true; isConnecting = true
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try { webSocket = apiClient.createWebSocket("/ws/notifications?token=${apiClient.apiToken}", listener) }
        catch (e: Exception) { Log.e(TAG, "Create failed: ${e.message}"); isConnecting = false; scheduleReconnect(); return }
        scope?.launch { while (isActive) { delay(PING_INTERVAL_MS); try { webSocket?.send("__ping__") } catch (_: Exception) {} } }
    }

    fun disconnect() {
        shouldReconnect = false; isConnecting = false; scope?.cancel(); scope = null
        try { webSocket?.close(1000, "Client closing") } catch (_: Exception) {}; webSocket = null
    }

    private var reconnectJob: Job? = null
    private fun scheduleReconnect() {
        if (!shouldReconnect) return; reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            reconnectAttempt++
            val delayMs = minOf(INITIAL_RECONNECT_DELAY_MS * (1L shl minOf(reconnectAttempt - 1, 4)), MAX_RECONNECT_DELAY_MS)
            Log.d(TAG, "Reconnect in ${delayMs}ms (attempt $reconnectAttempt)")
            delay(delayMs)
            if (shouldReconnect) connect()
        }
    }
}