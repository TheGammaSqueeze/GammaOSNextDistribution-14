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

import static android.telephony.ims.RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED;
import static android.telephony.ims.RcsUceAdapter.PUBLISH_STATE_OK;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.ims.PublishAttributes;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.SipDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PublishAttributesTest {

    @Test
    public void testAttributes() {
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

        List<RcsContactPresenceTuple> tuples = new ArrayList<>();
        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
                RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0");
        tuples.add(tupleBuilder.build());

        PublishAttributes attr = new PublishAttributes.Builder(PUBLISH_STATE_OK)
                .setSipDetails(debugInfo)
                .setPresenceTuples(tuples).build();


        assertEquals(PUBLISH_STATE_OK, attr.getPublishState());
        assertEquals(tuples.size(), attr.getPresenceTuples().size());

        SipDetails received = attr.getSipDetails();
        assertEquals(SipDetails.METHOD_REGISTER, received.getMethod());
        assertEquals(cseq, received.getCSeq());
        assertEquals(sipCode, received.getResponseCode());
        assertEquals(reasonPhase, received.getResponsePhrase());
        assertEquals(callId, received.getCallId());
        assertEquals(reasonHeaderCause, received.getReasonHeaderCause());
        assertEquals(reasonHeaderText, received.getReasonHeaderText());
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

        PublishAttributes attr = new PublishAttributes.Builder(PUBLISH_STATE_NOT_PUBLISHED)
                .setSipDetails(debugInfo)
                .build();

        Parcel parcel = Parcel.obtain();
        attr.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PublishAttributes unparcelledAttr = PublishAttributes.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(attr.getPublishState(), unparcelledAttr.getPublishState());

        SipDetails unparcelledDetails = unparcelledAttr.getSipDetails();
        assertEquals(debugInfo.getMethod(), unparcelledDetails.getMethod());
        assertEquals(debugInfo.getCSeq(), unparcelledDetails.getCSeq());
        assertEquals(debugInfo.getResponseCode(), unparcelledDetails.getResponseCode());
        assertEquals(debugInfo.getResponsePhrase(), unparcelledDetails.getResponsePhrase());
        assertEquals(debugInfo.getCallId(), unparcelledDetails.getCallId());
        assertEquals(debugInfo.getReasonHeaderCause(), unparcelledDetails.getReasonHeaderCause());
        assertEquals(debugInfo.getReasonHeaderText(), unparcelledDetails.getReasonHeaderText());
    }
}
