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

package com.android.cts.verifier.managedprovisioning;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.cts.verifier.R;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Activity that checks whether it can obtain location. Asks for permission in case it is missing.
 * Result is reported to whoever invoked it with startActivityForResult.
 */
public class LocationCheckerActivity extends Activity {
    private static final String TAG = LocationCheckerActivity.class.getSimpleName();

    public static final String ACTION_CHECK_LOCATION_PRIMARY =
            "com.android.cts.verifier.managedprovisioning.CHECK_LOCATION_ACCESS_PRIMARY";
    public static final String ACTION_CHECK_LOCATION_WORK =
            "com.android.cts.verifier.managedprovisioning.CHECK_LOCATION_ACCESS_WORK";
    public static final String WORK_ACTIVITY_ALIAS =
            "com.android.cts.verifier.managedprovisioning.WorkLocationCheckerActivityAlias";

    public static final String EXTRA_ERROR_ID = "extra-error-id";
    public static final int LOCATION_PERMISSION_REQUEST = 125;
    private static final int REQUEST_MAX_DURATION_MILLIS = 15000;

    private List<String> mProviders;
    private LocationManager mLocationManager;
    private TextView mStatusText;
    private final CancellationSignal mCancellationSignal = new CancellationSignal();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.location_checker);

        mLocationManager = getSystemService(LocationManager.class);
        mStatusText = findViewById(R.id.status_text);

        if (hasLocationPermission()) {
            requestCurrentLocation();
        } else {
            requestLocationPermission();
        }
    }

    private void requestCurrentLocation() {
        synchronized (this) {
            mProviders = mLocationManager.getAllProviders().stream()
                    .filter(mLocationManager::isProviderEnabled)
                    .collect(Collectors.toList());
            if (mProviders.isEmpty()) {
                finish(R.string.provisioning_byod_location_no_provider);
                return;
            }
            // Callback will be invoked with null if duration exceeded.
            LocationRequest request = new LocationRequest.Builder(0)
                    .setDurationMillis(REQUEST_MAX_DURATION_MILLIS).build();
            // Request location from all available providers.
            for (String provider: mProviders) {
                mLocationManager.getCurrentLocation(provider, request, mCancellationSignal,
                        getMainExecutor(), l -> onLocation(provider, l));
            }
            updateStatusLocked();
        }
    }

    private void updateStatusLocked() {
        String providers = String.join(", ", mProviders);
        mStatusText.setText(getString(R.string.provisioning_byod_location_trying, providers));
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grants) {
        // Test that the right permission was granted.
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (!permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION)
                    || grants[0] != PackageManager.PERMISSION_GRANTED
                    || !hasLocationPermission()) {
                Log.e(TAG, "The test needs location permission.");
                finish(R.string.provisioning_byod_location_mode_enable_missing_permission);
                return;
            }
            requestCurrentLocation();
        }
    }

    private void onLocation(String provider, Location location) {
        if (mCancellationSignal.isCanceled()) {
            return;
        } else if (location != null) {
            mCancellationSignal.cancel();
            finish(-1 /* no error */);
        }

        // location == null, provider wasn't able to get location, see if there are more providers
        synchronized (this) {
            mProviders.remove(provider);
            if (mProviders.isEmpty()) {
                finish(R.string.provisioning_byod_location_failed);
            } else {
                updateStatusLocked();
            }
        }
    }

    void finish(int messageId) {
        Intent result = new Intent();
        result.putExtra(EXTRA_ERROR_ID, messageId);
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}
