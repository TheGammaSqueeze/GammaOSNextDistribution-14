/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.tv.btservices.settings;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.tv.btservices.BluetoothDeviceService;
import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.PowerUtils;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.SettingsUtils.SettingsPanelAnimation;
import com.google.android.tv.btservices.SimplifiedConnection;
import com.google.android.tv.btservices.pairing.BluetoothPairingService;

import java.util.ArrayList;
import java.util.List;

public class ConnectedDevicesSettingsActivity extends Activity
        implements BluetoothDeviceProvider.Listener, ConnectedDevicesPreferenceFragment.Provider,
        BluetoothDevicePreferenceFragment.ServiceProvider {

    private static final String TAG = "Atv.ConDevsActivity";

    private SettingsPanelAnimation mPanelAnimation;

    private boolean mBtDeviceServiceBound;
    private BluetoothDeviceService.LocalBinder mBtDeviceServiceBinder;
    private boolean mBluetoothPairingServiceBound;
    private BluetoothPairingService.LocalBinder mBluetoothPairingServiceBinder;

    private ConnectedDevicesPreferenceFragment mDevicesPreferenceFragment =
            ConnectedDevicesPreferenceFragment.newInstance();

    private final SettingsPanelAnimation.FragmentFactory
            mFragmentFactory = () -> SettingsFragment.newInstance(mDevicesPreferenceFragment);

    private static final String TAG_FRAGMENT = "connectedDevicesSettingsFragment";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mVisible = false;

    private final ServiceConnection mBtDeviceServiceConnection = new SimplifiedConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBtDeviceServiceBinder = (BluetoothDeviceService.LocalBinder) service;
            mBtDeviceServiceBound = true;
            mBtDeviceServiceBinder.addListener(ConnectedDevicesSettingsActivity.this);
            mHandler.post(ConnectedDevicesSettingsActivity.this::updateConnectedDevices);
            mHandler.post(ConnectedDevicesSettingsActivity.this::updatePairedDevices);
        }

        @Override
        protected void cleanUp() {
            if (mBtDeviceServiceBinder != null) {
                mBtDeviceServiceBinder.removeListener(ConnectedDevicesSettingsActivity.this);
            }
            mBtDeviceServiceBound = false;
        }
    };

    private final ServiceConnection
            mBluetoothPairingServiceConnection = new SimplifiedConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBluetoothPairingServiceBinder = (BluetoothPairingService.LocalBinder) service;
            mBluetoothPairingServiceBound = true;
        }

        @Override
        protected void cleanUp() {
            mBluetoothPairingServiceBound = false;
        }
    };

    // BluetoothDeviceProvider.Listener implementation
    @Override
    public void onDeviceUpdated(BluetoothDevice device) {
        mHandler.post(this::updateConnectedDevices);
        mHandler.post(this::updatePairedDevices);
    }

    // ConnectedDevicesPreferenceFragment.Provider implementation
    @Override
    public boolean isCecEnabled() {
        return PowerUtils.isCecControlEnabled(this);
    }

    // ConnectedDevicesPreferenceFragment.Provider implementation
    @Override
    public List<BluetoothDevice> getBluetoothDevices() {
        if (mBtDeviceServiceBinder != null) {
            return mBtDeviceServiceBinder.getDevices();
        }
        return new ArrayList<>();
    }

    // ConnectedDevicesPreferenceFragment.Provider implementation
    @Override
    public BluetoothDeviceProvider getBluetoothDeviceProvider() {
        return mBtDeviceServiceBinder;
    }

    // BluetoothDevicePreferenceFragment.ServiceProvider implementation
    @Override
    public BluetoothPairingService.LocalBinder getBluetoothPairingServiceBinder() {
        return mBluetoothPairingServiceBinder;
    }

    private void updateConnectedDevices() {
        if (isVisible()) {
            mDevicesPreferenceFragment.updateConnectedDevices();
        }
    }

    private void updatePairedDevices() {
        if (isVisible()) {
            mDevicesPreferenceFragment.updatePairedDevices();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        final ViewGroup viewRoot = findViewById(android.R.id.content);
        // Change title for a11y context.
        setTitle(getString(R.string.connected_devices_pref_title));
        viewRoot.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);

        mPanelAnimation = new SettingsPanelAnimation(
                getFragmentManager(),
                TAG_FRAGMENT,
                viewRoot,
                mFragmentFactory,
                getWindow()
        );
        mPanelAnimation.transitionIn();

        bindService(new Intent(this, BluetoothUtils.getBluetoothDeviceServiceClass(this)),
                mBtDeviceServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private boolean isVisible() {
        return mVisible;
    }

    @Override
    public void onDestroy() {
        if (mBtDeviceServiceBound) {
            mBtDeviceServiceBinder.removeListener(this);
            unbindService(mBtDeviceServiceConnection);
        }
        if (mBluetoothPairingServiceBound) {
            unbindService(mBluetoothPairingServiceConnection);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        mPanelAnimation.transitionOut(this::finish);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // To make sure that extras bundled in the intent are up to date.
        this.setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVisible = true;
    }


    @Override
    public void onPause() {
        super.onPause();
        mVisible = false;
    }
}
