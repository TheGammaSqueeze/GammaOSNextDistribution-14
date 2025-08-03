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
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceAdminBugreportSharedEventTest {

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String STRING_VALUE = "Value";
    private static final String DIFFERENT_STRING_VALUE = "Value2";
    private static final Intent sIntent = new Intent();

    private static final String DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            TestDeviceAdminReceiver.class.getName();
    private static final String CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver";
    private static final String DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver2";
    private static final DeviceAdminReceiver DEVICE_ADMIN_RECEIVER = new TestDeviceAdminReceiver();
    private static final String BUGREPORT_HASH = "A";
    private static final String DIFFERENT_BUGREPORT_HASH = "A2";

    private static class TestDeviceAdminReceiver extends DeviceAdminReceiver {
    }

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereIntent_works() {
        Intent intent = new Intent(STRING_VALUE);
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, intent, BUGREPORT_HASH).log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereIntent_skipsNonMatching() {
        Intent intent = new Intent(STRING_VALUE);
        Intent differentIntent = new Intent();
        differentIntent.setAction(DIFFERENT_STRING_VALUE);
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, differentIntent, BUGREPORT_HASH).log();
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, intent, BUGREPORT_HASH).log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_works() {
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className().isEqualTo(
                        CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_skipsNonMatching() {
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH)
                .setDeviceAdminReceiver(DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className().isEqualTo(
                        CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_works() {
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH).log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_skipsNonMatching() {
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH)
                .log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereBugreportHash_works() {
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH).log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereBugreportHash().isEqualTo(BUGREPORT_HASH);

        assertThat(eventLogs.poll().bugreportHash()).isEqualTo(BUGREPORT_HASH);
    }

    @Test
    public void whereBugreportHash_skipsNonMatching() {
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, DIFFERENT_BUGREPORT_HASH).log();
        DeviceAdminBugreportSharedEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sIntent, BUGREPORT_HASH).log();

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName())
                        .whereBugreportHash().isEqualTo(BUGREPORT_HASH);

        assertThat(eventLogs.poll().bugreportHash()).isEqualTo(BUGREPORT_HASH);
    }

}
