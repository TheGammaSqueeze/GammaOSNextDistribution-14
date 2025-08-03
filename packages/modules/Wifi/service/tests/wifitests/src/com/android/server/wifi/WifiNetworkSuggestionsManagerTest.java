/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_CHANGE_WIFI_STATE;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_LINGER;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.wifi.WifiNetworkSuggestionsManager.DEFAULT_LINGER_DELAY_MS;
import static com.android.server.wifi.WifiNetworkSuggestionsManager.NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION;
import static com.android.server.wifi.WifiNetworkSuggestionsManager.NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION;
import static com.android.server.wifi.WifiNetworkSuggestionsManager.NOTIFICATION_USER_DISMISSED_INTENT_ACTION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.EAPConstants;
import android.net.wifi.ISuggestionConnectionStatusListener;
import android.net.wifi.ISuggestionUserApprovalStatusListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.view.LayoutInflater;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.MacAddressUtils;
import com.android.server.wifi.WifiNetworkSuggestionsManager.ExtendedWifiNetworkSuggestion;
import com.android.server.wifi.WifiNetworkSuggestionsManager.PerAppInfo;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.util.LruConnectionTracker;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link com.android.server.wifi.WifiNetworkSuggestionsManager}.
 */
@SmallTest
public class WifiNetworkSuggestionsManagerTest extends WifiBaseTest {

    private static final String TEST_PACKAGE_1 = "com.test12345";
    private static final String TEST_PACKAGE_2 = "com.test54321";
    private static final String TEST_PACKAGE_3 = "com.test54321";
    private static final String TEST_APP_NAME_1 = "test12345";
    private static final String TEST_APP_NAME_2 = "test54321";
    private static final String TEST_APP_NAME_3 = "test66666";
    private static final String TEST_FEATURE = "testFeature";
    private static final String TEST_BSSID = "00:11:22:33:44:55";
    private static final String TEST_FQDN = "FQDN";
    private static final String TEST_FRIENDLY_NAME = "test_friendly_name";
    private static final String TEST_REALM = "realm.test.com";
    private static final String TEST_CARRIER_NAME = "test_carrier";
    private static final int TEST_UID_1 = 5667;
    private static final int TEST_UID_2 = 4537;
    private static final int TEST_UID_3 = 7837;
    private static final int VALID_CARRIER_ID = 100;
    private static final int TEST_SUBID = 1;
    private static final int TEST_NETWORK_ID = 110;
    private static final int TEST_CARRIER_ID = 1911;
    private static final String TEST_IMSI = "123456*";
    private static final int DEFAULT_PRIORITY_GROUP = 0;
    private static final int TEST_PRIORITY_GROUP = 1;
    private static final String TEST_ANONYMOUS_IDENTITY = "AnonymousIdentity";
    private static final String USER_CONNECT_CHOICE = "SomeNetworkProfileId";
    private static final int TEST_RSSI = -50;
    private static final ParcelUuid GROUP_UUID = ParcelUuid
            .fromString("0000110B-0000-1000-8000-00805F9B34FB");
    private static final String UNTRANSLATED_HEX_SSID = "abcdef";

    private @Mock WifiContext mContext;
    private @Mock Resources mResources;
    private @Mock AppOpsManager mAppOpsManager;
    private @Mock WifiNotificationManager mWifiNotificationManager;
    private @Mock WifiDialogManager mWifiDialogManager;
    private @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    private @Mock WifiInjector mWifiInjector;
    private @Mock FrameworkFacade mFrameworkFacade;
    private @Mock WifiConfigStore mWifiConfigStore;
    private @Mock WifiConfigManager mWifiConfigManager;
    private @Mock NetworkSuggestionStoreData mNetworkSuggestionStoreData;
    private @Mock WifiMetrics mWifiMetrics;
    private @Mock WifiCarrierInfoManager mWifiCarrierInfoManager;
    private @Mock PasspointManager mPasspointManager;
    private @Mock ISuggestionConnectionStatusListener mConnectionStatusListener;
    private @Mock ISuggestionUserApprovalStatusListener mUserApprovalStatusListener;
    private @Mock IBinder mBinder;
    private @Mock ActivityManager mActivityManager;
    private @Mock WifiScoreCard mWifiScoreCard;
    private @Mock WifiKeyStore mWifiKeyStore;
    private @Mock WifiDialogManager.DialogHandle mDialogHandle;
    private @Mock Notification.Builder mNotificationBuilder;
    private @Mock Notification mNotification;
    private @Mock LruConnectionTracker mLruConnectionTracker;
    private @Mock ActiveModeWarden mActiveModeWarden;
    private @Mock ClientModeManager mPrimaryClientModeManager;
    private @Mock WifiGlobals mWifiGlobals;
    private @Mock SsidTranslator mSsidTranslator;
    private @Mock Clock mClock;
    private MockitoSession mStaticMockSession = null;
    private TestLooper mLooper;
    private final ArgumentCaptor<AppOpsManager.OnOpChangedListener> mAppOpChangedListenerCaptor =
            ArgumentCaptor.forClass(AppOpsManager.OnOpChangedListener.class);
    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    private final ArgumentCaptor<WifiCarrierInfoManager.OnImsiProtectedOrUserApprovedListener>
            mUserApproveCarrierListenerArgumentCaptor = ArgumentCaptor.forClass(
            WifiCarrierInfoManager.OnImsiProtectedOrUserApprovedListener.class);
    private final ArgumentCaptor<WifiConfigManager.OnNetworkUpdateListener> mNetworkListenerCaptor =
            ArgumentCaptor.forClass(WifiConfigManager.OnNetworkUpdateListener.class);

    private InOrder mInorder;

    private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private NetworkSuggestionStoreData.DataSource mDataSource;

    /**
     * Setup the mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession = mockitoSession()
                .mockStatic(WifiInjector.class)
                .startMocking();
        lenient().when(WifiInjector.getInstance()).thenReturn(mWifiInjector);
        mLooper = new TestLooper();

        mInorder = inOrder(mContext, mWifiPermissionsUtil);

        when(mWifiInjector.makeNetworkSuggestionStoreData(any()))
                .thenReturn(mNetworkSuggestionStoreData);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mFrameworkFacade);
        when(mWifiInjector.getPasspointManager()).thenReturn(mPasspointManager);
        when(mWifiInjector.getWifiScoreCard()).thenReturn(mWifiScoreCard);
        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.getWifiNotificationManager()).thenReturn(mWifiNotificationManager);
        when(mWifiInjector.getWifiDialogManager()).thenReturn(mWifiDialogManager);
        when(mWifiInjector.getSsidTranslator()).thenReturn(mSsidTranslator);
        when(mNotificationBuilder.setSmallIcon(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setTicker(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setContentTitle(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setStyle(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setDeleteIntent(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setShowWhen(anyBoolean())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setLocalOnly(anyBoolean())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setColor(anyInt())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.addAction(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setTimeoutAfter(anyLong())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.build()).thenReturn(mNotification);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mDialogHandle);
        when(mFrameworkFacade.makeNotificationBuilder(any(), anyString()))
                .thenReturn(mNotificationBuilder);
        when(mFrameworkFacade.getBroadcast(any(), anyInt(), any(), anyInt()))
                .thenReturn(mock(PendingIntent.class));
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);
        when(mContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .thenReturn(mock(LayoutInflater.class));
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mActivityManager.isLowRamDevice()).thenReturn(false);
        when(mActivityManager.getPackageImportance(any())).thenReturn(
                IMPORTANCE_FOREGROUND_SERVICE);
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(anyInt()))
                .thenReturn(true);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_WPA3_SAE | WifiManager.WIFI_FEATURE_OWE);
        when(mWifiGlobals.isWpa3SaeUpgradeEnabled()).thenReturn(true);
        when(mWifiGlobals.isOweUpgradeEnabled()).thenReturn(true);
        when(mSsidTranslator.getAllPossibleOriginalSsids(any())).thenAnswer(
                (Answer<List<WifiSsid>>) invocation -> Arrays.asList(invocation.getArgument(0),
                        WifiSsid.fromString(UNTRANSLATED_HEX_SSID))
        );
        when(mConnectionStatusListener.asBinder()).thenReturn(mBinder);
        when(mUserApprovalStatusListener.asBinder()).thenReturn(mBinder);

        // setup resource strings for notification.
        when(mResources.getString(eq(R.string.wifi_suggestion_title), anyString()))
                .thenAnswer(s -> "blah" + s.getArguments()[1]);
        when(mResources.getString(eq(R.string.wifi_suggestion_content), anyString()))
                .thenAnswer(s -> "blah" + s.getArguments()[1]);
        when(mResources.getText(eq(R.string.wifi_suggestion_action_allow_app)))
                .thenReturn("blah");
        when(mResources.getText(eq(R.string.wifi_suggestion_action_disallow_app)))
                .thenReturn("blah");

        // test app info
        when(mFrameworkFacade.getAppName(any(), eq(TEST_PACKAGE_1), eq(TEST_UID_1)))
            .thenReturn(TEST_APP_NAME_1);
        when(mFrameworkFacade.getAppName(any(), eq(TEST_PACKAGE_2), eq(TEST_UID_2)))
                .thenReturn(TEST_APP_NAME_2);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(any())).thenReturn(
                TelephonyManager.UNKNOWN_CARRIER_ID);
        when(mWifiCarrierInfoManager.isSubIdMatchingCarrierId(anyInt(), anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        when(mWifiCarrierInfoManager.isSimReady(SubscriptionManager.INVALID_SUBSCRIPTION_ID))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(anyInt())).thenReturn(
                false);
        when(mWifiCarrierInfoManager.getActiveSubscriptionIdInGroup(GROUP_UUID))
                .thenReturn(TEST_SUBID);

        when(mWifiKeyStore.updateNetworkKeys(any(), any())).thenReturn(true);

        mWifiNetworkSuggestionsManager =
                new WifiNetworkSuggestionsManager(
                        mContext,
                        new RunnerHandler(mLooper.getLooper(), 100, new LocalLog(128)),
                        mWifiInjector,
                        mWifiPermissionsUtil,
                        mWifiConfigManager,
                        mWifiConfigStore,
                        mWifiMetrics,
                        mWifiCarrierInfoManager,
                        mWifiKeyStore,
                        mLruConnectionTracker,
                        mClock);
        mWifiNetworkSuggestionsManager.enableVerboseLogging(true);
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(), any(), any(), any());

        ArgumentCaptor<NetworkSuggestionStoreData.DataSource> dataSourceArgumentCaptor =
                ArgumentCaptor.forClass(NetworkSuggestionStoreData.DataSource.class);

        verify(mWifiInjector).makeNetworkSuggestionStoreData(dataSourceArgumentCaptor.capture());
        mDataSource = dataSourceArgumentCaptor.getValue();
        assertNotNull(mDataSource);
        mDataSource.fromDeserialized(Map.of());

        verify(mWifiCarrierInfoManager).addImsiProtectedOrUserApprovedListener(
                mUserApproveCarrierListenerArgumentCaptor.capture());
        verify(mWifiConfigManager).addOnNetworkUpdateListener(mNetworkListenerCaptor.capture());
    }

    @After
    public void cleanUp() throws Exception {
        if (null != mStaticMockSession) {
            mStaticMockSession.finishMocking();
        }
    }

    /**
     * Verify successful addition of network suggestions.
     */
    @Test
    public void testAddNetworkSuggestionsSuccess() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.restricted = true;
        placeholderConfig.trusted = false;
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion1);
            }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion2);
            }};
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), anyBoolean(), anyBoolean())).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        verify(mPasspointManager).addOrUpdateProvider(
                passpointConfiguration, TEST_UID_2, TEST_PACKAGE_2, true, false, true);
        verify(mWifiMetrics, times(2))
                .incrementNetworkSuggestionApiUsageNumOfAppInType(
                        WifiNetworkSuggestionsManager.APP_TYPE_NON_PRIVILEGED);
        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
            }};
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);

        verify(mWifiMetrics, times(2)).incrementNetworkSuggestionApiNumModification();
        verify(mWifiMetrics, times(2)).addNetworkSuggestionPriorityGroup(anyInt());
        ArgumentCaptor<List<Integer>> maxSizesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mWifiMetrics, times(2)).noteNetworkSuggestionApiListSizeHistogram(
                maxSizesCaptor.capture());
        assertNotNull(maxSizesCaptor.getValue());
        assertEquals(maxSizesCaptor.getValue(), new ArrayList<Integer>() {{ add(1); add(1); }});
    }

    /**
     * Verify successful removal of network suggestions.
     */
    @Test
    public void testRemoveNetworkSuggestionsSuccess() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion1);
            }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion2);
            }};
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));

        // Now remove all of them.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mPasspointManager).removeProvider(eq(TEST_UID_2), eq(false),
                eq(passpointConfiguration.getUniqueId()), isNull());
        verify(mWifiScoreCard).removeNetwork(anyString());
        verify(mLruConnectionTracker).removeNetwork(any());

        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());

        verify(mWifiMetrics, times(4)).incrementNetworkSuggestionApiNumModification();
        verify(mWifiMetrics, times(2)).addNetworkSuggestionPriorityGroup(anyInt());
        ArgumentCaptor<List<Integer>> maxSizesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mWifiMetrics, times(4)).noteNetworkSuggestionApiListSizeHistogram(
                maxSizesCaptor.capture());
        // Only non-passpoint suggestion will trigger remove connect choice, passpoint suggestion
        // will trigger this in passpointManager
        verify(mWifiConfigManager).removeConnectChoiceFromAllNetworks(anyString());
        assertNotNull(maxSizesCaptor.getValue());
        assertEquals(maxSizesCaptor.getValue(), new ArrayList<Integer>() {{ add(1); add(1); }});
    }

    /**
     * Add or remove suggestion before user data store loaded will fail.
     */
    @Test
    public void testAddRemoveSuggestionBeforeUserDataLoaded() {
        // Clear the data source, and user data store is not loaded
        mDataSource.reset();
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    @Test
    public void testAddRemoveEnterpriseNetworkSuggestion() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};
        when(mWifiKeyStore.updateNetworkKeys(eq(networkSuggestion1.wifiConfiguration), any()))
                .thenReturn(true);
        when(mWifiKeyStore.updateNetworkKeys(eq(networkSuggestion2.wifiConfiguration), any()))
                .thenReturn(false);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        assertEquals(1, allNetworkSuggestions.size());
        WifiNetworkSuggestion removingSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        removingSuggestion.wifiConfiguration.SSID = networkSuggestion1.wifiConfiguration.SSID;
        // Remove with the networkSuggestion from external.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(
                        new ArrayList<WifiNetworkSuggestion>() {{ add(removingSuggestion); }},
                        TEST_UID_1, TEST_PACKAGE_1,
                        WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        // Make sure remove the keyStore with the internal config
        verify(mWifiKeyStore).removeKeys(eq(networkSuggestion1.wifiConfiguration.enterpriseConfig),
                eq(false));
        verify(mLruConnectionTracker).removeNetwork(any());
    }

    @Test
    public void testAddNetworkSuggestionWithInvalidKeyChainKeyAlias() {
        assumeTrue(SdkLevel.isAtLeastS());

        final WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setClientKeyPairAlias("some-alias");
        final WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                config, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        final List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        when(mWifiKeyStore.updateNetworkKeys(eq(networkSuggestion1.wifiConfiguration), any()))
                .thenReturn(true);
        when(mWifiKeyStore.validateKeyChainAlias(any(String.class), anyInt())).thenReturn(false);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testAddNetworkSuggestionWithValidKeyChainKeyAlias() {
        assumeTrue(SdkLevel.isAtLeastS());

        final WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setClientKeyPairAlias("some-alias");
        final WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                config, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        final List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        when(mWifiKeyStore.updateNetworkKeys(eq(networkSuggestion1.wifiConfiguration), any()))
                .thenReturn(true);
        when(mWifiKeyStore.validateKeyChainAlias(any(String.class), anyInt())).thenReturn(true);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testAddInsecureEnterpriseNetworkSuggestion() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.enterpriseConfig.setCaPath(null);
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.enterpriseConfig.setDomainSuffixMatch("");
        networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testAddOemPaidNetworkSuggestionOnPreSDevices() {
        assumeFalse(SdkLevel.isAtLeastS());

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.oemPaid = true;
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testAddOemPrivateNetworkSuggestionOnPreSDevices() {
        assumeFalse(SdkLevel.isAtLeastS());

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.oemPrivate = true;
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testSetSubscriptionIdOnPreSDevices() {
        assumeFalse(SdkLevel.isAtLeastS());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.subscriptionId = TEST_SUBID;
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testSetPriorityGroupOnPreSDevices() {
        assumeFalse(SdkLevel.isAtLeastS());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                TEST_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testSetSubscriptionGroupOnPreTDevices() {
        assumeFalse(SdkLevel.isAtLeastT());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.setSubscriptionGroup(GROUP_UUID);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    /**
     * Verify successful removal of all network suggestions.
     */
    @Test
    public void testRemoveAllNetworkSuggestionsSuccess() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);


        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));

        // Now remove all of them by sending an empty list.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(new ArrayList<>(), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mLruConnectionTracker).removeNetwork(any());
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(new ArrayList<>(), TEST_UID_2,
                        TEST_PACKAGE_2, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mPasspointManager).removeProvider(eq(TEST_UID_2), eq(false),
                eq(passpointConfiguration.getUniqueId()), isNull());

        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());
    }

    /**
     * Verify successful replace (add,remove, add) of network suggestions.
     */
    @Test
    public void testReplaceNetworkSuggestionsSuccess() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion);
            }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                add(networkSuggestion);
            }};
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);
    }

    /**
     * Verify that modify networks that are already active is allowed.
     * - Adding two suggestions and not add into the WifiConfigManager - before network selection
     * - Set user connect choice, Anonymous Identity and auto-join on suggestion
     * - Adding the suggestions with same profile should succeed. And no WifiConfigManager update.
     * - After in-place modify, user connect choice, Anonymous Identity and auto-join should be
     *   preserved.
     */
    @Test
    public void testAddNetworkSuggestionsSuccessOnInPlaceModificationWhenNotInWcm() {
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);

        // Create an EAP-SIM suggestion and a passpoint suggestion
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                eapSimConfig, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};

        // Verify adding suggestion succeed.
        when(mPasspointManager.addOrUpdateProvider(any(),
                anyInt(), anyString(), eq(true), anyBoolean(), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        // Nothing in WCM.
        when(mWifiConfigManager.getConfiguredNetwork(networkSuggestion1.wifiConfiguration
                .getProfileKey())).thenReturn(null);
        when(mWifiConfigManager.getConfiguredNetwork(networkSuggestion2.wifiConfiguration
                .getProfileKey())).thenReturn(null);

        // Set user connect choice, Anonymous Identity and auto join.
        WifiConfiguration config = new WifiConfiguration(eapSimConfig);
        config.fromWifiNetworkSuggestion = true;
        config.shared = false;
        config.ephemeral = true;
        config.creatorName = TEST_PACKAGE_1;
        config.creatorUid = TEST_UID_1;
        config.enterpriseConfig.setAnonymousIdentity(TEST_ANONYMOUS_IDENTITY);
        WifiConfigManager.OnNetworkUpdateListener listener = mNetworkListenerCaptor.getValue();
        listener.onConnectChoiceSet(List.of(config), USER_CONNECT_CHOICE, TEST_RSSI);
        mWifiNetworkSuggestionsManager.setAnonymousIdentity(config);
        mWifiNetworkSuggestionsManager.allowNetworkSuggestionAutojoin(config, false);

        // Keep the same suggestion as auto-join enabled, but user already mark it disabled.
        WifiNetworkSuggestion networkSuggestion3 = createWifiNetworkSuggestion(
                eapSimConfig, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        // Modify the same suggestion to mark it auto-join disabled.
        WifiNetworkSuggestion networkSuggestion4 = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, false,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion3);
                    add(networkSuggestion4);
                }};

        // Replace attempt should success.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<ExtendedWifiNetworkSuggestion> matchedPasspointSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN);
        assertEquals(1, matchedPasspointSuggestions.size());
        // As user didn't change the auto-join, will follow the newly added one.
        assertFalse(matchedPasspointSuggestions.iterator().next().isAutojoinEnabled);

        ScanDetail scanDetail = createScanDetailForNetwork(eapSimConfig);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        assertEquals(1, matchedSuggestions.size());
        ExtendedWifiNetworkSuggestion matchedSuggestion = matchedSuggestions.iterator().next();
        // As user changes the auto-join, will keep the user choice.
        assertFalse(matchedSuggestion.isAutojoinEnabled);
        // Verify user connect choice and Anonymous Identity are preserved during the modify.
        assertEquals(TEST_ANONYMOUS_IDENTITY, matchedSuggestion.anonymousIdentity);
        assertEquals(USER_CONNECT_CHOICE, matchedSuggestion.connectChoice);
        assertEquals(TEST_RSSI, matchedSuggestion.connectChoiceRssi);

        // Verify we did not update config in WCM.
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt(), any(), eq(false));
    }

    /**
     * Verify that modify networks that are already active and is cached in WifiConfigManager is
     * allowed and also updates the cache in WifiConfigManager.
     */
    @Test
    public void testAddNetworkSuggestionsSuccessOnInPlaceModificationWhenInWcm() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        // Assert that the original config was not metered.
        assertEquals(WifiConfiguration.METERED_OVERRIDE_NONE,
                networkSuggestion.wifiConfiguration.meteredOverride);
        verify(mWifiMetrics).incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NON_PRIVILEGED);
        reset(mWifiMetrics);
        // Store the original WifiConfiguration from WifiConfigManager.
        WifiConfiguration configInWcm =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        configInWcm.creatorUid = TEST_UID_1;
        configInWcm.creatorName = TEST_PACKAGE_1;
        configInWcm.fromWifiNetworkSuggestion = true;
        configInWcm.shared = false;
        setupGetConfiguredNetworksFromWcm(configInWcm);

        // Modify the original suggestion to mark it metered.
        networkSuggestion.wifiConfiguration.meteredOverride =
                WifiConfiguration.METERED_OVERRIDE_METERED;

        when(mWifiConfigManager.addOrUpdateNetwork(any(), eq(TEST_UID_1), eq(TEST_PACKAGE_1),
                eq(false))).thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        // Replace attempt should success.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiConfiguration.METERED_OVERRIDE_METERED,
                mWifiNetworkSuggestionsManager
                        .get(TEST_PACKAGE_1, TEST_UID_1).get(0).wifiConfiguration.meteredOverride);
        verify(mWifiMetrics, never()).incrementNetworkSuggestionApiUsageNumOfAppInType(anyInt());
        // Verify we did update config in WCM.
        ArgumentCaptor<WifiConfiguration> configCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(
                configCaptor.capture(), eq(TEST_UID_1), eq(TEST_PACKAGE_1), eq(false));
        assertNotNull(configCaptor.getValue());
        assertEquals(WifiConfiguration.METERED_OVERRIDE_METERED,
                configCaptor.getValue().meteredOverride);
    }

    /**
     * Verify that an attempt to add networks beyond the max per app is rejected.
     */
    @Test
    public void testAddNetworkSuggestionsFailureOnExceedsMaxPerApp() {
        // Add the max per app first.
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        for (int i = 0; i < WifiManager.NETWORK_SUGGESTIONS_MAX_PER_APP_HIGH_RAM; i++) {
            networkSuggestionList.add(createWifiNetworkSuggestion(
                    WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                    DEFAULT_PRIORITY_GROUP));
        }
        // The first add should succeed.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        List<WifiNetworkSuggestion> originalNetworkSuggestionsList = networkSuggestionList;

        // Now add 3 more.
        networkSuggestionList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            networkSuggestionList.add(createWifiNetworkSuggestion(
                    WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                    DEFAULT_PRIORITY_GROUP));
        }
        // The second add should fail.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Now remove 3 of the initially added ones.
        networkSuggestionList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            networkSuggestionList.add(originalNetworkSuggestionsList.get(i));
        }
        // The remove should succeed.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));

        // Now add 2 more.
        networkSuggestionList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            networkSuggestionList.add(createWifiNetworkSuggestion(
                    WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                    DEFAULT_PRIORITY_GROUP));
        }
        // This add should now succeed.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testAddNetworkSuggestionWithMismatchBetweenCarrierIdAndSubId() {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createEapNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        networkSuggestion.wifiConfiguration.subscriptionId = TEST_SUBID;
        when(mWifiCarrierInfoManager
                .isSubIdMatchingCarrierId(anyInt(), anyInt())).thenReturn(false);
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

    }

    /**
     * Verify that an attempt to remove an invalid set of network suggestions is rejected.
     */
    @Test
    public void testRemoveNetworkSuggestionsFailureOnInvalid() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion1);
            }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion2);
            }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        // Remove should fail because the network list is different.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList2, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the provided scan detail.
     */
    @Test
    public void
            testGetNetworkSuggestionsForScanDetailSuccessWithOneMatchForCarrierProvisioningApp() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        // This app should be pre-approved. No need to explicitly call
        // |setHasUserApprovedForApp(true, TEST_PACKAGE_1)|
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mWifiMetrics).incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NETWORK_PROVISIONING);
        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion.wifiConfiguration);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify add or remove suggestion list with null object will result error code.
     */
    @Test
    public void testAddRemoveNetworkSuggestionWithNullObject() {
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(Collections.singletonList(null),
                        TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion),
                        TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID,
                mWifiNetworkSuggestionsManager.remove(Collections.singletonList(null), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the provided scan detail.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailSuccessWithOneMatch() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion.wifiConfiguration);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the provided scan detail.
     *
     * The wifi configuration in the network suggestion is a type which could have upgradable types.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailSuccessWithOneMatchForUpgradableConfig() {
        WifiConfiguration upgradableConfig = new WifiConfiguration();
        upgradableConfig.SSID = "\"test\"";
        upgradableConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        upgradableConfig.preSharedKey = "\"PassW0rd\"";

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                upgradableConfig, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion.wifiConfiguration);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }


    /**
     * Verify a successful lookup of a single network suggestion matching the provided scan detail.
     *
     * The wifi configuration in the network suggestion is a leagcy object, says no security params
     * list, and only raw fields are set.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailSuccessWithOneMatchForLegacyConfig() {
        WifiConfiguration legacyConfig = new WifiConfiguration();
        legacyConfig.SSID = "\"test\"";
        legacyConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        legacyConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        legacyConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        legacyConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        legacyConfig.preSharedKey = "\"PassW0rd\"";

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                legacyConfig, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion.wifiConfiguration);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify a successful lookup of multiple network suggestions matching the provided scan detail.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailSuccessWithMultipleMatch() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        // Reuse the same network credentials to ensure they both match.
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion1);
            }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion2);
            }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion1.wifiConfiguration);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                add(networkSuggestion1);
                add(networkSuggestion2);
            }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the provided scan detail.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailSuccessWithBssidOneMatch() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail = createScanDetailForNetwork(wifiConfiguration);
        wifiConfiguration.BSSID = scanDetail.getBSSIDString();

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify a successful lookup of multiple network suggestions matching the provided scan detail.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailSuccessWithBssidMultipleMatch() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail = createScanDetailForNetwork(wifiConfiguration);
        wifiConfiguration.BSSID = scanDetail.getBSSIDString();

        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        // Reuse the same network credentials to ensure they both match.
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify a successful lookup of multiple network suggestions matching the provided scan detail.
     */
    @Test
    public void
            testGetNetworkSuggestionsForScanDetailSuccessWithBssidMultipleMatchFromSamePackage() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail = createScanDetailForNetwork(wifiConfiguration);
        wifiConfiguration.BSSID = scanDetail.getBSSIDString();

        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        // Reuse the same network credentials to ensure they both match.
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify a successful lookup of multiple network suggestions matching the provided scan detail.
     */
    @Test
    public void
            testGetNetworkSuggestionsForScanDetailSuccessWithBssidAndWithoutBssidMultipleMatch() {
        WifiConfiguration wifiConfiguration1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail = createScanDetailForNetwork(wifiConfiguration1);
        WifiConfiguration wifiConfiguration2 = new WifiConfiguration(wifiConfiguration1);
        wifiConfiguration2.BSSID = scanDetail.getBSSIDString();

        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                wifiConfiguration1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        // Reuse the same network credentials to ensure they both match.
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                wifiConfiguration2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);

        // Now change the bssid of the scan result to a different value, now only the general
        // (without bssid) suggestion.
        scanDetail.getScanResult().BSSID = MacAddressUtils.createRandomUnicastAddress().toString();
        matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify failure to lookup any network suggestion matching the provided scan detail when the
     * app providing the suggestion has not been approved.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailFailureOnAppNotApproved() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));

        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion.wifiConfiguration);

        assertTrue(mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail).isEmpty());
    }

    /**
     * Verify failure to lookup any network suggestion matching the provided scan detail.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailFailureOnSuggestionRemoval() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        ScanDetail scanDetail = createScanDetailForNetwork(wifiConfiguration);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                add(networkSuggestion);
            }};

        // add the suggestion & ensure lookup works.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        assertNotNull(mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                scanDetail));

        // remove the suggestion & ensure lookup fails.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(List.of(), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertTrue(mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail).isEmpty());
    }

    /**
     * Verify failure to lookup any network suggestion matching the provided scan detail.
     */
    @Test
    public void testGetNetworkSuggestionsForScanDetailFailureOnWrongNetwork() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        // Create a scan result corresponding to a different network.
        ScanDetail scanDetail = createScanDetailForNetwork(
                WifiConfigurationTestUtil.createPskNetwork());

        assertTrue(mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail).isEmpty());
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should trigger a broadcast to the app.
     * This should not trigger a connection failure callback to the app.
     */
    @Test
    public void testOnNetworkConnectionSuccessWithOneMatch() throws Exception {
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnNetworkConnectionSuccessWithOneMatchFromCarrierPrivilegedApp() {
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TEST_CARRIER_ID);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));

        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(networkSuggestion.wifiConfiguration)));
        mInorder.verify(mWifiPermissionsUtil)
                .doesUidBelongToCurrentUserOrDeviceOwner(eq(TEST_UID_1));

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Verify if a user saved network connected and it can match suggestions. Only the
     * carrier-privileged suggestor app can receive the broadcast if
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set to true.
     */
    @Test
    public void testOnSavedOpenNetworkConnectionSuccessWithMultipleMatch() throws Exception {
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenOweNetwork();
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        // Simulate connecting to the user saved open network.
        WifiConfiguration connectNetwork = new WifiConfiguration(config);
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();
        // Verify that the correct broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion1);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnNetworkConnectionFailureWithOneMatchButCallbackOnBinderDied()
            throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> drCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
        verify(mBinder).linkToDeath(drCaptor.capture(), anyInt());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        // Simulate binder was died.
        drCaptor.getValue().binderDied();
        mLooper.dispatchAll();
        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectFailure();
        // Verify no connection failure event was sent out.
        mInorder.verify(mWifiPermissionsUtil, never()).enforceCanAccessScanResults(
                eq(TEST_PACKAGE_1), eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        verify(mConnectionStatusListener, never()).onConnectionStatus(any(), anyInt());

        // Verify no more broadcast were sent out.
        mInorder.verify(mContext,  never()).sendBroadcastAsUser(
                any(), any());
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the current network
     * connection failure.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should trigger a connection failure callback to the app
     */
    @Test
    public void testOnNetworkConnectionFailureWithOneMatch() throws Exception {
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_DHCP, connectNetwork, TEST_BSSID);

        // Verify right callback were sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        verify(mConnectionStatusListener)
                .onConnectionStatus(networkSuggestion,
                        WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_IP_PROVISIONING);
        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectFailure();

        // Verify no more broadcast were sent out.
        mInorder.verify(mContext,  never()).sendBroadcastAsUser(
                any(), any());
    }

    /**
     * Verify a successful lookup of multiple network suggestion matching the connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should trigger a broadcast to all the apps.
     */
    @Test
    public void testOnNetworkConnectionSuccessWithMultipleMatch() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion1.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;

        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        ArgumentCaptor<String> packageNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> featureIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> uidCaptor = ArgumentCaptor.forClass(Integer.class);
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(
                packageNameCaptor.capture(), featureIdCaptor.capture(), uidCaptor.capture(),
                nullable(String.class));
        assertEquals(TEST_FEATURE, featureIdCaptor.getValue());
        assertEquals(packageNameCaptor.getValue(), TEST_PACKAGE_1);
        assertEquals(Integer.valueOf(TEST_UID_1), uidCaptor.getValue());
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion1);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Verify a successful lookup of multiple network suggestion matching the connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should trigger a broadcast to all the apps.
     */
    @Test
    public void testOnNetworkConnectionSuccessWithBssidMultipleMatch() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenOweNetwork();
        config.BSSID = TEST_BSSID;
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);
        WifiConfiguration connectNetwork = new WifiConfiguration(config);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcasts were sent out.
        ArgumentCaptor<String> packageNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> featureIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> uidCaptor = ArgumentCaptor.forClass(Integer.class);
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(
                packageNameCaptor.capture(), featureIdCaptor.capture(), uidCaptor.capture(),
                nullable(String.class));
        assertEquals(TEST_FEATURE, featureIdCaptor.getValue());
        assertEquals(packageNameCaptor.getValue(), TEST_PACKAGE_1);
        assertEquals(Integer.valueOf(TEST_UID_1), uidCaptor.getValue());
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion1);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Verify a successful lookup of multiple network suggestion matching the connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should trigger a broadcast to all the apps.
     */
    @Test
    public void testOnNetworkConnectionSuccessWithBssidAndWithoutBssidMultipleMatch() {
        WifiConfiguration wifiConfiguration1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration wifiConfiguration2 = new WifiConfiguration(wifiConfiguration1);
        wifiConfiguration2.BSSID = TEST_BSSID;
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                wifiConfiguration1, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                wifiConfiguration2, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion1.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;

        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcasts were sent out.
        ArgumentCaptor<String> packageNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> featureIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> uidCaptor = ArgumentCaptor.forClass(Integer.class);
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(
                packageNameCaptor.capture(), featureIdCaptor.capture(), uidCaptor.capture(),
                nullable(String.class));
        assertEquals(TEST_FEATURE, featureIdCaptor.getValue());
        assertEquals(packageNameCaptor.getValue(), TEST_PACKAGE_1);
        assertEquals(Integer.valueOf(TEST_UID_1), uidCaptor.getValue());
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion1);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * c) App has not been approved by the user.
     * This should not trigger a broadcast to the app.
     */
    @Test
    public void testOnNetworkConnectionWhenAppNotApproved() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mWifiPermissionsUtil, times(2))
                .checkNetworkCarrierProvisioningPermission(TEST_UID_1);
        assertFalse(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));

        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;

        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        // Verify no broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil, never()).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));
        mInorder.verify(mContext,  never()).sendBroadcastAsUser(
                any(), any());
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the connected network.
     * a) The corresponding network suggestion does not have the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should not trigger a broadcast to the app.
     */
    @Test
    public void testOnNetworkConnectionWhenIsAppInteractionRequiredNotSet() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mWifiPermissionsUtil, times(2))
                .checkNetworkCarrierProvisioningPermission(TEST_UID_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;

        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        // Verify no broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil, never()).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));
        mInorder.verify(mContext,  never()).sendBroadcastAsUser(
                any(), any());
    }

    /**
     * Verify a successful lookup of a single network suggestion matching the connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app does not hold location permission.
     * This should not trigger a broadcast to the app.
     */
    @Test
    public void testOnNetworkConnectionWhenAppDoesNotHoldLocationPermission() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mWifiPermissionsUtil, times(2))
                .checkNetworkCarrierProvisioningPermission(TEST_UID_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceCanAccessScanResults(
                eq(TEST_PACKAGE_1), eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));

        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;

        // Simulate connecting to the network.
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));

        // Verify no broadcast was sent out.
        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Verify triggering of config store write after successful addition of network suggestions.
     */
    @Test
    public void testAddNetworkSuggestionsConfigStoreWrite() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Verify config store interactions.
        verify(mWifiConfigManager).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());

        Map<String, PerAppInfo> networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertEquals(1, networkSuggestionsMapToWrite.size());
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_1));
        assertFalse(networkSuggestionsMapToWrite.get(TEST_PACKAGE_1).hasUserApproved);
        Collection<ExtendedWifiNetworkSuggestion> extNetworkSuggestionsToWrite =
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_1).extNetworkSuggestions.values();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(expectedAllNetworkSuggestions,
                extNetworkSuggestionsToWrite
                        .stream()
                        .collect(Collectors.mapping(
                                n -> n.wns,
                                Collectors.toSet())));

        // Ensure that the new data flag has been reset after read.
        assertFalse(mDataSource.hasNewDataToSerialize());
    }

    /**
     * Verify triggering of config store write after successful addition of network suggestions.
     * And store write is failure because out of memory.
     */
    @Test
    public void testAddNetworkSuggestionsConfigStoreWriteFailedByOOM() {
        when(mWifiConfigManager.saveToStore(anyBoolean())).thenThrow(new OutOfMemoryError())
                .thenReturn(true);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());

        Map<String, PerAppInfo> networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertEquals(1, networkSuggestionsMapToWrite.size());
        assertEquals(0, networkSuggestionsMapToWrite.get(TEST_PACKAGE_1)
                .extNetworkSuggestions.size());

        // Ensure that the new data flag has been reset after read.
        assertFalse(mDataSource.hasNewDataToSerialize());
    }

    /**
     * Verify triggering of config store write after successful removal of network suggestions.
     */
    @Test
    public void testRemoveNetworkSuggestionsConfigStoreWrite() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());

        // Expect a single app entry with no active suggestions.
        Map<String, PerAppInfo> networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertEquals(1, networkSuggestionsMapToWrite.size());
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_1));
        assertFalse(networkSuggestionsMapToWrite.get(TEST_PACKAGE_1).hasUserApproved);
        assertTrue(
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_1).extNetworkSuggestions.isEmpty());

        // Ensure that the new data flag has been reset after read.
        assertFalse(mDataSource.hasNewDataToSerialize());
    }

    /**
     * Verify that the internally used WifiConfiguration created by
     * ExtendedWifiNetworkSuggestion#createInternalWifiConfiguration forces MAC randomization off
     * if MAC randomization should be disabled for that particular config.
     */
    @Test
    public void testCarrierConfigSsidListToDisableMacRandomizationDisabled() {
        assumeTrue(SdkLevel.isAtLeastS());
        PerAppInfo appInfo = new PerAppInfo(TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        appInfo.hasUserApproved = true;

        // Create 2 WifiNetworkSuggestion and mock CarrierConfigManager to include the SSID
        // of the first suggetion in the MAC randomization disabled list.
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        when(mWifiCarrierInfoManager.shouldDisableMacRandomization(
                eq(networkSuggestion.getWifiConfiguration().SSID), anyInt(),
                anyInt())).thenReturn(true);
        ExtendedWifiNetworkSuggestion extendedWifiNetworkSuggestion =
                ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion, appInfo, true);
        // Verify MAC randomization is disabled for the first suggestion network.
        assertEquals(WifiConfiguration.RANDOMIZATION_NONE,
                extendedWifiNetworkSuggestion.createInternalWifiConfiguration(
                        mWifiCarrierInfoManager).macRandomizationSetting);

        ExtendedWifiNetworkSuggestion extendedWifiNetworkSuggestion2 =
                ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion2, appInfo, true);
        assertEquals(WifiConfiguration.RANDOMIZATION_PERSISTENT,
                extendedWifiNetworkSuggestion2.createInternalWifiConfiguration(
                        mWifiCarrierInfoManager).macRandomizationSetting);
    }

    /**
     * Verify handling of initial config store read.
     */
    @Test
    public void testNetworkSuggestionsConfigStoreLoad() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        PerAppInfo appInfo = new PerAppInfo(TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        appInfo.hasUserApproved = true;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        ExtendedWifiNetworkSuggestion ewns1 =
                ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion, appInfo, true);
        appInfo.extNetworkSuggestions.put(ewns1.hashCode(), ewns1);
        ExtendedWifiNetworkSuggestion ewns2 =
                ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion1, appInfo, true);
        appInfo.extNetworkSuggestions.put(ewns2.hashCode(), ewns2);
        mDataSource.fromDeserialized(new HashMap<String, PerAppInfo>() {{
                        put(TEST_PACKAGE_1, appInfo);
                }});

        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                    add(networkSuggestion1);
                }};
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);

        // Ensure we can lookup the network.
        ScanDetail scanDetail = createScanDetailForNetwork(networkSuggestion.wifiConfiguration);
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);

        // Ensure we can lookup the passpoint network.
        WifiConfiguration connectNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        connectNetwork.FQDN = TEST_FQDN;
        connectNetwork.providerFriendlyName = TEST_FRIENDLY_NAME;
        connectNetwork.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;

        matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager
                        .getNetworkSuggestionsForWifiConfiguration(connectNetwork, null);
        Set<ExtendedWifiNetworkSuggestion> expectedMatchingExtNetworkSuggestions =
                new HashSet<ExtendedWifiNetworkSuggestion>() {{
                    add(ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion1, appInfo, true));
                }};
        assertEquals(expectedMatchingExtNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify handling of config store read after user switch.
     */
    @Test
    public void testNetworkSuggestionsConfigStoreLoadAfterUserSwitch() {
        // Read the store initially.
        PerAppInfo appInfo1 = new PerAppInfo(TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        appInfo1.hasUserApproved = true;
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        ExtendedWifiNetworkSuggestion ewns =
                ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion1, appInfo1, true);
        appInfo1.extNetworkSuggestions.put(ewns.hashCode(), ewns);
        mDataSource.fromDeserialized(new HashMap<String, PerAppInfo>() {{
                    put(TEST_PACKAGE_1, appInfo1);
                }});


        // Now simulate user switch.
        mDataSource.reset();
        PerAppInfo appInfo2 = new PerAppInfo(TEST_UID_2, TEST_PACKAGE_2, TEST_FEATURE);
        appInfo2.hasUserApproved = true;
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        ExtendedWifiNetworkSuggestion ewns2 =
                ExtendedWifiNetworkSuggestion.fromWns(networkSuggestion2, appInfo2, true);
        appInfo2.extNetworkSuggestions.put(ewns2.hashCode(), ewns2);
        mDataSource.fromDeserialized(new HashMap<String, PerAppInfo>() {{
                    put(TEST_PACKAGE_2, appInfo2);
                }});

        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);

        // Ensure we can lookup the new network.
        ScanDetail scanDetail2 = createScanDetailForNetwork(networkSuggestion2.wifiConfiguration);
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail2);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);

        // Ensure that the previous network can no longer be looked up.
        ScanDetail scanDetail1 = createScanDetailForNetwork(networkSuggestion1.wifiConfiguration);
        assertTrue(mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail1).isEmpty());
    }

    /**
     * Verify that we will disconnect from the network if the only network suggestion matching the
     * connected network is removed.
     */
    @Test
    public void
            testRemoveNetworkSuggestionsMatchingConnectionSuccessWithOneMatch() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        // Now remove the network suggestion and ensure we did trigger a disconnect.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(connectNetwork)));
    }

    /**
     * Verify that we will disconnect from network when App removed all its suggestions by remove
     * empty list.
     */
    @Test
    public void
            testRemoveAllNetworkSuggestionsMatchingConnectionSuccessWithOneMatch() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        // Now remove all network suggestion and ensure we did trigger a disconnect.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(new ArrayList<>(), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(connectNetwork)));
    }


    /**
     * Verify that we remove the profile from WifiConfigManager no matter if it is currently
     * connected
     */
    @Test
    public void testRemoveAppMatchingConnectionSuccessWithMultipleMatch() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenOweNetwork();
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_2, TEST_PACKAGE_2);

        // Simulate connecting to the network.
        WifiConfiguration connectNetwork = new WifiConfiguration(config);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        // Now remove the current connected app and ensure we remove from WifiConfigManager.
        mWifiNetworkSuggestionsManager.removeApp(TEST_PACKAGE_1);
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(connectNetwork)));

        // Now remove the other app and ensure we remove from WifiConfigManager.
        mWifiNetworkSuggestionsManager.removeApp(TEST_PACKAGE_2);
        verify(mWifiConfigManager, times(2)).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(networkSuggestion2.wifiConfiguration)));
    }

    /**
     * Verify that we start tracking app-ops on first network suggestion add & stop tracking on the
     * last network suggestion remove.
     */
    @Test
    public void testAddRemoveNetworkSuggestionsStartStopAppOpsWatch() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        mInorder = inOrder(mAppOpsManager);

        // Watch app-ops changes on first add.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mInorder.verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE),
                eq(TEST_PACKAGE_1), mAppOpChangedListenerCaptor.capture());

        // Nothing happens on second add.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Now remove first add, nothing happens.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        // Stop watching app-ops changes on last remove.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList2, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());
        verify(mAppOpsManager, never()).stopWatchingMode(mAppOpChangedListenerCaptor.getValue());

        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Verify app-ops disable/enable after suggestions add.
     */
    @Test
    public void testAppOpsChangeAfterSuggestionsAdd() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);

        verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE), eq(TEST_PACKAGE_1),
                mAppOpChangedListenerCaptor.capture());
        AppOpsManager.OnOpChangedListener listener = mAppOpChangedListenerCaptor.getValue();
        assertNotNull(listener);

        // allow change wifi state.
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                        TEST_PACKAGE_1))
                .thenReturn(MODE_ALLOWED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        allNetworkSuggestions = mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);

        // disallow change wifi state & ensure we remove the app from database.
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                        TEST_PACKAGE_1))
                .thenReturn(MODE_IGNORED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        verify(mAppOpsManager).stopWatchingMode(mAppOpChangedListenerCaptor.getValue());
        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());
        verify(mWifiMetrics).incrementNetworkSuggestionUserRevokePermission();
    }

    /**
     * Verify app-ops disable/enable after config store load.
     */
    @Test
    public void testAppOpsChangeAfterConfigStoreLoad() {
        PerAppInfo appInfo = new PerAppInfo(TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        appInfo.hasUserApproved = true;
        mDataSource.fromDeserialized(new HashMap<String, PerAppInfo>() {{
                    put(TEST_PACKAGE_1, appInfo);
                }});
        // Even the app has no suggestion still monitor the ops change
        verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE), eq(TEST_PACKAGE_1),
                mAppOpChangedListenerCaptor.capture());
        AppOpsManager.OnOpChangedListener listener = mAppOpChangedListenerCaptor.getValue();
        assertNotNull(listener);

        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());
        assertTrue(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));

        // allow change wifi state.
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                        TEST_PACKAGE_1))
                .thenReturn(MODE_ALLOWED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        assertTrue(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));

        // disallow change wifi state & ensure we remove all the suggestions for that app.
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                        TEST_PACKAGE_1))
                .thenReturn(MODE_IGNORED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());
        // And user approval is also removed
        assertFalse(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));
    }

    /**
     * Verify app-ops disable with wrong uid to package mapping.
     */
    @Test
    public void testAppOpsChangeWrongUid() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<WifiNetworkSuggestion> allNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        Set<WifiNetworkSuggestion> expectedAllNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);

        verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE), eq(TEST_PACKAGE_1),
                mAppOpChangedListenerCaptor.capture());
        AppOpsManager.OnOpChangedListener listener = mAppOpChangedListenerCaptor.getValue();
        assertNotNull(listener);

        // disallow change wifi state & ensure we don't remove all the suggestions for that app.
        doThrow(new SecurityException()).when(mAppOpsManager).checkPackage(
                eq(TEST_UID_1), eq(TEST_PACKAGE_1));
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                        TEST_PACKAGE_1))
                .thenReturn(MODE_IGNORED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        allNetworkSuggestions = mWifiNetworkSuggestionsManager.getAllNetworkSuggestions();
        assertEquals(expectedAllNetworkSuggestions, allNetworkSuggestions);
    }

    /**
     * Verify that we stop tracking the package on its removal.
     */
    @Test
    public void testRemoveApp() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));

        // Remove all suggestions from TEST_PACKAGE_1 & TEST_PACKAGE_2, we should continue to track.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));

        assertTrue(mDataSource.hasNewDataToSerialize());
        Map<String, PerAppInfo> networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertEquals(2, networkSuggestionsMapToWrite.size());
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_1));
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_2));
        assertTrue(
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_1).extNetworkSuggestions.isEmpty());
        assertTrue(
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_2).extNetworkSuggestions.isEmpty());

        // Now remove TEST_PACKAGE_1, continue to track TEST_PACKAGE_2.
        mWifiNetworkSuggestionsManager.removeApp(TEST_PACKAGE_1);
        assertTrue(mDataSource.hasNewDataToSerialize());
        networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertEquals(1, networkSuggestionsMapToWrite.size());
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_2));
        assertTrue(
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_2).extNetworkSuggestions.isEmpty());

        // Now remove TEST_PACKAGE_2.
        mWifiNetworkSuggestionsManager.removeApp(TEST_PACKAGE_2);
        assertTrue(mDataSource.hasNewDataToSerialize());
        networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertTrue(networkSuggestionsMapToWrite.isEmpty());

        // Verify that we stopped watching these apps for app-ops changes.
        verify(mAppOpsManager, times(2)).stopWatchingMode(any());
    }


    /**
     * Verify that we stop tracking all packages & it's suggestions on network settings reset.
     */
    @Test
    public void testClear() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));

        // Remove all suggestions from TEST_PACKAGE_1 & TEST_PACKAGE_2, we should continue to track.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));

        assertTrue(mDataSource.hasNewDataToSerialize());
        Map<String, PerAppInfo> networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertEquals(2, networkSuggestionsMapToWrite.size());
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_1));
        assertTrue(networkSuggestionsMapToWrite.keySet().contains(TEST_PACKAGE_2));
        assertTrue(
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_1).extNetworkSuggestions.isEmpty());
        assertTrue(
                networkSuggestionsMapToWrite.get(TEST_PACKAGE_2).extNetworkSuggestions.isEmpty());

        // Now clear everything.
        mWifiNetworkSuggestionsManager.clear();
        assertTrue(mDataSource.hasNewDataToSerialize());
        networkSuggestionsMapToWrite = mDataSource.toSerialize();
        assertTrue(networkSuggestionsMapToWrite.isEmpty());

        // Verify that we stopped watching these apps for app-ops changes.
        verify(mAppOpsManager, times(2)).stopWatchingMode(any());

        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);
    }

    /**
     * Verify user dismissal notification when first time add suggestions and dismissal the user
     * approval notification when framework gets scan results.
     */
    @Test
    public void testUserApprovalNotificationDismissalWhenGetScanResult() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalNotification(TEST_APP_NAME_1);
        // Simulate user dismissal notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_DISMISSED_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        reset(mWifiNotificationManager);
        verify(mWifiMetrics).addUserApprovalSuggestionAppUiReaction(
                WifiNetworkSuggestionsManager.ACTION_USER_DISMISS, false);
        // Simulate finding the network in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));

        validateUserApprovalNotification(TEST_APP_NAME_1);

        // Simulate user dismissal notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_DISMISSED_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);

        reset(mWifiNotificationManager);
        // We should resend the notification next time the network is found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));

        validateUserApprovalNotification(TEST_APP_NAME_1);
        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify user dismissal notification when first time add suggestions and click on allow on
     * the user approval notification when framework gets scan results.
     */
    @Test
    public void testUserApprovalNotificationClickOnAllowWhenGetScanResult() throws RemoteException {
        mWifiNetworkSuggestionsManager.addSuggestionUserApprovalStatusListener(
                mUserApprovalStatusListener, TEST_PACKAGE_1, TEST_UID_1);
        verify(mUserApprovalStatusListener)
                .onUserApprovalStatusChange(WifiManager.STATUS_SUGGESTION_APPROVAL_UNKNOWN);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalNotification(TEST_APP_NAME_1);
        verify(mUserApprovalStatusListener)
                .onUserApprovalStatusChange(WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING);

        // Simulate user dismissal notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_DISMISSED_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        reset(mWifiNotificationManager);

        // Simulate finding the network in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));

        validateUserApprovalNotification(TEST_APP_NAME_1);

        // Simulate user clicking on allow in the notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        // Cancel the notification.
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());
        verify(mWifiMetrics).addUserApprovalSuggestionAppUiReaction(
                WifiNetworkSuggestionsManager.ACTION_USER_ALLOWED_APP, false);

        reset(mWifiNotificationManager);
        // We should not resend the notification next time the network is found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));
        verifyNoMoreInteractions(mWifiNotificationManager);
        verify(mUserApprovalStatusListener).onUserApprovalStatusChange(
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER);
    }

    /**
     * Verify user dismissal notification when first time add suggestions and click on disallow on
     * the user approval notification when framework gets scan results.
     */
    @Test
    public void testUserApprovalNotificationClickOnDisallowWhenGetScanResult()
            throws RemoteException {
        mWifiNetworkSuggestionsManager.addSuggestionUserApprovalStatusListener(
                mUserApprovalStatusListener,  TEST_PACKAGE_1, TEST_UID_1);
        verify(mUserApprovalStatusListener)
                .onUserApprovalStatusChange(WifiManager.STATUS_SUGGESTION_APPROVAL_UNKNOWN);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE),
                eq(TEST_PACKAGE_1), mAppOpChangedListenerCaptor.capture());
        validateUserApprovalNotification(TEST_APP_NAME_1);

        // Simulate user dismissal notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_DISMISSED_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        reset(mWifiNotificationManager);

        // Simulate finding the network in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));

        validateUserApprovalNotification(TEST_APP_NAME_1);
        verify(mUserApprovalStatusListener)
                .onUserApprovalStatusChange(WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING);

        // Simulate user clicking on disallow in the notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        // Ensure we turn off CHANGE_WIFI_STATE app-ops.
        verify(mAppOpsManager).setMode(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1, TEST_PACKAGE_1, MODE_IGNORED);
        // Cancel the notification.
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());
        verify(mWifiMetrics).addUserApprovalSuggestionAppUiReaction(
                WifiNetworkSuggestionsManager.ACTION_USER_DISALLOWED_APP, false);

        reset(mWifiNotificationManager);

        // Now trigger the app-ops callback to ensure we remove all of their suggestions.
        AppOpsManager.OnOpChangedListener listener = mAppOpChangedListenerCaptor.getValue();
        assertNotNull(listener);
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                        TEST_PACKAGE_1))
                .thenReturn(MODE_IGNORED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());

        // Assuming the user re-enabled the app again & added the same suggestions back.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // We should resend the notification when the network is again found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));
        verify(mUserApprovalStatusListener).onUserApprovalStatusChange(
                WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER);
        validateUserApprovalNotification(TEST_APP_NAME_1);
        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify that we don't send a new notification when a pending notification is active.
     */
    @Test
    public void testUserApprovalNotificationWhilePreviousNotificationActive() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Simulate finding the network in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));

        validateUserApprovalNotification(TEST_APP_NAME_1);

        reset(mWifiNotificationManager);
        // We should not resend the notification next time the network is found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));

        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify get network suggestion return the right result
     * 1. App never suggested, should return empty list.
     * 2. App has network suggestions, return all its suggestion.
     * 3. App suggested and remove them all, should return empty list.
     */
    @Test
    public void testGetNetworkSuggestions() {
        // test App never suggested.
        List<WifiNetworkSuggestion> storedNetworkSuggestionListPerApp =
                mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1);
        assertEquals(storedNetworkSuggestionListPerApp.size(), 0);

        // App add network suggestions then get stored suggestions.
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOweNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion3 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createSaeNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion4 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion1);
        networkSuggestionList.add(networkSuggestion2);
        networkSuggestionList.add(networkSuggestion3);
        networkSuggestionList.add(networkSuggestion4);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        storedNetworkSuggestionListPerApp =
                mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1);
        assertEquals(new HashSet<>(networkSuggestionList),
                new HashSet<>(storedNetworkSuggestionListPerApp));

        // App remove all network suggestions, expect empty list.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(new ArrayList<>(), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        storedNetworkSuggestionListPerApp =
                mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1);
        assertEquals(storedNetworkSuggestionListPerApp.size(), 0);
    }

    /**
     * Verify get hidden networks from All user approve network suggestions
     */
    @Test
    public void testGetHiddenNetworks() {

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion hiddenNetworkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskHiddenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion hiddenNetworkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskHiddenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion hiddenNetworkSuggestion3 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskHiddenNetwork(), null, true, false, true, false,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                    add(hiddenNetworkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(hiddenNetworkSuggestion2);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList3 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(hiddenNetworkSuggestion3);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList3, TEST_UID_3,
                        TEST_PACKAGE_3, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(false, TEST_UID_2, TEST_PACKAGE_2);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_3, TEST_PACKAGE_3);
        List<WifiScanner.ScanSettings.HiddenNetwork> hiddenNetworks =
                mWifiNetworkSuggestionsManager.retrieveHiddenNetworkList(true /* autoJoinOnly */);
        assertEquals(3, hiddenNetworks.size());
        hiddenNetworks =
                mWifiNetworkSuggestionsManager.retrieveHiddenNetworkList(false /* autoJoinOnly */);
        assertEquals(4, hiddenNetworks.size());
        assertEquals(hiddenNetworkSuggestion1.wifiConfiguration.SSID, hiddenNetworks.get(0).ssid);
    }

    /**
     * Verify handling of user clicking allow on the user approval dialog when first time
     * add suggestions.
     */
    @Test
    public void testUserApprovalDialogClickOnAllowDuringAddingSuggestionsFromFgApp() {
        // Fg app
        when(mActivityManager.getPackageImportance(any())).thenReturn(IMPORTANCE_FOREGROUND);

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalDialog(TEST_APP_NAME_1);

        // Simulate user clicking on allow in the dialog.
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), dialogCallbackCaptor.capture(), any());
        dialogCallbackCaptor.getValue().onPositiveButtonClicked();

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());
        verify(mWifiMetrics).addUserApprovalSuggestionAppUiReaction(
                WifiNetworkSuggestionsManager.ACTION_USER_ALLOWED_APP, true);

        // We should not resend the notification next time the network is found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));
        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify handling of user clicking Disallow on the user approval dialog when first time
     * add suggestions.
     */
    @Test
    public void testUserApprovalDialogClickOnDisallowWhenAddSuggestionsFromFgApp() {
        // Fg app
        when(mActivityManager.getPackageImportance(any())).thenReturn(IMPORTANCE_FOREGROUND);

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false,  true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE),
                eq(TEST_PACKAGE_1), mAppOpChangedListenerCaptor.capture());
        validateUserApprovalDialog(TEST_APP_NAME_1);

        // Simulate user clicking on disallow in the dialog.
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), dialogCallbackCaptor.capture(), any());
        dialogCallbackCaptor.getValue().onNegativeButtonClicked();
        // Ensure we turn off CHANGE_WIFI_STATE app-ops.
        verify(mAppOpsManager).setMode(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1, TEST_PACKAGE_1, MODE_IGNORED);

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());
        verify(mWifiMetrics).addUserApprovalSuggestionAppUiReaction(
                WifiNetworkSuggestionsManager.ACTION_USER_DISALLOWED_APP, true);

        // Now trigger the app-ops callback to ensure we remove all of their suggestions.
        AppOpsManager.OnOpChangedListener listener = mAppOpChangedListenerCaptor.getValue();
        assertNotNull(listener);
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                TEST_PACKAGE_1))
                .thenReturn(MODE_IGNORED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());

        // Assuming the user re-enabled the app again & added the same suggestions back.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalDialog(TEST_APP_NAME_1);
        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify handling of dismissal of the user approval dialog when first time
     * add suggestions.
     */
    @Test
    public void testUserApprovalDialiogDismissDuringAddingSuggestionsFromFgApp() {
        // Fg app
        when(mActivityManager.getPackageImportance(any())).thenReturn(IMPORTANCE_FOREGROUND);

        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalDialog(TEST_APP_NAME_1);

        // As the dialog is active, notification will not be sent
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());

        // Simulate user dismissing the dialog via home/back button.
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), dialogCallbackCaptor.capture(), any());
        dialogCallbackCaptor.getValue().onCancelled();
        mLooper.dispatchAll();

        // Verify no new config store or app-op interactions.
        verify(mWifiConfigManager).saveToStore(true); // 1 already done for add
        verify(mAppOpsManager, never()).setMode(any(), anyInt(), any(), anyInt());
        verify(mWifiMetrics).addUserApprovalSuggestionAppUiReaction(
                WifiNetworkSuggestionsManager.ACTION_USER_DISMISS, true);

        // We should resend the notification next time the network is found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));
        validateUserApprovalNotification(TEST_APP_NAME_1);
    }

    /**
     * Verify handling of user clicking allow on the user approval notification when first time
     * add suggestions.
     */
    @Test
    public void testUserApprovalNotificationClickOnAllowDuringAddingSuggestionsFromNonFgApp() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalNotification(TEST_APP_NAME_1);

        // Simulate user clicking on allow in the notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        // Cancel the notification.
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());

        reset(mWifiNotificationManager);
        // We should not resend the notification next time the network is found in scan results.
        mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                createScanDetailForNetwork(networkSuggestion.wifiConfiguration));
        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify handling of user clicking Disallow on the user approval notification when first time
     * add suggestions.
     */
    @Test
    public void testUserApprovalNotificationClickOnDisallowWhenAddSuggestionsFromNonFgApp() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false,  true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(mAppOpsManager).startWatchingMode(eq(OPSTR_CHANGE_WIFI_STATE),
                eq(TEST_PACKAGE_1), mAppOpChangedListenerCaptor.capture());
        validateUserApprovalNotification(TEST_APP_NAME_1);

        // Simulate user clicking on disallow in the notification.
        sendBroadcastForUserActionOnApp(
                NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION, TEST_PACKAGE_1, TEST_UID_1);
        // Ensure we turn off CHANGE_WIFI_STATE app-ops.
        verify(mAppOpsManager).setMode(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1, TEST_PACKAGE_1, MODE_IGNORED);
        // Cancel the notification.
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);

        // Verify config store interactions.
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        assertTrue(mDataSource.hasNewDataToSerialize());

        reset(mWifiNotificationManager);

        // Now trigger the app-ops callback to ensure we remove all of their suggestions.
        AppOpsManager.OnOpChangedListener listener = mAppOpChangedListenerCaptor.getValue();
        assertNotNull(listener);
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_CHANGE_WIFI_STATE, TEST_UID_1,
                TEST_PACKAGE_1))
                .thenReturn(MODE_IGNORED);
        listener.onOpChanged(OPSTR_CHANGE_WIFI_STATE, TEST_PACKAGE_1);
        mLooper.dispatchAll();
        assertTrue(mWifiNetworkSuggestionsManager.getAllNetworkSuggestions().isEmpty());

        // Assuming the user re-enabled the app again & added the same suggestions back.
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        validateUserApprovalNotification(TEST_APP_NAME_1);
        verifyNoMoreInteractions(mWifiNotificationManager);
    }

    /**
     * Verify a successful lookup of a single passpoint network suggestion matching the
     * connected network.
     * a) The corresponding network suggestion has the
     * {@link WifiNetworkSuggestion#isAppInteractionRequired} flag set.
     * b) The app holds location permission.
     * This should trigger a broadcast to the app.
     */
    @Test
    public void testOnPasspointNetworkConnectionSuccessWithOneMatch() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        // Simulate connecting to the network.
        WifiConfiguration connectNetwork = WifiConfigurationTestUtil.createPasspointNetwork();
        connectNetwork.FQDN = TEST_FQDN;
        connectNetwork.providerFriendlyName = TEST_FRIENDLY_NAME;
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        connectNetwork.setPasspointUniqueId(passpointConfiguration.getUniqueId());

        verify(mWifiMetrics, never()).incrementNetworkSuggestionApiNumConnectSuccess();
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    /**
     * Creates a scan detail corresponding to the provided network values.
     */
    private ScanDetail createScanDetailForNetwork(WifiConfiguration configuration) {
        return WifiConfigurationTestUtil.createScanDetailForNetwork(configuration,
                MacAddressUtils.createRandomUnicastAddress().toString(), -45, 0, 0, 0);
    }

    private void validatePostConnectionBroadcastSent(
            String expectedPackageName, WifiNetworkSuggestion expectedNetworkSuggestion) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> userHandleCaptor = ArgumentCaptor.forClass(UserHandle.class);
        mInorder.verify(mContext,  calls(1)).sendBroadcastAsUser(
                intentCaptor.capture(), userHandleCaptor.capture());

        assertEquals(userHandleCaptor.getValue(), UserHandle.SYSTEM);

        Intent intent = intentCaptor.getValue();
        assertEquals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION,
                intent.getAction());
        String packageName = intent.getPackage();
        WifiNetworkSuggestion networkSuggestion =
                intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_SUGGESTION);
        assertEquals(expectedPackageName, packageName);
        assertEquals(expectedNetworkSuggestion, networkSuggestion);
    }

    private void validateUserApprovalDialog(String... anyOfExpectedAppNames) {
        verify(mDialogHandle, atLeastOnce()).launchDialog();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mWifiDialogManager, atLeastOnce()).createSimpleDialog(
                any(), messageCaptor.capture(), any(), any(), any(), any(), any());
        String message = messageCaptor.getValue();
        assertNotNull(message);

        boolean foundMatch = false;
        for (int i = 0; i < anyOfExpectedAppNames.length; i++) {
            foundMatch = message.contains(anyOfExpectedAppNames[i]);
            if (foundMatch) break;
        }
        assertTrue(foundMatch);
    }

    private void validateUserApprovalNotification(String... anyOfExpectedAppNames) {
        verify(mWifiNotificationManager, atLeastOnce()).notify(
                eq(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE),
                eq(mNotification));
        ArgumentCaptor<Notification.BigTextStyle> contentCaptor =
                ArgumentCaptor.forClass(Notification.BigTextStyle.class);
        verify(mNotificationBuilder, atLeastOnce()).setStyle(contentCaptor.capture());
        Notification.BigTextStyle content = contentCaptor.getValue();
        assertNotNull(content);

        boolean foundMatch = false;
        for (int i = 0; i < anyOfExpectedAppNames.length; i++) {
            foundMatch = content.getBigText().toString().contains(anyOfExpectedAppNames[i]);
            if (foundMatch) break;
        }
        assertTrue(foundMatch);
    }

    private void sendBroadcastForUserActionOnApp(String action, String packageName, int uid) {
        Intent intent = new Intent()
                .setAction(action)
                .putExtra(WifiNetworkSuggestionsManager.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(WifiNetworkSuggestionsManager.EXTRA_UID, uid);
        assertNotNull(mBroadcastReceiverCaptor.getValue());
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
    }

    @Test
    public void testAddSuggestionWithValidCarrierIdWithCarrierProvisionPermission() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.carrierId = VALID_CARRIER_ID;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);

        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);

        assertEquals(status, WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        verify(mWifiMetrics).incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NETWORK_PROVISIONING);
    }

    @Test
    public void testAddSuggestionWithValidCarrierIdWithoutCarrierProvisionPermission() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.carrierId = VALID_CARRIER_ID;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);

        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);

        assertEquals(status,
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED);
    }

    @Test
    public void testAddSuggestionWithDefaultCarrierIdWithoutCarrierProvisionPermission() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);

        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);

        assertEquals(status, WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
    }

    /**
     * Verify we return the network suggestion matches the target FQDN and user already approved.
     */
    @Test
    public void testGetPasspointSuggestionFromFqdnWithUserApproval() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.FQDN = TEST_FQDN;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                TEST_PACKAGE_1, TEST_FEATURE), WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        Set<ExtendedWifiNetworkSuggestion> ewns =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN);
        assertEquals(1, ewns.size());
        assertEquals(networkSuggestion, ewns.iterator().next().wns);
    }

    /**
     * Verify we return no network suggestion with matched target FQDN but user not approved.
     */
    @Test
    public void testGetPasspointSuggestionFromFqdnWithoutUserApproval() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.FQDN = TEST_FQDN;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        placeholderConfig.creatorUid = TEST_UID_1;
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                TEST_PACKAGE_1, TEST_FEATURE), WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        Set<ExtendedWifiNetworkSuggestion> ewns =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN);
        assertTrue(ewns.isEmpty());
    }

    /**
     * Verify return true when allow user manually connect and user approved the app
     */
    @Test
    public void testIsPasspointSuggestionSharedWithUserSetToTrue() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.FQDN = TEST_FQDN;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        placeholderConfig.creatorUid = TEST_UID_1;
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                TEST_PACKAGE_1, TEST_FEATURE), WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(false, TEST_UID_1, TEST_PACKAGE_1);
        assertFalse(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        assertTrue(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));

        placeholderConfig.carrierId = TEST_CARRIER_ID;
        placeholderConfig.subscriptionId = TEST_SUBID;
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(placeholderConfig))
                .thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUBID)).thenReturn(false);
        assertFalse(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));

        when(mWifiCarrierInfoManager.isSimReady(TEST_SUBID)).thenReturn(true);
        assertTrue(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));

        placeholderConfig.meteredHint = true;
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(placeholderConfig))
                .thenReturn(true);
        assertFalse(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));
    }

    /**
     * Verify return true when disallow user manually connect and user approved the app
     */
    @Test
    public void testIsPasspointSuggestionSharedWithUserSetToFalse() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.FQDN = TEST_FQDN;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, true, false, false, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                TEST_PACKAGE_1, TEST_FEATURE), WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(false, TEST_UID_1, TEST_PACKAGE_1);
        assertFalse(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        assertFalse(mWifiNetworkSuggestionsManager
                .isPasspointSuggestionSharedWithUser(placeholderConfig));
    }

    /**
     * test getWifiConfigForMatchedNetworkSuggestionsSharedWithUser.
     *  - app without user approval will not be returned.
     *  - open network will not be returned.
     *  - suggestion doesn't allow user manually connect will not be return.
     *  - Multiple duplicate ScanResults will only return single matched config.
     */
    @Test
    public void testGetWifiConfigForMatchedNetworkSuggestionsSharedWithUser() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, false, false, false, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion3 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<ScanResult> scanResults = new ArrayList<>();
        scanResults.add(
                createScanDetailForNetwork(networkSuggestion1.wifiConfiguration).getScanResult());
        scanResults.add(
                createScanDetailForNetwork(networkSuggestion2.wifiConfiguration).getScanResult());

        // Create two same ScanResult for networkSuggestion3
        ScanResult scanResult1 = createScanDetailForNetwork(networkSuggestion3.wifiConfiguration)
                .getScanResult();
        ScanResult scanResult2 = new ScanResult(scanResult1);
        scanResults.add(scanResult1);
        scanResults.add(scanResult2);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                    add(networkSuggestion3);
                }};
        networkSuggestion1.wifiConfiguration.fromWifiNetworkSuggestion = true;
        networkSuggestion2.wifiConfiguration.fromWifiNetworkSuggestion = true;
        networkSuggestion3.wifiConfiguration.fromWifiNetworkSuggestion = true;
        networkSuggestion1.wifiConfiguration.shared = false;
        networkSuggestion2.wifiConfiguration.shared = false;
        networkSuggestion3.wifiConfiguration.shared = false;
        networkSuggestion1.wifiConfiguration.creatorName = TEST_PACKAGE_1;
        networkSuggestion2.wifiConfiguration.creatorName = TEST_PACKAGE_1;
        networkSuggestion3.wifiConfiguration.creatorName = TEST_PACKAGE_1;

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        setupGetConfiguredNetworksFromWcm(networkSuggestion1.wifiConfiguration,
                networkSuggestion2.wifiConfiguration, networkSuggestion3.wifiConfiguration);
        // When app is not approved, empty list will be returned
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(false, TEST_UID_1, TEST_PACKAGE_1);
        List<WifiConfiguration> wifiConfigurationList = mWifiNetworkSuggestionsManager
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(scanResults);
        assertEquals(0, wifiConfigurationList.size());
        // App is approved, only allow user connect, not open network will return.
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        wifiConfigurationList = mWifiNetworkSuggestionsManager
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(scanResults);
        assertEquals(1, wifiConfigurationList.size());
        networkSuggestion3.wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        WifiConfigurationTestUtil.assertConfigurationEqual(
                networkSuggestion3.wifiConfiguration, wifiConfigurationList.get(0));
    }

    /**
     * test getWifiConfigForMatchedNetworkSuggestionsSharedWithUser on carrier network.
     *  - SIM is not present will not be return
     */
    @Test
    public void testGetWifiConfigForMatchedCarrierNetworkSuggestionsSharedWithUser() {
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion1.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        networkSuggestion2.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        networkSuggestion1.wifiConfiguration.shared = false;
        networkSuggestion2.wifiConfiguration.shared = false;
        WifiConfiguration configuration = networkSuggestion1.wifiConfiguration;

        // Suggestion1 has SIM present, suggestion2 has SIM absent
        when(mWifiCarrierInfoManager
                .getBestMatchSubscriptionId(argThat(new WifiConfigMatcher(configuration))))
                .thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUBID)).thenReturn(true);
        List<ScanResult> scanResults = new ArrayList<>();
        scanResults.add(
                createScanDetailForNetwork(networkSuggestion1.wifiConfiguration).getScanResult());
        scanResults.add(
                createScanDetailForNetwork(networkSuggestion2.wifiConfiguration).getScanResult());

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        networkSuggestion1.wifiConfiguration.fromWifiNetworkSuggestion = true;
        networkSuggestion2.wifiConfiguration.fromWifiNetworkSuggestion = true;
        networkSuggestion1.wifiConfiguration.creatorName = TEST_PACKAGE_1;
        networkSuggestion2.wifiConfiguration.creatorName = TEST_PACKAGE_1;
        networkSuggestion1.wifiConfiguration.subscriptionId = TEST_SUBID;
        setupGetConfiguredNetworksFromWcm(networkSuggestion1.wifiConfiguration,
                networkSuggestion2.wifiConfiguration);
        List<WifiConfiguration> wifiConfigurationList = mWifiNetworkSuggestionsManager
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(scanResults);
        assertEquals(1, wifiConfigurationList.size());
        networkSuggestion1.wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        WifiConfigurationTestUtil.assertConfigurationEqual(
                networkSuggestion1.wifiConfiguration, wifiConfigurationList.get(0));
    }

    class WifiConfigMatcher implements ArgumentMatcher<WifiConfiguration> {
        private final WifiConfiguration mConfig;

        WifiConfigMatcher(WifiConfiguration config) {
            assertNotNull(config);
            mConfig = config;
        }

        @Override
        public boolean matches(WifiConfiguration otherConfig) {
            if (otherConfig == null) return false;
            return mConfig.SSID.equals(otherConfig.SSID);
        }
    }

    private void assertSuggestionsEquals(Set<WifiNetworkSuggestion> expectedSuggestions,
            Set<ExtendedWifiNetworkSuggestion> actualExtSuggestions) {
        Set<WifiNetworkSuggestion> actualSuggestions = actualExtSuggestions.stream()
                .map(ewns -> ewns.wns)
                .collect(Collectors.toSet());
        assertEquals(expectedSuggestions, actualSuggestions);
    }

    private void setupGetConfiguredNetworksFromWcm(WifiConfiguration...configs) {
        List<WifiConfiguration> wcmConfigs = new ArrayList<>();
        for (int i = 0; i < configs.length; i++) {
            WifiConfiguration config = new WifiConfiguration(configs[i]);
            config.shared = false;
            when(mWifiConfigManager.getConfiguredNetwork(config.getProfileKey()))
                    .thenReturn(config);
            wcmConfigs.add(config);
        }
        when(mWifiConfigManager.getConfiguredNetworks()).thenReturn(wcmConfigs);
    }

    /**
     * Verify error code returns when add SIM-based network from app has no carrier privileges.
     */
    @Test
    public void testAddSimCredentialNetworkWithoutCarrierPrivileges() {
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        if (SdkLevel.isAtLeastS()) {
            config.subscriptionId = TEST_SUBID;
        }
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TelephonyManager.UNKNOWN_CARRIER_ID);
        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED, status);
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
        assertEquals(0, mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).size());
        verify(mWifiMetrics, never()).incrementNetworkSuggestionApiUsageNumOfAppInType(anyInt());
    }

    /**
     * Verify success when add SIM-based network from app has carrier privileges.
     */
    @Test
    public void testAddSimCredentialNetworkWithCarrierPrivileges() throws RemoteException {
        mWifiNetworkSuggestionsManager.addSuggestionUserApprovalStatusListener(
                mUserApprovalStatusListener,  TEST_PACKAGE_1, TEST_UID_1);
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        if (SdkLevel.isAtLeastS()) {
            config.subscriptionId = TEST_SUBID;
        }
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(VALID_CARRIER_ID);
        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
        assertEquals(1,  mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).size());
        verify(mWifiMetrics).incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_CARRIER_PRIVILEGED);
        verify(mUserApprovalStatusListener).onUserApprovalStatusChange(
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE);

        WifiNetworkSuggestion suggestionToRemove = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(List.of(suggestionToRemove), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    /**
     * Verify success when add SIM-based network from app has carrier provision permission.
     */
    @Test
    public void testAddSimCredentialNetworkWithCarrierProvisionPermission() {
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        if (SdkLevel.isAtLeastS()) {
            config.subscriptionId = TEST_SUBID;
        }
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TelephonyManager.UNKNOWN_CARRIER_ID);
        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
        assertEquals(1,  mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).size());
    }

    /**
     * Verify matched SIM-based network will return when imsi protection is available.
     */
    @Test
    public void testMatchSimBasedNetworkWithImsiProtection() {
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(VALID_CARRIER_ID);
        when(mWifiCarrierInfoManager.getMatchingSubId(VALID_CARRIER_ID)).thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(TEST_SUBID)).thenReturn(true);
        ScanDetail scanDetail = createScanDetailForNetwork(config);
        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
        Set<WifiNetworkSuggestion> expectedMatchingNetworkSuggestions =
                new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
        assertSuggestionsEquals(expectedMatchingNetworkSuggestions, matchingExtNetworkSuggestions);
    }

    /**
     * Verify when SIM changes, the app loses carrier privilege. Suggestions from this app will be
     * removed. If this app suggests again, it will be considered as normal suggestor.
     */
    @Test
    public void testSimStateChangeWillResetCarrierPrivilegedApp() {
        if (SdkLevel.isAtLeastT()) {
            return;
        }
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(VALID_CARRIER_ID);
        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TelephonyManager.UNKNOWN_CARRIER_ID);
        mWifiNetworkSuggestionsManager.updateCarrierPrivilegedApps();
        assertEquals(0,  mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).size());
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED, status);
        networkSuggestionList.clear();
        networkSuggestionList.add(createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP));
        status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);
        verify(mWifiNotificationManager).notify(anyInt(), any());
    }

    /**
     * Verify when SIM changes, the app loses carrier privilege. Suggestions from this app will be
     * removed. If this app suggests again, it will be considered as normal suggestor.
     */
    @Test
    public void testCarrierPrivilegedChangeWillResetCarrierPrivilegedApp() {
        if (!SdkLevel.isAtLeastT()) {
            return;
        }
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        networkSuggestionList.add(networkSuggestion);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(VALID_CARRIER_ID);
        int status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TelephonyManager.UNKNOWN_CARRIER_ID);
        mWifiNetworkSuggestionsManager.updateCarrierPrivilegedApps(Collections.emptySet());
        assertEquals(0,  mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).size());
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED, status);
        networkSuggestionList.clear();
        networkSuggestionList.add(createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP));
        status = mWifiNetworkSuggestionsManager
                .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS, status);
        verify(mWifiNotificationManager).notify(anyInt(), any());
    }

    /**
     * Verify set AllowAutoJoin on suggestion network .
     */
    @Test
    public void testSetAllowAutoJoinOnSuggestionNetwork()  {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenOweNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        WifiConfiguration configuration =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        configuration.fromWifiNetworkSuggestion = true;
        configuration.shared = false;
        configuration.ephemeral = true;
        configuration.creatorName = TEST_PACKAGE_1;
        configuration.creatorUid = TEST_UID_1;
        // No matching will return false.
        assertFalse(mWifiNetworkSuggestionsManager
                .allowNetworkSuggestionAutojoin(configuration, false));
        verify(mWifiConfigManager, never()).saveToStore(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        reset(mWifiConfigManager);

        assertTrue(mWifiNetworkSuggestionsManager
                .allowNetworkSuggestionAutojoin(configuration, false));
        verify(mWifiConfigManager).saveToStore(true);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForWifiConfiguration(configuration,
                        TEST_BSSID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertFalse(ewns.isAutojoinEnabled);
        }
    }

    /**
     * Verify set AllowAutoJoin on passpoint suggestion network.
     */
    @Test
    public void testSetAllowAutoJoinOnPasspointSuggestionNetwork() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        reset(mWifiConfigManager);
        // Create WifiConfiguration for Passpoint network.
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.FQDN = TEST_FQDN;
        config.providerFriendlyName = TEST_FRIENDLY_NAME;
        config.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        config.fromWifiNetworkSuggestion = true;
        config.shared = false;
        config.ephemeral = true;
        config.creatorName = TEST_PACKAGE_1;
        config.creatorUid = TEST_UID_1;
        // When update PasspointManager is failure, will return false.
        when(mPasspointManager.enableAutojoin(anyString(), isNull(), anyBoolean()))
                .thenReturn(false);
        assertFalse(mWifiNetworkSuggestionsManager
                .allowNetworkSuggestionAutojoin(config, false));
        verify(mWifiConfigManager, never()).saveToStore(true);

        // When update PasspointManager is success, will return true and persist suggestion.
        when(mPasspointManager.enableAutojoin(anyString(), isNull(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mWifiNetworkSuggestionsManager
                .allowNetworkSuggestionAutojoin(config, false));
        verify(mWifiConfigManager).saveToStore(true);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForWifiConfiguration(config, TEST_BSSID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertFalse(ewns.isAutojoinEnabled);
        }
    }

    /**
     * Verify that both passpoint configuration and non passpoint configuration could match
     * the ScanResults.
     */
    @Test
    public void getMatchingScanResultsTestWithPasspointAndNonPasspointMatch() {
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN, null);
        PasspointConfiguration mockPasspoint = mock(PasspointConfiguration.class);
        WifiNetworkSuggestion passpointSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, mockPasspoint, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion nonPasspointSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(),
                null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> suggestions = new ArrayList<>() {{
                add(passpointSuggestion);
                add(nonPasspointSuggestion);
                }};
        ScanResult passpointScanResult = new ScanResult();
        passpointScanResult.wifiSsid = WifiSsid.fromUtf8Text("passpoint");
        List<ScanResult> ppSrList = new ArrayList<>() {{
                add(passpointScanResult);
                }};
        ScanResult nonPasspointScanResult = new ScanResult();
        nonPasspointScanResult.wifiSsid = WifiSsid.fromUtf8Text(
                nonPasspointSuggestion.wifiConfiguration.SSID);
        List<ScanResult> nonPpSrList = new ArrayList<>() {{
                add(nonPasspointScanResult);
                }};
        List<ScanResult> allSrList = new ArrayList<>() {{
                add(passpointScanResult);
                add(nonPasspointScanResult);
                add(null);
                }};
        when(mPasspointManager.getMatchingScanResults(eq(mockPasspoint), eq(allSrList)))
                .thenReturn(ppSrList);
        ScanResultMatchInfo mockMatchInfo = mock(ScanResultMatchInfo.class);
        ScanResultMatchInfo nonPasspointMi = new ScanResultMatchInfo();
        nonPasspointMi.networkSsid = nonPasspointSuggestion.wifiConfiguration.SSID;
        MockitoSession session = ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT)
                .mockStatic(ScanResultMatchInfo.class).startMocking();
        try {
            doReturn(nonPasspointMi).when(
                    () -> ScanResultMatchInfo.fromWifiConfiguration(
                            nonPasspointSuggestion.wifiConfiguration));
            doReturn(nonPasspointMi).when(
                    () -> ScanResultMatchInfo.fromScanResult(nonPasspointScanResult));
            doReturn(mockMatchInfo).when(
                    () -> ScanResultMatchInfo.fromScanResult(passpointScanResult));
            Map<WifiNetworkSuggestion, List<ScanResult>> result =
                    mWifiNetworkSuggestionsManager.getMatchingScanResults(suggestions, allSrList);
            assertEquals(2, result.size());
            assertEquals(1, result.get(nonPasspointSuggestion).size());
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Verify that the wifi configuration doesn't match anything
     */
    @Test
    public void getMatchingScanResultsTestWithMatchNothing() {
        WifiNetworkSuggestion nonPasspointSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(),
                null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> suggestions = new ArrayList<>() {{
                add(nonPasspointSuggestion);
                }};
        ScanResult nonPasspointScanResult = new ScanResult();
        nonPasspointScanResult.wifiSsid = WifiSsid.fromUtf8Text("non-passpoint");
        List<ScanResult> allSrList = new ArrayList<>() {{
                add(nonPasspointScanResult);
            }};

        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(
                ScanResultMatchInfo.class).startMocking();
        ScanResultMatchInfo mockMatchInfo = mock(ScanResultMatchInfo.class);
        ScanResultMatchInfo miFromConfig = new ScanResultMatchInfo();
        miFromConfig.networkSsid = nonPasspointSuggestion.wifiConfiguration.SSID;
        try {
            when(ScanResultMatchInfo.fromWifiConfiguration(any(WifiConfiguration.class)))
                    .thenReturn(miFromConfig);
            when(ScanResultMatchInfo.fromScanResult(eq(nonPasspointScanResult)))
                    .thenReturn(mockMatchInfo);
            Map<WifiNetworkSuggestion, List<ScanResult>> result =
                    mWifiNetworkSuggestionsManager.getMatchingScanResults(suggestions, allSrList);
            assertEquals(1, result.size());
            assertEquals(0, result.get(nonPasspointSuggestion).size());
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Verify that the wifi configuration doesn't match anything if the Wificonfiguration
     * is invalid.
     */
    @Test
    public void getMatchingScanResultsTestWithInvalidWifiConfiguration() {
        WifiNetworkSuggestion nonPasspointSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(),
                null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> suggestions = new ArrayList<>() {{
                add(nonPasspointSuggestion);
            }};
        ScanResult nonPasspointScanResult = new ScanResult();
        nonPasspointScanResult.wifiSsid = WifiSsid.fromUtf8Text("non-passpoint");
        List<ScanResult> allSrList = new ArrayList<>() {{
                add(nonPasspointScanResult);
            }};

        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(
                ScanResultMatchInfo.class).startMocking();
        try {
            when(ScanResultMatchInfo.fromWifiConfiguration(any(WifiConfiguration.class)))
                    .thenReturn(null);

            Map<WifiNetworkSuggestion, List<ScanResult>> result =
                    mWifiNetworkSuggestionsManager.getMatchingScanResults(suggestions, allSrList);
            assertEquals(1, result.size());
            assertEquals(0, result.get(nonPasspointSuggestion).size());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testUpdateAutoJoinIfImsiProtectionIsEnabledAndDisabled() {
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TEST_CARRIER_ID);
        when(mWifiCarrierInfoManager.getMatchingSubId(TEST_CARRIER_ID)).thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.getCarrierNameForSubId(TEST_SUBID))
                .thenReturn(TEST_CARRIER_NAME);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);

        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithSimCredential(TEST_FQDN, TEST_IMSI, TEST_REALM);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion passpointSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                Arrays.asList(networkSuggestion, passpointSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        mUserApproveCarrierListenerArgumentCaptor.getValue()
                .onImsiProtectedOrUserApprovalChanged(TEST_CARRIER_ID, true);

        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(createScanDetailForNetwork(eapSimConfig));
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertTrue(ewns.isAutojoinEnabled);
        }

        mUserApproveCarrierListenerArgumentCaptor.getValue()
                .onImsiProtectedOrUserApprovalChanged(TEST_CARRIER_ID, false);

        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertFalse(ewns.isAutojoinEnabled);
        }
    }

    /**
     * Verify when matching a SIM-Based network without IMSI protection, framework will mark it
     * auto-join disable and send notification. If user click on allow, will restore the auto-join
     * config.
     */
    @Test
    public void testSendImsiProtectionNotificationOnUserAllowed() {
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TEST_CARRIER_ID);
        when(mWifiCarrierInfoManager.getMatchingSubId(TEST_CARRIER_ID)).thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.getCarrierNameForSubId(TEST_SUBID))
                .thenReturn(TEST_CARRIER_NAME);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);

        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithSimCredential(TEST_FQDN, TEST_IMSI, TEST_REALM);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion passpointSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                Arrays.asList(networkSuggestion, passpointSuggestion);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        verifyNoMoreInteractions(mWifiNotificationManager);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(createScanDetailForNetwork(eapSimConfig));
        verify(mWifiCarrierInfoManager)
                .sendImsiProtectionExemptionNotificationIfRequired(TEST_CARRIER_ID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertFalse(ewns.isAutojoinEnabled);
        }

        // Simulate user approved carrier
        eapSimConfig.fromWifiNetworkSuggestion = true;
        eapSimConfig.shared = false;
        eapSimConfig.creatorUid = TEST_UID_1;
        eapSimConfig.creatorName = TEST_PACKAGE_1;
        when(mWifiConfigManager.getConfiguredNetwork(anyString())).thenReturn(eapSimConfig);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        mUserApproveCarrierListenerArgumentCaptor.getValue()
                .onImsiProtectedOrUserApprovalChanged(TEST_CARRIER_ID, true);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(true);
        verify(mPasspointManager).enableAutojoin(anyString(), any(), eq(true));
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false));
        matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(createScanDetailForNetwork(eapSimConfig));

        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertTrue(ewns.isAutojoinEnabled);
        }
        verify(mWifiConfigManager, atLeastOnce()).saveToStore(true);
    }

    @Test
    public void testAutoJoinIsNotChangedWhenOobPseudonymIsEnabled() {
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TEST_CARRIER_ID);
        when(mWifiCarrierInfoManager.getMatchingSubId(TEST_CARRIER_ID)).thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.getCarrierNameForSubId(TEST_SUBID))
                .thenReturn(TEST_CARRIER_NAME);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(true);
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);

        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithSimCredential(TEST_FQDN, TEST_IMSI, TEST_REALM);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.setPasspointUniqueId(passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion passpointSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                Arrays.asList(networkSuggestion, passpointSuggestion);


        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        verifyNoMoreInteractions(mWifiNotificationManager);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(createScanDetailForNetwork(eapSimConfig));
        verify(mWifiCarrierInfoManager)
                .sendImsiProtectionExemptionNotificationIfRequired(TEST_CARRIER_ID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertTrue(ewns.isAutojoinEnabled);
        }
    }

    @Test
    public void testNotSendImsiProtectionNotificationOnPeapNetwork() {
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE_1))
                .thenReturn(TEST_CARRIER_ID);
        when(mWifiCarrierInfoManager.getMatchingSubId(TEST_CARRIER_ID)).thenReturn(TEST_SUBID);
        when(mWifiCarrierInfoManager.getCarrierNameForSubId(TEST_SUBID))
                .thenReturn(TEST_CARRIER_NAME);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);

        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.AKA);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, true, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                Arrays.asList(networkSuggestion);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        verifyNoMoreInteractions(mWifiNotificationManager);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(createScanDetailForNetwork(eapSimConfig));
        verify(mWifiCarrierInfoManager, never())
                .sendImsiProtectionExemptionNotificationIfRequired(TEST_CARRIER_ID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertTrue(ewns.isAutojoinEnabled);
        }
    }

    /**
     * Verify adding invalid suggestions will return right error reason code.
     */
    @Test
    public void testAddInvalidNetworkSuggestions() {
        WifiConfiguration invalidConfig = WifiConfigurationTestUtil.createOpenNetwork();
        invalidConfig.SSID = "";
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(invalidConfig,
                null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager
                        .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
    }

    /**
     * Verify adding invalid passpoint suggestions will return right error reason code.
     */
    @Test
    public void testAddInvalidPasspointNetworkSuggestions() {
        PasspointConfiguration passpointConfiguration = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(TEST_FQDN);
        passpointConfiguration.setHomeSp(homeSp);
        WifiConfiguration placeholderConfig = new WifiConfiguration();
        placeholderConfig.FQDN = TEST_FQDN;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager
                        .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
    }

    private List<WifiNetworkSuggestion> setupAndGetPnoAvailableSuggestions() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = new WifiConfiguration();
        placeholderConfig.FQDN = TEST_FQDN;
        WifiNetworkSuggestion networkSuggestion =
                createWifiNetworkSuggestion(network1, null, false, false, true, true,
                        DEFAULT_PRIORITY_GROUP);
        WifiNetworkSuggestion passpointSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                    add(passpointSuggestion);
                }};
        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager
                        .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
        return networkSuggestionList;
    }

    /**
     * Verify getAllScanOptimizationSuggestionNetworks will only return user approved,
     * non-passpoint network.
     */
    @Test
    public void testGetPnoAvailableSuggestions() {
        List<WifiNetworkSuggestion> suggestions = setupAndGetPnoAvailableSuggestions();
        WifiConfiguration network1 = suggestions.get(0).wifiConfiguration;
        assertTrue(mWifiNetworkSuggestionsManager
                .getAllScanOptimizationSuggestionNetworks().isEmpty());
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        List<WifiConfiguration> pnoNetwork =
                mWifiNetworkSuggestionsManager.getAllScanOptimizationSuggestionNetworks();
        assertEquals(1, pnoNetwork.size());
        assertEquals(network1.SSID, pnoNetwork.get(0).SSID);
    }

    /**
     * Verifies that getAllPasspointScanOptimizationSuggestionNetworks will return the expected
     * user-approved Passpoint networks.
     */
    @Test
    public void testGetPasspointPnoAvailableSuggestions() {
        setupAndGetPnoAvailableSuggestions();
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        // Suggestion should be available in the full list of results, but not
        // in the list of Passpoint suggestions that include an SSID.
        assertFalse(
                mWifiNetworkSuggestionsManager
                        .getAllPasspointScanOptimizationSuggestionNetworks(false)
                        .isEmpty());
        assertTrue(
                mWifiNetworkSuggestionsManager
                        .getAllPasspointScanOptimizationSuggestionNetworks(true)
                        .isEmpty());

        // Assign an SSID to the Passpoint suggestion. It should now be retrievable when
        // requesting the list of Passpoint suggestions that include an SSID.
        final String ssid = "my-passpoint-network";
        when(mPasspointManager.getMostRecentSsidForProfile(any())).thenReturn(ssid);
        List<WifiConfiguration> configs =
                mWifiNetworkSuggestionsManager.getAllPasspointScanOptimizationSuggestionNetworks(
                        true);
        assertEquals(1, configs.size());
        assertEquals(ssid, configs.get(0).SSID);
    }

    /**
     * Verify if a suggestion is mostRecently connected, flag will be persist.
     */
    @Test
    public void testIsMostRecentlyConnectedSuggestion() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion =
                createWifiNetworkSuggestion(network, null, false, false, true, true,
                        DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList = Arrays.asList(networkSuggestion);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager
                        .add(networkSuggestionList, TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        when(mLruConnectionTracker.isMostRecentlyConnected(any())).thenReturn(true);
        Map<String, PerAppInfo> suggestionStore = new HashMap<>(mDataSource.toSerialize());
        PerAppInfo perAppInfo = suggestionStore.get(TEST_PACKAGE_1);
        ExtendedWifiNetworkSuggestion ewns =
                perAppInfo.extNetworkSuggestions.values().iterator().next();
        assertTrue(ewns.wns.wifiConfiguration.isMostRecentlyConnected);
        mDataSource.fromDeserialized(suggestionStore);
        verify(mLruConnectionTracker).addNetwork(any());
        reset(mLruConnectionTracker);

        when(mLruConnectionTracker.isMostRecentlyConnected(any())).thenReturn(false);
        suggestionStore = mDataSource.toSerialize();
        perAppInfo = suggestionStore.get(TEST_PACKAGE_1);
        ewns = perAppInfo.extNetworkSuggestions.values().iterator().next();
        assertFalse(ewns.wns.wifiConfiguration.isMostRecentlyConnected);
        mDataSource.fromDeserialized(suggestionStore);
        verify(mLruConnectionTracker, never()).addNetwork(any());
    }

    @Test
    public void testOnSuggestionUpdateListener() {
        WifiNetworkSuggestionsManager.OnSuggestionUpdateListener listener =
                mock(WifiNetworkSuggestionsManager.OnSuggestionUpdateListener.class);
        mWifiNetworkSuggestionsManager.addOnSuggestionUpdateListener(listener);

        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        verify(listener).onSuggestionsAddedOrUpdated(networkSuggestionList1);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(listener).onSuggestionsRemoved(networkSuggestionList1);
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromSameCarrierWithoutSameOpenSuggestion() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Without same open suggestion in the framework, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromSameCarrierWithoutSecureSuggestion() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Without secure suggestion in the framework, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredWithoutCarrierProvisioningPermission() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(anyString()))
                .thenReturn(TEST_CARRIER_ID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Without CarrierProvisioningPermission, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromDifferentCarrierId() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = VALID_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Open and secure suggestions have different carrierId, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromSameCarrierWithAutojoinDisabled() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, false, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Secure suggestions is auto-join disabled, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromSameCarrierWithDifferentMeterness() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network2.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Secure suggestions is auto-join disabled, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromSameCarrierWithNetworkDisabled() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        WifiConfiguration wcmConfig = new WifiConfiguration(network2);
        wcmConfig.fromWifiNetworkSuggestion = true;
        wcmConfig.creatorName = TEST_PACKAGE_1;
        wcmConfig.creatorUid = TEST_UID_1;
        WifiConfiguration.NetworkSelectionStatus status =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        when(status.isNetworkEnabled()).thenReturn(false);
        wcmConfig.setNetworkSelectionStatus(status);
        when(mWifiConfigManager.getConfiguredNetwork(wcmConfig.getProfileKey()))
                .thenReturn(wcmConfig);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Secure suggestions is auto-join disabled, should not be ignored.
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldNotBeIgnoredBySecureSuggestionFromSameCarrierWithOverlayFalse() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Both open and secure suggestions with same carrierId,
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testShouldBeIgnoredBySecureSuggestionFromSameCarrier() {
        when(mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt()))
                .thenReturn(true);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createOpenNetwork();
        ScanDetail scanDetail1 = createScanDetailForNetwork(network1);
        network1.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion1 = createWifiNetworkSuggestion(
                network1, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        ScanDetail scanDetail2 = createScanDetailForNetwork(network2);
        network2.carrierId = TEST_CARRIER_ID;
        WifiNetworkSuggestion suggestion2 = createWifiNetworkSuggestion(
                network2, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        // Both open and secure suggestions with same carrierId,
        List<WifiNetworkSuggestion> suggestionList = Arrays.asList(suggestion1, suggestion2);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(suggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertTrue(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network1, scanDetails));
    }

    @Test
    public void testUnregisterSuggestionConnectionStatusListenerNeverRegistered() {
        mWifiNetworkSuggestionsManager.unregisterSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1);
    }

    /**
     * Verify that we only return user approved suggestions.
     */
    @Test
    public void testGetApprovedNetworkSuggestions() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        // Reuse the same network credentials to ensure they both match.
        WifiNetworkSuggestion networkSuggestion2 = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        List<WifiNetworkSuggestion> networkSuggestionList2 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion2);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList2, TEST_UID_2,
                        TEST_PACKAGE_2, TEST_FEATURE));

        // nothing approved, return empty.
        assertTrue(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions().isEmpty());

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        // only app 1 approved.
        assertEquals(new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }},
                mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions());

        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_2);
        // both app 1 & 2 approved.
        assertEquals(new HashSet<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                    add(networkSuggestion2);
                }},
                mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions());
    }

    /**
     * Verify only carrier privileged app can suggest carrier merged network. A valid carrier
     * merged network must be metered enterprise network with a valid subscription Id.
     */
    @Test
    public void testAddCarrierMergedNetwork() {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        eapSimConfig.carrierMerged = true;
        eapSimConfig.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        eapSimConfig.subscriptionId = TEST_SUBID;
        config.carrierMerged = true;
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        config.subscriptionId = TEST_SUBID;

        // Adding carrier merged network < EAP will fail.
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Adding carrier merged network is not metered will fail.
        eapSimConfig.meteredOverride = WifiConfiguration.METERED_OVERRIDE_NONE;
        networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Adding carrier merged network without a valid SubID will fail.
        eapSimConfig.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        eapSimConfig.subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Adding carrier merged network from a non carrier privileged app will not be allowed.
        eapSimConfig.subscriptionId = TEST_SUBID;
        networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Adding a carrier merged network when the carrier configuration doesn't indicate it will
        // provision such networks is not allowed
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID)).thenReturn(
                true);
        eapSimConfig.carrierId = VALID_CARRIER_ID;
        networkSuggestion = createWifiNetworkSuggestion(
                eapSimConfig, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    /**
     * Verify when calling API from background user will fail.
     */
    @Test
    public void testCallingFromBackgroundUserWillFailed() {
        WifiConfiguration wifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                wifiConfiguration, null, false, false, true, true, DEFAULT_PRIORITY_GROUP);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        // When switch the user to background
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(TEST_UID_1))
                .thenReturn(false);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiNetworkSuggestionsManager.remove(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertTrue(mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).isEmpty());
        assertFalse(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));

        // When switch the user back to foreground
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(TEST_UID_1))
                .thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        assertFalse(mWifiNetworkSuggestionsManager.get(TEST_PACKAGE_1, TEST_UID_1).isEmpty());
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
    }

    @Test
    public void testSuggestionCarrierNetworkConnectionBroadcastAndDisconnectWithCarrierIdOnly() {
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUBID);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mInorder.verify(mWifiPermissionsUtil).doesUidBelongToCurrentUserOrDeviceOwner(anyInt());
        assertTrue(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE_1));

        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        connectNetwork.subscriptionId = TEST_SUBID;
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, connectNetwork, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(Arrays.asList(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(connectNetwork)));
        mInorder.verify(mWifiPermissionsUtil).doesUidBelongToCurrentUserOrDeviceOwner(anyInt());

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }

    @Test
    public void testTreatAppAsCarrierProvider() {
        assertFalse(mWifiNetworkSuggestionsManager
                .isAppWorkingAsCrossCarrierProvider(TEST_APP_NAME_1));
        mWifiNetworkSuggestionsManager.setAppWorkingAsCrossCarrierProvider(TEST_APP_NAME_1, true);
        assertTrue(mWifiNetworkSuggestionsManager
                .isAppWorkingAsCrossCarrierProvider(TEST_APP_NAME_1));
        mWifiNetworkSuggestionsManager.setAppWorkingAsCrossCarrierProvider(TEST_APP_NAME_1, false);
        assertFalse(mWifiNetworkSuggestionsManager
                .isAppWorkingAsCrossCarrierProvider(TEST_APP_NAME_1));
    }

    @Test
    public void testSetAnonymousIdentityOnSuggestionNetwork()  {
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        WifiConfiguration eapSimConfig = WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                new WifiConfiguration(eapSimConfig), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        WifiConfiguration configuration =
                new WifiConfiguration(eapSimConfig);
        configuration.fromWifiNetworkSuggestion = true;
        configuration.shared = false;
        configuration.ephemeral = true;
        configuration.creatorName = TEST_PACKAGE_1;
        configuration.creatorUid = TEST_UID_1;
        configuration.enterpriseConfig.setAnonymousIdentity(TEST_ANONYMOUS_IDENTITY);

        mWifiNetworkSuggestionsManager.setAnonymousIdentity(configuration);

        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForWifiConfiguration(configuration,
                        TEST_BSSID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertEquals(TEST_ANONYMOUS_IDENTITY, ewns.anonymousIdentity);
        }
        // Reset SIM network suggestion, Anonymous Identity should gone.
        mWifiNetworkSuggestionsManager.resetSimNetworkSuggestions();
        matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForWifiConfiguration(configuration,
                        TEST_BSSID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertEquals(null, ewns.anonymousIdentity);
        }
        verify(mWifiConfigManager, times(3)).saveToStore(true);
    }

    @Test
    public void testSetUserConnectChoice() {
        WifiConfigManager.OnNetworkUpdateListener listener = mNetworkListenerCaptor.getValue();
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenOweNetwork();
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                new WifiConfiguration(config), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);
        WifiConfiguration configuration =
                new WifiConfiguration(config);
        configuration.fromWifiNetworkSuggestion = true;
        configuration.shared = false;
        configuration.ephemeral = true;
        configuration.creatorName = TEST_PACKAGE_1;
        configuration.creatorUid = TEST_UID_1;

        listener.onConnectChoiceSet(List.of(configuration),
                USER_CONNECT_CHOICE, TEST_RSSI);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForWifiConfiguration(configuration,
                        TEST_BSSID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertEquals(USER_CONNECT_CHOICE, ewns.connectChoice);
            assertEquals(TEST_RSSI, ewns.connectChoiceRssi);
        }

        listener.onConnectChoiceRemoved(USER_CONNECT_CHOICE);
        matchedSuggestions = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForWifiConfiguration(configuration,
                        TEST_BSSID);
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestions) {
            assertEquals(null, ewns.connectChoice);
            assertEquals(0, ewns.connectChoiceRssi);
        }

        // Add suggestion and change user approval have 2, set and remove user choice have 2.
        verify(mWifiConfigManager, times(4)).saveToStore(true);

        reset(mWifiConfigManager);
        listener.onConnectChoiceRemoved(USER_CONNECT_CHOICE);
        listener.onConnectChoiceRemoved(null);
        verify(mWifiConfigManager, never()).saveToStore(anyBoolean());
    }

    /**
     * Helper function for creating a test configuration with user credential.
     *
     * @return {@link PasspointConfiguration}
     */
    private PasspointConfiguration createTestConfigWithUserCredential(String fqdn,
            String friendlyName) {
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(friendlyName);
        config.setHomeSp(homeSp);
        Credential credential = new Credential();
        credential.setRealm(TEST_REALM);
        credential.setCaCertificate(FakeKeys.CA_CERT0);
        Credential.UserCredential userCredential = new Credential.UserCredential();
        userCredential.setUsername("username");
        userCredential.setPassword("password");
        userCredential.setEapType(EAPConstants.EAP_TTLS);
        userCredential.setNonEapInnerMethod(Credential.UserCredential.AUTH_METHOD_MSCHAP);
        credential.setUserCredential(userCredential);
        config.setCredential(credential);
        return config;
    }

    /**
     * Helper function for creating a test configuration with SIM credential.
     *
     * @return {@link PasspointConfiguration}
     */
    private PasspointConfiguration createTestConfigWithSimCredential(String fqdn, String imsi,
            String realm) {
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(TEST_FRIENDLY_NAME);
        config.setHomeSp(homeSp);
        Credential credential = new Credential();
        credential.setRealm(realm);
        Credential.SimCredential simCredential = new Credential.SimCredential();
        simCredential.setImsi(imsi);
        simCredential.setEapType(EAPConstants.EAP_SIM);
        credential.setSimCredential(simCredential);
        config.setCredential(credential);
        return config;
    }

    private WifiConfiguration createPlaceholderConfigForPasspoint(String fqdn,
            String uniqueId) {
        WifiConfiguration config = new WifiConfiguration();
        config.FQDN = fqdn;
        config.setPasspointUniqueId(uniqueId);
        return config;
    }

    @Test
    public void testResetNotification() {
        mWifiNetworkSuggestionsManager.resetNotification();
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);
    }

    /**
     * Verify we return the merged network suggestion matches the target FQDN when merged network is
     * allowed .
     */
    @Test
    public void testGetMergedPasspointSuggestionFromFqdn() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID))
                .thenReturn(true);
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        placeholderConfig.carrierMerged = true;
        placeholderConfig.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        placeholderConfig.FQDN = TEST_FQDN;
        placeholderConfig.subscriptionId = TEST_SUBID;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(placeholderConfig,
                passpointConfiguration, true, false, true, true, DEFAULT_PRIORITY_GROUP);

        when(mPasspointManager.addOrUpdateProvider(any(PasspointConfiguration.class),
                anyInt(), anyString(), eq(true), eq(true), eq(false))).thenReturn(true);
        assertEquals(mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion), TEST_UID_1,
                TEST_PACKAGE_1, TEST_FEATURE), WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);

        Set<ExtendedWifiNetworkSuggestion> ewns =
                mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN);
        assertEquals(1, ewns.size());
        assertEquals(networkSuggestion, ewns.iterator().next().wns);

        // Change to disallow merged network, no matching suggestion should return.
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID))
                .thenReturn(false);
        ewns = mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN);
        assertEquals(0, ewns.size());
    }

    /**
     * Verify we return the merged network suggestion matches the target ScanDetail when merged
     * network is allowed .
     */
    @Test
    public void testGetMergedNetworkSuggestionsForScanDetail() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID))
                .thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        ScanDetail scanDetail = createScanDetailForNetwork(config);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        config.subscriptionId = TEST_SUBID;
        config.carrierMerged = true;
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<ExtendedWifiNetworkSuggestion> ewns = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail);
        assertEquals(1, ewns.size());

        // Change to disallow merged network, no matching suggestion should return.
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID))
                .thenReturn(false);
        ewns = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail);
        assertEquals(0, ewns.size());
    }

    /**
     * Verify we return the merged network suggestion matches the target ScanDetail when merged
     * network is allowed .
     */
    @Test
    public void testGetMergedNetworkSuggestionsWithSubscriptionGroupForScanDetail() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(TEST_UID_1))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any()))
                .thenReturn(TEST_SUBID);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        ScanDetail scanDetail = createScanDetailForNetwork(config);
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        config.setSubscriptionGroup(GROUP_UUID);
        config.carrierMerged = true;
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion), TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        Set<ExtendedWifiNetworkSuggestion> ewns = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail);
        assertEquals(1, ewns.size());
        assertEquals(TEST_SUBID, ewns.iterator().next()
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).subscriptionId);

        // Change to disallow merged network, no matching suggestion should return.
        when(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(TEST_SUBID))
                .thenReturn(false);
        ewns = mWifiNetworkSuggestionsManager
                .getNetworkSuggestionsForScanDetail(scanDetail);
        assertEquals(0, ewns.size());
    }

    @Test
    public void testAddNonRandomizedNetworkSuggestionForcedToRandomized() {
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.wifiConfiguration.macRandomizationSetting =
                WifiConfiguration.RANDOMIZATION_NONE;
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion),
                        TEST_UID_1, TEST_PACKAGE_1, TEST_FEATURE));
        assertEquals(WifiConfiguration.RANDOMIZATION_PERSISTENT,
                networkSuggestion.wifiConfiguration.macRandomizationSetting);
    }

    @Test
    public void testAddNonRandomizedPasspointSuggestionForcedToRandomized() {
        PasspointConfiguration passpointConfiguration =
                createTestConfigWithUserCredential(TEST_FQDN, TEST_FRIENDLY_NAME);
        WifiConfiguration placeholderConfig = createPlaceholderConfigForPasspoint(TEST_FQDN,
                passpointConfiguration.getUniqueId());
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                placeholderConfig, passpointConfiguration, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        networkSuggestion.passpointConfiguration.setMacRandomizationEnabled(false);
        networkSuggestion.passpointConfiguration.setNonPersistentMacRandomizationEnabled(false);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};
        when(mPasspointManager.addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        ArgumentCaptor<PasspointConfiguration> passpointConfigCaptor =
                ArgumentCaptor.forClass(PasspointConfiguration.class);
        verify(mPasspointManager).addOrUpdateProvider(passpointConfigCaptor.capture(),
                anyInt(), anyString(), anyBoolean(), anyBoolean(), eq(false));
        assertTrue(passpointConfigCaptor.getValue().isMacRandomizationEnabled());
        assertFalse(passpointConfigCaptor.getValue().isNonPersistentMacRandomizationEnabled());
    }

    @Test
    public void testIncompleteEnterpriseNetworkSuggestion() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"someNetwork\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        // EAP method is kept as Eap.NONE - should not crash, but return invalid
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                config, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};
        when(mWifiKeyStore.updateNetworkKeys(eq(networkSuggestion1.wifiConfiguration), any()))
                .thenReturn(true);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
    }

    /**
     * Verify successful removal of network suggestions with linger action when suggestion is
     * currently connected. Should trigger linger and delay remove WifiConfiguration.
     */
    @Test
    public void testRemoveNetworkSuggestionsWithActionCurrentlyConnected() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Now remove suggestion with lingering which is currently connected.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(networkSuggestion1.wifiConfiguration);
        when(mWifiConfigManager.getConfiguredNetwork(any()))
                .thenReturn(networkSuggestion1.wifiConfiguration);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(List.of(networkSuggestion1),
                        TEST_UID_1, TEST_PACKAGE_1, ACTION_REMOVE_SUGGESTION_LINGER));
        verify(mWifiScoreCard).removeNetwork(anyString());
        verify(mLruConnectionTracker).removeNetwork(any());
        verify(mPrimaryClientModeManager).setShouldReduceNetworkScore(true);
        verify(mWifiConfigManager, never()).removeSuggestionConfiguredNetwork(any());
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) DEFAULT_LINGER_DELAY_MS + 1);
        mLooper.moveTimeForward(DEFAULT_LINGER_DELAY_MS + 1);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(networkSuggestion1.wifiConfiguration)));

        verify(mWifiMetrics, times(2)).incrementNetworkSuggestionApiNumModification();
        verify(mWifiMetrics, times(1)).addNetworkSuggestionPriorityGroup(anyInt());
        ArgumentCaptor<List<Integer>> maxSizesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mWifiMetrics, times(2)).noteNetworkSuggestionApiListSizeHistogram(
                maxSizesCaptor.capture());
        verify(mWifiConfigManager).removeConnectChoiceFromAllNetworks(anyString());
        assertNotNull(maxSizesCaptor.getValue());
        assertEquals(maxSizesCaptor.getValue(), new ArrayList<Integer>() {{ add(1); }});
    }

    /**
     * Verify successful removal of network suggestions with linger action when suggestion is not
     * currently connected. Should remove WifiConfiguration immeditaly.
     */
    @Test
    public void testRemoveNetworkSuggestionsWithActionNotConnected() {
        WifiNetworkSuggestion networkSuggestion1 = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createOpenNetwork(), null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);

        List<WifiNetworkSuggestion> networkSuggestionList1 =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion1);
                }};

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList1, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));

        // Now remove suggestion with lingering which is currently connected.
        when(mWifiConfigManager.getConfiguredNetwork(any()))
                .thenReturn(networkSuggestion1.wifiConfiguration);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.remove(List.of(networkSuggestion1),
                        TEST_UID_1, TEST_PACKAGE_1, ACTION_REMOVE_SUGGESTION_LINGER));
        verify(mWifiScoreCard).removeNetwork(anyString());
        verify(mLruConnectionTracker).removeNetwork(any());
        verify(mPrimaryClientModeManager, never()).setShouldReduceNetworkScore(true);
        verify(mWifiConfigManager).removeSuggestionConfiguredNetwork(
                argThat(new WifiConfigMatcher(networkSuggestion1.wifiConfiguration)));

        verify(mWifiMetrics, times(2)).incrementNetworkSuggestionApiNumModification();
        verify(mWifiMetrics, times(1)).addNetworkSuggestionPriorityGroup(anyInt());
        ArgumentCaptor<List<Integer>> maxSizesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mWifiMetrics, times(2)).noteNetworkSuggestionApiListSizeHistogram(
                maxSizesCaptor.capture());
        verify(mWifiConfigManager).removeConnectChoiceFromAllNetworks(anyString());
        assertNotNull(maxSizesCaptor.getValue());
        assertEquals(maxSizesCaptor.getValue(), new ArrayList<Integer>() {{ add(1); }});
    }

    private static WifiNetworkSuggestion createWifiNetworkSuggestion(WifiConfiguration config,
            PasspointConfiguration passpointConfiguration,
            boolean isAppInteractionRequired,
            boolean isUserInteractionRequired,
            boolean isUserAllowedToManuallyConnect,
            boolean isInitialAutoJoinEnabled, int priorityGroup) {
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_PERSISTENT;
        return new WifiNetworkSuggestion(config, passpointConfiguration, isAppInteractionRequired,
                isUserInteractionRequired, isUserAllowedToManuallyConnect, isInitialAutoJoinEnabled,
                priorityGroup);
    }

    @Test
    public void testSuggestionWithBothSubscriptionIdAndSubscriptionGroupSetIsInvalid() {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.setSubscriptionGroup(GROUP_UUID);
        config.subscriptionId = TEST_SUBID;
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                config, null, false, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID,
                mWifiNetworkSuggestionsManager.add(List.of(networkSuggestion), TEST_UID_1,
                TEST_PACKAGE_1, TEST_FEATURE));
    }

    @Test
    public void testOnNetworkConnectionSuccessWithMatchAndNetworkTransitionDisable()
            throws Exception {
        assertTrue(mWifiNetworkSuggestionsManager.registerSuggestionConnectionStatusListener(
                mConnectionStatusListener, TEST_PACKAGE_1, TEST_UID_1));
        WifiNetworkSuggestion networkSuggestion = createWifiNetworkSuggestion(
                WifiConfigurationTestUtil.createPskNetwork(), null, true, false, true, true,
                DEFAULT_PRIORITY_GROUP);
        List<WifiNetworkSuggestion> networkSuggestionList =
                new ArrayList<WifiNetworkSuggestion>() {{
                    add(networkSuggestion);
                }};

        // Verify no crash with unmatched update
        mNetworkListenerCaptor.getValue().onSecurityParamsUpdate(networkSuggestion
                .wifiConfiguration, networkSuggestion.wifiConfiguration.getSecurityParamsList());
        mNetworkListenerCaptor.getValue().onSecurityParamsUpdate(null, Collections.EMPTY_LIST);

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiNetworkSuggestionsManager.add(networkSuggestionList, TEST_UID_1,
                        TEST_PACKAGE_1, TEST_FEATURE));
        mWifiNetworkSuggestionsManager.setHasUserApprovedForApp(true, TEST_UID_1, TEST_PACKAGE_1);

        // Simulate connecting to the network.
        WifiConfiguration connectNetwork =
                new WifiConfiguration(networkSuggestion.wifiConfiguration);
        connectNetwork.fromWifiNetworkSuggestion = true;
        connectNetwork.shared = false;
        connectNetwork.ephemeral = true;
        connectNetwork.creatorName = TEST_PACKAGE_1;
        connectNetwork.creatorUid = TEST_UID_1;
        WifiConfiguration updated = new WifiConfiguration(connectNetwork);
        updated.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_PSK, false);

        // Update to handle Network Transition Disable
        mNetworkListenerCaptor.getValue().onSecurityParamsUpdate(connectNetwork,
                updated.getSecurityParamsList());
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                WifiMetrics.ConnectionEvent.FAILURE_NONE, updated, TEST_BSSID);

        verify(mWifiMetrics).incrementNetworkSuggestionApiNumConnectSuccess();

        // Verify that the correct broadcast was sent out.
        mInorder.verify(mWifiPermissionsUtil).enforceCanAccessScanResults(eq(TEST_PACKAGE_1),
                eq(TEST_FEATURE), eq(TEST_UID_1), nullable(String.class));
        validatePostConnectionBroadcastSent(TEST_PACKAGE_1, networkSuggestion);

        // Verify no more broadcast were sent out.
        mInorder.verifyNoMoreInteractions();
    }
}
