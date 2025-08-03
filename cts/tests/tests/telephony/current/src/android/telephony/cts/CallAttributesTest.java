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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.CallAttributes;
import android.telephony.CallQuality;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;

import org.junit.Test;

public class CallAttributesTest {
    private static final String TEST_IMS_SESSION_ID = "1";
    private final CallQuality mTestCallQuality = new CallQuality();
    private final PreciseCallState mTestPreciseCallState = new PreciseCallState();

    @Test
    public void testParceling() {
        CallAttributes origCallAttribute =
                new CallAttributes(mTestPreciseCallState, 0, mTestCallQuality);

        Parcel callAttributesParcel = Parcel.obtain();
        origCallAttribute.writeToParcel(callAttributesParcel, 0);
        callAttributesParcel.setDataPosition(0);

        CallAttributes parcelResponse =
                CallAttributes.CREATOR.createFromParcel(callAttributesParcel);
        assertTrue(parcelResponse.equals(origCallAttribute));
    }

    @Test
    public void testCallAttributesGetter() {
        CallAttributes callAttributes =
                new CallAttributes(
                        mTestPreciseCallState, TelephonyManager.NETWORK_TYPE_LTE, mTestCallQuality);
        assertEquals(mTestCallQuality, callAttributes.getCallQuality());
        assertEquals(mTestPreciseCallState, callAttributes.getPreciseCallState());
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, callAttributes.getNetworkType());
    }
}
