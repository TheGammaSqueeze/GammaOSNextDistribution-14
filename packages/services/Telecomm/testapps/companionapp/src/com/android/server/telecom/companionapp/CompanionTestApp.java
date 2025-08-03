/*
 * Copyright (C) 2020 Android Open Source Project
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

package com.android.server.telecom.companionapp;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Companion Test App in Telecom
 */
public class CompanionTestApp extends Activity {

    private static String logtag = "CompanionTestApp";
    private CompanionDeviceManager mDeviceManager;
    private AssociationRequest mPairingRequest;
    private BluetoothDeviceFilter mDeviceFilter;

    private static final int SELECT_DEVICE_REQUEST_CODE = 42;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setupDeviceAssociation();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Button buttonStart = (Button)findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(startListener);

        Button buttonStop = (Button)findViewById(R.id.buttonStop);
        buttonStop.setOnClickListener(stopListener);
    }

    private OnClickListener startListener = new OnClickListener() {
        public void onClick(View v) {
            Log.d(logtag,"onClick() called - start button");
            Toast.makeText(CompanionTestApp.this, "Start Association", Toast.LENGTH_LONG).show();
            assosicate();
            Log.d(logtag,"onClick() ended - start button");
        }
    };

    private OnClickListener stopListener = new OnClickListener() {
        public void onClick(View v) {
            Log.d(logtag,"onClick() called - stop button");
            Toast.makeText(CompanionTestApp.this, "Stop Association", Toast.LENGTH_LONG).show();
            disassosicate();
            Log.d(logtag,"onClick() ended - stop button");
        }
    };

    // Setup Device Association Preparation, per
    // https://developer.android.com/guide/topics/connectivity/companion-device-pairing
    private void setupDeviceAssociation() {
        mDeviceManager = getSystemService(CompanionDeviceManager.class);

        mDeviceFilter = new BluetoothDeviceFilter.Builder()
                .build();

        mPairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(mDeviceFilter)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .build();

    }

    // Associate bluetooth device, per
    // https://developer.android.com/guide/topics/connectivity/companion-device-pairing
    private void assosicate() {
        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        mDeviceManager.associate(mPairingRequest,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        try {
                            startIntentSenderForResult(chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                        } catch (Exception ex) {
                            Log.d(logtag, "Callback onDeviceFound() Exception: " + ex);
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        Log.d(logtag, "Callback onFailure() called - error: " + error);
                    }
                },
                null);
    }

    // Disassociate the associated bluetooth device
    private void disassosicate() {
        for (String macAddress : mDeviceManager.getAssociations()) {
            mDeviceManager.disassociate(macAddress);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_DEVICE_REQUEST_CODE &&
                resultCode == Activity.RESULT_OK) {
            // User has chosen to pair with the Bluetooth device.
            BluetoothDevice deviceToPair =
                    data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
            deviceToPair.createBond();
        }
    }

    @Override
    protected void onStart() {
        Log.d(logtag,"onStart() called");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(logtag,"onResume() called");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(logtag,"onPause() called");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(logtag,"onStop() called");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(logtag,"onDestroy() called");
        super.onDestroy();
    }
}
