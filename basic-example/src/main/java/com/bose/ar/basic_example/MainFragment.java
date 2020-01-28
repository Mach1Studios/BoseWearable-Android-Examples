package com.bose.ar.basic_example;

//
//  MainFragment.java
//  BoseWearable
//
//  Created by Tambet Ingo on 12/10/2018.
//  Copyright © 2018 Bose Corporation. All rights reserved.
//

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.bose.blecore.DeviceException;
import com.bose.wearable.sensordata.Quaternion;
import com.bose.wearable.sensordata.SensorValue;
import com.bose.wearable.sensordata.Vector;
import com.google.android.material.snackbar.Snackbar;

import java.net.*;
import java.util.*;

import com.google.android.material.textfield.TextInputEditText;
import com.illposed.osc.*;

import java.util.Locale;

public class MainFragment extends Fragment {
    public static final String ARG_DEVICE_ADDRESS = "device-address";
    public static final String ARG_USE_SIMULATED_DEVICE = "use-simulated-device";
    private static final String TAG = MainFragment.class.getSimpleName();
    private static final Quaternion TRANSLATION_Q = new Quaternion(1, 0, 0, 0);

    private String mDeviceAddress;
    private boolean mUseSimulatedDevice;
    @SuppressWarnings("PMD.SingularField") // Need to keep a reference to it so it does not get GC'd
    private MainViewModel mViewModel;
    private View mParentView;
    @Nullable
    private ProgressBar mProgressBar;
    private TextView mPitch;
    private TextView mRoll;
    private TextView mYaw;
    public float fYaw;
    public float fPitch;
    public float fRoll;
    private float fLastYaw;
    private float fLastPitch;
    private float fLastRoll;

    public EditText mOscAddressInput;
    public EditText mOscPortInput;
    public boolean mYawEnabled = true;
    public boolean mPitchEnabled = false;
    public boolean mRollEnabled = false;

    @Nullable
    private Snackbar mSnackBar;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args != null) {
            mDeviceAddress = args.getString(ARG_DEVICE_ADDRESS);
            mUseSimulatedDevice = args.getBoolean(ARG_USE_SIMULATED_DEVICE, false);
        }

        if (mDeviceAddress == null && !mUseSimulatedDevice) {
            throw new IllegalArgumentException();
        }

        // Start the thread that sends messages
        oscThread.start();
    }

    // This is used to send messages
    private OSCPortOut oscPortOut;
    // init IP and port
    private String myIP = "127.0.0.1";
    private int myPort = 9901;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mParentView = view.findViewById(R.id.container);

        mOscAddressInput = view.findViewById(R.id.oscAddressInput);
        mOscPortInput = view.findViewById(R.id.oscPortInput);
        String myIP = mOscAddressInput.getText().toString();
        int myPort = Integer.parseInt(mOscPortInput.getText().toString());
        mPitch = view.findViewById(R.id.pitch);
        mRoll = view.findViewById(R.id.roll);
        mYaw = view.findViewById(R.id.yaw);

        Switch yawEnable = (Switch) view.findViewById(R.id.yawEnable);
        Switch pitchEnable = (Switch) view.findViewById(R.id.pitchEnable);
        Switch rollEnable = (Switch) view.findViewById(R.id.rollEnable);
        yawEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mYawEnabled = yawEnable.isChecked();
            }
        });
        pitchEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPitchEnabled = pitchEnable.isChecked();
            }
        });
        rollEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mRollEnabled = rollEnable.isChecked();
            }
        });

        mOscAddressInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if(s.length() != 0)
                    addressChanged();
            }
        });

        mOscPortInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if(s.length() != 0)
                    addressChanged();
            }
        });
    }

    public void addressChanged() {
        // the variable OSCPortOut tries to get an instance of OSCPortOut
        // at the address indicated by mOscAddressInput
        try {
            oscPortOut =
                    new OSCPortOut(InetAddress.getByName(mOscAddressInput.getText().toString()), Integer.parseInt(mOscPortInput.getText().toString()));
            // if the oscPort variable fails to be instantiated then sent
            // the error message
        } catch (Exception e) {
            showError("Couldn't set new address");
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Activity activity = requireActivity();
        mProgressBar = activity.findViewById(R.id.progressbar);

        mViewModel = ViewModelProviders.of(this)
            .get(MainViewModel.class);

        mViewModel.busy()
            .observe(this, this::onBusy);

        mViewModel.errors()
            .observe(this, this::onError);

        mViewModel.sensorsSuspended()
            .observe(this, this::onSensorsSuspended);

        mViewModel.accelerometerData()
            .observe(this, this::onAccelerometerData);

        mViewModel.rotationData()
            .observe(this, this::onRotationData);

        if (mDeviceAddress != null) {
            mViewModel.selectDevice(mDeviceAddress);
        } else if (mUseSimulatedDevice) {
            mViewModel.selectSimulatedDevice();
        }
    }

    @Override
    public void onDestroy() {
        onBusy(false);

        final Snackbar snackbar = mSnackBar;
        mSnackBar = null;
        if (snackbar != null) {
            snackbar.dismiss();
        }

        super.onDestroy();
    }

    private void onBusy(final boolean isBusy) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(isBusy ? View.VISIBLE : View.INVISIBLE);
        }

        final Activity activity = getActivity();
        final Window window = activity != null ? activity.getWindow() : null;
        if (window != null) {
            if (isBusy) {
                window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }
        }
    }

    private void onError(@NonNull final Event<DeviceException> event) {
        final DeviceException deviceException = event.get();
        if (deviceException != null) {
            showError(deviceException.getMessage());
            getFragmentManager().popBackStack();
        }
    }

    private void onSensorsSuspended(final boolean isSuspended) {
        final Snackbar snackbar;
        if (isSuspended) {
            snackbar = Snackbar.make(mParentView, R.string.sensors_suspended,
                Snackbar.LENGTH_INDEFINITE);
        } else if (mSnackBar != null) {
            snackbar = Snackbar.make(mParentView, R.string.sensors_resumed,
                Snackbar.LENGTH_SHORT);
        } else {
            snackbar = null;
        }

        if (snackbar != null) {
            snackbar.show();
        }

        mSnackBar = snackbar;
    }

    @SuppressWarnings("PMD.ReplaceVectorWithList") // PMD confuses SDK Vector with java.util.Vector
    private void onAccelerometerData(@NonNull final SensorValue sensorValue) {
        final Vector vector = sensorValue.vector();
    }

    private void onRotationData(@NonNull final SensorValue sensorValue) {
        final Quaternion quaternion = Quaternion.multiply(sensorValue.quaternion(), TRANSLATION_Q);

        if (mYawEnabled) {
            mYaw.setText(formatAngle(-quaternion.zRotation()));
            fYaw = Float.parseFloat(formatDegrees(-quaternion.zRotation()));
            //Log.d("YFormDebug", "Yaw Formatted: " + formatDegrees(-quaternion.zRotation()));
            //Log.d("YFinalDebug", "Yaw Final: " + Float.toString(fYaw));
        } else {
            mYaw.setText("0°");
            fYaw = 0.0f;
        }
        if (mPitchEnabled) {
            mPitch.setText(formatAngle(quaternion.xRotation() % Math.PI/2));
            fPitch = Float.parseFloat(formatDegrees(quaternion.xRotation() % Math.PI/2));
            //Log.d("PFormDebug", "Pitch Formatted: " + formatDegrees(quaternion.xRotation()));
            //Log.d("PFinalDebug", "Pitch Final: " + Float.toString(fPitch));
        } else {
            mPitch.setText("0°");
            fPitch = 0.0f;
        }
        if (mRollEnabled) {
            mRoll.setText(formatAngle(quaternion.yRotation() % Math.PI/2));
            fRoll = Float.parseFloat(formatDegrees(quaternion.yRotation() % Math.PI/2));
            //Log.d("RFormDebug", "Roll Formatted: " + formatDegrees(-quaternion.yRotation()));
            //Log.d("RFinalDebug", "Roll Final: " + Float.toString(fRoll));
        } else {
            mRoll.setText("0°");
            fRoll = 0.0f;
        }
        fLastYaw = fYaw;
        fLastPitch = fPitch;
        fLastRoll = fRoll;
    }

    private void showError(final String message) {
        final Context context = getContext();
        if (context != null) {
            final Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else {
            Log.e(TAG, "Device error: " + message);
        }
    }

    private static String formatValue(final double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static String formatDegrees(final double radians) {
        final double degrees = radians * 180 / Math.PI;
        return String.format(Locale.US,"%.3f", degrees);
    }

    private static String formatAngle(final double radians) {
        final double degrees = radians * 180 / Math.PI;
        return String.format(Locale.US, "%.2f°", degrees);
    }

    // This thread will contain all the code that pertains to OSC
    private Thread oscThread = new Thread() {
        @Override
        public void run() {
            /* The first part of the run() method initializes the OSCPortOut for sending messages.
             */

//            try {
//                // Connect to some IP address and port
//                oscPortOut = new OSCPortOut(InetAddress.getByName(myIP), myPort);
//            } catch(UnknownHostException e) {
//                // Error handling when your IP isn't found
//                return;
//            } catch(Exception e) {
//                // Error handling for any other errors
//                return;
//            }

            /* The second part of the run() method loops infinitely and sends messages every 10
             * milliseconds.
             */
            while (true) {
                if ((oscPortOut != null) && (fLastYaw != fYaw || fLastPitch != fPitch || fLastRoll != fRoll)) {
                    // Creating the message
                    List<Object> orientationOSC = new ArrayList<Object>();
                    orientationOSC.add(fYaw);
                    orientationOSC.add(fPitch);
                    orientationOSC.add(fRoll);

                    /* The version of JavaOSC from the Maven Repository is slightly different from the one
                     * from the download link on the main website at the time of writing this tutorial.
                     *
                     * The Maven Repository version (used here), takes a Collection, which is why we need
                     * Arrays.asList(thingsToSend).
                     *
                     * If you're using the downloadable version for some reason, you should switch the
                     * commented and uncommented lines for message below
                     */
                    OSCMessage message = new OSCMessage("/orientation", orientationOSC);
                    Log.d("OSCMessage", "OSC Address: " + message.getAddress().toString() + " | OSC Contents: " + message.getArguments().toString());

                    try {
                        // Send the messages
                        oscPortOut.send(message);

                        // Pause for half a second
                        sleep(10);
                    } catch (Exception e) {
                        // Error handling for some error
                    }
                }
            }
        }
    };
}
