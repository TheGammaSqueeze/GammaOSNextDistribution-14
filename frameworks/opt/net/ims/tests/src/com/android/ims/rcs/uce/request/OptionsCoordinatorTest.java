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

import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_COMMAND_ERROR;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_ERROR;
import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_NETWORK_RESPONSE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IRcsUceControllerCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.request.UceRequestCoordinator.RequestResult;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.UceStatsWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class OptionsCoordinatorTest extends ImsTestBase {

    @Mock OptionsRequest mRequest;
    @Mock CapabilityRequestResponse mResponse;
    @Mock RequestManagerCallback mRequestMgrCallback;
    @Mock IRcsUceControllerCallback mUceCallback;
    @Mock UceStatsWriter mUceStatsWriter;

    private int mSubId = 1;
    private long mTaskId = 1L;
    private Uri mContact = Uri.fromParts("sip", "test1", null);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mTaskId).when(mRequest).getTaskId();
        doReturn(mResponse).when(mRequest).getRequestResponse();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRequestUpdatedWithError() throws Exception {
        OptionsRequestCoordinator coordinator = getOptionsCoordinator();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_ERROR);

        verify(mRequest).onFinish();

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
        verify(mRequestMgrCallback).notifyUceRequestFinished(anyLong(), eq(mTaskId));
    }

    @Test
    @SmallTest
    public void testRequestCommandError() throws Exception {
        doReturn(Optional.of(3)).when(mResponse).getCommandError();
        OptionsRequestCoordinator coordinator = getOptionsCoordinator();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_COMMAND_ERROR);

        verify(mRequest).onFinish();
        verify(mUceStatsWriter).setUceEvent(eq(mSubId), eq(UceStatsWriter.OUTGOING_OPTION_EVENT),
            eq(false), eq(3), eq(0));

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
        verify(mRequestMgrCallback).notifyUceRequestFinished(anyLong(), eq(mTaskId));
    }

    @Test
    @SmallTest
    public void testRequestNetworkResponse() throws Exception {
        OptionsRequestCoordinator coordinator = getOptionsCoordinator();
        doReturn(true).when(mResponse).isNetworkResponseOK();
        doReturn(Optional.of(200)).when(mResponse).getNetworkRespSipCode();

        final List<RcsContactUceCapability> updatedCapList = new ArrayList<>();
        RcsContactUceCapability updatedCapability = getContactUceCapability();
        updatedCapList.add(updatedCapability);
        doReturn(updatedCapList).when(mResponse).getUpdatedContactCapability();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_NETWORK_RESPONSE);

        verify(mRequestMgrCallback).saveCapabilities(updatedCapList);
        verify(mUceCallback).onCapabilitiesReceived(updatedCapList);
        verify(mResponse).removeUpdatedCapabilities(updatedCapList);

        verify(mRequest).onFinish();
        verify(mUceStatsWriter).setUceEvent(eq(mSubId), eq(UceStatsWriter.OUTGOING_OPTION_EVENT),
            eq(true), eq(0), eq(200));

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
        verify(mRequestMgrCallback).notifyUceRequestFinished(anyLong(), eq(mTaskId));
    }

    private OptionsRequestCoordinator getOptionsCoordinator() {
        OptionsRequestCoordinator.Builder builder = new OptionsRequestCoordinator.Builder(
                mSubId, Collections.singletonList(mRequest), mRequestMgrCallback, mUceStatsWriter);
        builder.setCapabilitiesCallback(mUceCallback);
        OptionsRequestCoordinator coordinator = builder.build();
        return coordinator;
    }

    private RcsContactUceCapability getContactUceCapability() {
        int requestResult = RcsContactUceCapability.REQUEST_RESULT_FOUND;
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        mContact, RcsContactUceCapability.SOURCE_TYPE_NETWORK, requestResult);
        return builder.build();
    }
}
