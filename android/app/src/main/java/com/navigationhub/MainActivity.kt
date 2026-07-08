package com.navigationhub

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.navigationhub.NotificationHubApp
import com.navigationhub.R
import com.navigationhub.model.PushNotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById<TextView>(R.id.statusText)
        val btnPair = findViewById<Button>(R.id.btnPair)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnViewNotifications = findViewById<Button>(R.id.btnViewNotifications)

        updateStatus()

        btnPair.setOnClickListener {
            startActivity(Intent(this, com.navigationhub.ui.PairingActivity::class.java))
        }

        btnTest.setOnClickListener { testPush() }

        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnViewNotifications.setOnClickListener {
            startActivity(Intent(this, com.navigationhub.ui.NotificationsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val app = application as NotificationHubApp
        if (app.isConnected()) {
            statusText.text = "已连接到服务器"
        } else {
            statusText.text = "未连接，请先配对"
        }
    }

    private fun testPush() {
        val app = application as NotificationHubApp
        if (!app.isConnected()) {
            Toast.makeText(this, "请先配对", Toast.LENGTH_SHORT).show()
            return
        }
        val req = PushNotificationRequest(
            title = "测试通知",
            content = "这是一条来自 NotificationHub 的测试消息",
            appPackage = packageName,
            appName = "NotificationHub"
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.apiClient.pushNotification(req)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "测试消息已发送", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "发送失败：" + e.message.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
