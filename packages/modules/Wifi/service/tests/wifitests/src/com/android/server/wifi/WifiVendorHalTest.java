/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_STA;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.wifi.WifiStatusCode;
import android.net.InetAddresses;
import android.net.KeepalivePacketData;
import android.net.MacAddress;
import android.net.NattKeepalivePacketData;
import android.net.apf.ApfCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.RemoteException;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.server.wifi.HalDeviceManager.InterfaceDestroyedListener;
import com.android.server.wifi.hal.WifiApIface;
import com.android.server.wifi.hal.WifiChip;
import com.android.server.wifi.hal.WifiHal;
import com.android.server.wifi.hal.WifiStaIface;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.wifi.WifiVendorHal}.
 */
@SmallTest
public class WifiVendorHalTest extends WifiBaseTest {
    private static final String TEST_IFACE_NAME = "wlan0";
    private static final String TEST_IFACE_NAME_1 = "wlan1";
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("ee:33:a2:94:10:92");
    private static final WorkSource TEST_WORKSOURCE = new WorkSource();

    private WifiVendorHal mWifiVendorHal;
    private WifiLog mWifiLog;
    private TestLooper mLooper;
    private Handler mHandler;
    @Mock
    private Resources mResources;
    @Mock
    private Context mContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private HalDeviceManager mHalDeviceManager;
    @Mock
    private WifiVendorHal.HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    @Mock
    private WifiApIface mWifiApIface;
    @Mock
    private WifiChip mWifiChip;
    @Mock
    private WifiStaIface mWifiStaIface;
    private WifiStaIface.Callback mWifiStaIfaceEventCallback;
    private WifiChip.Callback mWifiChipEventCallback;
    @Mock
    private WifiNative.VendorHalDeathEventHandler mVendorHalDeathHandler;
    @Mock
    private WifiNative.VendorHalRadioModeChangeEventHandler mVendorHalRadioModeChangeHandler;
    @Mock
    private WifiGlobals mWifiGlobals;
    @Mock
    private ConcreteClientModeManager mConcreteClientModeManager;
    @Mock
    private SoftApManager mSoftApManager;
    @Mock
    private SsidTranslator mSsidTranslator;
    @Mock
    WifiChip.AfcChannelAllowance mAfcChannelAllowance;

    private ArgumentCaptor<List> mListCaptor = ArgumentCaptor.forClass(List.class);

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiLog = new FakeWifiLog();
        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        when(mWifiStaIface.enableLinkLayerStatsCollection(false)).thenReturn(true);

        // Setup the HalDeviceManager mock's start/stop behaviour. This can be overridden in
        // individual tests, if needed.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(true);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
                mLooper.dispatchAll();
                return true;
            }
        }).when(mHalDeviceManager).start();

        doAnswer(new AnswerWithArguments() {
            public void answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(false);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
                mLooper.dispatchAll();
            }
        }).when(mHalDeviceManager).stop();
        when(mHalDeviceManager.createStaIface(any(), any(), any(), eq(mConcreteClientModeManager)))
                .thenReturn(mWifiStaIface);
        when(mHalDeviceManager.createApIface(anyLong(), any(), any(), any(), anyBoolean(),
                eq(mSoftApManager), anyList()))
                .thenReturn(mWifiApIface);
        when(mHalDeviceManager.removeIface(any())).thenReturn(true);
        when(mHalDeviceManager.getChip(any(WifiHal.WifiInterface.class)))
                .thenReturn(mWifiChip);
        when(mHalDeviceManager.isSupported()).thenReturn(true);
        when(mWifiChip.registerCallback(any(WifiChip.Callback.class)))
                .thenReturn(true);
        mWifiStaIfaceEventCallback = null;

        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiStaIface.Callback callback) {
                mWifiStaIfaceEventCallback = callback;
                return true;
            }
        }).when(mWifiStaIface).registerFrameworkCallback(any(WifiStaIface.Callback.class));
        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiChip.Callback callback) {
                mWifiChipEventCallback = callback;
                return true;
            }
        }).when(mWifiChip).registerCallback(any(WifiChip.Callback.class));
        when(mWifiStaIface.getName()).thenReturn(TEST_IFACE_NAME);
        when(mWifiApIface.getName()).thenReturn(TEST_IFACE_NAME);

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled))
                .thenReturn(false);
        // Create the vendor HAL object under test.
        mWifiVendorHal = new WifiVendorHal(mContext, mHalDeviceManager, mHandler, mWifiGlobals,
                mSsidTranslator);

        // Initialize the vendor HAL to capture the registered callback.
        mWifiVendorHal.initialize(mVendorHalDeathHandler);
        ArgumentCaptor<WifiVendorHal.HalDeviceManagerStatusListener> hdmCallbackCaptor =
                ArgumentCaptor.forClass(WifiVendorHal.HalDeviceManagerStatusListener.class);
        verify(mHalDeviceManager).registerStatusListener(
                hdmCallbackCaptor.capture(), any(Handler.class));
        mHalDeviceManagerStatusCallbacks = hdmCallbackCaptor.getValue();
        when(mSsidTranslator.getTranslatedSsidAndRecordBssidCharset(any(), any()))
                .thenAnswer((Answer<WifiSsid>) invocation ->
                        getTranslatedSsid(invocation.getArgument(0)));
        when(mSsidTranslator.getAllPossibleOriginalSsids(any()))
                .thenAnswer((Answer<List<WifiSsid>>) invocation -> {
                    WifiSsid ssid = invocation.getArgument(0);
                    if (ssid.getBytes().length > 32) {
                        return Collections.emptyList();
                    }
                    return Collections.singletonList(ssid);
                });
    }

    /** Mock translating an SSID */
    private WifiSsid getTranslatedSsid(WifiSsid ssid) {
        byte[] ssidBytes = ssid.getBytes();
        for (int i = 0; i < ssidBytes.length; i++) {
            ssidBytes[i]++;
        }
        return WifiSsid.fromBytes(ssidBytes);
    }

    /**
     * Tests the successful starting of HAL in STA mode using
     * {@link WifiVendorHal#startVendorHalSta(ConcreteClientModeManager)}.
     */
    @Test
    public void testStartHalSuccessInStaMode() throws  Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).getChip(eq(mWifiStaIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();
        verify(mWifiStaIface).registerFrameworkCallback(any(WifiStaIface.Callback.class));
        verify(mWifiChip).registerCallback(any(WifiChip.Callback.class));

        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHalSta(ConcreteClientModeManager)}.
     */
    @Test
    public void testStartHalFailureInStaMode() throws Exception {
        // No callbacks are invoked in this case since the start itself failed. So, override
        // default AnswerWithArguments that we setup.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() throws Exception {
                return false;
            }
        }).when(mHalDeviceManager).start();
        assertFalse(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();

        verify(mHalDeviceManager, never()).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
        verify(mHalDeviceManager, never()).getChip(any(WifiHal.WifiInterface.class));
        verify(mWifiStaIface, never())
                .registerFrameworkCallback(any(WifiStaIface.Callback.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHalSta(ConcreteClientModeManager)}.
     */
    @Test
    public void testStartHalFailureInIfaceCreationInStaMode() throws Exception {
        when(mHalDeviceManager.createStaIface(any(), any(), any(), eq(mConcreteClientModeManager)))
                .thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
        verify(mHalDeviceManager, never()).getChip(any(WifiHal.WifiInterface.class));
        verify(mWifiStaIface, never())
                .registerFrameworkCallback(any(WifiStaIface.Callback.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHalSta(ConcreteClientModeManager)}.
     */
    @Test
    public void testStartHalFailureInChipGetInStaMode() throws Exception {
        when(mHalDeviceManager.getChip(any(WifiHal.WifiInterface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).getChip(any(WifiHal.WifiInterface.class));
        verify(mHalDeviceManager).stop();
        verify(mWifiStaIface).registerFrameworkCallback(any(WifiStaIface.Callback.class));

        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHalSta(ConcreteClientModeManager)}.
     */
    @Test
    public void testStartHalFailureInStaIfaceCallbackRegistration() throws Exception {
        when(mWifiStaIface.registerFrameworkCallback(any(WifiStaIface.Callback.class)))
                .thenReturn(false);
        assertFalse(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).stop();
        verify(mWifiStaIface).registerFrameworkCallback(any(WifiStaIface.Callback.class));

        verify(mHalDeviceManager, never()).getChip(any(WifiHal.WifiInterface.class));
        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHalSta(ConcreteClientModeManager)}.
     */
    @Test
    public void testStartHalFailureInChipCallbackRegistration() throws Exception {
        when(mWifiChip.registerCallback(any(WifiChip.Callback.class)))
                .thenReturn(false);
        assertFalse(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).getChip(any(WifiHal.WifiInterface.class));
        verify(mHalDeviceManager).stop();
        verify(mWifiStaIface).registerFrameworkCallback(any(WifiStaIface.Callback.class));
        verify(mWifiChip).registerCallback(any(WifiChip.Callback.class));

        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
    }

    /**
     * Tests the stopping of HAL in STA mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInStaMode() {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).getChip(eq(mWifiStaIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
    }

    /**
     * Tests the stopping of HAL in AP mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInApMode() {
        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(null, null,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager, new ArrayList<>()));

        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createApIface(
                anyLong(), any(), any(), any(), anyBoolean(), eq(mSoftApManager), anyList());
        verify(mHalDeviceManager).getChip(eq(mWifiApIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(any(), any(), any(),
                eq(mConcreteClientModeManager));
    }

    /**
     * Tests the handling of interface destroyed callback from HalDeviceManager.
     */
    @Test
    public void testStaInterfaceDestroyedHandling() throws  Exception {
        ArgumentCaptor<InterfaceDestroyedListener> internalListenerCaptor =
                ArgumentCaptor.forClass(InterfaceDestroyedListener.class);
        InterfaceDestroyedListener externalLister = mock(InterfaceDestroyedListener.class);

        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createStaIface(externalLister, TEST_WORKSOURCE,
                mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(internalListenerCaptor.capture(),
                any(), eq(TEST_WORKSOURCE), eq(mConcreteClientModeManager));
        verify(mHalDeviceManager).getChip(eq(mWifiStaIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();
        verify(mWifiStaIface).registerFrameworkCallback(any(WifiStaIface.Callback.class));
        verify(mWifiChip).registerCallback(any(WifiChip.Callback.class));

        // Now trigger the interface destroyed callback from HalDeviceManager and ensure the
        // external listener is invoked and iface removed from internal database.
        internalListenerCaptor.getValue().onDestroyed(TEST_IFACE_NAME);
        verify(externalLister).onDestroyed(TEST_IFACE_NAME);

        // This should fail now, since the interface was already destroyed.
        assertFalse(mWifiVendorHal.removeStaIface(TEST_IFACE_NAME));
        verify(mHalDeviceManager, never()).removeIface(any());
    }

    /**
     * Tests the handling of interface destroyed callback from HalDeviceManager.
     */
    @Test
    public void testApInterfaceDestroyedHandling() throws  Exception {
        ArgumentCaptor<InterfaceDestroyedListener> internalListenerCaptor =
                ArgumentCaptor.forClass(InterfaceDestroyedListener.class);
        InterfaceDestroyedListener externalLister = mock(InterfaceDestroyedListener.class);

        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(
                externalLister, TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ, false,
                mSoftApManager, new ArrayList<>()));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(anyLong(),
                internalListenerCaptor.capture(), any(), eq(TEST_WORKSOURCE), eq(false),
                eq(mSoftApManager), anyList());
        verify(mHalDeviceManager).getChip(eq(mWifiApIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();
        verify(mWifiChip).registerCallback(any(WifiChip.Callback.class));

        // Now trigger the interface destroyed callback from HalDeviceManager and ensure the
        // external listener is invoked and iface removed from internal database.
        internalListenerCaptor.getValue().onDestroyed(TEST_IFACE_NAME);
        verify(externalLister).onDestroyed(TEST_IFACE_NAME);

        // This should fail now, since the interface was already destroyed.
        assertFalse(mWifiVendorHal.removeApIface(TEST_IFACE_NAME));
        verify(mHalDeviceManager, never()).removeIface(any());
    }

    /**
     * Test that enter logs when verbose logging is enabled
     */
    @Test
    public void testEnterLogging() {
        mWifiVendorHal.mLog = spy(mWifiLog);
        mWifiVendorHal.enableVerboseLogging(true, false);
        mWifiVendorHal.installPacketFilter(TEST_IFACE_NAME, new byte[0]);
        verify(mWifiVendorHal.mLog).trace(eq("filter length %"), eq(1));
    }

    /**
     * Test that enter does not log when verbose logging is not enabled
     */
    @Test
    public void testEnterSilenceWhenNotEnabled() {
        mWifiVendorHal.mLog = spy(mWifiLog);
        mWifiVendorHal.installPacketFilter(TEST_IFACE_NAME, new byte[0]);
        mWifiVendorHal.enableVerboseLogging(true, false);
        mWifiVendorHal.enableVerboseLogging(false, false);
        mWifiVendorHal.installPacketFilter(TEST_IFACE_NAME, new byte[0]);
        verify(mWifiVendorHal.mLog, never()).trace(eq("filter length %"), anyInt());
    }

    /**
     * Test that getBgScanCapabilities is hooked up to the HAL correctly
     *
     * A call before the vendor HAL is started should return a non-null result with version 0
     *
     * A call after the HAL is started should return the mocked values.
     */
    @Test
    public void testGetBgScanCapabilities() throws Exception {
        WifiNative.ScanCapabilities capabilities = new WifiNative.ScanCapabilities();
        capabilities.max_scan_cache_size = 12;
        capabilities.max_scan_buckets = 34;
        capabilities.max_ap_cache_per_scan = 56;
        capabilities.max_scan_reporting_threshold = 78;
        when(mWifiStaIface.getBackgroundScanCapabilities()).thenReturn(capabilities);

        WifiNative.ScanCapabilities result = new WifiNative.ScanCapabilities();

        // should fail - not started
        assertFalse(mWifiVendorHal.getBgScanCapabilities(TEST_IFACE_NAME, result));
        // Start the vendor hal
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        // should succeed
        assertTrue(mWifiVendorHal.getBgScanCapabilities(TEST_IFACE_NAME, result));

        assertEquals(12, result.max_scan_cache_size);
        assertEquals(34, result.max_scan_buckets);
        assertEquals(56, result.max_ap_cache_per_scan);
        assertEquals(78, result.max_scan_reporting_threshold);
    }

    /**
     * Test get supported features. Tests whether we coalesce information from different sources
     * (IWifiStaIface, IWifiChip and HalDeviceManager) into the bitmask of supported features
     * correctly.
     */
    @Test
    public void testGetSupportedFeatures() throws Exception {
        long staIfaceCaps =
                WifiManager.WIFI_FEATURE_SCANNER | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS;
        long chipCaps = WifiManager.WIFI_FEATURE_TX_POWER_LIMIT;
        WifiChip.Response<Long> chipCapsResponse = new WifiChip.Response<>(chipCaps);
        chipCapsResponse.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
        when(mWifiStaIface.getCapabilities()).thenReturn(staIfaceCaps);
        when(mWifiChip.getCapabilitiesAfterIfacesExist()).thenReturn(chipCapsResponse);

        Set<Integer> halDeviceManagerSupportedIfaces = new HashSet<Integer>() {{
                add(WifiChip.IFACE_TYPE_STA);
                add(WifiChip.IFACE_TYPE_P2P);
            }};
        when(mHalDeviceManager.getSupportedIfaceTypes())
                .thenReturn(halDeviceManagerSupportedIfaces);
        when(mWifiGlobals.isWpa3SaeH2eSupported()).thenReturn(true);
        when(mHalDeviceManager.is24g5gDbsSupported(any())).thenReturn(true);

        long expectedFeatureSet = (
                WifiManager.WIFI_FEATURE_SCANNER
                        | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS
                        | WifiManager.WIFI_FEATURE_TX_POWER_LIMIT
                        | WifiManager.WIFI_FEATURE_INFRA
                        | WifiManager.WIFI_FEATURE_P2P
                        | WifiManager.WIFI_FEATURE_SAE_H2E
                        | WifiManager.WIFI_FEATURE_DUAL_BAND_SIMULTANEOUS
        );
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertEquals(expectedFeatureSet, mWifiVendorHal.getSupportedFeatureSet(TEST_IFACE_NAME));
    }

    /**
     * Test get supported features. Tests whether we coalesce information from package manager
     * if vendor hal is not supported.
     */
    @Test
    public void testGetSupportedFeaturesFromPackageManager() throws Exception {
        doAnswer(new AnswerWithArguments() {
            public boolean answer() throws Exception {
                return false;
            }
        }).when(mHalDeviceManager).start();
        when(mHalDeviceManager.isSupported()).thenReturn(false);
        assertFalse(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_WIFI)))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_WIFI_DIRECT)))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_WIFI_AWARE)))
                .thenReturn(true);

        long expectedFeatureSet = (
                WifiManager.WIFI_FEATURE_INFRA
                        | WifiManager.WIFI_FEATURE_P2P
                        | WifiManager.WIFI_FEATURE_AWARE
        );
        assertEquals(expectedFeatureSet, mWifiVendorHal.getSupportedFeatureSet(TEST_IFACE_NAME));
    }

    /**
     * Test that link layer stats are not enabled and harmless in AP mode
     *
     * Start the HAL in AP mode
     * - stats should not be enabled
     * Request link layer stats
     * - HAL layer should have been called to make the request
     */
    @Test
    public void testLinkLayerStatsNotEnabledAndHarmlessInApMode() throws Exception {
        when(mWifiStaIface.getLinkLayerStats()).thenReturn(null);

        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(null, null,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager, new ArrayList<>()));
        assertTrue(mWifiVendorHal.isHalStarted());
        assertNull(mWifiVendorHal.getWifiLinkLayerStats(TEST_IFACE_NAME));

        verify(mHalDeviceManager).start();

        verify(mWifiStaIface, never()).enableLinkLayerStatsCollection(false);
        verify(mWifiStaIface, never()).getLinkLayerStats();
    }

    /**
     * Test that getFirmwareVersion() and getDriverVersion() work
     *
     * Calls before the STA is started are expected to return null.
     */
    @Test
    public void testVersionGetters() throws Exception {
        String firmwareVersion = "fuzzy";
        String driverVersion = "dizzy";
        WifiChip.ChipDebugInfo chipDebugInfo =
                new WifiChip.ChipDebugInfo(driverVersion, firmwareVersion);
        when(mWifiChip.requestChipDebugInfo()).thenReturn(chipDebugInfo);

        assertNull(mWifiVendorHal.getFirmwareVersion());
        assertNull(mWifiVendorHal.getDriverVersion());

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        assertEquals(firmwareVersion, mWifiVendorHal.getFirmwareVersion());
        assertEquals(driverVersion, mWifiVendorHal.getDriverVersion());
    }

    @Test
    public void testStartSendingOffloadedPacket() throws Exception {
        byte[] srcMac = NativeUtil.macAddressToByteArray("4007b2088c81");
        byte[] dstMac = NativeUtil.macAddressToByteArray("4007b8675309");
        InetAddress src = InetAddresses.parseNumericAddress("192.168.13.13");
        InetAddress dst = InetAddresses.parseNumericAddress("93.184.216.34");
        int slot = 13;
        int millis = 16000;

        KeepalivePacketData kap =
                NattKeepalivePacketData.nattKeepalivePacket(src, 63000, dst, 4500);

        when(mWifiStaIface.startSendingKeepAlivePackets(
                anyInt(), any(), anyInt(), any(), any(), anyInt()
        )).thenReturn(true);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertEquals(0, mWifiVendorHal.startSendingOffloadedPacket(
                TEST_IFACE_NAME, slot, srcMac, dstMac, kap.getPacket(),
                OsConstants.ETH_P_IPV6, millis));

        verify(mWifiStaIface).startSendingKeepAlivePackets(
                eq(slot), any(), anyInt(), any(), any(), eq(millis));
    }

    @Test
    public void testStopSendingOffloadedPacket() throws Exception {
        int slot = 13;

        when(mWifiStaIface.stopSendingKeepAlivePackets(anyInt())).thenReturn(true);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(0 == mWifiVendorHal.stopSendingOffloadedPacket(
                TEST_IFACE_NAME, slot));

        verify(mWifiStaIface).stopSendingKeepAlivePackets(eq(slot));
    }

    /**
     * Test the setup, invocation, and removal of a RSSI event handler
     *
     */
    @Test
    public void testRssiMonitoring() throws Exception {
        when(mWifiStaIface.startRssiMonitoring(anyInt(), anyInt(), anyInt()))
                .thenReturn(true);
        when(mWifiStaIface.stopRssiMonitoring(anyInt()))
                .thenReturn(true);

        ArrayList<Byte> breach = new ArrayList<>(10);
        byte hi = -21;
        byte med = -42;
        byte lo = -84;
        Byte lower = -88;
        WifiNative.WifiRssiEventHandler handler;
        handler = ((cur) -> {
            breach.add(cur);
        });
        // not started
        assertEquals(-1, mWifiVendorHal.startRssiMonitoring(TEST_IFACE_NAME, hi, lo, handler));
        // not started
        assertEquals(-1, mWifiVendorHal.stopRssiMonitoring(TEST_IFACE_NAME));
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(TEST_IFACE_NAME, hi, lo, handler));
        int theCmdId = mWifiVendorHal.sRssiMonCmdId;
        breach.clear();
        mWifiStaIfaceEventCallback.onRssiThresholdBreached(theCmdId, new byte[6], lower);
        assertEquals(breach.get(0), lower);
        assertEquals(0, mWifiVendorHal.stopRssiMonitoring(TEST_IFACE_NAME));
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(TEST_IFACE_NAME, hi, lo, handler));
        // replacing works
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(TEST_IFACE_NAME, med, lo, handler));
        // null handler fails
        assertEquals(-1, mWifiVendorHal.startRssiMonitoring(
                TEST_IFACE_NAME, hi, lo, null));
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(TEST_IFACE_NAME, hi, lo, handler));
        // empty range
        assertEquals(-1, mWifiVendorHal.startRssiMonitoring(TEST_IFACE_NAME, lo, hi, handler));
    }

    /**
     * Test that getApfCapabilities is hooked up to the HAL correctly
     *
     * A call before the vendor HAL is started should return a non-null result with version 0
     *
     * A call after the HAL is started should return the mocked values.
     */
    @Test
    public void testApfCapabilities() throws Exception {
        int myVersion = 33;
        int myMaxSize = 1234;

        ApfCapabilities capabilities =
                new ApfCapabilities(myVersion, myMaxSize, android.system.OsConstants.ARPHRD_ETHER);
        when(mWifiStaIface.getApfPacketFilterCapabilities()).thenReturn(capabilities);

        assertEquals(0, mWifiVendorHal.getApfCapabilities(TEST_IFACE_NAME)
                .apfVersionSupported);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        ApfCapabilities actual = mWifiVendorHal.getApfCapabilities(TEST_IFACE_NAME);

        assertEquals(myVersion, actual.apfVersionSupported);
        assertEquals(myMaxSize, actual.maximumApfProgramSize);
        assertEquals(android.system.OsConstants.ARPHRD_ETHER, actual.apfPacketFormat);
        assertNotEquals(0, actual.apfPacketFormat);
    }

    /**
     * Test that an APF program can be installed.
     */
    @Test
    public void testInstallApf() throws Exception {
        byte[] filter = new byte[] {19, 53, 10};
        when(mWifiStaIface.installApfPacketFilter(any(byte[].class))).thenReturn(true);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.installPacketFilter(TEST_IFACE_NAME, filter));
        verify(mWifiStaIface).installApfPacketFilter(eq(filter));
    }

    /**
     * Test that an APF program and data buffer can be read back.
     */
    @Test
    public void testReadApf() throws Exception {
        byte[] program = new byte[] {65, 66, 67};
        when(mWifiStaIface.readApfPacketFilterData()).thenReturn(program);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertArrayEquals(program, mWifiVendorHal.readPacketFilter(TEST_IFACE_NAME));
    }

    /**
     * Test that startLoggingToDebugRingBuffer is plumbed to chip
     *
     * A call before the vendor hal is started should just return false.
     * After starting in STA mode, the call should succeed, and pass ther right things down.
     */
    @Test
    public void testStartLoggingRingBuffer() throws Exception {
        when(mWifiChip.startLoggingToDebugRingBuffer(
                any(String.class), anyInt(), anyInt(), anyInt()
        )).thenReturn(true);

        assertFalse(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 0, 0, "One"));
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 11, 3000, "One"));

        verify(mWifiChip).startLoggingToDebugRingBuffer("One", 1, 11, 3000);
    }

    /**
     * Same test as testStartLoggingRingBuffer, but in AP mode rather than STA.
     */
    @Test
    public void testStartLoggingRingBufferOnAp() throws Exception {
        when(mWifiChip.startLoggingToDebugRingBuffer(
                any(String.class), anyInt(), anyInt(), anyInt()
        )).thenReturn(true);

        assertFalse(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 0, 0, "One"));
        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(null, null,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager, new ArrayList<>()));
        assertTrue(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 11, 3000, "One"));

        verify(mWifiChip).startLoggingToDebugRingBuffer("One", 1, 11, 3000);
    }

    /**
     * Test that getRingBufferData calls forceDumpToDebugRingBuffer
     *
     * Try once before hal start, and twice after (one success, one failure).
     */
    @Test
    public void testForceRingBufferDump() throws Exception {
        when(mWifiChip.forceDumpToDebugRingBuffer(eq("Gunk"))).thenReturn(true);
        when(mWifiChip.forceDumpToDebugRingBuffer(eq("Glop"))).thenReturn(false);

        assertFalse(mWifiVendorHal.getRingBufferData("Gunk")); // hal not started

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        assertTrue(mWifiVendorHal.getRingBufferData("Gunk")); // mocked call succeeds
        assertFalse(mWifiVendorHal.getRingBufferData("Glop")); // mocked call fails

        verify(mWifiChip).forceDumpToDebugRingBuffer("Gunk");
        verify(mWifiChip).forceDumpToDebugRingBuffer("Glop");
    }

    /**
     * Test flush ring buffer to files.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testFlushRingBufferToFile() throws Exception {
        when(mWifiChip.flushRingBufferToFile()).thenReturn(true);

        assertFalse(mWifiVendorHal.flushRingBufferData());

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.flushRingBufferData());
        verify(mWifiChip).flushRingBufferToFile();
    }

    /**
     * Tests the start of packet fate monitoring.
     *
     * Try once before hal start, and once after (one success, one failure).
     */
    @Test
    public void testStartPktFateMonitoring() throws Exception {
        when(mWifiStaIface.startDebugPacketFateMonitoring()).thenReturn(true);

        assertFalse(mWifiVendorHal.startPktFateMonitoring(TEST_IFACE_NAME));
        verify(mWifiStaIface, never()).startDebugPacketFateMonitoring();

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.startPktFateMonitoring(TEST_IFACE_NAME));
        verify(mWifiStaIface).startDebugPacketFateMonitoring();
    }

    /**
     * Tests the retrieval of tx packet fates.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testGetTxPktFates() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        byte fate = WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED;
        long driverTimestampUsec = new Random().nextLong();
        byte frameType = WifiLoggerHal.FRAME_TYPE_ETHERNET_II;
        WifiNative.TxFateReport fateReport = new WifiNative.TxFateReport(
                fate, driverTimestampUsec, frameType, frameContentBytes);
        when(mWifiStaIface.getDebugTxPacketFates()).thenReturn(Arrays.asList(fateReport));

        assertEquals(0, mWifiVendorHal.getTxPktFates(TEST_IFACE_NAME).size());
        verify(mWifiStaIface, never()).getDebugTxPacketFates();

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        List<WifiNative.TxFateReport> retrievedFates =
                mWifiVendorHal.getTxPktFates(TEST_IFACE_NAME);
        assertEquals(1, retrievedFates.size());
        WifiNative.TxFateReport retrievedFate = retrievedFates.get(0);
        verify(mWifiStaIface).getDebugTxPacketFates();
        assertEquals(fate, retrievedFate.mFate);
        assertEquals(driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(frameType, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Tests the retrieval of rx packet fates.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testGetRxPktFates() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        byte fate = WifiLoggerHal.RX_PKT_FATE_SUCCESS;
        long driverTimestampUsec = new Random().nextLong();
        byte frameType = WifiLoggerHal.FRAME_TYPE_ETHERNET_II;
        WifiNative.RxFateReport fateReport = new WifiNative.RxFateReport(
                fate, driverTimestampUsec, frameType, frameContentBytes);
        when(mWifiStaIface.getDebugRxPacketFates()).thenReturn(Arrays.asList(fateReport));

        assertEquals(0, mWifiVendorHal.getRxPktFates(TEST_IFACE_NAME).size());
        verify(mWifiStaIface, never()).getDebugRxPacketFates();

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        List<WifiNative.RxFateReport> retrievedFates =
                mWifiVendorHal.getRxPktFates(TEST_IFACE_NAME);
        assertEquals(1, retrievedFates.size());
        WifiNative.RxFateReport retrievedFate = retrievedFates.get(0);
        verify(mWifiStaIface).getDebugRxPacketFates();
        assertEquals(fate, retrievedFate.mFate);
        assertEquals(driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(frameType, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Tests the nd offload enable/disable.
     */
    @Test
    public void testEnableDisableNdOffload() throws Exception {
        when(mWifiStaIface.enableNdOffload(anyBoolean())).thenReturn(true);

        assertFalse(mWifiVendorHal.configureNeighborDiscoveryOffload(TEST_IFACE_NAME, true));
        verify(mWifiStaIface, never()).enableNdOffload(anyBoolean());

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        assertTrue(mWifiVendorHal.configureNeighborDiscoveryOffload(TEST_IFACE_NAME, true));
        verify(mWifiStaIface).enableNdOffload(eq(true));
        assertTrue(mWifiVendorHal.configureNeighborDiscoveryOffload(TEST_IFACE_NAME, false));
        verify(mWifiStaIface).enableNdOffload(eq(false));
    }

    /**
     * Tests the nd offload enable failure.
     */
    @Test
    public void testEnableNdOffloadFailure() throws Exception {
        when(mWifiStaIface.enableNdOffload(eq(true))).thenReturn(false);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        assertFalse(mWifiVendorHal.configureNeighborDiscoveryOffload(TEST_IFACE_NAME, true));
        verify(mWifiStaIface).enableNdOffload(eq(true));
    }

    /**
     * Tests enableFirmwareRoaming successful enable
     */
    @Test
    public void testEnableFirmwareRoamingSuccess() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        when(mWifiStaIface.setRoamingState(eq(WifiNative.ENABLE_FIRMWARE_ROAMING)))
                .thenReturn(WifiNative.SET_FIRMWARE_ROAMING_SUCCESS);
        assertEquals(WifiNative.SET_FIRMWARE_ROAMING_SUCCESS,
                mWifiVendorHal.enableFirmwareRoaming(TEST_IFACE_NAME,
                                                     WifiNative.ENABLE_FIRMWARE_ROAMING));
    }

    /**
     * Tests enableFirmwareRoaming successful disable
     */
    @Test
    public void testDisbleFirmwareRoamingSuccess() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        when(mWifiStaIface.setRoamingState(eq(WifiNative.DISABLE_FIRMWARE_ROAMING)))
                .thenReturn(WifiNative.SET_FIRMWARE_ROAMING_SUCCESS);
        assertEquals(WifiNative.SET_FIRMWARE_ROAMING_SUCCESS,
                mWifiVendorHal.enableFirmwareRoaming(TEST_IFACE_NAME,
                                                     WifiNative.DISABLE_FIRMWARE_ROAMING));
    }

    /**
     * Tests enableFirmwareRoaming failure case - busy
     */
    @Test
    public void testEnableFirmwareRoamingFailureBusy() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        when(mWifiStaIface.setRoamingState(anyInt()))
                .thenReturn(WifiNative.SET_FIRMWARE_ROAMING_BUSY);
        assertEquals(WifiNative.SET_FIRMWARE_ROAMING_BUSY,
                mWifiVendorHal.enableFirmwareRoaming(TEST_IFACE_NAME,
                                                     WifiNative.ENABLE_FIRMWARE_ROAMING));
    }

    /**
     * Tests enableFirmwareRoaming generic failure case
     */
    @Test
    public void testEnableFirmwareRoamingFailure() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        when(mWifiStaIface.setRoamingState(anyInt()))
                .thenReturn(WifiNative.SET_FIRMWARE_ROAMING_FAILURE);
        assertEquals(WifiNative.SET_FIRMWARE_ROAMING_FAILURE,
                mWifiVendorHal.enableFirmwareRoaming(TEST_IFACE_NAME,
                                                     WifiNative.ENABLE_FIRMWARE_ROAMING));
    }

    /**
     * Tests configureRoaming success
     */
    @Test
    public void testConfigureRoamingSuccess() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        WifiNative.RoamingConfig roamingConfig = new WifiNative.RoamingConfig();
        roamingConfig.blocklistBssids = new ArrayList();
        roamingConfig.blocklistBssids.add("12:34:56:78:ca:fe");
        roamingConfig.allowlistSsids = new ArrayList();
        roamingConfig.allowlistSsids.add("\"xyzzy\"");
        roamingConfig.allowlistSsids.add("\"\u0F00 \u05D0\"");
        when(mWifiStaIface.configureRoaming(any(), any())).thenReturn(true);
        assertTrue(mWifiVendorHal.configureRoaming(TEST_IFACE_NAME, roamingConfig));
        verify(mWifiStaIface).configureRoaming(any(), any());
    }

    /**
     * Tests configureRoaming zero padding success
     */
    @Test
    public void testConfigureRoamingZeroPaddingSuccess() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        WifiNative.RoamingConfig roamingConfig = new WifiNative.RoamingConfig();
        roamingConfig.allowlistSsids = new ArrayList();
        roamingConfig.allowlistSsids.add("\"xyzzy\"");
        when(mWifiStaIface.configureRoaming(any(), any())).thenReturn(true);
        assertTrue(mWifiVendorHal.configureRoaming(TEST_IFACE_NAME, roamingConfig));
        verify(mWifiStaIface).configureRoaming(any(), mListCaptor.capture());
        byte[] allowlistSsidsPadded = new byte[32];
        allowlistSsidsPadded[0] = (byte) 0x78;
        allowlistSsidsPadded[1] = (byte) 0x79;
        allowlistSsidsPadded[2] = (byte) 0x7a;
        allowlistSsidsPadded[3] = (byte) 0x7a;
        allowlistSsidsPadded[4] = (byte) 0x79;
        assertArrayEquals((byte[]) mListCaptor.getValue().get(0), allowlistSsidsPadded);
        allowlistSsidsPadded[5] = (byte) 0x79;
        assertFalse(Arrays.equals((byte[]) mListCaptor.getValue().get(0), allowlistSsidsPadded));
    }

    /**
     * Tests configureRoaming success with null lists
     */
    @Test
    public void testConfigureRoamingResetSuccess() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        WifiNative.RoamingConfig roamingConfig = new WifiNative.RoamingConfig();
        when(mWifiStaIface.configureRoaming(any(), any())).thenReturn(true);
        assertTrue(mWifiVendorHal.configureRoaming(TEST_IFACE_NAME, roamingConfig));
        verify(mWifiStaIface).configureRoaming(any(), any());
    }

    /**
     * Tests configureRoaming failure when hal returns failure
     */
    @Test
    public void testConfigureRoamingFailure() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        WifiNative.RoamingConfig roamingConfig = new WifiNative.RoamingConfig();
        when(mWifiStaIface.configureRoaming(any(), any())).thenReturn(false);
        assertFalse(mWifiVendorHal.configureRoaming(TEST_IFACE_NAME, roamingConfig));
        verify(mWifiStaIface).configureRoaming(any(), any());
    }

    /**
     * Tests configureRoaming failure due to invalid bssid
     */
    @Test
    public void testConfigureRoamingBadBssid() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        WifiNative.RoamingConfig roamingConfig = new WifiNative.RoamingConfig();
        roamingConfig.blocklistBssids = new ArrayList();
        roamingConfig.blocklistBssids.add("12:34:56:78:zz:zz");
        when(mWifiStaIface.configureRoaming(any(), any())).thenReturn(true);
        assertFalse(mWifiVendorHal.configureRoaming(TEST_IFACE_NAME, roamingConfig));
        verify(mWifiStaIface, never()).configureRoaming(any(), any());
    }

    /**
     * Tests configureRoaming failure due to invalid ssid
     */
    @Test
    public void testConfigureRoamingBadSsid() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        WifiNative.RoamingConfig roamingConfig = new WifiNative.RoamingConfig();
        roamingConfig.allowlistSsids = new ArrayList();
        // Add an SSID that is too long (> 32 bytes) due to the multi-byte utf-8 characters
        roamingConfig.allowlistSsids.add("\"123456789012345678901234567890\u0F00\u05D0\"");
        when(mWifiStaIface.configureRoaming(any(), any())).thenReturn(true);
        assertTrue(mWifiVendorHal.configureRoaming(TEST_IFACE_NAME, roamingConfig));
        verify(mWifiStaIface).configureRoaming(any(), mListCaptor.capture());
        verify(mSsidTranslator).getAllPossibleOriginalSsids(any());
        assertTrue(mListCaptor.getValue().isEmpty());
    }

    /**
     * Tests the failure in retrieval of wlan wake reason stats.
     */
    @Test
    public void testGetWlanWakeReasonCountFailure() throws Exception {
        when(mWifiChip.getDebugHostWakeReasonStats()).thenReturn(null);

        // This should work in both AP & STA mode.
        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(null, null,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager, new ArrayList<>()));

        assertNull(mWifiVendorHal.getWlanWakeReasonCount());
        verify(mWifiChip).getDebugHostWakeReasonStats();
    }

    /**
     * Test that getFwMemoryDump is properly plumbed
     */
    @Test
    public void testGetFwMemoryDump() throws Exception {
        byte [] sample = NativeUtil.hexStringToByteArray("268c7a3fbfa4661c0bdd6a36");
        when(mWifiChip.requestFirmwareDebugDump()).thenReturn(sample);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertArrayEquals(sample, mWifiVendorHal.getFwMemoryDump());
    }

    /**
     * Test that getDriverStateDump is properly plumbed
     *
     * Just for variety, use AP mode here.
     */
    @Test
    public void testGetDriverStateDump() throws Exception {
        byte [] sample = NativeUtil.hexStringToByteArray("e83ff543cf80083e6459d20f");
        when(mWifiChip.requestDriverDebugDump()).thenReturn(sample);

        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(null, null,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager, new ArrayList<>()));
        assertArrayEquals(sample, mWifiVendorHal.getDriverStateDump());
    }

    /**
     * Test that background scan failure is handled correctly.
     */
    @Test
    public void testBgScanFailureCallback() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        mWifiStaIfaceEventCallback.onBackgroundScanFailure(mWifiVendorHal.mScan.cmdId);
        verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_FAILED);
    }

    /**
     * Test that background scan failure with wrong id is not reported.
     */
    @Test
    public void testBgScanFailureCallbackWithInvalidCmdId() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        mWifiStaIfaceEventCallback.onBackgroundScanFailure(mWifiVendorHal.mScan.cmdId + 1);
        verify(eventHandler, never()).onScanStatus(WifiNative.WIFI_SCAN_FAILED);
    }

    /**
     * Test that background scan full results are handled correctly.
     */
    @Test
    public void testBgScanFullScanResults() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        WifiSsid ssid = WifiSsid.fromUtf8Text("This is an SSID");
        MacAddress bssid = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
        ScanResult result = createFrameworkBgScanResult(ssid, bssid);
        mWifiStaIfaceEventCallback.onBackgroundFullScanResult(
                mWifiVendorHal.mScan.cmdId, 5, result);

        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(eventHandler).onFullScanResult(scanResultCaptor.capture(), eq(5));

        assertScanResultEqual(result, scanResultCaptor.getValue());
    }

    /**
     * Test that background scan results are handled correctly.
     */
    @Test
    public void testBgScanScanResults() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        WifiScanner.ScanData[] data = createFrameworkBgScanDatas();
        mWifiStaIfaceEventCallback.onBackgroundScanResults(
                mWifiVendorHal.mScan.cmdId, data);

        verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        assertScanDatasEqual(
                Arrays.asList(data), Arrays.asList(mWifiVendorHal.mScan.latestScanResults));
    }

    /**
     * Test that starting a new background scan when one is active will stop the previous one.
     */
    @Test
    public void testBgScanReplacement() throws Exception {
        when(mWifiStaIface.stopBackgroundScan(anyInt())).thenReturn(true);
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);
        int cmdId1 = mWifiVendorHal.mScan.cmdId;
        startBgScan(eventHandler);
        assertNotEquals(mWifiVendorHal.mScan.cmdId, cmdId1);
        verify(mWifiStaIface, times(2)).startBackgroundScan(anyInt(), any());
        verify(mWifiStaIface).stopBackgroundScan(cmdId1);
    }

    /**
     * Test stopping a background scan.
     */
    @Test
    public void testBgScanStop() throws Exception {
        when(mWifiStaIface.stopBackgroundScan(anyInt())).thenReturn(true);
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        int cmdId = mWifiVendorHal.mScan.cmdId;

        mWifiVendorHal.stopBgScan(TEST_IFACE_NAME);
        mWifiVendorHal.stopBgScan(TEST_IFACE_NAME); // second call should not do anything
        verify(mWifiStaIface).stopBackgroundScan(cmdId); // Should be called just once
    }

    /**
     * Test pausing and restarting a background scan.
     */
    @Test
    public void testBgScanPauseAndRestart() throws Exception {
        when(mWifiStaIface.stopBackgroundScan(anyInt())).thenReturn(true);
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiStaIfaceEventCallback);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        int cmdId = mWifiVendorHal.mScan.cmdId;

        mWifiVendorHal.pauseBgScan(TEST_IFACE_NAME);
        mWifiVendorHal.restartBgScan(TEST_IFACE_NAME);
        verify(mWifiStaIface).stopBackgroundScan(cmdId); // Should be called just once
        verify(mWifiStaIface, times(2)).startBackgroundScan(eq(cmdId), any());
    }

    /**
     * Test the handling of log handler set.
     */
    @Test
    public void testSetLogHandler() throws Exception {
        when(mWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(true);

        WifiNative.WifiLoggerEventHandler eventHandler =
                mock(WifiNative.WifiLoggerEventHandler.class);

        assertFalse(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mWifiChip, never()).enableDebugErrorAlerts(anyBoolean());

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        assertTrue(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mWifiChip).enableDebugErrorAlerts(eq(true));
        reset(mWifiChip);

        // Second call should fail.
        assertFalse(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mWifiChip, never()).enableDebugErrorAlerts(anyBoolean());
    }

    /**
     * Test the handling of log handler reset.
     */
    @Test
    public void testResetLogHandler() throws Exception {
        when(mWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(true);
        when(mWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(true);

        assertFalse(mWifiVendorHal.resetLogHandler());
        verify(mWifiChip, never()).enableDebugErrorAlerts(anyBoolean());
        verify(mWifiChip, never()).stopLoggingToDebugRingBuffer();

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        // Now set and then reset.
        assertTrue(mWifiVendorHal.setLoggingEventHandler(
                mock(WifiNative.WifiLoggerEventHandler.class)));
        assertTrue(mWifiVendorHal.resetLogHandler());
        verify(mWifiChip).enableDebugErrorAlerts(eq(false));
        verify(mWifiChip).stopLoggingToDebugRingBuffer();
        reset(mWifiChip);
    }

    /**
     * Test the handling of log handler reset.
     */
    @Test
    public void testResetLogHandlerAfterHalStop() throws Exception {
        when(mWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(true);
        when(mWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(true);

        // Start in STA mode.
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        // Now set the log handler, succeeds.
        assertTrue(mWifiVendorHal.setLoggingEventHandler(
                mock(WifiNative.WifiLoggerEventHandler.class)));
        verify(mWifiChip).enableDebugErrorAlerts(eq(true));

        // Stop
        mWifiVendorHal.stopVendorHal();

        // Reset the log handler after stop, not HAL methods invoked.
        assertFalse(mWifiVendorHal.resetLogHandler());
        verify(mWifiChip, never()).enableDebugErrorAlerts(eq(false));
        verify(mWifiChip, never()).stopLoggingToDebugRingBuffer();

        // Start in STA mode again.
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));

        // Now set the log handler again, should succeed.
        assertTrue(mWifiVendorHal.setLoggingEventHandler(
                mock(WifiNative.WifiLoggerEventHandler.class)));
        verify(mWifiChip, times(2)).enableDebugErrorAlerts(eq(true));
    }

    /**
     * Test the handling of alert callback.
     */
    @Test
    public void testAlertCallback() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiChipEventCallback);
        testAlertCallbackUsingProvidedCallback(mWifiChipEventCallback);
    }

    /**
     * Test the handling of ring buffer callback.
     */
    @Test
    public void testRingBufferDataCallback() throws Exception {
        when(mWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(true);
        when(mWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(true);

        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiChipEventCallback);

        byte[] errorData = new byte[45];
        new Random().nextBytes(errorData);

        // Randomly raise the callback before we register for the log callback.
        // This should be safely ignored. (Not trigger NPE.)
        mWifiChipEventCallback.onDebugRingBufferDataAvailable(
                new WifiNative.RingBufferStatus(), errorData);
        mLooper.dispatchAll();

        WifiNative.WifiLoggerEventHandler eventHandler =
                mock(WifiNative.WifiLoggerEventHandler.class);
        assertTrue(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mWifiChip).enableDebugErrorAlerts(eq(true));

        // Now raise the callback, this should be properly handled.
        mWifiChipEventCallback.onDebugRingBufferDataAvailable(
                new WifiNative.RingBufferStatus(), errorData);
        mLooper.dispatchAll();
        verify(eventHandler).onRingBufferData(
                any(WifiNative.RingBufferStatus.class), eq(errorData));

        // Now stop the logging and invoke the callback. This should be ignored.
        reset(eventHandler);
        assertTrue(mWifiVendorHal.resetLogHandler());
        mWifiChipEventCallback.onDebugRingBufferDataAvailable(
                new WifiNative.RingBufferStatus(), errorData);
        mLooper.dispatchAll();
        verify(eventHandler, never()).onRingBufferData(anyObject(), anyObject());
    }

    /**
     * Test the handling of Vendor HAL death.
     */
    @Test
    public void testVendorHalDeath() {
        // Invoke the HAL device manager status callback with ready set to false to indicate the
        // death of the HAL.
        when(mHalDeviceManager.isReady()).thenReturn(false);
        mHalDeviceManagerStatusCallbacks.onStatusChanged();
        mLooper.dispatchAll();

        verify(mVendorHalDeathHandler).onDeath();
    }

    /**
     * Test the STA Iface creation failure due to iface name retrieval failure.
     */
    @Test
    public void testCreateStaIfaceFailureInIfaceName() throws RemoteException {
        when(mWifiStaIface.getName()).thenReturn(null);
        assertTrue(mWifiVendorHal.startVendorHal());
        assertNull(mWifiVendorHal.createStaIface(null, TEST_WORKSOURCE,
                mConcreteClientModeManager));
        verify(mHalDeviceManager).createStaIface(any(), any(), eq(TEST_WORKSOURCE),
                eq(mConcreteClientModeManager));
    }

    /**
     * Test the STA Iface creation failure due to iface name retrieval failure.
     */
    @Test
    public void testCreateApIfaceFailureInIfaceName() throws RemoteException {
        when(mWifiApIface.getName()).thenReturn(null);
        assertTrue(mWifiVendorHal.startVendorHal());
        assertNull(mWifiVendorHal.createApIface(
                null, TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ, false, mSoftApManager,
                new ArrayList<>()));
        verify(mHalDeviceManager).createApIface(
                anyLong(), any(), any(), eq(TEST_WORKSOURCE), eq(false), eq(mSoftApManager),
                anyList());
    }

    /**
     * Test the creation and removal of STA Iface.
     */
    @Test
    public void testCreateRemoveStaIface() throws RemoteException {
        assertTrue(mWifiVendorHal.startVendorHal());
        String ifaceName = mWifiVendorHal.createStaIface(null, TEST_WORKSOURCE,
                mConcreteClientModeManager);
        verify(mHalDeviceManager).createStaIface(any(), any(), eq(TEST_WORKSOURCE),
                eq(mConcreteClientModeManager));
        assertEquals(TEST_IFACE_NAME, ifaceName);
        assertTrue(mWifiVendorHal.removeStaIface(ifaceName));
        verify(mHalDeviceManager).removeIface(eq(mWifiStaIface));
    }

    /**
     * Test the creation and removal of Ap Iface.
     */
    @Test
    public void testCreateRemoveApIface() throws RemoteException {
        assertTrue(mWifiVendorHal.startVendorHal());
        String ifaceName = mWifiVendorHal.createApIface(
                null, TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ, false, mSoftApManager,
                new ArrayList<>());
        verify(mHalDeviceManager).createApIface(
                anyLong(), any(), any(), eq(TEST_WORKSOURCE), eq(false), eq(mSoftApManager),
                anyList());
        assertEquals(TEST_IFACE_NAME, ifaceName);
        assertTrue(mWifiVendorHal.removeApIface(ifaceName));
        verify(mHalDeviceManager).removeIface(eq(mWifiApIface));
    }

    /**
     * Verifies radio mode change callback to indicate DBS mode.
     */
    @Test
    public void testRadioModeChangeCallbackToDbsMode() throws Exception {
        startHalInStaModeAndRegisterRadioModeChangeCallback();

        WifiChip.IfaceInfo ifaceInfo0 = new WifiChip.IfaceInfo(TEST_IFACE_NAME, 34);
        WifiChip.IfaceInfo ifaceInfo1 = new WifiChip.IfaceInfo(TEST_IFACE_NAME_1, 1);

        WifiChip.RadioModeInfo radioModeInfo0 = new WifiChip.RadioModeInfo(
                0, WifiScanner.WIFI_BAND_5_GHZ, Arrays.asList(ifaceInfo0));
        WifiChip.RadioModeInfo radioModeInfo1 = new WifiChip.RadioModeInfo(
                1, WifiScanner.WIFI_BAND_24_GHZ, Arrays.asList(ifaceInfo1));

        ArrayList<WifiChip.RadioModeInfo> radioModeInfos = new ArrayList<>();
        radioModeInfos.add(radioModeInfo0);
        radioModeInfos.add(radioModeInfo1);

        mWifiChipEventCallback.onRadioModeChange(radioModeInfos);
        mLooper.dispatchAll();
        verify(mVendorHalRadioModeChangeHandler).onDbs();

        verifyNoMoreInteractions(mVendorHalRadioModeChangeHandler);
    }

    /**
     * Verifies radio mode change callback to indicate SBS mode.
     */
    @Test
    public void testRadioModeChangeCallbackToSbsMode() throws Exception {
        startHalInStaModeAndRegisterRadioModeChangeCallback();

        WifiChip.IfaceInfo ifaceInfo0 = new WifiChip.IfaceInfo(TEST_IFACE_NAME, 34);
        WifiChip.IfaceInfo ifaceInfo1 = new WifiChip.IfaceInfo(TEST_IFACE_NAME_1, 36);

        WifiChip.RadioModeInfo radioModeInfo0 = new WifiChip.RadioModeInfo(
                0, WifiScanner.WIFI_BAND_5_GHZ, Arrays.asList(ifaceInfo0));
        WifiChip.RadioModeInfo radioModeInfo1 = new WifiChip.RadioModeInfo(
                1, WifiScanner.WIFI_BAND_5_GHZ, Arrays.asList(ifaceInfo1));

        ArrayList<WifiChip.RadioModeInfo> radioModeInfos = new ArrayList<>();
        radioModeInfos.add(radioModeInfo0);
        radioModeInfos.add(radioModeInfo1);

        mWifiChipEventCallback.onRadioModeChange(radioModeInfos);
        mLooper.dispatchAll();
        verify(mVendorHalRadioModeChangeHandler).onSbs(WifiScanner.WIFI_BAND_5_GHZ);

        verifyNoMoreInteractions(mVendorHalRadioModeChangeHandler);
    }

    /**
     * Verifies radio mode change callback to indicate SCC mode.
     */
    @Test
    public void testRadioModeChangeCallbackToSccMode() throws Exception {
        startHalInStaModeAndRegisterRadioModeChangeCallback();

        WifiChip.IfaceInfo ifaceInfo0 = new WifiChip.IfaceInfo(TEST_IFACE_NAME, 34);
        WifiChip.IfaceInfo ifaceInfo1 = new WifiChip.IfaceInfo(TEST_IFACE_NAME_1, 34);
        WifiChip.RadioModeInfo radioModeInfo0 = new WifiChip.RadioModeInfo(
                0, WifiScanner.WIFI_BAND_5_GHZ, Arrays.asList(ifaceInfo0, ifaceInfo1));

        ArrayList<WifiChip.RadioModeInfo> radioModeInfos = new ArrayList<>();
        radioModeInfos.add(radioModeInfo0);

        mWifiChipEventCallback.onRadioModeChange(radioModeInfos);
        mLooper.dispatchAll();
        verify(mVendorHalRadioModeChangeHandler).onScc(WifiScanner.WIFI_BAND_5_GHZ);

        verifyNoMoreInteractions(mVendorHalRadioModeChangeHandler);
    }

    /**
     * Verifies radio mode change callback to indicate MCC mode.
     */
    @Test
    public void testRadioModeChangeCallbackToMccMode() throws Exception {
        startHalInStaModeAndRegisterRadioModeChangeCallback();

        WifiChip.IfaceInfo ifaceInfo0 = new WifiChip.IfaceInfo(TEST_IFACE_NAME, 1);
        WifiChip.IfaceInfo ifaceInfo1 = new WifiChip.IfaceInfo(TEST_IFACE_NAME_1, 36);

        WifiChip.RadioModeInfo radioModeInfo0 = new WifiChip.RadioModeInfo(
                0, WifiScanner.WIFI_BAND_BOTH, Arrays.asList(ifaceInfo0, ifaceInfo1));

        ArrayList<WifiChip.RadioModeInfo> radioModeInfos = new ArrayList<>();
        radioModeInfos.add(radioModeInfo0);

        mWifiChipEventCallback.onRadioModeChange(radioModeInfos);
        mLooper.dispatchAll();
        verify(mVendorHalRadioModeChangeHandler).onMcc(WifiScanner.WIFI_BAND_BOTH);

        verifyNoMoreInteractions(mVendorHalRadioModeChangeHandler);
    }

    /**
     * Verifies radio mode change callback error cases.
     */
    @Test
    public void testRadioModeChangeCallbackErrorSimultaneousWithSameIfaceOnBothRadios()
            throws Exception {
        startHalInStaModeAndRegisterRadioModeChangeCallback();

        WifiChip.IfaceInfo ifaceInfo0 = new WifiChip.IfaceInfo(TEST_IFACE_NAME, 34);

        WifiChip.RadioModeInfo radioModeInfo0 = new WifiChip.RadioModeInfo(
                0, WifiScanner.WIFI_BAND_24_GHZ, Arrays.asList(ifaceInfo0));
        WifiChip.RadioModeInfo radioModeInfo1 = new WifiChip.RadioModeInfo(
                1, WifiScanner.WIFI_BAND_5_GHZ, Arrays.asList(ifaceInfo0));

        ArrayList<WifiChip.RadioModeInfo> radioModeInfos = new ArrayList<>();
        radioModeInfos.add(radioModeInfo0);
        radioModeInfos.add(radioModeInfo1);

        mWifiChipEventCallback.onRadioModeChange(radioModeInfos);
        mLooper.dispatchAll();
        // Ignored....

        verifyNoMoreInteractions(mVendorHalRadioModeChangeHandler);
    }

    @Test
    public void testIsItPossibleToCreateIface() {
        when(mHalDeviceManager.isItPossibleToCreateIface(eq(HDM_CREATE_IFACE_AP),
                any())).thenReturn(true);
        assertTrue(mWifiVendorHal.isItPossibleToCreateApIface(new WorkSource()));

        when(mHalDeviceManager.isItPossibleToCreateIface(eq(HDM_CREATE_IFACE_STA), any()))
                .thenReturn(true);
        assertTrue(mWifiVendorHal.isItPossibleToCreateStaIface(new WorkSource()));
    }

    @Test
    public void testIsStaApConcurrencySupported() {
        when(mHalDeviceManager.canDeviceSupportCreateTypeCombo(
                argThat(ifaceCombo -> ifaceCombo.get(WifiChip.IFACE_TYPE_STA) == 1
                        && ifaceCombo.get(WifiChip.IFACE_TYPE_AP) == 1))).thenReturn(true);
        assertTrue(mWifiVendorHal.isStaApConcurrencySupported());
    }

    @Test
    public void testIsStaStaConcurrencySupported() {
        when(mHalDeviceManager.canDeviceSupportCreateTypeCombo(
                argThat(ifaceCombo -> ifaceCombo.get(WifiChip.IFACE_TYPE_STA) == 2)))
                .thenReturn(true);
        assertTrue(mWifiVendorHal.isStaStaConcurrencySupported());
    }

    private void startHalInStaModeAndRegisterRadioModeChangeCallback() {
        mWifiVendorHal.registerRadioModeChangeHandler(mVendorHalRadioModeChangeHandler);
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertNotNull(mWifiChipEventCallback);
    }

    private void testAlertCallbackUsingProvidedCallback(WifiChip.Callback chipCallback)
            throws Exception {
        when(mWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(true);
        when(mWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(true);

        int errorCode = 5;
        byte[] errorData = new byte[45];
        new Random().nextBytes(errorData);

        // Randomly raise the callback before we register for the log callback.
        // This should be safely ignored. (Not trigger NPE.)
        chipCallback.onDebugErrorAlert(errorCode, errorData);
        mLooper.dispatchAll();

        WifiNative.WifiLoggerEventHandler eventHandler =
                mock(WifiNative.WifiLoggerEventHandler.class);
        assertTrue(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mWifiChip).enableDebugErrorAlerts(eq(true));

        // Now raise the callback, this should be properly handled.
        chipCallback.onDebugErrorAlert(errorCode, errorData);
        mLooper.dispatchAll();
        verify(eventHandler).onWifiAlert(eq(errorCode), eq(errorData));

        // Now stop the logging and invoke the callback. This should be ignored.
        reset(eventHandler);
        assertTrue(mWifiVendorHal.resetLogHandler());
        chipCallback.onDebugErrorAlert(errorCode, errorData);
        mLooper.dispatchAll();
        verify(eventHandler, never()).onWifiAlert(anyInt(), anyObject());
    }

    private void startBgScan(WifiNative.ScanEventHandler eventHandler) throws Exception {
        when(mWifiStaIface.startBackgroundScan(
                anyInt(), any(WifiStaIface.StaBackgroundScanParameters.class))).thenReturn(true);
        WifiNative.ScanSettings settings = new WifiNative.ScanSettings();
        settings.num_buckets = 1;
        WifiNative.BucketSettings bucketSettings = new WifiNative.BucketSettings();
        bucketSettings.bucket = 0;
        bucketSettings.period_ms = 16000;
        bucketSettings.report_events = WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
        settings.buckets = new WifiNative.BucketSettings[] {bucketSettings};
        assertTrue(mWifiVendorHal.startBgScan(TEST_IFACE_NAME, settings, eventHandler));
    }

    private ScanResult createFrameworkBgScanResult(
            @NonNull WifiSsid ssid, @NonNull MacAddress bssid) {
        ScanResult scanResult = new ScanResult();
        scanResult.setWifiSsid(getTranslatedSsid(ssid));
        scanResult.BSSID = bssid.toString();
        scanResult.frequency = 2432;
        scanResult.level = -45;
        scanResult.timestamp = 5;
        return scanResult;
    }

    private WifiScanner.ScanData[] createFrameworkBgScanDatas() {
        byte[] ssidBytes = new byte[8];
        byte[] bssidBytes = new byte[6];
        Random random = new Random();
        random.nextBytes(ssidBytes);
        random.nextBytes(bssidBytes);
        ScanResult result = createFrameworkBgScanResult(
                WifiSsid.fromBytes(ssidBytes), MacAddress.fromBytes(bssidBytes));

        WifiScanner.ScanData[] scanDatas = new WifiScanner.ScanData[1];
        ScanResult[] scanResults = new ScanResult[1];
        scanResults[0] = result;
        WifiScanner.ScanData scanData =
                new WifiScanner.ScanData(mWifiVendorHal.mScan.cmdId, 1,
                        5, WifiScanner.WIFI_BAND_UNSPECIFIED, scanResults);
        scanDatas[0] = scanData;
        return scanDatas;
    }

    private void assertScanResultEqual(ScanResult expected, ScanResult actual) {
        assertEquals(expected.SSID, actual.SSID);
        assertEquals(expected.wifiSsid, actual.wifiSsid);
        assertEquals(expected.BSSID, actual.BSSID);
        assertEquals(expected.frequency, actual.frequency);
        assertEquals(expected.level, actual.level);
        assertEquals(expected.timestamp, actual.timestamp);
    }

    private void assertScanResultsEqual(ScanResult[] expected, ScanResult[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertScanResultEqual(expected[i], actual[i]);
        }
    }

    private void assertScanDataEqual(WifiScanner.ScanData expected, WifiScanner.ScanData actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getFlags(), actual.getFlags());
        assertEquals(expected.getBucketsScanned(), actual.getBucketsScanned());
        assertScanResultsEqual(expected.getResults(), actual.getResults());
    }

    private void assertScanDatasEqual(
            List<WifiScanner.ScanData> expected, List<WifiScanner.ScanData> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertScanDataEqual(expected.get(i), actual.get(i));
        }
    }

    /**
     * Test case to validate Wi-Fi chip capabilities methods.
     */
    @Test
    public void testGetChipCapabilities() throws Exception {
        WifiChip.WifiChipCapabilities wifiChipCapabilities = new WifiChip.WifiChipCapabilities(3, 2,
                5);
        when(mHalDeviceManager.getChip(any(WifiHal.WifiInterface.class))).thenReturn(mWifiChip);
        when(mWifiChip.getId()).thenReturn(2);
        when(mWifiChip.getWifiChipCapabilities()).thenReturn(wifiChipCapabilities);
        // Start vendor hal and create sta interface.
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        // Positive test case.
        assertEquals(2, mWifiVendorHal.getMaxMloStrLinkCount(TEST_IFACE_NAME));
        assertEquals(3, mWifiVendorHal.getMaxMloAssociationLinkCount(TEST_IFACE_NAME));
        assertEquals(5, mWifiVendorHal.getMaxSupportedConcurrentTdlsSessions(TEST_IFACE_NAME));
        // Call again few times to check caching.
        for (int i = 0; i < 5; ++i) {
            assertEquals(2, mWifiVendorHal.getMaxMloStrLinkCount(TEST_IFACE_NAME));
            assertEquals(3, mWifiVendorHal.getMaxMloAssociationLinkCount(TEST_IFACE_NAME));
            assertEquals(5, mWifiVendorHal.getMaxSupportedConcurrentTdlsSessions(TEST_IFACE_NAME));
        }
        // Make sure only one call to chip.
        verify(mWifiChip, times(1)).getWifiChipCapabilities();
        // Negative test case: null capabilities
        when(mWifiChip.getId()).thenReturn(1);
        when(mWifiChip.getWifiChipCapabilities()).thenReturn(null);
        assertEquals(-1, mWifiVendorHal.getMaxMloStrLinkCount(TEST_IFACE_NAME));
        assertEquals(-1, mWifiVendorHal.getMaxMloAssociationLinkCount(TEST_IFACE_NAME));
        assertEquals(-1, mWifiVendorHal.getMaxSupportedConcurrentTdlsSessions(TEST_IFACE_NAME));
        // Negative test case: null chip
        when(mHalDeviceManager.getChip(any(WifiHal.WifiInterface.class))).thenReturn(null);
        assertEquals(-1, mWifiVendorHal.getMaxMloStrLinkCount(TEST_IFACE_NAME));
        assertEquals(-1, mWifiVendorHal.getMaxMloAssociationLinkCount(TEST_IFACE_NAME));
        assertEquals(-1, mWifiVendorHal.getMaxSupportedConcurrentTdlsSessions(TEST_IFACE_NAME));
    }

    /**
     * Verifies that setAfcChannelAllowance() calls underlying WifiChip.
     */
    @Test
    public void testSetAfcChannelAllowance() {
        assertTrue(mWifiVendorHal.startVendorHal());
        assertNotNull(mWifiVendorHal.createApIface(null, null,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager, new ArrayList<>()));

        mWifiVendorHal.setAfcChannelAllowance(mAfcChannelAllowance);
        verify(mWifiChip).setAfcChannelAllowance(mAfcChannelAllowance);
    }

    /**
     * Test setRoamingMode
     *
     * A call before the vendor HAL is started should return invalid interface.
     *
     * A call after the HAL is started should return success value.
     */
    @Test
    public void testSetRoamingMode() throws Exception {
        assertTrue(mWifiVendorHal.setRoamingMode(TEST_IFACE_NAME, WifiManager.ROAMING_MODE_NORMAL)
                == WifiStatusCode.ERROR_WIFI_IFACE_INVALID);
        // Start the vendor hal
        assertTrue(mWifiVendorHal.startVendorHalSta(mConcreteClientModeManager));
        assertTrue(mWifiVendorHal.setRoamingMode(TEST_IFACE_NAME, WifiManager.ROAMING_MODE_NORMAL)
                == WifiStatusCode.SUCCESS);
    }
}
