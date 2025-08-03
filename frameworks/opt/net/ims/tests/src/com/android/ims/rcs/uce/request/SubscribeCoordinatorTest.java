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

package com.android.ims.rcs.uce.request;

import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_CACHED_CAPABILITY_UPDATE;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_CAPABILITY_UPDATE;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_COMMAND_ERROR;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_ERROR;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_NETWORK_RESPONSE;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_NO_NEED_REQUEST_FROM_NETWORK;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_RESOURCE_TERMINATED;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_TERMINATED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.SipDetails;
import android.telephony.ims.aidl.IRcsUceControllerCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.UceDeviceState.DeviceStateResult;
import com.android.ims.rcs.uce.UceStatsWriter;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.request.UceRequestCoordinator.RequestResult;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class SubscribeCoordinatorTest extends ImsTestBase {

    @Mock SubscribeRequest mRequest;
    @Mock CapabilityRequestResponse mResponse;
    @Mock RequestManagerCallback mRequestMgrCallback;
    @Mock IRcsUceControllerCallback mUceCallback;
    @Mock DeviceStateResult mDeviceStateResult;
    @Mock UceStatsWriter mUceStatsWriter;

    private int mSubId = 1;
    private long mTaskId = 1L;
    private Uri mContact = Uri.fromParts("sip", "test1", null);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mTaskId).when(mRequest).getTaskId();
        doReturn(mResponse).when(mRequest).getRequestResponse();
        doReturn(Optional.empty()).when(mResponse).getReasonHeaderCause();
        doReturn(mDeviceStateResult).when(mRequestMgrCallback).getDeviceState();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRequestUpdatedWithError() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_ERROR);

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
        verify(mRequest).onFinish();
    }

    @Test
    @SmallTest
    public void testRequestCommandError() throws Exception {
        doReturn(Optional.of(3)).when(mResponse).getCommandError();
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_COMMAND_ERROR);

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());

        verify(mUceStatsWriter).setUceEvent(eq(mSubId), eq(UceStatsWriter.SUBSCRIBE_EVENT),
            eq(false), eq(3), eq(0));
        verify(mRequest).onFinish();
    }

    @Test
    @SmallTest
    public void testRequestNetworkRespSuccess() throws Exception {
        int responseCode = 200;
        String responsePhrase = "OK";
        int reasonHdrCause = 1;
        String reasonHdrText = "reasonText";
        int cSeq = 10;
        String callId = "TestCallId";
        SipDetails details = new SipDetails.Builder(SipDetails.METHOD_SUBSCRIBE).setCSeq(cSeq)
                .setSipResponseCode(responseCode, responsePhrase).setCallId(callId)
                .setSipResponseReasonHeader(reasonHdrCause, reasonHdrText).build();
        doReturn(Optional.ofNullable(details)).when(mResponse).getSipDetails();

        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();
        doReturn(true).when(mResponse).isNetworkResponseOK();
        doReturn(Optional.of(200)).when(mResponse).getNetworkRespSipCode();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_NETWORK_RESPONSE);

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertEquals(1, requestList.size());
        assertTrue(resultList.isEmpty());

        Iterator<RequestResult> requestResults = resultList.iterator();
        while (requestResults.hasNext()) {
            RequestResult req = requestResults.next();
            SipDetails receivedInfo = req.getSipDetails().orElse(null);
            assertNotNull(receivedInfo);
            assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
            assertEquals(cSeq, receivedInfo.getCSeq());
            assertEquals(responseCode, receivedInfo.getResponseCode());
            assertEquals(responsePhrase, receivedInfo.getResponsePhrase());
            assertEquals(reasonHdrCause, receivedInfo.getReasonHeaderCause());
            assertEquals(reasonHdrText, receivedInfo.getReasonHeaderText());
            assertEquals(callId, receivedInfo.getCallId());
        }

        verify(mUceStatsWriter).setSubscribeResponse(eq(mSubId), eq(mTaskId), eq(200));
        verify(mRequest, never()).onFinish();
    }

    @Test
    @SmallTest
    public void testRequestNetworkRespFailure() throws Exception {
        doReturn(Optional.of(400)).when(mResponse).getNetworkRespSipCode();

        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        List<EabCapabilityResult> eabResultList = new ArrayList<>();

        Uri contactUri = Uri.fromParts("tel", "123456789", null);
        EabCapabilityResult result = new EabCapabilityResult(contactUri,
                EabCapabilityResult.EAB_CONTACT_NOT_FOUND_FAILURE, null);
        eabResultList.add(result);

        doReturn(eabResultList).when(mRequestMgrCallback).
                getCapabilitiesFromCacheIncludingExpired(any());

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_NETWORK_RESPONSE);

        verify(mUceStatsWriter).setSubscribeResponse(eq(mSubId), eq(mTaskId), eq(400));
        verify(mRequestMgrCallback, never()).saveCapabilities(any());
        verify(mRequest).onFinish();
    }

    @Test
    @SmallTest
    public void testRequestNetworkRespError() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();
        doReturn(false).when(mResponse).isNetworkResponseOK();
        doReturn(true).when(mResponse).isRequestForbidden();
        Optional<Integer> respSipCode = Optional.of(400);
        Optional<String> respReason = Optional.of("Bad Request");
        doReturn(respSipCode).when(mResponse).getResponseSipCode();
        doReturn(respReason).when(mResponse).getResponseReason();
        doReturn(false).when(mDeviceStateResult).isRequestForbidden();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_NETWORK_RESPONSE);

        verify(mRequestMgrCallback).refreshDeviceState(respSipCode.get(), respReason.get());
        verify(mRequest).onFinish();

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
        verify(mRequestMgrCallback).notifyUceRequestFinished(anyLong(), eq(mTaskId));
    }

    @Test
    @SmallTest
    public void testRequestCapabilityUpdated() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        final List<RcsContactUceCapability> updatedCapList = new ArrayList<>();
        RcsContactPresenceTuple tuple = getContactPresenceTuple();
        RcsContactUceCapability updatedCapability = getContactUceCapability(tuple);
        updatedCapList.add(updatedCapability);
        doReturn(updatedCapList).when(mResponse).getUpdatedContactCapability();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_CAPABILITY_UPDATE);

        verify(mRequestMgrCallback).saveCapabilities(updatedCapList);
        verify(mUceCallback).onCapabilitiesReceived(updatedCapList);
        verify(mResponse).removeUpdatedCapabilities(updatedCapList);

        verify(mUceStatsWriter).setPresenceNotifyEvent(eq(mSubId), eq(mTaskId), any());
    }

    @Test
    @SmallTest
    public void testResourceTerminated() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        final List<RcsContactUceCapability> updatedCapList = new ArrayList<>();
        RcsContactUceCapability updatedCapability = getContactUceCapability();
        updatedCapList.add(updatedCapability);
        doReturn(updatedCapList).when(mResponse).getTerminatedResources();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_RESOURCE_TERMINATED);

        verify(mRequestMgrCallback).saveCapabilities(updatedCapList);
        verify(mUceCallback).onCapabilitiesReceived(updatedCapList);
        verify(mResponse).removeTerminatedResources(updatedCapList);

        verify(mUceStatsWriter).setPresenceNotifyEvent(eq(mSubId), eq(mTaskId), any());
    }

    @Test
    @SmallTest
    public void testCachedCapabilityUpdated() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        final List<RcsContactUceCapability> updatedCapList = new ArrayList<>();
        RcsContactUceCapability updatedCapability = getContactUceCapability();
        updatedCapList.add(updatedCapability);
        doReturn(updatedCapList).when(mResponse).getCachedContactCapability();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_CACHED_CAPABILITY_UPDATE);

        verify(mUceCallback).onCapabilitiesReceived(updatedCapList);
        verify(mResponse).removeCachedContactCapabilities();
    }

    @Test
    @SmallTest
    public void testRequestTerminated() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        doReturn("noresource").when(mResponse).getTerminatedReason();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_TERMINATED);

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());

        verify(mUceStatsWriter).setSubscribeTerminated(eq(mSubId), eq(mTaskId), eq("noresource"));
    }

    @Test
    @SmallTest
    public void testNoNeedRequestFromNetwork() throws Exception {
        SubscribeRequestCoordinator coordinator = getSubscribeCoordinator();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_NO_NEED_REQUEST_FROM_NETWORK);

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
    }

    private SubscribeRequestCoordinator getSubscribeCoordinator() {
        SubscribeRequestCoordinator.Builder builder = new SubscribeRequestCoordinator.Builder(
                mSubId, Collections.singletonList(mRequest), mRequestMgrCallback, mUceStatsWriter);
        builder.setCapabilitiesCallback(mUceCallback);
        SubscribeRequestCoordinator subCoor = builder.build();
        return subCoor;
    }

    private RcsContactUceCapability getContactUceCapability(RcsContactPresenceTuple tuple) {
        int requestResult = RcsContactUceCapability.REQUEST_RESULT_FOUND;
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        mContact, RcsContactUceCapability.SOURCE_TYPE_NETWORK, requestResult);
        builder.addCapabilityTuple(tuple);
        return builder.build();
    }

    private RcsContactUceCapability getContactUceCapability() {
        int requestResult = RcsContactUceCapability.REQUEST_RESULT_FOUND;
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        mContact, RcsContactUceCapability.SOURCE_TYPE_NETWORK, requestResult);
        return builder.build();
    }

    private RcsContactPresenceTuple getContactPresenceTuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open", RcsContactPresenceTuple.SERVICE_ID_CHAT_V1,
                "1.0");
        return builder.build();

    }
}
