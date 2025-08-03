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

package com.android.server.wifi.hal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.WifiDebugHostWakeReasonStats;
import android.hardware.wifi.V1_0.WifiDebugRingBufferFlags;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiDebugRingBufferVerboseLevel;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_5.IWifiChip.MultiStaUseCase;
import android.hardware.wifi.V1_5.WifiUsableChannel;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.RemoteException;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SarInfo;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WlanWakeReasonAndCounts;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WifiChipHidlImplTest extends WifiBaseTest {
    private static final String TEST_IFACE_NAME = "wlan0";

    private WifiChipHidlImpl mDut;
    private WifiStatus mWifiStatusSuccess;
    private WifiStatus mWifiStatusFailure;
    private WifiStatus mWifiStatusBusy;

    @Mock private IWifiChip mIWifiChipMock;
    @Mock private android.hardware.wifi.V1_1.IWifiChip mIWifiChipMockV11;
    @Mock private android.hardware.wifi.V1_2.IWifiChip mIWifiChipMockV12;
    @Mock private android.hardware.wifi.V1_3.IWifiChip mIWifiChipMockV13;
    @Mock private android.hardware.wifi.V1_4.IWifiChip mIWifiChipMockV14;
    @Mock private android.hardware.wifi.V1_5.IWifiChip mIWifiChipMockV15;
    @Mock private Context mContextMock;
    @Mock private Resources mResourcesMock;
    @Mock private SsidTranslator mSsidTranslatorMock;

    private class WifiChipHidlImplSpy extends WifiChipHidlImpl {
        private int mVersion;

        WifiChipHidlImplSpy(int hidlVersion) {
            super(mIWifiChipMock, mContextMock, mSsidTranslatorMock);
            mVersion = hidlVersion;
        }

        @Override
        protected android.hardware.wifi.V1_1.IWifiChip getWifiChipV1_1Mockable() {
            return mVersion >= 1 ? mIWifiChipMockV11 : null;
        }

        @Override
        protected android.hardware.wifi.V1_2.IWifiChip getWifiChipV1_2Mockable() {
            return mVersion >= 2 ? mIWifiChipMockV12 : null;
        }

        @Override
        protected android.hardware.wifi.V1_3.IWifiChip getWifiChipV1_3Mockable() {
            return mVersion >= 3 ? mIWifiChipMockV13 : null;
        }

        @Override
        protected android.hardware.wifi.V1_4.IWifiChip getWifiChipV1_4Mockable() {
            return mVersion >= 4 ? mIWifiChipMockV14 : null;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifiChip getWifiChipV1_5Mockable() {
            return mVersion >= 5 ? mIWifiChipMockV15 : null;
        }

        @Override
        protected android.hardware.wifi.V1_6.IWifiChip getWifiChipV1_6Mockable() {
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContextMock.getResources()).thenReturn(mResourcesMock);
        when(mResourcesMock.getBoolean(R.bool.config_wifiBridgedSoftApSupported)).thenReturn(true);
        when(mResourcesMock.getBoolean(R.bool.config_wifiStaWithBridgedSoftApConcurrencySupported))
                .thenReturn(true);
        mDut = new WifiChipHidlImplSpy(0);

        mWifiStatusSuccess = new WifiStatus();
        mWifiStatusSuccess.code = WifiStatusCode.SUCCESS;
        mWifiStatusFailure = new WifiStatus();
        mWifiStatusFailure.code = WifiStatusCode.ERROR_UNKNOWN;
        mWifiStatusFailure.description = "I don't even know what a Mock Turtle is.";
        mWifiStatusBusy = new WifiStatus();
        mWifiStatusBusy.code = WifiStatusCode.ERROR_BUSY;
        mWifiStatusBusy.description = "Don't bother me, kid";
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_*
     */
    @Test
    public void testChipFeatureMaskTranslation() {
        int caps = (
                android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.SET_TX_POWER_LIMIT
                        | android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2D_RTT
                        | android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2AP_RTT
        );
        long expected = (
                WifiManager.WIFI_FEATURE_TX_POWER_LIMIT
                        | WifiManager.WIFI_FEATURE_D2D_RTT
                        | WifiManager.WIFI_FEATURE_D2AP_RTT
        );
        assertEquals(expected, mDut.wifiFeatureMaskFromChipCapabilities(caps));
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_* for V1.3
     */
    @Test
    public void testChipFeatureMaskTranslation_1_3() {
        int caps = (
                android.hardware.wifi.V1_3.IWifiChip.ChipCapabilityMask.SET_LATENCY_MODE
                        | android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2D_RTT
        );
        long expected = (
                WifiManager.WIFI_FEATURE_LOW_LATENCY
                        | WifiManager.WIFI_FEATURE_D2D_RTT
        );
        assertEquals(expected, mDut.wifiFeatureMaskFromChipCapabilities_1_3(caps));
    }

    /**
     * Test that getRingBufferStatus gets and translates its values correctly.
     */
    @Test
    public void testRingBufferStatus() throws Exception {
        WifiDebugRingBufferStatus one = new WifiDebugRingBufferStatus();
        one.ringName = "One";
        one.flags = WifiDebugRingBufferFlags.HAS_BINARY_ENTRIES;
        one.ringId = 5607371;
        one.sizeInBytes = 54321;
        one.freeSizeInBytes = 42;
        one.verboseLevel = WifiDebugRingBufferVerboseLevel.VERBOSE;
        String oneExpect = "name: One flag: 1 ringBufferId: 5607371 ringBufferByteSize: 54321"
                + " verboseLevel: 2 writtenBytes: 0 readBytes: 0 writtenRecords: 0";

        WifiDebugRingBufferStatus two = new WifiDebugRingBufferStatus();
        two.ringName = "Two";
        two.flags = WifiDebugRingBufferFlags.HAS_ASCII_ENTRIES
                | WifiDebugRingBufferFlags.HAS_PER_PACKET_ENTRIES;
        two.ringId = 4512470;
        two.sizeInBytes = 300;
        two.freeSizeInBytes = 42;
        two.verboseLevel = WifiDebugRingBufferVerboseLevel.DEFAULT;

        ArrayList<WifiDebugRingBufferStatus> halBufferStatus = new ArrayList<>(2);
        halBufferStatus.add(one);
        halBufferStatus.add(two);

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiChip.getDebugRingBuffersStatusCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, halBufferStatus);
            }
        }).when(mIWifiChipMock).getDebugRingBuffersStatus(any(
                IWifiChip.getDebugRingBuffersStatusCallback.class));

        List<WifiNative.RingBufferStatus> actual = mDut.getDebugRingBuffersStatus();

        assertEquals(halBufferStatus.size(), actual.size());
        assertEquals(oneExpect, actual.get(0).toString());
        assertEquals(two.ringId, actual.get(1).ringBufferId);
    }

    /**
     * Tests the retrieval of wlan wake reason stats.
     */
    @Test
    public void testGetWlanWakeReasonCount() throws Exception {
        WifiDebugHostWakeReasonStats stats = new WifiDebugHostWakeReasonStats();
        Random rand = new Random();
        stats.totalCmdEventWakeCnt = rand.nextInt();
        stats.totalDriverFwLocalWakeCnt = rand.nextInt();
        stats.totalRxPacketWakeCnt = rand.nextInt();
        stats.rxPktWakeDetails.rxUnicastCnt = rand.nextInt();
        stats.rxPktWakeDetails.rxMulticastCnt = rand.nextInt();
        stats.rxIcmpPkWakeDetails.icmpPkt = rand.nextInt();
        stats.rxIcmpPkWakeDetails.icmp6Pkt = rand.nextInt();
        stats.rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt = rand.nextInt();
        stats.rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt = rand.nextInt();

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiChip.getDebugHostWakeReasonStatsCallback cb) {
                cb.onValues(mWifiStatusSuccess, stats);
            }
        }).when(mIWifiChipMock).getDebugHostWakeReasonStats(
                any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));

        WlanWakeReasonAndCounts retrievedStats = mDut.getDebugHostWakeReasonStats();
        verify(mIWifiChipMock).getDebugHostWakeReasonStats(
                any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));
        assertNotNull(retrievedStats);
        assertEquals(stats.totalCmdEventWakeCnt, retrievedStats.totalCmdEventWake);
        assertEquals(stats.totalDriverFwLocalWakeCnt, retrievedStats.totalDriverFwLocalWake);
        assertEquals(stats.totalRxPacketWakeCnt, retrievedStats.totalRxDataWake);
        assertEquals(stats.rxPktWakeDetails.rxUnicastCnt, retrievedStats.rxUnicast);
        assertEquals(stats.rxPktWakeDetails.rxMulticastCnt, retrievedStats.rxMulticast);
        assertEquals(stats.rxIcmpPkWakeDetails.icmpPkt, retrievedStats.icmp);
        assertEquals(stats.rxIcmpPkWakeDetails.icmp6Pkt, retrievedStats.icmp6);
        assertEquals(stats.rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt,
                retrievedStats.ipv4RxMulticast);
        assertEquals(stats.rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt,
                retrievedStats.ipv6Multicast);
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation for 1.0 interface.
     * This should return failure since SAR is not supported for this interface version.
     */
    @Test
    public void testSelectTxPowerScenario_1_0() throws RemoteException {
        // Create a SAR info record
        SarInfo sarInfo = new SarInfo();
        sarInfo.isVoiceCall = true;

        // Should fail because we exposed the 1.0 IWifiChip.
        assertFalse(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV11, never()).selectTxPowerScenario(anyInt());
        verify(mIWifiChipMockV12, never()).selectTxPowerScenario_1_2(anyInt());
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation for 1.1 interface.
     * This should return success.
     */
    @Test
    public void testSelectTxPowerScenario_1_1() throws RemoteException {
        // Create a SAR info record
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = false;
        sarInfo.isVoiceCall = true;

        // Now expose the 1.1 IWifiChip.
        mDut = new WifiChipHidlImplSpy(1);
        when(mIWifiChipMockV11.selectTxPowerScenario(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV11).selectTxPowerScenario(
                eq(android.hardware.wifi.V1_1.IWifiChip.TxPowerScenario.VOICE_CALL));
        verify(mIWifiChipMockV11, never()).resetTxPowerScenario();
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation for 1.2 interface.
     * This should return success.
     */
    @Test
    public void testSelectTxPowerScenario_1_2() throws RemoteException {
        // Create a SAR info record
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = false;
        sarInfo.isVoiceCall = true;

        // Now expose the 1.2 IWifiChip
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.selectTxPowerScenario_1_2(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).selectTxPowerScenario_1_2(
                eq(android.hardware.wifi.V1_2.IWifiChip.TxPowerScenario.VOICE_CALL));
        verify(mIWifiChipMockV12, never()).resetTxPowerScenario();
    }

    /**
     * Test the resetTxPowerScenario HIDL method invocation for 1.0 interface.
     * This should return failure since it does not support SAR.
     */
    @Test
    public void testResetTxPowerScenario_1_0() throws RemoteException {
        // Create a SAR info record
        SarInfo sarInfo = new SarInfo();

        // Should fail because we exposed the 1.0 IWifiChip.
        assertFalse(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV11, never()).resetTxPowerScenario();
    }

    /**
     * Test the resetTxPowerScenario HIDL method invocation for 1.1 interface.
     * This should return success.
     */
    @Test
    public void testResetTxPowerScenario_1_1() throws RemoteException {
        // Create a SAR info record
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = false;

        // Now expose the 1.1 IWifiChip.
        mDut = new WifiChipHidlImplSpy(1);
        when(mIWifiChipMockV11.resetTxPowerScenario()).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV11).resetTxPowerScenario();
        verify(mIWifiChipMockV11, never()).selectTxPowerScenario(anyInt());
    }

    /**
     * Test resetting SAR scenario when not needed.
     * Should return true without invoking the HAL method.
     */
    @Test
    public void testResetTxPowerScenario_not_needed_1_1() throws RemoteException {
        InOrder inOrder = inOrder(mIWifiChipMockV11);

        // Create a SAR info record (no SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = false;

        // Now expose the 1.1 IWifiChip.
        mDut = new WifiChipHidlImplSpy(1);
        when(mIWifiChipMockV11.resetTxPowerScenario()).thenReturn(mWifiStatusSuccess);

        // Calling reset once
        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        inOrder.verify(mIWifiChipMockV11).resetTxPowerScenario();
        inOrder.verify(mIWifiChipMockV11, never()).selectTxPowerScenario(anyInt());
        sarInfo.reportingSuccessful();

        // Calling reset a second time
        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        inOrder.verify(mIWifiChipMockV11, never()).resetTxPowerScenario();
        inOrder.verify(mIWifiChipMockV11, never()).selectTxPowerScenario(anyInt());
    }

    /**
     * Test the resetTxPowerScenario HIDL method invocation for 1.2 interface.
     * This should return success.
     */
    @Test
    public void testResetTxPowerScenario_1_2() throws RemoteException {
        // Create a SAR info record (no SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = false;

        // Now expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.resetTxPowerScenario()).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).resetTxPowerScenario();
        verify(mIWifiChipMockV12, never()).selectTxPowerScenario_1_2(anyInt());
    }

    /**
     * Test resetting SAR scenario when not needed.
     * Should return true without invoking the HAL method.
     */
    @Test
    public void testResetTxPowerScenario_not_needed_1_2() throws RemoteException {
        InOrder inOrder = inOrder(mIWifiChipMockV12);

        // Create a SAR info record (no SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = false;

        // Now expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.resetTxPowerScenario()).thenReturn(mWifiStatusSuccess);

        // Calling reset once
        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        inOrder.verify(mIWifiChipMockV12).resetTxPowerScenario();
        inOrder.verify(mIWifiChipMockV12, never()).selectTxPowerScenario(anyInt());
        sarInfo.reportingSuccessful();

        // Calling reset a second time
        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        inOrder.verify(mIWifiChipMockV12, never()).resetTxPowerScenario();
        inOrder.verify(mIWifiChipMockV12, never()).selectTxPowerScenario(anyInt());
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation with SAP and voice call support.
     * When SAP is enabled, should result in SAP with near-body scenario.
     */
    @Test
    public void testSapScenarios_SelectTxPowerV1_2() throws RemoteException {
        // Create a SAR info record (with SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = true;
        sarInfo.isWifiSapEnabled = true;

        // Expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.selectTxPowerScenario_1_2(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).selectTxPowerScenario_1_2(
                eq(android.hardware.wifi.V1_2.IWifiChip.TxPowerScenario.ON_BODY_CELL_ON));
        verify(mIWifiChipMockV12, never()).resetTxPowerScenario();
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation with SAP and voice call support.
     * When a voice call is ongoing, should result in cell with near-head scenario.
     */
    @Test
    public void testVoiceCallScenarios_SelectTxPowerV1_2() throws RemoteException {
        // Create a SAR info record (with SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = true;

        sarInfo.isVoiceCall = true;

        // Expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.selectTxPowerScenario_1_2(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).selectTxPowerScenario_1_2(
                eq(android.hardware.wifi.V1_2.IWifiChip.TxPowerScenario.ON_HEAD_CELL_ON));
        verify(mIWifiChipMockV12, never()).resetTxPowerScenario();
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation with SAP and voice call support.
     * When earpiece is active, should result in cell with near-head scenario.
     */
    @Test
    public void testEarPieceScenarios_SelectTxPowerV1_2() throws RemoteException {
        // Create a SAR info record (with SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = true;

        sarInfo.isEarPieceActive = true;

        // Expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.selectTxPowerScenario_1_2(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).selectTxPowerScenario_1_2(
                eq(android.hardware.wifi.V1_2.IWifiChip.TxPowerScenario.ON_HEAD_CELL_ON));
        verify(mIWifiChipMockV12, never()).resetTxPowerScenario();
    }

    /**
     * Test setting SAR scenario when not needed.
     * Should return true without invoking the HAL method.
     */
    @Test
    public void testSetTxPowerScenario_not_needed_1_2() throws RemoteException {
        InOrder inOrder = inOrder(mIWifiChipMockV12);

        // Create a SAR info record (no SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = true;

        // Now expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.resetTxPowerScenario()).thenReturn(mWifiStatusSuccess);

        // Calling set once
        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        inOrder.verify(mIWifiChipMockV12).resetTxPowerScenario();
        sarInfo.reportingSuccessful();

        // Calling set a second time
        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        inOrder.verify(mIWifiChipMockV12, never()).resetTxPowerScenario();
        inOrder.verify(mIWifiChipMockV12, never()).selectTxPowerScenario(anyInt());
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation with IWifiChip 1.2 interface.
     * Using the following inputs:
     *   - SAP is enabled
     *   - No voice call
     */
    @Test
    public void testSelectTxPowerScenario_1_2_sap() throws RemoteException {
        // Create a SAR info record (with SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = true;

        sarInfo.isWifiSapEnabled = true;
        sarInfo.isVoiceCall = false;

        // Expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.selectTxPowerScenario_1_2(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).selectTxPowerScenario_1_2(
                eq(android.hardware.wifi.V1_2.IWifiChip.TxPowerScenario.ON_BODY_CELL_ON));
    }

    /**
     * Test the selectTxPowerScenario HIDL method invocation with IWifiChip 1.2 interface.
     * Using the following inputs:
     *   - SAP is enabled
     *   - Voice call is enabled
     */
    @Test
    public void testSelectTxPowerScenario_1_2_head_sap_call() throws RemoteException {
        // Create a SAR info record (with SAP support)
        SarInfo sarInfo = new SarInfo();
        sarInfo.sarVoiceCallSupported = true;
        sarInfo.sarSapSupported = true;

        sarInfo.isWifiSapEnabled = true;
        sarInfo.isVoiceCall = true;

        // Expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        when(mIWifiChipMockV12.selectTxPowerScenario_1_2(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.selectTxPowerScenario(sarInfo));
        verify(mIWifiChipMockV12).selectTxPowerScenario_1_2(
                eq(android.hardware.wifi.V1_2.IWifiChip.TxPowerScenario.ON_HEAD_CELL_ON));
    }

    /**
     * Test the setLowLatencyMode HIDL method invocation with the IWifiChip 1.2 interface.
     * Should return false.
     *
     */
    @Test
    public void testSetLowLatencyMode_1_2() throws RemoteException {
        // Expose the 1.2 IWifiChip.
        mDut = new WifiChipHidlImplSpy(2);
        assertFalse(mDut.setLowLatencyMode(true));
        assertFalse(mDut.setLowLatencyMode(false));
    }

    /**
     * Test the setLowLatencyMode HIDL method invocation with the IWifiChip 1.3 interface.
     */
    @Test
    public void testSetLowLatencyMode_1_3_enabled() throws RemoteException {
        int mode = android.hardware.wifi.V1_3.IWifiChip.LatencyMode.LOW;

        // Expose the 1.3 IWifiChip.
        mDut = new WifiChipHidlImplSpy(3);
        when(mIWifiChipMockV13.setLatencyMode(anyInt())).thenReturn(mWifiStatusSuccess);
        assertTrue(mDut.setLowLatencyMode(true));
        verify(mIWifiChipMockV13).setLatencyMode(eq(mode));
    }

    /**
     * Test the setLowLatencyMode HIDL method invocation with the IWifiChip 1.3 interface
     */
    @Test
    public void testSetLowLatencyMode_1_3_disabled() throws RemoteException {
        int mode = android.hardware.wifi.V1_3.IWifiChip.LatencyMode.NORMAL;

        // Expose the 1.3 IWifiChip.
        mDut = new WifiChipHidlImplSpy(3);
        when(mIWifiChipMockV13.setLatencyMode(anyInt())).thenReturn(mWifiStatusSuccess);
        assertTrue(mDut.setLowLatencyMode(false));
        verify(mIWifiChipMockV13).setLatencyMode(eq(mode));
    }

    @Test
    public void testRemoveIfaceInstanceFromBridgedApIface() throws RemoteException {
        mDut = new WifiChipHidlImplSpy(5);
        when(mIWifiChipMockV15.removeIfaceInstanceFromBridgedApIface(any(), any()))
                .thenReturn(mWifiStatusSuccess);
        assertTrue(mDut.removeIfaceInstanceFromBridgedApIface(any(), any()));
    }

    @Test
    public void testSetCoexUnsafeChannels() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastS());
        mDut = new WifiChipHidlImplSpy(5);
        when(mIWifiChipMockV15.setCoexUnsafeChannels(any(), anyInt()))
                .thenReturn(mWifiStatusSuccess);
        final List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_5_GHZ, 36));
        final int restrictions = WifiManager.COEX_RESTRICTION_WIFI_DIRECT
                | WifiManager.COEX_RESTRICTION_WIFI_AWARE | WifiManager.COEX_RESTRICTION_SOFTAP;
        assertTrue(mDut.setCoexUnsafeChannels(unsafeChannels, restrictions));
    }

    @Test
    public void testSetMultiStaPrimaryConnection() throws Exception {
        mDut = new WifiChipHidlImplSpy(5);
        when(mIWifiChipMockV15.setMultiStaPrimaryConnection(any())).thenReturn(mWifiStatusSuccess);
        assertTrue(mDut.setMultiStaPrimaryConnection(TEST_IFACE_NAME));
        verify(mIWifiChipMockV15).setMultiStaPrimaryConnection(TEST_IFACE_NAME);
    }

    @Test
    public void testSetMultiStaUseCase() throws Exception {
        mDut = new WifiChipHidlImplSpy(5);
        when(mIWifiChipMockV15
                .setMultiStaUseCase(MultiStaUseCase.DUAL_STA_TRANSIENT_PREFER_PRIMARY))
                .thenReturn(mWifiStatusSuccess);
        when(mIWifiChipMockV15
                .setMultiStaUseCase(MultiStaUseCase.DUAL_STA_NON_TRANSIENT_UNBIASED))
                .thenReturn(mWifiStatusFailure);

        assertTrue(mDut.setMultiStaUseCase(WifiNative.DUAL_STA_TRANSIENT_PREFER_PRIMARY));
        verify(mIWifiChipMockV15).setMultiStaUseCase(
                MultiStaUseCase.DUAL_STA_TRANSIENT_PREFER_PRIMARY);

        assertFalse(mDut.setMultiStaUseCase(WifiNative.DUAL_STA_NON_TRANSIENT_UNBIASED));
        verify(mIWifiChipMockV15).setMultiStaUseCase(
                MultiStaUseCase.DUAL_STA_NON_TRANSIENT_UNBIASED);

        // illegal value.
        assertFalse(mDut.setMultiStaUseCase(5));
    }

    @Test
    public void testGetUsableChannels() throws Exception {
        mDut = new WifiChipHidlImplSpy(5);
        ArrayList<WifiUsableChannel> channels = new ArrayList<>();
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(int band, int mode, int filter,
                    android.hardware.wifi.V1_5.IWifiChip.getUsableChannelsCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, channels);
            }
        }).when(mIWifiChipMockV15).getUsableChannels(anyInt(), anyInt(), anyInt(),
                any(android.hardware.wifi.V1_5.IWifiChip.getUsableChannelsCallback.class));
        mDut.getUsableChannels(
                WifiScanner.WIFI_BAND_24_GHZ,
                WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI,
                WifiAvailableChannel.FILTER_CELLULAR_COEXISTENCE);
        verify(mIWifiChipMockV15).getUsableChannels(anyInt(), anyInt(), anyInt(),
                any(android.hardware.wifi.V1_5.IWifiChip.getUsableChannelsCallback.class));
    }
}
