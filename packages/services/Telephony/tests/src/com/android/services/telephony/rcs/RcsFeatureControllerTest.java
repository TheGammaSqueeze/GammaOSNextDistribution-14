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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.AccessNetworkConstants;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsRegistrationCallback;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;

import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.ims.FeatureConnector;
import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.imsphone.ImsRegistrationCallbackHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class RcsFeatureControllerTest extends TelephonyTestBase {

    private static final int TEST_SUB_ID = 1;

    private static final ImsReasonInfo REASON_DISCONNECTED = new ImsReasonInfo(
            ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN, 0, "test");

    @Mock RcsFeatureManager mFeatureManager;
    @Mock RcsFeatureController.FeatureConnectorFactory<RcsFeatureManager> mFeatureFactory;
    @Mock ImsRegistrationCallbackHelper.ImsRegistrationUpdate mRegistrationCallback;
    @Mock FeatureConnector<RcsFeatureManager> mFeatureConnector;
    @Mock RcsFeatureController.Feature mMockFeature;
    @Captor ArgumentCaptor<FeatureConnector.Listener<RcsFeatureManager>> mConnectorListener;

    private RcsFeatureController.RegistrationHelperFactory mRegistrationFactory =
            new RcsFeatureController.RegistrationHelperFactory() {
                @Override
                public ImsRegistrationCallbackHelper create(
                        ImsRegistrationCallbackHelper.ImsRegistrationUpdate cb, Executor executor) {
                    // Run on current thread for testing.
                    return new ImsRegistrationCallbackHelper(mRegistrationCallback, Runnable::run);
                }
            };

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testRcsFeatureManagerConnectDisconnect() throws Exception {
        RcsFeatureController controller = createFeatureController();
        controller.addFeature(mMockFeature, RcsFeatureController.Feature.class);
        verify(mMockFeature).onRcsDisconnected();
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);

        verify(mFeatureManager).updateCapabilities(TEST_SUB_ID);
        verify(mFeatureManager).registerImsRegistrationCallback(any());
        verify(mMockFeature).onRcsConnected(mFeatureManager);

        // Disconnect
        mConnectorListener.getValue().connectionUnavailable(
                FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);

        verify(mFeatureManager).unregisterImsRegistrationCallback(any());
        verify(mMockFeature, times(2)).onRcsDisconnected();
    }

    @Test
    public void testFeatureManagerConnectedAddRemoveFeature() throws Exception {
        RcsFeatureController controller = createFeatureController();
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);
        controller.addFeature(mMockFeature, RcsFeatureController.Feature.class);

        verify(mMockFeature).onRcsConnected(mFeatureManager);
        assertEquals(mMockFeature, controller.getFeature(RcsFeatureController.Feature.class));

        controller.removeFeature(RcsFeatureController.Feature.class);
        verify(mMockFeature).onDestroy();
        assertNull(controller.getFeature(RcsFeatureController.Feature.class));
    }

    @Test
    public void testFeatureManagerConnectedRegister() throws Exception {
        RcsFeatureController controller = createFeatureController();
        IImsRegistrationCallback regCb = mock(IImsRegistrationCallback.class);
        IImsCapabilityCallback capCb = mock(IImsCapabilityCallback.class);
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);

        try {
            controller.registerImsRegistrationCallback(TEST_SUB_ID, regCb);
            controller.registerRcsAvailabilityCallback(TEST_SUB_ID, capCb);
            controller.isCapable(RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            controller.isAvailable(RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            controller.getRegistrationTech(integer -> {
            });
            verify(mFeatureManager).registerImsRegistrationCallback(TEST_SUB_ID, regCb);
            verify(mFeatureManager).registerRcsAvailabilityCallback(TEST_SUB_ID, capCb);
            verify(mFeatureManager).isCapable(
                    RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            verify(mFeatureManager).isAvailable(
                    RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            verify(mFeatureManager).getImsRegistrationTech(any());
        } catch (ImsException e) {
            fail("ImsException not expected.");
        }

        controller.unregisterImsRegistrationCallback(TEST_SUB_ID, regCb);
        controller.unregisterRcsAvailabilityCallback(TEST_SUB_ID, capCb);
        verify(mFeatureManager).unregisterImsRegistrationCallback(TEST_SUB_ID, regCb);
        verify(mFeatureManager).unregisterRcsAvailabilityCallback(TEST_SUB_ID, capCb);
    }

    @Test
    public void testFeatureManagerConnectedHelper() throws Exception {
        RcsFeatureController controller = createFeatureController();
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);
        ArgumentCaptor<IImsRegistrationCallback> captor =
                ArgumentCaptor.forClass(IImsRegistrationCallback.class);
        verify(mFeatureManager).registerImsRegistrationCallback(captor.capture());
        assertNotNull(captor.getValue());

        captor.getValue().onDeregistered(REASON_DISCONNECTED, 0, 0);
        controller.getRegistrationState(result -> {
            assertNotNull(result);
            assertEquals(RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED, result.intValue());
        });
        verify(mRegistrationCallback).handleImsUnregistered(eq(REASON_DISCONNECTED),
                anyInt(), anyInt());

        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).build();
        captor.getValue().onRegistering(attr);
        controller.getRegistrationState(result -> {
            assertNotNull(result);
            assertEquals(RegistrationManager.REGISTRATION_STATE_REGISTERING, result.intValue());
        });
        verify(mRegistrationCallback).handleImsRegistering(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        captor.getValue().onRegistered(attr);
        controller.getRegistrationState(result -> {
            assertNotNull(result);
            assertEquals(RegistrationManager.REGISTRATION_STATE_REGISTERED, result.intValue());
        });
        verify(mRegistrationCallback).handleImsRegistered(attr);
    }

    @Test
    public void testFeatureManagerDisconnectedAddFeature() {
        RcsFeatureController controller = createFeatureController();
        // Disconnect the RcsFeatureManager
        mConnectorListener.getValue().connectionUnavailable(
                FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        controller.addFeature(mMockFeature, RcsFeatureController.Feature.class);

        verify(mMockFeature).onRcsDisconnected();
    }

    @Test
    public void testFeatureManagerDisconnectedException() {
        RcsFeatureController controller = createFeatureController();
        IImsRegistrationCallback regCb = mock(IImsRegistrationCallback.class);
        IImsCapabilityCallback capCb = mock(IImsCapabilityCallback.class);
        // Disconnect the RcsFeatureManager
        mConnectorListener.getValue().connectionUnavailable(
                FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);

        try {
            controller.registerImsRegistrationCallback(TEST_SUB_ID, null /*callback*/);
            fail("ImsException expected for IMS registration.");
        } catch (ImsException e) {
            //expected
        }
        try {
            controller.registerRcsAvailabilityCallback(TEST_SUB_ID, null /*callback*/);
            fail("ImsException expected for availability");
        } catch (ImsException e) {
            //expected
        }
        try {
            controller.isCapable(RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            fail("ImsException expected for capability check");
        } catch (ImsException e) {
            //expected
        }
        try {
            controller.isAvailable(RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            fail("ImsException expected for availability check");
        } catch (ImsException e) {
            //expected
        }
        controller.getRegistrationTech(integer -> {
            assertNotNull(integer);
            assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_NONE, integer.intValue());
        });
        controller.unregisterImsRegistrationCallback(TEST_SUB_ID, regCb);
        controller.unregisterRcsAvailabilityCallback(TEST_SUB_ID, capCb);
        verify(mFeatureManager, never()).unregisterImsRegistrationCallback(TEST_SUB_ID, regCb);
        verify(mFeatureManager, never()).unregisterRcsAvailabilityCallback(TEST_SUB_ID, capCb);
    }

    @Test
    public void testCarrierConfigChanged() throws Exception {
        RcsFeatureController controller = createFeatureController();
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);
        verify(mFeatureManager).updateCapabilities(TEST_SUB_ID);
        controller.addFeature(mMockFeature, RcsFeatureController.Feature.class);

        controller.onCarrierConfigChangedForSubscription();

        verify(mFeatureManager, times(2)).updateCapabilities(TEST_SUB_ID);
        verify(mMockFeature).onCarrierConfigChanged();
        verify(mMockFeature, never()).onAssociatedSubscriptionUpdated(anyInt());
    }

    @Test
    public void testChangeSubId() throws Exception {
        RcsFeatureController controller = createFeatureController();
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);
        verify(mFeatureManager).updateCapabilities(TEST_SUB_ID);
        controller.addFeature(mMockFeature, RcsFeatureController.Feature.class);

        controller.updateAssociatedSubscription(2 /*new subId*/);

        verify(mFeatureManager).updateCapabilities(2 /*new subId*/);
        verify(mMockFeature).onAssociatedSubscriptionUpdated(2 /*new subId*/);
    }

    @Test
    public void testDestroy() throws Exception {
        RcsFeatureController controller = createFeatureController();
        // Connect the RcsFeatureManager
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);
        controller.addFeature(mMockFeature, RcsFeatureController.Feature.class);
        controller.destroy();

        verify(mFeatureConnector).disconnect();
        verify(mMockFeature).onRcsDisconnected();
        verify(mMockFeature).onDestroy();
        assertNull(controller.getFeature(RcsFeatureController.Feature.class));
    }

    private RcsFeatureController createFeatureController() {
        RcsFeatureController controller = new RcsFeatureController(mContext, 0 /*slotId*/,
                TEST_SUB_ID, mRegistrationFactory);
        controller.setFeatureConnectorFactory(mFeatureFactory);
        doReturn(mFeatureConnector).when(mFeatureFactory).create(any(), anyInt(),
                mConnectorListener.capture(), any(), any());
        controller.connect();
        assertNotNull(mConnectorListener.getValue());
        return controller;
    }
}
