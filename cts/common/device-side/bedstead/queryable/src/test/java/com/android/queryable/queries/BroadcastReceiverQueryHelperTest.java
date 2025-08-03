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
import static com.android.queryable.queries.BroadcastReceiverQuery.broadcastReceiver;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;
import com.android.queryable.info.BroadcastReceiverInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class BroadcastReceiverQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private final Queryable mQuery = null;

    private static final Class<? extends BroadcastReceiver> CLASS_1 = BroadcastReceiver.class;

    private static final BroadcastReceiverInfo BROADCAST_RECEIVER_1_INFO =
            new BroadcastReceiverInfo(CLASS_1);
    private static final BroadcastReceiverInfo BROADCAST_RECEIVER_2_INFO =
            new BroadcastReceiverInfo("different.class.name");

    @Test
    public void matches_noRestrictions_returnsTrue() {
        BroadcastReceiverQueryHelper<Queryable> broadcastReceiverQueryHelper =
                new BroadcastReceiverQueryHelper<>(mQuery);

        assertThat(broadcastReceiverQueryHelper.matches(BROADCAST_RECEIVER_1_INFO)).isTrue();
    }

    @Test
    public void matches_receiverClass_doesMatch_returnsTrue() {
        BroadcastReceiverQueryHelper<Queryable> broadcastReceiverQueryHelper =
                new BroadcastReceiverQueryHelper<>(mQuery);

        broadcastReceiverQueryHelper.receiverClass().isSameClassAs(CLASS_1);

        assertThat(broadcastReceiverQueryHelper.matches(BROADCAST_RECEIVER_1_INFO)).isTrue();
    }

    @Test
    public void matches_receiverClass_doesNotMatch_returnsFalse() {
        BroadcastReceiverQueryHelper<Queryable> broadcastReceiverQueryHelper =
                new BroadcastReceiverQueryHelper<>(mQuery);

        broadcastReceiverQueryHelper.receiverClass().isSameClassAs(CLASS_1);

        assertThat(broadcastReceiverQueryHelper.matches(BROADCAST_RECEIVER_2_INFO)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        BroadcastReceiverQueryHelper<Queryable> broadcastReceiverQueryHelper =
                new BroadcastReceiverQueryHelper<>(mQuery);

        broadcastReceiverQueryHelper.receiverClass().isSameClassAs(CLASS_1);

        assertParcelsCorrectly(BroadcastReceiverQueryHelper.class, broadcastReceiverQueryHelper);
    }

    @Test
    public void broadcastReceiverQueryBase_queries() {
        assertThat(broadcastReceiver()
                .where().receiverClass().isSameClassAs(CLASS_1)
                .matches(BROADCAST_RECEIVER_1_INFO)
        ).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        BroadcastReceiverQueryHelper<Queryable> broadcastReceiverQueryHelper =
                new BroadcastReceiverQueryHelper<>(mQuery);

        assertThat(broadcastReceiverQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasReceiverClass_returnsFalse() {
        BroadcastReceiverQueryHelper<Queryable> broadcastReceiverQueryHelper =
                new BroadcastReceiverQueryHelper<>(mQuery);

        broadcastReceiverQueryHelper.receiverClass().className().isNotNull();

        assertThat(broadcastReceiverQueryHelper.isEmptyQuery()).isFalse();
    }
}
