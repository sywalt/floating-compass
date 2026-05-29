package com.example.floatingcompass

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingCompassService : Service(), SensorEventListener, LocationListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var sensorManager: SensorManager
    private var locationManager: LocationManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f
    private var hasGravity = false
    private var hasMagnetic = false
    private var viewAttached = false

    // 模式
    private var mode = MainActivity.MODE_COMPASS
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var currentLat = 0.0
    private var currentLng = 0.0
    private var hasLocation = false

    private lateinit var compassNeedle: ImageView
    private lateinit var compassDial: ImageView
    private lateinit var infoBar: LinearLayout
    private lateinit var tvDegree: TextView
    private lateinit var tvDirection: TextView
    private lateinit var windowParams: WindowManager.LayoutParams

    private val sizes = listOf(100, 160, 220)
    private var sizeIndex = 1
    private var clickCount = 0
    private var lastClickTime = 0L
    private val TRIPLE_CLICK_MS = 600L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            mode = it.getStringExtra(MainActivity.EXTRA_MODE) ?: MainActivity.MODE_COMPASS
            targetLat = it.getDoubleExtra(MainActivity.EXTRA_TARGET_LAT, 0.0)
            targetLng = it.getDoubleExtra(MainActivity.EXTRA_TARGET_LNG, 0.0)
        }
        if (mode == MainActivity.MODE_TARGET) startLocationUpdates()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        try {
            setupSensors()
            setupFloatingWindow()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, this)
            // 用最后已知位置先初始化
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { updateLocation(it) }
            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { updateLocation(it) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateLocation(location: Location) {
        currentLat = location.latitude
        currentLng = location.longitude
        hasLocation = true
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun setupFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_compass, null)

        compassDial   = floatingView.findViewById(R.id.compassDial)
        compassNeedle = floatingView.findViewById(R.id.compassNeedle)
        infoBar       = floatingView.findViewById(R.id.infoBar)
        tvDegree      = floatingView.findViewById(R.id.tvDegree)
        tvDirection   = floatingView.findViewById(R.id.tvDirection)

        if (magnetometer == null && mode == MainActivity.MODE_COMPASS) {
            tvDegree.text = "N/A"; tvDirection.text = "无磁力计"
        }
        if (mode == MainActivity.MODE_TARGET) {
            tvDirection.text = "等待GPS定位..."
            tvDegree.text = "---"
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40; y = 160
        }

        setSizeOnViews(sizeIndex)

        var initX = 0; var initY = 0
        var initTX = 0f; var initTY = 0f
        var dragging = false

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = windowParams.x; initY = windowParams.y
                    initTX = event.rawX;    initTY = event.rawY
                    dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initTX).toInt()
                    val dy = (event.rawY - initTY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) dragging = true
                    windowParams.x = initX + dx; windowParams.y = initY + dy
                    if (viewAttached) windowManager.updateViewLayout(floatingView, windowParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) handleClick(); true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, windowParams)
        viewAttached = true

        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun handleClick() {
        val now = System.currentTimeMillis()
        if (now - lastClickTime > TRIPLE_CLICK_MS) clickCount = 0
        clickCount++; lastClickTime = now
        if (clickCount >= 3) {
            clickCount = 0
            sizeIndex = (sizeIndex + 1) % sizes.size
            setSizeOnViews(sizeIndex)
            if (viewAttached) windowManager.updateViewLayout(floatingView, windowParams)
        }
    }

    private fun setSizeOnViews(index: Int) {
        val sizePx = dp(sizes[index])
        compassDial.layoutParams   = FrameLayout.LayoutParams(sizePx, sizePx)
        compassNeedle.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        infoBar.layoutParams = LinearLayout.LayoutParams(sizePx, LinearLayout.LayoutParams.WRAP_CONTENT)
        val dirSize = when (index) { 0 -> 12f; 1 -> 16f; else -> 20f }
        val degSize = when (index) { 0 -> 10f; 1 -> 13f; else -> 16f }
        tvDirection.textSize = dirSize
        tvDegree.textSize    = degSize
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.97f
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                for (i in 0..2) geomagnetic[i] = alpha * geomagnetic[i] + (1 - alpha) * event.values[i]
                hasMagnetic = true
            }
        }
        if (!hasGravity || !hasMagnetic) return

        val R = FloatArray(9); val I = FloatArray(9)
        if (!SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) return
        val orientation = FloatArray(3)
        SensorManager.getOrientation(R, orientation)
        val deviceHeading = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360)

        if (mode == MainActivity.MODE_TARGET && hasLocation) {
            // 计算当前位置到目标的方位角
            val fromLoc = android.location.Location("").apply { latitude = currentLat; longitude = currentLng }
            val toLoc   = android.location.Location("").apply { latitude = targetLat;  longitude = targetLng }
            val bearingToTarget = (fromLoc.bearingTo(toLoc) + 360) % 360

            // 指针需要指向目标：目标方位角 - 当前朝向 = 指针旋转角
            val pointerAngle = bearingToTarget - deviceHeading
            smoothRotate(pointerAngle)

            val dist = fromLoc.distanceTo(toLoc)
            // 第一行：目标在哪个方向
            tvDirection.text = "目标在 ${getDirection(bearingToTarget)}"
            // 第二行：距离
            tvDegree.text    = "距离 ${formatDistance(dist)}"
        } else if (mode == MainActivity.MODE_TARGET && !hasLocation) {
            smoothRotate(-deviceHeading)
            tvDirection.text = "等待GPS定位..."
            tvDegree.text    = "${deviceHeading.toInt()}°"
        } else {
            // 普通指南针模式
            smoothRotate(-deviceHeading)
            tvDirection.text = getDirection(deviceHeading)
            tvDegree.text    = "${deviceHeading.toInt()}°"
        }
    }

    private fun smoothRotate(targetAngle: Float) {
        var delta = targetAngle - currentAzimuth
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        currentAzimuth += delta * 0.15f
        compassNeedle.rotation = currentAzimuth
    }

    private fun formatDistance(meters: Float): String = when {
        meters < 1000 -> "${meters.toInt()}m"
        else -> "${"%.1f".format(meters / 1000)}km"
    }

    // LocationListener
    override fun onLocationChanged(location: Location) { updateLocation(location) }
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getDirection(d: Float) = when {
        d < 22.5 || d >= 337.5 -> "北"
        d < 67.5  -> "东北"
        d < 112.5 -> "东"
        d < 157.5 -> "东南"
        d < 202.5 -> "南"
        d < 247.5 -> "西南"
        d < 292.5 -> "西"
        else      -> "西北"
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        locationManager?.removeUpdates(this)
        if (viewAttached) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) { e.printStackTrace() }
            viewAttached = false
        }
        super.onDestroy()
    }
}
