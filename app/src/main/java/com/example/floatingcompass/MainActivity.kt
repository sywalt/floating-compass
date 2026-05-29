package com.example.floatingcompass

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
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
        checkSensorWarning(tvStatus)

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
            Toast.makeText(this, getString(R.string.service_stopped), Toast.LENGTH_SHORT).show()
            updateStatus(tvStatus)
        }
    }

    private fun checkSensorWarning(tvStatus: TextView) {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
            tvStatus.text = getString(R.string.no_sensor)
            tvStatus.setTextColor(0xFFFF6B6B.toInt())
        }
    }

    private fun updateStatus(tvStatus: TextView) {
        val hasPermission = checkOverlayPermission()
        tvStatus.text = getString(
            if (hasPermission) R.string.status_granted else R.string.status_denied
        )
        tvStatus.setTextColor(
            if (hasPermission) 0xFF3FB950.toInt() else 0xFFFF6B6B.toInt()
        )
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, getString(R.string.permission_hint), Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    private fun startFloatingService() {
        startService(Intent(this, FloatingCompassService::class.java))
        Toast.makeText(this, getString(R.string.service_started), Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        updateStatus(tvStatus)
        checkSensorWarning(tvStatus)
    }
}
