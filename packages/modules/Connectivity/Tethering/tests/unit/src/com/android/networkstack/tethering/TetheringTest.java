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

package com.android.networkstack.tethering;

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.content.pm.PackageManager.GET_ACTIVITIES;
import static android.hardware.usb.UsbManager.USB_CONFIGURED;
import static android.hardware.usb.UsbManager.USB_CONNECTED;
import static android.hardware.usb.UsbManager.USB_FUNCTION_NCM;
import static android.hardware.usb.UsbManager.USB_FUNCTION_RNDIS;
import static android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
import static android.net.ConnectivityManager.TYPE_MOBILE_DUN;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.RouteInfo.RTN_UNICAST;
import static android.net.TetheringManager.ACTION_TETHER_STATE_CHANGED;
import static android.net.TetheringManager.CONNECTIVITY_SCOPE_GLOBAL;
import static android.net.TetheringManager.EXTRA_ACTIVE_LOCAL_ONLY;
import static android.net.TetheringManager.EXTRA_ACTIVE_TETHER;
import static android.net.TetheringManager.EXTRA_AVAILABLE_TETHER;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_ETHERNET;
import static android.net.TetheringManager.TETHERING_NCM;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.TetheringManager.TETHER_ERROR_IFACE_CFG_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_INTERNAL_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_SERVICE_UNAVAIL;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_IFACE;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_FAILED;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_STARTED;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_STOPPED;
import static android.net.dhcp.IDhcpServer.STATUS_SUCCESS;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_MODE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_LOCAL_ONLY;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_TETHERED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.system.OsConstants.RT_SCOPE_UNIVERSE;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.android.modules.utils.build.SdkLevel.isAtLeastS;
import static com.android.modules.utils.build.SdkLevel.isAtLeastT;
import static com.android.net.module.util.Inet4AddressUtils.inet4AddressToIntHTH;
import static com.android.net.module.util.Inet4AddressUtils.intToInet4AddressHTH;
import static com.android.net.module.util.NetworkStackConstants.RFC7421_PREFIX_LENGTH;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_0;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_NONE;
import static com.android.networkstack.tethering.TestConnectivityManager.BROADCAST_FIRST;
import static com.android.networkstack.tethering.TestConnectivityManager.CALLBACKS_FIRST;
import static com.android.networkstack.tethering.Tethering.UserRestrictionActionListener;
import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_FORCE_USB_FUNCTIONS;
import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_USB_NCM_FUNCTION;
import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_USB_RNDIS_FUNCTION;
import static com.android.networkstack.tethering.TetheringNotificationUpdater.DOWNSTREAM_NONE;
import static com.android.networkstack.tethering.UpstreamNetworkMonitor.EVENT_ON_CAPABILITIES;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.usage.NetworkStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.EthernetManager;
import android.net.EthernetManager.TetheredInterfaceCallback;
import android.net.EthernetManager.TetheredInterfaceRequest;
import android.net.IConnectivityManager;
import android.net.IIntResultListener;
import android.net.INetd;
import android.net.ITetheringEventCallback;
import android.net.InetAddresses;
import android.net.InterfaceConfigurationParcel;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.RoutingCoordinatorManager;
import android.net.TetherStatesParcel;
import android.net.TetheredClient;
import android.net.TetheredClient.AddressInfo;
import android.net.TetheringCallbackStartedParcel;
import android.net.TetheringConfigurationParcel;
import android.net.TetheringInterface;
import android.net.TetheringManager;
import android.net.TetheringRequestParcel;
import android.net.dhcp.DhcpLeaseParcelable;
import android.net.dhcp.DhcpServerCallbacks;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpEventCallbacks;
import android.net.dhcp.IDhcpServer;
import android.net.ip.DadProxy;
import android.net.ip.IpServer;
import android.net.ip.RouterAdvertisementDaemon;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.SoftApCallback;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.SdkUtil.LateSdk;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.IpNeighborMonitor;
import com.android.networkstack.apishim.common.BluetoothPanShim;
import com.android.networkstack.apishim.common.BluetoothPanShim.TetheredInterfaceCallbackShim;
import com.android.networkstack.apishim.common.BluetoothPanShim.TetheredInterfaceRequestShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.tethering.TestConnectivityManager.TestNetworkAgent;
import com.android.networkstack.tethering.metrics.TetheringMetrics;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.MiscAsserts;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Vector;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TetheringTest {
    @Rule public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private static final int IFINDEX_OFFSET = 100;

    private static final String TEST_MOBILE_IFNAME = "test_rmnet_data0";
    private static final String TEST_DUN_IFNAME = "test_dun0";
    private static final String TEST_XLAT_MOBILE_IFNAME = "v4-test_rmnet_data0";
    private static final String TEST_RNDIS_IFNAME = "test_rndis0";
    private static final String TEST_WIFI_IFNAME = "test_wlan0";
    private static final String TEST_WLAN_IFNAME = "test_wlan1";
    private static final String TEST_WLAN2_IFNAME = "test_wlan2";
    private static final String TEST_P2P_IFNAME = "test_p2p-p2p0-0";
    private static final String TEST_NCM_IFNAME = "test_ncm0";
    private static final String TEST_ETH_IFNAME = "test_eth0";
    private static final String TEST_BT_IFNAME = "test_pan0";
    private static final String TETHERING_NAME = "Tethering";
    private static final String[] PROVISIONING_APP_NAME = {"some", "app"};
    private static final String PROVISIONING_NO_UI_APP_NAME = "no_ui_app";
    private static final String TEST_RNDIS_REGEX = "test_rndis\\d";
    private static final String TEST_NCM_REGEX = "test_ncm\\d";
    private static final String TEST_WIFI_REGEX = "test_wlan\\d";
    private static final String TEST_P2P_REGEX = "test_p2p-p2p\\d-.*";
    private static final String TEST_BT_REGEX = "test_pan\\d";
    private static final String TEST_CALLER_PKG = "com.test.tethering";

    private static final int CELLULAR_NETID = 100;
    private static final int WIFI_NETID = 101;
    private static final int DUN_NETID = 102;

    private static final int TETHER_USB_RNDIS_NCM_FUNCTIONS = 2;

    private static final int DHCPSERVER_START_TIMEOUT_MS = 1000;

    private static final Network[] NULL_NETWORK = new Network[] {null};

    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Context mContext;
    @Mock private NetworkStatsManager mStatsManager;
    @Mock private OffloadHardwareInterface mOffloadHardwareInterface;
    @Mock private OffloadHardwareInterface.ForwardedStats mForwardedStats;
    @Mock private Resources mResources;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private UsbManager mUsbManager;
    @Mock private WifiManager mWifiManager;
    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private IPv6TetheringCoordinator mIPv6TetheringCoordinator;
    @Mock private DadProxy mDadProxy;
    @Mock private RouterAdvertisementDaemon mRouterAdvertisementDaemon;
    @Mock private IpNeighborMonitor mIpNeighborMonitor;
    @Mock private IDhcpServer mDhcpServer;
    @Mock private INetd mNetd;
    @Mock private UserManager mUserManager;
    @Mock private EthernetManager mEm;
    @Mock private TetheringNotificationUpdater mNotificationUpdater;
    @Mock private BpfCoordinator mBpfCoordinator;
    @Mock private PackageManager mPackageManager;
    @Mock private BluetoothAdapter mBluetoothAdapter;
    @Mock private BluetoothPan mBluetoothPan;
    @Mock private BluetoothPanShim mBluetoothPanShim;
    @Mock private TetheredInterfaceRequestShim mTetheredInterfaceRequestShim;
    @Mock private TetheringMetrics mTetheringMetrics;

    private final MockIpServerDependencies mIpServerDependencies =
            spy(new MockIpServerDependencies());
    private final MockTetheringDependencies mTetheringDependencies =
            new MockTetheringDependencies();

    // Like so many Android system APIs, these cannot be mocked because it is marked final.
    // We have to use the real versions.
    private final PersistableBundle mCarrierConfig = new PersistableBundle();
    private TestLooper mLooper;

    private Vector<Intent> mIntents;
    private BroadcastInterceptingContext mServiceContext;
    private MockContentResolver mContentResolver;
    private BroadcastReceiver mBroadcastReceiver;
    private Tethering mTethering;
    private TestTetheringEventCallback mTetheringEventCallback;
    private Tethering.TetherMainSM mTetherMainSM;
    private PhoneStateListener mPhoneStateListener;
    private InterfaceConfigurationParcel mInterfaceConfiguration;
    private TetheringConfiguration mConfig;
    private EntitlementManager mEntitleMgr;
    private OffloadController mOffloadCtrl;
    private PrivateAddressCoordinator mPrivateAddressCoordinator;
    private SoftApCallback mSoftApCallback;
    private SoftApCallback mLocalOnlyHotspotCallback;
    private UpstreamNetworkMonitor mUpstreamNetworkMonitor;
    private UpstreamNetworkMonitor.EventListener mEventListener;
    private TetheredInterfaceCallbackShim mTetheredInterfaceCallbackShim;

    private TestConnectivityManager mCm;
    private boolean mForceEthernetServiceUnavailable = false;

    private class TestContext extends BroadcastInterceptingContext {
        TestContext(Context base) {
            super(base);
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            return mApplicationInfo;
        }

        @Override
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }

        @Override
        public String getPackageName() {
            return "TetheringTest";
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.WIFI_SERVICE.equals(name)) return mWifiManager;
            if (Context.USB_SERVICE.equals(name)) return mUsbManager;
            if (Context.TELEPHONY_SERVICE.equals(name)) return mTelephonyManager;
            if (Context.USER_SERVICE.equals(name)) return mUserManager;
            if (Context.NETWORK_STATS_SERVICE.equals(name)) return mStatsManager;
            if (Context.CONNECTIVITY_SERVICE.equals(name)) return mCm;
            if (Context.ETHERNET_SERVICE.equals(name)) {
                if (mForceEthernetServiceUnavailable) return null;

                return mEm;
            }
            return super.getSystemService(name);
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        public String getSystemServiceName(Class<?> serviceClass) {
            if (TelephonyManager.class.equals(serviceClass)) return Context.TELEPHONY_SERVICE;
            return super.getSystemServiceName(serviceClass);
        }
    }

    public class MockIpServerDependencies extends IpServer.Dependencies {
        @Override
        public DadProxy getDadProxy(
                Handler handler, InterfaceParams ifParams) {
            return mDadProxy;
        }

        @Override
        public RouterAdvertisementDaemon getRouterAdvertisementDaemon(
                InterfaceParams ifParams) {
            return mRouterAdvertisementDaemon;
        }

        @Override
        public InterfaceParams getInterfaceParams(String ifName) {
            assertTrue("Non-mocked interface " + ifName,
                    ifName.equals(TEST_RNDIS_IFNAME)
                            || ifName.equals(TEST_WLAN_IFNAME)
                            || ifName.equals(TEST_WLAN2_IFNAME)
                            || ifName.equals(TEST_WIFI_IFNAME)
                            || ifName.equals(TEST_MOBILE_IFNAME)
                            || ifName.equals(TEST_DUN_IFNAME)
                            || ifName.equals(TEST_P2P_IFNAME)
                            || ifName.equals(TEST_NCM_IFNAME)
                            || ifName.equals(TEST_ETH_IFNAME)
                            || ifName.equals(TEST_BT_IFNAME));
            final String[] ifaces = new String[] {
                    TEST_RNDIS_IFNAME, TEST_WLAN_IFNAME, TEST_WLAN2_IFNAME, TEST_WIFI_IFNAME,
                    TEST_MOBILE_IFNAME, TEST_DUN_IFNAME, TEST_P2P_IFNAME, TEST_NCM_IFNAME,
                    TEST_ETH_IFNAME};
            return new InterfaceParams(ifName,
                    CollectionUtils.indexOf(ifaces, ifName) + IFINDEX_OFFSET,
                    MacAddress.ALL_ZEROS_ADDRESS);
        }

        @SuppressWarnings("DoNotCall") // Ignore warning for synchronous to call to Thread.run()
        @Override
        public void makeDhcpServer(String ifName, DhcpServingParamsParcel params,
                DhcpServerCallbacks cb) {
            new Thread(() -> {
                try {
                    cb.onDhcpServerCreated(STATUS_SUCCESS, mDhcpServer);
                } catch (RemoteException e) {
                    fail(e.getMessage());
                }
            }).run();
        }

        public IpNeighborMonitor getIpNeighborMonitor(Handler h, SharedLog l,
                IpNeighborMonitor.NeighborEventConsumer c) {
            return mIpNeighborMonitor;
        }
    }

    public class MockTetheringDependencies extends TetheringDependencies {
        ArrayList<IpServer> mAllDownstreams;

        @Override
        public BpfCoordinator makeBpfCoordinator(
                BpfCoordinator.Dependencies deps) {
            return mBpfCoordinator;
        }

        @Override
        public OffloadHardwareInterface makeOffloadHardwareInterface(Handler h, SharedLog log) {
            return mOffloadHardwareInterface;
        }

        @Override
        public OffloadController makeOffloadController(Handler h, SharedLog log,
                OffloadController.Dependencies deps) {
            mOffloadCtrl = spy(super.makeOffloadController(h, log, deps));
            // Return real object here instead of mock because
            // testReportFailCallbackIfOffloadNotSupported depend on real OffloadController object.
            return mOffloadCtrl;
        }

        @Override
        public UpstreamNetworkMonitor makeUpstreamNetworkMonitor(Context ctx, Handler h,
                SharedLog log, UpstreamNetworkMonitor.EventListener listener) {
            // Use a real object instead of a mock so that some tests can use a real UNM and some
            // can use a mock.
            mEventListener = listener;
            mUpstreamNetworkMonitor = spy(super.makeUpstreamNetworkMonitor(ctx, h, log, listener));
            return mUpstreamNetworkMonitor;
        }

        @Override
        public IPv6TetheringCoordinator makeIPv6TetheringCoordinator(
                ArrayList<IpServer> notifyList, SharedLog log) {
            mAllDownstreams = notifyList;
            return mIPv6TetheringCoordinator;
        }

        @Override
        public IpServer.Dependencies makeIpServerDependencies() {
            return mIpServerDependencies;
        }

        @Override
        public EntitlementManager makeEntitlementManager(Context ctx, Handler h, SharedLog log,
                Runnable callback) {
            mEntitleMgr = spy(super.makeEntitlementManager(ctx, h, log, callback));
            return mEntitleMgr;
        }

        @Nullable
        @Override
        public LateSdk<RoutingCoordinatorManager> getRoutingCoordinator(final Context context) {
            return new LateSdk<>(null);
        }

        @Override
        public TetheringConfiguration generateTetheringConfiguration(Context ctx, SharedLog log,
                int subId) {
            mConfig = spy(new FakeTetheringConfiguration(ctx, log, subId));
            return mConfig;
        }

        @Override
        public INetd getINetd(Context context) {
            return mNetd;
        }

        @Override
        public Looper makeTetheringLooper() {
            return mLooper.getLooper();
        }

        @Override
        public Context getContext() {
            return mServiceContext;
        }

        @Override
        public BluetoothAdapter getBluetoothAdapter() {
            return mBluetoothAdapter;
        }

        @Override
        public TetheringNotificationUpdater makeNotificationUpdater(Context ctx, Looper looper) {
            return mNotificationUpdater;
        }

        @Override
        public boolean isTetheringDenied() {
            return false;
        }

        @Override
        public TetheringMetrics makeTetheringMetrics() {
            return mTetheringMetrics;
        }

        @Override
        public PrivateAddressCoordinator makePrivateAddressCoordinator(Context ctx,
                TetheringConfiguration cfg) {
            mPrivateAddressCoordinator = super.makePrivateAddressCoordinator(ctx, cfg);
            return mPrivateAddressCoordinator;
        }

        @Override
        public BluetoothPanShim makeBluetoothPanShim(BluetoothPan pan) {
            try {
                when(mBluetoothPanShim.requestTetheredInterface(
                        any(), any())).thenReturn(mTetheredInterfaceRequestShim);
            } catch (UnsupportedApiLevelException e) {
                fail("BluetoothPan#requestTetheredInterface is not supported");
            }
            return mBluetoothPanShim;
        }
    }

    private static LinkProperties buildUpstreamLinkProperties(String interfaceName,
            boolean withIPv4, boolean withIPv6, boolean with464xlat) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(interfaceName);

        if (withIPv4) {
            prop.addLinkAddress(new LinkAddress("10.1.2.3/15"));
            prop.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0),
                    InetAddresses.parseNumericAddress("10.0.0.1"),
                    interfaceName, RTN_UNICAST));
        }

        if (withIPv6) {
            prop.addDnsServer(InetAddresses.parseNumericAddress("2001:db8::2"));
            prop.addLinkAddress(
                    new LinkAddress(InetAddresses.parseNumericAddress("2001:db8::"),
                            RFC7421_PREFIX_LENGTH));
            prop.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0),
                    InetAddresses.parseNumericAddress("2001:db8::1"),
                    interfaceName, RTN_UNICAST));
        }

        if (with464xlat) {
            final String clatInterface = "v4-" + interfaceName;
            final LinkProperties stackedLink = new LinkProperties();
            stackedLink.setInterfaceName(clatInterface);
            stackedLink.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0),
                    InetAddresses.parseNumericAddress("192.0.0.1"),
                    clatInterface, RTN_UNICAST));

            prop.addStackedLink(stackedLink);
        }

        return prop;
    }

    private static NetworkCapabilities buildUpstreamCapabilities(int transport, int... otherCaps) {
        // TODO: add NOT_VCN_MANAGED.
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addTransportType(transport);
        for (int cap : otherCaps) {
            nc.addCapability(cap);
        }
        return nc;
    }

    private static UpstreamNetworkState buildMobileUpstreamState(boolean withIPv4,
            boolean withIPv6, boolean with464xlat) {
        return new UpstreamNetworkState(
                buildUpstreamLinkProperties(TEST_MOBILE_IFNAME, withIPv4, withIPv6, with464xlat),
                buildUpstreamCapabilities(TRANSPORT_CELLULAR, NET_CAPABILITY_INTERNET),
                new Network(CELLULAR_NETID));
    }

    private static UpstreamNetworkState buildMobileIPv4UpstreamState() {
        return buildMobileUpstreamState(true, false, false);
    }

    private static UpstreamNetworkState buildMobileIPv6UpstreamState() {
        return buildMobileUpstreamState(false, true, false);
    }

    private static UpstreamNetworkState buildMobileDualStackUpstreamState() {
        return buildMobileUpstreamState(true, true, false);
    }

    private static UpstreamNetworkState buildMobile464xlatUpstreamState() {
        return buildMobileUpstreamState(false, true, true);
    }

    private static UpstreamNetworkState buildWifiUpstreamState() {
        return new UpstreamNetworkState(
                buildUpstreamLinkProperties(TEST_WIFI_IFNAME, true /* IPv4 */, true /* IPv6 */,
                        false /* 464xlat */),
                buildUpstreamCapabilities(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET),
                new Network(WIFI_NETID));
    }

    private static UpstreamNetworkState buildDunUpstreamState() {
        return new UpstreamNetworkState(
                buildUpstreamLinkProperties(TEST_DUN_IFNAME, true /* IPv4 */, true /* IPv6 */,
                        false /* 464xlat */),
                buildUpstreamCapabilities(TRANSPORT_CELLULAR, NET_CAPABILITY_DUN),
                new Network(DUN_NETID));
    }

    // See FakeSettingsProvider#clearSettingsProvider() that this also needs to be called before
    // use.
    @BeforeClass
    public static void setupOnce() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mResources.getStringArray(R.array.config_tether_dhcp_range))
                .thenReturn(new String[0]);
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                false);
        when(mNetd.interfaceGetList())
                .thenReturn(new String[] {
                        TEST_MOBILE_IFNAME, TEST_WLAN_IFNAME, TEST_WLAN2_IFNAME, TEST_RNDIS_IFNAME,
                        TEST_P2P_IFNAME, TEST_NCM_IFNAME, TEST_ETH_IFNAME, TEST_BT_IFNAME});
        when(mResources.getString(R.string.config_wifi_tether_enable)).thenReturn("");
        mInterfaceConfiguration = new InterfaceConfigurationParcel();
        mInterfaceConfiguration.flags = new String[0];
        when(mRouterAdvertisementDaemon.start())
                .thenReturn(true);
        initOffloadConfiguration(OFFLOAD_HAL_VERSION_HIDL_1_0, 0 /* defaultDisabled */);
        when(mOffloadHardwareInterface.getForwardedStats(any())).thenReturn(mForwardedStats);

        mServiceContext = new TestContext(mContext);
        mServiceContext.setUseRegisteredHandlers(true);
        mContentResolver = new MockContentResolver(mServiceContext);
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        setTetheringSupported(true /* supported */);
        mIntents = new Vector<>();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mIntents.addElement(intent);
            }
        };
        mServiceContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(ACTION_TETHER_STATE_CHANGED));

        mCm = spy(new TestConnectivityManager(mServiceContext, mock(IConnectivityManager.class)));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)).thenReturn(true);
    }

    // In order to interact with syncSM from the test, tethering must be created in test thread.
    private void initTetheringOnTestThread() throws Exception {
        mLooper = new TestLooper();
        mTethering = new Tethering(mTetheringDependencies);
        mTetherMainSM = mTethering.getTetherMainSMForTesting();
        verify(mStatsManager, times(1)).registerNetworkStatsProvider(anyString(), any());
        verify(mNetd).registerUnsolicitedEventListener(any());
        verifyDefaultNetworkRequestFiled();
        mTetheringEventCallback = registerTetheringEventCallback();

        final ArgumentCaptor<PhoneStateListener> phoneListenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        verify(mTelephonyManager).listen(phoneListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE));
        mPhoneStateListener = phoneListenerCaptor.getValue();

        final ArgumentCaptor<SoftApCallback> softApCallbackCaptor =
                ArgumentCaptor.forClass(SoftApCallback.class);
        verify(mWifiManager).registerSoftApCallback(any(), softApCallbackCaptor.capture());
        mSoftApCallback = softApCallbackCaptor.getValue();

        if (isAtLeastT()) {
            final ArgumentCaptor<SoftApCallback> localOnlyCallbackCaptor =
                    ArgumentCaptor.forClass(SoftApCallback.class);
            verify(mWifiManager).registerLocalOnlyHotspotSoftApCallback(any(),
                    localOnlyCallbackCaptor.capture());
            mLocalOnlyHotspotCallback = localOnlyCallbackCaptor.getValue();
        }
    }

    private void setTetheringSupported(final boolean supported) {
        Settings.Global.putInt(mContentResolver, Settings.Global.TETHER_SUPPORTED,
                supported ? 1 : 0);
        when(mUserManager.hasUserRestriction(
                UserManager.DISALLOW_CONFIG_TETHERING)).thenReturn(!supported);
        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TetheringConfiguration.TETHER_USB_RNDIS_FUNCTION);
        // Setup tetherable configuration.
        when(mResources.getStringArray(R.array.config_tether_usb_regexs))
                .thenReturn(new String[] {TEST_RNDIS_REGEX});
        when(mResources.getStringArray(R.array.config_tether_wifi_regexs))
                .thenReturn(new String[] {TEST_WIFI_REGEX});
        when(mResources.getStringArray(R.array.config_tether_wifi_p2p_regexs))
                .thenReturn(new String[] {TEST_P2P_REGEX});
        when(mResources.getStringArray(R.array.config_tether_bluetooth_regexs))
                .thenReturn(new String[] {TEST_BT_REGEX});
        when(mResources.getStringArray(R.array.config_tether_ncm_regexs))
                .thenReturn(new String[] {TEST_NCM_REGEX});
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_ETHERNET)).thenReturn(true);
        when(mResources.getIntArray(R.array.config_tether_upstream_types)).thenReturn(
                new int[] {TYPE_WIFI, TYPE_MOBILE_DUN});
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic)).thenReturn(true);
    }

    private void initTetheringUpstream(UpstreamNetworkState upstreamState) {
        doReturn(upstreamState).when(mUpstreamNetworkMonitor).getCurrentPreferredUpstream();
        doReturn(upstreamState).when(mUpstreamNetworkMonitor).selectPreferredUpstreamType(any());
    }

    private TetheringRequestParcel createTetheringRequestParcel(final int type) {
        return createTetheringRequestParcel(type, null, null, false, CONNECTIVITY_SCOPE_GLOBAL);
    }

    private TetheringRequestParcel createTetheringRequestParcel(final int type,
            final LinkAddress serverAddr, final LinkAddress clientAddr, final boolean exempt,
            final int scope) {
        final TetheringRequestParcel request = new TetheringRequestParcel();
        request.tetheringType = type;
        request.localIPv4Address = serverAddr;
        request.staticClientAddress = clientAddr;
        request.exemptFromEntitlementCheck = exempt;
        request.showProvisioningUi = false;
        request.connectivityScope = scope;

        return request;
    }

    @NonNull
    private TestTetheringEventCallback registerTetheringEventCallback() {
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        mTethering.registerTetheringEventCallback(callback);
        mLooper.dispatchAll();
        // Pull the first event which is filed immediately after the callback registration.
        callback.expectUpstreamChanged(NULL_NETWORK);
        return callback;
    }

    @After
    public void tearDown() {
        mServiceContext.unregisterReceiver(mBroadcastReceiver);
        FakeSettingsProvider.clearSettingsProvider();
    }

    private void sendWifiApStateChanged(int state) {
        final Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(EXTRA_WIFI_AP_STATE, state);
        mServiceContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        mLooper.dispatchAll();
    }

    private void sendWifiApStateChanged(int state, String ifname, int ipmode) {
        final Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(EXTRA_WIFI_AP_STATE, state);
        intent.putExtra(EXTRA_WIFI_AP_INTERFACE_NAME, ifname);
        intent.putExtra(EXTRA_WIFI_AP_MODE, ipmode);
        mServiceContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        mLooper.dispatchAll();
    }

    private static final String[] P2P_RECEIVER_PERMISSIONS_FOR_BROADCAST = {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE
    };

    private void sendWifiP2pConnectionChanged(
            boolean isGroupFormed, boolean isGroupOwner, String ifname) {
        WifiP2pGroup group = null;
        WifiP2pInfo p2pInfo = new WifiP2pInfo();
        p2pInfo.groupFormed = isGroupFormed;
        if (isGroupFormed) {
            p2pInfo.isGroupOwner = isGroupOwner;
            group = mock(WifiP2pGroup.class);
            when(group.isGroupOwner()).thenReturn(isGroupOwner);
            when(group.getInterface()).thenReturn(ifname);
        }

        final Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        when(intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)).thenReturn(p2pInfo);
        when(intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)).thenReturn(group);

        mServiceContext.sendBroadcastAsUserMultiplePermissions(intent, UserHandle.ALL,
                P2P_RECEIVER_PERMISSIONS_FOR_BROADCAST);
        mLooper.dispatchAll();
    }

    // enableType:
    // No function enabled            = -1
    // TETHER_USB_RNDIS_FUNCTION      = 0
    // TETHER_USB_NCM_FUNCTIONS       = 1
    // TETHER_USB_RNDIS_NCM_FUNCTIONS = 2
    private boolean tetherUsbFunctionMatches(int function, int enabledType) {
        if (enabledType < 0) return false;

        if (enabledType == TETHER_USB_RNDIS_NCM_FUNCTIONS) return function < enabledType;

        return function == enabledType;
    }

    private void sendUsbBroadcast(boolean connected, boolean configured, int function) {
        final Intent intent = new Intent(UsbManager.ACTION_USB_STATE);
        intent.putExtra(USB_CONNECTED, connected);
        intent.putExtra(USB_CONFIGURED, configured);
        intent.putExtra(USB_FUNCTION_RNDIS,
                tetherUsbFunctionMatches(TETHER_USB_RNDIS_FUNCTION, function));
        intent.putExtra(USB_FUNCTION_NCM,
                tetherUsbFunctionMatches(TETHER_USB_NCM_FUNCTION, function));
        mServiceContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        mLooper.dispatchAll();
    }

    private void sendConfigurationChanged() {
        final Intent intent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
        mServiceContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        mLooper.dispatchAll();
    }

    private void verifyDefaultNetworkRequestFiled() {
        if (isAtLeastS()) {
            verify(mCm, times(1)).registerSystemDefaultNetworkCallback(
                    any(NetworkCallback.class), any(Handler.class));
        } else {
            ArgumentCaptor<NetworkRequest> reqCaptor = ArgumentCaptor.forClass(
                    NetworkRequest.class);
            verify(mCm, times(1)).requestNetwork(reqCaptor.capture(),
                    any(NetworkCallback.class), any(Handler.class));
            assertTrue(TestConnectivityManager.looksLikeDefaultRequest(reqCaptor.getValue()));
        }

        // The default network request is only ever filed once.
        verifyNoMoreInteractions(mCm);
    }

    private void verifyInterfaceServingModeStarted(String ifname) throws Exception {
        verify(mNetd).interfaceSetCfg(any(InterfaceConfigurationParcel.class));
        verify(mNetd).tetherInterfaceAdd(ifname);
        verify(mNetd).networkAddInterface(INetd.LOCAL_NET_ID, ifname);
        verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(ifname),
                anyString(), anyString());
    }

    private void verifyTetheringBroadcast(String ifname, String whichExtra) {
        // Verify that ifname is in the whichExtra array of the tether state changed broadcast.
        final Intent bcast = mIntents.get(0);
        assertEquals(ACTION_TETHER_STATE_CHANGED, bcast.getAction());
        final ArrayList<String> ifnames = bcast.getStringArrayListExtra(whichExtra);
        assertTrue(ifnames.contains(ifname));
        mIntents.remove(bcast);
    }

    public void failingLocalOnlyHotspotLegacyApBroadcast(
            boolean emulateInterfaceStatusChanged) throws Exception {
        initTetheringOnTestThread();
        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // hotspot mode is to be started.
        if (emulateInterfaceStatusChanged) {
            mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        }
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED);

        // If, and only if, Tethering received an interface status changed then
        // it creates a IpServer and sends out a broadcast indicating that the
        // interface is "available".
        if (emulateInterfaceStatusChanged) {
            // There is 1 IpServer state change event: STATE_AVAILABLE
            verify(mNotificationUpdater, times(1)).onDownstreamChanged(DOWNSTREAM_NONE);
            verifyTetheringBroadcast(TEST_WLAN_IFNAME, EXTRA_AVAILABLE_TETHER);
            verify(mWifiManager).updateInterfaceIpState(
                    TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        }
        verifyNoMoreInteractions(mNetd);
        verifyNoMoreInteractions(mWifiManager);
    }

    private void prepareNcmTethering() {
        // Emulate startTethering(TETHERING_NCM) called
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_NCM), TEST_CALLER_PKG,
                null);
        mLooper.dispatchAll();
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_NCM);
    }

    private void prepareUsbTethering() {
        // Emulate pressing the USB tethering button in Settings UI.
        final TetheringRequestParcel request = createTetheringRequestParcel(TETHERING_USB);
        mTethering.startTethering(request, TEST_CALLER_PKG, null);
        mLooper.dispatchAll();

        assertEquals(1, mTethering.getActiveTetheringRequests().size());
        assertEquals(request, mTethering.getActiveTetheringRequests().get(TETHERING_USB));

        if (mTethering.getTetheringConfiguration().isUsingNcm()) {
            verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_NCM);
            mTethering.interfaceStatusChanged(TEST_NCM_IFNAME, true);
        } else {
            verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_RNDIS);
            mTethering.interfaceStatusChanged(TEST_RNDIS_IFNAME, true);
        }

    }

    @Test
    public void testUsbConfiguredBroadcastStartsTethering() throws Exception {
        initTetheringOnTestThread();
        UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        initTetheringUpstream(upstreamState);
        prepareUsbTethering();

        // This should produce no activity of any kind.
        verifyNoMoreInteractions(mNetd);

        // Pretend we then receive USB configured broadcast.
        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
        // Now we should see the start of tethering mechanics (in this case:
        // tetherMatchingInterfaces() which starts by fetching all interfaces).
        verify(mNetd, times(1)).interfaceGetList();

        // Event callback should receive selected upstream
        verify(mUpstreamNetworkMonitor, times(1)).getCurrentPreferredUpstream();
        mTetheringEventCallback.expectUpstreamChanged(upstreamState.network);
    }

    @Test
    public void failingLocalOnlyHotspotLegacyApBroadcastWithIfaceStatusChanged() throws Exception {
        failingLocalOnlyHotspotLegacyApBroadcast(true);
    }

    @Test
    public void failingLocalOnlyHotspotLegacyApBroadcastSansIfaceStatusChanged() throws Exception {
        failingLocalOnlyHotspotLegacyApBroadcast(false);
    }

    private void verifyStopHotpot() throws Exception {
        verify(mNetd).tetherApplyDnsInterfaces();
        verify(mNetd).tetherInterfaceRemove(TEST_WLAN_IFNAME);
        verify(mNetd).networkRemoveInterface(INetd.LOCAL_NET_ID, TEST_WLAN_IFNAME);
        // interfaceSetCfg() called once for enabling and twice disabling IPv4.
        verify(mNetd, times(3)).interfaceSetCfg(any(InterfaceConfigurationParcel.class));
        verify(mNetd).tetherStop();
        verify(mNetd).ipfwdDisableForwarding(TETHERING_NAME);
        verify(mWifiManager, times(3)).updateInterfaceIpState(
                TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        verifyNoMoreInteractions(mNetd);
        verifyNoMoreInteractions(mWifiManager);
        // Asking for the last error after the per-interface state machine
        // has been reaped yields an unknown interface error.
        assertEquals(TETHER_ERROR_UNKNOWN_IFACE, mTethering.getLastErrorForTest(TEST_WLAN_IFNAME));
    }

    private void verifyStartHotspot() throws Exception {
        verifyStartHotspot(false /* isLocalOnly */);
    }

    private void verifyStartHotspot(boolean isLocalOnly) throws Exception {
        verifyInterfaceServingModeStarted(TEST_WLAN_IFNAME);
        verifyTetheringBroadcast(TEST_WLAN_IFNAME, EXTRA_AVAILABLE_TETHER);
        verify(mWifiManager).updateInterfaceIpState(
                TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_UNSPECIFIED);

        verify(mNetd).ipfwdEnableForwarding(TETHERING_NAME);
        verify(mNetd).tetherStartWithConfiguration(any());
        verifyNoMoreInteractions(mNetd);

        final int expectedState = isLocalOnly ? IFACE_IP_MODE_LOCAL_ONLY : IFACE_IP_MODE_TETHERED;
        verify(mWifiManager).updateInterfaceIpState(TEST_WLAN_IFNAME, expectedState);
        verifyNoMoreInteractions(mWifiManager);

        verify(mUpstreamNetworkMonitor).startObserveAllNetworks();
        if (isLocalOnly) {
            // There are 2 IpServer state change events: STATE_AVAILABLE -> STATE_LOCAL_ONLY.
            verify(mNotificationUpdater, times(2)).onDownstreamChanged(DOWNSTREAM_NONE);
        } else {
            // There are 2 IpServer state change events: STATE_AVAILABLE -> STATE_TETHERED.
            verify(mNotificationUpdater).onDownstreamChanged(DOWNSTREAM_NONE);
            verify(mNotificationUpdater).onDownstreamChanged(eq(1 << TETHERING_WIFI));
        }
    }

    public void workingLocalOnlyHotspotEnrichedApBroadcast(
            boolean emulateInterfaceStatusChanged) throws Exception {
        initTetheringOnTestThread();
        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // hotspot mode is to be started.
        if (emulateInterfaceStatusChanged) {
            mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        }
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_LOCAL_ONLY);

        verifyStartHotspot(true /* isLocalOnly */);
        verifyTetheringBroadcast(TEST_WLAN_IFNAME, EXTRA_ACTIVE_LOCAL_ONLY);

        // Emulate externally-visible WifiManager effects, when hotspot mode
        // is being torn down.
        sendWifiApStateChanged(WIFI_AP_STATE_DISABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_LOCAL_ONLY);
        mTethering.interfaceRemoved(TEST_WLAN_IFNAME);
        mLooper.dispatchAll();

        verifyStopHotpot();
    }

    /**
     * Send CMD_IPV6_TETHER_UPDATE to IpServers as would be done by IPv6TetheringCoordinator.
     */
    private void sendIPv6TetherUpdates(UpstreamNetworkState upstreamState) {
        // IPv6TetheringCoordinator must have been notified of downstream
        for (IpServer ipSrv : mTetheringDependencies.mAllDownstreams) {
            UpstreamNetworkState ipv6OnlyState = buildMobileUpstreamState(false, true, false);
            ipSrv.sendMessage(IpServer.CMD_IPV6_TETHER_UPDATE, 0, 0,
                    upstreamState.linkProperties.isIpv6Provisioned()
                            ? ipv6OnlyState.linkProperties
                            : null);
            break;
        }
        mLooper.dispatchAll();
    }

    private void runUsbTethering(UpstreamNetworkState upstreamState) {
        initTetheringUpstream(upstreamState);
        prepareUsbTethering();
        if (mTethering.getTetheringConfiguration().isUsingNcm()) {
            sendUsbBroadcast(true, true, TETHER_USB_NCM_FUNCTION);
            verify(mIPv6TetheringCoordinator).addActiveDownstream(
                    argThat(sm -> sm.linkProperties().getInterfaceName().equals(TEST_NCM_IFNAME)),
                    eq(IpServer.STATE_TETHERED));
        } else {
            sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
            verify(mIPv6TetheringCoordinator).addActiveDownstream(
                    argThat(sm -> sm.linkProperties().getInterfaceName().equals(TEST_RNDIS_IFNAME)),
                    eq(IpServer.STATE_TETHERED));
        }

    }

    private void assertSetIfaceToDadProxy(final int numOfCalls, final String ifaceName) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R || "S".equals(Build.VERSION.CODENAME)
                    || "T".equals(Build.VERSION.CODENAME)) {
            verify(mDadProxy, times(numOfCalls)).setUpstreamIface(
                     argThat(ifaceParams  -> ifaceName.equals(ifaceParams.name)));
        }
    }

    @Test
    public void workingMobileUsbTethering_IPv4() throws Exception {
        initTetheringOnTestThread();
        UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        runUsbTethering(upstreamState);

        verify(mNetd, times(1)).tetherAddForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);

        sendIPv6TetherUpdates(upstreamState);
        assertSetIfaceToDadProxy(0 /* numOfCalls */, "" /* ifaceName */);
        verify(mRouterAdvertisementDaemon, never()).buildNewRa(any(), notNull());
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
    }

    @Test
    public void workingMobileUsbTethering_IPv4LegacyDhcp() throws Exception {
        initTetheringOnTestThread();
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                true);
        sendConfigurationChanged();
        final UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        runUsbTethering(upstreamState);
        sendIPv6TetherUpdates(upstreamState);

        verify(mIpServerDependencies, never()).makeDhcpServer(any(), any(), any());
    }

    @Test
    public void workingMobileUsbTethering_IPv6() throws Exception {
        initTetheringOnTestThread();
        UpstreamNetworkState upstreamState = buildMobileIPv6UpstreamState();
        runUsbTethering(upstreamState);

        verify(mNetd, times(1)).tetherAddForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);

        sendIPv6TetherUpdates(upstreamState);
        // TODO: add interfaceParams to compare in verify.
        assertSetIfaceToDadProxy(1 /* numOfCalls */, TEST_MOBILE_IFNAME /* ifaceName */);
        verify(mRouterAdvertisementDaemon, times(1)).buildNewRa(any(), notNull());
        verify(mNetd, times(1)).tetherApplyDnsInterfaces();
    }

    @Test
    public void workingMobileUsbTethering_DualStack() throws Exception {
        initTetheringOnTestThread();
        UpstreamNetworkState upstreamState = buildMobileDualStackUpstreamState();
        runUsbTethering(upstreamState);

        verify(mNetd, times(1)).tetherAddForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);
        verify(mRouterAdvertisementDaemon, times(1)).start();
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());

        sendIPv6TetherUpdates(upstreamState);
        assertSetIfaceToDadProxy(1 /* numOfCalls */, TEST_MOBILE_IFNAME /* ifaceName */);
        verify(mRouterAdvertisementDaemon, times(1)).buildNewRa(any(), notNull());
        verify(mNetd, times(1)).tetherApplyDnsInterfaces();
    }

    @Test
    public void workingMobileUsbTethering_MultipleUpstreams() throws Exception {
        initTetheringOnTestThread();
        UpstreamNetworkState upstreamState = buildMobile464xlatUpstreamState();
        runUsbTethering(upstreamState);

        verify(mNetd, times(1)).tetherAddForward(TEST_RNDIS_IFNAME, TEST_XLAT_MOBILE_IFNAME);
        verify(mNetd, times(1)).tetherAddForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_RNDIS_IFNAME,
                TEST_XLAT_MOBILE_IFNAME);
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_RNDIS_IFNAME, TEST_MOBILE_IFNAME);

        sendIPv6TetherUpdates(upstreamState);
        assertSetIfaceToDadProxy(1 /* numOfCalls */, TEST_MOBILE_IFNAME /* ifaceName */);
        verify(mRouterAdvertisementDaemon, times(1)).buildNewRa(any(), notNull());
        verify(mNetd, times(1)).tetherApplyDnsInterfaces();
    }

    @Test
    public void workingMobileUsbTethering_v6Then464xlat() throws Exception {
        initTetheringOnTestThread();
        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TetheringConfiguration.TETHER_USB_NCM_FUNCTION);
        when(mResources.getStringArray(R.array.config_tether_usb_regexs))
                .thenReturn(new String[] {TEST_NCM_REGEX});
        sendConfigurationChanged();

        // Setup IPv6
        UpstreamNetworkState upstreamState = buildMobileIPv6UpstreamState();
        runUsbTethering(upstreamState);

        verify(mNetd, times(1)).tetherAddForward(TEST_NCM_IFNAME, TEST_MOBILE_IFNAME);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_NCM_IFNAME, TEST_MOBILE_IFNAME);

        // Then 464xlat comes up
        upstreamState = buildMobile464xlatUpstreamState();
        initTetheringUpstream(upstreamState);

        // Upstream LinkProperties changed: UpstreamNetworkMonitor sends EVENT_ON_LINKPROPERTIES.
        mEventListener.onUpstreamEvent(UpstreamNetworkMonitor.EVENT_ON_LINKPROPERTIES,
                upstreamState);
        mLooper.dispatchAll();

        // Forwarding is added for 464xlat
        verify(mNetd, times(1)).tetherAddForward(TEST_NCM_IFNAME, TEST_XLAT_MOBILE_IFNAME);
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_NCM_IFNAME,
                TEST_XLAT_MOBILE_IFNAME);
        // Forwarding was not re-added for v6 (still times(1))
        verify(mNetd, times(1)).tetherAddForward(TEST_NCM_IFNAME, TEST_MOBILE_IFNAME);
        verify(mNetd, times(1)).ipfwdAddInterfaceForward(TEST_NCM_IFNAME, TEST_MOBILE_IFNAME);
        // DHCP not restarted on downstream (still times(1))
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
    }

    @Test
    public void configTetherUpstreamAutomaticIgnoresConfigTetherUpstreamTypes() throws Exception {
        initTetheringOnTestThread();
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic)).thenReturn(true);
        sendConfigurationChanged();

        // Setup IPv6
        final UpstreamNetworkState upstreamState = buildMobileIPv6UpstreamState();
        runUsbTethering(upstreamState);

        // UpstreamNetworkMonitor should choose upstream automatically
        // (in this specific case: choose the default network).
        verify(mUpstreamNetworkMonitor, times(1)).getCurrentPreferredUpstream();
        verify(mUpstreamNetworkMonitor, never()).selectPreferredUpstreamType(any());

        mTetheringEventCallback.expectUpstreamChanged(upstreamState.network);
    }

    private void verifyDisableTryCellWhenTetheringStop(InOrder inOrder) {
        runStopUSBTethering();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
    }

    private void upstreamSelectionTestCommon(final boolean automatic, InOrder inOrder,
            TestNetworkAgent mobile, TestNetworkAgent wifi) throws Exception {
        // Enable automatic upstream selection.
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic)).thenReturn(automatic);
        sendConfigurationChanged();
        mLooper.dispatchAll();

        // Start USB tethering with no current upstream.
        prepareUsbTethering();
        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
        inOrder.verify(mUpstreamNetworkMonitor).startObserveAllNetworks();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(true);

        // Pretend cellular connected and expect the upstream to be set.
        mobile.fakeConnect();
        mCm.makeDefaultNetwork(mobile, BROADCAST_FIRST);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        // Switch upstream to wifi.
        wifi.fakeConnect();
        mCm.makeDefaultNetwork(wifi, BROADCAST_FIRST);
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        mTetheringEventCallback.expectUpstreamChanged(wifi.networkId);
    }

    private void verifyAutomaticUpstreamSelection(boolean configAutomatic) throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        // Enable automatic upstream selection.
        upstreamSelectionTestCommon(configAutomatic, inOrder, mobile, wifi);

        // This code has historically been racy, so test different orderings of CONNECTIVITY_ACTION
        // broadcasts and callbacks, and add mLooper.dispatchAll() calls between the two.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();

        // Switch upstreams a few times.
        mCm.makeDefaultNetwork(mobile, BROADCAST_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        mCm.makeDefaultNetwork(wifi, BROADCAST_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        mTetheringEventCallback.expectUpstreamChanged(wifi.networkId);

        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        mTetheringEventCallback.expectUpstreamChanged(wifi.networkId);

        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        // Wifi disconnecting should not have any affect since it's not the current upstream.
        wifi.fakeDisconnect();
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();

        // Lose and regain upstream.
        assertTrue(mUpstreamNetworkMonitor.getCurrentPreferredUpstream().linkProperties
                .hasIPv4Address());
        mCm.makeDefaultNetwork(null, BROADCAST_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mobile.fakeDisconnect();
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(true);
        mTetheringEventCallback.expectUpstreamChanged(NULL_NETWORK);

        mobile = new TestNetworkAgent(mCm, buildMobile464xlatUpstreamState());
        mobile.fakeConnect();
        mCm.makeDefaultNetwork(mobile, BROADCAST_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        // Check the IP addresses to ensure that the upstream is indeed not the same as the previous
        // mobile upstream, even though the netId is (unrealistically) the same.
        assertFalse(mUpstreamNetworkMonitor.getCurrentPreferredUpstream().linkProperties
                .hasIPv4Address());

        // Lose and regain upstream again.
        mCm.makeDefaultNetwork(null, CALLBACKS_FIRST, doDispatchAll);
        mobile.fakeDisconnect();
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(true);
        mTetheringEventCallback.expectUpstreamChanged(NULL_NETWORK);

        mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        mobile.fakeConnect();
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        assertTrue(mUpstreamNetworkMonitor.getCurrentPreferredUpstream().linkProperties
                .hasIPv4Address());

        // Check that the code does not crash if onLinkPropertiesChanged is received after onLost.
        mobile.fakeDisconnect();
        mobile.sendLinkProperties();
        mLooper.dispatchAll();

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    @Test
    public void testAutomaticUpstreamSelection() throws Exception {
        verifyAutomaticUpstreamSelection(true /* configAutomatic */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testAutomaticUpstreamSelectionWithConfigDisabled() throws Exception {
        // Expect that automatic config can't disable the automatic mode because automatic mode
        // is always enabled on U+ device.
        verifyAutomaticUpstreamSelection(false /* configAutomatic */);
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    public void testLegacyUpstreamSelection() throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        // Enable legacy upstream selection.
        upstreamSelectionTestCommon(false, inOrder, mobile, wifi);

        // Wifi disconnecting and the default network switch to mobile, the upstream should also
        // switch to mobile.
        wifi.fakeDisconnect();
        mLooper.dispatchAll();
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST, null);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(mobile.networkId);

        wifi.fakeConnect();
        mLooper.dispatchAll();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST, null);
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        mTetheringEventCallback.expectUpstreamChanged(wifi.networkId);

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    private void verifyWifiUpstreamAndUnregisterDunCallback(@NonNull final InOrder inOrder,
            @NonNull final TestNetworkAgent wifi, @NonNull final NetworkCallback currentDunCallack)
            throws Exception {
        assertNotNull(currentDunCallack);

        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        inOrder.verify(mCm).unregisterNetworkCallback(eq(currentDunCallack));
        mTetheringEventCallback.expectUpstreamChanged(wifi.networkId);
        mTetheringEventCallback.assertNoUpstreamChangeCallback();
    }

    @Nullable
    private NetworkCallback verifyDunUpstream(@NonNull final InOrder inOrder,
            @NonNull final TestNetworkAgent dun, final boolean needToRequestNetwork)
            throws Exception {
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(true);
        ArgumentCaptor<NetworkCallback> captor = ArgumentCaptor.forClass(NetworkCallback.class);
        NetworkCallback dunNetworkCallback = null;
        if (needToRequestNetwork) {
            inOrder.verify(mCm).requestNetwork(any(), eq(0), eq(TYPE_MOBILE_DUN), any(),
                    captor.capture());
            dunNetworkCallback = captor.getValue();
        }
        mTetheringEventCallback.expectUpstreamChanged(NULL_NETWORK);
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        dun.fakeConnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(dun.networkId);

        if (needToRequestNetwork) {
            assertNotNull(dunNetworkCallback);
        } else {
            assertNull(dunNetworkCallback);
        }

        return dunNetworkCallback;
    }

    // Overall test coverage:
    // - verifyChooseDunUpstreamByAutomaticMode: common, test#1, test#2
    // - testChooseDunUpstreamByAutomaticMode_defaultNetworkWifi: test#3, test#4
    // - testChooseDunUpstreamByAutomaticMode_loseDefaultNetworkWifi: test#5
    // - testChooseDunUpstreamByAutomaticMode_defaultNetworkCell: test#5, test#7
    // - testChooseDunUpstreamByAutomaticMode_loseAndRegainDun: test#8
    // - testChooseDunUpstreamByAutomaticMode_switchDefaultFromWifiToCell: test#9, test#10
    //
    // Overall test cases:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |       |       |       |   -   | --+
    // +-------+-------+-------+-------+-------+   |
    // |   -   |       |   V   |       |   -   |   |
    // +-------+-------+-------+-------+-------+   |
    // |   -   |       |   V   |   O   |  Dun  |   +-- chooseDunUpstreamTestCommon
    // +-------+-------+-------+-------+-------+   |
    // |   -   |   V   |   O   |   O   |  WiFi |   |
    // +-------+-------+-------+-------+-------+   |
    // |   -   |   V   |   O   |       |  WiFi | --+
    // +-------+-------+-------+-------+-------+
    // |       |   O   |   V   |       |   -   |
    // |   1   +-------+-------+-------+-------+
    // |       |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |       |   O   |   V   |       |   -   |
    // |   2   +-------+-------+-------+-------+
    // |       |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |   3   |   V   |   O   |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    // |   4   |   V   |       |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    // |   5   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |   6   |       |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |   7   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |       |       |       |       |   -   |
    // |   8   +-------+-------+-------+-------+
    // |       |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |       |   V   |       |   O   |  WiFi |
    // |   9   +-------+-------+-------+-------+
    // |       |   V   |       |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    // |       |   O   |   V   |       |   -   |
    // |   10  +-------+-------+-------+-------+
    // |       |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    //
    // Annotation:
    // 1. "V" means that the given network is connected and it is default network.
    // 2. "O" means that the given network is connected and it is not default network.
    //

    // Test case:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |       |       |       |   -   | --+
    // +-------+-------+-------+-------+-------+   |
    // |   -   |       |   V   |       |   -   |   |
    // +-------+-------+-------+-------+-------+   |
    // |   -   |       |   V   |   O   |  Dun  |   +-- chooseDunUpstreamTestCommon
    // +-------+-------+-------+-------+-------+   |
    // |   -   |   V   |   O   |   O   |  WiFi |   |
    // +-------+-------+-------+-------+-------+   |
    // |   -   |   V   |   O   |       |  WiFi | --+
    // +-------+-------+-------+-------+-------+
    // |       |   O   |   V   |       |   -   |
    // |   1   +-------+-------+-------+-------+
    // |       |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |       |   O   |   V   |       |   -   |
    // |   2   +-------+-------+-------+-------+
    // |       |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    //
    private void verifyChooseDunUpstreamByAutomaticMode(boolean configAutomatic) throws Exception {
        initTetheringOnTestThread();
        // Enable automatic upstream selection.
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        chooseDunUpstreamTestCommon(configAutomatic, inOrder, mobile, wifi, dun);

        // [1] When default network switch to mobile and wifi is connected (may have low signal),
        // automatic mode would request dun again and choose it as upstream.
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyDunUpstream(inOrder, dun, true /* needToRequestNetwork */);

        // [2] Lose and regain upstream again.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        dun.fakeDisconnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(true);
        mTetheringEventCallback.expectUpstreamChanged(NULL_NETWORK);
        inOrder.verify(mCm, never()).unregisterNetworkCallback(any(NetworkCallback.class));
        dun.fakeConnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(dun.networkId);

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    @Test
    public void testChooseDunUpstreamByAutomaticMode() throws Exception {
        verifyChooseDunUpstreamByAutomaticMode(true /* configAutomatic */);
    }

    // testChooseDunUpstreamByAutomaticMode_* doesn't verify configAutomatic:false because no
    // matter |configAutomatic| set to true or false, the result always be automatic mode. We
    // just need one test to make sure this behavior. Don't need to test this configuration
    // in all tests.
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testChooseDunUpstreamByAutomaticModeWithConfigDisabled() throws Exception {
        verifyChooseDunUpstreamByAutomaticMode(false /* configAutomatic */);
    }

    // Test case:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |   3   |   V   |   O   |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    // |   4   |   V   |       |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    //
    // See verifyChooseDunUpstreamByAutomaticMode for the annotation.
    //
    @Test
    public void testChooseDunUpstreamByAutomaticMode_defaultNetworkWifi() throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        final InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        final NetworkCallback dunNetworkCallback1 = setupDunUpstreamTest(
                true /* configAutomatic */, inOrder);

        // When wifi connected, unregister dun request and choose wifi as upstream.
        wifi.fakeConnect();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyWifiUpstreamAndUnregisterDunCallback(inOrder, wifi, dunNetworkCallback1);

        // When default network switch to mobile and wifi is connected (may have low signal),
        // automatic mode would request dun again and choose it as upstream.
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        final NetworkCallback dunNetworkCallback2 = verifyDunUpstream(inOrder, dun,
                true /* needToRequestNetwork */);

        // [3] When default network switch to wifi and mobile is still connected,
        // unregister dun request and choose wifi as upstream.
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyWifiUpstreamAndUnregisterDunCallback(inOrder, wifi, dunNetworkCallback2);

        // [4] When mobile is disconnected, keep wifi as upstream.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        mobile.fakeDisconnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    // Test case:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |   V   |       |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    // |   5   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    //
    // See verifyChooseDunUpstreamByAutomaticMode for the annotation.
    //
    @Test
    public void testChooseDunUpstreamByAutomaticMode_loseDefaultNetworkWifi() throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        final InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        final NetworkCallback dunNetworkCallback = setupDunUpstreamTest(
                true /* configAutomatic */, inOrder);

        // When wifi connected, unregister dun request and choose wifi as upstream.
        wifi.fakeConnect();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyWifiUpstreamAndUnregisterDunCallback(inOrder, wifi, dunNetworkCallback);

        // [5] When wifi is disconnected, automatic mode would request dun again and choose it
        // as upstream.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        mCm.makeDefaultNetwork(null, CALLBACKS_FIRST, doDispatchAll);
        wifi.fakeDisconnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        verifyDunUpstream(inOrder, dun, true /* needToRequestNetwork */);

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    // Test case:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |   6   |       |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |   7   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    //
    // See verifyChooseDunUpstreamByAutomaticMode for the annotation.
    //
    @Test
    public void testChooseDunUpstreamByAutomaticMode_defaultNetworkCell() throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        final InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        setupDunUpstreamTest(true /* configAutomatic */, inOrder);

        // Pretend dun connected and expect choose dun as upstream.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        dun.fakeConnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(dun.networkId);

        // [6] When mobile is connected and default network switch to mobile, keep dun as upstream.
        mobile.fakeConnect();
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();

        // [7] When mobile is disconnected, keep dun as upstream.
        mCm.makeDefaultNetwork(null, CALLBACKS_FIRST, doDispatchAll);
        mobile.fakeDisconnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    // Test case:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |       |       |       |       |   -   |
    // |   8   +-------+-------+-------+-------+
    // |       |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    //
    // See verifyChooseDunUpstreamByAutomaticMode for the annotation.
    //
    @Test
    public void testChooseDunUpstreamByAutomaticMode_loseAndRegainDun() throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        final InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        setupDunUpstreamTest(true /* configAutomatic */, inOrder);

        // Pretend dun connected and expect choose dun as upstream.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        dun.fakeConnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(dun.networkId);

        // [8] Lose and regain upstream again.
        dun.fakeDisconnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        verifyDunUpstream(inOrder, dun, false /* needToRequestNetwork */);

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    // Test case:
    // +-------+-------+-------+-------+-------+
    // | Test  | WiFi  | Cellu |  Dun  | Expec |
    // | Case  |       | alr   |       | ted   |
    // |   #   |       |       |       | Upstr |
    // |       |       |       |       | eam   |
    // +-------+-------+-------+-------+-------+
    // |   -   |       |       |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    // |       |   V   |       |   O   |  WiFi |
    // |   9   +-------+-------+-------+-------+
    // |       |   V   |       |       |  WiFi |
    // +-------+-------+-------+-------+-------+
    // |       |   O   |   V   |       |   -   |
    // |   10  +-------+-------+-------+-------+
    // |       |   O   |   V   |   O   |  Dun  |
    // +-------+-------+-------+-------+-------+
    //
    // See verifyChooseDunUpstreamByAutomaticMode for the annotation.
    //
    @Test
    public void testChooseDunUpstreamByAutomaticMode_switchDefaultFromWifiToCell()
            throws Exception {
        initTetheringOnTestThread();
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        final InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        final NetworkCallback dunNetworkCallback = setupDunUpstreamTest(
                true /* configAutomatic */, inOrder);

        // Pretend dun connected and expect choose dun as upstream.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        dun.fakeConnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(dun.networkId);

        // [9] When wifi is connected and default network switch to wifi, unregister dun request
        // and choose wifi as upstream. When dun is disconnected, keep wifi as upstream.
        wifi.fakeConnect();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyWifiUpstreamAndUnregisterDunCallback(inOrder, wifi, dunNetworkCallback);
        dun.fakeDisconnect(CALLBACKS_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();

        // [10] When mobile and mobile are connected and default network switch to mobile
        // (may have low signal), automatic mode would request dun again and choose it as
        // upstream.
        mobile.fakeConnect();
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyDunUpstream(inOrder, dun, true /* needToRequestNetwork */);

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    public void testChooseDunUpstreamByLegacyMode() throws Exception {
        initTetheringOnTestThread();
        // Enable Legacy upstream selection.
        TestNetworkAgent mobile = new TestNetworkAgent(mCm, buildMobileDualStackUpstreamState());
        TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());
        TestNetworkAgent dun = new TestNetworkAgent(mCm, buildDunUpstreamState());
        InOrder inOrder = inOrder(mCm, mUpstreamNetworkMonitor);
        chooseDunUpstreamTestCommon(false, inOrder, mobile, wifi, dun);

        // Legacy mode would keep use wifi as upstream (because it has higher priority in the
        // list).
        mCm.makeDefaultNetwork(mobile, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        mTetheringEventCallback.assertNoUpstreamChangeCallback();
        // BUG: when wifi disconnect, the dun request would not be filed again because wifi is
        // no longer be default network which do not have CONNECTIVIY_ACTION broadcast.
        wifi.fakeDisconnect();
        mLooper.dispatchAll();
        inOrder.verify(mCm, never()).requestNetwork(any(), eq(0), eq(TYPE_MOBILE_DUN), any(),
                any());

        // Change the legacy priority list that dun is higher than wifi.
        when(mResources.getIntArray(R.array.config_tether_upstream_types)).thenReturn(
                new int[] { TYPE_MOBILE_DUN, TYPE_WIFI });
        sendConfigurationChanged();
        mLooper.dispatchAll();

        // Make wifi as default network. Note: mobile also connected.
        wifi.fakeConnect();
        mLooper.dispatchAll();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        // BUG: dun has higher priority than wifi but tethering don't file dun request because
        // current upstream is wifi.
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(false);
        inOrder.verify(mCm, never()).requestNetwork(any(), eq(0), eq(TYPE_MOBILE_DUN), any(),
                any());

        verifyDisableTryCellWhenTetheringStop(inOrder);
    }

    private NetworkCallback setupDunUpstreamTest(final boolean automatic, InOrder inOrder) {
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic)).thenReturn(automatic);
        when(mTelephonyManager.isTetheringApnRequired()).thenReturn(true);
        sendConfigurationChanged();
        mLooper.dispatchAll();

        // Start USB tethering with no current upstream.
        prepareUsbTethering();
        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
        inOrder.verify(mUpstreamNetworkMonitor).startObserveAllNetworks();
        inOrder.verify(mUpstreamNetworkMonitor).setTryCell(true);
        ArgumentCaptor<NetworkCallback> captor = ArgumentCaptor.forClass(NetworkCallback.class);
        inOrder.verify(mCm).requestNetwork(any(), eq(0), eq(TYPE_MOBILE_DUN), any(),
                captor.capture() /* DUN network callback */);

        return captor.getValue();
    }

    private void chooseDunUpstreamTestCommon(final boolean automatic, InOrder inOrder,
            TestNetworkAgent mobile, TestNetworkAgent wifi, TestNetworkAgent dun)
            throws Exception {
        final NetworkCallback dunNetworkCallback = setupDunUpstreamTest(automatic, inOrder);

        // Pretend cellular connected and expect the upstream to be not set.
        mobile.fakeConnect();
        mCm.makeDefaultNetwork(mobile, BROADCAST_FIRST);
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();

        // Pretend dun connected and expect choose dun as upstream.
        final Runnable doDispatchAll = () -> mLooper.dispatchAll();
        dun.fakeConnect(BROADCAST_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.expectUpstreamChanged(dun.networkId);

        // When wifi connected, unregister dun request and choose wifi as upstream.
        wifi.fakeConnect();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verifyWifiUpstreamAndUnregisterDunCallback(inOrder, wifi, dunNetworkCallback);
        dun.fakeDisconnect(BROADCAST_FIRST, doDispatchAll);
        mLooper.dispatchAll();
        mTetheringEventCallback.assertNoUpstreamChangeCallback();
    }

    private void runNcmTethering() {
        prepareNcmTethering();
        sendUsbBroadcast(true, true, TETHER_USB_NCM_FUNCTION);
    }

    @Test
    public void workingNcmTethering() throws Exception {
        initTetheringOnTestThread();
        runNcmTethering();

        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
    }

    @Test
    public void workingNcmTethering_LegacyDhcp() throws Exception {
        initTetheringOnTestThread();
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                true);
        sendConfigurationChanged();
        runNcmTethering();

        verify(mIpServerDependencies, never()).makeDhcpServer(any(), any(), any());
    }

    @Test
    public void workingLocalOnlyHotspotEnrichedApBroadcastWithIfaceChanged() throws Exception {
        workingLocalOnlyHotspotEnrichedApBroadcast(true);
    }

    @Test
    public void workingLocalOnlyHotspotEnrichedApBroadcastSansIfaceChanged() throws Exception {
        workingLocalOnlyHotspotEnrichedApBroadcast(false);
    }

    // TODO: Test with and without interfaceStatusChanged().
    @Test
    public void failingWifiTetheringLegacyApBroadcast() throws Exception {
        initTetheringOnTestThread();
        when(mWifiManager.startTetheredHotspot(any(SoftApConfiguration.class))).thenReturn(true);

        // Emulate pressing the WiFi tethering button.
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_WIFI), TEST_CALLER_PKG,
                null);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).startTetheredHotspot(null);
        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mNetd);

        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // tethering mode is to be started.
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED);

        // There is 1 IpServer state change event: STATE_AVAILABLE
        verify(mNotificationUpdater, times(1)).onDownstreamChanged(DOWNSTREAM_NONE);
        verifyTetheringBroadcast(TEST_WLAN_IFNAME, EXTRA_AVAILABLE_TETHER);
        verify(mWifiManager).updateInterfaceIpState(
                TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        verifyNoMoreInteractions(mNetd);
        verifyNoMoreInteractions(mWifiManager);
    }

    // TODO: Test with and without interfaceStatusChanged().
    @Test
    public void workingWifiTetheringEnrichedApBroadcast() throws Exception {
        initTetheringOnTestThread();
        when(mWifiManager.startTetheredHotspot(any(SoftApConfiguration.class))).thenReturn(true);

        // Emulate pressing the WiFi tethering button.
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_WIFI), TEST_CALLER_PKG,
                null);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).startTetheredHotspot(null);
        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mNetd);

        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // tethering mode is to be started.
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);

        verifyStartHotspot();
        verifyTetheringBroadcast(TEST_WLAN_IFNAME, EXTRA_ACTIVE_TETHER);
        // In tethering mode, in the default configuration, an explicit request
        // for a mobile network is also made.
        verify(mUpstreamNetworkMonitor, times(1)).setTryCell(true);

        /////
        // We do not currently emulate any upstream being found.
        //
        // This is why there are no calls to verify mNetd.tetherAddForward() or
        // mNetd.ipfwdAddInterfaceForward().
        /////

        // Emulate pressing the WiFi tethering button.
        mTethering.stopTethering(TETHERING_WIFI);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).stopSoftAp();
        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mNetd);

        // Emulate externally-visible WifiManager effects, when tethering mode
        // is being torn down.
        sendWifiApStateChanged(WIFI_AP_STATE_DISABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);
        mTethering.interfaceRemoved(TEST_WLAN_IFNAME);
        mLooper.dispatchAll();

        verifyStopHotpot();
    }

    // TODO: Test with and without interfaceStatusChanged().
    @Test
    public void failureEnablingIpForwarding() throws Exception {
        initTetheringOnTestThread();
        when(mWifiManager.startTetheredHotspot(any(SoftApConfiguration.class))).thenReturn(true);
        doThrow(new RemoteException()).when(mNetd).ipfwdEnableForwarding(TETHERING_NAME);

        // Emulate pressing the WiFi tethering button.
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_WIFI), TEST_CALLER_PKG,
                null);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).startTetheredHotspot(null);
        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mNetd);
        verify(mTetheringMetrics).createBuilder(eq(TETHERING_WIFI), anyString());

        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // tethering mode is to be started.
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);

        // We verify get/set called three times here: twice for setup and once during
        // teardown because all events happen over the course of the single
        // dispatchAll() above. Note that once the IpServer IPv4 address config
        // code is refactored the two calls during shutdown will revert to one.
        verify(mNetd, times(3)).interfaceSetCfg(argThat(p -> TEST_WLAN_IFNAME.equals(p.ifName)));
        verify(mNetd, times(1)).tetherInterfaceAdd(TEST_WLAN_IFNAME);
        verify(mNetd, times(1)).networkAddInterface(INetd.LOCAL_NET_ID, TEST_WLAN_IFNAME);
        verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(TEST_WLAN_IFNAME),
                anyString(), anyString());
        verify(mWifiManager).updateInterfaceIpState(
                TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        verify(mWifiManager).updateInterfaceIpState(
                TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_TETHERED);
        // There are 3 IpServer state change event:
        //         STATE_AVAILABLE -> STATE_TETHERED -> STATE_AVAILABLE.
        verify(mNotificationUpdater, times(2)).onDownstreamChanged(DOWNSTREAM_NONE);
        verify(mNotificationUpdater, times(1)).onDownstreamChanged(eq(1 << TETHERING_WIFI));
        verifyTetheringBroadcast(TEST_WLAN_IFNAME, EXTRA_AVAILABLE_TETHER);
        // This is called, but will throw.
        verify(mNetd, times(1)).ipfwdEnableForwarding(TETHERING_NAME);
        // This never gets called because of the exception thrown above.
        verify(mNetd, times(0)).tetherStartWithConfiguration(any());
        // When the main state machine transitions to an error state it tells
        // downstream interfaces, which causes us to tell Wi-Fi about the error
        // so it can take down AP mode.
        verify(mNetd, times(1)).tetherApplyDnsInterfaces();
        verify(mNetd, times(1)).tetherInterfaceRemove(TEST_WLAN_IFNAME);
        verify(mNetd, times(1)).networkRemoveInterface(INetd.LOCAL_NET_ID, TEST_WLAN_IFNAME);
        verify(mWifiManager).updateInterfaceIpState(
                TEST_WLAN_IFNAME, WifiManager.IFACE_IP_MODE_CONFIGURATION_ERROR);

        verify(mTetheringMetrics, times(0)).maybeUpdateUpstreamType(any());
        verify(mTetheringMetrics, times(2)).updateErrorCode(eq(TETHERING_WIFI),
                eq(TETHER_ERROR_INTERNAL_ERROR));
        verify(mTetheringMetrics, times(2)).sendReport(eq(TETHERING_WIFI));

        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mNetd);
    }

    private UserRestrictionActionListener makeUserRestrictionActionListener(
            final Tethering tethering, final boolean currentDisallow, final boolean nextDisallow) {
        final Bundle newRestrictions = new Bundle();
        newRestrictions.putBoolean(UserManager.DISALLOW_CONFIG_TETHERING, nextDisallow);
        when(mUserManager.getUserRestrictions()).thenReturn(newRestrictions);

        final UserRestrictionActionListener ural =
                new UserRestrictionActionListener(mUserManager, tethering, mNotificationUpdater);
        ural.mDisallowTethering = currentDisallow;
        return ural;
    }

    private void runUserRestrictionsChange(
            boolean currentDisallow, boolean nextDisallow, boolean isTetheringActive,
            int expectedInteractionsWithShowNotification) throws  Exception {
        final Tethering mockTethering = mock(Tethering.class);
        when(mockTethering.isTetheringActive()).thenReturn(isTetheringActive);

        final UserRestrictionActionListener ural =
                makeUserRestrictionActionListener(mockTethering, currentDisallow, nextDisallow);
        ural.onUserRestrictionsChanged();

        verify(mNotificationUpdater, times(expectedInteractionsWithShowNotification))
                .notifyTetheringDisabledByRestriction();
        verify(mockTethering, times(expectedInteractionsWithShowNotification)).untetherAll();
    }

    @Test
    public void testDisallowTetheringWhenTetheringIsNotActive() throws Exception {
        final boolean isTetheringActive = false;
        final boolean currDisallow = false;
        final boolean nextDisallow = true;
        final int expectedInteractionsWithShowNotification = 0;

        runUserRestrictionsChange(currDisallow, nextDisallow, isTetheringActive,
                expectedInteractionsWithShowNotification);
    }

    @Test
    public void testDisallowTetheringWhenTetheringIsActive() throws Exception {
        final boolean isTetheringActive = true;
        final boolean currDisallow = false;
        final boolean nextDisallow = true;
        final int expectedInteractionsWithShowNotification = 1;

        runUserRestrictionsChange(currDisallow, nextDisallow, isTetheringActive,
                expectedInteractionsWithShowNotification);
    }

    @Test
    public void testAllowTetheringWhenTetheringIsNotActive() throws Exception {
        final boolean isTetheringActive = false;
        final boolean currDisallow = true;
        final boolean nextDisallow = false;
        final int expectedInteractionsWithShowNotification = 0;

        runUserRestrictionsChange(currDisallow, nextDisallow, isTetheringActive,
                expectedInteractionsWithShowNotification);
    }

    @Test
    public void testAllowTetheringWhenTetheringIsActive() throws Exception {
        final boolean isTetheringActive = true;
        final boolean currDisallow = true;
        final boolean nextDisallow = false;
        final int expectedInteractionsWithShowNotification = 0;

        runUserRestrictionsChange(currDisallow, nextDisallow, isTetheringActive,
                expectedInteractionsWithShowNotification);
    }

    @Test
    public void testDisallowTetheringUnchanged() throws Exception {
        final boolean isTetheringActive = true;
        final int expectedInteractionsWithShowNotification = 0;
        boolean currDisallow = true;
        boolean nextDisallow = true;

        runUserRestrictionsChange(currDisallow, nextDisallow, isTetheringActive,
                expectedInteractionsWithShowNotification);

        currDisallow = false;
        nextDisallow = false;

        runUserRestrictionsChange(currDisallow, nextDisallow, isTetheringActive,
                expectedInteractionsWithShowNotification);
    }

    @Test
    public void testUntetherUsbWhenRestrictionIsOn() throws Exception {
        initTetheringOnTestThread();
        // Start usb tethering and check that usb interface is tethered.
        final UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        runUsbTethering(upstreamState);
        assertContains(Arrays.asList(mTethering.getTetheredIfaces()), TEST_RNDIS_IFNAME);
        assertTrue(mTethering.isTetheringActive());
        assertEquals(0, mTethering.getActiveTetheringRequests().size());

        final Tethering.UserRestrictionActionListener ural = makeUserRestrictionActionListener(
                mTethering, false /* currentDisallow */, true /* nextDisallow */);

        ural.onUserRestrictionsChanged();
        mLooper.dispatchAll();

        // Verify that restriction notification has showed to user.
        verify(mNotificationUpdater, times(1)).notifyTetheringDisabledByRestriction();
        // Verify that usb tethering has been disabled.
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_NONE);
    }

    private class TestTetheringEventCallback extends ITetheringEventCallback.Stub {
        private final ArrayList<Network> mActualUpstreams = new ArrayList<>();
        private final ArrayList<TetheringConfigurationParcel> mTetheringConfigs =
                new ArrayList<>();
        private final ArrayList<TetherStatesParcel> mTetherStates = new ArrayList<>();
        private final ArrayList<Integer> mOffloadStatus = new ArrayList<>();
        private final ArrayList<List<TetheredClient>> mTetheredClients = new ArrayList<>();
        private final ArrayList<Long> mSupportedBitmaps = new ArrayList<>();

        // This function will remove the recorded callbacks, so it must be called once for
        // each callback. If this is called after multiple callback, the order matters.
        // onCallbackCreated counts as the first call to expectUpstreamChanged with
        // @see onCallbackCreated.
        public void expectUpstreamChanged(Network... networks) {
            if (networks == null) {
                assertNoUpstreamChangeCallback();
                return;
            }

            final ArrayList<Network> expectedUpstreams =
                    new ArrayList<Network>(Arrays.asList(networks));
            for (Network upstream : expectedUpstreams) {
                // throws OOB if no expectations
                assertEquals(upstream, mActualUpstreams.remove(0));
            }
            assertNoUpstreamChangeCallback();
        }

        // This function will remove the recorded callbacks, so it must be called once
        // for each callback. If this is called after multiple callback, the order matters.
        // onCallbackCreated counts as the first call to onConfigurationChanged with
        // @see onCallbackCreated.
        public void expectConfigurationChanged(TetheringConfigurationParcel... tetherConfigs) {
            final ArrayList<TetheringConfigurationParcel> expectedTetherConfig =
                    new ArrayList<TetheringConfigurationParcel>(Arrays.asList(tetherConfigs));
            for (TetheringConfigurationParcel config : expectedTetherConfig) {
                // throws OOB if no expectations
                final TetheringConfigurationParcel actualConfig = mTetheringConfigs.remove(0);
                assertTetherConfigParcelEqual(config, actualConfig);
            }
            assertNoConfigChangeCallback();
        }

        public void expectOffloadStatusChanged(final int expectedStatus) {
            assertOffloadStatusChangedCallback();
            assertEquals(Integer.valueOf(expectedStatus), mOffloadStatus.remove(0));
        }

        public TetherStatesParcel pollTetherStatesChanged() {
            assertStateChangeCallback();
            return mTetherStates.remove(0);
        }

        public void expectTetheredClientChanged(List<TetheredClient> leases) {
            assertFalse(mTetheredClients.isEmpty());
            final List<TetheredClient> result = mTetheredClients.remove(0);
            assertEquals(leases.size(), result.size());
            assertTrue(leases.containsAll(result));
        }

        public void expectSupportedTetheringTypes(Set<Integer> expectedTypes) {
            assertEquals(expectedTypes, TetheringManager.unpackBits(mSupportedBitmaps.remove(0)));
        }

        @Override
        public void onUpstreamChanged(Network network) {
            mActualUpstreams.add(network);
        }

        @Override
        public void onConfigurationChanged(TetheringConfigurationParcel config) {
            mTetheringConfigs.add(config);
        }

        @Override
        public void onTetherStatesChanged(TetherStatesParcel states) {
            mTetherStates.add(states);
        }

        @Override
        public void onTetherClientsChanged(List<TetheredClient> clients) {
            mTetheredClients.add(clients);
        }

        @Override
        public void onOffloadStatusChanged(final int status) {
            mOffloadStatus.add(status);
        }

        @Override
        public void onCallbackStarted(TetheringCallbackStartedParcel parcel) {
            mActualUpstreams.add(parcel.upstreamNetwork);
            mTetheringConfigs.add(parcel.config);
            mTetherStates.add(parcel.states);
            mOffloadStatus.add(parcel.offloadStatus);
            mTetheredClients.add(parcel.tetheredClients);
            mSupportedBitmaps.add(parcel.supportedTypes);
        }

        @Override
        public void onCallbackStopped(int errorCode) { }

        @Override
        public void onSupportedTetheringTypes(long supportedBitmap) {
            mSupportedBitmaps.add(supportedBitmap);
        }

        public void assertNoUpstreamChangeCallback() {
            assertTrue(mActualUpstreams.isEmpty());
        }

        public void assertNoConfigChangeCallback() {
            assertTrue(mTetheringConfigs.isEmpty());
        }

        public void assertNoStateChangeCallback() {
            assertTrue(mTetherStates.isEmpty());
        }

        public void assertStateChangeCallback() {
            assertFalse(mTetherStates.isEmpty());
        }

        public void assertOffloadStatusChangedCallback() {
            assertFalse(mOffloadStatus.isEmpty());
        }

        public void assertNoCallback() {
            assertNoUpstreamChangeCallback();
            assertNoConfigChangeCallback();
            assertNoStateChangeCallback();
            assertTrue(mTetheredClients.isEmpty());
        }

        private void assertTetherConfigParcelEqual(@NonNull TetheringConfigurationParcel actual,
                @NonNull TetheringConfigurationParcel expect) {
            assertArrayEquals(expect.tetherableUsbRegexs, actual.tetherableUsbRegexs);
            assertArrayEquals(expect.tetherableWifiRegexs, actual.tetherableWifiRegexs);
            assertArrayEquals(expect.tetherableBluetoothRegexs, actual.tetherableBluetoothRegexs);
            assertArrayEquals(expect.legacyDhcpRanges, actual.legacyDhcpRanges);
            assertArrayEquals(expect.provisioningApp, actual.provisioningApp);
            assertEquals(expect.provisioningAppNoUi, actual.provisioningAppNoUi);
        }
    }

    private void assertTetherStatesNotNullButEmpty(final TetherStatesParcel parcel) {
        assertFalse(parcel == null);
        assertEquals(0, parcel.availableList.length);
        assertEquals(0, parcel.tetheredList.length);
        assertEquals(0, parcel.localOnlyList.length);
        assertEquals(0, parcel.erroredIfaceList.length);
        assertEquals(0, parcel.lastErrorList.length);
        MiscAsserts.assertFieldCountEquals(5, TetherStatesParcel.class);
    }

    @Test
    public void testRegisterTetheringEventCallback() throws Exception {
        initTetheringOnTestThread();
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        TestTetheringEventCallback callback2 = new TestTetheringEventCallback();
        final TetheringInterface wifiIface = new TetheringInterface(
                TETHERING_WIFI, TEST_WLAN_IFNAME);

        // 1. Register one callback before running any tethering.
        mTethering.registerTetheringEventCallback(callback);
        mLooper.dispatchAll();
        callback.expectTetheredClientChanged(Collections.emptyList());
        callback.expectUpstreamChanged(NULL_NETWORK);
        callback.expectConfigurationChanged(
                mTethering.getTetheringConfiguration().toStableParcelable());
        TetherStatesParcel tetherState = callback.pollTetherStatesChanged();
        assertTetherStatesNotNullButEmpty(tetherState);
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STOPPED);
        // 2. Enable wifi tethering.
        UpstreamNetworkState upstreamState = buildMobileDualStackUpstreamState();
        initTetheringUpstream(upstreamState);
        when(mWifiManager.startTetheredHotspot(any(SoftApConfiguration.class))).thenReturn(true);
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        mLooper.dispatchAll();
        tetherState = callback.pollTetherStatesChanged();
        assertArrayEquals(tetherState.availableList, new TetheringInterface[] {wifiIface});

        mTethering.startTethering(createTetheringRequestParcel(TETHERING_WIFI), TEST_CALLER_PKG,
                null);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);
        tetherState = callback.pollTetherStatesChanged();
        assertArrayEquals(tetherState.tetheredList, new TetheringInterface[] {wifiIface});
        callback.expectUpstreamChanged(upstreamState.network);
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STARTED);

        // 3. Register second callback.
        mTethering.registerTetheringEventCallback(callback2);
        mLooper.dispatchAll();
        callback2.expectTetheredClientChanged(Collections.emptyList());
        callback2.expectUpstreamChanged(upstreamState.network);
        callback2.expectConfigurationChanged(
                mTethering.getTetheringConfiguration().toStableParcelable());
        tetherState = callback2.pollTetherStatesChanged();
        assertEquals(tetherState.tetheredList, new TetheringInterface[] {wifiIface});
        callback2.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STARTED);

        // 4. Unregister first callback and disable wifi tethering
        mTethering.unregisterTetheringEventCallback(callback);
        mLooper.dispatchAll();
        mTethering.stopTethering(TETHERING_WIFI);
        sendWifiApStateChanged(WIFI_AP_STATE_DISABLED);
        if (isAtLeastT()) {
            // After T, tethering doesn't support WIFI_AP_STATE_DISABLED with null interface name.
            callback2.assertNoStateChangeCallback();
            sendWifiApStateChanged(WIFI_AP_STATE_DISABLED, TEST_WLAN_IFNAME,
                    IFACE_IP_MODE_TETHERED);
        }
        tetherState = callback2.pollTetherStatesChanged();
        assertArrayEquals(tetherState.availableList, new TetheringInterface[] {wifiIface});
        mLooper.dispatchAll();
        callback2.expectUpstreamChanged(NULL_NETWORK);
        callback2.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STOPPED);
        callback.assertNoCallback();
    }

    @Test
    public void testReportFailCallbackIfOffloadNotSupported() throws Exception {
        initTetheringOnTestThread();
        final UpstreamNetworkState upstreamState = buildMobileDualStackUpstreamState();
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        mTethering.registerTetheringEventCallback(callback);
        mLooper.dispatchAll();
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STOPPED);

        // 1. Offload fail if no IOffloadHal.
        initOffloadConfiguration(OFFLOAD_HAL_VERSION_NONE, 0 /* defaultDisabled */);
        runUsbTethering(upstreamState);
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_FAILED);
        runStopUSBTethering();
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STOPPED);
        reset(mUsbManager, mIPv6TetheringCoordinator);
        // 2. Offload fail if disabled by settings.
        initOffloadConfiguration(OFFLOAD_HAL_VERSION_HIDL_1_0, 1 /* defaultDisabled */);
        runUsbTethering(upstreamState);
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_FAILED);
        runStopUSBTethering();
        callback.expectOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STOPPED);
    }

    private void runStopUSBTethering() {
        mTethering.stopTethering(TETHERING_USB);
        mLooper.dispatchAll();
        sendUsbBroadcast(true, true, -1 /* function */);
        mLooper.dispatchAll();
        verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_NONE);
    }

    private void initOffloadConfiguration(
            @OffloadHardwareInterface.OffloadHalVersion final int offloadHalVersion,
            final int defaultDisabled) {
        when(mOffloadHardwareInterface.initOffload(any())).thenReturn(offloadHalVersion);
        when(mOffloadHardwareInterface.getDefaultTetherOffloadDisabled()).thenReturn(
                defaultDisabled);
    }

    @Test
    public void testMultiSimAware() throws Exception {
        initTetheringOnTestThread();
        final TetheringConfiguration initailConfig = mTethering.getTetheringConfiguration();
        assertEquals(INVALID_SUBSCRIPTION_ID, initailConfig.activeDataSubId);

        final int fakeSubId = 1234;
        mPhoneStateListener.onActiveDataSubscriptionIdChanged(fakeSubId);
        final TetheringConfiguration newConfig = mTethering.getTetheringConfiguration();
        assertEquals(fakeSubId, newConfig.activeDataSubId);
        verify(mNotificationUpdater, times(1)).onActiveDataSubscriptionIdChanged(eq(fakeSubId));
    }

    @Test
    public void testNoDuplicatedEthernetRequest() throws Exception {
        initTetheringOnTestThread();
        final TetheredInterfaceRequest mockRequest = mock(TetheredInterfaceRequest.class);
        when(mEm.requestTetheredInterface(any(), any())).thenReturn(mockRequest);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_ETHERNET), TEST_CALLER_PKG,
                null);
        mLooper.dispatchAll();
        verify(mEm, times(1)).requestTetheredInterface(any(), any());
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_ETHERNET), TEST_CALLER_PKG,
                null);
        mLooper.dispatchAll();
        verifyNoMoreInteractions(mEm);
        mTethering.stopTethering(TETHERING_ETHERNET);
        mLooper.dispatchAll();
        verify(mockRequest, times(1)).release();
        mTethering.stopTethering(TETHERING_ETHERNET);
        mLooper.dispatchAll();
        verifyNoMoreInteractions(mEm);
    }

    private void workingWifiP2pGroupOwner(
            boolean emulateInterfaceStatusChanged) throws Exception {
        initTetheringOnTestThread();
        if (emulateInterfaceStatusChanged) {
            mTethering.interfaceStatusChanged(TEST_P2P_IFNAME, true);
        }
        sendWifiP2pConnectionChanged(true, true, TEST_P2P_IFNAME);

        verifyInterfaceServingModeStarted(TEST_P2P_IFNAME);
        verifyTetheringBroadcast(TEST_P2P_IFNAME, EXTRA_AVAILABLE_TETHER);
        verify(mNetd, times(1)).ipfwdEnableForwarding(TETHERING_NAME);
        verify(mNetd, times(1)).tetherStartWithConfiguration(any());
        verifyNoMoreInteractions(mNetd);
        verifyTetheringBroadcast(TEST_P2P_IFNAME, EXTRA_ACTIVE_LOCAL_ONLY);
        verify(mUpstreamNetworkMonitor, times(1)).startObserveAllNetworks();
        // There are 2 IpServer state change events: STATE_AVAILABLE -> STATE_LOCAL_ONLY
        verify(mNotificationUpdater, times(2)).onDownstreamChanged(DOWNSTREAM_NONE);

        assertEquals(TETHER_ERROR_NO_ERROR, mTethering.getLastErrorForTest(TEST_P2P_IFNAME));

        // Emulate externally-visible WifiP2pManager effects, when wifi p2p group
        // is being removed.
        sendWifiP2pConnectionChanged(false, true, TEST_P2P_IFNAME);
        mTethering.interfaceRemoved(TEST_P2P_IFNAME);

        verify(mNetd, times(1)).tetherApplyDnsInterfaces();
        verify(mNetd, times(1)).tetherInterfaceRemove(TEST_P2P_IFNAME);
        verify(mNetd, times(1)).networkRemoveInterface(INetd.LOCAL_NET_ID, TEST_P2P_IFNAME);
        // interfaceSetCfg() called once for enabling and twice for disabling IPv4.
        verify(mNetd, times(3)).interfaceSetCfg(any(InterfaceConfigurationParcel.class));
        verify(mNetd, times(1)).tetherStop();
        verify(mNetd, times(1)).ipfwdDisableForwarding(TETHERING_NAME);
        verify(mUpstreamNetworkMonitor, never()).getCurrentPreferredUpstream();
        verify(mUpstreamNetworkMonitor, never()).selectPreferredUpstreamType(any());
        verifyNoMoreInteractions(mNetd);
        // Asking for the last error after the per-interface state machine
        // has been reaped yields an unknown interface error.
        assertEquals(TETHER_ERROR_UNKNOWN_IFACE, mTethering.getLastErrorForTest(TEST_P2P_IFNAME));
    }

    private void workingWifiP2pGroupClient(
            boolean emulateInterfaceStatusChanged) throws Exception {
        initTetheringOnTestThread();
        if (emulateInterfaceStatusChanged) {
            mTethering.interfaceStatusChanged(TEST_P2P_IFNAME, true);
        }
        sendWifiP2pConnectionChanged(true, false, TEST_P2P_IFNAME);

        verify(mNetd, never()).interfaceSetCfg(any(InterfaceConfigurationParcel.class));
        verify(mNetd, never()).tetherInterfaceAdd(TEST_P2P_IFNAME);
        verify(mNetd, never()).networkAddInterface(INetd.LOCAL_NET_ID, TEST_P2P_IFNAME);
        verify(mNetd, never()).ipfwdEnableForwarding(TETHERING_NAME);
        verify(mNetd, never()).tetherStartWithConfiguration(any());

        // Emulate externally-visible WifiP2pManager effects, when wifi p2p group
        // is being removed.
        sendWifiP2pConnectionChanged(false, false, TEST_P2P_IFNAME);
        mTethering.interfaceRemoved(TEST_P2P_IFNAME);

        verify(mNetd, never()).tetherApplyDnsInterfaces();
        verify(mNetd, never()).tetherInterfaceRemove(TEST_P2P_IFNAME);
        verify(mNetd, never()).networkRemoveInterface(INetd.LOCAL_NET_ID, TEST_P2P_IFNAME);
        verify(mNetd, never()).interfaceSetCfg(any(InterfaceConfigurationParcel.class));
        verify(mNetd, never()).tetherStop();
        verify(mNetd, never()).ipfwdDisableForwarding(TETHERING_NAME);
        verifyNoMoreInteractions(mNetd);
        // Asking for the last error after the per-interface state machine
        // has been reaped yields an unknown interface error.
        assertEquals(TETHER_ERROR_UNKNOWN_IFACE, mTethering.getLastErrorForTest(TEST_P2P_IFNAME));
    }

    @Test
    public void workingWifiP2pGroupOwnerWithIfaceChanged() throws Exception {
        workingWifiP2pGroupOwner(true);
    }

    @Test
    public void workingWifiP2pGroupOwnerSansIfaceChanged() throws Exception {
        workingWifiP2pGroupOwner(false);
    }

    private void workingWifiP2pGroupOwnerLegacyMode(
            boolean emulateInterfaceStatusChanged) throws Exception {
        initTetheringOnTestThread();
        // change to legacy mode and update tethering information by chaning SIM
        when(mResources.getStringArray(R.array.config_tether_wifi_p2p_regexs))
                .thenReturn(new String[]{});
        final int fakeSubId = 1234;
        mPhoneStateListener.onActiveDataSubscriptionIdChanged(fakeSubId);

        if (emulateInterfaceStatusChanged) {
            mTethering.interfaceStatusChanged(TEST_P2P_IFNAME, true);
        }
        sendWifiP2pConnectionChanged(true, true, TEST_P2P_IFNAME);

        verify(mNetd, never()).interfaceSetCfg(any(InterfaceConfigurationParcel.class));
        verify(mNetd, never()).tetherInterfaceAdd(TEST_P2P_IFNAME);
        verify(mNetd, never()).networkAddInterface(INetd.LOCAL_NET_ID, TEST_P2P_IFNAME);
        verify(mNetd, never()).ipfwdEnableForwarding(TETHERING_NAME);
        verify(mNetd, never()).tetherStartWithConfiguration(any());
        assertEquals(TETHER_ERROR_UNKNOWN_IFACE, mTethering.getLastErrorForTest(TEST_P2P_IFNAME));
    }
    @Test
    public void workingWifiP2pGroupOwnerLegacyModeWithIfaceChanged() throws Exception {
        workingWifiP2pGroupOwnerLegacyMode(true);
    }

    @Test
    public void workingWifiP2pGroupOwnerLegacyModeSansIfaceChanged() throws Exception {
        workingWifiP2pGroupOwnerLegacyMode(false);
    }

    @Test
    public void workingWifiP2pGroupClientWithIfaceChanged() throws Exception {
        workingWifiP2pGroupClient(true);
    }

    @Test
    public void workingWifiP2pGroupClientSansIfaceChanged() throws Exception {
        workingWifiP2pGroupClient(false);
    }

    private void setDataSaverEnabled(boolean enabled) {
        final int status = enabled ? RESTRICT_BACKGROUND_STATUS_ENABLED
                : RESTRICT_BACKGROUND_STATUS_DISABLED;
        doReturn(status).when(mCm).getRestrictBackgroundStatus();

        final Intent intent = new Intent(ACTION_RESTRICT_BACKGROUND_CHANGED);
        mServiceContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        mLooper.dispatchAll();
    }

    @Test
    public void testDataSaverChanged() throws Exception {
        initTetheringOnTestThread();
        // Start Tethering.
        final UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        runUsbTethering(upstreamState);
        assertContains(Arrays.asList(mTethering.getTetheredIfaces()), TEST_RNDIS_IFNAME);
        // Data saver is ON.
        setDataSaverEnabled(true);
        // Verify that tethering should be disabled.
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        sendUsbBroadcast(true, true, -1 /* function */);
        mLooper.dispatchAll();
        assertEquals(mTethering.getTetheredIfaces(), new String[0]);
        reset(mUsbManager, mIPv6TetheringCoordinator);

        runUsbTethering(upstreamState);
        // Verify that user can start tethering again without turning OFF data saver.
        assertContains(Arrays.asList(mTethering.getTetheredIfaces()), TEST_RNDIS_IFNAME);

        // If data saver is keep ON with change event, tethering should not be OFF this time.
        setDataSaverEnabled(true);
        verify(mUsbManager, times(0)).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        assertContains(Arrays.asList(mTethering.getTetheredIfaces()), TEST_RNDIS_IFNAME);

        // If data saver is turned OFF, it should not change tethering.
        setDataSaverEnabled(false);
        verify(mUsbManager, times(0)).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        assertContains(Arrays.asList(mTethering.getTetheredIfaces()), TEST_RNDIS_IFNAME);
    }

    private static <T> void assertContains(Collection<T> collection, T element) {
        assertTrue(element + " not found in " + collection, collection.contains(element));
    }

    private class ResultListener extends IIntResultListener.Stub {
        private final int mExpectedResult;
        private boolean mHasResult = false;
        ResultListener(final int expectedResult) {
            mExpectedResult = expectedResult;
        }

        @Override
        public void onResult(final int resultCode) {
            mHasResult = true;
            if (resultCode != mExpectedResult) {
                fail("expected result: " + mExpectedResult + " but actual result: " + resultCode);
            }
        }

        public void assertHasResult() {
            if (!mHasResult) fail("No callback result");
        }
    }

    @Test
    public void testMultipleStartTethering() throws Exception {
        initTetheringOnTestThread();
        final LinkAddress serverLinkAddr = new LinkAddress("192.168.20.1/24");
        final LinkAddress clientLinkAddr = new LinkAddress("192.168.20.42/24");
        final String serverAddr = "192.168.20.1";
        final ResultListener firstResult = new ResultListener(TETHER_ERROR_NO_ERROR);
        final ResultListener secondResult = new ResultListener(TETHER_ERROR_NO_ERROR);
        final ResultListener thirdResult = new ResultListener(TETHER_ERROR_NO_ERROR);

        // Enable USB tethering and check that Tethering starts USB.
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_USB), TEST_CALLER_PKG,
                firstResult);
        mLooper.dispatchAll();
        firstResult.assertHasResult();
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_RNDIS);
        verifyNoMoreInteractions(mUsbManager);

        // Enable USB tethering again with the same request and expect no change to USB.
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_USB), TEST_CALLER_PKG,
                secondResult);
        mLooper.dispatchAll();
        secondResult.assertHasResult();
        verify(mUsbManager, never()).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        reset(mUsbManager);

        // Enable USB tethering with a different request and expect that USB is stopped and
        // started.
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_USB,
                  serverLinkAddr, clientLinkAddr, false, CONNECTIVITY_SCOPE_GLOBAL),
                  TEST_CALLER_PKG, thirdResult);
        mLooper.dispatchAll();
        thirdResult.assertHasResult();
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_RNDIS);

        // Expect that when USB comes up, the DHCP server is configured with the requested address.
        mTethering.interfaceStatusChanged(TEST_RNDIS_IFNAME, true);
        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
        verify(mNetd).interfaceSetCfg(argThat(cfg -> serverAddr.equals(cfg.ipv4Addr)));
    }

    @Test
    public void testRequestStaticIp() throws Exception {
        initTetheringOnTestThread();
        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TetheringConfiguration.TETHER_USB_NCM_FUNCTION);
        when(mResources.getStringArray(R.array.config_tether_usb_regexs))
                .thenReturn(new String[] {TEST_NCM_REGEX});
        sendConfigurationChanged();

        final LinkAddress serverLinkAddr = new LinkAddress("192.168.0.123/24");
        final LinkAddress clientLinkAddr = new LinkAddress("192.168.0.42/24");
        final String serverAddr = "192.168.0.123";
        final int clientAddrParceled = 0xc0a8002a;
        final ArgumentCaptor<DhcpServingParamsParcel> dhcpParamsCaptor =
                ArgumentCaptor.forClass(DhcpServingParamsParcel.class);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_USB,
                  serverLinkAddr, clientLinkAddr, false, CONNECTIVITY_SCOPE_GLOBAL),
                  TEST_CALLER_PKG, null);
        mLooper.dispatchAll();
        verify(mUsbManager, times(1)).setCurrentFunctions(UsbManager.FUNCTION_NCM);
        mTethering.interfaceStatusChanged(TEST_NCM_IFNAME, true);
        sendUsbBroadcast(true, true, TETHER_USB_NCM_FUNCTION);
        verify(mNetd).interfaceSetCfg(argThat(cfg -> serverAddr.equals(cfg.ipv4Addr)));
        verify(mIpServerDependencies, times(1)).makeDhcpServer(any(), dhcpParamsCaptor.capture(),
                any());
        final DhcpServingParamsParcel params = dhcpParamsCaptor.getValue();
        assertEquals(serverAddr, intToInet4AddressHTH(params.serverAddr).getHostAddress());
        assertEquals(24, params.serverAddrPrefixLength);
        assertEquals(clientAddrParceled, params.singleClientAddr);
    }

    @Test
    public void testUpstreamNetworkChanged() throws Exception {
        initTetheringOnTestThread();
        final InOrder inOrder = inOrder(mNotificationUpdater);

        // Gain upstream.
        final UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        initTetheringUpstream(upstreamState);
        mTetherMainSM.chooseUpstreamType(true);
        mTetheringEventCallback.expectUpstreamChanged(upstreamState.network);
        inOrder.verify(mNotificationUpdater)
                .onUpstreamCapabilitiesChanged(upstreamState.networkCapabilities);

        // Set the upstream with the same network ID but different object and the same capability.
        final UpstreamNetworkState upstreamState2 = buildMobileIPv4UpstreamState();
        initTetheringUpstream(upstreamState2);
        mTetherMainSM.chooseUpstreamType(true);
        // Expect that no upstream change event and capabilities changed event.
        mTetheringEventCallback.assertNoUpstreamChangeCallback();
        inOrder.verify(mNotificationUpdater, never()).onUpstreamCapabilitiesChanged(any());

        // Set the upstream with the same network ID but different object and different capability.
        final UpstreamNetworkState upstreamState3 = buildMobileIPv4UpstreamState();
        assertFalse(upstreamState3.networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED));
        upstreamState3.networkCapabilities.addCapability(NET_CAPABILITY_VALIDATED);
        initTetheringUpstream(upstreamState3);
        mTetherMainSM.chooseUpstreamType(true);
        // Expect that no upstream change event and capabilities changed event.
        mTetheringEventCallback.assertNoUpstreamChangeCallback();
        mTetherMainSM.handleUpstreamNetworkMonitorCallback(EVENT_ON_CAPABILITIES, upstreamState3);
        inOrder.verify(mNotificationUpdater)
                .onUpstreamCapabilitiesChanged(upstreamState3.networkCapabilities);


        // Lose upstream.
        initTetheringUpstream(null);
        mTetherMainSM.chooseUpstreamType(true);
        mTetheringEventCallback.expectUpstreamChanged(NULL_NETWORK);
        inOrder.verify(mNotificationUpdater).onUpstreamCapabilitiesChanged(null);
    }

    @Test
    public void testUpstreamCapabilitiesChanged() throws Exception {
        initTetheringOnTestThread();
        final InOrder inOrder = inOrder(mNotificationUpdater);
        final UpstreamNetworkState upstreamState = buildMobileIPv4UpstreamState();
        initTetheringUpstream(upstreamState);

        mTetherMainSM.chooseUpstreamType(true);
        inOrder.verify(mNotificationUpdater)
                .onUpstreamCapabilitiesChanged(upstreamState.networkCapabilities);

        mTetherMainSM.handleUpstreamNetworkMonitorCallback(EVENT_ON_CAPABILITIES, upstreamState);
        inOrder.verify(mNotificationUpdater)
                .onUpstreamCapabilitiesChanged(upstreamState.networkCapabilities);

        // Verify that onUpstreamCapabilitiesChanged is called if current upstream network
        // capabilities changed.
        // Expect that capability is changed with new capability VALIDATED.
        assertFalse(upstreamState.networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED));
        upstreamState.networkCapabilities.addCapability(NET_CAPABILITY_VALIDATED);
        mTetherMainSM.handleUpstreamNetworkMonitorCallback(EVENT_ON_CAPABILITIES, upstreamState);
        inOrder.verify(mNotificationUpdater)
                .onUpstreamCapabilitiesChanged(upstreamState.networkCapabilities);

        // Verify that onUpstreamCapabilitiesChanged won't be called if not current upstream network
        // capabilities changed.
        final UpstreamNetworkState upstreamState2 = new UpstreamNetworkState(
                upstreamState.linkProperties, upstreamState.networkCapabilities,
                new Network(WIFI_NETID));
        mTetherMainSM.handleUpstreamNetworkMonitorCallback(EVENT_ON_CAPABILITIES, upstreamState2);
        inOrder.verify(mNotificationUpdater, never()).onUpstreamCapabilitiesChanged(any());
    }

    @Test
    public void testUpstreamCapabilitiesChanged_startStopTethering() throws Exception {
        initTetheringOnTestThread();
        final TestNetworkAgent wifi = new TestNetworkAgent(mCm, buildWifiUpstreamState());

        // Start USB tethering with no current upstream.
        prepareUsbTethering();
        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);

        // Pretend wifi connected and expect the upstream to be set.
        wifi.fakeConnect();
        mCm.makeDefaultNetwork(wifi, CALLBACKS_FIRST);
        mLooper.dispatchAll();
        verify(mNotificationUpdater).onUpstreamCapabilitiesChanged(
                wifi.networkCapabilities);

        // Stop tethering.
        // Expect that TetherModeAliveState#exit sends capabilities change notification to null.
        runStopUSBTethering();
        verify(mNotificationUpdater).onUpstreamCapabilitiesChanged(null);
    }

    @Test
    public void testDumpTetheringLog() throws Exception {
        initTetheringOnTestThread();
        final FileDescriptor mockFd = mock(FileDescriptor.class);
        final PrintWriter mockPw = mock(PrintWriter.class);
        runUsbTethering(null);
        mTethering.dump(mockFd, mockPw, new String[0]);
        verify(mConfig).dump(any());
        verify(mEntitleMgr).dump(any());
        verify(mOffloadCtrl).dump(any());
    }

    @Test
    public void testExemptFromEntitlementCheck() throws Exception {
        initTetheringOnTestThread();
        setupForRequiredProvisioning();
        final TetheringRequestParcel wifiNotExemptRequest =
                createTetheringRequestParcel(TETHERING_WIFI, null, null, false,
                        CONNECTIVITY_SCOPE_GLOBAL);
        mTethering.startTethering(wifiNotExemptRequest, TEST_CALLER_PKG, null);
        mLooper.dispatchAll();
        verify(mEntitleMgr).startProvisioningIfNeeded(TETHERING_WIFI, false);
        verify(mEntitleMgr, never()).setExemptedDownstreamType(TETHERING_WIFI);
        assertFalse(mEntitleMgr.isCellularUpstreamPermitted());
        mTethering.stopTethering(TETHERING_WIFI);
        mLooper.dispatchAll();
        verify(mEntitleMgr).stopProvisioningIfNeeded(TETHERING_WIFI);
        reset(mEntitleMgr);

        setupForRequiredProvisioning();
        final TetheringRequestParcel wifiExemptRequest =
                createTetheringRequestParcel(TETHERING_WIFI, null, null, true,
                        CONNECTIVITY_SCOPE_GLOBAL);
        mTethering.startTethering(wifiExemptRequest, TEST_CALLER_PKG, null);
        mLooper.dispatchAll();
        verify(mEntitleMgr, never()).startProvisioningIfNeeded(TETHERING_WIFI, false);
        verify(mEntitleMgr).setExemptedDownstreamType(TETHERING_WIFI);
        assertTrue(mEntitleMgr.isCellularUpstreamPermitted());
        mTethering.stopTethering(TETHERING_WIFI);
        mLooper.dispatchAll();
        verify(mEntitleMgr).stopProvisioningIfNeeded(TETHERING_WIFI);
        reset(mEntitleMgr);

        // If one app enables tethering without provisioning check first, then another app enables
        // tethering of the same type but does not disable the provisioning check.
        setupForRequiredProvisioning();
        mTethering.startTethering(wifiExemptRequest, TEST_CALLER_PKG, null);
        mLooper.dispatchAll();
        verify(mEntitleMgr, never()).startProvisioningIfNeeded(TETHERING_WIFI, false);
        verify(mEntitleMgr).setExemptedDownstreamType(TETHERING_WIFI);
        assertTrue(mEntitleMgr.isCellularUpstreamPermitted());
        reset(mEntitleMgr);
        setupForRequiredProvisioning();
        mTethering.startTethering(wifiNotExemptRequest, TEST_CALLER_PKG, null);
        mLooper.dispatchAll();
        verify(mEntitleMgr).startProvisioningIfNeeded(TETHERING_WIFI, false);
        verify(mEntitleMgr, never()).setExemptedDownstreamType(TETHERING_WIFI);
        assertFalse(mEntitleMgr.isCellularUpstreamPermitted());
        mTethering.stopTethering(TETHERING_WIFI);
        mLooper.dispatchAll();
        verify(mEntitleMgr).stopProvisioningIfNeeded(TETHERING_WIFI);
        reset(mEntitleMgr);
    }

    private void setupForRequiredProvisioning() {
        // Produce some acceptable looking provision app setting if requested.
        when(mResources.getStringArray(R.array.config_mobile_hotspot_provision_app))
                .thenReturn(PROVISIONING_APP_NAME);
        when(mResources.getString(R.string.config_mobile_hotspot_provision_app_no_ui))
                .thenReturn(PROVISIONING_NO_UI_APP_NAME);
        // Act like the CarrierConfigManager is present and ready unless told otherwise.
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(mCarrierConfigManager);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mCarrierConfig);
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL, true);
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        sendConfigurationChanged();
    }

    private static UpstreamNetworkState buildV4UpstreamState(final LinkAddress address,
            final Network network, final String iface, final int transportType) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(iface);

        prop.addLinkAddress(address);

        final NetworkCapabilities capabilities = new NetworkCapabilities()
                .addTransportType(transportType);
        return new UpstreamNetworkState(prop, capabilities, network);
    }

    private void updateV4Upstream(final LinkAddress ipv4Address, final Network network,
            final String iface, final int transportType) {
        final UpstreamNetworkState upstream = buildV4UpstreamState(ipv4Address, network, iface,
                transportType);
        mEventListener.onUpstreamEvent(UpstreamNetworkMonitor.EVENT_ON_LINKPROPERTIES, upstream);
        mLooper.dispatchAll();
    }

    @Test
    public void testHandleIpConflict() throws Exception {
        initTetheringOnTestThread();
        final Network wifiNetwork = new Network(200);
        final Network[] allNetworks = { wifiNetwork };
        doReturn(allNetworks).when(mCm).getAllNetworks();
        InOrder inOrder = inOrder(mUsbManager, mNetd);
        runUsbTethering(null);

        inOrder.verify(mNetd).tetherInterfaceAdd(TEST_RNDIS_IFNAME);

        final ArgumentCaptor<InterfaceConfigurationParcel> ifaceConfigCaptor =
                ArgumentCaptor.forClass(InterfaceConfigurationParcel.class);
        verify(mNetd).interfaceSetCfg(ifaceConfigCaptor.capture());
        final String ipv4Address = ifaceConfigCaptor.getValue().ipv4Addr;
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());

        // Cause a prefix conflict by assigning a /30 out of the downstream's /24 to the upstream.
        updateV4Upstream(new LinkAddress(InetAddresses.parseNumericAddress(ipv4Address), 30),
                wifiNetwork, TEST_WIFI_IFNAME, TRANSPORT_WIFI);
        // verify turn off usb tethering
        inOrder.verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        sendUsbBroadcast(true, true, -1 /* function */);
        mLooper.dispatchAll();
        inOrder.verify(mNetd).tetherInterfaceRemove(TEST_RNDIS_IFNAME);

        // verify restart usb tethering
        inOrder.verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_RNDIS);

        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
        mLooper.dispatchAll();
        inOrder.verify(mNetd).tetherInterfaceAdd(TEST_RNDIS_IFNAME);
    }

    @Test
    public void testNoAddressAvailable() throws Exception {
        initTetheringOnTestThread();
        final Network wifiNetwork = new Network(200);
        final Network btNetwork = new Network(201);
        final Network mobileNetwork = new Network(202);
        final Network[] allNetworks = { wifiNetwork, btNetwork, mobileNetwork };
        doReturn(allNetworks).when(mCm).getAllNetworks();
        runUsbTethering(null);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
        reset(mUsbManager);
        final TetheredInterfaceRequest mockRequest = mock(TetheredInterfaceRequest.class);
        when(mEm.requestTetheredInterface(any(), any())).thenReturn(mockRequest);
        final ArgumentCaptor<TetheredInterfaceCallback> callbackCaptor =
                ArgumentCaptor.forClass(TetheredInterfaceCallback.class);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_ETHERNET),
                TEST_CALLER_PKG, null);
        mLooper.dispatchAll();
        verify(mEm).requestTetheredInterface(any(), callbackCaptor.capture());
        TetheredInterfaceCallback ethCallback = callbackCaptor.getValue();
        ethCallback.onAvailable(TEST_ETH_IFNAME);
        mLooper.dispatchAll();
        reset(mUsbManager, mEm);

        updateV4Upstream(new LinkAddress("192.168.0.100/16"), wifiNetwork, TEST_WIFI_IFNAME,
                TRANSPORT_WIFI);
        updateV4Upstream(new LinkAddress("172.16.0.0/12"), btNetwork, TEST_BT_IFNAME,
                TRANSPORT_BLUETOOTH);
        updateV4Upstream(new LinkAddress("10.0.0.0/8"), mobileNetwork, TEST_MOBILE_IFNAME,
                TRANSPORT_CELLULAR);

        mLooper.dispatchAll();
        // verify turn off usb tethering
        verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        // verify turn off ethernet tethering
        verify(mockRequest).release();
        sendUsbBroadcast(true, true, -1 /* function */);
        ethCallback.onUnavailable();
        mLooper.dispatchAll();
        // verify restart usb tethering
        verify(mUsbManager).setCurrentFunctions(UsbManager.FUNCTION_RNDIS);
        // verify restart ethernet tethering
        verify(mEm).requestTetheredInterface(any(), callbackCaptor.capture());
        ethCallback = callbackCaptor.getValue();
        ethCallback.onAvailable(TEST_ETH_IFNAME);

        reset(mUsbManager, mEm);
        when(mNetd.interfaceGetList())
                .thenReturn(new String[] {
                        TEST_MOBILE_IFNAME, TEST_WLAN_IFNAME, TEST_RNDIS_IFNAME, TEST_P2P_IFNAME,
                        TEST_NCM_IFNAME, TEST_ETH_IFNAME});

        mTethering.interfaceStatusChanged(TEST_RNDIS_IFNAME, true);
        sendUsbBroadcast(true, true, TETHER_USB_RNDIS_FUNCTION);
        assertContains(Arrays.asList(mTethering.getTetherableIfacesForTest()), TEST_RNDIS_IFNAME);
        assertContains(Arrays.asList(mTethering.getTetherableIfacesForTest()), TEST_ETH_IFNAME);
        assertEquals(TETHER_ERROR_IFACE_CFG_ERROR, mTethering.getLastErrorForTest(
                TEST_RNDIS_IFNAME));
        assertEquals(TETHER_ERROR_IFACE_CFG_ERROR, mTethering.getLastErrorForTest(TEST_ETH_IFNAME));
    }

    @Test
    public void testProvisioningNeededButUnavailable() throws Exception {
        initTetheringOnTestThread();
        assertTrue(mTethering.isTetheringSupported());
        verify(mPackageManager, never()).getPackageInfo(PROVISIONING_APP_NAME[0], GET_ACTIVITIES);

        setupForRequiredProvisioning();
        assertTrue(mTethering.isTetheringSupported());
        verify(mPackageManager).getPackageInfo(PROVISIONING_APP_NAME[0], GET_ACTIVITIES);
        reset(mPackageManager);

        doThrow(PackageManager.NameNotFoundException.class).when(mPackageManager).getPackageInfo(
                PROVISIONING_APP_NAME[0], GET_ACTIVITIES);
        setupForRequiredProvisioning();
        assertFalse(mTethering.isTetheringSupported());
        verify(mPackageManager).getPackageInfo(PROVISIONING_APP_NAME[0], GET_ACTIVITIES);
    }

    @Test
    public void testUpdateConnectedClients() throws Exception {
        initTetheringOnTestThread();
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        runAsShell(NETWORK_SETTINGS, () -> {
            mTethering.registerTetheringEventCallback(callback);
            mLooper.dispatchAll();
        });
        callback.expectTetheredClientChanged(Collections.emptyList());

        IDhcpEventCallbacks eventCallbacks;
        final ArgumentCaptor<IDhcpEventCallbacks> dhcpEventCbsCaptor =
                 ArgumentCaptor.forClass(IDhcpEventCallbacks.class);
        // Run local only tethering.
        mTethering.interfaceStatusChanged(TEST_P2P_IFNAME, true);
        sendWifiP2pConnectionChanged(true, true, TEST_P2P_IFNAME);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS)).startWithCallbacks(
                any(), dhcpEventCbsCaptor.capture());
        eventCallbacks = dhcpEventCbsCaptor.getValue();
        // Update lease for local only tethering.
        final MacAddress testMac1 = MacAddress.fromString("11:11:11:11:11:11");
        final DhcpLeaseParcelable p2pLease = createDhcpLeaseParcelable("clientId1", testMac1,
                "192.168.50.24", 24, Long.MAX_VALUE, "test1");
        final List<TetheredClient> connectedClients = notifyDhcpLeasesChanged(TETHERING_WIFI_P2P,
                eventCallbacks, p2pLease);
        callback.expectTetheredClientChanged(connectedClients);
        reset(mDhcpServer);

        // Run wifi tethering.
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS)).startWithCallbacks(
                any(), dhcpEventCbsCaptor.capture());
        eventCallbacks = dhcpEventCbsCaptor.getValue();
        final MacAddress testMac2 = MacAddress.fromString("22:22:22:22:22:22");
        final DhcpLeaseParcelable wifiLease = createDhcpLeaseParcelable("clientId2", testMac2,
                "192.168.43.24", 24, Long.MAX_VALUE, "test2");
        verifyHotspotClientUpdate(false /* isLocalOnly */, testMac2, wifiLease, connectedClients,
                eventCallbacks, callback);

        // Test onStarted callback that register second callback when tethering is running.
        TestTetheringEventCallback callback2 = new TestTetheringEventCallback();
        runAsShell(NETWORK_SETTINGS, () -> {
            mTethering.registerTetheringEventCallback(callback2);
            mLooper.dispatchAll();
        });
        callback2.expectTetheredClientChanged(connectedClients);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testUpdateConnectedClientsForLocalOnlyHotspot() throws Exception {
        initTetheringOnTestThread();
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        runAsShell(NETWORK_SETTINGS, () -> {
            mTethering.registerTetheringEventCallback(callback);
            mLooper.dispatchAll();
        });
        callback.expectTetheredClientChanged(Collections.emptyList());

        // Run local only hotspot.
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_LOCAL_ONLY);

        final ArgumentCaptor<IDhcpEventCallbacks> dhcpEventCbsCaptor =
                 ArgumentCaptor.forClass(IDhcpEventCallbacks.class);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS)).startWithCallbacks(
                any(), dhcpEventCbsCaptor.capture());
        final IDhcpEventCallbacks eventCallbacks = dhcpEventCbsCaptor.getValue();

        final List<TetheredClient> connectedClients = new ArrayList<>();
        final MacAddress testMac = MacAddress.fromString("22:22:22:22:22:22");
        final DhcpLeaseParcelable wifiLease = createDhcpLeaseParcelable("clientId", testMac,
                "192.168.43.24", 24, Long.MAX_VALUE, "test");
        verifyHotspotClientUpdate(true /* isLocalOnly */, testMac, wifiLease, connectedClients,
                eventCallbacks, callback);

        // Client disconnect from local only hotspot.
        mLocalOnlyHotspotCallback.onConnectedClientsChanged(Collections.emptyList());
        callback.expectTetheredClientChanged(Collections.emptyList());
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testConnectedClientsForSapAndLohsConcurrency() throws Exception {
        initTetheringOnTestThread();
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        runAsShell(NETWORK_SETTINGS, () -> {
            mTethering.registerTetheringEventCallback(callback);
            mLooper.dispatchAll();
        });
        callback.expectTetheredClientChanged(Collections.emptyList());

        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);
        final ArgumentCaptor<IDhcpEventCallbacks> dhcpEventCbsCaptor =
                 ArgumentCaptor.forClass(IDhcpEventCallbacks.class);
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS)).startWithCallbacks(
                any(), dhcpEventCbsCaptor.capture());
        IDhcpEventCallbacks eventCallbacks = dhcpEventCbsCaptor.getValue();
        final List<TetheredClient> connectedClients = new ArrayList<>();
        final MacAddress wifiMac = MacAddress.fromString("11:11:11:11:11:11");
        final DhcpLeaseParcelable wifiLease = createDhcpLeaseParcelable("clientId", wifiMac,
                "192.168.2.12", 24, Long.MAX_VALUE, "test");
        verifyHotspotClientUpdate(false /* isLocalOnly */, wifiMac, wifiLease, connectedClients,
                eventCallbacks, callback);
        reset(mDhcpServer);

        mTethering.interfaceStatusChanged(TEST_WLAN2_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN2_IFNAME, IFACE_IP_MODE_LOCAL_ONLY);

        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS)).startWithCallbacks(
                any(), dhcpEventCbsCaptor.capture());
        eventCallbacks = dhcpEventCbsCaptor.getValue();
        final MacAddress localOnlyMac = MacAddress.fromString("22:22:22:22:22:22");
        final DhcpLeaseParcelable localOnlyLease = createDhcpLeaseParcelable("clientId",
                localOnlyMac, "192.168.43.24", 24, Long.MAX_VALUE, "test");
        verifyHotspotClientUpdate(true /* isLocalOnly */, localOnlyMac, localOnlyLease,
                connectedClients, eventCallbacks, callback);

        assertTrue(isIpServerActive(TETHERING_WIFI, TEST_WLAN_IFNAME, IpServer.STATE_TETHERED));
        assertTrue(isIpServerActive(TETHERING_WIFI, TEST_WLAN2_IFNAME, IpServer.STATE_LOCAL_ONLY));
    }

    private boolean isIpServerActive(int type, String ifName, int mode) {
        for (IpServer ipSrv : mTetheringDependencies.mAllDownstreams) {
            if (ipSrv.interfaceType() == type && ipSrv.interfaceName().equals(ifName)
                    && ipSrv.servingMode() == mode) {
                return true;
            }
        }

        return false;
    }

    private void verifyHotspotClientUpdate(final boolean isLocalOnly, final MacAddress testMac,
            final DhcpLeaseParcelable dhcpLease, final List<TetheredClient> currentClients,
            final IDhcpEventCallbacks dhcpCallback, final TestTetheringEventCallback callback)
            throws Exception {
        // Update mac address from softAp callback before getting dhcp lease.
        final TetheredClient noAddrClient = notifyConnectedWifiClientsChanged(testMac, isLocalOnly);
        final List<TetheredClient> withNoAddrClients = new ArrayList<>(currentClients);
        withNoAddrClients.add(noAddrClient);
        callback.expectTetheredClientChanged(withNoAddrClients);

        // Update dhcp lease for hotspot.
        currentClients.addAll(notifyDhcpLeasesChanged(TETHERING_WIFI, dhcpCallback, dhcpLease));
        callback.expectTetheredClientChanged(currentClients);
    }

    private TetheredClient notifyConnectedWifiClientsChanged(final MacAddress mac,
            boolean isLocalOnly) throws Exception {
        final ArrayList<WifiClient> wifiClients = new ArrayList<>();
        final WifiClient testClient = mock(WifiClient.class);
        when(testClient.getMacAddress()).thenReturn(mac);
        wifiClients.add(testClient);
        if (isLocalOnly) {
            mLocalOnlyHotspotCallback.onConnectedClientsChanged(wifiClients);
        } else {
            mSoftApCallback.onConnectedClientsChanged(wifiClients);
        }
        return new TetheredClient(mac, Collections.emptyList() /* addresses */, TETHERING_WIFI);
    }

    private List<TetheredClient> notifyDhcpLeasesChanged(int type, IDhcpEventCallbacks callback,
            DhcpLeaseParcelable... leases) throws Exception {
        final List<DhcpLeaseParcelable> dhcpLeases = Arrays.asList(leases);
        callback.onLeasesChanged(dhcpLeases);
        mLooper.dispatchAll();

        return toTetheredClients(dhcpLeases, type);
    }

    private List<TetheredClient> toTetheredClients(List<DhcpLeaseParcelable> leaseParcelables,
            int type) throws Exception {
        final ArrayList<TetheredClient> clients = new ArrayList<>();
        for (DhcpLeaseParcelable lease : leaseParcelables) {
            final LinkAddress address = new LinkAddress(
                    intToInet4AddressHTH(lease.netAddr), lease.prefixLength,
                    0 /* flags */, RT_SCOPE_UNIVERSE /* as per RFC6724#3.2 */,
                    lease.expTime /* deprecationTime */, lease.expTime /* expirationTime */);

            final MacAddress macAddress = MacAddress.fromBytes(lease.hwAddr);

            final AddressInfo addressInfo = new TetheredClient.AddressInfo(address, lease.hostname);
            clients.add(new TetheredClient(
                    macAddress,
                    Collections.singletonList(addressInfo),
                    type));
        }

        return clients;
    }

    private DhcpLeaseParcelable createDhcpLeaseParcelable(final String clientId,
            final MacAddress hwAddr, final String netAddr, final int prefixLength,
            final long expTime, final String hostname) throws Exception {
        final DhcpLeaseParcelable lease = new DhcpLeaseParcelable();
        lease.clientId = clientId.getBytes();
        lease.hwAddr = hwAddr.toByteArray();
        lease.netAddr = inet4AddressToIntHTH(
                (Inet4Address) InetAddresses.parseNumericAddress(netAddr));
        lease.prefixLength = prefixLength;
        lease.expTime = expTime;
        lease.hostname = hostname;

        return lease;
    }

    @Test
    public void testBluetoothTethering() throws Exception {
        initTetheringOnTestThread();
        // Switch to @IgnoreUpTo(Build.VERSION_CODES.S_V2) when it is available for AOSP.
        assumeTrue(isAtLeastT());

        final ResultListener result = new ResultListener(TETHER_ERROR_NO_ERROR);
        mockBluetoothSettings(true /* bluetoothOn */, true /* tetheringOn */);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_BLUETOOTH),
                TEST_CALLER_PKG, result);
        mLooper.dispatchAll();
        verifySetBluetoothTethering(true /* enable */, true /* bindToPanService */);
        result.assertHasResult();

        mTetheredInterfaceCallbackShim.onAvailable(TEST_BT_IFNAME);
        mLooper.dispatchAll();
        verifyNetdCommandForBtSetup();

        // If PAN disconnect, tethering should also be stopped.
        mTetheredInterfaceCallbackShim.onUnavailable();
        mLooper.dispatchAll();
        verifyNetdCommandForBtTearDown();

        // Tethering could restart if PAN reconnect.
        mTetheredInterfaceCallbackShim.onAvailable(TEST_BT_IFNAME);
        mLooper.dispatchAll();
        verifyNetdCommandForBtSetup();

        // Pretend that bluetooth tethering was disabled.
        mockBluetoothSettings(true /* bluetoothOn */, false /* tetheringOn */);
        mTethering.stopTethering(TETHERING_BLUETOOTH);
        mLooper.dispatchAll();
        verifySetBluetoothTethering(false /* enable */, false /* bindToPanService */);

        verifyNetdCommandForBtTearDown();
    }

    @Test
    public void testBluetoothTetheringBeforeT() throws Exception {
        initTetheringOnTestThread();
        // Switch to @IgnoreAfter(Build.VERSION_CODES.S_V2) when it is available for AOSP.
        assumeFalse(isAtLeastT());

        final ResultListener result = new ResultListener(TETHER_ERROR_NO_ERROR);
        mockBluetoothSettings(true /* bluetoothOn */, true /* tetheringOn */);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_BLUETOOTH),
                TEST_CALLER_PKG, result);
        mLooper.dispatchAll();
        verifySetBluetoothTethering(true /* enable */, true /* bindToPanService */);
        result.assertHasResult();

        mTethering.interfaceAdded(TEST_BT_IFNAME);
        mLooper.dispatchAll();

        mTethering.interfaceStatusChanged(TEST_BT_IFNAME, false);
        mTethering.interfaceStatusChanged(TEST_BT_IFNAME, true);
        final ResultListener tetherResult = new ResultListener(TETHER_ERROR_NO_ERROR);
        mTethering.tether(TEST_BT_IFNAME, IpServer.STATE_TETHERED, tetherResult);
        mLooper.dispatchAll();
        tetherResult.assertHasResult();

        verifyNetdCommandForBtSetup();

        // Turning tethering on a second time does not bind to the PAN service again, since it's
        // already bound.
        mockBluetoothSettings(true /* bluetoothOn */, true /* tetheringOn */);
        final ResultListener secondResult = new ResultListener(TETHER_ERROR_NO_ERROR);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_BLUETOOTH),
                TEST_CALLER_PKG, secondResult);
        mLooper.dispatchAll();
        verifySetBluetoothTethering(true /* enable */, false /* bindToPanService */);
        secondResult.assertHasResult();

        mockBluetoothSettings(true /* bluetoothOn */, false /* tetheringOn */);
        mTethering.stopTethering(TETHERING_BLUETOOTH);
        mLooper.dispatchAll();
        final ResultListener untetherResult = new ResultListener(TETHER_ERROR_NO_ERROR);
        mTethering.untether(TEST_BT_IFNAME, untetherResult);
        mLooper.dispatchAll();
        untetherResult.assertHasResult();
        verifySetBluetoothTethering(false /* enable */, false /* bindToPanService */);

        verifyNetdCommandForBtTearDown();
    }

    @Test
    public void testBluetoothServiceDisconnects() throws Exception {
        initTetheringOnTestThread();
        final ResultListener result = new ResultListener(TETHER_ERROR_NO_ERROR);
        mockBluetoothSettings(true /* bluetoothOn */, true /* tetheringOn */);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_BLUETOOTH),
                TEST_CALLER_PKG, result);
        mLooper.dispatchAll();
        ServiceListener panListener = verifySetBluetoothTethering(true /* enable */,
                true /* bindToPanService */);
        result.assertHasResult();

        mTethering.interfaceAdded(TEST_BT_IFNAME);
        mLooper.dispatchAll();

        if (isAtLeastT()) {
            mTetheredInterfaceCallbackShim.onAvailable(TEST_BT_IFNAME);
            mLooper.dispatchAll();
        } else {
            mTethering.interfaceStatusChanged(TEST_BT_IFNAME, false);
            mTethering.interfaceStatusChanged(TEST_BT_IFNAME, true);
            final ResultListener tetherResult = new ResultListener(TETHER_ERROR_NO_ERROR);
            mTethering.tether(TEST_BT_IFNAME, IpServer.STATE_TETHERED, tetherResult);
            mLooper.dispatchAll();
            tetherResult.assertHasResult();
        }

        verifyNetdCommandForBtSetup();

        panListener.onServiceDisconnected(BluetoothProfile.PAN);
        mTethering.interfaceStatusChanged(TEST_BT_IFNAME, false);
        mLooper.dispatchAll();

        verifyNetdCommandForBtTearDown();
    }

    private void mockBluetoothSettings(boolean bluetoothOn, boolean tetheringOn) {
        when(mBluetoothAdapter.isEnabled()).thenReturn(bluetoothOn);
        when(mBluetoothPan.isTetheringOn()).thenReturn(tetheringOn);
    }

    private void verifyNetdCommandForBtSetup() throws Exception {
        if (isAtLeastT()) {
            verify(mNetd).interfaceSetCfg(argThat(cfg -> TEST_BT_IFNAME.equals(cfg.ifName)
                    && assertContainsFlag(cfg.flags, INetd.IF_STATE_UP)));
        }
        verify(mNetd).tetherInterfaceAdd(TEST_BT_IFNAME);
        verify(mNetd).networkAddInterface(INetd.LOCAL_NET_ID, TEST_BT_IFNAME);
        verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(TEST_BT_IFNAME),
                anyString(), anyString());
        verify(mNetd).ipfwdEnableForwarding(TETHERING_NAME);
        verify(mNetd).tetherStartWithConfiguration(any());
        verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(TEST_BT_IFNAME),
                anyString(), anyString());
        verifyNoMoreInteractions(mNetd);
        reset(mNetd);
    }

    private boolean assertContainsFlag(String[] flags, String match) {
        for (String flag : flags) {
            if (flag.equals(match)) return true;
        }
        return false;
    }

    private void verifyNetdCommandForBtTearDown() throws Exception {
        verify(mNetd).tetherApplyDnsInterfaces();
        verify(mNetd).tetherInterfaceRemove(TEST_BT_IFNAME);
        verify(mNetd).networkRemoveInterface(INetd.LOCAL_NET_ID, TEST_BT_IFNAME);
        // One is ipv4 address clear (set to 0.0.0.0), another is set interface down which only
        // happen after T. Before T, the interface configuration control in bluetooth side.
        verify(mNetd, times(isAtLeastT() ? 2 : 1)).interfaceSetCfg(
                any(InterfaceConfigurationParcel.class));
        verify(mNetd).tetherStop();
        verify(mNetd).ipfwdDisableForwarding(TETHERING_NAME);
        reset(mNetd);
    }

    // If bindToPanService is true, this function would return ServiceListener which could notify
    // PanService is connected or disconnected.
    private ServiceListener verifySetBluetoothTethering(final boolean enable,
            final boolean bindToPanService) throws Exception {
        ServiceListener listener = null;
        verify(mBluetoothAdapter).isEnabled();
        if (bindToPanService) {
            final ArgumentCaptor<ServiceListener> listenerCaptor =
                    ArgumentCaptor.forClass(ServiceListener.class);
            verify(mBluetoothAdapter).getProfileProxy(eq(mServiceContext), listenerCaptor.capture(),
                    eq(BluetoothProfile.PAN));
            listener = listenerCaptor.getValue();
            listener.onServiceConnected(BluetoothProfile.PAN, mBluetoothPan);
            mLooper.dispatchAll();
        } else {
            verify(mBluetoothAdapter, never()).getProfileProxy(eq(mServiceContext), any(),
                    anyInt());
        }

        if (isAtLeastT()) {
            if (enable) {
                final ArgumentCaptor<TetheredInterfaceCallbackShim> callbackCaptor =
                        ArgumentCaptor.forClass(TetheredInterfaceCallbackShim.class);
                verify(mBluetoothPanShim).requestTetheredInterface(any(), callbackCaptor.capture());
                mTetheredInterfaceCallbackShim = callbackCaptor.getValue();
            } else {
                verify(mTetheredInterfaceRequestShim).release();
            }
        } else {
            verify(mBluetoothPan).setBluetoothTethering(enable);
        }
        verify(mBluetoothPan).isTetheringOn();
        verifyNoMoreInteractions(mBluetoothAdapter, mBluetoothPan);
        reset(mBluetoothAdapter, mBluetoothPan);

        return listener;
    }

    private void runDualStackUsbTethering(final String expectedIface) throws Exception {
        when(mNetd.interfaceGetList()).thenReturn(new String[] {expectedIface});
        when(mRouterAdvertisementDaemon.start())
                .thenReturn(true);
        final UpstreamNetworkState upstreamState = buildMobileDualStackUpstreamState();
        runUsbTethering(upstreamState);

        verify(mNetd).interfaceGetList();
        verify(mNetd).tetherAddForward(expectedIface, TEST_MOBILE_IFNAME);
        verify(mNetd).ipfwdAddInterfaceForward(expectedIface, TEST_MOBILE_IFNAME);

        verify(mRouterAdvertisementDaemon).start();
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS)).startWithCallbacks(
                any(), any());
        sendIPv6TetherUpdates(upstreamState);
        assertSetIfaceToDadProxy(1 /* numOfCalls */, TEST_MOBILE_IFNAME /* ifaceName */);
        verify(mRouterAdvertisementDaemon).buildNewRa(any(), notNull());
        verify(mNetd).tetherApplyDnsInterfaces();
    }

    private void forceUsbTetheringUse(final int function) {
        setSetting(TETHER_FORCE_USB_FUNCTIONS, function);
    }

    private void setSetting(final String key, final int value) {
        Settings.Global.putInt(mContentResolver, key, value);
        final ContentObserver observer = mTethering.getSettingsObserverForTest();
        observer.onChange(false /* selfChange */, Settings.Global.getUriFor(key));
        mLooper.dispatchAll();
    }

    private void verifyUsbTetheringStopDueToSettingChange(final String iface) {
        verify(mUsbManager, times(2)).setCurrentFunctions(UsbManager.FUNCTION_NONE);
        mTethering.interfaceRemoved(iface);
        sendUsbBroadcast(true, true, -1 /* no functions enabled */);
        reset(mUsbManager, mNetd, mDhcpServer, mRouterAdvertisementDaemon,
                mIPv6TetheringCoordinator, mDadProxy);
    }

    @Test
    public void testUsbFunctionConfigurationChange() throws Exception {
        initTetheringOnTestThread();
        // Run TETHERING_NCM.
        runNcmTethering();
        verify(mDhcpServer, timeout(DHCPSERVER_START_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
        verify(mTetheringMetrics).createBuilder(eq(TETHERING_NCM), anyString());
        verify(mTetheringMetrics, times(1)).maybeUpdateUpstreamType(any());

        // Change the USB tethering function to NCM. Because the USB tethering function was set to
        // RNDIS (the default), tethering is stopped.
        forceUsbTetheringUse(TETHER_USB_NCM_FUNCTION);
        verifyUsbTetheringStopDueToSettingChange(TEST_NCM_IFNAME);
        verify(mTetheringMetrics).updateErrorCode(anyInt(), eq(TETHER_ERROR_NO_ERROR));
        verify(mTetheringMetrics).sendReport(eq(TETHERING_NCM));

        // If TETHERING_USB is forced to use ncm function, TETHERING_NCM would no longer be
        // available.
        final ResultListener ncmResult = new ResultListener(TETHER_ERROR_SERVICE_UNAVAIL);
        mTethering.startTethering(createTetheringRequestParcel(TETHERING_NCM), TEST_CALLER_PKG,
                ncmResult);
        mLooper.dispatchAll();
        ncmResult.assertHasResult();
        verify(mTetheringMetrics, times(2)).createBuilder(eq(TETHERING_NCM), anyString());
        verify(mTetheringMetrics, times(1)).maybeUpdateUpstreamType(any());
        verify(mTetheringMetrics).updateErrorCode(eq(TETHERING_NCM),
                eq(TETHER_ERROR_SERVICE_UNAVAIL));
        verify(mTetheringMetrics, times(2)).sendReport(eq(TETHERING_NCM));

        // Run TETHERING_USB with ncm configuration.
        runDualStackUsbTethering(TEST_NCM_IFNAME);

        // Change configuration to rndis.
        forceUsbTetheringUse(TETHER_USB_RNDIS_FUNCTION);
        verifyUsbTetheringStopDueToSettingChange(TEST_NCM_IFNAME);

        // Run TETHERING_USB with rndis configuration.
        runDualStackUsbTethering(TEST_RNDIS_IFNAME);
        runStopUSBTethering();
    }

    public static ArraySet<Integer> getAllSupportedTetheringTypes() {
        return new ArraySet<>(new Integer[] { TETHERING_USB, TETHERING_NCM, TETHERING_WIFI,
                TETHERING_WIFI_P2P, TETHERING_BLUETOOTH, TETHERING_ETHERNET });
    }

    private void setUserRestricted(boolean restricted) {
        final Bundle restrictions = new Bundle();
        restrictions.putBoolean(UserManager.DISALLOW_CONFIG_TETHERING, restricted);
        when(mUserManager.getUserRestrictions()).thenReturn(restrictions);
        when(mUserManager.hasUserRestriction(
                UserManager.DISALLOW_CONFIG_TETHERING)).thenReturn(restricted);

        final Intent intent = new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED);
        mServiceContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        mLooper.dispatchAll();
    }

    @Test
    public void testTetheringSupported() throws Exception {
        initTetheringOnTestThread();
        final ArraySet<Integer> expectedTypes = getAllSupportedTetheringTypes();
        // Check tethering is supported after initialization.
        TestTetheringEventCallback callback = new TestTetheringEventCallback();
        mTethering.registerTetheringEventCallback(callback);
        mLooper.dispatchAll();
        verifySupported(callback, expectedTypes);

        // Could change tethering supported by settings.
        setSetting(Settings.Global.TETHER_SUPPORTED, 0);
        verifySupported(callback, new ArraySet<>());
        setSetting(Settings.Global.TETHER_SUPPORTED, 1);
        verifySupported(callback, expectedTypes);

        // Could change tethering supported by user restriction.
        setUserRestricted(true /* restricted */);
        verifySupported(callback, new ArraySet<>());
        setUserRestricted(false /* restricted */);
        verifySupported(callback, expectedTypes);

        // Usb tethering is not supported:
        expectedTypes.remove(TETHERING_USB);
        when(mResources.getStringArray(R.array.config_tether_usb_regexs))
                .thenReturn(new String[0]);
        sendConfigurationChanged();
        verifySupported(callback, expectedTypes);
        // Wifi tethering is not supported:
        expectedTypes.remove(TETHERING_WIFI);
        when(mResources.getStringArray(R.array.config_tether_wifi_regexs))
                .thenReturn(new String[0]);
        sendConfigurationChanged();
        verifySupported(callback, expectedTypes);
        // Bluetooth tethering is not supported:
        expectedTypes.remove(TETHERING_BLUETOOTH);
        when(mResources.getStringArray(R.array.config_tether_bluetooth_regexs))
                .thenReturn(new String[0]);

        if (isAtLeastT()) {
            sendConfigurationChanged();
            verifySupported(callback, expectedTypes);

            // P2p tethering is not supported:
            expectedTypes.remove(TETHERING_WIFI_P2P);
            when(mResources.getStringArray(R.array.config_tether_wifi_p2p_regexs))
                    .thenReturn(new String[0]);
            sendConfigurationChanged();
            verifySupported(callback, expectedTypes);
            // Ncm tethering is not supported:
            expectedTypes.remove(TETHERING_NCM);
            when(mResources.getStringArray(R.array.config_tether_ncm_regexs))
                    .thenReturn(new String[0]);
            sendConfigurationChanged();
            verifySupported(callback, expectedTypes);
            // Ethernet tethering (last supported type) is not supported:
            expectedTypes.remove(TETHERING_ETHERNET);
            mForceEthernetServiceUnavailable = true;
            sendConfigurationChanged();
            verifySupported(callback, new ArraySet<>());
        } else {
            // If wifi, usb and bluetooth are all not supported, all the types are not supported.
            sendConfigurationChanged();
            verifySupported(callback, new ArraySet<>());
        }
    }

    private void verifySupported(final TestTetheringEventCallback callback,
            final ArraySet<Integer> expectedTypes) {
        assertEquals(expectedTypes.size() > 0, mTethering.isTetheringSupported());
        callback.expectSupportedTetheringTypes(expectedTypes);
    }

    @Test
    public void testIpv4AddressForSapAndLohsConcurrency() throws Exception {
        initTetheringOnTestThread();
        mTethering.interfaceStatusChanged(TEST_WLAN_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN_IFNAME, IFACE_IP_MODE_TETHERED);

        ArgumentCaptor<InterfaceConfigurationParcel> ifaceConfigCaptor =
                ArgumentCaptor.forClass(InterfaceConfigurationParcel.class);
        verify(mNetd).interfaceSetCfg(ifaceConfigCaptor.capture());
        InterfaceConfigurationParcel ifaceConfig = ifaceConfigCaptor.getValue();
        final IpPrefix sapPrefix = new IpPrefix(
                InetAddresses.parseNumericAddress(ifaceConfig.ipv4Addr), ifaceConfig.prefixLength);

        mTethering.interfaceStatusChanged(TEST_WLAN2_IFNAME, true);
        sendWifiApStateChanged(WIFI_AP_STATE_ENABLED, TEST_WLAN2_IFNAME, IFACE_IP_MODE_LOCAL_ONLY);

        ifaceConfigCaptor = ArgumentCaptor.forClass(InterfaceConfigurationParcel.class);
        verify(mNetd, times(2)).interfaceSetCfg(ifaceConfigCaptor.capture());
        ifaceConfig = ifaceConfigCaptor.getValue();
        final IpPrefix lohsPrefix = new IpPrefix(
                InetAddresses.parseNumericAddress(ifaceConfig.ipv4Addr), ifaceConfig.prefixLength);
        assertFalse(sapPrefix.equals(lohsPrefix));
    }
    // TODO: Test that a request for hotspot mode doesn't interfere with an
    // already operating tethering mode interface.
}
