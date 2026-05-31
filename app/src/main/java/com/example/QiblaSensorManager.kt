package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

class QiblaSensorManager(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // True North heading in degrees [0, 360), where 0 is True North
    private val _headingFlow = MutableStateFlow(0f)
    val headingFlow: StateFlow<Float> = _headingFlow

    // Current quality status of compass (e.g. accuracy state)
    private val _accuracyFlow = MutableStateFlow(3) // 3 = SENSOR_STATUS_ACCURACY_HIGH
    val accuracyFlow: StateFlow<Int> = _accuracyFlow

    // Expose whether the hardware supports a compass/magnetometer sensor
    val hasMagnetometer: Boolean = magnetometer != null

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private var useRotationVector = false

    // Low-pass filter smoothing coefficient for raw accelerometer/geomagnetic inputs.
    private val sensorAlpha = 0.12f

    // Heading unit-vector low-pass parameters to gracefully prevent angular wrap jitter
    private var filterX = 0.0
    private var filterY = 0.0
    private var isFilterInitialized = false
    private val headingAlpha = 0.08 // Smooth responsive angular filtering

    // Magnetic declination to convert raw magnetic heading to true North heading
    private var declination = 0f
    private var lastHeadingPublishTime = 0L

    fun setDeclination(dec: Float) {
        declination = dec
    }

    fun start() {
        if (rotationVectorSensor != null) {
            useRotationVector = sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            // Also register magnetometer listener purely to receive accuracy callbacks
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            useRotationVector = false
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    private fun getDisplayRotation(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
        } else {
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val r = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(r, event.values)
            processRotationMatrix(r)
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val current = gravity ?: event.values.clone()
            for (i in current.indices) {
                current[i] = sensorAlpha * event.values[i] + (1.0f - sensorAlpha) * current[i]
            }
            gravity = current
        }

        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val current = geomagnetic ?: event.values.clone()
            for (i in current.indices) {
                current[i] = sensorAlpha * event.values[i] + (1.0f - sensorAlpha) * current[i]
            }
            geomagnetic = current
        }

        val grav = gravity
        val geom = geomagnetic

        if (grav != null && geom != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            val success = SensorManager.getRotationMatrix(r, i, grav, geom)
            if (success) {
                processRotationMatrix(r)
            }
        }
    }

    private fun processRotationMatrix(r: FloatArray) {
        val outR = FloatArray(9)
        val rotation = getDisplayRotation()
        var worldX = SensorManager.AXIS_X
        var worldY = SensorManager.AXIS_Y

        when (rotation) {
            Surface.ROTATION_90 -> {
                worldX = SensorManager.AXIS_Y
                worldY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> {
                worldX = SensorManager.AXIS_MINUS_X
                worldY = SensorManager.AXIS_MINUS_Y
            }
            Surface.ROTATION_270 -> {
                worldX = SensorManager.AXIS_MINUS_Y
                worldY = SensorManager.AXIS_X
            }
        }

        SensorManager.remapCoordinateSystem(r, worldX, worldY, outR)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(outR, orientation)

        // orientation[0] is azimuth (rotation around Z axis), in radians, ranging from -PI to PI
        val rawHeadingRad = orientation[0].toDouble()

        // Compute smoothed average heading in unit circular space to avoid 360° jump boundary discontinuities
        val x = cos(rawHeadingRad)
        val y = sin(rawHeadingRad)
        if (!isFilterInitialized) {
            filterX = x
            filterY = y
            isFilterInitialized = true
        } else {
            filterX = headingAlpha * x + (1.0 - headingAlpha) * filterX
            filterY = headingAlpha * y + (1.0 - headingAlpha) * filterY
        }
        
        val filteredHeadingRad = atan2(filterY, filterX)
        
        // Convert azimuth bearing (pointing clockwise from North) to degrees
        val rawHeadingDeg = (Math.toDegrees(filteredHeadingRad) + 360.0) % 360.0

        // Factor in magnetic declination (True North = Magnetic North + Declination)
        val trueHeading = (rawHeadingDeg + declination + 360.0) % 360.0
        val now = System.currentTimeMillis()
        if (now - lastHeadingPublishTime >= 40) {
            lastHeadingPublishTime = now
            _headingFlow.value = trueHeading.toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            _accuracyFlow.value = accuracy
        }
    }
}
