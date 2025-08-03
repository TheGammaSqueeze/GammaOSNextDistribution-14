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

package com.android.tests.scheduling;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.DeviceConfig;
import android.scheduling.RebootReadinessManager;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RebootReadinessTest {

    private static final String PROPERTY_ACTIVE_POLLING_INTERVAL_MS = "active_polling_interval_ms";
    private static final String PROPERTY_DISABLE_INTERACTIVITY_CHECK =
            "disable_interactivity_check";
    private static final String PROPERTY_DISABLE_APP_ACTIVITY_CHECK = "disable_app_activity_check";
    private static final String PROPERTY_DISABLE_SUBSYSTEMS_CHECK = "disable_subsystems_check";


    @Test
    public void testRebootReadyBroadcastReceived() throws Exception {
        setTestConfigurations();
        RebootReadinessManager rebootReadinessManager = InstrumentationRegistry.getContext()
                .getSystemService(RebootReadinessManager.class);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.REBOOT);
        CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(
                        RebootReadinessManager.EXTRA_IS_READY_TO_REBOOT, false)) {
                    latch.countDown();
                }
            }
        };
        IntentFilter filter = new IntentFilter(RebootReadinessManager.ACTION_REBOOT_READY);
        InstrumentationRegistry.getContext().registerReceiver(receiver, filter);
        if (rebootReadinessManager != null) {
            rebootReadinessManager.markRebootPending();
        }
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    // Set DeviceConfig properties so the device is reboot ready.
    private void setTestConfigurations() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.WRITE_DEVICE_CONFIG);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_ACTIVE_POLLING_INTERVAL_MS, "1000", false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_INTERACTIVITY_CHECK, "true", false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_APP_ACTIVITY_CHECK, "true", false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_SUBSYSTEMS_CHECK, "true", false);
    }
}
