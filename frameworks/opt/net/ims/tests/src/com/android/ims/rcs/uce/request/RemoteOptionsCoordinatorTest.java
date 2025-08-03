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

import static com.android.ims.rcs.uce.request.UceRequestCoordinator.REQUEST_UPDATE_REMOTE_REQUEST_DONE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IOptionsRequestCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.request.RemoteOptionsRequest.RemoteOptResponse;
import com.android.ims.rcs.uce.request.UceRequestCoordinator.RequestResult;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.UceStatsWriter;

import java.util.Collection;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class RemoteOptionsCoordinatorTest extends ImsTestBase {

    @Mock RemoteOptionsRequest mRequest;
    @Mock RemoteOptResponse mResponse;
    @Mock RequestManagerCallback mRequestMgrCallback;
    @Mock IOptionsRequestCallback mOptRequestCallback;
    @Mock UceStatsWriter mUceStatsWriter;

    private int mSubId = 1;
    private long mTaskId = 1L;
    private Uri mContact = Uri.fromParts("sip", "test1", null);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mTaskId).when(mRequest).getTaskId();
        doReturn(mResponse).when(mRequest).getRemoteOptResponse();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRemoteRequestFinished() throws Exception {
        RemoteOptionsCoordinator coordinator = getRemoteOptCoordinator();
        RcsContactUceCapability updatedCapability = getContactUceCapability();
        doReturn(updatedCapability).when(mResponse).getRcsContactCapability();
        doReturn(true).when(mResponse).isNumberBlocked();

        coordinator.onRequestUpdated(mTaskId, REQUEST_UPDATE_REMOTE_REQUEST_DONE);

        verify(mOptRequestCallback).respondToCapabilityRequest(updatedCapability, true);

        verify(mRequest).onFinish();
        verify(mUceStatsWriter).setUceEvent(eq(mSubId), eq(UceStatsWriter.INCOMING_OPTION_EVENT),
            eq(true), eq(0), eq(200));

        Collection<UceRequest> requestList = coordinator.getActivatedRequest();
        Collection<RequestResult> resultList = coordinator.getFinishedRequest();
        assertTrue(requestList.isEmpty());
        assertEquals(1, resultList.size());
        verify(mRequestMgrCallback).notifyUceRequestFinished(anyLong(), eq(mTaskId));
    }

    private RemoteOptionsCoordinator getRemoteOptCoordinator() {
        RemoteOptionsCoordinator.Builder builder = new RemoteOptionsCoordinator.Builder(
                mSubId, Collections.singletonList(mRequest), mRequestMgrCallback, mUceStatsWriter);
        builder.setOptionsRequestCallback(mOptRequestCallback);
        return builder.build();
    }

    private RcsContactUceCapability getContactUceCapability() {
        int requestResult = RcsContactUceCapability.REQUEST_RESULT_FOUND;
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        mContact, RcsContactUceCapability.SOURCE_TYPE_NETWORK, requestResult);
        return builder.build();
    }
}
