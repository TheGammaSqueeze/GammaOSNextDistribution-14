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

package com.google.android.car.adaslocation;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LastLocationRequest;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdasLocationActivity extends AppCompatActivity {
    private static final int KS_PERMISSIONS_REQUEST = 1;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    private boolean mIsRegister;
    private LocationManager mLocationManager;
    private FloatingActionButton mObserveFab;
    private TextView mObserveLocationResult;
    private LocationListener mLocationListener;
    private FloatingActionButton mQueryFab;
    private TextView mLastLocationResult;
    private TextView mMainLocationEnabled;
    private TextView mAdasLocationEnabled;
    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mLocationManager = getApplicationContext().getSystemService(LocationManager.class);
        mObserveFab = findViewById(R.id.observe_fab);
        mObserveLocationResult = findViewById(R.id.current_location_result);
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                mObserveLocationResult.setText(locationToFormattedString(location));
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        mObserveFab.setOnClickListener(
                v -> {
                    if (!mIsRegister) {
                        startListening();
                    } else {
                        stopListening();
                    }
                }
        );
        mQueryFab = findViewById(R.id.query_fab);
        mLastLocationResult = findViewById(R.id.last_location_result);
        mQueryFab.setOnClickListener(
                v -> {
                    LastLocationRequest request =
                            new LastLocationRequest.Builder().setAdasGnssBypass(true).build();
                    Location location =
                            mLocationManager.getLastKnownLocation(
                                    LocationManager.GPS_PROVIDER, request);
                    if (location != null) {
                        mLastLocationResult.setText(locationToFormattedString(location));
                    } else {
                        mLastLocationResult.setText(R.string.no_last_location);
                    }
                }
        );

        mReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (LocationManager.MODE_CHANGED_ACTION == intent.getAction()) {
                            mMainLocationEnabled.setText(Boolean.toString(mLocationManager
                                    .isLocationEnabled()));
                            return;
                        }
                        if (LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED
                                == intent.getAction()) {
                            mAdasLocationEnabled
                                    .setText(Boolean.toString(mLocationManager
                                            .isAdasGnssLocationEnabled()));
                            return;
                        }
                    }
                };
    }

    @Override
    public void onStart() {
        super.onStart();
        initPermissions();

        mMainLocationEnabled = findViewById(R.id.main_location_status);
        mMainLocationEnabled.setText(Boolean.toString(mLocationManager.isLocationEnabled()));
        mAdasLocationEnabled = findViewById(R.id.adas_location_status);
        mAdasLocationEnabled.setText(Boolean.toString(mLocationManager
                .isAdasGnssLocationEnabled()));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
        intentFilter.addAction(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED);
        registerReceiver(mReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onResume() {
        super.onResume();
        Set<String> missingPermissions = checkExistingPermissions();
        if (!missingPermissions.isEmpty()) {
            return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mIsRegister) {
            stopListening();
        }
        mLastLocationResult.setText("");
    }

    private static String locationToFormattedString(Location location) {
        return String.format("Location: lat=%10.6f, lon=%10.6f ",
                location.getLatitude(),
                location.getLongitude());
    }

    private void initPermissions() {
        Set<String> missingPermissions = checkExistingPermissions();
        if (!missingPermissions.isEmpty()) {
            requestPermissions(missingPermissions);
        }
    }

    private Set<String> checkExistingPermissions() {
        return Arrays.stream(REQUIRED_PERMISSIONS).filter(permission -> ActivityCompat
                .checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                .collect(Collectors.toSet());
    }

    private void requestPermissions(Set<String> permissions) {
        requestPermissions(permissions.toArray(new String[permissions.size()]),
                KS_PERMISSIONS_REQUEST);
    }

    private void startListening() {
        LocationRequest request = new LocationRequest.Builder(0).setAdasGnssBypass(true).build();
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, request, this.getMainExecutor(), mLocationListener);
        mObserveLocationResult.setText(R.string.waiting_for_location);
        mIsRegister = true;
    }

    private void stopListening() {
        mLocationManager.removeUpdates(mLocationListener);
        mObserveLocationResult.setText("");
        mIsRegister = false;
    }
}
