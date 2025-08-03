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
 * limitations under the License.
 */

package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import android.car.Car;
import android.car.CarFeatures;
import android.car.feature.Flags;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.List;

@SmallTest
public final class CarFeatureTest extends CarApiTestBase {

    private static final String TAG = CarFeatureTest.class.getSimpleName();

    private static final String BLUETOOTH_SERVICE = "car_bluetooth";

    // List in CarFeatureController should be inline with this.
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

    private static final String NON_EXISTING_FEATURE = "ThisFeatureDoesNotExist";

    @Test
    public void checkMandatoryFeatures() {
        Car car = getCar();
        assertThat(car).isNotNull();
        for (int i = 0; i < MANDATORY_FEATURES.size(); i++) {
            String mandatoryFeature = MANDATORY_FEATURES.valueAt(i);
            assertThat(car.isFeatureEnabled(mandatoryFeature)).isTrue();
        }
    }

    @Test
    public void toggleOptionalFeature() {
        Car car = getCar();
        assertThat(car).isNotNull();
        for (int i = 0; i < OPTIONAL_FEATURES.size(); i++) {
            String optionalFeature = OPTIONAL_FEATURES.valueAt(i);
            boolean enabled = getCar().isFeatureEnabled(optionalFeature);
            toggleOptionalFeature(optionalFeature, !enabled, enabled);
            toggleOptionalFeature(optionalFeature, enabled, enabled);
        }
    }

    @Test
    public void testGetAllEnabledFeatures() {
        Car car = getCar();
        assertThat(car).isNotNull();
        List<String> allEnabledFeatures = car.getAllEnabledFeatures();
        assertThat(allEnabledFeatures).isNotEmpty();
        for (int i = 0; i < MANDATORY_FEATURES.size(); i++) {
            String mandatoryFeature = MANDATORY_FEATURES.valueAt(i);
            assertThat(allEnabledFeatures).contains(mandatoryFeature);
        }
    }

    @Test
    public void testEnableDisableForMandatoryFeatures() {
        for (int i = 0; i < MANDATORY_FEATURES.size(); i++) {
            String mandatoryFeature = MANDATORY_FEATURES.valueAt(i);
            assertThat(getCar().enableFeature(mandatoryFeature)).isEqualTo(
                    Car.FEATURE_REQUEST_MANDATORY);
            assertThat(getCar().disableFeature(mandatoryFeature)).isEqualTo(
                    Car.FEATURE_REQUEST_MANDATORY);
        }
    }

    @Test
    public void testEnableDisableForNonExistingFeature() {
        assertThat(getCar().enableFeature(NON_EXISTING_FEATURE)).isEqualTo(
                Car.FEATURE_REQUEST_NOT_EXISTING);
        assertThat(getCar().disableFeature(NON_EXISTING_FEATURE)).isEqualTo(
                Car.FEATURE_REQUEST_NOT_EXISTING);
    }

    private void toggleOptionalFeature(String feature, boolean enable, boolean originallyEnabled) {
        if (enable) {
            if (originallyEnabled) {
                assertThat(getCar().enableFeature(feature)).isEqualTo(
                        Car.FEATURE_REQUEST_ALREADY_IN_THE_STATE);
            } else {
                assertThat(getCar().enableFeature(feature)).isEqualTo(Car.FEATURE_REQUEST_SUCCESS);
                assertThat(getCar().getAllPendingEnabledFeatures()).contains(feature);
            }
            assertThat(getCar().getAllPendingDisabledFeatures()).doesNotContain(feature);
        } else {
            if (originallyEnabled) {
                assertThat(getCar().disableFeature(feature)).isEqualTo(Car.FEATURE_REQUEST_SUCCESS);
                assertThat(getCar().getAllPendingDisabledFeatures()).contains(feature);
            } else {
                assertThat(getCar().disableFeature(feature)).isEqualTo(
                        Car.FEATURE_REQUEST_ALREADY_IN_THE_STATE);
            }
            assertThat(getCar().getAllPendingEnabledFeatures()).doesNotContain(feature);
        }
    }

    private static ArraySet<String> combineFeatures(List<String> features,
            ArraySet<String> flaggedFeatures) {
        ArraySet<String> combinedFeatures = new ArraySet<>(features);
        combinedFeatures.addAll(flaggedFeatures);
        return combinedFeatures;
    }
}
