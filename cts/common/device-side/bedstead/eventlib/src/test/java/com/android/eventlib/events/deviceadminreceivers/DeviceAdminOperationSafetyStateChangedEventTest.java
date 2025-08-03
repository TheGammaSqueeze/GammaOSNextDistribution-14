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

package com.android.eventlib.events.deviceadminreceivers;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceAdminOperationSafetyStateChangedEventTest {

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final String DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            TestDeviceAdminReceiver.class.getName();
    private static final String CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver";
    private static final String DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver2";
    private static final DeviceAdminReceiver DEVICE_ADMIN_RECEIVER = new TestDeviceAdminReceiver();
    private static final int REASON = 1;
    private static final int DIFFERENT_REASON = 2;
    private static final boolean IS_SAFE = true;
    private static final boolean DIFFERENT_IS_SAFE = false;

    private static class TestDeviceAdminReceiver extends DeviceAdminReceiver {
    }

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_works() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_skipsNonMatching() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE)
                .setDeviceAdminReceiver(DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_works() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE).log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_skipsNonMatching() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE)
                .log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereReason_works() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE).log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereReason().isEqualTo(REASON);

        assertThat(eventLogs.poll().reason()).isEqualTo(REASON);
    }

    @Test
    public void whereReason_skipsNonMatching() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, DIFFERENT_REASON, IS_SAFE).log();
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE).log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereReason().isEqualTo(REASON);

        assertThat(eventLogs.poll().reason()).isEqualTo(REASON);
    }

    @Test
    public void whereIsSafe_works() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE).log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereIsSafe().isEqualTo(IS_SAFE);

        assertThat(eventLogs.poll().isSafe()).isEqualTo(IS_SAFE);
    }

    @Test
    public void whereIsSafe_skipsNonMatching() {
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, DIFFERENT_IS_SAFE).log();
        DeviceAdminOperationSafetyStateChangedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, REASON, IS_SAFE).log();

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName())
                        .whereIsSafe().isEqualTo(IS_SAFE);

        assertThat(eventLogs.poll().isSafe()).isEqualTo(IS_SAFE);
    }
}
