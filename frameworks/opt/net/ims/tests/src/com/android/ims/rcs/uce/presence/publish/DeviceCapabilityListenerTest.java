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

package com.android.ims.rcs.uce.presence.publish;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.refEq;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Handler;
import android.telecom.TelecomManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RegistrationManager.RegistrationCallback;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.UceStatsWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class DeviceCapabilityListenerTest extends ImsTestBase {

    private static final long HANDLER_WAIT_TIMEOUT_MS = 2000L;
    private static final long HANDLER_SENT_DELAY_MS = 1000L;

    @Mock DeviceCapabilityInfo mDeviceCapability;
    @Mock PublishController.PublishControllerCallback mCallback;
    @Mock ImsMmTelManager mImsMmTelManager;
    @Mock ImsRcsManager mImsRcsManager;
    @Mock ProvisioningManager mProvisioningManager;
    @Mock DeviceCapabilityListener.ImsMmTelManagerFactory mImsMmTelMgrFactory;
    @Mock DeviceCapabilityListener.ImsRcsManagerFactory mImsRcsMgrFactory;
    @Mock DeviceCapabilityListener.ProvisioningManagerFactory mProvisioningMgrFactory;
    @Mock UceStatsWriter mUceStatsWriter;

    int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mImsMmTelManager).when(mImsMmTelMgrFactory).getImsMmTelManager(anyInt());
        doReturn(mImsRcsManager).when(mImsRcsMgrFactory).getImsRcsManager(anyInt());
        doReturn(mProvisioningManager).when(mProvisioningMgrFactory).
                getProvisioningManager(anyInt());

        doReturn(true).when(mDeviceCapability).updateTtyPreferredMode(anyInt());
        doReturn(true).when(mDeviceCapability).updateMobileData(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateVtSetting(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateVtSetting(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateMmtelCapabilitiesChanged(any());

        doNothing().when(mUceStatsWriter).setImsRegistrationFeatureTagStats(
                anyInt(), anyList(), anyInt());
        doNothing().when(mUceStatsWriter).setStoreCompleteImsRegistrationFeatureTagStats(anyInt());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testTurnOnListener() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();

        deviceCapListener.initialize();

        verify(mContext).registerReceiver(any(), any(),
            eq(android.Manifest.permission.MODIFY_PHONE_STATE), any(), anyInt());
        verify(mProvisioningManager).registerProvisioningChangedCallback(any(), any());
    }

    @Test
    @SmallTest
    public void testDestroy() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.initialize();

        // The listener is destroyed.
        deviceCapListener.onDestroy();

        verify(mContext).unregisterReceiver(any());
        verify(mProvisioningManager).unregisterProvisioningChangedCallback(any());
    }

    @Test
    @SmallTest
    public void testTtyPreferredModeChange() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        final BroadcastReceiver receiver = deviceCapListener.mReceiver;

        Intent intent = new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        receiver.onReceive(mContext, intent);

        Handler handler = deviceCapListener.getHandler();
        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateTtyPreferredMode(anyInt());
        verify(mCallback).requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_TTY_PREFERRED_CHANGE);
    }

    @Test
    @SmallTest
    public void testMmtelRegistration() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.setImsCallbackRegistered(true);
        RegistrationCallback registrationCallback = deviceCapListener.mMmtelRegistrationCallback;

        registrationCallback.onRegistered(1);

        Handler handler = deviceCapListener.getHandler();
        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateImsMmtelRegistered(1);
        // update capability, but not trigger PUBLISH message when MmTel registered.
        verify(mCallback, never()).requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_MMTEL_REGISTERED);
    }

    @Test
    @SmallTest
    public void testRcsRegistration() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.setImsCallbackRegistered(true);
        RegistrationCallback registrationCallback = deviceCapListener.mRcsRegistrationCallback;

        List<String> list = new ArrayList<>();
        list.add("+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.im\"");
        list.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"");
        list.add("+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.ftsms\"");
        Set<String> featureTags = new HashSet<String>(list);

        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE)
                .setFeatureTags(featureTags)
                .build();

        // Notify DeviceCapabilityListener that registered has caused a change and requires publish
        doReturn(true).when(mDeviceCapability).updateImsRcsRegistered(attr);

        registrationCallback.onRegistered(attr);
        Handler handler = deviceCapListener.getHandler();
        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateImsRcsRegistered(attr);
        verify(mCallback).requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_RCS_REGISTERED);
        verify(mUceStatsWriter).setImsRegistrationFeatureTagStats(anyInt(),
            refEq(list), eq(ImsRegistrationImplBase.REGISTRATION_TECH_LTE));
    }

    @Test
    @SmallTest
    public void testMmtelCapabilityChange() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        ImsMmTelManager.CapabilityCallback callback = deviceCapListener.mMmtelCapabilityCallback;

        MmTelFeature.MmTelCapabilities capabilities = new MmTelFeature.MmTelCapabilities();
        callback.onCapabilitiesStatusChanged(capabilities);

        Handler handler = deviceCapListener.getHandler();
        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateMmtelCapabilitiesChanged(capabilities);
        verify(mCallback).requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_MMTEL_CAPABILITY_CHANGE);
    }

    @Test
    @SmallTest
    public void testImsUnregistration() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.setImsCallbackRegistered(true);

        // set the Ims is registered
        doReturn(true).when(mDeviceCapability).isImsRegistered();
        // MMTEL unregistered
        RegistrationCallback mmtelRegiCallback = deviceCapListener.mMmtelRegistrationCallback;

        ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, -1, "");
        mmtelRegiCallback.onUnregistered(info);

        Handler handler = deviceCapListener.getHandler();
        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateImsMmtelUnregistered();

        // Do not send internal publish trigger
        verify(mCallback, never()).requestPublishFromInternal(anyInt());
        // Only MMTEL unregistered. Verify do not send ImsUnregistered.
        verify(mCallback, never()).updateImsUnregistered();

        // set the Ims Unregistered
        doReturn(false).when(mDeviceCapability).isImsRegistered();
        // RCS unregistered
        RegistrationCallback rcsRegiCallback = deviceCapListener.mRcsRegistrationCallback;
        doReturn(true).when(mDeviceCapability).updateImsRcsUnregistered();

        rcsRegiCallback.onUnregistered(info);

        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateImsRcsUnregistered();
        // Do not send internal publish trigger
        verify(mCallback, never()).requestPublishFromInternal(anyInt());
        verify(mUceStatsWriter).setStoreCompleteImsRegistrationFeatureTagStats(anyInt());

        verify(mCallback).updateImsUnregistered();
    }

    @Test
    @SmallTest
    public void testRcsAndMmtelUnregistration() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.setImsCallbackRegistered(true);

        Handler handler = deviceCapListener.getHandler();
        ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, -1, "");
        // RCS unregistered
        RegistrationCallback rcsRegiCallback = deviceCapListener.mRcsRegistrationCallback;

        doReturn(true).when(mDeviceCapability).updateImsRcsUnregistered();
        // RCS is unregistered but MMTEL is registered.
        doReturn(true).when(mDeviceCapability).isImsRegistered();
        rcsRegiCallback.onUnregistered(info);

        // MMTEL unregistered
        RegistrationCallback mmtelRegiCallback = deviceCapListener.mMmtelRegistrationCallback;
        // set the Ims is unregistered
        doReturn(false).when(mDeviceCapability).isImsRegistered();
        mmtelRegiCallback.onUnregistered(info);

        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        // Do not send internal publish trigger
        verify(mCallback, never()).requestPublishFromInternal(anyInt());
        // IMS is unregistered. Verify send ImsUnregistered.
        verify(mCallback).updateImsUnregistered();
    }

    @Test
    @SmallTest
    public void testUnregisterRcsOnlyFromImsRegistration() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.setImsCallbackRegistered(true);
        Handler handler = deviceCapListener.getHandler();

        // set the Ims is registered
        doReturn(true).when(mDeviceCapability).isImsRegistered();
        ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, -1, "");
        // RCS unregistered
        RegistrationCallback rcsRegiCallback = deviceCapListener.mRcsRegistrationCallback;

        doReturn(true).when(mDeviceCapability).updateImsRcsUnregistered();
        // RCS is unregistered but MMTEL is registered.
        doReturn(true).when(mDeviceCapability).isImsRegistered();
        rcsRegiCallback.onUnregistered(info);

        waitForHandlerActionDelayed(handler, HANDLER_WAIT_TIMEOUT_MS, HANDLER_SENT_DELAY_MS);

        verify(mDeviceCapability).updateImsRcsUnregistered();
        // Only RCS unregistered. Verify the request of the modify publish is sent.
        verify(mCallback).requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_RCS_URI_CHANGE);

        // Only RCS unregistered. Verify do not send ImsUnregistered.
        verify(mCallback, never()).updateImsUnregistered();
    }

    private DeviceCapabilityListener createDeviceCapabilityListener() {
        DeviceCapabilityListener deviceCapListener = new DeviceCapabilityListener(mContext,
                mSubId, mDeviceCapability, mCallback, mUceStatsWriter);
        deviceCapListener.setImsMmTelManagerFactory(mImsMmTelMgrFactory);
        deviceCapListener.setImsRcsManagerFactory(mImsRcsMgrFactory);
        deviceCapListener.setProvisioningMgrFactory(mProvisioningMgrFactory);
        return deviceCapListener;
    }
}
