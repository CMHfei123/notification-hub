package com.navigationhub.model

data class Device(
    val id: String = "",
    val name: String = "",
    val deviceType: String = "android",
    val platform: String = "android",
    val platformVersion: String = "",
    val isActive: Boolean = true,
    val lastSeen: String = "",
    val pairedAt: String = ""
)

data class DeviceRegisterRequest(
    val name: String,
    val deviceType: String = "android",
    val platform: String = "android",
    val platformVersion: String = android.os.Build.VERSION.RELEASE
)

data class TokenResponse(
    val accessToken: String = "",
    val tokenType: String = "bearer",
    val deviceId: String? = null
)
