/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.cts;

import static android.telephony.data.NetworkSliceInfo.SLICE_SERVICE_TYPE_EMBB;
import static android.telephony.data.NetworkSliceInfo.SLICE_SERVICE_TYPE_MIOT;
import static android.telephony.data.NetworkSliceInfo.SLICE_STATUS_CONFIGURED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.os.Parcel;
import android.telephony.data.NetworkSliceInfo;

import org.junit.Test;

public class NetworkSliceInfoTest {
    private static final int TEST_SLICE_DIFFERENTIATOR = 1;
    private static final int TEST_SLICE_SERVICE_TYPE = SLICE_SERVICE_TYPE_EMBB;
    private static final int TEST_HPLMN_SLICE_DIFFERENTIATOR = 10;
    private static final int TEST_HPLMN_SLICE_SERVICE_TYPE = SLICE_SERVICE_TYPE_MIOT;
    private static final int TEST_SLICE_STATUS = SLICE_STATUS_CONFIGURED;

    @Test
    public void testParceling() {
        testParceling(new NetworkSliceInfo.Builder()
                .setSliceServiceType(TEST_SLICE_SERVICE_TYPE)
                .build());

        testParceling(new NetworkSliceInfo.Builder()
                .setSliceServiceType(TEST_SLICE_SERVICE_TYPE)
                .setSliceDifferentiator(TEST_SLICE_DIFFERENTIATOR)
                .build());

        testParceling(new NetworkSliceInfo.Builder()
                .setSliceServiceType(TEST_SLICE_SERVICE_TYPE)
                .setSliceDifferentiator(TEST_SLICE_DIFFERENTIATOR)
                .setMappedHplmnSliceServiceType(TEST_HPLMN_SLICE_SERVICE_TYPE)
                .build());

        testParceling(new NetworkSliceInfo.Builder()
                .setSliceServiceType(TEST_SLICE_SERVICE_TYPE)
                .setSliceDifferentiator(TEST_SLICE_DIFFERENTIATOR)
                .setMappedHplmnSliceServiceType(TEST_HPLMN_SLICE_SERVICE_TYPE)
                .setMappedHplmnSliceDifferentiator(TEST_HPLMN_SLICE_DIFFERENTIATOR)
                .build());
    }

    private void testParceling(NetworkSliceInfo sliceInfo1) {
        Parcel stateParcel = Parcel.obtain();
        sliceInfo1.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        NetworkSliceInfo parcelResponse = NetworkSliceInfo.CREATOR.createFromParcel(stateParcel);
        assertThat(parcelResponse).isEqualTo(sliceInfo1);
    }

    @Test
    public void testSliceDifferentiatorRange() {
        new NetworkSliceInfo.Builder()
                .setSliceDifferentiator(NetworkSliceInfo.MIN_SLICE_DIFFERENTIATOR)
                .setSliceDifferentiator(NetworkSliceInfo.MAX_SLICE_DIFFERENTIATOR)
                .setMappedHplmnSliceDifferentiator(NetworkSliceInfo.MIN_SLICE_DIFFERENTIATOR)
                .setMappedHplmnSliceDifferentiator(NetworkSliceInfo.MAX_SLICE_DIFFERENTIATOR);

        try {
            new NetworkSliceInfo.Builder()
                    .setSliceDifferentiator(NetworkSliceInfo.MIN_SLICE_DIFFERENTIATOR - 1);
            fail("Illegal state exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        try {
            new NetworkSliceInfo.Builder()
                    .setMappedHplmnSliceDifferentiator(
                            NetworkSliceInfo.MIN_SLICE_DIFFERENTIATOR - 1);
            fail("Illegal state exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        try {
            new NetworkSliceInfo.Builder()
                    .setSliceDifferentiator(NetworkSliceInfo.MAX_SLICE_DIFFERENTIATOR + 1);
            fail("Illegal state exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        try {
            new NetworkSliceInfo.Builder()
                    .setMappedHplmnSliceDifferentiator(
                            NetworkSliceInfo.MAX_SLICE_DIFFERENTIATOR + 1);
            fail("Illegal state exception expected");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testGetterAndSetterForSliceStatus() {
        NetworkSliceInfo si = new NetworkSliceInfo.Builder().setStatus(TEST_SLICE_STATUS).build();
        assertThat(si.getStatus()).isEqualTo(SLICE_STATUS_CONFIGURED);
    }
}
