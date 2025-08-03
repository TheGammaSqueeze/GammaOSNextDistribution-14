/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.car.Car;
import android.car.Car.FeaturerRequestEnum;
import android.car.CarFeatures;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.AtomicFileHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Pair;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Component controlling the feature of car.
 */
public final class CarFeatureController implements CarServiceBase {

    private static final String TAG = CarLog.tagFor(CarFeatureController.class);

    // We define this here for compatibility with older feature lists only
    private static final String BLUETOOTH_SERVICE = "car_bluetooth";

    private static final List<String> NON_FLAGGED_MANDATORY_FEATURES = List.of(
            Car.APP_FOCUS_SERVICE,
            Car.AUDIO_SERVICE,
            Car.CAR_ACTIVITY_SERVICE,
            Car.CAR_BUGREPORT_SERVICE,
            Car.CAR_DEVICE_POLICY_SERVICE,
            Car.CAR_DRIVING_STATE_SERVICE,
            Car.CAR_INPUT_SERVICE,
            Car.CAR_MEDIA_SERVICE,
            Car.CAR_OCCUPANT_ZONE_SERVICE,
            Car.CAR_PERFORMANCE_SERVICE,
            Car.CAR_USER_SERVICE,
            Car.CAR_UX_RESTRICTION_SERVICE,
            Car.CAR_WATCHDOG_SERVICE,
            Car.INFO_SERVICE,
            Car.PACKAGE_SERVICE,
            Car.POWER_SERVICE,
            Car.PROJECTION_SERVICE,
            Car.PROPERTY_SERVICE,
            Car.TEST_SERVICE,
            // All items below here are deprecated, but still should be supported
            BLUETOOTH_SERVICE,
            Car.CABIN_SERVICE,
            Car.HVAC_SERVICE,
            Car.SENSOR_SERVICE,
            Car.VENDOR_EXTENSION_SERVICE
    );

    private static final ArraySet<String> FLAGGED_MANDATORY_FEATURES = new ArraySet<>(1);

    static {
        if (Flags.persistApSettings()) {
            FLAGGED_MANDATORY_FEATURES.add(Car.CAR_WIFI_SERVICE);
        }

        // Note: if a new entry is added here, the capacity of FLAGGED_MANDATORY_FEATURES
        // should also be increased.
    }

    // Use ArraySet for better search performance. Memory consumption is fixed and it not an issue.
    // Should keep alphabetical order under each bucket.
    // Update CarFeatureTest as well when this is updated.
    private static final ArraySet<String> MANDATORY_FEATURES = combineFeatures(
            NON_FLAGGED_MANDATORY_FEATURES,
            FLAGGED_MANDATORY_FEATURES);

    private static final List<String> NON_FLAGGED_OPTIONAL_FEATURES = List.of(
            CarFeatures.FEATURE_CAR_USER_NOTICE_SERVICE,
            Car.CLUSTER_HOME_SERVICE,
            Car.CAR_NAVIGATION_SERVICE,
            Car.CAR_OCCUPANT_CONNECTION_SERVICE,
            Car.CAR_REMOTE_DEVICE_SERVICE,
            Car.DIAGNOSTIC_SERVICE,
            Car.EXPERIMENTAL_CAR_USER_SERVICE,
            Car.OCCUPANT_AWARENESS_SERVICE,
            Car.STORAGE_MONITORING_SERVICE,
            Car.VEHICLE_MAP_SERVICE,
            Car.CAR_TELEMETRY_SERVICE,
            Car.CAR_EVS_SERVICE,
            Car.CAR_REMOTE_ACCESS_SERVICE,
            Car.EXPERIMENTAL_CAR_KEYGUARD_SERVICE,
            // All items below here are deprecated, but still could be supported
            Car.CAR_INSTRUMENT_CLUSTER_SERVICE
    );

    private static final ArraySet<String> FLAGGED_OPTIONAL_FEATURES = new ArraySet<>();

    static {
        if (Flags.displayCompatibility()) {
            FLAGGED_OPTIONAL_FEATURES.add(Car.CAR_DISPLAY_COMPAT_SERVICE);
        }

        // Note: if a new entry is added here, the capacity of FLAGGED_OPTIONAL_FEATURES
        // should also be increased.
    }

    private static final ArraySet<String> OPTIONAL_FEATURES = combineFeatures(
            NON_FLAGGED_OPTIONAL_FEATURES, FLAGGED_OPTIONAL_FEATURES);

    // This is a feature still under development and cannot be enabled in user build.
    private static final ArraySet<String> NON_USER_ONLY_FEATURES = new ArraySet<>();

    // Features that depend on another feature being enabled (i.e. legacy API support).
    // For example, VMS_SUBSCRIBER_SERVICE will be enabled if VEHICLE_MAP_SERVICE is enabled
    // and disabled if VEHICLE_MAP_SERVICE is disabled.
    private static final List<Pair<String, String>> SUPPORT_FEATURES = List.of(
            Pair.create(Car.VEHICLE_MAP_SERVICE, Car.VMS_SUBSCRIBER_SERVICE)
    );

    private static final String FEATURE_CONFIG_FILE_NAME = "car_feature_config.txt";

    // Last line starts with this with number of features for extra confidence check.
    private static final String CONFIG_FILE_LAST_LINE_MARKER = ",,";

    // This hash is generated using the featured enabled via config.xml file of resources. Whenever
    // feature are updated in resource file, we should regenerate {@code FEATURE_CONFIG_FILE_NAME}.
    private static final String CONFIG_FILE_HASH_MARKER = "Hash:";

    // Set once in constructor and not updated. Access it without lock so that it can be accessed
    // quickly.
    private final ArraySet<String> mEnabledFeatures;

    private final Context mContext;

    private final List<String> mDefaultEnabledFeaturesFromConfig;
    private final List<String> mDisabledFeaturesFromVhal;

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final AtomicFile mFeatureConfigFile;

    @GuardedBy("mLock")
    private final List<String> mPendingEnabledFeatures = new ArrayList<>();

    @GuardedBy("mLock")
    private final List<String> mPendingDisabledFeatures = new ArrayList<>();

    @GuardedBy("mLock")
    private ArraySet<String> mAvailableExperimentalFeatures = new ArraySet<>();

    public CarFeatureController(@NonNull Context context,
            @NonNull String[] defaultEnabledFeaturesFromConfig,
            @NonNull String[] disabledFeaturesFromVhal, @NonNull File dataDir) {
        if (!BuildHelper.isUserBuild()) {
            OPTIONAL_FEATURES.addAll(NON_USER_ONLY_FEATURES);
        }
        mContext = context;
        Arrays.sort(defaultEnabledFeaturesFromConfig);
        mDefaultEnabledFeaturesFromConfig = Arrays.asList(defaultEnabledFeaturesFromConfig);
        mDisabledFeaturesFromVhal = Arrays.asList(disabledFeaturesFromVhal);
        Slogf.i(TAG, "mDefaultEnabledFeaturesFromConfig:" + mDefaultEnabledFeaturesFromConfig
                + ",mDisabledFeaturesFromVhal:" + mDisabledFeaturesFromVhal);
        mEnabledFeatures = new ArraySet<>(MANDATORY_FEATURES);
        mFeatureConfigFile = new AtomicFile(new File(dataDir, FEATURE_CONFIG_FILE_NAME));
        boolean shouldLoadDefaultConfig = !AtomicFileHelper.exists(mFeatureConfigFile);
        if (!shouldLoadDefaultConfig) {
            if (!loadFromConfigFileLocked()) {
                shouldLoadDefaultConfig = true;
            }
        }
        if (!checkMandatoryFeaturesLocked()) { // mandatory feature missing, force default config
            mEnabledFeatures.clear();
            mEnabledFeatures.addAll(MANDATORY_FEATURES);
            shouldLoadDefaultConfig = true;
        }
        // Separate if to use this as backup for failure in loadFromConfigFileLocked()
        if (shouldLoadDefaultConfig) {
            parseDefaultConfig();
            dispatchDefaultConfigUpdate();
        }
        addSupportFeatures(mEnabledFeatures);
    }

    @VisibleForTesting
    List<String> getDisabledFeaturesFromVhal() {
        return mDisabledFeaturesFromVhal;
    }

    @Override
    public void init() {
        // nothing should be done here. This should work with only constructor.
    }

    @Override
    public void release() {
        // nothing should be done here.
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarFeatureController*");
        writer.println(" mEnabledFeatures:" + mEnabledFeatures);
        writer.println(" mDefaultEnabledFeaturesFromConfig:" + mDefaultEnabledFeaturesFromConfig);
        writer.println(" mDisabledFeaturesFromVhal:" + mDisabledFeaturesFromVhal);
        synchronized (mLock) {
            writer.println(" mAvailableExperimentalFeatures:" + mAvailableExperimentalFeatures);
            writer.println(" mPendingEnabledFeatures:" + mPendingEnabledFeatures);
            writer.println(" mPendingDisabledFeatures:" + mPendingDisabledFeatures);
            dumpConfigFile(writer);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpConfigFile(IndentingPrintWriter writer) {
        writer.println(" mFeatureConfigFile:");
        FileInputStream fis;
        try {
            synchronized (mLock) {
                fis = mFeatureConfigFile.openRead();
            }
        } catch (FileNotFoundException e) {
            Slogf.i(TAG, "Feature config file not found");
            return;
        }
        writer.increaseIndent();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                writer.println(line);
            }
        } catch (IOException e) {
            Slogf.w(TAG, "Cannot read config file", e);
        } finally {
            try {
                fis.close();
            } catch (IOException e) {
                Slogf.e(TAG, "Couldn't close FileInputStream");
            }
        }
        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        for (int i = 0; i < mEnabledFeatures.size(); i++) {
            String enabledFeature = mEnabledFeatures.valueAt(i);
            proto.write(CarFeatureControlDumpProto.ENABLED_FEATURES, enabledFeature);
        }
        for (int i = 0; i < mDefaultEnabledFeaturesFromConfig.size(); i++) {
            String defaultEnabledFeature = mDefaultEnabledFeaturesFromConfig.get(i);
            proto.write(CarFeatureControlDumpProto.DEFAULT_ENABLED_FEATURES_FROM_CONFIG,
                    defaultEnabledFeature);
        }
        for (int i = 0; i < mDisabledFeaturesFromVhal.size(); i++) {
            String disabledFeature = mDisabledFeaturesFromVhal.get(i);
            proto.write(CarFeatureControlDumpProto.DISABLED_FEATURES_FROM_VHAL,
                    disabledFeature);
        }
        synchronized (mLock) {
            for (int i = 0; i < mAvailableExperimentalFeatures.size(); i++) {
                String experimentalFeature = mAvailableExperimentalFeatures.valueAt(i);
                proto.write(CarFeatureControlDumpProto.AVAILABLE_EXPERIMENTAL_FEATURES,
                        experimentalFeature);
            }
            for (int i = 0; i < mPendingEnabledFeatures.size(); i++) {
                String pendingEnabledFeature = mPendingEnabledFeatures.get(i);
                proto.write(CarFeatureControlDumpProto.PENDING_ENABLED_FEATURES,
                        pendingEnabledFeature);
            }
            for (int i = 0; i < mPendingDisabledFeatures.size(); i++) {
                String pendingDisabledFeature = mPendingDisabledFeatures.get(i);
                proto.write(CarFeatureControlDumpProto.PENDING_DISABLED_FEATURES,
                        pendingDisabledFeature);
            }
        }
    }

    /** Check {@link Car#isFeatureEnabled(String)} */
    public boolean isFeatureEnabled(String featureName) {
        return mEnabledFeatures.contains(featureName);
    }

    private boolean checkMandatoryFeaturesLocked() {
        // Ensure that mandatory features are always there
        for (int i = 0; i < MANDATORY_FEATURES.size(); i++) {
            String mandatoryFeature = MANDATORY_FEATURES.valueAt(i);
            if (!mEnabledFeatures.contains(mandatoryFeature)) {
                Slogf.e(TAG, "Mandatory feature missing in mEnabledFeatures:" + mandatoryFeature);
                return false;
            }
        }
        return true;
    }

    @FeaturerRequestEnum
    private int checkFeatureExisting(String featureName) {
        if (MANDATORY_FEATURES.contains(featureName)) {
            return Car.FEATURE_REQUEST_MANDATORY;
        }
        if (!OPTIONAL_FEATURES.contains(featureName)) {
            synchronized (mLock) {
                if (!mAvailableExperimentalFeatures.contains(featureName)) {
                    Slogf.e(TAG, "enableFeature requested for non-existing feature:"
                            + featureName);
                    return Car.FEATURE_REQUEST_NOT_EXISTING;
                }
            }
        }
        return Car.FEATURE_REQUEST_SUCCESS;
    }

    /** Check {@link Car#enableFeature(String)} */
    public int enableFeature(String featureName) {
        assertPermission();
        int checkResult = checkFeatureExisting(featureName);
        if (checkResult != Car.FEATURE_REQUEST_SUCCESS) {
            return checkResult;
        }

        boolean alreadyEnabled = mEnabledFeatures.contains(featureName);
        boolean shouldUpdateConfigFile = false;
        synchronized (mLock) {
            if (mPendingDisabledFeatures.remove(featureName)) {
                shouldUpdateConfigFile = true;
            }
            if (!mPendingEnabledFeatures.contains(featureName) && !alreadyEnabled) {
                shouldUpdateConfigFile = true;
                mPendingEnabledFeatures.add(featureName);
            }
        }
        if (shouldUpdateConfigFile) {
            Slogf.w(TAG, "Enabling feature in config file:" + featureName);
            dispatchDefaultConfigUpdate();
        }
        if (alreadyEnabled) {
            return Car.FEATURE_REQUEST_ALREADY_IN_THE_STATE;
        } else {
            return Car.FEATURE_REQUEST_SUCCESS;
        }
    }

    /** Check {@link Car#disableFeature(String)} */
    public int disableFeature(String featureName) {
        assertPermission();
        int checkResult = checkFeatureExisting(featureName);
        if (checkResult != Car.FEATURE_REQUEST_SUCCESS) {
            return checkResult;
        }

        boolean alreadyDisabled = !mEnabledFeatures.contains(featureName);
        boolean shouldUpdateConfigFile = false;
        synchronized (mLock) {
            if (mPendingEnabledFeatures.remove(featureName)) {
                shouldUpdateConfigFile = true;
            }
            if (!mPendingDisabledFeatures.contains(featureName) && !alreadyDisabled) {
                shouldUpdateConfigFile = true;
                mPendingDisabledFeatures.add(featureName);
            }
        }
        if (shouldUpdateConfigFile) {
            Slogf.w(TAG, "Disabling feature in config file:" + featureName);
            dispatchDefaultConfigUpdate();
        }
        if (alreadyDisabled) {
            return Car.FEATURE_REQUEST_ALREADY_IN_THE_STATE;
        } else {
            return Car.FEATURE_REQUEST_SUCCESS;
        }
    }

    /**
     * Set available experimental features. Only features set through this call will be allowed to
     * be enabled for experimental features. Setting this is not allowed for USER build.
     *
     * @return True if set is allowed and set. False if experimental feature is not allowed.
     */
    public boolean setAvailableExperimentalFeatureList(List<String> experimentalFeatures) {
        assertPermission();
        if (BuildHelper.isUserBuild()) {
            Slogf.e(TAG, "Experimental feature list set for USER build", new RuntimeException());
            return false;
        }
        synchronized (mLock) {
            mAvailableExperimentalFeatures.clear();
            mAvailableExperimentalFeatures.addAll(experimentalFeatures);
        }
        return true;
    }

    /** Check {@link Car#getAllEnabledFeatures()} */
    public List<String> getAllEnabledFeatures() {
        assertPermission();
        return new ArrayList<>(mEnabledFeatures);
    }

    /** Check {@link Car#getAllPendingDisabledFeatures()} */
    public List<String> getAllPendingDisabledFeatures() {
        assertPermission();
        synchronized (mLock) {
            return new ArrayList<>(mPendingDisabledFeatures);
        }
    }

    /** Check {@link Car#getAllPendingEnabledFeatures()} */
    public List<String> getAllPendingEnabledFeatures() {
        assertPermission();
        synchronized (mLock) {
            return new ArrayList<>(mPendingEnabledFeatures);
        }
    }

    /** Returns currently enabled experimental features */
    public @NonNull List<String> getEnabledExperimentalFeatures() {
        if (BuildHelper.isUserBuild()) {
            Slogf.e(TAG, "getEnabledExperimentalFeatures called in USER build",
                    new RuntimeException());
            return Collections.emptyList();
        }
        ArrayList<String> experimentalFeature = new ArrayList<>();
        for (int i = 0; i < mEnabledFeatures.size(); i++) {
            String enabledFeature = mEnabledFeatures.valueAt(i);
            if (MANDATORY_FEATURES.contains(enabledFeature)) {
                continue;
            }
            if (OPTIONAL_FEATURES.contains(enabledFeature)) {
                continue;
            }
            experimentalFeature.add(enabledFeature);
        }
        return experimentalFeature;
    }

    void handleCorruptConfigFileLocked(String msg, String line) {
        Slogf.e(TAG, msg + ", considered as corrupt, line:" + line);
        mEnabledFeatures.clear();
    }

    // TODO(b/227645920): add unit test
    @GuardedBy("mLock")
    private boolean loadFromConfigFileLocked() {
        // done without lock, should be only called from constructor.
        FileInputStream fis;
        try {
            fis = mFeatureConfigFile.openRead();
        } catch (FileNotFoundException e) {
            Slogf.i(TAG, "Feature config file not found, this could be 1st boot");
            return false;
        }
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            boolean lastLinePassed = false;
            boolean hashChecked = false;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    if (!lastLinePassed) {
                        handleCorruptConfigFileLocked("No last line checksum", "");
                        return false;
                    }
                    break;
                }
                if (lastLinePassed && !line.isEmpty()) {
                    handleCorruptConfigFileLocked(
                            "Config file has additional line after last line marker", line);
                    return false;
                } else {
                    if (line.startsWith(CONFIG_FILE_HASH_MARKER)) {
                        int expectedHashValue = mDefaultEnabledFeaturesFromConfig.hashCode();
                        int fileHashValue = Integer
                                .parseInt(line.substring(CONFIG_FILE_HASH_MARKER.length()).strip());
                        if (expectedHashValue != fileHashValue) {
                            handleCorruptConfigFileLocked("Config hash doesn't match. Expected: "
                                    + expectedHashValue, line);
                            return false;
                        }
                        hashChecked = true;
                        continue;
                    }

                    if (!hashChecked) {
                        handleCorruptConfigFileLocked("Config file doesn't have hash value.", "");
                        return false;
                    }

                    if (line.startsWith(CONFIG_FILE_LAST_LINE_MARKER)) {
                        int numberOfFeatures;
                        try {
                            numberOfFeatures = Integer.parseInt(
                                    line.substring(CONFIG_FILE_LAST_LINE_MARKER.length()));
                        } catch (NumberFormatException e) {
                            handleCorruptConfigFileLocked(
                                    "Config file has corrupt last line, not a number", line);
                            return false;
                        }
                        int actualNumberOfFeatures = mEnabledFeatures.size();
                        if (numberOfFeatures != actualNumberOfFeatures) {
                            handleCorruptConfigFileLocked(
                                    "Config file has wrong number of features, expected:"
                                            + numberOfFeatures + " actual:"
                                            + actualNumberOfFeatures, line);
                            return false;
                        }
                        lastLinePassed = true;
                    } else {
                        mEnabledFeatures.add(line);
                    }
                }
            }
        } catch (IOException e) {
            Slogf.w(TAG, "Cannot load config file", e);
            return false;
        }
        Slogf.i(TAG, "Loaded features:" + mEnabledFeatures);
        return true;
    }

    private void persistToFeatureConfigFile(ArraySet<String> features) {
        removeSupportFeatures(features);
        synchronized (mLock) {
            features.removeAll(mPendingDisabledFeatures);
            features.addAll(mPendingEnabledFeatures);
            FileOutputStream fos;
            try {
                fos = mFeatureConfigFile.startWrite();
            } catch (IOException e) {
                Slogf.e(TAG, "Cannot create config file", e);
                return;
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos,
                    StandardCharsets.UTF_8))) {
                Slogf.i(TAG, "Updating features:" + features);
                writer.write(CONFIG_FILE_HASH_MARKER
                        + mDefaultEnabledFeaturesFromConfig.hashCode());
                writer.newLine();
                for (int i = 0; i < features.size(); i++) {
                    String feature = features.valueAt(i);
                    writer.write(feature);
                    writer.newLine();
                }
                writer.write(CONFIG_FILE_LAST_LINE_MARKER + features.size());
                writer.flush();
                mFeatureConfigFile.finishWrite(fos);
            } catch (IOException e) {
                mFeatureConfigFile.failWrite(fos);
                Slogf.e(TAG, "Cannot create config file", e);
            }
        }
    }

    private void assertPermission() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_FEATURES);
    }

    private void dispatchDefaultConfigUpdate() {
        mHandler.removeCallbacksAndMessages(null);
        ArraySet<String> featuresToPersist = new ArraySet<>(mEnabledFeatures);
        mHandler.post(() -> persistToFeatureConfigFile(featuresToPersist));
    }

    private void parseDefaultConfig() {
        for (int i = 0; i < mDefaultEnabledFeaturesFromConfig.size(); i++) {
            String defaultEnabledFeature = mDefaultEnabledFeaturesFromConfig.get(i);
            if (mDisabledFeaturesFromVhal.contains(defaultEnabledFeature)) {
                continue;
            }
            if (OPTIONAL_FEATURES.contains(defaultEnabledFeature)) {
                mEnabledFeatures.add(defaultEnabledFeature);
            } else if (NON_USER_ONLY_FEATURES.contains(defaultEnabledFeature)) {
                Slogf.e(TAG, "config_default_enabled_optional_car_features including "
                        + "user build only feature, will be ignored:" + defaultEnabledFeature);
            } else {
                throw new IllegalArgumentException(
                        "config_default_enabled_optional_car_features include non-optional "
                                + "features:" + defaultEnabledFeature);
            }
        }
        Slogf.i(TAG, "Loaded default features:" + mEnabledFeatures);
    }

    private static void addSupportFeatures(Collection<String> features) {
        SUPPORT_FEATURES.stream()
                .filter(entry -> features.contains(entry.first))
                .forEach(entry -> features.add(entry.second));
    }

    private static void removeSupportFeatures(Collection<String> features) {
        SUPPORT_FEATURES.stream()
                .filter(entry -> features.contains(entry.first))
                .forEach(entry -> features.remove(entry.second));
    }

    private static ArraySet<String> combineFeatures(List<String> features,
            ArraySet<String> flaggedFeatures) {
        ArraySet<String> combinedFeatures = new ArraySet<>(features);
        combinedFeatures.addAll(flaggedFeatures);
        return combinedFeatures;
    }
}
