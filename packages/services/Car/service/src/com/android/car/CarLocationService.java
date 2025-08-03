/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.app.ActivityManager;
import android.car.ICarPerUserService;
import android.car.ILocationManagerProxy;
import android.car.builtin.util.Slogf;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerManager.CarPowerStateListenerWithCompletion;
import android.car.hardware.power.CarPowerManager.CompletablePowerStateChangeFuture;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.PowerComponent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * This service stores the last known location from {@link LocationManager} when a car is parked
 * and restores the location when the car is powered on.
 */
public class CarLocationService extends BroadcastReceiver implements CarServiceBase,
        CarPowerStateListenerWithCompletion {
    private static final String TAG = CarLog.tagFor(CarLocationService.class);
    private static final String FILENAME = "location_cache.json";
    // The accuracy for the stored timestamp
    private static final long GRANULARITY_ONE_DAY_MS = 24 * 60 * 60 * 1000L;
    // The time-to-live for the cached location
    private static final long TTL_THIRTY_DAYS_MS = 30 * GRANULARITY_ONE_DAY_MS;
    // The maximum number of times to try injecting a location
    private static final int MAX_LOCATION_INJECTION_ATTEMPTS = 10;

    // Constants for location serialization.
    private static final String PROVIDER = "provider";
    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    private static final String ALTITUDE = "altitude";
    private static final String SPEED = "speed";
    private static final String BEARING = "bearing";
    private static final String ACCURACY = "accuracy";
    private static final String VERTICAL_ACCURACY = "verticalAccuracy";
    private static final String SPEED_ACCURACY = "speedAccuracy";
    private static final String BEARING_ACCURACY = "bearingAccuracy";
    private static final String IS_FROM_MOCK_PROVIDER = "isFromMockProvider";
    private static final String CAPTURE_TIME = "captureTime";

    private final Object mLock = new Object();

    // Used internally for mILocationManagerProxy synchronization
    private final Object mLocationManagerProxyLock = new Object();

    private final Context mContext;
    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());

    private final CarPowerManager mCarPowerManager;
    private final CarDrivingStateService mCarDrivingStateService;
    private final CarPerUserServiceHelper mCarPerUserServiceHelper;
    private final CarPowerManagementService mCarPowerManagementService;

    @GuardedBy("mLock")
    private LocationManager mLocationManager;

    // Allows us to interact with the {@link LocationManager} as the foreground user.
    @GuardedBy("mLocationManagerProxyLock")
    private ILocationManagerProxy mILocationManagerProxy;

    // Used for supporting features such as suspend to RAM and suspend to disk. Power policy
    // listener listens to changes in the PowerComponent.LOCATION state. If PowerComponent.LOCATION
    // is disabled, suspend gnss functionality. If PowerComponent.LOCATION is enabled, resume gnss
    // functionality.
    private final ICarPowerPolicyListener mPowerPolicyListener =
            new ICarPowerPolicyListener.Stub() {
                @Override
                public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                        CarPowerPolicy accumulatedPolicy) {
                    LocationManager locationManager;

                    synchronized (mLock) {
                        // LocationManager can be temporarily unavailable during boot up. Reacquire
                        // here if mLocationManager is null.
                        if (mLocationManager == null) {
                            logd("Null location manager. Retry.");
                            mLocationManager = mContext.getSystemService(LocationManager.class);
                        }

                        // If LocationManager is still null after the retry, return.
                        if (mLocationManager == null) {
                            logd("Null location manager. Skip gnss controls.");
                            return;
                        }

                        locationManager = mLocationManager;
                    }

                    boolean isOn =
                            accumulatedPolicy.isComponentEnabled(PowerComponent.LOCATION);
                    if (isOn) {
                        logd("Resume GNSS requests.");
                        locationManager.setAutomotiveGnssSuspended(false);
                        if (locationManager.isAutomotiveGnssSuspended()) {
                            Slogf.w(TAG,
                                    "Failed - isAutomotiveGnssSuspended is true. "
                                    + "GNSS should NOT be suspended.");
                        }
                    } else {
                        logd("Suspend GNSS requests.");
                        locationManager.setAutomotiveGnssSuspended(true);
                        if (!locationManager.isAutomotiveGnssSuspended()) {
                            Slogf.w(TAG,
                                    "Failed - isAutomotiveGnssSuspended is false. "
                                    + "GNSS should be suspended.");
                        }
                    }
                }
    };

    // Maintains mILocationManagerProxy for the current foreground user.
    private final CarPerUserServiceHelper.ServiceCallback mUserServiceCallback =
            new CarPerUserServiceHelper.ServiceCallback() {
                @Override
                public void onServiceConnected(ICarPerUserService carPerUserService) {
                    logd("Connected to CarPerUserService");
                    if (carPerUserService == null) {
                        logd("ICarPerUserService is null. Cannot get location manager proxy");
                        return;
                    }
                    synchronized (mLocationManagerProxyLock) {
                        try {
                            mILocationManagerProxy = carPerUserService.getLocationManagerProxy();
                        } catch (RemoteException e) {
                            Slogf.e(TAG, "RemoteException from ICarPerUserService", e);
                            return;
                        }
                    }
                    int currentUser = ActivityManager.getCurrentUser();
                    logd("Current user: %s", currentUser);
                    if (UserManager.isHeadlessSystemUserMode()
                            && currentUser > UserHandle.SYSTEM.getIdentifier()) {
                        asyncOperation(() -> loadLocation());
                    }
                }

                @Override
                public void onPreUnbind() {
                    logd("Before Unbinding from CarPerUserService");
                    synchronized (mLocationManagerProxyLock) {
                        mILocationManagerProxy = null;
                    }
                }

                @Override
                public void onServiceDisconnected() {
                    logd("Disconnected from CarPerUserService");
                    synchronized (mLocationManagerProxyLock) {
                        mILocationManagerProxy = null;
                    }
                }
            };

    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener =
            new ICarDrivingStateChangeListener.Stub() {
                @Override
                public void onDrivingStateChanged(CarDrivingStateEvent event) {
                    logd("onDrivingStateChanged: %s", event);
                    if (event != null
                            && event.eventValue == CarDrivingStateEvent.DRIVING_STATE_MOVING) {
                        deleteCacheFile();
                        if (mCarDrivingStateService != null) {
                            mCarDrivingStateService.unregisterDrivingStateChangeListener(
                                    mICarDrivingStateChangeEventListener);
                        }
                    }
                }
            };

    public CarLocationService(Context context) {
        logd("constructed");
        mContext = context;

        mCarPowerManagementService = CarLocalServices.getService(
                CarPowerManagementService.class);
        if (mCarPowerManagementService == null) {
            Slogf.w(TAG, "Cannot find CarPowerManagementService.");
        }

        mCarPowerManager = CarLocalServices.createCarPowerManager(mContext);
        if (mCarPowerManager == null) {
            Slogf.w(TAG, "Cannot find CarPowerManager.");
        }

        mCarPerUserServiceHelper = CarLocalServices.getService(CarPerUserServiceHelper.class);
        if (mCarPerUserServiceHelper == null) {
            Slogf.w(TAG, "Cannot find CarPerUserServiceHelper.");
        }

        mCarDrivingStateService = CarLocalServices.getService(CarDrivingStateService.class);
        if (mCarDrivingStateService == null) {
            Slogf.w(TAG, "Cannot find CarDrivingStateService.");
        }
    }

    @Override
    public void init() {
        logd("init");
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);
        mContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);

        if (mCarDrivingStateService != null) {
            CarDrivingStateEvent event = mCarDrivingStateService.getCurrentDrivingState();
            if (event != null && event.eventValue == CarDrivingStateEvent.DRIVING_STATE_MOVING) {
                deleteCacheFile();
            } else {
                mCarDrivingStateService.registerDrivingStateChangeListener(
                        mICarDrivingStateChangeEventListener);
            }
        }

        if (mCarPowerManager != null) { // null case happens for testing.
            mCarPowerManager.setListenerWithCompletion((command) -> mHandler.post(command),
                    CarLocationService.this);
        }

        if (mCarPerUserServiceHelper != null) {
            mCarPerUserServiceHelper.registerServiceCallback(mUserServiceCallback);
        }

        synchronized (mLock) {
            mLocationManager = mContext.getSystemService(LocationManager.class);
            if (mLocationManager == null) {
                Slogf.w(TAG, "Null location manager.");
            }
        }

        // Power policy listener will check for the LocationManager dependency and reacquire it if
        // is not available yet at time of boot.
        addPowerPolicyListener();
    }

    @Override
    public void release() {
        logd("release");
        if (mCarPowerManager != null) {
            mCarPowerManager.clearListener();
        }
        if (mCarDrivingStateService != null) {
            mCarDrivingStateService.unregisterDrivingStateChangeListener(
                    mICarDrivingStateChangeEventListener);
        }
        if (mCarPerUserServiceHelper != null) {
            mCarPerUserServiceHelper.unregisterServiceCallback(mUserServiceCallback);
        }
        if (mCarPowerManagementService != null) {
            mCarPowerManagementService.removePowerPolicyListener(mPowerPolicyListener);
        }
        mContext.unregisterReceiver(this);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println(TAG);
        mCarPerUserServiceHelper.dump(writer);
        writer.printf("Context: %s\n", mContext);
        writer.printf("MAX_LOCATION_INJECTION_ATTEMPTS: %d\n", MAX_LOCATION_INJECTION_ATTEMPTS);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @Override
    public void onStateChanged(int state, CompletablePowerStateChangeFuture future) {
        logd("onStateChanged: %d", state);
        switch (state) {
            case CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE:
                if (future != null) {
                    future.complete();
                }
                break;
            case CarPowerManager.STATE_SHUTDOWN_PREPARE:
                storeLocation();
                // Notify the CarPowerManager that it may proceed to shutdown or suspend.
                if (future != null) {
                    future.complete();
                }
                break;
            case CarPowerManager.STATE_SUSPEND_EXIT:
                if (mCarDrivingStateService != null) {
                    CarDrivingStateEvent event = mCarDrivingStateService.getCurrentDrivingState();
                    if (event != null
                            && event.eventValue == CarDrivingStateEvent.DRIVING_STATE_MOVING) {
                        deleteCacheFile();
                    } else {
                        logd("Registering to receive driving state.");
                        mCarDrivingStateService.registerDrivingStateChangeListener(
                                mICarDrivingStateChangeEventListener);
                    }
                }
                if (future != null) {
                    future.complete();
                }
            default:
                // This service does not need to do any work for these events but should still
                // notify the CarPowerManager that it may proceed.
                if (future != null) {
                    future.complete();
                }
                break;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logd("onReceive %s", intent);
        // If the system user is headless but the current user is still the system user, then we
        // should not delete the location cache file due to missing location permissions.
        if (isCurrentUserHeadlessSystemUser()) {
            logd("Current user is headless system user.");
            return;
        }
        synchronized (mLocationManagerProxyLock) {
            if (mILocationManagerProxy == null) {
                logd("Null location manager.");
                return;
            }
            String action = intent.getAction();
            try {
                if (action == LocationManager.MODE_CHANGED_ACTION) {
                    boolean locationEnabled = mILocationManagerProxy.isLocationEnabled();
                    logd("isLocationEnabled(): %s", locationEnabled);
                    if (!locationEnabled) {
                        deleteCacheFile();
                    }
                } else {
                    logd("Unexpected intent.");
                }
            } catch (RemoteException e) {
                Slogf.e(TAG, "RemoteException from ILocationManagerProxy", e);
            }
        }
    }

    /**
     * If config_enableCarLocationServiceGnssControlsForPowerManagement is true, add a power policy
     * listener.
     */
    private void addPowerPolicyListener() {
        if (mCarPowerManagementService == null) {
            return;
        }

        if (!mContext.getResources().getBoolean(
                R.bool.config_enableCarLocationServiceGnssControlsForPowerManagement)) {
            logd("GNSS controls not enabled.");
            return;
        }

        CarPowerPolicyFilter carPowerPolicyFilter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.LOCATION).build();
        mCarPowerManagementService.addPowerPolicyListener(
                carPowerPolicyFilter, mPowerPolicyListener);
    }

    /** Tells whether the current foreground user is the headless system user. */
    private boolean isCurrentUserHeadlessSystemUser() {
        int currentUserId = ActivityManager.getCurrentUser();
        return UserManager.isHeadlessSystemUserMode()
                && currentUserId == UserHandle.SYSTEM.getIdentifier();
    }

    /**
     * Gets the last known location from the location manager proxy and store it in a file.
     */
    private void storeLocation() {
        Location location = null;
        synchronized (mLocationManagerProxyLock) {
            if (mILocationManagerProxy == null) {
                logd("Null location manager proxy.");
                return;
            }
            try {
                location = mILocationManagerProxy.getLastKnownLocation(
                        LocationManager.GPS_PROVIDER);
            } catch (RemoteException e) {
                Slogf.e(TAG, "RemoteException from ILocationManagerProxy", e);
            }
        }
        if (location == null) {
            logd("Not storing null location");
        } else {
            logd("Storing location");
            AtomicFile atomicFile = new AtomicFile(getLocationCacheFile());
            FileOutputStream fos = null;
            try {
                fos = atomicFile.startWrite();
                try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(fos, "UTF-8"))) {
                    jsonWriter.beginObject();
                    jsonWriter.name(PROVIDER).value(location.getProvider());
                    jsonWriter.name(LATITUDE).value(location.getLatitude());
                    jsonWriter.name(LONGITUDE).value(location.getLongitude());
                    if (location.hasAltitude()) {
                        jsonWriter.name(ALTITUDE).value(location.getAltitude());
                    }
                    if (location.hasSpeed()) {
                        jsonWriter.name(SPEED).value(location.getSpeed());
                    }
                    if (location.hasBearing()) {
                        jsonWriter.name(BEARING).value(location.getBearing());
                    }
                    if (location.hasAccuracy()) {
                        jsonWriter.name(ACCURACY).value(location.getAccuracy());
                    }
                    if (location.hasVerticalAccuracy()) {
                        jsonWriter.name(VERTICAL_ACCURACY).value(
                                location.getVerticalAccuracyMeters());
                    }
                    if (location.hasSpeedAccuracy()) {
                        jsonWriter.name(SPEED_ACCURACY).value(
                                location.getSpeedAccuracyMetersPerSecond());
                    }
                    if (location.hasBearingAccuracy()) {
                        jsonWriter.name(BEARING_ACCURACY).value(
                                location.getBearingAccuracyDegrees());
                    }
                    if (location.isFromMockProvider()) {
                        jsonWriter.name(IS_FROM_MOCK_PROVIDER).value(true);
                    }
                    long currentTime = location.getTime();
                    // Round the time down to only be accurate within one day.
                    jsonWriter.name(CAPTURE_TIME).value(
                            currentTime - currentTime % GRANULARITY_ONE_DAY_MS);
                    jsonWriter.endObject();
                }
                atomicFile.finishWrite(fos);
            } catch (IOException e) {
                Slogf.e(TAG, "Unable to write to disk", e);
                atomicFile.failWrite(fos);
            }
        }
    }

    /**
     * Reads a previously stored location and attempts to inject it into the location manager proxy.
     */
    private void loadLocation() {
        Location location = readLocationFromCacheFile();
        logd("Read location from timestamp %s", location.getTime());
        long currentTime = System.currentTimeMillis();
        if (location.getTime() + TTL_THIRTY_DAYS_MS < currentTime) {
            logd("Location expired.");
            deleteCacheFile();
        } else {
            location.setTime(currentTime);
            long elapsedTime = SystemClock.elapsedRealtimeNanos();
            location.setElapsedRealtimeNanos(elapsedTime);
            if (location.isComplete()) {
                injectLocation(location, 1);
            }
        }
    }

    private Location readLocationFromCacheFile() {
        Location location = new Location((String) null);
        File file = getLocationCacheFile();
        AtomicFile atomicFile = new AtomicFile(file);
        try (FileInputStream fis = atomicFile.openRead()) {
            JsonReader reader = new JsonReader(new InputStreamReader(fis, "UTF-8"));
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case PROVIDER:
                        location.setProvider(reader.nextString());
                        break;
                    case LATITUDE:
                        location.setLatitude(reader.nextDouble());
                        break;
                    case LONGITUDE:
                        location.setLongitude(reader.nextDouble());
                        break;
                    case ALTITUDE:
                        location.setAltitude(reader.nextDouble());
                        break;
                    case SPEED:
                        location.setSpeed((float) reader.nextDouble());
                        break;
                    case BEARING:
                        location.setBearing((float) reader.nextDouble());
                        break;
                    case ACCURACY:
                        location.setAccuracy((float) reader.nextDouble());
                        break;
                    case VERTICAL_ACCURACY:
                        location.setVerticalAccuracyMeters((float) reader.nextDouble());
                        break;
                    case SPEED_ACCURACY:
                        location.setSpeedAccuracyMetersPerSecond((float) reader.nextDouble());
                        break;
                    case BEARING_ACCURACY:
                        location.setBearingAccuracyDegrees((float) reader.nextDouble());
                        break;
                    case IS_FROM_MOCK_PROVIDER:
                        location.setIsFromMockProvider(reader.nextBoolean());
                        break;
                    case CAPTURE_TIME:
                        location.setTime(reader.nextLong());
                        break;
                    default:
                        Slogf.w(TAG, "Unrecognized key: " + name);
                        reader.skipValue();
                }
            }
            reader.endObject();
        } catch (FileNotFoundException e) {
            logd("Location cache file not found: %s", file);
        } catch (IOException e) {
            Slogf.e(TAG, "Unable to read from disk", e);
        } catch (NumberFormatException | IllegalStateException e) {
            Slogf.e(TAG, "Unexpected format", e);
        }
        return location;
    }

    private void deleteCacheFile() {
        File file = getLocationCacheFile();
        boolean deleted = file.delete();
        if (deleted) {
            logd("Successfully deleted cache file at %s", file);
        } else {
            logd("Failed to delete cache file at %s", file);
        }
    }

    /**
     * Attempts to inject the location multiple times in case the LocationManager was not fully
     * initialized or has not updated its handle to the current user yet.
     */
    private void injectLocation(Location location, int attemptCount) {
        boolean success = false;
        synchronized (mLocationManagerProxyLock) {
            if (mILocationManagerProxy == null) {
                logd("Null location manager proxy.");
            } else {
                try {
                    success = mILocationManagerProxy.injectLocation(location);
                } catch (RemoteException e) {
                    Slogf.e(TAG, "RemoteException from ILocationManagerProxy", e);
                }
            }
        }
        if (success) {
            logd("Successfully injected stored location on attempt %s.", attemptCount);
            return;
        } else if (attemptCount <= MAX_LOCATION_INJECTION_ATTEMPTS) {
            logd("Failed to inject stored location on attempt %s.", attemptCount);
            asyncOperation(() -> {
                injectLocation(location, attemptCount + 1);
            }, 200 * attemptCount);
        } else {
            logd("No location injected.");
        }
    }

    private File getLocationCacheFile() {
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        return new File(systemInterface.getSystemCarDir(), FILENAME);
    }

    @VisibleForTesting
    void asyncOperation(Runnable operation) {
        asyncOperation(operation, 0);
    }

    private void asyncOperation(Runnable operation, long delayMillis) {
        mHandler.postDelayed(() -> operation.run(), delayMillis);
    }

    private static void logd(String msg, Object... vals) {
        // Disable logs here if they become too spammy.
        Slogf.d(TAG, msg, vals);
    }
}
