package com.mach1.ar.multi_example;

//
//  HomeFragment.java
//  BoseWearable
//
//  Created by Tambet Ingo on 01/04/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.bose.ar.multi_example.R;
import com.bose.blecore.DeviceException;
import com.bose.blecore.Logger;
import com.bose.blecore.ScanError;
import com.bose.bosewearableui.DeviceConnectorActivity;

@RequiresApi(api = Build.VERSION_CODES.O)
public class HomeFragment extends Fragment {
    public static final String ARG_ID = "id";
    private static final int REQUEST_CODE_CONNECTOR = 1;

    private int mId;
    private HomeViewModel mViewModel;
    private Button mSearchButton;
    @Nullable
    private View mProgressBar;

    public interface Listener {
        void onDeviceConnected(final int fragmentId,
                               @NonNull final Bundle args);
    }

    public static HomeFragment newInstance(final int id) {
        final Bundle args = new Bundle();
        args.putInt(ARG_ID, id);

        final HomeFragment fragment = new HomeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        if (args != null) {
            mId = args.getInt(ARG_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSearchButton = view.findViewById(R.id.searchButton);
        mSearchButton.setOnClickListener(v -> onSearchClicked());

        mProgressBar = requireActivity().findViewById(R.id.progressbar);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this)
            .get(HomeViewModel.class);

        mViewModel.errors()
            .observe(this, this::onError);

        mViewModel.state()
            .observe(this, this::onStateChanged);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_CONNECTOR:
                if (resultCode == Activity.RESULT_OK) {
                    final String deviceAddress = data != null ? data.getStringExtra(DeviceConnectorActivity.CONNECTED_DEVICE) : null;
                    if (deviceAddress != null) {
                        onDeviceSelected(deviceAddress);
                    } else {
                        showNoDeviceError();
                    }
                } else if (resultCode == DeviceConnectorActivity.RESULT_SCAN_ERROR) {
                    final ScanError scanError = (ScanError) data.getSerializableExtra(DeviceConnectorActivity.FAILURE_REASON);
                    showScanError(scanError);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void onSearchClicked() {
        showDeviceConnector();
    }

    private void showDeviceConnector() {
        final Intent intent = DeviceConnectorActivity.newIntent(requireContext(), -1,
            MainViewModel.sensorIntent(), MainViewModel.gestureIntent());

        startActivityForResult(intent, REQUEST_CODE_CONNECTOR);
    }

    private void onError(final Event<DeviceException> event) {
        final DeviceException e = event.get();
        if (e != null) {
            showError(e.getMessage());
            showDeviceConnector();
        }
    }

    private void onStateChanged(final ConnectionState state) {
        if (state == ConnectionState.IDLE) {
            busy(false);
        } else if (state instanceof ConnectionState.Connecting) {
            busy(true);
        } else if (state instanceof ConnectionState.Connected) {
            busy(false);
            onDeviceSelected(((ConnectionState.Connected) state).destination().bluetoothDevice().getAddress());
            mViewModel.reset();
        }
    }

    private void busy(final boolean isBusy) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        }

        mSearchButton.setEnabled(!isBusy);
    }

    private void onDeviceSelected(@NonNull final String deviceAddress) {
        final Bundle args = new Bundle();
        args.putString(MainFragment.ARG_DEVICE_ADDRESS, deviceAddress);
        navigateToDeviceFragment(args);
    }

    private void navigateToDeviceFragment(@NonNull final Bundle args) {
        final Activity activity = getActivity();
        if (activity instanceof Listener) {
            ((Listener) activity).onDeviceConnected(mId, args);
        }
    }

    private void showNoDeviceError() {
        final Context context = getContext();
        if (context != null) {
            Toast.makeText(context, getString(R.string.no_device_selected),
                Toast.LENGTH_LONG)
                .show();
        }
    }

    private void showScanError(@NonNull final ScanError error) {
        final Context context = getContext();
        if (context == null) {
            Logger.e(Logger.Topic.DISCOVERY, "Scan failed with " + error);
            return;
        }

        final String reasonStr;
        switch (error) {
            case ALREADY_STARTED:
                reasonStr = context.getString(R.string.scan_error_already_started);
                break;
            case INTERNAL_ERROR:
                reasonStr = context.getString(R.string.scan_error_internal);
                break;
            case PERMISSION_DENIED:
                reasonStr = context.getString(R.string.scan_error_permission_denied);
                break;
            case BLUETOOTH_DISABLED:
                reasonStr = context.getString(R.string.scan_error_bluetooth_disabled);
                break;
            case FEATURE_UNSUPPORTED:
                reasonStr = context.getString(R.string.scan_error_feature_unsupported);
                break;
            case APPLICATION_REGISTRATION_FAILED:
                reasonStr = context.getString(R.string.scan_error_application_registration_failed);
                break;
            case UNKNOWN:
            default:
                reasonStr = context.getString(R.string.scan_error_unknown);
                break;
        }

        showError(context.getString(R.string.scan_failed, reasonStr));
    }

    private void showError(@NonNull final String message) {
        final Context context = getContext();
        if (context == null) {
            Logger.e(Logger.Topic.DISCOVERY, "Scan failed with " + message);
            return;
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG)
            .show();
    }
}
