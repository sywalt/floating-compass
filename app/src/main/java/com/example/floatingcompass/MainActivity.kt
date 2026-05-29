package com.example.floatingcompass

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_OVERLAY = 1001
        const val REQUEST_LOCATION = 1002
        const val EXTRA_MODE = "mode"
        const val EXTRA_TARGET_LAT = "target_lat"
        const val EXTRA_TARGET_LNG = "target_lng"
        const val MODE_COMPASS = "compass"
        const val MODE_TARGET = "target"
    }

    private var currentMode = MODE_COMPASS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus     = findViewById<TextView>(R.id.tvStatus)
        val btnModeCompass = findViewById<Button>(R.id.btnModeCompass)
        val btnModeTarget  = findViewById<Button>(R.id.btnModeTarget)
        val layoutTarget   = findViewById<LinearLayout>(R.id.layoutTarget)
        val etLat        = findViewById<EditText>(R.id.etLat)
        val etLng        = findViewById<EditText>(R.id.etLng)
        val btnStart     = findViewById<Button>(R.id.btnStart)
        val btnStop      = findViewById<Button>(R.id.btnStop)

        updateStatus(tvStatus)
        checkSensorWarning(tvStatus)

        // 模式切换
        btnModeCompass.setOnClickListener {
            currentMode = MODE_COMPASS
            layoutTarget.visibility = View.GONE
            btnModeCompass.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            btnModeCompass.setTextColor(0xFFFFFFFF.toInt())
            btnModeTarget.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            btnModeTarget.setTextColor(0xFF8B949E.toInt())
        }

        btnModeTarget.setOnClickListener {
            currentMode = MODE_TARGET
            layoutTarget.visibility = View.VISIBLE
            btnModeTarget.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_dark)
            btnModeTarget.setTextColor(0xFFFFFFFF.toInt())
            btnModeCompass.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            btnModeCompass.setTextColor(0xFF8B949E.toInt())
        }

        // 快捷城市
        findViewById<Button>(R.id.btnBeijing).setOnClickListener {
            etLat.setText("39.9042"); etLng.setText("116.4074")
        }
        findViewById<Button>(R.id.btnShanghai).setOnClickListener {
            etLat.setText("31.2304"); etLng.setText("121.4737")
        }
        findViewById<Button>(R.id.btnGuangzhou).setOnClickListener {
            etLat.setText("23.1291"); etLng.setText("113.2644")
        }

        btnStart.setOnClickListener {
            if (!checkOverlayPermission()) { requestOverlayPermission(); return@setOnClickListener }
            if (currentMode == MODE_TARGET) {
                if (!checkLocationPermission()) { requestLocationPermission(); return@setOnClickListener }
                val lat = etLat.text.toString().toDoubleOrNull()
                val lng = etLng.text.toString().toDoubleOrNull()
                if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                    Toast.makeText(this, "请输入有效的坐标", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startService(Intent(this, FloatingCompassService::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_TARGET)
                    putExtra(EXTRA_TARGET_LAT, lat)
                    putExtra(EXTRA_TARGET_LNG, lng)
                })
                Toast.makeText(this, "目标导航已启动 → ($lat, $lng)", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, FloatingCompassService::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_COMPASS)
                })
                Toast.makeText(this, getString(R.string.service_started), Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, FloatingCompassService::class.java))
            Toast.makeText(this, getString(R.string.service_stopped), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION)
    }

    private fun checkSensorWarning(tvStatus: TextView) {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        if (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
            tvStatus.text = getString(R.string.no_sensor)
            tvStatus.setTextColor(0xFFFF9500.toInt())
        }
    }

    private fun updateStatus(tvStatus: TextView) {
        val ok = checkOverlayPermission()
        tvStatus.text = getString(if (ok) R.string.status_granted else R.string.status_denied)
        tvStatus.setTextColor(if (ok) 0xFF3FB950.toInt() else 0xFFFF6B6B.toInt())
    }

    private fun checkOverlayPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun requestOverlayPermission() {
        Toast.makeText(this, getString(R.string.permission_hint), Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQUEST_OVERLAY)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "定位权限已获取，请重新点击启动", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        updateStatus(tvStatus)
        checkSensorWarning(tvStatus)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY && checkOverlayPermission()) {
            Toast.makeText(this, "权限已获取，请重新点击启动", Toast.LENGTH_SHORT).show()
        }
    }
}
