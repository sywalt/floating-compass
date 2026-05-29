package com.example.floatingcompass

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

class FloatingCompassService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f
    private var hasGravity = false
    private var hasMagnetic = false

    private lateinit var compassNeedle: ImageView
    private lateinit var tvDegree: TextView
    private lateinit var tvDirection: TextView

    // 尺寸档位：小(120dp) / 中(200dp) / 大(280dp)
    private val sizes = listOf(120, 200, 280)
    private var sizeIndex = 1  // 默认中号

    // 三击检测
    private var clickCount = 0
    private var lastClickTime = 0L
    private val TRIPLE_CLICK_INTERVAL = 600L  // 600ms内三击

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupSensors()
        setupFloatingWindow()
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_compass, null)

        compassNeedle = floatingView.findViewById(R.id.compassNeedle)
        tvDegree = floatingView.findViewById(R.id.tvDegree)
        tvDirection = floatingView.findViewById(R.id.tvDirection)

        if (magnetometer == null) {
            tvDegree.text = "N/A"
            tvDirection.text = "无磁力计"
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(sizes[sizeIndex]),
            dp(sizes[sizeIndex] + 30),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 160
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        handleClick(params)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun handleClick(params: WindowManager.LayoutParams) {
        val now = System.currentTimeMillis()
        if (now - lastClickTime > TRIPLE_CLICK_INTERVAL) {
            clickCount = 0
        }
        clickCount++
        lastClickTime = now

        if (clickCount >= 3) {
            clickCount = 0
            cycleSize(params)
        }
    }

    private fun cycleSize(params: WindowManager.LayoutParams) {
        sizeIndex = (sizeIndex + 1) % sizes.size
        val newSizePx = dp(sizes[sizeIndex])
        params.width = newSizePx
        params.height = newSizePx + dp(30)

        // 同步缩放表盘和指针
        val dialView = floatingView.findViewById<ImageView>(R.id.compassDial)
        val needleView = floatingView.findViewById<ImageView>(R.id.compassNeedle)
        val infoBar = floatingView.findViewById<View>(R.id.infoBar)

        val lp = ViewGroup.LayoutParams(newSizePx, newSizePx)
        dialView.layoutParams = lp
        needleView.layoutParams = ViewGroup.LayoutParams(newSizePx, newSizePx)

        val infoLp = infoBar.layoutParams as ViewGroup.LayoutParams
        infoLp.width = newSizePx
        infoBar.layoutParams = infoLp

        // 字号随尺寸变化
        val textSize = when (sizeIndex) {
            0 -> 10f   // 小
            1 -> 14f   // 中
            else -> 18f // 大
        }
        tvDegree.textSize = textSize
        tvDirection.textSize = textSize

        windowManager.updateViewLayout(floatingView, params)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.97f
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * event.values[0]
                geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * event.values[1]
                geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * event.values[2]
                hasMagnetic = true
            }
        }

        if (!hasGravity || !hasMagnetic) return

        val R = FloatArray(9)
        val I = FloatArray(9)
        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            val target = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360)

            var delta = target - currentAzimuth
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360
            currentAzimuth += delta * 0.15f
            currentAzimuth = (currentAzimuth + 360) % 360

            compassNeedle.rotation = -currentAzimuth
            tvDegree.text = "${currentAzimuth.toInt()}°"
            tvDirection.text = getDirection(currentAzimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getDirection(degrees: Float): String = when {
        degrees < 22.5 || degrees >= 337.5 -> "北"
        degrees < 67.5  -> "东北"
        degrees < 112.5 -> "东"
        degrees < 157.5 -> "东南"
        degrees < 202.5 -> "南"
        degrees < 247.5 -> "西南"
        degrees < 292.5 -> "西"
        else            -> "西北"
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        super.onDestroy()
    }
}
