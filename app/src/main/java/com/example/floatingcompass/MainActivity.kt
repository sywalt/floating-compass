package com.example.floatingcompass

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        updateStatus(tvStatus)

        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                startFloatingService()
                updateStatus(tvStatus)
            } else {
                requestOverlayPermission()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, FloatingCompassService::class.java))
            Toast.makeText(this, "悬浮指南针已关闭", Toast.LENGTH_SHORT).show()
            updateStatus(tvStatus)
        }
    }

    private fun updateStatus(tvStatus: TextView) {
        val hasPermission = checkOverlayPermission()
        tvStatus.text = if (hasPermission) "✅ 悬浮窗权限已授予" else "❌ 尚未授予悬浮窗权限"
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "请在设置中开启"显示在其他应用上层"权限", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    private fun startFloatingService() {
        startService(Intent(this, FloatingCompassService::class.java))
        Toast.makeText(this, "🧭 悬浮指南针已启动", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能运行", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.tvStatus))
    }
}
