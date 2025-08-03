/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.opp;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothOppBatchTest {
    private BluetoothOppBatch mBluetoothOppBatch;
    private Context mContext;

    private BluetoothOppShareInfo mInitShareInfo;

    @Before
    public void setUp() throws Exception {
        mInitShareInfo = new BluetoothOppShareInfo(0, null, null, null, null, 0,
                "00:11:22:33:44:55", 0, 0, BluetoothShare.STATUS_PENDING, 0, 0, 0, false);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mBluetoothOppBatch = new BluetoothOppBatch(mContext, mInitShareInfo);
    }

    @Test
    public void constructor_instanceCreatedCorrectly() {
        assertThat(mBluetoothOppBatch.mTimestamp).isEqualTo(mInitShareInfo.mTimestamp);
        assertThat(mBluetoothOppBatch.mDirection).isEqualTo(mInitShareInfo.mDirection);
        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_PENDING);
        assertThat(mBluetoothOppBatch.mDestination.getAddress())
                .isEqualTo(mInitShareInfo.mDestination);
        assertThat(mBluetoothOppBatch.hasShare(mInitShareInfo)).isTrue();
    }

    @Test
    public void addShare_shareInfoStoredCorrectly() {
        BluetoothOppShareInfo newBluetoothOppShareInfo = new BluetoothOppShareInfo(1, null, null,
                null, null, 0, "AA:BB:22:CD:E0:55", 0, 0, BluetoothShare.STATUS_PENDING, 0, 0, 0,
                false);

        mBluetoothOppBatch.registerListener(new BluetoothOppBatch.BluetoothOppBatchListener() {
            @Override
            public void onShareAdded(int id) {
                assertThat(id).isEqualTo(newBluetoothOppShareInfo.mId);
            }

            @Override
            public void onShareDeleted(int id) {
            }

            @Override
            public void onBatchCanceled() {
            }
        });
        assertThat(mBluetoothOppBatch.isEmpty()).isFalse();
        assertThat(mBluetoothOppBatch.getNumShares()).isEqualTo(1);
        assertThat(mBluetoothOppBatch.hasShare(mInitShareInfo)).isTrue();
        assertThat(mBluetoothOppBatch.hasShare(newBluetoothOppShareInfo)).isFalse();
        mBluetoothOppBatch.addShare(newBluetoothOppShareInfo);
        assertThat(mBluetoothOppBatch.getNumShares()).isEqualTo(2);
        assertThat(mBluetoothOppBatch.hasShare(mInitShareInfo)).isTrue();
        assertThat(mBluetoothOppBatch.hasShare(newBluetoothOppShareInfo)).isTrue();
    }

    @Test
    public void cancelBatch_cancelSuccessfully() {

        BluetoothMethodProxy proxy = spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(proxy);
        doReturn(0).when(proxy).contentResolverDelete(any(), any(), any(), any());
        doReturn(0).when(proxy).contentResolverUpdate(any(), any(), any(), any(), any());

        assertThat(mBluetoothOppBatch.getPendingShare()).isEqualTo(mInitShareInfo);

        mBluetoothOppBatch.cancelBatch();
        assertThat(mBluetoothOppBatch.isEmpty()).isTrue();

        BluetoothMethodProxy.setInstanceForTesting(null);
    }
}
