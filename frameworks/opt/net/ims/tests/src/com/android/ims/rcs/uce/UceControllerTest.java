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

package com.android.ims.rcs.uce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IOptionsRequestCallback;
import android.telephony.ims.aidl.IRcsUceControllerCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.eab.EabController;
import com.android.ims.rcs.uce.options.OptionsController;
import com.android.ims.rcs.uce.presence.publish.PublishController;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.request.UceRequestManager;
import com.android.ims.rcs.uce.UceDeviceState.DeviceStateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class UceControllerTest extends ImsTestBase {

    @Mock EabController mEabController;
    @Mock PublishController mPublishController;
    @Mock SubscribeController mSubscribeController;
    @Mock OptionsController mOptionsController;
    @Mock UceController.ControllerFactory mControllerFactory;

    @Mock UceRequestManager mTaskManager;
    @Mock UceController.RequestManagerFactory mTaskManagerFactory;

    @Mock UceDeviceState mDeviceState;
    @Mock DeviceStateResult mDeviceStateResult;
    @Mock RcsFeatureManager mFeatureManager;
    @Mock UceController.UceControllerCallback mCallback;
    @Mock IRcsUceControllerCallback mCapabilitiesCallback;
    @Mock IOptionsRequestCallback mOptionsRequestCallback;

    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mEabController).when(mControllerFactory).createEabController(any(), eq(mSubId),
                any(), any());
        doReturn(mPublishController).when(mControllerFactory).createPublishController(any(),
                eq(mSubId), any(), any());
        doReturn(mSubscribeController).when(mControllerFactory).createSubscribeController(any(),
                eq(mSubId));
        doReturn(mOptionsController).when(mControllerFactory).createOptionsController(any(),
                eq(mSubId));
        doReturn(mTaskManager).when(mTaskManagerFactory).createRequestManager(any(), eq(mSubId),
                any(), any());
        doReturn(mDeviceStateResult).when(mDeviceState).getCurrentState();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRcsConnected() throws Exception {
        UceController uceController = createUceController();

        uceController.onRcsConnected(mFeatureManager);

        verify(mEabController).onRcsConnected(mFeatureManager);
        verify(mPublishController).onRcsConnected(mFeatureManager);
        verify(mSubscribeController).onRcsConnected(mFeatureManager);
        verify(mOptionsController).onRcsConnected(mFeatureManager);
        verify(mFeatureManager).addCapabilityEventCallback(any());
    }

    @Test
    @SmallTest
    public void testRcsDisconnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);

        uceController.onRcsDisconnected();

        verify(mFeatureManager).removeCapabilityEventCallback(any());
        verify(mEabController).onRcsDisconnected();
        verify(mPublishController).onRcsDisconnected();
        verify(mSubscribeController).onRcsDisconnected();
        verify(mOptionsController).onRcsDisconnected();
    }

    @Test
    @SmallTest
    public void testOnDestroyed() throws Exception {
        UceController uceController = createUceController();

        uceController.onDestroy();

        verify(mTaskManager).onDestroy();
        verify(mEabController).onDestroy();
        verify(mPublishController).onDestroy();
        verify(mSubscribeController).onDestroy();
        verify(mOptionsController).onDestroy();
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWithRcsDisconnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsDisconnected();

        List<Uri> uriList = new ArrayList<>();
        uceController.requestCapabilities(uriList, mCapabilitiesCallback);

        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_GENERIC_FAILURE, 0L, null);
        verify(mTaskManager, never()).sendCapabilityRequest(any(), eq(false), any());
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWithForbidden() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);
        doReturn(true).when(mDeviceStateResult).isRequestForbidden();
        doReturn(Optional.of(RcsUceAdapter.ERROR_FORBIDDEN)).when(mDeviceStateResult)
                .getErrorCode();

        List<Uri> uriList = new ArrayList<>();
        uriList.add(Uri.fromParts("sip", "test", null));
        uceController.requestCapabilities(uriList, mCapabilitiesCallback);

        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_FORBIDDEN, 0L, null);
        verify(mTaskManager, never()).sendCapabilityRequest(any(), eq(false), any());
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWithRcsConnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);
        doReturn(false).when(mDeviceStateResult).isRequestForbidden();
        // This API should only be applied to PUBLISH.
        // Even if the return value is true, the capabilities request must be processed.
        doReturn(true).when(mDeviceStateResult).isPublishRequestBlocked();

        List<Uri> uriList = new ArrayList<>();
        uriList.add(Uri.fromParts("sip", "test", null));
        uceController.requestCapabilities(uriList, mCapabilitiesCallback);

        verify(mTaskManager).sendCapabilityRequest(uriList, false, mCapabilitiesCallback);
    }

    @Test
    @SmallTest
    public void testRequestAvailabilityWithRcsDisconnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsDisconnected();

        Uri contact = Uri.fromParts("sip", "test", null);
        uceController.requestAvailability(contact, mCapabilitiesCallback);

        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_GENERIC_FAILURE, 0L, null);
        verify(mTaskManager, never()).sendAvailabilityRequest(any(), any());
    }

    @Test
    @SmallTest
    public void testRequestAvailabilityWithForbidden() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);
        doReturn(true).when(mDeviceStateResult).isRequestForbidden();
        doReturn(Optional.of(RcsUceAdapter.ERROR_FORBIDDEN)).when(mDeviceStateResult)
                .getErrorCode();

        Uri contact = Uri.fromParts("sip", "test", null);
        uceController.requestAvailability(contact, mCapabilitiesCallback);

        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_FORBIDDEN, 0L, null);
        verify(mTaskManager, never()).sendCapabilityRequest(any(), eq(false), any());
    }

    @Test
    @SmallTest
    public void testRequestAvailabilityWithRcsConnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);
        doReturn(false).when(mDeviceStateResult).isRequestForbidden();

        Uri contact = Uri.fromParts("sip", "test", null);
        uceController.requestAvailability(contact, mCapabilitiesCallback);

        verify(mTaskManager).sendAvailabilityRequest(contact, mCapabilitiesCallback);
    }

    @Test
    @SmallTest
    public void TestRequestPublishCapabilitiesFromService() throws Exception {
        UceController uceController = createUceController();

        int triggerType = RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN;
        uceController.onRequestPublishCapabilitiesFromService(triggerType);

        verify(mPublishController).requestPublishCapabilitiesFromService(triggerType);
    }

    @Test
    @SmallTest
    public void testUnpublish() throws Exception {
        UceController uceController = createUceController();

        uceController.onUnpublish();

        verify(mPublishController).onUnpublish();
    }

    @Test
    @SmallTest
    public void testRegisterPublishStateCallback() {
        UceController uceController = createUceController();

        uceController.registerPublishStateCallback(any(), anyBoolean());

        verify(mPublishController).registerPublishStateCallback(any(), anyBoolean());
    }

    @Test
    @SmallTest
    public void unregisterPublishStateCallback() {
        UceController uceController = createUceController();

        uceController.unregisterPublishStateCallback(any());

        verify(mPublishController).unregisterPublishStateCallback(any());
    }

    @Test
    @SmallTest
    public void testGetUcePublishState() {
        UceController uceController = createUceController();

        uceController.getUcePublishState(anyBoolean());

        verify(mPublishController).getUcePublishState(anyBoolean());
    }

    private UceController createUceController() {
        UceController uceController = new UceController(mContext, mSubId, mDeviceState,
                mControllerFactory, mTaskManagerFactory);
        uceController.setUceControllerCallback(mCallback);
        return uceController;
    }
}
