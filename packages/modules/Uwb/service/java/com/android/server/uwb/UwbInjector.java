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

package com.android.server.uwb;

import static android.Manifest.permission.UWB_RANGING;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.ApexEnvironment;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionManager;
import android.provider.Settings;
import android.util.AtomicFile;
import android.util.Log;

import com.android.server.uwb.advertisement.UwbAdvertiseManager;
import com.android.server.uwb.correction.UwbFilterEngine;
import com.android.server.uwb.correction.filtering.IFilter;
import com.android.server.uwb.correction.filtering.MedAvgFilter;
import com.android.server.uwb.correction.filtering.MedAvgRotationFilter;
import com.android.server.uwb.correction.filtering.PositionFilterImpl;
import com.android.server.uwb.correction.pose.GyroPoseSource;
import com.android.server.uwb.correction.pose.IPoseSource;
import com.android.server.uwb.correction.pose.IntegPoseSource;
import com.android.server.uwb.correction.pose.RotationPoseSource;
import com.android.server.uwb.correction.pose.SixDofPoseSource;
import com.android.server.uwb.correction.primers.AoaPrimer;
import com.android.server.uwb.correction.primers.BackAzimuthPrimer;
import com.android.server.uwb.correction.primers.ElevationPrimer;
import com.android.server.uwb.correction.primers.FovPrimer;
import com.android.server.uwb.data.ServiceProfileData;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.multchip.UwbMultichipData;
import com.android.server.uwb.pm.ProfileManager;
import com.android.uwb.flags.FeatureFlags;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * To be used for dependency injection (especially helps mocking static dependencies).
 */
public class UwbInjector {
    private static final String TAG = "UwbInjector";
    private static final String APEX_NAME = "com.android.uwb";
    private static final String VENDOR_SERVICE_NAME = "uwb_vendor";
    private static final String BOOT_DEFAULT_UWB_COUNTRY_CODE = "ro.boot.uwbcountrycode";

    /**
     * The path where the Uwb apex is mounted.
     * Current value = "/apex/com.android.uwb"
     */
    private static final String UWB_APEX_PATH =
            new File("/apex", APEX_NAME).getAbsolutePath();
    private static final int APP_INFO_FLAGS_SYSTEM_APP =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

    private final UwbContext mContext;
    private final Looper mLooper;
    private final PermissionManager mPermissionManager;
    private final UserManager mUserManager;
    private final UwbConfigStore mUwbConfigStore;
    private final ProfileManager mProfileManager;
    private final UwbSettingsStore mUwbSettingsStore;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbCountryCode mUwbCountryCode;
    private final UciLogModeStore mUciLogModeStore;
    private final UwbServiceCore mUwbService;
    private final UwbMetrics mUwbMetrics;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final UwbMultichipData mUwbMultichipData;
    private final SystemBuildProperties mSystemBuildProperties;
    private final UwbDiagnostics mUwbDiagnostics;
    private IPoseSource mDefaultPoseSource;
    private final ReentrantLock mPoseLock = new ReentrantLock();
    private int mPoseSourceRefCount = 0;

    private final UwbSessionManager mUwbSessionManager;
    private final FeatureFlags mFeatureFlags;

    public UwbInjector(@NonNull UwbContext context) {
        // Create UWB service thread.
        HandlerThread uwbHandlerThread = new HandlerThread("UwbService");
        uwbHandlerThread.start();
        mLooper = uwbHandlerThread.getLooper();

        mContext = context;
        mPermissionManager = context.getSystemService(PermissionManager.class);
        mUserManager = mContext.getSystemService(UserManager.class);
        mUwbConfigStore = new UwbConfigStore(context, new Handler(mLooper), this,
                UwbConfigStore.createSharedFiles());
        mProfileManager = new ProfileManager(context, new Handler(mLooper),
                mUwbConfigStore, this);
        mUwbSettingsStore = new UwbSettingsStore(
                context, new Handler(mLooper),
                new AtomicFile(new File(getDeviceProtectedDataDir(),
                        UwbSettingsStore.FILE_NAME)), this);
        mUwbMultichipData = new UwbMultichipData(mContext);
        mUciLogModeStore = new UciLogModeStore(mUwbSettingsStore);
        mNativeUwbManager = new NativeUwbManager(this, mUciLogModeStore, mUwbMultichipData);
        mUwbCountryCode =
                new UwbCountryCode(mContext, mNativeUwbManager, new Handler(mLooper), this);
        mUwbMetrics = new UwbMetrics(this);
        mDeviceConfigFacade = new DeviceConfigFacade(new Handler(mLooper), mContext);
        UwbConfigurationManager uwbConfigurationManager =
                new UwbConfigurationManager(mNativeUwbManager, this);
        UwbSessionNotificationManager uwbSessionNotificationManager =
                new UwbSessionNotificationManager(this);
        UwbAdvertiseManager uwbAdvertiseManager = new UwbAdvertiseManager(this,
                mDeviceConfigFacade);
        mUwbSessionManager =
                new UwbSessionManager(uwbConfigurationManager, mNativeUwbManager, mUwbMetrics,
                        uwbAdvertiseManager, uwbSessionNotificationManager, this,
                        mContext.getSystemService(AlarmManager.class),
                        mContext.getSystemService(ActivityManager.class),
                        mLooper);
        mUwbService = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, uwbConfigurationManager, this, mLooper);
        mSystemBuildProperties = new SystemBuildProperties();
        mUwbDiagnostics = new UwbDiagnostics(mContext, this, mSystemBuildProperties);
        mFeatureFlags = new com.android.uwb.flags.FeatureFlagsImpl();
    }

    public FeatureFlags getFeatureFlags() {
        return mFeatureFlags;
    }

    public Looper getUwbServiceLooper() {
        return mLooper;
    }

    public UserManager getUserManager() {
        return mUserManager;
    }

    /**
     * Construct an instance of {@link ServiceProfileData}.
     */
    public ServiceProfileData makeServiceProfileData(ServiceProfileData.DataSource dataSource) {
        return new ServiceProfileData(dataSource);
    }

    public ProfileManager getProfileManager() {
        return mProfileManager;
    }

    public UwbConfigStore getUwbConfigStore() {
        return mUwbConfigStore;
    }

    public UwbSettingsStore getUwbSettingsStore() {
        return mUwbSettingsStore;
    }

    public NativeUwbManager getNativeUwbManager() {
        return mNativeUwbManager;
    }

    public UwbCountryCode getUwbCountryCode() {
        return mUwbCountryCode;
    }

    public UciLogModeStore getUciLogModeStore() {
        return mUciLogModeStore;
    }

    public UwbMetrics getUwbMetrics() {
        return mUwbMetrics;
    }

    public DeviceConfigFacade getDeviceConfigFacade() {
        return mDeviceConfigFacade;
    }

    public UwbMultichipData getMultichipData() {
        return mUwbMultichipData;
    }

    public UwbServiceCore getUwbServiceCore() {
        return mUwbService;
    }

    public UwbDiagnostics getUwbDiagnostics() {
        return mUwbDiagnostics;
    }

    public UwbSessionManager getUwbSessionManager() {
        return mUwbSessionManager;
    }

    /**
     * Create a UwbShellCommand instance.
     */
    public UwbShellCommand makeUwbShellCommand(UwbServiceImpl uwbService) {
        return new UwbShellCommand(this, uwbService, mContext);
    }

    /**
     * Creates a Geocoder.
     */
    @Nullable
    public Geocoder makeGeocoder() {
        return new Geocoder(mContext);
    }

    /**
     * Returns whether geocoder is supported on this device or not.
     */
    public boolean isGeocoderPresent() {
        return Geocoder.isPresent();
    }

    /**
     * Throws security exception if the UWB_RANGING permission is not granted for the calling app.
     *
     * <p>Should be used in situations where the app op should not be noted.
     */
    public void enforceUwbRangingPermissionForPreflight(
            @NonNull AttributionSource attributionSource) {
        if (!attributionSource.checkCallingUid()) {
            throw new SecurityException("Invalid attribution source " + attributionSource
                    + ", callingUid: " + Binder.getCallingUid());
        }
        int permissionCheckResult = mPermissionManager.checkPermissionForPreflight(
                UWB_RANGING, attributionSource);
        if (permissionCheckResult != PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold UWB_RANGING permission");
        }
    }

    /**
     * Returns true if the UWB_RANGING permission is granted for the calling app.
     *
     * <p>Used for checking permission before first data delivery for the session.
     */
    public boolean checkUwbRangingPermissionForStartDataDelivery(
            @NonNull AttributionSource attributionSource, @NonNull String message) {
        int permissionCheckResult = mPermissionManager.checkPermissionForStartDataDelivery(
                UWB_RANGING, attributionSource, message);
        return permissionCheckResult == PERMISSION_GRANTED;
    }

    /** Indicate permission manager that the ranging session is done or stopped. */
    public void finishUwbRangingPermissionForDataDelivery(
            @NonNull AttributionSource attributionSource) {
        mPermissionManager.finishDataDelivery(UWB_RANGING, attributionSource);
    }

    /**
     * Get device protected storage dir for the UWB apex.
     */
    @NonNull
    public static File getDeviceProtectedDataDir() {
        return ApexEnvironment.getApexEnvironment(APEX_NAME).getDeviceProtectedDataDir();
    }

    /**
     * Get integer value from Settings.
     *
     * @throws Settings.SettingNotFoundException
     */
    public int getGlobalSettingsInt(@NonNull String key) throws Settings.SettingNotFoundException {
        return Settings.Global.getInt(mContext.getContentResolver(), key);
    }

    /**
     * Get integer value from Settings.
     */
    public int getGlobalSettingsInt(@NonNull String key, int defValue) {
        return Settings.Global.getInt(mContext.getContentResolver(), key, defValue);
    }

    /**
     * Get string value from Settings.
     */
    @Nullable
    public String getGlobalSettingsString(@NonNull String key) {
        return Settings.Global.getString(mContext.getContentResolver(), key);
    }

    /**
     * Helper method for classes to register a ContentObserver
     * {@see ContentResolver#registerContentObserver(Uri,boolean,ContentObserver)}.
     */
    public void registerContentObserver(Uri uri, boolean notifyForDescendants,
            ContentObserver contentObserver) {
        mContext.getContentResolver().registerContentObserver(uri, notifyForDescendants,
                contentObserver);
    }

    /**
     * Helper method for classes to unregister a ContentObserver
     * {@see ContentResolver#unregisterContentObserver(ContentObserver)}.
     */
    public void unregisterContentObserver(ContentObserver contentObserver) {
        mContext.getContentResolver().unregisterContentObserver(contentObserver);
    }

    /**
     * Uwb user specific folder.
     */
    public static File getCredentialProtectedDataDirForUser(int userId) {
        return ApexEnvironment.getApexEnvironment(APEX_NAME)
                .getCredentialProtectedDataDirForUser(UserHandle.of(userId));
    }

    /**
     * Returns true if the app is in the Uwb apex, false otherwise.
     * Checks if the app's path starts with "/apex/com.android.uwb".
     */
    public static boolean isAppInUwbApex(ApplicationInfo appInfo) {
        return appInfo.sourceDir.startsWith(UWB_APEX_PATH);
    }

    /**
     * Get the current time of the clock in milliseconds.
     *
     * @return Current time in milliseconds.
     */
    public long getWallClockMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Returns milliseconds since boot, including time spent in sleep.
     *
     * @return Current time since boot in milliseconds.
     */
    public long getElapsedSinceBootMillis() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * Returns nanoseconds since boot, including time spent in sleep.
     *
     * @return Current time since boot in milliseconds.
     */
    public long getElapsedSinceBootNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Is this a valid country code
     *
     * @param countryCode A 2-Character alphanumeric country code.
     * @return true if the countryCode is valid, false otherwise.
     */
    private static boolean isValidCountryCode(String countryCode) {
        return countryCode != null && countryCode.length() == 2
                && countryCode.chars().allMatch(Character::isLetterOrDigit);
    }

    /**
     * Default country code stored in system property
     *
     * @return Country code if available, null otherwise.
     */
    public String getOemDefaultCountryCode() {
        String country = SystemProperties.get(BOOT_DEFAULT_UWB_COUNTRY_CODE);
        return isValidCountryCode(country) ? country.toUpperCase(Locale.US) : null;
    }

    /**
     * Helper method creating a context based on the app's uid (to deal with multi user scenarios)
     */
    @Nullable
    private Context createPackageContextAsUser(int uid) {
        Context userContext;
        try {
            userContext = mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unknown package name");
            return null;
        }
        if (userContext == null) {
            Log.e(TAG, "Unable to retrieve user context for " + uid);
            return null;
        }
        return userContext;
    }

    /** Helper method to check if the app is a system app. */
    public boolean isSystemApp(int uid, @NonNull String packageName) {
        try {
            ApplicationInfo info = createPackageContextAsUser(uid)
                    .getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return (info.flags & APP_INFO_FLAGS_SYSTEM_APP) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            // In case of exception, assume unknown app (more strict checking)
            // Note: This case will never happen since checkPackage is
            // called to verify validity before checking App's version.
            Log.e(TAG, "Failed to get the app info", e);
        }
        return false;
    }

    /** Whether the uid is signed with the same key as the platform. */
    public boolean isAppSignedWithPlatformKey(int uid) {
        return mContext.getPackageManager().checkSignatures(uid, Process.SYSTEM_UID)
                == PackageManager.SIGNATURE_MATCH;
    }

    private static Map<String, Integer> sOverridePackageImportance = new HashMap();
    public void setOverridePackageImportance(String packageName, int importance) {
        sOverridePackageImportance.put(packageName, importance);
    }
    public void resetOverridePackageImportance(String packageName) {
        sOverridePackageImportance.remove(packageName);
    }

    /** Helper method to retrieve app importance. */
    private int getPackageImportance(int uid, @NonNull String packageName) {
        if (sOverridePackageImportance.containsKey(packageName)) {
            Log.w(TAG, "Overriding package importance for testing");
            return sOverridePackageImportance.get(packageName);
        }
        try {
            return createPackageContextAsUser(uid)
                    .getSystemService(ActivityManager.class)
                    .getPackageImportance(packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to retrieve the app importance", e);
            return ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
        }
    }

    /** Helper method to check if the app is from foreground app/service. */
    public static boolean isForegroundAppOrServiceImportance(int importance) {
        return importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
    }

    /** Helper method to check if the app is from foreground app/service. */
    public boolean isForegroundAppOrService(int uid, @NonNull String packageName) {
        long identity = Binder.clearCallingIdentity();
        try {
            return isForegroundAppOrServiceImportance(getPackageImportance(uid, packageName));
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to retrieve the app importance", e);
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /* Helps to mock the executor for tests */
    public int runTaskOnSingleThreadExecutor(FutureTask<Integer> task, int timeoutMs)
            throws InterruptedException, TimeoutException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(task);
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            executor.shutdownNow();
            throw e;
        }
    }

    /**
     * Gets the configured pose source, which is reference counted. If there are no references
     * to the pose source, one will be created based on the device configuration. This may
     * @return A shared or new pose source, or null if one is not configured or available.
     */
    public IPoseSource acquirePoseSource() {
        mPoseLock.lock();
        try {
            // Keep our ref counts accurate because isEnableFilters can change at runtime.
            mPoseSourceRefCount++;

            if (!getDeviceConfigFacade().isEnableFilters()) {
                return null;
            }

            if (mDefaultPoseSource != null) {
                // Already have a pose source.
                return mDefaultPoseSource;
            }

            switch (mDeviceConfigFacade.getPoseSourceType()) {
                case NONE:
                    mDefaultPoseSource = null;
                    break;
                case ROTATION_VECTOR:
                    mDefaultPoseSource = new RotationPoseSource(mContext, 100);
                    break;
                case GYRO:
                    mDefaultPoseSource = new GyroPoseSource(mContext, 100);
                    break;
                case SIXDOF:
                    mDefaultPoseSource = new SixDofPoseSource(mContext, 100);
                    break;
                case DOUBLE_INTEGRATE:
                    mDefaultPoseSource = new IntegPoseSource(mContext, 100);
                    break;
            }

            return mDefaultPoseSource;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to create the configured UWB pose source: "
                    + ex.getMessage());
            mPoseSourceRefCount--;
            return null;
        } finally {
            mPoseLock.unlock();
        }
    }

    /**
     * Decrements the reference counts to the default pose source, and closes the source once the
     * count reaches zero. This must be called once for each time acquirePoseSource() is called.
     */
    public void releasePoseSource() {
        mPoseLock.lock();
        try {
            // Keep our ref counts accurate because isEnableFilters can change at runtime.
            --mPoseSourceRefCount;
            if (mPoseSourceRefCount <= 0 && mDefaultPoseSource != null) {
                mDefaultPoseSource.close();
                mDefaultPoseSource = null;
            }
        } finally {
            mPoseLock.unlock();
        }
    }

    /**
     * Creates a filter engine using the default pose source. A default pose source must first be
     * acquired with {@link #acquirePoseSource()}.
     *
     * @return A fully configured filter engine, or null if filtering is disabled.
     */
    public UwbFilterEngine createFilterEngine(IPoseSource poseSource) {
        DeviceConfigFacade cfg = getDeviceConfigFacade();
        if (!cfg.isEnableFilters()) {
            return null;
        }

        // This could go wrong if the config flags or overlay have bad values.
        try {
            IFilter azimuthFilter = new MedAvgRotationFilter(
                    cfg.getFilterAngleWindow(),
                    cfg.getFilterAngleInliersPercent() / 100f);
            IFilter elevationFilter = new MedAvgRotationFilter(
                    cfg.getFilterAngleWindow(),
                    cfg.getFilterAngleInliersPercent() / 100f);
            IFilter distanceFilter = new MedAvgFilter(
                    cfg.getFilterDistanceWindow(),
                    cfg.getFilterDistanceInliersPercent() / 100f);

            PositionFilterImpl posFilter = new PositionFilterImpl(
                    azimuthFilter,
                    elevationFilter,
                    distanceFilter);

            UwbFilterEngine.Builder builder = new UwbFilterEngine.Builder().setFilter(posFilter);

            if (poseSource != null) {
                builder.setPoseSource(poseSource);
            }

            // Order is important.
            if (cfg.isEnablePrimerEstElevation()) {
                builder.addPrimer(new ElevationPrimer());
            }

            // AoAPrimer requires an elevation estimation in order to convert to spherical coords.
            if (cfg.isEnablePrimerAoA()) {
                builder.addPrimer(new AoaPrimer());
            }

            // Fov requires an elevation and a spherical coord.
            if (cfg.isEnablePrimerFov()) {
                builder.addPrimer(new FovPrimer(cfg.getPrimerFovDegree()));
            }

            // Back azimuth detection requires true spherical.
            if (cfg.isEnableBackAzimuth()) {
                builder.addPrimer(new BackAzimuthPrimer(
                        cfg.getFrontAzimuthRadiansPerSecond(),
                        cfg.getBackAzimuthRadiansPerSecond(),
                        cfg.getBackAzimuthWindow(),
                        cfg.isEnableBackAzimuthMasking(),
                        cfg.getMirrorScoreStdRadians(),
                        cfg.getBackNoiseInfluenceCoeff()));
            }

            return builder.build();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to create UWB filter engine: " + ex.getMessage());
            return null;
        }
    }
}
