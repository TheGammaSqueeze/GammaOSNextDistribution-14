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

package com.android.ims.rcs.uce.request;

import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.SipDetails;
import android.telephony.ims.aidl.ISubscribeResponseCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SubscribeRequestTest extends ImsTestBase {

    @Mock SubscribeController mSubscribeController;
    @Mock CapabilityRequestResponse mRequestResponse;
    @Mock RequestManagerCallback mRequestManagerCallback;

    private int mSubId = 1;
    private long mCoordId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRequestCapabilities() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();

        List<Uri> uriList = new ArrayList<>();
        subscribeRequest.requestCapabilities(uriList);

        verify(mSubscribeController).requestCapabilities(eq(uriList), any());
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWhenDestroy() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        subscribeRequest.onFinish();

        List<Uri> uriList = new ArrayList<>();
        subscribeRequest.requestCapabilities(uriList);

        verify(mRequestResponse).setRequestInternalError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mRequestResponse, never()).setSipDetails(any());
        verify(mRequestManagerCallback).notifyRequestError(eq(mCoordId), anyLong());
        verify(mSubscribeController, never()).requestCapabilities(any(), any());
    }

    @Test
    @SmallTest
    public void testCommandErrorCallback() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        callback.onCommandError(COMMAND_CODE_NOT_SUPPORTED);

        verify(mRequestResponse).setCommandError(COMMAND_CODE_NOT_SUPPORTED);
        verify(mRequestResponse, never()).setSipDetails(any(SipDetails.class));
        verify(mRequestManagerCallback).notifyCommandError(eq(mCoordId), anyLong());
    }

    @Test
    @SmallTest
    public void testNetworkResponse() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();

        int sipCode = 200;
        String reason = "OK";
        SipDetails details = new SipDetails.Builder(SipDetails.METHOD_SUBSCRIBE).setCSeq(1)
                .setSipResponseCode(sipCode, reason).setCallId("callId").build();

        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();
        callback.onNetworkResponse(details);

        verify(mRequestResponse).setSipDetails(eq(details));
        verify(mRequestResponse, never()).setNetworkResponseCode(anyInt(), anyString());
        verify(mRequestManagerCallback).notifyNetworkResponse(eq(mCoordId), anyLong());
    }

    @Test
    @SmallTest
    public void testResourceTerminated() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        Uri contact = Uri.fromParts("sip", "test", null);
        List<RcsContactTerminatedReason> list = new ArrayList<>();
        list.add(new RcsContactTerminatedReason(contact, "terminated"));
        callback.onResourceTerminated(list);

        verify(mRequestResponse).addTerminatedResource(list);
        verify(mRequestResponse, never()).setSipDetails(any());
        verify(mRequestManagerCallback).notifyResourceTerminated(eq(mCoordId), anyLong());
    }

    @Test
    @SmallTest
    public void testCapabilitiesUpdate() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        List<String> pidfXml = new ArrayList<>();
        pidfXml.add(getPidfData());
        callback.onNotifyCapabilitiesUpdate(pidfXml);

        verify(mRequestResponse).addUpdatedCapabilities(any());
        verify(mRequestResponse, never()).setSipDetails(any());
        verify(mRequestManagerCallback).notifyCapabilitiesUpdated(eq(mCoordId), anyLong());
    }

    @Test
    @SmallTest
    public void testTerminatedCallback() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        doReturn(true).when(mRequestResponse).isNetworkResponseOK();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        String reason = "forbidden";
        long retryAfterMillis = 10000L;
        callback.onTerminated(reason, retryAfterMillis);

        verify(mRequestResponse).setTerminated(reason, retryAfterMillis);
        verify(mRequestResponse, never()).setSipDetails(any());
        verify(mRequestManagerCallback).notifyTerminated(eq(mCoordId), anyLong());
    }

    private SubscribeRequest getSubscribeRequest() {
        SubscribeRequest request = new SubscribeRequest(mSubId, UceRequest.REQUEST_TYPE_CAPABILITY,
                mRequestManagerCallback, mSubscribeController, mRequestResponse);
        request.setRequestCoordinatorId(mCoordId);
        return request;
    }

    private String getPidfData() {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"sip:test\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                // tuple 1
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                // support audio
                .append("<caps:servcaps>")
                .append("<caps:audio>true</caps:audio>")
                // support video
                .append("<caps:video>true</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>sip:test</contact>")
                .append("</tuple>");

        return pidfBuilder.toString();
    }
}
