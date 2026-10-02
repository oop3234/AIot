package com.example.pdrprototype

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var pdr: PDRProcessor
    private lateinit var trajectoryView: TrajectoryView

    private lateinit var statusText: TextView
    private lateinit var stepText: TextView
    private lateinit var distanceText: TextView
    private lateinit var positionText: TextView
    private lateinit var headingText: TextView
    private lateinit var accelerationText: TextView
    private lateinit var startButton: Button
    private lateinit var resetButton: Button

    private var running = false

    private val accelerometerData = FloatArray(3)
    private val magnetometerData = FloatArray(3)

    private var accelerationMagnitude = 0.0
    private var filteredAcceleration = 0.0
    private var previousAcceleration = 0.0
    private var lastStepTime = 0L

    private val stepThreshold = 1.2
    private val minimumStepInterval = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdr = PDRProcessor()
        createUI()
        initializeSensors()
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun createUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        statusText = TextView(this).apply {
            text = "상태: 정지"
            textSize = 18f
        }
        root.addView(statusText)

        stepText = TextView(this).apply {
            text = "걸음: 0"
            textSize = 18f
        }
        root.addView(stepText)

        distanceText = TextView(this).apply {
            text = "거리: 0.00 m"
            textSize = 18f
        }
        root.addView(distanceText)

        positionText = TextView(this).apply {
            text = "위치: X=0.00 Y=0.00"
            textSize = 18f
        }
        root.addView(positionText)

        headingText = TextView(this).apply {
            text = "방향: 0°"
            textSize = 18f
        }
        root.addView(headingText)

        accelerationText = TextView(this).apply {
            text = "가속도: 0.00"
            textSize = 18f
        }
        root.addView(accelerationText)

        trajectoryView = TrajectoryView(this)
        root.addView(
            trajectoryView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
        )

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        startButton = Button(this).apply { text = "시작" }
        resetButton = Button(this).apply { text = "초기화" }

        buttonLayout.addView(
            startButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        buttonLayout.addView(
            resetButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(buttonLayout)

        setContentView(root)

        startButton.setOnClickListener {
            if (running) stopPDR() else startPDR()
        }

        resetButton.setOnClickListener {
            resetPDR()
        }
    }

    private fun startPDR() {
        running = true
        statusText.text = "상태: 측정 중"
        startButton.text = "정지"

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopPDR() {
        running = false
        statusText.text = "상태: 정지"
        startButton.text = "시작"
        sensorManager.unregisterListener(this)
    }

    private fun resetPDR() {
        pdr.reset()
        trajectoryView.reset()
        filteredAcceleration = 0.0
        previousAcceleration = 0.0
        lastStepTime = 0L
        updateUI()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerData, 0, 3)
                processAcceleration()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerData, 0, 3)
                calculateHeading()
            }

            Sensor.TYPE_GYROSCOPE -> {
                // 다음 단계에서 자이로 기반 센서 융합에 사용
            }
        }
    }

    private fun processAcceleration() {
        val x = accelerometerData[0]
        val y = accelerometerData[1]
        val z = accelerometerData[2]

        accelerationMagnitude = sqrt(
            (x * x + y * y + z * z).toDouble()
        )

        val dynamicAcceleration = abs(accelerationMagnitude - 9.81)

        filteredAcceleration =
            0.8 * filteredAcceleration + 0.2 * dynamicAcceleration

        detectStep(filteredAcceleration)

        accelerationText.text =
            String.format("가속도: %.2f", filteredAcceleration)
    }

    private fun detectStep(acceleration: Double) {
        val now = System.currentTimeMillis()

        val peakDetected =
            acceleration > stepThreshold &&
            acceleration > previousAcceleration

        val enoughTime =
            now - lastStepTime > minimumStepInterval

        if (peakDetected && enoughTime) {
            lastStepTime = now
            pdr.onStep()

            trajectoryView.updatePosition(
                pdr.x,
                pdr.y
            )

            updateUI()
        }

        previousAcceleration = acceleration
    }

    private fun calculateHeading() {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerData,
            magnetometerData
        )

        if (!success) return

        SensorManager.getOrientation(
            rotationMatrix,
            orientation
        )

        var azimuth = Math.toDegrees(orientation[0].toDouble())
        if (azimuth < 0) azimuth += 360.0

        pdr.setHeading(azimuth)

        headingText.text =
            String.format("방향: %.1f°", azimuth)
    }

    private fun updateUI() {
        stepText.text = "걸음: ${pdr.stepCount}"
        distanceText.text =
            String.format("거리: %.2f m", pdr.totalDistance)
        positionText.text =
            String.format("위치: X=%.2f Y=%.2f", pdr.x, pdr.y)
        headingText.text =
            String.format("방향: %.1f°", pdr.heading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
