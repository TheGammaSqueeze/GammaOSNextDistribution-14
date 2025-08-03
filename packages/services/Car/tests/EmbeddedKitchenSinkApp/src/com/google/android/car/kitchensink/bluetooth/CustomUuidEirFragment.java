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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CustomUuidEirFragment extends Fragment {
    private static final String TAG = "CAR.BLUETOOTH.KS";

    private static final int DISCOVERABLE_TIMEOUT_TWO_MINUTES = 120_000;
    private static final int ADAPTER_ON_TIMEOUT_MS = 1_000;

    BluetoothAdapter mAdapter;
    int mScanModeNotDiscoverable;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if ((state == BluetoothAdapter.STATE_ON)
                        || (state == BluetoothAdapter.STATE_OFF)) {
                    refreshUi();
                    if (state == BluetoothAdapter.STATE_ON) {
                        mAdapterOnLock.lock();
                        mAdapterOnCondition.signal();
                        mAdapterOnLock.unlock();
                    }
                }
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                refreshUi();
            }
        }
    };

    // Some functions require the Bluetooth adapter to be on, and will enable if it isn't already.
    // However, there is some latency for the adapter to reach the ON state.
    private final ReentrantLock mAdapterOnLock = new ReentrantLock();
    private final Condition mAdapterOnCondition = mAdapterOnLock.newCondition();

    Switch mAdvertisingToggle;
    Switch mBtAdapterToggle;
    Button mResetUuidDefaults;
    List<EditText> mUuidEditTexts;
    List<Switch> mUuidSwitches;

    private static final String UUID_SERVICE_NAME = "Custom UUID test";
    private static final String UUID1_DEFAULT = "01234567-89ab-cdef-0123-456789abcdef";
    private static final String UUID2_DEFAULT = "fedcba98-7654-3210-fedc-ba9876543210";
    private static final String UUID3_DEFAULT = new StringBuilder(String.format("%032x",
            new BigInteger(1, "Hello World! Hi!".getBytes()))).insert(20, "-").insert(16, "-")
            .insert(12, "-").insert(8, "-").toString();
    private static final String UUID4_DEFAULT = new StringBuilder(String.format("%032x",
            new BigInteger(1, "Foo!Bar!Baz!Fum!".getBytes()))).insert(20, "-").insert(16, "-")
            .insert(12, "-").insert(8, "-").toString();
    private static final List<String> DEFAULT_UUIDS =
            Arrays.asList(UUID1_DEFAULT, UUID2_DEFAULT, UUID3_DEFAULT, UUID4_DEFAULT);

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bluetooth_uuid_eir, container, false);

        mAdvertisingToggle = (Switch) v.findViewById(R.id.advertising_toggle);
        mBtAdapterToggle = (Switch) v.findViewById(R.id.bt_adapter_toggle);

        if (!BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_CONNECT)
                || !BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_SCAN)) {
            BluetoothPermissionChecker.requestMultiplePermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN},
                    this,
                    () -> {
                        mAdvertisingToggle.setEnabled(true);
                        mBtAdapterToggle.setEnabled(true);
                    },
                    () -> {
                        mAdvertisingToggle.setEnabled(false);
                        mBtAdapterToggle.setEnabled(false);
                        Toast.makeText(getContext(),
                                "UUID test cannot run without Bluetooth permissions. "
                                        + "(You can change permissions in Settings.)",
                                Toast.LENGTH_SHORT).show();
                    }
            );
        }

        mResetUuidDefaults = (Button) v.findViewById(R.id.reset_uuid_defaults);

        mUuidEditTexts = Arrays.asList(
                (EditText) v.findViewById(R.id.uuid1),
                (EditText) v.findViewById(R.id.uuid2),
                (EditText) v.findViewById(R.id.uuid3),
                (EditText) v.findViewById(R.id.uuid4));
        mUuidSwitches = Arrays.asList(
                (Switch) v.findViewById(R.id.uuid1_toggle),
                (Switch) v.findViewById(R.id.uuid2_toggle),
                (Switch) v.findViewById(R.id.uuid3_toggle),
                (Switch) v.findViewById(R.id.uuid4_toggle));

        mAdvertisingToggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setAdvertisingState(isChecked));

        mBtAdapterToggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setAdapterState(isChecked));

        mResetUuidDefaults.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setUuidsToDefault();
            }
        });

        // Associating each {@link EditText} used for entering UUIDs with its corresponding
        // {@link Switch}.
        Iterator<EditText> textIter = mUuidEditTexts.iterator();
        Iterator<Switch> switchIter = mUuidSwitches.iterator();
        Switch toggle;
        while (textIter.hasNext() && switchIter.hasNext()) {
            toggle = switchIter.next();
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                EditText mUuidText = textIter.next();
                BluetoothServerSocket mSocket;
                /**
                 * When the API {@link BluetoothAdapter#listenUsingRfcommWithServiceRecord} is
                 * called, the 128-bit UUID is added to the EIR. When the socket created by that
                 * API is closed, the UUID is deleted from the EIR.
                 */
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mSocket = setUuidInEir(buttonView, isChecked, mUuidText, mSocket);
                }
            });
        }
        // Disable each UUID's {@link Switch} if Adapter or Advertising toggles are not enabled,
        // which indicates Bluetooth permissions have not been granted.
        if (!mAdvertisingToggle.isEnabled() || !mBtAdapterToggle.isEnabled()) {
            while (switchIter.hasNext()) {
                toggle = switchIter.next();
                toggle.setEnabled(false);
            }
        }

        setUuidsToDefault();

        BluetoothManager bluetoothManager =
                Objects.requireNonNull(getContext().getSystemService(BluetoothManager.class));
        mAdapter = Objects.requireNonNull(bluetoothManager.getAdapter());

        // We don't know if "OFF" is {@link BluetoothAdapter#SCAN_MODE_NONE}
        // or {@link BluetoothAdapter#SCAN_MODE_CONNECTABLE}. If the current scan mode is
        // {@link BluetoothAdapter#SCAN_MODE_CONNECTABLE_DISCOVERABLE}, then we'll set "OFF" to
        // {@link BluetoothAdapter#SCAN_MODE_CONNECTABLE}.
        if (BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_SCAN)) {
            mScanModeNotDiscoverable = mAdapter.getScanMode();
            Log.d(TAG, "Original scan mode was: " + mScanModeNotDiscoverable + ", "
                    + scanModeToText(mScanModeNotDiscoverable));
        }
        if (mScanModeNotDiscoverable == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            mScanModeNotDiscoverable = BluetoothAdapter.SCAN_MODE_CONNECTABLE;
        }

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        getContext().registerReceiver(mReceiver, filter);

        return v;
    }

    @Override
    public void onStop() {
        // Turn off advertising toggle to stop advertising.
        mAdvertisingToggle.setChecked(false);
        // Turn off UUID switches to remove them from EIR.
        turnOffUuidSwitches();

        super.onStop();
        getContext().unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume() {
        if (BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_CONNECT)) {
            mBtAdapterToggle.setEnabled(true);
        } else {
            mBtAdapterToggle.setEnabled(false);
        }
        if (BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_SCAN)) {
            mAdvertisingToggle.setEnabled(true);
        } else {
            mAdvertisingToggle.setEnabled(false);
        }
        super.onResume();
        refreshUi();
    }

    /**
     * When the API {@link BluetoothAdapter#listenUsingRfcommWithServiceRecord} is called, the
     * 128-bit UUID is added to the EIR. When the socket created by that API is closed, the
     * UUID is deleted from the EIR.
     */
    private BluetoothServerSocket setUuidInEir(CompoundButton buttonView, boolean isChecked,
            EditText uuidText, BluetoothServerSocket socket) {
        if (isChecked) {
            // Add the corresponding UUID to the EIR
            if (uuidText == null) {
                Log.e(TAG, "setUuidInEir: Can't find EditText corresponding to toggle");
                return null;
            }
            uuidText.setEnabled(false);
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidText.getText().toString());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "setUuidInEir: Invalid UUID format (hyphens matter!)");
                Toast.makeText(getContext(),
                        "Invalid UUID format (hyphens matter!)",
                        Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                uuidText.setEnabled(true);
                return null;
            }
            // Can't create a socket if adapter is not enabled.
            if (!mAdapter.isEnabled()) {
                mAdapter.enable();
                if (!waitForAdapterOn(ADAPTER_ON_TIMEOUT_MS)) {
                    return null;
                }
            }
            try {
                socket = mAdapter.listenUsingRfcommWithServiceRecord(
                        UUID_SERVICE_NAME, uuid);
            } catch (Exception e) {
                Log.e(TAG, "setUuidInEir: Can't create socket");
                Toast.makeText(getContext(),
                        "Can't create socket. Verify adapter is ON?",
                        Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                uuidText.setEnabled(true);
                return null;
            }
            return socket;
        } else {
            // Remove the existing UUID from the EIR
            if (uuidText == null) {
                Log.e(TAG, "setUuidInEir: Can't find EditText corresponding to toggle");
                return null;
            }
            uuidText.setEnabled(true);
            if (socket == null) {
                Log.w(TAG, "setUuidInEir: Can't find socket corresponding to toggle");
                return null;
            }
            try {
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "setUuidInEir: Can't close socket");
            }
            return null;
        }
    }

    private void setAdvertisingState(boolean isChecked) {
        if (isChecked) {
            // Start advertising EIR by entering discoverable mode.
            if (!mAdapter.isEnabled()) {
                mAdapter.enable();
                if (!waitForAdapterOn(ADAPTER_ON_TIMEOUT_MS)) {
                    return;
                }
            }
            mAdapter.setDiscoverableTimeout(Duration.ofMillis(DISCOVERABLE_TIMEOUT_TWO_MINUTES));
            mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
            Log.d(TAG, "Started discovery.");
        } else {
            // Stop advertising.
            Log.d(TAG, "Stopping discovery, setting scan mode to: " + mScanModeNotDiscoverable
                    + ", " + scanModeToText(mScanModeNotDiscoverable));
            mAdapter.setScanMode(mScanModeNotDiscoverable);
        }
    }

    private void setAdapterState(boolean isChecked) {
        if (isChecked) {
            mAdapter.enable();
        } else {
            mAdapter.disable();
        }
    }

    private void refreshUi() {
        if (mBtAdapterToggle.isEnabled()) {
            mBtAdapterToggle.setChecked(mAdapter.isEnabled());
        }
        if (mAdvertisingToggle.isEnabled()) {
            mAdvertisingToggle.setChecked(mAdapter.getScanMode()
                    == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        }
    }

    private void turnOffUuidSwitches() {
        for (Iterator<Switch> i = mUuidSwitches.iterator(); i.hasNext(); ) {
            i.next().setChecked(false);
        }
    }

    private void setUuidsToDefault() {
        turnOffUuidSwitches();
        Iterator<EditText> textIter = mUuidEditTexts.iterator();
        Iterator<String> uuidIter = DEFAULT_UUIDS.iterator();
        while (textIter.hasNext() && uuidIter.hasNext()) {
            textIter.next().setText(uuidIter.next());
        }
    }

    private String scanModeToText(int mode) {
        switch (mode) {
            case BluetoothAdapter.SCAN_MODE_NONE:
                return "None";
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                return "Connectable";
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return "Connectable+Discoverable";
            default:
                return "Unknown";
        }
    }

    private boolean waitForAdapterOn(int timeout) {
        mAdapterOnLock.lock();
        try {
            while (!mAdapter.isEnabled()) {
                Toast.makeText(getContext(), "Waiting for adapter to turn ON",
                        Toast.LENGTH_SHORT).show();
                if (mAdapterOnCondition.await(timeout, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "waitForAdapterOn: timed out");
                    Toast.makeText(getContext(), "Timed out waiting for adapter to turn ON",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForAdapterOn: " + e);
            Toast.makeText(getContext(), "Exception when waiting for adapter to turn ON",
                    Toast.LENGTH_SHORT).show();
            return false;
        } finally {
            mAdapterOnLock.unlock();
        }
        return true;
    }
}
