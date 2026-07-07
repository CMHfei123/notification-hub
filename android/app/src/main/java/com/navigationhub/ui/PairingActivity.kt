package com.navigationhub.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.navigationhub.NotificationHubApp
import com.navigationhub.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        statusText = findViewById(R.id.pairingStatus)
        codeInput = findViewById(R.id.pairingCode)
        val btnManualPair = findViewById<Button>(R.id.btnManualPair)

        statusText.text = "Enter pairing code from Web dashboard"

        btnManualPair.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.length >= 6) {
                pairWithCode(code)
            } else {
                Toast.makeText(this, "Enter valid pairing code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pairWithCode(code: String) {
        statusText.text = "Pairing..."
        val app = application as NotificationHubApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokenResp = app.apiClient.registerDevice(android.os.Build.MODEL)
                app.apiClient.apiToken = tokenResp.accessToken
                app.apiClient.verifyPairing(code)
                app.connect("192.168.1.18", 8856, tokenResp.accessToken)
                runOnUiThread {
                    statusText.text = "Paired successfully!"
                    Toast.makeText(this@PairingActivity, "Device paired!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Pairing failed: " + e.message
                }
            }
        }
    }
}
