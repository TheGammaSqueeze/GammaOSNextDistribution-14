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

package com.android.adservices.service.fixture;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemProperties;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Rule;
import org.junit.Test;

/** Unit test for {@link TestableSystemProperties} */
public final class TestableSystemPropertiesTest {
    @Rule
    public AdServicesExtendedMockitoRule mAdServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule(TestableSystemProperties::new);

    @Test
    public void testForceToReturnDefaultValue_intValue() {
        String systemPropertyKey = "debug.adservices.testKeyInt";
        int shellCommandConfiguredValue = 1;
        int defaultValue = 2;
        int apiConfiguredValue = 3;
        setSystemPropertyByRunningAdbCommand(systemPropertyKey, shellCommandConfiguredValue);

        // Verify system property from adb command returns the configured value.
        assertThat(getSystemPropertyByRunningAdbCommand(systemPropertyKey))
                .isEqualTo(String.valueOf(shellCommandConfiguredValue));

        // Verify the SystemProperties getter method is stubbed to return overridden value.
        assertThat(SystemProperties.getInt(systemPropertyKey, defaultValue))
                .isEqualTo(defaultValue);

        // Now set the SystemProperties value using method and verify that correct value is
        // returned.
        TestableSystemProperties.set(systemPropertyKey, String.valueOf(apiConfiguredValue));
        assertThat(SystemProperties.getInt(systemPropertyKey, defaultValue))
                .isEqualTo(apiConfiguredValue);
    }

    @Test
    public void testForceToReturnDefaultValue_longValue() {
        String systemPropertyKey = "debug.adservices.testKeyLong";
        long shellCommandConfiguredValue = 1L;
        long defaultValue = 2L;
        long apiConfiguredValue = 3L;
        setSystemPropertyByRunningAdbCommand(systemPropertyKey, shellCommandConfiguredValue);

        // Verify system property from adb command returns the configured value.
        assertThat(getSystemPropertyByRunningAdbCommand(systemPropertyKey))
                .isEqualTo(String.valueOf(shellCommandConfiguredValue));

        // Verify the SystemProperties getter method is stubbed to return overridden value.
        assertThat(SystemProperties.getLong(systemPropertyKey, defaultValue))
                .isEqualTo(defaultValue);

        // Now set the SystemProperties value using method and verify that correct value is
        // returned.
        TestableSystemProperties.set(systemPropertyKey, String.valueOf(apiConfiguredValue));
        assertThat(SystemProperties.getLong(systemPropertyKey, defaultValue))
                .isEqualTo(apiConfiguredValue);
    }

    @Test
    public void testForceToReturnDefaultValue_booleanValue() {
        String systemPropertyKey = "debug.adservices.testKeyBoolean";
        boolean shellCommandConfiguredValue = true;
        boolean defaultValue = false;
        boolean apiConfiguredValue = true;
        setSystemPropertyByRunningAdbCommand(systemPropertyKey, shellCommandConfiguredValue);

        // Verify system property from adb command returns the configured value.
        assertThat(getSystemPropertyByRunningAdbCommand(systemPropertyKey))
                .isEqualTo(String.valueOf(shellCommandConfiguredValue));

        // Verify the SystemProperties getter method is stubbed to return overridden value.
        assertThat(SystemProperties.getBoolean(systemPropertyKey, defaultValue))
                .isEqualTo(defaultValue);

        // Now set the SystemProperties value using method and verify that correct value is
        // returned.
        TestableSystemProperties.set(systemPropertyKey, String.valueOf(apiConfiguredValue));
        assertThat(SystemProperties.getBoolean(systemPropertyKey, defaultValue))
                .isEqualTo(apiConfiguredValue);
    }

    @Test
    public void testForceToReturnDefaultValue_stringValue() {
        String systemPropertyKey = "debug.adservices.testKeyString";
        String shellCommandConfiguredValue = "1";
        String defaultValue = "2";
        String apiConfiguredValue = "3";
        setSystemPropertyByRunningAdbCommand(systemPropertyKey, shellCommandConfiguredValue);

        // Verify system property from adb command returns the configured value.
        assertThat(getSystemPropertyByRunningAdbCommand(systemPropertyKey))
                .isEqualTo(String.valueOf(shellCommandConfiguredValue));

        // Verify the SystemProperties getter method is stubbed to return overridden value.
        assertThat(SystemProperties.get(systemPropertyKey, defaultValue)).isEqualTo(defaultValue);

        // Now set the SystemProperties value using method and verify that correct value is
        // returned.
        TestableSystemProperties.set(systemPropertyKey, apiConfiguredValue);
        assertThat(SystemProperties.get(systemPropertyKey, defaultValue))
                .isEqualTo(apiConfiguredValue);
    }

    @Test
    public void testGetterWithoutDefaultValue() {
        String systemPropertyKey = "debug.adservices.testKeyString";
        String shellCommandConfiguredValue = "1";
        String emptyString = "";
        String apiConfiguredValue = "2";

        setSystemPropertyByRunningAdbCommand(systemPropertyKey, shellCommandConfiguredValue);

        // Verify system property from adb command returns the configured value.
        assertThat(getSystemPropertyByRunningAdbCommand(systemPropertyKey))
                .isEqualTo(shellCommandConfiguredValue);

        // Verify the SystemProperties getter method is stubbed to return overridden value.
        assertThat(SystemProperties.get(systemPropertyKey)).isEqualTo(emptyString);

        TestableSystemProperties.set(systemPropertyKey, apiConfiguredValue);
        assertThat(SystemProperties.get(systemPropertyKey)).isEqualTo(apiConfiguredValue);
    }

    private <T> void setSystemPropertyByRunningAdbCommand(
            String systemPropertyKey, T systemPropertyValue) {
        String stringVal = String.valueOf(systemPropertyValue);

        ShellUtils.runShellCommand("setprop %s %s", systemPropertyKey, stringVal);
    }

    private String getSystemPropertyByRunningAdbCommand(String systemPropertyKey) {
        return ShellUtils.runShellCommand("getprop %s", systemPropertyKey);
    }
}
