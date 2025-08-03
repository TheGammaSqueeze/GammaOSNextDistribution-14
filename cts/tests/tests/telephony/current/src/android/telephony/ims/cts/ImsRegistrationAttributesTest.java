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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.AccessNetworkConstants;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.SipDetails;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImsRegistrationAttributesTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testRegistrationTypeToTransportAttr() {
        int cseq = 1;
        int sipCode = 200;
        String responsePhrase = "OK";
        String callId = "call-id";
        int reasonHeaderCause = 10;
        String reasonHeaderText = "reasonHeaderText";

        ArraySet<String> featureTags = new ArraySet<>();
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.msg\"");
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"");
        featureTags.add("+g.gsma.callcomposer");

        SipDetails debugInfo = new SipDetails.Builder(SipDetails.METHOD_REGISTER)
                .setCSeq(cseq)
                .setSipResponseCode(sipCode, responsePhrase)
                .setSipResponseReasonHeader(reasonHeaderCause, reasonHeaderText)
                .setCallId(callId)
                .build();

        // IWLAN
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).setFeatureTags(featureTags)
                .setSipDetails(debugInfo)
                .build();
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                attr.getRegistrationTechnology());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                attr.getTransportType());
        assertEquals(0, (attr.getAttributeFlags()
                & ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET));
        assertEquals(featureTags, attr.getFeatureTags());

        assertNotNull(attr.getSipDetails());
        assertEquals(SipDetails.METHOD_REGISTER, attr.getSipDetails().getMethod());
        assertEquals(cseq, attr.getSipDetails().getCSeq());
        assertEquals(sipCode, attr.getSipDetails().getResponseCode());
        assertEquals(responsePhrase, attr.getSipDetails().getResponsePhrase());
        assertEquals(reasonHeaderCause, attr.getSipDetails().getReasonHeaderCause());
        assertEquals(reasonHeaderText, attr.getSipDetails().getReasonHeaderText());
        assertEquals(callId, attr.getSipDetails().getCallId());

        //LTE
        attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).build();
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                attr.getRegistrationTechnology());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                attr.getTransportType());
        assertEquals(0, (attr.getAttributeFlags()
                & ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET));
        assertNotNull(attr.getFeatureTags());
        assertEquals(0, attr.getFeatureTags().size());
        assertNull(attr.getSipDetails());

        // cross sim
        attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM).build();
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM,
                attr.getRegistrationTechnology());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                attr.getTransportType());
        assertEquals(ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET,
                (attr.getAttributeFlags()
                        & ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET));
        assertNotNull(attr.getFeatureTags());
        assertEquals(0, attr.getFeatureTags().size());
        assertNull(attr.getSipDetails());

        // NR
        attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_NR).build();
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_NR,
                attr.getRegistrationTechnology());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                attr.getTransportType());
        assertEquals(0,
                (attr.getAttributeFlags()
                        & ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET));
        assertNotNull(attr.getFeatureTags());
        assertEquals(0, attr.getFeatureTags().size());
        assertNull(attr.getSipDetails());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EMERGENCY_REGISTRATION_STATE)
    public void testRegistrationTypeToTransportAttrForEmergency() {
        // emergency call with emergency registration
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE)
                .setFlagRegistrationTypeEmergency()
                .build();
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                attr.getRegistrationTechnology());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                attr.getTransportType());
        assertEquals(0, (attr.getAttributeFlags()
                & ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET));
        assertEquals(ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY,
                (attr.getAttributeFlags()
                        & ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY));
        assertEquals(0, (attr.getAttributeFlags()
                & ImsRegistrationAttributes.ATTR_VIRTUAL_FOR_ANONYMOUS_EMERGENCY_CALL));
        assertNotNull(attr.getFeatureTags());
        assertEquals(0, attr.getFeatureTags().size());
        assertNull(attr.getSipDetails());

        // emergency call without emergency registration
        attr = new ImsRegistrationAttributes.Builder(
            ImsRegistrationImplBase.REGISTRATION_TECH_LTE)
            .setFlagRegistrationTypeEmergency()
            .setFlagVirtualRegistrationForEmergencyCall()
            .build();
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                attr.getRegistrationTechnology());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                attr.getTransportType());
        assertEquals(0, (attr.getAttributeFlags()
                & ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET));
        assertEquals(ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY,
                (attr.getAttributeFlags()
                        & ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY));
        assertEquals(ImsRegistrationAttributes.ATTR_VIRTUAL_FOR_ANONYMOUS_EMERGENCY_CALL,
                (attr.getAttributeFlags()
                        & ImsRegistrationAttributes.ATTR_VIRTUAL_FOR_ANONYMOUS_EMERGENCY_CALL));
        assertNotNull(attr.getFeatureTags());
        assertEquals(0, attr.getFeatureTags().size());
        assertNull(attr.getSipDetails());
    }

    @Test
    public void testParcelUnparcel() {
        ArraySet<String> featureTags = new ArraySet<>();
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.msg\"");
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"");
        featureTags.add("+g.gsma.callcomposer");
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).setFeatureTags(featureTags)
                .build();

        Parcel parcel = Parcel.obtain();
        attr.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ImsRegistrationAttributes unparcelledAttr =
                ImsRegistrationAttributes.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(attr.getRegistrationTechnology(), unparcelledAttr.getRegistrationTechnology());
        assertEquals(attr.getTransportType(), unparcelledAttr.getTransportType());
        assertEquals(attr.getAttributeFlags(), unparcelledAttr.getAttributeFlags());
        assertEquals(attr.getFeatureTags(), unparcelledAttr.getFeatureTags());
        assertNull(unparcelledAttr.getSipDetails());
    }

    @Test
    public void testParcelUnparcelWithSipDebugInfo() {
        SipDetails debugInfo = new SipDetails.Builder(SipDetails.METHOD_REGISTER)
                .setCSeq(1)
                .setSipResponseCode(200, "OK")
                .setSipResponseReasonHeader(10, "CallBusy")
                .setCallId("callId")
                .build();

        ArraySet<String> featureTags = new ArraySet<>();
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.msg\"");
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"");
        featureTags.add("+g.gsma.callcomposer");
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).setFeatureTags(featureTags)
                .setSipDetails(debugInfo)
                .build();

        Parcel parcel = Parcel.obtain();
        attr.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ImsRegistrationAttributes unparcelledAttr =
                ImsRegistrationAttributes.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(attr.getRegistrationTechnology(), unparcelledAttr.getRegistrationTechnology());
        assertEquals(attr.getTransportType(), unparcelledAttr.getTransportType());
        assertEquals(attr.getAttributeFlags(), unparcelledAttr.getAttributeFlags());
        assertEquals(attr.getFeatureTags(), unparcelledAttr.getFeatureTags());

        SipDetails unparcelledDetails = unparcelledAttr.getSipDetails();

        assertEquals(debugInfo.getMethod(), unparcelledDetails.getMethod());
        assertEquals(debugInfo.getCSeq(), unparcelledDetails.getCSeq());
        assertEquals(debugInfo.getResponseCode(), unparcelledDetails.getResponseCode());
        assertEquals(debugInfo.getResponsePhrase(), unparcelledDetails.getResponsePhrase());
        assertEquals(debugInfo.getReasonHeaderCause(), unparcelledDetails.getReasonHeaderCause());
        assertEquals(debugInfo.getReasonHeaderText(), unparcelledDetails.getReasonHeaderText());
        assertEquals(debugInfo.getCallId(), unparcelledDetails.getCallId());
    }
}
