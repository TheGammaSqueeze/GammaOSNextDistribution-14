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

import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_INVALID_CONFIGURATION_ENTERPRISE;

import static com.android.server.wifi.WifiConfigManager.BUFFERED_WRITE_ALARM_TAG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.Manifest;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.app.test.TestAlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.DhcpOption;
import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiScoreCard.PerNetwork;
import com.android.server.wifi.proto.nano.WifiMetricsProto.UserActionEvent;
import com.android.server.wifi.util.LruConnectionTracker;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConfigManager}.
 */
@SmallTest
public class WifiConfigManagerTest extends WifiBaseTest {

    private static final String TEST_SSID = "\"test_ssid\"";
    private static final String UNTRANSLATED_HEX_SSID = "abcdef";
    private static final String TEST_BSSID = "0a:08:5c:67:89:00";
    private static final long TEST_WALLCLOCK_CREATION_TIME_MILLIS = 9845637;
    private static final long TEST_WALLCLOCK_UPDATE_TIME_MILLIS = 75455637;
    private static final int TEST_CREATOR_UID = WifiConfigurationTestUtil.TEST_UID;
    private static final int TEST_NO_PERM_UID = 7;
    private static final int TEST_UPDATE_UID = 4;
    private static final int TEST_SYSUI_UID = 56;
    private static final int TEST_OTHER_USER_UID = 1000000000;
    private static final int TEST_DEFAULT_USER = UserHandle.USER_SYSTEM;
    private static final int TEST_MAX_NUM_ACTIVE_CHANNELS_FOR_PARTIAL_SCAN = 5;
    private static final Integer[] TEST_FREQ_LIST = {2400, 2450, 5150, 5175, 5650};
    private static final String TEST_CREATOR_NAME = "com.wificonfigmanager.creator";
    private static final String TEST_UPDATE_NAME = "com.wificonfigmanager.update";
    private static final String TEST_NO_PERM_NAME = "com.wificonfigmanager.noperm";
    private static final String TEST_WIFI_NAME = "android.uid.system";
    private static final String TEST_OTHER_USER_NAME = "com.test.package";
    private static final String TEST_DEFAULT_GW_MAC_ADDRESS = "0f:67:ad:ef:09:34";
    private static final String TEST_STATIC_PROXY_HOST_1 = "192.168.48.1";
    private static final int    TEST_STATIC_PROXY_PORT_1 = 8000;
    private static final String TEST_STATIC_PROXY_EXCLUSION_LIST_1 = "";
    private static final String TEST_PAC_PROXY_LOCATION_1 = "http://bleh";
    private static final String TEST_STATIC_PROXY_HOST_2 = "192.168.1.1";
    private static final int    TEST_STATIC_PROXY_PORT_2 = 3000;
    private static final String TEST_STATIC_PROXY_EXCLUSION_LIST_2 = "";
    private static final String TEST_PAC_PROXY_LOCATION_2 = "http://blah";
    private static final int TEST_RSSI = -50;
    private static final int TEST_FREQUENCY_1 = 2412;
    private static final MacAddress TEST_RANDOMIZED_MAC =
            MacAddress.fromString("d2:11:19:34:a5:20");
    private static final int DATA_SUBID = 1;
    private static final String SYSUI_PACKAGE_NAME = "com.android.systemui";
    private static final int ALL_NON_CARRIER_MERGED_WIFI_MIN_DISABLE_DURATION_MINUTES = 30;
    private static final int ALL_NON_CARRIER_MERGED_WIFI_MAX_DISABLE_DURATION_MINUTES = 480;
    private static final int TEST_NETWORK_SELECTION_ENABLE_REASON =
            NetworkSelectionStatus.DISABLED_NONE;
    private static final int TEST_NETWORK_SELECTION_TEMP_DISABLE_REASON =
            NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY;
    private static final int TEST_NETWORK_SELECTION_PERM_DISABLE_REASON =
            NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER;
    private static final String TEST_PACKAGE_NAME = "com.test.xxxx";

    @Mock private Context mContext;
    @Mock private Clock mClock;
    @Mock private UserManager mUserManager;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private TelephonyManager mDataTelephonyManager;
    @Mock private WifiKeyStore mWifiKeyStore;
    @Mock private WifiConfigStore mWifiConfigStore;
    @Mock private PackageManager mPackageManager;
    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private WifiInjector mWifiInjector;
    @Mock private WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock private NetworkListSharedStoreData mNetworkListSharedStoreData;
    @Mock private NetworkListUserStoreData mNetworkListUserStoreData;
    @Mock private RandomizedMacStoreData mRandomizedMacStoreData;
    @Mock private WifiConfigManager.OnNetworkUpdateListener mWcmListener;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private FeatureFlags mFeatureFlags;
    @Mock private MacAddressUtil mMacAddressUtil;
    @Mock private WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock private WifiScoreCard mWifiScoreCard;
    @Mock private PerNetwork mPerNetwork;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private WifiConfigManager.OnNetworkUpdateListener mListener;
    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private ClientModeManager mPrimaryClientModeManager;
    @Mock private WifiGlobals mWifiGlobals;
    @Mock private BuildProperties mBuildProperties;
    @Mock private SsidTranslator mSsidTranslator;
    private LruConnectionTracker mLruConnectionTracker;

    private MockResources mResources;
    private InOrder mContextConfigStoreMockOrder;
    private InOrder mNetworkListStoreDataMockOrder;
    private WifiConfigManager mWifiConfigManager;
    private boolean mStoreReadTriggered = false;
    private TestLooper mLooper = new TestLooper();
    private MockitoSession mSession;
    private WifiCarrierInfoManager mWifiCarrierInfoManager;
    private TestAlarmManager mAlarmManager;

    /**
     * Setup the mocks and an instance of WifiConfigManager before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Set up the inorder for verifications. This is needed to verify that the broadcasts,
        // store writes for network updates followed by network additions are in the expected order.
        mContextConfigStoreMockOrder = inOrder(mContext, mWifiConfigStore);
        mNetworkListStoreDataMockOrder =
                inOrder(mNetworkListSharedStoreData, mNetworkListUserStoreData);

        // Set up the package name stuff & permission override.
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        mAlarmManager = new TestAlarmManager();
        mLooper = new TestLooper();
        when(mContext.getSystemService(AlarmManager.class))
                .thenReturn(mAlarmManager.getAlarmManager());
        mResources = new MockResources();
        mResources.setBoolean(
                R.bool.config_wifi_only_link_same_credential_configurations, false);
        mResources.setBoolean(
                R.bool.config_wifiAllowLinkingUnknownDefaultGatewayConfigurations, true);
        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                TEST_MAX_NUM_ACTIVE_CHANNELS_FOR_PARTIAL_SCAN);
        mResources.setBoolean(R.bool.config_wifi_connected_mac_randomization_supported, true);
        mResources.setInteger(R.integer.config_wifiMaxPnoSsidCount, 16);
        mResources.setInteger(
                R.integer.config_wifiAllNonCarrierMergedWifiMinDisableDurationMinutes,
                ALL_NON_CARRIER_MERGED_WIFI_MIN_DISABLE_DURATION_MINUTES);
        mResources.setInteger(
                R.integer.config_wifiAllNonCarrierMergedWifiMaxDisableDurationMinutes,
                ALL_NON_CARRIER_MERGED_WIFI_MAX_DISABLE_DURATION_MINUTES);
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurations, -1);
        mResources.setInteger(
                R.integer.config_wifiMaxNumWifiConfigurationsAddedByAllApps, 200);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);

        // Setup UserManager profiles for the default user.
        setupUserProfiles(TEST_DEFAULT_USER);

        doAnswer(new AnswerWithArguments() {
            public String answer(int uid) throws Exception {
                if (uid == TEST_CREATOR_UID) {
                    return TEST_CREATOR_NAME;
                } else if (uid == TEST_UPDATE_UID) {
                    return TEST_UPDATE_NAME;
                } else if (uid == TEST_SYSUI_UID) {
                    return SYSUI_PACKAGE_NAME;
                } else if (uid == TEST_NO_PERM_UID) {
                    return TEST_NO_PERM_NAME;
                } else if (uid == Process.WIFI_UID) {
                    return TEST_WIFI_NAME;
                } else if (uid == TEST_OTHER_USER_UID) {
                    return TEST_OTHER_USER_NAME;
                }
                fail("Unexpected UID: " + uid);
                return "";
            }
        }).when(mPackageManager).getNameForUid(anyInt());
        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, int reason) {
                if (reason == TEST_NETWORK_SELECTION_ENABLE_REASON) {
                    config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                            NetworkSelectionStatus.NETWORK_SELECTION_ENABLED);
                    config.status = WifiConfiguration.Status.ENABLED;
                } else if (reason == TEST_NETWORK_SELECTION_TEMP_DISABLE_REASON) {
                    config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                            NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
                } else if (reason == TEST_NETWORK_SELECTION_PERM_DISABLE_REASON) {
                    config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                            NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
                    config.status = WifiConfiguration.Status.DISABLED;
                } else {
                    // ignore all other failure reasons for simplicity of mocking.
                }
                return true;
            }
        }).when(mWifiBlocklistMonitor).updateNetworkSelectionStatus(any(), anyInt());

        when(mContext.getSystemService(ActivityManager.class))
                .thenReturn(mock(ActivityManager.class));

        when(mWifiKeyStore
                .updateNetworkKeys(any(WifiConfiguration.class), any()))
                .thenReturn(true);

        setupStoreDataForRead(new ArrayList<>(), new ArrayList<>());

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mockIsDeviceOwner(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), any())).thenReturn(false);
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(anyInt()))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isDeviceInDemoMode(any())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(any(), anyInt())).thenReturn(true);
        when(mWifiLastResortWatchdog.shouldIgnoreSsidUpdate()).thenReturn(false);
        when(mMacAddressUtil.calculatePersistentMacForSta(any(), anyInt()))
                .thenReturn(TEST_RANDOMIZED_MAC);
        when(mWifiScoreCard.lookupNetwork(any())).thenReturn(mPerNetwork);

        WifiContext wifiContext = mock(WifiContext.class);
        when(wifiContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackagesHoldingPermissions(any(String[].class), anyInt()))
                .thenReturn(Collections.emptyList());
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.delaySaveToStore()).thenReturn(true);
        mWifiCarrierInfoManager = spy(new WifiCarrierInfoManager(mTelephonyManager,
                mSubscriptionManager, mWifiInjector, mock(FrameworkFacade.class),
                wifiContext, mock(WifiConfigStore.class), mock(Handler.class),
                mWifiMetrics, mClock, mock(WifiPseudonymManager.class)));
        mLruConnectionTracker = new LruConnectionTracker(100, mContext);

        when(mWifiInjector.getClock()).thenReturn(mClock);
        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mWifiInjector.getWifiCarrierInfoManager()).thenReturn(mWifiCarrierInfoManager);
        when(mWifiInjector.getWifiMetrics()).thenReturn(mWifiMetrics);
        when(mWifiInjector.getWifiBlocklistMonitor()).thenReturn(mWifiBlocklistMonitor);
        when(mWifiInjector.getWifiLastResortWatchdog()).thenReturn(mWifiLastResortWatchdog);
        when(mWifiInjector.getWifiScoreCard()).thenReturn(mWifiScoreCard);
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mWifiPermissionsUtil);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mFrameworkFacade);
        when(mWifiInjector.getMacAddressUtil()).thenReturn(mMacAddressUtil);
        when(mWifiInjector.getBuildProperties()).thenReturn(mBuildProperties);

        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(WifiInjector.class, withSettings().lenient())
                .mockStatic(WifiConfigStore.class, withSettings().lenient())
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(WifiInjector.getInstance()).thenReturn(mWifiInjector);
        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.getSsidTranslator()).thenReturn(mSsidTranslator);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_WPA3_SAE | WifiManager.WIFI_FEATURE_OWE);
        when(mWifiGlobals.isWpa3SaeUpgradeEnabled()).thenReturn(true);
        when(mWifiGlobals.isOweUpgradeEnabled()).thenReturn(true);
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);
        when(WifiConfigStore.createUserFiles(anyInt(), anyBoolean())).thenReturn(mock(List.class));
        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mDataTelephonyManager);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        when(mSsidTranslator.getTranslatedSsid(any())).thenAnswer(
                (Answer<WifiSsid>) invocation -> getTranslatedSsid(invocation.getArgument(0)));
        when(mSsidTranslator.getAllPossibleOriginalSsids(any())).thenAnswer(
                (Answer<List<WifiSsid>>) invocation -> Arrays.asList(invocation.getArgument(0),
                        WifiSsid.fromString(UNTRANSLATED_HEX_SSID))
        );

        createWifiConfigManager();
        mWifiConfigManager.addOnNetworkUpdateListener(mWcmListener);
    }

    /** Mock translating an SSID */
    private WifiSsid getTranslatedSsid(WifiSsid ssid) {
        byte[] originalBytes = ssid.getBytes();
        byte[] translatedBytes = new byte[originalBytes.length + 1];
        for (int i = 0; i < originalBytes.length; i++) {
            translatedBytes[i] = (byte) (originalBytes[i] + 1);
        }
        return WifiSsid.fromBytes(translatedBytes);
    }

    private void mockIsDeviceOwner(boolean result) {
        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), any())).thenReturn(result);
        when(mWifiPermissionsUtil.isDeviceOwner(anyInt())).thenReturn(result);
    }

    private void mockIsAdmin(boolean result) {
        when(mWifiPermissionsUtil.isAdmin(anyInt(), any())).thenReturn(result);
    }

    private void mockIsOrganizationOwnedDeviceAdmin(boolean result) {
        when(mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(anyInt(), any()))
                .thenReturn(result);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Verifies that network retrieval via
     * {@link WifiConfigManager#getConfiguredNetworks()} and
     * {@link WifiConfigManager#getConfiguredNetworksWithPasswords()} works even if we have not
     * yet loaded data from store.
     */
    @Test
    public void testGetConfiguredNetworksBeforeLoadFromStore() {
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
        assertTrue(mWifiConfigManager.getConfiguredNetworksWithPasswords().isEmpty());
    }

    /**
     * Verifies that network addition via
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} fails if we have not
     * yet loaded data from store.
     */
    @Test
    public void testAddNetworkIsRejectedBeforeLoadFromStore() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        assertFalse(
                mWifiConfigManager.addOrUpdateNetwork(openNetwork, TEST_CREATOR_UID).isSuccess());
    }

    /**
     * Verifies the {@link WifiConfigManager#saveToStore(boolean)} is rejected until the store has
     * been read first using {@link WifiConfigManager#loadFromStore()}.
     */
    @Test
    public void testSaveToStoreIsRejectedBeforeLoadFromStore() throws Exception {
        assertFalse(mWifiConfigManager.saveToStore(true));
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());

        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).read();

        assertTrue(mWifiConfigManager.saveToStore(true));
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
        verify(mWifiMetrics).wifiConfigStored(anyInt());
    }

    /**
     * Verify that a randomized MAC address is generated even if the KeyStore operation fails.
     */
    @Test
    public void testRandomizedMacIsGeneratedEvenIfKeyStoreFails() {
        when(mMacAddressUtil.calculatePersistentMacForSta(any(), anyInt())).thenReturn(null);

        // Try adding a network.
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);
        verifyAddNetworkToWifiConfigManager(openNetwork);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());

        // Verify that despite KeyStore returning null, we are still getting a valid MAC address.
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS,
                retrievedNetworks.get(0).getRandomizedMacAddress().toString());
    }

    /**
     * Verifies the addition of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddSingleOpenNetwork() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        // Ensure that the newly added network is disabled.
        assertEquals(WifiConfiguration.Status.DISABLED, retrievedNetworks.get(0).status);
    }

    /**
     * Verifies the addition of a WAPI-PSK network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddWapiPskNetwork() {
        WifiConfiguration wapiPskNetwork = WifiConfigurationTestUtil.createWapiPskNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(wapiPskNetwork);

        verifyAddNetworkToWifiConfigManager(wapiPskNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        // Ensure that the newly added network is disabled.
        assertEquals(WifiConfiguration.Status.DISABLED, retrievedNetworks.get(0).status);
    }

    /**
     * Verifies the addition of a WAPI-PSK network with hex bytes using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddWapiPskHexNetwork() {
        WifiConfiguration wapiPskNetwork = WifiConfigurationTestUtil.createWapiPskNetwork();
        wapiPskNetwork.preSharedKey =
            "123456780abcdef0123456780abcdef0123456780abcdef0123456780abcdef0";
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(wapiPskNetwork);

        verifyAddNetworkToWifiConfigManager(wapiPskNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        // Ensure that the newly added network is disabled.
        assertEquals(WifiConfiguration.Status.DISABLED, retrievedNetworks.get(0).status);
    }

    /**
     * Verifies the addition of a WAPI-CERT network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddWapiCertNetwork() {
        WifiConfiguration wapiCertNetwork = WifiConfigurationTestUtil.createWapiCertNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(wapiCertNetwork);

        verifyAddNetworkToWifiConfigManager(wapiCertNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        // Ensure that the newly added network is disabled.
        assertEquals(WifiConfiguration.Status.DISABLED, retrievedNetworks.get(0).status);
    }

    /**
     * Verifies the addition of a single network when the corresponding ephemeral network exists.
     */
    @Test
    public void testAddSingleOpenNetworkWhenCorrespondingEphemeralNetworkExists() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);
        WifiConfiguration ephemeralNetwork = new WifiConfiguration(openNetwork);
        ephemeralNetwork.ephemeral = true;

        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNetwork);

        NetworkUpdateResult result = addNetworkToWifiConfigManager(openNetwork);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());

        verifyNetworkRemoveBroadcast();
        verifyNetworkAddBroadcast();

        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        // Verify that the config store write was triggered with this new configuration.
        verifyNetworkInConfigStoreData(openNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Ensure that the newly added network is disabled.
        assertEquals(WifiConfiguration.Status.DISABLED, retrievedNetworks.get(0).status);
    }

    /**
     * Verifies the addition of an ephemeral network when the corresponding ephemeral network
     * exists.
     */
    @Test
    public void testAddEphemeralNetworkWhenCorrespondingEphemeralNetworkExists() throws Exception {
        WifiConfiguration ephemeralNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        ephemeralNetwork.ephemeral = true;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(ephemeralNetwork);

        WifiConfiguration ephemeralNetwork2 = new WifiConfiguration(ephemeralNetwork);
        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNetwork);

        NetworkUpdateResult result = addNetworkToWifiConfigManager(ephemeralNetwork2);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        verifyNetworkUpdateBroadcast();

        // Ensure that the write was not invoked for ephemeral network addition.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verifies when adding a new network with already saved MAC, the saved MAC gets set to the
     * internal WifiConfiguration.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddingNetworkWithMatchingMacAddressOverridesField() {
        int prevMappingSize = mWifiConfigManager.getRandomizedMacAddressMappingSize();
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        Map<String, String> randomizedMacAddressMapping = new HashMap<>();
        final String randMac = "12:23:34:45:56:67";
        randomizedMacAddressMapping.put(openNetwork.getNetworkKey(), randMac);
        assertNotEquals(randMac, openNetwork.getRandomizedMacAddress());
        when(mRandomizedMacStoreData.getMacMapping()).thenReturn(randomizedMacAddressMapping);

        // reads XML data storage
        //mWifiConfigManager.loadFromStore();
        verifyAddNetworkToWifiConfigManager(openNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(randMac, retrievedNetworks.get(0).getRandomizedMacAddress().toString());
        // Verify that for networks that we already have randomizedMacAddressMapping saved
        // we are still correctly writing into the WifiConfigStore.
        assertEquals(prevMappingSize + 1, mWifiConfigManager.getRandomizedMacAddressMappingSize());
    }

    /**
     * Verifies that when a network is added, removed, and then added back again, its randomized
     * MAC address doesn't change.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testRandomizedMacAddressIsPersistedOverForgetNetwork() {
        int prevMappingSize = mWifiConfigManager.getRandomizedMacAddressMappingSize();
        // Create and add an open network
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkToWifiConfigManager(openNetwork);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();

        // Gets the randomized MAC address of the network added.
        final String randMac = retrievedNetworks.get(0).getRandomizedMacAddress().toString();
        // This MAC should be different from the default, uninitialized randomized MAC.
        assertNotEquals(openNetwork.getRandomizedMacAddress().toString(), randMac);

        // Remove the added network
        verifyRemoveNetworkFromWifiConfigManager(openNetwork);
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        // Adds the network back again and verify randomized MAC address stays the same.
        verifyAddNetworkToWifiConfigManager(openNetwork);
        retrievedNetworks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(randMac, retrievedNetworks.get(0).getRandomizedMacAddress().toString());
        // Verify that we are no longer persisting the randomized MAC address with WifiConfigStore.
        assertEquals(prevMappingSize, mWifiConfigManager.getRandomizedMacAddressMappingSize());
    }

    /**
     * Verifies that when a network is read from xml storage, it is assigned a randomized MAC
     * address if it doesn't have one yet. Then try removing the network and then add it back
     * again and verify the randomized MAC didn't change.
     */
    @Test
    public void testRandomizedMacAddressIsGeneratedForConfigurationReadFromStore()
            throws Exception {
        // Create and add an open network
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        final String defaultMac = openNetwork.getRandomizedMacAddress().toString();
        List<WifiConfiguration> sharedConfigList = new ArrayList<>();
        sharedConfigList.add(openNetwork);

        // Setup xml storage
        setupStoreDataForRead(sharedConfigList, new ArrayList<>());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        // Gets the randomized MAC address of the network added.
        final String randMac = retrievedNetworks.get(0).getRandomizedMacAddress().toString();
        // This MAC should be different from the default, uninitialized randomized MAC.
        assertNotEquals(defaultMac, randMac);

        assertTrue(mWifiConfigManager.removeNetwork(
                openNetwork.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        // Adds the network back again and verify randomized MAC address stays the same.
        mWifiConfigManager.addOrUpdateNetwork(openNetwork, TEST_CREATOR_UID);
        retrievedNetworks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(randMac, retrievedNetworks.get(0).getRandomizedMacAddress().toString());
    }

    /**
     * Verify that add or update networks when WifiConfiguration has RepeaterEnabled set,
     * requires Network Settings Permission.
     */
    @Test
    public void testAddOrUpdateNetworkRepeaterEnabled() throws Exception {
        // Create an open network
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.setRepeaterEnabled(true);

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);

        NetworkUpdateResult result = addNetworkToWifiConfigManager(openNetwork);
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, result.getNetworkId());

        // Now try with the permission is granted
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        result = addNetworkToWifiConfigManager(openNetwork);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, result.getNetworkId());
    }

    /**
     * Verifies the modification of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testUpdateSingleOpenNetwork() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        reset(mWcmListener);

        // Now change BSSID for the network.
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);
        // Change the trusted bit.
        openNetwork.trusted = false;
        // change the oem paid bit.
        openNetwork.oemPaid = true;
        openNetwork.oemPrivate = true;
        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(openNetwork);

        // Now verify that the modification has been effective.
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        verify(mWcmListener).onNetworkUpdated(
                wifiConfigCaptor.capture(), wifiConfigCaptor.capture(), anyBoolean());
        WifiConfiguration newConfig = wifiConfigCaptor.getAllValues().get(1);
        WifiConfiguration oldConfig = wifiConfigCaptor.getAllValues().get(0);
        assertEquals(openNetwork.networkId, newConfig.networkId);
        assertFalse(newConfig.trusted);
        assertTrue(newConfig.oemPaid);
        assertTrue(newConfig.oemPrivate);
        assertEquals(TEST_BSSID, newConfig.BSSID);
        assertEquals(openNetwork.networkId, oldConfig.networkId);
        assertTrue(oldConfig.trusted);
        assertFalse(oldConfig.oemPaid);
        assertFalse(oldConfig.oemPrivate);
        assertNull(oldConfig.BSSID);
    }

    /**
     * Verifies the modification of a single network will remove its bssid from
     * the blocklist.
     */
    @Test
    public void testUpdateSingleOpenNetworkInBlockList() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        reset(mWcmListener);

        // mock the simplest bssid block list
        Map<String, String> mBssidStatusMap = new ArrayMap<>();
        doAnswer(new AnswerWithArguments() {
            public void answer(String ssid) {
                mBssidStatusMap.entrySet().removeIf(e -> e.getValue().equals(ssid));
            }
        }).when(mWifiBlocklistMonitor).clearBssidBlocklistForSsid(
                anyString());
        doAnswer(new AnswerWithArguments() {
            public int answer(String ssid) {
                return (int) mBssidStatusMap.entrySet().stream()
                        .filter(e -> e.getValue().equals(ssid)).count();
            }
        }).when(mWifiBlocklistMonitor).updateAndGetNumBlockedBssidsForSsid(
                anyString());
        // add bssid to the blocklist
        mBssidStatusMap.put(TEST_BSSID, openNetwork.SSID);
        mBssidStatusMap.put("aa:bb:cc:dd:ee:ff", openNetwork.SSID);

        // Now change BSSID for the network.
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);
        // Change the trusted bit.
        openNetwork.trusted = false;
        // change the oem paid bit.
        openNetwork.oemPaid = true;
        openNetwork.oemPrivate = true;
        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(openNetwork);

        // Now verify that the modification has been effective.
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        verify(mWcmListener).onNetworkUpdated(
                wifiConfigCaptor.capture(), wifiConfigCaptor.capture(), anyBoolean());
        WifiConfiguration newConfig = wifiConfigCaptor.getAllValues().get(1);
        WifiConfiguration oldConfig = wifiConfigCaptor.getAllValues().get(0);
        assertEquals(openNetwork.networkId, newConfig.networkId);
        assertFalse(newConfig.trusted);
        assertTrue(newConfig.oemPaid);
        assertTrue(newConfig.oemPrivate);
        assertEquals(TEST_BSSID, newConfig.BSSID);
        assertEquals(openNetwork.networkId, oldConfig.networkId);
        assertTrue(oldConfig.trusted);
        assertFalse(oldConfig.oemPaid);
        assertFalse(oldConfig.oemPrivate);
        assertNull(oldConfig.BSSID);

        assertEquals(0, mWifiBlocklistMonitor.updateAndGetNumBlockedBssidsForSsid(
                openNetwork.SSID));
    }

    @Test
    public void testOverrideWifiConfigModifyAllNetworks() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, openNetwork.networkId);
        reset(mWcmListener);

        // try to modify the network with another UID and assert failure
        WifiConfiguration configToUpdate = wifiConfigCaptor.getValue();
        configToUpdate.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(configToUpdate, TEST_OTHER_USER_UID);
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, result.getNetworkId());

        // give the override wifi config permission and verify success
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        result = mWifiConfigManager.addOrUpdateNetwork(configToUpdate, TEST_OTHER_USER_UID);
        assertEquals(configToUpdate.networkId, result.getNetworkId());
    }

    @Test
    public void testAddNetworkDoNotOverrideExistingNetwork() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        triggerStoreReadIfNeeded();
        // Verify add network success for the first time
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, mWifiConfigManager.addNetwork(
                config, TEST_CREATOR_UID).getNetworkId());
        // add the network again and verify it fails due to the same network already existing
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, mWifiConfigManager.addNetwork(
                config, TEST_CREATOR_UID).getNetworkId());
    }

    /**
     * Verifies that the organization owned device admin could modify other other fields in the
     * Wificonfiguration but not the macRandomizationSetting field for networks they do not own.
     */
    @Test
    public void testCannotUpdateMacRandomizationSettingWithoutPermissionDO() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        mockIsOrganizationOwnedDeviceAdmin(true);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        reset(mWcmListener);

        // Change BSSID for the network and verify success
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);
        NetworkUpdateResult networkUpdateResult = updateNetworkToWifiConfigManager(openNetwork);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, networkUpdateResult.getNetworkId());

        // Now change the macRandomizationSetting and verify failure
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        networkUpdateResult = updateNetworkToWifiConfigManager(openNetwork);
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, networkUpdateResult.getNetworkId());
    }

    /**
     * Verifies that the admin could set and modify the macRandomizationSetting field
     * for networks they own.
     */
    @Test
    public void testCanUpdateMacRandomizationSettingForAdminNetwork() {
        assumeTrue(SdkLevel.isAtLeastT());
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        mockIsOrganizationOwnedDeviceAdmin(true);
        mockIsAdmin(true);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        assertEquals(wifiConfigCaptor.getValue().macRandomizationSetting,
                WifiConfiguration.RANDOMIZATION_NONE);
        reset(mWcmListener);

        // Change macRandomizationSetting for the network and verify success
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        NetworkUpdateResult networkUpdateResult =
                mWifiConfigManager.addOrUpdateNetwork(openNetwork, TEST_CREATOR_UID);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, networkUpdateResult.getNetworkId());
        verify(mWcmListener).onNetworkUpdated(
                wifiConfigCaptor.capture(), wifiConfigCaptor.capture(), anyBoolean());
        WifiConfiguration newConfig = wifiConfigCaptor.getAllValues().get(1);
        assertEquals(WifiConfiguration.RANDOMIZATION_AUTO, newConfig.macRandomizationSetting);
        assertEquals(openNetwork.networkId, newConfig.networkId);
        reset(mWcmListener);
    }

    /**
     * Verifies that mac randomization settings could be modified by a caller with NETWORK_SETTINGS
     * permission.
     */
    @Test
    public void testCanUpdateMacRandomizationSettingWithNetworkSettingPermission() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);
        // Verify user action event is not logged when the network is added
        verify(mWifiMetrics, never()).logUserActionEvent(anyInt(), anyBoolean(), anyBoolean());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        reset(mWcmListener);

        // Now change the macRandomizationSetting and verify success
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        NetworkUpdateResult networkUpdateResult = updateNetworkToWifiConfigManager(openNetwork);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, networkUpdateResult.getNetworkId());
        verify(mWifiMetrics).logUserActionEvent(
                UserActionEvent.EVENT_CONFIGURE_MAC_RANDOMIZATION_OFF, openNetwork.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verifies that mac randomization settings could be modified by a caller with
     * NETWORK_SETUP_WIZARD permission.
     */
    @Test
    public void testCanUpdateMacRandomizationSettingWithSetupWizardPermission() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(true);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        reset(mWcmListener);

        // Now change the macRandomizationSetting and verify success
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        NetworkUpdateResult networkUpdateResult = updateNetworkToWifiConfigManager(openNetwork);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, networkUpdateResult.getNetworkId());

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verifies that mac randomization settings could be disabled by a caller when
     * demo mode is enabled.
     */
    @Test
    public void testCanAddConfigWithDisabledMacRandomizationWhenInDemoMode() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isDeviceInDemoMode(any())).thenReturn(true);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    /**
     * Verify that the mac randomization setting could be modified by the creator of a passpoint
     * network.
     */
    @Test
    public void testCanUpdateMacRandomizationSettingWithPasspointCreatorUid() throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        // Disable MAC randomization and verify this is added in successfully.
        passpointNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(passpointNetwork);

        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);
        // Ensure that configured network list is not empty.
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        // Ensure that this is not returned in the saved network list.
        assertTrue(mWifiConfigManager.getSavedNetworks(Process.WIFI_UID).isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(passpointNetwork.networkId, wifiConfigCaptor.getValue().networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verify that the mac randomization setting could be modified when added by wifi suggestions.
     */
    @Test
    public void testCanUpdateMacRandomizationSettingForSuggestions() throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        mockIsDeviceOwner(false);

        // Create 2 open networks. One is from suggestion and the other is not.
        WifiConfiguration openNetworkSuggestion = WifiConfigurationTestUtil.createOpenNetwork();
        openNetworkSuggestion.macRandomizationSetting =
                WifiConfiguration.RANDOMIZATION_NON_PERSISTENT;
        openNetworkSuggestion.fromWifiNetworkSuggestion = true;

        WifiConfiguration openNetworkSaved = WifiConfigurationTestUtil.createOpenNetwork();
        openNetworkSaved.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NON_PERSISTENT;
        openNetworkSaved.fromWifiNetworkSuggestion = false;

        // Add both networks into WifiConfigManager, and verify only is network from suggestions is
        // successfully added.
        addNetworkToWifiConfigManager(openNetworkSuggestion);
        addNetworkToWifiConfigManager(openNetworkSaved);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        assertEquals(openNetworkSuggestion.getProfileKey(),
                retrievedNetworks.get(0).getProfileKey());
    }

    /**
     * Verifies the addition of a single ephemeral network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and verifies that
     * the {@link WifiConfigManager#getSavedNetworks(int)} does not return this network.
     */
    @Test
    public void testAddSingleEphemeralNetwork() throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration ephemeralNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        ephemeralNetwork.ephemeral = true;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(ephemeralNetwork);

        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Ensure that this is not returned in the saved network list.
        assertTrue(mWifiConfigManager.getSavedNetworks(Process.WIFI_UID).isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(ephemeralNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    private void addSinglePasspointNetwork(boolean isHomeProviderNetwork) throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        passpointNetwork.isHomeProviderNetwork = isHomeProviderNetwork;

        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);
        // Ensure that configured network list is not empty.
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        // Ensure that this is not returned in the saved network list.
        assertTrue(mWifiConfigManager.getSavedNetworks(Process.WIFI_UID).isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(passpointNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        assertEquals(passpointNetwork.isHomeProviderNetwork,
                wifiConfigCaptor.getValue().isHomeProviderNetwork);
    }

    /**
     * Verifies the addition of a single home Passpoint network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and verifies that
     * the {@link WifiConfigManager#getSavedNetworks(int)} ()} does not return this network.
     */
    @Test
    public void testAddSingleHomePasspointNetwork() throws Exception {
        addSinglePasspointNetwork(true);
    }

    /**
     * Verifies the addition of a single roaming Passpoint network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and verifies that
     * the {@link WifiConfigManager#getSavedNetworks(int)} ()} does not return this network.
     */
    @Test
    public void testAddSingleRoamingPasspointNetwork() throws Exception {
        addSinglePasspointNetwork(false);
    }

    /**
     * Verifies the addition of 2 networks (1 normal and 1 ephemeral) using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and ensures that
     * the ephemeral network configuration is not persisted in config store.
     */
    @Test
    public void testAddMultipleNetworksAndEnsureEphemeralNetworkNotPersisted() {
        WifiConfiguration ephemeralNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        ephemeralNetwork.ephemeral = true;
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        assertTrue(addNetworkToWifiConfigManager(ephemeralNetwork).isSuccess());
        assertTrue(addNetworkToWifiConfigManager(openNetwork).isSuccess());
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        // The open network addition should trigger a store write.
        Pair<List<WifiConfiguration>, List<WifiConfiguration>> networkListStoreData =
                captureWriteNetworksListStoreData();
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.addAll(networkListStoreData.first);
        networkList.addAll(networkListStoreData.second);
        assertFalse(isNetworkInConfigStoreData(ephemeralNetwork, networkList));
        assertTrue(isNetworkInConfigStoreData(openNetwork, networkList));
    }

    /**
     * Verifies the addition of a single suggestion network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int, String, boolean)} and verifies
     * that the {@link WifiConfigManager#getSavedNetworks()} does not return this network.
     */
    @Test
    public void testAddSingleSuggestionNetwork() throws Exception {
        WifiConfiguration suggestionNetwork = WifiConfigurationTestUtil.createEapNetwork();
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        suggestionNetwork.ephemeral = true;
        suggestionNetwork.fromWifiNetworkSuggestion = true;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(suggestionNetwork);

        verifyAddSuggestionOrRequestNetworkToWifiConfigManager(suggestionNetwork);
        verify(mWifiKeyStore, never()).updateNetworkKeys(any(), any());

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Ensure that this is not returned in the saved network list.
        assertTrue(mWifiConfigManager.getSavedNetworks(Process.WIFI_UID).isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(suggestionNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        assertTrue(mWifiConfigManager
                .removeNetwork(suggestionNetwork.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        verify(mWifiKeyStore, never()).removeKeys(any(), eq(false));
    }

    /**
     * Verifies the addition of a single specifier network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int, String, boolean)} and verifies
     * that the {@link WifiConfigManager#getSavedNetworks()} does not return this network.
     */
    @Test
    public void testAddSingleSpecifierNetwork() throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration suggestionNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        suggestionNetwork.ephemeral = true;
        suggestionNetwork.fromWifiNetworkSpecifier = true;
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(suggestionNetwork);

        verifyAddSuggestionOrRequestNetworkToWifiConfigManager(suggestionNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Ensure that this is not returned in the saved network list.
        assertTrue(mWifiConfigManager.getSavedNetworks(Process.WIFI_UID).isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(suggestionNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    /**
     * Verifies that the modification of a single open network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} with a UID which
     * has no permission to modify the network fails.
     */
    @Test
    public void testUpdateSingleOpenNetworkFailedDueToPermissionDenied() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);

        // Now change BSSID of the network.
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);

        // Update the same configuration and ensure that the operation failed.
        NetworkUpdateResult result = updateNetworkToWifiConfigManager(openNetwork);
        assertTrue(result.getNetworkId() == WifiConfiguration.INVALID_NETWORK_ID);
    }

    /**
     * Test that configs containing vendor data can only be added/updated if the
     * caller has the proper permissions.
     */
    @Test
    public void testAddNetworkWithVendorDataPermissionCheck() {
        assumeTrue(SdkLevel.isAtLeastV());
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.setVendorData(Arrays.asList(mock(OuiKeyedData.class)));

        // Expect failure if the caller does not have the permission.
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        NetworkUpdateResult result = addNetworkToWifiConfigManager(configuration,
                configuration.creatorUid);
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, result.getNetworkId());

        // Expect success if the caller has the permission.
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        verifyAddNetworkToWifiConfigManager(configuration);
    }

    /**
     * Verifies that the modification of a single open network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} with the creator UID
     * should always succeed.
     */
    @Test
    public void testUpdateSingleOpenNetworkSuccessWithCreatorUID() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);

        // Now change BSSID of the network.
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);

        // Update the same configuration using the creator UID.
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(openNetwork, TEST_CREATOR_UID);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);

        // Now verify that the modification has been effective.
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verifies the addition of a single PSK network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and verifies that
     * {@link WifiConfigManager#getSavedNetworks()} masks the password.
     */
    @Test
    public void testAddSinglePskNetwork() {
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(pskNetwork);

        verifyAddNetworkToWifiConfigManager(pskNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        List<WifiConfiguration> retrievedSavedNetworks = mWifiConfigManager.getSavedNetworks(
                Process.WIFI_UID);
        assertEquals(retrievedSavedNetworks.size(), 1);
        assertEquals(retrievedSavedNetworks.get(0).getProfileKey(),
                pskNetwork.getProfileKey());
        assertPasswordsMaskedInWifiConfiguration(retrievedSavedNetworks.get(0));
    }

    /**
     * Verifies the addition of a single WEP network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and verifies that
     * {@link WifiConfigManager#getSavedNetworks()} masks the password.
     */
    @Test
    public void testAddSingleWepNetwork() {
        WifiConfiguration wepNetwork = WifiConfigurationTestUtil.createWepNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(wepNetwork);

        verifyAddNetworkToWifiConfigManager(wepNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        List<WifiConfiguration> retrievedSavedNetworks = mWifiConfigManager.getSavedNetworks(
                Process.WIFI_UID);
        assertEquals(retrievedSavedNetworks.size(), 1);
        assertEquals(retrievedSavedNetworks.get(0).getProfileKey(),
                wepNetwork.getProfileKey());
        assertPasswordsMaskedInWifiConfiguration(retrievedSavedNetworks.get(0));
    }

    /**
     * Verifies the modification of an IpConfiguration using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testUpdateIpConfiguration() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);

        // Now change BSSID of the network.
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);

        // Update the same configuration and ensure that the IP configuration change flags
        // are not set.
        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(openNetwork);

        // Configure mock DevicePolicyManager to give Profile Owner permission so that we can modify
        // proxy settings on a configuration
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), any())).thenReturn(true);

        // Change the IpConfiguration now and ensure that the IP configuration flags are set now.
        assertAndSetNetworkIpConfiguration(
                openNetwork,
                WifiConfigurationTestUtil.createStaticIpConfigurationWithStaticProxy());
        verifyUpdateNetworkToWifiConfigManagerWithIpChange(openNetwork);

        // Now verify that all the modifications have been effective.
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verifies the removal of a single network using
     * {@link WifiConfigManager#removeNetwork(int)}
     */
    @Test
    public void testRemoveSingleOpenNetwork() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        reset(mWcmListener);

        // Ensure that configured network list is not empty.
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        verifyRemoveNetworkFromWifiConfigManager(openNetwork);
        // Ensure that configured network list is empty now.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
        verify(mWcmListener).onNetworkRemoved(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    /**
     * Verifies the removal of an ephemeral network using
     * {@link WifiConfigManager#removeNetwork(int)}
     */
    @Test
    public void testRemoveSingleEphemeralNetwork() throws Exception {
        WifiConfiguration ephemeralNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        ephemeralNetwork.ephemeral = true;
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNetwork);
        // Ensure that configured network list is not empty.
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(ephemeralNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        verifyRemoveEphemeralNetworkFromWifiConfigManager(ephemeralNetwork);
        // Ensure that configured network list is empty now.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
        verify(mWcmListener).onNetworkRemoved(wifiConfigCaptor.capture());
        assertEquals(ephemeralNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    /**
     * Verifies the removal of a Passpoint network using
     * {@link WifiConfigManager#removeNetwork(int)}
     */
    @Test
    public void testRemoveSinglePasspointNetwork() throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();

        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);
        // Ensure that configured network list is not empty.
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());
        verify(mWcmListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(passpointNetwork.networkId, wifiConfigCaptor.getValue().networkId);

        verifyRemovePasspointNetworkFromWifiConfigManager(passpointNetwork);
        // Ensure that configured network list is empty now.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
        verify(mWcmListener).onNetworkRemoved(wifiConfigCaptor.capture());
        assertEquals(passpointNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    /**
     * Verify that a Passpoint network that's added by an app with {@link #TEST_CREATOR_UID} can
     * be removed by WiFi Service with {@link Process#WIFI_UID}.
     *
     * @throws Exception
     */
    @Test
    public void testRemovePasspointNetworkAddedByOther() throws Exception {
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();

        // Passpoint network is added using TEST_CREATOR_UID.
        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);
        // Ensure that configured network list is not empty.
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        assertTrue(mWifiConfigManager.removeNetwork(
                passpointNetwork.networkId, Process.WIFI_UID, null));

        // Verify keys are not being removed.
        verify(mWifiKeyStore, never()).removeKeys(any(WifiEnterpriseConfig.class), eq(false));
        verifyNetworkRemoveBroadcast();
        // Ensure that the write was not invoked for Passpoint network remove.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());

    }
    /**
     * Verifies the addition & update of multiple networks using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and the
     * removal of networks using
     * {@link WifiConfigManager#removeNetwork(int)}
     */
    @Test
    public void testAddUpdateRemoveMultipleNetworks() {
        List<WifiConfiguration> networks = new ArrayList<>();
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration wepNetwork = WifiConfigurationTestUtil.createWepNetwork();
        networks.add(openNetwork);
        networks.add(pskNetwork);
        networks.add(wepNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);
        verifyAddNetworkToWifiConfigManager(pskNetwork);
        verifyAddNetworkToWifiConfigManager(wepNetwork);

        // Now verify that all the additions has been effective.
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Modify all the 3 configurations and update it to WifiConfigManager.
        assertAndSetNetworkBSSID(openNetwork, TEST_BSSID);
        assertAndSetNetworkBSSID(pskNetwork, TEST_BSSID);
        assertAndSetNetworkIpConfiguration(
                wepNetwork,
                WifiConfigurationTestUtil.createStaticIpConfigurationWithPacProxy());

        // Configure mock DevicePolicyManager to give Profile Owner permission so that we can modify
        // proxy settings on a configuration
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), any())).thenReturn(true);

        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(openNetwork);
        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(pskNetwork);
        verifyUpdateNetworkToWifiConfigManagerWithIpChange(wepNetwork);
        // Now verify that all the modifications has been effective.
        retrievedNetworks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Now remove all 3 networks.
        verifyRemoveNetworkFromWifiConfigManager(openNetwork);
        verifyRemoveNetworkFromWifiConfigManager(pskNetwork);
        verifyRemoveNetworkFromWifiConfigManager(wepNetwork);

        // Ensure that configured network list is empty now.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
    }

    /**
     * Verifies the update of network status using
     * {@link WifiConfigManager#updateNetworkSelectionStatus(int, int)}.
     */
    @Test
    public void testNetworkSelectionStatus() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        int networkId = result.getNetworkId();
        // First set it to enabled.
        mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);

        // Now set it to temporarily disabled.
        mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                TEST_NETWORK_SELECTION_TEMP_DISABLE_REASON);
        verify(mWcmListener).onNetworkTemporarilyDisabled(wifiConfigCaptor.capture(),
                eq(TEST_NETWORK_SELECTION_TEMP_DISABLE_REASON));
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        verifyNetworkUpdateBroadcast();
        // Now set it to permanently disabled.
        mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                TEST_NETWORK_SELECTION_PERM_DISABLE_REASON);
        verify(mWcmListener).onNetworkPermanentlyDisabled(
                wifiConfigCaptor.capture(), eq(TEST_NETWORK_SELECTION_PERM_DISABLE_REASON));
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        verifyNetworkUpdateBroadcast();
        // Now set it back to enabled.
        mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        verify(mWcmListener, times(2)).onNetworkEnabled(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
        verifyNetworkUpdateBroadcast();
    }

    /**
     * Verify tryEnableNetwork sets NetworkSelectionStatus to NETWORK_SELECTION_ENABLED when
     * WifiBlocklistMonitor.shouldEnableNetwork returns true.
     */
    @Test
    public void testTryEnableNetwork() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);
        when(mWifiBlocklistMonitor.shouldEnableNetwork(any())).thenReturn(true);

        // first set the network to temporarily disabled
        verifyDisableNetwork(result, false);
        verifyNetworkUpdateBroadcast();

        assertTrue(mWifiConfigManager.tryEnableNetwork(result.getNetworkId()));
        assertEquals(NetworkSelectionStatus.NETWORK_SELECTION_ENABLED,
                mWifiConfigManager.getConfiguredNetworks().get(0)
                        .getNetworkSelectionStatus().getNetworkSelectionStatus());
        verifyNetworkUpdateBroadcast();
    }

    /**
     * Verify enableTemporaryDisabledNetworks successfully enable temporarily disabled networks.
     */
    @Test
    public void testEnableTemporaryDisabledNetworks() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);
        verifyDisableNetwork(result, false);
        assertEquals(true, mWifiConfigManager.getConfiguredNetworks().get(0)
                .getNetworkSelectionStatus().isNetworkTemporaryDisabled());

        mWifiConfigManager.enableTemporaryDisabledNetworks();
        verify(mWifiBlocklistMonitor).clearBssidBlocklist();
        assertEquals(true, mWifiConfigManager.getConfiguredNetworks().get(0)
                .getNetworkSelectionStatus().isNetworkEnabled());
    }

    /**
     * Verify that permanently disabled network do not get affected by
     * enableTemporaryDisabledNetworks
     */
    @Test
    public void testEnableTemporaryDisabledNetworks_PermanentDisableNetworksStillDisabled() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);
        verifyDisableNetwork(result, true);
        assertEquals(true, mWifiConfigManager.getConfiguredNetworks().get(0)
                .getNetworkSelectionStatus().isNetworkPermanentlyDisabled());

        mWifiConfigManager.enableTemporaryDisabledNetworks();
        verify(mWifiBlocklistMonitor).clearBssidBlocklist();
        assertEquals(true, mWifiConfigManager.getConfiguredNetworks().get(0)
                .getNetworkSelectionStatus().isNetworkPermanentlyDisabled());
    }

    private void verifyDisableNetwork(NetworkUpdateResult result, boolean permanentlyDisable) {
        // First set it to enabled.
        int networkId = result.getNetworkId();
        assertTrue(mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON));
        assertEquals(NetworkSelectionStatus.NETWORK_SELECTION_ENABLED,
                mWifiConfigManager.getConfiguredNetworks().get(0)
                        .getNetworkSelectionStatus().getNetworkSelectionStatus());

        if (permanentlyDisable) {
            assertTrue(mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                    TEST_NETWORK_SELECTION_PERM_DISABLE_REASON));
            assertEquals(NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED,
                    mWifiConfigManager.getConfiguredNetworks().get(0)
                            .getNetworkSelectionStatus().getNetworkSelectionStatus());
        } else {
            assertTrue(mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                    TEST_NETWORK_SELECTION_TEMP_DISABLE_REASON));
            assertEquals(NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED,
                    mWifiConfigManager.getConfiguredNetworks().get(0)
                            .getNetworkSelectionStatus().getNetworkSelectionStatus());
        }
    }

    /**
     * Verifies the enabling of network using
     * {@link WifiConfigManager#enableNetwork(int, boolean, int)} and
     * {@link WifiConfigManager#disableNetwork(int, int)}.
     */
    @Test
    public void testEnableDisableNetwork() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        verify(mWifiBlocklistMonitor).clearBssidBlocklistForSsid(openNetwork.SSID);
        assertTrue(mWifiConfigManager.enableNetwork(
                result.getNetworkId(), false, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        NetworkSelectionStatus retrievedStatus = retrievedNetwork.getNetworkSelectionStatus();
        assertTrue(retrievedStatus.isNetworkEnabled());
        verifyUpdateNetworkStatus(retrievedNetwork, WifiConfiguration.Status.ENABLED);
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
        verify(mWifiBlocklistMonitor, times(2)).clearBssidBlocklistForSsid(openNetwork.SSID);

        // Now set it disabled.
        assertTrue(mWifiConfigManager.disableNetwork(
                result.getNetworkId(), TEST_CREATOR_UID, TEST_CREATOR_NAME));
        retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        retrievedStatus = retrievedNetwork.getNetworkSelectionStatus();
        assertTrue(retrievedStatus.isNetworkPermanentlyDisabled());
        verifyUpdateNetworkStatus(retrievedNetwork, WifiConfiguration.Status.DISABLED);
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
        verify(mWifiMetrics, atLeast(2)).wifiConfigStored(anyInt());
    }

    /**
     * Verifies the enabling of network using
     * {@link WifiConfigManager#enableNetwork(int, boolean, int)} with a UID which
     * has no permission to modify the network fails..
     */
    @Test
    public void testEnableNetworkFailedDueToPermissionDenied() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);

        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        assertEquals(WifiConfiguration.INVALID_NETWORK_ID,
                mWifiConfigManager.getLastSelectedNetwork());

        // Now try to set it enable with |TEST_UPDATE_UID|, it should fail and the network
        // should remain disabled.
        assertFalse(mWifiConfigManager.enableNetwork(
                result.getNetworkId(), true, TEST_UPDATE_UID, TEST_UPDATE_NAME));
        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        NetworkSelectionStatus retrievedStatus = retrievedNetwork.getNetworkSelectionStatus();
        assertFalse(retrievedStatus.isNetworkEnabled());
        assertEquals(WifiConfiguration.Status.DISABLED, retrievedNetwork.status);

        // Set last selected network even if the app has no permission to enable it.
        assertEquals(result.getNetworkId(), mWifiConfigManager.getLastSelectedNetwork());
    }

    /**
     * Verifies the enabling of network using
     * {@link WifiConfigManager#disableNetwork(int, int)} with a UID which
     * has no permission to modify the network fails..
     */
    @Test
    public void testDisableNetworkFailedDueToPermissionDenied() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);

        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);
        assertTrue(mWifiConfigManager.enableNetwork(
                result.getNetworkId(), true, TEST_CREATOR_UID, TEST_CREATOR_NAME));

        assertEquals(result.getNetworkId(), mWifiConfigManager.getLastSelectedNetwork());

        // Now try to set it disabled with |TEST_UPDATE_UID|, it should fail and the network
        // should remain enabled.
        assertFalse(mWifiConfigManager.disableNetwork(
                result.getNetworkId(), TEST_UPDATE_UID, TEST_CREATOR_NAME));
        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        NetworkSelectionStatus retrievedStatus = retrievedNetwork.getNetworkSelectionStatus();
        assertTrue(retrievedStatus.isNetworkEnabled());
        assertEquals(WifiConfiguration.Status.ENABLED, retrievedNetwork.status);

        // Clear the last selected network even if the app has no permission to disable it.
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID,
                mWifiConfigManager.getLastSelectedNetwork());
    }

    /**
     * Verifies the allowance/disallowance of autojoin to a network using
     * {@link WifiConfigManager#allowAutojoin(int, boolean)}
     */
    @Test
    public void testAllowDisallowAutojoin() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        assertTrue(mWifiConfigManager.allowAutojoin(
                result.getNetworkId(), true));
        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertTrue(retrievedNetwork.allowAutojoin);
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);

        // Now set it disallow auto-join.
        assertTrue(mWifiConfigManager.allowAutojoin(
                result.getNetworkId(), false));
        retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertFalse(retrievedNetwork.allowAutojoin);
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
        verify(mWifiMetrics, atLeast(2)).wifiConfigStored(anyInt());
    }

    /**
     * Verifies the updation of network's connectUid using
     * {@link WifiConfigManager#updateLastConnectUid(int, int)}.
     */
    @Test
    public void testConnectNetworkUpdatesLastConnectUid() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        mWifiConfigManager
                .updateBeforeConnect(result.getNetworkId(), TEST_CREATOR_UID, TEST_PACKAGE_NAME,
                        true);

        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertEquals(TEST_CREATOR_UID, retrievedNetwork.lastConnectUid);
    }

    /**
     * Verifies that any configuration update attempt with an null config is gracefully
     * handled.
     * This invokes {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}.
     */
    @Test
    public void testAddOrUpdateNetworkWithNullConfig() {
        NetworkUpdateResult result = mWifiConfigManager.addOrUpdateNetwork(null, TEST_CREATOR_UID);
        assertFalse(result.isSuccess());
    }

    /**
     * Verifies that attempting to remove a network without any configs stored will return false.
     * This tests the case where we have not loaded any configs, potentially due to a pending store
     * read.
     * This invokes {@link WifiConfigManager#removeNetwork(int)}.
     */
    @Test
    public void testRemoveNetworkWithEmptyConfigStore() {
        int networkId = new Random().nextInt();
        assertFalse(mWifiConfigManager.removeNetwork(
                networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));
    }

    /**
     * Verifies that any configuration removal attempt with an invalid networkID is gracefully
     * handled.
     * This invokes {@link WifiConfigManager#removeNetwork(int)}.
     */
    @Test
    public void testRemoveNetworkWithInvalidNetworkId() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        verifyAddNetworkToWifiConfigManager(openNetwork);

        // Change the networkID to an invalid one.
        openNetwork.networkId++;
        assertFalse(mWifiConfigManager.removeNetwork(
                openNetwork.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));
    }

    /**
     * Verifies that any configuration update attempt with an invalid networkID is gracefully
     * handled.
     * This invokes {@link WifiConfigManager#enableNetwork(int, boolean, int)},
     * {@link WifiConfigManager#disableNetwork(int, int)},
     * {@link WifiConfigManager#updateNetworkSelectionStatus(int, int)} and
     * {@link WifiConfigManager#updateLastConnectUid(int, int)}.
     */
    @Test
    public void testChangeConfigurationWithInvalidNetworkId() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        assertFalse(mWifiConfigManager.enableNetwork(
                result.getNetworkId() + 1, false, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertFalse(mWifiConfigManager.disableNetwork(
                result.getNetworkId() + 1, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertFalse(mWifiConfigManager.updateNetworkSelectionStatus(
                result.getNetworkId() + 1, NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verifies multiple modification of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}.
     * This test is basically checking if the apps can reset some of the fields of the config after
     * addition. The fields being reset in this test are the |preSharedKey| and |wepKeys|.
     * 1. Create an open network initially.
     * 2. Modify the added network config to a WEP network config with all the 4 keys set.
     * 3. Modify the added network config to a WEP network config with only 1 key set.
     * 4. Modify the added network config to a PSK network config.
     */
    @Test
    public void testMultipleUpdatesSingleNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenOweNetwork();
        verifyAddNetworkToWifiConfigManager(network);

        // Now add |wepKeys| to the network. We don't need to update the |allowedKeyManagement|
        // fields for open to WEP conversion.
        String[] wepKeys =
                Arrays.copyOf(WifiConfigurationTestUtil.TEST_WEP_KEYS,
                        WifiConfigurationTestUtil.TEST_WEP_KEYS.length);
        int wepTxKeyIdx = WifiConfigurationTestUtil.TEST_WEP_TX_KEY_INDEX;
        assertAndSetNetworkWepKeysAndTxIndex(network, wepKeys, wepTxKeyIdx);

        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(network);
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network, mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));

        // Now shuffle the keys around
        String[] wepKeysNew = Arrays.copyOf(wepKeys, wepKeys.length);
        Collections.rotate(Arrays.asList(wepKeysNew), 2);
        wepTxKeyIdx = 0;
        assertAndSetNetworkWepKeysAndTxIndex(network, wepKeysNew, wepTxKeyIdx);

        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(network);
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network, mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));
    }

    /**
     * Verifies the modification of a WifiEnteriseConfig using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}.
     */
    @Test
    public void testUpdateWifiEnterpriseConfig() {
        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        verifyAddNetworkToWifiConfigManager(network);

        // Set the |password| field in WifiEnterpriseConfig and modify the config to PEAP/GTC.
        network.enterpriseConfig =
                WifiConfigurationTestUtil.createPEAPWifiEnterpriseConfigWithGTCPhase2();
        assertAndSetNetworkEnterprisePassword(network, "test");

        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(network);
        network.enterpriseConfig.setCaPath(WifiConfigurationTestUtil.TEST_CA_CERT_PATH);
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network, mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));

        // Reset the |password| field in WifiEnterpriseConfig and modify the config to TLS/None.
        network.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        network.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
        assertAndSetNetworkEnterprisePassword(network, "");

        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(network);
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network, mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));
    }

    /**
     * Verify that the protected WifiEnterpriseConfig fields are set correctly.
     */
    @Test
    public void testWifiEnterpriseConfigProtectedFields() {
        // Add an external config. Expect the internal config to have the default values.
        WifiConfiguration externalConfig = WifiConfigurationTestUtil.createEapNetwork();
        externalConfig.enterpriseConfig.setUserApproveNoCaCert(true);
        externalConfig.enterpriseConfig.setTofuDialogState(
                WifiEnterpriseConfig.TOFU_DIALOG_STATE_ACCEPTED);

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(externalConfig);
        WifiConfiguration internalConfig = mWifiConfigManager.getConfiguredNetwork(
                result.getNetworkId());
        assertFalse(internalConfig.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(
                WifiEnterpriseConfig.TOFU_DIALOG_STATE_UNSPECIFIED,
                internalConfig.enterpriseConfig.getTofuDialogState());

        // Update using an external config. Expect internal config to retain the default values.
        result = verifyUpdateNetworkToWifiConfigManager(externalConfig);
        internalConfig = mWifiConfigManager.getConfiguredNetwork(
                result.getNetworkId());
        assertFalse(internalConfig.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(
                WifiEnterpriseConfig.TOFU_DIALOG_STATE_UNSPECIFIED,
                internalConfig.enterpriseConfig.getTofuDialogState());

        // If the internal config's values are updated by the framework, merging
        // with an external config should not overwrite the internal values.
        mWifiConfigManager.setUserApproveNoCaCert(externalConfig.networkId, true);
        mWifiConfigManager.setTofuDialogApproved(externalConfig.networkId, true);
        externalConfig.enterpriseConfig.setUserApproveNoCaCert(false);
        externalConfig.enterpriseConfig.setTofuDialogApproved(false);

        result = verifyUpdateNetworkToWifiConfigManager(externalConfig);
        internalConfig = mWifiConfigManager.getConfiguredNetwork(
                result.getNetworkId());
        assertTrue(internalConfig.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(
                WifiEnterpriseConfig.TOFU_DIALOG_STATE_ACCEPTED,
                internalConfig.enterpriseConfig.getTofuDialogState());
    }

    /**
     * Verify that the TOFU connection state is set correctly when an Enterprise config is added or
     * updated.
     */
    @Test
    public void testEnterpriseConfigTofuStateMerge() {
        long featureSet = WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE;
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(featureSet);

        // If the configuration has never connected, the merged TOFU connection state
        // should be set based on the latest external configuration.
        WifiConfiguration config =
                prepareTofuEapConfig(
                        WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        WifiConfiguration internalConfig =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertEquals(
                WifiEnterpriseConfig.TOFU_STATE_NOT_ENABLED,
                internalConfig.enterpriseConfig.getTofuConnectionState());

        // Enabling TOFU in the external config should lead to the Enabled Pre-Connection state.
        config.enterpriseConfig.enableTrustOnFirstUse(true);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.PEAP);
        result = updateNetworkToWifiConfigManager(config);
        internalConfig = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertEquals(
                WifiEnterpriseConfig.TOFU_STATE_ENABLED_PRE_CONNECTION,
                internalConfig.enterpriseConfig.getTofuConnectionState());

        // Invalid post-connection values in the external config should be ignored,
        // since external configs should not be setting their own TOFU connection state.
        config.enterpriseConfig.setTofuConnectionState(
                WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING);
        result = updateNetworkToWifiConfigManager(config);
        internalConfig = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertEquals(
                WifiEnterpriseConfig.TOFU_STATE_ENABLED_PRE_CONNECTION,
                internalConfig.enterpriseConfig.getTofuConnectionState());

        // Post-connection states in the internal config should always persist.
        mWifiConfigManager.setTofuPostConnectionState(
                result.getNetworkId(), WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING);
        result = updateNetworkToWifiConfigManager(config);
        internalConfig = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertEquals(
                WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING,
                internalConfig.enterpriseConfig.getTofuConnectionState());
    }

    /**
     * Verifies the modification of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} by passing in nulls
     * in all the publicly exposed fields.
     */
    @Test
    public void testUpdateSingleNetworkWithNullValues() {
        WifiConfiguration network = WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork();
        verifyAddNetworkToWifiConfigManager(network);

        // Save a copy of the original network for comparison.
        WifiConfiguration originalNetwork = new WifiConfiguration(network);

        // Now set all the public fields to null and try updating the network.
        // except for allowedKeyManagement which is not allowed to be empty.
        network.allowedAuthAlgorithms.clear();
        network.allowedProtocols.clear();
        network.allowedPairwiseCiphers.clear();
        network.allowedGroupCiphers.clear();

        // Update the network.
        NetworkUpdateResult result = updateNetworkToWifiConfigManager(network);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertFalse(result.isNewNetwork());

        // Verify no changes to the original network configuration.
        verifyNetworkUpdateBroadcast();
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        verifyNetworkInConfigStoreData(originalNetwork);
        assertFalse(result.hasIpChanged());
        assertFalse(result.hasProxyChanged());

        // Copy over the updated debug params to the original network config before comparison.
        originalNetwork.lastUpdateUid = network.lastUpdateUid;
        originalNetwork.lastUpdateName = network.lastUpdateName;

        // Now verify that there was no change to the network configurations.
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                originalNetwork,
                mWifiConfigManager.getConfiguredNetworkWithPassword(originalNetwork.networkId));

        // Save a copy of the original network for comparison.
        network = new WifiConfiguration(originalNetwork);

        // Now set all the public fields to null including enterprise config and try updating the
        // network.
        network.allowedAuthAlgorithms.clear();
        network.allowedProtocols.clear();
        network.allowedKeyManagement.clear();
        network.allowedPairwiseCiphers.clear();
        network.allowedGroupCiphers.clear();
        network.enterpriseConfig = null;

        // Update the network.
        result = updateNetworkToWifiConfigManager(network);
        assertTrue(result.getNetworkId() == WifiConfiguration.INVALID_NETWORK_ID);
    }

    /**
     * Verifies the addition of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} by passing in null
     * in IpConfiguraion fails.
     */
    @Test
    public void testAddSingleNetworkWithNullIpConfigurationFails() {
        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        network.setIpConfiguration(null);
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(network, TEST_CREATOR_UID);
        assertFalse(result.isSuccess());
    }

    /**
     * Verifies that the modification of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} does not modify
     * existing configuration if there is a failure.
     */
    @Test
    public void testUpdateSingleNetworkFailureDoesNotModifyOriginal() {
        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        network.enterpriseConfig =
                WifiConfigurationTestUtil.createPEAPWifiEnterpriseConfigWithGTCPhase2();
        verifyAddNetworkToWifiConfigManager(network);

        // Save a copy of the original network for comparison.
        WifiConfiguration originalNetwork = new WifiConfiguration(network);

        // Now modify the network's EAP method.
        network.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithNonePhase2();

        // Fail this update because of cert installation failure.
        when(mWifiKeyStore
                .updateNetworkKeys(any(WifiConfiguration.class), any(WifiConfiguration.class)))
                .thenReturn(false);
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(network, TEST_UPDATE_UID);
        assertTrue(result.getNetworkId() == WifiConfiguration.INVALID_NETWORK_ID);

        // Now verify that there was no change to the network configurations.
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                originalNetwork,
                mWifiConfigManager.getConfiguredNetworkWithPassword(originalNetwork.networkId));
    }

    /**
     * Verifies the matching of networks with different encryption types with the
     * corresponding scan detail using
     * {@link WifiConfigManager#getSavedNetworkForScanDetailAndCache(ScanDetail)}.
     * The test also verifies that the provided scan detail was cached,
     */
    @Test
    public void testMatchScanDetailToNetworksAndCache() {
        // Create networks of different types and ensure that they're all matched using
        // the corresponding ScanDetail correctly.
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createOpenNetwork());
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createWepNetwork());
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createPskNetwork());
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createEapNetwork());
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createSaeNetwork());
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createOweNetwork());
        verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
                WifiConfigurationTestUtil.createEapSuiteBNetwork());
    }

    /**
     * Verifies that scan details with wrong SSID/authentication types are not matched using
     * {@link WifiConfigManager#getSavedNetworkForScanDetailAndCache(ScanDetail)}
     * to the added networks.
     */
    @Test
    public void testNoMatchScanDetailToNetwork() {
        // First create networks of different types.
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration wepNetwork = WifiConfigurationTestUtil.createWepNetwork();
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration eapNetwork = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration saeNetwork = WifiConfigurationTestUtil.createSaeNetwork();
        WifiConfiguration oweNetwork = WifiConfigurationTestUtil.createOweNetwork();
        WifiConfiguration eapSuiteBNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork();

        // Now add them to WifiConfigManager.
        verifyAddNetworkToWifiConfigManager(openNetwork);
        verifyAddNetworkToWifiConfigManager(wepNetwork);
        verifyAddNetworkToWifiConfigManager(pskNetwork);
        verifyAddNetworkToWifiConfigManager(eapNetwork);
        verifyAddNetworkToWifiConfigManager(saeNetwork);
        verifyAddNetworkToWifiConfigManager(oweNetwork);
        verifyAddNetworkToWifiConfigManager(eapSuiteBNetwork);

        // Now create fake scan detail corresponding to the networks.
        ScanDetail openNetworkScanDetail = createScanDetailForNetwork(openNetwork);
        ScanDetail wepNetworkScanDetail = createScanDetailForNetwork(wepNetwork);
        ScanDetail pskNetworkScanDetail = createScanDetailForNetwork(pskNetwork);
        ScanDetail eapNetworkScanDetail = createScanDetailForNetwork(eapNetwork);
        ScanDetail saeNetworkScanDetail = createScanDetailForNetwork(saeNetwork);
        ScanDetail oweNetworkScanDetail = createScanDetailForNetwork(oweNetwork);
        ScanDetail eapSuiteBNetworkScanDetail = createScanDetailForNetwork(eapSuiteBNetwork);

        // Now mix and match parameters from different scan details.
        openNetworkScanDetail.getScanResult().setWifiSsid(
                wepNetworkScanDetail.getScanResult().getWifiSsid());
        wepNetworkScanDetail.getScanResult().capabilities =
                pskNetworkScanDetail.getScanResult().capabilities;
        pskNetworkScanDetail.getScanResult().capabilities =
                eapNetworkScanDetail.getScanResult().capabilities;
        eapNetworkScanDetail.getScanResult().capabilities =
                saeNetworkScanDetail.getScanResult().capabilities;
        saeNetworkScanDetail.getScanResult().capabilities =
                oweNetworkScanDetail.getScanResult().capabilities;
        oweNetworkScanDetail.getScanResult().capabilities =
                eapSuiteBNetworkScanDetail.getScanResult().capabilities;
        eapSuiteBNetworkScanDetail.getScanResult().capabilities =
                openNetworkScanDetail.getScanResult().capabilities;


        // Try to lookup a saved network using the modified scan details. All of these should fail.
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                openNetworkScanDetail));
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                wepNetworkScanDetail));
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                pskNetworkScanDetail));
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                eapNetworkScanDetail));
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                saeNetworkScanDetail));
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                oweNetworkScanDetail));
        assertNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                eapSuiteBNetworkScanDetail));

        // All the cache's should be empty as well.
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(openNetwork.networkId));
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(wepNetwork.networkId));
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(pskNetwork.networkId));
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(eapNetwork.networkId));
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(saeNetwork.networkId));
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(oweNetwork.networkId));
        assertNull(mWifiConfigManager.getScanDetailCacheForNetwork(eapSuiteBNetwork.networkId));
    }

    /**
     * Verifies that ScanDetail added for a network is cached correctly with network ID
     */
    @Test
    public void testUpdateScanDetailForNetwork() {
        // First add the provided network.
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);

        // Now create a fake scan detail corresponding to the network.
        ScanDetail scanDetail = createScanDetailForNetwork(testNetwork);
        ScanResult scanResult = scanDetail.getScanResult();

        mWifiConfigManager.updateScanDetailForNetwork(result.getNetworkId(), scanDetail);

        // Now retrieve the scan detail cache and ensure that the new scan detail is in cache.
        ScanDetailCache retrievedScanDetailCache =
                mWifiConfigManager.getScanDetailCacheForNetwork(result.getNetworkId());
        assertEquals(1, retrievedScanDetailCache.size());
        ScanResult retrievedScanResult = retrievedScanDetailCache.getScanResult(scanResult.BSSID);

        ScanTestUtil.assertScanResultEquals(scanResult, retrievedScanResult);
    }

    /**
     * Verifies that ScanDetail added for a network is cached correctly without network ID
     */
    @Test
    public void testUpdateScanDetailCacheFromScanDetail() {
        // First add the provided network.
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);

        // Now create a fake scan detail corresponding to the network.
        ScanDetail scanDetail = createScanDetailForNetwork(testNetwork);
        ScanResult scanResult = scanDetail.getScanResult();

        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);

        // Now retrieve the scan detail cache and ensure that the new scan detail is in cache.
        ScanDetailCache retrievedScanDetailCache =
                mWifiConfigManager.getScanDetailCacheForNetwork(result.getNetworkId());
        assertEquals(1, retrievedScanDetailCache.size());
        ScanResult retrievedScanResult = retrievedScanDetailCache.getScanResult(scanResult.BSSID);

        ScanTestUtil.assertScanResultEquals(scanResult, retrievedScanResult);
    }

    /**
     * Verifies that scan detail cache is trimmed down when the size of the cache for a network
     * exceeds {@link WifiConfigManager#SCAN_CACHE_ENTRIES_MAX_SIZE}.
     */
    @Test
    public void testScanDetailCacheTrimForNetwork() {
        // Add a single network.
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkToWifiConfigManager(openNetwork);

        ScanDetailCache scanDetailCache;
        String testBssidPrefix = "00:a5:b8:c9:45:";

        // Modify |BSSID| field in the scan result and add copies of scan detail
        // |SCAN_CACHE_ENTRIES_MAX_SIZE| times.
        int scanDetailNum = 1;
        for (; scanDetailNum <= WifiConfigManager.SCAN_CACHE_ENTRIES_MAX_SIZE; scanDetailNum++) {
            // Create fake scan detail caches with different BSSID for the network.
            ScanDetail scanDetail =
                    createScanDetailForNetwork(
                            openNetwork, String.format("%s%02x", testBssidPrefix, scanDetailNum));
            mWifiConfigManager.updateScanDetailForNetwork(openNetwork.networkId, scanDetail);

            // The size of scan detail cache should keep growing until it hits
            // |SCAN_CACHE_ENTRIES_MAX_SIZE|.
            scanDetailCache =
                    mWifiConfigManager.getScanDetailCacheForNetwork(openNetwork.networkId);
            assertEquals(scanDetailNum, scanDetailCache.size());
        }

        // Now add the |SCAN_CACHE_ENTRIES_MAX_SIZE + 1| entry. This should trigger the trim.
        ScanDetail scanDetail =
                createScanDetailForNetwork(
                        openNetwork, String.format("%s%02x", testBssidPrefix, scanDetailNum));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(scanDetail));

        // Retrieve the scan detail cache and ensure that the size was trimmed down to
        // |SCAN_CACHE_ENTRIES_TRIM_SIZE + 1|. The "+1" is to account for the new entry that
        // was added after the trim.
        scanDetailCache = mWifiConfigManager.getScanDetailCacheForNetwork(openNetwork.networkId);
        assertEquals(WifiConfigManager.SCAN_CACHE_ENTRIES_TRIM_SIZE + 1, scanDetailCache.size());
    }

    /**
     * Verifies that hasEverConnected is false for a newly added network.
     */
    @Test
    public void testAddNetworkHasEverConnectedFalse() {
        verifyAddNetworkHasEverConnectedFalse(WifiConfigurationTestUtil.createOpenNetwork());
    }

    /**
     * Verifies that hasEverConnected is false for a newly added network even when new config has
     * mistakenly set HasEverConnected to true.
     */
    @Test
    public void testAddNetworkOverridesHasEverConnectedWhenTrueInNewConfig() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.getNetworkSelectionStatus().setHasEverConnected(true);
        verifyAddNetworkHasEverConnectedFalse(openNetwork);
    }

    /**
     * Verify that the |HasEverConnected| is set when
     * {@link WifiConfigManager#updateNetworkAfterConnect(int, boolean, boolean, int)} is invoked.
     */
    @Test
    public void testUpdateConfigAfterConnectHasEverConnectedTrue() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkHasEverConnectedFalse(openNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(openNetwork.networkId);
    }

    /**
     * Verifies that hasEverConnected is cleared when a network config |preSharedKey| is updated.
     */
    @Test
    public void testUpdatePreSharedKeyClearsHasEverConnected() {
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkHasEverConnectedFalse(pskNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(pskNetwork.networkId);

        // Now update the same network with a different psk.
        String newPsk = "\"newpassword\"";
        assertFalse(pskNetwork.preSharedKey.equals(newPsk));
        pskNetwork.preSharedKey = newPsk;
        verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(pskNetwork);
    }

    /**
     * Verifies that hasEverConnected is cleared when a network config |wepKeys| is updated.
     */
    @Test
    public void testUpdateWepKeysClearsHasEverConnected() {
        WifiConfiguration wepNetwork = WifiConfigurationTestUtil.createWepNetwork();
        verifyAddNetworkHasEverConnectedFalse(wepNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(wepNetwork.networkId);

        // Now update the same network with a different wep key.
        assertFalse(wepNetwork.wepKeys[0].equals("\"pswrd\""));
        wepNetwork.wepKeys = new String[] {"\"pswrd\"", null, null, null};
        wepNetwork.wepTxKeyIndex = 0;
        verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(wepNetwork);
    }

    /**
     * Verifies that hasEverConnected is cleared when a network config |wepTxKeyIndex| is updated.
     */
    @Test
    public void testUpdateWepTxKeyClearsHasEverConnected() {
        WifiConfiguration wepNetwork = WifiConfigurationTestUtil.createWepNetwork();
        verifyAddNetworkHasEverConnectedFalse(wepNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(wepNetwork.networkId);

        // Now update the same network with a different wep.
        assertFalse(wepNetwork.wepTxKeyIndex == 3);
        wepNetwork.wepTxKeyIndex = 3;
        verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(wepNetwork);
    }

    /**
     * Verifies that hasEverConnected is cleared when the security type of a network config
     * is updated.
     */
    @Test
    public void testUpdateSecurityTypeClearsHasEverConnected() {
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkHasEverConnectedFalse(pskNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(pskNetwork.networkId);

        pskNetwork.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(pskNetwork);
    }

    /**
     * Verifies that hasEverConnected is cleared when a network config |hiddenSSID| is
     * updated.
     */
    @Test
    public void testUpdateHiddenSSIDClearsHasEverConnected() {
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkHasEverConnectedFalse(pskNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(pskNetwork.networkId);

        assertFalse(pskNetwork.hiddenSSID);
        pskNetwork.hiddenSSID = true;
        verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(pskNetwork);
    }

    /**
     * Verifies that hasEverConnected is cleared when a network config |enterpriseConfig| is
     * updated.
     */
    @Test
    public void testUpdateEnterpriseConfigClearsHasEverConnected() {
        WifiConfiguration eapNetwork = WifiConfigurationTestUtil.createEapNetwork();
        eapNetwork.enterpriseConfig =
                WifiConfigurationTestUtil.createPEAPWifiEnterpriseConfigWithGTCPhase2();
        verifyAddNetworkHasEverConnectedFalse(eapNetwork);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(eapNetwork.networkId);

        assertFalse(eapNetwork.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.TLS);
        eapNetwork.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(eapNetwork);
    }

    /**
     * Verifies that if the app sends back the masked passwords in an update, we ignore it.
     */
    @Test
    public void testUpdateIgnoresMaskedPasswords() {
        WifiConfiguration someRandomNetworkWithAllMaskedFields =
                WifiConfigurationTestUtil.createPskNetwork();
        someRandomNetworkWithAllMaskedFields.wepKeys = WifiConfigurationTestUtil.TEST_WEP_KEYS;
        someRandomNetworkWithAllMaskedFields.preSharedKey = WifiConfigurationTestUtil.TEST_PSK;
        someRandomNetworkWithAllMaskedFields.enterpriseConfig.setPassword(
                WifiConfigurationTestUtil.TEST_EAP_PASSWORD);

        NetworkUpdateResult result =
                verifyAddNetworkToWifiConfigManager(someRandomNetworkWithAllMaskedFields);

        // All of these passwords must be masked in this retrieved network config.
        WifiConfiguration retrievedNetworkWithMaskedPassword =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertPasswordsMaskedInWifiConfiguration(retrievedNetworkWithMaskedPassword);
        // Ensure that the passwords are present internally.
        WifiConfiguration retrievedNetworkWithPassword =
                mWifiConfigManager.getConfiguredNetworkWithPassword(result.getNetworkId());
        assertEquals(someRandomNetworkWithAllMaskedFields.preSharedKey,
                retrievedNetworkWithPassword.preSharedKey);
        assertEquals(someRandomNetworkWithAllMaskedFields.wepKeys,
                retrievedNetworkWithPassword.wepKeys);
        assertEquals(someRandomNetworkWithAllMaskedFields.enterpriseConfig.getPassword(),
                retrievedNetworkWithPassword.enterpriseConfig.getPassword());

        // Now update the same network config using the masked config.
        verifyUpdateNetworkToWifiConfigManager(retrievedNetworkWithMaskedPassword);

        // Retrieve the network config with password and ensure that they have not been overwritten
        // with *.
        retrievedNetworkWithPassword =
                mWifiConfigManager.getConfiguredNetworkWithPassword(result.getNetworkId());
        assertEquals(someRandomNetworkWithAllMaskedFields.preSharedKey,
                retrievedNetworkWithPassword.preSharedKey);
        assertEquals(someRandomNetworkWithAllMaskedFields.wepKeys,
                retrievedNetworkWithPassword.wepKeys);
        assertEquals(someRandomNetworkWithAllMaskedFields.enterpriseConfig.getPassword(),
                retrievedNetworkWithPassword.enterpriseConfig.getPassword());
    }

    /**
     * Verifies that randomized MAC address is masked out when we return
     * external configs except when explicitly asked for MAC address.
     */
    @Test
    public void testGetConfiguredNetworksMasksRandomizedMac() {
        int targetUidConfigNonCreator = TEST_CREATOR_UID + 100;

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(config);

        // Verify that randomized MAC address is masked when obtaining saved networks from
        // invalid UID
        List<WifiConfiguration> configs = mWifiConfigManager.getSavedNetworks(Process.INVALID_UID);
        assertEquals(1, configs.size());
        assertRandomizedMacAddressMaskedInWifiConfiguration(configs.get(0));

        // Verify that randomized MAC address is unmasked when obtaining saved networks from
        // system UID
        configs = mWifiConfigManager.getSavedNetworks(Process.WIFI_UID);
        assertEquals(1, configs.size());
        String macAddress = configs.get(0).getRandomizedMacAddress().toString();
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, macAddress);

        // Verify that randomized MAC address is masked when obtaining saved networks from
        // (carrier app) non-creator of the config
        configs = mWifiConfigManager.getSavedNetworks(targetUidConfigNonCreator);
        assertEquals(1, configs.size());
        assertRandomizedMacAddressMaskedInWifiConfiguration(configs.get(0));

        // Verify that randomized MAC address is unmasked when obtaining saved networks from
        // (carrier app) creator of the config
        configs = mWifiConfigManager.getSavedNetworks(TEST_CREATOR_UID);
        assertEquals(1, configs.size());
        assertEquals(macAddress, configs.get(0).getRandomizedMacAddress().toString());

        // Verify that randomized MAC address is unmasked when getting list of privileged (with
        // password) configurations
        WifiConfiguration configWithRandomizedMac = mWifiConfigManager
                .getConfiguredNetworkWithPassword(result.getNetworkId());
        assertEquals(macAddress, configs.get(0).getRandomizedMacAddress().toString());

        // Ensure that the MAC address is present when asked for config with MAC address.
        configWithRandomizedMac = mWifiConfigManager
                .getConfiguredNetworkWithoutMasking(result.getNetworkId());
        assertEquals(macAddress, configs.get(0).getRandomizedMacAddress().toString());
    }

    /**
     * Verify that the non-persistent randomization allowlist works for Passpoint (by checking FQDN)
     */
    @Test
    public void testShouldUseNonPersistentRandomizationPasspoint() {
        WifiConfiguration c = WifiConfigurationTestUtil.createPasspointNetwork();
        // Adds SSID to the allowlist.
        Set<String> ssidList = new HashSet<>();
        ssidList.add(c.SSID);
        when(mDeviceConfigFacade.getNonPersistentMacRandomizationSsidAllowlist())
                .thenReturn(ssidList);

        // Verify that if for passpoint networks we don't check for the SSID to be in the allowlist
        assertFalse(mWifiConfigManager.shouldUseNonPersistentRandomization(c));

        // instead we check for the FQDN
        ssidList.clear();
        ssidList.add(c.FQDN);
        assertTrue(mWifiConfigManager.shouldUseNonPersistentRandomization(c));
    }

    /**
     * Verify that macRandomizationSetting == RANDOMIZATION_NON_PERSISTENT enables
     * non-persistent MAC randomization.
     */
    @Test
    public void testShouldUseNonPersistentRandomization_randomizationNonPersistent() {
        WifiConfiguration c = WifiConfigurationTestUtil.createPasspointNetwork();
        c.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NON_PERSISTENT;
        assertTrue(mWifiConfigManager.shouldUseNonPersistentRandomization(c));
    }

    /**
     * Verify that macRandomizationSetting = RANDOMIZATION_PERSISTENT disables non-persistent
     * randomization.
     */
    @Test
    public void testShouldUseNonPersistentRandomization_randomizationPersistent() {
        WifiConfiguration c = WifiConfigurationTestUtil.createPasspointNetwork();
        c.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_PERSISTENT;
        assertFalse(mWifiConfigManager.shouldUseNonPersistentRandomization(c));
    }

    /**
     * Verify that shouldUseNonPersistentRandomization returns true for an open network that has
     * been connected before and never had captive portal detected.
     */
    @Test
    public void testShouldUseNonPersistentRandomization_openNetworkNoCaptivePortal() {
        mResources.setBoolean(R.bool.config_wifiAllowNonPersistentMacRandomizationOnOpenSsids,
                true);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        // verify non-persistent randomization is not enabled because the config has never been
        // connected.
        assertTrue(config.getNetworkSelectionStatus().hasNeverDetectedCaptivePortal());
        assertFalse(mWifiConfigManager.shouldUseNonPersistentRandomization(config));

        config.getNetworkSelectionStatus().setHasEverConnected(true);
        assertTrue(mWifiConfigManager.shouldUseNonPersistentRandomization(config));
    }

    /**
     * Verify that when DeviceConfigFacade#isNonPersistentMacRandomizationEnabled returns true, any
     * networks that already use randomized MAC use non-persistent MAC randomization instead.
     */
    @Test
    public void testNonPersistentMacRandomizationIsEnabledGlobally() {
        when(mFrameworkFacade.getIntegerSetting(eq(mContext),
                eq(WifiConfigManager.NON_PERSISTENT_MAC_RANDOMIZATION_FEATURE_FORCE_ENABLE_FLAG),
                anyInt())).thenReturn(1);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(mWifiConfigManager.shouldUseNonPersistentRandomization(config));

        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        assertFalse(mWifiConfigManager.shouldUseNonPersistentRandomization(config));
    }

    /**
     * Verify that when non-persistent MAC randomization is enabled the MAC address changes after 24
     * hours of the first connection this MAC address is used.
     */
    private void testNonPersistentMacRandomizationEvery24Hours(boolean isForSecondaryDbs) {
        setUpWifiConfigurationForNonPersistentRandomization();
        WifiConfiguration config = getFirstInternalWifiConfiguration();

        assertEquals(TEST_WALLCLOCK_CREATION_TIME_MILLIS, config.randomizedMacLastModifiedTimeMs);
        MacAddress firstMac = config.getRandomizedMacAddress();
        for (int i = 0; i < 24; i++) {
            when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                    + i * 60 * 60 * 1000);
            mWifiConfigManager.updateNetworkAfterDisconnect(config.networkId);
            assertEquals("Randomized MAC should be the same after " + i + " hours",
                    isForSecondaryDbs ? MacAddressUtil.nextMacAddress(firstMac) : firstMac,
                    mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config,
                            isForSecondaryDbs));
            config = getFirstInternalWifiConfiguration();
        }

        // verify that after 24 hours the randomized MAC address changes.
        long timeAfter24Hours = TEST_WALLCLOCK_CREATION_TIME_MILLIS + 24 * 60 * 60 * 1000;
        when(mClock.getWallClockMillis()).thenReturn(timeAfter24Hours);
        assertNotEquals(firstMac,
                mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config, isForSecondaryDbs));
        config = getFirstInternalWifiConfiguration();
        assertEquals(timeAfter24Hours, config.randomizedMacLastModifiedTimeMs);
    }

    /* For Non Dbs network configuration */
    @Test
    public void testNonPersistentMacRandomizationEvery24HoursNonDbs() {
        testNonPersistentMacRandomizationEvery24Hours(false);
    }

    /* For Dbs network configuration */
    @Test
    public void testNonPersistentMacRandomizationEvery24HoursDbs() {
        testNonPersistentMacRandomizationEvery24Hours(true);
    }

    /**
     * Verifies that getRandomizedMacAndUpdateIfNeeded updates the randomized MAC address and
     * |randomizedMacExpirationTimeMs| correctly.
     *
     * Then verify that getRandomizedMacAndUpdateIfNeeded sets the randomized MAC back to the
     * persistent MAC.
     */
    private void testRandomizedMacUpdateAndRestore(boolean isForSecondaryDbs) {
        setUpWifiConfigurationForNonPersistentRandomization();
        // get the non-persistent randomized MAC address.
        WifiConfiguration config = getFirstInternalWifiConfiguration();
        final MacAddress randMac = config.getRandomizedMacAddress();
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, randMac.toString());
        assertEquals(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                        + WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS,
                config.randomizedMacExpirationTimeMs);

        // verify the new randomized mac should be different from the original mac.
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                + WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS + 1);
        MacAddress randMac2 = mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config,
                isForSecondaryDbs);

        // verify internal WifiConfiguration has MacAddress updated correctly by comparing the
        // MAC address from internal WifiConfiguration with the value returned by API.
        config = getFirstInternalWifiConfiguration();
        assertEquals(randMac2,
                isForSecondaryDbs ? MacAddressUtil.nextMacAddress(config.getRandomizedMacAddress())
                        : config.getRandomizedMacAddress());
        assertNotEquals(randMac, randMac2);

        // Now disable non-persistent randomization and verify the randomized MAC is changed back to
        // the persistent MAC.
        Set<String> blocklist = new HashSet<>();
        blocklist.add(config.SSID);
        when(mDeviceConfigFacade.getNonPersistentMacRandomizationSsidBlocklist())
                .thenReturn(blocklist);
        MacAddress persistentMac = mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config,
                isForSecondaryDbs);

        // verify internal WifiConfiguration has MacAddress updated correctly by comparing the
        // MAC address from internal WifiConfiguration with the value returned by API.
        config = getFirstInternalWifiConfiguration();
        assertEquals(persistentMac,
                isForSecondaryDbs ? MacAddressUtil.nextMacAddress(config.getRandomizedMacAddress())
                        : config.getRandomizedMacAddress());
        assertNotEquals(persistentMac, randMac);
        assertNotEquals(persistentMac, randMac2);
    }

    /* For Non Dbs network configuration */
    @Test
    public void testRandomizedMacUpdateAndRestoreNonDbs() {
        testRandomizedMacUpdateAndRestore(false);
    }
    /* For Dbs network configuration */
    @Test
    public void testRandomizedMacUpdateAndRestoreDbs() {
        testRandomizedMacUpdateAndRestore(true);
    }

    @Test
    public void testNonPersistentRandomizationDbsMacAddress() {
        setUpWifiConfigurationForNonPersistentRandomization();
        WifiConfiguration config = getFirstInternalWifiConfiguration();
        MacAddress randomMac = config.getRandomizedMacAddress();
        MacAddress newMac = mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config,
                true);
        assertTrue(newMac.equals(MacAddressUtil.nextMacAddress(randomMac)));
    }

    /**
     * Verifies that the updateRandomizedMacExpireTime method correctly updates the
     * |randomizedMacExpirationTimeMs| field of the given WifiConfiguration.
     */
    @Test
    public void testUpdateRandomizedMacExpireTime() {
        setUpWifiConfigurationForNonPersistentRandomization();
        WifiConfiguration config = getFirstInternalWifiConfiguration();
        when(mClock.getWallClockMillis()).thenReturn(0L);

        // verify that |NON_PERSISTENT_MAC_REFRESH_MS_MIN| is honored as the lower bound.
        long dhcpLeaseTimeInSeconds =
                (WifiConfigManager.NON_PERSISTENT_MAC_REFRESH_MS_MIN / 1000) - 1;
        mWifiConfigManager.updateRandomizedMacExpireTime(config, dhcpLeaseTimeInSeconds);
        config = getFirstInternalWifiConfiguration();
        assertEquals(WifiConfigManager.NON_PERSISTENT_MAC_REFRESH_MS_MIN,
                config.randomizedMacExpirationTimeMs);

        // verify that |NON_PERSISTENT_MAC_REFRESH_MS_MAX| is honored as the upper bound.
        dhcpLeaseTimeInSeconds = (WifiConfigManager.NON_PERSISTENT_MAC_REFRESH_MS_MAX / 1000) + 1;
        mWifiConfigManager.updateRandomizedMacExpireTime(config, dhcpLeaseTimeInSeconds);
        config = getFirstInternalWifiConfiguration();
        assertEquals(WifiConfigManager.NON_PERSISTENT_MAC_REFRESH_MS_MAX,
                config.randomizedMacExpirationTimeMs);

        // finally verify setting a valid value between the upper and lower bounds.
        dhcpLeaseTimeInSeconds = (WifiConfigManager.NON_PERSISTENT_MAC_REFRESH_MS_MIN / 1000) + 5;
        mWifiConfigManager.updateRandomizedMacExpireTime(config, dhcpLeaseTimeInSeconds);
        config = getFirstInternalWifiConfiguration();
        assertEquals(WifiConfigManager.NON_PERSISTENT_MAC_REFRESH_MS_MIN + 5000,
                config.randomizedMacExpirationTimeMs);
    }

    /**
     * Verifies that the expiration time of the currently used non-persistent MAC is set to the
     * maximum of some predefined time and the remaining DHCP lease duration at disconnect.
     */
    @Test
    public void testRandomizedMacExpirationTimeUpdatedAtDisconnect() {
        setUpWifiConfigurationForNonPersistentRandomization();
        WifiConfiguration config = getFirstInternalWifiConfiguration();
        when(mClock.getWallClockMillis()).thenReturn(0L);

        // First set the DHCP expiration time to be longer than the predefined time.
        long dhcpLeaseTimeInSeconds = (WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS
                / 1000) + 1;
        mWifiConfigManager.updateRandomizedMacExpireTime(config, dhcpLeaseTimeInSeconds);
        config = getFirstInternalWifiConfiguration();
        long expirationTime = config.randomizedMacExpirationTimeMs;
        assertEquals(WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS + 1000,
                expirationTime);

        // Verify that network disconnect does not update the expiration time since the remaining
        // lease duration is greater.
        mWifiConfigManager.updateNetworkAfterDisconnect(config.networkId);
        config = getFirstInternalWifiConfiguration();
        assertEquals(expirationTime, config.randomizedMacExpirationTimeMs);

        // Simulate time moving forward, then verify that a network disconnection updates the
        // MAC address expiration time correctly.
        when(mClock.getWallClockMillis()).thenReturn(5000L);
        mWifiConfigManager.updateNetworkAfterDisconnect(config.networkId);
        config = getFirstInternalWifiConfiguration();
        assertEquals(WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS + 5000,
                config.randomizedMacExpirationTimeMs);
    }

    /**
     * Verifies that the randomized MAC address is not updated when insufficient time have past
     * since the previous update.
     */
    @Test
    public void testRandomizedMacIsNotUpdatedDueToTimeConstraint() {
        setUpWifiConfigurationForNonPersistentRandomization();
        // get the persistent randomized MAC address.
        WifiConfiguration config = getFirstInternalWifiConfiguration();
        final MacAddress randMac = config.getRandomizedMacAddress();
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, randMac.toString());
        assertEquals(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                + WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS,
                config.randomizedMacExpirationTimeMs);

        // verify that the randomized MAC is unchanged.
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                + WifiConfigManager.NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS);
        MacAddress newMac = mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config, false);
        assertEquals(randMac, newMac);
    }

    /**
     * Verifies that non-persistent randomization SSID lists from DeviceConfig and overlay are being
     * combined together properly.
     */
    @Test
    public void testPerDeviceNonPersistentRandomizationSsids() {
        // This will add the SSID to allowlist using DeviceConfig.
        setUpWifiConfigurationForNonPersistentRandomization();
        WifiConfiguration config = getFirstInternalWifiConfiguration();
        MacAddress randMac = config.getRandomizedMacAddress();

        // add to non-persistent randomization blocklist using overlay.
        mResources.setStringArray(R.array.config_wifi_non_persistent_randomization_ssid_blocklist,
                new String[] {config.SSID});
        MacAddress persistentMac = mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config,
                false);
        // verify that now the persistent randomized MAC is used.
        assertNotEquals(randMac, persistentMac);
    }

    private WifiConfiguration getFirstInternalWifiConfiguration() {
        List<WifiConfiguration> configs = mWifiConfigManager.getSavedNetworks(Process.WIFI_UID);
        assertEquals(1, configs.size());
        return configs.get(0);
    }

    private void setUpWifiConfigurationForNonPersistentRandomization() {
        // sets up a WifiConfiguration for non-persistent randomization.
        WifiConfiguration c = WifiConfigurationTestUtil.createOpenNetwork();
        // Adds the WifiConfiguration to non-persistent randomization allowlist.
        Set<String> ssidList = new HashSet<>();
        ssidList.add(c.SSID);
        when(mDeviceConfigFacade.getNonPersistentMacRandomizationSsidAllowlist())
                .thenReturn(ssidList);
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(c);
        mWifiConfigManager.updateNetworkAfterDisconnect(result.getNetworkId());

        // Verify the MAC address is valid, and is NOT calculated from the (SSID + security type)
        assertTrue(WifiConfiguration.isValidMacAddressForRandomization(
                getFirstInternalWifiConfiguration().getRandomizedMacAddress()));
        verify(mMacAddressUtil, never()).calculatePersistentMacForSta(any(), anyInt());
    }

    /**
     * Verifies that macRandomizationSetting is not masked out when MAC randomization is supported.
     */
    @Test
    public void testGetConfiguredNetworksNotMaskMacRandomizationSetting() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(config);

        // Verify macRandomizationSetting is not masked out when feature is supported.
        List<WifiConfiguration> configs = mWifiConfigManager.getSavedNetworks(Process.WIFI_UID);
        assertEquals(1, configs.size());
        assertEquals(WifiConfiguration.RANDOMIZATION_AUTO, configs.get(0).macRandomizationSetting);
    }

    /**
     * Verifies that macRandomizationSetting is masked out to WifiConfiguration.RANDOMIZATION_NONE
     * when MAC randomization is not supported on the device.
     */
    @Test
    public void testGetConfiguredNetworksMasksMacRandomizationSetting() {
        mResources.setBoolean(R.bool.config_wifi_connected_mac_randomization_supported, false);
        createWifiConfigManager();
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(config);

        // Verify macRandomizationSetting is masked out when feature is unsupported.
        List<WifiConfiguration> configs = mWifiConfigManager.getSavedNetworks(Process.WIFI_UID);
        assertEquals(1, configs.size());
        assertEquals(WifiConfiguration.RANDOMIZATION_NONE, configs.get(0).macRandomizationSetting);
    }

    /**
     * Verifies that passwords are masked out when we return external configs except when
     * explicitly asked for them.
     */
    @Test
    public void testGetConfiguredNetworksMasksPasswords() {
        WifiConfiguration networkWithPasswords = WifiConfigurationTestUtil.createPskNetwork();
        networkWithPasswords.wepKeys = WifiConfigurationTestUtil.TEST_WEP_KEYS;
        networkWithPasswords.preSharedKey = WifiConfigurationTestUtil.TEST_PSK;
        networkWithPasswords.enterpriseConfig.setPassword(
                WifiConfigurationTestUtil.TEST_EAP_PASSWORD);

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(networkWithPasswords);

        // All of these passwords must be masked in this retrieved network config.
        WifiConfiguration retrievedNetworkWithMaskedPassword =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertPasswordsMaskedInWifiConfiguration(retrievedNetworkWithMaskedPassword);

        // Ensure that the passwords are present when asked for configs with passwords.
        WifiConfiguration retrievedNetworkWithPassword =
                mWifiConfigManager.getConfiguredNetworkWithPassword(result.getNetworkId());
        assertEquals(networkWithPasswords.preSharedKey, retrievedNetworkWithPassword.preSharedKey);
        assertEquals(networkWithPasswords.wepKeys, retrievedNetworkWithPassword.wepKeys);
        assertEquals(networkWithPasswords.enterpriseConfig.getPassword(),
                retrievedNetworkWithPassword.enterpriseConfig.getPassword());

        retrievedNetworkWithPassword =
                mWifiConfigManager.getConfiguredNetworkWithoutMasking(result.getNetworkId());
        assertEquals(networkWithPasswords.preSharedKey, retrievedNetworkWithPassword.preSharedKey);
        assertEquals(networkWithPasswords.wepKeys, retrievedNetworkWithPassword.wepKeys);
        assertEquals(networkWithPasswords.enterpriseConfig.getPassword(),
                retrievedNetworkWithPassword.enterpriseConfig.getPassword());
    }

    /**
     * Verifies the linking of networks when they have the same default GW Mac address in
     * {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)}.
     */
    @Test
    public void testNetworkLinkUsingGwMacAddress() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey2\"";
        network3.preSharedKey = "\"preSharedKey3\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);

        // Set the same default GW mac address for all of the networks.
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network1.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network2.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network3.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));

        // Now create fake scan detail corresponding to the networks.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1);
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2);
        ScanDetail networkScanDetail3 = createScanDetailForNetwork(network3);

        // Now save all these scan details corresponding to each of this network and expect
        // all of these networks to be linked with each other.
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail3));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network3.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);
        mWifiConfigManager.updateLinkedNetworks(network3.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertEquals(2, network.linkedConfigurations.size());
            for (WifiConfiguration otherNetwork : retrievedNetworks) {
                if (otherNetwork == network) {
                    continue;
                }
                assertNotNull(network.linkedConfigurations.get(
                        otherNetwork.getProfileKey()));
            }
        }
    }

    /**
     * Verifies the linking of networks when they have the same default GW Mac address in
     * {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)} AND the
     * same pre-shared key.
     */
    @Test
    public void testNetworkLinkOnlyIfCredentialsMatch() {
        mResources.setBoolean(
                R.bool.config_wifi_only_link_same_credential_configurations, true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        // Set the same pre-shared key for network 1 and 2 but not 3
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey1\"";
        network3.preSharedKey = "\"preSharedKey2\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);

        // Set the same default GW mac address for all of the networks.
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network1.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network2.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network3.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));

        // Now create fake scan detail corresponding to the networks.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1);
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2);
        ScanDetail networkScanDetail3 = createScanDetailForNetwork(network3);

        // Now save all these scan details corresponding to each of this network and expect
        // all of these networks to be linked with each other.
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail3));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network3.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);
        mWifiConfigManager.updateLinkedNetworks(network3.networkId);

        Map<Integer, WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks().stream().collect(
                        Collectors.toMap(config -> config.networkId, Function.identity()));
        // Networks 1 and 2 should link due to GW match + same pre-shared key
        WifiConfiguration retrievedNetwork1 = retrievedNetworks.get(network1.networkId);
        assertEquals(1, retrievedNetwork1.linkedConfigurations.size());
        assertNotNull(retrievedNetwork1.linkedConfigurations.get(network2.getProfileKey()));
        WifiConfiguration retrievedNetwork2 = retrievedNetworks.get(network2.networkId);
        assertEquals(1, retrievedNetwork2.linkedConfigurations.size());
        assertNotNull(retrievedNetwork2.linkedConfigurations.get(network1.getProfileKey()));
        // Network 3 should not link due to different pre-shared key
        WifiConfiguration retrievedNetwork3 = retrievedNetworks.get(network3.networkId);
        assertNull(retrievedNetwork3.linkedConfigurations);
    }

    /**
     * Verifies no linking of networks when the gateways have VRRP MAC addresses that match the VRRP
     * prefix of 00:00:5E:00:01.
     */
    @Test
    public void testNoNetworkLinkVrrpMacAddress() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey2\"";
        network3.preSharedKey = "\"preSharedKey3\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);

        final String vrrpMacAddress = "00:00:5E:00:01:00";
        // Set the same VRRP GW mac address for all of the networks.
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network1.networkId, vrrpMacAddress));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network2.networkId, vrrpMacAddress));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network3.networkId, vrrpMacAddress));

        // Now create fake scan detail corresponding to the networks.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1);
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2);
        ScanDetail networkScanDetail3 = createScanDetailForNetwork(network3);

        // Now save all these scan details corresponding to each of this network and expect
        // all of these networks to be linked with each other.
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail3));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network3.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);
        mWifiConfigManager.updateLinkedNetworks(network3.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertNull(network.linkedConfigurations);
        }
    }

    /**
     * Verifies the linking of networks when they have the same default GW Mac address in
     * {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)} for
     * PSK and SAE networks.
     */
    @Test
    public void testNetworkLinkUsingGwMacAddressWithPskAndSaeTypes() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createSaeNetwork();
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey2\"";
        network3.preSharedKey = "\"preSharedKey3\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);

        // Set the same default GW mac address for all of the networks.
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network1.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network2.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network3.networkId, TEST_DEFAULT_GW_MAC_ADDRESS));

        // Now create fake scan detail corresponding to the networks.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1);
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2);
        ScanDetail networkScanDetail3 = createScanDetailForNetwork(network3);

        // Now save all these scan details corresponding to each of this network and expect
        // all of these networks to be linked with each other.
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail3));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network3.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);
        mWifiConfigManager.updateLinkedNetworks(network3.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertEquals(2, network.linkedConfigurations.size());
            for (WifiConfiguration otherNetwork : retrievedNetworks) {
                if (otherNetwork == network) {
                    continue;
                }
                assertNotNull(network.linkedConfigurations.get(
                        otherNetwork.getProfileKey()));
            }
        }
    }

    /**
     * Verify that setRecentFailureAssociationStatus is setting the recent failure reason and
     * expiration timestamp properly. Then, verify that cleanupExpiredRecentFailureReasons
     * properly clears expired recent failure statuses.
     */
    @Test
    public void testSetRecentFailureAssociationStatus() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(config);
        int expectedStatusCode = WifiConfiguration.RECENT_FAILURE_POOR_CHANNEL_CONDITIONS;
        long updateTimeMs = 1234L;
        mResources.setInteger(R.integer.config_wifiRecentFailureReasonExpirationMinutes, 1);
        long expectedExpirationTimeMs = updateTimeMs + 60_000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(updateTimeMs);
        mWifiConfigManager.setRecentFailureAssociationStatus(config.networkId, expectedStatusCode);

        // Verify the config changed broadcast is sent
        verifyNetworkUpdateBroadcast();

        // Verify the recent failure association status is set properly
        List<WifiConfiguration> configs = mWifiConfigManager.getSavedNetworks(Process.WIFI_UID);
        assertEquals(1, configs.size());
        verify(mWifiMetrics).incrementRecentFailureAssociationStatusCount(eq(expectedStatusCode));
        assertEquals(expectedStatusCode, configs.get(0).getRecentFailureReason());
        assertEquals(updateTimeMs,
                configs.get(0).recentFailure.getLastUpdateTimeSinceBootMillis());

        // Verify at t-1 the failure status is not cleared yet.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(expectedExpirationTimeMs - 1);
        mWifiConfigManager.cleanupExpiredRecentFailureReasons();
        assertEquals(expectedStatusCode, mWifiConfigManager.getSavedNetworks(Process.WIFI_UID)
                .get(0).getRecentFailureReason());

        // Verify at the expiration time the failure status is cleared.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(expectedExpirationTimeMs);
        mWifiConfigManager.cleanupExpiredRecentFailureReasons();
        assertEquals(WifiConfiguration.RECENT_FAILURE_NONE,
                mWifiConfigManager.getSavedNetworks(Process.WIFI_UID)
                        .get(0).getRecentFailureReason());

        verifyNetworkUpdateBroadcast();
    }

    /**
     * Verifies the linking of networks when they have scan results with same first 16 ASCII of
     * bssid in
     * {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)}.
     */
    @Test
    public void testNetworkLinkUsingBSSIDMatch() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey2\"";
        network3.preSharedKey = "\"preSharedKey3\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);

        // Create scan results with bssid which is different in only the last char.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1, "af:89:56:34:56:67");
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2, "af:89:56:34:56:68");
        ScanDetail networkScanDetail3 = createScanDetailForNetwork(network3, "af:89:56:34:56:69");

        // Now save all these scan details corresponding to each of this network and expect
        // all of these networks to be linked with each other.
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail3));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network3.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);
        mWifiConfigManager.updateLinkedNetworks(network3.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertEquals(2, network.linkedConfigurations.size());
            for (WifiConfiguration otherNetwork : retrievedNetworks) {
                if (otherNetwork == network) {
                    continue;
                }
                assertNotNull(network.linkedConfigurations.get(
                        otherNetwork.getProfileKey()));
            }
        }

        // Verify that testGetLinkedNetworksWithoutMasking returns configs with
        // candidate security type PSK
        for (WifiConfiguration network : mWifiConfigManager.getLinkedNetworksWithoutMasking(
                network1.networkId).values()) {
            assertTrue(network.getNetworkSelectionStatus().getCandidateSecurityParams()
                    .isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK));
        }
    }

    /**
     * Verifies the linking of networks does not happen for non WPA networks when they have scan
     * results with same first 16 ASCII of bssid in
     * {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)}.
     */
    @Test
    public void testNoNetworkLinkUsingBSSIDMatchForNonWpaNetworks() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);

        // Create scan results with bssid which is different in only the last char.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1, "af:89:56:34:56:67");
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2, "af:89:56:34:56:68");

        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertNull(network.linkedConfigurations);
        }
    }

    /**
     * Verifies the linking of networks does not happen for networks with more than
     * {@link WifiConfigManager#LINK_CONFIGURATION_MAX_SCAN_CACHE_ENTRIES} scan
     * results with same first 16 ASCII of bssid in
     * {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)}.
     */
    @Test
    public void testNoNetworkLinkUsingBSSIDMatchForNetworksWithHighScanDetailCacheSize() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey2\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);

        // Create 7 scan results with bssid which is different in only the last char.
        String test_bssid_base = "af:89:56:34:56:6";
        int scan_result_num = 0;
        for (; scan_result_num < WifiConfigManager.LINK_CONFIGURATION_MAX_SCAN_CACHE_ENTRIES + 1;
             scan_result_num++) {
            ScanDetail networkScanDetail =
                    createScanDetailForNetwork(
                            network1, test_bssid_base + Integer.toString(scan_result_num));
            assertNotNull(
                    mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                            networkScanDetail));
        }

        // Now add 1 scan result to the other network with bssid which is different in only the
        // last char.
        ScanDetail networkScanDetail2 =
                createScanDetailForNetwork(
                        network2, test_bssid_base + Integer.toString(scan_result_num++));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertNull(network.linkedConfigurations);
        }
    }

    /**
     * Verifies the linking of networks when they have scan results with same first 16 ASCII of
     * bssid in {@link WifiConfigManager#getOrCreateScanDetailCacheForNetwork(WifiConfiguration)}
     * and then subsequently delinked when the networks have default gateway set which do not match.
     */
    @Test
    public void testNetworkLinkUsingBSSIDMatchAndThenUnlinkDueToGwMacAddress() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network1.preSharedKey = "\"preSharedKey1\"";
        network2.preSharedKey = "\"preSharedKey2\"";
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);

        // Create scan results with bssid which is different in only the last char.
        ScanDetail networkScanDetail1 = createScanDetailForNetwork(network1, "af:89:56:34:56:67");
        ScanDetail networkScanDetail2 = createScanDetailForNetwork(network2, "af:89:56:34:56:68");

        // Now save all these scan details corresponding to each of this network and expect
        // all of these networks to be linked with each other.
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail1));
        assertNotNull(mWifiConfigManager.getSavedNetworkForScanDetailAndCache(
                networkScanDetail2));

        // Now update the linked networks for all of the networks
        mWifiConfigManager.updateNetworkSelectionStatus(network1.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateNetworkSelectionStatus(network2.networkId,
                TEST_NETWORK_SELECTION_ENABLE_REASON);
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertEquals(1, network.linkedConfigurations.size());
            for (WifiConfiguration otherNetwork : retrievedNetworks) {
                if (otherNetwork == network) {
                    continue;
                }
                assertNotNull(network.linkedConfigurations.get(
                        otherNetwork.getProfileKey()));
            }
        }

        // Now Set different GW mac address for both the networks and ensure they're unlinked.
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network1.networkId, "de:ad:fe:45:23:34"));
        assertTrue(mWifiConfigManager.setNetworkDefaultGwMacAddress(
                network2.networkId, "ad:de:fe:45:23:34"));
        mWifiConfigManager.updateLinkedNetworks(network1.networkId);
        mWifiConfigManager.updateLinkedNetworks(network2.networkId);

        retrievedNetworks = mWifiConfigManager.getConfiguredNetworks();
        for (WifiConfiguration network : retrievedNetworks) {
            assertNull(network.linkedConfigurations);
        }
    }

    /**
     * Verifies the foreground user switch using {@link WifiConfigManager#handleUserSwitch(int)}
     * and ensures that any shared private networks networkId is not changed.
     * Test scenario:
     * 1. Load the shared networks from shared store and user 1 store.
     * 2. Switch to user 2 and ensure that the shared network's Id is not changed.
     */
    @Test
    public void testHandleUserSwitchDoesNotChangeSharedNetworksId() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        int appId = 674;
        long currentTimeMs = 67823;
        when(mClock.getWallClockMillis()).thenReturn(currentTimeMs);

        // Create 3 networks. 1 for user1, 1 for user2 and 1 shared.
        final WifiConfiguration user1Network = WifiConfigurationTestUtil.createPskNetwork();
        user1Network.shared = false;
        user1Network.creatorUid = UserHandle.getUid(user1, appId);
        final WifiConfiguration user2Network = WifiConfigurationTestUtil.createPskNetwork();
        user2Network.shared = false;
        user2Network.creatorUid = UserHandle.getUid(user2, appId);
        final WifiConfiguration sharedNetwork1 = WifiConfigurationTestUtil.createPskNetwork();
        final WifiConfiguration sharedNetwork2 = WifiConfigurationTestUtil.createPskNetwork();

        // Set up the store data that is loaded initially.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork1);
                add(sharedNetwork2);
            }
        };
        List<WifiConfiguration> user1Networks = new ArrayList<WifiConfiguration>() {
            {
                add(user1Network);
            }
        };
        setupStoreDataForRead(sharedNetworks, user1Networks);
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Fetch the network ID's assigned to the shared networks initially.
        int sharedNetwork1Id = WifiConfiguration.INVALID_NETWORK_ID;
        int sharedNetwork2Id = WifiConfiguration.INVALID_NETWORK_ID;
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        for (WifiConfiguration network : retrievedNetworks) {
            if (network.getProfileKey().equals(sharedNetwork1.getProfileKey())) {
                sharedNetwork1Id = network.networkId;
            } else if (network.getProfileKey().equals(
                    sharedNetwork2.getProfileKey())) {
                sharedNetwork2Id = network.networkId;
            }
        }
        assertTrue(sharedNetwork1Id != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(sharedNetwork2Id != WifiConfiguration.INVALID_NETWORK_ID);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(TEST_SSID));

        // Set up the user 2 store data that is loaded at user switch.
        List<WifiConfiguration> user2Networks = new ArrayList<WifiConfiguration>() {
            {
                add(user2Network);
            }
        };
        setupStoreDataForUserRead(user2Networks, new HashMap<>());
        // Now switch the user to user 2 and ensure that shared network's IDs have not changed.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        mWifiConfigManager.handleUserSwitch(user2);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));

        // Again fetch the network ID's assigned to the shared networks and ensure they have not
        // changed.
        int updatedSharedNetwork1Id = WifiConfiguration.INVALID_NETWORK_ID;
        int updatedSharedNetwork2Id = WifiConfiguration.INVALID_NETWORK_ID;
        retrievedNetworks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        for (WifiConfiguration network : retrievedNetworks) {
            if (network.getProfileKey().equals(sharedNetwork1.getProfileKey())) {
                updatedSharedNetwork1Id = network.networkId;
            } else if (network.getProfileKey().equals(
                    sharedNetwork2.getProfileKey())) {
                updatedSharedNetwork2Id = network.networkId;
            }
        }
        assertEquals(sharedNetwork1Id, updatedSharedNetwork1Id);
        assertEquals(sharedNetwork2Id, updatedSharedNetwork2Id);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(TEST_SSID));
    }

    /**
     * Verifies the foreground user switch using {@link WifiConfigManager#handleUserSwitch(int)}
     * and ensures that any old user private networks are not visible anymore.
     * Test scenario:
     * 1. Load the shared networks from shared store and user 1 store.
     * 2. Switch to user 2 and ensure that the user 1's private network has been removed.
     */
    @Test
    public void testHandleUserSwitchRemovesOldUserPrivateNetworks() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        int appId = 674;

        // Create 3 networks. 1 for user1, 1 for user2 and 1 shared.
        final WifiConfiguration user1Network = WifiConfigurationTestUtil.createPskNetwork();
        user1Network.shared = false;
        user1Network.creatorUid = UserHandle.getUid(user1, appId);
        final WifiConfiguration user2Network = WifiConfigurationTestUtil.createPskNetwork();
        user2Network.shared = false;
        user2Network.creatorUid = UserHandle.getUid(user2, appId);
        final WifiConfiguration sharedNetwork = WifiConfigurationTestUtil.createPskNetwork();

        // Set up the store data that is loaded initially.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
            }
        };
        List<WifiConfiguration> user1Networks = new ArrayList<WifiConfiguration>() {
            {
                add(user1Network);
            }
        };
        setupStoreDataForRead(sharedNetworks, user1Networks);
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Fetch the network ID assigned to the user 1 network initially.
        int user1NetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        for (WifiConfiguration network : retrievedNetworks) {
            if (network.getProfileKey().equals(user1Network.getProfileKey())) {
                user1NetworkId = network.networkId;
            }
        }

        // Set up the user 2 store data that is loaded at user switch.
        List<WifiConfiguration> user2Networks = new ArrayList<WifiConfiguration>() {
            {
                add(user2Network);
            }
        };
        setupStoreDataForUserRead(user2Networks, new HashMap<>());
        // Now switch the user to user 2 and ensure that user 1's private network has been removed.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        when(mWifiPermissionsUtil.doesUidBelongToUser(user1Network.creatorUid, user1))
                .thenReturn(true);
        Set<Integer> removedNetworks = mWifiConfigManager.handleUserSwitch(user2);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));
        assertTrue((removedNetworks.size() == 1) && (removedNetworks.contains(user1NetworkId)));
        verify(mWcmListener).onNetworkRemoved(any());

        // Set the expected networks to be |sharedNetwork| and |user2Network|.
        List<WifiConfiguration> expectedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
                add(user2Network);
            }
        };
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                expectedNetworks, mWifiConfigManager.getConfiguredNetworksWithPasswords());

        // Send another user switch  indication with the same user 2. This should be ignored and
        // hence should not remove any new networks.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        removedNetworks = mWifiConfigManager.handleUserSwitch(user2);
        assertTrue(removedNetworks.isEmpty());
    }

    @Test
    public void testHandleUserSwitchRemovesOldUserEphemeralNetworks() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        int appId = 674;

        // Create 2 networks. 1 ephemeral network for user1 and 1 shared.
        final WifiConfiguration sharedNetwork = WifiConfigurationTestUtil.createPskNetwork();

        // Set up the store data that is loaded initially.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
            }
        };
        setupStoreDataForRead(sharedNetworks, Collections.EMPTY_LIST);
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        WifiConfiguration ephemeralNetwork = WifiConfigurationTestUtil.createEphemeralNetwork();
        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNetwork);

        // Fetch the network ID assigned to the user 1 network initially.
        int ephemeralNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        for (WifiConfiguration network : retrievedNetworks) {
            if (network.getProfileKey().equals(ephemeralNetwork.getProfileKey())) {
                ephemeralNetworkId = network.networkId;
            }
        }

        // Now switch the user to user 2 and ensure that user 1's private network has been removed.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        Set<Integer> removedNetworks = mWifiConfigManager.handleUserSwitch(user2);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));
        assertTrue((removedNetworks.size() == 1));
        assertTrue(removedNetworks.contains(ephemeralNetworkId));
        verifyNetworkRemoveBroadcast();
        verify(mWcmListener).onNetworkRemoved(any());


        // Set the expected networks to be |sharedNetwork|.
        List<WifiConfiguration> expectedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
            }
        };
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                expectedNetworks, mWifiConfigManager.getConfiguredNetworksWithPasswords());

        // Send another user switch  indication with the same user 2. This should be ignored and
        // hence should not remove any new networks.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        removedNetworks = mWifiConfigManager.handleUserSwitch(user2);
        assertTrue(removedNetworks.isEmpty());
    }

    /**
     * Verifies the foreground user switch using {@link WifiConfigManager#handleUserSwitch(int)}
     * and ensures that user switch from a user with no private networks is handled.
     * Test scenario:
     * 1. Load the shared networks from shared store and emptu user 1 store.
     * 2. Switch to user 2 and ensure that no private networks were removed.
     */
    @Test
    public void testHandleUserSwitchWithNoOldUserPrivateNetworks() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        int appId = 674;

        // Create 2 networks. 1 for user2 and 1 shared.
        final WifiConfiguration user2Network = WifiConfigurationTestUtil.createPskNetwork();
        user2Network.shared = false;
        user2Network.creatorUid = UserHandle.getUid(user2, appId);
        final WifiConfiguration sharedNetwork = WifiConfigurationTestUtil.createPskNetwork();

        // Set up the store data that is loaded initially.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
            }
        };
        setupStoreDataForRead(sharedNetworks, new ArrayList<>());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Set up the user 2 store data that is loaded at user switch.
        List<WifiConfiguration> user2Networks = new ArrayList<WifiConfiguration>() {
            {
                add(user2Network);
            }
        };
        setupStoreDataForUserRead(user2Networks, new HashMap<>());
        // Now switch the user to user 2 and ensure that no private network has been removed.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        Set<Integer> removedNetworks = mWifiConfigManager.handleUserSwitch(user2);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));
        assertTrue(removedNetworks.isEmpty());
    }

    /**
     * Verifies the foreground user switch using {@link WifiConfigManager#handleUserSwitch(int)}
     * and ensures that any non current user private networks are moved to shared store file.
     * This test simulates the following test case:
     * 1. Loads the shared networks from shared store at bootup.
     * 2. Load the private networks from user store on user 1 unlock.
     * 3. Switch to user 2 and ensure that the user 2's private network has been moved to user 2's
     * private store file.
     */
    @Test
    public void testHandleUserSwitchPushesOtherPrivateNetworksToSharedStore() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user1);

        int appId = 674;

        // Create 3 networks. 1 for user1, 1 for user2 and 1 shared.
        final WifiConfiguration user1Network = WifiConfigurationTestUtil.createPskNetwork();
        user1Network.shared = false;
        user1Network.creatorUid = UserHandle.getUid(user1, appId);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(user1Network.creatorUid))
                .thenReturn(false);

        final WifiConfiguration user2Network = WifiConfigurationTestUtil.createPskNetwork();
        user2Network.shared = false;
        user2Network.creatorUid = UserHandle.getUid(user2, appId);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(user2Network.creatorUid))
                .thenReturn(false);

        final WifiConfiguration sharedNetwork = WifiConfigurationTestUtil.createPskNetwork();

        // Set up the shared store data that is loaded at bootup. User 2's private network
        // is still in shared store because they have not yet logged-in after upgrade.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
                add(user2Network);
            }
        };
        setupStoreDataForRead(sharedNetworks, new ArrayList<>());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Set up the user store data that is loaded at user unlock.
        List<WifiConfiguration> userNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(user1Network);
            }
        };
        setupStoreDataForUserRead(userNetworks, new HashMap<>());
        when(mWifiPermissionsUtil.doesUidBelongToUser(user1Network.creatorUid, user1))
                .thenReturn(true);
        when(mWifiPermissionsUtil.doesUidBelongToUser(user2Network.creatorUid, user2))
                .thenReturn(true);
        mWifiConfigManager.handleUserUnlock(user1);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));
        assertFalse(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        // Capture the written data for the user 1 and ensure that it corresponds to what was
        // setup.
        Pair<List<WifiConfiguration>, List<WifiConfiguration>> writtenNetworkList =
                captureWriteNetworksListStoreData();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                sharedNetworks, writtenNetworkList.first);
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                userNetworks, writtenNetworkList.second);

        // Now switch the user to user2 and ensure that user 2's private network has been moved to
        // the user store.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        mWifiConfigManager.handleUserSwitch(user2);
        // Set the expected network list before comparing. user1Network should be in shared data.
        // Note: In the real world, user1Network will no longer be visible now because it should
        // already be in user1's private store file. But, we're purposefully exposing it
        // via |loadStoreData| to test if other user's private networks are pushed to shared store.
        List<WifiConfiguration> expectedSharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
                add(user1Network);
            }
        };
        List<WifiConfiguration> expectedUserNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(user2Network);
            }
        };
        // Capture the first written data triggered for saving the old user's network
        // configurations.
        writtenNetworkList = captureWriteNetworksListStoreData();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                sharedNetworks, writtenNetworkList.first);
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                userNetworks, writtenNetworkList.second);

        // Now capture the next written data triggered after the switch and ensure that user 2's
        // network is now in user store data.
        writtenNetworkList = captureWriteNetworksListStoreData();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                expectedSharedNetworks, writtenNetworkList.first);
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                expectedUserNetworks, writtenNetworkList.second);
    }

    /**
     * Verify that unlocking an user that owns a legacy Passpoint configuration (which is stored
     * temporarily in the share store) will migrate it to PasspointManager and removed from
     * the list of configured networks.
     *
     * @throws Exception
     */
    @Test
    public void testHandleUserUnlockRemovePasspointConfigFromSharedConfig() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int appId = 674;

        final WifiConfiguration passpointConfig =
                WifiConfigurationTestUtil.createPasspointNetwork();
        passpointConfig.creatorUid = UserHandle.getUid(user1, appId);
        passpointConfig.isLegacyPasspointConfig = true;

        // Set up the shared store data to contain one legacy Passpoint configuration.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(passpointConfig);
            }
        };
        setupStoreDataForRead(sharedNetworks, new ArrayList<>());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();
        assertEquals(1, mWifiConfigManager.getConfiguredNetworks().size());

        // Unlock the owner of the legacy Passpoint configuration, verify it is removed from
        // the configured networks (migrated to PasspointManager).
        setupStoreDataForUserRead(new ArrayList<WifiConfiguration>(), new HashMap<>());
        when(mWifiPermissionsUtil.doesUidBelongToUser(passpointConfig.creatorUid, user1))
                .thenReturn(true);
        mWifiConfigManager.handleUserUnlock(user1);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));
        assertFalse(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        Pair<List<WifiConfiguration>, List<WifiConfiguration>> writtenNetworkList =
                captureWriteNetworksListStoreData();
        assertTrue(writtenNetworkList.first.isEmpty());
        assertTrue(writtenNetworkList.second.isEmpty());
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
    }

    /**
     * Verifies the foreground user switch using {@link WifiConfigManager#handleUserSwitch(int)}
     * and {@link WifiConfigManager#handleUserUnlock(int)} and ensures that the new store is
     * read immediately if the user is unlocked during the switch.
     */
    @Test
    public void testHandleUserSwitchWhenUnlocked() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Set up the internal data first.
        assertTrue(mWifiConfigManager.loadFromStore());

        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        // user2 is unlocked and switched to foreground.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        mWifiConfigManager.handleUserSwitch(user2);

        verify(mWifiCarrierInfoManager).onUnlockedUserSwitching(eq(user2));
        // Ensure that the read was invoked.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
    }

    /**
     * Verifies the foreground user switch using {@link WifiConfigManager#handleUserSwitch(int)}
     * and {@link WifiConfigManager#handleUserUnlock(int)} and ensures that the new store is not
     * read until the user is unlocked.
     */
    @Test
    public void testHandleUserSwitchWhenLocked() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Set up the internal data first.
        assertTrue(mWifiConfigManager.loadFromStore());

        // user2 is locked and switched to foreground.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(false);
        mWifiConfigManager.handleUserSwitch(user2);

        // Ensure that the read was not invoked.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        // Now try unlocking some other user (user1), this should be ignored.
        mWifiConfigManager.handleUserUnlock(user1);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        // Unlock the user2 and ensure that we read the data now.
        mWifiConfigManager.handleUserUnlock(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
    }

    /**
     * Verifies that the user stop handling using {@link WifiConfigManager#handleUserStop(int)}
     * and ensures that the store is written only when the foreground user is stopped.
     */
    @Test
    public void testHandleUserStop() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Set up the internal data first.
        assertTrue(mWifiConfigManager.loadFromStore());

        // Try stopping background user2 first, this should not do anything.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(false);
        mWifiConfigManager.handleUserStop(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        // Now try stopping the foreground user1, this should trigger a write to store.
        mWifiConfigManager.handleUserStop(user1);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
        verify(mWifiMetrics).wifiConfigStored(anyInt());
    }

    /**
     * Verifies that the user stop handling using {@link WifiConfigManager#handleUserStop(int)}
     * and ensures that the shared data is not lost when the foreground user is stopped.
     */
    @Test
    public void testHandleUserStopDoesNotClearSharedData() throws Exception {
        int user1 = TEST_DEFAULT_USER;

        //
        // Setup the database for the user before initiating stop.
        //
        int appId = 674;
        // Create 2 networks. 1 for user1, and 1 shared.
        final WifiConfiguration user1Network = WifiConfigurationTestUtil.createPskNetwork();
        user1Network.shared = false;
        user1Network.creatorUid = UserHandle.getUid(user1, appId);
        final WifiConfiguration sharedNetwork = WifiConfigurationTestUtil.createPskNetwork();

        // Set up the store data that is loaded initially.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(sharedNetwork);
            }
        };
        List<WifiConfiguration> user1Networks = new ArrayList<WifiConfiguration>() {
            {
                add(user1Network);
            }
        };
        setupStoreDataForRead(sharedNetworks, user1Networks);
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Ensure that we have 2 networks in the database before the stop.
        assertEquals(2, mWifiConfigManager.getConfiguredNetworks().size());
        when(mWifiPermissionsUtil.doesUidBelongToUser(user1Network.creatorUid, user1))
                .thenReturn(true);
        mWifiConfigManager.handleUserStop(user1);

        // Ensure that we only have 1 shared network in the database after the stop.
        assertEquals(1, mWifiConfigManager.getConfiguredNetworks().size());
        assertEquals(sharedNetwork.SSID, mWifiConfigManager.getConfiguredNetworks().get(0).SSID);
    }

    /**
     * Verify that hasNeverDetectedCaptivePortal is set to false after noteCaptivePortalDetected
     * gets called.
     */
    @Test
    public void testNoteCaptivePortalDetected() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        assertTrue(mWifiConfigManager.getConfiguredNetworks().get(0).getNetworkSelectionStatus()
                .hasNeverDetectedCaptivePortal());

        mWifiConfigManager.noteCaptivePortalDetected(openNetwork.networkId);
        assertFalse(mWifiConfigManager.getConfiguredNetworks().get(0).getNetworkSelectionStatus()
                .hasNeverDetectedCaptivePortal());
    }

    /**
     * Verifies the foreground user unlock via {@link WifiConfigManager#handleUserUnlock(int)}
     * results in a store read after bootup.
     */
    @Test
    public void testHandleUserUnlockAfterBootup() throws Exception {
        int user1 = TEST_DEFAULT_USER;

        // Set up the internal data first.
        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        // Unlock the user1 (default user) for the first time and ensure that we read the data.
        mWifiConfigManager.handleUserUnlock(user1);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
        verify(mWifiMetrics).wifiConfigStored(anyInt());
        // Verify shut down handling
        mWifiConfigManager.handleShutDown();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
    }

    /**
     * Verifies that the store read after bootup received after
     * foreground user unlock via {@link WifiConfigManager#handleUserUnlock(int)}
     * results in a user store read.
     */
    @Test
    public void testHandleBootupAfterUserUnlock() throws Exception {
        int user1 = TEST_DEFAULT_USER;

        // Unlock the user1 (default user) for the first time and ensure that we don't read the
        // data.
        mWifiConfigManager.handleUserUnlock(user1);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        setupStoreDataForUserRead(new ArrayList<WifiConfiguration>(), new HashMap<>());
        // Read from store now.
        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .setUserStores(any(List.class));
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).read();
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());
    }

    /**
     * Verifies that the store read after bootup received after
     * a user switch via {@link WifiConfigManager#handleUserSwitch(int)}
     * results in a user store read.
     */
    @Test
    public void testHandleBootupAfterUserSwitch() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Switch from user1 to user2 and ensure that we don't read or write any data
        // (need to wait for loadFromStore invocation).
        mWifiConfigManager.handleUserSwitch(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());

        // Now load from the store.
        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).read();

        // Unlock the user2 and ensure that we read from the user store.
        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        mWifiConfigManager.handleUserUnlock(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
        verify(mWifiMetrics, atLeastOnce()).wifiConfigStored(anyInt());
    }

    /**
     * Verifies that the store read after bootup received after
     * a previous user unlock and user switch via {@link WifiConfigManager#handleUserSwitch(int)}
     * results in a user store read.
     */
    @Test
    public void testHandleBootupAfterPreviousUserUnlockAndSwitch() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Unlock the user1 (default user) for the first time and ensure that we don't read the data
        // (need to wait for loadFromStore invocation).
        mWifiConfigManager.handleUserUnlock(user1);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        // Switch from user1 to user2 and ensure that we don't read or write any data
        // (need to wait for loadFromStore invocation).
        mWifiConfigManager.handleUserSwitch(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());

        // Now load from the store.
        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).read();

        // Unlock the user2 and ensure that we read from the user store.
        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        mWifiConfigManager.handleUserUnlock(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
        verify(mWifiMetrics, atLeastOnce()).wifiConfigStored(anyInt());
    }

    /**
     * Verifies that the store read after bootup received after
     * a user switch and unlock of a previous user via {@link WifiConfigManager#
     * handleUserSwitch(int)} results in a user store read.
     */
    @Test
    public void testHandleBootupAfterUserSwitchAndPreviousUserUnlock() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Switch from user1 to user2 and ensure that we don't read or write any data
        // (need to wait for loadFromStore invocation).
        mWifiConfigManager.handleUserSwitch(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));

        // Unlock the user1 for the first time and ensure that we don't read the data
        mWifiConfigManager.handleUserUnlock(user1);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).read();
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(anyBoolean());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));
        verify(mWifiMetrics, never()).wifiConfigStored(anyInt());

        // Now load from the store.
        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mWifiConfigStore).read();

        // Unlock the user2 and ensure that we read from the user store.
        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        mWifiConfigManager.handleUserUnlock(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
        verify(mWifiMetrics, atLeastOnce()).wifiConfigStored(anyInt());
    }

    /**
     * Verifies the foreground user unlock via {@link WifiConfigManager#handleUserUnlock(int)} does
     * not always result in a store read unless the user had switched or just booted up.
     */
    @Test
    public void testHandleUserUnlockWithoutSwitchOrBootup() throws Exception {
        int user1 = TEST_DEFAULT_USER;
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        // Set up the internal data first.
        assertTrue(mWifiConfigManager.loadFromStore());

        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        // user2 is unlocked and switched to foreground.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        mWifiConfigManager.handleUserSwitch(user2);
        // Ensure that the read was invoked.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));

        // Unlock the user2 again and ensure that we don't read the data now.
        mWifiConfigManager.handleUserUnlock(user2);
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never())
                .switchUserStoresAndRead(any(List.class));
    }

    /**
     * Verifies the private network addition using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     * by a non foreground user is rejected.
     */
    @Test
    public void testAddNetworkUsingBackgroundUserUId() throws Exception {
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        int creatorUid = UserHandle.getUid(user2, 674);

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(creatorUid)).thenReturn(false);
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(creatorUid))
                .thenReturn(false);

        // Create a network for user2 try adding it. This should be rejected.
        final WifiConfiguration user2Network = WifiConfigurationTestUtil.createPskNetwork();
        NetworkUpdateResult result = addNetworkToWifiConfigManager(user2Network, creatorUid);
        assertFalse(result.isSuccess());
    }

    /**
     * Verifies the private network addition using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     * by SysUI is always accepted.
     */
    @Test
    public void testAddNetworkUsingSysUiUid() throws Exception {
        // Set up the user profiles stuff. Needed for |WifiConfigurationUtil.isVisibleToAnyProfile|
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(false);
        mWifiConfigManager.handleUserSwitch(user2);

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_SYSUI_UID)).thenReturn(true);

        // Create a network for user2 try adding it. This should be rejected.
        final WifiConfiguration user2Network = WifiConfigurationTestUtil.createPskNetwork();
        NetworkUpdateResult result = addNetworkToWifiConfigManager(user2Network, TEST_SYSUI_UID);
        assertTrue(result.isSuccess());
    }

    /**
     * Verifies the loading of networks using {@link WifiConfigManager#loadFromStore()}
     * attempts to read from the stores even when the store files are not present.
     */
    @Test
    public void testFreshInstallLoadFromStore() throws Exception {
        assertTrue(mWifiConfigManager.loadFromStore());

        verify(mWifiConfigStore).read();

        assertTrue(mWifiConfigManager.getConfiguredNetworksWithPasswords().isEmpty());
    }

    /**
     * Verifies the loading of networks using {@link WifiConfigManager#loadFromStore()}
     * attempts to read from the stores even if the store files are not present and the
     * user unlock already comes in.
     */
    @Test
    public void testFreshInstallLoadFromStoreAfterUserUnlock() throws Exception {
        int user1 = TEST_DEFAULT_USER;

        // Unlock the user1 (default user) for the first time and ensure that we don't read the
        // data.
        mWifiConfigManager.handleUserUnlock(user1);
        verify(mWifiConfigStore, never()).read();

        // Read from store now.
        assertTrue(mWifiConfigManager.loadFromStore());

        // Ensure that the read was invoked.
        verify(mWifiConfigStore).read();
    }

    /**
     * Verifies the user switch using {@link WifiConfigManager#handleUserSwitch(int)} is handled
     * when the store files (new or legacy) are not present.
     */
    @Test
    public void testHandleUserSwitchAfterFreshInstall() throws Exception {
        int user2 = TEST_DEFAULT_USER + 1;

        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        setupStoreDataForUserRead(new ArrayList<>(), new HashMap<>());
        // Now switch the user to user 2.
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        mWifiConfigManager.handleUserSwitch(user2);
        // Ensure that the read was invoked.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore)
                .switchUserStoresAndRead(any(List.class));
    }

    /**
     * Verifies that the last user selected network parameter is set when
     * {@link WifiConfigManager#enableNetwork(int, boolean, int)} with disableOthers flag is set
     * to true and cleared when either {@link WifiConfigManager#disableNetwork(int, int)} or
     * {@link WifiConfigManager#removeNetwork(int, int)} is invoked using the same network ID.
     */
    @Test
    public void testLastSelectedNetwork() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(67L);
        assertTrue(mWifiConfigManager.enableNetwork(
                result.getNetworkId(), true, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertEquals(result.getNetworkId(), mWifiConfigManager.getLastSelectedNetwork());
        assertEquals(67, mWifiConfigManager.getLastSelectedTimeStamp());

        // Now disable the network and ensure that the last selected flag is cleared.
        assertTrue(mWifiConfigManager.disableNetwork(
                result.getNetworkId(), TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertEquals(
                WifiConfiguration.INVALID_NETWORK_ID, mWifiConfigManager.getLastSelectedNetwork());

        // Enable it again and remove the network to ensure that the last selected flag was cleared.
        assertTrue(mWifiConfigManager.enableNetwork(
                result.getNetworkId(), true, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertEquals(result.getNetworkId(), mWifiConfigManager.getLastSelectedNetwork());
        assertEquals(openNetwork.getProfileKey(),
                mWifiConfigManager.getLastSelectedNetworkConfigKey());

        assertTrue(mWifiConfigManager.removeNetwork(
                result.getNetworkId(), TEST_CREATOR_UID, TEST_CREATOR_NAME));
        assertEquals(
                WifiConfiguration.INVALID_NETWORK_ID, mWifiConfigManager.getLastSelectedNetwork());
    }

    /**
     * Verifies that all the networks for the provided app is removed when
     * {@link WifiConfigManager#removeNetworksForApp(ApplicationInfo)} is invoked.
     */
    @Test
    public void testRemoveNetworksForApp() throws Exception {
        when(mPackageManager.getNameForUid(TEST_CREATOR_UID)).thenReturn(TEST_CREATOR_NAME);

        verifyRemoveNetworksForApp();
    }

    /**
     * Verifies that all the networks for the provided app is removed when
     * {@link WifiConfigManager#removeNetworksForApp(ApplicationInfo)} is invoked.
     */
    @Test
    public void testRemoveNetworksForAppUsingSharedUid() throws Exception {
        when(mPackageManager.getNameForUid(TEST_CREATOR_UID))
                .thenReturn(TEST_CREATOR_NAME + ":" + TEST_CREATOR_UID);

        verifyRemoveNetworksForApp();
    }

    /**
     * Verifies that all the networks for the provided user is removed when
     * {@link WifiConfigManager#removeNetworksForUser(int)} is invoked.
     */
    @Test
    public void testRemoveNetworksForUser() throws Exception {
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createPskNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createWepNetwork());

        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        assertEquals(3, mWifiConfigManager.removeNetworksForUser(TEST_DEFAULT_USER).size());

        // Ensure all the networks are removed now.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
    }

    private void verifySetUserConnectChoice(int preferredNetId, int notPreferredNetId) {
        assertTrue(mWifiConfigManager.setNetworkCandidateScanResult(
                notPreferredNetId, mock(ScanResult.class), 54, mock(SecurityParams.class)));
        assertTrue(mWifiConfigManager.updateNetworkAfterConnect(preferredNetId, false,
                true, TEST_RSSI));
        WifiConfiguration preferred = mWifiConfigManager.getConfiguredNetwork(preferredNetId);
        assertNull(preferred.getNetworkSelectionStatus().getConnectChoice());
        WifiConfiguration notPreferred = mWifiConfigManager.getConfiguredNetwork(notPreferredNetId);
        assertEquals(preferred.getProfileKey(),
                notPreferred.getNetworkSelectionStatus().getConnectChoice());
        assertEquals(TEST_RSSI, notPreferred.getNetworkSelectionStatus().getConnectChoiceRssi());
    }

    /**
     * Verifies that the connect choice is removed from all networks when
     * {@link WifiConfigManager#removeNetwork(int, int)} is invoked.
     */
    @Test
    public void testRemoveNetworkRemovesConnectChoice() throws Exception {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);
        mWifiConfigManager.addOnNetworkUpdateListener(mListener);
        // Set connect choice of network 2 over network 1.
        verifySetUserConnectChoice(network2.networkId, network1.networkId);
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();

        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(network1.networkId);
        assertEquals(
                network2.getProfileKey(),
                retrievedNetwork.getNetworkSelectionStatus().getConnectChoice());

        // Remove network 3 and ensure that the connect choice on network 1 is not removed.
        assertTrue(mWifiConfigManager.removeNetwork(
                network3.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(network1.networkId);
        assertEquals(
                network2.getProfileKey(),
                retrievedNetwork.getNetworkSelectionStatus().getConnectChoice());

        // Now remove network 2 and ensure that the connect choice on network 1 is removed..
        assertTrue(mWifiConfigManager.removeNetwork(
                network2.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));
        retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(network1.networkId);
        assertNotEquals(
                network2.getProfileKey(),
                retrievedNetwork.getNetworkSelectionStatus().getConnectChoice());
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();

        // This should have triggered 2 buffered writes. 1 for setting the connect choice, 1 for
        // clearing it after network removal.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, times(2)).write(true);
        verify(mListener, times(2)).onConnectChoiceRemoved(anyString());
        verify(mWifiMetrics, atLeast(2)).wifiConfigStored(anyInt());
    }

    /**
     * Disabling autojoin should clear associated connect choice links.
     */
    @Test
    public void testDisableAutojoinRemovesConnectChoice() throws Exception {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);
        mWifiConfigManager.addOnNetworkUpdateListener(mListener);

        // Set connect choice of network 2 over network 1 and network 3.
        verifySetUserConnectChoice(network2.networkId, network1.networkId);
        verifySetUserConnectChoice(network2.networkId, network3.networkId);

        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(network3.networkId);
        assertEquals(
                network2.getProfileKey(),
                retrievedNetwork.getNetworkSelectionStatus().getConnectChoice());

        // Disable network 3
        assertTrue(mWifiConfigManager.allowAutojoin(network3.networkId, false));
        // Ensure that the connect choice on network 3 is removed.
        retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(network3.networkId);
        assertEquals(
                null,
                retrievedNetwork.getNetworkSelectionStatus().getConnectChoice());
        // Ensure that the connect choice on network 1 is not removed.
        retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(network1.networkId);
        assertEquals(
                network2.getProfileKey(),
                retrievedNetwork.getNetworkSelectionStatus().getConnectChoice());

        verify(mListener).onConnectChoiceRemoved(network3.getProfileKey());
    }

    /**
     * Verifies that all the ephemeral and passpoint networks are removed when
     * {@link WifiConfigManager#removeAllEphemeralOrPasspointConfiguredNetworks()} is invoked.
     */
    @Test
    public void testRemoveAllEphemeralOrPasspointConfiguredNetworks() throws Exception {
        WifiConfiguration savedOpenNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration ephemeralNetwork = WifiConfigurationTestUtil.createEphemeralNetwork();
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        WifiConfiguration suggestionNetwork = WifiConfigurationTestUtil.createEphemeralNetwork();
        suggestionNetwork.fromWifiNetworkSuggestion = true;

        verifyAddNetworkToWifiConfigManager(savedOpenNetwork);
        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNetwork);
        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);
        verifyAddEphemeralNetworkToWifiConfigManager(suggestionNetwork);

        mWifiConfigManager.addOnNetworkUpdateListener(mListener);

        List<WifiConfiguration> expectedConfigsBeforeRemove = new ArrayList<WifiConfiguration>(
                Arrays.asList(savedOpenNetwork, ephemeralNetwork, passpointNetwork,
                        suggestionNetwork));
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                expectedConfigsBeforeRemove,
                mWifiConfigManager.getConfiguredNetworksWithPasswords());

        assertTrue(mWifiConfigManager.removeAllEphemeralOrPasspointConfiguredNetworks());

        List<WifiConfiguration> expectedConfigsAfterRemove = new ArrayList<WifiConfiguration>() {{
                add(savedOpenNetwork);
            }};
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                expectedConfigsAfterRemove, mWifiConfigManager.getConfiguredNetworks());

        // No more ephemeral or passpoint networks to remove now.
        assertFalse(mWifiConfigManager.removeAllEphemeralOrPasspointConfiguredNetworks());

        // Suggestion and passpoint network will not trigger conncet choice remove.
        verify(mListener).onConnectChoiceRemoved(ephemeralNetwork.getProfileKey());
    }

    /**
     * Verifies that Passpoint network corresponding with given config key (FQDN) is removed.
     *
     * @throws Exception
     */
    @Test
    public void testRemovePasspointConfiguredNetwork() throws Exception {
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);

        assertTrue(mWifiConfigManager.removePasspointConfiguredNetwork(
                passpointNetwork.getProfileKey()));
    }

    /**
     * Verifies that suggested network corresponding with given config key is removed.
     *
     * @throws Exception
     */
    @Test
    public void testRemoveSuggestionConfiguredNetwork() throws Exception {
        WifiConfiguration suggestedNetwork = WifiConfigurationTestUtil.createEphemeralNetwork();
        suggestedNetwork.creatorUid = TEST_CREATOR_UID;
        suggestedNetwork.fromWifiNetworkSuggestion = true;
        verifyAddEphemeralNetworkToWifiConfigManager(suggestedNetwork);

        assertTrue(mWifiConfigManager.removeSuggestionConfiguredNetwork(suggestedNetwork));
    }

    /**
     * Verifies that the modification of a single network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} and ensures that any
     * updates to the network config in
     * {@link WifiKeyStore#updateNetworkKeys(WifiConfiguration, WifiConfiguration)} is reflected
     * in the internal database.
     */
    @Test
    public void testUpdateSingleNetworkWithKeysUpdate() {
        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        network.enterpriseConfig =
                WifiConfigurationTestUtil.createPEAPWifiEnterpriseConfigWithGTCPhase2();
        verifyAddNetworkToWifiConfigManager(network);

        // Now verify that network configurations match before we make any change.
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network,
                mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));

        // Modify the network ca_cert field in updateNetworkKeys method during a network
        // config update.
        final String newCaCertAlias = "test";
        assertNotEquals(newCaCertAlias, network.enterpriseConfig.getCaCertificateAlias());

        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiConfiguration newConfig, WifiConfiguration existingConfig) {
                newConfig.enterpriseConfig.setCaCertificateAlias(newCaCertAlias);
                return true;
            }
        }).when(mWifiKeyStore).updateNetworkKeys(
                any(WifiConfiguration.class), any(WifiConfiguration.class));

        verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(network);

        // Now verify that the keys update is reflected in the configuration fetched from internal
        // db.
        network.enterpriseConfig.setCaCertificateAlias(newCaCertAlias);
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network,
                mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));
    }

    /**
     * Verifies that the dump method prints out all the saved network details with passwords masked.
     * {@link WifiConfigManager#dump(FileDescriptor, PrintWriter, String[])}.
     */
    @Test
    public void testDump() {
        WifiConfiguration pskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration eapNetwork = WifiConfigurationTestUtil.createEapNetwork();
        eapNetwork.enterpriseConfig.setPassword("blah");

        verifyAddNetworkToWifiConfigManager(pskNetwork);
        verifyAddNetworkToWifiConfigManager(eapNetwork);

        StringWriter stringWriter = new StringWriter();
        mWifiConfigManager.dump(
                new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        String dumpString = stringWriter.toString();

        // Ensure that the network SSIDs were dumped out.
        assertTrue(dumpString.contains(pskNetwork.SSID));
        assertTrue(dumpString.contains(eapNetwork.SSID));

        // Ensure that the network passwords were not dumped out.
        assertFalse(dumpString.contains(pskNetwork.preSharedKey));
        assertFalse(dumpString.contains(eapNetwork.enterpriseConfig.getPassword()));
    }

    /**
     * Verifies the ordering of network list generated using
     * {@link WifiConfigManager#retrieveHiddenNetworkList()}.
     */
    @Test
    public void testRetrieveHiddenList() {
        // Create and add 3 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createWepHiddenNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskHiddenNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        network3.allowAutojoin = false;
        verifyAddNetworkToWifiConfigManager(network1);
        verifyAddNetworkToWifiConfigManager(network2);
        verifyAddNetworkToWifiConfigManager(network3);
        mWifiConfigManager.updateNetworkAfterConnect(network3.networkId, false, false, TEST_RSSI);

        // Now set scan results of network2 to set the corresponding
        // {@link NetworkSelectionStatus#mSeenInLastQualifiedNetworkSelection} field.
        assertTrue(mWifiConfigManager.setNetworkCandidateScanResult(network2.networkId,
                createScanDetailForNetwork(network2).getScanResult(), 54,
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK)));

        // Retrieve the hidden network list & verify the order of the networks returned.
        List<WifiScanner.ScanSettings.HiddenNetwork> hiddenNetworks =
                mWifiConfigManager.retrieveHiddenNetworkList(false);
        assertEquals(4, hiddenNetworks.size());
        assertEquals(network3.SSID, hiddenNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, hiddenNetworks.get(1).ssid); // Possible original SSID
        assertEquals(network2.SSID, hiddenNetworks.get(2).ssid);
        assertEquals(network1.SSID, hiddenNetworks.get(3).ssid);

        hiddenNetworks = mWifiConfigManager.retrieveHiddenNetworkList(true);
        assertEquals(3, hiddenNetworks.size());
        assertEquals(network2.SSID, hiddenNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, hiddenNetworks.get(1).ssid); // Possible original SSID
        assertEquals(network1.SSID, hiddenNetworks.get(2).ssid);
    }

    /**
     * Verifies the addition of network configurations using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} with same SSID and
     * default key mgmt does not add duplicate network configs.
     */
    @Test
    public void testAddMultipleNetworksWithSameSSIDAndDefaultKeyMgmt() {
        final String ssid = "\"test_blah\"";
        // Add a network with the above SSID and default key mgmt and ensure it was added
        // successfully.
        WifiConfiguration network1 = new WifiConfiguration();
        network1.SSID = ssid;
        network1.convertLegacyFieldsToSecurityParamsIfNeeded();
        NetworkUpdateResult result = addNetworkToWifiConfigManager(network1);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network1, retrievedNetworks.get(0));

        // Now add a second network with the same SSID and default key mgmt and ensure that it
        // didn't add a new duplicate network.
        WifiConfiguration network2 = new WifiConfiguration();
        network2.SSID = ssid;
        network2.convertLegacyFieldsToSecurityParamsIfNeeded();
        result = addNetworkToWifiConfigManager(network2);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertFalse(result.isNewNetwork());

        retrievedNetworks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network2, retrievedNetworks.get(0));
    }

    /**
     * Verifies the addition of network configurations using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} with same SSID and
     * different key mgmt should add different network configs.
     */
    @Test
    public void testAddMultipleNetworksWithSameSSIDAndDifferentKeyMgmt() {
        final String ssid = "\"test_blah\"";
        // Add a network with the above SSID and WPA_PSK key mgmt and ensure it was added
        // successfully.
        WifiConfiguration network1 = new WifiConfiguration();
        network1.SSID = ssid;
        network1.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        network1.preSharedKey = "\"test_blah\"";
        NetworkUpdateResult result = addNetworkToWifiConfigManager(network1);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network1, retrievedNetworks.get(0));

        // Now add a second network with the same SSID and NONE key mgmt and ensure that it
        // does add a new network.
        WifiConfiguration network2 = new WifiConfiguration();
        network2.SSID = ssid;
        network2.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        result = addNetworkToWifiConfigManager(network2);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());

        retrievedNetworks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(2, retrievedNetworks.size());
        List<WifiConfiguration> networks = Arrays.asList(network1, network2);
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Verifies that adding a network with a proxy, without having permission OVERRIDE_WIFI_CONFIG,
     * holding device policy, or profile owner policy fails.
     */
    @Test
    public void testAddNetworkWithProxyFails() {
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                false, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithStaticProxy(),
                false, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
    }

    /**
     * Verifies that adding a network with a PAC or STATIC proxy with permission
     * OVERRIDE_WIFI_CONFIG is successful
     */
    @Test
    public void testAddNetworkWithProxyWithConfOverride() {
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true,  // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true,  // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithStaticProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
    }

    /**
     * Verifies that adding a network with a PAC or STATIC proxy, while holding policy
     * {@link DeviceAdminInfo.USES_POLICY_PROFILE_OWNER} is successful
     */
    @Test
    public void testAddNetworkWithProxyAsProfileOwner() {
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false,  // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false,  // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithStaticProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
    }
    /**
     * Verifies that adding a network with a PAC or STATIC proxy, while holding policy
     * {@link DeviceAdminInfo.USES_POLICY_DEVICE_OWNER} is successful
     */
    @Test
    public void testAddNetworkWithProxyAsDeviceOwner() {
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false,  // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false,  // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithStaticProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
    }

    /**
     * Verifies that adding a network with a PAC or STATIC proxy, while having the
     * {@link android.Manifest.permission#NETWORK_MANAGED_PROVISIONING} permission is successful
     */
    @Test
    public void testAddNetworkWithProxyWithNetworkManagedPermission() {
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withNetworkSetupWizard
                true, // withNetworkManagedProvisioning
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false,  // withNetworkSettings
                false, // withNetworkSetupWizard
                true, // withNetworkManagedProvisioning
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithStaticProxy(),
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
    }

    /**
     * Verifies that updating a network (that has no proxy) and adding a PAC or STATIC proxy fails
     * without being able to override configs, or holding Device or Profile owner policies.
     */
    @Test
    public void testUpdateNetworkAddProxyFails() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(network);
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                false, // assertSuccess
                result.getNetworkId()); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithStaticProxy(),
                false, // assertSuccess
                result.getNetworkId()); // Update networkID
    }
    /**
     * Verifies that updating a network and adding a proxy is successful in the cases where app can
     * override configs, holds policy {@link DeviceAdminInfo.USES_POLICY_PROFILE_OWNER},
     * and holds policy {@link DeviceAdminInfo.USES_POLICY_DEVICE_OWNER}, and that it fails
     * otherwise.
     */
    @Test
    public void testUpdateNetworkAddProxyWithPermissionAndSystem() {
        // Testing updating network with uid permission OVERRIDE_WIFI_CONFIG
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        NetworkUpdateResult result = addNetworkToWifiConfigManager(network, TEST_CREATOR_UID);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        result = addNetworkToWifiConfigManager(network, TEST_CREATOR_UID);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withNetworkSetupWizard
                false, false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Testing updating network with proxy while holding Profile Owner policy
        network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        result = addNetworkToWifiConfigManager(network, TEST_NO_PERM_UID);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Testing updating network with proxy while holding Device Owner Policy
        network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        result = addNetworkToWifiConfigManager(network, TEST_NO_PERM_UID);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy(),
                true, // assertSuccess
                result.getNetworkId()); // Update networkID
    }

    /**
     * Verifies that updating a network that has a proxy without changing the proxy, can succeed
     * without proxy specific permissions.
     */
    @Test
    public void testUpdateNetworkUnchangedProxy() {
        IpConfiguration ipConf = WifiConfigurationTestUtil.createDHCPIpConfigurationWithPacProxy();
        // First create a WifiConfiguration with proxy
        NetworkUpdateResult result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                        false, // withNetworkSettings
                        true, // withProfileOwnerPolicy
                        false, // withDeviceOwnerPolicy
                        ipConf,
                        true, // assertSuccess
                        WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        // Update the network while using the same ipConf, and no proxy specific permissions
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                        false, // withNetworkSettings
                        false, // withProfileOwnerPolicy
                        false, // withDeviceOwnerPolicy
                        ipConf,
                        true, // assertSuccess
                        result.getNetworkId()); // Update networkID
    }

    /**
     * Verifies that updating a network with a different proxy succeeds in the cases where app can
     * override configs, holds policy {@link DeviceAdminInfo.USES_POLICY_PROFILE_OWNER},
     * and holds policy {@link DeviceAdminInfo.USES_POLICY_DEVICE_OWNER}, and that it fails
     * otherwise.
     */
    @Test
    public void testUpdateNetworkDifferentProxy() {
        // Create two proxy configurations of the same type, but different values
        IpConfiguration ipConf1 =
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithSpecificProxy(
                        WifiConfigurationTestUtil.STATIC_PROXY_SETTING,
                        TEST_STATIC_PROXY_HOST_1,
                        TEST_STATIC_PROXY_PORT_1,
                        TEST_STATIC_PROXY_EXCLUSION_LIST_1,
                        TEST_PAC_PROXY_LOCATION_1);
        IpConfiguration ipConf2 =
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithSpecificProxy(
                        WifiConfigurationTestUtil.STATIC_PROXY_SETTING,
                        TEST_STATIC_PROXY_HOST_2,
                        TEST_STATIC_PROXY_PORT_2,
                        TEST_STATIC_PROXY_EXCLUSION_LIST_2,
                        TEST_PAC_PROXY_LOCATION_2);

        // Update with Conf Override
        NetworkUpdateResult result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf2,
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Update as Device Owner
        result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                ipConf2,
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Update as Profile Owner
        result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf2,
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Update with no permissions (should fail)
        result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf2,
                false, // assertSuccess
                result.getNetworkId()); // Update networkID
    }
    /**
     * Verifies that updating a network removing its proxy succeeds in the cases where app can
     * override configs, holds policy {@link DeviceAdminInfo.USES_POLICY_PROFILE_OWNER},
     * and holds policy {@link DeviceAdminInfo.USES_POLICY_DEVICE_OWNER}, and that it fails
     * otherwise.
     */
    @Test
    public void testUpdateNetworkRemoveProxy() {
        // Create two different IP configurations, one with a proxy and another without.
        IpConfiguration ipConf1 =
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithSpecificProxy(
                        WifiConfigurationTestUtil.STATIC_PROXY_SETTING,
                        TEST_STATIC_PROXY_HOST_1,
                        TEST_STATIC_PROXY_PORT_1,
                        TEST_STATIC_PROXY_EXCLUSION_LIST_1,
                        TEST_PAC_PROXY_LOCATION_1);
        IpConfiguration ipConf2 =
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithSpecificProxy(
                        WifiConfigurationTestUtil.NONE_PROXY_SETTING,
                        TEST_STATIC_PROXY_HOST_2,
                        TEST_STATIC_PROXY_PORT_2,
                        TEST_STATIC_PROXY_EXCLUSION_LIST_2,
                        TEST_PAC_PROXY_LOCATION_2);

        // Update with Conf Override
        NetworkUpdateResult result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                true, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf2,
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Update as Device Owner
        result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                true, // withDeviceOwnerPolicy
                ipConf2,
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Update as Profile Owner
        result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf2,
                true, // assertSuccess
                result.getNetworkId()); // Update networkID

        // Update with no permissions (should fail)
        result = verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                true, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf1,
                true, // assertSuccess
                WifiConfiguration.INVALID_NETWORK_ID); // Update networkID
        verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
                false, // withNetworkSettings
                false, // withProfileOwnerPolicy
                false, // withDeviceOwnerPolicy
                ipConf2,
                false, // assertSuccess
                result.getNetworkId()); // Update networkID
    }

    /**
     * Verifies that the app specified BSSID is converted and saved in lower case.
     */
    @Test
    public void testAppSpecifiedBssidIsSavedInLowerCase() {
        final String bssid = "0A:08:5C:BB:89:6D"; // upper case
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.BSSID = bssid;

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        WifiConfiguration retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(
                result.getNetworkId());

        assertNotEquals(retrievedNetwork.BSSID, bssid);
        assertEquals(retrievedNetwork.BSSID, bssid.toLowerCase());
    }

    /**
     * Verifies that the method resetSimNetworks updates SIM presence status and SIM configs.
     */
    @Test
    public void testResetSimNetworks() {
        String expectedIdentity = "13214561234567890@wlan.mnc456.mcc321.3gppnetwork.org";
        when(mDataTelephonyManager.getSubscriberId()).thenReturn("3214561234567890");
        when(mDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_LOADED);
        when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        List<SubscriptionInfo> subList = new ArrayList<>() {{
                add(mock(SubscriptionInfo.class));
            }};
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(subList);
        when(mSubscriptionManager.getActiveSubscriptionIdList())
                .thenReturn(new int[]{DATA_SUBID});

        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration simNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        WifiConfiguration peapSimNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.SIM);
        network.enterpriseConfig.setIdentity("identity1");
        network.enterpriseConfig.setAnonymousIdentity("anonymous_identity1");
        simNetwork.enterpriseConfig.setIdentity("identity2");
        simNetwork.enterpriseConfig.setAnonymousIdentity("anonymous_identity2");
        peapSimNetwork.enterpriseConfig.setIdentity("identity3");
        peapSimNetwork.enterpriseConfig.setAnonymousIdentity("anonymous_identity3");
        verifyAddNetworkToWifiConfigManager(network);
        verifyAddNetworkToWifiConfigManager(simNetwork);
        verifyAddNetworkToWifiConfigManager(peapSimNetwork);
        MockitoSession mockSession = ExtendedMockito.mockitoSession()
                .mockStatic(SubscriptionManager.class)
                .startMocking();
        when(SubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(DATA_SUBID);
        when(SubscriptionManager.isValidSubscriptionId(anyInt())).thenReturn(true);

        // SIM was removed, resetting SIM Networks
        mWifiConfigManager.resetSimNetworks();

        // Verify SIM configs are reset and non-SIM configs are not changed.
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network,
                mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));
        WifiConfiguration retrievedSimNetwork =
                mWifiConfigManager.getConfiguredNetwork(simNetwork.networkId);
        assertEquals("", retrievedSimNetwork.enterpriseConfig.getAnonymousIdentity());
        assertEquals("", retrievedSimNetwork.enterpriseConfig.getIdentity());
        WifiConfiguration retrievedPeapSimNetwork =
                mWifiConfigManager.getConfiguredNetwork(peapSimNetwork.networkId);
        assertEquals(expectedIdentity, retrievedPeapSimNetwork.enterpriseConfig.getIdentity());
        assertNotEquals("", retrievedPeapSimNetwork.enterpriseConfig.getAnonymousIdentity());

        mockSession.finishMocking();
    }

    /**
     * {@link WifiConfigManager#resetSimNetworks()} should reset all non-PEAP SIM networks, no
     * matter if {@link WifiCarrierInfoManager#getSimIdentity()} returns null or not.
     */
    @Test
    public void testResetSimNetworks_getSimIdentityNull_shouldResetAllNonPeapSimIdentities() {
        when(mDataTelephonyManager.getSubscriberId()).thenReturn("");
        when(mDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_LOADED);
        when(mDataTelephonyManager.getSimOperator()).thenReturn("");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        List<SubscriptionInfo> subList = new ArrayList<>() {{
                add(mock(SubscriptionInfo.class));
            }};
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(subList);
        when(mSubscriptionManager.getActiveSubscriptionIdList())
                .thenReturn(new int[]{DATA_SUBID});

        WifiConfiguration peapSimNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.SIM);
        peapSimNetwork.enterpriseConfig.setIdentity("identity_peap");
        peapSimNetwork.enterpriseConfig.setAnonymousIdentity("anonymous_identity_peap");
        verifyAddNetworkToWifiConfigManager(peapSimNetwork);

        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        network.enterpriseConfig.setIdentity("identity");
        network.enterpriseConfig.setAnonymousIdentity("anonymous_identity");
        verifyAddNetworkToWifiConfigManager(network);

        WifiConfiguration simNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        simNetwork.enterpriseConfig.setIdentity("identity_eap_sim");
        simNetwork.enterpriseConfig.setAnonymousIdentity("anonymous_identity_eap_sim");
        verifyAddNetworkToWifiConfigManager(simNetwork);
        MockitoSession mockSession = ExtendedMockito.mockitoSession()
                .mockStatic(SubscriptionManager.class)
                .startMocking();
        when(SubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(DATA_SUBID);
        when(SubscriptionManager.isValidSubscriptionId(anyInt())).thenReturn(true);

        // SIM was removed, resetting SIM Networks
        mWifiConfigManager.resetSimNetworks();

        // EAP non-SIM network should be unchanged
        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network, mWifiConfigManager.getConfiguredNetworkWithPassword(network.networkId));
        // EAP-SIM network should have identity & anonymous identity reset
        WifiConfiguration retrievedSimNetwork =
                mWifiConfigManager.getConfiguredNetwork(simNetwork.networkId);
        assertEquals("", retrievedSimNetwork.enterpriseConfig.getAnonymousIdentity());
        assertEquals("", retrievedSimNetwork.enterpriseConfig.getIdentity());
        // PEAP network should have unchanged identity & anonymous identity
        WifiConfiguration retrievedPeapSimNetwork =
                mWifiConfigManager.getConfiguredNetwork(peapSimNetwork.networkId);
        assertEquals("identity_peap", retrievedPeapSimNetwork.enterpriseConfig.getIdentity());
        assertEquals("anonymous_identity_peap",
                retrievedPeapSimNetwork.enterpriseConfig.getAnonymousIdentity());

        mockSession.finishMocking();
    }

    /**
     * Verifies that SIM configs are reset on {@link WifiConfigManager#loadFromStore()}.
     */
    @Test
    public void testLoadFromStoreResetsSimIdentity() {
        when(mDataTelephonyManager.getSubscriberId()).thenReturn("3214561234567890");
        when(mDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_LOADED);
        when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);

        WifiConfiguration simNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        simNetwork.enterpriseConfig.setIdentity("identity");
        simNetwork.enterpriseConfig.setAnonymousIdentity("anonymous_identity");

        WifiConfiguration peapSimNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE);
        peapSimNetwork.enterpriseConfig.setIdentity("identity");
        peapSimNetwork.enterpriseConfig.setAnonymousIdentity("anonymous_identity");

        // Set up the store data.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(simNetwork);
                add(peapSimNetwork);
            }
        };
        setupStoreDataForRead(sharedNetworks, new ArrayList<>());

        // read from store now
        assertTrue(mWifiConfigManager.loadFromStore());

        // assert that the expected identities are reset
        WifiConfiguration retrievedSimNetwork =
                mWifiConfigManager.getConfiguredNetwork(simNetwork.networkId);
        assertEquals("", retrievedSimNetwork.enterpriseConfig.getIdentity());
        assertEquals("", retrievedSimNetwork.enterpriseConfig.getAnonymousIdentity());

        WifiConfiguration retrievedPeapNetwork =
                mWifiConfigManager.getConfiguredNetwork(peapSimNetwork.networkId);
        assertEquals("identity", retrievedPeapNetwork.enterpriseConfig.getIdentity());
        assertEquals("anonymous_identity",
                retrievedPeapNetwork.enterpriseConfig.getAnonymousIdentity());
    }

    /**
     * Verifies that SIM configs are reset on {@link WifiConfigManager#loadFromStore()}.
     */
    @Test
    public void testLoadFromStoreIgnoresMalformedNetworks() {
        WifiConfiguration validNetwork = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration invalidNetwork = WifiConfigurationTestUtil.createPskNetwork();
        // Hex SSID larger than 32 chars.
        invalidNetwork.SSID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Set up the store data.
        List<WifiConfiguration> sharedNetworks = Arrays.asList(validNetwork, invalidNetwork);
        setupStoreDataForRead(sharedNetworks, new ArrayList<>());

        // read from store now
        assertTrue(mWifiConfigManager.loadFromStore());

        // Ensure that the invalid network has been ignored.
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                Arrays.asList(validNetwork), retrievedNetworks);
    }

    /**
     * Verifies the deletion of ephemeral network using
     * {@link WifiConfigManager#userTemporarilyDisabledNetwork(String)}.
     */
    @Test
    public void testUserDisableNetwork() throws Exception {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);
        verifyAddNetworkToWifiConfigManager(openNetwork);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        verifyExpiryOfTimeout(openNetwork);
    }

    /**
     * Verifies the disconnection of Passpoint network using
     * {@link WifiConfigManager#userTemporarilyDisabledNetwork(String)}.
     */
    @Test
    public void testDisablePasspointNetwork() throws Exception {
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();

        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);

        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(passpointNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        verifyExpiryOfTimeout(passpointNetwork);
    }

    /**
     * Verifies the disconnection of Passpoint network using
     * {@link WifiConfigManager#userTemporarilyDisabledNetwork(String)} and ensures that any user
     * choice set over other networks is removed.
     */
    @Test
    public void testDisablePasspointNetworkRemovesUserChoice() throws Exception {
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        verifyAddNetworkToWifiConfigManager(savedNetwork);
        verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork);

        mWifiConfigManager.addOnNetworkUpdateListener(mListener);
        // Set connect choice of passpoint network over saved network.
        verifySetUserConnectChoice(passpointNetwork.networkId, savedNetwork.networkId);

        WifiConfiguration retrievedSavedNetwork =
                mWifiConfigManager.getConfiguredNetwork(savedNetwork.networkId);
        assertEquals(
                passpointNetwork.getProfileKey(),
                retrievedSavedNetwork.getNetworkSelectionStatus().getConnectChoice());

        // Disable the passpoint network & ensure the user choice is now removed from saved network.
        mWifiConfigManager.userTemporarilyDisabledNetwork(passpointNetwork.FQDN, TEST_UPDATE_UID);

        retrievedSavedNetwork = mWifiConfigManager.getConfiguredNetwork(savedNetwork.networkId);
        assertNull(retrievedSavedNetwork.getNetworkSelectionStatus().getConnectChoice());
        verify(mListener).onConnectChoiceRemoved(passpointNetwork.getProfileKey());
    }

    @Test
    public void testUserEnableDisabledNetwork() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);
        verifyAddNetworkToWifiConfigManager(openNetwork);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        mWifiConfigManager.userTemporarilyDisabledNetwork(openNetwork.SSID, TEST_UPDATE_UID);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(openNetwork.SSID));
        mWifiConfigManager.userEnabledNetwork(retrievedNetworks.get(0).networkId);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(openNetwork.SSID));
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_DISCONNECT_WIFI),
                anyInt());
    }

    @Test
    public void testUserAddOrUpdateSavedNetworkEnableNetwork() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);
        mWifiConfigManager.userTemporarilyDisabledNetwork(openNetwork.SSID, TEST_UPDATE_UID);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(openNetwork.SSID));

        verifyAddNetworkToWifiConfigManager(openNetwork);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(openNetwork.SSID));
    }

    @Test
    public void testUserAddPasspointNetworkEnableNetwork() {
        WifiConfiguration passpointNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(passpointNetwork);
        mWifiConfigManager.userTemporarilyDisabledNetwork(passpointNetwork.FQDN, TEST_UPDATE_UID);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(passpointNetwork.FQDN));
        // Add new passpoint network will enable the network.
        NetworkUpdateResult result = addNetworkToWifiConfigManager(passpointNetwork);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(passpointNetwork.FQDN));

        mWifiConfigManager.userTemporarilyDisabledNetwork(passpointNetwork.FQDN, TEST_UPDATE_UID);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(passpointNetwork.FQDN));
        // Update a existing passpoint network will not enable network.
        result = addNetworkToWifiConfigManager(passpointNetwork);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertFalse(result.isNewNetwork());
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(passpointNetwork.FQDN));
    }

    private void verifyExpiryOfTimeout(WifiConfiguration config) {
        // Disable the ephemeral network.
        long disableTimeMs = 546643L;
        long currentTimeMs = disableTimeMs;
        when(mClock.getWallClockMillis()).thenReturn(currentTimeMs);
        String network = config.isPasspoint() ? config.FQDN : config.SSID;
        mWifiConfigManager.userTemporarilyDisabledNetwork(network, TEST_UPDATE_UID);
        // Before timer is triggered, timer will not expiry will enable network.
        currentTimeMs = disableTimeMs
                + WifiConfigManager.USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS + 1;
        when(mClock.getWallClockMillis()).thenReturn(currentTimeMs);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network));
        for (int i = 0; i < WifiConfigManager.SCAN_RESULT_MISSING_COUNT_THRESHOLD; i++) {
            mWifiConfigManager.updateUserDisabledList(new ArrayList<>());
        }
        // Before the expiry of timeout.
        currentTimeMs = currentTimeMs
                + WifiConfigManager.USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS - 1;
        when(mClock.getWallClockMillis()).thenReturn(currentTimeMs);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network));

        // After the expiry of timeout.
        currentTimeMs = currentTimeMs
                + WifiConfigManager.USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS + 1;
        when(mClock.getWallClockMillis()).thenReturn(currentTimeMs);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network));
    }

    @Test
    public void testMaxDisableDurationEnableDisabledNetwork() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);
        verifyAddNetworkToWifiConfigManager(openNetwork);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        // Disable the network.
        long maxDisableDuration = ALL_NON_CARRIER_MERGED_WIFI_MAX_DISABLE_DURATION_MINUTES
                * 60 * 1000;
        when(mClock.getWallClockMillis()).thenReturn(0L);
        String network = openNetwork.SSID;
        mWifiConfigManager.userTemporarilyDisabledNetwork(network, TEST_UPDATE_UID);

        // Verify that the network is disabled.
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network));

        // Before the maxDisableDuration, the network should still be disabled.
        when(mClock.getWallClockMillis()).thenReturn(maxDisableDuration);
        assertTrue(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network));

        // After the maxDisableDuration, the network should be enabled.
        when(mClock.getWallClockMillis()).thenReturn(maxDisableDuration + 1);
        assertFalse(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network));
    }

    /**
     * Verify that when startRestrictingAutoJoinToSubscriptionId is called, all
     * non-carrier-merged networks are disabled for a duration, and non-carrier-merged networks
     * visible at the time of API call are disabled a longer duration, until they disappear from
     * scan results for sufficiently long.
     */
    @Test
    public void testStartTemporarilyDisablingAllNonCarrierMergedWifi() {
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfiguration visibleNetwork = retrievedNetworks.get(0);
        ScanDetail scanDetail = createScanDetailForNetwork(visibleNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);
        WifiConfiguration otherNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        // verify both networks are disabled at the start
        when(mClock.getWallClockMillis()).thenReturn(0L);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mWifiConfigManager.startRestrictingAutoJoinToSubscriptionId(5);
        mWifiConfigManager.updateUserDisabledList(new ArrayList<String>());
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(visibleNetwork));
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(otherNetwork));

        // the network visible at the start of the API call should still be disabled, but the
        // other non-carrier-merged network should now be free to connect
        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                ALL_NON_CARRIER_MERGED_WIFI_MIN_DISABLE_DURATION_MINUTES * 60 * 1000 + 1L);
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(visibleNetwork));
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(otherNetwork));

        // all networks should be clear to connect by now.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                WifiConfigManager.USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS + 1);
        when(mClock.getWallClockMillis()).thenReturn(
                WifiConfigManager.USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS + 1);
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                visibleNetwork));
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(otherNetwork));
    }

    /**
     * Verify when there that non carrier merged networks disabled due to carrier selection gets
     * re-enabled when cellular data becomes unavailable.
     */
    @Test
    public void testNoCellularDataEnabledNonCarrierMergedWifi() {
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfiguration visibleNetwork = retrievedNetworks.get(0);
        ScanDetail scanDetail = createScanDetailForNetwork(visibleNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);

        // verify the network is disabled after startRestrictingAutoJoinToSubscriptionId is called
        when(mClock.getWallClockMillis()).thenReturn(0L);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mWifiConfigManager.startRestrictingAutoJoinToSubscriptionId(5);
        mWifiConfigManager.updateUserDisabledList(new ArrayList<String>());
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(visibleNetwork));

        mWifiConfigManager.onCellularConnectivityChanged(WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE);
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                visibleNetwork));
    }

    /**
     * Verify that startRestrictingAutoJoinToSubscriptionId disables all passpoint networks
     * with the same FQDN as the visible passpoint network, even if the SSID is different.
     */
    @Test
    public void testStartTemporarilyDisablingAllNonCarrierMergedWifiPasspointFqdn()
            throws Exception {
        int testSubscriptionId = 5;
        WifiConfiguration passpointNetwork_1 = WifiConfigurationTestUtil.createPasspointNetwork();
        WifiConfiguration passpointNetwork_2 = WifiConfigurationTestUtil.createPasspointNetwork();
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        // Verify the 2 passpoint network have the same FQDN, but different SSID.
        assertEquals(passpointNetwork_1.FQDN, passpointNetwork_2.FQDN);
        assertNotEquals(passpointNetwork_1.SSID, passpointNetwork_2.SSID);
        NetworkUpdateResult result =
                verifyAddPasspointNetworkToWifiConfigManager(passpointNetwork_1);

        // Make sure the passpointNetwork_1 is seen in a scan.
        WifiConfiguration visibleNetwork = mWifiConfigManager.getConfiguredNetworksWithPasswords()
                .get(0);
        ScanDetail scanDetail = createScanDetailForNetwork(visibleNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        mWifiConfigManager.updateScanDetailForNetwork(result.getNetworkId(), scanDetail);

        // verify all networks are disabled at the start
        when(mClock.getWallClockMillis()).thenReturn(0L);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mWifiConfigManager.startRestrictingAutoJoinToSubscriptionId(5);
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                passpointNetwork_1));
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                passpointNetwork_2));
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(openNetwork));

        // now the open network should be clear to connect because it is not visible at the start
        // of the API call. But both passpoint network should still be disabled due to the FQDN
        // of passpointNetwork_1 being disabled.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                ALL_NON_CARRIER_MERGED_WIFI_MIN_DISABLE_DURATION_MINUTES * 60 * 1000 + 1L);
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                passpointNetwork_1));
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                passpointNetwork_2));
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(openNetwork));
    }

    /**
     * Verify that stopRestrictingAutoJoinToSubscriptionId cancels the effects of
     * startRestrictingAutoJoinToSubscriptionId.
     */
    @Test
    public void testStopRestrictAutoJoinToSubscriptionId() {
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfiguration visibleNetwork = retrievedNetworks.get(0);
        ScanDetail scanDetail = createScanDetailForNetwork(visibleNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);

        WifiConfiguration otherNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        // verify both networks are disabled at the start
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mWifiConfigManager.startRestrictingAutoJoinToSubscriptionId(5);
        mWifiConfigManager.updateUserDisabledList(new ArrayList<String>());
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(visibleNetwork));
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(otherNetwork));

        mWifiConfigManager.stopRestrictingAutoJoinToSubscriptionId();
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(
                visibleNetwork));
        assertFalse(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(otherNetwork));
    }

    /**
     * Verify that the carrier network with the matching subscription ID is still allowed for
     * auto connect after startRestrictingAutoJoinToSubscriptionId is called.
     */
    @Test
    public void testStartTemporarilyDisablingAllNonCarrierMergedWifiAllowCarrierNetwork() {
        int testSubscriptionId = 5;
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenNetwork();
        network.carrierMerged = true;
        network.subscriptionId = testSubscriptionId;
        verifyAddNetworkToWifiConfigManager(network);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfiguration carrierNetwork = retrievedNetworks.get(0);
        WifiConfiguration otherNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        // verify that the carrier network is not disabled.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mWifiConfigManager.startRestrictingAutoJoinToSubscriptionId(testSubscriptionId);
        assertFalse(mWifiConfigManager
                .isNonCarrierMergedNetworkTemporarilyDisabled(carrierNetwork));
        assertTrue(mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(otherNetwork));
    }

    private NetworkUpdateResult verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
            boolean withNetworkSettings,
            boolean withProfileOwnerPolicy,
            boolean withDeviceOwnerPolicy,
            IpConfiguration ipConfiguration,
            boolean assertSuccess,
            int networkId) {
        return verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(withNetworkSettings,
                false, false, withProfileOwnerPolicy, withDeviceOwnerPolicy, ipConfiguration,
                assertSuccess, networkId);
    }

    private NetworkUpdateResult verifyAddOrUpdateNetworkWithProxySettingsAndPermissions(
            boolean withNetworkSettings,
            boolean withNetworkSetupWizard,
            boolean withNetworkManagedProvisioning,
            boolean withProfileOwnerPolicy,
            boolean withDeviceOwnerPolicy,
            IpConfiguration ipConfiguration,
            boolean assertSuccess,
            int networkId) {
        WifiConfiguration network;
        if (networkId == WifiConfiguration.INVALID_NETWORK_ID) {
            network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        } else {
            network = mWifiConfigManager.getConfiguredNetwork(networkId);
        }
        network.setIpConfiguration(ipConfiguration);
        mockIsAdmin(withDeviceOwnerPolicy || withProfileOwnerPolicy);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), any()))
                .thenReturn(withProfileOwnerPolicy);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt()))
                .thenReturn(withNetworkSettings);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt()))
                .thenReturn(withNetworkSetupWizard);
        when(mWifiPermissionsUtil.checkNetworkManagedProvisioningPermission(anyInt()))
                .thenReturn(withNetworkManagedProvisioning);
        int uid = withNetworkSettings || withNetworkSetupWizard || withNetworkManagedProvisioning
                ? TEST_CREATOR_UID
                : TEST_NO_PERM_UID;
        NetworkUpdateResult result = addNetworkToWifiConfigManager(network, uid);
        assertEquals(assertSuccess, result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        return result;
    }

    private void createWifiConfigManager() {
        mWifiConfigManager =
                new WifiConfigManager(
                        mContext,
                        mWifiKeyStore,
                        mWifiConfigStore,
                        mNetworkListSharedStoreData,
                        mNetworkListUserStoreData,
                        mRandomizedMacStoreData,
                        mLruConnectionTracker,
                        mWifiInjector,
                        new Handler(mLooper.getLooper()));
        mWifiConfigManager.enableVerboseLogging(true);
    }

    /**
     * This method sets defaults in the provided WifiConfiguration object if not set
     * so that it can be used for comparison with the configuration retrieved from
     * WifiConfigManager.
     */
    private void setDefaults(WifiConfiguration configuration) {
        if (configuration.getIpAssignment() == IpConfiguration.IpAssignment.UNASSIGNED) {
            configuration.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
        }
        if (configuration.getProxySettings() == IpConfiguration.ProxySettings.UNASSIGNED) {
            configuration.setProxySettings(IpConfiguration.ProxySettings.NONE);
        }
        configuration.status = WifiConfiguration.Status.DISABLED;
        configuration.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        configuration.getNetworkSelectionStatus().setNetworkSelectionDisableReason(
                NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER);
    }

    /**
     * Modifies the provided configuration with creator uid and package name.
     */
    private void setCreationDebugParams(WifiConfiguration configuration, int uid,
                                        String packageName) {
        configuration.creatorUid = configuration.lastUpdateUid = uid;
        configuration.creatorName = configuration.lastUpdateName = packageName;
    }

    /**
     * Modifies the provided configuration with update uid and package name.
     */
    private void setUpdateDebugParams(WifiConfiguration configuration) {
        configuration.lastUpdateUid = TEST_UPDATE_UID;
        configuration.lastUpdateName = TEST_UPDATE_NAME;
    }

    /**
     * Modifies the provided WifiConfiguration with the specified bssid value. Also, asserts that
     * the existing |BSSID| field is not the same value as the one being set
     */
    private void assertAndSetNetworkBSSID(WifiConfiguration configuration, String bssid) {
        assertNotEquals(bssid, configuration.BSSID);
        configuration.BSSID = bssid;
    }

    /**
     * Modifies the provided WifiConfiguration with the specified |IpConfiguration| object. Also,
     * asserts that the existing |mIpConfiguration| field is not the same value as the one being set
     */
    private void assertAndSetNetworkIpConfiguration(
            WifiConfiguration configuration, IpConfiguration ipConfiguration) {
        assertNotEquals(ipConfiguration, configuration.getIpConfiguration());
        configuration.setIpConfiguration(ipConfiguration);
    }

    /**
     * Modifies the provided WifiConfiguration with the specified |wepKeys| value and
     * |wepTxKeyIndex|.
     */
    private void assertAndSetNetworkWepKeysAndTxIndex(
            WifiConfiguration configuration, String[] wepKeys, int wepTxKeyIdx) {
        assertNotEquals(wepKeys, configuration.wepKeys);
        assertNotEquals(wepTxKeyIdx, configuration.wepTxKeyIndex);
        configuration.wepKeys = Arrays.copyOf(wepKeys, wepKeys.length);
        configuration.wepTxKeyIndex = wepTxKeyIdx;
    }

    /**
     * Modifies the provided WifiConfiguration with the specified |preSharedKey| value.
     */
    private void assertAndSetNetworkPreSharedKey(
            WifiConfiguration configuration, String preSharedKey) {
        assertNotEquals(preSharedKey, configuration.preSharedKey);
        configuration.preSharedKey = preSharedKey;
    }

    /**
     * Modifies the provided WifiConfiguration with the specified enteprise |password| value.
     */
    private void assertAndSetNetworkEnterprisePassword(
            WifiConfiguration configuration, String password) {
        assertNotEquals(password, configuration.enterpriseConfig.getPassword());
        configuration.enterpriseConfig.setPassword(password);
    }

    /**
     * Helper method to capture the networks list store data that will be written by
     * WifiConfigStore.write() method.
     */
    private Pair<List<WifiConfiguration>, List<WifiConfiguration>>
            captureWriteNetworksListStoreData() {
        try {
            ArgumentCaptor<ArrayList> sharedConfigsCaptor =
                    ArgumentCaptor.forClass(ArrayList.class);
            ArgumentCaptor<ArrayList> userConfigsCaptor =
                    ArgumentCaptor.forClass(ArrayList.class);
            mNetworkListStoreDataMockOrder.verify(mNetworkListSharedStoreData)
                    .setConfigurations(sharedConfigsCaptor.capture());
            mNetworkListStoreDataMockOrder.verify(mNetworkListUserStoreData)
                    .setConfigurations(userConfigsCaptor.capture());
            mContextConfigStoreMockOrder.verify(mWifiConfigStore).write(true);
            return Pair.create(sharedConfigsCaptor.getValue(), userConfigsCaptor.getValue());
        } catch (Exception e) {
            fail("Exception encountered during write " + e);
        }
        return null;
    }

    /**
     * Returns whether the provided network was in the store data or not.
     */
    private boolean isNetworkInConfigStoreData(WifiConfiguration configuration) {
        Pair<List<WifiConfiguration>, List<WifiConfiguration>> networkListStoreData =
                captureWriteNetworksListStoreData();
        if (networkListStoreData == null) {
            return false;
        }
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.addAll(networkListStoreData.first);
        networkList.addAll(networkListStoreData.second);
        return isNetworkInConfigStoreData(configuration, networkList);
    }

    /**
     * Returns whether the provided network was in the store data or not.
     */
    private boolean isNetworkInConfigStoreData(
            WifiConfiguration configuration, List<WifiConfiguration> networkList) {
        WifiConfiguration translatedConfig = new WifiConfiguration(configuration);
        if (translatedConfig.SSID != null && translatedConfig.SSID.length() > 0
                && translatedConfig.SSID.charAt(0) != '\"') {
            // Translate the SSID only if it's specified in hexadecimal.
            translatedConfig.SSID = getTranslatedSsid(WifiSsid.fromString(configuration.SSID))
                    .toString();
        }
        for (WifiConfiguration retrievedConfig : networkList) {
            for (SecurityParams p: retrievedConfig.getSecurityParamsList()) {
                WifiConfiguration tmpConfig = new WifiConfiguration(retrievedConfig);
                tmpConfig.setSecurityParams(p);
                if (tmpConfig.getProfileKey().equals(
                        translatedConfig.getProfileKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Setup expectations for WifiNetworksListStoreData and DeletedEphemeralSsidsStoreData
     * after WifiConfigStore#read.
     */
    private void setupStoreDataForRead(List<WifiConfiguration> sharedConfigurations,
            List<WifiConfiguration> userConfigurations) {
        when(mNetworkListSharedStoreData.getConfigurations())
                .thenReturn(sharedConfigurations);
        when(mNetworkListUserStoreData.getConfigurations()).thenReturn(userConfigurations);
    }

    /**
     * Setup expectations for WifiNetworksListStoreData and DeletedEphemeralSsidsStoreData
     * after WifiConfigStore#switchUserStoresAndRead.
     */
    private void setupStoreDataForUserRead(List<WifiConfiguration> userConfigurations,
            Map<String, Long> deletedEphemeralSsids) {
        when(mNetworkListUserStoreData.getConfigurations()).thenReturn(userConfigurations);
    }

    /**
     * Verifies that the provided network was not present in the last config store write.
     */
    private void verifyNetworkNotInConfigStoreData(WifiConfiguration configuration) {
        assertFalse(isNetworkInConfigStoreData(configuration));
    }

    /**
     * Verifies that the provided network was present in the last config store write.
     */
    private void verifyNetworkInConfigStoreData(WifiConfiguration configuration) {
        assertTrue(isNetworkInConfigStoreData(configuration));
    }

    private void assertPasswordsMaskedInWifiConfiguration(WifiConfiguration configuration) {
        if (!TextUtils.isEmpty(configuration.preSharedKey)) {
            assertEquals(WifiConfigManager.PASSWORD_MASK, configuration.preSharedKey);
        }
        if (configuration.wepKeys != null) {
            for (int i = 0; i < configuration.wepKeys.length; i++) {
                if (!TextUtils.isEmpty(configuration.wepKeys[i])) {
                    assertEquals(WifiConfigManager.PASSWORD_MASK, configuration.wepKeys[i]);
                }
            }
        }
        if (!TextUtils.isEmpty(configuration.enterpriseConfig.getPassword())) {
            assertEquals(
                    WifiConfigManager.PASSWORD_MASK,
                    configuration.enterpriseConfig.getPassword());
        }
    }

    private void assertRandomizedMacAddressMaskedInWifiConfiguration(
            WifiConfiguration configuration) {
        MacAddress defaultMac = MacAddress.fromString(WifiInfo.DEFAULT_MAC_ADDRESS);
        MacAddress randomizedMacAddress = configuration.getRandomizedMacAddress();
        if (randomizedMacAddress != null) {
            assertEquals(defaultMac, randomizedMacAddress);
        }
    }

    /**
     * Verifies that the network was present in the network change broadcast and returns the
     * change reason.
     */
    private int verifyNetworkInBroadcastAndReturnReason() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        mContextConfigStoreMockOrder.verify(mContext).sendBroadcastAsUser(
                intentCaptor.capture(),
                eq(UserHandle.ALL),
                eq(android.Manifest.permission.ACCESS_WIFI_STATE));

        Intent intent = intentCaptor.getValue();

        WifiConfiguration retrievedConfig =
                (WifiConfiguration) intent.getExtra(WifiManager.EXTRA_WIFI_CONFIGURATION);
        assertNull(retrievedConfig);

        // Verify System only broadcast
        mContextConfigStoreMockOrder.verify(mContext).sendBroadcastAsUser(
                intentCaptor.capture(),
                eq(UserHandle.SYSTEM),
                eq(android.Manifest.permission.NETWORK_STACK));
        intent = intentCaptor.getValue();

        return intent.getIntExtra(WifiManager.EXTRA_CHANGE_REASON, -1);
    }

    /**
     * Verifies that we sent out an add broadcast with the provided network.
     */
    private void verifyNetworkAddBroadcast() {
        assertEquals(
                verifyNetworkInBroadcastAndReturnReason(),
                WifiManager.CHANGE_REASON_ADDED);
    }

    /**
     * Verifies that we sent out an update broadcast with the provided network.
     */
    private void verifyNetworkUpdateBroadcast() {
        assertEquals(
                verifyNetworkInBroadcastAndReturnReason(),
                WifiManager.CHANGE_REASON_CONFIG_CHANGE);
    }

    /**
     * Verifies that we sent out a remove broadcast with the provided network.
     */
    private void verifyNetworkRemoveBroadcast() {
        assertEquals(
                verifyNetworkInBroadcastAndReturnReason(),
                WifiManager.CHANGE_REASON_REMOVED);
    }

    private void verifyWifiConfigStoreRead() {
        assertTrue(mWifiConfigManager.loadFromStore());
        mContextConfigStoreMockOrder.verify(mContext).sendBroadcastAsUser(
                any(Intent.class),
                eq(UserHandle.ALL),
                eq(android.Manifest.permission.ACCESS_WIFI_STATE));
        mContextConfigStoreMockOrder.verify(mContext).sendBroadcastAsUser(
                any(Intent.class),
                eq(UserHandle.SYSTEM),
                eq(android.Manifest.permission.NETWORK_STACK));
    }

    private void triggerStoreReadIfNeeded() {
        // Trigger a store read if not already done.
        if (!mStoreReadTriggered) {
            verifyWifiConfigStoreRead();
            mStoreReadTriggered = true;
        }
    }

    /**
     * Adds the provided configuration to WifiConfigManager with uid = TEST_CREATOR_UID.
     */
    private NetworkUpdateResult addNetworkToWifiConfigManager(WifiConfiguration configuration) {
        return addNetworkToWifiConfigManager(configuration, TEST_CREATOR_UID, null);
    }

    /**
     * Adds the provided configuration to WifiConfigManager with uid specified.
     */
    private NetworkUpdateResult addNetworkToWifiConfigManager(WifiConfiguration configuration,
                                                              int uid) {
        return addNetworkToWifiConfigManager(configuration, uid, null);
    }

    /**
     * Adds the provided configuration to WifiConfigManager and modifies the provided configuration
     * with creator/update uid, package name and time. This also sets defaults for fields not
     * populated.
     * These fields are populated internally by WifiConfigManager and hence we need
     * to modify the configuration before we compare the added network with the retrieved network.
     * This method also triggers a store read if not already done.
     */
    private NetworkUpdateResult addNetworkToWifiConfigManager(WifiConfiguration configuration,
                                                              int uid,
                                                              @Nullable String packageName) {
        clearInvocations(mContext, mWifiConfigStore, mNetworkListSharedStoreData,
                mNetworkListUserStoreData);
        triggerStoreReadIfNeeded();
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS);
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(configuration, uid, packageName, false);
        setDefaults(configuration);
        setCreationDebugParams(
                configuration, uid, packageName != null ? packageName : TEST_CREATOR_NAME);
        configuration.networkId = result.getNetworkId();
        return result;
    }

    /**
     * Add network to WifiConfigManager and ensure that it was successful.
     */
    private NetworkUpdateResult verifyAddNetworkToWifiConfigManager(
            WifiConfiguration configuration) {
        NetworkUpdateResult result = addNetworkToWifiConfigManager(configuration,
                configuration.creatorUid);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());
        assertTrue(result.hasIpChanged());
        assertTrue(result.hasProxyChanged());

        verifyNetworkAddBroadcast();
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        // Verify that the config store write was triggered with this new configuration.
        verifyNetworkInConfigStoreData(configuration);
        return result;
    }

    /**
     * Add ephemeral network to WifiConfigManager and ensure that it was successful.
     */
    private NetworkUpdateResult verifyAddEphemeralNetworkToWifiConfigManager(
            WifiConfiguration configuration) throws Exception {
        NetworkUpdateResult result = addNetworkToWifiConfigManager(configuration);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());
        assertTrue(result.hasIpChanged());
        assertTrue(result.hasProxyChanged());

        verifyNetworkAddBroadcast();
        // Ensure that the write was not invoked for ephemeral network addition.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(true);
        return result;
    }

    /**
     * Add ephemeral network to WifiConfigManager and ensure that it was successful.
     */
    private NetworkUpdateResult verifyAddSuggestionOrRequestNetworkToWifiConfigManager(
            WifiConfiguration configuration) throws Exception {
        NetworkUpdateResult result =
                addNetworkToWifiConfigManager(configuration, TEST_CREATOR_UID, TEST_CREATOR_NAME);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());
        assertTrue(result.hasIpChanged());
        assertTrue(result.hasProxyChanged());

        verifyNetworkAddBroadcast();
        // Ensure that the write was not invoked for ephemeral network addition.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(true);
        return result;
    }

    /**
     * Add Passpoint network to WifiConfigManager and ensure that it was successful.
     */
    private NetworkUpdateResult verifyAddPasspointNetworkToWifiConfigManager(
            WifiConfiguration configuration) throws Exception {
        NetworkUpdateResult result = addNetworkToWifiConfigManager(configuration);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());
        assertTrue(result.hasIpChanged());
        assertTrue(result.hasProxyChanged());

        // Verify keys are not being installed.
        verify(mWifiKeyStore, never()).updateNetworkKeys(any(WifiConfiguration.class),
                any(WifiConfiguration.class));
        verifyNetworkAddBroadcast();
        // Ensure that the write was not invoked for Passpoint network addition.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(true);
        return result;
    }

    /**
     * Updates the provided configuration to WifiConfigManager and modifies the provided
     * configuration with update uid, package name and time.
     * These fields are populated internally by WifiConfigManager and hence we need
     * to modify the configuration before we compare the added network with the retrieved network.
     */
    private NetworkUpdateResult updateNetworkToWifiConfigManager(WifiConfiguration configuration) {
        clearInvocations(mContext, mWifiConfigStore, mNetworkListSharedStoreData,
                mNetworkListUserStoreData);
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_UPDATE_TIME_MILLIS);
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(configuration, TEST_UPDATE_UID);
        setUpdateDebugParams(configuration);
        return result;
    }

    /**
     * Update network to WifiConfigManager config change and ensure that it was successful.
     */
    private NetworkUpdateResult verifyUpdateNetworkToWifiConfigManager(
            WifiConfiguration configuration) {
        NetworkUpdateResult result = updateNetworkToWifiConfigManager(configuration);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertFalse(result.isNewNetwork());

        verifyNetworkUpdateBroadcast();
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        // Verify that the config store write was triggered with this new configuration.
        verifyNetworkInConfigStoreData(configuration);
        return result;
    }

    /**
     * Update network to WifiConfigManager without IP config change and ensure that it was
     * successful.
     */
    private NetworkUpdateResult verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(
            WifiConfiguration configuration) {
        NetworkUpdateResult result = verifyUpdateNetworkToWifiConfigManager(configuration);
        assertFalse(result.hasIpChanged());
        assertFalse(result.hasProxyChanged());
        return result;
    }

    /**
     * Update network to WifiConfigManager with IP config change and ensure that it was
     * successful.
     */
    private NetworkUpdateResult verifyUpdateNetworkToWifiConfigManagerWithIpChange(
            WifiConfiguration configuration) {
        NetworkUpdateResult result = verifyUpdateNetworkToWifiConfigManager(configuration);
        assertTrue(result.hasIpChanged());
        assertTrue(result.hasProxyChanged());
        return result;
    }

    /**
     * Removes network from WifiConfigManager and ensure that it was successful.
     */
    private void verifyRemoveNetworkFromWifiConfigManager(
            WifiConfiguration configuration) {
        assertTrue(mWifiConfigManager.removeNetwork(
                configuration.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));

        verifyNetworkRemoveBroadcast();
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        // Verify if the config store write was triggered without this new configuration.
        verifyNetworkNotInConfigStoreData(configuration);
        verify(mWifiBlocklistMonitor, atLeastOnce()).handleNetworkRemoved(configuration.SSID);
    }

    /**
     * Removes ephemeral network from WifiConfigManager and ensure that it was successful.
     */
    private void verifyRemoveEphemeralNetworkFromWifiConfigManager(
            WifiConfiguration configuration) throws Exception {
        assertTrue(mWifiConfigManager.removeNetwork(
                configuration.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));

        verifyNetworkRemoveBroadcast();
        // Ensure that the write was not invoked for ephemeral network remove.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(true);
    }

    /**
     * Removes Passpoint network from WifiConfigManager and ensure that it was successful.
     */
    private void verifyRemovePasspointNetworkFromWifiConfigManager(
            WifiConfiguration configuration) throws Exception {
        assertTrue(mWifiConfigManager.removeNetwork(
                configuration.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME));

        // Verify keys are not being removed.
        verify(mWifiKeyStore, never()).removeKeys(any(WifiEnterpriseConfig.class), eq(false));
        verifyNetworkRemoveBroadcast();
        // Ensure that the write was not invoked for Passpoint network remove.
        mContextConfigStoreMockOrder.verify(mWifiConfigStore, never()).write(true);
    }

    /**
     * Verifies the provided network's public status and ensures that the network change broadcast
     * has been sent out.
     */
    private void verifyUpdateNetworkStatus(WifiConfiguration configuration, int status) {
        assertEquals(status, configuration.status);
        verifyNetworkUpdateBroadcast();
    }

    /**
     * Creates a scan detail corresponding to the provided network and given BSSID, level &frequency
     * values.
     */
    private ScanDetail createScanDetailForNetwork(
            WifiConfiguration configuration, String bssid, int level, int frequency) {
        return WifiConfigurationTestUtil.createScanDetailForNetwork(configuration, bssid, level,
                frequency, mClock.getUptimeSinceBootMillis(), mClock.getWallClockMillis());
    }
    /**
     * Creates a scan detail corresponding to the provided network and BSSID value.
     */
    private ScanDetail createScanDetailForNetwork(WifiConfiguration configuration, String bssid) {
        return createScanDetailForNetwork(configuration, bssid, 0, 0);
    }

    /**
     * Creates a scan detail corresponding to the provided network and fixed BSSID value.
     */
    private ScanDetail createScanDetailForNetwork(WifiConfiguration configuration) {
        return createScanDetailForNetwork(configuration, TEST_BSSID);
    }

    /**
     * Adds the provided network and then creates a scan detail corresponding to the network. The
     * method then creates a ScanDetail corresponding to the network and ensures that the network
     * is properly matched using
     * {@link WifiConfigManager#getSavedNetworkForScanDetailAndCache(ScanDetail)} and also
     * verifies that the provided scan detail was cached,
     */
    private void verifyAddSingleNetworkAndMatchScanDetailToNetworkAndCache(
            WifiConfiguration network) {
        // First add the provided network.
        verifyAddNetworkToWifiConfigManager(network);

        // Now create a fake scan detail corresponding to the network.
        ScanDetail scanDetail = createScanDetailForNetwork(network);
        ScanResult scanResult = scanDetail.getScanResult();

        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getSavedNetworkForScanDetailAndCache(scanDetail);
        // Retrieve the network with password data for comparison.
        retrievedNetwork =
                mWifiConfigManager.getConfiguredNetworkWithPassword(retrievedNetwork.networkId);

        WifiConfigurationTestUtil.assertConfigurationEqualForConfigManagerAddOrUpdate(
                network, retrievedNetwork);

        // Now retrieve the scan detail cache and ensure that the new scan detail is in cache.
        ScanDetailCache retrievedScanDetailCache =
                mWifiConfigManager.getScanDetailCacheForNetwork(network.networkId);
        assertEquals(1, retrievedScanDetailCache.size());
        ScanResult retrievedScanResult = retrievedScanDetailCache.getScanResult(scanResult.BSSID);

        ScanTestUtil.assertScanResultEquals(scanResult, retrievedScanResult);
    }

    /**
     * Adds a new network and verifies that the |HasEverConnected| flag is set to false.
     */
    private void verifyAddNetworkHasEverConnectedFalse(WifiConfiguration network) {
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(network);
        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertFalse("Adding a new network should not have hasEverConnected set to true.",
                retrievedNetwork.getNetworkSelectionStatus().hasEverConnected());
    }

    /**
     * Updates an existing network with some credential change and verifies that the
     * |HasEverConnected| flag is set to false.
     */
    private void verifyUpdateNetworkWithCredentialChangeHasEverConnectedFalse(
            WifiConfiguration network) {
        NetworkUpdateResult result = verifyUpdateNetworkToWifiConfigManagerWithoutIpChange(network);
        WifiConfiguration retrievedNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertFalse("Updating network credentials config should clear hasEverConnected.",
                retrievedNetwork.getNetworkSelectionStatus().hasEverConnected());
        assertTrue(result.hasCredentialChanged());
    }

    /**
     * Updates an existing network after connection using
     * {@link WifiConfigManager#updateNetworkAfterConnect(int, boolean, boolean, int)} and asserts that the
     * |HasEverConnected| flag is set to true.
     */
    private void verifyUpdateNetworkAfterConnectHasEverConnectedTrue(int networkId) {
        assertTrue(mWifiConfigManager.updateNetworkAfterConnect(
                networkId, false, false, TEST_RSSI));
        WifiConfiguration retrievedNetwork = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue("hasEverConnected expected to be true after connection.",
                retrievedNetwork.getNetworkSelectionStatus().hasEverConnected());
        assertEquals(0, mLruConnectionTracker.getAgeIndexOfNetwork(retrievedNetwork));
    }

    /**
     * Sets up a user profiles for WifiConfigManager testing.
     *
     * @param userId Id of the user.
     */
    private void setupUserProfiles(int userId) {
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(userId))).thenReturn(true);
    }

    private void verifyRemoveNetworksForApp() {
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createPskNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createWepNetwork());

        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        ApplicationInfo app = new ApplicationInfo();
        app.uid = TEST_CREATOR_UID;
        app.packageName = TEST_CREATOR_NAME;
        assertEquals(3, mWifiConfigManager.removeNetworksForApp(app).size());

        // Ensure all the networks are removed now.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());
    }

    /**
     * Verifies that isInFlakyRandomizationSsidHotlist returns true if the network's SSID is in
     * the hotlist and the network is using randomized MAC.
     */
    @Test
    public void testFlakyRandomizationSsidHotlist() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);
        int networkId = result.getNetworkId();

        // should return false when there is nothing in the hotlist
        assertFalse(mWifiConfigManager.isInFlakyRandomizationSsidHotlist(networkId));

        // add the network's SSID to the hotlist and verify the method returns true
        Set<String> ssidHotlist = new HashSet<>();
        ssidHotlist.add(openNetwork.SSID);
        when(mDeviceConfigFacade.getRandomizationFlakySsidHotlist()).thenReturn(ssidHotlist);
        assertTrue(mWifiConfigManager.isInFlakyRandomizationSsidHotlist(networkId));

        // Now change the macRandomizationSetting to "trusted" and then verify
        // isInFlakyRandomizationSsidHotlist returns false
        openNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        NetworkUpdateResult networkUpdateResult = updateNetworkToWifiConfigManager(openNetwork);
        assertNotEquals(WifiConfiguration.INVALID_NETWORK_ID, networkUpdateResult.getNetworkId());
        assertFalse(mWifiConfigManager.isInFlakyRandomizationSsidHotlist(networkId));
    }

    /**
     * Verifies that findScanRssi returns valid RSSI when scan was done recently
     */
    @Test
    public void testFindScanRssiRecentScan() {
        // First add the provided network.
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);

        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS);
        ScanDetail scanDetail = createScanDetailForNetwork(testNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        ScanResult scanResult = scanDetail.getScanResult();

        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS + 2000);
        assertEquals(mWifiConfigManager.findScanRssi(result.getNetworkId(), 5000), TEST_RSSI);
    }

    /**
     * Verifies that findScanRssi returns INVALID_RSSI when scan was done a long time ago
     */
    @Test
    public void testFindScanRssiOldScan() {
        // First add the provided network.
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);

        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS);
        ScanDetail scanDetail = createScanDetailForNetwork(testNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        ScanResult scanResult = scanDetail.getScanResult();

        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS + 15000);
        assertEquals(mWifiConfigManager.findScanRssi(result.getNetworkId(), 5000),
                WifiInfo.INVALID_RSSI);
    }

    /**
     * Verify when save to store, isMostRecentlyConnected flag will be set.
     */
    @Test
    public void testMostRecentlyConnectedNetwork() {
        WifiConfiguration testNetwork1 = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkToWifiConfigManager(testNetwork1);
        WifiConfiguration testNetwork2 = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkToWifiConfigManager(testNetwork2);
        mWifiConfigManager.updateNetworkAfterConnect(
                testNetwork2.networkId, false, false, TEST_RSSI);
        assertTrue(mAlarmManager.isPending(BUFFERED_WRITE_ALARM_TAG));
        mAlarmManager.dispatch(BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        Pair<List<WifiConfiguration>, List<WifiConfiguration>> networkStoreData =
                captureWriteNetworksListStoreData();
        List<WifiConfiguration> sharedNetwork = networkStoreData.first;
        assertFalse(sharedNetwork.get(0).isMostRecentlyConnected);
        assertTrue(sharedNetwork.get(1).isMostRecentlyConnected);
    }

    /**
     * Verify scan comparator gives the most recently connected network highest priority
     */
    @Test
    public void testScanComparator() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenOweNetwork();
        verifyAddNetworkToWifiConfigManager(network1);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createOpenOweNetwork();
        verifyAddNetworkToWifiConfigManager(network2);
        WifiConfiguration network3 = WifiConfigurationTestUtil.createOpenOweNetwork();
        verifyAddNetworkToWifiConfigManager(network3);
        WifiConfiguration network4 = WifiConfigurationTestUtil.createOpenOweNetwork();
        verifyAddNetworkToWifiConfigManager(network4);

        // Connect two network in order, network3 --> network2
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(network3.networkId);
        verifyUpdateNetworkAfterConnectHasEverConnectedTrue(network2.networkId);
        // Set network4 {@link NetworkSelectionStatus#mSeenInLastQualifiedNetworkSelection} to true.
        assertTrue(mWifiConfigManager.setNetworkCandidateScanResult(network4.networkId,
                createScanDetailForNetwork(network4).getScanResult(), 54,
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_OPEN)));
        List<WifiConfiguration> networkList = mWifiConfigManager.getConfiguredNetworks();
        // Expected order should be based on connection order and last qualified selection.
        Collections.sort(networkList, mWifiConfigManager.getScanListComparator());
        assertEquals(network2.SSID, networkList.get(0).SSID);
        assertEquals(network3.SSID, networkList.get(1).SSID);
        assertEquals(network4.SSID, networkList.get(2).SSID);
        assertEquals(network1.SSID, networkList.get(3).SSID);
        // Remove network to check the age index changes.
        mWifiConfigManager.removeNetwork(network3.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME);
        assertEquals(Integer.MAX_VALUE, mLruConnectionTracker.getAgeIndexOfNetwork(network3));
        assertEquals(0, mLruConnectionTracker.getAgeIndexOfNetwork(network2));
        mWifiConfigManager.removeNetwork(network2.networkId, TEST_CREATOR_UID, TEST_CREATOR_NAME);
        assertEquals(Integer.MAX_VALUE, mLruConnectionTracker.getAgeIndexOfNetwork(network2));
    }

    /**
     * Ensures that setting the user connect choice updates the
     * NetworkSelectionStatus#mConnectChoice for all other WifiConfigurations in range in the last
     * round of network selection.
     *
     * Expected behavior: WifiConfiguration.NetworkSelectionStatus#mConnectChoice is set to
     *                    test1's configkey for test2. test3's WifiConfiguration is unchanged.
     */
    @Test
    public void testSetUserConnectChoice() throws Exception {
        WifiConfiguration selectedWifiConfig = WifiConfigurationTestUtil.createOpenNetwork();
        selectedWifiConfig.getNetworkSelectionStatus().setCandidate(mock(ScanResult.class));
        selectedWifiConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        selectedWifiConfig.getNetworkSelectionStatus().setConnectChoice("bogusKey");

        WifiConfiguration configInLastNetworkSelection =
                WifiConfigurationTestUtil.createOpenNetwork();
        configInLastNetworkSelection.getNetworkSelectionStatus()
                .setSeenInLastQualifiedNetworkSelection(true);

        WifiConfiguration configNotInLastNetworkSelection =
                WifiConfigurationTestUtil.createOpenNetwork();

        mWifiConfigManager.addOnNetworkUpdateListener(mListener);
        List<WifiConfiguration> sharedConfigs = Arrays.asList(
                selectedWifiConfig, configInLastNetworkSelection, configNotInLastNetworkSelection);
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        assertTrue(mWifiConfigManager.updateNetworkAfterConnect(selectedWifiConfig.networkId,
                false, true, TEST_RSSI));

        selectedWifiConfig = mWifiConfigManager.getConfiguredNetwork(selectedWifiConfig.networkId);
        assertEquals(NetworkSelectionStatus.DISABLED_NONE,
                selectedWifiConfig.getNetworkSelectionStatus().getNetworkSelectionStatus());
        assertNull(selectedWifiConfig.getNetworkSelectionStatus().getConnectChoice());

        configInLastNetworkSelection = mWifiConfigManager.getConfiguredNetwork(
                configInLastNetworkSelection.networkId);
        assertEquals(selectedWifiConfig.getProfileKey(),
                configInLastNetworkSelection.getNetworkSelectionStatus().getConnectChoice());

        configNotInLastNetworkSelection = mWifiConfigManager.getConfiguredNetwork(
                configNotInLastNetworkSelection.networkId);
        assertNull(configNotInLastNetworkSelection.getNetworkSelectionStatus().getConnectChoice());

        ArgumentCaptor<List<WifiConfiguration>> listArgumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mListener).onConnectChoiceSet(listArgumentCaptor.capture(),
                eq(selectedWifiConfig.getProfileKey()), eq(TEST_RSSI));
        Collection<WifiConfiguration> networks = listArgumentCaptor.getValue();
        assertEquals(1, networks.size());
    }

    /**
     * Ensures that setting the user connect choice updates the all the suggestion passpoint network
     * by callback.
     */
    @Test
    public void testSetUserConnectChoiceForSuggestionAndPasspointNetwork() throws Exception {
        WifiConfiguration selectedWifiConfig = WifiConfigurationTestUtil.createOpenNetwork();
        selectedWifiConfig.getNetworkSelectionStatus().setCandidate(mock(ScanResult.class));
        selectedWifiConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        selectedWifiConfig.getNetworkSelectionStatus().setConnectChoice("bogusKey");

        WifiConfiguration configInLastNetworkSelection1 =
                WifiConfigurationTestUtil.createOpenNetwork();
        configInLastNetworkSelection1.getNetworkSelectionStatus()
                .setSeenInLastQualifiedNetworkSelection(true);
        configInLastNetworkSelection1.fromWifiNetworkSuggestion = true;

        WifiConfiguration configInLastNetworkSelection2 =
                WifiConfigurationTestUtil.createPasspointNetwork();
        configInLastNetworkSelection2.getNetworkSelectionStatus()
                .setSeenInLastQualifiedNetworkSelection(true);

        mWifiConfigManager.addOnNetworkUpdateListener(mListener);
        List<WifiConfiguration> sharedConfigs = Arrays.asList(
                selectedWifiConfig, configInLastNetworkSelection1, configInLastNetworkSelection2);
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        assertTrue(mWifiConfigManager.updateNetworkAfterConnect(selectedWifiConfig.networkId,
                false, true, TEST_RSSI));

        selectedWifiConfig = mWifiConfigManager.getConfiguredNetwork(selectedWifiConfig.networkId);
        assertEquals(NetworkSelectionStatus.DISABLED_NONE,
                selectedWifiConfig.getNetworkSelectionStatus().getNetworkSelectionStatus());
        assertNull(selectedWifiConfig.getNetworkSelectionStatus().getConnectChoice());

        ArgumentCaptor<List<WifiConfiguration>> listArgumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mListener).onConnectChoiceSet(listArgumentCaptor.capture(),
                eq(selectedWifiConfig.getProfileKey()), eq(TEST_RSSI));
        Collection<WifiConfiguration> networks = listArgumentCaptor.getValue();
        assertEquals(2, networks.size());
    }

    @Test
    public void testConnectToExistingNetworkSuccess() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        config.getNetworkSelectionStatus().setConnectChoice("bogusKey");

        List<WifiConfiguration> sharedConfigs = Arrays.asList(config);
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        config = mWifiConfigManager.getConfiguredNetwork(config.networkId);
        assertFalse(config.getNetworkSelectionStatus().isNetworkEnabled());
        assertNotEquals(TEST_CREATOR_UID, config.lastConnectUid);
        assertEquals("bogusKey", config.getNetworkSelectionStatus().getConnectChoice());

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_CREATOR_UID))
                .thenReturn(true);

        mWifiConfigManager.updateBeforeConnect(config.networkId, TEST_CREATOR_UID,
                TEST_PACKAGE_NAME, true);

        config = mWifiConfigManager.getConfiguredNetwork(config.networkId);
        // network became enabled
        assertTrue(config.getNetworkSelectionStatus().isNetworkEnabled());
        // lastConnectUid updated
        assertEquals(TEST_CREATOR_UID, config.lastConnectUid);
        // connect choice should still be "bogusKey". This should only get cleared after the
        // connection has actually completed.
        assertEquals("bogusKey", config.getNetworkSelectionStatus().getConnectChoice());
    }

    /**
     * Test that if callingUid doesn't have NETWORK_SETTINGS permission, the connect succeeds,
     * but user connect choice isn't cleared.
     */
    @Test
    public void testConnectToExistingNetworkSuccessNoNetworkSettingsPermission() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        config.getNetworkSelectionStatus().setConnectChoice("bogusKey");

        List<WifiConfiguration> sharedConfigs = Arrays.asList(config);
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        config = mWifiConfigManager.getConfiguredNetwork(config.networkId);
        assertFalse(config.getNetworkSelectionStatus().isNetworkEnabled());
        assertNotEquals(TEST_CREATOR_UID, config.lastConnectUid);
        assertEquals("bogusKey", config.getNetworkSelectionStatus().getConnectChoice());

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_CREATOR_UID))
                .thenReturn(false);

        mWifiConfigManager.updateBeforeConnect(config.networkId, TEST_CREATOR_UID,
                TEST_PACKAGE_NAME, true);

        config = mWifiConfigManager.getConfiguredNetwork(config.networkId);
        // network became enabled
        assertTrue(config.getNetworkSelectionStatus().isNetworkEnabled());
        // lastConnectUid updated
        assertEquals(TEST_CREATOR_UID, config.lastConnectUid);
        // connect choice not cleared
        assertEquals("bogusKey", config.getNetworkSelectionStatus().getConnectChoice());
    }

    @Test
    public void testConnectToExistingNetworkFailure_UidDoesNotBelongToCurrentUser()
            throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        config.getNetworkSelectionStatus().setConnectChoice("bogusKey");

        List<WifiConfiguration> sharedConfigs = Arrays.asList(config);
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        config = mWifiConfigManager.getConfiguredNetwork(config.networkId);
        assertFalse(config.getNetworkSelectionStatus().isNetworkEnabled());
        assertNotEquals(TEST_OTHER_USER_UID, config.lastConnectUid);
        assertEquals("bogusKey", config.getNetworkSelectionStatus().getConnectChoice());

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_OTHER_USER_UID))
                .thenReturn(false);
        when(mUserManager.isSameProfileGroup(any(), any())).thenReturn(false);

        mWifiConfigManager.updateBeforeConnect(config.networkId, TEST_OTHER_USER_UID,
                TEST_PACKAGE_NAME, true);

        // network still disabled
        assertFalse(config.getNetworkSelectionStatus().isNetworkEnabled());
        // lastConnectUid wasn't changed
        assertNotEquals(TEST_OTHER_USER_UID, config.lastConnectUid);
        // connect choice wasn't changed
        assertEquals("bogusKey", config.getNetworkSelectionStatus().getConnectChoice());
    }

    @Test
    public void updateBeforeSaveNetworkSuccess() throws Exception {
        // initialize WifiConfigManager and start off with one network
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result =
                mWifiConfigManager.updateBeforeSaveNetwork(config, TEST_CREATOR_UID,
                TEST_PACKAGE_NAME);

        assertTrue(result.isSuccess());

        config = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());

        assertNotNull(config);
        assertTrue(config.getNetworkSelectionStatus().isNetworkEnabled());
    }

    /** Verifies that configs save fails with an invalid UID. */
    @Test
    public void updateBeforeSaveNetworkConfigFailsWithInvalidUid() throws Exception {
        // initialize WifiConfigManager and start off with one network
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_OTHER_USER_UID))
                .thenReturn(false);
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(TEST_OTHER_USER_UID))
                .thenReturn(false);

        NetworkUpdateResult result =
                mWifiConfigManager.updateBeforeSaveNetwork(config, TEST_OTHER_USER_UID,
                TEST_PACKAGE_NAME);

        assertFalse(result.isSuccess());
        assertNull(mWifiConfigManager.getConfiguredNetwork(config.networkId));
    }

    /** Verifies that save fails with a null config. */
    @Test
    public void updateBeforeSaveNetworkNullConfigFails() throws Exception {
        // initialize WifiConfigManager and start off with one network
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());

        NetworkUpdateResult result =
                mWifiConfigManager.updateBeforeSaveNetwork(null, TEST_OTHER_USER_UID,
                TEST_PACKAGE_NAME);

        assertFalse(result.isSuccess());
    }

    /**
     * Verifies all ephemeral carrier networks are removed
     */
    @Test
    public void testRemoveEphemeralCarrierNetwork() throws Exception {
        WifiConfiguration savedPskNetwork = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration ephemeralCarrierNetwork = WifiConfigurationTestUtil.createPskNetwork();
        ephemeralCarrierNetwork.ephemeral = true;
        ephemeralCarrierNetwork.subscriptionId = DATA_SUBID;
        ephemeralCarrierNetwork.creatorName = TEST_CREATOR_NAME;
        WifiConfiguration ephemeralNonCarrierNetwork = WifiConfigurationTestUtil.createPskNetwork();
        ephemeralNonCarrierNetwork.ephemeral = true;
        verifyAddNetworkToWifiConfigManager(savedPskNetwork);
        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralCarrierNetwork);
        verifyAddEphemeralNetworkToWifiConfigManager(ephemeralNonCarrierNetwork);

        List<WifiConfiguration> networks = Arrays.asList(savedPskNetwork,
                ephemeralNonCarrierNetwork, ephemeralCarrierNetwork);
        mWifiConfigManager.removeEphemeralCarrierNetworks(Set.of(TEST_CREATOR_NAME));
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);

        networks = Arrays.asList(savedPskNetwork,
                ephemeralNonCarrierNetwork);
        mWifiConfigManager.removeEphemeralCarrierNetworks(Collections.emptySet());
        retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
    }

    /**
     * Check persist random Mac Address is generated from stable Mac Random key.
     */
    @Test
    public void testGetPersistRandomMacAddress() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork();
        mWifiConfigManager.getPersistentMacAddress(network);
        verify(mMacAddressUtil).calculatePersistentMacForSta(eq(network.getNetworkKey()), anyInt());
    }

    private void verifyAddUpgradableNetwork(
            WifiConfiguration baseConfig,
            WifiConfiguration upgradableConfig) {
        int baseSecurityType = baseConfig.getDefaultSecurityParams().getSecurityType();
        int upgradableSecurityType = upgradableConfig.getDefaultSecurityParams().getSecurityType();

        NetworkUpdateResult result = addNetworkToWifiConfigManager(baseConfig);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        WifiConfiguration unmergedNetwork = retrievedNetworks.get(0);
        assertTrue(unmergedNetwork.isSecurityType(baseSecurityType));
        assertTrue(unmergedNetwork.isSecurityType(upgradableSecurityType));
        assertTrue(unmergedNetwork.getSecurityParams(upgradableSecurityType)
                .isAddedByAutoUpgrade());

        result = addNetworkToWifiConfigManager(upgradableConfig);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);

        retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        // Two networks should be merged into one.
        assertEquals(1, retrievedNetworks.size());
        WifiConfiguration mergedNetwork = retrievedNetworks.get(0);
        assertTrue(mergedNetwork.isSecurityType(baseSecurityType));
        assertTrue(mergedNetwork.isSecurityType(upgradableSecurityType));
        assertEquals(upgradableConfig.getDefaultSecurityParams().isAddedByAutoUpgrade(),
                mergedNetwork.getSecurityParams(upgradableSecurityType).isAddedByAutoUpgrade());
        assertEquals(upgradableConfig.hiddenSSID, mergedNetwork.hiddenSSID);
    }

    /**
     * Verifies the addition of an upgradable network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddUpgradableNetworkForPskSae() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"upgradableNetwork\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        upgradableConfig.preSharedKey = "\"Passw0rd\"";

        verifyAddUpgradableNetwork(baseConfig, upgradableConfig);
    }

    /**
     * Verifies that updating an existing upgradable network that was added by auto upgrade will
     * retain the isAddedByAutoUpgrade() value.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testUpdateUpgradedNetworkKeepsIsAddedByAutoUpgradeValue() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        WifiConfiguration upgradedConfig = new WifiConfiguration();
        upgradedConfig.SSID = "\"upgradableNetwork\"";
        upgradedConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        upgradedConfig.preSharedKey = "\"Passw0rd\"";
        upgradedConfig.getDefaultSecurityParams().setIsAddedByAutoUpgrade(true);

        verifyAddUpgradableNetwork(baseConfig, upgradedConfig);
    }

    /**
     * Verifies that adding an unhidden upgraded config will update the existing network to
     * unhidden.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddUnhiddenUpgradedNetworkOverwritesHiddenSsidValue() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        baseConfig.hiddenSSID = true;
        WifiConfiguration upgradedConfig = new WifiConfiguration();
        upgradedConfig.SSID = "\"upgradableNetwork\"";
        upgradedConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        upgradedConfig.preSharedKey = "\"Passw0rd\"";
        upgradedConfig.hiddenSSID = false;

        verifyAddUpgradableNetwork(baseConfig, upgradedConfig);
    }

    /**
     * Verifies that adding a hidden upgraded config will update the existing network to hidden.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddHiddenUpgradedNetworkOverwritesHiddenSsidValue() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        baseConfig.hiddenSSID = false;
        WifiConfiguration upgradedConfig = new WifiConfiguration();
        upgradedConfig.SSID = "\"upgradableNetwork\"";
        upgradedConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        upgradedConfig.preSharedKey = "\"Passw0rd\"";
        upgradedConfig.hiddenSSID = true;

        verifyAddUpgradableNetwork(baseConfig, upgradedConfig);
    }

    /**
     * Verifies the addition of an upgradable network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddUpgradableNetworkForWpa2Wpa3Enterprise() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        baseConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"upgradableNetwork\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        upgradableConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);

        verifyAddUpgradableNetwork(baseConfig, upgradableConfig);
    }

    /**
     * Verifies the addition of an upgradable network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddUpgradableNetworkForOpenOwe() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"upgradableNetwork\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);

        verifyAddUpgradableNetwork(baseConfig, upgradableConfig);
    }

    private void verifyAddDowngradableNetwork(
            WifiConfiguration baseConfig,
            WifiConfiguration downgradableConfig) {
        int baseSecurityType = baseConfig.getDefaultSecurityParams().getSecurityType();
        int downgradableSecurityType = downgradableConfig.getDefaultSecurityParams()
                .getSecurityType();

        NetworkUpdateResult result = addNetworkToWifiConfigManager(baseConfig);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        WifiConfiguration unmergedNetwork = retrievedNetworks.get(0);
        assertEquals(baseSecurityType,
                unmergedNetwork.getDefaultSecurityParams().getSecurityType());
        assertTrue(unmergedNetwork.isSecurityType(baseSecurityType));
        assertFalse(unmergedNetwork.isSecurityType(downgradableSecurityType));
        assertFalse(unmergedNetwork.getSecurityParams(baseSecurityType)
                .isAddedByAutoUpgrade());

        result = addNetworkToWifiConfigManager(downgradableConfig);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);

        retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        // Two networks should be merged into one.
        assertEquals(1, retrievedNetworks.size());
        WifiConfiguration mergedNetwork = retrievedNetworks.get(0);
        // default type is changed to downgrading one.
        assertEquals(downgradableSecurityType,
                mergedNetwork.getDefaultSecurityParams().getSecurityType());
        assertTrue(mergedNetwork.isSecurityType(baseSecurityType));
        assertTrue(mergedNetwork.isSecurityType(downgradableSecurityType));
        assertFalse(mergedNetwork.getSecurityParams(baseSecurityType)
                .isAddedByAutoUpgrade());
        if (baseConfig.hiddenSSID && !downgradableConfig.hiddenSSID) {
            // Merged network should still be hidden if the base config is hidden.
            assertTrue(mergedNetwork.hiddenSSID);
        } else {
            assertEquals(downgradableConfig.hiddenSSID, mergedNetwork.hiddenSSID);
        }
    }

    /**
     * Verifies the addition of a downgradable network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddDowngradableNetworkSaePsk() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"downgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        WifiConfiguration downgradableConfig = new WifiConfiguration();
        downgradableConfig.SSID = "\"downgradableNetwork\"";
        downgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        downgradableConfig.preSharedKey = "\"Passw0rd\"";

        verifyAddDowngradableNetwork(
                baseConfig,
                downgradableConfig);
    }

    /**
     * Verifies the addition of a downgradable network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddDowngradableNetworkWpa2Wpa3Enterprise() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"downgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        baseConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        WifiConfiguration downgradableConfig = new WifiConfiguration();
        downgradableConfig.SSID = "\"downgradableNetwork\"";
        downgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        downgradableConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);

        verifyAddDowngradableNetwork(
                baseConfig,
                downgradableConfig);
    }

    /**
     * Verifies the addition of a downgradable network using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddDowngradableNetworkOweOpen() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"downgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        WifiConfiguration downgradableConfig = new WifiConfiguration();
        downgradableConfig.SSID = "\"downgradableNetwork\"";
        downgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);

        verifyAddDowngradableNetwork(
                baseConfig,
                downgradableConfig);
    }

    /**
     * Verifies that adding an unhidden downgraded config won't update a hidden existing network to
     * unhidden. This is to prevent the case where a user adds a downgraded network that's only
     * visible due to scanning for a hidden SSID from an existing upgradeable config.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddUnhiddenDowngradedNetworkDoesntOverwriteHiddenSsidValue() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"downgradableConfig\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        baseConfig.hiddenSSID = true;
        WifiConfiguration downgradableConfig = new WifiConfiguration();
        downgradableConfig.SSID = "\"downgradableConfig\"";
        downgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        downgradableConfig.preSharedKey = "\"Passw0rd\"";
        downgradableConfig.hiddenSSID = false;

        verifyAddDowngradableNetwork(baseConfig, downgradableConfig);
    }

    /**
     * Verifies that adding an hidden downgraded config updates an existing network to hidden.
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)}
     */
    @Test
    public void testAddHiddenDowngradedNetworkOverwritesHiddenSsidValueToTrue() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"downgradableConfig\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        baseConfig.hiddenSSID = false;
        WifiConfiguration downgradableConfig = new WifiConfiguration();
        downgradableConfig.SSID = "\"downgradableConfig\"";
        downgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        downgradableConfig.preSharedKey = "\"Passw0rd\"";
        downgradableConfig.hiddenSSID = true;

        verifyAddDowngradableNetwork(baseConfig, downgradableConfig);
    }

    private void verifyLoadFromStoreMergeUpgradableConfigurations(
            WifiConfiguration baseConfig,
            WifiConfiguration upgradableConfig) {
        int baseSecurityType = baseConfig.getDefaultSecurityParams().getSecurityType();
        int upgradableSecurityType = upgradableConfig.getDefaultSecurityParams().getSecurityType();

        // The source guarantees that base configuration has necessary auto-upgrade type.
        WifiConfigurationUtil.addUpgradableSecurityTypeIfNecessary(baseConfig);

        // Set up the store data.
        List<WifiConfiguration> sharedNetworks = new ArrayList<WifiConfiguration>() {
            {
                add(baseConfig);
                add(upgradableConfig);
            }
        };
        setupStoreDataForRead(sharedNetworks, new ArrayList<>());

        // read from store now
        assertTrue(mWifiConfigManager.loadFromStore());

        // assert that the expected identities are reset
        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());

        WifiConfiguration mergedNetwork = retrievedNetworks.get(0);
        assertTrue(mergedNetwork.isSecurityType(baseSecurityType));
        assertTrue(mergedNetwork.isSecurityType(upgradableSecurityType));
        assertFalse(mergedNetwork.getSecurityParams(upgradableSecurityType)
                .isAddedByAutoUpgrade());
    }

    /**
     * Verifies that upgradable configuration are merged on
     * {@link WifiConfigManager#loadFromStore()}.
     */
    @Test
    public void testLoadFromStoreMergeUpgradableConfigurationsPskSae() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        baseConfig.preSharedKey = "\"Passw0rd\"";
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"upgradableNetwork\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        upgradableConfig.preSharedKey = "\"Passw0rd\"";

        verifyLoadFromStoreMergeUpgradableConfigurations(
                baseConfig,
                upgradableConfig);
        // reverse ordered networks should still be merged.
        verifyLoadFromStoreMergeUpgradableConfigurations(
                upgradableConfig,
                baseConfig);
    }

    /**
     * Verifies that upgradable configuration are merged on
     * {@link WifiConfigManager#loadFromStore()}.
     */
    @Test
    public void testLoadFromStoreMergeUpgradableConfigurationsOpenOwe() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"upgradableNetwork\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);

        verifyLoadFromStoreMergeUpgradableConfigurations(
                baseConfig,
                upgradableConfig);
        // reverse ordered networks should still be merged.
        verifyLoadFromStoreMergeUpgradableConfigurations(
                upgradableConfig,
                baseConfig);
    }

    /**
     * Verifies that upgradable configuration are merged on
     * {@link WifiConfigManager#loadFromStore()}.
     */
    @Test
    public void testLoadFromStoreMergeUpgradableConfigurationsWpa2Wpa3Enterprise() {
        WifiConfiguration baseConfig = new WifiConfiguration();
        baseConfig.SSID = "\"upgradableNetwork\"";
        baseConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        baseConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"upgradableNetwork\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        upgradableConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);

        verifyLoadFromStoreMergeUpgradableConfigurations(
                baseConfig,
                upgradableConfig);
        // reverse ordered networks should still be merged.
        verifyLoadFromStoreMergeUpgradableConfigurations(
                upgradableConfig,
                baseConfig);
    }

    private void verifyTransitionDisableIndicationForSecurityType(
            WifiConfiguration testNetwork, int indication) {

        int disabledType = testNetwork.getDefaultSecurityParams().getSecurityType();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);
        int networkId = result.getNetworkId();
        mWifiConfigManager.addOnNetworkUpdateListener(mListener);

        WifiConfiguration configBefore = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue(configBefore.getSecurityParams(disabledType).isEnabled());

        mWifiConfigManager.updateNetworkTransitionDisable(result.getNetworkId(), indication);

        WifiConfiguration configAfter = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertFalse(configAfter.getSecurityParams(disabledType).isEnabled());
        verify(mListener).onSecurityParamsUpdate(any(), eq(configAfter.getSecurityParamsList()));
    }

    /**
     * Verify Transition Disable Indication for the PSK/SAE configuration.
     */
    @Test
    public void testPskSaeTransitionDisableIndication() {
        verifyTransitionDisableIndicationForSecurityType(
                WifiConfigurationTestUtil.createPskNetwork(),
                WifiMonitor.TDI_USE_WPA3_PERSONAL);
    }

    /**
     * Verify Transition Disable Indication for WPA2/WPA3 Enterprise configuration.
     */
    @Test
    public void testWpa2Wpa3EnterpriseValidationTransitionDisableIndication() {
        verifyTransitionDisableIndicationForSecurityType(
                WifiConfigurationTestUtil.createEapNetwork(),
                WifiMonitor.TDI_USE_WPA3_ENTERPRISE);
    }

    /**
     * Verify Transition Disable Indication for Open/OWE configuration.
     */
    @Test
    public void testOpenOweValidationTransitionDisableIndication() {
        verifyTransitionDisableIndicationForSecurityType(
                WifiConfigurationTestUtil.createOpenNetwork(),
                WifiMonitor.TDI_USE_ENHANCED_OPEN);
    }

    /**
     * Verify Transition Disable Indication for the AP validation enforcement.
     */
    @Test
    public void testSaeApValidationTransitionDisableIndication() {
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createPskNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);
        int networkId = result.getNetworkId();

        WifiConfiguration configBefore = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertFalse(configBefore.getSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                .isSaePkOnlyMode());

        int indication = WifiMonitor.TDI_USE_SAE_PK;
        mWifiConfigManager.updateNetworkTransitionDisable(result.getNetworkId(), indication);

        WifiConfiguration configAfter = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue(configAfter.getSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                .isSaePkOnlyMode());
    }

    private void verifyNonApplicableTransitionDisableIndicationForSecurityType(
            WifiConfiguration testNetwork, int indication) {

        int disabledType = testNetwork.getDefaultSecurityParams().getSecurityType();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);
        int networkId = result.getNetworkId();

        WifiConfiguration configBefore = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue(configBefore.getSecurityParams(disabledType).isEnabled());

        mWifiConfigManager.updateNetworkTransitionDisable(result.getNetworkId(), indication);

        WifiConfiguration configAfter = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue(configBefore.getSecurityParams(disabledType).isEnabled());
    }

    /**
     * Verify Transition Disable Indication does not change anything if
     * the type is not applicable.
     */
    @Test
    public void testOpenTransitionDisableIndicationNotAffectPskSaeType() {
        verifyNonApplicableTransitionDisableIndicationForSecurityType(
                WifiConfigurationTestUtil.createPskNetwork(),
                WifiMonitor.TDI_USE_SAE_PK
                        | WifiMonitor.TDI_USE_ENHANCED_OPEN
                        | WifiMonitor.TDI_USE_WPA3_ENTERPRISE);
    }

    /**
     * Verify Transition Disable Indication does not change anything if
     * the type is not applicable.
     */
    @Test
    public void testNonApplicableTransitionDisableIndicationNotAffectWpa2Wpa3EnterpriseType() {
        verifyNonApplicableTransitionDisableIndicationForSecurityType(
                WifiConfigurationTestUtil.createEapNetwork(),
                WifiMonitor.TDI_USE_SAE_PK
                        | WifiMonitor.TDI_USE_ENHANCED_OPEN
                        | WifiMonitor.TDI_USE_WPA3_PERSONAL);
    }

    /**
     * Verify Transition Disable Indication does not change anything if
     * the type is not applicable.
     */
    @Test
    public void testNonApplicableTransitionDisableIndicationNotAffectOpenOweType() {
        verifyNonApplicableTransitionDisableIndicationForSecurityType(
                WifiConfigurationTestUtil.createOpenNetwork(),
                WifiMonitor.TDI_USE_SAE_PK
                        | WifiMonitor.TDI_USE_WPA3_PERSONAL
                        | WifiMonitor.TDI_USE_WPA3_ENTERPRISE);
    }

    /**
     * Verify Transition Disable Indication does not change anything if
     * the type is not applicable.
     */
    @Test
    public void testNonApplicableTransitionDisableIndicationNotAffectApValidation() {
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createPskNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(testNetwork);
        int networkId = result.getNetworkId();

        WifiConfiguration configBefore = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertFalse(configBefore.getSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                .isSaePkOnlyMode());

        int indication = WifiMonitor.TDI_USE_ENHANCED_OPEN
                | WifiMonitor.TDI_USE_WPA3_PERSONAL
                | WifiMonitor.TDI_USE_WPA3_ENTERPRISE;
        mWifiConfigManager.updateNetworkTransitionDisable(result.getNetworkId(), indication);

        WifiConfiguration configAfter = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertFalse(configAfter.getSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                .isSaePkOnlyMode());
    }

    @Test
    public void testLoadFromSharedAndUserStoreHandleFailureOnUserBuilds() throws Exception {
        when(mBuildProperties.isUserBuild()).thenReturn(true);

        List<WifiConfiguration> sharedConfigs =
                Arrays.asList(WifiConfigurationTestUtil.createOpenNetwork());
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());

        // Throw an exception when reading.
        doThrow(new XmlPullParserException("")).when(mWifiConfigStore).read();
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Should have no existing saved networks.
        assertTrue(mWifiConfigManager.getConfiguredNetworks().isEmpty());

        // Verify that we can add a new network.
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        assertFalse(mWifiConfigManager.getConfiguredNetworks().isEmpty());
    }

    @Test
    public void testLoadFromUserStoreHandleFailureOnUserBuilds() throws Exception {
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        int user2 = TEST_DEFAULT_USER + 1;
        setupUserProfiles(user2);

        List<WifiConfiguration> sharedConfigs =
                Arrays.asList(WifiConfigurationTestUtil.createOpenNetwork());
        // Setup xml storage
        setupStoreDataForRead(sharedConfigs, Arrays.asList());

        // Throw an exception when reading.
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        // Should have 1 shared saved networks.
        assertEquals(1, mWifiConfigManager.getConfiguredNetworks().size());

        // Now trigger a user switch and throw an excepton when reading user store file.
        doThrow(new XmlPullParserException("")).when(mWifiConfigStore).switchUserStoresAndRead(
                any());
        when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user2))).thenReturn(true);
        mWifiConfigManager.handleUserSwitch(user2);
        verify(mWifiConfigStore).switchUserStoresAndRead(any(List.class));

        // Verify that we can add a new network.
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createOpenNetwork());
        assertEquals(2, mWifiConfigManager.getConfiguredNetworks().size());
    }

    /**
     * Verify that the protected WifiEnterpriseConfig fields are loaded correctly
     * from the XML store.
     */
    @Test
    public void testLoadEnterpriseConfigProtectedFields() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setUserApproveNoCaCert(true);
        config.enterpriseConfig.setTofuDialogState(WifiEnterpriseConfig.TOFU_DIALOG_STATE_ACCEPTED);
        config.enterpriseConfig.setTofuConnectionState(
                WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING);
        List<WifiConfiguration> storedConfigs = Arrays.asList(config);

        // Setup xml storage
        setupStoreDataForRead(storedConfigs, Arrays.asList());
        assertTrue(mWifiConfigManager.loadFromStore());
        verify(mWifiConfigStore).read();

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        assertEquals(1, retrievedNetworks.size());
        assertTrue(retrievedNetworks.get(0).enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(
                WifiEnterpriseConfig.TOFU_DIALOG_STATE_ACCEPTED,
                retrievedNetworks.get(0).enterpriseConfig.getTofuDialogState());
        assertEquals(
                WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING,
                retrievedNetworks.get(0).enterpriseConfig.getTofuConnectionState());
    }

    /**
     * Verify that updating an existing config to a incompatible type works well.
     */
    @Test
    public void testUpdateExistingConfigWithIncompatibleSecurityType() {
        WifiConfiguration testNetwork = WifiConfigurationTestUtil.createPskNetwork();
        int networkIdBefore = verifyAddNetworkToWifiConfigManager(testNetwork).getNetworkId();
        WifiConfiguration configBefore = mWifiConfigManager.getConfiguredNetwork(networkIdBefore);
        assertFalse(configBefore.isSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN));
        assertTrue(configBefore.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK));

        // Change the type from PSK to Open.
        testNetwork.networkId = networkIdBefore;
        testNetwork.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        testNetwork.preSharedKey = null;
        int networkIdAfter = addNetworkToWifiConfigManager(testNetwork).getNetworkId();
        assertEquals(networkIdBefore, networkIdAfter);

        WifiConfiguration configAfter = mWifiConfigManager.getConfiguredNetwork(networkIdAfter);
        assertFalse(configAfter.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK));
        assertTrue(configAfter.isSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN));
    }

    @Test
    public void testRemoveNonCallerConfiguredNetworks() {
        final int callerUid = TEST_CREATOR_UID;
        WifiConfiguration callerNetwork0 = WifiConfigurationTestUtil.createPskNetwork();
        addNetworkToWifiConfigManager(callerNetwork0, callerUid, null);
        WifiConfiguration callerNetwork1 = WifiConfigurationTestUtil.createPskNetwork();
        addNetworkToWifiConfigManager(callerNetwork1, callerUid, null);
        WifiConfiguration nonCallerNetwork0 = WifiConfigurationTestUtil.createPskNetwork();
        addNetworkToWifiConfigManager(nonCallerNetwork0, TEST_OTHER_USER_UID, null);
        WifiConfiguration nonCallerNetwork1 = WifiConfigurationTestUtil.createPskNetwork();
        addNetworkToWifiConfigManager(nonCallerNetwork1, TEST_OTHER_USER_UID, null);

        assertTrue(mWifiConfigManager.removeNonCallerConfiguredNetwork(callerUid));
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                Arrays.asList(callerNetwork0, callerNetwork1),
                mWifiConfigManager.getConfiguredNetworksWithPasswords());
    }

    /**
     * Verify getMostRecentScanResultsForConfiguredNetworks returns most recent scan results.
     */
    @Test
    public void testGetMostRecentScanResultsForConfiguredNetworks() {
        int testMaxAgeMillis = 2000;
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS);
        ScanDetail scanDetail = createScanDetailForNetwork(openNetwork, TEST_BSSID,
                TEST_RSSI, TEST_FREQUENCY_1);
        ScanResult scanResult = scanDetail.getScanResult();
        mWifiConfigManager.updateScanDetailCacheFromScanDetailForSavedNetwork(scanDetail);

        // verify the ScanResult is returned before the testMaxAgeMillis
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                + testMaxAgeMillis - 1);
        List<ScanResult> scanResults = mWifiConfigManager
                .getMostRecentScanResultsForConfiguredNetworks(testMaxAgeMillis);
        assertEquals(1, scanResults.size());
        assertEquals(TEST_BSSID, scanResults.get(0).BSSID);

        // verify no ScanResult is returned after the timeout.
        when(mClock.getWallClockMillis()).thenReturn(TEST_WALLCLOCK_CREATION_TIME_MILLIS
                + testMaxAgeMillis);
        assertEquals(0, mWifiConfigManager
                .getMostRecentScanResultsForConfiguredNetworks(testMaxAgeMillis).size());
    }

    private void verifyAddUpgradableTypeNetwork(
            WifiConfiguration baseConfig, WifiConfiguration newConfig,
            boolean isNewConfigUpgradableType) {
        NetworkUpdateResult result = addNetworkToWifiConfigManager(baseConfig);
        int baseConfigId = result.getNetworkId();
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertTrue(result.isNewNetwork());

        result = addNetworkToWifiConfigManager(newConfig);
        assertEquals(baseConfigId, result.getNetworkId());
        assertFalse(result.isNewNetwork());

        List<WifiConfiguration> retrievedSavedNetworks = mWifiConfigManager.getSavedNetworks(
                Process.WIFI_UID);
        assertEquals(1, retrievedSavedNetworks.size());
        if (isNewConfigUpgradableType) {
            assertEquals(baseConfig.getProfileKey(),
                    retrievedSavedNetworks.get(0).getProfileKey());
        } else {
            assertEquals(newConfig.getProfileKey(),
                    retrievedSavedNetworks.get(0).getProfileKey());
        }
    }

    /**
     * Verifies the new SAE network is merged to existing PSK network correctly.
     */
    @Test
    public void testMergeSaeToPskNetwork() {
        verifyAddUpgradableTypeNetwork(
                WifiConfigurationTestUtil.createPskNetwork(TEST_SSID),
                WifiConfigurationTestUtil.createSaeNetwork(TEST_SSID),
                true);
    }

    /**
     * Verifies the new PSK network is merged to existing SAE network correctly.
     */
    @Test
    public void testMergePskToSaeNetwork() {
        verifyAddUpgradableTypeNetwork(
                WifiConfigurationTestUtil.createSaeNetwork(TEST_SSID),
                WifiConfigurationTestUtil.createPskNetwork(TEST_SSID),
                false);
    }

    /**
     * Verifies the new OWE network is merged to existing Open network correctly.
     */
    @Test
    public void testMergeOweToOpenNetwork() {
        verifyAddUpgradableTypeNetwork(
                WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID),
                WifiConfigurationTestUtil.createOweNetwork(TEST_SSID),
                true);
    }

    /**
     * Verifies the new Open network is merged to existing OWE network correctly.
     */
    @Test
    public void testMergeOpenToOweNetwork() {
        verifyAddUpgradableTypeNetwork(
                WifiConfigurationTestUtil.createOweNetwork(TEST_SSID),
                WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID),
                false);
    }

    /**
     * Verifies the new WPA3 Enterprise network is merged
     * to existing WPA2 Enterprise network correctly.
     */
    @Test
    public void testMergeWpa3EnterpriseToWpa2EnterprsieNetwork() {
        verifyAddUpgradableTypeNetwork(
                WifiConfigurationTestUtil.createEapNetwork(TEST_SSID),
                WifiConfigurationTestUtil.createWpa3EnterpriseNetwork(TEST_SSID),
                true);
    }

    /**
     * Verifies the new WPA2 Enterprise network is merged
     * to existing WPA3 Enterprise network correctly.
     */
    @Test
    public void testMergeWpa2EnterpriseToWpa3EnterpriseNetwork() {
        verifyAddUpgradableTypeNetwork(
                WifiConfigurationTestUtil.createWpa3EnterpriseNetwork(TEST_SSID),
                WifiConfigurationTestUtil.createEapNetwork(TEST_SSID),
                false);
    }

    /**
     * Verifies that excess networks are removed in the right order when more than the max amount of
     * WifiConfigurations are added.
     */
    @Test
    public void testRemoveExcessNetworksOnAdd() {
        final int maxNumWifiConfigs = 8;
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurations, maxNumWifiConfigs);
        List<WifiConfiguration> configsInDeletionOrder = new ArrayList<>();
        WifiConfiguration currentConfig = WifiConfigurationTestUtil.createPskNetwork();
        currentConfig.status = WifiConfiguration.Status.CURRENT;
        currentConfig.isCurrentlyConnected = true;
        WifiConfiguration lessDeletionPriorityConfig = WifiConfigurationTestUtil.createPskNetwork();
        lessDeletionPriorityConfig.setDeletionPriority(1);
        WifiConfiguration newlyAddedConfig = WifiConfigurationTestUtil.createPskNetwork();
        newlyAddedConfig.lastUpdated = 1;
        WifiConfiguration recentConfig = WifiConfigurationTestUtil.createPskNetwork();
        recentConfig.lastConnected = 2;
        WifiConfiguration notRecentConfig = WifiConfigurationTestUtil.createPskNetwork();
        notRecentConfig.lastConnected = 1;
        WifiConfiguration oneRebootSaeConfig = WifiConfigurationTestUtil.createSaeNetwork();
        oneRebootSaeConfig.numRebootsSinceLastUse = 1;
        WifiConfiguration oneRebootOpenConfig = WifiConfigurationTestUtil.createOpenNetwork();
        oneRebootOpenConfig.numRebootsSinceLastUse = 1;
        oneRebootOpenConfig.numAssociation = 1;
        WifiConfiguration noAssociationConfig = WifiConfigurationTestUtil.createOpenNetwork();
        noAssociationConfig.numRebootsSinceLastUse = 1;
        noAssociationConfig.numAssociation = 0;

        configsInDeletionOrder.add(noAssociationConfig);
        configsInDeletionOrder.add(oneRebootOpenConfig);
        configsInDeletionOrder.add(oneRebootSaeConfig);
        configsInDeletionOrder.add(notRecentConfig);
        configsInDeletionOrder.add(recentConfig);
        configsInDeletionOrder.add(newlyAddedConfig);
        configsInDeletionOrder.add(lessDeletionPriorityConfig);
        configsInDeletionOrder.add(currentConfig);

        for (WifiConfiguration config : configsInDeletionOrder) {
            verifyAddNetworkToWifiConfigManager(config);
        }
        assertEquals(mWifiConfigManager.getConfiguredNetworks().size(), maxNumWifiConfigs);

        // Add carrier configs to flush out the other configs, since they are deleted last.
        for (WifiConfiguration configToDelete : configsInDeletionOrder) {
            WifiConfiguration carrierConfig = WifiConfigurationTestUtil.createPskNetwork();
            carrierConfig.carrierId = 1;
            verifyAddNetworkToWifiConfigManager(carrierConfig);

            assertEquals(mWifiConfigManager.getConfiguredNetworks().size(), maxNumWifiConfigs);
            for (WifiConfiguration savedConfig : mWifiConfigManager.getConfiguredNetworks()) {
                if (savedConfig.networkId == configToDelete.networkId) {
                    fail("Config was not deleted: " + configToDelete);
                }
            }
        }
    }

    /**
     * Verifies that the limit on app-added networks is enforced when an app attempts to add
     * a new network. Configs added by a system app should not be removed, even if they have a
     * higher overall deletion priority.
     */
    @Test
    public void testRemoveExcessAppAddedNetworksOnAdd() {
        final int maxTotalConfigs = 6;
        final int maxAppAddedConfigs = 4;
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurations, maxTotalConfigs);
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurationsAddedByAllApps,
                maxAppAddedConfigs);

        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), any())).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), any())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(any(), eq(TEST_CREATOR_UID)))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(any(), eq(TEST_OTHER_USER_UID)))
                .thenReturn(false);

        WifiConfiguration currentConfig = WifiConfigurationTestUtil.createPskNetwork();
        currentConfig.status = WifiConfiguration.Status.CURRENT;
        currentConfig.isCurrentlyConnected = true;
        WifiConfiguration lessDeletionPriorityConfig = WifiConfigurationTestUtil.createPskNetwork();
        lessDeletionPriorityConfig.setDeletionPriority(1);
        WifiConfiguration newlyAddedConfig = WifiConfigurationTestUtil.createPskNetwork();
        newlyAddedConfig.lastUpdated = 1;
        WifiConfiguration recentConfig = WifiConfigurationTestUtil.createPskNetwork();
        recentConfig.lastConnected = 2;
        WifiConfiguration notRecentConfig = WifiConfigurationTestUtil.createPskNetwork();
        notRecentConfig.lastConnected = 1;
        WifiConfiguration oneRebootSaeConfig = WifiConfigurationTestUtil.createSaeNetwork();
        oneRebootSaeConfig.numRebootsSinceLastUse = 1;
        WifiConfiguration oneRebootOpenConfig = WifiConfigurationTestUtil.createOpenNetwork();
        oneRebootOpenConfig.numRebootsSinceLastUse = 1;
        oneRebootOpenConfig.numAssociation = 1;
        WifiConfiguration noAssociationConfig = WifiConfigurationTestUtil.createOpenNetwork();
        noAssociationConfig.numRebootsSinceLastUse = 1;
        noAssociationConfig.numAssociation = 0;

        // System-added configs. Have high overall deletion priority.
        verifyAddNetworkToWifiConfigManager(oneRebootOpenConfig);
        verifyAddNetworkToWifiConfigManager(noAssociationConfig);

        // App-added configs. Have low overall deletion priority.
        List<WifiConfiguration> appAddedConfigsInDeletionOrder = new ArrayList<>();
        appAddedConfigsInDeletionOrder.add(recentConfig);
        appAddedConfigsInDeletionOrder.add(newlyAddedConfig);
        appAddedConfigsInDeletionOrder.add(lessDeletionPriorityConfig);
        appAddedConfigsInDeletionOrder.add(currentConfig);
        for (int i = 0; i < appAddedConfigsInDeletionOrder.size(); i++) {
            WifiConfiguration config = appAddedConfigsInDeletionOrder.get(i);
            config.creatorUid = TEST_OTHER_USER_UID;
        }

        for (WifiConfiguration config : appAddedConfigsInDeletionOrder) {
            verifyAddNetworkToWifiConfigManager(config);
        }
        assertEquals(mWifiConfigManager.getConfiguredNetworks().size(), maxTotalConfigs);

        // Adding new app-added configs should only overwrite the existing app-added configs.
        for (WifiConfiguration configToDelete : appAddedConfigsInDeletionOrder) {
            WifiConfiguration carrierConfig = WifiConfigurationTestUtil.createPskNetwork();
            carrierConfig.carrierId = 1;
            carrierConfig.creatorUid = TEST_OTHER_USER_UID;
            verifyAddNetworkToWifiConfigManager(carrierConfig);

            assertEquals(mWifiConfigManager.getConfiguredNetworks().size(), maxTotalConfigs);
            for (WifiConfiguration savedConfig : mWifiConfigManager.getConfiguredNetworks()) {
                if (savedConfig.networkId == configToDelete.networkId) {
                    fail("Config was not deleted: " + configToDelete);
                }
            }
        }

        // Check that the system-added configs are still stored.
        for (WifiConfiguration systemAddedConfig :
                Arrays.asList(oneRebootOpenConfig, noAssociationConfig)) {
            boolean found = false;
            for (WifiConfiguration storedConfig : mWifiConfigManager.getConfiguredNetworks()) {
                if (systemAddedConfig.networkId == storedConfig.networkId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                fail("System-added config was deleted: " + systemAddedConfig);
            }
        }
    }

    /**
     * Verifies that excess app-added networks are only considered for removal
     * if the total number of networks reaches the app-added limit.
     *
     * This directly tests an optimization for enforcing the app-added limit. Retrieving the
     * list of app-added configs is expensive (since we need to check the permissions on
     * all the stored configs), so we should only retrieve it when necessary.
     */
    @Test
    public void testFilterAtAppAddedLimit() {
        final int maxTotalConfigs = 4;
        final int maxAppAddedConfigs = 3;
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurations, maxTotalConfigs);
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurationsAddedByAllApps,
                maxAppAddedConfigs);

        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), any())).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), any())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(any(), eq(TEST_CREATOR_UID)))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(any(), eq(TEST_OTHER_USER_UID)))
                .thenReturn(false);

        // Add the maximum number of app-added configs.
        for (int i = 0; i < maxAppAddedConfigs; i++) {
            WifiConfiguration appAddedConfig = WifiConfigurationTestUtil.createPskNetwork();
            appAddedConfig.creatorUid = TEST_OTHER_USER_UID;
            verifyAddNetworkToWifiConfigManager(appAddedConfig);
        }
        assertEquals(maxAppAddedConfigs, mWifiConfigManager.getConfiguredNetworks().size());

        // Since the app-added limit was not exceeded, the app-added config list was not retrieved.
        // We only expect a single permission check per add.
        int expectedNumPermissionChecks = maxAppAddedConfigs;
        verify(mWifiPermissionsUtil, times(expectedNumPermissionChecks))
                .isProfileOwner(anyInt(), any());

        // The next app-added config will trigger the retrieval of the app-added config list.
        // Expect the normal permission check, plus one additional from when
        // the app-added config list is generated.
        WifiConfiguration appAddedConfig = WifiConfigurationTestUtil.createPskNetwork();
        appAddedConfig.creatorUid = TEST_OTHER_USER_UID;
        verifyAddNetworkToWifiConfigManager(appAddedConfig);
        assertEquals(maxAppAddedConfigs, mWifiConfigManager.getConfiguredNetworks().size());

        expectedNumPermissionChecks += 2;
        verify(mWifiPermissionsUtil, times(expectedNumPermissionChecks))
                .isProfileOwner(anyInt(), any());

        // Adding a system config will not trigger the retrieval of the app-added config list.
        // We only expect a single permission check for this add.
        WifiConfiguration systemConfig = WifiConfigurationTestUtil.createPskNetwork();
        systemConfig.creatorUid = TEST_CREATOR_UID;
        verifyAddNetworkToWifiConfigManager(systemConfig);
        assertEquals(maxTotalConfigs, mWifiConfigManager.getConfiguredNetworks().size());

        expectedNumPermissionChecks += 1;
        verify(mWifiPermissionsUtil, times(expectedNumPermissionChecks))
                .isProfileOwner(anyInt(), any());
    }

    /**
     * Verifies that {@link WifiConfigManager#filterNonAppAddedNetworks(List)} properly filters
     * out non app-added networks. Also checks that the permissions are cached for networks
     * with the same creator.
     */
    @Test
    public void testFilterNonAppAddedNetworks() {
        int totalConfigs = 3;
        List<WifiConfiguration> configs = new ArrayList<>();
        for (int i = 0; i < totalConfigs; i++) {
            configs.add(WifiConfigurationTestUtil.createPskNetwork());
        }

        // Configs 0 and 1 will belong to the same creator.
        configs.get(0).creatorUid = 1;
        configs.get(1).creatorUid = 1;
        configs.get(2).creatorUid = 2;

        configs.get(0).creatorName = "common";
        configs.get(1).creatorName = "common";
        configs.get(2).creatorName = "different";

        // UIDs 1 and 2 belong to a PO and an app, respectively.
        when(mWifiPermissionsUtil.isProfileOwner(eq(1), anyString())).thenReturn(true);
        when(mWifiPermissionsUtil.isProfileOwner(eq(2), anyString())).thenReturn(false);
        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), any())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(any(), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isSignedWithPlatformKey(anyInt())).thenReturn(false);

        // Verify that the non app-added networks (those created by UID 1) are filtered out.
        List<WifiConfiguration> appAddedNetworks =
                mWifiConfigManager.filterNonAppAddedNetworks(configs);
        assertEquals(1, appAddedNetworks.size());

        // Permissions should only be checked once per unique creator.
        verify(mWifiPermissionsUtil, times(2)).isProfileOwner(anyInt(), any());
    }

    @Test
    public void testNetworkValidation() {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();

        NetworkUpdateResult result = verifyAddNetworkToWifiConfigManager(openNetwork);

        // Set internet to be validated and verify internet validation tracking is updated.
        int networkId = result.getNetworkId();
        mWifiConfigManager.setNetworkValidatedInternetAccess(networkId, true);
        WifiConfiguration updatedConfig = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue(updatedConfig.getNetworkSelectionStatus().hasEverValidatedInternetAccess());
        assertTrue(updatedConfig.validatedInternetAccess);

        // Set internet validation failed now and verify again. hasEverValidatedInternetAccess
        // should still be true but validatedInternetAccess should be false.
        mWifiConfigManager.setNetworkValidatedInternetAccess(networkId, false);
        updatedConfig = mWifiConfigManager.getConfiguredNetwork(networkId);
        assertTrue(updatedConfig.getNetworkSelectionStatus().hasEverValidatedInternetAccess());
        assertFalse(updatedConfig.validatedInternetAccess);
    }

    /**
     * Verifies that adding a network fails if the amount of saved networks is maxed out and the
     * next network to be removed is the same as the added network.
     */
    @Test
    public void testAddNetworkFailsIfNetworkMustBeRemoved() {
        mResources.setInteger(R.integer.config_wifiMaxNumWifiConfigurations, 5);
        // Max out number of configurations with SAE networks
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createSaeNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createSaeNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createSaeNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createSaeNetwork());
        verifyAddNetworkToWifiConfigManager(WifiConfigurationTestUtil.createSaeNetwork());

        // Add an open network, which should be deleted before SAE networks
        NetworkUpdateResult result = addNetworkToWifiConfigManager(
                WifiConfigurationTestUtil.createOpenNetwork());

        assertFalse(result.isSuccess());
    }

    /**
     * Verifies that isUserSelected() is set after a connection and cleared after disconnection.
     */
    @Test
    public void testIsUserSelectedUpdatedAfterConnectDisnect() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkToWifiConfigManager(config);
        mWifiConfigManager.incrementNumRebootsSinceLastUse();
        assertEquals(false,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).isUserSelected());

        mWifiConfigManager.updateNetworkAfterConnect(config.networkId, true, false, TEST_RSSI);

        assertEquals(true,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).isUserSelected());

        mWifiConfigManager.updateNetworkAfterDisconnect(config.networkId);

        assertEquals(false,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).isUserSelected());
    }

    /**
     * Verifies that numRebootsSinceLastUse is reset after a connection.
     */
    @Test
    public void testNumRebootsSinceLastUseUpdatedAfterConnect() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkToWifiConfigManager(config);
        mWifiConfigManager.incrementNumRebootsSinceLastUse();
        assertEquals(1,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).numRebootsSinceLastUse);

        mWifiConfigManager.updateNetworkAfterConnect(config.networkId, false, false, TEST_RSSI);

        assertEquals(0,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).numRebootsSinceLastUse);
    }

    /**
     * Verifies that numRebootsSinceLastUse is reset after a config update.
     */
    @Test
    public void testNumRebootsSinceLastUseUpdatedAfterConfigUpdate() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        verifyAddNetworkToWifiConfigManager(config);
        mWifiConfigManager.incrementNumRebootsSinceLastUse();
        assertEquals(1,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).numRebootsSinceLastUse);

        verifyUpdateNetworkToWifiConfigManager(config);

        assertEquals(0,
                mWifiConfigManager.getConfiguredNetwork(config.networkId).numRebootsSinceLastUse);
    }

    /**
     * Verifies the addition of a network with incomplete security parameters using
     * {@link WifiConfigManager#addOrUpdateNetwork(WifiConfiguration, int)} does not cause a crash.
     */
    @Test
    public void testAddIncompleteEnterpriseConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"someNetwork\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        // EAP method is kept as Eap.NONE - should not crash, but return invalid ID
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        assertTrue(result.getNetworkId() == WifiConfiguration.INVALID_NETWORK_ID);

        // Now add this network correctly
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        result = addNetworkToWifiConfigManager(config);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);

        // Now try to update
        config = new WifiConfiguration();
        config.SSID = "\"someNetwork\"";
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        config.networkId = result.getNetworkId();
        NetworkUpdateResult updateResult = addNetworkToWifiConfigManager(config);
        assertTrue(result.getNetworkId() == updateResult.getNetworkId());
    }

    private WifiConfiguration prepareTofuEapConfig(int eapMethod, int phase2Method) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"TofuSsid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.enterpriseConfig.setEapMethod(eapMethod);
        config.enterpriseConfig.setPhase2Method(phase2Method);
        config.enterpriseConfig.enableTrustOnFirstUse(true);
        return config;
    }

    private void verifyAddTofuEnterpriseConfig(boolean isTofuSupported) {
        long featureSet = isTofuSupported ? WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE : 0L;
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(featureSet);

        WifiConfiguration config = prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE);
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        if (isTofuSupported) {
            assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        } else {
            assertEquals(WifiConfiguration.INVALID_NETWORK_ID,
                    WifiConfiguration.INVALID_NETWORK_ID);
        }

        // The config uses EAP-SIM type which does not use Server Cert.
        config = prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig.enableTrustOnFirstUse(true);
        result = addNetworkToWifiConfigManager(config);
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, result.getNetworkId());

        // The config alraedy has Root CA cert.
        config = prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig.setCaPath("/path/to/ca");
        config.enterpriseConfig.enableTrustOnFirstUse(true);
        result = addNetworkToWifiConfigManager(config);
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, result.getNetworkId());
    }

    /**
     * Verifies the addition of a network with Trust On First Use support.
     */
    @Test
    public void testAddTofuEnterpriseConfigWithTofuSupport() {
        verifyAddTofuEnterpriseConfig(true);
    }

    /**
     * Verifies the addition of a network without Trust On First Use support.
     */
    @Test
    public void testAddTofuEnterpriseConfigWithoutTofuSupport() {
        verifyAddTofuEnterpriseConfig(false);
    }

    @Test
    public void testCustomDhcpOptions() {
        assumeTrue(SdkLevel.isAtLeastT());
        byte[] oui1 = new byte[]{0x01, 0x02, 0x03};
        byte[] oui2 = new byte[]{0x04, 0x05, 0x06};
        byte[] oui3 = new byte[]{0x07, 0x08, 0x09};

        ArrayList<DhcpOption> option1 = new ArrayList<DhcpOption>(
                Arrays.asList(new DhcpOption((byte) 0x10, new byte[]{0x11, 0x12})));
        ArrayList<DhcpOption> option2 = new ArrayList<DhcpOption>(
                Arrays.asList(new DhcpOption((byte) 0x13, new byte[]{0x14, 0x15})));
        ArrayList<DhcpOption> option3 = new ArrayList<DhcpOption>(
                Arrays.asList(new DhcpOption((byte) 0x16, new byte[]{0x17})));
        ArrayList<DhcpOption> option23 = new ArrayList<DhcpOption>();
        option23.addAll(option2);
        option23.addAll(option3);

        mWifiConfigManager.addCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), oui1, option1);
        mWifiConfigManager.addCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), oui2, option2);
        mWifiConfigManager.addCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), oui3, option3);
        assertEquals(1, mWifiConfigManager.getCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), Collections.singletonList(oui1)).size());
        assertEquals(1, mWifiConfigManager.getCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), Collections.singletonList(oui2)).size());
        assertEquals(2, mWifiConfigManager.getCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), Arrays.asList(oui2, oui3)).size());

        mWifiConfigManager.removeCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), oui1);
        assertEquals(0, mWifiConfigManager.getCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), Collections.singletonList(oui1)).size());
        List<DhcpOption> actual2 = mWifiConfigManager.getCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID), Collections.singletonList(oui2));
        assertEquals(option2, actual2);
        List<DhcpOption> actual23 = mWifiConfigManager
                .getCustomDhcpOptions(WifiSsid.fromString(TEST_SSID), Arrays.asList(oui2, oui3));
        assertTrue(option23.size() == actual23.size());
        assertTrue(option23.containsAll(actual23));
        assertTrue(actual23.containsAll(option23));
    }

    @Test
    public void testAddOnNetworkUpdateListenerNull() throws Exception {
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        mWifiConfigManager.addOnNetworkUpdateListener(mListener);
        mWifiConfigManager.addOnNetworkUpdateListener(null);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkToWifiConfigManager(openNetwork);
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(openNetwork, TEST_CREATOR_UID);
        verify(mListener).onNetworkAdded(wifiConfigCaptor.capture());
        assertEquals(openNetwork.networkId, wifiConfigCaptor.getValue().networkId);
    }

    @Test
    public void testRemoveOnNetworkUpdateListenerNull() throws Exception {
        mWifiConfigManager.addOnNetworkUpdateListener(mListener);
        mWifiConfigManager.addOnNetworkUpdateListener(null);
        mWifiConfigManager.removeOnNetworkUpdateListener(null);
        mWifiConfigManager.removeOnNetworkUpdateListener(mListener);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        verifyAddNetworkToWifiConfigManager(openNetwork);
        NetworkUpdateResult result =
                mWifiConfigManager.addOrUpdateNetwork(openNetwork, TEST_CREATOR_UID);
        verify(mListener, never()).onNetworkAdded(anyObject());
    }

    private int verifyAddNetwork(WifiConfiguration config, boolean expectNew) {
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        assertTrue(result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID);
        assertEquals(expectNew, result.isNewNetwork());
        return result.getNetworkId();
    }

    @Test
    public void testUpdateCaCertificateSuccess() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);

        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        assertTrue(mWifiConfigManager.updateCaCertificate(eapPeapNetId, FakeKeys.CA_CERT0,
                FakeKeys.CA_CERT1, null, false));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertFalse(config.enterpriseConfig.isTrustOnFirstUseEnabled());
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(FakeKeys.CA_CERT0, config.enterpriseConfig.getCaCertificate());
    }

    @Test
    public void testUpdateCaCertificatePathSuccess() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);

        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        assertTrue(mWifiConfigManager.updateCaCertificate(eapPeapNetId, FakeKeys.CA_CERT0,
                FakeKeys.CA_CERT1, null, true));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertFalse(config.enterpriseConfig.isTrustOnFirstUseEnabled());
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(WifiConfigurationUtil.getSystemTrustStorePath(),
                config.enterpriseConfig.getCaPath());
        assertEquals(null, config.enterpriseConfig.getCaCertificate());
        assertEquals("", config.enterpriseConfig.getCaCertificateAlias());
    }

    @Test
    public void testUpdateCaCertificateWithoutAltSubjectNames() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);

        verifyAddNetwork(WifiConfigurationTestUtil.createOpenNetwork(), true);
        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        verifyAddNetwork(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE), true);

        X509Certificate mockServerCert = mock(X509Certificate.class);
        X500Principal mockSubjectDn = mock(X500Principal.class);
        when(mockServerCert.getSubjectX500Principal()).thenReturn(mockSubjectDn);
        when(mockSubjectDn.getName()).thenReturn(
                "C=TW,ST=Taiwan,L=Taipei,O=Google,CN=mockServerCert");

        assertTrue(mWifiConfigManager.updateCaCertificate(eapPeapNetId, FakeKeys.CA_CERT0,
                mockServerCert, "1234", false));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertFalse(config.enterpriseConfig.isTrustOnFirstUseEnabled());
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals("mockServerCert", config.enterpriseConfig.getDomainSuffixMatch());
        assertEquals("", config.enterpriseConfig.getAltSubjectMatch());
    }

    @Test
    public void testUpdateCaCertificateWithAltSubjectNames() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);

        verifyAddNetwork(WifiConfigurationTestUtil.createOpenNetwork(), true);
        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        verifyAddNetwork(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE), true);

        X509Certificate mockServerCert = mock(X509Certificate.class);
        X500Principal mockSubjectPrincipal = mock(X500Principal.class);
        when(mockServerCert.getSubjectX500Principal()).thenReturn(mockSubjectPrincipal);
        when(mockSubjectPrincipal.getName()).thenReturn(
                "C=TW,ST=Taiwan,L=Taipei,O=Google,CN=mockServerCert");
        List<List<?>> altNames = new ArrayList<>();
        // DNS name 1 with type 2
        altNames.add(Arrays.asList(new Object[]{2, "wifi.android"}));
        // EMail with type 1
        altNames.add(Arrays.asList(new Object[]{1, "test@wifi.com"}));
        // DNS name 2 with type 2
        altNames.add(Arrays.asList(new Object[]{2, "network.android"}));
        // RID name 2 with type 8, this one should be ignored.
        altNames.add(Arrays.asList(new Object[]{8, "1.2.3.4"}));
        // URI name with type 6
        altNames.add(Arrays.asList(new Object[]{6, "http://test.android.com"}));
        when(mockServerCert.getSubjectAlternativeNames()).thenReturn(altNames);

        assertTrue(mWifiConfigManager.updateCaCertificate(eapPeapNetId, FakeKeys.CA_CERT0,
                mockServerCert, "1234", false));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertFalse(config.enterpriseConfig.isTrustOnFirstUseEnabled());
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals("mockServerCert", config.enterpriseConfig.getDomainSuffixMatch());
        assertEquals("DNS:wifi.android;EMAIL:test@wifi.com;DNS:network.android;"
                + "URI:http://test.android.com",
                config.enterpriseConfig.getAltSubjectMatch());
    }

    @Test
    public void testUpdateCaCertificateFaiulreInvalidArgument() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);

        int openNetId = verifyAddNetwork(WifiConfigurationTestUtil.createOpenNetwork(), true);
        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        int eapSimNetId = verifyAddNetwork(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE), true);

        // Invalid network id
        assertFalse(mWifiConfigManager.updateCaCertificate(-1, FakeKeys.CA_CERT0,
                FakeKeys.CA_CERT1, "1234", false));

        // Not an enterprise network
        assertFalse(mWifiConfigManager.updateCaCertificate(openNetId, FakeKeys.CA_CERT0,
                FakeKeys.CA_CERT1, "1234", false));

        // Not a certificate baseed enterprise network
        assertFalse(mWifiConfigManager.updateCaCertificate(eapSimNetId, FakeKeys.CA_CERT0,
                FakeKeys.CA_CERT1, "1234", false));

        // No cert
        assertFalse(mWifiConfigManager.updateCaCertificate(eapPeapNetId, null, null, null,
                false));

        // No valid subject
        X509Certificate mockServerCert = mock(X509Certificate.class);
        X500Principal mockSubjectPrincipal = mock(X500Principal.class);
        when(mockServerCert.getSubjectX500Principal()).thenReturn(mockSubjectPrincipal);
        when(mockSubjectPrincipal.getName()).thenReturn("");
        assertFalse(mWifiConfigManager.updateCaCertificate(eapPeapNetId, FakeKeys.CA_CERT0,
                mockServerCert, "1234", false));
    }

    @Test
    public void testUpdateCaCertificateSuccessWithSelfSignedCertificate() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);
        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        X509Certificate mockCaCert = mock(X509Certificate.class);
        when(mockCaCert.getBasicConstraints()).thenReturn(-1);
        assertTrue(mWifiConfigManager.updateCaCertificate(eapPeapNetId, mockCaCert,
                FakeKeys.CA_CERT1, null, false));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertFalse(config.enterpriseConfig.isTrustOnFirstUseEnabled());
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals(mockCaCert, config.enterpriseConfig.getCaCertificate());
    }

    @Test
    public void testUpdateServerCertificateHashSuccess() throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);
        int eapPeapNetId = verifyAddNetwork(prepareTofuEapConfig(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        assertTrue(mWifiConfigManager.updateCaCertificate(eapPeapNetId, FakeKeys.CA_CERT1,
                FakeKeys.CA_CERT1, "1234", false));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertFalse(config.enterpriseConfig.isTrustOnFirstUseEnabled());
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());
        assertEquals("hash://server/sha256/1234", config.enterpriseConfig.getCaCertificateAlias());
    }

    @Test
    public void testUpdateCaCertificateFailureWithSelfSignedCertificateAndTofuNotEnabled()
            throws Exception {
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);
        int eapPeapNetId = verifyAddNetwork(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE), true);
        X509Certificate mockCaCert = mock(X509Certificate.class);
        when(mockCaCert.getBasicConstraints()).thenReturn(-1);
        assertFalse(mWifiConfigManager.updateCaCertificate(eapPeapNetId, mockCaCert,
                FakeKeys.CA_CERT1, null, false));
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(eapPeapNetId);
        assertEquals(null, config.enterpriseConfig.getCaCertificate());
    }

    @Test
    public void testUpdateNetworkWithCreatorOverride() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        int openNetId = verifyAddNetwork(WifiConfigurationTestUtil.createOpenNetwork(), true);
        assertEquals(TEST_CREATOR_UID, mWifiConfigManager
                .getConfiguredNetwork(openNetId).creatorUid);
        config.networkId = openNetId;
        NetworkUpdateResult result = mWifiConfigManager.addOrUpdateNetwork(
                config, TEST_UPDATE_UID, TEST_UPDATE_NAME, true);
        assertEquals(openNetId, result.getNetworkId());

        assertEquals(TEST_UPDATE_UID, mWifiConfigManager
                .getConfiguredNetwork(openNetId).creatorUid);
        assertEquals(TEST_UPDATE_NAME, mWifiConfigManager
                .getConfiguredNetwork(openNetId).creatorName);
    }

    @Test
    public void testAddNetworkWithHexadecimalSsid() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.SSID = UNTRANSLATED_HEX_SSID.toUpperCase();
        WifiSsid translatedSsid = getTranslatedSsid(WifiSsid.fromString(openNetwork.SSID));
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        // Verify the profile keys with translated SSIDs are the same
        openNetwork.SSID = translatedSsid.toString();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        // Verify the retrieved SSID is all lowercase.
        assertEquals(retrievedNetworks.get(0).SSID, retrievedNetworks.get(0).SSID.toLowerCase());
        // Verify the retrieved SSID is translated.
        assertEquals(retrievedNetworks.get(0).SSID, translatedSsid.toString());
    }

    @Test
    public void testAddNetworkWithHexadecimalSsidWithLongTranslation() {
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        // Create a 32 byte SSID
        openNetwork.SSID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        // Translation adds an extra byte over the 32 byte limit
        WifiSsid translatedSsid = getTranslatedSsid(WifiSsid.fromString(openNetwork.SSID));
        List<WifiConfiguration> networks = new ArrayList<>();
        networks.add(openNetwork);

        verifyAddNetworkToWifiConfigManager(openNetwork);

        List<WifiConfiguration> retrievedNetworks =
                mWifiConfigManager.getConfiguredNetworksWithPasswords();
        // Verify the profile keys with translated SSIDs are the same
        openNetwork.SSID = translatedSsid.toString();
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigManagerAddOrUpdate(
                networks, retrievedNetworks);
        // Verify the retrieved SSID is all lowercase.
        assertEquals(retrievedNetworks.get(0).SSID, retrievedNetworks.get(0).SSID.toLowerCase());
        // Verify the retrieved SSID is translated.
        assertEquals(retrievedNetworks.get(0).SSID, translatedSsid.toString());
        // Verify the retrieved SSID byte length is greater than 32.
        assertTrue(WifiSsid.fromString(retrievedNetworks.get(0).SSID).getBytes().length > 32);
    }

    @Test
    public void testAddNetworkWithInvalidHexadecimalSsidFails() {
        // Create a 2 and a half byte SSID
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.SSID = "abcde";

        NetworkUpdateResult result = addNetworkToWifiConfigManager(openNetwork);

        assertFalse(result.isSuccess());
    }

    /**
     * Verify that if the caller has NETWORK_SETTINGS permission, and the overlay
     * config_wifiAllowInsecureEnterpriseConfigurationsForSettingsAndSUW is set, then it can add an
     * insecure Enterprise network, with Root CA certificate not set and/or domain name not set.
     */
    @Test
    public void testAddInsecureEnterpriseNetworkWithNetworkSettingsPerm() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        // First set flag to not allow
        when(mWifiGlobals.isInsecureEnterpriseConfigurationAllowed()).thenReturn(false);

        // Create an insecure Enterprise network
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setCaPath(null);
        config.enterpriseConfig.setDomainSuffixMatch(null);
        assertFalse(config.enterpriseConfig.isUserApproveNoCaCert());

        // Verify operation fails
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        assertFalse(result.isSuccess());
        assertEquals(STATUS_INVALID_CONFIGURATION_ENTERPRISE, result.getStatusCode());

        // Set flag to allow
        when(mWifiGlobals.isInsecureEnterpriseConfigurationAllowed()).thenReturn(true);

        // Verify operation succeeds
        NetworkUpdateResult res = addNetworkToWifiConfigManager(config);
        assertTrue(res.isSuccess());
        config = mWifiConfigManager.getConfiguredNetwork(res.getNetworkId());
        assertNotNull(config);
        assertTrue(config.enterpriseConfig.isUserApproveNoCaCert());
    }

    /**
     * Verify that if the caller does NOT have NETWORK_SETTINGS permission, then it cannot add an
     * insecure Enterprise network, with Root CA certificate not set and/or domain name not set,
     * regardless of the overlay config_wifiAllowInsecureEnterpriseConfigurationsForSettingsAndSUW
     * value.
     */
    @Test
    public void testAddInsecureEnterpriseNetworkWithNoNetworkSettingsPerm() throws Exception {

        // First set flag to not allow
        when(mWifiGlobals.isInsecureEnterpriseConfigurationAllowed()).thenReturn(false);

        // Create an insecure Enterprise network
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setCaPath(null);
        config.enterpriseConfig.setDomainSuffixMatch(null);

        // Verify operation fails
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        assertFalse(result.isSuccess());
        assertEquals(STATUS_INVALID_CONFIGURATION_ENTERPRISE, result.getStatusCode());

        // Set flag to allow
        when(mWifiGlobals.isInsecureEnterpriseConfigurationAllowed()).thenReturn(true);

        // Verify operation still fails
        assertFalse(addNetworkToWifiConfigManager(config).isSuccess());
    }

    /**
     * Verify that the send DHCP hostname setting is correctly updated
     */
    @Test
    public void testAddNetworkUpdatesSendDhcpHostname() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.setSendDhcpHostnameEnabled(false);

        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);

        assertTrue(result.isSuccess());
        config = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertFalse(config.isSendDhcpHostnameEnabled());

        config.setSendDhcpHostnameEnabled(true);
        result = updateNetworkToWifiConfigManager(config);

        assertTrue(result.isSuccess());
        config = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertTrue(config.isSendDhcpHostnameEnabled());
    }

    /**
     * Verify that changing the DHCP hostname setting fails without NETWORK_SETTINGS or
     * NETWORK_SETUP_WIZARD permissions.
     */
    @Test
    public void testUpdateSendDhcpHostnameFailsWithoutSettingsOrSuwPermission() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_CREATOR_UID))
                .thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(TEST_CREATOR_UID))
                .thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_UPDATE_UID))
                .thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(TEST_UPDATE_UID))
                .thenReturn(false);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        // Fail to add if dhcp hostname setting is set to DO_NOT_SEND
        config.setSendDhcpHostnameEnabled(false);
        NetworkUpdateResult result = addNetworkToWifiConfigManager(config);
        assertFalse(result.isSuccess());

        // Can add if dhcp hostname setting is reset to default SEND
        config.setSendDhcpHostnameEnabled(true);
        result = addNetworkToWifiConfigManager(config);
        assertTrue(result.isSuccess());
        config = mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        assertTrue(config.isSendDhcpHostnameEnabled());

        // Fail to update if dchp hostname setting is set to DO_NOT_SEND
        config.setSendDhcpHostnameEnabled(false);
        result = updateNetworkToWifiConfigManager(config);
        assertFalse(result.isSuccess());
    }
}
