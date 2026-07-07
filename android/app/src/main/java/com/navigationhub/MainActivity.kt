package com.navigationhub

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.navigationhub.R

            

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val btnPair = findViewById(R.id.btnPair)
        val btnTest = findViewById(R.id.btnTest)
        val btnSettings = findViewById(R.id.btnSettings)

        updateStatus()

        btnPair.setOnClickListener {
            startActivity(Intent(this, com.navigationhub.ui.PairingActivity::class.java))
        }

        btnTest.setOnClickListener { testPush() }

        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val app = application as NotificationHubApp
        if (app.isConnected()) {
            statusText.text = "Connected to server"
        } else {
            statusText.text = "Not connected - please pair"
        }
    }

    private fun testPush() {
        val app = application as NotificationHubApp
        if (!app.isConnected()) {
            Toast.makeText(this, "Please pair first", Toast.LENGTH_SHORT).show()
            return
        }
        val req = com.navigationhub.model.PushNotificationRequest(
            title = "Test Notification",
            content = "This is a test from NotificationHub",
            appPackage = packageName,
            appName = "NotificationHub"
        )
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                app.apiClient.pushNotification(req)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Test sent!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
