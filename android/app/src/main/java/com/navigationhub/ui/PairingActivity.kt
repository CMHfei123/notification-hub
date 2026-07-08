package com.navigationhub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.navigationhub.NotificationHubApp
import com.navigationhub.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PairingActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            try {
                val qrJson = JSONObject(result.contents)
                val code = qrJson.optString("code", "")
                val host = hostInput.text.toString().trim()
                val port = portInput.text.toString().trim().toIntOrNull() ?: 8856
                if (code.isNotEmpty()) {
                    codeInput.setText(code)
                    pairWithCode(host, port, code)
                } else {
                    Toast.makeText(this, "\u65E0\u6548\u7684\u914D\u5BF9\u4E8C\u7EF4\u7801", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "\u65E0\u6548\u7684\u4E8C\u7EF4\u7801\u683C\u5F0F", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)
        statusText = findViewById(R.id.pairingStatus)
        codeInput = findViewById(R.id.pairingCode)
        hostInput = findViewById(R.id.serverHost)
        portInput = findViewById(R.id.serverPort)

        findViewById<Button>(R.id.btnManualPair).setOnClickListener {
            val code = codeInput.text.toString().trim()
            val host = hostInput.text.toString().trim()
            val port = portInput.text.toString().trim().toIntOrNull() ?: 8856
            if (code.length >= 6 && host.isNotEmpty()) pairWithCode(host, port, code)
            else Toast.makeText(this, "\u8BF7\u586B\u5199\u670D\u52A1\u5668\u5730\u5740\u548C\u914D\u5BF9\u7801", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnScanQr).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                startScan()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startScan()
        else Toast.makeText(this, "\u9700\u8981\u6444\u50CF\u5934\u6743\u9650\u624D\u80FD\u626B\u63CF\u4E8C\u7EF4\u7801", Toast.LENGTH_SHORT).show()
    }

    private fun startScan() {
        scanLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("\u626B\u63CF\u4EEA\u8868\u677F\u4E0A\u7684\u914D\u5BF9\u4E8C\u7EF4\u7801")
            setBeepEnabled(false)
            setOrientationLocked(false)
        })
    }

    private fun pairWithCode(host: String, port: Int, code: String) {
        statusText.text = "\u6B63\u5728\u914D\u5BF9..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = application as NotificationHubApp
                app.apiClient.configure(host, port, "")
                val tokenResp = app.apiClient.registerDevice(android.os.Build.MODEL, app.deviceUid)
                app.apiClient.apiToken = tokenResp.accessToken
                app.apiClient.verifyPairing(code)
                app.connect(host, port, tokenResp.accessToken)
                withContext(Dispatchers.Main) {
                    statusText.text = "\u914D\u5BF9\u6210\u529F"
                    Toast.makeText(this@PairingActivity, "\u8BBE\u5907\u5DF2\u914D\u5BF9", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { statusText.text = "\u914D\u5BF9\u5931\u8D25: " + e.message.toString() }
            }
        }
    }
}
