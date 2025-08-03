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

package com.android.car.internal.test;

import static com.google.common.truth.Truth.assertThat;

import android.car.apitest.CarLessApiTestBase;
import android.car.apitest.StableAIDLTestLargeParcelable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.test.filters.SmallTest;

import com.android.car.internal.LargeParcelable;
import com.android.compatibility.common.util.NonApiTest;

import org.junit.Test;

@SmallTest
public final class LargeParcelableJavaStableAIDLCompTest extends CarLessApiTestBase {

    private static final String TAG = LargeParcelableJavaStableAIDLCompTest.class.getSimpleName();

    private static final int ARRAY_LENGTH_SMALL = 2048;
    // The current threshold is 4096.
    private static final int ARRAY_LENGTH_BIG = 4099;

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testTestLargeParcelableToStableAIDLTestLargeParcelableSmall() throws Exception {
        doTestTestLargeParcelableToStableAIDLTestLargeParcelable(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testTestLargeParcelableToStableAIDLTestLargeParcelableBig() throws Exception {
        doTestTestLargeParcelableToStableAIDLTestLargeParcelable(ARRAY_LENGTH_BIG);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testStableAIDLTestLargeParcelableToTestLargeParcelableSmall() throws Exception {
        doTestStableAIDLTestLargeParcelableToTestLargeParcelable(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testStableAIDLTestLargeParcelableToTestLargeParcelableBig() throws Exception {
        doTestStableAIDLTestLargeParcelableToTestLargeParcelable(ARRAY_LENGTH_BIG);
    }

    private void doTestTestLargeParcelableToStableAIDLTestLargeParcelable(int payloadSize) {
        byte[] payload = LargeParcelableTest.createByteArray(payloadSize);
        TestLargeParcelable origP = new TestLargeParcelable(payload);
        Parcel p = Parcel.obtain();
        origP.writeToParcel(p, 0);
        p.setDataPosition(0);

        // A parcel generated from TestLargeParcelable should be compatible with
        // StableAIDLTestLargeParcelable.
        StableAIDLTestLargeParcelable stableP = new StableAIDLTestLargeParcelable();
        stableP.readFromParcel(p);

        if (payloadSize > LargeParcelable.MAX_DIRECT_PAYLOAD_SIZE) {
            assertThat(stableP.sharedMemoryFd).isNotNull();
            assertThat(stableP.payload).isNull();
        } else {
            assertThat(stableP.sharedMemoryFd).isNull();
            assertThat(stableP.payload).isNotNull();
            assertThat(stableP.payload.length).isNotEqualTo(0);
        }

        StableAIDLTestLargeParcelable payloadP = (StableAIDLTestLargeParcelable)
                LargeParcelable.reconstructStableAIDLParcelable(stableP, true);

        assertThat(payloadP).isNotNull();
        assertThat(payloadP.payload).isNotNull();
        assertThat(payloadP.payload).isEqualTo(payload);
    }

    private void doTestStableAIDLTestLargeParcelableToTestLargeParcelable(int payloadSize) {
        byte[] payload = LargeParcelableTest.createByteArray(payloadSize);
        StableAIDLTestLargeParcelable origP = new StableAIDLTestLargeParcelable();
        origP.payload = payload;
        Parcelable stableP = LargeParcelable.toLargeParcelable(origP, () -> {
            StableAIDLTestLargeParcelable o = new StableAIDLTestLargeParcelable();
            o.payload = new byte[0];
            return o;
        });
        Parcel p = Parcel.obtain();
        stableP.writeToParcel(p, 0);
        p.setDataPosition(0);

        // A parcel generated from StableAIDLTestLargeParcelable should be compatible with
        // TestLargeParcelable.
        TestLargeParcelable testP = new TestLargeParcelable(p);

        assertThat(testP.byteData).isEqualTo(payload);
    }

}
