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

package com.android.compatibility.common.deviceinfo;

import static android.Manifest.permission.TEST_BIOMETRIC;

import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.SensorProperties;
import android.hardware.biometrics.SensorProperties.ComponentInfo;
import android.os.Build;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.compatibility.common.util.SystemUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Biometrics info collector.
 */
@TargetApi(Build.VERSION_CODES.S)
public class BiometricsDeviceInfo extends DeviceInfo {
    private static final String TAG = "BiometricsDeviceInfo";

    private static final String DUMPSYS_BIOMETRIC = "dumpsys biometric";

    // BiometricAuthenticator.Modality.TYPE_FINGERPRINT
    private static final int AUTHENTICATOR_TYPE_FINGERPRINT = 1 << 1;
    // BiometricAuthenticator.Modality.TYPE_IRIS
    private static final int AUTHENTICATOR_TYPE_IRIS = 1 << 2;
    // BiometricAuthenticator.Modality.TYPE_FACE
    private static final int AUTHENTICATOR_TYPE_FACE = 1 << 3;

    // BiometricManager.Authenticators.BIOMETRIC_CONVENIENCE
    private static final int AUTHENTICATOR_BIOMETRIC_CONVENIENCE = 0x0FFF;
    // BiometricManager.Authenticators.BIOMETRIC_WEAK
    private static final int AUTHENTICATOR_BIOMETRIC_WEAK = 0x00FF;
    // BiometricManager.Authenticators.BIOMETRIC_STRONG
    private static final int AUTHENTICATOR_BIOMETRIC_STRONG = 0x000F;

    // SensorProperties.STRENGTH_CONVENIENCE
    private static final int SENSOR_PROPERTIES_STRENGTH_CONVENIENCE = 0;
    // SensorProperties.STRENGTH_WEAK
    private static final int SENSOR_PROPERTIES_STRENGTH_WEAK = 1;
    // SensorProperties.STRENGTH_STRONG
    private static final int SENSOR_PROPERTIES_STRENGTH_STRONG = 2;

    private static final String BIOMETRIC_PROPERTIES = "biometric_properties";
    private static final String SENSOR_ID = "sensor_id";

    private static final String SENSOR_MODALITY = "modality";
    private static final int MODALITY_UNKNOWN = 0;
    private static final int MODALITY_FINGERPRINT = 1;   // 1 << 0
    private static final int MODALITY_IRIS = 2;          // 1 << 1
    private static final int MODALITY_FACE = 4;          // 1 << 2

    private static final String STRENGTH = "strength";
    private static final String CURRENT_STRENGTH = "current_strength";
    private static final int STRENGTH_UNKNOWN = 0;
    private static final int STRENGTH_CONVENIENCE = 1;
    private static final int STRENGTH_WEAK = 2;
    private static final int STRENGTH_STRONG = 3;

    private static final String COMPONENT_INFO = "component_info";
    private static final String COMPONENT_INFO_COMPONENT_ID = "component_id";
    private static final String COMPONENT_INFO_HARDWARE_VERSION = "hardware_version";
    private static final String COMPONENT_INFO_FIRMWARE_VERSION = "firmware_version";
    private static final String COMPONENT_INFO_SERIAL_NUMBER = "serial_number";
    private static final String COMPONENT_INFO_SOFTWARE_VERSION = "software_version";

    /**
     * Information of a single sensor.
     */
    private static class SensorInfo {
        private final int mModality;
        private final int mCurStrength;

        private SensorInfo(int modality, int curStrength) {
            mModality = modality;
            mCurStrength = curStrength;
        }
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            Log.d(TAG, "Skipping test for devices not launching with at least Android S");
            return;
        }

        final PackageManager pm = getContext().getPackageManager();
        if (!(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                || pm.hasSystemFeature(PackageManager.FEATURE_FACE)
                || pm.hasSystemFeature(PackageManager.FEATURE_IRIS))) {
            Log.d(TAG, "Skipping test for devices without biometric features");
            return;
        }

        final BiometricManager bm = getContext().getSystemService(BiometricManager.class);
        final List<SensorProperties> sensorProperties =
                SystemUtil.callWithShellPermissionIdentity(
                        () -> bm != null ? bm.getSensorProperties() : null, TEST_BIOMETRIC
                );
        if (sensorProperties == null) {
            Log.d(TAG, "Cannot get sensor properties");
            return;
        }
        final Map<Integer, SensorInfo> sensors = getSensorInfo();

        store.startArray(BIOMETRIC_PROPERTIES);
        for (SensorProperties props : sensorProperties) {
            final SensorInfo sensorInfo = sensors.getOrDefault(props.getSensorId(), null);
            store.startGroup();
            store.addResult(SENSOR_ID, props.getSensorId());
            store.addResult(SENSOR_MODALITY,
                    sensorInfo != null ? sensorInfo.mModality : MODALITY_UNKNOWN);
            store.addResult(STRENGTH, propertyStrengthToSensorStrength(props.getSensorStrength()));
            store.addResult(CURRENT_STRENGTH,
                    sensorInfo != null ? sensorInfo.mCurStrength : STRENGTH_UNKNOWN);
            store.startArray(COMPONENT_INFO);
            for (ComponentInfo info : props.getComponentInfo()) {
                store.startGroup();
                store.addResult(COMPONENT_INFO_COMPONENT_ID, info.getComponentId());
                store.addResult(COMPONENT_INFO_HARDWARE_VERSION, info.getHardwareVersion());
                store.addResult(COMPONENT_INFO_FIRMWARE_VERSION, info.getFirmwareVersion());
                store.addResult(COMPONENT_INFO_SERIAL_NUMBER, info.getSerialNumber());
                store.addResult(COMPONENT_INFO_SOFTWARE_VERSION, info.getSoftwareVersion());
                store.endGroup();
            }
            store.endArray(); // "component_info"
            store.endGroup();
        }
        store.endArray(); // "biometric_properties"
    }

    /**
     * A helper function to get information of each sensor.
     * @return Mapping of sensor ID to the corresponding sensor information
     */
    private Map<Integer, SensorInfo> getSensorInfo() {
        final Map<Integer, SensorInfo> sensors = new HashMap<>();

        final String output = SystemUtil.runShellCommand(DUMPSYS_BIOMETRIC);
        if (output == null || output.isEmpty()) {
            Log.d(TAG, "dumpsys biometric does not generate anything");
            return sensors;
        }

        Pattern pattern = Pattern.compile("ID\\((?<ID>\\d+)\\).*"
                + "oemStrength:\\s*(?<strength>\\d+).*"
                + "updatedStrength:\\s*(?<curStrength>\\d+).*"
                + "modality\\s*(?<modality>\\d+)");
        Matcher matcher = pattern.matcher(output);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            final int sensorId = Integer.parseInt(matcher.group("ID"));
            final int modality = toModality(Integer.parseInt(matcher.group("modality")));
            final int strength = authenticatorStrengthToSensorStrength(Integer.parseInt(
                    matcher.group("strength")));
            final int curStrength = authenticatorStrengthToSensorStrength(Integer.parseInt(
                    matcher.group("curStrength")));
            assertTrue("The current strength cannot be stronger than the original strength",
                    curStrength <= strength);
            sensors.put(sensorId, new SensorInfo(modality, curStrength));
        }
        if (!matched) {
            Log.d(TAG, "No matched sensor info: dumpsys output=" + output);
        }
        return sensors;
    }

    /**
     * Convert a modality (BiometricAuthenticator.Modality) to the corresponding enum to be stored.
     *
     * @param modality See BiometricAuthenticator.Modality
     * @return The enum to be stored
     */
    private int toModality(int modality) {
        switch (modality) {
            case AUTHENTICATOR_TYPE_FINGERPRINT:
                return MODALITY_FINGERPRINT;
            case AUTHENTICATOR_TYPE_IRIS:
                return MODALITY_IRIS;
            case AUTHENTICATOR_TYPE_FACE:
                return MODALITY_FACE;
            default:
                return MODALITY_UNKNOWN;
        }
    }

    /**
     * Convert an authenticator strength (BiometricManager.Authenticators.Types) to the
     * corresponding enum to be stored.
     *
     * @param strength See BiometricManager.Authenticators.Types
     * @return The enum to be stored
     */
    private int authenticatorStrengthToSensorStrength(int strength) {
        switch (strength) {
            case AUTHENTICATOR_BIOMETRIC_CONVENIENCE:
                return STRENGTH_CONVENIENCE;
            case AUTHENTICATOR_BIOMETRIC_WEAK:
                return STRENGTH_WEAK;
            case AUTHENTICATOR_BIOMETRIC_STRONG:
                return STRENGTH_STRONG;
            default:
                return STRENGTH_UNKNOWN;
        }
    }

    /**
     * Convert a sensor property strength (SensorProperties.Strength) to the corresponding enum to
     * be stored.
     *
     * @param strength See SensorProperties.Strength
     * @return The enum to be stored
     */
    private int propertyStrengthToSensorStrength(int strength) {
        switch (strength) {
            case SENSOR_PROPERTIES_STRENGTH_CONVENIENCE:
                return STRENGTH_CONVENIENCE;
            case SENSOR_PROPERTIES_STRENGTH_WEAK:
                return STRENGTH_WEAK;
            case SENSOR_PROPERTIES_STRENGTH_STRONG:
                return STRENGTH_STRONG;
            default:
                return STRENGTH_UNKNOWN;
        }
    }
}
