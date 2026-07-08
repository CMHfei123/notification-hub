package com.navigationhub.network

import android.content.Context
import android.util.Log
import com.navigationhub.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class ApiClient(private val context: Context) {
    companion object { private const val TAG = "ApiClient" }

    private val jsonMediaType = "application/json".toMediaType()
    private val trustAllCerts = arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    private val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    var baseUrl: String = ""
    var apiToken: String = ""

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .build()

    fun configure(host: String, port: Int, token: String) {
        baseUrl = "https://$host:$port"
        apiToken = token
    }

    private fun buildRequest(path: String, body: String? = null): Request {
        val url = "$baseUrl$path"
        val builder = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiToken").addHeader("Content-Type", "application/json")
        if (body != null) builder.post(body.toRequestBody(jsonMediaType))
        return builder.build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response")
        if (!response.isSuccessful) {
            val detail = try { JSONObject(body).optString("detail", body.substring(0, minOf(body.length, 200))) } catch (e: Exception) { body }
            throw IOException("HTTP ${response.code}: $detail")
        }
        body
    }

    suspend fun registerDevice(name: String, deviceUid: String = "", deviceType: String = "android"): TokenResponse {
        val json = JSONObject().apply {
            put("name", name); put("device_uid", deviceUid); put("device_type", deviceType)
            put("platform", "android"); put("platform_version", android.os.Build.VERSION.RELEASE)
        }
        val resp = execute(buildRequest("/api/auth/register", json.toString()))
        val obj = JSONObject(resp)
        return TokenResponse(accessToken = obj.getString("access_token"), deviceId = obj.optString("device_id"))
    }

    suspend fun verifyPairing(code: String): PairingVerifyResponse {
        val json = JSONObject().apply { put("pairing_code", code) }
        val resp = execute(buildRequest("/api/pairing/verify", json.toString()))
        val obj = JSONObject(resp)
        return PairingVerifyResponse(success = obj.optBoolean("success"), message = obj.optString("message"), deviceId = obj.optString("device_id", null), apiToken = obj.optString("api_token", null))
    }

    suspend fun pushNotification(request: PushNotificationRequest) {
        val json = JSONObject().apply {
            put("title", request.title); put("content", request.content); put("app_package", request.appPackage)
            put("app_name", request.appName); put("notification_type", request.notificationType)
            put("is_sms", request.isSms); put("verification_code", request.verificationCode); put("category", request.category)
        }
        try { execute(buildRequest("/api/notifications/push", json.toString())) }
        catch (e: Exception) { throw e }
    }

    suspend fun fetchNotifications(page: Int = 1, pageSize: Int = 50): List<Map<String, Any>> {
        val resp = execute(buildRequest("/api/notifications?page=$page&page_size=$pageSize"))
        val obj = JSONObject(resp)
        val arr = obj.getJSONArray("notifications")
        return (0 until arr.length()).map { i ->
            val n = arr.getJSONObject(i)
            mapOf("id" to n.optString("id", ""), "device_id" to n.optString("device_id", ""),
                "device_name" to n.optString("device_name", ""), "app_name" to n.optString("app_name", ""),
                "title" to n.optString("title", ""), "content" to n.optString("content", ""),
                "notification_type" to n.optString("notification_type", "general"),
                "is_sms" to n.optBoolean("is_sms", false),
                "verification_code" to n.optString("verification_code", ""),
                "created_at" to n.optString("created_at", ""))
        }
    }

    suspend fun getDevices(): List<DeviceResponse> {
        val resp = execute(buildRequest("/api/devices"))
        val obj = JSONObject(resp)
        val arr = obj.getJSONArray("devices")
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            DeviceResponse(id = d.getString("id"), name = d.optString("name"), deviceType = d.optString("device_type"),
                platform = d.optString("platform"), platformVersion = d.optString("platform_version"),
                lastSeen = d.optString("last_seen", null), isActive = d.optBoolean("is_active", true))
        }
    }

    fun createWebSocket(path: String, listener: WebSocketListener = object : WebSocketListener() {}): WebSocket {
        val url = "$baseUrl$path".replace("https://", "wss://")
        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiToken").build()
        return client.newWebSocket(request, listener)
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
