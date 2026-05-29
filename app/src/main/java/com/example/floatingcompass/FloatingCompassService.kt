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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private lateinit var compassDial: ImageView
    private lateinit var infoBar: LinearLayout
    private lateinit var tvDegree: TextView
    private lateinit var tvDirection: TextView
    private lateinit var windowParams: WindowManager.LayoutParams

    // 尺寸档位 dp
    private val sizes = listOf(100, 160, 220)
    private var sizeIndex = 1

    // 三击检测
    private var clickCount = 0
    private var lastClickTime = 0L
    private val TRIPLE_CLICK_MS = 600L

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

        if (magnetometer == null) {
            tvDegree.text = "N/A"
            tvDirection.text = "无磁力计"
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
            x = 40
            y = 160
        }

        applySize()

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
                    windowParams.x = initX + dx
                    windowParams.y = initY + dy
                    windowManager.updateViewLayout(floatingView, windowParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) handleClick()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, windowParams)

        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun handleClick() {
        val now = System.currentTimeMillis()
        if (now - lastClickTime > TRIPLE_CLICK_MS) clickCount = 0
        clickCount++
        lastClickTime = now
        if (clickCount >= 3) {
            clickCount = 0
            sizeIndex = (sizeIndex + 1) % sizes.size
            applySize()
        }
    }

    private fun applySize() {
        val sizePx = dp(sizes[sizeIndex])

        // 更新表盘和指针尺寸
        listOf(compassDial, compassNeedle).forEach { view ->
            view.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).also {
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }

        // 更新信息栏宽度
        infoBar.layoutParams = FrameLayout.LayoutParams(sizePx, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp(8)
        }

        // 字号随尺寸变化
        val textSize = when (sizeIndex) {
            0    -> 10f
            1    -> 14f
            else -> 18f
        }
        tvDegree.textSize    = textSize
        tvDirection.textSize = textSize

        windowManager.updateViewLayout(floatingView, windowParams)
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
        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            val target = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360)
            var delta = target - currentAzimuth
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360
            currentAzimuth = (currentAzimuth + delta * 0.15f + 360) % 360

            compassNeedle.rotation = -currentAzimuth
            tvDegree.text    = "${currentAzimuth.toInt()}°"
            tvDirection.text = getDirection(currentAzimuth)
        }
    }

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
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        super.onDestroy()
    }
}
