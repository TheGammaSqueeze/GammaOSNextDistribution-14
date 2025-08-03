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

package com.android.server.nearby;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static com.android.server.nearby.NearbyConfiguration.NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY;
import static com.android.server.nearby.NearbyConfiguration.NEARBY_MAINLINE_NANO_APP_MIN_VERSION;
import static com.android.server.nearby.NearbyConfiguration.NEARBY_SUPPORT_TEST_APP;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class NearbyConfigurationTest {

    private static final String NAMESPACE = NearbyConfiguration.getNamespace();
    private NearbyConfiguration mNearbyConfiguration;

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG, READ_DEVICE_CONFIG);
    }

    @Test
    public void testDeviceConfigChanged() throws InterruptedException {
        mNearbyConfiguration = new NearbyConfiguration();

        DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP,
                "false", false);
        DeviceConfig.setProperty(NAMESPACE, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY,
                "false", false);
        DeviceConfig.setProperty(NAMESPACE, NEARBY_MAINLINE_NANO_APP_MIN_VERSION,
                "1", false);
        Thread.sleep(500);

        assertThat(mNearbyConfiguration.isTestAppSupported()).isFalse();
        assertThat(mNearbyConfiguration.isPresenceBroadcastLegacyEnabled()).isFalse();
        assertThat(mNearbyConfiguration.getNanoAppMinVersion()).isEqualTo(1);

        DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP,
                "true", false);
        DeviceConfig.setProperty(NAMESPACE, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY,
                "true", false);
        DeviceConfig.setProperty(NAMESPACE, NEARBY_MAINLINE_NANO_APP_MIN_VERSION,
                "3", false);
        Thread.sleep(500);

        // TestAppSupported Flag can only be set to true in user-debug devices.
        if (Build.isDebuggable()) {
            assertThat(mNearbyConfiguration.isTestAppSupported()).isTrue();
        } else {
            assertThat(mNearbyConfiguration.isTestAppSupported()).isFalse();
        }
        assertThat(mNearbyConfiguration.isPresenceBroadcastLegacyEnabled()).isTrue();
        assertThat(mNearbyConfiguration.getNanoAppMinVersion()).isEqualTo(3);
    }

    @After
    public void tearDown() {
        // Sets DeviceConfig values back to default
        DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP,
                "false", true);
        DeviceConfig.setProperty(NAMESPACE, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY,
                "false", true);
        DeviceConfig.setProperty(NAMESPACE, NEARBY_MAINLINE_NANO_APP_MIN_VERSION,
                "0", true);
    }
}
