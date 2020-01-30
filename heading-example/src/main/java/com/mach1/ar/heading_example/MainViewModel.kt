package com.mach1.ar.heading_example

//
//  MainViewMode1.kt
//  BoseWearable
//
//  Created by Tambet Ingo on 11/20/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

import android.app.Application
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bose.blecore.DeviceException
import com.bose.blecore.DiscoveredDevice
import com.bose.blecore.ScopedSession
import com.bose.blecore.scopedSession
import com.bose.wearable.BoseWearable
import com.bose.wearable.BoseWearableException
import com.bose.wearable.sensordata.GestureIntent
import com.bose.wearable.sensordata.Quaternion
import com.bose.wearable.sensordata.SensorIntent
import com.bose.wearable.sensordata.times
import com.bose.wearable.services.wearablesensor.SamplePeriod
import com.bose.wearable.services.wearablesensor.SensorType
import com.bose.wearable.wearabledevice.*
import com.google.android.gms.location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val boseWearable = BoseWearable.getInstance()
    private val locationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(app)
    private val _busy = MutableLiveData<Boolean>()
    private val _errors = MutableLiveData<Event<DeviceException>>()
    private val _sensorsSuspended = MutableLiveData<SuspensionState>()
    private val _heading = MutableLiveData<Double>()
    private val _accuracy = MutableLiveData<Double>()

    private var selectedDevice: DiscoveredDevice? = null
    private var simulatedDevice = false
    private var session: ScopedSession? = null
    private var location: Location? = null
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            result?.let { location = result.lastLocation }
        }
    }

    val busy: LiveData<Boolean>
        get() = _busy

    val errors: LiveData<Event<DeviceException>>
        get() = _errors

    val sensorsSuspended: LiveData<SuspensionState>
        get() = _sensorsSuspended

    val heading: LiveData<Double>
        get() = _heading

    val accuracy: LiveData<Double>
        get() = _accuracy

    var useTrueNorth: Boolean = true

    init {
        val locationRequest = LocationRequest.create().apply {
            interval = 10_000
            fastestInterval = 5_000
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }

        locationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        locationClient.lastLocation.addOnSuccessListener { location = it }
    }

    override fun onCleared() {
        locationClient.removeLocationUpdates(locationCallback)
        stopSession()
        super.onCleared()
    }

    fun selectDevice(deviceAddress: String) {
        val btManager = boseWearable.bluetoothManager()
        val device = btManager.deviceByAddress(deviceAddress)

        if (sameDevice(selectedDevice, device)) {
            return
        }

        stopSession()

        selectedDevice = device
        simulatedDevice = false

        if (device != null) {
            onSessionCreated(btManager.scopedSession(device, context = viewModelScope.coroutineContext))
        }
    }

    fun selectSimulatedDevice() {
        stopSession()

        selectedDevice = null
        simulatedDevice = true

        val session = ScopedSession(boseWearable.createSimulatedSession(),
                context = viewModelScope.coroutineContext)
        onSessionCreated(session)
    }

    private fun onSessionCreated(session: ScopedSession) {
        this.session = session

        session.coroutineContext[Job]!!.invokeOnCompletion { handler ->
            // Session was closed.
            selectedDevice = null
            simulatedDevice = false
            _busy.value = false
            if (handler?.cause is DeviceException) {
                // It was closed because of an error.
                _errors.value = Event(handler.cause as DeviceException)
            }
        }

        monitorSuspension(session)
        monitorSensors(session)
    }

    private fun monitorSuspension(session: ScopedSession) {
        val device = session.device() as WearableDevice
        var sensorsConfigured = false

        session.launch {
            device.monitorSuspension()
                    .collect { suspensionState ->
                        _sensorsSuspended.value = suspensionState

                        if (!sensorsConfigured && suspensionState is SuspensionState.Active) {
                            configureSensors(device)
                            sensorsConfigured = true
                        }
                    }
        }
    }

    private suspend fun configureSensors(device: WearableDevice) {
        val sensorConf = device.sensorConfiguration()
                .disableAll()
                .enableSensor(SensorType.ROTATION_VECTOR, SAMPLE_PERIOD)

        _busy.value = true

        try {
            device.configureSensors(sensorConf)
        } catch (e: BoseWearableException) {
            _errors.value = Event(e)
        } finally {
            _busy.value = false
        }
    }

    private fun monitorSensors(session: ScopedSession) {
        session.launch {
            val device = session.device() as WearableDevice
            // Suspend the coroutine until it is cancelled
            device.monitorSensors()
                    .filter { it.sensorType() == SensorType.ROTATION_VECTOR }
                    .collect { sensorValue ->
                        val quaternion = sensorValue.quaternion()!! * TRANSLATION_Q
                        var heading = Math.toDegrees(-quaternion.zRotation())
                        if (useTrueNorth) {
                            heading = trueHeading(heading)
                        }
                        _heading.value = heading
                        _accuracy.value = Math.toDegrees(sensorValue.quaternionAccuracy()!!.estimatedAccuracy())
                    }
        }
    }

    private fun stopSession() {
        val session = this.session ?: return

        this.session = null
        _sensorsSuspended.value = SuspensionState.Active
        session.close()
    }

    private fun trueHeading(degrees: Double): Double {
        val location = this.location ?: return degrees

        val geoField = GeomagneticField(location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis())

        var heading = degrees + geoField.declination

        // Normalize the value
        if (heading <= -180) {
            heading += 360.0
        }
        if (heading > 180) {
            heading -= 360.0
        }

        return heading
    }

    companion object {
        private val SAMPLE_PERIOD = SamplePeriod._20_MS
        val sensorIntent = SensorIntent(setOf(SensorType.ROTATION_VECTOR), setOf(SAMPLE_PERIOD))
        val gestureIntent: GestureIntent = GestureIntent.EMPTY
        private val TRANSLATION_Q = Quaternion(1.0, 0.0, 0.0, 0.0)

        private fun sameDevice(a: DiscoveredDevice?, b: DiscoveredDevice?): Boolean {
            if (a == null && b != null) {
                return false
            }
            if (b == null && a != null) {
                return false
            }
            if (a == null) {
                return true
            }
            val aAddress = a.bluetoothDevice().address
            val bAddress = b!!.bluetoothDevice().address
            return aAddress == bAddress
        }
    }
}
