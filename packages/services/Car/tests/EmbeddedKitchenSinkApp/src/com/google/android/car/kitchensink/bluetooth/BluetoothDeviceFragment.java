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

package com.google.android.car.kitchensink.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.Set;
import java.util.concurrent.Executor;

public class BluetoothDeviceFragment extends Fragment {
    private static final String TAG = "CAR.BLUETOOTH.KS";
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mPickedDevice;
    Executor mExecutor;
    BluetoothDeviceTypeChecker mDeviceTypeChecker;

    TextView mPickedDeviceText;
    Button mDevicePicker;
    TableLayout mTableLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bluetooth_devices, container, false);
        mDeviceTypeChecker = new BluetoothDeviceTypeChecker(getContext(), true);
        mDevicePicker = v.findViewById(R.id.bluetooth_pick_device);
        mTableLayout = v.findViewById(R.id.PairedDeviceTable);
        mExecutor = new ThreadPerTaskExecutor();

        // Pick a bluetooth device
        mDevicePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchDevicePicker();
            }
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAllDevices();
    }

    void launchDevicePicker() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        getContext().registerReceiver(mPickerReceiver, filter);

        Intent intent = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        getContext().startActivity(intent);
    }

    void checkAllDevices() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth Adapter not available");
            return;
        }
        mTableLayout.removeAllViews();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        Context context = getContext();
        for (BluetoothDevice device : bondedDevices) {
            TableRow row = new TableRow(context);
            TextView deviceName = new TextView(context);
            deviceName.setText(device.getName());
            TextView deviceType = new TextView(context);
            deviceType.setText(Boolean.toString(mDeviceTypeChecker.isIapDevice(device)));
            row.addView(deviceName);
            row.addView(deviceType);
            mTableLayout.addView(row);
        }
    }

    private void addDeviceToTable(BluetoothDevice device, String value) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Context context = getContext();
                TableRow row = new TableRow(context);
                TextView deviceName = new TextView(context);
                TextView deviceValue = new TextView(context);
                deviceName.setText(device.getName());
                deviceValue.setText(value);
                row.addView(deviceName);
                row.addView(deviceValue);
                mTableLayout.addView(row);
            }
        });
    }

    private final BroadcastReceiver mPickerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "mPickerReceiver got " + action);

            if (BluetoothDevicePicker.ACTION_DEVICE_SELECTED.equals(action)) {
                final BluetoothDevice device =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.v(TAG, "mPickerReceiver got " + device);
                if (device == null) {
                    Toast.makeText(getContext(), "No device selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        addDeviceToTable(device,
                                Boolean.toString(mDeviceTypeChecker.isIapDevice(device)));
                        Log.w(TAG, "Is iAP" + mDeviceTypeChecker.isIapDevice(device));
                    }
                });
                Log.w(TAG, "Dispatched");
                getContext().unregisterReceiver(mPickerReceiver);
            }
        }
    };

    private class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }
}
