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
import android.os.PersistableBundle;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceAdminTransferOwnershipCompleteEventTest {

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String STRING_VALUE = "Value";
    private static final String DIFFERENT_STRING_VALUE = "Value2";

    private static final String DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            TestDeviceAdminReceiver.class.getName();
    private static final String CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver";
    private static final String DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver2";
    private static final DeviceAdminReceiver DEVICE_ADMIN_RECEIVER = new TestDeviceAdminReceiver();
    private static final PersistableBundle sPersistableBundle = new PersistableBundle();

    private static class TestDeviceAdminReceiver extends DeviceAdminReceiver {
    }

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereBundle_works() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("STRING", STRING_VALUE);
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, bundle).log();

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName())
                        .whereBundle().key("STRING").stringValue().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().bundle()).isEqualTo(bundle);
    }

    @Test
    public void whereBundle_skipsNonMatching() {
        PersistableBundle differentBundle = new PersistableBundle();
        differentBundle.putString("STRING", DIFFERENT_STRING_VALUE);
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, differentBundle).log();
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("STRING", STRING_VALUE);
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, bundle).log();

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName())
                        .whereBundle().key("STRING").stringValue().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().bundle()).isEqualTo(bundle);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_works() {
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sPersistableBundle)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_skipsNonMatching() {
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sPersistableBundle)
                .setDeviceAdminReceiver(DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sPersistableBundle)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_works() {
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sPersistableBundle).log();

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_skipsNonMatching() {
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sPersistableBundle)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminTransferOwnershipCompleteEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, sPersistableBundle)
                .log();

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

}
