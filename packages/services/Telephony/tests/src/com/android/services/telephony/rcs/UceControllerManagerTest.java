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

package com.android.services.telephony.rcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.ImsException;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;

import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.TestExecutorService;
import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

@RunWith(AndroidJUnit4.class)
public class UceControllerManagerTest extends TelephonyTestBase {

    @Mock private UceController mUceController;
    @Mock private RcsFeatureManager mRcsFeatureManager;

    private final ExecutorService mExecutorService = new TestExecutorService();

    private int mSlotId = 1;
    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mSubId).when(mUceController).getSubId();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testRcsConnected() throws Exception {
        UceControllerManager controllerManager = getUceControllerManager();

        controllerManager.onRcsConnected(mRcsFeatureManager);

        verify(mUceController).onRcsConnected(mRcsFeatureManager);
    }

    @Test
    public void testRcsDisconnected() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();

        uceCtrlManager.onRcsDisconnected();

        verify(mUceController).onRcsDisconnected();
    }

    @Test
    public void testDestroy() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();

        uceCtrlManager.onDestroy();

        verify(mUceController).onDestroy();
    }

    @Test
    public void testSubIdAndCarrierConfigUpdate() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();

        // Updates with the same subId should not destroy the UceController
        uceCtrlManager.onCarrierConfigChanged();
        verify(mUceController, never()).onDestroy();

        // Updates with different subIds should trigger the creation of a new controller.
        uceCtrlManager.onAssociatedSubscriptionUpdated(mSubId + 1);
        verify(mUceController).onDestroy();
    }

    @Test
    public void testSubIdAndCarrierConfigUpdateWithInvalidSubId() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();

        // Updates with the same subId should not destroy the UceController
        uceCtrlManager.onCarrierConfigChanged();
        verify(mUceController, never()).onDestroy();

        // Updates with invalid subscription ID
        uceCtrlManager.onAssociatedSubscriptionUpdated(-1);

        verify(mUceController).onDestroy();
        assertNull(uceCtrlManager.getUceController());
    }

    @Test
    public void testRequestCapabilitiesWithRcsUnavailable() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(true).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsDisconnected();

        try {
            List<Uri> contacts = Arrays.asList(Uri.fromParts("sip", "00000", null));
            IRcsUceControllerCallback callback = Mockito.mock(IRcsUceControllerCallback.class);

            uceCtrlManager.requestCapabilities(contacts, callback);

            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
            return;
        }
        fail();
    }

    @Test
    public void testRequestCapabilitiesWithRcsConnected() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(false).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsConnected(mRcsFeatureManager);

        try {
            List<Uri> contacts = Arrays.asList(Uri.fromParts("sip", "00000", null));
            IRcsUceControllerCallback callback = Mockito.mock(IRcsUceControllerCallback.class);

            uceCtrlManager.requestCapabilities(contacts, callback);

            verify(mUceController).requestCapabilities(contacts, callback);
        } catch (ImsException e) {
            fail();
        }
    }

    @Test
    public void testRequestNetworkAvailability() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(false).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsConnected(mRcsFeatureManager);

        Uri contact = Uri.fromParts("sip", "00000", null);
        IRcsUceControllerCallback callback = Mockito.mock(IRcsUceControllerCallback.class);

        uceCtrlManager.requestNetworkAvailability(contact, callback);

        verify(mUceController).requestAvailability(contact, callback);
    }

    @Test
    public void testRequestNetworkAvailabilityWithRcsUnavailable() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(true).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsDisconnected();

        try {
            Uri contact = Uri.fromParts("sip", "00000", null);
            IRcsUceControllerCallback callback = Mockito.mock(IRcsUceControllerCallback.class);
            uceCtrlManager.requestNetworkAvailability(contact, callback);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
            return;
        }
        fail();
    }

    @Test
    public void testGetPublishState() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(false).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsConnected(mRcsFeatureManager);

        uceCtrlManager.getUcePublishState(true);

        verify(mUceController).getUcePublishState(eq(true));
    }

    @Test
    public void testGetPublishStateWithRcsUnavailable() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(true).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsDisconnected();

        try {
            uceCtrlManager.getUcePublishState(true);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
            return;
        }
        fail();
    }

    @Test
    public void testRegisterPublishStateCallback() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        IRcsUcePublishStateCallback callback = Mockito.mock(IRcsUcePublishStateCallback.class);

        uceCtrlManager.registerPublishStateCallback(callback, true);

        verify(mUceController).registerPublishStateCallback(callback, true);
    }

    @Test
    public void testRegisterPublishStateCallbackWithRcsUnavailable() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        doReturn(true).when(mUceController).isUnavailable();
        uceCtrlManager.onRcsDisconnected();

        try {
            IRcsUcePublishStateCallback callback = Mockito.mock(IRcsUcePublishStateCallback.class);
            uceCtrlManager.registerPublishStateCallback(callback, true);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
            return;
        }
        fail();
    }

    @Test
    public void testUnregisterPublishStateCallback() throws Exception {
        UceControllerManager uceCtrlManager = getUceControllerManager();
        IRcsUcePublishStateCallback callback = Mockito.mock(IRcsUcePublishStateCallback.class);

        uceCtrlManager.unregisterPublishStateCallback(callback);

        verify(mUceController).unregisterPublishStateCallback(callback);
    }

    private UceControllerManager getUceControllerManager() {
        UceControllerManager manager = new UceControllerManager(mContext, mSlotId,
                mExecutorService, mUceController);
        return manager;
    }
}
