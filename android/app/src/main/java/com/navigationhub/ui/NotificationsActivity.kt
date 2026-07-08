package com.navigationhub.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navigationhub.NotificationHubApp
import com.navigationhub.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private var adapter: NotificationAdapter? = null
    private var notifications = mutableListOf<Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        recyclerView = findViewById(R.id.notificationList)
        emptyText = findViewById(R.id.emptyText)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(notifications)
        recyclerView.adapter = adapter
        loadNotifications()
        val app = application as NotificationHubApp
        app.setOnNotificationReceivedListener { runOnUiThread { loadNotifications() } }
    }

    private fun loadNotifications() {
        val app = application as NotificationHubApp
        if (!app.isConnected()) { emptyText.visibility = View.VISIBLE; emptyText.text = "请先配对服务器"; return }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = app.apiClient.fetchNotifications()
                withContext(Dispatchers.Main) {
                    notifications.clear(); notifications.addAll(items)
                    adapter?.notifyDataSetChanged()
                    emptyText.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@NotificationsActivity, "加载失败: " + e.message, Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

class NotificationAdapter(private val items: List<Map<String, Any>>) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.notifDeviceName)
        val appName: TextView = view.findViewById(R.id.notifAppName)
        val title: TextView = view.findViewById(R.id.notifTitle)
        val content: TextView = view.findViewById(R.id.notifContent)
        val time: TextView = view.findViewById(R.id.notifTime)
        val smsCode: TextView = view.findViewById(R.id.notifSmsCode)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.deviceName.text = item["device_name"]?.toString() ?: "未知设备"
        holder.appName.text = item["app_name"]?.toString() ?: ""
        holder.title.text = item["title"]?.toString() ?: ""
        holder.content.text = item["content"]?.toString() ?: ""
        val createdAt = item["created_at"]?.toString() ?: ""
        holder.time.text = if (createdAt.length >= 19) createdAt.substring(11, 19) else createdAt
        val smsCode = item["verification_code"]?.toString() ?: ""
        if (smsCode.isNotEmpty()) { holder.smsCode.visibility = View.VISIBLE; holder.smsCode.text = "验证码: $smsCode" }
        else { holder.smsCode.visibility = View.GONE }
    }
    override fun getItemCount() = items.size
}
