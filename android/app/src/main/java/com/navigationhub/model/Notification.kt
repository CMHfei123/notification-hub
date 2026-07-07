package com.navigationhub.model

data class PushNotificationRequest(
    val title: String = "",
    val content: String = "",
    val appPackage: String = "",
    val appName: String = "",
    val notificationType: String = "general",
    val isSms: Boolean = false,
    val verificationCode: String = "",
    val category: String = ""
)

data class NotificationResponse(
    val id: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val appPackage: String = "",
    val appName: String = "",
    val title: String = "",
    val content: String = "",
    val notificationType: String = "general",
    val isSms: Boolean = false,
    val verificationCode: String = "",
    val category: String = "",
    val createdAt: String = ""
)

data class PairingCodeResponse(
    val pairingCode: String = "",
    val qrcodeBase64: String = "",
    val expiresAt: String = ""
)

data class PairingVerifyRequest(
    val pairingCode: String = ""
)

data class PairingVerifyResponse(
    val success: Boolean = false,
    val message: String = "",
    val deviceId: String? = null,
    val apiToken: String? = null
)

data class DeviceListResponse(
    val devices: List<DeviceResponse> = emptyList()
)

data class DeviceResponse(
    val id: String = "",
    val name: String = "",
    val deviceType: String = "",
    val platform: String = "",
    val platformVersion: String = "",
    val lastSeen: String? = null,
    val pairedAt: String? = null,
    val isActive: Boolean = true
)
