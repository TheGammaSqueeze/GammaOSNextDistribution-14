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
package com.android.timezone.location.provider;

import static android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY;
import static android.location.LocationRequest.QUALITY_HIGH_ACCURACY;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import static com.android.timezone.location.provider.core.LogUtils.LOG_TAG;
import static com.android.timezone.location.provider.core.LogUtils.formatElapsedRealtimeMillis;
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_ACTIVE;
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_PASSIVE;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.lookup.GeoTimeZonesFinder;
import com.android.timezone.location.provider.core.Cancellable;
import com.android.timezone.location.provider.core.Environment;
import com.android.timezone.location.provider.core.LocationListeningAccountant;
import com.android.timezone.location.provider.core.MetricsReporter;
import com.android.timezone.location.provider.core.TimeZoneProviderResult;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * The real implementation of {@link Environment}.
 */
class EnvironmentImpl implements Environment {

    private static final String RESOURCE_CONFIG_PROPERTIES_NAME = "offlineltzprovider.properties";

    /**
     *
     * The config properties key to determine how the tzs2.dat file is loaded.
     */
    private static final String RESOURCE_CONFIG_KEY_GEODATA_FILE_MANAGER_IMPL =
            "geodata.file_manager_impl";

    /**
     * The config properties key for the namespace to pass to {@link android.provider.DeviceConfig}
     * for server-pushed configuration.
     */
    private static final String RESOURCE_CONFIG_KEY_DEVICE_CONFIG_NAMESPACE =
            "deviceconfig.namespace";

    /**
     * The config properties key for the prefix that should be applied to keys passed to
     * {@link android.provider.DeviceConfig}.
     */
    private static final String RESOURCE_CONFIG_KEY_DEVICE_CONFIG_KEY_PREFIX =
            "deviceconfig.key_prefix";

    /**
     * The config properties key for the implementation of {@link MetricsReporter} to use.
     */
    private static final String RESOURCE_CONFIG_METRICS_REPORTER_IMPL = "metrics_reporter.impl";

    /** An arbitrary value larger than the largest time we might want to hold a wake lock. */
    private static final long WAKELOCK_ACQUIRE_MILLIS = Duration.ofMinutes(1).toMillis();

    /**
     * For every hour spent passive listening, 40 seconds of active listening are allowed, i.e.
     * 90 passive time units : 1 active time units.
     */
    private static final long DEFAULT_PASSIVE_TO_ACTIVE_RATIO = (60 * 60) / 40;
    private static final String DEVICE_CONFIG_KEY_SUFFIX_PASSIVE_TO_ACTIVE_RATIO =
            "passive_to_active_ratio";

    private static final Duration DEFAULT_MINIMUM_PASSIVE_LISTENING_DURATION =
            Duration.ofMinutes(2);
    private static final String DEVICE_CONFIG_KEY_SUFFIX_MINIMUM_PASSIVE_LISTENING_DURATION_MILLIS =
            "min_passive_listening_duration_millis";

    private static final Duration DEFAULT_LOCATION_NOT_KNOWN_AGE_THRESHOLD = Duration.ofMinutes(1);
    private static final String DEVICE_CONFIG_KEY_SUFFIX_LOCATION_NOT_KNOWN_AGE_THRESHOLD_MILLIS =
            "location_not_known_age_threshold_millis";

    private static final Duration DEFAULT_LOCATION_KNOWN_AGE_THRESHOLD = Duration.ofMinutes(15);
    private static final String DEVICE_CONFIG_KEY_SUFFIX_LOCATION_KNOWN_AGE_THRESHOLD_MILLIS =
            "location_known_age_threshold_millis";

    private static final Duration DEFAULT_MINIMUM_ACTIVE_LISTENING_DURATION = Duration.ofSeconds(5);
    private static final String DEVICE_CONFIG_KEY_SUFFIX_MINIMUM_ACTIVE_LISTENING_DURATION_MILLIS =
            "min_active_listening_duration_millis";

    private static final Duration DEFAULT_MAXIMUM_ACTIVE_LISTENING_DURATION =
            Duration.ofSeconds(10);
    private static final String DEVICE_CONFIG_KEY_SUFFIX_MAXIMUM_ACTIVE_LISTENING_DURATION_MILLIS =
            "max_active_listening_duration_millis";

    private static final Duration DEFAULT_INITIAL_ACTIVE_LISTENING_BUDGET =
            DEFAULT_MINIMUM_ACTIVE_LISTENING_DURATION;
    private static final String DEVICE_CONFIG_KEY_SUFFIX_INITIAL_ACTIVE_LISTENING_BUDGET_MILLIS =
            "min_initial_active_listening_budget_millis";

    private static final Duration DEFAULT_MAX_ACTIVE_LISTENING_BUDGET =
            DEFAULT_MAXIMUM_ACTIVE_LISTENING_DURATION.multipliedBy(4);
    private static final String DEVICE_CONFIG_KEY_SUFFIX_MAX_ACTIVE_LISTENING_BUDGET_MILLIS =
            "max_active_listening_budget_millis";

    @NonNull private final LocationManager mLocationManager;
    @NonNull private final Handler mHandler;
    @NonNull private final Consumer<TimeZoneProviderResult> mResultConsumer;
    @NonNull private final HandlerExecutor mExecutor;
    @NonNull private final File mGeoDataFile;
    @NonNull private final PowerManager.WakeLock mWakeLock;
    @NonNull private final String mDeviceConfigNamespace;
    @NonNull private final String mDeviceConfigKeyPrefix;
    @NonNull private final DelegatingLocationListeningAccountant mLocationListeningAccountant;
    @NonNull private final MetricsReporter mMetricsReporter;

    EnvironmentImpl(@NonNull Context context,
            @NonNull Consumer<TimeZoneProviderResult> resultConsumer) {
        mLocationManager = context.getSystemService(LocationManager.class);

        PowerManager powerManager = context.getSystemService(PowerManager.class);
        mWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, LOG_TAG + ":wakelock");

        mResultConsumer = Objects.requireNonNull(resultConsumer);
        mHandler = new Handler(Looper.getMainLooper());
        mExecutor = new HandlerExecutor(mHandler);

        ClassLoader classLoader = getClass().getClassLoader();
        Properties configProperties = loadConfigProperties(classLoader);
        GeoDataFileManager geoDataFileManager =
                createGeoDataFileManager(context, classLoader, configProperties);
        mGeoDataFile = geoDataFileManager.getGeoDataFile();

        mDeviceConfigNamespace = Objects.requireNonNull(
                configProperties.getProperty(RESOURCE_CONFIG_KEY_DEVICE_CONFIG_NAMESPACE));
        mDeviceConfigKeyPrefix = Objects.requireNonNull(
                configProperties.getProperty(RESOURCE_CONFIG_KEY_DEVICE_CONFIG_KEY_PREFIX));

        mMetricsReporter = createMetricsReporter(classLoader, configProperties);

        LocationListeningAccountant realLocationListeningAccountant =
                createRealLocationListeningAccountant();
        mLocationListeningAccountant =
                new DelegatingLocationListeningAccountant(realLocationListeningAccountant);

        Duration initialActiveListeningBudget = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_INITIAL_ACTIVE_LISTENING_BUDGET_MILLIS,
                DEFAULT_INITIAL_ACTIVE_LISTENING_BUDGET);
        mLocationListeningAccountant.depositActiveListeningAmount(initialActiveListeningBudget);

        // Monitor for changes that affect the accountant's configuration.
        DeviceConfig.addOnPropertiesChangedListener(
                mDeviceConfigNamespace, mExecutor, this::handleDeviceConfigChanged);
    }

    private static GeoDataFileManager createGeoDataFileManager(
            @NonNull Context context, @NonNull ClassLoader classLoader,
            @NonNull Properties configProperties) {
        String className =
                configProperties.getProperty(RESOURCE_CONFIG_KEY_GEODATA_FILE_MANAGER_IMPL);
        try {
            Class<?> clazz = classLoader.loadClass(className);
            GeoDataFileManager geoDataFileManager =
                    (GeoDataFileManager) clazz.getConstructor().newInstance();
            geoDataFileManager.init(context, configProperties);
            return geoDataFileManager;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate GeoDataFileManager", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize GeoDataFileManager", e);
        }
    }

    private static MetricsReporter createMetricsReporter(
            @NonNull ClassLoader classLoader, @NonNull Properties configProperties) {
        String className = configProperties.getProperty(RESOURCE_CONFIG_METRICS_REPORTER_IMPL);
        try {
            Class<?> clazz = classLoader.loadClass(className);
            return (MetricsReporter) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate MetricsReporter", e);
        }
    }

    private static Properties loadConfigProperties(ClassLoader classLoader) {
        Properties configProperties = new Properties();
        try (InputStream configStream =
                classLoader.getResourceAsStream(RESOURCE_CONFIG_PROPERTIES_NAME)) {
            if (configStream == null) {
                throw new IllegalStateException("Unable to find config properties"
                        + " resource=" + RESOURCE_CONFIG_PROPERTIES_NAME);
            }
            configProperties.load(configStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load config properties from"
                    + " resource=" + RESOURCE_CONFIG_PROPERTIES_NAME, e);
        }
        return configProperties;
    }

    private void handleDeviceConfigChanged(@NonNull DeviceConfig.Properties properties) {
        LocationListeningAccountant newAccountant = createRealLocationListeningAccountant();
        mLocationListeningAccountant.replaceDelegate(newAccountant);
    }

    @NonNull
    private LocationListeningAccountant createRealLocationListeningAccountant() {
        Duration minPassiveListeningDuration = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_MINIMUM_PASSIVE_LISTENING_DURATION_MILLIS,
                DEFAULT_MINIMUM_PASSIVE_LISTENING_DURATION);
        Duration maxActiveListeningBalance = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_MAX_ACTIVE_LISTENING_BUDGET_MILLIS,
                DEFAULT_MAX_ACTIVE_LISTENING_BUDGET);
        Duration minActiveListeningDuration = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_MINIMUM_ACTIVE_LISTENING_DURATION_MILLIS,
                DEFAULT_MINIMUM_ACTIVE_LISTENING_DURATION);
        Duration maxActiveListeningDuration = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_MAXIMUM_ACTIVE_LISTENING_DURATION_MILLIS,
                DEFAULT_MAXIMUM_ACTIVE_LISTENING_DURATION);
        Duration locationNotKnownAgeThreshold = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_LOCATION_NOT_KNOWN_AGE_THRESHOLD_MILLIS,
                DEFAULT_LOCATION_NOT_KNOWN_AGE_THRESHOLD);
        Duration locationKnownAgeThreshold = getDeviceConfigDuration(
                DEVICE_CONFIG_KEY_SUFFIX_LOCATION_KNOWN_AGE_THRESHOLD_MILLIS,
                DEFAULT_LOCATION_KNOWN_AGE_THRESHOLD);
        long passiveToActiveRatio = getDeviceConfigLong(
                DEVICE_CONFIG_KEY_SUFFIX_PASSIVE_TO_ACTIVE_RATIO,
                DEFAULT_PASSIVE_TO_ACTIVE_RATIO);
        return new RealLocationListeningAccountant(
                minPassiveListeningDuration, maxActiveListeningBalance,
                minActiveListeningDuration, maxActiveListeningDuration,
                locationNotKnownAgeThreshold, locationKnownAgeThreshold,
                passiveToActiveRatio);
    }

    @Override
    @NonNull
    public LocationListeningAccountant getLocationListeningAccountant() {
        return mLocationListeningAccountant;
    }

    @Override
    @NonNull
    public <T> Cancellable requestDelayedCallback(
            @NonNull Consumer<T> callback, @Nullable T callbackToken, @NonNull Duration delay) {
        Objects.requireNonNull(callback);
        Objects.requireNonNull(delay);

        // Deliberate use of an anonymous class as the equality of lambdas is not well defined but
        // instance equality is required for the remove call.
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                callback.accept(callbackToken);
            }
        };

        String identifier = delay + "@" + formatElapsedRealtimeMillis(elapsedRealtimeMillis());
        Cancellable cancellable = new BaseCancellable(identifier) {
            @Override
            public void onCancel() {
                mHandler.removeCallbacks(runnable);
            }
        };

        mHandler.postDelayed(runnable, delay.toMillis());
        return cancellable;
    }

    @Override
    @NonNull
    public Cancellable startPassiveLocationListening(@NonNull Duration duration,
            @NonNull Consumer<LocationListeningResult> listeningResultConsumer,
            @NonNull Consumer<Duration> passiveListeningCompletedCallback) {
        Objects.requireNonNull(duration);
        Objects.requireNonNull(listeningResultConsumer);

        try {
            // Keep a wakelock while we call requestLocationUpdates() so we can calculate a fairly
            // accurate (upper bound) of how long the device has been passively listening.
            acquireWakeLock();
            long startElapsedRealtimeMillis = elapsedRealtimeMillis();

            // Deliberate use of an anonymous class as the equality of lambdas is not well defined.
            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    long resultElapsedRealtimeMillis = elapsedRealtimeMillis();
                    LocationListeningResult result = new LocationListeningResult(
                            LOCATION_LISTEN_MODE_PASSIVE,
                            duration, startElapsedRealtimeMillis,
                            resultElapsedRealtimeMillis,
                            location);

                    // Note: We only log metrics when listening stops (since we're primarily
                    // interested in active usage for system health reasons). Passive listening
                    // continues until it is cancelled or the timeout managed by this class
                    // triggers, so there is no need to do metrics logging here.

                    listeningResultConsumer.accept(result);
                }
            };

            // A value that tries to ensure we will get some updates while listening.
            long minUpdateInterval = duration.dividedBy(3).toMillis();

            // Using the passive provider means we will potentially see GPS-originated locations
            // in addition to just "fused" locationprovider updates.
            // We don't use the setDurationMillis() with a real time, since we handle our own
            // timeout below, which provides an explicit callback when listening stops.
            LocationRequest locationRequest = new LocationRequest.Builder(minUpdateInterval)
                    .setMinUpdateDistanceMeters(0)
                    .setMinUpdateIntervalMillis(minUpdateInterval)
                    .setDurationMillis(Long.MAX_VALUE  /* indefinite updates, until cancelled */)
                    .setMaxUpdates(Integer.MAX_VALUE /* indefinite updates, until cancelled */)
                    .setQuality(QUALITY_BALANCED_POWER_ACCURACY) /* try not to be power hungry */
                    .setMaxUpdateDelayMillis(0 /* no batching */)
                    .build();

            mLocationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER, locationRequest, mExecutor, locationListener);

            String callbackIdentifier = "passive:" + duration + "@"
                    + formatElapsedRealtimeMillis(startElapsedRealtimeMillis);
            Consumer<String> timeoutCallback = token -> {
                // When the timeout triggers we must cancel the location listening.
                mLocationManager.removeUpdates(locationListener);

                // Also calculate the amount of time the device was actually listening. Because
                // passive listening doesn't prevent sleep, we calculate the actual time, not the
                // time we asked for. This is done under a partial wake lock to ensure we don't
                // sleep in the middle and get misleading values.
                try {
                    acquireWakeLock();
                    long actualTimeListeningMillis =
                            elapsedRealtimeMillis() - startElapsedRealtimeMillis;
                    Duration actualTimeListening = Duration.ofMillis(actualTimeListeningMillis);
                    passiveListeningCompletedCallback.accept(actualTimeListening);

                    mMetricsReporter.reportLocationListeningCompletedEvent(
                            LOCATION_LISTEN_MODE_PASSIVE, duration.toMillis(),
                            actualTimeListeningMillis,
                            MetricsReporter.LISTENING_STOPPED_REASON_TIMED_OUT);
                } finally {
                    releaseWakeLock();
                }
            };
            Cancellable timeoutCancellable =
                    requestDelayedCallback(timeoutCallback, callbackIdentifier, duration);

            return new BaseCancellable(callbackIdentifier) {
                @Override
                public void onCancel() {
                    long timeListeningMillis = elapsedRealtimeMillis() - startElapsedRealtimeMillis;
                    mMetricsReporter.reportLocationListeningCompletedEvent(
                            LOCATION_LISTEN_MODE_PASSIVE, duration.toMillis(), timeListeningMillis,
                            MetricsReporter.LISTENING_STOPPED_REASON_CANCELLED);

                    timeoutCancellable.cancel();
                    mLocationManager.removeUpdates(locationListener);
                }
            };
        } finally {
            releaseWakeLock();
        }
    }

    @NonNull
    @Override
    public Cancellable startActiveGetCurrentLocation(@NonNull Duration duration,
            @NonNull Consumer<LocationListeningResult> locationResultConsumer) {
        try {
            // Keep a wakelock while we call getCurrentLocation() so we can calculate a fairly
            // accurate (upper bound) of how long the device has been actively listening when we
            // get a result.
            acquireWakeLock();

            CancellationSignal cancellationSignal = new CancellationSignal();
            long startElapsedRealtimeMillis = elapsedRealtimeMillis();
            String identifier = "active:"  + duration + "@"
                    + formatElapsedRealtimeMillis(startElapsedRealtimeMillis);
            Cancellable locationListenerCancellable = new BaseCancellable(identifier) {
                @Override
                protected void onCancel() {
                    long timeListeningMillis = elapsedRealtimeMillis() - startElapsedRealtimeMillis;
                    mMetricsReporter.reportLocationListeningCompletedEvent(
                            LOCATION_LISTEN_MODE_ACTIVE, duration.toMillis(), timeListeningMillis,
                            MetricsReporter.LISTENING_STOPPED_REASON_CANCELLED);

                    cancellationSignal.cancel();
                }
            };

            long intervalMillis = 0; // Not used
            long requestedDurationMillis = duration.toMillis();
            LocationRequest locationRequest = new LocationRequest.Builder(intervalMillis)
                    .setDurationMillis(requestedDurationMillis)
                    .setQuality(QUALITY_HIGH_ACCURACY /* try to force GPS on when it's needed */)
                    .setMaxUpdateDelayMillis(0 /* no batching */)
                    .build();

            Consumer<Location> locationConsumer = location -> {
                long resultElapsedRealtimeMillis = elapsedRealtimeMillis();
                LocationListeningResult result = new LocationListeningResult(
                        LOCATION_LISTEN_MODE_ACTIVE,
                        duration,
                        startElapsedRealtimeMillis,
                        resultElapsedRealtimeMillis,
                        location);
                mMetricsReporter.reportLocationListeningCompletedEvent(
                        LOCATION_LISTEN_MODE_ACTIVE,
                        result.getRequestedListeningDuration().toMillis(),
                        result.getTotalEstimatedTimeListening().toMillis(),
                        MetricsReporter.LISTENING_STOPPED_REASON_LOCATION_OBTAINED);

                locationResultConsumer.accept(result);
            };

            mLocationManager.getCurrentLocation(
                    LocationManager.FUSED_PROVIDER, locationRequest, cancellationSignal, mExecutor,
                    locationConsumer);

            return locationListenerCancellable;
        } finally {
            releaseWakeLock();
        }
    }

    @Override
    @NonNull
    public GeoTimeZonesFinder createGeoTimeZoneFinder() throws IOException {
        return GeoTimeZonesFinder.create(mGeoDataFile);
    }

    @Override
    public void reportTimeZoneProviderResult(@NonNull TimeZoneProviderResult result) {
        mResultConsumer.accept(result);
    }

    @Override
    public void acquireWakeLock() {
        mWakeLock.acquire(WAKELOCK_ACQUIRE_MILLIS);
    }

    @Override
    public void releaseWakeLock() {
        mWakeLock.release();
    }

    @Override
    public long elapsedRealtimeMillis() {
        return SystemClock.elapsedRealtime();
    }

    @NonNull
    private Duration getDeviceConfigDuration(@NonNull String key, @NonNull Duration defaultValue) {
        Objects.requireNonNull(defaultValue);

        long deviceConfigValue = getDeviceConfigLong(key, -1);
        if (deviceConfigValue < 0) {
            return defaultValue;
        }
        return Duration.ofMillis(deviceConfigValue);
    }

    private long getDeviceConfigLong(@NonNull String keySuffix, long defaultValue) {
        String key = mDeviceConfigKeyPrefix + keySuffix;
        return DeviceConfig.getLong(mDeviceConfigNamespace, key, defaultValue);
    }

    private static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        HandlerExecutor(@NonNull Handler handler) {
            mHandler = Objects.requireNonNull(handler);
        }

        @Override
        public void execute(Runnable command) {
            if (!mHandler.post(command)) {
                throw new RejectedExecutionException(mHandler + " is shutting down");
            }
        }
    }

    private abstract static class BaseCancellable implements Cancellable {
        final String mIdentifier;
        boolean mCancelled = false;

        BaseCancellable(String identifier) {
            mIdentifier = identifier;
        }

        @Override
        public void cancel() {
            if (!mCancelled) {
                mCancelled = true;
                onCancel();
            }
        }

        protected abstract void onCancel();

        @Override
        public String toString() {
            return "{"
                    + "mIdentifier=" + mIdentifier
                    + ", mCancelled=" + mCancelled
                    + '}';
        }
    }
}
