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

package com.android.queryable.info;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminReceiver;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.info.DeviceAdminReceiverInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class DeviceAdminReceiverInfoTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Class<? extends DeviceAdminReceiver> TEST_DEVICE_ADMIN_RECEIVER_CLASS =
            DeviceAdminReceiver.class;
    private static final String TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            DeviceAdminReceiver.class.getName();
    private static final String TEST_DEVICE_ADMIN_RECEIVER_SIMPLE_NAME =
            DeviceAdminReceiver.class.getSimpleName();
    private static final DeviceAdminReceiver TEST_DEVICE_ADMIN_RECEIVER_INSTANCE =
            new DeviceAdminReceiver();

    @Test
    public void classConstructor_setsClassName() {
        DeviceAdminReceiverInfo deviceAdminReceiverInfo = new DeviceAdminReceiverInfo(
                TEST_DEVICE_ADMIN_RECEIVER_CLASS);

        assertThat(deviceAdminReceiverInfo.className())
                .isEqualTo(TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void instanceConstructor_setsClassName() {
        DeviceAdminReceiverInfo deviceAdminReceiverInfo = new DeviceAdminReceiverInfo(
                TEST_DEVICE_ADMIN_RECEIVER_INSTANCE);

        assertThat(deviceAdminReceiverInfo.className())
                .isEqualTo(TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void stringConstructor_setsClassName() {
        DeviceAdminReceiverInfo deviceAdminReceiverInfo = new DeviceAdminReceiverInfo(
                TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(deviceAdminReceiverInfo.className())
                .isEqualTo(TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void simpleName_getsSimpleName() {
        DeviceAdminReceiverInfo deviceAdminReceiverInfo = new DeviceAdminReceiverInfo(
                TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(deviceAdminReceiverInfo.simpleName())
                .isEqualTo(TEST_DEVICE_ADMIN_RECEIVER_SIMPLE_NAME);
    }

    @Test
    public void parcel_parcelsCorrectly() {
        DeviceAdminReceiverInfo deviceAdminReceiverInfo = new DeviceAdminReceiverInfo(
                TEST_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertParcelsCorrectly(DeviceAdminReceiverInfo.class, deviceAdminReceiverInfo);
    }
}
