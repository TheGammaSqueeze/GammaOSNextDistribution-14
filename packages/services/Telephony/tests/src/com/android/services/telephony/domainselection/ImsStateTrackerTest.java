/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.services.telephony.domainselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.BarringInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.stub.ImsRegistrationImplBase;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for ImsStateTracker.
 */
@RunWith(AndroidJUnit4.class)
public class ImsStateTrackerTest {
    private static final int SLOT_0 = 0;
    private static final int SUB_1 = 1;
    private static final int SUB_2 = 2;
    private static final long TIMEOUT_MS = 100;
    private static final long MSG_PROCESS_DELAY_MS = 10;

    @Mock private ImsMmTelManager mMmTelManager;
    @Mock private ImsMmTelManager mMmTelManager2;
    @Mock private ImsStateTracker.BarringInfoListener mBarringInfoListener;
    @Mock private ImsStateTracker.ServiceStateListener mServiceStateListener;
    @Mock private ImsStateTracker.ImsStateListener mImsStateListener;
    @Mock private ServiceState mServiceState;

    private Context mContext;
    private Looper mLooper;
    private BarringInfo mBarringInfo = new BarringInfo();
    private ImsStateTracker mImsStateTracker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext() {
            @Override
            public String getSystemServiceName(Class<?> serviceClass) {
                if (serviceClass == ImsManager.class) {
                    return Context.TELEPHONY_IMS_SERVICE;
                }
                return super.getSystemServiceName(serviceClass);
            }
        };

        HandlerThread handlerThread = new HandlerThread(
                ImsStateTrackerTest.class.getSimpleName());
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mImsStateTracker = new ImsStateTracker(mContext, SLOT_0, mLooper);

        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        when(imsManager.getImsMmTelManager(eq(SUB_1))).thenReturn(mMmTelManager);
        when(imsManager.getImsMmTelManager(eq(SUB_2))).thenReturn(mMmTelManager2);
    }

    @After
    public void tearDown() throws Exception {
        mImsStateTracker.destroy();
        mImsStateTracker = null;
        mMmTelManager = null;

        if (mLooper != null) {
            mLooper.quit();
            mLooper = null;
        }
    }

    @Test
    @SmallTest
    public void testInit() {
        assertEquals(SLOT_0, mImsStateTracker.getSlotId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, mImsStateTracker.getSubId());
    }

    @Test
    @SmallTest
    public void testStartWithInvalidSubId() {
        mImsStateTracker.start(SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, mImsStateTracker.getSubId());
        assertTrue(isImsStateUnavailable());
    }

    @Test
    @SmallTest
    public void testStart() throws ImsException {
        mImsStateTracker.start(SUB_1);

        assertEquals(SUB_1, mImsStateTracker.getSubId());
        assertTrue(isImsStateInit());
        verify(mMmTelManager).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));
    }

    @Test
    @SmallTest
    public void testStartWithDifferentSubId() throws ImsException {
        mImsStateTracker.start(SUB_1);

        assertEquals(SUB_1, mImsStateTracker.getSubId());
        assertTrue(isImsStateInit());

        mImsStateTracker.start(SUB_2);

        assertEquals(SUB_2, mImsStateTracker.getSubId());
        assertTrue(isImsStateInit());
        verify(mMmTelManager).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));
        verify(mMmTelManager).unregisterImsStateCallback(
                any(ImsStateCallback.class));
        verify(mMmTelManager2).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));
    }

    @Test
    @SmallTest
    public void testStartWithSameSubId() throws ImsException {
        mImsStateTracker.start(SUB_1);

        assertEquals(SUB_1, mImsStateTracker.getSubId());
        assertTrue(isImsStateInit());

        mImsStateTracker.start(SUB_1);

        assertEquals(SUB_1, mImsStateTracker.getSubId());
        assertTrue(isImsStateInit());
        verify(mMmTelManager).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));
        verify(mMmTelManager, never()).unregisterImsStateCallback(
                any(ImsStateCallback.class));
    }

    @Test
    @SmallTest
    public void testStartWhenRegisteringCallbacksThrowException() throws ImsException {
        doAnswer((invocation) -> {
            throw new ImsException("Intended exception for ImsStateCallback.");
        }).when(mMmTelManager).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));

        mImsStateTracker.start(SUB_1);

        assertEquals(SUB_1, mImsStateTracker.getSubId());

        mImsStateTracker.start(SUB_2);

        assertEquals(SUB_2, mImsStateTracker.getSubId());

        verify(mMmTelManager, never()).unregisterImsStateCallback(
                any(ImsStateCallback.class));
    }

    @Test
    @SmallTest
    public void testStartAfterUnavailableWithReasonSubscriptionInactive() throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE);

        mImsStateTracker.start(SUB_1);

        assertTrue(isImsStateInit());
        // One is invoked in setUpImsStateCallback and the other is invoked in start(int).
        verify(mMmTelManager, times(2)).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));
        // ImsStateCallback has already been set to null when onUnavailable is called.
        verify(mMmTelManager, never()).unregisterImsStateCallback(
                any(ImsStateCallback.class));
    }

    @Test
    @SmallTest
    public void testUpdateServiceStateBeforeAddingListener() {
        mImsStateTracker.updateServiceState(mServiceState);
        mImsStateTracker.addServiceStateListener(mServiceStateListener);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(mServiceStateListener).onServiceStateUpdated(eq(mServiceState));

        mImsStateTracker.removeServiceStateListener(mServiceStateListener);
        ServiceState ss = Mockito.mock(ServiceState.class);
        mImsStateTracker.updateServiceState(ss);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verifyNoMoreInteractions(mServiceStateListener);
    }

    @Test
    @SmallTest
    public void testUpdateServiceStateAfterAddingListener() {
        mImsStateTracker.addServiceStateListener(mServiceStateListener);
        mImsStateTracker.updateServiceState(mServiceState);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(mServiceStateListener).onServiceStateUpdated(eq(mServiceState));

        mImsStateTracker.removeServiceStateListener(mServiceStateListener);
        ServiceState ss = Mockito.mock(ServiceState.class);
        mImsStateTracker.updateServiceState(ss);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verifyNoMoreInteractions(mServiceStateListener);
    }

    @Test
    @SmallTest
    public void testAddAndRemoveServiceStateListener() {
        mImsStateTracker.getHandler().post(() -> {
            SystemClock.sleep(MSG_PROCESS_DELAY_MS);
        });
        mImsStateTracker.updateServiceState(mServiceState);
        mImsStateTracker.addServiceStateListener(mServiceStateListener);
        mImsStateTracker.removeServiceStateListener(mServiceStateListener);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(mServiceStateListener, never()).onServiceStateUpdated(eq(mServiceState));
    }

    @Test
    @SmallTest
    public void testUpdateBarringInfoBeforeAddingListener() {
        mImsStateTracker.updateBarringInfo(mBarringInfo);
        mImsStateTracker.addBarringInfoListener(mBarringInfoListener);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(mBarringInfoListener).onBarringInfoUpdated(eq(mBarringInfo));

        mImsStateTracker.removeBarringInfoListener(mBarringInfoListener);
        BarringInfo bi = new BarringInfo();
        mImsStateTracker.updateBarringInfo(bi);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verifyNoMoreInteractions(mBarringInfoListener);
    }

    @Test
    @SmallTest
    public void testUpdateBarringInfoAfterAddingListener() {
        mImsStateTracker.addBarringInfoListener(mBarringInfoListener);
        mImsStateTracker.updateBarringInfo(mBarringInfo);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(mBarringInfoListener).onBarringInfoUpdated(eq(mBarringInfo));

        mImsStateTracker.removeBarringInfoListener(mBarringInfoListener);
        BarringInfo bi = new BarringInfo();
        mImsStateTracker.updateBarringInfo(bi);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verifyNoMoreInteractions(mBarringInfoListener);
    }

    @Test
    @SmallTest
    public void testAddAndRemoveBarringInfoListener() {
        mImsStateTracker.getHandler().post(() -> {
            SystemClock.sleep(MSG_PROCESS_DELAY_MS);
        });
        mImsStateTracker.updateBarringInfo(mBarringInfo);
        mImsStateTracker.addBarringInfoListener(mBarringInfoListener);
        mImsStateTracker.removeBarringInfoListener(mBarringInfoListener);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(mBarringInfoListener, never()).onBarringInfoUpdated(eq(mBarringInfo));
    }

    @Test
    @SmallTest
    public void testNotifyImsStateCallbackOnAvailable() throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onAvailable();

        assertTrue(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(mImsStateTracker.isImsStateReady());
        verify(mMmTelManager).registerImsRegistrationCallback(
                any(Executor.class), any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager).registerMmTelCapabilityCallback(
                any(Executor.class), any(ImsMmTelManager.CapabilityCallback.class));
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    @SmallTest
    public void testNotifyImsStateCallbackOnUnavailableWithReasonUnknownPermanentError()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR);

        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    @SmallTest
    public void testNotifyImsStateCallbackOnUnavailableWithReasonNoImsServiceConfigured()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED);

        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    public void testNotifyImsStateCallbackOnUnavailableWithReasonUnknownTemporaryError()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(isImsStateUnavailable());
        assertFalse(mImsStateTracker.isImsStateReady());

        waitForHandlerActionDelayed(mImsStateTracker.getHandler(),
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS,
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS + TIMEOUT_MS);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    public void testNotifyImsStateCallbackOnUnavailableWithReasonImsServiceNotReady()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_IMS_SERVICE_NOT_READY);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(isImsStateUnavailable());
        assertFalse(mImsStateTracker.isImsStateReady());

        waitForHandlerActionDelayed(mImsStateTracker.getHandler(),
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS,
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS + TIMEOUT_MS);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    public void testNotifyImsStateCallbackOnUnavailableWithReasonImsServiceDisconnected()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(isImsStateUnavailable());
        assertFalse(mImsStateTracker.isImsStateReady());

        waitForHandlerActionDelayed(mImsStateTracker.getHandler(),
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS,
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS + TIMEOUT_MS);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mMmTelManager, never()).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager, never()).unregisterMmTelCapabilityCallback(
                any(ImsMmTelManager.CapabilityCallback.class));
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    @SmallTest
    public void testNotifyImsStateCallbackOnUnavailableWithReasonSubscriptionInactive()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mMmTelManager, never()).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager, never()).unregisterMmTelCapabilityCallback(
                any(ImsMmTelManager.CapabilityCallback.class));
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    public void testNotifyImsStateCallbackOnAvailableUnavailableWithReasonImsServiceDisconnected()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onAvailable();
        callback.onUnavailable(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(isImsStateUnavailable());
        assertFalse(mImsStateTracker.isImsStateReady());

        waitForHandlerActionDelayed(mImsStateTracker.getHandler(),
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS,
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS + TIMEOUT_MS);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mMmTelManager).registerImsRegistrationCallback(
                any(Executor.class), any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager).registerMmTelCapabilityCallback(
                any(Executor.class), any(ImsMmTelManager.CapabilityCallback.class));
        verify(mMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager).unregisterMmTelCapabilityCallback(
                any(ImsMmTelManager.CapabilityCallback.class));
        verify(mImsStateListener, times(2)).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    public void testNotifyImsStateCallbackOnUnavailableAvailableWithReasonImsServiceDisconnected()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onUnavailable(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED);
        callback.onAvailable();

        assertTrue(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(isImsStateUnavailable());
        assertFalse(mImsStateTracker.isImsStateReady());

        waitForHandlerActionDelayed(mImsStateTracker.getHandler(),
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS,
                ImsStateTracker.MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS + TIMEOUT_MS);

        assertTrue(mImsStateTracker.isMmTelFeatureAvailable());
        assertFalse(isImsStateUnavailable());
        assertFalse(mImsStateTracker.isImsStateReady());
        verify(mMmTelManager).registerImsRegistrationCallback(
                any(Executor.class), any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager).registerMmTelCapabilityCallback(
                any(Executor.class), any(ImsMmTelManager.CapabilityCallback.class));
        verify(mMmTelManager, never()).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager, never()).unregisterMmTelCapabilityCallback(
                any(ImsMmTelManager.CapabilityCallback.class));
        verify(mImsStateListener).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    @SmallTest
    public void testNotifyImsStateCallbackOnAvailableUnavailableWithReasonSubscriptionInactive()
            throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onAvailable();
        callback.onUnavailable(ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE);

        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        assertTrue(isImsStateUnavailable());
        assertTrue(mImsStateTracker.isImsStateReady());
        verify(mMmTelManager).registerImsRegistrationCallback(
                any(Executor.class), any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager).registerMmTelCapabilityCallback(
                any(Executor.class), any(ImsMmTelManager.CapabilityCallback.class));
        verify(mMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        verify(mMmTelManager).unregisterMmTelCapabilityCallback(
                any(ImsMmTelManager.CapabilityCallback.class));
        verify(mImsStateListener, times(2)).onImsMmTelFeatureAvailableChanged();
    }

    @Test
    @SmallTest
    public void testNotifyImsRegistrationCallbackOnRegistered() throws ImsException {
        RegistrationManager.RegistrationCallback callback = setUpImsRegistrationCallback();
        callback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).build());

        // It's false because the MMTEL capabilities are not updated yet.
        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertFalse(mImsStateTracker.isImsRegisteredOverWlan());
        assertFalse(mImsStateTracker.isImsRegisteredOverCrossSim());
        assertEquals(AccessNetworkType.EUTRAN, mImsStateTracker.getImsAccessNetworkType());

        callback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_NR).build());

        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertFalse(mImsStateTracker.isImsRegisteredOverWlan());
        assertFalse(mImsStateTracker.isImsRegisteredOverCrossSim());
        assertEquals(AccessNetworkType.NGRAN, mImsStateTracker.getImsAccessNetworkType());

        callback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).build());

        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertTrue(mImsStateTracker.isImsRegisteredOverWlan());
        assertFalse(mImsStateTracker.isImsRegisteredOverCrossSim());
        assertEquals(AccessNetworkType.IWLAN, mImsStateTracker.getImsAccessNetworkType());

        callback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM).build());

        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertTrue(mImsStateTracker.isImsRegisteredOverWlan());
        assertTrue(mImsStateTracker.isImsRegisteredOverCrossSim());
        assertEquals(AccessNetworkType.IWLAN, mImsStateTracker.getImsAccessNetworkType());

        callback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_NONE).build());

        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertFalse(mImsStateTracker.isImsRegisteredOverWlan());
        assertFalse(mImsStateTracker.isImsRegisteredOverCrossSim());
        assertEquals(AccessNetworkType.UNKNOWN, mImsStateTracker.getImsAccessNetworkType());

        verify(mImsStateListener, times(5)).onImsRegistrationStateChanged();
    }

    @Test
    @SmallTest
    public void testNotifyImsRegistrationCallbackOnUnregistered() throws ImsException {
        RegistrationManager.RegistrationCallback callback = setUpImsRegistrationCallback();
        callback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).build());

        // It's false because the MMTEL capabilities are not updated yet.
        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertFalse(mImsStateTracker.isImsRegisteredOverWlan());
        assertEquals(AccessNetworkType.EUTRAN, mImsStateTracker.getImsAccessNetworkType());

        callback.onUnregistered(new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 0, null));

        // When IMS is unregistered, the MMTEL capability is also reset.
        assertTrue(mImsStateTracker.isImsStateReady());
        assertFalse(mImsStateTracker.isImsRegistered());
        assertFalse(mImsStateTracker.isImsRegisteredOverWlan());
        assertEquals(AccessNetworkType.UNKNOWN, mImsStateTracker.getImsAccessNetworkType());

        verify(mImsStateListener, times(2)).onImsRegistrationStateChanged();
    }

    @Test
    @SmallTest
    public void testNotifyMmTelCapabilityCallbackOnCapabilitiesStatusChanged() throws ImsException {
        ImsMmTelManager.CapabilityCallback callback = setUpMmTelCapabilityCallback();

        assertFalse(mImsStateTracker.isImsVoiceCapable());
        assertFalse(mImsStateTracker.isImsVideoCapable());
        assertFalse(mImsStateTracker.isImsSmsCapable());
        assertFalse(mImsStateTracker.isImsUtCapable());

        MmTelCapabilities capabilities = new MmTelCapabilities(
                MmTelCapabilities.CAPABILITY_TYPE_VOICE
                | MmTelCapabilities.CAPABILITY_TYPE_VIDEO
                | MmTelCapabilities.CAPABILITY_TYPE_SMS
                | MmTelCapabilities.CAPABILITY_TYPE_UT
            );
        callback.onCapabilitiesStatusChanged(capabilities);

        assertTrue(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsVoiceCapable());
        assertTrue(mImsStateTracker.isImsVideoCapable());
        assertTrue(mImsStateTracker.isImsSmsCapable());
        assertTrue(mImsStateTracker.isImsUtCapable());

        capabilities = new MmTelCapabilities();
        callback.onCapabilitiesStatusChanged(capabilities);

        assertTrue(mImsStateTracker.isImsStateReady());
        assertFalse(mImsStateTracker.isImsVoiceCapable());
        assertFalse(mImsStateTracker.isImsVideoCapable());
        assertFalse(mImsStateTracker.isImsSmsCapable());
        assertFalse(mImsStateTracker.isImsUtCapable());

        verify(mImsStateListener, times(2)).onImsMmTelCapabilitiesChanged();
    }

    @Test
    @SmallTest
    public void testAddImsStateListenerWhenImsStateReady() throws ImsException {
        ImsMmTelManager.CapabilityCallback callback = setUpMmTelCapabilityCallback();

        MmTelCapabilities capabilities = new MmTelCapabilities(
                MmTelCapabilities.CAPABILITY_TYPE_VOICE
                | MmTelCapabilities.CAPABILITY_TYPE_VIDEO
                | MmTelCapabilities.CAPABILITY_TYPE_SMS
                | MmTelCapabilities.CAPABILITY_TYPE_UT
            );
        callback.onCapabilitiesStatusChanged(capabilities);

        ImsStateTracker.ImsStateListener listener =
                Mockito.mock(ImsStateTracker.ImsStateListener.class);
        mImsStateTracker.addImsStateListener(listener);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(listener).onImsMmTelFeatureAvailableChanged();
        verify(listener).onImsRegistrationStateChanged();
        verify(listener).onImsMmTelCapabilitiesChanged();
    }

    @Test
    @SmallTest
    public void testAddAndRemoveImsStateListenerWhenImsStateReady() throws ImsException {
        ImsMmTelManager.CapabilityCallback callback = setUpMmTelCapabilityCallback();

        MmTelCapabilities capabilities = new MmTelCapabilities(
                MmTelCapabilities.CAPABILITY_TYPE_VOICE
                | MmTelCapabilities.CAPABILITY_TYPE_VIDEO
                | MmTelCapabilities.CAPABILITY_TYPE_SMS
                | MmTelCapabilities.CAPABILITY_TYPE_UT
            );
        callback.onCapabilitiesStatusChanged(capabilities);

        Handler handler = new Handler(mLooper);
        ImsStateTracker.ImsStateListener listener =
                Mockito.mock(ImsStateTracker.ImsStateListener.class);
        handler.post(() -> {
            mImsStateTracker.addImsStateListener(listener);
            mImsStateTracker.removeImsStateListener(listener);
        });
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        verify(listener, never()).onImsMmTelFeatureAvailableChanged();
        verify(listener, never()).onImsRegistrationStateChanged();
        verify(listener, never()).onImsMmTelCapabilitiesChanged();
    }

    private ImsStateCallback setUpImsStateCallback() throws ImsException {
        mImsStateTracker.start(SUB_1);
        mImsStateTracker.addImsStateListener(mImsStateListener);
        waitForHandlerAction(mImsStateTracker.getHandler(), TIMEOUT_MS);

        assertEquals(SUB_1, mImsStateTracker.getSubId());
        assertFalse(mImsStateTracker.isMmTelFeatureAvailable());
        ArgumentCaptor<ImsStateCallback> callbackCaptor =
                ArgumentCaptor.forClass(ImsStateCallback.class);
        verify(mMmTelManager).registerImsStateCallback(
                any(Executor.class), callbackCaptor.capture());

        ImsStateCallback imsStateCallback = callbackCaptor.getValue();
        assertNotNull(imsStateCallback);
        return imsStateCallback;
    }

    private RegistrationManager.RegistrationCallback setUpImsRegistrationCallback()
            throws ImsException {
        ImsStateCallback imsStateCallback = setUpImsStateCallback();
        imsStateCallback.onAvailable();

        assertTrue(mImsStateTracker.isMmTelFeatureAvailable());
        ArgumentCaptor<RegistrationManager.RegistrationCallback> callbackCaptor =
                ArgumentCaptor.forClass(RegistrationManager.RegistrationCallback.class);
        verify(mMmTelManager).registerImsRegistrationCallback(
                any(Executor.class), callbackCaptor.capture());

        RegistrationManager.RegistrationCallback registrationCallback = callbackCaptor.getValue();
        assertNotNull(registrationCallback);
        return registrationCallback;
    }

    private ImsMmTelManager.CapabilityCallback setUpMmTelCapabilityCallback()
            throws ImsException {
        RegistrationManager.RegistrationCallback registrationCallback =
                setUpImsRegistrationCallback();
        registrationCallback.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).build());

        assertTrue(mImsStateTracker.isMmTelFeatureAvailable());
        // It's false because the MMTEL capabilities are not updated.
        assertFalse(mImsStateTracker.isImsStateReady());
        assertTrue(mImsStateTracker.isImsRegistered());
        assertFalse(mImsStateTracker.isImsRegisteredOverWlan());
        assertEquals(AccessNetworkType.EUTRAN, mImsStateTracker.getImsAccessNetworkType());
        ArgumentCaptor<ImsMmTelManager.CapabilityCallback> callbackCaptor =
                ArgumentCaptor.forClass(ImsMmTelManager.CapabilityCallback.class);
        verify(mMmTelManager).registerMmTelCapabilityCallback(
                any(Executor.class), callbackCaptor.capture());

        ImsMmTelManager.CapabilityCallback capabilityCallback = callbackCaptor.getValue();
        assertNotNull(capabilityCallback);
        return capabilityCallback;
    }

    private boolean isImsStateUnavailable() {
        return mImsStateTracker.isImsStateReady()
                && !mImsStateTracker.isImsRegistered()
                && !mImsStateTracker.isMmTelFeatureAvailable()
                && !mImsStateTracker.isImsVoiceCapable()
                && !mImsStateTracker.isImsVideoCapable()
                && !mImsStateTracker.isImsSmsCapable()
                && !mImsStateTracker.isImsUtCapable()
                && (AccessNetworkType.UNKNOWN == mImsStateTracker.getImsAccessNetworkType());
    }

    private boolean isImsStateInit() {
        return !mImsStateTracker.isImsStateReady()
                && !mImsStateTracker.isImsRegistered()
                && !mImsStateTracker.isMmTelFeatureAvailable()
                && !mImsStateTracker.isImsVoiceCapable()
                && !mImsStateTracker.isImsVideoCapable()
                && !mImsStateTracker.isImsSmsCapable()
                && !mImsStateTracker.isImsUtCapable()
                && (AccessNetworkType.UNKNOWN == mImsStateTracker.getImsAccessNetworkType());
    }

    private void waitForHandlerAction(Handler h, long timeoutMillis) {
        waitForHandlerActionDelayed(h, 0, timeoutMillis);
    }

    private void waitForHandlerActionDelayed(Handler h, long delayMillis, long timeoutMillis) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.postDelayed(lock::countDown, delayMillis);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }
}
