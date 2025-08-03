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
package com.android.adservices.service;

import static com.android.adservices.service.CommonFlags.ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.fixture.TestableSystemProperties;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Test;


public final class CommonPhFlagsTest extends AdServicesExtendedMockitoTestCase {
    private final CommonFlags mPhFlags = new CommonPhFlags() {};

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new, TestableSystemProperties::new)
                .build();
    }

    @Test
    public void testGetAdServicesShellCommandEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(mPhFlags.getAdServicesShellCommandEnabled())
                .isEqualTo(ADSERVICES_SHELL_COMMAND_ENABLED);

        boolean phOverridingValue = !ADSERVICES_SHELL_COMMAND_ENABLED;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SHELL_COMMAND_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mPhFlags.getAdServicesShellCommandEnabled()).isEqualTo(phOverridingValue);
    }
}
