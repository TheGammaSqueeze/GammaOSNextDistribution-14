/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service;

import static com.android.adservices.common.DeviceConfigUtil.setAdservicesFlag;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_COBALT_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.NAMESPACE_ADSERVICES;
import static com.android.adservices.service.FlagsTest.getConstantValue;

import android.os.SystemProperties;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.ExtendedMockitoExpectations;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Test;

/* TODO(b/326254556): test fail if properties are already set. Example:
 *
 * adb shell setprop debug.adservices.topics_epoch_job_flex_ms 42
 * adb shell setprop debug.adservices.test.measurement_kill_switch true
 */

@SpyStatic(SystemProperties.class)
public class PhFlagsSystemPropertyOverrideTest extends AdServicesExtendedMockitoTestCase {

    private final Flags mPhFlags = PhFlags.getInstance();

    private final FlagGuard mGlobalKillSwitchGuard = value -> setGlobalKillSwitch(!value);
    private final FlagGuard mMsmtKillSwitchesGuard = value -> setMsmmtKillSwitch(!value);

    // Overriding DeviceConfig stub to avoid Read device config permission errors and to also
    // test the behavior of flags, when both device config and system properties are set.
    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new)
                .build();
    }

    // TODO(b/326254556): add 2 tests (T and pre-T) for getGlobalKillSwitch() itself

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetTopicsEpochJobFlexMs() {
        testUnguardedFlag(
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                TOPICS_EPOCH_JOB_FLEX_MS,
                flags -> flags.getTopicsEpochJobFlexMs());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetAdIdKillSwitch() {
        testUnguardedLegacyKillSwitch(
                KEY_ADID_KILL_SWITCH, "ADID_KILL_SWITCH", flags -> flags.getAdIdKillSwitch());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetLegacyMeasurementKillSwitch() {
        testLegacyKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                flags -> flags.getLegacyMeasurementKillSwitch());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetMeasurementEnabled() {
        testFeatureFlagBackedByLegacyKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                flags -> flags.getMeasurementEnabled());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetMeasurementAttributionFallbackJobEnabled() {
        testFeatureFlagBackedByLegacyKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                mMsmtKillSwitchesGuard,
                flags -> flags.getMeasurementAttributionFallbackJobEnabled());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetCobaltLoggingEnabled() {
        testFeatureFlag(
                KEY_COBALT_LOGGING_ENABLED,
                "COBALT_LOGGING_ENABLED",
                flags -> flags.getCobaltLoggingEnabled());
    }

    /**
     * Tests the behavior of a flag that is not guarded by any other flag.
     *
     * @param name name of the flag
     * @param defaultValue Java constant (like {@code TOPICS_EPOCH_JOB_FLEX_MS"} defining the
     *     default value of the flag
     * @param flaginator helper object used to get the value of the flag
     */
    private void testUnguardedFlag(String name, long defaultValue, Flaginator<Long> flaginator) {
        // Without any overriding, the value is the hard coded constant.
        expect.withMessage("getter for %s by default", name)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        // Now overriding with the value in both system properties and device config.
        long systemPropertyValue = defaultValue + 1;
        long deviceConfigValue = defaultValue + 2;
        mockGetSystemProperty(name, systemPropertyValue);

        setAdservicesFlag(name, deviceConfigValue);

        expect.withMessage("getter for %s prefers system property value", name)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(systemPropertyValue);
    }

    /**
     * Tests the behavior of a boolean flag that is guarded by the global kill switch.
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "COBALT_LOGGING_ENABLED"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testFeatureFlag(
            String flagName, String defaultValueConstant, Flaginator<Boolean> flaginator) {
        testFeatureFlag(flagName, defaultValueConstant, mGlobalKillSwitchGuard, flaginator);
    }

    // TODO(b/326254556): remove if not used (other than by testFeatureFlag() above, which passes
    // mGlobalKillSwitchGuard
    /**
     * Tests the behavior of a feature flag that is guarded by a "generic" guard (typically the
     * global kill switch and a per-API kill switch).
     *
     * @param flagName name of the legacy kill switch flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "MDD_LOGGER_KILL_SWITCH"} defining the default value of the flag
     * @param guard helper object used enable / disable the guarding flags
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testFeatureFlag(
            String flagName,
            String defaultValueConstant,
            FlagGuard guard,
            Flaginator<Boolean> flaginator) {
        internalTestForFeatureFlag(
                flagName, defaultValueConstant, FeatureFlagType.FEATURE_FLAG, guard, flaginator);
    }

    /**
     * Tests the behavior of a feature flag that is guarded by the global kill switch but whose
     * {@code DeviceConfig} flag is a legacy kill switch flag (i.e., when the kill switch is
     * enabled, the feature flag is disabled and viceversa)
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "MEASUREMENT_KILL_SWITCH"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testFeatureFlagBackedByLegacyKillSwitch(
            String flagName, String defaultValueConstant, Flaginator<Boolean> flaginator) {
        testFeatureFlagBackedByLegacyKillSwitch(
                flagName, defaultValueConstant, mGlobalKillSwitchGuard, flaginator);
    }

    /**
     * Tests the behavior of a feature flag that is guarded by a "generic" guard (typically the
     * global kill switch and a per-API kill switch) but whose {@code DeviceConfig} flag is a legacy
     * kill switch flag (i.e., when the kill switch is enabled, the feature flag is disabled and
     * viceversa)
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "MEASUREMENT_KILL_SWITCH"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testFeatureFlagBackedByLegacyKillSwitch(
            String flagName,
            String defaultValueConstant,
            FlagGuard guard,
            Flaginator<Boolean> flaginator) {
        internalTestForFeatureFlag(
                flagName,
                defaultValueConstant,
                FeatureFlagType.FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH,
                guard,
                flaginator);
    }

    /**
     * Tests the behavior of a feature flag that is not guarded by any other flag but whose {@code
     * DeviceConfig} flag is a legacy kill switch flag (i.e., when the kill switch is enabled, the
     * feature flag is disabled and viceversa)
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "ADID_KILL_SWITCH"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testUnguardedLegacyKillSwitch(
            String flagName, String defaultValueConstant, Flaginator<Boolean> flaginator) {
        internalTestForFeatureFlag(
                flagName,
                defaultValueConstant,
                FeatureFlagType.LEGACY_KILL_SWITCH,
                /* guard= */ null,
                flaginator);
    }

    /**
     * Tests the behavior of a boolean flag that is guarded by the global kill switch.
     *
     * @param flagName name of the flag
     * @param defaultValue default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testLegacyKillSwitch(
            String flagName, String defaultValueConstant, Flaginator<Boolean> flaginator) {
        internalTestForFeatureFlag(
                flagName,
                defaultValueConstant,
                FeatureFlagType.LEGACY_KILL_SWITCH,
                mGlobalKillSwitchGuard,
                flaginator);
    }

    // Should not be called by tests directly
    private void internalTestForFeatureFlag(
            String flagName,
            String defaultValueConstant,
            FeatureFlagType type,
            @Nullable FlagGuard guard,
            Flaginator<Boolean> flaginator) {

        // This is the value hardcoded by a constant on Flags.java
        boolean constantValue = getConstantValue(defaultValueConstant);
        // This is the default value for the flag's getter - ideally it should be the same as the
        // constant value, but it's the opposite when it's backed by a "legacy" kill switch.
        boolean defaultValue =
                type.equals(FeatureFlagType.FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH)
                        ? !constantValue
                        : constantValue;

        // This is the value of the getter when it's "disabled"
        boolean disabledValue = type.equals(FeatureFlagType.LEGACY_KILL_SWITCH);

        Log.d(
                mTag,
                "internalTestFlag("
                        + defaultValueConstant
                        + ") part 1: flagName="
                        + flagName
                        + ", constantValue="
                        + constantValue
                        + ", type="
                        + type
                        + ", defaultValue="
                        + defaultValue
                        + ", disabledValue="
                        + disabledValue);

        if (guard != null) {
            // First check the behavior when the guarding flags are in place(like kill switches on)
            guard.setEnabled(false);
            expect.withMessage(
                            "getter of %s by default when guarding kill switches are on",
                            defaultValueConstant)
                    .that(flaginator.getFlagValue(mPhFlags))
                    .isEqualTo(disabledValue);

            // Make sure neither DeviceConfig or SystemProperty was called
            verifyGetBooleanSystemPropertyNotCalled(flagName);
            verifyGetBooleanDeviceConfigFlagNotCalled(flagName);

            // Then enable the guarding flags
            guard.setEnabled(true);
        }

        // Without any overriding, the value is the hard coded constant.
        expect.withMessage(
                        "getter of %s by default when guarding kill switches are off",
                        defaultValueConstant)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        // Now overriding the device config flag and system properties, so the expected value
        // is driven by the system properties one (and the feature flag type)
        boolean systemPropertyValue = !constantValue;
        boolean deviceConfigValue = !systemPropertyValue;
        boolean expectedFlagValue =
                type.equals(FeatureFlagType.FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH)
                        ? !systemPropertyValue
                        : systemPropertyValue;
        Log.d(
                mTag,
                "internalTestFlag("
                        + defaultValueConstant
                        + ") part 2: systemPropertyValue="
                        + systemPropertyValue
                        + ", deviceConfigValue="
                        + deviceConfigValue
                        + ", expectedFlagValue="
                        + expectedFlagValue);

        mockGetSystemProperty(flagName, systemPropertyValue);
        setAdservicesFlag(flagName, deviceConfigValue);

        expect.withMessage(
                        "getter of %s when overridden by system property value",
                        defaultValueConstant)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(expectedFlagValue);
    }

    private void setGlobalKillSwitch(boolean value) {
        ExtendedMockitoExpectations.mockGetAdServicesFlag(KEY_GLOBAL_KILL_SWITCH, value);
    }

    private void setMsmmtKillSwitch(boolean value) {
        setGlobalKillSwitch(value);
        ExtendedMockitoExpectations.mockGetAdServicesFlag(KEY_MEASUREMENT_KILL_SWITCH, value);
    }

    private void mockGetSystemProperty(String name, long value) {
        ExtendedMockitoExpectations.mockGetSystemProperty(
                PhFlags.getSystemPropertyName(name), value);
    }

    private void mockGetSystemProperty(String name, boolean value) {
        Log.v(mTag, "Setting system property: " + name + "=" + value);
        ExtendedMockitoExpectations.mockGetSystemProperty(
                PhFlags.getSystemPropertyName(name), value);
    }

    private void verifyGetBooleanSystemPropertyNotCalled(String name) {
        ExtendedMockitoExpectations.verifyGetBooleanSystemPropertyNotCalled(
                PhFlags.getSystemPropertyName(name));
    }

    private void verifyGetBooleanDeviceConfigFlagNotCalled(String name) {
        ExtendedMockitoExpectations.verifyGetBooleanDeviceConfigFlagNotCalled(
                NAMESPACE_ADSERVICES, name);
    }

    /** Interface used to abstract the feature flags / kill switches guarding a flag. */
    private interface FlagGuard {
        void setEnabled(boolean value);
    }

    // TODO(b/325135083): ideally it should fetch the FeatureFlag.Type from the constant annotation
    private enum FeatureFlagType {
        FEATURE_FLAG,
        FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH,
        LEGACY_KILL_SWITCH
    }
}
