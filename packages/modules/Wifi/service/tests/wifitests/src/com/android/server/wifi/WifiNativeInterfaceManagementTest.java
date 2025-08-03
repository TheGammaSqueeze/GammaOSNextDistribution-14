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
 * limitations under the License
 */

package com.android.server.wifi;

import static com.android.server.wifi.p2p.WifiP2pNative.P2P_IFACE_NAME;
import static com.android.server.wifi.p2p.WifiP2pNative.P2P_INTERFACE_PROPERTY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiScanner;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.Handler;
import android.os.WorkSource;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.HalDeviceManager.InterfaceDestroyedListener;
import com.android.server.wifi.WifiNative.SupplicantDeathEventHandler;
import com.android.server.wifi.WifiNative.VendorHalDeathEventHandler;
import com.android.server.wifi.p2p.WifiP2pNative;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.NetdWrapper.NetdEventObserver;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for the interface management operations in
 * {@link com.android.server.wifi.WifiNative}.
 */
@SmallTest
public class WifiNativeInterfaceManagementTest extends WifiBaseTest {
    private static final String IFACE_NAME_0 = "mockWlan0";
    private static final String IFACE_NAME_1 = "mockWlan1";
    private static final String SELF_RECOVERY_IFACE_NAME = "mockWlan2";
    private static final WorkSource TEST_WORKSOURCE = new WorkSource();
    private static final long TEST_SUPPORTED_FEATURES = 0;
    private static final int STA_FAILURE_CODE_START_DAEMON = 1;
    private static final int STA_FAILURE_CODE_SETUP_INTERFACE = 2;
    private static final int STA_FAILURE_CODE_WIFICOND_SETUP_INTERFACE = 3;
    private static final int STA_FAILURE_CODE_CREAT_IFACE = 4;
    private static final int SOFTAP_FAILURE_CODE_SETUP_INTERFACE = 1;
    private static final int SOFTAP_FAILURE_CODE_START_DAEMON = 2;
    private static final int SOFTAP_FAILURE_CODE_CREATE_IFACE = 3;
    private static final int SOFTAP_FAILURE_CODE_BRIDGED_AP_INSTANCES = 4;
    private static final int P2P_FAILURE_CODE_CREATE_INTERFACE = 1;
    private static final int TEST_SUPPORTED_BANDS = 15;

    MockResources mResources;

    @Mock private WifiVendorHal mWifiVendorHal;
    @Mock private WifiNl80211Manager mWificondControl;
    @Mock private SupplicantStaIfaceHal mSupplicantStaIfaceHal;
    @Mock private HostapdHal mHostapdHal;
    @Mock private WifiMonitor mWifiMonitor;
    @Mock private NetdWrapper mNetdWrapper;
    @Mock private PropertyService mPropertyService;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock BuildProperties mBuildProperties;
    @Mock private WifiInjector mWifiInjector;
    @Mock private WifiContext mContext;
    @Mock private HalDeviceManager mHalDeviceManager;
    @Mock private WifiP2pNative mWifiP2pNative;

    @Mock private WifiNative.StatusListener mStatusListener;
    @Mock private WifiNative.InterfaceCallback mIfaceCallback0;
    @Mock private WifiNative.InterfaceCallback mIfaceCallback1;
    @Mock private WifiNative.InterfaceEventCallback mIfaceEventCallback0;
    @Mock private HalDeviceManager.InterfaceDestroyedListener mP2pInterfaceDestroyedListener;
    @Mock private Handler mP2pEventHandler;

    @Mock private WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock private WifiGlobals mWifiGlobals;
    @Mock private ConcreteClientModeManager mConcreteClientModeManager;
    @Mock private SoftApManager mSoftApManager;
    @Mock DeviceConfigFacade mDeviceConfigFacade;

    private TestLooper mLooper;
    private WifiNative.Iface mActiveP2pIface;

    private ArgumentCaptor<VendorHalDeathEventHandler> mWifiVendorHalDeathHandlerCaptor =
            ArgumentCaptor.forClass(VendorHalDeathEventHandler.class);
    private ArgumentCaptor<Runnable> mWificondDeathHandlerCaptor =
            ArgumentCaptor.forClass(Runnable.class);
    private ArgumentCaptor<WifiNative.VendorHalRadioModeChangeEventHandler>
            mWifiVendorHalRadioModeChangeHandlerCaptor =
            ArgumentCaptor.forClass(WifiNative.VendorHalRadioModeChangeEventHandler.class);
    private ArgumentCaptor<SupplicantDeathEventHandler> mSupplicantDeathHandlerCaptor =
            ArgumentCaptor.forClass(SupplicantDeathEventHandler.class);
    private ArgumentCaptor<WifiNative.HostapdDeathEventHandler> mHostapdDeathHandlerCaptor =
            ArgumentCaptor.forClass(WifiNative.HostapdDeathEventHandler.class);
    private ArgumentCaptor<NetdEventObserver> mNetworkObserverCaptor0 =
            ArgumentCaptor.forClass(NetdEventObserver.class);
    private ArgumentCaptor<NetdEventObserver> mNetworkObserverCaptor1 =
            ArgumentCaptor.forClass(NetdEventObserver.class);
    private ArgumentCaptor<InterfaceDestroyedListener> mIfaceDestroyedListenerCaptor0 =
            ArgumentCaptor.forClass(InterfaceDestroyedListener.class);
    private ArgumentCaptor<InterfaceDestroyedListener> mIfaceDestroyedListenerCaptor1 =
            ArgumentCaptor.forClass(InterfaceDestroyedListener.class);
    private InOrder mInOrder;

    private WifiNative mWifiNative;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();

        // Setup mocks for the positive single interface cases, individual tests can modify the
        // mocks for negative or multi-interface tests.
        when(mWifiVendorHal.initialize(mWifiVendorHalDeathHandlerCaptor.capture()))
            .thenReturn(true);
        doNothing().when(mWifiVendorHal).registerRadioModeChangeHandler(
                mWifiVendorHalRadioModeChangeHandlerCaptor.capture());
        when(mWifiVendorHal.isVendorHalSupported()).thenReturn(true);
        when(mWifiVendorHal.isVendorHalReady()).thenReturn(true);
        when(mWifiVendorHal.startVendorHal()).thenReturn(true);
        when(mWifiVendorHal.createStaIface(any(), any(), any())).thenReturn(IFACE_NAME_0);
        when(mWifiVendorHal.createApIface(any(), any(), anyInt(),
                anyBoolean(), any(), anyList())).thenReturn(IFACE_NAME_0);
        when(mWifiVendorHal.getBridgedApInstances(any())).thenReturn(
                List.of(IFACE_NAME_0));
        when(mWifiVendorHal.removeStaIface(any())).thenReturn(true);
        when(mWifiVendorHal.removeApIface(any())).thenReturn(true);
        when(mWifiVendorHal.replaceStaIfaceRequestorWs(any(), any())).thenReturn(true);
        when(mWifiVendorHal.getUsableChannels(anyInt(), anyInt(), anyInt())).thenReturn(
                new ArrayList<>());
        when(mWifiVendorHal.enableStaChannelForPeerNetwork(anyBoolean(), anyBoolean())).thenReturn(
                true);

        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(true);

        when(mWificondControl.setupInterfaceForClientMode(any(), any(), any(), any())).thenReturn(
                true);
        when(mWificondControl.setupInterfaceForSoftApMode(any())).thenReturn(true);
        when(mWificondControl.tearDownClientInterface(any())).thenReturn(true);
        when(mWificondControl.tearDownSoftApInterface(any())).thenReturn(true);
        when(mWificondControl.tearDownInterfaces()).thenReturn(true);
        when(mWificondControl.registerApCallback(any(), any(), any())).thenReturn(true);

        when(mSupplicantStaIfaceHal.registerDeathHandler(mSupplicantDeathHandlerCaptor.capture()))
            .thenReturn(true);
        when(mSupplicantStaIfaceHal.deregisterDeathHandler()).thenReturn(true);
        when(mSupplicantStaIfaceHal.initialize()).thenReturn(true);
        when(mSupplicantStaIfaceHal.isInitializationStarted()).thenReturn(false);
        when(mSupplicantStaIfaceHal.isInitializationComplete()).thenReturn(true);
        when(mSupplicantStaIfaceHal.startDaemon()).thenReturn(true);
        when(mSupplicantStaIfaceHal.setupIface(any())).thenReturn(true);
        when(mSupplicantStaIfaceHal.teardownIface(any())).thenReturn(true);

        when(mHostapdHal.registerDeathHandler(mHostapdDeathHandlerCaptor.capture()))
                .thenReturn(true);
        when(mHostapdHal.deregisterDeathHandler()).thenReturn(true);
        when(mHostapdHal.initialize()).thenReturn(true);
        when(mHostapdHal.isInitializationStarted()).thenReturn(false);
        when(mHostapdHal.isInitializationComplete()).thenReturn(true);
        when(mHostapdHal.startDaemon()).thenReturn(true);
        when(mHostapdHal.addAccessPoint(any(), any(), anyBoolean(), any())).thenReturn(true);
        when(mHostapdHal.removeAccessPoint(any())).thenReturn(true);
        when(mHostapdHal.registerApCallback(any(), any())).thenReturn(true);

        when(mWifiGlobals.isWifiInterfaceAddedSelfRecoveryEnabled()).thenReturn(false);

        when(mWifiInjector.makeNetdWrapper()).thenReturn(mNetdWrapper);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
        when(mWifiInjector.getContext()).thenReturn(mContext);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.getHalDeviceManager()).thenReturn(mHalDeviceManager);
        when(mWifiInjector.getWifiP2pNative()).thenReturn(mWifiP2pNative);
        mResources = getMockResources();
        mResources.setBoolean(R.bool.config_wifiNetworkCentricQosPolicyFeatureEnabled, false);
        mResources.setString(
                R.string.config_wifiSelfRecoveryInterfaceName, SELF_RECOVERY_IFACE_NAME);
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.isInterfaceFailureBugreportEnabled()).thenReturn(false);

        when(mWifiSettingsConfigStore.get(
                eq(WifiSettingsConfigStore.WIFI_NATIVE_SUPPORTED_FEATURES)))
                .thenReturn(TEST_SUPPORTED_FEATURES);
        when(mWifiSettingsConfigStore.get(
                eq(WifiSettingsConfigStore.WIFI_NATIVE_SUPPORTED_STA_BANDS)))
                .thenReturn(TEST_SUPPORTED_BANDS);

        mInOrder = inOrder(mWifiVendorHal, mWificondControl, mSupplicantStaIfaceHal, mHostapdHal,
                mWifiMonitor, mNetdWrapper, mIfaceCallback0, mIfaceCallback1, mIfaceEventCallback0,
                mWifiMetrics, mWifiP2pNative);

        mWifiNative = new WifiNative(
                mWifiVendorHal, mSupplicantStaIfaceHal, mHostapdHal, mWificondControl,
                mWifiMonitor, mPropertyService, mWifiMetrics,
                new Handler(mLooper.getLooper()), null, mBuildProperties, mWifiInjector);
        mWifiNative.initialize();
        mWifiNative.registerStatusListener(mStatusListener);

        mInOrder.verify(mWifiVendorHal).initialize(any());
        mInOrder.verify(mWificondControl).setOnServiceDeadCallback(
                mWificondDeathHandlerCaptor.capture());
        mInOrder.verify(mWificondControl).tearDownInterfaces();
        mInOrder.verify(mWifiVendorHal).registerRadioModeChangeHandler(any());
        mActiveP2pIface = null;
    }

    @After
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(mWifiVendorHal, mWificondControl, mSupplicantStaIfaceHal,
                mHostapdHal, mWifiMonitor, mNetdWrapper, mIfaceCallback0, mIfaceCallback1,
                mIfaceEventCallback0, mWifiMetrics);
    }

    private MockResources getMockResources() {
        MockResources resources = new MockResources();
        return resources;
    }

    /**
     * Verifies the setup of a single client interface.
     */
    @Test
    public void testSetupClientInterface() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
    }

    /**
     * Verifies the setup of a single client interface when the overlay indicates that the
     * network-centric QoS policy feature is enabled.
     */
    @Test
    public void testSetupClientInterfaceWithQosPolicyFeatureEnabled() throws Exception {
        mResources.setBoolean(R.bool.config_wifiNetworkCentricQosPolicyFeatureEnabled, true);
        when(mSupplicantStaIfaceHal.isAidlService()).thenReturn(true);
        when(mSupplicantStaIfaceHal
                .setNetworkCentricQosPolicyFeatureEnabled(anyString(), anyBoolean()))
                .thenReturn(true);
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        verify(mSupplicantStaIfaceHal).isAidlService();
        verify(mSupplicantStaIfaceHal)
                .setNetworkCentricQosPolicyFeatureEnabled(IFACE_NAME_0, true);
    }

    /**
     * Verifies the setup of a single client interface (for scan).
     */
    @Test
    public void testSetupClientInterfaceForScan() throws Exception {
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        verifyNoMoreInteractions(mWifiVendorHal, mWificondControl, mSupplicantStaIfaceHal,
                mHostapdHal, mNetdWrapper, mIfaceCallback0, mIfaceCallback1, mWifiMetrics);
    }

    /**
     * Verifies the setup of a single softAp interface.
     */
    @Test
    public void testSetupSoftApInterface() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0,
                mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getSoftApInterfaceNames());
    }

    /**
     * Verifies the setup of a single softAp interface.
     */
    @Test
    public void testSetupSoftApInterfaceInBridgedMode() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0,
                mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, true, true, 0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getSoftApInterfaceNames());
        assertEquals(Set.of(), mWifiNative.getClientInterfaceNames());
    }

    /**
     * Verifies the setup & teardown of a single client interface.
     */
    @Test
    public void testSetupAndTeardownClientInterface() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownClientInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0.getValue(), mNetworkObserverCaptor0.getValue());
    }

    /**
     * Verifies the setup & teardown of a single client interface (for scan).
     */
    @Test
    public void testSetupAndTeardownClientInterfaceForScan() throws Exception {
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownClientInterfaceForScan(false, false, IFACE_NAME_0,
                mIfaceCallback0, mIfaceDestroyedListenerCaptor0.getValue(),
                mNetworkObserverCaptor0.getValue());
        verifyNoMoreInteractions(mWifiVendorHal, mWificondControl, mSupplicantStaIfaceHal,
                mHostapdHal, mNetdWrapper, mIfaceCallback0, mIfaceCallback1, mWifiMetrics);
    }

    /**
     * Verifies the setup & teardown of a single softAp interface.
     */
    @Test
    public void testSetupAndTeardownSoftApInterface() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getSoftApInterfaceNames());
        assertEquals(Set.of(), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0.getValue(), mNetworkObserverCaptor0.getValue());
    }

    /**
     * Verifies the setup & teardown of a client & softAp interface.
     *
     * Sequence tested:
     * a) Setup client interface.
     * b) Setup softAp interface.
     * c) Teardown client interface.
     * d) Teardown softAp interface.
     */
    @Test
    public void testSetupAndTeardownClientAndSoftApInterface_Seq1() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSetupSoftApInterface(
                true, false, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownClientInterface(false, true, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0.getValue(), mNetworkObserverCaptor0.getValue());
        executeAndValidateTeardownSoftApInterface(false, false, IFACE_NAME_1, mIfaceCallback1,
                mIfaceDestroyedListenerCaptor1.getValue(), mNetworkObserverCaptor1.getValue());
    }

    /**
     * Verifies the setup & teardown of a client & softAp interface.
     *
     * Sequence tested:
     * a) Setup client interface.
     * b) Setup softAp interface.
     * c) Teardown softAp interface.
     * d) Teardown client interface.
     */
    @Test
    public void testSetupAndTeardownClientAndSoftApInterface_Seq2() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSetupSoftApInterface(
                true, false, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownSoftApInterface(true, false, IFACE_NAME_1, mIfaceCallback1,
                mIfaceDestroyedListenerCaptor1.getValue(), mNetworkObserverCaptor1.getValue());
        executeAndValidateTeardownClientInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0.getValue(), mNetworkObserverCaptor0.getValue());
    }

    /**
     * Verifies the setup & teardown of a client & softAp interface.
     *
     * Sequence tested:
     * a) Setup softAp interface.
     * b) Setup client interface.
     * c) Teardown softAp interface.
     * d) Teardown client interface.
     */
    @Test
    public void testSetupAndTeardownClientAndSoftApInterface_Seq3() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSetupClientInterface(
                false, true, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getSoftApInterfaceNames());
        assertEquals(Set.of(IFACE_NAME_1), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownSoftApInterface(true, false, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0.getValue(), mNetworkObserverCaptor0.getValue());
        executeAndValidateTeardownClientInterface(false, false, IFACE_NAME_1, mIfaceCallback1,
                mIfaceDestroyedListenerCaptor1.getValue(), mNetworkObserverCaptor1.getValue());
    }

    /**
     * Verifies the setup & teardown of a client & softAp interface.
     *
     * Sequence tested:
     * a) Setup softAp interface.
     * b) Setup client interface.
     * c) Teardown client interface.
     * d) Teardown softAp interface.
     */
    @Test
    public void testSetupAndTeardownClientAndSoftApInterface_Seq4() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSetupClientInterface(
                false, true, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getSoftApInterfaceNames());
        assertEquals(Set.of(IFACE_NAME_1), mWifiNative.getClientInterfaceNames());
        executeAndValidateTeardownClientInterface(false, true, IFACE_NAME_1, mIfaceCallback1,
                mIfaceDestroyedListenerCaptor1.getValue(), mNetworkObserverCaptor1.getValue());
        executeAndValidateTeardownSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0.getValue(), mNetworkObserverCaptor0.getValue());
    }

    /**
     * Verifies the setup of a client & softAp interface & then initiate teardown of all active
     * interfaces.
     *
     * Sequence tested:
     * a) Setup softAp interface.
     * b) Setup client interface.
     * c) Teardown all active interfaces.
     */
    @Test
    public void testTeardownAllInterfaces() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSetupClientInterface(
                false, true, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);

        // Assert that a client & softap interface is present.
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getSoftApInterfaceNames());
        assertEquals(Set.of(IFACE_NAME_1), mWifiNative.getClientInterfaceNames());

        mWifiNative.teardownAllInterfaces();

        // Note: This is not using InOrder because order of interface deletion cannot be
        // predetermined.

        // Verify STA removal
        verify(mWifiMonitor).stopMonitoring(IFACE_NAME_1);
        verify(mNetdWrapper).unregisterObserver(mNetworkObserverCaptor1.getValue());
        verify(mSupplicantStaIfaceHal).teardownIface(IFACE_NAME_1);
        verify(mWificondControl).tearDownClientInterface(IFACE_NAME_1);
        verify(mSupplicantStaIfaceHal, atLeastOnce()).isInitializationStarted();
        verify(mSupplicantStaIfaceHal).deregisterDeathHandler();
        verify(mSupplicantStaIfaceHal).terminate();
        verify(mIfaceCallback1).onDestroyed(IFACE_NAME_1);

        // Verify AP removal
        verify(mNetdWrapper).unregisterObserver(mNetworkObserverCaptor0.getValue());
        verify(mHostapdHal).removeAccessPoint(IFACE_NAME_0);
        verify(mWificondControl).tearDownSoftApInterface(IFACE_NAME_0);
        verify(mHostapdHal).deregisterDeathHandler();
        verify(mHostapdHal).terminate();

        // Verify we stopped HAL & wificond
        verify(mWificondControl, times(2)).tearDownInterfaces(); // first time at initialize
        verify(mWifiVendorHal).stopVendorHal();
        verify(mIfaceCallback0).onDestroyed(IFACE_NAME_0);

        verify(mWifiVendorHal, atLeastOnce()).isVendorHalReady();
        verify(mWifiVendorHal, atLeastOnce()).isVendorHalSupported();

        // Assert that the client & softap interface is no more there.
        assertEquals(Set.of(), mWifiNative.getClientInterfaceNames());
        assertEquals(Set.of(), mWifiNative.getSoftApInterfaceNames());
    }

    /**
     * Verifies the setup of a client interface and then a SoftAp interface which would
     * destroy the Client interface. This is what would happen on older devices which do not
     * support concurrent interfaces.
     */
    @Test
    public void testSetupClientAndSoftApInterfaceCausesClientInterfaceTeardown() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        // Trigger the STA interface teardown when AP interface is created.
        // The iface name will remain the same.
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public String answer(InterfaceDestroyedListener destroyedListener, WorkSource ws,
                    int band, boolean isBridged, SoftApManager softApManager,
                    List<OuiKeyedData> vendorData) {
                mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
                return IFACE_NAME_0;
            }
        }).when(mWifiVendorHal).createApIface(any(), any(), anyInt(), eq(false), any(), anyList());
        assertEquals(IFACE_NAME_0, mWifiNative.setupInterfaceForSoftApMode(mIfaceCallback1,
                TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ, false, mSoftApManager,
                new ArrayList<>()));

        validateHostApdStart();
        // Creation of AP interface should trigger the STA interface destroy
        validateOnDestroyedClientInterface(
                false, true, IFACE_NAME_0, mIfaceCallback0, mNetworkObserverCaptor0.getValue());
        // Now continue with rest of AP interface setup.
        validateSetupInterfaceForSoftAp(IFACE_NAME_0, mNetworkObserverCaptor1);

        // Execute a teardown of the interface to ensure that the new iface removal works.
        executeAndValidateTeardownSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback1,
                mIfaceDestroyedListenerCaptor1.getValue(), mNetworkObserverCaptor1.getValue());
    }

    private void validateSwitchInterfaceToScan(String ifaceName, WorkSource workSource) {
        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).replaceStaIfaceRequestorWs(ifaceName, workSource);
        mInOrder.verify(mSupplicantStaIfaceHal).teardownIface(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
        mInOrder.verify(mSupplicantStaIfaceHal).deregisterDeathHandler();
        mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
        mInOrder.verify(mSupplicantStaIfaceHal).terminate();
        mInOrder.verify(mSupplicantStaIfaceHal).getAdvancedCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getSupportedFeatureSet(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getWpaDriverFeatureSet(ifaceName);
        mInOrder.verify(mWifiVendorHal).getTwtCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getUsableChannels(anyInt(), anyInt(), anyInt());
    }

    private void validateHostApdStart() {
        mInOrder.verify(mHostapdHal).isInitializationStarted();
        mInOrder.verify(mHostapdHal).initialize();
        mInOrder.verify(mHostapdHal).startDaemon();
        mInOrder.verify(mHostapdHal).isInitializationComplete();
        mInOrder.verify(mHostapdHal).registerDeathHandler(any());
        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).createApIface(
                mIfaceDestroyedListenerCaptor1.capture(), eq(TEST_WORKSOURCE), anyInt(), eq(false),
                eq(mSoftApManager), anyList());
    }

    private void validateSetupInterfaceForScan(String ifaceName,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor) {
        mInOrder.verify(mWificondControl).setupInterfaceForClientMode(eq(ifaceName), any(),
                any(), any());
        mInOrder.verify(mNetdWrapper, atLeastOnce())
                .registerObserver(networkObserverCaptor.capture());
        mInOrder.verify(mWifiMonitor).startMonitoring(ifaceName);
        mInOrder.verify(mNetdWrapper).isInterfaceUp(ifaceName);
        mInOrder.verify(mWifiVendorHal).enableLinkLayerStats(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getAdvancedCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getSupportedFeatureSet(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getWpaDriverFeatureSet(ifaceName);
        mInOrder.verify(mWifiVendorHal).getTwtCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getUsableChannels(anyInt(), anyInt(), anyInt());
        mInOrder.verify(mWifiVendorHal).enableStaChannelForPeerNetwork(anyBoolean(), anyBoolean());
    }

    private void validateSetupInterfaceForSoftAp(String ifaceName,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor) {
        mInOrder.verify(mWificondControl).setupInterfaceForSoftApMode(ifaceName);
        mInOrder.verify(mNetdWrapper).registerObserver(networkObserverCaptor.capture());
        mInOrder.verify(mNetdWrapper).isInterfaceUp(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getAdvancedCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getSupportedFeatureSet(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getWpaDriverFeatureSet(ifaceName);
        mInOrder.verify(mWifiVendorHal).getTwtCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getUsableChannels(anyInt(), anyInt(), anyInt());
    }

    /**
     * Verifies the setup of a client interface and then a SoftAp interface which would
     * destroy the Client interface. This is what would happen on older devices which do not
     * support concurrent interfaces.
     */
    @Test
    public void testSetupSoftApAndClientInterfaceCausesSoftApInterfaceTeardown() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        // Trigger the AP interface teardown when STA interface is created.
        // The iface name will remain the same.
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public String answer(InterfaceDestroyedListener destroyedListener, WorkSource ws,
                    ConcreteClientModeManager concreteClientModeManager) {
                mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
                return IFACE_NAME_0;
            }
        }).when(mWifiVendorHal).createStaIface(any(), any(), eq(mConcreteClientModeManager));

        assertEquals(IFACE_NAME_0,
                mWifiNative.setupInterfaceForClientInScanMode(mIfaceCallback1, TEST_WORKSOURCE,
                        mConcreteClientModeManager));
        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).createStaIface(
                mIfaceDestroyedListenerCaptor1.capture(), eq(TEST_WORKSOURCE),
                eq(mConcreteClientModeManager));
        // Creation of STA interface should trigger the AP interface destroy.
        validateOnDestroyedSoftApInterface(
                true, false, IFACE_NAME_0, mIfaceCallback0, mNetworkObserverCaptor0.getValue());
        // Now continue with rest of STA interface setup.
        validateSetupInterfaceForScan(IFACE_NAME_0, mNetworkObserverCaptor1);

        // Execute a teardown of the interface to ensure that the new iface removal works.
        executeAndValidateTeardownClientInterfaceForScan(false, false, IFACE_NAME_0,
                mIfaceCallback1, mIfaceDestroyedListenerCaptor1.getValue(),
                mNetworkObserverCaptor1.getValue());
    }

    /**
     * Verifies the setup of a client interface and trigger an interface down event.
     * This should be ignored since interface is considered to be down before setup.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceDown() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, false, mNetworkObserverCaptor0.getValue());
    }

    /**
     * Verifies the setup of a client interface and trigger an interface up event.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceUp() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onUp(IFACE_NAME_0);
    }

    /**
     * Triggers adding the interface configured as
     * {@link R.string.config_wifiSelfRecoveryInterfaceName}. Verifies that this fires
     * {@link com.android.server.wifi.WifiNative.InterfaceEventCallback#onInterfaceAdded(String)}.
     */
    @Test
    public void testSetupClientInterfaceForScanAndTriggerInterfaceAdded() throws Exception {
        when(mWifiGlobals.isWifiInterfaceAddedSelfRecoveryEnabled()).thenReturn(true);
        mWifiNative.setWifiNativeInterfaceEventCallback(mIfaceEventCallback0);
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceAdded(
                SELF_RECOVERY_IFACE_NAME, mNetworkObserverCaptor0.getAllValues());
    }

    /**
     * Verifies the setup of a client interface and trigger an interface up event, followed by a
     * down event.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceUpFollowedByDown() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onUp(IFACE_NAME_0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, false, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onDown(IFACE_NAME_0);
        mInOrder.verify(mWifiMetrics).incrementNumClientInterfaceDown();
    }

    /**
     * Verifies the setup of a softap interface and trigger an interface up event, followed by a
     * down event.
     */
    @Test
    public void testSetupSoftApInterfaceAndTriggerInterfaceUpFollowedByDown() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onUp(IFACE_NAME_0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, false, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onDown(IFACE_NAME_0);
        mInOrder.verify(mWifiMetrics).incrementNumSoftApInterfaceDown();
    }

    /**
     * Verifies the setup of a client interface and trigger an interface up event, followed by
     * link down/up events. The link state change events should be ignored since we only care for
     * interface state changes.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceUpFollowedByLinkDownAndUp()
            throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onUp(IFACE_NAME_0);

        // Trigger a link down, with the interface still up.
        // Should not trigger the external iface callback.
        mNetworkObserverCaptor0.getValue().interfaceLinkStateChanged(IFACE_NAME_0, false);
        mLooper.dispatchAll();
        mInOrder.verify(mNetdWrapper).isInterfaceUp(IFACE_NAME_0);

        // Now trigger a link up, with the interface still up.
        // Should not trigger the external iface callback.
        mNetworkObserverCaptor0.getValue().interfaceLinkStateChanged(IFACE_NAME_0, true);
        mLooper.dispatchAll();
        mInOrder.verify(mNetdWrapper).isInterfaceUp(IFACE_NAME_0);
    }

    /**
     * Verifies the setup of a client interface and trigger an interface up event, followed by
     * link down/up events. The link state change events should be ignored since we only care for
     * interface state changes.
     */
    @Test
    public void testSetupSoftApInterfaceAndTriggerInterfaceUpFollowedByLinkDownAndUp()
            throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onUp(IFACE_NAME_0);

        // Trigger a link down, with the interface still up.
        // Should not trigger the external iface callback.
        mNetworkObserverCaptor0.getValue().interfaceLinkStateChanged(IFACE_NAME_0, false);
        mLooper.dispatchAll();
        mInOrder.verify(mNetdWrapper).isInterfaceUp(IFACE_NAME_0);

        // Now trigger a link up, with the interface still up.
        // Should not trigger the external iface callback.
        mNetworkObserverCaptor0.getValue().interfaceLinkStateChanged(IFACE_NAME_0, true);
        mLooper.dispatchAll();
        mInOrder.verify(mNetdWrapper).isInterfaceUp(IFACE_NAME_0);
    }

    /**
     * Verifies the setup of a client interface and trigger an interface up event twice.
     * The second interface up event should be masked.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceUpTwice() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
        mInOrder.verify(mIfaceCallback0).onUp(IFACE_NAME_0);

        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor0.getValue());
    }

    /**
     * Verifies the setup of a client interface and trigger an interface up event on a different
     * interface.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceUpOnAnInvalidIface() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        mNetworkObserverCaptor0.getValue().interfaceLinkStateChanged(IFACE_NAME_1, true);
        mLooper.dispatchAll();
    }

    /**
     * Verifies that interface down on a destroyed interface is ignored.
     * The test triggers
     * a) Setup of a client interface
     * b) Setup of a SoftAp interface which would destroy the Client interface.
     * This is what would happen on older devices which do not support concurrent interfaces.
     * c) Once the client interface is destroyed, trigger an interface up event on the old
     * network observer. This should be ignored.
     * d) Trigger an interface down event on the new network observer. This should trigger an
     * interface up event to external clients.
     * e) Remove the new SoftAp interface.
     */
    @Test
    public void testSetupClientInterfaceAndTriggerInterfaceUpOnDestroyedIface() throws Exception {
        // Step (a)
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        // Step (b)
        // Trigger the STA interface teardown when AP interface is created.
        // The iface name will remain the same.
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public String answer(InterfaceDestroyedListener destroyedListener, WorkSource ws,
                    int band, boolean isBridged, SoftApManager softApManager,
                    List<OuiKeyedData> vendorData) {
                mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
                return IFACE_NAME_0;
            }
        }).when(mWifiVendorHal).createApIface(any(), any(), anyInt(), eq(false), any(), anyList());
        when(mWifiVendorHal.isVendorHalSupported()).thenReturn(true);

        assertEquals(IFACE_NAME_0, mWifiNative.setupInterfaceForSoftApMode(mIfaceCallback1,
                TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ, false, mSoftApManager,
                new ArrayList<>()));
        validateHostApdStart();
        // Creation of AP interface should trigger the STA interface destroy
        validateOnDestroyedClientInterface(
                false, true, IFACE_NAME_0, mIfaceCallback0, mNetworkObserverCaptor0.getValue());
        // Now continue with rest of AP interface setup.
        validateSetupInterfaceForSoftAp(IFACE_NAME_0, mNetworkObserverCaptor1);

        // Step (c) - Iface up on old iface, ignored!
        mNetworkObserverCaptor0.getValue().interfaceLinkStateChanged(IFACE_NAME_0, true);
        mLooper.dispatchAll();

        // Step (d) - Iface up on new iface, handled!
        executeAndValidateInterfaceStateChange(
                IFACE_NAME_0, true, mNetworkObserverCaptor1.getValue());
        mInOrder.verify(mIfaceCallback1).onUp(IFACE_NAME_0);

        // Execute a teardown of the softap interface to ensure that the new iface removal works.
        executeAndValidateTeardownSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback1,
                mIfaceDestroyedListenerCaptor1.getValue(), mNetworkObserverCaptor1.getValue());
    }

    /**
     * Verifies the setup of a client interface and wificond death handling.
     */
    @Test
    public void testSetupClientInterfaceAndWicondDied() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        // Trigger wificond death
        mWificondDeathHandlerCaptor.getValue().run();

        mInOrder.verify(mWifiMetrics).incrementNumWificondCrashes();

        verify(mStatusListener).onStatusChanged(false);
        verify(mStatusListener).onStatusChanged(true);
    }

    /**
     * Verifies the setup of a soft ap interface and vendor HAL death handling.
     */
    @Test
    public void testSetupSoftApInterfaceAndVendorHalDied() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        // Trigger vendor HAL death

        mWifiVendorHalDeathHandlerCaptor.getValue().onDeath();

        mInOrder.verify(mWifiMetrics).incrementNumHalCrashes();

        verify(mStatusListener).onStatusChanged(false);
        verify(mStatusListener).onStatusChanged(true);

    }

    /**
     * Verifies the setup of a client interface and supplicant HAL death handling.
     */
    @Test
    public void testSetupClientInterfaceAndSupplicantDied() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        // Trigger supplicant death
        mSupplicantDeathHandlerCaptor.getValue().onDeath();
        mLooper.dispatchAll();

        mInOrder.verify(mWifiMetrics).incrementNumSupplicantCrashes();

        verify(mStatusListener).onStatusChanged(false);
        verify(mStatusListener).onStatusChanged(true);
    }

    /**
     * Verifies the setup of a soft ap interface and hostapd death handling.
     */
    @Test
    public void testStartSoftApAndHostapdDied() throws Exception {
        when(mHostapdHal.isApInfoCallbackSupported()).thenReturn(true);
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, false, true, 0);

        // Start softap
        assertEquals(SoftApManager.START_RESULT_SUCCESS,
                mWifiNative.startSoftAp(IFACE_NAME_0, new SoftApConfiguration.Builder().build(),
                        true, mock(WifiNative.SoftApHalCallback.class)));

        mInOrder.verify(mHostapdHal).isApInfoCallbackSupported();
        mInOrder.verify(mHostapdHal).registerApCallback(any(), any());
        mInOrder.verify(mHostapdHal).addAccessPoint(any(), any(), anyBoolean(), any());

        // Trigger vendor HAL death
        mHostapdDeathHandlerCaptor.getValue().onDeath();

        mInOrder.verify(mWifiMetrics).incrementNumHostapdCrashes();

        verify(mStatusListener).onStatusChanged(false);
        verify(mStatusListener).onStatusChanged(true);
        verify(mWificondControl, never()).registerApCallback(any(), any(), any());
    }

    /**
     * Verifies the setup of a soft ap interface and hostapd death handling.
     */
    @Test
    public void testStartSoftApWithWifiCondCallbackAndHostapdDied() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, false, true, 0);

        // Start softap
        assertEquals(SoftApManager.START_RESULT_SUCCESS,
                mWifiNative.startSoftAp(IFACE_NAME_0, new SoftApConfiguration.Builder().build(),
                        true, mock(WifiNative.SoftApHalCallback.class)));

        mInOrder.verify(mHostapdHal).isApInfoCallbackSupported();
        mInOrder.verify(mWificondControl).registerApCallback(any(), any(), any());
        verify(mHostapdHal, never()).registerApCallback(any(), any());
        mInOrder.verify(mHostapdHal).addAccessPoint(any(), any(), anyBoolean(), any());

        // Trigger vendor HAL death
        mHostapdDeathHandlerCaptor.getValue().onDeath();

        mInOrder.verify(mWifiMetrics).incrementNumHostapdCrashes();

        verify(mStatusListener).onStatusChanged(false);
        verify(mStatusListener).onStatusChanged(true);
    }

    private void validateInterfaceTearDown(String ifaceName) {
        // To test if the failure is handled cleanly, invoke teardown and ensure that
        // none of the mocks are used because the iface does not exist in the internal
        // database.
        if (mWifiNative.hasAnyIface()) {
            mWifiNative.teardownInterface(ifaceName);
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            if (mWifiNative.hasAnyStaIfaceForScan()) {
                mInOrder.verify(mWifiVendorHal).removeStaIface(anyString());
            }
            if (mWifiNative.hasAnyApIface()) {
                mInOrder.verify(mWifiVendorHal).removeApIface(anyString());
            }
        }
    }

    /**
     * Verifies failure handling in setup of a client interface.
     */
    @Test
    public void testSetupClientInterfaceFailureInStartHal() throws Exception {
        when(mWifiVendorHal.startVendorHal()).thenReturn(false);
        assertNull(mWifiNative.setupInterfaceForClientInScanMode(
                mIfaceCallback0, TEST_WORKSOURCE, mConcreteClientModeManager));

        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).startVendorHal();
        mInOrder.verify(mWifiMetrics).incrementNumSetupClientInterfaceFailureDueToHal();

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a client interface.
     */
    @Test
    public void testSetupClientInterfaceFailureInStartSupplicant() throws Exception {
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        when(mSupplicantStaIfaceHal.startDaemon()).thenReturn(false);
        executeAndValidateSwitchClientInterfaceToConnectivityMode(false, false, IFACE_NAME_0,
                TEST_WORKSOURCE, true, STA_FAILURE_CODE_START_DAEMON);

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a client interface.
     */
    @Test
    public void testSetupClientInterfaceFailureInHalCreateStaIface() throws Exception {
        when(mWifiVendorHal.createStaIface(any(), any(), eq(mConcreteClientModeManager)))
                .thenReturn(null);
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, true, STA_FAILURE_CODE_CREAT_IFACE);

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a client interface.
     */
    @Test
    public void testSetupClientInterfaceFailureInWificondSetupInterfaceForClientMode()
            throws Exception {
        when(mWificondControl.setupInterfaceForClientMode(any(), any(), any(), any())).thenReturn(
                false);
        assertNull(mWifiNative.setupInterfaceForClientInScanMode(mIfaceCallback0, TEST_WORKSOURCE,
                mConcreteClientModeManager));
        validateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, true, STA_FAILURE_CODE_WIFICOND_SETUP_INTERFACE);

        // Trigger the HAL interface destroyed callback to verify the whole removal sequence.
        mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
        validateOnDestroyedClientInterfaceForScan(false, false, IFACE_NAME_0, mIfaceCallback0,
                null);

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a client interface.
     */
    @Test
    public void testSetupClientInterfaceFailureInSupplicantSetupIface() throws Exception {
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        when(mSupplicantStaIfaceHal.setupIface(any())).thenReturn(false);
        executeAndValidateSwitchClientInterfaceToConnectivityMode(false, false, IFACE_NAME_0,
                TEST_WORKSOURCE, true, STA_FAILURE_CODE_SETUP_INTERFACE);

        // Trigger the HAL interface destroyed callback to verify the whole removal sequence.
        mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
        validateOnDestroyedClientInterfaceForScan(false, false, IFACE_NAME_0, mIfaceCallback0,
                mNetworkObserverCaptor0.getValue());

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a softAp interface.
     */
    @Test
    public void testSetupSoftApInterfaceFailureInStartHal() throws Exception {
        when(mWifiVendorHal.startVendorHal()).thenReturn(false);
        assertNull(mWifiNative.setupInterfaceForSoftApMode(mIfaceCallback0, TEST_WORKSOURCE,
                SoftApConfiguration.BAND_2GHZ, false, mSoftApManager,
                new ArrayList<>()));

        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).startVendorHal();
        mInOrder.verify(mWifiMetrics).incrementNumSetupSoftApInterfaceFailureDueToHal();

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a softAp interface.
     */
    @Test
    public void testSetupSoftApInterfaceFailureInStartHostapd() throws Exception {
        when(mHostapdHal.startDaemon()).thenReturn(false);
        executeAndValidateSetupSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                mIfaceDestroyedListenerCaptor0, mNetworkObserverCaptor0,
                false, true, SOFTAP_FAILURE_CODE_START_DAEMON);
        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a softAp interface.
     */
    @Test
    public void testSetupSoftApInterfaceFailureInHalCreateApIface() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, null, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, false, true, SOFTAP_FAILURE_CODE_CREATE_IFACE);

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a softAp interface.
     */
    @Test
    public void testSetupSoftApInterfaceFailureInHalGetBridgedInstances() throws Exception {
        when(mWifiVendorHal.getBridgedApInstances(any())).thenReturn(null);
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, true, true, SOFTAP_FAILURE_CODE_BRIDGED_AP_INSTANCES);

        // Trigger the HAL interface destroyed callback to verify the whole removal sequence.
        mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
        validateOnDestroyedSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                null);

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies failure handling in setup of a softAp interface.
     */
    @Test
    public void testSetupSoftApInterfaceFailureInWificondSetupInterfaceForSoftapMode()
            throws Exception {
        when(mWificondControl.setupInterfaceForSoftApMode(any())).thenReturn(false);
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, false, true, SOFTAP_FAILURE_CODE_SETUP_INTERFACE);

        // Trigger the HAL interface destroyed callback to verify the whole removal sequence.
        mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
        validateOnDestroyedSoftApInterface(false, false, IFACE_NAME_0, mIfaceCallback0,
                null);

        validateInterfaceTearDown(IFACE_NAME_0);
    }

    /**
     * Verifies the interface state query API.
     */
    @Test
    public void testIsInterfaceUp() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);

        when(mNetdWrapper.isInterfaceUp(IFACE_NAME_0)).thenReturn(true);
        assertTrue(mWifiNative.isInterfaceUp(IFACE_NAME_0));

        when(mNetdWrapper.isInterfaceUp(IFACE_NAME_0)).thenReturn(false);
        assertFalse(mWifiNative.isInterfaceUp(IFACE_NAME_0));

        verify(mNetdWrapper, times(3)).isInterfaceUp(IFACE_NAME_0);
    }

    /**
     * Verifies that the interface name is null when there are no interfaces setup.
     */
    @Test
    public void testGetClientInterfaceNameWithNoInterfacesSetup() throws Exception {
        assertEquals(Set.of(), mWifiNative.getClientInterfaceNames());
    }

    /**
     * Verifies that the interface name is null when there are no client interfaces setup.
     */
    @Test
    public void testGetClientInterfaceNameWithNoClientInterfaceSetup() throws Exception {
        executeAndValidateSetupSoftApInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(), mWifiNative.getClientInterfaceNames());
    }

    /**
     * Verifies that the interface name is not null when there is one client interface setup.
     */
    @Test
    public void testGetClientInterfaceNameWithOneClientInterfaceSetup() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
    }

    /**
     * Verifies that the interface name is not null when there are more than one client interfaces
     * setup.
     */
    @Test
    public void testGetClientInterfaceNameWithMoreThanOneClientInterfaceSetup() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSetupClientInterface(
                true, false, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);
        assertEquals(Set.of(IFACE_NAME_0, IFACE_NAME_1), mWifiNative.getClientInterfaceNames());
    }

    /*
     * Verifies the setup of a client interface and then a SoftAp interface which would
     * destroy the Client interface. This is what would happen on older devices which do not
     * support the vendor HAL.
     */
    @Test
    public void testSetupClientAndSoftApInterfaceCausesClientInterfaceTeardownWithNoVendorHal()
            throws Exception {
        when(mWifiVendorHal.isVendorHalSupported()).thenReturn(false);
        when(mPropertyService.getString(any(), any())).thenReturn(IFACE_NAME_0);

        // First setup a STA interface and verify.
        executeAndValidateSetupClientInterfaceForScan(false, false, IFACE_NAME_0,
                mIfaceCallback0, mIfaceDestroyedListenerCaptor0, mNetworkObserverCaptor0, false, 0);

        // Now setup an AP interface.
        executeAndValidateSetupSoftApInterface(true, false, IFACE_NAME_0,
                mIfaceCallback1, mIfaceDestroyedListenerCaptor1, mNetworkObserverCaptor1, false,
                false, 0);
    }

    /**
     * Verifies the setup of a client interface and then a SoftAp interface which would
     * destroy the Client interface. This is what would happen on older devices which do not
     * support the vendor HAL.
     */
    @Test
    public void testSetupSoftApAndClientInterfaceCausesSoftApInterfaceTeardownWithNoVendorHal()
            throws Exception {
        when(mWifiVendorHal.isVendorHalSupported()).thenReturn(false);
        when(mPropertyService.getString(any(), any())).thenReturn(IFACE_NAME_0);
        // First setup an AP interface and verify.
        executeAndValidateSetupSoftApInterface(false, false, IFACE_NAME_0,
                mIfaceCallback0, mIfaceDestroyedListenerCaptor0, mNetworkObserverCaptor0, false,
                false, 0);

        // Now setup a STA interface.
        executeAndValidateSetupClientInterface(
                false, true, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, false, 0);
    }

    /**
     * Verifies the handling of radio mode change callbacks.
     */
    @Test
    public void testRadioModeChangeCallback() {
        WifiNative.VendorHalRadioModeChangeEventHandler handler =
                mWifiVendorHalRadioModeChangeHandlerCaptor.getValue();

        handler.onMcc(WifiScanner.WIFI_BAND_5_GHZ);
        mInOrder.verify(mWifiMetrics).incrementNumRadioModeChangeToMcc();

        handler.onScc(WifiScanner.WIFI_BAND_24_GHZ);
        mInOrder.verify(mWifiMetrics).incrementNumRadioModeChangeToScc();

        handler.onSbs(WifiScanner.WIFI_BAND_24_GHZ);
        mInOrder.verify(mWifiMetrics).incrementNumRadioModeChangeToSbs();

        handler.onDbs();
        mInOrder.verify(mWifiMetrics).incrementNumRadioModeChangeToDbs();
    }

    /**
     * Verifies the switch of existing client interface in connectivity mode to scan mode.
     */
    @Test
    public void testSwitchClientInterfaceToScanMode() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertTrue(mWifiNative.switchClientInterfaceToScanMode(IFACE_NAME_0, TEST_WORKSOURCE));
        validateSwitchInterfaceToScan(IFACE_NAME_0, TEST_WORKSOURCE);
    }

    /**
     * Verifies that a switch to scan mode when already in scan mode is rejected.
     */
    @Test
    public void testSwitchClientInterfaceToScanModeFailsWhenAlreadyInScanMode() throws Exception {
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertTrue(mWifiNative.switchClientInterfaceToScanMode(IFACE_NAME_0, TEST_WORKSOURCE));
    }

    private void executeAndValidateSwitchClientInterfaceToConnectivityMode(
            boolean hasStaIface, boolean hasApIface, String ifaceName, WorkSource workSource,
            boolean vendorHalSupported, int failureCode) {
        assertEquals(failureCode == 0 ? true : false,
                mWifiNative.switchClientInterfaceToConnectivityMode(ifaceName, workSource));

        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        if (vendorHalSupported) {
            mInOrder.verify(mWifiVendorHal).replaceStaIfaceRequestorWs(ifaceName, workSource);
        }
        if (!hasStaIface) {
            mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
            mInOrder.verify(mSupplicantStaIfaceHal).initialize();
            mInOrder.verify(mSupplicantStaIfaceHal).startDaemon();
            if (failureCode == STA_FAILURE_CODE_START_DAEMON) {
                mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
                mInOrder.verify(mWifiVendorHal).removeStaIface(ifaceName);
                mInOrder.verify(
                        mWifiMetrics).incrementNumSetupClientInterfaceFailureDueToSupplicant();
                return;
            }
            mInOrder.verify(mSupplicantStaIfaceHal).isInitializationComplete();
            mInOrder.verify(mSupplicantStaIfaceHal).registerDeathHandler(any());
            when(mSupplicantStaIfaceHal.isInitializationStarted()).thenReturn(true);
        }
        mInOrder.verify(mSupplicantStaIfaceHal).setupIface(ifaceName);
        if (failureCode == STA_FAILURE_CODE_SETUP_INTERFACE) {
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            mInOrder.verify(mWifiVendorHal).removeStaIface(ifaceName);
            mInOrder.verify(mWifiMetrics).incrementNumSetupClientInterfaceFailureDueToSupplicant();
        } else {
            mInOrder.verify(mSupplicantStaIfaceHal).getAdvancedCapabilities(ifaceName);
            mInOrder.verify(mWifiVendorHal).getSupportedFeatureSet(ifaceName);
            mInOrder.verify(mSupplicantStaIfaceHal).getWpaDriverFeatureSet(ifaceName);
            mInOrder.verify(mWifiVendorHal).getTwtCapabilities(ifaceName);
            mInOrder.verify(mWifiVendorHal).getUsableChannels(anyInt(), anyInt(), anyInt());
        }
    }

    /**
     * Verifies the switch of existing client interface in scan mode to connectivity mode.
     */
    @Test
    public void testSwitchClientInterfaceToConnectivityMode() throws Exception {
        executeAndValidateSetupClientInterfaceForScan(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        executeAndValidateSwitchClientInterfaceToConnectivityMode(false, false, IFACE_NAME_0,
                TEST_WORKSOURCE, true, 0);
    }

    /**
     * Verifies that a switch to connectivity mode when already in connectivity mode is rejected.
     */
    @Test
    public void testSwitchClientInterfaceToConnectivityModeFailsWhenAlreadyInConnectivityMode()
            throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertTrue(mWifiNative.switchClientInterfaceToConnectivityMode(
                IFACE_NAME_0, TEST_WORKSOURCE));
    }

    /**
     * Verifies the setup of two client interfaces.
     */
    @Test
    public void testSetupTwoClientInterfaces() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());

        executeAndValidateSetupClientInterface(
                true, false, IFACE_NAME_1, mIfaceCallback1, mIfaceDestroyedListenerCaptor1,
                mNetworkObserverCaptor1);
        assertEquals(Set.of(IFACE_NAME_0, IFACE_NAME_1), mWifiNative.getClientInterfaceNames());
    }

    @Test
    public void testSetupP2pInterfaceAndTeardownP2p() throws Exception {
        executeAndValidateCreateP2pInterface(false, false, false, P2P_IFACE_NAME, true, 0);
        executeAndValidateTeardownP2pInterface(false, false, false, false, false,
                mActiveP2pIface, true);
    }

    @Test
    public void testSetupP2pInterfaceAndTeardownP2pWhenClientInterfaceExist() throws Exception {
        // Start client interface
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        executeAndValidateCreateP2pInterface(true, false, false, P2P_IFACE_NAME, true, 0);
        assertTrue(mWifiNative.hasAnyP2pIface());
        executeAndValidateTeardownP2pInterface(true, true, true, false, false,
                mActiveP2pIface, true);
        assertFalse(mWifiNative.hasAnyP2pIface());
        verify(mWifiVendorHal, never()).stopVendorHal();
    }

    /**
     * Verifies the setup of a single client interface and teardown by P2P on.
     */
    @Test
    public void testSetupClientInterfaceAndTeardownP2p() throws Exception {
        executeAndValidateCreateP2pInterface(false, false, false, P2P_IFACE_NAME, true, 0);
        // Trigger the P2P interface teardown when STA interface is created.
        // The iface name will remain the same.
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public String answer(InterfaceDestroyedListener destroyedListener, WorkSource ws,
                    ConcreteClientModeManager concreteClientModeManager) {
                mWifiNative.teardownP2pIface(mActiveP2pIface.id);
                return IFACE_NAME_0;
            }
        }).when(mWifiVendorHal).createStaIface(any(), any(), eq(mConcreteClientModeManager));

        assertEquals(IFACE_NAME_0,
                mWifiNative.setupInterfaceForClientInScanMode(mIfaceCallback0, TEST_WORKSOURCE,
                        mConcreteClientModeManager));
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        validateSetupClientInterfaceForScan(
                false, false, true, IFACE_NAME_0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0, true, 0);
        verify(mSupplicantStaIfaceHal, atLeastOnce()).isInitializationStarted();
        verify(mWifiVendorHal, never()).stopVendorHal();
        verify(mWifiP2pNative).stopP2pSupplicantIfNecessary();
    }

    /**
     * Verifies the setup of a single client interface (for scan) and teardown by P2P on.
     */
    @Test
    public void testCreateP2pIfaceAndTeardownClientIface() throws Exception {
        executeAndValidateSetupClientInterface(
                false, false, IFACE_NAME_0, mIfaceCallback0, mIfaceDestroyedListenerCaptor0,
                mNetworkObserverCaptor0);
        assertEquals(Set.of(IFACE_NAME_0), mWifiNative.getClientInterfaceNames());
        // Trigger the STA interface teardown when P2p interface is created.
        // The iface name will remain the same.
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public String answer(
                    HalDeviceManager.InterfaceDestroyedListener p2pInterfaceDestroyedListener,
                    Handler handler, WorkSource requestorWs) {
                mIfaceDestroyedListenerCaptor0.getValue().onDestroyed(IFACE_NAME_0);
                return P2P_IFACE_NAME;
            }
        }).when(mHalDeviceManager).createP2pIface(any(), any(), any());
        mActiveP2pIface = mWifiNative.createP2pIface(mP2pInterfaceDestroyedListener,
                    mP2pEventHandler, TEST_WORKSOURCE);
        assertEquals(P2P_IFACE_NAME, mActiveP2pIface.name);
        // Creation of P2P interface should trigger the STA interface destroy
        verify(mWifiVendorHal, atLeastOnce()).isVendorHalSupported();
        verify(mWifiVendorHal, atLeastOnce()).isVendorHalReady();
        validateOnDestroyedClientInterface(false, false, true,
                IFACE_NAME_0, mIfaceCallback0, mNetworkObserverCaptor0.getValue());
        validateCreateP2pInterface(true, false, false, P2P_IFACE_NAME, true, 0);
        executeAndValidateTeardownP2pInterface(false, false, false, false, false,
                mActiveP2pIface, true);
    }

    private void executeAndValidateSetupClientInterface(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor) throws Exception {
        executeAndValidateSetupClientInterface(hasStaIface, hasApIface, ifaceName, callback,
                destroyedListenerCaptor,
                networkObserverCaptor, true, 0);
    }

    private void executeAndValidateSetupClientInterface(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean vendorHalSupported,
            int failureCode) throws Exception {
        executeAndValidateSetupClientInterface(hasStaIface, hasApIface, false, ifaceName, callback,
                destroyedListenerCaptor,
                networkObserverCaptor, vendorHalSupported, failureCode);
    }

    private void executeAndValidateSetupClientInterface(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean vendorHalSupported,
            int failureCode) throws Exception {
        when(mWifiVendorHal.createStaIface(any(), any(), eq(mConcreteClientModeManager)))
                .thenReturn(ifaceName);
        executeAndValidateSetupClientInterfaceForScan(
                hasStaIface, hasApIface, hasP2pIface, ifaceName, callback, destroyedListenerCaptor,
                networkObserverCaptor, vendorHalSupported, failureCode);
        executeAndValidateSwitchClientInterfaceToConnectivityMode(hasStaIface, hasApIface,
                ifaceName, TEST_WORKSOURCE, vendorHalSupported, failureCode);
    }

    private void executeAndValidateTeardownClientInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            InterfaceDestroyedListener destroyedListener,
            NetdEventObserver networkObserver) throws Exception {
        executeAndValidateTeardownClientInterface(anyOtherStaIface, anyOtherApIface,
                false /* anyOtherP2pIface */, ifaceName, callback, destroyedListener,
                networkObserver);
    }

    private void executeAndValidateTeardownClientInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface, boolean anyOtherP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            InterfaceDestroyedListener destroyedListener,
            NetdEventObserver networkObserver) throws Exception {
        mWifiNative.teardownInterface(ifaceName);

        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).removeStaIface(ifaceName);

        // Now trigger the HalDeviceManager destroy callback to initiate the rest of the teardown.
        destroyedListener.onDestroyed(ifaceName);

        validateOnDestroyedClientInterface(
                anyOtherStaIface, anyOtherApIface, anyOtherP2pIface,
                ifaceName, callback, networkObserver);
    }

    private void validateOnDestroyedClientInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            NetdEventObserver networkObserver) throws Exception {
        validateOnDestroyedClientInterface(
                anyOtherStaIface, anyOtherApIface, false /* anyOtherP2pIface */,
                ifaceName, callback, networkObserver);
    }

    private void validateOnDestroyedClientInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface, boolean anyOtherP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            NetdEventObserver networkObserver) throws Exception {
        mInOrder.verify(mWifiMonitor).stopMonitoring(ifaceName);
        if (networkObserver != null) {
            mInOrder.verify(mNetdWrapper).unregisterObserver(networkObserver);
        }
        mInOrder.verify(mSupplicantStaIfaceHal).teardownIface(ifaceName);
        mInOrder.verify(mWificondControl).tearDownClientInterface(ifaceName);

        if (!anyOtherStaIface) {
            mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
            mInOrder.verify(mSupplicantStaIfaceHal).deregisterDeathHandler();
            if (!anyOtherP2pIface) {
                mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
                mInOrder.verify(mSupplicantStaIfaceHal).terminate();
            }
            when(mSupplicantStaIfaceHal.isInitializationStarted()).thenReturn(false);
        }
        if (!anyOtherStaIface && !anyOtherApIface && !anyOtherP2pIface) {
            mInOrder.verify(mWificondControl).tearDownInterfaces();
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            mInOrder.verify(mWifiVendorHal).stopVendorHal();
        }
        mInOrder.verify(mWifiVendorHal).isVendorHalReady();
        mInOrder.verify(callback).onDestroyed(ifaceName);
    }

    private void executeAndValidateSetupClientInterfaceForScan(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor) throws Exception {
        executeAndValidateSetupClientInterfaceForScan(hasStaIface, hasApIface, ifaceName, callback,
                destroyedListenerCaptor, networkObserverCaptor, true, 0);
    }

    private void executeAndValidateSetupClientInterfaceForScan(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor,
            boolean vendorHalSupported, int failureCode) throws Exception {
        executeAndValidateSetupClientInterfaceForScan(hasStaIface, hasApIface,
                false /* hasP2pIface */, ifaceName, callback,
                destroyedListenerCaptor, networkObserverCaptor, vendorHalSupported, failureCode);
    }

    private void executeAndValidateSetupClientInterfaceForScan(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor,
            boolean vendorHalSupported, int failureCode) throws Exception {
        if (failureCode != STA_FAILURE_CODE_CREAT_IFACE) {
            when(mWifiVendorHal.createStaIface(any(), any(), eq(mConcreteClientModeManager)))
                    .thenReturn(ifaceName);
        }
        assertEquals(failureCode == 0 ? ifaceName : null,
                mWifiNative.setupInterfaceForClientInScanMode(callback, TEST_WORKSOURCE,
                        mConcreteClientModeManager));

        validateSetupClientInterfaceForScan(
                hasStaIface, hasApIface, hasP2pIface, ifaceName, destroyedListenerCaptor,
                networkObserverCaptor, vendorHalSupported, failureCode);
    }

    private void validateStartHal(boolean hasAnyIface, boolean vendorHalSupported) {
        verify(mWifiVendorHal, atLeastOnce()).isVendorHalSupported();
        if (!hasAnyIface) {
            if (vendorHalSupported) {
                mInOrder.verify(mWifiVendorHal).startVendorHal();
                if (SdkLevel.isAtLeastS()) {
                    mInOrder.verify(mWifiVendorHal).setCoexUnsafeChannels(any(), anyInt());
                }
            }
            if (SdkLevel.isAtLeastS()) {
                mInOrder.verify(mWificondControl).registerCountryCodeChangedListener(any(),
                        any());
            }
        }
    }

    private void validateSetupClientInterfaceForScan(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean vendorHalSupported,
            int failureCode) throws Exception {
        validateSetupClientInterfaceForScan(hasStaIface, hasApIface, false /* hasP2pIfacd */,
                ifaceName, destroyedListenerCaptor, networkObserverCaptor, vendorHalSupported,
                failureCode);
    }

    private void validateSetupClientInterfaceForScan(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean vendorHalSupported,
            int failureCode) throws Exception {
        validateStartHal(hasStaIface || hasApIface || hasP2pIface, vendorHalSupported);
        if (vendorHalSupported) {
            mInOrder.verify(mWifiVendorHal).createStaIface(
                    destroyedListenerCaptor.capture(), eq(TEST_WORKSOURCE),
                    eq(mConcreteClientModeManager));
            if (failureCode == STA_FAILURE_CODE_CREAT_IFACE) {
                verify(mWifiMetrics).incrementNumSetupClientInterfaceFailureDueToHal();
                return;
            }
        } else {
            if (hasApIface) {
                // Creation of STA interface should trigger the AP interface destroy.
                mInOrder.verify(mNetdWrapper).unregisterObserver(
                        mNetworkObserverCaptor0.getValue());
                mInOrder.verify(mHostapdHal).removeAccessPoint(ifaceName);
                mInOrder.verify(mWificondControl).tearDownSoftApInterface(ifaceName);
                mInOrder.verify(mHostapdHal).deregisterDeathHandler();
                mInOrder.verify(mHostapdHal).terminate();
                mInOrder.verify(mWifiVendorHal).isVendorHalReady();
                mInOrder.verify(mIfaceCallback0).onDestroyed(ifaceName);
            }
        }
        mInOrder.verify(mWificondControl).setupInterfaceForClientMode(eq(ifaceName), any(), any(),
                any());
        if (failureCode == STA_FAILURE_CODE_WIFICOND_SETUP_INTERFACE) {
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            verify(mWifiVendorHal).removeStaIface(ifaceName);
            verify(mWifiMetrics).incrementNumSetupClientInterfaceFailureDueToWificond();
            return;
        }
        mInOrder.verify(mNetdWrapper, atLeastOnce())
                .registerObserver(networkObserverCaptor.capture());
        mInOrder.verify(mWifiMonitor).startMonitoring(ifaceName);
        mInOrder.verify(mNetdWrapper).isInterfaceUp(ifaceName);
        mInOrder.verify(mWifiVendorHal).enableLinkLayerStats(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getAdvancedCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getSupportedFeatureSet(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getWpaDriverFeatureSet(ifaceName);
        mInOrder.verify(mWifiVendorHal).getTwtCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getUsableChannels(anyInt(), anyInt(), anyInt());
        mInOrder.verify(mWifiVendorHal).enableStaChannelForPeerNetwork(anyBoolean(), anyBoolean());
    }

    private void executeAndValidateTeardownClientInterfaceForScan(
            boolean anyOtherStaIface, boolean anyOtherApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            InterfaceDestroyedListener destroyedListener,
            NetdEventObserver networkObserver) throws Exception {
        executeAndValidateTeardownClientInterfaceForScan(anyOtherStaIface, anyOtherApIface,
                false /* anyOtherP2pIface */, ifaceName, callback, destroyedListener,
                networkObserver);
    }

    private void executeAndValidateTeardownClientInterfaceForScan(
            boolean anyOtherStaIface, boolean anyOtherApIface, boolean anyOtherP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            InterfaceDestroyedListener destroyedListener,
            NetdEventObserver networkObserver) throws Exception {
        mWifiNative.teardownInterface(ifaceName);

        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).removeStaIface(ifaceName);

        // Now trigger the HalDeviceManager destroy callback to initiate the rest of the teardown.
        destroyedListener.onDestroyed(ifaceName);

        validateOnDestroyedClientInterfaceForScan(
                anyOtherStaIface, anyOtherApIface, anyOtherP2pIface,
                ifaceName, callback, networkObserver);
    }

    private void validateOnDestroyedClientInterfaceForScan(
            boolean anyOtherStaIface, boolean anyOtherApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            NetdEventObserver networkObserver) throws Exception {
        validateOnDestroyedClientInterfaceForScan(
                anyOtherStaIface, anyOtherApIface, false /* anyOtherP2pIface */,
                ifaceName, callback, networkObserver);
    }

    private void validateOnDestroyedClientInterfaceForScan(
            boolean anyOtherStaIface, boolean anyOtherApIface, boolean anyOtherP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            NetdEventObserver networkObserver) throws Exception {
        mInOrder.verify(mWifiMonitor).stopMonitoring(ifaceName);
        if (networkObserver != null) {
            mInOrder.verify(mNetdWrapper).unregisterObserver(networkObserver);
        }
        mInOrder.verify(mWificondControl).tearDownClientInterface(ifaceName);

        if (!anyOtherStaIface && !anyOtherApIface && !anyOtherP2pIface) {
            mInOrder.verify(mWificondControl).tearDownInterfaces();
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            mInOrder.verify(mWifiVendorHal).stopVendorHal();
        }
        mInOrder.verify(mWifiVendorHal).isVendorHalReady();
        mInOrder.verify(callback).onDestroyed(ifaceName);
    }

    private void executeAndValidateSetupSoftApInterface(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor) throws Exception {
        executeAndValidateSetupSoftApInterface(hasStaIface, hasApIface, ifaceName,
                callback, destroyedListenerCaptor, networkObserverCaptor, false, true, 0);
    }

    private void executeAndValidateSetupSoftApInterface(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean isBridged,
            boolean vendorHalSupported, int failureCode) throws Exception {
        executeAndValidateSetupSoftApInterface(hasStaIface, hasApIface, false /* hasP2pIface */,
                ifaceName, callback, destroyedListenerCaptor, networkObserverCaptor,
                isBridged, vendorHalSupported, failureCode);
    }

    private void executeAndValidateSetupSoftApInterface(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean isBridged,
            boolean vendorHalSupported, int failureCode) throws Exception {
        when(mWifiVendorHal.createApIface(any(), any(), anyInt(), eq(isBridged), any(), anyList()))
                .thenReturn(ifaceName);
        assertEquals(failureCode == 0 ? ifaceName : null, mWifiNative.setupInterfaceForSoftApMode(
                callback, TEST_WORKSOURCE, SoftApConfiguration.BAND_2GHZ, isBridged,
                mSoftApManager, new ArrayList<>()));

        validateSetupSoftApInterface(
                hasStaIface, hasApIface, ifaceName, destroyedListenerCaptor,
                networkObserverCaptor, isBridged, vendorHalSupported, failureCode);
    }

    private void validateSetupSoftApInterface(
            boolean hasStaIface, boolean hasApIface,
            String ifaceName, ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean isBridged,
            boolean vendorHalSupported, int failureCode) throws Exception {
        validateSetupSoftApInterface(hasStaIface, hasApIface, false /* hasP2pIface */,
                ifaceName, destroyedListenerCaptor,
                networkObserverCaptor, isBridged, vendorHalSupported, failureCode);
    }

    private void validateSetupSoftApInterface(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, ArgumentCaptor<InterfaceDestroyedListener> destroyedListenerCaptor,
            ArgumentCaptor<NetdEventObserver> networkObserverCaptor, boolean isBridged,
            boolean vendorHalSupported, int failureCode) throws Exception {
        validateStartHal(hasStaIface || hasApIface || hasP2pIface, vendorHalSupported);
        if (!hasApIface) {
            mInOrder.verify(mHostapdHal).isInitializationStarted();
            mInOrder.verify(mHostapdHal).initialize();
            mInOrder.verify(mHostapdHal).startDaemon();
            if (failureCode == SOFTAP_FAILURE_CODE_START_DAEMON) {
                mInOrder.verify(mWifiMetrics).incrementNumSetupSoftApInterfaceFailureDueToHostapd();
                return;
            }
            mInOrder.verify(mHostapdHal).isInitializationComplete();
            mInOrder.verify(mHostapdHal).registerDeathHandler(any());
        }
        if (vendorHalSupported) {
            mInOrder.verify(mWifiVendorHal).createApIface(
                    destroyedListenerCaptor.capture(), eq(TEST_WORKSOURCE),
                    eq(SoftApConfiguration.BAND_2GHZ), eq(isBridged), eq(mSoftApManager),
                    anyList());
            if (failureCode == SOFTAP_FAILURE_CODE_CREATE_IFACE) {
                mInOrder.verify(mWifiMetrics).incrementNumSetupSoftApInterfaceFailureDueToHal();
                return;
            }
        } else {
            if (hasStaIface) {
                // Creation of AP interface should trigger the STA interface destroy
                mInOrder.verify(mWifiMonitor).stopMonitoring(ifaceName);
                mInOrder.verify(mNetdWrapper).unregisterObserver(
                        mNetworkObserverCaptor0.getValue());
                if (mWifiNative.hasAnyStaIfaceForConnectivity()) {
                    mInOrder.verify(mSupplicantStaIfaceHal).teardownIface(ifaceName);
                }
                mInOrder.verify(mWificondControl).tearDownClientInterface(ifaceName);
                if (mWifiNative.hasAnyStaIfaceForConnectivity()) {
                    mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
                    mInOrder.verify(mSupplicantStaIfaceHal).deregisterDeathHandler();
                    mInOrder.verify(mSupplicantStaIfaceHal).isInitializationStarted();
                    mInOrder.verify(mSupplicantStaIfaceHal).terminate();
                    when(mSupplicantStaIfaceHal.isInitializationStarted()).thenReturn(false);
                }
                mInOrder.verify(mWifiVendorHal).isVendorHalReady();
                mInOrder.verify(mIfaceCallback0).onDestroyed(ifaceName);
            }
        }
        if (isBridged) {
            mInOrder.verify(mWifiVendorHal).getBridgedApInstances(eq(ifaceName));
            if (failureCode == SOFTAP_FAILURE_CODE_BRIDGED_AP_INSTANCES) {
                mInOrder.verify(mWifiVendorHal).removeApIface(ifaceName);
                mInOrder.verify(mWifiMetrics).incrementNumSetupSoftApInterfaceFailureDueToHal();
                return;
            }
        }
        mInOrder.verify(mWificondControl).setupInterfaceForSoftApMode(ifaceName);
        if (failureCode == SOFTAP_FAILURE_CODE_SETUP_INTERFACE) {
            mInOrder.verify(mWifiVendorHal).removeApIface(ifaceName);
            mInOrder.verify(mWifiMetrics).incrementNumSetupSoftApInterfaceFailureDueToWificond();
            return;
        }
        // Now continue with rest of AP interface setup.
        mInOrder.verify(mNetdWrapper).registerObserver(networkObserverCaptor.capture());
        mInOrder.verify(mNetdWrapper).isInterfaceUp(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getAdvancedCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getSupportedFeatureSet(ifaceName);
        mInOrder.verify(mSupplicantStaIfaceHal).getWpaDriverFeatureSet(ifaceName);
        mInOrder.verify(mWifiVendorHal).getTwtCapabilities(ifaceName);
        mInOrder.verify(mWifiVendorHal).getUsableChannels(anyInt(), anyInt(), anyInt());
    }

    private void executeAndValidateTeardownSoftApInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            InterfaceDestroyedListener destroyedListener,
            NetdEventObserver networkObserver) throws Exception {
        mWifiNative.teardownInterface(ifaceName);

        mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
        mInOrder.verify(mWifiVendorHal).removeApIface(ifaceName);

        // Now trigger the HalDeviceManager destroy callback to initiate the rest of the teardown.
        destroyedListener.onDestroyed(ifaceName);

        validateOnDestroyedSoftApInterface(
                anyOtherStaIface, anyOtherApIface, false /* anyOtherP2pIface */,
                ifaceName, callback, networkObserver);
    }

    private void validateOnDestroyedSoftApInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            NetdEventObserver networkObserver) throws Exception {
        validateOnDestroyedSoftApInterface(
                anyOtherStaIface, anyOtherApIface, false /* anyOtherP2pIface */,
                ifaceName, callback, networkObserver);
    }

    private void validateOnDestroyedSoftApInterface(
            boolean anyOtherStaIface, boolean anyOtherApIface, boolean anyOtherP2pIface,
            String ifaceName, @Mock WifiNative.InterfaceCallback callback,
            NetdEventObserver networkObserver) throws Exception {
        if (networkObserver != null) {
            mInOrder.verify(mNetdWrapper).unregisterObserver(networkObserver);
        }
        mInOrder.verify(mHostapdHal).removeAccessPoint(ifaceName);
        mInOrder.verify(mWificondControl).tearDownSoftApInterface(ifaceName);

        if (!anyOtherApIface) {
            mInOrder.verify(mHostapdHal).deregisterDeathHandler();
            mInOrder.verify(mHostapdHal).terminate();
        }
        if (!anyOtherStaIface && !anyOtherApIface && !anyOtherP2pIface) {
            mInOrder.verify(mWificondControl).tearDownInterfaces();
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            mInOrder.verify(mWifiVendorHal).stopVendorHal();
        }
        mInOrder.verify(mWifiVendorHal).isVendorHalReady();
        mInOrder.verify(callback).onDestroyed(ifaceName);
    }

    private void executeAndValidateInterfaceStateChange(
            String ifaceName, boolean up, NetdEventObserver networkObserver) throws Exception {
        when(mNetdWrapper.isInterfaceUp(ifaceName)).thenReturn(up);
        networkObserver.interfaceLinkStateChanged(ifaceName, up);
        mLooper.dispatchAll();
        mInOrder.verify(mNetdWrapper).isInterfaceUp(ifaceName);
    }

    private void executeAndValidateInterfaceAdded(
            String ifaceName, List<NetdEventObserver> networkObservers) throws Exception {
        networkObservers.forEach(observer -> observer.interfaceAdded(ifaceName));
        mLooper.dispatchAll();
        mInOrder.verify(mIfaceEventCallback0).onInterfaceAdded(ifaceName);
    }

    private void executeAndValidateCreateP2pInterface(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, boolean vendorHalSupported, int failureCode) throws Exception {
        if (failureCode != P2P_FAILURE_CODE_CREATE_INTERFACE) {
            if (vendorHalSupported) {
                when(mHalDeviceManager.createP2pIface(any(), any(), any()))
                        .thenReturn(ifaceName);
            } else {
                when(mPropertyService.getString(P2P_INTERFACE_PROPERTY, P2P_IFACE_NAME))
                        .thenReturn(ifaceName);
            }
        }
        mActiveP2pIface = mWifiNative.createP2pIface(mP2pInterfaceDestroyedListener,
                    mP2pEventHandler, TEST_WORKSOURCE);
        if (failureCode == 0) {
            assertNotNull(mActiveP2pIface);
            assertEquals(mActiveP2pIface.name, ifaceName);
        } else {
            assertNull(mActiveP2pIface);
        }
        validateCreateP2pInterface(hasStaIface, hasApIface, hasP2pIface,
                ifaceName, vendorHalSupported, failureCode);
    }

    private void validateCreateP2pInterface(
            boolean hasStaIface, boolean hasApIface, boolean hasP2pIface,
            String ifaceName, boolean vendorHalSupported, int failureCode) throws Exception {
        validateStartHal(hasStaIface || hasApIface || hasP2pIface, vendorHalSupported);
        if (vendorHalSupported) {
            verify(mHalDeviceManager).createP2pIface(eq(mP2pInterfaceDestroyedListener),
                    eq(mP2pEventHandler), eq(TEST_WORKSOURCE));
            if (failureCode == P2P_FAILURE_CODE_CREATE_INTERFACE) {
                verify(mWifiMetrics).incrementNumSetupP2pInterfaceFailureDueToHal();
            }
        } else {
            verify(mPropertyService).getString(eq(P2P_INTERFACE_PROPERTY), eq(P2P_IFACE_NAME));
        }
    }

    private void executeAndValidateTeardownP2pInterface(
            boolean anyOtherStaIface, boolean anyOtherConnectivityStaIface,
            boolean isSupplicantStartedBefore, boolean anyOtherApIface,
            boolean anyOtherP2pIface, WifiNative.Iface iface, boolean vendorHalSupported)
            throws Exception {
        mWifiNative.teardownP2pIface(iface.id);

        validateOnDestroyedP2pInterface(anyOtherStaIface, anyOtherConnectivityStaIface,
                isSupplicantStartedBefore, anyOtherApIface,
                anyOtherP2pIface, vendorHalSupported);
    }

    private void validateOnDestroyedP2pInterface(
            boolean anyOtherStaIface, boolean anyOtherConnectivityStaIface,
            boolean isSupplicantStartedBefore, boolean anyOtherApIface,
            boolean anyOtherP2pIface, boolean vendorHalSupported) throws Exception {
        if (vendorHalSupported && !anyOtherStaIface && !anyOtherApIface && !anyOtherP2pIface) {
            mInOrder.verify(mWificondControl).tearDownInterfaces();
            mInOrder.verify(mWifiVendorHal).isVendorHalSupported();
            mInOrder.verify(mWifiVendorHal).stopVendorHal();
        }
        if (!anyOtherConnectivityStaIface) {
            mInOrder.verify(mSupplicantStaIfaceHal, atLeastOnce()).isInitializationStarted();
            if (isSupplicantStartedBefore) {
                mInOrder.verify(mSupplicantStaIfaceHal).deregisterDeathHandler();
                mInOrder.verify(mSupplicantStaIfaceHal).terminate();
            } else {
                if (!anyOtherP2pIface) {
                    mInOrder.verify(mWifiP2pNative).stopP2pSupplicantIfNecessary();
                }
            }
        }
    }
}
