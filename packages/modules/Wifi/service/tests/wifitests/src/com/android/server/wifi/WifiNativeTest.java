/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.wifi;

import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_NATIVE_SUPPORTED_FEATURES;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;
import android.net.wifi.nl80211.NativeScanResult;
import android.net.wifi.nl80211.RadioChainInfo;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.net.wifi.nl80211.WifiNl80211Manager.SendMgmtFrameCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.WorkSource;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.coex.CoexManager;
import com.android.server.wifi.hal.WifiChip;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.NetdWrapper;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.WifiNative}.
 */
@SmallTest
public class WifiNativeTest extends WifiBaseTest {
    private static final String WIFI_IFACE_NAME = "mockWlan";
    private static final long FATE_REPORT_DRIVER_TIMESTAMP_USEC = 12345;
    private static final byte[] FATE_REPORT_FRAME_BYTES = new byte[] {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 0, 1, 2, 3, 4, 5, 6, 7};
    private static final WifiNative.TxFateReport TX_FATE_REPORT = new WifiNative.TxFateReport(
            WifiLoggerHal.TX_PKT_FATE_SENT,
            FATE_REPORT_DRIVER_TIMESTAMP_USEC,
            WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
            FATE_REPORT_FRAME_BYTES
    );
    private static final WifiNative.RxFateReport RX_FATE_REPORT = new WifiNative.RxFateReport(
            WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID,
            FATE_REPORT_DRIVER_TIMESTAMP_USEC,
            WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
            FATE_REPORT_FRAME_BYTES
    );
    private static final FrameTypeMapping[] FRAME_TYPE_MAPPINGS = new FrameTypeMapping[] {
            new FrameTypeMapping(WifiLoggerHal.FRAME_TYPE_UNKNOWN, "unknown", "N/A"),
            new FrameTypeMapping(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, "data", "Ethernet"),
            new FrameTypeMapping(WifiLoggerHal.FRAME_TYPE_80211_MGMT, "802.11 management",
                    "802.11 Mgmt"),
            new FrameTypeMapping((byte) 42, "42", "N/A")
    };
    private static final FateMapping[] TX_FATE_MAPPINGS = new FateMapping[] {
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_ACKED, "acked"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_SENT, "sent"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_FW_QUEUED, "firmware queued"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_FW_DROP_INVALID,
                    "firmware dropped (invalid frame)"),
            new FateMapping(
                    WifiLoggerHal.TX_PKT_FATE_FW_DROP_NOBUFS,  "firmware dropped (no bufs)"),
            new FateMapping(
                    WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER, "firmware dropped (other)"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED, "driver queued"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_DRV_DROP_INVALID,
                    "driver dropped (invalid frame)"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_DRV_DROP_NOBUFS,
                    "driver dropped (no bufs)"),
            new FateMapping(WifiLoggerHal.TX_PKT_FATE_DRV_DROP_OTHER, "driver dropped (other)"),
            new FateMapping((byte) 42, "42")
    };
    private static final FateMapping[] RX_FATE_MAPPINGS = new FateMapping[] {
            new FateMapping(WifiLoggerHal.RX_PKT_FATE_SUCCESS, "success"),
            new FateMapping(WifiLoggerHal.RX_PKT_FATE_FW_QUEUED, "firmware queued"),
            new FateMapping(
                    WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER, "firmware dropped (filter)"),
            new FateMapping(WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID,
                    "firmware dropped (invalid frame)"),
            new FateMapping(
                    WifiLoggerHal.RX_PKT_FATE_FW_DROP_NOBUFS, "firmware dropped (no bufs)"),
            new FateMapping(
                    WifiLoggerHal.RX_PKT_FATE_FW_DROP_OTHER, "firmware dropped (other)"),
            new FateMapping(WifiLoggerHal.RX_PKT_FATE_DRV_QUEUED, "driver queued"),
            new FateMapping(
                    WifiLoggerHal.RX_PKT_FATE_DRV_DROP_FILTER, "driver dropped (filter)"),
            new FateMapping(WifiLoggerHal.RX_PKT_FATE_DRV_DROP_INVALID,
                    "driver dropped (invalid frame)"),
            new FateMapping(
                    WifiLoggerHal.RX_PKT_FATE_DRV_DROP_NOBUFS, "driver dropped (no bufs)"),
            new FateMapping(WifiLoggerHal.RX_PKT_FATE_DRV_DROP_OTHER, "driver dropped (other)"),
            new FateMapping((byte) 42, "42")
    };
    private static final WifiNl80211Manager.SignalPollResult SIGNAL_POLL_RESULT =
            new WifiNl80211Manager.SignalPollResult(-60, 12, 6, 5240);

    private static final Set<Integer> SCAN_FREQ_SET = Set.of(
            2410,
            2450,
            5050,
            5200);

    private static final String TEST_QUOTED_SSID_1 = "\"testSsid1\"";
    private static final String TEST_QUOTED_SSID_2 = "\"testSsid2\"";
    private static final int[] TEST_FREQUENCIES_1 = {};
    private static final int[] TEST_FREQUENCIES_2 = {2500, 5124};
    private static final List<String> SCAN_HIDDEN_NETWORK_SSID_SET = List.of(
            TEST_QUOTED_SSID_1,
            TEST_QUOTED_SSID_2);

    private static final List<byte[]> SCAN_HIDDEN_NETWORK_BYTE_SSID_SET = List.of(
            NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(TEST_QUOTED_SSID_1)),
            NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(TEST_QUOTED_SSID_2)));

    private static final WifiNative.PnoSettings TEST_PNO_SETTINGS =
            new WifiNative.PnoSettings() {{
                isConnected = false;
                periodInMs = 6000;
                scanIterations = 3;
                scanIntervalMultiplier = 3;
                networkList = new WifiNative.PnoNetwork[2];
                networkList[0] = new WifiNative.PnoNetwork();
                networkList[1] = new WifiNative.PnoNetwork();
                networkList[0].ssid = TEST_QUOTED_SSID_1;
                networkList[1].ssid = TEST_QUOTED_SSID_2;
                networkList[0].frequencies = TEST_FREQUENCIES_1;
                networkList[1].frequencies = TEST_FREQUENCIES_2;
            }};
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("ee:33:a2:94:10:92");

    private static final String TEST_MAC_ADDRESS_STR = "f4:f5:e8:51:9e:09";
    private static final String TEST_BSSID_STR = "a8:bd:27:5b:33:72";
    private static final int TEST_MCS_RATE = 5;
    private static final int TEST_SEQUENCE_NUM = 0x66b0;

    private static final byte[] TEST_SSID =
            new byte[] {'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    private static final byte[] TEST_BSSID =
            new byte[] {(byte) 0x12, (byte) 0xef, (byte) 0xa1,
                    (byte) 0x2c, (byte) 0x97, (byte) 0x8b};
    // This the IE buffer which is consistent with TEST_SSID.
    private static final byte[] TEST_INFO_ELEMENT_SSID =
            new byte[] {
                    // Element ID for SSID.
                    (byte) 0x00,
                    // Length of the SSID: 0x0b or 11.
                    (byte) 0x0b,
                    // This is string "GoogleGuest"
                    'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    // RSN IE data indicating EAP key management.
    private static final byte[] TEST_INFO_ELEMENT_RSN =
            new byte[] {
                    // Element ID for RSN.
                    (byte) 0x30,
                    // Length of the element data.
                    (byte) 0x18,
                    (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                    (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                    (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02, (byte) 0x01, (byte) 0x00,
                    (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01, (byte) 0x00, (byte) 0x00 };

    private static final int TEST_FREQUENCY = 2456;
    private static final int TEST_SIGNAL_MBM = -4500;
    private static final long TEST_TSF = 34455441;
    private static final int TEST_CAPABILITY = 0b0000_0000_0010_0100;
    private static final boolean TEST_ASSOCIATED = true;
    private static final NativeScanResult MOCK_NATIVE_SCAN_RESULT = createMockNativeScanResult();
    private static NativeScanResult createMockNativeScanResult() {
        NativeScanResult result = new NativeScanResult();
        result.ssid = TEST_SSID;
        result.bssid = TEST_BSSID;
        result.infoElement = TEST_INFO_ELEMENT_SSID;
        result.frequency = TEST_FREQUENCY;
        result.signalMbm = TEST_SIGNAL_MBM;
        result.tsf = TEST_TSF;
        result.capability = TEST_CAPABILITY;
        result.associated = TEST_ASSOCIATED;
        result.radioChainInfos = new ArrayList<>();
        return result;
    }

    public static final long WIFI_TEST_FEATURE = 0x800000000L;

    private static final RadioChainInfo MOCK_NATIVE_RADIO_CHAIN_INFO_1 = new RadioChainInfo(1, -89);
    private static final RadioChainInfo MOCK_NATIVE_RADIO_CHAIN_INFO_2 = new RadioChainInfo(0, -78);
    private static final WorkSource TEST_WORKSOURCE = new WorkSource();
    private static final WorkSource TEST_WORKSOURCE2 = new WorkSource();

    MockResources mResources;

    @Mock private WifiContext mContext;
    @Mock private WifiVendorHal mWifiVendorHal;
    @Mock private WifiNl80211Manager mWificondControl;
    @Mock private SupplicantStaIfaceHal mStaIfaceHal;
    @Mock private HostapdHal mHostapdHal;
    @Mock private WifiMonitor mWifiMonitor;
    @Mock private PropertyService mPropertyService;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private Handler mHandler;
    @Mock private SendMgmtFrameCallback mSendMgmtFrameCallback;
    @Mock private Random mRandom;
    @Mock private WifiInjector mWifiInjector;
    @Mock private NetdWrapper mNetdWrapper;
    @Mock private CoexManager mCoexManager;
    @Mock BuildProperties mBuildProperties;
    @Mock private WifiNative.InterfaceCallback mInterfaceCallback;
    @Mock private WifiCountryCode.ChangeListener mWifiCountryCodeChangeListener;
    @Mock WifiSettingsConfigStore mSettingsConfigStore;
    @Mock private ConcreteClientModeManager mConcreteClientModeManager;
    @Mock private SoftApManager mSoftApManager;
    @Mock private SsidTranslator mSsidTranslator;
    @Mock private WifiGlobals mWifiGlobals;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock WifiChip.AfcChannelAllowance mAfcChannelAllowance;

    private MockitoSession mSession;
    ArgumentCaptor<WifiNl80211Manager.ScanEventCallback> mScanCallbackCaptor =
            ArgumentCaptor.forClass(WifiNl80211Manager.ScanEventCallback.class);

    private WifiNative mWifiNative;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mWifiVendorHal.initialize(any())).thenReturn(true);
        when(mWifiVendorHal.isVendorHalSupported()).thenReturn(true);
        when(mWifiVendorHal.startVendorHal()).thenReturn(true);
        when(mWifiVendorHal.startVendorHalSta(eq(mConcreteClientModeManager))).thenReturn(true);
        when(mWifiVendorHal.createStaIface(any(), any(), eq(mConcreteClientModeManager)))
                .thenReturn(WIFI_IFACE_NAME);
        when(mWifiVendorHal.createApIface(any(), any(), anyInt(), anyBoolean(), any(), anyList()))
                .thenReturn(WIFI_IFACE_NAME);

        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(true);

        when(mWificondControl.setupInterfaceForClientMode(any(), any(), any(), any())).thenReturn(
                true);
        when(mWificondControl.setupInterfaceForSoftApMode(any())).thenReturn(true);

        when(mStaIfaceHal.registerDeathHandler(any())).thenReturn(true);
        when(mStaIfaceHal.isInitializationComplete()).thenReturn(true);
        when(mStaIfaceHal.initialize()).thenReturn(true);
        when(mStaIfaceHal.startDaemon()).thenReturn(true);
        when(mStaIfaceHal.setupIface(any())).thenReturn(true);

        when(mHostapdHal.isInitializationStarted()).thenReturn(true);
        when(mHostapdHal.startDaemon()).thenReturn(true);
        when(mHostapdHal.isInitializationComplete()).thenReturn(true);
        when(mHostapdHal.registerDeathHandler(any())).thenReturn(true);

        when(mWifiInjector.makeNetdWrapper()).thenReturn(mNetdWrapper);
        when(mWifiInjector.getCoexManager()).thenReturn(mCoexManager);

        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mSettingsConfigStore);
        when(mWifiInjector.getContext()).thenReturn(mContext);
        when(mWifiInjector.getSsidTranslator()).thenReturn(mSsidTranslator);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        mResources = getMockResources();
        mResources.setBoolean(R.bool.config_wifiNetworkCentricQosPolicyFeatureEnabled, false);
        when(mContext.getResources()).thenReturn(mResources);
        when(mSettingsConfigStore.get(eq(WIFI_NATIVE_SUPPORTED_FEATURES)))
                .thenReturn(WIFI_TEST_FEATURE);
        when(mSsidTranslator.getTranslatedSsidAndRecordBssidCharset(any(), any()))
                .thenAnswer((Answer<WifiSsid>) invocation ->
                        getTranslatedSsid(invocation.getArgument(0)));
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.isInterfaceFailureBugreportEnabled()).thenReturn(false);

        // Mock static methods from WifiStatsLog.
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiStatsLog.class)
                .startMocking();

        mWifiNative = new WifiNative(
                mWifiVendorHal, mStaIfaceHal, mHostapdHal, mWificondControl,
                mWifiMonitor, mPropertyService, mWifiMetrics,
                mHandler, mRandom, mBuildProperties, mWifiInjector);
        mWifiNative.enableVerboseLogging(true, true);
        mWifiNative.initialize();
        assertNull(mWifiNative.mUnknownAkmMap);
    }

    @After
    public void tearDown() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /** Mock translating an SSID */
    private WifiSsid getTranslatedSsid(WifiSsid ssid) {
        byte[] ssidBytes = ssid.getBytes();
        for (int i = 0; i < ssidBytes.length; i++) {
            ssidBytes[i]++;
        }
        return WifiSsid.fromBytes(ssidBytes);
    }

    private MockResources getMockResources() {
        MockResources resources = new MockResources();
        return resources;
    }

    /**
     * Verifies that TxFateReport's constructor sets all of the TxFateReport fields.
     */
    @Test
    public void testTxFateReportCtorSetsFields() {
        WifiNative.TxFateReport fateReport = new WifiNative.TxFateReport(
                WifiLoggerHal.TX_PKT_FATE_SENT,  // non-zero value
                FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                WifiLoggerHal.FRAME_TYPE_ETHERNET_II,  // non-zero value
                FATE_REPORT_FRAME_BYTES
        );
        assertEquals(WifiLoggerHal.TX_PKT_FATE_SENT, fateReport.mFate);
        assertEquals(FATE_REPORT_DRIVER_TIMESTAMP_USEC, fateReport.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, fateReport.mFrameType);
        assertArrayEquals(FATE_REPORT_FRAME_BYTES, fateReport.mFrameBytes);
    }

    /**
     * Verifies that RxFateReport's constructor sets all of the RxFateReport fields.
     */
    @Test
    public void testRxFateReportCtorSetsFields() {
        WifiNative.RxFateReport fateReport = new WifiNative.RxFateReport(
                WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID,  // non-zero value
                FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                WifiLoggerHal.FRAME_TYPE_ETHERNET_II,  // non-zero value
                FATE_REPORT_FRAME_BYTES
        );
        assertEquals(WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID, fateReport.mFate);
        assertEquals(FATE_REPORT_DRIVER_TIMESTAMP_USEC, fateReport.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, fateReport.mFrameType);
        assertArrayEquals(FATE_REPORT_FRAME_BYTES, fateReport.mFrameBytes);
    }

    /**
     * Verifies the hashCode methods for HiddenNetwork and PnoNetwork classes
     */
    @Test
    public void testHashCode() {
        WifiNative.HiddenNetwork hiddenNet1 = new WifiNative.HiddenNetwork();
        hiddenNet1.ssid = new String("sametext");

        WifiNative.HiddenNetwork hiddenNet2 = new WifiNative.HiddenNetwork();
        hiddenNet2.ssid = new String("sametext");

        assertTrue(hiddenNet1.equals(hiddenNet2));
        assertEquals(hiddenNet1.hashCode(), hiddenNet2.hashCode());

        WifiNative.PnoNetwork pnoNet1 = new WifiNative.PnoNetwork();
        pnoNet1.ssid = new String("sametext");
        pnoNet1.flags = 2;
        pnoNet1.auth_bit_field = 4;
        pnoNet1.frequencies = TEST_FREQUENCIES_2;

        WifiNative.PnoNetwork pnoNet2 = new WifiNative.PnoNetwork();
        pnoNet2.ssid = new String("sametext");
        pnoNet2.flags = 2;
        pnoNet2.auth_bit_field = 4;
        pnoNet2.frequencies = TEST_FREQUENCIES_2;

        assertTrue(pnoNet1.equals(pnoNet2));
        assertEquals(pnoNet1.hashCode(), pnoNet2.hashCode());
    }

    // Support classes for test{Tx,Rx}FateReportToString.
    private static class FrameTypeMapping {
        byte mTypeNumber;
        String mExpectedTypeText;
        String mExpectedProtocolText;
        FrameTypeMapping(byte typeNumber, String expectedTypeText, String expectedProtocolText) {
            this.mTypeNumber = typeNumber;
            this.mExpectedTypeText = expectedTypeText;
            this.mExpectedProtocolText = expectedProtocolText;
        }
    }
    private static class FateMapping {
        byte mFateNumber;
        String mExpectedText;
        FateMapping(byte fateNumber, String expectedText) {
            this.mFateNumber = fateNumber;
            this.mExpectedText = expectedText;
        }
    }

    /**
     * Verifies that FateReport.getTableHeader() prints the right header.
     */
    @Test
    public void testFateReportTableHeader() {
        final String header = WifiNative.FateReport.getTableHeader();
        assertEquals(
                "\nTime usec        Walltime      Direction  Fate                              "
                + "Protocol      Type                     Result\n"
                + "---------        --------      ---------  ----                              "
                + "--------      ----                     ------\n", header);
    }

    /**
     * Verifies that TxFateReport.toTableRowString() includes the information we care about.
     */
    @Test
    public void testTxFateReportToTableRowString() {
        WifiNative.TxFateReport fateReport = TX_FATE_REPORT;
        assertTrue(
                fateReport.toTableRowString().replaceAll("\\s+", " ").trim().matches(
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC + " "  // timestamp
                            + "\\d{2}:\\d{2}:\\d{2}\\.\\d{3} "  // walltime
                            + "TX "  // direction
                            + "sent "  // fate
                            + "Ethernet "  // type
                            + "N/A "  // protocol
                            + "N/A"  // result
                )
        );

        for (FrameTypeMapping frameTypeMapping : FRAME_TYPE_MAPPINGS) {
            fateReport = new WifiNative.TxFateReport(
                    WifiLoggerHal.TX_PKT_FATE_SENT,
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                    frameTypeMapping.mTypeNumber,
                    FATE_REPORT_FRAME_BYTES
            );
            assertTrue(
                    fateReport.toTableRowString().replaceAll("\\s+", " ").trim().matches(
                            FATE_REPORT_DRIVER_TIMESTAMP_USEC + " "  // timestamp
                                    + "\\d{2}:\\d{2}:\\d{2}\\.\\d{3} "  // walltime
                                    + "TX "  // direction
                                    + "sent "  // fate
                                    + frameTypeMapping.mExpectedProtocolText + " "  // type
                                    + "N/A "  // protocol
                                    + "N/A"  // result
                    )
            );
        }

        for (FateMapping fateMapping : TX_FATE_MAPPINGS) {
            fateReport = new WifiNative.TxFateReport(
                    fateMapping.mFateNumber,
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                    WifiLoggerHal.FRAME_TYPE_80211_MGMT,
                    FATE_REPORT_FRAME_BYTES
            );
            assertTrue(
                    fateReport.toTableRowString().replaceAll("\\s+", " ").trim().matches(
                            FATE_REPORT_DRIVER_TIMESTAMP_USEC + " "  // timestamp
                                    + "\\d{2}:\\d{2}:\\d{2}\\.\\d{3} "  // walltime
                                    + "TX "  // direction
                                    + Pattern.quote(fateMapping.mExpectedText) + " "  // fate
                                    + "802.11 Mgmt "  // type
                                    + "N/A "  // protocol
                                    + "N/A"  // result
                    )
            );
        }
    }

    /**
     * Verifies that TxFateReport.toVerboseStringWithPiiAllowed() includes the information we care
     * about.
     */
    @Test
    public void testTxFateReportToVerboseStringWithPiiAllowed() {
        WifiNative.TxFateReport fateReport = TX_FATE_REPORT;

        String verboseFateString = fateReport.toVerboseStringWithPiiAllowed();
        assertTrue(verboseFateString.contains("Frame direction: TX"));
        assertTrue(verboseFateString.contains("Frame timestamp: 12345"));
        assertTrue(verboseFateString.contains("Frame fate: sent"));
        assertTrue(verboseFateString.contains("Frame type: data"));
        assertTrue(verboseFateString.contains("Frame protocol: Ethernet"));
        assertTrue(verboseFateString.contains("Frame protocol type: N/A"));
        assertTrue(verboseFateString.contains("Frame length: 16"));
        assertTrue(verboseFateString.contains(
                "61 62 63 64 65 66 67 68 00 01 02 03 04 05 06 07")); // hex dump
        // TODO(quiche): uncomment this, once b/27975149 is fixed.
        // assertTrue(verboseFateString.contains("abcdefgh........"));  // hex dump

        for (FrameTypeMapping frameTypeMapping : FRAME_TYPE_MAPPINGS) {
            fateReport = new WifiNative.TxFateReport(
                    WifiLoggerHal.TX_PKT_FATE_SENT,
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                    frameTypeMapping.mTypeNumber,
                    FATE_REPORT_FRAME_BYTES
            );
            verboseFateString = fateReport.toVerboseStringWithPiiAllowed();
            assertTrue(verboseFateString.contains("Frame type: "
                    + frameTypeMapping.mExpectedTypeText));
        }

        for (FateMapping fateMapping : TX_FATE_MAPPINGS) {
            fateReport = new WifiNative.TxFateReport(
                    fateMapping.mFateNumber,
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                    WifiLoggerHal.FRAME_TYPE_80211_MGMT,
                    FATE_REPORT_FRAME_BYTES
            );
            verboseFateString = fateReport.toVerboseStringWithPiiAllowed();
            assertTrue(verboseFateString.contains("Frame fate: " + fateMapping.mExpectedText));
        }
    }

    /**
     * Verifies that RxFateReport.toTableRowString() includes the information we care about.
     */
    @Test
    public void testRxFateReportToTableRowString() {
        WifiNative.RxFateReport fateReport = RX_FATE_REPORT;
        assertTrue(
                fateReport.toTableRowString().replaceAll("\\s+", " ").trim().matches(
                        FATE_REPORT_DRIVER_TIMESTAMP_USEC + " "  // timestamp
                                + "\\d{2}:\\d{2}:\\d{2}\\.\\d{3} "  // walltime
                                + "RX "  // direction
                                + Pattern.quote("firmware dropped (invalid frame) ")  // fate
                                + "Ethernet "  // type
                                + "N/A "  // protocol
                                + "N/A"  // result
                )
        );

        // FrameTypeMappings omitted, as they're the same as for TX.

        for (FateMapping fateMapping : RX_FATE_MAPPINGS) {
            fateReport = new WifiNative.RxFateReport(
                    fateMapping.mFateNumber,
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                    WifiLoggerHal.FRAME_TYPE_80211_MGMT,
                    FATE_REPORT_FRAME_BYTES
            );
            assertTrue(
                    fateReport.toTableRowString().replaceAll("\\s+", " ").trim().matches(
                            FATE_REPORT_DRIVER_TIMESTAMP_USEC + " "  // timestamp
                                    + "\\d{2}:\\d{2}:\\d{2}\\.\\d{3} "  // walltime
                                    + "RX "  // direction
                                    + Pattern.quote(fateMapping.mExpectedText) + " " // fate
                                    + "802.11 Mgmt "  // type
                                    + "N/A " // protocol
                                    + "N/A"  // result
                    )
            );
        }
    }

    /**
     * Verifies that RxFateReport.toVerboseStringWithPiiAllowed() includes the information we care
     * about.
     */
    @Test
    public void testRxFateReportToVerboseStringWithPiiAllowed() {
        WifiNative.RxFateReport fateReport = RX_FATE_REPORT;

        String verboseFateString = fateReport.toVerboseStringWithPiiAllowed();
        assertTrue(verboseFateString.contains("Frame direction: RX"));
        assertTrue(verboseFateString.contains("Frame timestamp: 12345"));
        assertTrue(verboseFateString.contains("Frame fate: firmware dropped (invalid frame)"));
        assertTrue(verboseFateString.contains("Frame type: data"));
        assertTrue(verboseFateString.contains("Frame protocol: Ethernet"));
        assertTrue(verboseFateString.contains("Frame protocol type: N/A"));
        assertTrue(verboseFateString.contains("Frame length: 16"));
        assertTrue(verboseFateString.contains(
                "61 62 63 64 65 66 67 68 00 01 02 03 04 05 06 07")); // hex dump
        // TODO(quiche): uncomment this, once b/27975149 is fixed.
        // assertTrue(verboseFateString.contains("abcdefgh........"));  // hex dump

        // FrameTypeMappings omitted, as they're the same as for TX.

        for (FateMapping fateMapping : RX_FATE_MAPPINGS) {
            fateReport = new WifiNative.RxFateReport(
                    fateMapping.mFateNumber,
                    FATE_REPORT_DRIVER_TIMESTAMP_USEC,
                    WifiLoggerHal.FRAME_TYPE_80211_MGMT,
                    FATE_REPORT_FRAME_BYTES
            );
            verboseFateString = fateReport.toVerboseStringWithPiiAllowed();
            assertTrue(verboseFateString.contains("Frame fate: " + fateMapping.mExpectedText));
        }
    }

    /**
     * Verifies that startPktFateMonitoring returns false when HAL is not started.
     */
    @Test
    public void testStartPktFateMonitoringReturnsFalseWhenHalIsNotStarted() {
        assertFalse(mWifiNative.isHalStarted());
        assertFalse(mWifiNative.startPktFateMonitoring(WIFI_IFACE_NAME));
    }

    /**
     * Verifies that getTxPktFates returns error when HAL is not started.
     */
    @Test
    public void testGetTxPktFatesReturnsErrorWhenHalIsNotStarted() {
        assertFalse(mWifiNative.isHalStarted());
        assertEquals(0, mWifiNative.getTxPktFates(WIFI_IFACE_NAME).size());
    }

    /**
     * Verifies that getRxPktFates returns error when HAL is not started.
     */
    @Test
    public void testGetRxPktFatesReturnsErrorWhenHalIsNotStarted() {
        assertFalse(mWifiNative.isHalStarted());
        assertEquals(0, mWifiNative.getRxPktFates(WIFI_IFACE_NAME).size());
    }

    // TODO(quiche): Add tests for the success cases (when HAL has been started). Specifically:
    // - testStartPktFateMonitoringCallsHalIfHalIsStarted()
    // - testGetTxPktFatesCallsHalIfHalIsStarted()
    // - testGetRxPktFatesCallsHalIfHalIsStarted()
    //
    // Adding these tests is difficult to do at the moment, because we can't mock out the HAL
    // itself. Also, we can't mock out the native methods, because those methods are private.
    // b/28005116.

    /** Verifies that getDriverStateDumpNative returns null when HAL is not started. */
    @Test
    public void testGetDriverStateDumpReturnsNullWhenHalIsNotStarted() {
        assertEquals(null, mWifiNative.getDriverStateDump());
    }

    // TODO(b/28005116): Add test for the success case of getDriverStateDump().

    /**
     * Verifies getWifiLinkLayerStats() calls underlying WifiVendorHal.
     *
     */
    @Test
    public void testGetWifiLinkLayerStatsForClientInConnectivityMode() throws Exception {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        mWifiNative.getWifiLinkLayerStats(WIFI_IFACE_NAME);
        mWifiNative.getWifiLinkLayerStats(WIFI_IFACE_NAME);
        verify(mWifiVendorHal, times(2)).getWifiLinkLayerStats(eq(WIFI_IFACE_NAME));
    }

    /**
     * Verifies client mode + scan success.
     */
    @Test
    public void testClientModeScanSuccess() {
        InOrder order = inOrder(mWificondControl, mNetdWrapper, mWifiVendorHal);
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        order.verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                mScanCallbackCaptor.capture(), any());
        order.verify(mNetdWrapper).isInterfaceUp(eq(WIFI_IFACE_NAME));
        order.verify(mWifiVendorHal).enableLinkLayerStats(eq(WIFI_IFACE_NAME));

        mScanCallbackCaptor.getValue().onScanResultReady();
        verify(mWifiMonitor).broadcastScanResultEvent(WIFI_IFACE_NAME);
    }

    /**
     * Verifies client mode + scan failure.
     */
    @Test
    public void testClientModeScanFailure() {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                mScanCallbackCaptor.capture(), any());

        if (SdkLevel.isAtLeastU()) {
            mScanCallbackCaptor.getValue().onScanFailed(WifiScanner.REASON_UNSPECIFIED);
        } else {
            mScanCallbackCaptor.getValue().onScanFailed();
        }
        verify(mWifiMonitor).broadcastScanFailedEvent(WIFI_IFACE_NAME,
                WifiScanner.REASON_UNSPECIFIED);
    }

    /**
     * Verifies client mode + PNO scan success.
     */
    @Test
    public void testClientModePnoScanSuccess() {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                any(), mScanCallbackCaptor.capture());

        mScanCallbackCaptor.getValue().onScanResultReady();
        verify(mWifiMonitor).broadcastPnoScanResultEvent(WIFI_IFACE_NAME);
        verify(mWifiMetrics).incrementPnoFoundNetworkEventCount();
    }

    /**
     * Verifies client mode + PNO scan failure.
     */
    @Test
    public void testClientModePnoScanFailure() {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                any(), mScanCallbackCaptor.capture());

        mScanCallbackCaptor.getValue().onScanFailed();
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__SCAN_FAILED,
                0, false, false, false, false,
                WifiStatsLog.PNO_SCAN_STOPPED__FAILURE_CODE__WIFICOND_SCAN_FAILURE));
    }

    /**
     * Verifies scan mode + scan success.
     */
    @Test
    public void testScanModeScanSuccess() {
        InOrder order = inOrder(mWificondControl, mNetdWrapper, mWifiVendorHal);
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        order.verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                mScanCallbackCaptor.capture(), any());
        order.verify(mNetdWrapper).isInterfaceUp(eq(WIFI_IFACE_NAME));
        order.verify(mWifiVendorHal).enableLinkLayerStats(eq(WIFI_IFACE_NAME));

        mScanCallbackCaptor.getValue().onScanResultReady();
        verify(mWifiMonitor).broadcastScanResultEvent(WIFI_IFACE_NAME);
    }

    /**
     * Verifies scan mode + scan success.
     */
    @Test
    public void testBridgedApModeWifiCondSetupTeardown() {
        String instance1 = "instance1";
        String instance2 = "instance2";
        when(mWifiVendorHal.getBridgedApInstances(WIFI_IFACE_NAME))
                .thenReturn(Arrays.asList(instance1, instance2));
        mWifiNative.setupInterfaceForSoftApMode(null, TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ
                | SoftApConfiguration.BAND_5GHZ, true, mSoftApManager, new ArrayList<>());
        ArgumentCaptor<HalDeviceManager.InterfaceDestroyedListener> ifaceDestroyedListenerCaptor =
                ArgumentCaptor.forClass(HalDeviceManager.InterfaceDestroyedListener.class);
        verify(mWifiVendorHal).createApIface(ifaceDestroyedListenerCaptor.capture(), any(),
                anyInt(), anyBoolean(), any(), anyList());
        verify(mWificondControl).setupInterfaceForSoftApMode(instance1);

        when(mWifiVendorHal.getBridgedApInstances(WIFI_IFACE_NAME)).thenReturn(null);
        ifaceDestroyedListenerCaptor.getValue().onDestroyed(WIFI_IFACE_NAME);

        verify(mWificondControl).tearDownSoftApInterface(instance1);
    }

    /**
     * Verifies scan mode + scan failure.
     */
    @Test
    public void testScanModeScanFailure() {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                mScanCallbackCaptor.capture(), any());

        if (SdkLevel.isAtLeastU()) {
            mScanCallbackCaptor.getValue().onScanFailed(WifiScanner.REASON_UNSPECIFIED);
        } else {
            mScanCallbackCaptor.getValue().onScanFailed();
        }
        verify(mWifiMonitor).broadcastScanFailedEvent(eq(WIFI_IFACE_NAME),
                eq(WifiScanner.REASON_UNSPECIFIED));
    }

    /**
     * Verifies scan mode + PNO scan success.
     */
    @Test
    public void testScanModePnoScanSuccess() {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                any(), mScanCallbackCaptor.capture());

        mScanCallbackCaptor.getValue().onScanResultReady();
        verify(mWifiMonitor).broadcastPnoScanResultEvent(WIFI_IFACE_NAME);
        verify(mWifiMetrics).incrementPnoFoundNetworkEventCount();
    }

    /**
     * Verifies scan mode + PNO scan failure.
     */
    @Test
    public void testScanModePnoScanFailure() {
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWificondControl).setupInterfaceForClientMode(eq(WIFI_IFACE_NAME), any(),
                any(), mScanCallbackCaptor.capture());

        mScanCallbackCaptor.getValue().onScanFailed();
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__SCAN_FAILED,
                0, false, false, false, false,
                WifiStatsLog.PNO_SCAN_STOPPED__FAILURE_CODE__WIFICOND_SCAN_FAILURE));
    }

    /**
     * Verifies starting the hal results in coex unsafe channels being updated with cached values.
     */
    @Test
    public void testStartHalUpdatesCoexUnsafeChannels() {
        assumeTrue(SdkLevel.isAtLeastS());
        final List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        final int restrictions = 0;
        when(mCoexManager.getCoexUnsafeChannels()).thenReturn(unsafeChannels);
        when(mCoexManager.getCoexRestrictions()).thenReturn(restrictions);
        mWifiNative.setCoexUnsafeChannels(unsafeChannels, restrictions);

        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWifiVendorHal, times(2)).setCoexUnsafeChannels(unsafeChannels, restrictions);

        mWifiNative.teardownAllInterfaces();
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWifiVendorHal, times(3)).setCoexUnsafeChannels(unsafeChannels, restrictions);

        mWifiNative.teardownAllInterfaces();
        mWifiNative.setupInterfaceForSoftApMode(null, TEST_WORKSOURCE, WIFI_BAND_24_GHZ, false,
                mSoftApManager, new ArrayList<>());
        verify(mWifiVendorHal, times(4)).setCoexUnsafeChannels(unsafeChannels, restrictions);
    }

    /**
     * Verifies that signalPoll() calls underlying WificondControl.
     */
    @Test
    public void testSignalPoll() throws Exception {
        when(mWificondControl.signalPoll(WIFI_IFACE_NAME))
                .thenReturn(SIGNAL_POLL_RESULT);
        when(mStaIfaceHal.getSignalPollResults(WIFI_IFACE_NAME)).thenReturn(null);

        WifiSignalPollResults pollResults = mWifiNative.signalPoll(WIFI_IFACE_NAME);
        assertEquals(SIGNAL_POLL_RESULT.currentRssiDbm, pollResults.getRssi());
        assertEquals(SIGNAL_POLL_RESULT.txBitrateMbps, pollResults.getTxLinkSpeed());
        assertEquals(SIGNAL_POLL_RESULT.associationFrequencyMHz,
                pollResults.getFrequency());
        assertEquals(SIGNAL_POLL_RESULT.rxBitrateMbps, pollResults.getRxLinkSpeed());

        verify(mWificondControl).signalPoll(WIFI_IFACE_NAME);
    }

    /**
     * Verifies that scan() calls underlying WificondControl.
     */
    @Test
    public void testScan() throws Exception {
        // This test will NOT run if the device has SDK level S or later
        assumeFalse(SdkLevel.isAtLeastS());
        mWifiNative.scan(WIFI_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, SCAN_FREQ_SET,
                SCAN_HIDDEN_NETWORK_SSID_SET, false, null);
        ArgumentCaptor<List<byte[]>> ssidSetCaptor = ArgumentCaptor.forClass(List.class);
        verify(mWificondControl).startScan(
                eq(WIFI_IFACE_NAME), eq(WifiScanner.SCAN_TYPE_HIGH_ACCURACY),
                eq(SCAN_FREQ_SET), ssidSetCaptor.capture());
        List<byte[]> ssidSet = ssidSetCaptor.getValue();
        assertArrayEquals(ssidSet.toArray(), SCAN_HIDDEN_NETWORK_BYTE_SSID_SET.toArray());
    }

    /**
     * Verifies that scan() calls the new startScan API with a Bundle when the Sdk level
     * is S or above.
     */
    @Test
    public void testScanWithBundle() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        byte[] vendorIes =
                new byte[]{(byte) 0xdd, 0x7, 0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33,
                        (byte) 0xdd, 0x7, 0x00, 0x50, (byte) 0xf2, 0x08, 0x44, 0x55, 0x66};
        mWifiNative.scan(WIFI_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, SCAN_FREQ_SET,
                SCAN_HIDDEN_NETWORK_SSID_SET, true, vendorIes);
        ArgumentCaptor<List<byte[]>> ssidSetCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        if (SdkLevel.isAtLeastU()) {
            verify(mWificondControl).startScan2(
                    eq(WIFI_IFACE_NAME), eq(WifiScanner.SCAN_TYPE_HIGH_ACCURACY),
                    eq(SCAN_FREQ_SET), ssidSetCaptor.capture(), bundleCaptor.capture());
        } else {
            verify(mWificondControl).startScan(
                    eq(WIFI_IFACE_NAME), eq(WifiScanner.SCAN_TYPE_HIGH_ACCURACY),
                    eq(SCAN_FREQ_SET), ssidSetCaptor.capture(), bundleCaptor.capture());
        }
        List<byte[]> ssidSet = ssidSetCaptor.getValue();
        assertArrayEquals(ssidSet.toArray(), SCAN_HIDDEN_NETWORK_BYTE_SSID_SET.toArray());
        Bundle bundle = bundleCaptor.getValue();
        assertTrue(bundle.getBoolean(WifiNl80211Manager.SCANNING_PARAM_ENABLE_6GHZ_RNR));
        assertArrayEquals(vendorIes,
                bundle.getByteArray(WifiNl80211Manager.EXTRA_SCANNING_PARAM_VENDOR_IES));
    }

    /**
     * Verifies that startPnoscan() calls underlying WificondControl.
     */
    @Test
    public void testStartPnoScanOnRequestProcessed() throws Exception {
        mWifiNative.startPnoScan(WIFI_IFACE_NAME, TEST_PNO_SETTINGS);

        ArgumentCaptor<WifiNl80211Manager.PnoScanRequestCallback> captor =
                ArgumentCaptor.forClass(WifiNl80211Manager.PnoScanRequestCallback.class);
        verify(mWificondControl).startPnoScan(eq(WIFI_IFACE_NAME),
                eq(TEST_PNO_SETTINGS.toNativePnoSettings()), any(), captor.capture());
        captor.getValue().onPnoRequestSucceeded();
        verify(mWifiMetrics).incrementPnoScanStartAttemptCount();
    }

    /**
     * Verifies that startPnoscan() calls underlying WificondControl.
     */
    @Test
    public void testStartPnoScanOnRequestFailed() throws Exception {
        mWifiNative.startPnoScan(WIFI_IFACE_NAME, TEST_PNO_SETTINGS);

        ArgumentCaptor<WifiNl80211Manager.PnoScanRequestCallback> captor =
                ArgumentCaptor.forClass(WifiNl80211Manager.PnoScanRequestCallback.class);
        verify(mWificondControl).startPnoScan(eq(WIFI_IFACE_NAME),
                eq(TEST_PNO_SETTINGS.toNativePnoSettings()), any(), captor.capture());
        captor.getValue().onPnoRequestFailed();
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__SCAN_FAILED,
                0, false, false, false, false,
                WifiStatsLog.PNO_SCAN_STOPPED__FAILURE_CODE__WIFICOND_REQUEST_FAILURE));
    }

    /**
     * Verifies that stopPnoscan() calls underlying WificondControl.
     */
    @Test
    public void testStopPnoScan() throws Exception {
        mWifiNative.stopPnoScan(WIFI_IFACE_NAME);
        verify(mWificondControl).stopPnoScan(WIFI_IFACE_NAME);
    }

    /**
     * Verifies that getScanResults() can parse NativeScanResult from wificond correctly,
     */
    @Test
    public void testGetScanResults() {
        // Mock the returned array of NativeScanResult.
        List<NativeScanResult> mockScanResults = Arrays.asList(MOCK_NATIVE_SCAN_RESULT);
        when(mWificondControl.getScanResults(anyString(), anyInt())).thenReturn(mockScanResults);

        ArrayList<ScanDetail> returnedScanResults = mWifiNative.getScanResults(WIFI_IFACE_NAME);
        assertEquals(mockScanResults.size(), returnedScanResults.size());
        // Since NativeScanResult is organized differently from ScanResult, this only checks
        // a few fields.
        for (int i = 0; i < mockScanResults.size(); i++) {
            assertEquals(getTranslatedSsid(WifiSsid.fromBytes(mockScanResults.get(i).getSsid())),
                    returnedScanResults.get(i).getScanResult().getWifiSsid());
            assertEquals(mockScanResults.get(i).getFrequencyMhz(),
                    returnedScanResults.get(i).getScanResult().frequency);
            assertEquals(mockScanResults.get(i).getTsf(),
                    returnedScanResults.get(i).getScanResult().timestamp);
        }
    }

    /**
     * Verifies that getScanResults() can parse NativeScanResult from wificond correctly,
     */
    @Test
    public void testGetScanResultsWithInvalidSsidLength() {
        // Mock the returned array of NativeScanResult.
        List<NativeScanResult> mockScanResults = Arrays.asList(createMockNativeScanResult());
        for (NativeScanResult scanResult : mockScanResults) {
            scanResult.ssid = Arrays.copyOf(scanResult.ssid, 33);
        }
        when(mWificondControl.getScanResults(anyString(), anyInt())).thenReturn(mockScanResults);

        assertEquals(0, mWifiNative.getScanResults(WIFI_IFACE_NAME).size());
    }

    /**
     * Verifies that getScanResults() can parse NativeScanResult from wificond correctly,
     * when there is radio chain info.
     */
    @Test
    public void testGetScanResultsWithRadioChainInfo() throws Exception {
        // Mock the returned array of NativeScanResult.
        NativeScanResult nativeScanResult = createMockNativeScanResult();
        // Add radio chain info
        List<RadioChainInfo> nativeRadioChainInfos = Arrays.asList(
                MOCK_NATIVE_RADIO_CHAIN_INFO_1, MOCK_NATIVE_RADIO_CHAIN_INFO_2);
        nativeScanResult.radioChainInfos = nativeRadioChainInfos;
        List<NativeScanResult> mockScanResults = Arrays.asList(nativeScanResult);

        when(mWificondControl.getScanResults(anyString(), anyInt())).thenReturn(mockScanResults);

        ArrayList<ScanDetail> returnedScanResults = mWifiNative.getScanResults(WIFI_IFACE_NAME);
        assertEquals(mockScanResults.size(), returnedScanResults.size());
        // Since NativeScanResult is organized differently from ScanResult, this only checks
        // a few fields.
        for (int i = 0; i < mockScanResults.size(); i++) {
            assertEquals(getTranslatedSsid(WifiSsid.fromBytes(mockScanResults.get(i).getSsid())),
                    returnedScanResults.get(i).getScanResult().getWifiSsid());
            assertEquals(mockScanResults.get(i).getFrequencyMhz(),
                    returnedScanResults.get(i).getScanResult().frequency);
            assertEquals(mockScanResults.get(i).getTsf(),
                    returnedScanResults.get(i).getScanResult().timestamp);
            ScanResult.RadioChainInfo[] scanRcis = returnedScanResults.get(
                    i).getScanResult().radioChainInfos;
            assertEquals(nativeRadioChainInfos.size(), scanRcis.length);
            for (int j = 0; j < scanRcis.length; ++j) {
                assertEquals(nativeRadioChainInfos.get(j).getChainId(), scanRcis[j].id);
                assertEquals(nativeRadioChainInfos.get(j).getLevelDbm(), scanRcis[j].level);
            }
        }
    }

    /**
     * Verifies that connectToNetwork() calls underlying WificondControl and SupplicantStaIfaceHal.
     */
    @Test
    public void testConnectToNetwork() throws Exception {
        WifiConfiguration config = mock(WifiConfiguration.class);
        mWifiNative.connectToNetwork(WIFI_IFACE_NAME, config);
        // connectToNetwork() should abort ongoing scan before connection.
        verify(mWificondControl).abortScan(WIFI_IFACE_NAME);
        verify(mStaIfaceHal).connectToNetwork(WIFI_IFACE_NAME, config);
    }

    /**
     * Verifies that roamToNetwork() calls underlying WificondControl and SupplicantStaIfaceHal.
     */
    @Test
    public void testRoamToNetwork() throws Exception {
        WifiConfiguration config = mock(WifiConfiguration.class);
        mWifiNative.roamToNetwork(WIFI_IFACE_NAME, config);
        // roamToNetwork() should abort ongoing scan before connection.
        verify(mWificondControl).abortScan(WIFI_IFACE_NAME);
        verify(mStaIfaceHal).roamToNetwork(WIFI_IFACE_NAME, config);
    }

    /**
     * Verifies that removeIfaceInstanceFromBridgedApIface() calls underlying WifiVendorHal.
     */
    @Test
    public void testRemoveIfaceInstanceFromBridgedApIface() throws Exception {
        mWifiNative.removeIfaceInstanceFromBridgedApIface(
                "br_" + WIFI_IFACE_NAME, WIFI_IFACE_NAME);
        verify(mWifiVendorHal).removeIfaceInstanceFromBridgedApIface(
                "br_" + WIFI_IFACE_NAME, WIFI_IFACE_NAME);
    }

    /**
     * Verifies that setMacAddress() calls underlying WifiVendorHal.
     */
    @Test
    public void testStaSetMacAddress() throws Exception {
        mWifiNative.setStaMacAddress(WIFI_IFACE_NAME, TEST_MAC_ADDRESS);
        verify(mStaIfaceHal).disconnect(WIFI_IFACE_NAME);
        verify(mWifiVendorHal).setStaMacAddress(WIFI_IFACE_NAME, TEST_MAC_ADDRESS);
    }

    /**
     * Verifies that setMacAddress() calls underlying WifiVendorHal.
     */
    @Test
    public void testApSetMacAddress() throws Exception {
        mWifiNative.setApMacAddress(WIFI_IFACE_NAME, TEST_MAC_ADDRESS);
        verify(mWifiVendorHal).setApMacAddress(WIFI_IFACE_NAME, TEST_MAC_ADDRESS);
    }

    /**
     * Verifies that resetApMacToFactoryMacAddress() calls underlying WifiVendorHal.
     */
    @Test
    public void testResetApMacToFactoryMacAddress() throws Exception {
        mWifiNative.resetApMacToFactoryMacAddress(WIFI_IFACE_NAME);
        verify(mWifiVendorHal).resetApMacToFactoryMacAddress(WIFI_IFACE_NAME);
    }

    /**
     * Verifies that setCoexUnsafeChannels() calls underlying WifiVendorHal.
     */
    @Test
    public void testSetCoexUnsafeChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiNative.setCoexUnsafeChannels(Collections.emptyList(), 0);
        verify(mWifiVendorHal).setCoexUnsafeChannels(Collections.emptyList(), 0);
    }

    /**
     * Verifies that isSetMacAddressSupported() calls underlying WifiVendorHal.
     */
    @Test
    public void testIsApSetMacAddressSupported() throws Exception {
        mWifiNative.isApSetMacAddressSupported(WIFI_IFACE_NAME);
        verify(mWifiVendorHal).isApSetMacAddressSupported(WIFI_IFACE_NAME);
    }

    /**
     * Test that selectTxPowerScenario() calls into WifiVendorHal (success case)
     */
    @Test
    public void testSelectTxPowerScenario_success() throws Exception {
        when(mWifiVendorHal.selectTxPowerScenario(any(SarInfo.class))).thenReturn(true);
        SarInfo sarInfo = new SarInfo();
        assertTrue(mWifiNative.selectTxPowerScenario(sarInfo));
        verify(mWifiVendorHal).selectTxPowerScenario(sarInfo);
    }

    /**
     * Test that selectTxPowerScenario() calls into WifiVendorHal (failure case)
     */
    @Test
    public void testSelectTxPowerScenario_failure() throws Exception {
        when(mWifiVendorHal.selectTxPowerScenario(any(SarInfo.class))).thenReturn(false);
        SarInfo sarInfo = new SarInfo();
        assertFalse(mWifiNative.selectTxPowerScenario(sarInfo));
        verify(mWifiVendorHal).selectTxPowerScenario(sarInfo);
    }

    /**
     * Test that setPowerSave() with true, results in calling into SupplicantStaIfaceHal
     */
    @Test
    public void testSetPowerSaveTrue() throws Exception {
        mWifiNative.setPowerSave(WIFI_IFACE_NAME, true);
        verify(mStaIfaceHal).setPowerSave(WIFI_IFACE_NAME, true);
    }

    /**
     * Test that setPowerSave() with false, results in calling into SupplicantStaIfaceHal
     */
    @Test
    public void testSetPowerSaveFalse() throws Exception {
        mWifiNative.setPowerSave(WIFI_IFACE_NAME, false);
        verify(mStaIfaceHal).setPowerSave(WIFI_IFACE_NAME, false);
    }

    /**
     * Test that setLowLatencyMode() with true, results in calling into WifiVendorHal
     */
    @Test
    public void testLowLatencyModeTrue() throws Exception {
        when(mWifiVendorHal.setLowLatencyMode(anyBoolean())).thenReturn(true);
        assertTrue(mWifiNative.setLowLatencyMode(true));
        verify(mWifiVendorHal).setLowLatencyMode(true);
    }

    /**
     * Test that setLowLatencyMode() with false, results in calling into WifiVendorHal
     */
    @Test
    public void testLowLatencyModeFalse() throws Exception {
        when(mWifiVendorHal.setLowLatencyMode(anyBoolean())).thenReturn(true);
        assertTrue(mWifiNative.setLowLatencyMode(false));
        verify(mWifiVendorHal).setLowLatencyMode(false);
    }

    /**
     * Test that setLowLatencyMode() returns with failure when WifiVendorHal fails.
     */
    @Test
    public void testSetLowLatencyModeFail() throws Exception {
        final boolean lowLatencyMode = true;
        when(mWifiVendorHal.setLowLatencyMode(anyBoolean())).thenReturn(false);
        assertFalse(mWifiNative.setLowLatencyMode(lowLatencyMode));
        verify(mWifiVendorHal).setLowLatencyMode(lowLatencyMode);
    }

    @Test
    public void testStaGetFactoryMacAddress() throws Exception {
        when(mWifiVendorHal.getStaFactoryMacAddress(any()))
                .thenReturn(MacAddress.BROADCAST_ADDRESS);
        assertNotNull(mWifiNative.getStaFactoryMacAddress(WIFI_IFACE_NAME));
        verify(mWifiVendorHal).getStaFactoryMacAddress(any());
    }


    @Test
    public void testGetApFactoryMacAddress() throws Exception {
        when(mWifiVendorHal.getApFactoryMacAddress(any())).thenReturn(MacAddress.BROADCAST_ADDRESS);
        assertNotNull(mWifiNative.getApFactoryMacAddress(WIFI_IFACE_NAME));
        verify(mWifiVendorHal).getApFactoryMacAddress(any());
    }

    /**
     * Test that flushRingBufferData(), results in calling into WifiVendorHal
     */
    @Test
    public void testFlushRingBufferDataTrue() throws Exception {
        when(mWifiVendorHal.flushRingBufferData()).thenReturn(true);
        assertTrue(mWifiNative.flushRingBufferData());
        verify(mWifiVendorHal).flushRingBufferData();
    }

    /**
     * Tests that WifiNative#sendMgmtFrame() calls WificondControl#sendMgmtFrame()
     */
    @Test
    public void testSendMgmtFrame() {
        mWifiNative.sendMgmtFrame(WIFI_IFACE_NAME, FATE_REPORT_FRAME_BYTES,
                mSendMgmtFrameCallback, TEST_MCS_RATE);

        verify(mWificondControl).sendMgmtFrame(eq(WIFI_IFACE_NAME),
                AdditionalMatchers.aryEq(FATE_REPORT_FRAME_BYTES), eq(TEST_MCS_RATE),
                any(), eq(mSendMgmtFrameCallback));
    }

    /**
     * Tests that probeLink() generates the correct frame and calls WificondControl#sendMgmtFrame().
     */
    @Test
    public void testProbeLinkSuccess() {
        byte[] expectedFrame = {
                0x40, 0x00, 0x3c, 0x00, (byte) 0xa8, (byte) 0xbd, 0x27, 0x5b,
                0x33, 0x72, (byte) 0xf4, (byte) 0xf5, (byte) 0xe8, 0x51, (byte) 0x9e, 0x09,
                (byte) 0xa8, (byte) 0xbd, 0x27, 0x5b, 0x33, 0x72, (byte) 0xb0, 0x66,
                0x00, 0x00
        };

        when(mStaIfaceHal.getMacAddress(WIFI_IFACE_NAME)).thenReturn(TEST_MAC_ADDRESS_STR);

        when(mRandom.nextInt()).thenReturn(TEST_SEQUENCE_NUM);

        mWifiNative.probeLink(WIFI_IFACE_NAME, MacAddress.fromString(TEST_BSSID_STR),
                mSendMgmtFrameCallback, TEST_MCS_RATE);

        verify(mSendMgmtFrameCallback, never()).onFailure(anyInt());
        verify(mWificondControl).sendMgmtFrame(eq(WIFI_IFACE_NAME),
                AdditionalMatchers.aryEq(expectedFrame), eq(TEST_MCS_RATE),
                any(), eq(mSendMgmtFrameCallback));
    }

    /**
     * Tests that probeLink() triggers the failure callback when it cannot get the sender MAC
     * address.
     */
    @Test
    public void testProbeLinkFailureCannotGetSenderMac() {
        when(mStaIfaceHal.getMacAddress(WIFI_IFACE_NAME)).thenReturn(null);

        mWifiNative.probeLink(WIFI_IFACE_NAME, MacAddress.fromString(TEST_BSSID_STR),
                mSendMgmtFrameCallback, TEST_MCS_RATE);

        verify(mSendMgmtFrameCallback).onFailure(
                WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
        verify(mWificondControl, never()).sendMgmtFrame(any(), any(), anyInt(), any(), any());
    }

    /**
     * Tests that probeLink() triggers the failure callback when it cannot get the BSSID.
     */
    @Test
    public void testProbeLinkFailureCannotGetBssid() {
        when(mStaIfaceHal.getMacAddress(WIFI_IFACE_NAME)).thenReturn(TEST_MAC_ADDRESS_STR);

        mWifiNative.probeLink(WIFI_IFACE_NAME, null, mSendMgmtFrameCallback, TEST_MCS_RATE);

        verify(mSendMgmtFrameCallback).onFailure(
                WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
        verify(mWificondControl, never()).sendMgmtFrame(any(), any(), anyInt(), any(), any());
    }

    /**
     * Tests that WifiNative#addHlpReq() calls
     * SupplicantStaIfaceHal#addHlpReq()
     */
    @Test
    public void testaddHlpReq() {
        byte[] hlpPacket = {
                0x40, 0x00, 0x3c, 0x00, (byte) 0xa8, (byte) 0xbd, 0x27, 0x5b,
                0x33, 0x72, (byte) 0xf4, (byte) 0xf5, (byte) 0xe8, 0x51, (byte) 0x9e, 0x09,
                (byte) 0xa8, (byte) 0xbd, 0x27, 0x5b, 0x33, 0x72, (byte) 0xb0, 0x66,
                0x00, 0x00
        };
        mWifiNative.addHlpReq(WIFI_IFACE_NAME, TEST_MAC_ADDRESS, hlpPacket);

        verify(mStaIfaceHal).addHlpReq(eq(WIFI_IFACE_NAME),
                eq(TEST_MAC_ADDRESS.toByteArray()), eq(hlpPacket));
    }

    /**
     * Tests that WifiNative#flushAllHlp() calls
     * SupplicantStaIfaceHal#flushAllHlp()
     */
    @Test
    public void testflushAllHlp() {
        mWifiNative.flushAllHlp(WIFI_IFACE_NAME);

        verify(mStaIfaceHal).flushAllHlp(eq(WIFI_IFACE_NAME));
    }

    @Test
    public void testIsItPossibleToCreateIface() {
        // HAL not started
        when(mWifiVendorHal.isHalStarted()).thenReturn(false);
        // Using any() here since SparseArray doesn't support Object.equals().
        when(mWifiVendorHal.canDeviceSupportCreateTypeCombo(any())).thenReturn(true);
        when(mWifiVendorHal.isItPossibleToCreateStaIface(any())).thenReturn(false);
        assertTrue(mWifiNative.isItPossibleToCreateStaIface(new WorkSource()));

        when(mWifiVendorHal.isItPossibleToCreateApIface(any())).thenReturn(false);
        assertTrue(mWifiNative.isItPossibleToCreateApIface(new WorkSource()));

        when(mWifiVendorHal.isItPossibleToCreateBridgedApIface(any())).thenReturn(false);
        assertTrue(mWifiNative.isItPossibleToCreateBridgedApIface(new WorkSource()));

        // HAL started
        when(mWifiVendorHal.isHalStarted()).thenReturn(true);
        when(mWifiVendorHal.isItPossibleToCreateStaIface(any())).thenReturn(true);
        assertTrue(mWifiNative.isItPossibleToCreateStaIface(new WorkSource()));

        when(mWifiVendorHal.isItPossibleToCreateApIface(any())).thenReturn(true);
        assertTrue(mWifiNative.isItPossibleToCreateApIface(new WorkSource()));

        when(mWifiVendorHal.isItPossibleToCreateBridgedApIface(any())).thenReturn(true);
        assertTrue(mWifiNative.isItPossibleToCreateBridgedApIface(new WorkSource()));
    }

    @Test
    public void testReplaceStaIfaceRequestorWs() {
        assertEquals(WIFI_IFACE_NAME,
                mWifiNative.setupInterfaceForClientInScanMode(
                        mInterfaceCallback, TEST_WORKSOURCE, mConcreteClientModeManager));
        when(mWifiVendorHal.replaceStaIfaceRequestorWs(WIFI_IFACE_NAME, TEST_WORKSOURCE2))
                .thenReturn(true);

        assertTrue(mWifiNative.replaceStaIfaceRequestorWs(WIFI_IFACE_NAME, TEST_WORKSOURCE2));
        verify(mWifiVendorHal).replaceStaIfaceRequestorWs(
                eq(WIFI_IFACE_NAME), same(TEST_WORKSOURCE2));
    }

    /**
     * Verifies that updateLinkedNetworks() calls underlying SupplicantStaIfaceHal.
     */
    @Test
    public void testUpdateLinkedNetworks() {
        when(mStaIfaceHal.updateLinkedNetworks(any(), anyInt(), any())).thenReturn(true);

        assertTrue(mWifiNative.updateLinkedNetworks(WIFI_IFACE_NAME, 0, null));
        verify(mStaIfaceHal).updateLinkedNetworks(WIFI_IFACE_NAME, 0, null);
    }

    /**
     * Verifies that getEapAnonymousIdentity() works as expected.
     */
    @Test
    public void testGetEapAnonymousIdentity() {
        // Verify the empty use case
        when(mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(WIFI_IFACE_NAME))
                .thenReturn("");
        assertTrue(TextUtils.isEmpty(mWifiNative.getEapAnonymousIdentity(WIFI_IFACE_NAME)));

        // Verify with an anonymous identity
        final String anonymousId = "anonymous@homerealm.example.org";
        when(mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(WIFI_IFACE_NAME))
                .thenReturn(anonymousId);
        assertEquals(anonymousId, mWifiNative.getEapAnonymousIdentity(WIFI_IFACE_NAME));

        // Verify with a pseudonym identity
        final String pseudonymId = "a4624bc22490da3@homerealm.example.org";
        when(mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(WIFI_IFACE_NAME))
                .thenReturn(pseudonymId);
        assertEquals(pseudonymId, mWifiNative.getEapAnonymousIdentity(WIFI_IFACE_NAME));

        // Verify that decorated anonymous identity is truncated
        when(mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(WIFI_IFACE_NAME))
                .thenReturn("otherrealm.example.net!" + anonymousId);
        assertEquals(anonymousId, mWifiNative.getEapAnonymousIdentity(WIFI_IFACE_NAME));

        // Verify that recursive decorated anonymous identity is truncated
        when(mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(WIFI_IFACE_NAME))
                .thenReturn("proxyrealm.example.com!otherrealm.example.net!" + anonymousId);
        assertEquals(anonymousId, mWifiNative.getEapAnonymousIdentity(WIFI_IFACE_NAME));

        // Verify an invalid decoration with no identity use cases
        when(mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(WIFI_IFACE_NAME))
                .thenReturn("otherrealm.example.net!");
        assertNull(mWifiNative.getEapAnonymousIdentity(WIFI_IFACE_NAME));
    }


    @Test
    public void testCountryCodeChangedListener() {
        assumeTrue(SdkLevel.isAtLeastS());
        final String testCountryCode = "US";
        ArgumentCaptor<WifiNl80211Manager.CountryCodeChangedListener>
                mCountryCodeChangedListenerCaptor = ArgumentCaptor.forClass(
                WifiNl80211Manager.CountryCodeChangedListener.class);
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        verify(mWificondControl).registerCountryCodeChangedListener(any(),
                mCountryCodeChangedListenerCaptor.capture());
        mCountryCodeChangedListenerCaptor.getValue().onCountryCodeChanged(testCountryCode);
        verify(mWifiCountryCodeChangeListener).onDriverCountryCodeChanged(testCountryCode);
    }

    @Test
    public void testSetStaCountryCodeSuccessful() {
        when(mStaIfaceHal.setCountryCode(any(), any())).thenReturn(true);
        final String testCountryCode = "US";
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        mWifiNative.setStaCountryCode(WIFI_IFACE_NAME, testCountryCode);
        verify(mStaIfaceHal).setCountryCode(WIFI_IFACE_NAME, testCountryCode);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiCountryCodeChangeListener).onSetCountryCodeSucceeded(testCountryCode);
        }
    }

    @Test
    public void testSetStaCountryCodeFailure() {
        when(mStaIfaceHal.setCountryCode(any(), any())).thenReturn(false);
        final String testCountryCode = "US";
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        mWifiNative.setStaCountryCode(WIFI_IFACE_NAME, testCountryCode);
        verify(mStaIfaceHal).setCountryCode(WIFI_IFACE_NAME, testCountryCode);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiCountryCodeChangeListener, never())
                    .onSetCountryCodeSucceeded(testCountryCode);
        }
    }

    /**
     * Verifies setEapAnonymousIdentity() sunny case.
     */
    @Test
    public void testSetEapAnonymousIdentitySuccess() throws Exception {
        when(mStaIfaceHal.setEapAnonymousIdentity(any(), any(), anyBoolean())).thenReturn(true);
        final String anonymousIdentity = "abc@realm.com";
        assertTrue(mWifiNative.setEapAnonymousIdentity(WIFI_IFACE_NAME, anonymousIdentity, true));
        verify(mStaIfaceHal).setEapAnonymousIdentity(eq(WIFI_IFACE_NAME),
                eq(anonymousIdentity), eq(true));
    }

    /**
     * Verifies setEapAnonymousIdentity() sunny case when native service is
     * not updated.
     */
    @Test
    public void testSetEapAnonymousIdentitySuccessWithNotUpdateToNativeService() throws Exception {
        when(mStaIfaceHal.setEapAnonymousIdentity(any(), any(), anyBoolean())).thenReturn(true);
        final String anonymousIdentity = "abc@realm.com";
        assertTrue(mWifiNative.setEapAnonymousIdentity(WIFI_IFACE_NAME, anonymousIdentity, false));
        verify(mStaIfaceHal).setEapAnonymousIdentity(eq(WIFI_IFACE_NAME),
                eq(anonymousIdentity), eq(false));
    }

    /**
     * Verifies that setEapAnonymousIdentity() fails with null anonymous identity.
     */
    @Test
    public void testSetEapAnonymousIdentityFailureWithNullString() throws Exception {
        when(mStaIfaceHal.setEapAnonymousIdentity(any(), any(), anyBoolean())).thenReturn(true);
        assertFalse(mWifiNative.setEapAnonymousIdentity(WIFI_IFACE_NAME, null, true));
        verify(mStaIfaceHal, never()).setEapAnonymousIdentity(any(), any(), anyBoolean());
    }

    @Test
    public void testSetApCountryCodeSuccessful() {
        when(mWifiVendorHal.setApCountryCode(any(), any())).thenReturn(true);
        final String testCountryCode = "US";
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        mWifiNative.setApCountryCode(WIFI_IFACE_NAME, testCountryCode);
        verify(mWifiVendorHal).setApCountryCode(WIFI_IFACE_NAME, testCountryCode);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiCountryCodeChangeListener).onSetCountryCodeSucceeded(testCountryCode);
        }
    }

    @Test
    public void testSetApCountryCodeFailure() {
        when(mWifiVendorHal.setApCountryCode(any(), any())).thenReturn(false);
        final String testCountryCode = "US";
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        mWifiNative.setApCountryCode(WIFI_IFACE_NAME, testCountryCode);
        verify(mWifiVendorHal).setApCountryCode(WIFI_IFACE_NAME, testCountryCode);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiCountryCodeChangeListener, never())
                    .onSetCountryCodeSucceeded(testCountryCode);
        }
    }

    @Test
    public void testSetChipCountryCodeSuccessful() {
        when(mWifiVendorHal.setChipCountryCode(any())).thenReturn(true);
        final String testCountryCode = "US";
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        mWifiNative.setChipCountryCode(testCountryCode);
        verify(mWifiVendorHal).setChipCountryCode(testCountryCode);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiCountryCodeChangeListener).onSetCountryCodeSucceeded(testCountryCode);
        }
    }

    @Test
    public void testSetChipCountryCodeFailure() {
        when(mWifiVendorHal.setChipCountryCode(any())).thenReturn(false);
        final String testCountryCode = "US";
        mWifiNative.registerCountryCodeEventListener(mWifiCountryCodeChangeListener);
        mWifiNative.setChipCountryCode(testCountryCode);
        verify(mWifiVendorHal).setChipCountryCode(testCountryCode);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiCountryCodeChangeListener, never())
                .onSetCountryCodeSucceeded(testCountryCode);
        }
    }

    /**
     * Tests notifyWifiCondCountryCodeChanged
     */
    @Test
    public void testNotifyWifiCondCountryCodeChanged() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        String testCountryCode = "US";
        mWifiNative.countryCodeChanged(testCountryCode);
        verify(mWificondControl).notifyCountryCodeChanged(testCountryCode);
    }

    /**
     * Tests that getSupportedFeatureSet() guaranteed to include the feature set stored in config
     * store even when interface doesn't exist.
     *
     */
    @Test
    public void testGetSupportedFeatureSetWhenInterfaceDoesntExist() throws Exception {
        long featureSet = mWifiNative.getSupportedFeatureSet(null);
        assertEquals(featureSet, WIFI_TEST_FEATURE);
    }

    /**
     * Verifies that getSupportedBandsForSta() calls underlying vendor HAL.
     */
    @Test
    public void testGetSupportedBandsFromHal() throws Exception {
        List<WifiAvailableChannel> usableChannelList = new ArrayList<>();
        usableChannelList.add(new WifiAvailableChannel(2412, WifiAvailableChannel.OP_MODE_STA));
        usableChannelList.add(new WifiAvailableChannel(5160, WifiAvailableChannel.OP_MODE_STA));
        when(mWifiVendorHal.getUsableChannels(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ,
                WifiAvailableChannel.OP_MODE_STA,
                WifiAvailableChannel.FILTER_REGULATORY)).thenReturn(usableChannelList);
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        mWifiNative.switchClientInterfaceToConnectivityMode(WIFI_IFACE_NAME, TEST_WORKSOURCE);
        assertEquals(3, mWifiNative.getSupportedBandsForSta(WIFI_IFACE_NAME));
    }

    /**
     * Verifies that getSupportedBandsForStaFromWifiCond() calls underlying wificond.
     */
    @Test
    public void testGetSupportedBands() throws Exception {
        when(mWificondControl.getChannelsMhzForBand(WifiScanner.WIFI_BAND_24_GHZ)).thenReturn(
                new int[]{2412});
        when(mWificondControl.getChannelsMhzForBand(WifiScanner.WIFI_BAND_5_GHZ)).thenReturn(
                new int[]{5160});
        when(mWificondControl.getChannelsMhzForBand(WifiScanner.WIFI_BAND_6_GHZ)).thenReturn(
                new int[0]);
        when(mWificondControl.getChannelsMhzForBand(WifiScanner.WIFI_BAND_60_GHZ)).thenReturn(
                new int[0]);
        when(mWifiVendorHal.getUsableChannels(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ,
                WifiAvailableChannel.OP_MODE_STA,
                WifiAvailableChannel.FILTER_REGULATORY)).thenReturn(null);
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        mWifiNative.switchClientInterfaceToConnectivityMode(WIFI_IFACE_NAME, TEST_WORKSOURCE);
        verify(mWificondControl).getChannelsMhzForBand(WifiScanner.WIFI_BAND_24_GHZ);
        verify(mWificondControl).getChannelsMhzForBand(WifiScanner.WIFI_BAND_5_GHZ);
        assertEquals(3, mWifiNative.getSupportedBandsForSta(WIFI_IFACE_NAME));
    }

    /**
     * Verifies that isSoftApInstanceDiedHandlerSupported() calls underlying HostapdHal.
     */
    @Test
    public void testIsSoftApInstanceDiedHandlerSupported() throws Exception {
        mWifiNative.isSoftApInstanceDiedHandlerSupported();
        verify(mHostapdHal).isSoftApInstanceDiedHandlerSupported();
    }

    @Test
    public void testGetCachedScanResultsLocationDisabledOrInvalidTimestamp() throws Exception {
        ScanResult[] scanResults = new ScanResult[2];
        for (int i = 0; i < 2; i++) {
            ScanResult scanResult = new ScanResult();
            scanResult.timestamp = 0;
            scanResults[i] = scanResult;
        }
        ScanData testScanData = new ScanData(0, 0,
                0, WifiScanner.WIFI_BAND_UNSPECIFIED, scanResults);
        when(mWifiVendorHal.getCachedScanData(any())).thenReturn(testScanData);

        mWifiNative.setLocationModeEnabled(false);
        ScanData scanData = mWifiNative.getCachedScanResults(WIFI_IFACE_NAME);
        // Get no scan result because the location mode is disabled
        assertEquals(0, scanData.getResults().length);

        mWifiNative.setLocationModeEnabled(true);
        scanData = mWifiNative.getCachedScanResults(WIFI_IFACE_NAME);
        // Get no scan result because the scan timestamp is too new
        assertEquals(0, scanData.getResults().length);
    }

    @Test
    public void testGetCachedScanResultsLocationEnabledValidTimestamp() throws Exception {
        ScanResult[] scanResults = new ScanResult[3];
        for (int i = 0; i < 3; i++) {
            ScanResult scanResult = new ScanResult();
            // 1st ScanResult has invalid timestamp
            scanResult.timestamp = (i > 0) ? Long.MAX_VALUE : 0;
            scanResults[i] = scanResult;
        }
        ScanData testScanData = new ScanData(0, 0,
                0, WifiScanner.WIFI_BAND_UNSPECIFIED, scanResults);
        when(mWifiVendorHal.getCachedScanData(any())).thenReturn(testScanData);

        mWifiNative.setLocationModeEnabled(true);
        ScanData scanData = mWifiNative.getCachedScanResults(WIFI_IFACE_NAME);
        // Get the last two scan results which has the valid timestamp
        assertEquals(2, scanData.getResults().length);
    }

    @Test
    public void testEnableStaChannelForPeerNetworkWithOverride() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableStaIndoorChannelForPeerNetwork, true);
        mResources.setBoolean(R.bool.config_wifiEnableStaDfsChannelForPeerNetwork, true);
        mWifiNative.setupInterfaceForClientInScanMode(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mWifiVendorHal).enableStaChannelForPeerNetwork(true, true);
    }

    /**
     * Verifies that setAfcChannelAllowance() calls underlying WifiVendorHal.
     */
    @Test
    public void testSetAfcChannelAllowance() {
        mWifiNative.setAfcChannelAllowance(mAfcChannelAllowance);
        verify(mWifiVendorHal).setAfcChannelAllowance(mAfcChannelAllowance);
    }

    /**
     * Verifies that overlay config item config_wifiUnknownAkmToKnownAkmMapping is parsed correctly
     * and an expected value is set in unknown AKM map.
     */
    @Test
    public void testConfigWifiUnknownAkmToKnownAkmMapping() throws Exception {
        // Test that UnknownAkmMap is not set if two values are not added in the config.
        mResources.setStringArray(
                R.array.config_wifiUnknownAkmToKnownAkmMapping, new String[] {"1234"});
        WifiNative wifiNativeInstance =
                new WifiNative(
                        mWifiVendorHal,
                        mStaIfaceHal,
                        mHostapdHal,
                        mWificondControl,
                        mWifiMonitor,
                        mPropertyService,
                        mWifiMetrics,
                        mHandler,
                        mRandom,
                        mBuildProperties,
                        mWifiInjector);
        assertNull(wifiNativeInstance.mUnknownAkmMap);

        // Test that UnknownAkmMap is not set if non-integer values are added in the config.
        mResources.setStringArray(
                R.array.config_wifiUnknownAkmToKnownAkmMapping, new String[] {"1234, bad"});
        wifiNativeInstance =
                new WifiNative(
                        mWifiVendorHal,
                        mStaIfaceHal,
                        mHostapdHal,
                        mWificondControl,
                        mWifiMonitor,
                        mPropertyService,
                        mWifiMetrics,
                        mHandler,
                        mRandom,
                        mBuildProperties,
                        mWifiInjector);
        assertNull(wifiNativeInstance.mUnknownAkmMap);

        // Test that UnknownAkmMap is not set when an invalid AKM is set in the known AKM field
        // known AKM - 555 (which is not a valid AKM suite specifier)
        mResources.setStringArray(
                R.array.config_wifiUnknownAkmToKnownAkmMapping, new String[] {"9846784, 555"});
        wifiNativeInstance =
                new WifiNative(
                        mWifiVendorHal,
                        mStaIfaceHal,
                        mHostapdHal,
                        mWificondControl,
                        mWifiMonitor,
                        mPropertyService,
                        mWifiMetrics,
                        mHandler,
                        mRandom,
                        mBuildProperties,
                        mWifiInjector);
        assertNull(wifiNativeInstance.mUnknownAkmMap);

        // Test that UnknownAkmMap is set for a valid configuration
        // known AKM - 28053248 (which corresponds to ScanResult.KEY_MGMT_EAP)
        mResources.setStringArray(
                R.array.config_wifiUnknownAkmToKnownAkmMapping, new String[] {"9846784, 28053248"});
        wifiNativeInstance =
                new WifiNative(
                        mWifiVendorHal,
                        mStaIfaceHal,
                        mHostapdHal,
                        mWificondControl,
                        mWifiMonitor,
                        mPropertyService,
                        mWifiMetrics,
                        mHandler,
                        mRandom,
                        mBuildProperties,
                        mWifiInjector);
        assertEquals(1, wifiNativeInstance.mUnknownAkmMap.size());
        assertEquals(ScanResult.KEY_MGMT_EAP, wifiNativeInstance.mUnknownAkmMap.get(9846784));

        // Test that UnknownAkmMap is set for multiple valid configuration entries
        // known AKM - 28053248 (which corresponds to ScanResult.KEY_MGMT_EAP)
        // known AKM - 413929216 (which corresponds to ScanResult.KEY_MGMT_SAE_EXT_KEY)
        mResources.setStringArray(
                R.array.config_wifiUnknownAkmToKnownAkmMapping,
                new String[] {"9846784, 28053248", "1234, 413929216"});
        wifiNativeInstance =
                new WifiNative(
                        mWifiVendorHal,
                        mStaIfaceHal,
                        mHostapdHal,
                        mWificondControl,
                        mWifiMonitor,
                        mPropertyService,
                        mWifiMetrics,
                        mHandler,
                        mRandom,
                        mBuildProperties,
                        mWifiInjector);
        assertEquals(2, wifiNativeInstance.mUnknownAkmMap.size());
        assertEquals(ScanResult.KEY_MGMT_EAP, wifiNativeInstance.mUnknownAkmMap.get(9846784));
        assertEquals(ScanResult.KEY_MGMT_SAE_EXT_KEY, wifiNativeInstance.mUnknownAkmMap.get(1234));
    }

    @Test
    public void testSetRoamingMode() throws Exception {
        int status = 0;
        when(mWifiVendorHal.setRoamingMode(eq(WIFI_IFACE_NAME), anyInt())).thenReturn(status);
        assertEquals(status, mWifiNative.setRoamingMode(WIFI_IFACE_NAME,
                WifiManager.ROAMING_MODE_NORMAL));
        verify(mWifiVendorHal).setRoamingMode(WIFI_IFACE_NAME, WifiManager.ROAMING_MODE_NORMAL);
    }
}
