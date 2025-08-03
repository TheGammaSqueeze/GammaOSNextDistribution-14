/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.net.cts;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.NETWORK_FACTORY;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.NETWORK_SETUP_WIZARD;
import static android.Manifest.permission.NETWORK_STACK;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.TETHER_PRIVILEGED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;
import static android.content.pm.PackageManager.FEATURE_ETHERNET;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.content.pm.PackageManager.FEATURE_USB_HOST;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.pm.PackageManager.FEATURE_WIFI;
import static android.content.pm.PackageManager.FEATURE_WIFI_DIRECT;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.EXTRA_NETWORK;
import static android.net.ConnectivityManager.EXTRA_NETWORK_REQUEST;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_BACKGROUND;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_DOZABLE;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_LOW_POWER_STANDBY;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_2;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_POWERSAVE;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_RESTRICTED;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_STANDBY;
import static android.net.ConnectivityManager.FIREWALL_RULE_ALLOW;
import static android.net.ConnectivityManager.FIREWALL_RULE_DEFAULT;
import static android.net.ConnectivityManager.FIREWALL_RULE_DENY;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE;
import static android.net.ConnectivityManager.TYPE_BLUETOOTH;
import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE_CBS;
import static android.net.ConnectivityManager.TYPE_MOBILE_DUN;
import static android.net.ConnectivityManager.TYPE_MOBILE_EMERGENCY;
import static android.net.ConnectivityManager.TYPE_MOBILE_FOTA;
import static android.net.ConnectivityManager.TYPE_MOBILE_HIPRI;
import static android.net.ConnectivityManager.TYPE_MOBILE_IA;
import static android.net.ConnectivityManager.TYPE_MOBILE_IMS;
import static android.net.ConnectivityManager.TYPE_MOBILE_MMS;
import static android.net.ConnectivityManager.TYPE_MOBILE_SUPL;
import static android.net.ConnectivityManager.TYPE_PROXY;
import static android.net.ConnectivityManager.TYPE_VPN;
import static android.net.ConnectivityManager.TYPE_WIFI_P2P;
import static android.net.ConnectivitySettingsManager.setUidsAllowedOnRestrictedNetworks;
import static android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND;
import static android.net.NetworkCapabilities.NET_CAPABILITY_IMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.net.cts.util.CtsNetUtils.ConnectivityActionReceiver;
import static android.net.cts.util.CtsNetUtils.HTTP_PORT;
import static android.net.cts.util.CtsNetUtils.NETWORK_CALLBACK_ACTION;
import static android.net.cts.util.CtsNetUtils.TEST_HOST;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;
import static android.net.cts.util.CtsTetheringUtils.TestTetheringEventCallback;
import static android.os.MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT;
import static android.os.Process.INVALID_UID;
import static android.provider.Settings.Global.NETWORK_METERED_MULTIPATH_PREFERENCE;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.AF_UNSPEC;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.modules.utils.build.SdkLevel.isAtLeastS;
import static com.android.net.module.util.NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTPS_URL;
import static com.android.net.module.util.NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTP_URL;
import static com.android.networkstack.apishim.ConstantsShim.BLOCKED_REASON_LOCKDOWN_VPN;
import static com.android.networkstack.apishim.ConstantsShim.BLOCKED_REASON_NONE;
import static com.android.networkstack.apishim.ConstantsShim.RECEIVER_EXPORTED;
import static com.android.testutils.Cleanup.testAndCleanup;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.TestNetworkTrackerKt.initTestNetwork;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.CaptivePortalData;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.ConnectivitySettingsManager;
import android.net.InetAddresses;
import android.net.IpSecManager;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.NetworkStateSnapshot;
import android.net.OemNetworkPreferences;
import android.net.ProxyInfo;
import android.net.SocketKeepalive;
import android.net.TelephonyNetworkSpecifier;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.Uri;
import android.net.cts.util.CtsNetUtils;
import android.net.cts.util.CtsTetheringUtils;
import android.net.util.KeepaliveUtils;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VintfRuntimeInfo;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Range;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.ArrayUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.CollectionUtils;
import com.android.networkstack.apishim.ConnectivityManagerShimImpl;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.NetworkInformationShimImpl;
import com.android.networkstack.apishim.common.ConnectivityManagerShim;
import com.android.testutils.CompatUtil;
import com.android.testutils.ConnectivityModuleTest;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DeviceConfigRule;
import com.android.testutils.DeviceInfoUtils;
import com.android.testutils.DumpTestUtils;
import com.android.testutils.RecorderCallback.CallbackEntry;
import com.android.testutils.SkipMainlinePresubmit;
import com.android.testutils.SkipPresubmit;
import com.android.testutils.TestHttpServer;
import com.android.testutils.TestNetworkTracker;
import com.android.testutils.TestableNetworkCallback;

import junit.framework.AssertionFailedError;

import libcore.io.Streams;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;

@RunWith(AndroidJUnit4.class)
public class ConnectivityManagerTest {
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    @Rule
    public final DeviceConfigRule mTestValidationConfigRule = new DeviceConfigRule(
            5 /* retryCountBeforeSIfConfigChanged */);

    private static final String TAG = ConnectivityManagerTest.class.getSimpleName();

    public static final int TYPE_MOBILE = ConnectivityManager.TYPE_MOBILE;
    public static final int TYPE_WIFI = ConnectivityManager.TYPE_WIFI;

    private static final int HOST_ADDRESS = 0x7f000001;// represent ip 127.0.0.1
    private static final int KEEPALIVE_CALLBACK_TIMEOUT_MS = 2000;
    private static final int INTERVAL_KEEPALIVE_RETRY_MS = 500;
    private static final int MAX_KEEPALIVE_RETRY_COUNT = 3;
    private static final int MIN_KEEPALIVE_INTERVAL = 10;

    private static final int NETWORK_CALLBACK_TIMEOUT_MS = 30_000;
    // Timeout for waiting network to be validated.
    private static final int LISTEN_ACTIVITY_TIMEOUT_MS = 30_000;
    private static final int NO_CALLBACK_TIMEOUT_MS = 100;
    private static final int NETWORK_REQUEST_TIMEOUT_MS = 3000;
    private static final int SOCKET_TIMEOUT_MS = 100;
    private static final int NUM_TRIES_MULTIPATH_PREF_CHECK = 20;
    private static final long INTERVAL_MULTIPATH_PREF_CHECK_MS = 500;
    // device could have only one interface: data, wifi.
    private static final int MIN_NUM_NETWORK_TYPES = 1;

    // Airplane Mode BroadcastReceiver Timeout
    private static final long AIRPLANE_MODE_CHANGE_TIMEOUT_MS = 10_000L;

    // Timeout for applying uids allowed on restricted networks
    private static final long APPLYING_UIDS_ALLOWED_ON_RESTRICTED_NETWORKS_TIMEOUT_MS = 3_000L;

    // Minimum supported keepalive counts for wifi and cellular.
    public static final int MIN_SUPPORTED_CELLULAR_KEEPALIVE_COUNT = 1;
    public static final int MIN_SUPPORTED_WIFI_KEEPALIVE_COUNT = 3;

    private static final String NETWORK_METERED_MULTIPATH_PREFERENCE_RES_NAME =
            "config_networkMeteredMultipathPreference";
    private static final String KEEPALIVE_ALLOWED_UNPRIVILEGED_RES_NAME =
            "config_allowedUnprivilegedKeepalivePerUid";
    private static final String KEEPALIVE_RESERVED_PER_SLOT_RES_NAME =
            "config_reservedPrivilegedKeepaliveSlots";
    private static final String TEST_RESTRICTED_NW_IFACE_NAME = "test-restricted-nw";

    private static final LinkAddress TEST_LINKADDR = new LinkAddress(
            InetAddresses.parseNumericAddress("2001:db8::8"), 64);

    private static final int AIRPLANE_MODE_OFF = 0;
    private static final int AIRPLANE_MODE_ON = 1;

    private static final String TEST_HTTPS_URL_PATH = "/https_path";
    private static final String TEST_HTTP_URL_PATH = "/http_path";
    private static final String LOCALHOST_HOSTNAME = "localhost";
    // Re-connecting to the AP, obtaining an IP address, revalidating can take a long time
    private static final long WIFI_CONNECT_TIMEOUT_MS = 60_000L;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private ConnectivityManager mCm;
    private ConnectivityManagerShim mCmShim;
    private WifiManager mWifiManager;
    private PackageManager mPackageManager;
    private TelephonyManager mTm;
    private final ArraySet<Integer> mNetworkTypes = new ArraySet<>();
    private UiAutomation mUiAutomation;
    private CtsNetUtils mCtsNetUtils;
    // The registered callbacks.
    private List<NetworkCallback> mRegisteredCallbacks = new ArrayList<>();
    // Used for cleanup purposes.
    private final List<Range<Integer>> mVpnRequiredUidRanges = new ArrayList<>();

    private final TestHttpServer mHttpServer = new TestHttpServer(LOCALHOST_HOSTNAME);

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mCmShim = ConnectivityManagerShimImpl.newInstance(mContext);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mPackageManager = mContext.getPackageManager();
        mCtsNetUtils = new CtsNetUtils(mContext);
        mTm = mContext.getSystemService(TelephonyManager.class);

        if (isAtLeastS()) {
            addSupportedNetworkTypes();
        } else {
            addLegacySupportedNetworkTypes();
        }

        mUiAutomation = mInstrumentation.getUiAutomation();

        assertNotNull("CTS requires a working Internet connection", mCm.getActiveNetwork());
    }

    private void addLegacySupportedNetworkTypes() {
        // Network type support as expected for android R-
        // Get com.android.internal.R.array.networkAttributes
        int resId = mContext.getResources().getIdentifier("networkAttributes", "array", "android");
        String[] naStrings = mContext.getResources().getStringArray(resId);
        boolean wifiOnly = mPackageManager.hasSystemFeature(FEATURE_WIFI)
                && !mPackageManager.hasSystemFeature(FEATURE_TELEPHONY);
        for (String naString : naStrings) {
            try {
                final String[] splitConfig = naString.split(",");
                // Format was name,type,radio,priority,restoreTime,dependencyMet
                final int type = Integer.parseInt(splitConfig[1]);
                if (wifiOnly && ConnectivityManager.isNetworkTypeMobile(type)) {
                    continue;
                }
                mNetworkTypes.add(type);
            } catch (Exception e) {}
        }
    }

    private void addSupportedNetworkTypes() {
        final PackageManager pm = mContext.getPackageManager();
        if (pm.hasSystemFeature(FEATURE_WIFI)) {
            mNetworkTypes.add(TYPE_WIFI);
        }
        if (pm.hasSystemFeature(FEATURE_WIFI_DIRECT)) {
            mNetworkTypes.add(TYPE_WIFI_P2P);
        }
        if (mContext.getSystemService(TelephonyManager.class).isDataCapable()) {
            mNetworkTypes.add(TYPE_MOBILE);
            mNetworkTypes.add(TYPE_MOBILE_MMS);
            mNetworkTypes.add(TYPE_MOBILE_SUPL);
            mNetworkTypes.add(TYPE_MOBILE_DUN);
            mNetworkTypes.add(TYPE_MOBILE_HIPRI);
            mNetworkTypes.add(TYPE_MOBILE_FOTA);
            mNetworkTypes.add(TYPE_MOBILE_IMS);
            mNetworkTypes.add(TYPE_MOBILE_CBS);
            mNetworkTypes.add(TYPE_MOBILE_IA);
            mNetworkTypes.add(TYPE_MOBILE_EMERGENCY);
        }
        if (pm.hasSystemFeature(FEATURE_BLUETOOTH)) {
            mNetworkTypes.add(TYPE_BLUETOOTH);
        }
        if (pm.hasSystemFeature(FEATURE_WATCH)) {
            mNetworkTypes.add(TYPE_PROXY);
        }
        if (mContext.getSystemService(Context.ETHERNET_SERVICE) != null) {
            mNetworkTypes.add(TYPE_ETHERNET);
        }
        mNetworkTypes.add(TYPE_VPN);
    }

    @After
    public void tearDown() throws Exception {
        // Release any NetworkRequests filed to connect mobile data.
        if (mCtsNetUtils.cellConnectAttempted()) {
            mCtsNetUtils.disconnectFromCell();
        }

        if (TestUtils.shouldTestSApis()) {
            runWithShellPermissionIdentity(
                    () -> mCmShim.setRequireVpnForUids(false, mVpnRequiredUidRanges),
                    NETWORK_SETTINGS);
        }

        // All tests in this class require a working Internet connection as they start. Make
        // sure there is still one as they end that's ready to use for the next test to use.
        mTestValidationConfigRule.runAfterNextCleanup(() -> {
            final TestNetworkCallback callback = new TestNetworkCallback();
            registerDefaultNetworkCallback(callback);
            try {
                assertNotNull("Couldn't restore Internet connectivity",
                        callback.waitForAvailable());
            } finally {
                // Unregister all registered callbacks.
                unregisterRegisteredCallbacks();
            }
        });
    }

    @Test
    public void testIsNetworkTypeValid() {
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_WIFI));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_MMS));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_SUPL));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_DUN));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_HIPRI));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_WIMAX));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_BLUETOOTH));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_DUMMY));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_ETHERNET));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_FOTA));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_IMS));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_CBS));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_WIFI_P2P));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_IA));
        assertFalse(mCm.isNetworkTypeValid(-1));
        assertTrue(mCm.isNetworkTypeValid(0));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.MAX_NETWORK_TYPE));
        assertFalse(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.MAX_NETWORK_TYPE+1));

        NetworkInfo[] ni = mCm.getAllNetworkInfo();

        for (NetworkInfo n: ni) {
            assertTrue(ConnectivityManager.isNetworkTypeValid(n.getType()));
        }

    }

    @Test
    public void testSetNetworkPreference() {
        // getNetworkPreference() and setNetworkPreference() are both deprecated so they do
        // not preform any action.  Verify they are at least still callable.
        mCm.setNetworkPreference(mCm.getNetworkPreference());
    }

    @Test
    public void testGetActiveNetworkInfo() {
        NetworkInfo ni = mCm.getActiveNetworkInfo();

        assertNotNull("You must have an active network connection to complete CTS", ni);
        assertTrue(ConnectivityManager.isNetworkTypeValid(ni.getType()));
        assertTrue(ni.getState() == State.CONNECTED);
    }

    @Test
    public void testGetActiveNetwork() {
        Network network = mCm.getActiveNetwork();
        assertNotNull("You must have an active network connection to complete CTS", network);

        NetworkInfo ni = mCm.getNetworkInfo(network);
        assertNotNull("Network returned from getActiveNetwork was invalid", ni);

        // Similar to testGetActiveNetworkInfo above.
        assertTrue(ConnectivityManager.isNetworkTypeValid(ni.getType()));
        assertTrue(ni.getState() == State.CONNECTED);
    }

    @Test
    public void testGetNetworkInfo() {
        for (int type = -1; type <= ConnectivityManager.MAX_NETWORK_TYPE+1; type++) {
            if (shouldBeSupported(type)) {
                NetworkInfo ni = mCm.getNetworkInfo(type);
                assertTrue("Info shouldn't be null for " + type, ni != null);
                State state = ni.getState();
                assertTrue("Bad state for " + type, State.UNKNOWN.ordinal() >= state.ordinal()
                           && state.ordinal() >= State.CONNECTING.ordinal());
                DetailedState ds = ni.getDetailedState();
                assertTrue("Bad detailed state for " + type,
                           DetailedState.FAILED.ordinal() >= ds.ordinal()
                           && ds.ordinal() >= DetailedState.IDLE.ordinal());
            } else {
                assertNull("Info should be null for " + type, mCm.getNetworkInfo(type));
            }
        }
    }

    @Test
    public void testGetAllNetworkInfo() {
        NetworkInfo[] ni = mCm.getAllNetworkInfo();
        assertTrue(ni.length >= MIN_NUM_NETWORK_TYPES);
        for (int type = 0; type <= ConnectivityManager.MAX_NETWORK_TYPE; type++) {
            int desiredFoundCount = (shouldBeSupported(type) ? 1 : 0);
            int foundCount = 0;
            for (NetworkInfo i : ni) {
                if (i.getType() == type) foundCount++;
            }
            if (foundCount != desiredFoundCount) {
                Log.e(TAG, "failure in testGetAllNetworkInfo.  Dump of returned NetworkInfos:");
                for (NetworkInfo networkInfo : ni) Log.e(TAG, "  " + networkInfo);
            }
            assertTrue("Unexpected foundCount of " + foundCount + " for type " + type,
                    foundCount == desiredFoundCount);
        }
    }

    private String getSubscriberIdForCellNetwork(Network cellNetwork) {
        final NetworkCapabilities cellCaps = mCm.getNetworkCapabilities(cellNetwork);
        final NetworkSpecifier specifier = cellCaps.getNetworkSpecifier();
        assertTrue(specifier instanceof TelephonyNetworkSpecifier);
        // Get subscription from Telephony network specifier.
        final int subId = ((TelephonyNetworkSpecifier) specifier).getSubscriptionId();
        assertNotEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, subId);

        // Get subscriber Id from telephony manager.
        final TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        return runWithShellPermissionIdentity(() -> tm.getSubscriberId(subId),
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }

    @AppModeFull(reason = "Cannot request network in instant app mode")
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testGetAllNetworkStateSnapshots()
            throws InterruptedException {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_TELEPHONY));
        // Make sure cell is active to retrieve IMSI for verification in later step.
        final Network cellNetwork = mCtsNetUtils.connectToCell();
        final String subscriberId = getSubscriberIdForCellNetwork(cellNetwork);
        assertFalse(TextUtils.isEmpty(subscriberId));

        // Verify the API cannot be called without proper permission.
        assertThrows(SecurityException.class, () -> mCm.getAllNetworkStateSnapshots());

        // Get all networks, verify the result of getAllNetworkStateSnapshots matches the result
        // got from other APIs.
        final Network[] networks = mCm.getAllNetworks();
        assertGreaterOrEqual(networks.length, 1);
        final TestableNetworkCallback allNetworkLinkPropertiesListener =
                new TestableNetworkCallback();
        mCm.registerNetworkCallback(new NetworkRequest.Builder().clearCapabilities().build(),
                allNetworkLinkPropertiesListener);

        final List<NetworkStateSnapshot> snapshots = runWithShellPermissionIdentity(
                () -> mCm.getAllNetworkStateSnapshots(), NETWORK_SETTINGS);
        assertEquals(networks.length, snapshots.size());
        for (final Network network : networks) {
            // Can't use a lambda because it will cause the test to crash on R with
            // NoClassDefFoundError.
            NetworkStateSnapshot snapshot = null;
            for (NetworkStateSnapshot item : snapshots) {
                if (item.getNetwork().equals(network)) {
                    snapshot = item;
                    break;
                }
            }
            assertNotNull(snapshot);
            final NetworkCapabilities caps =
                    Objects.requireNonNull(mCm.getNetworkCapabilities(network));
            // Redact specifier of the capabilities of the snapshot before comparing since
            // the result returned from getNetworkCapabilities always get redacted.
            final NetworkSpecifier snapshotCapSpecifier =
                    snapshot.getNetworkCapabilities().getNetworkSpecifier();
            final NetworkSpecifier redactedSnapshotCapSpecifier =
                    snapshotCapSpecifier == null ? null : snapshotCapSpecifier.redact();
            assertEquals("", caps.describeImmutableDifferences(
                    snapshot.getNetworkCapabilities()
                            .setNetworkSpecifier(redactedSnapshotCapSpecifier)));

            // Don't check that the mutable fields are the same with synchronous calls, as
            // the device may add or remove content of these fields in the middle of the test.
            // Instead, search the target LinkProperties from received LinkPropertiesChanged
            // callbacks. This is guaranteed to succeed because the callback is registered
            // before getAllNetworkStateSnapshots is called.
            final LinkProperties lpFromSnapshot = snapshot.getLinkProperties();
            allNetworkLinkPropertiesListener.eventuallyExpect(CallbackEntry.LINK_PROPERTIES_CHANGED,
                    NETWORK_CALLBACK_TIMEOUT_MS, 0 /* mark */, entry ->
                            entry.getNetwork().equals(network)
                                    && entry.getLp().equals(lpFromSnapshot));

            assertEquals(mCm.getNetworkInfo(network).getType(), snapshot.getLegacyType());

            if (network.equals(cellNetwork)) {
                assertEquals(subscriberId, snapshot.getSubscriberId());
            }
        }
    }

    private boolean checkPermission(String perm, int uid) {
        return mContext.checkPermission(perm, -1 /* pid */, uid) == PERMISSION_GRANTED;
    }

    private String findPackageByPermissions(@NonNull List<String> requiredPermissions,
                @NonNull List<String> forbiddenPermissions) throws Exception {
        final List<PackageInfo> packageInfos =
                mPackageManager.getInstalledPackages(GET_PERMISSIONS);
        for (PackageInfo packageInfo : packageInfos) {
            final int uid = mPackageManager.getPackageUid(packageInfo.packageName, 0 /* flags */);
            if (!CollectionUtils.all(requiredPermissions, perm -> checkPermission(perm, uid))) {
                continue;
            }
            if (CollectionUtils.any(forbiddenPermissions, perm -> checkPermission(perm, uid))) {
                continue;
            }

            return packageInfo.packageName;
        }
        return null;
    }

    @DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
    @AppModeFull(reason = "Cannot get installed packages in instant app mode")
    @Test
    public void testGetRedactedLinkPropertiesForPackage() throws Exception {
        final String groundedPkg = findPackageByPermissions(
                List.of(), /* requiredPermissions */
                List.of(ACCESS_NETWORK_STATE) /* forbiddenPermissions */);
        assertNotNull("Couldn't find any package without ACCESS_NETWORK_STATE", groundedPkg);
        final int groundedUid = mPackageManager.getPackageUid(groundedPkg, 0 /* flags */);

        final String normalPkg = findPackageByPermissions(
                List.of(ACCESS_NETWORK_STATE) /* requiredPermissions */,
                List.of(NETWORK_SETTINGS, NETWORK_STACK,
                        PERMISSION_MAINLINE_NETWORK_STACK) /* forbiddenPermissions */);
        assertNotNull("Couldn't find any package with ACCESS_NETWORK_STATE but"
                + " without NETWORK_SETTINGS", normalPkg);
        final int normalUid = mPackageManager.getPackageUid(normalPkg, 0 /* flags */);

        // There are some privileged packages on the system, like the phone process, the network
        // stack and the system server.
        final String privilegedPkg = findPackageByPermissions(
                List.of(ACCESS_NETWORK_STATE, NETWORK_SETTINGS), /* requiredPermissions */
                List.of() /* forbiddenPermissions */);
        assertNotNull("Couldn't find a package with sufficient permissions", privilegedPkg);
        final int privilegedUid = mPackageManager.getPackageUid(privilegedPkg, 0);

        // Set parcelSensitiveFields to true to preserve CaptivePortalApiUrl & CaptivePortalData
        // when parceling.
        final LinkProperties lp = new LinkProperties(new LinkProperties(),
                true /* parcelSensitiveFields */);
        final Uri capportUrl = Uri.parse("https://capport.example.com/api");
        final CaptivePortalData capportData = new CaptivePortalData.Builder().build();
        final int mtu = 12345;
        lp.setMtu(mtu);
        lp.setCaptivePortalApiUrl(capportUrl);
        lp.setCaptivePortalData(capportData);

        // No matter what the given uid is, a SecurityException will be thrown if the caller
        // doesn't hold the NETWORK_SETTINGS permission.
        assertThrows(SecurityException.class,
                () -> mCm.getRedactedLinkPropertiesForPackage(lp, groundedUid, groundedPkg));
        assertThrows(SecurityException.class,
                () -> mCm.getRedactedLinkPropertiesForPackage(lp, normalUid, normalPkg));
        assertThrows(SecurityException.class,
                () -> mCm.getRedactedLinkPropertiesForPackage(lp, privilegedUid, privilegedPkg));

        runAsShell(NETWORK_SETTINGS, () -> {
            // No matter what the given uid is, if the given LinkProperties is null, then
            // NullPointerException will be thrown.
            assertThrows(NullPointerException.class,
                    () -> mCm.getRedactedLinkPropertiesForPackage(null, groundedUid, groundedPkg));
            assertThrows(NullPointerException.class,
                    () -> mCm.getRedactedLinkPropertiesForPackage(null, normalUid, normalPkg));
            assertThrows(NullPointerException.class,
                    () -> mCm.getRedactedLinkPropertiesForPackage(
                            null, privilegedUid, privilegedPkg));

            // Make sure null is returned for a UID without ACCESS_NETWORK_STATE.
            assertNull(mCm.getRedactedLinkPropertiesForPackage(lp, groundedUid, groundedPkg));

            // CaptivePortalApiUrl & CaptivePortalData will be set to null if given uid doesn't hold
            // the NETWORK_SETTINGS permission.
            assertNull(mCm.getRedactedLinkPropertiesForPackage(lp, normalUid, normalPkg)
                    .getCaptivePortalApiUrl());
            assertNull(mCm.getRedactedLinkPropertiesForPackage(lp, normalUid, normalPkg)
                    .getCaptivePortalData());
            // MTU is not sensitive and is not redacted.
            assertEquals(mtu, mCm.getRedactedLinkPropertiesForPackage(lp, normalUid, normalPkg)
                    .getMtu());

            // CaptivePortalApiUrl & CaptivePortalData will be preserved if the given uid holds the
            // NETWORK_SETTINGS permission.
            assertNotNull(lp.getCaptivePortalApiUrl());
            assertNotNull(lp.getCaptivePortalData());
            assertEquals(lp.getCaptivePortalApiUrl(),
                    mCm.getRedactedLinkPropertiesForPackage(lp, privilegedUid, privilegedPkg)
                            .getCaptivePortalApiUrl());
            assertEquals(lp.getCaptivePortalData(),
                    mCm.getRedactedLinkPropertiesForPackage(lp, privilegedUid, privilegedPkg)
                            .getCaptivePortalData());
        });
    }

    private NetworkCapabilities redactNc(@NonNull final NetworkCapabilities nc, int uid,
            @NonNull String packageName) {
        return mCm.getRedactedNetworkCapabilitiesForPackage(nc, uid, packageName);
    }

    @ConnectivityModuleTest
    @DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
    @AppModeFull(reason = "Cannot get installed packages in instant app mode")
    @Test
    public void testGetRedactedNetworkCapabilitiesForPackage() throws Exception {
        final String groundedPkg = findPackageByPermissions(
                List.of(), /* requiredPermissions */
                List.of(ACCESS_NETWORK_STATE) /* forbiddenPermissions */);
        assertNotNull("Couldn't find any package without ACCESS_NETWORK_STATE", groundedPkg);
        final int groundedUid = mPackageManager.getPackageUid(groundedPkg, 0 /* flags */);

        // A package which doesn't have any of the permissions below, but has NETWORK_STATE.
        // There should be a number of packages like this on the device; AOSP has many,
        // including contacts, webview, the keyboard, pacprocessor, messaging.
        final String normalPkg = findPackageByPermissions(
                List.of(ACCESS_NETWORK_STATE) /* requiredPermissions */,
                List.of(NETWORK_SETTINGS, NETWORK_FACTORY, NETWORK_SETUP_WIZARD,
                        NETWORK_STACK, PERMISSION_MAINLINE_NETWORK_STACK,
                        ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION) /* forbiddenPermissions */);
        assertNotNull("Can't find a package with ACCESS_NETWORK_STATE but without any of"
                + " the forbidden permissions", normalPkg);
        final int normalUid = mPackageManager.getPackageUid(normalPkg, 0 /* flags */);

        // There are some privileged packages on the system, like the phone process, the network
        // stack and the system server.
        final String privilegedPkg = findPackageByPermissions(
                List.of(ACCESS_NETWORK_STATE, NETWORK_SETTINGS, NETWORK_FACTORY,
                        ACCESS_FINE_LOCATION), /* requiredPermissions */
                List.of() /* forbiddenPermissions */);
        assertNotNull("Couldn't find a package with sufficient permissions", privilegedPkg);
        final int privilegedUid = mPackageManager.getPackageUid(privilegedPkg, 0);

        final Set<Range<Integer>> uids = new ArraySet<>();
        uids.add(new Range<>(10000, 10100));
        uids.add(new Range<>(10200, 10300));
        final String ssid = "My-WiFi";
        // This test will set underlying networks in the capabilities to redact to see if they
        // are appropriately redacted, so fetch the default network to put in there as an example.
        final Network defaultNetwork = mCm.getActiveNetwork();
        assertNotNull("CTS requires a working Internet connection", defaultNetwork);
        final int subId1 = 1;
        final int subId2 = 2;
        final int[] administratorUids = {normalUid};
        final String bssid = "location sensitive";
        final int rssi = 43; // not location sensitive
        final WifiInfo wifiInfo = new WifiInfo.Builder()
                .setBssid(bssid)
                .setRssi(rssi)
                .build();
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .setUids(uids)
                .setSsid(ssid)
                .setUnderlyingNetworks(List.of(defaultNetwork))
                .setSubscriptionIds(Set.of(subId1, subId2))
                .setAdministratorUids(administratorUids)
                .setOwnerUid(normalUid)
                .setTransportInfo(wifiInfo)
                .build();

        // No matter what the given uid is, a SecurityException will be thrown if the caller
        // doesn't hold the NETWORK_SETTINGS permission.
        assertThrows(SecurityException.class, () -> redactNc(nc, groundedUid, groundedPkg));
        assertThrows(SecurityException.class, () -> redactNc(nc, normalUid, normalPkg));
        assertThrows(SecurityException.class, () -> redactNc(nc, privilegedUid, privilegedPkg));

        runAsShell(NETWORK_SETTINGS, () -> {
            // Make sure that the NC is null if the package doesn't hold ACCESS_NETWORK_STATE.
            assertNull(redactNc(nc, groundedUid, groundedPkg));

            // Uids, ssid & underlying networks will be redacted if the given uid
            // doesn't hold the associated permissions. The wifi transport info is also suitably
            // redacted.
            final NetworkCapabilities redactedNormal = redactNc(nc, normalUid, normalPkg);
            assertNull(redactedNormal.getUids());
            assertNull(redactedNormal.getSsid());
            assertNull(redactedNormal.getUnderlyingNetworks());
            // TODO: Make subIds public and update to verify the size is 2
            final int subIdsSize = redactedNormal.getSubscriptionIds().size();
            assertTrue(subIdsSize == 0 || subIdsSize == 2);
            assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS,
                    ((WifiInfo) redactedNormal.getTransportInfo()).getBSSID());
            assertEquals(rssi, ((WifiInfo) redactedNormal.getTransportInfo()).getRssi());

            // Uids, ssid, underlying networks & subscriptionIds will be preserved if the given uid
            // holds the associated permissions.
            final NetworkCapabilities redactedPrivileged =
                    redactNc(nc, privilegedUid, privilegedPkg);
            assertEquals(uids, redactedPrivileged.getUids());
            assertEquals(ssid, redactedPrivileged.getSsid());
            assertEquals(List.of(defaultNetwork), redactedPrivileged.getUnderlyingNetworks());
            assertEquals(Set.of(subId1, subId2), redactedPrivileged.getSubscriptionIds());
            assertEquals(bssid, ((WifiInfo) redactedPrivileged.getTransportInfo()).getBSSID());
            assertEquals(rssi, ((WifiInfo) redactedPrivileged.getTransportInfo()).getRssi());

            // The owner uid is only preserved when the network is a VPN and the uid is the
            // same as the owner uid.
            nc.addTransportType(TRANSPORT_VPN);
            assertEquals(normalUid, redactNc(nc, normalUid, normalPkg).getOwnerUid());
            assertEquals(INVALID_UID, redactNc(nc, privilegedUid, privilegedPkg).getOwnerUid());
            nc.removeTransportType(TRANSPORT_VPN);

            // If the given uid doesn't hold location permissions, the owner uid will be set to
            // INVALID_UID even when sent to that UID (this avoids a wifi suggestor knowing where
            // the device is by virtue of the device connecting to its own network).
            assertEquals(INVALID_UID, redactNc(nc, normalUid, normalPkg).getOwnerUid());

            // If the given uid holds location permissions, the owner uid is preserved. This works
            // because the shell holds ACCESS_FINE_LOCATION.
            final int[] administratorUids2 = { privilegedUid };
            nc.setAdministratorUids(administratorUids2);
            nc.setOwnerUid(privilegedUid);
            assertEquals(privilegedUid, redactNc(nc, privilegedUid, privilegedPkg).getOwnerUid());
        });
    }

    /**
     * Tests that connections can be opened on WiFi and cellphone networks,
     * and that they are made from different IP addresses.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    @RequiresDevice // Virtual devices use a single internet connection for all networks
    public void testOpenConnection() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_TELEPHONY));

        Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
        Network cellNetwork = mCtsNetUtils.connectToCell();
        // This server returns the requestor's IP address as the response body.
        URL url = new URL("http://google-ipv6test.appspot.com/ip.js?fmt=text");
        String wifiAddressString = httpGet(wifiNetwork, url);
        String cellAddressString = httpGet(cellNetwork, url);

        assertFalse(String.format("Same address '%s' on two different networks (%s, %s)",
                wifiAddressString, wifiNetwork, cellNetwork),
                wifiAddressString.equals(cellAddressString));

        // Verify that the IP addresses that the requests appeared to come from are actually on the
        // respective networks.
        assertOnNetwork(wifiAddressString, wifiNetwork);
        assertOnNetwork(cellAddressString, cellNetwork);

        assertFalse("Unexpectedly equal: " + wifiNetwork, wifiNetwork.equals(cellNetwork));
    }

    /**
     * Performs a HTTP GET to the specified URL on the specified Network, and returns
     * the response body decoded as UTF-8.
     */
    private static String httpGet(Network network, URL httpUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) network.openConnection(httpUrl);
        try {
            InputStream inputStream = connection.getInputStream();
            return Streams.readFully(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private void assertOnNetwork(String adressString, Network network) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(adressString);
        LinkProperties linkProperties = mCm.getLinkProperties(network);
        // To make sure that the request went out on the right network, check that
        // the IP address seen by the server is assigned to the expected network.
        // We can only do this for IPv6 addresses, because in IPv4 we will likely
        // have a private IPv4 address, and that won't match what the server sees.
        if (address instanceof Inet6Address) {
            assertContains(linkProperties.getAddresses(), address);
        }
    }

    private static<T> void assertContains(Collection<T> collection, T element) {
        assertTrue(element + " not found in " + collection, collection.contains(element));
    }

    private void assertStartUsingNetworkFeatureUnsupported(int networkType, String feature) {
        try {
            mCm.startUsingNetworkFeature(networkType, feature);
            fail("startUsingNetworkFeature is no longer supported in the current API version");
        } catch (UnsupportedOperationException expected) {}
    }

    private void assertStopUsingNetworkFeatureUnsupported(int networkType, String feature) {
        try {
            mCm.startUsingNetworkFeature(networkType, feature);
            fail("stopUsingNetworkFeature is no longer supported in the current API version");
        } catch (UnsupportedOperationException expected) {}
    }

    private void assertRequestRouteToHostUnsupported(int networkType, int hostAddress) {
        try {
            mCm.requestRouteToHost(networkType, hostAddress);
            fail("requestRouteToHost is no longer supported in the current API version");
        } catch (UnsupportedOperationException expected) {}
    }

    @Test
    public void testStartUsingNetworkFeature() {

        final String invalidateFeature = "invalidateFeature";
        final String mmsFeature = "enableMMS";

        assertStartUsingNetworkFeatureUnsupported(TYPE_MOBILE, invalidateFeature);
        assertStopUsingNetworkFeatureUnsupported(TYPE_MOBILE, invalidateFeature);
        assertStartUsingNetworkFeatureUnsupported(TYPE_WIFI, mmsFeature);
    }

    private boolean shouldEthernetBeSupported() {
        // Instant mode apps aren't allowed to query the Ethernet service due to selinux policies.
        // When in instant mode, don't fail if the Ethernet service is available. Instead, rely on
        // the fact that Ethernet should be supported if the device has a hardware Ethernet port, or
        // if the device can be a USB host and thus can use USB Ethernet adapters.
        //
        // Note that this test this will still fail in instant mode if a device supports Ethernet
        // via other hardware means. We are not currently aware of any such device.
        return hasEthernetService()
                || mPackageManager.hasSystemFeature(FEATURE_ETHERNET)
                || mPackageManager.hasSystemFeature(FEATURE_USB_HOST);
    }

    private boolean hasEthernetService() {
        // On Q creating EthernetManager from a thread that does not have a looper (like the test
        // thread) crashes because it tried to use Looper.myLooper() through the default Handler
        // constructor to run onAvailabilityChanged callbacks. Use ServiceManager to check whether
        // the service exists instead.
        // TODO: remove once Q is no longer supported in MTS, as ServiceManager is hidden API
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            return ServiceManager.getService(Context.ETHERNET_SERVICE) != null;
        }
        return mContext.getSystemService(Context.ETHERNET_SERVICE) != null;
    }

    private boolean shouldBeSupported(int networkType) {
        return mNetworkTypes.contains(networkType)
            || (networkType == ConnectivityManager.TYPE_VPN)
            || (networkType == ConnectivityManager.TYPE_ETHERNET && shouldEthernetBeSupported());
    }

    @Test
    public void testIsNetworkSupported() {
        for (int type = -1; type <= ConnectivityManager.MAX_NETWORK_TYPE; type++) {
            boolean supported = mCm.isNetworkSupported(type);
            if (shouldBeSupported(type)) {
                assertTrue("Network type " + type + " should be supported", supported);
            } else {
                assertFalse("Network type " + type + " should not be supported", supported);
            }
        }
    }

    @Test
    public void testRequestRouteToHost() {
        for (int type = -1 ; type <= ConnectivityManager.MAX_NETWORK_TYPE; type++) {
            assertRequestRouteToHostUnsupported(type, HOST_ADDRESS);
        }
    }

    @Test
    public void testTest() {
        mCm.getBackgroundDataSetting();
    }

    private NetworkRequest makeDefaultRequest() {
        // Make a request that is similar to the way framework tracks the system
        // default network.
        return new NetworkRequest.Builder()
                .clearCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
    }

    private NetworkRequest makeWifiNetworkRequest() {
        return new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
    }

    private NetworkRequest makeCellNetworkRequest() {
        return new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
    }

    private boolean hasPrivateDnsValidated(CallbackEntry entry, Network networkForPrivateDns) {
        if (!networkForPrivateDns.equals(entry.getNetwork())) return false;
        final NetworkCapabilities nc = ((CallbackEntry.CapabilitiesChanged) entry).getCaps();
        return !nc.isPrivateDnsBroken() && nc.hasCapability(NET_CAPABILITY_VALIDATED);
    }

    @AppModeFull(reason = "WRITE_SECURE_SETTINGS permission can't be granted to instant apps")
    @Test @IgnoreUpTo(Build.VERSION_CODES.Q)
    @SkipMainlinePresubmit(reason = "Out of SLO flakiness")
    public void testIsPrivateDnsBroken() throws InterruptedException {
        final String invalidPrivateDnsServer = "invalidhostname.example.com";
        final String goodPrivateDnsServer = "dns.google";
        mCtsNetUtils.storePrivateDnsSetting();
        final TestableNetworkCallback cb = new TestableNetworkCallback();
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET).build();
        registerNetworkCallback(networkRequest, cb);
        final Network networkForPrivateDns = mCm.getActiveNetwork();
        try {
            // Verifying the good private DNS sever
            mCtsNetUtils.setPrivateDnsStrictMode(goodPrivateDnsServer);
            cb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> hasPrivateDnsValidated(entry, networkForPrivateDns));

            // Verifying the broken private DNS sever
            mCtsNetUtils.setPrivateDnsStrictMode(invalidPrivateDnsServer);
            cb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> (((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                    .isPrivateDnsBroken()) && networkForPrivateDns.equals(entry.getNetwork()));
        } finally {
            mCtsNetUtils.restorePrivateDnsSetting();
            // Toggle network to make sure it is re-validated
            mCm.reportNetworkConnectivity(networkForPrivateDns, true);
            cb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> !(((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                    .isPrivateDnsBroken()) && networkForPrivateDns.equals(entry.getNetwork()));
        }
    }

    /**
     * Exercises both registerNetworkCallback and unregisterNetworkCallback. This checks to
     * see if we get a callback for the TRANSPORT_WIFI transport type being available.
     *
     * <p>In order to test that a NetworkCallback occurs, we need some change in the network
     * state (either a transport or capability is now available). The most straightforward is
     * WiFi. We could add a version that uses the telephony data connection but it's not clear
     * that it would increase test coverage by much (how many devices have 3G radio but not Wifi?).
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testRegisterNetworkCallback() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        // We will register for a WIFI network being available or lost.
        final TestNetworkCallback callback = new TestNetworkCallback();
        registerNetworkCallback(makeWifiNetworkRequest(), callback);

        final TestNetworkCallback defaultTrackingCallback = new TestNetworkCallback();
        registerDefaultNetworkCallback(defaultTrackingCallback);

        final TestNetworkCallback systemDefaultCallback = new TestNetworkCallback();
        final TestNetworkCallback perUidCallback = new TestNetworkCallback();
        final TestNetworkCallback bestMatchingCallback = new TestNetworkCallback();
        final Handler h = new Handler(Looper.getMainLooper());
        if (TestUtils.shouldTestSApis()) {
            assertThrows(SecurityException.class, () ->
                    registerSystemDefaultNetworkCallback(systemDefaultCallback, h));
            runWithShellPermissionIdentity(() -> {
                registerSystemDefaultNetworkCallback(systemDefaultCallback, h);
                registerDefaultNetworkCallbackForUid(Process.myUid(), perUidCallback, h);
            }, NETWORK_SETTINGS);
            registerBestMatchingNetworkCallback(makeDefaultRequest(), bestMatchingCallback, h);
        }

        Network wifiNetwork = null;
        mCtsNetUtils.ensureWifiConnected();

        // Now we should expect to get a network callback about availability of the wifi
        // network even if it was already connected as a state-based action when the callback
        // is registered.
        wifiNetwork = callback.waitForAvailable();
        assertNotNull("Did not receive onAvailable for TRANSPORT_WIFI request",
                wifiNetwork);

        final Network defaultNetwork = defaultTrackingCallback.waitForAvailable();
        assertNotNull("Did not receive onAvailable on default network callback",
                defaultNetwork);

        if (TestUtils.shouldTestSApis()) {
            assertNotNull("Did not receive onAvailable on system default network callback",
                    systemDefaultCallback.waitForAvailable());
            final Network perUidNetwork = perUidCallback.waitForAvailable();
            assertNotNull("Did not receive onAvailable on per-UID default network callback",
                    perUidNetwork);
            assertEquals(defaultNetwork, perUidNetwork);
            final Network bestMatchingNetwork = bestMatchingCallback.waitForAvailable();
            assertNotNull("Did not receive onAvailable on best matching network callback",
                    bestMatchingNetwork);
            assertEquals(defaultNetwork, bestMatchingNetwork);
        }
    }

    @ConnectivityModuleTest
    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testRegisterSystemDefaultNetworkCallbackPermission() {
        final Handler h = new Handler(Looper.getMainLooper());
        // Verify registerSystemDefaultNetworkCallback can be accessed via
        // CONNECTIVITY_USE_RESTRICTED_NETWORKS permission.
        runWithShellPermissionIdentity(() ->
                        registerSystemDefaultNetworkCallback(new TestNetworkCallback(), h),
                CONNECTIVITY_USE_RESTRICTED_NETWORKS);
    }

    /**
     * Tests both registerNetworkCallback and unregisterNetworkCallback similarly to
     * {@link #testRegisterNetworkCallback} except that a {@code PendingIntent} is used instead
     * of a {@code NetworkCallback}.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testRegisterNetworkCallback_withPendingIntent() {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        // Create a ConnectivityActionReceiver that has an IntentFilter for our locally defined
        // action, NETWORK_CALLBACK_ACTION.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(NETWORK_CALLBACK_ACTION);

        final ConnectivityActionReceiver receiver = new ConnectivityActionReceiver(
                mCm, ConnectivityManager.TYPE_WIFI, NetworkInfo.State.CONNECTED);
        final int flags = SdkLevel.isAtLeastT() ? RECEIVER_EXPORTED : 0;
        mContext.registerReceiver(receiver, filter, flags);

        // Create a broadcast PendingIntent for NETWORK_CALLBACK_ACTION.
        final Intent intent = new Intent(NETWORK_CALLBACK_ACTION)
                .setPackage(mContext.getPackageName());
        // While ConnectivityService would put extra info such as network or request id before
        // broadcasting the inner intent. The MUTABLE flag needs to be added accordingly.
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0 /*requestCode*/,
                intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);

        // We will register for a WIFI network being available or lost.
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), pendingIntent);

        try {
            mCtsNetUtils.ensureWifiConnected();

            // Now we expect to get the Intent delivered notifying of the availability of the wifi
            // network even if it was already connected as a state-based action when the callback
            // is registered.
            assertTrue("Did not receive expected Intent " + intent + " for TRANSPORT_WIFI",
                    receiver.waitForState());
        } catch (InterruptedException e) {
            fail("Broadcast receiver or NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(pendingIntent);
            pendingIntent.cancel();
            mContext.unregisterReceiver(receiver);
        }
    }

    private void runIdenticalPendingIntentsRequestTest(boolean useListen) throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        // Disconnect before registering callbacks, reconnect later to fire them
        mCtsNetUtils.ensureWifiDisconnected(null);

        final NetworkRequest firstRequest = makeWifiNetworkRequest();
        final NetworkRequest secondRequest = new NetworkRequest(firstRequest);
        // Will match wifi or test, since transports are ORed; but there should only be wifi
        secondRequest.networkCapabilities.addTransportType(TRANSPORT_TEST);

        PendingIntent firstIntent = null;
        PendingIntent secondIntent = null;
        BroadcastReceiver receiver = null;

        // Avoid receiving broadcasts from other runs by appending a timestamp
        final String broadcastAction = NETWORK_CALLBACK_ACTION + System.currentTimeMillis();
        try {
            // Intent is mutable to receive EXTRA_NETWORK_REQUEST from ConnectivityService
            final String extraBoolKey = "extra_bool";
            firstIntent = PendingIntent.getBroadcast(mContext,
                    0 /* requestCode */,
                    new Intent(broadcastAction).putExtra(extraBoolKey, false)
                            .setPackage(mContext.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            if (useListen) {
                mCm.registerNetworkCallback(firstRequest, firstIntent);
            } else {
                mCm.requestNetwork(firstRequest, firstIntent);
            }

            // Second intent equals the first as per filterEquals (extras don't count), so first
            // intent will be updated with the new extras
            secondIntent = PendingIntent.getBroadcast(mContext,
                    0 /* requestCode */,
                    new Intent(broadcastAction).putExtra(extraBoolKey, true)
                            .setPackage(mContext.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            // Because secondIntent.intentFilterEquals the first, the request should be replaced
            if (useListen) {
                mCm.registerNetworkCallback(secondRequest, secondIntent);
            } else {
                mCm.requestNetwork(secondRequest, secondIntent);
            }

            final IntentFilter filter = new IntentFilter();
            filter.addAction(broadcastAction);

            final CompletableFuture<Network> networkFuture = new CompletableFuture<>();
            final AtomicInteger receivedCount = new AtomicInteger(0);
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final NetworkRequest request = intent.getParcelableExtra(EXTRA_NETWORK_REQUEST);
                    assertPendingIntentRequestMatches(request, secondRequest, useListen);
                    receivedCount.incrementAndGet();
                    networkFuture.complete(intent.getParcelableExtra(EXTRA_NETWORK));
                }
            };
            final int flags = SdkLevel.isAtLeastT() ? RECEIVER_EXPORTED : 0;
            mContext.registerReceiver(receiver, filter, flags);

            final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
            try {
                assertEquals(wifiNetwork, networkFuture.get(
                        NETWORK_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                throw new AssertionError("PendingIntent not received for " + secondRequest, e);
            }

            // Sleep for a small amount of time to try to check that only one callback is ever
            // received (so the first callback was really unregistered). This does not guarantee
            // that the test will fail if it runs very slowly, but it should at least be very
            // noticeably flaky.
            Thread.sleep(NO_CALLBACK_TIMEOUT_MS);

            // For R- frameworks, listens will receive duplicated callbacks. See b/189868426.
            if (isAtLeastS() || !useListen) {
                assertEquals("PendingIntent should only be received once", 1, receivedCount.get());
            }
        } finally {
            if (firstIntent != null) mCm.unregisterNetworkCallback(firstIntent);
            if (secondIntent != null) mCm.unregisterNetworkCallback(secondIntent);
            if (receiver != null) mContext.unregisterReceiver(receiver);
            mCtsNetUtils.ensureWifiConnected();
        }
    }

    private void assertPendingIntentRequestMatches(NetworkRequest broadcasted, NetworkRequest filed,
            boolean useListen) {
        assertArrayEquals(filed.networkCapabilities.getCapabilities(),
                broadcasted.networkCapabilities.getCapabilities());
        // For R- frameworks, listens will receive duplicated callbacks. See b/189868426.
        if (!isAtLeastS() && useListen) return;
        assertArrayEquals(filed.networkCapabilities.getTransportTypes(),
                broadcasted.networkCapabilities.getTransportTypes());
    }

    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testRegisterNetworkRequest_identicalPendingIntents() throws Exception {
        runIdenticalPendingIntentsRequestTest(false /* useListen */);
    }

    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testRegisterNetworkCallback_identicalPendingIntents() throws Exception {
        runIdenticalPendingIntentsRequestTest(true /* useListen */);
    }

    /**
     * Exercises the requestNetwork with NetworkCallback API. This checks to
     * see if we get a callback for an INTERNET request.
     */
    @AppModeFull(reason = "CHANGE_NETWORK_STATE permission can't be granted to instant apps")
    @Test
    public void testRequestNetworkCallback() throws Exception {
        final TestNetworkCallback callback = new TestNetworkCallback();
        requestNetwork(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), callback);

        // Wait to get callback for availability of internet
        Network internetNetwork = callback.waitForAvailable();
        assertNotNull("Did not receive NetworkCallback#onAvailable for INTERNET", internetNetwork);
    }

    /**
     * Exercises the requestNetwork with NetworkCallback API with timeout - expected to
     * fail. Use WIFI and switch Wi-Fi off.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testRequestNetworkCallback_onUnavailable() {
        boolean previousWifiEnabledState = false;
        if (mPackageManager.hasSystemFeature(FEATURE_WIFI)) {
            previousWifiEnabledState = mWifiManager.isWifiEnabled();
            if (previousWifiEnabledState) {
                mCtsNetUtils.ensureWifiDisconnected(null);
            }
        }

        final TestNetworkCallback callback = new TestNetworkCallback();
        requestNetwork(new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build(),
                callback, 100);

        try {
            // Wait to get callback for unavailability of requested network
            assertTrue("Did not receive NetworkCallback#onUnavailable",
                    callback.waitForUnavailable());
        } catch (InterruptedException e) {
            fail("NetworkCallback wait was interrupted.");
        } finally {
            if (previousWifiEnabledState) {
                mCtsNetUtils.connectToWifi();
            }
        }
    }

    private InetAddress getFirstV4Address(Network network) {
        LinkProperties linkProperties = mCm.getLinkProperties(network);
        for (InetAddress address : linkProperties.getAddresses()) {
            if (address instanceof Inet4Address) {
                return address;
            }
        }
        return null;
    }

    /**
     * Checks that enabling/disabling wifi causes CONNECTIVITY_ACTION broadcasts.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testToggleWifiConnectivityAction() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        mCtsNetUtils.reconnectWifiAndWaitForConnectivityAction();
    }

    /** Verify restricted networks cannot be requested. */
    @AppModeFull(reason = "CHANGE_NETWORK_STATE permission can't be granted to instant apps")
    @Test
    public void testRestrictedNetworks() {
        // Verify we can request unrestricted networks:
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET).build();
        NetworkCallback callback = new NetworkCallback();
        mCm.requestNetwork(request, callback);
        mCm.unregisterNetworkCallback(callback);
        // Verify we cannot request restricted networks:
        request = new NetworkRequest.Builder().addCapability(NET_CAPABILITY_IMS).build();
        callback = new NetworkCallback();
        try {
            mCm.requestNetwork(request, callback);
            fail("No exception thrown when restricted network requested.");
        } catch (SecurityException expected) {}
    }

    // Returns "true", "false" or "none"
    private String getWifiMeteredStatus(String ssid) throws Exception {
        // Interestingly giving the SSID as an argument to list wifi-networks
        // only works iff the network in question has the "false" policy.
        // Also unfortunately runShellCommand does not pass the command to the interpreter
        // so it's not possible to | grep the ssid.
        final String command = "cmd netpolicy list wifi-networks";
        final String policyString = runShellCommand(mInstrumentation, command);

        final Matcher m = Pattern.compile("^" + ssid + ";(true|false|none)$",
                Pattern.MULTILINE | Pattern.UNIX_LINES).matcher(policyString);
        if (!m.find()) {
            fail("Unexpected format from cmd netpolicy, policyString = " + policyString);
        }
        return m.group(1);
    }

    // metered should be "true", "false" or "none"
    private void setWifiMeteredStatus(String ssid, String metered) throws Exception {
        final String setCommand = "cmd netpolicy set metered-network " + ssid + " " + metered;
        runShellCommand(mInstrumentation, setCommand);
        assertEquals(getWifiMeteredStatus(ssid), metered);
    }

    private String unquoteSSID(String ssid) {
        // SSID is returned surrounded by quotes if it can be decoded as UTF-8.
        // Otherwise it's guaranteed not to start with a quote.
        if (ssid.charAt(0) == '"') {
            return ssid.substring(1, ssid.length() - 1);
        } else {
            return ssid;
        }
    }

    private Network waitForActiveNetworkMetered(final int targetTransportType,
            final boolean requestedMeteredness, final boolean waitForValidation,
            final boolean useSystemDefault)
            throws Exception {
        final CompletableFuture<Network> networkFuture = new CompletableFuture<>();
        final NetworkCallback networkCallback = new NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                if (!nc.hasTransport(targetTransportType)) return;

                final boolean metered = !nc.hasCapability(NET_CAPABILITY_NOT_METERED);
                final boolean validated = nc.hasCapability(NET_CAPABILITY_VALIDATED);
                if (metered == requestedMeteredness && (!waitForValidation || validated)) {
                    networkFuture.complete(network);
                }
            }
        };

        try {
            // Registering a callback here guarantees onCapabilitiesChanged is called immediately
            // with the current setting. Therefore, if the setting has already been changed,
            // this method will return right away, and if not, it'll wait for the setting to change.
            if (useSystemDefault) {
                runWithShellPermissionIdentity(() ->
                                registerSystemDefaultNetworkCallback(networkCallback,
                                        new Handler(Looper.getMainLooper())),
                        NETWORK_SETTINGS);
            } else {
                registerDefaultNetworkCallback(networkCallback);
            }

            // Changing meteredness on wifi involves reconnecting, which can take several seconds
            // (involves re-associating, DHCP...).
            return networkFuture.get(NETWORK_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out waiting for active network metered status to "
                    + "change to " + requestedMeteredness + " ; network = "
                    + mCm.getActiveNetwork(), e);
        }
    }

    private Network setWifiMeteredStatusAndWait(String ssid, boolean isMetered,
            boolean waitForValidation) throws Exception {
        setWifiMeteredStatus(ssid, Boolean.toString(isMetered) /* metered */);
        mCtsNetUtils.ensureWifiConnected();
        return waitForActiveNetworkMetered(TRANSPORT_WIFI,
                isMetered /* requestedMeteredness */,
                waitForValidation,
                true /* useSystemDefault */);
    }

    private void assertMultipathPreferenceIsEventually(Network network, int oldValue,
            int expectedValue) {
        // Quick check : if oldValue == expectedValue, there is no way to guarantee the test
        // is not flaky.
        assertNotSame(oldValue, expectedValue);

        for (int i = 0; i < NUM_TRIES_MULTIPATH_PREF_CHECK; ++i) {
            final int actualValue = mCm.getMultipathPreference(network);
            if (actualValue == expectedValue) {
                return;
            }
            if (actualValue != oldValue) {
                fail("Multipath preference is neither previous (" + oldValue
                        + ") nor expected (" + expectedValue + ")");
            }
            SystemClock.sleep(INTERVAL_MULTIPATH_PREF_CHECK_MS);
        }
        fail("Timed out waiting for multipath preference to change. expected = "
                + expectedValue + " ; actual = " + mCm.getMultipathPreference(network));
    }

    private int getCurrentMeteredMultipathPreference(ContentResolver resolver) {
        final String rawMeteredPref = Settings.Global.getString(resolver,
                NETWORK_METERED_MULTIPATH_PREFERENCE);
        return TextUtils.isEmpty(rawMeteredPref)
            ? getIntResourceForName(NETWORK_METERED_MULTIPATH_PREFERENCE_RES_NAME)
            : Integer.parseInt(rawMeteredPref);
    }

    private int findNextPrefValue(ContentResolver resolver) {
        // A bit of a nuclear hammer, but race conditions in CTS are bad. To be able to
        // detect a correct setting value without race conditions, the next pref must
        // be a valid value (range 0..3) that is different from the old setting of the
        // metered preference and from the unmetered preference.
        final int meteredPref = getCurrentMeteredMultipathPreference(resolver);
        final int unmeteredPref = ConnectivityManager.MULTIPATH_PREFERENCE_UNMETERED;
        if (0 != meteredPref && 0 != unmeteredPref) return 0;
        if (1 != meteredPref && 1 != unmeteredPref) return 1;
        return 2;
    }

    /**
     * Verify that getMultipathPreference does return appropriate values
     * for metered and unmetered networks.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testGetMultipathPreference() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));
        final ContentResolver resolver = mContext.getContentResolver();
        mCtsNetUtils.ensureWifiConnected();
        final String ssid = unquoteSSID(mWifiManager.getConnectionInfo().getSSID());
        final String oldMeteredSetting = getWifiMeteredStatus(ssid);
        final String oldMeteredMultipathPreference = Settings.Global.getString(
                resolver, NETWORK_METERED_MULTIPATH_PREFERENCE);
        try {
            final int initialMeteredPreference = getCurrentMeteredMultipathPreference(resolver);
            int newMeteredPreference = findNextPrefValue(resolver);
            Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                    Integer.toString(newMeteredPreference));
            // Wifi meteredness changes from unmetered to metered will disconnect and reconnect
            // since R.
            final Network network = setWifiMeteredStatusAndWait(ssid, true /* isMetered */,
                    false /* waitForValidation */);
            assertEquals(ssid, unquoteSSID(mWifiManager.getConnectionInfo().getSSID()));
            assertEquals(mCm.getNetworkCapabilities(network).hasCapability(
                    NET_CAPABILITY_NOT_METERED), false);
            assertMultipathPreferenceIsEventually(network, initialMeteredPreference,
                    newMeteredPreference);

            final int oldMeteredPreference = newMeteredPreference;
            newMeteredPreference = findNextPrefValue(resolver);
            Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                    Integer.toString(newMeteredPreference));
            assertEquals(mCm.getNetworkCapabilities(network).hasCapability(
                    NET_CAPABILITY_NOT_METERED), false);
            assertMultipathPreferenceIsEventually(network,
                    oldMeteredPreference, newMeteredPreference);

            // No disconnect from unmetered to metered.
            setWifiMeteredStatusAndWait(ssid, false /* isMetered */, false /* waitForValidation */);
            assertEquals(mCm.getNetworkCapabilities(network).hasCapability(
                    NET_CAPABILITY_NOT_METERED), true);
            assertMultipathPreferenceIsEventually(network, newMeteredPreference,
                    ConnectivityManager.MULTIPATH_PREFERENCE_UNMETERED);
        } finally {
            Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                    oldMeteredMultipathPreference);
            setWifiMeteredStatus(ssid, oldMeteredSetting);
        }
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) @ConnectivityModuleTest
    public void testSetBackgroundNetworkingShellCommand() {
        final int testUid = 54352;
        runShellCommand("cmd connectivity set-background-networking-enabled-for-uid " + testUid
                + " true");
        int rule = runAsShell(NETWORK_SETTINGS,
                () -> mCm.getUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, testUid));
        assertEquals(rule, FIREWALL_RULE_ALLOW);

        runShellCommand("cmd connectivity set-background-networking-enabled-for-uid " + testUid
                + " false");
        rule = runAsShell(NETWORK_SETTINGS,
                () -> mCm.getUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, testUid));
        assertEquals(rule, FIREWALL_RULE_DENY);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) @ConnectivityModuleTest
    public void testGetBackgroundNetworkingShellCommand() {
        final int testUid = 54312;
        runAsShell(NETWORK_SETTINGS,
                () -> mCm.setUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, testUid,
                        FIREWALL_RULE_ALLOW));
        String output = runShellCommand(
                "cmd connectivity get-background-networking-enabled-for-uid " + testUid);
        assertTrue(output.contains("allow"));

        runAsShell(NETWORK_SETTINGS,
                () -> mCm.setUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, testUid,
                        FIREWALL_RULE_DEFAULT));
        output = runShellCommand(
                "cmd connectivity get-background-networking-enabled-for-uid " + testUid);
        assertTrue(output.contains("deny"));
    }

    // TODO: move the following socket keep alive test to dedicated test class.
    /**
     * Callback used in tcp keepalive offload that allows caller to wait callback fires.
     */
    private static class TestSocketKeepaliveCallback extends SocketKeepalive.Callback {
        public enum CallbackType { ON_STARTED, ON_STOPPED, ON_ERROR };

        public static class CallbackValue {
            public final CallbackType callbackType;
            public final int error;

            private CallbackValue(final CallbackType type, final int error) {
                this.callbackType = type;
                this.error = error;
            }

            public static class OnStartedCallback extends CallbackValue {
                OnStartedCallback() { super(CallbackType.ON_STARTED, 0); }
            }

            public static class OnStoppedCallback extends CallbackValue {
                OnStoppedCallback() { super(CallbackType.ON_STOPPED, 0); }
            }

            public static class OnErrorCallback extends CallbackValue {
                OnErrorCallback(final int error) { super(CallbackType.ON_ERROR, error); }
            }

            @Override
            public boolean equals(Object o) {
                return o.getClass() == this.getClass()
                        && this.callbackType == ((CallbackValue) o).callbackType
                        && this.error == ((CallbackValue) o).error;
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %d)", getClass().getSimpleName(), callbackType, error);
            }
        }

        private final LinkedBlockingQueue<CallbackValue> mCallbacks = new LinkedBlockingQueue<>();

        @Override
        public void onStarted() {
            mCallbacks.add(new CallbackValue.OnStartedCallback());
        }

        @Override
        public void onStopped() {
            mCallbacks.add(new CallbackValue.OnStoppedCallback());
        }

        @Override
        public void onError(final int error) {
            mCallbacks.add(new CallbackValue.OnErrorCallback(error));
        }

        public CallbackValue pollCallback() {
            try {
                return mCallbacks.poll(KEEPALIVE_CALLBACK_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                fail("Callback not seen after " + KEEPALIVE_CALLBACK_TIMEOUT_MS + " ms");
            }
            return null;
        }
        private void expectCallback(CallbackValue expectedCallback) {
            final CallbackValue actualCallback = pollCallback();
            assertEquals(expectedCallback, actualCallback);
        }

        public void expectStarted() {
            expectCallback(new CallbackValue.OnStartedCallback());
        }

        public void expectStopped() {
            expectCallback(new CallbackValue.OnStoppedCallback());
        }

        public void expectError(int error) {
            expectCallback(new CallbackValue.OnErrorCallback(error));
        }
    }

    private InetAddress getAddrByName(final String hostname, final int family) throws Exception {
        final InetAddress[] allAddrs = InetAddress.getAllByName(hostname);
        for (InetAddress addr : allAddrs) {
            if (family == AF_INET && addr instanceof Inet4Address) return addr;

            if (family == AF_INET6 && addr instanceof Inet6Address) return addr;

            if (family == AF_UNSPEC) return addr;
        }
        return null;
    }

    private Socket getConnectedSocket(final Network network, final String host, final int port,
            final int family) throws Exception {
        final Socket s = network.getSocketFactory().createSocket();
        try {
            final InetAddress addr = getAddrByName(host, family);
            if (addr == null) fail("Fail to get destination address for " + family);

            final InetSocketAddress sockAddr = new InetSocketAddress(addr, port);
            s.connect(sockAddr);
        } catch (Exception e) {
            s.close();
            throw e;
        }
        return s;
    }

    private int getSupportedKeepalivesForNet(@NonNull Network network) throws Exception {
        final NetworkCapabilities nc = mCm.getNetworkCapabilities(network);

        // Get number of supported concurrent keepalives for testing network.
        final int[] keepalivesPerTransport = runAsShell(NETWORK_SETTINGS,
                () -> mCm.getSupportedKeepalives());
        return KeepaliveUtils.getSupportedKeepalivesForNetworkCapabilities(
                keepalivesPerTransport, nc);
    }

    private static boolean isTcpKeepaliveSupportedByKernel() {
        final String kVersionString = VintfRuntimeInfo.getKernelVersion();
        return DeviceInfoUtils.compareMajorMinorVersion(kVersionString, "4.8") >= 0;
    }

    /**
     * Verifies that the keepalive API cannot create any keepalive when the maximum number of
     * keepalives is set to 0.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    // getSupportedKeepalives is available in updatable ConnectivityManager (S+)
    // Also, this feature is not mainlined before S, and it's fine to skip on R- devices.
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R) @ConnectivityModuleTest
    @Test
    public void testKeepaliveWifiUnsupported() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final Network network = mCtsNetUtils.ensureWifiConnected();
        if (getSupportedKeepalivesForNet(network) != 0) return;
        final InetAddress srcAddr = getFirstV4Address(network);
        assumeTrue("This test requires native IPv4", srcAddr != null);

        runWithShellPermissionIdentity(() -> {
            assertEquals(0, createConcurrentSocketKeepalives(network, srcAddr, 1, 0));
            assertEquals(0, createConcurrentSocketKeepalives(network, srcAddr, 0, 1));
        });
    }

    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    // getSupportedKeepalives is available in updatable ConnectivityManager (S+)
    // Also, this feature is not mainlined before S, and it's fine to skip on R- devices.
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R) @ConnectivityModuleTest
    @Test
    @RequiresDevice // Keepalive is not supported on virtual hardware
    public void testCreateTcpKeepalive() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final Network network = mCtsNetUtils.ensureWifiConnected();
        if (getSupportedKeepalivesForNet(network) == 0) return;
        final InetAddress srcAddr = getFirstV4Address(network);
        assumeTrue("This test requires native IPv4", srcAddr != null);

        // If kernel < 4.8 then it doesn't support TCP keepalive, but it might still support
        // NAT-T keepalive. If keepalive limits from resource overlay is not zero, TCP keepalive
        // needs to be supported except if the kernel doesn't support it.
        if (!isTcpKeepaliveSupportedByKernel()) {
            // Verify that the callback result is expected.
            runWithShellPermissionIdentity(() -> {
                assertEquals(0, createConcurrentSocketKeepalives(network, srcAddr, 0, 1));
            });
            Log.i(TAG, "testCreateTcpKeepalive is skipped for kernel "
                    + VintfRuntimeInfo.getKernelVersion());
            return;
        }

        final byte[] requestBytes = CtsNetUtils.HTTP_REQUEST.getBytes("UTF-8");
        // So far only ipv4 tcp keepalive offload is supported.
        // TODO: add test case for ipv6 tcp keepalive offload when it is supported.
        try (Socket s = getConnectedSocket(network, TEST_HOST, HTTP_PORT, AF_INET)) {

            // Should able to start keep alive offload when socket is idle.
            final Executor executor = mContext.getMainExecutor();
            final TestSocketKeepaliveCallback callback = new TestSocketKeepaliveCallback();

            mUiAutomation.adoptShellPermissionIdentity();
            try (SocketKeepalive sk = mCm.createSocketKeepalive(network, s, executor, callback)) {
                sk.start(MIN_KEEPALIVE_INTERVAL);
                callback.expectStarted();

                // App should not able to write during keepalive offload.
                final OutputStream out = s.getOutputStream();
                try {
                    out.write(requestBytes);
                    fail("Should not able to write");
                } catch (IOException e) { }
                // App should not able to read during keepalive offload.
                final InputStream in = s.getInputStream();
                byte[] responseBytes = new byte[4096];
                try {
                    in.read(responseBytes);
                    fail("Should not able to read");
                } catch (IOException e) { }

                // Stop.
                sk.stop();
                callback.expectStopped();
            } finally {
                mUiAutomation.dropShellPermissionIdentity();
            }

            // Ensure socket is still connected.
            assertTrue(s.isConnected());
            assertFalse(s.isClosed());

            // Let socket be not idle.
            try {
                final OutputStream out = s.getOutputStream();
                out.write(requestBytes);
            } catch (IOException e) {
                fail("Failed to write data " + e);
            }
            // Make sure response data arrives.
            final MessageQueue fdHandlerQueue = Looper.getMainLooper().getQueue();
            final FileDescriptor fd = s.getFileDescriptor$();
            final CountDownLatch mOnReceiveLatch = new CountDownLatch(1);
            fdHandlerQueue.addOnFileDescriptorEventListener(fd, EVENT_INPUT, (readyFd, events) -> {
                mOnReceiveLatch.countDown();
                return 0; // Unregister listener.
            });
            if (!mOnReceiveLatch.await(2, TimeUnit.SECONDS)) {
                fdHandlerQueue.removeOnFileDescriptorEventListener(fd);
                fail("Timeout: no response data");
            }

            // Should get ERROR_SOCKET_NOT_IDLE because there is still data in the receive queue
            // that has not been read.
            mUiAutomation.adoptShellPermissionIdentity();
            try (SocketKeepalive sk = mCm.createSocketKeepalive(network, s, executor, callback)) {
                sk.start(MIN_KEEPALIVE_INTERVAL);
                callback.expectError(SocketKeepalive.ERROR_SOCKET_NOT_IDLE);
            } finally {
                mUiAutomation.dropShellPermissionIdentity();
            }
        }
    }

    private ArrayList<SocketKeepalive> createConcurrentKeepalivesOfType(
            int requestCount, @NonNull TestSocketKeepaliveCallback callback,
            Supplier<SocketKeepalive> kaFactory) {
        final ArrayList<SocketKeepalive> kalist = new ArrayList<>();

        int remainingRetries = MAX_KEEPALIVE_RETRY_COUNT;

        // Test concurrent keepalives with the given supplier.
        while (kalist.size() < requestCount) {
            final SocketKeepalive ka = kaFactory.get();
            ka.start(MIN_KEEPALIVE_INTERVAL);
            TestSocketKeepaliveCallback.CallbackValue cv = callback.pollCallback();
            assertNotNull(cv);
            if (cv.callbackType == TestSocketKeepaliveCallback.CallbackType.ON_ERROR) {
                if (kalist.size() == 0 && cv.error == SocketKeepalive.ERROR_UNSUPPORTED) {
                    // Unsupported.
                    break;
                } else if (cv.error == SocketKeepalive.ERROR_INSUFFICIENT_RESOURCES) {
                    // Limit reached or temporary unavailable due to stopped slot is not yet
                    // released.
                    if (remainingRetries > 0) {
                        SystemClock.sleep(INTERVAL_KEEPALIVE_RETRY_MS);
                        remainingRetries--;
                        continue;
                    }
                    break;
                }
            }
            if (cv.callbackType == TestSocketKeepaliveCallback.CallbackType.ON_STARTED) {
                kalist.add(ka);
            } else {
                fail("Unexpected error when creating " + (kalist.size() + 1) + " "
                        + ka.getClass().getSimpleName() + ": " + cv);
            }
        }

        return kalist;
    }

    private @NonNull ArrayList<SocketKeepalive> createConcurrentNattSocketKeepalives(
            @NonNull Network network, @NonNull InetAddress srcAddr, int requestCount,
            @NonNull TestSocketKeepaliveCallback callback)  throws Exception {

        final Executor executor = mContext.getMainExecutor();

        // Initialize a real NaT-T socket.
        final IpSecManager mIpSec = (IpSecManager) mContext.getSystemService(Context.IPSEC_SERVICE);
        final UdpEncapsulationSocket nattSocket = mIpSec.openUdpEncapsulationSocket();
        final InetAddress dstAddr = getAddrByName(TEST_HOST, AF_INET);
        assertNotNull(srcAddr);
        assertNotNull(dstAddr);

        // Test concurrent Nat-T keepalives.
        final ArrayList<SocketKeepalive> result = createConcurrentKeepalivesOfType(requestCount,
                callback, () -> mCm.createSocketKeepalive(network, nattSocket,
                        srcAddr, dstAddr, executor, callback));

        nattSocket.close();
        return result;
    }

    private @NonNull ArrayList<SocketKeepalive> createConcurrentTcpSocketKeepalives(
            @NonNull Network network, int requestCount,
            @NonNull TestSocketKeepaliveCallback callback) {
        final Executor executor = mContext.getMainExecutor();

        // Create concurrent TCP keepalives.
        return createConcurrentKeepalivesOfType(requestCount, callback, () -> {
            // Assert that TCP connections can be established. The file descriptor of tcp
            // sockets will be duplicated and kept valid in service side if the keepalives are
            // successfully started.
            try (Socket tcpSocket = getConnectedSocket(network, TEST_HOST, HTTP_PORT,
                    AF_INET)) {
                return mCm.createSocketKeepalive(network, tcpSocket, executor, callback);
            } catch (Exception e) {
                fail("Unexpected error when creating TCP socket: " + e);
            }
            return null;
        });
    }

    /**
     * Creates concurrent keepalives until the specified counts of each type of keepalives are
     * reached or the expected error callbacks are received for each type of keepalives.
     *
     * @return the total number of keepalives created.
     */
    private int createConcurrentSocketKeepalives(
            @NonNull Network network, @NonNull InetAddress srcAddr, int nattCount, int tcpCount)
            throws Exception {
        final ArrayList<SocketKeepalive> kalist = new ArrayList<>();
        final TestSocketKeepaliveCallback callback = new TestSocketKeepaliveCallback();

        kalist.addAll(createConcurrentNattSocketKeepalives(network, srcAddr, nattCount, callback));
        kalist.addAll(createConcurrentTcpSocketKeepalives(network, tcpCount, callback));

        final int ret = kalist.size();

        // Clean up.
        for (final SocketKeepalive ka : kalist) {
            ka.stop();
            callback.expectStopped();
        }
        kalist.clear();

        return ret;
    }

    /**
     * Verifies that the concurrent keepalive slots meet the minimum requirement, and don't
     * get leaked after iterations.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    // getSupportedKeepalives is available in updatable ConnectivityManager (S+)
    // Also, this feature is not mainlined before S, and it's fine to skip on R- devices.
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R) @ConnectivityModuleTest
    @RequiresDevice // Keepalive is not supported on virtual hardware
    public void testSocketKeepaliveLimitWifi() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final Network network = mCtsNetUtils.ensureWifiConnected();
        final int supported = getSupportedKeepalivesForNet(network);
        if (supported == 0) {
            return;
        }
        final InetAddress srcAddr = getFirstV4Address(network);
        assumeTrue("This test requires native IPv4", srcAddr != null);

        runWithShellPermissionIdentity(() -> {
            // Verifies that the supported keepalive slots meet MIN_SUPPORTED_KEEPALIVE_COUNT.
            assertGreaterOrEqual(supported, MIN_SUPPORTED_WIFI_KEEPALIVE_COUNT);

            // Verifies that Nat-T keepalives can be established.
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr,
                    supported + 1, 0));
            // Verifies that keepalives don't get leaked in second round.
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr, supported,
                    0));
        });

        // If kernel < 4.8 then it doesn't support TCP keepalive, but it might still support
        // NAT-T keepalive. Test below cases only if TCP keepalive is supported by kernel.
        if (!isTcpKeepaliveSupportedByKernel()) return;

        runWithShellPermissionIdentity(() -> {
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr, 0,
                    supported + 1));

            // Verifies that different types can be established at the same time.
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr,
                    supported / 2, supported - supported / 2));

            // Verifies that keepalives don't get leaked in second round.
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr, 0,
                    supported));
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr,
                    supported / 2, supported - supported / 2));
        });
    }

    /**
     * Verifies that the concurrent keepalive slots meet the minimum telephony requirement, and
     * don't get leaked after iterations.
     */
    @AppModeFull(reason = "Cannot request network in instant app mode")
    @Test
    // getSupportedKeepalives is available in updatable ConnectivityManager (S+)
    // Also, this feature is not mainlined before S, and it's fine to skip on R- devices.
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R) @ConnectivityModuleTest
    @RequiresDevice // Keepalive is not supported on virtual hardware
    public void testSocketKeepaliveLimitTelephony() throws Exception {
        if (!mPackageManager.hasSystemFeature(FEATURE_TELEPHONY)) {
            Log.i(TAG, "testSocketKeepaliveLimitTelephony cannot execute unless device"
                    + " supports telephony");
            return;
        }

        final int firstSdk = SdkLevel.isAtLeastS()
                ? Build.VERSION.DEVICE_INITIAL_SDK_INT
                // FIRST_SDK_INT was a @TestApi field renamed to DEVICE_INITIAL_SDK_INT in S
                : Build.VERSION.class.getField("FIRST_SDK_INT").getInt(null);
        if (firstSdk < Build.VERSION_CODES.Q) {
            Log.i(TAG, "testSocketKeepaliveLimitTelephony: skip test for devices launching"
                    + " before Q: " + firstSdk);
            return;
        }

        final Network network = mCtsNetUtils.connectToCell();
        final int supported = getSupportedKeepalivesForNet(network);
        final InetAddress srcAddr = getFirstV4Address(network);
        assumeTrue("This test requires native IPv4", srcAddr != null);

        runWithShellPermissionIdentity(() -> {
            // Verifies that the supported keepalive slots meet minimum requirement.
            assertGreaterOrEqual(supported, MIN_SUPPORTED_CELLULAR_KEEPALIVE_COUNT);
            // Verifies that Nat-T keepalives can be established.
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr,
                    supported + 1, 0));
            // Verifies that keepalives don't get leaked in second round.
            assertEquals(supported, createConcurrentSocketKeepalives(network, srcAddr, supported,
                    0));
        });
    }

    private int getIntResourceForName(@NonNull String resName) {
        final Resources r = mContext.getResources();
        final int resId = r.getIdentifier(resName, "integer", "android");
        return r.getInteger(resId);
    }

    /**
     * Verifies that the keepalive slots are limited as customized for unprivileged requests.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    // getSupportedKeepalives is available in updatable ConnectivityManager (S+)
    // Also, this feature is not mainlined before S, and it's fine to skip on R- devices.
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R) @ConnectivityModuleTest
    @RequiresDevice // Keepalive is not supported on virtual hardware
    public void testSocketKeepaliveUnprivileged() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final Network network = mCtsNetUtils.ensureWifiConnected();
        final int supported = getSupportedKeepalivesForNet(network);
        if (supported == 0) {
            return;
        }
        final InetAddress srcAddr = getFirstV4Address(network);
        assumeTrue("This test requires native IPv4", srcAddr != null);

        // Resource ID might be shifted on devices that compiled with different symbols.
        // Thus, resolve ID at runtime is needed.
        final int allowedUnprivilegedPerUid =
                getIntResourceForName(KEEPALIVE_ALLOWED_UNPRIVILEGED_RES_NAME);
        final int reservedPrivilegedSlots =
                getIntResourceForName(KEEPALIVE_RESERVED_PER_SLOT_RES_NAME);
        // Verifies that unprivileged request per uid cannot exceed the limit customized in the
        // resource. Currently, unprivileged keepalive slots are limited to Nat-T only, this test
        // does not apply to TCP.
        assertGreaterOrEqual(supported, reservedPrivilegedSlots);
        assertGreaterOrEqual(supported, allowedUnprivilegedPerUid);
        final int expectedUnprivileged =
                Math.min(allowedUnprivilegedPerUid, supported - reservedPrivilegedSlots);
        assertEquals(expectedUnprivileged,
                createConcurrentSocketKeepalives(network, srcAddr, supported + 1, 0));
    }

    private static void assertGreaterOrEqual(long greater, long lesser) {
        assertTrue("" + greater + " expected to be greater than or equal to " + lesser,
                greater >= lesser);
    }

    private void verifyBindSocketToRestrictedNetworkDisallowed() throws Exception {
        final TestableNetworkCallback testNetworkCb = new TestableNetworkCallback();
        final NetworkRequest testRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(
                        TEST_RESTRICTED_NW_IFACE_NAME))
                .build();
        runWithShellPermissionIdentity(() -> requestNetwork(testRequest, testNetworkCb),
                CONNECTIVITY_USE_RESTRICTED_NETWORKS,
                // CONNECTIVITY_INTERNAL is for requesting restricted network because shell does not
                // have CONNECTIVITY_USE_RESTRICTED_NETWORKS on R.
                CONNECTIVITY_INTERNAL);

        // Create a restricted network and ensure this package cannot bind to that network either.
        final NetworkAgent agent = createRestrictedNetworkAgent(mContext);
        final Network network = agent.getNetwork();

        try (Socket socket = new Socket()) {
            // Verify that the network is restricted.
            testNetworkCb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                    NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> network.equals(entry.getNetwork())
                            && (!((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                            .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)));
            // CtsNetTestCases package doesn't hold CONNECTIVITY_USE_RESTRICTED_NETWORKS, so it
            // does not allow to bind socket to restricted network.
            assertThrows(IOException.class, () -> network.bindSocket(socket));
        } finally {
            agent.unregister();
        }
    }

    /**
     * Verifies that apps are not allowed to access restricted networks even if they declare the
     * CONNECTIVITY_USE_RESTRICTED_NETWORKS permission in their manifests.
     * See. b/144679405.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testRestrictedNetworkPermission() throws Exception {
        // Ensure that CONNECTIVITY_USE_RESTRICTED_NETWORKS isn't granted to this package.
        final PackageInfo app = mPackageManager.getPackageInfo(mContext.getPackageName(),
                GET_PERMISSIONS);
        final int index = ArrayUtils.indexOf(
                app.requestedPermissions, CONNECTIVITY_USE_RESTRICTED_NETWORKS);
        assertTrue(index >= 0);
        assertTrue(app.requestedPermissionsFlags[index] != PERMISSION_GRANTED);

        if (mPackageManager.hasSystemFeature(FEATURE_WIFI)) {
            // Expect binding to the wifi network to succeed.
            final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
            try (Socket socket = new Socket()) {
                wifiNetwork.bindSocket(socket);
            }
        }

        // Ensure that this package cannot bind to any restricted network that's currently
        // connected.
        Network[] networks = mCm.getAllNetworks();
        for (Network network : networks) {
            final NetworkCapabilities nc = mCm.getNetworkCapabilities(network);
            if (nc == null) {
                continue;
            }

            try (Socket socket = new Socket()) {
                if (nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
                    network.bindSocket(socket);  // binding should succeed
                } else {
                    assertThrows(IOException.class, () -> network.bindSocket(socket));
                }
            }
        }

        verifyBindSocketToRestrictedNetworkDisallowed();
    }

    /**
     * Verifies that apps are allowed to call setAirplaneMode if they declare
     * NETWORK_AIRPLANE_MODE permission in their manifests.
     * See b/145164696.
     */
    @AppModeFull(reason = "NETWORK_AIRPLANE_MODE permission can't be granted to instant apps")
    @Test
    @SkipPresubmit(reason = "Out of SLO flakiness")
    public void testSetAirplaneMode() throws Exception{
        // Starting from T, wifi supports airplane mode enhancement which may not disconnect wifi
        // when airplane mode is on. The actual behavior that the device will have could only be
        // checked with hidden wifi APIs(see Settings.Secure.WIFI_APM_STATE). Thus, stop verifying
        // wifi on T+ device.
        final boolean verifyWifi = mPackageManager.hasSystemFeature(FEATURE_WIFI)
                && !SdkLevel.isAtLeastT();
        final boolean supportTelephony = mPackageManager.hasSystemFeature(FEATURE_TELEPHONY);
        // store the current state of airplane mode
        final boolean isAirplaneModeEnabled = isAirplaneModeEnabled();
        final TestableNetworkCallback wifiCb = new TestableNetworkCallback();
        final TestableNetworkCallback telephonyCb = new TestableNetworkCallback();
        // disable airplane mode to reach a known state
        runShellCommand("cmd connectivity airplane-mode disable");
        // Verify that networks are available as expected if wifi or cell is supported. Continue the
        // test if none of them are supported since test should still able to verify the permission
        // mechanism.
        if (verifyWifi) {
            mCtsNetUtils.ensureWifiConnected();
            registerCallbackAndWaitForAvailable(makeWifiNetworkRequest(), wifiCb);
        }
        if (supportTelephony) {
            // connectToCell needs to be followed by disconnectFromCell, which is called in tearDown
            mCtsNetUtils.connectToCell();
            registerCallbackAndWaitForAvailable(makeCellNetworkRequest(), telephonyCb);
        }

        try {
            // Verify we cannot set Airplane Mode without correct permission:
            assertThrows(SecurityException.class, () -> setAndVerifyAirplaneMode(true));

            // disable airplane mode again to reach a known state
            runShellCommand("cmd connectivity airplane-mode disable");

            // Verify we can enable Airplane Mode with correct permission.
            // TODO: test that NETWORK_AIRPLANE_MODE works as well, once the shell has it.
            runAsShell(NETWORK_SETTINGS, () -> setAndVerifyAirplaneMode(true));

            // Verify that the enabling airplane mode takes effect as expected to prevent flakiness
            // caused by fast airplane mode switches. Ensure network lost before turning off
            // airplane mode.
            if (verifyWifi) waitForLost(wifiCb);
            if (supportTelephony) waitForLost(telephonyCb);

            // Verify we can disable Airplane Mode with correct permission:
            runAsShell(NETWORK_SETTINGS, () -> setAndVerifyAirplaneMode(false));

            // Verify that turning airplane mode off takes effect as expected.
            // connectToCell only registers a request, it cannot / does not need to be called twice
            mCtsNetUtils.ensureWifiConnected();
            if (verifyWifi) waitForAvailable(wifiCb);
            if (supportTelephony) waitForAvailable(telephonyCb);
        } finally {
            // Restore the previous state of airplane mode and permissions:
            runShellCommand("cmd connectivity airplane-mode "
                    + (isAirplaneModeEnabled ? "enable" : "disable"));
        }
    }

    private void registerCallbackAndWaitForAvailable(@NonNull final NetworkRequest request,
            @NonNull final TestableNetworkCallback cb) {
        registerNetworkCallback(request, cb);
        waitForAvailable(cb);
    }

    private void waitForAvailable(@NonNull final TestableNetworkCallback cb) {
        cb.eventuallyExpect(CallbackEntry.AVAILABLE, NETWORK_CALLBACK_TIMEOUT_MS,
                c -> c instanceof CallbackEntry.Available);
    }

    private void waitForTransport(
            @NonNull final TestableNetworkCallback cb, final int expectedTransport) {
        cb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                NETWORK_CALLBACK_TIMEOUT_MS,
                entry -> ((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                        .hasTransport(expectedTransport));
    }

    private void waitForAvailable(
            @NonNull final TestableNetworkCallback cb, @NonNull final Network expectedNetwork) {
        cb.expectAvailableCallbacks(expectedNetwork, false /* suspended */,
                null /* validated */,
                false /* blocked */, NETWORK_CALLBACK_TIMEOUT_MS);
    }

    private void waitForLost(@NonNull final TestableNetworkCallback cb) {
        cb.eventuallyExpect(CallbackEntry.LOST, NETWORK_CALLBACK_TIMEOUT_MS,
                c -> c instanceof CallbackEntry.Lost);
    }

    private void setAndVerifyAirplaneMode(Boolean expectedResult)
            throws Exception {
        final CompletableFuture<Boolean> actualResult = new CompletableFuture();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // The defaultValue of getExtraBoolean should be the opposite of what is
                // expected, thus ensuring a test failure if the extra is absent.
                actualResult.complete(intent.getBooleanExtra("state", !expectedResult));
            }
        };
        try {
            mContext.registerReceiver(receiver,
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
            mCm.setAirplaneMode(expectedResult);
            final String msg = "Setting Airplane Mode failed,";
            assertEquals(msg, expectedResult, actualResult.get(AIRPLANE_MODE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS));
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private static boolean isAirplaneModeEnabled() {
        return runShellCommand("cmd connectivity airplane-mode")
                .trim().equals("enabled");
    }

    @Test
    public void testGetCaptivePortalServerUrl() {
        final String permission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                ? CONNECTIVITY_INTERNAL
                : NETWORK_SETTINGS;
        final String url = runAsShell(permission, mCm::getCaptivePortalServerUrl);
        assertNotNull("getCaptivePortalServerUrl must not be null", url);
        try {
            final URL parsedUrl = new URL(url);
            // As per the javadoc, the URL must be HTTP
            assertEquals("Invalid captive portal URL protocol", "http", parsedUrl.getProtocol());
        } catch (MalformedURLException e) {
            throw new AssertionFailedError("Captive portal server URL is invalid: " + e);
        }
    }

    /**
     * Verifies that apps are forbidden from getting ssid information from
     * {@Code NetworkCapabilities} if they do not hold NETWORK_SETTINGS permission.
     * See b/161370134.
     */
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testSsidInNetworkCapabilities() throws Exception {
        assumeTrue("testSsidInNetworkCapabilities cannot execute unless device supports WiFi",
                mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final Network network = mCtsNetUtils.ensureWifiConnected();
        final String ssid = unquoteSSID(mWifiManager.getConnectionInfo().getSSID());
        assertNotNull("Ssid getting from WifiManager is null", ssid);
        // This package should have no NETWORK_SETTINGS permission. Verify that no ssid is contained
        // in the NetworkCapabilities.
        verifySsidFromQueriedNetworkCapabilities(network, ssid, false /* hasSsid */);
        verifySsidFromCallbackNetworkCapabilities(ssid, false /* hasSsid */);
        // Adopt shell permission to allow to get ssid information.
        runWithShellPermissionIdentity(() -> {
            verifySsidFromQueriedNetworkCapabilities(network, ssid, true /* hasSsid */);
            verifySsidFromCallbackNetworkCapabilities(ssid, true /* hasSsid */);
        });
    }

    private void verifySsidFromQueriedNetworkCapabilities(@NonNull Network network,
            @NonNull String ssid, boolean hasSsid) throws Exception {
        // Verify if ssid is contained in NetworkCapabilities queried from ConnectivityManager.
        final NetworkCapabilities nc = mCm.getNetworkCapabilities(network);
        assertNotNull("NetworkCapabilities of the network is null", nc);
        assertEquals(hasSsid, Pattern.compile(ssid).matcher(nc.toString()).find());
    }

    private void verifySsidFromCallbackNetworkCapabilities(@NonNull String ssid, boolean hasSsid)
            throws Exception {
        final CompletableFuture<NetworkCapabilities> foundNc = new CompletableFuture();
        final NetworkCallback callback = new NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                foundNc.complete(nc);
            }
        };

        registerNetworkCallback(makeWifiNetworkRequest(), callback);
        // Registering a callback here guarantees onCapabilitiesChanged is called immediately
        // because WiFi network should be connected.
        final NetworkCapabilities nc =
                foundNc.get(NETWORK_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        // Verify if ssid is contained in the NetworkCapabilities received from callback.
        assertNotNull("NetworkCapabilities of the network is null", nc);
        assertEquals(hasSsid, Pattern.compile(ssid).matcher(nc.toString()).find());
    }

    /**
     * Verify background request can only be requested when acquiring
     * {@link android.Manifest.permission.NETWORK_SETTINGS}.
     */
    @AppModeFull(reason = "Instant apps cannot create test networks")
    @Test
    public void testRequestBackgroundNetwork() {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());

        // Create a tun interface. Use the returned interface name as the specifier to create
        // a test network request.
        final TestNetworkManager tnm = runWithShellPermissionIdentity(() ->
                mContext.getSystemService(TestNetworkManager.class),
                android.Manifest.permission.MANAGE_TEST_NETWORKS);
        final TestNetworkInterface testNetworkInterface = runWithShellPermissionIdentity(() ->
                    tnm.createTunInterface(new LinkAddress[]{TEST_LINKADDR}),
                android.Manifest.permission.MANAGE_TEST_NETWORKS,
                android.Manifest.permission.NETWORK_SETTINGS);
        assertNotNull(testNetworkInterface);

        final NetworkRequest testRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_TEST)
                // Test networks do not have NOT_VPN or TRUSTED capabilities by default
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(
                        testNetworkInterface.getInterfaceName()))
                .build();

        // Verify background network cannot be requested without NETWORK_SETTINGS permission.
        final TestableNetworkCallback callback = new TestableNetworkCallback();
        final Handler handler = new Handler(Looper.getMainLooper());
        assertThrows(SecurityException.class,
                () -> requestBackgroundNetwork(testRequest, callback, handler));

        Network testNetwork = null;
        try {
            // Request background test network via Shell identity which has NETWORK_SETTINGS
            // permission granted.
            runWithShellPermissionIdentity(
                    () -> requestBackgroundNetwork(testRequest, callback, handler),
                    new String[] { android.Manifest.permission.NETWORK_SETTINGS });

            // Register the test network agent which has no foreground request associated to it.
            // And verify it can satisfy the background network request just fired.
            final Binder binder = new Binder();
            runWithShellPermissionIdentity(() ->
                    tnm.setupTestNetwork(testNetworkInterface.getInterfaceName(), binder),
                    new String[] { android.Manifest.permission.MANAGE_TEST_NETWORKS,
                            android.Manifest.permission.NETWORK_SETTINGS });
            waitForAvailable(callback);
            testNetwork = callback.getLastAvailableNetwork();
            assertNotNull(testNetwork);

            // The test network that has just connected is a foreground network,
            // non-listen requests will get available callback before it can be put into
            // background if no foreground request can be satisfied. Thus, wait for a short
            // period is needed to let foreground capability go away.
            callback.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                    NETWORK_CALLBACK_TIMEOUT_MS,
                    c -> c instanceof CallbackEntry.CapabilitiesChanged
                            && !((CallbackEntry.CapabilitiesChanged) c).getCaps()
                            .hasCapability(NET_CAPABILITY_FOREGROUND));
            final NetworkCapabilities nc = mCm.getNetworkCapabilities(testNetwork);
            assertFalse("expected background network, but got " + nc,
                    nc.hasCapability(NET_CAPABILITY_FOREGROUND));
        } finally {
            final Network n = testNetwork;
            runWithShellPermissionIdentity(() -> {
                if (null != n) {
                    tnm.teardownTestNetwork(n);
                    callback.eventuallyExpect(CallbackEntry.LOST,
                            NETWORK_CALLBACK_TIMEOUT_MS,
                            lost -> n.equals(lost.getNetwork()));
                }
                testNetworkInterface.getFileDescriptor().close();
            }, new String[] { android.Manifest.permission.MANAGE_TEST_NETWORKS });
        }
    }

    private class DetailedBlockedStatusCallback extends TestableNetworkCallback {
        public void expectAvailableCallbacksWithBlockedReasonNone(Network network) {
            super.expectAvailableCallbacks(network, false /* suspended */, true /* validated */,
                    BLOCKED_REASON_NONE, NETWORK_CALLBACK_TIMEOUT_MS);
        }
        public void eventuallyExpectBlockedStatusCallback(Network network, int blockedStatus) {
            super.eventuallyExpect(CallbackEntry.BLOCKED_STATUS_INT, NETWORK_CALLBACK_TIMEOUT_MS,
                    (it) -> it.getNetwork().equals(network) && it.getReason() == blockedStatus);
        }
        public void onBlockedStatusChanged(Network network, int blockedReasons) {
            Log.v(TAG, "onBlockedStatusChanged " + network + " " + blockedReasons);
            getHistory().add(new CallbackEntry.BlockedStatusInt(network, blockedReasons));
        }
        private void assertNoBlockedStatusCallback() {
            super.assertNoCallback(NO_CALLBACK_TIMEOUT_MS,
                    c -> c instanceof CallbackEntry.BlockedStatus);
        }
    }

    private void setRequireVpnForUids(boolean requireVpn, Collection<Range<Integer>> ranges)
            throws Exception {
        mCmShim.setRequireVpnForUids(requireVpn, ranges);
        for (Range<Integer> range : ranges) {
            if (requireVpn) {
                mVpnRequiredUidRanges.add(range);
            } else {
                assertTrue(mVpnRequiredUidRanges.remove(range));
            }
        }
    }

    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testBlockedStatusCallback() throws Exception {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());
        // The test will need a stable active network that is persistent during the test.
        // Try to connect to a wifi network and wait for it becomes the default network before
        // starting the test to prevent from sudden active network change caused by previous
        // executed tests.
        if (mPackageManager.hasSystemFeature(FEATURE_WIFI)) {
            final Network expectedDefaultNetwork = mCtsNetUtils.ensureWifiConnected();
            mCtsNetUtils.expectNetworkIsSystemDefault(expectedDefaultNetwork);
        }

        final DetailedBlockedStatusCallback myUidCallback = new DetailedBlockedStatusCallback();
        final DetailedBlockedStatusCallback otherUidCallback = new DetailedBlockedStatusCallback();

        final int myUid = Process.myUid();
        final int otherUid = UserHandle.getUid(5, Process.FIRST_APPLICATION_UID);
        final Handler handler = new Handler(Looper.getMainLooper());

        registerDefaultNetworkCallback(myUidCallback, handler);
        runWithShellPermissionIdentity(() -> registerDefaultNetworkCallbackForUid(
                otherUid, otherUidCallback, handler), NETWORK_SETTINGS);

        final Network defaultNetwork = myUidCallback.expect(CallbackEntry.AVAILABLE).getNetwork();
        final List<DetailedBlockedStatusCallback> allCallbacks =
                List.of(myUidCallback, otherUidCallback);
        for (DetailedBlockedStatusCallback callback : allCallbacks) {
            callback.eventuallyExpectBlockedStatusCallback(defaultNetwork, BLOCKED_REASON_NONE);
        }

        final Range<Integer> myUidRange = new Range<>(myUid, myUid);
        final Range<Integer> otherUidRange = new Range<>(otherUid, otherUid);

        runWithShellPermissionIdentity(() -> setRequireVpnForUids(
                true, List.of(myUidRange)), NETWORK_SETTINGS);
        myUidCallback.eventuallyExpectBlockedStatusCallback(defaultNetwork,
                BLOCKED_REASON_LOCKDOWN_VPN);
        otherUidCallback.assertNoBlockedStatusCallback();

        runWithShellPermissionIdentity(() -> setRequireVpnForUids(
                true, List.of(myUidRange, otherUidRange)), NETWORK_SETTINGS);
        myUidCallback.assertNoBlockedStatusCallback();
        otherUidCallback.eventuallyExpectBlockedStatusCallback(defaultNetwork,
                BLOCKED_REASON_LOCKDOWN_VPN);

        // setRequireVpnForUids does no deduplication or refcounting. Removing myUidRange does not
        // unblock myUid because it was added to the blocked ranges twice.
        runWithShellPermissionIdentity(() ->
                setRequireVpnForUids(false, List.of(myUidRange)), NETWORK_SETTINGS);
        myUidCallback.assertNoBlockedStatusCallback();
        otherUidCallback.assertNoBlockedStatusCallback();

        runWithShellPermissionIdentity(() -> setRequireVpnForUids(
                false, List.of(myUidRange, otherUidRange)), NETWORK_SETTINGS);
        myUidCallback.eventuallyExpectBlockedStatusCallback(defaultNetwork, BLOCKED_REASON_NONE);
        otherUidCallback.eventuallyExpectBlockedStatusCallback(defaultNetwork, BLOCKED_REASON_NONE);

        myUidCallback.assertNoBlockedStatusCallback();
        otherUidCallback.assertNoBlockedStatusCallback();
    }

    @Test
    public void testSetVpnDefaultForUids() {
        assumeTrue(TestUtils.shouldTestUApis());
        final String session = UUID.randomUUID().toString();
        assertThrows(NullPointerException.class, () -> mCm.setVpnDefaultForUids(session, null));
        assertThrows(SecurityException.class,
                () -> mCm.setVpnDefaultForUids(session, new ArraySet<>()));
        // For testing the complete behavior of setVpnDefaultForUids(), please refer to
        // HostsideVpnTests.
    }

    private void doTestLegacyLockdownEnabled() throws Exception {
        NetworkInfo info = mCm.getActiveNetworkInfo();
        assertNotNull(info);
        assertEquals(DetailedState.CONNECTED, info.getDetailedState());

        final TestableNetworkCallback callback = new TestableNetworkCallback();
        try {
            mCmShim.setLegacyLockdownVpnEnabled(true);

            // setLegacyLockdownVpnEnabled is asynchronous and only takes effect when the
            // ConnectivityService handler thread processes it. Ensure it has taken effect by doing
            // something that blocks until the handler thread is idle.
            registerDefaultNetworkCallback(callback);
            waitForAvailable(callback);

            // Test one of the effects of setLegacyLockdownVpnEnabled: the fact that any NetworkInfo
            // in state CONNECTED is degraded to CONNECTING if the legacy VPN is not connected.
            info = mCm.getActiveNetworkInfo();
            assertNotNull(info);
            assertEquals(DetailedState.CONNECTING, info.getDetailedState());
        } finally {
            mCmShim.setLegacyLockdownVpnEnabled(false);
        }
    }

    @Test
    public void testLegacyLockdownEnabled() {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());
        runWithShellPermissionIdentity(() -> doTestLegacyLockdownEnabled(), NETWORK_SETTINGS);
    }

    @Test
    public void testGetCapabilityCarrierName() {
        assumeTrue(TestUtils.shouldTestSApis());
        assertEquals("ENTERPRISE", NetworkInformationShimImpl.newInstance()
                .getCapabilityCarrierName(ConstantsShim.NET_CAPABILITY_ENTERPRISE));
        assertNull(NetworkInformationShimImpl.newInstance()
                .getCapabilityCarrierName(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));
    }

    @Test
    public void testSetGlobalProxy() {
        assumeTrue(TestUtils.shouldTestSApis());
        // Behavior is verified in gts. Verify exception thrown w/o permission.
        assertThrows(SecurityException.class, () -> mCm.setGlobalProxy(
                ProxyInfo.buildDirectProxy("example.com" /* host */, 8080 /* port */)));
    }

    @Test
    public void testFactoryResetWithoutPermission() {
        assumeTrue(TestUtils.shouldTestSApis());
        assertThrows(SecurityException.class, () -> mCm.factoryReset());
    }

    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testFactoryReset() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());

        // Store current settings.
        final int curAvoidBadWifi =
                ConnectivitySettingsManager.getNetworkAvoidBadWifi(mContext);
        final int curPrivateDnsMode = ConnectivitySettingsManager.getPrivateDnsMode(mContext);

        final CtsTetheringUtils tetherUtils = new CtsTetheringUtils(mContext);
        final TestTetheringEventCallback tetherEventCallback =
                tetherUtils.registerTetheringEventCallback();
        try {
            tetherEventCallback.assumeWifiTetheringSupported(mContext);
            // To prevent WiFi-to-WiFi interruption while entering APM:
            //  - If WiFi is retained while entering APM, hotspot will also remain enabled.
            //  - If WiFi is off before APM or disabled while entering APM, hotspot will be
            //    disabled.
            //
            // To ensure hotspot always be disabled after enabling APM, disable wifi before
            // enabling the hotspot.
            mCtsNetUtils.disableWifi();

            tetherUtils.startWifiTethering(tetherEventCallback);
            // Update setting to verify the behavior.
            setAirplaneMode(true);
            // Verify softap lost to make sure airplane mode takes effect. This could
            // prevent the race condition between airplane mode enabled and the followed
            // up wifi tethering enabled.
            tetherEventCallback.expectNoTetheringActive();
            tetherUtils.expectSoftApDisabled();

            // start wifi tethering
            tetherUtils.startWifiTethering(tetherEventCallback);

            ConnectivitySettingsManager.setPrivateDnsMode(mContext,
                    ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF);
            ConnectivitySettingsManager.setNetworkAvoidBadWifi(mContext,
                    ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI_IGNORE);
            assertEquals(AIRPLANE_MODE_ON, Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON));
            // Verify factoryReset
            runAsShell(NETWORK_SETTINGS, TETHER_PRIVILEGED, () -> {
                mCm.factoryReset();
                tetherEventCallback.expectNoTetheringActive();
            });
            verifySettings(AIRPLANE_MODE_OFF,
                    ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC,
                    ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI_PROMPT);
        } finally {
            // Restore settings.
            setAirplaneMode(false);
            ConnectivitySettingsManager.setNetworkAvoidBadWifi(mContext, curAvoidBadWifi);
            ConnectivitySettingsManager.setPrivateDnsMode(mContext, curPrivateDnsMode);
            tetherUtils.unregisterTetheringEventCallback(tetherEventCallback);
            tetherUtils.stopAllTethering();
            mCtsNetUtils.ensureWifiConnected();
        }
    }

    private void setAirplaneMode(boolean enable) {
        runAsShell(NETWORK_SETTINGS, () -> mCm.setAirplaneMode(enable));
    }

    /**
     * Verify that {@link ConnectivityManager#setProfileNetworkPreference} cannot be called
     * without required NETWORK_STACK permissions.
     */
    @Test
    public void testSetProfileNetworkPreference_NoPermission() {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());
        assertThrows(SecurityException.class, () -> mCm.setProfileNetworkPreference(
                UserHandle.of(0), PROFILE_NETWORK_PREFERENCE_ENTERPRISE, null /* executor */,
                null /* listener */));
    }

    @Test
    public void testSystemReady() {
        assumeTrue(TestUtils.shouldTestSApis());
        assertThrows(SecurityException.class, () -> mCm.systemReady());
    }

    @Test
    public void testGetIpSecNetIdRange() {
        assumeTrue(TestUtils.shouldTestSApis());
        // The lower refers to ConnectivityManager.TUN_INTF_NETID_START.
        final long lower = 64512;
        // The upper refers to ConnectivityManager.TUN_INTF_NETID_START
        // + ConnectivityManager.TUN_INTF_NETID_RANGE - 1
        final long upper = 65535;
        assertEquals(lower, (long) ConnectivityManager.getIpSecNetIdRange().getLower());
        assertEquals(upper, (long) ConnectivityManager.getIpSecNetIdRange().getUpper());
    }

    private void verifySettings(int expectedAirplaneMode, int expectedPrivateDnsMode,
            int expectedAvoidBadWifi) throws Exception {
        assertEquals(expectedAirplaneMode, Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON));
        assertEquals(expectedPrivateDnsMode,
                ConnectivitySettingsManager.getPrivateDnsMode(mContext));
        assertEquals(expectedAvoidBadWifi,
                ConnectivitySettingsManager.getNetworkAvoidBadWifi(mContext));
    }

    /**
     * Verify that per-app OEM network preference functions as expected for network preference TEST.
     * For specified apps, validate networks are prioritized in order: unmetered, TEST transport,
     * default network.
     */
    @AppModeFull(reason = "Instant apps cannot create test networks")
    @Test
    public void testSetOemNetworkPreferenceForTestPref() throws Exception {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final TestNetworkTracker tnt = callWithShellPermissionIdentity(
                () -> initTestNetwork(mContext, TEST_LINKADDR, NETWORK_CALLBACK_TIMEOUT_MS));
        final TestableNetworkCallback defaultCallback = new TestableNetworkCallback();
        final TestableNetworkCallback systemDefaultCallback = new TestableNetworkCallback();

        final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
        final NetworkCapabilities wifiNetworkCapabilities = callWithShellPermissionIdentity(
                () -> mCm.getNetworkCapabilities(wifiNetwork));
        final String ssid = unquoteSSID(wifiNetworkCapabilities.getSsid());
        final boolean oldMeteredValue = wifiNetworkCapabilities.isMetered();

        testAndCleanup(() -> {
            // This network will be used for unmetered. Wait for it to be validated because
            // OEM_NETWORK_PREFERENCE_TEST only prefers NOT_METERED&VALIDATED to a network with
            // TRANSPORT_TEST, like OEM_NETWORK_PREFERENCE_OEM_PAID.
            setWifiMeteredStatusAndWait(ssid, false /* isMetered */, true /* waitForValidation */);

            setOemNetworkPreferenceForMyPackage(OemNetworkPreferences.OEM_NETWORK_PREFERENCE_TEST);
            registerTestOemNetworkPreferenceCallbacks(defaultCallback, systemDefaultCallback);

            // Validate that an unmetered network is used over other networks.
            waitForAvailable(defaultCallback, wifiNetwork);
            systemDefaultCallback.eventuallyExpect(CallbackEntry.AVAILABLE,
                    NETWORK_CALLBACK_TIMEOUT_MS, cb -> wifiNetwork.equals(cb.getNetwork()));

            // Validate that when setting unmetered to metered, unmetered is lost and replaced by
            // the network with the TEST transport. Also wait for validation here, in case there
            // is a bug that's only visible when the network is validated.
            setWifiMeteredStatusAndWait(ssid, true /* isMetered */, true /* waitForValidation */);
            defaultCallback.eventuallyExpect(CallbackEntry.LOST, NETWORK_CALLBACK_TIMEOUT_MS,
                    l -> l.getNetwork().equals(wifiNetwork));
            waitForAvailable(defaultCallback, tnt.getNetwork());
            // Depending on if this device has cellular connectivity or not, multiple available
            // callbacks may be received. Eventually, metered Wi-Fi should be the final available
            // callback in any case therefore confirm its receipt before continuing to assure the
            // system is in the expected state.
            waitForTransport(systemDefaultCallback, TRANSPORT_WIFI);
        }, /* cleanup */ () -> {
                // Validate that removing the test network will fallback to the default network.
                runWithShellPermissionIdentity(tnt::teardown);
                // The other callbacks (LP or NC changes) would receive before LOST callback. Use
                // eventuallyExpect to check callback for avoiding test flake.
                defaultCallback.eventuallyExpect(CallbackEntry.LOST, NETWORK_CALLBACK_TIMEOUT_MS,
                        lost -> tnt.getNetwork().equals(lost.getNetwork()));
                waitForAvailable(defaultCallback);
            }, /* cleanup */ () -> {
                setWifiMeteredStatusAndWait(ssid, oldMeteredValue, false /* waitForValidation */);
            }, /* cleanup */ () -> {
                // Cleanup any prior test state from setOemNetworkPreference
                clearOemNetworkPreference();
            });
    }

    /**
     * Verify that per-app OEM network preference functions as expected for network pref TEST_ONLY.
     * For specified apps, validate that only TEST transport type networks are used.
     */
    @AppModeFull(reason = "Instant apps cannot create test networks")
    @Test
    public void testSetOemNetworkPreferenceForTestOnlyPref() throws Exception {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final TestNetworkTracker tnt = callWithShellPermissionIdentity(
                () -> initTestNetwork(mContext, TEST_LINKADDR, NETWORK_CALLBACK_TIMEOUT_MS));
        final TestableNetworkCallback defaultCallback = new TestableNetworkCallback();
        final TestableNetworkCallback systemDefaultCallback = new TestableNetworkCallback();

        final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
        final Network testNetwork = tnt.getNetwork();

        testAndCleanup(() -> {
            setOemNetworkPreferenceForMyPackage(
                    OemNetworkPreferences.OEM_NETWORK_PREFERENCE_TEST_ONLY);
            registerTestOemNetworkPreferenceCallbacks(defaultCallback, systemDefaultCallback);
            waitForAvailable(defaultCallback, testNetwork);
            systemDefaultCallback.eventuallyExpect(CallbackEntry.AVAILABLE,
                    NETWORK_CALLBACK_TIMEOUT_MS, cb -> wifiNetwork.equals(cb.getNetwork()));
        }, /* cleanup */ () -> {
                runWithShellPermissionIdentity(tnt::teardown);
                defaultCallback.eventuallyExpect(CallbackEntry.LOST, NETWORK_CALLBACK_TIMEOUT_MS,
                        cb -> testNetwork.equals(cb.getNetwork()));

                // This network preference should only ever use the test network therefore available
                // should not trigger when the test network goes down (e.g. switch to cellular).
                defaultCallback.assertNoCallback();
                // The system default should still be connected to Wi-fi
                assertEquals(wifiNetwork, systemDefaultCallback.getLastAvailableNetwork());
            }, /* cleanup */ () -> {
                // Cleanup any prior test state from setOemNetworkPreference
                clearOemNetworkPreference();

                // The default (non-test) network should be available as the network pref was
                // cleared.
                waitForAvailable(defaultCallback);
            });
    }

    private void registerTestOemNetworkPreferenceCallbacks(
            @NonNull final TestableNetworkCallback defaultCallback,
            @NonNull final TestableNetworkCallback systemDefaultCallback) {
        registerDefaultNetworkCallback(defaultCallback);
        runWithShellPermissionIdentity(() ->
                registerSystemDefaultNetworkCallback(systemDefaultCallback,
                        new Handler(Looper.getMainLooper())), NETWORK_SETTINGS);
    }

    private static final class OnCompleteListenerCallback {
        final CompletableFuture<Object> mDone = new CompletableFuture<>();

        public void onComplete() {
            mDone.complete(new Object());
        }

        void expectOnComplete() throws Exception {
            try {
                mDone.get(NETWORK_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                fail("Expected onComplete() not received after "
                        + NETWORK_CALLBACK_TIMEOUT_MS + " ms");
            }
        }
    }

    private void setOemNetworkPreferenceForMyPackage(final int networkPref) throws Exception {
        final OemNetworkPreferences pref = new OemNetworkPreferences.Builder()
                .addNetworkPreference(mContext.getPackageName(), networkPref)
                .build();
        final OnCompleteListenerCallback oemPrefListener = new OnCompleteListenerCallback();
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            mCm.setOemNetworkPreference(
                    pref, mContext.getMainExecutor(), oemPrefListener::onComplete);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
        oemPrefListener.expectOnComplete();
    }

    /**
     * This will clear the OEM network preference on the device. As there is currently no way of
     * getting the existing preference, if this is executed while an existing preference is in
     * place, that preference will need to be reapplied after executing this test.
     * @throws Exception
     */
    private void clearOemNetworkPreference() throws Exception {
        final OemNetworkPreferences clearPref = new OemNetworkPreferences.Builder().build();
        final OnCompleteListenerCallback oemPrefListener = new OnCompleteListenerCallback();
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            mCm.setOemNetworkPreference(
                    clearPref, mContext.getMainExecutor(), oemPrefListener::onComplete);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
        oemPrefListener.expectOnComplete();
    }

    @Test
    public void testSetAcceptPartialConnectivity_NoPermission_GetException() {
        assumeTrue(TestUtils.shouldTestSApis());
        assertThrows(SecurityException.class, () -> mCm.setAcceptPartialConnectivity(
                mCm.getActiveNetwork(), false /* accept */ , false /* always */));
    }

    private void ensureCellIsValidatedBeforeMockingValidationUrls() {
        // Verify that current supported network is validated so that the mock http server will not
        // apply to unexpected networks. Also see aosp/2208680.
        //
        // This may also apply to wifi in principle, but in practice methods that mock validation
        // URL all disconnect wifi forcefully anyway, so don't wait for wifi to validate.
        if (mPackageManager.hasSystemFeature(FEATURE_TELEPHONY)) {
            ensureValidatedNetwork(makeCellNetworkRequest());
        }
    }

    private void ensureValidatedNetwork(NetworkRequest request) {
        final TestableNetworkCallback cb = new TestableNetworkCallback();
        mCm.registerNetworkCallback(request, cb);
        cb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                NETWORK_CALLBACK_TIMEOUT_MS,
                entry -> ((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                        .hasCapability(NET_CAPABILITY_VALIDATED));
        mCm.unregisterNetworkCallback(cb);
    }

    @AppModeFull(reason = "WRITE_DEVICE_CONFIG permission can't be granted to instant apps")
    @Test
    public void testAcceptPartialConnectivity_validatedNetwork() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());
        assumeTrue("testAcceptPartialConnectivity_validatedNetwork cannot execute"
                        + " unless device supports WiFi",
                mPackageManager.hasSystemFeature(FEATURE_WIFI));

        try {
            // Wait for partial connectivity to be detected on the network
            final Network network = preparePartialConnectivity();

            runAsShell(NETWORK_SETTINGS, () -> {
                // The always bit is verified in NetworkAgentTest
                mCm.setAcceptPartialConnectivity(network, true /* accept */, false /* always */);
            });

            // Accept partial connectivity network should result in a validated network
            expectNetworkHasCapability(network, NET_CAPABILITY_VALIDATED, WIFI_CONNECT_TIMEOUT_MS);
        } finally {
            mHttpServer.stop();
            mTestValidationConfigRule.runAfterNextCleanup(this::reconnectWifi);
        }
    }

    @AppModeFull(reason = "WRITE_DEVICE_CONFIG permission can't be granted to instant apps")
    @Test
    public void testRejectPartialConnectivity_TearDownNetwork() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());
        assumeTrue("testAcceptPartialConnectivity_validatedNetwork cannot execute"
                        + " unless device supports WiFi",
                mPackageManager.hasSystemFeature(FEATURE_WIFI));

        final TestNetworkCallback cb = new TestNetworkCallback();
        try {
            // Wait for partial connectivity to be detected on the network
            final Network network = preparePartialConnectivity();

            requestNetwork(makeWifiNetworkRequest(), cb);
            runAsShell(NETWORK_SETTINGS, () -> {
                // The always bit is verified in NetworkAgentTest
                mCm.setAcceptPartialConnectivity(network, false /* accept */, false /* always */);
            });
            // Reject partial connectivity network should cause the network being torn down
            assertEquals(network, cb.waitForLost());
        } finally {
            mHttpServer.stop();
            // Wifi will not automatically reconnect to the network. ensureWifiDisconnected cannot
            // apply here. Thus, turn off wifi first and restart to restore.
            mTestValidationConfigRule.runAfterNextCleanup(() -> {
                mCtsNetUtils.disableWifi();
                mCtsNetUtils.ensureWifiConnected();
            });
        }
    }

    @Test
    public void testSetAcceptUnvalidated_NoPermission_GetException() {
        assumeTrue(TestUtils.shouldTestSApis());
        assertThrows(SecurityException.class, () -> mCm.setAcceptUnvalidated(
                mCm.getActiveNetwork(), false /* accept */ , false /* always */));
    }

    @AppModeFull(reason = "WRITE_DEVICE_CONFIG permission can't be granted to instant apps")
    @Test
    public void testRejectUnvalidated_TearDownNetwork() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());
        final boolean canRunTest = mPackageManager.hasSystemFeature(FEATURE_WIFI)
                && mPackageManager.hasSystemFeature(FEATURE_TELEPHONY);
        assumeTrue("testAcceptPartialConnectivity_validatedNetwork cannot execute"
                        + " unless device supports WiFi and telephony", canRunTest);

        final TestableNetworkCallback wifiCb = new TestableNetworkCallback();
        try {
            // Ensure at least one default network candidate connected.
            mCtsNetUtils.connectToCell();

            final Network wifiNetwork = prepareUnvalidatedNetwork();
            // Default network should not be wifi ,but checking that wifi is not the default doesn't
            // guarantee that it won't become the default in the future.
            assertNotEquals(wifiNetwork, mCm.getActiveNetwork());

            registerNetworkCallback(makeWifiNetworkRequest(), wifiCb);
            runAsShell(NETWORK_SETTINGS, () -> {
                mCm.setAcceptUnvalidated(wifiNetwork, false /* accept */, false /* always */);
            });
            waitForLost(wifiCb);
        } finally {
            mHttpServer.stop();
            /// Wifi will not automatically reconnect to the network. ensureWifiDisconnected cannot
            // apply here. Thus, turn off wifi first and restart to restore.
            mTestValidationConfigRule.runAfterNextCleanup(() -> {
                mCtsNetUtils.disableWifi();
                mCtsNetUtils.ensureWifiConnected();
            });
        }
    }

    @AppModeFull(reason = "WRITE_DEVICE_CONFIG permission can't be granted to instant apps")
    @Test
    public void testSetAvoidUnvalidated() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());
        // TODO: Allow in debuggable ROM only. To be replaced by FabricatedOverlay
        assumeTrue(Build.isDebuggable());
        final boolean canRunTest = mPackageManager.hasSystemFeature(FEATURE_WIFI)
                && mPackageManager.hasSystemFeature(FEATURE_TELEPHONY);
        assumeTrue("testSetAvoidUnvalidated cannot execute"
                + " unless device supports WiFi and telephony", canRunTest);

        final TestableNetworkCallback wifiCb = new TestableNetworkCallback();
        final TestableNetworkCallback defaultCb = new TestableNetworkCallback();
        final int previousAvoidBadWifi =
                ConnectivitySettingsManager.getNetworkAvoidBadWifi(mContext);

        allowBadWifi();

        try {
            final Network cellNetwork = mCtsNetUtils.connectToCell();
            final Network wifiNetwork = prepareValidatedNetwork();

            registerDefaultNetworkCallback(defaultCb);
            registerNetworkCallback(makeWifiNetworkRequest(), wifiCb);

            // Verify wifi is the default network.
            defaultCb.eventuallyExpect(CallbackEntry.AVAILABLE, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> wifiNetwork.equals(entry.getNetwork()));
            wifiCb.eventuallyExpect(CallbackEntry.AVAILABLE, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> wifiNetwork.equals(entry.getNetwork()));
            assertTrue(mCm.getNetworkCapabilities(wifiNetwork).hasCapability(
                    NET_CAPABILITY_VALIDATED));

            // The cell network has already been checked to be validated.
            // Configure response code for unvalidated network.
            configTestServer(Status.INTERNAL_ERROR, Status.INTERNAL_ERROR);
            mCm.reportNetworkConnectivity(wifiNetwork, false);
            // Default network should stay on unvalidated wifi because avoid bad wifi is disabled.
            defaultCb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                    NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> !((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                            .hasCapability(NET_CAPABILITY_VALIDATED));
            wifiCb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                    NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> !((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                            .hasCapability(NET_CAPABILITY_VALIDATED));

            runAsShell(NETWORK_SETTINGS, () -> {
                mCm.setAvoidUnvalidated(wifiNetwork);
            });
            // Default network should be updated to validated cellular network.
            defaultCb.eventuallyExpect(CallbackEntry.AVAILABLE, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> cellNetwork.equals(entry.getNetwork()));
            // The network should not validate again.
            wifiCb.assertNoCallback(NO_CALLBACK_TIMEOUT_MS, c -> isValidatedCaps(c));
        } finally {
            resetAvoidBadWifi(previousAvoidBadWifi);
            mHttpServer.stop();
            mTestValidationConfigRule.runAfterNextCleanup(this::reconnectWifi);
        }
    }

    private boolean isValidatedCaps(CallbackEntry c) {
        if (!(c instanceof CallbackEntry.CapabilitiesChanged)) return false;
        final CallbackEntry.CapabilitiesChanged capsChanged = (CallbackEntry.CapabilitiesChanged) c;
        return capsChanged.getCaps().hasCapability(NET_CAPABILITY_VALIDATED);
    }

    private void resetAvoidBadWifi(int settingValue) {
        setTestAllowBadWifiResource(0 /* timeMs */);
        ConnectivitySettingsManager.setNetworkAvoidBadWifi(mContext, settingValue);
    }

    private void allowBadWifi() {
        setTestAllowBadWifiResource(
                System.currentTimeMillis() + WIFI_CONNECT_TIMEOUT_MS /* timeMs */);
        ConnectivitySettingsManager.setNetworkAvoidBadWifi(mContext,
                ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI_IGNORE);
    }

    private void setTestAllowBadWifiResource(long timeMs) {
        runAsShell(NETWORK_SETTINGS, () -> {
            mCm.setTestAllowBadWifiUntil(timeMs);
        });
    }

    private Network expectNetworkHasCapability(Network network, int expectedNetCap, long timeout)
            throws Exception {
        final CompletableFuture<Network> future = new CompletableFuture();
        final NetworkCallback cb = new NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) {
                if (n.equals(network) && nc.hasCapability(expectedNetCap)) {
                    future.complete(network);
                }
            }
        };

        registerNetworkCallback(new NetworkRequest.Builder().build(), cb);
        return future.get(timeout, TimeUnit.MILLISECONDS);
    }

    private void prepareHttpServer() throws Exception {
        runAsShell(READ_DEVICE_CONFIG, () -> {
            // Verify that the test URLs are not normally set on the device, but do not fail if the
            // test URLs are set to what this test uses (URLs on localhost), in case the test was
            // interrupted manually and rerun.
            assertEmptyOrLocalhostUrl(TEST_CAPTIVE_PORTAL_HTTPS_URL);
            assertEmptyOrLocalhostUrl(TEST_CAPTIVE_PORTAL_HTTP_URL);
        });

        NetworkValidationTestUtil.clearValidationTestUrlsDeviceConfig();

        mHttpServer.start();
    }

    private Network reconnectWifi() {
        mCtsNetUtils.ensureWifiDisconnected(null /* wifiNetworkToCheck */);
        return mCtsNetUtils.ensureWifiConnected();
    }

    private Network prepareValidatedNetwork() throws Exception {
        ensureCellIsValidatedBeforeMockingValidationUrls();

        prepareHttpServer();
        configTestServer(Status.NO_CONTENT, Status.NO_CONTENT);
        // Disconnect wifi first then start wifi network with configuration.
        final Network wifiNetwork = reconnectWifi();

        return expectNetworkHasCapability(wifiNetwork, NET_CAPABILITY_VALIDATED,
                WIFI_CONNECT_TIMEOUT_MS);
    }

    private Network preparePartialConnectivity() throws Exception {
        ensureCellIsValidatedBeforeMockingValidationUrls();

        prepareHttpServer();
        // Configure response code for partial connectivity
        configTestServer(Status.INTERNAL_ERROR  /* httpsStatusCode */,
                Status.NO_CONTENT  /* httpStatusCode */);
        // Disconnect wifi first then start wifi network with configuration.
        mCtsNetUtils.ensureWifiDisconnected(null /* wifiNetworkToCheck */);
        final Network network = mCtsNetUtils.ensureWifiConnected();

        return expectNetworkHasCapability(network, NET_CAPABILITY_PARTIAL_CONNECTIVITY,
                WIFI_CONNECT_TIMEOUT_MS);
    }

    private Network prepareUnvalidatedNetwork() throws Exception {
        ensureCellIsValidatedBeforeMockingValidationUrls();

        prepareHttpServer();
        // Configure response code for unvalidated network
        configTestServer(Status.INTERNAL_ERROR /* httpsStatusCode */,
                Status.INTERNAL_ERROR /* httpStatusCode */);

        // Disconnect wifi first then start wifi network with configuration.
        mCtsNetUtils.ensureWifiDisconnected(null /* wifiNetworkToCheck */);
        final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
        return expectNetworkHasCapability(wifiNetwork, NET_CAPABILITY_INTERNET,
                WIFI_CONNECT_TIMEOUT_MS);
    }

    private String makeUrl(String path) {
        return "http://localhost:" + mHttpServer.getListeningPort() + path;
    }

    private void assertEmptyOrLocalhostUrl(String urlKey) {
        final String url = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_CONNECTIVITY, urlKey);
        assertTrue(urlKey + " must not be set in production scenarios, current value= " + url,
                TextUtils.isEmpty(url) || LOCALHOST_HOSTNAME.equals(Uri.parse(url).getHost()));
    }

    private void configTestServer(IStatus httpsStatusCode, IStatus httpStatusCode) {
        mHttpServer.addResponse(new TestHttpServer.Request(
                TEST_HTTPS_URL_PATH, Method.GET, "" /* queryParameters */),
                httpsStatusCode, null /* locationHeader */, "" /* content */);
        mHttpServer.addResponse(new TestHttpServer.Request(
                TEST_HTTP_URL_PATH, Method.GET, "" /* queryParameters */),
                httpStatusCode, null /* locationHeader */, "" /* content */);
        NetworkValidationTestUtil.setHttpsUrlDeviceConfig(mTestValidationConfigRule,
                makeUrl(TEST_HTTPS_URL_PATH));
        NetworkValidationTestUtil.setHttpUrlDeviceConfig(mTestValidationConfigRule,
                makeUrl(TEST_HTTP_URL_PATH));
        NetworkValidationTestUtil.setUrlExpirationDeviceConfig(mTestValidationConfigRule,
                System.currentTimeMillis() + WIFI_CONNECT_TIMEOUT_MS);
    }

    @AppModeFull(reason = "Need WiFi support to test the default active network")
    // NetworkActivityTracker is not mainlined before S.
    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    public void testDefaultNetworkActiveListener() throws Exception {
        final boolean supportWifi = mPackageManager.hasSystemFeature(FEATURE_WIFI);
        final boolean supportTelephony = mPackageManager.hasSystemFeature(FEATURE_TELEPHONY);
        assumeTrue("testDefaultNetworkActiveListener cannot execute"
                + " unless device supports WiFi or telephony", (supportWifi || supportTelephony));

        if (supportWifi) {
            mCtsNetUtils.ensureWifiDisconnected(null /* wifiNetworkToCheck */);
        } else {
            mCtsNetUtils.disconnectFromCell();
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final ConnectivityManager.OnNetworkActiveListener listener = () -> future.complete(true);
        mCm.addDefaultNetworkActiveListener(listener);
        testAndCleanup(() -> {
            // New default network connected will trigger a network activity notification.
            if (supportWifi) {
                mCtsNetUtils.ensureWifiConnected();
            } else {
                mCtsNetUtils.connectToCell();
            }
            assertTrue(future.get(LISTEN_ACTIVITY_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }, () -> {
                mCm.removeDefaultNetworkActiveListener(listener);
            });
    }

    /**
     *  The networks used in this test are real networks and as such they can see seemingly random
     *  updates of their capabilities or link properties as conditions change, e.g. the network
     *  loses validation or IPv4 shows up. Many tests should simply treat these callbacks as
     *  spurious.
     */
    private void assertNoCallbackExceptCapOrLpChange(
            @NonNull final TestableNetworkCallback cb) {
        cb.assertNoCallback(NO_CALLBACK_TIMEOUT_MS,
                c -> !(c instanceof CallbackEntry.CapabilitiesChanged
                        || c instanceof CallbackEntry.LinkPropertiesChanged));
    }

    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testMobileDataPreferredUids() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());
        final boolean canRunTest = mPackageManager.hasSystemFeature(FEATURE_WIFI)
                && mPackageManager.hasSystemFeature(FEATURE_TELEPHONY);
        assumeTrue("testMobileDataPreferredUidsWithCallback cannot execute"
                + " unless device supports both WiFi and telephony", canRunTest);

        final int uid = mPackageManager.getPackageUid(mContext.getPackageName(), 0 /* flag */);
        final Set<Integer> mobileDataPreferredUids =
                ConnectivitySettingsManager.getMobileDataPreferredUids(mContext);
        // CtsNetTestCases uid should not list in MOBILE_DATA_PREFERRED_UIDS setting because it just
        // installs to device. In case the uid is existed in setting mistakenly, try to remove the
        // uid and set correct uids to setting.
        mobileDataPreferredUids.remove(uid);
        ConnectivitySettingsManager.setMobileDataPreferredUids(mContext, mobileDataPreferredUids);

        // For testing mobile data preferred uids feature, it needs both wifi and cell network.
        final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
        final Network cellNetwork = mCtsNetUtils.connectToCell();
        final TestableNetworkCallback defaultTrackingCb = new TestableNetworkCallback();
        final TestableNetworkCallback systemDefaultCb = new TestableNetworkCallback();
        final Handler h = new Handler(Looper.getMainLooper());
        runWithShellPermissionIdentity(() -> registerSystemDefaultNetworkCallback(
                systemDefaultCb, h), NETWORK_SETTINGS);
        registerDefaultNetworkCallback(defaultTrackingCb);

        try {
            // CtsNetTestCases uid is not listed in MOBILE_DATA_PREFERRED_UIDS setting, so the
            // per-app default network should be same as system default network.
            waitForAvailable(systemDefaultCb, wifiNetwork);
            waitForAvailable(defaultTrackingCb, wifiNetwork);
            // Active network for CtsNetTestCases uid should be wifi now.
            assertEquals(wifiNetwork, mCm.getActiveNetwork());

            // Add CtsNetTestCases uid to MOBILE_DATA_PREFERRED_UIDS setting, then available per-app
            // default network callback should be received with cell network.
            final Set<Integer> newMobileDataPreferredUids = new ArraySet<>(mobileDataPreferredUids);
            newMobileDataPreferredUids.add(uid);
            ConnectivitySettingsManager.setMobileDataPreferredUids(
                    mContext, newMobileDataPreferredUids);
            defaultTrackingCb.eventuallyExpect(CallbackEntry.AVAILABLE, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> cellNetwork.equals(entry.getNetwork()));
            // No change for system default network. Expect no callback except CapabilitiesChanged
            // or LinkPropertiesChanged which may be triggered randomly from wifi network.
            assertNoCallbackExceptCapOrLpChange(systemDefaultCb);
            // Active network for CtsNetTestCases uid should change to cell, too.
            assertEquals(cellNetwork, mCm.getActiveNetwork());

            // Remove CtsNetTestCases uid from MOBILE_DATA_PREFERRED_UIDS setting, then available
            // per-app default network callback should be received again with system default network
            newMobileDataPreferredUids.remove(uid);
            ConnectivitySettingsManager.setMobileDataPreferredUids(
                    mContext, newMobileDataPreferredUids);
            defaultTrackingCb.eventuallyExpect(CallbackEntry.AVAILABLE, NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> wifiNetwork.equals(entry.getNetwork()));
            // No change for system default network. Expect no callback except CapabilitiesChanged
            // or LinkPropertiesChanged which may be triggered randomly from wifi network.
            assertNoCallbackExceptCapOrLpChange(systemDefaultCb);
            // Active network for CtsNetTestCases uid should change back to wifi.
            assertEquals(wifiNetwork, mCm.getActiveNetwork());
        } finally {
            // Restore setting.
            ConnectivitySettingsManager.setMobileDataPreferredUids(
                    mContext, mobileDataPreferredUids);
        }
    }

    private void assertBindSocketToNetworkSuccess(final Network network) throws Exception {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                for (int i = 0; i < 300; i++) {
                    SystemClock.sleep(10);

                    try (Socket socket = new Socket()) {
                        network.bindSocket(socket);
                        future.complete(true);
                        return;
                    } catch (IOException e) { }
                }
            });
            assertTrue(future.get(APPLYING_UIDS_ALLOWED_ON_RESTRICTED_NETWORKS_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS));
        } finally {
            executor.shutdown();
        }
    }

    private static NetworkAgent createRestrictedNetworkAgent(final Context context) {
        // Create test network agent with restricted network.
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(
                        TEST_RESTRICTED_NW_IFACE_NAME))
                .build();
        final NetworkAgent agent = new NetworkAgent(context, Looper.getMainLooper(), TAG, nc,
                new LinkProperties(), 10 /* score */, new NetworkAgentConfig.Builder().build(),
                new NetworkProvider(context, Looper.getMainLooper(), TAG)) {};
        runWithShellPermissionIdentity(() -> agent.register(),
                android.Manifest.permission.MANAGE_TEST_NETWORKS);
        agent.markConnected();

        return agent;
    }

    @AppModeFull(reason = "WRITE_SECURE_SETTINGS permission can't be granted to instant apps")
    @Test
    public void testUidsAllowedOnRestrictedNetworks() throws Exception {
        assumeTestSApis();

        // TODO (b/175199465): figure out a reasonable permission check for
        //  setUidsAllowedOnRestrictedNetworks that allows tests but not system-external callers.
        assumeTrue(Build.isDebuggable());

        final int uid = mPackageManager.getPackageUid(mContext.getPackageName(), 0 /* flag */);
        final Set<Integer> originalUidsAllowedOnRestrictedNetworks =
                ConnectivitySettingsManager.getUidsAllowedOnRestrictedNetworks(mContext);
        // CtsNetTestCases uid should not list in UIDS_ALLOWED_ON_RESTRICTED_NETWORKS setting
        // because it has been just installed to device. In case the uid is existed in setting
        // mistakenly, try to remove the uid and set correct uids to setting.
        originalUidsAllowedOnRestrictedNetworks.remove(uid);
        runWithShellPermissionIdentity(() -> setUidsAllowedOnRestrictedNetworks(
                mContext, originalUidsAllowedOnRestrictedNetworks), NETWORK_SETTINGS);

        // File a restricted network request with permission first to hold the connection.
        final TestableNetworkCallback testNetworkCb = new TestableNetworkCallback();
        final NetworkRequest testRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(
                        TEST_RESTRICTED_NW_IFACE_NAME))
                .build();
        runWithShellPermissionIdentity(() -> requestNetwork(testRequest, testNetworkCb),
                CONNECTIVITY_USE_RESTRICTED_NETWORKS);

        // File another restricted network request without permission.
        final TestableNetworkCallback restrictedNetworkCb = new TestableNetworkCallback();
        final NetworkRequest restrictedRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(
                        TEST_RESTRICTED_NW_IFACE_NAME))
                .build();
        // Uid is not in allowed list and no permissions. Expect that SecurityException will throw.
        assertThrows(SecurityException.class,
                () -> mCm.requestNetwork(restrictedRequest, restrictedNetworkCb));

        final NetworkAgent agent = createRestrictedNetworkAgent(mContext);
        final Network network = agent.getNetwork();

        try (Socket socket = new Socket()) {
            // Verify that the network is restricted.
            testNetworkCb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                    NETWORK_CALLBACK_TIMEOUT_MS,
                    entry -> network.equals(entry.getNetwork())
                            && (!((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                            .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)));
            // CtsNetTestCases package doesn't hold CONNECTIVITY_USE_RESTRICTED_NETWORKS, so it
            // does not allow to bind socket to restricted network.
            assertThrows(IOException.class, () -> network.bindSocket(socket));

            // Add CtsNetTestCases uid to UIDS_ALLOWED_ON_RESTRICTED_NETWORKS setting, then it can
            // bind socket to restricted network normally.
            final Set<Integer> newUidsAllowedOnRestrictedNetworks =
                    new ArraySet<>(originalUidsAllowedOnRestrictedNetworks);
            newUidsAllowedOnRestrictedNetworks.add(uid);
            runWithShellPermissionIdentity(() -> setUidsAllowedOnRestrictedNetworks(
                    mContext, newUidsAllowedOnRestrictedNetworks), NETWORK_SETTINGS);
            // Wait a while for sending allowed uids on the restricted network to netd.
            // TODD: Have a significant signal to know the uids has been sent to netd.
            assertBindSocketToNetworkSuccess(network);

            if (TestUtils.shouldTestTApis()) {
                // Uid is in allowed list. Try file network request again.
                requestNetwork(restrictedRequest, restrictedNetworkCb);
                // Verify that the network is restricted.
                restrictedNetworkCb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED,
                        NETWORK_CALLBACK_TIMEOUT_MS,
                        entry -> network.equals(entry.getNetwork())
                                && (!((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)));
            }
        } finally {
            agent.unregister();

            // Restore setting.
            runWithShellPermissionIdentity(() -> setUidsAllowedOnRestrictedNetworks(
                    mContext, originalUidsAllowedOnRestrictedNetworks), NETWORK_SETTINGS);
        }
    }

    @Test
    public void testDump() throws Exception {
        final String dumpOutput = DumpTestUtils.dumpServiceWithShellPermission(
                Context.CONNECTIVITY_SERVICE, "--short");
        assertTrue(dumpOutput, dumpOutput.contains("Active default network"));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testDumpBpfNetMaps() throws Exception {
        final String[] args = new String[] {"--short", "trafficcontroller"};
        String dumpOutput = DumpTestUtils.dumpServiceWithShellPermission(
                Context.CONNECTIVITY_SERVICE, args);
        assertTrue(dumpOutput, dumpOutput.contains("TrafficController"));
        assertFalse(dumpOutput, dumpOutput.contains("BPF map content"));

        dumpOutput = DumpTestUtils.dumpServiceWithShellPermission(
                Context.CONNECTIVITY_SERVICE, args[1]);
        assertTrue(dumpOutput, dumpOutput.contains("BPF map content"));
    }

    private void checkFirewallBlocking(final DatagramSocket srcSock, final DatagramSocket dstSock,
            final boolean expectBlock, final int chain) throws Exception {
        final int uid = Process.myUid();
        final Random random = new Random();
        final byte[] sendData = new byte[100];
        random.nextBytes(sendData);

        final DatagramPacket pkt = new DatagramPacket(sendData, sendData.length,
                InetAddresses.parseNumericAddress("::1"), dstSock.getLocalPort());
        try {
            srcSock.send(pkt);
        } catch (IOException e) {
            if (expectBlock) {
                return;
            }
            fail("Expect not to be blocked by firewall but sending packet was blocked:"
                    + " chain=" + chain
                    + " chainEnabled=" + mCm.getFirewallChainEnabled(chain)
                    + " uid=" + uid
                    + " uidFirewallRule=" + mCm.getUidFirewallRule(chain, uid));
        }

        dstSock.receive(pkt);
        assertArrayEquals(sendData, pkt.getData());

        if (expectBlock) {
            fail("Expect to be blocked by firewall but sending packet was not blocked:"
                    + " chain=" + chain
                    + " chainEnabled=" + mCm.getFirewallChainEnabled(chain)
                    + " uid=" + uid
                    + " uidFirewallRule=" + mCm.getUidFirewallRule(chain, uid));
        }
    }

    private static final boolean EXPECT_PASS = false;
    private static final boolean EXPECT_BLOCK = true;

    // ALLOWLIST means the firewall denies all by default, uids must be explicitly allowed
    // DENYLIST means the firewall allows all by default, uids must be explicitly denyed
    private static final boolean ALLOWLIST = true;
    private static final boolean DENYLIST = false;

    private void doTestFirewallBlocking(final int chain, final boolean isAllowList) {
        final int myUid = Process.myUid();
        final int ruleToAddMatch = isAllowList ? FIREWALL_RULE_ALLOW : FIREWALL_RULE_DENY;
        final int ruleToRemoveMatch = isAllowList ? FIREWALL_RULE_DENY : FIREWALL_RULE_ALLOW;

        runWithShellPermissionIdentity(() -> {
            // Firewall chain status will be restored after the test.
            final boolean wasChainEnabled = mCm.getFirewallChainEnabled(chain);
            final int previousUidFirewallRule = mCm.getUidFirewallRule(chain, myUid);
            final DatagramSocket srcSock = new DatagramSocket();
            final DatagramSocket dstSock = new DatagramSocket();
            testAndCleanup(() -> {
                if (wasChainEnabled) {
                    mCm.setFirewallChainEnabled(chain, false /* enable */);
                }
                if (previousUidFirewallRule == ruleToAddMatch) {
                    mCm.setUidFirewallRule(chain, myUid, ruleToRemoveMatch);
                }
                dstSock.setSoTimeout(SOCKET_TIMEOUT_MS);

                // Chain disabled, UID not on chain.
                checkFirewallBlocking(srcSock, dstSock, EXPECT_PASS, chain);

                // Chain enabled, UID not on chain.
                mCm.setFirewallChainEnabled(chain, true /* enable */);
                assertTrue(mCm.getFirewallChainEnabled(chain));
                checkFirewallBlocking(
                        srcSock, dstSock, isAllowList ? EXPECT_BLOCK : EXPECT_PASS, chain);

                // Chain enabled, UID on chain.
                mCm.setUidFirewallRule(chain, myUid, ruleToAddMatch);
                checkFirewallBlocking(
                        srcSock, dstSock, isAllowList ?  EXPECT_PASS : EXPECT_BLOCK, chain);

                // Chain disabled, UID on chain.
                mCm.setFirewallChainEnabled(chain, false /* enable */);
                assertFalse(mCm.getFirewallChainEnabled(chain));
                checkFirewallBlocking(srcSock, dstSock, EXPECT_PASS, chain);

                // Chain disabled, UID not on chain.
                mCm.setUidFirewallRule(chain, myUid, ruleToRemoveMatch);
                checkFirewallBlocking(srcSock, dstSock, EXPECT_PASS, chain);
            }, /* cleanup */ () -> {
                    srcSock.close();
                    dstSock.close();
                }, /* cleanup */ () -> {
                    // Restore the global chain status
                    mCm.setFirewallChainEnabled(chain, wasChainEnabled);
                }, /* cleanup */ () -> {
                    // Restore the uid firewall rule status
                    try {
                        mCm.setUidFirewallRule(chain, myUid, previousUidFirewallRule);
                    } catch (IllegalStateException ignored) {
                        // Removing match causes an exception when the rule entry for the uid does
                        // not exist. But this is fine and can be ignored.
                    }
                });
        }, NETWORK_SETTINGS);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingDozable() {
        doTestFirewallBlocking(FIREWALL_CHAIN_DOZABLE, ALLOWLIST);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingBackground() {
        doTestFirewallBlocking(FIREWALL_CHAIN_BACKGROUND, ALLOWLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingPowersave() {
        doTestFirewallBlocking(FIREWALL_CHAIN_POWERSAVE, ALLOWLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingRestricted() {
        doTestFirewallBlocking(FIREWALL_CHAIN_RESTRICTED, ALLOWLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingLowPowerStandby() {
        doTestFirewallBlocking(FIREWALL_CHAIN_LOW_POWER_STANDBY, ALLOWLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingStandby() {
        doTestFirewallBlocking(FIREWALL_CHAIN_STANDBY, DENYLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingOemDeny1() {
        doTestFirewallBlocking(FIREWALL_CHAIN_OEM_DENY_1, DENYLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingOemDeny2() {
        doTestFirewallBlocking(FIREWALL_CHAIN_OEM_DENY_2, DENYLIST);
    }

    @Test @IgnoreUpTo(SC_V2) @ConnectivityModuleTest
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testFirewallBlockingOemDeny3() {
        doTestFirewallBlocking(FIREWALL_CHAIN_OEM_DENY_3, DENYLIST);
    }

    private void assertSocketOpen(final Socket socket) throws Exception {
        mCtsNetUtils.testHttpRequest(socket);
    }

    private void assertSocketClosed(final Socket socket) throws Exception {
        try {
            mCtsNetUtils.testHttpRequest(socket);
            fail("Socket is expected to be closed");
        } catch (SocketException expected) {
        }
    }

    private void setUidFirewallRule(final int chain, final int uid, final int rule) {
        try {
            mCm.setUidFirewallRule(chain, uid, rule);
        } catch (IllegalStateException ignored) {
            // Removing match causes an exception when the rule entry for the uid does
            // not exist. But this is fine and can be ignored.
        }
    }

    private static final boolean EXPECT_OPEN = false;
    private static final boolean EXPECT_CLOSE = true;

    private void doTestFirewallCloseSocket(final int chain, final int rule, final int targetUid,
            final boolean expectClose) {
        runWithShellPermissionIdentity(() -> {
            // Firewall chain status will be restored after the test.
            final boolean wasChainEnabled = mCm.getFirewallChainEnabled(chain);
            final int myUid = Process.myUid();
            final int previousMyUidFirewallRule = mCm.getUidFirewallRule(chain, myUid);
            final int previousUidFirewallRule = mCm.getUidFirewallRule(chain, targetUid);
            final Socket socket = new Socket(TEST_HOST, HTTP_PORT);
            socket.setSoTimeout(NETWORK_REQUEST_TIMEOUT_MS);
            testAndCleanup(() -> {
                mCm.setFirewallChainEnabled(chain, false /* enable */);
                assertSocketOpen(socket);

                setUidFirewallRule(chain, targetUid, rule);
                if (targetUid != myUid) {
                    // If this test does not set rule on myUid, remove existing rule on myUid
                    setUidFirewallRule(chain, myUid, FIREWALL_RULE_DEFAULT);
                }

                mCm.setFirewallChainEnabled(chain, true /* enable */);

                if (expectClose) {
                    assertSocketClosed(socket);
                } else {
                    assertSocketOpen(socket);
                }
            }, /* cleanup */ () -> {
                    // Restore the global chain status
                    mCm.setFirewallChainEnabled(chain, wasChainEnabled);
                }, /* cleanup */ () -> {
                    // Restore the uid firewall rule status
                    setUidFirewallRule(chain, targetUid, previousUidFirewallRule);
                    if (targetUid != myUid) {
                        setUidFirewallRule(chain, myUid, previousMyUidFirewallRule);
                    }
                }, /* cleanup */ () -> {
                    socket.close();
                });
        }, NETWORK_SETTINGS);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) @ConnectivityModuleTest
    public void testFirewallCloseSocketAllowlistChainAllow() {
        doTestFirewallCloseSocket(FIREWALL_CHAIN_DOZABLE, FIREWALL_RULE_ALLOW,
                Process.myUid(), EXPECT_OPEN);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) @ConnectivityModuleTest
    public void testFirewallCloseSocketAllowlistChainDeny() {
        doTestFirewallCloseSocket(FIREWALL_CHAIN_DOZABLE, FIREWALL_RULE_DENY,
                Process.myUid(), EXPECT_CLOSE);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) @ConnectivityModuleTest
    public void testFirewallCloseSocketAllowlistChainOtherUid() {
        doTestFirewallCloseSocket(FIREWALL_CHAIN_DOZABLE, FIREWALL_RULE_ALLOW,
                Process.myUid() + 1, EXPECT_CLOSE);
        doTestFirewallCloseSocket(FIREWALL_CHAIN_DOZABLE, FIREWALL_RULE_DENY,
                Process.myUid() + 1, EXPECT_CLOSE);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) @ConnectivityModuleTest
    public void testFirewallCloseSocketDenylistChainAllow() {
        doTestFirewallCloseSocket(FIREWALL_CHAIN_STANDBY, FIREWALL_RULE_ALLOW,
                Process.myUid(), EXPECT_OPEN);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) @ConnectivityModuleTest
    public void testFirewallCloseSocketDenylistChainDeny() {
        doTestFirewallCloseSocket(FIREWALL_CHAIN_STANDBY, FIREWALL_RULE_DENY,
                Process.myUid(), EXPECT_CLOSE);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) @ConnectivityModuleTest
    public void testFirewallCloseSocketDenylistChainOtherUid() {
        doTestFirewallCloseSocket(FIREWALL_CHAIN_STANDBY, FIREWALL_RULE_ALLOW,
                Process.myUid() + 1, EXPECT_OPEN);
        doTestFirewallCloseSocket(FIREWALL_CHAIN_STANDBY, FIREWALL_RULE_DENY,
                Process.myUid() + 1, EXPECT_OPEN);
    }

    private void assumeTestSApis() {
        // Cannot use @IgnoreUpTo(Build.VERSION_CODES.R) because this test also requires API 31
        // shims, and @IgnoreUpTo does not check that.
        assumeTrue(TestUtils.shouldTestSApis());
    }

    private void unregisterRegisteredCallbacks() {
        for (NetworkCallback callback: mRegisteredCallbacks) {
            mCm.unregisterNetworkCallback(callback);
        }
    }

    private void registerDefaultNetworkCallback(NetworkCallback callback) {
        mCm.registerDefaultNetworkCallback(callback);
        mRegisteredCallbacks.add(callback);
    }

    private void registerDefaultNetworkCallback(NetworkCallback callback, Handler handler) {
        mCm.registerDefaultNetworkCallback(callback, handler);
        mRegisteredCallbacks.add(callback);
    }

    private void registerNetworkCallback(NetworkRequest request, NetworkCallback callback) {
        mCm.registerNetworkCallback(request, callback);
        mRegisteredCallbacks.add(callback);
    }

    private void registerSystemDefaultNetworkCallback(NetworkCallback callback, Handler handler) {
        mCmShim.registerSystemDefaultNetworkCallback(callback, handler);
        mRegisteredCallbacks.add(callback);
    }

    private void registerDefaultNetworkCallbackForUid(int uid, NetworkCallback callback,
            Handler handler) throws Exception {
        mCmShim.registerDefaultNetworkCallbackForUid(uid, callback, handler);
        mRegisteredCallbacks.add(callback);
    }

    private void requestNetwork(NetworkRequest request, NetworkCallback callback) {
        mCm.requestNetwork(request, callback);
        mRegisteredCallbacks.add(callback);
    }

    private void requestNetwork(NetworkRequest request, NetworkCallback callback, int timeoutSec) {
        mCm.requestNetwork(request, callback, timeoutSec);
        mRegisteredCallbacks.add(callback);
    }

    private void registerBestMatchingNetworkCallback(NetworkRequest request,
            NetworkCallback callback, Handler handler) {
        mCm.registerBestMatchingNetworkCallback(request, callback, handler);
        mRegisteredCallbacks.add(callback);
    }

    private void requestBackgroundNetwork(NetworkRequest request, NetworkCallback callback,
            Handler handler) throws Exception {
        mCmShim.requestBackgroundNetwork(request, callback, handler);
        mRegisteredCallbacks.add(callback);
    }
}
