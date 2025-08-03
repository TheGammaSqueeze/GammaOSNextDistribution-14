/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.queryable.queries;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;
import static com.android.queryable.queries.DeviceAdminReceiverQuery.deviceAdminReceiver;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminReceiver;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;
import com.android.queryable.info.DeviceAdminReceiverInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class DeviceAdminReceiverQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private final Queryable mQuery = null;

    private static final Class<? extends DeviceAdminReceiver> CLASS_1 =
            DeviceAdminReceiver.class;

    private static final DeviceAdminReceiverInfo DELEGATED_ADMIN_RECEIVER_1_INFO =
            new DeviceAdminReceiverInfo(CLASS_1);
    private static final DeviceAdminReceiverInfo DELEGATED_ADMIN_RECEIVER_2_INFO =
            new DeviceAdminReceiverInfo("different.class.name");

    @Test
    public void matches_noRestrictions_returnsTrue() {
        DeviceAdminReceiverQueryHelper<Queryable> deviceAdminReceiverQueryHelper =
                new DeviceAdminReceiverQueryHelper<>(mQuery);

        assertThat(deviceAdminReceiverQueryHelper.matches(DELEGATED_ADMIN_RECEIVER_1_INFO))
                .isTrue();
    }

    @Test
    public void matches_broadcastReceiver_doesMatch_returnsTrue() {
        DeviceAdminReceiverQueryHelper<Queryable> deviceAdminReceiverQueryHelper =
                new DeviceAdminReceiverQueryHelper<>(mQuery);

        deviceAdminReceiverQueryHelper.broadcastReceiver().receiverClass().isSameClassAs(CLASS_1);

        assertThat(deviceAdminReceiverQueryHelper.matches(DELEGATED_ADMIN_RECEIVER_1_INFO))
                .isTrue();
    }

    @Test
    public void matches_broadcastReceiver_doesNotMatch_returnsFalse() {
        DeviceAdminReceiverQueryHelper<Queryable> deviceAdminReceiverQueryHelper =
                new DeviceAdminReceiverQueryHelper<>(mQuery);

        deviceAdminReceiverQueryHelper.broadcastReceiver().receiverClass().isSameClassAs(CLASS_1);

        assertThat(deviceAdminReceiverQueryHelper.matches(DELEGATED_ADMIN_RECEIVER_2_INFO))
                .isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        DeviceAdminReceiverQueryHelper<Queryable> deviceAdminReceiverQueryHelper =
                new DeviceAdminReceiverQueryHelper<>(mQuery);

        deviceAdminReceiverQueryHelper.broadcastReceiver().receiverClass().isSameClassAs(CLASS_1);

        assertParcelsCorrectly(
                DeviceAdminReceiverQueryHelper.class, deviceAdminReceiverQueryHelper);
    }

    @Test
    public void deviceAdminReceiverQueryBase_queries() {
        assertThat(deviceAdminReceiver()
                .where().broadcastReceiver().receiverClass().isSameClassAs(CLASS_1)
                .matches(DELEGATED_ADMIN_RECEIVER_1_INFO)
        ).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        DeviceAdminReceiverQueryHelper<Queryable> deviceAdminReceiverQueryHelper =
                new DeviceAdminReceiverQueryHelper<>(mQuery);

        assertThat(deviceAdminReceiverQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasBroadcastReceiverQuery_returnsFalse() {
        DeviceAdminReceiverQueryHelper<Queryable> deviceAdminReceiverQueryHelper =
                new DeviceAdminReceiverQueryHelper<>(mQuery);

        deviceAdminReceiverQueryHelper
                .broadcastReceiver().receiverClass().className().isNotNull();

        assertThat(deviceAdminReceiverQueryHelper.isEmptyQuery()).isFalse();
    }
}
