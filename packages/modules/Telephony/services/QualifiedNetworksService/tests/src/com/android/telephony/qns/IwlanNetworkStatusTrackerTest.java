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

package com.android.telephony.qns;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.location.Country;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TelephonyNetworkSpecifier;
import android.net.vcn.VcnTransportInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(JUnit4.class)
public class IwlanNetworkStatusTrackerTest extends QnsTest {

    private static final int INVALID_SUB_ID = -1;
    private static final int CURRENT_SLOT_ID = 0;
    private static final int CURRENT_SUB_ID = 0;
    private static final int ACTIVE_DATA_SUB_ID = 1;
    private IwlanNetworkStatusTracker mIwlanNetworkStatusTracker;
    private IwlanNetworkStatusTracker.IwlanAvailabilityInfo mIwlanAvailabilityInfo;
    private final TestHandler[] mHandlers = new TestHandler[2];
    private Handler mEventHandler;
    private final HandlerThread[] mHandlerThreads = new HandlerThread[2];
    @Mock private Network mMockNetwork;
    private MockitoSession mMockSession;
    private NetworkCapabilities mNetworkCapabilities;

    private class TestHandlerThread extends HandlerThread {
        TestHandlerThread() {
            super("");
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            setReady(true);
        }
    }

    private class TestHandler extends Handler {
        private int mSlotId;
        CountDownLatch mLatch;

        TestHandler(HandlerThread ht, int slotId) {
            super(ht.getLooper());
            mSlotId = slotId;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            QnsAsyncResult ar = (QnsAsyncResult) msg.obj;
            mIwlanAvailabilityInfo = (IwlanNetworkStatusTracker.IwlanAvailabilityInfo) ar.mResult;
            if (mLatch != null) {
                mLatch.countDown();
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mMockSession = mockitoSession().mockStatic(QnsUtils.class).startMocking();
        mHandlerThreads[0] = new TestHandlerThread();
        mHandlerThreads[0].start();
        waitUntilReady();
        mHandlerThreads[1] = new TestHandlerThread();
        mHandlerThreads[1].start();
        waitUntilReady();
        mIwlanNetworkStatusTracker = new IwlanNetworkStatusTracker(sMockContext);
        mIwlanNetworkStatusTracker.initBySlotIndex(
                mMockQnsConfigManager,
                mMockQnsEventDispatcher,
                mMockQnsImsManager,
                mMockQnsTelephonyListener,
                0);
        mIwlanNetworkStatusTracker.initBySlotIndex(
                mMockQnsConfigManager,
                mMockQnsEventDispatcher,
                mMockQnsImsManager,
                mMockQnsTelephonyListener,
                1);
        mHandlers[0] = new TestHandler(mHandlerThreads[0], 0);
        mHandlers[1] = new TestHandler(mHandlerThreads[1], 1);
    }

    @After
    public void tearDown() {
        for (HandlerThread handlerThread : mHandlerThreads) {
            if (handlerThread != null) {
                handlerThread.quit();
            }
        }
        if (mIwlanNetworkStatusTracker != null) {
            mIwlanNetworkStatusTracker.unregisterIwlanNetworksChanged(0, mHandlers[0]);
            mIwlanNetworkStatusTracker.unregisterIwlanNetworksChanged(1, mHandlers[1]);
            mIwlanNetworkStatusTracker.close();
        }
        mIwlanAvailabilityInfo = null;
        mMockSession.finishMocking();
    }

    @Test
    public void testHandleMessage_InvalidSubID() throws InterruptedException {
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        mHandlers[0].mLatch = new CountDownLatch(1);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(CURRENT_SLOT_ID, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(200, TimeUnit.MILLISECONDS));

        // If sim is invalid, no event is notified because of already info was notified.
        mHandlers[0].mLatch = new CountDownLatch(1);
        prepareNetworkCapabilitiesForTest(INVALID_SUB_ID, false /* isVcn */);
        mIwlanNetworkStatusTracker.onCrossSimEnabledEvent(true, 0);
        assertFalse(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(mIwlanAvailabilityInfo);
        assertFalse(mIwlanAvailabilityInfo.isCrossWfc());
    }

    @Test
    public void testHandleMessage_ValidSubID() throws InterruptedException {
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        mHandlers[0].mLatch = new CountDownLatch(1);
        lenient().when(QnsUtils.getSubId(sMockContext, CURRENT_SLOT_ID)).thenReturn(CURRENT_SUB_ID);
        lenient().when(QnsUtils.isCrossSimCallingEnabled(mMockQnsImsManager)).thenReturn(true);
        lenient().when(QnsUtils.isDefaultDataSubs(CURRENT_SLOT_ID)).thenReturn(false);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(CURRENT_SLOT_ID, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(200, TimeUnit.MILLISECONDS));
        prepareNetworkCapabilitiesForTest(ACTIVE_DATA_SUB_ID, false /* isVcn */);
        mIwlanNetworkStatusTracker.onCrossSimEnabledEvent(true, CURRENT_SLOT_ID);
        mIwlanNetworkStatusTracker.onIwlanServiceStateChanged(CURRENT_SLOT_ID, true);
        waitForLastHandlerAction(mEventHandler);
        assertNotNull(mIwlanAvailabilityInfo);
        assertTrue(mIwlanAvailabilityInfo.isCrossWfc());
    }

    @Test
    public void testHandleMessage_ValidSubID_DDS_over_nDDS() throws InterruptedException {
        // Verifies that DDS can also establish cross-sim over nDDS, as long as nDDS is the current
        // active data sub.
        mHandlers[0].mLatch = new CountDownLatch(1);
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        lenient().when(QnsUtils.getSubId(sMockContext, CURRENT_SLOT_ID)).thenReturn(CURRENT_SUB_ID);
        lenient().when(QnsUtils.isCrossSimCallingEnabled(mMockQnsImsManager)).thenReturn(true);
        lenient().when(QnsUtils.isDefaultDataSubs(CURRENT_SLOT_ID)).thenReturn(false);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(CURRENT_SLOT_ID, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(200, TimeUnit.MILLISECONDS));
        prepareNetworkCapabilitiesForTest(ACTIVE_DATA_SUB_ID, false /* isVcn */);
        mIwlanNetworkStatusTracker.onCrossSimEnabledEvent(true, CURRENT_SLOT_ID);
        mIwlanNetworkStatusTracker.onIwlanServiceStateChanged(CURRENT_SLOT_ID, true);
        waitForLastHandlerAction(mEventHandler);
        assertNotNull(mIwlanAvailabilityInfo);
        assertTrue(mIwlanAvailabilityInfo.isCrossWfc());
    }

    @Test
    public void testHandleMessage_VcnWithValidSubID() throws InterruptedException {
        mHandlers[0].mLatch = new CountDownLatch(1);
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        lenient().when(QnsUtils.getSubId(sMockContext, CURRENT_SLOT_ID)).thenReturn(CURRENT_SUB_ID);
        lenient().when(QnsUtils.isCrossSimCallingEnabled(mMockQnsImsManager)).thenReturn(true);
        lenient().when(QnsUtils.isDefaultDataSubs(CURRENT_SLOT_ID)).thenReturn(false);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(CURRENT_SLOT_ID, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(200, TimeUnit.MILLISECONDS));
        prepareNetworkCapabilitiesForTest(ACTIVE_DATA_SUB_ID, true /* isVcn */);
        mIwlanNetworkStatusTracker.onCrossSimEnabledEvent(true, CURRENT_SLOT_ID);
        mIwlanNetworkStatusTracker.onIwlanServiceStateChanged(CURRENT_SLOT_ID, true);
        waitForLastHandlerAction(mEventHandler);
        assertNotNull(mIwlanAvailabilityInfo);
        assertTrue(mIwlanAvailabilityInfo.isCrossWfc());
    }

    private void prepareNetworkCapabilitiesForTest(int subId, boolean isVcn) {
        NetworkCapabilities.Builder builder =
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (isVcn) {
            builder.setTransportInfo(new VcnTransportInfo(subId));
        } else {
            builder.setNetworkSpecifier(new TelephonyNetworkSpecifier(subId));
        }
        mNetworkCapabilities = builder.build();
        when(mMockConnectivityManager.getActiveNetwork()).thenReturn(mMockNetwork);
        when(mMockConnectivityManager.getNetworkCapabilities(mMockNetwork))
                .thenReturn(mNetworkCapabilities);
    }

    @Test
    public void testHandleMessage_DisableCrossSim() throws InterruptedException {
        testHandleMessage_ValidSubID();
        mHandlers[0].mLatch = new CountDownLatch(1);
        lenient().when(QnsUtils.isCrossSimCallingEnabled(mMockQnsImsManager)).thenReturn(false);
        lenient().when(QnsUtils.isDefaultDataSubs(CURRENT_SLOT_ID)).thenReturn(false);
        mIwlanNetworkStatusTracker.onCrossSimEnabledEvent(false, 0);
        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(mIwlanAvailabilityInfo);
        assertFalse(mIwlanAvailabilityInfo.isCrossWfc());
    }

    private ConnectivityManager.NetworkCallback setupNetworkCallback() {
        ArgumentCaptor<ConnectivityManager.NetworkCallback> callbackArg =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
        verify(mMockConnectivityManager, atLeastOnce())
                .registerDefaultNetworkCallback(callbackArg.capture(), isA(Handler.class));
        ConnectivityManager.NetworkCallback networkCallback = callbackArg.getValue();
        assertNotNull(networkCallback);
        return networkCallback;
    }

    @Test
    public void testDefaultNetworkCallback_Wifi() throws InterruptedException {
        testDefaultNetworkCallback(true, true);
    }

    @Test
    public void testDefaultNetworkCallback_Cellular() throws InterruptedException {
        testDefaultNetworkCallback(false, false);
    }

    public void testDefaultNetworkCallback(boolean isWifi, boolean isIwlanRegistered)
            throws InterruptedException {
        mHandlers[0].mLatch = new CountDownLatch(1);
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(0, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(200, TimeUnit.MILLISECONDS));
        ConnectivityManager.NetworkCallback networkCallback = setupNetworkCallback();
        TelephonyNetworkSpecifier tns = new TelephonyNetworkSpecifier(0);
        mNetworkCapabilities =
                new NetworkCapabilities.Builder()
                        .addTransportType(
                                isWifi
                                        ? NetworkCapabilities.TRANSPORT_WIFI
                                        : NetworkCapabilities.TRANSPORT_CELLULAR)
                        .setNetworkSpecifier(tns)
                        .build();
        when(mMockConnectivityManager.getNetworkCapabilities(mMockNetwork))
                .thenReturn(mNetworkCapabilities);
        networkCallback.onAvailable(mMockNetwork);
        if (isWifi) {
            mIwlanNetworkStatusTracker.onIwlanServiceStateChanged(0, isIwlanRegistered);
        }
        waitForLastHandlerAction(mEventHandler);
        verifyIwlanAvailabilityInfo(isWifi, isIwlanRegistered);

        networkCallback.onCapabilitiesChanged(mMockNetwork, mNetworkCapabilities);
        // no callback is expected since onAvailable already reported information
        waitForLastHandlerAction(mEventHandler);
        verifyIwlanAvailabilityInfo(isWifi, isIwlanRegistered);
    }

    private void verifyIwlanAvailabilityInfo(boolean isWifi, boolean isIwlanRegistered) {
        assertNotNull(mIwlanAvailabilityInfo);
        if (isWifi && isIwlanRegistered) {
            assertTrue(mIwlanAvailabilityInfo.getIwlanAvailable());
        } else {
            assertFalse(mIwlanAvailabilityInfo.getIwlanAvailable());
        }
        assertFalse(mIwlanAvailabilityInfo.isCrossWfc());
    }

    @Test
    public void testDefaultNetworkCallback_onLost() throws InterruptedException {
        testDefaultNetworkCallback(true, true);
        mHandlers[0].mLatch = new CountDownLatch(1);
        ConnectivityManager.NetworkCallback networkCallback = setupNetworkCallback();
        networkCallback.onLost(mMockNetwork);
        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(mIwlanAvailabilityInfo);
        assertFalse(mIwlanAvailabilityInfo.getIwlanAvailable());
    }

    @Test
    public void testRegisterIwlanNetworksChanged() throws Exception {
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        mHandlers[0].mLatch = new CountDownLatch(1);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(0, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(mIwlanAvailabilityInfo);
        assertFalse(mIwlanAvailabilityInfo.getIwlanAvailable());
        assertFalse(mIwlanAvailabilityInfo.isCrossWfc());
    }

    @Test
    public void testRegisterIwlanNetworksChanged_Validate() throws Exception {
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(0);
        when(mMockQnsConfigManager.blockIpv6OnlyWifi()).thenReturn(false);
        mHandlers[0].mLatch = new CountDownLatch(2);
        mHandlers[1].mLatch = new CountDownLatch(1);

        // Count down 1 mHandlers[0].mLatch
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(0, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);

        mIwlanNetworkStatusTracker.mLastIwlanAvailabilityInfo.put(0,
                mIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false));

        // When registering a new handler, if the IwlanAvailabilityInfo information is updated,
        // the existing one is also notified.
        // Count down 1 mHandlers[1].mLatch and Count down 1 mHandlers[0].mLatch as well
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(0, mHandlers[1], 1);
        waitForLastHandlerAction(mEventHandler);

        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(mHandlers[1].mLatch.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testUnregisterIwlanNetworksChanged() throws InterruptedException {
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        mHandlers[0].mLatch = new CountDownLatch(1);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(CURRENT_SLOT_ID, mHandlers[0], 1);
        waitForLastHandlerAction(mEventHandler);
        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        ConnectivityManager.NetworkCallback networkCallback = setupNetworkCallback();
        TelephonyNetworkSpecifier tns = new TelephonyNetworkSpecifier(CURRENT_SLOT_ID);
        mNetworkCapabilities =
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(tns)
                        .build();
        when(mMockConnectivityManager.getNetworkCapabilities(mMockNetwork))
                .thenReturn(mNetworkCapabilities);
        mHandlers[0].mLatch = new CountDownLatch(1);
        mIwlanNetworkStatusTracker.unregisterIwlanNetworksChanged(CURRENT_SLOT_ID, mHandlers[0]);
        waitForLastHandlerAction(mEventHandler);
        networkCallback.onAvailable(mMockNetwork);
        assertFalse(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testIsInternationalRoaming() {
        boolean isInternationalRoaming;
        mEventHandler = mIwlanNetworkStatusTracker.mHandlerSparseArray.get(CURRENT_SLOT_ID);
        when(mMockTelephonyManager.getSimCountryIso()).thenReturn("CA");
        ArgumentCaptor<Consumer<Country>> capture = ArgumentCaptor.forClass(Consumer.class);
        verify(mMockCountryDetector)
                .registerCountryDetectorCallback(isA(Executor.class), capture.capture());
        Consumer<Country> countryConsumer = capture.getValue();

        countryConsumer.accept(new Country("US", Country.COUNTRY_SOURCE_LOCATION));
        waitForLastHandlerAction(mEventHandler);

        isInternationalRoaming = mIwlanNetworkStatusTracker.isInternationalRoaming(anyInt());
        assertTrue(isInternationalRoaming);

        countryConsumer.accept(new Country("CA", Country.COUNTRY_SOURCE_LOCATION));
        waitForLastHandlerAction(mEventHandler);

        isInternationalRoaming = mIwlanNetworkStatusTracker.isInternationalRoaming(anyInt());
        assertFalse(isInternationalRoaming);
    }

    @Test
    public void testWifiDisabling() throws InterruptedException {
        testDefaultNetworkCallback(true, true);
        mHandlers[0].mLatch = new CountDownLatch(1);
        mIwlanNetworkStatusTracker.onWifiDisabling();
        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(mIwlanAvailabilityInfo);
        assertFalse(mIwlanAvailabilityInfo.getIwlanAvailable());
    }

    @Test
    public void testDefaultNetworkCallback_IwlanNotRegistered() throws InterruptedException {
        testDefaultNetworkCallback(true, false);
    }

    @Test
    public void testWifiToggleQuickOffOn() throws InterruptedException {
        testWifiDisabling();
        mHandlers[0].mLatch = new CountDownLatch(1);
        mIwlanNetworkStatusTracker.onWifiEnabled();
        assertTrue(mHandlers[0].mLatch.await(100, TimeUnit.MILLISECONDS));
        verifyIwlanAvailabilityInfo(true, true);
    }

    @Test
    public void testWifiToggleQuickOffOn_inCrossSimEnabledCondition() throws InterruptedException {
        mIwlanNetworkStatusTracker.onWifiEnabled();
        testHandleMessage_ValidSubID();
        mIwlanNetworkStatusTracker.onWifiDisabling();
        assertNotNull(mIwlanAvailabilityInfo);
        assertTrue(mIwlanAvailabilityInfo.isCrossWfc());
        mIwlanNetworkStatusTracker.onWifiEnabled();
        assertNotNull(mIwlanAvailabilityInfo);
        assertTrue(mIwlanAvailabilityInfo.isCrossWfc());
    }
}
