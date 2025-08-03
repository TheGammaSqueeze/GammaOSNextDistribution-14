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

package android.net.wifi;

import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_METERED;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_LINGER;
import static android.net.wifi.WifiManager.ActionListener;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_SOFTAP;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_AWARE;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_DIRECT;
import static android.net.wifi.WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.REQUEST_REGISTERED;
import static android.net.wifi.WifiManager.OnWifiActivityEnergyInfoListener;
import static android.net.wifi.WifiManager.SAP_START_FAILURE_GENERAL;
import static android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;
import static android.net.wifi.WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION;
import static android.net.wifi.WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED;
import static android.net.wifi.WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED;
import static android.net.wifi.WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY;
import static android.net.wifi.WifiManager.VERBOSE_LOGGING_LEVEL_WIFI_AWARE_ENABLED_ONLY;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_FAILED;
import static android.net.wifi.WifiManager.WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY;
import static android.net.wifi.WifiManager.WIFI_FEATURE_ADDITIONAL_STA_MBB;
import static android.net.wifi.WifiManager.WIFI_FEATURE_ADDITIONAL_STA_MULTI_INTERNET;
import static android.net.wifi.WifiManager.WIFI_FEATURE_ADDITIONAL_STA_RESTRICTED;
import static android.net.wifi.WifiManager.WIFI_FEATURE_AP_STA;
import static android.net.wifi.WifiManager.WIFI_FEATURE_D2D_WHEN_INFRA_STA_DISABLED;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DECORATED_IDENTITY;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP_AKM;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DUAL_BAND_SIMULTANEOUS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OWE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_P2P;
import static android.net.wifi.WifiManager.WIFI_FEATURE_PASSPOINT;
import static android.net.wifi.WifiManager.WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_SCANNER;
import static android.net.wifi.WifiManager.WIFI_FEATURE_T2LM_NEGOTIATION;
import static android.net.wifi.WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WEP;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SAE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SUITE_B;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA_PERSONAL;
import static android.net.wifi.WifiManager.WpsCallback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.DhcpInfo;
import android.net.DhcpOption;
import android.net.MacAddress;
import android.net.TetheringManager;
import android.net.wifi.WifiManager.ActiveCountryCodeChangedCallback;
import android.net.wifi.WifiManager.CoexCallback;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.WifiManager.LocalOnlyHotspotObserver;
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation;
import android.net.wifi.WifiManager.LocalOnlyHotspotSubscription;
import android.net.wifi.WifiManager.NetworkRequestMatchCallback;
import android.net.wifi.WifiManager.NetworkRequestUserSelectionCallback;
import android.net.wifi.WifiManager.OnWifiUsabilityStatsListener;
import android.net.wifi.WifiManager.ScanResultsCallback;
import android.net.wifi.WifiManager.SoftApCallback;
import android.net.wifi.WifiManager.SubsystemRestartTrackingCallback;
import android.net.wifi.WifiManager.SuggestionConnectionStatusListener;
import android.net.wifi.WifiManager.SuggestionUserApprovalStatusListener;
import android.net.wifi.WifiManager.TrafficStateCallback;
import android.net.wifi.WifiManager.WifiConnectedNetworkScorer;
import android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats;
import android.net.wifi.WifiUsabilityStatsEntry.LinkStats;
import android.net.wifi.WifiUsabilityStatsEntry.RadioStats;
import android.net.wifi.WifiUsabilityStatsEntry.RateStats;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.connectivity.WifiActivityEnergyInfo;
import android.os.test.TestLooper;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Unit tests for {@link android.net.wifi.WifiManager}.
 */
@SmallTest
public class WifiManagerTest {

    private static final int ERROR_NOT_SET = -1;
    private static final int ERROR_TEST_REASON = 5;
    private static final int TEST_UID = 14553;
    private static final int TEST_NETWORK_ID = 143;
    private static final String TEST_PACKAGE_NAME = "TestPackage";
    private static final String TEST_FEATURE_ID = "TestFeature";
    private static final String TEST_COUNTRY_CODE = "US";
    private static final String[] TEST_MAC_ADDRESSES = {"da:a1:19:0:0:0"};
    private static final int TEST_SUB_ID = 3;
    private static final String[] TEST_AP_INSTANCES = new String[] {"wlan1", "wlan2"};
    private static final int[] TEST_AP_FREQS = new int[] {2412, 5220};
    private static final int[] TEST_AP_BWS = new int[] {SoftApInfo.CHANNEL_WIDTH_20MHZ_NOHT,
            SoftApInfo.CHANNEL_WIDTH_80MHZ};
    private static final MacAddress[] TEST_AP_BSSIDS = new MacAddress[] {
            MacAddress.fromString("22:33:44:55:66:77"),
            MacAddress.fromString("aa:bb:cc:dd:ee:ff")};
    private static final MacAddress[] TEST_AP_CLIENTS = new MacAddress[] {
            MacAddress.fromString("22:33:44:aa:aa:77"),
            MacAddress.fromString("aa:bb:cc:11:11:ff"),
            MacAddress.fromString("22:bb:cc:11:aa:ff")};
    private static final String TEST_SSID = "\"Test WiFi Networks\"";
    private static final byte[] TEST_OUI = new byte[]{0x01, 0x02, 0x03};
    private static final int TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS = 1000;

    private static final TetheringManager.TetheringRequest TEST_TETHERING_REQUEST =
            new TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build();
    private static final String TEST_INTERFACE_NAME = "test-wlan0";

    @Mock Context mContext;
    @Mock android.net.wifi.IWifiManager mWifiService;
    @Mock ApplicationInfo mApplicationInfo;
    @Mock WifiConfiguration mApConfig;
    @Mock SoftApCallback mSoftApCallback;
    @Mock TrafficStateCallback mTrafficStateCallback;
    @Mock NetworkRequestMatchCallback mNetworkRequestMatchCallback;
    @Mock OnWifiUsabilityStatsListener mOnWifiUsabilityStatsListener;
    @Mock OnWifiActivityEnergyInfoListener mOnWifiActivityEnergyInfoListener;
    @Mock SuggestionConnectionStatusListener mSuggestionConnectionListener;
    @Mock
    WifiManager.LocalOnlyConnectionFailureListener mLocalOnlyConnectionFailureListener;
    @Mock Runnable mRunnable;
    @Mock Executor mExecutor;
    @Mock Executor mAnotherExecutor;
    @Mock ActivityManager mActivityManager;
    @Mock WifiConnectedNetworkScorer mWifiConnectedNetworkScorer;
    @Mock SuggestionUserApprovalStatusListener mSuggestionUserApprovalStatusListener;
    @Mock ActiveCountryCodeChangedCallback mActiveCountryCodeChangedCallback;

    private Handler mHandler;
    private TestLooper mLooper;
    private WifiManager mWifiManager;
    private WifiNetworkSuggestion mWifiNetworkSuggestion;
    private ScanResultsCallback mScanResultsCallback;
    private CoexCallback mCoexCallback;
    private SubsystemRestartTrackingCallback mRestartCallback;
    private int mRestartCallbackMethodRun = 0; // 1: restarting, 2: restarted
    private WifiActivityEnergyInfo mWifiActivityEnergyInfo;

    private HashMap<String, SoftApInfo> mTestSoftApInfoMap = new HashMap<>();
    private HashMap<String, List<WifiClient>> mTestWifiClientsMap = new HashMap<>();
    private SoftApInfo mTestApInfo1 = new SoftApInfo();
    private SoftApInfo mTestApInfo2 = new SoftApInfo();

    /**
     * Util function to check public field which used for softap  in WifiConfiguration
     * same as the value in SoftApConfiguration.
     *
     */
    private boolean compareWifiAndSoftApConfiguration(
            SoftApConfiguration softApConfig, WifiConfiguration wifiConfig) {
        // SoftApConfiguration#toWifiConfiguration() creates a config with an unquoted UTF-8 SSID
        // instead of the double quoted behavior in the javadoc for WifiConfiguration#SSID. Thus,
        // we need to compare the wifi config SSID directly with the unquoted UTF-8 text.
        if (!Objects.equals(wifiConfig.SSID, softApConfig.getWifiSsid().getUtf8Text())) {
            return false;
        }
        if (!Objects.equals(wifiConfig.BSSID, softApConfig.getBssid())) {
            return false;
        }
        if (!Objects.equals(wifiConfig.preSharedKey, softApConfig.getPassphrase())) {
            return false;
        }

        if (wifiConfig.hiddenSSID != softApConfig.isHiddenSsid()) {
            return false;
        }
        switch (softApConfig.getSecurityType()) {
            case SoftApConfiguration.SECURITY_TYPE_OPEN:
                if (wifiConfig.getAuthType() != WifiConfiguration.KeyMgmt.NONE) {
                    return false;
                }
                break;
            case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
                if (wifiConfig.getAuthType() != WifiConfiguration.KeyMgmt.WPA2_PSK) {
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private SoftApConfiguration generatorTestSoftApConfig() {
        return new SoftApConfiguration.Builder()
                .setSsid("TestSSID")
                .setPassphrase("TestPassphrase", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .build();
    }

    private void initTestInfoAndAddToTestMap(int numberOfInfos) {
        if (numberOfInfos > 2) return;
        for (int i = 0; i < numberOfInfos; i++) {
            SoftApInfo info = mTestApInfo1;
            if (i == 1) info = mTestApInfo2;
            info.setFrequency(TEST_AP_FREQS[i]);
            info.setBandwidth(TEST_AP_BWS[i]);
            info.setBssid(TEST_AP_BSSIDS[i]);
            info.setApInstanceIdentifier(TEST_AP_INSTANCES[i]);
            mTestSoftApInfoMap.put(TEST_AP_INSTANCES[i], info);
        }
    }

    private List<WifiClient> initWifiClientAndAddToTestMap(String targetInstance,
            int numberOfClients, int startIdx) {
        if (numberOfClients > 3) return null;
        List<WifiClient> clients = new ArrayList<>();
        for (int i = startIdx; i < startIdx + numberOfClients; i++) {
            WifiClient client = new WifiClient(TEST_AP_CLIENTS[i], targetInstance);
            clients.add(client);
        }
        mTestWifiClientsMap.put(targetInstance, clients);
        return clients;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mHandler = spy(new Handler(mLooper.getLooper()));
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mContext.getOpPackageName()).thenReturn(TEST_PACKAGE_NAME);
        mWifiManager = new WifiManager(mContext, mWifiService, mLooper.getLooper());
        verify(mWifiService).getVerboseLoggingLevel();
        mWifiNetworkSuggestion = new WifiNetworkSuggestion();
        mScanResultsCallback = new ScanResultsCallback() {
            @Override
            public void onScanResultsAvailable() {
                mRunnable.run();
            }
        };
        if (SdkLevel.isAtLeastS()) {
            mCoexCallback = new CoexCallback() {
                @Override
                public void onCoexUnsafeChannelsChanged(
                        @NonNull List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
                    mRunnable.run();
                }
            };
            AttributionSource attributionSource = mock(AttributionSource.class);
            when(mContext.getAttributionSource()).thenReturn(attributionSource);
        }
        mRestartCallback = new SubsystemRestartTrackingCallback() {
            @Override
            public void onSubsystemRestarting() {
                mRestartCallbackMethodRun = 1;
                mRunnable.run();
            }

            @Override
            public void onSubsystemRestarted() {
                mRestartCallbackMethodRun = 2;
                mRunnable.run();
            }
        };
        mWifiActivityEnergyInfo = new WifiActivityEnergyInfo(0, 0, 0, 0, 0, 0);
        mTestSoftApInfoMap.clear();
        mTestWifiClientsMap.clear();
    }

    /**
     * Check the call to setCoexUnsafeChannels calls WifiServiceImpl to setCoexUnsafeChannels with
     * the provided CoexUnsafeChannels and restrictions bitmask.
     */
    @Test
    public void testSetCoexUnsafeChannelsGoesToWifiServiceImpl() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;

        mWifiManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        verify(mWifiService).setCoexUnsafeChannels(unsafeChannels, restrictions);
    }

    /**
     * Verify an IllegalArgumentException if passed a null value for unsafeChannels.
     */
    @Test
    public void testSetCoexUnsafeChannelsThrowsIllegalArgumentExceptionOnNullUnsafeChannels() {
        assumeTrue(SdkLevel.isAtLeastS());
        try {
            mWifiManager.setCoexUnsafeChannels(null, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterCoexCallbackWithNullCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.registerCoexCallback(mExecutor, null);
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterCoexCallbackWithNullExecutor() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.registerCoexCallback(null, mCoexCallback);
    }

    /**
     * Verify client provided callback is being called to the right callback.
     */
    @Test
    public void testAddCoexCallbackAndReceiveEvent() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        ArgumentCaptor<ICoexCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ICoexCallback.Stub.class);
        mWifiManager.registerCoexCallback(new SynchronousExecutor(), mCoexCallback);
        verify(mWifiService).registerCoexCallback(callbackCaptor.capture());
        callbackCaptor.getValue().onCoexUnsafeChannelsChanged(Collections.emptyList(), 0);
        verify(mRunnable).run();
    }

    /**
     * Verify client provided callback is being called to the right executor.
     */
    @Test
    public void testRegisterCoexCallbackWithTheTargetExecutor() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        ArgumentCaptor<ICoexCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ICoexCallback.Stub.class);
        mWifiManager.registerCoexCallback(mExecutor, mCoexCallback);
        verify(mWifiService).registerCoexCallback(callbackCaptor.capture());
        mWifiManager.registerCoexCallback(mAnotherExecutor, mCoexCallback);
        callbackCaptor.getValue().onCoexUnsafeChannelsChanged(Collections.emptyList(), 0);
        verify(mExecutor, never()).execute(any(Runnable.class));
        verify(mAnotherExecutor).execute(any(Runnable.class));
    }

    /**
     * Verify client register unregister then register again, to ensure callback still works.
     */
    @Test
    public void testRegisterUnregisterThenRegisterAgainWithCoexCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        ArgumentCaptor<ICoexCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ICoexCallback.Stub.class);
        mWifiManager.registerCoexCallback(new SynchronousExecutor(), mCoexCallback);
        verify(mWifiService).registerCoexCallback(callbackCaptor.capture());
        mWifiManager.unregisterCoexCallback(mCoexCallback);
        callbackCaptor.getValue().onCoexUnsafeChannelsChanged(Collections.emptyList(), 0);
        verify(mRunnable, never()).run();
        mWifiManager.registerCoexCallback(new SynchronousExecutor(), mCoexCallback);
        callbackCaptor.getValue().onCoexUnsafeChannelsChanged(Collections.emptyList(), 0);
        verify(mRunnable).run();
    }

    /**
     * Verify client unregisterCoexCallback.
     */
    @Test
    public void testUnregisterCoexCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.unregisterCoexCallback(mCoexCallback);
        verify(mWifiService).unregisterCoexCallback(any());
    }

    /**
     * Verify client unregisterCoexCallback with null callback will cause an exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUnregisterCoexCallbackWithNullCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.unregisterCoexCallback(null);
    }

    /**
     * Verify that call is passed to binder.
     */
    @Test
    public void testRestartWifiSubsystem() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.restartWifiSubsystem();
        verify(mWifiService).restartWifiSubsystem();
    }

    /**
     * Verify that can register a subsystem restart tracking callback and that calls are passed
     * through when registered and blocked once unregistered.
     */
    @Test
    public void testRegisterSubsystemRestartTrackingCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mRestartCallbackMethodRun = 0; // none
        ArgumentCaptor<ISubsystemRestartCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISubsystemRestartCallback.Stub.class);
        mWifiManager.registerSubsystemRestartTrackingCallback(new SynchronousExecutor(),
                mRestartCallback);
        verify(mWifiService).registerSubsystemRestartCallback(callbackCaptor.capture());
        mWifiManager.unregisterSubsystemRestartTrackingCallback(mRestartCallback);
        verify(mWifiService).unregisterSubsystemRestartCallback(callbackCaptor.capture());
        callbackCaptor.getValue().onSubsystemRestarting();
        verify(mRunnable, never()).run();
        mWifiManager.registerSubsystemRestartTrackingCallback(new SynchronousExecutor(),
                mRestartCallback);
        callbackCaptor.getValue().onSubsystemRestarting();
        assertEquals(mRestartCallbackMethodRun, 1); // restarting
        callbackCaptor.getValue().onSubsystemRestarted();
        verify(mRunnable, times(2)).run();
        assertEquals(mRestartCallbackMethodRun, 2); // restarted
    }

    /**
     * Check the call to startSoftAp calls WifiService to startSoftAp with the provided
     * WifiConfiguration.  Verify that the return value is propagated to the caller.
     */
    @Test
    public void testStartSoftApCallsServiceWithWifiConfig() throws Exception {
        when(mWifiService.startSoftAp(mApConfig, TEST_PACKAGE_NAME)).thenReturn(true);
        assertTrue(mWifiManager.startSoftAp(mApConfig));

        when(mWifiService.startSoftAp(mApConfig, TEST_PACKAGE_NAME)).thenReturn(false);
        assertFalse(mWifiManager.startSoftAp(mApConfig));
    }

    /**
     * Check the call to startSoftAp calls WifiService to startSoftAp with a null config.  Verify
     * that the return value is propagated to the caller.
     */
    @Test
    public void testStartSoftApCallsServiceWithNullConfig() throws Exception {
        when(mWifiService.startSoftAp(null, TEST_PACKAGE_NAME)).thenReturn(true);
        assertTrue(mWifiManager.startSoftAp(null));

        when(mWifiService.startSoftAp(null, TEST_PACKAGE_NAME)).thenReturn(false);
        assertFalse(mWifiManager.startSoftAp(null));
    }

    /**
     * Check the call to stopSoftAp calls WifiService to stopSoftAp.
     */
    @Test
    public void testStopSoftApCallsService() throws Exception {
        when(mWifiService.stopSoftAp()).thenReturn(true);
        assertTrue(mWifiManager.stopSoftAp());

        when(mWifiService.stopSoftAp()).thenReturn(false);
        assertFalse(mWifiManager.stopSoftAp());
    }

    /**
     * Check the call to validateSoftApConfiguration calls WifiService to
     * validateSoftApConfiguration.
     */
    @Test
    public void testValidateSoftApConfigurationCallsService() throws Exception {
        SoftApConfiguration apConfig = generatorTestSoftApConfig();
        when(mWifiService.validateSoftApConfiguration(any())).thenReturn(true);
        assertTrue(mWifiManager.validateSoftApConfiguration(apConfig));

        when(mWifiService.validateSoftApConfiguration(any())).thenReturn(false);
        assertFalse(mWifiManager.validateSoftApConfiguration(apConfig));
    }

    /**
     * Throws  IllegalArgumentException when calling validateSoftApConfiguration with null.
     */
    @Test
    public void testValidateSoftApConfigurationWithNullConfiguration() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mWifiManager.validateSoftApConfiguration(null));
    }

    /**
     * Check the call to startSoftAp calls WifiService to startSoftAp with the provided
     * WifiConfiguration.  Verify that the return value is propagated to the caller.
     */
    @Test
    public void testStartTetheredHotspotCallsServiceWithSoftApConfig() throws Exception {
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        when(mWifiService.startTetheredHotspot(softApConfig, TEST_PACKAGE_NAME))
                .thenReturn(true);
        assertTrue(mWifiManager.startTetheredHotspot(softApConfig));

        when(mWifiService.startTetheredHotspot(softApConfig, TEST_PACKAGE_NAME))
                .thenReturn(false);
        assertFalse(mWifiManager.startTetheredHotspot(softApConfig));
    }

    /**
     * Check the call to startSoftAp calls WifiService to startSoftAp with a null config.  Verify
     * that the return value is propagated to the caller.
     */
    @Test
    public void testStartTetheredHotspotCallsServiceWithNullConfig() throws Exception {
        when(mWifiService.startTetheredHotspot(null, TEST_PACKAGE_NAME)).thenReturn(true);
        assertTrue(mWifiManager.startTetheredHotspot(null));

        when(mWifiService.startTetheredHotspot(null, TEST_PACKAGE_NAME)).thenReturn(false);
        assertFalse(mWifiManager.startTetheredHotspot(null));
    }

    /**
     * Test creation of a LocalOnlyHotspotReservation and verify that close properly calls
     * WifiService.stopLocalOnlyHotspot.
     */
    @Test
    public void testCreationAndCloseOfLocalOnlyHotspotReservation() throws Exception {
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);

        callback.onStarted(mWifiManager.new LocalOnlyHotspotReservation(softApConfig));

        assertEquals(softApConfig, callback.mRes.getSoftApConfiguration());
        WifiConfiguration wifiConfig = callback.mRes.getWifiConfiguration();
        assertTrue(compareWifiAndSoftApConfiguration(softApConfig, wifiConfig));

        callback.mRes.close();
        verify(mWifiService).stopLocalOnlyHotspot();
    }

    /**
     * Verify stopLOHS is called when try-with-resources is used properly.
     */
    @Test
    public void testLocalOnlyHotspotReservationCallsStopProperlyInTryWithResources()
            throws Exception {
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);

        callback.onStarted(mWifiManager.new LocalOnlyHotspotReservation(softApConfig));

        try (WifiManager.LocalOnlyHotspotReservation res = callback.mRes) {
            assertEquals(softApConfig, res.getSoftApConfiguration());
            WifiConfiguration wifiConfig = callback.mRes.getWifiConfiguration();
            assertTrue(compareWifiAndSoftApConfiguration(softApConfig, wifiConfig));
        }

        verify(mWifiService).stopLocalOnlyHotspot();
    }

    /**
     * Test creation of a LocalOnlyHotspotSubscription.
     * TODO: when registrations are tracked, verify removal on close.
     */
    @Test
    public void testCreationOfLocalOnlyHotspotSubscription() throws Exception {
        try (WifiManager.LocalOnlyHotspotSubscription sub =
                     mWifiManager.new LocalOnlyHotspotSubscription()) {
            sub.close();
        }
    }

    public class TestLocalOnlyHotspotCallback extends LocalOnlyHotspotCallback {
        public boolean mOnStartedCalled = false;
        public boolean mOnStoppedCalled = false;
        public int mFailureReason = -1;
        public LocalOnlyHotspotReservation mRes = null;
        public long mCallingThreadId = -1;

        @Override
        public void onStarted(LocalOnlyHotspotReservation r) {
            mRes = r;
            mOnStartedCalled = true;
            mCallingThreadId = Thread.currentThread().getId();
        }

        @Override
        public void onStopped() {
            mOnStoppedCalled = true;
            mCallingThreadId = Thread.currentThread().getId();
        }

        @Override
        public void onFailed(int reason) {
            mFailureReason = reason;
            mCallingThreadId = Thread.currentThread().getId();
        }
    }

    /**
     * Verify callback is properly plumbed when called.
     */
    @Test
    public void testLocalOnlyHotspotCallback() {
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        assertFalse(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(ERROR_NOT_SET, callback.mFailureReason);
        assertEquals(null, callback.mRes);

        // test onStarted
        WifiManager.LocalOnlyHotspotReservation res =
                mWifiManager.new LocalOnlyHotspotReservation(softApConfig);
        callback.onStarted(res);
        assertEquals(res, callback.mRes);
        assertTrue(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(ERROR_NOT_SET, callback.mFailureReason);

        // test onStopped
        callback.onStopped();
        assertEquals(res, callback.mRes);
        assertTrue(callback.mOnStartedCalled);
        assertTrue(callback.mOnStoppedCalled);
        assertEquals(ERROR_NOT_SET, callback.mFailureReason);

        // test onFailed
        callback.onFailed(ERROR_TEST_REASON);
        assertEquals(res, callback.mRes);
        assertTrue(callback.mOnStartedCalled);
        assertTrue(callback.mOnStoppedCalled);
        assertEquals(ERROR_TEST_REASON, callback.mFailureReason);
    }

    public class TestLocalOnlyHotspotObserver extends LocalOnlyHotspotObserver {
        public boolean mOnRegistered = false;
        public boolean mOnStartedCalled = false;
        public boolean mOnStoppedCalled = false;
        public SoftApConfiguration mConfig = null;
        public LocalOnlyHotspotSubscription mSub = null;
        public long mCallingThreadId = -1;

        @Override
        public void onRegistered(LocalOnlyHotspotSubscription sub) {
            mOnRegistered = true;
            mSub = sub;
            mCallingThreadId = Thread.currentThread().getId();
        }

        @Override
        public void onStarted(SoftApConfiguration config) {
            mOnStartedCalled = true;
            mConfig = config;
            mCallingThreadId = Thread.currentThread().getId();
        }

        @Override
        public void onStopped() {
            mOnStoppedCalled = true;
            mCallingThreadId = Thread.currentThread().getId();
        }
    }

    /**
     * Verify observer is properly plumbed when called.
     */
    @Test
    public void testLocalOnlyHotspotObserver() {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        assertFalse(observer.mOnRegistered);
        assertFalse(observer.mOnStartedCalled);
        assertFalse(observer.mOnStoppedCalled);
        assertEquals(null, observer.mConfig);
        assertEquals(null, observer.mSub);

        WifiManager.LocalOnlyHotspotSubscription sub =
                mWifiManager.new LocalOnlyHotspotSubscription();
        observer.onRegistered(sub);
        assertTrue(observer.mOnRegistered);
        assertFalse(observer.mOnStartedCalled);
        assertFalse(observer.mOnStoppedCalled);
        assertEquals(null, observer.mConfig);
        assertEquals(sub, observer.mSub);

        observer.onStarted(softApConfig);
        assertTrue(observer.mOnRegistered);
        assertTrue(observer.mOnStartedCalled);
        assertFalse(observer.mOnStoppedCalled);
        assertEquals(softApConfig, observer.mConfig);
        assertEquals(sub, observer.mSub);

        observer.onStopped();
        assertTrue(observer.mOnRegistered);
        assertTrue(observer.mOnStartedCalled);
        assertTrue(observer.mOnStoppedCalled);
        assertEquals(softApConfig, observer.mConfig);
        assertEquals(sub, observer.mSub);
    }

    @Test
    public void testSetSsidsDoNotBlocklist() throws Exception {
        // test non-empty set
        List<WifiSsid> expectedSsids = new ArrayList<>();
        expectedSsids.add(WifiSsid.fromString("\"TEST_SSID\""));
        mWifiManager.setSsidsAllowlist(new ArraySet<>(expectedSsids));
        verify(mWifiService).setSsidsAllowlist(any(), eq(expectedSsids));

        // test empty set
        mWifiManager.setSsidsAllowlist(Collections.EMPTY_SET);
        verify(mWifiService).setSsidsAllowlist(any(),
                eq(Collections.EMPTY_LIST));
    }

    /**
     * Verify call to startLocalOnlyHotspot goes to WifiServiceImpl.
     */
    @Test
    public void testStartLocalOnlyHotspot() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);

        verify(mWifiService).startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class),
                anyString(), nullable(String.class), eq(null), any());
    }

    /**
     * Verify a SecurityException is thrown for callers without proper permissions for
     * startLocalOnlyHotspot.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityException() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        doThrow(new SecurityException()).when(mWifiService).startLocalOnlyHotspot(
                any(ILocalOnlyHotspotCallback.class), anyString(), nullable(String.class),
                eq(null), any());
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
    }

    /**
     * Verify an IllegalStateException is thrown for callers that already have a pending request for
     * startLocalOnlyHotspot.
     */
    @Test(expected = IllegalStateException.class)
    public void testStartLocalOnlyHotspotThrowsIllegalStateException() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        doThrow(new IllegalStateException()).when(mWifiService).startLocalOnlyHotspot(
                any(ILocalOnlyHotspotCallback.class), anyString(), nullable(String.class),
                eq(null), any());
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
    }

    /**
     * Verify that the handler provided by the caller is used for the callbacks.
     */
    @Test
    public void testCorrectLooperIsUsedForHandler() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(ERROR_INCOMPATIBLE_MODE);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mLooper.dispatchAll();
        assertEquals(ERROR_INCOMPATIBLE_MODE, callback.mFailureReason);
        verify(mContext, never()).getMainLooper();
        verify(mContext, never()).getMainExecutor();
    }

    /**
     * Verify that the main looper's thread is used if a handler is not provided by the reqiestomg
     * application.
     */
    @Test
    public void testMainLooperIsUsedWhenHandlerNotProvided() throws Exception {
        // record thread from looper.getThread and check ids.
        TestLooper altLooper = new TestLooper();
        when(mContext.getMainExecutor()).thenReturn(altLooper.getNewExecutor());
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(ERROR_INCOMPATIBLE_MODE);
        mWifiManager.startLocalOnlyHotspot(callback, null);
        altLooper.dispatchAll();
        assertEquals(ERROR_INCOMPATIBLE_MODE, callback.mFailureReason);
        assertEquals(altLooper.getLooper().getThread().getId(), callback.mCallingThreadId);
        verify(mContext).getMainExecutor();
    }

    /**
     * Verify the LOHS onStarted callback is triggered when WifiManager receives a HOTSPOT_STARTED
     * message from WifiServiceImpl.
     */
    @Test
    public void testOnStartedIsCalledWithReservation() throws Exception {
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        TestLooper callbackLooper = new TestLooper();
        Handler callbackHandler = new Handler(callbackLooper.getLooper());
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        when(mWifiService.startLocalOnlyHotspot(internalCallback.capture(), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, callbackHandler);
        callbackLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(callback.mOnStartedCalled);
        assertEquals(null, callback.mRes);
        // now trigger the callback
        internalCallback.getValue().onHotspotStarted(softApConfig);
        mLooper.dispatchAll();
        callbackLooper.dispatchAll();
        assertTrue(callback.mOnStartedCalled);
        assertEquals(softApConfig, callback.mRes.getSoftApConfiguration());
        WifiConfiguration wifiConfig = callback.mRes.getWifiConfiguration();
        assertTrue(compareWifiAndSoftApConfiguration(softApConfig, wifiConfig));
    }

    /**
     * Verify the LOHS onStarted callback is triggered when WifiManager receives a HOTSPOT_STARTED
     * message from WifiServiceImpl when softap enabled with SAE security type.
     */
    @Test
    public void testOnStartedIsCalledWithReservationAndSaeSoftApConfig() throws Exception {
        SoftApConfiguration softApConfig = new SoftApConfiguration.Builder()
                .setSsid("TestSSID")
                .setPassphrase("TestPassphrase", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .build();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        TestLooper callbackLooper = new TestLooper();
        Handler callbackHandler = new Handler(callbackLooper.getLooper());
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        when(mWifiService.startLocalOnlyHotspot(internalCallback.capture(), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, callbackHandler);
        callbackLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(callback.mOnStartedCalled);
        assertEquals(null, callback.mRes);
        // now trigger the callback
        internalCallback.getValue().onHotspotStarted(softApConfig);
        mLooper.dispatchAll();
        callbackLooper.dispatchAll();
        assertTrue(callback.mOnStartedCalled);
        assertEquals(softApConfig, callback.mRes.getSoftApConfiguration());
        assertEquals(null, callback.mRes.getWifiConfiguration());
    }

    /**
     * Verify onFailed is called if WifiServiceImpl sends a HOTSPOT_STARTED message with a null
     * config.
     */
    @Test
    public void testOnStartedIsCalledWithNullConfig() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        TestLooper callbackLooper = new TestLooper();
        Handler callbackHandler = new Handler(callbackLooper.getLooper());
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        when(mWifiService.startLocalOnlyHotspot(internalCallback.capture(), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, callbackHandler);
        callbackLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(callback.mOnStartedCalled);
        assertEquals(null, callback.mRes);
        // now trigger the callback
        internalCallback.getValue().onHotspotStarted(null);
        mLooper.dispatchAll();
        callbackLooper.dispatchAll();
        assertFalse(callback.mOnStartedCalled);
        assertEquals(ERROR_GENERIC, callback.mFailureReason);
    }

    /**
     * Verify onStopped is called if WifiServiceImpl sends a HOTSPOT_STOPPED message.
     */
    @Test
    public void testOnStoppedIsCalled() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        TestLooper callbackLooper = new TestLooper();
        Handler callbackHandler = new Handler(callbackLooper.getLooper());
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        when(mWifiService.startLocalOnlyHotspot(internalCallback.capture(), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, callbackHandler);
        callbackLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(callback.mOnStoppedCalled);
        // now trigger the callback
        internalCallback.getValue().onHotspotStopped();
        mLooper.dispatchAll();
        callbackLooper.dispatchAll();
        assertTrue(callback.mOnStoppedCalled);
    }

    /**
     * Verify onFailed is called if WifiServiceImpl sends a HOTSPOT_FAILED message.
     */
    @Test
    public void testOnFailedIsCalled() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        TestLooper callbackLooper = new TestLooper();
        Handler callbackHandler = new Handler(callbackLooper.getLooper());
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        when(mWifiService.startLocalOnlyHotspot(internalCallback.capture(), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, callbackHandler);
        callbackLooper.dispatchAll();
        mLooper.dispatchAll();
        assertEquals(ERROR_NOT_SET, callback.mFailureReason);
        // now trigger the callback
        internalCallback.getValue().onHotspotFailed(ERROR_NO_CHANNEL);
        mLooper.dispatchAll();
        callbackLooper.dispatchAll();
        assertEquals(ERROR_NO_CHANNEL, callback.mFailureReason);
    }

    /**
     * Verify callback triggered from startLocalOnlyHotspot with an incompatible mode failure.
     */
    @Test
    public void testLocalOnlyHotspotCallbackFullOnIncompatibleMode() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(ERROR_INCOMPATIBLE_MODE);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mLooper.dispatchAll();
        assertEquals(ERROR_INCOMPATIBLE_MODE, callback.mFailureReason);
        assertFalse(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(null, callback.mRes);
    }

    /**
     * Verify callback triggered from startLocalOnlyHotspot with a tethering disallowed failure.
     */
    @Test
    public void testLocalOnlyHotspotCallbackFullOnTetheringDisallowed() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(ERROR_TETHERING_DISALLOWED);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mLooper.dispatchAll();
        assertEquals(ERROR_TETHERING_DISALLOWED, callback.mFailureReason);
        assertFalse(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(null, callback.mRes);
    }

    /**
     * Verify a SecurityException resulting from an application without necessary permissions will
     * bubble up through the call to start LocalOnlyHotspot and will not trigger other callbacks.
     */
    @Test(expected = SecurityException.class)
    public void testLocalOnlyHotspotCallbackFullOnSecurityException() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        doThrow(new SecurityException()).when(mWifiService).startLocalOnlyHotspot(
                any(ILocalOnlyHotspotCallback.class), anyString(), nullable(String.class),
                eq(null), any());
        try {
            mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        } catch (SecurityException e) {
            assertEquals(ERROR_NOT_SET, callback.mFailureReason);
            assertFalse(callback.mOnStartedCalled);
            assertFalse(callback.mOnStoppedCalled);
            assertEquals(null, callback.mRes);
            throw e;
        }

    }

    /**
     * Verify the handler passed to startLocalOnlyHotspot is correctly used for callbacks when
     * SoftApMode fails due to a underlying error.
     */
    @Test
    public void testLocalOnlyHotspotCallbackFullOnNoChannelError() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mLooper.dispatchAll();
        //assertEquals(ERROR_NO_CHANNEL, callback.mFailureReason);
        assertFalse(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(null, callback.mRes);
    }

    /**
     * Verify that the call to cancel a LOHS request does call stopLOHS.
     */
    @Test
    public void testCancelLocalOnlyHotspotRequestCallsStopOnWifiService() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mWifiManager.cancelLocalOnlyHotspotRequest();
        verify(mWifiService).stopLocalOnlyHotspot();
    }

    /**
     * Verify that we do not crash if cancelLocalOnlyHotspotRequest is called without an existing
     * callback stored.
     */
    @Test
    public void testCancelLocalOnlyHotspotReturnsWithoutExistingRequest() {
        mWifiManager.cancelLocalOnlyHotspotRequest();
    }

    /**
     * Verify that the callback is not triggered if the LOHS request was already cancelled.
     */
    @Test
    public void testCallbackAfterLocalOnlyHotspotWasCancelled() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(REQUEST_REGISTERED);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mWifiManager.cancelLocalOnlyHotspotRequest();
        verify(mWifiService).stopLocalOnlyHotspot();
        mLooper.dispatchAll();
        assertEquals(ERROR_NOT_SET, callback.mFailureReason);
        assertFalse(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(null, callback.mRes);
    }

    /**
     * Verify that calling cancel LOHS request does not crash if an error callback was already
     * handled.
     */
    @Test
    public void testCancelAfterLocalOnlyHotspotCallbackTriggered() throws Exception {
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        when(mWifiService.startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class), anyString(),
                nullable(String.class), eq(null), any())).thenReturn(ERROR_INCOMPATIBLE_MODE);
        mWifiManager.startLocalOnlyHotspot(callback, mHandler);
        mLooper.dispatchAll();
        assertEquals(ERROR_INCOMPATIBLE_MODE, callback.mFailureReason);
        assertFalse(callback.mOnStartedCalled);
        assertFalse(callback.mOnStoppedCalled);
        assertEquals(null, callback.mRes);
        mWifiManager.cancelLocalOnlyHotspotRequest();
        verify(mWifiService, never()).stopLocalOnlyHotspot();
    }

    @Test
    public void testStartLocalOnlyHotspotForwardsCustomConfig() throws Exception {
        SoftApConfiguration customConfig = new SoftApConfiguration.Builder()
                .setSsid("customSsid")
                .build();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback();
        mWifiManager.startLocalOnlyHotspot(customConfig, mExecutor, callback);
        verify(mWifiService).startLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class),
                anyString(), nullable(String.class), eq(customConfig), any());
    }

    /**
     * Verify the watchLocalOnlyHotspot call goes to WifiServiceImpl.
     */
    @Test
    public void testWatchLocalOnlyHotspot() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();

        mWifiManager.watchLocalOnlyHotspot(observer, mHandler);
        verify(mWifiService).startWatchLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class));
    }

    /**
     * Verify a SecurityException is thrown for callers without proper permissions for
     * startWatchLocalOnlyHotspot.
     */
    @Test(expected = SecurityException.class)
    public void testStartWatchLocalOnlyHotspotThrowsSecurityException() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        doThrow(new SecurityException()).when(mWifiService)
                .startWatchLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class));
        mWifiManager.watchLocalOnlyHotspot(observer, mHandler);
    }

    /**
     * Verify an IllegalStateException is thrown for callers that already have a pending request for
     * watchLocalOnlyHotspot.
     */
    @Test(expected = IllegalStateException.class)
    public void testStartWatchLocalOnlyHotspotThrowsIllegalStateException() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        doThrow(new IllegalStateException()).when(mWifiService)
                .startWatchLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class));
        mWifiManager.watchLocalOnlyHotspot(observer, mHandler);
    }

    /**
     * Verify an IllegalArgumentException is thrown if a callback or executor is not provided.
     */
    @Test
    public void testAddWifiVerboseLoggingStatusChangedListenerIllegalArguments() throws Exception {
        try {
            mWifiManager.addWifiVerboseLoggingStatusChangedListener(
                    new HandlerExecutor(mHandler), null);
            fail("expected IllegalArgumentException - null callback");
        } catch (IllegalArgumentException expected) {
        }
        try {
            WifiManager.WifiVerboseLoggingStatusChangedListener listener =
                    new WifiManager.WifiVerboseLoggingStatusChangedListener() {
                        @Override
                        public void onWifiVerboseLoggingStatusChanged(boolean enabled) {

                        }
                    };
            mWifiManager.addWifiVerboseLoggingStatusChangedListener(null, listener);
            fail("expected IllegalArgumentException - null executor");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify the call to addWifiVerboseLoggingStatusChangedListener and
     * removeWifiVerboseLoggingStatusChangedListener goes to WifiServiceImpl.
     */
    @Test
    public void testWifiVerboseLoggingStatusChangedListenerGoesToWifiServiceImpl()
            throws Exception {
        WifiManager.WifiVerboseLoggingStatusChangedListener listener =
                new WifiManager.WifiVerboseLoggingStatusChangedListener() {
                    @Override
                    public void onWifiVerboseLoggingStatusChanged(boolean enabled) {

                    }
                };
        mWifiManager.addWifiVerboseLoggingStatusChangedListener(new HandlerExecutor(mHandler),
                listener);
        verify(mWifiService).addWifiVerboseLoggingStatusChangedListener(
                any(IWifiVerboseLoggingStatusChangedListener.Stub.class));
        mWifiManager.removeWifiVerboseLoggingStatusChangedListener(listener);
        verify(mWifiService).removeWifiVerboseLoggingStatusChangedListener(
                any(IWifiVerboseLoggingStatusChangedListener.Stub.class));
    }

    /**
     * Verify an IllegalArgumentException is thrown if a callback is not provided.
     */
    @Test
    public void testRemoveWifiVerboseLoggingStatusChangedListenerIllegalArguments()
            throws Exception {
        try {
            mWifiManager.removeWifiVerboseLoggingStatusChangedListener(null);
            fail("expected IllegalArgumentException - null callback");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test
    public void registerSoftApCallbackThrowsIllegalArgumentExceptionOnNullArgumentForCallback() {
        try {
            mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor is null.
     */
    @Test
    public void registerSoftApCallbackThrowsIllegalArgumentExceptionOnNullArgumentForExecutor() {
        try {
            mWifiManager.registerSoftApCallback(null, mSoftApCallback);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test
    public void unregisterSoftApCallbackThrowsIllegalArgumentExceptionOnNullArgumentForCallback() {
        try {
            mWifiManager.unregisterSoftApCallback(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify the call to registerSoftApCallback goes to WifiServiceImpl.
     */
    @Test
    public void registerSoftApCallbackCallGoesToWifiServiceImpl() throws Exception {
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(any(ISoftApCallback.Stub.class));
    }

    /**
     * Verify the call to unregisterSoftApCallback goes to WifiServiceImpl.
     */
    @Test
    public void unregisterSoftApCallbackCallGoesToWifiServiceImpl() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
        verify(mWifiService).unregisterSoftApCallback(callbackCaptor.getValue());
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnStateChanged() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        SoftApState state = new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_INTERFACE_NAME);
        callbackCaptor.getValue().onStateChanged(state);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onStateChanged(WIFI_AP_STATE_ENABLED, 0);
        ArgumentCaptor<SoftApState> softApStateCaptor = ArgumentCaptor.forClass(SoftApState.class);
        verify(mSoftApCallback).onStateChanged(softApStateCaptor.capture());
        assertEquals(state, softApStateCaptor.getValue());
    }

    /*
     * Verify client-provided callback is being called through callback proxy when registration.
     */
    @Test
    public void softApCallbackProxyCallsOnRegistrationAndApStartedWithClientsConnected()
            throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());
        // Prepare test info and clients
        initTestInfoAndAddToTestMap(1);
        List<WifiClient> clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        // Trigger callback with registration in AP started and clients connected.
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, true);

        mLooper.dispatchAll();
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onInfoChanged(mTestApInfo1);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(mTestApInfo1)));
    }


    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnConnectedClientsChangedEvenIfNoInfoChanged()
            throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());
        List<WifiClient> clientList;
        // Verify the register callback in disable state.
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, true);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onConnectedClientsChanged(new ArrayList<WifiClient>());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        verify(mSoftApCallback).onInfoChanged(new SoftApInfo());
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test first client connected
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test second client connected
        mTestWifiClientsMap.clear();
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 2, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test second client disconnect
        mTestWifiClientsMap.clear();
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnConnectedClientsChanged() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());
        List<WifiClient> clientList;
        // Verify the register callback in disable state.
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, true);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onConnectedClientsChanged(new ArrayList<WifiClient>());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        verify(mSoftApCallback).onInfoChanged(new SoftApInfo());
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Single AP mode Test
        // Test info update
        initTestInfoAndAddToTestMap(1);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(mTestApInfo1);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(mTestApInfo1)));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test first client connected
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test second client connected
        mTestWifiClientsMap.clear();
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 2, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test second client disconnect
        mTestWifiClientsMap.clear();
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test bridged mode case
        mTestSoftApInfoMap.clear();
        initTestInfoAndAddToTestMap(2);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                  infos.contains(mTestApInfo1) && infos.contains(mTestApInfo2)
                  ));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test client connect to second instance
        List<WifiClient> clientListOnSecond =
                initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[1], 1, 2); // client3 to wlan2
        List<WifiClient> totalList = new ArrayList<>();
        totalList.addAll(clientList);
        totalList.addAll(clientListOnSecond);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo2, clientListOnSecond);
        verify(mSoftApCallback).onConnectedClientsChanged(totalList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test shutdown on second instance
        mTestSoftApInfoMap.clear();
        mTestWifiClientsMap.clear();
        initTestInfoAndAddToTestMap(1);
        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(mTestApInfo1)));
        // second instance have client connected before, thus it should send empty list
        verify(mSoftApCallback).onConnectedClientsChanged(
                mTestApInfo2, new ArrayList<WifiClient>());
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test bridged mode disable when client connected
        mTestSoftApInfoMap.clear();
        mTestWifiClientsMap.clear();
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        verify(mSoftApCallback).onConnectedClientsChanged(new ArrayList<WifiClient>());
        verify(mSoftApCallback).onConnectedClientsChanged(
                mTestApInfo1, new ArrayList<WifiClient>());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);
    }


    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnSoftApInfoChanged() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());
        // Verify the register callback in disable state.
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, true);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onConnectedClientsChanged(new ArrayList<WifiClient>());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        verify(mSoftApCallback).onInfoChanged(new SoftApInfo());
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Single AP mode Test
        // Test info update
        initTestInfoAndAddToTestMap(1);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(mTestApInfo1);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(mTestApInfo1)));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test info changed
        SoftApInfo changedInfo = new SoftApInfo(mTestSoftApInfoMap.get(TEST_AP_INSTANCES[0]));
        changedInfo.setFrequency(2422);
        mTestSoftApInfoMap.put(TEST_AP_INSTANCES[0], changedInfo);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(changedInfo);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(changedInfo)));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());

        // Test Stop, all of infos is empty
        mTestSoftApInfoMap.clear();
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(new SoftApInfo());
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnSoftApInfoChangedWhenClientConnected() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());
        List<WifiClient> clientList;
        // Verify the register callback in disable state.
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, true);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onConnectedClientsChanged(new ArrayList<WifiClient>());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        verify(mSoftApCallback).onInfoChanged(new SoftApInfo());
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Single AP mode Test
        // Test info update
        initTestInfoAndAddToTestMap(1);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(mTestApInfo1);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(mTestApInfo1)));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        // checked NO any infoChanged, includes InfoChanged(SoftApInfo)
        // and InfoChanged(List<SoftApInfo>)
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback, never()).onInfoChanged(any(List.class));
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test info changed when client connected
        SoftApInfo changedInfo = new SoftApInfo(mTestSoftApInfoMap.get(TEST_AP_INSTANCES[0]));
        changedInfo.setFrequency(2422);
        mTestSoftApInfoMap.put(TEST_AP_INSTANCES[0], changedInfo);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(changedInfo);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(changedInfo)));
        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(changedInfo, clientList);
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test Stop, all of infos is empty
        mTestSoftApInfoMap.clear();
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onInfoChanged(new SoftApInfo());
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        verify(mSoftApCallback).onConnectedClientsChanged(any());
        verify(mSoftApCallback).onConnectedClientsChanged(any(), any());
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnSoftApInfoChangedInBridgedMode() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        // Test bridged mode case
        initTestInfoAndAddToTestMap(2);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                  infos.contains(mTestApInfo1) && infos.contains(mTestApInfo2)
                  ));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test bridged mode case but an info changed
        SoftApInfo changedInfoBridgedMode = new SoftApInfo(mTestSoftApInfoMap.get(
                TEST_AP_INSTANCES[0]));
        changedInfoBridgedMode.setFrequency(2422);
        mTestSoftApInfoMap.put(TEST_AP_INSTANCES[0], changedInfoBridgedMode);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                  infos.contains(changedInfoBridgedMode) && infos.contains(mTestApInfo2)
                  ));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test bridged mode case but an instance shutdown
        mTestSoftApInfoMap.clear();
        initTestInfoAndAddToTestMap(1);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                  infos.contains(mTestApInfo1)
                  ));
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);

        // Test bridged mode disable case
        mTestSoftApInfoMap.clear();
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), true, false);
        mLooper.dispatchAll();
        verify(mSoftApCallback, never()).onInfoChanged(any(SoftApInfo.class));
        verify(mSoftApCallback).onInfoChanged(new ArrayList<SoftApInfo>());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any());
        verify(mSoftApCallback, never()).onConnectedClientsChanged(any(), any());
        // After verify, reset mSoftApCallback for nex test
        reset(mSoftApCallback);
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnCapabilityChanged() throws Exception {
        SoftApCapability testSoftApCapability = new SoftApCapability(0);
        testSoftApCapability.setMaxSupportedClients(10);
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        callbackCaptor.getValue().onCapabilityChanged(testSoftApCapability);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onCapabilityChanged(testSoftApCapability);
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void softApCallbackProxyCallsOnBlockedClientConnecting() throws Exception {
        WifiClient testWifiClient = new WifiClient(MacAddress.fromString("22:33:44:55:66:77"),
                TEST_AP_INSTANCES[0]);
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        callbackCaptor.getValue().onBlockedClientConnecting(testWifiClient,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
        mLooper.dispatchAll();
        verify(mSoftApCallback).onBlockedClientConnecting(testWifiClient,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
    }

    /*
     * Verify client-provided callback is being called through callback proxy on multiple events
     */
    @Test
    public void softApCallbackProxyCallsOnMultipleUpdates() throws Exception {
        SoftApCapability testSoftApCapability = new SoftApCapability(0);
        testSoftApCapability.setMaxSupportedClients(10);
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        SoftApState state0 = new SoftApState(WIFI_AP_STATE_ENABLING, 0,
                TEST_TETHERING_REQUEST, TEST_INTERFACE_NAME);
        callbackCaptor.getValue().onStateChanged(state0);
        SoftApState state1 = new SoftApState(WIFI_AP_STATE_FAILED, SAP_START_FAILURE_GENERAL,
                TEST_TETHERING_REQUEST, TEST_INTERFACE_NAME);
        callbackCaptor.getValue().onStateChanged(state1);
        callbackCaptor.getValue().onCapabilityChanged(testSoftApCapability);


        mLooper.dispatchAll();
        verify(mSoftApCallback).onStateChanged(WIFI_AP_STATE_ENABLING, 0);
        verify(mSoftApCallback).onStateChanged(WIFI_AP_STATE_FAILED, SAP_START_FAILURE_GENERAL);
        verify(mSoftApCallback).onCapabilityChanged(testSoftApCapability);
        ArgumentCaptor<SoftApState> softApStateCaptor =
                ArgumentCaptor.forClass(SoftApState.class);
        verify(mSoftApCallback, times(2)).onStateChanged(softApStateCaptor.capture());
        assertEquals(state0, softApStateCaptor.getAllValues().get(0));
        assertEquals(state1, softApStateCaptor.getAllValues().get(1));
    }

    /*
     * Verify client-provided callback is being called on the correct thread
     */
    @Test
    public void softApCallbackIsCalledOnCorrectThread() throws Exception {
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        TestLooper altLooper = new TestLooper();
        Handler altHandler = new Handler(altLooper.getLooper());
        mWifiManager.registerSoftApCallback(new HandlerExecutor(altHandler), mSoftApCallback);
        verify(mWifiService).registerSoftApCallback(callbackCaptor.capture());

        SoftApState state = new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_INTERFACE_NAME);
        callbackCaptor.getValue().onStateChanged(state);
        altLooper.dispatchAll();
        verify(mSoftApCallback).onStateChanged(WIFI_AP_STATE_ENABLED, 0);
        ArgumentCaptor<SoftApState> softApStateCaptor =
                ArgumentCaptor.forClass(SoftApState.class);
        verify(mSoftApCallback).onStateChanged(softApStateCaptor.capture());
        SoftApState softApState = softApStateCaptor.getValue();
        assertEquals(WIFI_AP_STATE_ENABLED, softApState.getState());
        assertEquals(0, softApState.getFailureReason());
        assertEquals(TEST_INTERFACE_NAME, softApState.getIface());
        assertEquals(TEST_TETHERING_REQUEST, softApState.getTetheringRequest());
    }

    /**
     * Verify that the handler provided by the caller is used for registering soft AP callback.
     */
    @Test
    public void testCorrectLooperIsUsedForSoftApCallbackHandler() throws Exception {
        mWifiManager.registerSoftApCallback(new HandlerExecutor(mHandler), mSoftApCallback);
        mLooper.dispatchAll();
        verify(mWifiService).registerSoftApCallback(any(ISoftApCallback.Stub.class));
        verify(mContext, never()).getMainLooper();
        verify(mContext, never()).getMainExecutor();
    }

    /**
     * Verify that the handler provided by the caller is used for the observer.
     */
    @Test
    public void testCorrectLooperIsUsedForObserverHandler() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        mWifiManager.watchLocalOnlyHotspot(observer, mHandler);
        mLooper.dispatchAll();
        assertTrue(observer.mOnRegistered);
        verify(mContext, never()).getMainLooper();
        verify(mContext, never()).getMainExecutor();
    }

    /**
     * Verify that the main looper's thread is used if a handler is not provided by the requesting
     * application.
     */
    @Test
    public void testMainLooperIsUsedWhenHandlerNotProvidedForObserver() throws Exception {
        // record thread from looper.getThread and check ids.
        TestLooper altLooper = new TestLooper();
        when(mContext.getMainExecutor()).thenReturn(altLooper.getNewExecutor());
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        mWifiManager.watchLocalOnlyHotspot(observer, null);
        altLooper.dispatchAll();
        assertTrue(observer.mOnRegistered);
        assertEquals(altLooper.getLooper().getThread().getId(), observer.mCallingThreadId);
        verify(mContext).getMainExecutor();
    }

    /**
     * Verify the LOHS onRegistered observer callback is triggered when WifiManager receives a
     * HOTSPOT_OBSERVER_REGISTERED message from WifiServiceImpl.
     */
    @Test
    public void testOnRegisteredIsCalledWithSubscription() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        TestLooper observerLooper = new TestLooper();
        Handler observerHandler = new Handler(observerLooper.getLooper());
        assertFalse(observer.mOnRegistered);
        assertEquals(null, observer.mSub);
        mWifiManager.watchLocalOnlyHotspot(observer, observerHandler);
        verify(mWifiService).startWatchLocalOnlyHotspot(any(ILocalOnlyHotspotCallback.class));
        // now trigger the callback
        observerLooper.dispatchAll();
        mLooper.dispatchAll();
        assertTrue(observer.mOnRegistered);
        assertNotNull(observer.mSub);
    }

    /**
     * Verify the LOHS onStarted observer callback is triggered when WifiManager receives a
     * HOTSPOT_STARTED message from WifiServiceImpl.
     */
    @Test
    public void testObserverOnStartedIsCalledWithWifiConfig() throws Exception {
        SoftApConfiguration softApConfig = generatorTestSoftApConfig();
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        TestLooper observerLooper = new TestLooper();
        Handler observerHandler = new Handler(observerLooper.getLooper());
        mWifiManager.watchLocalOnlyHotspot(observer, observerHandler);
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        verify(mWifiService).startWatchLocalOnlyHotspot(internalCallback.capture());
        observerLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(observer.mOnStartedCalled);
        // now trigger the callback
        internalCallback.getValue().onHotspotStarted(softApConfig);
        mLooper.dispatchAll();
        observerLooper.dispatchAll();
        assertTrue(observer.mOnStartedCalled);
        assertEquals(softApConfig, observer.mConfig);
    }

    /**
     * Verify the LOHS onStarted observer callback is triggered not when WifiManager receives a
     * HOTSPOT_STARTED message from WifiServiceImpl with a null config.
     */
    @Test
    public void testObserverOnStartedNotCalledWithNullConfig() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        TestLooper observerLooper = new TestLooper();
        Handler observerHandler = new Handler(observerLooper.getLooper());
        mWifiManager.watchLocalOnlyHotspot(observer, observerHandler);
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        verify(mWifiService).startWatchLocalOnlyHotspot(internalCallback.capture());
        observerLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(observer.mOnStartedCalled);
        // now trigger the callback
        internalCallback.getValue().onHotspotStarted(null);
        mLooper.dispatchAll();
        observerLooper.dispatchAll();
        assertFalse(observer.mOnStartedCalled);
        assertEquals(null, observer.mConfig);
    }


    /**
     * Verify the LOHS onStopped observer callback is triggered when WifiManager receives a
     * HOTSPOT_STOPPED message from WifiServiceImpl.
     */
    @Test
    public void testObserverOnStoppedIsCalled() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        TestLooper observerLooper = new TestLooper();
        Handler observerHandler = new Handler(observerLooper.getLooper());
        mWifiManager.watchLocalOnlyHotspot(observer, observerHandler);
        ArgumentCaptor<ILocalOnlyHotspotCallback> internalCallback =
                ArgumentCaptor.forClass(ILocalOnlyHotspotCallback.class);
        verify(mWifiService).startWatchLocalOnlyHotspot(internalCallback.capture());
        observerLooper.dispatchAll();
        mLooper.dispatchAll();
        assertFalse(observer.mOnStoppedCalled);
        // now trigger the callback
        internalCallback.getValue().onHotspotStopped();
        mLooper.dispatchAll();
        observerLooper.dispatchAll();
        assertTrue(observer.mOnStoppedCalled);
    }

    /**
     * Verify WifiServiceImpl is not called if there is not a registered LOHS observer callback.
     */
    @Test
    public void testUnregisterWifiServiceImplNotCalledWithoutRegisteredObserver() throws Exception {
        mWifiManager.unregisterLocalOnlyHotspotObserver();
        verifyZeroInteractions(mWifiService);
    }

    /**
     * Verify WifiServiceImpl is called when there is a registered LOHS observer callback.
     */
    @Test
    public void testUnregisterWifiServiceImplCalledWithRegisteredObserver() throws Exception {
        TestLocalOnlyHotspotObserver observer = new TestLocalOnlyHotspotObserver();
        TestLooper observerLooper = new TestLooper();
        Handler observerHandler = new Handler(observerLooper.getLooper());
        mWifiManager.watchLocalOnlyHotspot(observer, observerHandler);
        mWifiManager.unregisterLocalOnlyHotspotObserver();
        verify(mWifiService).stopWatchLocalOnlyHotspot();
    }

    /**
     * Test that calls to get the current WPS config token return null and do not have any
     * interactions with WifiServiceImpl.
     */
    @Test
    public void testGetCurrentNetworkWpsNfcConfigurationTokenReturnsNull() {
        assertNull(mWifiManager.getCurrentNetworkWpsNfcConfigurationToken());
        verifyNoMoreInteractions(mWifiService);
    }


    class WpsCallbackTester extends WpsCallback {
        public boolean mStarted = false;
        public boolean mSucceeded = false;
        public boolean mFailed = false;
        public int mFailureCode = -1;

        @Override
        public void onStarted(String pin) {
            mStarted = true;
        }

        @Override
        public void onSucceeded() {
            mSucceeded = true;
        }

        @Override
        public void onFailed(int reason) {
            mFailed = true;
            mFailureCode = reason;
        }

    }

    /**
     * Verify that a call to start WPS immediately returns a failure.
     */
    @Test
    public void testStartWpsImmediatelyFailsWithCallback() {
        WpsCallbackTester wpsCallback = new WpsCallbackTester();
        mWifiManager.startWps(null, wpsCallback);
        assertTrue(wpsCallback.mFailed);
        assertEquals(ActionListener.FAILURE_INTERNAL_ERROR, wpsCallback.mFailureCode);
        assertFalse(wpsCallback.mStarted);
        assertFalse(wpsCallback.mSucceeded);
        verifyNoMoreInteractions(mWifiService);
    }

    /**
     * Verify that a call to start WPS does not go to WifiServiceImpl if we do not have a callback.
     */
    @Test
    public void testStartWpsDoesNotCallWifiServiceImpl() {
        mWifiManager.startWps(null, null);
        verifyNoMoreInteractions(mWifiService);
    }

    /**
     * Verify that a call to cancel WPS immediately returns a failure.
     */
    @Test
    public void testCancelWpsImmediatelyFailsWithCallback() {
        WpsCallbackTester wpsCallback = new WpsCallbackTester();
        mWifiManager.cancelWps(wpsCallback);
        assertTrue(wpsCallback.mFailed);
        assertEquals(ActionListener.FAILURE_INTERNAL_ERROR, wpsCallback.mFailureCode);
        assertFalse(wpsCallback.mStarted);
        assertFalse(wpsCallback.mSucceeded);
        verifyNoMoreInteractions(mWifiService);
    }

    /**
     * Verify that a call to cancel WPS does not go to WifiServiceImpl if we do not have a callback.
     */
    @Test
    public void testCancelWpsDoesNotCallWifiServiceImpl() {
        mWifiManager.cancelWps(null);
        verifyNoMoreInteractions(mWifiService);
    }

    /**
     * Verify that a successful call properly returns true.
     */
    @Test
    public void testSetWifiApConfigurationSuccessReturnsTrue() throws Exception {
        WifiConfiguration apConfig = new WifiConfiguration();

        when(mWifiService.setWifiApConfiguration(eq(apConfig), eq(TEST_PACKAGE_NAME)))
                .thenReturn(true);
        assertTrue(mWifiManager.setWifiApConfiguration(apConfig));
    }

    /**
     * Verify that a failed call properly returns false.
     */
    @Test
    public void testSetWifiApConfigurationFailureReturnsFalse() throws Exception {
        WifiConfiguration apConfig = new WifiConfiguration();

        when(mWifiService.setWifiApConfiguration(eq(apConfig), eq(TEST_PACKAGE_NAME)))
                .thenReturn(false);
        assertFalse(mWifiManager.setWifiApConfiguration(apConfig));
    }

    /**
     * Verify Exceptions are rethrown when underlying calls to WifiService throw exceptions.
     */
    @Test
    public void testSetWifiApConfigurationRethrowsException() throws Exception {
        doThrow(new SecurityException()).when(mWifiService).setWifiApConfiguration(any(), any());

        try {
            mWifiManager.setWifiApConfiguration(new WifiConfiguration());
            fail("setWifiApConfiguration should rethrow Exceptions from WifiService");
        } catch (SecurityException e) { }
    }

    /**
     * Verify that a successful call properly returns true.
     */
    @Test
    public void testSetSoftApConfigurationSuccessReturnsTrue() throws Exception {
        SoftApConfiguration apConfig = generatorTestSoftApConfig();

        when(mWifiService.setSoftApConfiguration(eq(apConfig), eq(TEST_PACKAGE_NAME)))
                .thenReturn(true);
        assertTrue(mWifiManager.setSoftApConfiguration(apConfig));
    }

    /**
     * Verify that a failed call properly returns false.
     */
    @Test
    public void testSetSoftApConfigurationFailureReturnsFalse() throws Exception {
        SoftApConfiguration apConfig = generatorTestSoftApConfig();

        when(mWifiService.setSoftApConfiguration(eq(apConfig), eq(TEST_PACKAGE_NAME)))
                .thenReturn(false);
        assertFalse(mWifiManager.setSoftApConfiguration(apConfig));
    }

    /**
     * Verify Exceptions are rethrown when underlying calls to WifiService throw exceptions.
     */
    @Test
    public void testSetSoftApConfigurationRethrowsException() throws Exception {
        doThrow(new SecurityException()).when(mWifiService).setSoftApConfiguration(any(), any());

        try {
            mWifiManager.setSoftApConfiguration(generatorTestSoftApConfig());
            fail("setWifiApConfiguration should rethrow Exceptions from WifiService");
        } catch (SecurityException e) { }
    }

    /**
     * Check the call to startScan calls WifiService.
     */
    @Test
    public void testStartScan() throws Exception {
        when(mWifiService.startScan(eq(TEST_PACKAGE_NAME), nullable(String.class))).thenReturn(
                true);
        assertTrue(mWifiManager.startScan());

        when(mWifiService.startScan(eq(TEST_PACKAGE_NAME), nullable(String.class))).thenReturn(
                false);
        assertFalse(mWifiManager.startScan());
    }

    /**
     * Verify main looper is used when handler is not provided.
     */
    @Test
    public void registerTrafficStateCallbackUsesMainLooperOnNullArgumentForHandler()
            throws Exception {
        ArgumentCaptor<ITrafficStateCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ITrafficStateCallback.Stub.class);
        mWifiManager.registerTrafficStateCallback(
                new HandlerExecutor(new Handler(mLooper.getLooper())), mTrafficStateCallback);
        verify(mWifiService).registerTrafficStateCallback(callbackCaptor.capture());

        assertEquals(0, mLooper.dispatchAll());
        callbackCaptor.getValue().onStateChanged(TrafficStateCallback.DATA_ACTIVITY_INOUT);
        assertEquals(1, mLooper.dispatchAll());
        verify(mTrafficStateCallback).onStateChanged(TrafficStateCallback.DATA_ACTIVITY_INOUT);
    }

    /**
     * Verify the call to unregisterTrafficStateCallback goes to WifiServiceImpl.
     */
    @Test
    public void unregisterTrafficStateCallbackCallGoesToWifiServiceImpl() throws Exception {
        ArgumentCaptor<ITrafficStateCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ITrafficStateCallback.Stub.class);
        mWifiManager.registerTrafficStateCallback(new HandlerExecutor(mHandler),
                mTrafficStateCallback);
        verify(mWifiService).registerTrafficStateCallback(callbackCaptor.capture());

        mWifiManager.unregisterTrafficStateCallback(mTrafficStateCallback);
        verify(mWifiService).unregisterTrafficStateCallback(callbackCaptor.getValue());
    }

    /*
     * Verify client-provided callback is being called through callback proxy on multiple events
     */
    @Test
    public void trafficStateCallbackProxyCallsOnMultipleUpdates() throws Exception {
        ArgumentCaptor<ITrafficStateCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ITrafficStateCallback.Stub.class);
        mWifiManager.registerTrafficStateCallback(new HandlerExecutor(mHandler),
                mTrafficStateCallback);
        verify(mWifiService).registerTrafficStateCallback(callbackCaptor.capture());

        InOrder inOrder = inOrder(mTrafficStateCallback);

        callbackCaptor.getValue().onStateChanged(TrafficStateCallback.DATA_ACTIVITY_IN);
        callbackCaptor.getValue().onStateChanged(TrafficStateCallback.DATA_ACTIVITY_INOUT);
        callbackCaptor.getValue().onStateChanged(TrafficStateCallback.DATA_ACTIVITY_OUT);

        mLooper.dispatchAll();
        inOrder.verify(mTrafficStateCallback).onStateChanged(
                TrafficStateCallback.DATA_ACTIVITY_IN);
        inOrder.verify(mTrafficStateCallback).onStateChanged(
                TrafficStateCallback.DATA_ACTIVITY_INOUT);
        inOrder.verify(mTrafficStateCallback).onStateChanged(
                TrafficStateCallback.DATA_ACTIVITY_OUT);
    }

    /**
     * Verify client-provided callback is being called on the correct thread
     */
    @Test
    public void trafficStateCallbackIsCalledOnCorrectThread() throws Exception {
        ArgumentCaptor<ITrafficStateCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ITrafficStateCallback.Stub.class);
        TestLooper altLooper = new TestLooper();
        Handler altHandler = new Handler(altLooper.getLooper());
        mWifiManager.registerTrafficStateCallback(new HandlerExecutor(altHandler),
                mTrafficStateCallback);
        verify(mContext, never()).getMainLooper();
        verify(mContext, never()).getMainExecutor();
        verify(mWifiService).registerTrafficStateCallback(callbackCaptor.capture());

        assertEquals(0, altLooper.dispatchAll());
        callbackCaptor.getValue().onStateChanged(TrafficStateCallback.DATA_ACTIVITY_INOUT);
        assertEquals(1, altLooper.dispatchAll());
        verify(mTrafficStateCallback).onStateChanged(TrafficStateCallback.DATA_ACTIVITY_INOUT);
    }

    /**
     * Verify the call to registerNetworkRequestMatchCallback goes to WifiServiceImpl.
     */
    @Test
    public void registerNetworkRequestMatchCallbackCallGoesToWifiServiceImpl()
            throws Exception {
        ArgumentCaptor<INetworkRequestMatchCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(INetworkRequestMatchCallback.Stub.class);
        mWifiManager.registerNetworkRequestMatchCallback(
                new HandlerExecutor(new Handler(mLooper.getLooper())),
                mNetworkRequestMatchCallback);
        verify(mWifiService).registerNetworkRequestMatchCallback(callbackCaptor.capture());

        INetworkRequestUserSelectionCallback iUserSelectionCallback =
                mock(INetworkRequestUserSelectionCallback.class);

        assertEquals(0, mLooper.dispatchAll());

        callbackCaptor.getValue().onAbort();
        assertEquals(1, mLooper.dispatchAll());
        verify(mNetworkRequestMatchCallback).onAbort();

        callbackCaptor.getValue().onMatch(new ArrayList<ScanResult>());
        assertEquals(1, mLooper.dispatchAll());
        verify(mNetworkRequestMatchCallback).onMatch(anyList());

        callbackCaptor.getValue().onUserSelectionConnectSuccess(new WifiConfiguration());
        assertEquals(1, mLooper.dispatchAll());
        verify(mNetworkRequestMatchCallback).onUserSelectionConnectSuccess(
                any(WifiConfiguration.class));

        callbackCaptor.getValue().onUserSelectionConnectFailure(new WifiConfiguration());
        assertEquals(1, mLooper.dispatchAll());
        verify(mNetworkRequestMatchCallback).onUserSelectionConnectFailure(
                any(WifiConfiguration.class));
    }

    /**
     * Verify the call to unregisterNetworkRequestMatchCallback goes to WifiServiceImpl.
     */
    @Test
    public void unregisterNetworkRequestMatchCallbackCallGoesToWifiServiceImpl() throws Exception {
        ArgumentCaptor<INetworkRequestMatchCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(INetworkRequestMatchCallback.Stub.class);
        mWifiManager.registerNetworkRequestMatchCallback(new HandlerExecutor(mHandler),
                mNetworkRequestMatchCallback);
        verify(mWifiService).registerNetworkRequestMatchCallback(callbackCaptor.capture());

        mWifiManager.unregisterNetworkRequestMatchCallback(mNetworkRequestMatchCallback);
        verify(mWifiService).unregisterNetworkRequestMatchCallback(callbackCaptor.getValue());
    }

    /**
     * Verify the call to NetworkRequestUserSelectionCallback goes to
     * WifiServiceImpl.
     */
    @Test
    public void networkRequestUserSelectionCallbackCallGoesToWifiServiceImpl()
            throws Exception {
        ArgumentCaptor<INetworkRequestMatchCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(INetworkRequestMatchCallback.Stub.class);
        mWifiManager.registerNetworkRequestMatchCallback(
                new HandlerExecutor(new Handler(mLooper.getLooper())),
                mNetworkRequestMatchCallback);
        verify(mWifiService).registerNetworkRequestMatchCallback(callbackCaptor.capture());

        INetworkRequestUserSelectionCallback iUserSelectionCallback =
                mock(INetworkRequestUserSelectionCallback.class);
        ArgumentCaptor<NetworkRequestUserSelectionCallback> userSelectionCallbackCaptor =
                ArgumentCaptor.forClass(NetworkRequestUserSelectionCallback.class);
        callbackCaptor.getValue().onUserSelectionCallbackRegistration(
                iUserSelectionCallback);
        assertEquals(1, mLooper.dispatchAll());
        verify(mNetworkRequestMatchCallback).onUserSelectionCallbackRegistration(
                userSelectionCallbackCaptor.capture());

        WifiConfiguration selected = new WifiConfiguration();
        userSelectionCallbackCaptor.getValue().select(selected);
        verify(iUserSelectionCallback).select(selected);

        userSelectionCallbackCaptor.getValue().reject();
        verify(iUserSelectionCallback).reject();
    }

    /**
     * Check the call to getAllMatchingWifiConfigs calls getAllMatchingFqdnsForScanResults and
     * getWifiConfigsForPasspointProfiles of WifiService in order.
     */
    @Test
    public void testGetAllMatchingWifiConfigs() throws Exception {
        Map<String, List<ScanResult>> passpointProfiles = new HashMap<>();
        passpointProfiles.put("www.test.com_987a69bca26", new ArrayList<>());
        when(mWifiService.getAllMatchingPasspointProfilesForScanResults(
                any(List.class))).thenReturn(passpointProfiles);
        InOrder inOrder = inOrder(mWifiService);

        mWifiManager.getAllMatchingWifiConfigs(new ArrayList<>());

        inOrder.verify(mWifiService).getAllMatchingPasspointProfilesForScanResults(any(List.class));
        inOrder.verify(mWifiService).getWifiConfigsForPasspointProfiles(any(List.class));
    }

    /**
     * Check the call to getMatchingOsuProviders calls getMatchingOsuProviders of WifiService
     * with the provided a list of ScanResult.
     */
    @Test
    public void testGetMatchingOsuProviders() throws Exception {
        mWifiManager.getMatchingOsuProviders(new ArrayList<>());

        verify(mWifiService).getMatchingOsuProviders(any(List.class));
    }

    /**
     * Verify calls to {@link WifiManager#addNetworkSuggestions(List)},
     * {@link WifiManager#getNetworkSuggestions()} and
     * {@link WifiManager#removeNetworkSuggestions(List)}.
     */
    @Test
    public void addGetRemoveNetworkSuggestions() throws Exception {
        List<WifiNetworkSuggestion> testList = new ArrayList<>();
        when(mWifiService.addNetworkSuggestions(any(List.class), anyString(),
                nullable(String.class))).thenReturn(STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mWifiService.removeNetworkSuggestions(any(List.class), anyString(), anyInt()))
                .thenReturn(STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mWifiService.getNetworkSuggestions(anyString()))
                .thenReturn(testList);

        assertEquals(STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiManager.addNetworkSuggestions(testList));
        verify(mWifiService).addNetworkSuggestions(anyList(), eq(TEST_PACKAGE_NAME),
                nullable(String.class));

        assertEquals(testList, mWifiManager.getNetworkSuggestions());
        verify(mWifiService).getNetworkSuggestions(eq(TEST_PACKAGE_NAME));

        assertEquals(STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiManager.removeNetworkSuggestions(new ArrayList<>()));
        verify(mWifiService).removeNetworkSuggestions(anyList(), eq(TEST_PACKAGE_NAME),
                eq(ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    @Test
    public void testRemoveNetworkSuggestionWithAction() throws Exception {
        when(mWifiService.removeNetworkSuggestions(anyList(), anyString(), anyInt()))
                .thenReturn(STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        assertEquals(STATUS_NETWORK_SUGGESTIONS_SUCCESS, mWifiManager
                .removeNetworkSuggestions(new ArrayList<>(), ACTION_REMOVE_SUGGESTION_LINGER));
        verify(mWifiService).removeNetworkSuggestions(any(List.class),
                eq(TEST_PACKAGE_NAME), eq(ACTION_REMOVE_SUGGESTION_LINGER));
    }

    /**
     * Verify call to {@link WifiManager#getMaxNumberOfNetworkSuggestionsPerApp()}.
     */
    @Test
    public void getMaxNumberOfNetworkSuggestionsPerApp() {
        when(mContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(mActivityManager.isLowRamDevice()).thenReturn(true);
        assertEquals(256, mWifiManager.getMaxNumberOfNetworkSuggestionsPerApp());

        when(mActivityManager.isLowRamDevice()).thenReturn(false);
        assertEquals(1024, mWifiManager.getMaxNumberOfNetworkSuggestionsPerApp());
    }

    /**
     * Verify getting the factory MAC address.
     */
    @Test
    public void testGetFactoryMacAddress() throws Exception {
        when(mWifiService.getFactoryMacAddresses()).thenReturn(TEST_MAC_ADDRESSES);
        assertArrayEquals(TEST_MAC_ADDRESSES, mWifiManager.getFactoryMacAddresses());
        verify(mWifiService).getFactoryMacAddresses();
    }

    /**
     * Verify the call to getCallerConfiguredNetworks goes to WifiServiceImpl.
     */
    @Test
    public void testGetCallerConfiguredNetworks() throws Exception {
        mWifiManager.getCallerConfiguredNetworks();
        verify(mWifiService).getConfiguredNetworks(any(), any(), eq(true));
    }

    @Test
    public void testGetPrivilegedConfiguredNetworks() throws Exception {
        mWifiManager.getPrivilegedConfiguredNetworks();
        verify(mWifiService).getPrivilegedConfiguredNetworks(any(), any(), any());
    }

    /**
     * Verify the call to startRestrictingAutoJoinToSubscriptionId goes to WifiServiceImpl.
     */
    @Test
    public void testStartRestrictAutoJoinToSubscriptionId() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.startRestrictingAutoJoinToSubscriptionId(1);
        verify(mWifiService).startRestrictingAutoJoinToSubscriptionId(1);
    }

    /**
     * Verify the call to stopRestrictingAutoJoinToSubscriptionId goes to WifiServiceImpl.
     */
    @Test
    public void testStopTemporarilyDisablingAllNonCarrierMergedWifi() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.stopRestrictingAutoJoinToSubscriptionId();
        verify(mWifiService).stopRestrictingAutoJoinToSubscriptionId();
    }

    /**
     * Verify the call to addOnWifiUsabilityStatsListener goes to WifiServiceImpl.
     */
    @Test
    public void addOnWifiUsabilityStatsListenerGoesToWifiServiceImpl() throws Exception {
        mExecutor = new SynchronousExecutor();
        ArgumentCaptor<IOnWifiUsabilityStatsListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(IOnWifiUsabilityStatsListener.Stub.class);
        mWifiManager.addOnWifiUsabilityStatsListener(mExecutor, mOnWifiUsabilityStatsListener);
        verify(mWifiService).addOnWifiUsabilityStatsListener(callbackCaptor.capture());
        ContentionTimeStats[] contentionTimeStats = new ContentionTimeStats[4];
        contentionTimeStats[0] = new ContentionTimeStats(1, 2, 3, 4);
        contentionTimeStats[1] = new ContentionTimeStats(5, 6, 7, 8);
        contentionTimeStats[2] = new ContentionTimeStats(9, 10, 11, 12);
        contentionTimeStats[3] = new ContentionTimeStats(13, 14, 15, 16);
        RateStats[] rateStats = new RateStats[2];
        rateStats[0] = new RateStats(1, 3, 5, 7, 9, 11, 13, 15, 17);
        rateStats[1] = new RateStats(2, 4, 6, 8, 10, 12, 14, 16, 18);
        RadioStats[] radioStats = new RadioStats[2];
        radioStats[0] = new RadioStats(0, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        radioStats[1] = new RadioStats(1, 20, 21, 22, 23, 24, 25, 26, 27, 28);
        SparseArray<LinkStats> linkStats = new SparseArray<>();
        linkStats.put(0,
                new LinkStats(0, WifiUsabilityStatsEntry.LINK_STATE_NOT_IN_USE, 0, -50, 300,
                        200,
                        188, 2, 2, 100, 300, 100, 100, 200,
                        contentionTimeStats, rateStats));
        linkStats.put(1,
                new LinkStats(0, WifiUsabilityStatsEntry.LINK_STATE_IN_USE, 0, -40, 860, 600,
                        388, 2, 2, 200, 400, 100, 100, 200,
                        contentionTimeStats, rateStats));
        callbackCaptor.getValue().onWifiUsabilityStats(1, true,
                new WifiUsabilityStatsEntry(System.currentTimeMillis(), -50, 100, 10, 0, 5, 5,
                        100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 1, 100, 10,
                        100, 27, contentionTimeStats, rateStats, radioStats, 101, true, true, true,
                        0, 10, 10, true, linkStats));
        verify(mOnWifiUsabilityStatsListener).onWifiUsabilityStats(anyInt(), anyBoolean(),
                any(WifiUsabilityStatsEntry.class));
    }

    /**
     * Verify the call to removeOnWifiUsabilityStatsListener goes to WifiServiceImpl.
     */
    @Test
    public void removeOnWifiUsabilityListenerGoesToWifiServiceImpl() throws Exception {
        mExecutor = new SynchronousExecutor();
        ArgumentCaptor<IOnWifiUsabilityStatsListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(IOnWifiUsabilityStatsListener.Stub.class);
        mWifiManager.addOnWifiUsabilityStatsListener(mExecutor, mOnWifiUsabilityStatsListener);
        verify(mWifiService).addOnWifiUsabilityStatsListener(callbackCaptor.capture());

        mWifiManager.removeOnWifiUsabilityStatsListener(mOnWifiUsabilityStatsListener);
        verify(mWifiService).removeOnWifiUsabilityStatsListener(callbackCaptor.getValue());
    }

    /**
     * Test behavior of isEnhancedOpenSupported
     */
    @Test
    public void testIsEnhancedOpenSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_OWE));
        assertTrue(mWifiManager.isEnhancedOpenSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_OWE));
        assertFalse(mWifiManager.isEnhancedOpenSupported());
    }

    /**
     * Test behavior of isWpa3SaeSupported
     */
    @Test
    public void testIsWpa3SaeSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_WPA3_SAE));
        assertTrue(mWifiManager.isWpa3SaeSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_WPA3_SAE));
        assertFalse(mWifiManager.isWpa3SaeSupported());
    }

    /**
     * Test behavior of isWpa3SuiteBSupported
     */
    @Test
    public void testIsWpa3SuiteBSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_WPA3_SUITE_B));
        assertTrue(mWifiManager.isWpa3SuiteBSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_WPA3_SUITE_B));
        assertFalse(mWifiManager.isWpa3SuiteBSupported());
    }

    /**
     * Test behavior of isEasyConnectSupported
     */
    @Test
    public void testIsEasyConnectSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_DPP));
        assertTrue(mWifiManager.isEasyConnectSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_DPP));
        assertFalse(mWifiManager.isEasyConnectSupported());
    }

    /**
     * Test behavior of isEasyConnectDppAkmSupported
     */
    @Test
    public void testIsEasyConnectDppAkmSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_DPP_AKM));
        assertTrue(mWifiManager.isEasyConnectDppAkmSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_DPP_AKM));
        assertFalse(mWifiManager.isEasyConnectDppAkmSupported());
    }

    /**
     * Test behavior of isEasyConnectEnrolleeResponderModeSupported
     */
    @Test
    public void testIsEasyConnectEnrolleeResponderModeSupported() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_DPP_ENROLLEE_RESPONDER));
        assertTrue(mWifiManager.isEasyConnectEnrolleeResponderModeSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_DPP_ENROLLEE_RESPONDER));
        assertFalse(mWifiManager.isEasyConnectEnrolleeResponderModeSupported());
    }

    /**
     * Test behavior of isStaApConcurrencySupported
     */
    @Test
    public void testIsStaApConcurrencyOpenSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_AP_STA));
        assertTrue(mWifiManager.isStaApConcurrencySupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_AP_STA));
        assertFalse(mWifiManager.isStaApConcurrencySupported());
    }

    /**
     * Test behavior of isStaConcurrencySupported
     */
    @Test
    public void testIsStaConcurrencySupported() throws Exception {
        when(mWifiService.getSupportedFeatures()).thenReturn(0L);
        assertFalse(mWifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported());
        assertFalse(mWifiManager.isMakeBeforeBreakWifiSwitchingSupported());
        assertFalse(mWifiManager.isStaConcurrencyForRestrictedConnectionsSupported());
        assertFalse(mWifiManager.isStaConcurrencyForMultiInternetSupported());

        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_ADDITIONAL_STA_LOCAL_ONLY));
        assertTrue(mWifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported());
        assertFalse(mWifiManager.isMakeBeforeBreakWifiSwitchingSupported());
        assertFalse(mWifiManager.isStaConcurrencyForRestrictedConnectionsSupported());
        assertFalse(mWifiManager.isStaConcurrencyForMultiInternetSupported());

        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_ADDITIONAL_STA_MBB
                        | WIFI_FEATURE_ADDITIONAL_STA_RESTRICTED
                        | WIFI_FEATURE_ADDITIONAL_STA_MULTI_INTERNET));
        assertFalse(mWifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported());
        assertTrue(mWifiManager.isMakeBeforeBreakWifiSwitchingSupported());
        assertTrue(mWifiManager.isStaConcurrencyForRestrictedConnectionsSupported());
        assertTrue(mWifiManager.isStaConcurrencyForMultiInternetSupported());
    }

    /**
     * Test behavior of {@link WifiManager#addNetwork(WifiConfiguration)}
     */
    @Test
    public void testAddNetwork() throws Exception {
        WifiConfiguration configuration = new WifiConfiguration();
        when(mWifiService.addOrUpdateNetwork(any(), anyString(), any()))
                .thenReturn(TEST_NETWORK_ID);

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);

        assertEquals(mWifiManager.addNetwork(configuration), TEST_NETWORK_ID);
        verify(mWifiService).addOrUpdateNetwork(eq(configuration), eq(TEST_PACKAGE_NAME),
                bundleCaptor.capture());
        if (SdkLevel.isAtLeastS()) {
            assertEquals(mContext.getAttributionSource(),
                    bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        } else {
            assertNull(bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        }

        // send a null config
        assertEquals(mWifiManager.addNetwork(null), -1);
    }

    /**
     * Test {@link WifiManager#addNetworkPrivileged(WifiConfiguration)} goes to WifiService.
     * Also verify that an IllegalArgumentException is thrown if the input is null.
     */
    @Test
    public void testAddNetworkPrivileged() throws Exception {
        WifiConfiguration configuration = new WifiConfiguration();
        mWifiManager.addNetworkPrivileged(configuration);
        verify(mWifiService).addOrUpdateNetworkPrivileged(configuration,
                mContext.getOpPackageName());

        // send a null config and verify an exception is thrown
        try {
            mWifiManager.addNetworkPrivileged(null);
            fail("configuration is null - IllegalArgumentException is expected.");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test behavior of {@link WifiManager#addNetwork(WifiConfiguration)}
     */
    @Test
    public void testUpdateNetwork() throws Exception {
        WifiConfiguration configuration = new WifiConfiguration();
        when(mWifiService.addOrUpdateNetwork(any(), anyString(), any()))
                .thenReturn(TEST_NETWORK_ID);

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);

        configuration.networkId = TEST_NETWORK_ID;
        assertEquals(mWifiManager.updateNetwork(configuration), TEST_NETWORK_ID);
        verify(mWifiService).addOrUpdateNetwork(eq(configuration), eq(TEST_PACKAGE_NAME),
                bundleCaptor.capture());
        if (SdkLevel.isAtLeastS()) {
            assertEquals(mContext.getAttributionSource(),
                    bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        } else {
            assertNull(bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        }

        // config with invalid network ID
        configuration.networkId = -1;
        assertEquals(mWifiManager.updateNetwork(configuration), -1);

        // send a null config
        assertEquals(mWifiManager.updateNetwork(null), -1);
    }

    /**
     * Test behavior of {@link WifiManager#enableNetwork(int, boolean)}
     */
    @Test
    public void testEnableNetwork() throws Exception {
        when(mWifiService.enableNetwork(anyInt(), anyBoolean(), anyString()))
                .thenReturn(true);
        assertTrue(mWifiManager.enableNetwork(TEST_NETWORK_ID, true));
        verify(mWifiService).enableNetwork(TEST_NETWORK_ID, true, mContext.getOpPackageName());
    }

    /**
     * Test behavior of {@link WifiManager#disableNetwork(int)}
     */
    @Test
    public void testDisableNetwork() throws Exception {
        when(mWifiService.disableNetwork(anyInt(), anyString()))
                .thenReturn(true);
        assertTrue(mWifiManager.disableNetwork(TEST_NETWORK_ID));
        verify(mWifiService).disableNetwork(TEST_NETWORK_ID, mContext.getOpPackageName());
    }

    /**
     * Test behavior of {@link WifiManager#allowAutojoin(int, boolean)}
     * @throws Exception
     */
    @Test
    public void testAllowAutojoin() throws Exception {
        mWifiManager.allowAutojoin(1, true);
        verify(mWifiService).allowAutojoin(1, true);
    }

    /**
     * Test behavior of {@link WifiManager#allowAutojoinPasspoint(String, boolean)}
     * @throws Exception
     */
    @Test
    public void testAllowAutojoinPasspoint() throws Exception {
        final String fqdn = "FullyQualifiedDomainName";
        mWifiManager.allowAutojoinPasspoint(fqdn, true);
        verify(mWifiService).allowAutojoinPasspoint(fqdn, true);
    }

    /**
     * Test behavior of
     * {@link WifiManager#setMacRandomizationSettingPasspointEnabled(String, boolean)}
     */
    @Test
    public void testSetMacRandomizationSettingPasspointEnabled() throws Exception {
        final String fqdn = "FullyQualifiedDomainName";
        mWifiManager.setMacRandomizationSettingPasspointEnabled(fqdn, true);
        verify(mWifiService).setMacRandomizationSettingPasspointEnabled(fqdn, true);
    }

    /**
     * Test behavior of
     * {@link WifiManager#setMacRandomizationSettingPasspointEnabled(String, boolean)}
     */
    @Test
    public void testSetPasspointMeteredOverride() throws Exception {
        final String fqdn = "FullyQualifiedDomainName";
        mWifiManager.setPasspointMeteredOverride(fqdn, METERED_OVERRIDE_METERED);
        verify(mWifiService).setPasspointMeteredOverride(fqdn, METERED_OVERRIDE_METERED);
    }

    /**
     * Test behavior of {@link WifiManager#disconnect()}
     */
    @Test
    public void testDisconnect() throws Exception {
        when(mWifiService.disconnect(anyString())).thenReturn(true);
        assertTrue(mWifiManager.disconnect());
        verify(mWifiService).disconnect(mContext.getOpPackageName());
    }

    /**
     * Test behavior of {@link WifiManager#reconnect()}
     */
    @Test
    public void testReconnect() throws Exception {
        when(mWifiService.reconnect(anyString())).thenReturn(true);
        assertTrue(mWifiManager.reconnect());
        verify(mWifiService).reconnect(mContext.getOpPackageName());
    }

    /**
     * Test behavior of {@link WifiManager#reassociate()}
     */
    @Test
    public void testReassociate() throws Exception {
        when(mWifiService.reassociate(anyString())).thenReturn(true);
        assertTrue(mWifiManager.reassociate());
        verify(mWifiService).reassociate(mContext.getOpPackageName());
    }

    /**
     * Test behavior of {@link WifiManager#getSupportedFeatures()}
     */
    @Test
    public void testGetSupportedFeatures() throws Exception {
        long supportedFeatures =
                WIFI_FEATURE_SCANNER
                        | WIFI_FEATURE_PASSPOINT
                        | WIFI_FEATURE_P2P;
        when(mWifiService.getSupportedFeatures())
                .thenReturn(Long.valueOf(supportedFeatures));

        assertTrue(mWifiManager.isWifiScannerSupported());
        assertTrue(mWifiManager.isPasspointSupported());
        assertTrue(mWifiManager.isP2pSupported());
        assertFalse(mWifiManager.isPortableHotspotSupported());
        assertFalse(mWifiManager.isDeviceToDeviceRttSupported());
        assertFalse(mWifiManager.isDeviceToApRttSupported());
        assertFalse(mWifiManager.isPreferredNetworkOffloadSupported());
        assertFalse(mWifiManager.isTdlsSupported());
        assertFalse(mWifiManager.isOffChannelTdlsSupported());
        assertFalse(mWifiManager.isEnhancedPowerReportingSupported());
    }

    /**
     * Tests that passing a null Executor to {@link WifiManager#getWifiActivityEnergyInfoAsync}
     * throws an exception.
     */
    @Test(expected = NullPointerException.class)
    public void testGetWifiActivityInfoNullExecutor() throws Exception {
        mWifiManager.getWifiActivityEnergyInfoAsync(null, mOnWifiActivityEnergyInfoListener);
    }

    /**
     * Tests that passing a null listener to {@link WifiManager#getWifiActivityEnergyInfoAsync}
     * throws an exception.
     */
    @Test(expected = NullPointerException.class)
    public void testGetWifiActivityInfoNullListener() throws Exception {
        mWifiManager.getWifiActivityEnergyInfoAsync(mExecutor, null);
    }

    /** Tests that the listener runs on the correct Executor. */
    @Test
    public void testGetWifiActivityInfoRunsOnCorrectExecutor() throws Exception {
        mWifiManager.getWifiActivityEnergyInfoAsync(mExecutor, mOnWifiActivityEnergyInfoListener);
        ArgumentCaptor<IOnWifiActivityEnergyInfoListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnWifiActivityEnergyInfoListener.class);
        verify(mWifiService).getWifiActivityEnergyInfoAsync(listenerCaptor.capture());
        IOnWifiActivityEnergyInfoListener listener = listenerCaptor.getValue();
        listener.onWifiActivityEnergyInfo(mWifiActivityEnergyInfo);
        verify(mExecutor).execute(any());

        // ensure that the executor is only triggered once
        listener.onWifiActivityEnergyInfo(mWifiActivityEnergyInfo);
        verify(mExecutor).execute(any());
    }

    /** Tests that the correct listener runs. */
    @Test
    public void testGetWifiActivityInfoRunsCorrectListener() throws Exception {
        int[] flag = {0};
        mWifiManager.getWifiActivityEnergyInfoAsync(
                new SynchronousExecutor(), info -> flag[0]++);
        ArgumentCaptor<IOnWifiActivityEnergyInfoListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnWifiActivityEnergyInfoListener.class);
        verify(mWifiService).getWifiActivityEnergyInfoAsync(listenerCaptor.capture());
        IOnWifiActivityEnergyInfoListener listener = listenerCaptor.getValue();
        listener.onWifiActivityEnergyInfo(mWifiActivityEnergyInfo);
        assertEquals(1, flag[0]);

        // ensure that the listener is only triggered once
        listener.onWifiActivityEnergyInfo(mWifiActivityEnergyInfo);
        assertEquals(1, flag[0]);
    }

    /**
     * Test behavior of {@link WifiManager#getConnectionInfo()}
     */
    @Test
    public void testGetConnectionInfo() throws Exception {
        WifiInfo wifiInfo = new WifiInfo();
        when(mWifiService.getConnectionInfo(anyString(), nullable(String.class))).thenReturn(
                wifiInfo);

        assertEquals(wifiInfo, mWifiManager.getConnectionInfo());
    }

    /**
     * Test behavior of {@link WifiManager#is24GHzBandSupported()}
     */
    @Test
    public void testIs24GHzBandSupported() throws Exception {
        when(mWifiService.is24GHzBandSupported()).thenReturn(true);
        assertTrue(mWifiManager.is24GHzBandSupported());
        verify(mWifiService).is24GHzBandSupported();
    }

    /**
     * Test behavior of {@link WifiManager#is5GHzBandSupported()}
     */
    @Test
    public void testIs5GHzBandSupported() throws Exception {
        when(mWifiService.is5GHzBandSupported()).thenReturn(true);
        assertTrue(mWifiManager.is5GHzBandSupported());
        verify(mWifiService).is5GHzBandSupported();
    }

    /**
     * Test behavior of {@link WifiManager#is6GHzBandSupported()}
     */
    @Test
    public void testIs6GHzBandSupported() throws Exception {
        when(mWifiService.is6GHzBandSupported()).thenReturn(true);
        assertTrue(mWifiManager.is6GHzBandSupported());
        verify(mWifiService).is6GHzBandSupported();
    }

    /**
     * Test behavior of {@link WifiManager#is60GHzBandSupported()}
     */
    @Test
    public void testIs60GHzBandSupported() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        when(mWifiService.is60GHzBandSupported()).thenReturn(true);
        assertTrue(mWifiManager.is60GHzBandSupported());
        verify(mWifiService).is60GHzBandSupported();
    }

    /**
     * Test behavior of {@link WifiManager#isWifiStandardSupported()}
     */
    @Test
    public void testIsWifiStandardSupported() throws Exception {
        int standard = ScanResult.WIFI_STANDARD_11AX;
        when(mWifiService.isWifiStandardSupported(standard)).thenReturn(true);
        assertTrue(mWifiManager.isWifiStandardSupported(standard));
        verify(mWifiService).isWifiStandardSupported(standard);
    }

    /**
     * Test behavior of {@link WifiManager#getDhcpInfo()}
     */
    @Test
    public void testGetDhcpInfo() throws Exception {
        DhcpInfo dhcpInfo = new DhcpInfo();

        when(mWifiService.getDhcpInfo(TEST_PACKAGE_NAME)).thenReturn(dhcpInfo);
        assertEquals(dhcpInfo, mWifiManager.getDhcpInfo());
        verify(mWifiService).getDhcpInfo(TEST_PACKAGE_NAME);
    }

    /**
     * Test behavior of {@link WifiManager#setWifiEnabled(boolean)}
     */
    @Test
    public void testSetWifiEnabled() throws Exception {
        when(mWifiService.setWifiEnabled(anyString(), anyBoolean())).thenReturn(true);
        assertTrue(mWifiManager.setWifiEnabled(true));
        verify(mWifiService).setWifiEnabled(mContext.getOpPackageName(), true);
        assertTrue(mWifiManager.setWifiEnabled(false));
        verify(mWifiService).setWifiEnabled(mContext.getOpPackageName(), false);
    }

    /**
     * Test behavior of {@link WifiManager#connect(int, ActionListener)}
     */
    @Test
    public void testConnectWithListener() throws Exception {
        ActionListener externalListener = mock(ActionListener.class);
        mWifiManager.connect(TEST_NETWORK_ID, externalListener);

        ArgumentCaptor<IActionListener> binderListenerCaptor =
                ArgumentCaptor.forClass(IActionListener.class);
        verify(mWifiService).connect(eq(null), eq(TEST_NETWORK_ID), binderListenerCaptor.capture(),
                anyString(), any());
        assertNotNull(binderListenerCaptor.getValue());

        // Trigger on success.
        binderListenerCaptor.getValue().onSuccess();
        mLooper.dispatchAll();
        verify(externalListener).onSuccess();

        // Trigger on failure.
        binderListenerCaptor.getValue().onFailure(WifiManager.ActionListener.FAILURE_BUSY);
        mLooper.dispatchAll();
        verify(externalListener).onFailure(WifiManager.ActionListener.FAILURE_BUSY);
    }

    /**
     * Test behavior of {@link WifiManager#connect(int, ActionListener)}
     */
    @Test
    public void testConnectWithListenerHandleSecurityException() throws Exception {
        doThrow(new SecurityException()).when(mWifiService)
                .connect(eq(null), anyInt(), any(IActionListener.class), anyString(), any());
        ActionListener externalListener = mock(ActionListener.class);
        mWifiManager.connect(TEST_NETWORK_ID, externalListener);

        mLooper.dispatchAll();
        verify(externalListener).onFailure(ActionListener.FAILURE_NOT_AUTHORIZED);
    }

    /**
     * Test behavior of {@link WifiManager#connect(int, ActionListener)}
     */
    @Test
    public void testConnectWithListenerHandleRemoteException() throws Exception {
        doThrow(new RemoteException()).when(mWifiService)
                .connect(eq(null), anyInt(), any(IActionListener.class), anyString(), any());
        ActionListener externalListener = mock(ActionListener.class);
        mWifiManager.connect(TEST_NETWORK_ID, externalListener);

        mLooper.dispatchAll();
        verify(externalListener).onFailure(ActionListener.FAILURE_INTERNAL_ERROR);
    }

    /**
     * Test behavior of {@link WifiManager#connect(int, ActionListener)}
     */
    @Test
    public void testConnectWithoutListener() throws Exception {
        WifiConfiguration configuration = new WifiConfiguration();
        mWifiManager.connect(configuration, null);

        verify(mWifiService).connect(eq(configuration), eq(WifiConfiguration.INVALID_NETWORK_ID),
                eq(null), anyString(), any());
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterScanResultsCallbackWithNullCallback() throws Exception {
        mWifiManager.registerScanResultsCallback(mExecutor, null);
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterCallbackWithNullExecutor() throws Exception {
        mWifiManager.registerScanResultsCallback(null, mScanResultsCallback);
    }

    /**
     * Verify client provided callback is being called to the right callback.
     */
    @Test
    public void testAddScanResultsCallbackAndReceiveEvent() throws Exception {
        ArgumentCaptor<IScanResultsCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(IScanResultsCallback.Stub.class);
        mWifiManager.registerScanResultsCallback(new SynchronousExecutor(), mScanResultsCallback);
        verify(mWifiService).registerScanResultsCallback(callbackCaptor.capture());
        callbackCaptor.getValue().onScanResultsAvailable();
        verify(mRunnable).run();
    }

    /**
     * Verify client provided callback is being called to the right executor.
     */
    @Test
    public void testRegisterScanResultsCallbackWithTheTargetExecutor() throws Exception {
        ArgumentCaptor<IScanResultsCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(IScanResultsCallback.Stub.class);
        mWifiManager.registerScanResultsCallback(mExecutor, mScanResultsCallback);
        verify(mWifiService).registerScanResultsCallback(callbackCaptor.capture());
        mWifiManager.registerScanResultsCallback(mAnotherExecutor, mScanResultsCallback);
        callbackCaptor.getValue().onScanResultsAvailable();
        verify(mExecutor, never()).execute(any(Runnable.class));
        verify(mAnotherExecutor).execute(any(Runnable.class));
    }

    /**
     * Verify client register unregister then register again, to ensure callback still works.
     */
    @Test
    public void testRegisterUnregisterThenRegisterAgainWithScanResultCallback() throws Exception {
        ArgumentCaptor<IScanResultsCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(IScanResultsCallback.Stub.class);
        mWifiManager.registerScanResultsCallback(new SynchronousExecutor(), mScanResultsCallback);
        verify(mWifiService).registerScanResultsCallback(callbackCaptor.capture());
        mWifiManager.unregisterScanResultsCallback(mScanResultsCallback);
        callbackCaptor.getValue().onScanResultsAvailable();
        verify(mRunnable, never()).run();
        mWifiManager.registerScanResultsCallback(new SynchronousExecutor(), mScanResultsCallback);
        callbackCaptor.getValue().onScanResultsAvailable();
        verify(mRunnable).run();
    }

    /**
     * Verify client unregisterScanResultsCallback.
     */
    @Test
    public void testUnregisterScanResultsCallback() throws Exception {
        mWifiManager.unregisterScanResultsCallback(mScanResultsCallback);
        verify(mWifiService).unregisterScanResultsCallback(any());
    }

    /**
     * Verify client unregisterScanResultsCallback with null callback will cause an exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUnregisterScanResultsCallbackWithNullCallback() throws Exception {
        mWifiManager.unregisterScanResultsCallback(null);
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddSuggestionConnectionStatusListenerWithNullExecutor() {
        mWifiManager.addSuggestionConnectionStatusListener(null, mSuggestionConnectionListener);
    }

    /**
     * Verify an IllegalArgumentException is thrown if listener is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddSuggestionConnectionStatusListenerWithNullListener() {
        mWifiManager.addSuggestionConnectionStatusListener(mExecutor, null);
    }

    /**
     * Verify client provided listener is being called to the right listener.
     */
    @Test
    public void testAddSuggestionConnectionStatusListenerAndReceiveEvent() throws Exception {
        int errorCode = STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION;
        ArgumentCaptor<ISuggestionConnectionStatusListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISuggestionConnectionStatusListener.Stub.class);
        Executor executor = new SynchronousExecutor();
        mWifiManager.addSuggestionConnectionStatusListener(executor,
                mSuggestionConnectionListener);
        verify(mWifiService).registerSuggestionConnectionStatusListener(callbackCaptor.capture(),
                anyString(), nullable(String.class));
        callbackCaptor.getValue().onConnectionStatus(mWifiNetworkSuggestion, errorCode);
        verify(mSuggestionConnectionListener).onConnectionStatus(any(WifiNetworkSuggestion.class),
                eq(errorCode));
    }

    /**
     * Verify client provided listener is being called to the right executor.
     */
    @Test
    public void testAddSuggestionConnectionStatusListenerWithTheTargetExecutor() throws Exception {
        int errorCode = STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION;
        ArgumentCaptor<ISuggestionConnectionStatusListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISuggestionConnectionStatusListener.Stub.class);
        mWifiManager.addSuggestionConnectionStatusListener(mExecutor,
                mSuggestionConnectionListener);
        verify(mWifiService).registerSuggestionConnectionStatusListener(callbackCaptor.capture(),
                anyString(), nullable(String.class));
        callbackCaptor.getValue().onConnectionStatus(any(WifiNetworkSuggestion.class), errorCode);
        verify(mExecutor).execute(any(Runnable.class));
    }

    /**
     * Verify an IllegalArgumentException is thrown if listener is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveSuggestionConnectionListenerWithNullListener() {
        mWifiManager.removeSuggestionConnectionStatusListener(null);
    }

    /**
     * Verify removeSuggestionConnectionListener.
     */
    @Test
    public void testRemoveSuggestionConnectionListener() throws Exception {
        ArgumentCaptor<ISuggestionConnectionStatusListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISuggestionConnectionStatusListener.Stub.class);
        mWifiManager.addSuggestionConnectionStatusListener(mExecutor,
                mSuggestionConnectionListener);
        verify(mWifiService).registerSuggestionConnectionStatusListener(callbackCaptor.capture(),
                anyString(), nullable(String.class));

        mWifiManager.removeSuggestionConnectionStatusListener(mSuggestionConnectionListener);
        verify(mWifiService).unregisterSuggestionConnectionStatusListener(
                eq(callbackCaptor.getValue()), anyString());
    }

    /** Test {@link WifiManager#calculateSignalLevel(int)} */
    @Test
    public void testCalculateSignalLevel() throws Exception {
        when(mWifiService.calculateSignalLevel(anyInt())).thenReturn(3);
        int actual = mWifiManager.calculateSignalLevel(-60);
        verify(mWifiService).calculateSignalLevel(-60);
        assertEquals(3, actual);
    }

    /** Test {@link WifiManager#getMaxSignalLevel()} */
    @Test
    public void testGetMaxSignalLevel() throws Exception {
        when(mWifiService.calculateSignalLevel(anyInt())).thenReturn(4);
        int actual = mWifiManager.getMaxSignalLevel();
        verify(mWifiService).calculateSignalLevel(Integer.MAX_VALUE);
        assertEquals(4, actual);
    }

    /*
     * Test behavior of isWapiSupported
     * @throws Exception
     */
    @Test
    public void testIsWapiSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WifiManager.WIFI_FEATURE_WAPI));
        assertTrue(mWifiManager.isWapiSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WifiManager.WIFI_FEATURE_WAPI));
        assertFalse(mWifiManager.isWapiSupported());
    }

    /*
     * Test that DPP channel list is parsed correctly
     */
    @Test
    public void testparseDppChannelList() throws Exception {
        String channelList = "81/1,2,3,4,5,6,7,8,9,10,11,115/36,40,44,48";
        SparseArray<int[]> expectedResult = new SparseArray<>();
        expectedResult.append(81, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
        expectedResult.append(115, new int[]{36, 40, 44, 48});

        SparseArray<int[]> result = WifiManager.parseDppChannelList(channelList);
        assertEquals(expectedResult.size(), result.size());

        int index = 0;
        int key;

        // Compare the two primitive int arrays
        do {
            try {
                key = result.keyAt(index);
            } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                break;
            }
            int[] expected = expectedResult.get(key);
            int[] output = result.get(key);
            assertEquals(expected.length, output.length);
            for (int i = 0; i < output.length; i++) {
                assertEquals(expected[i], output[i]);
            }
            index++;
        } while (true);
    }

    /*
     * Test that DPP channel list parser gracefully fails for invalid input
     */
    @Test
    public void testparseDppChannelListWithInvalidFormats() throws Exception {
        String channelList = "1,2,3,4,5,6,7,8,9,10,11,36,40,44,48";
        SparseArray<int[]> result = WifiManager.parseDppChannelList(channelList);
        assertEquals(result.size(), 0);

        channelList = "ajgalskgjalskjg3-09683dh";
        result = WifiManager.parseDppChannelList(channelList);
        assertEquals(result.size(), 0);

        channelList = "13/abc,46////";
        result = WifiManager.parseDppChannelList(channelList);
        assertEquals(result.size(), 0);

        channelList = "11/4,5,13/";
        result = WifiManager.parseDppChannelList(channelList);
        assertEquals(result.size(), 0);

        channelList = "/24,6";
        result = WifiManager.parseDppChannelList(channelList);
        assertEquals(result.size(), 0);
    }

    /**
     * Test getWifiConfigsForMatchedNetworkSuggestions for given scanResults.
     */
    @Test
    public void testGetWifiConfigsForMatchedNetworkSuggestions() throws Exception {
        List<WifiConfiguration> testResults = new ArrayList<>();
        testResults.add(new WifiConfiguration());

        when(mWifiService.getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any(List.class)))
                .thenReturn(testResults);
        assertEquals(testResults, mWifiManager
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(new ArrayList<>()));
    }

    /**
     * Verify the call to setWifiConnectedNetworkScorer goes to WifiServiceImpl.
     */
    @Test
    public void setWifiConnectedNetworkScorerGoesToWifiServiceImpl() throws Exception {
        mExecutor = new SynchronousExecutor();
        mWifiManager.setWifiConnectedNetworkScorer(mExecutor, mWifiConnectedNetworkScorer);
        verify(mWifiService).setWifiConnectedNetworkScorer(any(IBinder.class),
                any(IWifiConnectedNetworkScorer.Stub.class));
    }

    /**
     * Verify the call to clearWifiConnectedNetworkScorer goes to WifiServiceImpl.
     */
    @Test
    public void clearWifiConnectedNetworkScorerGoesToWifiServiceImpl() throws Exception {
        mExecutor = new SynchronousExecutor();
        mWifiManager.setWifiConnectedNetworkScorer(mExecutor, mWifiConnectedNetworkScorer);
        verify(mWifiService).setWifiConnectedNetworkScorer(any(IBinder.class),
                any(IWifiConnectedNetworkScorer.Stub.class));

        mWifiManager.clearWifiConnectedNetworkScorer();
        verify(mWifiService).clearWifiConnectedNetworkScorer();
    }

    /**
     * Verify that Wi-Fi connected scorer receives score update observer after registeration.
     */
    @Test
    public void verifyScorerReceiveScoreUpdateObserverAfterRegistration() throws Exception {
        mExecutor = new SynchronousExecutor();
        mWifiManager.setWifiConnectedNetworkScorer(mExecutor, mWifiConnectedNetworkScorer);
        ArgumentCaptor<IWifiConnectedNetworkScorer.Stub> scorerCaptor =
                ArgumentCaptor.forClass(IWifiConnectedNetworkScorer.Stub.class);
        verify(mWifiService).setWifiConnectedNetworkScorer(any(IBinder.class),
                scorerCaptor.capture());
        scorerCaptor.getValue().onSetScoreUpdateObserver(any());
        mLooper.dispatchAll();
        verify(mWifiConnectedNetworkScorer).onSetScoreUpdateObserver(any());
    }

    /**
     * Verify that Wi-Fi connected scorer receives session ID when onStart/onStop methods
     * are called.
     */
    @Test
    public void verifyScorerReceiveSessionIdWhenStartStopIsCalled() throws Exception {
        mExecutor = new SynchronousExecutor();
        mWifiManager.setWifiConnectedNetworkScorer(mExecutor, mWifiConnectedNetworkScorer);
        ArgumentCaptor<IWifiConnectedNetworkScorer.Stub> callbackCaptor =
                ArgumentCaptor.forClass(IWifiConnectedNetworkScorer.Stub.class);
        verify(mWifiService).setWifiConnectedNetworkScorer(any(IBinder.class),
                callbackCaptor.capture());
        callbackCaptor.getValue().onStart(new WifiConnectedSessionInfo.Builder(10)
                .setUserSelected(true)
                .build());
        callbackCaptor.getValue().onStop(10);
        mLooper.dispatchAll();
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == 10
                        && sessionInfo.isUserSelected()));
        verify(mWifiConnectedNetworkScorer).onStop(10);
    }

    /**
     * Verify the call to setWifiScoringEnabled goes to WifiServiceImpl.
     */
    @Test
    public void setWifiScoringEnabledGoesToWifiServiceImpl() throws Exception {
        mWifiManager.setWifiScoringEnabled(true);
        verify(mWifiService).setWifiScoringEnabled(true);
    }

    /**
     * Verify the call to addCustomDhcpOptions goes to WifiServiceImpl.
     */
    @Test
    public void addCustomDhcpOptionsGoesToWifiServiceImpl() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiManager.addCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), TEST_OUI, new ArrayList<DhcpOption>());
        verify(mWifiService).addCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), TEST_OUI, new ArrayList<DhcpOption>());
    }

    /**
     * Verify the call to removeCustomDhcpOptions goes to WifiServiceImpl.
     */
    @Test
    public void removeCustomDhcpOptionsGoesToWifiServiceImpl() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiManager.removeCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), TEST_OUI);
        verify(mWifiService).removeCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), TEST_OUI);
    }

    @Test
    public void testScanThrottle() throws Exception {
        mWifiManager.setScanThrottleEnabled(true);
        verify(mWifiService).setScanThrottleEnabled(true);

        when(mWifiService.isScanThrottleEnabled()).thenReturn(false);
        assertFalse(mWifiManager.isScanThrottleEnabled());
        verify(mWifiService).isScanThrottleEnabled();
    }

    @Test
    public void testAutoWakeup() throws Exception {
        mWifiManager.setAutoWakeupEnabled(true);
        verify(mWifiService).setAutoWakeupEnabled(true);

        when(mWifiService.isAutoWakeupEnabled()).thenReturn(false);
        assertFalse(mWifiManager.isAutoWakeupEnabled());
        verify(mWifiService).isAutoWakeupEnabled();
    }


    @Test
    public void testScanAvailable() throws Exception {
        mWifiManager.setScanAlwaysAvailable(true);
        verify(mWifiService).setScanAlwaysAvailable(true, TEST_PACKAGE_NAME);

        when(mWifiService.isScanAlwaysAvailable()).thenReturn(false);
        assertFalse(mWifiManager.isScanAlwaysAvailable());
        verify(mWifiService).isScanAlwaysAvailable();
    }

    @Test
    public void testSetCarrierNetworkOffload() throws Exception {
        mWifiManager.setCarrierNetworkOffloadEnabled(TEST_SUB_ID, true, false);
        verify(mWifiService).setCarrierNetworkOffloadEnabled(TEST_SUB_ID,
                true, false);
    }

    @Test
    public void testGetCarrierNetworkOffload() throws Exception {
        when(mWifiService.isCarrierNetworkOffloadEnabled(TEST_SUB_ID, false)).thenReturn(true);
        assertTrue(mWifiManager.isCarrierNetworkOffloadEnabled(TEST_SUB_ID, false));
        verify(mWifiService).isCarrierNetworkOffloadEnabled(TEST_SUB_ID, false);
    }


    /**
     * Verify an IllegalArgumentException is thrown if listener is not provided.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveSuggestionUserApprovalStatusListenerWithNullListener() {
        mWifiManager.removeSuggestionUserApprovalStatusListener(null);
    }


    /**
     * Verify removeSuggestionUserApprovalStatusListener.
     */
    @Test
    public void testRemoveSuggestionUserApprovalStatusListener() throws Exception {
        ArgumentCaptor<ISuggestionUserApprovalStatusListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISuggestionUserApprovalStatusListener.Stub.class);
        mWifiManager.addSuggestionUserApprovalStatusListener(mExecutor,
                mSuggestionUserApprovalStatusListener);
        verify(mWifiService).addSuggestionUserApprovalStatusListener(callbackCaptor.capture(),
                anyString());

        mWifiManager.removeSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener);
        verify(mWifiService).removeSuggestionUserApprovalStatusListener(
                eq(callbackCaptor.getValue()), anyString());
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor not provided.
     */
    @Test(expected = NullPointerException.class)
    public void testAddSuggestionUserApprovalStatusListenerWithNullExecutor() {
        mWifiManager.addSuggestionUserApprovalStatusListener(null,
                mSuggestionUserApprovalStatusListener);
    }

    /**
     * Verify an IllegalArgumentException is thrown if listener is not provided.
     */
    @Test(expected = NullPointerException.class)
    public void testAddSuggestionUserApprovalStatusListenerWithNullListener() {
        mWifiManager.addSuggestionUserApprovalStatusListener(mExecutor, null);
    }

    /**
     * Verify client provided listener is being called to the right listener.
     */
    @Test
    public void testAddSuggestionUserApprovalStatusListenerAndReceiveEvent() throws Exception {
        ArgumentCaptor<ISuggestionUserApprovalStatusListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISuggestionUserApprovalStatusListener.Stub.class);
        Executor executor = new SynchronousExecutor();
        mWifiManager.addSuggestionUserApprovalStatusListener(executor,
                mSuggestionUserApprovalStatusListener);
        verify(mWifiService).addSuggestionUserApprovalStatusListener(callbackCaptor.capture(),
                anyString());
        callbackCaptor.getValue().onUserApprovalStatusChange(
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER);
        verify(mSuggestionUserApprovalStatusListener).onUserApprovalStatusChange(
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER);
    }

    /**
     * Verify client provided listener is being called to the right executor.
     */
    @Test
    public void testAddSuggestionUserApprovalStatusListenerWithTheTargetExecutor()
            throws Exception {
        ArgumentCaptor<ISuggestionUserApprovalStatusListener.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISuggestionUserApprovalStatusListener.Stub.class);
        mWifiManager.addSuggestionUserApprovalStatusListener(mExecutor,
                mSuggestionUserApprovalStatusListener);
        verify(mWifiService).addSuggestionUserApprovalStatusListener(callbackCaptor.capture(),
                anyString());
        callbackCaptor.getValue().onUserApprovalStatusChange(
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER);
        verify(mExecutor).execute(any(Runnable.class));
    }

    @Test
    public void testSetExternalPnoScanRequestNullFrequencies() throws Exception {
        mWifiManager.setExternalPnoScanRequest(Collections.EMPTY_LIST, null,
                mock(Executor.class), mock(WifiManager.PnoScanResultsCallback.class));
        // null frequencies should get converted to empty array
        verify(mWifiService).setExternalPnoScanRequest(any(), any(), eq(Collections.EMPTY_LIST),
                eq(new int[0]), any(), any());
    }

    @Test
    public void testSetEmergencyScanRequestInProgress() throws Exception {
        mWifiManager.setEmergencyScanRequestInProgress(true);
        verify(mWifiService).setEmergencyScanRequestInProgress(true);

        mWifiManager.setEmergencyScanRequestInProgress(false);
        verify(mWifiService).setEmergencyScanRequestInProgress(false);
    }

    @Test
    public void testRemoveAppState() throws Exception {
        mWifiManager.removeAppState(TEST_UID, TEST_PACKAGE_NAME);
        verify(mWifiService).removeAppState(TEST_UID, TEST_PACKAGE_NAME);
    }

    /**
     * Test behavior of isPasspointTermsAndConditionsSupported
     */
    @Test
    public void testIsPasspointTermsAndConditionsSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS));
        assertTrue(mWifiManager.isPasspointTermsAndConditionsSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS));
        assertFalse(mWifiManager.isPasspointTermsAndConditionsSupported());
    }

    /**
     * Verify the call to setOverrideCountryCode goes to WifiServiceImpl.
     */
    @Test
    public void testSetOverrideCountryCode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.setOverrideCountryCode(TEST_COUNTRY_CODE);
        verify(mWifiService).setOverrideCountryCode(eq(TEST_COUNTRY_CODE));
    }

    /**
     * Verify the call to clearOverrideCountryCode goes to WifiServiceImpl.
     */
    @Test
    public void testClearOverrideCountryCode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.clearOverrideCountryCode();
        verify(mWifiService).clearOverrideCountryCode();
    }

    /**
     * Verify the call to setDefaultCountryCode goes to WifiServiceImpl.
     */
    @Test
    public void testSetDefaultCountryCode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiManager.setDefaultCountryCode(TEST_COUNTRY_CODE);
        verify(mWifiService).setDefaultCountryCode(eq(TEST_COUNTRY_CODE));
    }

    /**
     * Test behavior of flushPasspointAnqpCache
     */
    @Test
    public void testFlushPasspointAnqpCache() throws Exception {
        mWifiManager.flushPasspointAnqpCache();
        verify(mWifiService).flushPasspointAnqpCache(anyString());
    }

    @Test
    public void testSetPnoScanState() throws Exception {
        mWifiManager.setPnoScanState(WifiManager.PNO_SCAN_STATE_DISABLED_UNTIL_WIFI_TOGGLE);
        verify(mWifiService).setPnoScanEnabled(false, true, TEST_PACKAGE_NAME);

        mWifiManager.setPnoScanState(WifiManager.PNO_SCAN_STATE_DISABLED_UNTIL_REBOOT);
        verify(mWifiService).setPnoScanEnabled(false, false, TEST_PACKAGE_NAME);

        mWifiManager.setPnoScanState(WifiManager.PNO_SCAN_STATE_ENABLED);
        verify(mWifiService).setPnoScanEnabled(eq(true), anyBoolean(), any());

        assertThrows(IllegalArgumentException.class, () -> mWifiManager.setPnoScanState(999));
    }



    /**
     * Test behavior of isDecoratedIdentitySupported
     */
    @Test
    public void testIsDecoratedIdentitySupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_DECORATED_IDENTITY));
        assertTrue(mWifiManager.isDecoratedIdentitySupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_DECORATED_IDENTITY));
        assertFalse(mWifiManager.isDecoratedIdentitySupported());
    }

    /**
     * Test behavior of isTrustOnFirstUseSupported.
     */
    @Test
    public void testIsTrustOnFirstUseSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_TRUST_ON_FIRST_USE));
        assertTrue(mWifiManager.isTrustOnFirstUseSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_TRUST_ON_FIRST_USE));
        assertFalse(mWifiManager.isTrustOnFirstUseSupported());
    }

    /**
     * Verify call to getAllowedChannels goes to WifiServiceImpl
     */
    @Test
    public void testGetAllowedChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        int band = WifiScanner.WIFI_BAND_24_5_6_GHZ;
        int mode = WifiAvailableChannel.OP_MODE_WIFI_AWARE
                | WifiAvailableChannel.OP_MODE_WIFI_DIRECT_GO
                | WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI;
        mWifiManager.getAllowedChannels(band, mode);
        verify(mWifiService).getUsableChannels(eq(band), eq(mode),
                eq(WifiAvailableChannel.FILTER_REGULATORY), eq(TEST_PACKAGE_NAME), any());
    }

    /**
     * Verify call to getUsableChannels goes to WifiServiceImpl
     */
    @Test
    public void testGetUsableChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        int band = WifiScanner.WIFI_BAND_BOTH_WITH_DFS;
        int mode = WifiAvailableChannel.OP_MODE_WIFI_AWARE
                | WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI;
        mWifiManager.getUsableChannels(band, mode);
        verify(mWifiService).getUsableChannels(eq(band), eq(mode),
                eq(WifiAvailableChannel.FILTER_CONCURRENCY
                    | WifiAvailableChannel.FILTER_CELLULAR_COEXISTENCE),
                eq(TEST_PACKAGE_NAME), any());
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test
    public void testRegisterActiveCountryCodeChangedCallbackThrowsExceptionOnNullCallback() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiManager.registerActiveCountryCodeChangedCallback(
                    new HandlerExecutor(mHandler), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor is null.
     */
    @Test
    public void testRegisterActiveCountryCodeChangedCallbackThrowsExceptionOnNullExecutor() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiManager.registerActiveCountryCodeChangedCallback(null,
                    mActiveCountryCodeChangedCallback);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test
    public void testUnregisterActiveCountryCodeChangedCallbackThrowsExceptionOnNullCallback() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiManager.unregisterActiveCountryCodeChangedCallback(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify the call to registerActiveCountryCodeChangedCallback goes to WifiServiceImpl.
     */
    @Test
    public void testRegisterActiveCountryCodeChangedCallbackCallGoesToWifiServiceImpl()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiManager.registerActiveCountryCodeChangedCallback(new HandlerExecutor(mHandler),
                mActiveCountryCodeChangedCallback);
        verify(mWifiService).registerDriverCountryCodeChangedListener(
                any(IOnWifiDriverCountryCodeChangedListener.Stub.class), anyString(),
                any() /* getAttributionTag(), nullable */);
    }

    /**
     * Verify the call to unregisterActiveCountryCodeChangedCallback goes to WifiServiceImpl.
     */
    @Test
    public void testUnregisterActiveCountryCodeChangedCallbackCallGoesToWifiServiceImpl()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        ArgumentCaptor<IOnWifiDriverCountryCodeChangedListener.Stub> listenerCaptor =
                ArgumentCaptor.forClass(IOnWifiDriverCountryCodeChangedListener.Stub.class);
        mWifiManager.registerActiveCountryCodeChangedCallback(new HandlerExecutor(mHandler),
                mActiveCountryCodeChangedCallback);
        verify(mWifiService).registerDriverCountryCodeChangedListener(listenerCaptor.capture(),
                 anyString(), any() /* getAttributionTag(), nullable */);

        mWifiManager.unregisterActiveCountryCodeChangedCallback(
                mActiveCountryCodeChangedCallback);
        verify(mWifiService).unregisterDriverCountryCodeChangedListener(listenerCaptor.getValue());
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void testDriverCountryCodeChangedCallbackProxyCallsOnActiveCountryCodeChanged()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        ArgumentCaptor<IOnWifiDriverCountryCodeChangedListener.Stub> listenerCaptor =
                ArgumentCaptor.forClass(IOnWifiDriverCountryCodeChangedListener.Stub.class);
        mWifiManager.registerActiveCountryCodeChangedCallback(new HandlerExecutor(mHandler),
                mActiveCountryCodeChangedCallback);
        verify(mWifiService).registerDriverCountryCodeChangedListener(listenerCaptor.capture(),
                 anyString(), any() /* getAttributionTag(), nullable */);

        listenerCaptor.getValue().onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mActiveCountryCodeChangedCallback).onActiveCountryCodeChanged(TEST_COUNTRY_CODE);
    }

    /*
     * Verify client-provided callback is being called through callback proxy
     */
    @Test
    public void testDriverCountryCodeChangedCallbackProxyCallsOnCountryCodeInactiveWhenNull()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        ArgumentCaptor<IOnWifiDriverCountryCodeChangedListener.Stub> listenerCaptor =
                ArgumentCaptor.forClass(IOnWifiDriverCountryCodeChangedListener.Stub.class);
        mWifiManager.registerActiveCountryCodeChangedCallback(new HandlerExecutor(mHandler),
                mActiveCountryCodeChangedCallback);
        verify(mWifiService).registerDriverCountryCodeChangedListener(listenerCaptor.capture(),
                 anyString(), any() /* getAttributionTag(), nullable */);

        listenerCaptor.getValue().onDriverCountryCodeChanged(null);
        mLooper.dispatchAll();
        verify(mActiveCountryCodeChangedCallback).onCountryCodeInactive();
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test
    public void registerLohsSoftApCallbackThrowsIllegalArgumentExceptionOnNullCallback() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiManager.registerLocalOnlyHotspotSoftApCallback(
                    new HandlerExecutor(mHandler), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if executor is null.
     */
    @Test
    public void registerLohsSoftApCallbackThrowsIllegalArgumentExceptionOnNullExecutor() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiManager.registerLocalOnlyHotspotSoftApCallback(null, mSoftApCallback);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify an IllegalArgumentException is thrown if callback is not provided.
     */
    @Test
    public void unregisterLohsSoftApCallbackThrowsIllegalArgumentExceptionOnNullCallback() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiManager.unregisterLocalOnlyHotspotSoftApCallback(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify the call to registerLocalOnlyHotspotSoftApCallback goes to WifiServiceImpl.
     */
    @Test
    public void registerLocalOnlyHotspotSoftApCallbackCallGoesToWifiServiceImpl()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiManager.registerLocalOnlyHotspotSoftApCallback(new HandlerExecutor(mHandler),
                mSoftApCallback);
        verify(mWifiService).registerLocalOnlyHotspotSoftApCallback(
                any(ISoftApCallback.Stub.class), any(Bundle.class));
    }

    /**
     * Verify the call to unregisterLocalOnlyHotspotSoftApCallback goes to WifiServiceImpl.
     */
    @Test
    public void unregisterLocalOnlyHotspotSoftApCallbackCallGoesToWifiServiceImpl()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerLocalOnlyHotspotSoftApCallback(new HandlerExecutor(mHandler),
                mSoftApCallback);
        verify(mWifiService).registerLocalOnlyHotspotSoftApCallback(callbackCaptor.capture(),
                any(Bundle.class));

        mWifiManager.unregisterLocalOnlyHotspotSoftApCallback(mSoftApCallback);
        verify(mWifiService).unregisterLocalOnlyHotspotSoftApCallback(eq(callbackCaptor.getValue()),
                any(Bundle.class));
    }

    /**
     * Verify lohs client-provided callback is being called through callback proxy.
     */
    @Test
    public void softApCallbackProxyCallsCallbackForLohsRegister() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiClient testWifiClient = new WifiClient(MacAddress.fromString("22:33:44:55:66:77"),
                TEST_AP_INSTANCES[0]);
        SoftApCapability testSoftApCapability = new SoftApCapability(0);
        // Prepare test info and clients
        initTestInfoAndAddToTestMap(1);
        List<WifiClient> clientList = initWifiClientAndAddToTestMap(TEST_AP_INSTANCES[0], 1, 0);

        ArgumentCaptor<ISoftApCallback.Stub> callbackCaptor =
                ArgumentCaptor.forClass(ISoftApCallback.Stub.class);
        mWifiManager.registerLocalOnlyHotspotSoftApCallback(
                new HandlerExecutor(mHandler), mSoftApCallback);
        verify(mWifiService).registerLocalOnlyHotspotSoftApCallback(callbackCaptor.capture(),
                any(Bundle.class));

        SoftApState state = new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_INTERFACE_NAME);
        callbackCaptor.getValue().onStateChanged(state);
        callbackCaptor.getValue().onConnectedClientsOrInfoChanged(
                (Map<String, SoftApInfo>) mTestSoftApInfoMap.clone(),
                (Map<String, List<WifiClient>>) mTestWifiClientsMap.clone(), false, true);
        callbackCaptor.getValue().onCapabilityChanged(testSoftApCapability);
        callbackCaptor.getValue().onBlockedClientConnecting(testWifiClient,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);

        mLooper.dispatchAll();
        verify(mSoftApCallback).onStateChanged(WIFI_AP_STATE_ENABLED, 0);
        ArgumentCaptor<SoftApState> softApStateCaptor =
                ArgumentCaptor.forClass(SoftApState.class);
        verify(mSoftApCallback).onStateChanged(softApStateCaptor.capture());
        assertEquals(state, softApStateCaptor.getValue());

        verify(mSoftApCallback).onConnectedClientsChanged(clientList);
        verify(mSoftApCallback).onConnectedClientsChanged(mTestApInfo1, clientList);
        verify(mSoftApCallback).onInfoChanged(mTestApInfo1);
        verify(mSoftApCallback).onInfoChanged(Mockito.argThat((List<SoftApInfo> infos) ->
                        infos.contains(mTestApInfo1)));

        verify(mSoftApCallback).onCapabilityChanged(testSoftApCapability);

        verify(mSoftApCallback).onBlockedClientConnecting(testWifiClient,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
    }

    /*
     * Verify call to {@link WifiManager#isStaConcurrencyForMultiInternetSupported}.
     */
    @Test
    public void testIsStaConcurrencyForMultiInternetSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_ADDITIONAL_STA_MULTI_INTERNET));
        assertTrue(mWifiManager.isStaConcurrencyForMultiInternetSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_ADDITIONAL_STA_MULTI_INTERNET));
        assertFalse(mWifiManager.isStaConcurrencyForMultiInternetSupported());
    }

    /*
     * Verify call to {@link WifiManager#getStaConcurrencyForMultiInternetMode()}.
     */
    @Test
    public void testGetStaConcurrencyForMultiInternetMode() throws Exception {
        final int mode = mWifiManager.getStaConcurrencyForMultiInternetMode();
        verify(mWifiService).getStaConcurrencyForMultiInternetMode();
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED, mode);
    }

    /*
     * Verify call to {@link WifiManager#setStaConcurrencyForMultiInternetMode()}.
     */
    @Test
    public void testSetStaConcurrencyForMultiInternetMode() throws Exception {
        mWifiManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP);
        verify(mWifiService).setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP);
    }

    /*
     * Verify call to {@link WifiManager#isDualBandSimultaneousSupported}.
     */
    @Test
    public void testIsDualBandSimultaneousSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_DUAL_BAND_SIMULTANEOUS));
        assertTrue(mWifiManager.isDualBandSimultaneousSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_DUAL_BAND_SIMULTANEOUS));
        assertFalse(mWifiManager.isDualBandSimultaneousSupported());
    }
    /*
     * Verify call to {@link WifiManager#isTidToLinkMappingSupported()}
     */
    @Test
    public void testIsTidToLinkMappingSupported() throws Exception {
        when(mWifiService.getSupportedFeatures()).thenReturn(WIFI_FEATURE_T2LM_NEGOTIATION);
        assertTrue(mWifiManager.isTidToLinkMappingNegotiationSupported());
        when(mWifiService.getSupportedFeatures()).thenReturn(~WIFI_FEATURE_T2LM_NEGOTIATION);
        assertFalse(mWifiManager.isTidToLinkMappingNegotiationSupported());
    }

    /**
     * Verify call to
     * {@link WifiManager#reportCreateInterfaceImpact(int, boolean, Executor, BiConsumer)}.
     */
    @Test
    public void testIsItPossibleToCreateInterface() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        final int interfaceToCreate = WifiManager.WIFI_INTERFACE_TYPE_DIRECT;
        final boolean requireNewInterface = false;
        final boolean canCreate = true;
        final String packageName1 = "TestPackage1";
        final String packageName2 = "TestPackage2";
        final int[] interfaces =
                {WifiManager.WIFI_INTERFACE_TYPE_AP, WifiManager.WIFI_INTERFACE_TYPE_AWARE};
        final String[] packagesForInterfaces =
                {TEST_PACKAGE_NAME, packageName1 + "," + packageName2};
        final Set<WifiManager.InterfaceCreationImpact> interfacePairs = Set.of(
                new WifiManager.InterfaceCreationImpact(interfaces[0],
                        new ArraySet<>(new String[]{TEST_PACKAGE_NAME})),
                new WifiManager.InterfaceCreationImpact(interfaces[1],
                        new ArraySet<>(new String[]{packageName1, packageName2})));
        when(mContext.getOpPackageName()).thenReturn(TEST_PACKAGE_NAME);
        BiConsumer<Boolean, Set<WifiManager.InterfaceCreationImpact>> resultCallback = mock(
                BiConsumer.class);
        ArgumentCaptor<IInterfaceCreationInfoCallback.Stub> cbCaptor = ArgumentCaptor.forClass(
                IInterfaceCreationInfoCallback.Stub.class);
        ArgumentCaptor<Set<WifiManager.InterfaceCreationImpact>> resultCaptor =
                ArgumentCaptor.forClass(Set.class);

        mWifiManager.reportCreateInterfaceImpact(interfaceToCreate, requireNewInterface,
                new SynchronousExecutor(), resultCallback);
        verify(mWifiService).reportCreateInterfaceImpact(eq(TEST_PACKAGE_NAME),
                eq(interfaceToCreate), eq(requireNewInterface), cbCaptor.capture());
        cbCaptor.getValue().onResults(canCreate, interfaces, packagesForInterfaces);
        verify(resultCallback).accept(eq(canCreate), resultCaptor.capture());
        assertEquals(interfacePairs, resultCaptor.getValue());
    }

    /**
     * Verify call to getChannelData goes to WifiServiceImpl
     */
    @Test
    public void testChannelData() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        Consumer<List<Bundle>> resultsCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        mWifiManager.getChannelData(executor, resultsCallback);
        verify(mWifiService).getChannelData(any(IListListener.Stub.class), eq(TEST_PACKAGE_NAME),
                any(Bundle.class));
    }

    /**
     * Verify call to {@link WifiManager#addQosPolicies(List, Executor, Consumer)}.
     */
    @Test
    public void testAddQosPolicies() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        final int policyId = 2;
        final int direction = QosPolicyParams.DIRECTION_DOWNLINK;
        final int userPriority = QosPolicyParams.USER_PRIORITY_VIDEO_LOW;
        final int ipVersion = QosPolicyParams.IP_VERSION_4;
        QosPolicyParams policyParams = new QosPolicyParams.Builder(policyId, direction)
                .setUserPriority(userPriority)
                .setIpVersion(ipVersion)
                .build();
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        Consumer<List<Integer>> resultsCallback = mock(Consumer.class);

        mWifiManager.addQosPolicies(Arrays.asList(policyParams), executor, resultsCallback);
        verify(mWifiService).addQosPolicies(any(), any(), eq(TEST_PACKAGE_NAME),
                any(IListListener.Stub.class));
    }

    /**
     * Verify call to {@link WifiManager#removeQosPolicies(int[])}
     */
    @Test
    public void testRemoveQosPolicies() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int[] policyIdList = new int[]{127, 128};
        mWifiManager.removeQosPolicies(policyIdList);
        verify(mWifiService).removeQosPolicies(any(), eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testAddRemoveLocaOnlyConnectionListener() throws RemoteException {
        assertThrows(IllegalArgumentException.class, () -> mWifiManager
                .addLocalOnlyConnectionFailureListener(null, mLocalOnlyConnectionFailureListener));
        assertThrows(IllegalArgumentException.class, () -> mWifiManager
                .addLocalOnlyConnectionFailureListener(mExecutor, null));
        mWifiManager.addLocalOnlyConnectionFailureListener(mExecutor,
                mLocalOnlyConnectionFailureListener);
        verify(mWifiService).addLocalOnlyConnectionStatusListener(any(), eq(TEST_PACKAGE_NAME),
                nullable(String.class));
        mWifiManager.removeLocalOnlyConnectionFailureListener(mLocalOnlyConnectionFailureListener);
        verify(mWifiService).removeLocalOnlyConnectionStatusListener(any(), eq(TEST_PACKAGE_NAME));
    }

    /**
     * Verify if the call for set / get link layer stats polling interval goes to WifiServiceImpl
     */
    @Test
    public void testSetAndGetLinkLayerStatsPollingInterval() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiManager.setLinkLayerStatsPollingInterval(TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS);
        verify(mWifiService).setLinkLayerStatsPollingInterval(
                eq(TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS));

        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        Consumer<Integer> resultsCallback = mock(Consumer.class);
        mWifiManager.getLinkLayerStatsPollingInterval(executor, resultsCallback);
        verify(mWifiService).getLinkLayerStatsPollingInterval(any(IIntegerListener.class));

        // null executor
        assertThrows(NullPointerException.class,
                () -> mWifiManager.getLinkLayerStatsPollingInterval(null, resultsCallback));
        // null resultsCallback
        assertThrows(NullPointerException.class,
                () -> mWifiManager.getLinkLayerStatsPollingInterval(executor, null));
    }

    /**
     * Verify {@link WifiManager#setMloMode(int)} and {@link WifiManager#getMloMode()}.
     */
    @Test
    public void testMloMode() throws RemoteException {
        Consumer<Boolean> resultsSetCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        // Out of range values.
        assertThrows(IllegalArgumentException.class,
                () -> mWifiManager.setMloMode(-1, executor, resultsSetCallback));
        assertThrows(IllegalArgumentException.class,
                () -> mWifiManager.setMloMode(1000, executor, resultsSetCallback));
        // Null executor/callback exception.
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.setMloMode(WifiManager.MLO_MODE_DEFAULT, null,
                        resultsSetCallback));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> mWifiManager.setMloMode(WifiManager.MLO_MODE_DEFAULT, executor, null));
        // Set and verify.
        mWifiManager.setMloMode(WifiManager.MLO_MODE_LOW_POWER, executor, resultsSetCallback);
        verify(mWifiService).setMloMode(eq(WifiManager.MLO_MODE_LOW_POWER),
                any(IBooleanListener.Stub.class));
        // Get and verify.
        Consumer<Integer> resultsGetCallback = mock(Consumer.class);
        mWifiManager.getMloMode(executor, resultsGetCallback);
        verify(mWifiService).getMloMode(any(IIntegerListener.Stub.class));
    }

    @Test
    public void testVerboseLogging() throws RemoteException {
        mWifiManager.setVerboseLoggingEnabled(true);
        verify(mWifiService).enableVerboseLogging(VERBOSE_LOGGING_LEVEL_ENABLED);
        mWifiManager.setVerboseLoggingEnabled(false);
        verify(mWifiService).enableVerboseLogging(VERBOSE_LOGGING_LEVEL_DISABLED);
        when(mWifiService.getVerboseLoggingLevel()).thenReturn(VERBOSE_LOGGING_LEVEL_ENABLED);
        assertTrue(mWifiManager.isVerboseLoggingEnabled());
        when(mWifiService.getVerboseLoggingLevel())
                .thenReturn(VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY);
        assertTrue(mWifiManager.isVerboseLoggingEnabled());
        when(mWifiService.getVerboseLoggingLevel())
                .thenReturn(VERBOSE_LOGGING_LEVEL_WIFI_AWARE_ENABLED_ONLY);
        assertFalse(mWifiManager.isVerboseLoggingEnabled());
        when(mWifiService.getVerboseLoggingLevel()).thenReturn(VERBOSE_LOGGING_LEVEL_DISABLED);
        assertFalse(mWifiManager.isVerboseLoggingEnabled());
    }

    /**
     * Test behavior of isWepSupported
     */
    @Test
    public void testIsWepSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_WEP));
        assertTrue(mWifiManager.isWepSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_WEP));
        assertFalse(mWifiManager.isWepSupported());
    }

    /**
     * Test behavior of isWpaPersonalSupported
     */
    @Test
    public void testIsWpaPersonalSupported() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_WPA_PERSONAL));
        assertTrue(mWifiManager.isWpaPersonalSupported());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_WPA_PERSONAL));
        assertFalse(mWifiManager.isWpaPersonalSupported());
    }

    @Test
    public void testSetWepAllowed() throws Exception {
        mWifiManager.setWepAllowed(true);
        verify(mWifiService).setWepAllowed(true);
        mWifiManager.setWepAllowed(false);
        verify(mWifiService).setWepAllowed(false);
    }

    @Test
    public void testQueryWepAllowed() throws Exception {
        Consumer<Boolean> resultsSetCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        // Null executor/callback exception.
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.queryWepAllowed(null, resultsSetCallback));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> mWifiManager.queryWepAllowed(executor, null));
        // Set and verify.
        mWifiManager.queryWepAllowed(executor, resultsSetCallback);
        verify(mWifiService).queryWepAllowed(
                any(IBooleanListener.Stub.class));
    }

    /**
     * Verify {@link WifiManager#setPerSsidRoamingMode(WifiSsid, int)}.
     */
    @Test
    public void testSetPerSsidRoamingMode() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        // Invalid input throws exception.
        assertThrows(IllegalArgumentException.class,
                () -> mWifiManager.setPerSsidRoamingMode(WifiSsid.fromString(TEST_SSID), -1));
        assertThrows(IllegalArgumentException.class,
                () -> mWifiManager.setPerSsidRoamingMode(WifiSsid.fromString(TEST_SSID), 3));
        assertThrows(NullPointerException.class,
                () -> mWifiManager.setPerSsidRoamingMode(null, WifiManager.ROAMING_MODE_NORMAL));
        // Set and verify.
        mWifiManager.setPerSsidRoamingMode(WifiSsid.fromString(TEST_SSID),
                WifiManager.ROAMING_MODE_NORMAL);
        verify(mWifiService).setPerSsidRoamingMode(WifiSsid.fromString(TEST_SSID),
                WifiManager.ROAMING_MODE_NORMAL, TEST_PACKAGE_NAME);
    }

    /**
     * Verify {@link WifiManager#removePerSsidRoamingMode(WifiSsid)}.
     */
    @Test
    public void testRemovePerSsidRoamingMode() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        // Invalid input throws exception.
        assertThrows(NullPointerException.class,
                () -> mWifiManager.removePerSsidRoamingMode(null));
        // Remove and verify.
        mWifiManager.removePerSsidRoamingMode(WifiSsid.fromString(TEST_SSID));
        verify(mWifiService).removePerSsidRoamingMode(WifiSsid.fromString(TEST_SSID),
                TEST_PACKAGE_NAME);
    }

    /**
     * Verify {@link WifiManager#getPerSsidRoamingModes()}.
     */
    @Test
    public void testGetPerSsidRoamingModes() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        Consumer<Map<String, Integer>> resultsSetCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        // Null executor/callback exception.
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.getPerSsidRoamingModes(null,
                        resultsSetCallback));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.getPerSsidRoamingModes(executor,
                        null));
        // Get and verify.
        mWifiManager.getPerSsidRoamingModes(executor, resultsSetCallback);
        verify(mWifiService).getPerSsidRoamingModes(eq(TEST_PACKAGE_NAME),
                any(IMapListener.Stub.class));
    }

    @Test
    public void testGetTwtCapabilities() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        Consumer<Bundle> resultCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        // Null check
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.getTwtCapabilities(executor, null));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.getTwtCapabilities(null, resultCallback));
        // Get and verify
        mWifiManager.getTwtCapabilities(executor, resultCallback);
        verify(mWifiService).getTwtCapabilities(any(ITwtCapabilitiesListener.Stub.class),
                bundleCaptor.capture());
        verify(mContext.getAttributionSource()).equals(
                bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
    }

    @Test
    public void testSetupTwtSession() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        TwtSessionCallback resultCallback = mock(TwtSessionCallback.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        TwtRequest twtRequest = mock(TwtRequest.class);
        // Null check
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.setupTwtSession(null, executor, resultCallback));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.setupTwtSession(twtRequest, null, resultCallback));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.setupTwtSession(twtRequest, executor, null));
        // Call twtSessionSetup and verify
        mWifiManager.setupTwtSession(twtRequest, executor, resultCallback);
        verify(mWifiService).setupTwtSession(any(TwtRequest.class), any(ITwtCallback.class),
                bundleCaptor.capture());
        verify(mContext.getAttributionSource()).equals(
                bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
    }

    @Test
    public void testGetStatsTwtSession() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        Consumer<Bundle> resultCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        // Null check
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.getStatsTwtSession(0, null, resultCallback));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.getStatsTwtSession(0, executor, null));
        // Call twtSessionGetStats and verify
        mWifiManager.getStatsTwtSession(2, executor, resultCallback);
        verify(mWifiService).getStatsTwtSession(eq(2), any(ITwtStatsListener.class),
                bundleCaptor.capture());
        verify(mContext.getAttributionSource()).equals(
                bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
    }

    @Test
    public void testTeardownTwtSession() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        // Call twtSessionTeardown and verify
        mWifiManager.teardownTwtSession(10);
        verify(mWifiService).teardownTwtSession(eq(10), bundleCaptor.capture());
        verify(mContext.getAttributionSource()).equals(
                bundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
    }

    /**
     * Test behavior of isD2dSupportedWhenInfraStaDisabled.
     */
    @Test
    public void testIsD2dSupportedWhenInfraStaDisabled() throws Exception {
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(WIFI_FEATURE_D2D_WHEN_INFRA_STA_DISABLED));
        assertTrue(mWifiManager.isD2dSupportedWhenInfraStaDisabled());
        when(mWifiService.getSupportedFeatures())
                .thenReturn(new Long(~WIFI_FEATURE_D2D_WHEN_INFRA_STA_DISABLED));
        assertFalse(mWifiManager.isD2dSupportedWhenInfraStaDisabled());
    }

    @Test
    public void testSetD2dAllowedInfraStaDisabled() throws Exception {
        mWifiManager.setD2dAllowedWhenInfraStaDisabled(true);
        verify(mWifiService).setD2dAllowedWhenInfraStaDisabled(true);
        mWifiManager.setD2dAllowedWhenInfraStaDisabled(false);
        verify(mWifiService).setD2dAllowedWhenInfraStaDisabled(false);
    }

    @Test
    public void testQueryD2dAllowedInfraStaDisabled() throws Exception {
        Consumer<Boolean> resultsSetCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        // Null executor/callback exception.
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.queryD2dAllowedWhenInfraStaDisabled(null, resultsSetCallback));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> mWifiManager.queryD2dAllowedWhenInfraStaDisabled(executor, null));
        // Set and verify.
        mWifiManager.queryD2dAllowedWhenInfraStaDisabled(executor, resultsSetCallback);
        verify(mWifiService).queryD2dAllowedWhenInfraStaDisabled(
                any(IBooleanListener.Stub.class));
    }

    @Test
    public void testRetrieveRestoreWifiBackupData() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        Consumer<byte[]> resultsSetCallback = mock(Consumer.class);
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        byte[] testByteArray = new byte[0];
        // Null executor/callback exception.
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> mWifiManager.retrieveWifiBackupData(null, resultsSetCallback));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> mWifiManager.retrieveWifiBackupData(executor, null));
        // Call and verify.
        mWifiManager.retrieveWifiBackupData(executor, resultsSetCallback);
        verify(mWifiService).retrieveWifiBackupData(
                any(IByteArrayListener.Stub.class));
        mWifiManager.restoreWifiBackupData(testByteArray);
        verify(mWifiService).restoreWifiBackupData(eq(testByteArray));
    }


    @Test
    public void testIsPreferredNetworkOffloadSupported() throws Exception {
        mWifiManager.isPreferredNetworkOffloadSupported();
        verify(mWifiService).isPnoSupported();
    }
}
