package com.bose.ar.heading_example

//
//  HomeFragment.kt
//  BoseWearable
//
//  Created by Tambet Ingo on 02/19/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bose.blecore.Logger
import com.bose.blecore.ScanError
import com.bose.bosewearableui.DeviceConnectorActivity
import com.bose.wearable.BoseWearable
import kotlinx.android.synthetic.main.fragment_home.*

@TargetApi(BoseWearable.MINIMUM_SUPPORTED_OS_VERSION)
class HomeFragment : Fragment(R.layout.fragment_home) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchButton.setOnClickListener { onSearchClicked() }
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        autoConnectSwitch.isChecked = prefs.getBoolean(PREF_AUTO_CONNECT_ENABLED, true)
        autoConnectSwitch.setOnCheckedChangeListener { _: CompoundButton?, enabled: Boolean ->
            prefs.edit()
                    .putBoolean(PREF_AUTO_CONNECT_ENABLED, enabled)
                    .apply()
        }
        simulatedDeviceButton.setOnClickListener { onSimulatedDeviceClicked() }
        versionText.text = getString(R.string.version_name, BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_CONNECTOR -> if (resultCode == Activity.RESULT_OK) {
                val deviceAddress = data?.getStringExtra(DeviceConnectorActivity.CONNECTED_DEVICE)
                if (deviceAddress != null) {
                    onDeviceSelected(deviceAddress)
                } else {
                    showNoDeviceError()
                }
            } else if (resultCode == DeviceConnectorActivity.RESULT_SCAN_ERROR) {
                val scanError = data!!.getSerializableExtra(DeviceConnectorActivity.FAILURE_REASON) as ScanError
                showScanError(scanError)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onSearchClicked() {
        val autoConnectTimeout = if (autoConnectSwitch.isChecked) AUTO_CONNECT_TIMEOUT else 0
        val intent = DeviceConnectorActivity.newIntent(requireContext(), autoConnectTimeout,
                MainViewModel.sensorIntent, MainViewModel.gestureIntent)
        startActivityForResult(intent, REQUEST_CODE_CONNECTOR)
    }

    private fun onSimulatedDeviceClicked() {
        val args = Bundle().apply {
            putBoolean(MainFragment.ARG_USE_SIMULATED_DEVICE, true)
        }
        navigateToDeviceFragment(args)
    }

    private fun onDeviceSelected(deviceAddress: String) {
        val args = Bundle().apply {
            putString(MainFragment.ARG_DEVICE_ADDRESS, deviceAddress)
        }
        navigateToDeviceFragment(args)
    }

    private fun navigateToDeviceFragment(args: Bundle) {
        val fragment = MainFragment()
        fragment.arguments = args
        parentFragmentManager
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.content, fragment)
                .commit()
    }

    private fun showNoDeviceError() {
        context?.let {
            Toast.makeText(it, getString(R.string.no_device_selected),
                    Toast.LENGTH_LONG)
                    .show()
        }
    }

    private fun showScanError(error: ScanError) {
        val context = context
        if (context == null) {
            Logger.e(Logger.Topic.DISCOVERY, "Scan failed with $error")
            return
        }
        val reasonStr: String
        reasonStr = when (error) {
            ScanError.ALREADY_STARTED -> context.getString(R.string.scan_error_already_started)
            ScanError.INTERNAL_ERROR -> context.getString(R.string.scan_error_internal)
            ScanError.PERMISSION_DENIED -> context.getString(R.string.scan_error_permission_denied)
            ScanError.BLUETOOTH_DISABLED -> context.getString(R.string.scan_error_bluetooth_disabled)
            ScanError.FEATURE_UNSUPPORTED -> context.getString(R.string.scan_error_feature_unsupported)
            ScanError.APPLICATION_REGISTRATION_FAILED -> context.getString(R.string.scan_error_application_registration_failed)
            ScanError.LOCATION_DISABLED -> context.getString(R.string.scan_error_location_disabled)
            ScanError.UNKNOWN -> context.getString(R.string.scan_error_unknown)
            else -> context.getString(R.string.scan_error_unknown)
        }
        Toast.makeText(context, context.getString(R.string.scan_failed, reasonStr),
                Toast.LENGTH_LONG)
                .show()
    }

    companion object {
        private const val REQUEST_CODE_CONNECTOR = 1
        private const val AUTO_CONNECT_TIMEOUT = 5
        private const val PREF_AUTO_CONNECT_ENABLED = "auto-connect-enabled"
    }
}