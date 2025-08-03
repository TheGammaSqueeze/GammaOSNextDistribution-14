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

package com.google.android.tv.btservices.pairing;

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

import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.SettingsUtils;
import com.google.android.tv.btservices.SimplifiedConnection;
import com.google.android.tv.btservices.settings.SettingsFragment;

public class BluetoothScannerActivity
        extends Activity implements BluetoothPairingService.ScanningListener,
                                    BluetoothPairingService.PairingListener,
                                    BluetoothScannerFragment.PairingHandler {

    private static final String TAG = "Atv.BtScannerActivity";
    private static final String TAG_FRAGMENT = "bluetoothScannerFragment";

    private SettingsUtils.SettingsPanelAnimation mPanelAnimation;
    private SettingsUtils.SettingsPanelAnimation.FragmentFactory mFragmentFactory;
    private BluetoothScannerFragment mBluetoothScannerFragment;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private BluetoothPairingService.LocalBinder mBluetoothPairingServiceBinder;
    private boolean mBluetoothPairingServiceBound = false;

    private final ServiceConnection mBluetoothPairingServiceConnection =
            new SimplifiedConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    mBluetoothPairingServiceBinder = (BluetoothPairingService.LocalBinder)service;
                    mBluetoothPairingServiceBinder.addPairingListener(
                            BluetoothScannerActivity.this);
                    mBluetoothPairingServiceBinder.addScanningListener(
                            BluetoothScannerActivity.this);
                    mBluetoothPairingServiceBound = true;
                }

                @Override
                protected void cleanUp() {
                    mBluetoothPairingServiceBound = false;
                }
            };

    private void startScanning() {
        if (mBluetoothPairingServiceBinder != null) {
            mBluetoothPairingServiceBinder.addPairingListener(BluetoothScannerActivity.this);
            mBluetoothPairingServiceBinder.addScanningListener(BluetoothScannerActivity.this);
        }
    }

    private void stopScanning() {
        if (mBluetoothPairingServiceBinder != null) {
            mBluetoothPairingServiceBinder.removePairingListener(BluetoothScannerActivity.this);
            mBluetoothPairingServiceBinder.removeScanningListener(BluetoothScannerActivity.this);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        mBluetoothScannerFragment = BluetoothScannerFragment.newInstance();
        mFragmentFactory = () -> SettingsFragment.newInstance(mBluetoothScannerFragment);
        bindService(new Intent(this, BluetoothPairingService.class),
                mBluetoothPairingServiceConnection, Context.BIND_AUTO_CREATE);
        final ViewGroup viewRoot = findViewById(android.R.id.content);
        mPanelAnimation = new SettingsUtils.SettingsPanelAnimation(
                getFragmentManager(),
                TAG_FRAGMENT,
                viewRoot,
                mFragmentFactory,
                getWindow()
        );
        mPanelAnimation.transitionIn();
    }

    @Override
    public void onResume() {
        super.onResume();
        startScanning();
        if (mBluetoothPairingServiceBinder != null) {
            mBluetoothPairingServiceBinder.restartScanning();
        }
    }

    @Override
    public void onPause() {
        stopScanning();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mBluetoothPairingServiceBound) {
            unbindService(mBluetoothPairingServiceConnection);
        }
        super.onDestroy();
    }

    private static boolean isMatchingDevice(BluetoothDevice device) {
        return device != null && (BluetoothUtils.isRemoteClass(device)
                || BluetoothUtils.isBluetoothHeadset(device));
    }

    /** BluetoothScannerFragment.PairingHandler implementation */
    @Override
    public void pairDevice(BluetoothDevice device) {
        mBluetoothPairingServiceBinder.pairDevice(device);
    }

    /** BluetoothPairingService.ScanningListener implementation */
    @Override
    public void updateScanning(boolean isScanning) {
        mHandler.post(() -> mBluetoothScannerFragment.showProgress(isScanning));
    }

    /** BluetoothPairingService.ScanningListener implementation */
    @Override
    public void updateDevice(BluetoothDevice device, int status) {
        if (!isMatchingDevice(device)) return;
        mHandler.post(() -> mBluetoothScannerFragment.updateDevice(device, status));
    }

    /** BluetoothPairingService.PairingListener implementation */
    @Override
    public void updatePairingStatus(BluetoothDevice device, int status) {
        if (!isMatchingDevice(device)) return;
        mHandler.post(() -> mBluetoothScannerFragment.updatePairingStatus(device, status));
    }
}
