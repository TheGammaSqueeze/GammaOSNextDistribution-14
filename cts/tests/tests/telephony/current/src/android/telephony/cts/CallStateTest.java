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
import android.telephony.CallQuality;
import android.telephony.CallState;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;

import org.junit.Test;

public class CallStateTest {
    private static final String TEST_IMS_SESSION_ID = "1";
    private final CallQuality mTestCallQuality = new CallQuality();

    @Test
    public void testParceling() {
        CallState origCallState =
                new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                        .setCallQuality(mTestCallQuality)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setImsCallSessionId(TEST_IMS_SESSION_ID)
                        .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL)
                        .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE).build();

        Parcel callStateParcel = Parcel.obtain();
        origCallState.writeToParcel(callStateParcel, 0);
        callStateParcel.setDataPosition(0);

        CallState parcelResponse =
                CallState.CREATOR.createFromParcel(callStateParcel);
        assertTrue(parcelResponse.equals(origCallState));
    }

    @Test
    public void testCallAttributesGetter() {
        CallState testCallState =
                new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                        .setCallQuality(mTestCallQuality)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setImsCallSessionId(TEST_IMS_SESSION_ID)
                        .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL)
                        .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE).build();
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE, testCallState.getCallState());
        assertEquals(mTestCallQuality, testCallState.getCallQuality());
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, testCallState.getNetworkType());
        assertEquals(TEST_IMS_SESSION_ID, testCallState.getImsCallSessionId());
        assertEquals(ImsCallProfile.SERVICE_TYPE_NORMAL, testCallState.getImsCallServiceType());
        assertEquals(ImsCallProfile.CALL_TYPE_VOICE, testCallState.getImsCallType());
    }
}
