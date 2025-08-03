/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.net.NetworkInfo.DetailedState.AUTHENTICATING;
import static android.net.NetworkInfo.DetailedState.CONNECTED;
import static android.net.NetworkInfo.DetailedState.CONNECTING;
import static android.net.NetworkInfo.DetailedState.DISCONNECTED;
import static android.net.NetworkInfo.DetailedState.OBTAINING_IPADDR;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_METERED;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NONE;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NOT_METERED;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_NO_INTERNET_PERMANENT;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_UNWANTED_LOW_RSSI;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED;
import static android.net.wifi.WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE;
import static android.net.wifi.WifiConfiguration.SECURITY_TYPE_OPEN;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_OWE;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_PSK;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_SUCCESS;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.ClientModeImpl.ARP_TABLE_PATH;
import static com.android.server.wifi.ClientModeImpl.CMD_PRE_DHCP_ACTION;
import static com.android.server.wifi.ClientModeImpl.CMD_PRE_DHCP_ACTION_COMPLETE;
import static com.android.server.wifi.ClientModeImpl.CMD_UNWANTED_NETWORK;
import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;
import static com.android.server.wifi.WifiSettingsConfigStore.SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_STA_FACTORY_MAC_ADDRESS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyByte;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.app.test.TestAlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback.AssociationRejectionData;
import android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback.MboAssocDisallowedReasonCode;
import android.net.CaptivePortalData;
import android.net.DhcpResultsParcelable;
import android.net.InetAddresses;
import android.net.IpConfiguration;
import android.net.IpPrefix;
import android.net.Layer2InformationParcelable;
import android.net.Layer2PacketParcelable;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkProvider;
import android.net.NetworkSpecifier;
import android.net.ProvisioningConfigurationParcelable;
import android.net.RouteInfo;
import android.net.StaticIpConfiguration;
import android.net.Uri;
import android.net.apf.ApfCapabilities;
import android.net.ip.IIpClient;
import android.net.ip.IpClientCallbacks;
import android.net.networkstack.aidl.ip.ReachabilityLossInfoParcelable;
import android.net.networkstack.aidl.ip.ReachabilityLossReason;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnNetworkPolicyResult;
import android.net.wifi.IActionListener;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkAgentSpecifier;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.flags.Flags;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.util.ScanResultUtil;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.Range;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ClientMode.LinkProbeCallback;
import com.android.server.wifi.ClientModeManagerBroadcastQueue.QueuedBroadcast;
import com.android.server.wifi.WifiNative.ConnectionCapabilities;
import com.android.server.wifi.WifiScoreCard.PerBssid;
import com.android.server.wifi.WifiScoreCard.PerNetwork;
import com.android.server.wifi.b2b.WifiRoamingModeManager;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.PasspointProvisioningTestUtil;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.p2p.WifiP2pServiceImpl;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.proto.nano.WifiMetricsProto.StaEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiIsUnusableEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStats;
import com.android.server.wifi.util.ActionListenerWrapper;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.RssiUtilTest;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.verification.VerificationMode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Unit tests for {@link com.android.server.wifi.ClientModeImpl}.
 */
@SmallTest
public class ClientModeImplTest extends WifiBaseTest {
    public static final String TAG = "ClientModeImplTest";

    private static final int MANAGED_PROFILE_UID = 1100000;
    private static final int OTHER_USER_UID = 1200000;
    private static final int LOG_REC_LIMIT_IN_VERBOSE_MODE = ClientModeImpl.NUM_LOG_RECS_VERBOSE;
    private static final int LOG_REC_LIMIT_IN_VERBOSE_MODE_LOW_RAM =
            ClientModeImpl.NUM_LOG_RECS_VERBOSE_LOW_MEMORY;
    private static final int FRAMEWORK_NETWORK_ID = 0;
    private static final int PASSPOINT_NETWORK_ID = 1;
    private static final int OTHER_NETWORK_ID = 47;
    private static final int TEST_RSSI = -54;
    private static final int TEST_NETWORK_ID = 54;
    private static final int WPS_SUPPLICANT_NETWORK_ID = 5;
    private static final int WPS_FRAMEWORK_NETWORK_ID = 10;
    private static final String DEFAULT_TEST_SSID = "\"GoogleGuest\"";
    private static final String OP_PACKAGE_NAME = "com.xxx";
    private static final int TEST_UID = Process.SYSTEM_UID + 1000;
    private static final MacAddress TEST_GLOBAL_MAC_ADDRESS =
            MacAddress.fromString("10:22:34:56:78:92");
    private static final MacAddress TEST_LOCAL_MAC_ADDRESS =
            MacAddress.fromString("2a:53:43:c3:56:21");
    private static final MacAddress TEST_LOCAL_MAC_ADDRESS_SECONDARY_DBS =
            MacAddress.fromString("2a:53:43:c3:56:22");
    private static final MacAddress TEST_DEFAULT_MAC_ADDRESS =
            MacAddress.fromString(WifiInfo.DEFAULT_MAC_ADDRESS);
   // NetworkAgent creates threshold ranges with Integers
    private static final int RSSI_THRESHOLD_MAX = -30;
    private static final int RSSI_THRESHOLD_MIN = -76;
    // Threshold breach callbacks are called with bytes
    private static final byte RSSI_THRESHOLD_BREACH_MIN = -80;
    private static final byte RSSI_THRESHOLD_BREACH_MAX = -20;

    private static final int DATA_SUBID = 1;
    private static final int CARRIER_ID_1 = 100;

    private static final long TEST_BSSID = 0x112233445566L;
    private static final int TEST_DELAY_IN_SECONDS = 300;
    private static final String TEST_ATTRIBUTION_TAG = "TEST_ATTRIBUTION_TAG";

    private static final int DEFINED_ERROR_CODE = 32764;
    private static final String TEST_TERMS_AND_CONDITIONS_URL =
            "https://policies.google.com/terms?hl=en-US";
    private static final String VENUE_URL =
            "https://www.android.com/android-11/";
    private static final long[] TEST_RCOI_ARRAY = {0xcafeL, 0xbabaL};
    private static final long TEST_MATCHED_RCOI = TEST_RCOI_ARRAY[0];

    private static final String TEST_AP_MLD_MAC_ADDRESS_STR = "02:03:04:05:06:07";
    private static final MacAddress TEST_AP_MLD_MAC_ADDRESS =
            MacAddress.fromString(TEST_AP_MLD_MAC_ADDRESS_STR);

    private static final String TEST_MLO_LINK_ADDR_STR = "02:03:04:05:06:0A";
    private static final MacAddress TEST_MLO_LINK_ADDR =
            MacAddress.fromString(TEST_MLO_LINK_ADDR_STR);

    private static final String TEST_MLO_LINK_ADDR_1_STR = "02:03:04:05:06:0B";
    private static final MacAddress TEST_MLO_LINK_ADDR_1 =
            MacAddress.fromString(TEST_MLO_LINK_ADDR_1_STR);

    private static final int TEST_MLO_LINK_ID = 1;
    private static final int TEST_MLO_LINK_ID_1 = 2;

    private static final int TEST_MLO_LINK_FREQ = 5160;
    private static final int TEST_MLO_LINK_FREQ_1 = 2437;

    private static final String TEST_TDLS_PEER_ADDR_STR = "02:55:11:02:36:4C";

    private long mBinderToken;
    private MockitoSession mSession;
    private TestNetworkParams mTestNetworkParams = new TestNetworkParams();

    // Attribution tag to be used in the mocked connect sequence
    private String mAttributionTagForConnect = TEST_ATTRIBUTION_TAG;

    /**
     * Helper class for setting the default parameters of the WifiConfiguration that gets used
     * in connect().
     */
    class TestNetworkParams {
        public boolean hasEverConnected = false;
    }

    private static <T> T mockWithInterfaces(Class<T> class1, Class<?>... interfaces) {
        return mock(class1, withSettings().extraInterfaces(interfaces));
    }

    private void enableDebugLogs() {
        mCmi.enableVerboseLogging(true);
    }

    private FrameworkFacade getFrameworkFacade() throws Exception {
        FrameworkFacade facade = mock(FrameworkFacade.class);

        doAnswer(new AnswerWithArguments() {
            public void answer(
                    Context context, String ifname, IpClientCallbacks callback) {
                mIpClientCallback = callback;
                callback.onIpClientCreated(mIpClient);
            }
        }).when(facade).makeIpClient(any(), anyString(), any());

        return facade;
    }

    private WifiContext getContext() throws Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)).thenReturn(true);

        WifiContext context = mock(WifiContext.class);
        when(context.getPackageManager()).thenReturn(mPackageManager);

        MockContentResolver mockContentResolver = new MockContentResolver();
        mockContentResolver.addProvider(Settings.AUTHORITY,
                new MockContentProvider(context) {
                    @Override
                    public Bundle call(String method, String arg, Bundle extras) {
                        return new Bundle();
                    }
                });
        when(context.getContentResolver()).thenReturn(mockContentResolver);

        when(context.getSystemService(Context.POWER_SERVICE)).thenReturn(mPowerManager);
        when(context.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mPowerManager.isInteractive()).thenReturn(true);
        when(mPowerManager.newWakeLock(anyInt(), anyString())).thenReturn(
                mock(PowerManager.WakeLock.class));

        mAlarmManager = new TestAlarmManager();
        when(context.getSystemService(Context.ALARM_SERVICE)).thenReturn(
                mAlarmManager.getAlarmManager());

        when(context.getOpPackageName()).thenReturn(OP_PACKAGE_NAME);

        when(context.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);

        WifiP2pManager p2pm = mock(WifiP2pManager.class);
        when(context.getSystemService(WifiP2pManager.class)).thenReturn(p2pm);
        final CountDownLatch untilDone = new CountDownLatch(1);
        mP2pThread = new HandlerThread("WifiP2pMockThread") {
            @Override
            protected void onLooperPrepared() {
                untilDone.countDown();
            }
        };
        mP2pThread.start();
        untilDone.await();
        Handler handler = new Handler(mP2pThread.getLooper());
        when(p2pm.getP2pStateMachineMessenger()).thenReturn(new Messenger(handler));

        mUserAllContext = mock(Context.class);
        when(context.createContextAsUser(UserHandle.ALL, 0)).thenReturn(mUserAllContext);

        return context;
    }

    private MockResources getMockResources() {
        MockResources resources = new MockResources();
        return resources;
    }

    private IState getCurrentState() throws
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
        method.setAccessible(true);
        return (IState) method.invoke(mCmi);
    }

    private static HandlerThread getCmiHandlerThread(ClientModeImpl cmi) throws
            NoSuchFieldException, InvocationTargetException, IllegalAccessException {
        Field field = StateMachine.class.getDeclaredField("mSmThread");
        field.setAccessible(true);
        return (HandlerThread) field.get(cmi);
    }

    private static void stopLooper(final Looper looper) throws Exception {
        new Handler(looper).post(new Runnable() {
            @Override
            public void run() {
                looper.quitSafely();
            }
        });
    }

    private void dumpState() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        mCmi.dump(null, writer, null);
        writer.flush();
        Log.d(TAG, "ClientModeImpl state -" + stream.toString());
    }

    private static ScanDetail getGoogleGuestScanDetail(int rssi, String bssid, int freq) {
        ScanResult.InformationElement[] ie = new ScanResult.InformationElement[1];
        ie[0] = ScanResults.generateSsidIe(TEST_SSID);
        NetworkDetail nd = new NetworkDetail(TEST_BSSID_STR, ie, new ArrayList<String>(), sFreq);
        ScanDetail detail = new ScanDetail(nd, TEST_WIFI_SSID, bssid, "", rssi, freq,
                Long.MAX_VALUE, /* needed so that scan results aren't rejected because
                                   there older than scan start */
                ie, new ArrayList<String>(), ScanResults.generateIERawDatafromScanResultIE(ie));

        return detail;
    }

    private static ScanDetail getHiddenScanDetail(int rssi, String bssid, int freq) {
        ScanResult.InformationElement ie = new ScanResult.InformationElement();
        WifiSsid ssid = WifiSsid.fromBytes(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        ie.id = ScanResult.InformationElement.EID_SSID;
        ie.bytes = ssid.getBytes();
        ScanResult.InformationElement[] ies = new ScanResult.InformationElement[1];
        ies[0] = ie;
        NetworkDetail nd = new NetworkDetail(TEST_BSSID_STR, ies, new ArrayList<String>(), sFreq);
        ScanDetail detail = new ScanDetail(nd, ssid, bssid, "", rssi, freq,
                Long.MAX_VALUE, /* needed so that scan results aren't rejected because
                                   there older than scan start */
                ies, new ArrayList<String>(), ScanResults.generateIERawDatafromScanResultIE(ies));

        return detail;
    }

    private ArrayList<ScanDetail> getMockScanResults() {
        ScanResults sr = ScanResults.create(0, 2412, 2437, 2462, 5180, 5220, 5745, 5825);
        ArrayList<ScanDetail> list = sr.getScanDetailArrayList();

        list.add(getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        return list;
    }

    private void injectDhcpSuccess(DhcpResultsParcelable dhcpResults) {
        mIpClientCallback.onNewDhcpResults(dhcpResults);
        mIpClientCallback.onProvisioningSuccess(new LinkProperties());
    }

    private void injectDhcpSuccess() {
        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;
        injectDhcpSuccess(dhcpResults);
    }

    private void injectDhcpFailure() {
        mIpClientCallback.onNewDhcpResults((DhcpResultsParcelable) null);
        mIpClientCallback.onProvisioningFailure(new LinkProperties());
    }

    static final String   TEST_SSID = "\"GoogleGuest\"";
    static final String   SSID_NO_QUOTE = TEST_SSID.replace("\"", "");
    static final WifiSsid TEST_WIFI_SSID = WifiSsid.fromUtf8Text(SSID_NO_QUOTE);
    static final String   TEST_SSID1 = "\"RandomSsid1\"";
    static final String   SSID_NO_QUOTE1 = TEST_SSID1.replace("\"", "");
    static final WifiSsid TEST_WIFI_SSID1 = WifiSsid.fromUtf8Text(SSID_NO_QUOTE1);
    static final String   TEST_BSSID_STR = "01:02:03:04:05:06";
    static final String   TEST_BSSID_STR1 = "02:01:04:03:06:05";
    static final String   TEST_BSSID_STR2 = "02:01:04:03:06:04";
    static final int      sFreq = 2437;
    static final int      sFreq1 = 5240;
    static final String   WIFI_IFACE_NAME = "mockWlan";
    static final String sFilsSsid = "FILS-AP";
    static final ApfCapabilities APF_CAP = new ApfCapabilities(1, 2, 3);
    static final long TEST_TX_BYTES = 6666;
    static final long TEST_RX_BYTES = 8888;
    static final int TEST_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER = 1;
    static final int TEST_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER = 2;
    static final int TEST_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER = 9;
    static final int TEST_DUAL_STACK_NETWORK_MAX_DTIM_MULTIPLIER = 2;
    static final int TEST_CHANNEL = 6;
    static final int TEST_CHANNEL_1 = 44;

    ClientModeImpl mCmi;
    HandlerThread mWifiCoreThread;
    HandlerThread mP2pThread;
    HandlerThread mSyncThread;
    TestAlarmManager mAlarmManager;
    MockWifiMonitor mWifiMonitor;
    TestLooper mLooper;
    WifiContext mContext;
    Context mUserAllContext;
    MockResources mResources;
    FrameworkFacade mFrameworkFacade;
    IpClientCallbacks mIpClientCallback;
    OsuProvider mOsuProvider;
    WifiConfiguration mConnectedNetwork;
    WifiConfiguration mTestConfig;
    ExtendedWifiInfo mWifiInfo;
    WifiRoamingModeManager mWifiRoamingModeManager;
    ConnectionCapabilities mConnectionCapabilities = new ConnectionCapabilities();

    @Mock ActivityManager mActivityManager;
    @Mock WifiNetworkAgent mWifiNetworkAgent;
    @Mock SupplicantStateTracker mSupplicantStateTracker;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiNative mWifiNative;
    @Mock WifiScoreCard mWifiScoreCard;
    @Mock PerNetwork mPerNetwork;
    @Mock PerBssid mPerBssid;
    @Mock WifiScoreCard.NetworkConnectionStats mPerNetworkRecentStats;
    @Mock WifiHealthMonitor mWifiHealthMonitor;
    @Mock WifiTrafficPoller mWifiTrafficPoller;
    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock WifiStateTracker mWifiStateTracker;
    @Mock PasspointManager mPasspointManager;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock IIpClient mIpClient;
    @Mock TelephonyManager mTelephonyManager;
    @Mock TelephonyManager mDataTelephonyManager;
    @Mock WrongPasswordNotifier mWrongPasswordNotifier;
    @Mock Clock mClock;
    @Mock ScanDetailCache mScanDetailCache;
    @Mock WifiDiagnostics mWifiDiagnostics;
    @Mock IProvisioningCallback mProvisioningCallback;
    @Mock WakeupController mWakeupController;
    @Mock WifiDataStall mWifiDataStall;
    @Mock WifiNetworkFactory mWifiNetworkFactory;
    @Mock UntrustedWifiNetworkFactory mUntrustedWifiNetworkFactory;
    @Mock OemWifiNetworkFactory mOemWifiNetworkFactory;
    @Mock RestrictedWifiNetworkFactory mRestrictedWifiNetworkFactory;
    @Mock MultiInternetManager mMultiInternetManager;
    @Mock WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock LinkProbeManager mLinkProbeManager;
    @Mock PackageManager mPackageManager;
    @Mock WifiLockManager mWifiLockManager;
    @Mock AsyncChannel mNullAsyncChannel;
    @Mock BatteryStatsManager mBatteryStatsManager;
    @Mock MboOceController mMboOceController;
    @Mock ConnectionFailureNotifier mConnectionFailureNotifier;
    @Mock EapFailureNotifier mEapFailureNotifier;
    @Mock SimRequiredNotifier mSimRequiredNotifier;
    @Mock ThroughputPredictor mThroughputPredictor;
    @Mock ScanRequestProxy mScanRequestProxy;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock Network mNetwork;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock WifiScoreReport mWifiScoreReport;
    @Mock PowerManager mPowerManager;
    @Mock WifiP2pConnection mWifiP2pConnection;
    @Mock WifiGlobals mWifiGlobals;
    @Mock LinkProbeCallback mLinkProbeCallback;
    @Mock ClientModeImplMonitor mCmiMonitor;
    @Mock ClientModeManagerBroadcastQueue mBroadcastQueue;
    @Mock WifiNetworkSelector mWifiNetworkSelector;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock ClientModeManager mPrimaryClientModeManager;
    @Mock WifiSettingsConfigStore mSettingsConfigStore;
    @Mock Uri mMockUri;
    @Mock WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock WifiPseudonymManager mWifiPseudonymManager;
    @Mock WifiNotificationManager mWifiNotificationManager;

    @Mock WifiConnectivityHelper mWifiConnectivityHelper;
    @Mock InsecureEapNetworkHandler mInsecureEapNetworkHandler;
    @Mock ScanResult mScanResult;
    @Mock HandlerThread mWifiHandlerThread;
    @Mock SsidTranslator mSsidTranslator;
    @Mock ApplicationQosPolicyRequestHandler mApplicationQosPolicyRequestHandler;
    @Mock LocalLog mLocalLog;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    @Mock WifiCountryCode mWifiCountryCode;
    @Mock WifiRoamingConfigStore mWifiRoamingConfigStore;

    @Mock DeviceWiphyCapabilities mDeviceWiphyCapabilities;

    @Captor ArgumentCaptor<WifiConfigManager.OnNetworkUpdateListener> mConfigUpdateListenerCaptor;
    @Captor ArgumentCaptor<WifiNetworkAgent.Callback> mWifiNetworkAgentCallbackCaptor;
    @Captor ArgumentCaptor<WifiCarrierInfoManager.OnCarrierOffloadDisabledListener>
            mOffloadDisabledListenerArgumentCaptor = ArgumentCaptor.forClass(
                    WifiCarrierInfoManager.OnCarrierOffloadDisabledListener.class);
    @Captor ArgumentCaptor<BroadcastReceiver> mScreenStateBroadcastReceiverCaptor;

    @Captor
    ArgumentCaptor<WifiDeviceStateChangeManager.StateChangeCallback>
            mStateChangeCallbackArgumentCaptor;

    @Captor ArgumentCaptor<ProvisioningConfigurationParcelable> mProvisioningConfigurationCaptor;
    WifiInfo mPrimaryWifiInfo;

    private void setUpWifiNative() throws Exception {
        when(mWifiNative.getStaFactoryMacAddress(WIFI_IFACE_NAME)).thenReturn(
                TEST_GLOBAL_MAC_ADDRESS);
        doAnswer(new AnswerWithArguments() {
            public void answer(WifiSettingsConfigStore.Key<String> key, Object macAddress) {
                when(mSettingsConfigStore.get(WIFI_STA_FACTORY_MAC_ADDRESS))
                        .thenReturn((String) macAddress);
            }
        }).when(mSettingsConfigStore).put(eq(WIFI_STA_FACTORY_MAC_ADDRESS), any(String.class));
        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_GLOBAL_MAC_ADDRESS.toString());
        ConnectionCapabilities cap = new ConnectionCapabilities();
        cap.wifiStandard = ScanResult.WIFI_STANDARD_11AC;
        when(mWifiNative.getConnectionCapabilities(WIFI_IFACE_NAME))
                .thenReturn(mConnectionCapabilities);
        when(mWifiNative.setStaMacAddress(eq(WIFI_IFACE_NAME), anyObject()))
                .then(new AnswerWithArguments() {
                    public boolean answer(String iface, MacAddress mac) {
                        when(mWifiNative.getMacAddress(iface)).thenReturn(mac.toString());
                        return true;
                    }
                });
        when(mWifiNative.connectToNetwork(any(), any())).thenReturn(true);
        when(mWifiNative.getApfCapabilities(anyString())).thenReturn(APF_CAP);
        when(mWifiNative.isQosPolicyFeatureEnabled()).thenReturn(true);
    }

    /** Reset verify() counters on WifiNative, and restore when() mocks on mWifiNative */
    private void resetWifiNative() throws Exception {
        reset(mWifiNative);
        setUpWifiNative();
    }

    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "Setting up ...");

        // Ensure looper exists
        mLooper = new TestLooper();

        MockitoAnnotations.initMocks(this);

        /** uncomment this to enable logs from ClientModeImpls */
        // enableDebugLogs();
        mWifiMonitor = spy(new MockWifiMonitor());
        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mDataTelephonyManager);
        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Collections.emptySet());
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(Process.INVALID_UID, ""));
        setUpWifiNative();
        doAnswer(new AnswerWithArguments() {
            public MacAddress answer(
                    WifiConfiguration config, boolean isForSecondaryDbs) {
                MacAddress mac = config.getRandomizedMacAddress();
                if (isForSecondaryDbs) {
                    mac = MacAddressUtil.nextMacAddress(mac);
                }
                return mac;
            }
        }).when(mWifiConfigManager).getRandomizedMacAndUpdateIfNeeded(any(), anyBoolean());

        mTestNetworkParams = new TestNetworkParams();
        when(mWifiNetworkFactory.hasConnectionRequests()).thenReturn(true);
        when(mUntrustedWifiNetworkFactory.hasConnectionRequests()).thenReturn(true);
        when(mOemWifiNetworkFactory.hasConnectionRequests()).thenReturn(true);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(true);
        when(mApplicationQosPolicyRequestHandler.isFeatureEnabled()).thenReturn(true);
        when(mWifiInjector.getWifiHandlerLocalLog()).thenReturn(mLocalLog);
        when(mWifiInjector.getWifiCountryCode()).thenReturn(mWifiCountryCode);
        when(mWifiInjector.getApplicationQosPolicyRequestHandler())
                .thenReturn(mApplicationQosPolicyRequestHandler);

        mFrameworkFacade = getFrameworkFacade();
        mContext = getContext();
        mWifiInfo = new ExtendedWifiInfo(mWifiGlobals, WIFI_IFACE_NAME);
        mWifiRoamingModeManager = new WifiRoamingModeManager(mWifiNative,
                mActiveModeWarden, mWifiRoamingConfigStore);

        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(true);
        mResources = getMockResources();
        mResources.setIntArray(R.array.config_wifiRssiLevelThresholds,
                RssiUtilTest.RSSI_THRESHOLDS);
        mResources.setInteger(R.integer.config_wifiLinkBandwidthUpdateThresholdPercent, 25);
        mResources.setInteger(R.integer.config_wifiClientModeImplNumLogRecs, 100);
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        when(mContext.getResources()).thenReturn(mResources);

        when(mWifiGlobals.getPollRssiIntervalMillis()).thenReturn(3000);
        when(mWifiGlobals.getIpReachabilityDisconnectEnabled()).thenReturn(true);
        when(mWifiGlobals.getRepeatedNudFailuresThreshold()).thenReturn(Integer.MAX_VALUE);

        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.WIFI_FREQUENCY_BAND,
                WifiManager.WIFI_FREQUENCY_BAND_AUTO)).thenReturn(
                WifiManager.WIFI_FREQUENCY_BAND_AUTO);
        when(mFrameworkFacade.getTxBytes(eq(WIFI_IFACE_NAME))).thenReturn(TEST_TX_BYTES);
        when(mFrameworkFacade.getRxBytes(eq(WIFI_IFACE_NAME))).thenReturn(TEST_RX_BYTES);
        when(mFrameworkFacade.getTotalTxBytes()).thenReturn(TEST_TX_BYTES);
        when(mFrameworkFacade.getTotalRxBytes()).thenReturn(TEST_RX_BYTES);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        doAnswer(inv -> {
            mIpClientCallback.onQuit();
            return null;
        }).when(mIpClient).shutdown();
        when(mWifiNetworkAgent.getNetwork()).thenReturn(mNetwork);

        // static mocking
        mSession = ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT)
                .mockStatic(WifiInjector.class, withSettings().lenient())
                .spyStatic(MacAddress.class)
                .startMocking();
        when(WifiInjector.getInstance()).thenReturn(mWifiInjector);

        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_WPA3_SAE | WifiManager.WIFI_FEATURE_OWE);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.getWifiHandlerThread()).thenReturn(mWifiHandlerThread);
        when(mWifiInjector.getSsidTranslator()).thenReturn(mSsidTranslator);
        when(mWifiInjector.getWifiDeviceStateChangeManager())
                .thenReturn(mWifiDeviceStateChangeManager);
        when(mWifiHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        when(mWifiNative.getDeviceWiphyCapabilities(any())).thenReturn(mDeviceWiphyCapabilities);
        if (Flags.getDeviceCrossAkmRoamingSupport() && SdkLevel.isAtLeastV()) {
            when(mDeviceWiphyCapabilities.getMaxNumberAkms()).thenReturn(2);
        }
        when(mWifiGlobals.isOweUpgradeEnabled()).thenReturn(true);
        when(mWifiGlobals.getClientModeImplNumLogRecs()).thenReturn(100);
        when(mWifiGlobals.isSaveFactoryMacToConfigStoreEnabled()).thenReturn(true);
        when(mWifiGlobals.getNetworkNotFoundEventThreshold()).thenReturn(3);
        when(mWifiGlobals.getMacRandomizationUnsupportedSsidPrefixes()).thenReturn(
                Collections.EMPTY_SET);
        when(mWifiInjector.makeWifiNetworkAgent(any(), any(), any(), any(), any()))
                .thenAnswer(new AnswerWithArguments() {
                    public WifiNetworkAgent answer(
                            @NonNull NetworkCapabilities nc,
                            @NonNull LinkProperties linkProperties,
                            @NonNull NetworkAgentConfig naConfig,
                            @Nullable NetworkProvider provider,
                            @NonNull WifiNetworkAgent.Callback callback) {
                        when(mWifiNetworkAgent.getCallback()).thenReturn(callback);
                        return mWifiNetworkAgent;
                    }
                });
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        // Update the WifiInfo in WifiActiveModeWarden
        doAnswer(inv -> {
            if (mCmi != null) {
                mPrimaryWifiInfo = mCmi.getConnectionInfo();
            }
            return null;
        }).when(mActiveModeWarden).updateCurrentConnectionInfo();
        initializeCmi();
        // Retrieve factory MAC address on first bootup.
        verify(mWifiNative).getStaFactoryMacAddress(WIFI_IFACE_NAME);
        if (Flags.getDeviceCrossAkmRoamingSupport() && SdkLevel.isAtLeastV()) {
            verify(mWifiGlobals).setWpa3SaeUpgradeOffloadEnabled();
        }

        mOsuProvider = PasspointProvisioningTestUtil.generateOsuProvider(true);
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createOpenNetwork());
        when(mNullAsyncChannel.sendMessageSynchronously(any())).thenReturn(null);
        when(mWifiScoreCard.getL2KeyAndGroupHint(any())).thenReturn(new Pair<>(null, null));
        when(mDeviceConfigFacade.isAbnormalDisconnectionBugreportEnabled()).thenReturn(true);
        when(mDeviceConfigFacade.isAbnormalConnectionFailureBugreportEnabled()).thenReturn(true);
        when(mDeviceConfigFacade.isOverlappingConnectionBugreportEnabled()).thenReturn(true);
        when(mDeviceConfigFacade.getOverlappingConnectionDurationThresholdMs()).thenReturn(
                DeviceConfigFacade.DEFAULT_OVERLAPPING_CONNECTION_DURATION_THRESHOLD_MS);
        when(mWifiScoreCard.detectAbnormalConnectionFailure(anyString()))
                .thenReturn(WifiHealthMonitor.REASON_NO_FAILURE);
        when(mWifiScoreCard.detectAbnormalDisconnection(any()))
                .thenReturn(WifiHealthMonitor.REASON_NO_FAILURE);
        when(mPerNetwork.getRecentStats()).thenReturn(mPerNetworkRecentStats);
        when(mWifiScoreCard.lookupNetwork(any())).thenReturn(mPerNetwork);
        when(mWifiScoreCard.lookupBssid(any(), any())).thenReturn(mPerBssid);
        when(mThroughputPredictor.predictMaxTxThroughput(any())).thenReturn(90);
        when(mThroughputPredictor.predictMaxRxThroughput(any())).thenReturn(80);
        when(mWifiInjector.getWifiRoamingModeManager()).thenReturn(mWifiRoamingModeManager);

        doAnswer(new AnswerWithArguments() {
            public void answer(boolean shouldReduceNetworkScore) {
                mCmi.setShouldReduceNetworkScore(shouldReduceNetworkScore);
            }
        }).when(mClientModeManager).setShouldReduceNetworkScore(anyBoolean());
        doAnswer(new AnswerWithArguments() {
            public void answer(ClientModeManager manager, QueuedBroadcast broadcast) {
                broadcast.send();
            }
        }).when(mBroadcastQueue).queueOrSendBroadcast(any(), any());
    }

    private void validateConnectionInfo() {
        // The WifiInfo#equals returns false for pre-S, don't enforce the check.
        if (!SdkLevel.isAtLeastS()) return;
        if (mClientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
            assertEquals(mCmi.getConnectionInfo(), mPrimaryWifiInfo);
        }
    }

    private void initializeCmi() throws Exception {
        mCmi = new ClientModeImpl(mContext, mWifiMetrics, mClock, mWifiScoreCard, mWifiStateTracker,
                mWifiPermissionsUtil, mWifiConfigManager, mPasspointManager,
                mWifiMonitor, mWifiDiagnostics, mWifiDataStall,
                new ScoringParams(), new WifiThreadRunner(new Handler(mLooper.getLooper())),
                mWifiNetworkSuggestionsManager, mWifiHealthMonitor, mThroughputPredictor,
                mDeviceConfigFacade, mScanRequestProxy, mWifiInfo, mWifiConnectivityManager,
                mWifiBlocklistMonitor, mConnectionFailureNotifier,
                WifiInjector.REGULAR_NETWORK_CAPABILITIES_FILTER, mWifiNetworkFactory,
                mUntrustedWifiNetworkFactory, mOemWifiNetworkFactory, mRestrictedWifiNetworkFactory,
                mMultiInternetManager, mWifiLastResortWatchdog, mWakeupController,
                mWifiLockManager, mFrameworkFacade, mLooper.getLooper(),
                mWifiNative, mWrongPasswordNotifier, mWifiTrafficPoller, mLinkProbeManager,
                1, mBatteryStatsManager, mSupplicantStateTracker, mMboOceController,
                mWifiCarrierInfoManager, mWifiPseudonymManager, mEapFailureNotifier,
                mSimRequiredNotifier, mWifiScoreReport, mWifiP2pConnection, mWifiGlobals,
                WIFI_IFACE_NAME, mClientModeManager, mCmiMonitor,
                mBroadcastQueue, mWifiNetworkSelector, mTelephonyManager, mWifiInjector,
                mSettingsConfigStore, false, mWifiNotificationManager,
                mWifiConnectivityHelper);
        mCmi.mInsecureEapNetworkHandler = mInsecureEapNetworkHandler;

        mWifiCoreThread = getCmiHandlerThread(mCmi);

        mBinderToken = Binder.clearCallingIdentity();

        verify(mWifiConfigManager, atLeastOnce()).addOnNetworkUpdateListener(
                mConfigUpdateListenerCaptor.capture());
        assertNotNull(mConfigUpdateListenerCaptor.getValue());

        verify(mWifiCarrierInfoManager, atLeastOnce()).addOnCarrierOffloadDisabledListener(
                mOffloadDisabledListenerArgumentCaptor.capture());
        assertNotNull(mOffloadDisabledListenerArgumentCaptor.getValue());

        mCmi.enableVerboseLogging(true);
        mLooper.dispatchAll();

        verify(mWifiLastResortWatchdog, atLeastOnce()).clearAllFailureCounts();
        assertEquals("DisconnectedState", getCurrentState().getName());
        validateConnectionInfo();

        verify(mWifiDeviceStateChangeManager, atLeastOnce())
                .registerStateChangeCallback(mStateChangeCallbackArgumentCaptor.capture());
    }

    @After
    public void cleanUp() throws Exception {
        Binder.restoreCallingIdentity(mBinderToken);

        if (mSyncThread != null) stopLooper(mSyncThread.getLooper());
        if (mWifiCoreThread != null) stopLooper(mWifiCoreThread.getLooper());
        if (mP2pThread != null) stopLooper(mP2pThread.getLooper());

        mWifiCoreThread = null;
        mP2pThread = null;
        mSyncThread = null;
        mCmi = null;
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     *  Test that mode changes accurately reflect the value for isWifiEnabled.
     */
    @Test
    public void checkIsWifiEnabledForModeChanges() throws Exception {
        // now disable client mode and verify the reported wifi state
        mCmi.stop();
        mLooper.dispatchAll();
        verify(mContext, never())
                .sendStickyBroadcastAsUser(argThat(new WifiEnablingStateIntentMatcher()), any());
    }

    private static class WifiEnablingStateIntentMatcher implements ArgumentMatcher<Intent> {
        @Override
        public boolean matches(Intent intent) {
            return WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())
                    && WifiManager.WIFI_STATE_ENABLING
                            == intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                                    WifiManager.WIFI_STATE_DISABLED);
        }
    }

    private class NetworkStateChangedIntentMatcher implements ArgumentMatcher<Intent> {
        private final NetworkInfo.DetailedState mState;
        NetworkStateChangedIntentMatcher(NetworkInfo.DetailedState state) {
            mState = state;
        }
        @Override
        public boolean matches(Intent intent) {
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION != intent.getAction()) {
                // not the correct type
                return false;
            }
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            return networkInfo.getDetailedState() == mState;
        }
    }

    private void canSaveNetworkConfig() throws Exception {
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.saveNetwork(
                new NetworkUpdateResult(TEST_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();
    }

    /**
     * Verifies that configs can be saved when in client mode.
     */
    @Test
    public void canSaveNetworkConfigInClientMode() throws Exception {
        canSaveNetworkConfig();
    }

    /**
     * Verifies that admin restricted configs can be saved without triggering a connection.
     */
    @Test
    public void canSaveAdminRestrictedNetworkWithoutConnecting() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        mWifiInfo.setNetworkId(WifiConfiguration.INVALID_NETWORK_ID);
        IActionListener connectActionListener = mock(IActionListener.class);
        when(mWifiPermissionsUtil.isAdminRestrictedNetwork(any())).thenReturn(true);
        mCmi.saveNetwork(
                new NetworkUpdateResult(TEST_NETWORK_ID, STATUS_SUCCESS, false, false, true, false),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(connectActionListener).onSuccess();
        verify(mWifiPermissionsUtil).isAdminRestrictedNetwork(any());
        verify(mClientModeManager, never())
                .setShouldReduceNetworkScore(false);
    }

    /**
     * Verifies that deprecated security type configs can be saved without triggering a connection.
     */
    @Test
    public void canSaveDeprecatedSecurityTypeNetworkWithoutConnecting() throws Exception {
        mWifiInfo.setNetworkId(WifiConfiguration.INVALID_NETWORK_ID);
        IActionListener connectActionListener = mock(IActionListener.class);
        when(mWifiGlobals.isDeprecatedSecurityTypeNetwork(any())).thenReturn(true);
        mCmi.saveNetwork(
                new NetworkUpdateResult(TEST_NETWORK_ID, STATUS_SUCCESS, false, false, true, false),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(connectActionListener).onSuccess();
        verify(mClientModeManager, never())
                .setShouldReduceNetworkScore(false);
    }

    private WifiConfiguration createTestNetwork(boolean isHidden) {
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.SSID = TEST_SSID;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.hiddenSSID = isHidden;
        return config;
    }

    private void initializeMocksForAddedNetwork(WifiConfiguration config) throws Exception {
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(Arrays.asList(config));
        when(mWifiConfigManager.getConfiguredNetwork(0)).thenReturn(config);
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(0)).thenReturn(config);
    }

    private void initializeMocksForAddedNetwork(boolean isHidden) throws Exception {
        mTestConfig = createTestNetwork(isHidden);
        initializeMocksForAddedNetwork(mTestConfig);
    }

    private void initializeAndAddNetworkAndVerifySuccess(WifiConfiguration config)
            throws Exception {
        initializeMocksForAddedNetwork(config);
    }

    private void initializeAndAddNetworkAndVerifySuccess() throws Exception {
        initializeAndAddNetworkAndVerifySuccess(false);
    }

    private void initializeAndAddNetworkAndVerifySuccess(boolean isHidden) throws Exception {
        initializeMocksForAddedNetwork(isHidden);
    }

    private void setupAndStartConnectSequence(WifiConfiguration config) throws Exception {
        when(mWifiConfigManager.getConfiguredNetwork(eq(config.networkId)))
                .thenReturn(config);
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(
                eq(config.networkId))).thenReturn(config);

        verify(mWifiNative).removeAllNetworks(WIFI_IFACE_NAME);

        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(config.networkId),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, mAttributionTagForConnect);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();
    }

    private void validateSuccessfulConnectSequence(WifiConfiguration config) {
        verify(mWifiConnectivityManager).prepareForForcedConnection(eq(config.networkId));
        verify(mWifiConfigManager).getConfiguredNetworkWithoutMasking(eq(config.networkId));
        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
        verify(mCmiMonitor).onConnectionStart(mClientModeManager);
        assertEquals("L2ConnectingState", mCmi.getCurrentState().getName());
    }

    private void validateFailureConnectSequence(WifiConfiguration config) {
        verify(mWifiConnectivityManager).prepareForForcedConnection(eq(config.networkId));
        verify(mWifiConfigManager, never())
                .getConfiguredNetworkWithoutMasking(eq(config.networkId));
        verify(mWifiNative, never()).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
    }

    /**
     * Tests the network connection initiation sequence with the default network request pending
     * from WifiNetworkFactory.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnect() throws Exception {
        WifiConfiguration config = mConnectedNetwork;
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.setRandomizedMacAddress(TEST_LOCAL_MAC_ADDRESS);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.getNetworkSelectionStatus().setHasEverConnected(mTestNetworkParams.hasEverConnected);
        assertEquals(null, config.getNetworkSelectionStatus().getCandidateSecurityParams());
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
        assertEquals(config.getSecurityParamsList().stream()
                        .filter(WifiConfigurationUtil::isSecurityParamsValid)
                        .findFirst().orElse(null),
                config.getNetworkSelectionStatus().getCandidateSecurityParams());
        mConnectedNetwork.getNetworkSelectionStatus().setLastUsedSecurityParams(
                config.getNetworkSelectionStatus().getCandidateSecurityParams());
    }

    /**
     * Tests the manual connection request will run network selection to find
     * a proper security params, but not use the default one.
     */
    @Test
    public void triggerConnectWithUpgradeType() throws Exception {
        String ssid = "TestOpenOweSsid";
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createOpenOweNetwork(
                ScanResultUtil.createQuotedSsid(ssid)));
        doAnswer(new AnswerWithArguments() {
            public WifiConfiguration answer(List<WifiCandidates.Candidate> candidates) {
                config.getNetworkSelectionStatus().setCandidateSecurityParams(
                        SecurityParams.createSecurityParamsBySecurityType(
                                WifiConfiguration.SECURITY_TYPE_OWE));
                return config;
            }
        }).when(mWifiNetworkSelector).selectNetwork(any());
        String caps = "[RSN-OWE_TRANSITION]";
        ScanResult scanResult = new ScanResult.Builder(WifiSsid.fromUtf8Text(ssid),
                TEST_BSSID_STR)
                .setHessid(1245)
                .setCaps(caps)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResult.InformationElement ie = createIE(ScanResult.InformationElement.EID_SSID,
                ssid.getBytes(StandardCharsets.UTF_8));
        scanResult.informationElements = new ScanResult.InformationElement[]{ie};
        when(mScanRequestProxy.getScanResults()).thenReturn(Arrays.asList(scanResult));

        config.networkId = FRAMEWORK_NETWORK_ID;
        config.setRandomizedMacAddress(TEST_LOCAL_MAC_ADDRESS);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.getNetworkSelectionStatus().setHasEverConnected(mTestNetworkParams.hasEverConnected);
        assertEquals(null, config.getNetworkSelectionStatus().getCandidateSecurityParams());

        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
        assertEquals(WifiConfiguration.SECURITY_TYPE_OWE,
                config.getNetworkSelectionStatus().getCandidateSecurityParams().getSecurityType());
    }

    /**
     * Tests the manual connection request will use the last used security params when there
     * is no scan result.
     */
    @Test
    public void triggerConnectWithLastUsedSecurityParams() throws Exception {
        String ssid = "TestOpenOweSsid";
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createOpenOweNetwork(
                ScanResultUtil.createQuotedSsid(ssid)));
        when(mScanRequestProxy.getScanResults()).thenReturn(new ArrayList<>());

        config.networkId = FRAMEWORK_NETWORK_ID;
        config.setRandomizedMacAddress(TEST_LOCAL_MAC_ADDRESS);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.getNetworkSelectionStatus().setHasEverConnected(mTestNetworkParams.hasEverConnected);
        config.getNetworkSelectionStatus().setLastUsedSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_OWE));
        assertEquals(null, config.getNetworkSelectionStatus().getCandidateSecurityParams());

        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
        assertEquals(WifiConfiguration.SECURITY_TYPE_OWE,
                config.getNetworkSelectionStatus().getCandidateSecurityParams().getSecurityType());
    }

    /**
     * Tests the network connection initiation sequence with the default network request pending
     * from WifiNetworkFactory.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectFromNonSettingsApp() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID;
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(Process.myUid()))
                .thenReturn(false);
        setupAndStartConnectSequence(config);
        verify(mWifiConnectivityManager).prepareForForcedConnection(eq(config.networkId));
        verify(mWifiConfigManager).getConfiguredNetworkWithoutMasking(eq(config.networkId));
        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
    }

    /**
     * Tests the network connection initiation sequence with no network request pending from
     * from WifiNetworkFactory.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we don't invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectWithNoNetworkRequest() throws Exception {
        // Remove the network requests.
        when(mWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mUntrustedWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mOemWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID;
        setupAndStartConnectSequence(config);
        validateFailureConnectSequence(config);
    }

    /**
     * Tests the entire successful network connection flow.
     */
    @Test
    public void testConnect() throws Exception {
        connect(null);
    }

    private void connect() throws Exception {
        connect(null);
    }

    /**
     * Simulate a connect
     *
     * @param wnmDataForTermsAndConditions Use null unless it is required to simulate a terms and
     *                                     conditions acceptance notification from Passpoint
     * @throws Exception
     */
    private void connect(WnmData wnmDataForTermsAndConditions) throws Exception {
        assertNull(mCmi.getConnectingWifiConfiguration());
        assertNull(mCmi.getConnectedWifiConfiguration());

        triggerConnect();
        validateConnectionInfo();

        assertNotNull(mCmi.getConnectingWifiConfiguration());
        assertNull(mCmi.getConnectedWifiConfiguration());

        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID);
        config.carrierId = CARRIER_ID_1;
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);

        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        ScanResult scanResult = new ScanResult.Builder(WifiSsid.fromUtf8Text(sFilsSsid),
                TEST_BSSID_STR)
                .setHessid(1245)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResult.InformationElement ie = createIE(ScanResult.InformationElement.EID_SSID,
                sFilsSsid.getBytes(StandardCharsets.UTF_8));
        scanResult.informationElements = new ScanResult.InformationElement[]{ie};
        when(mScanRequestProxy.getScanResult(eq(TEST_BSSID_STR))).thenReturn(scanResult);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        validateConnectionInfo();

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        // WifiInfo should be updated if it is primary
        validateConnectionInfo();

        verify(mWifiMetrics).noteFirstL2ConnectionAfterBoot(true);

        // L2 connected, but not L3 connected yet. So, still "Connecting"...
        assertNotNull(mCmi.getConnectingWifiConfiguration());
        assertNull(mCmi.getConnectedWifiConfiguration());

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();
        validateConnectionInfo();

        assertEquals("L3ProvisioningState", getCurrentState().getName());
        verifyNetworkStateChangedBroadcast(times(1),
                new NetworkStateChangedIntentMatcher(CONNECTING));
        verifyNetworkStateChangedBroadcast(times(1),
                new NetworkStateChangedIntentMatcher(OBTAINING_IPADDR));

        if (wnmDataForTermsAndConditions != null) {
            mCmi.sendMessage(WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
                    0, 0, wnmDataForTermsAndConditions);
            mLooper.dispatchAll();
        }

        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        assertNull(mCmi.getConnectingWifiConfiguration());
        assertNotNull(mCmi.getConnectedWifiConfiguration());

        // Verify WifiMetrics logging for metered metrics based on DHCP results
        verify(mWifiMetrics).addMeteredStat(any(), anyBoolean());
        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertEquals(TEST_BSSID_STR, wifiInfo.getBSSID());
        assertEquals(sFreq, wifiInfo.getFrequency());
        assertEquals(TEST_WIFI_SSID, wifiInfo.getWifiSsid());
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, wifiInfo.getMacAddress());
        assertEquals(mConnectedNetwork.getDefaultSecurityParams().getSecurityType(),
                mWifiInfo.getCurrentSecurityType());
        if (wifiInfo.isPasspointAp()) {
            assertEquals(wifiInfo.getPasspointProviderFriendlyName(),
                    WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME);
        } else {
            assertNull(wifiInfo.getPasspointProviderFriendlyName());
        }
        assertEquals(Arrays.asList(scanResult.informationElements),
                    wifiInfo.getInformationElements());
        assertNotNull(wifiInfo.getNetworkKey());
        expectRegisterNetworkAgent((na) -> {
            if (!mConnectedNetwork.carrierMerged) {
                assertNull(na.subscriberId);
            }
        }, (nc) -> {
                if (SdkLevel.isAtLeastS()) {
                    WifiInfo wifiInfoFromTi = (WifiInfo) nc.getTransportInfo();
                    assertEquals(TEST_BSSID_STR, wifiInfoFromTi.getBSSID());
                    assertEquals(sFreq, wifiInfoFromTi.getFrequency());
                    assertEquals(TEST_WIFI_SSID, wifiInfoFromTi.getWifiSsid());
                    if (wifiInfo.isPasspointAp()) {
                        assertEquals(wifiInfoFromTi.getPasspointProviderFriendlyName(),
                                WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME);
                    } else {
                        assertNull(wifiInfoFromTi.getPasspointProviderFriendlyName());
                    }
                }
            });

        assertFalse(mCmi.isIpProvisioningTimedOut());
        // Ensure the connection stats for the network is updated.
        verify(mWifiConfigManager).updateNetworkAfterConnect(eq(FRAMEWORK_NETWORK_ID),
                anyBoolean(), anyBoolean(), anyInt());
        verify(mWifiConfigManager).updateRandomizedMacExpireTime(any(), anyLong());
        verifyNetworkStateChangedBroadcast(times(1),
                new NetworkStateChangedIntentMatcher(CONNECTED));

        // Anonymous Identity is not set.
        assertEquals("", mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        verify(mWifiStateTracker).updateState(WIFI_IFACE_NAME, WifiStateTracker.CONNECTED);
        assertEquals("L3ConnectedState", getCurrentState().getName());
        verify(mWifiMetrics).incrementNumOfCarrierWifiConnectionSuccess();
        verify(mWifiLockManager).updateWifiClientConnected(mClientModeManager, true);
        verify(mWifiNative).getConnectionCapabilities(any());
        verify(mThroughputPredictor).predictMaxTxThroughput(any());
        verify(mWifiMetrics).setConnectionMaxSupportedLinkSpeedMbps(WIFI_IFACE_NAME, 90, 80);
        assertEquals(90, wifiInfo.getMaxSupportedTxLinkSpeedMbps());
        verify(mWifiMetrics).noteFirstL3ConnectionAfterBoot(true);
        validateConnectionInfo();
    }

    private void connectWithIpProvisionTimeout(boolean lateDhcpResponse) throws Exception {
        mResources.setBoolean(R.bool.config_wifiRemainConnectedAfterIpProvisionTimeout, true);
        assertNull(mCmi.getConnectingWifiConfiguration());
        assertNull(mCmi.getConnectedWifiConfiguration());

        triggerConnect();
        validateConnectionInfo();

        assertNotNull(mCmi.getConnectingWifiConfiguration());
        assertNull(mCmi.getConnectedWifiConfiguration());

        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID);
        config.carrierId = CARRIER_ID_1;
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);

        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        ScanResult scanResult = new ScanResult.Builder(WifiSsid.fromUtf8Text(sFilsSsid),
                TEST_BSSID_STR)
                .setHessid(1245)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResult.InformationElement ie = createIE(ScanResult.InformationElement.EID_SSID,
                sFilsSsid.getBytes(StandardCharsets.UTF_8));
        scanResult.informationElements = new ScanResult.InformationElement[]{ie};
        when(mScanRequestProxy.getScanResult(eq(TEST_BSSID_STR))).thenReturn(scanResult);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        validateConnectionInfo();

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        // WifiInfo should be updated if it is primary
        validateConnectionInfo();

        verify(mWifiMetrics).noteFirstL2ConnectionAfterBoot(true);

        // L2 connected, but not L3 connected yet. So, still "Connecting"...
        assertNotNull(mCmi.getConnectingWifiConfiguration());
        assertNull(mCmi.getConnectedWifiConfiguration());

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();
        validateConnectionInfo();

        assertEquals("L3ProvisioningState", getCurrentState().getName());
        verifyNetworkStateChangedBroadcast(times(1),
                new NetworkStateChangedIntentMatcher(CONNECTING));
        verifyNetworkStateChangedBroadcast(times(1),
                new NetworkStateChangedIntentMatcher(OBTAINING_IPADDR));
        mLooper.dispatchAll();

        mLooper.moveTimeForward(mCmi.WAIT_FOR_L3_PROVISIONING_TIMEOUT_MS);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).handleConnectionStateChanged(mClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        assertTrue(mCmi.isIpProvisioningTimedOut());
        verify(mWifiConfigManager).setIpProvisioningTimedOut(eq(FRAMEWORK_NETWORK_ID), eq(true));

        if (!lateDhcpResponse) {
            return;
        }

        // Simulate the scenario of a late response of the dhcp after the network is labeled a local
        // only due to ip provisioning timeout.

        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();
        assertNull(mCmi.getConnectingWifiConfiguration());
        assertNotNull(mCmi.getConnectedWifiConfiguration());

        // Verify WifiMetrics logging for metered metrics based on DHCP results
        verify(mWifiMetrics).addMeteredStat(any(), anyBoolean());
        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertEquals(TEST_BSSID_STR, wifiInfo.getBSSID());
        assertEquals(sFreq, wifiInfo.getFrequency());
        assertEquals(TEST_WIFI_SSID, wifiInfo.getWifiSsid());
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, wifiInfo.getMacAddress());
        assertEquals(mConnectedNetwork.getDefaultSecurityParams().getSecurityType(),
                mWifiInfo.getCurrentSecurityType());

        if (wifiInfo.isPasspointAp()) {
            assertEquals(wifiInfo.getPasspointProviderFriendlyName(),
                    WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME);
        } else {
            assertNull(wifiInfo.getPasspointProviderFriendlyName());
        }
        assertEquals(Arrays.asList(scanResult.informationElements),
                wifiInfo.getInformationElements());
        assertNotNull(wifiInfo.getNetworkKey());
        expectRegisterNetworkAgent((na) -> {
            if (!mConnectedNetwork.carrierMerged) {
                assertNull(na.subscriberId);
            }
        }, (nc) -> {
                if (SdkLevel.isAtLeastS()) {
                    WifiInfo wifiInfoFromTi = (WifiInfo) nc.getTransportInfo();
                    assertEquals(TEST_BSSID_STR, wifiInfoFromTi.getBSSID());
                    assertEquals(sFreq, wifiInfoFromTi.getFrequency());
                    assertEquals(TEST_WIFI_SSID, wifiInfoFromTi.getWifiSsid());
                    if (wifiInfo.isPasspointAp()) {
                        assertEquals(wifiInfoFromTi.getPasspointProviderFriendlyName(),
                                WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME);
                    } else {
                        assertNull(wifiInfoFromTi.getPasspointProviderFriendlyName());
                    }
                }
            });

        assertFalse(mCmi.isIpProvisioningTimedOut());
        verify(mWifiConfigManager).setIpProvisioningTimedOut(eq(FRAMEWORK_NETWORK_ID), eq(false));

        // Ensure the connection stats for the network is updated.
        verify(mWifiConfigManager).updateRandomizedMacExpireTime(any(), anyLong());
        verifyNetworkStateChangedBroadcast(atLeast(1),
                new NetworkStateChangedIntentMatcher(CONNECTED));

        //Ensure that the network is registered
        verify(mWifiConfigManager).updateNetworkAfterConnect(
                eq(FRAMEWORK_NETWORK_ID), anyBoolean(), anyBoolean(), anyInt());
        verify(mWifiConnectivityManager, times(2)).handleConnectionStateChanged(mClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);


        // Anonymous Identity is not set.
        assertEquals("", mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        verify(mWifiStateTracker).updateState(WIFI_IFACE_NAME, WifiStateTracker.CONNECTED);
        assertEquals("L3ConnectedState", getCurrentState().getName());
        verify(mWifiMetrics).incrementNumOfCarrierWifiConnectionSuccess();
        verify(mWifiLockManager).updateWifiClientConnected(mClientModeManager, true);
        verify(mWifiNative).getConnectionCapabilities(any());
        verify(mThroughputPredictor).predictMaxTxThroughput(any());
        verify(mWifiMetrics).setConnectionMaxSupportedLinkSpeedMbps(WIFI_IFACE_NAME, 90, 80);
        assertEquals(90, wifiInfo.getMaxSupportedTxLinkSpeedMbps());
        verify(mWifiMetrics).noteFirstL3ConnectionAfterBoot(true);
        validateConnectionInfo();

    }

    @Test
    public void testConnectWithIpProvisioningTimeout() throws Exception {
        connectWithIpProvisionTimeout(false);
    }

    @Test
    public void testConnectWithIpProvisioningTimeoutLateDhcpResponse() throws Exception {
        connectWithIpProvisionTimeout(true);
    }

    private void verifyNetworkStateChangedBroadcast(VerificationMode mode,
            ArgumentCaptor<Intent> intentCaptor) {
        if (SdkLevel.isAtLeastU()) {
            verify(mUserAllContext, mode).sendStickyBroadcast(intentCaptor.capture(), any());
        } else {
            verify(mContext, mode).sendStickyBroadcastAsUser(intentCaptor.capture(), any());
        }
    }

    private void verifyNetworkStateChangedBroadcast(VerificationMode mode,
            ArgumentMatcher<Intent> intentMatcher) {
        if (SdkLevel.isAtLeastU()) {
            verify(mUserAllContext, mode).sendStickyBroadcast(argThat(intentMatcher), any());
        } else {
            verify(mContext, mode).sendStickyBroadcastAsUser(argThat(intentMatcher), any());
        }
    }

    private void inOrderVerifyNetworkStateChangedBroadcasts(
            ArgumentMatcher<Intent>... intentMatchers) {
        final InOrder inOrder = SdkLevel.isAtLeastU()
                ? inOrder(mUserAllContext) : inOrder(mContext);
        for (ArgumentMatcher<Intent> intentMatcher : intentMatchers) {
            if (SdkLevel.isAtLeastU()) {
                inOrder.verify(mUserAllContext).sendStickyBroadcast(argThat(intentMatcher), any());
            } else {
                inOrder.verify(mContext).sendStickyBroadcastAsUser(argThat(intentMatcher), any());
            }
        }
    }

    private void setupEapSimConnection() throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.carrierId = CARRIER_ID_1;
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");

        triggerConnect();

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    @Test
    public void testUpdatingOobPseudonymToSupplicant() throws Exception {
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(true);

        ArgumentCaptor<WifiPseudonymManager.PseudonymUpdatingListener> listenerCaptor =
                ArgumentCaptor.forClass(WifiPseudonymManager.PseudonymUpdatingListener.class);
        setupEapSimConnection();
        verify(mWifiPseudonymManager).registerPseudonymUpdatingListener(listenerCaptor.capture());
        String expectedPseudonym = "abc-123";
        String expectedDecoratedPseudonym = "abc-123@wlan.mnc456.mcc123.3gppnetwork.org";
        when(mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(any(), eq(expectedPseudonym)))
                .thenReturn(expectedDecoratedPseudonym);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(true);
        listenerCaptor.getValue().onUpdated(CARRIER_ID_1, expectedPseudonym);
        mLooper.dispatchAll();

        verify(mWifiNative).setEapAnonymousIdentity(
                anyString(), eq(expectedDecoratedPseudonym), eq(true));

        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiPseudonymManager)
                .unregisterPseudonymUpdatingListener(eq(listenerCaptor.getValue()));
    }

    @Test
    public void testNotUpdatingOobPseudonymToSupplicant() throws Exception {
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(true);

        ArgumentCaptor<WifiPseudonymManager.PseudonymUpdatingListener> listenerCaptor =
                ArgumentCaptor.forClass(WifiPseudonymManager.PseudonymUpdatingListener.class);
        setupEapSimConnection();
        verify(mWifiPseudonymManager).registerPseudonymUpdatingListener(listenerCaptor.capture());
        String expectedPseudonym = "abc-123";
        String expectedDecoratedPseudonym = "abc-123@wlan.mnc456.mcc123.3gppnetwork.org";
        when(mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(any(), eq(expectedPseudonym)))
                .thenReturn(expectedDecoratedPseudonym);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(false);
        listenerCaptor.getValue().onUpdated(CARRIER_ID_1, expectedPseudonym);
        mLooper.dispatchAll();

        verify(mWifiNative, never()).setEapAnonymousIdentity(
                anyString(), anyString(), anyBoolean());

        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiPseudonymManager)
                .unregisterPseudonymUpdatingListener(eq(listenerCaptor.getValue()));
    }

    /**
     * Test when a roam occurs simultaneously with another connection attempt.
     * The roam's NETWORK_CONNECTION_EVENT should be ignored, only the new network's
     * NETWORK_CONNECTION_EVENT should be acted upon.
     */
    @Test
    public void roamRaceWithConnect() throws Exception {
        connect();

        initializeAndAddNetworkAndVerifySuccess();

        // connect to new network
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = OTHER_NETWORK_ID;
        setupAndStartConnectSequence(config);

        // in L2ConnectingState
        assertEquals("L2ConnectingState", getCurrentState().getName());

        // send NETWORK_CONNECTION_EVENT for previous network ID
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(
                        FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        // should ignore it, stay in L2ConnectingState
        assertEquals("L2ConnectingState", getCurrentState().getName());

        // send expected new network SSID
        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(config.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(
                        OTHER_NETWORK_ID, wifiSsid, TEST_BSSID_STR1, false, null));
        mLooper.dispatchAll();

        // then move to next state
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    @Test
    public void testSimAuthRequestIsHandledWhileAlreadyConnectedSuccess() throws Exception {
        connect();

        WifiCarrierInfoManager.SimAuthRequestData requestData =
                new WifiCarrierInfoManager.SimAuthRequestData();
        requestData.protocol = WifiEnterpriseConfig.Eap.SIM;
        requestData.networkId = FRAMEWORK_NETWORK_ID;
        String testSimAuthResponse = "TEST_SIM_AUTH_RESPONSE";
        when(mWifiCarrierInfoManager.getGsmSimAuthResponse(any(), any()))
                .thenReturn(testSimAuthResponse);
        mCmi.sendMessage(WifiMonitor.SUP_REQUEST_SIM_AUTH, requestData);
        mLooper.dispatchAll();

        // Expect success
        verify(mWifiNative).simAuthResponse(WIFI_IFACE_NAME, WifiNative.SIM_AUTH_RESP_TYPE_GSM_AUTH,
                testSimAuthResponse);
    }

    @Test
    public void testSimAuthRequestIsHandledWhileAlreadyConnectedFail() throws Exception {
        connect();

        WifiCarrierInfoManager.SimAuthRequestData requestData =
                new WifiCarrierInfoManager.SimAuthRequestData();
        requestData.protocol = WifiEnterpriseConfig.Eap.SIM;
        requestData.networkId = FRAMEWORK_NETWORK_ID;
        // Mock WifiCarrierInfoManager to return null so that sim auth fails.
        when(mWifiCarrierInfoManager.getGsmSimAuthResponse(any(), any())).thenReturn(null);
        mCmi.sendMessage(WifiMonitor.SUP_REQUEST_SIM_AUTH, requestData);
        mLooper.dispatchAll();

        // Expect failure
        verify(mWifiNative).simAuthFailedResponse(WIFI_IFACE_NAME);
    }

    /**
     * When the SIM card was removed, if the current wifi connection is not using it, the connection
     * should be kept.
     */
    @Test
    public void testResetSimWhenNonConnectedSimRemoved() throws Exception {
        setupEapSimConnection();
        doReturn(true).when(mWifiCarrierInfoManager).isSimReady(eq(DATA_SUBID));
        mCmi.sendMessage(ClientModeImpl.CMD_RESET_SIM_NETWORKS,
                ClientModeImpl.RESET_SIM_REASON_SIM_REMOVED);
        mLooper.dispatchAll();

        verify(mSimRequiredNotifier, never()).showSimRequiredNotification(any(), any());
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    /**
     * When the SIM card was removed, if the current wifi connection is using it, the connection
     * should be disconnected, otherwise, the connection shouldn't be impacted.
     */
    @Test
    public void testResetSimWhenConnectedSimRemoved() throws Exception {
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        setupEapSimConnection();
        doReturn(false).when(mWifiCarrierInfoManager).isSimReady(eq(DATA_SUBID));
        mCmi.sendMessage(ClientModeImpl.CMD_RESET_SIM_NETWORKS,
                ClientModeImpl.RESET_SIM_REASON_SIM_REMOVED);
        mLooper.dispatchAll();

        verify(mSimRequiredNotifier).showSimRequiredNotification(any(), any());
        verify(mWifiNative, times(2)).removeAllNetworks(WIFI_IFACE_NAME);
        verify(mWifiMetrics).startConnectionEvent(
                anyString(), any(), anyString(), anyInt(), eq(true), anyInt(), anyInt());
    }

    /**
     * When the SIM card was removed, if the current wifi connection is using it, the connection
     * should be disconnected, otherwise, the connection shouldn't be impacted.
     */
    @Test
    public void testResetSimWhenConnectedSimRemovedAfterNetworkRemoval() throws Exception {
        setupEapSimConnection();
        doReturn(false).when(mWifiCarrierInfoManager).isSimReady(eq(DATA_SUBID));
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(null);
        mCmi.sendMessage(ClientModeImpl.CMD_RESET_SIM_NETWORKS,
                ClientModeImpl.RESET_SIM_REASON_SIM_REMOVED);
        mLooper.dispatchAll();

        verify(mSimRequiredNotifier, never()).showSimRequiredNotification(any(), any());
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    /**
     * When the default data SIM is changed, if the current wifi connection is carrier wifi,
     * the connection should be disconnected.
     */
    @Test
    public void testResetSimWhenDefaultDataSimChanged() throws Exception {
        setupEapSimConnection();
        mCmi.sendMessage(ClientModeImpl.CMD_RESET_SIM_NETWORKS,
                ClientModeImpl.RESET_SIM_REASON_DEFAULT_DATA_SIM_CHANGED);
        mLooper.dispatchAll();

        verify(mWifiNative, times(2)).removeAllNetworks(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_RESET_SIM_NETWORKS));
        verify(mSimRequiredNotifier, never()).showSimRequiredNotification(any(), anyString());
    }

    /**
     * Tests anonymous identity is set again whenever a connection is established for the carrier
     * that supports encrypted IMSI and anonymous identity and no real pseudonym was provided.
     */
    @Test
    public void testSetAnonymousIdentityWhenConnectionIsEstablishedNoPseudonym() throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");
        String expectedAnonymousIdentity = "anonymous@wlan.mnc456.mcc123.3gppnetwork.org";

        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(any()))
                .thenReturn(expectedAnonymousIdentity);

        // Initial value should be "not set"
        assertEquals("", mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        triggerConnect();

        // CMD_START_CONNECT should have set anonymousIdentity to anonymous@<realm>
        assertEquals(expectedAnonymousIdentity,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mWifiNative.getEapAnonymousIdentity(anyString()))
                .thenReturn(expectedAnonymousIdentity);

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        verify(mWifiNative).getEapAnonymousIdentity(any());

        // Post connection value should remain "not set"
        assertEquals("", mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        // verify that WifiConfigManager#addOrUpdateNetwork() was called to clear any previously
        // stored pseudonym. i.e. to enable Encrypted IMSI for subsequent connections.
        // Note: This test will fail if future logic will have additional conditions that would
        // trigger "add or update network" operation. The test needs to be updated to account for
        // this change.
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
    }

    /**
     * Tests anonymous identity is set again whenever a connection is established for the carrier
     * that supports encrypted IMSI and anonymous identity but real pseudonym was provided for
     * subsequent connections.
     */
    @Test
    public void testSetAnonymousIdentityWhenConnectionIsEstablishedWithPseudonym()
            throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");
        String expectedAnonymousIdentity = "anonymous@wlan.mnc456.mcc123.3gppnetwork.org";
        String pseudonym = "83bcca9384fca@wlan.mnc456.mcc123.3gppnetwork.org";

        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(any()))
                .thenReturn(expectedAnonymousIdentity);

        triggerConnect();

        // CMD_START_CONNECT should have set anonymousIdentity to anonymous@<realm>
        assertEquals(expectedAnonymousIdentity,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mWifiNative.getEapAnonymousIdentity(anyString()))
                .thenReturn(pseudonym);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(true);

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        verify(mWifiNative).getEapAnonymousIdentity(any());
        // No decorated pseudonum, only update the cached data.
        verify(mWifiNative).setEapAnonymousIdentity(any(), eq(pseudonym), eq(false));
        assertEquals(pseudonym,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        // Verify that WifiConfigManager#addOrUpdateNetwork() was called if there we received a
        // real pseudonym to be stored. i.e. Encrypted IMSI will be used once, followed by
        // pseudonym usage in all subsequent connections.
        // Note: This test will fail if future logic will have additional conditions that would
        // trigger "add or update network" operation. The test needs to be updated to account for
        // this change.
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
    }

    /**
     * Tests anonymous identity is set again whenever a connection is established for the carrier
     * that supports encrypted IMSI and anonymous identity but real but not decorated pseudonym was
     * provided for subsequent connections.
     */
    @Test
    public void testSetAnonymousIdentityWhenConnectionIsEstablishedWithNonDecoratedPseudonym()
            throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");
        String pseudonym = "83bcca9384fca";
        String realm = "wlan.mnc456.mcc123.3gppnetwork.org";
        String expectedAnonymousIdentity = "anonymous@wlan.mnc456.mcc123.3gppnetwork.org";

        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(any()))
                .thenReturn(expectedAnonymousIdentity);
        doAnswer(invocation -> { return invocation.getArgument(1) + "@" + realm; })
                .when(mWifiCarrierInfoManager).decoratePseudonymWith3GppRealm(any(), anyString());
        triggerConnect();

        // CMD_START_CONNECT should have set anonymousIdentity to anonymous@<realm>
        assertEquals(expectedAnonymousIdentity,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mWifiNative.getEapAnonymousIdentity(anyString()))
                .thenReturn(pseudonym);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(true);

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        verify(mWifiNative).getEapAnonymousIdentity(any());
        verify(mWifiNative).setEapAnonymousIdentity(any(), eq(pseudonym + "@" + realm),
                eq(true));
        assertEquals(pseudonym + "@" + realm,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        // Verify that WifiConfigManager#addOrUpdateNetwork() was called if there we received a
        // real pseudonym to be stored. i.e. Encrypted IMSI will be used once, followed by
        // pseudonym usage in all subsequent connections.
        // Note: This test will fail if future logic will have additional conditions that would
        // trigger "add or update network" operation. The test needs to be updated to account for
        // this change.
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
    }

    /**
     * Tests anonymous identity will be set to suggestion network.
     */
    @Test
    public void testSetAnonymousIdentityWhenConnectionIsEstablishedWithPseudonymForSuggestion()
            throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");
        mConnectedNetwork.fromWifiNetworkSuggestion = true;
        String expectedAnonymousIdentity = "anonymous@wlan.mnc456.mcc123.3gppnetwork.org";
        String pseudonym = "83bcca9384fca@wlan.mnc456.mcc123.3gppnetwork.org";

        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(any()))
                .thenReturn(expectedAnonymousIdentity);

        triggerConnect();

        // CMD_START_CONNECT should have set anonymousIdentity to anonymous@<realm>
        assertEquals(expectedAnonymousIdentity,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mWifiNative.getEapAnonymousIdentity(anyString()))
                .thenReturn(pseudonym);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(true);

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        verify(mWifiNative).getEapAnonymousIdentity(any());
        // No decorated pseudonum, only update the cached data.
        verify(mWifiNative).setEapAnonymousIdentity(any(), eq(pseudonym), eq(false));
        assertEquals(pseudonym,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        // Verify that WifiConfigManager#addOrUpdateNetwork() was called if there we received a
        // real pseudonym to be stored. i.e. Encrypted IMSI will be used once, followed by
        // pseudonym usage in all subsequent connections.
        // Note: This test will fail if future logic will have additional conditions that would
        // trigger "add or update network" operation. The test needs to be updated to account for
        // this change.
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
        verify(mWifiNetworkSuggestionsManager).setAnonymousIdentity(any());
    }

    /**
     * For EAP SIM network, the In-band pseudonym from supplicant should override the OOB
     * pseudonym if the OOB Pseudonym feature is enabled.
     */
    @Test
    public void testUpdatePseudonymAsInBandPseudonymWhenConnectionIsEstablished()
            throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        String initialOobPseudonym = "1234abcg@wlan.mnc456.mcc123.3gppnetwork.org";
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity(initialOobPseudonym);
        mConnectedNetwork.fromWifiNetworkSuggestion = true;
        String pseudonymFromSupplicant = "83bcca9384fca@wlan.mnc456.mcc123.3gppnetwork.org";

        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);

        triggerConnect();

        // CMD_START_CONNECT doesn't change the anonymous identity.
        assertEquals(initialOobPseudonym,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mWifiNative.getEapAnonymousIdentity(anyString()))
                .thenReturn(pseudonymFromSupplicant);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(true);

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        verify(mWifiNative).getEapAnonymousIdentity(any());
        // No decorated pseudonum, only update the cached data.
        verify(mWifiNative).setEapAnonymousIdentity(any(), eq(pseudonymFromSupplicant),
                eq(false));
        verify(mWifiPseudonymManager).setInBandPseudonym(anyInt(), eq(pseudonymFromSupplicant));
        assertEquals(pseudonymFromSupplicant,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
    }

    /**
     * Tests anonymous identity will be set to passpoint network.
     */
    @Test
    public void testSetAnonymousIdentityWhenConnectionIsEstablishedWithPseudonymForPasspoint()
            throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");
        mConnectedNetwork.FQDN = WifiConfigurationTestUtil.TEST_FQDN;
        mConnectedNetwork.providerFriendlyName =
                WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME;
        mConnectedNetwork.setPasspointUniqueId(WifiConfigurationTestUtil.TEST_FQDN + "_"
                + WifiConfigurationTestUtil.TEST_FQDN.hashCode());
        String expectedAnonymousIdentity = "anonymous@wlan.mnc456.mcc123.3gppnetwork.org";
        String pseudonym = "83bcca9384fca@wlan.mnc456.mcc123.3gppnetwork.org";

        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(any()))
                .thenReturn(expectedAnonymousIdentity);

        triggerConnect();

        // CMD_START_CONNECT should have set anonymousIdentity to anonymous@<realm>
        assertEquals(expectedAnonymousIdentity,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mWifiNative.getEapAnonymousIdentity(anyString()))
                .thenReturn(pseudonym);
        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)).thenReturn(true);

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        verify(mWifiNative).getEapAnonymousIdentity(any());
        // No decorated pseudonum, only update the cached data.
        verify(mWifiNative).setEapAnonymousIdentity(any(), eq(pseudonym), eq(false));
        assertEquals(pseudonym,
                mConnectedNetwork.enterpriseConfig.getAnonymousIdentity());
        // Verify that WifiConfigManager#addOrUpdateNetwork() was called if there we received a
        // real pseudonym to be stored. i.e. Encrypted IMSI will be used once, followed by
        // pseudonym usage in all subsequent connections.
        // Note: This test will fail if future logic will have additional conditions that would
        // trigger "add or update network" operation. The test needs to be updated to account for
        // this change.
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
        verify(mPasspointManager).setAnonymousIdentity(any());
    }
    /**
     * Tests the Passpoint information is set in WifiInfo for Passpoint AP connection.
     */
    @Test
    public void connectPasspointAp() throws Exception {
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createPasspointNetwork());
        config.SSID = TEST_WIFI_SSID.toString();
        config.BSSID = TEST_BSSID_STR;
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.roamingConsortiumIds = TEST_RCOI_ARRAY;
        when(mPasspointManager.getSelectedRcoiForNetwork(eq(config.getPasspointUniqueId()),
                eq(config.SSID))).thenReturn(TEST_MATCHED_RCOI);
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
        assertEquals(TEST_MATCHED_RCOI, config.enterpriseConfig.getSelectedRcoi());

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATING));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertEquals(WifiConfigurationTestUtil.TEST_FQDN, wifiInfo.getPasspointFqdn());
        assertEquals(WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME,
                wifiInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Tests that Passpoint fields in WifiInfo are reset when connecting to a non-Passpoint network
     * during DisconnectedState.
     * @throws Exception
     */
    @Test
    public void testResetWifiInfoPasspointFields() throws Exception {
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createPasspointNetwork());
        config.SSID = TEST_WIFI_SSID.toString();
        config.BSSID = TEST_BSSID_STR;
        config.networkId = PASSPOINT_NETWORK_ID;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(PASSPOINT_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATING));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATING));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertNull(wifiInfo.getPasspointFqdn());
        assertNull(wifiInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Tests the OSU information is set in WifiInfo for OSU AP connection.
     */
    @Test
    public void connectOsuAp() throws Exception {
        WifiConfiguration osuConfig = spy(WifiConfigurationTestUtil.createEphemeralNetwork());
        osuConfig.SSID = TEST_WIFI_SSID.toString();
        osuConfig.BSSID = TEST_BSSID_STR;
        osuConfig.osu = true;
        osuConfig.networkId = FRAMEWORK_NETWORK_ID;
        osuConfig.providerFriendlyName = WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME;
        osuConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        setupAndStartConnectSequence(osuConfig);
        validateSuccessfulConnectSequence(osuConfig);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATING));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertTrue(wifiInfo.isOsuAp());
        assertEquals(WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME,
                wifiInfo.getPasspointProviderFriendlyName());
    }

    /**
     * Tests that OSU fields in WifiInfo are reset when connecting to a non-OSU network during
     * DisconnectedState.
     * @throws Exception
     */
    @Test
    public void testResetWifiInfoOsuFields() throws Exception {
        WifiConfiguration osuConfig = spy(WifiConfigurationTestUtil.createEphemeralNetwork());
        osuConfig.SSID = TEST_WIFI_SSID.toString();
        osuConfig.BSSID = TEST_BSSID_STR;
        osuConfig.osu = true;
        osuConfig.networkId = PASSPOINT_NETWORK_ID;
        osuConfig.providerFriendlyName = WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME;
        osuConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        setupAndStartConnectSequence(osuConfig);
        validateSuccessfulConnectSequence(osuConfig);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(PASSPOINT_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATING));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATING));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertFalse(wifiInfo.isOsuAp());
    }

    /**
     * Tests that {@link WifiInfo#getHiddenSsid()} returns {@code true} if we've connected to a
     * hidden SSID network.
     * @throws Exception
     */
    @Test
    public void testConnectHiddenSsid() throws Exception {
        connect();

        // Set the scan detail cache for hidden SSID.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        ScanDetail hiddenScanDetail = getHiddenScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR1)).thenReturn(hiddenScanDetail);
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR1)).thenReturn(
                hiddenScanDetail.getScanResult());

        mCmi.sendMessage(WifiMonitor.ASSOCIATED_BSSID_EVENT, 0, 0, TEST_BSSID_STR1);
        mLooper.dispatchAll();

        // Hidden SSID scan result should set WifiInfo.getHiddenSsid() to true.
        assertTrue(mWifiInfo.getHiddenSSID());

        // Set the scan detail cache for non-hidden SSID.
        ScanDetail googleGuestScanDetail =
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq1);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(googleGuestScanDetail);
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                googleGuestScanDetail.getScanResult());

        mCmi.sendMessage(WifiMonitor.ASSOCIATED_BSSID_EVENT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        // Non-hidden SSID scan result should set WifiInfo.getHiddenSsid() to false.
        assertFalse(mWifiInfo.getHiddenSSID());
    }

    /**
     * Verify that WifiStateTracker is called if wifi is disabled while connected.
     */
    @Test
    public void verifyWifiStateTrackerUpdatedWhenDisabled() throws Exception {
        connect();

        mCmi.stop();
        mLooper.dispatchAll();
        verify(mWifiStateTracker).updateState(WIFI_IFACE_NAME, WifiStateTracker.DISCONNECTED);
    }

    @Test
    public void testIdleModeChanged_firmwareRoaming() throws Exception {
        // verify no-op when either the feature flag is disabled or firmware roaming is not
        // supported
        when(mWifiGlobals.isDisableFirmwareRoamingInIdleMode()).thenReturn(false);
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        mCmi.onIdleModeChanged(true);
        verify(mWifiNative, never()).enableFirmwareRoaming(anyString(), anyInt());
        when(mWifiGlobals.isDisableFirmwareRoamingInIdleMode()).thenReturn(true);
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);
        mCmi.onIdleModeChanged(true);
        verify(mWifiNative, never()).enableFirmwareRoaming(anyString(), anyInt());

        // Enable both, then verify firmware roaming is disabled when idle mode is entered
        when(mWifiGlobals.isDisableFirmwareRoamingInIdleMode()).thenReturn(true);
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        mCmi.onIdleModeChanged(true);
        verify(mWifiNative).enableFirmwareRoaming(anyString(),
                eq(WifiNative.DISABLE_FIRMWARE_ROAMING));

        // Verify firmware roaming is enabled when idle mode exited
        when(mWifiRoamingConfigStore.getRoamingMode(anyString())).thenReturn(
                WifiManager.ROAMING_MODE_NORMAL);
        mCmi.onIdleModeChanged(false);
        verify(mWifiNative).setRoamingMode(anyString(),
                eq(WifiManager.ROAMING_MODE_NORMAL));
    }

    @Test
    public void testIdleModeChanged_firmwareRoamingLocalOnlyCase() throws Exception {
        // mock connected network to be local only
        connect();
        mConnectedNetwork.fromWifiNetworkSpecifier = true;

        // Enable feature, then verify firmware roaming is disabled when idle mode is entered
        when(mWifiGlobals.isDisableFirmwareRoamingInIdleMode()).thenReturn(true);
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        mCmi.onIdleModeChanged(true);
        verify(mWifiNative).enableFirmwareRoaming(anyString(),
                eq(WifiNative.DISABLE_FIRMWARE_ROAMING));

        // Verify firmware roaming is not enabled when idle mode exited
        mCmi.onIdleModeChanged(false);
        verify(mWifiNative, never()).enableFirmwareRoaming(anyString(),
                eq(WifiNative.ENABLE_FIRMWARE_ROAMING));
    }

    /**
     * Verify that when the primary connects, roaming mode is set
     * based on the connected network ssid.
     */
    @Test
    public void testPerSsidRoamingModePrimary() throws Exception {
        when(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID)).thenReturn(
                WifiManager.ROAMING_MODE_NORMAL);
        connect();
        verify(mWifiRoamingConfigStore).getRoamingMode(TEST_SSID);
        verify(mWifiNative).setRoamingMode(anyString(),
                eq(WifiManager.ROAMING_MODE_NORMAL));
    }

    /**
     * Verify that when the secondary connects, roaming mode is not set.
     */
    @Test
    public void testPerSsidRoamingModeSecondary() throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();
        verify(mWifiRoamingConfigStore, never()).getRoamingMode(anyString());
        verify(mWifiNative, never()).setRoamingMode(anyString(), anyInt());
    }

    /**
     * Tests the network connection initiation sequence with no network request pending from
     * from WifiNetworkFactory when we're already connected to a different network.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectWithNoNetworkRequestAndAlreadyConnected() throws Exception {
        // Simulate the first connection.
        connect();

        // Remove the network requests.
        when(mWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mUntrustedWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mOemWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID + 1;
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
        verify(mWifiPermissionsUtil, atLeastOnce()).checkNetworkSettingsPermission(anyInt());
    }

    /**
     * Tests the network connection initiation sequence from a non-privileged app with no network
     * request pending from from WifiNetworkFactory when we're already connected to a different
     * network.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we don't invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectWithNoNetworkRequestAndAlreadyConnectedButNonPrivilegedApp()
            throws Exception {
        // Simulate the first connection.
        connect();

        // Remove the network requests.
        when(mWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mUntrustedWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mOemWifiNetworkFactory.hasConnectionRequests()).thenReturn(false);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(false);

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID + 1;
        setupAndStartConnectSequence(config);
        verify(mWifiConnectivityManager).prepareForForcedConnection(eq(config.networkId));
        verify(mWifiConfigManager, never())
                .getConfiguredNetworkWithoutMasking(eq(config.networkId));
        verify(mWifiNative, never()).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
        // These are called from connectToUserSelectNetwork() and L2ConnectedState::enter()
        verify(mWifiPermissionsUtil, times(4)).checkNetworkSettingsPermission(anyInt());
    }

    /**
     * If caller tries to connect to a network that is already connected, the connection request
     * should succeed.
     *
     * Test: Create and connect to a network, then try to reconnect to the same network. Verify
     * that connection request returns with CONNECT_NETWORK_SUCCEEDED.
     */
    @Test
    public void reconnectToConnectedNetworkWithNetworkId() throws Exception {
        connect();

        // try to reconnect
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(FRAMEWORK_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, TEST_ATTRIBUTION_TAG);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        // Verify that we didn't trigger a second connection.
        verify(mWifiNative, times(1)).connectToNetwork(eq(WIFI_IFACE_NAME), any());
    }

    /**
     * If caller tries to connect to a network that is already connected, the connection request
     * should succeed.
     *
     * Test: Create and connect to a network, then try to reconnect to the same network. Verify
     * that connection request returns with CONNECT_NETWORK_SUCCEEDED.
     */
    @Test
    public void reconnectToConnectedNetworkWithConfig() throws Exception {
        connect();

        // try to reconnect
        IActionListener connectActionListener = mock(IActionListener.class);
        int callingUid = Binder.getCallingUid();
        mCmi.connectNetwork(
                new NetworkUpdateResult(FRAMEWORK_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                callingUid, OP_PACKAGE_NAME, TEST_ATTRIBUTION_TAG);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        // Verify that we didn't trigger a second connection.
        verify(mWifiNative, times(1)).connectToNetwork(eq(WIFI_IFACE_NAME), any());
    }

    /**
     * If caller tries to connect to a network that is already connecting, the connection request
     * should succeed.
     *
     * Test: Create and trigger connect to a network, then try to reconnect to the same network.
     * Verify that connection request returns with CONNECT_NETWORK_SUCCEEDED and did not trigger a
     * new connection.
     */
    @Test
    public void reconnectToConnectingNetwork() throws Exception {
        triggerConnect();

        // try to reconnect to the same network (before connection is established).
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(FRAMEWORK_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        // Verify that we didn't trigger a second connection.
        verify(mWifiNative, times(1)).connectToNetwork(eq(WIFI_IFACE_NAME), any());
    }

    /**
     * If caller tries to connect to a network that is already connecting, the connection request
     * should succeed.
     *
     * Test: Create and trigger connect to a network, then try to reconnect to the same network.
     * Verify that connection request returns with CONNECT_NETWORK_SUCCEEDED and did trigger a new
     * connection.
     */
    @Test
    public void reconnectToConnectingNetworkWithCredentialChange() throws Exception {
        triggerConnect();

        // try to reconnect to the same network with a credential changed (before connection is
        // established).
        NetworkUpdateResult networkUpdateResult = new NetworkUpdateResult(
                FRAMEWORK_NETWORK_ID,
                STATUS_SUCCESS,
                false /* ip */,
                false /* proxy */,
                true /* credential */,
                false /* isNewNetwork */);
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                networkUpdateResult,
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        // Verify that we triggered a second connection.
        verify(mWifiNative, times(2)).connectToNetwork(eq(WIFI_IFACE_NAME), any());
    }

    /**
     * If caller tries to connect to a network that previously failed connection, the connection
     * request should succeed.
     *
     * Test: Create and trigger connect to a network, then fail the connection. Now try to reconnect
     * to the same network. Verify that connection request returns with CONNECT_NETWORK_SUCCEEDED
     * and did trigger a new * connection.
     */
    @Test
    public void connectAfterAssociationRejection() throws Exception {
        triggerConnect();

        // fail the connection.
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.AP_UNABLE_TO_HANDLE_NEW_STA, false));
        mLooper.dispatchAll();

        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(FRAMEWORK_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        // Verify that we triggered a second connection.
        verify(mWifiNative, times(2)).connectToNetwork(eq(WIFI_IFACE_NAME), any());
    }

    /**
     * If caller tries to connect to a network that previously failed connection, the connection
     * request should succeed.
     *
     * Test: Create and trigger connect to a network, then fail the connection. Now try to reconnect
     * to the same network. Verify that connection request returns with CONNECT_NETWORK_SUCCEEDED
     * and did trigger a new * connection.
     */
    @Test
    public void connectAfterConnectionFailure() throws Exception {
        triggerConnect();

        // fail the connection.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(FRAMEWORK_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        // Verify that we triggered a second connection.
        verify(mWifiNative, times(2)).connectToNetwork(eq(WIFI_IFACE_NAME), any());
    }

    /**
     * If the interface has been switched to scan, the network disconnection event should clear the
     * current network.
     * @throws Exception
     */
    @Test
    public void testNetworkDisconnectAfterInterfaceSwitchedToScan() throws Exception {
        triggerConnect();
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(FRAMEWORK_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
        // Disconnection from previous network.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();
        verify(mActiveModeWarden).setCurrentNetwork(null);
    }

    /**
     * If caller tries to connect to a new network while still provisioning the current one,
     * the connection attempt should succeed.
     */
    @Test
    public void connectWhileObtainingIp() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        verify(mWifiNative).removeAllNetworks(WIFI_IFACE_NAME);

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        // Connect to a different network
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID + 1;
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);

        // Disconnection from previous network.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        // Ensure we don't end the new connection event.
        verify(mWifiMetrics, never()).endConnectionEvent(
                any(), eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION),
                anyInt(), anyInt(), anyInt(), anyInt());
        verify(mWifiConnectivityManager).prepareForForcedConnection(FRAMEWORK_NETWORK_ID + 1);
    }

    /**
     * If there is a network removal while still connecting to it, the connection
     * should be aborted.
     */
    @Test
    public void networkRemovalWhileObtainingIp() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        verify(mWifiNative).removeAllNetworks(WIFI_IFACE_NAME);

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());
        reset(mWifiNative);

        // Simulate the target network removal & the disconnect trigger.
        WifiConfiguration removedNetwork = new WifiConfiguration();
        removedNetwork.networkId = FRAMEWORK_NETWORK_ID;
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkRemoved(removedNetwork);
        }
        mLooper.dispatchAll();

        verify(mWifiNative).removeNetworkCachedData(FRAMEWORK_NETWORK_ID);
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_NETWORK_REMOVED));

        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID)).thenReturn(null);
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Tests that manual connection to a network (from settings app) logs the correct nominator ID.
     */
    @Test
    public void testManualConnectNominator() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);

        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(TEST_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Process.SYSTEM_UID, OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();

        verify(mWifiMetrics).setNominatorForNetwork(TEST_NETWORK_ID,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL);
    }

    private void startConnectSuccess() throws Exception {
        startConnectSuccess(FRAMEWORK_NETWORK_ID);
    }

    private void startConnectSuccess(int networkId) throws Exception {
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(networkId),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();
    }

    @Test
    public void testDhcpFailure() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());
        injectDhcpFailure();
        mLooper.dispatchAll();

        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        // Verify this is not counted as a IP renewal failure
        verify(mWifiMetrics, never()).incrementIpRenewalFailure();
        // Verifies that WifiLastResortWatchdog be notified
        // by DHCP failure
        verify(mWifiLastResortWatchdog, times(2)).noteConnectionFailureAndTriggerIfNeeded(
                eq(TEST_SSID), eq(TEST_BSSID_STR),
                eq(WifiLastResortWatchdog.FAILURE_CODE_DHCP), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_DHCP_FAILURE), anyInt());
        verify(mWifiBlocklistMonitor, never()).handleDhcpProvisioningSuccess(
                TEST_BSSID_STR, TEST_SSID);
        verify(mWifiBlocklistMonitor, never()).handleNetworkValidationSuccess(
                TEST_BSSID_STR, TEST_SSID);
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 3, true);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
    }

    /**
     * Verify that a IP renewal failure is logged when IP provisioning fail in the
     * L3ConnectedState.
     */
    @Test
    public void testDhcpRenewalMetrics() throws Exception {
        connect();
        injectDhcpFailure();
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementIpRenewalFailure();
    }

    /**
     * Verify that the network selection status will be updated with DISABLED_AUTHENTICATION_FAILURE
     * when wrong password authentication failure is detected and the network had been
     * connected previously.
     */
    @Test
    public void testWrongPasswordWithPreviouslyConnected() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = createTestNetwork(false);
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWrongPasswordNotifier, never()).onWrongPasswordError(any());
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE));

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify that when wrong password authentication failure notification is not sent when
     * the network was local only.
     */
    @Test
    public void testWrongPasswordWithLocalOnlyConnection() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = createTestNetwork(false);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Set.of(TEST_UID));
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(TEST_UID, OP_PACKAGE_NAME));
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWrongPasswordNotifier, never()).onWrongPasswordError(any());
        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify that when wrong password authentication failure notification is sent when
     * the network was NOT local only.
     */
    @Test
    public void testWrongPasswordWithNonLocalOnlyConnection() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = createTestNetwork(false);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Collections.emptySet());
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(Process.INVALID_UID, ""));
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWrongPasswordNotifier).onWrongPasswordError(any());
        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * It is observed sometimes the WifiMonitor.NETWORK_DISCONNECTION_EVENT is observed before the
     * actual connection failure messages while making a connection.
     * The test make sure that make sure that the connection event is ended properly in the above
     * case.
     */
    @Test
    public void testDisconnectionEventInL2ConnectingStateEndsConnectionEvent() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = createTestNetwork(false);
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWifiMetrics).endConnectionEvent(
                any(), eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION),
                anyInt(), anyInt(), anyInt(), anyInt());
        verify(mWifiConnectivityManager).handleConnectionAttemptEnded(
                any(), anyInt(), anyInt(), any(), any());
        assertEquals(WifiInfo.SECURITY_TYPE_UNKNOWN, mWifiInfo.getCurrentSecurityType());
        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify that the network selection status will be updated with DISABLED_BY_WRONG_PASSWORD
     * when wrong password authentication failure is detected and the network has never been
     * connected.
     */
    @Test
    public void testWrongPasswordWithNeverConnected() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.getNetworkSelectionStatus().setHasEverConnected(false);
        config.carrierId = CARRIER_ID_1;
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        // Need to add supplicant state changed event to simulate broadcasts correctly
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.FOUR_WAY_HANDSHAKE));

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWrongPasswordNotifier).onWrongPasswordError(config);
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD));
        verify(mWifiMetrics).incrementNumOfCarrierWifiConnectionAuthFailure();
        // Verify broadcasts corresponding to supplicant ASSOCIATED, FOUR_WAY_HANDSHAKE, then
        // finally wrong password causing disconnect.
        inOrderVerifyNetworkStateChangedBroadcasts(
                new NetworkStateChangedIntentMatcher(CONNECTING),
                new NetworkStateChangedIntentMatcher(AUTHENTICATING),
                new NetworkStateChangedIntentMatcher(DISCONNECTED));
        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify that the function resetCarrierKeysForImsiEncryption() in TelephonyManager
     * is called when a Authentication failure is detected with a vendor specific EAP Error
     * of certification expired while using EAP-SIM
     * In this test case, it is assumed that the network had been connected previously.
     */
    @Test
    public void testEapSimErrorVendorSpecific() throws Exception {
        when(mWifiMetrics.startConnectionEvent(any(), any(), anyString(), anyInt(), anyBoolean(),
                anyInt(), anyInt())).thenReturn(80000);
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        config.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);
        when(mWifiScoreCard.detectAbnormalConnectionFailure(anyString()))
                .thenReturn(WifiHealthMonitor.REASON_AUTH_FAILURE);

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE,
                        WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED));
        mLooper.dispatchAll();

        verify(mEapFailureNotifier).onEapFailure(
                WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED, config, true);
        verify(mWifiCarrierInfoManager).resetCarrierKeysForImsiEncryption(any());
        verify(mDeviceConfigFacade).isAbnormalConnectionFailureBugreportEnabled();
        verify(mWifiScoreCard).detectAbnormalConnectionFailure(anyString());
        verify(mWifiDiagnostics, times(2)).takeBugReport(anyString(), anyString());
    }

    @Test
    public void testEapAkaRetrieveOobPseudonymTriggeredByAuthenticationFailure() throws Exception {
        when(mWifiMetrics.startConnectionEvent(any(), any(), anyString(), anyInt(), anyBoolean(),
                anyInt(), anyInt())).thenReturn(80000);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(anyInt())).thenReturn(true);
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        config.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.AKA);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);
        when(mWifiScoreCard.detectAbnormalConnectionFailure(anyString()))
                .thenReturn(WifiHealthMonitor.REASON_AUTH_FAILURE);

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE,
                        -1));
        mLooper.dispatchAll();

        verify(mEapFailureNotifier).onEapFailure(
                -1, config, true);
        verify(mWifiPseudonymManager).retrieveOobPseudonymWithRateLimit(anyInt());
        verify(mDeviceConfigFacade).isAbnormalConnectionFailureBugreportEnabled();
        verify(mWifiScoreCard).detectAbnormalConnectionFailure(anyString());
        verify(mWifiDiagnostics, times(2)).takeBugReport(anyString(), anyString());
    }

    /**
     * Verify that the function resetCarrierKeysForImsiEncryption() in TelephonyManager
     * is not called when a Authentication failure is detected with a vendor specific EAP Error
     * of certification expired while using other methods than EAP-SIM, EAP-AKA, or EAP-AKA'.
     */
    @Test
    public void testEapTlsErrorVendorSpecific() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        config.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE,
                        WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED));
        mLooper.dispatchAll();

        verify(mWifiCarrierInfoManager, never()).resetCarrierKeysForImsiEncryption(any());
    }

    @Test
    public void testAuthFailureOnDifferentSsidIsIgnored() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo("WRONG_SSID",
                        MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE,
                        WifiNative.EAP_SIM_NOT_SUBSCRIBED));
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_AUTHENTICATION_NO_SUBSCRIPTION));
    }

    /**
     * Verify that the network selection status will be updated with
     * DISABLED_AUTHENTICATION_NO_SUBSCRIBED when service is not subscribed.
     */
    @Test
    public void testEapSimNoSubscribedError() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE,
                        WifiNative.EAP_SIM_NOT_SUBSCRIBED));
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_AUTHENTICATION_NO_SUBSCRIPTION));
    }

    @Test
    public void testBadNetworkEvent() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiDiagnostics, never()).takeBugReport(anyString(), anyString());
    }


    @Test
    public void getWhatToString() throws Exception {
        assertEquals("CMD_PRE_DHCP_ACTION", mCmi.getWhatToString(CMD_PRE_DHCP_ACTION));
        assertEquals("CMD_IP_REACHABILITY_LOST", mCmi.getWhatToString(
                ClientModeImpl.CMD_IP_REACHABILITY_LOST));
        assertEquals("CMD_IP_REACHABILITY_FAILURE", mCmi.getWhatToString(
                ClientModeImpl.CMD_IP_REACHABILITY_FAILURE));
    }

    @Test
    public void disconnect() throws Exception {
        when(mWifiScoreCard.detectAbnormalDisconnection(any()))
                .thenReturn(WifiHealthMonitor.REASON_SHORT_CONNECTION_NONLOCAL);
        InOrder inOrderWifiLockManager = inOrder(mWifiLockManager);
        connect();
        inOrderWifiLockManager.verify(mWifiLockManager)
                .updateWifiClientConnected(mClientModeManager, true);

        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        verify(mWifiStateTracker).updateState(WIFI_IFACE_NAME, WifiStateTracker.DISCONNECTED);
        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mCmiMonitor).onConnectionEnd(mClientModeManager);
        inOrderWifiLockManager.verify(mWifiLockManager)
                .updateWifiClientConnected(mClientModeManager, false);
        verify(mWifiScoreCard).detectAbnormalDisconnection(WIFI_IFACE_NAME);
        verify(mWifiDiagnostics).takeBugReport(anyString(), anyString());
        verify(mWifiNative).disableNetwork(WIFI_IFACE_NAME);
        // Set MAC address thrice - once at bootup, once for new connection, once for disconnect.
        verify(mWifiNative, times(3)).setStaMacAddress(eq(WIFI_IFACE_NAME), any());
        // ClientModeManager should only be stopped when in lingering mode
        verify(mClientModeManager, never()).stop();
    }

    @Test
    public void secondaryRoleCmmDisconnected_stopsClientModeManager() throws Exception {
        // Owning ClientModeManager has role SECONDARY_TRANSIENT
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        connect();

        // ClientModeManager never stopped
        verify(mClientModeManager, never()).stop();

        // Disconnected from network
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());

        // Since in lingering mode, disconnect => stop ClientModeManager
        verify(mClientModeManager).stop();
    }

    @Test
    public void primaryCmmDisconnected_doesntStopsClientModeManager() throws Exception {
        // Owning ClientModeManager is primary
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);

        connect();

        // ClientModeManager never stopped
        verify(mClientModeManager, never()).stop();

        // Disconnected from network
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());

        // Since primary => don't stop ClientModeManager
        verify(mClientModeManager, never()).stop();
    }

    /**
     * Successfully connecting to a network will set WifiConfiguration's value of HasEverConnected
     * to true.
     *
     * Test: Successfully create and connect to a network. Check the config and verify
     * WifiConfiguration.getHasEverConnected() is true.
     */
    @Test
    public void setHasEverConnectedTrueOnConnect() throws Exception {
        connect();
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkAfterConnect(eq(0), eq(true),
                eq(false),
                anyInt());
    }

    /**
     * Fail network connection attempt and verify HasEverConnected remains false.
     *
     * Test: Successfully create a network but fail when connecting. Check the config and verify
     * WifiConfiguration.getHasEverConnected() is false.
     */
    @Test
    public void connectionFailureDoesNotSetHasEverConnectedTrue() throws Exception {
        testDhcpFailure();
        verify(mWifiConfigManager, never())
                .updateNetworkAfterConnect(eq(0), eq(true), eq(false), anyInt());
    }

    @Test
    public void iconQueryTest() throws Exception {
        // TODO(b/31065385): Passpoint config management.
    }

    @Test
    public void verboseLogRecSizeIsGreaterThanNormalSize() {
        assertTrue(LOG_REC_LIMIT_IN_VERBOSE_MODE > mWifiGlobals.getClientModeImplNumLogRecs());
    }

    /**
     * Verifies that, by default, we allow only the "normal" number of log records.
     */
    @Test
    public void normalLogRecSizeIsUsedByDefault() {
        mCmi.enableVerboseLogging(false);
        assertEquals(mWifiGlobals.getClientModeImplNumLogRecs(), mCmi.getLogRecMaxSize());
    }

    /**
     * Verifies that, in verbose mode, we allow a larger number of log records.
     */
    @Test
    public void enablingVerboseLoggingUpdatesLogRecSize() {
        when(mActivityManager.isLowRamDevice()).thenReturn(false);
        mCmi.enableVerboseLogging(true);
        assertEquals(LOG_REC_LIMIT_IN_VERBOSE_MODE, mCmi.getLogRecMaxSize());
    }

    /**
     * Verifies that, in verbose mode, we allow a larger number of log records on a low ram device.
     */
    @Test
    public void enablingVerboseLoggingUpdatesLogRecSizeLowRamDevice() {
        when(mActivityManager.isLowRamDevice()).thenReturn(true);
        mCmi.enableVerboseLogging(true);
        assertEquals(LOG_REC_LIMIT_IN_VERBOSE_MODE_LOW_RAM, mCmi.getLogRecMaxSize());
    }

    @Test
    public void disablingVerboseLoggingClearsRecords() {
        mCmi.sendMessage(ClientModeImpl.CMD_DISCONNECT);
        mLooper.dispatchAll();
        assertTrue(mCmi.getLogRecSize() >= 1);

        mCmi.enableVerboseLogging(false);
        assertEquals(0, mCmi.getLogRecSize());
    }

    @Test
    public void disablingVerboseLoggingUpdatesLogRecSize() {
        mCmi.enableVerboseLogging(true);
        mCmi.enableVerboseLogging(false);
        assertEquals(mWifiGlobals.getClientModeImplNumLogRecs(), mCmi.getLogRecMaxSize());
    }

    @Test
    public void logRecsIncludeDisconnectCommand() {
        // There's nothing special about the DISCONNECT command. It's just representative of
        // "normal" commands.
        mCmi.sendMessage(ClientModeImpl.CMD_DISCONNECT);
        mLooper.dispatchAll();
        assertEquals(1, mCmi.copyLogRecs()
                .stream()
                .filter(logRec -> logRec.getWhat() == ClientModeImpl.CMD_DISCONNECT)
                .count());
    }

    @Test
    public void logRecsExcludeRssiPollCommandByDefault() {
        mCmi.enableVerboseLogging(false);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL);
        mLooper.dispatchAll();
        assertEquals(0, mCmi.copyLogRecs()
                .stream()
                .filter(logRec -> logRec.getWhat() == ClientModeImpl.CMD_RSSI_POLL)
                .count());
    }

    @Test
    public void logRecsIncludeRssiPollCommandWhenVerboseLoggingIsEnabled() {
        mCmi.enableVerboseLogging(true);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL);
        mLooper.dispatchAll();
        assertEquals(1, mCmi.copyLogRecs()
                .stream()
                .filter(logRec -> logRec.getWhat() == ClientModeImpl.CMD_RSSI_POLL)
                .count());
    }

    /**
     * Verify that syncStartSubscriptionProvisioning will redirect calls with right parameters
     * to {@link PasspointManager} with expected true being returned when in client mode.
     */
    @Test
    public void syncStartSubscriptionProvisioningInClientMode() throws Exception {
        when(mPasspointManager.startSubscriptionProvisioning(anyInt(),
                any(OsuProvider.class), any(IProvisioningCallback.class))).thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mCmi.syncStartSubscriptionProvisioning(
                OTHER_USER_UID, mOsuProvider, mProvisioningCallback));
        verify(mPasspointManager).startSubscriptionProvisioning(OTHER_USER_UID, mOsuProvider,
                mProvisioningCallback);
        mLooper.stopAutoDispatch();
    }

    @Test
    public void testGetCurrentNetwork() throws Exception {
        // getCurrentNetwork() returns null when disconnected
        assertNull(mCmi.getCurrentNetwork());
        connect();

        assertEquals("L3ConnectedState", getCurrentState().getName());
        // getCurrentNetwork() returns non-null Network when connected
        assertEquals(mNetwork, mCmi.getCurrentNetwork());
        // Now trigger disconnect
        mCmi.disconnect();
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertNull(mCmi.getCurrentNetwork());
    }

    /**
     *  Test that we disconnect from a network if it was removed while we are in the
     *  L3ProvisioningState.
     */
    @Test
    public void disconnectFromNetworkWhenRemovedWhileObtainingIpAddr() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        verify(mWifiNative).removeAllNetworks(WIFI_IFACE_NAME);

        startConnectSuccess();

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);

        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        // trigger removal callback to trigger disconnect.
        WifiConfiguration removedConfig = new WifiConfiguration();
        removedConfig.networkId = FRAMEWORK_NETWORK_ID;
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkRemoved(removedConfig);
        }

        reset(mWifiConfigManager);

        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID)).thenReturn(null);

        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        verify(mWifiNative, times(2)).disconnect(WIFI_IFACE_NAME);
    }

    /**
     * Verifies that WifiInfo is updated upon SUPPLICANT_STATE_CHANGE_EVENT.
     */
    @Test
    public void testWifiInfoUpdatedUponSupplicantStateChangedEvent() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        // Set the scan detail cache for roaming target.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1).getScanResult());

        // This simulates the behavior of roaming to network with |TEST_BSSID_STR1|, |sFreq1|.
        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is updated.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR1, sFreq1,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertEquals(TEST_BSSID_STR1, wifiInfo.getBSSID());
        assertEquals(sFreq1, wifiInfo.getFrequency());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR1, sFreq1,
                        SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        wifiInfo = mWifiInfo;
        assertNull(wifiInfo.getBSSID());
        assertEquals(WifiManager.UNKNOWN_SSID, wifiInfo.getSSID());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, wifiInfo.getNetworkId());
        assertEquals(SupplicantState.DISCONNECTED, wifiInfo.getSupplicantState());
        assertEquals("DisconnectedState", getCurrentState().getName());
    }


    /**
     * Verifies that WifiInfo is updated upon SUPPLICANT_STATE_CHANGE_EVENT.
     */
    @Test
    public void testWifiInfoUpdatedUponSupplicantStateChangedEventWithWrongSsid() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        // Set the scan detail cache for roaming target.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1).getScanResult());

        // This simulates the behavior of roaming to network with |TEST_BSSID_STR1|, |sFreq1|.
        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is updated.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR1, sFreq1,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertEquals(TEST_BSSID_STR1, wifiInfo.getBSSID());
        assertEquals(sFreq1, wifiInfo.getFrequency());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());

        // Send state change event with wrong ssid.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID1, TEST_BSSID_STR, sFreq,
                        SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        wifiInfo = mWifiInfo;
        assertNull(wifiInfo.getBSSID());
        assertEquals(WifiManager.UNKNOWN_SSID, wifiInfo.getSSID());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, wifiInfo.getNetworkId());
        assertEquals(SupplicantState.DISCONNECTED, wifiInfo.getSupplicantState());
        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    @Test
    public void testTriggerWifiNetworkStateChangedListener() throws Exception {
        InOrder inOrder = inOrder(mActiveModeWarden);
        connect();
        inOrder.verify(mActiveModeWarden).onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTING);
        inOrder.verify(mActiveModeWarden).onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener
                        .WIFI_NETWORK_STATUS_OBTAINING_IPADDR);
        inOrder.verify(mActiveModeWarden).onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED);
        inOrder.verify(mActiveModeWarden, never()).onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED);
    }

    /**
     * Verifies that WifiInfo is updated upon CMD_ASSOCIATED_BSSID event.
     */
    @Test
    public void testWifiInfoUpdatedUponAssociatedBSSIDEvent() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        // Set the scan detail cache for roaming target.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq1).getScanResult());

        // This simulates the behavior of roaming to network with |TEST_BSSID_STR1|, |sFreq1|.
        // Send a CMD_ASSOCIATED_BSSID, verify WifiInfo is updated.
        mCmi.sendMessage(WifiMonitor.ASSOCIATED_BSSID_EVENT, 0, 0, TEST_BSSID_STR1);
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertEquals(TEST_BSSID_STR1, wifiInfo.getBSSID());
        assertEquals(sFreq1, wifiInfo.getFrequency());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());
        verifyNetworkStateChangedBroadcast(times(2),
                new NetworkStateChangedIntentMatcher(CONNECTED));
    }

    private void callmethod(VerificationMode mode, ArgumentMatcher<Intent> intentMatcher) {
        if (SdkLevel.isAtLeastU()) {
            verify(mUserAllContext, mode).sendStickyBroadcast(
                    argThat(intentMatcher), any());
        } else {
            verify(mContext, mode).sendStickyBroadcastAsUser(
                    argThat(intentMatcher), any());
        }
    }

    /**
     * Verifies that WifiInfo is cleared upon exiting and entering WifiInfo, and that it is not
     * updated by SUPPLICAN_STATE_CHANGE_EVENTs in ScanModeState.
     * This protects ClientModeImpl from  getting into a bad state where WifiInfo says wifi is
     * already Connected or Connecting, (when it is in-fact Disconnected), so
     * WifiConnectivityManager does not attempt any new Connections, freezing wifi.
     */
    @Test
    public void testWifiInfoCleanedUpEnteringExitingConnectableState() throws Exception {
        InOrder inOrderMetrics = inOrder(mWifiMetrics);
        Log.i(TAG, mCmi.getCurrentState().getName());
        String initialBSSID = "aa:bb:cc:dd:ee:ff";
        WifiInfo wifiInfo = mWifiInfo;
        wifiInfo.setBSSID(initialBSSID);

        // reset mWifiNative since initializeCmi() was called in setup()
        resetWifiNative();

        // Set CMI to CONNECT_MODE and verify state, and wifi enabled in ConnectivityManager
        initializeCmi();
        inOrderMetrics.verify(mWifiMetrics)
                .setWifiState(WIFI_IFACE_NAME, WifiMetricsProto.WifiLog.WIFI_DISCONNECTED);
        inOrderMetrics.verify(mWifiMetrics)
                .logStaEvent(WIFI_IFACE_NAME, StaEvent.TYPE_WIFI_ENABLED);
        assertNull(wifiInfo.getBSSID());

        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is updated
        connect();
        assertEquals(TEST_BSSID_STR, wifiInfo.getBSSID());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());

        // Set CMI to DISABLED_MODE, verify state and wifi disabled in ConnectivityManager, and
        // WifiInfo is reset() and state set to DISCONNECTED
        mCmi.stop();
        mLooper.dispatchAll();

        inOrderMetrics.verify(mWifiMetrics).setWifiState(WIFI_IFACE_NAME,
                WifiMetricsProto.WifiLog.WIFI_DISABLED);
        inOrderMetrics.verify(mWifiMetrics)
                .logStaEvent(WIFI_IFACE_NAME, StaEvent.TYPE_WIFI_DISABLED);
        assertNull(wifiInfo.getBSSID());
        assertEquals(SupplicantState.DISCONNECTED, wifiInfo.getSupplicantState());
    }

    @Test
    public void testWifiInfoCleanedUpEnteringExitingConnectableState2() throws Exception {
        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is not updated
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();
        assertNull(mWifiInfo.getBSSID());
        assertEquals(SupplicantState.DISCONNECTED, mWifiInfo.getSupplicantState());
    }

    @Test
    public void testWifiInfoCleanedUpEnteringExitingConnectableState3() throws Exception {
        String initialBSSID = "aa:bb:cc:dd:ee:ff";
        InOrder inOrderMetrics = inOrder(mWifiMetrics);

        // Set the bssid to something, so we can verify it is cleared (just in case)
        mWifiInfo.setBSSID(initialBSSID);

        initializeCmi();

        inOrderMetrics.verify(mWifiMetrics)
                .setWifiState(WIFI_IFACE_NAME, WifiMetricsProto.WifiLog.WIFI_DISCONNECTED);
        inOrderMetrics.verify(mWifiMetrics)
                .logStaEvent(WIFI_IFACE_NAME, StaEvent.TYPE_WIFI_ENABLED);
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertEquals(SupplicantState.DISCONNECTED, mWifiInfo.getSupplicantState());
        assertNull(mWifiInfo.getBSSID());
    }

    /**
     * Test that connected SSID and BSSID are exposed to system server.
     * Also tests that {@link ClientModeImpl#getConnectionInfo()} always
     * returns a copy of WifiInfo.
     */
    @Test
    public void testConnectedIdsAreVisibleFromSystemServer() throws Exception {
        WifiInfo wifiInfo = mWifiInfo;
        // Get into a connected state, with known BSSID and SSID
        connect();
        assertEquals(TEST_BSSID_STR, wifiInfo.getBSSID());
        assertEquals(TEST_WIFI_SSID, wifiInfo.getWifiSsid());

        WifiInfo connectionInfo = mCmi.getConnectionInfo();
        assertEquals(wifiInfo.getSSID(), connectionInfo.getSSID());
        assertEquals(wifiInfo.getBSSID(), connectionInfo.getBSSID());
        assertEquals(wifiInfo.getMacAddress(), connectionInfo.getMacAddress());
    }

    /**
     * Test that reconnectCommand() triggers connectivity scan when ClientModeImpl
     * is in DisconnectedMode.
     */
    @Test
    public void testReconnectCommandWhenDisconnected() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|, and then disconnect.
        disconnect();
        verify(mActiveModeWarden).onNetworkStateChanged(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY,
                WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED);

        mCmi.reconnect(ClientModeImpl.WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).forceConnectivityScan(ClientModeImpl.WIFI_WORK_SOURCE);
    }

    /**
     * Test that reconnectCommand() doesn't trigger connectivity scan when ClientModeImpl
     * is in ConnectedMode.
     */
    @Test
    public void testReconnectCommandWhenConnected() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        mCmi.reconnect(ClientModeImpl.WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(ClientModeImpl.WIFI_WORK_SOURCE);
    }

    /**
     * Verifies that ClientModeImpl sets and unsets appropriate 'RecentFailureReason' values
     * on a WifiConfiguration when it fails association, authentication, or successfully connects
     */
    @Test
    public void testExtraFailureReason_ApIsBusy() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        // Trigger a connection to this (CMD_START_CONNECT will actually fail, but it sets up
        // targetNetworkId state)
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        // Simulate an ASSOCIATION_REJECTION_EVENT, due to the AP being busy
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.AP_UNABLE_TO_HANDLE_NEW_STA,
                        false));
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(eq(0),
                eq(WifiConfiguration.RECENT_FAILURE_AP_UNABLE_TO_HANDLE_NEW_STA));
        assertEquals("DisconnectedState", getCurrentState().getName());

        // Simulate an AUTHENTICATION_FAILURE_EVENT, which should clear the ExtraFailureReason
        reset(mWifiConfigManager);
        initializeAndAddNetworkAndVerifySuccess();
        // Trigger a connection to this (CMD_START_CONNECT will actually fail, but it sets up
        // targetNetworkId state)
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_TIMEOUT, -1));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).clearRecentFailureReason(eq(0));
        verify(mWifiConfigManager, never()).setRecentFailureAssociationStatus(anyInt(), anyInt());

        // Simulate a NETWORK_CONNECTION_EVENT which should clear the ExtraFailureReason
        reset(mWifiConfigManager);
        initializeAndAddNetworkAndVerifySuccess();
        // Trigger a connection to this (CMD_START_CONNECT will actually fail, but it sets up
        // targetNetworkId state)
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, null, false, null));
        mLooper.dispatchAll();
        verify(mWifiConfigManager).clearRecentFailureReason(eq(0));
        verify(mWifiConfigManager, never()).setRecentFailureAssociationStatus(anyInt(), anyInt());
    }

    private WifiConfiguration makeLastSelectedWifiConfiguration(int lastSelectedNetworkId,
            long timeSinceLastSelected) {
        long lastSelectedTimestamp = 45666743454L;

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                lastSelectedTimestamp + timeSinceLastSelected);
        when(mWifiConfigManager.getLastSelectedTimeStamp()).thenReturn(lastSelectedTimestamp);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(lastSelectedNetworkId);

        WifiConfiguration currentConfig = new WifiConfiguration();
        currentConfig.networkId = lastSelectedNetworkId;
        return currentConfig;
    }

    /**
     * Test that the helper method
     * {@link ClientModeImpl#isRecentlySelectedByTheUser(WifiConfiguration)}
     * returns true when we connect to the last selected network before expiration of
     * {@link ClientModeImpl#LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS}.
     */
    @Test
    public void testIsRecentlySelectedByTheUser_SameNetworkNotExpired() {
        WifiConfiguration currentConfig = makeLastSelectedWifiConfiguration(5,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        assertTrue(mCmi.isRecentlySelectedByTheUser(currentConfig));
    }

    /**
     * Test that the helper method
     * {@link ClientModeImpl#isRecentlySelectedByTheUser(WifiConfiguration)}
     * returns false when we connect to the last selected network after expiration of
     * {@link ClientModeImpl#LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS}.
     */
    @Test
    public void testIsRecentlySelectedByTheUser_SameNetworkExpired() {
        WifiConfiguration currentConfig = makeLastSelectedWifiConfiguration(5,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS + 1);
        assertFalse(mCmi.isRecentlySelectedByTheUser(currentConfig));
    }

    /**
     * Test that the helper method
     * {@link ClientModeImpl#isRecentlySelectedByTheUser(WifiConfiguration)}
     * returns false when we connect to a different network to the last selected network.
     */
    @Test
    public void testIsRecentlySelectedByTheUser_DifferentNetwork() {
        WifiConfiguration currentConfig = makeLastSelectedWifiConfiguration(5,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        currentConfig.networkId = 4;
        assertFalse(mCmi.isRecentlySelectedByTheUser(currentConfig));
    }

    private void expectRegisterNetworkAgent(Consumer<NetworkAgentConfig> configChecker,
            Consumer<NetworkCapabilities> networkCapabilitiesChecker) {
        // Expects that the code calls registerNetworkAgent and provides a way for the test to
        // verify the messages sent through the NetworkAgent to ConnectivityService.
        // We cannot just use a mock object here because mWifiNetworkAgent is private to CMI.
        ArgumentCaptor<NetworkAgentConfig> configCaptor =
                ArgumentCaptor.forClass(NetworkAgentConfig.class);
        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        verify(mWifiInjector).makeWifiNetworkAgent(
                networkCapabilitiesCaptor.capture(),
                any(),
                configCaptor.capture(),
                any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        configChecker.accept(configCaptor.getValue());
        networkCapabilitiesChecker.accept(networkCapabilitiesCaptor.getValue());
    }

    private void expectNetworkAgentUpdateCapabilities(
            Consumer<NetworkCapabilities> networkCapabilitiesChecker) throws Exception {
        ArgumentCaptor<NetworkCapabilities> captor = ArgumentCaptor.forClass(
                NetworkCapabilities.class);
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent).sendNetworkCapabilitiesAndCache(captor.capture());
        networkCapabilitiesChecker.accept(captor.getValue());
    }

    /**
     * Verify that when a network is explicitly selected, but noInternetAccessExpected is false,
     * the {@link NetworkAgentConfig} contains the right values of explicitlySelected,
     * acceptUnvalidated and acceptPartialConnectivity.
     */
    @Test
    public void testExplicitlySelected_ExplicitInternetExpected() throws Exception {
        // Network is explicitly selected.
        WifiConfiguration config = makeLastSelectedWifiConfiguration(FRAMEWORK_NETWORK_ID,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        mConnectedNetwork.noInternetAccessExpected = false;

        connect();
        expectRegisterNetworkAgent((agentConfig) -> {
            assertTrue(agentConfig.explicitlySelected);
            assertFalse(agentConfig.acceptUnvalidated);
            assertFalse(agentConfig.acceptPartialConnectivity);
        }, (cap) -> { });
    }

    /**
     * Verify that when a network is explicitly selected, has role SECONDARY_TRANSIENT, but
     * noInternetAccessExpected is false, the {@link NetworkAgentConfig} contains the right values
     * of explicitlySelected, acceptUnvalidated and acceptPartialConnectivity.
     */
    @Test
    public void testExplicitlySelected_secondaryTransient_expectNotExplicitlySelected()
            throws Exception {
        // Network is explicitly selected.
        WifiConfiguration config = makeLastSelectedWifiConfiguration(FRAMEWORK_NETWORK_ID,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        mConnectedNetwork.noInternetAccessExpected = false;

        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        connect();
        expectRegisterNetworkAgent((agentConfig) -> {
            assertFalse(agentConfig.explicitlySelected);
            assertFalse(agentConfig.acceptUnvalidated);
            assertFalse(agentConfig.acceptPartialConnectivity);
        }, (cap) -> { });
    }

    /**
     * Verify that when a network is not explicitly selected, but noInternetAccessExpected is true,
     * the {@link NetworkAgentConfig} contains the right values of explicitlySelected,
     * acceptUnvalidated and acceptPartialConnectivity.
     */
    @Test
    public void testExplicitlySelected_NotExplicitNoInternetExpected() throws Exception {
        // Network is no longer explicitly selected.
        WifiConfiguration config = makeLastSelectedWifiConfiguration(FRAMEWORK_NETWORK_ID,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS + 1);
        mConnectedNetwork.noInternetAccessExpected = true;

        connect();
        expectRegisterNetworkAgent((agentConfig) -> {
            assertFalse(agentConfig.explicitlySelected);
            assertFalse(agentConfig.acceptUnvalidated);
            assertTrue(agentConfig.acceptPartialConnectivity);
        }, (cap) -> { });
    }

    /**
     * Verify that when a network is explicitly selected, and noInternetAccessExpected is true,
     * the {@link NetworkAgentConfig} contains the right values of explicitlySelected,
     * acceptUnvalidated and acceptPartialConnectivity.
     */
    @Test
    public void testExplicitlySelected_ExplicitNoInternetExpected() throws Exception {
        // Network is explicitly selected.
        WifiConfiguration config = makeLastSelectedWifiConfiguration(FRAMEWORK_NETWORK_ID,
                ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        mConnectedNetwork.noInternetAccessExpected = true;

        connect();
        expectRegisterNetworkAgent((agentConfig) -> {
            assertTrue(agentConfig.explicitlySelected);
            assertTrue(agentConfig.acceptUnvalidated);
            assertTrue(agentConfig.acceptPartialConnectivity);
        }, (cap) -> { });
    }

    /**
     * Verify that Rssi Monitoring is started and the callback registered after connecting.
     */
    @Test
    public void verifyRssiMonitoringCallbackIsRegistered() throws Exception {
        // Simulate the first connection.
        connect();

        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        ArrayList<Integer> thresholdsArray = new ArrayList<>();
        thresholdsArray.add(RSSI_THRESHOLD_MAX);
        thresholdsArray.add(RSSI_THRESHOLD_MIN);
        mWifiNetworkAgentCallbackCaptor.getValue().onSignalStrengthThresholdsUpdated(
                thresholdsArray.stream().mapToInt(Integer::intValue).toArray());
        mLooper.dispatchAll();

        ArgumentCaptor<WifiNative.WifiRssiEventHandler> rssiEventHandlerCaptor =
                ArgumentCaptor.forClass(WifiNative.WifiRssiEventHandler.class);
        verify(mWifiNative).startRssiMonitoring(anyString(), anyByte(), anyByte(),
                rssiEventHandlerCaptor.capture());

        // breach below min
        rssiEventHandlerCaptor.getValue().onRssiThresholdBreached(RSSI_THRESHOLD_BREACH_MIN);
        mLooper.dispatchAll();
        WifiInfo wifiInfo = mWifiInfo;
        assertEquals(RSSI_THRESHOLD_BREACH_MIN, wifiInfo.getRssi());

        // breach above max
        rssiEventHandlerCaptor.getValue().onRssiThresholdBreached(RSSI_THRESHOLD_BREACH_MAX);
        mLooper.dispatchAll();
        assertEquals(RSSI_THRESHOLD_BREACH_MAX, wifiInfo.getRssi());
    }

    /**
     * Verify that RSSI monitoring is not enabled on secondary STA.
     */
    @Test
    public void testRssiMonitoringOnSecondaryIsNotEnabled() throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();

        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        int[] thresholds = {RSSI_THRESHOLD_BREACH_MAX};
        mWifiNetworkAgentCallbackCaptor.getValue().onSignalStrengthThresholdsUpdated(thresholds);
        mLooper.dispatchAll();

        verify(mWifiNative, never()).startRssiMonitoring(anyString(), anyByte(), anyByte(),
                any());
    }

    /**
     * Verify that RSSI and link layer stats polling works in connected mode
     */
    @Test
    public void verifyConnectedModeRssiPolling() throws Exception {
        final long startMillis = 1_500_000_000_100L;
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, -42, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 0);
        mCmi.enableRssiPolling(true);
        connect();
        mLooper.dispatchAll();
        assertRssiChangeBroadcastSent(1);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 3333);
        mLooper.dispatchAll();
        WifiInfo wifiInfo = mWifiInfo;
        assertEquals(llStats.txmpdu_be, wifiInfo.txSuccess);
        assertEquals(llStats.rxmpdu_bk, wifiInfo.rxSuccess);
        assertEquals(signalPollResults.getRssi(), wifiInfo.getRssi());
        assertEquals(signalPollResults.getTxLinkSpeed(), wifiInfo.getLinkSpeed());
        assertEquals(signalPollResults.getTxLinkSpeed(), wifiInfo.getTxLinkSpeedMbps());
        assertEquals(signalPollResults.getRxLinkSpeed(), wifiInfo.getRxLinkSpeedMbps());
        assertEquals(sFreq, wifiInfo.getFrequency());
        verify(mPerNetwork, atLeastOnce()).getTxLinkBandwidthKbps();
        verify(mPerNetwork, atLeastOnce()).getRxLinkBandwidthKbps();
        verify(mWifiScoreCard).noteSignalPoll(any());
    }

    /**
     * Verify that RSSI polling will send RSSI broadcasts if the RSSI signal level has changed
     */
    @Test
    public void verifyConnectedModeRssiPollingWithSameSignalLevel() throws Exception {
        final long startMillis = 1_500_000_000_100L;
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, -42, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 0);
        mCmi.enableRssiPolling(true);
        connect();
        mLooper.dispatchAll();
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 3333);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        // Two broadcasts, one when enabling RSSI polling and the other for RSSI polling after
        // IP configuration success resets the current level.
        assertRssiChangeBroadcastSent(2);

        // Same RSSI should not send another broadcast
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        assertRssiChangeBroadcastSent(2);

        // Same signal level should not send another broadcast
        signalPollResults.addEntry(0, -43, 65, 54, sFreq);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        assertRssiChangeBroadcastSent(2);

        // Different signal level should send another broadcast
        signalPollResults.addEntry(0, -70, 65, 54, sFreq);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        assertRssiChangeBroadcastSent(3);
    }

    /**
     * Verify that RSSI polling with verbose logging enabled by the user will send RSSI broadcasts
     * if the RSSI has changed at all.
     */
    @Test
    public void verifyConnectedModeRssiPollingWithSameSignalLevelVerboseLoggingEnabled()
            throws Exception {
        when(mWifiGlobals.getVerboseLoggingLevel())
                .thenReturn(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED);
        final long startMillis = 1_500_000_000_100L;
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, -42, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 0);
        mCmi.enableRssiPolling(true);
        connect();
        mLooper.dispatchAll();
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 3333);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        // Two broadcasts, one when enabling RSSI polling and the other for RSSI polling after
        // IP configuration success resets the current level.
        assertRssiChangeBroadcastSent(2);

        // Same RSSI should not send another broadcast
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        assertRssiChangeBroadcastSent(2);

        // Different RSSI but same signal level should send another broadcast since we're verbose.
        signalPollResults.addEntry(0, -43, 65, 54, sFreq);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        assertRssiChangeBroadcastSent(3);
    }

    /**
     * Verify link bandwidth update in connected mode
     */
    @Test
    public void verifyConnectedModeNetworkCapabilitiesBandwidthUpdate() throws Exception {
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(40_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(50_000);
        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Collections.emptySet());
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(Process.INVALID_UID, ""));
        mCmi.enableRssiPolling(true);
        // Simulate the first connection.
        connectWithValidInitRssi(-42);

        // NetworkCapabilities should be always updated after the connection
        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);
        verify(mWifiInjector).makeWifiNetworkAgent(
                networkCapabilitiesCaptor.capture(), any(), any(), any(), any());
        NetworkCapabilities networkCapabilities = networkCapabilitiesCaptor.getValue();
        assertNotNull(networkCapabilities);
        assertEquals(-42, mWifiInfo.getRssi());
        assertEquals(40_000, networkCapabilities.getLinkUpstreamBandwidthKbps());
        assertEquals(50_000, networkCapabilities.getLinkDownstreamBandwidthKbps());
        verify(mCmi.mNetworkAgent, times(2))
                .sendNetworkCapabilitiesAndCache(networkCapabilitiesCaptor.capture());

        // Enable RSSI polling
        final long startMillis = 1_500_000_000_100L;
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, -42, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 0);
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(82_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(92_000);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 3333);
        mLooper.dispatchAll();

        // NetworkCapabilities should be updated after a big change of bandwidth
        verify(mCmi.mNetworkAgent, times(3))
                .sendNetworkCapabilitiesAndCache(networkCapabilitiesCaptor.capture());
        networkCapabilities = networkCapabilitiesCaptor.getValue();
        assertEquals(82_000, networkCapabilities.getLinkUpstreamBandwidthKbps());
        assertEquals(92_000, networkCapabilities.getLinkDownstreamBandwidthKbps());

        // NetworkCapabilities should be updated when the connected channel frequency is changed
        // For example due to AP channel switch announcement(CSA).
        WifiSignalPollResults signalPollResults1 = new WifiSignalPollResults();
        signalPollResults1.addEntry(0, -42, 65, 54, sFreq1);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults1);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();

        verify(mCmi.mNetworkAgent, times(4))
                .sendNetworkCapabilitiesAndCache(networkCapabilitiesCaptor.capture());
        assertEquals(sFreq1, mWifiInfo.getFrequency());

        // No update after a small change of bandwidth
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(72_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(82_000);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 3333);
        mLooper.dispatchAll();
        verify(mCmi.mNetworkAgent, times(4))
                .sendNetworkCapabilitiesAndCache(networkCapabilitiesCaptor.capture());
        networkCapabilities = networkCapabilitiesCaptor.getValue();
        assertEquals(82_000, networkCapabilities.getLinkUpstreamBandwidthKbps());
        assertEquals(92_000, networkCapabilities.getLinkDownstreamBandwidthKbps());
    }

    /**
     * Verify RSSI polling with verbose logging
     */
    @Test
    public void verifyConnectedModeRssiPollingWithVerboseLogging() throws Exception {
        mCmi.enableVerboseLogging(true);
        verifyConnectedModeRssiPolling();
    }

    /**
     * Verify that calls to start and stop filtering multicast packets are passed on to the IpClient
     * instance.
     */
    @Test
    public void verifyMcastLockManagerFilterControllerCallsUpdateIpClient() throws Exception {
        reset(mIpClient);
        WifiMulticastLockManager.FilterController filterController =
                mCmi.getMcastLockManagerFilterController();
        filterController.startFilteringMulticastPackets();
        verify(mIpClient).setMulticastFilter(eq(true));
        filterController.stopFilteringMulticastPackets();
        verify(mIpClient).setMulticastFilter(eq(false));
    }

    /**
     * Verifies that when
     * 1. Global feature support flag is set to false
     * 2. connected MAC randomization is on and
     * 3. macRandomizationSetting of the WifiConfiguration is RANDOMIZATION_AUTO and
     * 4. randomized MAC for the network to connect to is different from the current MAC.
     *
     * The factory MAC address is used for the connection, and no attempt is made to change it.
     */
    @Test
    public void testConnectedMacRandomizationNotSupported() throws Exception {
        // reset mWifiNative since initializeCmi() was called in setup()
        resetWifiNative();

        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(false);
        initializeCmi();
        initializeAndAddNetworkAndVerifySuccess();

        connect();
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
        verify(mWifiNative, never()).setStaMacAddress(any(), any());
        // try to retrieve factory MAC address (once at bootup, once for this connection)
        verify(mSettingsConfigStore, times(2)).get(any());
    }

    /**
     * Verifies that when
     * 1. connected MAC randomization is on and
     * 2. macRandomizationSetting of the WifiConfiguration is RANDOMIZATION_AUTO and
     * 3. current MAC set to the driver is a randomized MAC address.
     * 4. SSID of network to connect is in the MAC randomization forced disable list.
     *
     * Then the current MAC will be set to the factory MAC when CMD_START_CONNECT executes.
     */
    @Test
    public void testConnectedMacRandomizationRandomizationForceDisabled() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_LOCAL_MAC_ADDRESS.toString());
        mResources.setStringArray(R.array.config_wifiForceDisableMacRandomizationSsidList,
                new String[]{mConnectedNetwork.SSID});

        connect();
        verify(mWifiNative).setStaMacAddress(WIFI_IFACE_NAME, TEST_GLOBAL_MAC_ADDRESS);
        verify(mWifiMetrics).logStaEvent(
                eq(WIFI_IFACE_NAME), eq(StaEvent.TYPE_MAC_CHANGE), any(WifiConfiguration.class));
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), eq(Process.SYSTEM_UID));
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    @Test
    public void testConnectedMacRandomizationForceDisabledSsidPrefix() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_LOCAL_MAC_ADDRESS.toString());

        Set<String> ssidPrefixSet = new ArraySet<>();
        // Take the prefix of 2 characters, which should include the " and the first letter of
        // actual SSID
        ssidPrefixSet.add(mConnectedNetwork.SSID.substring(0, 2));
        when(mWifiGlobals.getMacRandomizationUnsupportedSsidPrefixes()).thenReturn(ssidPrefixSet);

        // Verify MAC randomization is disabled
        connect();
        verify(mWifiNative).setStaMacAddress(WIFI_IFACE_NAME, TEST_GLOBAL_MAC_ADDRESS);
        verify(mWifiMetrics).logStaEvent(
                eq(WIFI_IFACE_NAME), eq(StaEvent.TYPE_MAC_CHANGE), any(WifiConfiguration.class));
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), eq(Process.SYSTEM_UID));
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    /**
     * Verifies that when
     * 1. connected MAC randomization is on and
     * 2. macRandomizationSetting of the WifiConfiguration is RANDOMIZATION_AUTO and
     * 3. randomized MAC for the network to connect to is different from the current MAC.
     *
     * Then the current MAC gets set to the randomized MAC when CMD_START_CONNECT executes.
     */
    @Test
    public void testConnectedMacRandomizationRandomizationPersistentDifferentMac()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        connect();
        verify(mWifiNative).setStaMacAddress(WIFI_IFACE_NAME, TEST_LOCAL_MAC_ADDRESS);
        verify(mWifiMetrics).logStaEvent(
                eq(WIFI_IFACE_NAME), eq(StaEvent.TYPE_MAC_CHANGE), any(WifiConfiguration.class));
        assertEquals(TEST_LOCAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    /**
     * Verifies that when
     * 1. connected MAC randomization is on and
     * 2. macRandomizationSetting of the WifiConfiguration is RANDOMIZATION_AUTO and
     * 3. randomized MAC for the network to connect to is same as the current MAC.
     *
     * Then MAC change should not occur when CMD_START_CONNECT executes.
     */
    @Test
    public void testConnectedMacRandomizationRandomizationPersistentSameMac() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_LOCAL_MAC_ADDRESS.toString());

        connect();
        verify(mWifiNative, never()).setStaMacAddress(WIFI_IFACE_NAME, TEST_LOCAL_MAC_ADDRESS);
        verify(mWifiMetrics, never()).logStaEvent(
                any(), eq(StaEvent.TYPE_MAC_CHANGE), any(WifiConfiguration.class));
        assertEquals(TEST_LOCAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    /** Verifies connecting to secondary DBS network with Mac randomization, the MAC address is
     * the expected secondary Mac address.
     * @throws Exception
     */
    @Test
    public void testConnectedMacRandomizationRandomizationSecondaryDbs()
            throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(mClientModeManager.isSecondaryInternet()).thenReturn(true);
        when(mClientModeManager.isSecondaryInternetDbsAp()).thenReturn(true);
        initializeAndAddNetworkAndVerifySuccess();
        connect();
        verify(mWifiNative).setStaMacAddress(WIFI_IFACE_NAME, TEST_LOCAL_MAC_ADDRESS_SECONDARY_DBS);
        verify(mWifiMetrics).logStaEvent(
                eq(WIFI_IFACE_NAME), eq(StaEvent.TYPE_MAC_CHANGE), any(WifiConfiguration.class));
        assertEquals(TEST_LOCAL_MAC_ADDRESS_SECONDARY_DBS.toString(), mWifiInfo.getMacAddress());
    }

    /**
     * Verifies that when
     * 1. connected MAC randomization is on and
     * 2. macRandomizationSetting of the WifiConfiguration is RANDOMIZATION_NONE and
     * 3. current MAC address is not the factory MAC.
     *
     * Then the current MAC gets set to the factory MAC when CMD_START_CONNECT executes.
     * @throws Exception
     */
    @Test
    public void testConnectedMacRandomizationRandomizationNoneDifferentMac() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_LOCAL_MAC_ADDRESS.toString());

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(0)).thenReturn(config);

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        verify(mWifiNative).setStaMacAddress(WIFI_IFACE_NAME, TEST_GLOBAL_MAC_ADDRESS);
        verify(mWifiMetrics).logStaEvent(
                eq(WIFI_IFACE_NAME), eq(StaEvent.TYPE_MAC_CHANGE), any(WifiConfiguration.class));
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    /**
     * Verifies that when
     * 1. connected MAC randomization is on and
     * 2. macRandomizationSetting of the WifiConfiguration is RANDOMIZATION_NONE and
     *
     * Then the factory MAC should be used to connect to the network.
     * @throws Exception
     */
    @Test
    public void testConnectedMacRandomizationRandomizationNoneSameMac() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        clearInvocations(mWifiNative, mSettingsConfigStore);

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(0)).thenReturn(config);

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        verify(mSettingsConfigStore).get(WIFI_STA_FACTORY_MAC_ADDRESS);
        verify(mWifiNative, never()).getStaFactoryMacAddress(WIFI_IFACE_NAME);
        verify(mSettingsConfigStore, never()).put(
                WIFI_STA_FACTORY_MAC_ADDRESS, TEST_GLOBAL_MAC_ADDRESS.toString());

        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());

        // Now disconnect & reconnect - should use the cached factory MAC address.
        mCmi.disconnect();
        mLooper.dispatchAll();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        verify(mSettingsConfigStore, times(2)).get(WIFI_STA_FACTORY_MAC_ADDRESS);
        // No new call to retrieve & store factory MAC address.
        verify(mWifiNative, never()).getStaFactoryMacAddress(WIFI_IFACE_NAME);
        verify(mSettingsConfigStore, never()).put(
                WIFI_STA_FACTORY_MAC_ADDRESS, TEST_GLOBAL_MAC_ADDRESS.toString());
    }

    /**
     * Verifies that WifiInfo returns DEFAULT_MAC_ADDRESS as mac address when Connected MAC
     * Randomization is on and the device is not connected to a wifi network.
     */
    @Test
    public void testWifiInfoReturnDefaultMacWhenDisconnectedWithRandomization() throws Exception {
        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_LOCAL_MAC_ADDRESS.toString());

        connect();
        assertEquals(TEST_LOCAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());

        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, mWifiInfo.getMacAddress());
        assertFalse(mWifiInfo.hasRealMacAddress());
    }

    /**
     * Verifies that we don't set MAC address when config returns an invalid MAC address.
     */
    @Test
    public void testDoNotSetMacWhenInvalid() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.setRandomizedMacAddress(MacAddress.fromString(WifiInfo.DEFAULT_MAC_ADDRESS));
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(0)).thenReturn(config);

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        // setStaMacAddress is invoked once when ClientModeImpl starts to prevent leak of factory
        // MAC.
        verify(mWifiNative).setStaMacAddress(eq(WIFI_IFACE_NAME), any(MacAddress.class));
    }

    /**
     * Verify that we don't crash when WifiNative returns null as the current MAC address.
     * @throws Exception
     */
    @Test
    public void testMacRandomizationWifiNativeReturningNull() throws Exception {
        when(mWifiNative.getMacAddress(anyString())).thenReturn(null);
        initializeAndAddNetworkAndVerifySuccess();

        connect();
        verify(mWifiNative).setStaMacAddress(WIFI_IFACE_NAME, TEST_LOCAL_MAC_ADDRESS);
    }

    /**
     * Verifies that a notification is posted when a connection failure happens on a network
     * in the hotlist. Then verify that tapping on the notification launches an dialog, which
     * could be used to set the randomization setting for a network to "Trusted".
     */
    @Test
    public void testConnectionFailureSendRandomizationSettingsNotification() throws Exception {
        when(mWifiConfigManager.isInFlakyRandomizationSsidHotlist(anyInt())).thenReturn(true);
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, FRAMEWORK_NETWORK_ID, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_TIMEOUT, -1));
        mLooper.dispatchAll();

        WifiConfiguration config = mCmi.getConnectedWifiConfiguration();
        verify(mConnectionFailureNotifier)
                .showFailedToConnectDueToNoRandomizedMacSupportNotification(FRAMEWORK_NETWORK_ID);
    }

    /**
     * Verifies that a notification is not posted when a wrong password failure happens on a
     * network in the hotlist.
     */
    @Test
    public void testNotCallingIsInFlakyRandomizationSsidHotlistOnWrongPassword() throws Exception {
        when(mWifiConfigManager.isInFlakyRandomizationSsidHotlist(anyInt())).thenReturn(true);
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, FRAMEWORK_NETWORK_ID, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        mLooper.dispatchAll();

        verify(mConnectionFailureNotifier, never())
                .showFailedToConnectDueToNoRandomizedMacSupportNotification(anyInt());
    }

    /**
     * Verifies that CMD_START_CONNECT make WifiDiagnostics report
     * CONNECTION_EVENT_STARTED
     * @throws Exception
     */
    @Test
    public void testReportConnectionEventIsCalledAfterCmdStartConnect() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        verify(mWifiDiagnostics, never()).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_STARTED), any());
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_STARTED), any());
    }

    @Test
    public void testApNoResponseTriggerTimeout() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never()).handleBssidConnectionFailure(any(), any(),
                anyInt(), anyInt());

        mLooper.moveTimeForward(ClientModeImpl.CONNECTING_WATCHDOG_TIMEOUT_MS);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(any(), any(),
                eq(WifiBlocklistMonitor.REASON_FAILURE_NO_RESPONSE), anyInt());
        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_TIMEOUT), any());
    }

    @Test
    public void testApNoResponseTimerCanceledAfterConnectionSuccess() throws Exception {
        connect();
        mLooper.moveTimeForward(ClientModeImpl.CONNECTING_WATCHDOG_TIMEOUT_MS);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never()).handleBssidConnectionFailure(any(), any(),
                anyInt(), anyInt());
        verify(mWifiDiagnostics, never()).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_TIMEOUT), any());
    }

    private void verifyConnectionEventTimeoutDoesNotOccur() {
        mLooper.moveTimeForward(ClientModeImpl.CONNECTING_WATCHDOG_TIMEOUT_MS);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics, never()).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_TIMEOUT), any());
    }

    /**
     * Verifies that association failures make WifiDiagnostics report CONNECTION_EVENT_FAILED
     * and then cancel any pending timeouts.
     * Also, send connection status to {@link WifiNetworkFactory} & {@link WifiConnectivityManager}.
     * @throws Exception
     */
    @Test
    public void testReportConnectionEventIsCalledAfterAssociationFailure() throws Exception {
        mConnectedNetwork.getNetworkSelectionStatus()
                .setCandidate(getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq)
                        .getScanResult());
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.AP_UNABLE_TO_HANDLE_NEW_STA, false));
        verify(mWifiDiagnostics, never()).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_FAILED), any());
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_FAILED), any());
        verify(mWifiConnectivityManager).handleConnectionAttemptEnded(
                mClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.ASSOCIATION_REJECTION_AP_UNABLE_TO_HANDLE_NEW_STA,
                TEST_BSSID_STR,
                mTestConfig);
        verify(mWifiNetworkFactory).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION),
                eq(mTestConfig), eq(TEST_BSSID_STR), eq(WifiMetricsProto.ConnectionEvent
                        .ASSOCIATION_REJECTION_AP_UNABLE_TO_HANDLE_NEW_STA));
        verify(mWifiNetworkSuggestionsManager).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION),
                eq(mTestConfig), eq(null));
        verify(mWifiMetrics, never())
                .incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        verifyConnectionEventTimeoutDoesNotOccur();

        clearInvocations(mWifiDiagnostics, mWifiConfigManager, mWifiNetworkFactory,
                mWifiNetworkSuggestionsManager);

        // Now trigger a disconnect event from supplicant, this should be ignored since the
        // connection tracking should have already ended.
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false));
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiDiagnostics, mWifiConfigManager, mWifiNetworkFactory,
                mWifiNetworkSuggestionsManager);
    }

    /**
     * Verifies that authentication failures make WifiDiagnostics report
     * CONNECTION_EVENT_FAILED and then cancel any pending timeouts.
     * Also, send connection status to {@link WifiNetworkFactory} & {@link WifiConnectivityManager}.
     * @throws Exception
     */
    @Test
    public void testReportConnectionEventIsCalledAfterAuthenticationFailure() throws Exception {
        mConnectedNetwork.getNetworkSelectionStatus()
                .setCandidate(getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq)
                        .getScanResult());
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_FAILED), any());
        verify(mWifiConnectivityManager).handleConnectionAttemptEnded(
                mClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD, TEST_BSSID_STR,
                mTestConfig);
        verify(mWifiNetworkFactory).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE),
                eq(mTestConfig), eq(TEST_BSSID_STR), eq(WifiMetricsProto.ConnectionEvent
                        .AUTH_FAILURE_WRONG_PSWD));
        verify(mWifiNetworkSuggestionsManager).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE),
                eq(mTestConfig), eq(null));
        verify(mWifiMetrics, never())
                .incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        verifyConnectionEventTimeoutDoesNotOccur();

        clearInvocations(mWifiDiagnostics, mWifiConfigManager, mWifiNetworkFactory,
                mWifiNetworkSuggestionsManager);

        // Now trigger a disconnect event from supplicant, this should be ignored since the
        // connection tracking should have already ended.
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false));
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiDiagnostics, mWifiConfigManager, mWifiNetworkFactory,
                mWifiNetworkSuggestionsManager);
    }

    /**
     * Verify that if a NETWORK_DISCONNECTION_EVENT is received in L3ConnectedState, then an
     * abnormal disconnect is reported to WifiBlocklistMonitor.
     */
    @Test
    public void testAbnormalDisconnectNotifiesWifiBlocklistMonitor() throws Exception {
        // trigger RSSI poll to update WifiInfo
        mCmi.enableRssiPolling(true);
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, TEST_RSSI, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);

        connect();
        mLooper.dispatchAll();
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mConnectedNetwork), eq(WifiBlocklistMonitor.REASON_ABNORMAL_DISCONNECT),
                anyInt());
    }

    @Test
    public void testAbnormalDisconnectIpReachabilityLostNotifiesWifiBlocklistMonitor()
            throws Exception {
        // trigger RSSI poll to update WifiInfo
        mCmi.enableRssiPolling(true);
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, TEST_RSSI, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);

        // Connect, trigger CMD_IP_REACHABILITY_LOST and verifiy REASON_ABNORMAL_DISCONNECT is
        // sent to mWifiBlocklistMonitor
        connect();
        mLooper.dispatchAll();
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mIpClientCallback.onReachabilityLost("CMD_IP_REACHABILITY_LOST");
        mLooper.dispatchAll();

        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mConnectedNetwork), eq(WifiBlocklistMonitor.REASON_ABNORMAL_DISCONNECT),
                anyInt());
    }

    /**
     * Verify that ClientModeImpl notifies WifiBlocklistMonitor correctly when the RSSI is
     * too low.
     */
    @Test
    public void testNotifiesWifiBlocklistMonitorLowRssi() throws Exception {
        int testLowRssi = -80;
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, FRAMEWORK_NETWORK_ID, 0,
                ClientModeImpl.SUPPLICANT_BSSID_ANY);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, true));
        when(mWifiConfigManager.findScanRssi(eq(FRAMEWORK_NETWORK_ID), anyInt()))
                .thenReturn(testLowRssi);
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(testLowRssi, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(testLowRssi, TEST_BSSID_STR, sFreq).getScanResult());
        mLooper.dispatchAll();

        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(TEST_BSSID_STR, mTestConfig,
                WifiBlocklistMonitor.REASON_ASSOCIATION_TIMEOUT, testLowRssi);
    }

    /**
     * Verify that the recent failure association status is updated properly when
     * ASSOC_REJECTED_TEMPORARILY occurs.
     */
    @Test
    public void testAssocRejectedTemporarilyUpdatesRecentAssociationFailureStatus()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.ASSOC_REJECTED_TEMPORARILY,
                        false));
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_REFUSED_TEMPORARILY));
    }

    /**
     * Verify that WifiScoreCard and WifiBlocklistMonitor are notified properly when
     * disconnection occurs in middle of connection states.
     */
    @Test
    public void testDisconnectConnecting() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.ReasonCode.FOURWAY_HANDSHAKE_TIMEOUT,
                        false));
        mLooper.dispatchAll();
        verify(mWifiScoreCard).noteConnectionFailure(any(), anyInt(), anyString(), anyInt());
        verify(mWifiScoreCard).resetConnectionState(WIFI_IFACE_NAME);
        // Verify that the WifiBlocklistMonitor is notified of a non-locally generated disconnect
        // that occurred mid connection attempt.
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(anyString(), eq(mTestConfig),
                eq(WifiBlocklistMonitor.REASON_NONLOCAL_DISCONNECT_CONNECTING), anyInt());
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES));
    }

    private void triggerConnectionWithConsecutiveFailure() throws Exception {
        when(mPerNetworkRecentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_CONNECTION_FAILURE))
                .thenReturn(WifiBlocklistMonitor.NUM_CONSECUTIVE_FAILURES_PER_NETWORK_EXP_BACKOFF);
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, FRAMEWORK_NETWORK_ID, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.ReasonCode.FOURWAY_HANDSHAKE_TIMEOUT,
                        false));
        mLooper.dispatchAll();
    }

    /**
     * Verify that the WifiConfigManager is notified when a network experiences consecutive
     * connection failures.
     */
    @Test
    public void testDisableNetworkConsecutiveFailures() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        triggerConnectionWithConsecutiveFailure();

        verify(mWifiScoreCard).noteConnectionFailure(any(), anyInt(), anyString(), anyInt());
        verify(mWifiScoreCard).resetConnectionState(WIFI_IFACE_NAME);
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(anyString(), eq(mTestConfig),
                eq(WifiBlocklistMonitor.REASON_NONLOCAL_DISCONNECT_CONNECTING), anyInt());
        verify(mWifiConfigManager).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES);
    }

    @Test
    public void testDisableNetworkConsecutiveFailuresDoNotOverrideDisabledNetworks()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        // Now mock the connecting network to be already disabled
        mTestConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NETWORK_SELECTION_PERMANENTLY_DISABLED);
        triggerConnectionWithConsecutiveFailure();

        verify(mWifiScoreCard).noteConnectionFailure(any(), anyInt(), anyString(), anyInt());
        verify(mWifiScoreCard).resetConnectionState(WIFI_IFACE_NAME);
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(anyString(), eq(mTestConfig),
                eq(WifiBlocklistMonitor.REASON_NONLOCAL_DISCONNECT_CONNECTING), anyInt());

        // should not disable due to DISABLED_CONSECUTIVE_FAILURES
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES);
    }

    /**
     * Verify that a network that was successfully connected to before will get permanently disabled
     * for wrong password when the number of wrong password failures exceed a threshold.
     */
    @Test
    public void testUpgradeMultipleWrongPasswordFailuresToPermanentWrongPassword()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        startConnectSuccess();

        // mock the target network to be something that had been successfully connected before
        WifiConfiguration config = createTestNetwork(false);
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        // mock number of wrong password failures to be less than the threshold
        when(mPerNetworkRecentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE))
                .thenReturn(ClientModeImpl.THRESHOLD_TO_PERM_WRONG_PASSWORD - 1);

        // trigger the wrong password failure
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        // should not disable network permanently
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);

        // Bump up the wrong password count to reach the threshold and verify the network is
        // disabled permanently.
        when(mPerNetworkRecentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE))
                .thenReturn(ClientModeImpl.THRESHOLD_TO_PERM_WRONG_PASSWORD);

        startConnectSuccess();
        // trigger the wrong password failure
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);
    }

    /**
     * Verify that the recent failure association status is updated properly when
     * DENIED_POOR_CHANNEL_CONDITIONS occurs.
     */
    @Test
    public void testAssocRejectedPoorChannelConditionsUpdatesRecentAssociationFailureStatus()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.DENIED_POOR_CHANNEL_CONDITIONS,
                        false));
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_POOR_CHANNEL_CONDITIONS));
    }

    /**
     * Verify that the recent failure association status is updated properly when a disconnection
     * with reason code DISASSOC_AP_BUSY occurs.
     */
    @Test
    public void testNetworkDisconnectionApBusyUpdatesRecentAssociationFailureStatus()
            throws Exception {
        connect();
        // Disconnection with reason = DISASSOC_AP_BUSY
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_AP_BUSY, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_DISCONNECTION_AP_BUSY));
        verify(mWifiBlocklistMonitor).blockBssidForDurationMs(any(), any(),
                anyLong(), eq(WifiBlocklistMonitor.REASON_AP_UNABLE_TO_HANDLE_NEW_STA), anyInt());
    }

    /**
     * Verify that the recent failure association status is updated properly when a disconnection
     * with reason code DISASSOC_AP_BUSY occurs.
     */
    @Test
    public void testMidConnectionDisconnectionApBusyUpdatesRecentAssociationFailureStatus()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        startConnectSuccess();
        assertEquals("L2ConnectingState", getCurrentState().getName());

        // Disconnection with reason = DISASSOC_AP_BUSY
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 5, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_DISCONNECTION_AP_BUSY));
    }

    /**
     * Verify that the recent failure association status is updated properly when
     * ASSOCIATION_REJECTION_EVENT with OCE RSSI based association rejection attribute is received.
     */
    @Test
    public void testOceRssiBasedAssociationRejectionUpdatesRecentAssociationFailureStatus()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        initializeAndAddNetworkAndVerifySuccess();
        AssociationRejectionData assocRejectData = new AssociationRejectionData();
        assocRejectData.ssid = NativeUtil.decodeSsid(TEST_SSID);
        assocRejectData.bssid = NativeUtil.macAddressToByteArray(TEST_BSSID_STR);
        assocRejectData.statusCode =
                ISupplicantStaIfaceCallback.StatusCode.DENIED_POOR_CHANNEL_CONDITIONS;
        assocRejectData.isOceRssiBasedAssocRejectAttrPresent = true;
        assocRejectData.oceRssiBasedAssocRejectData.retryDelayS = 10;
        assocRejectData.oceRssiBasedAssocRejectData.deltaRssi = 20;
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(assocRejectData));
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_OCE_RSSI_BASED_ASSOCIATION_REJECTION));
    }

    /**
     * Verify that the recent failure association status is updated properly when
     * ASSOCIATION_REJECTION_EVENT with MBO association disallowed attribute is received.
     */
    @Test
    public void testMboAssocDisallowedIndInAssocRejectUpdatesRecentAssociationFailureStatus()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        initializeAndAddNetworkAndVerifySuccess();
        AssociationRejectionData assocRejectData = new AssociationRejectionData();
        assocRejectData.ssid = NativeUtil.decodeSsid(TEST_SSID);
        assocRejectData.bssid = NativeUtil.macAddressToByteArray(TEST_BSSID_STR);
        assocRejectData.statusCode =
                ISupplicantStaIfaceCallback.StatusCode.DENIED_POOR_CHANNEL_CONDITIONS;
        assocRejectData.isMboAssocDisallowedReasonCodePresent = true;
        assocRejectData.mboAssocDisallowedReason = MboAssocDisallowedReasonCode
                .MAX_NUM_STA_ASSOCIATED;
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(assocRejectData));
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_MAX_NUM_STA_ASSOCIATED));
    }

    /**
     * Verifies that the WifiBlocklistMonitor is notified, but the WifiLastResortWatchdog is
     * not notified of association rejections of type REASON_CODE_AP_UNABLE_TO_HANDLE_NEW_STA.
     * @throws Exception
     */
    @Test
    public void testAssociationRejectionWithReasonApUnableToHandleNewStaUpdatesWatchdog()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.AP_UNABLE_TO_HANDLE_NEW_STA, false));
        mLooper.dispatchAll();
        verify(mWifiLastResortWatchdog, never()).noteConnectionFailureAndTriggerIfNeeded(
                anyString(), anyString(), anyInt(), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_AP_UNABLE_TO_HANDLE_NEW_STA),
                anyInt());
    }

    /**
     * Verifies that the WifiBlocklistMonitor is notified, but the WifiLastResortWatchdog is
     * not notified of association rejections of type DENIED_INSUFFICIENT_BANDWIDTH.
     * @throws Exception
     */
    @Test
    public void testAssociationRejectionWithReasonDeniedInsufficientBandwidth()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR, ISupplicantStaIfaceCallback
                        .StatusCode.DENIED_INSUFFICIENT_BANDWIDTH, false));
        mLooper.dispatchAll();
        verify(mWifiLastResortWatchdog, never()).noteConnectionFailureAndTriggerIfNeeded(
                anyString(), anyString(), anyInt(), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_AP_UNABLE_TO_HANDLE_NEW_STA),
                anyInt());
    }

    /**
     * Verifies that WifiLastResortWatchdog and WifiBlocklistMonitor is notified of
     * general association rejection failures.
     * @throws Exception
     */
    @Test
    public void testAssociationRejectionUpdatesWatchdog() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID);
        config.carrierId = CARRIER_ID_1;
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false));
        mLooper.dispatchAll();
        verify(mWifiLastResortWatchdog).noteConnectionFailureAndTriggerIfNeeded(
                anyString(), anyString(), anyInt(), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_ASSOCIATION_REJECTION), anyInt());
        verify(mWifiMetrics).incrementNumOfCarrierWifiConnectionNonAuthFailure();
    }

    private void testAssociationRejectionForRole(boolean isSecondary) throws Exception {
        if (isSecondary) {
            when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
            when(mClientModeManager.isSecondaryInternet()).thenReturn(true);
        }
        when(mPerNetworkRecentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_CONNECTION_FAILURE))
                .thenReturn(WifiBlocklistMonitor.NUM_CONSECUTIVE_FAILURES_PER_NETWORK_EXP_BACKOFF);
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR,
                        ISupplicantStaIfaceCallback.StatusCode.SUPPORTED_CHANNEL_NOT_VALID, false));
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_ASSOCIATION_REJECTION),
                anyInt());
        if (isSecondary) {
            verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION);
            verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES);
            verify(mWifiLastResortWatchdog, never()).noteConnectionFailureAndTriggerIfNeeded(
                    anyString(), anyString(), anyInt(), anyBoolean());
        }
        else {
            verify(mWifiConfigManager).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION);
            verify(mWifiConfigManager).updateNetworkSelectionStatus(FRAMEWORK_NETWORK_ID,
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES);
            verify(mWifiLastResortWatchdog).noteConnectionFailureAndTriggerIfNeeded(
                    anyString(), anyString(), anyInt(), anyBoolean());
        }
    }

    @Test
    public void testAssociationRejectionPrimary() throws Exception {
        testAssociationRejectionForRole(false);
    }

    @Test
    public void testAssociationRejectionSecondary() throws Exception {
        testAssociationRejectionForRole(true);
    }

    /**
     * Verifies that WifiLastResortWatchdog is not notified of authentication failures of type
     * ERROR_AUTH_FAILURE_WRONG_PSWD.
     * @throws Exception
     */
    @Test
    public void testFailureWrongPassIsIgnoredByWatchdog() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1));
        mLooper.dispatchAll();
        verify(mWifiLastResortWatchdog, never()).noteConnectionFailureAndTriggerIfNeeded(
                anyString(), anyString(), anyInt(), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_WRONG_PASSWORD), anyInt());
    }

    /**
     * Verifies that WifiLastResortWatchdog is not notified of authentication failures of type
     * ERROR_AUTH_FAILURE_EAP_FAILURE.
     * @throws Exception
     */
    @Test
    public void testEapFailureIsIgnoredByWatchdog() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE, -1));
        mLooper.dispatchAll();
        verify(mWifiLastResortWatchdog, never()).noteConnectionFailureAndTriggerIfNeeded(
                anyString(), anyString(), anyInt(), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_EAP_FAILURE), anyInt());
    }

    /**
     * Verifies that WifiLastResortWatchdog is notified of other types of authentication failures.
     * @throws Exception
     */
    @Test
    public void testAuthenticationFailureUpdatesWatchdog() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_TIMEOUT, -1));
        mLooper.dispatchAll();
        verify(mWifiLastResortWatchdog).noteConnectionFailureAndTriggerIfNeeded(
                anyString(), anyString(), anyInt(), anyBoolean());
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(eq(TEST_BSSID_STR),
                eq(mTestConfig), eq(WifiBlocklistMonitor.REASON_AUTHENTICATION_FAILURE), anyInt());
    }

    /**
     * Verify that WifiBlocklistMonitor is notified of the SSID pre-connection so that it could
     * send down to firmware the list of blocked BSSIDs.
     */
    @Test
    public void testBssidBlocklistSentToFirmwareAfterCmdStartConnect() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        verify(mWifiBlocklistMonitor, never()).updateFirmwareRoamingConfiguration(
                Set.of(TEST_SSID));
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).updateFirmwareRoamingConfiguration(Set.of(TEST_SSID));
        // But don't expect to see connection success yet
        verify(mWifiScoreCard, never()).noteIpConfiguration(any());
        // And certainly not validation success
        verify(mWifiScoreCard, never()).noteValidationSuccess(any());
    }

    /**
     * Verifies that dhcp failures make WifiDiagnostics report CONNECTION_EVENT_FAILED and then
     * cancel any pending timeouts.
     * Also, send connection status to {@link WifiNetworkFactory} & {@link WifiConnectivityManager}.
     * @throws Exception
     */
    @Test
    public void testReportConnectionEventIsCalledAfterDhcpFailure() throws Exception {
        mConnectedNetwork.getNetworkSelectionStatus()
                .setCandidate(getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq)
                        .getScanResult());
        testDhcpFailure();
        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_FAILED), any());
        verify(mWifiConnectivityManager).handleConnectionAttemptEnded(
                mClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_BSSID_STR,
                mTestConfig);
        verify(mWifiNetworkFactory).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_DHCP), any(WifiConfiguration.class),
                eq(TEST_BSSID_STR), eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN));
        verify(mWifiNetworkSuggestionsManager).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_DHCP), any(WifiConfiguration.class),
                any(String.class));
        verify(mWifiMetrics, never())
                .incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        verifyConnectionEventTimeoutDoesNotOccur();
    }

    /**
     * Verifies that a successful validation make WifiDiagnostics report CONNECTION_EVENT_SUCCEEDED
     * and then cancel any pending timeouts.
     * Also, send connection status to {@link WifiNetworkFactory} & {@link WifiConnectivityManager}.
     */
    @Test
    public void testReportConnectionEventIsCalledAfterSuccessfulConnection() throws Exception {
        mConnectedNetwork.getNetworkSelectionStatus()
                .setCandidate(getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR1, sFreq)
                        .getScanResult());
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_SUCCEEDED), any());
        verify(mWifiConnectivityManager).handleConnectionAttemptEnded(
                mClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_BSSID_STR,
                mConnectedNetwork);
        verify(mWifiNetworkFactory).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_NONE), eq(mConnectedNetwork),
                eq(TEST_BSSID_STR), eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN));
        verify(mWifiNetworkSuggestionsManager).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_NONE), eq(mConnectedNetwork),
                any(String.class));
        verify(mCmiMonitor).onInternetValidated(mClientModeManager);
        // BSSID different, record this connection.
        verify(mWifiMetrics).incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        verifyConnectionEventTimeoutDoesNotOccur();
    }

    /**
     * Verify that score card is notified of a connection attempt
     */
    @Test
    public void testScoreCardNoteConnectionAttemptAfterCmdStartConnect() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        verify(mWifiScoreCard, never()).noteConnectionAttempt(any(), anyInt(), anyString());
        mLooper.dispatchAll();
        verify(mWifiScoreCard).noteConnectionAttempt(any(), anyInt(), anyString());
        verify(mWifiConfigManager).findScanRssi(anyInt(), anyInt());
        // But don't expect to see connection success yet
        verify(mWifiScoreCard, never()).noteIpConfiguration(any());
        // And certainly not validation success
        verify(mWifiScoreCard, never()).noteValidationSuccess(any());

    }

    /**
     * Verify that score card is notified of a successful connection
     */
    @Test
    public void testScoreCardNoteConnectionComplete() throws Exception {
        Pair<String, String> l2KeyAndCluster = Pair.create("Wad", "Gab");
        when(mWifiScoreCard.getL2KeyAndGroupHint(any())).thenReturn(l2KeyAndCluster);
        connect();
        mLooper.dispatchAll();
        verify(mWifiScoreCard).noteIpConfiguration(any());
        ArgumentCaptor<Layer2InformationParcelable> captor =
                ArgumentCaptor.forClass(Layer2InformationParcelable.class);
        verify(mIpClient, atLeastOnce()).updateLayer2Information(captor.capture());
        final Layer2InformationParcelable info = captor.getValue();
        assertEquals(info.l2Key, "Wad");
        assertEquals(info.cluster, "Gab");
    }

    /**
     * Verifies that Layer2 information is updated only when supplicant state change moves
     * to COMPLETED and on Wi-Fi disconnection. ie when device connect, roam & disconnect.
     */
    @Test
    public void testLayer2InformationUpdate() throws Exception {
        InOrder inOrder = inOrder(mIpClient);
        when(mWifiScoreCard.getL2KeyAndGroupHint(any())).thenReturn(
                Pair.create("Wad", "Gab"));
        // Simulate connection
        connect();

        inOrder.verify(mIpClient).updateLayer2Information(any());
        inOrder.verify(mIpClient).startProvisioning(any());

        // Simulate Roaming
        when(mWifiScoreCard.getL2KeyAndGroupHint(any())).thenReturn(
                Pair.create("aaa", "bbb"));
        // Now send a network connection (indicating a roam) event
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR1, false, null));
        mLooper.dispatchAll();

        inOrder.verify(mIpClient).updateLayer2Information(any());

        // Simulate disconnection
        when(mWifiScoreCard.getL2KeyAndGroupHint(any())).thenReturn(new Pair<>(null, null));
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR1, sFreq1,
                        SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        verify(mWifiScoreCard, times(3)).getL2KeyAndGroupHint(any());
        verify(mIpClient, times(3)).updateLayer2Information(any());



    }

    /**
     * Verify that score card/health monitor are notified when wifi is disabled while disconnected
     */
    @Test
    public void testScoreCardNoteWifiDisabledWhileDisconnected() throws Exception {
        // connecting and disconnecting shouldn't note wifi disabled
        disconnect();
        mLooper.dispatchAll();

        verify(mWifiScoreCard, times(1)).resetConnectionState(WIFI_IFACE_NAME);
        verify(mWifiScoreCard, never()).noteWifiDisabled(any());

        // disabling while disconnected should note wifi disabled
        mCmi.stop();
        mLooper.dispatchAll();
        verify(mWifiScoreCard, times(2)).resetConnectionState(WIFI_IFACE_NAME);
    }

    /**
     * Verify that score card/health monitor are notified when wifi is disabled while connected
     */
    @Test
    public void testScoreCardNoteWifiDisabledWhileConnected() throws Exception {
        // Get into connected state
        connect();
        mLooper.dispatchAll();
        verify(mWifiScoreCard, never()).noteWifiDisabled(any());

        // disabling while connected should note wifi disabled
        mCmi.stop();
        mLooper.dispatchAll();

        verify(mWifiScoreCard).noteWifiDisabled(any());
        verify(mWifiScoreCard).resetConnectionState(WIFI_IFACE_NAME);
    }

    /**
     * Verify that IPClient instance is shutdown when wifi is disabled.
     */
    @Test
    public void verifyIpClientShutdownWhenDisabled() throws Exception {
        mCmi.stop();
        mLooper.dispatchAll();
        verify(mIpClient).shutdown();
    }

    /**
     * Verify that WifiInfo's MAC address is updated when the state machine receives
     * NETWORK_CONNECTION_EVENT while in L3ConnectedState.
     */
    @Test
    public void verifyWifiInfoMacUpdatedWithNetworkConnectionWhileConnected() throws Exception {
        connect();
        assertEquals("L3ConnectedState", getCurrentState().getName());
        assertEquals(TEST_LOCAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());

        // Verify receiving a NETWORK_CONNECTION_EVENT changes the MAC in WifiInfo
        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_GLOBAL_MAC_ADDRESS.toString());
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    /**
     * Verify that WifiInfo's MAC address is updated when the state machine receives
     * NETWORK_CONNECTION_EVENT while in DisconnectedState.
     */
    @Test
    public void verifyWifiInfoMacUpdatedWithNetworkConnectionWhileDisconnected() throws Exception {
        disconnect();
        assertEquals("DisconnectedState", getCurrentState().getName());
        // Since MAC randomization is enabled, wifiInfo's MAC should be set to default MAC
        // when disconnect happens.
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, mWifiInfo.getMacAddress());

        setupAndStartConnectSequence(mConnectedNetwork);
        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_LOCAL_MAC_ADDRESS.toString());
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        assertEquals(TEST_LOCAL_MAC_ADDRESS.toString(), mWifiInfo.getMacAddress());
    }

    @Test
    public void internetValidationFailure_notUserSelected_expectTemporarilyDisabled()
            throws Exception {
        // Setup RSSI poll to update WifiInfo with low RSSI
        mCmi.enableRssiPolling(true);
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, RSSI_THRESHOLD_BREACH_MIN, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);

        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        WifiConfiguration currentNetwork = new WifiConfiguration();
        currentNetwork.networkId = FRAMEWORK_NETWORK_ID;
        currentNetwork.SSID = DEFAULT_TEST_SSID;
        currentNetwork.noInternetAccessExpected = false;
        currentNetwork.numNoInternetAccessReports = 1;
        currentNetwork.getNetworkSelectionStatus().setHasEverValidatedInternetAccess(true);

        // not user selected
        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(currentNetwork);
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(FRAMEWORK_NETWORK_ID))
                .thenReturn(currentNetwork);

        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(FRAMEWORK_NETWORK_ID + 1);

        // internet validation failure
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, null /* captivePortalUr; */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .incrementNetworkNoInternetAccessReports(FRAMEWORK_NETWORK_ID);
        // expect temporarily disabled
        verify(mWifiConfigManager).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NO_INTERNET_TEMPORARY);
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(TEST_BSSID_STR,
                currentNetwork, WifiBlocklistMonitor.REASON_NETWORK_VALIDATION_FAILURE,
                RSSI_THRESHOLD_BREACH_MIN);
        verify(mWifiScoreCard).noteValidationFailure(any());
    }

    @Test
    public void testMbbInternetValidationErrorExpectDisconnect() throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        // Make Before Break CMM
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagerInRole(
                ROLE_CLIENT_SECONDARY_TRANSIENT)).thenReturn(mock(ConcreteClientModeManager.class));

        // internet validation failure without detecting captive portal
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, null /* captivePortalUr; */);
        mLooper.dispatchAll();
        verify(mCmiMonitor).onInternetValidationFailed(mClientModeManager, false);
    }

    @Test
    public void testMbbNetworkUnwantedExpectDisconnect() throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        // Make Before Break CMM
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagerInRole(
                ROLE_CLIENT_SECONDARY_TRANSIENT)).thenReturn(mock(ConcreteClientModeManager.class));

        // internet was lost and network got unwanted.
        mWifiNetworkAgentCallbackCaptor.getValue().onNetworkUnwanted();
        mLooper.dispatchAll();
        // expect disconnection
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testMbbInternetValidationErrorCaptivePortalNoDisconnect() throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        // Make Before Break CMM
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        // mock internet validation failure with captive portal detected
        when(mMockUri.toString()).thenReturn("TEST_URI");
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, mMockUri);
        mLooper.dispatchAll();

        // expect no disconnection
        verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testCaptivePortalDetectedNotifiesCmiMonitor() throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        // captive portal detected
        when(mMockUri.toString()).thenReturn("TEST_URI");
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, mMockUri);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).noteCaptivePortalDetected(anyInt());
        verify(mCmiMonitor).onCaptivePortalDetected(mClientModeManager);
    }

    @Test
    public void testInternetValidationFailureUserSelectedRecently_ExpectDisabled()
            throws Exception {
        testInternetValidationUserSelectedRecently(false);
        // expect disabled because the network has never had internet validation passed
        verify(mWifiConfigManager).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NO_INTERNET_PERMANENT);
    }

    @Test
    public void testInternetValidationFailureUserSelectedRecently_ExpectNotDisabled()
            throws Exception {
        testInternetValidationUserSelectedRecently(true);
        // expect not disabled
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NO_INTERNET_PERMANENT);
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NO_INTERNET_TEMPORARY);
    }

    private void testInternetValidationUserSelectedRecently(boolean hasEverValidatedInternetAccess)
            throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        WifiConfiguration currentNetwork = new WifiConfiguration();
        currentNetwork.networkId = FRAMEWORK_NETWORK_ID;
        currentNetwork.noInternetAccessExpected = false;
        currentNetwork.numNoInternetAccessReports = 1;
        currentNetwork.getNetworkSelectionStatus().setHasEverValidatedInternetAccess(
                hasEverValidatedInternetAccess);

        // user last picked this network
        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(currentNetwork);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(FRAMEWORK_NETWORK_ID);

        // user recently picked this network
        when(mWifiConfigManager.getLastSelectedTimeStamp()).thenReturn(1234L);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1235L);

        // internet validation failure
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .incrementNetworkNoInternetAccessReports(FRAMEWORK_NETWORK_ID);
    }

    @Test
    public void testInternetValidationFailureUserSelectedTooLongAgoExpectTemporarilyDisabled()
            throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        WifiConfiguration currentNetwork = new WifiConfiguration();
        currentNetwork.networkId = FRAMEWORK_NETWORK_ID;
        currentNetwork.noInternetAccessExpected = false;
        currentNetwork.numNoInternetAccessReports = 1;
        currentNetwork.getNetworkSelectionStatus().setHasEverValidatedInternetAccess(true);

        // user last picked this network
        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(currentNetwork);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(FRAMEWORK_NETWORK_ID);

        // user picked this network a long time ago
        when(mWifiConfigManager.getLastSelectedTimeStamp()).thenReturn(1234L);
        when(mClock.getElapsedSinceBootMillis())
                .thenReturn(1235L + ClientModeImpl.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS);

        // internet validation failure
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .incrementNetworkNoInternetAccessReports(FRAMEWORK_NETWORK_ID);
        // expect temporarily disabled
        verify(mWifiConfigManager).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NO_INTERNET_TEMPORARY);
    }

    @Test
    public void testNetworkInternetValidationFailureNoInternetExpected_ExpectNotDisabled()
            throws Exception {
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        WifiConfiguration currentNetwork = new WifiConfiguration();
        currentNetwork.networkId = FRAMEWORK_NETWORK_ID;
        // no internet expected
        currentNetwork.noInternetAccessExpected = true;
        currentNetwork.numNoInternetAccessReports = 1;

        // user didn't pick this network
        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(currentNetwork);
        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(FRAMEWORK_NETWORK_ID))
                .thenReturn(currentNetwork);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(FRAMEWORK_NETWORK_ID + 1);

        // internet validation failure
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .incrementNetworkNoInternetAccessReports(FRAMEWORK_NETWORK_ID);
        // expect not disabled since no internet is expected on this network
        verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NO_INTERNET_TEMPORARY);
    }

    /**
     * Verify that we do not set the user connect choice after a successful connection if the
     * connection is not made by the user.
     */
    @Test
    public void testNonSettingsConnectionNotSetUserConnectChoice() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        connect();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiConfigManager).updateNetworkAfterConnect(eq(FRAMEWORK_NETWORK_ID),
                eq(false), eq(false),
                anyInt());
    }

    /**
     * Verify that we do not set the user connect choice after connecting to a newly added saved
     * network.
     */
    @Test
    public void testNoSetUserConnectChoiceOnFirstConnection() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        connect();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiConfigManager).updateNetworkAfterConnect(eq(FRAMEWORK_NETWORK_ID),
                eq(true), eq(false),
                anyInt());
    }

    private void verifyUserConnectChoice(boolean shouldSetUcc) throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mTestNetworkParams.hasEverConnected = true;
        connect();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiConfigManager).updateNetworkAfterConnect(eq(FRAMEWORK_NETWORK_ID),
                eq(shouldSetUcc), eq(shouldSetUcc),
                anyInt());
    }

    /**
     * Verify that on the second successful connection to a saved network we set the user connect
     * choice.
     */
    @Test
    public void testConnectionSetUserConnectChoiceOnSecondConnection() throws Exception {
        verifyUserConnectChoice(true);
    }

    @Test
    public void testDisallowUserConnectChoice() throws Exception {
        mAttributionTagForConnect = ClientModeImpl.ATTRIBUTION_TAG_DISALLOW_CONNECT_CHOICE;
        verifyUserConnectChoice(false);
    }

    /**
     * Verify that on the first successful connection to an ephemeral network we set the user
     * connect choice.
     */
    @Test
    public void testConnectionSetUserConnectChoiceOnEphemeralConfig() throws Exception {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        mConnectedNetwork.ephemeral = true;
        connect();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiConfigManager).updateNetworkAfterConnect(eq(FRAMEWORK_NETWORK_ID),
                eq(true), eq(true),
                anyInt());
    }

    /**
     * Verify that we enable the network when we detect validated internet access.
     */
    @Test
    public void verifyNetworkSelectionEnableOnInternetValidation() throws Exception {
        // Simulate the first connection.
        connect();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiBlocklistMonitor).handleDhcpProvisioningSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiBlocklistMonitor, never()).handleNetworkValidationSuccess(
                TEST_BSSID_STR, TEST_SSID);

        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(FRAMEWORK_NETWORK_ID + 1);

        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .setNetworkValidatedInternetAccess(FRAMEWORK_NETWORK_ID, true);
        verify(mWifiConfigManager).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NONE);
        verify(mWifiScoreCard).noteValidationSuccess(any());
        verify(mWifiBlocklistMonitor).handleNetworkValidationSuccess(TEST_BSSID_STR, TEST_SSID);
    }

    /**
     * Verify that the logic clears the terms and conditions URL after we got a notification that
     * the network was validated (i.e. the user accepted and internt access is available).
     */
    @Test
    public void testTermsAndConditionsClearUrlAfterNetworkValidation() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        InOrder inOrder = inOrder(mWifiNetworkAgent);

        // Simulate the first connection.
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createPasspointNetwork());
        WnmData wnmData = WnmData.createTermsAndConditionsAccetanceRequiredEvent(TEST_BSSID,
                TEST_TERMS_AND_CONDITIONS_URL);
        when(mPasspointManager.handleTermsAndConditionsEvent(eq(wnmData),
                any(WifiConfiguration.class))).thenReturn(new URL(TEST_TERMS_AND_CONDITIONS_URL));
        connect(wnmData);
        // Verify that link properties contains the T&C URL and captive is set to true
        inOrder.verify(mWifiNetworkAgent)
                .sendLinkProperties(argThat(linkProperties -> TEST_TERMS_AND_CONDITIONS_URL.equals(
                        linkProperties.getCaptivePortalData().getUserPortalUrl().toString())
                        && linkProperties.getCaptivePortalData().isCaptive()));
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiBlocklistMonitor).handleDhcpProvisioningSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiBlocklistMonitor, never())
                .handleNetworkValidationSuccess(TEST_BSSID_STR, TEST_SSID);
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(FRAMEWORK_NETWORK_ID + 1);
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .setNetworkValidatedInternetAccess(FRAMEWORK_NETWORK_ID, true);
        verify(mWifiConfigManager).updateNetworkSelectionStatus(
                FRAMEWORK_NETWORK_ID, DISABLED_NONE);
        verify(mWifiScoreCard).noteValidationSuccess(any());
        verify(mWifiBlocklistMonitor).handleNetworkValidationSuccess(TEST_BSSID_STR, TEST_SSID);

        // Now that the network has been validated, link properties must not have a T&C URL anymore
        // and captive is set to false
        inOrder.verify(mWifiNetworkAgent)
                .sendLinkProperties(argThat(linkProperties ->
                        linkProperties.getCaptivePortalData().getUserPortalUrl() == null
                                && !linkProperties.getCaptivePortalData().isCaptive()));
    }

    private void connectWithValidInitRssi(int initRssiDbm) throws Exception {
        triggerConnect();
        mWifiInfo.setRssi(initRssiDbm);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

    }

    /**
     * Verify that we set the INTERNET and bandwidth capability in the network agent when connected
     * as a result of auto-join/legacy API's. Also verify up/down stream bandwidth values when
     * Rx link speed is unavailable.
     */
    @Test
    public void verifyNetworkCapabilities() throws Exception {
        mWifiInfo.setFrequency(5825);
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(40_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(50_000);
        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Collections.emptySet());
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(Process.INVALID_UID, ""));
        // Simulate the first connection.
        connectWithValidInitRssi(-42);

        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);
        verify(mWifiInjector).makeWifiNetworkAgent(
                networkCapabilitiesCaptor.capture(), any(), any(), any(), any());

        NetworkCapabilities networkCapabilities = networkCapabilitiesCaptor.getValue();
        assertNotNull(networkCapabilities);

        // Should have internet capability.
        assertTrue(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));

        assertEquals(mConnectedNetwork.creatorUid, networkCapabilities.getOwnerUid());
        if (SdkLevel.isAtLeastT()) {
            assertEquals(Set.of(mConnectedNetwork.creatorUid),
                    networkCapabilities.getAllowedUids());
        }
        assertArrayEquals(
                new int[] {mConnectedNetwork.creatorUid},
                networkCapabilities.getAdministratorUids());

        // Should set bandwidth correctly
        assertEquals(-42, mWifiInfo.getRssi());
        assertEquals(40_000, networkCapabilities.getLinkUpstreamBandwidthKbps());
        assertEquals(50_000, networkCapabilities.getLinkDownstreamBandwidthKbps());

        // Should set band correctly.
        // There is no accessor to get the band from the WifiNetworkAgentSpecifier, so match against
        // a WifiNetworkSpecifier.
        // TODO: should there be?
        final NetworkSpecifier spec = networkCapabilities.getNetworkSpecifier();
        assertTrue(spec instanceof WifiNetworkAgentSpecifier);
        final WifiNetworkAgentSpecifier wnas = (WifiNetworkAgentSpecifier) spec;
        assertTrue(wnas.satisfiesNetworkSpecifier(
                new WifiNetworkSpecifier.Builder().setBand(ScanResult.WIFI_BAND_5_GHZ).build()));
    }

    /**
     * Verify that we don't set the INTERNET capability in the network agent when connected
     * as a result of the new network request API. Also verify up/down stream bandwidth values
     * when both Tx and Rx link speed are unavailable.
     */
    @Test
    public void verifyNetworkCapabilitiesForSpecificRequestWithInternet() throws Exception {
        mWifiInfo.setFrequency(2437);
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(30_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(40_000);
        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Set.of(TEST_UID));
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(TEST_UID, OP_PACKAGE_NAME));
        when(mWifiConnectivityManager.hasMultiInternetConnection()).thenReturn(true);
        // Simulate the first connection.
        connectWithValidInitRssi(-42);
        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        verify(mWifiInjector).makeWifiNetworkAgent(
                networkCapabilitiesCaptor.capture(), any(), any(), any(), any());

        NetworkCapabilities networkCapabilities = networkCapabilitiesCaptor.getValue();
        assertNotNull(networkCapabilities);

        // should not have internet capability.
        assertFalse(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));

        NetworkSpecifier networkSpecifier = networkCapabilities.getNetworkSpecifier();
        assertTrue(networkSpecifier instanceof WifiNetworkAgentSpecifier);
        WifiNetworkAgentSpecifier wifiNetworkAgentSpecifier =
                (WifiNetworkAgentSpecifier) networkSpecifier;

        // createNetworkAgentSpecifier does not write the BSSID to the current wifi configuration.
        WifiConfiguration expectedConfig = new WifiConfiguration(
                mCmi.getConnectedWifiConfiguration());
        expectedConfig.BSSID = TEST_BSSID_STR;
        WifiNetworkAgentSpecifier expectedWifiNetworkAgentSpecifier =
                new WifiNetworkAgentSpecifier(expectedConfig, ScanResult.WIFI_BAND_24_GHZ,
                        true /* matchLocalOnlySpecifiers */);
        assertEquals(expectedWifiNetworkAgentSpecifier, wifiNetworkAgentSpecifier);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(Set.of(new Range<Integer>(TEST_UID, TEST_UID)),
                    networkCapabilities.getUids());
        } else {
            assertEquals(TEST_UID, networkCapabilities.getRequestorUid());
            assertEquals(OP_PACKAGE_NAME, networkCapabilities.getRequestorPackageName());
        }
        assertEquals(30_000, networkCapabilities.getLinkUpstreamBandwidthKbps());
        assertEquals(40_000, networkCapabilities.getLinkDownstreamBandwidthKbps());
    }

    /**
     * Verify that we set the INTERNET capability in the network agent when connected
     * as a result of the new network which indicate the internet capabilites should be set.
     */
    @Test
    public void verifyNetworkCapabilitiesForSpecificRequest() throws Exception {
        mWifiInfo.setFrequency(2437);
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(30_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(40_000);
        when(mWifiNetworkFactory.getSpecificNetworkRequestUids(any(), any()))
                .thenReturn(Set.of(TEST_UID));
        when(mWifiNetworkFactory.getSpecificNetworkRequestUidAndPackageName(any(), any()))
                .thenReturn(Pair.create(TEST_UID, OP_PACKAGE_NAME));
        when(mWifiNetworkFactory.shouldHaveInternetCapabilities()).thenReturn(true);
        when(mWifiConnectivityManager.hasMultiInternetConnection()).thenReturn(true);
        // Simulate the first connection.
        connectWithValidInitRssi(-42);
        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        verify(mWifiInjector).makeWifiNetworkAgent(
                networkCapabilitiesCaptor.capture(), any(), any(), any(), any());

        NetworkCapabilities networkCapabilities = networkCapabilitiesCaptor.getValue();
        assertNotNull(networkCapabilities);

        // should not have internet capability.
        assertTrue(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));

        NetworkSpecifier networkSpecifier = networkCapabilities.getNetworkSpecifier();
        assertTrue(networkSpecifier instanceof WifiNetworkAgentSpecifier);
        WifiNetworkAgentSpecifier wifiNetworkAgentSpecifier =
                (WifiNetworkAgentSpecifier) networkSpecifier;

        // createNetworkAgentSpecifier does not write the BSSID to the current wifi configuration.
        WifiConfiguration expectedConfig = new WifiConfiguration(
                mCmi.getConnectedWifiConfiguration());
        expectedConfig.BSSID = TEST_BSSID_STR;
        WifiNetworkAgentSpecifier expectedWifiNetworkAgentSpecifier =
                new WifiNetworkAgentSpecifier(expectedConfig, ScanResult.WIFI_BAND_24_GHZ,
                        true /* matchLocalOnlySpecifiers */);
        assertEquals(expectedWifiNetworkAgentSpecifier, wifiNetworkAgentSpecifier);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(Set.of(new Range<Integer>(TEST_UID, TEST_UID)),
                    networkCapabilities.getUids());
        } else {
            assertEquals(TEST_UID, networkCapabilities.getRequestorUid());
            assertEquals(OP_PACKAGE_NAME, networkCapabilities.getRequestorPackageName());
        }
        assertEquals(30_000, networkCapabilities.getLinkUpstreamBandwidthKbps());
        assertEquals(40_000, networkCapabilities.getLinkDownstreamBandwidthKbps());
    }

    /**
     *  Verifies that no RSSI change broadcast should be sent
     */
    private void failOnRssiChangeBroadcast() throws Exception {
        mCmi.enableVerboseLogging(true);
        doAnswer(invocation -> {
            final Intent intent = invocation.getArgument(0);
            if (WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction())) {
                fail("Should not send RSSI_CHANGED broadcast!");
            }
            return null;
        }).when(mContext).sendBroadcastAsUser(any(), any());

        doAnswer(invocation -> {
            final Intent intent = invocation.getArgument(0);
            if (WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction())) {
                fail("Should not send RSSI_CHANGED broadcast!");
            }
            return null;
        }).when(mContext).sendBroadcastAsUser(any(), any(), anyString());

        doAnswer(invocation -> {
            final Intent intent = invocation.getArgument(0);
            if (WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction())) {
                fail("Should not send RSSI_CHANGED broadcast!");
            }
            return null;
        }).when(mContext).sendBroadcastAsUser(any(), any(), anyString(), any());
    }

    /**
     * Verifies that RSSI change broadcast is sent.
     */
    private void assertRssiChangeBroadcastSent(int times) throws Exception {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(times)).sendBroadcastAsUser(intentCaptor.capture(),
                eq(UserHandle.ALL), eq(Manifest.permission.ACCESS_WIFI_STATE), any());
        if (times > 0) {
            Intent intent = intentCaptor.getValue();
            assertNotNull(intent);
            assertEquals(WifiManager.RSSI_CHANGED_ACTION, intent.getAction());
        }
    }

    /**
     * Verify that we check for data stall during rssi poll
     * and then check that wifi link layer usage data are being updated.
     */
    @Test
    public void verifyRssiPollChecksDataStall() throws Exception {
        mCmi.enableRssiPolling(true);
        connect();

        failOnRssiChangeBroadcast();
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        WifiLinkLayerStats oldLLStats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(oldLLStats);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        WifiLinkLayerStats newLLStats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(newLLStats);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        verify(mWifiDataStall).checkDataStallAndThroughputSufficiency(WIFI_IFACE_NAME,
                mConnectionCapabilities, oldLLStats, newLLStats, mWifiInfo, TEST_TX_BYTES,
                TEST_RX_BYTES);
        verify(mWifiMetrics).incrementWifiLinkLayerUsageStats(WIFI_IFACE_NAME, newLLStats);
    }

    /**
     * Verify that we update wifi usability stats entries during rssi poll and that when we get
     * a data stall we label and save the current list of usability stats entries.
     * @throws Exception
     */
    @Test
    public void verifyRssiPollUpdatesWifiUsabilityMetrics() throws Exception {
        mCmi.enableRssiPolling(true);
        connect();

        failOnRssiChangeBroadcast();
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        WifiLinkLayerStats stats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(stats);
        when(mWifiDataStall.checkDataStallAndThroughputSufficiency(any(),
                any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(WifiIsUnusableEvent.TYPE_UNKNOWN);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        verify(mWifiMetrics).updateWifiUsabilityStatsEntries(any(), any(), eq(stats));
        verify(mWifiMetrics, never()).addToWifiUsabilityStatsList(any(),
                WifiUsabilityStats.LABEL_BAD, eq(anyInt()), eq(-1));

        when(mWifiDataStall.checkDataStallAndThroughputSufficiency(any(), any(), any(), any(),
                any(), anyLong(), anyLong()))
                .thenReturn(WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(10L);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        verify(mWifiMetrics, times(2)).updateWifiUsabilityStatsEntries(any(), any(), eq(stats));
        when(mClock.getElapsedSinceBootMillis())
                .thenReturn(10L + ClientModeImpl.DURATION_TO_WAIT_ADD_STATS_AFTER_DATA_STALL_MS);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        verify(mWifiMetrics).addToWifiUsabilityStatsList(WIFI_IFACE_NAME,
                WifiUsabilityStats.LABEL_BAD, WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX, -1);
    }

    /**
     * Verify that when ordered to setPowerSave(true) while Interface is created,
     * WifiNative is called with the right powerSave mode.
     */
    @Test
    public void verifySetPowerSaveTrueSuccess() throws Exception {
        // called once during setup()
        verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, true);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK, true));
        assertTrue(mCmi.enablePowerSave());
        verify(mWifiNative, times(3)).setPowerSave(WIFI_IFACE_NAME, true);
    }

    /**
     * Verify that when ordered to setPowerSave(false) while Interface is created,
     * WifiNative is called with the right powerSave mode.
     */
    @Test
    public void verifySetPowerSaveFalseSuccess() throws Exception {
        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_DHCP, false));
        verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);
    }

    /**
     * Verify that using setPowerSave with multiple clients (DHCP/WifiLock) operates correctly:
     * - Disable power save if ANY client disables it
     * - Enable power save only if ALL clients no longer disable it
     */
    @Test
    public void verifySetPowerSaveMultipleSources() {
        InOrder inOrderWifiNative = inOrder(mWifiNative);

        // #1 Disable-> #2 Disable-> #2 Enable-> #1 Enable
        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK, false));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_DHCP, false));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_DHCP, true));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK, true));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, true);

        // #1 Disable-> #2 Disable-> #1 Enable-> #2 Enable
        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK, false));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_DHCP, false));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK, true));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_DHCP, true));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, true);

        // #1 Disable-> #2 Disable-> global enable
        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK, false));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.setPowerSave(ClientMode.POWER_SAVE_CLIENT_DHCP, false));
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, false);

        assertTrue(mCmi.enablePowerSave());
        inOrderWifiNative.verify(mWifiNative).setPowerSave(WIFI_IFACE_NAME, true);
    }

    /**
     * Verify that we call into WifiTrafficPoller during rssi poll
     */
    @Test
    public void verifyRssiPollCallsWifiTrafficPoller() throws Exception {
        mCmi.enableRssiPolling(true);
        connect();

        verify(mWifiTrafficPoller).notifyOnDataActivity(anyLong(), anyLong());
    }

    /**
     * Verify that LinkProbeManager is updated during RSSI poll
     */
    @Test
    public void verifyRssiPollCallsLinkProbeManager() throws Exception {
        mCmi.enableRssiPolling(true);

        connect();
        // reset() should be called when RSSI polling is enabled and entering L2L3ConnectedState
        verify(mLinkProbeManager).resetOnNewConnection(); // called first time here
        verify(mLinkProbeManager, never()).resetOnScreenTurnedOn(); // not called
        verify(mLinkProbeManager).updateConnectionStats(any(), any());

        mCmi.enableRssiPolling(false);
        mLooper.dispatchAll();
        // reset() should be called when in L2L3ConnectedState (or child states) and RSSI polling
        // becomes enabled
        mCmi.enableRssiPolling(true);
        mLooper.dispatchAll();
        verify(mLinkProbeManager, times(1)).resetOnNewConnection(); // verify not called again
        verify(mLinkProbeManager).resetOnScreenTurnedOn(); // verify called here
    }

    /**
     * Verify that when ordered to setLowLatencyMode(true),
     * WifiNative is called with the right lowLatency mode.
     */
    @Test
    public void verifySetLowLatencyTrueSuccess() throws Exception {
        when(mWifiNative.setLowLatencyMode(anyBoolean())).thenReturn(true);
        assertTrue(mCmi.setLowLatencyMode(true));
        verify(mWifiNative).setLowLatencyMode(true);
    }

    /**
     * Verify that when ordered to setLowLatencyMode(false),
     * WifiNative is called with the right lowLatency mode.
     */
    @Test
    public void verifySetLowLatencyFalseSuccess() throws Exception {
        when(mWifiNative.setLowLatencyMode(anyBoolean())).thenReturn(true);
        assertTrue(mCmi.setLowLatencyMode(false));
        verify(mWifiNative).setLowLatencyMode(false);
    }

    /**
     * Verify that when WifiNative fails to set low latency mode,
     * then the call setLowLatencyMode() returns with failure,
     */
    @Test
    public void verifySetLowLatencyModeFailure() throws Exception {
        final boolean lowLatencyMode = true;
        when(mWifiNative.setLowLatencyMode(anyBoolean())).thenReturn(false);
        assertFalse(mCmi.setLowLatencyMode(lowLatencyMode));
        verify(mWifiNative).setLowLatencyMode(eq(lowLatencyMode));
    }

    /**
     * Verify the wifi module can be confiured to always get the factory MAC address from
     * WifiNative instead of using saved value in WifiConfigStore.
     */
    @Test
    public void testGetFactoryMacAddressAlwaysFromWifiNative() throws Exception {
        // Configure overlay to always retrieve the MAC address from WifiNative.
        when(mWifiGlobals.isSaveFactoryMacToConfigStoreEnabled()).thenReturn(false);
        initializeAndAddNetworkAndVerifySuccess();

        clearInvocations(mWifiNative, mSettingsConfigStore);
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mCmi.getFactoryMacAddress());
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mCmi.getFactoryMacAddress());
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mCmi.getFactoryMacAddress());

        verify(mWifiNative, times(3)).getStaFactoryMacAddress(WIFI_IFACE_NAME);
    }

    /**
     * Verify getting the factory MAC address success case.
     */
    @Test
    public void testGetFactoryMacAddressSuccess() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        clearInvocations(mWifiNative, mSettingsConfigStore);

        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mCmi.getFactoryMacAddress());
        verify(mSettingsConfigStore).get(WIFI_STA_FACTORY_MAC_ADDRESS); // try config store.
        verify(mWifiNative, never()).getStaFactoryMacAddress(WIFI_IFACE_NAME); // not native
        verify(mSettingsConfigStore, never()).put(eq(WIFI_STA_FACTORY_MAC_ADDRESS), any());

        clearInvocations(mWifiNative, mSettingsConfigStore);

        // get it again, should now use the config store MAC address, not native.
        assertEquals(TEST_GLOBAL_MAC_ADDRESS.toString(), mCmi.getFactoryMacAddress());
        verify(mSettingsConfigStore).get(WIFI_STA_FACTORY_MAC_ADDRESS);

        // Verify secondary MAC is not stored
        verify(mSettingsConfigStore, never()).put(
                eq(SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS), any());
        verify(mSettingsConfigStore, never()).get(SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS);

        // Query again as secondary STA, and then verify the result is saved to secondary.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mCmi.getFactoryMacAddress();
        verify(mWifiNative).getStaFactoryMacAddress(WIFI_IFACE_NAME);
        verify(mSettingsConfigStore).put(eq(SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS), any());
        verify(mSettingsConfigStore).get(SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS);

        verifyNoMoreInteractions(mWifiNative, mSettingsConfigStore);
    }

    /**
     * Verify getting the factory MAC address failure case.
     */
    @Test
    public void testGetFactoryMacAddressFail() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        clearInvocations(mWifiNative, mSettingsConfigStore);

        when(mSettingsConfigStore.get(WIFI_STA_FACTORY_MAC_ADDRESS)).thenReturn(null);
        when(mWifiNative.getStaFactoryMacAddress(WIFI_IFACE_NAME)).thenReturn(null);
        assertNull(mCmi.getFactoryMacAddress());
        verify(mSettingsConfigStore).get(WIFI_STA_FACTORY_MAC_ADDRESS);
        verify(mWifiNative).getStaFactoryMacAddress(WIFI_IFACE_NAME);

        verifyNoMoreInteractions(mWifiNative, mSettingsConfigStore);
    }

    /**
     * Verify that when WifiNative#getStaFactoryMacAddress fails, if the device does not support
     * MAC randomization then the currently programmed MAC address gets returned.
     */
    @Test
    public void testGetFactoryMacAddressFailWithNoMacRandomizationSupport() throws Exception {
        // reset mWifiNative since initializeCmi() was called in setup()
        resetWifiNative();

        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(false);
        initializeCmi();
        initializeAndAddNetworkAndVerifySuccess();

        clearInvocations(mWifiNative, mSettingsConfigStore);

        when(mSettingsConfigStore.get(WIFI_STA_FACTORY_MAC_ADDRESS)).thenReturn(null);
        when(mWifiNative.getStaFactoryMacAddress(WIFI_IFACE_NAME)).thenReturn(null);
        when(mWifiNative.getMacAddress(WIFI_IFACE_NAME))
                .thenReturn(TEST_DEFAULT_MAC_ADDRESS.toString());
        assertEquals(TEST_DEFAULT_MAC_ADDRESS.toString(), mCmi.getFactoryMacAddress());

        verify(mSettingsConfigStore).get(WIFI_STA_FACTORY_MAC_ADDRESS);
        verify(mWifiNative).getStaFactoryMacAddress(WIFI_IFACE_NAME);
        verify(mWifiNative).getMacAddress(WIFI_IFACE_NAME);

        verifyNoMoreInteractions(mWifiNative, mSettingsConfigStore);
    }

    /**
     * Verify the MAC address is being randomized at start to prevent leaking the factory MAC.
     */
    @Test
    public void testRandomizeMacAddressOnStart() throws Exception {
        ArgumentCaptor<MacAddress> macAddressCaptor = ArgumentCaptor.forClass(MacAddress.class);
        verify(mWifiNative).setStaMacAddress(anyString(), macAddressCaptor.capture());
        MacAddress currentMac = macAddressCaptor.getValue();

        assertNotEquals("The currently programmed MAC address should be different from the factory "
                + "MAC address after ClientModeImpl starts",
                mCmi.getFactoryMacAddress(), currentMac.toString());

        // Verify interface up will not re-randomize the MAC address again.
        mCmi.onUpChanged(true);
        verify(mWifiNative).setStaMacAddress(anyString(), macAddressCaptor.capture());
    }

    /**
     * Verify if re-randomizing had failed, then we will retry the next time the interface comes up.
     */
    @Test
    public void testRandomizeMacAddressFailedRetryOnInterfaceUp() throws Exception {
        // mock setting the MAC address to fail
        when(mWifiNative.setStaMacAddress(eq(WIFI_IFACE_NAME), anyObject())).thenReturn(false);
        initializeCmi();

        ArgumentCaptor<MacAddress> macAddressCaptor = ArgumentCaptor.forClass(MacAddress.class);
        verify(mWifiNative, times(2)).setStaMacAddress(anyString(), macAddressCaptor.capture());
        MacAddress currentMac = macAddressCaptor.getValue();

        // mock setting the MAC address to succeed
        when(mWifiNative.setStaMacAddress(eq(WIFI_IFACE_NAME), anyObject()))
                .then(new AnswerWithArguments() {
                    public boolean answer(String iface, MacAddress mac) {
                        when(mWifiNative.getMacAddress(iface)).thenReturn(mac.toString());
                        return true;
                    }
                });

        // Verify interface up will re-randomize the MAC address since the last attempt failed.
        mCmi.onUpChanged(true);
        verify(mWifiNative, times(3)).setStaMacAddress(anyString(), macAddressCaptor.capture());
        assertNotEquals("The currently programmed MAC address should be different from the factory "
                        + "MAC address after ClientModeImpl starts",
                mCmi.getFactoryMacAddress(), currentMac.toString());

        // Verify interface up will not re-randomize the MAC address since the last attempt
        // succeeded.
        mCmi.onUpChanged(true);
        verify(mWifiNative, times(3)).setStaMacAddress(anyString(), macAddressCaptor.capture());
    }

    /**
     * Verify the MAC address is being randomized at start to prevent leaking the factory MAC.
     */
    @Test
    public void testNoRandomizeMacAddressOnStartIfMacRandomizationNotEnabled() throws Exception {
        // reset mWifiNative since initializeCmi() was called in setup()
        resetWifiNative();

        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(false);
        initializeCmi();
        verify(mWifiNative, never()).setStaMacAddress(anyString(), any());
    }

    /**
     * Verify bugreport will be taken when get IP_REACHABILITY_LOST
     */
    @Test
    public void testTakebugreportbyIpReachabilityLost() throws Exception {
        connect();

        mIpClientCallback.onReachabilityLost("CMD_IP_REACHABILITY_LOST");
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).triggerBugReportDataCapture(
                eq(WifiDiagnostics.REPORT_REASON_REACHABILITY_LOST));
    }

    /**
     * Verify bugreport will be taken when get IP_REACHABILITY_FAILURE
     */
    @Test
    public void testTakeBugReportByIpReachabilityFailure() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();

        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ROAM);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiDiagnostics).triggerBugReportDataCapture(
                eq(WifiDiagnostics.REPORT_REASON_REACHABILITY_FAILURE));
    }
    /**
     * Verifies that WifiLastResortWatchdog is notified of FOURWAY_HANDSHAKE_TIMEOUT.
     */
    @Test
    public void testHandshakeTimeoutUpdatesWatchdog() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        // Verifies that WifiLastResortWatchdog won't be notified
        // by other reason code
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 2, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiLastResortWatchdog, never()).noteConnectionFailureAndTriggerIfNeeded(
                eq(TEST_SSID), eq(TEST_BSSID_STR),
                eq(WifiLastResortWatchdog.FAILURE_CODE_AUTHENTICATION), anyBoolean());

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        // Verifies that WifiLastResortWatchdog be notified
        // for FOURWAY_HANDSHAKE_TIMEOUT.
        disconnectEventInfo = new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 15, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiLastResortWatchdog).noteConnectionFailureAndTriggerIfNeeded(
                eq(TEST_SSID), eq(TEST_BSSID_STR),
                eq(WifiLastResortWatchdog.FAILURE_CODE_AUTHENTICATION), anyBoolean());

    }

    /**
     * Verify that WifiInfo is correctly populated after connection.
     */
    @Test
    public void verifyWifiInfoGetNetworkSpecifierPackageName() throws Exception {
        mConnectedNetwork.fromWifiNetworkSpecifier = true;
        mConnectedNetwork.ephemeral = true;
        mConnectedNetwork.trusted = true;
        mConnectedNetwork.creatorName = OP_PACKAGE_NAME;
        mConnectedNetwork.restricted = true;
        connect();

        assertTrue(mWifiInfo.isEphemeral());
        assertTrue(mWifiInfo.isTrusted());
        assumeTrue(mWifiInfo.isRestricted());
        assertEquals(OP_PACKAGE_NAME,
                mWifiInfo.getRequestingPackageName());
    }

    /**
     * Verify that WifiInfo is correctly populated after connection.
     */
    @Test
    public void verifyWifiInfoGetNetworkSuggestionPackageName() throws Exception {
        mConnectedNetwork.fromWifiNetworkSuggestion = true;
        mConnectedNetwork.ephemeral = true;
        mConnectedNetwork.trusted = true;
        mConnectedNetwork.creatorName = OP_PACKAGE_NAME;
        mConnectedNetwork.restricted = true;
        connect();

        assertTrue(mWifiInfo.isEphemeral());
        assertTrue(mWifiInfo.isTrusted());
        assumeTrue(mWifiInfo.isRestricted());
        assertEquals(OP_PACKAGE_NAME,
                mWifiInfo.getRequestingPackageName());
    }

    /**
     * Verify that a WifiIsUnusableEvent is logged and the current list of usability stats entries
     * are labeled and saved when receiving an IP reachability lost message.
     * @throws Exception
     */
    @Test
    public void verifyIpReachabilityLostMsgUpdatesWifiUsabilityMetrics() throws Exception {
        connect();

        mIpClientCallback.onReachabilityLost("CMD_IP_REACHABILITY_LOST");
        mLooper.dispatchAll();
        verify(mWifiMetrics).logWifiIsUnusableEvent(WIFI_IFACE_NAME,
                WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST);
        verify(mWifiMetrics).addToWifiUsabilityStatsList(WIFI_IFACE_NAME,
                WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);
    }

    /**
     * Verify that a WifiIsUnusableEvent is logged and the current list of usability stats entries
     * are labeled and saved when receiving an IP reachability failure message with non roam type.
     * @throws Exception
     */
    @Test
    public void verifyIpReachabilityFailureMsgUpdatesWifiUsabilityMetrics() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();

        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.CONFIRM);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiMetrics).logWifiIsUnusableEvent(WIFI_IFACE_NAME,
                WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST);
        verify(mWifiMetrics).addToWifiUsabilityStatsList(WIFI_IFACE_NAME,
                WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);
    }

    /**
     * Verify that a WifiIsUnusableEvent isn't logged and the current list of usability stats
     * entries are not labeled and saved when receiving an IP reachability failure message with
     * non roam type but {@link isHandleRssiOrganicKernelFailuresEnabled} is enabled.
     * @throws Exception
     */
    @Test
    public void verifyIpReachabilityFailureMsgUpdatesWifiUsabilityMetrics_enableFlag()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(true);
        assumeTrue(SdkLevel.isAtLeastU());
        connect();

        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.CONFIRM);
        mCmi.sendMessage(ClientModeImpl.CMD_IP_REACHABILITY_FAILURE, lossInfo);
        mLooper.dispatchAll();
        verify(mWifiMetrics, never()).logWifiIsUnusableEvent(WIFI_IFACE_NAME,
                WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST);
        verify(mWifiMetrics, never()).addToWifiUsabilityStatsList(WIFI_IFACE_NAME,
                WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);
    }

    /**
     * Tests that when {@link ClientModeImpl} receives a SUP_REQUEST_IDENTITY message, it responds
     * to the supplicant with the SIM identity.
     */
    @Test
    public void testSupRequestIdentity_setsIdentityResponse() throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        mConnectedNetwork.SSID = DEFAULT_TEST_SSID;
        String expectetIdentity = "13214561234567890@wlan.mnc456.mcc321.3gppnetwork.org";

        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.getSimIdentity(any()))
                .thenReturn(Pair.create(expectetIdentity, ""));

        triggerConnect();

        mCmi.sendMessage(WifiMonitor.SUP_REQUEST_IDENTITY,
                0, FRAMEWORK_NETWORK_ID, DEFAULT_TEST_SSID);
        mLooper.dispatchAll();

        verify(mWifiNative).simIdentityResponse(WIFI_IFACE_NAME,
                expectetIdentity, "");
    }

    /**
     * Verifies that WifiLastResortWatchdog is notified of DHCP failures when recevied
     * NETWORK_DISCONNECTION_EVENT while in L3ProvisioningState.
     */
    @Test
    public void testDhcpFailureUpdatesWatchdog_WhenDisconnectedWhileObtainingIpAddr()
            throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        verify(mWifiNative).removeAllNetworks(WIFI_IFACE_NAME);

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        // Verifies that WifiLastResortWatchdog be notified.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiLastResortWatchdog).noteConnectionFailureAndTriggerIfNeeded(
                eq(TEST_SSID), eq(TEST_BSSID_STR),
                eq(WifiLastResortWatchdog.FAILURE_CODE_DHCP), anyBoolean());
    }

    /**
     * Verifies that we trigger a disconnect when the {@link WifiConfigManager}.
     * OnNetworkUpdateListener#onNetworkRemoved(WifiConfiguration)} is invoked.
     */
    @Test
    public void testOnNetworkRemoved() throws Exception {
        connect();

        WifiConfiguration removedNetwork = new WifiConfiguration();
        removedNetwork.networkId = FRAMEWORK_NETWORK_ID;
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkRemoved(removedNetwork);
        }
        mLooper.dispatchAll();

        verify(mWifiNative).removeNetworkCachedData(FRAMEWORK_NETWORK_ID);
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    /**
     * Verifies that we trigger a disconnect when the {@link WifiConfigManager
     * .OnNetworkUpdateListener#onNetworkPermanentlyDisabled(WifiConfiguration, int)} is invoked.
     */
    @Test
    public void testOnNetworkPermanentlyDisabled() throws Exception {
        connect();

        WifiConfiguration disabledNetwork = new WifiConfiguration();
        disabledNetwork.networkId = FRAMEWORK_NETWORK_ID;
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkPermanentlyDisabled(disabledNetwork,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);
        }
        mLooper.dispatchAll();

        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testUnwantedDisconnectTemporarilyDisableNetwork() throws Exception {
        connect();
        when(mWifiGlobals.disableUnwantedNetworkOnLowRssi()).thenReturn(true);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(-1);
        mWifiInfo.setRssi(RSSI_THRESHOLD_MIN);
        mCmi.sendMessage(CMD_UNWANTED_NETWORK,
                ClientModeImpl.NETWORK_STATUS_UNWANTED_DISCONNECT);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(DISABLED_UNWANTED_LOW_RSSI));
    }

    /**
     * Verify that the current network is permanently disabled when
     * NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN is received and percent internet availability is
     * less than the threshold.
     */
    @Test
    public void testLowPrababilityInternetPermanentlyDisableNetwork() throws Exception {
        connect();
        when(mPerBssid.estimatePercentInternetAvailability()).thenReturn(
                ClientModeImpl.PROBABILITY_WITH_INTERNET_TO_PERMANENTLY_DISABLE_NETWORK - 1);
        mCmi.sendMessage(CMD_UNWANTED_NETWORK,
                ClientModeImpl.NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(DISABLED_NO_INTERNET_PERMANENT));
    }

    /**
     * Verify that the current network is temporarily disabled when
     * NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN is received and percent internet availability is
     * over the threshold.
     */
    @Test
    public void testHighProbabilityInternetTemporarilyDisableNetwork() throws Exception {
        connect();
        when(mPerBssid.estimatePercentInternetAvailability()).thenReturn(
                ClientModeImpl.PROBABILITY_WITH_INTERNET_TO_PERMANENTLY_DISABLE_NETWORK);
        mCmi.sendMessage(CMD_UNWANTED_NETWORK,
                ClientModeImpl.NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(DISABLED_NO_INTERNET_TEMPORARY));
    }

    /**
     * Verifies that we don't trigger a disconnect when the {@link WifiConfigManager
     * .OnNetworkUpdateListener#onNetworkPermanentlyDisabled(WifiConfiguration, int)} is invoked.
     */
    @Test
    public void testOnNetworkPermanentlyDisabledWithNoInternet() throws Exception {
        connect();

        WifiConfiguration disabledNetwork = new WifiConfiguration();
        disabledNetwork.networkId = FRAMEWORK_NETWORK_ID;
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkPermanentlyDisabled(disabledNetwork,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NO_INTERNET_PERMANENT);
        }
        mLooper.dispatchAll();

        assertEquals("L3ConnectedState", getCurrentState().getName());
    }

    /**
     * Verifies that we don't trigger a disconnect when the {@link WifiConfigManager
     * .OnNetworkUpdateListener#onNetworkTemporarilyDisabled(WifiConfiguration, int)} is invoked.
     */
    @Test
    public void testOnNetworkTemporarilyDisabledWithNoInternet() throws Exception {
        connect();

        WifiConfiguration disabledNetwork = new WifiConfiguration();
        disabledNetwork.networkId = FRAMEWORK_NETWORK_ID;
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkTemporarilyDisabled(disabledNetwork,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY);
        }
        mLooper.dispatchAll();

        assertEquals("L3ConnectedState", getCurrentState().getName());
    }

    /**
     * Verify that MboOce/WifiDataStall enable/disable methods are called in ClientMode.
     */
    @Test
    public void verifyMboOceWifiDataStallSetupInClientMode() throws Exception {
        verify(mMboOceController).enable();
        mCmi.stop();
        mLooper.dispatchAll();
        verify(mMboOceController).disable();
    }

    @Test
    public void verifyWifiMonitorHandlersDeregisteredOnStop() throws Exception {
        verify(mWifiMonitor, atLeastOnce())
                .registerHandler(eq(WIFI_IFACE_NAME), anyInt(), any());
        verify(mWifiMetrics).registerForWifiMonitorEvents(WIFI_IFACE_NAME);
        verify(mWifiLastResortWatchdog).registerForWifiMonitorEvents(WIFI_IFACE_NAME);

        mCmi.stop();
        mLooper.dispatchAll();

        verify(mWifiMonitor, atLeastOnce())
                .deregisterHandler(eq(WIFI_IFACE_NAME), anyInt(), any());
        verify(mWifiMetrics).deregisterForWifiMonitorEvents(WIFI_IFACE_NAME);
        verify(mWifiLastResortWatchdog).deregisterForWifiMonitorEvents(WIFI_IFACE_NAME);
    }

    @Test
    public void onBluetoothConnectionStateChanged() throws Exception {
        // reset mWifiNative since initializeCmi() was called in setup()
        resetWifiNative();

        when(mWifiGlobals.isBluetoothConnected()).thenReturn(false);
        initializeCmi();
        verify(mWifiNative).setBluetoothCoexistenceScanMode(any(), eq(false));

        when(mWifiGlobals.isBluetoothConnected()).thenReturn(true);
        mCmi.onBluetoothConnectionStateChanged();
        mLooper.dispatchAll();
        verify(mWifiNative).setBluetoothCoexistenceScanMode(any(), eq(true));

        when(mWifiGlobals.isBluetoothConnected()).thenReturn(false);
        mCmi.onBluetoothConnectionStateChanged();
        mLooper.dispatchAll();
        verify(mWifiNative, times(2)).setBluetoothCoexistenceScanMode(any(), eq(false));
    }

    /**
     * Test that handleBssTransitionRequest() blocklist the BSS upon
     * receiving BTM request frame that contains MBO-OCE IE with an
     * association retry delay attribute.
     */
    @Test
    public void testBtmFrameWithMboAssocretryDelayBlockListTheBssid() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        MboOceController.BtmFrameData btmFrmData = new MboOceController.BtmFrameData();

        btmFrmData.mStatus = MboOceConstants.BTM_RESPONSE_STATUS_REJECT_UNSPECIFIED;
        btmFrmData.mBssTmDataFlagsMask = MboOceConstants.BTM_DATA_FLAG_DISASSOCIATION_IMMINENT
                | MboOceConstants.BTM_DATA_FLAG_MBO_ASSOC_RETRY_DELAY_INCLUDED;
        btmFrmData.mBlockListDurationMs = 60000;

        mCmi.sendMessage(WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE, btmFrmData);
        mLooper.dispatchAll();

        verify(mWifiMetrics, times(1)).incrementSteeringRequestCountIncludingMboAssocRetryDelay();
        verify(mWifiBlocklistMonitor).blockBssidForDurationMs(eq(TEST_BSSID_STR), any(),
                eq(btmFrmData.mBlockListDurationMs), anyInt(), anyInt());
    }

    /**
     * Test that handleBssTransitionRequest() blocklist the BSS upon
     * receiving BTM request frame that contains disassociation imminent bit
     * set to 1.
     */
    @Test
    public void testBtmFrameWithDisassocImminentBitBlockListTheBssid() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        MboOceController.BtmFrameData btmFrmData = new MboOceController.BtmFrameData();

        btmFrmData.mStatus = MboOceConstants.BTM_RESPONSE_STATUS_ACCEPT;
        btmFrmData.mBssTmDataFlagsMask = MboOceConstants.BTM_DATA_FLAG_DISASSOCIATION_IMMINENT;

        mCmi.sendMessage(WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE, btmFrmData);
        mLooper.dispatchAll();

        verify(mWifiMetrics, never()).incrementSteeringRequestCountIncludingMboAssocRetryDelay();
        verify(mWifiBlocklistMonitor).blockBssidForDurationMs(eq(TEST_BSSID_STR), any(),
                eq(MboOceConstants.DEFAULT_BLOCKLIST_DURATION_MS), anyInt(), anyInt());
    }

    /**
     * Test that handleBssTransitionRequest() trigger force scan for
     * network selection when status code is REJECT.
     */
    @Test
    public void testBTMRequestRejectTriggerNetworkSelction() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        MboOceController.BtmFrameData btmFrmData = new MboOceController.BtmFrameData();

        btmFrmData.mStatus = MboOceConstants.BTM_RESPONSE_STATUS_REJECT_UNSPECIFIED;
        btmFrmData.mBssTmDataFlagsMask = MboOceConstants.BTM_DATA_FLAG_DISASSOCIATION_IMMINENT
                | MboOceConstants.BTM_DATA_FLAG_BSS_TERMINATION_INCLUDED
                | MboOceConstants.BTM_DATA_FLAG_MBO_CELL_DATA_CONNECTION_PREFERENCE_INCLUDED;
        btmFrmData.mBlockListDurationMs = 60000;

        mCmi.sendMessage(WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE, btmFrmData);
        mLooper.dispatchAll();

        verify(mWifiBlocklistMonitor, never()).blockBssidForDurationMs(eq(TEST_BSSID_STR),
                any(), eq(btmFrmData.mBlockListDurationMs), anyInt(), anyInt());
        verify(mWifiConnectivityManager).forceConnectivityScan(ClientModeImpl.WIFI_WORK_SOURCE);
        verify(mWifiMetrics, times(1)).incrementMboCellularSwitchRequestCount();
        verify(mWifiMetrics, times(1))
                .incrementForceScanCountDueToSteeringRequest();

    }

    /**
     * Test that handleBssTransitionRequest() does not trigger force
     * scan when status code is accept.
     */
    @Test
    public void testBTMRequestAcceptDoNotTriggerNetworkSelction() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        MboOceController.BtmFrameData btmFrmData = new MboOceController.BtmFrameData();

        btmFrmData.mStatus = MboOceConstants.BTM_RESPONSE_STATUS_ACCEPT;
        btmFrmData.mBssTmDataFlagsMask = MboOceConstants.BTM_DATA_FLAG_DISASSOCIATION_IMMINENT;

        mCmi.sendMessage(WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE, btmFrmData);
        mLooper.dispatchAll();

        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(ClientModeImpl.WIFI_WORK_SOURCE);
    }

    private static ScanResult.InformationElement createIE(int id, byte[] bytes) {
        ScanResult.InformationElement ie = new ScanResult.InformationElement();
        ie.id = id;
        ie.bytes = bytes;
        return ie;
    }

    /**
     * Helper function for setting up fils test.
     *
     * @param isDriverSupportFils true if driver support fils.
     * @return wifi configuration.
     */
    private WifiConfiguration setupFilsTest(boolean isDriverSupportFils) {
        WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        config.SSID = ScanResultUtil.createQuotedSsid(sFilsSsid);
        config.networkId = 1;
        config.setRandomizedMacAddress(TEST_LOCAL_MAC_ADDRESS);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;

        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(anyInt())).thenReturn(config);
        if (isDriverSupportFils) {
            when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                    WifiManager.WIFI_FEATURE_FILS_SHA256 | WifiManager.WIFI_FEATURE_FILS_SHA384);
        } else {
            when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn((long) 0);
        }

        return config;
    }

    /**
     * Helper function for setting up a scan result with FILS supported AP.
     *
     */
    private void setupFilsEnabledApInScanResult() {
        String caps = "[WPA2-EAP/SHA1+EAP/SHA256+EAP-FILS-SHA256-CCMP]"
                + "[RSN-EAP/SHA1+EAP/SHA256+EAP-FILS-SHA256-CCMP][ESS]";
        ScanResult scanResult = new ScanResult.Builder(WifiSsid.fromUtf8Text(sFilsSsid),
                TEST_BSSID_STR)
                .setHessid(1245)
                .setCaps(caps)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResult.InformationElement ie = createIE(ScanResult.InformationElement.EID_SSID,
                sFilsSsid.getBytes(StandardCharsets.UTF_8));
        scanResult.informationElements = new ScanResult.InformationElement[]{ie};
        when(mScanRequestProxy.getScanResults()).thenReturn(Arrays.asList(scanResult));
        when(mScanRequestProxy.getScanResult(eq(TEST_BSSID_STR))).thenReturn(scanResult);
    }


    /**
     * Helper function to send CMD_START_FILS_CONNECTION along with HLP IEs.
     *
     */
    private void prepareFilsHlpPktAndSendStartConnect() {
        Layer2PacketParcelable l2Packet = new Layer2PacketParcelable();
        l2Packet.dstMacAddress = TEST_GLOBAL_MAC_ADDRESS;
        l2Packet.payload = new byte[] {0x00, 0x12, 0x13, 0x00, 0x12, 0x13, 0x00, 0x12, 0x13,
                0x12, 0x13, 0x00, 0x12, 0x13, 0x00, 0x12, 0x13, 0x00, 0x12, 0x13, 0x55, 0x66};
        mIpClientCallback.onPreconnectionStart(Collections.singletonList(l2Packet));
        mLooper.dispatchAll();
        assertEquals("L2ConnectingState", mCmi.getCurrentState().getName());
    }

    /**
     * Verifies that while connecting to AP, the logic looks into the scan result and
     * looks for AP matching the network type and ssid and update the wificonfig with FILS
     * AKM if supported.
     *
     * @throws Exception
     */
    @Test
    public void testFilsAKMUpdateBeforeConnect() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiConfiguration config = setupFilsTest(true);
        setupFilsEnabledApInScanResult();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.FILS_SHA256));
        verify(mWifiNative, never()).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
    }

    /**
     * Verifies that while connecting to AP, framework updates the wifi config with
     * FILS AKM only if underlying driver support FILS feature.
     *
     * @throws Exception
     */
    @Test
    public void testFilsAkmIsNotAddedinWifiConfigIfDriverDoesNotSupportFils() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiConfiguration config = setupFilsTest(false);
        setupFilsEnabledApInScanResult();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        assertFalse(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.FILS_SHA256));
        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
    }


    /**
     * Verifies that the HLP (DHCP) packets are send to wpa_supplicant
     * prior to Fils connection.
     *
     * @throws Exception
     */
    @Test
    public void testFilsHlpUpdateBeforeFilsConnection() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiConfiguration config = setupFilsTest(true);
        setupFilsEnabledApInScanResult();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        prepareFilsHlpPktAndSendStartConnect();

        verify(mWifiNative).flushAllHlp(eq(WIFI_IFACE_NAME));
        verify(mWifiNative).addHlpReq(eq(WIFI_IFACE_NAME), any(), any());
        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
    }

    /**
     * Verifies that an association rejection in first FILS connect attempt doesn't block
     * the second connection attempt.
     *
     * @throws Exception
     */
    @Test
    public void testFilsSecondConnectAttemptIsNotBLockedAfterAssocReject() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiConfiguration config = setupFilsTest(true);
        setupFilsEnabledApInScanResult();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        prepareFilsHlpPktAndSendStartConnect();

        verify(mWifiNative, times(1)).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));

        mCmi.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT,
                new AssocRejectEventInfo(TEST_SSID, TEST_BSSID_STR, 2, false));
        mLooper.dispatchAll();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        prepareFilsHlpPktAndSendStartConnect();

        verify(mWifiNative, times(2)).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
    }

    /**
     * Verifies Fils connection.
     *
     * @throws Exception
     */
    @Test
    public void testFilsConnection() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiConfiguration config = setupFilsTest(true);
        setupFilsEnabledApInScanResult();

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        assertEquals("L2ConnectingState", mCmi.getCurrentState().getName());

        prepareFilsHlpPktAndSendStartConnect();

        verify(mWifiMetrics, times(1)).incrementConnectRequestWithFilsAkmCount();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, true, null));
        mLooper.dispatchAll();

        verify(mWifiMetrics, times(1)).incrementL2ConnectionThroughFilsAuthCount();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(sFilsSsid),
                TEST_BSSID_STR, sFreq, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertNotNull(wifiInfo);
        assertEquals(TEST_BSSID_STR, wifiInfo.getBSSID());
        assertTrue(WifiSsid.fromUtf8Text(sFilsSsid).equals(wifiInfo.getWifiSsid()));
        assertEquals("L3ConnectedState", getCurrentState().getName());
    }

    /**
     * Verifies that while connecting to secondary STA, framework doesn't change the roaming
     * configuration.
     * @throws Exception
     */
    @Test
    public void testConnectSecondaryStaNotChangeRoamingConfig() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME), eq(mTestConfig));
        verify(mWifiBlocklistMonitor, never()).setAllowlistSsids(anyString(), any());
    }

    /**
     * Tests the wifi info is updated correctly for connecting network.
     */
    @Test
    public void testWifiInfoOnConnectingNextNetwork() throws Exception {
        mConnectedNetwork.ephemeral = true;
        mConnectedNetwork.trusted = true;
        mConnectedNetwork.oemPaid = true;
        mConnectedNetwork.oemPrivate = true;
        mConnectedNetwork.carrierMerged = true;
        mConnectedNetwork.osu = true;
        mConnectedNetwork.subscriptionId = DATA_SUBID;

        triggerConnect();
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);

        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());

        // before the fist success connection, there is no valid wifi info.
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, mWifiInfo.getNetworkId());

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID,
                    TEST_WIFI_SSID, TEST_BSSID_STR, sFreq, SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        // retrieve correct wifi info on receiving the supplicant state change event.
        assertEquals(FRAMEWORK_NETWORK_ID, mWifiInfo.getNetworkId());
        assertEquals(mConnectedNetwork.ephemeral, mWifiInfo.isEphemeral());
        assertEquals(mConnectedNetwork.trusted, mWifiInfo.isTrusted());
        assertEquals(mConnectedNetwork.osu, mWifiInfo.isOsuAp());
        assertEquals(mConnectedNetwork.restricted, mWifiInfo.isRestricted());
        if (SdkLevel.isAtLeastS()) {
            assertEquals(mConnectedNetwork.oemPaid, mWifiInfo.isOemPaid());
            assertEquals(mConnectedNetwork.oemPrivate, mWifiInfo.isOemPrivate());
            assertEquals(mConnectedNetwork.carrierMerged, mWifiInfo.isCarrierMerged());
            assertEquals(DATA_SUBID, mWifiInfo.getSubscriptionId());
        }
    }

    /**
     * Verify that we disconnect when we mark a previous unmetered network metered.
     */
    @Test
    public void verifyDisconnectOnMarkingNetworkMetered() throws Exception {
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_METERED;

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_NETWORK_METERED));
    }

    /**
     * Verify that we only update capabilites when we mark a previous unmetered network metered.
     */
    @Test
    public void verifyUpdateCapabilitiesOnMarkingNetworkUnmetered() throws Exception {
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_METERED;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
        reset(mWifiNetworkAgent);

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NOT_METERED;

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());

        expectNetworkAgentUpdateCapabilities((cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
    }


    /**
     * Verify that we disconnect when we mark a previous unmetered network metered.
     */
    @Test
    public void verifyDisconnectOnMarkingNetworkAutoMeteredWithMeteredHint() throws Exception {
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NOT_METERED;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
        reset(mWifiNetworkAgent);

        // Mark network metered none.
        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NONE;

        // Set metered hint in WifiInfo (either via DHCP or ScanResult IE).
        WifiInfo wifiInfo = mWifiInfo;
        wifiInfo.setMeteredHint(true);

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    /**
     * Verify that we only update capabilites when we mark a previous unmetered network metered.
     */
    @Test
    public void verifyUpdateCapabilitiesOnMarkingNetworkAutoMeteredWithoutMeteredHint()
            throws Exception {
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_METERED;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
        reset(mWifiNetworkAgent);

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NONE;

        // Reset metered hint in WifiInfo.
        WifiInfo wifiInfo = mWifiInfo;
        wifiInfo.setMeteredHint(false);

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());

        expectNetworkAgentUpdateCapabilities((cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
    }

    /**
     * Verify that we do nothing on no metered change.
     */
    @Test
    public void verifyDoNothingMarkingNetworkAutoMeteredWithMeteredHint() throws Exception {
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_METERED;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
        reset(mWifiNetworkAgent);

        // Mark network metered none.
        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NONE;

        // Set metered hint in WifiInfo (either via DHCP or ScanResult IE).
        WifiInfo wifiInfo = mWifiInfo;
        wifiInfo.setMeteredHint(true);

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());

        verifyNoMoreInteractions(mWifiNetworkAgent);
    }

    /**
     * Verify that we do nothing on no metered change.
     */
    @Test
    public void verifyDoNothingMarkingNetworkAutoMeteredWithoutMeteredHint() throws Exception {
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NOT_METERED;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        });
        reset(mWifiNetworkAgent);

        // Mark network metered none.
        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.meteredOverride = METERED_OVERRIDE_NONE;

        // Reset metered hint in WifiInfo.
        WifiInfo wifiInfo = mWifiInfo;
        wifiInfo.setMeteredHint(false);

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());

        verifyNoMoreInteractions(mWifiNetworkAgent);
    }

    /*
     * Verify that network cached data is cleared correctly in
     * disconnected state.
     */
    @Test
    public void testNetworkCachedDataIsClearedCorrectlyInDisconnectedState() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();

        // got UNSPECIFIED during this connection attempt
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 1, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiNative, never()).removeNetworkCachedData(anyInt());

        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        mLooper.dispatchAll();
        // got 4WAY_HANDSHAKE_TIMEOUT during this connection attempt
        disconnectEventInfo = new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 15, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiNative).removeNetworkCachedData(FRAMEWORK_NETWORK_ID);
    }

    /*
     * Verify that network cached data is cleared correctly in
     * disconnected state.
     */
    @Test
    public void testNetworkCachedDataIsClearedCorrectlyInL3ProvisioningState() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        verify(mWifiNative).removeAllNetworks(WIFI_IFACE_NAME);

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        // got 4WAY_HANDSHAKE_TIMEOUT during this connection attempt
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 15, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWifiNative).removeNetworkCachedData(FRAMEWORK_NETWORK_ID);
    }

    /*
     * Verify that network cached data is NOT cleared in L3ConnectedState.
     */
    @Test
    public void testNetworkCachedDataIsClearedIf4WayHandshakeFailure() throws Exception {
        when(mWifiScoreCard.detectAbnormalDisconnection(any()))
                .thenReturn(WifiHealthMonitor.REASON_SHORT_CONNECTION_NONLOCAL);
        InOrder inOrderWifiLockManager = inOrder(mWifiLockManager);
        connect();
        inOrderWifiLockManager.verify(mWifiLockManager)
                .updateWifiClientConnected(mClientModeManager, true);

        // got 4WAY_HANDSHAKE_TIMEOUT
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 15, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).removeNetworkCachedData(anyInt());
    }

    /**
     * Verify that network cached data is cleared on changing the credential.
     */
    @Test
    public void testNetworkCachedDataIsClearedOnChangingTheCredential() throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createPasspointNetwork());
        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.enterpriseConfig.setPassword("fakePassword");

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, true);
        }
        mLooper.dispatchAll();
        verify(mWifiNative).removeNetworkCachedData(eq(oldConfig.networkId));
    }


    @Test
    public void testVerifyWifiInfoStateOnFrameworkDisconnect() throws Exception {
        connect();

        assertEquals(mWifiInfo.getSupplicantState(), SupplicantState.COMPLETED);

        // Now trigger disconnect
        mCmi.disconnect();
        mLooper.dispatchAll();

        // get disconnect event
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        assertEquals(mWifiInfo.getSupplicantState(), SupplicantState.DISCONNECTED);
    }

    @Test
    public void testVerifyWifiInfoStateOnFrameworkDisconnectButMissingDisconnectEvent()
            throws Exception {
        connect();

        assertEquals(mWifiInfo.getSupplicantState(), SupplicantState.COMPLETED);

        // Now trigger disconnect
        mCmi.disconnect();
        mLooper.dispatchAll();

        // missing disconnect event, but got supplicant state change with disconnect state instead.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, WifiSsid.fromUtf8Text(mConnectedNetwork.SSID),
                        TEST_BSSID_STR, sFreq, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        assertEquals(mWifiInfo.getSupplicantState(), SupplicantState.DISCONNECTED);
    }

    /**
     * Ensures that we only disable the current network & set MAC address only when we exit
     * ConnectingState.
     * @throws Exception
     */
    @Test
    public void testDisableNetworkOnExitingConnectingOrConnectedState() throws Exception {
        connect();
        String oldSsid = mConnectedNetwork.SSID;

        // Trigger connection to a different network
        mConnectedNetwork.SSID = "\"" + oldSsid.concat("blah") + "\"";
        mConnectedNetwork.networkId++;
        mConnectedNetwork.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        setupAndStartConnectSequence(mConnectedNetwork);

        // Send disconnect event for the old network.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(oldSsid, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertEquals("L2ConnectingState", getCurrentState().getName());
        // Since we remain in connecting state, we should not disable the network or set random MAC
        // address on disconnect.
        verify(mWifiNative, never()).disableNetwork(WIFI_IFACE_NAME);
        // Set MAC address thrice - once at bootup, twice for the 2 connections.
        verify(mWifiNative, times(3)).setStaMacAddress(eq(WIFI_IFACE_NAME), any());

        // Send disconnect event for the new network.
        disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        verify(mWifiNative).disableNetwork(WIFI_IFACE_NAME);
        // Set MAC address thrice - once at bootup, twice for the connections,
        // once for the disconnect.
        verify(mWifiNative, times(4)).setStaMacAddress(eq(WIFI_IFACE_NAME), any());
    }

    @Test
    public void testIpReachabilityFailureConfirmTriggersDisconnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // normal behavior w/o overlay
        when(mWifiGlobals.disableNudDisconnectsForWapiInSpecificCc()).thenReturn(false);
        when(mWifiCountryCode.getCountryCode()).thenReturn("CN");

        // Trigger ip reachability failure and ensure we trigger a disconnect.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.CONFIRM);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testIpReachabilityFailureConfirmDoesNotTriggersDisconnectionForWapi()
            throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createWapiPskNetwork());

        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        when(mWifiGlobals.disableNudDisconnectsForWapiInSpecificCc()).thenReturn(true);
        when(mWifiCountryCode.getCountryCode()).thenReturn("CN");

        // Trigger ip reachability failure and ensure we trigger a disconnect.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.CONFIRM);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        if (SdkLevel.isAtLeastV()) {
            verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        } else {
            verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
        }
    }

    @Test
    public void testIpReachabilityMonitorNotStartOnStaticIpConfiguration() throws Exception {
        final List<InetAddress> dnsServers = new ArrayList<>();
        dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dnsServers.add(InetAddresses.parseNumericAddress("4.4.4.4"));
        final StaticIpConfiguration staticIpConfig =
                new StaticIpConfiguration.Builder()
                        .setIpAddress(new LinkAddress("192.0.2.2/25"))
                        .setGateway(InetAddresses.parseNumericAddress("192.0.2.1"))
                        .setDnsServers(dnsServers)
                        .build();
        final IpConfiguration ipConfig = new IpConfiguration();
        ipConfig.setStaticIpConfiguration(staticIpConfig);
        ipConfig.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
        mConnectedNetwork.setIpConfiguration(ipConfig);

        triggerConnect();
        validateConnectionInfo();

        // Simulate L2 connection.
        final WifiSsid wifiSsid =
                WifiSsid.fromBytes(
                        NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(
                WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        // Simulate L3 connection.
        final DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = staticIpConfig;
        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();
        expectRegisterNetworkAgent((agentConfig) -> {}, (cap) -> {});

        verify(mIpClient).startProvisioning(mProvisioningConfigurationCaptor.capture());
        assertFalse(mProvisioningConfigurationCaptor.getValue().usingIpReachabilityMonitor);
    }

    private void doIpReachabilityFailureTest(int lossReason, boolean shouldWifiDisconnect)
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // Trigger ip reachability failure from cmd_confirm or organic kernel probe and ensure
        // wifi never disconnects and eventually state machine transits to L3ProvisioningState
        // on U and above, but wifi should still disconnect on previous platforms.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", lossReason);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        if (!shouldWifiDisconnect) {
            verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
            assertEquals("L3ProvisioningState", getCurrentState().getName());
        } else {
            verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        }
    }

    @Test
    public void testIpReachabilityFailureConfirm_enableHandleRssiOrganicKernelFailuresFlag()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(true);

        doIpReachabilityFailureTest(ReachabilityLossReason.CONFIRM,
                false /* shouldWifiDisconnect */);
    }

    @Test
    public void testIpReachabilityFailureConfirm_disableHandleRssiOrganicKernelFailuresFlag()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(false);

        // should still disconnect in following scenario
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createPskNetwork());
        when(mWifiGlobals.disableNudDisconnectsForWapiInSpecificCc()).thenReturn(true);
        when(mWifiCountryCode.getCurrentDriverCountryCode()).thenReturn("CN");

        doIpReachabilityFailureTest(ReachabilityLossReason.CONFIRM,
                true /* shouldWifiDisconnect */);
    }

    @Test
    public void testIpReachabilityFailureOrganicTriggersDisconnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // Trigger ip reachability failure and ensure we trigger a disconnect.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ORGANIC);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    /**
     * Verify that when HandleRssiOrganicKernelFailuresEnabled, multiple IP reachability failures
     * within a specified time window will lead to disconnect and network disabled.
     */
    @Test
    public void testRepeatedIpReachabilityFailureDisableNetwork() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(true);
        int failureThreshold = 5;
        int failureWindowMs = 60000;
        when(mWifiGlobals.getRepeatedNudFailuresThreshold()).thenReturn(failureThreshold);
        when(mWifiGlobals.getRepeatedNudFailuresWindowMs()).thenReturn(failureWindowMs);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);

        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        for (int i = 0; i < failureThreshold; i++) {
            // increment time outside the failure window. Failure counter should never add up and
            // thus not trigger blocking.
            when(mClock.getElapsedSinceBootMillis()).thenReturn(
                    (long) (i + 1) * (failureWindowMs + 1));
            verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
            verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(anyInt(),
                    eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_REPEATED_NUD_FAILURES));
            ReachabilityLossInfoParcelable lossInfo =
                    new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ORGANIC);
            mIpClientCallback.onReachabilityFailure(lossInfo);
            mLooper.dispatchAll();
        }

        // Now trigger NUD failure within the failure window and verify the network is blocked.
        for (int i = 0; i < failureThreshold - 1; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(
                    (long) (i + failureThreshold + 1) * (failureWindowMs));
            verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
            verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(anyInt(),
                    eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_REPEATED_NUD_FAILURES));
            ReachabilityLossInfoParcelable lossInfo =
                    new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ORGANIC);
            mIpClientCallback.onReachabilityFailure(lossInfo);
            mLooper.dispatchAll();
        }

        // Should disconnect and block network after the last iteration.
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_REPEATED_NUD_FAILURES));
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testIpReachabilityFailureOrganic_enableHandleRssiOrganicKernelFailuresFlag()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(true);

        doIpReachabilityFailureTest(ReachabilityLossReason.ORGANIC,
                false /* shouldWifiDisconnect */);
    }

    @Test
    public void testIpReachabilityFailureOrganic_disableHandleRssiOrganicKernelFailuresFlag()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(false);

        doIpReachabilityFailureTest(ReachabilityLossReason.ORGANIC,
                true /* shouldWifiDisconnect */);
    }

    @Test
    public void testIpReachabilityFailureRoamWithNullConfigDisconnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // mock the current network as removed
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(null);

        // Trigger ip reachability failure and ensure we disconnect.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ROAM);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testIpReachabilityFailureRoamL3ProvisioningState() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // Trigger ip reachability failure and ensure we do not trigger a disconnect.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ROAM);
        mIpClientCallback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent).unregisterAfterReplacement(anyInt());
        verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    @Test
    public void testIpReachabilityFailureRoamL3ProvisioningState_recreateIpClient()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // Save current IpClientCallbacks instance and verify the onProvisioningFailure
        // from this instance won't trigger wifi disconnection after recreating a new
        // IpClient and IpClientCallbacks in WaitBeforeL3ProvisioningState.
        final IpClientCallbacks callback = mIpClientCallback;

        // Trigger ip reachability failure and ensure we do not trigger a disconnect.
        ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", ReachabilityLossReason.ROAM);
        callback.onReachabilityFailure(lossInfo);
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent).unregisterAfterReplacement(anyInt());
        verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
        assertEquals("L3ProvisioningState", getCurrentState().getName());

        // Verify that onProvisioningFailure from the legacy IpClientCallbacks instance
        // doesn't trigger wifi disconnection.
        callback.onProvisioningFailure(new LinkProperties());
        mLooper.dispatchAll();
        verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);

        // Verify that onProvisioningFailure from the current IpClientCallbacks instance
        // triggers wifi disconnection.
        mIpClientCallback.onProvisioningFailure(new LinkProperties());
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    private void runProvisioningFailureCalledAfterReachabilityFailureTest(int lossReason)
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // Save current IpClientCallbacks instance and verify an immediate IP provisioning
        // failure event from this instance after IP reachability failure still triggers a
        // WiFi disconnection due to the race, for example:
        // - IpClient sends onReachabilityFailure callback, wifi module receives this callback
        //   and post CMD_IP_REACHABILITY_FAILURE, return;
        // - IpClient sends onProvisioningFailure callback, wifi module receives this callback
        //   and post CMD_IP_CONFIGURATION_LOST, return;
        // - state machine processes CMD_IP_REACHABILITY_FAILURE first and eventually transition
        //   to WaitBeforeL3ProvisioningState and recreate an IpClient instance there.
        // - state machine receives CMD_IP_CONFIGURATION_LOST from the old IpClientCallbacks
        //   instanceat at WaitBeforeL3ProvisioningState, but defer the command to its parent state
        //   until transition to L3ProvisioningState.
        // - CMD_IP_CONFIGURATION_LOST is processed at L2ConnectedState eventually, and trigger
        //   WiFi disconnection there.
        final IpClientCallbacks callback = mIpClientCallback;
        final ReachabilityLossInfoParcelable lossInfo =
                new ReachabilityLossInfoParcelable("", lossReason);
        callback.onReachabilityFailure(lossInfo);
        callback.onProvisioningFailure(new LinkProperties());
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent).unregisterAfterReplacement(anyInt());
        verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
        assertEquals("L3ProvisioningState", getCurrentState().getName());

        // Verify that onProvisioningFailure from the current IpClientCallbacks instance
        // triggers wifi disconnection.
        mIpClientCallback.onProvisioningFailure(new LinkProperties());
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
    }

    @Test
    public void testProvisioningFailureCalledAfterReachabilityFailure_postRoam()
            throws Exception {
        runProvisioningFailureCalledAfterReachabilityFailureTest(ReachabilityLossReason.ROAM);
    }

    @Test
    public void testProvisioningFailureCalledAfterReachabilityFailure_rssiConfirm()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(true);

        runProvisioningFailureCalledAfterReachabilityFailureTest(ReachabilityLossReason.CONFIRM);
    }

    @Test
    public void testProvisioningFailureCalledAfterReachabilityFailure_organicKernel()
            throws Exception {
        when(mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()).thenReturn(true);

        runProvisioningFailureCalledAfterReachabilityFailureTest(ReachabilityLossReason.ORGANIC);
    }

    @Test
    public void testIpReachabilityLostAndRoamEventsRace() throws Exception {
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { }, (cap) -> { });
        reset(mWifiNetworkAgent);

        // Trigger ip reachability loss and ensure we trigger a disconnect.
        mIpClientCallback.onReachabilityLost("CMD_IP_REACHABILITY_LOST");
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);

        // Now send a network connection (indicating a roam) event before we get the disconnect
        // event.
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        // ensure that we ignored the transient roam while we're disconnecting.
        verifyNoMoreInteractions(mWifiNetworkAgent);

        // Now send the disconnect event and ensure that we transition to "DisconnectedState".
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());
        verify(mWifiNetworkAgent).unregister();

        verifyNoMoreInteractions(mWifiNetworkAgent);
    }

    @Test
    public void testConnectionWhileDisconnecting() throws Exception {
        connect();

        // Trigger a disconnect event.
        mCmi.disconnect();
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());

        // Trigger a new connection before the NETWORK_DISCONNECTION_EVENT comes in.
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID + 1;
        setupAndStartConnectSequence(config);
        // Ensure that we triggered the connection attempt.
        validateSuccessfulConnectSequence(config);

        // Now trigger the disconnect event for the previous disconnect and ensure we handle it
        // correctly and remain in ConnectingState.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        assertEquals("L2ConnectingState", mCmi.getCurrentState().getName());
    }

    @Test
    public void testConnectionWatchdog() throws Exception {
        triggerConnect();
        Log.i(TAG, "Triggering Connect done");

        // Simulate watchdog timeout and ensure we retuned to disconnected state.
        mLooper.moveTimeForward(ClientModeImpl.CONNECTING_WATCHDOG_TIMEOUT_MS + 5L);
        mLooper.dispatchAll();

        verify(mWifiNative).disableNetwork(WIFI_IFACE_NAME);
        assertEquals("DisconnectedState", mCmi.getCurrentState().getName());
    }

    @Test
    public void testRoamAfterConnectDoesNotChangeNetworkInfoInNetworkStateChangeBroadcast()
            throws Exception {
        connect();

        // The last NETWORK_STATE_CHANGED_ACTION should be to mark the network connected.
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        verifyNetworkStateChangedBroadcast(atLeastOnce(), intentCaptor);
        Intent intent = intentCaptor.getValue();
        assertNotNull(intent);
        assertEquals(WifiManager.NETWORK_STATE_CHANGED_ACTION, intent.getAction());
        NetworkInfo networkInfo = (NetworkInfo) intent.getExtra(WifiManager.EXTRA_NETWORK_INFO);
        assertTrue(networkInfo.isConnected());

        reset(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.createContextAsUser(UserHandle.ALL, 0)).thenReturn(mUserAllContext);

        // send roam event
        mCmi.sendMessage(WifiMonitor.ASSOCIATED_BSSID_EVENT, 0, 0, TEST_BSSID_STR1);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR1, sFreq1,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        verifyNetworkStateChangedBroadcast(atLeastOnce(), intentCaptor);
        intent = intentCaptor.getValue();
        assertNotNull(intent);
        assertEquals(WifiManager.NETWORK_STATE_CHANGED_ACTION, intent.getAction());
        networkInfo = (NetworkInfo) intent.getExtra(WifiManager.EXTRA_NETWORK_INFO);
        assertTrue(networkInfo.isConnected());
    }


    /**
     * Ensure that {@link ClientModeImpl#dump(FileDescriptor, PrintWriter, String[])}
     * {@link WifiNative#getWifiLinkLayerStats(String)}, at least once before calling
     * {@link WifiScoreReport#dump(FileDescriptor, PrintWriter, String[])}.
     *
     * This ensures that WifiScoreReport will always get updated RSSI and link layer stats before
     * dumping during a bug report, no matter if the screen is on or not.
     */
    @Test
    public void testWifiScoreReportDump() throws Exception {
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        InOrder inOrder = inOrder(mWifiNative, mWifiScoreReport);
        inOrder.verify(mWifiNative, never()).getWifiLinkLayerStats(any());
        connect();

        mCmi.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), null);
        mLooper.dispatchAll();

        inOrder.verify(mWifiNative, atLeastOnce()).getWifiLinkLayerStats(any());
        inOrder.verify(mWifiScoreReport).dump(any(), any(), any());
    }

    @Test
    public void testHandleScreenChangedDontUpdateLinkLayerStatsWhenDisconnected() {
        setScreenState(true);
        setScreenState(false);
        setScreenState(true);
        verify(mWifiNative, never()).getWifiLinkLayerStats(any());
    }

    @Test
    public void clearRequestingPackageNameInWifiInfoOnConnectionFailure() throws Exception {
        mConnectedNetwork.fromWifiNetworkSpecifier = true;
        mConnectedNetwork.ephemeral = true;
        mConnectedNetwork.creatorName = OP_PACKAGE_NAME;

        triggerConnect();

        // association completed
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        assertTrue(mWifiInfo.isEphemeral());
        assertEquals(OP_PACKAGE_NAME, mWifiInfo.getRequestingPackageName());

        // fail the connection.
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(mConnectedNetwork.SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();

        assertFalse(mWifiInfo.isEphemeral());
        assertNull(mWifiInfo.getRequestingPackageName());
    }

    @Test
    public void handleAssociationRejectionWhenRoaming() throws Exception {
        connect();

        assertTrue(SupplicantState.isConnecting(mWifiInfo.getSupplicantState()));

        when(mWifiNative.roamToNetwork(any(), any())).thenReturn(true);

        // Trigger roam to a BSSID.
        mCmi.startRoamToNetwork(FRAMEWORK_NETWORK_ID, TEST_BSSID_STR1);
        mLooper.dispatchAll();


        assertEquals(TEST_BSSID_STR1, mCmi.getConnectingBssid());
        assertEquals(FRAMEWORK_NETWORK_ID, mCmi.getConnectingWifiConfiguration().networkId);

        verify(mWifiNative).roamToNetwork(any(), any());
        assertEquals("RoamingState", getCurrentState().getName());

        // fail the connection.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, 0,
                        SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        // Ensure we reset WifiInfo fields.
        assertFalse(SupplicantState.isConnecting(mWifiInfo.getSupplicantState()));
    }

    @Test
    public void testOemPaidNetworkCapability() throws Exception {
        // oemPaid introduced in S, not applicable to R
        assumeTrue(SdkLevel.isAtLeastS());
        mConnectedNetwork.oemPaid = true;
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { },
                (cap) -> {
                    assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID));
                    assertFalse(cap.hasCapability(NetworkCapabilities
                            .NET_CAPABILITY_NOT_RESTRICTED));
                });
    }
    @Test
    public void testNotOemPaidNetworkCapability() throws Exception {
        // oemPaid introduced in S, not applicable to R
        assumeTrue(SdkLevel.isAtLeastS());
        mConnectedNetwork.oemPaid = false;
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { },
                (cap) -> {
                    assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID));
                    assertTrue(cap.hasCapability(NetworkCapabilities
                            .NET_CAPABILITY_NOT_RESTRICTED));
                });
    }

    @Test
    public void testRestrictedetworkCapability() throws Exception {
        // oemPaid introduced in S, not applicable to R
        assumeTrue(SdkLevel.isAtLeastS());
        mConnectedNetwork.restricted = true;
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { },
                (cap) -> {
                    assertFalse(cap.hasCapability(NetworkCapabilities
                            .NET_CAPABILITY_NOT_RESTRICTED));
                });
    }

    @Test
    public void testOemPrivateNetworkCapability() throws Exception {
        // oemPrivate introduced in S, not applicable to R
        assumeTrue(SdkLevel.isAtLeastS());
        mConnectedNetwork.oemPrivate = true;
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { },
                (cap) -> {
                    assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE));
                    assertFalse(cap.hasCapability(NetworkCapabilities
                            .NET_CAPABILITY_NOT_RESTRICTED));
                });
    }

    @Test
    public void testNotOemPrivateNetworkCapability() throws Exception {
        // oemPrivate introduced in S, not applicable to R
        assumeTrue(SdkLevel.isAtLeastS());
        mConnectedNetwork.oemPrivate = false;
        connect();
        expectRegisterNetworkAgent((agentConfig) -> { },
                (cap) -> {
                    assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE));
                    assertTrue(cap.hasCapability(NetworkCapabilities
                            .NET_CAPABILITY_NOT_RESTRICTED));
                });
    }

    @Test
    public void testSendLinkProbeFailure() throws Exception {
        mCmi.probeLink(mLinkProbeCallback, -1);

        verify(mLinkProbeCallback).onFailure(LinkProbeCallback.LINK_PROBE_ERROR_NOT_CONNECTED);
        verify(mLinkProbeCallback, never()).onAck(anyInt());
        verify(mWifiNative, never()).probeLink(any(), any(), any(), anyInt());
    }

    @Test
    public void testSendLinkProbeSuccess() throws Exception {
        connect();

        mCmi.probeLink(mLinkProbeCallback, -1);

        verify(mWifiNative).probeLink(any(), any(), eq(mLinkProbeCallback), eq(-1));
        verify(mLinkProbeCallback, never()).onFailure(anyInt());
        verify(mLinkProbeCallback, never()).onAck(anyInt());
    }

    private void setupPasspointConnection() throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createPasspointNetwork());
        mConnectedNetwork.carrierId = CARRIER_ID_1;
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any(WifiConfiguration.class)))
                .thenReturn(DATA_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(DATA_SUBID)).thenReturn(true);
        mConnectedNetwork.enterpriseConfig.setAnonymousIdentity("");
        triggerConnect();

        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(mConnectedNetwork);
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanRequestProxy.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq));
        when(mScanDetailCache.getScanResult(TEST_BSSID_STR)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, TEST_BSSID_STR, sFreq).getScanResult());

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    /**
     * When connecting to a Passpoint network, verify that the Venue URL ANQP request is sent.
     */
    @Test
    public void testVenueUrlRequestForPasspointNetworks() throws Exception {
        setupPasspointConnection();
        verify(mPasspointManager).requestVenueUrlAnqpElement(any(ScanResult.class));
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    /**
     * Verify that the Venue URL ANQP request is not sent for non-Passpoint EAP networks
     */
    @Test
    public void testVenueUrlNotRequestedForNonPasspointNetworks() throws Exception {
        setupEapSimConnection();
        verify(mPasspointManager, never()).requestVenueUrlAnqpElement(any(ScanResult.class));
        assertEquals("L3ProvisioningState", getCurrentState().getName());
    }

    @Test
    public void testFirmwareRoam() throws Exception {
        connect();

        // Now send a network connection (indicating a roam) event
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR1, false, null));
        mLooper.dispatchAll();

        verifyNetworkStateChangedBroadcast(times(2),
                new NetworkStateChangedIntentMatcher(CONNECTED));
    }

    @Test
    public void testFirmwareRoamDisconnectSecondaryInternetOnSameBand() throws Exception {
        connect();
        // After connection, the primary should be on 2.4Ghz.
        assertTrue(mWifiInfo.is24GHz());

        // Verify no disconnection of secondary if it's on another band.
        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo secondaryWifiInfo = mock(WifiInfo.class);
        when(secondaryWifiInfo.is24GHz()).thenReturn(false);
        when(secondaryWifiInfo.is5GHz()).thenReturn(true);
        when(secondaryCmm.isConnected()).thenReturn(true);
        when(secondaryCmm.isSecondaryInternet()).thenReturn(true);
        when(secondaryCmm.getConnectionInfo()).thenReturn(secondaryWifiInfo);
        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(secondaryCmm);

        // Now send a network connection (indicating a roam) event, and verify no disconnection
        // of secondary
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR1, false, null));
        mLooper.dispatchAll();

        assertTrue(mWifiInfo.is24GHz());
        verify(secondaryCmm, never()).disconnect();

        // Set the secondary to the same band as primary and then verify a disconnect after the
        // primary roams.
        when(secondaryWifiInfo.is24GHz()).thenReturn(true);
        when(secondaryWifiInfo.is5GHz()).thenReturn(false);
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR1, false, null));
        mLooper.dispatchAll();

        assertTrue(mWifiInfo.is24GHz());
        verify(secondaryCmm).disconnect();
    }

    @Test
    public void testProvisioningUpdateAfterConnect() throws Exception {
        connect();

        // Trigger a IP params update (maybe a dhcp lease renewal).
        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        verifyNetworkStateChangedBroadcast(times(2),
                new NetworkStateChangedIntentMatcher(CONNECTED));
    }

    /**
     * Verify that the Deauth-Imminent WNM-Notification is handled by relaying to the Passpoint
     * Manager.
     */
    @Test
    public void testHandlePasspointDeauthImminentWnmNotification() throws Exception {
        setupEapSimConnection();
        WnmData wnmData = WnmData.createDeauthImminentEvent(TEST_BSSID, "", false,
                TEST_DELAY_IN_SECONDS);
        mCmi.sendMessage(WifiMonitor.HS20_DEAUTH_IMMINENT_EVENT, 0, 0, wnmData);
        mLooper.dispatchAll();
        verify(mPasspointManager).handleDeauthImminentEvent(eq(wnmData),
                any(WifiConfiguration.class));
    }

    /**
     * Verify that the network selection status will be updated and the function onEapFailure()
     * in EapFailureNotifier is called when a EAP Authentication failure is detected
     * with carrier erroe code.
     */
    @Test
    public void testCarrierEapFailure() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        WifiBlocklistMonitor.CarrierSpecificEapFailureConfig eapFailureConfig =
                new WifiBlocklistMonitor.CarrierSpecificEapFailureConfig(1, -1, true);

        startConnectSuccess();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);
        when(mEapFailureNotifier.onEapFailure(anyInt(), eq(config), anyBoolean())).thenReturn(
                eapFailureConfig);

        mCmi.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(TEST_SSID, MacAddress.fromString(TEST_BSSID_STR),
                        WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE, DEFINED_ERROR_CODE)
        );
        mLooper.dispatchAll();

        verify(mEapFailureNotifier).onEapFailure(DEFINED_ERROR_CODE, config, true);
        verify(mWifiBlocklistMonitor).loadCarrierConfigsForDisableReasonInfos(eapFailureConfig);
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_AUTHENTICATION_PRIVATE_EAP_ERROR));
    }

    /**
     * When connected to a Passpoint network, verify that the Venue URL and T&C URL are updated in
     * the {@link LinkProperties} object when provisioning complete and when link properties change
     * events are received.
     */
    @Test
    public void testVenueAndTCUrlsUpdateForPasspointNetworks() throws Exception {
        // This tests new S functionality/APIs, not applicable to R.
        assumeTrue(SdkLevel.isAtLeastS());
        setupPasspointConnection();
        when(mPasspointManager.getVenueUrl(any(ScanResult.class))).thenReturn(new URL(VENUE_URL));
        WnmData wnmData = WnmData.createTermsAndConditionsAccetanceRequiredEvent(TEST_BSSID,
                TEST_TERMS_AND_CONDITIONS_URL);
        when(mPasspointManager.handleTermsAndConditionsEvent(eq(wnmData),
                any(WifiConfiguration.class))).thenReturn(new URL(TEST_TERMS_AND_CONDITIONS_URL));
        mCmi.sendMessage(WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
                0, 0, wnmData);
        DhcpResultsParcelable dhcpResults = new DhcpResultsParcelable();
        dhcpResults.baseConfiguration = new StaticIpConfiguration();
        dhcpResults.baseConfiguration.gateway = InetAddresses.parseNumericAddress("1.2.3.4");
        dhcpResults.baseConfiguration.ipAddress =
                new LinkAddress(InetAddresses.parseNumericAddress("192.168.1.100"), 0);
        dhcpResults.baseConfiguration.dnsServers.add(InetAddresses.parseNumericAddress("8.8.8.8"));
        dhcpResults.leaseDuration = 3600;
        injectDhcpSuccess(dhcpResults);
        mCmi.mNetworkAgent = null;
        mLooper.dispatchAll();
        LinkProperties linkProperties = mock(LinkProperties.class);
        mIpClientCallback.onLinkPropertiesChange(linkProperties);
        mLooper.dispatchAll();
        verify(mPasspointManager, times(2)).getVenueUrl(any(ScanResult.class));
        final ArgumentCaptor<CaptivePortalData> captivePortalDataCaptor =
                ArgumentCaptor.forClass(CaptivePortalData.class);
        verify(linkProperties).setCaptivePortalData(captivePortalDataCaptor.capture());
        assertEquals(WifiConfigurationTestUtil.TEST_PROVIDER_FRIENDLY_NAME,
                captivePortalDataCaptor.getValue().getVenueFriendlyName());
        assertEquals(VENUE_URL, captivePortalDataCaptor.getValue().getVenueInfoUrl().toString());
        assertEquals(TEST_TERMS_AND_CONDITIONS_URL, captivePortalDataCaptor.getValue()
                .getUserPortalUrl().toString());
    }

    /**
     * Verify that the T&C WNM-Notification is handled by relaying to the Passpoint
     * Manager.
     */
    @Test
    public void testHandlePasspointTermsAndConditionsWnmNotification() throws Exception {
        setupEapSimConnection();
        WnmData wnmData = WnmData.createTermsAndConditionsAccetanceRequiredEvent(TEST_BSSID,
                TEST_TERMS_AND_CONDITIONS_URL);
        when(mPasspointManager.handleTermsAndConditionsEvent(eq(wnmData),
                any(WifiConfiguration.class))).thenReturn(new URL(TEST_TERMS_AND_CONDITIONS_URL));
        mCmi.sendMessage(WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
                0, 0, wnmData);
        mLooper.dispatchAll();
        verify(mPasspointManager).handleTermsAndConditionsEvent(eq(wnmData),
                any(WifiConfiguration.class));
        verify(mWifiNative, never()).disconnect(anyString());
    }

    /**
     * Verify that when a bad URL is received in the T&C WNM-Notification, the connection is
     * disconnected.
     */
    @Test
    public void testHandlePasspointTermsAndConditionsWnmNotificationWithBadUrl() throws Exception {
        setupEapSimConnection();
        WnmData wnmData = WnmData.createTermsAndConditionsAccetanceRequiredEvent(TEST_BSSID,
                TEST_TERMS_AND_CONDITIONS_URL);
        when(mPasspointManager.handleTermsAndConditionsEvent(eq(wnmData),
                any(WifiConfiguration.class))).thenReturn(null);
        mCmi.sendMessage(WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
                0, 0, wnmData);
        mLooper.dispatchAll();
        verify(mPasspointManager).handleTermsAndConditionsEvent(eq(wnmData),
                any(WifiConfiguration.class));
        verify(mWifiNative).disconnect(eq(WIFI_IFACE_NAME));
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_PASSPOINT_TAC));
    }

    private void verifyTransitionDisableEvent(String caps, int indication, boolean shouldUpdate)
            throws Exception {
        final int networkId = FRAMEWORK_NETWORK_ID;
        ScanResult scanResult = new ScanResult.Builder(WifiSsid.fromUtf8Text(sFilsSsid),
                TEST_BSSID_STR)
                .setHessid(1245)
                .setCaps(caps)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResult.InformationElement ie = createIE(ScanResult.InformationElement.EID_SSID,
                sFilsSsid.getBytes(StandardCharsets.UTF_8));
        scanResult.informationElements = new ScanResult.InformationElement[]{ie};
        when(mScanRequestProxy.getScanResults()).thenReturn(Arrays.asList(scanResult));
        when(mScanRequestProxy.getScanResult(eq(TEST_BSSID_STR))).thenReturn(scanResult);

        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        mCmi.sendMessage(WifiMonitor.TRANSITION_DISABLE_INDICATION,
                networkId, indication);
        mLooper.dispatchAll();

        if (shouldUpdate) {
            verify(mWifiConfigManager).updateNetworkTransitionDisable(
                    eq(networkId), eq(indication));
        } else {
            verify(mWifiConfigManager, never()).updateNetworkTransitionDisable(
                    anyInt(), anyInt());
        }
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateSaeOnlyTransitionDisableIndicationFromPskSaeBss() throws Exception {
        String caps = "[PSK][SAE]";
        int indication = WifiMonitor.TDI_USE_WPA3_PERSONAL;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateSaeOnlyTransitionDisableIndicationFromSaeBss() throws Exception {
        String caps = "[SAE]";
        int indication = WifiMonitor.TDI_USE_WPA3_PERSONAL;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testDropSaeOnlyTransitionDisableIndicationFromPskBss() throws Exception {
        String caps = "[PSK]";
        int indication = WifiMonitor.TDI_USE_WPA3_PERSONAL;
        boolean shouldUpdate = false;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateSaePkTransitionDisableIndicationFromPskSaeBss() throws Exception {
        String caps = "[PSK][SAE]";
        int indication = WifiMonitor.TDI_USE_SAE_PK;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateSaePkTransitionDisableIndicationFromSaeBss() throws Exception {
        String caps = "[SAE]";
        int indication = WifiMonitor.TDI_USE_SAE_PK;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testDropSaePkTransitionDisableIndicationFromPskBss() throws Exception {
        String caps = "[PSK]";
        int indication = WifiMonitor.TDI_USE_SAE_PK;
        boolean shouldUpdate = false;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateOweOnlyTransitionDisableIndicationFromOpenOweBss() throws Exception {
        String caps = "[OWE_TRANSITION]";
        int indication = WifiMonitor.TDI_USE_ENHANCED_OPEN;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateOweOnlyTransitionDisableIndicationFromOweBss() throws Exception {
        String caps = "[OWE]";
        int indication = WifiMonitor.TDI_USE_ENHANCED_OPEN;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testDropOweOnlyTransitionDisableIndicationFromOpenBss() throws Exception {
        String caps = "";
        int indication = WifiMonitor.TDI_USE_ENHANCED_OPEN;
        boolean shouldUpdate = false;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateWpa3EnterpriseTransitionDisableIndicationFromTransitionBss()
            throws Exception {
        String caps = "[EAP/SHA1-EAP/SHA256][RSN][MFPC]";
        int indication = WifiMonitor.TDI_USE_WPA3_ENTERPRISE;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testUpdateWpa3EnterpriseTransitionDisableIndicationFromWpa3EnterpriseBss()
            throws Exception {
        String caps = "[EAP/SHA256][RSN][MFPC][MFPR]";
        int indication = WifiMonitor.TDI_USE_WPA3_ENTERPRISE;
        boolean shouldUpdate = true;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the Transition Disable event is routed correctly.
     */
    @Test
    public void testDropWpa3EnterpriseTransitionDisableIndicationFromWpa2EnterpriseBss()
            throws Exception {
        String caps = "[EAP/SHA1]";
        int indication = WifiMonitor.TDI_USE_WPA3_ENTERPRISE;
        boolean shouldUpdate = false;
        verifyTransitionDisableEvent(caps, indication, shouldUpdate);
    }

    /**
     * Verify that the network selection status will be updated with DISABLED_NETWORK_NOT_FOUND
     * when number of NETWORK_NOT_FOUND_EVENT event reaches the threshold.
     */
    @Test
    public void testNetworkNotFoundEventUpdatesAssociationFailureStatus()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        initializeAndAddNetworkAndVerifySuccess();
        mCmi.sendMessage(ClientModeImpl.CMD_START_CONNECT, 0, 0, TEST_BSSID_STR);
        for (int i = 0; i < mWifiGlobals.getNetworkNotFoundEventThreshold(); i++) {
            mCmi.sendMessage(WifiMonitor.NETWORK_NOT_FOUND_EVENT, DEFAULT_TEST_SSID);
        }
        mLooper.dispatchAll();
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_NETWORK_NOT_FOUND));
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(anyInt(),
                eq(WifiConfiguration.RECENT_FAILURE_NETWORK_NOT_FOUND));

        verify(mWifiDiagnostics).reportConnectionEvent(
                eq(WifiDiagnostics.CONNECTION_EVENT_FAILED), any());
        verify(mWifiConnectivityManager).handleConnectionAttemptEnded(
                mClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_NETWORK_NOT_FOUND,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_BSSID_STR,
                mTestConfig);
        verify(mWifiNetworkFactory).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_NOT_FOUND),
                eq(mTestConfig), eq(TEST_BSSID_STR),
                eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN));
        verify(mWifiNetworkSuggestionsManager).handleConnectionAttemptEnded(
                eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_NOT_FOUND),
                eq(mTestConfig), eq(null));
        verify(mWifiMetrics, never())
                .incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        verifyConnectionEventTimeoutDoesNotOccur();

        clearInvocations(mWifiDiagnostics, mWifiConfigManager, mWifiNetworkFactory,
                mWifiNetworkSuggestionsManager);

        // Now trigger a disconnect event from supplicant, this should be ignored since the
        // connection tracking should have already ended.
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false));
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiDiagnostics, mWifiConfigManager, mWifiNetworkFactory,
                mWifiNetworkSuggestionsManager);
    }

    /**
     * Verify that the subscriberId will be filled in NetworkAgentConfig
     * after connecting to a merged network. And also VCN policy will be checked.
     */
    @Test
    public void triggerConnectToMergedNetwork() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        VcnManager vcnManager = mock(VcnManager.class);
        VcnNetworkPolicyResult vcnUnderlyingNetworkPolicy = mock(VcnNetworkPolicyResult.class);
        when(mContext.getSystemService(VcnManager.class)).thenReturn(vcnManager);
        ArgumentCaptor<VcnManager.VcnNetworkPolicyChangeListener> policyChangeListenerCaptor =
                ArgumentCaptor.forClass(VcnManager.VcnNetworkPolicyChangeListener.class);
        InOrder inOrder = inOrder(vcnManager, vcnUnderlyingNetworkPolicy);
        doAnswer(new AnswerWithArguments() {
            public VcnNetworkPolicyResult answer(NetworkCapabilities networkCapabilities,
                    LinkProperties linkProperties) throws Exception {
                networkCapabilities.removeCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
                when(vcnUnderlyingNetworkPolicy.getNetworkCapabilities())
                        .thenReturn(networkCapabilities);
                return vcnUnderlyingNetworkPolicy;
            }
        }).when(vcnManager).applyVcnNetworkPolicy(any(), any());
        when(vcnUnderlyingNetworkPolicy.isTeardownRequested()).thenReturn(false);

        String testSubscriberId = "TestSubscriberId";
        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mDataTelephonyManager);
        when(mDataTelephonyManager.getSubscriberId()).thenReturn(testSubscriberId);
        mConnectedNetwork.carrierMerged = true;
        mConnectedNetwork.subscriptionId = DATA_SUBID;
        connect();
        expectRegisterNetworkAgent((agentConfig) -> {
            assertEquals(testSubscriberId, agentConfig.subscriberId);
        }, (cap) -> {
                assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED));
                assertEquals(Collections.singleton(DATA_SUBID), cap.getSubscriptionIds());
            });
        // Verify VCN policy listener is registered
        inOrder.verify(vcnManager).addVcnNetworkPolicyChangeListener(any(),
                    policyChangeListenerCaptor.capture());
        assertNotNull(policyChangeListenerCaptor.getValue());

        // Verify getting new capability from VcnManager
        inOrder.verify(vcnManager).applyVcnNetworkPolicy(any(NetworkCapabilities.class),
                any(LinkProperties.class));
        inOrder.verify(vcnUnderlyingNetworkPolicy).isTeardownRequested();
        inOrder.verify(vcnUnderlyingNetworkPolicy).getNetworkCapabilities();

        // Update policy with tear down request.
        when(vcnUnderlyingNetworkPolicy.isTeardownRequested()).thenReturn(true);
        policyChangeListenerCaptor.getValue().onPolicyChanged();
        mLooper.dispatchAll();

        // The merged carrier network should be disconnected.
        inOrder.verify(vcnManager).applyVcnNetworkPolicy(any(NetworkCapabilities.class),
                any(LinkProperties.class));
        inOrder.verify(vcnUnderlyingNetworkPolicy).isTeardownRequested();
        inOrder.verify(vcnUnderlyingNetworkPolicy).getNetworkCapabilities();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_VCN_REQUEST));
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());

        // In DisconnectedState, policy update should result no capability update.
        reset(mWifiConfigManager, vcnManager);
        policyChangeListenerCaptor.getValue().onPolicyChanged();
        verifyNoMoreInteractions(mWifiConfigManager, vcnManager);
    }

    /**
     * Verify when connect to a unmerged network, will not mark it as a VCN network.
     */
    @Test
    public void triggerConnectToUnmergedNetwork() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        VcnManager vcnManager = mock(VcnManager.class);
        when(mContext.getSystemService(VcnManager.class)).thenReturn(vcnManager);
        VcnNetworkPolicyResult vcnUnderlyingNetworkPolicy = mock(VcnNetworkPolicyResult.class);
        ArgumentCaptor<VcnManager.VcnNetworkPolicyChangeListener> policyChangeListenerCaptor =
                ArgumentCaptor.forClass(VcnManager.VcnNetworkPolicyChangeListener.class);
        doAnswer(new AnswerWithArguments() {
            public VcnNetworkPolicyResult answer(NetworkCapabilities networkCapabilities,
                    LinkProperties linkProperties) throws Exception {
                networkCapabilities.removeCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
                when(vcnUnderlyingNetworkPolicy.getNetworkCapabilities())
                        .thenReturn(networkCapabilities);
                return vcnUnderlyingNetworkPolicy;
            }
        }).when(vcnManager).applyVcnNetworkPolicy(any(), any());
        when(vcnUnderlyingNetworkPolicy.isTeardownRequested()).thenReturn(false);

        String testSubscriberId = "TestSubscriberId";
        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mDataTelephonyManager);
        when(mDataTelephonyManager.getSubscriberId()).thenReturn(testSubscriberId);
        connect();
        expectRegisterNetworkAgent((agentConfig) -> {
            assertEquals(null, agentConfig.subscriberId);
        }, (cap) -> {
                assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED));
                assertTrue(cap.getSubscriptionIds().isEmpty());
            });

        // Verify VCN policy listener is registered
        verify(vcnManager).addVcnNetworkPolicyChangeListener(any(),
                policyChangeListenerCaptor.capture());
        assertNotNull(policyChangeListenerCaptor.getValue());

        policyChangeListenerCaptor.getValue().onPolicyChanged();
        mLooper.dispatchAll();

        verifyNoMoreInteractions(vcnManager, vcnUnderlyingNetworkPolicy);
    }

    /**
     * Verifies that we trigger a disconnect when the {@link WifiConfigManager}.
     * OnNetworkUpdateListener#onNetworkRemoved(WifiConfiguration)} is invoked.
     */
    @Test
    public void testOnCarrierOffloadDisabled() throws Exception {
        mConnectedNetwork.subscriptionId = DATA_SUBID;
        connect();

        mOffloadDisabledListenerArgumentCaptor.getValue()
                .onCarrierOffloadDisabled(DATA_SUBID, false);
        mLooper.dispatchAll();

        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_CARRIER_OFFLOAD_DISABLED));
        verify(mWifiConnectivityManager).clearCachedCandidates();
    }

    @Test
    public void testPacketFilter() throws Exception {
        connect();

        verify(mIpClient).startProvisioning(mProvisioningConfigurationCaptor.capture());
        assertEquals(APF_CAP, mProvisioningConfigurationCaptor.getValue().apfCapabilities);

        byte[] filter = new byte[20];
        new Random().nextBytes(filter);
        mIpClientCallback.installPacketFilter(filter);
        mLooper.dispatchAll();

        verify(mWifiNative).installPacketFilter(WIFI_IFACE_NAME, filter);

        when(mWifiNative.readPacketFilter(WIFI_IFACE_NAME)).thenReturn(filter);
        mIpClientCallback.startReadPacketFilter();
        mLooper.dispatchAll();
        verify(mIpClient).readPacketFilterComplete(filter);
        verify(mWifiNative).readPacketFilter(WIFI_IFACE_NAME);
    }

    @Test
    public void testPacketFilterOnSecondarySupported() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableApfOnNonPrimarySta, true);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();

        verify(mIpClient).startProvisioning(mProvisioningConfigurationCaptor.capture());
        assertEquals(APF_CAP, mProvisioningConfigurationCaptor.getValue().apfCapabilities);
    }

    @Test
    public void testPacketFilterOnSecondaryNotSupported() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableApfOnNonPrimarySta, false);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();

        verify(mIpClient).startProvisioning(mProvisioningConfigurationCaptor.capture());
        assertNull(mProvisioningConfigurationCaptor.getValue().apfCapabilities);
    }

    @Test
    public void testPacketFilterOnRoleChangeOnSecondaryCmm() throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();

        verify(mWifiScoreReport).onRoleChanged(ROLE_CLIENT_PRIMARY);

        byte[] filter = new byte[20];
        new Random().nextBytes(filter);
        mIpClientCallback.installPacketFilter(filter);
        mLooper.dispatchAll();

        // packet filter will not be installed if the secondary STA doesn't support APF.
        verify(mWifiNative, never()).installPacketFilter(WIFI_IFACE_NAME, filter);

        mIpClientCallback.startReadPacketFilter();
        mLooper.dispatchAll();
        // Return null as packet filter is not installed.
        verify(mIpClient).readPacketFilterComplete(eq(null));
        verify(mWifiNative, never()).readPacketFilter(WIFI_IFACE_NAME);

        // Now invoke role change, that should apply the APF
        when(mWifiNative.readPacketFilter(WIFI_IFACE_NAME)).thenReturn(filter);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mCmi.onRoleChanged();
        mIpClientCallback.installPacketFilter(filter);
        mLooper.dispatchAll();
        verify(mWifiScoreReport, times(2)).onRoleChanged(ROLE_CLIENT_PRIMARY);
        // verify that the APF capabilities are updated in IpClient.
        verify(mWifiNative, times(1)).getApfCapabilities(WIFI_IFACE_NAME);
        verify(mIpClient, times(1)).updateApfCapabilities(eq(APF_CAP));
        verify(mWifiNative, times(1)).installPacketFilter(WIFI_IFACE_NAME, filter);
    }

    @Test
    public void testPacketFilterOnRoleChangeOnSecondaryCmmWithSupportForNonPrimaryApf()
            throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableApfOnNonPrimarySta, true);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();

        byte[] filter = new byte[20];
        new Random().nextBytes(filter);
        mIpClientCallback.installPacketFilter(filter);
        mLooper.dispatchAll();

        // apply the data.
        verify(mWifiNative).installPacketFilter(WIFI_IFACE_NAME, filter);

        when(mWifiNative.readPacketFilter(WIFI_IFACE_NAME)).thenReturn(filter);
        mIpClientCallback.startReadPacketFilter();
        mLooper.dispatchAll();
        verify(mIpClient).readPacketFilterComplete(filter);
        // return the applied data.
        verify(mWifiNative).readPacketFilter(WIFI_IFACE_NAME);

        // Now invoke role change, that should not apply the APF
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mCmi.onRoleChanged();
        // ignore (since it was already applied)
        verify(mIpClient, never()).updateApfCapabilities(eq(APF_CAP));
        verify(mWifiNative, times(1)).installPacketFilter(WIFI_IFACE_NAME, filter);
    }

    @Test
    public void testSetMaxDtimMultiplier_IPv4OnlyNetwork() throws Exception {
        connect();

        mIpClientCallback.setMaxDtimMultiplier(TEST_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        mLooper.dispatchAll();

        verify(mWifiNative).setDtimMultiplier(WIFI_IFACE_NAME,
                TEST_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    public void testSetMaxDtimMultiplier_IPv6OnlyNetwork() throws Exception {
        connect();

        mIpClientCallback.setMaxDtimMultiplier(TEST_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        mLooper.dispatchAll();

        verify(mWifiNative).setDtimMultiplier(WIFI_IFACE_NAME,
                TEST_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
    }

    private void runSetMaxDtimMultiplierInDualStackTest() throws Exception {
        connect();

        mIpClientCallback.setMaxDtimMultiplier(TEST_DUAL_STACK_NETWORK_MAX_DTIM_MULTIPLIER);
        mLooper.dispatchAll();

        verify(mWifiNative).setDtimMultiplier(WIFI_IFACE_NAME,
                TEST_DUAL_STACK_NETWORK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    public void testSetMaxDtimMultiplier_DualStackNetwork() throws Exception {
        runSetMaxDtimMultiplierInDualStackTest();
    }

    @Test
    public void testSetMaxDtimMultiplier_EnableMulticastLock() throws Exception {
        runSetMaxDtimMultiplierInDualStackTest();
        reset(mIpClient);

        // simulate the multicast lock is held.
        WifiMulticastLockManager.FilterController filterController =
                mCmi.getMcastLockManagerFilterController();
        filterController.startFilteringMulticastPackets();
        verify(mIpClient).setMulticastFilter(eq(true));

        reset(mWifiNative);

        mIpClientCallback.setMaxDtimMultiplier(TEST_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);
        mLooper.dispatchAll();

        verify(mWifiNative).setDtimMultiplier(WIFI_IFACE_NAME,
                TEST_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    public void testWifiInfoUpdateOnRoleChange() throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        connect();
        // Should not set WifiInfo.isPrimary
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            if (SdkLevel.isAtLeastS()) {
                WifiInfo wifiInfoFromTi = (WifiInfo) cap.getTransportInfo();
                assertFalse(wifiInfoFromTi.isPrimary());
            }
        });
        reset(mWifiNetworkAgent);

        // Now invoke role change, that should set WifiInfo.isPrimary
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mCmi.onRoleChanged();
        expectNetworkAgentUpdateCapabilities((cap) -> {
            if (SdkLevel.isAtLeastS()) {
                WifiInfo wifiInfoFromTi = (WifiInfo) cap.getTransportInfo();
                assertTrue(wifiInfoFromTi.isPrimary());
            }
        });
    }

    /**
     * Verify onCellularConnectivityChanged plumbs the information to the right locations.
     */
    @Test
    public void testOnCellularConnectivityChanged() {
        mCmi.onCellularConnectivityChanged(WifiDataStall.CELLULAR_DATA_AVAILABLE);
        verify(mWifiConfigManager).onCellularConnectivityChanged(
                WifiDataStall.CELLULAR_DATA_AVAILABLE);

        mCmi.onCellularConnectivityChanged(WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE);
        verify(mWifiConfigManager).onCellularConnectivityChanged(
                WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE);
    }

    /**
     * Verify that when cellular data is lost and wifi is not connected, we force a connectivity
     * scan.
     */
    @Test
    public void testOnCellularConnectivityChangedForceConnectivityScan() throws Exception {
        mResources.setBoolean(R.bool.config_wifiScanOnCellularDataLossEnabled, true);
        // verify a connectivity scan is forced since wifi is not connected
        mCmi.onCellularConnectivityChanged(WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE);
        verify(mWifiConnectivityManager).forceConnectivityScan(WIFI_WORK_SOURCE);

        // verify that after wifi is connected, loss of cellular data will not trigger scans.
        connect();
        mCmi.onCellularConnectivityChanged(WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE);
        verify(mWifiConnectivityManager).forceConnectivityScan(WIFI_WORK_SOURCE);
    }

    private void setScreenState(boolean screenOn) {
        WifiDeviceStateChangeManager.StateChangeCallback callback =
                mStateChangeCallbackArgumentCaptor.getValue();
        assertNotNull(callback);
        callback.onScreenStateChanged(screenOn);
    }

    @Test
    public void verifyRssiPollOnScreenStateChange() throws Exception {
        setScreenState(true);
        connect();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        WifiLinkLayerStats oldLLStats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(oldLLStats);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        verify(mWifiNative).getWifiLinkLayerStats(WIFI_IFACE_NAME);
        verify(mWifiDataStall).checkDataStallAndThroughputSufficiency(WIFI_IFACE_NAME,
                mConnectionCapabilities, null, oldLLStats, mWifiInfo, TEST_TX_BYTES, TEST_RX_BYTES);
        verify(mWifiMetrics).incrementWifiLinkLayerUsageStats(WIFI_IFACE_NAME, oldLLStats);

        WifiLinkLayerStats newLLStats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(newLLStats);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        verify(mWifiNative, times(2)).getWifiLinkLayerStats(WIFI_IFACE_NAME);

        verify(mWifiDataStall).checkDataStallAndThroughputSufficiency(WIFI_IFACE_NAME,
                mConnectionCapabilities, oldLLStats, newLLStats, mWifiInfo, TEST_TX_BYTES,
                TEST_RX_BYTES);
        verify(mWifiMetrics).incrementWifiLinkLayerUsageStats(WIFI_IFACE_NAME, newLLStats);

        // Now set the screen state to false & move time forward, ensure no more link layer stats
        // collection.
        setScreenState(false);
        mLooper.dispatchAll();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiNative, mWifiMetrics, mWifiDataStall);
    }

    @Test
    public void verifyRssiPollOnSecondaryCmm() throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mCmi.onRoleChanged();
        setScreenState(true);
        connect();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        verifyNoMoreInteractions(mWifiNative, mWifiMetrics, mWifiDataStall);

        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(new WifiLinkLayerStats());

        // No link layer stats collection on secondary CMM.
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiNative, mWifiMetrics, mWifiDataStall);
    }

    @Test
    public void verifyRssiPollOnOnRoleChangeToPrimary() throws Exception {
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mCmi.onRoleChanged();
        setScreenState(true);
        connect();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(new WifiLinkLayerStats());

        // No link layer stats collection on secondary CMM.
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiNative, mWifiMetrics, mWifiDataStall);

        // Now invoke role change, that should start rssi polling on the new primary.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mCmi.onRoleChanged();
        mLooper.dispatchAll();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        WifiLinkLayerStats oldLLStats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(oldLLStats);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        verify(mWifiNative).getWifiLinkLayerStats(WIFI_IFACE_NAME);
        verify(mWifiDataStall).checkDataStallAndThroughputSufficiency(WIFI_IFACE_NAME,
                mConnectionCapabilities, null, oldLLStats, mWifiInfo, TEST_TX_BYTES, TEST_RX_BYTES);
        verify(mWifiMetrics).incrementWifiLinkLayerUsageStats(WIFI_IFACE_NAME, oldLLStats);
    }

    @Test
    public void verifyRssiPollOnOnRoleChangeToSecondary() throws Exception {
        setScreenState(true);
        connect();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        // RSSI polling is enabled on primary.
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        WifiLinkLayerStats oldLLStats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(oldLLStats);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        verify(mWifiNative).getWifiLinkLayerStats(WIFI_IFACE_NAME);
        verify(mWifiDataStall).checkDataStallAndThroughputSufficiency(WIFI_IFACE_NAME,
                mConnectionCapabilities, null, oldLLStats, mWifiInfo, TEST_TX_BYTES, TEST_RX_BYTES);
        verify(mWifiMetrics).incrementWifiLinkLayerUsageStats(WIFI_IFACE_NAME, oldLLStats);

        // Now invoke role change, that should stop rssi polling on the secondary.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mCmi.onRoleChanged();
        mLooper.dispatchAll();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        // No link layer stats collection on secondary CMM.
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mWifiNative, mWifiMetrics, mWifiDataStall);
    }

    @Test
    public void verifyRssiPollWhenLinkLayerStatsIsNotSupported() throws Exception {
        setScreenState(true);
        connect();
        clearInvocations(mWifiNative, mWifiMetrics, mWifiDataStall);

        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(0L);
        WifiLinkLayerStats stats = new WifiLinkLayerStats();
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(stats);
        mLooper.moveTimeForward(mWifiGlobals.getPollRssiIntervalMillis());
        mLooper.dispatchAll();
        verify(mWifiNative, never()).getWifiLinkLayerStats(any());
        verify(mWifiDataStall).checkDataStallAndThroughputSufficiency(WIFI_IFACE_NAME,
                mConnectionCapabilities, null, null, mWifiInfo, TEST_TX_BYTES, TEST_RX_BYTES);
        verify(mWifiMetrics).incrementWifiLinkLayerUsageStats(WIFI_IFACE_NAME, null);
    }

    @Test
    public void testClientModeImplWhenIpClientIsNotReady() throws Exception {
        WifiConfiguration config = mConnectedNetwork;
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.setRandomizedMacAddress(TEST_LOCAL_MAC_ADDRESS);
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.getNetworkSelectionStatus().setHasEverConnected(mTestNetworkParams.hasEverConnected);
        assertNull(config.getNetworkSelectionStatus().getCandidateSecurityParams());

        mFrameworkFacade = mock(FrameworkFacade.class);
        ArgumentCaptor<IpClientCallbacks> captor = ArgumentCaptor.forClass(IpClientCallbacks.class);
        // reset mWifiNative since initializeCmi() was called in setup()
        resetWifiNative();

        // reinitialize ClientModeImpl with IpClient is not ready.
        initializeCmi();
        verify(mFrameworkFacade).makeIpClient(any(), anyString(), captor.capture());

        // Manually connect should fail.
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(config.networkId),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
        verify(mWifiConfigManager, never())
                .getConfiguredNetworkWithoutMasking(eq(config.networkId));
        verify(mWifiNative, never()).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));

        // Auto connect should also fail
        mCmi.startConnectToNetwork(config.networkId, MANAGED_PROFILE_UID, config.BSSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never())
                .getConfiguredNetworkWithoutMasking(eq(config.networkId));
        verify(mWifiNative, never()).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));

        // Make IpClient ready connection should succeed.
        captor.getValue().onIpClientCreated(mIpClient);
        mLooper.dispatchAll();

        triggerConnect();
    }

    private void testNetworkRemovedUpdatesLinkedNetworks(boolean isSecondary) throws Exception {
        if (isSecondary) {
            when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        }
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        WifiConfiguration connectedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid1\"");
        connectedConfig.networkId = FRAMEWORK_NETWORK_ID;
        WifiConfiguration removeConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid2\"");
        removeConfig.networkId = FRAMEWORK_NETWORK_ID + 1;
        connectedConfig.linkedConfigurations = new HashMap<>();
        connectedConfig.linkedConfigurations.put(removeConfig.getProfileKey(), 1);
        removeConfig.linkedConfigurations = new HashMap<>();
        removeConfig.linkedConfigurations.put(connectedConfig.getProfileKey(), 1);
        when(mWifiConfigManager.getConfiguredNetwork(connectedConfig.networkId))
                .thenReturn(connectedConfig);
        when(mWifiConfigManager.getConfiguredNetwork(removeConfig.networkId))
                .thenReturn(removeConfig);
        mConnectedNetwork = connectedConfig;
        connect();

        when(mWifiNative.getCurrentNetworkSecurityParams(any())).thenReturn(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkRemoved(removeConfig);
        }
        mLooper.dispatchAll();

        if (!isSecondary) {
            verify(mWifiConfigManager).updateLinkedNetworks(connectedConfig.networkId);
        } else {
            verify(mWifiConfigManager, never()).updateLinkedNetworks(connectedConfig.networkId);
        }
    }

    @Test
    public void testNetworkRemovedUpdatesLinkedNetworksPrimary() throws Exception {
        testNetworkRemovedUpdatesLinkedNetworks(false);
    }

    @Test
    public void testNetworkRemovedUpdatesLinkedNetworksSecondary() throws Exception {
        testNetworkRemovedUpdatesLinkedNetworks(true);
    }

    @Test
    public void testConnectClearsAllowlistSsids() throws Exception {
        connect();
        verify(mWifiBlocklistMonitor)
                .setAllowlistSsids(eq(mConnectedNetwork.SSID), eq(Collections.emptyList()));
    }

    @Test
    public void testNetworkUpdatedUpdatesLinkedNetworks() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        WifiConfiguration connectedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid1\"");
        connectedConfig.networkId = FRAMEWORK_NETWORK_ID;
        WifiConfiguration updatedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid2\"");
        updatedConfig.networkId = FRAMEWORK_NETWORK_ID + 1;
        connectedConfig.linkedConfigurations = new HashMap<>();
        connectedConfig.linkedConfigurations.put(updatedConfig.getProfileKey(), 1);
        updatedConfig.linkedConfigurations = new HashMap<>();
        updatedConfig.linkedConfigurations.put(connectedConfig.getProfileKey(), 1);
        when(mWifiConfigManager.getConfiguredNetwork(connectedConfig.networkId))
                .thenReturn(connectedConfig);
        when(mWifiConfigManager.getConfiguredNetwork(updatedConfig.networkId))
                .thenReturn(updatedConfig);
        mConnectedNetwork = connectedConfig;
        connect();

        when(mWifiNative.getCurrentNetworkSecurityParams(any())).thenReturn(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.saveNetwork(
                new NetworkUpdateResult(
                        updatedConfig.networkId, STATUS_SUCCESS, false, false, true, false),
                new ActionListenerWrapper(connectActionListener),
                Binder.getCallingUid(), OP_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateLinkedNetworks(connectedConfig.networkId);
    }

    @Test
    public void testNetworkValidationUpdatesLinkedNetworks() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        BufferedReader reader = mock(BufferedReader.class);
        WifiConfiguration connectedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid1\"");
        connectedConfig.networkId = FRAMEWORK_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(connectedConfig.networkId))
                .thenReturn(connectedConfig);
        mConnectedNetwork = connectedConfig;
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());
        verify(mWifiBlocklistMonitor).setAllowlistSsids(
                eq(connectedConfig.SSID), eq(Collections.emptyList()));
        verify(mWifiBlocklistMonitor).updateFirmwareRoamingConfiguration(
                eq(Set.of(connectedConfig.SSID)));

        LinkProperties linkProperties = mock(LinkProperties.class);
        RouteInfo routeInfo = mock(RouteInfo.class);
        IpPrefix ipPrefix = mock(IpPrefix.class);
        Inet4Address destinationAddress = mock(Inet4Address.class);
        InetAddress gatewayAddress = mock(InetAddress.class);
        String hostAddress = "127.0.0.1";
        String gatewayMac = "192.168.0.1";
        when(linkProperties.getRoutes()).thenReturn(Arrays.asList(routeInfo));
        when(routeInfo.isDefaultRoute()).thenReturn(true);
        when(routeInfo.getDestination()).thenReturn(ipPrefix);
        when(ipPrefix.getAddress()).thenReturn(destinationAddress);
        when(routeInfo.hasGateway()).thenReturn(true);
        when(routeInfo.getGateway()).thenReturn(gatewayAddress);
        when(gatewayAddress.getHostAddress()).thenReturn(hostAddress);
        when(mWifiInjector.createBufferedReader(ARP_TABLE_PATH)).thenReturn(reader);
        when(reader.readLine()).thenReturn(new StringJoiner(" ")
                .add(hostAddress)
                .add("HWType")
                .add("Flags")
                .add(gatewayMac)
                .add("Mask")
                .add("Device")
                .toString());

        mIpClientCallback.onLinkPropertiesChange(linkProperties);
        mLooper.dispatchAll();
        when(mWifiNative.getCurrentNetworkSecurityParams(any())).thenReturn(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        when(mWifiConfigManager.setNetworkDefaultGwMacAddress(anyInt(), any())).thenReturn(true);
        when(mWifiConfigManager.saveToStore(anyBoolean())).thenReturn(true);
        WifiConfiguration linkedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid2\"");
        linkedConfig.networkId = connectedConfig.networkId + 1;
        Map<String, WifiConfiguration> linkedNetworks = new HashMap<>();
        linkedNetworks.put(linkedConfig.getProfileKey(), linkedConfig);
        when(mWifiConfigManager.getLinkedNetworksWithoutMasking(connectedConfig.networkId))
                .thenReturn(linkedNetworks);
        when(mWifiNative.updateLinkedNetworks(any(), anyInt(), any())).thenReturn(true);
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .setNetworkDefaultGwMacAddress(mConnectedNetwork.networkId, gatewayMac);
        verify(mWifiConfigManager).updateLinkedNetworks(connectedConfig.networkId);
        verify(mWifiNative).updateLinkedNetworks(
                any(), eq(connectedConfig.networkId), eq(linkedNetworks));
        List<String> allowlistSsids = new ArrayList<>();
        allowlistSsids.add(linkedConfig.SSID);
        allowlistSsids.add(connectedConfig.SSID);
        verify(mWifiBlocklistMonitor).setAllowlistSsids(
                eq(connectedConfig.SSID), eq(allowlistSsids));
        verify(mWifiBlocklistMonitor).updateFirmwareRoamingConfiguration(
                eq(new ArraySet<>(allowlistSsids)));
    }

    @Test
    public void testNonPskNetworkDoesNotUpdateLinkedNetworks() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        BufferedReader reader = mock(BufferedReader.class);
        WifiConfiguration connectedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid1\"");
        connectedConfig.networkId = FRAMEWORK_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(connectedConfig.networkId))
                .thenReturn(connectedConfig);
        mConnectedNetwork = connectedConfig;
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        LinkProperties linkProperties = mock(LinkProperties.class);
        RouteInfo routeInfo = mock(RouteInfo.class);
        IpPrefix ipPrefix = mock(IpPrefix.class);
        Inet4Address destinationAddress = mock(Inet4Address.class);
        InetAddress gatewayAddress = mock(InetAddress.class);
        String hostAddress = "127.0.0.1";
        String gatewayMac = "192.168.0.1";
        when(linkProperties.getRoutes()).thenReturn(Arrays.asList(routeInfo));
        when(routeInfo.isDefaultRoute()).thenReturn(true);
        when(routeInfo.getDestination()).thenReturn(ipPrefix);
        when(ipPrefix.getAddress()).thenReturn(destinationAddress);
        when(routeInfo.hasGateway()).thenReturn(true);
        when(routeInfo.getGateway()).thenReturn(gatewayAddress);
        when(gatewayAddress.getHostAddress()).thenReturn(hostAddress);
        when(mWifiInjector.createBufferedReader(ARP_TABLE_PATH)).thenReturn(reader);
        when(reader.readLine()).thenReturn(new StringJoiner(" ")
                .add(hostAddress)
                .add("HWType")
                .add("Flags")
                .add(gatewayMac)
                .add("Mask")
                .add("Device")
                .toString());

        mIpClientCallback.onLinkPropertiesChange(linkProperties);
        mLooper.dispatchAll();
        when(mWifiNative.getCurrentNetworkSecurityParams(any())).thenReturn(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));
        when(mWifiConfigManager.setNetworkDefaultGwMacAddress(anyInt(), any())).thenReturn(true);
        when(mWifiConfigManager.saveToStore(anyBoolean())).thenReturn(true);
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .setNetworkDefaultGwMacAddress(mConnectedNetwork.networkId, gatewayMac);
        verify(mWifiConfigManager, never()).updateLinkedNetworks(connectedConfig.networkId);
    }

    @Test
    public void testInvalidScanResultDoesNotUpdateLinkedNetworks() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        BufferedReader reader = mock(BufferedReader.class);
        WifiConfiguration connectedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid1\"");
        connectedConfig.networkId = FRAMEWORK_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(connectedConfig.networkId))
                .thenReturn(connectedConfig);
        mConnectedNetwork = connectedConfig;
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());

        LinkProperties linkProperties = mock(LinkProperties.class);
        RouteInfo routeInfo = mock(RouteInfo.class);
        IpPrefix ipPrefix = mock(IpPrefix.class);
        Inet4Address destinationAddress = mock(Inet4Address.class);
        InetAddress gatewayAddress = mock(InetAddress.class);
        String hostAddress = "127.0.0.1";
        String gatewayMac = "192.168.0.1";
        when(linkProperties.getRoutes()).thenReturn(Arrays.asList(routeInfo));
        when(routeInfo.isDefaultRoute()).thenReturn(true);
        when(routeInfo.getDestination()).thenReturn(ipPrefix);
        when(ipPrefix.getAddress()).thenReturn(destinationAddress);
        when(routeInfo.hasGateway()).thenReturn(true);
        when(routeInfo.getGateway()).thenReturn(gatewayAddress);
        when(gatewayAddress.getHostAddress()).thenReturn(hostAddress);
        when(mWifiInjector.createBufferedReader(ARP_TABLE_PATH)).thenReturn(reader);
        when(reader.readLine()).thenReturn(new StringJoiner(" ")
                .add(hostAddress)
                .add("HWType")
                .add("Flags")
                .add(gatewayMac)
                .add("Mask")
                .add("Device")
                .toString());

        mIpClientCallback.onLinkPropertiesChange(linkProperties);
        mLooper.dispatchAll();
        when(mWifiNative.getCurrentNetworkSecurityParams(any())).thenReturn(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        when(mWifiConfigManager.setNetworkDefaultGwMacAddress(anyInt(), any())).thenReturn(true);
        when(mWifiConfigManager.saveToStore(anyBoolean())).thenReturn(true);

        // FT/PSK scan, do not update linked networks
        ScanResult ftPskScan = new ScanResult();
        ftPskScan.capabilities = "FT/PSK";
        when(mScanDetailCache.getScanResult(any())).thenReturn(ftPskScan);
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();
        verify(mWifiConfigManager)
                .setNetworkDefaultGwMacAddress(mConnectedNetwork.networkId, gatewayMac);
        verify(mWifiConfigManager, never()).updateLinkedNetworks(connectedConfig.networkId);

        // FT/SAE scan, do not update linked networks
        ScanResult ftSaeScan = new ScanResult();
        ftSaeScan.capabilities = "FT/SAE";
        when(mScanRequestProxy.getScanResult(any())).thenReturn(ftSaeScan);
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();
        verify(mWifiConfigManager)
                .setNetworkDefaultGwMacAddress(mConnectedNetwork.networkId, gatewayMac);
        verify(mWifiConfigManager, never()).updateLinkedNetworks(connectedConfig.networkId);

        // Null scan, do not update linked networks
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();
        verify(mWifiConfigManager)
                .setNetworkDefaultGwMacAddress(mConnectedNetwork.networkId, gatewayMac);
        verify(mWifiConfigManager, never()).updateLinkedNetworks(connectedConfig.networkId);
    }

    @Test
    public void testLinkedNetworksFiltersOutAutojoinDisabledNetworks() throws Exception {
        mResources.setBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming, true);
        BufferedReader reader = mock(BufferedReader.class);
        WifiConfiguration connectedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid1\"");
        connectedConfig.networkId = FRAMEWORK_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(connectedConfig.networkId))
                .thenReturn(connectedConfig);
        mConnectedNetwork = connectedConfig;
        connect();
        verify(mWifiInjector).makeWifiNetworkAgent(any(), any(), any(), any(),
                mWifiNetworkAgentCallbackCaptor.capture());
        verify(mWifiBlocklistMonitor).setAllowlistSsids(
                eq(connectedConfig.SSID), eq(Collections.emptyList()));
        verify(mWifiBlocklistMonitor).updateFirmwareRoamingConfiguration(
                eq(Set.of(connectedConfig.SSID)));

        LinkProperties linkProperties = mock(LinkProperties.class);
        RouteInfo routeInfo = mock(RouteInfo.class);
        IpPrefix ipPrefix = mock(IpPrefix.class);
        Inet4Address destinationAddress = mock(Inet4Address.class);
        InetAddress gatewayAddress = mock(InetAddress.class);
        String hostAddress = "127.0.0.1";
        String gatewayMac = "192.168.0.1";
        when(linkProperties.getRoutes()).thenReturn(Arrays.asList(routeInfo));
        when(routeInfo.isDefaultRoute()).thenReturn(true);
        when(routeInfo.getDestination()).thenReturn(ipPrefix);
        when(ipPrefix.getAddress()).thenReturn(destinationAddress);
        when(routeInfo.hasGateway()).thenReturn(true);
        when(routeInfo.getGateway()).thenReturn(gatewayAddress);
        when(gatewayAddress.getHostAddress()).thenReturn(hostAddress);
        when(mWifiInjector.createBufferedReader(ARP_TABLE_PATH)).thenReturn(reader);
        when(reader.readLine()).thenReturn(new StringJoiner(" ")
                .add(hostAddress)
                .add("HWType")
                .add("Flags")
                .add(gatewayMac)
                .add("Mask")
                .add("Device")
                .toString());

        mIpClientCallback.onLinkPropertiesChange(linkProperties);
        mLooper.dispatchAll();
        when(mWifiNative.getCurrentNetworkSecurityParams(any())).thenReturn(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        when(mWifiConfigManager.setNetworkDefaultGwMacAddress(anyInt(), any())).thenReturn(true);
        when(mWifiConfigManager.saveToStore(anyBoolean())).thenReturn(true);
        WifiConfiguration linkedConfig = WifiConfigurationTestUtil.createPskNetwork("\"ssid2\"");
        linkedConfig.networkId = connectedConfig.networkId + 1;
        linkedConfig.allowAutojoin = false;
        Map<String, WifiConfiguration> linkedNetworks = new HashMap<>();
        linkedNetworks.put(linkedConfig.getProfileKey(), linkedConfig);
        when(mWifiConfigManager.getLinkedNetworksWithoutMasking(connectedConfig.networkId))
                .thenReturn(linkedNetworks);
        when(mWifiNative.updateLinkedNetworks(any(), anyInt(), any())).thenReturn(true);
        mWifiNetworkAgentCallbackCaptor.getValue().onValidationStatus(
                NetworkAgent.VALIDATION_STATUS_VALID, null /* captivePortalUrl */);
        mLooper.dispatchAll();

        verify(mWifiConfigManager)
                .setNetworkDefaultGwMacAddress(mConnectedNetwork.networkId, gatewayMac);
        verify(mWifiConfigManager).updateLinkedNetworks(connectedConfig.networkId);
        verify(mWifiNative).updateLinkedNetworks(
                any(), eq(connectedConfig.networkId), eq(linkedNetworks));
        verify(mWifiBlocklistMonitor, times(2)).setAllowlistSsids(
                eq(connectedConfig.SSID), eq(Collections.emptyList()));
        verify(mWifiBlocklistMonitor).updateFirmwareRoamingConfiguration(
                eq(Collections.emptySet()));
    }

    /**
     * Verify that we disconnect when we mark a previous trusted network untrusted.
     */
    @Test
    public void verifyDisconnectOnMarkingNetworkUntrusted() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED));
        });

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.trusted = false;

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_NETWORK_UNTRUSTED));
    }

    /**
     * Verify that we only update capabilities when we mark a previous untrusted network trusted.
     */
    @Test
    public void verifyUpdateCapabilitiesOnMarkingNetworkTrusted() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mConnectedNetwork.trusted = false;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED));
        });
        reset(mWifiNetworkAgent);

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.trusted = true;

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());

        expectNetworkAgentUpdateCapabilities((cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED));
        });
    }

    /**
     * Verify on device before T we will not disconnect when we mark a previous trusted network
     * untrusted.
     */
    @Test
    public void verifyNoDisconnectOnMarkingNetworkUntrustedBeforeT() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertTrue(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED));
        });

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.trusted = false;

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        verify(mWifiNative, never()).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics, never()).logStaEvent(anyString(), anyInt(), anyInt());
    }

    /**
     * Verify on a build before T we will not update capabilities or disconnect when we mark a
     * previous untrusted network trusted.
     */
    @Test
    public void verifyNoUpdateCapabilitiesOnMarkingNetworkTrustedBeforeT() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        mConnectedNetwork.trusted = false;
        connect();
        expectRegisterNetworkAgent((config) -> { }, (cap) -> {
            assertFalse(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED));
        });
        reset(mWifiNetworkAgent);

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.trusted = true;

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        assertEquals("L3ConnectedState", getCurrentState().getName());
        verifyNoMoreInteractions(mWifiNetworkAgent);
    }

    private void verifyUpdateAutoUpgradeFlagForSaeOnR(
            boolean isWpa3SaeUpgradeEnabled, boolean isWpa2PersonalOnlyNetworkInRange,
            boolean isWpa2Wpa3PersonalTransitionNetworkInRange,
            boolean isWpa3PersonalOnlyNetworkInRange, boolean shouldBeUpdated)
            throws Exception {

        when(mWifiGlobals.isWpa3SaeUpgradeEnabled()).thenReturn(isWpa3SaeUpgradeEnabled);
        when(mScanRequestProxy.isWpa2PersonalOnlyNetworkInRange(any()))
                .thenReturn(isWpa2PersonalOnlyNetworkInRange);
        when(mScanRequestProxy.isWpa2Wpa3PersonalTransitionNetworkInRange(any()))
                .thenReturn(isWpa2Wpa3PersonalTransitionNetworkInRange);
        when(mScanRequestProxy.isWpa3PersonalOnlyNetworkInRange(any()))
                .thenReturn(isWpa3PersonalOnlyNetworkInRange);
        initializeAndAddNetworkAndVerifySuccess();

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.networkId = TEST_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);

        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(TEST_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Process.SYSTEM_UID, OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();
        if (shouldBeUpdated) {
            verify(mWifiConfigManager).updateIsAddedByAutoUpgradeFlag(
                    eq(TEST_NETWORK_ID), eq(WifiConfiguration.SECURITY_TYPE_SAE),
                    eq(false));
        } else {
            verify(mWifiConfigManager, never()).updateIsAddedByAutoUpgradeFlag(
                    anyInt(), anyInt(), anyBoolean());
        }
    }

    /**
     * Tests that manual connection to a network (from settings app) updates
     * the auto upgrade flag for SAE on R.
     * - SAE auto-upgrade is disabled.
     * - No WPA2 PSK network
     * - No WPA2/WPA3 network
     * - A WPA3-SAE-only network exists.
     */
    @Test
    public void testManualConnectUpdateAutoUpgradeFlagForSaeOnR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());

        verifyUpdateAutoUpgradeFlagForSaeOnR(false, false, false, true, true);
    }

    /**
     * Tests that manual connection to a network (from settings app) does not update
     * the auto upgrade flag for SAE on R if auto-upgrade is enabled.
     */
    @Test
    public void testManualConnectNotUpdateAutoUpgradeFlagForSaeOnRWhenAutoUpgradeEnabled()
            throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());

        verifyUpdateAutoUpgradeFlagForSaeOnR(true, false, false, true, false);
    }

    /**
     * Tests that manual connection to a network (from settings app) does not update
     * the auto upgrade flag for SAE on R if there are psk networks.
     */
    @Test
    public void testManualConnectNotUpdateAutoUpgradeFlagForSaeOnRWithPskNetworks()
            throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());

        verifyUpdateAutoUpgradeFlagForSaeOnR(false, true, false, true, false);
    }

    /**
     * Tests that manual connection to a network (from settings app) does not update
     * the auto upgrade flag for SAE on R if there are psk/ase networks.
     */
    @Test
    public void testManualConnectNotUpdateAutoUpgradeFlagForSaeOnRWithPskSaeNetworks()
            throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());

        verifyUpdateAutoUpgradeFlagForSaeOnR(false, false, true, true, false);
    }

    /**
     * Tests that manual connection to a network (from settings app) does not update
     * the auto upgrade flag for SAE on R if there is no WPA3 SAE only network..
     */
    @Test
    public void testManualConnectNotUpdateAutoUpgradeFlagForSaeOnRWithoutSaeOnlyNetworks()
            throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());

        verifyUpdateAutoUpgradeFlagForSaeOnR(false, false, true, true, false);
    }

    private WifiConfiguration setupLegacyEapNetworkTest(boolean isUserSelected) throws Exception {
        return setupTrustOnFirstUse(false, false, isUserSelected);
    }

    private WifiConfiguration setupTrustOnFirstUse(
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported, boolean isUserSelected)
            throws Exception {
        if (isTrustOnFirstUseSupported) {
            when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                    WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE);
        }
        mCmi.mInsecureEapNetworkHandler = mInsecureEapNetworkHandler;

        WifiConfiguration eapTlsConfig = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.NONE));
        eapTlsConfig.networkId = FRAMEWORK_NETWORK_ID;
        eapTlsConfig.SSID = TEST_SSID;
        if (isAtLeastT && isTrustOnFirstUseSupported) {
            eapTlsConfig.enterpriseConfig.enableTrustOnFirstUse(true);
        }
        eapTlsConfig.enterpriseConfig.setCaPath("");
        eapTlsConfig.enterpriseConfig.setDomainSuffixMatch("");
        eapTlsConfig.setRandomizedMacAddress(TEST_LOCAL_MAC_ADDRESS);
        eapTlsConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;

        initializeAndAddNetworkAndVerifySuccess(eapTlsConfig);

        if (isUserSelected) {
            startConnectSuccess();
        } else {
            mCmi.startConnectToNetwork(
                    eapTlsConfig.networkId,
                    Process.SYSTEM_UID,
                    ClientModeImpl.SUPPLICANT_BSSID_ANY);
            mLooper.dispatchAll();
        }
        verify(mInsecureEapNetworkHandler).prepareConnection(eq(eapTlsConfig));

        if (isTrustOnFirstUseSupported) {
            mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                    new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                            SupplicantState.ASSOCIATED));
            mLooper.dispatchAll();

            CertificateEventInfo certificateEventInfo =
                    spy(new CertificateEventInfo(FakeKeys.CA_CERT0, "1234"));
            mCmi.sendMessage(WifiMonitor.TOFU_CERTIFICATE_EVENT,
                    FRAMEWORK_NETWORK_ID, 0, certificateEventInfo);
            mLooper.dispatchAll();
            verify(mInsecureEapNetworkHandler).addPendingCertificate(
                    eq(eapTlsConfig.networkId), eq(0), eq(certificateEventInfo));

            // Adding a certificate in depth 0 will cause a disconnection when TOFU is supported
            DisconnectEventInfo disconnectEventInfo =
                    new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 3, true);
            mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
            mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                    new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                            SupplicantState.DISCONNECTED));
            mLooper.dispatchAll();
        }

        verify(mInsecureEapNetworkHandler).startUserApprovalIfNecessary(eq(isUserSelected));
        if (isTrustOnFirstUseSupported) {
            assertEquals("DisconnectedState", getCurrentState().getName());
        }
        return eapTlsConfig;
    }

    /**
     * Verify Trust On First Use support.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupTrustOnFirstUse(true, true, true);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onAccept(testConfig.SSID,
                testConfig.networkId);
        mLooper.dispatchAll();
        ArgumentCaptor<WifiConfiguration> wifiConfigurationArgumentCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        // TOFU will first connect to get the certificates, and then connect once approved
        verify(mWifiNative, times(2)).connectToNetwork(eq(WIFI_IFACE_NAME),
                wifiConfigurationArgumentCaptor.capture());
        assertEquals(testConfig.networkId, wifiConfigurationArgumentCaptor.getValue().networkId);
    }

    /**
     * Verify Trust On First Use support.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupTrustOnFirstUse(true, true, true);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onReject(testConfig.SSID, false);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(eq(ClientModeImpl.WIFI_WORK_SOURCE));
        verify(mWifiMetrics).endConnectionEvent(
                any(), eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION),
                eq(WifiMetricsProto.ConnectionEvent.HLF_NONE),
                eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN),
                anyInt(), anyInt());
        ArgumentCaptor<WifiConfiguration> wifiConfigurationArgumentCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        // TOFU will connect only once to get the certificates, but will not proceed
        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME),
                wifiConfigurationArgumentCaptor.capture());
        assertEquals(testConfig.networkId, wifiConfigurationArgumentCaptor.getValue().networkId);

    }

    /**
     * Verify Trust On First Use support.
     * - This network is selected by a user.
     * - Errors occur in InsecureEapNetworkHandler.
     */
    @Test
    public void verifyTrustOnFirstUseErrorWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupTrustOnFirstUse(true, true, true);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onError(testConfig.SSID);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(eq(ClientModeImpl.WIFI_WORK_SOURCE));
        verify(mWifiMetrics).endConnectionEvent(
                any(), eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION),
                eq(WifiMetricsProto.ConnectionEvent.HLF_NONE),
                eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN),
                anyInt(), anyInt());
    }

    /**
     * Verify Trust On First Use support.
     * - this network is automatically connected.
     * - Tap the notification.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupTrustOnFirstUse(true, true, false);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onAccept(testConfig.SSID,
                testConfig.networkId);
        mLooper.dispatchAll();
        ArgumentCaptor<WifiConfiguration> wifiConfigurationArgumentCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        // TOFU will first connect to get the certificates, and then connect once approved
        verify(mWifiNative, times(2)).connectToNetwork(eq(WIFI_IFACE_NAME),
                wifiConfigurationArgumentCaptor.capture());
        assertEquals(testConfig.networkId, wifiConfigurationArgumentCaptor.getValue().networkId);
    }

    /**
     * Verify Trust On First Use support.
     * - this network is automatically connected.
     * - Tap the notification
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupTrustOnFirstUse(true, true, false);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onReject(testConfig.SSID, false);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(eq(ClientModeImpl.WIFI_WORK_SOURCE));
        verify(mWifiMetrics).endConnectionEvent(
                any(), eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION),
                eq(WifiMetricsProto.ConnectionEvent.HLF_NONE),
                eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN),
                anyInt(), anyInt());
        ArgumentCaptor<WifiConfiguration> wifiConfigurationArgumentCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        // TOFU will connect only once to get the certificates, but will not proceed
        verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME),
                wifiConfigurationArgumentCaptor.capture());
        assertEquals(testConfig.networkId, wifiConfigurationArgumentCaptor.getValue().networkId);
    }

    /**
     * Verify Trust On First Use support.
     * - This network is automatically connected.
     * - Errors occur in InsecureEapNetworkHandler.
     */
    @Test
    public void verifyTrustOnFirstUseErrorWhenAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupTrustOnFirstUse(true, true, false);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onError(testConfig.SSID);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(eq(ClientModeImpl.WIFI_WORK_SOURCE));
        verify(mWifiMetrics).endConnectionEvent(
                any(), eq(WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION),
                eq(WifiMetricsProto.ConnectionEvent.HLF_NONE),
                eq(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN),
                anyInt(), anyInt());
    }

    /**
     * Verify that InsecureEapNetworkHandler cleanup() is called if wifi is disabled.
     */
    @Test
    public void verifyInsecureEapNetworkCleanUpWhenDisabled() throws Exception {
        mCmi.stop();
        mLooper.dispatchAll();
        verify(mInsecureEapNetworkHandler).cleanup();
    }

    /**
     * Verify legacy EAP network handling.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupLegacyEapNetworkTest(true);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onAccept(testConfig.SSID,
                testConfig.networkId);
        mLooper.dispatchAll();
        verify(mWifiMetrics, never()).endConnectionEvent(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    /**
     * Verify legacy EAP network handling.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupLegacyEapNetworkTest(true);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onReject(testConfig.SSID, true);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(eq(ClientModeImpl.WIFI_WORK_SOURCE));
        verify(mWifiNative).disconnect(eq(WIFI_IFACE_NAME));
    }

    /**
     * Verify legacy EAP network handling.
     * - This network is automatically connected.
     * - Tap "connect anyway" on the notification.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptOnNotificationWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupLegacyEapNetworkTest(false);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onAccept(testConfig.SSID,
                testConfig.networkId);
        mLooper.dispatchAll();
        verify(mWifiMetrics, never()).endConnectionEvent(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    /**
     * Verify legacy EAP network handling.
     * - This network is automatically connected.
     * - Tap "Disconnect now" on the notification
     */
    @Test
    public void verifyLegacyEapNetworkRejectOnNotificationWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        WifiConfiguration testConfig = setupLegacyEapNetworkTest(false);

        mCmi.mInsecureEapNetworkHandlerCallbacksImpl.onReject(testConfig.SSID, true);
        mLooper.dispatchAll();
        verify(mFrameworkFacade, never()).makeAlertDialogBuilder(any());
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(eq(ClientModeImpl.WIFI_WORK_SOURCE));
        verify(mWifiNative).disconnect(eq(WIFI_IFACE_NAME));
    }

    private void setScanResultWithMloInfo() {
        List<MloLink> mloLinks = new ArrayList<>();
        MloLink link1 = new MloLink();
        link1.setBand(WifiScanner.WIFI_BAND_24_GHZ);
        link1.setChannel(TEST_CHANNEL);
        link1.setApMacAddress(MacAddress.fromString(TEST_BSSID_STR));
        link1.setLinkId(TEST_MLO_LINK_ID);
        link1.setRssi(TEST_RSSI);
        MloLink link2 = new MloLink();
        link2.setBand(WifiScanner.WIFI_BAND_5_GHZ);
        link2.setChannel(TEST_CHANNEL_1);
        link2.setApMacAddress(MacAddress.fromString(TEST_BSSID_STR1));
        link2.setLinkId(TEST_MLO_LINK_ID_1);
        link2.setRssi(TEST_RSSI);
        mloLinks.add(link1);
        mloLinks.add(link2);

        when(mScanResult.getApMldMacAddress()).thenReturn(TEST_AP_MLD_MAC_ADDRESS);
        when(mScanResult.getApMloLinkId()).thenReturn(TEST_MLO_LINK_ID);
        when(mScanResult.getAffiliatedMloLinks()).thenReturn(mloLinks);
        mScanResult.level = TEST_RSSI;

        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanResult(any())).thenReturn(mScanResult);
    }

    private void setScanResultWithoutMloInfo() {
        mConnectionCapabilities.wifiStandard = ScanResult.WIFI_STANDARD_11AC;

        when(mScanResult.getApMldMacAddress()).thenReturn(null);
        when(mScanResult.getApMloLinkId()).thenReturn(MloLink.INVALID_MLO_LINK_ID);
        when(mScanResult.getAffiliatedMloLinks()).thenReturn(Collections.emptyList());

        when(mScanRequestProxy.getScanResults()).thenReturn(Arrays.asList(mScanResult));
        when(mScanRequestProxy.getScanResult(any())).thenReturn(mScanResult);
    }

    private void setConnection() throws Exception {
        WifiConfiguration config = createTestNetwork(false);
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
    }

    private void setConnectionMloLinksInfo() {
        mConnectionCapabilities.wifiStandard = ScanResult.WIFI_STANDARD_11BE;
        WifiNative.ConnectionMloLinksInfo info = new WifiNative.ConnectionMloLinksInfo();
        info.links = new WifiNative.ConnectionMloLink[2];
        info.links[0] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID, TEST_MLO_LINK_ADDR,
                TEST_AP_MLD_MAC_ADDRESS, Byte.MIN_VALUE, Byte.MAX_VALUE, 5160);
        info.links[1] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID_1, TEST_MLO_LINK_ADDR_1,
                TEST_AP_MLD_MAC_ADDRESS, Byte.MAX_VALUE, Byte.MIN_VALUE, 2437);
        when(mWifiNative.getConnectionMloLinksInfo(WIFI_IFACE_NAME)).thenReturn(info);
    }

    /**
     * Verify Affiliated link BSSID matching
     */
    @Test
    public  void testAffiliatedLinkBssidMatch() throws Exception {
        setConnection();
        setScanResultWithMloInfo();
        // Associate
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        // Validate Affiliated BSSID match
        assertTrue(mCmi.isAffiliatedLinkBssid(MacAddress.fromString(TEST_BSSID_STR)));
        assertTrue(mCmi.isAffiliatedLinkBssid(MacAddress.fromString(TEST_BSSID_STR1)));
        assertFalse(mCmi.isAffiliatedLinkBssid(MacAddress.fromString(TEST_BSSID_STR2)));
    }

    /**
     * Verify MLO parameters update from ScanResult at association
     */
    @Test
    public void verifyMloParametersUpdateAssoc() throws Exception {
        setConnection();
        setScanResultWithMloInfo();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        WifiInfo connectionInfo = mCmi.getConnectionInfo();
        assertNotNull(connectionInfo.getApMldMacAddress());
        assertEquals(TEST_AP_MLD_MAC_ADDRESS_STR, connectionInfo.getApMldMacAddress().toString());
        assertEquals(TEST_MLO_LINK_ID, connectionInfo.getApMloLinkId());
        assertEquals(2, connectionInfo.getAffiliatedMloLinks().size());
        // None of the links are active or idle.
        assertTrue(connectionInfo.getAssociatedMloLinks().isEmpty());
    }

    /**
     * Verify MLO parameters update when roaming to a MLD ap, and then get cleared when roaming to
     * a non MLD supported AP.
     */
    @Test
    public void verifyMloParametersUpdateRoam() throws Exception {
        connect();
        setScanResultWithMloInfo();

        // Roam to an MLD AP
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        WifiInfo connectionInfo = mCmi.getConnectionInfo();
        assertNotNull(connectionInfo.getApMldMacAddress());
        assertEquals(TEST_AP_MLD_MAC_ADDRESS_STR, connectionInfo.getApMldMacAddress().toString());
        assertEquals(TEST_MLO_LINK_ID, connectionInfo.getApMloLinkId());
        assertEquals(2, connectionInfo.getAffiliatedMloLinks().size());

        // Now perform Roaming to a non-MLD AP
        setScanResultWithoutMloInfo();
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        connectionInfo = mCmi.getConnectionInfo();
        assertNull(connectionInfo.getApMldMacAddress());
        assertEquals(MloLink.INVALID_MLO_LINK_ID, connectionInfo.getApMloLinkId());
        assertTrue(connectionInfo.getAffiliatedMloLinks().isEmpty());
        assertTrue(connectionInfo.getAssociatedMloLinks().isEmpty());
    }

    /**
     * Verify API calls on affiliated BSSIDs during association and disconnect.
     */
    @Test
    public void verifyAffiliatedBssidsAssocDisconnect() throws Exception {
        List<String> affiliatedBssids = Arrays.asList(TEST_BSSID_STR1);

        connect();
        setScanResultWithMloInfo();
        setConnectionMloLinksInfo();
        mLooper.dispatchAll();

        // Association
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).setAffiliatedBssids(eq(TEST_BSSID_STR), eq(affiliatedBssids));

        // Disconnect
        DisconnectEventInfo disconnectEventInfo =
                new DisconnectEventInfo(TEST_SSID, TEST_BSSID_STR, 0, false);
        mCmi.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, disconnectEventInfo);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).removeAffiliatedBssids(eq(TEST_BSSID_STR));
    }

    private void configureMloLinksInfoWithIdleLinks() {
        mConnectionCapabilities.wifiStandard = ScanResult.WIFI_STANDARD_11BE;
        WifiNative.ConnectionMloLinksInfo info = new WifiNative.ConnectionMloLinksInfo();
        info.links = new WifiNative.ConnectionMloLink[2];
        info.links[0] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID, TEST_MLO_LINK_ADDR,
                TEST_AP_MLD_MAC_ADDRESS, (byte) 0xFF, (byte) 0xFF, 2437);
        info.links[1] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID_1, TEST_MLO_LINK_ADDR_1,
                TEST_AP_MLD_MAC_ADDRESS, (byte) 0, (byte) 0, 5160);
        when(mWifiNative.getConnectionMloLinksInfo(WIFI_IFACE_NAME)).thenReturn(info);
    }

    private void reconfigureMloLinksInfoWithOneLink() {
        mConnectionCapabilities.wifiStandard = ScanResult.WIFI_STANDARD_11BE;
        WifiNative.ConnectionMloLinksInfo info = new WifiNative.ConnectionMloLinksInfo();
        info.links = new WifiNative.ConnectionMloLink[1];
        info.links[0] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID, TEST_MLO_LINK_ADDR,
                TEST_AP_MLD_MAC_ADDRESS, (byte) 0xFF, (byte) 0xFF, 2437);
        when(mWifiNative.getConnectionMloLinksInfo(WIFI_IFACE_NAME)).thenReturn(info);
    }

    @Test
    public void verifyMloLinkChangeTidToLinkMapping() throws Exception {
        // Initialize
        connect();
        setScanResultWithMloInfo();
        setConnectionMloLinksInfo();
        mLooper.dispatchAll();

        // Association
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        // Make sure all links are active
        for (MloLink link: mWifiInfo.getAffiliatedMloLinks()) {
            assertEquals(link.getState(), MloLink.MLO_LINK_STATE_ACTIVE);
        }
        // All links are associated (ACTIVE)
        assertEquals(mWifiInfo.getAssociatedMloLinks().size(),
                mWifiInfo.getAffiliatedMloLinks().size());

        // TID to link mapping. Make sure one link is IDLE.
        configureMloLinksInfoWithIdleLinks();
        mCmi.sendMessage(WifiMonitor.MLO_LINKS_INFO_CHANGED,
                WifiMonitor.MloLinkInfoChangeReason.TID_TO_LINK_MAP);
        mLooper.dispatchAll();
        List<MloLink> links = mWifiInfo.getAffiliatedMloLinks();
        assertEquals(links.get(0).getState(), MloLink.MLO_LINK_STATE_ACTIVE);
        assertEquals(links.get(1).getState(), MloLink.MLO_LINK_STATE_IDLE);
        // All links are associated (ACTIVE & IDLE)
        assertEquals(mWifiInfo.getAssociatedMloLinks().size(),
                mWifiInfo.getAffiliatedMloLinks().size());

        // LInk Removal. Make sure removed link is UNASSOCIATED.
        reconfigureMloLinksInfoWithOneLink();
        mCmi.sendMessage(WifiMonitor.MLO_LINKS_INFO_CHANGED,
                WifiMonitor.MloLinkInfoChangeReason.MULTI_LINK_RECONFIG_AP_REMOVAL);
        mLooper.dispatchAll();
        links = mWifiInfo.getAffiliatedMloLinks();
        assertEquals(links.get(0).getState(), MloLink.MLO_LINK_STATE_ACTIVE);
        assertEquals(links.get(1).getState(), MloLink.MLO_LINK_STATE_UNASSOCIATED);
        // One link is unassociated.
        assertEquals(2, mWifiInfo.getAffiliatedMloLinks().size());
        assertEquals(1, mWifiInfo.getAssociatedMloLinks().size());

    }

    @Test
    public void verifyMloLinkChangeAfterAssociation() throws Exception {
        // Initialize
        connect();
        setScanResultWithMloInfo();
        setConnectionMloLinksInfo();
        mLooper.dispatchAll();

        // Association
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        // Verify all links are associated (ACTIVE) after assciation.
        assertEquals(mWifiInfo.getAssociatedMloLinks().size(),
                mWifiInfo.getAffiliatedMloLinks().size());

        // verify that affiliated link state the scan results are still in unassociated state.
        for (MloLink link : mScanResult.getAffiliatedMloLinks()) {
            assertEquals(MloLink.MLO_LINK_STATE_UNASSOCIATED, link.getState());
        }
        // verify that affiliated link state are active
        for (MloLink link : mWifiInfo.getAffiliatedMloLinks()) {
            assertEquals(MloLink.MLO_LINK_STATE_ACTIVE, link.getState());
        }

        setScanResultWithMloInfo();
        // Send FOUR_WAY_HANDSHAKE, GROUP_HANDSHAKE and COMPLETED and verify all links are still
        // asscoaited.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.FOUR_WAY_HANDSHAKE));

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.GROUP_HANDSHAKE));

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();
        // Verify all links are still associated (ACTIVE).
        assertEquals(mWifiInfo.getAssociatedMloLinks().size(),
                mWifiInfo.getAffiliatedMloLinks().size());
    }
    /**
     * Verify that an event that occurs on a managed network is handled by
     * logEventIfManagedNetwork.
     */
    @Test
    public void verifyEventHandledByLogEventIfManagedNetwork() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        MacAddress bssid = MacAddress.fromString(TEST_BSSID_STR);
        int numMaskedOctets = 4;

        mResources.setInteger(
                R.integer.config_wifiNumMaskedBssidOctetsInSecurityLog, numMaskedOctets);
        when(mWifiPermissionsUtil.isAdmin(anyInt(), any())).thenReturn(true);
        MockitoSession scanResultUtilSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(ScanResultUtil.class, withSettings().lenient())
                .startMocking();
        connect();

        // Connect will generate the Associated and Connected events. Confirm the events were
        // handled by checking that redactBssid() was called for each one.
        ExtendedMockito.verify(() ->
                ScanResultUtil.redactBssid(bssid, numMaskedOctets), times(2));
        scanResultUtilSession.finishMocking();
    }

    /**
     * Verify that QoS policy reset events are handled when mNetworkAgent is non-null.
     */
    @Test
    public void verifyQosPolicyResetEventWithNonNullNetworkAgent() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        assertNotNull(mCmi.mNetworkAgent);
        mCmi.sendMessage(WifiMonitor.QOS_POLICY_RESET_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent).sendRemoveAllDscpPolicies();
    }

    /**
     * Verify that QoS policy reset events are not handled when mNetworkAgent is null.
     */
    @Test
    public void verifyQosPolicyResetEventWithNullNetworkAgent() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        assertNull(mCmi.mNetworkAgent);
        mCmi.sendMessage(WifiMonitor.QOS_POLICY_RESET_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent, never()).sendRemoveAllDscpPolicies();
    }

    /**
     * Verify that QoS policy reset events are not handled if the QoS policy feature was
     * not enabled in WifiNative.
     */
    @Test
    public void verifyQosPolicyResetEventWhenQosFeatureNotEnabled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        connect();
        assertNotNull(mCmi.mNetworkAgent);
        when(mWifiNative.isQosPolicyFeatureEnabled()).thenReturn(false);
        mCmi.sendMessage(WifiMonitor.QOS_POLICY_RESET_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent, never()).sendRemoveAllDscpPolicies();
    }

    /**
     * Verify that QoS policy request events are handled when mNetworkAgent is non-null.
     */
    @Test
    public void verifyQosPolicyRequestEvent() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        final int dialogToken = 124;

        // Event should be ignored by the QosPolicyRequestHandler if no network is connected,
        // since mNetworkAgent is null.
        assertNull(mCmi.mNetworkAgent);
        mCmi.sendMessage(WifiMonitor.QOS_POLICY_REQUEST_EVENT, dialogToken, 0,
                new ArrayList<SupplicantStaIfaceHal.QosPolicyRequest>());
        mLooper.dispatchAll();

        // New events should be processed after connecting to a network,
        // since mNetworkAgent is not null.
        connect();
        assertNotNull(mCmi.mNetworkAgent);
        mCmi.sendMessage(WifiMonitor.QOS_POLICY_REQUEST_EVENT, dialogToken + 1, 0,
                new ArrayList<SupplicantStaIfaceHal.QosPolicyRequest>());
        mLooper.dispatchAll();

        verify(mWifiNative, never()).sendQosPolicyResponse(
                eq(WIFI_IFACE_NAME), eq(dialogToken), eq(true), any());
        verify(mWifiNative).sendQosPolicyResponse(
                eq(WIFI_IFACE_NAME), eq(dialogToken + 1), eq(true), any());
    }

    private void verifyConnectWithDisabledType(
            WifiConfiguration config, List<ScanResult> results,
            boolean shouldDropRequest) throws Exception {
        WifiDialogManager mockWifiDialogManager = mock(WifiDialogManager.class);
        WifiDialogManager.DialogHandle mockDialogHandle =
                mock(WifiDialogManager.DialogHandle.class);
        when(mockWifiDialogManager.createLegacySimpleDialog(any(), any(), any(), any(), any(),
                any(), any())).thenReturn(mockDialogHandle);
        when(mWifiInjector.getWifiDialogManager()).thenReturn(mockWifiDialogManager);
        // Add some irrelevant networks to ensure they are filtered.
        final String irrelevantSsid = "IrrelevantSsid";
        results.add(makeScanResult(irrelevantSsid, "[PSK]"));
        results.add(makeScanResult(irrelevantSsid, "[SAE]"));
        results.add(makeScanResult(irrelevantSsid, "[PSK][SAE]"));
        results.add(makeScanResult(irrelevantSsid, ""));
        results.add(makeScanResult(irrelevantSsid, "[OWE_TRANSITION]"));
        results.add(makeScanResult(irrelevantSsid, "[OWE]"));
        results.add(makeScanResult(irrelevantSsid, "[RSN][EAP/SHA1][MFPC]"));
        results.add(makeScanResult(irrelevantSsid, "[RSN][EAP/SHA1][EAP/SHA256][MFPC]"));
        results.add(makeScanResult(irrelevantSsid, "[RSN][EAP/SHA256][MFPC][MFPR]"));
        when(mScanRequestProxy.getScanResults()).thenReturn(results);

        initializeAndAddNetworkAndVerifySuccess();

        when(mWifiConfigManager.getConfiguredNetworkWithoutMasking(TEST_NETWORK_ID))
                .thenReturn(config);
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(config);

        IActionListener connectActionListener = mock(IActionListener.class);
        mCmi.connectNetwork(
                new NetworkUpdateResult(TEST_NETWORK_ID),
                new ActionListenerWrapper(connectActionListener),
                Process.SYSTEM_UID, OP_PACKAGE_NAME, null);
        mLooper.dispatchAll();
        verify(connectActionListener).onSuccess();
        if (shouldDropRequest) {
            verify(mockDialogHandle).launchDialog();
            verify(mWifiNative, never()).connectToNetwork(any(), any());
        } else {
            verify(mockDialogHandle, never()).launchDialog();
            verify(mWifiNative).connectToNetwork(eq(WIFI_IFACE_NAME), eq(config));
        }
    }

    private ScanResult makeScanResult(String ssid, String caps) {
        ScanResult scanResult = new ScanResult.Builder(
                WifiSsid.fromUtf8Text(ssid.replace("\"", "")),
                TEST_BSSID_STR)
                .setHessid(1245)
                .setCaps(caps)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResult.InformationElement ie = createIE(ScanResult.InformationElement.EID_SSID,
                ssid.getBytes(StandardCharsets.UTF_8));
        scanResult.informationElements = new ScanResult.InformationElement[]{ie};
        return scanResult;
    }

    private void verifyConnectWithDisabledPskType(
            boolean isWpa2PersonalOnlyNetworkInRange,
            boolean isWpa2Wpa3PersonalTransitionNetworkInRange,
            boolean isWpa3PersonalOnlyNetworkInRange, boolean shouldDropRequest)
            throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.networkId = TEST_NETWORK_ID;
        config.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_PSK, false);
        List<ScanResult> results = new ArrayList<>();
        if (isWpa2PersonalOnlyNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[PSK]"));
        }
        if (isWpa2Wpa3PersonalTransitionNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[PSK][SAE]"));
        }
        if (isWpa3PersonalOnlyNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[SAE]"));
        }

        verifyConnectWithDisabledType(config, results, shouldDropRequest);
    }

    @Test
    public void testConnectDropWithOnlyDisabledPskNetwork() throws Exception {
        boolean isWpa2PersonalOnlyNetworkInRange = true;
        boolean isWpa2Wpa3PersonalTransitionNetworkInRange = false;
        boolean isWpa3PersonalOnlyNetworkInRange = false;
        boolean shouldDropRequest = true;

        verifyConnectWithDisabledPskType(isWpa2PersonalOnlyNetworkInRange,
                isWpa2Wpa3PersonalTransitionNetworkInRange,
                isWpa3PersonalOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithPskSaeNetwork() throws Exception {
        boolean isWpa2PersonalOnlyNetworkInRange = true;
        boolean isWpa2Wpa3PersonalTransitionNetworkInRange = true;
        boolean isWpa3PersonalOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledPskType(isWpa2PersonalOnlyNetworkInRange,
                isWpa2Wpa3PersonalTransitionNetworkInRange,
                isWpa3PersonalOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithSaeNetwork() throws Exception {
        boolean isWpa2PersonalOnlyNetworkInRange = true;
        boolean isWpa2Wpa3PersonalTransitionNetworkInRange = false;
        boolean isWpa3PersonalOnlyNetworkInRange = true;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledPskType(isWpa2PersonalOnlyNetworkInRange,
                isWpa2Wpa3PersonalTransitionNetworkInRange,
                isWpa3PersonalOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithoutPskNetwork() throws Exception {
        boolean isWpa2PersonalOnlyNetworkInRange = false;
        boolean isWpa2Wpa3PersonalTransitionNetworkInRange = true;
        boolean isWpa3PersonalOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledPskType(isWpa2PersonalOnlyNetworkInRange,
                isWpa2Wpa3PersonalTransitionNetworkInRange,
                isWpa3PersonalOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectDisabledPskAllowedForNonUserSelectedNetwork() throws Exception {
        boolean isWpa2PersonalOnlyNetworkInRange = true;
        boolean isWpa2Wpa3PersonalTransitionNetworkInRange = false;
        boolean isWpa3PersonalOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        // Only a request from settings or setup wizard are considered a user selected network.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);

        verifyConnectWithDisabledPskType(isWpa2PersonalOnlyNetworkInRange,
                isWpa2Wpa3PersonalTransitionNetworkInRange,
                isWpa3PersonalOnlyNetworkInRange, shouldDropRequest);
    }

    private void verifyConnectWithDisabledOpenType(
            boolean isOpenOnlyNetworkInRange,
            boolean isOpenOweTransitionNetworkInRange,
            boolean isOweOnlyNetworkInRange, boolean shouldDropRequest)
            throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenOweNetwork();
        config.networkId = TEST_NETWORK_ID;
        config.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_OPEN, false);
        List<ScanResult> results = new ArrayList<>();
        if (isOpenOnlyNetworkInRange) {
            results.add(makeScanResult(config.SSID, ""));
        }
        if (isOpenOweTransitionNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[OWE_TRANSITION]"));
        }
        if (isOweOnlyNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[OWE]"));
        }

        verifyConnectWithDisabledType(config, results, shouldDropRequest);
    }

    @Test
    public void testConnectDropWithOnlyDisabledOpenNetwork() throws Exception {
        boolean isOpenOnlyNetworkInRange = true;
        boolean isOpenOweTransitionNetworkInRange = false;
        boolean isOweOnlyNetworkInRange = false;
        boolean shouldDropRequest = true;

        verifyConnectWithDisabledOpenType(isOpenOnlyNetworkInRange,
                isOpenOweTransitionNetworkInRange,
                isOweOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithOpenOweNetwork() throws Exception {
        boolean isOpenOnlyNetworkInRange = true;
        boolean isOpenOweTransitionNetworkInRange = true;
        boolean isOweOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledOpenType(isOpenOnlyNetworkInRange,
                isOpenOweTransitionNetworkInRange,
                isOweOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithOweNetwork() throws Exception {
        boolean isOpenOnlyNetworkInRange = true;
        boolean isOpenOweTransitionNetworkInRange = false;
        boolean isOweOnlyNetworkInRange = true;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledOpenType(isOpenOnlyNetworkInRange,
                isOpenOweTransitionNetworkInRange,
                isOweOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithoutOpenNetwork() throws Exception {
        boolean isOpenOnlyNetworkInRange = false;
        boolean isOpenOweTransitionNetworkInRange = true;
        boolean isOweOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledOpenType(isOpenOnlyNetworkInRange,
                isOpenOweTransitionNetworkInRange,
                isOweOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectDisabledOpenAllowedForNonUserSelectedNetwork() throws Exception {
        boolean isOpenOnlyNetworkInRange = true;
        boolean isOpenOweTransitionNetworkInRange = false;
        boolean isOweOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        // Only a request from settings or setup wizard are considered a user selected network.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);

        verifyConnectWithDisabledOpenType(isOpenOnlyNetworkInRange,
                isOpenOweTransitionNetworkInRange,
                isOweOnlyNetworkInRange, shouldDropRequest);
    }

    private void verifyConnectWithDisabledWpa2EnterpriseType(
            boolean isWpa2EnterpriseOnlyNetworkInRange,
            boolean isWpa2Wpa3EnterpriseTransitionNetworkInRange,
            boolean isWpa3EnterpriseOnlyNetworkInRange, boolean shouldDropRequest)
            throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork();
        config.networkId = TEST_NETWORK_ID;
        config.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_EAP, false);
        List<ScanResult> results = new ArrayList<>();
        if (isWpa2EnterpriseOnlyNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[RSN][EAP/SHA1][MFPC]"));
        }
        if (isWpa2Wpa3EnterpriseTransitionNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[RSN][EAP/SHA1][EAP/SHA256][MFPC]"));
        }
        if (isWpa3EnterpriseOnlyNetworkInRange) {
            results.add(makeScanResult(config.SSID, "[RSN][EAP/SHA256][MFPC][MFPR]"));
        }

        verifyConnectWithDisabledType(config, results, shouldDropRequest);
    }

    @Test
    public void testConnectDropWithOnlyDisabledWpa2EnterpriseNetwork() throws Exception {
        boolean isWpa2EnterpriseOnlyNetworkInRange = true;
        boolean isWpa2Wpa3EnterpriseTransitionNetworkInRange = false;
        boolean isWpa3EnterpriseOnlyNetworkInRange = false;
        boolean shouldDropRequest = true;

        verifyConnectWithDisabledWpa2EnterpriseType(isWpa2EnterpriseOnlyNetworkInRange,
                isWpa2Wpa3EnterpriseTransitionNetworkInRange,
                isWpa3EnterpriseOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithWpa2Wpa3EnterpriseNetwork() throws Exception {
        boolean isWpa2EnterpriseOnlyNetworkInRange = true;
        boolean isWpa2Wpa3EnterpriseTransitionNetworkInRange = true;
        boolean isWpa3EnterpriseOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledWpa2EnterpriseType(isWpa2EnterpriseOnlyNetworkInRange,
                isWpa2Wpa3EnterpriseTransitionNetworkInRange,
                isWpa3EnterpriseOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithWpa3EnterpriseNetwork() throws Exception {
        boolean isWpa2EnterpriseOnlyNetworkInRange = true;
        boolean isWpa2Wpa3EnterpriseTransitionNetworkInRange = false;
        boolean isWpa3EnterpriseOnlyNetworkInRange = true;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledWpa2EnterpriseType(isWpa2EnterpriseOnlyNetworkInRange,
                isWpa2Wpa3EnterpriseTransitionNetworkInRange,
                isWpa3EnterpriseOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectAllowedWithoutWpa2EnterpriseNetwork() throws Exception {
        boolean isWpa2EnterpriseOnlyNetworkInRange = false;
        boolean isWpa2Wpa3EnterpriseTransitionNetworkInRange = true;
        boolean isWpa3EnterpriseOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        verifyConnectWithDisabledWpa2EnterpriseType(isWpa2EnterpriseOnlyNetworkInRange,
                isWpa2Wpa3EnterpriseTransitionNetworkInRange,
                isWpa3EnterpriseOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testConnectDisabledWpa2EnterpriseAllowedForNonUserSelectedNetwork()
            throws Exception {
        boolean isWpa2EnterpriseOnlyNetworkInRange = true;
        boolean isWpa2Wpa3EnterpriseTransitionNetworkInRange = false;
        boolean isWpa3EnterpriseOnlyNetworkInRange = false;
        boolean shouldDropRequest = false;

        // Only a request from settings or setup wizard are considered a user selected network.
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(anyInt())).thenReturn(false);

        verifyConnectWithDisabledWpa2EnterpriseType(isWpa2EnterpriseOnlyNetworkInRange,
                isWpa2Wpa3EnterpriseTransitionNetworkInRange,
                isWpa3EnterpriseOnlyNetworkInRange, shouldDropRequest);
    }

    @Test
    public void testUpdateAkmByConnectionInfo() throws Exception {
        mConnectedNetwork = spy(WifiConfigurationTestUtil.createPskSaeNetwork());

        triggerConnect();

        // WifiInfo is not updated yet.
        assertEquals(WifiInfo.SECURITY_TYPE_UNKNOWN, mWifiInfo.getCurrentSecurityType());

        WifiSsid wifiSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(mConnectedNetwork.SSID)));
        BitSet akm = new BitSet();
        akm.set(WifiConfiguration.KeyMgmt.SAE);
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, akm));
        mLooper.dispatchAll();
        // WifiInfo is updated to the actual used type.
        assertEquals(WifiInfo.SECURITY_TYPE_SAE, mWifiInfo.getCurrentSecurityType());

        // Roam to a PSK BSS.
        akm.clear();
        akm.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, akm));
        mLooper.dispatchAll();
        // WifiInfo is updated to the actual used type.
        assertEquals(WifiInfo.SECURITY_TYPE_PSK, mWifiInfo.getCurrentSecurityType());

        // Roam back to a SAE BSS.
        akm.clear();
        akm.set(WifiConfiguration.KeyMgmt.SAE);
        mCmi.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, akm));
        mLooper.dispatchAll();
        // WifiInfo is updated to the actual used type.
        assertEquals(WifiInfo.SECURITY_TYPE_SAE, mWifiInfo.getCurrentSecurityType());
    }

    @Test
    public void testUpdateWpa3EnterpriseSecurityTypeByConnectionInfo() throws Exception {
        // Create a WifiConfiguration with WPA2 enterprise and WPA3 enterprise security params
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork());
        config.networkId = FRAMEWORK_NETWORK_ID;

        SecurityParams wpa3EnterpriseParams =
                config.getSecurityParams(SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        assertNotNull(wpa3EnterpriseParams);
        // Trigger connection with security params as WPA3 enterprise where PMF is mandatory
        config.getNetworkSelectionStatus().setLastUsedSecurityParams(wpa3EnterpriseParams);
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);

        // WifiInfo is not updated yet.
        assertEquals(WifiInfo.SECURITY_TYPE_UNKNOWN, mWifiInfo.getCurrentSecurityType());

        WifiSsid wifiSsid =
                WifiSsid.fromBytes(
                        NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(config.SSID)));

        BitSet akm = new BitSet();
        akm.set(WifiConfiguration.KeyMgmt.WPA_EAP_SHA256);
        // Send network connection event with WPA_EAP_SHA256 AKM
        mCmi.sendMessage(
                WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, wifiSsid, TEST_BSSID_STR, false, akm));
        mLooper.dispatchAll();
        // WifiInfo is updated with WPA3-Enterprise security type derived from the AKM sent in
        // network connection event and the PMF settings used in connection.
        assertEquals(
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE, mWifiInfo.getCurrentSecurityType());
    }

    @Test
    public void testEnableTdls() throws Exception {
        connect();
        when(mWifiNative.getMaxSupportedConcurrentTdlsSessions(WIFI_IFACE_NAME)).thenReturn(1);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME))
                .thenReturn(WifiManager.WIFI_FEATURE_TDLS);
        when(mWifiNative.startTdls(eq(WIFI_IFACE_NAME), eq(TEST_TDLS_PEER_ADDR_STR), anyBoolean()))
                .thenReturn(true);
        assertEquals(1, mCmi.getMaxSupportedConcurrentTdlsSessions());
        assertTrue(mCmi.isTdlsOperationCurrentlyAvailable());
        assertTrue(mCmi.enableTdls(TEST_TDLS_PEER_ADDR_STR, true));
        assertEquals(1, mCmi.getNumberOfEnabledTdlsSessions());
        verify(mWifiNative).startTdls(eq(WIFI_IFACE_NAME), eq(TEST_TDLS_PEER_ADDR_STR),
                eq(true));
        assertFalse(mCmi.enableTdls(TEST_TDLS_PEER_ADDR_STR, true));
        assertTrue(mCmi.enableTdls(TEST_TDLS_PEER_ADDR_STR, false));
        verify(mWifiNative).startTdls(eq(WIFI_IFACE_NAME), eq(TEST_TDLS_PEER_ADDR_STR),
                eq(false));
        assertEquals(0, mCmi.getNumberOfEnabledTdlsSessions());
    }

    /**
     * Verify that the frequency will be updated on receiving BSS_FREQUENCY_CHANGED_EVENT event.
     */
    @Test
    public void testBssFrequencyChangedUpdatesFrequency() throws Exception {
        connect();
        assertEquals(sFreq, mWifiInfo.getFrequency());
        mCmi.sendMessage(WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT, sFreq1);
        mLooper.dispatchAll();
        assertEquals(sFreq1, mWifiInfo.getFrequency());
    }

    /**
     * Verify that the frequency is updated on roaming even when the scan result doesn't have the
     * roamed BSS scan entry.
     */
    @Test
    public void testFrequencyUpdateOnRoamWithoutScanResultEntry() throws Exception {
        // Connect to network with |TEST_BSSID_STR|, |sFreq|.
        connect();

        // Return null scan detail cache for roaming target.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(TEST_BSSID_STR1)).thenReturn(null);

        // This simulates the behavior of roaming to network with |TEST_BSSID_STR1|, |sFreq1|.
        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is updated with new frequency.
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, TEST_WIFI_SSID, TEST_BSSID_STR1, sFreq1,
                        SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWifiInfo;
        assertEquals(sFreq1, wifiInfo.getFrequency());
    }

    /**
     * Verify that static attributes (channel, state, link id and band) of the affiliated MLO links
     * are not changed after signal poll.
     */
    @Test
    public void testMloLinkAttributesAfterSignalPoll() throws Exception {
        connect();
        setScanResultWithMloInfo();

        // Associate to one link only
        mConnectionCapabilities.wifiStandard = ScanResult.WIFI_STANDARD_11BE;
        WifiNative.ConnectionMloLinksInfo info = new WifiNative.ConnectionMloLinksInfo();
        info.links = new WifiNative.ConnectionMloLink[1];
        info.links[0] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID, TEST_MLO_LINK_ADDR,
                TEST_AP_MLD_MAC_ADDRESS, Byte.MAX_VALUE, Byte.MAX_VALUE, 2437);
        when(mWifiNative.getConnectionMloLinksInfo(WIFI_IFACE_NAME)).thenReturn(info);

        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        // Verify the link attributes
        List<MloLink> links = mWifiInfo.getAffiliatedMloLinks();
        assertEquals(links.get(0).getState(), MloLink.MLO_LINK_STATE_ACTIVE);
        assertEquals(links.get(0).getBand(), WifiScanner.WIFI_BAND_24_GHZ);
        assertEquals(links.get(0).getChannel(), TEST_CHANNEL);
        assertEquals(links.get(0).getLinkId(), TEST_MLO_LINK_ID);
        assertEquals(links.get(1).getState(), MloLink.MLO_LINK_STATE_UNASSOCIATED);
        assertEquals(links.get(1).getBand(), WifiScanner.WIFI_BAND_5_GHZ);
        assertEquals(links.get(1).getChannel(), TEST_CHANNEL_1);
        assertEquals(links.get(1).getLinkId(), TEST_MLO_LINK_ID_1);

        // Signal poll for associated link only
        final long startMillis = 1_500_000_000_100L;
        WifiLinkLayerStats llStats = new WifiLinkLayerStats();
        llStats.txmpdu_be = 1000;
        llStats.rxmpdu_bk = 2000;
        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(TEST_MLO_LINK_ID, -42, 65, 54, sFreq);
        when(mWifiNative.getSupportedFeatureSet(WIFI_IFACE_NAME)).thenReturn(
                WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        when(mWifiNative.getWifiLinkLayerStats(any())).thenReturn(llStats);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);
        when(mClock.getWallClockMillis()).thenReturn(startMillis + 0);
        mCmi.enableRssiPolling(true);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();

        // Make sure static link attributes has not changed
        links = mWifiInfo.getAffiliatedMloLinks();
        assertEquals(links.get(0).getState(), MloLink.MLO_LINK_STATE_ACTIVE);
        assertEquals(links.get(0).getBand(), WifiScanner.WIFI_BAND_24_GHZ);
        assertEquals(links.get(0).getChannel(), TEST_CHANNEL);
        assertEquals(links.get(0).getLinkId(), TEST_MLO_LINK_ID);
        assertEquals(links.get(1).getState(), MloLink.MLO_LINK_STATE_UNASSOCIATED);
        assertEquals(links.get(1).getBand(), WifiScanner.WIFI_BAND_5_GHZ);
        assertEquals(links.get(1).getChannel(), TEST_CHANNEL_1);
        assertEquals(links.get(1).getLinkId(), TEST_MLO_LINK_ID_1);

        // Make sure the dynamic attributes (Tx link speed, Rx link speed and RSSI) are matching
        // with poll results for associated links.
        assertEquals(65, links.get(0).getTxLinkSpeedMbps());
        assertEquals(54, links.get(0).getRxLinkSpeedMbps());
        assertEquals(-42, links.get(0).getRssi());

        // Make sure the RSSI is matching with scan cache for un-associated links
        assertEquals(TEST_RSSI, links.get(1).getRssi());

        // Send signal poll for un-associated link
        signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(TEST_MLO_LINK_ID_1, -42, 65, 54, sFreq);
        when(mWifiNative.signalPoll(any())).thenReturn(signalPollResults);
        mCmi.enableRssiPolling(true);
        mCmi.sendMessage(ClientModeImpl.CMD_RSSI_POLL, 1);
        mLooper.dispatchAll();
        links = mWifiInfo.getAffiliatedMloLinks();

        // Make sure the RSSI is matching with scan result for associated link
        assertEquals(TEST_RSSI, links.get(0).getRssi());

    }

    /**
     * Verify that during DHCP process, 1. If P2P is in waiting state, clientModeImpl doesn't send a
     * message to block P2P discovery. 2. If P2P is not in waiting state, clientModeImpl sends a
     * message to block P2P discovery. 3. On DHCP completion, clientModeImpl sends a message to
     * unblock P2P discovery.
     */
    @Test
    public void testP2pBlockDiscoveryDuringDhcp() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        startConnectSuccess();

        mCmi.sendMessage(
                WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT,
                0,
                0,
                new StateChangeResult(
                        0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq, SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();

        mCmi.sendMessage(
                WifiMonitor.NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(0, TEST_WIFI_SSID, TEST_BSSID_STR, false, null));
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionSuccess(TEST_BSSID_STR, TEST_SSID);

        mCmi.sendMessage(
                WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT,
                0,
                0,
                new StateChangeResult(
                        0, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("L3ProvisioningState", getCurrentState().getName());

        when(mWifiP2pConnection.isConnected()).thenReturn(true);
        when(mWifiP2pConnection.isP2pInWaitingState()).thenReturn(true);

        mIpClientCallback.onPreDhcpAction();
        mLooper.dispatchAll();
        verify(mWifiP2pConnection, never()).sendMessage(anyInt(), anyInt(), anyInt());
        verify(mIpClient).completedPreDhcpAction();

        when(mWifiP2pConnection.isConnected()).thenReturn(true);
        when(mWifiP2pConnection.isP2pInWaitingState()).thenReturn(false);

        mIpClientCallback.onPreDhcpAction();
        mLooper.dispatchAll();
        verify(mWifiP2pConnection)
                .sendMessage(
                        eq(WifiP2pServiceImpl.BLOCK_DISCOVERY),
                        eq(WifiP2pServiceImpl.ENABLED),
                        eq(CMD_PRE_DHCP_ACTION_COMPLETE));
        verify(mIpClient).completedPreDhcpAction();

        mIpClientCallback.onPostDhcpAction();
        mLooper.dispatchAll();
        verify(mWifiP2pConnection)
                .sendMessage(
                        eq(WifiP2pServiceImpl.BLOCK_DISCOVERY), eq(WifiP2pServiceImpl.DISABLED));
    }

    /**
     * Verify that MLO link will be updated on receiving BSS_FREQUENCY_CHANGED_EVENT event.
     */
    @Test
    public void testBssFrequencyChangedUpdatesMloLink() throws Exception {
        connect();
        mLooper.dispatchAll();

        mConnectionCapabilities.wifiStandard = ScanResult.WIFI_STANDARD_11BE;
        WifiNative.ConnectionMloLinksInfo info = new WifiNative.ConnectionMloLinksInfo();
        info.links = new WifiNative.ConnectionMloLink[2];
        info.links[0] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID, TEST_MLO_LINK_ADDR,
                TEST_AP_MLD_MAC_ADDRESS, Byte.MIN_VALUE, Byte.MAX_VALUE, TEST_MLO_LINK_FREQ);
        info.links[1] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID_1, TEST_MLO_LINK_ADDR_1,
                TEST_AP_MLD_MAC_ADDRESS, Byte.MAX_VALUE, Byte.MIN_VALUE, TEST_MLO_LINK_FREQ_1);
        when(mWifiNative.getConnectionMloLinksInfo(WIFI_IFACE_NAME)).thenReturn(info);
        mCmi.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(FRAMEWORK_NETWORK_ID, TEST_WIFI_SSID, TEST_BSSID_STR, sFreq,
                        SupplicantState.ASSOCIATED));
        mLooper.dispatchAll();
        assertEquals(mWifiInfo.getAssociatedMloLinks().size(),
                mWifiInfo.getAffiliatedMloLinks().size());

        info.links[0] = new WifiNative.ConnectionMloLink(TEST_MLO_LINK_ID, TEST_MLO_LINK_ADDR,
                TEST_AP_MLD_MAC_ADDRESS, Byte.MIN_VALUE, Byte.MAX_VALUE, 6215);
        when(mWifiNative.getConnectionMloLinksInfo(WIFI_IFACE_NAME)).thenReturn(info);
        mCmi.sendMessage(WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT, 6215);
        mLooper.dispatchAll();
        List<MloLink> links = mWifiInfo.getAffiliatedMloLinks();
        assertEquals(53, links.get(0).getChannel());
        assertEquals(WifiScanner.WIFI_BAND_6_GHZ, links.get(0).getBand());
    }

    /**
     * Verify that we disconnect Wi-Fi 7 is toggled.
     */
    @Test
    public void verifyDisconnectOnTogglingWifi7() throws Exception {
        connect();

        WifiConfiguration oldConfig = new WifiConfiguration(mConnectedNetwork);
        mConnectedNetwork.setWifi7Enabled(false);

        for (WifiConfigManager.OnNetworkUpdateListener listener : mConfigUpdateListenerCaptor
                .getAllValues()) {
            listener.onNetworkUpdated(mConnectedNetwork, oldConfig, false);
        }
        mLooper.dispatchAll();
        verify(mWifiNative).disconnect(WIFI_IFACE_NAME);
        verify(mWifiMetrics).logStaEvent(anyString(), eq(StaEvent.TYPE_FRAMEWORK_DISCONNECT),
                eq(StaEvent.DISCONNECT_NETWORK_WIFI7_TOGGLED));
    }

    private void testDhcpHostnameSetting(
            boolean configEnabled,
            @WifiManager.SendDhcpHostnameRestriction int restriction,
            int security,
            int expectedHostnameSetting) throws Exception {
        if (!SdkLevel.isAtLeastV()) {
            expectedHostnameSetting = IIpClient.HOSTNAME_SETTING_UNSET;
        }
        when(mWifiGlobals.getSendDhcpHostnameRestriction()).thenReturn(restriction);
        mConnectedNetwork.setSecurityParams(security);
        mConnectedNetwork.setSendDhcpHostnameEnabled(configEnabled);
        connect();

        verify(mIpClient).startProvisioning(mProvisioningConfigurationCaptor.capture());
        assertEquals(expectedHostnameSetting,
                mProvisioningConfigurationCaptor.getValue().hostnameSetting);
    }

    @Test
    public void testSendDhcpHostnameEnabled() throws Exception {
        testDhcpHostnameSetting(true, 0, SECURITY_TYPE_OPEN, IIpClient.HOSTNAME_SETTING_SEND);
    }

    @Test
    public void testSendDhcpHostnameDisabled() throws Exception {
        testDhcpHostnameSetting(false, 0,
                SECURITY_TYPE_OPEN, IIpClient.HOSTNAME_SETTING_DO_NOT_SEND);
    }

    @Test
    public void testSendDhcpHostnameEnabledWithOpenRestriction() throws Exception {
        testDhcpHostnameSetting(true, WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN,
                SECURITY_TYPE_OPEN, IIpClient.HOSTNAME_SETTING_DO_NOT_SEND);
    }

    @Test
    public void testSendDhcpHostnameEnabledWithOpenRestrictionOwe() throws Exception {
        testDhcpHostnameSetting(true, WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN,
                SECURITY_TYPE_OWE, IIpClient.HOSTNAME_SETTING_DO_NOT_SEND);
    }

    @Test
    public void testSendDhcpHostnameEnabledWithSecureRestriction() throws Exception {
        testDhcpHostnameSetting(true, WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_SECURE,
                SECURITY_TYPE_PSK, IIpClient.HOSTNAME_SETTING_DO_NOT_SEND);
    }
}
