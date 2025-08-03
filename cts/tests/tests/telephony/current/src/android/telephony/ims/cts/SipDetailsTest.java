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

package android.telephony.ims.cts;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.ims.SipDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SipDetailsTest {

    @Test
    public void testSipDetailsAttr() {
        int cseq = 1;
        int sipCode = 200;
        String responsePhrase = "OK";
        String callId = "call-id";
        int reasonHeaderCause = 600;
        String reasonHeaderText = "Busy Everywhere";

        SipDetails debugInfo = new SipDetails.Builder(SipDetails.METHOD_REGISTER)
                .setCSeq(cseq)
                .setSipResponseCode(sipCode, responsePhrase)
                .setSipResponseReasonHeader(reasonHeaderCause, reasonHeaderText)
                .setCallId(callId)
                .build();

        assertEquals(SipDetails.METHOD_REGISTER, debugInfo.getMethod());
        assertEquals(cseq, debugInfo.getCSeq());
        assertEquals(sipCode, debugInfo.getResponseCode());
        assertEquals(responsePhrase, debugInfo.getResponsePhrase());
        assertEquals(callId, debugInfo.getCallId());
        assertEquals(reasonHeaderCause, debugInfo.getReasonHeaderCause());
        assertEquals(reasonHeaderText, debugInfo.getReasonHeaderText());
    }

    @Test
    public void testParcelUnparcel() {
        int cseq = 1;
        int sipCode = 200;
        String reasonPhase = "OK";
        String callId = "call-id";
        int reasonHeaderCause = 600;
        String reasonHeaderText = "Busy Everywhere";

        SipDetails debugInfo = new SipDetails.Builder(SipDetails.METHOD_REGISTER)
                .setCSeq(cseq)
                .setSipResponseCode(sipCode, reasonPhase)
                .setSipResponseReasonHeader(reasonHeaderCause, reasonHeaderText)
                .setCallId(callId)
                .build();

        Parcel parcel = Parcel.obtain();
        debugInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SipDetails unparcelledDetails = SipDetails.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(debugInfo.getMethod(), unparcelledDetails.getMethod());
        assertEquals(debugInfo.getCSeq(), unparcelledDetails.getCSeq());
        assertEquals(debugInfo.getResponseCode(), unparcelledDetails.getResponseCode());
        assertEquals(debugInfo.getResponsePhrase(), unparcelledDetails.getResponsePhrase());
        assertEquals(debugInfo.getCallId(), unparcelledDetails.getCallId());
        assertEquals(debugInfo.getReasonHeaderCause(), unparcelledDetails.getReasonHeaderCause());
        assertEquals(debugInfo.getReasonHeaderText(), unparcelledDetails.getReasonHeaderText());
    }
}
