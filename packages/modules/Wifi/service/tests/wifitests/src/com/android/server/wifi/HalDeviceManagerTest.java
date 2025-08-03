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

import static com.android.server.wifi.HalDeviceManager.CHIP_CAPABILITY_ANY;
import static com.android.server.wifi.HalDeviceManager.HAL_IFACE_MAP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_NAN;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_P2P;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_STA;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.test.MockAnswerUtil;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.HalDeviceManager.InterfaceDestroyedListener;
import com.android.server.wifi.hal.WifiApIface;
import com.android.server.wifi.hal.WifiChip;
import com.android.server.wifi.hal.WifiHal;
import com.android.server.wifi.hal.WifiHal.WifiInterface;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiP2pIface;
import com.android.server.wifi.hal.WifiRttController;
import com.android.server.wifi.hal.WifiStaIface;
import com.android.server.wifi.util.WorkSourceHelper;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import com.google.common.collect.ImmutableList;

import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit test harness for HalDeviceManagerTest.
 */
@SmallTest
public class HalDeviceManagerTest extends WifiBaseTest {
    private static final WorkSource TEST_WORKSOURCE_0 = new WorkSource(450, "com.test.0");
    private static final WorkSource TEST_WORKSOURCE_1 = new WorkSource(451, "com.test.1");
    private static final WorkSource TEST_WORKSOURCE_2 = new WorkSource(452, "com.test.2");

    private HalDeviceManager mDut;
    @Mock WifiHal mWifiMock;
    @Mock WifiRttController mRttControllerMock;
    @Mock HalDeviceManager.ManagerStatusListener mManagerStatusListenerMock;
    @Mock private WifiContext mContext;
    @Mock private Resources mResources;
    @Mock private Clock mClock;
    @Mock private WifiInjector mWifiInjector;
    @Mock private ConcreteClientModeManager mConcreteClientModeManager;
    @Mock private SoftApManager mSoftApManager;
    @Mock private WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock private InterfaceConflictManager mInterfaceConflictManager;
    @Mock private WorkSourceHelper mWorkSourceHelper0;
    @Mock private WorkSourceHelper mWorkSourceHelper1;
    @Mock private WorkSourceHelper mWorkSourceHelper2;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private FeatureFlags mFeatureFlags;
    private TestLooper mTestLooper;
    private Handler mHandler;
    private ArgumentCaptor<WifiHal.Callback> mWifiEventCallbackCaptor = ArgumentCaptor.forClass(
            WifiHal.Callback.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();
    private boolean mIsBridgedSoftApSupported = false;
    private boolean mIsStaWithBridgedSoftApConcurrencySupported = false;
    private boolean mWifiUserApprovalRequiredForD2dInterfacePriority = false;
    private boolean mWaitForDestroyedListeners = false;

    private class HalDeviceManagerSpy extends HalDeviceManager {
        HalDeviceManagerSpy() {
            super(mContext, mClock, mWifiInjector, mHandler);
            enableVerboseLogging(true);
        }

        @Override
        protected WifiHal getWifiHalMockable(WifiContext context, WifiInjector wifiInjector) {
            return mWifiMock;
        }

        @Override
        protected boolean isWaitForDestroyedListenersMockable() {
            return mWaitForDestroyedListeners;
        }
    }

    @Before
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mHandler = new Handler(mTestLooper.getLooper());

        when(mWifiInjector.getInterfaceConflictManager()).thenReturn(mInterfaceConflictManager);
        when(mInterfaceConflictManager.needsUserApprovalToDelete(anyInt(), any(), anyInt(), any()))
                .thenReturn(false);
        when(mWifiInjector.makeWsHelper(TEST_WORKSOURCE_0)).thenReturn(mWorkSourceHelper0);
        when(mWifiInjector.makeWsHelper(TEST_WORKSOURCE_1)).thenReturn(mWorkSourceHelper1);
        when(mWifiInjector.makeWsHelper(TEST_WORKSOURCE_2)).thenReturn(mWorkSourceHelper2);
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        when(mWorkSourceHelper0.getWorkSource()).thenReturn(TEST_WORKSOURCE_0);
        when(mWorkSourceHelper1.getWorkSource()).thenReturn(TEST_WORKSOURCE_1);
        when(mWorkSourceHelper2.getWorkSource()).thenReturn(TEST_WORKSOURCE_2);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.singleWifiThread()).thenReturn(true);
        when(mConcreteClientModeManager.getRole()).thenReturn(
                ClientModeManager.ROLE_CLIENT_PRIMARY);

        when(mWifiMock.registerEventCallback(any(WifiHal.Callback.class))).thenReturn(true);
        when(mWifiMock.start()).thenReturn(WifiHal.WIFI_STATUS_SUCCESS);
        when(mWifiMock.stop()).thenReturn(true);
        when(mWifiMock.isStarted()).thenReturn(true);
        when(mWifiMock.isInitializationComplete()).thenReturn(true);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(R.bool.config_wifiBridgedSoftApSupported))
                .thenReturn(mIsBridgedSoftApSupported);
        when(mResources.getBoolean(R.bool.config_wifiStaWithBridgedSoftApConcurrencySupported))
                .thenReturn(mIsStaWithBridgedSoftApConcurrencySupported);
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority))
                .thenReturn(mWifiUserApprovalRequiredForD2dInterfacePriority);
        when(mResources.getBoolean(R.bool.config_wifiWaitForDestroyedListeners))
                .thenReturn(mWaitForDestroyedListeners);
        when(mResources.getInteger(R.integer.config_disconnectedP2pIfaceLowPriorityTimeoutMs))
                .thenReturn(-1);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);

        mDut = new HalDeviceManagerSpy();
        mDut.handleBootCompleted();
    }

    /**
     * Print out the dump of the device manager after each test. Not used in test validation
     * (internal state) - but can help in debugging failed tests.
     */
    @After
    public void after() throws Exception {
        dumpDut("after: ");
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Chip Independent Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Test basic startup flow:
     * - Start Wi-Fi -> onStart
     * - Stop Wi-Fi -> onStop
     */
    @Test
    public void testStartStopFlow() throws Exception {
        TestChipV5 chipMock = new HalDeviceManagerTest.TestChipV5();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: onStop called
        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate that multiple callback registrations are called and that duplicate ones are
     * only called once.
     */
    @Test
    public void testMultipleCallbackRegistrations() throws Exception {
        TestChipV5 chipMock = new HalDeviceManagerTest.TestChipV5();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, mManagerStatusListenerMock);

        // register another 2 callbacks - one of them twice
        HalDeviceManager.ManagerStatusListener callback1 = mock(
                HalDeviceManager.ManagerStatusListener.class);
        HalDeviceManager.ManagerStatusListener callback2 = mock(
                HalDeviceManager.ManagerStatusListener.class);
        mDut.registerStatusListener(callback2, mHandler);
        mDut.registerStatusListener(callback1, mHandler);
        mDut.registerStatusListener(callback2, mHandler);

        // startup
        executeAndValidateStartupSequence();

        // verify
        verify(callback1).onStatusChanged();
        verify(callback2).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, callback1, callback2);
    }

    /**
     * Validate IWifi death listener and registration flow.
     */
    @Test
    public void testWifiDeathAndRegistration() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // act: IWifi service death
        ArgumentCaptor<WifiHal.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(WifiHal.DeathRecipient.class);
        verify(mWifiMock, atLeastOnce()).initialize(deathRecipientCaptor.capture());
        deathRecipientCaptor.getValue().onDeath();
        mTestLooper.dispatchAll();

        // verify: getting onStop
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // act: start
        collector.checkThat(mDut.start(), equalTo(true));
        mWifiEventCallbackCaptor.getValue().onStart();
        mTestLooper.dispatchAll();

        // verify: service and callback calls
        mInOrder.verify(mWifiMock).start();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate IWifi onFailure causes notification
     */
    @Test
    public void testWifiFail() throws Exception {
        HalDeviceManagerTest.TestChipV5 chipMock = new HalDeviceManagerTest.TestChipV5();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // act: IWifi failure
        mWifiEventCallbackCaptor.getValue().onFailure(WifiHal.WIFI_STATUS_ERROR_UNKNOWN);
        mTestLooper.dispatchAll();

        // verify: getting onStop
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // act: start again
        collector.checkThat(mDut.start(), equalTo(true));
        mWifiEventCallbackCaptor.getValue().onStart();
        mTestLooper.dispatchAll();

        // verify: service and callback calls
        mInOrder.verify(mWifiMock).start();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validates that when (for some reason) the cache is out-of-sync with the actual chip status
     * then Wi-Fi is shut-down.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testCacheMismatchError() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Request STA
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Request NAN
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("NAN can't be created", nanIface, IsNull.notNullValue());

        // fiddle with the "chip" by removing the STA
        chipMock.interfaceNames.get(WifiChip.IFACE_TYPE_STA).remove("wlan0");

        // Now try to request another NAN.
        WifiNanIface nanIface2 =
                mDut.createNanIface(nanDestroyedListener, mHandler, TEST_WORKSOURCE_0);
        collector.checkThat("NAN can't be created", nanIface2, IsNull.nullValue());
        mTestLooper.dispatchAll();

        // verify that Wi-Fi is shut-down: should also get all onDestroyed messages that are
        // registered (even if they seem out-of-sync to chip)
        verify(mWifiMock).stop();
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();
        verify(staDestroyedListener).onDestroyed(getName(staIface));
        verify(nanDestroyedListener).onDestroyed(getName(nanIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                nanDestroyedListener);
    }

    /**
     * Validate that when no chip info is found an empty list is returned.
     */
    @Test
    public void testGetSupportedIfaceTypesError() throws Exception {
        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        assertEquals(0, results.size());
    }

    /**
     * Test start HAL can retry upon failure.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testStartHalRetryUponNotAvailableFailure() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start())
                .thenReturn(WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE)
                .thenReturn(WifiHal.WIFI_STATUS_SUCCESS);

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence(2, true);
    }

    /**
     * Test start HAL fails after multiple retry failures.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testStartHalRetryFailUponMultipleNotAvailableFailures() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start()).thenReturn(WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE);

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip);
        executeAndValidateStartupSequence(mDut.START_HAL_RETRY_TIMES + 1, false);
    }

    /**
     * Test start HAL fails after multiple retry failures.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testStartHalRetryFailUponTrueFailure() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start()).thenReturn(WifiHal.WIFI_STATUS_ERROR_UNKNOWN);

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip);
        executeAndValidateStartupSequence(1, false);
    }

    /**
     * Validate RTT configuration when the callback is registered first and the chip is
     * configured later - i.e. RTT isn't available immediately.
     */
    @Test
    public void testAndTriggerRttLifecycleCallbacksRegBeforeChipConfig() throws Exception {
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);

        InOrder io = inOrder(cb);

        // initialize a test chip (V1 is fine since we're not testing any specifics of
        // concurrency in this test).
        ChipMockBase chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // register initial cb: don't expect RTT since chip isn't configured
        mDut.registerRttControllerLifecycleCallback(cb, mHandler);
        mTestLooper.dispatchAll();
        io.verify(cb, times(0)).onNewRttController(any());

        // create a STA - that will get the chip configured and get us an RTT controller
        validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        verify(chipMock.chip).createRttController();
        io.verify(cb).onNewRttController(any(WifiRttController.class));

        verifyNoMoreInteractions(cb);
    }

    /**
     * Validate the RTT Controller lifecycle using a multi-mode chip (i.e. a chip which can
     * switch modes, during which RTT is destroyed).
     *
     * 1. Validate that an RTT is created as soon as the callback is registered - if the chip
     * is already configured (i.e. it is possible to create the RTT controller).
     *
     * 2. Validate that only the registered callback is triggered, not previously registered ones
     * and not duplicate ones.
     *
     * 3. Validate that onDestroy callbacks are triggered on mode change.
     */
    @Test
    public void testAndTriggerRttLifecycleCallbacksMultiModeChip() throws Exception {
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb1 = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb2 = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InOrder io1 = inOrder(cb1);
        InOrder io2 = inOrder(cb2);

        // initialize a test chip (V1 is a must since we're testing a multi-mode chip) & create a
        // STA (which will configure the chip).
        ChipMockBase chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        mInOrder.verify(chipMock.chip, times(0)).createRttController();

        // register initial cb - expect the cb right away
        mDut.registerRttControllerLifecycleCallback(cb1, mHandler);
        mTestLooper.dispatchAll();
        verify(chipMock.chip).createRttController();
        io1.verify(cb1).onNewRttController(any(WifiRttController.class));

        // register a second callback and the first one again
        mDut.registerRttControllerLifecycleCallback(cb2, mHandler);
        mDut.registerRttControllerLifecycleCallback(cb1, mHandler);
        mTestLooper.dispatchAll();
        io2.verify(cb2).onNewRttController(any(WifiRttController.class));
        verify(chipMock.chip, times(1)).createRttController();

        // change to AP mode (which for TestChipV1 doesn't allow RTT): trigger onDestroyed for all
        when(mRttControllerMock.validate()).thenReturn(false);
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP,
                "wlan0",
                TestChipV1.AP_CHIP_MODE_ID,
                new WifiInterface[]{staIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(staIface), staDestroyedListener)
        );
        mTestLooper.dispatchAll();
        verify(chipMock.chip, times(2)).createRttController(); // but returns a null!
        io1.verify(cb1).onRttControllerDestroyed();
        io2.verify(cb2).onRttControllerDestroyed();

        // change back to STA mode (which for TestChipV1 will re-allow RTT): trigger onNew for all
        validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.AP_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                new WifiInterface[]{apIface}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(apIface), apDestroyedListener)
        );
        mTestLooper.dispatchAll();
        verify(chipMock.chip, times(3)).createRttController();
        io1.verify(cb1).onNewRttController(any(WifiRttController.class));
        io2.verify(cb2).onNewRttController(any(WifiRttController.class));

        verifyNoMoreInteractions(cb1, cb2);
    }

    /**
     * Validate the RTT Controller lifecycle using a single-mode chip. Specifically validate
     * that RTT isn't impacted during STA -> AP change.
     */
    @Test
    public void testAndTriggerRttLifecycleCallbacksSingleModeChip() throws Exception {
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);

        InOrder io = inOrder(cb);

        // initialize a test chip (V2 is a must since we need a single mode chip)
        // & create a STA (which will configure the chip).
        ChipMockBase chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();
        WifiInterface sta = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        mInOrder.verify(chipMock.chip, times(0)).createRttController();

        // register initial cb - expect the cb right away
        mDut.registerRttControllerLifecycleCallback(cb, mHandler);
        mTestLooper.dispatchAll();
        verify(chipMock.chip).createRttController();
        io.verify(cb).onNewRttController(any(WifiRttController.class));

        // create an AP: no mode change for TestChipV2 -> expect no impact on RTT
        validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        mTestLooper.dispatchAll();

        when(mRttControllerMock.validate()).thenReturn(false);
        chipMock.chipModeIdValidForRtt = -1;
        mDut.removeIface(sta);
        mTestLooper.dispatchAll();
        verify(chipMock.chip, times(2)).createRttController();
        io.verify(cb).onRttControllerDestroyed();

        verifyNoMoreInteractions(cb);
    }

    /**
     * Validate a flow sequence for test chip 1:
     * - create P2P (privileged app)
     * - create NAN (system app): will get refused
     * - replace P2P requestorWs with fg app
     * - create NAN (system app)
     */
    @Test
    public void testReplaceRequestorWs() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initialize a test chip & create a STA (which will configure the chip).
        ChipMockBase chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create P2P interface from privileged app: should succeed.
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_P2P,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("P2P not created", p2pIface, IsNull.notNullValue());

        // get NAN interface from a system app: should fail
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        List<Pair<Integer, WorkSource>> nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, false, TEST_WORKSOURCE_1);
        assertNull("Should not create this NAN", nanDetails);
        nanDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_1);
        assertNull("Should not create this NAN", nanDetails);
        WifiNanIface nanIface = mDut.createNanIface(null, null, TEST_WORKSOURCE_1);
        collector.checkThat("not allocated interface", nanIface, IsNull.nullValue());

        // Now replace the requestorWs (fg app now) for the P2P iface.
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertTrue(mDut.replaceRequestorWs(p2pIface, TEST_WORKSOURCE_2));

        // get NAN interface again from a system app: should succeed now
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        nanIface = (WifiNanIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_NAN,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                new WifiInterface[]{p2pIface}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_1, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("not allocated interface", nanIface, IsNull.notNullValue());
    }

    /**
     * Validate a flow sequence for test chip 2 if a new interface is able to delete an existing
     * interface with user approval.
     *
     * Flow sequence:
     * - create P2P (privileged app)
     * - create NAN (foreground app) but cannot delete P2P with user approval: should fail
     * - create NAN (foreground app) but can delete P2P with user approval: should succeed
     */
    @Test
    public void testInterfaceCreationFlowIfCanDeleteWithUserApproval() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mDut = new HalDeviceManagerSpy();
        ChipMockBase chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Create P2P interface from privileged app: should succeed.
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_P2P,
                "wlan0",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("P2P was not created", p2pIface, IsNull.notNullValue());

        // Create NAN interface from foreground app: should fail.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        List<Pair<Integer, WorkSource>> nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_1);
        assertNull("Should not create this NAN", nanDetails);
        WifiInterface nanIface = mDut.createNanIface(null, null, TEST_WORKSOURCE_1);
        collector.checkThat("NAN was created", nanIface, IsNull.nullValue());

        // Can now delete P2P with user approval
        when(mInterfaceConflictManager.needsUserApprovalToDelete(anyInt(), any(), anyInt(), any()))
                .thenReturn(true);

        // Can create NAN now.
        nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_1);
        assertNotNull("Should create this NAN", nanDetails);
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_NAN,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                new WifiInterface[]{p2pIface}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_1, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), nanDestroyedListener)
        );
        collector.checkThat("NAN was not created", nanIface, IsNull.notNullValue());
    }

    /**
     * Validate that secondary internet STA is treated as opportunistic and can be deleted by other
     * foreground apps.
     *
     * Flow sequence:
     * - create two STAs (privileged app)
     * - create AP (foreground app): should fail
     * - Turn STAs into secondary internet
     * - create AP (foreground app): should succeed
     */
    @Test
    public void testSecondaryInternetStaTreatedAsOpportunistic() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV2.STATIC_CHIP_INFO_JSON_STRING);
        mDut = new HalDeviceManagerSpy();
        ChipMockBase chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // Create two STA interface from privileged app: should succeed.
        WifiInterface staIface1 = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA was not created", staIface1, IsNull.notNullValue());
        WifiInterface staIface2 = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("STA was not created", staIface2, IsNull.notNullValue());

        // Foreground AP cannot be created
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_2);
        assertNull("Should not create this AP", apDetails);
        WifiInterface apIface = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_2, false, mSoftApManager,
                new ArrayList<>());
        collector.checkThat("AP was created", apIface, IsNull.nullValue());

        // Switch STA to secondary internet. Foreground AP can be created now.
        when(mConcreteClientModeManager.getRole()).thenReturn(
                ClientModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(mConcreteClientModeManager.isSecondaryInternet()).thenReturn(true);
        apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_2);
        assertNotNull("Should create this AP", apDetails);
        apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                new WifiInterface[]{staIface2}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs);
        );
        collector.checkThat("AP was not created", apIface, IsNull.notNullValue());
    }

    /**
     * Validate that disconnected P2P is treated as opportunistic and can be deleted by other
     * foreground apps after config_disconnectedP2pIfaceLowPriorityTimeoutMs.
     *
     * Flow sequence:
     * - create STA and P2P (privileged app)
     * - create NAN (foreground app): should fail
     * - advance clock to config_disconnectedP2pIfaceLowPriorityTimeoutMs
     * - create NAN (foreground app): should succeed
     */
    @Test
    public void testDisconnectedP2pTreatedAsOpportunisticAfterTimeout() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mResources.getInteger(R.integer.config_disconnectedP2pIfaceLowPriorityTimeoutMs))
                .thenReturn(1);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV2.STATIC_CHIP_INFO_JSON_STRING);
        mDut = new HalDeviceManagerSpy();
        ChipMockBase chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();
        ArgumentCaptor<BroadcastReceiver> brCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext, atLeastOnce()).registerReceiver(brCaptor.capture(), any(), any(), any());

        // Create STA and P2P interface from privileged app: should succeed.
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA was not created", staIface, IsNull.notNullValue());
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_P2P,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P was not created", p2pIface, IsNull.notNullValue());

        // Foreground NAN cannot be created
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        List<Pair<Integer, WorkSource>> nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_2);
        assertNull("Should not create this NAN", nanDetails);
        WifiInterface nanIface = mDut.createNanIface(null, null, TEST_WORKSOURCE_2);
        collector.checkThat("NAN was created", nanIface, IsNull.nullValue());

        // Timeout the P2P but also connect it. Foreground NAN still can't be created since P2P is
        // connected.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1L);
        final NetworkInfo networkInfo = new NetworkInfo(ConnectivityManager.TYPE_WIFI_P2P,
                0, "WIFI_P2P", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);
        Intent connectionIntent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        connectionIntent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, networkInfo);
        brCaptor.getValue().onReceive(mContext, connectionIntent);
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_2);
        assertNull("Should not create this NAN", nanDetails);
        nanIface = mDut.createNanIface(null, null, TEST_WORKSOURCE_2);
        collector.checkThat("NAN was created", nanIface, IsNull.nullValue());

        // Simulate P2P disconnection. Foreground NAN can be created now.
        networkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, null, null);
        connectionIntent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, networkInfo);
        brCaptor.getValue().onReceive(mContext, connectionIntent);
        assertFalse("Should not treat disconnected P2P as privileged iface",
                mDut.creatingIfaceWillDeletePrivilegedIface(
                        HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_2));
        nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_2);
        assertNotNull("Should create this NAN", nanDetails);
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_NAN,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                new WifiInterface[]{p2pIface}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs);
        );
        collector.checkThat("NAN was not created", nanIface, IsNull.notNullValue());
    }

    /**
     * Validate the behavior of creatingIfaceWillDeletePrivilegedIface
     */
    @Test
    public void testCreatingIfaceWillDeletePrivilegedIface() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // No interface needs to be deleted
        assertFalse(mDut.creatingIfaceWillDeletePrivilegedIface(
                HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Request STA
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Privileged AP beats privileged STA
        assertTrue(mDut.creatingIfaceWillDeletePrivilegedIface(
                HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Internal AP cannot beat privileged STA
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_INTERNAL);
        assertFalse(mDut.creatingIfaceWillDeletePrivilegedIface(
                HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Foreground AP can beat background STA
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_BG);
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.creatingIfaceWillDeletePrivilegedIface(
                HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Chip Specific Tests - but should work on all chips!
    // (i.e. add copies for each test chip)
    //////////////////////////////////////////////////////////////////////////////////////

    // TestChipV1

    /**
     * Validate creation of STA interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateStaInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_STA, "wlan0",
                TestChipV1.STA_CHIP_MODE_ID);
    }

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV1.AP_CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV1.STA_CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV1.STA_CHIP_MODE_ID);
    }

    // TestChipV2

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV2() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV2(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV2.CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV2() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV2(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV2.CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV2() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV2(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV2.CHIP_MODE_ID);
    }

    // TestChipV3
    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV3() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV3(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV3.CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV3() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV3(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV3.CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV3() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV3(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV3.CHIP_MODE_ID);
    }

    // TestChipV4

    /**
     * Validate creation of STA interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateStaInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_STA, "wlan0",
                TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV4.CHIP_MODE_ID);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV1 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate creation of AP interface when in STA mode - but with no interface created. Expect
     * a change in chip mode.
     */
    @Test
    public void testCreateApWithStaModeUpTestChipV1() throws Exception {
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        WifiApIface iface = (WifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                name, // ifaceName
                TestChipV1.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: callback triggered
        verify(idl).onDestroyed(getName(iface));
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Verify that when the thread that caused an iface to get destroyed is not the thread the
     * onDestroy callback is intended to be invoked on, then onDestroy is will get posted to the
     * correct thread.
     */
    @Test
    public void testOnDestroyedWithHandlerTriggeredOnDifferentThread() throws Exception {
        when(mFeatureFlags.singleWifiThread()).thenReturn(false);
        long currentThreadId = 983757; // arbitrary current thread ID
        when(mWifiInjector.getCurrentThreadId()).thenReturn(currentThreadId);
        // RETURNS_DEEP_STUBS allows mocking nested method calls
        Handler staIfaceOnDestroyedHandler = mock(Handler.class, Mockito.RETURNS_DEEP_STUBS);
        // Configure the handler to be on a different thread as the current thread.
        when(staIfaceOnDestroyedHandler.getLooper().getThread().getId())
                .thenReturn(currentThreadId + 1);
        InterfaceDestroyedListener staIdl = mock(InterfaceDestroyedListener.class);
        ArgumentCaptor<Runnable> lambdaCaptor = ArgumentCaptor.forClass(Runnable.class);

        // simulate adding a STA iface and then stopping wifi
        simulateStartAndStopWifi(staIdl, staIfaceOnDestroyedHandler);

        // Verify a runnable is posted because current thread is different than the intended thread
        // for running "onDestroyed"
        verify(staIfaceOnDestroyedHandler).postAtFrontOfQueue(lambdaCaptor.capture());

        // Verify onDestroyed is only run after the posted runnable is dispatched
        verify(staIdl, never()).onDestroyed("wlan0");
        lambdaCaptor.getValue().run();
        verify(staIdl).onDestroyed("wlan0");
    }

    /**
     * Verify that when the thread that caused an interface to get destroyed is not the thread the
     * onDestroy callback is intended to be invoked on, dispatchDestroyedListeners will block till
     * onDestroy callback is done, provided the overlay config_wifiWaitForDestroyedListeners is
     * True.
     */
    @Test
    public void testOnDestroyedWaitingWithHandlerTriggeredOnDifferentThread() throws Exception {
        when(mFeatureFlags.singleWifiThread()).thenReturn(false);
        // Enable waiting for destroy listeners
        mWaitForDestroyedListeners = true;
        // Setup a separate thread for destroy
        HandlerThread mHandlerThread = new HandlerThread("DestroyListener");
        mHandlerThread.start();
        Handler staIfaceOnDestroyedHandler = spy(mHandlerThread.getThreadHandler());
        InterfaceDestroyedListener staIdl = mock(InterfaceDestroyedListener.class);
        // Setup Wi-Fi
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, staIdl, chipMock.chip);
        // Start Wi-Fi
        assertTrue(mDut.start());
        // Create STA Iface.
        WifiStaIface staIface = mock(WifiStaIface.class);
        when(staIface.getName()).thenReturn("wlan0");
        doAnswer(new CreateStaIfaceAnswer(chipMock, true, staIface))
                .when(chipMock.chip).createStaIface();
        assertEquals(staIface, mDut.createStaIface(staIdl, staIfaceOnDestroyedHandler,
                TEST_WORKSOURCE_0, mConcreteClientModeManager));
        // Remove STA interface
        mDut.removeIface(staIface);
        // Dispatch
        mTestLooper.startAutoDispatch();
        mTestLooper.dispatchAll();
        // Validate OnDestroyed is called before removing interface.
        mInOrder.verify(staIdl).onDestroyed("wlan0");
        mInOrder.verify(chipMock.chip).removeStaIface("wlan0");
    }

    /**
     * Verify that when the thread that caused an iface to get destroyed is already the thread the
     * onDestroy callback is intended to be invoked on, then onDestroy is invoked directly.
     */
    @Test
    public void testOnDestroyedWithHandlerTriggeredOnSameThread() throws Exception {
        long currentThreadId = 983757; // arbitrary current thread ID
        when(mWifiInjector.getCurrentThreadId()).thenReturn(currentThreadId);
        // RETURNS_DEEP_STUBS allows mocking nested method calls
        Handler staIfaceOnDestroyedHandler = mock(Handler.class, Mockito.RETURNS_DEEP_STUBS);
        // Configure the handler thread ID so it's the same as the current thread.
        when(staIfaceOnDestroyedHandler.getLooper().getThread().getId())
                .thenReturn(currentThreadId);
        InterfaceDestroyedListener staIdl = mock(InterfaceDestroyedListener.class);

        // simulate adding a STA iface and then stopping wifi
        simulateStartAndStopWifi(staIdl, staIfaceOnDestroyedHandler);

        // Verify a runnable is never posted
        verify(staIfaceOnDestroyedHandler, never()).postAtFrontOfQueue(any());
        // Verify onDestroyed is triggered directly
        verify(staIdl).onDestroyed("wlan0");
    }

    private void simulateStartAndStopWifi(InterfaceDestroyedListener staIdl,
            Handler staIfaceOnDestroyedHandler) throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);

        // start Wi-Fi
        assertTrue(mDut.start());

        // Create STA Iface.
        WifiStaIface staIface = mock(WifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName();
        doAnswer(new CreateStaIfaceAnswer(chipMock, true, staIface))
                .when(chipMock.chip).createStaIface();
        assertEquals(staIface, mDut.createStaIface(staIdl, staIfaceOnDestroyedHandler,
                TEST_WORKSOURCE_0, mConcreteClientModeManager));

        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.STA_CHIP_MODE_ID);

        // Stop Wi-Fi
        mDut.stop();
        mInOrder.verify(mWifiMock).stop();
    }

    /**
     * Validate creation of AP interface when in STA mode with a single STA iface created.
     * Expect a change in chip mode.
     */
    @Test
    public void testCreateApWithStaIfaceUpTestChipV1UsingHandlerListeners() throws Exception {
        // Make the creation and InterfaceDestroyListener running on the same thread to verify the
        // order in the real scenario.
        when(mWifiInjector.getCurrentThreadId())
                .thenReturn(mTestLooper.getLooper().getThread().getId());

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        InterfaceDestroyedListener staIdl = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener apIdl = mock(
                InterfaceDestroyedListener.class);

        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock, staIdl, apIdl);

        // Register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, null);
        assertTrue(mDut.start());
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // Create STA Iface first.
        WifiStaIface staIface = mock(WifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName();
        doAnswer(new CreateStaIfaceAnswer(chipMock, true, staIface))
                .when(chipMock.chip).createStaIface();
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertTrue("Expecting nothing to destroy on creating STA", staDetails.isEmpty());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertTrue("Expecting nothing to destroy on creating STA", staDetails.isEmpty());
        assertEquals(staIface, mDut.createStaIface(staIdl, mHandler, TEST_WORKSOURCE_0,
                mConcreteClientModeManager));

        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.STA_CHIP_MODE_ID);

        // Now Create AP Iface.
        WifiApIface apIface = mock(WifiApIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(apIface).getName();
        doAnswer(new CreateApIfaceAnswer(chipMock, true, apIface))
                .when(chipMock.chip).createApIface(anyList());
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertEquals("Should get STA destroy details", 1, apDetails.size());
        assertEquals("Need to destroy the STA",
                Pair.create(WifiChip.IFACE_TYPE_STA, TEST_WORKSOURCE_0), apDetails.get(0));
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertEquals("Should get STA destroy details", 1, apDetails.size());
        assertEquals("Need to destroy the STA",
                Pair.create(WifiChip.IFACE_TYPE_STA, TEST_WORKSOURCE_0), apDetails.get(0));
        assertEquals(apIface, mDut.createApIface(
                CHIP_CAPABILITY_ANY, apIdl, mHandler, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>()));
        mInOrder.verify(chipMock.chip).removeStaIface(getName(staIface));
        mInOrder.verify(staIdl).onDestroyed(getName(staIface));
        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.AP_CHIP_MODE_ID);

        // Stop Wi-Fi
        mDut.stop();

        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();
        mInOrder.verify(apIdl).onDestroyed(getName(apIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staIdl, apIdl);
    }

    /**
     * Validate creation of a lower priority AP interface (i.e. LOHS) when in STA mode with a single
     * STA iface created. Expect a change in chip mode.
     */
    @Test
    public void testCreateLowerPriorityApWithStaIfaceUpTestChipV1UsingHandlerListeners()
            throws Exception {
        // Make the creation and InterfaceDestroyListener running on the same thread to verify the
        // order in the real scenario.
        when(mWifiInjector.getCurrentThreadId())
                .thenReturn(mTestLooper.getLooper().getThread().getId());

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        InterfaceDestroyedListener staIdl = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener apIdl = mock(
                InterfaceDestroyedListener.class);

        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock, staIdl, apIdl);

        // Register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, null);
        assertTrue(mDut.start());
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // Create STA Iface first.
        WifiStaIface staIface = mock(WifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName();
        doAnswer(new CreateStaIfaceAnswer(chipMock, true, staIface))
                .when(chipMock.chip).createStaIface();
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertTrue("Expecting nothing to destroy on creating STA", staDetails.isEmpty());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertTrue("Expecting nothing to destroy on creating STA", staDetails.isEmpty());
        assertEquals(staIface, mDut.createStaIface(staIdl, mHandler, TEST_WORKSOURCE_0,
                mConcreteClientModeManager));

        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.STA_CHIP_MODE_ID);

        // Now Create AP Iface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        WifiApIface apIface = mock(WifiApIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(apIface).getName();
        doAnswer(new CreateApIfaceAnswer(chipMock, true, apIface))
                .when(chipMock.chip).createApIface(anyList());
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertEquals("Should get STA destroy details", 1, apDetails.size());
        assertEquals("Need to destroy the STA",
                Pair.create(WifiChip.IFACE_TYPE_STA, TEST_WORKSOURCE_0), apDetails.get(0));
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertEquals("Should get STA destroy details", 1, apDetails.size());
        assertEquals("Need to destroy the STA",
                Pair.create(WifiChip.IFACE_TYPE_STA, TEST_WORKSOURCE_0), apDetails.get(0));
        assertEquals(apIface, mDut.createApIface(
                CHIP_CAPABILITY_ANY, apIdl, mHandler, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>()));
        mInOrder.verify(chipMock.chip).removeStaIface(getName(staIface));
        mInOrder.verify(staIdl).onDestroyed(getName(staIface));
        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.AP_CHIP_MODE_ID);

        // Stop Wi-Fi
        mDut.stop();

        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();
        mInOrder.verify(apIdl).onDestroyed(getName(apIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staIdl, apIdl);
    }

    /**
     * Validate creation of interface with valid listener but Null handler will be failed.
     */
    @Test
    public void testCreateIfaceTestChipV1UsingNullHandlerListeners() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock, idl);

        // Register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, null);
        assertTrue(mDut.start());
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // Create STA Iface will be failure because null handler.
        WifiStaIface staIface = mock(WifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName();
        doAnswer(new CreateStaIfaceAnswer(chipMock, true, staIface))
                .when(chipMock.chip).createStaIface();
        assertNull(mDut.createStaIface(idl, null, TEST_WORKSOURCE_0, mConcreteClientModeManager));

        // Create AP Iface will be failure because null handler.
        WifiApIface apIface = mock(WifiApIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(apIface).getName();
        doAnswer(new CreateApIfaceAnswer(chipMock, true, apIface))
                .when(chipMock.chip).createApIface(anyList());
        assertNull(mDut.createApIface(
                CHIP_CAPABILITY_ANY, idl, null, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>()));

        // Create NAN Iface will be failure because null handler.
        WifiNanIface nanIface = mock(WifiNanIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(nanIface).getName();
        doAnswer(new CreateNanIfaceAnswer(chipMock, true, nanIface))
                .when(chipMock.chip).createNanIface();
        assertNull(mDut.createNanIface(idl, null, TEST_WORKSOURCE_0));

        // Create P2P Iface will be failure because null handler.
        WifiP2pIface p2pIface = mock(WifiP2pIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(p2pIface).getName();
        doAnswer(new CreateP2pIfaceAnswer(chipMock, true, p2pIface))
                .when(chipMock.chip).createP2pIface();
        assertNull(mDut.createP2pIface(idl, null, TEST_WORKSOURCE_0));

        // Stop Wi-Fi
        mDut.stop();

        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Validate creation of AP interface when in AP mode - but with no interface created. Expect
     * no change in chip mode.
     */
    @Test
    public void testCreateApWithApModeUpTestChipV1() throws Exception {
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        WifiApIface iface = (WifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.AP_CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                name, // ifaceName
                TestChipV1.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: callback triggered
        verify(idl).onDestroyed(getName(iface));
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV1() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV1(), TestChipV1.STA_CHIP_MODE_ID);
    }

    /**
     * Validates that trying to allocate a STA from a lower priority app and then another STA from
     * a privileged app exists, the request fails. Only one STA at a time is permitted (by
     * TestChipV1 chip).
     */
    @Test
    public void testDuplicateStaRequestsFromLowerPriorityAppTestChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener1 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        // get STA interface (from a privileged app)
        WifiInterface staIface1 = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener1, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface1, IsNull.notNullValue());

        // get STA interface again (from a system app)
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_1);
        assertNotNull("Should not have a problem if STA already exists", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNull("Should not be able to create a new STA", staDetails);
        WifiInterface staIface2 = mDut.createStaIface(
                staDestroyedListener2, mHandler, TEST_WORKSOURCE_1, mConcreteClientModeManager);
        collector.checkThat("STA created", staIface2, IsNull.nullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener1,
                staDestroyedListener2);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)}
     */
    @Test
    public void testCanDeviceSupportCreateTypeComboChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);

        // Try to query iface support before starting the HAL. Should return false without any
        // stored static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }}
        ));
        verify(mWifiMock, never()).getChipIds();
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();
        clearInvocations(mWifiMock);

        // Create a STA to get the static chip info from driver and save it to store.
        validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );

        // Verify that the latest static chip info is saved to store.
        verify(mWifiSettingsConfigStore).put(eq(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO),
                eq(new JSONArray(TestChipV1.STATIC_CHIP_INFO_JSON_STRING).toString()));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }}
        ));
        // AP should now be supported after we read directly from the chip.
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));

        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 2);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)} with stored
     * static chip info.
     */
    @Test
    public void testCanDeviceSupportCreateTypeComboChipV1WithStoredStaticChipInfo()
            throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);

        // Try to query iface support before starting the HAL. Should return true with the stored
        // static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV1.STATIC_CHIP_INFO_JSON_STRING);
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));

        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 2);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validates that {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)} with
     * outdated stored static chip info will be updated once we load the chip info when the driver
     * is up.
     */
    @Test
    public void testCanDeviceSupportCreateTypeComboChipV1WithOutdatedStoredStaticChipInfo()
            throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);

        // Try to query iface support before starting the HAL. Should return false with the outdated
        // stored static chip info that's missing AP capabilities.
        String outdatedStaticChipInfo =
                        "["
                        + "    {"
                        + "        \"chipId\": 10,"
                        + "        \"chipCapabilities\": -1,"
                        + "        \"availableModes\": ["
                        + "            {"
                        + "                \"id\": 0,"
                        + "                \"availableCombinations\": ["
                        + "                    {"
                        + "                        \"limits\": ["
                        + "                            {"
                        + "                                \"maxIfaces\": 1,"
                        + "                                \"types\": [0]"
                        + "                            },"
                        + "                            {"
                        + "                                \"maxIfaces\": 1,"
                        + "                                \"types\": [3, 4]"
                        + "                            }"
                        + "                        ]"
                        + "                    }"
                        + "                ]"
                        + "            }"
                        + "        ]"
                        + "    }"
                        + "]";
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(outdatedStaticChipInfo);

        // Can create a NAN but not an AP from the stored static chip info
        assertTrue(
                mDut.canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {
                            {
                                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
                            }
                        }));
        assertFalse(
                mDut.canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {
                            {
                                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
                            }
                        }));

        verify(mWifiMock, never()).getChipIds();
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();
        clearInvocations(mWifiMock);

        // Create a STA to get the static chip info from driver and save it to store.
        validateInterfaceSequence(
                chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );

        // Verify that the latest static chip info is saved to store.
        verify(mWifiSettingsConfigStore)
                .put(
                        eq(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO),
                        eq(new JSONArray(TestChipV1.STATIC_CHIP_INFO_JSON_STRING).toString()));

        // Now we can create an AP.
        assertTrue(
                mDut.canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {
                            {
                                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
                            }
                        }));
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV1() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // FG app not allowed to create AP interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // New system app not allowed to create AP interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Privileged app allowed to create AP interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // FG app allowed to create NAN interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // BG app allowed to create P2P interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.getRequestorWsPriority()).thenReturn(WorkSourceHelper.PRIORITY_BG);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV1ForR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface.
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // Allowed to create AP interface (since AP can teardown STA interface)
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Allow to create NAN interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Allow to create P2P interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.getRequestorWsPriority()).thenReturn(WorkSourceHelper.PRIORITY_BG);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV2 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate a flow sequence for test chip 2:
     * - create STA (system app)
     * - create P2P (system app)
     * - create NAN (privileged app): should tear down P2P first
     * - create AP (privileged app)
     * - create STA (system app): will get refused
     * - create AP (system app): will get refuse
     * - tear down AP
     * - create STA (system app)
     * - create STA (system app): will get refused
     * - create AP (privileged app): should get created and the last created STA should get
     *   destroyed
     * - tear down P2P
     * - create NAN (system app)
     */
    @Test
    public void testInterfaceCreationFlowTestChipV2() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA (system app)
        when(mClock.getUptimeSinceBootMillis()).thenReturn(15L);
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P (system app)
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN (system app)
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP (privileged app)
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not be able to create a new STA", staDetails);
        WifiInterface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0,
                mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2 (system app): should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not be able to create a new AP", apDetails);
        WifiInterface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>());
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // create STA2 (system app): using a later clock
        when(mClock.getUptimeSinceBootMillis()).thenReturn(20L);
        staIface2 = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener2, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA 2 interface wasn't created", staIface2, IsNull.notNullValue());

        // request STA3 (system app): should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        WifiInterface staIface3 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0,
                mConcreteClientModeManager);
        collector.checkThat("STA3 should not be created", staIface3, IsNull.nullValue());

        // create AP (privileged app) - this will destroy the last STA created, i.e. STA2
        apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{staIface2}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                // destroyedInterfacesDestroyedListeners...
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(staIface2), staDestroyedListener2)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());

        // tear down NAN
        mDut.removeIface(nanIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeNanIface("wlan0");
        verify(nanDestroyedListener).onDestroyed(getName(nanIface));

        // create NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                staDestroyedListener2, apDestroyedListener, p2pDestroyedListener,
                nanDestroyedListener);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV2() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV2(), TestChipV2.CHIP_MODE_ID);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV2() throws Exception {
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV2() throws Exception {
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)}
     */
    @Test
    public void testCanDeviceSupportIfaceComboTestChipV2() throws Exception {
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        // Try to query iface support before starting the HAL. Should return true with the stored
        // static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV2.STATIC_CHIP_INFO_JSON_STRING);
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }}
        ));
        verify(mWifiMock, never()).getChipIds();
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();

        clearInvocations(mWifiMock);

        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 2);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));

        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 2);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 2);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV2() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // FG app not allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // New system app not allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Privileged app allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // FG app allowed to create NAN interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // BG app allowed to create P2P interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper0.getRequestorWsPriority()).thenReturn(WorkSourceHelper.PRIORITY_BG);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV3 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate a flow sequence for test chip 3:
     * - create STA (system app)
     * - create P2P (system app)
     * - create NAN (privileged app): should tear down P2P first
     * - create AP (privileged app): should tear down NAN first
     * - create STA (system app): will get refused
     * - create AP (system app): will get refused
     * - request P2P (system app): failure
     * - request P2P (privileged app): failure
     * - tear down AP
     * - create STA (system app)
     * - create P2P (system app): will get refused
     * - create STA (system app): will get refused
     * - make STA secondary, create P2P (privileged app): should tear down STA
     * - create STA (foreground app): will get refused
     */
    @Test
    public void testInterfaceCreationFlowTestChipV3() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA (system app)
        when(mClock.getUptimeSinceBootMillis()).thenReturn(15L);
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P (system app)
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN (privileged app): will destroy P2P
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP (privileged app): will destroy NAN
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeP2pIface("p2p0");

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        WifiInterface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0,
                mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2 (system app): should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this AP", apDetails);
        WifiInterface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>());
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // request P2P (system app): should fail
        List<Pair<Integer, WorkSource>> p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        String p2pIfaceName = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIfaceName, IsNull.nullValue());

        // request P2P (privileged app): should fail
        p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_2);
        assertNull("should not create this p2p", p2pDetails);
        p2pIfaceName = mDut.createP2pIface(null, null, TEST_WORKSOURCE_2);
        collector.checkThat("P2P should not be created", p2pIfaceName, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // create STA2 (system app): using a later clock
        when(mClock.getUptimeSinceBootMillis()).thenReturn(20L);
        staIface2 = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener2, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA 2 interface wasn't created", staIface2, IsNull.notNullValue());

        // request P2P (system app): should fail even though both are privileged
        p2pDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        p2pIfaceName = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIfaceName, IsNull.nullValue());

        // request STA3 (system app): should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        WifiInterface staIface3 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0,
                mConcreteClientModeManager);
        collector.checkThat("STA3 should not be created", staIface3, IsNull.nullValue());

        // Make STA2 secondary and create P2P (privileged app): should destroy the STA
        when(mConcreteClientModeManager.getRole())
                .thenReturn(ClientModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);
        p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{staIface2}, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(staIface2), staDestroyedListener2)
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        verify(chipMock.chip).removeStaIface("wlan1");
        verify(staDestroyedListener2).onDestroyed(getName(staIface2));

        // request STA2 (foreground app): should fail
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_1);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNull("should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_1, mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                staDestroyedListener2, apDestroyedListener, p2pDestroyedListener,
                nanDestroyedListener);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV3() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV3(), TestChipV3.CHIP_MODE_ID);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV3() throws Exception {
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV3() throws Exception {
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV3() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // FG app not allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // New system app not allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Privileged app allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // FG app not allowed to create NAN interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Privileged app allowed to create P2P interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV4 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate a flow sequence for test chip 4:
     * - create STA (system app)
     * - create P2P (system app)
     * - create NAN (privileged app): should tear down P2P first
     * - create AP (privileged app): should tear down NAN first
     * - create STA (system app): will get refused
     * - create AP (system app): will get refused
     * - request P2P (system app): failure
     * - request P2P (privileged app): failure
     * - tear down AP
     * - create STA (system app): will get refused
     * - create NAN (privileged app)
     * - create STA (foreground app): will get refused
     */
    @Test
    public void testInterfaceCreationFlowTestChipV4() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA (system app)
        when(mClock.getUptimeSinceBootMillis()).thenReturn(15L);
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P (system app)
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN (privileged app): will destroy P2P
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("allocated NAN interface", nanIface, IsNull.notNullValue());

        // create AP (privileged app): will destroy NAN
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeP2pIface("p2p0");

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        WifiInterface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0,
                mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2 (system app): should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this AP", apDetails);
        WifiInterface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>());
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // request P2P (system app): should fail
        List<Pair<Integer, WorkSource>> p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        String p2pIfaceName = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIfaceName, IsNull.nullValue());

        // request P2P (privileged app): should fail
        p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_2);
        assertNull("should not create this p2p", p2pDetails);
        p2pIfaceName = mDut.createP2pIface(null, null, TEST_WORKSOURCE_2);
        collector.checkThat("P2P should not be created", p2pIfaceName, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // request STA2 (system app): should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0, mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // create NAN (privileged app)
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // request STA2 (foreground app): should fail
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_1);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNull("should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_1, mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // tear down STA
        mDut.removeIface(staIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeStaIface("wlan0");
        verify(staDestroyedListener).onDestroyed(getName(staIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                staDestroyedListener2, apDestroyedListener, p2pDestroyedListener,
                nanDestroyedListener);
    }

    @Test
    public void testInterfaceCreationFlowTestChipV4ForR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN: will destroy P2P
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP: will destroy NAN
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeP2pIface("p2p0");

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this STA", staDetails);
        WifiInterface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0,
                mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2: should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this AP", apDetails);
        WifiInterface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager,
                new ArrayList<>());
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // request P2P: should fail
        List<Pair<Integer, WorkSource>> p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        String p2pIfaceName = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIfaceName, IsNull.nullValue());

        // request NAN: should fail
        List<Pair<Integer, WorkSource>> nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this nan", nanDetails);
        // Expect a WifiNanIface wrapper object this time, since we are calling
        // createNanIface instead of createIface.
        WifiNanIface nanIface2 =
                mDut.createNanIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("NAN should not be created", nanIface2, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // request STA2: should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0, mConcreteClientModeManager);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // create NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());
    }

    /**
     * Validate a flow sequence for test chip 3:
     * - create NAN (internal request)
     * - create AP (privileged app): should tear down NAN first
     */
    @Test
    public void testInterfaceCreationFlowTestChipV3WithInternalRequest() throws Exception {
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create P2P (internal request)
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_INTERNAL);
        // create NAN (privileged app): will destroy P2P
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP (privileged app): will destroy NAN
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new WifiInterface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeNanIface("wlan0");

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, apDestroyedListener,
                nanDestroyedListener);
    }


    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV4() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV4(), TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV4() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV4() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(WifiChip.IFACE_TYPE_AP);
        correctResults.add(WifiChip.IFACE_TYPE_STA);
        correctResults.add(WifiChip.IFACE_TYPE_P2P);
        correctResults.add(WifiChip.IFACE_TYPE_NAN);

        assertEquals(correctResults, results);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV4() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // FG app not allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // New system app not allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Privileged app allowed to create STA interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // FG app not allowed to create NAN interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Privileged app allowed to create P2P interface.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV4ForR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface.
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface.
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // Not allowed to create STA interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Not allowed to create AP interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Not allowed to create NAN interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Not allowed to create P2P interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    public void verify60GhzIfaceCreation(
            ChipMockBase chipMock, int chipModeId, int finalChipModeId, boolean isWigigSupported)
            throws Exception {
        long requiredChipCapabilities = WifiManager.WIFI_FEATURE_INFRA_60G;
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface staIface;
        if (isWigigSupported) {
            staIface = validateInterfaceSequence(chipMock,
                    false, // chipModeValid
                    -1000, // chipModeId (only used if chipModeValid is true)
                    HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                    "wlan0", // ifaceName
                    finalChipModeId, // finalChipMode
                    requiredChipCapabilities, // requiredChipCapabilities
                    null, // tearDownList
                    mock(InterfaceDestroyedListener.class), // destroyedListener
                    TEST_WORKSOURCE_0 // requestorWs
            );
            collector.checkThat("STA created", staIface, IsNull.notNullValue());
        } else {
            assertFalse("Should not be able to create this STA", mDut.isItPossibleToCreateIface(
                    HDM_CREATE_IFACE_STA, requiredChipCapabilities, TEST_WORKSOURCE_1));
            staIface = mDut.createStaIface(
                    requiredChipCapabilities, null, null, TEST_WORKSOURCE_1,
                    mConcreteClientModeManager);
            mInOrder.verify(chipMock.chip, times(0)).configureChip(anyInt());
            collector.checkThat("STA should not be created", staIface, IsNull.nullValue());
        }

        // get AP interface from system app.
        when(mWorkSourceHelper0.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface apIface;
        if (isWigigSupported) {
            apIface = validateInterfaceSequence(chipMock,
                    true, // chipModeValid
                    chipModeId, // chipModeId (only used if chipModeValid is true)
                    HDM_CREATE_IFACE_AP, // createIfaceType
                    "wlan0", // ifaceName
                    finalChipModeId, // finalChipMode
                    requiredChipCapabilities, // requiredChipCapabilities
                    null, // tearDownList
                    mock(InterfaceDestroyedListener.class), // destroyedListener
                    TEST_WORKSOURCE_0 // requestorWs
            );
            collector.checkThat("AP created", apIface, IsNull.notNullValue());
        } else {
            assertFalse("Should not be able to create this AP", mDut.isItPossibleToCreateIface(
                    HDM_CREATE_IFACE_AP, requiredChipCapabilities, TEST_WORKSOURCE_0));
            apIface = mDut.createApIface(
                    requiredChipCapabilities, null, null, TEST_WORKSOURCE_0, false, mSoftApManager,
                    new ArrayList<>());
            collector.checkThat("AP should not be created", apIface, IsNull.nullValue());
        }
        if (SdkLevel.isAtLeastS()) {
            // Privileged app allowed to create P2P interface.
            when(mWorkSourceHelper1.getRequestorWsPriority())
                    .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
            assertThat(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P,
                    WifiManager.WIFI_FEATURE_INFRA_60G,
                    TEST_WORKSOURCE_1), is(isWigigSupported));
        }
    }

    /*
     * Verify that 60GHz iface creation request could be procceed by a chip supports
     * WIGIG.
     */
    @Test
    public void testIsItPossibleToCreate60GhzIfaceTestChipV5() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        verify60GhzIfaceCreation(
                chipMock, TestChipV5.CHIP_MODE_ID, TestChipV5.CHIP_MODE_ID, true);
    }

    /*
     * Verify that 60GHz iface creation request cannot be processed by a chip that does
     * not support WIGIG.
     */
    @Test
    public void testIsItPossibleToCreate60GhzIfaceTestChipV4() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        verify60GhzIfaceCreation(
                chipMock, TestChipV4.CHIP_MODE_ID, TestChipV4.CHIP_MODE_ID, false);
    }

    /**
     * Validate creation of AP interface from blank start-up.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV15() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV5(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV5.CHIP_MODE_ID);
    }
    /**
     * Validate creation of AP Bridge interface from blank start-up.
     */
    @Test
    public void testCreateApBridgeInterfaceNoInitModeTestChipV15() throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = true;
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV5(), HDM_CREATE_IFACE_AP_BRIDGE, "wlan0",
                TestChipV5.CHIP_MODE_ID);
    }

    /**
     * Validate creation of AP Bridge interface must destroy an existing STA if the device doesn't
     * support STA + Bridged AP.
     */
    @Test
    public void testCreateApBridgeInterfaceWithoutStaBridgedApConcurrencyV15() throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV5 chipMock = new TestChipV5();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the STA
        WifiInterface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV5.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        // Cannot create Bridged AP without destroying the STA
        List<Pair<Integer, WorkSource>> bridgedApDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP_BRIDGE, true, TEST_WORKSOURCE_0);
        assertFalse(bridgedApDetails.isEmpty());
    }

    /**
     * Validate creation of AP Bridge interface succeeds if there is a STA up and the device
     * supports STA + Bridged AP.
     */
    @Test
    public void testCreateApBridgeInterfaceWithStaBridgedApConcurrencyV15() throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = true;
        TestChipV5 chipMock = new TestChipV5();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        WifiInterface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV5.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        List<Pair<Integer, WorkSource>> bridgedApDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP_BRIDGE, true, TEST_WORKSOURCE_1);
        // STA + AP_BRIDGED not supported
        assertEquals(0, bridgedApDetails.size());
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)}
     */
    @Test
    public void testCanDeviceSupportIfaceComboTestChipV6() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        testChip.initialize();
        mInOrder = inOrder(mWifiMock, testChip.chip, mManagerStatusListenerMock);
        // Try to query iface support before starting the HAL. Should return true with the stored
        // static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV6.STATIC_CHIP_INFO_JSON_STRING);
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }
        }));
        verify(mWifiMock, never()).getChipIds();
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();

        clearInvocations(mWifiMock);

        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
            }
        }));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }
        }));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }
        }));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, 2);
            }
        }));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate creation of AP Bridge interface from blank start-up in TestChipV6
     */
    @Test
    public void testCreateApBridgeInterfaceNoInitModeTestChipV6() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        runCreateSingleXxxInterfaceNoInitMode(testChip, HDM_CREATE_IFACE_AP_BRIDGE, "wlan0",
                TestChipV6.CHIP_MODE_ID);
    }

    /**
     * Validate creation of STA will not downgrade an AP Bridge interface in TestChipV6, since it
     * can support STA and AP Bridge concurrently.
     */
    @Test
    public void testCreateStaDoesNotDowngradeApBridgeInterfaceTestChipV6() throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV6 chipMock = new TestChipV6();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the bridged AP
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        WifiInterface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE,
                "wlan0",
                TestChipV6.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        when(mSoftApManager.getBridgedApDowngradeIfaceInstanceForRemoval()).thenReturn("instance1");
        // Should be able to create a STA without downgrading the bridged AP
        iface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV6.CHIP_MODE_ID,
                HDM_CREATE_IFACE_STA,
                "wlan3",
                TestChipV6.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());
        assertEquals(2, bridgedApInstances.size());
    }

    private WifiInterface setupDbsSupportTest(ChipMockBase testChip, int onlyChipMode,
            ImmutableList<ArrayList<Integer>> radioCombinations) throws Exception {
        List<WifiChip.WifiRadioCombination> combos = new ArrayList<>();
        for (ArrayList<Integer> comb : radioCombinations) {
            List<WifiChip.WifiRadioConfiguration> configs = new ArrayList<>();
            for (Integer b : comb) {
                configs.add(new WifiChip.WifiRadioConfiguration(b, 0));
            }
            combos.add(new WifiChip.WifiRadioCombination(new ArrayList<>(configs)));
        }
        testChip.chipSupportedRadioCombinations = combos;

        testChip.initialize();
        mInOrder = inOrder(mWifiMock, testChip.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Request STA
        WifiInterface staIface = validateInterfaceSequence(testChip,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // createIfaceType
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Request P2P
        WifiInterface p2pIface = validateInterfaceSequence(testChip,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("P2P can't be created", p2pIface, IsNull.notNullValue());
        mTestLooper.dispatchAll();

        return staIface;
    }

    /**
     * Validate 24GHz/5GHz DBS support.
     */
    @Test
    public void test24g5gDbsSupport() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinations = ImmutableList.of(
                new ArrayList(Arrays.asList(
                        WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_5_GHZ)));
        WifiInterface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinations);

        assertTrue(mDut.is24g5gDbsSupported(iface));
        assertFalse(mDut.is5g6gDbsSupported(iface));
    }

    /**
     * Validate 5GHz/6GHz DBS support.
     */
    @Test
    public void test5g6gDbsSupport() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinations = ImmutableList.of(
                new ArrayList(Arrays.asList(
                        WifiScanner.WIFI_BAND_5_GHZ, WifiScanner.WIFI_BAND_6_GHZ)));
        WifiInterface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinations);

        assertFalse(mDut.is24g5gDbsSupported(iface));
        assertTrue(mDut.is5g6gDbsSupported(iface));
    }

    /**
     * Validate 2.4GHz/5GHz DBS and 5GHz/6GHz DBS support.
     */
    @Test
    public void test24g5gAnd5g6gDbsSupport() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinations = ImmutableList.of(
                new ArrayList(Arrays.asList(
                        WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_5_GHZ)),
                new ArrayList(Arrays.asList(
                        WifiScanner.WIFI_BAND_5_GHZ, WifiScanner.WIFI_BAND_6_GHZ)));
        WifiInterface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinations);

        assertTrue(mDut.is24g5gDbsSupported(iface));
        assertTrue(mDut.is5g6gDbsSupported(iface));
    }

    /**
     * Validate band combinations supported by the chip.
     */
    @Test
    public void testBandCombinations() throws Exception {
        // Prepare the chip configuration.
        // e.g. supported band combination for this test case.
        //          2.4
        //          5
        //          6
        //          2.4 x 5
        //          2.4 x 6
        //          5 x 6
        //          5 x 5  (SBS)
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinationMatrix = ImmutableList.of(
                new ArrayList(Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ)),
                new ArrayList(Arrays.asList(WifiScanner.WIFI_BAND_5_GHZ)),
                new ArrayList(Arrays.asList(WifiScanner.WIFI_BAND_6_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_5_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_6_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_5_GHZ, WifiScanner.WIFI_BAND_6_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_5_GHZ, WifiScanner.WIFI_BAND_5_GHZ)));
        WifiInterface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinationMatrix);
        // Test all valid combinations.
        for (List<Integer> supportedBands:mDut.getSupportedBandCombinations(iface)) {
            // Test the list returned is unmodifiable.
            assertThrows(UnsupportedOperationException.class,
                    () -> supportedBands.addAll(Collections.emptyList()));
            // shuffle the list to check the order.
            List<Integer> bands = new ArrayList<>(supportedBands);
            Collections.shuffle(bands);
            assertTrue(mDut.isBandCombinationSupported(iface, bands));
        }
        // Test invalid combinations.
        assertFalse(mDut.isBandCombinationSupported(iface,
                Arrays.asList(WifiScanner.WIFI_BAND_6_GHZ, WifiScanner.WIFI_BAND_6_GHZ)));
        assertFalse(mDut.isBandCombinationSupported(iface,
                Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_24_GHZ)));
    }

    /**
     * Validate that a requested iface should have higher priority than ALL of the existing ifaces
     * for a mode change.
     */
    @Test
    public void testIsItPossibleToCreateIfaceBetweenChipModesTestChipV7() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        TestChipV7 chipMock = new TestChipV7();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get STA interface from privileged app.
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV7.DUAL_STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get STA interface from foreground app.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        staIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV7.DUAL_STA_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV7.DUAL_STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // New system app not allowed to create AP interface since it would tear down the privileged
        // app STA during the chip mode change.
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_2));

        // Privileged app allowed to create AP interface since it is able to tear down the
        // privileged app STA during the chip mode change.
        when(mWorkSourceHelper2.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_2));
    }

    /**
     * Validate that a requested iface should delete the correct AP/AP_BRIDGED based on available
     * concurrency and not priority.
     */
    @Test
    public void testCreateInterfaceRemovesCorrectApIfaceTestChipV8() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        TestChipV8 chipMock = new TestChipV8();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        // get AP_BRIDGED interface for a privileged app.
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        WifiInterface apBridgedIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV8.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("Bridged AP created", apBridgedIface, IsNull.notNullValue());

        // get AP interface for a system app.
        when(mWorkSourceHelper1.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_SYSTEM);
        WifiInterface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV8.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV8.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // Check that the impact to add a STA will remove the AP_BRIDGED (TEST_WORKSOURCE_0) and not
        // the AP (TEST_WORKSOURCE_1), even though the AP has lower priority.
        List<Pair<Integer, WorkSource>> impactToCreateSta =
                mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertFalse(impactToCreateSta.isEmpty());
        assertEquals(TEST_WORKSOURCE_0, impactToCreateSta.get(0).second);
    }

    /**
     * Validate creation of STA with a downgraded bridged AP in chip V9 with no STA + Bridged AP
     * concurrency.
     */
    @Test
    public void testCreateStaInterfaceWithDowngradedBridgedApTestChipV9()
            throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV9 chipMock = new TestChipV9();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the bridged AP
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        WifiInterface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE,
                "wlan0",
                TestChipV9.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        // Downgrade the bridged AP.
        chipMock.bridgedApInstancesByName.get("wlan0").remove(0);

        // Should be able to create a STA now since STA + AP is supported
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNotNull(staDetails);
        assertEquals(0, staDetails.size());
    }

    /**
     * Validate a bridged AP will be downgraded to make room for a STA in chip V9 with no STA +
     * Bridged AP concurrency
     */
    @Test
    public void testCreateStaInterfaceWillDowngradeBridgedApTestChipV9()
            throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV9 chipMock = new TestChipV9();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the bridged AP
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        WifiInterface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE,
                "wlan0",
                TestChipV9.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        when(mSoftApManager.getBridgedApDowngradeIfaceInstanceForRemoval()).thenReturn("instance1");
        // Should be able to create a STA by downgrading the bridged AP
        iface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV9.CHIP_MODE_ID,
                HDM_CREATE_IFACE_STA,
                "wlan3",
                TestChipV9.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());
    }

    /**
     * Validates available modes after driver ready.
     */
    @Test
    public void testCanDeviceSupportCreateTypeComboAfterDriverReadyChipV10() throws Exception {
        TestChipV10 chipMock = new TestChipV10();
        chipMock.initialize();
        chipMock.allowGetCapsBeforeIfaceCreated = false;
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);

        // Try to query AP support before starting the HAL. Should return false without any
        // stored static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));
        verify(mWifiMock, never()).getChipIds();
        when(mWifiMock.isStarted()).thenReturn(true);

        // Start the HAL
        executeAndValidateStartupSequence();

        // Still can't create AP since driver isn't ready.
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, 1);
            }}
        ));

        // Create a STA to get the available modes driver and save it to store.
        validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV10.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        verify(mWifiSettingsConfigStore).put(eq(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO),
                eq(new JSONArray(TestChipV10.STATIC_CHIP_INFO_JSON_STRING).toString()));

        // Now we can create the AP
        validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV10.CHIP_MODE_ID, // finalChipMode
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV10.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );

        // Stop and start Wifi and re-initialize the chip to the default available modes.
        mDut.stop();
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);

        // Try creating an AP again -- should succeed since we're using the cached
        // available modes we loaded from the driver.
        validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV10.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // utilities
    ///////////////////////////////////////////////////////////////////////////////////////
    private void dumpDut(String prefix) {
        StringWriter sw = new StringWriter();
        mDut.dump(null, new PrintWriter(sw), null);
        Log.e("HalDeviceManager", prefix + sw.toString());
    }

    private void executeAndValidateStartupSequence() throws Exception {
        executeAndValidateStartupSequence(1, true);
    }

    private void executeAndValidateStartupSequence(int numAttempts, boolean success)
            throws Exception {
        // act: register listener & start Wi-Fi
        mDut.initialize();
        mDut.registerStatusListener(mManagerStatusListenerMock, mHandler);
        collector.checkThat(mDut.start(), equalTo(success));

        // verify
        verify(mWifiMock).registerEventCallback(mWifiEventCallbackCaptor.capture());
        mInOrder.verify(mWifiMock, times(numAttempts)).start();

        if (success) {
            // act: trigger onStart callback of IWifiEventCallback
            mWifiEventCallbackCaptor.getValue().onStart();
            mTestLooper.dispatchAll();

            // verify: onStart called on registered listener
            mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();
        }
    }

    private void runCreateSingleXxxInterfaceNoInitMode(ChipMockBase chipMock, int ifaceTypeToCreate,
            String ifaceName, int finalChipMode) throws Exception {
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        WifiInterface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                ifaceTypeToCreate,
                ifaceName,
                finalChipMode,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: remove interface
        mDut.removeIface(iface);
        mTestLooper.dispatchAll();

        // verify: callback triggered
        switch (ifaceTypeToCreate) {
            case HDM_CREATE_IFACE_STA:
                mInOrder.verify(chipMock.chip).removeStaIface(ifaceName);
                break;
            case HDM_CREATE_IFACE_AP_BRIDGE:
            case HDM_CREATE_IFACE_AP:
                mInOrder.verify(chipMock.chip).removeApIface(ifaceName);
                break;
            case HDM_CREATE_IFACE_P2P:
                mInOrder.verify(chipMock.chip).removeP2pIface(ifaceName);
                break;
            case HDM_CREATE_IFACE_NAN:
                mInOrder.verify(chipMock.chip).removeNanIface(ifaceName);
                break;
        }

        verify(idl).onDestroyed(ifaceName);

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     *
     * Relevant for any chip which supports STA + NAN || P2P (or a richer combination - but bottom
     * line of NAN and P2P being exclusive).
     */
    public void runP2pAndNanExclusiveInteractionsTestChip(ChipMockBase chipMock,
            int onlyChipMode) throws Exception {
        chipMock.initialize();
        mInOrder = inOrder(mWifiMock, chipMock.chip, mManagerStatusListenerMock);
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Request STA
        WifiInterface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // createIfaceType
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Request NAN
        WifiInterface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );

        // Request P2P
        WifiInterface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                onlyChipMode, // finalChipMode
                new WifiInterface[]{nanIface}, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0, // requestorWs
                // destroyedInterfacesDestroyedListeners...
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("P2P can't be created", p2pIface, IsNull.notNullValue());
        mTestLooper.dispatchAll();
        verify(nanDestroyedListener).onDestroyed(getName(nanIface));

        // Request NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                new WifiInterface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("NAN can't be created", nanIface, IsNull.notNullValue());

        mTestLooper.dispatchAll();
        verify(p2pDestroyedListener).onDestroyed(getName(p2pIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                nanDestroyedListener, p2pDestroyedListener);
    }

    private WifiInterface validateInterfaceSequence(ChipMockBase chipMock,
            boolean chipModeValid, int chipModeId,
            int createIfaceType, String ifaceName, int finalChipMode,
            long requiredChipCapabilities,
            WifiInterface[] tearDownList,
            InterfaceDestroyedListener destroyedListener,
            WorkSource requestorWs,
            InterfaceDestroyedListenerWithIfaceName...destroyedInterfacesDestroyedListeners)
            throws Exception {
        // configure chip mode response
        chipMock.chipModeValid = chipModeValid;
        chipMock.chipModeId = chipModeId;

        // check if can create interface
        List<Pair<Integer, WorkSource>> details = mDut.reportImpactToCreateIface(
                createIfaceType, true, requestorWs);
        if (tearDownList == null || tearDownList.length == 0) {
            assertTrue("Details list must be empty - can create" + details, details.isEmpty());
        } else { // TODO: assumes that at most a single entry - which is the current usage
            assertEquals("Details don't match " + details, tearDownList.length, details.size());
            assertEquals("Details don't match " + details, getType(tearDownList[0]),
                    HAL_IFACE_MAP.get(details.get(0).first.intValue()));
        }

        WifiInterface iface = null;

        // configure: interface to be created
        // act: request the interface
        switch (createIfaceType) {
            case HDM_CREATE_IFACE_STA:
                iface = mock(WifiStaIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName();
                doAnswer(new CreateStaIfaceAnswer(chipMock, true, iface))
                        .when(chipMock.chip).createStaIface();
                mDut.createStaIface(requiredChipCapabilities,
                        destroyedListener, mHandler, requestorWs, mConcreteClientModeManager);
                break;
            case HDM_CREATE_IFACE_AP_BRIDGE:
            case HDM_CREATE_IFACE_AP:
                iface = mock(WifiApIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName();
                if (createIfaceType == HDM_CREATE_IFACE_AP_BRIDGE) {
                    doAnswer(new GetBridgedInstancesAnswer(chipMock, ifaceName))
                            .when((WifiApIface) iface).getBridgedInstances();
                }
                doAnswer(new CreateApIfaceAnswer(chipMock, true, iface))
                        .when(chipMock.chip).createApIface(anyList());
                doAnswer(new CreateApIfaceAnswer(chipMock, true, iface))
                        .when(chipMock.chip).createBridgedApIface(anyList());
                mDut.createApIface(requiredChipCapabilities,
                        destroyedListener, mHandler, requestorWs,
                        createIfaceType == HDM_CREATE_IFACE_AP_BRIDGE, mSoftApManager,
                        new ArrayList<>());
                break;
            case HDM_CREATE_IFACE_P2P:
                iface = mock(WifiP2pIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName();
                doAnswer(new CreateP2pIfaceAnswer(chipMock, true, iface))
                        .when(chipMock.chip).createP2pIface();
                mDut.createP2pIface(requiredChipCapabilities,
                        destroyedListener, mHandler, requestorWs);
                break;
            case HDM_CREATE_IFACE_NAN:
                iface = mock(WifiNanIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName();
                doAnswer(new CreateNanIfaceAnswer(chipMock, true, iface))
                        .when(chipMock.chip).createNanIface();
                mDut.createNanIface(destroyedListener, mHandler, requestorWs);
                break;
        }

        // validate: optional tear down of interfaces
        if (tearDownList != null) {
            for (WifiInterface tearDownIface: tearDownList) {
                switch (getType(tearDownIface)) {
                    case WifiChip.IFACE_TYPE_STA:
                        mInOrder.verify(chipMock.chip).removeStaIface(getName(tearDownIface));
                        break;
                    case WifiChip.IFACE_TYPE_AP:
                        mInOrder.verify(chipMock.chip).removeApIface(getName(tearDownIface));
                        break;
                    case WifiChip.IFACE_TYPE_P2P:
                        mInOrder.verify(chipMock.chip).removeP2pIface(getName(tearDownIface));
                        break;
                    case WifiChip.IFACE_TYPE_NAN:
                        mInOrder.verify(chipMock.chip).removeNanIface(getName(tearDownIface));
                        break;
                }
            }
        }

        // validate: optional switch to the requested mode
        if (!chipModeValid || chipModeId != finalChipMode) {
            mInOrder.verify(chipMock.chip).configureChip(finalChipMode);
        } else {
            mInOrder.verify(chipMock.chip, times(0)).configureChip(anyInt());
        }

        // validate: create interface
        switch (createIfaceType) {
            case HDM_CREATE_IFACE_STA:
                mInOrder.verify(chipMock.chip).createStaIface();
                break;
            case HDM_CREATE_IFACE_AP_BRIDGE:
                mInOrder.verify(chipMock.chip).createBridgedApIface(anyList());
                break;
            case HDM_CREATE_IFACE_AP:
                mInOrder.verify(chipMock.chip).createApIface(anyList());
                break;
            case HDM_CREATE_IFACE_P2P:
                mInOrder.verify(chipMock.chip).createP2pIface();
                break;
            case HDM_CREATE_IFACE_NAN:
                mInOrder.verify(chipMock.chip).createNanIface();
                break;
        }

        // verify: callbacks on deleted interfaces
        mTestLooper.dispatchAll();
        for (int i = 0; i < destroyedInterfacesDestroyedListeners.length; ++i) {
            destroyedInterfacesDestroyedListeners[i].validate();
        }
        return iface;
    }

    private WifiInterface validateInterfaceSequence(ChipMockBase chipMock,
            boolean chipModeValid, int chipModeId,
            int createIfaceType, String ifaceName, int finalChipMode,
            WifiInterface[] tearDownList,
            InterfaceDestroyedListener destroyedListener,
            WorkSource requestorWs,
            InterfaceDestroyedListenerWithIfaceName...destroyedInterfacesDestroyedListeners)
            throws Exception {
        return validateInterfaceSequence(chipMock, chipModeValid, chipModeId,
                createIfaceType, ifaceName,
                finalChipMode, CHIP_CAPABILITY_ANY,
                tearDownList, destroyedListener, requestorWs,
                destroyedInterfacesDestroyedListeners);
    }

    private int getType(WifiInterface iface) throws Exception {
        return mDut.getType(iface);
    }

    private String getName(WifiInterface iface) throws Exception {
        return mDut.getName(iface);
    }

    private static class InterfaceDestroyedListenerWithIfaceName {
        private final String mIfaceName;
        @Mock private final InterfaceDestroyedListener mListener;

        InterfaceDestroyedListenerWithIfaceName(
                String ifaceName, InterfaceDestroyedListener listener) {
            mIfaceName = ifaceName;
            mListener = listener;
        }

        public void validate() {
            verify(mListener).onDestroyed(mIfaceName);
        }
    }

    // Answer objects
    private class GetChipIdsAnswer extends MockAnswerUtil.AnswerWithArguments {
        private boolean mSuccess;
        private ArrayList<Integer> mChipIds;

        GetChipIdsAnswer(boolean success, ArrayList<Integer> chipIds) {
            mSuccess = success;
            mChipIds = chipIds;
        }

        public List<Integer> answer() {
            List<Integer> ret = mSuccess ? mChipIds : null;
            return ret;
        }
    }

    private class GetChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private boolean mSuccess;
        private WifiChip mChip;

        GetChipAnswer(boolean success, WifiChip chip) {
            mSuccess = success;
            mChip = chip;
        }

        public WifiChip answer(int chipId) {
            return mSuccess ? mChip : null;
        }
    }

    private class GetCapabilitiesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetCapabilitiesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiChip.Response<Long> answer() {
            WifiChip.Response<Long> response =
                    new WifiChip.Response<>(mChipMockBase.chipCapabilities);
            response.setStatusCode(mChipMockBase.allowGetCapsBeforeIfaceCreated
                    ? WifiHal.WIFI_STATUS_SUCCESS : WifiHal.WIFI_STATUS_ERROR_UNKNOWN);
            return response;
        }
    }

    private class GetIdAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetIdAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public int answer() {
            return mChipMockBase.chipId;
        }
    }

    private class GetAvailableModesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetAvailableModesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public List<WifiChip.ChipMode> answer() {
            return mChipMockBase.availableModes;
        }
    }

    private class GetModeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetModeAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiChip.Response<Integer> answer() {
            WifiChip.Response<Integer> response = new WifiChip.Response<>(mChipMockBase.chipModeId);
            response.setStatusCode(mChipMockBase.chipModeValid
                    ? WifiHal.WIFI_STATUS_SUCCESS : WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE);
            return response;
        }
    }

    private class ConfigureChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        ConfigureChipAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public boolean answer(int chipMode) {
            mChipMockBase.chipModeValid = true;
            mChipMockBase.chipModeId = chipMode;
            mChipMockBase.onChipConfigured();
            return true;
        }
    }

    private class GetXxxIfaceNamesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private @WifiChip.IfaceType int mIfaceType;

        GetXxxIfaceNamesAnswer(ChipMockBase chipMockBase, @WifiChip.IfaceType int ifaceType) {
            mChipMockBase = chipMockBase;
            mIfaceType = ifaceType;
        }

        public List<String> answer() {
            return mChipMockBase.interfaceNames.get(mIfaceType);
        }
    }

    private class GetStaIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetStaIfaceAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiStaIface answer(String name) {
            return (WifiStaIface)
                    mChipMockBase.interfacesByName.get(WifiChip.IFACE_TYPE_STA).get(name);
        }
    }

    private class GetApIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetApIfaceAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiApIface answer(String name) {
            return (WifiApIface)
                    mChipMockBase.interfacesByName.get(WifiChip.IFACE_TYPE_AP).get(name);
        }
    }

    private class GetP2pIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetP2pIfaceAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiP2pIface answer(String name) {
            return (WifiP2pIface)
                    mChipMockBase.interfacesByName.get(WifiChip.IFACE_TYPE_P2P).get(name);
        }
    }

    private class GetNanIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetNanIfaceAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiNanIface answer(String name) {
            return (WifiNanIface)
                    mChipMockBase.interfacesByName.get(WifiChip.IFACE_TYPE_NAN).get(name);
        }
    }

    private class CreateXxxIfaceAnswer  extends MockAnswerUtil.AnswerWithArguments {
        protected ChipMockBase mChipMockBase;
        protected boolean mSuccess;
        protected WifiInterface mWifiIface;
        private @WifiChip.IfaceType int mType;

        CreateXxxIfaceAnswer(ChipMockBase chipMockBase, boolean success, WifiInterface wifiIface,
                @WifiChip.IfaceType int type) {
            mChipMockBase = chipMockBase;
            mSuccess = success;
            mWifiIface = wifiIface;
            mType = type;
        }

        protected void addInterfaceInfo() {
            if (mSuccess) {
                try {
                    String ifaceName = getName(mWifiIface);
                    mChipMockBase.interfaceNames.get(mType).add(ifaceName);
                    mChipMockBase.interfacesByName.get(mType).put(ifaceName, mWifiIface);
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    }

    private class CreateStaIfaceAnswer extends CreateXxxIfaceAnswer {
        CreateStaIfaceAnswer(ChipMockBase chipMockBase, boolean success, WifiInterface wifiIface) {
            super(chipMockBase, success, wifiIface, WifiChip.IFACE_TYPE_STA);
        }

        public WifiStaIface answer() {
            addInterfaceInfo();
            return mSuccess ? (WifiStaIface) mWifiIface : null;
        }
    }

    private class CreateApIfaceAnswer extends CreateXxxIfaceAnswer {
        CreateApIfaceAnswer(ChipMockBase chipMockBase, boolean success, WifiInterface wifiIface) {
            super(chipMockBase, success, wifiIface, WifiChip.IFACE_TYPE_AP);
        }

        public WifiApIface answer(@NonNull List<OuiKeyedData> vendorData) {
            addInterfaceInfo();
            return mSuccess ? (WifiApIface) mWifiIface : null;
        }
    }

    private class CreateP2pIfaceAnswer extends CreateXxxIfaceAnswer {
        CreateP2pIfaceAnswer(ChipMockBase chipMockBase, boolean success, WifiInterface wifiIface) {
            super(chipMockBase, success, wifiIface, WifiChip.IFACE_TYPE_P2P);
        }

        public WifiP2pIface answer() {
            addInterfaceInfo();
            return mSuccess ? (WifiP2pIface) mWifiIface : null;
        }
    }

    private class CreateNanIfaceAnswer extends CreateXxxIfaceAnswer {
        CreateNanIfaceAnswer(ChipMockBase chipMockBase, boolean success, WifiInterface wifiIface) {
            super(chipMockBase, success, wifiIface, WifiChip.IFACE_TYPE_NAN);
        }

        public WifiNanIface answer() {
            addInterfaceInfo();
            return mSuccess ? (WifiNanIface) mWifiIface : null;
        }
    }

    private class CreateRttControllerAnswer extends MockAnswerUtil.AnswerWithArguments {
        private final ChipMockBase mChipMockBase;
        private final WifiRttController mRttController;

        CreateRttControllerAnswer(ChipMockBase chipMockBase, WifiRttController rttController) {
            mChipMockBase = chipMockBase;
            mRttController = rttController;
        }

        public WifiRttController answer() {
            if (mChipMockBase.chipModeIdValidForRtt == mChipMockBase.chipModeId) {
                return mRttController;
            } else {
                return null;
            }
        }
    }

    private class RemoveXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private int mType;

        RemoveXxxIfaceAnswer(ChipMockBase chipMockBase, @WifiChip.IfaceType int type) {
            mChipMockBase = chipMockBase;
            mType = type;
        }

        public boolean answer(String ifname) {
            try {
                if (!mChipMockBase.interfaceNames.get(mType).remove(ifname)) {
                    return false;
                }
                if (mChipMockBase.interfacesByName.get(mType).remove(ifname) == null) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        }
    }

    private class GetNameAnswer extends MockAnswerUtil.AnswerWithArguments {
        private String mName;

        GetNameAnswer(String name) {
            mName = name;
        }

        public String answer() {
            return mName;
        }
    }

    private class GetSupportedRadioCombinationsAnswer
            extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetSupportedRadioCombinationsAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public List<WifiChip.WifiRadioCombination> answer() {
            return mChipMockBase.chipSupportedRadioCombinations;
        }
    }

    private class GetBridgedInstancesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private String mName;

        GetBridgedInstancesAnswer(ChipMockBase chipMockBase, String name) {
            mChipMockBase = chipMockBase;
            mName = name;
        }

        public List<String> answer() {
            ArrayList<String> bridgedApInstances =
                    mChipMockBase.bridgedApInstancesByName.get(mName);
            if (bridgedApInstances == null) {
                bridgedApInstances = new ArrayList<>();
            }
            return bridgedApInstances;
        }
    }

    // chip configuration

    WifiChip.ChipConcurrencyCombinationLimit createConcurrencyComboLimit(
            int maxIfaces, Integer... ifaceTypes) {
        // Assume we will always get at least 1 iface type.
        return new WifiChip.ChipConcurrencyCombinationLimit(maxIfaces, Arrays.asList(ifaceTypes));
    }

    WifiChip.ChipConcurrencyCombination createConcurrencyCombo(
            WifiChip.ChipConcurrencyCombinationLimit... limits) {
        return new WifiChip.ChipConcurrencyCombination(Arrays.asList(limits));
    }

    WifiChip.ChipMode createChipMode(int modeId, WifiChip.ChipConcurrencyCombination... combos) {
        return new WifiChip.ChipMode(modeId, Arrays.asList(combos));
    }

    private static final int CHIP_MOCK_V1 = 0;
    private static final int CHIP_MOCK_V2 = 1;
    private static final int CHIP_MOCK_V3 = 2;
    private static final int CHIP_MOCK_V4 = 3;
    private static final int CHIP_MOCK_V5 = 4;
    private static final int CHIP_MOCK_V6 = 6;

    private class ChipMockBase {
        public int chipMockId;

        public WifiChip chip;
        public int chipId;
        public boolean chipModeValid = false;
        public int chipModeId = -1000;
        public int chipModeIdValidForRtt = -1; // single chip mode ID where RTT can be created
        public long chipCapabilities = 0L;
        public boolean allowGetCapsBeforeIfaceCreated = true;
        public List<WifiChip.WifiRadioCombination> chipSupportedRadioCombinations = null;
        public Map<Integer, ArrayList<String>> interfaceNames = new HashMap<>();
        public Map<Integer, Map<String, WifiInterface>> interfacesByName = new HashMap<>();
        public Map<String, ArrayList<String>> bridgedApInstancesByName = new HashMap<>();

        public ArrayList<WifiChip.ChipMode> availableModes;

        void initialize() throws Exception {
            chip = mock(WifiChip.class);

            interfaceNames.put(WifiChip.IFACE_TYPE_STA, new ArrayList<>());
            interfaceNames.put(WifiChip.IFACE_TYPE_AP, new ArrayList<>());
            interfaceNames.put(WifiChip.IFACE_TYPE_P2P, new ArrayList<>());
            interfaceNames.put(WifiChip.IFACE_TYPE_NAN, new ArrayList<>());

            interfacesByName.put(WifiChip.IFACE_TYPE_STA, new HashMap<>());
            interfacesByName.put(WifiChip.IFACE_TYPE_AP, new HashMap<>());
            interfacesByName.put(WifiChip.IFACE_TYPE_P2P, new HashMap<>());
            interfacesByName.put(WifiChip.IFACE_TYPE_NAN, new HashMap<>());

            when(chip.registerCallback(any(WifiChip.Callback.class))).thenReturn(true);
            when(chip.configureChip(anyInt())).thenAnswer(new ConfigureChipAnswer(this));
            doAnswer(new GetCapabilitiesAnswer(this))
                    .when(chip).getCapabilitiesBeforeIfacesExist();
            doAnswer(new GetIdAnswer(this)).when(chip).getId();
            doAnswer(new GetModeAnswer(this)).when(chip).getMode();
            doAnswer(new GetXxxIfaceNamesAnswer(this, WifiChip.IFACE_TYPE_STA))
                    .when(chip).getStaIfaceNames();
            doAnswer(new GetXxxIfaceNamesAnswer(this, WifiChip.IFACE_TYPE_AP))
                    .when(chip).getApIfaceNames();
            doAnswer(new GetXxxIfaceNamesAnswer(this, WifiChip.IFACE_TYPE_P2P))
                    .when(chip).getP2pIfaceNames();
            doAnswer(new GetXxxIfaceNamesAnswer(this, WifiChip.IFACE_TYPE_NAN))
                    .when(chip).getNanIfaceNames();
            doAnswer(new GetStaIfaceAnswer(this)).when(chip).getStaIface(anyString());
            doAnswer(new GetApIfaceAnswer(this)).when(chip).getApIface(anyString());
            doAnswer(new GetP2pIfaceAnswer(this)).when(chip).getP2pIface(anyString());
            doAnswer(new GetNanIfaceAnswer(this)).when(chip).getNanIface(anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, WifiChip.IFACE_TYPE_STA))
                    .when(chip).removeStaIface(anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, WifiChip.IFACE_TYPE_AP))
                    .when(chip).removeApIface(anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, WifiChip.IFACE_TYPE_P2P))
                    .when(chip).removeP2pIface(anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, WifiChip.IFACE_TYPE_NAN))
                    .when(chip).removeNanIface(anyString());
            when(chip.removeIfaceInstanceFromBridgedApIface(anyString(), anyString()))
                    .thenAnswer((invocation) -> {
                        this.bridgedApInstancesByName.get(invocation.getArgument(0))
                                .remove(invocation.getArgument(1));
                        return true;
                    });

            doAnswer(new CreateRttControllerAnswer(this, mRttControllerMock))
                    .when(chip).createRttController();
            when(mRttControllerMock.setup()).thenReturn(true);
            when(mRttControllerMock.validate()).thenReturn(true);
            doAnswer(new GetSupportedRadioCombinationsAnswer(this))
                    .when(chip).getSupportedRadioCombinations();
        }

        void onChipConfigured() {
            // Do nothing
        }
    }

    // test chip configuration V1:
    // mode: STA + (NAN || P2P)
    // mode: AP
    private class TestChipV1 extends ChipMockBase {
        static final int STA_CHIP_MODE_ID = 0;
        static final int AP_CHIP_MODE_ID = 1;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 10,"
                + "        \"chipCapabilities\": 0,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 0,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [3, 4]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            },"
                + "            {"
                + "                \"id\": 1,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [1]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V1;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 10;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode 0: 1xSTA + 1x{P2P,NAN}
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_P2P,
                            WifiChip.IFACE_CONCURRENCY_TYPE_NAN));
            availableModes.add(createChipMode(STA_CHIP_MODE_ID, combo1));

            // Mode 1: 1xAP
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            availableModes.add(createChipMode(AP_CHIP_MODE_ID, combo2));

            chipModeIdValidForRtt = STA_CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V2:
    // mode: STA + (STA || AP) + (NAN || P2P)
    private class TestChipV2 extends ChipMockBase {
        // only mode (different number from any in TestChipV1 so can catch test errors)
        static final int CHIP_MODE_ID = 5;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 12,"
                + "        \"chipCapabilities\": 0,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 5,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0, 1]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [3, 4]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V2;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 12;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode (only one): 1xSTA + 1x{STA,AP} + 1x{P2P,NAN}
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA,
                            WifiChip.IFACE_CONCURRENCY_TYPE_AP),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_P2P,
                            WifiChip.IFACE_CONCURRENCY_TYPE_NAN));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V3:
    // mode:
    //    STA + (STA || AP)
    //    STA + (NAN || P2P)
    private class TestChipV3 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 7;

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V3;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 15;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode (only one): 1xSTA + 1x{STA,AP}, 1xSTA + 1x{P2P,NAN}
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA,
                            WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_P2P,
                            WifiChip.IFACE_CONCURRENCY_TYPE_NAN));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1, combo2));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V4:
    // mode:
    //    STA + AP
    //    STA + (NAN || P2P)
    private class TestChipV4 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 15;

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V4;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 23;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode (only one): 1xSTA + 1xAP, 1xSTA + 1x{P2P,NAN}
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_P2P,
                            WifiChip.IFACE_CONCURRENCY_TYPE_NAN));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1, combo2));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V5 for 60GHz:
    // mode:
    //    STA + AP
    //    STA + (NAN || P2P)
    private class TestChipV5 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 3;
        static final int CHIP_ID = 5;

        void initialize() throws Exception {
            super.initialize();
            chipMockId = CHIP_MOCK_V5;

            chipCapabilities |= WifiManager.WIFI_FEATURE_INFRA_60G;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = CHIP_ID;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();
            List<WifiChip.ChipConcurrencyCombination> combos = new ArrayList<>();

            // Mode (only one): 1xSTA + 1xAP, 1xSTA + 1x{P2P,NAN}
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            combos.add(combo1);

            // Simulate HIDL 1.0 to 1.6 conversion for the 1xSTA + 1xAP combo.
            if (mIsBridgedSoftApSupported) {
                List<WifiChip.ChipConcurrencyCombinationLimit> limits = new ArrayList<>();
                limits.add(createConcurrencyComboLimit(
                        1, WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED));
                if (mIsStaWithBridgedSoftApConcurrencySupported) {
                    limits.add(createConcurrencyComboLimit(
                            1, WifiChip.IFACE_CONCURRENCY_TYPE_STA));
                }
                WifiChip.ChipConcurrencyCombination combo2 =
                        new WifiChip.ChipConcurrencyCombination(limits);
                combos.add(combo2);
            }

            WifiChip.ChipConcurrencyCombination combo3 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_P2P,
                            WifiChip.IFACE_CONCURRENCY_TYPE_NAN));
            combos.add(combo3);
            availableModes.add(new WifiChip.ChipMode(CHIP_MODE_ID, combos));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V6 for Bridged AP:
    // mode:
    //    STA + (AP || AP_BRIDGED)
    //    STA + (NAN || P2P)
    private class TestChipV6 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 60;
        static final int CHIP_ID = 6;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 6,"
                + "        \"chipCapabilities\": 0,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 60,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [1, 2]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();
            chipMockId = CHIP_MOCK_V6;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = CHIP_ID;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode 60 (only one): 1xSTA + 1x{AP,AP_BRIDGED}, 1xSTA + 1x{P2P,NAN}
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP,
                            WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED));
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_P2P,
                            WifiChip.IFACE_CONCURRENCY_TYPE_NAN));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1, combo2));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V7 for testing interface priorities for mode switching
    // mode 0: STA + STA
    // mode 1: AP
    // mode 2: STA + AP || AP + AP_BRIDGED
    private class TestChipV7 extends ChipMockBase {
        static final int DUAL_STA_CHIP_MODE_ID = 71;
        static final int AP_CHIP_MODE_ID = 72;
        static final int AP_AP_BRIDGED_CHIP_MODE_ID = 73;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 70;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode: 2xSTA
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(2, WifiChip.IFACE_CONCURRENCY_TYPE_STA));
            availableModes.add(createChipMode(DUAL_STA_CHIP_MODE_ID, combo1));

            // Mode: 1xAP
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            availableModes.add(createChipMode(AP_CHIP_MODE_ID, combo2));

            // Mode: 1xSTA + 1xAP, 1xAP + 1xAP_BRIDGED
            WifiChip.ChipConcurrencyCombination combo3 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            WifiChip.ChipConcurrencyCombination combo4 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED));
            availableModes.add(createChipMode(AP_AP_BRIDGED_CHIP_MODE_ID, combo3, combo4));

            chipModeIdValidForRtt = DUAL_STA_CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V8 for testing AP/AP_BRIDGED deletion
    // mode 0: STA + AP || AP + AP_BRIDGED
    private class TestChipV8 extends ChipMockBase {
        static final int CHIP_MODE_ID = 71;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 80;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // initialize placeholder chip modes
            availableModes = new ArrayList<>();
            List<WifiChip.ChipConcurrencyCombinationLimit> limits = new ArrayList<>();

            // Mode: 1xSTA + 1xAP, 1xAP + 1xAP_BRIDGED
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1, combo2));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }

    // test chip configuration V9 for Bridged AP without STA + Bridged AP concurrency:
    // mode:
    //    (STA + AP) || (AP_BRIDGED)
    private class TestChipV9 extends ChipMockBase {
        static final int CHIP_MODE_ID = 90;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 9;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // Initialize availableModes
            availableModes = new ArrayList<>();

            // Mode 90 (only one): (1xSTA + 1xAP) || (1xAP_BRIDGED)
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            WifiChip.ChipConcurrencyCombination combo2 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED));

            // Add a combo which doesn't allow any AP.
            WifiChip.ChipConcurrencyCombination combo3 = createConcurrencyCombo(
                    createConcurrencyComboLimit(2, WifiChip.IFACE_CONCURRENCY_TYPE_STA));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1, combo2, combo3));

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }
    }


    // Test chip configuration V10 to test available modes from driver:
    // Default:
    //     mode: STA
    // After driver ready:
    //     mode: STA + AP
    private class TestChipV10 extends ChipMockBase {
        static final int CHIP_MODE_ID = 100;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 10,"
                + "        \"chipCapabilities\": -1,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 100,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [1]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 10;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(true, chipIds)).when(mWifiMock).getChipIds();
            doAnswer(new GetChipAnswer(true, chip)).when(mWifiMock).getChip(anyInt());

            // initialize placeholder chip modes
            configureDefaultAvailableModes();

            chipModeIdValidForRtt = CHIP_MODE_ID;
            doAnswer(new GetAvailableModesAnswer(this)).when(chip).getAvailableModes();
        }

        // STA
        void configureDefaultAvailableModes() {
            availableModes = new ArrayList<>();
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1));
        }

        // STA + AP
        void configureDriverAvailableModes() {
            availableModes = new ArrayList<>();
            WifiChip.ChipConcurrencyCombination combo1 = createConcurrencyCombo(
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_STA),
                    createConcurrencyComboLimit(1, WifiChip.IFACE_CONCURRENCY_TYPE_AP));
            availableModes.add(createChipMode(CHIP_MODE_ID, combo1));
        }

        @Override
        void onChipConfigured() {
            configureDriverAvailableModes();
        }
    }
}
