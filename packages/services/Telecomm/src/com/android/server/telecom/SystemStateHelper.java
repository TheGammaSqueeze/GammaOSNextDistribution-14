/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import android.annotation.NonNull;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.telecom.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides various system states to the rest of the telecom codebase.
 */
public class SystemStateHelper implements UiModeManager.OnProjectionStateChangedListener {
    public interface SystemStateListener {
        /**
         * Listener method to inform interested parties when a package name requests to enter or
         * exit car mode.
         * @param priority the priority of the enter/exit request.
         * @param packageName the package name of the requester.
         * @param isCarMode {@code true} if the package is entering car mode, {@code false}
         *                              otherwise.
         */
        void onCarModeChanged(int priority, String packageName, boolean isCarMode);

        /**
         * Listener method to inform interested parties when a package has set automotive projection
         * state.
         * @param automotiveProjectionPackage the package that set automotive projection.
         */
        void onAutomotiveProjectionStateSet(String automotiveProjectionPackage);

        /**
         * Listener method to inform interested parties when automotive projection state has been
         * cleared.
         */
        void onAutomotiveProjectionStateReleased();

        /**
         * Notifies when a package has been uninstalled.
         * @param packageName the package name of the uninstalled package
         */
        void onPackageUninstalled(String packageName);
    }

    private final Context mContext;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("SSH.oR");
            try {
                synchronized (mLock) {
                    String action = intent.getAction();
                    if (UiModeManager.ACTION_ENTER_CAR_MODE_PRIORITIZED.equals(action)) {
                        int priority = intent.getIntExtra(UiModeManager.EXTRA_PRIORITY,
                                UiModeManager.DEFAULT_PRIORITY);
                        String callingPackage = intent.getStringExtra(
                                UiModeManager.EXTRA_CALLING_PACKAGE);
                        Log.i(SystemStateHelper.this,
                                "ENTER_CAR_MODE_PRIORITIZED; priority=%d, pkg=%s",
                                priority, callingPackage);
                        onEnterCarMode(priority, callingPackage);
                    } else if (UiModeManager.ACTION_EXIT_CAR_MODE_PRIORITIZED.equals(action)) {
                        int priority = intent.getIntExtra(UiModeManager.EXTRA_PRIORITY,
                                UiModeManager.DEFAULT_PRIORITY);
                        String callingPackage = intent.getStringExtra(
                                UiModeManager.EXTRA_CALLING_PACKAGE);
                        Log.i(SystemStateHelper.this,
                                "EXIT_CAR_MODE_PRIORITIZED; priority=%d, pkg=%s",
                                priority, callingPackage);
                        onExitCarMode(priority, callingPackage);
                    } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        Uri data = intent.getData();
                        if (data == null) {
                            Log.w(SystemStateHelper.this,
                                    "Got null data for package removed, ignoring");
                            return;
                        }
                        mListeners.forEach(
                                l -> l.onPackageUninstalled(data.getEncodedSchemeSpecificPart()));
                    } else {
                        Log.w(SystemStateHelper.this,
                                "Unexpected intent received: %s", intent.getAction());
                    }
                }
            } finally {
                Log.endSession();
            }
        }
    };

    @Override
    public void onProjectionStateChanged(int activeProjectionTypes,
            @NonNull Set<String> projectingPackages) {
        Log.startSession("SSH.oPSC");
        try {
            synchronized (mLock) {
                if (projectingPackages.isEmpty()) {
                    onReleaseAutomotiveProjection();
                } else {
                    onSetAutomotiveProjection(projectingPackages.iterator().next());
                }
            }
        } finally {
            Log.endSession();
        }

    }

    private Set<SystemStateListener> mListeners = new CopyOnWriteArraySet<>();
    private boolean mIsCarModeOrProjectionActive;
    private final TelecomSystem.SyncRoot mLock;

    public SystemStateHelper(Context context, TelecomSystem.SyncRoot lock) {
        mContext = context;
        mLock = lock;

        IntentFilter intentFilter1 = new IntentFilter(
                UiModeManager.ACTION_ENTER_CAR_MODE_PRIORITIZED);
        intentFilter1.addAction(UiModeManager.ACTION_EXIT_CAR_MODE_PRIORITIZED);
        intentFilter1.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        IntentFilter intentFilter2 = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter2.addDataScheme("package");
        intentFilter2.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter1);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter2);
        Log.i(this, "Registering broadcast receiver: %s", intentFilter1);
        Log.i(this, "Registering broadcast receiver: %s", intentFilter2);

        mContext.getSystemService(UiModeManager.class).addOnProjectionStateChangedListener(
                UiModeManager.PROJECTION_TYPE_AUTOMOTIVE, mContext.getMainExecutor(), this);
        mIsCarModeOrProjectionActive = getSystemCarModeOrProjectionState();
    }

    public void addListener(SystemStateListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public boolean removeListener(SystemStateListener listener) {
        return mListeners.remove(listener);
    }

    public boolean isCarModeOrProjectionActive() {
        return mIsCarModeOrProjectionActive;
    }

    public boolean isDeviceAtEar() {
        return isDeviceAtEar(mContext);
    }

    /**
     * Returns a guess whether the phone is up to the user's ear. Use the proximity sensor and
     * the gravity sensor to make a guess
     * @return true if the proximity sensor is activated, the magnitude of gravity in directions
     *         parallel to the screen is greater than some configurable threshold, and the
     *         y-component of gravity isn't less than some other configurable threshold.
     */
    public static boolean isDeviceAtEar(Context context) {
        SensorManager sm = context.getSystemService(SensorManager.class);
        if (sm == null) {
            return false;
        }
        Sensor grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (grav == null || proximity == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(true);
        CountDownLatch gravLatch = new CountDownLatch(1);
        CountDownLatch proxLatch = new CountDownLatch(1);

        final double xyGravityThreshold = context.getResources().getFloat(
                R.dimen.device_on_ear_xy_gravity_threshold);
        final double yGravityNegativeThreshold = context.getResources().getFloat(
                R.dimen.device_on_ear_y_gravity_negative_threshold);

        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                    if (gravLatch.getCount() == 0) {
                        return;
                    }
                    double xyMag = Math.sqrt(event.values[0] * event.values[0]
                            + event.values[1] * event.values[1]);
                    if (xyMag < xyGravityThreshold
                            || event.values[1] < yGravityNegativeThreshold) {
                        result.set(false);
                    }
                    gravLatch.countDown();
                } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                    if (proxLatch.getCount() == 0) {
                        return;
                    }
                    if (event.values[0] >= proximity.getMaximumRange()) {
                        result.set(false);
                    }
                    proxLatch.countDown();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        try {
            sm.registerListener(listener, grav, SensorManager.SENSOR_DELAY_FASTEST);
            sm.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_FASTEST);
            boolean accelValid = gravLatch.await(100, TimeUnit.MILLISECONDS);
            boolean proxValid = proxLatch.await(100, TimeUnit.MILLISECONDS);
            if (accelValid && proxValid) {
                return result.get();
            } else {
                Log.w(SystemStateHelper.class.getSimpleName(),
                        "Timed out waiting for sensors: %b %b", accelValid, proxValid);
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        } finally {
            sm.unregisterListener(listener);
        }
    }

    private void onEnterCarMode(int priority, String packageName) {
        Log.i(this, "Entering carmode");
        mIsCarModeOrProjectionActive = getSystemCarModeOrProjectionState();
        for (SystemStateListener listener : mListeners) {
            listener.onCarModeChanged(priority, packageName, true /* isCarMode */);
        }
    }

    private void onExitCarMode(int priority, String packageName) {
        Log.i(this, "Exiting carmode");
        mIsCarModeOrProjectionActive = getSystemCarModeOrProjectionState();
        for (SystemStateListener listener : mListeners) {
            listener.onCarModeChanged(priority, packageName, false /* isCarMode */);
        }
    }

    private void onSetAutomotiveProjection(String packageName) {
        Log.i(this, "Automotive projection set.");
        mIsCarModeOrProjectionActive = getSystemCarModeOrProjectionState();
        for (SystemStateListener listener : mListeners) {
            listener.onAutomotiveProjectionStateSet(packageName);
        }

    }

    private void onReleaseAutomotiveProjection() {
        Log.i(this, "Automotive projection released.");
        mIsCarModeOrProjectionActive = getSystemCarModeOrProjectionState();
        for (SystemStateListener listener : mListeners) {
            listener.onAutomotiveProjectionStateReleased();
        }
    }

    /**
     * Checks the system for the current car projection state.
     *
     * @return True if projection is active, false otherwise.
     */
    private boolean getSystemCarModeOrProjectionState() {
        UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);

        if (uiModeManager != null) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR
                    || (uiModeManager.getActiveProjectionTypes()
                            & UiModeManager.PROJECTION_TYPE_AUTOMOTIVE) != 0;
        }

        Log.w(this, "Got null UiModeManager, returning false.");
        return false;
    }
}
