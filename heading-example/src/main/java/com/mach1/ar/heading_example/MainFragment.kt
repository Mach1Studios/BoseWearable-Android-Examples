package com.mach1.ar.heading_example

//
//  MainFragment.kt
//  BoseWearable
//
//  Created by Tambet Ingo on 02/19/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bose.ar.heading_example.R
import com.bose.blecore.DeviceException
import com.bose.wearable.wearabledevice.SuspensionState
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_main.*
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
class MainFragment : Fragment(R.layout.fragment_main) {
    private lateinit var deviceAddress: String
    private var useSimulatedDevice = false
    private val viewModel: MainViewModel by viewModels()
    private var progressBar: ProgressBar? = null
    private var snackBar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            deviceAddress = args.getString(ARG_DEVICE_ADDRESS, "")
            useSimulatedDevice = args.getBoolean(ARG_USE_SIMULATED_DEVICE, false)
        }
        require(!(deviceAddress.isBlank() && !useSimulatedDevice))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity: Activity = requireActivity()
        progressBar = activity.findViewById(R.id.progressbar)

        viewModel.busy
                .observe(viewLifecycleOwner, Observer { isBusy -> onBusy(isBusy) })
        viewModel.errors
                .observe(viewLifecycleOwner, Observer { event -> onError(event) })
        viewModel.sensorsSuspended
                .observe(viewLifecycleOwner, Observer { isSuspended -> onSensorsSuspended(isSuspended) })
        viewModel.heading
                .observe(viewLifecycleOwner, Observer { heading -> onHeadingUpdated(heading) })
        viewModel.accuracy
                .observe(viewLifecycleOwner, Observer { accuracy -> onAccuracyUpdated(accuracy) })

        trueNorthSwitch.isChecked = viewModel.useTrueNorth
        trueNorthSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            viewModel.useTrueNorth = isChecked
        }

        if (!deviceAddress.isBlank()) {
            viewModel.selectDevice(deviceAddress)
        } else if (useSimulatedDevice) {
            viewModel.selectSimulatedDevice()
        }
    }

    override fun onDestroy() {
        onBusy(false)
        snackBar?.dismiss()
        snackBar = null
        super.onDestroy()
    }

    private fun onBusy(isBusy: Boolean) {
        progressBar?.visibility = if (isBusy) View.VISIBLE else View.INVISIBLE

        val activity: Activity? = activity
        val window = activity?.window
        if (window != null) {
            if (isBusy) {
                window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        }
    }

    private fun onError(event: Event<DeviceException>) {
        event.get()?.let {
            showError(it.message)
            parentFragmentManager.popBackStack()
        }
    }

    private fun onSensorsSuspended(suspensionState: SuspensionState) {
        val snackbar: Snackbar? = when {
            suspensionState is SuspensionState.Suspended -> {
                Snackbar.make(container, getString(R.string.sensors_suspended, suspensionState.reason),
                        Snackbar.LENGTH_INDEFINITE)
            }
            this.snackBar != null -> Snackbar.make(container, R.string.sensors_resumed, Snackbar.LENGTH_SHORT)
            else -> null
        }
        snackbar?.show()
        this.snackBar = snackbar
    }

    private fun onHeadingUpdated(heading: Double) {
        headingText.text = formatAngle(heading)
    }

    private fun onAccuracyUpdated(accuracy: Double) {
        accuracyText.text = formatAngle(accuracy)
    }

    private fun showError(message: String?) {
        val context = context
        if (context != null) {
            val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } else {
            Log.e(TAG, "Device error: $message")
        }
    }

    private fun formatAngle(angle: Double): String =
        String.format(Locale.US, "%.2f°", angle)

    companion object {
        const val ARG_DEVICE_ADDRESS = "device-address"
        const val ARG_USE_SIMULATED_DEVICE = "use-simulated-device"
        private const val TAG = "MainFragment"
    }
}