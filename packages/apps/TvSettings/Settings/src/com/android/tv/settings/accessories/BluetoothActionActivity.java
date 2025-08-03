/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.KEY_BLUETOOTH_TOGGLE;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.KEY_CONNECT;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.KEY_DISCONNECT;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.KEY_EXTRAS_DEVICE;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.KEY_FORGET;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.KEY_RENAME;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceProvider.YES;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.DIRECTION_BACK;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.EXTRAS_DIRECTION;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

/**
 * The {@Activity} for handling confirmation UI of Bluetooth-related actions such as connection and
 * renaming.
 */
public class BluetoothActionActivity extends Activity implements BluetoothActionFragment.Listener {

    private boolean mBtDeviceServiceBound;
    private BluetoothDevice mDevice;
    private BluetoothDevicesService.LocalBinder mBtDeviceServiceBinder;

    private final ServiceConnection mBtDeviceServiceConnection = new SimplifiedConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBtDeviceServiceBinder = (BluetoothDevicesService.LocalBinder) service;
            mBtDeviceServiceBound = true;
        }

        @Override
        protected void cleanUp() {
            mBtDeviceServiceBound = false;
            mBtDeviceServiceBinder = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDevice = getIntent().getParcelableExtra(KEY_EXTRAS_DEVICE);
        bindService(new Intent(this, AccessoryUtils.getBluetoothDeviceServiceClass()),
                mBtDeviceServiceConnection, Context.BIND_AUTO_CREATE);
        BluetoothActionFragment responseFragment = new BluetoothActionFragment();
        responseFragment.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().add(android.R.id.content, responseFragment)
                .commit();
    }

    @Override
    public void onChoice(String key, int choice) {
        BluetoothDeviceProvider provider = mBtDeviceServiceBinder;
        Intent i = new Intent();

        if (provider == null) {
            return;
        }
        if (key == null) {
            setResult(RESULT_OK, i);
            finish();
        }
        switch (key) {
            case KEY_BLUETOOTH_TOGGLE:
                if (choice == YES) {
                    if (AccessoryUtils.getDefaultBluetoothAdapter() != null) {
                        AccessoryUtils.getDefaultBluetoothAdapter().disable();
                    }
                    i.putExtra(EXTRAS_DIRECTION, DIRECTION_BACK);
                }
                break;
            case KEY_CONNECT:
                if (choice == YES) {
                    provider.connectDevice(mDevice);
                    i.putExtra(EXTRAS_DIRECTION, DIRECTION_BACK);
                }
                break;
            case KEY_DISCONNECT:
                if (choice == YES) {
                    provider.disconnectDevice(mDevice);
                    i.putExtra(EXTRAS_DIRECTION, DIRECTION_BACK);
                }
                break;
            case KEY_FORGET:
                if (choice == YES) {
                    provider.forgetDevice(mDevice);
                    i.putExtra(EXTRAS_DIRECTION, DIRECTION_BACK);
                }
                break;
        }
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void onText(String key, String text) {
        BluetoothDeviceProvider provider = mBtDeviceServiceBinder;
        if (KEY_RENAME.equals(key)) {
            if (mDevice != null) {
                provider.renameDevice(mDevice, text);
            }
        }
        Intent i = new Intent();
        i.putExtra(EXTRAS_DIRECTION, DIRECTION_BACK);
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void onDestroy() {
        if (mBtDeviceServiceBound) {
            unbindService(mBtDeviceServiceConnection);
        }
        super.onDestroy();
    }
}
