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

package com.android.server.connectivity;

import static android.content.Intent.ACTION_CONFIGURATION_CHANGED;
import static android.net.CaptivePortal.APP_RETURN_DISMISSED;
import static android.net.CaptivePortal.APP_RETURN_WANTED_AS_IS;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_DNS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_FALLBACK;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_HTTP;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_HTTPS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_PRIVDNS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_PARTIAL;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_SKIPPED;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_VALID;
import static android.net.InetAddresses.parseNumericAddress;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.metrics.ValidationProbeEvent.PROBE_HTTP;
import static android.net.util.DataStallUtils.CONFIG_DATA_STALL_CONSECUTIVE_DNS_TIMEOUT_THRESHOLD;
import static android.net.util.DataStallUtils.CONFIG_DATA_STALL_EVALUATION_TYPE;
import static android.net.util.DataStallUtils.CONFIG_DATA_STALL_MIN_EVALUATE_INTERVAL;
import static android.net.util.DataStallUtils.CONFIG_DATA_STALL_TCP_POLLING_INTERVAL;
import static android.net.util.DataStallUtils.CONFIG_DATA_STALL_VALID_DNS_TIME_THRESHOLD;
import static android.net.util.DataStallUtils.DATA_STALL_EVALUATION_TYPE_DNS;
import static android.net.util.DataStallUtils.DATA_STALL_EVALUATION_TYPE_TCP;
import static android.net.util.DataStallUtils.DEFAULT_DATA_STALL_EVALUATION_TYPES;
import static android.os.Build.VERSION_CODES.S_V2;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;

import static com.android.net.module.util.NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTPS_URL;
import static com.android.net.module.util.NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTP_URL;
import static com.android.net.module.util.NetworkStackConstants.TEST_URL_EXPIRATION_TIME;
import static com.android.networkstack.util.DnsUtils.PRIVATE_DNS_PROBE_HOST_SUFFIX;
import static com.android.networkstack.util.NetworkStackUtils.CAPTIVE_PORTAL_FALLBACK_PROBE_SPECS;
import static com.android.networkstack.util.NetworkStackUtils.CAPTIVE_PORTAL_MODE;
import static com.android.networkstack.util.NetworkStackUtils.CAPTIVE_PORTAL_MODE_IGNORE;
import static com.android.networkstack.util.NetworkStackUtils.CAPTIVE_PORTAL_MODE_PROMPT;
import static com.android.networkstack.util.NetworkStackUtils.CAPTIVE_PORTAL_OTHER_FALLBACK_URLS;
import static com.android.networkstack.util.NetworkStackUtils.CAPTIVE_PORTAL_USE_HTTPS;
import static com.android.networkstack.util.NetworkStackUtils.DEFAULT_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT;
import static com.android.networkstack.util.NetworkStackUtils.DNS_PROBE_PRIVATE_IP_NO_INTERNET_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.REEVALUATE_WHEN_RESUME;
import static com.android.server.connectivity.NetworkMonitor.CONFIG_ASYNC_PRIVDNS_PROBE_TIMEOUT_MS;
import static com.android.server.connectivity.NetworkMonitor.INITIAL_REEVALUATE_DELAY_MS;
import static com.android.server.connectivity.NetworkMonitor.extractCharset;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.CaptivePortalData;
import android.net.ConnectivityManager;
import android.net.DataStallReportParcelable;
import android.net.DnsResolver;
import android.net.INetd;
import android.net.INetworkMonitorCallbacks;
import android.net.InetAddresses;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkTestResultParcelable;
import android.net.PrivateDnsConfigParcel;
import android.net.Uri;
import android.net.captiveportal.CaptivePortalProbeResult;
import android.net.metrics.IpConnectivityLog;
import android.net.networkstack.aidl.NetworkMonitorParameters;
import android.net.shared.PrivateDnsConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;

import androidx.test.filters.SmallTest;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.SharedLog;
import com.android.networkstack.NetworkStackNotifier;
import com.android.networkstack.R;
import com.android.networkstack.apishim.CaptivePortalDataShimImpl;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.NetworkAgentConfigShimImpl;
import com.android.networkstack.apishim.NetworkInformationShimImpl;
import com.android.networkstack.apishim.common.CaptivePortalDataShim;
import com.android.networkstack.apishim.common.NetworkAgentConfigShim;
import com.android.networkstack.apishim.common.NetworkInformationShim;
import com.android.networkstack.apishim.common.ShimUtils;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.metrics.DataStallDetectionStats;
import com.android.networkstack.metrics.DataStallStatsUtils;
import com.android.networkstack.netlink.TcpSocketTracker;
import com.android.networkstack.util.NetworkStackUtils;
import com.android.server.NetworkStackService.NetworkStackServiceManager;
import com.android.server.connectivity.nano.CellularData;
import com.android.server.connectivity.nano.DnsEvent;
import com.android.server.connectivity.nano.WifiData;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.FunctionalUtils.ThrowingConsumer;
import com.android.testutils.HandlerUtils;

import com.google.protobuf.nano.MessageNano;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.net.ssl.SSLHandshakeException;

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@SuppressLint("NewApi")  // Uses hidden APIs, which the linter would identify as missing APIs.
public class NetworkMonitorTest {
    private static final String LOCATION_HEADER = "location";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private @Mock Context mContext;
    private @Mock Configuration mConfiguration;
    private @Mock Resources mResources;
    private @Mock IpConnectivityLog mLogger;
    private @Mock SharedLog mValidationLogger;
    private @Mock DnsResolver mDnsResolver;
    private @Mock ConnectivityManager mCm;
    private @Mock TelephonyManager mTelephony;
    private @Mock WifiManager mWifi;
    private @Mock NetworkStackServiceManager mServiceManager;
    private @Mock NetworkStackNotifier mNotifier;
    private @Mock HttpURLConnection mHttpConnection;
    private @Mock HttpURLConnection mOtherHttpConnection1;
    private @Mock HttpURLConnection mOtherHttpConnection2;
    private @Mock HttpURLConnection mHttpsConnection;
    private @Mock HttpURLConnection mOtherHttpsConnection1;
    private @Mock HttpURLConnection mOtherHttpsConnection2;
    private @Mock HttpURLConnection mFallbackConnection;
    private @Mock HttpURLConnection mOtherFallbackConnection;
    private @Mock HttpURLConnection mTestOverriddenUrlConnection;
    private @Mock HttpURLConnection mCapportApiConnection;
    private @Mock HttpURLConnection mSpeedTestConnection;
    private @Mock Random mRandom;
    private @Mock NetworkMonitor.Dependencies mDependencies;
    // Mockito can't create a mock of INetworkMonitorCallbacks on Q because it can't find
    // CaptivePortalData on notifyCaptivePortalDataChanged. Use a spy on a mock IBinder instead.
    private INetworkMonitorCallbacks mCallbacks = spy(
            INetworkMonitorCallbacks.Stub.asInterface(mock(IBinder.class)));
    private @Spy Network mCleartextDnsNetwork = new Network(TEST_NETID);
    private @Mock Network mNetwork;
    private @Mock DataStallStatsUtils mDataStallStatsUtils;
    private @Mock TcpSocketTracker.Dependencies mTstDependencies;
    private @Mock INetd mNetd;
    private @Mock TcpSocketTracker mTst;
    @GuardedBy("mCreatedNetworkMonitors")
    private final HashSet<WrappedNetworkMonitor> mCreatedNetworkMonitors = new HashSet<>();
    @GuardedBy("mRegisteredReceivers")
    private final HashSet<BroadcastReceiver> mRegisteredReceivers = new HashSet<>();
    private @Mock Context mMccContext;
    private @Mock Resources mMccResource;
    private @Mock WifiInfo mWifiInfo;

    private static final int TEST_NETID = 4242;
    private static final int TEST_NETID2 = 2121;
    private static final String TEST_HTTP_URL = "http://www.google.com/gen_204";
    private static final String TEST_HTTP_OTHER_URL1 = "http://other1.google.com/gen_204";
    private static final String TEST_HTTP_OTHER_URL2 = "http://other2.google.com/gen_204";
    private static final String TEST_HTTPS_URL = "https://www.google.com/gen_204";
    private static final String TEST_HTTPS_OTHER_URL1 = "https://other1.google.com/gen_204";
    private static final String TEST_HTTPS_OTHER_URL2 = "https://other2.google.com/gen_204";
    private static final String TEST_FALLBACK_URL = "http://fallback.google.com/gen_204";
    private static final String TEST_OTHER_FALLBACK_URL = "http://otherfallback.google.com/gen_204";
    private static final String TEST_INVALID_OVERRIDE_URL = "https://override.example.com/test";
    private static final String TEST_OVERRIDE_URL = "http://localhost:12345/test";
    private static final String TEST_CAPPORT_API_URL = "https://capport.example.com/api";
    private static final String TEST_LOGIN_URL = "https://testportal.example.com/login";
    private static final String TEST_VENUE_INFO_URL = "https://venue.example.com/info";
    private static final String TEST_SPEED_TEST_URL = "https://speedtest.example.com";
    private static final String TEST_RELATIVE_URL = "/test/relative/gen_204";
    private static final String TEST_MCCMNC = "123456";
    private static final String TEST_FRIENDLY_NAME = "Friendly Name";
    private static final String[] TEST_HTTP_URLS = {TEST_HTTP_OTHER_URL1, TEST_HTTP_OTHER_URL2};
    private static final String[] TEST_HTTPS_URLS = {TEST_HTTPS_OTHER_URL1, TEST_HTTPS_OTHER_URL2};
    private static final int TEST_TCP_FAIL_RATE = 99;
    private static final int TEST_TCP_PACKET_COUNT = 50;
    private static final long TEST_ELAPSED_TIME_MS = 123456789L;
    private static final int TEST_SIGNAL_STRENGTH = -100;
    private static final int VALIDATION_RESULT_INVALID = 0;
    private static final int VALIDATION_RESULT_PORTAL = 0;
    private static final String TEST_REDIRECT_URL = "android.com";
    private static final int PROBES_PRIVDNS_VALID = NETWORK_VALIDATION_PROBE_DNS
            | NETWORK_VALIDATION_PROBE_HTTPS | NETWORK_VALIDATION_PROBE_PRIVDNS;

    private static final int RETURN_CODE_DNS_SUCCESS = 0;
    private static final int RETURN_CODE_DNS_TIMEOUT = 255;
    private static final int DEFAULT_DNS_TIMEOUT_THRESHOLD = 5;

    private static final int HANDLER_TIMEOUT_MS = 1000;
    private static final int TEST_MIN_STALL_EVALUATE_INTERVAL_MS = 500;
    private static final int TEST_MIN_VALID_STALL_DNS_TIME_THRESHOLD_MS = 5000;
    private static final int STALL_EXPECTED_LAST_PROBE_TIME_MS =
            TEST_MIN_STALL_EVALUATE_INTERVAL_MS + HANDLER_TIMEOUT_MS;
    private static final NetworkAgentConfigShim TEST_AGENT_CONFIG =
            NetworkAgentConfigShimImpl.newInstance(null);
    private static final LinkProperties TEST_LINK_PROPERTIES = new LinkProperties();
    private static final int THREAD_QUIT_MAX_RETRY_COUNT = 3;

    // Cannot have a static member for the LinkProperties with captive portal API information, as
    // the initializer would crash on Q (the members in LinkProperties were introduced in R).
    private static LinkProperties makeCapportLPs() {
        final LinkProperties lp = new LinkProperties(TEST_LINK_PROPERTIES);
        lp.setCaptivePortalApiUrl(Uri.parse(TEST_CAPPORT_API_URL));
        return lp;
    }

    private static final NetworkCapabilities CELL_SUSPENDED_METERED_CAPABILITIES =
            new NetworkCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NET_CAPABILITY_INTERNET);

    private static final NetworkCapabilities CELL_METERED_CAPABILITIES =
            new NetworkCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NET_CAPABILITY_INTERNET)
            .addCapability(NET_CAPABILITY_NOT_SUSPENDED);

    private static final NetworkCapabilities CELL_NOT_METERED_CAPABILITIES =
            new NetworkCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

    private static final NetworkCapabilities WIFI_NOT_METERED_CAPABILITIES =
            new NetworkCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

    private static final NetworkCapabilities CELL_NO_INTERNET_CAPABILITIES =
            new NetworkCapabilities().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

    private static final NetworkCapabilities WIFI_OEM_PAID_CAPABILITIES =
            new NetworkCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .addCapability(NET_CAPABILITY_OEM_PAID)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);

    /**
     * Fakes DNS responses.
     *
     * Allows test methods to configure the IP addresses that will be resolved by
     * Network#getAllByName and by DnsResolver#query.
     */
    class FakeDns {
        /** Data class to record the Dns entry. */
        class DnsEntry {
            final String mHostname;
            final int mType;
            final AddressSupplier mAddressesSupplier;
            DnsEntry(String host, int type, AddressSupplier addr) {
                mHostname = host;
                mType = type;
                mAddressesSupplier = addr;
            }
            // Full match or partial match that target host contains the entry hostname to support
            // random private dns probe hostname.
            private boolean matches(String hostname, int type) {
                return hostname.endsWith(mHostname) && type == mType;
            }
        }
        interface AddressSupplier {
            List<InetAddress> get() throws DnsResolver.DnsException;
        }

        class InstantAddressSupplier implements AddressSupplier {
            private final List<InetAddress> mAddresses;
            InstantAddressSupplier(List<InetAddress> addresses) {
                mAddresses = addresses;
            }
            @Override
            public List<InetAddress> get() {
                return mAddresses;
            }
        }

        private final ArrayList<DnsEntry> mAnswers = new ArrayList<DnsEntry>();
        private boolean mNonBypassPrivateDnsWorking = true;

        /** Whether DNS queries on mNonBypassPrivateDnsWorking should succeed. */
        private void setNonBypassPrivateDnsWorking(boolean working) {
            mNonBypassPrivateDnsWorking = working;
        }

        /** Clears all DNS entries. */
        private void clearAll() {
            synchronized (mAnswers) {
                mAnswers.clear();
            }
        }

        /** Returns the answer for a given name and type on the given mock network. */
        private CompletableFuture<List<InetAddress>> getAnswer(Network mockNetwork, String hostname,
                int type) {
            if (mockNetwork == mNetwork && !mNonBypassPrivateDnsWorking) {
                return CompletableFuture.completedFuture(null);
            }

            final AddressSupplier answerSupplier;

            synchronized (mAnswers) {
                answerSupplier = mAnswers.stream()
                        .filter(e -> e.matches(hostname, type))
                        .map(answer -> answer.mAddressesSupplier).findFirst().orElse(null);
            }
            if (answerSupplier == null) {
                return CompletableFuture.completedFuture(null);
            }

            if (answerSupplier instanceof InstantAddressSupplier) {
                // Save latency waiting for a query thread if the answer is hardcoded.
                return CompletableFuture.completedFuture(
                        ((InstantAddressSupplier) answerSupplier).get());
            }
            final CompletableFuture<List<InetAddress>> answerFuture = new CompletableFuture<>();
            new Thread(() -> {
                try {
                    answerFuture.complete(answerSupplier.get());
                } catch (DnsResolver.DnsException e) {
                    answerFuture.completeExceptionally(e);
                }
            }).start();
            return answerFuture;
        }

        /** Sets the answer for a given name and type. */
        private void setAnswer(String hostname, String[] answer, int type) {
            setAnswer(hostname, new InstantAddressSupplier(generateAnswer(answer)), type);
        }

        private void setAnswer(String hostname, AddressSupplier answerSupplier, int type) {
            DnsEntry record = new DnsEntry(hostname, type, answerSupplier);
            synchronized (mAnswers) {
                // Remove the existing one.
                mAnswers.removeIf(entry -> entry.matches(hostname, type));
                // Add or replace a new record.
                mAnswers.add(record);
            }
        }

        private List<InetAddress> generateAnswer(String[] answer) {
            if (answer == null) return new ArrayList<>();
            return Arrays.stream(answer).map(InetAddresses::parseNumericAddress).collect(toList());
        }

        /** Simulates a getAllByName call for the specified name on the specified mock network. */
        private InetAddress[] getAllByName(Network mockNetwork, String hostname)
                throws UnknownHostException {
            final List<InetAddress> answer;
            try {
                answer = queryAllTypes(mockNetwork, hostname).get(
                        HANDLER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new AssertionError("No mock DNS reply within timeout", e);
            }
            if (answer == null || answer.size() == 0) {
                throw new UnknownHostException(hostname);
            }
            return answer.toArray(new InetAddress[0]);
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private CompletableFuture<List<InetAddress>> queryAllTypes(
                Network mockNetwork, String hostname) {
            if (mockNetwork == mNetwork && !mNonBypassPrivateDnsWorking) {
                return CompletableFuture.completedFuture(null);
            }

            final CompletableFuture<List<InetAddress>> aFuture =
                    getAnswer(mockNetwork, hostname, TYPE_A)
                            .exceptionally(e -> Collections.emptyList());
            final CompletableFuture<List<InetAddress>> aaaaFuture =
                    getAnswer(mockNetwork, hostname, TYPE_AAAA)
                            .exceptionally(e -> Collections.emptyList());

            final CompletableFuture<List<InetAddress>> combinedFuture = new CompletableFuture<>();
            aFuture.thenAcceptBoth(aaaaFuture, (res1, res2) -> {
                final List<InetAddress> answer = new ArrayList<>();
                if (res1 != null) answer.addAll(res1);
                if (res2 != null) answer.addAll(res2);
                combinedFuture.complete(answer);
            });
            return combinedFuture;
        }

        /** Starts mocking DNS queries. */
        private void startMocking() throws UnknownHostException {
            // Queries on mNetwork using getAllByName.
            doAnswer(invocation -> {
                return getAllByName((Network) invocation.getMock(), invocation.getArgument(0));
            }).when(mNetwork).getAllByName(any());

            // Queries on mCleartextDnsNetwork using DnsResolver#query.
            doAnswer(invocation -> {
                return mockQuery(invocation, 0 /* posNetwork */, 1 /* posHostname */,
                        3 /* posExecutor */, 5 /* posCallback */, -1 /* posType */);
            }).when(mDnsResolver).query(any(), any(), anyInt(), any(), any(), any());

            // Queries on mCleartextDnsNetwork using DnsResolver#query with QueryType.
            doAnswer(invocation -> {
                return mockQuery(invocation, 0 /* posNetwork */, 1 /* posHostname */,
                        4 /* posExecutor */, 6 /* posCallback */, 2 /* posType */);
            }).when(mDnsResolver).query(any(), any(), anyInt(), anyInt(), any(), any(), any());
        }

        // Mocking queries on DnsResolver#query.
        private Answer mockQuery(InvocationOnMock invocation, int posNetwork, int posHostname,
                int posExecutor, int posCallback, int posType) {
            String hostname = (String) invocation.getArgument(posHostname);
            Executor executor = (Executor) invocation.getArgument(posExecutor);
            DnsResolver.Callback<List<InetAddress>> callback = invocation.getArgument(posCallback);
            Network network = invocation.getArgument(posNetwork);

            final CompletableFuture<List<InetAddress>> answerFuture = posType != -1
                    ? getAnswer(network, hostname, invocation.getArgument(posType))
                    : queryAllTypes(network, hostname);

            answerFuture.whenComplete((answer, exception) -> {
                new Handler(Looper.getMainLooper()).post(() -> executor.execute(() -> {
                    if (exception != null) {
                        if (!(exception instanceof DnsResolver.DnsException)) {
                            throw new AssertionError("Test error building DNS response", exception);
                        }
                        callback.onError((DnsResolver.DnsException) exception);
                        return;
                    }
                    if (answer != null && answer.size() > 0) {
                        callback.onAnswer(answer, 0);
                    }
                }));
            });
            // If the future does not complete or has no answer do nothing. The timeout should fire.
            return null;
        }
    }

    private FakeDns mFakeDns;

    @GuardedBy("mThreadsToBeCleared")
    private final ArrayList<Thread> mThreadsToBeCleared = new ArrayList<>();
    @GuardedBy("mExecutorServiceToBeCleared")
    private final ArrayList<ExecutorService> mExecutorServiceToBeCleared = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mCleartextDnsNetwork).when(mDependencies).getPrivateDnsBypassNetwork(any());
        doReturn(mDnsResolver).when(mDependencies).getDnsResolver();
        doReturn(mRandom).when(mDependencies).getRandom();
        doReturn(Settings.Global.CAPTIVE_PORTAL_MODE_PROMPT).when(mDependencies)
                .getSetting(any(), eq(Settings.Global.CAPTIVE_PORTAL_MODE), anyInt());
        doReturn(1).when(mDependencies)
                .getDeviceConfigPropertyInt(any(), eq(CAPTIVE_PORTAL_USE_HTTPS), anyInt());
        doReturn(TEST_HTTP_URL).when(mDependencies)
                .getSetting(any(), eq(Settings.Global.CAPTIVE_PORTAL_HTTP_URL), any());
        doReturn(TEST_HTTPS_URL).when(mDependencies)
                .getSetting(any(), eq(Settings.Global.CAPTIVE_PORTAL_HTTPS_URL), any());
        doAnswer((invocation) -> {
            synchronized (mThreadsToBeCleared) {
                mThreadsToBeCleared.add(invocation.getArgument(0));
            }
            return null;
        }).when(mDependencies).onThreadCreated(any());
        doAnswer((invocation) -> {
            synchronized (mExecutorServiceToBeCleared) {
                mExecutorServiceToBeCleared.add(invocation.getArgument(0));
            }
            return null;
        }).when(mDependencies).onExecutorServiceCreated(any());

        doReturn(mCleartextDnsNetwork).when(mNetwork).getPrivateDnsBypassingCopy();

        doReturn(mCm).when(mContext).getSystemService(Context.CONNECTIVITY_SERVICE);
        doReturn(mTelephony).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(mWifi).when(mContext).getSystemService(Context.WIFI_SERVICE);
        doReturn(mResources).when(mContext).getResources();

        doReturn(mNotifier).when(mServiceManager).getNotifier();

        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mTelephony).getDataNetworkType();
        doReturn(TEST_MCCMNC).when(mTelephony).getNetworkOperator();
        doReturn(TEST_MCCMNC).when(mTelephony).getSimOperator();

        doReturn("").when(mResources).getString(anyInt());
        doReturn(new String[0]).when(mResources).getStringArray(anyInt());
        doReturn(mConfiguration).when(mResources).getConfiguration();
        doReturn(mMccResource).when(mMccContext).getResources();

        setFallbackUrl(TEST_FALLBACK_URL);
        setOtherFallbackUrls(TEST_OTHER_FALLBACK_URL);
        setFallbackSpecs(null); // Test with no fallback spec by default
        doReturn(0).when(mRandom).nextInt();

        doReturn(mNetd).when(mTstDependencies).getNetd();
        // DNS probe timeout should not be defined more than half of HANDLER_TIMEOUT_MS. Otherwise,
        // it will fail the test because of timeout expired for querying AAAA and A sequentially.
        doReturn(200).when(mResources)
                .getInteger(eq(R.integer.config_captive_portal_dns_probe_timeout));
        doReturn(200).when(mDependencies).getDeviceConfigPropertyInt(
                eq(NAMESPACE_CONNECTIVITY), eq(CONFIG_ASYNC_PRIVDNS_PROBE_TIMEOUT_MS), anyInt());

        doAnswer((invocation) -> {
            URL url = invocation.getArgument(0);
            switch(url.toString()) {
                case TEST_HTTP_URL:
                    return mHttpConnection;
                case TEST_HTTP_OTHER_URL1:
                    return mOtherHttpConnection1;
                case TEST_HTTP_OTHER_URL2:
                    return mOtherHttpConnection2;
                case TEST_HTTPS_URL:
                    return mHttpsConnection;
                case TEST_HTTPS_OTHER_URL1:
                    return mOtherHttpsConnection1;
                case TEST_HTTPS_OTHER_URL2:
                    return mOtherHttpsConnection2;
                case TEST_FALLBACK_URL:
                    return mFallbackConnection;
                case TEST_OTHER_FALLBACK_URL:
                    return mOtherFallbackConnection;
                case TEST_OVERRIDE_URL:
                case TEST_INVALID_OVERRIDE_URL:
                    return mTestOverriddenUrlConnection;
                case TEST_CAPPORT_API_URL:
                    return mCapportApiConnection;
                case TEST_SPEED_TEST_URL:
                    return mSpeedTestConnection;
                default:
                    fail("URL not mocked: " + url.toString());
                    return null;
            }
        }).when(mCleartextDnsNetwork).openConnection(any());
        initHttpConnection(mHttpConnection);
        initHttpConnection(mHttpsConnection);
        initHttpConnection(mFallbackConnection);
        initHttpConnection(mOtherFallbackConnection);

        mFakeDns = new FakeDns();
        mFakeDns.startMocking();
        // Set private dns suffix answer. sendPrivateDnsProbe() in NetworkMonitor send probe with
        // one time hostname. The hostname will be [random generated UUID] + HOST_SUFFIX differently
        // each time. That means the host answer cannot be pre-set into the answer list. Thus, set
        // the host suffix and use partial match in FakeDns to match the target host and reply the
        // intended answer.
        mFakeDns.setAnswer(PRIVATE_DNS_PROBE_HOST_SUFFIX, new String[]{"192.0.2.2"}, TYPE_A);
        mFakeDns.setAnswer(PRIVATE_DNS_PROBE_HOST_SUFFIX, new String[]{"2001:db8::1"}, TYPE_AAAA);

        doAnswer((invocation) -> {
            synchronized (mRegisteredReceivers) {
                mRegisteredReceivers.add(invocation.getArgument(0));
            }
            return new Intent();
        }).when(mContext).registerReceiver(any(BroadcastReceiver.class), any());
        doAnswer((invocation) -> {
            synchronized (mRegisteredReceivers) {
                mRegisteredReceivers.add(invocation.getArgument(0));
            }
            return new Intent();
        }).when(mContext).registerReceiver(any(BroadcastReceiver.class), any(), anyInt());

        doAnswer((invocation) -> {
            synchronized (mRegisteredReceivers) {
                mRegisteredReceivers.remove(invocation.getArgument(0));
            }
            return null;
        }).when(mContext).unregisterReceiver(any());

        initCallbacks(11 /* interfaceVersion */);

        setMinDataStallEvaluateInterval(TEST_MIN_STALL_EVALUATE_INTERVAL_MS);
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_DNS);
        setValidDataStallDnsTimeThreshold(TEST_MIN_VALID_STALL_DNS_TIME_THRESHOLD_MS);
        setConsecutiveDnsTimeoutThreshold(5);
    }

    private static <T> void quitResourcesThat(Supplier<List<T>> supplier,
            ThrowingConsumer terminator) throws Exception {
        // Run it multiple times since new threads might be generated in a thread
        // that is about to be terminated, e.g. each thread that runs
        // isCaptivePortal could generate 2 more probing threads.
        for (int retryCount = 0; retryCount < THREAD_QUIT_MAX_RETRY_COUNT; retryCount++) {
            final List<T> resourcesToBeCleared = supplier.get();
            if (resourcesToBeCleared.isEmpty()) return;
            for (final T resource : resourcesToBeCleared) {
                terminator.accept(resource);
            }
        }

        assertEquals(Collections.emptyList(), supplier.get());
    }

    private void quitNetworkMonitors() throws Exception {
        quitResourcesThat(() -> {
            synchronized (mCreatedNetworkMonitors) {
                final ArrayList<WrappedNetworkMonitor> ret =
                        new ArrayList<>(mCreatedNetworkMonitors);
                mCreatedNetworkMonitors.clear();
                return ret;
            }
        }, (it) -> {
            final WrappedNetworkMonitor nm = (WrappedNetworkMonitor) it;
            nm.notifyNetworkDisconnected();
            nm.awaitQuit();
        });
        synchronized (mRegisteredReceivers) {
            assertEquals("BroadcastReceiver still registered after disconnect",
                    0, mRegisteredReceivers.size());
        }
        quitThreads();
        quitExecutorServices();
    }

    private void quitExecutorServices() throws Exception {
        quitResourcesThat(() -> {
            synchronized (mExecutorServiceToBeCleared) {
                final ArrayList<ExecutorService> ret = new ArrayList<>(mExecutorServiceToBeCleared);
                mExecutorServiceToBeCleared.clear();
                return ret;
            }
        }, (it) -> {
            final ExecutorService ecs = (ExecutorService) it;
            ecs.awaitTermination(HANDLER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        });
    }

    private void quitThreads() throws Exception {
        quitResourcesThat(() -> {
            synchronized (mThreadsToBeCleared) {
                final ArrayList<Thread> ret = new ArrayList<>(mThreadsToBeCleared);
                mThreadsToBeCleared.clear();
                return ret;
            }
        }, (it) -> {
            final Thread th = (Thread) it;
            th.interrupt();
            th.join(HANDLER_TIMEOUT_MS);
            if (th.isAlive()) fail("Threads did not terminate within timeout.");
        });
    }

    @After
    public void tearDown() throws Exception {
        mFakeDns.clearAll();
        quitNetworkMonitors();
        // Clear mocks to prevent from stubs holding instances and cause memory leaks.
        Mockito.framework().clearInlineMocks();
    }

    private void initHttpConnection(HttpURLConnection connection) {
        doReturn(new ArrayMap<>()).when(connection).getRequestProperties();
        // Explicitly set the HttpURLConnection methods so that these will not interact with real
        // methods to prevent threading issue in the test.
        doReturn(new HashMap<>()).when(connection).getHeaderFields();
        doReturn(null).when(connection).getHeaderField(eq("location"));
        doNothing().when(connection).setInstanceFollowRedirects(anyBoolean());
        doNothing().when(connection).setConnectTimeout(anyInt());
        doNothing().when(connection).setReadTimeout(anyInt());
        doNothing().when(connection).setRequestProperty(any(), any());
        doNothing().when(connection).setUseCaches(anyBoolean());
        doNothing().when(connection).disconnect();
    }

    private void initCallbacks(int interfaceVersion) throws Exception {
        try {
            doReturn(interfaceVersion).when(mCallbacks).getInterfaceVersion();
        } catch (RemoteException e) {
            // Can't happen as mCallbacks is a mock
            fail("Error mocking getInterfaceVersion" + e);
        }
        // Explicitly set the callback methods so that these will not interact with real methods
        // to prevent threading issue in the test. Really this should be a mock but this is not
        // possible currently ; see comments on the member for the reasons.
        doNothing().when(mCallbacks).notifyNetworkTestedWithExtras(any());
        doNothing().when(mCallbacks).showProvisioningNotification(any(), any());
        doNothing().when(mCallbacks).hideProvisioningNotification();
        doNothing().when(mCallbacks).notifyProbeStatusChanged(anyInt(), anyInt());
        doNothing().when(mCallbacks).notifyDataStallSuspected(any());
        doNothing().when(mCallbacks).notifyCaptivePortalDataChanged(any());
        doNothing().when(mCallbacks).notifyPrivateDnsConfigResolved(any());
        doNothing().when(mCallbacks).notifyNetworkTested(anyInt(), any());
    }

    private boolean getIsCaptivePortalCheckEnabled(Context context,
                NetworkMonitor.Dependencies dp) {
        String symbol = CAPTIVE_PORTAL_MODE;
        int defaultValue = CAPTIVE_PORTAL_MODE_PROMPT;
        int mode = dp.getSetting(context, symbol, defaultValue);
        return mode != CAPTIVE_PORTAL_MODE_IGNORE;
    }

    private TcpSocketTracker getTcpSocketTrackerOrNull(Context context,
                NetworkMonitor.Dependencies dp) {
        return (getIsCaptivePortalCheckEnabled(context, dp)
                && (dp.getDeviceConfigPropertyInt(
                NAMESPACE_CONNECTIVITY,
                CONFIG_DATA_STALL_EVALUATION_TYPE,
                DEFAULT_DATA_STALL_EVALUATION_TYPES)
                & DATA_STALL_EVALUATION_TYPE_TCP) != 0) ? mTst : null;
    }

    private class WrappedNetworkMonitor extends NetworkMonitor {
        private long mProbeTime = 0;
        private final ConditionVariable mQuitCv = new ConditionVariable(false);

        WrappedNetworkMonitor() {
            super(mContext, mCallbacks, mNetwork, mLogger, mValidationLogger, mServiceManager,
                    mDependencies, getTcpSocketTrackerOrNull(mContext, mDependencies));
        }

        @Override
        protected long getLastProbeTime() {
            return mProbeTime;
        }

        protected void setLastProbeTime(long time) {
            mProbeTime = time;
        }

        @Override
        protected void addDnsEvents(@NonNull final DataStallDetectionStats.Builder stats) {
            if ((getDataStallEvaluationType() & DATA_STALL_EVALUATION_TYPE_DNS) != 0) {
                generateTimeoutDnsEvent(stats, DEFAULT_DNS_TIMEOUT_THRESHOLD);
            }
        }

        @Override
        protected void onQuitting() {
            super.onQuitting();
            mQuitCv.open();
        }

        protected void awaitQuit() {
            assertTrue("NetworkMonitor did not quit after " + HANDLER_TIMEOUT_MS + "ms",
                    mQuitCv.block(HANDLER_TIMEOUT_MS));
        }

        protected Context getContext() {
            return mContext;
        }
    }

    private WrappedNetworkMonitor makeMonitor(NetworkCapabilities nc) {
        final WrappedNetworkMonitor nm = new WrappedNetworkMonitor();
        nm.start();
        setNetworkCapabilities(nm, nc);
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
        mCreatedNetworkMonitors.add(nm);

        return nm;
    }

    private WrappedNetworkMonitor makeCellMeteredNetworkMonitor() {
        final WrappedNetworkMonitor nm = makeMonitor(CELL_METERED_CAPABILITIES);
        return nm;
    }

    private WrappedNetworkMonitor makeCellNotMeteredNetworkMonitor() {
        final WrappedNetworkMonitor nm = makeMonitor(CELL_NOT_METERED_CAPABILITIES);
        return nm;
    }

    private WrappedNetworkMonitor makeWifiNotMeteredNetworkMonitor() {
        final WrappedNetworkMonitor nm = makeMonitor(WIFI_NOT_METERED_CAPABILITIES);
        return nm;
    }

    private void setNetworkCapabilities(NetworkMonitor nm, NetworkCapabilities nc) {
        nm.notifyNetworkCapabilitiesChanged(nc);
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
    }

    @Test
    public void testOnlyWifiTransport() {
        final WrappedNetworkMonitor wnm = makeMonitor(CELL_METERED_CAPABILITIES);
        assertFalse(wnm.onlyWifiTransport());
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .addTransportType(TRANSPORT_VPN);
        setNetworkCapabilities(wnm, nc);
        assertFalse(wnm.onlyWifiTransport());
        nc.removeTransportType(TRANSPORT_VPN);
        setNetworkCapabilities(wnm, nc);
        assertTrue(wnm.onlyWifiTransport());
    }

    @Test
    public void testNeedEvaluatingBandwidth() throws Exception {
        // Make metered network first, the transport type is TRANSPORT_CELLULAR. That means the
        // test cannot pass the condition check in needEvaluatingBandwidth().
        final WrappedNetworkMonitor wnm1 = makeCellMeteredNetworkMonitor();
        // Don't set the config_evaluating_bandwidth_url to make
        // the condition check fail in needEvaluatingBandwidth().
        assertFalse(wnm1.needEvaluatingBandwidth());
        // Make the NetworkCapabilities to have the TRANSPORT_WIFI but it still cannot meet the
        // condition check.
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI);
        setNetworkCapabilities(wnm1, nc);
        assertFalse(wnm1.needEvaluatingBandwidth());
        // Make the network to be non-metered wifi but it still cannot meet the condition check
        // since the config_evaluating_bandwidth_url is not set.
        nc.addCapability(NET_CAPABILITY_NOT_METERED);
        setNetworkCapabilities(wnm1, nc);
        assertFalse(wnm1.needEvaluatingBandwidth());
        // All configurations are set correctly.
        doReturn(TEST_SPEED_TEST_URL).when(mResources).getString(
                R.string.config_evaluating_bandwidth_url);
        final WrappedNetworkMonitor wnm2 = makeCellMeteredNetworkMonitor();
        setNetworkCapabilities(wnm2, nc);
        assertTrue(wnm2.needEvaluatingBandwidth());
        // Set mIsBandwidthCheckPassedOrIgnored to true and expect needEvaluatingBandwidth() will
        // return false.
        wnm2.mIsBandwidthCheckPassedOrIgnored = true;
        assertFalse(wnm2.needEvaluatingBandwidth());
        // Reset mIsBandwidthCheckPassedOrIgnored back to false.
        wnm2.mIsBandwidthCheckPassedOrIgnored = false;
        // Shouldn't evaluate network bandwidth on the metered wifi.
        nc.removeCapability(NET_CAPABILITY_NOT_METERED);
        setNetworkCapabilities(wnm2, nc);
        assertFalse(wnm2.needEvaluatingBandwidth());
        // Shouldn't evaluate network bandwidth on the unmetered cellular.
        nc.addCapability(NET_CAPABILITY_NOT_METERED);
        nc.removeTransportType(TRANSPORT_WIFI);
        nc.addTransportType(TRANSPORT_CELLULAR);
        assertFalse(wnm2.needEvaluatingBandwidth());
    }

    @Test
    public void testEvaluatingBandwidthState_meteredNetwork() throws Exception {
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);
        final NetworkCapabilities meteredCap = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET);
        doReturn(TEST_SPEED_TEST_URL).when(mResources).getString(
                R.string.config_evaluating_bandwidth_url);
        final NetworkMonitor nm = runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES,
                meteredCap, NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
        // Evaluating bandwidth process won't be executed when the network is metered wifi.
        // Check that the connection hasn't been opened and the state should transition to validated
        // state directly.
        verify(mCleartextDnsNetwork, never()).openConnection(new URL(TEST_SPEED_TEST_URL));
        assertEquals(NETWORK_VALIDATION_RESULT_VALID,
                nm.getEvaluationState().getEvaluationResult());
    }

    @Test
    public void testEvaluatingBandwidthState_nonMeteredNetworkWithWrongConfig() throws Exception {
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);
        final NetworkCapabilities nonMeteredCap = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_METERED);
        doReturn("").when(mResources).getString(R.string.config_evaluating_bandwidth_url);
        final NetworkMonitor nm = runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES,
                nonMeteredCap, NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
        // Non-metered network with wrong configuration(the config_evaluating_bandwidth_url is
        // empty). Check that the connection hasn't been opened and the state should transition to
        // validated state directly.
        verify(mCleartextDnsNetwork, never()).openConnection(new URL(TEST_SPEED_TEST_URL));
        assertEquals(NETWORK_VALIDATION_RESULT_VALID,
                nm.getEvaluationState().getEvaluationResult());
    }

    @Test
    public void testMatchesHttpContent() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        doReturn("[\\s\\S]*line2[\\s\\S]*").when(mResources).getString(
                R.string.config_network_validation_failed_content_regexp);
        assertTrue(wnm.matchesHttpContent("This is line1\nThis is line2\nThis is line3",
                R.string.config_network_validation_failed_content_regexp));
        assertFalse(wnm.matchesHttpContent("hello",
                R.string.config_network_validation_failed_content_regexp));
        // Set an invalid regex and expect to get the false even though the regex is the same as the
        // content.
        doReturn("[").when(mResources).getString(
                R.string.config_network_validation_failed_content_regexp);
        assertFalse(wnm.matchesHttpContent("[",
                R.string.config_network_validation_failed_content_regexp));
    }

    @Test
    public void testMatchesHttpContentLength() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        // Set the range of content length.
        doReturn(100).when(mResources).getInteger(R.integer.config_min_matches_http_content_length);
        doReturn(1000).when(mResources).getInteger(
                R.integer.config_max_matches_http_content_length);
        assertFalse(wnm.matchesHttpContentLength(100));
        assertFalse(wnm.matchesHttpContentLength(1000));
        assertTrue(wnm.matchesHttpContentLength(500));

        // Test the invalid value.
        assertFalse(wnm.matchesHttpContentLength(-1));
        assertFalse(wnm.matchesHttpContentLength(0));
        assertFalse(wnm.matchesHttpContentLength(Integer.MAX_VALUE + 1L));

        // Set the wrong value for min and max config to make sure the function is working even
        // though the config is wrong.
        doReturn(1000).when(mResources).getInteger(
                R.integer.config_min_matches_http_content_length);
        doReturn(100).when(mResources).getInteger(
                R.integer.config_max_matches_http_content_length);
        assertFalse(wnm.matchesHttpContentLength(100));
        assertFalse(wnm.matchesHttpContentLength(1000));
        assertFalse(wnm.matchesHttpContentLength(500));
    }

    @Test
    public void testGetResStringConfig() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        // Set the config and expect to get the customized value.
        final String regExp = ".*HTTP.*200.*not a captive portal.*";
        doReturn(regExp).when(mResources).getString(
                R.string.config_network_validation_failed_content_regexp);
        assertEquals(regExp, wnm.getResStringConfig(mContext,
                R.string.config_network_validation_failed_content_regexp, null));
        doThrow(new Resources.NotFoundException()).when(mResources).getString(eq(
                R.string.config_network_validation_failed_content_regexp));
        // If the config is not found, then expect to get the default value - null.
        assertNull(wnm.getResStringConfig(mContext,
                R.string.config_network_validation_failed_content_regexp, null));
    }

    @Test
    public void testGetResIntConfig() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        // Set the config and expect to get the customized value.
        doReturn(100).when(mResources).getInteger(R.integer.config_min_matches_http_content_length);
        doReturn(1000).when(mResources).getInteger(
                R.integer.config_max_matches_http_content_length);
        assertEquals(100, wnm.getResIntConfig(mContext,
                R.integer.config_min_matches_http_content_length, Integer.MAX_VALUE));
        assertEquals(1000, wnm.getResIntConfig(mContext,
                R.integer.config_max_matches_http_content_length, 0));
        doThrow(new Resources.NotFoundException())
                .when(mResources).getInteger(
                        eq(R.integer.config_min_matches_http_content_length));
        doThrow(new Resources.NotFoundException())
                .when(mResources).getInteger(eq(R.integer.config_max_matches_http_content_length));
        // If the config is not found, then expect to get the default value.
        assertEquals(Integer.MAX_VALUE, wnm.getResIntConfig(mContext,
                R.integer.config_min_matches_http_content_length, Integer.MAX_VALUE));
        assertEquals(0, wnm.getResIntConfig(mContext,
                R.integer.config_max_matches_http_content_length, 0));
    }

    @Test
    public void testGetHttpProbeUrl() {
        // If config_captive_portal_http_url is set and the global setting is set, the config is
        // used.
        doReturn(TEST_HTTP_URL).when(mResources).getString(R.string.config_captive_portal_http_url);
        doReturn(TEST_HTTP_OTHER_URL2).when(mResources).getString(
                R.string.default_captive_portal_http_url);
        doReturn(TEST_HTTP_OTHER_URL1).when(mDependencies)
                .getSetting(any(), eq(Settings.Global.CAPTIVE_PORTAL_HTTP_URL), any());
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        assertEquals(TEST_HTTP_URL, wnm.getCaptivePortalServerHttpUrl(mContext));
        // If config_captive_portal_http_url is unset and the global setting is set, the global
        // setting is used.
        doReturn(null).when(mResources).getString(R.string.config_captive_portal_http_url);
        assertEquals(TEST_HTTP_OTHER_URL1, wnm.getCaptivePortalServerHttpUrl(mContext));
        // If both config_captive_portal_http_url and global setting are unset,
        // default_captive_portal_http_url is used. But the global setting will only be read in the
        // constructor.
        doReturn(null).when(mDependencies)
                .getSetting(any(), eq(Settings.Global.CAPTIVE_PORTAL_HTTP_URL), any());
        assertEquals(TEST_HTTP_OTHER_URL1, wnm.getCaptivePortalServerHttpUrl(mContext));
        // default_captive_portal_http_url is used when the configuration is applied in new NM.
        final WrappedNetworkMonitor wnm2 = makeCellNotMeteredNetworkMonitor();
        assertEquals(TEST_HTTP_OTHER_URL2, wnm2.getCaptivePortalServerHttpUrl(mContext));
    }

    @Test
    public void testGetLocationMcc() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkPermission(
                eq(android.Manifest.permission.ACCESS_FINE_LOCATION),  anyInt(), anyInt());
        assertNull(wnm.getLocationMcc());
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkPermission(
                eq(android.Manifest.permission.ACCESS_FINE_LOCATION),  anyInt(), anyInt());
        doReturn(new ContextWrapper(mContext)).when(mContext).createConfigurationContext(any());
        doReturn(null).when(mTelephony).getAllCellInfo();
        assertNull(wnm.getLocationMcc());
        // Prepare CellInfo and check if the vote mechanism is working or not.
        final List<CellInfo> cellList = new ArrayList<CellInfo>();
        doReturn(cellList).when(mTelephony).getAllCellInfo();
        assertNull(wnm.getLocationMcc());
        cellList.add(makeTestCellInfoGsm("460"));
        cellList.add(makeTestCellInfoGsm("460"));
        cellList.add(makeTestCellInfoLte("466"));
        // The count of 460 is 2 and the count of 466 is 1, so the getLocationMcc() should return
        // 460.
        assertEquals("460", wnm.getLocationMcc());
        // getCustomizedContextOrDefault() shouldn't return mContext when using neighbor mcc
        // is enabled and the sim is not ready.
        doReturn(true).when(mResources).getBoolean(R.bool.config_no_sim_card_uses_neighbor_mcc);
        doReturn(TelephonyManager.SIM_STATE_ABSENT).when(mTelephony).getSimState();
        assertEquals(460,
                wnm.getCustomizedContextOrDefault().getResources().getConfiguration().mcc);
        doReturn(false).when(mResources).getBoolean(R.bool.config_no_sim_card_uses_neighbor_mcc);
        assertEquals(wnm.getContext(), wnm.getCustomizedContextOrDefault());
    }

    private void checkCustomizedContextByCarrierId(WrappedNetworkMonitor wnm, int carrierId) {
        doReturn(carrierId).when(mTelephony).getSimCarrierId();
        assertNotNull(wnm.getMccMncOverrideInfo());
        // Check if the mcc & mnc has changed as expected.
        assertEquals(460,
                wnm.getCustomizedContextOrDefault().getResources().getConfiguration().mcc);
        assertEquals(03,
                wnm.getCustomizedContextOrDefault().getResources().getConfiguration().mnc);
    }

    @Test
    public void testGetMccMncOverrideInfo() {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        doReturn(new ContextWrapper(mContext)).when(mContext).createConfigurationContext(any());
        // 1839 is VZW's carrier id.
        doReturn(1839).when(mTelephony).getSimCarrierId();
        assertNull(wnm.getMccMncOverrideInfo());
        // 1854 is CTC's carrier id.
        // This line should be removed when the production code is corrected.
        checkCustomizedContextByCarrierId(wnm, 1854);
        // 2237 is CT's carrier id.
        checkCustomizedContextByCarrierId(wnm, 2237);
        // Every mcc and mnc should be set in sCarrierIdToMccMnc.
        // Check if there is any unset value in mcc or mnc.
        for (int i = 0; i < wnm.sCarrierIdToMccMnc.size(); i++) {
            assertNotEquals(-1, wnm.sCarrierIdToMccMnc.valueAt(i).mcc);
            assertNotEquals(-1, wnm.sCarrierIdToMccMnc.valueAt(i).mnc);
        }
    }

    @Test
    public void testConfigurationChange_BeforeNMConnected() throws Exception {
        final WrappedNetworkMonitor nm = new WrappedNetworkMonitor();
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        // Verify configuration change receiver is registered after start().
        verify(mContext, never()).registerReceiver(receiverCaptor.capture(),
                argThat(receiver -> ACTION_CONFIGURATION_CHANGED.equals(receiver.getAction(0))));
        nm.start();
        synchronized (mCreatedNetworkMonitors) {
            mCreatedNetworkMonitors.add(nm);
        }
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
        verify(mContext, times(1)).registerReceiver(receiverCaptor.capture(),
                argThat(receiver -> ACTION_CONFIGURATION_CHANGED.equals(receiver.getAction(0))));
        // Update a new URL and send a configuration change
        doReturn(TEST_HTTPS_OTHER_URL1).when(mResources).getString(
                R.string.config_captive_portal_https_url);
        receiverCaptor.getValue().onReceive(mContext, new Intent(ACTION_CONFIGURATION_CHANGED));
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
        // Should stay in default state before receiving CMD_NETWORK_CONNECTED
        verify(mOtherHttpsConnection1, never()).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_ConfigurationChange_RenewUrls() throws Exception {
        setStatus(mHttpsConnection, 204);
        final NetworkMonitor nm = runValidatedNetworkTest();
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext, times(1)).registerReceiver(receiverCaptor.capture(),
                argThat(receiver -> ACTION_CONFIGURATION_CHANGED.equals(receiver.getAction(0))));

        // New URLs with partial connectivity
        doReturn(TEST_HTTPS_OTHER_URL1).when(mResources).getString(
                R.string.config_captive_portal_https_url);
        doReturn(TEST_HTTP_OTHER_URL1).when(mResources).getString(
                R.string.config_captive_portal_http_url);
        setStatus(mOtherHttpsConnection1, 500);
        setStatus(mOtherHttpConnection1, 204);

        // Receive configuration. Expect a reevaluation triggered.
        receiverCaptor.getValue().onReceive(mContext, new Intent(ACTION_CONFIGURATION_CHANGED));

        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
        verifyNetworkTested(NETWORK_VALIDATION_RESULT_PARTIAL,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                1 /* interactions */);
        verify(mOtherHttpsConnection1, times(1)).getResponseCode();
        verify(mOtherHttpConnection1, times(1)).getResponseCode();
    }

    private CellInfoGsm makeTestCellInfoGsm(String mcc) throws Exception {
        final CellInfoGsm info = new CellInfoGsm();
        final CellIdentityGsm ci = makeCellIdentityGsm(0, 0, 0, 0, mcc, "01", "", "");
        info.setCellIdentity(ci);
        return info;
    }

    private CellInfoLte makeTestCellInfoLte(String mcc) throws Exception {
        final CellInfoLte info = new CellInfoLte();
        final CellIdentityLte ci = makeCellIdentityLte(0, 0, 0, 0, 0, mcc, "01", "", "");
        info.setCellIdentity(ci);
        return info;
    }

    private void setupNoSimCardNeighborMcc() throws Exception {
        // Enable using neighbor resource by camping mcc feature.
        doReturn(true).when(mResources).getBoolean(R.bool.config_no_sim_card_uses_neighbor_mcc);
        final List<CellInfo> cellList = new ArrayList<CellInfo>();
        final int testMcc = 460;
        cellList.add(makeTestCellInfoGsm(Integer.toString(testMcc)));
        doReturn(cellList).when(mTelephony).getAllCellInfo();
        final Configuration config = mResources.getConfiguration();
        config.mcc = testMcc;
        doReturn(mMccContext).when(mContext).createConfigurationContext(eq(config));
    }

    @Test
    public void testMakeFallbackUrls() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        // Value exist in setting provider.
        URL[] urls = wnm.makeCaptivePortalFallbackUrls(mContext);
        assertEquals(urls[0].toString(), TEST_FALLBACK_URL);

        // Clear setting provider value. Verify it to get configuration from resource instead.
        setFallbackUrl(null);
        // Verify that getting resource with exception.
        doThrow(Resources.NotFoundException.class).when(mResources)
                .getStringArray(R.array.config_captive_portal_fallback_urls);
        urls = wnm.makeCaptivePortalFallbackUrls(mContext);
        assertEquals(urls.length, 0);

        // Verify resource return 2 different URLs.
        doReturn(new String[] {"http://testUrl1.com", "http://testUrl2.com"}).when(mResources)
                .getStringArray(R.array.config_captive_portal_fallback_urls);
        urls = wnm.makeCaptivePortalFallbackUrls(mContext);
        assertEquals(urls.length, 2);
        assertEquals("http://testUrl1.com", urls[0].toString());
        assertEquals("http://testUrl2.com", urls[1].toString());

        // Even though the using neighbor resource by camping mcc feature is enabled, the
        // customized context has been assigned and won't change. So calling
        // makeCaptivePortalFallbackUrls() still gets the original value.
        setupNoSimCardNeighborMcc();
        doReturn(new String[] {"http://testUrl3.com"}).when(mMccResource)
                .getStringArray(R.array.config_captive_portal_fallback_urls);
        urls = wnm.makeCaptivePortalFallbackUrls(mContext);
        assertEquals(urls.length, 2);
        assertEquals("http://testUrl1.com", urls[0].toString());
        assertEquals("http://testUrl2.com", urls[1].toString());
    }

    @Test
    public void testMakeFallbackUrlsWithCustomizedContext() throws Exception {
        // Value is expected to be replaced by location resource.
        setupNoSimCardNeighborMcc();
        doReturn(new String[] {"http://testUrl.com"}).when(mMccResource)
                .getStringArray(R.array.config_captive_portal_fallback_urls);
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        final URL[] urls = wnm.makeCaptivePortalFallbackUrls(mMccContext);
        assertEquals(urls.length, 1);
        assertEquals("http://testUrl.com", urls[0].toString());
    }

    private static CellIdentityGsm makeCellIdentityGsm(int lac, int cid, int arfcn, int bsic,
            String mccStr, String mncStr, String alphal, String alphas)
            throws ReflectiveOperationException {
        if (ShimUtils.isAtLeastR()) {
            return new CellIdentityGsm(lac, cid, arfcn, bsic, mccStr, mncStr, alphal, alphas,
                    Collections.emptyList() /* additionalPlmns */);
        } else {
            // API <= Q does not have the additionalPlmns parameter
            final Constructor<CellIdentityGsm> constructor = CellIdentityGsm.class.getConstructor(
                    int.class, int.class, int.class, int.class, String.class, String.class,
                    String.class, String.class);
            return constructor.newInstance(lac, cid, arfcn, bsic, mccStr, mncStr, alphal, alphas);
        }
    }

    private static CellIdentityLte makeCellIdentityLte(int ci, int pci, int tac, int earfcn,
            int bandwidth, String mccStr, String mncStr, String alphal, String alphas)
            throws ReflectiveOperationException {
        if (ShimUtils.isAtLeastR()) {
            return new CellIdentityLte(ci, pci, tac, earfcn, new int[] {} /* bands */,
                    bandwidth, mccStr, mncStr, alphal, alphas,
                    Collections.emptyList() /* additionalPlmns */, null /* csgInfo */);
        } else {
            // API <= Q does not have the additionalPlmns and csgInfo parameters
            final Constructor<CellIdentityLte> constructor = CellIdentityLte.class.getConstructor(
                    int.class, int.class, int.class, int.class, int.class, String.class,
                    String.class, String.class, String.class);
            return constructor.newInstance(ci, pci, tac, earfcn, bandwidth, mccStr, mncStr, alphal,
                    alphas);
        }
    }

    @Test
    public void testGetIntSetting() throws Exception {
        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();

        // No config resource, no device config. Expect to get default resource.
        doThrow(new Resources.NotFoundException())
                .when(mResources).getInteger(eq(R.integer.config_captive_portal_dns_probe_timeout));
        doAnswer(invocation -> {
            int defaultValue = invocation.getArgument(2);
            return defaultValue;
        }).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(NetworkMonitor.CONFIG_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT),
                anyInt());
        assertEquals(DEFAULT_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT, wnm.getIntSetting(mContext,
                R.integer.config_captive_portal_dns_probe_timeout,
                NetworkMonitor.CONFIG_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT,
                DEFAULT_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT));

        // Set device config. Expect to get device config.
        doReturn(1234).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(NetworkMonitor.CONFIG_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT), anyInt());
        assertEquals(1234, wnm.getIntSetting(mContext,
                R.integer.config_captive_portal_dns_probe_timeout,
                NetworkMonitor.CONFIG_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT,
                DEFAULT_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT));

        // Set config resource. Expect to get config resource.
        doReturn(5678).when(mResources)
                        .getInteger(eq(R.integer.config_captive_portal_dns_probe_timeout));
        assertEquals(5678, wnm.getIntSetting(mContext,
                R.integer.config_captive_portal_dns_probe_timeout,
                NetworkMonitor.CONFIG_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT,
                DEFAULT_CAPTIVE_PORTAL_DNS_PROBE_TIMEOUT));
    }

    @Test
    public void testIsCaptivePortal_HttpProbeIsPortal() throws Exception {
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        runPortalNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_Http200EmptyResponse() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 200);
        // Invalid if there is no content (can't login to an empty page)
        runNetworkTest(VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */, null);
        verify(mCallbacks, never()).showProvisioningNotification(any(), any());
    }

    private void doCaptivePortal200ResponseTest(String expectedRedirectUrl) throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 200);
        doReturn(100L).when(mHttpConnection).getContentLengthLong();
        // Redirect URL was null before S
        runNetworkTest(VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded */, expectedRedirectUrl);
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS)).showProvisioningNotification(any(), any());
    }

    @Test @IgnoreAfter(Build.VERSION_CODES.R)
    public void testIsCaptivePortal_HttpProbeIs200Portal_R() throws Exception {
        doCaptivePortal200ResponseTest(null);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testIsCaptivePortal_HttpProbeIs200Portal() throws Exception {
        doCaptivePortal200ResponseTest(TEST_HTTP_URL);
    }

    private void setupPrivateIpResponse(String privateAddr) throws Exception {
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        final String httpHost = new URL(TEST_HTTP_URL).getHost();
        mFakeDns.setAnswer(httpHost, new String[] { "2001:db8::123" }, TYPE_AAAA);
        final InetAddress parsedPrivateAddr = parseNumericAddress(privateAddr);
        mFakeDns.setAnswer(httpHost, new String[] { privateAddr },
                (parsedPrivateAddr instanceof Inet6Address) ? TYPE_AAAA : TYPE_A);
    }

    @Test
    public void testIsCaptivePortal_PrivateIpNotPortal_Enabled_IPv4() throws Exception {
        doReturn(true).when(mDependencies)
                .isFeatureEnabled(any(), eq(DNS_PROBE_PRIVATE_IP_NO_INTERNET_VERSION));
        setupPrivateIpResponse("192.168.1.1");
        runFailedNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_PrivateIpNotPortal_Enabled_IPv6() throws Exception {
        doReturn(true).when(mDependencies)
                .isFeatureEnabled(any(), eq(DNS_PROBE_PRIVATE_IP_NO_INTERNET_VERSION));
        setupPrivateIpResponse("fec0:1234::1");
        runFailedNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_PrivateIpNotPortal_Disabled() throws Exception {
        setupPrivateIpResponse("192.168.1.1");
        runPortalNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_HttpsProbeIsNotPortal() throws Exception {
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 500);

        runValidatedNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_FallbackProbeIsPortal() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setPortal302(mFallbackConnection);
        runPortalNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_HttpSucceedFallbackProbeIsPortal() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 204);
        setPortal302(mFallbackConnection);
        runPortalNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_FallbackProbeIsNotPortal() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 500);

        // Fallback probe did not see portal, HTTPS failed -> inconclusive
        runFailedNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_OtherFallbackProbeIsPortal() throws Exception {
        // Set all fallback probes but one to invalid URLs to verify they are being skipped
        setFallbackUrl(TEST_FALLBACK_URL);
        setOtherFallbackUrls(TEST_FALLBACK_URL + "," + TEST_OTHER_FALLBACK_URL);

        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 500);
        setPortal302(mOtherFallbackConnection);

        // TEST_OTHER_FALLBACK_URL is third
        doReturn(2).when(mRandom).nextInt();

        // First check always uses the first fallback URL: inconclusive
        final NetworkMonitor monitor = runFailedNetworkTest();
        verify(mFallbackConnection, times(1)).getResponseCode();
        verify(mOtherFallbackConnection, never()).getResponseCode();

        // Second check should be triggered automatically after the reevaluate delay, and uses the
        // URL chosen by mRandom
        // Ensure that the reevaluate delay is not changed to a large value, otherwise this test
        // would block for too long and a different test strategy should be used.
        assertTrue(INITIAL_REEVALUATE_DELAY_MS < 2000);
        verify(mOtherFallbackConnection, timeout(INITIAL_REEVALUATE_DELAY_MS + HANDLER_TIMEOUT_MS))
                .getResponseCode();
        verifyNetworkTestedPortal(TEST_LOGIN_URL, 1 /* interactions */);
    }

    @Test
    public void testIsCaptivePortal_AllProbesFailed() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 404);

        runFailedNetworkTest();
        verify(mFallbackConnection, times(1)).getResponseCode();
        verify(mOtherFallbackConnection, never()).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_InvalidUrlSkipped() throws Exception {
        setFallbackUrl("invalid");
        setOtherFallbackUrls("otherinvalid," + TEST_OTHER_FALLBACK_URL + ",yetanotherinvalid");

        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setPortal302(mOtherFallbackConnection);
        runPortalNetworkTest();
        verify(mOtherFallbackConnection, times(1)).getResponseCode();
        verify(mFallbackConnection, never()).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_CapportApiIsPortalWithNullPortalUrl() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        final long bytesRemaining = 10_000L;
        final long secondsRemaining = 500L;
        // Set content without partal url.
        setApiContent(mCapportApiConnection, "{'captive': true,"
                + "'venue-info-url': '" + TEST_VENUE_INFO_URL + "',"
                + "'bytes-remaining': " + bytesRemaining + ","
                + "'seconds-remaining': " + secondsRemaining + "}");
        setPortal302(mHttpConnection);

        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded*/, TEST_LOGIN_URL);

        verify(mCapportApiConnection).getResponseCode();

        verify(mHttpConnection, times(1)).getResponseCode();
        verify(mCallbacks, never()).notifyCaptivePortalDataChanged(any());
    }

    @Test
    public void testIsCaptivePortal_CapportApiIsPortalWithValidPortalUrl() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        final long bytesRemaining = 10_000L;
        final long secondsRemaining = 500L;

        setApiContent(mCapportApiConnection, "{'captive': true,"
                + "'user-portal-url': '" + TEST_LOGIN_URL + "',"
                + "'venue-info-url': '" + TEST_VENUE_INFO_URL + "',"
                + "'bytes-remaining': " + bytesRemaining + ","
                + "'seconds-remaining': " + secondsRemaining + "}");

        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded*/, TEST_LOGIN_URL);

        verify(mHttpConnection, never()).getResponseCode();
        verify(mCapportApiConnection).getResponseCode();

        final ArgumentCaptor<CaptivePortalData> capportDataCaptor =
                ArgumentCaptor.forClass(CaptivePortalData.class);
        verify(mCallbacks).notifyCaptivePortalDataChanged(capportDataCaptor.capture());
        final CaptivePortalData p = capportDataCaptor.getValue();
        assertTrue(p.isCaptive());
        assertEquals(Uri.parse(TEST_LOGIN_URL), p.getUserPortalUrl());
        assertEquals(Uri.parse(TEST_VENUE_INFO_URL), p.getVenueInfoUrl());
        assertEquals(bytesRemaining, p.getByteLimit());
        final long expectedExpiry = currentTimeMillis() + secondsRemaining * 1000;
        // Actual expiry will be slightly lower as some time as passed
        assertTrue(p.getExpiryTimeMillis() <= expectedExpiry);
        assertTrue(p.getExpiryTimeMillis() > expectedExpiry - 30_000);
    }

    @Test
    public void testIsCaptivePortal_CapportApiRevalidation() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setValidProbes();
        final NetworkMonitor nm = runValidatedNetworkTest();

        setApiContent(mCapportApiConnection, "{'captive': true, "
                + "'user-portal-url': '" + TEST_LOGIN_URL + "'}");
        nm.notifyLinkPropertiesChanged(makeCapportLPs());

        verifyNetworkTestedPortal(TEST_LOGIN_URL, 1 /* interactions */);
        final ArgumentCaptor<CaptivePortalData> capportCaptor = ArgumentCaptor.forClass(
                CaptivePortalData.class);
        verify(mCallbacks).notifyCaptivePortalDataChanged(capportCaptor.capture());
        assertEquals(Uri.parse(TEST_LOGIN_URL), capportCaptor.getValue().getUserPortalUrl());

        // HTTP probe was sent on first validation but not re-sent when there was a portal URL.
        verify(mHttpConnection, times(1)).getResponseCode();
        verify(mCapportApiConnection, times(1)).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_NoRevalidationBeforeNetworkConnected() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());

        final NetworkMonitor nm = makeCellMeteredNetworkMonitor();

        final LinkProperties lp = makeCapportLPs();

        // LinkProperties changed, but NM should not revalidate before notifyNetworkConnected
        nm.notifyLinkPropertiesChanged(lp);
        verify(mHttpConnection, after(100).never()).getResponseCode();
        verify(mHttpsConnection, never()).getResponseCode();
        verify(mCapportApiConnection, never()).getResponseCode();

        setValidProbes();
        setApiContent(mCapportApiConnection, "{'captive': true, "
                + "'user-portal-url': '" + TEST_LOGIN_URL + "'}");

        // After notifyNetworkConnected, validation uses the capport API contents
        notifyNetworkConnected(nm, lp, CELL_METERED_CAPABILITIES);
        verifyNetworkTestedPortal(TEST_LOGIN_URL, 1 /* interactions */);

        verify(mHttpConnection, never()).getResponseCode();
        verify(mCapportApiConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_CapportApiNotPortalNotValidated() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setApiContent(mCapportApiConnection, "{'captive': false,"
                + "'venue-info-url': '" + TEST_VENUE_INFO_URL + "'}");
        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */, null /* redirectUrl */);

        final ArgumentCaptor<CaptivePortalData> capportCaptor = ArgumentCaptor.forClass(
                CaptivePortalData.class);
        verify(mCallbacks).notifyCaptivePortalDataChanged(capportCaptor.capture());
        assertEquals(Uri.parse(TEST_VENUE_INFO_URL), capportCaptor.getValue().getVenueInfoUrl());
    }

    @Test
    public void testIsCaptivePortal_CapportApiNotPortalPartial() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 204);
        setApiContent(mCapportApiConnection, "{'captive': false,"
                + "'venue-info-url': '" + TEST_VENUE_INFO_URL + "'}");
        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                NETWORK_VALIDATION_RESULT_PARTIAL,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                null /* redirectUrl */);

        final ArgumentCaptor<CaptivePortalData> capportCaptor = ArgumentCaptor.forClass(
                CaptivePortalData.class);
        verify(mCallbacks).notifyCaptivePortalDataChanged(capportCaptor.capture());
        assertEquals(Uri.parse(TEST_VENUE_INFO_URL), capportCaptor.getValue().getVenueInfoUrl());
    }

    @Test
    public void testIsCaptivePortal_CapportApiNotPortalValidated() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);
        setApiContent(mCapportApiConnection, "{'captive': false,"
                + "'venue-info-url': '" + TEST_VENUE_INFO_URL + "'}");
        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP
                        | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);

        final ArgumentCaptor<CaptivePortalData> capportCaptor = ArgumentCaptor.forClass(
                CaptivePortalData.class);
        verify(mCallbacks).notifyCaptivePortalDataChanged(capportCaptor.capture());
        assertEquals(Uri.parse(TEST_VENUE_INFO_URL), capportCaptor.getValue().getVenueInfoUrl());
    }

    @Test
    public void testIsCaptivePortal_CapportApiInvalidContent() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        setApiContent(mCapportApiConnection, "{SomeInvalidText");
        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded */,
                TEST_LOGIN_URL);

        verify(mCallbacks, never()).notifyCaptivePortalDataChanged(any());
        verify(mHttpConnection).getResponseCode();
    }

    private void runCapportApiInvalidUrlTest(String url) throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        final LinkProperties lp = new LinkProperties(TEST_LINK_PROPERTIES);
        lp.setCaptivePortalApiUrl(Uri.parse(url));
        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded */,
                TEST_LOGIN_URL);

        verify(mCallbacks, never()).notifyCaptivePortalDataChanged(any());
        verify(mCapportApiConnection, never()).getInputStream();
        verify(mHttpConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_HttpIsInvalidCapportApiScheme() throws Exception {
        runCapportApiInvalidUrlTest("http://capport.example.com");
    }

    @Test
    public void testIsCaptivePortal_FileIsInvalidCapportApiScheme() throws Exception {
        runCapportApiInvalidUrlTest("file://localhost/myfile");
    }

    @Test
    public void testIsCaptivePortal_InvalidUrlFormat() throws Exception {
        runCapportApiInvalidUrlTest("ThisIsNotAValidUrl");
    }

    @Test @IgnoreUpTo(S_V2)
    public void testVpnReevaluationWhenUnderlyingNetworkChange() throws Exception {
        // Skip this test if the test is built against SDK < T
        assumeTrue(ConstantsShim.VERSION > S_V2);
        // Start a VPN network
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);
        final NetworkAgentConfigShim config = NetworkAgentConfigShimImpl.newInstance(
                new NetworkAgentConfig.Builder().setVpnRequiresValidation(true).build());
        final NetworkMonitor nm = runNetworkTest(config, TEST_LINK_PROPERTIES, nc,
                NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS, null);

        // Underlying network changed.
        notifyUnderlyingNetworkChange(nm, nc , List.of(new Network(TEST_NETID)));
        // The underlying network change should cause a re-validation
        verifyNetworkTestedValidFromHttps(1 /* interactions */);

        notifyUnderlyingNetworkChange(nm, nc , List.of(new Network(TEST_NETID)));
        // Identical networks should not cause revalidation. The interaction stays in 1 time which
        // is verified in runNetworkTest.
        verifyNetworkTestedValidFromHttps(1 /* interactions */);

        // Change to another network
        notifyUnderlyingNetworkChange(nm, nc , List.of(new Network(TEST_NETID2)));
        verifyNetworkTestedValidFromHttps(2 /* interactions */);
    }

    private void notifyUnderlyingNetworkChange(NetworkMonitor nm, NetworkCapabilities nc,
            List<Network> underlyingNetworks) {
        final NetworkCapabilities newNc = new NetworkCapabilities.Builder(nc)
                .setUnderlyingNetworks(underlyingNetworks).build();
        nm.notifyNetworkCapabilitiesChanged(newNc);
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
    }

    @Test
    public void testIsCaptivePortal_CapportApiNotSupported() throws Exception {
        // Test that on a R+ device, if NetworkStack was compiled without CaptivePortalData support
        // (built against Q), NetworkMonitor behaves as expected.
        assumeFalse(CaptivePortalDataShimImpl.isSupported());
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        setApiContent(mCapportApiConnection, "{'captive': false,"
                + "'venue-info-url': '" + TEST_VENUE_INFO_URL + "'}");
        runNetworkTest(TEST_AGENT_CONFIG, makeCapportLPs(), CELL_METERED_CAPABILITIES,
                VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded */, TEST_LOGIN_URL);

        verify(mCallbacks, never()).notifyCaptivePortalDataChanged(any());
        verify(mHttpConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_HttpsProbeMatchesFailRegex() throws Exception {
        setStatus(mHttpsConnection, 200);
        setStatus(mHttpConnection, 500);
        final String content = "test";
        doReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .when(mHttpsConnection).getInputStream();
        doReturn(Long.valueOf(content.length())).when(mHttpsConnection).getContentLengthLong();
        doReturn(1).when(mResources).getInteger(R.integer.config_min_matches_http_content_length);
        doReturn(10).when(mResources).getInteger(
                R.integer.config_max_matches_http_content_length);
        doReturn("te.t").when(mResources).getString(
                R.string.config_network_validation_failed_content_regexp);
        runFailedNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_HttpProbeMatchesSuccessRegex() throws Exception {
        setStatus(mHttpsConnection, 500);
        setStatus(mHttpConnection, 200);
        final String content = "test";
        doReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .when(mHttpConnection).getInputStream();
        doReturn(Long.valueOf(content.length())).when(mHttpConnection).getContentLengthLong();
        doReturn(1).when(mResources).getInteger(R.integer.config_min_matches_http_content_length);
        doReturn(10).when(mResources).getInteger(
                R.integer.config_max_matches_http_content_length);
        doReturn("te.t").when(mResources).getString(
                R.string.config_network_validation_success_content_regexp);
        runPartialConnectivityNetworkTest(
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP);
    }

    private void setupFallbackSpec() throws IOException {
        setFallbackSpecs("http://example.com@@/@@204@@/@@"
                + "@@,@@"
                + TEST_OTHER_FALLBACK_URL + "@@/@@30[12]@@/@@https://(www\\.)?google.com/?.*");

        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);

        // Use the 2nd fallback spec
        doReturn(1).when(mRandom).nextInt();
    }

    @Test
    public void testIsCaptivePortal_FallbackSpecIsFail() throws Exception {
        setupFallbackSpec();
        set302(mOtherFallbackConnection, "https://www.google.com/test?q=3");

        runNetworkTest(VALIDATION_RESULT_INVALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_FALLBACK,
                null /* redirectUrl */);
        verify(mOtherFallbackConnection, times(1)).getResponseCode();
        verify(mFallbackConnection, never()).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_FallbackSpecIsPortal() throws Exception {
        setupFallbackSpec();
        setPortal302(mOtherFallbackConnection);
        runPortalNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_IgnorePortals() throws Exception {
        setCaptivePortalMode(Settings.Global.CAPTIVE_PORTAL_MODE_IGNORE);
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);

        runNoValidationNetworkTest();
    }

    @Test
    public void testIsCaptivePortal_OverriddenHttpsUrlValid() throws Exception {
        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() + TimeUnit.MINUTES.toMillis(9)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTPS_URL, TEST_OVERRIDE_URL);
        setStatus(mTestOverriddenUrlConnection, 204);
        setStatus(mHttpConnection, 204);

        runValidatedNetworkTest();
        verify(mHttpsConnection, never()).getResponseCode();
        verify(mTestOverriddenUrlConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_OverriddenHttpUrlPortal() throws Exception {
        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() + TimeUnit.MINUTES.toMillis(9)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTP_URL, TEST_OVERRIDE_URL);
        setStatus(mHttpsConnection, 500);
        setPortal302(mTestOverriddenUrlConnection);

        runPortalNetworkTest();
        verify(mHttpConnection, never()).getResponseCode();
        verify(mTestOverriddenUrlConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_InvalidHttpOverrideUrl() throws Exception {
        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() + TimeUnit.MINUTES.toMillis(9)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTP_URL, TEST_INVALID_OVERRIDE_URL);
        setStatus(mHttpsConnection, 500);
        setPortal302(mHttpConnection);

        runPortalNetworkTest();
        verify(mTestOverriddenUrlConnection, never()).getResponseCode();
        verify(mHttpConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_InvalidHttpsOverrideUrl() throws Exception {
        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() + TimeUnit.MINUTES.toMillis(9)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTPS_URL, TEST_INVALID_OVERRIDE_URL);
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        runValidatedNetworkTest();
        verify(mTestOverriddenUrlConnection, never()).getResponseCode();
        verify(mHttpsConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_ExpiredHttpsOverrideUrl() throws Exception {
        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTPS_URL, TEST_OVERRIDE_URL);
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        runValidatedNetworkTest();
        verify(mTestOverriddenUrlConnection, never()).getResponseCode();
        verify(mHttpsConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_TestHttpUrlExpirationTooLarge() throws Exception {
        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() + TimeUnit.MINUTES.toMillis(20)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTP_URL, TEST_OVERRIDE_URL);
        setStatus(mHttpsConnection, 500);
        setPortal302(mHttpConnection);

        runPortalNetworkTest();
        verify(mTestOverriddenUrlConnection, never()).getResponseCode();
        verify(mHttpConnection).getResponseCode();
    }

    @Test
    public void testIsCaptivePortal_TestUrlsWithUrlOverlays() throws Exception {
        setupResourceForMultipleProbes();
        doReturn(TEST_HTTPS_URL).when(mResources)
                .getString(R.string.config_captive_portal_https_url);
        doReturn(TEST_HTTP_URL).when(mResources)
                .getString(R.string.config_captive_portal_http_url);

        setDeviceConfig(TEST_URL_EXPIRATION_TIME,
                String.valueOf(currentTimeMillis() + TimeUnit.MINUTES.toMillis(9)));
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTPS_URL, TEST_OVERRIDE_URL);
        setDeviceConfig(TEST_CAPTIVE_PORTAL_HTTP_URL, TEST_OVERRIDE_URL);
        setStatus(mTestOverriddenUrlConnection, 204);

        runValidatedNetworkTest();
        verify(mHttpsConnection, never()).getResponseCode();
        verify(mHttpConnection, never()).getResponseCode();
        verify(mOtherHttpsConnection1, never()).getResponseCode();
        verify(mOtherHttpsConnection2, never()).getResponseCode();
        verify(mOtherHttpConnection1, never()).getResponseCode();
        verify(mOtherHttpConnection2, never()).getResponseCode();

        // Used for both HTTP and HTTPS: can be called once (if HTTPS validates first) or twice
        verify(mTestOverriddenUrlConnection, atLeastOnce()).getResponseCode();
    }

    @Test
    public void testIsDataStall_EvaluationDisabled() {
        setDataStallEvaluationType(0);
        WrappedNetworkMonitor wrappedMonitor = makeCellMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(SystemClock.elapsedRealtime() - 100);
        assertFalse(wrappedMonitor.isDataStall());
    }

    @Test
    public void testIsDataStall_EvaluationDisabledOnIgnorePortal() {
        setCaptivePortalMode(Settings.Global.CAPTIVE_PORTAL_MODE_IGNORE);
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_DNS);
        final WrappedNetworkMonitor nm = makeCellMeteredNetworkMonitor();
        nm.setLastProbeTime(SystemClock.elapsedRealtime() - 1000);

        assertNull(nm.getDnsStallDetector());
        assertFalse(nm.isDataStall());
    }

    @Test
    public void testIsDataStall_EvaluationDnsOnNotMeteredNetwork() throws Exception {
        WrappedNetworkMonitor wrappedMonitor = makeCellNotMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(SystemClock.elapsedRealtime() - 100);
        makeDnsTimeoutEvent(wrappedMonitor, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        assertTrue(wrappedMonitor.isDataStall());
        verify(mCallbacks).notifyDataStallSuspected(
                matchDnsDataStallParcelable(DEFAULT_DNS_TIMEOUT_THRESHOLD));
    }

    @Test
    public void testIsDataStall_EvaluationDnsOnMeteredNetwork() throws Exception {
        WrappedNetworkMonitor wrappedMonitor = makeCellMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(SystemClock.elapsedRealtime() - 100);
        assertFalse(wrappedMonitor.isDataStall());

        wrappedMonitor.setLastProbeTime(
                SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        makeDnsTimeoutEvent(wrappedMonitor, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        assertTrue(wrappedMonitor.isDataStall());
        verify(mCallbacks).notifyDataStallSuspected(
                matchDnsDataStallParcelable(DEFAULT_DNS_TIMEOUT_THRESHOLD));
    }

    @Test
    public void testIsDataStall_EvaluationDnsWithDnsTimeoutCount() throws Exception {
        WrappedNetworkMonitor wrappedMonitor = makeCellMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(
                SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        makeDnsTimeoutEvent(wrappedMonitor, 3);
        assertFalse(wrappedMonitor.isDataStall());
        // Reset consecutive timeout counts.
        makeDnsSuccessEvent(wrappedMonitor, 1);
        makeDnsTimeoutEvent(wrappedMonitor, 2);
        assertFalse(wrappedMonitor.isDataStall());

        makeDnsTimeoutEvent(wrappedMonitor, 3);
        assertTrue(wrappedMonitor.isDataStall());

        // The expected timeout count is the previous 2 DNS timeouts + the most recent 3 timeouts
        verify(mCallbacks).notifyDataStallSuspected(
                matchDnsDataStallParcelable(5 /* timeoutCount */));

        // Set the value to larger than the default dns log size.
        setConsecutiveDnsTimeoutThreshold(51);
        wrappedMonitor = makeCellMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(
                SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        makeDnsTimeoutEvent(wrappedMonitor, 50);
        assertFalse(wrappedMonitor.isDataStall());

        makeDnsTimeoutEvent(wrappedMonitor, 1);
        assertTrue(wrappedMonitor.isDataStall());

        // The expected timeout count is the previous 50 DNS timeouts + the most recent timeout
        verify(mCallbacks).notifyDataStallSuspected(
                matchDnsDataStallParcelable(51 /* timeoutCount */));
    }

    @Test
    public void testIsDataStall_SkipEvaluateOnValidationNotRequiredNetwork() {
        // Make DNS and TCP stall condition satisfied.
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_DNS | DATA_STALL_EVALUATION_TYPE_TCP);
        doReturn(0).when(mTst).getLatestReceivedCount();
        doReturn(true).when(mTst).isDataStallSuspected();
        final WrappedNetworkMonitor nm = makeMonitor(CELL_NO_INTERNET_CAPABILITIES);
        nm.setLastProbeTime(SystemClock.elapsedRealtime() - 1000);
        makeDnsTimeoutEvent(nm, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        assertFalse(nm.isDataStall());
    }

    @Test
    public void testIsDataStall_EvaluationDnsWithDnsTimeThreshold() throws Exception {
        // Test dns events happened in valid dns time threshold.
        WrappedNetworkMonitor wrappedMonitor = makeCellMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(SystemClock.elapsedRealtime() - 100);
        makeDnsTimeoutEvent(wrappedMonitor, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        assertFalse(wrappedMonitor.isDataStall());
        wrappedMonitor.setLastProbeTime(
                SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        assertTrue(wrappedMonitor.isDataStall());
        verify(mCallbacks).notifyDataStallSuspected(
                matchDnsDataStallParcelable(DEFAULT_DNS_TIMEOUT_THRESHOLD));

        // Test dns events happened before valid dns time threshold.
        setValidDataStallDnsTimeThreshold(0);
        wrappedMonitor = makeCellMeteredNetworkMonitor();
        wrappedMonitor.setLastProbeTime(SystemClock.elapsedRealtime() - 100);
        makeDnsTimeoutEvent(wrappedMonitor, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        assertFalse(wrappedMonitor.isDataStall());
        wrappedMonitor.setLastProbeTime(
                SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        assertFalse(wrappedMonitor.isDataStall());
    }

    @Test
    public void testIsDataStall_EvaluationTcp() throws Exception {
        // Evaluate TCP only. Expect ignoring DNS signal.
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_TCP);
        WrappedNetworkMonitor wrappedMonitor = makeMonitor(CELL_METERED_CAPABILITIES);
        assertFalse(wrappedMonitor.isDataStall());
        // Packet received.
        doReturn(5).when(mTst).getLatestReceivedCount();
        // Trigger a tcp event immediately.
        setTcpPollingInterval(0);
        wrappedMonitor.sendTcpPollingEvent();
        HandlerUtils.waitForIdle(wrappedMonitor.getHandler(), HANDLER_TIMEOUT_MS);
        assertFalse(wrappedMonitor.isDataStall());

        doReturn(0).when(mTst).getLatestReceivedCount();
        doReturn(true).when(mTst).isDataStallSuspected();
        // Trigger a tcp event immediately.
        setTcpPollingInterval(0);
        wrappedMonitor.sendTcpPollingEvent();
        HandlerUtils.waitForIdle(wrappedMonitor.getHandler(), HANDLER_TIMEOUT_MS);
        assertTrue(wrappedMonitor.isDataStall());
        verify(mCallbacks).notifyDataStallSuspected(matchTcpDataStallParcelable());
    }

    @Test
    public void testIsDataStall_EvaluationDnsAndTcp() throws Exception {
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_DNS | DATA_STALL_EVALUATION_TYPE_TCP);
        setupTcpDataStall();
        final WrappedNetworkMonitor nm = makeMonitor(CELL_METERED_CAPABILITIES);
        nm.setLastProbeTime(SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        makeDnsTimeoutEvent(nm, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        assertTrue(nm.isDataStall());
        verify(mCallbacks).notifyDataStallSuspected(
                matchDnsAndTcpDataStallParcelable(DEFAULT_DNS_TIMEOUT_THRESHOLD));

        doReturn(5).when(mTst).getLatestReceivedCount();
        // Trigger a tcp event immediately.
        setTcpPollingInterval(0);
        nm.sendTcpPollingEvent();
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
        assertFalse(nm.isDataStall());
    }

    @Test
    public void testIsDataStall_DisableTcp() {
        // Disable tcp detection with only DNS detect. keep the tcp signal but set to no DNS signal.
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_DNS);
        WrappedNetworkMonitor wrappedMonitor = makeMonitor(CELL_METERED_CAPABILITIES);
        makeDnsSuccessEvent(wrappedMonitor, 1);
        wrappedMonitor.sendTcpPollingEvent();
        HandlerUtils.waitForIdle(wrappedMonitor.getHandler(), HANDLER_TIMEOUT_MS);
        assertFalse(wrappedMonitor.isDataStall());
        verify(mTst, never()).isDataStallSuspected();
        verify(mTst, never()).pollSocketsInfo();
    }

    @Test
    public void testBrokenNetworkNotValidated() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 404);

        runFailedNetworkTest();
    }

    private void doValidationSkippedTest(NetworkCapabilities nc, int validationResult)
            throws Exception {
        runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES, nc, validationResult,
                0 /* probesSucceeded */, null /* redirectUrl */);
        verify(mCleartextDnsNetwork, never()).openConnection(any());
    }

    @Test
    public void testNoInternetCapabilityValidated() throws Exception {
        doValidationSkippedTest(CELL_NO_INTERNET_CAPABILITIES,
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_SKIPPED);
    }

    @Test
    public void testNoInternetCapabilityValidated_OlderPlatform() throws Exception {
        // Before callbacks version 11, NETWORK_VALIDATION_RESULT_SKIPPED is not sent
        initCallbacks(10);
        doValidationSkippedTest(CELL_NO_INTERNET_CAPABILITIES, NETWORK_VALIDATION_RESULT_VALID);
    }

    @Test
    public void testNoTrustedCapabilityValidated() throws Exception {
        // Cannot use the NetworkCapabilities builder on Q
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .addTransportType(TRANSPORT_CELLULAR);
        if (ShimUtils.isAtLeastS()) {
            nc.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        }
        doValidationSkippedTest(nc,
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_SKIPPED);
    }

    @Test
    public void testRestrictedCapabilityValidated() throws Exception {
        // Cannot use the NetworkCapabilities builder on Q
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(TRANSPORT_CELLULAR);
        if (ShimUtils.isAtLeastS()) {
            nc.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        }
        doValidationSkippedTest(nc,
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_SKIPPED);
    }

    private static NetworkCapabilities makeVcnUnderlyingCarrierWifiCaps() {
        // Must be called from within the test because NOT_VCN_MANAGED is an invalid capability
        // value up to Android R. Thus, this must be guarded by an SDK check in tests that use this.
        return new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
    }

    @Test
    public void testVcnUnderlyingNetwork() throws Exception {
        assumeTrue(ShimUtils.isAtLeastS());
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        final NetworkMonitor nm = runNetworkTest(TEST_AGENT_CONFIG,
                TEST_LINK_PROPERTIES, makeVcnUnderlyingCarrierWifiCaps(),
                NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
        assertEquals(NETWORK_VALIDATION_RESULT_VALID,
                nm.getEvaluationState().getEvaluationResult());
    }

    @Test
    public void testVcnUnderlyingNetworkBadNetwork() throws Exception {
        assumeTrue(ShimUtils.isAtLeastS());
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 404);

        final NetworkMonitor nm = runNetworkTest(TEST_AGENT_CONFIG,
                TEST_LINK_PROPERTIES, makeVcnUnderlyingCarrierWifiCaps(),
                VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */, null /* redirectUrl */);
        assertEquals(VALIDATION_RESULT_INVALID,
                nm.getEvaluationState().getEvaluationResult());
    }

    private static NetworkCapabilities makeDunNetworkCaps() {
        return new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                .build();
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testDunNetwork() throws Exception {
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        runNetworkTest(TEST_AGENT_CONFIG,
                TEST_LINK_PROPERTIES, makeDunNetworkCaps(),
                NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testDunNetwork_BadNetwork() throws Exception {
        setStatus(mHttpsConnection, 500);
        setStatus(mHttpConnection, 500);

        runNetworkTest(TEST_AGENT_CONFIG,
                TEST_LINK_PROPERTIES, makeDunNetworkCaps(),
                VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */, null /* redirectUrl */);
    }

    @Test @IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    public void testDunNetwork_UpToT_Disabled() throws Exception {
        doValidationSkippedTest(makeDunNetworkCaps(),
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_SKIPPED);
    }

    @Test @IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    public void testDunNetwork_UpToT_Enabled() throws Exception {
        doReturn(true).when(mResources).getBoolean(R.bool.config_validate_dun_networks);

        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        runNetworkTest(TEST_AGENT_CONFIG,
                TEST_LINK_PROPERTIES, makeDunNetworkCaps(),
                NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
    }

    public void setupAndLaunchCaptivePortalApp(final NetworkMonitor nm, String expectedUrl)
            throws Exception {
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        doReturn(TEST_LOGIN_URL).when(mHttpConnection).getHeaderField(eq("location"));
        notifyNetworkConnected(nm, CELL_METERED_CAPABILITIES);

        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(1))
                .showProvisioningNotification(any(), any());

        assertCaptivePortalAppReceiverRegistered(true /* isPortal */);

        // Check that startCaptivePortalApp sends the expected intent.
        nm.launchCaptivePortalApp();

        final ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        final ArgumentCaptor<Network> networkCaptor = ArgumentCaptor.forClass(Network.class);
        verify(mCm, timeout(HANDLER_TIMEOUT_MS).times(1))
                .startCaptivePortalApp(networkCaptor.capture(), bundleCaptor.capture());
        verify(mNotifier).notifyCaptivePortalValidationPending(networkCaptor.getValue());
        final Bundle bundle = bundleCaptor.getValue();
        final Network bundleNetwork = bundle.getParcelable(ConnectivityManager.EXTRA_NETWORK);
        assertEquals(TEST_NETID, bundleNetwork.netId);
        // network is passed both in bundle and as parameter, as the bundle is opaque to the
        // framework and only intended for the captive portal app, but the framework needs
        // the network to identify the right NetworkMonitor.
        assertEquals(TEST_NETID, networkCaptor.getValue().netId);
        // Portal URL should be detection URL.
        final String redirectUrl = bundle.getString(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL);
        assertEquals(expectedUrl, redirectUrl);
    }


    @Test
    public void testCaptivePortalLogin_beforeR() throws Exception {
        assumeFalse(ShimUtils.isAtLeastR());
        testCaptivePortalLogin(TEST_HTTP_URL);
    }

    @Test
    public void testCaptivePortalLogin_AfterR() throws Exception {
        assumeTrue(ShimUtils.isAtLeastR());
        testCaptivePortalLogin(TEST_LOGIN_URL);
    }

    private void testCaptivePortalLogin(String expectedUrl) throws Exception {
        final NetworkMonitor nm = makeMonitor(CELL_METERED_CAPABILITIES);
        setupAndLaunchCaptivePortalApp(nm, expectedUrl);

        // Have the app report that the captive portal is dismissed, and check that we revalidate.
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        nm.notifyCaptivePortalAppFinished(APP_RETURN_DISMISSED);
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).atLeastOnce())
                .notifyNetworkTestedWithExtras(matchNetworkTestResultParcelable(
                        NETWORK_VALIDATION_RESULT_VALID,
                        NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP));
        assertCaptivePortalAppReceiverRegistered(false /* isPortal */);
    }

    @Test
    public void testCaptivePortalUseAsIs_beforeR() throws Exception {
        assumeFalse(ShimUtils.isAtLeastR());
        testCaptivePortalUseAsIs(TEST_HTTP_URL);
    }

    @Test
    public void testCaptivePortalUseAsIs_AfterR() throws Exception {
        assumeTrue(ShimUtils.isAtLeastR());
        testCaptivePortalUseAsIs(TEST_LOGIN_URL);
    }

    private void testCaptivePortalUseAsIs(String expectedUrl) throws Exception {
        final NetworkMonitor nm = makeMonitor(CELL_METERED_CAPABILITIES);
        setupAndLaunchCaptivePortalApp(nm, expectedUrl);

        // The user decides this network is wanted as is, either by encountering an SSL error or
        // encountering an unknown scheme and then deciding to continue through the browser, or by
        // selecting this option through the options menu.
        nm.notifyCaptivePortalAppFinished(APP_RETURN_WANTED_AS_IS);
        // The captive portal is still closed, but the network validates since the user said so.
        // One interaction is triggered when APP_RETURN_WANTED_AS_IS response is received.
        // Another interaction is triggered when state machine gets into ValidatedState.
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(2))
                .notifyNetworkTestedWithExtras(matchNetworkTestResultParcelable(
                        NETWORK_VALIDATION_RESULT_VALID, 0 /* probesSucceeded */));
        // Revalidate.
        nm.forceReevaluation(0 /* responsibleUid */);

        // The network should still be valid.
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(3))
                .notifyNetworkTestedWithExtras(matchNetworkTestResultParcelable(
                        NETWORK_VALIDATION_RESULT_VALID, 0 /* probesSucceeded */));
    }

    private void runPrivateDnsSuccessTest() throws Exception {
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        // Verify dns query only get v6 address.
        mFakeDns.setAnswer("dns6.google", new String[]{"2001:db8::53"}, TYPE_AAAA);
        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns6.google",
                new InetAddress[0]));
        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(1 /* interaction */);

        // Verify dns query only get v4 address.
        mFakeDns.setAnswer("dns4.google", new String[]{"192.0.2.1"}, TYPE_A);
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns4.google",
                new InetAddress[0]));
        verifyNetworkTestedValidFromPrivateDns(2 /* interactions */);
        // NetworkMonitor will check if the probes has changed or not, if the probes has not
        // changed, the callback won't be fired. The interaction stays in 1 time.
        verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(1 /* interaction */);

        // Verify dns query get both v4 and v6 address.
        mFakeDns.setAnswer("dns.google", new String[]{"2001:db8::54"}, TYPE_AAAA);
        mFakeDns.setAnswer("dns.google", new String[]{"192.0.2.3"}, TYPE_A);
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));
        verifyNetworkTestedValidFromPrivateDns(3 /* interactions */);
        // Verify no further interaction.
        verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(1 /* interaction */);
    }

    @Test
    public void testPrivateDnsSuccess_SyncDns() throws Exception {
        doReturn(false).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        runPrivateDnsSuccessTest();
    }

    @Test
    public void testPrivateDnsSuccess_AsyncDns() throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        runPrivateDnsSuccessTest();
    }

    private void runProbeStatusChangedTest() throws Exception {
        // Set no record in FakeDns and expect validation to fail.
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));
        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);
        verifyNetworkTestedInvalidFromHttps(1 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndHttpsSucceeded(1 /* interaction */);

        // Fix DNS and retry, expect validation to succeed.
        mFakeDns.setAnswer("dns.google", new String[]{"2001:db8::1"}, TYPE_AAAA);

        wnm.forceReevaluation(Process.myUid());
        // ProbeCompleted should be reset to 0
        HandlerUtils.waitForIdle(wnm.getHandler(), HANDLER_TIMEOUT_MS);
        assertEquals(wnm.getEvaluationState().getProbeCompletedResult(), 0);
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(1 /* interaction */);
    }

    @Test
    public void testProbeStatusChanged_SyncDns() throws Exception {
        doReturn(false).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        runProbeStatusChangedTest();
    }

    @Test
    public void testProbeStatusChanged_AsyncDns() throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        runProbeStatusChangedTest();
    }

    private void runPrivateDnsResolutionRetryUpdateTest() throws Exception {
        // Set no record in FakeDns and expect validation to fail.
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));
        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);
        verifyNetworkTestedInvalidFromHttps(1 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndHttpsSucceeded(1 /* interactions */);

        // Fix DNS and retry, expect validation to succeed.
        mFakeDns.setAnswer("dns.google", new String[]{"2001:db8::1"}, TYPE_AAAA);

        wnm.forceReevaluation(Process.myUid());
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(1 /* interaction */);

        // Change configuration to an invalid DNS name, expect validation to fail.
        mFakeDns.setAnswer("dns.bad", new String[0], TYPE_A);
        mFakeDns.setAnswer("dns.bad", new String[0], TYPE_AAAA);
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.bad", new InetAddress[0]));
        // Strict mode hostname resolve fail. Expect only notification for evaluation fail. No probe
        // notification.
        verifyNetworkTestedInvalidFromHttps(2 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndHttpsSucceeded(2 /* interaction */);

        // Change configuration back to working again, but make private DNS not work.
        // Expect validation to fail.
        mFakeDns.setNonBypassPrivateDnsWorking(false);
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google",
                new InetAddress[0]));
        verifyNetworkTestedInvalidFromHttps(3 /* interactions */);
        // NetworkMonitor will check if the probes has changed or not, if the probes has not
        // changed, the callback won't be fired. No further interaction.
        verifyProbeStatusChangedPrivateDnsCompleteAndHttpsSucceeded(2 /* interaction */);

        // Make private DNS work again. Expect validation to succeed.
        mFakeDns.setNonBypassPrivateDnsWorking(true);
        wnm.forceReevaluation(Process.myUid());
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);
        verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(1 /* interaction */);
    }

    @Test
    public void testPrivateDnsResolutionRetryUpdate_SyncDns() throws Exception {
        doReturn(false).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        runPrivateDnsResolutionRetryUpdateTest();
    }

    @Test
    public void testPrivateDnsResolutionRetryUpdate_AsyncDns() throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        runPrivateDnsResolutionRetryUpdateTest();
    }

    @Test
    public void testAsyncPrivateDnsResolution_PartialTimeout() throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));

        // Only provide AAAA answer
        mFakeDns.setAnswer("dns.google", new String[]{"2001:db8::1"}, TYPE_AAAA);

        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);

        final PrivateDnsConfigParcel expectedConfig = new PrivateDnsConfigParcel();
        expectedConfig.hostname = "dns.google";
        expectedConfig.ips = new String[] {"2001:db8::1"};
        expectedConfig.privateDnsMode = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

        verify(mCallbacks).notifyPrivateDnsConfigResolved(expectedConfig);
    }

    @Test
    public void testAsyncPrivateDnsResolution_PartialFailure() throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));

        // A succeeds, AAAA fails
        mFakeDns.setAnswer("dns.google", new String[]{"192.0.2.123"}, TYPE_A);
        mFakeDns.setAnswer("dns.google", () -> {
            // DnsResolver.DnsException constructor is T+, so use a mock instead
            throw mock(DnsResolver.DnsException.class);
        }, TYPE_AAAA);

        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);

        final PrivateDnsConfigParcel expectedConfig = new PrivateDnsConfigParcel();
        expectedConfig.hostname = "dns.google";
        expectedConfig.ips = new String[] {"192.0.2.123"};
        expectedConfig.privateDnsMode = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

        verify(mCallbacks).notifyPrivateDnsConfigResolved(expectedConfig);
    }

    @Test
    public void testAsyncPrivateDnsResolution_AQuerySucceedsFirst_PrioritizeAAAA()
            throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));

        final ConditionVariable v4Queried = new ConditionVariable();
        mFakeDns.setAnswer("dns.google", () -> {
            v4Queried.open();
            return List.of(parseNumericAddress("192.0.2.123"));
        }, TYPE_A);
        mFakeDns.setAnswer("dns.google", () -> {
            // Make sure the v6 query processing is a bit slower than the v6 one. The small delay
            // below still does not guarantee that the v4 query will complete first, but it should
            // the large majority of the time, which should be enough to test it. Even if it does
            // not, the test should pass.
            v4Queried.block(HANDLER_TIMEOUT_MS);
            SystemClock.sleep(10L);
            return List.of(parseNumericAddress("2001:db8::1"), parseNumericAddress("2001:db8::2"));
        }, TYPE_AAAA);

        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);

        final PrivateDnsConfigParcel expectedConfig = new PrivateDnsConfigParcel();
        expectedConfig.hostname = "dns.google";
        // The IPv6 addresses are still first
        expectedConfig.ips = new String[] {"2001:db8::1", "2001:db8::2", "192.0.2.123"};
        expectedConfig.privateDnsMode = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

        verify(mCallbacks).notifyPrivateDnsConfigResolved(expectedConfig);
    }

    @Test
    public void testAsyncPrivateDnsResolution_ConfigChange_RestartsWithNewConfig()
            throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("v1.google", new InetAddress[0]));

        final ConditionVariable blockReplies = new ConditionVariable();
        final CountDownLatch queriedLatch = new CountDownLatch(2);
        mFakeDns.setAnswer("v1.google", () -> {
            queriedLatch.countDown();
            blockReplies.block(HANDLER_TIMEOUT_MS);
            return List.of(parseNumericAddress("192.0.2.123"));
        }, TYPE_A);
        mFakeDns.setAnswer("v1.google", () -> {
            queriedLatch.countDown();
            blockReplies.block(HANDLER_TIMEOUT_MS);
            return List.of(parseNumericAddress("2001:db8::1"));
        }, TYPE_AAAA);

        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);

        queriedLatch.await(HANDLER_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Send config update while DNS queries are in flight
        mFakeDns.setAnswer("v2.google", new String[] { "192.0.2.124" }, TYPE_A);
        mFakeDns.setAnswer("v2.google", new String[] { "2001:db8::2" }, TYPE_AAAA);
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("v2.google", new InetAddress[0]));

        // Let the original queries finish. Once DNS queries finish results are posted to the
        // handler, so they will be processed on the handler after the DNS settings change.
        blockReplies.open();

        // Expect only callbacks for the 2nd configuration
        verifyNetworkTestedValidFromPrivateDns(1 /* interactions */);

        final PrivateDnsConfigParcel expectedConfig = new PrivateDnsConfigParcel();
        expectedConfig.hostname = "v2.google";
        expectedConfig.ips = new String[] {"2001:db8::2", "192.0.2.124"};
        expectedConfig.privateDnsMode = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

        verify(mCallbacks).notifyPrivateDnsConfigResolved(expectedConfig);
    }

    @Test
    public void testAsyncPrivateDnsResolution_TurnOffStrictMode_SkipsDnsValidation()
            throws Exception {
        doReturn(true).when(mDependencies).isFeatureEnabled(
                any(), eq(NetworkStackUtils.NETWORKMONITOR_ASYNC_PRIVDNS_RESOLUTION));
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);

        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("v1.google", new InetAddress[0]));

        final ConditionVariable blockReplies = new ConditionVariable();
        final CountDownLatch queriedLatch = new CountDownLatch(2);
        mFakeDns.setAnswer("v1.google", () -> {
            queriedLatch.countDown();
            blockReplies.block(HANDLER_TIMEOUT_MS);
            return List.of(parseNumericAddress("192.0.2.123"));
        }, TYPE_A);
        mFakeDns.setAnswer("v1.google", () -> {
            queriedLatch.countDown();
            blockReplies.block(HANDLER_TIMEOUT_MS);
            return List.of(parseNumericAddress("2001:db8::1"));
        }, TYPE_AAAA);

        notifyNetworkConnected(wnm, CELL_NOT_METERED_CAPABILITIES);

        queriedLatch.await(HANDLER_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Send config update while DNS queries are in flight
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig(true /* useTls */));

        // Let the original queries finish. Once DNS queries finish results are posted to the
        // handler, so they will be processed on the handler after the DNS settings change.
        blockReplies.open();

        verifyNetworkTestedValidFromHttps(1 /* interactions */);
        verify(mCallbacks, never()).notifyPrivateDnsConfigResolved(any());
    }

    @Test
    public void testReevaluationInterval_networkResume() throws Exception {
        // Setup nothing and expect validation to fail.
        doReturn(true).when(mDependencies).isFeatureEnabled(any(), eq(REEVALUATE_WHEN_RESUME));
        final NetworkMonitor nm = runFailedNetworkTest();
        verifyNetworkTested(VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */,
                1 /* interactions */);
        // Reevaluation delay doubled right after 1st validation failure.
        assertEquals(INITIAL_REEVALUATE_DELAY_MS * 2, nm.getReevaluationDelayMs());

        // Suspend the network. Verify re-evaluation count does not increase.
        setNetworkCapabilities(nm, CELL_SUSPENDED_METERED_CAPABILITIES);
        verifyNetworkTested(VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */,
                1 /* interactions */);
        // Verify the count does not increase.
        assertEquals(INITIAL_REEVALUATE_DELAY_MS * 2, nm.getReevaluationDelayMs());

        // Resume the network, verify re-evaluation runs immediately and the timer resets.
        setNetworkCapabilities(nm, CELL_METERED_CAPABILITIES);
        // Wait for another idle to prevent from flaky because the handler fires another message
        // to re-evaluate.
        HandlerUtils.waitForIdle(nm.getHandler(), HANDLER_TIMEOUT_MS);
        assertEquals(INITIAL_REEVALUATE_DELAY_MS, nm.getReevaluationDelayMs());
        verifyNetworkTested(VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */,
                2 /* interactions */);
    }

    @Test
    public void testReevaluationInterval_verifiedNetwork() throws Exception {
        final WrappedNetworkMonitor wnm = prepareValidatedStateNetworkMonitor(
                CELL_METERED_CAPABILITIES);
        assertEquals(INITIAL_REEVALUATE_DELAY_MS, wnm.getReevaluationDelayMs());

        // Suspend the network. Verify re-evaluation count does not increase.
        setNetworkCapabilities(wnm, CELL_SUSPENDED_METERED_CAPABILITIES);
        verifyNetworkTestedValidFromHttps(1 /* interactions */);
        assertEquals(INITIAL_REEVALUATE_DELAY_MS, wnm.getReevaluationDelayMs());

        // Resume the network. Verify re-evaluation count does not increase.
        setNetworkCapabilities(wnm, CELL_METERED_CAPABILITIES);
        verifyNetworkTestedValidFromHttps(1 /* interactions */);
        assertEquals(INITIAL_REEVALUATE_DELAY_MS, wnm.getReevaluationDelayMs());
    }

    @Test
    public void testTcpSocketTracker_setCapabilities() throws Exception {
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_TCP);
        final InOrder inOrder = inOrder(mTst);
        final WrappedNetworkMonitor wnm = prepareValidatedStateNetworkMonitor(
                CELL_METERED_CAPABILITIES);
        inOrder.verify(mTst).setNetworkCapabilities(eq(CELL_METERED_CAPABILITIES));

        // Suspend the network. Verify the capabilities would be passed to TcpSocketTracker.
        setNetworkCapabilities(wnm, CELL_SUSPENDED_METERED_CAPABILITIES);
        inOrder.verify(mTst).setNetworkCapabilities(eq(CELL_SUSPENDED_METERED_CAPABILITIES));
    }

    @Test
    public void testDataStall_setOpportunisticMode() {
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_TCP);
        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        InOrder inOrder = inOrder(mTst);
        // Initialized with default value.
        inOrder.verify(mTst).setOpportunisticMode(false);

        // Strict mode.
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig("dns.google", new InetAddress[0]));
        HandlerUtils.waitForIdle(wnm.getHandler(), HANDLER_TIMEOUT_MS);
        inOrder.verify(mTst).setOpportunisticMode(false);

        // Opportunistic mode.
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig(true /* useTls */));
        HandlerUtils.waitForIdle(wnm.getHandler(), HANDLER_TIMEOUT_MS);
        inOrder.verify(mTst).setOpportunisticMode(true);

        // Off mode.
        wnm.notifyPrivateDnsSettingsChanged(new PrivateDnsConfig(false /* useTls */));
        HandlerUtils.waitForIdle(wnm.getHandler(), HANDLER_TIMEOUT_MS);
        inOrder.verify(mTst).setOpportunisticMode(false);
    }

    @Test
    public void testDataStall_StallDnsSuspectedAndSendMetricsOnCell() throws Exception {
        testDataStall_StallDnsSuspectedAndSendMetrics(NetworkCapabilities.TRANSPORT_CELLULAR,
                CELL_METERED_CAPABILITIES);
    }

    @Test
    public void testDataStall_StallDnsSuspectedAndSendMetricsOnWifi() throws Exception {
        testDataStall_StallDnsSuspectedAndSendMetrics(NetworkCapabilities.TRANSPORT_WIFI,
                WIFI_NOT_METERED_CAPABILITIES);
    }

    private void testDataStall_StallDnsSuspectedAndSendMetrics(int transport,
            NetworkCapabilities nc) throws Exception {
        // NM suspects data stall from DNS signal and sends data stall metrics.
        final WrappedNetworkMonitor nm = prepareValidatedStateNetworkMonitor(nc);
        makeDnsTimeoutEvent(nm, 5);
        // Trigger a dns signal to start evaluate data stall and upload metrics.
        nm.notifyDnsResponse(RETURN_CODE_DNS_TIMEOUT);
        // Verify data sent as expected.
        verifySendDataStallDetectionStats(nm, DATA_STALL_EVALUATION_TYPE_DNS, transport);
    }

    @Test
    public void testDataStall_NoStallSuspectedAndSendMetrics() throws Exception {
        final WrappedNetworkMonitor nm = prepareValidatedStateNetworkMonitor(
                CELL_METERED_CAPABILITIES);
        // Setup no data stall dns signal.
        makeDnsTimeoutEvent(nm, 3);
        assertFalse(nm.isDataStall());
        // Trigger a dns signal to start evaluate data stall.
        nm.notifyDnsResponse(RETURN_CODE_DNS_SUCCESS);
        verify(mDependencies, never()).writeDataStallDetectionStats(any(), any());
    }

    @Test
    public void testDataStall_StallTcpSuspectedAndSendMetricsOnCell() throws Exception {
        testDataStall_StallTcpSuspectedAndSendMetrics(CELL_METERED_CAPABILITIES);
    }

    @Test
    public void testDataStall_StallTcpSuspectedAndSendMetricsOnWifi() throws Exception {
        testDataStall_StallTcpSuspectedAndSendMetrics(WIFI_NOT_METERED_CAPABILITIES);
    }

    private void testDataStall_StallTcpSuspectedAndSendMetrics(NetworkCapabilities nc)
            throws Exception {
        setupTcpDataStall();
        setTcpPollingInterval(1);
        // NM suspects data stall from TCP signal and sends data stall metrics.
        setDataStallEvaluationType(DATA_STALL_EVALUATION_TYPE_TCP);
        final WrappedNetworkMonitor nm = prepareValidatedStateNetworkMonitor(nc);
        // Trigger a tcp event immediately.
        nm.sendTcpPollingEvent();
        // Allow only one transport type in the context of this test for simplification.
        final int[] transports = nc.getTransportTypes();
        assertEquals(1, transports.length);
        verifySendDataStallDetectionStats(nm, DATA_STALL_EVALUATION_TYPE_TCP, transports[0]);
    }

    private WrappedNetworkMonitor prepareValidatedStateNetworkMonitor(NetworkCapabilities nc)
            throws Exception {
        // Connect a VALID network to simulate the data stall detection because data stall
        // evaluation will only start from validated state.
        setStatus(mHttpsConnection, 204);
        final WrappedNetworkMonitor nm;
        // Allow only one transport type in the context of this test for simplification.
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            nm = makeCellMeteredNetworkMonitor();
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            nm = makeWifiNotMeteredNetworkMonitor();
            setupTestWifiInfo();
        } else {
            nm = null;
            fail("Undefined transport type");
        }
        notifyNetworkConnected(nm, nc);
        verifyNetworkTestedValidFromHttps(1 /* interactions */);
        nm.setLastProbeTime(SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        return nm;
    }

    private void setupTcpDataStall() {
        doReturn(0).when(mTst).getLatestReceivedCount();
        doReturn(TEST_TCP_FAIL_RATE).when(mTst).getLatestPacketFailPercentage();
        doReturn(TEST_TCP_PACKET_COUNT).when(mTst).getSentSinceLastRecv();
        doReturn(true).when(mTst).isDataStallSuspected();
        doReturn(true).when(mTst).pollSocketsInfo();
    }

    private void verifySendDataStallDetectionStats(WrappedNetworkMonitor nm, int evalType,
            int transport) {
        // Verify data sent as expectated.
        final ArgumentCaptor<CaptivePortalProbeResult> probeResultCaptor =
                ArgumentCaptor.forClass(CaptivePortalProbeResult.class);
        final ArgumentCaptor<DataStallDetectionStats> statsCaptor =
                ArgumentCaptor.forClass(DataStallDetectionStats.class);
        // TCP data stall detection may be triggered more than once because NM stays in the
        // ValidatedState and polling timer is set to 0.
        verify(mDependencies, timeout(HANDLER_TIMEOUT_MS).atLeast(1))
                .writeDataStallDetectionStats(statsCaptor.capture(), probeResultCaptor.capture());
        // Ensure probe will not stop due to rate-limiting mechanism.
        nm.setLastProbeTime(SystemClock.elapsedRealtime() - STALL_EXPECTED_LAST_PROBE_TIME_MS);
        assertTrue(nm.isDataStall());
        assertTrue(probeResultCaptor.getValue().isSuccessful());
        verifyTestDataStallDetectionStats(evalType, transport, statsCaptor.getValue());
    }

    private void verifyTestDataStallDetectionStats(int evalType, int transport,
            DataStallDetectionStats stats) {
        assertEquals(transport, stats.mNetworkType);
        switch (transport) {
            case NetworkCapabilities.TRANSPORT_WIFI:
                assertArrayEquals(makeTestWifiDataNano(), stats.mWifiInfo);
                // Expedient way to check stats.mCellularInfo contains the neutral byte array that
                // is sent to represent a lack of data, as stats.mCellularInfo is not supposed to
                // contain null.
                assertArrayEquals(DataStallDetectionStats.emptyCellDataIfNull(null),
                        stats.mCellularInfo);
                break;
            case NetworkCapabilities.TRANSPORT_CELLULAR:
                // Expedient way to check stats.mWifiInfo contains the neutral byte array that is
                // sent to represent a lack of data, as stats.mWifiInfo is not supposed to contain
                // null.
                assertArrayEquals(DataStallDetectionStats.emptyWifiInfoIfNull(null),
                        stats.mWifiInfo);
                assertArrayEquals(makeTestCellDataNano(), stats.mCellularInfo);
                break;
            default:
                // Add other cases.
                fail("Unexpected transport type");
        }

        assertEquals(evalType, stats.mEvaluationType);
        if ((evalType & DATA_STALL_EVALUATION_TYPE_TCP) != 0) {
            assertEquals(TEST_TCP_FAIL_RATE, stats.mTcpFailRate);
            assertEquals(TEST_TCP_PACKET_COUNT, stats.mTcpSentSinceLastRecv);
        } else {
            assertEquals(DataStallDetectionStats.UNSPECIFIED_TCP_FAIL_RATE, stats.mTcpFailRate);
            assertEquals(DataStallDetectionStats.UNSPECIFIED_TCP_PACKETS_COUNT,
                    stats.mTcpSentSinceLastRecv);
        }

        if ((evalType & DATA_STALL_EVALUATION_TYPE_DNS) != 0) {
            assertArrayEquals(stats.mDns, makeTestDnsTimeoutNano(DEFAULT_DNS_TIMEOUT_THRESHOLD));
        } else {
            assertArrayEquals(stats.mDns, makeTestDnsTimeoutNano(0 /* times */));
        }
    }

    private DataStallDetectionStats makeTestDataStallDetectionStats(int evaluationType,
            int transportType) {
        final DataStallDetectionStats.Builder stats = new DataStallDetectionStats.Builder()
                .setEvaluationType(evaluationType)
                .setNetworkType(transportType);
        switch (transportType) {
            case NetworkCapabilities.TRANSPORT_CELLULAR:
                stats.setCellData(TelephonyManager.NETWORK_TYPE_LTE /* radioType */,
                        true /* roaming */,
                        TEST_MCCMNC /* networkMccmnc */,
                        TEST_MCCMNC /* simMccmnc */,
                        CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN /* signalStrength */);
                break;
            case NetworkCapabilities.TRANSPORT_WIFI:
                setupTestWifiInfo();
                stats.setWiFiData(mWifiInfo);
                break;
            default:
                break;
        }

        if ((evaluationType & DATA_STALL_EVALUATION_TYPE_TCP) != 0) {
            generateTestTcpStats(stats);
        }

        if ((evaluationType & DATA_STALL_EVALUATION_TYPE_DNS) != 0) {
            generateTimeoutDnsEvent(stats, DEFAULT_DNS_TIMEOUT_THRESHOLD);
        }

        return stats.build();
    }

    private byte[] makeTestDnsTimeoutNano(int timeoutCount) {
        // Make a expected nano dns message.
        final DnsEvent event = new DnsEvent();
        event.dnsReturnCode = new int[timeoutCount];
        event.dnsTime = new long[timeoutCount];
        Arrays.fill(event.dnsReturnCode, RETURN_CODE_DNS_TIMEOUT);
        Arrays.fill(event.dnsTime, TEST_ELAPSED_TIME_MS);
        return MessageNano.toByteArray(event);
    }

    private byte[] makeTestCellDataNano() {
        final CellularData data = new CellularData();
        data.ratType = DataStallEventProto.RADIO_TECHNOLOGY_LTE;
        data.networkMccmnc = TEST_MCCMNC;
        data.simMccmnc = TEST_MCCMNC;
        data.isRoaming = true;
        data.signalStrength = 0;
        return MessageNano.toByteArray(data);
    }

    private byte[] makeTestWifiDataNano() {
        final WifiData data = new WifiData();
        data.wifiBand = DataStallEventProto.AP_BAND_2GHZ;
        data.signalStrength = TEST_SIGNAL_STRENGTH;
        return MessageNano.toByteArray(data);
    }

    private void setupTestWifiInfo() {
        doReturn(mWifiInfo).when(mWifi).getConnectionInfo();
        doReturn(TEST_SIGNAL_STRENGTH).when(mWifiInfo).getRssi();
        // Set to 2.4G band. Map to DataStallEventProto.AP_BAND_2GHZ proto definition.
        doReturn(2450).when(mWifiInfo).getFrequency();
    }

    private void testDataStallMetricsWithCellular(int evalType) {
        testDataStallMetrics(evalType, NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private void testDataStallMetricsWithWiFi(int evalType) {
        testDataStallMetrics(evalType, NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void testDataStallMetrics(int evalType, int transportType) {
        setDataStallEvaluationType(evalType);
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addTransportType(transportType)
                .addCapability(NET_CAPABILITY_INTERNET);
        final WrappedNetworkMonitor wrappedMonitor = makeMonitor(nc);
        setupTestWifiInfo();
        final DataStallDetectionStats stats =
                makeTestDataStallDetectionStats(evalType, transportType);
        assertEquals(wrappedMonitor.buildDataStallDetectionStats(transportType, evalType), stats);

        if ((evalType & DATA_STALL_EVALUATION_TYPE_TCP) != 0) {
            verify(mTst, timeout(HANDLER_TIMEOUT_MS).atLeastOnce()).getLatestPacketFailPercentage();
        } else {
            verify(mTst, never()).getLatestPacketFailPercentage();
        }
    }

    @Test
    public void testCollectDataStallMetrics_DnsWithCellular() {
        testDataStallMetricsWithCellular(DATA_STALL_EVALUATION_TYPE_DNS);
    }

    @Test
    public void testCollectDataStallMetrics_DnsWithWiFi() {
        testDataStallMetricsWithWiFi(DATA_STALL_EVALUATION_TYPE_DNS);
    }

    @Test
    public void testCollectDataStallMetrics_TcpWithCellular() {
        testDataStallMetricsWithCellular(DATA_STALL_EVALUATION_TYPE_TCP);
    }

    @Test
    public void testCollectDataStallMetrics_TcpWithWiFi() {
        testDataStallMetricsWithWiFi(DATA_STALL_EVALUATION_TYPE_TCP);
    }

    @Test
    public void testCollectDataStallMetrics_TcpAndDnsWithWifi() {
        testDataStallMetricsWithWiFi(
                DATA_STALL_EVALUATION_TYPE_TCP | DATA_STALL_EVALUATION_TYPE_DNS);
    }

    @Test
    public void testCollectDataStallMetrics_TcpAndDnsWithCellular() {
        testDataStallMetricsWithCellular(
                DATA_STALL_EVALUATION_TYPE_TCP | DATA_STALL_EVALUATION_TYPE_DNS);
    }

    @Test
    public void testIgnoreHttpsProbe() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 204);
        // Expect to send HTTP, HTTPS, FALLBACK probe and evaluation result notifications to CS.
        final NetworkMonitor nm = runNetworkTest(NETWORK_VALIDATION_RESULT_PARTIAL,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                null /* redirectUrl */);

        nm.setAcceptPartialConnectivity();
        // Expect to update evaluation result notifications to CS.
        verifyNetworkTested(NETWORK_VALIDATION_RESULT_PARTIAL | NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                1 /* interactions */);
    }

    @Test
    public void testIsPartialConnectivity() throws Exception {
        setStatus(mHttpsConnection, 500);
        setStatus(mHttpConnection, 204);
        setStatus(mFallbackConnection, 500);
        runPartialConnectivityNetworkTest(
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP);
    }

    @Test
    public void testIsCaptivePortal_OnlyFallbackSucceed() throws Exception {
        setStatus(mHttpsConnection, 500);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 204);
        runNetworkTest(VALIDATION_RESULT_INVALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_FALLBACK,
                null /* redirectUrl */);
    }

    private void assertIpAddressArrayEquals(String[] expected, InetAddress[] actual) {
        String[] actualStrings = new String[actual.length];
        for (int i = 0; i < actual.length; i++) {
            actualStrings[i] = actual[i].getHostAddress();
        }
        assertArrayEquals("Array of IP addresses differs", expected, actualStrings);
    }

    @Test
    public void testSendDnsProbeWithTimeout() throws Exception {
        WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        final int shortTimeoutMs = 200;
        // v6 only.
        String[] expected = new String[]{"2001:db8::"};
        mFakeDns.setAnswer("www.google.com", expected, TYPE_AAAA);
        InetAddress[] actual = wnm.sendDnsProbeWithTimeout("www.google.com", shortTimeoutMs);
        assertIpAddressArrayEquals(expected, actual);
        // v4 only.
        expected = new String[]{"192.0.2.1"};
        mFakeDns.setAnswer("www.android.com", expected, TYPE_A);
        actual = wnm.sendDnsProbeWithTimeout("www.android.com", shortTimeoutMs);
        assertIpAddressArrayEquals(expected, actual);
        // Both v4 & v6.
        expected = new String[]{"192.0.2.1", "2001:db8::"};
        mFakeDns.setAnswer("www.googleapis.com", new String[]{"192.0.2.1"}, TYPE_A);
        mFakeDns.setAnswer("www.googleapis.com", new String[]{"2001:db8::"}, TYPE_AAAA);
        actual = wnm.sendDnsProbeWithTimeout("www.googleapis.com", shortTimeoutMs);
        assertIpAddressArrayEquals(expected, actual);
        // Clear DNS response.
        mFakeDns.setAnswer("www.android.com", new String[0], TYPE_A);
        try {
            actual = wnm.sendDnsProbeWithTimeout("www.android.com", shortTimeoutMs);
            fail("No DNS results, expected UnknownHostException");
        } catch (UnknownHostException e) {
        }

        mFakeDns.setAnswer("www.android.com", (String[]) null, TYPE_A);
        mFakeDns.setAnswer("www.android.com", (String[]) null, TYPE_AAAA);
        try {
            wnm.sendDnsProbeWithTimeout("www.android.com", shortTimeoutMs);
            fail("DNS query timed out, expected UnknownHostException");
        } catch (UnknownHostException e) {
        }
    }

    @Test
    public void testNotifyNetwork_WithforceReevaluation() throws Exception {
        // Set validated result for both HTTP and HTTPS probes.
        setValidProbes();
        final NetworkMonitor nm = runValidatedNetworkTest();
        // Verify forceReevaluation will not reset the validation result but only probe result until
        // getting the validation result.
        setSslException(mHttpsConnection);
        nm.forceReevaluation(Process.myUid());
        // Expect to send HTTP, HTTPs, FALLBACK and evaluation results.
        verifyNetworkTested(NETWORK_VALIDATION_RESULT_PARTIAL,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                1 /* interactions */);
    }

    @Test
    public void testNotifyNetwork_NotifyNetworkTestedOldInterfaceVersion() throws Exception {
        // Use old interface version so notifyNetworkTested is used over
        // notifyNetworkTestedWithExtras
        initCallbacks(4);

        // Trigger Network validation
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);
        final NetworkMonitor nm = makeMonitor(CELL_METERED_CAPABILITIES);
        notifyNetworkConnected(nm, CELL_METERED_CAPABILITIES);
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS))
                .notifyNetworkTested(eq(NETWORK_VALIDATION_RESULT_VALID
                        | NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS),
                        eq(null) /* redirectUrl */);
    }

    @Test
    public void testDismissPortalInValidatedNetworkEnabledOsSupported() throws Exception {
        assumeTrue(ShimUtils.isAtLeastR());
        testDismissPortalInValidatedNetworkEnabled(TEST_LOGIN_URL, TEST_LOGIN_URL);
    }

    @Test
    public void testDismissPortalInValidatedNetworkEnabledOsSupported_NullLocationUrl()
            throws Exception {
        assumeTrue(ShimUtils.isAtLeastR());
        testDismissPortalInValidatedNetworkEnabled(TEST_HTTP_URL, null /* locationUrl */);
    }

    @Test
    public void testDismissPortalInValidatedNetworkEnabledOsSupported_InvalidLocationUrl()
            throws Exception {
        assumeTrue(ShimUtils.isAtLeastR());
        testDismissPortalInValidatedNetworkEnabled(TEST_HTTP_URL, TEST_RELATIVE_URL);
    }

    @Test
    public void testDismissPortalInValidatedNetworkEnabledOsNotSupported() throws Exception {
        assumeFalse(ShimUtils.isAtLeastR());
        testDismissPortalInValidatedNetworkEnabled(TEST_HTTP_URL, TEST_LOGIN_URL);
    }

    private void testDismissPortalInValidatedNetworkEnabled(String expectedUrl, String locationUrl)
            throws Exception {
        setSslException(mHttpsConnection);
        setPortal302(mHttpConnection);
        doReturn(locationUrl).when(mHttpConnection).getHeaderField(eq("location"));
        final NetworkMonitor nm = makeMonitor(CELL_METERED_CAPABILITIES);
        notifyNetworkConnected(nm, CELL_METERED_CAPABILITIES);

        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(1))
            .showProvisioningNotification(any(), any());

        assertCaptivePortalAppReceiverRegistered(true /* isPortal */);
        // Check that startCaptivePortalApp sends the expected intent.
        nm.launchCaptivePortalApp();

        final ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        final ArgumentCaptor<Network> networkCaptor = ArgumentCaptor.forClass(Network.class);
        verify(mCm, timeout(HANDLER_TIMEOUT_MS).times(1))
            .startCaptivePortalApp(networkCaptor.capture(), bundleCaptor.capture());
        verify(mNotifier).notifyCaptivePortalValidationPending(networkCaptor.getValue());
        final Bundle bundle = bundleCaptor.getValue();
        final Network bundleNetwork = bundle.getParcelable(ConnectivityManager.EXTRA_NETWORK);
        assertEquals(TEST_NETID, bundleNetwork.netId);
        // Network is passed both in bundle and as parameter, as the bundle is opaque to the
        // framework and only intended for the captive portal app, but the framework needs
        // the network to identify the right NetworkMonitor.
        assertEquals(TEST_NETID, networkCaptor.getValue().netId);
        // Portal URL should be redirect URL.
        final String redirectUrl = bundle.getString(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL);
        assertEquals(expectedUrl, redirectUrl);
    }

    @Test
    public void testEvaluationState_clearProbeResults() throws Exception {
        setValidProbes();
        final NetworkMonitor nm = runValidatedNetworkTest();
        nm.getEvaluationState().clearProbeResults();
        // Verify probe results are all reset and only evaluation result left.
        assertEquals(NETWORK_VALIDATION_RESULT_VALID,
                nm.getEvaluationState().getEvaluationResult());
        assertEquals(0, nm.getEvaluationState().getProbeResults());
    }

    @Test
    public void testEvaluationState_reportProbeResult() throws Exception {
        setValidProbes();
        final NetworkMonitor nm = runValidatedNetworkTest();

        nm.reportHttpProbeResult(NETWORK_VALIDATION_PROBE_HTTP,
                CaptivePortalProbeResult.success(1 << PROBE_HTTP));
        // Verify result should be appended and notifyNetworkTestedWithExtras callback is triggered
        // once.
        assertEquals(NETWORK_VALIDATION_RESULT_VALID,
                nm.getEvaluationState().getEvaluationResult());
        assertEquals(NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS
                | NETWORK_VALIDATION_PROBE_HTTP, nm.getEvaluationState().getProbeResults());

        nm.reportHttpProbeResult(NETWORK_VALIDATION_PROBE_HTTP,
                CaptivePortalProbeResult.failed(1 << PROBE_HTTP));
        // Verify DNS probe result should not be cleared.
        assertEquals(NETWORK_VALIDATION_PROBE_DNS,
                nm.getEvaluationState().getProbeResults() & NETWORK_VALIDATION_PROBE_DNS);
    }

    @Test
    public void testEvaluationState_reportEvaluationResult() throws Exception {
        setStatus(mHttpsConnection, 500);
        setStatus(mHttpConnection, 204);
        final NetworkMonitor nm = runNetworkTest(NETWORK_VALIDATION_RESULT_PARTIAL,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                null /* redirectUrl */);

        nm.getEvaluationState().reportEvaluationResult(NETWORK_VALIDATION_RESULT_VALID,
                null /* redirectUrl */);
        verifyNetworkTested(NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                1 /* interactions */);

        nm.getEvaluationState().reportEvaluationResult(
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_PARTIAL,
                null /* redirectUrl */);
        verifyNetworkTested(
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_PARTIAL,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                1 /* interactions */);

        nm.getEvaluationState().reportEvaluationResult(VALIDATION_RESULT_INVALID,
                TEST_REDIRECT_URL);
        verifyNetworkTested(VALIDATION_RESULT_INVALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP,
                TEST_REDIRECT_URL, 1 /* interactions */);
    }

    @Test
    public void testExtractCharset() {
        assertEquals(StandardCharsets.UTF_8, extractCharset(null));
        assertEquals(StandardCharsets.UTF_8, extractCharset("text/html;charset=utf-8"));
        assertEquals(StandardCharsets.UTF_8, extractCharset("text/html;charset=UtF-8"));
        assertEquals(StandardCharsets.UTF_8, extractCharset("text/html; Charset=\"utf-8\""));
        assertEquals(StandardCharsets.UTF_8, extractCharset("image/png"));
        assertEquals(StandardCharsets.UTF_8, extractCharset("Text/HTML;"));
        assertEquals(StandardCharsets.UTF_8, extractCharset("multipart/form-data; boundary=-aa*-"));
        assertEquals(StandardCharsets.UTF_8, extractCharset("text/plain;something=else"));
        assertEquals(StandardCharsets.UTF_8, extractCharset("text/plain;charset=ImNotACharset"));

        assertEquals(StandardCharsets.ISO_8859_1, extractCharset("text/plain; CharSeT=ISO-8859-1"));
        assertEquals(Charset.forName("Shift_JIS"), extractCharset("text/plain;charset=Shift_JIS"));
        assertEquals(Charset.forName("Windows-1251"), extractCharset(
                "text/plain;charset=Windows-1251 ; somethingelse"));
    }

    @Test
    public void testReadAsString() throws IOException {
        final String repeatedString = "1aテスト-?";
        // Infinite stream repeating characters
        class TestInputStream extends InputStream {
            private final byte[] mBytes = repeatedString.getBytes(StandardCharsets.UTF_8);
            private int mPosition = -1;

            @Override
            public int read() {
                mPosition = (mPosition + 1) % mBytes.length;
                return mBytes[mPosition];
            }
        }

        final String readString = NetworkMonitor.readAsString(new TestInputStream(),
                1500 /* maxLength */, StandardCharsets.UTF_8);

        assertEquals(1500, readString.length());
        for (int i = 0; i < readString.length(); i++) {
            assertEquals(repeatedString.charAt(i % repeatedString.length()), readString.charAt(i));
        }
    }

    @Test
    public void testReadAsString_StreamShorterThanLimit() throws Exception {
        final WrappedNetworkMonitor wnm = makeCellNotMeteredNetworkMonitor();
        final byte[] content = "The HTTP response code is 200 but it is not a captive portal."
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(new String(content), wnm.readAsString(new ByteArrayInputStream(content),
                content.length, StandardCharsets.UTF_8));
        // Test the case that the stream ends earlier than the limit.
        assertEquals(new String(content), wnm.readAsString(new ByteArrayInputStream(content),
                content.length + 10, StandardCharsets.UTF_8));
    }

    @Test
    public void testMultipleProbesOnPortalNetwork() throws Exception {
        setupResourceForMultipleProbes();
        // One of the http probes is portal, then result is portal.
        setPortal302(mOtherHttpConnection1);
        runPortalNetworkTest();
        // Get conclusive result from one of the HTTP probe. Expect to create 2 HTTP and 2 HTTPS
        // probes as resource configuration, but the portal can be detected before other probes
        // start.
        verify(mCleartextDnsNetwork, atMost(4)).openConnection(any());
        verify(mCleartextDnsNetwork, atLeastOnce()).openConnection(any());
        verify(mOtherHttpConnection1).getResponseCode();
    }

    @Test
    public void testMultipleProbesOnValidNetwork() throws Exception {
        setupResourceForMultipleProbes();
        // One of the https probes succeeds, then it's validated.
        setStatus(mOtherHttpsConnection2, 204);
        runValidatedNetworkTest();
        // Get conclusive result from one of the HTTPS probe. Expect to create 2 HTTP and 2 HTTPS
        // probes as resource configuration, but the network may validate from the HTTPS probe
        // before other probes start.
        verify(mCleartextDnsNetwork, atMost(4)).openConnection(any());
        verify(mCleartextDnsNetwork, atLeastOnce()).openConnection(any());
        verify(mOtherHttpsConnection2).getResponseCode();
    }

    @Test
    public void testMultipleProbesOnInValidNetworkForPrioritizedResource() throws Exception {
        setupResourceForMultipleProbes();
        // The configuration resource is prioritized. Only use configurations from resource.(i.e
        // Only configuration for mOtherHttpsConnection2, mOtherHttpsConnection2,
        // mOtherHttpConnection2, mOtherHttpConnection2 will affect the result.)
        // Configure mHttpsConnection is no-op.
        setStatus(mHttpsConnection, 204);
        runFailedNetworkTest();
        // No conclusive result from both HTTP and HTTPS probes. Expect to create 2 HTTP and 2 HTTPS
        // probes as resource configuration. All probes are expected to have been run because this
        // network is set to never validate (no probe has a success or portal result), so NM tests
        // all probes to completion.
        verify(mCleartextDnsNetwork, times(4)).openConnection(any());
        verify(mHttpsConnection, never()).getResponseCode();
    }

    @Test
    public void testMultipleProbesOnInValidNetwork() throws Exception {
        setupResourceForMultipleProbes();
        runFailedNetworkTest();
        // No conclusive result from both HTTP and HTTPS probes. Expect to create 2 HTTP and 2 HTTPS
        // probes as resource configuration.
        verify(mCleartextDnsNetwork, times(4)).openConnection(any());
    }

    @Test
    public void testIsCaptivePortal_FromExternalSource() throws Exception {
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        assumeTrue(ShimUtils.isAtLeastS());
        final NetworkMonitor monitor = makeMonitor(WIFI_NOT_METERED_CAPABILITIES);

        NetworkInformationShim networkShim = NetworkInformationShimImpl.newInstance();
        CaptivePortalDataShim captivePortalData = new CaptivePortalDataShimImpl(
                new CaptivePortalData.Builder().setCaptive(true).build());
        final LinkProperties linkProperties = new LinkProperties(TEST_LINK_PROPERTIES);
        networkShim.setCaptivePortalData(linkProperties, captivePortalData);
        CaptivePortalDataShim captivePortalDataShim =
                networkShim.getCaptivePortalData(linkProperties);

        try {
            // Set up T&C captive portal info from Passpoint
            captivePortalData = captivePortalDataShim.withPasspointInfo(TEST_FRIENDLY_NAME,
                    Uri.parse(TEST_VENUE_INFO_URL), Uri.parse(TEST_LOGIN_URL));
        } catch (UnsupportedApiLevelException e) {
            // Minimum API level for this test is 31
            return;
        }

        networkShim.setCaptivePortalData(linkProperties, captivePortalData);
        monitor.notifyLinkPropertiesChanged(linkProperties);
        final NetworkCapabilities networkCapabilities =
                new NetworkCapabilities(WIFI_NOT_METERED_CAPABILITIES);
        notifyNetworkConnected(monitor, linkProperties, networkCapabilities);
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(1))
                .showProvisioningNotification(any(), any());
        assertCaptivePortalAppReceiverRegistered(true /* isPortal */);
        verifyNetworkTestedPortal(TEST_LOGIN_URL, 1 /* interactions */);

        // Force reevaluation and confirm that the network is still captive
        HandlerUtils.waitForIdle(monitor.getHandler(), HANDLER_TIMEOUT_MS);
        monitor.forceReevaluation(Process.myUid());
        assertEquals(monitor.getEvaluationState().getProbeCompletedResult(), 0);
        verifyNetworkTestedPortal(TEST_LOGIN_URL, 2 /* interactions */);

        // Check that startCaptivePortalApp sends the expected intent.
        monitor.launchCaptivePortalApp();

        verify(mCm, timeout(HANDLER_TIMEOUT_MS).times(1)).startCaptivePortalApp(
                argThat(network -> TEST_NETID == network.netId),
                argThat(bundle -> bundle.getString(
                        ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL).equals(TEST_LOGIN_URL)
                        && TEST_NETID == ((Network) bundle.getParcelable(
                        ConnectivityManager.EXTRA_NETWORK)).netId));
    }

    @Test
    public void testOemPaidNetworkValidated() throws Exception {
        setValidProbes();

        final NetworkMonitor nm = runNetworkTest(
                TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES, WIFI_OEM_PAID_CAPABILITIES,
                NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
        assertEquals(NETWORK_VALIDATION_RESULT_VALID,
                nm.getEvaluationState().getEvaluationResult());
    }

    @Test
    public void testOemPaidNetwork_AllProbesFailed() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 404);

        runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES, WIFI_OEM_PAID_CAPABILITIES,
                VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */, null /* redirectUrl */);
    }

    @Test
    public void testOemPaidNetworkNoInternetCapabilityValidated() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mHttpConnection, 500);
        setStatus(mFallbackConnection, 404);

        final NetworkCapabilities networkCapabilities =
                new NetworkCapabilities(WIFI_OEM_PAID_CAPABILITIES);
        networkCapabilities.removeCapability(NET_CAPABILITY_INTERNET);

        final int validationResult =
                NETWORK_VALIDATION_RESULT_VALID | NETWORK_VALIDATION_RESULT_SKIPPED;
        runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES, networkCapabilities,
                validationResult, 0 /* probesSucceeded */, null /* redirectUrl */);

        verify(mCleartextDnsNetwork, never()).openConnection(any());
        verify(mHttpsConnection, never()).getResponseCode();
        verify(mHttpConnection, never()).getResponseCode();
        verify(mFallbackConnection, never()).getResponseCode();
    }

    @Test
    public void testOemPaidNetwork_CaptivePortalNotLaunched() throws Exception {
        setSslException(mHttpsConnection);
        setStatus(mFallbackConnection, 404);
        setPortal302(mHttpConnection);

        runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES, WIFI_OEM_PAID_CAPABILITIES,
                VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded */,
                TEST_LOGIN_URL);

        verify(mCallbacks, never()).showProvisioningNotification(any(), any());
    }

    private void setupResourceForMultipleProbes() {
        // Configure the resource to send multiple probe.
        doReturn(TEST_HTTPS_URLS).when(mResources)
                .getStringArray(R.array.config_captive_portal_https_urls);
        doReturn(TEST_HTTP_URLS).when(mResources)
                .getStringArray(R.array.config_captive_portal_http_urls);
    }

    private void makeDnsTimeoutEvent(WrappedNetworkMonitor wrappedMonitor, int count) {
        for (int i = 0; i < count; i++) {
            wrappedMonitor.getDnsStallDetector().accumulateConsecutiveDnsTimeoutCount(
                    RETURN_CODE_DNS_TIMEOUT);
        }
    }

    private void makeDnsSuccessEvent(WrappedNetworkMonitor wrappedMonitor, int count) {
        for (int i = 0; i < count; i++) {
            wrappedMonitor.getDnsStallDetector().accumulateConsecutiveDnsTimeoutCount(
                    RETURN_CODE_DNS_SUCCESS);
        }
    }

    private DataStallDetectionStats makeEmptyDataStallDetectionStats() {
        return new DataStallDetectionStats.Builder().build();
    }

    private void setDataStallEvaluationType(int type) {
        doReturn(type).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(CONFIG_DATA_STALL_EVALUATION_TYPE), anyInt());
    }

    private void setMinDataStallEvaluateInterval(int time) {
        doReturn(time).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(CONFIG_DATA_STALL_MIN_EVALUATE_INTERVAL), anyInt());
    }

    private void setValidDataStallDnsTimeThreshold(int time) {
        doReturn(time).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(CONFIG_DATA_STALL_VALID_DNS_TIME_THRESHOLD), anyInt());
    }

    private void setConsecutiveDnsTimeoutThreshold(int num) {
        doReturn(num).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(CONFIG_DATA_STALL_CONSECUTIVE_DNS_TIMEOUT_THRESHOLD), anyInt());
    }

    private void setTcpPollingInterval(int time) {
        doReturn(time).when(mDependencies).getDeviceConfigPropertyInt(any(),
                eq(CONFIG_DATA_STALL_TCP_POLLING_INTERVAL), anyInt());
    }

    private void setFallbackUrl(String url) {
        doReturn(url).when(mDependencies).getSetting(any(),
                eq(Settings.Global.CAPTIVE_PORTAL_FALLBACK_URL), any());
    }

    private void setOtherFallbackUrls(String urls) {
        doReturn(urls).when(mDependencies).getDeviceConfigProperty(any(),
                eq(CAPTIVE_PORTAL_OTHER_FALLBACK_URLS), any());
    }

    private void setFallbackSpecs(String specs) {
        doReturn(specs).when(mDependencies).getDeviceConfigProperty(any(),
                eq(CAPTIVE_PORTAL_FALLBACK_PROBE_SPECS), any());
    }

    private void setCaptivePortalMode(int mode) {
        doReturn(mode).when(mDependencies).getSetting(any(),
                eq(Settings.Global.CAPTIVE_PORTAL_MODE), anyInt());
    }

    private void setDeviceConfig(String key, String value) {
        doReturn(value).when(mDependencies).getDeviceConfigProperty(eq(NAMESPACE_CONNECTIVITY),
                eq(key), any() /* defaultValue */);
    }

    private NetworkMonitor runPortalNetworkTest() throws Exception {
        final NetworkMonitor nm = runNetworkTest(VALIDATION_RESULT_PORTAL,
                0 /* probesSucceeded */, TEST_LOGIN_URL);
        assertCaptivePortalAppReceiverRegistered(true /* isPortal */);
        return nm;
    }

    private NetworkMonitor runNoValidationNetworkTest() throws Exception {
        final NetworkMonitor nm = runNetworkTest(NETWORK_VALIDATION_RESULT_VALID,
                0 /* probesSucceeded */, null /* redirectUrl */);
        assertCaptivePortalAppReceiverRegistered(false /* isPortal */);
        return nm;
    }

    private NetworkMonitor runFailedNetworkTest() throws Exception {
        final NetworkMonitor nm = runNetworkTest(
                VALIDATION_RESULT_INVALID, 0 /* probesSucceeded */, null /* redirectUrl */);
        assertCaptivePortalAppReceiverRegistered(false /* isPortal */);
        return nm;
    }

    private NetworkMonitor runPartialConnectivityNetworkTest(int probesSucceeded)
            throws Exception {
        final NetworkMonitor nm = runNetworkTest(NETWORK_VALIDATION_RESULT_PARTIAL,
                probesSucceeded, null /* redirectUrl */);
        assertCaptivePortalAppReceiverRegistered(false /* isPortal */);
        return nm;
    }

    private NetworkMonitor runValidatedNetworkTest() throws Exception {
        // Expect to send HTTPS and evaluation results.
        return runNetworkTest(NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                null /* redirectUrl */);
    }

    private NetworkMonitor runNetworkTest(int testResult, int probesSucceeded, String redirectUrl)
            throws Exception {
        return runNetworkTest(TEST_AGENT_CONFIG, TEST_LINK_PROPERTIES, CELL_METERED_CAPABILITIES,
                testResult, probesSucceeded, redirectUrl);
    }

    private NetworkMonitor runNetworkTest(NetworkAgentConfigShim config,
            LinkProperties lp, NetworkCapabilities nc,
            int testResult, int probesSucceeded, String redirectUrl) throws Exception {
        final NetworkMonitor monitor = makeMonitor(nc);
        notifyNetworkConnected(monitor, config, lp, nc);
        verifyNetworkTested(testResult, probesSucceeded, redirectUrl, 1 /* interactions */);
        HandlerUtils.waitForIdle(monitor.getHandler(), HANDLER_TIMEOUT_MS);

        return monitor;
    }

    private void verifyProbeStatusChangedPrivateDnsCompleteAndSucceeded(int interactions)
            throws Exception {
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(interactions))
                .notifyProbeStatusChanged(eq(PROBES_PRIVDNS_VALID), eq(PROBES_PRIVDNS_VALID));
    }

    private void verifyProbeStatusChangedPrivateDnsCompleteAndHttpsSucceeded(int interactions)
            throws Exception {
        verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(interactions))
                .notifyProbeStatusChanged(
                        eq(PROBES_PRIVDNS_VALID),
                        eq(NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS));
    }

    private void verifyNetworkTestedInvalidFromHttps(int interactions) throws Exception {
        verifyNetworkTested(VALIDATION_RESULT_INVALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                interactions);
    }

    private void verifyNetworkTestedPortal(String redirectUrl, int interactions) throws Exception {
        verifyNetworkTested(VALIDATION_RESULT_PORTAL, 0 /* probesSucceeded */, redirectUrl,
                interactions);
    }

    private void verifyNetworkTestedValidFromHttps(int interactions) throws Exception {
        verifyNetworkTested(NETWORK_VALIDATION_RESULT_VALID,
                NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS,
                interactions);
    }

    private void verifyNetworkTestedValidFromPrivateDns(int interactions) throws Exception {
        verifyNetworkTested(NETWORK_VALIDATION_RESULT_VALID, PROBES_PRIVDNS_VALID, interactions);
    }

    private void verifyNetworkTested(int testResult, int probesSucceeded, int interactions)
            throws Exception {
        verifyNetworkTested(testResult, probesSucceeded, null /* redirectUrl */, interactions);
    }

    private void verifyNetworkTested(int testResult, int probesSucceeded, String redirectUrl,
            int interactions) throws RemoteException {
        try {
            verify(mCallbacks, timeout(HANDLER_TIMEOUT_MS).times(interactions))
                    .notifyNetworkTestedWithExtras(
                            matchNetworkTestResultParcelable(
                                    testResult, probesSucceeded, redirectUrl));
        } catch (AssertionFailedError e) {
            // Capture the callbacks up to now to give a better error message
            final ArgumentCaptor<NetworkTestResultParcelable> captor =
                    ArgumentCaptor.forClass(NetworkTestResultParcelable.class);

            // Call verify() again to verify the same method call verified by the previous verify
            // call which failed, but this time use a captor to log the exact parcel sent by
            // NetworkMonitor.
            // This assertion will fail if notifyNetworkTested was not called at all.
            verify(mCallbacks, times(interactions)).notifyNetworkTestedWithExtras(captor.capture());

            final List<NetworkTestResultParcelable> results = captor.getAllValues();
            final NetworkTestResultParcelable lastResult = results.get(results.size() - 1);
            fail(String.format("notifyNetworkTestedWithExtras was not called %d times with the "
                    + "expected result within timeout. "
                    + "Expected result %d, probes succeeded %d, redirect URL %s, "
                    + "last result was (%d, %d, %s).",
                    interactions, testResult, probesSucceeded, redirectUrl,
                    lastResult.result, lastResult.probesSucceeded, lastResult.redirectUrl));
        }
    }

    private void notifyNetworkConnected(NetworkMonitor nm, NetworkAgentConfigShim config,
            LinkProperties lp, NetworkCapabilities nc) throws Exception {
        if (SdkLevel.isAtLeastT()) {
            nm.notifyNetworkConnectedParcel(makeParams(config, lp, nc));
        } else {
            nm.notifyNetworkConnected(lp, nc);
        }
    }

    private void notifyNetworkConnected(NetworkMonitor nm, LinkProperties lp,
            NetworkCapabilities nc) throws Exception {
        notifyNetworkConnected(nm, TEST_AGENT_CONFIG, lp, nc);
    }

    private void notifyNetworkConnected(NetworkMonitor nm, NetworkCapabilities nc)
            throws Exception {
        notifyNetworkConnected(nm, TEST_LINK_PROPERTIES, nc);
    }

    private NetworkMonitorParameters makeParams(@NonNull final NetworkAgentConfigShim config,
            @NonNull final LinkProperties prop, @NonNull final NetworkCapabilities caps)
            throws Exception {
        final NetworkMonitorParameters params = new NetworkMonitorParameters();
        config.writeToNetworkMonitorParams(params);
        params.linkProperties = prop;
        params.networkCapabilities = caps;
        return params;
    }

    private void setSslException(HttpURLConnection connection) throws IOException {
        doThrow(new SSLHandshakeException("Invalid cert")).when(connection).getResponseCode();
    }

    private void setValidProbes() throws IOException {
        setStatus(mHttpsConnection, 204);
        setStatus(mHttpConnection, 204);
    }

    private void set302(HttpURLConnection connection, String location) throws IOException {
        setStatus(connection, 302);
        doReturn(location).when(connection).getHeaderField(LOCATION_HEADER);
    }

    private void setPortal302(HttpURLConnection connection) throws IOException {
        set302(connection, TEST_LOGIN_URL);
    }

    private void setApiContent(HttpURLConnection connection, String content) throws IOException {
        setStatus(connection, 200);
        final Map<String, List<String>> headerFields = new HashMap<>();
        headerFields.put(
                CONTENT_TYPE_HEADER, singletonList("application/captive+json;charset=UTF-8"));
        doReturn(headerFields).when(connection).getHeaderFields();
        doReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .when(connection).getInputStream();
    }

    private void setStatus(HttpURLConnection connection, int status) throws IOException {
        doReturn(status).when(connection).getResponseCode();
    }

    private void generateTimeoutDnsEvent(DataStallDetectionStats.Builder stats, int num) {
        for (int i = 0; i < num; i++) {
            stats.addDnsEvent(RETURN_CODE_DNS_TIMEOUT, TEST_ELAPSED_TIME_MS /* timeMs */);
        }
    }

    private void generateTestTcpStats(DataStallDetectionStats.Builder stats) {
        doReturn(TEST_TCP_FAIL_RATE).when(mTst).getLatestPacketFailPercentage();
        doReturn(TEST_TCP_PACKET_COUNT).when(mTst).getSentSinceLastRecv();
        stats.setTcpFailRate(TEST_TCP_FAIL_RATE).setTcpSentSinceLastRecv(TEST_TCP_PACKET_COUNT);
    }

    private NetworkTestResultParcelable matchNetworkTestResultParcelable(final int result,
            final int probesSucceeded) {
        return matchNetworkTestResultParcelable(result, probesSucceeded, null /* redirectUrl */);
    }

    private NetworkTestResultParcelable matchNetworkTestResultParcelable(final int result,
            final int probesSucceeded, String redirectUrl) {
        // TODO: also verify probesAttempted
        return argThat(p -> p.result == result && p.probesSucceeded == probesSucceeded
                && Objects.equals(p.redirectUrl, redirectUrl));
    }

    private DataStallReportParcelable matchDnsAndTcpDataStallParcelable(final int timeoutCount) {
        return argThat(p ->
                (p.detectionMethod & ConstantsShim.DETECTION_METHOD_DNS_EVENTS) != 0
                && (p.detectionMethod & ConstantsShim.DETECTION_METHOD_TCP_METRICS) != 0
                && p.dnsConsecutiveTimeouts == timeoutCount);
    }

    private DataStallReportParcelable matchDnsDataStallParcelable(final int timeoutCount) {
        return argThat(p -> (p.detectionMethod & ConstantsShim.DETECTION_METHOD_DNS_EVENTS) != 0
                && p.dnsConsecutiveTimeouts == timeoutCount);
    }

    private DataStallReportParcelable matchTcpDataStallParcelable() {
        return argThat(p -> (p.detectionMethod & ConstantsShim.DETECTION_METHOD_TCP_METRICS) != 0);
    }

    private void assertCaptivePortalAppReceiverRegistered(boolean isPortal) {
        // There will be configuration change receiver registered after NetworkMonitor being
        // started. If captive portal app receiver is registered, then the size of the registered
        // receivers will be 2. Otherwise, mRegisteredReceivers should only contain 1 configuration
        // change receiver.
        synchronized (mRegisteredReceivers) {
            assertEquals(isPortal ? 2 : 1, mRegisteredReceivers.size());
        }
    }
}
