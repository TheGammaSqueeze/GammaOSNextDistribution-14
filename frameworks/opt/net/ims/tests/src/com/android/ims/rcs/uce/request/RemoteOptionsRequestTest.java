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

import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_CACHED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.request.RemoteOptionsRequest.RemoteOptResponse;
import com.android.ims.rcs.uce.util.NetworkSipCode;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class RemoteOptionsRequestTest extends ImsTestBase {

    private static final String FEATURE_TAG_CHAT =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String FEATURE_TAG_FILE_TRANSFER =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";

    private int mSubId = 1;
    private long mCoordId = 1L;
    private Uri mTestContact;
    private RcsContactUceCapability mDeviceCapability;

    @Mock RequestManagerCallback mRequestManagerCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTestContact = Uri.fromParts("sip", "test", null);

        List<String> featureTags = new ArrayList<>();
        featureTags.add(FEATURE_TAG_CHAT);
        featureTags.add(FEATURE_TAG_FILE_TRANSFER);

        OptionsBuilder builder = new OptionsBuilder(mTestContact, SOURCE_TYPE_CACHED);
        builder.addFeatureTag(FEATURE_TAG_CHAT);
        builder.addFeatureTag(FEATURE_TAG_FILE_TRANSFER);
        mDeviceCapability = builder.build();

        doReturn(mDeviceCapability).when(mRequestManagerCallback).getDeviceCapabilities(anyInt());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRequestCapabilities() throws Exception {
        RemoteOptionsRequest request = getRequest();
        List<String> featureTags = Arrays.asList(FEATURE_TAG_CHAT, FEATURE_TAG_FILE_TRANSFER);
        request.setRemoteFeatureTags(featureTags);
        request.setIsRemoteNumberBlocked(false);

        request.executeRequest();

        verify(mRequestManagerCallback).saveCapabilities(any());

        RemoteOptResponse response = request.getRemoteOptResponse();
        assertEquals(mDeviceCapability, response.getRcsContactCapability());
        assertFalse(response.isNumberBlocked());

        verify(mRequestManagerCallback).notifyRemoteRequestDone(eq(mCoordId), anyLong());
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWhenBlocked() throws Exception {
        RemoteOptionsRequest request = getRequest();
        List<String> featureTags = Arrays.asList(FEATURE_TAG_CHAT, FEATURE_TAG_FILE_TRANSFER);
        request.setRemoteFeatureTags(featureTags);
        request.setIsRemoteNumberBlocked(true);

        request.executeRequest();

        verify(mRequestManagerCallback).saveCapabilities(any());

        RemoteOptResponse response = request.getRemoteOptResponse();
        assertEquals(mDeviceCapability, response.getRcsContactCapability());
        assertTrue(response.isNumberBlocked());

        verify(mRequestManagerCallback).notifyRemoteRequestDone(eq(mCoordId), anyLong());
    }

    @Test
    @SmallTest
    public void testsendCapabilitiesRequestWhenDestroy() throws Exception {
        RemoteOptionsRequest request = getRequest();
        request.onFinish();

        request.executeRequest();

        RemoteOptResponse response = request.getRemoteOptResponse();
        int errorSipCode = response.getErrorSipCode().orElse(-1);
        String reason = response.getErrorReason().orElse("");
        assertEquals(NetworkSipCode.SIP_CODE_SERVICE_UNAVAILABLE, errorSipCode);
        assertEquals(NetworkSipCode.SIP_SERVICE_UNAVAILABLE, reason);

        verify(mRequestManagerCallback).notifyRemoteRequestDone(eq(mCoordId), anyLong());
        verify(mRequestManagerCallback, never()).saveCapabilities(any());
    }

    private RemoteOptionsRequest getRequest() {
        RemoteOptionsRequest request = new RemoteOptionsRequest(mSubId, mRequestManagerCallback);
        request.setRequestCoordinatorId(mCoordId);
        request.setContactUri(Collections.singletonList(mTestContact));
        return request;
    }
}
