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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class BroadcastReceiverInfoTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();


    private static class TestBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

        }
    }

    private static final Class<? extends BroadcastReceiver> TEST_BROADCAST_RECEIVER_CLASS =
            TestBroadcastReceiver.class;
    private static final String TEST_BROADCAST_RECEIVER_CLASS_NAME =
            TestBroadcastReceiver.class.getName();
    private static final String TEST_BROADCAST_RECEIVER_SIMPLE_NAME =
            TestBroadcastReceiver.class.getSimpleName();
    private static final BroadcastReceiver TEST_BROADCAST_RECEIVER_INSTANCE =
            new TestBroadcastReceiver();

    @Test
    public void classConstructor_setsClassName() {
        BroadcastReceiverInfo broadcastReceiverInfo = new BroadcastReceiverInfo(
                TEST_BROADCAST_RECEIVER_CLASS);

        assertThat(broadcastReceiverInfo.className())
                .isEqualTo(TEST_BROADCAST_RECEIVER_CLASS_NAME);
    }

    @Test
    public void instanceConstructor_setsClassName() {
        BroadcastReceiverInfo broadcastReceiverInfo = new BroadcastReceiverInfo(
                TEST_BROADCAST_RECEIVER_INSTANCE);

        assertThat(broadcastReceiverInfo.className())
                .isEqualTo(TEST_BROADCAST_RECEIVER_CLASS_NAME);
    }

    @Test
    public void stringConstructor_setsClassName() {
        BroadcastReceiverInfo broadcastReceiverInfo = new BroadcastReceiverInfo(
                TEST_BROADCAST_RECEIVER_CLASS_NAME);

        assertThat(broadcastReceiverInfo.className())
                .isEqualTo(TEST_BROADCAST_RECEIVER_CLASS_NAME);
    }

    @Test
    public void simpleName_getsSimpleName() {
        BroadcastReceiverInfo broadcastReceiverInfo = new BroadcastReceiverInfo(
                TEST_BROADCAST_RECEIVER_CLASS_NAME);

        assertThat(broadcastReceiverInfo.simpleName())
                .isEqualTo(TEST_BROADCAST_RECEIVER_SIMPLE_NAME);
    }

    @Test
    public void parcel_parcelsCorrectly() {
        BroadcastReceiverInfo broadcastReceiverInfo = new BroadcastReceiverInfo(
                TEST_BROADCAST_RECEIVER_CLASS_NAME);

        assertParcelsCorrectly(BroadcastReceiverInfo.class, broadcastReceiverInfo);
    }
}
