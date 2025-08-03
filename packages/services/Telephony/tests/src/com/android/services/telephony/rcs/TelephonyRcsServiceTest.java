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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.PropertyInvalidatedCache;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.ims.FeatureConnector;
import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.ISub;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class TelephonyRcsServiceTest extends TelephonyTestBase {

    @Captor ArgumentCaptor<BroadcastReceiver> mReceiverCaptor;
    @Mock TelephonyRcsService.FeatureFactory mFeatureFactory;
    @Mock TelephonyRcsService.ResourceProxy mResourceProxy;
    @Mock UceControllerManager mMockUceSlot0;
    @Mock UceControllerManager mMockUceSlot1;
    @Mock SipTransportController mMockSipTransportSlot0;
    @Mock SipTransportController mMockSipTransportSlot1;
    @Mock RcsFeatureController.RegistrationHelperFactory mRegistrationFactory;
    @Mock RcsFeatureController.FeatureConnectorFactory<RcsFeatureManager> mFeatureConnectorFactory;
    @Mock FeatureConnector<RcsFeatureManager> mFeatureConnector;

    @Mock
    private ISub mISub;

    @Mock
    private TelephonyManager mTelephonyManager;

    private RcsFeatureController mFeatureControllerSlot0;
    private RcsFeatureController mFeatureControllerSlot1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        TelephonyManager.setupISubForTest(mISub);
        TelephonyManager.enableServiceHandleCaching();
        PropertyInvalidatedCache.disableForTestMode();

        //set up default slot-> sub ID mappings.
        setSlotToSubIdMapping(0 /*slotId*/, 1/*subId*/);
        setSlotToSubIdMapping(1 /*slotId*/, 2/*subId*/);

        doReturn(mFeatureConnector).when(mFeatureConnectorFactory).create(any(), anyInt(),
                any(), any(), any());
        mFeatureControllerSlot0 = createFeatureController(0 /*slotId*/, 1 /*subId*/);
        mFeatureControllerSlot1 = createFeatureController(1 /*slotId*/, 2 /*subId*/);
        doReturn(mFeatureControllerSlot0).when(mFeatureFactory).createController(any(), eq(0),
                anyInt());
        doReturn(mFeatureControllerSlot1).when(mFeatureFactory).createController(any(), eq(1),
                anyInt());
        doReturn(mMockUceSlot0).when(mFeatureFactory).createUceControllerManager(any(), eq(0),
                anyInt());
        doReturn(mMockUceSlot1).when(mFeatureFactory).createUceControllerManager(any(), eq(1),
                anyInt());
        doReturn(mMockSipTransportSlot0).when(mFeatureFactory).createSipTransportController(any(),
                eq(0), anyInt());
        doReturn(mMockSipTransportSlot1).when(mFeatureFactory).createSipTransportController(any(),
                eq(1), anyInt());
        doReturn(true).when(mResourceProxy).getDeviceUceEnabled(any());

        replaceInstance(TelephonyManager.class, "sInstance", null, mTelephonyManager);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testUceControllerPresenceConnected() {
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();
    }

    @Test
    public void testUceControllerOptionsConnected() {
        setCarrierConfig(1 /*subId*/, CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL,
                true /*isEnabled*/);
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();
    }

    @Test
    public void testNoFeaturesEnabled() {
        createRcsService(1 /*numSlots*/);
        // No carrier config set for UCE.
        verify(mFeatureControllerSlot0, never()).addFeature(mMockUceSlot0,
                UceControllerManager.class);
        verify(mFeatureControllerSlot0, never()).connect();
    }

    @Test
    public void testSipTransportConnected() {
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0, never()).addFeature(mMockSipTransportSlot0,
                SipTransportController.class);
        verify(mFeatureControllerSlot0, never()).connect();


        // Send carrier config update for each slot.
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                true /*isEnabled*/);
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0).addFeature(mMockSipTransportSlot0,
                SipTransportController.class);
        verify(mFeatureControllerSlot0).connect();
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
    }

    @Test
    public void testSipTransportConnectedOneSlot() {
        createRcsService(2 /*numSlots*/);
        verify(mFeatureControllerSlot0, never()).addFeature(mMockSipTransportSlot0,
                SipTransportController.class);
        verify(mFeatureControllerSlot0, never()).connect();
        verify(mFeatureControllerSlot0, never()).addFeature(mMockSipTransportSlot1,
                SipTransportController.class);
        verify(mFeatureControllerSlot1, never()).connect();


        // Send carrier config update for slot 0 only
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                true /*isEnabled*/);
        setCarrierConfig(2 /*subId*/,
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                false /*isEnabled*/);
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        sendCarrierConfigChanged(1 /*slotId*/, 2 /*subId*/);
        verify(mFeatureControllerSlot0).addFeature(mMockSipTransportSlot0,
                SipTransportController.class);
        verify(mFeatureControllerSlot1, never()).addFeature(mMockSipTransportSlot0,
                SipTransportController.class);
        verify(mFeatureControllerSlot0).connect();
        verify(mFeatureControllerSlot1, never()).connect();
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
        verify(mFeatureControllerSlot1, never()).updateAssociatedSubscription(1);
    }

    @Test
    public void testNoFeaturesEnabledCarrierConfigChanged() {
        createRcsService(1 /*numSlots*/);
        // No carrier config set for UCE.

        sendCarrierConfigChanged(0, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mFeatureControllerSlot0, never()).addFeature(mMockUceSlot0,
                UceControllerManager.class);
        verify(mFeatureControllerSlot0, never()).connect();
        verify(mFeatureControllerSlot0, never()).updateAssociatedSubscription(anyInt());
    }


    @Test
    public void testSlotUpdates() {
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        setCarrierConfig(2 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        TelephonyRcsService service = createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();

        // there should be no changes if the new num slots = old num
        service.updateFeatureControllerSize(1 /*newNumSlots*/);
        verify(mFeatureControllerSlot0, times(1)).addFeature(mMockUceSlot0,
                UceControllerManager.class);
        verify(mFeatureControllerSlot0, times(1)).connect();

        // Add a new slot.
        verify(mFeatureControllerSlot1, never()).addFeature(mMockUceSlot1,
                UceControllerManager.class);
        verify(mFeatureControllerSlot1, never()).connect();
        service.updateFeatureControllerSize(2 /*newNumSlots*/);
        // This shouldn't have changed for slot 0.
        verify(mFeatureControllerSlot0, times(1)).addFeature(mMockUceSlot0,
                UceControllerManager.class);
        verify(mFeatureControllerSlot0, times(1)).connect();
        verify(mFeatureControllerSlot1).addFeature(mMockUceSlot1, UceControllerManager.class);
        verify(mFeatureControllerSlot1, times(1)).connect();

        // Remove a slot.
        verify(mFeatureControllerSlot0, never()).destroy();
        verify(mFeatureControllerSlot1, never()).destroy();
        service.updateFeatureControllerSize(1 /*newNumSlots*/);
        // addFeature/connect shouldn't have been called again
        verify(mFeatureControllerSlot0, times(1)).addFeature(mMockUceSlot0,
                UceControllerManager.class);
        verify(mFeatureControllerSlot0, times(1)).connect();
        verify(mFeatureControllerSlot1, times(1)).addFeature(mMockUceSlot1,
                UceControllerManager.class);
        verify(mFeatureControllerSlot1, times(1)).connect();
        // Verify destroy is only called for slot 1.
        verify(mFeatureControllerSlot0, never()).destroy();
        verify(mFeatureControllerSlot1, times(1)).destroy();
    }

    @Test
    public void testCarrierConfigUpdateAssociatedSub() {
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        setCarrierConfig(2 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        createRcsService(2 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot1).addFeature(mMockUceSlot1, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();
        verify(mFeatureControllerSlot1).connect();


        // Send carrier config update for each slot.
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
        verify(mFeatureControllerSlot1, never()).updateAssociatedSubscription(1);
        sendCarrierConfigChanged(1 /*slotId*/, 2 /*subId*/);
        verify(mFeatureControllerSlot0, never()).updateAssociatedSubscription(2);
        verify(mFeatureControllerSlot1, times(1)).updateAssociatedSubscription(2);
    }

    @Test
    public void testCarrierConfigNotifyFeatures() {
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();


        // Send carrier config update twice with no update to subId
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
        verify(mFeatureControllerSlot0, never()).onCarrierConfigChangedForSubscription();
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0, times(1)).updateAssociatedSubscription(1);
        // carrier config changed should be sent here
        verify(mFeatureControllerSlot0).onCarrierConfigChangedForSubscription();
    }

    @Test
    public void testCarrierConfigUpdateUceToNoUce() {
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();


        // Send carrier config update for each slot.
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                false /*isEnabled*/);
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0).removeFeature(UceControllerManager.class);
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
    }

    @Test
    public void testCarrierConfigUpdateTransportToNoTransport() {
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                true /*isEnabled*/);
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0).addFeature(mMockSipTransportSlot0,
                SipTransportController.class);
        verify(mFeatureControllerSlot0).connect();


        // Send carrier config update for each slot.
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                false /*isEnabled*/);
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0).removeFeature(SipTransportController.class);
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
    }

    @Test
    public void testCarrierConfigUpdateNoUceToUce() {
        createRcsService(1 /*numSlots*/);
        verify(mFeatureControllerSlot0, never()).addFeature(mMockUceSlot0,
                UceControllerManager.class);
        verify(mFeatureControllerSlot0, never()).connect();


        // Send carrier config update for each slot.
        setCarrierConfig(1 /*subId*/,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                true /*isEnabled*/);
        sendCarrierConfigChanged(0 /*slotId*/, 1 /*subId*/);
        verify(mFeatureControllerSlot0).addFeature(mMockUceSlot0, UceControllerManager.class);
        verify(mFeatureControllerSlot0).connect();
        verify(mFeatureControllerSlot0).updateAssociatedSubscription(1);
    }

    private void sendCarrierConfigChanged(int slotId, int subId) {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, slotId);
        intent.putExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        mReceiverCaptor.getValue().onReceive(mContext, intent);
    }

    private void setCarrierConfig(int subId, String key, boolean value) {
        PersistableBundle bundle = mContext.getCarrierConfig(subId);
        bundle.putBoolean(key, value);
    }

    private void setSlotToSubIdMapping(int slotId, int loadedSubId) throws Exception {
        doReturn(loadedSubId).when(mISub).getSubId(slotId);
    }

    private TelephonyRcsService createRcsService(int numSlots) {
        TelephonyRcsService service = new TelephonyRcsService(mContext, numSlots, mResourceProxy);
        service.setFeatureFactory(mFeatureFactory);
        service.initialize();
        verify(mContext).registerReceiver(mReceiverCaptor.capture(), any());
        return service;
    }

    private RcsFeatureController createFeatureController(int slotId, int subId) {
        // Create a spy instead of a mock because TelephonyRcsService relies on state provided by
        // RcsFeatureController.
        RcsFeatureController controller = spy(new RcsFeatureController(mContext, slotId, subId,
                mRegistrationFactory));
        controller.setFeatureConnectorFactory(mFeatureConnectorFactory);
        return controller;
    }
}
