/*
 * Copyright 2018 The Android Open Source Project
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

import static android.net.wifi.WifiManager.EXTRA_PREVIOUS_WIFI_STATE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_STATE;
import static android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_UNKNOWN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.SelfRecovery.REASON_IFACE_ADDED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.RegistrationManager;
import android.util.LocalLog;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ClientModeManagerBroadcastQueue.QueuedBroadcast;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Unit tests for {@link ConcreteClientModeManager}.
 */
@SmallTest
public class ConcreteClientModeManagerTest extends WifiBaseTest {
    private static final String TAG = "ClientModeManagerTest";
    private static final String TEST_INTERFACE_NAME = "testif0";
    private static final String TEST_ADDED_INTERFACE_NAME = "testif1";
    private static final String OTHER_INTERFACE_NAME = "notTestIf";
    private static final int TEST_WIFI_OFF_DEFERRING_TIME_MS = 4000;
    private static final int TEST_ACTIVE_SUBSCRIPTION_ID = 1;
    private static final WorkSource TEST_WORKSOURCE = new WorkSource();
    private static final WorkSource TEST_WORKSOURCE2 = new WorkSource();
    private static final int TEST_UID = 435546654;

    TestLooper mLooper;

    ConcreteClientModeManager mClientModeManager;

    @Mock Context mContext;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiNative mWifiNative;
    @Mock Clock mClock;
    @Mock ActiveModeManager.Listener<ConcreteClientModeManager> mListener;
    @Mock WakeupController mWakeupController;
    @Mock WifiInjector mWifiInjector;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock WifiDiagnostics mWifiDiagnostics;
    @Mock ClientModeImpl mClientModeImpl;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock PersistableBundle mCarrierConfigBundle;
    @Mock ImsMmTelManager mImsMmTelManager;
    @Mock ConnectivityManager mConnectivityManager;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock SubscriptionInfo mActiveSubscriptionInfo;
    @Mock SelfRecovery mSelfRecovery;
    @Mock WifiGlobals mWifiGlobals;
    @Mock ScanOnlyModeImpl mScanOnlyModeImpl;
    @Mock DefaultClientModeManager mDefaultClientModeManager;
    @Mock ClientModeManagerBroadcastQueue mBroadcastQueue;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock LocalLog mLocalLog;

    private RegistrationManager.RegistrationCallback mImsMmTelManagerRegistrationCallback = null;
    private @RegistrationManager.ImsRegistrationState int mCurrentImsRegistrationState =
            RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED;
    private @AccessNetworkConstants.TransportType int mCurrentImsConnectionType =
            AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    private NetworkRequest mImsRequest = null;
    private NetworkCallback mImsNetworkCallback = null;
    private long mElapsedSinceBootMillis = 0L;
    private List<SubscriptionInfo> mSubscriptionInfoList = new ArrayList<>();
    private MockResources mResources;

    private MockitoSession mStaticMockSession = null;

    final ArgumentCaptor<WifiNative.InterfaceCallback> mInterfaceCallbackCaptor =
            ArgumentCaptor.forClass(WifiNative.InterfaceCallback.class);
    final ArgumentCaptor<WifiNative.InterfaceEventCallback> mInterfaceEventCallbackCaptor =
            ArgumentCaptor.forClass(WifiNative.InterfaceEventCallback.class);

    /**
     * If mContext is reset, call it again to ensure system services could be retrieved
     * from the context.
     */
    private void setUpSystemServiceForContext() {
        when(mContext.getSystemService(eq(CarrierConfigManager.class)))
                .thenReturn(mCarrierConfigManager);
        when(mContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mSubscriptionManager);
        when(mContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mConnectivityManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiInjector.makeClientModeImpl(any(), any(), anyBoolean()))
                .thenReturn(mClientModeImpl);
        when(mWifiInjector.makeScanOnlyModeImpl(any())).thenReturn(mScanOnlyModeImpl);
    }

    /*
     * Use this helper to move mock looper and clock together.
     */
    private void moveTimeForward(long timeMillis) {
        mLooper.moveTimeForward(timeMillis);
        mElapsedSinceBootMillis += timeMillis;
    }


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Prepare data
        mResources = new MockResources();
        mResources.setInteger(R.integer.config_wifiDelayDisconnectOnImsLostMs, 0);
        mSubscriptionInfoList.add(mActiveSubscriptionInfo);

        setUpSystemServiceForContext();

        /**
         * default mock for IMS deregistration:
         * * No wifi calling
         * * No network
         * * no deferring time for wifi off
         */
        mStaticMockSession = mockitoSession()
            .mockStatic(ImsMmTelManager.class)
            .mockStatic(SubscriptionManager.class)
            .startMocking();
        lenient().when(ImsMmTelManager.createForSubscriptionId(eq(TEST_ACTIVE_SUBSCRIPTION_ID)))
                .thenReturn(mImsMmTelManager);
        lenient().when(SubscriptionManager.isValidSubscriptionId(eq(TEST_ACTIVE_SUBSCRIPTION_ID)))
                .thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(Executor executor, RegistrationManager.RegistrationCallback c) {
                mImsMmTelManagerRegistrationCallback = c;
                // When the callback is registered, it will initiate the callback c to
                // be called with the current registration state.
                switch (mCurrentImsRegistrationState) {
                    case RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED:
                        c.onUnregistered(null);
                        break;
                    case RegistrationManager.REGISTRATION_STATE_REGISTERED:
                        c.onRegistered(mCurrentImsConnectionType);
                        break;
                }
            }
        }).when(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        doAnswer(new AnswerWithArguments() {
            public void answer(RegistrationManager.RegistrationCallback c) {
                if (mImsMmTelManagerRegistrationCallback == c) {
                    mImsMmTelManagerRegistrationCallback = null;
                }
            }
        }).when(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt())).thenReturn(false);

        when(mActiveSubscriptionInfo.getSubscriptionId()).thenReturn(TEST_ACTIVE_SUBSCRIPTION_ID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(mSubscriptionInfoList);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mCarrierConfigBundle);
        when(mCarrierConfigBundle
                .getInt(eq(CarrierConfigManager.Ims.KEY_WIFI_OFF_DEFERRING_TIME_MILLIS_INT)))
                .thenReturn(0);
        doAnswer(new AnswerWithArguments() {
            public void answer(NetworkRequest req, NetworkCallback callback, Handler handler) {
                mImsRequest = req;
                mImsNetworkCallback = callback;
            }
        }).when(mConnectivityManager).registerNetworkCallback(any(), any(), any());
        doAnswer(new AnswerWithArguments() {
            public void answer(NetworkCallback callback) {
                if (mImsNetworkCallback == callback) mImsNetworkCallback = null;
            }
        }).when(mConnectivityManager).unregisterNetworkCallback(any(NetworkCallback.class));
        doAnswer(new AnswerWithArguments() {
            public long answer() {
                return mElapsedSinceBootMillis;
            }
        }).when(mClock).getElapsedSinceBootMillis();
        when(mWifiNative.replaceStaIfaceRequestorWs(TEST_INTERFACE_NAME, TEST_WORKSOURCE))
                .thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(ClientModeManager manager, QueuedBroadcast broadcast) {
                broadcast.send();
            }
        }).when(mBroadcastQueue).queueOrSendBroadcast(any(), any());
        when(mDeviceConfigFacade.isInterfaceFailureBugreportEnabled()).thenReturn(true);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mWifiInjector.getWifiDiagnostics()).thenReturn(mWifiDiagnostics);
        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mWifiInjector.getWifiHandlerLocalLog()).thenReturn(mLocalLog);
        mLooper = new TestLooper();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    private ConcreteClientModeManager createClientModeManager(ActiveModeManager.ClientRole role) {
        return new ConcreteClientModeManager(mContext, mLooper.getLooper(), mClock, mWifiNative,
                mListener, mWifiMetrics, mWakeupController, mWifiInjector, mSelfRecovery,
                mWifiGlobals, mDefaultClientModeManager, 0, TEST_WORKSOURCE, role,
                mBroadcastQueue, false);
    }

    private void startClientInScanOnlyModeAndVerifyEnabled() throws Exception {
        when(mWifiNative.setupInterfaceForClientInScanMode(any(), any(), any()))
                .thenReturn(TEST_INTERFACE_NAME);
        mClientModeManager = createClientModeManager(ROLE_CLIENT_SCAN_ONLY);
        mLooper.dispatchAll();

        verify(mWifiNative).setupInterfaceForClientInScanMode(
                mInterfaceCallbackCaptor.capture(), eq(TEST_WORKSOURCE), eq(mClientModeManager));
        verify(mWifiNative).setWifiNativeInterfaceEventCallback(
                mInterfaceEventCallbackCaptor.capture());
        verify(mWifiInjector, never()).makeClientModeImpl(any(), any(), anyBoolean());

        // now mark the interface as up
        mInterfaceCallbackCaptor.getValue().onUp(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();

        // DeferStopHandler(): ConnectivityManager.class
        verify(mContext).getSystemService(eq(ConnectivityManager.class));
        verify(mContext).getResources();

        // Ensure that no public broadcasts were sent.
        verifyNoMoreInteractions(mContext);
        verify(mListener).onStarted(mClientModeManager);
        verify(mWifiNative).setScanMode(TEST_INTERFACE_NAME, true);
    }

    private void startClientInConnectModeAndVerifyEnabled() throws Exception {
        when(mWifiNative.setupInterfaceForClientInScanMode(any(), any(), any()))
                .thenReturn(TEST_INTERFACE_NAME);
        when(mWifiNative.switchClientInterfaceToConnectivityMode(any(), any()))
                .thenReturn(true);
        mClientModeManager = createClientModeManager(ROLE_CLIENT_PRIMARY);
        mLooper.dispatchAll();

        verify(mWifiNative).setupInterfaceForClientInScanMode(
                mInterfaceCallbackCaptor.capture(), eq(TEST_WORKSOURCE), eq(mClientModeManager));
        verify(mWifiNative).setWifiNativeInterfaceEventCallback(
                mInterfaceEventCallbackCaptor.capture());
        verify(mWifiNative).switchClientInterfaceToConnectivityMode(
                TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mWifiInjector)
                .makeClientModeImpl(eq(TEST_INTERFACE_NAME), eq(mClientModeManager), anyBoolean());

        // now mark the interface as up
        mInterfaceCallbackCaptor.getValue().onUp(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();

        verify(mClientModeImpl).onUpChanged(true);
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce()).sendStickyBroadcastAsUser(intentCaptor.capture(),
                eq(UserHandle.ALL));

        List<Intent> intents = intentCaptor.getAllValues();
        assertEquals(2, intents.size());
        Log.d(TAG, "captured intents: " + intents);
        checkWifiConnectModeStateChangedBroadcast(intents.get(0), WIFI_STATE_ENABLING,
                WIFI_STATE_DISABLED);
        checkWifiConnectModeStateChangedBroadcast(intents.get(1), WIFI_STATE_ENABLED,
                WIFI_STATE_ENABLING);
        verify(mActiveModeWarden).setWifiStateForApiCalls(WIFI_STATE_ENABLED);

        verify(mListener).onStarted(mClientModeManager);
    }

    private void checkWifiConnectModeStateChangedBroadcast(
            Intent intent, int expectedCurrentState, int expectedPrevState) {
        String action = intent.getAction();
        assertEquals(WIFI_STATE_CHANGED_ACTION, action);
        int currentState = intent.getIntExtra(EXTRA_WIFI_STATE, WIFI_STATE_UNKNOWN);
        assertEquals(expectedCurrentState, currentState);
        int prevState = intent.getIntExtra(EXTRA_PREVIOUS_WIFI_STATE, WIFI_STATE_UNKNOWN);
        assertEquals(expectedPrevState, prevState);
    }

    private void verifyConnectModeNotificationsForCleanShutdown(int fromState) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce())
                .sendStickyBroadcastAsUser(intentCaptor.capture(), eq(UserHandle.ALL));

        List<Intent> intents = intentCaptor.getAllValues();
        assertTrue(intents.size() >= 2);
        checkWifiConnectModeStateChangedBroadcast(intents.get(intents.size() - 2),
                WIFI_STATE_DISABLING, fromState);
        checkWifiConnectModeStateChangedBroadcast(intents.get(intents.size() - 1),
                WIFI_STATE_DISABLED, WIFI_STATE_DISABLING);
        verify(mActiveModeWarden).setWifiStateForApiCalls(WIFI_STATE_DISABLED);
    }

    private void verifyConnectModeNotificationsForFailure() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce())
                .sendStickyBroadcastAsUser(intentCaptor.capture(), eq(UserHandle.ALL));

        List<Intent> intents = intentCaptor.getAllValues();
        assertEquals(2, intents.size());
        checkWifiConnectModeStateChangedBroadcast(intents.get(0), WIFI_STATE_DISABLING,
                WIFI_STATE_UNKNOWN);
        checkWifiConnectModeStateChangedBroadcast(intents.get(1), WIFI_STATE_DISABLED,
                WIFI_STATE_DISABLING);
        verify(mActiveModeWarden).setWifiStateForApiCalls(WIFI_STATE_DISABLED);
    }

    /**
     * ClientMode start sets up an interface in ClientMode.
     */
    @Test
    public void clientInConnectModeStartCreatesClientInterface() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
    }

    /**
     * ClientMode start sets up an interface in ClientMode.
     */
    @Test
    public void clientInScanOnlyModeStartCreatesClientInterface() throws Exception {
        startClientInScanOnlyModeAndVerifyEnabled();

        mClientModeManager.getFactoryMacAddress();
        // in scan only mode, should get value from ScanOnlyModeImpl
        verify(mScanOnlyModeImpl).getFactoryMacAddress();
    }

    /**
     * Adding a new interface will trigger self recovery.
     */
    @Test
    public void clientInScanOnlyModeInterfaceHotPlug() throws Exception {
        startClientInScanOnlyModeAndVerifyEnabled();

        mInterfaceEventCallbackCaptor.getValue()
                .onInterfaceAdded(TEST_ADDED_INTERFACE_NAME);
        mLooper.dispatchAll();
        verify(mSelfRecovery).trigger(eq(REASON_IFACE_ADDED));
    }

    /**
     * Adding an interface after the previous one has been destroyed (e.g. due to Suspend-to-RAM)
     * triggers self recovery.
     */
    @Test
    public void interfaceDestroyedAndAdded() throws Exception {
        startClientInScanOnlyModeAndVerifyEnabled();
        mInterfaceCallbackCaptor.getValue().onDestroyed(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();

        mInterfaceEventCallbackCaptor.getValue()
                .onInterfaceAdded(TEST_ADDED_INTERFACE_NAME);
        mLooper.dispatchAll();
        verify(mSelfRecovery).trigger(eq(REASON_IFACE_ADDED));
    }

    /**
     * Switch ClientModeManager from ScanOnly mode To Connect mode.
     */
    @Test
    public void switchFromScanOnlyModeToConnectMode() throws Exception {
        startClientInScanOnlyModeAndVerifyEnabled();

        when(mWifiNative.switchClientInterfaceToConnectivityMode(any(), any()))
                .thenReturn(true);
        mClientModeManager.setRole(ActiveModeManager.ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiNative).setScanMode(TEST_INTERFACE_NAME, false);

        verify(mWifiInjector)
                .makeClientModeImpl(eq(TEST_INTERFACE_NAME), eq(mClientModeManager), anyBoolean());

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce()).sendStickyBroadcastAsUser(intentCaptor.capture(),
                eq(UserHandle.ALL));

        List<Intent> intents = intentCaptor.getAllValues();
        assertEquals(2, intents.size());
        Log.d(TAG, "captured intents: " + intents);
        checkWifiConnectModeStateChangedBroadcast(intents.get(0), WIFI_STATE_ENABLING,
                WIFI_STATE_DISABLED);
        checkWifiConnectModeStateChangedBroadcast(intents.get(1), WIFI_STATE_ENABLED,
                WIFI_STATE_ENABLING);
        verify(mActiveModeWarden).setWifiStateForApiCalls(WIFI_STATE_ENABLED);

        verify(mListener).onStarted(mClientModeManager);
        verify(mListener).onRoleChanged(mClientModeManager);

        mClientModeManager.getFactoryMacAddress();
        // in client mode, should get value from ClientModeImpl
        verify(mClientModeImpl).getFactoryMacAddress();
    }

    /**
     * Verify that no more public broadcasts are sent out after
     * setWifiStateChangeBroadcastEnabled(false) is called.
     */
    @Test
    public void testDisableWifiStateChangedBroadcasts() throws Exception {
        startClientInConnectModeAndVerifyEnabled();

        mClientModeManager.setWifiStateChangeBroadcastEnabled(false);
        mClientModeManager.stop();
        mLooper.dispatchAll();

        verify(mClientModeImpl).stop();
        verify(mActiveModeWarden, never()).setWifiStateForApiCalls(WIFI_STATE_DISABLED);

        // Ensure that only public broadcasts for the "start" events were sent.
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce()).sendStickyBroadcastAsUser(intentCaptor.capture(),
                eq(UserHandle.ALL));

        List<Intent> intents = intentCaptor.getAllValues();
        assertEquals(2, intents.size());
        Log.d(TAG, "captured intents: " + intents);
        checkWifiConnectModeStateChangedBroadcast(intents.get(0), WIFI_STATE_ENABLING,
                WIFI_STATE_DISABLED);
        checkWifiConnectModeStateChangedBroadcast(intents.get(1), WIFI_STATE_ENABLED,
                WIFI_STATE_ENABLING);
    }

    @Test
    public void testSwitchFromConnectModeToScanOnlyModeFail() throws Exception {
        startClientInConnectModeAndVerifyEnabled();

        // mock wifi native role switch failure
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(false);
        InOrder inOrder = inOrder(mContext);
        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // Verify callback received for failed transition
        verify(mListener).onStartFailure(mClientModeManager);

        // First check WIFI_STATE_DISABLING is broadcast as the CMM tries to switch to scan only
        // mode
        inOrder.verify(mContext, atLeastOnce()).sendStickyBroadcastAsUser(
                argThat(intent ->
                        intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                                == WifiManager.WIFI_STATE_DISABLING),
                any());

        // Then verify WIFI_STATE_DISABLED is broadcast due to WifiNative failing
        // to switch CMM role.
        inOrder.verify(mContext).sendStickyBroadcastAsUser(
                argThat(intent ->
                        intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                                == WIFI_STATE_DISABLED),
                any());
    }

    /**
     * Switch ClientModeManager from Connect mode to ScanOnly mode.
     */
    @Test
    public void switchFromConnectModeToScanOnlyMode() throws Exception {
        startClientInConnectModeAndVerifyEnabled();

        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);
        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);
        verify(mWifiNative).setupInterfaceForClientInScanMode(
                mInterfaceCallbackCaptor.capture(), eq(TEST_WORKSOURCE), eq(mClientModeManager));
        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mClientModeImpl).stop();

        // DeferStopHandler(): ConnectivityManager.class
        // getWifiOffDeferringTimeMs(): SubscriptionManager.class
        verify(mContext).getSystemService(eq(ConnectivityManager.class));
        verify(mContext).getSystemService(eq(SubscriptionManager.class));
        verify(mImsMmTelManager, never()).registerImsRegistrationCallback(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mContext).getResources();

        // Ensure that no public broadcasts were sent.
        verifyNoMoreInteractions(mContext);
        verify(mListener).onStarted(mClientModeManager);
        verify(mListener).onRoleChanged(mClientModeManager);
    }

    /**
     * ClientMode increments failure metrics when failing to setup client mode in connectivity mode.
     */
    @Test
    public void detectAndReportErrorWhenSetupForClientInConnectivityModeWifiNativeFailure()
            throws Exception {
        when(mWifiNative.setupInterfaceForClientInScanMode(any(), any(), any()))
                .thenReturn(TEST_INTERFACE_NAME);
        when(mWifiNative.switchClientInterfaceToConnectivityMode(any(), any())).thenReturn(false);

        mClientModeManager = createClientModeManager(ROLE_CLIENT_PRIMARY);
        mLooper.dispatchAll();

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce()).sendStickyBroadcastAsUser(intentCaptor.capture(),
                eq(UserHandle.ALL));
        List<Intent> intents = intentCaptor.getAllValues();
        assertEquals(2, intents.size());
        checkWifiConnectModeStateChangedBroadcast(intents.get(0), WIFI_STATE_ENABLING,
                WIFI_STATE_DISABLED);
        checkWifiConnectModeStateChangedBroadcast(intents.get(1), WIFI_STATE_DISABLED,
                WIFI_STATE_UNKNOWN);
        verify(mActiveModeWarden).setWifiStateForApiCalls(WIFI_STATE_DISABLED);
        verify(mListener).onStartFailure(mClientModeManager);
        verify(mWifiDiagnostics).takeBugReport(anyString(), anyString());
    }

    /** Tests failure when setting up iface for scan only mode. */
    @Test
    public void detectAndReportErrorWhenSetupInterfaceForClientInScanModeWifiNativeFailure()
            throws Exception {
        // failed to setup iface in Scan Only mode
        when(mWifiNative.setupInterfaceForClientInScanMode(any(), any(), any())).thenReturn(null);

        mClientModeManager = createClientModeManager(ROLE_CLIENT_PRIMARY);
        mLooper.dispatchAll();

        verify(mActiveModeWarden, never()).setWifiStateForApiCalls(anyInt());
        verify(mListener).onStartFailure(mClientModeManager);
        verify(mWifiDiagnostics).takeBugReport(anyString(), anyString());

        mClientModeManager.getFactoryMacAddress();
        // wifi is off, should get value from DefaultClientModeManager
        verify(mDefaultClientModeManager).getFactoryMacAddress();
    }

    /**
     * ClientMode stop before start has been processed properly cleans up state & invokes the
     * onStopped callback.
     */
    @Test
    public void clientModeStopBeforeStartCleansUpState() throws Exception {
        mClientModeManager = createClientModeManager(ROLE_CLIENT_PRIMARY);
        // Invoke stop before the internal start is processed by the state machine.
        mClientModeManager.stop();
        mLooper.dispatchAll();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        // Don't initiate wifi native setup.
        verifyNoMoreInteractions(mListener, mWifiNative);
        assertNull(mClientModeManager.getRole());
        assertNull(mClientModeManager.getPreviousRole());
    }

    /**
     * ClientMode stop properly cleans up state & invokes the onStopped callback.
     */
    @Test
    public void clientModeStopCleansUpState() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        // role has not been reset yet
        ActiveModeManager.ClientRole lastRole = mClientModeManager.getRole();
        assertNotNull(lastRole);
        assertNull(mClientModeManager.getPreviousRole());

        long testChangeRoleTimestamp = 12234455L;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testChangeRoleTimestamp);
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        // then role will be reset
        assertNull(mClientModeManager.getRole());
        assertEquals("Should equal previous role", lastRole,
                mClientModeManager.getPreviousRole());
        assertEquals("The role change timestamp should match", testChangeRoleTimestamp,
                mClientModeManager.getLastRoleChangeSinceBootMs());

        verify(mImsMmTelManager, never()).registerImsRegistrationCallback(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * Triggering interface down when ClientMode is active properly exits the active state.
     */
    @Test
    public void clientModeStartedStopsWhenInterfaceDown() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mContext);
        setUpSystemServiceForContext();
        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(false);
        mInterfaceCallbackCaptor.getValue().onDown(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();
        verify(mSelfRecovery).trigger(SelfRecovery.REASON_STA_IFACE_DOWN);
        verifyConnectModeNotificationsForFailure();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
    }

    /**
     * Triggering interface down when ClientMode is active and Connected MacRandomization is enabled
     * does not exit the active state.
     */
    @Test
    public void clientModeStartedWithConnectedMacRandDoesNotStopWhenInterfaceDown()
            throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mContext);
        setUpSystemServiceForContext();
        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(true);
        mInterfaceCallbackCaptor.getValue().onDown(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();
        verify(mSelfRecovery, never()).trigger(SelfRecovery.REASON_STA_IFACE_DOWN);
        verify(mContext, never()).sendStickyBroadcastAsUser(any(), any());
    }

    /**
     * Testing the handling of an interface destroyed notification.
     */
    @Test
    public void clientModeStartedStopsOnInterfaceDestroyed() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mContext);
        setUpSystemServiceForContext();
        mInterfaceCallbackCaptor.getValue().onDestroyed(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();
        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);
        verify(mClientModeImpl).handleIfaceDestroyed();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        mClientModeManager.stop();
        mLooper.dispatchAll();
        verify(mWifiNative, never()).teardownInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verify that onDestroyed after client mode is stopped doesn't trigger a callback.
     */
    @Test
    public void noCallbackOnInterfaceDestroyedWhenAlreadyStopped() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mListener);

        mClientModeManager.stop();
        mLooper.dispatchAll();
        verify(mWifiNative).teardownInterface(TEST_INTERFACE_NAME);

        // now trigger interface destroyed and make sure callback doesn't get called
        mInterfaceCallbackCaptor.getValue().onDestroyed(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        verifyNoMoreInteractions(mListener);
        verify(mClientModeImpl, never()).handleIfaceDestroyed();
    }

    /**
     * Entering ScanOnly state starts the WakeupController.
     */
    @Test
    public void scanModeEnterStartsWakeupController() throws Exception {
        startClientInScanOnlyModeAndVerifyEnabled();

        verify(mWakeupController).start();
    }

    /**
     * Exiting ScanOnly state stops the WakeupController.
     */
    @Test
    public void scanModeExitStopsWakeupController() throws Exception {
        startClientInScanOnlyModeAndVerifyEnabled();

        mClientModeManager.stop();
        mLooper.dispatchAll();

        assertNull(mClientModeManager.getRole());

        InOrder inOrder = inOrder(mWakeupController, mWifiNative, mListener);

        inOrder.verify(mListener).onStarted(mClientModeManager);
        inOrder.verify(mWakeupController).start();
        inOrder.verify(mWakeupController).stop();
        inOrder.verify(mWifiNative).teardownInterface(eq(TEST_INTERFACE_NAME));
    }

    private void setUpVoWifiTest(
            boolean isWifiCallingAvailable,
            int wifiOffDeferringTimeMs) {
        mCurrentImsRegistrationState = (isWifiCallingAvailable)
            ? RegistrationManager.REGISTRATION_STATE_REGISTERED
            : RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED;
        mCurrentImsConnectionType = (isWifiCallingAvailable)
            ? AccessNetworkConstants.TRANSPORT_TYPE_WLAN
            : AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt())).thenReturn(isWifiCallingAvailable);
        when(mCarrierConfigBundle
                .getInt(eq(CarrierConfigManager.Ims.KEY_WIFI_OFF_DEFERRING_TIME_MILLIS_INT)))
                .thenReturn(wifiOffDeferringTimeMs);
    }

    /**
     * Secondary CMM will stop without deferring.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeWithWifiCallingOnSecondaryTransient()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);
        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();

        // Make sure CMM is not primary
        mClientModeManager.setRole(ROLE_CLIENT_SECONDARY_TRANSIENT, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // Stop CMM and verify the Defer stop code is skipped
        mClientModeManager.stop();
        mLooper.dispatchAll();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
        verify(mImsMmTelManager, never()).registerImsRegistrationCallback(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics).noteWifiOff(eq(false), eq(false), anyInt());
    }

    /**
     * ClientMode stop properly with IMS deferring time without WifiCalling.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeNoWifiCalling() throws Exception {
        setUpVoWifiTest(false,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        verify(mImsMmTelManager, never()).registerImsRegistrationCallback(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics).noteWifiOff(eq(false), eq(false), anyInt());

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode stop properly with IMS deferring time and IMS is registered on WWAN.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeAndImsOnWwan() throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);
        mCurrentImsConnectionType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode stop properly with IMS deferring time, Wifi calling.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeWithWifiCalling() throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        mImsNetworkCallback.onAvailable(null);
        mLooper.dispatchAll();

        // Not yet finish IMS deregistration.
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mListener, never()).onStopped(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service IMS service is de-registered.
        assertNotNull(mImsMmTelManagerRegistrationCallback);
        mImsMmTelManagerRegistrationCallback.onUnregistered(null);
        assertNotNull(mImsNetworkCallback);
        mImsNetworkCallback.onLost(null);
        mLooper.dispatchAll();

        // Now Wifi could be turned off actually.
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        assertNull(mImsNetworkCallback);
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode stop properly with IMS deferring time, Wifi calling.
     *
     * The network losts first and then IMS is de-registered.
     * The WIFI should be off after IMS deregistration.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeWithWifiCallingAndNetworkLostFirst()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        mImsNetworkCallback.onAvailable(null);
        mLooper.dispatchAll();

        // Not yet finish IMS deregistration.
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener, never()).onStopped(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service the network lost.
        assertNotNull(mImsNetworkCallback);
        mImsNetworkCallback.onLost(null);
        mLooper.dispatchAll();

        // Since IMS service is not de-registered yet, wifi should be available.
        moveTimeForward(1000);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service IMS service is de-registered.
        assertNotNull(mImsMmTelManagerRegistrationCallback);
        mImsMmTelManagerRegistrationCallback.onUnregistered(null);
        mLooper.dispatchAll();

        // Now Wifi could be turned off actually.
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        assertNull(mImsNetworkCallback);
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode stop properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is done before reaching the timeout.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeAndWifiCallingOnImsRegistered()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();

        // Not yet finish IMS deregistration.
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener, never()).onStopped(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service IMS service is de-registered.
        assertNotNull(mImsMmTelManagerRegistrationCallback);
        mImsMmTelManagerRegistrationCallback.onRegistered(0);
        mLooper.dispatchAll();

        // Now Wifi could be turned off actually.
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode stop properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is NOT done before reaching the timeout.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeAndWifiCallingTimedOut()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener, never()).onStopped(any());

        // 1/2 deferring time passed, should be still waiting for the callback.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS / 2);
        mLooper.dispatchAll();
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mListener, never()).onStopped(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Exceeding the timeout, wifi should be stopped.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS / 2 + 1000);
        mLooper.dispatchAll();
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(true), anyInt());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode stop properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is NOT done before reaching the timeout with multiple stop calls.
     */
    @Test
    public void clientModeStopWithWifiOffDeferringTimeAndWifiCallingTimedOutMultipleStop()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mListener, never()).onStopped(any());

        mClientModeManager.stop();
        mLooper.dispatchAll();
        // should not register another listener.
        verify(mImsMmTelManager, times(1)).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mListener, never()).onStopped(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Exceeding the timeout, wifi should be stopped.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS + 1000);
        mLooper.dispatchAll();
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(true), anyInt());

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * Switch to scan mode properly with IMS deferring time without WifiCalling.
     */
    @Test
    public void switchToScanOnlyModeWithWifiOffDeferringTimeNoWifiCalling() throws Exception {
        setUpVoWifiTest(false,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager, never()).registerImsRegistrationCallback(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics).noteWifiOff(eq(false), eq(false), anyInt());
    }

    /**
     * Switch to scan mode properly with IMS deferring time and IMS is registered on WWAN.
     */
    @Test
    public void switchToScanOnlyModeWithWifiOffDeferringTimeAndImsOnWwan() throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);
        mCurrentImsConnectionType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);

        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());
    }

    /**
     * Switch to scan mode properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is done before reaching the timeout.
     */
    @Test
    public void switchToScanOnlyModeWithWifiOffDeferringTimeAndWifiCallingOnImsUnregistered()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mImsNetworkCallback.onAvailable(null);
        mLooper.dispatchAll();

        // Not yet finish IMS deregistration.
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service IMS service is de-registered.
        assertNotNull(mImsMmTelManagerRegistrationCallback);
        mImsMmTelManagerRegistrationCallback.onUnregistered(null);
        assertNotNull(mImsNetworkCallback);
        mImsNetworkCallback.onLost(null);
        mLooper.dispatchAll();

        // Now Wifi could be switched to scan mode actually.
        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        assertNull(mImsNetworkCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());
    }

    /**
     * Switch to scan mode properly with IMS deferring time and Wifi calling.
     *
     * Network lost before IMS deregistration is done. The wifi should be still available
     * until IMS is de-registered or time out.
     */
    @Test
    public void switchToScanOnlyModeWithWifiOffDeferringTimeAndWifiCallingOnNetworkLostFirst()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mImsNetworkCallback.onAvailable(null);
        mLooper.dispatchAll();

        // Not yet finish IMS deregistration.
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service network lost
        assertNotNull(mImsNetworkCallback);
        mImsNetworkCallback.onLost(null);
        mLooper.dispatchAll();

        // Since IMS service is not de-registered yet, wifi should be available.
        moveTimeForward(1000);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service IMS service is de-registered.
        assertNotNull(mImsMmTelManagerRegistrationCallback);
        mImsMmTelManagerRegistrationCallback.onUnregistered(null);
        mLooper.dispatchAll();

        // Now Wifi could be switched to scan mode actually.
        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        assertNull(mImsNetworkCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());
    }

    /**
     * Switch to scan mode properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is done before reaching the timeout.
     */
    @Test
    public void switchToScanOnlyModeWithWifiOffDeferringTimeAndWifiCallingOnImsRegistered()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // Not yet finish IMS deregistration.
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Notify wifi service IMS service is de-registered.
        assertNotNull(mImsMmTelManagerRegistrationCallback);
        mImsMmTelManagerRegistrationCallback.onRegistered(0);
        mLooper.dispatchAll();

        // Now Wifi could be switched to scan mode actually.
        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(false), anyInt());
    }

    /**
     * Switch to scan mode properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is NOT done before reaching the timeout.
     */
    @Test
    public void switchToScanOnlyModeWithWifiOffDeferringTimeAndWifiCallingTimedOut()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());

        // 1/2 deferring time passed, should be still waiting for the callback.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS / 2);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Exceeding the timeout, wifi should be stopped.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS / 2 + 1000);
        mLooper.dispatchAll();
        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(true), anyInt());
    }

    /**
     * Switch to scan mode properly with IMS deferring time and Wifi calling.
     *
     * IMS deregistration is NOT done before reaching the timeout with multiple stop calls.
     */
    @Test
    public void
            switchToScanOnlyModeWithWifiOffDeferringTimeAndWifiCallingTimedOutMultipleSwitch()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        // should not register another listener.
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager, times(1)).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Exceeding the timeout, wifi should be stopped.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS + 1000);
        mLooper.dispatchAll();
        verify(mWifiNative).switchClientInterfaceToScanMode(TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        verify(mWifiMetrics).noteWifiOff(eq(true), eq(true), anyInt());
    }

    /**
     * Stay at connected mode with IMS deferring time and Wifi calling
     * when the target state is not ROLE_CLIENT_SCAN_ONLY.
     *
     * Simulate a user toggle wifi multiple times before doing wifi stop and stay at
     * ON position.
     */
    @Test
    public void
            stayAtConnectedModeWithWifiOffDeferringTimeAndWifiCallingTimedOutMultipleSwitch()
            throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        when(mWifiNative.switchClientInterfaceToScanMode(any(), any()))
                .thenReturn(true);

        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mClientModeManager.setRole(ActiveModeManager.ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mClientModeManager.setRole(ROLE_CLIENT_SCAN_ONLY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mClientModeManager.setRole(ActiveModeManager.ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mImsMmTelManager).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());

        // should not register another listener.
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(any(), any());
        verify(mImsMmTelManager, times(1)).registerImsRegistrationCallback(
                any(Executor.class),
                any(RegistrationManager.RegistrationCallback.class));
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());

        // Exceeding the timeout, wifi should NOT be stopped.
        moveTimeForward(TEST_WIFI_OFF_DEFERRING_TIME_MS + 1000);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).switchClientInterfaceToScanMode(
                TEST_INTERFACE_NAME, TEST_WORKSOURCE);
        verify(mImsMmTelManager).unregisterImsRegistrationCallback(
                any(RegistrationManager.RegistrationCallback.class));
        assertNull(mImsMmTelManagerRegistrationCallback);
        verify(mWifiMetrics, never()).noteWifiOff(anyBoolean(), anyBoolean(), anyInt());
    }

    /**
     * ClientMode stop properly with IMS deferring time without WifiCalling.
     */
    @Test
    public void clientModeStopWithImsManagerException() throws Exception {
        setUpVoWifiTest(true,
                TEST_WIFI_OFF_DEFERRING_TIME_MS);
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Test Runtime Exception"));

        startClientInConnectModeAndVerifyEnabled();
        reset(mContext, mListener);
        setUpSystemServiceForContext();
        mClientModeManager.stop();
        mLooper.dispatchAll();
        when(mClientModeImpl.hasQuit()).thenReturn(true);
        mClientModeManager.onClientModeImplQuit();
        verify(mListener).onStopped(mClientModeManager);

        verifyConnectModeNotificationsForCleanShutdown(WIFI_STATE_ENABLED);

        verify(mImsMmTelManager, never()).registerImsRegistrationCallback(any(), any());
        verify(mImsMmTelManager, never()).unregisterImsRegistrationCallback(any());
        verify(mWifiMetrics).noteWifiOff(eq(false), eq(false), anyInt());

        // on an explicit stop, we should not trigger the callback
        verifyNoMoreInteractions(mListener);
    }

    /**
     * ClientMode starts up in connect mode and then change connectivity roles.
     */
    @Test
    public void clientInConnectModeChangeRoles() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mListener);

        // Set the same role again, no-op.
        assertEquals(ActiveModeManager.ROLE_CLIENT_PRIMARY, mClientModeManager.getRole());
        mClientModeManager.setRole(ActiveModeManager.ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        verify(mWifiNative).replaceStaIfaceRequestorWs(
                eq(TEST_INTERFACE_NAME), same(TEST_WORKSOURCE));
        verify(mListener, never()).onRoleChanged(any()); // no callback sent.

        // Change the connectivity role.
        ActiveModeManager.Listener<ConcreteClientModeManager> newListener =
                mock(ActiveModeManager.Listener.class);
        mClientModeManager.setRole(
                ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT, TEST_WORKSOURCE, newListener);
        mLooper.dispatchAll();
        verify(mWifiNative, times(2)).replaceStaIfaceRequestorWs(
                eq(TEST_INTERFACE_NAME), same(TEST_WORKSOURCE));
        verify(newListener).onRoleChanged(mClientModeManager); // callback sent on new listener.
        verifyNoMoreInteractions(mListener); // no callback sent on older listener.
    }

    @Test
    public void clientInConnectModeChangeRolesNewWorkSource_WifiNativeFailed_noCallback()
            throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        reset(mListener);

        when(mWifiNative.replaceStaIfaceRequestorWs(TEST_INTERFACE_NAME, TEST_WORKSOURCE2))
                .thenReturn(false);

        // set new mode with new WorkSource
        mClientModeManager.setRole(ROLE_CLIENT_SECONDARY_TRANSIENT, TEST_WORKSOURCE2);
        mLooper.dispatchAll();

        verify(mWifiNative).replaceStaIfaceRequestorWs(
                eq(TEST_INTERFACE_NAME), same(TEST_WORKSOURCE2));
        // no callback sent since replaceStaIfaceRequestorWs failed
        verify(mListener, never()).onRoleChanged(any());
    }

    @Test
    public void setRoleBeforeInvokingListener() throws Exception {
        when(mWifiNative.setupInterfaceForClientInScanMode(any(), any(), any()))
                .thenReturn(TEST_INTERFACE_NAME);
        when(mWifiNative.switchClientInterfaceToConnectivityMode(any(), any()))
                .thenReturn(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(ActiveModeManager clientModeManager) throws Exception {
                assertEquals(ROLE_CLIENT_SCAN_ONLY, clientModeManager.getRole());
            }
        }).when(mListener).onStarted(mClientModeManager);
        mClientModeManager = createClientModeManager(ROLE_CLIENT_SCAN_ONLY);
        mLooper.dispatchAll();

        ActiveModeManager.ClientRole lastRole = mClientModeManager.getRole();
        assertEquals(ROLE_CLIENT_SCAN_ONLY, lastRole);
        assertNull(mClientModeManager.getPreviousRole());

        verify(mWifiNative).setupInterfaceForClientInScanMode(
                mInterfaceCallbackCaptor.capture(), eq(TEST_WORKSOURCE), eq(mClientModeManager));
        mInterfaceCallbackCaptor.getValue().onUp(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();
        verify(mListener).onStarted(mClientModeManager); // callback sent.

        // Change to connectivity role.
        long testChangeRoleTimestamp = 12234455L;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testChangeRoleTimestamp);
        doAnswer(new AnswerWithArguments() {
            public void answer(ActiveModeManager clientModeManager) throws Exception {
                assertEquals(ActiveModeManager.ROLE_CLIENT_PRIMARY, clientModeManager.getRole());
            }
        }).when(mListener).onRoleChanged(mClientModeManager);
        mClientModeManager.setRole(ActiveModeManager.ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        verify(mListener).onRoleChanged(mClientModeManager); // callback sent.

        // Verify the role is changed and previousRole is updated.
        assertEquals("Should equal previous role", lastRole,
                mClientModeManager.getPreviousRole());
        assertEquals("The role change timestamp should match", testChangeRoleTimestamp,
                mClientModeManager.getLastRoleChangeSinceBootMs());
    }

    @Test
    public void propagateSettingsToClientModeImpl() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        verify(mWifiInjector).makeClientModeImpl(any(), any(), eq(false));
        verify(mClientModeImpl).setShouldReduceNetworkScore(false);

        mClientModeManager.enableVerboseLogging(true);
        verify(mClientModeImpl).enableVerboseLogging(true);

        mClientModeManager.enableVerboseLogging(false);
        verify(mClientModeImpl).enableVerboseLogging(false);

        mClientModeManager.setShouldReduceNetworkScore(true);
        verify(mClientModeImpl).setShouldReduceNetworkScore(true);

        mClientModeManager.setShouldReduceNetworkScore(false);
        verify(mClientModeImpl, times(2)).setShouldReduceNetworkScore(false);

        mClientModeManager.onIdleModeChanged(true);
        verify(mClientModeImpl).onIdleModeChanged(true);

        mClientModeManager.onIdleModeChanged(false);
        verify(mClientModeImpl).onIdleModeChanged(false);
    }

    @Test
    public void propagateConnectedWifiScorerToPrimaryClientModeImpl() throws Exception {
        startClientInConnectModeAndVerifyEnabled();

        IBinder iBinder = mock(IBinder.class);
        IWifiConnectedNetworkScorer iScorer = mock(IWifiConnectedNetworkScorer.class);
        mClientModeManager.setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);
        verify(mClientModeImpl).setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);

        mClientModeManager.clearWifiConnectedNetworkScorer();
        verify(mClientModeImpl).clearWifiConnectedNetworkScorer();

        mClientModeManager.setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);
        verify(mClientModeImpl, times(2)).setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);

        mClientModeManager.onNetworkSwitchAccepted(1, "macAddress");
        verify(mClientModeImpl).onNetworkSwitchAccepted(1, "macAddress");

        mClientModeManager.onNetworkSwitchRejected(1, "macAddress");
        verify(mClientModeImpl).onNetworkSwitchRejected(1, "macAddress");
    }

    @Test
    public void updateConnectModeStateInAllRoles() throws Exception {
        startClientInConnectModeAndVerifyEnabled();
        mClientModeManager.setRole(ROLE_CLIENT_SECONDARY_TRANSIENT, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mClientModeManager.stop();
        // disabling broadcast wasn't sent out (since role is secondary)
        verify(mContext, never()).sendStickyBroadcastAsUser(
                argThat(intent ->
                        intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                                == WifiManager.WIFI_STATE_DISABLING),
                any());
        // Wifi state should not be updated due to the role is not primary.
        verify(mActiveModeWarden, never()).setWifiStateForApiCalls(WIFI_STATE_DISABLING);
    }

    @Test
    public void changeRoleResetsSettings() throws Exception {
        startClientInConnectModeAndVerifyEnabled();

        verify(mClientModeImpl).setShouldReduceNetworkScore(false);

        mClientModeManager.setRole(ROLE_CLIENT_SECONDARY_TRANSIENT, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // reset upon role change
        verify(mClientModeImpl, times(2)).setShouldReduceNetworkScore(false);
    }

    @Test
    public void sameRoleDoesntResetsSettings() throws Exception {
        startClientInConnectModeAndVerifyEnabled();

        verify(mClientModeImpl).setShouldReduceNetworkScore(false);

        mClientModeManager.setRole(ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // no role change, no reset
        verify(mClientModeImpl).setShouldReduceNetworkScore(false);
    }
}
