package com.catenarymaps.catenary

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonSource

private const val HEADING_SMOOTHING = 0.2f
private const val SENSOR_SMOOTHING = 0.15f

@Composable
fun UserLocationCompassGlow(source: GeoJsonSource) {
    val heading = rememberCompassHeading() ?: return

    SymbolLayer(
        id = "user-location-compass-glow",
        source = source,
        iconImage = image(painterResource(R.drawable.compass_glow_radius)),
        // The drawable is 60 dp wide. This produces widths of 18, 36 and 48 dp,
        // approximately three times the location dot's 6, 12 and 16 dp diameters.
        iconSize =
            interpolate(
                type = linear(),
                input = zoom(),
                12 to const(2f),
                15 to const(3f)
            ),
        iconRotate = const(heading),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconPitchAlignment = const(IconPitchAlignment.Map),
        // The narrow end at the bottom of the vector is fixed to the location point.
        iconAnchor = const(SymbolAnchor.Bottom),
        // Do not participate in MapLibre symbol collision or placement decisions.
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        minZoom = 0f,
        visible = true
    )
}

@Composable
private fun rememberCompassHeading(): Float? {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val sensorManager =
        remember(context) {
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
    val magnetometer =
        remember(sensorManager) {
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    val accelerometer =
        remember(sensorManager) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
    val rotationVector =
        remember(sensorManager) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        }

    // A north-referenced heading needs a magnetic-field sensor. If the device
    // cannot supply one, this remains null and the map layer is not composed.
    val hasCompass = magnetometer != null && (rotationVector != null || accelerometer != null)
    var heading by remember(hasCompass) { mutableStateOf<Float?>(null) }

    DisposableEffect(
        lifecycleOwner,
        sensorManager,
        magnetometer,
        accelerometer,
        rotationVector,
        view
    ) {
        if (!hasCompass) {
            heading = null
            onDispose {}
        } else {
            val rotationMatrix = FloatArray(9)
            val adjustedMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val gravity = FloatArray(3)
            val magneticField = FloatArray(3)
            var gravityReady = false
            var magneticFieldReady = false
            var registered = false

            fun publishHeading(matrix: FloatArray) {
                val (xAxis, yAxis) =
                    when (view.display?.rotation ?: Surface.ROTATION_0) {
                        Surface.ROTATION_90 ->
                            SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        Surface.ROTATION_180 ->
                            SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        Surface.ROTATION_270 ->
                            SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }

                if (!SensorManager.remapCoordinateSystem(
                        matrix,
                        xAxis,
                        yAxis,
                        adjustedMatrix
                    )
                ) {
                    return
                }

                SensorManager.getOrientation(adjustedMatrix, orientation)
                val next =
                    ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                heading = smoothHeading(heading, next)
            }

            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        when (event.sensor.type) {
                            Sensor.TYPE_ROTATION_VECTOR,
                            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                                SensorManager.getRotationMatrixFromVector(
                                    rotationMatrix,
                                    event.values
                                )
                                publishHeading(rotationMatrix)
                            }

                            Sensor.TYPE_ACCELEROMETER -> {
                                gravityReady =
                                    smoothVector(gravity, event.values, gravityReady)
                            }

                            Sensor.TYPE_MAGNETIC_FIELD -> {
                                magneticFieldReady =
                                    smoothVector(magneticField, event.values, magneticFieldReady)
                            }
                        }

                        if (rotationVector == null && gravityReady && magneticFieldReady) {
                            if (SensorManager.getRotationMatrix(
                                    rotationMatrix,
                                    null,
                                    gravity,
                                    magneticField
                                )
                            ) {
                                publishHeading(rotationMatrix)
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD &&
                            accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                        ) {
                            heading = null
                        }
                    }
                }

            fun registerSensors() {
                if (registered) return

                registered =
                    if (rotationVector != null) {
                        sensorManager.registerListener(
                            listener,
                            rotationVector,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    } else {
                        val accelerationSensor = accelerometer
                        val magneticSensor = magnetometer
                        if (accelerationSensor == null || magneticSensor == null) {
                            false
                        } else {
                            val accelerationRegistered =
                                sensorManager.registerListener(
                                    listener,
                                    accelerationSensor,
                                    SensorManager.SENSOR_DELAY_UI
                                )
                            val magneticRegistered =
                                sensorManager.registerListener(
                                    listener,
                                    magneticSensor,
                                    SensorManager.SENSOR_DELAY_UI
                                )
                            if (!accelerationRegistered || !magneticRegistered) {
                                sensorManager.unregisterListener(listener)
                                false
                            } else {
                                true
                            }
                        }
                    }

                if (!registered) heading = null
            }

            fun unregisterSensors() {
                if (registered) {
                    sensorManager.unregisterListener(listener)
                    registered = false
                }
            }

            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> registerSensors()
                        Lifecycle.Event.ON_PAUSE -> {
                            unregisterSensors()
                            heading = null
                        }
                        else -> Unit
                    }
                }

            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                registerSensors()
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                unregisterSensors()
            }
        }
    }

    return heading
}

private fun smoothVector(
    target: FloatArray,
    values: FloatArray,
    initialized: Boolean
): Boolean {
    for (index in 0..2) {
        target[index] =
            if (initialized) {
                target[index] + SENSOR_SMOOTHING * (values[index] - target[index])
            } else {
                values[index]
            }
    }
    return true
}

private fun smoothHeading(previous: Float?, next: Float): Float {
    if (previous == null) return next

    val shortestDelta = ((next - previous + 540f) % 360f) - 180f
    return (previous + shortestDelta * HEADING_SMOOTHING + 360f) % 360f
}
