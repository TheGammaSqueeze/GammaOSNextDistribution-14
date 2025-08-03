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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.data.ApnSetting;
import android.telephony.data.ThrottleStatus;

import com.android.telephony.qns.QualifiedNetworksServiceImpl.QualifiedNetworksInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class QualifiedNetworksServiceImplTest extends QnsTest {

    private static final int TEST_QNS_CONFIGURATION_LOADED = 1;
    private static final int TEST_QUALIFIED_NETWORKS_CHANGED = 2;
    private static final int TEST_QNS_CONFIGURATION_CHANGED = 3;
    private int mSlotIndex = 0;
    @Mock private AccessNetworkEvaluator mMockAne;

    HandlerThread mHandlerThread;
    QualifiedNetworksServiceImpl mQualifiedNetworksService;
    QualifiedNetworksServiceImpl.NetworkAvailabilityProviderImpl mProvider;

    private class TestHandlerThread extends HandlerThread {

        TestHandlerThread() {
            super("");
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            mProvider =
                    (QualifiedNetworksServiceImpl.NetworkAvailabilityProviderImpl)
                            mQualifiedNetworksService.onCreateNetworkAvailabilityProvider(
                                    mSlotIndex);
            setReady(true);
        }
    }

    private class TestQnsComponents extends QnsComponents {

        TestQnsComponents(int slotId) {
            super(
                    sMockContext,
                    mMockCellNetStatusTracker,
                    mMockCellularQm,
                    mMockIwlanNetworkStatusTracker,
                    mMockQnsImsManager,
                    mMockQnsConfigManager,
                    mMockQnsEventDispatcher,
                    mMockQnsProvisioningListener,
                    mMockQnsTelephonyListener,
                    mMockQnsCallStatusTracker,
                    mMockQnsTimer,
                    mMockWifiBm,
                    mMockWifiQm,
                    mMockQnsMetrics,
                    slotId);
        }

        @Override
        public synchronized void createQnsComponents(int slotId) {
            // always use mocked components for testing.
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        when(mMockTelephonyManager.getActiveModemCount()).thenReturn(2);
        mQualifiedNetworksService = new QualifiedNetworksServiceImpl();
        mQualifiedNetworksService.mHandlerThread = new HandlerThread("QnsImplTest");
        mQualifiedNetworksService.mHandlerThread.start();
        mQualifiedNetworksService.mQnsComponents = new TestQnsComponents(mSlotIndex);
        Field f = QualifiedNetworksServiceImpl.class.getDeclaredField("mContext");
        f.setAccessible(true);
        f.set(mQualifiedNetworksService, sMockContext);
    }

    @After
    public void tearDown() {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
        mQualifiedNetworksService.onDestroy();
    }

    private void createNap() {
        setReady(false);
        mHandlerThread = new TestHandlerThread();
        mHandlerThread.start();
        waitUntilReady();
    }

    @Test
    public void testNetworkAvailabilityProviderLifeCycle() {

        // Valid slot
        mSlotIndex = 0;
        createNap();
        assertEquals(1, mQualifiedNetworksService.mProviderMap.size());

        // Closing NAP
        mQualifiedNetworksService.mProviderMap.get(0).close();
        waitForLastHandlerAction(mQualifiedNetworksService.mProviderMap.get(0).mConfigHandler);
        mHandlerThread.quit();

        // recreating NAP instance
        mSlotIndex = 0;
        mQualifiedNetworksService.mQnsComponents = new TestQnsComponents(mSlotIndex);
        createNap();
        waitForLastHandlerAction(mQualifiedNetworksService.mProviderMap.get(0).mConfigHandler);
        assertEquals(1, mQualifiedNetworksService.mProviderMap.size());
        mHandlerThread.quit();

        // Invalid slot
        mSlotIndex = 3;
        mQualifiedNetworksService.mQnsComponents = new TestQnsComponents(mSlotIndex);
        createNap();
        waitForLastHandlerAction(mQualifiedNetworksService.mProviderMap.get(0).mHandler);
        waitForLastHandlerAction(mQualifiedNetworksService.mProviderMap.get(0).mConfigHandler);
        assertEquals(1, mQualifiedNetworksService.mProviderMap.size());
    }

    @Test
    public void testQualifiedNetworksInfo() {
        QualifiedNetworksInfo info =
                new QualifiedNetworksInfo(
                        NetworkCapabilities.NET_CAPABILITY_IMS, new ArrayList<>());
        assertEquals(info.getNetCapability(), NetworkCapabilities.NET_CAPABILITY_IMS);
        assertTrue(info.getAccessNetworkTypes().isEmpty());
        info.setAccessNetworkTypes(List.of(AccessNetworkType.EUTRAN));
        assertEquals(List.of(AccessNetworkType.EUTRAN), info.getAccessNetworkTypes());
        info.setNetCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_MMS, info.getNetCapability());
    }

    @Test
    public void testAneCreation() {
        List<Integer> supportedNetCapabilities1 = new ArrayList<>();
        supportedNetCapabilities1.add(NetworkCapabilities.NET_CAPABILITY_IMS);
        supportedNetCapabilities1.add(NetworkCapabilities.NET_CAPABILITY_EIMS);
        supportedNetCapabilities1.add(NetworkCapabilities.NET_CAPABILITY_MMS);
        supportedNetCapabilities1.add(NetworkCapabilities.NET_CAPABILITY_XCAP);
        List<Integer> supportedNetCapabilities2 = new ArrayList<>();
        supportedNetCapabilities2.add(NetworkCapabilities.NET_CAPABILITY_IMS);
        supportedNetCapabilities2.add(NetworkCapabilities.NET_CAPABILITY_XCAP);

        when(mMockQnsConfigManager.getQnsSupportedNetCapabilities())
                .thenReturn(supportedNetCapabilities1)
                .thenReturn(supportedNetCapabilities2);
        when(mMockQnsConfigManager.getQnsSupportedTransportType(anyInt()))
                .thenAnswer(
                        (invocation) -> {
                            if (NetworkCapabilities.NET_CAPABILITY_XCAP
                                    == (int) invocation.getArgument(0)) {
                                return QnsConstants.TRANSPORT_TYPE_ALLOWED_WWAN;
                            }
                            return QnsConstants.TRANSPORT_TYPE_ALLOWED_BOTH;
                        });

        createNap();
        // registerForConfigurationLoaded is called on different thread.
        waitForLastHandlerAction(mProvider.mConfigHandler);
        mProvider.mConfigHandler.sendEmptyMessage(TEST_QNS_CONFIGURATION_LOADED);
        waitForLastHandlerAction(mProvider.mConfigHandler); // ANEs take time to create.

        assertEquals(3, mProvider.mEvaluators.size());

        mProvider.mConfigHandler.sendEmptyMessage(TEST_QNS_CONFIGURATION_LOADED);
        waitForLastHandlerAction(mProvider.mConfigHandler); // ANEs take time to create.

        assertEquals(1, mProvider.mEvaluators.size());

        mProvider.close();
    }

    @Test
    public void testReportThrottle_Enable() {
        long timer = 10000;
        buildThrottle(mSlotIndex, ApnSetting.TYPE_IMS, timer);
        verify(mMockAne)
                .updateThrottleStatus(true, timer, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testReportThrottle_Disable() {
        long timer = -1;
        buildThrottle(mSlotIndex, ApnSetting.TYPE_IMS, -1);
        verify(mMockAne)
                .updateThrottleStatus(false, timer, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testReportThrottle_WrongSlot() {
        long timer = 10000;
        buildThrottle(1, ApnSetting.TYPE_IMS, timer);
        verify(mMockAne, never()).updateThrottleStatus(anyBoolean(), anyLong(), anyInt());
    }

    @Test
    public void testReportThrottle_NoAne() {
        long timer = 10000;
        buildThrottle(mSlotIndex, ApnSetting.TYPE_XCAP, timer);
        verify(mMockAne, never()).updateThrottleStatus(anyBoolean(), anyLong(), anyInt());
    }

    private void buildThrottle(int slot, int apnType, long timer) {
        ThrottleStatus.Builder builder =
                new ThrottleStatus.Builder()
                        .setSlotIndex(slot)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setApnType(apnType);
        if (timer == -1) {
            builder.setNoThrottle();
        } else {
            builder.setThrottleExpiryTimeMillis(timer);
        }

        createNap();
        mProvider.mEvaluators.put(NetworkCapabilities.NET_CAPABILITY_IMS, mMockAne);
        mProvider.reportThrottleStatusChanged(List.of(builder.build()));
    }

    @Test
    public void testOnEmergencyDataNetworkPreferenceChangedWlan() {
        createNap();
        mProvider.mEvaluators.put(NetworkCapabilities.NET_CAPABILITY_EIMS, mMockAne);
        mProvider.reportEmergencyDataNetworkPreferredTransportChanged(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        verify(mMockAne)
                .onEmergencyPreferredTransportTypeChanged(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

    }

    @Test
    public void testOnEmergencyDataNetworkPreferenceChangedWwan() {
        createNap();
        mProvider.mEvaluators.put(NetworkCapabilities.NET_CAPABILITY_EIMS, mMockAne);
        mProvider.reportEmergencyDataNetworkPreferredTransportChanged(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        verify(mMockAne)
                .onEmergencyPreferredTransportTypeChanged(
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testOnEmergencyDataNetworkPreferenceChangedWrongAne() {
        createNap();
        mProvider.mEvaluators.put(NetworkCapabilities.NET_CAPABILITY_IMS, mMockAne);
        mProvider.reportEmergencyDataNetworkPreferredTransportChanged(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        verify(mMockAne, never()).onEmergencyPreferredTransportTypeChanged(anyInt());
    }

    @Test
    public void testOnConfigurationLoaded() {
        createNap();
        Message.obtain(mProvider.mConfigHandler, TEST_QNS_CONFIGURATION_LOADED, null)
                .sendToTarget();
        waitForLastHandlerAction(mProvider.mConfigHandler);
        ArgumentCaptor<Handler> capture = ArgumentCaptor.forClass(Handler.class);

        verify(mMockQnsConfigManager)
                .registerForConfigurationLoaded(
                        capture.capture(), eq(TEST_QNS_CONFIGURATION_LOADED));

        Handler configHandler = capture.getValue();
        assertEquals(mProvider.mConfigHandler, configHandler);
        configHandler.sendEmptyMessage(TEST_QNS_CONFIGURATION_CHANGED);
        // ToDo: implementation of onConfigurationChanged()
    }

    @Test
    public void testQnsChangedHandlerEvent() {
        createNap();
        QualifiedNetworksInfo info =
                new QualifiedNetworksInfo(
                        NetworkCapabilities.NET_CAPABILITY_IMS, new ArrayList<>());
        info.setAccessNetworkTypes(List.of(AccessNetworkType.EUTRAN));
        QnsAsyncResult ar = new QnsAsyncResult(null, info, null);
        Message.obtain(mProvider.mHandler, TEST_QUALIFIED_NETWORKS_CHANGED, ar).sendToTarget();
        waitForLastHandlerAction(mProvider.mHandler);
        assertEquals(info.getNetCapability(), NetworkCapabilities.NET_CAPABILITY_IMS);
        assertEquals(List.of(AccessNetworkType.EUTRAN), info.getAccessNetworkTypes());
    }
}
