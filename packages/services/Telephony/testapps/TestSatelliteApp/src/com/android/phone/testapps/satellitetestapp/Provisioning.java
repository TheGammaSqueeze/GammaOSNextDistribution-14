/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.phone.testapps.satellitetestapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteProvisionStateCallback;
import android.telephony.satellite.stub.SatelliteResult;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Activity related to Provisioning APIs.
 */
public class Provisioning extends Activity {

    private static final String TAG = "Provisioning";

    private boolean mProvisioned = false;

    private SatelliteManager mSatelliteManager;
    private SatelliteProvisionStateCallbackTestApp mCallback;
    private static final long TIMEOUT = 3000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSatelliteManager = getSystemService(SatelliteManager.class);
        mCallback = new SatelliteProvisionStateCallbackTestApp();

        setContentView(R.layout.activity_Provisioning);
        findViewById(R.id.provisionSatelliteService)
                .setOnClickListener(this::provisionServiceApp);
        findViewById(R.id.deprovisionSatelliteService)
                .setOnClickListener(this::deprovisionServiceApp);
        findViewById(R.id.requestIsSatelliteProvisioned)
                .setOnClickListener(this::requestIsProvisionedApp);
        findViewById(R.id.registerForSatelliteProvisionStateChanged)
                .setOnClickListener(this::registerForProvisionStateChangedApp);
        findViewById(R.id.unregisterForSatelliteProvisionStateChanged)
                .setOnClickListener(this::unregisterForProvisionStateChangedApp);
        findViewById(R.id.showCurrentSatelliteProvisionState)
                .setOnClickListener(this::showCurrentSatelliteProvisionStateApp);
        findViewById(R.id.Back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Provisioning.this, SatelliteTestApp.class));
            }
        });
    }

    protected class SatelliteProvisionStateCallbackTestApp implements
            SatelliteProvisionStateCallback {
        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            mProvisioned = provisioned;
            Log.d(TAG, "onSatelliteProvisionStateChanged in SatelliteTestApp: provisioned="
                    + mProvisioned);
        }
    }

    private void provisionServiceApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        CancellationSignal cancellationSignal = new CancellationSignal();
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        mSatelliteManager.provisionService("SATELLITE_TOKEN", testProvisionData,
                cancellationSignal, Runnable::run, error::offer);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to provision the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to provision SatelliteService with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("Successfully provisioned the satellite");
            }
        } catch (InterruptedException e) {
            textView.setText("Provision SatelliteService exception caught =" + e);
        }
    }

    private void deprovisionServiceApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mSatelliteManager.deprovisionService("SATELLITE_TOKEN", Runnable::run,
                error::offer);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to deprovision the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to deprovision SatelliteService with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("Successfully deprovisioned the satellite");
            }
        } catch (InterruptedException e) {
            textView.setText("Deprovision SatelliteService exception caught =" + e);
        }
    }

    private void requestIsProvisionedApp(View view) {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> mReceiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean result) {
                enabled.set(result);
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestIsSatelliteProvisioned result: "
                        + enabled.get());
            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestIsSatelliteProvisioned error : "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestIsProvisioned(Runnable::run, mReceiver);
    }

    private void registerForProvisionStateChangedApp(View view) {
        int result = mSatelliteManager.registerForProvisionStateChanged(Runnable::run,
                mCallback);
        TextView textView = findViewById(R.id.text_id);
        textView.setText("Status for registerForSatelliteProvisionStateChanged : "
                + SatelliteErrorUtils.mapError(result));
    }

    private void unregisterForProvisionStateChangedApp(View view) {
        mSatelliteManager.unregisterForProvisionStateChanged(mCallback);
        TextView textView = findViewById(R.id.text_id);
        textView.setText("unregisterForSatelliteProvisionStateChanged is successful");
    }

    private void showCurrentSatelliteProvisionStateApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("Current Provision State is " + mProvisioned);
    }

    // Fetch the stored data in onResume()
    // Because this is what will be called when the app opens again
    @Override
    protected void onResume() {
        super.onResume();
        // Fetching the stored data from the SharedPreference
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        boolean isProvisioned = sh.getBoolean("provision_state", mProvisioned);

        // Setting the fetched data
        mProvisioned = isProvisioned;
    }

    // Store the data in the SharedPreference in the onPause() method
    // When the user closes the application onPause() will be called and data will be stored
    @Override
    protected void onPause() {
        super.onPause();
        // Creating a shared pref object with a file name "MySharedPref" in private mode
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        // write all the data entered by the user in SharedPreference and apply
        myEdit.putBoolean("provision_state", mProvisioned);
        myEdit.apply();
    }

    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        final SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
        sharedPrefsEditor.remove("provision_state");
        sharedPrefsEditor.commit();
    }
}
