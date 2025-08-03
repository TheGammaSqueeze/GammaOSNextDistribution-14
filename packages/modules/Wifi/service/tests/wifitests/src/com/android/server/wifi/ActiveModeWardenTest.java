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
 * limitations under the License.
 */

package com.android.server.wifi;

import static android.net.wifi.WifiManager.SAP_START_FAILURE_GENERAL;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_FAILED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_LOCAL_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.ActiveModeManager.ROLE_SOFTAP_LOCAL_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_SOFTAP_TETHERED;
import static com.android.server.wifi.ActiveModeWarden.INTERNAL_REQUESTOR_WS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_NATIVE_SUPPORTED_STA_BANDS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.LocationManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.wifi.ISubsystemRestartCallback;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.IWifiNetworkStateChangedListener;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApConfiguration.Builder;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SoftApState;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.BatteryStatsManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserManager;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ActiveModeManager.ClientConnectivityRole;
import com.android.server.wifi.ActiveModeManager.Listener;
import com.android.server.wifi.ActiveModeManager.SoftApRole;
import com.android.server.wifi.ActiveModeWarden.ExternalClientModeManagerRequestListener;
import com.android.server.wifi.util.GeneralUtil.Mutable;
import com.android.server.wifi.util.LastCallerInfoManager;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link com.android.server.wifi.ActiveModeWarden}.
 */
@SmallTest
public class ActiveModeWardenTest extends WifiBaseTest {
    public static final String TAG = "WifiActiveModeWardenTest";

    private static final String ENABLED_STATE_STRING = "EnabledState";
    private static final String DISABLED_STATE_STRING = "DisabledState";
    private static final String TEST_SSID_1 = "\"Ssid12345\"";
    private static final String TEST_SSID_2 = "\"Ssid45678\"";
    private static final String TEST_SSID_3 = "\"Ssid98765\"";
    private static final String TEST_BSSID_1 = "01:12:23:34:45:56";
    private static final String TEST_BSSID_2 = "10:21:32:43:54:65";
    private static final String TEST_BSSID_3 = "11:22:33:44:55:66";

    private static final String WIFI_IFACE_NAME = "mockWlan";
    private static final String WIFI_IFACE_NAME_1 = "mockWlan1";
    private static final int TEST_WIFI_RECOVERY_DELAY_MS = 2000;
    private static final int TEST_AP_FREQUENCY = 2412;
    private static final int TEST_AP_BANDWIDTH = SoftApInfo.CHANNEL_WIDTH_20MHZ;
    private static final int TEST_UID = 435546654;
    private static final long TEST_FEATURE_SET = 0xAB3DEF;
    private static final String TEST_PACKAGE = "com.test";
    private static final String TEST_COUNTRYCODE = "US";
    private static final WorkSource TEST_WORKSOURCE = new WorkSource(TEST_UID, TEST_PACKAGE);
    private static final WorkSource SETTINGS_WORKSOURCE =
            new WorkSource(Process.SYSTEM_UID, "system-service");
    private static final int TEST_SUPPORTED_BANDS = 15;

    TestLooper mLooper;
    @Mock WifiInjector mWifiInjector;
    @Mock Context mContext;
    @Mock Resources mResources;
    @Mock WifiNative mWifiNative;
    @Mock WifiApConfigStore mWifiApConfigStore;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock SoftApManager mSoftApManager;
    @Mock DefaultClientModeManager mDefaultClientModeManager;
    @Mock BatteryStatsManager mBatteryStats;
    @Mock SelfRecovery mSelfRecovery;
    @Mock WifiDiagnostics mWifiDiagnostics;
    @Mock ScanRequestProxy mScanRequestProxy;
    @Mock FrameworkFacade mFacade;
    @Mock WifiSettingsStore mSettingsStore;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock SoftApCapability mSoftApCapability;
    @Mock ActiveModeWarden.ModeChangeCallback mModeChangeCallback;
    @Mock ActiveModeWarden.PrimaryClientModeManagerChangedCallback mPrimaryChangedCallback;
    @Mock WifiMetrics mWifiMetrics;
    @Mock ISubsystemRestartCallback mSubsystemRestartCallback;
    @Mock ExternalScoreUpdateObserverProxy mExternalScoreUpdateObserverProxy;
    @Mock DppManager mDppManager;
    @Mock SarManager mSarManager;
    @Mock HalDeviceManager mHalDeviceManager;
    @Mock UserManager mUserManager;
    @Mock PackageManager mPackageManager;
    @Mock Network mNetwork;
    @Mock LocalLog mLocalLog;
    @Mock WifiSettingsConfigStore mSettingsConfigStore;
    @Mock LastCallerInfoManager mLastCallerInfoManager;
    @Mock WifiGlobals mWifiGlobals;

    Listener<ConcreteClientModeManager> mClientListener;
    Listener<SoftApManager> mSoftApListener;
    WifiServiceImpl.SoftApCallbackInternal mSoftApManagerCallback;
    SoftApModeConfiguration mSoftApConfig;
    @Mock WifiServiceImpl.SoftApCallbackInternal mSoftApStateMachineCallback;
    @Mock WifiServiceImpl.SoftApCallbackInternal mLohsStateMachineCallback;
    WifiNative.StatusListener mWifiNativeStatusListener;
    ActiveModeWarden mActiveModeWarden;
    private SoftApInfo mTestSoftApInfo;

    final ArgumentCaptor<WifiNative.StatusListener> mStatusListenerCaptor =
            ArgumentCaptor.forClass(WifiNative.StatusListener.class);

    private BroadcastReceiver mEmergencyCallbackModeChangedBr;
    private BroadcastReceiver mEmergencyCallStateChangedBr;

    /**
     * Set up the test environment.
     */
    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "Setting up ...");

        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();

        when(mWifiInjector.getScanRequestProxy()).thenReturn(mScanRequestProxy);
        when(mWifiInjector.getSarManager()).thenReturn(mSarManager);
        when(mWifiInjector.getHalDeviceManager()).thenReturn(mHalDeviceManager);
        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mWifiInjector.getWifiHandlerLocalLog()).thenReturn(mLocalLog);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);
        when(mContext.getResources()).thenReturn(mResources);
        when(mSoftApManager.getRole()).thenReturn(ROLE_SOFTAP_TETHERED);

        when(mResources.getString(R.string.wifi_localhotspot_configure_ssid_default))
                .thenReturn("AndroidShare");
        when(mResources.getInteger(R.integer.config_wifi_framework_recovery_timeout_delay))
                .thenReturn(TEST_WIFI_RECOVERY_DELAY_MS);
        when(mResources.getBoolean(R.bool.config_wifiScanHiddenNetworksScanOnlyMode))
                .thenReturn(false);
        when(mResources.getBoolean(R.bool.config_wifi_turn_off_during_emergency_call))
                .thenReturn(true);

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mFacade.getSettingsWorkSource(mContext)).thenReturn(SETTINGS_WORKSOURCE);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)).thenReturn(true);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mSettingsConfigStore);
        when(mWifiInjector.getLastCallerInfoManager()).thenReturn(mLastCallerInfoManager);
        when(mSettingsConfigStore.get(
                eq(WIFI_NATIVE_SUPPORTED_STA_BANDS))).thenReturn(
                TEST_SUPPORTED_BANDS);
        // Default force that WPA Personal is deprecated since the feature set is opposite to the
        // API value.
        when(mWifiGlobals.isWpaPersonalDeprecated()).thenReturn(true);
        doAnswer(new Answer<ClientModeManager>() {
            public ClientModeManager answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                mClientListener = (Listener<ConcreteClientModeManager>) args[0];
                return mClientModeManager;
            }
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        doAnswer(new Answer<SoftApManager>() {
            public SoftApManager answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                mSoftApListener = (Listener<SoftApManager>) args[0];
                mSoftApManagerCallback = (WifiServiceImpl.SoftApCallbackInternal) args[1];
                mSoftApConfig = (SoftApModeConfiguration) args[2];
                return mSoftApManager;
            }
        }).when(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), any(), any(), any(),
                anyBoolean());
        when(mWifiNative.initialize()).thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(true);

        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        verify(mWifiMetrics).noteWifiEnabledDuringBoot(false);

        verify(mWifiNative).registerStatusListener(mStatusListenerCaptor.capture());
        verify(mWifiNative).initialize();
        mWifiNativeStatusListener = mStatusListenerCaptor.getValue();

        mActiveModeWarden.registerSoftApCallback(mSoftApStateMachineCallback);
        mActiveModeWarden.registerLohsCallback(mLohsStateMachineCallback);
        mActiveModeWarden.registerModeChangeCallback(mModeChangeCallback);
        mActiveModeWarden.registerPrimaryClientModeManagerChangedCallback(mPrimaryChangedCallback);
        when(mSubsystemRestartCallback.asBinder()).thenReturn(Mockito.mock(IBinder.class));
        mActiveModeWarden.registerSubsystemRestartCallback(mSubsystemRestartCallback);
        mTestSoftApInfo = new SoftApInfo();
        mTestSoftApInfo.setFrequency(TEST_AP_FREQUENCY);
        mTestSoftApInfo.setBandwidth(TEST_AP_BANDWIDTH);

        ArgumentCaptor<BroadcastReceiver> bcastRxCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
                bcastRxCaptor.capture(),
                argThat(filter ->
                        filter.hasAction(TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)));
        mEmergencyCallbackModeChangedBr = bcastRxCaptor.getValue();

        verify(mContext).registerReceiver(
                bcastRxCaptor.capture(),
                argThat(filter ->
                        filter.hasAction(TelephonyManager.ACTION_EMERGENCY_CALL_STATE_CHANGED)));
        mEmergencyCallStateChangedBr = bcastRxCaptor.getValue();
    }

    private ActiveModeWarden createActiveModeWarden() {
        ActiveModeWarden warden = new ActiveModeWarden(
                mWifiInjector,
                mLooper.getLooper(),
                mWifiNative,
                mDefaultClientModeManager,
                mBatteryStats,
                mWifiDiagnostics,
                mContext,
                mSettingsStore,
                mFacade,
                mWifiPermissionsUtil,
                mWifiMetrics,
                mExternalScoreUpdateObserverProxy,
                mDppManager,
                mWifiGlobals);
        // SelfRecovery is created in WifiInjector after ActiveModeWarden, so getSelfRecovery()
        // returns null when constructing ActiveModeWarden.
        when(mWifiInjector.getSelfRecovery()).thenReturn(mSelfRecovery);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)).thenReturn(true);
        warden.setWifiStateForApiCalls(WIFI_STATE_ENABLED);
        return warden;
    }

    /**
     * Clean up after tests - explicitly set tested object to null.
     */
    @After
    public void cleanUp() throws Exception {
        mActiveModeWarden = null;
        mLooper.dispatchAll();
    }

    private void emergencyCallbackModeChanged(boolean enabled) {
        Intent intent = new Intent(TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, enabled);
        mEmergencyCallbackModeChangedBr.onReceive(mContext, intent);
    }

    private void emergencyCallStateChanged(boolean enabled) {
        Intent intent = new Intent(TelephonyManager.ACTION_EMERGENCY_CALL_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_PHONE_IN_EMERGENCY_CALL, enabled);
        mEmergencyCallStateChangedBr.onReceive(mContext, intent);
    }

    private void enterClientModeActiveState() throws Exception {
        enterClientModeActiveState(false);
    }

    /**
     * Helper method to enter the EnabledState and set ClientModeManager in ConnectMode.
     * @param isClientModeSwitch true if switching from another mode, false if creating a new one
     */
    private void enterClientModeActiveState(boolean isClientModeSwitch) throws Exception {
        enterClientModeActiveState(isClientModeSwitch, TEST_FEATURE_SET);
    }

    /**
     * Helper method with tested feature set to enter the EnabledState and set ClientModeManager
     * in ConnectMode.
     *
     * @param isClientModeSwitch true if switching from another mode, false if creating a new one
     * @param testFeatureSet a customized feature set to test
     */
    private void enterClientModeActiveState(boolean isClientModeSwitch, long testFeatureSet)
            throws Exception {
        String fromState = mActiveModeWarden.getCurrentMode();
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mClientModeManager.getCurrentNetwork()).thenReturn(mNetwork);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(testFeatureSet);
        // ClientModeManager starts in SCAN_ONLY role.
        mClientListener.onRoleChanged(mClientModeManager);
        mLooper.dispatchAll();

        assertInEnabledState();
        if (!isClientModeSwitch) {
            verify(mWifiInjector).makeClientModeManager(
                    any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        } else {
            verify(mClientModeManager).setRole(ROLE_CLIENT_PRIMARY, SETTINGS_WORKSOURCE);
        }
        verify(mScanRequestProxy, times(1)).enableScanning(true, true);
        if (fromState.equals(DISABLED_STATE_STRING)) {
            verify(mBatteryStats).reportWifiOn();
        }
        for (int i = 0; i < 3; i++) {
            mActiveModeWarden.updateClientScanModeAfterCountryCodeUpdate(TEST_COUNTRYCODE);
        }
        verify(mClientModeManager, atLeastOnce()).getInterfaceName();
        verify(mWifiNative, atLeastOnce()).getSupportedFeatureSet(WIFI_IFACE_NAME);
        assertEquals(testFeatureSet, mActiveModeWarden.getSupportedFeatureSet());
        verify(mScanRequestProxy, times(4)).enableScanning(true, true);
        assertEquals(mClientModeManager, mActiveModeWarden.getPrimaryClientModeManager());
        verify(mModeChangeCallback).onActiveModeManagerRoleChanged(mClientModeManager);
    }

    private void enterScanOnlyModeActiveState() throws Exception {
        enterScanOnlyModeActiveState(false);
    }

    /**
     * Helper method to enter the EnabledState and set ClientModeManager in ScanOnlyMode.
     */
    private void enterScanOnlyModeActiveState(boolean isClientModeSwitch) throws Exception {
        String fromState = mActiveModeWarden.getCurrentMode();
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
        when(mClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);
        when(mClientModeManager.getCurrentNetwork()).thenReturn(null);
        when(mWifiNative.getSupportedFeatureSet(null)).thenReturn(TEST_FEATURE_SET);
        if (!isClientModeSwitch) {
            mClientListener.onStarted(mClientModeManager);
            mLooper.dispatchAll();
            verify(mWifiInjector).makeClientModeManager(
                    any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_SCAN_ONLY), anyBoolean());
            verify(mModeChangeCallback).onActiveModeManagerAdded(mClientModeManager);
        } else {
            mClientListener.onRoleChanged(mClientModeManager);
            mLooper.dispatchAll();
            verify(mClientModeManager).setRole(ROLE_CLIENT_SCAN_ONLY, INTERNAL_REQUESTOR_WS);
            // If switching from client mode back to scan only mode, role change would have been
            // called once before when transitioning from scan only mode to client mode.
            // Verify that it was called again.
            verify(mModeChangeCallback, times(2))
                    .onActiveModeManagerRoleChanged(mClientModeManager);
            verify(mWifiNative, atLeastOnce()).getSupportedFeatureSet(null);
            assertEquals(TEST_FEATURE_SET, mActiveModeWarden.getSupportedFeatureSet());
        }
        assertInEnabledState();
        verify(mScanRequestProxy).enableScanning(true, false);
        if (fromState.equals(DISABLED_STATE_STRING)) {
            verify(mBatteryStats).reportWifiOn();
        }
        verify(mBatteryStats).reportWifiState(BatteryStatsManager.WIFI_STATE_OFF_SCANNING, null);
        assertEquals(mClientModeManager, mActiveModeWarden.getScanOnlyClientModeManager());
    }

    private void enterSoftApActiveMode() throws Exception {
        enterSoftApActiveMode(
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_TETHERED, null,
                mSoftApCapability, TEST_COUNTRYCODE, null));
    }

    private int mTimesCreatedSoftApManager = 1;

    /**
     * Helper method to activate SoftApManager.
     *
     * This method puts the test object into the correct state and verifies steps along the way.
     */
    private void enterSoftApActiveMode(SoftApModeConfiguration softApConfig) throws Exception {
        String fromState = mActiveModeWarden.getCurrentMode();
        SoftApRole softApRole = softApConfig.getTargetMode() == WifiManager.IFACE_IP_MODE_TETHERED
                ? ROLE_SOFTAP_TETHERED : ROLE_SOFTAP_LOCAL_ONLY;
        mActiveModeWarden.startSoftAp(softApConfig, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        when(mSoftApManager.getRole()).thenReturn(softApRole);
        when(mSoftApManager.getSoftApModeConfiguration()).thenReturn(softApConfig);
        mSoftApListener.onStarted(mSoftApManager);
        mLooper.dispatchAll();

        assertInEnabledState();
        assertThat(softApConfig).isEqualTo(mSoftApConfig);
        verify(mWifiInjector, times(mTimesCreatedSoftApManager)).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(softApRole), anyBoolean());
        mTimesCreatedSoftApManager++;
        if (fromState.equals(DISABLED_STATE_STRING)) {
            verify(mBatteryStats).reportWifiOn();
        }
        if (softApRole == ROLE_SOFTAP_TETHERED) {
            assertEquals(mSoftApManager, mActiveModeWarden.getTetheredSoftApManager());
            assertNull(mActiveModeWarden.getLocalOnlySoftApManager());
        } else {
            assertEquals(mSoftApManager, mActiveModeWarden.getLocalOnlySoftApManager());
            assertNull(mActiveModeWarden.getTetheredSoftApManager());
        }
        verify(mModeChangeCallback).onActiveModeManagerAdded(mSoftApManager);
    }

    private void enterStaDisabledMode(boolean isSoftApModeManagerActive) {
        String fromState = mActiveModeWarden.getCurrentMode();
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        if (mClientListener != null) {
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
            verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);
        }

        if (isSoftApModeManagerActive) {
            assertInEnabledState();
        } else {
            assertInDisabledState();
        }
        if (fromState.equals(ENABLED_STATE_STRING)) {
            verify(mScanRequestProxy).enableScanning(false, false);
        }
        // Ensure we return the default client mode manager when wifi is off.
        assertEquals(mDefaultClientModeManager, mActiveModeWarden.getPrimaryClientModeManager());
    }

    private void shutdownWifi() {
        mActiveModeWarden.recoveryDisableWifi();
        mLooper.dispatchAll();
    }

    private void assertInEnabledState() {
        assertThat(mActiveModeWarden.getCurrentMode()).isEqualTo(ENABLED_STATE_STRING);
    }

    private void assertInDisabledState() {
        assertThat(mActiveModeWarden.getCurrentMode()).isEqualTo(DISABLED_STATE_STRING);
    }

    /**
     * Emergency mode is a sub-mode within each main state (ScanOnly, Client, DisabledState).
     */
    private void assertInEmergencyMode() {
        assertThat(mActiveModeWarden.isInEmergencyMode()).isTrue();
    }

    private void assertNotInEmergencyMode() {
        assertThat(mActiveModeWarden.isInEmergencyMode()).isFalse();
    }

    /**
     * Counts the number of times a void method was called on a mock.
     *
     * Void methods cannot be passed to Mockito.mockingDetails(). Thus we have to use method name
     * matching instead.
     */
    private static int getMethodInvocationCount(Object mock, String methodName) {
        long count = mockingDetails(mock).getInvocations()
                .stream()
                .filter(invocation -> methodName.equals(invocation.getMethod().getName()))
                .count();
        return (int) count;
    }

    /**
     * Counts the number of times a non-void method was called on a mock.
     *
     * For non-void methods, can pass the method call literal directly:
     * e.g. getMethodInvocationCount(mock.method());
     */
    private static int getMethodInvocationCount(Object mockMethod) {
        return mockingDetails(mockMethod).getInvocations().size();
    }

    private void assertWifiShutDown(Runnable r) {
        assertWifiShutDown(r, 1);
    }

    /**
     * Asserts that the runnable r has shut down wifi properly.
     *
     * @param r     runnable that will shut down wifi
     * @param times expected number of times that <code>r</code> shut down wifi
     */
    private void assertWifiShutDown(Runnable r, int times) {
        // take snapshot of ActiveModeManagers
        Collection<ActiveModeManager> activeModeManagers =
                mActiveModeWarden.getActiveModeManagers();

        List<Integer> expectedStopInvocationCounts = activeModeManagers
                .stream()
                .map(manager -> getMethodInvocationCount(manager, "stop") + times)
                .collect(Collectors.toList());

        r.run();

        List<Integer> actualStopInvocationCounts = activeModeManagers
                .stream()
                .map(manager -> getMethodInvocationCount(manager, "stop"))
                .collect(Collectors.toList());

        String managerNames = activeModeManagers.stream()
                .map(manager -> manager.getClass().getCanonicalName())
                .collect(Collectors.joining(", ", "[", "]"));

        assertWithMessage(managerNames).that(actualStopInvocationCounts)
                .isEqualTo(expectedStopInvocationCounts);
    }

    private void assertEnteredEcmMode(Runnable r) {
        assertEnteredEcmMode(r, 1);
    }

    /**
     * Asserts that the runnable r has entered ECM state properly.
     *
     * @param r     runnable that will enter ECM
     * @param times expected number of times that <code>r</code> shut down wifi
     */
    private void assertEnteredEcmMode(Runnable r, int times) {
        // take snapshot of ActiveModeManagers
        Collection<ActiveModeManager> activeModeManagers =
                mActiveModeWarden.getActiveModeManagers();

        boolean disableWifiInEcm = mFacade.getConfigWiFiDisableInECBM(mContext);

        List<Integer> expectedStopInvocationCounts = activeModeManagers.stream()
                .map(manager -> {
                    int initialCount = getMethodInvocationCount(manager, "stop");
                    // carrier config enabled, all mode managers should have been shut down once
                    int count = disableWifiInEcm ? initialCount + times : initialCount;
                    if (manager instanceof SoftApManager) {
                        // expect SoftApManager.close() to be called
                        return count + times;
                    } else {
                        // don't expect other Managers close() to be called
                        return count;
                    }
                })
                .collect(Collectors.toList());

        r.run();

        assertInEmergencyMode();

        List<Integer> actualStopInvocationCounts = activeModeManagers.stream()
                .map(manager -> getMethodInvocationCount(manager, "stop"))
                .collect(Collectors.toList());

        String managerNames = activeModeManagers.stream()
                .map(manager -> manager.getClass().getCanonicalName())
                .collect(Collectors.joining(", ", "[", "]"));

        assertWithMessage(managerNames).that(actualStopInvocationCounts)
                .isEqualTo(expectedStopInvocationCounts);
    }

    /** Test that after starting up, ActiveModeWarden is in the DisabledState State. */
    @Test
    public void testDisabledStateAtStartup() {
        assertInDisabledState();
    }

    /**
     * Test that ActiveModeWarden properly enters the EnabledState (in ScanOnlyMode) from the
     * DisabledState state.
     */
    @Test
    public void testEnterScanOnlyModeFromDisabled() throws Exception {
        enterScanOnlyModeActiveState();
    }

    /**
     * Test that ActiveModeWarden enables hidden network scanning in scan-only-mode
     * if configured to do.
     */
    @Test
    public void testScanOnlyModeScanHiddenNetworks() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifiScanHiddenNetworksScanOnlyMode))
                .thenReturn(true);

        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_SCAN_ONLY), anyBoolean());
        verify(mScanRequestProxy).enableScanning(true, true);
    }

    /**
     * Test that ActiveModeWarden properly starts the SoftApManager from the
     * DisabledState state.
     */
    @Test
    public void testEnterSoftApModeFromDisabled() throws Exception {
        enterSoftApActiveMode();
    }

    /**
     * Test that ActiveModeWarden properly starts the SoftApManager from another state.
     */
    @Test
    public void testEnterSoftApModeFromDifferentState() throws Exception {
        enterClientModeActiveState();
        assertInEnabledState();
        reset(mBatteryStats, mScanRequestProxy);
        enterSoftApActiveMode();
    }

    /**
     * Test that we can disable wifi fully from the EnabledState (in ScanOnlyMode).
     */
    @Test
    public void testDisableWifiFromScanOnlyModeActiveState() throws Exception {
        enterScanOnlyModeActiveState();

        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        verify(mClientModeManager).stop();
        verify(mBatteryStats).reportWifiOff();
        assertInDisabledState();
    }

    /**
     * Test that we can disable wifi when SoftApManager is active and not impact softap.
     */
    @Test
    public void testDisableWifiFromSoftApModeActiveStateDoesNotStopSoftAp() throws Exception {
        enterSoftApActiveMode();
        enterScanOnlyModeActiveState();

        reset(mDefaultClientModeManager);
        enterStaDisabledMode(true);
        verify(mSoftApManager, never()).stop();
        verify(mBatteryStats, never()).reportWifiOff();
    }

    /**
     * Test that we can switch from the EnabledState (in ScanOnlyMode) to another mode.
     */
    @Test
    public void testSwitchModeWhenScanOnlyModeActiveState() throws Exception {
        enterScanOnlyModeActiveState();

        reset(mBatteryStats, mScanRequestProxy);
        enterClientModeActiveState(true);
        mLooper.dispatchAll();
    }

    /**
     * Test that we can switch from the EnabledState (in ConnectMode) to another mode.
     */
    @Test
    public void testSwitchModeWhenConnectModeActiveState() throws Exception {
        enterClientModeActiveState();

        verify(mPrimaryChangedCallback).onChange(null, mClientModeManager);

        reset(mBatteryStats, mScanRequestProxy);
        enterScanOnlyModeActiveState(true);
        mLooper.dispatchAll();

        verify(mPrimaryChangedCallback).onChange(mClientModeManager, null);
    }

    /**
     * Test that wifi toggle switching the primary to scan only mode will also remove the additional
     * CMMs.
     */
    @Test
    public void testSwitchFromConnectModeToScanOnlyModeRemovesAdditionalCMMs() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        // request for an additional CMM
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Listener<ConcreteClientModeManager> additionalClientListener =
                requestAdditionalClientModeManager(ROLE_CLIENT_SECONDARY_TRANSIENT,
                        additionalClientModeManager, externalRequestListener, TEST_SSID_2,
                        TEST_BSSID_2);

        // Verify that there exists both a primary and a secondary transient CMM
        List<ClientModeManager> currentCMMs = mActiveModeWarden.getClientModeManagers();
        assertEquals(2, currentCMMs.size());
        assertTrue(currentCMMs.stream().anyMatch(cmm -> cmm.getRole() == ROLE_CLIENT_PRIMARY));
        assertTrue(currentCMMs.stream().anyMatch(
                cmm -> cmm.getRole() == ROLE_CLIENT_SECONDARY_TRANSIENT));

        InOrder inOrder = inOrder(additionalClientModeManager, mClientModeManager);
        // disable wifi and switch primary CMM to scan only mode
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // Verify that we first stop the additional CMM and then switch the primary to scan only
        // mode
        inOrder.verify(additionalClientModeManager).stop();
        inOrder.verify(mClientModeManager).setRole(ROLE_CLIENT_SCAN_ONLY, INTERNAL_REQUESTOR_WS);
    }

    /**
     * Verify that when there are only secondary CMMs available, the user toggling wifi on will
     * create a new primary CMM.
     */
    @Test
    public void testToggleWifiWithOnlySecondaryCmmsCreatesPrimaryOrScanOnlyCmm() throws Exception {
        enterClientModeActiveState();
        verify(mWifiInjector, times(1)).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // toggling wifi on again should be no-op when primary is already available
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector, times(1)).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // Make the primary CMM change to local only secondary role.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);
        mClientListener.onRoleChanged(mClientModeManager);
        mLooper.dispatchAll();

        // Verify that there only exists the ROLE_CLIENT_LOCAL_ONLY CMM.
        List<ClientModeManager> currentCMMs = mActiveModeWarden.getClientModeManagers();
        assertEquals(1, currentCMMs.size());
        assertTrue(currentCMMs.get(0).getRole() == ROLE_CLIENT_LOCAL_ONLY);

        // verify wifi toggling on should recreate the primary CMM
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
    }

    @Test
    public void testPrimaryNotCreatedTwice() throws Exception {
        enterClientModeActiveState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // toggling wifi on again should be no-op when primary is already available
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // Simulate the primary not fully started by making the role null and targetRole primary.
        when(mClientModeManager.getRole()).thenReturn(null);
        when(mClientModeManager.getTargetRole()).thenReturn(ROLE_CLIENT_PRIMARY);

        // Verify that there is no primary, but there is a CMM with targetRole as primary.
        List<ClientModeManager> currentCMMs = mActiveModeWarden.getClientModeManagers();
        assertEquals(1, currentCMMs.size());
        ConcreteClientModeManager currentCmm = (ConcreteClientModeManager) currentCMMs.get(0);
        assertTrue(currentCmm.getRole() == null);
        assertTrue(currentCmm.getTargetRole() == ROLE_CLIENT_PRIMARY);

        // verify wifi toggling on should not create another primary CMM.
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
    }

    /**
     * Reentering EnabledState should be a NOP.
     */
    @Test
    public void testReenterClientModeActiveStateIsNop() throws Exception {
        enterClientModeActiveState();
        verify(mWifiInjector, times(1)).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        // Should not start again.
        verify(mWifiInjector, times(1)).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
    }

    /**
     * Test that we can switch mode when SoftApManager is active to another mode.
     */
    @Test
    public void testSwitchModeWhenSoftApActiveMode() throws Exception {
        enterSoftApActiveMode();

        reset(mWifiNative);

        enterClientModeActiveState();
        mLooper.dispatchAll();
        verify(mSoftApManager, never()).stop();
        assertInEnabledState();
        verify(mWifiNative, never()).teardownAllInterfaces();
    }

    /**
     * Test that we activate SoftApModeManager if we are already in DisabledState due to
     * a failure.
     */
    @Test
    public void testEnterSoftApModeActiveWhenAlreadyInSoftApMode() throws Exception {
        enterSoftApActiveMode();
        // now inject failure through the SoftApManager.Listener
        mSoftApListener.onStartFailure(mSoftApManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);
        assertInDisabledState();
        // clear the first call to start SoftApManager
        reset(mSoftApManager, mBatteryStats, mModeChangeCallback);

        enterSoftApActiveMode();
    }

    /**
     * Test that we return to the DisabledState after a failure is reported when in the
     * EnabledState.
     */
    @Test
    public void testScanOnlyModeFailureWhenActive() throws Exception {
        enterScanOnlyModeActiveState();
        // now inject a failure through the ScanOnlyModeManager.Listener
        mClientListener.onStartFailure(mClientModeManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);
        assertInDisabledState();
        verify(mBatteryStats).reportWifiOff();
    }

    /**
     * Test that we return to the DisabledState after a failure is reported when
     * SoftApManager is active.
     */
    @Test
    public void testSoftApFailureWhenActive() throws Exception {
        enterSoftApActiveMode();
        // now inject failure through the SoftApManager.Listener
        mSoftApListener.onStartFailure(mSoftApManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);
        verify(mBatteryStats).reportWifiOff();
    }

    /**
     * Test that we return to the DisabledState after the ClientModeManager running in ScanOnlyMode
     * is stopped.
     */
    @Test
    public void testScanOnlyModeDisabledWhenActive() throws Exception {
        enterScanOnlyModeActiveState();

        // now inject the stop message through the ScanOnlyModeManager.Listener
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        assertInDisabledState();
        verify(mBatteryStats).reportWifiOff();
    }

    /**
     * Test that we return to the DisabledState after the SoftApManager is stopped.
     */
    @Test
    public void testSoftApDisabledWhenActive() throws Exception {
        enterSoftApActiveMode();
        reset(mWifiNative);
        // now inject failure through the SoftApManager.Listener
        mSoftApListener.onStartFailure(mSoftApManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);
        verify(mBatteryStats).reportWifiOff();
        verifyNoMoreInteractions(mWifiNative);
    }

    /**
     * Verifies that SoftApStateChanged event is being passed from SoftApManager to WifiServiceImpl
     */
    @Test
    public void callsWifiServiceCallbackOnSoftApStateChanged() throws Exception {
        enterSoftApActiveMode();

        mSoftApListener.onStarted(mSoftApManager);
        SoftApState softApState = new SoftApState(
                WifiManager.WIFI_AP_STATE_ENABLED, 0, null, null);
        mSoftApManagerCallback.onStateChanged(softApState);
        mLooper.dispatchAll();

        verify(mSoftApStateMachineCallback).onStateChanged(softApState);
    }

    /**
     * Verifies that SoftApStateChanged event isn't passed to WifiServiceImpl for LOHS,
     * so the state change for LOHS doesn't affect Wifi Tethering indication.
     */
    @Test
    public void doesntCallWifiServiceCallbackOnLOHSStateChanged() throws Exception {
        enterSoftApActiveMode(new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_LOCAL_ONLY, null, mSoftApCapability, TEST_COUNTRYCODE,
                null));

        mSoftApListener.onStarted(mSoftApManager);
        SoftApState softApState = new SoftApState(
                WifiManager.WIFI_AP_STATE_ENABLED, 0, null, null);
        mSoftApManagerCallback.onStateChanged(softApState);
        mLooper.dispatchAll();

        verify(mSoftApStateMachineCallback, never()).onStateChanged(softApState);
        verify(mSoftApStateMachineCallback, never()).onConnectedClientsOrInfoChanged(any(),
                any(), anyBoolean());
    }

    /**
     * Verifies that ConnectedClientsOrInfoChanged event is being passed from SoftApManager
     * to WifiServiceImpl
     */
    @Test
    public void callsWifiServiceCallbackOnSoftApConnectedClientsChanged() throws Exception {
        final Map<String, List<WifiClient>> testClients = new HashMap();
        final Map<String, SoftApInfo> testInfos = new HashMap();
        enterSoftApActiveMode();
        mSoftApManagerCallback.onConnectedClientsOrInfoChanged(testInfos, testClients, false);
        mLooper.dispatchAll();

        verify(mSoftApStateMachineCallback).onConnectedClientsOrInfoChanged(
                testInfos, testClients, false);
    }

    /**
     * Test that we remain in the active state when we get a state change update that scan mode is
     * active.
     */
    @Test
    public void testScanOnlyModeStaysActiveOnEnabledUpdate() throws Exception {
        enterScanOnlyModeActiveState();
        // now inject success through the Listener
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();
        assertInEnabledState();
        verify(mClientModeManager, never()).stop();
    }

    /**
     * Test that a config passed in to the call to enterSoftApMode is used to create the new
     * SoftApManager.
     */
    @Test
    public void testConfigIsPassedToWifiInjector() throws Exception {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("ThisIsAConfig");
        SoftApModeConfiguration softApConfig = new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_TETHERED, configBuilder.build(), mSoftApCapability,
                TEST_COUNTRYCODE, null);
        enterSoftApActiveMode(softApConfig);
    }

    /**
     * Test that when enterSoftAPMode is called with a null config, we pass a null config to
     * WifiInjector.makeSoftApManager.
     *
     * Passing a null config to SoftApManager indicates that the default config should be used.
     */
    @Test
    public void testNullConfigIsPassedToWifiInjector() throws Exception {
        enterSoftApActiveMode();
    }

    /**
     * Test that two calls to switch to SoftAPMode in succession ends up with the correct config.
     *
     * Expectation: we should end up in SoftAPMode state configured with the second config.
     */
    @Test
    public void testStartSoftApModeTwiceWithTwoConfigs() throws Exception {
        when(mWifiInjector.getWifiApConfigStore()).thenReturn(mWifiApConfigStore);
        Builder configBuilder1 = new SoftApConfiguration.Builder();
        configBuilder1.setSsid("ThisIsAConfig");
        SoftApModeConfiguration softApConfig1 = new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_TETHERED, configBuilder1.build(),
                mSoftApCapability, TEST_COUNTRYCODE, null);
        Builder configBuilder2 = new SoftApConfiguration.Builder();
        configBuilder2.setSsid("ThisIsASecondConfig");
        SoftApModeConfiguration softApConfig2 = new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_TETHERED, configBuilder2.build(),
                mSoftApCapability, TEST_COUNTRYCODE, null);

        doAnswer(new Answer<SoftApManager>() {
            public SoftApManager answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                mSoftApListener = (Listener<SoftApManager>) args[0];
                return mSoftApManager;
            }
        }).when(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), eq(softApConfig1), any(), any(),
                anyBoolean());
        // make a second softap manager
        SoftApManager softapManager = mock(SoftApManager.class);
        Mutable<Listener<SoftApManager>> softApListener =
                new Mutable<>();
        doAnswer(new Answer<SoftApManager>() {
            public SoftApManager answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                softApListener.value = (Listener<SoftApManager>) args[0];
                return softapManager;
            }
        }).when(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), eq(softApConfig2), any(), any(),
                anyBoolean());

        mActiveModeWarden.startSoftAp(softApConfig1, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mSoftApListener.onStarted(mSoftApManager);
        mActiveModeWarden.startSoftAp(softApConfig2, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        softApListener.value.onStarted(softapManager);

        verify(mWifiInjector, times(2)).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
        verify(mBatteryStats).reportWifiOn();
    }

    /**
     * Test that we safely disable wifi if it is already disabled.
     */
    @Test
    public void disableWifiWhenAlreadyOff() throws Exception {
        enterStaDisabledMode(false);
        verify(mWifiNative).getSupportedFeatureSet(null);
        verify(mWifiNative).isStaApConcurrencySupported();
        verify(mWifiNative).isStaStaConcurrencySupported();
        verify(mWifiNative).isP2pStaConcurrencySupported();
        verifyZeroInteractions(mWifiNative);
    }

    /**
     * Trigger recovery and a bug report if we see a native failure
     * while the device is not shutting down
     */
    @Test
    public void handleWifiNativeFailureDeviceNotShuttingDown() throws Exception {
        mWifiNativeStatusListener.onStatusChanged(false);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).triggerBugReportDataCapture(
                WifiDiagnostics.REPORT_REASON_WIFINATIVE_FAILURE);
        verify(mSelfRecovery).trigger(eq(SelfRecovery.REASON_WIFINATIVE_FAILURE));
    }

    /**
     * Verify the device shutting down doesn't trigger recovery or bug report.
     */
    @Test
    public void handleWifiNativeFailureDeviceShuttingDown() throws Exception {
        mActiveModeWarden.notifyShuttingDown();
        mWifiNativeStatusListener.onStatusChanged(false);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics, never()).triggerBugReportDataCapture(
                WifiDiagnostics.REPORT_REASON_WIFINATIVE_FAILURE);
        verify(mSelfRecovery, never()).trigger(eq(SelfRecovery.REASON_WIFINATIVE_FAILURE));
    }

    /**
     * Verify an onStatusChanged callback with "true" does not trigger recovery.
     */
    @Test
    public void handleWifiNativeStatusReady() throws Exception {
        mWifiNativeStatusListener.onStatusChanged(true);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics, never()).triggerBugReportDataCapture(
                WifiDiagnostics.REPORT_REASON_WIFINATIVE_FAILURE);
        verify(mSelfRecovery, never()).trigger(eq(SelfRecovery.REASON_WIFINATIVE_FAILURE));
    }

    /**
     * Verify that mode stop is safe even if the underlying Client mode exited already.
     */
    @Test
    public void shutdownWifiDoesNotCrashWhenClientModeExitsOnDestroyed() throws Exception {
        enterClientModeActiveState();

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        shutdownWifi();

        assertInDisabledState();
    }

    /**
     * Verify that an interface destruction callback is safe after already having been stopped.
     */
    @Test
    public void onDestroyedCallbackDoesNotCrashWhenClientModeAlreadyStopped() throws Exception {
        enterClientModeActiveState();

        shutdownWifi();

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        assertInDisabledState();
    }

    /**
     * Verify that mode stop is safe even if the underlying softap mode exited already.
     */
    @Test
    public void shutdownWifiDoesNotCrashWhenSoftApExitsOnDestroyed() throws Exception {
        enterSoftApActiveMode();

        mSoftApListener.onStopped(mSoftApManager);
        mLooper.dispatchAll();
        SoftApState softApState = new SoftApState(
                WifiManager.WIFI_AP_STATE_DISABLED, 0, null, null);
        mSoftApManagerCallback.onStateChanged(softApState);
        mLooper.dispatchAll();

        shutdownWifi();

        verify(mSoftApStateMachineCallback).onStateChanged(softApState);
    }

    /**
     * Verify that an interface destruction callback is safe after already having been stopped.
     */
    @Test
    public void onDestroyedCallbackDoesNotCrashWhenSoftApModeAlreadyStopped() throws Exception {
        enterSoftApActiveMode();

        shutdownWifi();

        mSoftApListener.onStopped(mSoftApManager);
        SoftApState softApState = new SoftApState(
                WifiManager.WIFI_AP_STATE_DISABLED, 0, null, null);
        mSoftApManagerCallback.onStateChanged(softApState);
        mLooper.dispatchAll();

        verify(mSoftApStateMachineCallback).onStateChanged(softApState);
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);
    }

    /**
     * Verify that we do not crash when calling dump and wifi is fully disabled.
     */
    @Test
    public void dumpWhenWifiFullyOffDoesNotCrash() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        mActiveModeWarden.dump(null, writer, null);
    }

    /**
     * Verify that we trigger dump on active mode managers.
     */
    @Test
    public void dumpCallsActiveModeManagers() throws Exception {
        enterSoftApActiveMode();
        enterClientModeActiveState();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        mActiveModeWarden.dump(null, writer, null);

        verify(mSoftApManager).dump(null, writer, null);
        verify(mClientModeManager).dump(null, writer, null);
    }

    /**
     * Verify that stopping tethering doesn't stop LOHS.
     */
    @Test
    public void testStopTetheringButNotLOHS() throws Exception {
        // prepare WiFi configurations
        when(mWifiInjector.getWifiApConfigStore()).thenReturn(mWifiApConfigStore);
        SoftApModeConfiguration tetherConfig =
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_TETHERED, null,
                mSoftApCapability, TEST_COUNTRYCODE, null);
        SoftApConfiguration lohsConfigWC = mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                mContext, null, mSoftApCapability);
        SoftApModeConfiguration lohsConfig =
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_LOCAL_ONLY, lohsConfigWC,
                mSoftApCapability, TEST_COUNTRYCODE, null);

        // mock SoftAPManagers
        when(mSoftApManager.getRole()).thenReturn(ROLE_SOFTAP_TETHERED);
        doAnswer(new Answer<SoftApManager>() {
            public SoftApManager answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                mSoftApListener = (Listener<SoftApManager>) args[0];
                return mSoftApManager;
            }
        }).when(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), eq(tetherConfig),
                eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
        // make a second softap manager
        SoftApManager lohsSoftapManager = mock(SoftApManager.class);
        when(lohsSoftapManager.getRole()).thenReturn(ROLE_SOFTAP_LOCAL_ONLY);
        Mutable<Listener<SoftApManager>> lohsSoftApListener = new Mutable<>();
        doAnswer(new Answer<SoftApManager>() {
            public SoftApManager answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                lohsSoftApListener.value = (Listener<SoftApManager>) args[0];
                return lohsSoftapManager;
            }
        }).when(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), eq(lohsConfig),
                eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_LOCAL_ONLY), anyBoolean());

        // enable tethering and LOHS
        mActiveModeWarden.startSoftAp(tetherConfig, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mSoftApListener.onStarted(mSoftApManager);
        mActiveModeWarden.startSoftAp(lohsConfig, TEST_WORKSOURCE);
        mLooper.dispatchAll();
        lohsSoftApListener.value.onStarted(lohsSoftapManager);
        verify(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), eq(tetherConfig),
                eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
        verify(mWifiInjector).makeSoftApManager(any(Listener.class),
                any(WifiServiceImpl.SoftApCallbackInternal.class), eq(lohsConfig),
                eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_LOCAL_ONLY), anyBoolean());
        verify(mBatteryStats).reportWifiOn();

        // disable tethering
        mActiveModeWarden.stopSoftAp(WifiManager.IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        verify(mSoftApManager).stop();
        verify(lohsSoftapManager, never()).stop();

        mSoftApListener.onStopped(mSoftApManager);
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);
    }

    /**
     * Verify that toggling wifi from disabled starts client mode.
     */
    @Test
    public void enableWifi() throws Exception {
        assertInDisabledState();

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY),
                anyBoolean());
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        // always set primary, even with single STA
        verify(mWifiNative).setMultiStaPrimaryConnection(WIFI_IFACE_NAME);

        assertInEnabledState();
    }

    /**
     * Test verifying that we can enter scan mode when the scan mode changes
     */
    @Test
    public void enableScanMode() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(new WorkSource(Process.WIFI_UID)), eq(ROLE_CLIENT_SCAN_ONLY),
                anyBoolean());
        assertInEnabledState();
        verify(mClientModeManager, never()).stop();
    }

    /**
     * Test verifying that we ignore scan enable event when wifi is already enabled.
     */
    @Test
    public void ignoreEnableScanModeWhenWifiEnabled() throws Exception {
        // Turn on WIFI
        assertInDisabledState();
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();
        assertInEnabledState();

        // Now toggle scan only change, should be ignored. We should send a role change
        // again with PRIMARY & the cached requestorWs.
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();
        verify(mClientModeManager).setRole(ROLE_CLIENT_PRIMARY, TEST_WORKSOURCE);
        assertInEnabledState();
        verify(mClientModeManager, never()).stop();
    }

    /**
     * Verify that if scanning is enabled at startup, we enter scan mode
     */
    @Test
    public void testEnterScanModeAtStartWhenSet() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);

        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        assertInEnabledState();
    }

    /**
     * Verify that if Wifi is enabled at startup, we enter client mode
     */
    @Test
    public void testEnterClientModeAtStartWhenSet() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);

        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        verify(mWifiMetrics).noteWifiEnabledDuringBoot(true);

        assertInEnabledState();

        verify(mWifiInjector)
                .makeClientModeManager(any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
    }

    /**
     * Do not enter scan mode if location mode disabled.
     */
    @Test
    public void testDoesNotEnterScanModeWhenLocationModeDisabled() throws Exception {
        // Start a new WifiController with wifi disabled
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);

        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        assertInDisabledState();

        // toggling scan always available is not sufficient for scan mode
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        assertInDisabledState();
    }

    /**
     * Only enter scan mode if location mode enabled
     */
    @Test
    public void testEnterScanModeWhenLocationModeEnabled() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);

        reset(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        ArgumentCaptor<BroadcastReceiver> bcastRxCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
                bcastRxCaptor.capture(),
                argThat(filter -> filter.hasAction(LocationManager.MODE_CHANGED_ACTION)));
        BroadcastReceiver broadcastReceiver = bcastRxCaptor.getValue();

        assertInDisabledState();

        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        Intent intent = new Intent(LocationManager.MODE_CHANGED_ACTION);
        broadcastReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();

        assertInEnabledState();
    }

    /**
     * Do not change Wi-Fi state when airplane mode changes if
     * DISALLOW_CHANGE_WIFI_STATE user restriction is set.
     */
    @Test
    public void testWifiStateUnaffectedByAirplaneMode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CHANGE_WIFI_STATE),
                any())).thenReturn(true);
        when(mSettingsStore.updateAirplaneModeTracker()).thenReturn(true);

        reset(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();

        ArgumentCaptor<BroadcastReceiver> bcastRxCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
                bcastRxCaptor.capture(),
                argThat(filter -> filter.hasAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)));
        BroadcastReceiver broadcastReceiver = bcastRxCaptor.getValue();

        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        broadcastReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();

        verify(mSettingsStore, never()).handleAirplaneModeToggled();

        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CHANGE_WIFI_STATE),
                any())).thenReturn(false);
        broadcastReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();

        verify(mSettingsStore).handleAirplaneModeToggled();
        verify(mLastCallerInfoManager, never()).put(eq(WifiManager.API_WIFI_ENABLED),
                anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    }

    /**
     * Wi-Fi remains on when airplane mode changes if airplane mode enhancement is enabled.
     */
    @Test
    public void testWifiRemainsOnAirplaneModeEnhancement() throws Exception {
        enterClientModeActiveState();
        assertInEnabledState();
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);

        // Wi-Fi remains on when APM enhancement enabled
        assertWifiShutDown(() -> {
            when(mSettingsStore.shouldWifiRemainEnabledWhenApmEnabled()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        }, 0);
        verify(mLastCallerInfoManager, never()).put(eq(WifiManager.API_WIFI_ENABLED),
                anyInt(), anyInt(), anyInt(), any(), anyBoolean());

        // Wi-Fi shuts down when APM enhancement disabled
        assertWifiShutDown(() -> {
            when(mSettingsStore.shouldWifiRemainEnabledWhenApmEnabled()).thenReturn(false);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_WIFI_ENABLED), anyInt(), anyInt(),
                anyInt(), eq("android_apm"), eq(false));
    }

    /** Wi-Fi state is restored properly when SoftAp is enabled during airplane mode. */
    @Test
    public void testWifiStateRestoredWhenSoftApEnabledDuringApm() throws Exception {
        enableWifi();
        assertInEnabledState();

        // enabling airplane mode shuts down wifi
        assertWifiShutDown(
                () -> {
                    when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
                    mActiveModeWarden.airplaneModeToggled();
                    mLooper.dispatchAll();
                });
        verify(mLastCallerInfoManager)
                .put(
                        eq(WifiManager.API_WIFI_ENABLED),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        eq("android_apm"),
                        eq(false));
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        // start SoftAp
        mActiveModeWarden.startSoftAp(
                new SoftApModeConfiguration(
                        WifiManager.IFACE_IP_MODE_LOCAL_ONLY,
                        null,
                        mSoftApCapability,
                        TEST_COUNTRYCODE,
                        null),
                TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // disabling airplane mode enables wifi
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        verify(mLastCallerInfoManager)
                .put(
                        eq(WifiManager.API_WIFI_ENABLED),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        eq("android_apm"),
                        eq(true));
    }

    /**
     * Disabling location mode when in scan mode will disable wifi
     */
    @Test
    public void testExitScanModeWhenLocationModeDisabled() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        reset(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        mLooper.dispatchAll();
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        ArgumentCaptor<BroadcastReceiver> bcastRxCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
                bcastRxCaptor.capture(),
                argThat(filter -> filter.hasAction(LocationManager.MODE_CHANGED_ACTION)));
        BroadcastReceiver broadcastReceiver = bcastRxCaptor.getValue();

        assertInEnabledState();

        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);
        Intent intent = new Intent(LocationManager.MODE_CHANGED_ACTION);
        broadcastReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        assertInDisabledState();
    }

    /**
     * When in Client mode, make sure ECM triggers wifi shutdown.
     */
    @Test
    public void testEcmOnFromClientMode() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        enableWifi();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
    }

    /**
     * ECM disabling messages, when in client mode (not expected) do not trigger state changes.
     */
    @Test
    public void testEcmOffInClientMode() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        enableWifi();

        // Test with WifiDisableInECBM turned off
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
    }

    /**
     * When ECM activates and we are in client mode, disabling ECM should return us to client mode.
     */
    @Test
    public void testEcmDisabledReturnsToClientMode() throws Exception {
        enableWifi();
        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // test ecm changed
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        assertInEnabledState();
    }

    /**
     * When Ecm mode is enabled, we should shut down wifi when we get an emergency mode changed
     * update.
     */
    @Test
    public void testEcmOnFromScanMode() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
    }

    /**
     * When Ecm mode is disabled, we should not shut down scan mode if we get an emergency mode
     * changed update, but we should turn off soft AP
     */
    @Test
    public void testEcmOffInScanMode() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        assertInEnabledState();

        // Test with WifiDisableInECBM turned off:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
    }

    /**
     * When ECM is disabled, we should return to scan mode
     */
    @Test
    public void testEcmDisabledReturnsToScanMode() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // test ecm changed
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        assertInEnabledState();
    }

    /**
     * When Ecm mode is enabled, we should shut down wifi when we get an emergency mode changed
     * update.
     */
    @Test
    public void testEcmOnFromSoftApMode() throws Exception {
        enterSoftApActiveMode();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
    }

    /**
     * When Ecm mode is disabled, we should shut down softap mode if we get an emergency mode
     * changed update
     */
    @Test
    public void testEcmOffInSoftApMode() throws Exception {
        enterSoftApActiveMode();

        // Test with WifiDisableInECBM turned off:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);

        // test ecm changed
        emergencyCallbackModeChanged(true);
        mLooper.dispatchAll();

        verify(mSoftApManager).stop();
    }

    /**
     * When ECM is activated and we were in softap mode, we should just return to wifi off when ECM
     * ends
     */
    @Test
    public void testEcmDisabledRemainsDisabledWhenSoftApHadBeenOn() throws Exception {
        assertInDisabledState();

        enterSoftApActiveMode();

        // verify Soft AP Manager started
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            mSoftApListener.onStopped(mSoftApManager);
            mLooper.dispatchAll();
        });

        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);

        // test ecm changed
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        assertInDisabledState();

        // verify no additional calls to enable softap
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
    }

    /**
     * Wifi should remain off when already disabled and we enter ECM.
     */
    @Test
    public void testEcmOnFromDisabledMode() throws Exception {
        assertInDisabledState();
        verify(mWifiInjector, never()).makeSoftApManager(
                any(), any(), any(), any(), any(), anyBoolean());
        verify(mWifiInjector, never()).makeClientModeManager(
                any(), any(), any(), anyBoolean());

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
    }


    /**
     * Updates about call state change also trigger entry of ECM mode.
     */
    @Test
    public void testEnterEcmOnEmergencyCallStateChange() throws Exception {
        assertInDisabledState();

        enableWifi();
        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            // test call state changed
            emergencyCallStateChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });

        emergencyCallStateChanged(false);
        mLooper.dispatchAll();

        assertInEnabledState();
    }

    /**
     * Verify when both ECM and call state changes arrive, we enter ECM mode
     */
    @Test
    public void testEnterEcmWithBothSignals() throws Exception {
        assertInDisabledState();

        enableWifi();
        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            emergencyCallStateChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });

        assertWifiShutDown(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        }, 0); // does not cause another shutdown

        // client mode only started once so far
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        emergencyCallStateChanged(false);
        mLooper.dispatchAll();

        // stay in ecm, do not send an additional client mode trigger
        assertInEmergencyMode();
        // assert that the underlying state is in disabled state
        assertInDisabledState();

        // client mode still only started once
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        // now we can re-enable wifi
        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    /**
     * Verify when both ECM and call state changes arrive but out of order, we enter ECM mode
     */
    @Test
    public void testEnterEcmWithBothSignalsOutOfOrder() throws Exception {
        assertInDisabledState();

        enableWifi();

        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });
        assertInDisabledState();

        assertEnteredEcmMode(() -> {
            emergencyCallStateChanged(true);
            mLooper.dispatchAll();
        }, 0); // does not enter ECM state again

        emergencyCallStateChanged(false);
        mLooper.dispatchAll();

        // stay in ecm, do not send an additional client mode trigger
        assertInEmergencyMode();
        // assert that the underlying state is in disabled state
        assertInDisabledState();

        // client mode still only started once
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        // now we can re-enable wifi
        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    /**
     * Verify when both ECM and call state changes arrive but completely out of order,
     * we still enter and properly exit ECM mode
     */
    @Test
    public void testEnterEcmWithBothSignalsOppositeOrder() throws Exception {
        assertInDisabledState();

        enableWifi();

        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            emergencyCallStateChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });
        assertInDisabledState();

        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        }, 0); // still only 1 shutdown

        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        // stay in ecm, do not send an additional client mode trigger
        assertInEmergencyMode();
        // assert that the underlying state is in disabled state
        assertInDisabledState();

        // client mode still only started once
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        emergencyCallStateChanged(false);
        mLooper.dispatchAll();

        // now we can re-enable wifi
        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    /**
     * When ECM is active, we might get addition signals of ECM mode, drop those additional signals,
     * we must exit when one of each signal is received.
     *
     * In any case, duplicate signals indicate a bug from Telephony. Each signal should be turned
     * off before it is turned on again.
     */
    @Test
    public void testProperExitFromEcmModeWithMultipleMessages() throws Exception {
        assertInDisabledState();

        enableWifi();

        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            emergencyCallStateChanged(true);
            emergencyCallStateChanged(true);
            emergencyCallbackModeChanged(true);
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });
        assertInDisabledState();

        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(false);
            mLooper.dispatchAll();
            emergencyCallbackModeChanged(false);
            mLooper.dispatchAll();
            emergencyCallbackModeChanged(false);
            mLooper.dispatchAll();
            emergencyCallbackModeChanged(false);
            mLooper.dispatchAll();
        }, 0);

        // didn't enter client mode again
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInDisabledState();

        // now we will exit ECM
        emergencyCallStateChanged(false);
        mLooper.dispatchAll();

        // now we can re-enable wifi
        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    /**
     * Toggling wifi on when in ECM does not exit ecm mode and enable wifi
     */
    @Test
    public void testWifiDoesNotToggleOnWhenInEcm() throws Exception {
        assertInDisabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        // test ecm changed
        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // now toggle wifi and verify we do not start wifi
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector, never()).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInDisabledState();
        assertInEmergencyMode();

        // now we will exit ECM
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();
        assertNotInEmergencyMode();

        // Wifi toggle on now takes effect
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(SETTINGS_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    /**
     * Toggling wifi off when in ECM does not disable wifi when getConfigWiFiDisableInECBM is
     * disabled.
     */
    @Test
    public void testWifiDoesNotToggleOffWhenInEcmAndConfigDisabled() throws Exception {
        enableWifi();
        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // Test with WifiDisableInECBM turned off
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);
        // test ecm changed
        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // now toggle wifi and verify we do not start wifi
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // still only called once
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        verify(mClientModeManager, never()).stop();
        assertInEnabledState();
        assertInEmergencyMode();

        // now we will exit ECM
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();
        assertNotInEmergencyMode();

        // Wifi toggle off now takes effect
        verify(mClientModeManager).stop();
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();
    }

    @Test
    public void testAirplaneModeDoesNotToggleOnWhenInEcm() throws Exception {
        // TODO(b/139829963): investigate the expected behavior is when toggling airplane mode in
        //  ECM
    }

    /**
     * Toggling scan mode when in ECM does not exit ecm mode and enable scan mode
     */
    @Test
    public void testScanModeDoesNotToggleOnWhenInEcm() throws Exception {
        assertInDisabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // now enable scanning and verify we do not start wifi
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        verify(mWifiInjector, never()).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInDisabledState();
    }


    /**
     * Toggling softap mode when in ECM does not exit ecm mode and enable softap
     */
    @Test
    public void testSoftApModeDoesNotToggleOnWhenInEcm() throws Exception {
        assertInDisabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // try to start Soft AP
        mActiveModeWarden.startSoftAp(
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_TETHERED, null,
                mSoftApCapability, TEST_COUNTRYCODE, null), TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector, never())
                .makeSoftApManager(any(), any(), any(), eq(TEST_WORKSOURCE), any(), anyBoolean());
        assertInDisabledState();

        // verify triggered Soft AP failure callback
        ArgumentCaptor<SoftApState> softApStateCaptor =
                ArgumentCaptor.forClass(SoftApState.class);
        verify(mSoftApStateMachineCallback).onStateChanged(softApStateCaptor.capture());
        assertThat(softApStateCaptor.getValue().getState()).isEqualTo(WIFI_AP_STATE_FAILED);
        assertThat(softApStateCaptor.getValue().getFailureReason())
                .isEqualTo(SAP_START_FAILURE_GENERAL);

        // try to start LOHS
        mActiveModeWarden.startSoftAp(
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_LOCAL_ONLY, null,
                mSoftApCapability, TEST_COUNTRYCODE, null), TEST_WORKSOURCE);
        mLooper.dispatchAll();

        verify(mWifiInjector, never())
                .makeSoftApManager(any(), any(), any(), eq(TEST_WORKSOURCE), any(), anyBoolean());
        assertInDisabledState();

        // verify triggered LOHS failure callback
        verify(mLohsStateMachineCallback).onStateChanged(softApStateCaptor.capture());
        assertThat(softApStateCaptor.getValue().getState()).isEqualTo(WIFI_AP_STATE_FAILED);
        assertThat(softApStateCaptor.getValue().getFailureReason())
                .isEqualTo(SAP_START_FAILURE_GENERAL);
    }

    /**
     * Toggling off softap mode when in ECM does not induce a mode change
     */
    @Test
    public void testSoftApStoppedDoesNotSwitchModesWhenInEcm() throws Exception {
        assertInDisabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        mActiveModeWarden.stopSoftAp(WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        mLooper.dispatchAll();

        assertInDisabledState();
        verifyNoMoreInteractions(mSoftApManager, mClientModeManager);
    }

    /**
     * Toggling softap mode when in airplane mode needs to enable softap
     */
    @Test
    public void testSoftApModeToggleWhenInAirplaneMode() throws Exception {
        // Test with airplane mode turned on:
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);

        // Turn on SoftAp.
        mActiveModeWarden.startSoftAp(
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_TETHERED, null,
                mSoftApCapability, TEST_COUNTRYCODE, null), TEST_WORKSOURCE);
        mLooper.dispatchAll();
        verify(mWifiInjector)
                .makeSoftApManager(any(), any(), any(), eq(TEST_WORKSOURCE), any(), anyBoolean());

        // Turn off SoftAp.
        mActiveModeWarden.stopSoftAp(WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        mLooper.dispatchAll();

        verify(mSoftApManager).stop();
    }

    /**
     * Toggling off scan mode when in ECM does not induce a mode change
     */
    @Test
    public void testScanModeStoppedSwitchModeToDisabledStateWhenInEcm() throws Exception {
        enterScanOnlyModeActiveState();
        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });

        // Spurious onStopped
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        assertInDisabledState();
    }

    /**
     * Toggling off client mode when in ECM does not induce a mode change
     */
    @Test
    public void testClientModeStoppedSwitchModeToDisabledStateWhenInEcm() throws Exception {
        enterClientModeActiveState();
        assertInEnabledState();

        // Test with WifiDisableInECBM turned on:
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });

        // Spurious onStopped
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        assertInDisabledState();
    }

    /**
     * When AP mode is enabled and wifi was previously in AP mode, we should return to
     * EnabledState after the AP is disabled.
     * Enter EnabledState, activate AP mode, disable AP mode.
     * <p>
     * Expected: AP should successfully start and exit, then return to EnabledState.
     */
    @Test
    public void testReturnToEnabledStateAfterAPModeShutdown() throws Exception {
        enableWifi();
        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        mActiveModeWarden.startSoftAp(
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_TETHERED, null,
                mSoftApCapability, TEST_COUNTRYCODE, null), TEST_WORKSOURCE);
        // add an "unexpected" sta mode stop to simulate a single interface device
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);

        // Now stop the AP
        mSoftApListener.onStopped(mSoftApManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);

        // We should re-enable client mode
        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    /**
     * When in STA mode and SoftAP is enabled and the device supports STA+AP (i.e. the STA wasn't
     * shut down when the AP started), both modes will be running concurrently.
     *
     * Then when the AP is disabled, we should remain in STA mode.
     *
     * Enter EnabledState, activate AP mode, toggle WiFi off.
     * <p>
     * Expected: AP should successfully start and exit, then return to EnabledState.
     */
    @Test
    public void testReturnToEnabledStateAfterWifiEnabledShutdown() throws Exception {
        enableWifi();
        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        mActiveModeWarden.startSoftAp(
                new SoftApModeConfiguration(WifiManager.IFACE_IP_MODE_TETHERED, null,
                mSoftApCapability, TEST_COUNTRYCODE, null), TEST_WORKSOURCE);
        mLooper.dispatchAll();

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mSoftApListener.onStopped(mSoftApManager);
        mLooper.dispatchAll();

        // wasn't called again
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    @Test
    public void testRestartWifiStackInEnabledStateTriggersBugReport() throws Exception {
        enableWifi();

        // note: using a reason that will typical not start a bug report on purpose to guarantee
        // that it is the flag and not the reason which controls it.
        mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                true);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).takeBugReport(anyString(), anyString());
        verify(mSubsystemRestartCallback).onSubsystemRestarting();
    }

    @Test
    public void testRestartWifiWatchdogDoesNotTriggerBugReport() throws Exception {
        enableWifi();
        // note: using a reason that will typical start a bug report on purpose to guarantee that
        // it is the flag and not the reason which controls it.
        mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                false);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics, never()).takeBugReport(anyString(), anyString());
        verify(mSubsystemRestartCallback).onSubsystemRestarting();
    }

    /**
     * When in sta mode, CMD_RECOVERY_DISABLE_WIFI messages should trigger wifi to disable.
     */
    @Test
    public void testRecoveryDisabledTurnsWifiOff() throws Exception {
        enableWifi();
        assertInEnabledState();
        mActiveModeWarden.recoveryDisableWifi();
        mLooper.dispatchAll();
        verify(mClientModeManager).stop();
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);
    }

    /**
     * When wifi is disabled, CMD_RECOVERY_DISABLE_WIFI should not trigger a state change.
     */
    @Test
    public void testRecoveryDisabledWhenWifiAlreadyOff() throws Exception {
        assertInDisabledState();
        assertWifiShutDown(() -> {
            mActiveModeWarden.recoveryDisableWifi();
            mLooper.dispatchAll();
        });
        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS + 10);
        mLooper.dispatchAll();

        // Ensure we did not restart wifi.
        assertInDisabledState();
    }

    /**
     * The command to trigger a WiFi reset should not trigger any action by WifiController if we
     * are not in STA mode.
     * WiFi is not in connect mode, so any calls to reset the wifi stack due to connection failures
     * should be ignored.
     * Create and start WifiController in DisabledState, send command to restart WiFi
     * <p>
     * Expected: WiFiController should not call ActiveModeWarden.disableWifi()
     */
    @Test
    public void testRestartWifiStackInDisabledState() throws Exception {
        assertInDisabledState();

        mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        mLooper.dispatchAll();

        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS + 10);
        mLooper.dispatchAll();

        assertInDisabledState();
        verifyNoMoreInteractions(mClientModeManager, mSoftApManager);
    }

    @Test
    public void testNetworkStateChangeListener() throws Exception {
        IWifiNetworkStateChangedListener testListener =
                mock(IWifiNetworkStateChangedListener.class);
        when(testListener.asBinder()).thenReturn(mock(IBinder.class));

        // register listener and verify results delivered
        mActiveModeWarden.addWifiNetworkStateChangedListener(testListener);
        mActiveModeWarden.onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED);
        verify(testListener).onWifiNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED);

        // unregister listener and verify results no longer delivered
        mActiveModeWarden.removeWifiNetworkStateChangedListener(testListener);
        mActiveModeWarden.onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED);
        verify(testListener, never()).onWifiNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED);
    }

    /**
     * The command to trigger a WiFi reset should trigger a wifi reset in ClientModeImpl through
     * the ActiveModeWarden.shutdownWifi() call when in STA mode.
     * When WiFi is in scan mode, calls to reset the wifi stack due to native failure
     * should trigger a supplicant stop, and subsequently, a driver reload.
     * Create and start WifiController in EnabledState, send command to restart WiFi
     * <p>
     * Expected: WiFiController should call ActiveModeWarden.shutdownWifi() and
     * ActiveModeWarden should enter SCAN_ONLY mode and the wifi driver should be started.
     */
    @Test
    public void testRestartWifiStackInStaScanEnabledState() throws Exception {
        assertInDisabledState();

        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(new WorkSource(Process.WIFI_UID)), eq(ROLE_CLIENT_SCAN_ONLY),
                anyBoolean());

        mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        mLooper.dispatchAll();

        verify(mClientModeManager).stop();
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);

        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
        mLooper.dispatchAll();

        verify(mWifiInjector, times(2)).makeClientModeManager(any(), any(), any(), anyBoolean());
        assertInEnabledState();

        verify(mSubsystemRestartCallback).onSubsystemRestarting();
        verify(mSubsystemRestartCallback).onSubsystemRestarted();
    }

    /**
     * The command to trigger a WiFi reset should trigger a wifi reset in ClientModeImpl through
     * the ActiveModeWarden.shutdownWifi() call when in STA mode.
     * WiFi is in connect mode, calls to reset the wifi stack due to connection failures
     * should trigger a supplicant stop, and subsequently, a driver reload.
     * Create and start WifiController in EnabledState, send command to restart WiFi
     * <p>
     * Expected: WiFiController should call ActiveModeWarden.shutdownWifi() and
     * ActiveModeWarden should enter CONNECT_MODE and the wifi driver should be started.
     */
    @Test
    public void testRestartWifiStackInStaConnectEnabledState() throws Exception {
        enableWifi();
        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        assertWifiShutDown(() -> {
            mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                    true);
            mLooper.dispatchAll();
            // Complete the stop
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });

        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);

        // still only started once
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
        mLooper.dispatchAll();

        // started again
        verify(mWifiInjector, times(2)).makeClientModeManager(any(), any(), any(), anyBoolean());
        assertInEnabledState();

        verify(mSubsystemRestartCallback).onSubsystemRestarting();
        verify(mSubsystemRestartCallback).onSubsystemRestarted();
    }

    /**
     * The command to trigger a WiFi reset should not trigger a reset when in ECM mode.
     * Enable wifi and enter ECM state, send command to restart wifi.
     * <p>
     * Expected: The command to trigger a wifi reset should be ignored and we should remain in ECM
     * mode.
     */
    @Test
    public void testRestartWifiStackDoesNotExitECMMode() throws Exception {
        enableWifi();
        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), eq(false));

        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            emergencyCallStateChanged(true);
            mLooper.dispatchAll();
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });
        assertInEmergencyMode();
        assertInDisabledState();
        verify(mClientModeManager).stop();
        verify(mClientModeManager, atLeastOnce()).getRole();
        verify(mClientModeManager).clearWifiConnectedNetworkScorer();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);

        mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                false);
        mLooper.dispatchAll();

        // wasn't called again
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEmergencyMode();
        assertInDisabledState();

        verify(mClientModeManager, atLeastOnce()).getInterfaceName();
        verify(mClientModeManager, atLeastOnce()).getPreviousRole();
        verifyNoMoreInteractions(mClientModeManager, mSoftApManager);
    }

    /**
     * The command to trigger a WiFi reset should trigger a wifi reset in SoftApManager through
     * the ActiveModeWarden.shutdownWifi() call when in SAP enabled mode.
     */
    @Test
    public void testRestartWifiStackInTetheredSoftApEnabledState() throws Exception {
        enterSoftApActiveMode();
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        // Return true to indicate Wifi recovery in progress
        when(mSelfRecovery.isRecoveryInProgress()).thenReturn(true);
        assertWifiShutDown(() -> {
            mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                    true);
            mLooper.dispatchAll();
            // Complete the stop
            mSoftApListener.onStopped(mSoftApManager);
            mLooper.dispatchAll();
        });

        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);

        // still only started once
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
        // No client mode manager created
        verify(mWifiInjector, never()).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        verify(mSelfRecovery).isRecoveryInProgress();
        verify(mSelfRecovery).onWifiStopped();

        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
        mLooper.dispatchAll();

        // started again
        verify(mWifiInjector, times(2)).makeSoftApManager(
                any(), any(), any(), any(), any(), anyBoolean());
        assertInEnabledState();

        verify(mSelfRecovery).onRecoveryCompleted();
        verify(mSubsystemRestartCallback).onSubsystemRestarting();
        verify(mSubsystemRestartCallback).onSubsystemRestarted();
    }

    /**
     * The command to trigger a WiFi reset should trigger a wifi reset in SoftApManager through
     * the ActiveModeWarden.shutdownWifi() call when in SAP enabled mode.
     * If the shutdown isn't done fast enough to transit to disabled state it should still
     * bring up soft ap manager later.
     */
    @Test
    public void testRestartWifiStackInTetheredSoftApEnabledState_SlowDisable() throws Exception {
        enterSoftApActiveMode();
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());

        assertWifiShutDown(() -> {
            mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                    true);
            mLooper.dispatchAll();
            mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
            mLooper.dispatchAll();
        });
        // Wifi is still not disabled yet.
        verify(mModeChangeCallback, never()).onActiveModeManagerRemoved(mSoftApManager);
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());
        assertInEnabledState();

        // Now complete the stop and transit to disabled state
        mSoftApListener.onStopped(mSoftApManager);
        // mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
        mLooper.dispatchAll();

        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);
        // started again
        verify(mWifiInjector, times(1)).makeSoftApManager(
                any(), any(), any(), any(), any(), anyBoolean());
        assertInDisabledState();

        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
        mLooper.dispatchAll();

        // started again
        verify(mWifiInjector, times(2)).makeSoftApManager(
                any(), any(), any(), any(), any(), anyBoolean());
        assertInEnabledState();

        verify(mSubsystemRestartCallback).onSubsystemRestarting();
        verify(mSubsystemRestartCallback).onSubsystemRestarted();
    }

    /**
     * The command to trigger a WiFi reset should trigger a wifi reset in SoftApManager &
     * ClientModeManager through the ActiveModeWarden.shutdownWifi() call when in STA + SAP
     * enabled mode.
     */
    @Test
    public void testRestartWifiStackInTetheredSoftApAndStaConnectEnabledState() throws Exception {
        enableWifi();
        enterSoftApActiveMode();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());

        assertWifiShutDown(() -> {
            mActiveModeWarden.recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                    true);
            mLooper.dispatchAll();
            // Complete the stop
            mClientListener.onStopped(mClientModeManager);
            mSoftApListener.onStopped(mSoftApManager);
            mLooper.dispatchAll();
        });

        verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);
        verify(mModeChangeCallback).onActiveModeManagerRemoved(mSoftApManager);

        // still only started once
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        verify(mWifiInjector).makeSoftApManager(
                any(), any(), any(), eq(TEST_WORKSOURCE), eq(ROLE_SOFTAP_TETHERED), anyBoolean());

        mLooper.moveTimeForward(TEST_WIFI_RECOVERY_DELAY_MS);
        mLooper.dispatchAll();

        // started again
        verify(mWifiInjector, times(2)).makeClientModeManager(any(), any(), any(), anyBoolean());
        verify(mWifiInjector, times(2)).makeSoftApManager(
                any(), any(), any(), any(), any(), anyBoolean());
        assertInEnabledState();

        verify(mSubsystemRestartCallback).onSubsystemRestarting();
        verify(mSubsystemRestartCallback).onSubsystemRestarted();
    }

    /**
     * Tests that when Wifi is already disabled and another Wifi toggle command arrives,
     * don't enter scan mode if {@link WifiSettingsStore#isScanAlwaysAvailable()} is false.
     * Note: {@link WifiSettingsStore#isScanAlwaysAvailable()} returns false if either the wifi
     * scanning is disabled and airplane mode is on.
     */
    @Test
    public void staDisabled_toggleWifiOff_scanNotAvailable_dontGoToScanMode() {
        assertInDisabledState();

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);

        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        assertInDisabledState();
        verify(mWifiInjector, never()).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), any(), anyBoolean());
    }

    /**
     * Tests that when Wifi is already disabled and another Wifi toggle command arrives,
     * enter scan mode if {@link WifiSettingsStore#isScanAlwaysAvailable()} is true.
     * Note: {@link WifiSettingsStore#isScanAlwaysAvailable()} returns true if both the wifi
     * scanning is enabled and airplane mode is off.
     */
    @Test
    public void staDisabled_toggleWifiOff_scanAvailable_goToScanMode() {
        assertInDisabledState();

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);

        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_SCAN_ONLY), anyBoolean());
    }

    /**
     * Tests that if the carrier config to disable Wifi is enabled during ECM, Wifi is shut down
     * when entering ECM and turned back on when exiting ECM.
     */
    @Test
    public void ecmDisablesWifi_exitEcm_restartWifi() throws Exception {
        enterClientModeActiveState();

        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);
        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
        assertInEnabledState();
        verify(mClientModeManager).stop();

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        assertNotInEmergencyMode();
        // client mode restarted
        verify(mWifiInjector, times(2)).makeClientModeManager(any(), any(), any(), anyBoolean());
        assertInEnabledState();
    }

    /**
     * Tests that if the carrier config to disable Wifi is not enabled during ECM, Wifi remains on
     * during ECM, and nothing happens after exiting ECM.
     */
    @Test
    public void ecmDoesNotDisableWifi_exitEcm_noOp() throws Exception {
        enterClientModeActiveState();

        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);
        assertEnteredEcmMode(() -> {
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });
        assertInEnabledState();
        verify(mClientModeManager, never()).stop();

        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        assertNotInEmergencyMode();
        // client mode manager not started again
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());
        assertInEnabledState();
    }

    @Test
    public void testUpdateCapabilityInSoftApActiveMode() throws Exception {
        SoftApCapability testCapability = new SoftApCapability(0);
        enterSoftApActiveMode();
        mActiveModeWarden.updateSoftApCapability(testCapability,
                WifiManager.IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        verify(mSoftApManager).updateCapability(testCapability);
    }

    @Test
    public void testUpdateConfigInSoftApActiveMode() throws Exception {
        SoftApConfiguration testConfig = new SoftApConfiguration.Builder()
                .setSsid("Test123").build();
        enterSoftApActiveMode();
        mActiveModeWarden.updateSoftApConfiguration(testConfig);
        mLooper.dispatchAll();
        verify(mSoftApManager).updateConfiguration(testConfig);
    }

    @Test
    public void testUpdateCapabilityInNonSoftApActiveMode() throws Exception {
        SoftApCapability testCapability = new SoftApCapability(0);
        enterClientModeActiveState();
        mActiveModeWarden.updateSoftApCapability(testCapability,
                WifiManager.IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        verify(mSoftApManager, never()).updateCapability(any());
    }

    @Test
    public void testUpdateLocalModeSoftApCapabilityInTetheredSoftApActiveMode() throws Exception {
        SoftApCapability testCapability = new SoftApCapability(0);
        enterSoftApActiveMode(); // Tethered mode
        mActiveModeWarden.updateSoftApCapability(testCapability,
                WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mSoftApManager, never()).updateCapability(any());
    }

    @Test
    public void testUpdateConfigInNonSoftApActiveMode() throws Exception {
        SoftApConfiguration testConfig = new SoftApConfiguration.Builder()
                .setSsid("Test123").build();
        enterClientModeActiveState();
        mActiveModeWarden.updateSoftApConfiguration(testConfig);
        mLooper.dispatchAll();
        verify(mSoftApManager, never()).updateConfiguration(any());
    }

    @Test
    public void isStaApConcurrencySupported() throws Exception {
        enterClientModeActiveState();
        when(mWifiNative.isStaApConcurrencySupported()).thenReturn(false);
        mClientListener.onStarted(mClientModeManager);
        assertEquals(0L,
                mActiveModeWarden.getSupportedFeatureSet() & WifiManager.WIFI_FEATURE_AP_STA);

        when(mWifiNative.isStaApConcurrencySupported()).thenReturn(true);
        mClientListener.onStarted(mClientModeManager);
        assertEquals(WifiManager.WIFI_FEATURE_AP_STA,
                mActiveModeWarden.getSupportedFeatureSet() & WifiManager.WIFI_FEATURE_AP_STA);
    }

    @Test
    public void isStaStaConcurrencySupported() throws Exception {
        // STA + STA not supported.
        when(mWifiNative.isStaStaConcurrencySupported()).thenReturn(false);
        assertFalse(mActiveModeWarden.isStaStaConcurrencySupportedForLocalOnlyConnections());
        assertFalse(mActiveModeWarden.isStaStaConcurrencySupportedForMbb());
        assertFalse(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections());

        // STA + STA supported, but no use-cases enabled.
        when(mWifiNative.isStaStaConcurrencySupported()).thenReturn(true);
        assertFalse(mActiveModeWarden.isStaStaConcurrencySupportedForLocalOnlyConnections());
        assertFalse(mActiveModeWarden.isStaStaConcurrencySupportedForMbb());
        assertFalse(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections());

        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.isStaStaConcurrencySupportedForLocalOnlyConnections());

        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.isStaStaConcurrencySupportedForMbb());

        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections());
    }

    private Listener<ConcreteClientModeManager> requestAdditionalClientModeManager(
            ClientConnectivityRole additionaClientModeManagerRole,
            ConcreteClientModeManager additionalClientModeManager,
            ExternalClientModeManagerRequestListener externalRequestListener,
            String ssid, String bssid)
            throws Exception {
        enterClientModeActiveState();
        when(additionalClientModeManager.getRequestorWs()).thenReturn(TEST_WORKSOURCE);

        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();

        // Connected to ssid1/bssid1
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);

        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(additionaClientModeManagerRole);

        // request for ssid2/bssid2
        if (additionaClientModeManagerRole == ROLE_CLIENT_LOCAL_ONLY) {
            mActiveModeWarden.requestLocalOnlyClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, ssid, bssid, false);
        } else if (additionaClientModeManagerRole == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, ssid, bssid);
        } else if (additionaClientModeManagerRole == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            mActiveModeWarden.requestSecondaryTransientClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, ssid, bssid);
        }
        mLooper.dispatchAll();
        verify(mWifiInjector)
                .makeClientModeManager(any(), eq(TEST_WORKSOURCE),
                        eq(additionaClientModeManagerRole), anyBoolean());
        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        // capture last use case set
        ArgumentCaptor<Integer> useCaseCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mWifiNative, atLeastOnce()).setMultiStaUseCase(useCaseCaptor.capture());
        int lastUseCaseSet = useCaseCaptor.getValue().intValue();
        // Ensure the hardware is correctly configured for STA + STA
        if (additionaClientModeManagerRole == ROLE_CLIENT_LOCAL_ONLY
                || additionaClientModeManagerRole == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            assertEquals(WifiNative.DUAL_STA_NON_TRANSIENT_UNBIASED, lastUseCaseSet);
        } else if (additionaClientModeManagerRole == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            assertEquals(WifiNative.DUAL_STA_TRANSIENT_PREFER_PRIMARY, lastUseCaseSet);
        }

        // verify last set of primary connection is for WIFI_IFACE_NAME
        ArgumentCaptor<String> ifaceNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(mWifiNative, atLeastOnce()).setMultiStaPrimaryConnection(ifaceNameCaptor.capture());
        assertEquals(WIFI_IFACE_NAME, ifaceNameCaptor.getValue());

        // Returns the new local only client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());
        // the additional CMM never became primary
        verify(mPrimaryChangedCallback, never()).onChange(any(), eq(additionalClientModeManager));
        if (additionaClientModeManagerRole == ROLE_CLIENT_LOCAL_ONLY) {
            assertEquals(Set.of(TEST_WORKSOURCE), mActiveModeWarden.getSecondaryRequestWs());
        }
        return additionalClientListener.value;
    }

    @Test
    public void testRemoveDefaultClientModeManager() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        // Verify removing a non DefaultClientModeManager works properly.
        requestRemoveAdditionalClientModeManager(ROLE_CLIENT_LOCAL_ONLY);

        // Verify that a request to remove DefaultClientModeManager is ignored.
        ClientModeManager defaultClientModeManager = mock(DefaultClientModeManager.class);

        mActiveModeWarden.removeClientModeManager(defaultClientModeManager);
        mLooper.dispatchAll();
        verify(defaultClientModeManager, never()).stop();
    }

    private void requestRemoveAdditionalClientModeManager(
            ClientConnectivityRole role) throws Exception {
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Listener<ConcreteClientModeManager> additionalClientListener =
                requestAdditionalClientModeManager(role, additionalClientModeManager,
                        externalRequestListener, TEST_SSID_2, TEST_BSSID_2);

        mActiveModeWarden.removeClientModeManager(additionalClientModeManager);
        mLooper.dispatchAll();
        verify(additionalClientModeManager).stop();
        additionalClientListener.onStopped(additionalClientModeManager);
        mLooper.dispatchAll();
        verify(mModeChangeCallback).onActiveModeManagerRemoved(additionalClientModeManager);
        // the additional CMM still never became primary
        verify(mPrimaryChangedCallback, never()).onChange(any(), eq(additionalClientModeManager));
    }

    private void requestRemoveAdditionalClientModeManagerWhenNotAllowed(
            ClientConnectivityRole role, boolean clientIsExpected,
            long featureSet) throws Exception {
        enterClientModeActiveState(false, featureSet);

        // Connected to ssid1/bssid1
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(role);

        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        // request for ssid2/bssid2
        if (role == ROLE_CLIENT_LOCAL_ONLY) {
            mActiveModeWarden.requestLocalOnlyClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, false);
        } else if (role == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        } else if (role == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            mActiveModeWarden.requestSecondaryTransientClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        }
        mLooper.dispatchAll();
        verifyNoMoreInteractions(additionalClientModeManager);
        // Returns the existing primary client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        if (clientIsExpected) {
            assertEquals(mClientModeManager, requestedClientModeManager.getValue());

            mActiveModeWarden.removeClientModeManager(requestedClientModeManager.getValue());
        } else {
            assertNull(requestedClientModeManager.getValue());
        }
        mLooper.dispatchAll();
        verifyNoMoreInteractions(additionalClientModeManager);
    }

    private void requestAdditionalClientModeManagerWhenWifiIsOff(
            ClientConnectivityRole role) throws Exception {
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        if (role == ROLE_CLIENT_LOCAL_ONLY) {
            mActiveModeWarden.requestLocalOnlyClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1, false);
        } else if (role == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1);
        } else if (role == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            mActiveModeWarden.requestSecondaryTransientClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1);
        }
        mLooper.dispatchAll();

        verify(externalRequestListener).onAnswer(null);
    }

    public void requestAdditionalClientModeManagerWhenAlreadyPresent(
            ClientConnectivityRole role) throws Exception {
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        requestAdditionalClientModeManager(role, additionalClientModeManager,
                externalRequestListener, TEST_SSID_2, TEST_BSSID_2);

        // set additional CMM connected to ssid2/bssid2
        WifiConfiguration config2 = new WifiConfiguration();
        config2.SSID = TEST_SSID_2;
        when(additionalClientModeManager.getConnectedWifiConfiguration()).thenReturn(config2);
        when(additionalClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_2);

        // request for ssid3/bssid3
        // request for one more CMM (returns the existing one).
        if (role == ROLE_CLIENT_LOCAL_ONLY) {
            mActiveModeWarden.requestLocalOnlyClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_3, TEST_BSSID_3, false);
        } else if (role == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_3, TEST_BSSID_3);
        } else if (role == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            mActiveModeWarden.requestSecondaryTransientClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_3, TEST_BSSID_3);
        }
        mLooper.dispatchAll();

        // Don't make another client mode manager.
        verify(mWifiInjector, times(1))
                .makeClientModeManager(any(), any(), eq(role), anyBoolean());
        // Returns the existing client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener, times(2)).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());
    }

    public void requestAdditionalClientModeManagerWhenAlreadyPresentSameBssid(
            ClientConnectivityRole role) throws Exception {
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        requestAdditionalClientModeManager(role, additionalClientModeManager,
                externalRequestListener, TEST_SSID_2, TEST_BSSID_2);

        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());

        // set additional CMM connected to ssid2/bssid2
        WifiConfiguration config2 = new WifiConfiguration();
        config2.SSID = TEST_SSID_2;
        when(additionalClientModeManager.getConnectedWifiConfiguration()).thenReturn(config2);
        when(additionalClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_2);

        // request for the same SSID/BSSID and expect the existing CMM to get returned twice.
        if (role == ROLE_CLIENT_LOCAL_ONLY) {
            mActiveModeWarden.requestLocalOnlyClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, false);
        } else if (role == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        } else if (role == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            mActiveModeWarden.requestSecondaryTransientClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        }
        mLooper.dispatchAll();

        // Don't make another client mode manager.
        verify(mWifiInjector, times(1))
                .makeClientModeManager(any(), any(), eq(role), anyBoolean());
        // Returns the existing client mode manager.
        verify(externalRequestListener, times(2)).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());
    }

    private void requestAdditionalClientModeManagerWhenConnectingToPrimaryBssid(
            ClientConnectivityRole role) throws Exception {
        enterClientModeActiveState();

        // Connected to ssid1/bssid1
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(role);

        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        // request for same ssid1/bssid1
        if (role == ROLE_CLIENT_LOCAL_ONLY) {
            mActiveModeWarden.requestLocalOnlyClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1, false);
        } else if (role == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1);
        } else if (role == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            mActiveModeWarden.requestSecondaryTransientClientModeManager(
                    externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1);
        }
        mLooper.dispatchAll();
        verifyNoMoreInteractions(additionalClientModeManager);
        // Returns the existing primary client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(mClientModeManager, requestedClientModeManager.getValue());
    }

    @Test
    public void requestRemoveLocalOnlyClientModeManager() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        requestRemoveAdditionalClientModeManager(ROLE_CLIENT_LOCAL_ONLY);
    }

    @Test
    public void requestRemoveLocalOnlyClientModeManagerWhenStaStaNotSupported() throws Exception {
        // Ensure that we cannot create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_LOCAL_ONLY, true,
                TEST_FEATURE_SET);
    }

    @Test
    public void requestRemoveLocalOnlyClientModeManagerWhenFeatureDisabled() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_LOCAL_ONLY, true,
                TEST_FEATURE_SET);
    }

    @Test
    public void testRequestSecondaryClientModeManagerWhenWifiIsDisabling()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        // Set wifi to disabling and verify secondary CMM is not obtained
        mActiveModeWarden.setWifiStateForApiCalls(WIFI_STATE_DISABLING);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_1, TEST_BSSID_1, false);
        mLooper.dispatchAll();

        verify(externalRequestListener).onAnswer(null);
    }

    @Test
    public void requestLocalOnlyClientModeManagerWhenWifiIsOff() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        requestAdditionalClientModeManagerWhenWifiIsOff(ROLE_CLIENT_LOCAL_ONLY);
    }

    @Test
    public void requestLocalOnlyClientModeManagerWhenAlreadyPresent() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        requestAdditionalClientModeManagerWhenAlreadyPresent(ROLE_CLIENT_LOCAL_ONLY);
    }

    @Test
    public void requestLocalOnlyClientModeManagerWhenAlreadyPresentSameBssid() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        requestAdditionalClientModeManagerWhenAlreadyPresentSameBssid(ROLE_CLIENT_LOCAL_ONLY);
    }

    @Test
    public void requestLocalOnlyClientModeManagerWhenConnectingToPrimaryBssid() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));

        requestAdditionalClientModeManagerWhenConnectingToPrimaryBssid(ROLE_CLIENT_LOCAL_ONLY);
    }

    @Test
    public void requestRemoveLocalOnlyClientModeManagerWhenNotSystemAppAndTargetSdkLessThanS()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);

        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                TEST_PACKAGE, Build.VERSION_CODES.S, TEST_UID))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                "system-service", Build.VERSION_CODES.S, Process.SYSTEM_UID))
                .thenReturn(false);
        // Simulate explicit user approval
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, true));
        WorkSource workSource = new WorkSource(TEST_WORKSOURCE);
        workSource.add(SETTINGS_WORKSOURCE);
        verify(mWifiNative).isItPossibleToCreateStaIface(eq(workSource));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_LOCAL_ONLY,
                true,  TEST_FEATURE_SET);
    }

    @Test
    public void requestRemoveLocalOnlyClientModeManagerWhenNotSystemAppAndTargetSdkEqualToS()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                TEST_PACKAGE, Build.VERSION_CODES.S, TEST_UID))
                .thenReturn(false);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        requestRemoveAdditionalClientModeManager(ROLE_CLIENT_LOCAL_ONLY);
    }

    @Test
    public void requestRemoveLoClientModeManagerWhenNotSystemAppAndTargetSdkLessThanSAndCantCreate()
            throws Exception {
        // Ensure that we can't create more client ifaces - so will attempt to fallback (which we
        // should be able to do for <S apps)
        when(mWifiNative.isStaStaConcurrencySupported()).thenReturn(true);
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(false);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                TEST_PACKAGE, Build.VERSION_CODES.S, TEST_UID))
                .thenReturn(true);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_LOCAL_ONLY,
                true,  TEST_FEATURE_SET | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY);
    }

    private void testLoFallbackAboveAndroidS(boolean isStaStaSupported) throws Exception {
        when(mWifiNative.isStaStaConcurrencySupported()).thenReturn(isStaStaSupported);
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(false);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                TEST_PACKAGE, Build.VERSION_CODES.S, TEST_UID))
                .thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        long expectedFeatureSet = TEST_FEATURE_SET;
        if (isStaStaSupported) {
            expectedFeatureSet |= WifiManager.WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY;
        }

        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_LOCAL_ONLY,
                !isStaStaSupported,
                expectedFeatureSet);
    }

    @Test
    public void requestRemoveLoClientModeManagerWhenNotSystemAppAndTargetSdkEqualToSAndCantCreate()
            throws Exception {
        // Ensure that we can't create more client ifaces - so will attempt to fallback (which we
        // can't for >=S apps)
        testLoFallbackAboveAndroidS(true);
    }

    @Test
    public void requestRemoveLoClientModeManagerWhenNotSystemAppAndTargetSdkEqualToSAndCantCreate2()
            throws Exception {
        // Ensure that we can't create more client ifaces and STA+STA is not supported, we
        // fallback even for >=S apps
        testLoFallbackAboveAndroidS(false);
    }

    @Test
    public void requestRemoveSecondaryLongLivedClientModeManager() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        requestRemoveAdditionalClientModeManager(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void requestRemoveSecondaryLongLivedClientModeManagerWhenStaStaNotSupported()
            throws Exception {
        // Ensure that we cannot create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_SECONDARY_LONG_LIVED,
                true,  TEST_FEATURE_SET);
    }

    @Test
    public void requestRemoveSecondaryLongLivedClientModeManagerWhenFeatureDisabled()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_SECONDARY_LONG_LIVED,
                true,  TEST_FEATURE_SET);
    }

    @Test
    public void requestSecondaryLongLivedClientModeManagerWhenWifiIsOff() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        requestAdditionalClientModeManagerWhenWifiIsOff(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void requestSecondaryLongLivedClientModeManagerWhenAlreadyPresent() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        requestAdditionalClientModeManagerWhenAlreadyPresent(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void requestSecondaryLongLivedClientModeManagerWhenAlreadyPresentSameBssid()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        requestAdditionalClientModeManagerWhenAlreadyPresentSameBssid(
                ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void requestSecondaryLongLivedClientModeManagerWhenConnectingToPrimaryBssid()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        requestAdditionalClientModeManagerWhenConnectingToPrimaryBssid(
                ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void requestRemoveSecondaryTransientClientModeManager() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        requestRemoveAdditionalClientModeManager(ROLE_CLIENT_SECONDARY_TRANSIENT);
    }

    @Test
    public void requestRemoveSecondaryTransientClientModeManagerWhenStaStaNotSupported()
            throws Exception {
        // Ensure that we cannot create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_SECONDARY_TRANSIENT,
                true,  TEST_FEATURE_SET);
    }

    @Test
    public void requestRemoveSecondaryTransientClientModeManagerWhenFeatureDisabled()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));
        requestRemoveAdditionalClientModeManagerWhenNotAllowed(ROLE_CLIENT_SECONDARY_TRANSIENT,
                true,  TEST_FEATURE_SET);
    }

    @Test
    public void requestSecondaryTransientClientModeManagerWhenWifiIsOff() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        requestAdditionalClientModeManagerWhenWifiIsOff(ROLE_CLIENT_SECONDARY_TRANSIENT);
    }

    @Test
    public void requestSecondaryTransientClientModeManagerWhenAlreadyPresent() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        requestAdditionalClientModeManagerWhenAlreadyPresent(ROLE_CLIENT_SECONDARY_TRANSIENT);
    }

    @Test
    public void requestSecondaryTransientClientModeManagerWhenAlreadyPresentSameBssid()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        requestAdditionalClientModeManagerWhenAlreadyPresentSameBssid(
                ROLE_CLIENT_SECONDARY_TRANSIENT);
    }

    @Test
    public void requestSecondaryTransientClientModeManagerWhenConnectingToPrimaryBssid()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        requestAdditionalClientModeManagerWhenConnectingToPrimaryBssid(
                ROLE_CLIENT_SECONDARY_TRANSIENT);
    }

    @Test
    public void requestHighPrioSecondaryTransientClientModeManagerWhenConnectedToLocalOnlyBssid()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        enterClientModeActiveState();

        // Primary Connected to ssid1/bssid1
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener1 =
                new Mutable<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener1.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), eq(ROLE_CLIENT_LOCAL_ONLY),
                anyBoolean());
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);

        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        // request for ssid2/bssid2
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, false);
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        additionalClientListener1.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        // Returns the new client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());

        // set additional CMM connected to ssid2/bssid2
        WifiConfiguration config2 = new WifiConfiguration();
        config2.SSID = TEST_SSID_2;
        when(additionalClientModeManager.getConnectedWifiConfiguration()).thenReturn(config2);
        when(additionalClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_2);

        // request for same ssid2/bssid2 for a different role.
        // request for one more CMM (should return the existing local only one).
        mActiveModeWarden.requestSecondaryTransientClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();

        // Don't make another client mode manager, but should switch role of existing client mode
        // manager.
        verify(mWifiInjector, never())
                .makeClientModeManager(any(), any(), eq(ROLE_CLIENT_SECONDARY_TRANSIENT),
                        anyBoolean());
        ArgumentCaptor<Listener<ConcreteClientModeManager>>
                additionalClientListener2 = ArgumentCaptor.forClass(
                        Listener.class);
        verify(additionalClientModeManager).setRole(eq(ROLE_CLIENT_SECONDARY_TRANSIENT),
                eq(TEST_WORKSOURCE), additionalClientListener2.capture());

        // Simulate completion of role switch.
        additionalClientListener2.getValue().onRoleChanged(additionalClientModeManager);

        // Returns the existing client mode manager.
        verify(externalRequestListener, times(2)).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());
    }

    @Test
    public void requestLowPrioSecondaryTransientClientModeManagerWhenConnectedToLocalOnlyBssid()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        enterClientModeActiveState();

        // Primary Connected to ssid1/bssid1
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener1 =
                new Mutable<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener1.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), eq(ROLE_CLIENT_LOCAL_ONLY),
                anyBoolean());
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);

        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        // request for ssid2/bssid2
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, false);
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        additionalClientListener1.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        // Returns the new client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());

        // set additional CMM connected to ssid2/bssid2
        WifiConfiguration config2 = new WifiConfiguration();
        config2.SSID = TEST_SSID_2;
        when(additionalClientModeManager.getConnectedWifiConfiguration()).thenReturn(config2);
        when(additionalClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_2);

        // Now, deny the creation of STA for the new request
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(false);

        // request for same ssid2/bssid2 for a different role.
        // request for one more CMM (should return null).
        mActiveModeWarden.requestSecondaryTransientClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();

        // Don't make another client mode manager or change role
        verify(mWifiInjector, never())
                .makeClientModeManager(any(), any(), eq(ROLE_CLIENT_SECONDARY_TRANSIENT),
                        anyBoolean());
        verify(additionalClientModeManager, never()).setRole(eq(ROLE_CLIENT_SECONDARY_TRANSIENT),
                eq(TEST_WORKSOURCE), any());

        // Ensure the request is rejected.
        verify(externalRequestListener, times(2)).onAnswer(requestedClientModeManager.capture());
        assertNull(requestedClientModeManager.getValue());
    }

    @Test
    public void requestSecondaryTransientClientModeManagerWhenDppInProgress()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        // Create primary STA.
        enterClientModeActiveState();

        // Start DPP session
        when(mDppManager.isSessionInProgress()).thenReturn(true);

        // request secondary transient CMM creation.
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), eq(ROLE_CLIENT_SECONDARY_TRANSIENT),
                anyBoolean());
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        mActiveModeWarden.requestSecondaryTransientClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();

        // verify that we did not create a secondary CMM.
        verifyNoMoreInteractions(additionalClientModeManager);
        // Returns the existing primary client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(mClientModeManager, requestedClientModeManager.getValue());

        // Stop ongoing DPP session.
        when(mDppManager.isSessionInProgress()).thenReturn(false);

        // request secondary transient CMM creation again, now it should be allowed.
        mActiveModeWarden.requestSecondaryTransientClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();
        verify(mWifiInjector)
                .makeClientModeManager(any(), eq(TEST_WORKSOURCE),
                        eq(ROLE_CLIENT_SECONDARY_TRANSIENT), anyBoolean());
        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        // Returns the new secondary client mode manager.
        verify(externalRequestListener, times(2)).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());
    }

    @Test
    public void testRequestForSecondaryLocalOnlyForEnterCarModePrioritized() throws Exception {
        // mock caller to have ENTER_CAR_MODE_PRIORITIZED
        when(mWifiPermissionsUtil.checkEnterCarModePrioritized(anyInt())).thenReturn(true);
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        enterClientModeActiveState();
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);

        // mock requesting local only secondary
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, false);
        mLooper.dispatchAll();
        // Verify the primary is given to the externalRequestListener
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        verify(mWifiInjector, never()).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        assertEquals(ROLE_CLIENT_PRIMARY, requestedClientModeManager.getValue().getRole());

        // Request for non local-only STA and verify the secondary STA is provided instead.
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(any(), any(),
                eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), anyBoolean());

        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        verify(externalRequestListener, times(2)).onAnswer(
                requestedClientModeManager.capture());
        assertEquals(ROLE_CLIENT_SECONDARY_LONG_LIVED,
                requestedClientModeManager.getValue().getRole());
    }

    @Test
    public void testRequestForSecondaryLocalOnlyForShell() throws Exception {
        // mock caller to have ENTER_CAR_MODE_PRIORITIZED
        when(mWifiPermissionsUtil.checkEnterCarModePrioritized(anyInt())).thenReturn(true);
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, false));
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        enterClientModeActiveState();
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);

        // Request will shell uid for local-only STA and verify the secondary is provided instead.
        WorkSource shellWs = new WorkSource(0, "shell");
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, shellWs, TEST_SSID_2, TEST_BSSID_2, false);
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(any(), any(),
                eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        verify(mWifiInjector).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        assertEquals(ROLE_CLIENT_LOCAL_ONLY, requestedClientModeManager.getValue().getRole());
    }

    @Test
    public void configureHwOnMbbSwitch()
            throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Listener<ConcreteClientModeManager> additionalClientListener =
                requestAdditionalClientModeManager(ROLE_CLIENT_SECONDARY_TRANSIENT,
                        additionalClientModeManager, externalRequestListener, TEST_SSID_2,
                        TEST_BSSID_2);

        // Now simulate the MBB role switch.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mClientListener.onRoleChanged(mClientModeManager);

        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        additionalClientListener.onRoleChanged(additionalClientModeManager);

        // verify last use case set is PREFER_PRIMARY
        ArgumentCaptor<Integer> useCaseCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mWifiNative, atLeastOnce()).setMultiStaUseCase(useCaseCaptor.capture());
        int lastUseCaseSet = useCaseCaptor.getValue().intValue();
        assertEquals(WifiNative.DUAL_STA_TRANSIENT_PREFER_PRIMARY, lastUseCaseSet);

        // verify last set of primary connection is for WIFI_IFACE_NAME_1
        ArgumentCaptor<String> ifaceNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(mWifiNative, atLeastOnce()).setMultiStaPrimaryConnection(ifaceNameCaptor.capture());
        assertEquals(WIFI_IFACE_NAME_1, ifaceNameCaptor.getValue());
    }

    @Test
    public void airplaneModeToggleOnDisablesWifi() throws Exception {
        enterClientModeActiveState();
        assertInEnabledState();

        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_WIFI_ENABLED), anyInt(), anyInt(),
                anyInt(), eq("android_apm"), eq(false));

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();
    }

    @Test
    public void testGetActiveModeManagersOrder() throws Exception {
        enableWifi();
        enterSoftApActiveMode();
        assertInEnabledState();

        Collection<ActiveModeManager> activeModeManagers =
                mActiveModeWarden.getActiveModeManagers();
        if (activeModeManagers == null) {
            fail("activeModeManagers list should not be null");
        }
        Object[] modeManagers = activeModeManagers.toArray();
        assertEquals(2, modeManagers.length);
        assertTrue(modeManagers[0] instanceof SoftApManager);
        assertTrue(modeManagers[1] instanceof ConcreteClientModeManager);
    }

    @Test
    public void airplaneModeToggleOnDisablesSoftAp() throws Exception {
        enterSoftApActiveMode();
        assertInEnabledState();

        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });

        mSoftApListener.onStopped(mSoftApManager);
        mLooper.dispatchAll();
        assertInDisabledState();
    }

    @Test
    public void airplaneModeToggleOffIsDeferredWhileProcessingToggleOnWithOneModeManager()
            throws Exception {
        enterClientModeActiveState();
        assertInEnabledState();

        // APM toggle on
        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });


        // APM toggle off before the stop is complete.
        assertInEnabledState();
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        // We should be back to enabled state.
        assertInEnabledState();
    }

    @Test
    public void airplaneModeToggleOffIsDeferredWhileProcessingToggleOnWithOneModeManager2()
            throws Exception {
        enterClientModeActiveState();
        assertInEnabledState();

        // APM toggle on
        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });


        // APM toggle off before the stop is complete.
        assertInEnabledState();
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        // This test is identical to
        // airplaneModeToggleOffIsDeferredWhileProcessingToggleOnWithOneModeManager, except the
        // dispatchAll() here is removed. There could be a race between airplaneModeToggled and
        // mClientListener.onStopped(). See b/160105640#comment5.

        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();

        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        // We should be back to enabled state.
        assertInEnabledState();
    }

    @Test
    public void airplaneModeToggleOffIsDeferredWhileProcessingToggleOnWithTwoModeManager()
            throws Exception {
        enterClientModeActiveState();
        enterSoftApActiveMode();
        assertInEnabledState();

        // APM toggle on
        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });


        // APM toggle off before the stop is complete.
        assertInEnabledState();
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();

        // AP stopped, should not process APM toggle.
        mSoftApListener.onStopped(mSoftApManager);
        mLooper.dispatchAll();
        verify(mWifiInjector, times(1)).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        // STA also stopped, should process APM toggle.
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        verify(mWifiInjector, times(2)).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_PRIMARY), anyBoolean());

        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();

        // We should be back to enabled state.
        assertInEnabledState();
    }

    @Test
    public void propagateVerboseLoggingFlagToClientModeManager() throws Exception {
        mActiveModeWarden.enableVerboseLogging(true);
        enterClientModeActiveState();
        assertInEnabledState();
        verify(mWifiInjector).makeClientModeManager(any(), any(), any(), eq(true));

        mActiveModeWarden.enableVerboseLogging(false);
        verify(mClientModeManager).enableVerboseLogging(false);
    }

    @Test
    public void propagateConnectedWifiScorerToPrimaryClientModeManager() throws Exception {
        IBinder iBinder = mock(IBinder.class);
        IWifiConnectedNetworkScorer iScorer = mock(IWifiConnectedNetworkScorer.class);
        mActiveModeWarden.setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);
        verify(iScorer).onSetScoreUpdateObserver(mExternalScoreUpdateObserverProxy);
        enterClientModeActiveState();
        assertInEnabledState();
        verify(mClientModeManager).setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);

        mActiveModeWarden.clearWifiConnectedNetworkScorer();
        verify(mClientModeManager).clearWifiConnectedNetworkScorer();

        mActiveModeWarden.setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);
        verify(mClientModeManager, times(2)).setWifiConnectedNetworkScorer(iBinder, iScorer,
                TEST_UID);
    }

    @Test
    public void propagateConnectedWifiScorerToPrimaryClientModeManager_enterScanOnlyState()
            throws Exception {
        IBinder iBinder = mock(IBinder.class);
        IWifiConnectedNetworkScorer iScorer = mock(IWifiConnectedNetworkScorer.class);
        mActiveModeWarden.setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);
        verify(iScorer).onSetScoreUpdateObserver(mExternalScoreUpdateObserverProxy);
        enterClientModeActiveState();
        assertInEnabledState();
        verify(mClientModeManager).setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);

        enterScanOnlyModeActiveState(true);

        verify(mClientModeManager).clearWifiConnectedNetworkScorer();
    }

    @Test
    public void handleWifiScorerSetScoreUpdateObserverFailure() throws Exception {
        IBinder iBinder = mock(IBinder.class);
        IWifiConnectedNetworkScorer iScorer = mock(IWifiConnectedNetworkScorer.class);
        doThrow(new RemoteException()).when(iScorer).onSetScoreUpdateObserver(any());
        mActiveModeWarden.setWifiConnectedNetworkScorer(iBinder, iScorer, TEST_UID);
        verify(iScorer).onSetScoreUpdateObserver(mExternalScoreUpdateObserverProxy);
        enterClientModeActiveState();
        assertInEnabledState();
        // Ensure we did not propagate the scorer.
        verify(mClientModeManager, never()).setWifiConnectedNetworkScorer(iBinder, iScorer,
                TEST_UID);
    }

    /** Verify that the primary changed callback is triggered when entering client mode. */
    @Test
    public void testAddPrimaryClientModeManager() throws Exception {
        enterClientModeActiveState();

        verify(mPrimaryChangedCallback).onChange(null, mClientModeManager);
    }

    /** Verify the primary changed callback is not triggered when there is no primary. */
    @Test
    public void testNoAddPrimaryClientModeManager() throws Exception {
        enterScanOnlyModeActiveState();

        verify(mPrimaryChangedCallback, never()).onChange(any(), any());
    }

    /**
     * Verify the primary changed callback is triggered when changing the primary from one
     * ClientModeManager to another.
     */
    @Test
    public void testSwitchPrimaryClientModeManager() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_TRANSIENT, false));

        enterClientModeActiveState();

        verify(mPrimaryChangedCallback).onChange(null, mClientModeManager);

        // Connected to ssid1/bssid1
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), eq(ROLE_CLIENT_SECONDARY_TRANSIENT),
                anyBoolean());
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        // request for ssid2/bssid2
        mActiveModeWarden.requestSecondaryTransientClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(TEST_WORKSOURCE), eq(ROLE_CLIENT_SECONDARY_TRANSIENT), anyBoolean());
        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        // Returns the new client mode manager.
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        assertEquals(additionalClientModeManager, requestedClientModeManager.getValue());

        // primary didn't change yet
        verify(mPrimaryChangedCallback, never()).onChange(any(), eq(additionalClientModeManager));

        // change primary
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mClientListener.onRoleChanged(mClientModeManager);
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        additionalClientListener.value.onRoleChanged(additionalClientModeManager);

        // verify callback triggered
        verify(mPrimaryChangedCallback).onChange(mClientModeManager, null);
        verify(mPrimaryChangedCallback).onChange(null, additionalClientModeManager);
    }

    @Test
    public void testRegisterPrimaryCmmChangedCallbackWhenConnectModeActiveState() throws Exception {
        enterClientModeActiveState();

        // register a new primary cmm change callback.
        ActiveModeWarden.PrimaryClientModeManagerChangedCallback primarCmmCallback = mock(
                ActiveModeWarden.PrimaryClientModeManagerChangedCallback.class);
        mActiveModeWarden.registerPrimaryClientModeManagerChangedCallback(primarCmmCallback);
        // Ensure we get the callback immediately.
        verify(primarCmmCallback).onChange(null, mClientModeManager);
    }

    @Test
    public void testGetCmmInRolesWithNullRoleInOneCmm() throws Exception {
        enterClientModeActiveState();

        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);

        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        when(mWifiInjector.makeClientModeManager(
                any(), any(), any(), anyBoolean())).thenReturn(additionalClientModeManager);

        mActiveModeWarden.requestLocalOnlyClientModeManager(
                mock(ExternalClientModeManagerRequestListener.class),
                TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, false);
        mLooper.dispatchAll();

        // No role set, should be ignored.
        when(additionalClientModeManager.getRole()).thenReturn(null);
        assertEquals(1, mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_PRIMARY, ROLE_CLIENT_LOCAL_ONLY).size());

        // Role set, should be included.
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);
        assertEquals(2, mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_PRIMARY, ROLE_CLIENT_LOCAL_ONLY).size());
    }

    /**
     * Helper method to enter the EnabledState and set ClientModeManager in ScanOnlyMode during
     * emergency scan processing.
     */
    private void indicateStartOfEmergencyScan(
            boolean hasAnyOtherStaToggleEnabled,
            @Nullable ActiveModeManager.ClientRole expectedRole)
            throws Exception {
        String fromState = mActiveModeWarden.getCurrentMode();
        mActiveModeWarden.setEmergencyScanRequestInProgress(true);
        mLooper.dispatchAll();

        if (!hasAnyOtherStaToggleEnabled) {
            when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
            mClientListener.onStarted(mClientModeManager);
            mLooper.dispatchAll();
            verify(mWifiInjector).makeClientModeManager(
                    any(), eq(SETTINGS_WORKSOURCE), eq(ROLE_CLIENT_SCAN_ONLY), anyBoolean());
            verify(mModeChangeCallback).onActiveModeManagerAdded(mClientModeManager);
            verify(mScanRequestProxy).enableScanning(true, false);
            verify(mBatteryStats).reportWifiOn();
            verify(mBatteryStats).reportWifiState(
                    BatteryStatsManager.WIFI_STATE_OFF_SCANNING, null);
        } else {
            verify(mClientModeManager).setRole(eq(expectedRole), any());
            verify(mClientModeManager, never()).stop();
            assertEquals(fromState, mActiveModeWarden.getCurrentMode());
        }
        assertInEnabledState();
    }

    private void indicateEndOfEmergencyScan(
            boolean hasAnyOtherStaToggleEnabled,
            @Nullable ActiveModeManager.ClientRole expectedRole) {
        String fromState = mActiveModeWarden.getCurrentMode();
        mActiveModeWarden.setEmergencyScanRequestInProgress(false);
        mLooper.dispatchAll();
        if (!hasAnyOtherStaToggleEnabled) {
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
            verify(mModeChangeCallback).onActiveModeManagerRemoved(mClientModeManager);
            verify(mScanRequestProxy).enableScanning(false, false);
            assertInDisabledState();
        } else {
            // Nothing changes.
            verify(mClientModeManager).setRole(eq(expectedRole), any());
            verify(mClientModeManager, never()).stop();
            assertEquals(fromState, mActiveModeWarden.getCurrentMode());
        }
    }

    @Test
    public void testEmergencyScanWhenWifiDisabled() throws Exception {
        // Wifi fully disabled.
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);

        indicateStartOfEmergencyScan(false, null);

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(false, null);
    }

    @Test
    public void testEmergencyScanWhenWifiEnabled() throws Exception {
        // Wifi enabled.
        enterClientModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        indicateStartOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);
    }

    @Test
    public void testEmergencyScanWhenScanOnlyModeEnabled() throws Exception {
        // Scan only enabled.
        enterScanOnlyModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        indicateStartOfEmergencyScan(true, ROLE_CLIENT_SCAN_ONLY);

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(true, ROLE_CLIENT_SCAN_ONLY);
    }

    @Test
    public void testEmergencyScanWhenEcmOnWithWifiDisableInEcbm() throws Exception {
        // Wifi enabled.
        enterClientModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        // Test with WifiDisableInECBM turned on
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            // fully shutdown
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });
        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        indicateStartOfEmergencyScan(false, null);

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(false, null);
    }

    @Test
    public void testEmergencyScanWhenEcmOnWithoutWifiDisableInEcbm() throws Exception {
        // Wifi enabled.
        enterClientModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        // Test with WifiDisableInECBM turned off
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        indicateStartOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);
    }

    @Test
    public void testWifiDisableDuringEmergencyScan() throws Exception {
        // Wifi enabled.
        enterClientModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        indicateStartOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);

        // Toggle off wifi
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();

        // Ensure that we switched the role to scan only state because of the emergency scan.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
        mClientListener.onRoleChanged(mClientModeManager);
        mLooper.dispatchAll();
        verify(mClientModeManager).setRole(ROLE_CLIENT_SCAN_ONLY, INTERNAL_REQUESTOR_WS);
        verify(mClientModeManager, never()).stop();
        assertInEnabledState();

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(false, null);
    }

    @Test
    public void testScanOnlyModeDisableDuringEmergencyScan() throws Exception {
        // Scan only enabled.
        enterScanOnlyModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        indicateStartOfEmergencyScan(true, ROLE_CLIENT_SCAN_ONLY);

        // To reset setRole invocation above which is checked below.
        clearInvocations(mClientModeManager);

        // Toggle off scan only mode
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(false);
        mActiveModeWarden.scanAlwaysModeChanged();
        mLooper.dispatchAll();

        // Ensure that we remained in scan only state because of the emergency scan.
        verify(mClientModeManager).setRole(eq(ROLE_CLIENT_SCAN_ONLY), any());
        verify(mClientModeManager, never()).stop();
        assertInEnabledState();

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(false, null);
    }

    @Test
    public void testEcmOffWithWifiDisabledStateDuringEmergencyScan() throws Exception {
        // Wifi enabled.
        enterClientModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        // Test with WifiDisableInECBM turned on
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(true);

        assertWifiShutDown(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
            // fully shutdown
            mClientListener.onStopped(mClientModeManager);
            mLooper.dispatchAll();
        });
        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        indicateStartOfEmergencyScan(false, null);

        // Now turn off ECM
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        // Ensure we turned wifi back on.
        verify(mClientModeManager).setRole(eq(ROLE_CLIENT_PRIMARY), any());
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mClientListener.onRoleChanged(mClientModeManager);
        verify(mScanRequestProxy).enableScanning(true, true);
        assertInEnabledState();

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);
    }

    @Test
    public void testEcmOffWithoutWifiDisabledStateDuringEmergencyScan() throws Exception {
        // Wifi enabled.
        enterClientModeActiveState();

        reset(mBatteryStats, mScanRequestProxy, mModeChangeCallback);

        // Test with WifiDisableInECBM turned off
        when(mFacade.getConfigWiFiDisableInECBM(mContext)).thenReturn(false);

        assertEnteredEcmMode(() -> {
            // test ecm changed
            emergencyCallbackModeChanged(true);
            mLooper.dispatchAll();
        });

        // Now turn off ECM
        emergencyCallbackModeChanged(false);
        mLooper.dispatchAll();

        // Ensure that we remained in connected state.
        verify(mClientModeManager).setRole(eq(ROLE_CLIENT_PRIMARY), any());
        verify(mClientModeManager, never()).stop();
        assertInEnabledState();

        // To reset setRole invocation above which is checked inside |indicateEndOfEmergencyScan|
        clearInvocations(mClientModeManager);

        indicateEndOfEmergencyScan(true, ROLE_CLIENT_PRIMARY);
    }

    @Test
    public void testRequestForSecondaryLocalOnlyForPreSAppWithUserConnect() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                TEST_PACKAGE, Build.VERSION_CODES.S, TEST_UID))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                "system-service", Build.VERSION_CODES.S, Process.SYSTEM_UID))
                .thenReturn(false);
        assertFalse(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, true));
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_SECONDARY_LONG_LIVED, false));

        enterClientModeActiveState();
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);

        // mock requesting local only secondary
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, true);
        mLooper.dispatchAll();
        // Verify the primary is given to the externalRequestListener
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());
        verify(mWifiInjector, never()).makeClientModeManager(
                any(), any(), eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        assertEquals(ROLE_CLIENT_PRIMARY, requestedClientModeManager.getValue().getRole());

        // Request for non local-only STA and verify the secondary STA is provided instead.
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2);
        mLooper.dispatchAll();
        verify(mWifiInjector).makeClientModeManager(any(), any(),
                eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), anyBoolean());

        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        verify(externalRequestListener, times(2)).onAnswer(
                requestedClientModeManager.capture());
        assertEquals(ROLE_CLIENT_SECONDARY_LONG_LIVED,
                requestedClientModeManager.getValue().getRole());
    }

    @Test
    public void testRequestForSecondaryLocalOnlyForAppWithUserConnect() throws Exception {
        // Ensure that we can create more client ifaces.
        when(mWifiNative.isItPossibleToCreateStaIface(any())).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE, TEST_UID)).thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(
                TEST_PACKAGE, Build.VERSION_CODES.S, TEST_UID))
                .thenReturn(false);
        assertTrue(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                TEST_WORKSOURCE, ROLE_CLIENT_LOCAL_ONLY, true));

        enterClientModeActiveState();
        ArgumentCaptor<ClientModeManager> requestedClientModeManager =
                ArgumentCaptor.forClass(ClientModeManager.class);
        ExternalClientModeManagerRequestListener externalRequestListener = mock(
                ExternalClientModeManagerRequestListener.class);
        Mutable<Listener<ConcreteClientModeManager>> additionalClientListener =
                new Mutable<>();
        ConcreteClientModeManager additionalClientModeManager =
                mock(ConcreteClientModeManager.class);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            additionalClientListener.value =
                    (Listener<ConcreteClientModeManager>) args[0];
            return additionalClientModeManager;
        }).when(mWifiInjector).makeClientModeManager(
                any(Listener.class), any(), any(), anyBoolean());
        when(additionalClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME_1);
        when(additionalClientModeManager.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);

        // mock requesting local only secondary
        mActiveModeWarden.requestLocalOnlyClientModeManager(
                externalRequestListener, TEST_WORKSOURCE, TEST_SSID_2, TEST_BSSID_2, true);
        mLooper.dispatchAll();
        WorkSource ws = new WorkSource(TEST_WORKSOURCE);
        ws.add(SETTINGS_WORKSOURCE);
        verify(mWifiInjector).makeClientModeManager(
                any(), eq(ws), eq(ROLE_CLIENT_LOCAL_ONLY), anyBoolean());
        additionalClientListener.value.onStarted(additionalClientModeManager);
        mLooper.dispatchAll();
        // Verify the primary is given to the externalRequestListener
        verify(externalRequestListener).onAnswer(requestedClientModeManager.capture());

        assertEquals(ROLE_CLIENT_LOCAL_ONLY, requestedClientModeManager.getValue().getRole());
    }

    @Test
    public void testSetAndGetWifiState() {
        int invalidState = 5;
        mActiveModeWarden.setWifiStateForApiCalls(WIFI_STATE_ENABLED);
        assertEquals(WIFI_STATE_ENABLED, mActiveModeWarden.getWifiState());
        mActiveModeWarden.setWifiStateForApiCalls(invalidState);
        assertEquals(WIFI_STATE_ENABLED, mActiveModeWarden.getWifiState());
    }

    /**
     * Verifies that getSupportedFeatureSet() adds capabilities based on interface
     * combination.
     */
    @Test
    public void testGetSupportedFeaturesForStaApConcurrency() throws Exception {
        enterScanOnlyModeActiveState();
        long supportedFeaturesFromWifiNative = WifiManager.WIFI_FEATURE_OWE;
        when(mWifiNative.getSupportedFeatureSet(null)).thenReturn(
                supportedFeaturesFromWifiNative);
        when(mWifiNative.isStaApConcurrencySupported()).thenReturn(false);
        mClientListener.onStarted(mClientModeManager);

        assertEquals(supportedFeaturesFromWifiNative,
                mActiveModeWarden.getSupportedFeatureSet());

        when(mWifiNative.isStaApConcurrencySupported()).thenReturn(true);
        mClientListener.onStarted(mClientModeManager);

        assertEquals(supportedFeaturesFromWifiNative | WifiManager.WIFI_FEATURE_AP_STA,
                mActiveModeWarden.getSupportedFeatureSet());
    }

    /**
     * Verifies that getSupportedFeatureSet() adds capabilities based on interface
     * combination.
     */
    @Test
    public void testGetSupportedFeaturesForStaStaConcurrency() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        enterScanOnlyModeActiveState();
        long supportedFeaturesFromWifiNative = WifiManager.WIFI_FEATURE_OWE;
        when(mWifiNative.getSupportedFeatureSet(null)).thenReturn(
                supportedFeaturesFromWifiNative);

        mClientListener.onStarted(mClientModeManager);
        assertEquals(supportedFeaturesFromWifiNative, mActiveModeWarden.getSupportedFeatureSet());

        when(mWifiNative.isStaStaConcurrencySupported()).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled))
                .thenReturn(true);
        mClientListener.onStarted(mClientModeManager);
        assertEquals(supportedFeaturesFromWifiNative
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY,
                mActiveModeWarden.getSupportedFeatureSet());

        when(mResources.getBoolean(
                R.bool.config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled))
                .thenReturn(true);
        mClientListener.onStarted(mClientModeManager);
        assertEquals(supportedFeaturesFromWifiNative
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_MBB,
                mActiveModeWarden.getSupportedFeatureSet());

        when(mResources.getBoolean(R.bool.config_wifiMultiStaRestrictedConcurrencyEnabled))
                .thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiMultiStaMultiInternetConcurrencyEnabled))
                .thenReturn(true);
        mClientListener.onStarted(mClientModeManager);
        assertEquals(supportedFeaturesFromWifiNative
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_MBB
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_RESTRICTED
                        | WifiManager.WIFI_FEATURE_ADDITIONAL_STA_MULTI_INTERNET,
                mActiveModeWarden.getSupportedFeatureSet());
    }

    private long testGetSupportedFeaturesCaseForMacRandomization(
            long supportedFeaturesFromWifiNative, boolean apMacRandomizationEnabled,
            boolean staConnectedMacRandomizationEnabled, boolean p2pMacRandomizationEnabled) {
        when(mResources.getBoolean(
                R.bool.config_wifi_connected_mac_randomization_supported))
                .thenReturn(staConnectedMacRandomizationEnabled);
        when(mResources.getBoolean(
                R.bool.config_wifi_ap_mac_randomization_supported))
                .thenReturn(apMacRandomizationEnabled);
        when(mResources.getBoolean(
                R.bool.config_wifi_p2p_mac_randomization_supported))
                .thenReturn(p2pMacRandomizationEnabled);
        when(mWifiNative.getSupportedFeatureSet(anyString()))
                .thenReturn(supportedFeaturesFromWifiNative);
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();
        return mActiveModeWarden.getSupportedFeatureSet();
    }

    /** Verifies that syncGetSupportedFeatures() masks out capabilities based on system flags. */
    @Test
    public void syncGetSupportedFeaturesForMacRandomization() throws Exception {
        final long featureStaConnectedMacRandomization =
                WifiManager.WIFI_FEATURE_CONNECTED_RAND_MAC;
        final long featureApMacRandomization =
                WifiManager.WIFI_FEATURE_AP_RAND_MAC;
        final long featureP2pMacRandomization =
                WifiManager.WIFI_FEATURE_CONNECTED_RAND_MAC;

        enterClientModeActiveState();
        assertEquals(featureStaConnectedMacRandomization | featureApMacRandomization
                        | featureP2pMacRandomization,
                testGetSupportedFeaturesCaseForMacRandomization(
                        featureP2pMacRandomization, true, true, true));
        // p2p supported by HAL, but disabled by overlay.
        assertEquals(featureStaConnectedMacRandomization | featureApMacRandomization,
                testGetSupportedFeaturesCaseForMacRandomization(
                        featureP2pMacRandomization, true, true, false));
        assertEquals(featureStaConnectedMacRandomization | featureApMacRandomization,
                testGetSupportedFeaturesCaseForMacRandomization(0, true, true, false));
    }

    private long testGetSupportedFeaturesCaseForRtt(
            long supportedFeaturesFromWifiNative, boolean rttDisabled) {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)).thenReturn(
                !rttDisabled);
        when(mWifiNative.getSupportedFeatureSet(anyString())).thenReturn(
                supportedFeaturesFromWifiNative);
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();
        return mActiveModeWarden.getSupportedFeatureSet();
    }

    /** Verifies that syncGetSupportedFeatures() masks out capabilities based on system flags. */
    @Test
    public void syncGetSupportedFeaturesForRtt() throws Exception {
        final long featureAware = WifiManager.WIFI_FEATURE_AWARE;
        final long featureInfra = WifiManager.WIFI_FEATURE_INFRA;
        final long featureD2dRtt = WifiManager.WIFI_FEATURE_D2D_RTT;
        final long featureD2apRtt = WifiManager.WIFI_FEATURE_D2AP_RTT;
        final long featureLongBits = 0x1000000000L;
        enterClientModeActiveState();
        assertEquals(0, testGetSupportedFeaturesCaseForRtt(0, false));
        assertEquals(0, testGetSupportedFeaturesCaseForRtt(0, true));
        assertEquals(featureAware | featureInfra,
                testGetSupportedFeaturesCaseForRtt(featureAware | featureInfra, false));
        assertEquals(featureAware | featureInfra,
                testGetSupportedFeaturesCaseForRtt(featureAware | featureInfra, true));
        assertEquals(featureInfra | featureD2dRtt,
                testGetSupportedFeaturesCaseForRtt(featureInfra | featureD2dRtt, false));
        assertEquals(featureInfra,
                testGetSupportedFeaturesCaseForRtt(featureInfra | featureD2dRtt, true));
        assertEquals(featureInfra | featureD2apRtt,
                testGetSupportedFeaturesCaseForRtt(featureInfra | featureD2apRtt, false));
        assertEquals(featureInfra,
                testGetSupportedFeaturesCaseForRtt(featureInfra | featureD2apRtt, true));
        assertEquals(featureInfra | featureD2dRtt | featureD2apRtt,
                testGetSupportedFeaturesCaseForRtt(
                        featureInfra | featureD2dRtt | featureD2apRtt, false));
        assertEquals(featureInfra,
                testGetSupportedFeaturesCaseForRtt(
                        featureInfra | featureD2dRtt | featureD2apRtt, true));

        assertEquals(featureLongBits | featureInfra | featureD2dRtt | featureD2apRtt,
                testGetSupportedFeaturesCaseForRtt(
                        featureLongBits | featureInfra | featureD2dRtt | featureD2apRtt, false));
        assertEquals(featureLongBits | featureInfra,
                testGetSupportedFeaturesCaseForRtt(
                        featureLongBits | featureInfra | featureD2dRtt | featureD2apRtt, true));
    }

    @Test
    public void testGetCurrentNetworkScanOnly() throws Exception {
        enterScanOnlyModeActiveState();
        assertNull(mActiveModeWarden.getCurrentNetwork());
    }

    @Test public void testGetCurrentNetworkClientMode() throws Exception {
        mActiveModeWarden.setCurrentNetwork(mNetwork);
        assertEquals(mNetwork, mActiveModeWarden.getCurrentNetwork());
    }

    /**
     *  Verifies that isClientModeManagerConnectedOrConnectingToBssid() checks for Affiliated link
     *  BSSID, if exists.
     */
    @Test
    public void testClientModeManagerConnectedOrConnectingToBssid() {

        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = TEST_SSID_1;
        MacAddress bssid2 = MacAddress.fromString(TEST_BSSID_2);
        when(mClientModeManager.getConnectedWifiConfiguration()).thenReturn(config1);
        when(mClientModeManager.getConnectedBssid()).thenReturn(TEST_BSSID_1);
        when(mClientModeManager.isAffiliatedLinkBssid(eq(bssid2))).thenReturn(true);

        assertTrue(mActiveModeWarden.isClientModeManagerConnectedOrConnectingToBssid(
                mClientModeManager, TEST_SSID_1, TEST_BSSID_2));
    }

    @Test
    public void syncGetSupportedBands() throws Exception {
        enterClientModeActiveState();
        when(mWifiNative.getSupportedBandsForSta(anyString())).thenReturn(11);
        mClientListener.onStarted(mClientModeManager);
        mLooper.dispatchAll();
        verify(mSettingsConfigStore).put(WIFI_NATIVE_SUPPORTED_STA_BANDS, 11);
        assertTrue(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_24_GHZ));
        assertTrue(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_5_GHZ));
        assertFalse(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY));
        assertTrue(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_6_GHZ));
    }

    @Test
    public void testSatelliteModeOnDisableWifi() throws Exception {
        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // Satellite mode is ON, disable Wifi
        assertWifiShutDown(() -> {
            when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
            mActiveModeWarden.handleSatelliteModeChange();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();
    }

    @Test
    public void testSatelliteModeOffEnableWifi() throws Exception {
        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // Satellite mode is ON, disable Wifi
        assertWifiShutDown(() -> {
            when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
            mActiveModeWarden.handleSatelliteModeChange();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is off, enable Wifi
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(false);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInEnabledState();
    }


    @Test
    public void testSatelliteModeOnAirplaneModeOn() throws Exception {
        // Sequence: Satellite ON -> APM ON -> Satellite OFF -> APM OFF

        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // Satellite mode is ON, disable Wifi
        assertWifiShutDown(() -> {
            when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
            mActiveModeWarden.handleSatelliteModeChange();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle on, no change to Wifi state
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is off, no change to Wifi state as APM is on
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(false);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle off, enable Wifi
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInEnabledState();
    }

    @Test
    public void testAirplaneModeOnSatelliteModeOn() throws Exception {
        // Sequence: APM ON -> Satellite ON -> APM OFF -> Satellite OFF

        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // APM toggle on, Wifi disabled
        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is on, no change to Wifi state
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle off, no change to Wifi state
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is off, enable Wifi
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(false);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInEnabledState();
    }

    @Test
    public void testToggleSatelliteModeBeforeAirplaneMode() throws Exception {
        // Sequence: APM ON -> Satellite ON -> Satellite OFF -> APM OFF

        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // APM toggle on, Wifi disabled
        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_WIFI_ENABLED), anyInt(), anyInt(),
                anyInt(), eq("android_apm"), eq(false));
        verify(mLastCallerInfoManager, never()).put(eq(WifiManager.API_WIFI_ENABLED), anyInt(),
                anyInt(), anyInt(), eq("android_apm"), eq(true));
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is on, no change to Wifi state
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is off, no change to Wifi state
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(false);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle off, enable Wifi
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInEnabledState();
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_WIFI_ENABLED), anyInt(), anyInt(),
                anyInt(), eq("android_apm"), eq(true));
    }

    @Test
    public void testToggleAirplaneModeBeforeSatelliteMode() throws Exception {
        // Sequence: Satellite ON -> APM ON -> APM OFF -> Satellite OFF

        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // Satellite mode is ON, disable Wifi
        assertWifiShutDown(() -> {
            when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
            mActiveModeWarden.handleSatelliteModeChange();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle on, no change to Wifi state
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle off, no change to Wifi state
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is off, enable Wifi
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(false);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInEnabledState();
    }

    @Test
    public void testToggleWifiWithSatelliteAndAirplaneMode() throws Exception {
        // Sequence: APM ON -> Wifi ON -> Satellite ON -> APM OFF -> Satellite OFF

        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // APM toggle on, Wifi disabled
        assertWifiShutDown(() -> {
            when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
            mActiveModeWarden.airplaneModeToggled();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // Wifi on
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        mActiveModeWarden.wifiToggled(TEST_WORKSOURCE);
        mLooper.dispatchAll();
        assertInEnabledState();

        // Satellite mode is ON, disable Wifi
        assertWifiShutDown(() -> {
            when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
            mActiveModeWarden.handleSatelliteModeChange();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // APM toggle off, no change to Wifi state
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mActiveModeWarden.airplaneModeToggled();
        mLooper.dispatchAll();
        assertInDisabledState();

        // Satellite mode is off, enable Wifi
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(false);
        mActiveModeWarden.handleSatelliteModeChange();
        mLooper.dispatchAll();
        assertInEnabledState();
    }

    @Test
    public void testSatelliteModemDisableWifiWhenLocationModeChanged() throws Exception {
        when(mSettingsStore.isScanAlwaysAvailable()).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);

        // Wifi is enabled
        enterClientModeActiveState();
        assertInEnabledState();

        // Satellite mode is ON, disable Wifi
        assertWifiShutDown(() -> {
            when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
            mActiveModeWarden.handleSatelliteModeChange();
            mLooper.dispatchAll();
        });
        mClientListener.onStopped(mClientModeManager);
        mLooper.dispatchAll();
        assertInDisabledState();

        // Location state changes
        ArgumentCaptor<BroadcastReceiver> bcastRxCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
                bcastRxCaptor.capture(),
                argThat(filter -> filter.hasAction(LocationManager.MODE_CHANGED_ACTION)));
        BroadcastReceiver broadcastReceiver = bcastRxCaptor.getValue();

        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        Intent intent = new Intent(LocationManager.MODE_CHANGED_ACTION);
        broadcastReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();

        // Ensure Wi-Fi is still disabled
        assertInDisabledState();
    }

    @Test
    public void testOnIdleModeChanged() throws Exception {
        enterClientModeActiveState();
        List<ClientModeManager> currentCMMs = mActiveModeWarden.getClientModeManagers();
        assertTrue(currentCMMs.size() >= 1);
        mActiveModeWarden.onIdleModeChanged(true);
        for (ClientModeManager cmm : currentCMMs) {
            verify(cmm).onIdleModeChanged(true);
        }
    }

    @Test
    public void testWepNotDeprecated() throws Exception {
        when(mWifiGlobals.isWepSupported()).thenReturn(true);
        enterClientModeActiveState(false, TEST_FEATURE_SET | WifiManager.WIFI_FEATURE_WEP);
    }

    @Test
    public void testWpaPersonalNotDeprecated() throws Exception {
        when(mWifiGlobals.isWpaPersonalDeprecated()).thenReturn(false);
        enterClientModeActiveState(false, TEST_FEATURE_SET | WifiManager.WIFI_FEATURE_WPA_PERSONAL);
    }

    @Test
    public void testD2dSupportedWhenInfraStaDisabled() throws Exception {
        when(mWifiNative.isP2pStaConcurrencySupported()).thenReturn(true);
        when(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled()).thenReturn(true);
        mActiveModeWarden = createActiveModeWarden();
        mActiveModeWarden.start();
        verify(mWifiGlobals).setD2dStaConcurrencySupported(true);
        verify(mWifiGlobals, atLeastOnce()).isD2dSupportedWhenInfraStaDisabled();
    }
}
