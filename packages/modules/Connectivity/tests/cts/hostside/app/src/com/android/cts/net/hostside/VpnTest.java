/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.net.hostside;

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.content.pm.PackageManager.FEATURE_WIFI;
import static android.net.ConnectivityManager.TYPE_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.os.Process.INVALID_UID;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.ECONNABORTED;
import static android.system.OsConstants.IPPROTO_ICMP;
import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.test.MoreAsserts.assertNotEqual;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.cts.net.hostside.VpnTest.TestSocketKeepaliveCallback.CallbackType.ON_DATA_RECEIVED;
import static com.android.cts.net.hostside.VpnTest.TestSocketKeepaliveCallback.CallbackType.ON_ERROR;
import static com.android.cts.net.hostside.VpnTest.TestSocketKeepaliveCallback.CallbackType.ON_PAUSED;
import static com.android.cts.net.hostside.VpnTest.TestSocketKeepaliveCallback.CallbackType.ON_RESUMED;
import static com.android.cts.net.hostside.VpnTest.TestSocketKeepaliveCallback.CallbackType.ON_STARTED;
import static com.android.cts.net.hostside.VpnTest.TestSocketKeepaliveCallback.CallbackType.ON_STOPPED;
import static com.android.networkstack.apishim.ConstantsShim.BLOCKED_REASON_LOCKDOWN_VPN;
import static com.android.networkstack.apishim.ConstantsShim.BLOCKED_REASON_NONE;
import static com.android.networkstack.apishim.ConstantsShim.RECEIVER_EXPORTED;
import static com.android.testutils.Cleanup.testAndCleanup;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.RecorderCallback.CallbackEntry.BLOCKED_STATUS_INT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.IpSecManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.SocketKeepalive;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.TransportInfo;
import android.net.Uri;
import android.net.VpnManager;
import android.net.VpnService;
import android.net.VpnTransportInfo;
import android.net.cts.util.CtsNetUtils;
import android.net.util.KeepaliveUtils;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.test.MoreAsserts;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Range;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.ArrayTrackRecord;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.PacketBuilder;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.RecorderCallback;
import com.android.testutils.RecorderCallback.CallbackEntry;
import com.android.testutils.TestableNetworkCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the VpnService API.
 *
 * These tests establish a VPN via the VpnService API, and have the service reflect the packets back
 * to the device without causing any network traffic. This allows testing the local VPN data path
 * without a network connection or a VPN server.
 *
 * Note: in Lollipop, VPN functionality relies on kernel support for UID-based routing. If these
 * tests fail, it may be due to the lack of kernel support. The necessary patches can be
 * cherry-picked from the Android common kernel trees:
 *
 * android-3.10:
 *   https://android-review.googlesource.com/#/c/99220/
 *   https://android-review.googlesource.com/#/c/100545/
 *
 * android-3.4:
 *   https://android-review.googlesource.com/#/c/99225/
 *   https://android-review.googlesource.com/#/c/100557/
 *
 * To ensure that the kernel has the required commits, run the kernel unit
 * tests described at:
 *
 *   https://source.android.com/devices/tech/config/kernel_network_tests.html
 *
 */
@RunWith(AndroidJUnit4.class)
public class VpnTest {

    // These are neither public nor @TestApi.
    // TODO: add them to @TestApi.
    private static final String PRIVATE_DNS_MODE_SETTING = "private_dns_mode";
    private static final String PRIVATE_DNS_MODE_PROVIDER_HOSTNAME = "hostname";
    private static final String PRIVATE_DNS_MODE_OPPORTUNISTIC = "opportunistic";
    private static final String PRIVATE_DNS_SPECIFIER_SETTING = "private_dns_specifier";
    private static final int NETWORK_CALLBACK_TIMEOUT_MS = 30_000;

    private static final LinkAddress TEST_IP4_DST_ADDR = new LinkAddress("198.51.100.1/24");
    private static final LinkAddress TEST_IP4_SRC_ADDR = new LinkAddress("198.51.100.2/24");
    private static final LinkAddress TEST_IP6_DST_ADDR = new LinkAddress("2001:db8:1:3::1/64");
    private static final LinkAddress TEST_IP6_SRC_ADDR = new LinkAddress("2001:db8:1:3::2/64");
    private static final short TEST_SRC_PORT = 5555;

    public static String TAG = "VpnTest";
    public static int TIMEOUT_MS = 3 * 1000;
    public static int SOCKET_TIMEOUT_MS = 100;
    public static String TEST_HOST = "connectivitycheck.gstatic.com";

    private static final String AUTOMATIC_ON_OFF_KEEPALIVE_VERSION =
                "automatic_on_off_keepalive_version";
    // Enabled since version 1 means it's always enabled because the version is always above 1
    private static final String AUTOMATIC_ON_OFF_KEEPALIVE_ENABLED = "1";
    private static final long TEST_TCP_POLLING_TIMER_EXPIRED_PERIOD_MS = 60_000L;

    private UiDevice mDevice;
    private MyActivity mActivity;
    private String mPackageName;
    private ConnectivityManager mCM;
    private WifiManager mWifiManager;
    private RemoteSocketFactoryClient mRemoteSocketFactoryClient;
    private CtsNetUtils mCtsNetUtils;
    private PackageManager mPackageManager;
    private Context mTestContext;
    private Context mTargetContext;
    Network mNetwork;
    final Object mLock = new Object();
    final Object mLockShutdown = new Object();

    private String mOldPrivateDnsMode;
    private String mOldPrivateDnsSpecifier;

    // The registered callbacks.
    private List<NetworkCallback> mRegisteredCallbacks = new ArrayList<>();

    @Rule
    public final DevSdkIgnoreRule mDevSdkIgnoreRule = new DevSdkIgnoreRule();

    private boolean supportedHardware() {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();
        return !pm.hasSystemFeature("android.hardware.type.watch");
    }

    public final <T extends Activity> T launchActivity(String packageName, Class<T> activityClass) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(packageName, activityClass.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final T activity = (T) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        return activity;
    }

    @Before
    public void setUp() throws Exception {
        mNetwork = null;
        mTestContext = getInstrumentation().getContext();
        mTargetContext = getInstrumentation().getTargetContext();
        storePrivateDnsSetting();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mActivity = launchActivity(mTargetContext.getPackageName(), MyActivity.class);
        mPackageName = mActivity.getPackageName();
        mCM = (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE);
        mRemoteSocketFactoryClient = new RemoteSocketFactoryClient(mActivity);
        mRemoteSocketFactoryClient.bind();
        mDevice.waitForIdle();
        mCtsNetUtils = new CtsNetUtils(mTestContext);
        mPackageManager = mTestContext.getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        restorePrivateDnsSetting();
        mRemoteSocketFactoryClient.unbind();
        mCtsNetUtils.tearDown();
        Log.i(TAG, "Stopping VPN");
        stopVpn();
        unregisterRegisteredCallbacks();
        mActivity.finish();
    }

    private void registerNetworkCallback(NetworkRequest request, NetworkCallback callback) {
        mCM.registerNetworkCallback(request, callback);
        mRegisteredCallbacks.add(callback);
    }

    private void registerDefaultNetworkCallback(NetworkCallback callback) {
        mCM.registerDefaultNetworkCallback(callback);
        mRegisteredCallbacks.add(callback);
    }

    private void registerSystemDefaultNetworkCallback(NetworkCallback callback, Handler h) {
        mCM.registerSystemDefaultNetworkCallback(callback, h);
        mRegisteredCallbacks.add(callback);
    }

    private void registerDefaultNetworkCallbackForUid(int uid, NetworkCallback callback,
            Handler h) {
        mCM.registerDefaultNetworkCallbackForUid(uid, callback, h);
        mRegisteredCallbacks.add(callback);
    }

    private void unregisterRegisteredCallbacks() {
        for (NetworkCallback callback: mRegisteredCallbacks) {
            mCM.unregisterNetworkCallback(callback);
        }
    }

    private void prepareVpn() throws Exception {
        final int REQUEST_ID = 42;

        // Attempt to prepare.
        Log.i(TAG, "Preparing VPN");
        Intent intent = VpnService.prepare(mActivity);

        if (intent != null) {
            // Start the confirmation dialog and click OK.
            mActivity.startActivityForResult(intent, REQUEST_ID);
            mDevice.waitForIdle();

            String packageName = intent.getComponent().getPackageName();
            String resourceIdRegex = "android:id/button1$|button_start_vpn";
            final UiObject okButton = new UiObject(new UiSelector()
                    .className("android.widget.Button")
                    .packageName(packageName)
                    .resourceIdMatches(resourceIdRegex));
            if (okButton.waitForExists(TIMEOUT_MS) == false) {
                mActivity.finishActivity(REQUEST_ID);
                fail("VpnService.prepare returned an Intent for '" + intent.getComponent() + "' " +
                     "to display the VPN confirmation dialog, but this test could not find the " +
                     "button to allow the VPN application to connect. Please ensure that the "  +
                     "component displays a button with a resource ID matching the regexp: '" +
                     resourceIdRegex + "'.");
            }

            // Click the button and wait for RESULT_OK.
            okButton.click();
            try {
                int result = mActivity.getResult(TIMEOUT_MS);
                if (result != MyActivity.RESULT_OK) {
                    fail("The VPN confirmation dialog did not return RESULT_OK when clicking on " +
                         "the button matching the regular expression '" + resourceIdRegex +
                         "' of " + intent.getComponent() + "'. Please ensure that clicking on " +
                         "that button allows the VPN application to connect. " +
                         "Return value: " + result);
                }
            } catch (InterruptedException e) {
                fail("VPN confirmation dialog did not return after " + TIMEOUT_MS + "ms");
            }

            // Now we should be prepared.
            intent = VpnService.prepare(mActivity);
            if (intent != null) {
                fail("VpnService.prepare returned non-null even after the VPN dialog " +
                     intent.getComponent() + "returned RESULT_OK.");
            }
        }
    }

    private void updateUnderlyingNetworks(@Nullable ArrayList<Network> underlyingNetworks)
            throws Exception {
        final Intent intent = new Intent(mActivity, MyVpnService.class)
                .putExtra(mPackageName + ".cmd", MyVpnService.CMD_UPDATE_UNDERLYING_NETWORKS)
                .putParcelableArrayListExtra(
                        mPackageName + ".underlyingNetworks", underlyingNetworks);
        mActivity.startService(intent);
    }

    private void establishVpn(String[] addresses, String[] routes, String[] excludedRoutes,
            String allowedApplications, String disallowedApplications,
            @Nullable ProxyInfo proxyInfo, @Nullable ArrayList<Network> underlyingNetworks,
            boolean isAlwaysMetered, boolean addRoutesByIpPrefix)
            throws Exception {
        final Intent intent = new Intent(mActivity, MyVpnService.class)
                .putExtra(mPackageName + ".cmd", MyVpnService.CMD_CONNECT)
                .putExtra(mPackageName + ".addresses", TextUtils.join(",", addresses))
                .putExtra(mPackageName + ".routes", TextUtils.join(",", routes))
                .putExtra(mPackageName + ".excludedRoutes", TextUtils.join(",", excludedRoutes))
                .putExtra(mPackageName + ".allowedapplications", allowedApplications)
                .putExtra(mPackageName + ".disallowedapplications", disallowedApplications)
                .putExtra(mPackageName + ".httpProxy", proxyInfo)
                .putParcelableArrayListExtra(
                        mPackageName + ".underlyingNetworks", underlyingNetworks)
                .putExtra(mPackageName + ".isAlwaysMetered", isAlwaysMetered)
                .putExtra(mPackageName + ".addRoutesByIpPrefix", addRoutesByIpPrefix);
        mActivity.startService(intent);
    }

    // TODO: Consider replacing arguments with a Builder.
    private void startVpn(
            String[] addresses, String[] routes, String allowedApplications,
            String disallowedApplications, @Nullable ProxyInfo proxyInfo,
            @Nullable ArrayList<Network> underlyingNetworks, boolean isAlwaysMetered)
            throws Exception {
        startVpn(addresses, routes, new String[0] /* excludedRoutes */, allowedApplications,
                disallowedApplications, proxyInfo, underlyingNetworks, isAlwaysMetered);
    }

    private void startVpn(
            String[] addresses, String[] routes, String[] excludedRoutes,
            String allowedApplications, String disallowedApplications,
            @Nullable ProxyInfo proxyInfo,
            @Nullable ArrayList<Network> underlyingNetworks, boolean isAlwaysMetered)
            throws Exception {
        startVpn(addresses, routes, excludedRoutes, allowedApplications, disallowedApplications,
                proxyInfo, underlyingNetworks, isAlwaysMetered, false /* addRoutesByIpPrefix */);
    }

    private void startVpn(
            String[] addresses, String[] routes, String[] excludedRoutes,
            String allowedApplications, String disallowedApplications,
            @Nullable ProxyInfo proxyInfo,
            @Nullable ArrayList<Network> underlyingNetworks, boolean isAlwaysMetered,
            boolean addRoutesByIpPrefix)
            throws Exception {
        prepareVpn();

        // Register a callback so we will be notified when our VPN comes up.
        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        final NetworkCallback callback = new NetworkCallback() {
            public void onAvailable(Network network) {
                synchronized (mLock) {
                    Log.i(TAG, "Got available callback for network=" + network);
                    mNetwork = network;
                    mLock.notify();
                }
            }
        };
        registerNetworkCallback(request, callback);

        // Start the service and wait up for TIMEOUT_MS ms for the VPN to come up.
        establishVpn(addresses, routes, excludedRoutes, allowedApplications, disallowedApplications,
                proxyInfo, underlyingNetworks, isAlwaysMetered, addRoutesByIpPrefix);
        synchronized (mLock) {
            if (mNetwork == null) {
                 Log.i(TAG, "bf mLock");
                 mLock.wait(TIMEOUT_MS);
                 Log.i(TAG, "af mLock");
            }
        }

        if (mNetwork == null) {
            fail("VPN did not become available after " + TIMEOUT_MS + "ms");
        }
    }

    private void stopVpn() {
        // Register a callback so we will be notified when our VPN comes up.
        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        final NetworkCallback callback = new NetworkCallback() {
            public void onLost(Network network) {
                synchronized (mLockShutdown) {
                    Log.i(TAG, "Got lost callback for network=" + network
                            + ",mNetwork = " + mNetwork);
                    if( mNetwork == network){
                        mLockShutdown.notify();
                    }
                }
            }
       };
        registerNetworkCallback(request, callback);
        // Simply calling mActivity.stopService() won't stop the service, because the system binds
        // to the service for the purpose of sending it a revoke command if another VPN comes up,
        // and stopping a bound service has no effect. Instead, "start" the service again with an
        // Intent that tells it to disconnect.
        Intent intent = new Intent(mActivity, MyVpnService.class)
                .putExtra(mPackageName + ".cmd", MyVpnService.CMD_DISCONNECT);
        mActivity.startService(intent);
        synchronized (mLockShutdown) {
            try {
                 Log.i(TAG, "bf mLockShutdown");
                 mLockShutdown.wait(TIMEOUT_MS);
                 Log.i(TAG, "af mLockShutdown");
            } catch(InterruptedException e) {}
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
            }
        }
    }

    private static void checkPing(String to) throws IOException, ErrnoException {
        InetAddress address = InetAddress.getByName(to);
        FileDescriptor s;
        final int LENGTH = 64;
        byte[] packet = new byte[LENGTH];
        byte[] header;

        // Construct a ping packet.
        Random random = new Random();
        random.nextBytes(packet);
        if (address instanceof Inet6Address) {
            s = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_ICMPV6);
            header = new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        } else {
            // Note that this doesn't actually work due to http://b/18558481 .
            s = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
            header = new byte[] { (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        }
        System.arraycopy(header, 0, packet, 0, header.length);

        // Send the packet.
        int port = random.nextInt(65534) + 1;
        Os.connect(s, address, port);
        Os.write(s, packet, 0, packet.length);

        // Expect a reply.
        StructPollfd pollfd = new StructPollfd();
        pollfd.events = (short) POLLIN;  // "error: possible loss of precision"
        pollfd.fd = s;
        int ret = Os.poll(new StructPollfd[] { pollfd }, SOCKET_TIMEOUT_MS);
        assertEquals("Expected reply after sending ping", 1, ret);

        byte[] reply = new byte[LENGTH];
        int read = Os.read(s, reply, 0, LENGTH);
        assertEquals(LENGTH, read);

        // Find out what the kernel set the ICMP ID to.
        InetSocketAddress local = (InetSocketAddress) Os.getsockname(s);
        port = local.getPort();
        packet[4] = (byte) ((port >> 8) & 0xff);
        packet[5] = (byte) (port & 0xff);

        // Check the contents.
        if (packet[0] == (byte) 0x80) {
            packet[0] = (byte) 0x81;
        } else {
            packet[0] = 0;
        }
        // Zero out the checksum in the reply so it matches the uninitialized checksum in packet.
        reply[2] = reply[3] = 0;
        MoreAsserts.assertEquals(packet, reply);
    }

    // Writes data to out and checks that it appears identically on in.
    private static void writeAndCheckData(
            OutputStream out, InputStream in, byte[] data) throws IOException {
        out.write(data, 0, data.length);
        out.flush();

        byte[] read = new byte[data.length];
        int bytesRead = 0, totalRead = 0;
        do {
            bytesRead = in.read(read, totalRead, read.length - totalRead);
            totalRead += bytesRead;
        } while (bytesRead >= 0 && totalRead < data.length);
        assertEquals(totalRead, data.length);
        MoreAsserts.assertEquals(data, read);
    }

    private void checkTcpReflection(String to, String expectedFrom) throws IOException {
        // Exercise TCP over the VPN by "connecting to ourselves". We open a server socket and a
        // client socket, and connect the client socket to a remote host, with the port of the
        // server socket. The PacketReflector reflects the packets, changing the source addresses
        // but not the ports, so our client socket is connected to our server socket, though both
        // sockets think their peers are on the "remote" IP address.

        // Open a listening socket.
        ServerSocket listen = new ServerSocket(0, 10, InetAddress.getByName("::"));

        // Connect the client socket to it.
        InetAddress toAddr = InetAddress.getByName(to);
        Socket client = new Socket();
        try {
            client.connect(new InetSocketAddress(toAddr, listen.getLocalPort()), SOCKET_TIMEOUT_MS);
            if (expectedFrom == null) {
                closeQuietly(listen);
                closeQuietly(client);
                fail("Expected connection to fail, but it succeeded.");
            }
        } catch (IOException e) {
            if (expectedFrom != null) {
                closeQuietly(listen);
                fail("Expected connection to succeed, but it failed.");
            } else {
                // We expected the connection to fail, and it did, so there's nothing more to test.
                return;
            }
        }

        // The connection succeeded, and we expected it to succeed. Send some data; if things are
        // working, the data will be sent to the VPN, reflected by the PacketReflector, and arrive
        // at our server socket. For good measure, send some data in the other direction.
        Socket server = null;
        try {
            // Accept the connection on the server side.
            listen.setSoTimeout(SOCKET_TIMEOUT_MS);
            server = listen.accept();
            checkConnectionOwnerUidTcp(client);
            checkConnectionOwnerUidTcp(server);
            // Check that the source and peer addresses are as expected.
            assertEquals(expectedFrom, client.getLocalAddress().getHostAddress());
            assertEquals(expectedFrom, server.getLocalAddress().getHostAddress());
            assertEquals(
                    new InetSocketAddress(toAddr, client.getLocalPort()),
                    server.getRemoteSocketAddress());
            assertEquals(
                    new InetSocketAddress(toAddr, server.getLocalPort()),
                    client.getRemoteSocketAddress());

            // Now write some data.
            final int LENGTH = 32768;
            byte[] data = new byte[LENGTH];
            new Random().nextBytes(data);

            // Make sure our writes don't block or time out, because we're single-threaded and can't
            // read and write at the same time.
            server.setReceiveBufferSize(LENGTH * 2);
            client.setSendBufferSize(LENGTH * 2);
            client.setSoTimeout(SOCKET_TIMEOUT_MS);
            server.setSoTimeout(SOCKET_TIMEOUT_MS);

            // Send some data from client to server, then from server to client.
            writeAndCheckData(client.getOutputStream(), server.getInputStream(), data);
            writeAndCheckData(server.getOutputStream(), client.getInputStream(), data);
        } finally {
            closeQuietly(listen);
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    private void checkConnectionOwnerUidUdp(DatagramSocket s, boolean expectSuccess) {
        final int expectedUid = expectSuccess ? Process.myUid() : INVALID_UID;
        InetSocketAddress loc = new InetSocketAddress(s.getLocalAddress(), s.getLocalPort());
        InetSocketAddress rem = new InetSocketAddress(s.getInetAddress(), s.getPort());
        int uid = mCM.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, loc, rem);
        assertEquals(expectedUid, uid);
    }

    private void checkConnectionOwnerUidTcp(Socket s) {
        final int expectedUid = Process.myUid();
        InetSocketAddress loc = new InetSocketAddress(s.getLocalAddress(), s.getLocalPort());
        InetSocketAddress rem = new InetSocketAddress(s.getInetAddress(), s.getPort());
        int uid = mCM.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, loc, rem);
        assertEquals(expectedUid, uid);
    }

    private void checkUdpEcho(String to, String expectedFrom) throws IOException {
        checkUdpEcho(to, expectedFrom, expectedFrom != null);
    }

    private void checkUdpEcho(String to, String expectedFrom,
            boolean expectConnectionOwnerIsVisible)
            throws IOException {
        DatagramSocket s;
        InetAddress address = InetAddress.getByName(to);
        if (address instanceof Inet6Address) {  // http://b/18094870
            s = new DatagramSocket(0, InetAddress.getByName("::"));
        } else {
            s = new DatagramSocket();
        }
        s.setSoTimeout(SOCKET_TIMEOUT_MS);

        Random random = new Random();
        byte[] data = new byte[random.nextInt(1650)];
        random.nextBytes(data);
        DatagramPacket p = new DatagramPacket(data, data.length);
        s.connect(address, 7);

        if (expectedFrom != null) {
            assertEquals("Unexpected source address: ",
                         expectedFrom, s.getLocalAddress().getHostAddress());
        }

        try {
            if (expectedFrom != null) {
                s.send(p);
                checkConnectionOwnerUidUdp(s, expectConnectionOwnerIsVisible);
                s.receive(p);
                MoreAsserts.assertEquals(data, p.getData());
            } else {
                try {
                    s.send(p);
                    s.receive(p);
                    fail("Received unexpected reply");
                } catch (IOException expected) {
                    checkConnectionOwnerUidUdp(s, expectConnectionOwnerIsVisible);
                }
            }
        } finally {
            s.close();
        }
    }

    private void checkTrafficOnVpn(String destination) throws Exception {
        final InetAddress address = InetAddress.getByName(destination);

        if (address instanceof Inet6Address) {
            checkUdpEcho(destination, "2001:db8:1:2::ffe");
            checkPing(destination);
            checkTcpReflection(destination, "2001:db8:1:2::ffe");
        } else {
            checkUdpEcho(destination, "192.0.2.2");
            checkTcpReflection(destination, "192.0.2.2");
        }

    }

    private void checkNoTrafficOnVpn(String destination) throws IOException {
        checkUdpEcho(destination, null);
        checkTcpReflection(destination, null);
    }

    private void checkTrafficOnVpn() throws Exception {
        checkTrafficOnVpn("192.0.2.251");
        checkTrafficOnVpn("2001:db8:dead:beef::f00");
    }

    private void checkNoTrafficOnVpn() throws Exception {
        checkNoTrafficOnVpn("192.0.2.251");
        checkNoTrafficOnVpn("2001:db8:dead:beef::f00");
    }

    private void checkTrafficBypassesVpn(String destination) throws Exception {
        checkUdpEcho(destination, null, true /* expectVpnOwnedConnection */);
        checkTcpReflection(destination, null);
    }

    private FileDescriptor openSocketFd(String host, int port, int timeoutMs) throws Exception {
        Socket s = new Socket(host, port);
        s.setSoTimeout(timeoutMs);
        // Dup the filedescriptor so ParcelFileDescriptor's finalizer doesn't garbage collect it
        // and cause our fd to become invalid. http://b/35927643 .
        FileDescriptor fd = Os.dup(ParcelFileDescriptor.fromSocket(s).getFileDescriptor());
        s.close();
        return fd;
    }

    private FileDescriptor openSocketFdInOtherApp(
            String host, int port, int timeoutMs) throws Exception {
        Log.d(TAG, String.format("Creating test socket in UID=%d, my UID=%d",
                mRemoteSocketFactoryClient.getUid(), Os.getuid()));
        FileDescriptor fd = mRemoteSocketFactoryClient.openSocketFd(host, port, TIMEOUT_MS);
        return fd;
    }

    private void sendRequest(FileDescriptor fd, String host) throws Exception {
        String request = "GET /generate_204 HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Connection: keep-alive\r\n\r\n";
        byte[] requestBytes = request.getBytes(StandardCharsets.UTF_8);
        int ret = Os.write(fd, requestBytes, 0, requestBytes.length);
        Log.d(TAG, "Wrote " + ret + "bytes");

        String expected = "HTTP/1.1 204 No Content\r\n";
        byte[] response = new byte[expected.length()];
        Os.read(fd, response, 0, response.length);

        String actual = new String(response, StandardCharsets.UTF_8);
        assertEquals(expected, actual);
        Log.d(TAG, "Got response: " + actual);
    }

    private void assertSocketStillOpen(FileDescriptor fd, String host) throws Exception {
        try {
            assertTrue(fd.valid());
            sendRequest(fd, host);
            assertTrue(fd.valid());
        } finally {
            Os.close(fd);
        }
    }

    private void assertSocketClosed(FileDescriptor fd, String host) throws Exception {
        try {
            assertTrue(fd.valid());
            sendRequest(fd, host);
            fail("Socket opened before VPN connects should be closed when VPN connects");
        } catch (ErrnoException expected) {
            assertEquals(ECONNABORTED, expected.errno);
            assertTrue(fd.valid());
        } finally {
            Os.close(fd);
        }
    }

    private ContentResolver getContentResolver() {
        return mTestContext.getContentResolver();
    }

    private boolean isPrivateDnsInStrictMode() {
        return PRIVATE_DNS_MODE_PROVIDER_HOSTNAME.equals(
                Settings.Global.getString(getContentResolver(), PRIVATE_DNS_MODE_SETTING));
    }

    private void storePrivateDnsSetting() {
        mOldPrivateDnsMode = Settings.Global.getString(getContentResolver(),
                PRIVATE_DNS_MODE_SETTING);
        mOldPrivateDnsSpecifier = Settings.Global.getString(getContentResolver(),
                PRIVATE_DNS_SPECIFIER_SETTING);
    }

    private void restorePrivateDnsSetting() {
        Settings.Global.putString(getContentResolver(), PRIVATE_DNS_MODE_SETTING,
                mOldPrivateDnsMode);
        Settings.Global.putString(getContentResolver(), PRIVATE_DNS_SPECIFIER_SETTING,
                mOldPrivateDnsSpecifier);
    }

    private void expectPrivateDnsHostname(final String hostname) throws Exception {
        for (Network network : mCtsNetUtils.getTestableNetworks()) {
            // Wait for private DNS setting to propagate.
            mCtsNetUtils.awaitPrivateDnsSetting("Test wait private DNS setting timeout",
                    network, hostname, false);
        }
    }

    private void setAndVerifyPrivateDns(boolean strictMode) throws Exception {
        final ContentResolver cr = mTestContext.getContentResolver();
        String privateDnsHostname;

        if (strictMode) {
            privateDnsHostname = "vpncts-nx.metric.gstatic.com";
            Settings.Global.putString(cr, PRIVATE_DNS_SPECIFIER_SETTING, privateDnsHostname);
            Settings.Global.putString(cr, PRIVATE_DNS_MODE_SETTING,
                    PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
        } else {
            Settings.Global.putString(cr, PRIVATE_DNS_MODE_SETTING, PRIVATE_DNS_MODE_OPPORTUNISTIC);
            privateDnsHostname = null;
        }

        expectPrivateDnsHostname(privateDnsHostname);

        String randomName = "vpncts-" + new Random().nextInt(1000000000) + "-ds.metric.gstatic.com";
        if (strictMode) {
            // Strict mode private DNS is enabled. DNS lookups should fail, because the private DNS
            // server name is invalid.
            try {
                InetAddress.getByName(randomName);
                fail("VPN DNS lookup should fail with private DNS enabled");
            } catch (UnknownHostException expected) {
            }
        } else {
            // Strict mode private DNS is disabled. DNS lookup should succeed, because the VPN
            // provides no DNS servers, and thus DNS falls through to the default network.
            assertNotNull("VPN DNS lookup should succeed with private DNS disabled",
                    InetAddress.getByName(randomName));
        }
    }

    // Tests that strict mode private DNS is used on VPNs.
    private void checkStrictModePrivateDns() throws Exception {
        final boolean initialMode = isPrivateDnsInStrictMode();
        setAndVerifyPrivateDns(!initialMode);
        setAndVerifyPrivateDns(initialMode);
    }

    private NetworkRequest makeVpnNetworkRequest() {
        return new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
    }

    private void expectUnderlyingNetworks(TestableNetworkCallback callback,
            @Nullable List<Network> expectUnderlyingNetworks) {
        callback.eventuallyExpect(RecorderCallback.CallbackEntry.NETWORK_CAPS_UPDATED,
                NETWORK_CALLBACK_TIMEOUT_MS,
                entry -> (Objects.equals(expectUnderlyingNetworks,
                        entry.getCaps().getUnderlyingNetworks())));
    }

    private void expectVpnNetwork(TestableNetworkCallback callback) {
        callback.eventuallyExpect(RecorderCallback.CallbackEntry.NETWORK_CAPS_UPDATED,
                NETWORK_CALLBACK_TIMEOUT_MS,
                entry -> entry.getCaps().hasTransport(TRANSPORT_VPN));
    }

    @Test @IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    public void testChangeUnderlyingNetworks() throws Exception {
        assumeTrue(supportedHardware());
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_WIFI));
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_TELEPHONY));
        final TestableNetworkCallback callback = new TestableNetworkCallback();
        final boolean isWifiEnabled = mWifiManager.isWifiEnabled();
        testAndCleanup(() -> {
            // Ensure both of wifi and mobile data are connected.
            final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
            assertTrue("Wifi is not connected", (wifiNetwork != null));
            final Network cellNetwork = mCtsNetUtils.connectToCell();
            assertTrue("Mobile data is not connected", (cellNetwork != null));
            // Store current default network.
            final Network defaultNetwork = mCM.getActiveNetwork();
            // Start VPN and set empty array as its underlying networks.
            startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"} /* addresses */,
                    new String[] {"0.0.0.0/0", "::/0"} /* routes */,
                    "" /* allowedApplications */, "" /* disallowedApplications */,
                    null /* proxyInfo */, new ArrayList<>() /* underlyingNetworks */,
                    false /* isAlwaysMetered */);
            // Acquire the NETWORK_SETTINGS permission for getting the underlying networks.
            runWithShellPermissionIdentity(() -> {
                registerNetworkCallback(makeVpnNetworkRequest(), callback);
                // Check that this VPN doesn't have any underlying networks.
                expectUnderlyingNetworks(callback, new ArrayList<Network>());

                // Update the underlying networks to null and the underlying networks should follow
                // the system default network.
                updateUnderlyingNetworks(null);
                expectUnderlyingNetworks(callback, List.of(defaultNetwork));

                // Update the underlying networks to mobile data.
                updateUnderlyingNetworks(new ArrayList<>(List.of(cellNetwork)));
                // Check the underlying networks of NetworkCapabilities which comes from
                // onCapabilitiesChanged is mobile data.
                expectUnderlyingNetworks(callback, List.of(cellNetwork));

                // Update the underlying networks to wifi.
                updateUnderlyingNetworks(new ArrayList<>(List.of(wifiNetwork)));
                // Check the underlying networks of NetworkCapabilities which comes from
                // onCapabilitiesChanged is wifi.
                expectUnderlyingNetworks(callback, List.of(wifiNetwork));

                // Update the underlying networks to wifi and mobile data.
                updateUnderlyingNetworks(new ArrayList<>(List.of(wifiNetwork, cellNetwork)));
                // Check the underlying networks of NetworkCapabilities which comes from
                // onCapabilitiesChanged is wifi and mobile data.
                expectUnderlyingNetworks(callback, List.of(wifiNetwork, cellNetwork));
            }, NETWORK_SETTINGS);
        }, () -> {
                if (isWifiEnabled) {
                    mCtsNetUtils.ensureWifiConnected();
                } else {
                    mCtsNetUtils.ensureWifiDisconnected(null);
                }
            });
    }

    @Test
    public void testDefault() throws Exception {
        assumeTrue(supportedHardware());
        if (!SdkLevel.isAtLeastS() && (
                SystemProperties.getInt("persist.adb.tcp.port", -1) > -1
                        || SystemProperties.getInt("service.adb.tcp.port", -1) > -1)) {
            // If adb TCP port opened, this test may running by adb over network.
            // All of socket would be destroyed in this test. So this test don't
            // support adb over network, see b/119382723.
            // This is fixed in S, but still affects previous Android versions,
            // and this test must be backwards compatible.
            // TODO: Delete this code entirely when R is no longer supported.
            Log.i(TAG, "adb is running over the network, so skip this test");
            return;
        }

        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(
                mTargetContext, MyVpnService.ACTION_ESTABLISHED);
        receiver.register();

        // Test the behaviour of a variety of types of network callbacks.
        final Network defaultNetwork = mCM.getActiveNetwork();
        final TestableNetworkCallback systemDefaultCallback = new TestableNetworkCallback();
        final TestableNetworkCallback otherUidCallback = new TestableNetworkCallback();
        final TestableNetworkCallback myUidCallback = new TestableNetworkCallback();
        if (SdkLevel.isAtLeastS()) {
            final int otherUid =
                    UserHandle.of(5 /* userId */).getUid(Process.FIRST_APPLICATION_UID);
            final Handler h = new Handler(Looper.getMainLooper());
            runWithShellPermissionIdentity(() -> {
                registerSystemDefaultNetworkCallback(systemDefaultCallback, h);
                registerDefaultNetworkCallbackForUid(otherUid, otherUidCallback, h);
                registerDefaultNetworkCallbackForUid(Process.myUid(), myUidCallback, h);
            }, NETWORK_SETTINGS);
            for (TestableNetworkCallback callback :
                    List.of(systemDefaultCallback, otherUidCallback, myUidCallback)) {
                callback.expectAvailableCallbacks(defaultNetwork, false /* suspended */,
                        true /* validated */, false /* blocked */, TIMEOUT_MS);
            }
        }

        FileDescriptor fd = openSocketFdInOtherApp(TEST_HOST, 80, TIMEOUT_MS);

        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                 new String[] {"0.0.0.0/0", "::/0"},
                 "", "", null, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        final Intent intent = receiver.awaitForBroadcast(TimeUnit.MINUTES.toMillis(1));
        assertNotNull("Failed to receive broadcast from VPN service", intent);
        assertFalse("Wrong VpnService#isAlwaysOn",
                intent.getBooleanExtra(MyVpnService.EXTRA_ALWAYS_ON, true));
        assertFalse("Wrong VpnService#isLockdownEnabled",
                intent.getBooleanExtra(MyVpnService.EXTRA_LOCKDOWN_ENABLED, true));

        assertSocketClosed(fd, TEST_HOST);

        checkTrafficOnVpn();

        final Network vpnNetwork = mCM.getActiveNetwork();
        myUidCallback.expectAvailableThenValidatedCallbacks(vpnNetwork, TIMEOUT_MS);
        assertEquals(vpnNetwork, mCM.getActiveNetwork());
        assertNotEqual(defaultNetwork, vpnNetwork);
        maybeExpectVpnTransportInfo(vpnNetwork);
        assertEquals(TYPE_VPN, mCM.getNetworkInfo(vpnNetwork).getType());

        if (SdkLevel.isAtLeastT()) {
            runWithShellPermissionIdentity(() -> {
                final NetworkCapabilities nc = mCM.getNetworkCapabilities(vpnNetwork);
                assertNotNull(nc);
                assertNotNull(nc.getUnderlyingNetworks());
                assertEquals(defaultNetwork, new ArrayList<>(nc.getUnderlyingNetworks()).get(0));
            }, NETWORK_SETTINGS);
        }

        if (SdkLevel.isAtLeastS()) {
            // Check that system default network callback has not seen any network changes, even
            // though the app's default network changed. Also check that otherUidCallback saw no
            // network changes, because otherUid is in a different user and not subject to the VPN.
            // This needs to be done before testing  private DNS because checkStrictModePrivateDns
            // will set the private DNS server to a nonexistent name, which will cause validation to
            // fail and could cause the default network to switch (e.g., from wifi to cellular).
            systemDefaultCallback.assertNoCallback();
            otherUidCallback.assertNoCallback();
        }

        checkStrictModePrivateDns();

        receiver.unregisterQuietly();
    }

    @Test
    public void testAppAllowed() throws Exception {
        assumeTrue(supportedHardware());

        FileDescriptor fd = openSocketFdInOtherApp(TEST_HOST, 80, TIMEOUT_MS);

        // Shell app must not be put in here or it would kill the ADB-over-network use case
        String allowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                 new String[] {"192.0.2.0/24", "2001:db8::/32"},
                 allowedApps, "", null, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        assertSocketClosed(fd, TEST_HOST);

        checkTrafficOnVpn();

        maybeExpectVpnTransportInfo(mCM.getActiveNetwork());

        checkStrictModePrivateDns();
    }

    private int getSupportedKeepalives(NetworkCapabilities nc) throws Exception {
        // Get number of supported concurrent keepalives for testing network.
        final int[] keepalivesPerTransport = KeepaliveUtils.getSupportedKeepalives(
                mTargetContext);
        return KeepaliveUtils.getSupportedKeepalivesForNetworkCapabilities(
                keepalivesPerTransport, nc);
    }

    // This class can't be private, otherwise the constants can't be static imported.
    static class TestSocketKeepaliveCallback extends SocketKeepalive.Callback {
        // This must be larger than the alarm delay in AutomaticOnOffKeepaliveTracker.
        private static final int KEEPALIVE_TIMEOUT_MS = 10_000;
        public enum CallbackType {
            ON_STARTED,
            ON_RESUMED,
            ON_STOPPED,
            ON_PAUSED,
            ON_ERROR,
            ON_DATA_RECEIVED
        }
        private ArrayTrackRecord<CallbackType> mHistory = new ArrayTrackRecord<>();
        private ArrayTrackRecord<CallbackType>.ReadHead mEvents = mHistory.newReadHead();

        @Override
        public void onStarted() {
            mHistory.add(ON_STARTED);
        }

        @Override
        public void onResumed() {
            mHistory.add(ON_RESUMED);
        }

        @Override
        public void onStopped() {
            mHistory.add(ON_STOPPED);
        }

        @Override
        public void onPaused() {
            mHistory.add(ON_PAUSED);
        }

        @Override
        public void onError(final int error) {
            mHistory.add(ON_ERROR);
        }

        @Override
        public void onDataReceived() {
            mHistory.add(ON_DATA_RECEIVED);
        }

        public CallbackType poll() {
            return mEvents.poll(KEEPALIVE_TIMEOUT_MS, it -> true);
        }
    }

    private InetAddress getV4AddrByName(final String hostname) throws Exception {
        final InetAddress[] allAddrs = InetAddress.getAllByName(hostname);
        for (InetAddress addr : allAddrs) {
            if (addr instanceof Inet4Address) return addr;
        }
        return null;
    }

    @Test
    public void testAutomaticOnOffKeepaliveModeNoClose() throws Exception {
        doTestAutomaticOnOffKeepaliveMode(false);
    }

    @Test
    public void testAutomaticOnOffKeepaliveModeClose() throws Exception {
        doTestAutomaticOnOffKeepaliveMode(true);
    }

    private void startKeepalive(SocketKeepalive kp, TestSocketKeepaliveCallback callback) {
        runWithShellPermissionIdentity(() -> {
            // Only SocketKeepalive.start() requires READ_DEVICE_CONFIG because feature is protected
            // by a feature flag. But also verify ON_STARTED callback received here to ensure
            // keepalive is indeed started because start() runs in the executor thread and shell
            // permission may be dropped before reading DeviceConfig.
            kp.start(10 /* intervalSec */, SocketKeepalive.FLAG_AUTOMATIC_ON_OFF, mNetwork);

            // Verify callback status.
            assertEquals(ON_STARTED, callback.poll());
        }, READ_DEVICE_CONFIG);
    }

    private void doTestAutomaticOnOffKeepaliveMode(final boolean closeSocket) throws Exception {
        assumeTrue(supportedHardware());

        // Get default network first before starting VPN
        final Network defaultNetwork = mCM.getActiveNetwork();
        final TestableNetworkCallback cb = new TestableNetworkCallback();
        registerDefaultNetworkCallback(cb);
        cb.expect(CallbackEntry.AVAILABLE, defaultNetwork);
        final NetworkCapabilities cap =
                cb.expect(CallbackEntry.NETWORK_CAPS_UPDATED, defaultNetwork).getCaps();
        final LinkProperties lp =
                cb.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, defaultNetwork).getLp();
        cb.expect(CallbackEntry.BLOCKED_STATUS, defaultNetwork);

        // Setup VPN
        final FileDescriptor fd = openSocketFdInOtherApp(TEST_HOST, 80, TIMEOUT_MS);
        final String allowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        startVpn(new String[]{"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[]{"192.0.2.0/24", "2001:db8::/32"},
                allowedApps, "" /* disallowedApplications */, null /* proxyInfo */,
                null /* underlyingNetworks */, false /* isAlwaysMetered */);
        assertSocketClosed(fd, TEST_HOST);

        // Decrease the TCP polling timer for testing.
        runWithShellPermissionIdentity(() -> mCM.setTestLowTcpPollingTimerForKeepalive(
                System.currentTimeMillis() + TEST_TCP_POLLING_TIMER_EXPIRED_PERIOD_MS),
                NETWORK_SETTINGS);

        // Setup keepalive
        final int supported = getSupportedKeepalives(cap);
        assumeTrue("Network " + defaultNetwork + " does not support keepalive", supported != 0);
        final InetAddress srcAddr = CollectionUtils.findFirst(lp.getAddresses(),
                it -> it instanceof Inet4Address);
        assumeTrue("This test requires native IPv4", srcAddr != null);

        final TestSocketKeepaliveCallback callback = new TestSocketKeepaliveCallback();

        final String origMode = runWithShellPermissionIdentity(() -> {
            final String mode = DeviceConfig.getProperty(
                    DeviceConfig.NAMESPACE_TETHERING, AUTOMATIC_ON_OFF_KEEPALIVE_VERSION);
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_TETHERING,
                    AUTOMATIC_ON_OFF_KEEPALIVE_VERSION,
                    AUTOMATIC_ON_OFF_KEEPALIVE_ENABLED, false /* makeDefault */);
            return mode;
        }, READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG);

        final IpSecManager ipSec = mTargetContext.getSystemService(IpSecManager.class);
        SocketKeepalive kp = null;
        try (IpSecManager.UdpEncapsulationSocket nattSocket = ipSec.openUdpEncapsulationSocket()) {
            final InetAddress dstAddr = getV4AddrByName(TEST_HOST);
            assertNotNull(dstAddr);

            // Start keepalive with dynamic keepalive mode enabled.
            final Executor executor = mTargetContext.getMainExecutor();
            kp = mCM.createSocketKeepalive(defaultNetwork, nattSocket,
                    srcAddr, dstAddr, executor, callback);
            startKeepalive(kp, callback);

            // There should be no open sockets on the VPN network, because any
            // open sockets were closed when startVpn above was called. So the
            // first TCP poll should trigger ON_PAUSED.
            assertEquals(ON_PAUSED, callback.poll());

            final Socket s = new Socket();
            mNetwork.bindSocket(s);
            s.connect(new InetSocketAddress(dstAddr, 80));
            assertEquals(ON_RESUMED, callback.poll());

            if (closeSocket) {
                s.close();
                assertEquals(ON_PAUSED, callback.poll());
            }

            kp.stop();
            assertEquals(ON_STOPPED, callback.poll());
        } finally {
            if (kp != null) kp.stop();

            runWithShellPermissionIdentity(() -> {
                DeviceConfig.setProperty(
                                DeviceConfig.NAMESPACE_TETHERING,
                                AUTOMATIC_ON_OFF_KEEPALIVE_VERSION,
                                origMode, false);
                mCM.setTestLowTcpPollingTimerForKeepalive(0);
            }, WRITE_DEVICE_CONFIG, NETWORK_SETTINGS);
        }
    }

    @Test
    public void testAppDisallowed() throws Exception {
        assumeTrue(supportedHardware());

        FileDescriptor localFd = openSocketFd(TEST_HOST, 80, TIMEOUT_MS);
        FileDescriptor remoteFd = openSocketFdInOtherApp(TEST_HOST, 80, TIMEOUT_MS);

        String disallowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        if (!SdkLevel.isAtLeastS()) {
            // If adb TCP port opened, this test may running by adb over TCP.
            // Add com.android.shell application into disallowedApps to exclude adb socket for VPN
            // test, see b/119382723 (the test doesn't support adb over TCP when adb runs as root).
            //
            // This is fixed in S, but still affects previous Android versions,
            // and this test must be backwards compatible.
            // TODO: Delete this code entirely when R is no longer supported.
            disallowedApps = disallowedApps + ",com.android.shell";
        }
        Log.i(TAG, "Append shell app to disallowedApps: " + disallowedApps);
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"192.0.2.0/24", "2001:db8::/32"},
                "", disallowedApps, null, null /* underlyingNetworks */,
                false /* isAlwaysMetered */);

        assertSocketStillOpen(localFd, TEST_HOST);
        assertSocketStillOpen(remoteFd, TEST_HOST);

        checkNoTrafficOnVpn();

        final Network network = mCM.getActiveNetwork();
        final NetworkCapabilities nc = mCM.getNetworkCapabilities(network);
        assertFalse(nc.hasTransport(TRANSPORT_VPN));
    }

    @Test
    public void testSocketClosed() throws Exception {
        assumeTrue(supportedHardware());

        final FileDescriptor localFd = openSocketFd(TEST_HOST, 80, TIMEOUT_MS);
        final List<FileDescriptor> remoteFds = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            remoteFds.add(openSocketFdInOtherApp(TEST_HOST, 80, TIMEOUT_MS));
        }

        final String allowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"192.0.2.0/24", "2001:db8::/32"},
                allowedApps, "", null, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        // Socket owned by VPN uid is not closed
        assertSocketStillOpen(localFd, TEST_HOST);

        // Sockets not owned by VPN uid are closed
        for (final FileDescriptor remoteFd: remoteFds) {
            assertSocketClosed(remoteFd, TEST_HOST);
        }
    }

    @Test
    public void testExcludedRoutes() throws Exception {
        assumeTrue(supportedHardware());
        assumeTrue(SdkLevel.isAtLeastT());

        // Shell app must not be put in here or it would kill the ADB-over-network use case
        String allowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        startVpn(new String[]{"192.0.2.2/32", "2001:db8:1:2::ffe/128"} /* addresses */,
                new String[]{"0.0.0.0/0", "::/0"} /* routes */,
                new String[]{"192.0.2.0/24", "2001:db8::/32"} /* excludedRoutes */,
                allowedApps, "" /* disallowedApplications */, null /* proxyInfo */,
                null /* underlyingNetworks */, false /* isAlwaysMetered */);

        // Excluded routes should bypass VPN.
        checkTrafficBypassesVpn("192.0.2.1");
        checkTrafficBypassesVpn("2001:db8:dead:beef::f00");
        // Other routes should go through VPN, since default routes are included.
        checkTrafficOnVpn("198.51.100.1");
        checkTrafficOnVpn("2002:db8::1");
    }

    @Test
    public void testIncludedRoutes() throws Exception {
        assumeTrue(supportedHardware());

        // Shell app must not be put in here or it would kill the ADB-over-network use case
        String allowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        startVpn(new String[]{"192.0.2.2/32", "2001:db8:1:2::ffe/128"} /* addresses */,
                new String[]{"192.0.2.0/24", "2001:db8::/32"} /* routes */,
                allowedApps, "" /* disallowedApplications */, null /* proxyInfo */,
                null /* underlyingNetworks */, false /* isAlwaysMetered */);

        // Included routes should go through VPN.
        checkTrafficOnVpn("192.0.2.1");
        checkTrafficOnVpn("2001:db8:dead:beef::f00");
        // Other routes should bypass VPN, since default routes are not included.
        checkTrafficBypassesVpn("198.51.100.1");
        checkTrafficBypassesVpn("2002:db8::1");
    }

    @Test
    public void testInterleavedRoutes() throws Exception {
        assumeTrue(supportedHardware());
        assumeTrue(SdkLevel.isAtLeastT());

        // Shell app must not be put in here or it would kill the ADB-over-network use case
        String allowedApps = mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
        startVpn(new String[]{"192.0.2.2/32", "2001:db8:1:2::ffe/128"} /* addresses */,
                new String[]{"0.0.0.0/0", "192.0.2.0/32", "::/0", "2001:db8::/128"} /* routes */,
                new String[]{"192.0.2.0/24", "2001:db8::/32"} /* excludedRoutes */,
                allowedApps, "" /* disallowedApplications */, null /* proxyInfo */,
                null /* underlyingNetworks */, false /* isAlwaysMetered */,
                true /* addRoutesByIpPrefix */);

        // Excluded routes should bypass VPN.
        checkTrafficBypassesVpn("192.0.2.1");
        checkTrafficBypassesVpn("2001:db8:dead:beef::f00");

        // Included routes inside excluded routes should go through VPN, since the longest common
        // prefix precedes.
        checkTrafficOnVpn("192.0.2.0");
        checkTrafficOnVpn("2001:db8::");

        // Other routes should go through VPN, since default routes are included.
        checkTrafficOnVpn("198.51.100.1");
        checkTrafficOnVpn("2002:db8::1");
    }

    @Test
    public void testGetConnectionOwnerUidSecurity() throws Exception {
        assumeTrue(supportedHardware());

        DatagramSocket s;
        InetAddress address = InetAddress.getByName("localhost");
        s = new DatagramSocket();
        s.setSoTimeout(SOCKET_TIMEOUT_MS);
        s.connect(address, 7);
        InetSocketAddress loc = new InetSocketAddress(s.getLocalAddress(), s.getLocalPort());
        InetSocketAddress rem = new InetSocketAddress(s.getInetAddress(), s.getPort());
        try {
            int uid = mCM.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, loc, rem);
            assertEquals("Only an active VPN app should see connection information",
                    INVALID_UID, uid);
        } catch (SecurityException acceptable) {
            // R and below throw SecurityException if a non-active VPN calls this method.
            // As long as we can't actually get socket information, either behaviour is fine.
            return;
        }
    }

    @Test
    public void testSetProxy() throws  Exception {
        assumeTrue(supportedHardware());
        ProxyInfo initialProxy = mCM.getDefaultProxy();
        // Receiver for the proxy change broadcast.
        BlockingBroadcastReceiver proxyBroadcastReceiver = new ProxyChangeBroadcastReceiver();
        proxyBroadcastReceiver.register();

        String allowedApps = mPackageName;
        ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("10.0.0.1", 8888);
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "",
                testProxyInfo, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        // Check that the proxy change broadcast is received
        try {
            assertNotNull("No proxy change was broadcast when VPN is connected.",
                    proxyBroadcastReceiver.awaitForBroadcast());
        } finally {
            proxyBroadcastReceiver.unregisterQuietly();
        }

        // Proxy is set correctly in network and in link properties.
        assertNetworkHasExpectedProxy(testProxyInfo, mNetwork);
        assertDefaultProxy(testProxyInfo);

        proxyBroadcastReceiver = new ProxyChangeBroadcastReceiver();
        proxyBroadcastReceiver.register();
        stopVpn();
        try {
            assertNotNull("No proxy change was broadcast when VPN was disconnected.",
                    proxyBroadcastReceiver.awaitForBroadcast());
        } finally {
            proxyBroadcastReceiver.unregisterQuietly();
        }

        // After disconnecting from VPN, the proxy settings are the ones of the initial network.
        assertDefaultProxy(initialProxy);
    }

    @Test
    public void testSetProxyDisallowedApps() throws Exception {
        assumeTrue(supportedHardware());
        ProxyInfo initialProxy = mCM.getDefaultProxy();

        String disallowedApps = mPackageName;
        if (!SdkLevel.isAtLeastS()) {
            // If adb TCP port opened, this test may running by adb over TCP.
            // Add com.android.shell application into disallowedApps to exclude adb socket for VPN
            // test, see b/119382723 (the test doesn't support adb over TCP when adb runs as root).
            //
            // This is fixed in S, but still affects previous Android versions,
            // and this test must be backwards compatible.
            // TODO: Delete this code entirely when R is no longer supported.
            disallowedApps += ",com.android.shell";
        }
        ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("10.0.0.1", 8888);
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, "", disallowedApps,
                testProxyInfo, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        // The disallowed app does has the proxy configs of the default network.
        assertNetworkHasExpectedProxy(initialProxy, mCM.getActiveNetwork());
        assertDefaultProxy(initialProxy);
    }

    @Test
    public void testNoProxy() throws Exception {
        assumeTrue(supportedHardware());
        ProxyInfo initialProxy = mCM.getDefaultProxy();
        BlockingBroadcastReceiver proxyBroadcastReceiver = new ProxyChangeBroadcastReceiver();
        proxyBroadcastReceiver.register();
        String allowedApps = mPackageName;
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "", null,
                null /* underlyingNetworks */, false /* isAlwaysMetered */);

        try {
            assertNotNull("No proxy change was broadcast.",
                    proxyBroadcastReceiver.awaitForBroadcast());
        } finally {
            proxyBroadcastReceiver.unregisterQuietly();
        }

        // The VPN network has no proxy set.
        assertNetworkHasExpectedProxy(null, mNetwork);

        proxyBroadcastReceiver = new ProxyChangeBroadcastReceiver();
        proxyBroadcastReceiver.register();
        stopVpn();
        try {
            assertNotNull("No proxy change was broadcast.",
                    proxyBroadcastReceiver.awaitForBroadcast());
        } finally {
            proxyBroadcastReceiver.unregisterQuietly();
        }
        // After disconnecting from VPN, the proxy settings are the ones of the initial network.
        assertDefaultProxy(initialProxy);
        assertNetworkHasExpectedProxy(initialProxy, mCM.getActiveNetwork());
    }

    @Test
    public void testBindToNetworkWithProxy() throws Exception {
        assumeTrue(supportedHardware());
        String allowedApps = mPackageName;
        Network initialNetwork = mCM.getActiveNetwork();
        ProxyInfo initialProxy = mCM.getDefaultProxy();
        ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("10.0.0.1", 8888);
        // Receiver for the proxy change broadcast.
        BlockingBroadcastReceiver proxyBroadcastReceiver = new ProxyChangeBroadcastReceiver();
        proxyBroadcastReceiver.register();
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "",
                testProxyInfo, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        assertDefaultProxy(testProxyInfo);
        mCM.bindProcessToNetwork(initialNetwork);
        try {
            assertNotNull("No proxy change was broadcast.",
                proxyBroadcastReceiver.awaitForBroadcast());
        } finally {
            proxyBroadcastReceiver.unregisterQuietly();
        }
        assertDefaultProxy(initialProxy);
    }

    @Test
    public void testVpnMeterednessWithNoUnderlyingNetwork() throws Exception {
        if (!supportedHardware()) {
            return;
        }
        // VPN is not routing any traffic i.e. its underlying networks is an empty array.
        ArrayList<Network> underlyingNetworks = new ArrayList<>();
        String allowedApps = mPackageName;

        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "", null,
                underlyingNetworks, false /* isAlwaysMetered */);

        // VPN should now be the active network.
        assertEquals(mNetwork, mCM.getActiveNetwork());
        assertVpnTransportContains(NetworkCapabilities.TRANSPORT_VPN);
        // VPN with no underlying networks should be metered by default.
        assertTrue(isNetworkMetered(mNetwork));
        assertTrue(mCM.isActiveNetworkMetered());

        maybeExpectVpnTransportInfo(mCM.getActiveNetwork());

        if (SdkLevel.isAtLeastT()) {
            runWithShellPermissionIdentity(() -> {
                final NetworkCapabilities nc = mCM.getNetworkCapabilities(mNetwork);
                assertNotNull(nc);
                assertNotNull(nc.getUnderlyingNetworks());
                assertEquals(underlyingNetworks, new ArrayList<>(nc.getUnderlyingNetworks()));
            }, NETWORK_SETTINGS);
        }
    }

    @Test
    public void testVpnMeterednessWithNullUnderlyingNetwork() throws Exception {
        if (!supportedHardware()) {
            return;
        }
        Network underlyingNetwork = mCM.getActiveNetwork();
        if (underlyingNetwork == null) {
            Log.i(TAG, "testVpnMeterednessWithNullUnderlyingNetwork cannot execute"
                    + " unless there is an active network");
            return;
        }
        // VPN tracks platform default.
        ArrayList<Network> underlyingNetworks = null;
        String allowedApps = mPackageName;

        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "", null,
                underlyingNetworks, false /*isAlwaysMetered */);

        // Ensure VPN transports contains underlying network's transports.
        assertVpnTransportContains(underlyingNetwork);
        // Its meteredness should be same as that of underlying network.
        assertEquals(isNetworkMetered(underlyingNetwork), isNetworkMetered(mNetwork));
        // Meteredness based on VPN capabilities and CM#isActiveNetworkMetered should be in sync.
        assertEquals(isNetworkMetered(mNetwork), mCM.isActiveNetworkMetered());

        maybeExpectVpnTransportInfo(mCM.getActiveNetwork());
    }

    @Test
    public void testVpnMeterednessWithNonNullUnderlyingNetwork() throws Exception {
        if (!supportedHardware()) {
            return;
        }
        Network underlyingNetwork = mCM.getActiveNetwork();
        if (underlyingNetwork == null) {
            Log.i(TAG, "testVpnMeterednessWithNonNullUnderlyingNetwork cannot execute"
                    + " unless there is an active network");
            return;
        }
        // VPN explicitly declares WiFi to be its underlying network.
        ArrayList<Network> underlyingNetworks = new ArrayList<>(1);
        underlyingNetworks.add(underlyingNetwork);
        String allowedApps = mPackageName;

        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "", null,
                underlyingNetworks, false /* isAlwaysMetered */);

        // Ensure VPN transports contains underlying network's transports.
        assertVpnTransportContains(underlyingNetwork);
        // Its meteredness should be same as that of underlying network.
        assertEquals(isNetworkMetered(underlyingNetwork), isNetworkMetered(mNetwork));
        // Meteredness based on VPN capabilities and CM#isActiveNetworkMetered should be in sync.
        assertEquals(isNetworkMetered(mNetwork), mCM.isActiveNetworkMetered());

        maybeExpectVpnTransportInfo(mCM.getActiveNetwork());

        if (SdkLevel.isAtLeastT()) {
            final Network vpnNetwork = mCM.getActiveNetwork();
            assertNotEqual(underlyingNetwork, vpnNetwork);
            runWithShellPermissionIdentity(() -> {
                final NetworkCapabilities nc = mCM.getNetworkCapabilities(vpnNetwork);
                assertNotNull(nc);
                assertNotNull(nc.getUnderlyingNetworks());
                final List<Network> underlying = nc.getUnderlyingNetworks();
                assertEquals(underlyingNetwork, underlying.get(0));
            }, NETWORK_SETTINGS);
        }
    }

    @Test
    public void testAlwaysMeteredVpnWithNullUnderlyingNetwork() throws Exception {
        if (!supportedHardware()) {
            return;
        }
        Network underlyingNetwork = mCM.getActiveNetwork();
        if (underlyingNetwork == null) {
            Log.i(TAG, "testAlwaysMeteredVpnWithNullUnderlyingNetwork cannot execute"
                    + " unless there is an active network");
            return;
        }
        // VPN tracks platform default.
        ArrayList<Network> underlyingNetworks = null;
        String allowedApps = mPackageName;
        boolean isAlwaysMetered = true;

        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "", null,
                underlyingNetworks, isAlwaysMetered);

        // VPN's meteredness does not depend on underlying network since it is always metered.
        assertTrue(isNetworkMetered(mNetwork));
        assertTrue(mCM.isActiveNetworkMetered());

        maybeExpectVpnTransportInfo(mCM.getActiveNetwork());
    }

    @Test
    public void testAlwaysMeteredVpnWithNonNullUnderlyingNetwork() throws Exception {
        if (!supportedHardware()) {
            return;
        }
        Network underlyingNetwork = mCM.getActiveNetwork();
        if (underlyingNetwork == null) {
            Log.i(TAG, "testAlwaysMeteredVpnWithNonNullUnderlyingNetwork cannot execute"
                    + " unless there is an active network");
            return;
        }
        // VPN explicitly declares its underlying network.
        ArrayList<Network> underlyingNetworks = new ArrayList<>(1);
        underlyingNetworks.add(underlyingNetwork);
        String allowedApps = mPackageName;
        boolean isAlwaysMetered = true;

        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"0.0.0.0/0", "::/0"}, allowedApps, "", null,
                underlyingNetworks, isAlwaysMetered);

        // VPN's meteredness does not depend on underlying network since it is always metered.
        assertTrue(isNetworkMetered(mNetwork));
        assertTrue(mCM.isActiveNetworkMetered());

        maybeExpectVpnTransportInfo(mCM.getActiveNetwork());

        if (SdkLevel.isAtLeastT()) {
            final Network vpnNetwork = mCM.getActiveNetwork();
            assertNotEqual(underlyingNetwork, vpnNetwork);
            runWithShellPermissionIdentity(() -> {
                final NetworkCapabilities nc = mCM.getNetworkCapabilities(vpnNetwork);
                assertNotNull(nc);
                assertNotNull(nc.getUnderlyingNetworks());
                final List<Network> underlying = nc.getUnderlyingNetworks();
                assertEquals(underlyingNetwork, underlying.get(0));
            }, NETWORK_SETTINGS);
        }
    }

    @Test
    public void testB141603906() throws Exception {
        if (!supportedHardware()) {
            return;
        }
        final InetSocketAddress src = new InetSocketAddress(0);
        final InetSocketAddress dst = new InetSocketAddress(0);
        final int NUM_THREADS = 8;
        final int NUM_SOCKETS = 5000;
        final Thread[] threads = new Thread[NUM_THREADS];
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                 new String[] {"0.0.0.0/0", "::/0"},
                 "" /* allowedApplications */, "com.android.shell" /* disallowedApplications */,
                null /* proxyInfo */, null /* underlyingNetworks */, false /* isAlwaysMetered */);

        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < NUM_SOCKETS; j++) {
                    mCM.getConnectionOwnerUid(IPPROTO_TCP, src, dst);
                }
            });
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        stopVpn();
    }

    private boolean isNetworkMetered(Network network) {
        NetworkCapabilities nc = mCM.getNetworkCapabilities(network);
        return !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    private void assertVpnTransportContains(Network underlyingNetwork) {
        int[] transports = mCM.getNetworkCapabilities(underlyingNetwork).getTransportTypes();
        assertVpnTransportContains(transports);
    }

    private void assertVpnTransportContains(int... transports) {
        NetworkCapabilities vpnCaps = mCM.getNetworkCapabilities(mNetwork);
        for (int transport : transports) {
            assertTrue(vpnCaps.hasTransport(transport));
        }
    }

    private void maybeExpectVpnTransportInfo(Network network) {
        assumeTrue(SdkLevel.isAtLeastS());
        final NetworkCapabilities vpnNc = mCM.getNetworkCapabilities(network);
        assertTrue(vpnNc.hasTransport(TRANSPORT_VPN));
        final TransportInfo ti = vpnNc.getTransportInfo();
        assertTrue(ti instanceof VpnTransportInfo);
        assertEquals(VpnManager.TYPE_VPN_SERVICE, ((VpnTransportInfo) ti).getType());
    }

    private void assertDefaultProxy(ProxyInfo expected) throws Exception {
        assertEquals("Incorrect proxy config.", expected, mCM.getDefaultProxy());
        String expectedHost = expected == null ? null : expected.getHost();
        String expectedPort = expected == null ? null : String.valueOf(expected.getPort());

        // ActivityThread may not have time to set it in the properties yet which will cause flakes.
        // Wait for some time to deflake the test.
        int attempt = 0;
        while (!(Objects.equals(expectedHost, System.getProperty("http.proxyHost"))
                && Objects.equals(expectedPort, System.getProperty("http.proxyPort")))
                && attempt < 300) {
            attempt++;
            Log.d(TAG, "Wait for proxy being updated, attempt=" + attempt);
            Thread.sleep(100);
        }
        assertEquals("Incorrect proxy host system property.", expectedHost,
            System.getProperty("http.proxyHost"));
        assertEquals("Incorrect proxy port system property.", expectedPort,
            System.getProperty("http.proxyPort"));
    }

    private void assertNetworkHasExpectedProxy(ProxyInfo expected, Network network) {
        LinkProperties lp = mCM.getLinkProperties(network);
        assertNotNull("The network link properties object is null.", lp);
        assertEquals("Incorrect proxy config.", expected, lp.getHttpProxy());

        assertEquals(expected, mCM.getProxyForNetwork(network));
    }

    class ProxyChangeBroadcastReceiver extends BlockingBroadcastReceiver {
        private boolean received;

        public ProxyChangeBroadcastReceiver() {
            super(mTestContext, Proxy.PROXY_CHANGE_ACTION);
            received = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!received) {
                // Do not call onReceive() more than once.
                super.onReceive(context, intent);
            }
            received = true;
        }
    }

    /**
     * Verifies that DownloadManager has CONNECTIVITY_USE_RESTRICTED_NETWORKS permission that can
     * bind socket to VPN when it is in VPN disallowed list but requested downloading app is in VPN
     * allowed list.
     * See b/165774987.
     */
    @Test
    public void testDownloadWithDownloadManagerDisallowed() throws Exception {
        assumeTrue(supportedHardware());

        // Start a VPN with DownloadManager package in disallowed list.
        startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                new String[] {"192.0.2.0/24", "2001:db8::/32"},
                "" /* allowedApps */, "com.android.providers.downloads", null /* proxyInfo */,
                null /* underlyingNetworks */, false /* isAlwaysMetered */);

        final DownloadManager dm = mTestContext.getSystemService(DownloadManager.class);
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            final int flags = SdkLevel.isAtLeastT() ? RECEIVER_EXPORTED : 0;
            mTestContext.registerReceiver(receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flags);

            // Enqueue a request and check only one download.
            final long id = dm.enqueue(new Request(
                    Uri.parse("https://google-ipv6test.appspot.com/ip.js?fmt=text")));
            assertEquals(1, getTotalNumberDownloads(dm, new Query()));
            assertEquals(1, getTotalNumberDownloads(dm, new Query().setFilterById(id)));

            // Wait for download complete and check status.
            assertEquals(id, receiver.get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(1, getTotalNumberDownloads(dm,
                    new Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)));

            // Remove download.
            assertEquals(1, dm.remove(id));
            assertEquals(0, getTotalNumberDownloads(dm, new Query()));
        } finally {
            mTestContext.unregisterReceiver(receiver);
        }
    }

    private static int getTotalNumberDownloads(final DownloadManager dm, final Query query) {
        try (Cursor cursor = dm.query(query)) { return cursor.getCount(); }
    }

    private static class DownloadCompleteReceiver extends BroadcastReceiver {
        private final CompletableFuture<Long> future = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            future.complete(intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, -1 /* defaultValue */));
        }

        public long get(long timeout, TimeUnit unit) throws Exception {
            return future.get(timeout, unit);
        }
    }

    private static final boolean EXPECT_PASS = false;
    private static final boolean EXPECT_BLOCK = true;

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBlockIncomingPackets() throws Exception {
        assumeTrue(supportedHardware());
        final Network network = mCM.getActiveNetwork();
        assertNotNull("Requires a working Internet connection", network);

        final int remoteUid = mRemoteSocketFactoryClient.getUid();
        final List<Range<Integer>> lockdownRange = List.of(new Range<>(remoteUid, remoteUid));
        final DetailedBlockedStatusCallback remoteUidCallback = new DetailedBlockedStatusCallback();

        // Create a TUN interface
        final FileDescriptor tunFd = runWithShellPermissionIdentity(() -> {
            final TestNetworkManager tnm = mTestContext.getSystemService(TestNetworkManager.class);
            final TestNetworkInterface iface = tnm.createTunInterface(List.of(
                    TEST_IP4_DST_ADDR, TEST_IP6_DST_ADDR));
            return iface.getFileDescriptor().getFileDescriptor();
        }, MANAGE_TEST_NETWORKS);

        // Create a remote UDP socket
        final FileDescriptor remoteUdpFd = mRemoteSocketFactoryClient.openDatagramSocketFd();

        testAndCleanup(() -> {
            runWithShellPermissionIdentity(() -> {
                registerDefaultNetworkCallbackForUid(remoteUid, remoteUidCallback,
                        new Handler(Looper.getMainLooper()));
            }, NETWORK_SETTINGS);
            remoteUidCallback.expectAvailableCallbacksWithBlockedReasonNone(network);

            // The remote UDP socket can receive packets coming from the TUN interface
            checkBlockIncomingPacket(tunFd, remoteUdpFd, EXPECT_PASS);

            // Lockdown uid that has the remote UDP socket
            runWithShellPermissionIdentity(() -> {
                mCM.setRequireVpnForUids(true /* requireVpn */, lockdownRange);
            }, NETWORK_SETTINGS);

            // setRequireVpnForUids setup a lockdown rule asynchronously. So it needs to wait for
            // BlockedStatusCallback to be fired before checking the blocking status of incoming
            // packets.
            remoteUidCallback.expect(BLOCKED_STATUS_INT, network,
                    cb -> cb.getReason() == BLOCKED_REASON_LOCKDOWN_VPN);

            if (SdkLevel.isAtLeastT()) {
                // On T and above, lockdown rule drop packets not coming from lo regardless of the
                // VPN connectivity.
                checkBlockIncomingPacket(tunFd, remoteUdpFd, EXPECT_BLOCK);
            }

            // Start the VPN that has default routes. This VPN should have interface filtering rule
            // for incoming packet and drop packets not coming from lo nor the VPN interface.
            final String allowedApps =
                    mRemoteSocketFactoryClient.getPackageName() + "," + mPackageName;
            startVpn(new String[]{"192.0.2.2/32", "2001:db8:1:2::ffe/128"},
                    new String[]{"0.0.0.0/0", "::/0"}, allowedApps, "" /* disallowedApplications */,
                    null /* proxyInfo */, null /* underlyingNetworks */,
                    false /* isAlwaysMetered */);

            checkBlockIncomingPacket(tunFd, remoteUdpFd, EXPECT_BLOCK);
        }, /* cleanup */ () -> {
                Os.close(tunFd);
            }, /* cleanup */ () -> {
                Os.close(remoteUdpFd);
            }, /* cleanup */ () -> {
                runWithShellPermissionIdentity(() -> {
                    mCM.setRequireVpnForUids(false /* requireVpn */, lockdownRange);
                }, NETWORK_SETTINGS);
            });
    }

    @Test
    public void testSetVpnDefaultForUids() throws Exception {
        assumeTrue(supportedHardware());
        assumeTrue(SdkLevel.isAtLeastU());

        final Network defaultNetwork = mCM.getActiveNetwork();
        assertNotNull("There must be a default network", defaultNetwork);

        final TestableNetworkCallback defaultNetworkCallback = new TestableNetworkCallback();
        final String session = UUID.randomUUID().toString();
        final int myUid = Process.myUid();

        testAndCleanup(() -> {
            registerDefaultNetworkCallback(defaultNetworkCallback);
            defaultNetworkCallback.expectAvailableCallbacks(defaultNetwork);

            final Range<Integer> myUidRange = new Range<>(myUid, myUid);
            runWithShellPermissionIdentity(() -> {
                mCM.setVpnDefaultForUids(session, List.of(myUidRange));
            }, NETWORK_SETTINGS);

            // The VPN will be the only default network for the app, so it's expected to receive
            // onLost() callback.
            defaultNetworkCallback.eventuallyExpect(CallbackEntry.LOST);

            final ArrayList<Network> underlyingNetworks = new ArrayList<>();
            underlyingNetworks.add(defaultNetwork);
            startVpn(new String[] {"192.0.2.2/32", "2001:db8:1:2::ffe/128"} /* addresses */,
                    new String[] {"0.0.0.0/0", "::/0"} /* routes */,
                    "" /* allowedApplications */, "" /* disallowedApplications */,
                    null /* proxyInfo */, underlyingNetworks, false /* isAlwaysMetered */);

            expectVpnNetwork(defaultNetworkCallback);
        }, /* cleanup */ () -> {
                stopVpn();
                defaultNetworkCallback.eventuallyExpect(CallbackEntry.LOST);
            }, /* cleanup */ () -> {
                runWithShellPermissionIdentity(() -> {
                    mCM.setVpnDefaultForUids(session, new ArraySet<>());
                }, NETWORK_SETTINGS);
                // The default network of the app will be changed back to wifi when the VPN network
                // preference feature is disabled.
                defaultNetworkCallback.eventuallyExpect(CallbackEntry.AVAILABLE,
                        NETWORK_CALLBACK_TIMEOUT_MS,
                        entry -> defaultNetwork.equals(entry.getNetwork()));
            });
    }

    private ByteBuffer buildIpv4UdpPacket(final Inet4Address dstAddr, final Inet4Address srcAddr,
            final short dstPort, final short srcPort, final byte[] payload) throws IOException {

        final ByteBuffer buffer = PacketBuilder.allocate(false /* hasEther */,
                OsConstants.IPPROTO_IP, OsConstants.IPPROTO_UDP, payload.length);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);

        packetBuilder.writeIpv4Header(
                (byte) 0 /* TOS */,
                (short) 27149 /* ID */,
                (short) 0x4000 /* flags=DF, offset=0 */,
                (byte) 64 /* TTL */,
                (byte) OsConstants.IPPROTO_UDP,
                srcAddr,
                dstAddr);
        packetBuilder.writeUdpHeader(srcPort, dstPort);
        buffer.put(payload);

        return packetBuilder.finalizePacket();
    }

    private ByteBuffer buildIpv6UdpPacket(final Inet6Address dstAddr, final Inet6Address srcAddr,
            final short dstPort, final short srcPort, final byte[] payload) throws IOException {

        final ByteBuffer buffer = PacketBuilder.allocate(false /* hasEther */,
                OsConstants.IPPROTO_IPV6, OsConstants.IPPROTO_UDP, payload.length);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);

        packetBuilder.writeIpv6Header(
                0x60000000 /* version=6, traffic class=0, flow label=0 */,
                (byte) OsConstants.IPPROTO_UDP,
                (short) 64 /* hop limit */,
                srcAddr,
                dstAddr);
        packetBuilder.writeUdpHeader(srcPort, dstPort);
        buffer.put(payload);

        return packetBuilder.finalizePacket();
    }

    private void checkBlockUdp(
            final FileDescriptor srcTunFd,
            final FileDescriptor dstUdpFd,
            final boolean ipv6,
            final boolean expectBlock) throws Exception {
        final Random random = new Random();
        final byte[] sendData = new byte[100];
        random.nextBytes(sendData);
        final short dstPort = (short) ((InetSocketAddress) Os.getsockname(dstUdpFd)).getPort();

        ByteBuffer buf;
        if (ipv6) {
            buf = buildIpv6UdpPacket(
                    (Inet6Address) TEST_IP6_DST_ADDR.getAddress(),
                    (Inet6Address) TEST_IP6_SRC_ADDR.getAddress(),
                    dstPort, TEST_SRC_PORT, sendData);
        } else {
            buf = buildIpv4UdpPacket(
                    (Inet4Address) TEST_IP4_DST_ADDR.getAddress(),
                    (Inet4Address) TEST_IP4_SRC_ADDR.getAddress(),
                    dstPort, TEST_SRC_PORT, sendData);
        }

        Os.write(srcTunFd, buf);

        final StructPollfd pollfd = new StructPollfd();
        pollfd.events = (short) POLLIN;
        pollfd.fd = dstUdpFd;
        final int ret = Os.poll(new StructPollfd[]{pollfd}, SOCKET_TIMEOUT_MS);

        if (expectBlock) {
            assertEquals("Expect not to receive a packet but received a packet", 0, ret);
        } else {
            assertEquals("Expect to receive a packet but did not receive a packet", 1, ret);
            final byte[] recvData = new byte[sendData.length];
            final int readSize = Os.read(dstUdpFd, recvData, 0 /* byteOffset */, recvData.length);
            assertEquals(recvData.length, readSize);
            MoreAsserts.assertEquals(sendData, recvData);
        }
    }

    private void checkBlockIncomingPacket(
            final FileDescriptor srcTunFd,
            final FileDescriptor dstUdpFd,
            final boolean expectBlock) throws Exception {
        checkBlockUdp(srcTunFd, dstUdpFd, false /* ipv6 */, expectBlock);
        checkBlockUdp(srcTunFd, dstUdpFd, true /* ipv6 */, expectBlock);
    }

    private class DetailedBlockedStatusCallback extends TestableNetworkCallback {
        public void expectAvailableCallbacksWithBlockedReasonNone(Network network) {
            super.expectAvailableCallbacks(network, false /* suspended */, true /* validated */,
                    BLOCKED_REASON_NONE, NETWORK_CALLBACK_TIMEOUT_MS);
        }
        public void onBlockedStatusChanged(Network network, int blockedReasons) {
            getHistory().add(new CallbackEntry.BlockedStatusInt(network, blockedReasons));
        }
    }
}
