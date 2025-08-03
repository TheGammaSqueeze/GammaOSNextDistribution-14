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

package com.android.phone;

import static android.telephony.ims.ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED;
import static android.telephony.ims.ImsStateCallback.REASON_IMS_SERVICE_NOT_READY;
import static android.telephony.ims.ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED;
import static android.telephony.ims.ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE;
import static android.telephony.ims.feature.ImsFeature.FEATURE_MMTEL;
import static android.telephony.ims.feature.ImsFeature.FEATURE_RCS;

import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED;
import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_IMS_UNSUPPORTED;
import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_NOT_READY;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.testing.TestableLooper;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.TelephonyTestBase;
import com.android.ims.FeatureConnector;
import com.android.ims.ImsManager;
import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.IImsStateCallback;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ims.ImsResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.Executor;

/**
 * Unit tests for RcsProvisioningMonitor
 */
public class ImsStateCallbackControllerTest extends TelephonyTestBase {
    private static final String TAG = "ImsStateCallbackControllerTest";
    private static final int FAKE_SUB_ID_BASE = 0x0FFFFFF0;

    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;

    private static final int SLOT_0_SUB_ID = 1;
    private static final int SLOT_1_SUB_ID = 2;
    private static final int SLOT_2_SUB_ID = 3;

    private ImsStateCallbackController mImsStateCallbackController;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    @Mock private SubscriptionManager mSubscriptionManager;
    private SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener;
    @Mock private TelephonyRegistryManager mTelephonyRegistryManager;
    @Mock private ITelephony.Stub mITelephony;
    @Mock private RcsFeatureManager mRcsFeatureManager;
    @Mock private ImsManager mMmTelFeatureManager;
    @Mock private ImsStateCallbackController.MmTelFeatureConnectorFactory mMmTelFeatureFactory;
    @Mock private ImsStateCallbackController.RcsFeatureConnectorFactory mRcsFeatureFactory;
    @Mock private FeatureConnector<ImsManager> mMmTelFeatureConnectorSlot0;
    @Mock private FeatureConnector<ImsManager> mMmTelFeatureConnectorSlot1;
    @Mock private FeatureConnector<RcsFeatureManager> mRcsFeatureConnectorSlot0;
    @Mock private FeatureConnector<RcsFeatureManager> mRcsFeatureConnectorSlot1;
    @Captor ArgumentCaptor<FeatureConnector.Listener<ImsManager>> mMmTelConnectorListenerSlot0;
    @Captor ArgumentCaptor<FeatureConnector.Listener<ImsManager>> mMmTelConnectorListenerSlot1;
    @Captor ArgumentCaptor<FeatureConnector.Listener<RcsFeatureManager>> mRcsConnectorListenerSlot0;
    @Captor ArgumentCaptor<FeatureConnector.Listener<RcsFeatureManager>> mRcsConnectorListenerSlot1;
    @Mock private PhoneGlobals mPhone;
    @Mock ImsStateCallbackController.PhoneFactoryProxy mPhoneFactoryProxy;
    @Mock Phone mPhoneSlot0;
    @Mock Phone mPhoneSlot1;
    @Mock private IBinder mBinder0;
    @Mock private IBinder mBinder1;
    @Mock private IBinder mBinder2;
    @Mock private IBinder mBinder3;
    @Mock private IImsStateCallback mCallback0;
    @Mock private IImsStateCallback mCallback1;
    @Mock private IImsStateCallback mCallback2;
    @Mock private IImsStateCallback mCallback3;
    @Mock private ImsResolver mImsResolver;

    private Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mPhone.getMainExecutor()).thenReturn(mExecutor);
        when(mPhone.getSystemServiceName(eq(SubscriptionManager.class)))
                .thenReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        when(mPhone.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mSubscriptionManager);
        when(mPhone.getSystemServiceName(eq(TelephonyRegistryManager.class)))
                .thenReturn(Context.TELEPHONY_REGISTRY_SERVICE);
        when(mPhone.getSystemService(eq(Context.TELEPHONY_REGISTRY_SERVICE)))
                .thenReturn(mTelephonyRegistryManager);
        when(mPhoneFactoryProxy.getPhone(eq(0))).thenReturn(mPhoneSlot0);
        when(mPhoneFactoryProxy.getPhone(eq(1))).thenReturn(mPhoneSlot1);
        when(mPhoneSlot0.getSubId()).thenReturn(SLOT_0_SUB_ID);
        when(mPhoneSlot1.getSubId()).thenReturn(SLOT_1_SUB_ID);

        when(mCallback0.asBinder()).thenReturn(mBinder0);
        when(mCallback1.asBinder()).thenReturn(mBinder1);
        when(mCallback2.asBinder()).thenReturn(mBinder2);
        when(mCallback3.asBinder()).thenReturn(mBinder3);

        // slot 0
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(0), eq(FEATURE_MMTEL)))
                .thenReturn(true);
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(0), eq(FEATURE_RCS)))
                .thenReturn(true);

        // slot 1
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(1), eq(FEATURE_MMTEL)))
                .thenReturn(true);
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(1), eq(FEATURE_RCS)))
                .thenReturn(true);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                mSubChangedListener = (SubscriptionManager.OnSubscriptionsChangedListener)
                        invocation.getArguments()[0];
                return null;
            }
        }).when(mTelephonyRegistryManager).addOnSubscriptionsChangedListener(
                any(SubscriptionManager.OnSubscriptionsChangedListener.class),
                any());

        mHandlerThread = new HandlerThread("ImsStateCallbackControllerTest");
        mHandlerThread.start();
    }

    @After
    public void tearDown() throws Exception {
        if (mImsStateCallbackController != null) {
            mImsStateCallbackController.destroy();
            mImsStateCallbackController = null;
        }

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testMmTelRegisterThenUnregisterCallback() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testMmTelConnectionUnavailable() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testMmTelConnectionReady() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback0, times(0)).onAvailable();

        mMmTelConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        verify(mCallback0, atLeastOnce()).onAvailable();

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testMmTelIgnoreDuplicatedConsecutiveReason() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testRcsRegisterThenUnregisterCallback() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testRcsConnectionUnavailable() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // TelephonyRcsService notifying active features
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, false, true);
        processAllMessages();

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testRcsConnectionReady() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // TelephonyRcsService notifying active features
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, false, true);
        processAllMessages();

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        mRcsConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(0)).onAvailable();

        // RcsFeatureController notifying STATE_READY
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, true, true);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);
        processAllMessages();
        verify(mCallback0, times(2)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(2)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        // RcsFeatureController notifying STATE_READY
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, true, true);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();

        mRcsConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(2)).onAvailable();

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testRcsHasNoActiveFeature() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // TelephonyRcsService notifying NO active feature
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, false, false);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(0)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        mRcsConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(0)).onAvailable();

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testRcsIgnoreDuplicatedConsecutiveReason() throws Exception {
        createController(1);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback0, "callback0");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // TelephonyRcsService notifying active features
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, false, true);
        processAllMessages();

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
    }

    @Test
    @SmallTest
    public void testCallbackRemovedWhenSubInfoChanged() throws Exception {
        createController(2);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_1_SUB_ID, FEATURE_RCS, mCallback1, "callback1");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback1));

        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        makeFakeActiveSubIds(0);
        mExecutor.execute(() -> mSubChangedListener.onSubscriptionsChanged());
        processAllMessages();

        verify(mCallback0, times(1)).onUnavailable(REASON_SUBSCRIPTION_INACTIVE);
        verify(mCallback1, times(1)).onUnavailable(REASON_SUBSCRIPTION_INACTIVE);

        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback1));
    }

    @Test
    @SmallTest
    public void testCarrierConfigurationChanged() throws Exception {
        createController(2);

        when(mImsResolver.isImsServiceConfiguredForFeature(eq(1), eq(FEATURE_MMTEL)))
                .thenReturn(false);

        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_1_SUB_ID, FEATURE_MMTEL, mCallback1, "callback1");
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_1_SUB_ID, FEATURE_RCS, mCallback2, "callback2");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback1));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback2));

        // check initial reason
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback2, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        verify(mCallback0, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);
        verify(mCallback1, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);
        verify(mCallback2, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        // ensure only one reason reported until now
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        // state change in RCS for slot 0
        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);

        // ensure there is no change, since callbacks are not interested RCS on slot 0
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        // carrier config changed, no MMTEL package for slot 1
        mImsStateCallbackController.notifyCarrierConfigChanged(SLOT_1);
        processAllMessages();

        // only the callback for MMTEL of slot 1 received the reason
        verify(mCallback0, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);
        verify(mCallback1, times(1)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);
        verify(mCallback2, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        // ensure no other callbacks
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        mMmTelConnectorListenerSlot1.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        mMmTelConnectorListenerSlot1.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);

        // resons except REASON_NO_IMS_SERVICE_CONFIGURED are discared
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        // IMS package for MMTEL of slot 1 is added
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(1), eq(FEATURE_MMTEL)))
                .thenReturn(true);
        mImsStateCallbackController.notifyCarrierConfigChanged(SLOT_1);
        processAllMessages();

        // ensure the callback to MMTEL of slot 1 only received REASON_IMS_SERVICE_DISCONNECTED
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(2)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback2, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // ensure no other reason repored
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(3)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        // carrier config changed, no MMTEL package for slot 1
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(1), eq(FEATURE_MMTEL)))
                .thenReturn(false);
        mImsStateCallbackController.notifyCarrierConfigChanged(SLOT_1);
        mImsStateCallbackController.notifyCarrierConfigChanged(SLOT_1);
        processAllMessages();
        // only the callback for MMTEL of slot 1 received the reason
        verify(mCallback0, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);
        verify(mCallback1, times(2)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);
        verify(mCallback2, times(0)).onUnavailable(REASON_NO_IMS_SERVICE_CONFIGURED);

        // ensure no other reason repored
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(4)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        mMmTelConnectorListenerSlot1.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);

        // resons except REASON_NO_IMS_SERVICE_CONFIGURED are discared
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(4)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        // IMS package for MMTEL of slot 1 is added
        when(mImsResolver.isImsServiceConfiguredForFeature(eq(1), eq(FEATURE_MMTEL)))
                .thenReturn(true);
        mImsStateCallbackController.notifyCarrierConfigChanged(SLOT_1);
        processAllMessages();

        // ensure the callback to MMTEL of slot 1
        // there is a pending reason UNAVAILABLE_REASON_NOT_READY
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(2)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback2, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // ensure no other reason repored
        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(5)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());

        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback1));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback2));

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        mImsStateCallbackController.unregisterImsStateCallback(mCallback1);
        mImsStateCallbackController.unregisterImsStateCallback(mCallback2);
        processAllMessages();

        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback1));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback2));
    }

    @Test
    @SmallTest
    public void testMultiSubscriptions() throws Exception {
        createController(2);

        // registration
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback1, "callback1");
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_1_SUB_ID, FEATURE_MMTEL, mCallback2, "callback2");
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_1_SUB_ID, FEATURE_RCS, mCallback3, "callback3");
        processAllMessages();
        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback1));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback2));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback3));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback2, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback3, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);

        // TelephonyRcsService notifying active features
        // slot 0
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, false, true);
        // slot 1
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_1, false, true);
        processAllMessages();

        verify(mCallback0, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());

        verify(mCallback0, times(0)).onAvailable();
        verify(mCallback1, times(0)).onAvailable();
        verify(mCallback2, times(0)).onAvailable();
        verify(mCallback3, times(0)).onAvailable();

        // connectionUnavailable
        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());

        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());

        mMmTelConnectorListenerSlot1.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(anyInt());

        mRcsConnectorListenerSlot1.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(1)).onUnavailable(REASON_IMS_SERVICE_NOT_READY);
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        // connectionReady
        mMmTelConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();
        verify(mCallback1, times(0)).onAvailable();
        verify(mCallback2, times(0)).onAvailable();
        verify(mCallback3, times(0)).onAvailable();
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        mRcsConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();
        verify(mCallback1, times(0)).onAvailable();
        verify(mCallback2, times(0)).onAvailable();
        verify(mCallback3, times(0)).onAvailable();
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, true, true);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();
        verify(mCallback1, times(1)).onAvailable();
        verify(mCallback2, times(0)).onAvailable();
        verify(mCallback3, times(0)).onAvailable();
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        mMmTelConnectorListenerSlot1.getValue().connectionReady(null, SLOT_1_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();
        verify(mCallback1, times(1)).onAvailable();
        verify(mCallback2, times(1)).onAvailable();
        verify(mCallback3, times(0)).onAvailable();
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        mRcsConnectorListenerSlot1.getValue().connectionReady(null, SLOT_1_SUB_ID);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();
        verify(mCallback1, times(1)).onAvailable();
        verify(mCallback2, times(1)).onAvailable();
        verify(mCallback3, times(0)).onAvailable();
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_1, true, true);
        processAllMessages();
        verify(mCallback0, times(1)).onAvailable();
        verify(mCallback1, times(1)).onAvailable();
        verify(mCallback2, times(1)).onAvailable();
        verify(mCallback3, times(1)).onAvailable();
        verify(mCallback0, times(2)).onUnavailable(anyInt());
        verify(mCallback1, times(2)).onUnavailable(anyInt());
        verify(mCallback2, times(2)).onUnavailable(anyInt());
        verify(mCallback3, times(2)).onUnavailable(anyInt());

        // unregistration
        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback1));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback2));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback3));

        mImsStateCallbackController.unregisterImsStateCallback(mCallback1);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback1));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback2));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback3));

        mImsStateCallbackController.unregisterImsStateCallback(mCallback2);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback1));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback2));
        assertTrue(mImsStateCallbackController.isRegistered(mCallback3));

        mImsStateCallbackController.unregisterImsStateCallback(mCallback3);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback1));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback2));
        assertFalse(mImsStateCallbackController.isRegistered(mCallback3));
    }

    @Test
    @SmallTest
    public void testSlotUpdates() throws Exception {
        createController(1);

        verify(mMmTelFeatureConnectorSlot0, times(1)).connect();
        verify(mRcsFeatureConnectorSlot0, times(1)).connect();
        verify(mMmTelFeatureConnectorSlot0, times(0)).disconnect();
        verify(mRcsFeatureConnectorSlot0, times(0)).disconnect();

        // Add a new slot.
        mImsStateCallbackController.updateFeatureControllerSize(2);

        // connect in slot 1
        verify(mMmTelFeatureConnectorSlot1, times(1)).connect();
        verify(mRcsFeatureConnectorSlot1, times(1)).connect();

        // no change in slot 0
        verify(mMmTelFeatureConnectorSlot0, times(1)).connect();
        verify(mRcsFeatureConnectorSlot0, times(1)).connect();

        // Remove a slot.
        mImsStateCallbackController.updateFeatureControllerSize(1);

        // destroy in slot 1
        verify(mMmTelFeatureConnectorSlot1, times(1)).disconnect();
        verify(mRcsFeatureConnectorSlot1, times(1)).disconnect();

        // no change in slot 0
        verify(mMmTelFeatureConnectorSlot0, times(0)).disconnect();
        verify(mRcsFeatureConnectorSlot0, times(0)).disconnect();
    }

    @Test
    @SmallTest
    public void testMmTelConnectionReadyWhenReEnableSim() throws Exception {
        createController(1);

        // MMTEL feature
        mMmTelConnectorListenerSlot0.getValue().connectionReady(null, SLOT_0_SUB_ID);
        processAllMessages();
        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);
        processAllMessages();
        mMmTelConnectorListenerSlot0.getValue().connectionReady(null,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_MMTEL, mCallback0, "callback0");
        processAllMessages();

        assertTrue(mImsStateCallbackController.isRegistered(mCallback0));
        verify(mCallback0, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback0, times(0)).onAvailable();

        mImsStateCallbackController.unregisterImsStateCallback(mCallback0);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback0));

        // RCS feature
        // TelephonyRcsService notifying active features
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, false, true);
        processAllMessages();
        // RcsFeatureController notifying STATE_READY
        mImsStateCallbackController.notifyExternalRcsStateChanged(SLOT_0, true, true);
        processAllMessages();
        mRcsConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);
        processAllMessages();
        mRcsConnectorListenerSlot0.getValue().connectionReady(null,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        mImsStateCallbackController
                .registerImsStateCallback(SLOT_0_SUB_ID, FEATURE_RCS, mCallback1, "callback1");
        processAllMessages();

        assertTrue(mImsStateCallbackController.isRegistered(mCallback1));
        verify(mCallback1, times(1)).onUnavailable(REASON_IMS_SERVICE_DISCONNECTED);
        verify(mCallback1, times(0)).onAvailable();

        mImsStateCallbackController.unregisterImsStateCallback(mCallback1);
        processAllMessages();
        assertFalse(mImsStateCallbackController.isRegistered(mCallback1));
    }

    @Test
    @SmallTest
    public void testImsManagerInstance() throws Exception {
        createController(1);

        // MmTelConnection not ready
        // check ImsManager instance
        ImsManager imsManager = mImsStateCallbackController.getImsManager(SLOT_0_SUB_ID);
        assertNull(imsManager);

        // MmTelConnection ready
        mMmTelConnectorListenerSlot0.getValue()
                .connectionReady(mMmTelFeatureManager, SLOT_0_SUB_ID);
        processAllMessages();

        // check ImsManager instance
        imsManager = mImsStateCallbackController.getImsManager(SLOT_0_SUB_ID);
        assertNotNull(imsManager);

        // MmTelConnection unavailable
        mMmTelConnectorListenerSlot0.getValue()
                .connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
        processAllMessages();

        // MmTelConnection unavailable
        // check ImsManager instance
        imsManager = mImsStateCallbackController.getImsManager(SLOT_0_SUB_ID);
        assertNull(imsManager);
    }

    private void createController(int slotCount) throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        makeFakeActiveSubIds(slotCount);

        when(mMmTelFeatureFactory
                .create(any(), eq(0), any(), mMmTelConnectorListenerSlot0.capture(), any()))
                .thenReturn(mMmTelFeatureConnectorSlot0);
        when(mMmTelFeatureFactory
                .create(any(), eq(1), any(), mMmTelConnectorListenerSlot1.capture(), any()))
                .thenReturn(mMmTelFeatureConnectorSlot1);
        when(mRcsFeatureFactory
                .create(any(), eq(0), mRcsConnectorListenerSlot0.capture(), any(), any()))
                .thenReturn(mRcsFeatureConnectorSlot0);
        when(mRcsFeatureFactory
                .create(any(), eq(1), mRcsConnectorListenerSlot1.capture(), any(), any()))
                .thenReturn(mRcsFeatureConnectorSlot1);

        mImsStateCallbackController =
                new ImsStateCallbackController(mPhone, mHandlerThread.getLooper(),
                        slotCount, mMmTelFeatureFactory, mRcsFeatureFactory, mImsResolver);

        replaceInstance(ImsStateCallbackController.class,
                "mPhoneFactoryProxy", mImsStateCallbackController, mPhoneFactoryProxy);
        mImsStateCallbackController.onSubChanged();

        mHandler = mImsStateCallbackController.getHandler();
        try {
            mLooper = new TestableLooper(mHandler.getLooper());
        } catch (Exception e) {
            logd("Unable to create looper from handler.");
        }

        verify(mRcsFeatureConnectorSlot0, atLeastOnce()).connect();
        verify(mMmTelFeatureConnectorSlot0, atLeastOnce()).connect();

        if (slotCount == 1) {
            verify(mRcsFeatureConnectorSlot1, times(0)).connect();
            verify(mMmTelFeatureConnectorSlot1, times(0)).connect();
        } else {
            verify(mRcsFeatureConnectorSlot1, atLeastOnce()).connect();
            verify(mMmTelFeatureConnectorSlot1, atLeastOnce()).connect();
        }
    }

    private void makeFakeActiveSubIds(int count) {
        final int[] subIds = new int[count];
        for (int i = 0; i < count; i++) {
            subIds[i] = FAKE_SUB_ID_BASE + i;
        }
        when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(subIds);
    }

    private void processAllMessages() {
        while (!mLooper.getLooper().getQueue().isIdle()) {
            mLooper.processAllMessages();
        }
    }

    private static void logd(String str) {
        Log.d(TAG, str);
    }
}
