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

import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.MANAGE_WIFI_COUNTRY_CODE;
import static android.Manifest.permission.WIFI_ACCESS_COEX_UNSAFE_CHANNELS;
import static android.Manifest.permission.WIFI_UPDATE_COEX_UNSAFE_CHANNELS;
import static android.content.Intent.ACTION_SHUTDOWN;
import static android.net.wifi.ScanResult.WIFI_BAND_60_GHZ;
import static android.net.wifi.ScanResult.WIFI_BAND_6_GHZ;
import static android.net.wifi.WifiAvailableChannel.FILTER_REGULATORY;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_SAP;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_STA;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_METERED;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT;
import static android.net.wifi.WifiManager.CHANNEL_DATA_KEY_FREQUENCY_MHZ;
import static android.net.wifi.WifiManager.CHANNEL_DATA_KEY_NUM_AP;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_SOFTAP;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_AWARE;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_DIRECT;
import static android.net.wifi.WifiManager.DEVICE_MOBILITY_STATE_STATIONARY;
import static android.net.wifi.WifiManager.EASY_CONNECT_CRYPTOGRAPHY_CURVE_PRIME256V1;
import static android.net.wifi.WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_CONFIGURATION_ERROR;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_LOCAL_ONLY;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_TETHERED;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.REQUEST_REGISTERED;
import static android.net.wifi.WifiManager.NOT_OVERRIDE_EXISTING_NETWORKS_ON_RESTORE;
import static android.net.wifi.WifiManager.SAP_START_FAILURE_GENERAL;
import static android.net.wifi.WifiManager.SAP_START_FAILURE_NO_CHANNEL;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_FAILED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;
import static android.os.Process.WIFI_UID;
import static android.os.Process.myUid;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_LOCAL_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.LocalOnlyHotspotRequestInfo.HOTSPOT_NO_ERROR;
import static com.android.server.wifi.SelfRecovery.REASON_API_CALL;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_NONE;
import static com.android.server.wifi.WifiSettingsConfigStore.D2D_ALLOWED_WHEN_INFRA_STA_DISABLED;
import static com.android.server.wifi.WifiSettingsConfigStore.SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI;
import static com.android.server.wifi.WifiSettingsConfigStore.SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_STA_FACTORY_MAC_ADDRESS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_WEP_ALLOWED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalAnswers.returnsSecondArg;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.app.compat.CompatChanges;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.bluetooth.BluetoothAdapter;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.wifi.WifiStatusCode;
import android.location.Location;
import android.location.LocationManager;
import android.net.DhcpInfo;
import android.net.DhcpOption;
import android.net.DhcpResultsParcelable;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkStack;
import android.net.TetheringManager;
import android.net.Uri;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.IActionListener;
import android.net.wifi.IBooleanListener;
import android.net.wifi.IByteArrayListener;
import android.net.wifi.ICoexCallback;
import android.net.wifi.IDppCallback;
import android.net.wifi.IIntegerListener;
import android.net.wifi.IInterfaceCreationInfoCallback;
import android.net.wifi.ILastCallerListener;
import android.net.wifi.IListListener;
import android.net.wifi.ILocalOnlyConnectionStatusListener;
import android.net.wifi.ILocalOnlyHotspotCallback;
import android.net.wifi.IMapListener;
import android.net.wifi.INetworkRequestMatchCallback;
import android.net.wifi.IOnWifiActivityEnergyInfoListener;
import android.net.wifi.IOnWifiDriverCountryCodeChangedListener;
import android.net.wifi.IOnWifiUsabilityStatsListener;
import android.net.wifi.IPnoScanResultsCallback;
import android.net.wifi.IScanResultsCallback;
import android.net.wifi.ISoftApCallback;
import android.net.wifi.IStringListener;
import android.net.wifi.ISubsystemRestartCallback;
import android.net.wifi.ISuggestionConnectionStatusListener;
import android.net.wifi.ISuggestionUserApprovalStatusListener;
import android.net.wifi.ITrafficStateCallback;
import android.net.wifi.ITwtCallback;
import android.net.wifi.ITwtCapabilitiesListener;
import android.net.wifi.ITwtStatsListener;
import android.net.wifi.IWifiBandsListener;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.IWifiLowLatencyLockListener;
import android.net.wifi.IWifiNetworkSelectionConfigListener;
import android.net.wifi.IWifiNetworkStateChangedListener;
import android.net.wifi.IWifiVerboseLoggingStatusChangedListener;
import android.net.wifi.MscsParams;
import android.net.wifi.QosCharacteristics;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SoftApState;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiBands;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.WifiNetworkSelectionConfig;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.os.connectivity.WifiActivityEnergyInfo;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.internal.os.PowerProfile;
import com.android.modules.utils.ParceledListSlice;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiServiceImpl.LocalOnlyRequestorCallback;
import com.android.server.wifi.WifiServiceImpl.SoftApCallbackInternal;
import com.android.server.wifi.b2b.WifiRoamingModeManager;
import com.android.server.wifi.coex.CoexManager;
import com.android.server.wifi.entitlement.PseudonymInfo;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.PasspointNetworkNominateHelper;
import com.android.server.wifi.hotspot2.PasspointProvisioningTestUtil;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto.UserActionEvent;
import com.android.server.wifi.util.ActionListenerWrapper;
import com.android.server.wifi.util.ApConfigUtil;
import com.android.server.wifi.util.LastCallerInfoManager;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import com.google.common.base.Strings;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Unit tests for {@link WifiServiceImpl}.
 *
 * Note: this is intended to build up over time and will not immediately cover the entire file.
 */
@SmallTest
public class WifiServiceImplTest extends WifiBaseTest {

    private static final String TAG = "WifiServiceImplTest";
    private static final String SCAN_PACKAGE_NAME = "scanPackage";
    private static final int DEFAULT_VERBOSE_LOGGING = 0;
    private static final String ANDROID_SYSTEM_PACKAGE = "android";
    private static final String TEST_PACKAGE_NAME = "TestPackage";
    private static final String TEST_PACKAGE_NAME_OTHER = "TestPackageOther";
    private static final String TEST_FEATURE_ID = "TestFeature";
    private static final String TEST_FEATURE_ID_OTHER = "TestFeatureOther";
    private static final String SYSUI_PACKAGE_NAME = "com.android.systemui";
    private static final String CERT_INSTALLER_PACKAGE_NAME = "com.android.certinstaller";
    private static final int TEST_PID = 6789;
    private static final int TEST_PID2 = 9876;
    private static final int TEST_UID = 1200000;
    private static final int OTHER_TEST_UID = 1300000;
    private static final int TEST_USER_HANDLE = 13;
    private static final int TEST_WIFI_CONNECTED_NETWORK_SCORER_IDENTIFIER = 1;
    private static final String WIFI_IFACE_NAME = "wlan0";
    private static final String WIFI_IFACE_NAME2 = "wlan1";
    private static final String TEST_COUNTRY_CODE = "US";
    private static final String TEST_NEW_COUNTRY_CODE = "TW";
    private static final String TEST_FACTORY_MAC = "10:22:34:56:78:92";
    private static final MacAddress TEST_FACTORY_MAC_ADDR = MacAddress.fromString(TEST_FACTORY_MAC);
    private static final String TEST_FQDN = "testfqdn";
    private static final String TEST_FRIENDLY_NAME = "testfriendlyname";
    private static final List<WifiConfiguration> TEST_WIFI_CONFIGURATION_LIST = Arrays.asList(
            WifiConfigurationTestUtil.generateWifiConfig(
                    0, 1000000, "\"red\"", true, true, null, null,
                    SECURITY_NONE),
            WifiConfigurationTestUtil.generateWifiConfig(
                    1, 1000001, "\"green\"", true, false, "example.com", "Green",
                    SECURITY_NONE),
            WifiConfigurationTestUtil.generateWifiConfig(
                    2, 1200000, "\"blue\"", false, true, null, null,
                    SECURITY_NONE),
            WifiConfigurationTestUtil.generateWifiConfig(
                    3, 1100000, "\"cyan\"", true, true, null, null,
                    SECURITY_NONE),
            WifiConfigurationTestUtil.generateWifiConfig(
                    4, 1100001, "\"yellow\"", true, true, "example.org", "Yellow",
                    SECURITY_NONE),
            WifiConfigurationTestUtil.generateWifiConfig(
                    5, 1100002, "\"magenta\"", false, false, null, null,
                    SECURITY_NONE));
    private static final int TEST_AP_FREQUENCY = 2412;
    private static final int TEST_AP_BANDWIDTH = SoftApInfo.CHANNEL_WIDTH_20MHZ;
    private static final TetheringManager.TetheringRequest TEST_TETHERING_REQUEST =
            new TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build();
    private static final String TEST_IFACE_NAME = "test-wlan0";
    private static final int NETWORK_CALLBACK_ID = 1100;
    private static final String TEST_CAP = "[RSN-PSK-CCMP]";
    private static final String TEST_SSID = "Sid's Place";
    private static final String TEST_SSID_WITH_QUOTES = "\"" + TEST_SSID + "\"";
    private static final String TEST_BSSID = "01:02:03:04:05:06";
    private static final String TEST_IP = "192.168.49.5";
    private static final String TEST_PACKAGE = "package";
    private static final int TEST_NETWORK_ID = 567;
    private static final WorkSource TEST_SETTINGS_WORKSOURCE = new WorkSource();
    private static final int TEST_SUB_ID = 1;
    private static final byte[] TEST_OUI = new byte[]{0x01, 0x02, 0x03};
    private static final int TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS = 1000;

    private SoftApInfo mTestSoftApInfo;
    private List<SoftApInfo> mTestSoftApInfoList;
    private Map<String, List<WifiClient>> mTestSoftApClients;
    private Map<String, SoftApInfo> mTestSoftApInfos;
    private WifiServiceImpl mWifiServiceImpl;
    private TestLooper mLooper;
    private WifiThreadRunner mWifiThreadRunner;
    private PowerManager mPowerManager;
    private PhoneStateListener mPhoneStateListener;
    private int mPid;
    private int mPid2 = Process.myPid();
    private OsuProvider mOsuProvider;
    private SoftApCallbackInternal mStateMachineSoftApCallback;
    private SoftApCallbackInternal mLohsApCallback;
    private String mLohsInterfaceName;
    private ApplicationInfo mApplicationInfo;
    private List<ClientModeManager> mClientModeManagers;
    private Bundle mExtras = new Bundle();
    private Bundle mAttribution = new Bundle();
    private static final String DPP_URI = "DPP:some_dpp_uri";
    private static final String DPP_PRODUCT_INFO = "DPP:some_dpp_uri_info";
    private static final WorkSource SETTINGS_WORKSOURCE =
            new WorkSource(Process.SYSTEM_UID, "system-service");

    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);

    final ArgumentCaptor<SoftApModeConfiguration> mSoftApModeConfigCaptor =
            ArgumentCaptor.forClass(SoftApModeConfiguration.class);

    @Mock Bundle mBundle;
    @Mock WifiContext mContext;
    @Mock Context mContextAsUser;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiCountryCode mWifiCountryCode;
    @Mock Clock mClock;
    @Mock WifiTrafficPoller mWifiTrafficPoller;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock HandlerThread mHandlerThread;
    @Mock Resources mResources;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock WifiLockManager mLockManager;
    @Mock WifiMulticastLockManager mWifiMulticastLockManager;
    @Mock WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock WifiBackupRestore mWifiBackupRestore;
    @Mock SoftApBackupRestore mSoftApBackupRestore;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock WifiPermissionsWrapper mWifiPermissionsWrapper;
    @Mock WifiSettingsStore mSettingsStore;
    @Mock ContentResolver mContentResolver;
    @Mock PackageManager mPackageManager;
    @Mock UserManager mUserManager;
    @Mock WifiApConfigStore mWifiApConfigStore;
    @Mock WifiConfiguration mApConfig;
    @Mock ActivityManager mActivityManager;
    @Mock AppOpsManager mAppOpsManager;
    @Mock IBinder mAppBinder;
    @Mock IBinder mAnotherAppBinder;
    @Mock LocalOnlyHotspotRequestInfo mRequestInfo;
    @Mock LocalOnlyHotspotRequestInfo mRequestInfo2;
    @Mock IProvisioningCallback mProvisioningCallback;
    @Mock ISoftApCallback mClientSoftApCallback;
    @Mock ISoftApCallback mAnotherSoftApCallback;
    @Mock PowerProfile mPowerProfile;
    @Mock WifiTrafficPoller mWifiTrafficPolller;
    @Mock ScanRequestProxy mScanRequestProxy;
    @Mock WakeupController mWakeupController;
    @Mock ITrafficStateCallback mTrafficStateCallback;
    @Mock INetworkRequestMatchCallback mNetworkRequestMatchCallback;
    @Mock WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock CoexManager mCoexManager;
    @Mock IOnWifiUsabilityStatsListener mOnWifiUsabilityStatsListener;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock WifiScoreCard mWifiScoreCard;
    @Mock WifiHealthMonitor mWifiHealthMonitor;
    @Mock PasspointManager mPasspointManager;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock FeatureFlags mFeatureFlags;
    @Mock IDppCallback mDppCallback;
    @Mock ILocalOnlyHotspotCallback mLohsCallback;
    @Mock ICoexCallback mCoexCallback;
    @Mock IScanResultsCallback mScanResultsCallback;
    @Mock ISuggestionConnectionStatusListener mSuggestionConnectionStatusListener;
    @Mock ILocalOnlyConnectionStatusListener mLocalOnlyConnectionStatusListener;
    @Mock ISuggestionUserApprovalStatusListener mSuggestionUserApprovalStatusListener;
    @Mock IOnWifiActivityEnergyInfoListener mOnWifiActivityEnergyInfoListener;
    @Mock ISubsystemRestartCallback mSubsystemRestartCallback;
    @Mock IWifiConnectedNetworkScorer mWifiConnectedNetworkScorer;
    @Mock WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock WifiScanAlwaysAvailableSettingsCompatibility mScanAlwaysAvailableSettingsCompatibility;
    @Mock PackageInfo mPackageInfo;
    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock WifiDataStall mWifiDataStall;
    @Mock WifiNative mWifiNative;
    @Mock ConnectHelper mConnectHelper;
    @Mock IActionListener mActionListener;
    @Mock WifiNetworkFactory mWifiNetworkFactory;
    @Mock UntrustedWifiNetworkFactory mUntrustedWifiNetworkFactory;
    @Mock OemWifiNetworkFactory mOemWifiNetworkFactory;
    @Mock RestrictedWifiNetworkFactory mRestrictedWifiNetworkFactory;
    @Mock MultiInternetManager mMultiInternetManager;
    @Mock MultiInternetWifiNetworkFactory mMultiInternetWifiNetworkFactory;
    @Mock WifiDiagnostics mWifiDiagnostics;
    @Mock WifiP2pConnection mWifiP2pConnection;
    @Mock SimRequiredNotifier mSimRequiredNotifier;
    @Mock WifiGlobals mWifiGlobals;
    @Mock AdaptiveConnectivityEnabledSettingObserver mAdaptiveConnectivityEnabledSettingObserver;
    @Mock MakeBeforeBreakManager mMakeBeforeBreakManager;
    @Mock WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock WifiPseudonymManager mWifiPseudonymManager;
    @Mock OpenNetworkNotifier mOpenNetworkNotifier;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock SarManager mSarManager;
    @Mock SelfRecovery mSelfRecovery;
    @Mock LastCallerInfoManager mLastCallerInfoManager;
    @Mock BuildProperties mBuildProperties;
    @Mock LinkProbeManager mLinkProbeManager;
    @Mock IOnWifiDriverCountryCodeChangedListener mIOnWifiDriverCountryCodeChangedListener;
    @Mock WifiShellCommand mWifiShellCommand;
    @Mock AfcManager mAfcManager;
    @Mock LocationManager mLocationManager;
    @Mock DevicePolicyManager mDevicePolicyManager;
    @Mock HalDeviceManager mHalDeviceManager;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock SsidTranslator mSsidTranslator;
    @Mock InterfaceConflictManager mInterfaceConflictManager;
    @Mock WifiKeyStore mWifiKeyStore;
    @Mock WifiPulledAtomLogger mWifiPulledAtomLogger;
    @Mock ScoringParams mScoringParams;
    @Mock ApplicationQosPolicyRequestHandler mApplicationQosPolicyRequestHandler;
    @Mock Location mLocation;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    @Mock PasspointNetworkNominateHelper mPasspointNetworkNominateHelper;
    @Mock WifiRoamingModeManager mWifiRoamingModeManager;
    @Mock BackupRestoreController mBackupRestoreController;
    @Mock WifiSettingsBackupRestore mWifiSettingsBackupRestore;
    @Captor ArgumentCaptor<Intent> mIntentCaptor;
    @Captor ArgumentCaptor<List> mListCaptor;
    @Mock TwtManager mTwtManager;
    @Rule
    // For frameworks
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    private MockitoSession mSession;

    WifiConfiguration mWifiConfig;

    WifiLog mLog;

    @Before public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = mockitoSession()
                .mockStatic(SubscriptionManager.class)
                .mockStatic(CompatChanges.class)
                .startMocking();

        mLog = spy(new LogcatLog(TAG));
        mLooper = new TestLooper();
        mApplicationInfo = new ApplicationInfo();
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
        when(mResources.getInteger(
                eq(R.integer.config_wifiHardwareSoftapMaxClientCount)))
                .thenReturn(10);
        WifiInjector.sWifiInjector = mWifiInjector;
        when(mRequestInfo.getPid()).thenReturn(mPid);
        when(mRequestInfo2.getPid()).thenReturn(mPid2);
        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mWifiInjector.getWifiCountryCode()).thenReturn(mWifiCountryCode);
        when(mWifiInjector.getWifiMetrics()).thenReturn(mWifiMetrics);
        when(mWifiInjector.getWifiNetworkFactory()).thenReturn(mWifiNetworkFactory);
        when(mWifiInjector.getUntrustedWifiNetworkFactory())
                .thenReturn(mUntrustedWifiNetworkFactory);
        when(mWifiInjector.getOemWifiNetworkFactory()).thenReturn(mOemWifiNetworkFactory);
        when(mWifiInjector.getRestrictedWifiNetworkFactory())
                .thenReturn(mRestrictedWifiNetworkFactory);
        when(mWifiInjector.getMultiInternetWifiNetworkFactory())
                .thenReturn(mMultiInternetWifiNetworkFactory);
        when(mWifiInjector.getMultiInternetManager()).thenReturn(mMultiInternetManager);
        when(mWifiInjector.getWifiDiagnostics()).thenReturn(mWifiDiagnostics);
        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mWifiInjector.getWifiHandlerThread()).thenReturn(mHandlerThread);
        when(mWifiInjector.getMakeBeforeBreakManager()).thenReturn(mMakeBeforeBreakManager);
        when(mWifiInjector.getWifiNotificationManager()).thenReturn(mWifiNotificationManager);
        when(mWifiInjector.getBuildProperties()).thenReturn(mBuildProperties);
        when(mWifiInjector.getLinkProbeManager()).thenReturn(mLinkProbeManager);
        when(mWifiInjector.makeWifiShellCommand(any())).thenReturn(mWifiShellCommand);
        when(mWifiInjector.getAfcManager()).thenReturn(mAfcManager);
        when(mWifiInjector.getPasspointNetworkNominateHelper())
                .thenReturn(mPasspointNetworkNominateHelper);
        // needed to mock this to call "handleBootCompleted"
        when(mWifiInjector.getPasspointProvisionerHandlerThread())
                .thenReturn(mock(HandlerThread.class));
        when(mWifiInjector.getWifiDeviceStateChangeManager())
                .thenReturn(mWifiDeviceStateChangeManager);
        when(mWifiInjector.getWifiSettingsBackupRestore()).thenReturn(mWifiSettingsBackupRestore);
        when(mWifiInjector.getBackupRestoreController()).thenReturn(mBackupRestoreController);
        when(mHandlerThread.getThreadHandler()).thenReturn(new Handler(mLooper.getLooper()));
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);
        when(mPackageManager.checkSignatures(anyInt(), anyInt()))
                .thenReturn(PackageManager.SIGNATURE_NO_MATCH);
        when(mWifiInjector.getWifiApConfigStore()).thenReturn(mWifiApConfigStore);
        doNothing().when(mFrameworkFacade).registerContentObserver(eq(mContext), any(),
                anyBoolean(), any());
        when(mFrameworkFacade.getSettingsWorkSource(any())).thenReturn(TEST_SETTINGS_WORKSOURCE);
        when(mContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(mContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);
        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);
        IPowerManager powerManagerService = mock(IPowerManager.class);
        IThermalService thermalService = mock(IThermalService.class);
        mPowerManager =
                new PowerManager(mContext, powerManagerService, thermalService, new Handler());
        when(mContext.getSystemServiceName(PowerManager.class)).thenReturn(Context.POWER_SERVICE);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mContext.createContextAsUser(eq(UserHandle.CURRENT), anyInt()))
                .thenReturn(mContextAsUser);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mFrameworkFacade);
        when(mWifiInjector.getWifiLockManager()).thenReturn(mLockManager);
        when(mWifiInjector.getWifiMulticastLockManager()).thenReturn(mWifiMulticastLockManager);
        when(mWifiInjector.getWifiLastResortWatchdog()).thenReturn(mWifiLastResortWatchdog);
        when(mWifiInjector.getWifiBackupRestore()).thenReturn(mWifiBackupRestore);
        when(mWifiInjector.getSoftApBackupRestore()).thenReturn(mSoftApBackupRestore);
        when(mWifiInjector.makeLog(anyString())).thenReturn(mLog);
        when(mWifiInjector.getWifiTrafficPoller()).thenReturn(mWifiTrafficPoller);
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mWifiPermissionsUtil);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiInjector.getWifiSettingsStore()).thenReturn(mSettingsStore);
        when(mWifiInjector.getClock()).thenReturn(mClock);
        when(mWifiInjector.getScanRequestProxy()).thenReturn(mScanRequestProxy);
        when(mWifiInjector.getWakeupController()).thenReturn(mWakeupController);
        when(mWifiInjector.getWifiNetworkSuggestionsManager())
                .thenReturn(mWifiNetworkSuggestionsManager);
        when(mWifiInjector.makeTelephonyManager()).thenReturn(mTelephonyManager);
        when(mWifiInjector.getWifiPulledAtomLogger()).thenReturn(mWifiPulledAtomLogger);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        when(mWifiInjector.getCoexManager()).thenReturn(mCoexManager);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getWifiBlocklistMonitor()).thenReturn(mWifiBlocklistMonitor);
        when(mWifiInjector.getPasspointManager()).thenReturn(mPasspointManager);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.delaySaveToStore()).thenReturn(true);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager);
        when(mClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);
        when(mWifiInjector.getWifiScoreCard()).thenReturn(mWifiScoreCard);
        when(mWifiInjector.getWifiHealthMonitor()).thenReturn(mWifiHealthMonitor);

        mWifiThreadRunner = new WifiThreadRunner(new Handler(mLooper.getLooper()));
        mWifiThreadRunner.setTimeoutsAreErrors(true);
        when(mWifiInjector.getWifiThreadRunner()).thenReturn(mWifiThreadRunner);

        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
        when(mWifiInjector.getWifiScanAlwaysAvailableSettingsCompatibility())
                .thenReturn(mScanAlwaysAvailableSettingsCompatibility);
        when(mWifiInjector.getWifiConnectivityManager()).thenReturn(mWifiConnectivityManager);
        when(mWifiInjector.getWifiDataStall()).thenReturn(mWifiDataStall);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getConnectHelper()).thenReturn(mConnectHelper);
        when(mWifiInjector.getWifiP2pConnection()).thenReturn(mWifiP2pConnection);
        when(mWifiInjector.getSimRequiredNotifier()).thenReturn(mSimRequiredNotifier);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.getAdaptiveConnectivityEnabledSettingObserver())
                .thenReturn(mAdaptiveConnectivityEnabledSettingObserver);
        when(mClientModeManager.syncStartSubscriptionProvisioning(anyInt(),
                any(OsuProvider.class), any(IProvisioningCallback.class))).thenReturn(true);
        // Create an OSU provider that can be provisioned via an open OSU AP
        mOsuProvider = PasspointProvisioningTestUtil.generateOsuProvider(true);
        when(mContext.getOpPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(mContext.getAttributionTag()).thenReturn(TEST_FEATURE_ID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_STACK),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_MANAGED_PROVISIONING),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mScanRequestProxy.startScan(anyInt(), anyString())).thenReturn(true);
        when(mLohsCallback.asBinder()).thenReturn(mock(IBinder.class));
        when(mWifiSettingsConfigStore.get(eq(WIFI_VERBOSE_LOGGING_ENABLED))).thenReturn(true);
        when(mWifiSettingsConfigStore.get(
                eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API)))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(false);
        when(mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_LOCAL_ONLY, ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(Collections.emptyList());
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(anyInt()))
                .thenReturn(true);
        // Defaulting apps to target SDK level that's prior to T. This is needed to test for
        // backward compatibility of API changes.
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
                anyInt())).thenReturn(true);
        when(mWifiInjector.getWifiCarrierInfoManager()).thenReturn(mWifiCarrierInfoManager);
        when(mWifiInjector.getWifiPseudonymManager()).thenReturn(mWifiPseudonymManager);
        when(mWifiInjector.getOpenNetworkNotifier()).thenReturn(mOpenNetworkNotifier);
        when(mClientSoftApCallback.asBinder()).thenReturn(mAppBinder);
        when(mAnotherSoftApCallback.asBinder()).thenReturn(mAnotherAppBinder);
        when(mIOnWifiDriverCountryCodeChangedListener.asBinder()).thenReturn(mAppBinder);
        when(mWifiInjector.getSarManager()).thenReturn(mSarManager);
        mClientModeManagers = Arrays.asList(mClientModeManager, mock(ClientModeManager.class));
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(mClientModeManagers);
        when(mWifiInjector.getSelfRecovery()).thenReturn(mSelfRecovery);
        when(mWifiInjector.getLastCallerInfoManager()).thenReturn(mLastCallerInfoManager);
        when(mUserManager.getUserRestrictions()).thenReturn(mBundle);
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDevicePolicyManager);
        when(mWifiInjector.getHalDeviceManager()).thenReturn(mHalDeviceManager);
        when(mWifiInjector.getWifiDialogManager()).thenReturn(mWifiDialogManager);
        when(mWifiInjector.getSsidTranslator()).thenReturn(mSsidTranslator);
        when(mWifiInjector.getInterfaceConflictManager()).thenReturn(mInterfaceConflictManager);
        when(mWifiInjector.getWifiKeyStore()).thenReturn(mWifiKeyStore);
        when(mWifiInjector.getScoringParams()).thenReturn(mScoringParams);
        when(mWifiInjector.getApplicationQosPolicyRequestHandler())
                .thenReturn(mApplicationQosPolicyRequestHandler);
        when(mWifiInjector.getWifiRoamingModeManager()).thenReturn(mWifiRoamingModeManager);
        when(mLocationManager.getProviders(anyBoolean())).thenReturn(List.of(
                LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER));
        when(mWifiInjector.getWifiRoamingModeManager()).thenReturn(mWifiRoamingModeManager);
        when(mWifiInjector.getTwtManager()).thenReturn(mTwtManager);

        doAnswer(new AnswerWithArguments() {
            public void answer(Runnable onStoppedListener) throws Throwable {
                onStoppedListener.run();
            }
        }).when(mMakeBeforeBreakManager).stopAllSecondaryTransientClientModeManagers(any());

        when(mWifiSettingsConfigStore.get(eq(WIFI_WEP_ALLOWED))).thenReturn(true);
        mWifiServiceImpl = makeWifiServiceImpl();
        mDppCallback = new IDppCallback() {
            @Override
            public void onSuccessConfigReceived(int newNetworkId) throws RemoteException {

            }

            @Override
            public void onSuccess(int status) throws RemoteException {

            }

            @Override
            public void onFailure(int status, String ssid, String channelList, int[] bandList)
                    throws RemoteException {

            }

            @Override
            public void onProgress(int status) throws RemoteException {

            }

            @Override
            public void onBootstrapUriGenerated(String uri) throws RemoteException {

            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        };

        // permission not granted by default
        doThrow(SecurityException.class).when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.NETWORK_SETUP_WIZARD), any());
        mTestSoftApInfo = new SoftApInfo();
        mTestSoftApInfo.setFrequency(TEST_AP_FREQUENCY);
        mTestSoftApInfo.setBandwidth(TEST_AP_BANDWIDTH);
        mTestSoftApInfo.setApInstanceIdentifier(WIFI_IFACE_NAME);
        when(mWifiNative.getChannelsForBand(anyInt())).thenReturn(new int[0]);

        mTestSoftApInfoList = new ArrayList<>();
        mTestSoftApInfoList.add(mTestSoftApInfo);

        mTestSoftApClients = new HashMap<>();
        mTestSoftApClients.put(WIFI_IFACE_NAME, new ArrayList<WifiClient>());
        mTestSoftApInfos = new HashMap<>();
        mTestSoftApInfos.put(WIFI_IFACE_NAME, mTestSoftApInfo);

        mWifiConfig = new WifiConfiguration();
        mWifiConfig.SSID = TEST_SSID;
        mWifiConfig.networkId = TEST_NETWORK_ID;

        setup24GhzSupported();
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

    private void stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(TestLooper looper) {
        looper.dispatchAll();
        looper.stopAutoDispatchAndIgnoreExceptions();
    }

    private WifiServiceImpl makeWifiServiceImpl() {
        WifiServiceImpl wifiServiceImpl =
                new WifiServiceImpl(mContext, mWifiInjector);
        ArgumentCaptor<SoftApCallbackInternal> softApCallbackCaptor =
                ArgumentCaptor.forClass(SoftApCallbackInternal.class);
        verify(mActiveModeWarden, atLeastOnce()).registerSoftApCallback(
                softApCallbackCaptor.capture());
        mStateMachineSoftApCallback = softApCallbackCaptor.getValue();
        ArgumentCaptor<SoftApCallbackInternal> lohsCallbackCaptor =
                ArgumentCaptor.forClass(SoftApCallbackInternal.class);
        mLohsInterfaceName = WIFI_IFACE_NAME;
        verify(mActiveModeWarden, atLeastOnce()).registerLohsCallback(
                lohsCallbackCaptor.capture());
        mLohsApCallback = lohsCallbackCaptor.getValue();
        mLooper.dispatchAll();
        return wifiServiceImpl;
    }

    private WifiServiceImpl makeWifiServiceImplWithMockRunnerWhichTimesOut() {
        WifiThreadRunner mockRunner = mock(WifiThreadRunner.class);
        when(mockRunner.call(any(), any())).then(returnsSecondArg());
        when(mockRunner.call(any(), any(int.class))).then(returnsSecondArg());
        when(mockRunner.call(any(), any(boolean.class))).then(returnsSecondArg());
        when(mockRunner.post(any())).thenReturn(false);

        when(mWifiInjector.getWifiThreadRunner()).thenReturn(mockRunner);
        // Reset mWifiCountryCode to avoid verify failure in makeWifiServiceImpl.
        reset(mWifiCountryCode);
        return makeWifiServiceImpl();
    }

    /**
     * Test that REMOVE_NETWORK returns failure to public API when WifiConfigManager returns
     * failure.
     */
    @Test
    public void testRemoveNetworkFailureAppBelowQSdk() {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID, null);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mWifiConfigManager.removeNetwork(anyInt(), anyInt(), anyString())).thenReturn(false);

        mLooper.startAutoDispatch();
        boolean succeeded = mWifiServiceImpl.removeNetwork(0, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertFalse(succeeded);
    }

    /**
     * Ensure WifiMetrics.dump() is the only dump called when 'dumpsys wifi WifiMetricsProto' is
     * called. This is required to support simple metrics collection via dumpsys
     */
    @Test
    public void testWifiMetricsDump() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        mLooper.startAutoDispatch();
        mWifiServiceImpl.dump(new FileDescriptor(), new PrintWriter(new StringWriter()),
                new String[]{mWifiMetrics.PROTO_DUMP_ARG});
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mWifiMetrics).setNonPersistentMacRandomizationForceEnabled(anyBoolean());
        verify(mWifiMetrics).setIsScanningAlwaysEnabled(anyBoolean());
        verify(mWifiMetrics).setVerboseLoggingEnabled(anyBoolean());
        verify(mWifiMetrics)
                .dump(any(FileDescriptor.class), any(PrintWriter.class), any(String[].class));
        verify(mClientModeManager, never())
                .dump(any(FileDescriptor.class), any(PrintWriter.class), any(String[].class));
    }

    /**
     * Ensure WifiServiceImpl.dump() doesn't throw an NPE when executed with null args
     */
    @Test
    public void testDumpNullArgs() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        mLooper.startAutoDispatch();
        mWifiServiceImpl.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mWifiDiagnostics).captureBugReportData(
                WifiDiagnostics.REPORT_REASON_USER_ACTION);
        verify(mWifiDiagnostics).dump(any(), any(), any());
        verify(mPasspointNetworkNominateHelper).dump(any());
    }

    @Test
    public void testWifiShellCommandIgnoredBeforeBoot() {
        // verify shell command can't be called before boot complete
        ParcelFileDescriptor mockDescriptor = mock(ParcelFileDescriptor.class);
        assertEquals(-1, mWifiServiceImpl.handleShellCommand(mockDescriptor,
                mockDescriptor, mockDescriptor, new String[0]));

        // verify shell command can now be called after boot complete
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        assertEquals(0, mWifiServiceImpl.handleShellCommand(mockDescriptor,
                mockDescriptor, mockDescriptor, new String[0]));
    }

    /**
     * Verify that metrics is incremented correctly for Privileged Apps.
     */
    @Test
    public void testSetWifiEnabledMetricsPrivilegedApp() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(anyBoolean())).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mWakeupController.isUsable()).thenReturn(false);

        InOrder inorder = inOrder(mWifiMetrics);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        mLooper.dispatchAll();
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setAutoJoinEnabledExternal(true, false);
        inorder.verify(mWifiMetrics).logUserActionEvent(UserActionEvent.EVENT_TOGGLE_WIFI_ON);
        inorder.verify(mWifiMetrics).incrementNumWifiToggles(eq(true), eq(true));
        inorder.verify(mWifiMetrics).reportWifiStateChanged(true, false, false);
        inorder.verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_TOGGLE_WIFI_OFF),
                anyInt());
        inorder.verify(mWifiMetrics).incrementNumWifiToggles(eq(true), eq(false));
        inorder.verify(mWifiMetrics).reportWifiStateChanged(false, false, false);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_WIFI_ENABLED), anyInt(),
                anyInt(), anyInt(), anyString(), eq(false));
    }

    /**
     * Verify that metrics is incremented correctly for normal Apps targeting pre-Q.
     */
    @Test
    public void testSetWifiEnabledMetricsNormalAppBelowQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(anyBoolean())).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mWakeupController.isUsable()).thenReturn(true);

        InOrder inorder = inOrder(mWifiMetrics);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        inorder.verify(mWifiMetrics).incrementNumWifiToggles(eq(false), eq(true));
        inorder.verify(mWifiMetrics).reportWifiStateChanged(true, true, false);
        inorder.verify(mWifiMetrics).incrementNumWifiToggles(eq(false), eq(false));
        inorder.verify(mWifiMetrics).reportWifiStateChanged(false, true, false);
    }

    /**
     * Verify that metrics is not incremented by apps targeting Q SDK.
     */
    @Test
    public void testSetWifiEnabledMetricsNormalAppTargetingQSdkNoIncrement() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);

        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        verify(mWifiMetrics, never()).incrementNumWifiToggles(anyBoolean(), anyBoolean());
        verify(mWifiMetrics, never()).reportWifiStateChanged(anyBoolean(), anyBoolean(),
                anyBoolean());
    }

    /**
     * Verify that wifi can be enabled by a caller with NETWORK_SETTINGS permission.
     */
    @Test
    public void testSetWifiEnabledSuccessWithNetworkSettingsPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify that wifi can be enabled by a caller with NETWORK_MANAGED_PROVISIONING permission.
     */
    @Test
    public void testSetWifiEnabledSuccessWithNetworkManagedProvisioningPermission()
            throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_MANAGED_PROVISIONING),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify that wifi can be enabled by the DO apps targeting Q SDK.
     */
    @Test
    public void testSetWifiEnabledSuccessForDOAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isDeviceOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));

        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify that wifi can be enabled by the system apps targeting Q SDK.
     */
    @Test
    public void testSetWifiEnabledSuccessForSystemAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));

        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify that wifi can be enabled by the apps targeting pre-Q SDK.
     */
    @Test
    public void testSetWifiEnabledSuccessForAppsTargetingBelowQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));

        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify that wifi cannot be enabled by the apps targeting Q SDK.
     */
    @Test
    public void testSetWifiEnabledFailureForAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));

        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify a SecurityException is thrown if OPSTR_CHANGE_WIFI_STATE is disabled for the app.
     */
    @Test
    public void testSetWifiEnableAppOpsRejected() throws Exception {
        doThrow(new SecurityException()).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        try {
            mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true);
            fail();
        } catch (SecurityException e) {

        }
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify a SecurityException is thrown if OP_CHANGE_WIFI_STATE is set to MODE_IGNORED
     * for the app.
     */
    @Test // No exception expected, but the operation should not be done
    public void testSetWifiEnableAppOpsIgnored() throws Exception {
        doReturn(AppOpsManager.MODE_IGNORED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);

        mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true);
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify that a call from an app with the NETWORK_SETTINGS permission can enable wifi if we
     * are in airplane mode.
     */
    @Test
    public void testSetWifiEnabledFromNetworkSettingsHolderWhenInAirplaneMode() throws Exception {
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertTrue(mWifiServiceImpl.setWifiEnabled(SYSUI_PACKAGE_NAME, true));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), SYSUI_PACKAGE_NAME)));
    }

    /**
     * Verify that a caller without the NETWORK_SETTINGS permission can't enable wifi
     * if we are in airplane mode.
     */
    @Test
    public void testSetWifiEnabledFromAppFailsWhenInAirplaneMode() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify that a user (NETWORK_SETTINGS) cannot enable wifi if DISALLOW_CHANGE_WIFI_STATE
     * user restriction is set.
     */
    @Test
    public void testSetWifiEnabledFromUserFailsWhenUserRestrictionSet() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CHANGE_WIFI_STATE),
                any())).thenReturn(true);

        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify that apps targeting pre-Q SDK cannot enable wifi if DISALLOW_CHANGE_WIFI_STATE
     * user restriction is set.
     */
    @Test
    public void testSetWifiEnabledFromAppTargetingBelowQSdkFailsWhenUserRestrictionSet()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CHANGE_WIFI_STATE),
                any())).thenReturn(true);

        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify that wifi can be enabled by the DO apps even when DISALLOW_CHANGE_WIFI_STATE
     * user restriction is set.
     */
    @Test
    public void testSetWifiEnabledSuccessForDOAppsWhenUserRestrictionSet() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isDeviceOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CHANGE_WIFI_STATE),
                any())).thenReturn(true);

        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify that a dialog is shown for third-party apps targeting pre-Q SDK enabling Wi-Fi.
     */
    @Test
    public void testSetWifiEnabledDialogForThirdPartyAppsTargetingBelowQSdk() throws Exception {
        when(mResources.getBoolean(
                R.bool.config_showConfirmationDialogForThirdPartyAppsEnablingWifi))
                .thenReturn(true);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        when(mPackageManager.getApplicationInfo(any(), anyInt())).thenReturn(applicationInfo);
        String appName = "appName";
        when(applicationInfo.loadLabel(any())).thenReturn(appName);
        String title = "appName wants to enable Wi-Fi.";
        String message = "Enable Wi-Fi?";
        String positiveButtonText = "Yes";
        String negativeButtonText = "No";
        when(mResources.getString(R.string.wifi_enable_request_dialog_title, appName))
                .thenReturn(title);
        when(mResources.getString(R.string.wifi_enable_request_dialog_message)).thenReturn(message);
        when(mResources.getString(R.string.wifi_enable_request_dialog_positive_button))
                .thenReturn(positiveButtonText);
        when(mResources.getString(R.string.wifi_enable_request_dialog_negative_button))
                .thenReturn(negativeButtonText);

        // Launch dialog
        WifiDialogManager.DialogHandle dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dialogHandle);
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden, times(0)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        mLooper.dispatchAll();
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                eq(title),
                eq(message),
                eq(positiveButtonText),
                eq(negativeButtonText),
                eq(null),
                callbackCaptor.capture(),
                any());
        verify(dialogHandle).launchDialog();

        // Verify extra call to setWifiEnabled won't launch a new dialog
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden, times(0)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        mLooper.dispatchAll();
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                eq(title),
                eq(message),
                eq(positiveButtonText),
                eq(negativeButtonText),
                eq(null),
                callbackCaptor.capture(),
                any());

        // Verify the negative reply does not enable wifi
        callbackCaptor.getValue().onNegativeButtonClicked();
        verify(mActiveModeWarden, times(0)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));

        // Verify the cancel reply does not enable wifi.
        dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dialogHandle);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        mLooper.dispatchAll();
        verify(mActiveModeWarden, times(0)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager, times(2)).createSimpleDialog(
                eq(title),
                eq(message),
                eq(positiveButtonText),
                eq(negativeButtonText),
                eq(null),
                callbackCaptor.capture(),
                any());
        verify(dialogHandle).launchDialog();
        callbackCaptor.getValue().onCancelled();
        verify(mActiveModeWarden, times(0)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));

        // Verify the positive reply will enable wifi.
        dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dialogHandle);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        mLooper.dispatchAll();
        verify(mActiveModeWarden, times(0)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager, times(3)).createSimpleDialog(
                eq(title),
                eq(message),
                eq(positiveButtonText),
                eq(negativeButtonText),
                eq(null),
                callbackCaptor.capture(),
                any());
        verify(dialogHandle).launchDialog();
        callbackCaptor.getValue().onPositiveButtonClicked();
        verify(mActiveModeWarden, times(1)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));

        // Verify disabling wifi works without dialog.
        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dialogHandle);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        mLooper.dispatchAll();
        verify(mActiveModeWarden, times(2)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(dialogHandle, never()).launchDialog();

        // Verify wifi becoming enabled will dismiss any outstanding dialogs.
        dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dialogHandle);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        mLooper.dispatchAll();
        verify(mActiveModeWarden, times(2)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager, times(4)).createSimpleDialog(
                eq(title),
                eq(message),
                eq(positiveButtonText),
                eq(negativeButtonText),
                eq(null),
                callbackCaptor.capture(),
                any());
        verify(dialogHandle).launchDialog();
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        // Enabled by privileged app
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        mLooper.dispatchAll();
        verify(mActiveModeWarden, times(3)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(dialogHandle).dismissDialog();

        // Verify wifi already enabled will not trigger dialog.
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(false);
        dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dialogHandle);
        when(mActiveModeWarden.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        mLooper.dispatchAll();
        verify(mActiveModeWarden, times(3)).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(dialogHandle, never()).launchDialog();
    }

    /**
     * Verify that no dialog is shown for non-third-party apps targeting pre-Q SDK enabling Wi-Fi.
     */
    @Test
    public void testSetWifiEnabledNoDialogForNonThirdPartyAppsTargetingBelowQSdk() {
        when(mResources.getBoolean(
                R.bool.config_showConfirmationDialogForThirdPartyAppsEnablingWifi))
                .thenReturn(true);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);

        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Helper to verify registering for state changes.
     */
    private void verifyApRegistration() {
        assertNotNull(mLohsApCallback);
    }

    /**
     * Helper to emulate local-only hotspot state changes.
     *
     * Must call verifyApRegistration first.
     */
    private void changeLohsState(int apState, int previousState, int error) {
        // TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
        //        apState, previousState, error, WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLohsApCallback.onStateChanged(new SoftApState(apState, error,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME));
    }

    /**
     * Verify that a call from an app with the NETWORK_SETTINGS permission can enable wifi if we
     * are in softap mode.
     */
    @Test
    public void testSetWifiEnabledFromNetworkSettingsHolderWhenApEnabled() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));

        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(SYSUI_PACKAGE_NAME, true));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), SYSUI_PACKAGE_NAME)));
    }

    /**
     * Verify that a call from an app cannot enable wifi if we are in softap mode for Pre-S
     */
    @Test
    public void testSetWifiEnabledFromAppFailsWhenApEnabledForPreS() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));

        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        if (!SdkLevel.isAtLeastS()) {
            assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
            verify(mSettingsStore, never()).handleWifiToggled(anyBoolean());
            verify(mActiveModeWarden, never()).wifiToggled(any());
        } else {
            assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
            verify(mActiveModeWarden).wifiToggled(
                    eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        }
    }


    /**
     * Verify that the CMD_TOGGLE_WIFI message won't be sent if wifi is already on.
     */
    @Test
    public void testSetWifiEnabledNoToggle() throws Exception {
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(false);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    /**
     * Verify a SecurityException is thrown if a caller does not have the CHANGE_WIFI_STATE
     * permission to toggle wifi.
     */
    @Test
    public void testSetWifiEnableWithoutChangeWifiStatePermission() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                                                eq("WifiService"));
        try {
            mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true);
            fail();
        } catch (SecurityException e) {
        }
    }

    /**
     * Verify that wifi can be disabled by a caller with NETWORK_SETTINGS permission.
     */
    @Test
    public void testSetWifiDisabledSuccessWithNetworkSettingsPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(mInterfaceConflictManager).reset();
    }

    /**
     * Verify that wifi can be disabled by a caller with NETWORK_MANAGED_PROVISIONING permission.
     */
    @Test
    public void testSetWifiDisabledSuccessWithNetworkManagedProvisioningPermission()
            throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_MANAGED_PROVISIONING),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(mInterfaceConflictManager).reset();
    }

    /**
     * Verify that wifi can be disabled by the PO apps targeting Q SDK.
     */
    @Test
    public void testSetWifiDisabledSuccessForPOAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));

        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(mInterfaceConflictManager).reset();
    }

    /**
     * Verify that wifi can be disabled by the system apps targeting Q SDK.
     */
    @Test
    public void testSetWifiDisabledSuccessForSystemAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));

        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(mInterfaceConflictManager).reset();
    }


    /**
     * Verify that wifi can be disabled by the apps targeting pre-Q SDK.
     */
    @Test
    public void testSetWifiDisabledSuccessForAppsTargetingBelowQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));

        verify(mActiveModeWarden).wifiToggled(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        verify(mInterfaceConflictManager).reset();
    }

    /**
     * Verify that wifi cannot be disabled by the apps targeting Q SDK.
     */
    @Test
    public void testSetWifiDisabledFailureForAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(false);

        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));

        verify(mActiveModeWarden, never()).wifiToggled(any());
        verify(mInterfaceConflictManager, never()).reset();
    }

    /**
     * Verify that CMD_TOGGLE_WIFI message won't be sent if wifi is already off.
     */
    @Test
    public void testSetWifiDisabledNoToggle() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        verify(mActiveModeWarden, never()).wifiToggled(any());
        verify(mInterfaceConflictManager, never()).reset();
    }

    /**
     * Verify a SecurityException is thrown if a caller does not have the CHANGE_WIFI_STATE
     * permission to toggle wifi.
     */
    @Test
    public void testSetWifiDisabledWithoutChangeWifiStatePermission() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false);
            fail();
        } catch (SecurityException e) { }
    }

    /**
     * Verify that the restartWifiSubsystem fails w/o the NETWORK_AIRPLANE_MODE permission.
     */
    @Test public void testRestartWifiSubsystemWithoutPermission() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(android.Manifest.permission.RESTART_WIFI_SUBSYSTEM), eq("WifiService"));

        try {
            mWifiServiceImpl.restartWifiSubsystem();
            fail("restartWifiSubsystem should fail w/o the APM permission!");
        } catch (SecurityException e) {
            // empty clause
        }
    }

    /**
     * Verify that a call to registerSubsystemRestartCallback throws a SecurityException if the
     * caller does not have the ACCESS_WIFI_STATE permission.
     */
    @Test
    public void testRegisterSubsystemRestartThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.registerSubsystemRestartCallback(mSubsystemRestartCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify that a call to unregisterSubsystemRestartCallback throws a SecurityException if the
     * caller does not have the ACCESS_WIFI_STATE permission.
     */
    @Test
    public void testUnregisterSubsystemRestartThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.unregisterSubsystemRestartCallback(mSubsystemRestartCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }


    /**
     * Test register and unregister subsystem restart callback will go to ActiveModeManager;
     */
    @Test
    public void testRegisterUnregisterSubsystemRestartCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mCoexCallback.asBinder()).thenReturn(mAppBinder);
        mWifiServiceImpl.registerSubsystemRestartCallback(mSubsystemRestartCallback);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).registerSubsystemRestartCallback(mSubsystemRestartCallback);
        mWifiServiceImpl.unregisterSubsystemRestartCallback(mSubsystemRestartCallback);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).unregisterSubsystemRestartCallback(mSubsystemRestartCallback);
    }

    @Test
    public void testAddWifiNetworkStateChangedListener() throws Exception {
        IWifiNetworkStateChangedListener testListener =
                mock(IWifiNetworkStateChangedListener.class);

        // Test success case
        mWifiServiceImpl.addWifiNetworkStateChangedListener(testListener);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).addWifiNetworkStateChangedListener(testListener);

        // Expect exception for null listener
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.addWifiNetworkStateChangedListener(null));

        // Expect exception when caller has no permission
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.addWifiNetworkStateChangedListener(testListener));
        verify(mActiveModeWarden).addWifiNetworkStateChangedListener(testListener);
    }

    /**
     * Verify that the restartWifiSubsystem succeeds and passes correct parameters.
     */
    @Test
    public void testRestartWifiSubsystem() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mContext.checkPermission(eq(android.Manifest.permission.RESTART_WIFI_SUBSYSTEM),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mWifiServiceImpl.restartWifiSubsystem();
        mLooper.dispatchAll();
        verify(mSelfRecovery).trigger(eq(REASON_API_CALL));
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_RESTART_WIFI_SUB_SYSTEM),
                anyInt());
    }

    /**
     * Ensure unpermitted callers cannot write the SoftApConfiguration.
     */
    @Test
    public void testSetWifiApConfigurationNotSavedWithoutPermission() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);
        WifiConfiguration apConfig = new WifiConfiguration();
        try {
            mWifiServiceImpl.setWifiApConfiguration(apConfig, TEST_PACKAGE_NAME);
            fail("Expected SecurityException");
        } catch (SecurityException e) { }
    }

    /**
     * Ensure softap config is written when the caller has the correct permission.
     */
    @Test
    public void testSetWifiApConfigurationSuccess() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        WifiConfiguration wifiApConfig = createValidWifiApConfiguration();

        assertTrue(mWifiServiceImpl.setWifiApConfiguration(wifiApConfig, TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiApConfigStore).setApConfiguration(eq(
                ApConfigUtil.fromWifiConfiguration(wifiApConfig)));
    }

    /**
     * Ensure that a null config does not overwrite the saved ap config.
     */
    @Test
    public void testSetWifiApConfigurationNullConfigNotSaved() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        assertFalse(mWifiServiceImpl.setWifiApConfiguration(null, TEST_PACKAGE_NAME));
        verify(mWifiApConfigStore, never()).setApConfiguration(isNull(SoftApConfiguration.class));
    }

    /**
     * Ensure that an invalid config does not overwrite the saved ap config.
     */
    @Test
    public void testSetWifiApConfigurationWithInvalidConfigNotSaved() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        assertFalse(mWifiServiceImpl.setWifiApConfiguration(new WifiConfiguration(),
                                                            TEST_PACKAGE_NAME));
        verify(mWifiApConfigStore, never()).setApConfiguration(any());
    }

    /**
     * Ensure unpermitted callers cannot write the SoftApConfiguration.
     */
    @Test
    public void testSetSoftApConfigurationNotSavedWithoutPermission() throws Exception {
        SoftApConfiguration apConfig = createValidSoftApConfiguration();
        try {
            mWifiServiceImpl.setSoftApConfiguration(apConfig, TEST_PACKAGE_NAME);
            fail("Expected SecurityException");
        } catch (SecurityException e) { }
    }

    /**
     * Ensure softap config is written when the caller has the correct permission.
     */
    @Test
    public void testSetSoftApConfigurationSuccessWithSettingPermission() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        SoftApConfiguration apConfig = createValidSoftApConfiguration();

        assertTrue(mWifiServiceImpl.setSoftApConfiguration(apConfig, TEST_PACKAGE_NAME));
        verify(mWifiApConfigStore).setApConfiguration(eq(apConfig));
        verify(mActiveModeWarden).updateSoftApConfiguration(apConfig);
        verify(mWifiPermissionsUtil).checkNetworkSettingsPermission(anyInt());
    }

    /**
     * Ensure softap config is written when the caller has the correct permission.
     */
    @Test
    public void testSetSoftApConfigurationSuccessWithOverridePermission() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        SoftApConfiguration apConfig = createValidSoftApConfiguration();

        assertTrue(mWifiServiceImpl.setSoftApConfiguration(apConfig, TEST_PACKAGE_NAME));
        verify(mWifiApConfigStore).setApConfiguration(eq(apConfig));
        verify(mActiveModeWarden).updateSoftApConfiguration(apConfig);
        verify(mWifiPermissionsUtil).checkConfigOverridePermission(anyInt());
    }

    /**
     * Ensure that a null config does not overwrite the saved ap config.
     */
    @Test
    public void testSetSoftApConfigurationNullConfigNotSaved() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        assertFalse(mWifiServiceImpl.setSoftApConfiguration(null, TEST_PACKAGE_NAME));
        verify(mWifiApConfigStore, never()).setApConfiguration(isNull(SoftApConfiguration.class));
        verify(mActiveModeWarden, never()).updateSoftApConfiguration(any());
        verify(mWifiPermissionsUtil).checkConfigOverridePermission(anyInt());
    }

    /**
     * Ensure that an invalid config does not overwrite the saved ap config.
     */
    @Test
    public void testSetSoftApConfigurationWithInvalidConfigNotSaved() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        assertFalse(mWifiServiceImpl.setSoftApConfiguration(
                new SoftApConfiguration.Builder().build(), TEST_PACKAGE_NAME));
        verify(mWifiApConfigStore, never()).setApConfiguration(any());
        verify(mWifiPermissionsUtil).checkConfigOverridePermission(anyInt());
    }

    /**
     * Ensure unpermitted callers are not able to retrieve the softap config.
     */
    @Test
    public void testGetSoftApConfigurationNotReturnedWithoutPermission() throws Exception {
        try {
            mWifiServiceImpl.getSoftApConfiguration();
            fail("Expected a SecurityException");
        } catch (SecurityException e) {
        }
    }

    /**
     * Ensure permitted callers are able to retrieve the softap config.
     */
    @Test
    public void testGetSoftApConfigurationSuccess() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        SoftApConfiguration apConfig = createValidSoftApConfiguration();
        when(mWifiApConfigStore.getApConfiguration()).thenReturn(apConfig);

        mLooper.startAutoDispatch();
        assertThat(apConfig).isEqualTo(mWifiServiceImpl.getSoftApConfiguration());

        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Ensure unpermitted callers are not able to retrieve the softap config.
     */
    @Test
    public void testGetWifiApConfigurationNotReturnedWithoutPermission() throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);
        try {
            mWifiServiceImpl.getWifiApConfiguration();
            fail("Expected a SecurityException");
        } catch (SecurityException e) {
        }
    }

    /**
     * Ensure permitted callers are able to retrieve the softap config.
     */
    @Test
    public void testGetWifiApConfigurationSuccess() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        SoftApConfiguration apConfig = new SoftApConfiguration.Builder().build();
        when(mWifiApConfigStore.getApConfiguration()).thenReturn(apConfig);

        mLooper.startAutoDispatch();
        WifiConfigurationTestUtil.assertConfigurationEqualForSoftAp(
                apConfig.toWifiConfiguration(),
                mWifiServiceImpl.getWifiApConfiguration());

        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Ensure we return the proper variable for the softap state after getting an AP state change
     * broadcast.
     */
    @Test
    public void testGetWifiApEnabled() throws Exception {
        // ap should be disabled when wifi hasn't been started
        assertEquals(WifiManager.WIFI_AP_STATE_DISABLED, mWifiServiceImpl.getWifiApEnabledState());

        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        // ap should be disabled initially
        assertEquals(WifiManager.WIFI_AP_STATE_DISABLED, mWifiServiceImpl.getWifiApEnabledState());

        // send an ap state change to verify WifiServiceImpl is updated
        verifyApRegistration();
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(
                        WIFI_AP_STATE_FAILED, SAP_START_FAILURE_GENERAL,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));
        mLooper.dispatchAll();

        assertEquals(WifiManager.WIFI_AP_STATE_FAILED, mWifiServiceImpl.getWifiApEnabledState());
    }

    /**
     * Ensure we do not allow unpermitted callers to get the wifi ap state.
     */
    @Test
    public void testGetWifiApEnabledPermissionDenied() {
        // we should not be able to get the state
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE),
                                                eq("WifiService"));

        try {
            mWifiServiceImpl.getWifiApEnabledState();
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Make sure we do start WifiController (wifi disabled) if the device is already decrypted.
     */
    @Test
    public void testWifiControllerStartsWhenDeviceBootsWithWifiDisabled() {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mWifiConfigManager).loadFromStore();
        verify(mActiveModeWarden).start();
        verify(mActiveModeWarden, never()).wifiToggled(any());
    }

    @Test
    public void testWifiVerboseLoggingInitialization() {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(eq(WIFI_VERBOSE_LOGGING_ENABLED))).thenReturn(true);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mWifiConfigManager).loadFromStore();
        verify(mActiveModeWarden).enableVerboseLogging(true);
        // show key mode is always disabled at the beginning.
        verify(mWifiGlobals).setVerboseLoggingLevel(eq(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED));
        verify(mActiveModeWarden).start();
        assertTrue(mWifiThreadRunner.mVerboseLoggingEnabled);
    }

    /**
     * Make sure we do start WifiController (wifi enabled) if the device is already decrypted.
     */
    @Test
    public void testWifiFullyStartsWhenDeviceBootsWithWifiEnabled() {
        when(mSettingsStore.handleWifiToggled(true)).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_DISABLED);
        when(mContext.getPackageName()).thenReturn(ANDROID_SYSTEM_PACKAGE);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mWifiConfigManager).loadFromStore();
        verify(mActiveModeWarden).start();
    }

    @Test
    public void testSetPnoScanEnabled() {
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setPnoScanEnabled(false, false, TEST_PACKAGE_NAME));

        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                    .thenReturn(true);
        } else {
            when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        }
        mWifiServiceImpl.setPnoScanEnabled(false, false, TEST_PACKAGE_NAME);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SET_PNO_SCAN_ENABLED),
                anyInt(), anyInt(), anyInt(), eq(TEST_PACKAGE_NAME), eq(false));
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setPnoScanEnabledByFramework(false, false);
    }

    @Test
    public void testSetPulledAtomCallbacks() {
        mWifiServiceImpl.checkAndStartWifi();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mWifiPulledAtomLogger).setPullAtomCallback(WifiStatsLog.WIFI_MODULE_INFO);
        verify(mWifiPulledAtomLogger).setPullAtomCallback(WifiStatsLog.WIFI_SETTING_INFO);
        verify(mWifiPulledAtomLogger).setPullAtomCallback(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO);
        verify(mWifiPulledAtomLogger).setPullAtomCallback(
                WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO);
    }

    /**
     * Verify that the setCoexUnsafeChannels calls the corresponding CoexManager API if
     * the config_wifiDefaultCoexAlgorithmEnabled is false.
     */
    @Test
    public void testSetCoexUnsafeChannelsDefaultAlgorithmDisabled() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mResources.getBoolean(R.bool.config_wifiDefaultCoexAlgorithmEnabled))
                .thenReturn(false);
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.addAll(Arrays.asList(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6),
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36)));
        int coexRestrictions = COEX_RESTRICTION_SOFTAP
                & COEX_RESTRICTION_WIFI_AWARE & COEX_RESTRICTION_WIFI_DIRECT;
        mWifiServiceImpl.setCoexUnsafeChannels(unsafeChannels, coexRestrictions);
        mLooper.dispatchAll();
        verify(mCoexManager, times(1)).setCoexUnsafeChannels(any(), anyInt());
    }

    /**
     * Verify that the setCoexUnsafeChannels does not call the corresponding CoexManager API if
     * the config_wifiDefaultCoexAlgorithmEnabled is true.
     */
    @Test
    public void testSetCoexUnsafeChannelsDefaultAlgorithmEnabled() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mResources.getBoolean(R.bool.config_wifiDefaultCoexAlgorithmEnabled))
                .thenReturn(true);
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        int coexRestrictions = COEX_RESTRICTION_SOFTAP
                & COEX_RESTRICTION_WIFI_AWARE & COEX_RESTRICTION_WIFI_DIRECT;
        mWifiServiceImpl.setCoexUnsafeChannels(unsafeChannels, coexRestrictions);
        mLooper.dispatchAll();
        verify(mCoexManager, never()).setCoexUnsafeChannels(any(), anyInt());
    }

    /**
     * Verify that setCoexUnsafeChannels throws an IllegalArgumentException if passed a null set.
     */
    @Test
    public void testSetCoexUnsafeChannelsNullSet() {
        assumeTrue(SdkLevel.isAtLeastS());
        try {
            mWifiServiceImpl.setCoexUnsafeChannels(null, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test register and unregister callback will go to CoexManager;
     */
    @Test
    public void testRegisterUnregisterCoexCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mCoexCallback.asBinder()).thenReturn(mAppBinder);
        mWifiServiceImpl.registerCoexCallback(mCoexCallback);
        mLooper.dispatchAll();
        verify(mCoexManager).registerRemoteCoexCallback(mCoexCallback);
        mWifiServiceImpl.unregisterCoexCallback(mCoexCallback);
        mLooper.dispatchAll();
        verify(mCoexManager).unregisterRemoteCoexCallback(mCoexCallback);
    }

    /**
     * Verify that a call to setCoexUnsafeChannels throws a SecurityException if the caller does
     * not have the WIFI_UPDATE_COEX_UNSAFE_CHANNELS permission.
     */
    @Test
    public void testSetCoexUnsafeChannelsThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(WIFI_UPDATE_COEX_UNSAFE_CHANNELS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setCoexUnsafeChannels(new ArrayList<>(), 0);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify that a call to registerCoexCallback throws a SecurityException if the caller does
     * not have the WIFI_ACCESS_COEX_UNSAFE_CHANNELS permission.
     */
    @Test
    public void testRegisterCoexCallbackThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(WIFI_ACCESS_COEX_UNSAFE_CHANNELS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.registerCoexCallback(mCoexCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify that a call to unregisterCoexCallback throws a SecurityException if the caller does
     * not have the WIFI_ACCESS_COEX_UNSAFE_CHANNELS permission.
     */
    @Test
    public void testUnregisterCoexCallbackThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(WIFI_ACCESS_COEX_UNSAFE_CHANNELS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.unregisterCoexCallback(mCoexCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify caller with proper permission can call startSoftAp.
     */
    @Test
    public void testStartSoftApWithPermissionsAndNullConfig() {
        mLooper.startAutoDispatch();
        boolean result = mWifiServiceImpl.startSoftAp(null, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertNull(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify caller with proper permissions but an invalid config does not start softap.
     */
    @Test
    public void testStartSoftApWithPermissionsAndInvalidConfig() {
        mLooper.startAutoDispatch();
        boolean result = mWifiServiceImpl.startSoftAp(mApConfig, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /**
     * Verify caller with proper permission and valid config does start softap.
     */
    @Test
    public void testStartSoftApWithPermissionsAndValidConfig() {
        WifiConfiguration config = createValidWifiApConfiguration();
        mLooper.startAutoDispatch();
        boolean result = mWifiServiceImpl.startSoftAp(config, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(
                mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        WifiConfigurationTestUtil.assertConfigurationEqualForSoftAp(
                config,
                mSoftApModeConfigCaptor.getValue().getSoftApConfiguration().toWifiConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SOFT_AP), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify a SecurityException is thrown when a caller without the correct permission attempts to
     * start softap.
     */
    @Test(expected = SecurityException.class)
    public void testStartSoftApWithoutPermissionThrowsException() throws Exception {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
        mLooper.startAutoDispatch();
        mWifiServiceImpl.startSoftAp(null, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify that startSoftAP() succeeds if the caller does not have the NETWORK_STACK permission
     * but does have the MAINLINE_NETWORK_STACK permission.
     */
    @Test
    public void testStartSoftApWithoutNetworkStackWithMainlineNetworkStackSucceeds() {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        WifiConfiguration config = createValidWifiApConfiguration();
        mLooper.startAutoDispatch();
        boolean result = mWifiServiceImpl.startSoftAp(config, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        WifiConfigurationTestUtil.assertConfigurationEqualForSoftAp(
                config,
                mSoftApModeConfigCaptor.getValue().getSoftApConfiguration().toWifiConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
        verify(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
    }

    /**
     * Verify that startSoftAp() with valid config succeeds after a failed call
     */
    @Test
    public void testStartSoftApWithValidConfigSucceedsAfterFailure() {
        // First initiate a failed call
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startSoftAp(mApConfig, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());

        // Next attempt a valid config
        WifiConfiguration config = createValidWifiApConfiguration();
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.startSoftAp(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        WifiConfigurationTestUtil.assertConfigurationEqualForSoftAp(
                config,
                mSoftApModeConfigCaptor.getValue().getSoftApConfiguration().toWifiConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify caller with proper permission can call startTetheredHotspot.
     */
    @Test
    public void testStartTetheredHotspotWithPermissionsAndNullConfig() {
        boolean result = mWifiServiceImpl.startTetheredHotspot(null, TEST_PACKAGE_NAME);
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertNull(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_TETHERED_HOTSPOT), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify a SecurityException is thrown when a caller without the correct permission attempts to
     * call startTetheredHotspotRequest().
     */
    @Test(expected = SecurityException.class)
    public void testStartTetheredHotspotRequestWithoutPermissionThrowsException() throws Exception {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
        mLooper.startAutoDispatch();
        TetheringManager.TetheringRequest request = new TetheringManager.TetheringRequest.Builder(
                TetheringManager.TETHERING_WIFI).build();
        mWifiServiceImpl.startTetheredHotspotRequest(request, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify that startTetheredHotspotRequest() succeeds if the caller does not have the
     * NETWORK_STACK permission but does have the MAINLINE_NETWORK_STACK permission.
     */
    @Test
    public void testStartTetheredHotspotRequestNetworkStackWithMainlineNetworkStackSucceeds() {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        mLooper.startAutoDispatch();
        TetheringManager.TetheringRequest request = new TetheringManager.TetheringRequest.Builder(
                TetheringManager.TETHERING_WIFI).build();
        boolean result = mWifiServiceImpl.startTetheredHotspotRequest(request, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration()).isNull();
        assertThat(mSoftApModeConfigCaptor.getValue().getTetheringRequest()).isEqualTo(request);
        verify(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
    }

    /**
     * Verify startTetheredHotspot fails with a null request.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStartTetheredHotspotNullRequestFails() {
        mLooper.startAutoDispatch();
        mWifiServiceImpl.startTetheredHotspotRequest(null, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify caller with proper permission can call startTetheredHotspot from a TetheringRequest.
     */
    @Test
    public void testStartTetheredHotspotRequestWithPermissions() {
        TetheringManager.TetheringRequest request = new TetheringManager.TetheringRequest.Builder(
                TetheringManager.TETHERING_WIFI).build();
        boolean result = mWifiServiceImpl.startTetheredHotspotRequest(request, TEST_PACKAGE_NAME);
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertNull(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertThat(mSoftApModeConfigCaptor.getValue().getTetheringRequest()).isEqualTo(request);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_TETHERED_HOTSPOT), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify caller with proper permissions but an invalid config does not start softap.
     */
    @Test
    public void testStartTetheredHotspotWithPermissionsAndInvalidConfig() {
        boolean result = mWifiServiceImpl.startTetheredHotspot(
                new SoftApConfiguration.Builder().build(), TEST_PACKAGE_NAME);
        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify caller with proper permission and valid config does start softap.
     */
    @Test
    public void testStartTetheredHotspotWithPermissionsAndValidConfig() {
        SoftApConfiguration config = createValidSoftApConfiguration();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify that while the DISALLOW_WIFI_TETHERING user restriction is set, softap does not start.
     */
    @Test
    public void testStartTetheredHotspotWithUserRestriction() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mBundle.getBoolean(UserManager.DISALLOW_WIFI_TETHERING)).thenReturn(true);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        SoftApConfiguration config = createValidSoftApConfiguration();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify that when the DISALLOW_WIFI_TETHERING user restriction is set, softap stops.
     */
    @Test
    public void testTetheredHotspotDisabledWhenUserRestrictionSet() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mBundle.getBoolean(UserManager.DISALLOW_WIFI_TETHERING)).thenReturn(true);
        mLooper.startAutoDispatch();
        mWifiServiceImpl.onUserRestrictionsChanged();
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_TETHERED);
    }

    /**
     * Verify isWifiBandSupported for 24GHz with an overlay override config
     */
    @Test
    public void testIsWifiBandSupported24gWithOverride() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi24ghzSupport)).thenReturn(true);
        assertTrue(mWifiServiceImpl.is24GHzBandSupported());
        verify(mActiveModeWarden, never()).isBandSupportedForSta(anyInt());
    }

    /**
     * Verify isWifiBandSupported for 5GHz with an overlay override config
     */
    @Test
    public void testIsWifiBandSupported5gWithOverride() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi5ghzSupport)).thenReturn(true);
        assertTrue(mWifiServiceImpl.is5GHzBandSupported());
        verify(mActiveModeWarden, never()).isBandSupportedForSta(anyInt());
    }

    /**
     * Verify isWifiBandSupported for 6GHz with an overlay override config
     */
    @Test
    public void testIsWifiBandSupported6gWithOverride() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi6ghzSupport)).thenReturn(true);
        assertTrue(mWifiServiceImpl.is6GHzBandSupported());
        verify(mActiveModeWarden, never()).isBandSupportedForSta(anyInt());
    }

    /**
     * Verify isWifiBandSupported for 24GHz with no overlay override config no channels
     */
    @Test
    public void testIsWifiBandSupported24gNoOverrideNoChannels() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi24ghzSupport)).thenReturn(false);
        when(mActiveModeWarden.isBandSupportedForSta(WIFI_BAND_24_GHZ)).thenReturn(false);
        assertFalse(mWifiServiceImpl.is24GHzBandSupported());
        verify(mActiveModeWarden).isBandSupportedForSta(WifiScanner.WIFI_BAND_24_GHZ);
    }

    /**
     * Verify isWifiBandSupported for 5GHz with no overlay override config no channels
     */
    @Test
    public void testIsWifiBandSupported5gNoOverrideNoChannels() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi5ghzSupport)).thenReturn(false);
        when(mActiveModeWarden.isBandSupportedForSta(WIFI_BAND_5_GHZ)).thenReturn(false);
        assertFalse(mWifiServiceImpl.is5GHzBandSupported());
        verify(mActiveModeWarden).isBandSupportedForSta(WifiScanner.WIFI_BAND_5_GHZ);
    }

    /**
     * Verify isWifiBandSupported for 24GHz with no overlay override config with channels
     */
    @Test
    public void testIsWifiBandSupported24gNoOverrideWithChannels() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi24ghzSupport)).thenReturn(false);
        when(mActiveModeWarden.isBandSupportedForSta(WIFI_BAND_24_GHZ)).thenReturn(true);
        assertTrue(mWifiServiceImpl.is24GHzBandSupported());
        verify(mActiveModeWarden).isBandSupportedForSta(WifiScanner.WIFI_BAND_24_GHZ);
    }

    /**
     * Verify isWifiBandSupported for 5GHz with no overlay override config with channels
     */
    @Test
    public void testIsWifiBandSupported5gNoOverrideWithChannels() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi5ghzSupport)).thenReturn(false);
        when(mActiveModeWarden.isBandSupportedForSta(WIFI_BAND_5_GHZ)).thenReturn(true);
        assertTrue(mWifiServiceImpl.is5GHzBandSupported());
        verify(mActiveModeWarden).isBandSupportedForSta(WifiScanner.WIFI_BAND_5_GHZ);
    }

    /**
     * Verify isWifiBandSupported for 6GHz with no overlay override config no channels
     */
    @Test
    public void testIsWifiBandSupported6gNoOverrideNoChannels() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi6ghzSupport)).thenReturn(false);
        when(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_6_GHZ)).thenReturn(
                false);
        assertFalse(mWifiServiceImpl.is6GHzBandSupported());
        verify(mActiveModeWarden).isBandSupportedForSta(WifiScanner.WIFI_BAND_6_GHZ);
    }

    /**
     * Verify isWifiBandSupported for 6GHz with no overlay override config with channels
     */
    @Test
    public void testIsWifiBandSupported6gNoOverrideWithChannels() throws Exception {
        when(mResources.getBoolean(R.bool.config_wifi6ghzSupport)).thenReturn(false);
        when(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_6_GHZ)).thenReturn(
                true);
        assertTrue(mWifiServiceImpl.is6GHzBandSupported());
        verify(mActiveModeWarden).isBandSupportedForSta(WifiScanner.WIFI_BAND_6_GHZ);
    }

    private void setup24GhzSupported() {
        when(mResources.getBoolean(R.bool.config_wifi24ghzSupport)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiSoftap24ghzSupported)).thenReturn(true);
    }

    private void setup24GhzUnsupported(boolean isOnlyUnsupportedSoftAp) {
        when(mResources.getBoolean(R.bool.config_wifiSoftap24ghzSupported)).thenReturn(false);
        if (!isOnlyUnsupportedSoftAp) {
            when(mResources.getBoolean(R.bool.config_wifi24ghzSupport)).thenReturn(false);
            when(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_24_GHZ))
                    .thenReturn(false);
        }
    }

    private void setup5GhzSupported() {
        when(mResources.getBoolean(R.bool.config_wifi5ghzSupport)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiSoftap5ghzSupported)).thenReturn(true);
    }

    private void setup5GhzUnsupported(boolean isOnlyUnsupportedSoftAp) {
        when(mResources.getBoolean(R.bool.config_wifiSoftap5ghzSupported)).thenReturn(false);
        if (!isOnlyUnsupportedSoftAp) {
            when(mResources.getBoolean(R.bool.config_wifi5ghzSupport)).thenReturn(false);
            when(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_5_GHZ))
                    .thenReturn(false);
        }
    }

    private void setup6GhzSupported() {
        when(mResources.getBoolean(R.bool.config_wifi6ghzSupport)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiSoftap6ghzSupported)).thenReturn(true);
    }

    private void setup6GhzUnsupported(boolean isOnlyUnsupportedSoftAp) {
        when(mResources.getBoolean(R.bool.config_wifiSoftap6ghzSupported)).thenReturn(false);
        if (!isOnlyUnsupportedSoftAp) {
            when(mResources.getBoolean(R.bool.config_wifi6ghzSupport)).thenReturn(false);
            when(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_6_GHZ))
                    .thenReturn(false);
        }
    }

    private void setup60GhzSupported() {
        when(mResources.getBoolean(R.bool.config_wifi60ghzSupport)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_wifiSoftap60ghzSupported)).thenReturn(true);
    }

    private void setup60GhzUnsupported(boolean isOnlyUnsupportedSoftAp) {
        when(mResources.getBoolean(R.bool.config_wifiSoftap60ghzSupported)).thenReturn(false);
        if (!isOnlyUnsupportedSoftAp) {
            when(mResources.getBoolean(R.bool.config_wifi60ghzSupport)).thenReturn(false);
            when(mActiveModeWarden.isBandSupportedForSta(WifiScanner.WIFI_BAND_60_GHZ))
                    .thenReturn(false);
        }
    }

    /**
     * Verify attempt to start softAp with a supported 24GHz band succeeds.
     */
    @Test
    public void testStartTetheredHotspotWithSupported24gBand() {
        setup24GhzSupported();

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify attempt to start softAp with a non-supported 2.4GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupported24gBand() {
        setup24GhzUnsupported(false);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a non-supported 2.4GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupportedSoftAp24gBand() {
        setup24GhzSupported();
        setup24GhzUnsupported(true);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a supported 5GHz band succeeds.
     */
    @Test
    public void testStartTetheredHotspotWithSupported5gBand() {
        setup5GhzSupported();

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_5GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify attempt to start softAp with a non-supported 5GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupported5gBand() {
        setup5GhzUnsupported(false);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_5GHZ)
                .build();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a non-supported 5GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupportedSoftAp5gBand() {
        setup5GhzSupported();
        setup5GhzUnsupported(true);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_5GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a supported 6GHz band succeeds.
     */
    @Test
    public void testStartTetheredHotspotWithSupported6gBand() {
        setup6GhzSupported();

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .setBand(SoftApConfiguration.BAND_6GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify attempt to start softAp with a non-supported 6GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupported6gBand() {
        setup6GhzUnsupported(false);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .setBand(SoftApConfiguration.BAND_6GHZ)
                .build();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a non-supported 6GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupportedSoftAp6gBand() {
        setup6GhzSupported();
        setup6GhzUnsupported(true);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .setBand(SoftApConfiguration.BAND_6GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }


    /**
     * Verify attempt to start softAp with a supported 60GHz band succeeds.
     */
    @Test
    public void testStartTetheredHotspotWithSupported60gBand() {
        setup60GhzSupported();

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_60GHZ)
                .build();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify attempt to start softAp with a non-supported 60GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupported60gBand() {
        setup60GhzUnsupported(false);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_60GHZ)
                .build();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a non-supported 60GHz band fails.
     */
    @Test
    public void testStartTetheredHotspotWithUnSupportedSoftAp60gBand() {
        setup60GhzSupported();
        setup60GhzUnsupported(true);

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_60GHZ)
                .build();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);

        assertFalse(result);
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    /**
     * Verify attempt to start softAp with a supported band succeeds.
     */
    @Test
    public void testStartTetheredHotspotWithSupportedBand() {
        setup5GhzSupported();

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_5GHZ)
                .build();

        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify a SecurityException is thrown when a caller without the correct permission attempts to
     * start softap.
     */
    @Test(expected = SecurityException.class)
    public void testStartTetheredHotspotWithoutPermissionThrowsException() throws Exception {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
        mWifiServiceImpl.startTetheredHotspot(null, TEST_PACKAGE_NAME);
    }

    /**
     * Verify that startTetheredHotspot() succeeds if the caller does not have the
     * NETWORK_STACK permission but does have the MAINLINE_NETWORK_STACK permission.
     */
    @Test
    public void testStartTetheredHotspotWithoutNetworkStackWithMainlineNetworkStackSucceeds() {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        SoftApConfiguration config = createValidSoftApConfiguration();
        boolean result = mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME);
        assertTrue(result);
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        verify(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify a valied call to startTetheredHotspot succeeds after a failed call.
     */
    @Test
    public void testStartTetheredHotspotWithValidConfigSucceedsAfterFailedCall() {
        // First issue an invalid call
        assertFalse(mWifiServiceImpl.startTetheredHotspot(
                new SoftApConfiguration.Builder().build(), TEST_PACKAGE_NAME));
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());

        assertTrue(mWifiServiceImpl.startTetheredHotspot(null, TEST_PACKAGE_NAME));
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertNull(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify caller with proper permission can call stopSoftAp.
     */
    @Test
    public void testStopSoftApWithPermissions() {
        boolean result = mWifiServiceImpl.stopSoftAp();
        assertTrue(result);
        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_TETHERED);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SOFT_AP), anyInt(),
                anyInt(), anyInt(), anyString(), eq(false));
    }

    /**
     * Verify SecurityException is thrown when a caller without the correct permission attempts to
     * stop softap.
     */
    @Test(expected = SecurityException.class)
    public void testStopSoftApWithoutPermissionThrowsException() throws Exception {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
        mWifiServiceImpl.stopSoftAp();
    }

    /**
     * Ensure that we handle app ops check failure when handling scan request.
     */
    @Test
    public void testStartScanFailureAppOpsIgnored() {
        doReturn(AppOpsManager.MODE_IGNORED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), SCAN_PACKAGE_NAME);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startScan(SCAN_PACKAGE_NAME, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy, never()).startScan(Process.myUid(), SCAN_PACKAGE_NAME);
    }

    /**
     * Ensure that we handle scan access permission check failure when handling scan request.
     */
    @Test
    public void testStartScanFailureInCanAccessScanResultsPermission() {
        doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                .enforceCanAccessScanResults(SCAN_PACKAGE_NAME, TEST_FEATURE_ID, Process.myUid(),
                        null);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startScan(SCAN_PACKAGE_NAME, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy, never()).startScan(Process.myUid(), SCAN_PACKAGE_NAME);
    }

    /**
     * Ensure that we handle scan request failure when posting the runnable to handler fails.
     */
    @Test
    public void testStartScanFailureInRunWithScissors() {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startScan(SCAN_PACKAGE_NAME, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy, never()).startScan(anyInt(), eq(SCAN_PACKAGE_NAME));
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_START_SCAN), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Ensure that we handle scan request failure from ScanRequestProxy fails.
     */
    @Test
    public void testStartScanFailureFromScanRequestProxy() {
        when(mScanRequestProxy.startScan(anyInt(), anyString())).thenReturn(false);

        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startScan(SCAN_PACKAGE_NAME, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy).startScan(Binder.getCallingUid(), SCAN_PACKAGE_NAME);
    }

    private WifiInfo setupForGetConnectionInfo() {
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setSSID(WifiSsid.fromUtf8Text(TEST_SSID));
        wifiInfo.setBSSID(TEST_BSSID);
        wifiInfo.setNetworkId(TEST_NETWORK_ID);
        wifiInfo.setFQDN(TEST_FQDN);
        wifiInfo.setProviderFriendlyName(TEST_FRIENDLY_NAME);
        return wifiInfo;
    }

    private WifiInfo parcelingRoundTrip(WifiInfo wifiInfo) {
        Parcel parcel = Parcel.obtain();
        wifiInfo.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        return WifiInfo.CREATOR.createFromParcel(parcel);
    }

    /**
     * Test that connected SSID and BSSID are not exposed to an app that does not have the
     * appropriate permissions.
     */
    @Test
    public void testConnectedIdsAreHiddenFromAppWithoutPermission() throws Exception {
        WifiInfo wifiInfo = setupForGetConnectionInfo();
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(wifiInfo);
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_ENABLED);
        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));

        mLooper.startAutoDispatch();
        WifiInfo connectionInfo = parcelingRoundTrip(
                mWifiServiceImpl.getConnectionInfo(TEST_PACKAGE, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(WifiManager.UNKNOWN_SSID, connectionInfo.getSSID());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, connectionInfo.getBSSID());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, connectionInfo.getNetworkId());
        assertNull(connectionInfo.getPasspointFqdn());
        assertNull(connectionInfo.getPasspointProviderFriendlyName());
        if (SdkLevel.isAtLeastS()) {
            try {
                connectionInfo.isPrimary();
                fail();
            } catch (SecurityException e) { /* pass */ }
        }
    }

    /**
     * Test that connected SSID and BSSID are not exposed to an app that does not have the
     * appropriate permissions, when enforceCanAccessScanResults raises a SecurityException.
     */
    @Test
    public void testConnectedIdsAreHiddenOnSecurityException() throws Exception {
        WifiInfo wifiInfo = setupForGetConnectionInfo();
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_ENABLED);
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(wifiInfo);

        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));

        mLooper.startAutoDispatch();
        WifiInfo connectionInfo = parcelingRoundTrip(
                mWifiServiceImpl.getConnectionInfo(TEST_PACKAGE, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(WifiManager.UNKNOWN_SSID, connectionInfo.getSSID());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, connectionInfo.getBSSID());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, connectionInfo.getNetworkId());
        assertNull(connectionInfo.getPasspointFqdn());
        assertNull(connectionInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Test that connected SSID and BSSID are exposed to an app that does have the
     * appropriate permissions.
     */
    @Test
    public void testConnectedIdsAreVisibleFromPermittedApp() throws Exception {
        WifiInfo wifiInfo = setupForGetConnectionInfo();
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_ENABLED);
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(wifiInfo);

        mLooper.startAutoDispatch();
        WifiInfo connectionInfo = parcelingRoundTrip(
                mWifiServiceImpl.getConnectionInfo(TEST_PACKAGE, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(TEST_SSID_WITH_QUOTES, connectionInfo.getSSID());
        assertEquals(TEST_BSSID, connectionInfo.getBSSID());
        assertEquals(TEST_NETWORK_ID, connectionInfo.getNetworkId());
        assertEquals(TEST_FQDN, connectionInfo.getPasspointFqdn());
        assertEquals(TEST_FRIENDLY_NAME, connectionInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Test that connected SSID and BSSID for secondary CMM are exposed to an app that requests
     * the second STA on a device that supports STA + STA.
     */
    @Test
    public void testConnectedIdsFromSecondaryCmmAreVisibleFromAppRequestingSecondaryCmm()
            throws Exception {
        WifiInfo wifiInfo = setupForGetConnectionInfo();
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_ENABLED);
        when(mActiveModeWarden.getSecondaryRequestWs())
                .thenReturn(Set.of(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE)));
        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        when(secondaryCmm.getRequestorWs())
                .thenReturn(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE));
        when(secondaryCmm.getConnectionInfo()).thenReturn(wifiInfo);
        when(mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_LOCAL_ONLY, ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(Arrays.asList(secondaryCmm));

        mLooper.startAutoDispatch();
        WifiInfo connectionInfo = parcelingRoundTrip(
                mWifiServiceImpl.getConnectionInfo(TEST_PACKAGE, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(TEST_SSID_WITH_QUOTES, connectionInfo.getSSID());
        assertEquals(TEST_BSSID, connectionInfo.getBSSID());
        assertEquals(TEST_NETWORK_ID, connectionInfo.getNetworkId());
        assertEquals(TEST_FQDN, connectionInfo.getPasspointFqdn());
        assertEquals(TEST_FRIENDLY_NAME, connectionInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Test that connected SSID and BSSID for secondary CMM are exposed to an app that requests
     * the second STA on a device that supports STA + STA. The request WorkSource of CMM is settings
     * promoted.
     */
    @Test
    public void testConnectedIdsAreVisibleFromAppRequestingSecondaryCmmWIthPromotesSettingsWs()
            throws Exception {
        WifiInfo wifiInfo = setupForGetConnectionInfo();
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_ENABLED);
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(new WifiInfo());
        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        WorkSource ws = new WorkSource(Binder.getCallingUid(), TEST_PACKAGE);
        ws.add(SETTINGS_WORKSOURCE);
        when(secondaryCmm.getRequestorWs()).thenReturn(ws);
        when(mActiveModeWarden.getSecondaryRequestWs()).thenReturn(Set.of(ws));
        when(secondaryCmm.getConnectionInfo()).thenReturn(wifiInfo);
        when(mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_LOCAL_ONLY, ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(Arrays.asList(secondaryCmm));
        ConcreteClientModeManager primaryCmm = mock(ConcreteClientModeManager.class);
        when(primaryCmm.getConnectionInfo()).thenReturn(new WifiInfo());
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(primaryCmm);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(SETTINGS_WORKSOURCE.getUid(0)))
                .thenReturn(true);

        mLooper.startAutoDispatch();
        WifiInfo connectionInfo = parcelingRoundTrip(
                mWifiServiceImpl.getConnectionInfo(TEST_PACKAGE, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(TEST_SSID_WITH_QUOTES, connectionInfo.getSSID());
        assertEquals(TEST_BSSID, connectionInfo.getBSSID());
        assertEquals(TEST_NETWORK_ID, connectionInfo.getNetworkId());
        assertEquals(TEST_FQDN, connectionInfo.getPasspointFqdn());
        assertEquals(TEST_FRIENDLY_NAME, connectionInfo.getPasspointProviderFriendlyName());
        verify(mActiveModeWarden, never()).getPrimaryClientModeManager();

        mLooper.startAutoDispatch();
        connectionInfo = parcelingRoundTrip(mWifiServiceImpl
                .getConnectionInfo(SETTINGS_WORKSOURCE.getPackageName(0), TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(WifiManager.UNKNOWN_SSID, connectionInfo.getSSID());
        verify(mActiveModeWarden).getConnectionInfo();
    }

    /**
     * Test that connected SSID and BSSID for primary CMM are exposed to an app that is not the one
     * that requests the second STA on a device that supports STA + STA.
     */
    @Test
    public void testConnectedIdsFromPrimaryCmmAreVisibleFromAppNotRequestingSecondaryCmm()
            throws Exception {
        WifiInfo wifiInfo = setupForGetConnectionInfo();
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(wifiInfo);
        when(mActiveModeWarden.getWifiState()).thenReturn(WIFI_STATE_ENABLED);
        when(mActiveModeWarden.getSecondaryRequestWs())
                .thenReturn(
                        Set.of(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME_OTHER)));

        mLooper.startAutoDispatch();
        WifiInfo connectionInfo = parcelingRoundTrip(
                mWifiServiceImpl.getConnectionInfo(TEST_PACKAGE, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(TEST_SSID_WITH_QUOTES, connectionInfo.getSSID());
        assertEquals(TEST_BSSID, connectionInfo.getBSSID());
        assertEquals(TEST_NETWORK_ID, connectionInfo.getNetworkId());
        assertEquals(TEST_FQDN, connectionInfo.getPasspointFqdn());
        assertEquals(TEST_FRIENDLY_NAME, connectionInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Test that configured network list are exposed empty list to an app that does not have the
     * appropriate permissions.
     */
    @Test
    public void testConfiguredNetworkListAreEmptyFromAppWithoutPermission() throws Exception {
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(TEST_WIFI_CONFIGURATION_LIST);

        // no permission = target SDK=Q && not a carrier app
        when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(anyString())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);

        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID, false);

        assertEquals(0, configs.getList().size());
    }

    /**
     * Test that configured network list are exposed empty list to an app that does not have the
     * appropriate permissions, when enforceCanAccessScanResults raises a SecurityException.
     */
    @Test
    public void testConfiguredNetworkListAreEmptyOnSecurityException() throws Exception {
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(TEST_WIFI_CONFIGURATION_LIST);

        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));

        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID, false);

        assertEquals(0, configs.getList().size());

    }

    /**
     * Test that configured network list are exposed to an app that does have the
     * appropriate permissions.
     */
    @Test
    public void testConfiguredNetworkListAreVisibleFromPermittedApp() throws Exception {
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(TEST_WIFI_CONFIGURATION_LIST);

        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID, false);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiConfigManager).getSavedNetworks(eq(WIFI_UID));
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                TEST_WIFI_CONFIGURATION_LIST, configs.getList());
    }

    @Test(expected = SecurityException.class)
    public void testGetCallerConfiguredNetworks_ThrowExceptionIfNotDoOrPO() {
        when(mWifiPermissionsUtil.isDeviceOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(false);

        mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE_NAME, TEST_FEATURE_ID, true);
    }

    @Test
    public void testGetCallerConfiguredNetworks_ReturnsCallerNetworks() {
        final int callerUid = Binder.getCallingUid();
        WifiConfiguration callerNetwork0 = WifiConfigurationTestUtil.generateWifiConfig(
                0, callerUid, "\"red\"", true, true, null, null, SECURITY_NONE);
        WifiConfiguration callerNetwork1 = WifiConfigurationTestUtil.generateWifiConfig(
                1, callerUid, "\"red\"", true, true, null, null, SECURITY_NONE);
        WifiConfiguration nonCallerNetwork0 = WifiConfigurationTestUtil.generateWifiConfig(
                2, 1200000, "\"blue\"", false, true, null, null, SECURITY_NONE);
        WifiConfiguration nonCallerNetwork1 = WifiConfigurationTestUtil.generateWifiConfig(
                3, 1100000, "\"cyan\"", true, true, null, null, SECURITY_NONE);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(Arrays.asList(
                callerNetwork0, callerNetwork1, nonCallerNetwork0, nonCallerNetwork1));

        // Caller does NOT need to have location permission to be able to retrieve its own networks.
        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));
        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE_NAME, TEST_FEATURE_ID, true);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                Arrays.asList(callerNetwork0, callerNetwork1), configs.getList());
    }

    /**
     * Test that admin may retrieve all networks but mac address is set to default for networks
     * they do not own.
     */
    @Test
    public void testGetConfiguredNetworksAdmin_ReturnsNetworksWithDefaultMac() {
        final int callerUid = Binder.getCallingUid();
        WifiConfiguration callerNetwork = WifiConfigurationTestUtil.generateWifiConfig(
                0, callerUid, "\"red\"", true, true, null, null, SECURITY_NONE);
        WifiConfiguration nonCallerNetwork = WifiConfigurationTestUtil.generateWifiConfig(
                2, 1200000, "\"blue\"", true, true, null, null, SECURITY_NONE);
        callerNetwork.setRandomizedMacAddress(TEST_FACTORY_MAC_ADDR);

        when(mWifiConfigManager.getSavedNetworks(callerUid)).thenReturn(Arrays.asList(
                callerNetwork, nonCallerNetwork));

        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE_NAME, TEST_FEATURE_ID, false);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                Arrays.asList(callerNetwork, nonCallerNetwork), configs.getList());

        for (WifiConfiguration config : configs.getList()) {
            if (config.getProfileKey().equals(callerNetwork.getProfileKey())) {
                assertEquals(TEST_FACTORY_MAC, config.getRandomizedMacAddress().toString());
            } else {
                assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS,
                        config.getRandomizedMacAddress().toString());
            }
        }
    }

    /**
     * Test that privileged network list are exposed null to an app that targets T or later and does
     * not have nearby devices permission.
     */
    @Test
    public void testPrivilegedConfiguredNetworkListNullOnSecurityExceptionPostT() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        when(mWifiConfigManager.getConfiguredNetworksWithPasswords())
                .thenReturn(TEST_WIFI_CONFIGURATION_LIST);

        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                any(), anyBoolean(), any());

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getPrivilegedConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID,
                        mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertNull(configs);
    }

    /**
     * Test that privileged network list are exposed null to an app that does not have the
     * appropriate permissions, when enforceCanAccessScanResults raises a SecurityException.
     */
    @Test
    public void testPrivilegedConfiguredNetworkListAreEmptyOnSecurityException() {
        when(mWifiConfigManager.getConfiguredNetworksWithPasswords())
                .thenReturn(TEST_WIFI_CONFIGURATION_LIST);

        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceCanAccessScanResults(
                anyString(), nullable(String.class), anyInt(), nullable(String.class));

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getPrivilegedConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID,
                        mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertNull(configs);
    }

    /**
     * Test that privileged network list are exposed to an app that does have the
     * appropriate permissions (simulated by not throwing an exception for READ_WIFI_CREDENTIAL).
     */
    @Test
    public void testPrivilegedConfiguredNetworkListAreVisibleFromPermittedApp() {
        when(mWifiConfigManager.getConfiguredNetworksWithPasswords())
                .thenReturn(TEST_WIFI_CONFIGURATION_LIST);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getPrivilegedConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID,
                        mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                TEST_WIFI_CONFIGURATION_LIST, configs.getList());
    }

    /**
     * Test fetching of scan results.
     */
    @Test
    public void testGetScanResults() {
        ScanResult[] scanResults =
                ScanTestUtil.createScanDatas(new int[][]{{2417, 2427, 5180, 5170}})[0]
                        .getResults();
        List<ScanResult> scanResultList =
                new ArrayList<>(Arrays.asList(scanResults));
        when(mScanRequestProxy.getScanResults()).thenReturn(scanResultList);

        String packageName = "test.com";
        String featureId = "test.com.featureId";
        mLooper.startAutoDispatch();
        List<ScanResult> retrievedScanResultList = mWifiServiceImpl.getScanResults(packageName,
                featureId);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy).getScanResults();

        ScanTestUtil.assertScanResultsEquals(scanResults,
                retrievedScanResultList.toArray(new ScanResult[retrievedScanResultList.size()]));
    }

    /**
     * Ensure that we handle scan results failure when posting the runnable to handler fails.
     */
    @Test
    public void testGetScanResultsFailureInRunWithScissors() {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        ScanResult[] scanResults =
                ScanTestUtil.createScanDatas(new int[][]{{2417, 2427, 5180, 5170}})[0]
                        .getResults();
        List<ScanResult> scanResultList =
                new ArrayList<>(Arrays.asList(scanResults));
        when(mScanRequestProxy.getScanResults()).thenReturn(scanResultList);

        String packageName = "test.com";
        String featureId = "test.com.featureId";
        mLooper.startAutoDispatch();
        List<ScanResult> retrievedScanResultList = mWifiServiceImpl.getScanResults(packageName,
                featureId);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy, never()).getScanResults();

        assertTrue(retrievedScanResultList.isEmpty());
    }

    /**
     * Test fetching of matching scan results with provided WifiNetworkSuggestion, but it doesn't
     * specify the scan results to be filtered.
     */
    @Test
    public void testGetMatchingScanResultsWithoutSpecifiedScanResults() {
        ScanResult[] scanResults =
                ScanTestUtil.createScanDatas(new int[][]{{2417, 2427, 5180, 5170}})[0]
                        .getResults();
        List<ScanResult> scanResultList =
                new ArrayList<>(Arrays.asList(scanResults));
        when(mScanRequestProxy.getScanResults()).thenReturn(scanResultList);
        WifiNetworkSuggestion mockSuggestion = mock(WifiNetworkSuggestion.class);
        List<WifiNetworkSuggestion> matchingSuggestions = List.of(mockSuggestion);
        Map<WifiNetworkSuggestion, List<ScanResult>> result = Map.of(
                mockSuggestion, scanResultList);
        when(mWifiNetworkSuggestionsManager.getMatchingScanResults(eq(matchingSuggestions),
                eq(scanResultList))).thenReturn(result);

        String packageName = "test.com";
        String featureId = "test.com.featureId";
        mLooper.startAutoDispatch();
        Map<WifiNetworkSuggestion, List<ScanResult>> retrievedScanResults =
                mWifiServiceImpl.getMatchingScanResults(
                        matchingSuggestions, null, packageName, featureId);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        ScanTestUtil.assertScanResultsEquals(scanResults,
                retrievedScanResults.get(mockSuggestion)
                        .toArray(new ScanResult[retrievedScanResults.size()]));
    }

    /**
     * Test fetching of matching scan results with provided WifiNetworkSuggestion and ScanResults.
     */
    @Test
    public void testGetMatchingScanResultsWithSpecifiedScanResults() {
        ScanResult[] scanResults =
                ScanTestUtil.createScanDatas(new int[][]{{2417, 2427, 5180, 5170}})[0]
                        .getResults();
        List<ScanResult> scanResultList =
                new ArrayList<>(Arrays.asList(scanResults));
        WifiNetworkSuggestion mockSuggestion = mock(WifiNetworkSuggestion.class);
        List<WifiNetworkSuggestion> matchingSuggestions = List.of(mockSuggestion);
        Map<WifiNetworkSuggestion, List<ScanResult>> result = Map.of(
                mockSuggestion, scanResultList);

        when(mWifiNetworkSuggestionsManager.getMatchingScanResults(eq(matchingSuggestions),
                eq(scanResultList))).thenReturn(result);

        String packageName = "test.com";
        String featureId = "test.com.featureId";
        mLooper.startAutoDispatch();
        Map<WifiNetworkSuggestion, List<ScanResult>> retrievedScanResults =
                mWifiServiceImpl.getMatchingScanResults(
                        matchingSuggestions, scanResultList, packageName, featureId);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        ScanTestUtil.assertScanResultsEquals(scanResults,
                retrievedScanResults.get(mockSuggestion)
                        .toArray(new ScanResult[retrievedScanResults.size()]));
    }

    /**
     * Ensure that we handle failure when posting the runnable to handler fails.
     */
    @Test
    public void testGetMatchingScanResultsFailureInRunWithScissors() {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        ScanResult[] scanResults =
                ScanTestUtil.createScanDatas(new int[][]{{2417, 2427, 5180, 5170}})[0]
                        .getResults();
        List<ScanResult> scanResultList =
                new ArrayList<>(Arrays.asList(scanResults));
        when(mScanRequestProxy.getScanResults()).thenReturn(scanResultList);
        WifiNetworkSuggestion mockSuggestion = mock(WifiNetworkSuggestion.class);
        List<WifiNetworkSuggestion> matchingSuggestions = List.of(mockSuggestion);
        Map<WifiNetworkSuggestion, List<ScanResult>> result = Map.of(
                mockSuggestion, scanResultList);

        when(mWifiNetworkSuggestionsManager.getMatchingScanResults(eq(matchingSuggestions),
                eq(scanResultList))).thenReturn(result);

        String packageName = "test.com";
        String featureId = "test.com.featureId";
        mLooper.startAutoDispatch();
        Map<WifiNetworkSuggestion, List<ScanResult>> retrievedScanResults =
                mWifiServiceImpl.getMatchingScanResults(
                        matchingSuggestions, null, packageName, featureId);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertTrue(retrievedScanResults.isEmpty());
    }

    private void setupLohsPermissions() {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mFrameworkFacade.isAppForeground(any(), anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_CONFIG_TETHERING), any()))
                .thenReturn(false);
    }

    private void registerLOHSRequestFull() {
        mLooper.startAutoDispatch();
        setupLohsPermissions();
        int result = mWifiServiceImpl.startLocalOnlyHotspot(mLohsCallback, TEST_PACKAGE_NAME,
                TEST_FEATURE_ID, null, mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(LocalOnlyHotspotCallback.REQUEST_REGISTERED, result);
        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
    }

    /**
     * Verify that the call to startLocalOnlyHotspot returns REQUEST_REGISTERED when successfully
     * called.
     */
    @Test
    public void testStartLocalOnlyHotspotSingleRegistrationReturnsRequestRegistered() {
        registerLOHSRequestFull();
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(), any());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if the caller does not
     * have the CHANGE_WIFI_STATE permission.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityExceptionWithoutCorrectPermission() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                                                eq("WifiService"));
        mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if the caller does not
     * have Location permission.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityExceptionWithoutLocationPermission() {
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceLocationPermission(eq(TEST_PACKAGE_NAME),
                                                                      eq(TEST_FEATURE_ID),
                                                                      anyInt());
        mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if the caller targets
     * Android T or later and does not have nearby devices permission.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsExceptionWithoutNearbyDevicesPermissionOnT() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                        any(), anyBoolean(), any());
        mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
    }

    /**
     * Verify that a call to startLocalOnlyHotspot will not check nearby devices permission if the
     * caller does not target T.
     */
    @Test
    public void testStartLocalOnlyHotspotDoesNotCheckNearbyPermissionIfTargetPreT() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(true);
        mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
        verify(mWifiPermissionsUtil, never()).enforceNearbyDevicesPermission(any(), anyBoolean(),
                any());
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if Location mode is
     * disabled.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityExceptionWithoutLocationEnabled() {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);
        mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
    }

    /**
     * Only start LocalOnlyHotspot if the caller is the foreground app at the time of the request.
     */
    @Test
    public void testStartLocalOnlyHotspotFailsIfRequestorNotForegroundApp() throws Exception {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        when(mFrameworkFacade.isAppForeground(any(), anyInt())).thenReturn(false);
        int result = mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
        assertEquals(LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE, result);
    }

    /**
     * Only start tethering if we are not tethering.
     */
    @Test
    public void testTetheringDoesNotStartWhenAlreadyTetheringActive() throws Exception {
        WifiConfiguration config = createValidWifiApConfiguration();
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.startSoftAp(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        WifiConfigurationTestUtil.assertConfigurationEqualForSoftAp(
                config,
                mSoftApModeConfigCaptor.getValue().getSoftApConfiguration().toWifiConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        assertEquals(WIFI_AP_STATE_ENABLED, mWifiServiceImpl.getWifiApEnabledState());
        reset(mActiveModeWarden);

        // Start another session without a stop, that should fail.
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startSoftAp(
                createValidWifiApConfiguration(), TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyNoMoreInteractions(mActiveModeWarden);
    }

    /**
     * Only start tethering if we are not tethering in new API: startTetheredHotspot.
     */
    @Test
    public void testStartTetheredHotspotDoesNotStartWhenAlreadyTetheringActive() throws Exception {
        SoftApConfiguration config = createValidSoftApConfiguration();
        assertTrue(mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME));
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(config).isEqualTo(mSoftApModeConfigCaptor.getValue().getSoftApConfiguration());
        assertNull(mSoftApModeConfigCaptor.getValue().getTetheringRequest());
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        assertEquals(WIFI_AP_STATE_ENABLED, mWifiServiceImpl.getWifiApEnabledState());
        reset(mActiveModeWarden);

        // Start another session without a stop, that should fail.
        assertFalse(mWifiServiceImpl.startTetheredHotspot(config, TEST_PACKAGE_NAME));

        verifyNoMoreInteractions(mActiveModeWarden);
    }

    /**
     * Only start LocalOnlyHotspot if we are not tethering.
     */
    @Test
    public void testHotspotDoesNotStartWhenAlreadyTethering() throws Exception {
        WifiConfiguration config = createValidWifiApConfiguration();
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.startSoftAp(config, TEST_PACKAGE_NAME));
        mStateMachineSoftApCallback.onStateChanged(
                new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                        TEST_TETHERING_REQUEST, TEST_IFACE_NAME));
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mFrameworkFacade.isAppForeground(any(), anyInt())).thenReturn(true);
        mLooper.dispatchAll();
        int returnCode = mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
        assertEquals(ERROR_INCOMPATIBLE_MODE, returnCode);
    }

    /**
     * Only start LocalOnlyHotspot if admin setting does not disallow tethering.
     */
    @Test
    public void testHotspotDoesNotStartWhenTetheringDisallowed() throws Exception {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mFrameworkFacade.isAppForeground(any(), anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_CONFIG_TETHERING), any()))
                .thenReturn(true);
        int returnCode = mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
        assertEquals(ERROR_TETHERING_DISALLOWED, returnCode);
    }

    /**
     * Verify that callers can only have one registered LOHS request.
     */
    @Test(expected = IllegalStateException.class)
    public void testStartLocalOnlyHotspotThrowsExceptionWhenCallerAlreadyRegistered() {
        registerLOHSRequestFull();

        // now do the second request that will fail
        mWifiServiceImpl.startLocalOnlyHotspot(
                mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
    }

    /**
     * Verify that the call to stopLocalOnlyHotspot does not do anything when there aren't any
     * registered callers.
     */
    @Test
    public void testStopLocalOnlyHotspotDoesNothingWithoutRegisteredRequests() throws Exception {
        // allow test to proceed without a permission check failure
        mWifiServiceImpl.stopLocalOnlyHotspot();
        mLooper.dispatchAll();
        // there is nothing registered, so this shouldn't do anything
        verify(mActiveModeWarden, never()).stopSoftAp(anyInt());
    }

    /**
     * Verify that the call to stopLocalOnlyHotspot does not do anything when one caller unregisters
     * but there is still an active request
     */
    @Test
    public void testStopLocalOnlyHotspotDoesNothingWithRemainingRequest() throws Exception {

        // register a request that will remain after the stopLOHS call
        mWifiServiceImpl.registerLOHSForTest(mPid, mRequestInfo);
        mLooper.dispatchAll();
        setupLocalOnlyHotspot();
        // Since we are calling with the same pid, the second register call will be removed
        mWifiServiceImpl.stopLocalOnlyHotspot();
        mLooper.dispatchAll();
        // there is still a valid registered request - do not tear down LOHS
        verify(mActiveModeWarden, never()).stopSoftAp(anyInt());
    }

    /**
     * Verify that the call to stopLocalOnlyHotspot sends a message to WifiController to stop
     * the softAp when there is one registered caller when that caller is removed.
     */
    @Test
    public void testStopLocalOnlyHotspotTriggersStopWithOneRegisteredRequest() throws Exception {
        setupLocalOnlyHotspot();

        verify(mActiveModeWarden).startSoftAp(any(), any());

        mWifiServiceImpl.stopLocalOnlyHotspot();
        mLooper.dispatchAll();

        // No permission check required for change_wifi_state.
        verify(mContext, never()).enforceCallingOrSelfPermission(
                eq("android.Manifest.permission.CHANGE_WIFI_STATE"), anyString());

        // there is was only one request registered, we should tear down LOHS
        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
    }

    /**
     * Verify that by default startLocalOnlyHotspot starts access point at 2 GHz.
     */
    @Test
    public void testStartLocalOnlyHotspotAt2Ghz() {
        SoftApConfiguration lohsConfig = createValidSoftApConfiguration();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                eq(mContext), eq(null), any())).thenReturn(lohsConfig);
        registerLOHSRequestFull();
        verify(mWifiApConfigStore).generateLocalOnlyHotspotConfig(
                eq(mContext), eq(null), any());
        verifyLohsBand(SoftApConfiguration.BAND_2GHZ);
    }

    private void verifyLohsBand(int expectedBand) {
        verify(mActiveModeWarden).startSoftAp(mSoftApModeConfigCaptor.capture(), any());
        final SoftApConfiguration configuration =
                mSoftApModeConfigCaptor.getValue().getSoftApConfiguration();
        assertNotNull(configuration);
        assertEquals(expectedBand, configuration.getBand());
    }

    private static class FakeLohsCallback extends ILocalOnlyHotspotCallback.Stub {
        boolean mIsStarted = false;
        SoftApConfiguration mSoftApConfig = null;

        @Override
        public void onHotspotStarted(SoftApConfiguration softApConfig) {
            mIsStarted = true;
            this.mSoftApConfig = softApConfig;
        }

        @Override
        public void onHotspotStopped() {
            mIsStarted = false;
            mSoftApConfig = null;
        }

        @Override
        public void onHotspotFailed(int i) {
            mIsStarted = false;
            mSoftApConfig = null;
        }
    }

    private void setupForCustomLohs() {
        setupLohsPermissions();
        when(mContext.checkPermission(eq(Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        setupWardenForCustomLohs();
    }

    private void setupWardenForCustomLohs() {
        doAnswer(invocation -> {
            changeLohsState(WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR);
            mWifiServiceImpl.updateInterfaceIpState(mLohsInterfaceName, IFACE_IP_MODE_LOCAL_ONLY);
            return null;
        }).when(mActiveModeWarden).startSoftAp(any(), any());
    }

    @Test(expected = SecurityException.class)
    public void testCustomLohs_FailsWithoutPermission() {
        SoftApConfiguration customConfig = new SoftApConfiguration.Builder()
                .setSsid("customConfig")
                .build();
        // set up basic permissions, but not NETWORK_SETUP_WIZARD
        setupLohsPermissions();
        setupWardenForCustomLohs();
        mWifiServiceImpl.startLocalOnlyHotspot(mLohsCallback, TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                customConfig, mExtras);
    }

    private static void nopDeathCallback(LocalOnlyHotspotRequestInfo requestor) {
    }

    @Test
    public void testCustomLohs_ExclusiveAfterShared() {
        mLooper.startAutoDispatch();
        FakeLohsCallback sharedCallback = new FakeLohsCallback();
        FakeLohsCallback exclusiveCallback = new FakeLohsCallback();
        SoftApConfiguration exclusiveConfig = new SoftApConfiguration.Builder()
                .setSsid("customSsid")
                .build();

        setupForCustomLohs();
        mWifiServiceImpl.registerLOHSForTest(mPid,
                new LocalOnlyHotspotRequestInfo(mLooper.getLooper(), new WorkSource(),
                        sharedCallback, WifiServiceImplTest::nopDeathCallback, null));
        assertThat(mWifiServiceImpl.startLocalOnlyHotspot(exclusiveCallback, TEST_PACKAGE_NAME,
                TEST_FEATURE_ID, exclusiveConfig, mExtras)).isEqualTo(ERROR_GENERIC);
        stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(mLooper);
        assertThat(sharedCallback.mIsStarted).isTrue();
        assertThat(exclusiveCallback.mIsStarted).isFalse();
    }

    @Test
    public void testCustomLohs_ExclusiveBeforeShared() {
        mLooper.startAutoDispatch();
        FakeLohsCallback sharedCallback = new FakeLohsCallback();
        FakeLohsCallback exclusiveCallback = new FakeLohsCallback();
        SoftApConfiguration exclusiveConfig = new SoftApConfiguration.Builder()
                .setSsid("customSsid")
                .build();

        setupForCustomLohs();
        mWifiServiceImpl.registerLOHSForTest(mPid,
                new LocalOnlyHotspotRequestInfo(mLooper.getLooper(), new WorkSource(),
                        exclusiveCallback, WifiServiceImplTest::nopDeathCallback, exclusiveConfig));
        stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(mLooper);
        assertThat(mWifiServiceImpl.startLocalOnlyHotspot(sharedCallback, TEST_PACKAGE_NAME,
                TEST_FEATURE_ID, null, mExtras)).isEqualTo(ERROR_GENERIC);
        assertThat(exclusiveCallback.mIsStarted).isTrue();
        assertThat(sharedCallback.mIsStarted).isFalse();
    }

    @Test
    public void testCustomLohs_Wpa2() {
        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("customSsid")
                .setPassphrase("passphrase", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .build();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                eq(mContext), eq(config), any())).thenReturn(config);
        FakeLohsCallback callback = new FakeLohsCallback();
        mLooper.startAutoDispatch();
        setupForCustomLohs();
        assertThat(
                mWifiServiceImpl.startLocalOnlyHotspot(callback, TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                        config, mExtras)).isEqualTo(REQUEST_REGISTERED);
        stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(mLooper);
        verify(mWifiApConfigStore).generateLocalOnlyHotspotConfig(
                eq(mContext), eq(config), any());
        // Use app's worksouce.
        verify(mActiveModeWarden).startSoftAp(any(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(callback.mIsStarted).isTrue();
        assertThat(callback.mSoftApConfig.getWifiSsid().getUtf8Text()).isEqualTo("customSsid");
        assertThat(callback.mSoftApConfig.getSecurityType())
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertThat(callback.mSoftApConfig.getPassphrase()).isEqualTo("passphrase");
    }

    @Test
    public void testCustomLohs_Open() {
        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("customSsid")
                .build();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                eq(mContext), eq(config), any())).thenReturn(config);
        FakeLohsCallback callback = new FakeLohsCallback();
        mLooper.startAutoDispatch();
        setupForCustomLohs();
        assertThat(
                mWifiServiceImpl.startLocalOnlyHotspot(callback, TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                        config, mExtras)).isEqualTo(REQUEST_REGISTERED);
        stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(mLooper);
        verify(mWifiApConfigStore).generateLocalOnlyHotspotConfig(
                eq(mContext), eq(config), any());
        // Use app's worksouce.
        verify(mActiveModeWarden).startSoftAp(any(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(callback.mIsStarted).isTrue();
        assertThat(callback.mSoftApConfig.getWifiSsid().getUtf8Text()).isEqualTo("customSsid");
        assertThat(callback.mSoftApConfig.getSecurityType())
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_OPEN);
        assertThat(callback.mSoftApConfig.getPassphrase()).isNull();
    }

    @Test
    public void testCustomLohs_GeneratesSsidIfAbsent() {
        SoftApConfiguration lohsConfig = createValidSoftApConfiguration();
        SoftApConfiguration customizedConfig = new SoftApConfiguration.Builder()
                .setPassphrase("passphrase", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .build();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                eq(mContext), eq(customizedConfig), any()))
                .thenReturn(lohsConfig);
        mLooper.startAutoDispatch();
        FakeLohsCallback callback = new FakeLohsCallback();

        setupForCustomLohs();
        assertThat(
                mWifiServiceImpl.startLocalOnlyHotspot(callback, TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                        customizedConfig, mExtras)).isEqualTo(REQUEST_REGISTERED);
        stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(mLooper);
        verify(mWifiApConfigStore).generateLocalOnlyHotspotConfig(
                eq(mContext), eq(customizedConfig), any());
        // Use app's worksouce.
        verify(mActiveModeWarden).startSoftAp(any(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(callback.mIsStarted).isTrue();
        assertThat(callback.mSoftApConfig.getWifiSsid()).isNotNull();
    }

    @Test
    public void testCustomLohs_ForwardsBssid() {
        mLooper.startAutoDispatch();
        SoftApConfiguration lohsConfig = createValidSoftApConfiguration();
        SoftApConfiguration.Builder customizedConfigBuilder =
                new SoftApConfiguration.Builder(lohsConfig)
                .setBssid(MacAddress.fromString("aa:bb:cc:dd:ee:ff"));
        if (SdkLevel.isAtLeastS()) {
            customizedConfigBuilder.setMacRandomizationSetting(
                    SoftApConfiguration.RANDOMIZATION_NONE);
        }
        SoftApConfiguration customizedConfig = customizedConfigBuilder.build();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                eq(mContext), eq(customizedConfig), any()))
                .thenReturn(customizedConfig);
        FakeLohsCallback callback = new FakeLohsCallback();

        setupForCustomLohs();
        assertThat(
                mWifiServiceImpl.startLocalOnlyHotspot(callback, TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                        customizedConfig, mExtras)).isEqualTo(REQUEST_REGISTERED);
        stopAutoDispatchWithDispatchAllBeforeStopAndIgnoreExceptions(mLooper);

        // Use app's worksouce.
        verify(mWifiApConfigStore).generateLocalOnlyHotspotConfig(
                eq(mContext), eq(customizedConfig), any());
        verify(mActiveModeWarden).startSoftAp(any(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        assertThat(callback.mIsStarted).isTrue();
        assertThat(callback.mSoftApConfig.getBssid().toString())
                .ignoringCase().isEqualTo("aa:bb:cc:dd:ee:ff");
    }

    /**
         * Verify that WifiServiceImpl does not send the stop ap message if there were no
         * pending LOHS requests upon a binder death callback.
         */
    @Test
    public void testServiceImplNotCalledWhenBinderDeathTriggeredNoRequests() {
        LocalOnlyRequestorCallback binderDeathCallback =
                mWifiServiceImpl.new LocalOnlyRequestorCallback();

        binderDeathCallback.onLocalOnlyHotspotRequestorDeath(mRequestInfo);
        verify(mActiveModeWarden, never()).stopSoftAp(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
    }

    /**
     * Verify that WifiServiceImpl does not send the stop ap message if there are remaining
     * registered LOHS requests upon a binder death callback.  Additionally verify that softap mode
     * will be stopped if that remaining request is removed (to verify the binder death properly
     * cleared the requestor that died).
     */
    @Test
    public void testServiceImplNotCalledWhenBinderDeathTriggeredWithRequests() throws Exception {
        LocalOnlyRequestorCallback binderDeathCallback =
                mWifiServiceImpl.new LocalOnlyRequestorCallback();

        // registering a request directly from the test will not trigger a message to start
        // softap mode
        mWifiServiceImpl.registerLOHSForTest(mPid, mRequestInfo);
        mLooper.dispatchAll();

        setupLocalOnlyHotspot();

        binderDeathCallback.onLocalOnlyHotspotRequestorDeath(mRequestInfo);
        mLooper.dispatchAll();
        verify(mActiveModeWarden, never()).stopSoftAp(anyInt());

        reset(mActiveModeWarden);

        // now stop as the second request and confirm CMD_SET_AP will be sent to make sure binder
        // death requestor was removed
        mWifiServiceImpl.stopLocalOnlyHotspot();
        mLooper.dispatchAll();
        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
    }

    /**
     * Verify that a call to registerSoftApCallback throws a SecurityException if the caller does
     * not have neither NETWORK_SETTINGS nor MAINLINE_NETWORK_STACK permission.
     */
    @Test(expected = SecurityException.class)
    public void registerSoftApCallbackThrowsSecurityExceptionOnMissingPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);
        mWifiServiceImpl.registerSoftApCallback(mClientSoftApCallback);
    }

    /**
     * Verify that a call to registerSoftApCallback throws an IllegalArgumentException if the
     * parameters are not provided.
     */
    @Test
    public void registerSoftApCallbackThrowsIllegalArgumentExceptionOnInvalidArguments() {
        try {
            mWifiServiceImpl.registerSoftApCallback(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that a call to unregisterSoftApCallback throws a SecurityException if the caller does
     * not have neither NETWORK_SETTINGS nor MAINLINE_NETWORK_STACK permission.
     */
    @Test(expected = SecurityException.class)
    public void unregisterSoftApCallbackThrowsSecurityExceptionOnMissingPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);
        mWifiServiceImpl.unregisterSoftApCallback(mClientSoftApCallback);
    }

    /**
     * Verifies that we handle softap callback registration failure if we encounter an exception
     * while linking to death.
     */
    @Test
    public void registerSoftApCallbackFailureOnLinkToDeath() throws Exception {
        doThrow(new RemoteException())
                .when(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        mWifiServiceImpl.registerSoftApCallback(mClientSoftApCallback);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onStateChanged(any());
        verify(mClientSoftApCallback, never()).onConnectedClientsOrInfoChanged(
                any(), any(), anyBoolean(), anyBoolean());
        verify(mClientSoftApCallback, never()).onCapabilityChanged(any());
    }

    /**
     * Registers a soft AP callback, then verifies that the current soft AP state and num clients
     * are sent to caller immediately after callback is registered.
     */
    private void registerSoftApCallbackAndVerify(ISoftApCallback callback) throws Exception {
        mWifiServiceImpl.registerSoftApCallback(callback);
        mLooper.dispatchAll();
        verify(callback).onStateChanged(
                eq(new SoftApState(WIFI_AP_STATE_DISABLED, 0, null, null)));
        verify(callback).onConnectedClientsOrInfoChanged(new HashMap<String, SoftApInfo>(),
                new HashMap<String, List<WifiClient>>(), false, true);
        verify(callback).onCapabilityChanged(ApConfigUtil.updateCapabilityFromResource(mContext));
        // Don't need to invoke callback when register.
        verify(callback, never()).onBlockedClientConnecting(any(), anyInt());
    }

    /**
     * Verify that unregisterSoftApCallback removes callback from registered callbacks list
     */
    @Test
    public void unregisterSoftApCallbackRemovesCallback() throws Exception {
        registerSoftApCallbackAndVerify(mClientSoftApCallback);

        mWifiServiceImpl.unregisterSoftApCallback(mClientSoftApCallback);
        mLooper.dispatchAll();

        reset(mClientSoftApCallback);
        mStateMachineSoftApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onConnectedClientsOrInfoChanged(
                any(), any(), anyBoolean(), anyBoolean());
    }

    /**
     * Verify that unregisterSoftApCallback is no-op if callback not registered.
     */
    @Test
    public void unregisterSoftApCallbackDoesNotRemoveCallbackIfCallbackNotMatching()
            throws Exception {
        registerSoftApCallbackAndVerify(mClientSoftApCallback);

        mWifiServiceImpl.unregisterSoftApCallback(mAnotherSoftApCallback);
        mLooper.dispatchAll();
        mStateMachineSoftApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback).onConnectedClientsOrInfoChanged(
                eq(mTestSoftApInfos), eq(mTestSoftApClients), eq(false), eq(false));
    }

    /**
     * Registers two callbacks, remove one then verify the right callback is being called on events.
     */
    @Test
    public void correctCallbackIsCalledAfterAddingTwoCallbacksAndRemovingOne() throws Exception {
        WifiClient testWifiClient = new WifiClient(MacAddress.fromString("22:33:44:55:66:77"),
                WIFI_IFACE_NAME2);
        mWifiServiceImpl.registerSoftApCallback(mClientSoftApCallback);
        mLooper.dispatchAll();

        reset(mClientSoftApCallback);
        when(mClientSoftApCallback.asBinder()).thenReturn(mAppBinder);
        // Change state from default before registering the second callback
        SoftApState state = new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME);
        mStateMachineSoftApCallback.onStateChanged(state);
        mStateMachineSoftApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mStateMachineSoftApCallback.onBlockedClientConnecting(testWifiClient, 0);


        // Register another callback and verify the new state is returned in the immediate callback
        mWifiServiceImpl.registerSoftApCallback(mAnotherSoftApCallback);
        mLooper.dispatchAll();
        verify(mAnotherSoftApCallback).onStateChanged(eq(state));
        verify(mAnotherSoftApCallback).onConnectedClientsOrInfoChanged(
                eq(mTestSoftApInfos), eq(mTestSoftApClients), eq(false), eq(true));
        // Verify only first callback will receive onBlockedClientConnecting since it call after
        // first callback register but before another callback register.
        verify(mClientSoftApCallback).onBlockedClientConnecting(testWifiClient, 0);
        verify(mAnotherSoftApCallback, never()).onBlockedClientConnecting(testWifiClient, 0);

        // unregister the fisrt callback
        mWifiServiceImpl.unregisterSoftApCallback(mClientSoftApCallback);
        mLooper.dispatchAll();

        // Update soft AP state and verify the remaining callback receives the event
        state = new SoftApState(
                WIFI_AP_STATE_FAILED, SAP_START_FAILURE_NO_CHANNEL,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME);
        mStateMachineSoftApCallback.onStateChanged(state);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onStateChanged(eq(state));
        verify(mAnotherSoftApCallback).onStateChanged(eq(state));
    }

    /**
     * Verify that wifi service registers for callers BinderDeath event
     */
    @Test
    public void registersForBinderDeathOnRegisterSoftApCallback() throws Exception {
        registerSoftApCallbackAndVerify(mClientSoftApCallback);
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    /**
     * Verify that we un-register the soft AP callback on receiving BinderDied event.
     */
    @Test
    public void unregistersSoftApCallbackOnBinderDied() throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> drCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        registerSoftApCallbackAndVerify(mClientSoftApCallback);
        verify(mAppBinder).linkToDeath(drCaptor.capture(), anyInt());

        drCaptor.getValue().binderDied();
        mLooper.dispatchAll();
        reset(mClientSoftApCallback);
        // Verify callback is removed from the list as well
        Map<String, List<WifiClient>> mTestSoftApClients = mock(Map.class);
        Map<String, SoftApInfo> mTestSoftApInfos = mock(Map.class);
        mStateMachineSoftApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onConnectedClientsOrInfoChanged(
                any(), any(), anyBoolean(), anyBoolean());
    }

    /**
     * Verify that soft AP callback is called on NumClientsChanged event
     */
    @Test
    public void callsRegisteredCallbacksOnConnectedClientsChangedEvent() throws Exception {
        registerSoftApCallbackAndVerify(mClientSoftApCallback);

        mStateMachineSoftApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback).onConnectedClientsOrInfoChanged(
                eq(mTestSoftApInfos), eq(mTestSoftApClients), eq(false), eq(false));
    }

    /**
     * Verify that soft AP callback is called on SoftApStateChanged event
     */
    @Test
    public void callsRegisteredCallbacksOnSoftApStateChangedEvent() throws Exception {
        registerSoftApCallbackAndVerify(mClientSoftApCallback);

        SoftApState state = new SoftApState(
                WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME);
        mStateMachineSoftApCallback.onStateChanged(state);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback).onStateChanged(eq(state));
    }

    /**
     * Verify that mSoftApState and mSoftApNumClients in WifiServiceImpl are being updated on soft
     * Ap events, even when no callbacks are registered.
     */
    @Test
    public void updatesSoftApStateAndConnectedClientsOnSoftApEvents() throws Exception {
        WifiClient testWifiClient = new WifiClient(MacAddress.fromString("22:33:44:55:66:77"),
                WIFI_IFACE_NAME2);
        SoftApState state = new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME);
        mStateMachineSoftApCallback.onStateChanged(state);
        mStateMachineSoftApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mStateMachineSoftApCallback.onBlockedClientConnecting(testWifiClient, 0);

        // Register callback after num clients and soft AP are changed.
        mWifiServiceImpl.registerSoftApCallback(mClientSoftApCallback);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback).onStateChanged(eq(state));
        verify(mClientSoftApCallback).onConnectedClientsOrInfoChanged(
                eq(mTestSoftApInfos), eq(mTestSoftApClients), eq(false), eq(true));
        // Don't need to invoke callback when register.
        verify(mClientSoftApCallback, never()).onBlockedClientConnecting(any(), anyInt());
    }

    private class IntentFilterMatcher implements ArgumentMatcher<IntentFilter> {
        @Override
        public boolean matches(IntentFilter filter) {
            return filter.hasAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        }
    }

    /**
     * Verify that onFailed is called for registered LOHS callers on SAP_START_FAILURE_GENERAL.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApFailureGeneric() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        registerLOHSRequestFull();

        changeLohsState(WIFI_AP_STATE_FAILED, WIFI_AP_STATE_DISABLED, SAP_START_FAILURE_GENERAL);
        mLooper.dispatchAll();

        verify(mLohsCallback).onHotspotFailed(ERROR_GENERIC);
    }

    /**
     * Verify that onFailed is called for registered LOHS callers on SAP_START_FAILURE_NO_CHANNEL.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApFailureNoChannel() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        registerLOHSRequestFull();

        changeLohsState(WIFI_AP_STATE_FAILED,
                WIFI_AP_STATE_DISABLED, SAP_START_FAILURE_NO_CHANNEL);

        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotFailed(ERROR_NO_CHANNEL);
    }

    /**
     * Common setup for starting a LOHS.
     */
    private void setupLocalOnlyHotspot() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        registerLOHSRequestFull();

        changeLohsState(WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR);
        mWifiServiceImpl.updateInterfaceIpState(mLohsInterfaceName, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStarted(any());
    }

    /**
     * Verify that onStopped is called for registered LOHS callers when a callback is
     * received with WIFI_AP_STATE_DISABLING and LOHS was active.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApDisabling() throws Exception {
        setupLocalOnlyHotspot();

        changeLohsState(WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR);

        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStopped();
    }


    /**
     * Verify that onStopped is called for registered LOHS callers when a callback is
     * received with WIFI_AP_STATE_DISABLED and LOHS was enabled.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApDisabled() throws Exception {
        setupLocalOnlyHotspot();

        changeLohsState(WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR);

        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStopped();
    }

    /**
     * Verify that no callbacks are called for registered LOHS callers when a callback is
     * received and the softap started.
     */
    @Test
    public void testRegisteredCallbacksNotTriggeredOnSoftApStart() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        registerLOHSRequestFull();

        changeLohsState(WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR);

        mLooper.dispatchAll();
        verifyZeroInteractions(ignoreStubs(mLohsCallback));
    }

    /**
     * Verify that onStopped is called only once for registered LOHS callers when
     * callbacks are received with WIFI_AP_STATE_DISABLING and
     * WIFI_AP_STATE_DISABLED when LOHS was enabled.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnlyOnceWhenSoftApDisabling() throws Exception {
        setupLocalOnlyHotspot();

        changeLohsState(WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR);
        changeLohsState(WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR);

        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStopped();
    }

    /**
     * Verify that onFailed is called only once for registered LOHS callers when
     * callbacks are received with WIFI_AP_STATE_FAILED twice.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnlyOnceWhenSoftApFailsTwice() throws Exception {
        setupLocalOnlyHotspot();

        changeLohsState(WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC);
        changeLohsState(WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC);

        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotFailed(ERROR_GENERIC);
    }

    /**
     * Verify that onFailed is called for all registered LOHS callers when
     * callbacks are received with WIFI_AP_STATE_FAILED.
     */
    @Test
    public void testAllRegisteredCallbacksTriggeredWhenSoftApFails() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        // make an additional request for this test
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        registerLOHSRequestFull();

        changeLohsState(WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC);
        changeLohsState(WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC);

        verify(mRequestInfo).sendHotspotFailedMessage(ERROR_GENERIC);
        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotFailed(ERROR_GENERIC);
    }

    /**
     * Verify that onStopped is called for all registered LOHS callers when
     * callbacks are received with WIFI_AP_STATE_DISABLED when LOHS was
     * active.
     */
    @Test
    public void testAllRegisteredCallbacksTriggeredWhenSoftApStops() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mRequestInfo).sendHotspotStartedMessage(any());
        verify(mLohsCallback).onHotspotStarted(any());

        reset(mRequestInfo);
        clearInvocations(mLohsCallback);

        changeLohsState(WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR);
        changeLohsState(WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR);

        verify(mRequestInfo).sendHotspotStoppedMessage();
        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStopped();
    }

    /**
     * Verify that onFailed is called for all registered LOHS callers when
     * callbacks are received with WIFI_AP_STATE_DISABLED when LOHS was
     * not active.
     */
    @Test
    public void testAllRegisteredCallbacksTriggeredWhenSoftApStopsLOHSNotActive() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();

        verifyApRegistration();

        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);
        mWifiServiceImpl.registerLOHSForTest(TEST_PID2, mRequestInfo2);

        changeLohsState(WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR);
        changeLohsState(WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR);

        verify(mRequestInfo).sendHotspotFailedMessage(ERROR_GENERIC);
        verify(mRequestInfo2).sendHotspotFailedMessage(ERROR_GENERIC);
    }

    /**
     * Verify that if we do not have registered LOHS requestors and we receive an update that LOHS
     * is up and ready for use, we tell WifiController to tear it down.  This can happen if softap
     * mode fails to come up properly and we get an onFailed message for a tethering call and we
     * had registered callers for LOHS.
     */
    @Test
    public void testLOHSReadyWithoutRegisteredRequestsStopsSoftApMode() {
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();

        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
    }

    /**
     * Verify that all registered LOHS requestors are notified via a HOTSPOT_STARTED message that
     * the hotspot is up and ready to use.
     */
    @Test
    public void testRegisteredLocalOnlyHotspotRequestorsGetOnStartedCallbackWhenReady()
            throws Exception {
        SoftApConfiguration lohsConfig = createValidSoftApConfiguration();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                eq(mContext), eq(null), any())).thenReturn(lohsConfig);
        registerLOHSRequestFull();
        verify(mWifiApConfigStore).generateLocalOnlyHotspotConfig(
                eq(mContext), eq(null), any());
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mRequestInfo).sendHotspotStartedMessage(any(SoftApConfiguration.class));

        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStarted(notNull());
    }

    /**
     * Verify that if a LOHS is already active, a new call to register a request will trigger the
     * onStarted callback.
     */
    @Test
    public void testRegisterLocalOnlyHotspotRequestAfterAlreadyStartedGetsOnStartedCallback()
            throws Exception {
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);
        mLooper.dispatchAll();

        changeLohsState(WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR);
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        registerLOHSRequestFull();
        verify(mLohsCallback).onHotspotStarted(any());
    }

    /**
     * Verify that if a LOHS request is active and we receive an update with an ip mode
     * configuration error, callers are notified via the onFailed callback with the generic
     * error and are unregistered.
     */
    @Test
    public void testCallOnFailedLocalOnlyHotspotRequestWhenIpConfigFails() throws Exception {
        setupLocalOnlyHotspot();
        reset(mActiveModeWarden);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_CONFIGURATION_ERROR);
        mLooper.dispatchAll();

        verify(mLohsCallback).onHotspotFailed(ERROR_GENERIC);
        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);

        clearInvocations(mLohsCallback);

        // send HOTSPOT_FAILED message should only happen once since the requestor should be
        // unregistered
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_CONFIGURATION_ERROR);
        mLooper.dispatchAll();
        verifyZeroInteractions(ignoreStubs(mLohsCallback));
    }

    /**
     * Verify that softap mode is stopped for tethering if we receive an update with an ip mode
     * configuration error.
     */
    @Test
    public void testStopSoftApWhenIpConfigFails() throws Exception {
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_CONFIGURATION_ERROR);
        mLooper.dispatchAll();

        verify(mActiveModeWarden).stopSoftAp(IFACE_IP_MODE_TETHERED);
    }

    /**
     * Verify that if a LOHS request is active and tethering starts, callers are notified on the
     * incompatible mode and are unregistered.
     */
    @Test
    public void testCallOnFailedLocalOnlyHotspotRequestWhenTetheringStarts() throws Exception {
        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStarted(any());
        clearInvocations(mLohsCallback);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();

        verify(mLohsCallback).onHotspotFailed(ERROR_INCOMPATIBLE_MODE);

        // sendMessage should only happen once since the requestor should be unregistered
        clearInvocations(mLohsCallback);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        verifyZeroInteractions(ignoreStubs(mLohsCallback));
    }

    /**
     * Verify that if LOHS is disabled, a new call to register a request will not trigger the
     * onStopped callback.
     */
    @Test
    public void testRegisterLocalOnlyHotspotRequestWhenStoppedDoesNotGetOnStoppedCallback()
            throws Exception {
        registerLOHSRequestFull();
        verifyZeroInteractions(ignoreStubs(mLohsCallback));
    }

    /**
     * Verify that if a LOHS was active and then stopped, a new call to register a request will
     * not trigger the onStarted callback.
     */
    @Test
    public void testRegisterLocalOnlyHotspotRequestAfterStoppedNoOnStartedCallback()
            throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verifyApRegistration();

        // register a request so we don't drop the LOHS interface ip update
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);
        changeLohsState(WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR);
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();

        registerLOHSRequestFull();
        mLooper.dispatchAll();

        verify(mLohsCallback).onHotspotStarted(any());

        clearInvocations(mLohsCallback);

        // now stop the hotspot
        changeLohsState(WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR);
        changeLohsState(WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR);
        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotStopped();

        clearInvocations(mLohsCallback);

        // now register a new caller - they should not get the onStarted callback
        ILocalOnlyHotspotCallback callback2 = mock(ILocalOnlyHotspotCallback.class);
        when(callback2.asBinder()).thenReturn(mock(IBinder.class));

        int result = mWifiServiceImpl.startLocalOnlyHotspot(
                callback2, TEST_PACKAGE_NAME, TEST_FEATURE_ID, null, mExtras);
        assertEquals(LocalOnlyHotspotCallback.REQUEST_REGISTERED, result);
        mLooper.dispatchAll();

        verify(mLohsCallback, never()).onHotspotStarted(any());
        verify(mLastCallerInfoManager, atLeastOnce()).put(
                eq(WifiManager.API_START_LOCAL_ONLY_HOTSPOT), anyInt(), anyInt(), anyInt(),
                anyString(), eq(true));
    }

    /**
     * Verify that a call to startWatchLocalOnlyHotspot is only allowed from callers with the
     * signature only NETWORK_SETTINGS permission.
     *
     * This test is expecting the permission check to enforce the permission and throw a
     * SecurityException for callers without the permission.  This exception should be bubbled up to
     * the caller of startLocalOnlyHotspot.
     */
    @Test(expected = SecurityException.class)
    public void testStartWatchLocalOnlyHotspotNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                                                eq("WifiService"));
        mWifiServiceImpl.startWatchLocalOnlyHotspot(mLohsCallback);
    }

    /**
     * Verify that the call to startWatchLocalOnlyHotspot throws the UnsupportedOperationException
     * when called until the implementation is complete.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testStartWatchLocalOnlyHotspotNotSupported() {
        mWifiServiceImpl.startWatchLocalOnlyHotspot(mLohsCallback);
    }

    /**
     * Verify that a call to stopWatchLocalOnlyHotspot is only allowed from callers with the
     * signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testStopWatchLocalOnlyHotspotNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                                                eq("WifiService"));
        mWifiServiceImpl.stopWatchLocalOnlyHotspot();
    }

    /**
     * Verify that the call to stopWatchLocalOnlyHotspot throws the UnsupportedOperationException
     * until the implementation is complete.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testStopWatchLocalOnlyHotspotNotSupported() {
        mWifiServiceImpl.stopWatchLocalOnlyHotspot();
    }

    /**
     * Verify that the call to addOrUpdateNetwork for installing Passpoint profile is redirected
     * to the Passpoint specific API addOrUpdatePasspointConfiguration.
     */
    @Test
    public void testAddPasspointProfileViaAddNetwork() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);

        PackageManager pm = mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(pm);
        when(pm.getApplicationInfoAsUser(any(), anyInt(), any())).thenReturn(mApplicationInfo);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(true);

        when(mPasspointManager.addOrUpdateProvider(
                any(PasspointConfiguration.class), anyInt(), eq(TEST_PACKAGE_NAME), eq(false),
                eq(true), eq(false))).thenReturn(true);
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mPasspointManager).addOrUpdateProvider(
                any(PasspointConfiguration.class), anyInt(), eq(TEST_PACKAGE_NAME), eq(false),
                eq(true), eq(false));
        reset(mPasspointManager);

        when(mPasspointManager.addOrUpdateProvider(
                any(PasspointConfiguration.class), anyInt(), eq(TEST_PACKAGE_NAME), anyBoolean(),
                anyBoolean(), eq(false))).thenReturn(false);
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mPasspointManager).addOrUpdateProvider(
                any(PasspointConfiguration.class), anyInt(), eq(TEST_PACKAGE_NAME), anyBoolean(),
                anyBoolean(), eq(false));
    }

    /**
     * Verify that the call to getAllMatchingPasspointProfilesForScanResults is not redirected to
     * specific API getAllMatchingPasspointProfilesForScanResults when the caller doesn't have
     * NETWORK_SETTINGS permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testGetAllMatchingPasspointProfilesForScanResultsWithoutPermissions() {
        mWifiServiceImpl.getAllMatchingPasspointProfilesForScanResults(new ArrayList<>());
    }

    /**
     * Verify that the call to getAllMatchingPasspointProfilesForScanResults is redirected to
     * specific API getAllMatchingPasspointProfilesForScanResults when the caller have
     * NETWORK_SETTINGS permissions and NETWORK_SETUP_WIZARD.
     */
    @Test
    public void testGetAllMatchingPasspointProfilesForScanResultsWithPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        mWifiServiceImpl.getAllMatchingPasspointProfilesForScanResults(createScanResultList());
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).getAllMatchingPasspointProfilesForScanResults(any());
    }

    /**
     * Verify that the call to getAllMatchingPasspointProfilesForScanResults is not redirected to
     * specific API getAllMatchingPasspointProfilesForScanResults when the caller provider invalid
     * ScanResult.
     */
    @Test
    public void testGetAllMatchingPasspointProfilesForScanResultsWithInvalidScanResult() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        mWifiServiceImpl.getAllMatchingPasspointProfilesForScanResults(new ArrayList<>());
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager, never()).getAllMatchingPasspointProfilesForScanResults(any());
    }

    /**
     * Verify that the call to getWifiConfigsForPasspointProfiles is not redirected to specific API
     * syncGetWifiConfigsForPasspointProfiles when the caller doesn't have NETWORK_SETTINGS
     * permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testGetWifiConfigsForPasspointProfilesWithoutPermissions() {
        mWifiServiceImpl.getWifiConfigsForPasspointProfiles(new ArrayList<>());
    }

    /**
     * Verify that the call to getMatchingOsuProviders is not redirected to specific API
     * syncGetMatchingOsuProviders when the caller doesn't have NETWORK_SETTINGS
     * permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testGetMatchingOsuProvidersWithoutPermissions() {
        mWifiServiceImpl.getMatchingOsuProviders(new ArrayList<>());
    }

    /**
     * Verify that the call to getMatchingOsuProviders is redirected to specific API
     * syncGetMatchingOsuProviders when the caller have NETWORK_SETTINGS
     * permissions and NETWORK_SETUP_WIZARD.
     */
    @Test
    public void testGetMatchingOsuProvidersWithPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        mWifiServiceImpl.getMatchingOsuProviders(createScanResultList());
        mLooper.stopAutoDispatch();
        verify(mPasspointManager).getMatchingOsuProviders(any());
    }

    /**
     * Verify that the call to getMatchingOsuProviders is not redirected to specific API
     * syncGetMatchingOsuProviders when the caller provider invalid ScanResult
     */
    @Test
    public void testGetMatchingOsuProvidersWithInvalidScanResult() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.getMatchingOsuProviders(new ArrayList<>());
        mLooper.dispatchAll();
        verify(mPasspointManager, never()).getMatchingOsuProviders(any());
    }

    /**
     * Verify that the call to getMatchingPasspointConfigsForOsuProviders is not redirected to
     * specific API syncGetMatchingPasspointConfigsForOsuProviders when the caller doesn't have
     * NETWORK_SETTINGS permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testGetMatchingPasspointConfigsForOsuProvidersWithoutPermissions() {
        mWifiServiceImpl.getMatchingPasspointConfigsForOsuProviders(new ArrayList<>());
    }

    /**
     * Verify that the call to startSubscriptionProvisioning is redirected to the Passpoint
     * specific API startSubscriptionProvisioning when the caller has the right permissions.
     */
    @Test
    public void testStartSubscriptionProvisioningWithPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.startSubscriptionProvisioning(mOsuProvider, mProvisioningCallback);
        mLooper.stopAutoDispatch();
        verify(mClientModeManager).syncStartSubscriptionProvisioning(anyInt(),
                eq(mOsuProvider), eq(mProvisioningCallback));
    }

    /**
     * Verify that the call to startSubscriptionProvisioning is not redirected to the Passpoint
     * specific API startSubscriptionProvisioning when the caller provides invalid arguments
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStartSubscriptionProvisioningWithInvalidProvider() throws Exception {
        mWifiServiceImpl.startSubscriptionProvisioning(null, mProvisioningCallback);
    }


    /**
     * Verify that the call to startSubscriptionProvisioning is not redirected to the Passpoint
     * specific API startSubscriptionProvisioning when the caller provides invalid callback
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStartSubscriptionProvisioningWithInvalidCallback() throws Exception {
        mWifiServiceImpl.startSubscriptionProvisioning(mOsuProvider, null);
    }

    /**
     * Verify that the call to startSubscriptionProvisioning is not redirected to the Passpoint
     * specific API startSubscriptionProvisioning when the caller doesn't have NETWORK_SETTINGS
     * permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testStartSubscriptionProvisioningWithoutPermissions() throws Exception {
        when(mContext.checkCallingOrSelfPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS))).thenReturn(
                PackageManager.PERMISSION_DENIED);
        when(mContext.checkSelfPermission(
                eq(android.Manifest.permission.NETWORK_SETUP_WIZARD))).thenReturn(
                PackageManager.PERMISSION_DENIED);

        mWifiServiceImpl.startSubscriptionProvisioning(mOsuProvider, mProvisioningCallback);
    }

    /**
     * Verify the call to getPasspointConfigurations when the caller doesn't have
     * NETWORK_SETTINGS and NETWORK_SETUP_WIZARD permissions.
     */
    @Test
    public void testGetPasspointConfigurationsWithOutPrivilegedPermissions() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.getPasspointConfigurations(TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).getProviderConfigs(Binder.getCallingUid(), false);
    }

    /**
     * Verify that the call to getPasspointConfigurations when the caller does have
     * NETWORK_SETTINGS permission.
     */
    @Test
    public void testGetPasspointConfigurationsWithPrivilegedPermissions() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.getPasspointConfigurations(TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).getProviderConfigs(Binder.getCallingUid(), true);
    }

    /**
     * Verify that GetPasspointConfigurations will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager}.
     */
    @Test
    public void testGetPasspointConfigurations() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        // Setup expected configs.
        List<PasspointConfiguration> expectedConfigs = new ArrayList<>();
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);
        expectedConfigs.add(config);

        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(expectedConfigs);
        mLooper.startAutoDispatch();
        assertEquals(expectedConfigs, mWifiServiceImpl.getPasspointConfigurations(TEST_PACKAGE));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        reset(mPasspointManager);

        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<PasspointConfiguration>());
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.getPasspointConfigurations(TEST_PACKAGE).isEmpty());
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify the call to removePasspointConfigurations when the caller doesn't have
     * NETWORK_SETTINGS and NETWORK_CARRIER_PROVISIONING permissions.
     */
    @Test
    public void testRemovePasspointConfigurationWithOutPrivilegedPermissions() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt())).thenReturn(
                false);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.removePasspointConfiguration(TEST_FQDN, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).removeProvider(Binder.getCallingUid(), false, null,
                TEST_FQDN);
    }

    /**
     * Verify the call to removePasspointConfigurations when the caller does have
     * NETWORK_CARRIER_PROVISIONING permission.
     */
    @Test
    public void testRemovePasspointConfigurationWithPrivilegedPermissions() {
        when(mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(anyInt())).thenReturn(
                true);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.removePasspointConfiguration(TEST_FQDN, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).removeProvider(Binder.getCallingUid(), true, null,
                TEST_FQDN);
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#restoreBackupData(byte[])} is only allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRestoreBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.restoreBackupData(null);
        verify(mWifiBackupRestore, never()).retrieveConfigurationsFromBackupData(any(byte[].class));
    }

    private void testRestoreNetworkConfiguration(int configNum, int batchNum,
            boolean allowOverride) {
        List<WifiConfiguration> configurations = new ArrayList<>();
        when(mResources.getInteger(
                eq(R.integer.config_wifiConfigurationRestoreNetworksBatchNum)))
                .thenReturn(batchNum);
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        for (int i = 0; i < configNum; i++) {
            configurations.add(config);
        }
        reset(mWifiConfigManager);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        when(mWifiConfigManager.addNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        mWifiServiceImpl.restoreNetworks(configurations);
        mLooper.dispatchAll();
        if (allowOverride) {
            verify(mWifiConfigManager, times(configNum)).addOrUpdateNetwork(eq(config), anyInt());
            verify(mWifiConfigManager, never()).addNetwork(eq(config), anyInt());
        } else {
            verify(mWifiConfigManager, never()).addOrUpdateNetwork(eq(config), anyInt());
            verify(mWifiConfigManager, times(configNum)).addNetwork(eq(config), anyInt());
        }
        verify(mWifiConfigManager, times(configNum)).enableNetwork(
                eq(TEST_NETWORK_ID), eq(false), anyInt(), eq(null));
        verify(mWifiConfigManager, times(configNum)).allowAutojoin(eq(TEST_NETWORK_ID),
                anyBoolean());
    }

    /**
     * Verify that a call to
     * {@link WifiServiceImpl#restoreNetworks(List)}
     * trigeering the process of the network restoration in batches.
     */
    @Test
    public void testRestoreNetworksWithBatchOverrideDisallowed() {
        lenient().when(CompatChanges.isChangeEnabled(eq(NOT_OVERRIDE_EXISTING_NETWORKS_ON_RESTORE),
                anyInt())).thenReturn(true);
        testRestoreNetworkConfiguration(0 /* configNum */, 50 /* batchNum*/, false);
        testRestoreNetworkConfiguration(1 /* configNum */, 50 /* batchNum*/, false);
        testRestoreNetworkConfiguration(20 /* configNum */, 50 /* batchNum*/, false);
        testRestoreNetworkConfiguration(700 /* configNum */, 50 /* batchNum*/, false);
        testRestoreNetworkConfiguration(700 /* configNum */, 0 /* batchNum*/, false);
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#restoreSupplicantBackupData(byte[], byte[])} is
     * only allowed from callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRestoreSupplicantBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.restoreSupplicantBackupData(null, null);
        verify(mWifiBackupRestore, never()).retrieveConfigurationsFromSupplicantBackupData(
                any(byte[].class), any(byte[].class));
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#retrieveBackupData()} is only allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRetrieveBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.retrieveBackupData();
        verify(mWifiBackupRestore, never()).retrieveBackupDataFromConfigurations(any(List.class));
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#restoreSoftApBackupData(byte[])}
     * is only allowed from callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRestoreSoftApBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.restoreSoftApBackupData(null);
        verify(mSoftApBackupRestore, never())
                .retrieveSoftApConfigurationFromBackupData(any(byte[].class));
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#restoreSoftApBackupData(byte[])}
     * will call WifiApConfigStore#upgradeSoftApConfiguration and
     * WifiApConfigStore#resetToDefaultForUnsupportedConfig.
     */
    @Test
    public void testRestoreSoftApBackupData() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
            anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        InOrder inorder = inOrder(mWifiApConfigStore);
        SoftApConfiguration testConfig = new SoftApConfiguration.Builder()
                .setSsid("test").build();
        byte[] testData = testConfig.toString().getBytes();
        when(mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(testData))
                .thenReturn(testConfig);
        mWifiServiceImpl.restoreSoftApBackupData(testData);
        mLooper.dispatchAll();
        inorder.verify(mWifiApConfigStore).upgradeSoftApConfiguration(testConfig);
        inorder.verify(mWifiApConfigStore).resetToDefaultForUnsupportedConfig(any());
        inorder.verify(mWifiApConfigStore).setApConfiguration(any());
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#retrieveSoftApBackupData()} is only allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRetrieveSoftApBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.retrieveSoftApBackupData();
        verify(mSoftApBackupRestore, never())
                .retrieveBackupDataFromSoftApConfiguration(any(SoftApConfiguration.class));
    }

    class TestWifiVerboseLoggingStatusChangedListener extends
            IWifiVerboseLoggingStatusChangedListener.Stub {
        public int numStatusChangedCounts;
        public boolean lastReceivedValue;
        @Override
        public void onStatusChanged(boolean enabled) throws RemoteException {
            numStatusChangedCounts++;
            lastReceivedValue = enabled;
        }
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#enableVerboseLogging(int)} is propagated to
     * registered {@link IWifiVerboseLoggingStatusChangedListener}. Then, verify that changes are no
     * longer propagated when the listener gets unregistered.
     */
    @Test
    public void testVerboseLoggingListener() throws Exception {
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        // Verbose logging is enabled first in the constructor for WifiServiceImpl, so reset
        // before invocation.
        reset(mClientModeManager);
        TestWifiVerboseLoggingStatusChangedListener listener =
                new TestWifiVerboseLoggingStatusChangedListener();
        mWifiServiceImpl.addWifiVerboseLoggingStatusChangedListener(listener);
        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED);
        verify(mWifiSettingsConfigStore).put(WIFI_VERBOSE_LOGGING_ENABLED, true);
        verify(mActiveModeWarden).enableVerboseLogging(anyBoolean());
        assertEquals(1, listener.numStatusChangedCounts);
        assertTrue(listener.lastReceivedValue);

        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED);
        assertEquals(2, listener.numStatusChangedCounts);
        assertFalse(listener.lastReceivedValue);

        // unregister the callback and verify no more updates happen.
        mWifiServiceImpl.removeWifiVerboseLoggingStatusChangedListener(listener);
        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED);
        assertEquals(2, listener.numStatusChangedCounts);
        assertFalse(listener.lastReceivedValue);
    }

    /**
     * Verify an exception is thrown for invalid inputs to
     * addWifiVerboseLoggingStatusChangedListener and removeWifiVerboseLoggingStatusChangedListener.
     */
    @Test
    public void testVerboseLoggingListenerInvalidInput() throws Exception {
        try {
            mWifiServiceImpl.addWifiVerboseLoggingStatusChangedListener(null);
            fail("expected IllegalArgumentException in addWifiVerboseLoggingStatusChangedListener");
        } catch (IllegalArgumentException e) {
        }
        try {
            mWifiServiceImpl.removeWifiVerboseLoggingStatusChangedListener(null);
            fail("expected IllegalArgumentException in "
                    + "removeWifiVerboseLoggingStatusChangedListener");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Verify a SecurityException if the caller doesn't have sufficient permissions.
     */
    @Test
    public void testVerboseLoggingListenerNoPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE),
                        eq("WifiService"));
        TestWifiVerboseLoggingStatusChangedListener listener =
                new TestWifiVerboseLoggingStatusChangedListener();
        try {
            mWifiServiceImpl.addWifiVerboseLoggingStatusChangedListener(listener);
            fail("expected IllegalArgumentException in addWifiVerboseLoggingStatusChangedListener");
        } catch (SecurityException e) {
        }
        try {
            mWifiServiceImpl.removeWifiVerboseLoggingStatusChangedListener(listener);
            fail("expected IllegalArgumentException in "
                    + "removeWifiVerboseLoggingStatusChangedListener");
        } catch (SecurityException e) {
        }
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#enableVerboseLogging(int)} is allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test
    public void testEnableVerboseLoggingWithNetworkSettingsPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        // Verbose logging is enabled first in the constructor for WifiServiceImpl, so reset
        // before invocation.
        reset(mClientModeManager);
        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED);
        verify(mWifiSettingsConfigStore).put(WIFI_VERBOSE_LOGGING_ENABLED, true);
        verify(mActiveModeWarden).enableVerboseLogging(anyBoolean());
        assertTrue(mWifiThreadRunner.mVerboseLoggingEnabled);
    }

    /**
     * Verify that setting verbose logging mode to
     * {@link WifiManager#VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY)} is allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test
    public void testEnableShowKeyVerboseLoggingWithNetworkSettingsPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        // Verbose logging is enabled first in the constructor for WifiServiceImpl, so reset
        // before invocation.
        reset(mClientModeManager);
        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY);
        verify(mWifiSettingsConfigStore).put(WIFI_VERBOSE_LOGGING_ENABLED, true);
        verify(mActiveModeWarden).enableVerboseLogging(anyBoolean());
        verify(mWifiGlobals).setVerboseLoggingLevel(
                eq(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY));

        // After auto disable show key mode after the countdown
        mLooper.moveTimeForward(WifiServiceImpl.AUTO_DISABLE_SHOW_KEY_COUNTDOWN_MILLIS + 1);
        mLooper.dispatchAll();
        verify(mWifiGlobals).setVerboseLoggingLevel(eq(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED));
    }

    /**
     * Verify that setting verbose logging level to
     * {@link WifiManager#VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY)} is not allowed for
     * the user build.
     */
    @Test(expected = SecurityException.class)
    public void testEnableShowKeyVerboseLoggingNotAllowedForUserBuild() throws Exception {
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        // Verbose logging is enabled first in the constructor for WifiServiceImpl, so reset
        // before invocation.
        reset(mClientModeManager);
        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY);
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#enableVerboseLogging(int)} is not allowed from
     * callers without the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testEnableVerboseLoggingWithNoNetworkSettingsPermission() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(android.Manifest.permission.DUMP),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        // Vebose logging is enabled first in the constructor for WifiServiceImpl, so reset
        // before invocation.
        reset(mClientModeManager);
        mWifiServiceImpl.enableVerboseLogging(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED);
        verify(mWifiSettingsConfigStore, never()).put(
                WIFI_VERBOSE_LOGGING_ENABLED, anyBoolean());
        verify(mActiveModeWarden, never()).enableVerboseLogging(anyBoolean());
    }

    /**
     * Verify that the CONNECT_NETWORK message received from an app without
     * one of the privileged permission is rejected with a security exception.
     */
    @Test
    public void testConnectNetworkWithoutPrivilegedPermission() throws Exception {
        try {
            mWifiServiceImpl.connect(mock(WifiConfiguration.class), TEST_NETWORK_ID,
                    mock(IActionListener.class), TEST_PACKAGE_NAME, mExtras);
            fail();
        } catch (SecurityException e) {
            mLooper.dispatchAll();
            verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        }
    }

    /**
     * Verify that the FORGET_NETWORK message received from an app without
     * one of the privileged permission is rejected with a security exception.
     */
    @Test
    public void testForgetNetworkWithoutPrivilegedPermission() throws Exception {
        try {
            mWifiServiceImpl.forget(TEST_NETWORK_ID, mock(IActionListener.class));
            fail();
        } catch (SecurityException e) {
            mLooper.dispatchAll();
            verify(mWifiConfigManager, never()).removeNetwork(anyInt(), anyInt(), any());
        }
    }

    /**
     * Verify that the SAVE_NETWORK message received from an app without
     * one of the privileged permission is rejected with a security exception.
     */
    @Test
    public void testSaveNetworkWithoutPrivilegedPermission() throws Exception {
        try {
            mWifiServiceImpl.save(mock(WifiConfiguration.class), mock(IActionListener.class),
                    TEST_PACKAGE_NAME);
            fail();
        } catch (SecurityException e) {
            mLooper.dispatchAll();
            verify(mWifiConfigManager, never()).updateBeforeSaveNetwork(any(), anyInt(), any());
        }
    }

    /**
     * Verify that the CONNECT_NETWORK message received from an app with
     * one of the privileged permission is forwarded to ClientModeManager.
     */
    @Test
    public void testConnectNetworkWithPrivilegedPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
            anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        mWifiServiceImpl.connect(config, TEST_NETWORK_ID, mock(IActionListener.class),
                TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).addOrUpdateNetwork(eq(config), anyInt());
        verify(mConnectHelper).connectToNetwork(any(NetworkUpdateResult.class),
                any(ActionListenerWrapper.class), anyInt(), any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_ADD_OR_UPDATE_NETWORK),
                anyInt());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_CONNECT_CONFIG), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify the secondary internet CMM is stopped when explicit connection is initiated on the
     * primary.
     */
    @Test
    public void testConnectNetworkStopConnectedSecondaryInternetCmm() throws Exception {
        // grant permissions to access WifiServiceImpl#connect
        when(mContext.checkPermission(
                        eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        WifiConfiguration config = WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork();
        config.SSID = TEST_SSID;
        WifiConfiguration otherConfig = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);

        // Mock ActiveModeWarden to return a primary CMM and a secondary CMM to be already
        // connected to the target network.
        List<ClientModeManager> clientModeManagers = new ArrayList<>();
        ClientModeManager primaryCmm = mock(ClientModeManager.class);
        when(primaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        ConcreteClientModeManager secondaryInternetCmm = mock(ConcreteClientModeManager.class);
        when(secondaryInternetCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(secondaryInternetCmm.isSecondaryInternet()).thenReturn(true);
        when(secondaryInternetCmm.isConnected()).thenReturn(true);
        when(secondaryInternetCmm.getConnectedWifiConfiguration()).thenReturn(otherConfig);
        clientModeManagers.add(primaryCmm);
        clientModeManagers.add(secondaryInternetCmm);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(clientModeManagers);

        // Verify that the secondary internet CMM is stopped when manual connection is started
        mWifiServiceImpl.connect(
                config, TEST_NETWORK_ID, mock(IActionListener.class), TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(primaryCmm, never()).stop();
        verify(secondaryInternetCmm).stop();
    }

    /**
     * Verify that the CONNECT_NETWORK message received from an app with
     * one of the privileged permission will stop secondary CMMs that are alraedy connected to
     * the same network before initiating the connection.
     */
    @Test
    public void testConnectNetworkStopSecondaryCmmOnSameNetwork() throws Exception {
        // grant permissions to access WifiServiceImpl#connect
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        WifiConfiguration config = WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork();
        config.SSID = TEST_SSID;
        WifiConfiguration localOnlyConfig = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);

        // Mock ActiveModeWarden to return a primary CMM and a secondary CMM to be already
        // connected to the target network.
        List<ClientModeManager> clientModeManagers = new ArrayList<>();
        ClientModeManager primaryCmm = mock(ClientModeManager.class);
        when(primaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        ConcreteClientModeManager localOnlyCmm = mock(ConcreteClientModeManager.class);
        when(localOnlyCmm.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);
        when(localOnlyCmm.isConnected()).thenReturn(true);
        when(localOnlyCmm.getConnectedWifiConfiguration()).thenReturn(localOnlyConfig);
        clientModeManagers.add(primaryCmm);
        clientModeManagers.add(localOnlyCmm);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(clientModeManagers);

        // Verify that the localOnlyCmm is not stopped since security type is different
        mWifiServiceImpl.connect(config, TEST_NETWORK_ID, mock(IActionListener.class),
                TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(primaryCmm, never()).stop();
        verify(localOnlyCmm, never()).stop();
        verify(mWifiConfigManager).addOrUpdateNetwork(eq(config), anyInt());
        verify(mConnectHelper).connectToNetwork(any(NetworkUpdateResult.class),
                any(ActionListenerWrapper.class), anyInt(), any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_ADD_OR_UPDATE_NETWORK),
                anyInt());

        // update mock so that the localOnlyConfig matches with target config.
        localOnlyConfig = WifiConfigurationTestUtil.createWpa3EnterpriseNetwork(TEST_SSID);
        when(localOnlyCmm.getConnectedWifiConfiguration()).thenReturn(localOnlyConfig);

        // Verify that the localOnlyCmm is stopped this time
        mWifiServiceImpl.connect(config, TEST_NETWORK_ID, mock(IActionListener.class),
                TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(primaryCmm, never()).stop();
        verify(localOnlyCmm).stop();
        verify(mWifiConfigManager, times(2)).addOrUpdateNetwork(eq(config), anyInt());
        verify(mConnectHelper, times(2)).connectToNetwork(any(NetworkUpdateResult.class),
                any(ActionListenerWrapper.class), anyInt(), any(), any());
        verify(mWifiMetrics, times(2)).logUserActionEvent(
                eq(UserActionEvent.EVENT_ADD_OR_UPDATE_NETWORK), anyInt());
    }

    @Test
    public void connectToNewNetwork_success() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        NetworkUpdateResult result = new NetworkUpdateResult(TEST_NETWORK_ID);
        when(mWifiConfigManager.addOrUpdateNetwork(eq(mWifiConfig), anyInt()))
                .thenReturn(result);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);

        mWifiServiceImpl.connect(mWifiConfig, WifiConfiguration.INVALID_NETWORK_ID,
                mActionListener, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();

        ArgumentCaptor<WifiConfiguration> configCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(configCaptor.capture(), anyInt());
        assertThat(configCaptor.getValue().networkId).isEqualTo(TEST_NETWORK_ID);

        verify(mConnectHelper).connectToNetwork(eq(result), any(), anyInt(), any(), any());
        verify(mContextAsUser).sendBroadcastWithMultiplePermissions(
                mIntentCaptor.capture(),
                aryEq(new String[]{
                        android.Manifest.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                }));

        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.WIFI_CREDENTIAL_CHANGED_ACTION);
        assertThat(intent.getStringExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_SSID))
                .isEqualTo(TEST_SSID);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_EVENT_TYPE, -1))
                .isEqualTo(WifiManager.WIFI_CREDENTIAL_SAVED);
    }

    @Test
    public void connectToNewNetwork_failure() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(eq(mWifiConfig), anyInt()))
                .thenReturn(NetworkUpdateResult.makeFailed());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        mWifiServiceImpl.connect(mWifiConfig, WifiConfiguration.INVALID_NETWORK_ID,
                mActionListener, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).addOrUpdateNetwork(eq(mWifiConfig), anyInt());

        verify(mClientModeManager, never()).connectNetwork(any(), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mActionListener).onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
        verify(mActionListener, never()).onSuccess();
    }

    @Test
    public void connectToExistingNetwork() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);

        mWifiServiceImpl.connect(null, TEST_NETWORK_ID, mActionListener, TEST_PACKAGE_NAME,
                mExtras);
        mLooper.dispatchAll();

        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt());

        verify(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_MANUAL_CONNECT), anyInt());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_CONNECT_NETWORK_ID), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    @Test
    public void connectToSimBasedNetworkWhenSimPresent() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(true);

        mWifiServiceImpl.connect(null, TEST_NETWORK_ID, mActionListener, TEST_PACKAGE_NAME,
                mExtras);
        mLooper.dispatchAll();

        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt());

        verify(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_MANUAL_CONNECT), anyInt());
    }

    @Test
    public void connectToSimBasedNetworkWhenSimAbsent() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(false);

        mWifiServiceImpl.connect(null, TEST_NETWORK_ID, mActionListener, TEST_PACKAGE_NAME,
                mExtras);
        mLooper.dispatchAll();

        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt());

        verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_MANUAL_CONNECT), anyInt());
    }

    @Test
    public void connectToSimBasedNetworkRequiresImsiEncryptionButNotReady() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(false);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(TEST_SUB_ID)).thenReturn(false);

        mWifiServiceImpl.connect(null, TEST_NETWORK_ID, mActionListener, TEST_PACKAGE_NAME,
                mExtras);
        mLooper.dispatchAll();

        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt());

        verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_MANUAL_CONNECT), anyInt());
    }

    @Test
    public void connectToSimBasedNetworkOobPseudonymEnabledButNotAvailable() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        when(mWifiPseudonymManager.getValidPseudonymInfo(anyInt())).thenReturn(Optional.empty());

        mWifiServiceImpl.connect(null, TEST_NETWORK_ID, mActionListener, TEST_PACKAGE_NAME,
                mExtras);
        mLooper.dispatchAll();

        verify(mWifiPseudonymManager).retrievePseudonymOnFailureTimeoutExpired(any());
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt());

        verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_MANUAL_CONNECT), anyInt());
    }

    @Test
    public void connectToSimBasedNetworkWhenOobPseudonymEnabledAndAvailable() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        when(mWifiPseudonymManager.getValidPseudonymInfo(anyInt()))
                .thenReturn(Optional.of(mock(PseudonymInfo.class)));

        mWifiServiceImpl.connect(null, TEST_NETWORK_ID, mActionListener, TEST_PACKAGE_NAME,
                mExtras);
        mLooper.dispatchAll();

        verify(mWifiPseudonymManager).updateWifiConfiguration(any());
        verify(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_MANUAL_CONNECT), anyInt());
    }

    /**
     * Verify that connecting to an admin restricted network fails to connect but saves the network
     */
    @Test
    public void connectToAdminRestrictedNetwork_failure() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        NetworkUpdateResult result = new NetworkUpdateResult(TEST_NETWORK_ID);
        when(mWifiConfigManager.addOrUpdateNetwork(eq(mWifiConfig), anyInt()))
                .thenReturn(result);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);
        when(mWifiPermissionsUtil.isAdminRestrictedNetwork(mWifiConfig)).thenReturn(true);

        mWifiServiceImpl.connect(mWifiConfig, WifiConfiguration.INVALID_NETWORK_ID,
                mActionListener, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();

        ArgumentCaptor<WifiConfiguration> configCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(configCaptor.capture(), anyInt());
        assertThat(configCaptor.getValue().networkId).isEqualTo(TEST_NETWORK_ID);

        verify(mWifiConfigManager).addOrUpdateNetwork(eq(mWifiConfig), anyInt());
        verify(mClientModeManager, never()).connectNetwork(any(), any(), anyInt(), any(), any());
        verify(mActionListener).onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
        verify(mActionListener, never()).onSuccess();
    }

    /**
     * Verify that the SAVE_NETWORK message received from an app with
     * one of the privileged permission is forwarded to ClientModeManager.
     */
    @Test
    public void testSaveNetworkWithPrivilegedPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
            anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiConfigManager.updateBeforeSaveNetwork(any(), anyInt(), any()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        mWifiServiceImpl.save(mock(WifiConfiguration.class), mock(IActionListener.class),
                TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).updateBeforeSaveNetwork(any(WifiConfiguration.class), anyInt(),
                any());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_ADD_OR_UPDATE_NETWORK),
                anyInt());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SAVE), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    @Test
    public void saveNetwork_success() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        NetworkUpdateResult result = new NetworkUpdateResult(TEST_NETWORK_ID);
        when(mWifiConfigManager.updateBeforeSaveNetwork(eq(mWifiConfig), anyInt(), any()))
                .thenReturn(result);

        mWifiServiceImpl.save(mWifiConfig, mActionListener, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateBeforeSaveNetwork(eq(mWifiConfig), anyInt(), any());

        verify(mClientModeManager).saveNetwork(eq(result), any(), anyInt(), any());
        verify(mContextAsUser).sendBroadcastWithMultiplePermissions(
                mIntentCaptor.capture(),
                aryEq(new String[]{
                        android.Manifest.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                }));

        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.WIFI_CREDENTIAL_CHANGED_ACTION);
        assertThat(intent.getStringExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_SSID))
                .isEqualTo(TEST_SSID);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_EVENT_TYPE, -1))
                .isEqualTo(WifiManager.WIFI_CREDENTIAL_SAVED);
    }

    @Test
    public void saveNetwork_failure() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.updateBeforeSaveNetwork(eq(mWifiConfig), anyInt(), any()))
                .thenReturn(NetworkUpdateResult.makeFailed());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        mWifiServiceImpl.save(mWifiConfig, mActionListener, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateBeforeSaveNetwork(eq(mWifiConfig), anyInt(), any());

        verify(mClientModeManager, never()).saveNetwork(any(), any(), anyInt(), any());
        verify(mContext, never()).sendBroadcastWithMultiplePermissions(any(), any());
        verify(mActionListener).onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
        verify(mActionListener, never()).onSuccess();
    }

    /**
     * Verify that the FORGET_NETWORK message received from an app with
     * one of the privileged permission is forwarded to ClientModeManager.
     */
    @Test
    public void testForgetNetworkWithPrivilegedPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
            anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiConfigManager.removeNetwork(anyInt(), anyInt(), any())).thenReturn(true);
        mWifiServiceImpl.forget(TEST_NETWORK_ID, mock(IActionListener.class));

        InOrder inOrder = inOrder(mWifiConfigManager, mWifiMetrics);
        inOrder.verify(mWifiMetrics).logUserActionEvent(
                UserActionEvent.EVENT_FORGET_WIFI, TEST_NETWORK_ID);

        mLooper.dispatchAll();
        inOrder.verify(mWifiConfigManager).removeNetwork(anyInt(), anyInt(), any());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_FORGET), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    @Test
    public void forgetNetwork_success() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);
        when(mWifiConfigManager.removeNetwork(eq(TEST_NETWORK_ID), anyInt(), any()))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        mWifiServiceImpl.forget(TEST_NETWORK_ID, mActionListener);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).removeNetwork(eq(TEST_NETWORK_ID), anyInt(), any());
        verify(mActionListener).onSuccess();
        verify(mActionListener, never())
                .onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);

        verify(mContextAsUser).sendBroadcastWithMultiplePermissions(
                mIntentCaptor.capture(),
                aryEq(new String[]{
                        android.Manifest.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                }));

        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.WIFI_CREDENTIAL_CHANGED_ACTION);
        assertThat(intent.getStringExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_SSID))
                .isEqualTo(TEST_SSID);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_EVENT_TYPE, -1))
                .isEqualTo(WifiManager.WIFI_CREDENTIAL_FORGOT);
    }

    @Test
    public void forgetNetwork_successNoLocation_dontBroadcastSsid() throws Exception {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);

        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);
        when(mWifiConfigManager.removeNetwork(eq(TEST_NETWORK_ID), anyInt(), any()))
                .thenReturn(true);

        mWifiServiceImpl.forget(TEST_NETWORK_ID, mActionListener);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).removeNetwork(eq(TEST_NETWORK_ID), anyInt(), any());
        verify(mActionListener).onSuccess();
        verify(mActionListener, never())
                .onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);

        verify(mContextAsUser).sendBroadcastWithMultiplePermissions(
                mIntentCaptor.capture(),
                aryEq(new String[]{
                        android.Manifest.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                }));

        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.WIFI_CREDENTIAL_CHANGED_ACTION);
        // SSID is null if location is disabled
        assertThat(intent.getStringExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_SSID)).isNull();
        assertThat(intent.getIntExtra(WifiManager.EXTRA_WIFI_CREDENTIAL_EVENT_TYPE, -1))
                .isEqualTo(WifiManager.WIFI_CREDENTIAL_FORGOT);
    }

    @Test
    public void forgetNetwork_failed() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.removeNetwork(eq(TEST_NETWORK_ID), anyInt(), any()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);

        mWifiServiceImpl.forget(TEST_NETWORK_ID, mActionListener);
        mLooper.dispatchAll();

        verify(mActionListener, never()).onSuccess();
        verify(mActionListener).onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
        verify(mWifiConfigManager).removeNetwork(eq(TEST_NETWORK_ID), anyInt(), any());
        verify(mContextAsUser, never()).sendBroadcastWithMultiplePermissions(any(), any());
    }

    /**
     * Verify that connecting to a supported security type network succeeds
     */
    @Test
    public void connectToSupportedSecurityTypeNetwork_success() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        NetworkUpdateResult result = new NetworkUpdateResult(TEST_NETWORK_ID);
        when(mWifiConfigManager.addOrUpdateNetwork(eq(mWifiConfig), anyInt()))
                .thenReturn(result);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);
        when(mWifiGlobals.isDeprecatedSecurityTypeNetwork(mWifiConfig)).thenReturn(false);

        mWifiServiceImpl.connect(mWifiConfig, WifiConfiguration.INVALID_NETWORK_ID,
                mActionListener, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();

        ArgumentCaptor<WifiConfiguration> configCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(configCaptor.capture(), anyInt());
        assertThat(configCaptor.getValue().networkId).isEqualTo(TEST_NETWORK_ID);

        verify(mConnectHelper).connectToNetwork(eq(result), any(), anyInt(), any(), any());
    }

    /**
     * Verify that connecting to a deprecated security type network fails,
     * but saves the network instead
     */
    @Test
    public void connectToDeprecatedSecurityTypeNetwork_failure() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        NetworkUpdateResult result = new NetworkUpdateResult(TEST_NETWORK_ID);
        when(mWifiConfigManager.addOrUpdateNetwork(eq(mWifiConfig), anyInt()))
                .thenReturn(result);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(mWifiConfig);
        when(mWifiGlobals.isDeprecatedSecurityTypeNetwork(mWifiConfig)).thenReturn(true);

        mWifiServiceImpl.connect(mWifiConfig, WifiConfiguration.INVALID_NETWORK_ID,
                mActionListener, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();

        ArgumentCaptor<WifiConfiguration> configCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(configCaptor.capture(), anyInt());
        assertThat(configCaptor.getValue().networkId).isEqualTo(TEST_NETWORK_ID);

        verify(mWifiConfigManager).addOrUpdateNetwork(eq(mWifiConfig), anyInt());
        verify(mClientModeManager, never()).connectNetwork(any(), any(), anyInt(), any(), any());
        verify(mActionListener).onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
        verify(mActionListener, never()).onSuccess();
    }

    /**
     * Tests the scenario when a scan request arrives while the device is idle. In this case
     * the scan is done when idle mode ends.
     */
    @Test
    public void testHandleDelayedScanAfterIdleMode() throws Exception {
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IdleModeIntentMatcher()),
                isNull(),
                any(Handler.class));

        // Tell the wifi service that the device became idle.
        when(mPowerManager.isDeviceIdleMode()).thenReturn(true);
        TestUtil.sendIdleModeChanged(mBroadcastReceiverCaptor.getValue(), mContext);

        // Send a scan request while the device is idle.
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.startScan(SCAN_PACKAGE_NAME, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        // No scans must be made yet as the device is idle.
        verify(mScanRequestProxy, never()).startScan(Process.myUid(), SCAN_PACKAGE_NAME);
        // Verify ActiveModeWarden is notified of the idle mode change
        verify(mActiveModeWarden).onIdleModeChanged(true);

        // Tell the wifi service that idle mode ended.
        when(mPowerManager.isDeviceIdleMode()).thenReturn(false);
        mLooper.startAutoDispatch();
        TestUtil.sendIdleModeChanged(mBroadcastReceiverCaptor.getValue(), mContext);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        // Verify ActiveModeWarden is notified of the idle mode change
        verify(mActiveModeWarden).onIdleModeChanged(false);

        // Must scan now.
        verify(mScanRequestProxy).startScan(Process.myUid(), TEST_PACKAGE_NAME);
        // The app ops check is executed with this package's identity (not the identity of the
        // original remote caller who requested the scan while idle).
        verify(mAppOpsManager).noteOp(
                AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);

        // Send another scan request. The device is not idle anymore, so it must be executed
        // immediately.
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.startScan(SCAN_PACKAGE_NAME, TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mScanRequestProxy).startScan(Process.myUid(), SCAN_PACKAGE_NAME);
    }

    /**
     * Verify that if the caller has NETWORK_SETTINGS permission, then it doesn't need
     * CHANGE_WIFI_STATE permission.
     */
    @Test
    public void testDisconnectWithNetworkSettingsPerm() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_WIFI_STATE, "WifiService");
        doThrow(new SecurityException()).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        assertTrue(mWifiServiceImpl.disconnect(TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verify(mClientModeManager).disconnect();
    }

    /**
     * Verify that if the caller doesn't have NETWORK_SETTINGS permission, it could still
     * get access with the CHANGE_WIFI_STATE permission.
     */
    @Test
    public void testDisconnectWithChangeWifiStatePerm() throws Exception {
        assertFalse(mWifiServiceImpl.disconnect(TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mClientModeManager, never()).disconnect();
    }

    /**
     * Verify that the operation fails if the caller has neither NETWORK_SETTINGS or
     * CHANGE_WIFI_STATE permissions.
     */
    @Test
    public void testDisconnectRejected() throws Exception {
        doThrow(new SecurityException()).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        try {
            mWifiServiceImpl.disconnect(TEST_PACKAGE_NAME);
            fail();
        } catch (SecurityException e) {

        }
        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mClientModeManager, never()).disconnect();
    }

    @Test
    public void testPackageFullyRemovedBroadcastHandling() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                                && filter.hasAction(Intent.ACTION_PACKAGE_REMOVED)
                                && filter.hasAction(Intent.ACTION_PACKAGE_CHANGED)),
                isNull(),
                any(Handler.class));
        int uid = TEST_UID;
        String packageName = TEST_PACKAGE_NAME;
        doThrow(new PackageManager.NameNotFoundException()).when(mPackageManager)
                .getApplicationInfo(TEST_PACKAGE_NAME, 0);
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intent.putExtra(Intent.EXTRA_UID, uid);
        intent.setData(Uri.fromParts("package", packageName, ""));
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        ArgumentCaptor<ApplicationInfo> aiCaptor = ArgumentCaptor.forClass(ApplicationInfo.class);
        verify(mWifiConfigManager).removeNetworksForApp(aiCaptor.capture());
        assertNotNull(aiCaptor.getValue());
        assertEquals(uid, aiCaptor.getValue().uid);
        assertEquals(packageName, aiCaptor.getValue().packageName);

        verify(mScanRequestProxy).clearScanRequestTimestampsForApp(packageName, uid);
        verify(mWifiNetworkSuggestionsManager).removeApp(packageName);
        verify(mWifiNetworkFactory).removeApp(packageName);
        verify(mPasspointManager).removePasspointProviderWithPackage(packageName);
    }

    @Test
    public void testPackageRemovedBroadcastHandling() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                                && filter.hasAction(Intent.ACTION_PACKAGE_REMOVED)
                                && filter.hasAction(Intent.ACTION_PACKAGE_CHANGED)),
                isNull(),
                any(Handler.class));
        int uid = TEST_UID;
        String packageName = TEST_PACKAGE_NAME;
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.putExtra(Intent.EXTRA_UID, uid);
        intent.setData(Uri.fromParts("package", packageName, ""));
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        ArgumentCaptor<ApplicationInfo> aiCaptor = ArgumentCaptor.forClass(ApplicationInfo.class);
        verify(mWifiConfigManager).removeNetworksForApp(aiCaptor.capture());
        assertNotNull(aiCaptor.getValue());
        assertEquals(uid, aiCaptor.getValue().uid);
        assertEquals(packageName, aiCaptor.getValue().packageName);

        verify(mScanRequestProxy).clearScanRequestTimestampsForApp(packageName, uid);
        verify(mWifiNetworkSuggestionsManager).removeApp(packageName);
        verify(mWifiNetworkFactory).removeApp(packageName);
        verify(mPasspointManager).removePasspointProviderWithPackage(packageName);
    }

    @Test
    public void testPackageRemovedWithReplacingBroadcastHandling() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext)
                .registerReceiver(
                        mBroadcastReceiverCaptor.capture(),
                        argThat(
                                (IntentFilter filter) ->
                                        filter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                                                && filter.hasAction(Intent.ACTION_PACKAGE_REMOVED)
                                                && filter.hasAction(Intent.ACTION_PACKAGE_CHANGED)),
                        isNull(),
                        any(Handler.class));
        int uid = TEST_UID;
        String packageName = TEST_PACKAGE_NAME;
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.putExtra(Intent.EXTRA_UID, uid);
        intent.setData(Uri.fromParts("package", packageName, ""));
        intent.putExtra(Intent.EXTRA_REPLACING, true);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).removeNetworksForApp(any());
        verify(mScanRequestProxy, never()).clearScanRequestTimestampsForApp(anyString(), anyInt());
        verify(mWifiNetworkSuggestionsManager, never()).removeApp(anyString());
        verify(mWifiNetworkFactory, never()).removeApp(anyString());
        verify(mPasspointManager, never()).removePasspointProviderWithPackage(anyString());
    }

    @Test
    public void testPackageDisableBroadcastHandling() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                                && filter.hasAction(Intent.ACTION_PACKAGE_REMOVED)
                                && filter.hasAction(Intent.ACTION_PACKAGE_CHANGED)),
                isNull(),
                any(Handler.class));
        int uid = TEST_UID;
        String packageName = TEST_PACKAGE_NAME;
        mPackageInfo.applicationInfo = mApplicationInfo;
        mApplicationInfo.enabled = false;
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        intent.putExtra(Intent.EXTRA_UID, uid);
        intent.setData(Uri.fromParts("package", packageName, ""));
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        ArgumentCaptor<ApplicationInfo> aiCaptor = ArgumentCaptor.forClass(ApplicationInfo.class);
        verify(mWifiConfigManager).removeNetworksForApp(aiCaptor.capture());
        assertNotNull(aiCaptor.getValue());
        assertEquals(uid, aiCaptor.getValue().uid);
        assertEquals(packageName, aiCaptor.getValue().packageName);

        verify(mScanRequestProxy).clearScanRequestTimestampsForApp(packageName, uid);
        verify(mWifiNetworkSuggestionsManager).removeApp(packageName);
        verify(mWifiNetworkFactory).removeApp(packageName);
        verify(mPasspointManager).removePasspointProviderWithPackage(packageName);
    }

    @Test
    public void testPackageRemovedBroadcastHandlingWithNoUid() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)),
                isNull(),
                any(Handler.class));

        String packageName = TEST_PACKAGE_NAME;
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.setData(Uri.fromParts("package", packageName, ""));
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);

        verify(mWifiConfigManager, never()).removeNetworksForApp(any());

        mLooper.dispatchAll();
        verify(mScanRequestProxy, never()).clearScanRequestTimestampsForApp(anyString(), anyInt());
        verify(mWifiNetworkSuggestionsManager, never()).removeApp(anyString());
        verify(mWifiNetworkFactory, never()).removeApp(anyString());
        verify(mPasspointManager, never()).removePasspointProviderWithPackage(anyString());
    }

    @Test
    public void testPackageRemovedBroadcastHandlingWithNoPackageName() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)),
                isNull(),
                any(Handler.class));

        int uid = TEST_UID;
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intent.putExtra(Intent.EXTRA_UID, uid);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);

        verify(mWifiConfigManager, never()).removeNetworksForApp(any());

        mLooper.dispatchAll();
        verify(mScanRequestProxy, never()).clearScanRequestTimestampsForApp(anyString(), anyInt());
        verify(mWifiNetworkSuggestionsManager, never()).removeApp(anyString());
        verify(mWifiNetworkFactory, never()).removeApp(anyString());
        verify(mPasspointManager, never()).removePasspointProviderWithPackage(anyString());
    }

    @Test
    public void testUserRemovedBroadcastHandling() {
        mWifiServiceImpl.checkAndStartWifi();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_USER_REMOVED)),
                isNull(),
                any(Handler.class));

        UserHandle userHandle = UserHandle.of(TEST_USER_HANDLE);
        // Send the broadcast
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        intent.putExtra(Intent.EXTRA_USER, userHandle);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).removeNetworksForUser(userHandle.getIdentifier());
    }

    @Test
    public void testBluetoothBroadcastHandling() {
        mWifiServiceImpl.checkAndStartWifi();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                                && filter.hasAction(BluetoothAdapter.ACTION_STATE_CHANGED)),
                isNull(),
                any(Handler.class));

        {
            Intent intent = new Intent(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                    BluetoothAdapter.STATE_DISCONNECTED);
            mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
            mLooper.dispatchAll();

            verify(mWifiGlobals).setBluetoothConnected(false);
            for (ClientModeManager cmm : mClientModeManagers) {
                verify(cmm).onBluetoothConnectionStateChanged();
            }
        }

        {
            Intent intent = new Intent(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                    BluetoothAdapter.STATE_CONNECTED);
            mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
            mLooper.dispatchAll();

            verify(mWifiGlobals).setBluetoothConnected(true);
            for (ClientModeManager cmm : mClientModeManagers) {
                verify(cmm, times(2)).onBluetoothConnectionStateChanged();
            }
        }

        {
            Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
            mLooper.dispatchAll();

            verify(mWifiGlobals).setBluetoothEnabled(false);
            for (ClientModeManager cmm : mClientModeManagers) {
                verify(cmm, times(3)).onBluetoothConnectionStateChanged();
            }
        }

        {
            Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
            mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
            mLooper.dispatchAll();

            verify(mWifiGlobals).setBluetoothEnabled(true);
            for (ClientModeManager cmm : mClientModeManagers) {
                verify(cmm, times(4)).onBluetoothConnectionStateChanged();
            }
        }
    }

    @Test
    public void testUserRemovedBroadcastHandlingWithWrongIntentAction() {
        mWifiServiceImpl.checkAndStartWifi();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_USER_REMOVED)),
                isNull(),
                any(Handler.class));

        UserHandle userHandle = UserHandle.of(TEST_USER_HANDLE);
        // Send the broadcast with wrong action
        Intent intent = new Intent(Intent.ACTION_USER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_USER, userHandle);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);

        verify(mWifiConfigManager, never()).removeNetworksForUser(anyInt());
    }

    private class IdleModeIntentMatcher implements ArgumentMatcher<IntentFilter> {
        @Override
        public boolean matches(IntentFilter filter) {
            return filter.hasAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        }
    }

    /**
     * Verifies that enforceChangePermission(String package) is called and the caller doesn't
     * have NETWORK_SETTINGS permission
     */
    private void verifyCheckChangePermission(String callingPackageName) {
        verify(mContext, atLeastOnce())
                .checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        anyInt(), anyInt());
        verify(mContext, atLeastOnce()).enforceCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_WIFI_STATE, "WifiService");
        verify(mAppOpsManager, atLeastOnce()).noteOp(
                AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), callingPackageName);
    }

    private WifiConfiguration createValidWifiApConfiguration() {
        WifiConfiguration apConfig = new WifiConfiguration();
        apConfig.SSID = "TestAp";
        apConfig.preSharedKey = "thisIsABadPassword";
        apConfig.allowedKeyManagement.set(KeyMgmt.WPA2_PSK);
        apConfig.apBand = WifiConfiguration.AP_BAND_2GHZ;

        return apConfig;
    }

    private SoftApConfiguration createValidSoftApConfiguration() {
        return new SoftApConfiguration.Builder()
                .setSsid("TestAp")
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ)
                .build();
    }

    /**
     * Verifies that sim state change does not set or reset the country code
     */
    @Test
    public void testSimStateChangeDoesNotResetCountryCode() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat((IntentFilter filter) ->
                        filter.hasAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)),
                isNull(),
                any(Handler.class));

        int userHandle = TEST_USER_HANDLE;
        // Send the broadcast
        Intent intent = new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        intent.putExtra(Intent.EXTRA_USER_HANDLE, userHandle);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        verify(mWifiCountryCode, never()).setTelephonyCountryCodeAndUpdate(any());
    }

    /**
     * Verifies that sim state change does not set or reset the country code
     */
    @Test
    public void testSimStateChangeDoesNotResetCountryCodeForRebroadcastedIntent() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat((IntentFilter filter) ->
                        filter.hasAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)),
                isNull(),
                any(Handler.class));

        int userHandle = TEST_USER_HANDLE;
        // Send the broadcast
        Intent intent = new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        intent.putExtra(Intent.EXTRA_USER_HANDLE, userHandle);
        intent.putExtra(TelephonyManager.EXTRA_SIM_STATE, Intent.SIM_STATE_ABSENT);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        verify(mWifiCountryCode, never()).setTelephonyCountryCodeAndUpdate(any());
    }

    /**
     * Verify removing sim will also remove an ephemeral Passpoint Provider. And reset carrier
     * privileged suggestor apps.
     */
    @Test
    public void testResetSimNetworkWhenRemovingSim() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)),
                isNull(),
                any(Handler.class));

        Intent intent = new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_ABSENT);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).resetSimNetworks();
        verify(mWifiConfigManager).stopRestrictingAutoJoinToSubscriptionId();
        verify(mSimRequiredNotifier, never()).dismissSimRequiredNotification();
        verify(mWifiNetworkSuggestionsManager).updateCarrierPrivilegedApps();
        verify(mWifiConfigManager, never()).removeAllEphemeralOrPasspointConfiguredNetworks();
        verify(mWifiNetworkSuggestionsManager).resetSimNetworkSuggestions();
        verify(mPasspointManager).resetSimPasspointNetwork();
    }

    /**
     * Verify inserting sim will reset carrier privileged suggestor apps.
     * and remove any previous notifications due to sim removal
     */
    @Test
    public void testResetCarrierPrivilegedAppsWhenInsertingSim() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED)),
                isNull(),
                any(Handler.class));

        Intent intent = new Intent(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_LOADED);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        verify(mWifiConfigManager, never()).resetSimNetworks();
        verify(mPasspointManager, never()).resetSimPasspointNetwork();
        verify(mWifiNetworkSuggestionsManager, never()).resetSimNetworkSuggestions();
        verify(mWifiConfigManager, never()).stopRestrictingAutoJoinToSubscriptionId();
        verify(mSimRequiredNotifier).dismissSimRequiredNotification();
        verify(mWifiNetworkSuggestionsManager).updateCarrierPrivilegedApps();
        verify(mWifiConfigManager, never()).removeAllEphemeralOrPasspointConfiguredNetworks();
        verify(mWifiConfigManager).enableTemporaryDisabledNetworks();
        verify(mWifiConnectivityManager).forceConnectivityScan(any());
    }

    @Test
    public void testResetSimNetworkWhenDefaultDataSimChanged() throws Exception {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(
                                TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)),
                isNull(),
                any(Handler.class));

        Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", 1);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).resetSimNetworks();
        verify(mWifiConfigManager).stopRestrictingAutoJoinToSubscriptionId();
        verify(mSimRequiredNotifier, never()).dismissSimRequiredNotification();
        verify(mWifiNetworkSuggestionsManager).updateCarrierPrivilegedApps();
        verify(mWifiConfigManager).removeEphemeralCarrierNetworks(anySet());
        verify(mWifiNetworkSuggestionsManager).resetSimNetworkSuggestions();
        verify(mPasspointManager).resetSimPasspointNetwork();
        verify(mWifiDataStall).resetPhoneStateListener();
    }

    /**
     * Verify that a call to registerTrafficStateCallback throws a SecurityException if the caller
     * does not have NETWORK_SETTINGS permission.
     */
    @Test
    public void registerTrafficStateCallbackThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.registerTrafficStateCallback(mTrafficStateCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that a call to registerTrafficStateCallback throws an IllegalArgumentException if the
     * parameters are not provided.
     */
    @Test
    public void registerTrafficStateCallbackThrowsIllegalArgumentExceptionOnInvalidArguments() {
        try {
            mWifiServiceImpl.registerTrafficStateCallback(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that a call to unregisterTrafficStateCallback throws a SecurityException if the caller
     * does not have NETWORK_SETTINGS permission.
     */
    @Test
    public void unregisterTrafficStateCallbackThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.unregisterTrafficStateCallback(mTrafficStateCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that registerTrafficStateCallback adds callback to {@link WifiTrafficPoller}.
     */
    @Test
    public void registerTrafficStateCallbackAndVerify() throws Exception {
        mWifiServiceImpl.registerTrafficStateCallback(mTrafficStateCallback);
        mLooper.dispatchAll();
        verify(mWifiTrafficPoller).addCallback(mTrafficStateCallback);
    }

    /**
     * Verify that unregisterTrafficStateCallback removes callback from {@link WifiTrafficPoller}.
     */
    @Test
    public void unregisterTrafficStateCallbackAndVerify() throws Exception {
        mWifiServiceImpl.unregisterTrafficStateCallback(mTrafficStateCallback);
        mLooper.dispatchAll();
        verify(mWifiTrafficPoller).removeCallback(mTrafficStateCallback);
    }

    /**
     * Verify that a call to registerNetworkRequestMatchCallback throws a SecurityException if the
     * caller does not have NETWORK_SETTINGS permission.
     */
    @Test
    public void registerNetworkRequestMatchCallbackThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.registerNetworkRequestMatchCallback(mNetworkRequestMatchCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that a call to registerNetworkRequestMatchCallback throws an IllegalArgumentException
     * if the parameters are not provided.
     */
    @Test
    public void
            registerNetworkRequestMatchCallbackThrowsIllegalArgumentExceptionOnInvalidArguments() {
        try {
            mWifiServiceImpl.registerNetworkRequestMatchCallback(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that a call to unregisterNetworkRequestMatchCallback throws a SecurityException if the
     * caller does not have NETWORK_SETTINGS permission.
     */
    @Test
    public void unregisterNetworkRequestMatchCallbackThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.unregisterNetworkRequestMatchCallback(mNetworkRequestMatchCallback);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that registerNetworkRequestMatchCallback adds callback to
     * {@link ClientModeManager}.
     */
    @Test
    public void registerNetworkRequestMatchCallbackAndVerify() throws Exception {
        mWifiServiceImpl.registerNetworkRequestMatchCallback(mNetworkRequestMatchCallback);
        mLooper.dispatchAll();
        verify(mWifiNetworkFactory).addCallback(mNetworkRequestMatchCallback);
    }

    /**
     * Verify that unregisterNetworkRequestMatchCallback removes callback from
     * {@link ClientModeManager}.
     */
    @Test
    public void unregisterNetworkRequestMatchCallbackAndVerify() throws Exception {
        mWifiServiceImpl.unregisterNetworkRequestMatchCallback(mNetworkRequestMatchCallback);
        mLooper.dispatchAll();
        verify(mWifiNetworkFactory).removeCallback(mNetworkRequestMatchCallback);
    }

    /**
     * Verify that Wifi configuration and Passpoint configuration are removed in factoryReset.
     */
    @Test
    public void testFactoryReset() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        final String fqdn = "example.com";
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.networkId = TEST_NETWORK_ID;
        WifiConfiguration eapNetwork = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.NONE);
        eapNetwork.networkId = TEST_NETWORK_ID + 1;
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        config.setHomeSp(homeSp);
        Credential credential = new Credential();
        credential.setRealm("example.com");
        config.setCredential(credential);

        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(Arrays.asList(openNetwork, eapNetwork));
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(Arrays.asList(config));

        mLooper.startAutoDispatch();
        mWifiServiceImpl.factoryReset(TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        // Let the final post inside the |factoryReset| method run to completion.
        mLooper.dispatchAll();

        verify(mWifiApConfigStore).setApConfiguration(null);
        verify(mWifiConfigManager).removeNetwork(
                openNetwork.networkId, Binder.getCallingUid(), TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).removeNetwork(
                eapNetwork.networkId, Binder.getCallingUid(), TEST_PACKAGE_NAME);
        verify(mWifiKeyStore).removeKeys(eapNetwork.enterpriseConfig, true);
        verify(mPasspointManager).removeProvider(anyInt(), anyBoolean(), eq(config.getUniqueId()),
                isNull());
        verify(mPasspointManager).clearAnqpRequestsAndFlushCache();
        verify(mWifiConfigManager).clearUserTemporarilyDisabledList();
        verify(mWifiConfigManager).removeAllEphemeralOrPasspointConfiguredNetworks();
        verify(mWifiNetworkFactory).clear();
        verify(mWifiNetworkSuggestionsManager).clear();
        verify(mWifiScoreCard).clear();
        verify(mWifiHealthMonitor).clear();
        verify(mPasspointManager).getProviderConfigs(anyInt(), anyBoolean());
        verify(mContext).resetResourceCache();
    }

    /**
     * Verify that a call to factoryReset throws a SecurityException if the caller does not have
     * the NETWORK_SETTINGS permission.
     */
    @Test
    public void testFactoryResetWithoutNetworkSettingsPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.factoryReset(TEST_PACKAGE_NAME);
            fail();
        } catch (SecurityException e) {
        }
        verify(mWifiConfigManager, never()).getSavedNetworks(anyInt());
        verify(mPasspointManager, never()).getProviderConfigs(anyInt(), anyBoolean());
    }

    /**
     * Verify that add or update networks is not allowed for apps targeting Q SDK.
     */
    @Test
    public void testAddOrUpdateNetworkIsNotAllowedForAppsTargetingQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics, never()).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for apps targeting below Q SDK.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForAppsTargetingBelowQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for apps targeting below Q SDK.
     */
    @Test
    public void testAddOrUpdateNetworkWithBssidAllowListIsNotAllowedForAppsNotPrivileged()
            throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.setBssidAllowlist(Collections.emptyList());
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics, never()).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is not allowed for apps targeting below Q SDK
     * when DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddOrUpdateNetworkIsNotAllowedForAppsTargetingBelowQSdkWithUserRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics, never()).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is not allowed for camera app when
     * DISALLOW_CONFIG_WIFI user restriction is set.
     */
    @Test
    public void testAddOrUpdateNetworkIsNotAllowedForCameraDisallowConfigWifi() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkCameraPermission(Binder.getCallingUid())).thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(false);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI),
                any())).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics, never()).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is not allowed for camera app when
     * DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddOrUpdateNetworkIsNotAllowedForCameraDisallowAddWifiConfig()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkCameraPermission(Binder.getCallingUid())).thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(false);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics, never()).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for settings app.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForSettingsApp() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.P;
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        // Ensure that we don't check for change permission.
        verify(mContext, never()).enforceCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_WIFI_STATE, "WifiService");
        verify(mAppOpsManager, never()).noteOp(
                AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for system apps.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForSystemApp() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for apps holding system alert permission.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForAppsWithSystemAlertPermission() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));

        // Verify caller fails to add network as Guest user.
        when(mWifiPermissionsUtil.checkSystemAlertWindowPermission(
                Process.myUid(), TEST_PACKAGE_NAME)).thenReturn(true);
        when(mWifiPermissionsUtil.isGuestUser()).thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(-1,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));

        // Verify caller successfully add network when not a Guest user.
        when(mWifiPermissionsUtil.isGuestUser()).thenReturn(false);
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiPermissionsUtil, times(2))
                .checkSystemAlertWindowPermission(anyInt(), anyString());
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for DeviceOwner app.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForDOApp() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isDeviceOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for ProfileOwner app.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForPOApp() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that add or update networks is allowed for an admin app.
     */
    @Test
    public void testAddOrUpdateNetworkIsAllowedForAdminApp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(),  anyInt(), any(), eq(false))).thenReturn(
                new NetworkUpdateResult(0));

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        assertEquals(0,
                mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verifyCheckChangePermission(TEST_PACKAGE_NAME);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    private void verifyAddOrUpdateNetworkPrivilegedDoesNotThrowException() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(0));
        mLooper.startAutoDispatch();
        mWifiServiceImpl.addOrUpdateNetworkPrivileged(config, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiConfigManager).addOrUpdateNetwork(any(),  anyInt(), any(), eq(false));
        verify(mWifiMetrics).incrementNumAddOrUpdateNetworkCalls();
    }

    /**
     * Verify that addOrUpdateNetworkPrivileged throws a SecurityException if the calling app
     * has no permissions.
     */
    @Test
    public void testAddOrUpdateNetworkPrivilegedNotAllowedForNormalApps() throws Exception {
        try {
            WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
            mWifiServiceImpl.addOrUpdateNetworkPrivileged(config, TEST_PACKAGE_NAME);
            fail("Expected SecurityException for apps without permission");
        } catch (SecurityException e) {
        }
    }

    /**
     * Verify that a privileged app with NETWORK_SETTINGS permission is allowed to call
     * addOrUpdateNetworkPrivileged.
     */
    @Test
    public void testAddOrUpdateNetworkPrivilegedIsAllowedForPrivilegedApp() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        verifyAddOrUpdateNetworkPrivilegedDoesNotThrowException();
    }

    /**
     * Verify that a system app is allowed to call addOrUpdateNetworkPrivileged.
     */
    @Test
    public void testAddOrUpdateNetworkPrivilegedIsAllowedForSystemApp() throws Exception {
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        verifyAddOrUpdateNetworkPrivilegedDoesNotThrowException();
    }

    /**
     * Verify that an admin app is allowed to call addOrUpdateNetworkPrivileged.
     */
    @Test
    public void testAddOrUpdateNetworkPrivilegedIsAllowedForAdminApp() throws Exception {
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        verifyAddOrUpdateNetworkPrivilegedDoesNotThrowException();
    }

    /**
     * Verify the proper status code is returned when addOrUpdateNetworkPrivileged failed due to
     * a failure in WifiConfigManager.addOrUpdateNetwork().
     */
    @Test
    public void testAddOrUpdateNetworkInvalidConfiguration() throws Exception {
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(-1));
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        mLooper.startAutoDispatch();
        WifiManager.AddNetworkResult result = mWifiServiceImpl.addOrUpdateNetworkPrivileged(
                config, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(WifiManager.AddNetworkResult.STATUS_ADD_WIFI_CONFIG_FAILURE,
                result.statusCode);
        assertEquals(-1, result.networkId);
    }

    /**
     * Verify that enableNetwork is allowed for privileged Apps
     */
    @Test
    public void testEnableNetworkWithDisableOthersAllowedForPrivilegedApps() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);

        doAnswer(new AnswerWithArguments() {
            public void answer(NetworkUpdateResult result, ActionListenerWrapper callback,
                    int callingUid, String packageName, String attributionTag) {
                callback.sendSuccess(); // return success
            }
        }).when(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());

        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.enableNetwork(TEST_NETWORK_ID, true, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatch();

        verify(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());
        verify(mWifiMetrics).incrementNumEnableNetworkCalls();
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_ENABLE_NETWORK), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify that enableNetwork (with disableOthers=true) is allowed for Apps targeting a SDK
     * version less than Q
     */
    @Test
    public void testEnabledNetworkWithDisableOthersAllowedForAppsTargetingBelowQSdk()
            throws Exception {
        mLooper.dispatchAll();
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(NetworkUpdateResult result, ActionListenerWrapper callback,
                    int callingUid, String packageName, String attributionTag) {
                callback.sendSuccess(); // return success
            }
        }).when(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());

        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.enableNetwork(TEST_NETWORK_ID, true, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatch();

        verify(mConnectHelper).connectToNetwork(
                eq(new NetworkUpdateResult(TEST_NETWORK_ID)), any(), anyInt(), any(), any());
        verify(mWifiMetrics).incrementNumEnableNetworkCalls();
    }

    /**
     * Verify that enableNetwork (with disableOthers=false) is allowed for Apps targeting a SDK
     * version less than Q
     */
    @Test
    public void testEnabledNetworkWithoutDisableOthersAllowedForAppsTargetingBelowQSdk()
            throws Exception {
        mLooper.dispatchAll();
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        when(mWifiConfigManager.enableNetwork(anyInt(), anyBoolean(), anyInt(), anyString()))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.enableNetwork(TEST_NETWORK_ID, false, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mWifiConfigManager).enableNetwork(eq(TEST_NETWORK_ID), eq(false),
                eq(Binder.getCallingUid()), eq(TEST_PACKAGE_NAME));
        verify(mWifiMetrics).incrementNumEnableNetworkCalls();
    }

    /**
     * Verify that enableNetwork is not allowed for Apps targeting Q SDK
     */
    @Test
    public void testEnableNetworkNotAllowedForAppsTargetingQ() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.enableNetwork(TEST_NETWORK_ID, true, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        verify(mWifiMetrics, never()).incrementNumEnableNetworkCalls();
    }

    /**
     * Verify that enableNetwork is not allowed for admin restricted network
     */
    @Test
    public void testEnableNetworkNotAllowedForAdminRestrictedNetwork() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE_NAME, Process.myUid())).thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiPermissionsUtil.isAdminRestrictedNetwork(config)).thenReturn(true);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.enableNetwork(TEST_NETWORK_ID, true, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        verify(mWifiMetrics, never()).incrementNumEnableNetworkCalls();
    }

    /**
     * Verify that enableNetwork is not allowed for deprecated security type network
     */
    @Test
    public void testEnableNetworkNotAllowedForDeprecatedSecurityTypeNetwork() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isSystem(TEST_PACKAGE_NAME, Process.myUid())).thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        when(mWifiGlobals.isDeprecatedSecurityTypeNetwork(config)).thenReturn(true);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.enableNetwork(TEST_NETWORK_ID, true, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mConnectHelper, never()).connectToNetwork(any(), any(), anyInt(), any(), any());
        verify(mWifiMetrics, never()).incrementNumEnableNetworkCalls();
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions.
     */
    @Test
    public void testAddNetworkSuggestions() {
        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE);
        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager, times(2)).add(
                any(), eq(Binder.getCallingUid()), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions for carrier app when DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddNetworkSuggestionsIsAllowedForCarrierAppWithUserRestriction() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(anyString())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager).add(
                any(), eq(Binder.getCallingUid()), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions for privileged app when DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddNetworkSuggestionsIsAllowedForPrivilegedAppWithUserRestriction() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager).add(
                any(), eq(Binder.getCallingUid()), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions for system app when DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddNetworkSuggestionsIsAllowedForSystemAppWithUserRestriction() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager).add(
                any(), eq(Binder.getCallingUid()), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions for admin app when DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddNetworkSuggestionsIsAllowedForAdminAppWithUserRestriction() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(anyInt(), anyString())).thenReturn(true);

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager).add(
                any(), eq(Binder.getCallingUid()), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we don't invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions for normal app when DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void testAddNetworkSuggestionsIsNotAllowedForNormalAppWithUserRestriction() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiNetworkSuggestionsManager.add(any(), anyInt(), anyString(),
                nullable(String.class))).thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_RESTRICTED_BY_ADMIN,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager, never()).add(any(), eq(Binder.getCallingUid()),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we don't invoke {@link WifiNetworkSuggestionsManager} to add network
     * suggestions when the looper sync call times out.
     */
    @Test
    public void testAddNetworkSuggestionsFailureInRunWithScissors() {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiServiceImpl.addNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        TEST_FEATURE_ID));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager, never()).add(any(), eq(Binder.getCallingUid()),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to remove network
     * suggestions.
     */
    @Test
    public void testRemoveNetworkSuggestions() {
        when(mWifiNetworkSuggestionsManager.remove(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID);
        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID,
                mWifiServiceImpl.removeNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        ACTION_REMOVE_SUGGESTION_DISCONNECT));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        when(mWifiNetworkSuggestionsManager.remove(any(), anyInt(), anyString(),
                anyInt()))
                .thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                mWifiServiceImpl.removeNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                        ACTION_REMOVE_SUGGESTION_DISCONNECT));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager, times(2)).remove(any(), anyInt(),
                eq(TEST_PACKAGE_NAME), eq(ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    /**
     * Ensure that we don't invoke {@link WifiNetworkSuggestionsManager} to remove network
     * suggestions when the looper sync call times out.
     */
    @Test
    public void testRemoveNetworkSuggestionsFailureInRunWithScissors() {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL,
                mWifiServiceImpl.removeNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME,
                ACTION_REMOVE_SUGGESTION_DISCONNECT));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager, never()).remove(any(), anyInt(),
                eq(TEST_PACKAGE_NAME), eq(ACTION_REMOVE_SUGGESTION_DISCONNECT));
    }

    /**
     * Ensure that we don't invoke {@link WifiNetworkSuggestionsManager} to remove network
     * suggestions when the action is invalid.
     */
    @Test
    public void testRemoveNetworkSuggestionsFailureWithInvalidAction() {
        mLooper.startAutoDispatch();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID,
                mWifiServiceImpl.removeNetworkSuggestions(mock(List.class), TEST_PACKAGE_NAME, 0));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mWifiNetworkSuggestionsManager, never()).remove(any(), anyInt(),
                eq(TEST_PACKAGE_NAME), anyInt());
    }

    @Test(expected = SecurityException.class)
    public void testRemoveNonCallerConfiguredNetworks_NormalAppCaller_ThrowsException() {
        mWifiServiceImpl.removeNonCallerConfiguredNetworks(TEST_PACKAGE_NAME);
    }

    @Test(expected = SecurityException.class)
    public void testRemoveNonCallerConfiguredNetworks_CallerIsProfileOwner_ThrowsException() {
        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        mWifiServiceImpl.removeNonCallerConfiguredNetworks(TEST_PACKAGE_NAME);
    }

    @Test
    public void testRemoveNonCallerConfiguredNetworks_NetworksRemoved() {
        final int callerUid = Binder.getCallingUid();
        when(mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(
                Binder.getCallingUid(), TEST_PACKAGE_NAME)).thenReturn(true);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.removeNonCallerConfiguredNetworks(TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiConfigManager).removeNonCallerConfiguredNetwork(eq(callerUid));
    }

    /**
     * Ensure that we invoke {@link WifiNetworkSuggestionsManager} to get network
     * suggestions.
     */
    @Test
    public void testGetNetworkSuggestions() {
        List<WifiNetworkSuggestion> testList = new ArrayList<>();
        when(mWifiNetworkSuggestionsManager.get(anyString(), anyInt())).thenReturn(testList);
        mLooper.startAutoDispatch();
        assertEquals(testList, mWifiServiceImpl.getNetworkSuggestions(TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager).get(eq(TEST_PACKAGE_NAME), anyInt());
    }

    /**
     * Ensure that we don't invoke {@link WifiNetworkSuggestionsManager} to get network
     * suggestions when the looper sync call times out.
     */
    @Test
    public void testGetNetworkSuggestionsFailureInRunWithScissors() {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.getNetworkSuggestions(TEST_PACKAGE_NAME).isEmpty());
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        verify(mWifiNetworkSuggestionsManager, never()).get(eq(TEST_PACKAGE_NAME), anyInt());
    }

    /**
     * Verify that if the caller has NETWORK_SETTINGS permission, then it can invoke
     * {@link WifiManager#disableEphemeralNetwork(String)}.
     */
    @Test
    public void testDisableEphemeralNetworkWithNetworkSettingsPerm() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.disableEphemeralNetwork(new String(), TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).userTemporarilyDisabledNetwork(anyString(), anyInt());
    }

    /**
     * Verify that if the caller does not have NETWORK_SETTINGS permission, then it cannot invoke
     * {@link WifiManager#disableEphemeralNetwork(String)}.
     */
    @Test
    public void testDisableEphemeralNetworkWithoutNetworkSettingsPerm() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        mWifiServiceImpl.disableEphemeralNetwork(new String(), TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).userTemporarilyDisabledNetwork(anyString(), anyInt());
    }

    /**
     * Verify getting the factory MAC address.
     */
    @Test
    public void testGetFactoryMacAddresses() throws Exception {
        when(mWifiGlobals.isSaveFactoryMacToConfigStoreEnabled()).thenReturn(true);
        when(mWifiSettingsConfigStore.get(WIFI_STA_FACTORY_MAC_ADDRESS)).thenReturn(null);
        when(mClientModeManager.getFactoryMacAddress()).thenReturn(TEST_FACTORY_MAC);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mLooper.startAutoDispatch();
        final String[] factoryMacs = mWifiServiceImpl.getFactoryMacAddresses();
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(1, factoryMacs.length);
        assertEquals(TEST_FACTORY_MAC, factoryMacs[0]);
        verify(mClientModeManager).getFactoryMacAddress();
        verify(mWifiSettingsConfigStore).get(WIFI_STA_FACTORY_MAC_ADDRESS);
    }

    /**
     * Verify getting the factory MAC address returns null when posting the runnable to handler
     * fails.
     */
    @Test
    public void testGetFactoryMacAddressesPostFail() throws Exception {
        mWifiServiceImpl = makeWifiServiceImplWithMockRunnerWhichTimesOut();

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mLooper.startAutoDispatch();
        assertArrayEquals(new String[0], mWifiServiceImpl.getFactoryMacAddresses());
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mClientModeManager, never()).getFactoryMacAddress();
    }

    /**
     * Verify getting the factory MAC address returns null when the lower layers fail.
     */
    @Test
    public void testGetFactoryMacAddressesFail() throws Exception {
        when(mClientModeManager.getFactoryMacAddress()).thenReturn(null);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mLooper.startAutoDispatch();
        assertArrayEquals(new String[0], mWifiServiceImpl.getFactoryMacAddresses());
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mClientModeManager).getFactoryMacAddress();
    }

    /**
     * Verify getting the factory MAC address throws a SecurityException if the calling app
     * doesn't have NETWORK_SETTINGS permission.
     */
    @Test
    public void testGetFactoryMacAddressesFailNoNetworkSettingsPermission() throws Exception {
        when(mClientModeManager.getFactoryMacAddress()).thenReturn(TEST_FACTORY_MAC);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        try {
            mLooper.startAutoDispatch();
            mWifiServiceImpl.getFactoryMacAddresses();
            mLooper.stopAutoDispatchAndIgnoreExceptions();
            fail();
        } catch (SecurityException e) {
            assertTrue("Exception message should contain 'factory MAC'",
                    e.toString().contains("factory MAC"));
        }
    }

    @Test
    public void testGetFactoryMacAddressesSuccessFromSettingStore() throws Exception {
        when(mWifiGlobals.isSaveFactoryMacToConfigStoreEnabled()).thenReturn(true);
        when(mWifiSettingsConfigStore.get(WIFI_STA_FACTORY_MAC_ADDRESS))
                .thenReturn(TEST_FACTORY_MAC);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mClientModeManager.getFactoryMacAddress()).thenReturn(TEST_FACTORY_MAC);
        final String[] factoryMacs = mWifiServiceImpl.getFactoryMacAddresses();
        assertEquals(1, factoryMacs.length);
        assertEquals(TEST_FACTORY_MAC, factoryMacs[0]);
        mLooper.dispatchAll();
        verify(mClientModeManager, never()).getFactoryMacAddress();
    }

    /**
     * Verify that a call to setDeviceMobilityState throws a SecurityException if the
     * caller does not have WIFI_SET_DEVICE_MOBILITY_STATE permission.
     */
    @Test(expected = SecurityException.class)
    public void setDeviceMobilityStateThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(android.Manifest.permission.WIFI_SET_DEVICE_MOBILITY_STATE),
                        eq("WifiService"));
        mWifiServiceImpl.setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
    }

    /**
     * Verifies that setDeviceMobilityState runs on a separate handler thread.
     */
    @Test
    public void setDeviceMobilityStateRunsOnHandler() {
        mWifiServiceImpl.setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiConnectivityManager, never())
                .setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiHealthMonitor, never())
                .setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiDataStall, never())
                .setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiHealthMonitor).setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiDataStall).setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
    }

    /**
     * Verify that a call to addOnWifiUsabilityStatsListener throws a SecurityException if
     * the caller does not have WIFI_UPDATE_USABILITY_STATS_SCORE permission.
     */
    @Test
    public void testAddStatsListenerThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.addOnWifiUsabilityStatsListener(mOnWifiUsabilityStatsListener);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that a call to addOnWifiUsabilityStatsListener throws an IllegalArgumentException
     * if the parameters are not provided.
     */
    @Test
    public void testAddStatsListenerThrowsIllegalArgumentExceptionOnInvalidArguments() {
        try {
            mWifiServiceImpl.addOnWifiUsabilityStatsListener(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that a call to removeOnWifiUsabilityStatsListener throws a SecurityException if
     * the caller does not have WIFI_UPDATE_USABILITY_STATS_SCORE permission.
     */
    @Test
    public void testRemoveStatsListenerThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.removeOnWifiUsabilityStatsListener(mOnWifiUsabilityStatsListener);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that addOnWifiUsabilityStatsListener adds listener to {@link WifiMetrics}.
     */
    @Test
    public void testAddOnWifiUsabilityStatsListenerAndVerify() throws Exception {
        mWifiServiceImpl.addOnWifiUsabilityStatsListener(mOnWifiUsabilityStatsListener);
        mLooper.dispatchAll();
        verify(mWifiMetrics).addOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
    }

    /**
     * Verify that removeOnWifiUsabilityStatsListener removes listener from
     * {@link WifiMetrics}.
     */
    @Test
    public void testRemoveOnWifiUsabilityStatsListenerAndVerify() throws Exception {
        mWifiServiceImpl.removeOnWifiUsabilityStatsListener(mOnWifiUsabilityStatsListener);
        mLooper.dispatchAll();
        verify(mWifiMetrics).removeOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
    }

    /**
     * Verify that a call to updateWifiUsabilityScore throws a SecurityException if the
     * caller does not have UPDATE_WIFI_USABILITY_SCORE permission.
     */
    @Test
    public void testUpdateWifiUsabilityScoreThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                eq(android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE),
                eq("WifiService"));
        try {
            mWifiServiceImpl.updateWifiUsabilityScore(anyInt(), anyInt(), 15);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that mClientModeManager in WifiServiceImpl is being updated on Wifi usability score
     * update event.
     */
    @Test
    public void testWifiUsabilityScoreUpdateAfterScoreEvent() {
        mWifiServiceImpl.updateWifiUsabilityScore(5, 10, 15);
        mLooper.dispatchAll();
        verify(mWifiMetrics).incrementWifiUsabilityScoreCount(WIFI_IFACE_NAME, 5, 10, 15);
    }

    private void startLohsAndTethering(boolean isApConcurrencySupported) throws Exception {
        // initialization
        when(mActiveModeWarden.canRequestMoreSoftApManagers(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME))))
                .thenReturn(isApConcurrencySupported);
        // For these tests, always use distinct interface names for LOHS and tethered.
        mLohsInterfaceName = WIFI_IFACE_NAME2;

        setupLocalOnlyHotspot();
        reset(mActiveModeWarden);

        when(mActiveModeWarden.canRequestMoreSoftApManagers(
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME))))
                .thenReturn(isApConcurrencySupported);

        // start tethering
        mLooper.startAutoDispatch();
        boolean tetheringResult = mWifiServiceImpl.startSoftAp(null, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertTrue(tetheringResult);
        verify(mActiveModeWarden).startSoftAp(any(),
                eq(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME)));
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
    }

    /**
     * Verify LOHS gets stopped when trying to start tethering concurrently on devices that
     * doesn't support dual AP operation.
     */
    @Test
    public void testStartLohsAndTethering1AP() throws Exception {
        startLohsAndTethering(false);

        // verify LOHS got stopped
        verify(mLohsCallback).onHotspotFailed(anyInt());
        verify(mActiveModeWarden).stopSoftAp(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
    }

    /**
     * Verify LOHS doesn't get stopped when trying to start tethering concurrently on devices
     * that does support dual AP operation.
     */
    @Test
    public void testStartLohsAndTethering2AP() throws Exception {
        startLohsAndTethering(true);

        // verify LOHS didn't get stopped
        verifyZeroInteractions(ignoreStubs(mLohsCallback));
        verify(mActiveModeWarden, never()).stopSoftAp(anyInt());
    }

    /**
     * Verify that the call to startDppAsConfiguratorInitiator throws a security exception when the
     * caller doesn't have NETWORK_SETTINGS permissions or NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testStartDppAsConfiguratorInitiatorWithoutPermissions() {
        mWifiServiceImpl.startDppAsConfiguratorInitiator(mAppBinder, TEST_PACKAGE_NAME, DPP_URI,
                1, 1, mDppCallback);
    }

    /**
     * Verify that the call to startDppAsEnrolleeInitiator throws a security exception when the
     * caller doesn't have NETWORK_SETTINGS permissions or NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testStartDppAsEnrolleeInitiatorWithoutPermissions() {
        mWifiServiceImpl.startDppAsEnrolleeInitiator(mAppBinder, DPP_URI, mDppCallback);
    }

    /**
     * Verify that the call to startDppAsEnrolleeResponder throws a security exception when the
     * caller doesn't have NETWORK_SETTINGS permissions or NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testStartDppAsEnrolleeResponderWithoutPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiServiceImpl.startDppAsEnrolleeResponder(mAppBinder, DPP_PRODUCT_INFO,
                EASY_CONNECT_CRYPTOGRAPHY_CURVE_PRIME256V1, mDppCallback);
    }

    /**
     * Verify that a call to StartDppAsEnrolleeResponder throws an IllegalArgumentException
     * if the deviceInfo length exceeds the max allowed length.
     */
    @Test(expected = SecurityException.class)
    public void testStartDppAsEnrolleeResponderThrowsIllegalArgumentExceptionOnDeviceInfoMaxLen() {
        assumeTrue(SdkLevel.isAtLeastS());
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(Strings.repeat("a",
                    WifiManager.getEasyConnectMaxAllowedResponderDeviceInfoLength() + 2));
            String deviceInfo = sb.toString();
            mWifiServiceImpl.startDppAsEnrolleeResponder(mAppBinder, deviceInfo,
                    EASY_CONNECT_CRYPTOGRAPHY_CURVE_PRIME256V1, mDppCallback);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that a call to StartDppAsEnrolleeResponder throws an IllegalArgumentException
     * if the deviceInfo contains characters which are not allowed as per spec (For example
     * semicolon)
     */
    @Test(expected = SecurityException.class)
    public void testStartDppAsEnrolleeResponderThrowsIllegalArgumentExceptionOnWrongDeviceInfo() {
        assumeTrue(SdkLevel.isAtLeastS());
        try {
            mWifiServiceImpl.startDppAsEnrolleeResponder(mAppBinder, "DPP;TESTER",
                    EASY_CONNECT_CRYPTOGRAPHY_CURVE_PRIME256V1, mDppCallback);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that the call to stopDppSession throws a security exception when the
     * caller doesn't have NETWORK_SETTINGS permissions or NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testStopDppSessionWithoutPermissions() {
        try {
            mWifiServiceImpl.stopDppSession();
        } catch (RemoteException e) {
        }
    }

    /**
     * Verifies that configs can be removed.
     */
    @Test
    public void testRemoveNetworkIsAllowedForAppsTargetingBelowQSdk() {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiConfigManager.removeNetwork(eq(0), anyInt(), anyString())).thenReturn(true);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);

        mLooper.startAutoDispatch();
        boolean result = mWifiServiceImpl.removeNetwork(0, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertTrue(result);
        verify(mWifiConfigManager).removeNetwork(anyInt(), anyInt(), anyString());
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is allowed for apps targeting below R SDK.
     */
    @Test
    public void addOrUpdatePasspointConfigIsAllowedForAppsTargetingBelowRSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        reset(mPasspointManager);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(false);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is not allowed for apps targeting R SDK.
     */
    @Test
    public void addOrUpdatePasspointConfigIsNotAllowedForAppsTargetingRSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager, never())
                .addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(), anyBoolean(),
                        eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is allowed for Settings apps.
     */
    @Test
    public void addOrUpdatePasspointConfigIsAllowedSettingsApp() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is allowed for System apps.
     */
    @Test
    public void addOrUpdatePasspointConfigIsAllowedSystemApp() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is allowed for DeviceOwner apps.
     */
    @Test
    public void addOrUpdatePasspointConfigIsAllowedSystemAlertDOApp() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isDeviceOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is allowed for ProfileOwner apps.
     */
    @Test
    public void addOrUpdatePasspointConfigIsAllowedSystemAlertPOApp() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration disconnects from current captive portal network
     * when provisioned via WifiInstaller.
     */
    @Test
    public void addOrUpdatePasspointConfigDisconnectsCaptivePortal() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE,
                        Process.myUid(), CERT_INSTALLER_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);
        WifiConfiguration captivePortalConfig =
                WifiConfigurationTestUtil.createCaptivePortalNetwork();
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setNetworkId(captivePortalConfig.networkId);
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiConfigManager.getConfiguredNetworkWithPassword(captivePortalConfig.networkId))
                .thenReturn(captivePortalConfig);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), CERT_INSTALLER_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config,
                CERT_INSTALLER_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false));
        verify(mClientModeManager).disconnect();
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is not allowed for apps targeting below R SDK
     * when the DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void addOrUpdatePasspointConfigIsNotAllowedForAppsTargetingBelowRSdkWithUserRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager, never())
                .addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(), anyBoolean(),
                        eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is not allowed for system apps
     * when the DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void addOrUpdatePasspointConfigIsNotAllowedForSystemAppWithUserRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isSystem(anyString(), anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager, never())
                .addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(), anyBoolean(),
                        eq(false));
    }

    /**
     * Verify that addOrUpdatePasspointConfiguration is allowed for admin apps
     * when the DISALLOW_ADD_WIFI_CONFIG user restriction is set.
     */
    @Test
    public void addOrUpdatePasspointConfigIsAllowedSystemAlertAdminAppWithUserRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.R), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.isAdmin(Binder.getCallingUid(), TEST_PACKAGE_NAME))
                .thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_WIFI_CONFIG),
                any())).thenReturn(true);
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(
                config, Binder.getCallingUid(), TEST_PACKAGE_NAME, false, true, false))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.addOrUpdatePasspointConfiguration(config, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).addOrUpdateProvider(any(), anyInt(), anyString(), anyBoolean(),
                anyBoolean(), eq(false));
    }

    /**
     * Verify that removePasspointConfiguration will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager}.
     */
    @Test
    public void removePasspointConfig() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        String fqdn = "test.com";
        when(mPasspointManager.removeProvider(anyInt(), anyBoolean(), isNull(), eq(fqdn)))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.removePasspointConfiguration(fqdn, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        reset(mPasspointManager);

        when(mPasspointManager.removeProvider(anyInt(), anyBoolean(), isNull(), eq(fqdn)))
                .thenReturn(false);
        mLooper.startAutoDispatch();
        assertFalse(mWifiServiceImpl.removePasspointConfiguration(fqdn, TEST_PACKAGE_NAME));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Test that DISABLE_NETWORK returns failure to public API when WifiConfigManager returns
     * failure.
     */
    @Test
    public void testDisableNetworkFailureAppBelowQSdk() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mWifiConfigManager.disableNetwork(anyInt(), anyInt(), anyString())).thenReturn(false);

        mLooper.startAutoDispatch();
        boolean succeeded = mWifiServiceImpl.disableNetwork(0, TEST_PACKAGE_NAME);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertFalse(succeeded);
    }

    @Test
    public void testSetNetworkSelectionConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiNetworkSelectionConfig nsConfig = new WifiNetworkSelectionConfig.Builder().build();
        // no permission to call API
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setNetworkSelectionConfig(nsConfig));

        // Null arguments
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setNetworkSelectionConfig(null));

        // has permission to call API
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mWifiServiceImpl.setNetworkSelectionConfig(nsConfig);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setNetworkSelectionConfig(nsConfig);
        verify(mLastCallerInfoManager).put(
                eq(WifiManager.API_SET_NETWORK_SELECTION_CONFIG),
                anyInt(), anyInt(), anyInt(), any(), eq(true));
    }

    @Test
    public void testGetNetworkSelectionConfig_Exceptions() {
        assumeTrue(SdkLevel.isAtLeastT());
        IWifiNetworkSelectionConfigListener listener =
                mock(IWifiNetworkSelectionConfigListener.class);
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getNetworkSelectionConfig(null));

        // No permission ==> SecurityException
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getNetworkSelectionConfig(listener));
    }

    @Test
    public void testGetNetworkSelectionConfig_GoodCase() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        IWifiNetworkSelectionConfigListener listener =
                mock(IWifiNetworkSelectionConfigListener.class);
        InOrder inOrder = inOrder(listener);

        int [] defaultRssi2 = {-83, -80, -73, -60};
        int [] defaultRssi5 = {-80, -77, -70, -57};
        int [] defaultRssi6 = {-80, -77, -70, -57};
        int [] customRssi2 = {-80, -70, -60, -50};
        int [] customRssi5 = {-80, -75, -70, -65};
        int [] customRssi6 = {-75, -70, -65, -60};
        int [] resetArray = {0, 0, 0, 0};

        // has permission to call API
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        // configure the default values for RSSI thresholds from ScoringParams
        when(mScoringParams.getRssiArray(ScanResult.BAND_24_GHZ_START_FREQ_MHZ))
                .thenReturn(defaultRssi2);
        when(mScoringParams.getRssiArray(ScanResult.BAND_5_GHZ_START_FREQ_MHZ))
                .thenReturn(defaultRssi5);
        when(mScoringParams.getRssiArray(ScanResult.BAND_6_GHZ_START_FREQ_MHZ))
                .thenReturn(defaultRssi6);

        // getting the WifiNetworkSelectionConfig when one hasn't been set returns the default one
        // built from the builder with RSSI thresholds from ScoringParams
        mWifiServiceImpl.getNetworkSelectionConfig(listener);
        mLooper.dispatchAll();
        WifiNetworkSelectionConfig defaultConfig = new WifiNetworkSelectionConfig.Builder()
                .setRssiThresholds(ScanResult.WIFI_BAND_24_GHZ, defaultRssi2)
                .setRssiThresholds(ScanResult.WIFI_BAND_5_GHZ, defaultRssi5)
                .setRssiThresholds(WIFI_BAND_6_GHZ, defaultRssi6)
                .build();
        inOrder.verify(listener).onResult(defaultConfig);

        // set the WifiNetworkSelectionConfig and verify that same config is retrieved
        WifiNetworkSelectionConfig customConfig = new WifiNetworkSelectionConfig.Builder()
                .setRssiThresholds(ScanResult.WIFI_BAND_24_GHZ, customRssi2)
                .setRssiThresholds(ScanResult.WIFI_BAND_5_GHZ, customRssi5)
                .setRssiThresholds(WIFI_BAND_6_GHZ, customRssi6)
                .setUserConnectChoiceOverrideEnabled(false)
                .setLastSelectionWeightEnabled(false)
                .build();

        mWifiServiceImpl.setNetworkSelectionConfig(customConfig);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setNetworkSelectionConfig(customConfig);

        mWifiServiceImpl.getNetworkSelectionConfig(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(customConfig);

        // resetting the RSSI thresholds returns the config with RSSI from ScoringParams
        WifiNetworkSelectionConfig resetConfig = new WifiNetworkSelectionConfig.Builder()
                .setRssiThresholds(ScanResult.WIFI_BAND_24_GHZ, resetArray)
                .setRssiThresholds(ScanResult.WIFI_BAND_5_GHZ, resetArray)
                .setRssiThresholds(WIFI_BAND_6_GHZ, defaultRssi6)
                .build();

        WifiNetworkSelectionConfig resetExpectedConfig = new WifiNetworkSelectionConfig.Builder()
                .setRssiThresholds(ScanResult.WIFI_BAND_24_GHZ, defaultRssi2)
                .setRssiThresholds(ScanResult.WIFI_BAND_5_GHZ, defaultRssi5)
                .setRssiThresholds(WIFI_BAND_6_GHZ, defaultRssi6)
                .build();

        mWifiServiceImpl.setNetworkSelectionConfig(resetConfig);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setNetworkSelectionConfig(resetConfig);

        mWifiServiceImpl.getNetworkSelectionConfig(listener);
        mLooper.dispatchAll();

        inOrder.verify(listener).onResult(resetExpectedConfig);
    }

    @Test
    public void testSetThirdPartyAppEnablingWifiConfirmationDialogEnabled() throws Exception {
        boolean enable = true;

        // no permission to call APIs
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt()))
                .thenReturn(false);
        assertThrows(SecurityException.class, () ->
                mWifiServiceImpl.setThirdPartyAppEnablingWifiConfirmationDialogEnabled(enable));
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.isThirdPartyAppEnablingWifiConfirmationDialogEnabled());

        // has permission to call APIs
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mWifiServiceImpl.setThirdPartyAppEnablingWifiConfirmationDialogEnabled(enable);
        verify(mWifiSettingsConfigStore).put(
                eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI), eq(enable));
        verify(mWifiSettingsConfigStore).put(
                eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API), eq(true));
        verify(mLastCallerInfoManager).put(
                eq(WifiManager.API_SET_THIRD_PARTY_APPS_ENABLING_WIFI_CONFIRMATION_DIALOG),
                anyInt(), anyInt(), anyInt(), any(), eq(enable));

        // get value before set by API
        when(mWifiSettingsConfigStore.get(
                eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API)))
                .thenReturn(false);
        when(mResources.getBoolean(
                R.bool.config_showConfirmationDialogForThirdPartyAppsEnablingWifi))
                .thenReturn(false);
        assertFalse(mWifiServiceImpl.isThirdPartyAppEnablingWifiConfirmationDialogEnabled());
        verify(mWifiSettingsConfigStore, never())
                .get(eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI));

        // get value after set by API
        when(mWifiSettingsConfigStore.get(
                eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API)))
                .thenReturn(true);
        when(mWifiSettingsConfigStore.get(eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI)))
                .thenReturn(true);
        assertTrue(mWifiServiceImpl.isThirdPartyAppEnablingWifiConfirmationDialogEnabled());
        verify(mWifiSettingsConfigStore).get(eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI));
        verify(mWifiSettingsConfigStore, times(2)).get(
                eq(SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API));
    }

    @Test(expected = SecurityException.class)
    public void testAllowAutojoinGlobalFailureNoPermission() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), anyString())).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), anyString())).thenReturn(false);
        mWifiServiceImpl.allowAutojoinGlobal(true, TEST_PACKAGE_NAME, mExtras);
    }

    @Test
    public void testAllowAutojoinGlobalWithPermission() throws Exception {
        // verify allowAutojoinGlobal with MANAGE_WIFI_NETWORK_SELECTION
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(anyInt(), anyString())).thenReturn(false);
        mWifiServiceImpl.allowAutojoinGlobal(true, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setAutoJoinEnabledExternal(true, false);

        // verify allowAutojoinGlobal with NETWORK_SETTINGS
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isAdmin(anyInt(), anyString())).thenReturn(false);
        mWifiServiceImpl.allowAutojoinGlobal(true, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, times(2)).setAutoJoinEnabledExternal(true, false);

        // verify allowAutojoinGlobal with device admin
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isAdmin(anyInt(), anyString())).thenReturn(true);
        mWifiServiceImpl.allowAutojoinGlobal(true, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setAutoJoinEnabledExternal(true, true);
    }

    @Test
    public void testAutoJoinGlobalWithAttributionSourceChain() {
        assumeTrue(SdkLevel.isAtLeastS());

        // verify allowAutojoinGlobal with device admin in attribution source chain
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        when(mWifiPermissionsUtil.isAdmin(anyInt(), anyString())).thenReturn(false);
        // mock attribution chain
        AttributionSource attributionSource = mock(AttributionSource.class);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        when(attributionSource.getUid()).thenReturn(TEST_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(attributionSource.getNext()).thenReturn(originalCaller);
        // mock the original caller to be device admin via the isAdmin check
        when(mWifiPermissionsUtil.isAdmin(OTHER_TEST_UID, TEST_PACKAGE_NAME_OTHER))
                .thenReturn(true);
        mWifiServiceImpl.allowAutojoinGlobal(true, TEST_PACKAGE_NAME, mAttribution);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setAutoJoinEnabledExternal(true, true);

        // mock the original caller to be not a device admin and then verify again.
        when(mWifiPermissionsUtil.isAdmin(OTHER_TEST_UID, TEST_PACKAGE_NAME_OTHER))
                .thenReturn(false);
        mWifiServiceImpl.allowAutojoinGlobal(false, TEST_PACKAGE_NAME, mAttribution);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setAutoJoinEnabledExternal(false, false);

        // mock the original caller to be device admin via the isLegacyDeviceAdmin check
        when(mWifiPermissionsUtil.isLegacyDeviceAdmin(OTHER_TEST_UID, TEST_PACKAGE_NAME_OTHER))
                .thenReturn(true);
        mWifiServiceImpl.allowAutojoinGlobal(true, TEST_PACKAGE_NAME, mAttribution);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, times(2)).setAutoJoinEnabledExternal(true, true);
    }

    @Test
    public void testQueryAutojoinGlobal_Exceptions() {
        // good inputs should result in no exceptions.
        IBooleanListener listener = mock(IBooleanListener.class);
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.queryAutojoinGlobal(null));

        // No permission ==> SecurityException
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.queryAutojoinGlobal(listener));
    }

    @Test
    public void testQueryAutojoinGlobal_GoodCase() throws RemoteException {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        IBooleanListener listener = mock(IBooleanListener.class);

        InOrder inOrder = inOrder(listener);
        when(mWifiConnectivityManager.getAutoJoinEnabledExternal()).thenReturn(true);
        mWifiServiceImpl.queryAutojoinGlobal(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(true);

        when(mWifiConnectivityManager.getAutoJoinEnabledExternal()).thenReturn(false);
        mWifiServiceImpl.queryAutojoinGlobal(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(false);
    }

    @Test(expected = SecurityException.class)
    public void testSetSsidsDoNotBlocklist_NoPermission() throws Exception {
        // by default no permissions are given so the call should fail.
        mWifiServiceImpl.setSsidsAllowlist(TEST_PACKAGE_NAME,
                Collections.EMPTY_LIST);
    }

    @Test
    public void testSetSsidsDoNotBlocklist_WithPermission() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        // verify setting an empty list
        mWifiServiceImpl.setSsidsAllowlist(TEST_PACKAGE_NAME,
                Collections.EMPTY_LIST);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).setSsidsAllowlist(Collections.EMPTY_LIST);

        // verify setting a list of valid SSIDs
        List<WifiSsid> expectedSsids = new ArrayList<>();
        expectedSsids.add(WifiSsid.fromString(TEST_SSID_WITH_QUOTES));
        mWifiServiceImpl.setSsidsAllowlist(TEST_PACKAGE_NAME, expectedSsids);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).setSsidsAllowlist(expectedSsids);
    }

    @Test
    public void testSetSsidsDoNotBlocklist_WithPermissionAndroidT()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);

        List<WifiSsid> expectedSsids = new ArrayList<>();
        expectedSsids.add(WifiSsid.fromString(TEST_SSID_WITH_QUOTES));
        mWifiServiceImpl.setSsidsAllowlist(TEST_PACKAGE_NAME, expectedSsids);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).setSsidsAllowlist(expectedSsids);
    }

    @Test
    public void testAllowAutojoinFailureNoNetworkSettingsPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.allowAutojoin(0, true);
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            // Test succeeded
        }
    }

    @Test
    public void testAllowAutojoinOnSuggestionNetwork() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowAutojoin = false;
        config.fromWifiNetworkSuggestion = true;
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);
        when(mWifiNetworkSuggestionsManager.allowNetworkSuggestionAutojoin(any(), anyBoolean()))
                .thenReturn(true);
        mWifiServiceImpl.allowAutojoin(0, true);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).getConfiguredNetwork(0);
        verify(mWifiNetworkSuggestionsManager).allowNetworkSuggestionAutojoin(any(), anyBoolean());
        verify(mWifiConfigManager).allowAutojoin(anyInt(), anyBoolean());
        verify(mWifiMetrics).logUserActionEvent(eq(UserActionEvent.EVENT_CONFIGURE_AUTO_CONNECT_ON),
                anyInt());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_ALLOW_AUTOJOIN), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    @Test
    public void testAllowAutojoinOnSavedNetwork() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowAutojoin = false;
        config.fromWifiNetworkSuggestion = false;
        config.fromWifiNetworkSpecifier = false;
        when(mWifiConfigManager.getConfiguredNetwork(0)).thenReturn(config);
        mWifiServiceImpl.allowAutojoin(0, true);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).getConfiguredNetwork(0);
        verify(mWifiNetworkSuggestionsManager, never())
                .allowNetworkSuggestionAutojoin(any(), anyBoolean());
        verify(mWifiConfigManager).allowAutojoin(anyInt(), anyBoolean());
    }

    @Test
    public void testAllowAutojoinOnWifiNetworkSpecifier() {
        WifiConfiguration config = new WifiConfiguration();
        config.fromWifiNetworkSpecifier = true;
        when(mWifiConfigManager.getConfiguredNetwork(0)).thenReturn(config);
        mWifiServiceImpl.allowAutojoin(0, true);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).getConfiguredNetwork(0);
        verify(mWifiNetworkSuggestionsManager, never())
                .allowNetworkSuggestionAutojoin(config, true);
        verify(mWifiConfigManager, never()).allowAutojoin(0, true);
    }

    @Test
    public void testAllowAutojoinOnSavedPasspointNetwork() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        when(mWifiConfigManager.getConfiguredNetwork(0)).thenReturn(config);
        when(mWifiNetworkSuggestionsManager.allowNetworkSuggestionAutojoin(any(), anyBoolean()))
                .thenReturn(true);
        mWifiServiceImpl.allowAutojoin(0, true);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).getConfiguredNetwork(0);
        verify(mWifiNetworkSuggestionsManager, never())
                .allowNetworkSuggestionAutojoin(config, true);
        verify(mWifiConfigManager, never()).allowAutojoin(0, true);
    }

    /**
     * Test that setMacRandomizationSettingPasspointEnabled is protected by NETWORK_SETTINGS
     * permission.
     */
    @Test
    public void testSetMacRandomizationSettingPasspointEnabledFailureNoNetworkSettingsPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setMacRandomizationSettingPasspointEnabled("TEST_FQDN", true);
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            // Test succeeded
        }
    }

    /**
     * Test that setMacRandomizationSettingPasspointEnabled makes the appropriate calls.
     */
    @Test
    public void testSetMacRandomizationSettingPasspointEnabled() throws Exception {
        mWifiServiceImpl.setMacRandomizationSettingPasspointEnabled("TEST_FQDN", true);
        mLooper.dispatchAll();
        verify(mPasspointManager).enableMacRandomization("TEST_FQDN", true);
    }

    /**
     * Test that setPasspointMeteredOverride is protected by NETWORK_SETTINGS permission.
     */
    @Test
    public void testSetPasspointMeteredOverrideFailureNoNetworkSettingsPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setPasspointMeteredOverride("TEST_FQDN", METERED_OVERRIDE_METERED);
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            // Test succeeded
        }
    }

    /**
     * Test that setPasspointMeteredOverride makes the appropriate calls.
     */
    @Test
    public void testSetPasspointMeteredOverride() throws Exception {
        mWifiServiceImpl.setPasspointMeteredOverride("TEST_FQDN", METERED_OVERRIDE_METERED);
        mLooper.dispatchAll();
        verify(mPasspointManager).setMeteredOverride("TEST_FQDN", METERED_OVERRIDE_METERED);
    }

    /**
     * Test handle boot completed sequence.
     */
    @Test
    public void testHandleBootCompleted() throws Exception {
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();

        verify(mWifiNetworkFactory).register();
        verify(mUntrustedWifiNetworkFactory).register();
        verify(mOemWifiNetworkFactory).register();
        verify(mRestrictedWifiNetworkFactory).register();
        verify(mMultiInternetWifiNetworkFactory).register();
        verify(mPasspointManager).initializeProvisioner(any());
        verify(mWifiP2pConnection).handleBootCompleted();
        verify(mWifiCountryCode).registerListener(any(WifiCountryCode.ChangeListener.class));
        verify(mWifiDeviceStateChangeManager).handleBootCompleted();
    }

    /**
     * Test handle user switch sequence.
     */
    @Test
    public void testHandleUserSwitch() throws Exception {
        mWifiServiceImpl.handleUserSwitch(5);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).handleUserSwitch(5);
        verify(mWifiNotificationManager).createNotificationChannels();
        verify(mWifiNetworkSuggestionsManager).resetNotification();
        verify(mWifiCarrierInfoManager).resetNotification();
        verify(mOpenNetworkNotifier).clearPendingNotification(false);
        verify(mWakeupController).resetNotification();
    }

    /**
     * Test handle user unlock sequence.
     */
    @Test
    public void testHandleUserUnlock() throws Exception {
        mWifiServiceImpl.handleUserUnlock(5);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).handleUserUnlock(5);
    }

    /**
     * Test handle user stop sequence.
     */
    @Test
    public void testHandleUserStop() throws Exception {
        mWifiServiceImpl.handleUserStop(5);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).handleUserStop(5);
    }

    /**
     * Test register scan result callback without permission.
     */
    @Test(expected = SecurityException.class)
    public void testRegisterScanResultCallbackWithMissingPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        mWifiServiceImpl.registerScanResultsCallback(mScanResultsCallback);
    }

    /**
     * Test unregister scan result callback without permission.
     */
    @Test(expected = SecurityException.class)
    public void testUnregisterScanResultCallbackWithMissingPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        mWifiServiceImpl.unregisterScanResultsCallback(mScanResultsCallback);
    }

    /**
     * Test register scan result callback with illegal argument.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterScanResultCallbackWithIllegalArgument() throws Exception {
        mWifiServiceImpl.registerScanResultsCallback(null);
    }

    /**
     * Test register and unregister callback will go to ScanRequestProxy;
     */
    @Test
    public void testRegisterUnregisterScanResultCallback() throws Exception {
        mWifiServiceImpl.registerScanResultsCallback(mScanResultsCallback);
        mLooper.dispatchAll();
        verify(mScanRequestProxy).registerScanResultsCallback(mScanResultsCallback);
        mWifiServiceImpl.unregisterScanResultsCallback(mScanResultsCallback);
        mLooper.dispatchAll();
        verify(mScanRequestProxy).unregisterScanResultsCallback(mScanResultsCallback);
    }

    /**
     * Test register callback without ACCESS_WIFI_STATE permission.
     */
    @Test(expected = SecurityException.class)
    public void testRegisterSuggestionNetworkCallbackWithMissingAccessWifiPermission() {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        mWifiServiceImpl.registerSuggestionConnectionStatusListener(
                mSuggestionConnectionStatusListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
    }

    /**
     * Test register callback without ACCESS_FINE_LOCATION permission.
     */
    @Test(expected = SecurityException.class)
    public void testRegisterSuggestionNetworkCallbackWithMissingFinePermission() {
        doNothing().when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        if (SdkLevel.isAtLeastT()) {
            doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                    .enforceLocationPermissionInManifest(anyInt(), eq(false));
        } else {
            doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                    .enforceLocationPermission(anyString(), anyString(), anyInt());
        }
        mWifiServiceImpl.registerSuggestionConnectionStatusListener(
                mSuggestionConnectionStatusListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
    }

    /**
     * Test register callback without callback
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterSuggestionNetworkCallbackWithIllegalArgument() {
        mWifiServiceImpl.registerSuggestionConnectionStatusListener(null, TEST_PACKAGE_NAME,
                TEST_FEATURE_ID);
    }

    /**
     * Test unregister callback without permission.
     */
    @Test(expected = SecurityException.class)
    public void testUnregisterSuggestionNetworkCallbackWithMissingPermission() {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        mWifiServiceImpl.unregisterSuggestionConnectionStatusListener(
                mSuggestionConnectionStatusListener, TEST_PACKAGE_NAME);
    }

    /**
     * Test register nad unregister callback will go to WifiNetworkSuggestionManager
     */
    @Test
    public void testRegisterUnregisterSuggestionNetworkCallback() throws Exception {
        mWifiServiceImpl.registerSuggestionConnectionStatusListener(
                mSuggestionConnectionStatusListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(mWifiNetworkSuggestionsManager).registerSuggestionConnectionStatusListener(
                eq(mSuggestionConnectionStatusListener), eq(TEST_PACKAGE_NAME), anyInt());
        mWifiServiceImpl.unregisterSuggestionConnectionStatusListener(
                mSuggestionConnectionStatusListener, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiNetworkSuggestionsManager).unregisterSuggestionConnectionStatusListener(
                eq(mSuggestionConnectionStatusListener), eq(TEST_PACKAGE_NAME), anyInt());
    }

    /**
     * Test to verify that the arguments are verified before dispatching the operation
     *
     * Steps: call acquireWifiLock with invalid arguments.
     * Expected: the call should throw proper Exception.
     */
    @Test
    public void acquireWifiLockShouldThrowExceptionOnInvalidArgs() {
        final int wifiLockModeInvalid = -1;

        // Package name is null.
        assertThrows(NullPointerException.class,
                () -> mWifiServiceImpl.acquireWifiLock(mAppBinder,
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "", null, null, null));

        // Invalid Lock mode.
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.acquireWifiLock(mAppBinder, wifiLockModeInvalid, "",
                        new WorkSource(TEST_UID, TEST_PACKAGE_NAME), TEST_PACKAGE_NAME, null));
    }

    private void setupReportActivityInfo() {
        WifiLinkLayerStats stats = new WifiLinkLayerStats();
        stats.on_time = 1000;
        stats.tx_time = 1;
        stats.rx_time = 2;
        stats.tx_time_per_level = new int[] {3, 4, 5};
        stats.on_time_scan = 6;
        when(mClientModeManager.getWifiLinkLayerStats()).thenReturn(stats);
        when(mPowerProfile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_IDLE))
                .thenReturn(7.0);
        when(mPowerProfile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_RX))
                .thenReturn(8.0);
        when(mPowerProfile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_TX))
                .thenReturn(9.0);
        when(mPowerProfile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_OPERATING_VOLTAGE))
                .thenReturn(10000.0);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(9999L);
    }

    private void validateWifiActivityEnergyInfo(WifiActivityEnergyInfo info) {
        assertNotNull(info);
        assertEquals(9999L, info.getTimeSinceBootMillis());
        assertEquals(WifiActivityEnergyInfo.STACK_STATE_STATE_IDLE, info.getStackState());
        assertEquals(1, info.getControllerTxDurationMillis());
        assertEquals(2, info.getControllerRxDurationMillis());
        assertEquals(6, info.getControllerScanDurationMillis());
        assertEquals(997, info.getControllerIdleDurationMillis());
    }

    /**
     * Tests that {@link WifiServiceImpl#getWifiActivityEnergyInfoAsync} throws
     * {@link SecurityException} if the caller doesn't have the necessary permissions.
     */
    @Test(expected = SecurityException.class)
    public void getWifiActivityEnergyInfoAsyncNoPermission() throws Exception {
        doThrow(SecurityException.class)
                .when(mContext).enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE), any());
        mWifiServiceImpl.getWifiActivityEnergyInfoAsync(mOnWifiActivityEnergyInfoListener);
    }

    /**
     * Tests that {@link WifiServiceImpl#getWifiActivityEnergyInfoAsync} passes null to the listener
     * if link layer stats is unsupported.
     */
    @Test
    public void getWifiActivityEnergyInfoAsyncFeatureUnsupported() throws Exception {
        when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(0L);

        mWifiServiceImpl.getWifiActivityEnergyInfoAsync(mOnWifiActivityEnergyInfoListener);
        verify(mOnWifiActivityEnergyInfoListener).onWifiActivityEnergyInfo(null);
    }

    /**
     * Tests that {@link WifiServiceImpl#getWifiActivityEnergyInfoAsync} passes the expected values
     * to the listener on success.
     */
    @Test
    public void getWifiActivityEnergyInfoAsyncSuccess() throws Exception {
        when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(Long.MAX_VALUE);
        setupReportActivityInfo();
        mWifiServiceImpl.getWifiActivityEnergyInfoAsync(mOnWifiActivityEnergyInfoListener);
        mLooper.dispatchAll();
        ArgumentCaptor<WifiActivityEnergyInfo> infoCaptor =
                ArgumentCaptor.forClass(WifiActivityEnergyInfo.class);
        verify(mOnWifiActivityEnergyInfoListener).onWifiActivityEnergyInfo(infoCaptor.capture());
        validateWifiActivityEnergyInfo(infoCaptor.getValue());
    }

    /**
     * Tests that {@link WifiServiceImpl#getWifiActivityEnergyInfoAsync} throws exception when
     * listener is null
     */
    @Test
    public void getWifiActivityEnergyInfoWithNullListener() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getWifiActivityEnergyInfoAsync(null));
    }

    @Test
    public void testCarrierConfigChangeUpdateSoftApCapability() throws Exception {
        lenient().when(SubscriptionManager.getActiveDataSubscriptionId())
                .thenReturn(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)),
                isNull(),
                any(Handler.class));

        // Send the broadcast
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).updateSoftApCapability(any(),
                eq(WifiManager.IFACE_IP_MODE_TETHERED));
    }

    @Test
    public void testPhoneActiveDataSubscriptionIdChangedUpdateSoftApCapability() throws Exception {
        lenient().when(SubscriptionManager.getActiveDataSubscriptionId())
                .thenReturn(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)),
                isNull(),
                any(Handler.class));
        ArgumentCaptor<PhoneStateListener> phoneStateListenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        verify(mTelephonyManager).listen(phoneStateListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE));
        mPhoneStateListener = phoneStateListenerCaptor.getValue();
        assertNotNull(mPhoneStateListener);
        mPhoneStateListener.onActiveDataSubscriptionIdChanged(2);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).updateSoftApCapability(any(),
                eq(WifiManager.IFACE_IP_MODE_TETHERED));
    }

    /**
     * Verify that the call to getWifiConfigsForMatchedNetworkSuggestions is not redirected to
     * specific API getWifiConfigForMatchedNetworkSuggestionsSharedWithUser when the caller doesn't
     * have NETWORK_SETTINGS permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testGetWifiConfigsForMatchedNetworkSuggestionsWithoutPermissions() {
        mWifiServiceImpl.getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(new ArrayList<>());
    }

    /**
     * Verify that the call to getWifiConfigsForMatchedNetworkSuggestions is redirected to
     * specific API getWifiConfigForMatchedNetworkSuggestionsSharedWithUser when the caller
     * have NETWORK_SETTINGS.
     */
    @Test
    public void testGetWifiConfigsForMatchedNetworkSuggestionsWithSettingPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        mWifiServiceImpl
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(createScanResultList());
        mLooper.stopAutoDispatch();
        verify(mWifiNetworkSuggestionsManager)
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any());
    }

    /**
     * Verify that the call to getWifiConfigsForMatchedNetworkSuggestions is redirected to
     * specific API getWifiConfigForMatchedNetworkSuggestionsSharedWithUser when the caller
     * have NETWORK_SETUP_WIZARD.
     */
    @Test
    public void testGetWifiConfigsForMatchedNetworkSuggestionsWithSetupWizardPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        mWifiServiceImpl
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(createScanResultList());
        mLooper.stopAutoDispatch();
        verify(mWifiNetworkSuggestionsManager)
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any());
    }

    @Test
    public void testGetWifiConfigsForMatchedNetworkSuggestionsWithInvalidScanResults() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(new ArrayList<>());
        mLooper.dispatchAll();
        verify(mWifiNetworkSuggestionsManager, never())
                .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any());
    }

    /**
     * Verify that a call to setWifiConnectedNetworkScorer throws a SecurityException if
     * the caller does not have WIFI_UPDATE_USABILITY_STATS_SCORE permission.
     */
    @Test
    public void testSetNetworkScorerThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that a call to setWifiConnectedNetworkScorer throws an IllegalArgumentException
     * if the parameters are not provided.
     */
    @Test
    public void testSetScorerThrowsIllegalArgumentExceptionOnInvalidArguments() {
        try {
            mWifiServiceImpl.setWifiConnectedNetworkScorer(mAppBinder, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify that a call to clearWifiConnectedNetworkScorer throws a SecurityException if
     * the caller does not have WIFI_UPDATE_USABILITY_STATS_SCORE permission.
     */
    @Test
    public void testClearNetworkScorerThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE),
                                eq("WifiService"));
        try {
            mWifiServiceImpl.clearWifiConnectedNetworkScorer();
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that setWifiConnectedNetworkScorer sets scorer to {@link WifiScoreReport}.
     */
    @Test
    public void testSetWifiConnectedNetworkScorerAndVerify() throws Exception {
        mLooper.startAutoDispatch();
        mWifiServiceImpl.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer);
        mLooper.stopAutoDispatch();
        verify(mActiveModeWarden).setWifiConnectedNetworkScorer(
                mAppBinder, mWifiConnectedNetworkScorer, myUid());
    }

    /**
     * Verify that clearWifiConnectedNetworkScorer clears scorer from {@link WifiScoreReport}.
     */
    @Test
    public void testClearWifiConnectedNetworkScorerAndVerify() throws Exception {
        mWifiServiceImpl.clearWifiConnectedNetworkScorer();
        mLooper.dispatchAll();
        verify(mActiveModeWarden).clearWifiConnectedNetworkScorer();
    }

    /**
     * Verify that a call to addWifiCustomDhcpOptions throws a SecurityException if
     * the caller does not have NETWORK_SETTINGS or OVERRIDE_WIFI_CONFIG permission.
     */
    @Test
    public void testAddCustomDhcpOptionsThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_SETTINGS))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.OVERRIDE_WIFI_CONFIG))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        try {
            mWifiServiceImpl.addCustomDhcpOptions(WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                    TEST_OUI, new ArrayList<DhcpOption>());
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that a call to removeWifiCustomDhcpOptions throws a SecurityException if
     * the caller does not have NETWORK_SETTINGS or OVERRIDE_WIFI_CONFIG permission.
     */
    @Test
    public void testRemoveCustomDhcpOptionsThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_SETTINGS))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.OVERRIDE_WIFI_CONFIG))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        try {
            mWifiServiceImpl.removeCustomDhcpOptions(WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                    TEST_OUI);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that addWifiCustomDhcpOptions adds DHCP option.
     */
    @Test
    public void testAddCustomDhcpOptionsAndVerify() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiServiceImpl.addCustomDhcpOptions(WifiSsid.fromString(TEST_SSID_WITH_QUOTES), TEST_OUI,
                new ArrayList<DhcpOption>());
        mLooper.dispatchAll();
        verify(mWifiConfigManager).addCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES), TEST_OUI, new ArrayList<DhcpOption>());
    }

    /**
     * Verify that removeWifiCustomDhcpOptions removes DHCP option.
     */
    @Test
    public void testRemoveCustomDhcpOptionsAndVerify() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiServiceImpl.removeCustomDhcpOptions(WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                TEST_OUI);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).removeCustomDhcpOptions(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES), TEST_OUI);
    }

    @Test
    public void getSupportedFeaturesVerboseLoggingThrottled() {
        mWifiServiceImpl.enableVerboseLogging(
                WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED); // this logs
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000L);
        when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(0x110L);
        mWifiServiceImpl.getSupportedFeatures();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1001L);
        mWifiServiceImpl.getSupportedFeatures(); // should not log
        when(mClock.getElapsedSinceBootMillis()).thenReturn(5000L);
        mWifiServiceImpl.getSupportedFeatures();
        when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(0x100L);
        mWifiServiceImpl.getSupportedFeatures();
        verify(mLog, times(4)).info(any());
    }

    /**
     * Verify startRestrictingAutoJoinToSubscriptionId is guarded by NETWORK_SETTINGS
     * permission.
     */
    @Test
    public void testStartTemporarilyDisablingAllNonCarrierMergedWifiPermission() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        try {
            mWifiServiceImpl.startRestrictingAutoJoinToSubscriptionId(1);
            fail();
        } catch (SecurityException e) {
            // pass
        }
    }

    /**
     * Verify startRestrictingAutoJoinToSubscriptionId works properly with permission.
     */
    @Test
    public void testStartTemporarilyDisablingAllNonCarrierMergedWifi() {
        assumeTrue(SdkLevel.isAtLeastS());
        List<ClientModeManager> cmmList = new ArrayList<>();
        ConcreteClientModeManager localOnlyCmm = mock(ConcreteClientModeManager.class);
        ConcreteClientModeManager secondaryTransientCmm = mock(ConcreteClientModeManager.class);
        when(localOnlyCmm.getRole()).thenReturn(ROLE_CLIENT_LOCAL_ONLY);
        when(secondaryTransientCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        cmmList.add(mClientModeManager);
        cmmList.add(localOnlyCmm);
        cmmList.add(secondaryTransientCmm);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(cmmList);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mWifiServiceImpl.startRestrictingAutoJoinToSubscriptionId(1);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).startRestrictingAutoJoinToSubscriptionId(1);
        verify(mWifiConnectivityManager).clearCachedCandidates();
        verify(localOnlyCmm, never()).disconnect();
        verify(secondaryTransientCmm).disconnect();
        verify(mClientModeManager).disconnect();
    }

    /**
     * Verify stopRestrictingAutoJoinToSubscriptionId is guarded by NETWORK_SETTINGS
     * and NETWORK_SETUP_WIZARD permission.
     */
    @Test
    public void testStopTemporarilyDisablingAllNonCarrierMergedWifiPermission() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        try {
            mWifiServiceImpl.stopRestrictingAutoJoinToSubscriptionId();
            fail();
        } catch (SecurityException e) {
            // pass
        }
    }

    /**
     * Verify stopRestrictingAutoJoinToSubscriptionId works properly with permission.
     */
    @Test
    public void testStopTemporarilyDisablingAllNonCarrierMergedWifi() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.stopRestrictingAutoJoinToSubscriptionId();
        mLooper.dispatchAll();
        verify(mWifiConfigManager).stopRestrictingAutoJoinToSubscriptionId();
    }

    @Test(expected = SecurityException.class)
    public void testGetCountryCodeThrowsException() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                any(), any(), anyInt(), any())).thenReturn(false);
        mWifiServiceImpl.getCountryCode(TEST_PACKAGE_NAME, TEST_FEATURE_ID);
    }

    @Test
    public void testGetCountryCode() {
        // verify get country code with network settings permission.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                any(), any(), anyInt(), any())).thenReturn(false);
        mWifiServiceImpl.getCountryCode(TEST_PACKAGE_NAME, TEST_FEATURE_ID);

        // verify get country code with coarse location permission.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                any(), any(), anyInt(), any())).thenReturn(true);
        mWifiServiceImpl.getCountryCode(TEST_PACKAGE_NAME, TEST_FEATURE_ID);
    }

    @Test
    public void testSetScanThrottleEnabledWithNetworkSettingsPermission() {
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.setScanThrottleEnabled(true);
        verify(mScanRequestProxy).setScanThrottleEnabled(true);

        mWifiServiceImpl.setScanThrottleEnabled(false);
        verify(mScanRequestProxy).setScanThrottleEnabled(false);
    }

    @Test(expected = SecurityException.class)
    public void testSetScanThrottleEnabledWithNoNetworkSettingsPermission() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));

        mWifiServiceImpl.setScanThrottleEnabled(true);
        verify(mScanRequestProxy, never()).setScanThrottleEnabled(true);
    }

    @Test
    public void testIsScanThrottleEnabled() {
        when(mScanRequestProxy.isScanThrottleEnabled()).thenReturn(true);
        assertTrue(mWifiServiceImpl.isScanThrottleEnabled());
        verify(mScanRequestProxy).isScanThrottleEnabled();
    }

    @Test
    public void testSetAutoWakeupEnabledWithNetworkSettingsPermission() {
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.setAutoWakeupEnabled(true);
        verify(mWakeupController).setEnabled(true);

        mWifiServiceImpl.setAutoWakeupEnabled(false);
        verify(mWakeupController).setEnabled(false);
    }

    @Test(expected = SecurityException.class)
    public void testSetAutoWakeupEnabledWithNoNetworkSettingsPermission() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));

        mWifiServiceImpl.setAutoWakeupEnabled(true);
        mLooper.dispatchAll();
        verify(mWakeupController, never()).setEnabled(true);
    }

    @Test
    public void testIsAutoWakeupEnabled() {
        when(mWakeupController.isEnabled()).thenReturn(true);
        assertTrue(mWifiServiceImpl.isAutoWakeupEnabled());
        verify(mWakeupController).isEnabled();
    }

    @Test
    public void testSetScanAlwaysAvailableWithNetworkSettingsPermission() {
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.setScanAlwaysAvailable(true, TEST_PACKAGE_NAME);
        verify(mSettingsStore).handleWifiScanAlwaysAvailableToggled(true);
        verify(mActiveModeWarden).scanAlwaysModeChanged();

        mWifiServiceImpl.setScanAlwaysAvailable(false, TEST_PACKAGE_NAME);
        verify(mSettingsStore).handleWifiScanAlwaysAvailableToggled(false);
        verify(mActiveModeWarden, times(2)).scanAlwaysModeChanged();
    }

    @Test(expected = SecurityException.class)
    public void testSetScanAlwaysAvailableWithNoNetworkSettingsPermission() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));

        mWifiServiceImpl.setScanAlwaysAvailable(true, TEST_PACKAGE_NAME);
        verify(mSettingsStore, never()).handleWifiScanAlwaysAvailableToggled(anyBoolean());
        verify(mActiveModeWarden, never()).scanAlwaysModeChanged();
    }

    @Test
    public void testIsScanAlwaysAvailable() {
        when(mSettingsStore.isScanAlwaysAvailableToggleEnabled()).thenReturn(true);
        assertTrue(mWifiServiceImpl.isScanAlwaysAvailable());
        verify(mSettingsStore).isScanAlwaysAvailableToggleEnabled();
    }

    private List<ScanResult> createScanResultList() {
        return Collections.singletonList(new ScanResult.Builder(WifiSsid.fromUtf8Text(TEST_SSID),
                TEST_BSSID)
                .setHessid(1245)
                .setCaps(TEST_CAP)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setChannelWidth(20)
                .setIs80211McRTTResponder(true)
                .build());
    }

    private void sendCountryCodeChangedBroadcast(String countryCode) {
        Intent intent = new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, countryCode);
        assertNotNull(mBroadcastReceiverCaptor.getValue());
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
    }

    @Test
    public void testCountryCodeBroadcastHanding() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)),
                isNull(),
                any(Handler.class));
        sendCountryCodeChangedBroadcast("US");
        verify(mWifiCountryCode).setTelephonyCountryCodeAndUpdate(any());
    }

    @Test
    public void testDumpShouldDumpWakeupController() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        mLooper.startAutoDispatch();
        mWifiServiceImpl.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mWakeupController).dump(any(), any(), any());
        verify(mPasspointNetworkNominateHelper).dump(any());
    }

    /**
     * Test register listener without permission.
     */
    @Test(expected = SecurityException.class)
    public void testAddSuggestionUserApprovalStatusListenerWithMissingPermission() {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        mWifiServiceImpl.addSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener, TEST_PACKAGE_NAME);
    }

    /**
     * Test register listener from background user.
     */
    @Test(expected = SecurityException.class)
    public void testAddSuggestionUserApprovalStatusListenerFromBackgroundUser() {
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(anyInt()))
                .thenReturn(false);
        mWifiServiceImpl.addSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener, TEST_PACKAGE_NAME);
    }

    /**
     * Test unregister listener from background user.
     */
    @Test(expected = SecurityException.class)
    public void testRemoveSuggestionUserApprovalStatusListenerFromBackgroundUser() {
        when(mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(anyInt()))
                .thenReturn(false);
        mWifiServiceImpl.removeSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener, TEST_PACKAGE_NAME);
    }

    /**
     * Test register listener without listener
     */
    @Test(expected = NullPointerException.class)
    public void testAddSuggestionUserApprovalStatusListenerWithIllegalArgument() {
        mWifiServiceImpl.addSuggestionUserApprovalStatusListener(null, TEST_PACKAGE_NAME);
    }

    /**
     * Test unregister callback without permission.
     */
    @Test(expected = SecurityException.class)
    public void testUnregisterSuggestionUserApprovalStatusListenerWithMissingPermission() {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        mWifiServiceImpl.removeSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener, TEST_PACKAGE_NAME);
    }

    /**
     * Test add and remove listener will go to WifiNetworkSuggestionManager
     */
    @Test
    public void testAddRemoveSuggestionUserApprovalStatusListener() {
        mWifiServiceImpl.addSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiNetworkSuggestionsManager).addSuggestionUserApprovalStatusListener(
                eq(mSuggestionUserApprovalStatusListener), eq(TEST_PACKAGE_NAME), anyInt());

        mWifiServiceImpl.removeSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiNetworkSuggestionsManager).removeSuggestionUserApprovalStatusListener(
                eq(mSuggestionUserApprovalStatusListener), eq(TEST_PACKAGE_NAME), anyInt());
    }

    @Test
    public void testGetDhcpInfo() throws Exception {
        DhcpResultsParcelable dhcpResultsParcelable = new DhcpResultsParcelable();
        dhcpResultsParcelable.leaseDuration = 100;
        when(mClientModeManager.syncGetDhcpResultsParcelable()).thenReturn(dhcpResultsParcelable);

        mLooper.startAutoDispatch();
        DhcpInfo dhcpInfo = mWifiServiceImpl.getDhcpInfo(TEST_PACKAGE);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(dhcpResultsParcelable.leaseDuration, dhcpInfo.leaseDuration);
    }

    @Test
    public void testGetDhcpInfoFromSecondaryCmmFromAppRequestingSecondaryCmm() throws Exception {
        DhcpResultsParcelable dhcpResultsParcelable = new DhcpResultsParcelable();
        dhcpResultsParcelable.leaseDuration = 100;
        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        when(secondaryCmm.getRequestorWs())
                .thenReturn(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE));
        when(secondaryCmm.syncGetDhcpResultsParcelable()).thenReturn(dhcpResultsParcelable);
        when(mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_LOCAL_ONLY, ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(Arrays.asList(secondaryCmm));

        mLooper.startAutoDispatch();
        DhcpInfo dhcpInfo = mWifiServiceImpl.getDhcpInfo(TEST_PACKAGE);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(dhcpResultsParcelable.leaseDuration, dhcpInfo.leaseDuration);
    }

    @Test
    public void testGetDhcpInfoFromPrimaryCmmFromAppNotRequestingSecondaryCmm() throws Exception {
        DhcpResultsParcelable dhcpResultsParcelable = new DhcpResultsParcelable();
        dhcpResultsParcelable.leaseDuration = 100;
        when(mClientModeManager.syncGetDhcpResultsParcelable()).thenReturn(dhcpResultsParcelable);
        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        when(secondaryCmm.getRequestorWs())
                .thenReturn(new WorkSource(Binder.getCallingUid(), TEST_PACKAGE_NAME_OTHER));
        when(mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_LOCAL_ONLY, ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(Arrays.asList(secondaryCmm));

        mLooper.startAutoDispatch();
        DhcpInfo dhcpInfo = mWifiServiceImpl.getDhcpInfo(TEST_PACKAGE);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertEquals(dhcpResultsParcelable.leaseDuration, dhcpInfo.leaseDuration);
    }

    @Test
    public void testSetEmergencyScanRequestInProgress() throws Exception {
        mWifiServiceImpl.setEmergencyScanRequestInProgress(true);
        verify(mActiveModeWarden).setEmergencyScanRequestInProgress(true);

        mWifiServiceImpl.setEmergencyScanRequestInProgress(false);
        verify(mActiveModeWarden).setEmergencyScanRequestInProgress(false);
    }

    @Test
    public void testSetCarrierNetworkOffloadEnabledWithoutPermissionThrowsException()
            throws Exception {
        try {
            mWifiServiceImpl.setCarrierNetworkOffloadEnabled(10, true, true);
            fail();
        } catch (SecurityException e) { }
    }

    @Test
    public void testSetCarrierNetworkOffloadEnabled() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.setCarrierNetworkOffloadEnabled(10, true, true);
        verify(mWifiCarrierInfoManager).setCarrierNetworkOffloadEnabled(10, true, true);

        mWifiServiceImpl.setCarrierNetworkOffloadEnabled(5, false, false);
        verify(mWifiCarrierInfoManager).setCarrierNetworkOffloadEnabled(5, false, false);
    }

    @Test
    public void testIsCarrierNetworkOffloadEnabled() throws Exception {
        when(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(10, true)).thenReturn(true);
        assertTrue(mWifiServiceImpl.isCarrierNetworkOffloadEnabled(10, true));
        verify(mWifiCarrierInfoManager).isCarrierNetworkOffloadEnabled(10, true);
    }

    @Test
    public void testSetEmergencyScanRequestWithoutPermissionThrowsException() throws Exception {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK), any());
        try {
            mWifiServiceImpl.setEmergencyScanRequestInProgress(true);
            fail();
        } catch (SecurityException e) { }
    }

    @Test
    public void testRemoveAppState() throws Exception {
        mWifiServiceImpl.removeAppState(TEST_UID, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mScanRequestProxy).clearScanRequestTimestampsForApp(TEST_PACKAGE_NAME, TEST_UID);
        verify(mWifiNetworkSuggestionsManager).removeApp(TEST_PACKAGE_NAME);
        verify(mWifiNetworkFactory).removeApp(TEST_PACKAGE_NAME);
        verify(mPasspointManager).removePasspointProviderWithPackage(TEST_PACKAGE_NAME);
    }

    @Test
    public void testRemoveAppStateWithoutPermissionThrowsException() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.NETWORK_SETTINGS), any());
        try {
            mWifiServiceImpl.removeAppState(TEST_UID, TEST_PACKAGE_NAME);
            fail();
        } catch (SecurityException e) { }
    }

    @Test
    public void testNotificationResetWithLocaleChange() {
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(Intent.ACTION_LOCALE_CHANGED)),
                isNull(),
                any(Handler.class));
        verify(mWifiNotificationManager).createNotificationChannels();
        clearInvocations(mWifiNotificationManager);

        Intent intent = new Intent(Intent.ACTION_LOCALE_CHANGED);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        verify(mWifiNotificationManager).createNotificationChannels();
        verify(mWifiNetworkSuggestionsManager).resetNotification();
        verify(mWifiCarrierInfoManager).resetNotification();
        verify(mOpenNetworkNotifier).clearPendingNotification(false);
        verify(mWakeupController).resetNotification();
    }

    /**
     * Verify that a call to setWifiScoringEnabled throws a SecurityException if the caller does
     * not have NETWORK_SETTINGS permission.
     */
    @Test
    public void testSetWifiScoringEnabledThrowsSecurityExceptionOnMissingPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS),
                eq("WifiService"));
        try {
            mWifiServiceImpl.setWifiScoringEnabled(true);
            fail("expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that setWifiScoringEnabled sets the boolean to {@link WifiSettingsStore}.
     */
    @Test
    public void testSetWifiScoringEnabledGoesToSettingsStore() {
        when(mSettingsStore.handleWifiScoringEnabled(anyBoolean())).thenReturn(true);
        assertTrue(mWifiServiceImpl.setWifiScoringEnabled(true));
        verify(mSettingsStore).handleWifiScoringEnabled(true);
    }

    @Test
    public void testEnabledTdlsWithMacAddress() {
        mWifiServiceImpl.enableTdlsWithMacAddress(TEST_BSSID, true);
        mLooper.dispatchAll();
        verify(mClientModeManager).enableTdls(TEST_BSSID, true);

        mWifiServiceImpl.enableTdlsWithMacAddress(TEST_BSSID, false);
        mLooper.dispatchAll();
        verify(mClientModeManager).enableTdls(TEST_BSSID, false);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SET_TDLS_ENABLED_WITH_MAC_ADDRESS),
                anyInt(), anyInt(), anyInt(), anyString(), eq(false));
    }

    @Test
    public void testEnabledTdlsWithMacAddressCallback() throws RemoteException {
        IBooleanListener listener = mock(IBooleanListener.class);
        InOrder inOrder = inOrder(listener);

        when(mClientModeManager.enableTdls(TEST_BSSID, true)).thenReturn(true);
        mWifiServiceImpl.enableTdlsWithRemoteMacAddress(TEST_BSSID, true, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(true);

        when(mClientModeManager.enableTdls(TEST_BSSID, false)).thenReturn(false);
        mWifiServiceImpl.enableTdlsWithRemoteMacAddress(TEST_BSSID, false, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(false);
        verify(mLastCallerInfoManager)
                .put(eq(WifiManager.API_SET_TDLS_ENABLED_WITH_MAC_ADDRESS),
                anyInt(), anyInt(), anyInt(), anyString(), eq(false));
    }

    @Test
    public void testEnabledTdlsWithIpAddressCallback() throws RemoteException {
        IBooleanListener listener = mock(IBooleanListener.class);
        InOrder inOrder = inOrder(listener);

        when(mClientModeManager.enableTdlsWithRemoteIpAddress(TEST_IP, true))
                .thenReturn(true);
        mWifiServiceImpl.enableTdlsWithRemoteIpAddress(TEST_IP, true, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(true);

        when(mClientModeManager.enableTdlsWithRemoteIpAddress(TEST_IP, false))
                .thenReturn(false);
        mWifiServiceImpl.enableTdlsWithRemoteIpAddress(TEST_IP, false, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(false);
        verify(mLastCallerInfoManager)
                .put(eq(WifiManager.API_SET_TDLS_ENABLED),
                        anyInt(), anyInt(), anyInt(), anyString(), eq(false));
    }

    @Test
    public void testIsTdlsOperationCurrentlyAvailable() throws RemoteException {
        IBooleanListener listener = mock(IBooleanListener.class);
        InOrder inOrder = inOrder(listener);

        when(mClientModeManager.isTdlsOperationCurrentlyAvailable()).thenReturn(true);
        mWifiServiceImpl.isTdlsOperationCurrentlyAvailable(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(true);

        when(mClientModeManager.isTdlsOperationCurrentlyAvailable()).thenReturn(false);
        mWifiServiceImpl.isTdlsOperationCurrentlyAvailable(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(false);
    }

    @Test
    public void testGetMaxSupportedConcurrentTdlsSessions() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        IIntegerListener listener = mock(IIntegerListener.class);

        when(mClientModeManager.getMaxSupportedConcurrentTdlsSessions()).thenReturn(5);
        mWifiServiceImpl.getMaxSupportedConcurrentTdlsSessions(listener);
        mLooper.dispatchAll();
        verify(listener).onResult(5);
    }

    @Test
    public void testGetNumberOfEnabledTdlsSessions() throws RemoteException {
        IIntegerListener listener = mock(IIntegerListener.class);

        when(mClientModeManager.getNumberOfEnabledTdlsSessions()).thenReturn(3);
        mWifiServiceImpl.getNumberOfEnabledTdlsSessions(listener);
        mLooper.dispatchAll();
        verify(listener).onResult(3);
    }

    /**
     * Verify that a call to setOverrideCountryCode() throws a SecurityException if the caller does
     * not have the MANAGE_WIFI_COUNTRY_CODE permission.
     */
    @Test
    public void testSetOverrideCountryCodeThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(MANAGE_WIFI_COUNTRY_CODE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setOverrideCountryCode(TEST_COUNTRY_CODE);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify the call to setOverrideCountryCode() goes to WifiCountryCode
     */
    @Test
    public void testSetOverrideCountryCode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiServiceImpl.setOverrideCountryCode(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mWifiCountryCode).setOverrideCountryCode(TEST_COUNTRY_CODE);
    }

    /**
     * Verify that a call to clearOverrideCountryCode() throws a SecurityException if the caller
     * does not have the MANAGE_WIFI_COUNTRY_CODE permission.
     */
    @Test
    public void testClearOverrideCountryCodeThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(MANAGE_WIFI_COUNTRY_CODE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.clearOverrideCountryCode();
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify the call to clearOverrideCountryCode() goes to WifiCountryCode
     */
    @Test
    public void testClearOverrideCountryCode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiServiceImpl.clearOverrideCountryCode();
        mLooper.dispatchAll();
        verify(mWifiCountryCode).clearOverrideCountryCode();
    }

    /**
     * Verify that a call to setDefaultCountryCode() throws a SecurityException if the caller does
     * not have the MANAGE_WIFI_COUNTRY_CODE permission.
     */
    @Test
    public void testSetDefaultCountryCodeThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(MANAGE_WIFI_COUNTRY_CODE),
                        eq("WifiService"));
        try {
            mWifiServiceImpl.setDefaultCountryCode(TEST_COUNTRY_CODE);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    /**
     * Verify the call to setDefaultCountryCode() goes to WifiCountryCode
     */
    @Test
    public void testSetDefaultCountryCode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiServiceImpl.setDefaultCountryCode(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mWifiCountryCode).setDefaultCountryCode(TEST_COUNTRY_CODE);
    }

    /**
     * Verify that a call to flushPasspointAnqpCache throws a SecurityException if the
     * caller does not have any permission.
     */
    @Test (expected = SecurityException.class)
    public void testFlushPasspointAnqpCacheThrowsSecurityExceptionOnMissingPermissions() {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), anyString())).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), anyString())).thenReturn(false);

        mWifiServiceImpl.flushPasspointAnqpCache(mContext.getPackageName());
    }

    /**
     * Verifies that the call to testFlushPasspointAnqpCache with DO permission calls Passpoint
     * manager to flush the ANQP cache and clear all pending requests.
     */
    @Test
    public void testFlushPasspointAnqpCacheWithDoPermissions() {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), eq(TEST_PACKAGE_NAME))).thenReturn(true);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(),
                eq(TEST_PACKAGE_NAME))).thenReturn(false);
        mWifiServiceImpl.flushPasspointAnqpCache(TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mPasspointManager).clearAnqpRequestsAndFlushCache();
    }

    /**
     * Verifies that the call to testFlushPasspointAnqpCache with PO permission calls Passpoint
     * manager to flush the ANQP cache and clear all pending requests.
     */
    @Test
    public void testFlushPasspointAnqpCacheWithPoPermissions() {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), eq(TEST_PACKAGE_NAME))).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(), eq(TEST_PACKAGE_NAME))).thenReturn(true);
        mWifiServiceImpl.flushPasspointAnqpCache(TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mPasspointManager).clearAnqpRequestsAndFlushCache();
    }

    /**
     * Verifies that the call to testFlushPasspointAnqpCache calls Passpoint manager to flush the
     * ANQP cache and clear all pending requests.
     */
    @Test
    public void testFlushPasspointAnqpCache() {
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_SETTINGS))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiPermissionsUtil.isDeviceOwner(anyInt(), eq(TEST_PACKAGE_NAME))).thenReturn(false);
        when(mWifiPermissionsUtil.isProfileOwner(anyInt(),
                eq(TEST_PACKAGE_NAME))).thenReturn(false);
        mWifiServiceImpl.flushPasspointAnqpCache(TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mPasspointManager).clearAnqpRequestsAndFlushCache();
    }

    /**
     * Verify that a call to getUsableChannels() throws a SecurityException if the caller does
     * not have the LOCATION_HARDWARE permission.
     */
    @Test
    public void testGetUsableChannelsThrowsSecurityExceptionOnMissingPermissions() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                .checkCallersHardwareLocationPermission(anyInt());
        try {
            mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA, FILTER_REGULATORY,
                    TEST_PACKAGE_NAME, mExtras);
            fail("expected SecurityException");
        } catch (SecurityException expected) { }
    }

    @Test
    public void testGetUsableChannelsThrowsSecurityExceptionOnMissingPermissionsAfterU() {
        assumeTrue(SdkLevel.isAtLeastU());

        // verify app targeting prior to Android U can call API with location permission
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
                anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(true);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA, FILTER_REGULATORY,
                TEST_PACKAGE_NAME, mExtras);

        // Verify app targeting prior to Android U fails to call API without location permission
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA,
                        FILTER_REGULATORY, TEST_PACKAGE_NAME, mExtras));

        // Verify app targeting Android U no longer need location.
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
                anyInt())).thenReturn(false);
        mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA, FILTER_REGULATORY,
                TEST_PACKAGE_NAME, mExtras);

        // Verify app targeting Android U will fail without nearby permission
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                        any(), anyBoolean(), any());
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA,
                        FILTER_REGULATORY, TEST_PACKAGE_NAME, mExtras));
        mLooper.stopAutoDispatch();
    }

    /**
     * Verify the call to isValidBandForGetUsableChannels()
     */
    @Test
    public void testIsValidBandForGetUsableChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_UNSPECIFIED), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_24_GHZ), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_5_GHZ), false);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_BOTH), false);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY), false);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_24_GHZ_WITH_5GHZ_DFS), false);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_5_GHZ_WITH_DFS), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_BOTH_WITH_DFS), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_6_GHZ), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_24_5_6_GHZ), false);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_60_GHZ), true);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_24_5_6_60_GHZ), false);
        assertEquals(WifiServiceImpl.isValidBandForGetUsableChannels(
                WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ), true);
    }

    /**
     * Verify that a call to getUsableChannels() throws an IllegalArgumentException
     * if the band specified is invalid for getAllowedChannels() method.
     */
    @Test
    public void testGetUsableChannelsThrowsIllegalArgumentExceptionOnInValidBand() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(true);
        try {
            mWifiServiceImpl.getUsableChannels(WIFI_BAND_5_GHZ, OP_MODE_STA, FILTER_REGULATORY,
                    TEST_PACKAGE_NAME, mExtras);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verify the call to getUsableChannels() goes to WifiNative
     */
    @Test
    public void testGetUsableChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(true);
        mLooper.startAutoDispatch();
        mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA, FILTER_REGULATORY,
                TEST_PACKAGE_NAME, mExtras);
        mLooper.stopAutoDispatch();
        verify(mWifiNative).getUsableChannels(anyInt(), anyInt(), anyInt());
    }

    /**
     * Verify the call to getUsableChannels() goes to cached SoftAp capabilities
     */
    @Test
    public void testGetUsableChannelsUsesStoredSoftApChannelsWhenDriverIsntUp() throws Exception {
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(true);
        when(mWifiNative.isHalSupported()).thenReturn(true);
        when(mWifiNative.isHalStarted()).thenReturn(false);
        setup5GhzSupported();
        setup6GhzSupported();
        setup60GhzSupported();
        when(mWifiCountryCode.getCountryCode()).thenReturn(TEST_COUNTRY_CODE);

        // No values stored
        mLooper.startAutoDispatch();
        assertThat(mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_SAP,
                FILTER_REGULATORY, TEST_PACKAGE_NAME, mExtras)).isEmpty();
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        // Country code doesn't match
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_SOFT_AP_COUNTRY_CODE))
                .thenReturn(TEST_COUNTRY_CODE);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_AVAILABLE_SOFT_AP_FREQS_MHZ))
                .thenReturn("[2452,5180,5955,58320]");
        when(mWifiCountryCode.getCountryCode()).thenReturn(TEST_NEW_COUNTRY_CODE);
        mLooper.startAutoDispatch();
        assertThat(mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_SAP,
                FILTER_REGULATORY, TEST_PACKAGE_NAME, mExtras)).isEmpty();
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        // Matching country code
        when(mWifiCountryCode.getCountryCode()).thenReturn(TEST_COUNTRY_CODE);

        mLooper.startAutoDispatch();
        assertThat(mWifiServiceImpl.getUsableChannels(WIFI_BAND_24_5_WITH_DFS_6_60_GHZ, OP_MODE_SAP,
                FILTER_REGULATORY, TEST_PACKAGE_NAME, mExtras)).containsExactly(
                new WifiAvailableChannel(2452, WifiAvailableChannel.OP_MODE_SAP),
                new WifiAvailableChannel(5180, WifiAvailableChannel.OP_MODE_SAP),
                new WifiAvailableChannel(5955, WifiAvailableChannel.OP_MODE_SAP),
                new WifiAvailableChannel(58320, WifiAvailableChannel.OP_MODE_SAP));
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify that driver country code updates store the new available Soft AP channels.
     */
    @Test
    public void testDriverCountryCodeChangedStoresAvailableSoftApChannels() throws Exception {
        setup5GhzSupported();
        setup24GhzSupported();
        setup6GhzSupported();
        setup60GhzSupported();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(true);
        when(mWifiNative.isHalSupported()).thenReturn(true);
        when(mWifiNative.isHalStarted()).thenReturn(true);
        when(mWifiNative.getUsableChannels(eq(WIFI_BAND_24_GHZ), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(
                        new WifiAvailableChannel(2452, WifiAvailableChannel.OP_MODE_SAP)));
        when(mWifiNative.getUsableChannels(eq(WIFI_BAND_5_GHZ), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(
                        new WifiAvailableChannel(5180, WifiAvailableChannel.OP_MODE_SAP)));
        when(mWifiNative.getUsableChannels(eq(WIFI_BAND_6_GHZ), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(
                        new WifiAvailableChannel(5955, WifiAvailableChannel.OP_MODE_SAP)));
        when(mWifiNative.getUsableChannels(eq(WIFI_BAND_60_GHZ), anyInt(), anyInt()))
                .thenReturn(null);

        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();

        verify(mWifiSettingsConfigStore).put(
                eq(WifiSettingsConfigStore.WIFI_SOFT_AP_COUNTRY_CODE), eq(TEST_COUNTRY_CODE));
        verify(mWifiSettingsConfigStore).put(
                eq(WifiSettingsConfigStore.WIFI_AVAILABLE_SOFT_AP_FREQS_MHZ),
                eq("[2452,5180,5955]"));
    }

    private List<WifiConfiguration> setupMultiTypeConfigs(
            long featureFlags, boolean saeAutoUpgradeEnabled, boolean oweAutoUpgradeEnabled) {
        when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(featureFlags);
        when(mWifiGlobals.isWpa3SaeUpgradeEnabled()).thenReturn(saeAutoUpgradeEnabled);
        when(mWifiGlobals.isOweUpgradeEnabled()).thenReturn(oweAutoUpgradeEnabled);

        List<WifiConfiguration> multiTypeConfigs  = new ArrayList<>();
        multiTypeConfigs.add(WifiConfigurationTestUtil.createOpenOweNetwork());
        multiTypeConfigs.add(WifiConfigurationTestUtil.createPskSaeNetwork());
        multiTypeConfigs.add(WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork());
        return multiTypeConfigs;
    }

    private boolean isSecurityParamsSupported(SecurityParams params, long wifiFeatures) {
        switch (params.getSecurityType()) {
            case WifiConfiguration.SECURITY_TYPE_SAE:
                return 0 != (wifiFeatures & WifiManager.WIFI_FEATURE_WPA3_SAE);
            case WifiConfiguration.SECURITY_TYPE_OWE:
                return 0 != (wifiFeatures & WifiManager.WIFI_FEATURE_OWE);
        }
        return true;
    }

    private List<WifiConfiguration> generateExpectedConfigs(
            List<WifiConfiguration> testConfigs,
            boolean saeAutoUpgradeEnabled, boolean oweAutoUpgradeEnabled) {
        if (!SdkLevel.isAtLeastS()) {
            return testConfigs;
        }
        WifiConfiguration tmpConfig;
        List<WifiConfiguration> expectedConfigs = new ArrayList<>();
        tmpConfig = new WifiConfiguration(testConfigs.get(0));
        tmpConfig.setSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_OPEN));
        expectedConfigs.add(tmpConfig);
        if (oweAutoUpgradeEnabled) {
            tmpConfig = new WifiConfiguration(testConfigs.get(0));
            tmpConfig.setSecurityParams(
                    SecurityParams.createSecurityParamsBySecurityType(
                            WifiConfiguration.SECURITY_TYPE_OWE));
            expectedConfigs.add(tmpConfig);
        }
        tmpConfig = new WifiConfiguration(testConfigs.get(1));
        tmpConfig.setSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        expectedConfigs.add(tmpConfig);
        if (saeAutoUpgradeEnabled) {
            tmpConfig = new WifiConfiguration(testConfigs.get(1));
            tmpConfig.setSecurityParams(
                    SecurityParams.createSecurityParamsBySecurityType(
                            WifiConfiguration.SECURITY_TYPE_SAE));
            expectedConfigs.add(tmpConfig);
        }
        tmpConfig = new WifiConfiguration(testConfigs.get(2));
        tmpConfig.setSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_EAP));
        expectedConfigs.add(tmpConfig);
        tmpConfig = new WifiConfiguration(testConfigs.get(2));
        tmpConfig.setSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE));
        expectedConfigs.add(tmpConfig);
        return expectedConfigs;
    }

    /**
     * verify multi-type configs are converted to legacy configs in getConfiguredNetworks
     * and getPrivilegedConfiguredNetworks when auto-upgrade is enabled.
     */
    @Test
    public void testGetConfiguredNetworksForMultiTypeConfigs() {
        long featureFlags = WifiManager.WIFI_FEATURE_WPA3_SAE | WifiManager.WIFI_FEATURE_OWE;
        List<WifiConfiguration> testConfigs = setupMultiTypeConfigs(
                featureFlags, true, true);
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(testConfigs);
        when(mWifiConfigManager.getConfiguredNetworksWithPasswords())
                .thenReturn(testConfigs);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID, false);
        ParceledListSlice<WifiConfiguration> privilegedConfigs =
                mWifiServiceImpl.getPrivilegedConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID,
                        mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        List<WifiConfiguration> expectedConfigs = generateExpectedConfigs(
                testConfigs, true, true);
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedConfigs, configs.getList());
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedConfigs, privilegedConfigs.getList());
    }

    /**
     * verify multi-type configs are converted to legacy configs in getConfiguredNetworks
     * and getPrivilegedConfiguredNetworks when auto-upgrade is not enabled.
     */
    @Test
    public void testGetConfiguredNetworksForMultiTypeConfigsWithoutAutoUpgradeEnabled() {
        long featureFlags = WifiManager.WIFI_FEATURE_WPA3_SAE | WifiManager.WIFI_FEATURE_OWE;
        List<WifiConfiguration> testConfigs = setupMultiTypeConfigs(
                featureFlags, false, false);
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(testConfigs);
        when(mWifiConfigManager.getConfiguredNetworksWithPasswords())
                .thenReturn(testConfigs);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID, false);
        ParceledListSlice<WifiConfiguration> privilegedConfigs =
                mWifiServiceImpl.getPrivilegedConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID,
                        mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        List<WifiConfiguration> expectedConfigs = generateExpectedConfigs(
                testConfigs, false, false);
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedConfigs, configs.getList());
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedConfigs, privilegedConfigs.getList());
    }

    /**
     * verify multi-type configs are converted to legacy configs in getConfiguredNetworks
     * and getPrivilegedConfiguredNetworks when security types are not supported.
     */
    @Test
    public void testGetConfiguredNetworksForMultiTypeConfigsWithoutHwSupport() {
        long featureFlags = 0L;
        List<WifiConfiguration> testConfigs = setupMultiTypeConfigs(
                featureFlags, true, true);
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(testConfigs);
        when(mWifiConfigManager.getConfiguredNetworksWithPasswords())
                .thenReturn(testConfigs);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

        mLooper.startAutoDispatch();
        ParceledListSlice<WifiConfiguration> configs =
                mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID, false);
        ParceledListSlice<WifiConfiguration> privilegedConfigs =
                mWifiServiceImpl.getPrivilegedConfiguredNetworks(TEST_PACKAGE, TEST_FEATURE_ID,
                        mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        List<WifiConfiguration> expectedConfigs = generateExpectedConfigs(
                testConfigs, true, true);
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedConfigs, configs.getList());
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedConfigs, privilegedConfigs.getList());
    }

    private void trySetTargetSdkToT() {
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                    eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        }
    }

    @Test (expected = SecurityException.class)
    public void testGetPrivilegedConnectedNetworkNoPermission() {
        trySetTargetSdkToT();
        if (SdkLevel.isAtLeastT()) {
            doThrow(new SecurityException())
                    .when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                            any(), anyBoolean(), any());
        } else {
            doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                    .enforceCanAccessScanResults(any(), any(), anyInt(), any());
        }

        mWifiServiceImpl.getPrivilegedConnectedNetwork(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);
        mLooper.dispatchAll();
    }

    @Test
    public void testGetPrivilegedConnectedNetworkNotConnected() {
        trySetTargetSdkToT();
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(new WifiInfo());

        mLooper.startAutoDispatch();
        assertNull(mWifiServiceImpl.getPrivilegedConnectedNetwork(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras));
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil).enforceNearbyDevicesPermission(any(), eq(true), any());
        } else {
            verify(mWifiPermissionsUtil).enforceCanAccessScanResults(any(), any(), anyInt(), any());
        }
    }

    @Test
    public void testGetPrivilegedConnectedNetworkSuccess() {
        // mock testConfig as the currently connected network
        trySetTargetSdkToT();
        WifiConfiguration testConfig = WifiConfigurationTestUtil.createPskSaeNetwork();
        testConfig.networkId = TEST_NETWORK_ID;
        testConfig.setRandomizedMacAddress(TEST_FACTORY_MAC_ADDR);
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setNetworkId(testConfig.networkId);
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiConfigManager.getConfiguredNetworkWithPassword(testConfig.networkId)).thenReturn(
                testConfig);

        mLooper.startAutoDispatch();
        WifiConfiguration result = mWifiServiceImpl.getPrivilegedConnectedNetwork(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil).enforceNearbyDevicesPermission(any(), eq(true), any());
        } else {
            verify(mWifiPermissionsUtil).enforceCanAccessScanResults(any(), any(), anyInt(), any());
        }
        verify(mWifiConfigManager).getConfiguredNetworkWithPassword(testConfig.networkId);
        // verify the credentials match
        assertEquals(testConfig.networkId, result.networkId);
        assertEquals(testConfig.SSID, result.SSID);
        assertEquals(testConfig.preSharedKey, result.preSharedKey);
        // verify the randomized MAC address is filtered out
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, result.getRandomizedMacAddress().toString());
    }

    /**
     * Verify that a call to isWifiPasspointEnabled throws a SecurityException if the
     * caller does not have the ACCESS_WIFI_STATE permission.
     */
    @Test (expected = SecurityException.class)
    public void testIsWifiPasspointEnabledWithoutPermissions() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE),
                        eq("WifiService"));

        mWifiServiceImpl.isWifiPasspointEnabled();
    }

    /**
     * Verify that the call to setWifiPasspointEnabled is not redirected to
     * specific API when the caller doesn't have
     * NETWORK_SETTINGS permissions and NETWORK_SETUP_WIZARD.
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiPasspointEnabledWithoutPermissions() {
        mLooper.startAutoDispatch();
        mWifiServiceImpl.setWifiPasspointEnabled(false);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify that the call to setWifiPasspointEnabled is redirected to
     * specific API when the caller have
     * NETWORK_SETTINGS permissions and NETWORK_SETUP_WIZARD.
     */
    @Test
    public void testSetWifiPasspointEnabledWithPermissions() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        mWifiServiceImpl.setWifiPasspointEnabled(false);
        mLooper.dispatchAll();
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        verify(mPasspointManager).setWifiPasspointEnabled(false);
    }

    @Test(expected = SecurityException.class)
    public void testSetScreenOnScanSchedule_NoPermission() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        mWifiServiceImpl.setScreenOnScanSchedule(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetScreenOnScanSchedule_BadInput1() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        // test that both arrays need to be null or non-null. Never one null one non-null.
        mWifiServiceImpl.setScreenOnScanSchedule(null, new int[] {1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetScreenOnScanSchedule_BadInput2() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        // test that the input should not be empty arrays.
        mWifiServiceImpl.setScreenOnScanSchedule(new int[0], new int[0]);
    }

    @Test
    public void testSetScreenOnScanSchedule_Success() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        int[] expectedSchedule = new int[] {20, 40, 80};
        int[] expectedType = new int[] {2, 2, 1};
        mWifiServiceImpl.setScreenOnScanSchedule(expectedSchedule, expectedType);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setExternalScreenOnScanSchedule(
                expectedSchedule, expectedType);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SET_SCAN_SCHEDULE), anyInt(),
                anyInt(), anyInt(), any(), eq(true));

        mWifiServiceImpl.setScreenOnScanSchedule(null, null);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setExternalScreenOnScanSchedule(null, null);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SET_SCAN_SCHEDULE), anyInt(),
                anyInt(), anyInt(), any(), eq(false));
    }

    @Test
    public void testSetOneShotScreenOnConnectivityScanDelayMillis() {
        assumeTrue(SdkLevel.isAtLeastT());
        int delayMs = 1234;

        // verify permission checks
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt()))
                .thenReturn(false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setOneShotScreenOnConnectivityScanDelayMillis(delayMs));

        // verify correct input
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setOneShotScreenOnConnectivityScanDelayMillis(-1));

        // verify correct call
        mWifiServiceImpl.setOneShotScreenOnConnectivityScanDelayMillis(delayMs);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setOneShotScreenOnConnectivityScanDelayMillis(delayMs);
    }

    @Test(expected = SecurityException.class)
    public void testSetExternalPnoScanRequest_NoPermission() {
        assumeTrue(SdkLevel.isAtLeastT());
        IPnoScanResultsCallback callback = mock(IPnoScanResultsCallback.class);
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromString("\"TEST_SSID_1\""));
        int[] frequencies = new int[] {TEST_AP_FREQUENCY};

        mWifiServiceImpl.setExternalPnoScanRequest(mAppBinder, callback, ssids, frequencies,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID);
    }

    @Test
    public void testSetExternalPnoScanRequest_Success() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiGlobals.isBackgroundScanSupported()).thenReturn(true);
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_PNO);
        when(mWifiPermissionsUtil.checkRequestCompanionProfileAutomotiveProjectionPermission(
                anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersLocationPermissionInManifest(
                anyInt(), anyBoolean())).thenReturn(true);
        IPnoScanResultsCallback callback = mock(IPnoScanResultsCallback.class);
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromString("\"TEST_SSID_1\""));
        int[] frequencies = new int[] {TEST_AP_FREQUENCY};

        mWifiServiceImpl.setExternalPnoScanRequest(mAppBinder, callback, ssids, frequencies,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).setExternalPnoScanRequest(anyInt(), any(), any(),
                eq(callback), eq(ssids), eq(frequencies));
    }

    @Test
    public void testSetExternalPnoScanRequest_PnoNotSupported() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(0L);
        when(mWifiPermissionsUtil.checkRequestCompanionProfileAutomotiveProjectionPermission(
                anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkCallersLocationPermissionInManifest(
                anyInt(), anyBoolean())).thenReturn(true);
        IPnoScanResultsCallback callback = mock(IPnoScanResultsCallback.class);
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromString("\"TEST_SSID_1\""));
        int[] frequencies = new int[] {TEST_AP_FREQUENCY};

        mWifiServiceImpl.setExternalPnoScanRequest(mAppBinder, callback, ssids, frequencies,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(callback).onRegisterFailed(WifiManager.PnoScanResultsCallback
                .REGISTER_PNO_CALLBACK_PNO_NOT_SUPPORTED);
    }

    @Test
    public void testClearExternalPnoScanRequest() {
        assumeTrue(SdkLevel.isAtLeastT());

        mWifiServiceImpl.clearExternalPnoScanRequest();
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).clearExternalPnoScanRequest(anyInt());
    }

    @Test
    public void testGetLastCallerInfoForApi_Exceptions() {
        // good inputs should result in no exceptions.
        ILastCallerListener listener = mock(ILastCallerListener.class);
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getLastCallerInfoForApi(
                        WifiManager.API_WIFI_ENABLED, null));

        // invalid ApiType ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getLastCallerInfoForApi(WifiManager.API_MAX + 1, listener));

        // No permission ==> SecurityException
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getLastCallerInfoForApi(
                        WifiManager.API_WIFI_ENABLED, listener));
    }

    @Test
    public void testGetLastCallerInfoForApi_GoodCase() throws RemoteException {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        ILastCallerListener listener = mock(ILastCallerListener.class);
        LastCallerInfoManager.LastCallerInfo expected =
                new LastCallerInfoManager.LastCallerInfo(0, 1, 2, TEST_PACKAGE_NAME, true);
        when(mLastCallerInfoManager.get(WifiManager.API_WIFI_ENABLED)).thenReturn(expected);

        InOrder inOrder = inOrder(listener);
        mWifiServiceImpl.getLastCallerInfoForApi(WifiManager.API_WIFI_ENABLED, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(TEST_PACKAGE_NAME, true);

        // Verify null is returned as packageName if there's no information about this ApiType.
        mWifiServiceImpl.getLastCallerInfoForApi(WifiManager.API_SOFT_AP, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(null, false);
    }

    /**
     * Verify that a call to registerDriverCountryCodeChangedListener throws a SecurityException
     * if the caller doesnot have ACCESS_COARSE_LOCATION permission.
     */
    @Test(expected = SecurityException.class)
    public void registerDriverCountryCodeChangedListenerThrowsSecurityExceptionWithoutPermission() {
        assumeTrue(SdkLevel.isAtLeastT());
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceLocationPermissionInManifest(anyInt(), eq(true));
        mWifiServiceImpl.registerDriverCountryCodeChangedListener(
                mIOnWifiDriverCountryCodeChangedListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
    }

    /**
     * Verify that a call registerDriverCountryCodeChangedListener throws an
     * IllegalArgumentException if the parameters are not provided.
     */
    @Test
    public void registerDriverCountryCodeChangedListenerThrowsIllegalArgumentException() {
        assumeTrue(SdkLevel.isAtLeastT());
        try {
            mWifiServiceImpl.registerDriverCountryCodeChangedListener(
                    null, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Verifies that we handle driver country code changed listener registration failure if we
     * encounter an exception while linking to death.
     */
    @Test
    public void registerDriverCountryCodeChangedListenerFailureOnLinkToDeath() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doThrow(new RemoteException())
                .when(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        mWifiServiceImpl.registerDriverCountryCodeChangedListener(
                mIOnWifiDriverCountryCodeChangedListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(mIOnWifiDriverCountryCodeChangedListener, never())
                .onDriverCountryCodeChanged(anyString());
    }

    /**
     * Verify that a call to registerDriverCountryCodeChangedListener succeeded.
     */
    private void verifyRegisterDriverCountryCodeChangedListenerSucceededAndTriggerListener(
            IOnWifiDriverCountryCodeChangedListener listener)
            throws Exception {
        doNothing()
                .when(mWifiPermissionsUtil).enforceLocationPermissionInManifest(anyInt(),
                                                                      eq(true));
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(eq(TEST_PACKAGE_NAME),
                eq(TEST_FEATURE_ID), anyInt(), any())).thenReturn(true);
        when(mWifiCountryCode.getCurrentDriverCountryCode()).thenReturn(TEST_COUNTRY_CODE);
        mWifiServiceImpl.registerDriverCountryCodeChangedListener(
                listener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(listener)
                .onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
    }

    /**
     * Verify that a call to registerDriverCountryCodeChangedListener succeeded but we don't nofiy
     * listener because permission gets denied in OpNote.
     */
    @Test
    public void verifyRegisterDriverCountryCodeChangedListenerSucceededButNoNotifyListener()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doNothing()
                .when(mWifiPermissionsUtil).enforceLocationPermissionInManifest(anyInt(),
                                                                      eq(true));
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(eq(TEST_PACKAGE_NAME),
                eq(TEST_FEATURE_ID), anyInt(), any())).thenReturn(false);
        when(mWifiCountryCode.getCurrentDriverCountryCode()).thenReturn(TEST_COUNTRY_CODE);
        mWifiServiceImpl.registerDriverCountryCodeChangedListener(
                  mIOnWifiDriverCountryCodeChangedListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(mIOnWifiDriverCountryCodeChangedListener, never())
                .onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
    }

    /**
     * Verify that DriverCountryCodeChanged will be dropped if register permission was removed.
     */
    @Test
    public void testDriverCountryCodeChangedDropWhenRegisterPermissionRemoved() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doNothing()
                .when(mWifiPermissionsUtil).enforceLocationPermissionInManifest(anyInt(),
                                                                      eq(true));
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), anyInt(), any())).thenReturn(true);
        verifyRegisterDriverCountryCodeChangedListenerSucceededAndTriggerListener(
                mIOnWifiDriverCountryCodeChangedListener);
        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(TEST_NEW_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mIOnWifiDriverCountryCodeChangedListener)
                .onDriverCountryCodeChanged(TEST_NEW_COUNTRY_CODE);
        // remove permission
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), anyInt(), any())).thenReturn(false);
        reset(mIOnWifiDriverCountryCodeChangedListener);
        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mIOnWifiDriverCountryCodeChangedListener, never())
                .onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
    }

    /**
     * Verify that unregisterDriverCountryCodeChangedListener removes listener from registered
     * listener list
     */
    @Test
    public void unregisterDriverCountryCodeChangedListenerRemovesListener() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        doNothing()
                .when(mWifiPermissionsUtil).enforceLocationPermissionInManifest(anyInt(),
                                                                      eq(true));
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), anyInt(), any())).thenReturn(true);
        verifyRegisterDriverCountryCodeChangedListenerSucceededAndTriggerListener(
                mIOnWifiDriverCountryCodeChangedListener);
        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(TEST_NEW_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mIOnWifiDriverCountryCodeChangedListener)
                .onDriverCountryCodeChanged(TEST_NEW_COUNTRY_CODE);
        mWifiServiceImpl.unregisterDriverCountryCodeChangedListener(
                mIOnWifiDriverCountryCodeChangedListener);
        mLooper.dispatchAll();
        reset(mIOnWifiDriverCountryCodeChangedListener);
        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        verify(mIOnWifiDriverCountryCodeChangedListener, never())
                .onDriverCountryCodeChanged(anyString());
    }


    /**
     * Verify that onFailed is called when enabling Lohs with non-supported configuration.
     */
    @Test
    public void testFailureCallbacksTriggeredWhenSoftApFailsBecauseNonSupportedConfiguration()
            throws Exception {
        when(mResources.getBoolean(R.bool.config_wifiSoftap6ghzSupported)).thenReturn(false);
        setupForCustomLohs();
        SoftApConfiguration lohsConfig = createValidSoftApConfiguration();
        SoftApConfiguration customizedConfig = new SoftApConfiguration.Builder(lohsConfig)
                .setBand(SoftApConfiguration.BAND_6GHZ)
                .setPassphrase("thisIsABadPassword", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .build();
        when(mWifiApConfigStore.generateLocalOnlyHotspotConfig(
                any(), any(), any())).thenReturn(customizedConfig);
        // Expect the result is registered but it should get failure because non-supported
        // configuration
        int result = mWifiServiceImpl.startLocalOnlyHotspot(mLohsCallback, TEST_PACKAGE_NAME,
                TEST_FEATURE_ID, customizedConfig, mExtras);
        assertEquals(LocalOnlyHotspotCallback.REQUEST_REGISTERED, result);
        mLooper.dispatchAll();
        verify(mLohsCallback).onHotspotFailed(ERROR_GENERIC);
    }
    /**
     * Registers a soft AP callback, then verifies that the current soft AP state and num clients
     * are sent to caller immediately after callback is registered.
     */
    private void registerLohsSoftApCallbackAndVerify(ISoftApCallback callback, Bundle bundle)
            throws Exception {
        mWifiServiceImpl.registerLocalOnlyHotspotSoftApCallback(mClientSoftApCallback, bundle);
        mLooper.dispatchAll();

        ArgumentCaptor<SoftApState> softApStateCaptor =
                ArgumentCaptor.forClass(SoftApState.class);
        verify(mClientSoftApCallback).onStateChanged(softApStateCaptor.capture());
        assertThat(softApStateCaptor.getValue().getState()).isEqualTo(WIFI_AP_STATE_DISABLED);
        assertThat(softApStateCaptor.getValue().getFailureReason()).isEqualTo(0);
        verify(mClientSoftApCallback).onConnectedClientsOrInfoChanged(
                new HashMap<String, SoftApInfo>(),
                new HashMap<String, List<WifiClient>>(), false, true);
        verify(mClientSoftApCallback).onCapabilityChanged(
                ApConfigUtil.updateCapabilityFromResource(mContext));
        // Don't need to invoke callback when register.
        verify(mClientSoftApCallback, never()).onBlockedClientConnecting(any(), anyInt());
    }

    /**
     * Verify that unregisterLocalOnlyHotspotSoftApCallback removes callback from registered
     * callbacks list.
     */
    @Test
    public void unregisterLohsSoftApCallbackRemovesCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        registerLohsSoftApCallbackAndVerify(mClientSoftApCallback, mExtras);

        mWifiServiceImpl.unregisterLocalOnlyHotspotSoftApCallback(mClientSoftApCallback, mExtras);
        mLooper.dispatchAll();

        reset(mClientSoftApCallback);
        mLohsApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onConnectedClientsOrInfoChanged(
                any(), any(), anyBoolean(), anyBoolean());
    }

    /**
     * Verify that unregisterLocalOnlyHotspotSoftApCallback is no-op if callback not registered.
     */
    @Test
    public void unregisterLohsSoftApCallbackDoesNotRemoveCallbackIfCallbackNotMatching()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        registerLohsSoftApCallbackAndVerify(mClientSoftApCallback, mExtras);

        mWifiServiceImpl.unregisterLocalOnlyHotspotSoftApCallback(mAnotherSoftApCallback, mExtras);
        mLooper.dispatchAll();
        mLohsApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback).onConnectedClientsOrInfoChanged(
                eq(mTestSoftApInfos), eq(mTestSoftApClients), eq(false), eq(false));
    }

    /**
     * Registers two lohs callbacks, remove one then verify the right callback is being called
     * on events.
     */
    @Test
    public void correctLohsCallbackIsCalledAfterAddingTwoCallbacksAndRemovingOne()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiClient testWifiClient = new WifiClient(MacAddress.fromString("22:33:44:55:66:77"),
                WIFI_IFACE_NAME2);
        registerLohsSoftApCallbackAndVerify(mClientSoftApCallback, mExtras);
        mLooper.dispatchAll();

        reset(mClientSoftApCallback);
        when(mClientSoftApCallback.asBinder()).thenReturn(mAppBinder);
        // Change state from default before registering the second callback
        SoftApState state = new SoftApState(WIFI_AP_STATE_ENABLED, 0,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME);
        mLohsApCallback.onStateChanged(state);
        mLohsApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLohsApCallback.onBlockedClientConnecting(testWifiClient, 0);


        // Register another callback and verify the new state is returned in the immediate callback
        mWifiServiceImpl.registerLocalOnlyHotspotSoftApCallback(mAnotherSoftApCallback, mExtras);
        mLooper.dispatchAll();
        verify(mAnotherSoftApCallback).onStateChanged(eq(state));
        verify(mAnotherSoftApCallback).onConnectedClientsOrInfoChanged(
                eq(mTestSoftApInfos), eq(mTestSoftApClients), eq(false), eq(true));
        // Verify only first callback will receive onBlockedClientConnecting since it call after
        // first callback register but before another callback register.
        verify(mClientSoftApCallback).onBlockedClientConnecting(testWifiClient, 0);
        verify(mAnotherSoftApCallback, never()).onBlockedClientConnecting(testWifiClient, 0);

        // unregister the fisrt callback
        mWifiServiceImpl.unregisterLocalOnlyHotspotSoftApCallback(mClientSoftApCallback, mExtras);
        mLooper.dispatchAll();

        // Update soft AP state and verify the remaining callback receives the event
        state = new SoftApState(
                WIFI_AP_STATE_FAILED, SAP_START_FAILURE_NO_CHANNEL,
                TEST_TETHERING_REQUEST, TEST_IFACE_NAME);
        mLohsApCallback.onStateChanged(state);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onStateChanged(eq(state));
        verify(mAnotherSoftApCallback).onStateChanged(eq(state));
    }

    /**
     * Verify that wifi service registers for lohs callers BinderDeath event
     */
    @Test
    public void registersForBinderDeathOnRegisterLohsSoftApCallback() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        registerLohsSoftApCallbackAndVerify(mClientSoftApCallback, mExtras);
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    /**
     * Verify that we un-register the lohs soft AP callback on receiving BinderDied event.
     */
    @Test
    public void unregistersLohsSoftApCallbackOnBinderDied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        ArgumentCaptor<IBinder.DeathRecipient> drCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        registerLohsSoftApCallbackAndVerify(mClientSoftApCallback, mExtras);
        verify(mAppBinder).linkToDeath(drCaptor.capture(), anyInt());

        drCaptor.getValue().binderDied();
        mLooper.dispatchAll();
        reset(mClientSoftApCallback);
        // Verify callback is removed from the list as well
        Map<String, List<WifiClient>> mTestSoftApClients = mock(Map.class);
        Map<String, SoftApInfo> mTestSoftApInfos = mock(Map.class);
        mLohsApCallback.onConnectedClientsOrInfoChanged(
                mTestSoftApInfos, mTestSoftApClients, false);
        mLooper.dispatchAll();
        verify(mClientSoftApCallback, never()).onConnectedClientsOrInfoChanged(
                any(), any(), anyBoolean(), anyBoolean());
    }

    /**
     * Verify that a call to registerLocalOnlyHotspotSoftApCallback throws a SecurityException
     * if the caller target Android T or later and does not have nearby devices permission.
     */
    @Test(expected = SecurityException.class)
    public void testRegisterLocalOnlyHotspotSoftApCallbackThrowsExceptionWithoutPermissionOnT() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                        any(), anyBoolean(), any());
        mWifiServiceImpl.registerLocalOnlyHotspotSoftApCallback(mClientSoftApCallback, mExtras);
    }

    /**
     * Verify that a call to unregisterLocalOnlyHotspotSoftApCallback throws a SecurityException
     * if the caller targets Android T or later and does not have nearby devices permission.
     */
    @Test(expected = SecurityException.class)
    public void testUnregisterLocalOnlyHotspotSoftApCallbackThrowsExceptionWithoutPermissionOnT() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                        any(), anyBoolean(), any());
        mWifiServiceImpl.unregisterLocalOnlyHotspotSoftApCallback(mClientSoftApCallback, mExtras);
    }

    /**
     * Verify getStaConcurrencyForMultiInternetMode
     */
    @Test
    public void testGetStaConcurrencyForMultiInternetMode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mMultiInternetManager.getStaConcurrencyForMultiInternetMode()).thenReturn(
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP);
        mLooper.startAutoDispatch();
        final int mode = mWifiServiceImpl.getStaConcurrencyForMultiInternetMode();
        verify(mMultiInternetManager).getStaConcurrencyForMultiInternetMode();
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP, mode);
    }

    /**
     * Verify that a call to setStaConcurrencyForMultiInternetMode throws a SecurityException
     * if the caller target Android T or later and does not have network settings permission.
     */
    @Test(expected = SecurityException.class)
    public void testSetStaConcurrencyForMultiInternetModeThrowsExceptionWithoutPermissionOnT() {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                                                eq("WifiService"));
        mLooper.startAutoDispatch();
        mWifiServiceImpl.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP);
    }

    /**
     * Verify setStaConcurrencyForMultiInternetMode
     */
    @Test
    public void testSetStaConcurrencyForMultiInternetMode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(anyInt()))
                .thenReturn(true);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETUP_WIZARD),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mLooper.startAutoDispatch();
        assertTrue(mWifiServiceImpl.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        verify(mMultiInternetManager).setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
    }

    /**
     * Verify attribution is passed in correctly by WifiManager#addOrUpdateNetwork.
     */
    @Test
    public void testAddOrUpdateNetworkAttribution_InvalidAttributions() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(Process.SYSTEM_UID);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);
        assertThrows(SecurityException.class, () -> {
            mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, null);
        });

        assertThrows(SecurityException.class, () -> {
            mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, new Bundle());
        });

        assertThrows(SecurityException.class, () -> {
            Bundle nullEntry = new Bundle();
            nullEntry.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, null);
            mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, nullEntry);
        });

        assertThrows(SecurityException.class, () -> {
            Bundle incorrectEntry = new Bundle();
            incorrectEntry.putInt(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, 10);
            mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, incorrectEntry);
        });

        when(attributionSource.checkCallingUid()).thenReturn(false);
        assertThrows(SecurityException.class, () -> {
            mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution);
        });
        when(attributionSource.checkCallingUid()).thenReturn(true); // restore

        // single first attributions should not fail - even if (theoretically, doesn't happen in
        // practice) are not trusted. I.e. this call checks that this method isn't called.
        AttributionSource freshAs = mock(AttributionSource.class);
        Bundle freshAttribution = new Bundle();
        freshAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, freshAs);
        when(freshAs.checkCallingUid()).thenReturn(true);
        when(freshAs.isTrusted(any(Context.class))).thenReturn(false);
        mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, freshAttribution);
        verify(freshAs, never()).isTrusted(any());

        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(false);
        when(attributionSource.getNext()).thenReturn(originalCaller);
        assertThrows(SecurityException.class, () -> {
            mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution);
        });
    }

    /**
     * Verify attribution passed to WifiManager#addOrUpdateNetwork is parsed when there's no chain.
     */
    @Test
    public void testAddOrUpdateNetworkAttribution_CorrectParsingNoChainSystemServer() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(Process.SYSTEM_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        when(attributionSource.getUid()).thenReturn(Process.SYSTEM_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);
        config.networkId = 1;

        mLooper.startAutoDispatch();
        mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution);
        mLooper.stopAutoDispatch();
        verify(mWifiConfigManager).addOrUpdateNetwork(config, Process.SYSTEM_UID,
                TEST_PACKAGE_NAME, false);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_UPDATE_NETWORK), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify attribution passed to WifiManager#addOrUpdateNetwork is parsed when there's no chain.
     */
    @Test
    public void testAddOrUpdateNetworkAttribution_CorrectParsingNoChainNonSystemServer() {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(TEST_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, null);
        mLooper.stopAutoDispatch();
        verify(mWifiConfigManager).addOrUpdateNetwork(config, TEST_UID, TEST_PACKAGE_NAME, false);
    }

    /**
     * Verify attribution passed to WifiManager#addOrUpdateNetwork is parsed when there's a chain.
     */
    @Test
    public void testAddOrUpdateNetworkAttribution_CorrectParsingWithChain() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(Process.SYSTEM_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));

        when(attributionSource.getUid()).thenReturn(TEST_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(true);
        when(attributionSource.getNext()).thenReturn(originalCaller);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution);
        mLooper.stopAutoDispatch();
        verify(mWifiConfigManager).addOrUpdateNetwork(config, OTHER_TEST_UID,
                TEST_PACKAGE_NAME_OTHER, false);
    }

    /**
     * Verify attribution passed to WifiManager#addOrUpdateNetwork is parsed when there's a chain,
     * When calling from {@link WifiManager#updateNetwork(WifiConfiguration)}, the creator will be
     * overridden by original caller.
     */
    @Test
    public void testAddOrUpdateNetworkAttribution_CorrectParsingWithChain_WithUpdateNetwork() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(Process.SYSTEM_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(true)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));

        when(attributionSource.getUid()).thenReturn(TEST_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(true);
        when(attributionSource.getNext()).thenReturn(originalCaller);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);
        config.networkId = TEST_NETWORK_ID;

        mLooper.startAutoDispatch();
        mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution);
        mLooper.stopAutoDispatch();
        verify(mWifiConfigManager).addOrUpdateNetwork(config, OTHER_TEST_UID,
                TEST_PACKAGE_NAME_OTHER, true);
    }

    /**
     * Verify attribution passed to WifiManager#addOrUpdateNetwork is parsed when there's a chain.
     * However, if the call is not made from the system server then the attribution is ignored and
     * the attribution is simply to the calling app as before.
     */
    @Test
    public void testAddOrUpdateNetworkAttribution_CorrectParsingWithChainButNotFromSystemServer() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(TEST_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt(), anyString(), eq(false)))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));

        when(attributionSource.getUid()).thenReturn(TEST_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(true);
        when(attributionSource.getNext()).thenReturn(originalCaller);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);

        mLooper.startAutoDispatch();
        mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME, mAttribution);
        mLooper.stopAutoDispatch();
        verify(mWifiConfigManager).addOrUpdateNetwork(config, TEST_UID,
                TEST_PACKAGE_NAME, false);
    }

    /**
     * Verify attribution passed to WifiManager#connect is parsed when there's a chain.
     */
    @Test
    public void testConnectAttribution_CorrectParsingWithChain() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(Process.SYSTEM_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));

        when(attributionSource.getUid()).thenReturn(TEST_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource.getAttributionTag()).thenReturn(TEST_FEATURE_ID);
        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(originalCaller.getAttributionTag()).thenReturn(TEST_FEATURE_ID_OTHER);
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(true);
        when(attributionSource.getNext()).thenReturn(originalCaller);

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        mWifiServiceImpl.connect(config, TEST_NETWORK_ID, mock(IActionListener.class),
                TEST_PACKAGE_NAME, mAttribution);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).addOrUpdateNetwork(eq(config), anyInt());
        verify(mConnectHelper).connectToNetwork(any(NetworkUpdateResult.class),
                any(ActionListenerWrapper.class), eq(OTHER_TEST_UID), eq(TEST_PACKAGE_NAME_OTHER),
                eq(TEST_FEATURE_ID_OTHER));
    }

    /**
     * Verify attribution passed to WifiManager#connect is parsed when there's a chain.
     * However, if the call is not made from the system server then the attribution is ignored and
     * the attribution is simply to the calling app as before.
     */
    @Test
    public void testConnectAttribution_CorrectParsingWithChainButNotFromSystemServer() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiServiceImpl = spy(mWifiServiceImpl);
        lenient().when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(TEST_UID);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));

        when(attributionSource.getUid()).thenReturn(TEST_UID);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource.getAttributionTag()).thenReturn(TEST_FEATURE_ID);
        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(OTHER_TEST_UID);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME_OTHER);
        when(originalCaller.getAttributionTag()).thenReturn(TEST_FEATURE_ID_OTHER);
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(true);
        when(attributionSource.getNext()).thenReturn(originalCaller);

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);
        mWifiServiceImpl.connect(config, TEST_NETWORK_ID, mock(IActionListener.class),
                TEST_PACKAGE_NAME, mAttribution);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).addOrUpdateNetwork(eq(config), anyInt());
        verify(mConnectHelper).connectToNetwork(any(NetworkUpdateResult.class),
                any(ActionListenerWrapper.class), eq(TEST_UID), eq(TEST_PACKAGE_NAME),
                eq(TEST_FEATURE_ID));
    }

    /**
     * Test that notifyWifiSsidPolicyChanged disconnects the current network
     * due to SSID allowlist restriction
     */
    @Test
    public void testNotifyWifiSsidPolicyChangedWithAllowlistRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setSSID(WifiSsid.fromUtf8Text(TEST_SSID));
        wifiInfo.setBSSID(TEST_BSSID);
        wifiInfo.setNetworkId(TEST_NETWORK_ID);
        wifiInfo.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PSK);

        when(mContext.checkPermission(eq(android.Manifest.permission.MANAGE_DEVICE_ADMINS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(
                Collections.singletonList(mClientModeManager));
        when(mClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);

        mWifiServiceImpl.notifyWifiSsidPolicyChanged(WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST,
                Arrays.asList(WifiSsid.fromUtf8Text("SSID")));
        mLooper.dispatchAll();

        verify(mClientModeManager).disconnect();
    }

    /**
     * Test that notifyWifiSsidPolicyChanged disconnects the current network
     * due to SSID denylist restriction
     */
    @Test
    public void testNotifyWifiSsidPolicyChangedWithDenylistRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setSSID(WifiSsid.fromUtf8Text(TEST_SSID));
        wifiInfo.setBSSID(TEST_BSSID);
        wifiInfo.setNetworkId(TEST_NETWORK_ID);
        wifiInfo.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PSK);

        when(mContext.checkPermission(eq(android.Manifest.permission.MANAGE_DEVICE_ADMINS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(
                Collections.singletonList(mClientModeManager));
        when(mClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);

        mWifiServiceImpl.notifyWifiSsidPolicyChanged(WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST,
                Arrays.asList(WifiSsid.fromUtf8Text(TEST_SSID)));
        mLooper.dispatchAll();

        verify(mClientModeManager).disconnect();
    }

    /**
     * Test that notifyWifiSsidPolicyChanged does not disconnect the current network
     * due to SSID restriction for Passpoint networks
     */
    @Test
    public void testNotifyWifiSsidPolicyChangedWithSsidRestrictionForPasspoint()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiInfo wifiInfo = setupForGetConnectionInfo();
        wifiInfo.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2);

        when(mContext.checkPermission(eq(android.Manifest.permission.MANAGE_DEVICE_ADMINS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(
                Collections.singletonList(mClientModeManager));
        when(mClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);

        mWifiServiceImpl.notifyWifiSsidPolicyChanged(WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST,
                Arrays.asList(WifiSsid.fromUtf8Text("SSID")));
        mLooper.dispatchAll();

        verify(mClientModeManager, never()).disconnect();
    }

    /**
     * Test that notifyWifiSsidPolicyChanged does not disconnect the current network
     * due to SSID restriction for Osu networks
     */
    @Test
    public void testNotifyWifiSsidPolicyChangedWithSsidRestrictionForOsu()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setSSID(WifiSsid.fromUtf8Text(TEST_SSID));
        wifiInfo.setBSSID(TEST_BSSID);
        wifiInfo.setNetworkId(TEST_NETWORK_ID);
        wifiInfo.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3);
        wifiInfo.setOsuAp(true);

        when(mContext.checkPermission(eq(android.Manifest.permission.MANAGE_DEVICE_ADMINS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(
                Collections.singletonList(mClientModeManager));
        when(mClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);

        mWifiServiceImpl.notifyWifiSsidPolicyChanged(WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST,
                Arrays.asList(WifiSsid.fromUtf8Text(TEST_SSID)));
        mLooper.dispatchAll();

        verify(mClientModeManager, never()).disconnect();
    }

    /**
     * Test that notifyMinimumRequiredWifiSecurityLevelChanged disconnects the current network
     * due to minimum security level restriction
     */
    @Test
    public void testNotifyMinimumRequiredWifiSecurityLevelChangedWithSecurityLevelRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiInfo wifiInfo = setupForGetConnectionInfo();
        wifiInfo.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PSK);

        when(mContext.checkPermission(eq(android.Manifest.permission.MANAGE_DEVICE_ADMINS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(
                Collections.singletonList(mClientModeManager));
        when(mClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);

        mWifiServiceImpl.notifyMinimumRequiredWifiSecurityLevelChanged(
                DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP);
        mLooper.dispatchAll();

        verify(mClientModeManager).disconnect();
    }

    @Test
    public void testIsItPossibleToCreateInterfaceInvalidConditions() {
        assumeTrue(SdkLevel.isAtLeastT());

        when(mWifiPermissionsUtil.checkManageWifiInterfacesPermission(anyInt())).thenReturn(true);
        IInterfaceCreationInfoCallback.Stub mockCallback = mock(
                IInterfaceCreationInfoCallback.Stub.class);

        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.reportCreateInterfaceImpact(null,
                        WifiManager.WIFI_INTERFACE_TYPE_AP, true, mockCallback));
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME,
                        WifiManager.WIFI_INTERFACE_TYPE_AP, true, null));
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME,
                        /* clearly invalid value */ 100, true, mockCallback));

        mWifiServiceImpl = spy(mWifiServiceImpl);
        when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(TEST_UID);
        doThrow(new SecurityException()).when(mWifiPermissionsUtil).checkPackage(TEST_UID,
                TEST_PACKAGE_NAME);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME,
                        WifiManager.WIFI_INTERFACE_TYPE_AP, false, mockCallback));

        when(mWifiPermissionsUtil.checkManageWifiInterfacesPermission(anyInt())).thenReturn(false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME,
                        WifiManager.WIFI_INTERFACE_TYPE_AP, false, mockCallback));

        when(mWifiPermissionsUtil.checkManageWifiInterfacesPermission(anyInt())).thenReturn(true);
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE),
                        eq("WifiService"));
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME,
                        WifiManager.WIFI_INTERFACE_TYPE_AP, false, mockCallback));
    }

    @Test
    public void testIsItPossibleToCreateInterface() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        IInterfaceCreationInfoCallback.Stub mockCallback = mock(
                IInterfaceCreationInfoCallback.Stub.class);
        final int interfaceToCreate = WifiManager.WIFI_INTERFACE_TYPE_AWARE;
        final int interfaceToCreateInternal = HalDeviceManager.HDM_CREATE_IFACE_NAN;
        mWifiServiceImpl = spy(mWifiServiceImpl);
        when(mWifiServiceImpl.getMockableCallingUid()).thenReturn(TEST_UID);
        when(mWifiPermissionsUtil.checkManageWifiInterfacesPermission(TEST_UID)).thenReturn(true);
        final WorkSource ws = new WorkSource(TEST_UID, TEST_PACKAGE_NAME);
        final WorkSource wsOther = new WorkSource(OTHER_TEST_UID, TEST_PACKAGE_NAME_OTHER);
        final String[] packagesOther = {TEST_PACKAGE_NAME_OTHER};

        ArgumentCaptor<Boolean> boolCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<int[]> intArrayCaptor = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<String[]> stringArrayCaptor = ArgumentCaptor.forClass(String[].class);

        // 3 results: failure, success with no side effects, success with side effects
        when(mHalDeviceManager.reportImpactToCreateIface(interfaceToCreateInternal, true, ws))
                .thenReturn(null)
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_P2P, wsOther)))
                .thenReturn(List.of(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN,
                        new WorkSource(WIFI_UID))));
        mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME, interfaceToCreate, true,
                mockCallback);
        mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME, interfaceToCreate, true,
                mockCallback);
        mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME, interfaceToCreate, true,
                mockCallback);
        mWifiServiceImpl.reportCreateInterfaceImpact(TEST_PACKAGE_NAME, interfaceToCreate, true,
                mockCallback);
        mLooper.dispatchAll();
        verify(mHalDeviceManager, times(4)).reportImpactToCreateIface(
                interfaceToCreateInternal, true, ws);
        verify(mockCallback, times(4)).onResults(boolCaptor.capture(), intArrayCaptor.capture(),
                stringArrayCaptor.capture());
        verify(mPackageManager).makeUidVisible(TEST_UID, OTHER_TEST_UID);

        // result 0: failure
        assertFalse(boolCaptor.getAllValues().get(0));
        assertNull(intArrayCaptor.getAllValues().get(0));
        assertNull(stringArrayCaptor.getAllValues().get(0));

        // result 1: success with no side effects
        assertTrue(boolCaptor.getAllValues().get(1));
        assertEquals(0, intArrayCaptor.getAllValues().get(1).length);
        assertEquals(0, stringArrayCaptor.getAllValues().get(1).length);

        // result 2: success with side effects
        assertTrue(boolCaptor.getAllValues().get(2));
        assertEquals(1, intArrayCaptor.getAllValues().get(2).length);
        assertEquals(WifiManager.WIFI_INTERFACE_TYPE_DIRECT,
                intArrayCaptor.getAllValues().get(2)[0]);
        assertArrayEquals(packagesOther, stringArrayCaptor.getAllValues().get(2));

        // result 2: success with side effects of empty packages
        assertTrue(boolCaptor.getAllValues().get(3));
        assertEquals(1, intArrayCaptor.getAllValues().get(3).length);
        assertEquals(WifiManager.WIFI_INTERFACE_TYPE_AWARE,
                intArrayCaptor.getAllValues().get(3)[0]);
        assertEquals(1, stringArrayCaptor.getAllValues().get(3).length);
        assertNull(stringArrayCaptor.getAllValues().get(3)[0]);
    }

    @Test
    public void testTetheredSoftApTrackerWhenCountryCodeChanged() throws Exception {
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();

        registerSoftApCallbackAndVerify(mClientSoftApCallback);
        reset(mClientSoftApCallback);
        when(mWifiPermissionsUtil.checkCallersCoarseLocationPermission(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), anyInt(), any())).thenReturn(true);
        if (SdkLevel.isAtLeastT()) {
            verifyRegisterDriverCountryCodeChangedListenerSucceededAndTriggerListener(
                    mIOnWifiDriverCountryCodeChangedListener);
            reset(mIOnWifiDriverCountryCodeChangedListener);
        }
        ArgumentCaptor<SoftApCapability> capabilityArgumentCaptor = ArgumentCaptor.forClass(
                SoftApCapability.class);
        // Country code update with HAL started
        when(mWifiNative.isHalStarted()).thenReturn(true);
        // Channel 9 - 2452Mhz
        WifiAvailableChannel channels2g = new WifiAvailableChannel(2452,
                WifiAvailableChannel.OP_MODE_SAP);
        when(mWifiNative.isHalSupported()).thenReturn(true);
        when(mWifiNative.isHalStarted()).thenReturn(true);
        when(mWifiNative.getUsableChannels(eq(WifiScanner.WIFI_BAND_24_GHZ), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Arrays.asList(channels2g)));
        mWifiServiceImpl.mCountryCodeTracker.onCountryCodeChangePending(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
        mLooper.dispatchAll();
        if (SdkLevel.isAtLeastT()) {
            verify(mIOnWifiDriverCountryCodeChangedListener)
                    .onDriverCountryCodeChanged(TEST_COUNTRY_CODE);
        }
        verify(mClientSoftApCallback).onCapabilityChanged(capabilityArgumentCaptor.capture());
        assertEquals(1, capabilityArgumentCaptor.getValue()
                .getSupportedChannelList(SoftApConfiguration.BAND_2GHZ).length);
        assertEquals(9, capabilityArgumentCaptor.getValue()
                .getSupportedChannelList(SoftApConfiguration.BAND_2GHZ)[0]);
        reset(mClientSoftApCallback);
        // Country code update with HAL not started
        when(mWifiNative.isHalStarted()).thenReturn(false);
        mWifiServiceImpl.mCountryCodeTracker.onCountryCodeChangePending(TEST_NEW_COUNTRY_CODE);
        mLooper.dispatchAll();
        if (SdkLevel.isAtLeastT()) {
            verify(mIOnWifiDriverCountryCodeChangedListener, never())
                    .onDriverCountryCodeChanged(TEST_NEW_COUNTRY_CODE);
        }
        verify(mClientSoftApCallback)
                .onCapabilityChanged(capabilityArgumentCaptor.capture());
        // The supported channels in soft AP capability got invalidated.
        assertEquals(0, capabilityArgumentCaptor.getValue()
                .getSupportedChannelList(SoftApConfiguration.BAND_2GHZ).length);
        verify(mWifiNative, times(2)).getUsableChannels(eq(WifiScanner.WIFI_BAND_24_GHZ), anyInt(),
                anyInt());
    }

    /**
     * Verify that change network connection state is not allowed for App target below Q SDK from
     * guest user
     */
    @Test
    public void testNotAllowedToChangeWifiOpsTargetBelowQSdkFromGuestUser() throws Exception {
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOp(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, Process.myUid(), TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(),
                eq(Build.VERSION_CODES.Q), anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.isGuestUser()).thenReturn(true);

        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mWifiMetrics, never()).incrementNumWifiToggles(anyBoolean(), anyBoolean());
        verify(mWifiMetrics, never()).reportWifiStateChanged(anyBoolean(), anyBoolean(),
                anyBoolean());

        assertFalse(mWifiServiceImpl.disconnect(TEST_PACKAGE_NAME));
        assertFalse(mWifiServiceImpl.reconnect(TEST_PACKAGE_NAME));
        assertFalse(mWifiServiceImpl.reassociate(TEST_PACKAGE_NAME));

        assertTrue(mWifiServiceImpl.getConfiguredNetworks(TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                false).getList().isEmpty());
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).getConfiguredNetworks();

        assertFalse(mWifiServiceImpl.removeNetwork(0, TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).removeNetwork(anyInt(), anyInt(), anyString());

        assertFalse(mWifiServiceImpl.enableNetwork(0, false, TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).enableNetwork(anyInt(), anyBoolean(), anyInt(),
                anyString());
        verify(mWifiMetrics, never()).incrementNumEnableNetworkCalls();

        assertFalse(mWifiServiceImpl.disableNetwork(0, TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).disableNetwork(anyInt(), anyInt(), anyString());

        PasspointConfiguration passpointConfig = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        passpointConfig.setHomeSp(homeSp);
        assertFalse(mWifiServiceImpl.addOrUpdatePasspointConfiguration(passpointConfig,
                TEST_PACKAGE_NAME));
        mLooper.dispatchAll();
        verify(mPasspointManager, never()).addOrUpdateProvider(any(), anyInt(), anyString(),
                anyBoolean(), anyBoolean(), eq(false));

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork(TEST_SSID);
        assertEquals(-1, mWifiServiceImpl.addOrUpdateNetwork(config, TEST_PACKAGE_NAME,
                mAttribution));
    }

    /**
     * Tests that {@link WifiServiceImpl#getCurrentNetwork} throws
     * {@link SecurityException} if the caller doesn't have the necessary permissions.
     */
    @Test(expected = SecurityException.class)
    public void testGetCurrentNetworkNoPermission() throws Exception {
        doThrow(SecurityException.class)
                .when(mContext).enforceCallingOrSelfPermission(eq(ACCESS_WIFI_STATE), any());
        mWifiServiceImpl.getCurrentNetwork();
    }

    /**
     * Verifies that WifiServiceImpl#getCurrentNetwork() returns the current Wifi network.
     */
    @Test
    public void testGetCurrentNetworkWithNetworkSettingsPermission() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        Network mockNetwork = mock(Network.class);
        when(mActiveModeWarden.getCurrentNetwork()).thenReturn(mockNetwork);
        assertEquals(mockNetwork, mWifiServiceImpl.getCurrentNetwork());
    }

    @Test
    public void testQueryLastConfiguredTetheredApPassphraseSinceBootExceptions() {
        // good inputs should result in no exceptions.
        IStringListener listener = mock(IStringListener.class);
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.queryLastConfiguredTetheredApPassphraseSinceBoot(null));

        // No permission ==> SecurityException
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.queryLastConfiguredTetheredApPassphraseSinceBoot(listener));
    }

    @Test
    public void testQueryLastConfiguredTetheredApPassphraseSinceBoot() throws RemoteException {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        IStringListener listener = mock(IStringListener.class);
        String lastPassphrase = "testLastPassphrase";

        InOrder inOrder = inOrder(listener);
        when(mWifiApConfigStore.getLastConfiguredTetheredApPassphraseSinceBoot())
                .thenReturn(lastPassphrase);
        mWifiServiceImpl.queryLastConfiguredTetheredApPassphraseSinceBoot(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(lastPassphrase);

        // Test on null return.
        when(mWifiApConfigStore.getLastConfiguredTetheredApPassphraseSinceBoot())
                .thenReturn(null);
        mWifiServiceImpl.queryLastConfiguredTetheredApPassphraseSinceBoot(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(null);
    }

    @Test
    public void testChannelDataThrowsExceptionsAndOnResult() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastT());
        List<Bundle> dataList = new ArrayList<>();
        IListListener listener = new IListListener() {
            @Override
            public void onResult(List value) throws RemoteException {
                dataList.addAll(value);
            }
            @Override
            public IBinder asBinder() {
                return null;
            }
        };

        //verify listener null case
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getChannelData(null, TEST_PACKAGE_NAME, mExtras));

        when(mScanRequestProxy.getScanResults()).thenReturn(createChannelDataScanResults());
        mWifiServiceImpl.getChannelData(listener, TEST_PACKAGE_NAME, mExtras);
        mLooper.dispatchAll();

        //verify the result
        assertEquals(2, dataList.size());
        Bundle dataBundle1 = dataList.get(0);
        assertEquals(2412, dataBundle1.getInt(CHANNEL_DATA_KEY_FREQUENCY_MHZ));
        assertEquals(1, dataBundle1.getInt(CHANNEL_DATA_KEY_NUM_AP));
        Bundle dataBundle2 = dataList.get(1);
        assertEquals(5805, dataBundle2.getInt(CHANNEL_DATA_KEY_FREQUENCY_MHZ));
        assertEquals(2, dataBundle2.getInt(CHANNEL_DATA_KEY_NUM_AP));

        //verify it will fail without nearby permission
        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                any(), anyBoolean(), any());
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getChannelData(listener, TEST_PACKAGE_NAME, mExtras));
    }

    /**
     * Test register callback without ACCESS_WIFI_STATE permission.
     */
    @Test
    public void testRegisterLocalOnlyNetworkCallbackWithMissingAccessWifiPermission() {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        assertThrows(SecurityException.class, () -> mWifiServiceImpl
                .addLocalOnlyConnectionStatusListener(
                mLocalOnlyConnectionStatusListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID));
    }

    /**
     * Test unregister callback without permission.
     */
    @Test
    public void testUnregisterLocalOnlyNetworkCallbackWithMissingPermission() {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(ACCESS_WIFI_STATE), eq("WifiService"));
        assertThrows(SecurityException.class, () -> mWifiServiceImpl
                .removeLocalOnlyConnectionStatusListener(
                        mLocalOnlyConnectionStatusListener, TEST_PACKAGE_NAME));
    }

    /**
     * Test register nad unregister callback will go to WifiNetworkSuggestionManager
     */
    @Test
    public void testRegisterUnregisterLocalOnlyNetworkCallback() throws Exception {
        mWifiServiceImpl.addLocalOnlyConnectionStatusListener(
                mLocalOnlyConnectionStatusListener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        verify(mWifiNetworkFactory).addLocalOnlyConnectionStatusListener(
                eq(mLocalOnlyConnectionStatusListener), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID)
        );
        mWifiServiceImpl.removeLocalOnlyConnectionStatusListener(
                mLocalOnlyConnectionStatusListener, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiNetworkFactory).removeLocalOnlyConnectionStatusListener(
                eq(mLocalOnlyConnectionStatusListener), eq(TEST_PACKAGE_NAME));
    }

    private List<ScanResult> createChannelDataScanResults() {
        List<ScanResult> scanResults = new ArrayList<>();

        scanResults.add(new ScanResult.Builder(WifiSsid.fromUtf8Text(TEST_SSID), TEST_BSSID)
                        .setHessid(1245)
                        .setCaps(TEST_CAP)
                        .setRssi(-78)
                        .setFrequency(2412)
                        .setTsf(1025)
                        .setDistanceCm(22)
                        .setDistanceSdCm(33)
                        .setChannelWidth(20)
                        .setIs80211McRTTResponder(true)
                        .build());
        scanResults.add(new ScanResult.Builder(WifiSsid.fromUtf8Text(TEST_SSID), TEST_BSSID)
                .setHessid(1245)
                .setCaps(TEST_CAP)
                .setRssi(-85)
                .setFrequency(2417)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setChannelWidth(20)
                .setIs80211McRTTResponder(true)
                .build());
        scanResults.add(new ScanResult.Builder(WifiSsid.fromUtf8Text(TEST_SSID), TEST_BSSID)
                .setHessid(1245)
                .setCaps(TEST_CAP)
                .setRssi(-60)
                .setFrequency(5805)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setChannelWidth(20)
                .setIs80211McRTTResponder(true)
                .build());
        scanResults.add(new ScanResult.Builder(WifiSsid.fromUtf8Text(TEST_SSID), TEST_BSSID)
                .setHessid(1245)
                .setCaps(TEST_CAP)
                .setRssi(-70)
                .setFrequency(5805)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setChannelWidth(20)
                .setIs80211McRTTResponder(true)
                .build());
        return scanResults;
    }

    private void enableQosPolicyFeature() {
        when(mApplicationQosPolicyRequestHandler.isFeatureEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
    }

    private List<QosPolicyParams> createDownlinkQosPolicyParamsList(int size, boolean uniqueIds) {
        List<QosPolicyParams> policyParamsList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int policyId = uniqueIds ? i + 2 : 5;
            policyParamsList.add(new QosPolicyParams.Builder(
                    policyId, QosPolicyParams.DIRECTION_DOWNLINK)
                    .setUserPriority(QosPolicyParams.USER_PRIORITY_VIDEO_LOW)
                    .setIpVersion(QosPolicyParams.IP_VERSION_4)
                    .build());
        }
        return policyParamsList;
    }

    private static List<QosPolicyParams> createUplinkQosPolicyParamsList(int size) {
        List<QosPolicyParams> policyParamsList = new ArrayList<>();
        QosCharacteristics mockQosCharacteristics = mock(QosCharacteristics.class);
        when(mockQosCharacteristics.validate()).thenReturn(true);

        for (int i = 0; i < size; i++) {
            int policyId = i + 2;
            policyParamsList.add(new QosPolicyParams.Builder(
                    policyId, QosPolicyParams.DIRECTION_UPLINK)
                    .setQosCharacteristics(mockQosCharacteristics)
                    .build());
        }

        return policyParamsList;
    }

    /**
     * Verify that addQosPolicies works correctly.
     */
    @Test
    public void testAddQosPoliciesSuccess() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        enableQosPolicyFeature();

        ConcreteClientModeManager primaryCmm = mock(ConcreteClientModeManager.class);
        when(primaryCmm.isWifiStandardSupported(anyInt())).thenReturn(true);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(primaryCmm);
        mLooper.startAutoDispatch(); // handles call to isWifiStandardSupported

        List<QosPolicyParams> paramsList = createDownlinkQosPolicyParamsList(5, true);
        IBinder binder = mock(IBinder.class);
        IListListener listener = mock(IListListener.class);
        mWifiServiceImpl.addQosPolicies(paramsList, binder, TEST_PACKAGE_NAME, listener);
        int expectedNumCalls = 1;

        if (SdkLevel.isAtLeastV()) {
            // Uplink policies are supported on SDK >= V
            paramsList = createUplinkQosPolicyParamsList(5);
            mWifiServiceImpl.addQosPolicies(paramsList, binder, TEST_PACKAGE_NAME, listener);
            expectedNumCalls += 1;
        }

        mLooper.dispatchAll();
        verify(mApplicationQosPolicyRequestHandler, times(expectedNumCalls)).queueAddRequest(
                anyList(), any(), any(), anyInt());
        mLooper.stopAutoDispatchAndIgnoreExceptions();
    }

    /**
     * Verify the expected error cases in addQosPolicies.
     */
    @Test
    public void testAddQosPoliciesError() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        enableQosPolicyFeature();
        List<QosPolicyParams> paramsList = createDownlinkQosPolicyParamsList(5, true);
        IBinder binder = mock(IBinder.class);
        IListListener listener = mock(IListListener.class);

        // Feature disabled
        when(mApplicationQosPolicyRequestHandler.isFeatureEnabled()).thenReturn(false);
        mWifiServiceImpl.addQosPolicies(paramsList, binder, TEST_PACKAGE_NAME, listener);
        enableQosPolicyFeature();

        verify(listener).onResult(mListCaptor.capture());
        List<Integer> statusList = mListCaptor.getValue();
        for (Integer status : statusList) {
            assertEquals(WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN, (int) status);
        }

        // Null argument
        assertThrows(NullPointerException.class, () ->
                mWifiServiceImpl.addQosPolicies(null, binder, TEST_PACKAGE_NAME, listener));
        assertThrows(NullPointerException.class, () ->
                mWifiServiceImpl.addQosPolicies(paramsList, null, TEST_PACKAGE_NAME, listener));
        assertThrows(NullPointerException.class, () ->
                mWifiServiceImpl.addQosPolicies(paramsList, binder, TEST_PACKAGE_NAME, null));

        // Invalid QoS policy params list
        List<QosPolicyParams> emptyList = createDownlinkQosPolicyParamsList(0, true);
        List<QosPolicyParams> largeList = createDownlinkQosPolicyParamsList(
                WifiManager.getMaxNumberOfPoliciesPerQosRequest() + 1, true);
        List<QosPolicyParams> duplicatePolicyList = createDownlinkQosPolicyParamsList(5, false);
        assertThrows(IllegalArgumentException.class, () ->
                mWifiServiceImpl.addQosPolicies(emptyList, binder, TEST_PACKAGE_NAME,
                        listener));
        assertThrows(IllegalArgumentException.class, () ->
                mWifiServiceImpl.addQosPolicies(largeList, binder, TEST_PACKAGE_NAME,
                        listener));
        assertThrows(IllegalArgumentException.class, () ->
                mWifiServiceImpl.addQosPolicies(
                        duplicatePolicyList, binder, TEST_PACKAGE_NAME, listener));

        if (SdkLevel.isAtLeastV()) {
            List<QosPolicyParams> mixedDirectionList = createDownlinkQosPolicyParamsList(1, true);
            QosCharacteristics mockQosCharacteristics = mock(QosCharacteristics.class);
            when(mockQosCharacteristics.validate()).thenReturn(true);
            mixedDirectionList.add(
                    new QosPolicyParams.Builder(101, QosPolicyParams.DIRECTION_UPLINK)
                            .setQosCharacteristics(mockQosCharacteristics)
                            .build());
            assertThrows(IllegalArgumentException.class, () ->
                    mWifiServiceImpl.addQosPolicies(
                            mixedDirectionList, binder, TEST_PACKAGE_NAME, listener));
        }
    }

    /**
     * Verify that removeQosPolicies works correctly.
     */
    @Test
    public void testRemoveQosPoliciesSuccess() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        enableQosPolicyFeature();

        int[] policyIds = new int[]{1, 2, 3};
        mWifiServiceImpl.removeQosPolicies(policyIds, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mApplicationQosPolicyRequestHandler).queueRemoveRequest(anyList(), anyInt());
    }

    /**
     * Verify the expected error cases in removeQosPolicies.
     */
    @Test
    public void testRemoveQosPoliciesError() {
        assumeTrue(SdkLevel.isAtLeastU());
        enableQosPolicyFeature();

        // Invalid policy ID list
        int[] emptyPolicyIdList = new int[0];
        int[] largePolicyIdList = new int[WifiManager.getMaxNumberOfPoliciesPerQosRequest() + 1];
        int[] duplicatePolicyIdList = new int[] {1, 2, 2};
        assertThrows(NullPointerException.class, () ->
                mWifiServiceImpl.removeQosPolicies(null, TEST_PACKAGE_NAME));
        assertThrows(IllegalArgumentException.class, () ->
                mWifiServiceImpl.removeQosPolicies(emptyPolicyIdList, TEST_PACKAGE_NAME));
        assertThrows(IllegalArgumentException.class, () ->
                mWifiServiceImpl.removeQosPolicies(largePolicyIdList, TEST_PACKAGE_NAME));
        assertThrows(IllegalArgumentException.class, () ->
                mWifiServiceImpl.removeQosPolicies(duplicatePolicyIdList, TEST_PACKAGE_NAME));
    }

    /**
     * Verify that removeAllQosPolicies works correctly.
     */
    @Test
    public void testRemoveAllQosPolicies() {
        assumeTrue(SdkLevel.isAtLeastU());
        enableQosPolicyFeature();

        mWifiServiceImpl.removeAllQosPolicies(TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mApplicationQosPolicyRequestHandler).queueRemoveAllRequest(anyInt());
    }

    /**
     * Verify if set / get link layer stats polling interval works correctly
     */
    @Test
    public void testSetAndGetLinkLayerStatsPollingInterval() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mWifiServiceImpl.setLinkLayerStatsPollingInterval(
                TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS);
        mLooper.dispatchAll();
        verify(mClientModeManager).setLinkLayerStatsPollingInterval(
                eq(TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS));

        IIntegerListener listener = mock(IIntegerListener.class);
        when(mWifiGlobals.getPollRssiIntervalMillis()).thenReturn(
                TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS);
        mWifiServiceImpl.getLinkLayerStatsPollingInterval(listener);
        mLooper.dispatchAll();
        verify(listener).onResult(TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS);
    }

    /**
     * Test exceptions for set / get link layer stats polling interval
     */
    @Test
    public void testSetAndGetLinkLayerStatsPollingIntervalThrowsExceptions() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        // Verify IllegalArgumentException for negative interval
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setLinkLayerStatsPollingInterval(
                        -TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS));
        // Verify NullPointerException for null listener
        assertThrows(NullPointerException.class,
                () -> mWifiServiceImpl.getLinkLayerStatsPollingInterval(null));
        // Verify SecurityException when the caller does not have permission
        when(mContext.checkCallingOrSelfPermission(android.Manifest.permission
                .MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setLinkLayerStatsPollingInterval(
                        TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS));
        IIntegerListener listener = mock(IIntegerListener.class);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getLinkLayerStatsPollingInterval(listener));
    }

    /**
     * Verify {@link WifiServiceImpl#setMloMode(int)}.
     */
    @Test
    public void testSetMloMode() throws RemoteException {
        // Android U+ only.
        assumeTrue(SdkLevel.isAtLeastU());
        // Mock listener.
        IBooleanListener listener = mock(IBooleanListener.class);
        InOrder inOrder = inOrder(listener);

        // Verify permission.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setMloMode(WifiManager.MLO_MODE_DEFAULT, listener));
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                true);

        // Verify setMloMode() success.
        when(mWifiNative.setMloMode(eq(WifiManager.MLO_MODE_LOW_POWER))).thenReturn(
                WifiStatusCode.SUCCESS);
        mWifiServiceImpl.setMloMode(WifiManager.MLO_MODE_LOW_POWER, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(true);

        // Verify setMloMode() failure case.
        mLooper.startAutoDispatch();
        when(mWifiNative.setMloMode(eq(WifiManager.MLO_MODE_HIGH_THROUGHPUT))).thenReturn(
                WifiStatusCode.ERROR_INVALID_ARGS);
        mWifiServiceImpl.setMloMode(WifiManager.MLO_MODE_HIGH_THROUGHPUT, listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(false);
    }

    /**
     * Verify {@link WifiServiceImpl#getMloMode()}.
     */
    @Test
    public void testGetMloMode() throws RemoteException {
        // Android U+ only.
        assumeTrue(SdkLevel.isAtLeastU());
        // Mock listener.
        IIntegerListener listener = mock(IIntegerListener.class);
        InOrder inOrder = inOrder(listener);

        // Verify permission.
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getMloMode(listener));
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                true);

        // Verify getMloMode() success.
        when(mWifiNative.getMloMode()).thenReturn(WifiManager.MLO_MODE_LOW_POWER);
        mWifiServiceImpl.getMloMode(listener);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(WifiManager.MLO_MODE_LOW_POWER);
    }

    /**
     * Verify add and remove of Wi-Fi low latency listener.
     */
    @Test
    public void testWifiDeviceLowLatencyModeListener() {
        // Setup listener.
        IWifiLowLatencyLockListener testListener = mock(IWifiLowLatencyLockListener.class);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                true);

        // Test success case.
        mWifiServiceImpl.addWifiLowLatencyLockListener(testListener);
        mLooper.dispatchAll();
        verify(mLockManager).addWifiLowLatencyLockListener(testListener);
        mWifiServiceImpl.removeWifiLowLatencyLockListener(testListener);
        mLooper.dispatchAll();
        verify(mLockManager).removeWifiLowLatencyLockListener(testListener);

        // Test null listener.
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.addWifiLowLatencyLockListener(null));
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.removeWifiLowLatencyLockListener(null));

        // Expect exception when caller has no permission.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.addWifiLowLatencyLockListener(testListener));

        // No exception for remove.
        mWifiServiceImpl.removeWifiLowLatencyLockListener(testListener);
    }

    /**
     * Verify that setWifiEnabled() returns false when satellite mode is on.
     */
    @Test
    public void testSetWifiEnabledSatelliteModeOn() {
        when(mSettingsStore.isSatelliteModeOn()).thenReturn(true);
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
    }

    /**
     * Verify {@link WifiServiceImpl#getMaxMloAssociationLinkCount(IIntegerListener)} and
     * {@link WifiServiceImpl#getMaxMloStrLinkCount(IIntegerListener)}.
     */
    @Test
    public void testGetMloCapabilities() throws RemoteException {
        // Android U+ only.
        assumeTrue(SdkLevel.isAtLeastU());
        // Mock listener.
        IIntegerListener listener = mock(IIntegerListener.class);
        InOrder inOrder = inOrder(listener);

        // Verify permission.
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getMaxMloStrLinkCount(listener, mExtras));
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getMaxMloAssociationLinkCount(listener, mExtras));
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                true);

        // verify listener == null.
        assertThrows(NullPointerException.class,
                () -> mWifiServiceImpl.getMaxMloStrLinkCount(null, mExtras));
        assertThrows(NullPointerException.class,
                () -> mWifiServiceImpl.getMaxMloAssociationLinkCount(null, mExtras));

        // Verify getMaxMloAssociationLinkCount().
        when(mWifiNative.getMaxMloAssociationLinkCount(WIFI_IFACE_NAME)).thenReturn(3);
        mWifiServiceImpl.getMaxMloAssociationLinkCount(listener, mExtras);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(3);

        // Verify getMaxMloStrLinkCount().
        when(mWifiNative.getMaxMloStrLinkCount(WIFI_IFACE_NAME)).thenReturn(2);
        mWifiServiceImpl.getMaxMloStrLinkCount(listener, mExtras);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(2);
    }

    @Test
    public void testSupportedBandCombinations() throws RemoteException {
        // Android U+ only.
        assumeTrue(SdkLevel.isAtLeastU());
        // Mock listener.
        IWifiBandsListener listener = mock(IWifiBandsListener.class);
        InOrder inOrder = inOrder(listener);

        // Verify permission.
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getSupportedSimultaneousBandCombinations(listener, mExtras));
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt())).thenReturn(
                true);

        // verify listener == null.
        assertThrows(NullPointerException.class,
                () -> mWifiServiceImpl.getSupportedSimultaneousBandCombinations(null, mExtras));

        // verify the behaviour if the band information is not available.
        Set<List<Integer>> supportedBandsSet = null;
        when(mWifiNative.getSupportedBandCombinations(WIFI_IFACE_NAME)).thenReturn(
                supportedBandsSet);
        mWifiServiceImpl.getSupportedSimultaneousBandCombinations(listener, mExtras);
        mLooper.dispatchAll();
        inOrder.verify(listener).onResult(eq(new WifiBands[0]));

        // Verify positive case.
        supportedBandsSet = Set.of(new ArrayList(Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ)),
                new ArrayList(Arrays.asList(WifiScanner.WIFI_BAND_5_GHZ)),
                new ArrayList(Arrays.asList(WifiScanner.WIFI_BAND_6_GHZ)), new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_5_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_6_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_5_GHZ, WifiScanner.WIFI_BAND_6_GHZ)),
                new ArrayList(
                        Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_5_GHZ,
                                WifiScanner.WIFI_BAND_6_GHZ)));
        when(mWifiNative.getSupportedBandCombinations(WIFI_IFACE_NAME)).thenReturn(
                supportedBandsSet);
        mWifiServiceImpl.getSupportedSimultaneousBandCombinations(listener, mExtras);
        mLooper.dispatchAll();
        ArgumentCaptor<WifiBands[]> resultCaptor = ArgumentCaptor.forClass(WifiBands[].class);
        inOrder.verify(listener).onResult(resultCaptor.capture());
        int entries = 0;
        for (WifiBands band : resultCaptor.getValue()) {
            ++entries;
            assertTrue(supportedBandsSet.stream().anyMatch(l -> {
                for (int i = 0; i < band.bands.length; ++i) {
                    if (i >= l.size()) return false;
                    if (band.bands[i] != l.get(i)) return false;
                }
                return true;
            }));
        }
        assertTrue(entries == supportedBandsSet.size());
    }

    /**
     * Verify that on a country code change, we inform the AFC Manager.
     */
    @Test
    public void testInformAfcManagerOnCountryCodeChange() {
        final String newCountryCode = "US";
        mWifiServiceImpl.mCountryCodeTracker.onDriverCountryCodeChanged(newCountryCode);
        mLooper.dispatchAll();

        // check that we let the AFC Manager know that the country code has changed
        verify(mAfcManager).onCountryCodeChange(newCountryCode);
    }

    @Test
    public void testShutDownHandling() {
        mWifiServiceImpl.checkAndStartWifi();
        mWifiServiceImpl.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                argThat((IntentFilter filter) ->
                        filter.hasAction(ACTION_SHUTDOWN)),
                isNull(),
                any(Handler.class));
        Intent intent = new Intent(ACTION_SHUTDOWN);
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
        verify(mActiveModeWarden).notifyShuttingDown();
        verify(mWifiScoreCard).resetAllConnectionStates();
        verify(mWifiConfigManager).handleShutDown();
    }

    @Test(expected = SecurityException.class)
    public void testSetWepAllowedWithoutPermission() {
        // by default no permissions are given so the call should fail.
        mWifiServiceImpl.setWepAllowed(true);
    }

    @Test
    public void testSetWepAllowedWithPermission() {
        when(mWifiSettingsConfigStore.get(eq(WIFI_WEP_ALLOWED))).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).get(eq(WIFI_WEP_ALLOWED));
        verify(mWifiGlobals).setWepAllowed(eq(false));
        // verify setWepAllowed with MANAGE_WIFI_NETWORK_SETTING
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mWifiServiceImpl.setWepAllowed(true);
        mLooper.dispatchAll();
        verify(mWifiGlobals).setWepAllowed(true);
        verify(mWifiSettingsConfigStore).put(eq(WIFI_WEP_ALLOWED), eq(true));
    }

    @Test
    public void testSetWepDisAllowedWithPermission() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        ConcreteClientModeManager cmmWep = mock(ConcreteClientModeManager.class);
        ConcreteClientModeManager cmmWpa = mock(ConcreteClientModeManager.class);
        WifiInfo mockWifiInfoWep = mock(WifiInfo.class);
        WifiInfo mockWifiInfoWpa = mock(WifiInfo.class);
        List<ClientModeManager> cmms = Arrays.asList(cmmWep, cmmWpa);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(cmms);
        when(mockWifiInfoWep.getCurrentSecurityType()).thenReturn(WifiInfo.SECURITY_TYPE_WEP);
        when(mockWifiInfoWpa.getCurrentSecurityType()).thenReturn(WifiInfo.SECURITY_TYPE_PSK);
        when(cmmWep.getConnectionInfo()).thenReturn(mockWifiInfoWep);
        when(cmmWpa.getConnectionInfo()).thenReturn(mockWifiInfoWpa);
        mWifiServiceImpl.setWepAllowed(false);
        mLooper.dispatchAll();
        verify(mWifiGlobals).setWepAllowed(false);
        verify(mWifiSettingsConfigStore).put(eq(WIFI_WEP_ALLOWED), eq(false));
        // Only WEP disconnect
        verify(cmmWep).disconnect();
        verify(cmmWpa, never()).disconnect();
    }

    @Test
    public void testQueryWepAllowedExceptionCases() {
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.queryWepAllowed(null));
        // by default no permissions are given so the call should fail.
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.queryWepAllowed(mock(IBooleanListener.class)));
    }

    @Test
    public void testQueryWepAllowedNormalCase() throws Exception {
        when(mWifiSettingsConfigStore.get(eq(WIFI_WEP_ALLOWED))).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).get(eq(WIFI_WEP_ALLOWED));
        verify(mWifiGlobals).setWepAllowed(eq(false));
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        IBooleanListener listener = mock(IBooleanListener.class);

        InOrder inOrder = inOrder(listener);
        when(mWifiSettingsConfigStore.get(eq(WIFI_WEP_ALLOWED))).thenReturn(true);
        mWifiServiceImpl.queryWepAllowed(listener);
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore, times(2)).get(eq(WIFI_WEP_ALLOWED));
        inOrder.verify(listener).onResult(true);

        when(mWifiSettingsConfigStore.get(eq(WIFI_WEP_ALLOWED))).thenReturn(false);
        mWifiServiceImpl.queryWepAllowed(listener);
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore, times(3)).get(eq(WIFI_WEP_ALLOWED));
        inOrder.verify(listener).onResult(false);
    }

    @Test
    public void testEnableAndDisableMscsSuccess() {
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager));
        when(mClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);

        // Test enableMscs
        MscsParams mscsParams = new MscsParams.Builder().build();
        mWifiServiceImpl.enableMscs(mscsParams);
        mLooper.dispatchAll();
        verify(mWifiNative).enableMscs(eq(mscsParams), eq(WIFI_IFACE_NAME));

        // Test disableMscs
        mWifiServiceImpl.disableMscs();
        mLooper.dispatchAll();
        verify(mWifiNative).disableMscs(eq(WIFI_IFACE_NAME));
    }

    @Test
    public void testEnableAndDisableMscsFailure() {
        // Verify the permissions check on both methods
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(false);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.enableMscs(mock(MscsParams.class)));
        assertThrows(SecurityException.class, () -> mWifiServiceImpl.disableMscs());

        // Verify the nullity check on enableMscs
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        assertThrows(NullPointerException.class, () -> mWifiServiceImpl.enableMscs(null));
    }

    @Test(expected = SecurityException.class)
    public void testSetSendDhcpHostnameRestrictionWithoutPermission() {
        // by default no permissions are given so the call should fail.
        mWifiServiceImpl.setSendDhcpHostnameRestriction(
                TEST_PACKAGE_NAME, WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSendDhcpHostnameRestrictionInvalidFlags() {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.setSendDhcpHostnameRestriction(
                TEST_PACKAGE_NAME, -1);
    }

    @Test
    public void testSetSendDhcpHostnameRestrictionWithPermission() {
        // verify setSendDhcpHostnameRestriction with NETWORK_SETTINGS
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.setSendDhcpHostnameRestriction(
                TEST_PACKAGE_NAME, WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN);
        mLooper.dispatchAll();
        verify(mWifiGlobals).setSendDhcpHostnameRestriction(
                eq(WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN));
    }

    @Test
    public void testQuerySendDhcpHostnameRestrictionExceptionCases() {
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.querySendDhcpHostnameRestriction(TEST_PACKAGE_NAME, null));
        // by default no permissions are given so the call should fail.
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.querySendDhcpHostnameRestriction(
                        TEST_PACKAGE_NAME, mock(IIntegerListener.class)));
    }

    @Test
    public void testQuerySendDhcpHostnameRestrictionNormalCase() throws Exception {
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        IIntegerListener listener = mock(IIntegerListener.class);

        InOrder inOrder = inOrder(listener);
        when(mWifiGlobals.getSendDhcpHostnameRestriction())
                .thenReturn(0);
        mWifiServiceImpl.querySendDhcpHostnameRestriction(TEST_PACKAGE_NAME, listener);
        mLooper.dispatchAll();
        verify(mWifiGlobals).getSendDhcpHostnameRestriction();
        inOrder.verify(listener).onResult(0);

        when(mWifiGlobals.getSendDhcpHostnameRestriction())
                .thenReturn(WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN);
        mWifiServiceImpl.querySendDhcpHostnameRestriction(TEST_PACKAGE_NAME, listener);
        mLooper.dispatchAll();
        verify(mWifiGlobals, times(2)).getSendDhcpHostnameRestriction();
        inOrder.verify(listener).onResult(WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN);
    }

    @Test
    public void testSetPerSsidRoamingModeByDeviceAdmin() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setPerSsidRoamingMode(
                        WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                        WifiManager.ROAMING_MODE_NORMAL, TEST_PACKAGE_NAME));
        when(mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(anyInt(),
                eq(TEST_PACKAGE_NAME))).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setPerSsidRoamingMode(
                        WifiSsid.fromString(TEST_SSID_WITH_QUOTES), -1, TEST_PACKAGE_NAME));

        mWifiServiceImpl.setPerSsidRoamingMode(WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                WifiManager.ROAMING_MODE_NORMAL, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiRoamingModeManager).setPerSsidRoamingMode(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                WifiManager.ROAMING_MODE_NORMAL, true);
    }

    @Test
    public void testSetPerSsidRoamingModeByNonAdmin() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setPerSsidRoamingMode(
                        WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                        WifiManager.ROAMING_MODE_NORMAL, TEST_PACKAGE_NAME));
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(
                anyInt())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setPerSsidRoamingMode(
                        WifiSsid.fromString(TEST_SSID_WITH_QUOTES), -1, TEST_PACKAGE_NAME));

        mWifiServiceImpl.setPerSsidRoamingMode(WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                WifiManager.ROAMING_MODE_NORMAL, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiRoamingModeManager).setPerSsidRoamingMode(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                WifiManager.ROAMING_MODE_NORMAL, false);
    }

    @Test
    public void testRemovePerSsidRoamingModeByDeviceAdmin() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.removePerSsidRoamingMode(
                        WifiSsid.fromString(TEST_SSID_WITH_QUOTES), TEST_PACKAGE_NAME));
        when(mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(anyInt(),
                eq(TEST_PACKAGE_NAME))).thenReturn(true);

        mWifiServiceImpl.removePerSsidRoamingMode(WifiSsid.fromString(TEST_SSID_WITH_QUOTES),
                TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiRoamingModeManager).removePerSsidRoamingMode(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES), true);
    }

    @Test
    public void testRemovePerSsidRoamingModeByNonAdmin() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.removePerSsidRoamingMode(
                        WifiSsid.fromString(TEST_SSID_WITH_QUOTES), TEST_PACKAGE_NAME));
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);

        mWifiServiceImpl.removePerSsidRoamingMode(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES), TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(mWifiRoamingModeManager).removePerSsidRoamingMode(
                WifiSsid.fromString(TEST_SSID_WITH_QUOTES), false);
    }

    @Test
    public void testGetPerSsidRoamingModesByDeviceAdmin() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT);
        IMapListener listener = mock(IMapListener.class);
        InOrder inOrder = inOrder(listener);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getPerSsidRoamingModes(TEST_PACKAGE_NAME, listener));
        when(mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(anyInt(),
                eq(TEST_PACKAGE_NAME))).thenReturn(true);

        Map<String, Integer> deviceAdminRoamingPolicies = new ArrayMap<>();
        deviceAdminRoamingPolicies.put(TEST_SSID_WITH_QUOTES, WifiManager.ROAMING_MODE_NORMAL);
        when(mWifiRoamingModeManager.getPerSsidRoamingModes(eq(true))).thenReturn(
                deviceAdminRoamingPolicies);

        mWifiServiceImpl.getPerSsidRoamingModes(TEST_PACKAGE_NAME, listener);
        mLooper.dispatchAll();
        ArgumentCaptor<Map<String, Integer>> resultCaptor = ArgumentCaptor.forClass(Map.class);
        inOrder.verify(listener).onResult(resultCaptor.capture());

        assertTrue(resultCaptor.getValue().get(TEST_SSID_WITH_QUOTES)
                == deviceAdminRoamingPolicies.get(TEST_SSID_WITH_QUOTES));
        assertTrue(resultCaptor.getValue().size()
                == deviceAdminRoamingPolicies.size());
    }

    @Test
    public void testGetPerSsidRoamingModesByNonAdmin() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getSupportedFeatureSet())
                .thenReturn(WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT);
        IMapListener listener = mock(IMapListener.class);
        InOrder inOrder = inOrder(listener);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getPerSsidRoamingModes(TEST_PACKAGE_NAME, listener));
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(
                anyInt())).thenReturn(true);

        Map<String, Integer> nonAdminRoamingPolicies = new ArrayMap<>();
        nonAdminRoamingPolicies.put(TEST_SSID_WITH_QUOTES, WifiManager.ROAMING_MODE_NONE);
        when(mWifiRoamingModeManager.getPerSsidRoamingModes(false)).thenReturn(
                nonAdminRoamingPolicies);

        mWifiServiceImpl.getPerSsidRoamingModes(TEST_PACKAGE_NAME, listener);
        mLooper.dispatchAll();
        ArgumentCaptor<Map<String, Integer>> resultCaptor = ArgumentCaptor.forClass(Map.class);
        inOrder.verify(listener).onResult(resultCaptor.capture());

        assertTrue(resultCaptor.getValue().get(TEST_SSID_WITH_QUOTES)
                == nonAdminRoamingPolicies.get(TEST_SSID_WITH_QUOTES));
        assertTrue(resultCaptor.getValue().size()
                == nonAdminRoamingPolicies.size());
    }

    private void verifyIsPnoSupported(boolean isBackgroundScanSupported, boolean isSwPnoEnabled,
            boolean isPnoFeatureSet) {
        when(mWifiGlobals.isBackgroundScanSupported()).thenReturn(isBackgroundScanSupported);
        when(mWifiGlobals.isSwPnoEnabled()).thenReturn(isSwPnoEnabled);
        if (isPnoFeatureSet) {
            when(mActiveModeWarden.getSupportedFeatureSet())
                    .thenReturn(WifiManager.WIFI_FEATURE_PNO);
        } else {
            when(mActiveModeWarden.getSupportedFeatureSet()).thenReturn(0L);
        }
        assertEquals(mWifiServiceImpl.isPnoSupported(),
                (isBackgroundScanSupported && isPnoFeatureSet) || isSwPnoEnabled);
    }

    /*
     * Verify that Pno is supported.
     */
    @Test
    public void testIsPnoSupported() throws Exception {
        verifyIsPnoSupported(false, false, false);
        verifyIsPnoSupported(true, false, false);
        verifyIsPnoSupported(false, true, false);
        verifyIsPnoSupported(false, false, true);
        verifyIsPnoSupported(true, true, false);
        verifyIsPnoSupported(false, true, true);
        verifyIsPnoSupported(true, false, true);
        verifyIsPnoSupported(true, true, true);
    }

    @Test
    public void testSetD2dAllowedWhenInfraStaDisabled() throws Exception {
        // by default no permissions are given so the call should fail.
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setD2dAllowedWhenInfraStaDisabled(true));
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        // verify setD2DAllowedWhenInfraStaDisabled with MANAGE_WIFI_NETWORK_SETTING
        when(mContext.checkPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiServiceImpl.setD2dAllowedWhenInfraStaDisabled(true);
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).put(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED), eq(true));

        mWifiServiceImpl.setD2dAllowedWhenInfraStaDisabled(false);
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).put(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED), eq(false));
    }

    @Test
    public void testQueryD2dAllowedWhenInfraStaDisabled() throws Exception {
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.queryD2dAllowedWhenInfraStaDisabled(null));

        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        IBooleanListener listener = mock(IBooleanListener.class);

        InOrder inOrder = inOrder(listener);
        when(mWifiSettingsConfigStore.get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)))
                .thenReturn(true);
        mWifiServiceImpl.queryD2dAllowedWhenInfraStaDisabled(listener);
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED));
        inOrder.verify(listener).onResult(true);

        when(mWifiSettingsConfigStore.get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)))
                .thenReturn(false);
        mWifiServiceImpl.queryD2dAllowedWhenInfraStaDisabled(listener);
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore, times(2)).get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED));
        inOrder.verify(listener).onResult(false);
    }

    @Test
    public void testGetTwtCapabilities() {
        assumeTrue(SdkLevel.isAtLeastV());
        ITwtCapabilitiesListener iTwtCapabilitiesListener = mock(ITwtCapabilitiesListener.class);
        // Test permission check
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getTwtCapabilities(iTwtCapabilitiesListener, mExtras));
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        // Test with null parameters
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getTwtCapabilities(null, mExtras));
        // Test getTwtCapabilities call
        mWifiServiceImpl.getTwtCapabilities(iTwtCapabilitiesListener, mExtras);
        mLooper.dispatchAll();
        verify(mTwtManager).getTwtCapabilities(eq(WIFI_IFACE_NAME), eq(iTwtCapabilitiesListener));
    }

    @Test
    public void testSetupTwtSession() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV());
        ITwtCallback iTwtCallback = mock(ITwtCallback.class);
        TwtRequest twtRequest = mock(TwtRequest.class);
        // Test permission check
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.setupTwtSession(twtRequest, iTwtCallback, mExtras));
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        // Test with null parameters
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setupTwtSession(null, iTwtCallback, mExtras));
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.setupTwtSession(twtRequest, null, mExtras));
        // Test with station not connected
        when(mClientModeManager.isConnected()).thenReturn(false);
        mWifiServiceImpl.setupTwtSession(twtRequest, iTwtCallback, mExtras);
        mLooper.dispatchAll();
        verify(iTwtCallback).onFailure(eq(TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE));
        // Test setupTwtSession with station connected
        when(mClientModeManager.isConnected()).thenReturn(true);
        mWifiServiceImpl.setupTwtSession(twtRequest, iTwtCallback, mExtras);
        mLooper.dispatchAll();
        verify(mTwtManager).setupTwtSession(eq(WIFI_IFACE_NAME), eq(twtRequest), eq(iTwtCallback),
                eq(Binder.getCallingUid()));
    }

    @Test
    public void testGetStatsTwtSession() {
        assumeTrue(SdkLevel.isAtLeastV());
        int sessionId = 10;
        ITwtStatsListener iTwtStatsListener = mock(ITwtStatsListener.class);
        // Test permission check
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.getStatsTwtSession(sessionId, iTwtStatsListener, mExtras));
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        // Test with null parameters
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.getStatsTwtSession(sessionId, null, mExtras));
        // Test getStatsTwtSession call
        mWifiServiceImpl.getStatsTwtSession(sessionId, iTwtStatsListener, mExtras);
        mLooper.dispatchAll();
        verify(mTwtManager).getStatsTwtSession(eq(WIFI_IFACE_NAME), eq(iTwtStatsListener),
                eq(sessionId));
    }

    @Test
    public void testTeardownTwtSession() {
        assumeTrue(SdkLevel.isAtLeastV());
        int sessionId = 10;
        // Test permission check
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.teardownTwtSession(sessionId, mExtras));
        mWifiServiceImpl.checkAndStartWifi();
        mLooper.dispatchAll();
        when(mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        // Test teardownTwtSession call
        mWifiServiceImpl.teardownTwtSession(sessionId, mExtras);
        mLooper.dispatchAll();
        verify(mTwtManager).tearDownTwtSession(eq(WIFI_IFACE_NAME), eq(sessionId));
    }

    @SuppressWarnings("BoxedPrimitiveEquality")
    @Test
    public void testRetrieveWifiBackupData() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        // Expect exception when caller has no permission
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        IByteArrayListener mockTestListener = mock(IByteArrayListener.class);
        // by default no permissions are given so the call should fail.
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.retrieveWifiBackupData(mockTestListener));
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        // null listener ==> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mWifiServiceImpl.retrieveWifiBackupData(null));
        mWifiServiceImpl.retrieveWifiBackupData(mockTestListener);
        mLooper.dispatchAll();
        verify(mBackupRestoreController).retrieveBackupData();
        verify(mockTestListener).onResult(any());
    }

    @SuppressWarnings("BoxedPrimitiveEquality")
    @Test
    public void testRestoreWifiBackupData() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        byte[] mockTestRestoredData = new byte[0];
        // Expect exception when caller has no permission
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        // by default no permissions are given so the call should fail.
        assertThrows(SecurityException.class,
                () -> mWifiServiceImpl.restoreWifiBackupData(mockTestRestoredData));
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.restoreWifiBackupData(mockTestRestoredData);
        mLooper.dispatchAll();
        verify(mBackupRestoreController).parserBackupDataAndDispatch(mockTestRestoredData);
    }
}
