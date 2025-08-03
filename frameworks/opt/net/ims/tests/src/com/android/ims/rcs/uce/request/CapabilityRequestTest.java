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

import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_CACHED;

import static com.android.ims.rcs.uce.eab.EabCapabilityResult.EAB_QUERY_SUCCESSFUL;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.UceDeviceState.DeviceStateResult;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class CapabilityRequestTest extends ImsTestBase {

    @Mock CapabilityRequestResponse mRequestResponse;
    @Mock UceRequestManager.RequestManagerCallback mReqMgrCallback;
    @Mock DeviceStateResult mDeviceStateResult;

    private final int mSubId = 1;
    private final long mCoordId = 1L;
    private final Uri contact1 = Uri.fromParts("sip", "test1", null);
    private final Uri contact2 = Uri.fromParts("sip", "test2", null);

    private boolean mRequestExecuted;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mDeviceStateResult).when(mReqMgrCallback).getDeviceState();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testCachedCapabilityCallback() throws Exception {
        CapabilityRequest request = getCapabilityRequest();

        // Assume that all the requested capabilities can be retrieved from the cache.
        PresenceBuilder builder1 = new PresenceBuilder(contact1, SOURCE_TYPE_CACHED,
                REQUEST_RESULT_FOUND);
        PresenceBuilder builder2 = new PresenceBuilder(contact2, SOURCE_TYPE_CACHED,
                REQUEST_RESULT_FOUND);

        EabCapabilityResult eabResult1 = new EabCapabilityResult(contact1, EAB_QUERY_SUCCESSFUL,
                builder1.build());
        EabCapabilityResult eabResult2 = new EabCapabilityResult(contact2, EAB_QUERY_SUCCESSFUL,
                builder2.build());

        List<EabCapabilityResult> eabResultList = new ArrayList<>();
        eabResultList.add(eabResult1);
        eabResultList.add(eabResult2);

        doReturn(false).when(mDeviceStateResult).isRequestForbidden();
        doReturn(eabResultList).when(mReqMgrCallback)
                .getCapabilitiesFromCacheIncludingExpired(any());

        // Execute the request.
        request.executeRequest();

        // Verify that it will notify the cached capabilities is updated.
        verify(mReqMgrCallback).notifyCachedCapabilitiesUpdated(eq(mCoordId), anyLong());

        // Verify that it does not need to request capabilities from network.
        verify(mReqMgrCallback).notifyNoNeedRequestFromNetwork(eq(mCoordId), anyLong());
        assertFalse(mRequestExecuted);
    }

    @Test
    @SmallTest
    public void testCachedCapabilityCallbackWithSkipGettingFromCache() throws Exception {
        CapabilityRequest request = getCapabilityRequest();

        // Assume that all the requested capabilities can be retrieved from the cache.
        PresenceBuilder builder1 = new PresenceBuilder(contact1, SOURCE_TYPE_CACHED,
                REQUEST_RESULT_FOUND);
        PresenceBuilder builder2 = new PresenceBuilder(contact2, SOURCE_TYPE_CACHED,
                REQUEST_RESULT_FOUND);

        EabCapabilityResult eabResult1 = new EabCapabilityResult(contact1, EAB_QUERY_SUCCESSFUL,
                builder1.build());
        EabCapabilityResult eabResult2 = new EabCapabilityResult(contact2, EAB_QUERY_SUCCESSFUL,
                builder2.build());

        List<EabCapabilityResult> eabResultList = new ArrayList<>();
        eabResultList.add(eabResult1);
        eabResultList.add(eabResult2);

        doReturn(false).when(mDeviceStateResult).isRequestForbidden();
        doReturn(eabResultList).when(mReqMgrCallback).getCapabilitiesFromCache(any());

        // Assume that skip getting capabilities from the cache.
        request.setSkipGettingFromCache(true);

        // Execute the request.
        request.executeRequest();

        // Verify that it will not notify the cached capabilities.
        verify(mReqMgrCallback, never()).notifyCachedCapabilitiesUpdated(eq(mCoordId), anyLong());

        // Verify that it will request capabilities from network.
        verify(mReqMgrCallback, never()).notifyNoNeedRequestFromNetwork(eq(mCoordId), anyLong());
        assertTrue(mRequestExecuted);
    }

    @Test
    @SmallTest
    public void testRequestCapabilities() throws Exception {
        CapabilityRequest request = getCapabilityRequest();

        // Assume that only one requested capabilities can be retrieved from the cache.
        PresenceBuilder builder = new PresenceBuilder(contact1, SOURCE_TYPE_CACHED,
                REQUEST_RESULT_FOUND);

        EabCapabilityResult eabResult = new EabCapabilityResult(contact1, EAB_QUERY_SUCCESSFUL,
                builder.build());

        List<EabCapabilityResult> eabResultList = new ArrayList<>();
        eabResultList.add(eabResult);

        doReturn(false).when(mDeviceStateResult).isRequestForbidden();
        doReturn(eabResultList).when(mReqMgrCallback)
                .getCapabilitiesFromCacheIncludingExpired(any());
        // This API should only be applied to PUBLISH.
        // Even if the return value is true, the capabilities request must be processed.
        doReturn(true).when(mDeviceStateResult).isPublishRequestBlocked();

        // Execute the request.
        request.executeRequest();

        // Verify that it will notify the cached capabilities is updated.
        verify(mReqMgrCallback).notifyCachedCapabilitiesUpdated(eq(mCoordId), anyLong());

        // Verify that it will request capability from the network.
        verify(mReqMgrCallback, never()).notifyNoNeedRequestFromNetwork(eq(mCoordId), anyLong());
        assertTrue(mRequestExecuted);
    }

    private CapabilityRequest getCapabilityRequest() {
        CapabilityRequest request = new CapabilityRequest(mSubId,
                UceRequest.REQUEST_TYPE_CAPABILITY, mReqMgrCallback, mRequestResponse) {
            @Override
            protected void requestCapabilities(List<Uri> requestCapUris) {
                mRequestExecuted = true;
            }
        };
        // Set the request coordinator ID
        request.setRequestCoordinatorId(mCoordId);

        // Set two contacts
        List<Uri> uriList = new ArrayList<>();
        uriList.add(contact1);
        uriList.add(contact2);
        request.setContactUri(uriList);
        return request;
    }
}
