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

package com.android.server;

import static android.Manifest.permission.DEVICE_POWER;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.NETWORK_STACK;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.InetAddresses.parseNumericAddress;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.net.connectivity.ConnectivityCompatChanges.ENABLE_PLATFORM_MDNS_BACKEND;
import static android.net.connectivity.ConnectivityCompatChanges.RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS_T_AND_LATER;
import static android.net.nsd.NsdManager.FAILURE_BAD_PARAMETERS;
import static android.net.nsd.NsdManager.FAILURE_INTERNAL_ERROR;
import static android.net.nsd.NsdManager.FAILURE_MAX_LIMIT;
import static android.net.nsd.NsdManager.FAILURE_OPERATION_NOT_RUNNING;

import static com.android.networkstack.apishim.api33.ConstantsShim.REGISTER_NSD_OFFLOAD_ENGINE;
import static com.android.server.NsdService.DEFAULT_RUNNING_APP_ACTIVE_IMPORTANCE_CUTOFF;
import static com.android.server.NsdService.MdnsListener;
import static com.android.server.NsdService.NO_TRANSACTION;
import static com.android.server.NsdService.parseTypeAndSubtype;
import static com.android.testutils.ContextUtils.mockService;

import static libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import static libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManager.OnUidImportanceListener;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.INetd;
import android.net.Network;
import android.net.mdns.aidl.DiscoveryInfo;
import android.net.mdns.aidl.GetAddressInfo;
import android.net.mdns.aidl.IMDnsEventListener;
import android.net.mdns.aidl.RegistrationInfo;
import android.net.mdns.aidl.ResolutionInfo;
import android.net.nsd.AdvertisingRequest;
import android.net.nsd.INsdManagerCallback;
import android.net.nsd.INsdServiceConnector;
import android.net.nsd.MDnsManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdManager.RegistrationListener;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdManager.ServiceInfoCallback;
import android.net.nsd.NsdServiceInfo;
import android.net.nsd.OffloadEngine;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.metrics.NetworkNsdReportedMetrics;
import com.android.server.NsdService.Dependencies;
import com.android.server.connectivity.mdns.MdnsAdvertiser;
import com.android.server.connectivity.mdns.MdnsAdvertisingOptions;
import com.android.server.connectivity.mdns.MdnsDiscoveryManager;
import com.android.server.connectivity.mdns.MdnsInterfaceSocket;
import com.android.server.connectivity.mdns.MdnsSearchOptions;
import com.android.server.connectivity.mdns.MdnsServiceBrowserListener;
import com.android.server.connectivity.mdns.MdnsServiceInfo;
import com.android.server.connectivity.mdns.MdnsSocketProvider;
import com.android.server.connectivity.mdns.MdnsSocketProvider.SocketRequestMonitor;
import com.android.server.connectivity.mdns.util.MdnsUtils;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

// TODOs:
//  - test client can send requests and receive replies
//  - test NSD_ON ENABLE/DISABLED listening
@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class NsdServiceTest {
    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    static final int PROTOCOL = NsdManager.PROTOCOL_DNS_SD;
    private static final long CLEANUP_DELAY_MS = 500;
    private static final long TIMEOUT_MS = 500;
    private static final long TEST_TIME_MS = 123L;
    private static final String SERVICE_NAME = "a_name";
    private static final String SERVICE_TYPE = "_test._tcp";
    private static final String SERVICE_FULL_NAME = SERVICE_NAME + "." + SERVICE_TYPE;
    private static final String DOMAIN_NAME = "mytestdevice.local";
    private static final int PORT = 2201;
    private static final int IFACE_IDX_ANY = 0;
    private static final String IPV4_ADDRESS = "192.0.2.0";
    private static final String IPV6_ADDRESS = "2001:db8::";

    // Records INsdManagerCallback created when NsdService#connect is called.
    // Only accessed on the test thread, since NsdService#connect is called by the NsdManager
    // constructor called on the test thread.
    private final Queue<INsdManagerCallback> mCreatedCallbacks = new LinkedList<>();

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Rule
    public TestRule ignoreRule = new DevSdkIgnoreRule();
    @Mock Context mContext;
    @Mock PackageManager mPackageManager;
    @Mock ContentResolver mResolver;
    @Mock MDnsManager mMockMDnsM;
    @Mock Dependencies mDeps;
    @Mock MdnsDiscoveryManager mDiscoveryManager;
    @Mock MdnsAdvertiser mAdvertiser;
    @Mock MdnsSocketProvider mSocketProvider;
    @Mock WifiManager mWifiManager;
    @Mock WifiManager.MulticastLock mMulticastLock;
    @Mock ActivityManager mActivityManager;
    @Mock NetworkNsdReportedMetrics mMetrics;
    @Mock MdnsUtils.Clock mClock;
    SocketRequestMonitor mSocketRequestMonitor;
    OnUidImportanceListener mUidImportanceListener;
    HandlerThread mThread;
    TestHandler mHandler;
    NsdService mService;

    private static class LinkToDeathRecorder extends Binder {
        IBinder.DeathRecipient mDr;

        @Override
        public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
            super.linkToDeath(recipient, flags);
            mDr = recipient;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mThread = new HandlerThread("mock-service-handler");
        mThread.start();
        mHandler = new TestHandler(mThread.getLooper());
        when(mContext.getContentResolver()).thenReturn(mResolver);
        mockService(mContext, MDnsManager.class, MDnsManager.MDNS_SERVICE, mMockMDnsM);
        mockService(mContext, WifiManager.class, Context.WIFI_SERVICE, mWifiManager);
        mockService(mContext, ActivityManager.class, Context.ACTIVITY_SERVICE, mActivityManager);
        doReturn(mPackageManager).when(mContext).getPackageManager();
        if (mContext.getSystemService(MDnsManager.class) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(MDnsManager.class);
            doCallRealMethod().when(mContext).getSystemService(WifiManager.class);
            doCallRealMethod().when(mContext).getSystemService(ActivityManager.class);
        }
        doReturn(true).when(mMockMDnsM).registerService(
                anyInt(), anyString(), anyString(), anyInt(), any(), anyInt());
        doReturn(true).when(mMockMDnsM).stopOperation(anyInt());
        doReturn(true).when(mMockMDnsM).discover(anyInt(), anyString(), anyInt());
        doReturn(true).when(mMockMDnsM).resolve(
                anyInt(), anyString(), anyString(), anyString(), anyInt());
        doReturn(false).when(mDeps).isMdnsDiscoveryManagerEnabled(any(Context.class));
        doReturn(mDiscoveryManager).when(mDeps)
                .makeMdnsDiscoveryManager(any(), any(), any(), any());
        doReturn(mMulticastLock).when(mWifiManager).createMulticastLock(any());
        doReturn(mSocketProvider).when(mDeps).makeMdnsSocketProvider(any(), any(), any(), any());
        doReturn(DEFAULT_RUNNING_APP_ACTIVE_IMPORTANCE_CUTOFF).when(mDeps).getDeviceConfigInt(
                eq(NsdService.MDNS_CONFIG_RUNNING_APP_ACTIVE_IMPORTANCE_CUTOFF), anyInt());
        doReturn(mAdvertiser).when(mDeps).makeMdnsAdvertiser(any(), any(), any(), any(), any(),
                any());
        doReturn(mMetrics).when(mDeps).makeNetworkNsdReportedMetrics(anyInt());
        doReturn(mClock).when(mDeps).makeClock();
        doReturn(TEST_TIME_MS).when(mClock).elapsedRealtime();
        mService = makeService();
        final ArgumentCaptor<SocketRequestMonitor> cbMonitorCaptor =
                ArgumentCaptor.forClass(SocketRequestMonitor.class);
        verify(mDeps).makeMdnsSocketProvider(any(), any(), any(), cbMonitorCaptor.capture());
        mSocketRequestMonitor = cbMonitorCaptor.getValue();

        final ArgumentCaptor<OnUidImportanceListener> uidListenerCaptor =
                ArgumentCaptor.forClass(OnUidImportanceListener.class);
        verify(mActivityManager).addOnUidImportanceListener(uidListenerCaptor.capture(), anyInt());
        mUidImportanceListener = uidListenerCaptor.getValue();
    }

    @After
    public void tearDown() throws Exception {
        if (mThread != null) {
            mThread.quitSafely();
            mThread.join();
        }

        // Clear inline mocks as there are possible memory leaks if not done (see mockito
        // doc for clearInlineMocks), and some tests create many of them.
        Mockito.framework().clearInlineMocks();
    }

    // Native mdns provided by Netd is removed after U.
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    @DisableCompatChanges({
            RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS_T_AND_LATER,
            ENABLE_PLATFORM_MDNS_BACKEND})
    public void testPreSClients() throws Exception {
        // Pre S client connected, the daemon should be started.
        connectClient(mService);
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient1 = verifyLinkToDeath(cb1);
        verify(mMockMDnsM, times(1)).registerEventListener(any());
        verify(mMockMDnsM, times(1)).startDaemon();

        connectClient(mService);
        final INsdManagerCallback cb2 = getCallback();
        final IBinder.DeathRecipient deathRecipient2 = verifyLinkToDeath(cb2);
        // Daemon has been started, it should not try to start it again.
        verify(mMockMDnsM, times(1)).registerEventListener(any());
        verify(mMockMDnsM, times(1)).startDaemon();

        deathRecipient1.binderDied();
        // Still 1 client remains, daemon shouldn't be stopped.
        waitForIdle();
        verify(mMockMDnsM, never()).stopDaemon();

        deathRecipient2.binderDied();
        // All clients are disconnected, the daemon should be stopped.
        verifyDelayMaybeStopDaemon(CLEANUP_DELAY_MS);
    }

    @Test
    @EnableCompatChanges(RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS_T_AND_LATER)
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testNoDaemonStartedWhenClientsConnect() throws Exception {
        // Creating an NsdManager will not cause daemon startup.
        connectClient(mService);
        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient1 = verifyLinkToDeath(cb1);

        // Creating another NsdManager will not cause daemon startup either.
        connectClient(mService);
        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();
        final INsdManagerCallback cb2 = getCallback();
        final IBinder.DeathRecipient deathRecipient2 = verifyLinkToDeath(cb2);

        // If there is no active request, try to clean up the daemon but should not do it because
        // daemon has not been started.
        deathRecipient1.binderDied();
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();
        deathRecipient2.binderDied();
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();
    }

    private IBinder.DeathRecipient verifyLinkToDeath(INsdManagerCallback cb)
            throws Exception {
        final IBinder.DeathRecipient dr = ((LinkToDeathRecorder) cb.asBinder()).mDr;
        assertNotNull(dr);
        return dr;
    }

    @Test
    @EnableCompatChanges(RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS_T_AND_LATER)
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testClientRequestsAreGCedAtDisconnection() throws Exception {
        final NsdManager client = connectClient(mService);
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient = verifyLinkToDeath(cb1);
        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();

        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        request.setPort(PORT);

        // Client registration request
        final RegistrationListener listener1 = mock(RegistrationListener.class);
        client.registerService(request, PROTOCOL, listener1);
        waitForIdle();
        verify(mMockMDnsM).registerEventListener(any());
        verify(mMockMDnsM).startDaemon();
        verify(mMockMDnsM).registerService(
                eq(2), eq(SERVICE_NAME), eq(SERVICE_TYPE), eq(PORT), any(), eq(IFACE_IDX_ANY));

        // Client discovery request
        final DiscoveryListener listener2 = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, listener2);
        waitForIdle();
        verify(mMockMDnsM).discover(3 /* id */, SERVICE_TYPE, IFACE_IDX_ANY);

        // Client resolve request
        final ResolveListener listener3 = mock(ResolveListener.class);
        client.resolveService(request, listener3);
        waitForIdle();
        verify(mMockMDnsM).resolve(
                4 /* id */, SERVICE_NAME, SERVICE_TYPE, "local." /* domain */, IFACE_IDX_ANY);

        // Client disconnects, stop the daemon after CLEANUP_DELAY_MS.
        deathRecipient.binderDied();
        verifyDelayMaybeStopDaemon(CLEANUP_DELAY_MS);
        // checks that request are cleaned
        verify(mMockMDnsM).stopOperation(2 /* id */);
        verify(mMockMDnsM).stopOperation(3 /* id */);
        verify(mMockMDnsM).stopOperation(4 /* id */);
    }

    @Test
    @EnableCompatChanges(RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS_T_AND_LATER)
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testCleanupDelayNoRequestActive() throws Exception {
        final NsdManager client = connectClient(mService);

        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        request.setPort(PORT);
        final RegistrationListener listener1 = mock(RegistrationListener.class);
        client.registerService(request, PROTOCOL, listener1);
        waitForIdle();
        verify(mMockMDnsM).registerEventListener(any());
        verify(mMockMDnsM).startDaemon();
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient = verifyLinkToDeath(cb1);
        verify(mMockMDnsM).registerService(
                eq(2), eq(SERVICE_NAME), eq(SERVICE_TYPE), eq(PORT), any(), eq(IFACE_IDX_ANY));

        client.unregisterService(listener1);
        waitForIdle();
        verify(mMockMDnsM).stopOperation(2 /* id */);

        verifyDelayMaybeStopDaemon(CLEANUP_DELAY_MS);
        reset(mMockMDnsM);
        deathRecipient.binderDied();
        // Client disconnects, daemon should not be stopped after CLEANUP_DELAY_MS.
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();
    }

    private IMDnsEventListener getEventListener() {
        final ArgumentCaptor<IMDnsEventListener> listenerCaptor =
                ArgumentCaptor.forClass(IMDnsEventListener.class);
        verify(mMockMDnsM).registerEventListener(listenerCaptor.capture());
        return listenerCaptor.getValue();
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testDiscoverOnTetheringDownstream() throws Exception {
        final NsdManager client = connectClient(mService);
        final int interfaceIdx = 123;
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, discListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> discIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).discover(discIdCaptor.capture(), eq(SERVICE_TYPE),
                eq(0) /* interfaceIdx */);
        // NsdManager uses a separate HandlerThread to dispatch callbacks (on ServiceHandler), so
        // this needs to use a timeout
        verify(discListener, timeout(TIMEOUT_MS)).onDiscoveryStarted(SERVICE_TYPE);
        final int discId = discIdCaptor.getValue();
        verify(mMetrics).reportServiceDiscoveryStarted(true /* isLegacy */, discId);

        final DiscoveryInfo discoveryInfo = new DiscoveryInfo(
                discId,
                IMDnsEventListener.SERVICE_FOUND,
                SERVICE_NAME,
                SERVICE_TYPE,
                DOMAIN_NAME,
                interfaceIdx,
                INetd.LOCAL_NET_ID); // LOCAL_NET_ID (99) used on tethering downstreams
        eventListener.onServiceDiscoveryStatus(discoveryInfo);
        waitForIdle();

        final ArgumentCaptor<NsdServiceInfo> discoveredInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(discListener, timeout(TIMEOUT_MS)).onServiceFound(discoveredInfoCaptor.capture());
        final NsdServiceInfo foundInfo = discoveredInfoCaptor.getValue();
        assertEquals(SERVICE_NAME, foundInfo.getServiceName());
        assertEquals(SERVICE_TYPE, foundInfo.getServiceType());
        assertNull(foundInfo.getHost());
        assertNull(foundInfo.getNetwork());
        assertEquals(interfaceIdx, foundInfo.getInterfaceIndex());

        // After discovering the service, verify resolving it
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(foundInfo, resolveListener);
        waitForIdle();

        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(SERVICE_NAME), eq(SERVICE_TYPE),
                eq("local.") /* domain */, eq(interfaceIdx));

        final int servicePort = 10123;
        final ResolutionInfo resolutionInfo = new ResolutionInfo(
                resolvIdCaptor.getValue(),
                IMDnsEventListener.SERVICE_RESOLVED,
                null /* serviceName */,
                null /* serviceType */,
                null /* domain */,
                SERVICE_FULL_NAME,
                DOMAIN_NAME,
                servicePort,
                new byte[0] /* txtRecord */,
                interfaceIdx);

        doReturn(true).when(mMockMDnsM).getServiceAddress(anyInt(), any(), anyInt());
        eventListener.onServiceResolutionStatus(resolutionInfo);
        waitForIdle();

        final ArgumentCaptor<Integer> getAddrIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).getServiceAddress(getAddrIdCaptor.capture(), eq(DOMAIN_NAME),
                eq(interfaceIdx));

        final String serviceAddress = "192.0.2.123";
        final int getAddrId = getAddrIdCaptor.getValue();
        final GetAddressInfo addressInfo = new GetAddressInfo(
                getAddrId,
                IMDnsEventListener.SERVICE_GET_ADDR_SUCCESS,
                SERVICE_FULL_NAME,
                serviceAddress,
                interfaceIdx,
                INetd.LOCAL_NET_ID);
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        eventListener.onGettingServiceAddressStatus(addressInfo);
        waitForIdle();

        final ArgumentCaptor<NsdServiceInfo> resInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(resolveListener, timeout(TIMEOUT_MS)).onServiceResolved(resInfoCaptor.capture());
        verify(mMetrics).reportServiceResolved(true /* isLegacy */, getAddrId, 10L /* durationMs */,
                false /* isServiceFromCache */, 0 /* sentQueryCount */);

        final NsdServiceInfo resolvedService = resInfoCaptor.getValue();
        assertEquals(SERVICE_NAME, resolvedService.getServiceName());
        assertEquals("." + SERVICE_TYPE, resolvedService.getServiceType());
        assertEquals(parseNumericAddress(serviceAddress), resolvedService.getHost());
        assertEquals(servicePort, resolvedService.getPort());
        assertNull(resolvedService.getNetwork());
        assertEquals(interfaceIdx, resolvedService.getInterfaceIndex());
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testDiscoverOnBlackholeNetwork() throws Exception {
        final NsdManager client = connectClient(mService);
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, discListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> discIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).discover(discIdCaptor.capture(), eq(SERVICE_TYPE),
                eq(0) /* interfaceIdx */);
        // NsdManager uses a separate HandlerThread to dispatch callbacks (on ServiceHandler), so
        // this needs to use a timeout
        verify(discListener, timeout(TIMEOUT_MS)).onDiscoveryStarted(SERVICE_TYPE);
        final int discId = discIdCaptor.getValue();
        verify(mMetrics).reportServiceDiscoveryStarted(true /* isLegacy */, discId);

        final DiscoveryInfo discoveryInfo = new DiscoveryInfo(
                discId,
                IMDnsEventListener.SERVICE_FOUND,
                SERVICE_NAME,
                SERVICE_TYPE,
                DOMAIN_NAME,
                123 /* interfaceIdx */,
                INetd.DUMMY_NET_ID); // netId of the blackhole network
        eventListener.onServiceDiscoveryStatus(discoveryInfo);
        waitForIdle();

        verify(discListener, never()).onServiceFound(any());
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testServiceRegistrationSuccessfulAndFailed() throws Exception {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        request.setPort(PORT);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        client.registerService(request, PROTOCOL, regListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> regIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).registerService(regIdCaptor.capture(),
                eq(SERVICE_NAME), eq(SERVICE_TYPE), eq(PORT), any(), eq(IFACE_IDX_ANY));

        // Register service successfully.
        final int regId = regIdCaptor.getValue();
        final RegistrationInfo registrationInfo = new RegistrationInfo(
                regId,
                IMDnsEventListener.SERVICE_REGISTERED,
                SERVICE_NAME,
                SERVICE_TYPE,
                PORT,
                new byte[0] /* txtRecord */,
                IFACE_IDX_ANY);
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        eventListener.onServiceRegistrationStatus(registrationInfo);

        final ArgumentCaptor<NsdServiceInfo> registeredInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(regListener, timeout(TIMEOUT_MS))
                .onServiceRegistered(registeredInfoCaptor.capture());
        final NsdServiceInfo registeredInfo = registeredInfoCaptor.getValue();
        assertEquals(SERVICE_NAME, registeredInfo.getServiceName());
        verify(mMetrics).reportServiceRegistrationSucceeded(
                true /* isLegacy */, regId, 10L /* durationMs */);

        // Fail to register service.
        final RegistrationInfo registrationFailedInfo = new RegistrationInfo(
                regId,
                IMDnsEventListener.SERVICE_REGISTRATION_FAILED,
                null /* serviceName */,
                null /* registrationType */,
                0 /* port */,
                new byte[0] /* txtRecord */,
                IFACE_IDX_ANY);
        doReturn(TEST_TIME_MS + 20L).when(mClock).elapsedRealtime();
        eventListener.onServiceRegistrationStatus(registrationFailedInfo);
        verify(regListener, timeout(TIMEOUT_MS))
                .onRegistrationFailed(any(), eq(FAILURE_INTERNAL_ERROR));
        verify(mMetrics).reportServiceRegistrationFailed(
                true /* isLegacy */, regId, 20L /* durationMs */);
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testServiceDiscoveryFailed() throws Exception {
        final NsdManager client = connectClient(mService);
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, discListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> discIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).discover(discIdCaptor.capture(), eq(SERVICE_TYPE), eq(IFACE_IDX_ANY));
        verify(discListener, timeout(TIMEOUT_MS)).onDiscoveryStarted(SERVICE_TYPE);
        final int discId = discIdCaptor.getValue();
        verify(mMetrics).reportServiceDiscoveryStarted(true /* isLegacy */, discId);

        // Fail to discover service.
        final DiscoveryInfo discoveryFailedInfo = new DiscoveryInfo(
                discId,
                IMDnsEventListener.SERVICE_DISCOVERY_FAILED,
                null /* serviceName */,
                null /* registrationType */,
                null /* domainName */,
                IFACE_IDX_ANY,
                0 /* netId */);
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        eventListener.onServiceDiscoveryStatus(discoveryFailedInfo);
        verify(discListener, timeout(TIMEOUT_MS))
                .onStartDiscoveryFailed(SERVICE_TYPE, FAILURE_INTERNAL_ERROR);
        verify(mMetrics).reportServiceDiscoveryFailed(
                true /* isLegacy */, discId, 10L /* durationMs */);
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testServiceResolutionFailed() throws Exception {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(request, resolveListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(SERVICE_NAME), eq(SERVICE_TYPE),
                eq("local.") /* domain */, eq(IFACE_IDX_ANY));

        // Fail to resolve service.
        final int resolvId = resolvIdCaptor.getValue();
        final ResolutionInfo resolutionFailedInfo = new ResolutionInfo(
                resolvId,
                IMDnsEventListener.SERVICE_RESOLUTION_FAILED,
                null /* serviceName */,
                null /* serviceType */,
                null /* domain */,
                null /* serviceFullName */,
                null /* domainName */,
                0 /* port */,
                new byte[0] /* txtRecord */,
                IFACE_IDX_ANY);
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        eventListener.onServiceResolutionStatus(resolutionFailedInfo);
        verify(resolveListener, timeout(TIMEOUT_MS))
                .onResolveFailed(any(), eq(FAILURE_INTERNAL_ERROR));
        verify(mMetrics).reportServiceResolutionFailed(
                true /* isLegacy */, resolvId, 10L /* durationMs */);
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testGettingAddressFailed() throws Exception {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(request, resolveListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(SERVICE_NAME), eq(SERVICE_TYPE),
                eq("local.") /* domain */, eq(IFACE_IDX_ANY));

        // Resolve service successfully.
        final ResolutionInfo resolutionInfo = new ResolutionInfo(
                resolvIdCaptor.getValue(),
                IMDnsEventListener.SERVICE_RESOLVED,
                null /* serviceName */,
                null /* serviceType */,
                null /* domain */,
                SERVICE_FULL_NAME,
                DOMAIN_NAME,
                PORT,
                new byte[0] /* txtRecord */,
                IFACE_IDX_ANY);
        doReturn(true).when(mMockMDnsM).getServiceAddress(anyInt(), any(), anyInt());
        eventListener.onServiceResolutionStatus(resolutionInfo);
        waitForIdle();

        final ArgumentCaptor<Integer> getAddrIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).getServiceAddress(getAddrIdCaptor.capture(), eq(DOMAIN_NAME),
                eq(IFACE_IDX_ANY));

        // Fail to get service address.
        final int getAddrId = getAddrIdCaptor.getValue();
        final GetAddressInfo gettingAddrFailedInfo = new GetAddressInfo(
                getAddrId,
                IMDnsEventListener.SERVICE_GET_ADDR_FAILED,
                null /* hostname */,
                null /* address */,
                IFACE_IDX_ANY,
                0 /* netId */);
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        eventListener.onGettingServiceAddressStatus(gettingAddrFailedInfo);
        verify(resolveListener, timeout(TIMEOUT_MS))
                .onResolveFailed(any(), eq(FAILURE_INTERNAL_ERROR));
        verify(mMetrics).reportServiceResolutionFailed(
                true /* isLegacy */, getAddrId, 10L /* durationMs */);
    }

    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @Test
    public void testPerClientListenerLimit() throws Exception {
        final NsdManager client1 = connectClient(mService);
        final NsdManager client2 = connectClient(mService);

        final String testType1 = "_testtype1._tcp";
        final NsdServiceInfo testServiceInfo1 = new NsdServiceInfo("MyTestService1", testType1);
        testServiceInfo1.setPort(12345);
        final String testType2 = "_testtype2._tcp";
        final NsdServiceInfo testServiceInfo2 = new NsdServiceInfo("MyTestService2", testType2);
        testServiceInfo2.setPort(12345);

        // Each client can register 200 requests (for example 100 discover and 100 register).
        final int numEachListener = 100;
        final ArrayList<DiscoveryListener> discListeners = new ArrayList<>(numEachListener);
        final ArrayList<RegistrationListener> regListeners = new ArrayList<>(numEachListener);
        for (int i = 0; i < numEachListener; i++) {
            final DiscoveryListener discListener1 = mock(DiscoveryListener.class);
            discListeners.add(discListener1);
            final RegistrationListener regListener1 = mock(RegistrationListener.class);
            regListeners.add(regListener1);
            final DiscoveryListener discListener2 = mock(DiscoveryListener.class);
            discListeners.add(discListener2);
            final RegistrationListener regListener2 = mock(RegistrationListener.class);
            regListeners.add(regListener2);
            client1.discoverServices(testType1, NsdManager.PROTOCOL_DNS_SD,
                    (Network) null, Runnable::run, discListener1);
            client1.registerService(testServiceInfo1, NsdManager.PROTOCOL_DNS_SD, Runnable::run,
                    regListener1);

            client2.registerService(testServiceInfo2, NsdManager.PROTOCOL_DNS_SD, Runnable::run,
                    regListener2);
            client2.discoverServices(testType2, NsdManager.PROTOCOL_DNS_SD,
                    (Network) null, Runnable::run, discListener2);
        }

        // Use a longer timeout than usual for the handler to process all the events. The
        // registrations take about 1s on a high-end 2013 device.
        HandlerUtils.waitForIdle(mHandler, 30_000L);
        for (int i = 0; i < discListeners.size(); i++) {
            // Callbacks are sent on the manager handler which is different from mHandler, so use
            // a short timeout (each callback should come quickly after the previous one).
            verify(discListeners.get(i), timeout(TEST_TIME_MS))
                    .onDiscoveryStarted(i % 2 == 0 ? testType1 : testType2);

            // registerService does not get a callback before probing finishes (will not happen as
            // this is mocked)
            verifyNoMoreInteractions(regListeners.get(i));
        }

        // The next registrations should fail
        final DiscoveryListener failDiscListener1 = mock(DiscoveryListener.class);
        final RegistrationListener failRegListener1 = mock(RegistrationListener.class);
        final DiscoveryListener failDiscListener2 = mock(DiscoveryListener.class);
        final RegistrationListener failRegListener2 = mock(RegistrationListener.class);

        client1.discoverServices(testType1, NsdManager.PROTOCOL_DNS_SD,
                (Network) null, Runnable::run, failDiscListener1);
        verify(failDiscListener1, timeout(TEST_TIME_MS))
                .onStartDiscoveryFailed(testType1, FAILURE_MAX_LIMIT);

        client1.registerService(testServiceInfo1, NsdManager.PROTOCOL_DNS_SD, Runnable::run,
                failRegListener1);
        verify(failRegListener1, timeout(TEST_TIME_MS)).onRegistrationFailed(
                argThat(a -> testServiceInfo1.getServiceName().equals(a.getServiceName())),
                eq(FAILURE_MAX_LIMIT));

        client1.discoverServices(testType2, NsdManager.PROTOCOL_DNS_SD,
                (Network) null, Runnable::run, failDiscListener2);
        verify(failDiscListener2, timeout(TEST_TIME_MS))
                .onStartDiscoveryFailed(testType2, FAILURE_MAX_LIMIT);

        client1.registerService(testServiceInfo2, NsdManager.PROTOCOL_DNS_SD, Runnable::run,
                failRegListener2);
        verify(failRegListener2, timeout(TEST_TIME_MS)).onRegistrationFailed(
                argThat(a -> testServiceInfo2.getServiceName().equals(a.getServiceName())),
                eq(FAILURE_MAX_LIMIT));
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testNoCrashWhenProcessResolutionAfterBinderDied() throws Exception {
        final NsdManager client = connectClient(mService);
        final INsdManagerCallback cb = getCallback();
        final IBinder.DeathRecipient deathRecipient = verifyLinkToDeath(cb);
        deathRecipient.binderDied();

        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(request, resolveListener);
        waitForIdle();

        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();
        verify(mMockMDnsM, never()).resolve(anyInt() /* id */, anyString() /* serviceName */,
                anyString() /* registrationType */, anyString() /* domain */,
                anyInt()/* interfaceIdx */);
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testStopServiceResolution() {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(request, resolveListener);
        waitForIdle();

        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(SERVICE_NAME), eq(SERVICE_TYPE),
                eq("local.") /* domain */, eq(IFACE_IDX_ANY));

        final int resolveId = resolvIdCaptor.getValue();
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        client.stopServiceResolution(resolveListener);
        waitForIdle();

        verify(mMockMDnsM).stopOperation(resolveId);
        verify(resolveListener, timeout(TIMEOUT_MS)).onResolutionStopped(argThat(ns ->
                request.getServiceName().equals(ns.getServiceName())
                        && request.getServiceType().equals(ns.getServiceType())));
        verify(mMetrics).reportServiceResolutionStop(
                true /* isLegacy */, resolveId, 10L /* durationMs */);
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testStopResolutionFailed() {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(request, resolveListener);
        waitForIdle();

        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(SERVICE_NAME), eq(SERVICE_TYPE),
                eq("local.") /* domain */, eq(IFACE_IDX_ANY));

        final int resolveId = resolvIdCaptor.getValue();
        doReturn(false).when(mMockMDnsM).stopOperation(anyInt());
        client.stopServiceResolution(resolveListener);
        waitForIdle();

        verify(mMockMDnsM).stopOperation(resolveId);
        verify(resolveListener, timeout(TIMEOUT_MS)).onStopResolutionFailed(argThat(ns ->
                        request.getServiceName().equals(ns.getServiceName())
                                && request.getServiceType().equals(ns.getServiceType())),
                eq(FAILURE_OPERATION_NOT_RUNNING));
    }

    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testStopResolutionDuringGettingAddress() throws RemoteException {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(request, resolveListener);
        waitForIdle();

        final IMDnsEventListener eventListener = getEventListener();
        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(SERVICE_NAME), eq(SERVICE_TYPE),
                eq("local.") /* domain */, eq(IFACE_IDX_ANY));

        // Resolve service successfully.
        final ResolutionInfo resolutionInfo = new ResolutionInfo(
                resolvIdCaptor.getValue(),
                IMDnsEventListener.SERVICE_RESOLVED,
                null /* serviceName */,
                null /* serviceType */,
                null /* domain */,
                SERVICE_FULL_NAME,
                DOMAIN_NAME,
                PORT,
                new byte[0] /* txtRecord */,
                IFACE_IDX_ANY);
        doReturn(true).when(mMockMDnsM).getServiceAddress(anyInt(), any(), anyInt());
        eventListener.onServiceResolutionStatus(resolutionInfo);
        waitForIdle();

        final ArgumentCaptor<Integer> getAddrIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).getServiceAddress(getAddrIdCaptor.capture(), eq(DOMAIN_NAME),
                eq(IFACE_IDX_ANY));

        final int getAddrId = getAddrIdCaptor.getValue();
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        client.stopServiceResolution(resolveListener);
        waitForIdle();

        verify(mMockMDnsM).stopOperation(getAddrId);
        verify(resolveListener, timeout(TIMEOUT_MS)).onResolutionStopped(argThat(ns ->
                request.getServiceName().equals(ns.getServiceName())
                        && request.getServiceType().equals(ns.getServiceType())));
        verify(mMetrics).reportServiceResolutionStop(
                true /* isLegacy */, getAddrId, 10L /* durationMs */);
    }

    private void verifyUpdatedServiceInfo(NsdServiceInfo info, String serviceName,
            String serviceType, List<InetAddress> address, int port, int interfaceIndex,
            Network network) {
        assertEquals(serviceName, info.getServiceName());
        assertEquals(serviceType, info.getServiceType());
        assertEquals(address, info.getHostAddresses());
        assertEquals(port, info.getPort());
        assertEquals(network, info.getNetwork());
        assertEquals(interfaceIndex, info.getInterfaceIndex());
    }

    @Test
    public void testRegisterAndUnregisterServiceInfoCallback() {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        final ServiceInfoCallback serviceInfoCallback = mock(
                ServiceInfoCallback.class);
        final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        final Network network = new Network(999);
        request.setNetwork(network);
        client.registerServiceInfoCallback(request, Runnable::run, serviceInfoCallback);
        waitForIdle();
        // Verify the registration callback start.
        final ArgumentCaptor<MdnsListener> listenerCaptor =
                ArgumentCaptor.forClass(MdnsListener.class);
        verify(mSocketProvider).startMonitoringSockets();
        verify(mDiscoveryManager).registerListener(eq(serviceTypeWithLocalDomain),
                listenerCaptor.capture(), argThat(options -> network.equals(options.getNetwork())));

        final MdnsListener listener = listenerCaptor.getValue();
        final int servInfoId = listener.mTransactionId;
        // Verify the service info callback registered.
        verify(mMetrics).reportServiceInfoCallbackRegistered(servInfoId);

        final MdnsServiceInfo mdnsServiceInfo = new MdnsServiceInfo(
                SERVICE_NAME,
                serviceTypeWithLocalDomain.split("\\."),
                List.of(), /* subtypes */
                new String[]{"android", "local"}, /* hostName */
                PORT,
                List.of(IPV4_ADDRESS),
                List.of(IPV6_ADDRESS),
                List.of() /* textStrings */,
                List.of() /* textEntries */,
                1234,
                network,
                Instant.MAX /* expirationTime */);

        // Callbacks for query sent.
        listener.onDiscoveryQuerySent(Collections.emptyList(), 1 /* transactionId */);

        // Verify onServiceFound callback
        listener.onServiceFound(mdnsServiceInfo, true /* isServiceFromCache */);
        final ArgumentCaptor<NsdServiceInfo> updateInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(serviceInfoCallback, timeout(TIMEOUT_MS).times(1))
                .onServiceUpdated(updateInfoCaptor.capture());
        verifyUpdatedServiceInfo(updateInfoCaptor.getAllValues().get(0) /* info */, SERVICE_NAME,
                SERVICE_TYPE,
                List.of(parseNumericAddress(IPV4_ADDRESS), parseNumericAddress(IPV6_ADDRESS)),
                PORT, IFACE_IDX_ANY, new Network(999));

        // Service addresses changed.
        final String v4Address = "192.0.2.1";
        final String v6Address = "2001:db8::1";
        final MdnsServiceInfo updatedServiceInfo = new MdnsServiceInfo(
                SERVICE_NAME,
                serviceTypeWithLocalDomain.split("\\."),
                List.of(), /* subtypes */
                new String[]{"android", "local"}, /* hostName */
                PORT,
                List.of(v4Address),
                List.of(v6Address),
                List.of() /* textStrings */,
                List.of() /* textEntries */,
                1234,
                network,
                Instant.MAX /* expirationTime */);

        // Verify onServiceUpdated callback.
        listener.onServiceUpdated(updatedServiceInfo);
        verify(serviceInfoCallback, timeout(TIMEOUT_MS).times(2))
                .onServiceUpdated(updateInfoCaptor.capture());
        verifyUpdatedServiceInfo(updateInfoCaptor.getAllValues().get(2) /* info */, SERVICE_NAME,
                SERVICE_TYPE,
                List.of(parseNumericAddress(v4Address), parseNumericAddress(v6Address)),
                PORT, IFACE_IDX_ANY, new Network(999));

        // Service lost then recovered.
        listener.onServiceRemoved(updatedServiceInfo);
        listener.onServiceFound(updatedServiceInfo, false /* isServiceFromCache */);

        // Verify service callback unregistration.
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        client.unregisterServiceInfoCallback(serviceInfoCallback);
        waitForIdle();
        verify(serviceInfoCallback, timeout(TIMEOUT_MS)).onServiceInfoCallbackUnregistered();
        verify(mMetrics).reportServiceInfoCallbackUnregistered(servInfoId, 10L /* durationMs */,
                3 /* updateCallbackCount */, 1 /* lostCallbackCount */,
                true /* isServiceFromCache */, 1 /* sentQueryCount */);
    }

    @Test
    public void testRegisterServiceCallbackFailed() {
        final NsdManager client = connectClient(mService);
        final String invalidServiceType = "a_service";
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, invalidServiceType);
        final ServiceInfoCallback serviceInfoCallback = mock(
                ServiceInfoCallback.class);
        client.registerServiceInfoCallback(request, Runnable::run, serviceInfoCallback);
        waitForIdle();

        // Fail to register service callback.
        verify(serviceInfoCallback, timeout(TIMEOUT_MS))
                .onServiceInfoCallbackRegistrationFailed(eq(FAILURE_BAD_PARAMETERS));
        verify(mMetrics).reportServiceInfoCallbackRegistrationFailed(NO_TRANSACTION);
    }

    @Test
    public void testUnregisterNotRegisteredCallback() {
        final NsdManager client = connectClient(mService);
        final ServiceInfoCallback serviceInfoCallback = mock(
                ServiceInfoCallback.class);

        assertThrows(IllegalArgumentException.class, () ->
                client.unregisterServiceInfoCallback(serviceInfoCallback));
    }

    private void setMdnsDiscoveryManagerEnabled() {
        doReturn(true).when(mDeps).isMdnsDiscoveryManagerEnabled(any(Context.class));
    }

    private void setMdnsAdvertiserEnabled() {
        doReturn(true).when(mDeps).isMdnsAdvertiserEnabled(any(Context.class));
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testMdnsDiscoveryManagerFeature() {
        // Create NsdService w/o feature enabled.
        final NsdManager client = connectClient(mService);
        final DiscoveryListener discListenerWithoutFeature = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, discListenerWithoutFeature);
        waitForIdle();

        final ArgumentCaptor<Integer> legacyIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).discover(legacyIdCaptor.capture(), any(), anyInt());
        verifyNoMoreInteractions(mDiscoveryManager);

        setMdnsDiscoveryManagerEnabled();
        final DiscoveryListener discListenerWithFeature = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, discListenerWithFeature);
        waitForIdle();

        final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        final ArgumentCaptor<MdnsServiceBrowserListener> listenerCaptor =
                ArgumentCaptor.forClass(MdnsServiceBrowserListener.class);
        verify(mDiscoveryManager).registerListener(eq(serviceTypeWithLocalDomain),
                listenerCaptor.capture(), any());

        client.stopServiceDiscovery(discListenerWithoutFeature);
        waitForIdle();
        verify(mMockMDnsM).stopOperation(legacyIdCaptor.getValue());

        client.stopServiceDiscovery(discListenerWithFeature);
        waitForIdle();
        verify(mDiscoveryManager).unregisterListener(serviceTypeWithLocalDomain,
                listenerCaptor.getValue());
    }

    @Test
    public void testDiscoveryWithMdnsDiscoveryManager() {
        setMdnsDiscoveryManagerEnabled();

        final NsdManager client = connectClient(mService);
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        final Network network = new Network(999);
        final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        // Verify the discovery start / stop.
        final ArgumentCaptor<MdnsListener> listenerCaptor =
                ArgumentCaptor.forClass(MdnsListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, network, r -> r.run(), discListener);
        waitForIdle();
        verify(mSocketProvider).startMonitoringSockets();
        verify(mDiscoveryManager).registerListener(eq(serviceTypeWithLocalDomain),
                listenerCaptor.capture(), argThat(options -> network.equals(options.getNetwork())));
        verify(discListener, timeout(TIMEOUT_MS)).onDiscoveryStarted(SERVICE_TYPE);

        final MdnsListener listener = listenerCaptor.getValue();
        final int discId = listener.mTransactionId;
        verify(mMetrics).reportServiceDiscoveryStarted(false /* isLegacy */, discId);

        // Callbacks for query sent.
        listener.onDiscoveryQuerySent(Collections.emptyList(), 1 /* transactionId */);
        listener.onDiscoveryQuerySent(Collections.emptyList(), 2 /* transactionId */);
        listener.onDiscoveryQuerySent(Collections.emptyList(), 3 /* transactionId */);

        final MdnsServiceInfo foundInfo = new MdnsServiceInfo(
                SERVICE_NAME, /* serviceInstanceName */
                serviceTypeWithLocalDomain.split("\\."), /* serviceType */
                List.of(), /* subtypes */
                new String[] {"android", "local"}, /* hostName */
                12345, /* port */
                List.of(IPV4_ADDRESS),
                List.of(IPV6_ADDRESS),
                List.of(), /* textStrings */
                List.of(), /* textEntries */
                1234, /* interfaceIndex */
                network,
                Instant.MAX /* expirationTime */);

        // Verify onServiceNameDiscovered callback
        listener.onServiceNameDiscovered(foundInfo, false /* isServiceFromCache */);
        verify(discListener, timeout(TIMEOUT_MS)).onServiceFound(argThat(info ->
                info.getServiceName().equals(SERVICE_NAME)
                        // Service type in discovery callbacks has a dot at the end
                        && info.getServiceType().equals(SERVICE_TYPE + ".")
                        && info.getNetwork().equals(network)));

        final MdnsServiceInfo removedInfo = new MdnsServiceInfo(
                SERVICE_NAME, /* serviceInstanceName */
                serviceTypeWithLocalDomain.split("\\."), /* serviceType */
                null, /* subtypes */
                null, /* hostName */
                0, /* port */
                List.of(), /* ipv4Address */
                List.of(), /* ipv6Address */
                null, /* textStrings */
                null, /* textEntries */
                1234, /* interfaceIndex */
                network,
                Instant.MAX /* expirationTime */);
        // Verify onServiceNameRemoved callback
        listener.onServiceNameRemoved(removedInfo);
        verify(discListener, timeout(TIMEOUT_MS)).onServiceLost(argThat(info ->
                info.getServiceName().equals(SERVICE_NAME)
                        // Service type in discovery callbacks has a dot at the end
                        && info.getServiceType().equals(SERVICE_TYPE + ".")
                        && info.getNetwork().equals(network)));

        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        client.stopServiceDiscovery(discListener);
        waitForIdle();
        verify(mDiscoveryManager).unregisterListener(eq(serviceTypeWithLocalDomain), any());
        verify(discListener, timeout(TIMEOUT_MS)).onDiscoveryStopped(SERVICE_TYPE);
        verify(mSocketProvider, timeout(CLEANUP_DELAY_MS + TIMEOUT_MS)).requestStopWhenInactive();
        verify(mMetrics).reportServiceDiscoveryStop(false /* isLegacy */, discId,
                10L /* durationMs */, 1 /* foundCallbackCount */, 1 /* lostCallbackCount */,
                1 /* servicesCount */, 3 /* sentQueryCount */);
    }

    @Test
    public void testDiscoveryWithMdnsDiscoveryManager_FailedWithInvalidServiceType() {
        setMdnsDiscoveryManagerEnabled();

        final NsdManager client = connectClient(mService);
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        final Network network = new Network(999);
        final String invalidServiceType = "a_service";
        client.discoverServices(
                invalidServiceType, PROTOCOL, network, r -> r.run(), discListener);
        waitForIdle();
        verify(discListener, timeout(TIMEOUT_MS))
                .onStartDiscoveryFailed(invalidServiceType, FAILURE_INTERNAL_ERROR);
        verify(mMetrics, times(1)).reportServiceDiscoveryFailed(
                false /* isLegacy */, NO_TRANSACTION, 0L /* durationMs */);

        final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        client.discoverServices(
                serviceTypeWithLocalDomain, PROTOCOL, network, r -> r.run(), discListener);
        waitForIdle();
        verify(discListener, timeout(TIMEOUT_MS))
                .onStartDiscoveryFailed(serviceTypeWithLocalDomain, FAILURE_INTERNAL_ERROR);
        verify(mMetrics, times(2)).reportServiceDiscoveryFailed(
                false /* isLegacy */, NO_TRANSACTION, 0L /* durationMs */);

        final String serviceTypeWithoutTcpOrUdpEnding = "_test._com";
        client.discoverServices(
                serviceTypeWithoutTcpOrUdpEnding, PROTOCOL, network, r -> r.run(), discListener);
        waitForIdle();
        verify(discListener, timeout(TIMEOUT_MS))
                .onStartDiscoveryFailed(serviceTypeWithoutTcpOrUdpEnding, FAILURE_INTERNAL_ERROR);
        verify(mMetrics, times(3)).reportServiceDiscoveryFailed(
                false /* isLegacy */, NO_TRANSACTION, 0L /* durationMs */);
    }

    @Test
    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    public void testDiscoveryWithMdnsDiscoveryManager_UsesSubtypes() {
        final String typeWithSubtype = SERVICE_TYPE + ",_subtype";
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo regInfo = new NsdServiceInfo("Instance", typeWithSubtype);
        final Network network = new Network(999);
        regInfo.setHostAddresses(List.of(parseNumericAddress("192.0.2.123")));
        regInfo.setPort(12345);
        regInfo.setNetwork(network);

        final RegistrationListener regListener = mock(RegistrationListener.class);
        client.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();
        verify(mAdvertiser).addOrUpdateService(anyInt(), argThat(s ->
                "Instance".equals(s.getServiceName())
                        && SERVICE_TYPE.equals(s.getServiceType())
                        && s.getSubtypes().equals(Set.of("_subtype"))), any(), anyInt());

        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        client.discoverServices(typeWithSubtype, PROTOCOL, network, Runnable::run, discListener);
        waitForIdle();
        final ArgumentCaptor<MdnsSearchOptions> optionsCaptor =
                ArgumentCaptor.forClass(MdnsSearchOptions.class);
        verify(mDiscoveryManager).registerListener(eq(SERVICE_TYPE + ".local"), any(),
                optionsCaptor.capture());
        assertEquals(Collections.singletonList("subtype"), optionsCaptor.getValue().getSubtypes());
    }

    @Test
    public void testResolutionWithMdnsDiscoveryManager() throws UnknownHostException {
        setMdnsDiscoveryManagerEnabled();

        final NsdManager client = connectClient(mService);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        final Network network = new Network(999);
        final String serviceType = "_nsd._service._tcp";
        final String constructedServiceType = "_service._tcp.local";
        final ArgumentCaptor<MdnsListener> listenerCaptor =
                ArgumentCaptor.forClass(MdnsListener.class);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, serviceType);
        request.setNetwork(network);
        client.resolveService(request, resolveListener);
        waitForIdle();
        verify(mSocketProvider).startMonitoringSockets();
        final ArgumentCaptor<MdnsSearchOptions> optionsCaptor =
                ArgumentCaptor.forClass(MdnsSearchOptions.class);
        verify(mDiscoveryManager).registerListener(eq(constructedServiceType),
                listenerCaptor.capture(),
                optionsCaptor.capture());
        assertEquals(network, optionsCaptor.getValue().getNetwork());
        // Subtypes are not used for resolution, only for discovery
        assertEquals(Collections.emptyList(), optionsCaptor.getValue().getSubtypes());

        final MdnsListener listener = listenerCaptor.getValue();
        final MdnsServiceInfo mdnsServiceInfo = new MdnsServiceInfo(
                SERVICE_NAME,
                constructedServiceType.split("\\."),
                List.of(), /* subtypes */
                new String[]{"android", "local"}, /* hostName */
                PORT,
                List.of(IPV4_ADDRESS),
                List.of("2001:db8::1", "2001:db8::2"),
                List.of() /* textStrings */,
                List.of(MdnsServiceInfo.TextEntry.fromBytes(new byte[]{
                        'k', 'e', 'y', '=', (byte) 0xFF, (byte) 0xFE})) /* textEntries */,
                1234,
                network,
                Instant.ofEpochSecond(1000_000L) /* expirationTime */);

        // Verify onServiceFound callback
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        listener.onServiceFound(mdnsServiceInfo, true /* isServiceFromCache */);
        final ArgumentCaptor<NsdServiceInfo> infoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(resolveListener, timeout(TIMEOUT_MS)).onServiceResolved(infoCaptor.capture());
        verify(mMetrics).reportServiceResolved(false /* isLegacy */, listener.mTransactionId,
                10 /* durationMs */, true /* isServiceFromCache */, 0 /* sendQueryCount */);

        final NsdServiceInfo info = infoCaptor.getValue();
        assertEquals(SERVICE_NAME, info.getServiceName());
        assertEquals("._service._tcp", info.getServiceType());
        assertEquals(PORT, info.getPort());
        assertTrue(info.getAttributes().containsKey("key"));
        assertEquals(1, info.getAttributes().size());
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFE}, info.getAttributes().get("key"));
        assertEquals(parseNumericAddress(IPV4_ADDRESS), info.getHost());
        assertEquals(3, info.getHostAddresses().size());
        assertTrue(info.getHostAddresses().stream().anyMatch(
                address -> address.equals(parseNumericAddress("2001:db8::1"))));
        assertTrue(info.getHostAddresses().stream().anyMatch(
                address -> address.equals(parseNumericAddress("2001:db8::2"))));
        assertEquals(network, info.getNetwork());
        assertEquals(Instant.ofEpochSecond(1000_000L), info.getExpirationTime());

        // Verify the listener has been unregistered.
        verify(mDiscoveryManager, timeout(TIMEOUT_MS))
                .unregisterListener(eq(constructedServiceType), any());
        verify(mSocketProvider, timeout(CLEANUP_DELAY_MS + TIMEOUT_MS)).requestStopWhenInactive();
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testMdnsAdvertiserFeatureFlagging() {
        // Create NsdService w/o feature enabled.
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo regInfo = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        regInfo.setHost(parseNumericAddress("192.0.2.123"));
        regInfo.setPort(12345);
        final RegistrationListener regListenerWithoutFeature = mock(RegistrationListener.class);
        client.registerService(regInfo, PROTOCOL, regListenerWithoutFeature);
        waitForIdle();

        final ArgumentCaptor<Integer> legacyIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).registerService(legacyIdCaptor.capture(), any(), any(), anyInt(),
                any(), anyInt());
        verifyNoMoreInteractions(mAdvertiser);

        setMdnsAdvertiserEnabled();
        final RegistrationListener regListenerWithFeature = mock(RegistrationListener.class);
        client.registerService(regInfo, PROTOCOL, regListenerWithFeature);
        waitForIdle();

        final ArgumentCaptor<Integer> serviceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAdvertiser).addOrUpdateService(serviceIdCaptor.capture(),
                argThat(info -> matches(info, regInfo)), any(), anyInt());

        client.unregisterService(regListenerWithoutFeature);
        waitForIdle();
        verify(mMockMDnsM).stopOperation(legacyIdCaptor.getValue());
        verify(mAdvertiser, never()).removeService(anyInt());

        doReturn(mock(MdnsAdvertiser.AdvertiserMetrics.class))
                .when(mAdvertiser).getAdvertiserMetrics(anyInt());
        client.unregisterService(regListenerWithFeature);
        waitForIdle();
        verify(mAdvertiser).removeService(serviceIdCaptor.getValue());
    }

    @Test
    @DisableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testTypeSpecificFeatureFlagging() {
        doReturn("_type1._tcp:flag1,_type2._tcp:flag2").when(mDeps).getTypeAllowlistFlags();
        doReturn(true).when(mDeps).isFeatureEnabled(any(),
                eq("mdns_discovery_manager_allowlist_flag1_version"));
        doReturn(true).when(mDeps).isFeatureEnabled(any(),
                eq("mdns_advertiser_allowlist_flag2_version"));

        final NsdManager client = connectClient(mService);
        final NsdServiceInfo service1 = new NsdServiceInfo(SERVICE_NAME, "_type1._tcp");
        service1.setHostAddresses(List.of(parseNumericAddress("2001:db8::123")));
        service1.setPort(1234);
        final NsdServiceInfo service2 = new NsdServiceInfo(SERVICE_NAME, "_type2._tcp");
        service1.setHostAddresses(List.of(parseNumericAddress("2001:db8::123")));
        service2.setPort(1234);

        client.discoverServices(service1.getServiceType(),
                NsdManager.PROTOCOL_DNS_SD, mock(DiscoveryListener.class));
        client.discoverServices(service2.getServiceType(),
                NsdManager.PROTOCOL_DNS_SD, mock(DiscoveryListener.class));
        waitForIdle();

        // The DiscoveryManager is enabled for _type1 but not _type2
        verify(mDiscoveryManager).registerListener(eq("_type1._tcp.local"), any(), any());
        verify(mDiscoveryManager, never()).registerListener(
                eq("_type2._tcp.local"), any(), any());

        client.resolveService(service1, mock(ResolveListener.class));
        client.resolveService(service2, mock(ResolveListener.class));
        waitForIdle();

        // Same behavior for resolve
        verify(mDiscoveryManager, times(2)).registerListener(
                eq("_type1._tcp.local"), any(), any());
        verify(mDiscoveryManager, never()).registerListener(
                eq("_type2._tcp.local"), any(), any());

        client.registerService(service1, NsdManager.PROTOCOL_DNS_SD,
                mock(RegistrationListener.class));
        client.registerService(service2, NsdManager.PROTOCOL_DNS_SD,
                mock(RegistrationListener.class));
        waitForIdle();

        // The advertiser is enabled for _type2 but not _type1
        verify(mAdvertiser, never()).addOrUpdateService(anyInt(),
                argThat(info -> matches(info, service1)), any(), anyInt());
        verify(mAdvertiser).addOrUpdateService(anyInt(), argThat(info -> matches(info, service2)),
                any(), anyInt());
    }

    @Test
    public void testAdvertiseWithMdnsAdvertiser() {
        setMdnsAdvertiserEnabled();

        final NsdManager client = connectClient(mService);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        // final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        final ArgumentCaptor<MdnsAdvertiser.AdvertiserCallback> cbCaptor =
                ArgumentCaptor.forClass(MdnsAdvertiser.AdvertiserCallback.class);
        verify(mDeps).makeMdnsAdvertiser(any(), any(), cbCaptor.capture(), any(), any(), any());

        final NsdServiceInfo regInfo = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        regInfo.setHost(parseNumericAddress("192.0.2.123"));
        regInfo.setPort(12345);
        regInfo.setAttribute("testattr", "testvalue");
        regInfo.setNetwork(new Network(999));

        client.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();
        verify(mSocketProvider).startMonitoringSockets();
        final ArgumentCaptor<Integer> idCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAdvertiser).addOrUpdateService(idCaptor.capture(), argThat(info ->
                matches(info, regInfo)), any(), anyInt());

        // Verify onServiceRegistered callback
        final MdnsAdvertiser.AdvertiserCallback cb = cbCaptor.getValue();
        final int regId = idCaptor.getValue();
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        cb.onRegisterServiceSucceeded(regId, regInfo);

        verify(regListener, timeout(TIMEOUT_MS)).onServiceRegistered(argThat(info -> matches(info,
                new NsdServiceInfo(regInfo.getServiceName(), null))));
        verify(mMetrics).reportServiceRegistrationSucceeded(
                false /* isLegacy */, regId, 10L /* durationMs */);

        final MdnsAdvertiser.AdvertiserMetrics metrics = new MdnsAdvertiser.AdvertiserMetrics(
                50 /* repliedRequestCount */, 100 /* sentPacketCount */,
                3 /* conflictDuringProbingCount */, 2 /* conflictAfterProbingCount */);
        doReturn(TEST_TIME_MS + 100L).when(mClock).elapsedRealtime();
        doReturn(metrics).when(mAdvertiser).getAdvertiserMetrics(regId);
        client.unregisterService(regListener);
        waitForIdle();
        verify(mAdvertiser).removeService(idCaptor.getValue());
        verify(regListener, timeout(TIMEOUT_MS)).onServiceUnregistered(
                argThat(info -> matches(info, regInfo)));
        verify(mSocketProvider, timeout(TIMEOUT_MS)).requestStopWhenInactive();
        verify(mMetrics).reportServiceUnregistration(false /* isLegacy */, regId,
                100L /* durationMs */, 50 /* repliedRequestCount */, 100 /* sentPacketCount */,
                3 /* conflictDuringProbingCount */, 2 /* conflictAfterProbingCount */);
    }

    @Test
    public void testAdvertiseWithMdnsAdvertiser_FailedWithInvalidServiceType() {
        setMdnsAdvertiserEnabled();

        final NsdManager client = connectClient(mService);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        // final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        final ArgumentCaptor<MdnsAdvertiser.AdvertiserCallback> cbCaptor =
                ArgumentCaptor.forClass(MdnsAdvertiser.AdvertiserCallback.class);
        verify(mDeps).makeMdnsAdvertiser(any(), any(), cbCaptor.capture(), any(), any(), any());

        final NsdServiceInfo regInfo = new NsdServiceInfo(SERVICE_NAME, "invalid_type");
        regInfo.setHost(parseNumericAddress("192.0.2.123"));
        regInfo.setPort(12345);
        regInfo.setAttribute("testattr", "testvalue");
        regInfo.setNetwork(new Network(999));

        client.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();
        verify(mAdvertiser, never()).addOrUpdateService(anyInt(), any(), any(), anyInt());

        verify(regListener, timeout(TIMEOUT_MS)).onRegistrationFailed(
                argThat(info -> matches(info, regInfo)), eq(FAILURE_INTERNAL_ERROR));
        verify(mMetrics).reportServiceRegistrationFailed(
                false /* isLegacy */, NO_TRANSACTION, 0L /* durationMs */);
    }

    @Test
    public void testAdvertiseWithMdnsAdvertiser_LongServiceName() {
        setMdnsAdvertiserEnabled();

        final NsdManager client = connectClient(mService);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        // final String serviceTypeWithLocalDomain = SERVICE_TYPE + ".local";
        final ArgumentCaptor<MdnsAdvertiser.AdvertiserCallback> cbCaptor =
                ArgumentCaptor.forClass(MdnsAdvertiser.AdvertiserCallback.class);
        verify(mDeps).makeMdnsAdvertiser(any(), any(), cbCaptor.capture(), any(), any(), any());

        final NsdServiceInfo regInfo = new NsdServiceInfo("a".repeat(70), SERVICE_TYPE);
        regInfo.setHost(parseNumericAddress("192.0.2.123"));
        regInfo.setPort(12345);
        regInfo.setAttribute("testattr", "testvalue");
        regInfo.setNetwork(new Network(999));

        client.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();
        final ArgumentCaptor<Integer> idCaptor = ArgumentCaptor.forClass(Integer.class);
        // Service name is truncated to 63 characters
        verify(mAdvertiser)
                .addOrUpdateService(
                        idCaptor.capture(),
                        argThat(info -> info.getServiceName().equals("a".repeat(63))),
                        any(),
                        anyInt());

        // Verify onServiceRegistered callback
        final MdnsAdvertiser.AdvertiserCallback cb = cbCaptor.getValue();
        final int regId = idCaptor.getValue();
        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        cb.onRegisterServiceSucceeded(regId, regInfo);

        verify(regListener, timeout(TIMEOUT_MS)).onServiceRegistered(
                argThat(info -> matches(info, new NsdServiceInfo(regInfo.getServiceName(), null))));
        verify(mMetrics).reportServiceRegistrationSucceeded(
                false /* isLegacy */, regId, 10L /* durationMs */);
    }

    @Test
    public void testAdvertiseCustomTtl_validTtl_success() {
        runValidTtlAdvertisingTest(30L);
        runValidTtlAdvertisingTest(10 * 3600L);
    }

    @Test
    public void testAdvertiseCustomTtl_ttlSmallerThan30SecondsButClientIsSystemServer_success() {
        when(mDeps.getCallingUid()).thenReturn(Process.SYSTEM_UID);

        runValidTtlAdvertisingTest(29L);
    }

    @Test
    public void testAdvertiseCustomTtl_ttlLargerThan10HoursButClientIsSystemServer_success() {
        when(mDeps.getCallingUid()).thenReturn(Process.SYSTEM_UID);

        runValidTtlAdvertisingTest(10 * 3600L + 1);
        runValidTtlAdvertisingTest(0xffffffffL);
    }

    private void runValidTtlAdvertisingTest(long validTtlSeconds) {
        setMdnsAdvertiserEnabled();

        final NsdManager client = connectClient(mService);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        final ArgumentCaptor<MdnsAdvertiser.AdvertiserCallback> cbCaptor =
                ArgumentCaptor.forClass(MdnsAdvertiser.AdvertiserCallback.class);
        verify(mDeps).makeMdnsAdvertiser(any(), any(), cbCaptor.capture(), any(), any(), any());

        final NsdServiceInfo regInfo = new NsdServiceInfo("Service custom TTL", SERVICE_TYPE);
        regInfo.setPort(1234);
        final AdvertisingRequest request =
                new AdvertisingRequest.Builder(regInfo, NsdManager.PROTOCOL_DNS_SD)
                    .setTtl(Duration.ofSeconds(validTtlSeconds)).build();

        client.registerService(request, Runnable::run, regListener);
        waitForIdle();

        final ArgumentCaptor<Integer> idCaptor = ArgumentCaptor.forClass(Integer.class);
        final MdnsAdvertisingOptions expectedAdverstingOptions =
                MdnsAdvertisingOptions.newBuilder().setTtl(request.getTtl()).build();
        verify(mAdvertiser).addOrUpdateService(idCaptor.capture(), any(),
                eq(expectedAdverstingOptions), anyInt());

        // Verify onServiceRegistered callback
        final MdnsAdvertiser.AdvertiserCallback cb = cbCaptor.getValue();
        final int regId = idCaptor.getValue();
        cb.onRegisterServiceSucceeded(regId, regInfo);

        verify(regListener, timeout(TIMEOUT_MS)).onServiceRegistered(
                argThat(info -> matches(info, new NsdServiceInfo(regInfo.getServiceName(), null))));
    }

    @Test
    public void testAdvertiseCustomTtl_invalidTtl_FailsWithBadParameters() {
        setMdnsAdvertiserEnabled();
        final long invalidTtlSeconds = 29L;
        final NsdManager client = connectClient(mService);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        final ArgumentCaptor<MdnsAdvertiser.AdvertiserCallback> cbCaptor =
                ArgumentCaptor.forClass(MdnsAdvertiser.AdvertiserCallback.class);
        verify(mDeps).makeMdnsAdvertiser(any(), any(), cbCaptor.capture(), any(), any(), any());

        final NsdServiceInfo regInfo = new NsdServiceInfo("Service custom TTL", SERVICE_TYPE);
        regInfo.setPort(1234);
        final AdvertisingRequest request =
                new AdvertisingRequest.Builder(regInfo, NsdManager.PROTOCOL_DNS_SD)
                    .setTtl(Duration.ofSeconds(invalidTtlSeconds)).build();
        client.registerService(request, Runnable::run, regListener);
        waitForIdle();

        verify(regListener, timeout(TIMEOUT_MS))
                .onRegistrationFailed(any(), eq(FAILURE_BAD_PARAMETERS));
    }

    @Test
    public void testStopServiceResolutionWithMdnsDiscoveryManager() {
        setMdnsDiscoveryManagerEnabled();

        final NsdManager client = connectClient(mService);
        final ResolveListener resolveListener = mock(ResolveListener.class);
        final Network network = new Network(999);
        final String serviceType = "_nsd._service._tcp";
        final String constructedServiceType = "_service._tcp.local";
        final ArgumentCaptor<MdnsListener> listenerCaptor =
                ArgumentCaptor.forClass(MdnsListener.class);
        final NsdServiceInfo request = new NsdServiceInfo(SERVICE_NAME, serviceType);
        request.setNetwork(network);
        client.resolveService(request, resolveListener);
        waitForIdle();
        verify(mSocketProvider).startMonitoringSockets();
        final ArgumentCaptor<MdnsSearchOptions> optionsCaptor =
                ArgumentCaptor.forClass(MdnsSearchOptions.class);
        verify(mDiscoveryManager).registerListener(eq(constructedServiceType),
                listenerCaptor.capture(),
                optionsCaptor.capture());
        assertEquals(network, optionsCaptor.getValue().getNetwork());
        // Subtypes are not used for resolution, only for discovery
        assertEquals(Collections.emptyList(), optionsCaptor.getValue().getSubtypes());

        doReturn(TEST_TIME_MS + 10L).when(mClock).elapsedRealtime();
        client.stopServiceResolution(resolveListener);
        waitForIdle();

        // Verify the listener has been unregistered.
        final MdnsListener listener = listenerCaptor.getValue();
        verify(mDiscoveryManager, timeout(TIMEOUT_MS))
                .unregisterListener(eq(constructedServiceType), eq(listener));
        verify(resolveListener, timeout(TIMEOUT_MS)).onResolutionStopped(argThat(ns ->
                request.getServiceName().equals(ns.getServiceName())
                        && request.getServiceType().equals(ns.getServiceType())));
        verify(mSocketProvider, timeout(CLEANUP_DELAY_MS + TIMEOUT_MS)).requestStopWhenInactive();
        verify(mMetrics).reportServiceResolutionStop(
                false /* isLegacy */, listener.mTransactionId, 10L /* durationMs */);
    }

    @Test
    public void testParseTypeAndSubtype() {
        final String serviceType1 = "test._tcp";
        final String serviceType2 = "_test._quic";
        final String serviceType3 = "_test._quic,_test1,_test2";
        final String serviceType4 = "_123._udp.";
        final String serviceType5 = "_TEST._999._tcp.";
        final String serviceType6 = "_998._tcp.,_TEST";
        final String serviceType7 = "_997._tcp,_TEST";
        final String serviceType8 = "_997._tcp,_test1,_test2,_test3";
        final String serviceType9 = "_test4._997._tcp,_test1,_test2,_test3";

        assertNull(parseTypeAndSubtype(serviceType1));
        assertNull(parseTypeAndSubtype(serviceType2));
        assertNull(parseTypeAndSubtype(serviceType3));
        assertEquals(new Pair<>("_123._udp", Collections.emptyList()),
                parseTypeAndSubtype(serviceType4));
        assertEquals(new Pair<>("_999._tcp", List.of("_TEST")), parseTypeAndSubtype(serviceType5));
        assertEquals(new Pair<>("_998._tcp", List.of("_TEST")), parseTypeAndSubtype(serviceType6));
        assertEquals(new Pair<>("_997._tcp", List.of("_TEST")), parseTypeAndSubtype(serviceType7));

        assertEquals(new Pair<>("_997._tcp", List.of("_test1", "_test2", "_test3")),
                parseTypeAndSubtype(serviceType8));
        assertEquals(new Pair<>("_997._tcp", List.of("_test4")),
                parseTypeAndSubtype(serviceType9));
    }

    @Test
    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    public void testEnablePlatformMdnsBackend() {
        final NsdManager client = connectClient(mService);
        final NsdServiceInfo regInfo = new NsdServiceInfo("a".repeat(70), SERVICE_TYPE);
        final Network network = new Network(999);
        regInfo.setHostAddresses(List.of(parseNumericAddress("192.0.2.123")));
        regInfo.setPort(12345);
        regInfo.setAttribute("testattr", "testvalue");
        regInfo.setNetwork(network);

        // Verify the registration uses MdnsAdvertiser
        final RegistrationListener regListener = mock(RegistrationListener.class);
        client.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();
        verify(mSocketProvider).startMonitoringSockets();
        verify(mAdvertiser).addOrUpdateService(anyInt(), any(), any(), anyInt());

        // Verify the discovery uses MdnsDiscoveryManager
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        client.discoverServices(SERVICE_TYPE, PROTOCOL, network, r -> r.run(), discListener);
        waitForIdle();
        verify(mDiscoveryManager).registerListener(anyString(), any(), any());

        // Verify the discovery uses MdnsDiscoveryManager
        final ResolveListener resolveListener = mock(ResolveListener.class);
        client.resolveService(regInfo, r -> r.run(), resolveListener);
        waitForIdle();
        verify(mDiscoveryManager, times(2)).registerListener(anyString(), any(), any());
    }

    @Test
    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    public void testTakeMulticastLockOnBehalfOfClient_ForWifiNetworksOnly() {
        // Test on one client in the foreground
        mUidImportanceListener.onUidImportance(123, IMPORTANCE_FOREGROUND);
        doReturn(123).when(mDeps).getCallingUid();
        final NsdManager client = connectClient(mService);

        final NsdServiceInfo regInfo = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        regInfo.setHostAddresses(List.of(parseNumericAddress("192.0.2.123")));
        regInfo.setPort(12345);
        // File a request for all networks
        regInfo.setNetwork(null);

        final RegistrationListener regListener = mock(RegistrationListener.class);
        client.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();
        verify(mSocketProvider).startMonitoringSockets();
        verify(mAdvertiser).addOrUpdateService(anyInt(), any(), any(), anyInt());

        final Network wifiNetwork1 = new Network(123);
        final Network wifiNetwork2 = new Network(124);
        final Network ethernetNetwork = new Network(125);

        final MdnsInterfaceSocket wifiNetworkSocket1 = mock(MdnsInterfaceSocket.class);
        final MdnsInterfaceSocket wifiNetworkSocket2 = mock(MdnsInterfaceSocket.class);
        final MdnsInterfaceSocket ethernetNetworkSocket = mock(MdnsInterfaceSocket.class);

        // Nothing happens for networks with no transports, no Wi-Fi transport, or VPN transport
        mHandler.post(() -> {
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    new Network(125), mock(MdnsInterfaceSocket.class), new int[0]);
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    ethernetNetwork, ethernetNetworkSocket,
                    new int[] { TRANSPORT_ETHERNET });
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    new Network(127), mock(MdnsInterfaceSocket.class),
                    new int[] { TRANSPORT_WIFI, TRANSPORT_VPN });
        });
        waitForIdle();
        verify(mWifiManager, never()).createMulticastLock(any());

        // First Wi-Fi network
        mHandler.post(() -> mSocketRequestMonitor.onSocketRequestFulfilled(
                wifiNetwork1, wifiNetworkSocket1, new int[] { TRANSPORT_WIFI }));
        waitForIdle();
        verify(mWifiManager).createMulticastLock(any());
        verify(mMulticastLock).acquire();

        // Second Wi-Fi network
        mHandler.post(() -> mSocketRequestMonitor.onSocketRequestFulfilled(
                wifiNetwork2, wifiNetworkSocket2, new int[] { TRANSPORT_WIFI }));
        waitForIdle();
        verifyNoMoreInteractions(mMulticastLock);

        // One Wi-Fi network becomes unused, nothing happens
        mHandler.post(() -> mSocketRequestMonitor.onSocketDestroyed(
                wifiNetwork1, wifiNetworkSocket1));
        waitForIdle();
        verifyNoMoreInteractions(mMulticastLock);

        // Ethernet network becomes unused, still nothing
        mHandler.post(() -> mSocketRequestMonitor.onSocketDestroyed(
                ethernetNetwork, ethernetNetworkSocket));
        waitForIdle();
        verifyNoMoreInteractions(mMulticastLock);

        // The second Wi-Fi network becomes unused, the lock is released
        mHandler.post(() -> mSocketRequestMonitor.onSocketDestroyed(
                wifiNetwork2, wifiNetworkSocket2));
        waitForIdle();
        verify(mMulticastLock).release();
    }

    @Test
    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    public void testTakeMulticastLockOnBehalfOfClient_ForForegroundAppsOnly() {
        final int uid1 = 12;
        final int uid2 = 34;
        final int uid3 = 56;
        final int uid4 = 78;
        final InOrder lockOrder = inOrder(mMulticastLock);
        // Connect one client without any foreground info
        doReturn(uid1).when(mDeps).getCallingUid();
        final NsdManager client1 = connectClient(mService);

        // Connect client2 as visible, but not foreground
        mUidImportanceListener.onUidImportance(uid2, IMPORTANCE_VISIBLE);
        waitForIdle();
        doReturn(uid2).when(mDeps).getCallingUid();
        final NsdManager client2 = connectClient(mService);

        // Connect client3, client4 as foreground
        mUidImportanceListener.onUidImportance(uid3, IMPORTANCE_FOREGROUND);
        waitForIdle();
        doReturn(uid3).when(mDeps).getCallingUid();
        final NsdManager client3 = connectClient(mService);

        mUidImportanceListener.onUidImportance(uid4, IMPORTANCE_FOREGROUND);
        waitForIdle();
        doReturn(uid4).when(mDeps).getCallingUid();
        final NsdManager client4 = connectClient(mService);

        // First client advertises on any network
        final NsdServiceInfo regInfo = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        regInfo.setHostAddresses(List.of(parseNumericAddress("192.0.2.123")));
        regInfo.setPort(12345);
        regInfo.setNetwork(null);
        final RegistrationListener regListener = mock(RegistrationListener.class);
        client1.registerService(regInfo, NsdManager.PROTOCOL_DNS_SD, Runnable::run, regListener);
        waitForIdle();

        final MdnsInterfaceSocket wifiSocket = mock(MdnsInterfaceSocket.class);
        final Network wifiNetwork = new Network(123);

        final MdnsInterfaceSocket ethSocket = mock(MdnsInterfaceSocket.class);
        final Network ethNetwork = new Network(234);

        mHandler.post(() -> {
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    wifiNetwork, wifiSocket, new int[] { TRANSPORT_WIFI });
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    ethNetwork, ethSocket, new int[] { TRANSPORT_ETHERNET });
        });
        waitForIdle();

        // No multicast lock since client1 has no foreground info
        lockOrder.verifyNoMoreInteractions();

        // Second client discovers specifically on the Wi-Fi network
        final DiscoveryListener discListener = mock(DiscoveryListener.class);
        client2.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, wifiNetwork,
                Runnable::run, discListener);
        waitForIdle();
        mHandler.post(() -> mSocketRequestMonitor.onSocketRequestFulfilled(
                wifiNetwork, wifiSocket, new int[] { TRANSPORT_WIFI }));
        waitForIdle();
        // No multicast lock since client2 is not visible enough
        lockOrder.verifyNoMoreInteractions();

        // Third client registers a callback on all networks
        final NsdServiceInfo cbInfo = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        cbInfo.setNetwork(null);
        final ServiceInfoCallback infoCb = mock(ServiceInfoCallback.class);
        client3.registerServiceInfoCallback(cbInfo, Runnable::run, infoCb);
        waitForIdle();
        mHandler.post(() -> {
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    wifiNetwork, wifiSocket, new int[] { TRANSPORT_WIFI });
            mSocketRequestMonitor.onSocketRequestFulfilled(
                    ethNetwork, ethSocket, new int[] { TRANSPORT_ETHERNET });
        });
        waitForIdle();

        // Multicast lock is taken for third client
        lockOrder.verify(mMulticastLock).acquire();

        // Client3 goes to the background
        mUidImportanceListener.onUidImportance(uid3, IMPORTANCE_CACHED);
        waitForIdle();
        lockOrder.verify(mMulticastLock).release();

        // client4 resolves on a different network
        final ResolveListener resolveListener = mock(ResolveListener.class);
        final NsdServiceInfo resolveInfo = new NsdServiceInfo(SERVICE_NAME, SERVICE_TYPE);
        resolveInfo.setNetwork(ethNetwork);
        client4.resolveService(resolveInfo, Runnable::run, resolveListener);
        waitForIdle();
        mHandler.post(() -> mSocketRequestMonitor.onSocketRequestFulfilled(
                ethNetwork, ethSocket, new int[] { TRANSPORT_ETHERNET }));
        waitForIdle();

        // client4 is foreground, but not Wi-Fi
        lockOrder.verifyNoMoreInteractions();

        // Second client becomes foreground
        mUidImportanceListener.onUidImportance(uid2, IMPORTANCE_FOREGROUND);
        waitForIdle();

        lockOrder.verify(mMulticastLock).acquire();

        // Second client is lost
        mUidImportanceListener.onUidImportance(uid2, IMPORTANCE_GONE);
        waitForIdle();

        lockOrder.verify(mMulticastLock).release();
    }

    @Test
    public void testNullINsdManagerCallback() {
        final NsdService service = new NsdService(mContext, mHandler, CLEANUP_DELAY_MS, mDeps) {
            @Override
            public INsdServiceConnector connect(INsdManagerCallback baseCb,
                    boolean runNewMdnsBackend) {
                // Pass null INsdManagerCallback
                return super.connect(null /* cb */, runNewMdnsBackend);
            }
        };

        assertThrows(IllegalArgumentException.class, () -> new NsdManager(mContext, service));
    }

    @Test
    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testRegisterOffloadEngine_checkPermission_V() {
        final NsdManager client = connectClient(mService);
        final OffloadEngine offloadEngine = mock(OffloadEngine.class);
        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(NETWORK_STACK);
        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                PERMISSION_MAINLINE_NETWORK_STACK);
        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(NETWORK_SETTINGS);
        doReturn(PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                REGISTER_NSD_OFFLOAD_ENGINE);

        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                REGISTER_NSD_OFFLOAD_ENGINE);
        doReturn(PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(DEVICE_POWER);
        assertThrows(SecurityException.class,
                () -> client.registerOffloadEngine("iface1", OffloadEngine.OFFLOAD_TYPE_REPLY,
                        OffloadEngine.OFFLOAD_CAPABILITY_BYPASS_MULTICAST_LOCK, Runnable::run,
                        offloadEngine));
        doReturn(PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                REGISTER_NSD_OFFLOAD_ENGINE);
        final OffloadEngine offloadEngine2 = mock(OffloadEngine.class);
        client.registerOffloadEngine("iface2", OffloadEngine.OFFLOAD_TYPE_REPLY,
                OffloadEngine.OFFLOAD_CAPABILITY_BYPASS_MULTICAST_LOCK, Runnable::run,
                offloadEngine2);
        client.unregisterOffloadEngine(offloadEngine2);
    }

    @Test
    @EnableCompatChanges(ENABLE_PLATFORM_MDNS_BACKEND)
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testRegisterOffloadEngine_checkPermission_U() {
        final NsdManager client = connectClient(mService);
        final OffloadEngine offloadEngine = mock(OffloadEngine.class);
        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(NETWORK_STACK);
        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                PERMISSION_MAINLINE_NETWORK_STACK);
        doReturn(PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(NETWORK_SETTINGS);
        doReturn(PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                REGISTER_NSD_OFFLOAD_ENGINE);

        doReturn(PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(DEVICE_POWER);
        client.registerOffloadEngine("iface2", OffloadEngine.OFFLOAD_TYPE_REPLY,
                OffloadEngine.OFFLOAD_CAPABILITY_BYPASS_MULTICAST_LOCK, Runnable::run,
                offloadEngine);
        client.unregisterOffloadEngine(offloadEngine);
    }


    private void waitForIdle() {
        HandlerUtils.waitForIdle(mHandler, TIMEOUT_MS);
    }

    NsdService makeService() {
        final NsdService service = new NsdService(mContext, mHandler, CLEANUP_DELAY_MS, mDeps) {
            @Override
            public INsdServiceConnector connect(INsdManagerCallback baseCb,
                    boolean runNewMdnsBackend) {
                // Wrap the callback in a transparent mock, to mock asBinder returning a
                // LinkToDeathRecorder. This will allow recording the binder death recipient
                // registered on the callback. Use a transparent mock and not a spy as the actual
                // implementation class is not public and cannot be spied on by Mockito.
                final INsdManagerCallback cb = mock(INsdManagerCallback.class,
                        AdditionalAnswers.delegatesTo(baseCb));
                doReturn(new LinkToDeathRecorder()).when(cb).asBinder();
                mCreatedCallbacks.add(cb);
                return super.connect(cb, runNewMdnsBackend);
            }
        };
        return service;
    }

    private INsdManagerCallback getCallback() {
        return mCreatedCallbacks.remove();
    }

    NsdManager connectClient(NsdService service) {
        final NsdManager nsdManager = new NsdManager(mContext, service);
        // Wait for client registration done.
        waitForIdle();
        return nsdManager;
    }

    void verifyDelayMaybeStopDaemon(long cleanupDelayMs) throws Exception {
        waitForIdle();
        // Stop daemon shouldn't be called immediately.
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();

        // Clean up the daemon after CLEANUP_DELAY_MS.
        verify(mMockMDnsM, timeout(cleanupDelayMs + TIMEOUT_MS)).unregisterEventListener(any());
        verify(mMockMDnsM, timeout(cleanupDelayMs + TIMEOUT_MS)).stopDaemon();
    }

    /**
     * Return true if two service info are the same.
     *
     * Useful for argument matchers as {@link NsdServiceInfo} does not implement equals.
     */
    private boolean matches(NsdServiceInfo a, NsdServiceInfo b) {
        return Objects.equals(a.getServiceName(), b.getServiceName())
                && Objects.equals(a.getServiceType(), b.getServiceType())
                && Objects.equals(a.getHost(), b.getHost())
                && Objects.equals(a.getNetwork(), b.getNetwork())
                && Objects.equals(a.getAttributes(), b.getAttributes());
    }

    public static class TestHandler extends Handler {
        public Message lastMessage;

        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            lastMessage = obtainMessage();
            lastMessage.copyFrom(msg);
        }
    }
}
