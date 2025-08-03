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

package com.android.telephony.qns;

import static android.net.NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED;

import static com.android.telephony.qns.QualityMonitor.EVENT_WIFI_NOTIFY_TIMER_EXPIRED;
import static com.android.telephony.qns.QualityMonitor.EVENT_WIFI_RSSI_CHANGED;
import static com.android.telephony.qns.WifiQualityMonitor.INVALID_RSSI;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.SignalThresholdInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
public class WifiQualityMonitorTest extends QnsTest {

    private static final int EVENT_QNS_TIMER_EXPIRED = 1;
    Context mContext;
    @Mock ConnectivityManager mConnectivityManager;
    QnsTimer mQnsTimer;
    @Mock WifiManager mWifiManager;
    @Mock NetworkCapabilities mNetworkCapabilityManager;
    @Mock private Network mMockNetwork;
    private WifiInfo mWifiInfo;
    private WifiQualityMonitor mWifiQualityMonitor;
    private Threshold[] mRetThresholds;
    Threshold[] mThs1 = new Threshold[1];
    Threshold[] mThs2 = new Threshold[1];
    Threshold[] mThs3 = new Threshold[1];
    int mSetRssi = -120;

    CountDownLatch mLatch;
    ThresholdListener mThresholdListener;

    private class ThresholdListener extends ThresholdCallback
            implements ThresholdCallback.WifiThresholdListener {

        ThresholdListener(Executor executor) {
            this.init(executor);
        }

        @Override
        public void onWifiThresholdChanged(Threshold[] thresholds) {
            mRetThresholds = thresholds;
            mLatch.countDown();
        }
    }

    Executor mExecutor = runnable -> new Thread(runnable).start();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(ConnectivityManager.class)).thenReturn(mConnectivityManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mWifiManager);
        when(mContext.getSystemService(NetworkCapabilities.class))
                .thenReturn(mNetworkCapabilityManager);
        mWifiInfo = new WifiInfo.Builder().setRssi(mSetRssi).build();
        mLatch = new CountDownLatch(1);
        mThresholdListener = new ThresholdListener(mExecutor);
        mQnsTimer = new QnsTimer(mContext);
        mWifiQualityMonitor = new WifiQualityMonitor(mContext, mQnsTimer);
    }

    @Test
    public void testGetCurrentQuality() {
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);
        int recv_rssi =
                mWifiQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
        assertEquals(mSetRssi, recv_rssi);
    }

    @Test
    public void testRegisterThresholdChange_RoveIn() throws InterruptedException {
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);
        mThs1[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -100,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mThs2[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -80,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_IMS, mThs1, 0);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_XCAP, mThs2, 0);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        // smaller threshold should register
        assertEquals(mThs1[0].getThreshold(), regThreshold);
    }

    @Test
    public void testUnregisterThresholdChange_RoveIn() throws InterruptedException {
        testRegisterThresholdChange_RoveIn();
        mWifiQualityMonitor.unregisterThresholdChange(NetworkCapabilities.NET_CAPABILITY_IMS, 0);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(mThs2[0].getThreshold(), regThreshold);
    }

    @Test
    public void testRegisterThresholdChange_RoveOut() {
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);
        mThs1[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -100,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mThs2[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -80,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_IMS, mThs1, 0);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_XCAP, mThs2, 0);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        // bigger threshold should register
        assertEquals(mThs2[0].getThreshold(), regThreshold);
    }

    @Test
    public void testUnregisterThresholdChange_RoveOut() throws InterruptedException {
        testRegisterThresholdChange_RoveOut();
        mWifiQualityMonitor.unregisterThresholdChange(NetworkCapabilities.NET_CAPABILITY_XCAP, 0);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(mThs1[0].getThreshold(), regThreshold);

        mWifiQualityMonitor.unregisterThresholdChange(NetworkCapabilities.NET_CAPABILITY_IMS, 0);
        regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(SIGNAL_STRENGTH_UNSPECIFIED, regThreshold);
    }

    @Test
    public void testUpdateThresholdsForNetCapability_RoveIn_Add() throws InterruptedException {
        testRegisterThresholdChange_RoveIn();
        mThs3[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -110,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mWifiQualityMonitor.updateThresholdsForNetCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS, 0, mThs3);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(mThs3[0].getThreshold(), regThreshold);
    }

    @Test
    public void testUpdateThresholdsForNetCapability_RoveIn_Remove() throws InterruptedException {
        testUpdateThresholdsForNetCapability_RoveIn_Add();
        mWifiQualityMonitor.updateThresholdsForNetCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS, 0, null);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(mThs2[0].getThreshold(), regThreshold);
    }

    @Test
    public void testUpdateThresholdsForNetCapability_RoveOut_Add() {
        testRegisterThresholdChange_RoveOut();
        mThs3[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -75,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mWifiQualityMonitor.updateThresholdsForNetCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS, 0, mThs3);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(mThs3[0].getThreshold(), regThreshold);
    }

    @Test
    public void testUpdateThresholdsForNetCapability_RoveOut_Remove() {
        testRegisterThresholdChange_RoveOut();
        mWifiQualityMonitor.updateThresholdsForNetCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS, 0, null);
        int regThreshold = mWifiQualityMonitor.getRegisteredThreshold();
        assertEquals(mThs2[0].getThreshold(), regThreshold);
    }

    @Test
    public void testBackhaulTimer() {
        mSetRssi = -65;
        mLatch = new CountDownLatch(1);
        mWifiInfo = new WifiInfo.Builder().setRssi(mSetRssi).build();
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);
        mThs1[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -100,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mThs2[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -80,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mThs3 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.IWLAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                            -70,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.IWLAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                            -68,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_IMS, mThs1, 0);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_XCAP, mThs1, 0);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_EIMS, mThs2, 0);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_MMS, mThs3, 0);

        mWifiQualityMonitor.mHandler.obtainMessage(EVENT_WIFI_RSSI_CHANGED, -65, 0).sendToTarget();
        waitForDelayedHandlerAction(mWifiQualityMonitor.mHandler, 1000, 200);
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        waitForDelayedHandlerAction(mWifiQualityMonitor.mHandler, 4000, 200);
        assertFalse(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        assertFalse(mWifiQualityMonitor.mHandler.hasMessages(EVENT_WIFI_NOTIFY_TIMER_EXPIRED));
    }

    @Test
    public void testValidateWqmStatus_ValidRange() {
        mSetRssi = -65;
        mLatch = new CountDownLatch(1);
        mWifiInfo = new WifiInfo.Builder().setRssi(mSetRssi).build();
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);

        setWqmThreshold();
        mWifiQualityMonitor.validateWqmStatus(-65);
        assertTrue(mWifiQualityMonitor.mHandler.hasMessages(EVENT_WIFI_RSSI_CHANGED));
    }

    @Test
    public void testValidateWqmStatus_InValidRssiWithValidThreshold() {
        mSetRssi = -65;
        mLatch = new CountDownLatch(1);
        mWifiInfo = new WifiInfo.Builder().setRssi(mSetRssi).build();
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);

        setWqmThreshold();

        mWifiQualityMonitor.validateWqmStatus(SIGNAL_STRENGTH_UNSPECIFIED);
        isWifiRssiChangedHandlerNotPosted();

        mWifiQualityMonitor.validateWqmStatus(INVALID_RSSI);
        isWifiRssiChangedHandlerNotPosted();

        mWifiQualityMonitor.validateWqmStatus(50);
        isWifiRssiChangedHandlerNotPosted();
    }

    @Test
    public void testValidateWqmStatus_ValidRssiWithInValidThreshold() {
        mThs1[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        SIGNAL_STRENGTH_UNSPECIFIED,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_IMS, mThs1, 0);

        mWifiQualityMonitor.validateWqmStatus(-65);
        isWifiRssiChangedHandlerNotPosted();
    }

    private void setWqmThreshold() {
        mThs1[0] =
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -70,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, NetworkCapabilities.NET_CAPABILITY_IMS, mThs1, 0);
    }

    private void isWifiRssiChangedHandlerNotPosted() {
        waitForDelayedHandlerAction(mWifiQualityMonitor.mHandler, 1000, 200);
        assertFalse(mWifiQualityMonitor.mHandler.hasMessages(EVENT_WIFI_RSSI_CHANGED));
    }

    @Test
    public void testUpdateThresholdsForNetCapabilityException() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        mWifiQualityMonitor.updateThresholdsForNetCapability(
                                NetworkCapabilities.NET_CAPABILITY_IMS, 0, mThs1));
    }

    @After
    public void tearDown() {
        mWifiQualityMonitor.close();
    }
}
