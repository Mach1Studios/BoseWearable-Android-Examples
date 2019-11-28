package com.bose.ar.multi_example;

//
//  HomeViewModel.java
//  BoseWearable
//
//  Created by Tambet Ingo on 09/18/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bose.blecore.DeviceException;
import com.bose.blecore.DiscoveredDevice;
import com.bose.blecore.Session;
import com.bose.blecore.SessionDelegate;
import com.bose.wearable.BoseWearable;

import java.util.concurrent.TimeUnit;

public class HomeViewModel extends ViewModel {
    @NonNull
    private final Handler mHandler = new Handler();
    @NonNull
    private final MutableLiveData<ConnectionState> mState = new MutableLiveData<>();
    @NonNull
    private final MutableLiveData<Event<DeviceException>> mErrors = new MutableLiveData<>();

    public HomeViewModel() {
        mState.setValue(ConnectionState.IDLE);
    }

    public LiveData<ConnectionState> state() {
        return mState;
    }

    public LiveData<Event<DeviceException>> errors() {
        return mErrors;
    }

    public boolean hasPreviouslyConnectedDevice() {
        return previouslyConnectedDevice() != null;
    }

    @Override
    protected void onCleared() {
        final ConnectionState state = mState.getValue();
        final Session session;
        if (state instanceof ConnectionState.Connected) {
            session = ((ConnectionState.Connected) state).session();
        } else if (state instanceof ConnectionState.Connecting) {
            session = ((ConnectionState.Connecting) state).session();
        } else {
            session = null;
        }

        if (session != null) {
            session.close();
        }

        super.onCleared();
    }

    public void reset() {
        mState.setValue(ConnectionState.IDLE);
    }

    public void reconnect(final long timeout) {
        final DiscoveredDevice device = previouslyConnectedDevice();
        if (device == null) {
            throw new IllegalStateException();
        }

        final ConnectionState state = mState.getValue();
        if (state != ConnectionState.IDLE) {
            throw new IllegalStateException();
        }

        final Session session = BoseWearable.getInstance()
            .bluetoothManager()
            .session(device);

        final Runnable cancelRunnable = () -> {
            session.close();
            mErrors.setValue(new Event<>(DeviceException.cancelled()));
            mState.setValue(ConnectionState.IDLE);
        };

        session.callback(new SessionDelegate() {
            @Override
            public void sessionConnected(@NonNull Session session) {
                mHandler.removeCallbacks(cancelRunnable);
                mState.setValue(new ConnectionState.Connected(device, session));
            }

            @Override
            public void sessionClosed(int statusCode) {
                mHandler.removeCallbacks(cancelRunnable);
                mState.setValue(ConnectionState.IDLE);
                if (statusCode != 0) {
                    mErrors.setValue(new Event<>(DeviceException.disconnectedByDevice()));
                }
            }

            @Override
            public void sessionError(@NonNull DeviceException exception) {
                mHandler.removeCallbacks(cancelRunnable);
                mState.setValue(ConnectionState.IDLE);
                mErrors.setValue(new Event<>(exception));
            }
        });

        mState.setValue(new ConnectionState.Connecting(device, session));
        session.open();
        mHandler.postDelayed(cancelRunnable, TimeUnit.SECONDS.toMillis(timeout));
    }

    private DiscoveredDevice previouslyConnectedDevice() {
        return BoseWearable.getInstance()
            .bluetoothManager()
            .mostRecentlyConnectedDevice();
    }
}
