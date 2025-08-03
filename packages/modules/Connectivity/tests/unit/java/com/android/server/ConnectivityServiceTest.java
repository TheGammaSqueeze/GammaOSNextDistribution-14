/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.CHANGE_NETWORK_STATE;
import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.CONTROL_OEM_PAID_NETWORK_PREFERENCE;
import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.DUMP;
import static android.Manifest.permission.GET_INTENT_SENDER_INTENT;
import static android.Manifest.permission.LOCAL_MAC_ADDRESS;
import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.Manifest.permission.NETWORK_FACTORY;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.NETWORK_SETUP_WIZARD;
import static android.Manifest.permission.NETWORK_STACK;
import static android.Manifest.permission.PACKET_KEEPALIVE_OFFLOAD;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.STATUS_BAR_SERVICE;
import static android.app.ActivityManager.UidFrozenStateChangedCallback.UID_FROZEN_STATE_FROZEN;
import static android.app.ActivityManager.UidFrozenStateChangedCallback.UID_FROZEN_STATE_UNFROZEN;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;
import static android.content.Intent.ACTION_PACKAGE_REPLACED;
import static android.content.Intent.ACTION_USER_ADDED;
import static android.content.Intent.ACTION_USER_REMOVED;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.content.pm.PackageManager.FEATURE_ETHERNET;
import static android.content.pm.PackageManager.FEATURE_WIFI;
import static android.content.pm.PackageManager.FEATURE_WIFI_DIRECT;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN;
import static android.net.ConnectivityManager.ACTION_DATA_ACTIVITY_CHANGE;
import static android.net.ConnectivityManager.BLOCKED_METERED_REASON_DATA_SAVER;
import static android.net.ConnectivityManager.BLOCKED_METERED_REASON_MASK;
import static android.net.ConnectivityManager.BLOCKED_METERED_REASON_USER_RESTRICTED;
import static android.net.ConnectivityManager.BLOCKED_REASON_BATTERY_SAVER;
import static android.net.ConnectivityManager.BLOCKED_REASON_NONE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.EXTRA_DEVICE_TYPE;
import static android.net.ConnectivityManager.EXTRA_IS_ACTIVE;
import static android.net.ConnectivityManager.EXTRA_NETWORK_INFO;
import static android.net.ConnectivityManager.EXTRA_NETWORK_TYPE;
import static android.net.ConnectivityManager.EXTRA_REALTIME_NS;
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
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_DEFAULT;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK;
import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_MOBILE_SUPL;
import static android.net.ConnectivityManager.TYPE_VPN;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_DNS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_FALLBACK;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_HTTP;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_HTTPS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_PRIVDNS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_PARTIAL;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_VALID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_BIP;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CBS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_EIMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_ENTERPRISE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND;
import static android.net.NetworkCapabilities.NET_CAPABILITY_FOTA;
import static android.net.NetworkCapabilities.NET_CAPABILITY_IA;
import static android.net.NetworkCapabilities.NET_CAPABILITY_IMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_MMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_MMTEL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_RCS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_SUPL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VSIM;
import static android.net.NetworkCapabilities.NET_CAPABILITY_WIFI_P2P;
import static android.net.NetworkCapabilities.NET_CAPABILITY_XCAP;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_1;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_2;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_3;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_4;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_5;
import static android.net.NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION;
import static android.net.NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS;
import static android.net.NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS;
import static android.net.NetworkCapabilities.REDACT_NONE;
import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE;
import static android.net.NetworkScore.KEEP_CONNECTED_FOR_HANDOVER;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_TEST;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_TEST_ONLY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_UNINITIALIZED;
import static android.net.Proxy.PROXY_CHANGE_ACTION;
import static android.net.RouteInfo.RTN_UNREACHABLE;
import static android.net.resolv.aidl.IDnsResolverUnsolicitedEventListener.PREFIX_OPERATION_ADDED;
import static android.net.resolv.aidl.IDnsResolverUnsolicitedEventListener.PREFIX_OPERATION_REMOVED;
import static android.net.resolv.aidl.IDnsResolverUnsolicitedEventListener.VALIDATION_RESULT_FAILURE;
import static android.net.resolv.aidl.IDnsResolverUnsolicitedEventListener.VALIDATION_RESULT_SUCCESS;
import static android.os.Process.INVALID_UID;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.telephony.DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH;
import static android.telephony.DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;

import static com.android.server.ConnectivityService.ALLOW_SATALLITE_NETWORK_FALLBACK;
import static com.android.server.ConnectivityService.DELAY_DESTROY_FROZEN_SOCKETS_VERSION;
import static com.android.net.module.util.DeviceConfigUtils.TETHERING_MODULE_NAME;
import static com.android.server.ConnectivityService.ALLOW_SYSUI_CONNECTIVITY_REPORTS;
import static com.android.server.ConnectivityService.KEY_DESTROY_FROZEN_SOCKETS_VERSION;
import static com.android.server.ConnectivityService.LOG_BPF_RC;
import static com.android.server.ConnectivityService.MAX_NETWORK_REQUESTS_PER_SYSTEM_UID;
import static com.android.server.ConnectivityService.PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED;
import static com.android.server.ConnectivityService.PREFERENCE_ORDER_OEM;
import static com.android.server.ConnectivityService.PREFERENCE_ORDER_PROFILE;
import static com.android.server.ConnectivityService.PREFERENCE_ORDER_VPN;
import static com.android.server.ConnectivityService.createDeliveryGroupKeyForConnectivityAction;
import static com.android.server.ConnectivityService.makeNflogPrefix;
import static com.android.server.ConnectivityServiceTestUtils.transportToLegacyType;
import static com.android.server.NetworkAgentWrapper.CallbackType.OnQosCallbackRegister;
import static com.android.server.NetworkAgentWrapper.CallbackType.OnQosCallbackUnregister;
import static com.android.testutils.Cleanup.testAndCleanup;
import static com.android.testutils.ConcurrentUtils.await;
import static com.android.testutils.ConcurrentUtils.durationOf;
import static com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import static com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.FunctionalUtils.ignoreExceptions;
import static com.android.testutils.HandlerUtils.visibleOnHandlerThread;
import static com.android.testutils.HandlerUtils.waitForIdleSerialExecutor;
import static com.android.testutils.MiscAsserts.assertContainsAll;
import static com.android.testutils.MiscAsserts.assertContainsExactly;
import static com.android.testutils.MiscAsserts.assertEmpty;
import static com.android.testutils.MiscAsserts.assertLength;
import static com.android.testutils.MiscAsserts.assertRunsInAtMost;
import static com.android.testutils.MiscAsserts.assertSameElements;
import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.RecorderCallback.CallbackEntry.AVAILABLE;
import static com.android.testutils.RecorderCallback.CallbackEntry.BLOCKED_STATUS;
import static com.android.testutils.RecorderCallback.CallbackEntry.BLOCKED_STATUS_INT;
import static com.android.testutils.RecorderCallback.CallbackEntry.LINK_PROPERTIES_CHANGED;
import static com.android.testutils.RecorderCallback.CallbackEntry.LOSING;
import static com.android.testutils.RecorderCallback.CallbackEntry.LOST;
import static com.android.testutils.RecorderCallback.CallbackEntry.NETWORK_CAPS_UPDATED;
import static com.android.testutils.RecorderCallback.CallbackEntry.RESUMED;
import static com.android.testutils.RecorderCallback.CallbackEntry.SUSPENDED;
import static com.android.testutils.RecorderCallback.CallbackEntry.UNAVAILABLE;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
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

import static java.util.Arrays.asList;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.UidFrozenStateChangedCallback;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.usage.NetworkStatsManager;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.location.LocationManager;
import android.net.CaptivePortal;
import android.net.CaptivePortalData;
import android.net.ConnectionInfo;
import android.net.ConnectivityDiagnosticsManager.DataStallReport;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.ConnectivityManager.PacketKeepalive;
import android.net.ConnectivityManager.PacketKeepaliveCallback;
import android.net.ConnectivityManager.TooManyRequestsException;
import android.net.ConnectivitySettingsManager;
import android.net.ConnectivityThread;
import android.net.DataStallReportParcelable;
import android.net.EthernetManager;
import android.net.EthernetNetworkSpecifier;
import android.net.IConnectivityDiagnosticsCallback;
import android.net.IDnsResolver;
import android.net.INetd;
import android.net.INetworkMonitor;
import android.net.INetworkMonitorCallbacks;
import android.net.IOnCompleteListener;
import android.net.IQosCallback;
import android.net.InetAddresses;
import android.net.InterfaceConfigurationParcel;
import android.net.IpPrefix;
import android.net.IpSecManager;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MatchAllNetworkSpecifier;
import android.net.NativeNetworkConfig;
import android.net.NativeNetworkType;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkPolicyManager;
import android.net.NetworkPolicyManager.NetworkPolicyCallback;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.net.NetworkSpecifier;
import android.net.NetworkStack;
import android.net.NetworkStateSnapshot;
import android.net.NetworkTestResultParcelable;
import android.net.OemNetworkPreferences;
import android.net.PacProxyManager;
import android.net.ProfileNetworkPreference;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.QosCallbackException;
import android.net.QosFilter;
import android.net.QosSession;
import android.net.ResolverParamsParcel;
import android.net.RouteInfo;
import android.net.RouteInfoParcel;
import android.net.SocketKeepalive;
import android.net.TelephonyNetworkSpecifier;
import android.net.TetheringManager;
import android.net.TransportInfo;
import android.net.UidRange;
import android.net.UidRangeParcel;
import android.net.UnderlyingNetworkInfo;
import android.net.Uri;
import android.net.VpnManager;
import android.net.VpnTransportInfo;
import android.net.connectivity.ConnectivityCompatChanges;
import android.net.metrics.IpConnectivityLog;
import android.net.netd.aidl.NativeUidRangeConfig;
import android.net.networkstack.NetworkStackClientBase;
import android.net.resolv.aidl.Nat64PrefixEventParcel;
import android.net.resolv.aidl.PrivateDnsValidationEventParcel;
import android.net.shared.PrivateDnsConfig;
import android.net.wifi.WifiInfo;
import android.os.BadParcelableException;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Messenger;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.SystemConfigManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.security.Credentials;
import android.system.Os;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.EpsBearerQosSessionAttributes;
import android.telephony.data.NrQosSessionAttributes;
import android.test.mock.MockContentResolver;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.IBatteryStats;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;
import com.android.internal.util.WakeupMessage;
import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.net.module.util.ArrayTrackRecord;
import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.LocationPermissionChecker;
import com.android.net.module.util.NetworkMonitorUtils;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.NetworkAgentConfigShimImpl;
import com.android.networkstack.apishim.common.BroadcastOptionsShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.server.ConnectivityService.ConnectivityDiagnosticsCallbackInfo;
import com.android.server.ConnectivityService.NetworkRequestInfo;
import com.android.server.ConnectivityServiceTest.ConnectivityServiceDependencies.DestroySocketsWrapper;
import com.android.server.ConnectivityServiceTest.ConnectivityServiceDependencies.ReportedInterfaces;
import com.android.server.connectivity.ApplicationSelfCertifiedNetworkCapabilities;
import com.android.server.connectivity.AutomaticOnOffKeepaliveTracker;
import com.android.server.connectivity.CarrierPrivilegeAuthenticator;
import com.android.server.connectivity.ClatCoordinator;
import com.android.server.connectivity.ConnectivityFlags;
import com.android.server.connectivity.ConnectivityResources;
import com.android.server.connectivity.KeepaliveTracker;
import com.android.server.connectivity.MultinetworkPolicyTracker;
import com.android.server.connectivity.MultinetworkPolicyTrackerTestDependencies;
import com.android.server.connectivity.Nat464Xlat;
import com.android.server.connectivity.NetworkAgentInfo;
import com.android.server.connectivity.NetworkNotificationManager;
import com.android.server.connectivity.NetworkNotificationManager.NotificationType;
import com.android.server.connectivity.ProxyTracker;
import com.android.server.connectivity.QosCallbackTracker;
import com.android.server.connectivity.SatelliteAccessController;
import com.android.server.connectivity.TcpKeepaliveController;
import com.android.server.connectivity.UidRangeUtils;
import com.android.server.connectivity.VpnProfileStore;
import com.android.server.net.NetworkPinner;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.FunctionalUtils.Function3;
import com.android.testutils.FunctionalUtils.ThrowingConsumer;
import com.android.testutils.FunctionalUtils.ThrowingRunnable;
import com.android.testutils.HandlerUtils;
import com.android.testutils.RecorderCallback.CallbackEntry;
import com.android.testutils.TestableNetworkCallback;
import com.android.testutils.TestableNetworkOfferCallback;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tests for {@link ConnectivityService}.
 *
 * Build, install and run with:
 *  runtest frameworks-net -c com.android.server.ConnectivityServiceTest
 */
// TODO : move methods from this test to smaller tests in the 'connectivityservice' directory
// to enable faster testing of smaller groups of functionality.
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRunner.MonitorThreadLeak
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class ConnectivityServiceTest {
    private static final String TAG = "ConnectivityServiceTest";

    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    @Rule
    public final PlatformCompatChangeRule compatChangeRule = new PlatformCompatChangeRule();

    private static final int TIMEOUT_MS = 2_000;
    // Broadcasts can take a long time to be delivered. The test will not wait for that long unless
    // there is a failure, so use a long timeout.
    private static final int BROADCAST_TIMEOUT_MS = 30_000;
    private static final int TEST_LINGER_DELAY_MS = 400;
    private static final int TEST_NASCENT_DELAY_MS = 300;
    // Chosen to be less than the linger and nascent timeout. This ensures that we can distinguish
    // between a LOST callback that arrives immediately and a LOST callback that arrives after
    // the linger/nascent timeout. For this, our assertions should run fast enough to leave
    // less than (mService.mLingerDelayMs - TEST_CALLBACK_TIMEOUT_MS) between the time callbacks are
    // supposedly fired, and the time we call expectCapChanged.
    private static final int TEST_CALLBACK_TIMEOUT_MS = 250;
    // Chosen to be less than TEST_CALLBACK_TIMEOUT_MS. This ensures that requests have time to
    // complete before callbacks are verified.
    private static final int TEST_REQUEST_TIMEOUT_MS = 150;

    private static final int UNREASONABLY_LONG_ALARM_WAIT_MS = 2_000;

    private static final long TIMESTAMP = 1234L;

    private static final int NET_ID = 110;
    private static final int OEM_PREF_ANY_NET_ID = -1;
    // Set a non-zero value to verify the flow to set tcp init rwnd value.
    private static final int TEST_TCP_INIT_RWND = 60;

    // Used for testing the per-work-profile default network.
    private static final int TEST_APP_ID = 103;
    private static final int TEST_WORK_PROFILE_USER_ID = 2;
    private static final int TEST_WORK_PROFILE_APP_UID =
            UserHandle.getUid(TEST_WORK_PROFILE_USER_ID, TEST_APP_ID);
    private static final int TEST_APP_ID_2 = 104;
    private static final int TEST_WORK_PROFILE_APP_UID_2 =
            UserHandle.getUid(TEST_WORK_PROFILE_USER_ID, TEST_APP_ID_2);
    private static final int TEST_APP_ID_3 = 105;
    private static final int TEST_APP_ID_4 = 106;
    private static final int TEST_APP_ID_5 = 107;

    private static final String CLAT_PREFIX = "v4-";
    private static final String MOBILE_IFNAME = "test_rmnet_data0";
    private static final String CLAT_MOBILE_IFNAME = CLAT_PREFIX + MOBILE_IFNAME;
    private static final String WIFI_IFNAME = "test_wlan0";
    private static final String WIFI_WOL_IFNAME = "test_wlan_wol";
    private static final String VPN_IFNAME = "tun10042";
    private static final String ETHERNET_IFNAME = "eth0";
    private static final String TEST_PACKAGE_NAME = "com.android.test.package";
    private static final int TEST_PACKAGE_UID = 123;
    private static final int TEST_PACKAGE_UID2 = 321;
    private static final int TEST_PACKAGE_UID3 = 456;
    private static final int NETWORK_ACTIVITY_NO_UID = -1;
    private static final int TEST_SUBSCRIPTION_ID = 1;

    private static final int PACKET_WAKEUP_MARK_MASK = 0x80000000;

    private static final String ALWAYS_ON_PACKAGE = "com.android.test.alwaysonvpn";

    private static final String INTERFACE_NAME = "interface";

    private static final String TEST_VENUE_URL_NA_PASSPOINT = "https://android.com/";
    private static final String TEST_VENUE_URL_NA_OTHER = "https://example.com/";
    private static final String TEST_TERMS_AND_CONDITIONS_URL_NA_PASSPOINT =
            "https://android.com/terms/";
    private static final String TEST_TERMS_AND_CONDITIONS_URL_NA_OTHER =
            "https://example.com/terms/";
    private static final String TEST_VENUE_URL_CAPPORT = "https://android.com/capport/";
    private static final String TEST_USER_PORTAL_API_URL_CAPPORT =
            "https://android.com/user/api/capport/";
    private static final String TEST_FRIENDLY_NAME = "Network friendly name";
    private static final String TEST_REDIRECT_URL = "http://example.com/firstPath";

    private MockContext mServiceContext;
    private HandlerThread mCsHandlerThread;
    private ConnectivityServiceDependencies mDeps;
    private AutomaticOnOffKeepaliveTrackerDependencies mAutoOnOffKeepaliveDependencies;
    private ConnectivityService mService;
    private WrappedConnectivityManager mCm;
    private TestNetworkAgentWrapper mWiFiAgent;
    private TestNetworkAgentWrapper mCellAgent;
    private TestNetworkAgentWrapper mEthernetAgent;
    private final List<TestNetworkAgentWrapper> mCreatedAgents = new ArrayList<>();
    private MockVpn mMockVpn;
    private Context mContext;
    private NetworkPolicyCallback mPolicyCallback;
    private WrappedMultinetworkPolicyTracker mPolicyTracker;
    private ProxyTracker mProxyTracker;
    private HandlerThread mAlarmManagerThread;
    private TestNetIdManager mNetIdManager;
    private QosCallbackMockHelper mQosCallbackMockHelper;
    private QosCallbackTracker mQosCallbackTracker;
    private TestNetworkCallback mDefaultNetworkCallback;
    private TestNetworkCallback mSystemDefaultNetworkCallback;
    private TestNetworkCallback mProfileDefaultNetworkCallback;
    private TestNetworkCallback mTestPackageDefaultNetworkCallback;
    private TestNetworkCallback mProfileDefaultNetworkCallbackAsAppUid2;
    private TestNetworkCallback mTestPackageDefaultNetworkCallback2;

    // State variables required to emulate NetworkPolicyManagerService behaviour.
    private int mBlockedReasons = BLOCKED_REASON_NONE;

    @Mock DeviceIdleInternal mDeviceIdleInternal;
    @Mock INetworkManagementService mNetworkManagementService;
    @Mock NetworkStatsManager mStatsManager;
    @Mock IDnsResolver mMockDnsResolver;
    @Mock INetd mMockNetd;
    @Mock NetworkStackClientBase mNetworkStack;
    @Mock PackageManager mPackageManager;
    @Mock UserManager mUserManager;
    @Mock NotificationManager mNotificationManager;
    @Mock AlarmManager mAlarmManager;
    @Mock IConnectivityDiagnosticsCallback mConnectivityDiagnosticsCallback;
    @Mock IBinder mIBinder;
    @Mock LocationManager mLocationManager;
    @Mock AppOpsManager mAppOpsManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock EthernetManager mEthernetManager;
    @Mock NetworkPolicyManager mNetworkPolicyManager;
    @Mock VpnProfileStore mVpnProfileStore;
    @Mock SystemConfigManager mSystemConfigManager;
    @Mock DevicePolicyManager mDevicePolicyManager;
    @Mock Resources mResources;
    @Mock ClatCoordinator mClatCoordinator;
    @Mock PacProxyManager mPacProxyManager;
    @Mock BpfNetMaps mBpfNetMaps;
    @Mock CarrierPrivilegeAuthenticator mCarrierPrivilegeAuthenticator;
    @Mock TetheringManager mTetheringManager;
    @Mock BroadcastOptionsShim mBroadcastOptionsShim;
    @Mock ActivityManager mActivityManager;
    @Mock DestroySocketsWrapper mDestroySocketsWrapper;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock KeepaliveTracker.Dependencies mMockKeepaliveTrackerDependencies;
    @Mock SatelliteAccessController mSatelliteAccessController;

    // BatteryStatsManager is final and cannot be mocked with regular mockito, so just mock the
    // underlying binder calls.
    final IBatteryStats mIBatteryStats = mock(IBatteryStats.class);
    final BatteryStatsManager mBatteryStatsManager = new BatteryStatsManager(mIBatteryStats);

    private ArgumentCaptor<ResolverParamsParcel> mResolverParamsParcelCaptor =
            ArgumentCaptor.forClass(ResolverParamsParcel.class);

    // This class exists to test bindProcessToNetwork and getBoundNetworkForProcess. These methods
    // do not go through ConnectivityService but talk to netd directly, so they don't automatically
    // reflect the state of our test ConnectivityService.
    private class WrappedConnectivityManager extends ConnectivityManager {
        private Network mFakeBoundNetwork;

        public synchronized boolean bindProcessToNetwork(Network network) {
            mFakeBoundNetwork = network;
            return true;
        }

        public synchronized Network getBoundNetworkForProcess() {
            return mFakeBoundNetwork;
        }

        public WrappedConnectivityManager(Context context, ConnectivityService service) {
            super(context, service);
        }
    }

    private class MockContext extends BroadcastInterceptingContext {
        private final MockContentResolver mContentResolver;

        @Spy private Resources mInternalResources;
        private final LinkedBlockingQueue<Intent> mStartedActivities = new LinkedBlockingQueue<>();

        // Map of permission name -> PermissionManager.Permission_{GRANTED|DENIED} constant
        // For permissions granted across the board, the key is only the permission name.
        // For permissions only granted to a combination of uid/pid, the key
        // is "<permission name>,<pid>,<uid>". PID+UID permissons have priority over generic ones.
        private final HashMap<String, Integer> mMockedPermissions = new HashMap<>();

        private void mockStringResource(int resId) {
            doAnswer((inv) -> {
                return "Mock string resource ID=" + inv.getArgument(0);
            }).when(mInternalResources).getString(resId);
        }

        MockContext(Context base, ContentProvider settingsProvider) {
            super(base);

            mInternalResources = spy(base.getResources());
            doReturn(new String[] {
                    "wifi,1,1,1,-1,true",
                    "mobile,0,0,0,-1,true",
                    "mobile_mms,2,0,2,60000,true",
                    "mobile_supl,3,0,2,60000,true",
            }).when(mInternalResources)
                    .getStringArray(com.android.internal.R.array.networkAttributes);

            final int[] stringResourcesToMock = new int[] {
                com.android.internal.R.string.config_customVpnAlwaysOnDisconnectedDialogComponent,
                com.android.internal.R.string.vpn_lockdown_config,
                com.android.internal.R.string.vpn_lockdown_connected,
                com.android.internal.R.string.vpn_lockdown_connecting,
                com.android.internal.R.string.vpn_lockdown_disconnected,
                com.android.internal.R.string.vpn_lockdown_error,
            };
            for (int resId : stringResourcesToMock) {
                mockStringResource(resId);
            }

            mContentResolver = new MockContentResolver();
            mContentResolver.addProvider(Settings.AUTHORITY, settingsProvider);
        }

        @Override
        public void startActivityAsUser(Intent intent, UserHandle handle) {
            mStartedActivities.offer(intent);
        }

        public Intent expectStartActivityIntent(int timeoutMs) {
            Intent intent = null;
            try {
                intent = mStartedActivities.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {}
            assertNotNull("Did not receive sign-in intent after " + timeoutMs + "ms", intent);
            return intent;
        }

        public void expectNoStartActivityIntent(int timeoutMs) {
            try {
                assertNull("Received unexpected Intent to start activity",
                        mStartedActivities.poll(timeoutMs, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {}
        }

        @Override
        public ComponentName startService(Intent service) {
            final String action = service.getAction();
            if (!VpnConfig.SERVICE_INTERFACE.equals(action)
                    && !ConstantsShim.ACTION_VPN_MANAGER_EVENT.equals(action)) {
                fail("Attempt to start unknown service, action=" + action);
            }
            return new ComponentName(service.getPackage(), "com.android.test.Service");
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.CONNECTIVITY_SERVICE.equals(name)) return mCm;
            if (Context.NOTIFICATION_SERVICE.equals(name)) return mNotificationManager;
            if (Context.USER_SERVICE.equals(name)) return mUserManager;
            if (Context.ALARM_SERVICE.equals(name)) return mAlarmManager;
            if (Context.LOCATION_SERVICE.equals(name)) return mLocationManager;
            if (Context.APP_OPS_SERVICE.equals(name)) return mAppOpsManager;
            if (Context.TELEPHONY_SERVICE.equals(name)) return mTelephonyManager;
            if (Context.ETHERNET_SERVICE.equals(name)) return mEthernetManager;
            if (Context.NETWORK_POLICY_SERVICE.equals(name)) return mNetworkPolicyManager;
            if (Context.DEVICE_POLICY_SERVICE.equals(name)) return mDevicePolicyManager;
            if (Context.SYSTEM_CONFIG_SERVICE.equals(name)) return mSystemConfigManager;
            if (Context.NETWORK_STATS_SERVICE.equals(name)) return mStatsManager;
            if (Context.BATTERY_STATS_SERVICE.equals(name)) return mBatteryStatsManager;
            if (Context.PAC_PROXY_SERVICE.equals(name)) return mPacProxyManager;
            if (Context.TETHERING_SERVICE.equals(name)) return mTetheringManager;
            if (Context.ACTIVITY_SERVICE.equals(name)) return mActivityManager;
            if (Context.TELEPHONY_SUBSCRIPTION_SERVICE.equals(name)) return mSubscriptionManager;
            // StatsManager is final and can't be mocked, and uses static methods for mostly
            // everything. The simplest fix is to return null and not have metrics in tests.
            if (Context.STATS_MANAGER.equals(name)) return null;
            return super.getSystemService(name);
        }

        final HashMap<UserHandle, UserManager> mUserManagers = new HashMap<>();
        @Override
        public Context createContextAsUser(UserHandle user, int flags) {
            final Context asUser = mock(Context.class, AdditionalAnswers.delegatesTo(this));
            doReturn(user).when(asUser).getUser();
            doAnswer((inv) -> {
                final UserManager um = mUserManagers.computeIfAbsent(user,
                        u -> mock(UserManager.class, AdditionalAnswers.delegatesTo(mUserManager)));
                return um;
            }).when(asUser).getSystemService(Context.USER_SERVICE);
            return asUser;
        }

        public void setWorkProfile(@NonNull final UserHandle userHandle, boolean value) {
            // This relies on all contexts for a given user returning the same UM mock
            final UserManager umMock = createContextAsUser(userHandle, 0 /* flags */)
                    .getSystemService(UserManager.class);
            doReturn(value).when(umMock).isManagedProfile();
            doReturn(value).when(mUserManager).isManagedProfile(eq(userHandle.getIdentifier()));
        }

        public void setDeviceOwner(@NonNull final UserHandle userHandle, String value) {
            // This relies on all contexts for a given user returning the same UM mock
            final DevicePolicyManager dpmMock = createContextAsUser(userHandle, 0 /* flags */)
                    .getSystemService(DevicePolicyManager.class);
            doReturn(value).when(dpmMock).getDeviceOwner();
            doReturn(value).when(mDevicePolicyManager).getDeviceOwner();
        }

        @Override
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }

        @Override
        public Resources getResources() {
            return mInternalResources;
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        private int checkMockedPermission(String permission, int pid, int uid,
                Function3<String, Integer, Integer, Integer> ifAbsent /* perm, uid, pid -> int */) {
            final Integer granted = mMockedPermissions.get(permission + "," + pid + "," + uid);
            if (null != granted) {
                return granted;
            }
            final Integer allGranted = mMockedPermissions.get(permission);
            if (null != allGranted) {
                return allGranted;
            }
            return ifAbsent.apply(permission, pid, uid);
        }

        @Override
        public int checkPermission(String permission, int pid, int uid) {
            return checkMockedPermission(permission, pid, uid,
                    (perm, p, u) -> super.checkPermission(perm, p, u));
        }

        @Override
        public int checkCallingOrSelfPermission(String permission) {
            return checkMockedPermission(permission, Process.myPid(), Process.myUid(),
                    (perm, p, u) -> super.checkCallingOrSelfPermission(perm));
        }

        @Override
        public void enforceCallingOrSelfPermission(String permission, String message) {
            final Integer granted = checkMockedPermission(permission,
                    Process.myPid(), Process.myUid(),
                    (perm, p, u) -> {
                        super.enforceCallingOrSelfPermission(perm, message);
                        // enforce will crash if the permission is not granted
                        return PERMISSION_GRANTED;
                    });

            if (!granted.equals(PERMISSION_GRANTED)) {
                throw new SecurityException("[Test] permission denied: " + permission);
            }
        }

        /**
         * Mock checks for the specified permission, and have them behave as per {@code granted}.
         *
         * This will apply to all calls no matter what the checked UID and PID are.
         *
         * <p>Passing null reverts to default behavior, which does a real permission check on the
         * test package.
         * @param granted One of {@link PackageManager#PERMISSION_GRANTED} or
         *                {@link PackageManager#PERMISSION_DENIED}.
         */
        public void setPermission(String permission, Integer granted) {
            mMockedPermissions.put(permission, granted);
        }

        /**
         * Mock checks for the specified permission, and have them behave as per {@code granted}.
         *
         * This will only apply to the passed UID and PID.
         *
         * <p>Passing null reverts to default behavior, which does a real permission check on the
         * test package.
         * @param granted One of {@link PackageManager#PERMISSION_GRANTED} or
         *                {@link PackageManager#PERMISSION_DENIED}.
         */
        public void setPermission(String permission, int pid, int uid, Integer granted) {
            final String key = permission + "," + pid + "," + uid;
            mMockedPermissions.put(key, granted);
        }

        @Override
        public Intent registerReceiverForAllUsers(@Nullable BroadcastReceiver receiver,
                @NonNull IntentFilter filter, @Nullable String broadcastPermission,
                @Nullable Handler scheduler) {
            // TODO: ensure MultinetworkPolicyTracker's BroadcastReceiver is tested; just returning
            // null should not pass the test
            return null;
        }

        @Override
        public void sendStickyBroadcast(Intent intent, Bundle options) {
            // Verify that delivery group policy APIs were used on U.
            if (mDeps.isAtLeastU() && CONNECTIVITY_ACTION.equals(intent.getAction())) {
                final NetworkInfo ni = intent.getParcelableExtra(EXTRA_NETWORK_INFO,
                        NetworkInfo.class);
                try {
                    verify(mBroadcastOptionsShim).setDeliveryGroupPolicy(
                            eq(ConstantsShim.DELIVERY_GROUP_POLICY_MOST_RECENT));
                    verify(mBroadcastOptionsShim).setDeliveryGroupMatchingKey(
                            eq(CONNECTIVITY_ACTION),
                            eq(createDeliveryGroupKeyForConnectivityAction(ni)));
                    verify(mBroadcastOptionsShim).setDeferralPolicy(
                            eq(ConstantsShim.DEFERRAL_POLICY_UNTIL_ACTIVE));
                } catch (UnsupportedApiLevelException e) {
                    throw new RuntimeException(e);
                }
            }
            super.sendStickyBroadcast(intent, options);
        }

        private final ArrayTrackRecord<Intent>.ReadHead mOrderedBroadcastAsUserHistory =
                new ArrayTrackRecord<Intent>().newReadHead();

        public void expectDataActivityBroadcast(int deviceType, boolean isActive, long tsNanos) {
            assertNotNull(mOrderedBroadcastAsUserHistory.poll(BROADCAST_TIMEOUT_MS,
                    intent -> intent.getAction().equals(ACTION_DATA_ACTIVITY_CHANGE)
                            && intent.getIntExtra(EXTRA_DEVICE_TYPE, -1) == deviceType
                            && intent.getBooleanExtra(EXTRA_IS_ACTIVE, !isActive) == isActive
                            && intent.getLongExtra(EXTRA_REALTIME_NS, -1) == tsNanos
            ));
        }

        @Override
        public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
                String receiverPermission, BroadcastReceiver resultReceiver,
                Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
            mOrderedBroadcastAsUserHistory.add(intent);
        }
    }

    // This was only added in the T SDK, but this test needs to build against the R+S SDKs, too.
    private static int toSdkSandboxUid(int appUid) {
        final int firstSdkSandboxUid = 20000;
        return appUid + (firstSdkSandboxUid - Process.FIRST_APPLICATION_UID);
    }

    // Create the list of ranges for the primary user (User 0), excluding excludedUids.
    private static List<Range<Integer>> intRangesPrimaryExcludingUids(List<Integer> excludedUids) {
        final List<Integer> excludedUidsList = new ArrayList<>(excludedUids);
        // Uid 0 is always excluded
        if (!excludedUidsList.contains(0)) {
            excludedUidsList.add(0);
        }
        return intRangesExcludingUids(PRIMARY_USER, excludedUidsList);
    }

    private static List<Range<Integer>> intRangesExcludingUids(int userId,
            List<Integer> excludedAppIds) {
        final List<Integer> excludedUids = CollectionUtils.map(excludedAppIds,
                appId -> UserHandle.getUid(userId, appId));
        final int userBase = userId * UserHandle.PER_USER_RANGE;
        final int maxUid = userBase + UserHandle.PER_USER_RANGE - 1;

        int start = userBase;
        Collections.sort(excludedUids);
        final List<Range<Integer>> ranges = new ArrayList<>();
        for (int excludedUid : excludedUids) {
            if (excludedUid == start) {
                start++;
            } else {
                ranges.add(new Range<>(start, excludedUid - 1));
                start = excludedUid + 1;
            }
        }
        if (start <= maxUid) {
            ranges.add(new Range<>(start, maxUid));
        }

        return ranges;
    }

    private void waitForIdle() {
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);
        waitForIdle(mCellAgent, TIMEOUT_MS);
        waitForIdle(mWiFiAgent, TIMEOUT_MS);
        waitForIdle(mEthernetAgent, TIMEOUT_MS);
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);
        HandlerUtils.waitForIdle(ConnectivityThread.get(), TIMEOUT_MS);
    }

    private void waitForIdle(TestNetworkAgentWrapper agent, long timeoutMs) {
        if (agent == null) {
            return;
        }
        agent.waitForIdle(timeoutMs);
    }

    @Test
    public void testWaitForIdle() throws Exception {
        final int attempts = 50;  // Causes the test to take about 200ms on bullhead-eng.

        // Tests that waitForIdle returns immediately if the service is already idle.
        for (int i = 0; i < attempts; i++) {
            waitForIdle();
        }

        // Bring up a network that we can use to send messages to ConnectivityService.
        ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTED);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        b.expectBroadcast();
        Network n = mWiFiAgent.getNetwork();
        assertNotNull(n);

        // Tests that calling waitForIdle waits for messages to be processed.
        for (int i = 0; i < attempts; i++) {
            mWiFiAgent.setSignalStrength(i);
            waitForIdle();
            assertEquals(i, mCm.getNetworkCapabilities(n).getSignalStrength());
        }
    }

    // This test has an inherent race condition in it, and cannot be enabled for continuous testing
    // or presubmit tests. It is kept for manual runs and documentation purposes.
    @Ignore
    public void verifyThatNotWaitingForIdleCausesRaceConditions() throws Exception {
        // Bring up a network that we can use to send messages to ConnectivityService.
        ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTED);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        b.expectBroadcast();
        Network n = mWiFiAgent.getNetwork();
        assertNotNull(n);

        // Ensure that not calling waitForIdle causes a race condition.
        final int attempts = 50;  // Causes the test to take about 200ms on bullhead-eng.
        for (int i = 0; i < attempts; i++) {
            mWiFiAgent.setSignalStrength(i);
            if (i != mCm.getNetworkCapabilities(n).getSignalStrength()) {
                // We hit a race condition, as expected. Pass the test.
                return;
            }
        }

        // No race? There is a bug in this test.
        fail("expected race condition at least once in " + attempts + " attempts");
    }

    private class TestNetworkAgentWrapper extends NetworkAgentWrapper {
        // Note : Please do not add any new instrumentation here. If you need new instrumentation,
        // please add it in CSAgentWrapper and use subclasses of CSTest instead of adding more
        // tools in ConnectivityServiceTest.
        private static final int VALIDATION_RESULT_INVALID = 0;

        private static final long DATA_STALL_TIMESTAMP = 10L;
        private static final int DATA_STALL_DETECTION_METHOD = 1;

        private INetworkMonitor mNetworkMonitor;
        private INetworkMonitorCallbacks mNmCallbacks;
        private int mNmValidationResult = VALIDATION_RESULT_INVALID;
        private int mProbesCompleted;
        private int mProbesSucceeded;
        private String mNmValidationRedirectUrl = null;
        private boolean mNmProvNotificationRequested = false;

        private final ConditionVariable mNetworkStatusReceived = new ConditionVariable();
        // Contains the redirectUrl from networkStatus(). Before reading, wait for
        // mNetworkStatusReceived.
        private String mRedirectUrl;

        TestNetworkAgentWrapper(int transport) throws Exception {
            this(transport, new LinkProperties(), null /* ncTemplate */, null /* provider */, null);
        }

        TestNetworkAgentWrapper(int transport, LinkProperties linkProperties)
                throws Exception {
            this(transport, linkProperties, null /* ncTemplate */, null /* provider */, null);
        }

        private TestNetworkAgentWrapper(int transport, LinkProperties linkProperties,
                NetworkCapabilities ncTemplate) throws Exception {
            this(transport, linkProperties, ncTemplate, null /* provider */, null);
        }

        private TestNetworkAgentWrapper(int transport, LinkProperties linkProperties,
                NetworkCapabilities ncTemplate, NetworkProvider provider) throws Exception {
            this(transport, linkProperties, ncTemplate, provider /* provider */, null);
        }

        private TestNetworkAgentWrapper(int transport, NetworkAgentWrapper.Callbacks callbacks)
                throws Exception {
            this(transport, new LinkProperties(), null /* ncTemplate */, null /* provider */,
                    callbacks);
        }

        private TestNetworkAgentWrapper(int transport, LinkProperties linkProperties,
                NetworkCapabilities ncTemplate, NetworkProvider provider,
                NetworkAgentWrapper.Callbacks callbacks) throws Exception {
            super(transport, linkProperties, ncTemplate, provider, callbacks, mServiceContext);
            mCreatedAgents.add(this);

            // Waits for the NetworkAgent to be registered, which includes the creation of the
            // NetworkMonitor.
            waitForIdle(TIMEOUT_MS);
            HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);
            HandlerUtils.waitForIdle(ConnectivityThread.get(), TIMEOUT_MS);
        }

        class TestInstrumentedNetworkAgent extends InstrumentedNetworkAgent {
            TestInstrumentedNetworkAgent(NetworkAgentWrapper wrapper, LinkProperties lp,
                    NetworkAgentConfig nac, NetworkProvider provider) {
                super(wrapper, lp, nac, provider);
            }

            @Override
            public void networkStatus(int status, String redirectUrl) {
                mRedirectUrl = redirectUrl;
                mNetworkStatusReceived.open();
            }

        }

        @Override
        protected InstrumentedNetworkAgent makeNetworkAgent(LinkProperties linkProperties,
                NetworkAgentConfig nac, NetworkProvider provider) throws Exception {
            mNetworkMonitor = mock(INetworkMonitor.class);

            final Answer validateAnswer = inv -> {
                new Thread(ignoreExceptions(this::onValidationRequested)).start();
                return null;
            };

            doAnswer(validateAnswer).when(mNetworkMonitor).notifyNetworkConnected(any(), any());
            doAnswer(validateAnswer).when(mNetworkMonitor).notifyNetworkConnectedParcel(any());
            doAnswer(validateAnswer).when(mNetworkMonitor).forceReevaluation(anyInt());

            final ArgumentCaptor<Network> nmNetworkCaptor = ArgumentCaptor.forClass(Network.class);
            final ArgumentCaptor<INetworkMonitorCallbacks> nmCbCaptor =
                    ArgumentCaptor.forClass(INetworkMonitorCallbacks.class);
            doNothing().when(mNetworkStack).makeNetworkMonitor(
                    nmNetworkCaptor.capture(),
                    any() /* name */,
                    nmCbCaptor.capture());

            final InstrumentedNetworkAgent na =
                    new TestInstrumentedNetworkAgent(this, linkProperties, nac, provider);

            assertEquals(na.getNetwork().netId, nmNetworkCaptor.getValue().netId);
            mNmCallbacks = nmCbCaptor.getValue();

            mNmCallbacks.onNetworkMonitorCreated(mNetworkMonitor);

            return na;
        }

        private void onValidationRequested() throws Exception {
            if (mDeps.isAtLeastT()) {
                verify(mNetworkMonitor).notifyNetworkConnectedParcel(any());
            } else {
                verify(mNetworkMonitor).notifyNetworkConnected(any(), any());
            }
            if (mNmProvNotificationRequested
                    && ((mNmValidationResult & NETWORK_VALIDATION_RESULT_VALID) != 0)) {
                mNmCallbacks.hideProvisioningNotification();
                mNmProvNotificationRequested = false;
            }

            mNmCallbacks.notifyProbeStatusChanged(mProbesCompleted, mProbesSucceeded);
            final NetworkTestResultParcelable p = new NetworkTestResultParcelable();
            p.result = mNmValidationResult;
            p.probesAttempted = mProbesCompleted;
            p.probesSucceeded = mProbesSucceeded;
            p.redirectUrl = mNmValidationRedirectUrl;
            p.timestampMillis = TIMESTAMP;
            mNmCallbacks.notifyNetworkTestedWithExtras(p);

            if (mNmValidationRedirectUrl != null) {
                mNmCallbacks.showProvisioningNotification(
                        "test_provisioning_notif_action", TEST_PACKAGE_NAME);
                mNmProvNotificationRequested = true;
            }
        }

        /**
         * Connect without adding any internet capability.
         */
        public void connectWithoutInternet() {
            super.connect();
        }

        /**
         * Transition this NetworkAgent to CONNECTED state with NET_CAPABILITY_INTERNET.
         * @param validated Indicate if network should pretend to be validated.
         */
        public void connect(boolean validated) {
            connect(validated, true, false /* privateDnsProbeSent */);
        }

        /**
         * Transition this NetworkAgent to CONNECTED state.
         *
         * @param validated Indicate if network should pretend to be validated.
         *                  Note that if this is true, this method will mock the NetworkMonitor
         *                  probes to pretend the network is invalid after it validated once,
         *                  so that subsequent attempts (with mNetworkMonitor.forceReevaluation)
         *                  will fail unless setNetworkValid is called again manually.
         * @param hasInternet Indicate if network should pretend to have NET_CAPABILITY_INTERNET.
         * @param privateDnsProbeSent whether the private DNS probe should be considered to have
         *                            been sent, assuming |validated| is true.
         *                            If |validated| is false, |privateDnsProbeSent| is not used.
         *                            If |validated| is true and |privateDnsProbeSent| is false,
         *                            the probe has not been sent.
         *                            If |validated| is true and |privateDnsProbeSent| is true,
         *                            the probe has been sent and has succeeded. When the NM probes
         *                            are mocked to be invalid, private DNS is the reason this
         *                            network is invalid ; see @param |validated|.
         */
        public void connect(boolean validated, boolean hasInternet,
                boolean privateDnsProbeSent) {
            final ConditionVariable validatedCv = new ConditionVariable();
            final ConditionVariable capsChangedCv = new ConditionVariable();
            final NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(getNetworkCapabilities().getTransportTypes()[0])
                    .clearCapabilities()
                    .build();
            if (validated) {
                setNetworkValid(privateDnsProbeSent);
            }
            final NetworkCallback callback = new NetworkCallback() {
                public void onCapabilitiesChanged(Network network,
                        NetworkCapabilities networkCapabilities) {
                    if (network.equals(getNetwork())) {
                        capsChangedCv.open();
                        if (networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)) {
                            validatedCv.open();
                        }
                    }
                }
            };
            mCm.registerNetworkCallback(request, callback);

            if (hasInternet) {
                addCapability(NET_CAPABILITY_INTERNET);
            }

            connectWithoutInternet();
            waitFor(capsChangedCv);

            if (validated) {
                // Wait for network to validate.
                waitFor(validatedCv);
                setNetworkInvalid(privateDnsProbeSent);
            }
            mCm.unregisterNetworkCallback(callback);
        }

        public void connectWithCaptivePortal(String redirectUrl,
                boolean privateDnsProbeSent) {
            setNetworkPortal(redirectUrl, privateDnsProbeSent);
            connect(false, true /* hasInternet */, privateDnsProbeSent);
        }

        public void connectWithPartialConnectivity() {
            setNetworkPartial();
            connect(false);
        }

        public void connectWithPartialValidConnectivity(boolean privateDnsProbeSent) {
            setNetworkPartialValid(privateDnsProbeSent);
            connect(false, true /* hasInternet */, privateDnsProbeSent);
        }

        void setNetworkValid(boolean privateDnsProbeSent) {
            mNmValidationResult = NETWORK_VALIDATION_RESULT_VALID;
            mNmValidationRedirectUrl = null;
            int probesSucceeded = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS;
            if (privateDnsProbeSent) {
                probesSucceeded |= NETWORK_VALIDATION_PROBE_PRIVDNS;
            }
            // The probesCompleted equals to probesSucceeded for the case of valid network, so put
            // the same value into two different parameter of the method.
            setProbesStatus(probesSucceeded, probesSucceeded);
        }

        void setNetworkInvalid(boolean invalidBecauseOfPrivateDns) {
            mNmValidationResult = VALIDATION_RESULT_INVALID;
            mNmValidationRedirectUrl = null;
            int probesCompleted = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS
                    | NETWORK_VALIDATION_PROBE_HTTP;
            int probesSucceeded = 0;
            // If |invalidBecauseOfPrivateDns| is true, it means the network is invalid because
            // NetworkMonitor tried to validate the private DNS but failed. Therefore it
            // didn't get a chance to try the HTTP probe.
            if (invalidBecauseOfPrivateDns) {
                probesCompleted &= ~NETWORK_VALIDATION_PROBE_HTTP;
                probesSucceeded = probesCompleted;
                probesCompleted |= NETWORK_VALIDATION_PROBE_PRIVDNS;
            }
            setProbesStatus(probesCompleted, probesSucceeded);
        }

        void setNetworkPortal(String redirectUrl, boolean privateDnsProbeSent) {
            setNetworkInvalid(privateDnsProbeSent);
            mNmValidationRedirectUrl = redirectUrl;
            // Suppose the portal is found when NetworkMonitor probes NETWORK_VALIDATION_PROBE_HTTP
            // in the beginning, so the NETWORK_VALIDATION_PROBE_HTTPS hasn't probed yet.
            int probesCompleted = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP;
            int probesSucceeded = VALIDATION_RESULT_INVALID;
            if (privateDnsProbeSent) {
                probesCompleted |= NETWORK_VALIDATION_PROBE_PRIVDNS;
            }
            setProbesStatus(probesCompleted, probesSucceeded);
        }

        void setNetworkPartial() {
            mNmValidationResult = NETWORK_VALIDATION_RESULT_PARTIAL;
            mNmValidationRedirectUrl = null;
            int probesCompleted = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS
                    | NETWORK_VALIDATION_PROBE_FALLBACK;
            int probesSucceeded = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_FALLBACK;
            setProbesStatus(probesCompleted, probesSucceeded);
        }

        void setNetworkPartialValid(boolean privateDnsProbeSent) {
            setNetworkPartial();
            mNmValidationResult |= NETWORK_VALIDATION_RESULT_VALID;
            mNmValidationRedirectUrl = null;
            int probesCompleted = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTPS
                    | NETWORK_VALIDATION_PROBE_HTTP;
            int probesSucceeded = NETWORK_VALIDATION_PROBE_DNS | NETWORK_VALIDATION_PROBE_HTTP;
            // Assume the partial network cannot pass the private DNS validation as well, so only
            // add NETWORK_VALIDATION_PROBE_DNS in probesCompleted but not probesSucceeded.
            if (privateDnsProbeSent) {
                probesCompleted |= NETWORK_VALIDATION_PROBE_PRIVDNS;
            }
            setProbesStatus(probesCompleted, probesSucceeded);
        }

        void setProbesStatus(int probesCompleted, int probesSucceeded) {
            mProbesCompleted = probesCompleted;
            mProbesSucceeded = probesSucceeded;
        }

        void notifyCapportApiDataChanged(CaptivePortalData data) {
            try {
                mNmCallbacks.notifyCaptivePortalDataChanged(data);
            } catch (RemoteException e) {
                throw new AssertionError("This cannot happen", e);
            }
        }

        public String waitForRedirectUrl() {
            assertTrue(mNetworkStatusReceived.block(TIMEOUT_MS));
            return mRedirectUrl;
        }

        public void expectDisconnected() {
            expectDisconnected(TIMEOUT_MS);
        }

        public void expectPreventReconnectReceived() {
            expectPreventReconnectReceived(TIMEOUT_MS);
        }

        void notifyDataStallSuspected() throws Exception {
            final DataStallReportParcelable p = new DataStallReportParcelable();
            p.detectionMethod = DATA_STALL_DETECTION_METHOD;
            p.timestampMillis = DATA_STALL_TIMESTAMP;
            mNmCallbacks.notifyDataStallSuspected(p);
        }
    }

    /**
     * A NetworkFactory that allows to wait until any in-flight NetworkRequest add or remove
     * operations have been processed and test for them.
     */
    private static class MockNetworkFactory extends NetworkFactory {
        // Note : Please do not add any new instrumentation here. If you need new instrumentation,
        // please add it in CSTest and use subclasses of CSTest instead of adding more
        // tools in ConnectivityServiceTest.
        private final AtomicBoolean mNetworkStarted = new AtomicBoolean(false);

        static class RequestEntry {
            @NonNull
            public final NetworkRequest request;

            RequestEntry(@NonNull final NetworkRequest request) {
                this.request = request;
            }

            static final class Add extends RequestEntry {
                Add(@NonNull final NetworkRequest request) {
                    super(request);
                }
            }

            static final class Remove extends RequestEntry {
                Remove(@NonNull final NetworkRequest request) {
                    super(request);
                }
            }

            @Override
            public String toString() {
                return "RequestEntry [ " + getClass().getName() + " : " + request + " ]";
            }
        }

        // History of received requests adds and removes.
        private final ArrayTrackRecord<RequestEntry>.ReadHead mRequestHistory =
                new ArrayTrackRecord<RequestEntry>().newReadHead();

        private static <T> T failIfNull(@Nullable final T obj, @Nullable final String message) {
            if (null == obj) fail(null != message ? message : "Must not be null");
            return obj;
        }

        public RequestEntry.Add expectRequestAdd() {
            return failIfNull((RequestEntry.Add) mRequestHistory.poll(TIMEOUT_MS,
                    it -> it instanceof RequestEntry.Add), "Expected request add");
        }

        public void expectRequestAdds(final int count) {
            for (int i = count; i > 0; --i) {
                expectRequestAdd();
            }
        }

        public RequestEntry.Remove expectRequestRemove() {
            return failIfNull((RequestEntry.Remove) mRequestHistory.poll(TIMEOUT_MS,
                    it -> it instanceof RequestEntry.Remove), "Expected request remove");
        }

        public void expectRequestRemoves(final int count) {
            for (int i = count; i > 0; --i) {
                expectRequestRemove();
            }
        }

        // Used to collect the networks requests managed by this factory. This is a duplicate of
        // the internal information stored in the NetworkFactory (which is private).
        private SparseArray<NetworkRequest> mNetworkRequests = new SparseArray<>();
        private final HandlerThread mHandlerSendingRequests;

        public MockNetworkFactory(Looper looper, Context context, String logTag,
                NetworkCapabilities filter, HandlerThread threadSendingRequests) {
            super(looper, context, logTag, filter);
            mHandlerSendingRequests = threadSendingRequests;
        }

        public int getMyRequestCount() {
            return getRequestCount();
        }

        protected void startNetwork() {
            mNetworkStarted.set(true);
        }

        protected void stopNetwork() {
            mNetworkStarted.set(false);
        }

        public boolean getMyStartRequested() {
            return mNetworkStarted.get();
        }


        @Override
        protected void needNetworkFor(NetworkRequest request) {
            mNetworkRequests.put(request.requestId, request);
            super.needNetworkFor(request);
            mRequestHistory.add(new RequestEntry.Add(request));
        }

        @Override
        protected void releaseNetworkFor(NetworkRequest request) {
            mNetworkRequests.remove(request.requestId);
            super.releaseNetworkFor(request);
            mRequestHistory.add(new RequestEntry.Remove(request));
        }

        public void assertRequestCountEquals(final int count) {
            assertEquals(count, getMyRequestCount());
        }

        // Trigger releasing the request as unfulfillable
        public void triggerUnfulfillable(NetworkRequest r) {
            super.releaseRequestAsUnfulfillableByAnyFactory(r);
        }

        public void assertNoRequestChanged() {
            // Make sure there are no remaining requests unaccounted for.
            HandlerUtils.waitForIdle(mHandlerSendingRequests, TIMEOUT_MS);
            assertNull(mRequestHistory.poll(0, r -> true));
        }
    }

    private Set<UidRange> uidRangesForUids(int... uids) {
        final ArraySet<UidRange> ranges = new ArraySet<>();
        for (final int uid : uids) {
            ranges.add(new UidRange(uid, uid));
        }
        return ranges;
    }

    private Set<UidRange> uidRangesForUids(Collection<Integer> uids) {
        return uidRangesForUids(CollectionUtils.toIntArray(uids));
    }

    // Helper class to mock vpn interaction.
    private class MockVpn implements TestableNetworkCallback.HasNetwork {
        // Note : Please do not add any new instrumentation here. If you need new instrumentation,
        // please add it in CSTest and use subclasses of CSTest instead of adding more
        // tools in ConnectivityServiceTest.

        // Careful ! This is different from mNetworkAgent, because MockNetworkAgent does
        // not inherit from NetworkAgent.
        private TestNetworkAgentWrapper mMockNetworkAgent;
        // Initialize a stored NetworkCapabilities following the defaults of VPN. The TransportInfo
        // should at least be updated to a valid VPN type before usage, see registerAgent(...).
        private NetworkCapabilities mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .setTransportInfo(new VpnTransportInfo(
                        VpnManager.TYPE_VPN_NONE,
                        null /* sessionId */,
                        false /* bypassable */,
                        false /* longLivedTcpConnectionsExpensive */))
                .build();
        private boolean mAgentRegistered = false;

        private int mVpnType = VpnManager.TYPE_VPN_SERVICE;
        private String mSessionKey;

        public void setUids(Set<UidRange> uids) {
            mNetworkCapabilities.setUids(UidRange.toIntRanges(uids));
            if (mAgentRegistered) {
                mMockNetworkAgent.setNetworkCapabilities(mNetworkCapabilities, true);
            }
        }

        public void setVpnType(int vpnType) {
            mVpnType = vpnType;
        }

        public Network getNetwork() {
            return (mMockNetworkAgent == null) ? null : mMockNetworkAgent.getNetwork();
        }

        public NetworkAgentConfig getNetworkAgentConfig() {
            return null == mMockNetworkAgent ? null : mMockNetworkAgent.getNetworkAgentConfig();
        }

        public int getActiveVpnType() {
            return mVpnType;
        }

        private LinkProperties makeLinkProperties() {
            final LinkProperties lp = new LinkProperties();
            lp.setInterfaceName(VPN_IFNAME);
            return lp;
        }

        private void registerAgent(boolean isAlwaysMetered, Set<UidRange> uids, LinkProperties lp)
                throws Exception {
            if (mAgentRegistered) throw new IllegalStateException("already registered");
            final String session = "MySession12345";
            setUids(uids);
            if (!isAlwaysMetered) mNetworkCapabilities.addCapability(NET_CAPABILITY_NOT_METERED);
            mNetworkCapabilities.setTransportInfo(new VpnTransportInfo(getActiveVpnType(),
                    session));
            mMockNetworkAgent = new TestNetworkAgentWrapper(TRANSPORT_VPN, lp,
                    mNetworkCapabilities);
            mMockNetworkAgent.waitForIdle(TIMEOUT_MS);

            verify(mMockNetd, times(1)).networkAddUidRangesParcel(
                    new NativeUidRangeConfig(mMockVpn.getNetwork().getNetId(),
                            toUidRangeStableParcels(uids), PREFERENCE_ORDER_VPN));
            verify(mMockNetd, never()).networkRemoveUidRangesParcel(argThat(config ->
                    mMockVpn.getNetwork().getNetId() == config.netId
                            && PREFERENCE_ORDER_VPN == config.subPriority));
            mAgentRegistered = true;
            verify(mMockNetd).networkCreate(nativeNetworkConfigVpn(getNetwork().netId,
                    !mMockNetworkAgent.isBypassableVpn(), mVpnType));
            mNetworkCapabilities.set(mMockNetworkAgent.getNetworkCapabilities());
        }

        private void registerAgent(Set<UidRange> uids) throws Exception {
            registerAgent(false /* isAlwaysMetered */, uids, makeLinkProperties());
        }

        private void connect(boolean validated, boolean hasInternet,
                boolean privateDnsProbeSent) {
            mMockNetworkAgent.connect(validated, hasInternet, privateDnsProbeSent);
        }

        private void connect(boolean validated) {
            mMockNetworkAgent.connect(validated);
        }

        private TestNetworkAgentWrapper getAgent() {
            return mMockNetworkAgent;
        }

        private void setOwnerAndAdminUid(int uid) throws Exception {
            mNetworkCapabilities.setOwnerUid(uid);
            mNetworkCapabilities.setAdministratorUids(new int[]{uid});
        }

        public void establish(LinkProperties lp, int uid, Set<UidRange> ranges, boolean validated,
                boolean hasInternet, boolean privateDnsProbeSent) throws Exception {
            setOwnerAndAdminUid(uid);
            registerAgent(false, ranges, lp);
            connect(validated, hasInternet, privateDnsProbeSent);
            waitForIdle();
        }

        public void establish(LinkProperties lp, int uid, Set<UidRange> ranges) throws Exception {
            establish(lp, uid, ranges, true, true, false);
        }

        public void establishForMyUid(LinkProperties lp) throws Exception {
            final int uid = Process.myUid();
            establish(lp, uid, uidRangesForUids(uid), true, true, false);
        }

        public void establishForMyUid(boolean validated, boolean hasInternet,
                boolean privateDnsProbeSent) throws Exception {
            final int uid = Process.myUid();
            establish(makeLinkProperties(), uid, uidRangesForUids(uid), validated, hasInternet,
                    privateDnsProbeSent);
        }

        public void establishForMyUid() throws Exception {
            establishForMyUid(makeLinkProperties());
        }

        public void sendLinkProperties(LinkProperties lp) {
            mMockNetworkAgent.sendLinkProperties(lp);
        }

        public void disconnect() {
            if (mMockNetworkAgent != null) {
                mMockNetworkAgent.disconnect();
            }
            mAgentRegistered = false;
            setUids(null);
            // Remove NET_CAPABILITY_INTERNET or MockNetworkAgent will refuse to connect later on.
            mNetworkCapabilities.removeCapability(NET_CAPABILITY_INTERNET);
        }

        private void startLegacyVpn() {
            // Do nothing.
        }

        // Mock the interaction of IkeV2VpnRunner start. In the context of ConnectivityService,
        // setVpnDefaultForUids() is the main interaction and a sessionKey is stored.
        private void startPlatformVpn() {
            mSessionKey = UUID.randomUUID().toString();
            // Assuming no disallowed applications
            final Set<Range<Integer>> ranges = UidRange.toIntRanges(Set.of(PRIMARY_UIDRANGE));
            mCm.setVpnDefaultForUids(mSessionKey, ranges);
            // Wait for vpn network preference updates.
            waitForIdle();
        }

        public void startLegacyVpnPrivileged(VpnProfile profile) {
            switch (profile.type) {
                case VpnProfile.TYPE_IKEV2_IPSEC_RSA:
                case VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS:
                case VpnProfile.TYPE_IKEV2_IPSEC_PSK:
                case VpnProfile.TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS:
                    startPlatformVpn();
                    break;
                case VpnProfile.TYPE_L2TP_IPSEC_PSK:
                case VpnProfile.TYPE_L2TP_IPSEC_RSA:
                case VpnProfile.TYPE_IPSEC_XAUTH_PSK:
                case VpnProfile.TYPE_IPSEC_XAUTH_RSA:
                case VpnProfile.TYPE_IPSEC_HYBRID_RSA:
                    startLegacyVpn();
                    break;
                default:
                    fail("Unknown VPN profile type");
            }
        }

        public void stopVpnRunnerPrivileged() {
            if (mSessionKey != null) {
                // Clear vpn network preference.
                mCm.setVpnDefaultForUids(mSessionKey, Collections.EMPTY_LIST);
                mSessionKey = null;
            }
            disconnect();
        }

        public boolean setUnderlyingNetworks(@Nullable Network[] networks) {
            if (!mAgentRegistered) return false;
            mMockNetworkAgent.setUnderlyingNetworks(
                    (networks == null) ? null : Arrays.asList(networks));
            return true;
        }
    }

    private UidRangeParcel[] toUidRangeStableParcels(final @NonNull Set<UidRange> ranges) {
        return ranges.stream().map(
                r -> new UidRangeParcel(r.start, r.stop)).toArray(UidRangeParcel[]::new);
    }

    private UidRangeParcel[] intToUidRangeStableParcels(final @NonNull Set<Integer> ranges) {
        return ranges.stream().map(r -> new UidRangeParcel(r, r)).toArray(UidRangeParcel[]::new);
    }

    private static UidRangeParcel[] intToUidRangeStableParcels(
            final @NonNull List<Range<Integer>> ranges) {
        return ranges.stream().map(
                r -> new UidRangeParcel(r.getLower(), r.getUpper())).toArray(UidRangeParcel[]::new);
    }

    private void assertVpnTransportInfo(NetworkCapabilities nc, int type) {
        assertNotNull(nc);
        final TransportInfo ti = nc.getTransportInfo();
        assertTrue("VPN TransportInfo is not a VpnTransportInfo: " + ti,
                ti instanceof VpnTransportInfo);
        assertEquals(type, ((VpnTransportInfo) ti).getType());

    }

    private void processBroadcast(Intent intent) {
        mServiceContext.sendBroadcast(intent);
        waitForIdle();
    }

    private void mockUidNetworkingBlocked() {
        doAnswer(i -> isUidBlocked(mBlockedReasons, i.getArgument(1))
        ).when(mNetworkPolicyManager).isUidNetworkingBlocked(anyInt(), anyBoolean());
    }

    private boolean isUidBlocked(int blockedReasons, boolean meteredNetwork) {
        final int blockedOnAllNetworksReason = (blockedReasons & ~BLOCKED_METERED_REASON_MASK);
        if (blockedOnAllNetworksReason != BLOCKED_REASON_NONE) {
            return true;
        }
        if (meteredNetwork) {
            return blockedReasons != BLOCKED_REASON_NONE;
        }
        return false;
    }

    private void setBlockedReasonChanged(int blockedReasons) {
        mBlockedReasons = blockedReasons;
        mPolicyCallback.onUidBlockedReasonChanged(Process.myUid(), blockedReasons);
    }

    private Nat464Xlat getNat464Xlat(NetworkAgentWrapper mna) {
        return mService.getNetworkAgentInfoForNetwork(mna.getNetwork()).clatd;
    }

    private class WrappedMultinetworkPolicyTracker extends MultinetworkPolicyTracker {
        volatile int mConfigMeteredMultipathPreference;

        WrappedMultinetworkPolicyTracker(Context c, Handler h, Runnable r) {
            super(c, h, r, new MultinetworkPolicyTrackerTestDependencies(mResources));
        }

        @Override
        public int configMeteredMultipathPreference() {
            return mConfigMeteredMultipathPreference;
        }
    }

    /**
     * Wait up to TIMEOUT_MS for {@code conditionVariable} to open.
     * Fails if TIMEOUT_MS goes by before {@code conditionVariable} opens.
     */
    static private void waitFor(ConditionVariable conditionVariable) {
        if (conditionVariable.block(TIMEOUT_MS)) {
            return;
        }
        fail("ConditionVariable was blocked for more than " + TIMEOUT_MS + "ms");
    }

    private <T> T doAsUid(final int uid, @NonNull final Supplier<T> what) {
        mDeps.setCallingUid(uid);
        try {
            return what.get();
        } finally {
            mDeps.setCallingUid(null);
        }
    }

    private void doAsUid(final int uid, @NonNull final Runnable what) {
        doAsUid(uid, () -> {
            what.run(); return Void.TYPE;
        });
    }

    private void registerNetworkCallbackAsUid(NetworkRequest request, NetworkCallback callback,
            int uid) {
        doAsUid(uid, () -> {
            mCm.registerNetworkCallback(request, callback);
        });
    }

    private void registerDefaultNetworkCallbackAsUid(@NonNull final NetworkCallback callback,
            final int uid) {
        doAsUid(uid, () -> {
            mCm.registerDefaultNetworkCallback(callback);
            waitForIdle();
        });
    }

    private void withPermission(String permission, ThrowingRunnable r) throws Exception {
        try {
            mServiceContext.setPermission(permission, PERMISSION_GRANTED);
            r.run();
        } finally {
            mServiceContext.setPermission(permission, null);
        }
    }

    private void withPermission(String permission, int pid, int uid, ThrowingRunnable r)
            throws Exception {
        try {
            mServiceContext.setPermission(permission, pid, uid, PERMISSION_GRANTED);
            r.run();
        } finally {
            mServiceContext.setPermission(permission, pid, uid, null);
        }
    }

    private static final int PRIMARY_USER = 0;
    private static final int SECONDARY_USER = 10;
    private static final int TERTIARY_USER = 11;
    private static final UidRange PRIMARY_UIDRANGE =
            UidRange.createForUser(UserHandle.of(PRIMARY_USER));
    private static final int APP1_UID = UserHandle.getUid(PRIMARY_USER, 10100);
    private static final int APP2_UID = UserHandle.getUid(PRIMARY_USER, 10101);
    private static final int VPN_UID = UserHandle.getUid(PRIMARY_USER, 10043);
    private static final UserInfo PRIMARY_USER_INFO = new UserInfo(PRIMARY_USER, "",
            UserInfo.FLAG_PRIMARY);
    private static final UserHandle PRIMARY_USER_HANDLE = new UserHandle(PRIMARY_USER);
    private static final UserHandle SECONDARY_USER_HANDLE = new UserHandle(SECONDARY_USER);
    private static final UserHandle TERTIARY_USER_HANDLE = new UserHandle(TERTIARY_USER);

    private static final int RESTRICTED_USER = 1;
    private static final UidRange RESTRICTED_USER_UIDRANGE =
            UidRange.createForUser(UserHandle.of(RESTRICTED_USER));
    private static final UserInfo RESTRICTED_USER_INFO = new UserInfo(RESTRICTED_USER, "",
            UserInfo.FLAG_RESTRICTED);
    static {
        RESTRICTED_USER_INFO.restrictedProfileParentId = PRIMARY_USER;
    }

    @Before
    public void setUp() throws Exception {
        mNetIdManager = new TestNetIdManager();

        mContext = InstrumentationRegistry.getContext();

        MockitoAnnotations.initMocks(this);

        // Note : Please do not add any new instrumentation here. If you need new instrumentation,
        // please add it in CSTest and use subclasses of CSTest instead of adding more
        // tools in ConnectivityServiceTest.
        doReturn(asList(PRIMARY_USER_INFO)).when(mUserManager).getAliveUsers();
        doReturn(asList(PRIMARY_USER_HANDLE)).when(mUserManager).getUserHandles(anyBoolean());
        doReturn(PRIMARY_USER_INFO).when(mUserManager).getUserInfo(PRIMARY_USER);
        // canHaveRestrictedProfile does not take a userId. It applies to the userId of the context
        // it was started from, i.e., PRIMARY_USER.
        doReturn(true).when(mUserManager).canHaveRestrictedProfile();
        doReturn(RESTRICTED_USER_INFO).when(mUserManager).getUserInfo(RESTRICTED_USER);

        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        doReturn(applicationInfo).when(mPackageManager)
                .getApplicationInfoAsUser(anyString(), anyInt(), any());
        doReturn(applicationInfo.targetSdkVersion).when(mPackageManager)
                .getTargetSdkVersion(anyString());
        doReturn(new int[0]).when(mSystemConfigManager).getSystemPermissionUids(anyString());

        // InstrumentationTestRunner prepares a looper, but AndroidJUnitRunner does not.
        // http://b/25897652 .
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mockDefaultPackages();
        mockHasSystemFeature(FEATURE_WIFI, true);
        mockHasSystemFeature(FEATURE_WIFI_DIRECT, true);
        mockHasSystemFeature(FEATURE_ETHERNET, true);
        doReturn(true).when(mTelephonyManager).isDataCapable();

        FakeSettingsProvider.clearSettingsProvider();
        mServiceContext = new MockContext(InstrumentationRegistry.getContext(),
                new FakeSettingsProvider());
        mServiceContext.setUseRegisteredHandlers(true);
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_GRANTED);
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_GRANTED);
        mServiceContext.setPermission(CONTROL_OEM_PAID_NETWORK_PREFERENCE, PERMISSION_GRANTED);
        mServiceContext.setPermission(PACKET_KEEPALIVE_OFFLOAD, PERMISSION_GRANTED);
        mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_GRANTED);
        mServiceContext.setPermission(READ_DEVICE_CONFIG, PERMISSION_GRANTED);

        mAlarmManagerThread = new HandlerThread("TestAlarmManager");
        mAlarmManagerThread.start();
        initAlarmManager(mAlarmManager, mAlarmManagerThread.getThreadHandler());

        mCsHandlerThread = new HandlerThread("TestConnectivityService");
        mProxyTracker = new ProxyTracker(mServiceContext, mock(Handler.class),
                16 /* EVENT_PROXY_HAS_CHANGED */);

        initMockedResources();
        final Context mockResContext = mock(Context.class);
        doReturn(mResources).when(mockResContext).getResources();
        ConnectivityResources.setResourcesContextForTest(mockResContext);
        mDeps = new ConnectivityServiceDependencies(mockResContext);
        doReturn(true).when(mMockKeepaliveTrackerDependencies)
                .isAddressTranslationEnabled(mServiceContext);
        doReturn(new ConnectivityResources(mockResContext)).when(mMockKeepaliveTrackerDependencies)
                .createConnectivityResources(mServiceContext);
        doReturn(new int[] {1, 3, 0, 0}).when(mMockKeepaliveTrackerDependencies)
                .getSupportedKeepalives(mServiceContext);
        mAutoOnOffKeepaliveDependencies =
                new AutomaticOnOffKeepaliveTrackerDependencies(mServiceContext);
        mService = new ConnectivityService(mServiceContext,
                mMockDnsResolver,
                mock(IpConnectivityLog.class),
                mMockNetd,
                mDeps);
        mService.mLingerDelayMs = TEST_LINGER_DELAY_MS;
        mService.mNascentDelayMs = TEST_NASCENT_DELAY_MS;

        final ArgumentCaptor<NetworkPolicyCallback> policyCallbackCaptor =
                ArgumentCaptor.forClass(NetworkPolicyCallback.class);
        verify(mNetworkPolicyManager).registerNetworkPolicyCallback(any(),
                policyCallbackCaptor.capture());
        mPolicyCallback = policyCallbackCaptor.getValue();

        // Create local CM before sending system ready so that we can answer
        // getSystemService() correctly.
        mCm = new WrappedConnectivityManager(InstrumentationRegistry.getContext(), mService);
        mService.systemReadyInternal();
        verify(mMockDnsResolver).registerUnsolicitedEventListener(any());

        mMockVpn = new MockVpn();
        mCm.bindProcessToNetwork(null);
        mQosCallbackTracker = mock(QosCallbackTracker.class);

        // Ensure that the default setting for Captive Portals is used for most tests
        setCaptivePortalMode(ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_PROMPT);
        setAlwaysOnNetworks(false);
        setPrivateDnsSettings(PRIVATE_DNS_MODE_OFF, "ignored.example.com");
        // Note : Please do not add any new instrumentation here. If you need new instrumentation,
        // please add it in CSTest and use subclasses of CSTest instead of adding more
        // tools in ConnectivityServiceTest.
    }

    private void initMockedResources() {
        doReturn(60000).when(mResources).getInteger(R.integer.config_networkTransitionTimeout);
        doReturn("").when(mResources).getString(R.string.config_networkCaptivePortalServerUrl);
        doReturn(new String[]{ WIFI_WOL_IFNAME }).when(mResources).getStringArray(
                R.array.config_wakeonlan_supported_interfaces);
        doReturn(new String[] { "0,1", "1,3" }).when(mResources).getStringArray(
                R.array.config_networkSupportedKeepaliveCount);
        doReturn(new String[0]).when(mResources).getStringArray(
                R.array.config_networkNotifySwitches);
        doReturn(new int[]{10, 11, 12, 14, 15}).when(mResources).getIntArray(
                R.array.config_protectedNetworks);
        // We don't test the actual notification value strings, so just return an empty array.
        // It doesn't matter what the values are as long as it's not null.
        doReturn(new String[0]).when(mResources)
                .getStringArray(R.array.network_switch_type_name);

        doReturn(R.array.config_networkSupportedKeepaliveCount).when(mResources)
                .getIdentifier(eq("config_networkSupportedKeepaliveCount"), eq("array"), any());
        doReturn(R.array.network_switch_type_name).when(mResources)
                .getIdentifier(eq("network_switch_type_name"), eq("array"), any());
        doReturn(1).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        doReturn(0).when(mResources).getInteger(R.integer.config_activelyPreferBadWifi);
        doReturn(true).when(mResources)
                .getBoolean(R.bool.config_cellular_radio_timesharing_capable);
        doReturn(PACKET_WAKEUP_MARK_MASK).when(mResources).getInteger(
                R.integer.config_networkWakeupPacketMask);
        doReturn(PACKET_WAKEUP_MARK_MASK).when(mResources).getInteger(
                R.integer.config_networkWakeupPacketMark);
    }

    class ConnectivityServiceDependencies extends ConnectivityService.Dependencies {
        final ConnectivityResources mConnRes;
        final ArraySet<Pair<Long, Integer>> mEnabledChangeIds = new ArraySet<>();

        // Note : Please do not add any new instrumentation here. If you need new instrumentation,
        // please add it in CSTest and use subclasses of CSTest instead of adding more
        // tools in ConnectivityServiceTest.
        ConnectivityServiceDependencies(final Context mockResContext) {
            mConnRes = new ConnectivityResources(mockResContext);
        }

        @Override
        public HandlerThread makeHandlerThread(@NonNull final String tag) {
            return mCsHandlerThread;
        }

        @Override
        public NetworkStackClientBase getNetworkStack() {
            return mNetworkStack;
        }

        @Override
        public ProxyTracker makeProxyTracker(final Context context, final Handler handler) {
            return mProxyTracker;
        }

        @Override
        public NetIdManager makeNetIdManager() {
            return mNetIdManager;
        }

        @Override
        public boolean queryUserAccess(final int uid, final Network network,
                final ConnectivityService cs) {
            return true;
        }

        @Override
        public MultinetworkPolicyTracker makeMultinetworkPolicyTracker(final Context c,
                final Handler h, final Runnable r) {
            if (null != mPolicyTracker) {
                throw new IllegalStateException("Multinetwork policy tracker already initialized");
            }
            mPolicyTracker = new WrappedMultinetworkPolicyTracker(mServiceContext, h, r);
            return mPolicyTracker;
        }

        @Override
        public AutomaticOnOffKeepaliveTracker makeAutomaticOnOffKeepaliveTracker(final Context c,
                final Handler h) {
            return new AutomaticOnOffKeepaliveTracker(c, h, mAutoOnOffKeepaliveDependencies);
        }

        @Override
        public ConnectivityResources getResources(final Context ctx) {
            return mConnRes;
        }

        @Override
        public LocationPermissionChecker makeLocationPermissionChecker(final Context context) {
            return new LocationPermissionChecker(context) {
                @Override
                protected int getCurrentUser() {
                    return runAsShell(CREATE_USERS, () -> super.getCurrentUser());
                }
            };
        }

        @Override
        public CarrierPrivilegeAuthenticator makeCarrierPrivilegeAuthenticator(
                @NonNull final Context context,
                @NonNull final TelephonyManager tm,
                final boolean requestRestrictedWifiEnabled,
                BiConsumer<Integer, Integer> listener) {
            return mDeps.isAtLeastT() ? mCarrierPrivilegeAuthenticator : null;
        }

        @Override
        public SatelliteAccessController makeSatelliteAccessController(
                @NonNull final Context context,
                Consumer<Set<Integer>> updateSatelliteNetworkFallbackUidCallback,
                @NonNull final Handler connectivityServiceInternalHandler) {
            return mSatelliteAccessController;
        }

        @Override
        public boolean intentFilterEquals(final PendingIntent a, final PendingIntent b) {
            return runAsShell(GET_INTENT_SENDER_INTENT, () -> a.intentFilterEquals(b));
        }

        @GuardedBy("this")
        private Integer mCallingUid = null;

        @Override
        public int getCallingUid() {
            synchronized (this) {
                if (null != mCallingUid) return mCallingUid;
                return super.getCallingUid();
            }
        }

        // Pass null for the real calling UID
        public void setCallingUid(final Integer uid) {
            synchronized (this) {
                mCallingUid = uid;
            }
        }

        @GuardedBy("this")
        private boolean mCellular464XlatEnabled = true;

        @Override
        public boolean getCellular464XlatEnabled() {
            synchronized (this) {
                return mCellular464XlatEnabled;
            }
        }

        public void setCellular464XlatEnabled(final boolean enabled) {
            synchronized (this) {
                mCellular464XlatEnabled = enabled;
            }
        }

        @GuardedBy("this")
        private Integer mConnectionOwnerUid = null;

        @Override
        public int getConnectionOwnerUid(final int protocol, final InetSocketAddress local,
                final InetSocketAddress remote) {
            synchronized (this) {
                if (null != mConnectionOwnerUid) return mConnectionOwnerUid;
                return super.getConnectionOwnerUid(protocol, local, remote);
            }
        }

        // Pass null to get the production implementation of getConnectionOwnerUid
        public void setConnectionOwnerUid(final Integer uid) {
            synchronized (this) {
                mConnectionOwnerUid = uid;
            }
        }

        final class ReportedInterfaces {
            public final Context context;
            public final String iface;
            public final int[] transportTypes;
            ReportedInterfaces(final Context c, final String i, final int[] t) {
                context = c;
                iface = i;
                transportTypes = t;
            }

            public boolean contentEquals(final Context c, final String i, final int[] t) {
                return Objects.equals(context, c) && Objects.equals(iface, i)
                        && Arrays.equals(transportTypes, t);
            }
        }

        final ArrayTrackRecord<ReportedInterfaces> mReportedInterfaceHistory =
                new ArrayTrackRecord<>();

        @Override
        public void reportNetworkInterfaceForTransports(final Context context, final String iface,
                final int[] transportTypes) {
            mReportedInterfaceHistory.add(new ReportedInterfaces(context, iface, transportTypes));
            super.reportNetworkInterfaceForTransports(context, iface, transportTypes);
        }

        @Override
        public boolean isFeatureEnabled(Context context, String name) {
            switch (name) {
                case ConnectivityFlags.NO_REMATCH_ALL_REQUESTS_ON_REGISTER:
                case ConnectivityFlags.CARRIER_SERVICE_CHANGED_USE_CALLBACK:
                    return true;
                case ConnectivityFlags.REQUEST_RESTRICTED_WIFI:
                    return true;
                case KEY_DESTROY_FROZEN_SOCKETS_VERSION:
                    return true;
                case DELAY_DESTROY_FROZEN_SOCKETS_VERSION:
                    return true;
                default:
                    return super.isFeatureEnabled(context, name);
            }
        }

        @Override
        public boolean isFeatureNotChickenedOut(Context context, String name) {
            switch (name) {
                case ALLOW_SYSUI_CONNECTIVITY_REPORTS:
                    return true;
                case LOG_BPF_RC:
                    return true;
                case ALLOW_SATALLITE_NETWORK_FALLBACK:
                    return true;
                default:
                    return super.isFeatureNotChickenedOut(context, name);
            }
        }

        public void setChangeIdEnabled(final boolean enabled, final long changeId, final int uid) {
            final Pair<Long, Integer> data = new Pair<>(changeId, uid);
            // mEnabledChangeIds is read on the handler thread and maybe the test thread, so
            // make sure both threads see it before continuing.
            visibleOnHandlerThread(mCsHandlerThread.getThreadHandler(), () -> {
                if (enabled) {
                    mEnabledChangeIds.add(data);
                } else {
                    mEnabledChangeIds.remove(data);
                }
            });
        }

        @Override
        public boolean isChangeEnabled(final long changeId, final int uid) {
            return mEnabledChangeIds.contains(new Pair<>(changeId, uid));
        }

        // In AOSP, build version codes are all over the place (e.g. at the time of this writing
        // U == V). Define custom ones.
        private static final int VERSION_UNMOCKED = -1;
        private static final int VERSION_R = 1;
        private static final int VERSION_S = 2;
        private static final int VERSION_T = 3;
        private static final int VERSION_U = 4;
        private static final int VERSION_V = 5;
        private static final int VERSION_MAX = VERSION_V;
        private int mSdkLevel = VERSION_UNMOCKED;

        private void setBuildSdk(final int sdkLevel) {
            if (sdkLevel > VERSION_MAX) {
                throw new IllegalArgumentException("setBuildSdk must not be called with"
                        + " Build.VERSION constants but Dependencies.VERSION_* constants");
            }
            visibleOnHandlerThread(mCsHandlerThread.getThreadHandler(), () -> mSdkLevel = sdkLevel);
        }

        @Override
        public boolean isAtLeastS() {
            return mSdkLevel == VERSION_UNMOCKED ? super.isAtLeastS()
                    : mSdkLevel >= VERSION_S;
        }

        @Override
        public boolean isAtLeastT() {
            return mSdkLevel == VERSION_UNMOCKED ? super.isAtLeastT()
                    : mSdkLevel >= VERSION_T;
        }

        @Override
        public boolean isAtLeastU() {
            return mSdkLevel == VERSION_UNMOCKED ? super.isAtLeastU()
                    : mSdkLevel >= VERSION_U;
        }

        @Override
        public BpfNetMaps getBpfNetMaps(Context context, INetd netd) {
            return mBpfNetMaps;
        }

        @Override
        public ClatCoordinator getClatCoordinator(INetd netd) {
            return mClatCoordinator;
        }

        final ArrayTrackRecord<Pair<String, Long>> mRateLimitHistory = new ArrayTrackRecord<>();
        final Map<String, Long> mActiveRateLimit = new HashMap<>();

        @Override
        public void enableIngressRateLimit(final String iface, final long rateInBytesPerSecond) {
            mRateLimitHistory.add(new Pair<>(iface, rateInBytesPerSecond));
            // Due to a TC limitation, the rate limit needs to be removed before it can be
            // updated. Check that this happened.
            assertEquals(-1L, (long) mActiveRateLimit.getOrDefault(iface, -1L));
            mActiveRateLimit.put(iface, rateInBytesPerSecond);
            // verify that clsact qdisc has already been created, otherwise attaching a tc police
            // filter will fail.
            try {
                verify(mMockNetd).networkAddInterface(anyInt(), eq(iface));
            } catch (RemoteException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void disableIngressRateLimit(final String iface) {
            mRateLimitHistory.add(new Pair<>(iface, -1L));
            assertNotEquals(-1L, (long) mActiveRateLimit.getOrDefault(iface, -1L));
            mActiveRateLimit.put(iface, -1L);
        }

        @Override
        public int getBpfProgramId(final int attachType) {
            return 0;
        }

        @Override
        public BroadcastOptionsShim makeBroadcastOptionsShim(BroadcastOptions options) {
            reset(mBroadcastOptionsShim);
            return mBroadcastOptionsShim;
        }

        @GuardedBy("this")
        private boolean mForceDisableCompatChangeCheck = true;

        /**
         * By default, the {@link #isChangeEnabled(long, String, UserHandle)} will always return
         * true as the mForceDisableCompatChangeCheck is true and compat change check logic is
         * never executed. The compat change check logic can be turned on by calling this method.
         * If this method is called, the
         * {@link libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges} or
         * {@link libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges} must be
         * used to turn on/off the compat change flag.
         */
        private void enableCompatChangeCheck() {
            synchronized (this) {
                mForceDisableCompatChangeCheck = false;
            }
        }

        @Override
        public boolean isChangeEnabled(long changeId,
                @NonNull final String packageName,
                @NonNull final UserHandle user) {
            synchronized (this) {
                if (mForceDisableCompatChangeCheck) {
                    return false;
                } else {
                    return super.isChangeEnabled(changeId, packageName, user);
                }
            }
        }

        // Class to be mocked and used to verify destroy sockets methods call
        public class DestroySocketsWrapper {
            public void destroyLiveTcpSockets(final Set<Range<Integer>> ranges,
                    final Set<Integer> exemptUids){}
            public void destroyLiveTcpSocketsByOwnerUids(final Set<Integer> ownerUids){}
        }

        @Override @SuppressWarnings("DirectInvocationOnMock")
        public void destroyLiveTcpSockets(final Set<Range<Integer>> ranges,
                final Set<Integer> exemptUids) {
            // Call mocked destroyLiveTcpSockets so that test can verify this method call
            mDestroySocketsWrapper.destroyLiveTcpSockets(ranges, exemptUids);
        }

        @Override @SuppressWarnings("DirectInvocationOnMock")
        public void destroyLiveTcpSocketsByOwnerUids(final Set<Integer> ownerUids) {
            // Call mocked destroyLiveTcpSocketsByOwnerUids so that test can verify this method call
            // Create copy of ownerUids so that tests can verify the correct value even if the
            // ConnectivityService update the ownerUids after this method call.
            mDestroySocketsWrapper.destroyLiveTcpSocketsByOwnerUids(new ArraySet<>(ownerUids));
        }

        final ArrayTrackRecord<Pair<Integer, Long>>.ReadHead mScheduledEvaluationTimeouts =
                new ArrayTrackRecord<Pair<Integer, Long>>().newReadHead();
        @Override
        public void scheduleEvaluationTimeout(@NonNull Handler handler,
                @NonNull final Network network, final long delayMs) {
            mScheduledEvaluationTimeouts.add(new Pair<>(network.netId, delayMs));
            super.scheduleEvaluationTimeout(handler, network, delayMs);
        }
    }

    private class AutomaticOnOffKeepaliveTrackerDependencies
            extends AutomaticOnOffKeepaliveTracker.Dependencies {
        AutomaticOnOffKeepaliveTrackerDependencies(Context context) {
            super(context);
        }

        @Override
        public boolean isTetheringFeatureNotChickenedOut(@NonNull final String name) {
            // Tests for enabling the feature are verified in AutomaticOnOffKeepaliveTrackerTest.
            // Assuming enabled here to focus on ConnectivityService tests.
            return true;
        }
        public KeepaliveTracker newKeepaliveTracker(@NonNull Context context,
                @NonNull Handler connectivityserviceHander) {
            return new KeepaliveTracker(context, connectivityserviceHander,
                    new TcpKeepaliveController(connectivityserviceHander),
                    mMockKeepaliveTrackerDependencies);
        }
    }

    private static void initAlarmManager(final AlarmManager am, final Handler alarmHandler) {
        doAnswer(inv -> {
            final long when = inv.getArgument(1);
            final WakeupMessage wakeupMsg = inv.getArgument(3);
            final Handler handler = inv.getArgument(4);

            long delayMs = when - SystemClock.elapsedRealtime();
            if (delayMs < 0) delayMs = 0;
            if (delayMs > UNREASONABLY_LONG_ALARM_WAIT_MS) {
                fail("Attempting to send msg more than " + UNREASONABLY_LONG_ALARM_WAIT_MS
                        + "ms into the future: " + delayMs);
            }
            alarmHandler.postDelayed(() -> handler.post(wakeupMsg::onAlarm), wakeupMsg /* token */,
                    delayMs);

            return null;
        }).when(am).setExact(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), anyLong(), anyString(),
                any(WakeupMessage.class), any());

        doAnswer(inv -> {
            final WakeupMessage wakeupMsg = inv.getArgument(0);
            alarmHandler.removeCallbacksAndMessages(wakeupMsg /* token */);
            return null;
        }).when(am).cancel(any(WakeupMessage.class));
    }

    @After
    public void tearDown() throws Exception {
        unregisterDefaultNetworkCallbacks();
        maybeTearDownEnterpriseNetwork();
        setAlwaysOnNetworks(false);
        if (mCellAgent != null) {
            mCellAgent.disconnect();
            mCellAgent = null;
        }
        if (mWiFiAgent != null) {
            mWiFiAgent.disconnect();
            mWiFiAgent = null;
        }
        if (mEthernetAgent != null) {
            mEthernetAgent.disconnect();
            mEthernetAgent = null;
        }

        if (mQosCallbackMockHelper != null) {
            mQosCallbackMockHelper.tearDown();
            mQosCallbackMockHelper = null;
        }
        mMockVpn.disconnect();
        waitForIdle();

        FakeSettingsProvider.clearSettingsProvider();
        ConnectivityResources.setResourcesContextForTest(null);

        for (TestNetworkAgentWrapper agent : mCreatedAgents) {
            agent.destroy();
        }
        mCreatedAgents.clear();

        mCsHandlerThread.quitSafely();
        mCsHandlerThread.join();
        mAlarmManagerThread.quitSafely();
        mAlarmManagerThread.join();
    }

    private void mockDefaultPackages() throws Exception {
        final String myPackageName = mContext.getPackageName();
        final PackageInfo myPackageInfo = mContext.getPackageManager().getPackageInfo(
                myPackageName, PackageManager.GET_PERMISSIONS);
        myPackageInfo.setLongVersionCode(9_999_999L);
        doReturn(new String[] {myPackageName}).when(mPackageManager)
                .getPackagesForUid(Binder.getCallingUid());
        doReturn(myPackageInfo).when(mPackageManager).getPackageInfoAsUser(
                eq(myPackageName), anyInt(), eq(UserHandle.getCallingUserId()));

        doReturn(asList(new PackageInfo[] {
                buildPackageInfo(/* SYSTEM */ false, APP1_UID),
                buildPackageInfo(/* SYSTEM */ false, APP2_UID),
                buildPackageInfo(/* SYSTEM */ false, VPN_UID)
        })).when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());

        final ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setPackageName(TETHERING_MODULE_NAME);
        doReturn(moduleInfo).when(mPackageManager)
                .getModuleInfo(TETHERING_MODULE_NAME, PackageManager.MODULE_APEX_NAME);
        doReturn(myPackageInfo).when(mPackageManager)
                .getPackageInfo(TETHERING_MODULE_NAME, PackageManager.MATCH_APEX);

        // Create a fake always-on VPN package.
        final int userId = UserHandle.getCallingUserId();
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = Build.VERSION_CODES.R;  // Always-on supported in N+.
        doReturn(applicationInfo).when(mPackageManager).getApplicationInfoAsUser(
                eq(ALWAYS_ON_PACKAGE), anyInt(), eq(userId));

        // Minimal mocking to keep Vpn#isAlwaysOnPackageSupported happy.
        ResolveInfo rInfo = new ResolveInfo();
        rInfo.serviceInfo = new ServiceInfo();
        rInfo.serviceInfo.metaData = new Bundle();
        final List<ResolveInfo> services = asList(new ResolveInfo[]{rInfo});
        doReturn(services).when(mPackageManager).queryIntentServicesAsUser(
                any(), eq(PackageManager.GET_META_DATA), eq(userId));
        doReturn(Process.myUid()).when(mPackageManager).getPackageUidAsUser(
                TEST_PACKAGE_NAME, userId);
        doReturn(VPN_UID).when(mPackageManager).getPackageUidAsUser(ALWAYS_ON_PACKAGE, userId);
    }

    private void verifyActiveNetwork(int transport) {
        // Test getActiveNetworkInfo()
        assertNotNull(mCm.getActiveNetworkInfo());
        assertEquals(transportToLegacyType(transport), mCm.getActiveNetworkInfo().getType());
        // Test getActiveNetwork()
        assertNotNull(mCm.getActiveNetwork());
        assertEquals(mCm.getActiveNetwork(), mCm.getActiveNetworkForUid(Process.myUid()));
        if (!NetworkCapabilities.isValidTransport(transport)) {
            throw new IllegalStateException("Unknown transport " + transport);
        }
        switch (transport) {
            case TRANSPORT_WIFI:
                assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
                break;
            case TRANSPORT_CELLULAR:
                assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
                break;
            case TRANSPORT_ETHERNET:
                assertEquals(mEthernetAgent.getNetwork(), mCm.getActiveNetwork());
                break;
            default:
                break;
        }
        // Test getNetworkInfo(Network)
        assertNotNull(mCm.getNetworkInfo(mCm.getActiveNetwork()));
        assertEquals(transportToLegacyType(transport),
                mCm.getNetworkInfo(mCm.getActiveNetwork()).getType());
        assertNotNull(mCm.getActiveNetworkInfoForUid(Process.myUid()));
        // Test getNetworkCapabilities(Network)
        assertNotNull(mCm.getNetworkCapabilities(mCm.getActiveNetwork()));
        assertTrue(mCm.getNetworkCapabilities(mCm.getActiveNetwork()).hasTransport(transport));
    }

    private void verifyNoNetwork() {
        waitForIdle();
        // Test getActiveNetworkInfo()
        assertNull(mCm.getActiveNetworkInfo());
        // Test getActiveNetwork()
        assertNull(mCm.getActiveNetwork());
        assertNull(mCm.getActiveNetworkForUid(Process.myUid()));
        // Test getAllNetworks()
        assertEmpty(mCm.getAllNetworks());
        assertEmpty(mCm.getAllNetworkStateSnapshots());
    }

    /**
     * Class to simplify expecting broadcasts using BroadcastInterceptingContext.
     * Ensures that the receiver is unregistered after the expected broadcast is received. This
     * cannot be done in the BroadcastReceiver itself because BroadcastInterceptingContext runs
     * the receivers' receive method while iterating over the list of receivers, and unregistering
     * the receiver during iteration throws ConcurrentModificationException.
     */
    private class ExpectedBroadcast extends CompletableFuture<Intent>  {
        private final BroadcastReceiver mReceiver;

        ExpectedBroadcast(BroadcastReceiver receiver) {
            mReceiver = receiver;
        }

        public Intent expectBroadcast(int timeoutMs) throws Exception {
            try {
                return get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                fail("Expected broadcast not received after " + timeoutMs + " ms");
                return null;
            } finally {
                mServiceContext.unregisterReceiver(mReceiver);
            }
        }

        public Intent expectBroadcast() throws Exception {
            return expectBroadcast(BROADCAST_TIMEOUT_MS);
        }

        public void expectNoBroadcast(int timeoutMs) throws Exception {
            waitForIdle();
            try {
                final Intent intent = get(timeoutMs, TimeUnit.MILLISECONDS);
                fail("Unexpected broadcast: " + intent.getAction() + " " + intent.getExtras());
            } catch (TimeoutException expected) {
            } finally {
                mServiceContext.unregisterReceiver(mReceiver);
            }
        }
    }

    private ExpectedBroadcast registerBroadcastReceiverThat(final String action, final int count,
            @NonNull final Predicate<Intent> filter) {
        final IntentFilter intentFilter = new IntentFilter(action);
        // AtomicReference allows receiver to access expected even though it is constructed later.
        final AtomicReference<ExpectedBroadcast> expectedRef = new AtomicReference<>();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            private int mRemaining = count;
            public void onReceive(Context context, Intent intent) {
                logIntent(intent);
                if (!filter.test(intent)) return;
                if (--mRemaining == 0) {
                    expectedRef.get().complete(intent);
                }
            }
        };
        final ExpectedBroadcast expected = new ExpectedBroadcast(receiver);
        expectedRef.set(expected);
        mServiceContext.registerReceiver(receiver, intentFilter);
        return expected;
    }

    private void logIntent(Intent intent) {
        final String action = intent.getAction();
        if (CONNECTIVITY_ACTION.equals(action)) {
            final int type = intent.getIntExtra(EXTRA_NETWORK_TYPE, -1);
            final NetworkInfo ni = intent.getParcelableExtra(EXTRA_NETWORK_INFO);
            Log.d(TAG, "Received " + action + ", type=" + type + " ni=" + ni);
        } else if (PROXY_CHANGE_ACTION.equals(action)) {
            final ProxyInfo proxy = (ProxyInfo) intent.getExtra(
                    Proxy.EXTRA_PROXY_INFO, ProxyInfo.buildPacProxy(Uri.EMPTY));
            Log.d(TAG, "Received " + action + ", proxy = " + proxy);
        } else {
            throw new IllegalArgumentException("Unsupported logging " + action);
        }
    }

    /** Expects that {@code count} CONNECTIVITY_ACTION broadcasts are received. */
    private ExpectedBroadcast expectConnectivityAction(final int count) {
        return registerBroadcastReceiverThat(CONNECTIVITY_ACTION, count, intent -> true);
    }

    private ExpectedBroadcast expectConnectivityAction(int type, NetworkInfo.DetailedState state) {
        return registerBroadcastReceiverThat(CONNECTIVITY_ACTION, 1, intent -> {
            final int actualType = intent.getIntExtra(EXTRA_NETWORK_TYPE, -1);
            final NetworkInfo ni = intent.getParcelableExtra(EXTRA_NETWORK_INFO);
            return type == actualType
                    && state == ni.getDetailedState()
                    && extraInfoInBroadcastHasExpectedNullness(ni);
        });
    }

    /** Expects that PROXY_CHANGE_ACTION broadcast is received. */
    private ExpectedBroadcast expectProxyChangeAction() {
        return registerBroadcastReceiverThat(PROXY_CHANGE_ACTION, 1, intent -> true);
    }

    private ExpectedBroadcast expectProxyChangeAction(ProxyInfo proxy) {
        return expectProxyChangeAction(actualProxy -> proxy.equals(actualProxy));
    }

    private ExpectedBroadcast expectProxyChangeAction(Predicate<ProxyInfo> tester) {
        return registerBroadcastReceiverThat(PROXY_CHANGE_ACTION, 1, intent -> {
            final ProxyInfo actualProxy = (ProxyInfo) intent.getExtra(Proxy.EXTRA_PROXY_INFO,
                    ProxyInfo.buildPacProxy(Uri.EMPTY));
            return tester.test(actualProxy);
        });
    }

    private boolean extraInfoInBroadcastHasExpectedNullness(NetworkInfo ni) {
        final DetailedState state = ni.getDetailedState();
        if (state == DetailedState.CONNECTED && ni.getExtraInfo() == null) return false;
        // Expect a null extraInfo if the network is CONNECTING, because a CONNECTIVITY_ACTION
        // broadcast with a state of CONNECTING only happens due to legacy VPN lockdown, which also
        // nulls out extraInfo.
        if (state == DetailedState.CONNECTING && ni.getExtraInfo() != null) return false;
        // Can't make any assertions about DISCONNECTED broadcasts. When a network actually
        // disconnects, disconnectAndDestroyNetwork sets its state to DISCONNECTED and its extraInfo
        // to null. But if the DISCONNECTED broadcast is just simulated by LegacyTypeTracker due to
        // a network switch, extraInfo will likely be populated.
        // This is likely a bug in CS, but likely not one we can fix without impacting apps.
        return true;
    }

    @Test
    public void testNetworkFeature() throws Exception {
        // Connect the cell agent and wait for the connected broadcast.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.addCapability(NET_CAPABILITY_SUPL);
        ExpectedBroadcast b = expectConnectivityAction(TYPE_MOBILE, DetailedState.CONNECTED);
        mCellAgent.connect(true);
        b.expectBroadcast();

        // Build legacy request for SUPL.
        final NetworkCapabilities legacyCaps = new NetworkCapabilities();
        legacyCaps.addTransportType(TRANSPORT_CELLULAR);
        legacyCaps.addCapability(NET_CAPABILITY_SUPL);
        final NetworkRequest legacyRequest = new NetworkRequest(legacyCaps, TYPE_MOBILE_SUPL,
                ConnectivityManager.REQUEST_ID_UNSET, NetworkRequest.Type.REQUEST);

        // File request, withdraw it and make sure no broadcast is sent
        b = expectConnectivityAction(1);
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.requestNetwork(legacyRequest, callback);
        callback.expect(AVAILABLE, mCellAgent);
        mCm.unregisterNetworkCallback(callback);
        b.expectNoBroadcast(800);  // 800ms long enough to at least flake if this is sent

        // Disconnect the network and expect mobile disconnected broadcast.
        b = expectConnectivityAction(TYPE_MOBILE, DetailedState.DISCONNECTED);
        mCellAgent.disconnect();
        b.expectBroadcast();
    }

    @Test
    public void testLingering() throws Exception {
        verifyNoNetwork();
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        assertNull(mCm.getActiveNetworkInfo());
        assertNull(mCm.getActiveNetwork());
        // Test bringing up validated cellular.
        ExpectedBroadcast b = expectConnectivityAction(TYPE_MOBILE, DetailedState.CONNECTED);
        mCellAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        assertLength(2, mCm.getAllNetworks());
        assertTrue(mCm.getAllNetworks()[0].equals(mCm.getActiveNetwork())
                || mCm.getAllNetworks()[1].equals(mCm.getActiveNetwork()));
        assertTrue(mCm.getAllNetworks()[0].equals(mWiFiAgent.getNetwork())
                || mCm.getAllNetworks()[1].equals(mWiFiAgent.getNetwork()));
        // Test bringing up validated WiFi.
        b = expectConnectivityAction(2);
        mWiFiAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        assertLength(2, mCm.getAllNetworks());
        assertTrue(mCm.getAllNetworks()[0].equals(mCm.getActiveNetwork())
                || mCm.getAllNetworks()[1].equals(mCm.getActiveNetwork()));
        assertTrue(mCm.getAllNetworks()[0].equals(mCellAgent.getNetwork())
                || mCm.getAllNetworks()[1].equals(mCellAgent.getNetwork()));
        // Test cellular linger timeout.
        mCellAgent.expectDisconnected();
        waitForIdle();
        assertLength(1, mCm.getAllNetworks());
        verifyActiveNetwork(TRANSPORT_WIFI);
        assertLength(1, mCm.getAllNetworks());
        assertEquals(mCm.getAllNetworks()[0], mCm.getActiveNetwork());
        // Test WiFi disconnect.
        b = expectConnectivityAction(1);
        mWiFiAgent.disconnect();
        b.expectBroadcast();
        verifyNoNetwork();
    }

    /**
     * Verify a newly created network will be inactive instead of torn down even if no one is
     * requesting.
     */
    @Test
    public void testNewNetworkInactive() throws Exception {
        // Create a callback that monitoring the testing network.
        final TestNetworkCallback listenCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(new NetworkRequest.Builder().build(), listenCallback);

        // 1. Create a network that is not requested by anyone, and does not satisfy any of the
        // default requests. Verify that the network will be inactive instead of torn down.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithoutInternet();
        listenCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        listenCallback.assertNoCallback();

        // Verify that the network will be torn down after nascent expiry. A small period of time
        // is added in case of flakiness.
        final int nascentTimeoutMs =
                mService.mNascentDelayMs + mService.mNascentDelayMs / 4;
        listenCallback.expect(LOST, mWiFiAgent, nascentTimeoutMs);

        // 2. Create a network that is satisfied by a request comes later.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithoutInternet();
        listenCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        final TestNetworkCallback wifiCallback = new TestNetworkCallback();
        mCm.requestNetwork(wifiRequest, wifiCallback);
        wifiCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        // Verify that the network will be kept since the request is still satisfied. And is able
        // to get disconnected as usual if the request is released after the nascent timer expires.
        listenCallback.assertNoCallback(nascentTimeoutMs);
        mCm.unregisterNetworkCallback(wifiCallback);
        listenCallback.expect(LOST, mWiFiAgent);

        // 3. Create a network that is satisfied by a request comes later.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithoutInternet();
        listenCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        mCm.requestNetwork(wifiRequest, wifiCallback);
        wifiCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        // Verify that the network will still be torn down after the request gets removed.
        mCm.unregisterNetworkCallback(wifiCallback);
        listenCallback.expect(LOST, mWiFiAgent);

        // There is no need to ensure that LOSING is never sent in the common case that the
        // network immediately satisfies a request that was already present, because it is already
        // verified anywhere whenever {@code TestNetworkCallback#expectAvailable*} is called.

        mCm.unregisterNetworkCallback(listenCallback);
    }

    /**
     * Verify a newly created network will be inactive and switch to background if only background
     * request is satisfied.
     */
    @Test
    public void testNewNetworkInactive_bgNetwork() throws Exception {
        // Create a callback that monitoring the wifi network.
        final TestNetworkCallback wifiListenCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build(), wifiListenCallback);

        // Create callbacks that can monitor background and foreground mobile networks.
        // This is done by granting using background networks permission before registration. Thus,
        // the service will not add {@code NET_CAPABILITY_FOREGROUND} by default.
        grantUsingBackgroundNetworksPermissionForUid(Binder.getCallingUid());
        final TestNetworkCallback bgMobileListenCallback = new TestNetworkCallback();
        final TestNetworkCallback fgMobileListenCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build(), bgMobileListenCallback);
        mCm.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_FOREGROUND).build(), fgMobileListenCallback);

        // Connect wifi, which satisfies default request.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        wifiListenCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);

        // Connect a cellular network, verify that satisfies only the background callback.
        setAlwaysOnNetworks(true);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        bgMobileListenCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        fgMobileListenCallback.assertNoCallback();
        assertFalse(isForegroundNetwork(mCellAgent));

        mCellAgent.disconnect();
        bgMobileListenCallback.expect(LOST, mCellAgent);
        fgMobileListenCallback.assertNoCallback();

        mCm.unregisterNetworkCallback(wifiListenCallback);
        mCm.unregisterNetworkCallback(bgMobileListenCallback);
        mCm.unregisterNetworkCallback(fgMobileListenCallback);
    }

    @Test
    public void testBinderDeathAfterUnregister() throws Exception {
        final NetworkCapabilities caps = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        final Handler handler = new Handler(ConnectivityThread.getInstanceLooper());
        final Messenger messenger = new Messenger(handler);
        final CompletableFuture<Binder.DeathRecipient> deathRecipient = new CompletableFuture<>();
        final Binder binder = new Binder() {
            private DeathRecipient mDeathRecipient;
            @Override
            public void linkToDeath(@NonNull final DeathRecipient recipient, final int flags) {
                synchronized (this) {
                    mDeathRecipient = recipient;
                }
                super.linkToDeath(recipient, flags);
                deathRecipient.complete(recipient);
            }

            @Override
            public boolean unlinkToDeath(@NonNull final DeathRecipient recipient, final int flags) {
                synchronized (this) {
                    if (null == mDeathRecipient) {
                        throw new IllegalStateException();
                    }
                    mDeathRecipient = null;
                }
                return super.unlinkToDeath(recipient, flags);
            }
        };
        final NetworkRequest request = mService.listenForNetwork(caps, messenger, binder,
                NetworkCallback.FLAG_NONE, mContext.getOpPackageName(),
                mContext.getAttributionTag());
        mService.releaseNetworkRequest(request);
        deathRecipient.get().binderDied();
        // Wait for the release message to be processed.
        waitForIdle();
        // After waitForIdle(), the message was processed and the service didn't crash.
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testValidatedCellularOutscoresUnvalidatedWiFi_CanTimeShare() throws Exception {
        // The behavior of this test should be the same whether the radio can time share or not.
        doTestValidatedCellularOutscoresUnvalidatedWiFi(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testValidatedCellularOutscoresUnvalidatedWiFi_CannotTimeShare() throws Exception {
        doTestValidatedCellularOutscoresUnvalidatedWiFi(false);
    }

    private void doTestValidatedCellularOutscoresUnvalidatedWiFi(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up unvalidated WiFi
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        ExpectedBroadcast b = expectConnectivityAction(1);
        mWiFiAgent.connect(false);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test bringing up unvalidated cellular
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false);
        waitForIdle();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test cellular disconnect.
        mCellAgent.disconnect();
        waitForIdle();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test bringing up validated cellular
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        b = expectConnectivityAction(2);
        mCellAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Test cellular disconnect.
        b = expectConnectivityAction(2);
        mCellAgent.disconnect();
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test WiFi disconnect.
        b = expectConnectivityAction(1);
        mWiFiAgent.disconnect();
        b.expectBroadcast();
        verifyNoNetwork();
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testUnvalidatedWifiOutscoresUnvalidatedCellular_CanTimeShare() throws Exception {
        doTestUnvalidatedWifiOutscoresUnvalidatedCellular(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testUnvalidatedWifiOutscoresUnvalidatedCellular_CannotTimeShare() throws Exception {
        doTestUnvalidatedWifiOutscoresUnvalidatedCellular(false);
    }

    private void doTestUnvalidatedWifiOutscoresUnvalidatedCellular(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up unvalidated cellular.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        ExpectedBroadcast b = expectConnectivityAction(1);
        mCellAgent.connect(false);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Test bringing up unvalidated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        b = expectConnectivityAction(2);
        mWiFiAgent.connect(false);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test WiFi disconnect.
        b = expectConnectivityAction(2);
        mWiFiAgent.disconnect();
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Test cellular disconnect.
        b = expectConnectivityAction(1);
        mCellAgent.disconnect();
        b.expectBroadcast();
        verifyNoNetwork();
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testUnlingeringDoesNotValidate_CanTimeShare() throws Exception {
        doTestUnlingeringDoesNotValidate(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testUnlingeringDoesNotValidate_CannotTimeShare() throws Exception {
        doTestUnlingeringDoesNotValidate(false);
    }

    private void doTestUnlingeringDoesNotValidate(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up unvalidated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        ExpectedBroadcast b = expectConnectivityAction(1);
        mWiFiAgent.connect(false);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        assertFalse(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        // Test bringing up validated cellular.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        b = expectConnectivityAction(2);
        mCellAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        assertFalse(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        // Test cellular disconnect.
        b = expectConnectivityAction(2);
        mCellAgent.disconnect();
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Unlingering a network should not cause it to be marked as validated.
        assertFalse(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testRequestMigrationToSameTransport_CanTimeShare() throws Exception {
        // Simulate a device where the cell radio is capable of time sharing
        mService.mCellularRadioTimesharingCapable = true;
        doTestRequestMigrationToSameTransport(TRANSPORT_CELLULAR, true);
        doTestRequestMigrationToSameTransport(TRANSPORT_WIFI, true);
        doTestRequestMigrationToSameTransport(TRANSPORT_ETHERNET, true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testRequestMigrationToSameTransport_CannotTimeShare() throws Exception {
        // Simulate a device where the cell radio is not capable of time sharing
        mService.mCellularRadioTimesharingCapable = false;
        doTestRequestMigrationToSameTransport(TRANSPORT_CELLULAR, false);
        doTestRequestMigrationToSameTransport(TRANSPORT_WIFI, true);
        doTestRequestMigrationToSameTransport(TRANSPORT_ETHERNET, true);
    }

    private void doTestRequestMigrationToSameTransport(final int transport,
            final boolean expectLingering) throws Exception {
        // To speed up tests the linger delay is very short by default in tests but this
        // test needs to make sure the delay is not incurred so a longer value is safer (it
        // reduces the risk that a bug exists but goes undetected). The alarm manager in the test
        // throws and crashes CS if this is set to anything more than the below constant though.
        mService.mLingerDelayMs = UNREASONABLY_LONG_ALARM_WAIT_MS;

        final TestNetworkCallback generalCb = new TestNetworkCallback();
        final TestNetworkCallback defaultCb = new TestNetworkCallback();
        mCm.registerNetworkCallback(
                new NetworkRequest.Builder().addTransportType(transport).build(),
                generalCb);
        mCm.registerDefaultNetworkCallback(defaultCb);

        // Bring up net agent 1
        final TestNetworkAgentWrapper net1 = new TestNetworkAgentWrapper(transport);
        net1.connect(true);
        // Make sure the default request is on net 1
        generalCb.expectAvailableThenValidatedCallbacks(net1);
        defaultCb.expectAvailableThenValidatedCallbacks(net1);

        // Bring up net 2 with primary and mms
        final TestNetworkAgentWrapper net2 = new TestNetworkAgentWrapper(transport);
        net2.addCapability(NET_CAPABILITY_MMS);
        net2.setScore(new NetworkScore.Builder().setTransportPrimary(true).build());
        net2.connect(true);

        // Make sure the default request goes to net 2
        generalCb.expectAvailableCallbacksUnvalidated(net2);
        if (expectLingering) {
            generalCb.expectLosing(net1);
        }
        generalCb.expectCaps(net2, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        defaultCb.expectAvailableDoubleValidatedCallbacks(net2);

        // Make sure cell 1 is unwanted immediately if the radio can't time share, but only
        // after some delay if it can.
        if (expectLingering) {
            net1.assertNotDisconnected(TEST_CALLBACK_TIMEOUT_MS); // always incurs the timeout
            generalCb.assertNoCallback();
            // assertNotDisconnected waited for TEST_CALLBACK_TIMEOUT_MS, so waiting for the
            // linger period gives TEST_CALLBACK_TIMEOUT_MS time for the event to process.
            net1.expectDisconnected(UNREASONABLY_LONG_ALARM_WAIT_MS);
        } else {
            net1.expectDisconnected(TEST_CALLBACK_TIMEOUT_MS);
        }
        net1.disconnect();
        generalCb.expect(LOST, net1);

        // Remove primary from net 2
        net2.setScore(new NetworkScore.Builder().build());
        // Request MMS
        final TestNetworkCallback mmsCallback = new TestNetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder().addCapability(NET_CAPABILITY_MMS).build(),
                mmsCallback);
        mmsCallback.expectAvailableCallbacksValidated(net2);

        // Bring up net 3 with primary but without MMS
        final TestNetworkAgentWrapper net3 = new TestNetworkAgentWrapper(transport);
        net3.setScore(new NetworkScore.Builder().setTransportPrimary(true).build());
        net3.connect(true);

        // Make sure default goes to net 3, but the MMS request doesn't
        generalCb.expectAvailableThenValidatedCallbacks(net3);
        defaultCb.expectAvailableDoubleValidatedCallbacks(net3);
        mmsCallback.assertNoCallback();
        net2.assertNotDisconnected(TEST_CALLBACK_TIMEOUT_MS); // Always incurs the timeout

        // Revoke MMS request and make sure net 2 is torn down with the appropriate delay
        mCm.unregisterNetworkCallback(mmsCallback);
        if (expectLingering) {
            // If the radio can time share, the linger delay hasn't elapsed yet, so apps will
            // get LOSING. If the radio can't time share, this is a hard loss, since the last
            // request keeping up this network has been removed and the network isn't lingering
            // for any other request.
            generalCb.expectLosing(net2);
            net2.assertNotDisconnected(TEST_CALLBACK_TIMEOUT_MS);
            // Timeout 0 because after a while LOST will actually arrive
            generalCb.assertNoCallback(0 /* timeoutMs */);
            net2.expectDisconnected(UNREASONABLY_LONG_ALARM_WAIT_MS);
        } else {
            net2.expectDisconnected(TEST_CALLBACK_TIMEOUT_MS);
        }
        net2.disconnect();
        generalCb.expect(LOST, net2);
        defaultCb.assertNoCallback();

        net3.disconnect();
        mCm.unregisterNetworkCallback(defaultCb);
        mCm.unregisterNetworkCallback(generalCb);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testCellularOutscoresWeakWifi_CanTimeShare() throws Exception {
        // The behavior of this test should be the same whether the radio can time share or not.
        doTestCellularOutscoresWeakWifi(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testCellularOutscoresWeakWifi_CannotTimeShare() throws Exception {
        doTestCellularOutscoresWeakWifi(false);
    }

    private void doTestCellularOutscoresWeakWifi(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up validated cellular.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        ExpectedBroadcast b = expectConnectivityAction(1);
        mCellAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Test bringing up validated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        b = expectConnectivityAction(2);
        mWiFiAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test WiFi getting really weak.
        b = expectConnectivityAction(2);
        mWiFiAgent.adjustScore(-11);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Test WiFi restoring signal strength.
        b = expectConnectivityAction(2);
        mWiFiAgent.adjustScore(11);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testReapingNetwork_CanTimeShare() throws Exception {
        doTestReapingNetwork(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testReapingNetwork_CannotTimeShare() throws Exception {
        doTestReapingNetwork(false);
    }

    private void doTestReapingNetwork(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up WiFi without NET_CAPABILITY_INTERNET.
        // Expect it to be torn down immediately because it satisfies no requests.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithoutInternet();
        mWiFiAgent.expectDisconnected();
        // Test bringing up cellular without NET_CAPABILITY_INTERNET.
        // Expect it to be torn down immediately because it satisfies no requests.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mCellAgent.connectWithoutInternet();
        mCellAgent.expectDisconnected();
        // Test bringing up validated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        final ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTED);
        mWiFiAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test bringing up unvalidated cellular.
        // Expect it to be torn down because it could never be the highest scoring network
        // satisfying the default request even if it validated.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false);
        mCellAgent.expectDisconnected();
        verifyActiveNetwork(TRANSPORT_WIFI);
        mWiFiAgent.disconnect();
        mWiFiAgent.expectDisconnected();
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testCellularFallback_CanTimeShare() throws Exception {
        doTestCellularFallback(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testCellularFallback_CannotTimeShare() throws Exception {
        doTestCellularFallback(false);
    }

    private void doTestCellularFallback(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up validated cellular.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        ExpectedBroadcast b = expectConnectivityAction(1);
        mCellAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Test bringing up validated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        b = expectConnectivityAction(2);
        mWiFiAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Reevaluate WiFi (it'll instantly fail DNS).
        b = expectConnectivityAction(2);
        assertTrue(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        mCm.reportBadNetwork(mWiFiAgent.getNetwork());
        // Should quickly fall back to Cellular.
        b.expectBroadcast();
        assertFalse(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Reevaluate cellular (it'll instantly fail DNS).
        b = expectConnectivityAction(2);
        assertTrue(mCm.getNetworkCapabilities(mCellAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        mCm.reportBadNetwork(mCellAgent.getNetwork());
        // Should quickly fall back to WiFi.
        b.expectBroadcast();
        assertFalse(mCm.getNetworkCapabilities(mCellAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertFalse(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        verifyActiveNetwork(TRANSPORT_WIFI);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testWiFiFallback_CanTimeShare() throws Exception {
        doTestWiFiFallback(true);
    }

    // TODO : migrate to @Parameterized
    @Test
    public void testWiFiFallback_CannotTimeShare() throws Exception {
        doTestWiFiFallback(false);
    }

    private void doTestWiFiFallback(
            final boolean cellRadioTimesharingCapable) throws Exception {
        mService.mCellularRadioTimesharingCapable = cellRadioTimesharingCapable;
        // Test bringing up unvalidated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        ExpectedBroadcast b = expectConnectivityAction(1);
        mWiFiAgent.connect(false);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        // Test bringing up validated cellular.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        b = expectConnectivityAction(2);
        mCellAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
        // Reevaluate cellular (it'll instantly fail DNS).
        b = expectConnectivityAction(2);
        assertTrue(mCm.getNetworkCapabilities(mCellAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        mCm.reportBadNetwork(mCellAgent.getNetwork());
        // Should quickly fall back to WiFi.
        b.expectBroadcast();
        assertFalse(mCm.getNetworkCapabilities(mCellAgent.getNetwork()).hasCapability(
                NET_CAPABILITY_VALIDATED));
        verifyActiveNetwork(TRANSPORT_WIFI);
    }

    @Test
    public void testRequiresValidation() {
        assertTrue(NetworkMonitorUtils.isValidationRequired(false /* isDunValidationRequired */,
                false /* isVpnValidationRequired */,
                mCm.getDefaultRequest().networkCapabilities));
    }

    /**
     * Utility NetworkCallback for testing. The caller must explicitly test for all the callbacks
     * this class receives, by calling expect() exactly once each time a callback is
     * received. assertNoCallback may be called at any time.
     */
    private class TestNetworkCallback extends TestableNetworkCallback {
        TestNetworkCallback() {
            // In the context of this test, the testable network callbacks should use waitForIdle
            // before calling assertNoCallback in an effort to detect issues where a callback is
            // not yet sent but a message currently in the queue of a handler will cause it to
            // be sent soon.
            super(TEST_CALLBACK_TIMEOUT_MS, TEST_CALLBACK_TIMEOUT_MS,
                    ConnectivityServiceTest.this::waitForIdle);
        }

        public CallbackEntry.Losing expectLosing(final HasNetwork n, final long timeoutMs) {
            final CallbackEntry.Losing losing = expect(LOSING, n, timeoutMs);
            final int maxMsToLive = losing.getMaxMsToLive();
            if (maxMsToLive < 0 || maxMsToLive > mService.mLingerDelayMs) {
                // maxMsToLive is the value that was received in the onLosing callback. That must
                // not be negative, so check that.
                // Also, maxMsToLive is the remaining time until the network expires.
                // mService.mLingerDelayMs is how long the network takes from when it's first
                // detected to be unneeded to when it expires, so maxMsToLive should never
                // be greater than that.
                fail(String.format("Invalid linger time value %d, must be between %d and %d",
                        maxMsToLive, 0, mService.mLingerDelayMs));
            }
            return losing;
        }

        public CallbackEntry.Losing expectLosing(final HasNetwork n) {
            return expectLosing(n, getDefaultTimeoutMs());
        }
    }

    // Can't be part of TestNetworkCallback because "cannot be declared static; static methods can
    // only be declared in a static or top level type".
    static void assertNoCallbacks(final long timeoutMs, TestNetworkCallback ... callbacks) {
        for (TestNetworkCallback c : callbacks) {
            c.assertNoCallback(timeoutMs);
        }
    }

    static void assertNoCallbacks(TestNetworkCallback ... callbacks) {
        for (TestNetworkCallback c : callbacks) {
            c.assertNoCallback(); // each callback uses its own timeout
        }
    }

    static void expectOnLost(TestNetworkAgentWrapper network, TestNetworkCallback ... callbacks) {
        for (TestNetworkCallback c : callbacks) {
            c.expect(LOST, network);
        }
    }

    static void expectAvailableCallbacksUnvalidatedWithSpecifier(TestNetworkAgentWrapper network,
            NetworkSpecifier specifier, TestNetworkCallback ... callbacks) {
        for (TestNetworkCallback c : callbacks) {
            c.expect(AVAILABLE, network);
            c.expectCaps(network, cb -> !cb.hasCapability(NET_CAPABILITY_VALIDATED)
                    && Objects.equals(specifier, cb.getNetworkSpecifier()));
            c.expect(LINK_PROPERTIES_CHANGED, network);
            c.expect(BLOCKED_STATUS, network);
        }
    }

    @Test
    public void testNetworkDoesntMatchRequestsUntilConnected() throws Exception {
        final TestNetworkCallback cb = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.requestNetwork(wifiRequest, cb);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        // Updating the score triggers a rematch.
        mWiFiAgent.setScore(new NetworkScore.Builder().build());
        cb.assertNoCallback();
        mWiFiAgent.connect(false);
        cb.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        cb.assertNoCallback();
        mCm.unregisterNetworkCallback(cb);
    }

    @Test
    public void testNetworkNotVisibleUntilConnected() throws Exception {
        final TestNetworkCallback cb = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(wifiRequest, cb);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        final NetworkCapabilities nc = mWiFiAgent.getNetworkCapabilities();
        nc.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        mWiFiAgent.setNetworkCapabilities(nc, true /* sendToConnectivityService */);
        cb.assertNoCallback();
        mWiFiAgent.connect(false);
        cb.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        final CallbackEntry found = CollectionUtils.findLast(cb.getHistory(),
                it -> it instanceof CallbackEntry.CapabilitiesChanged);
        assertTrue(((CallbackEntry.CapabilitiesChanged) found).getCaps()
                .hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        cb.assertNoCallback();
        mCm.unregisterNetworkCallback(cb);
    }

    @Test
    public void testStateChangeNetworkCallbacks() throws Exception {
        final TestNetworkCallback genericNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback wifiNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest genericRequest = new NetworkRequest.Builder()
                .clearCapabilities().build();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.registerNetworkCallback(genericRequest, genericNetworkCallback);
        mCm.registerNetworkCallback(wifiRequest, wifiNetworkCallback);
        mCm.registerNetworkCallback(cellRequest, cellNetworkCallback);

        // Test unvalidated networks
        ExpectedBroadcast b = expectConnectivityAction(1);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false);
        genericNetworkCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        cellNetworkCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        b.expectBroadcast();
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        // This should not trigger spurious onAvailable() callbacks, b/21762680.
        mCellAgent.adjustScore(-1);
        waitForIdle();
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());

        b = expectConnectivityAction(2);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        genericNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        wifiNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        b.expectBroadcast();
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        b = expectConnectivityAction(2);
        mWiFiAgent.disconnect();
        genericNetworkCallback.expect(CallbackEntry.LOST, mWiFiAgent);
        wifiNetworkCallback.expect(CallbackEntry.LOST, mWiFiAgent);
        cellNetworkCallback.assertNoCallback();
        b.expectBroadcast();
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        b = expectConnectivityAction(1);
        mCellAgent.disconnect();
        genericNetworkCallback.expect(CallbackEntry.LOST, mCellAgent);
        cellNetworkCallback.expect(CallbackEntry.LOST, mCellAgent);
        b.expectBroadcast();
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        // Test validated networks
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        genericNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        // This should not trigger spurious onAvailable() callbacks, b/21762680.
        mCellAgent.adjustScore(-1);
        waitForIdle();
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        genericNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        genericNetworkCallback.expectLosing(mCellAgent);
        genericNetworkCallback.expectCaps(mWiFiAgent,
                c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        wifiNetworkCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        cellNetworkCallback.expectLosing(mCellAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        // Cell will disconnect after the lingering period. Before that elapses check that
        // there have been no callbacks.
        assertNoCallbacks(0 /* timeoutMs */,
                genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        mWiFiAgent.disconnect();
        genericNetworkCallback.expect(LOST, mWiFiAgent);
        wifiNetworkCallback.expect(LOST, mWiFiAgent);
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);

        mCellAgent.disconnect();
        genericNetworkCallback.expect(LOST, mCellAgent);
        cellNetworkCallback.expect(LOST, mCellAgent);
        assertNoCallbacks(genericNetworkCallback, wifiNetworkCallback, cellNetworkCallback);
    }

    private void doNetworkCallbacksSanitizationTest(boolean sanitized) throws Exception {
        final TestNetworkCallback callback = new TestNetworkCallback();
        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(wifiRequest, callback);
        mCm.registerDefaultNetworkCallback(defaultCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        final LinkProperties newLp = new LinkProperties();
        final Uri capportUrl = Uri.parse("https://capport.example.com/api");
        final CaptivePortalData capportData = new CaptivePortalData.Builder()
                .setCaptive(true).build();

        final Uri expectedCapportUrl = sanitized ? null : capportUrl;
        newLp.setCaptivePortalApiUrl(capportUrl);
        mWiFiAgent.sendLinkProperties(newLp);
        callback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent, cb ->
                Objects.equals(expectedCapportUrl, cb.getLp().getCaptivePortalApiUrl()));
        defaultCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent, cb ->
                Objects.equals(expectedCapportUrl, cb.getLp().getCaptivePortalApiUrl()));

        final CaptivePortalData expectedCapportData = sanitized ? null : capportData;
        mWiFiAgent.notifyCapportApiDataChanged(capportData);
        callback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent, cb ->
                Objects.equals(expectedCapportData, cb.getLp().getCaptivePortalData()));
        defaultCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent, cb ->
                Objects.equals(expectedCapportData, cb.getLp().getCaptivePortalData()));

        final LinkProperties lp = mCm.getLinkProperties(mWiFiAgent.getNetwork());
        assertEquals(expectedCapportUrl, lp.getCaptivePortalApiUrl());
        assertEquals(expectedCapportData, lp.getCaptivePortalData());
    }

    @Test
    public void networkCallbacksSanitizationTest_Sanitize() throws Exception {
        mServiceContext.setPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                PERMISSION_DENIED);
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_DENIED);
        doNetworkCallbacksSanitizationTest(true /* sanitized */);
    }

    @Test
    public void networkCallbacksSanitizationTest_NoSanitize_NetworkStack() throws Exception {
        mServiceContext.setPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                PERMISSION_GRANTED);
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_DENIED);
        doNetworkCallbacksSanitizationTest(false /* sanitized */);
    }

    @Test
    public void networkCallbacksSanitizationTest_NoSanitize_Settings() throws Exception {
        mServiceContext.setPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                PERMISSION_DENIED);
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);
        doNetworkCallbacksSanitizationTest(false /* sanitized */);
    }

    @Test
    public void testOwnerUidCannotChange() throws Exception {
        final NetworkCapabilities ncTemplate = new NetworkCapabilities();
        final int originalOwnerUid = Process.myUid();
        ncTemplate.setOwnerUid(originalOwnerUid);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, new LinkProperties(), ncTemplate);
        mWiFiAgent.connect(false);
        waitForIdle();

        // Send ConnectivityService an update to the mWiFiAgent's capabilities that changes
        // the owner UID and an unrelated capability.
        NetworkCapabilities agentCapabilities = mWiFiAgent.getNetworkCapabilities();
        assertEquals(originalOwnerUid, agentCapabilities.getOwnerUid());
        agentCapabilities.setOwnerUid(42);
        assertFalse(agentCapabilities.hasCapability(NET_CAPABILITY_NOT_CONGESTED));
        agentCapabilities.addCapability(NET_CAPABILITY_NOT_CONGESTED);
        mWiFiAgent.setNetworkCapabilities(agentCapabilities, true);
        waitForIdle();

        // Owner UIDs are not visible without location permission.
        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        // Check that the capability change has been applied but the owner UID is not modified.
        NetworkCapabilities nc = mCm.getNetworkCapabilities(mWiFiAgent.getNetwork());
        assertEquals(originalOwnerUid, nc.getOwnerUid());
        assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_CONGESTED));
    }

    @Test
    public void testMultipleLingering() throws Exception {
        // This test would be flaky with the default 120ms timer: that is short enough that
        // lingered networks are torn down before assertions can be run. We don't want to mock the
        // lingering timer to keep the WakeupMessage logic realistic: this has already proven useful
        // in detecting races. Furthermore, sometimes the test is running while Phenotype is running
        // so hot that the test doesn't get the CPU for multiple hundreds of milliseconds, so this
        // needs to be suitably long.
        mService.mLingerDelayMs = 2_000;

        NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities().addCapability(NET_CAPABILITY_NOT_METERED)
                .build();
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);

        mCellAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mEthernetAgent.addCapability(NET_CAPABILITY_NOT_METERED);

        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mWiFiAgent.connect(true);
        // We get AVAILABLE on wifi when wifi connects and satisfies our unmetered request.
        // We then get LOSING when wifi validates and cell is outscored.
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        // TODO: Investigate sending validated before losing.
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mEthernetAgent.connect(true);
        callback.expectAvailableCallbacksUnvalidated(mEthernetAgent);
        // TODO: Investigate sending validated before losing.
        callback.expectLosing(mWiFiAgent);
        callback.expectCaps(mEthernetAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mEthernetAgent);
        assertEquals(mEthernetAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mEthernetAgent.disconnect();
        callback.expect(LOST, mEthernetAgent);
        defaultCallback.expect(LOST, mEthernetAgent);
        defaultCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        for (int i = 0; i < 4; i++) {
            TestNetworkAgentWrapper oldNetwork, newNetwork;
            if (i % 2 == 0) {
                mWiFiAgent.adjustScore(-15);
                oldNetwork = mWiFiAgent;
                newNetwork = mCellAgent;
            } else {
                mWiFiAgent.adjustScore(15);
                oldNetwork = mCellAgent;
                newNetwork = mWiFiAgent;

            }
            callback.expectLosing(oldNetwork);
            // TODO: should we send an AVAILABLE callback to newNetwork, to indicate that it is no
            // longer lingering?
            defaultCallback.expectAvailableCallbacksValidated(newNetwork);
            assertEquals(newNetwork.getNetwork(), mCm.getActiveNetwork());
        }
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());

        // Verify that if a network no longer satisfies a request, we send LOST and not LOSING, even
        // if the network is still up.
        mWiFiAgent.removeCapability(NET_CAPABILITY_NOT_METERED);
        // We expect a notification about the capabilities change, and nothing else.
        defaultCallback.expectCaps(mWiFiAgent, c -> !c.hasCapability(NET_CAPABILITY_NOT_METERED));
        defaultCallback.assertNoCallback();
        callback.expect(LOST, mWiFiAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Wifi no longer satisfies our listen, which is for an unmetered network.
        // But because its score is 55, it's still up (and the default network).
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());

        // Disconnect our test networks.
        mWiFiAgent.disconnect();
        defaultCallback.expect(LOST, mWiFiAgent);
        defaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        mCellAgent.disconnect();
        defaultCallback.expect(LOST, mCellAgent);
        waitForIdle();
        assertEquals(null, mCm.getActiveNetwork());

        mCm.unregisterNetworkCallback(callback);
        waitForIdle();

        // Check that a network is only lingered or torn down if it would not satisfy a request even
        // if it validated.
        request = new NetworkRequest.Builder().clearCapabilities().build();
        callback = new TestNetworkCallback();

        mCm.registerNetworkCallback(request, callback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false);   // Score: 10
        callback.expectAvailableCallbacksUnvalidated(mCellAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Bring up wifi with a score of 20.
        // Cell stays up because it would satisfy the default request if it validated.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);   // Score: 20
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        defaultCallback.expect(LOST, mWiFiAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Bring up wifi, then validate it. Previous versions would immediately tear down cell, but
        // it's arguably correct to linger it, since it was the default network before it validated.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        // TODO: Investigate sending validated before losing.
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        defaultCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        defaultCallback.expect(LOST, mWiFiAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        mCellAgent.disconnect();
        callback.expect(LOST, mCellAgent);
        defaultCallback.expect(LOST, mCellAgent);
        waitForIdle();
        assertEquals(null, mCm.getActiveNetwork());

        // If a network is lingering, and we add and remove a request from it, resume lingering.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        // TODO: Investigate sending validated before losing.
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        NetworkCallback noopCallback = new NetworkCallback();
        mCm.requestNetwork(cellRequest, noopCallback);
        // TODO: should this cause an AVAILABLE callback, to indicate that the network is no longer
        // lingering?
        mCm.unregisterNetworkCallback(noopCallback);
        callback.expectLosing(mCellAgent);

        // Similar to the above: lingering can start even after the lingered request is removed.
        // Disconnect wifi and switch to cell.
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        defaultCallback.expect(LOST, mWiFiAgent);
        defaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Cell is now the default network. Pin it with a cell-specific request.
        noopCallback = new NetworkCallback();  // Can't reuse NetworkCallbacks. http://b/20701525
        mCm.requestNetwork(cellRequest, noopCallback);

        // Now connect wifi, and expect it to become the default network.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        // The default request is lingering on cell, but nothing happens to cell, and we send no
        // callbacks for it, because it's kept up by cellRequest.
        callback.assertNoCallback();
        // Now unregister cellRequest and expect cell to start lingering.
        mCm.unregisterNetworkCallback(noopCallback);
        callback.expectLosing(mCellAgent);

        // Let linger run its course.
        callback.assertNoCallback(0 /* timeoutMs */);
        final int lingerTimeoutMs = mService.mLingerDelayMs + mService.mLingerDelayMs / 4;
        callback.expect(LOST, mCellAgent, lingerTimeoutMs);

        // Register a TRACK_DEFAULT request and check that it does not affect lingering.
        TestNetworkCallback trackDefaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(trackDefaultCallback);
        trackDefaultCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mEthernetAgent.connect(true);
        callback.expectAvailableCallbacksUnvalidated(mEthernetAgent);
        callback.expectLosing(mWiFiAgent);
        callback.expectCaps(mEthernetAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        trackDefaultCallback.expectAvailableDoubleValidatedCallbacks(mEthernetAgent);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mEthernetAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Let linger run its course.
        callback.expect(LOST, mWiFiAgent, lingerTimeoutMs);

        // Clean up.
        mEthernetAgent.disconnect();
        callback.expect(LOST, mEthernetAgent);
        defaultCallback.expect(LOST, mEthernetAgent);
        trackDefaultCallback.expect(LOST, mEthernetAgent);

        mCm.unregisterNetworkCallback(callback);
        mCm.unregisterNetworkCallback(defaultCallback);
        mCm.unregisterNetworkCallback(trackDefaultCallback);
    }

    private void grantUsingBackgroundNetworksPermissionForUid(final int uid) throws Exception {
        grantUsingBackgroundNetworksPermissionForUid(uid, mContext.getPackageName());
    }

    private void grantUsingBackgroundNetworksPermissionForUid(
            final int uid, final String packageName) throws Exception {
        doReturn(buildPackageInfo(true /* hasSystemPermission */, uid)).when(mPackageManager)
                .getPackageInfo(eq(packageName), eq(GET_PERMISSIONS));

        // Send a broadcast indicating a package was installed.
        final Intent addedIntent = new Intent(ACTION_PACKAGE_ADDED);
        addedIntent.putExtra(Intent.EXTRA_UID, uid);
        addedIntent.setData(Uri.parse("package:" + packageName));
        processBroadcast(addedIntent);
    }

    @Test
    public void testNetworkGoesIntoBackgroundAfterLinger() throws Exception {
        setAlwaysOnNetworks(true);
        grantUsingBackgroundNetworksPermissionForUid(Binder.getCallingUid());
        NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities()
                .build();
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);

        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        // Wifi comes up and cell lingers.
        mWiFiAgent.connect(true);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));

        // File a request for cellular, then release it.
        NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        NetworkCallback noopCallback = new NetworkCallback();
        mCm.requestNetwork(cellRequest, noopCallback);
        mCm.unregisterNetworkCallback(noopCallback);
        callback.expectLosing(mCellAgent);

        // Let linger run its course.
        callback.assertNoCallback();
        final int lingerTimeoutMs = TEST_LINGER_DELAY_MS + TEST_LINGER_DELAY_MS / 4;
        callback.expectCaps(mCellAgent, lingerTimeoutMs,
                c -> !c.hasCapability(NET_CAPABILITY_FOREGROUND));

        // Clean up.
        mCm.unregisterNetworkCallback(defaultCallback);
        mCm.unregisterNetworkCallback(callback);
    }

    /** Expects the specified notification and returns the notification ID. */
    private int expectNotification(TestNetworkAgentWrapper agent, NotificationType type) {
        verify(mNotificationManager, timeout(TIMEOUT_MS)).notify(
                eq(NetworkNotificationManager.tagFor(agent.getNetwork().netId)),
                eq(type.eventId), any());
        return type.eventId;
    }

    private void expectNoNotification(@NonNull final TestNetworkAgentWrapper agent) {
        verify(mNotificationManager, never()).notifyAsUser(anyString(), anyInt(), any(), any());
    }

    /**
     * Expects the specified notification happens when the unvalidated prompt message arrives
     *
     * @return the notification ID.
     **/
    private int expectUnvalidationCheckWillNotify(TestNetworkAgentWrapper agent,
            NotificationType type) {
        mService.scheduleEvaluationTimeout(agent.getNetwork(), 0 /* delayMs */);
        waitForIdle();
        return expectNotification(agent, type);
    }

    /**
     * Expects that the notification for the specified network is cleared.
     *
     * This generally happens when the network disconnects or when the newtwork validates. During
     * normal usage the notification is also cleared by the system when the notification is tapped.
     */
    private void expectClearNotification(TestNetworkAgentWrapper agent, NotificationType type) {
        verify(mNotificationManager, timeout(TIMEOUT_MS)).cancel(
                eq(NetworkNotificationManager.tagFor(agent.getNetwork().netId)), eq(type.eventId));
    }

    /**
     * Expects that no notification happens when the unvalidated prompt message arrives
     *
     * @return the notification ID.
     **/
    private void expectUnvalidationCheckWillNotNotify(TestNetworkAgentWrapper agent) {
        mService.scheduleEvaluationTimeout(agent.getNetwork(), 0 /*delayMs */);
        waitForIdle();
        expectNoNotification(agent);
    }

    private void expectDisconnectAndClearNotifications(TestNetworkCallback callback,
            TestNetworkAgentWrapper agent, NotificationType type) {
        callback.expect(LOST, agent);
        expectClearNotification(agent, type);
    }

    private NativeNetworkConfig nativeNetworkConfigPhysical(int netId, int permission) {
        return new NativeNetworkConfig(netId, NativeNetworkType.PHYSICAL, permission,
                /*secure=*/ false, VpnManager.TYPE_VPN_NONE, /*excludeLocalRoutes=*/ false);
    }

    private NativeNetworkConfig nativeNetworkConfigVpn(int netId, boolean secure, int vpnType) {
        return new NativeNetworkConfig(netId, NativeNetworkType.VIRTUAL, INetd.PERMISSION_NONE,
                secure, vpnType, /*excludeLocalRoutes=*/ false);
    }

    @Test
    public void testNetworkAgentCallbacks() throws Exception {
        // Keeps track of the order of events that happen in this test.
        final LinkedBlockingQueue<String> eventOrder = new LinkedBlockingQueue<>();

        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        final TestNetworkCallback callback = new TestNetworkCallback();

        // Expectations for state when various callbacks fire. These expectations run on the handler
        // thread and not on the test thread because they need to prevent the handler thread from
        // advancing while they examine state.

        // 1. When onCreated fires, netd has been told to create the network.
        final Consumer<NetworkAgent> onNetworkCreated = (agent) -> {
            eventOrder.offer("onNetworkCreated");
            try {
                verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                        agent.getNetwork().getNetId(), INetd.PERMISSION_NONE));
            } catch (RemoteException impossible) {
                fail();
            }
        };

        // 2. onNetworkUnwanted isn't precisely ordered with respect to any particular events. Just
        //    check that it is fired at some point after disconnect.
        final Consumer<NetworkAgent> onNetworkUnwanted = (agent) -> {
            eventOrder.offer("onNetworkUnwanted");
        };

        // 3. While the teardown timer is running, connectivity APIs report the network is gone, but
        //    netd has not yet been told to destroy it.
        final Consumer<Network> duringTeardown = (network) -> {
            eventOrder.offer("timePasses");
            assertNull(mCm.getLinkProperties(network));
            try {
                verify(mMockNetd, never()).networkDestroy(network.getNetId());
            } catch (RemoteException impossible) {
                fail();
            }
        };

        // 4. After onNetworkDisconnected is called, connectivity APIs report the network is gone,
        // and netd has been told to destroy it.
        final Consumer<NetworkAgent> onNetworkDisconnected = (agent) -> {
            eventOrder.offer("onNetworkDisconnected");
            assertNull(mCm.getLinkProperties(agent.getNetwork()));
            try {
                verify(mMockNetd).networkDestroy(agent.getNetwork().getNetId());
            } catch (RemoteException impossible) {
                fail();
            }
        };

        final NetworkAgentWrapper.Callbacks callbacks = new NetworkAgentWrapper.Callbacks(
                onNetworkCreated, onNetworkUnwanted, onNetworkDisconnected);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, callbacks);

        if (mService.shouldCreateNetworksImmediately()) {
            assertEquals("onNetworkCreated", eventOrder.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } else {
            assertNull(eventOrder.poll());
        }

        // Connect a network, and file a request for it after it has come up, to ensure the nascent
        // timer is cleared and the test does not have to wait for it. Filing the request after the
        // network has come up is necessary because ConnectivityService does not appear to clear the
        // nascent timer if the first request satisfied by the network was filed before the network
        // connected.
        // TODO: fix this bug, file the request before connecting, and remove the waitForIdle.
        mWiFiAgent.connectWithoutInternet();
        if (!mService.shouldCreateNetworksImmediately()) {
            assertEquals("onNetworkCreated", eventOrder.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } else {
            waitForIdle();
            assertNull(eventOrder.poll());
        }
        mCm.requestNetwork(request, callback);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        // Set teardown delay and make sure CS has processed it.
        mWiFiAgent.getNetworkAgent().setTeardownDelayMillis(300);
        waitForIdle();

        // Post the duringTeardown lambda to the handler so it fires while teardown is in progress.
        // The delay must be long enough it will run after the unregisterNetworkCallback has torn
        // down the network and started the teardown timer, and short enough that the lambda is
        // scheduled to run before the teardown timer.
        final Handler h = new Handler(mCsHandlerThread.getLooper());
        h.postDelayed(() -> duringTeardown.accept(mWiFiAgent.getNetwork()), 150);

        // Disconnect the network and check that events happened in the right order.
        mCm.unregisterNetworkCallback(callback);
        assertEquals("onNetworkUnwanted", eventOrder.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("timePasses", eventOrder.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("onNetworkDisconnected", eventOrder.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        mCm.unregisterNetworkCallback(callback);
    }

    @Test
    public void testExplicitlySelected() throws Exception {
        final NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities().addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        // Bring up validated cell
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);

        // Bring up unvalidated wifi with explicitlySelected=true.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(true, false);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        // Cell remains the default.
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());

        // Expect a high-priority NO_INTERNET notification.
        expectUnvalidationCheckWillNotify(mWiFiAgent, NotificationType.NO_INTERNET);

        // Lower WiFi's score to lower than cell, and check that it doesn't disconnect because
        // it's explicitly selected.
        mWiFiAgent.adjustScore(-40);
        mWiFiAgent.adjustScore(40);
        callback.assertNoCallback();

        // If the user chooses yes on the "No Internet access, stay connected?" dialog, we switch to
        // wifi even though it's unvalidated.
        mCm.setAcceptUnvalidated(mWiFiAgent.getNetwork(), true, false);
        callback.expectLosing(mCellAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());

        // Disconnect wifi, and then reconnect, again with explicitlySelected=true.
        mWiFiAgent.disconnect();
        expectDisconnectAndClearNotifications(callback, mWiFiAgent, NotificationType.NO_INTERNET);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(true, false);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        // Expect a high-priority NO_INTERNET notification.
        expectUnvalidationCheckWillNotify(mWiFiAgent, NotificationType.NO_INTERNET);

        // If the user chooses no on the "No Internet access, stay connected?" dialog, we ask the
        // network to disconnect.
        mCm.setAcceptUnvalidated(mWiFiAgent.getNetwork(), false, false);
        expectDisconnectAndClearNotifications(callback, mWiFiAgent, NotificationType.NO_INTERNET);
        reset(mNotificationManager);

        // Reconnect, again with explicitlySelected=true, but this time validate.
        // Expect no notifications.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(true, false);
        mWiFiAgent.connect(true);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        expectUnvalidationCheckWillNotNotify(mWiFiAgent);

        // Now request cell so it doesn't disconnect during the test
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .clearCapabilities().addTransportType(TRANSPORT_CELLULAR).build();
        final TestNetworkCallback cellCallback = new TestNetworkCallback();
        mCm.requestNetwork(cellRequest, cellCallback);

        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mEthernetAgent.connect(true);
        callback.expectAvailableCallbacksUnvalidated(mEthernetAgent);
        callback.expectLosing(mWiFiAgent);
        callback.expectCaps(mEthernetAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        assertEquals(mEthernetAgent.getNetwork(), mCm.getActiveNetwork());
        callback.assertNoCallback();

        // Disconnect wifi, and then reconnect as if the user had selected "yes, don't ask again"
        // (i.e., with explicitlySelected=true and acceptUnvalidated=true). Expect to switch to
        // wifi immediately.
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(true, true);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectLosing(mEthernetAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        mEthernetAgent.disconnect();
        callback.expect(LOST, mEthernetAgent);
        expectUnvalidationCheckWillNotNotify(mWiFiAgent);

        // Disconnect and reconnect with explicitlySelected=false and acceptUnvalidated=true.
        // Check that the network is not scored specially and that the device prefers cell data.
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(false, true);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        expectUnvalidationCheckWillNotNotify(mWiFiAgent);

        // Clean up.
        mWiFiAgent.disconnect();
        mCellAgent.disconnect();

        callback.expect(LOST, mWiFiAgent);
        callback.expect(LOST, mCellAgent);
        mCm.unregisterNetworkCallback(cellCallback);
    }

    private void doTestFirstEvaluation(
            @NonNull final Consumer<TestNetworkAgentWrapper> doConnect,
            final boolean waitForSecondCaps,
            final boolean evaluatedByValidation)
            throws Exception {
        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        doConnect.accept(mWiFiAgent);
        // Expect the available callbacks, but don't require specific values for their arguments
        // since this method doesn't know how the network was connected.
        callback.expect(AVAILABLE, mWiFiAgent);
        callback.expect(NETWORK_CAPS_UPDATED, mWiFiAgent);
        callback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent);
        callback.expect(BLOCKED_STATUS, mWiFiAgent);
        if (waitForSecondCaps) {
            // This is necessary because of b/245893397, the same bug that happens where we use
            // expectAvailableDoubleValidatedCallbacks.
            callback.expect(NETWORK_CAPS_UPDATED, mWiFiAgent);
        }
        final NetworkAgentInfo nai =
                mService.getNetworkAgentInfoForNetwork(mWiFiAgent.getNetwork());
        final long firstEvaluation = nai.getFirstEvaluationConcludedTime();
        if (evaluatedByValidation) {
            assertNotEquals(0L, firstEvaluation);
        } else {
            assertEquals(0L, firstEvaluation);
        }
        mService.scheduleEvaluationTimeout(mWiFiAgent.getNetwork(), 0L /* timeout */);
        waitForIdle();
        if (evaluatedByValidation) {
            assertEquals(firstEvaluation, nai.getFirstEvaluationConcludedTime());
        } else {
            assertNotEquals(0L, nai.getFirstEvaluationConcludedTime());
        }
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);

        mCm.unregisterNetworkCallback(callback);
    }

    @Test
    public void testEverEvaluated() throws Exception {
        doTestFirstEvaluation(naw -> naw.connect(true /* validated */),
                true /* waitForSecondCaps */, true /* immediatelyEvaluated */);
        doTestFirstEvaluation(naw -> naw.connectWithPartialConnectivity(),
                true /* waitForSecondCaps */, true /* immediatelyEvaluated */);
        doTestFirstEvaluation(naw -> naw.connectWithCaptivePortal(TEST_REDIRECT_URL, false),
                true /* waitForSecondCaps */, true /* immediatelyEvaluated */);
        doTestFirstEvaluation(naw -> naw.connect(false /* validated */),
                false /* waitForSecondCaps */, false /* immediatelyEvaluated */);
    }

    private void tryNetworkFactoryRequests(int capability) throws Exception {
        // Verify NOT_RESTRICTED is set appropriately
        final NetworkCapabilities nc = new NetworkRequest.Builder().addCapability(capability)
                .build().networkCapabilities;
        if (capability == NET_CAPABILITY_CBS || capability == NET_CAPABILITY_DUN
                || capability == NET_CAPABILITY_EIMS || capability == NET_CAPABILITY_FOTA
                || capability == NET_CAPABILITY_IA || capability == NET_CAPABILITY_IMS
                || capability == NET_CAPABILITY_RCS || capability == NET_CAPABILITY_XCAP
                || capability == NET_CAPABILITY_VSIM || capability == NET_CAPABILITY_BIP
                || capability == NET_CAPABILITY_ENTERPRISE || capability == NET_CAPABILITY_MMTEL) {
            assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        } else {
            assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        }

        NetworkCapabilities filter = new NetworkCapabilities();
        filter.addTransportType(TRANSPORT_CELLULAR);
        filter.addCapability(capability);
        // Add NOT_VCN_MANAGED capability into filter unconditionally since some requests will add
        // NOT_VCN_MANAGED automatically but not for NetworkCapabilities,
        // see {@code NetworkCapabilities#deduceNotVcnManagedCapability} for more details.
        filter.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        final HandlerThread handlerThread = new HandlerThread("testNetworkFactoryRequests");
        handlerThread.start();
        final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactory", filter, mCsHandlerThread);
        testFactory.setScoreFilter(45);
        testFactory.register();

        final NetworkCallback networkCallback;
        if (capability != NET_CAPABILITY_INTERNET) {
            // If the capability passed in argument is part of the default request, then the
            // factory will see the default request. Otherwise the filter will prevent the
            // factory from seeing it. In that case, add a request so it can be tested.
            assertFalse(testFactory.getMyStartRequested());
            NetworkRequest request = new NetworkRequest.Builder().addCapability(capability).build();
            networkCallback = new NetworkCallback();
            mCm.requestNetwork(request, networkCallback);
        } else {
            networkCallback = null;
        }
        testFactory.expectRequestAdd();
        testFactory.assertRequestCountEquals(1);
        assertTrue(testFactory.getMyStartRequested());

        // Now bring in a higher scored network.
        TestNetworkAgentWrapper testAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        // When testAgent connects, because of its score (50 legacy int / cell transport)
        // it will beat or equal the testFactory's offer, so the request will be removed.
        // Note the agent as validated only if the capability is INTERNET, as it's the only case
        // where it makes sense.
        testAgent.connect(NET_CAPABILITY_INTERNET == capability /* validated */);
        testAgent.addCapability(capability);
        testFactory.expectRequestRemove();
        testFactory.assertRequestCountEquals(0);
        assertFalse(testFactory.getMyStartRequested());

        // Add a request and make sure it's not sent to the factory, because the agent
        // is satisfying it better.
        final NetworkCallback cb = new ConnectivityManager.NetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder().addCapability(capability).build(), cb);
        expectNoRequestChanged(testFactory);
        testFactory.assertRequestCountEquals(0);
        assertFalse(testFactory.getMyStartRequested());

        // If using legacy scores, make the test agent weak enough to have the exact same score as
        // the factory (50 for cell - 5 adjustment). Make sure the factory doesn't see the request.
        // If not using legacy score, this is a no-op and the "same score removes request" behavior
        // has already been tested above.
        testAgent.adjustScore(-5);
        expectNoRequestChanged(testFactory);
        assertFalse(testFactory.getMyStartRequested());

        // Make the test agent weak enough that the factory will see the two requests (the one that
        // was just sent, and either the default one or the one sent at the top of this test if
        // the default won't be seen).
        testAgent.setScore(new NetworkScore.Builder().setLegacyInt(2).setExiting(true).build());
        testFactory.expectRequestAdds(2);
        testFactory.assertRequestCountEquals(2);
        assertTrue(testFactory.getMyStartRequested());

        // Now unregister and make sure the request is removed.
        mCm.unregisterNetworkCallback(cb);
        testFactory.expectRequestRemove();

        // Bring in a bunch of requests.
        assertEquals(1, testFactory.getMyRequestCount());
        ConnectivityManager.NetworkCallback[] networkCallbacks =
                new ConnectivityManager.NetworkCallback[10];
        for (int i = 0; i< networkCallbacks.length; i++) {
            networkCallbacks[i] = new ConnectivityManager.NetworkCallback();
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(capability);
            mCm.requestNetwork(builder.build(), networkCallbacks[i]);
        }
        testFactory.expectRequestAdds(10);
        testFactory.assertRequestCountEquals(11); // +1 for the default/test specific request
        assertTrue(testFactory.getMyStartRequested());

        // Remove the requests.
        for (int i = 0; i < networkCallbacks.length; i++) {
            mCm.unregisterNetworkCallback(networkCallbacks[i]);
        }
        testFactory.expectRequestRemoves(10);
        testFactory.assertRequestCountEquals(1);
        assertTrue(testFactory.getMyStartRequested());

        // Adjust the agent score up again. Expect the request to be withdrawn.
        testAgent.setScore(new NetworkScore.Builder().setLegacyInt(50).build());
        testFactory.expectRequestRemove();
        testFactory.assertRequestCountEquals(0);
        assertFalse(testFactory.getMyStartRequested());

        // Drop the higher scored network.
        testAgent.disconnect();
        testFactory.expectRequestAdd();
        testFactory.assertRequestCountEquals(1);
        assertEquals(1, testFactory.getMyRequestCount());
        assertTrue(testFactory.getMyStartRequested());

        testFactory.terminate();
        testFactory.assertNoRequestChanged();
        if (networkCallback != null) mCm.unregisterNetworkCallback(networkCallback);

        handlerThread.quitSafely();
        handlerThread.join();
    }

    @Test
    public void testNetworkFactoryRequests() throws Exception {
        tryNetworkFactoryRequests(NET_CAPABILITY_MMS);
        tryNetworkFactoryRequests(NET_CAPABILITY_SUPL);
        tryNetworkFactoryRequests(NET_CAPABILITY_DUN);
        tryNetworkFactoryRequests(NET_CAPABILITY_FOTA);
        tryNetworkFactoryRequests(NET_CAPABILITY_IMS);
        tryNetworkFactoryRequests(NET_CAPABILITY_CBS);
        tryNetworkFactoryRequests(NET_CAPABILITY_WIFI_P2P);
        tryNetworkFactoryRequests(NET_CAPABILITY_IA);
        tryNetworkFactoryRequests(NET_CAPABILITY_RCS);
        tryNetworkFactoryRequests(NET_CAPABILITY_MMTEL);
        tryNetworkFactoryRequests(NET_CAPABILITY_XCAP);
        tryNetworkFactoryRequests(NET_CAPABILITY_ENTERPRISE);
        tryNetworkFactoryRequests(NET_CAPABILITY_EIMS);
        tryNetworkFactoryRequests(NET_CAPABILITY_NOT_METERED);
        tryNetworkFactoryRequests(NET_CAPABILITY_INTERNET);
        tryNetworkFactoryRequests(NET_CAPABILITY_TRUSTED);
        tryNetworkFactoryRequests(NET_CAPABILITY_NOT_VPN);
        tryNetworkFactoryRequests(NET_CAPABILITY_VSIM);
        tryNetworkFactoryRequests(NET_CAPABILITY_BIP);
        // Skipping VALIDATED and CAPTIVE_PORTAL as they're disallowed.
    }

    @Test
    public void testRegisterIgnoringScore() throws Exception {
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.setScore(new NetworkScore.Builder().setLegacyInt(90).build());
        mWiFiAgent.connect(true /* validated */);

        // Make sure the factory sees the default network
        final NetworkCapabilities filter = new NetworkCapabilities();
        filter.addTransportType(TRANSPORT_CELLULAR);
        filter.addCapability(NET_CAPABILITY_INTERNET);
        filter.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        final HandlerThread handlerThread = new HandlerThread("testNetworkFactoryRequests");
        handlerThread.start();
        final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactory", filter, mCsHandlerThread);
        testFactory.register();

        final MockNetworkFactory testFactoryAll = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactoryAll", filter, mCsHandlerThread);
        testFactoryAll.registerIgnoringScore();

        // The regular test factory should not see the request, because WiFi is stronger than cell.
        expectNoRequestChanged(testFactory);
        // With ignoringScore though the request is seen.
        testFactoryAll.expectRequestAdd();

        // The legacy int will be ignored anyway, set the only other knob to true
        mWiFiAgent.setScore(new NetworkScore.Builder().setLegacyInt(110)
                .setTransportPrimary(true).build());

        expectNoRequestChanged(testFactory); // still not seeing the request
        expectNoRequestChanged(testFactoryAll); // still seeing the request

        mWiFiAgent.disconnect();
        handlerThread.quitSafely();
        handlerThread.join();
    }

    @Test
    public void testNetworkFactoryUnregister() throws Exception {
        // Make sure the factory sees the default network
        final NetworkCapabilities filter = new NetworkCapabilities();
        filter.addCapability(NET_CAPABILITY_INTERNET);
        filter.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);

        final HandlerThread handlerThread = new HandlerThread("testNetworkFactoryRequests");
        handlerThread.start();

        // Checks that calling setScoreFilter on a NetworkFactory immediately before closing it
        // does not crash.
        for (int i = 0; i < 100; i++) {
            final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                    mServiceContext, "testFactory", filter, mCsHandlerThread);
            // Register the factory and don't be surprised when the default request arrives.
            testFactory.register();
            testFactory.expectRequestAdd();

            testFactory.setScoreFilter(42);
            testFactory.terminate();
            testFactory.assertNoRequestChanged();

            if (i % 2 == 0) {
                try {
                    testFactory.register();
                    fail("Re-registering terminated NetworkFactory should throw");
                } catch (IllegalStateException expected) {
                }
            }
        }
        handlerThread.quitSafely();
        handlerThread.join();
    }

    @Test
    public void testNoMutableNetworkRequests() throws Exception {
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0 /* requestCode */, new Intent("a"), FLAG_IMMUTABLE);
        final NetworkRequest request1 = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_VALIDATED)
                .build();
        final NetworkRequest request2 = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL)
                .build();

        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected, () -> mCm.requestNetwork(request1, new NetworkCallback()));
        assertThrows(expected, () -> mCm.requestNetwork(request1, pendingIntent));
        assertThrows(expected, () -> mCm.requestNetwork(request2, new NetworkCallback()));
        assertThrows(expected, () -> mCm.requestNetwork(request2, pendingIntent));
    }

    @Test
    public void testNoAllowedUidsInNetworkRequests() throws Exception {
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0 /* requestCode */, new Intent("a"), FLAG_IMMUTABLE);
        final NetworkRequest r = new NetworkRequest.Builder().build();
        final ArraySet<Integer> allowedUids = new ArraySet<>();
        allowedUids.add(6);
        allowedUids.add(9);
        r.networkCapabilities.setAllowedUids(allowedUids);

        final Handler handler = new Handler(ConnectivityThread.getInstanceLooper());
        final NetworkCallback cb = new NetworkCallback();

        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected, () -> mCm.requestNetwork(r, cb));
        assertThrows(expected, () -> mCm.requestNetwork(r, pendingIntent));
        assertThrows(expected, () -> mCm.registerNetworkCallback(r, cb));
        assertThrows(expected, () -> mCm.registerNetworkCallback(r, cb, handler));
        assertThrows(expected, () -> mCm.registerNetworkCallback(r, pendingIntent));
        assertThrows(expected, () -> mCm.registerBestMatchingNetworkCallback(r, cb, handler));

        // Make sure that resetting the access UIDs to the empty set will allow calling
        // requestNetwork and registerNetworkCallback.
        r.networkCapabilities.setAllowedUids(Collections.emptySet());
        mCm.requestNetwork(r, cb);
        mCm.unregisterNetworkCallback(cb);
        mCm.registerNetworkCallback(r, cb);
        mCm.unregisterNetworkCallback(cb);
    }

    @Test
    public void testMMSonWiFi() throws Exception {
        // Test bringing up cellular without MMS NetworkRequest gets reaped
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.addCapability(NET_CAPABILITY_MMS);
        mCellAgent.connectWithoutInternet();
        mCellAgent.expectDisconnected();
        waitForIdle();
        assertEmpty(mCm.getAllNetworks());
        verifyNoNetwork();

        // Test bringing up validated WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        final ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTED);
        mWiFiAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);

        // Register MMS NetworkRequest
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.requestNetwork(builder.build(), networkCallback);

        // Test bringing up unvalidated cellular with MMS
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.addCapability(NET_CAPABILITY_MMS);
        mCellAgent.connectWithoutInternet();
        networkCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        verifyActiveNetwork(TRANSPORT_WIFI);

        // Test releasing NetworkRequest disconnects cellular with MMS
        mCm.unregisterNetworkCallback(networkCallback);
        mCellAgent.expectDisconnected();
        verifyActiveNetwork(TRANSPORT_WIFI);
    }

    @Test
    public void testMMSonCell() throws Exception {
        // Test bringing up cellular without MMS
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        ExpectedBroadcast b = expectConnectivityAction(TYPE_MOBILE, DetailedState.CONNECTED);
        mCellAgent.connect(false);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_CELLULAR);

        // Register MMS NetworkRequest
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.requestNetwork(builder.build(), networkCallback);

        // Test bringing up MMS cellular network
        TestNetworkAgentWrapper
                mmsNetworkAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mmsNetworkAgent.addCapability(NET_CAPABILITY_MMS);
        mmsNetworkAgent.connectWithoutInternet();
        networkCallback.expectAvailableCallbacksUnvalidated(mmsNetworkAgent);
        verifyActiveNetwork(TRANSPORT_CELLULAR);

        // Test releasing MMS NetworkRequest does not disconnect main cellular NetworkAgent
        mCm.unregisterNetworkCallback(networkCallback);
        mmsNetworkAgent.expectDisconnected();
        verifyActiveNetwork(TRANSPORT_CELLULAR);
    }

    @Test
    public void testPartialConnectivity() throws Exception {
        // Register network callback.
        NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities().addCapability(NET_CAPABILITY_INTERNET)
                .build();
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        // Bring up validated mobile data.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);

        // Bring up wifi with partial connectivity.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithPartialConnectivity();
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));

        // Mobile data should be the default network.
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        callback.assertNoCallback();

        // Expect a PARTIAL_CONNECTIVITY notification. The notification appears as soon as partial
        // connectivity is detected, and is low priority because the network was not explicitly
        // selected by the user. This happens if we reconnect to a network where the user previously
        // accepted partial connectivity without checking "always".
        expectNotification(mWiFiAgent, NotificationType.PARTIAL_CONNECTIVITY);

        // With HTTPS probe disabled, NetworkMonitor should pass the network validation with http
        // probe.
        mWiFiAgent.setNetworkPartialValid(false /* privateDnsProbeSent */);
        // If the user chooses yes to use this partial connectivity wifi, switch the default
        // network to wifi and check if wifi becomes valid or not.
        mCm.setAcceptPartialConnectivity(mWiFiAgent.getNetwork(), true /* accept */,
                false /* always */);
        // If user accepts partial connectivity network,
        // NetworkMonitor#setAcceptPartialConnectivity() should be called too.
        waitForIdle();
        verify(mWiFiAgent.mNetworkMonitor, times(1)).setAcceptPartialConnectivity();

        // Need a trigger point to let NetworkMonitor tell ConnectivityService that the network is
        // validated.
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        callback.expectLosing(mCellAgent);
        NetworkCapabilities nc =
                callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        assertTrue(nc.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());

        // Once the network validates, the notification disappears.
        expectClearNotification(mWiFiAgent, NotificationType.PARTIAL_CONNECTIVITY);

        // Disconnect and reconnect wifi with partial connectivity again.
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithPartialConnectivity();
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));

        // Mobile data should be the default network.
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        waitForIdle();

        // Expect a low-priority PARTIAL_CONNECTIVITY notification as soon as partial connectivity
        // is detected.
        expectNotification(mWiFiAgent, NotificationType.PARTIAL_CONNECTIVITY);

        // If the user chooses no, disconnect wifi immediately.
        mCm.setAcceptPartialConnectivity(mWiFiAgent.getNetwork(), false /* accept */,
                false /* always */);
        callback.expect(LOST, mWiFiAgent);
        expectClearNotification(mWiFiAgent, NotificationType.PARTIAL_CONNECTIVITY);
        reset(mNotificationManager);

        // If the user accepted partial connectivity before, and the device connects to that network
        // again, but now the network has full connectivity, then the network shouldn't contain
        // NET_CAPABILITY_PARTIAL_CONNECTIVITY.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        // acceptUnvalidated is also used as setting for accepting partial networks.
        mWiFiAgent.explicitlySelected(true /* explicitlySelected */, true /* acceptUnvalidated */);
        mWiFiAgent.connect(true);
        expectUnvalidationCheckWillNotNotify(mWiFiAgent);

        // If user accepted partial connectivity network before,
        // NetworkMonitor#setAcceptPartialConnectivity() will be called in
        // ConnectivityService#updateNetworkInfo().
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        verify(mWiFiAgent.mNetworkMonitor, times(1)).setAcceptPartialConnectivity();
        callback.expectLosing(mCellAgent);
        nc = callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        assertFalse(nc.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));

        // Wifi should be the default network.
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);

        // The user accepted partial connectivity and selected "don't ask again". Now the user
        // reconnects to the partial connectivity network. Switch to wifi as soon as partial
        // connectivity is detected.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(true /* explicitlySelected */, true /* acceptUnvalidated */);
        mWiFiAgent.connectWithPartialConnectivity();
        // If user accepted partial connectivity network before,
        // NetworkMonitor#setAcceptPartialConnectivity() will be called in
        // ConnectivityService#updateNetworkInfo().
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        verify(mWiFiAgent.mNetworkMonitor, times(1)).setAcceptPartialConnectivity();
        callback.expectLosing(mCellAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));
        expectUnvalidationCheckWillNotNotify(mWiFiAgent);

        mWiFiAgent.setNetworkValid(false /* privateDnsProbeSent */);

        // Need a trigger point to let NetworkMonitor tell ConnectivityService that the network is
        // validated.
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);

        // If the user accepted partial connectivity, and the device auto-reconnects to the partial
        // connectivity network, it should contain both PARTIAL_CONNECTIVITY and VALIDATED.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(false /* explicitlySelected */, true /* acceptUnvalidated */);

        // NetworkMonitor will immediately (once the HTTPS probe fails...) report the network as
        // valid, because ConnectivityService calls setAcceptPartialConnectivity before it calls
        // notifyNetworkConnected.
        mWiFiAgent.connectWithPartialValidConnectivity(false /* privateDnsProbeSent */);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        verify(mWiFiAgent.mNetworkMonitor, times(1)).setAcceptPartialConnectivity();
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY)
                && c.hasCapability(NET_CAPABILITY_VALIDATED));
        expectUnvalidationCheckWillNotNotify(mWiFiAgent);
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        verifyNoMoreInteractions(mNotificationManager);
    }

    @Test
    public void testCaptivePortalOnPartialConnectivity() throws Exception {
        final TestNetworkCallback wifiCallback = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        mCm.registerNetworkCallback(wifiRequest, wifiCallback);

        final TestNetworkCallback validatedCallback = new TestNetworkCallback();
        final NetworkRequest validatedRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_VALIDATED).build();
        mCm.registerNetworkCallback(validatedRequest, validatedCallback);

        // Bring up a network with a captive portal.
        // Expect onAvailable callback of listen for NET_CAPABILITY_CAPTIVE_PORTAL.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        String redirectUrl = "http://android.com/path";
        mWiFiAgent.connectWithCaptivePortal(redirectUrl, false /* privateDnsProbeSent */);
        wifiCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mWiFiAgent.waitForRedirectUrl(), redirectUrl);

        // This is necessary because of b/245893397, the same bug that happens where we use
        // expectAvailableDoubleValidatedCallbacks.
        // TODO : fix b/245893397 and remove this.
        wifiCallback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL));

        // Check that startCaptivePortalApp sends the expected command to NetworkMonitor.
        mCm.startCaptivePortalApp(mWiFiAgent.getNetwork());
        verify(mWiFiAgent.mNetworkMonitor, timeout(TIMEOUT_MS).times(1)).launchCaptivePortalApp();

        // Report that the captive portal is dismissed with partial connectivity, and check that
        // callbacks are fired with PARTIAL and without CAPTIVE_PORTAL.
        mWiFiAgent.setNetworkPartial();
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        waitForIdle();
        wifiCallback.expectCaps(mWiFiAgent,
                c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY)
                        && !c.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL));

        // Report partial connectivity is accepted.
        mWiFiAgent.setNetworkPartialValid(false /* privateDnsProbeSent */);
        mCm.setAcceptPartialConnectivity(mWiFiAgent.getNetwork(), true /* accept */,
                false /* always */);
        waitForIdle();
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        wifiCallback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        validatedCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        validatedCallback.expectCaps(mWiFiAgent,
                c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));

        mCm.unregisterNetworkCallback(wifiCallback);
        mCm.unregisterNetworkCallback(validatedCallback);
    }

    @Test
    public void testCaptivePortal() throws Exception {
        final TestNetworkCallback captivePortalCallback = new TestNetworkCallback();
        final NetworkRequest captivePortalRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL).build();
        mCm.registerNetworkCallback(captivePortalRequest, captivePortalCallback);

        final TestNetworkCallback validatedCallback = new TestNetworkCallback();
        final NetworkRequest validatedRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_VALIDATED).build();
        mCm.registerNetworkCallback(validatedRequest, validatedCallback);

        // Bring up a network with a captive portal.
        // Expect onAvailable callback of listen for NET_CAPABILITY_CAPTIVE_PORTAL.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        String firstRedirectUrl = "http://example.com/firstPath";
        mWiFiAgent.connectWithCaptivePortal(firstRedirectUrl, false /* privateDnsProbeSent */);
        captivePortalCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mWiFiAgent.waitForRedirectUrl(), firstRedirectUrl);

        // Take down network.
        // Expect onLost callback.
        mWiFiAgent.disconnect();
        captivePortalCallback.expect(LOST, mWiFiAgent);

        // Bring up a network with a captive portal.
        // Expect onAvailable callback of listen for NET_CAPABILITY_CAPTIVE_PORTAL.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        String secondRedirectUrl = "http://example.com/secondPath";
        mWiFiAgent.connectWithCaptivePortal(secondRedirectUrl, false /* privateDnsProbeSent */);
        captivePortalCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mWiFiAgent.waitForRedirectUrl(), secondRedirectUrl);

        // Make captive portal disappear then revalidate.
        // Expect onLost callback because network no longer provides NET_CAPABILITY_CAPTIVE_PORTAL.
        mWiFiAgent.setNetworkValid(false /* privateDnsProbeSent */);
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        captivePortalCallback.expect(LOST, mWiFiAgent);

        // Expect NET_CAPABILITY_VALIDATED onAvailable callback.
        validatedCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);

        // Break network connectivity.
        // Expect NET_CAPABILITY_VALIDATED onLost callback.
        mWiFiAgent.setNetworkInvalid(false /* invalidBecauseOfPrivateDns */);
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);
        validatedCallback.expect(LOST, mWiFiAgent);
    }

    private Intent startCaptivePortalApp(TestNetworkAgentWrapper networkAgent) throws Exception {
        Network network = networkAgent.getNetwork();
        // Check that startCaptivePortalApp sends the expected command to NetworkMonitor.
        mCm.startCaptivePortalApp(network);
        waitForIdle();
        verify(networkAgent.mNetworkMonitor).launchCaptivePortalApp();

        // NetworkMonitor uses startCaptivePortal(Network, Bundle) (startCaptivePortalAppInternal)
        final Bundle testBundle = new Bundle();
        final String testKey = "testkey";
        final String testValue = "testvalue";
        testBundle.putString(testKey, testValue);
        mServiceContext.setPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                PERMISSION_GRANTED);
        mCm.startCaptivePortalApp(network, testBundle);
        final Intent signInIntent = mServiceContext.expectStartActivityIntent(TIMEOUT_MS);
        assertEquals(ACTION_CAPTIVE_PORTAL_SIGN_IN, signInIntent.getAction());
        assertEquals(testValue, signInIntent.getStringExtra(testKey));
        return signInIntent;
    }

    @Test
    public void testCaptivePortalApp() throws Exception {
        final TestNetworkCallback captivePortalCallback = new TestNetworkCallback();
        final NetworkRequest captivePortalRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL).build();
        mCm.registerNetworkCallback(captivePortalRequest, captivePortalCallback);

        final TestNetworkCallback validatedCallback = new TestNetworkCallback();
        final NetworkRequest validatedRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_VALIDATED).build();
        mCm.registerNetworkCallback(validatedRequest, validatedCallback);

        // Bring up wifi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        validatedCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        Network wifiNetwork = mWiFiAgent.getNetwork();

        // Check that calling startCaptivePortalApp does nothing.
        final int fastTimeoutMs = 100;
        mCm.startCaptivePortalApp(wifiNetwork);
        waitForIdle();
        verify(mWiFiAgent.mNetworkMonitor, never()).launchCaptivePortalApp();
        mServiceContext.expectNoStartActivityIntent(fastTimeoutMs);

        // Turn into a captive portal.
        mWiFiAgent.setNetworkPortal("http://example.com", false /* privateDnsProbeSent */);
        mCm.reportNetworkConnectivity(wifiNetwork, false);
        captivePortalCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        validatedCallback.expect(LOST, mWiFiAgent);
        // This is necessary because of b/245893397, the same bug that happens where we use
        // expectAvailableDoubleValidatedCallbacks.
        // TODO : fix b/245893397 and remove this.
        captivePortalCallback.expectCaps(mWiFiAgent);

        startCaptivePortalApp(mWiFiAgent);

        // Report that the captive portal is dismissed, and check that callbacks are fired
        mWiFiAgent.setNetworkValid(false /* privateDnsProbeSent */);
        mWiFiAgent.mNetworkMonitor.forceReevaluation(Process.myUid());
        validatedCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        captivePortalCallback.expect(LOST, mWiFiAgent);

        mCm.unregisterNetworkCallback(validatedCallback);
        mCm.unregisterNetworkCallback(captivePortalCallback);
    }

    @Test
    public void testCaptivePortalApp_IgnoreNetwork() throws Exception {
        final TestNetworkCallback captivePortalCallback = new TestNetworkCallback();
        final NetworkRequest captivePortalRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL).build();
        mCm.registerNetworkCallback(captivePortalRequest, captivePortalCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connectWithCaptivePortal(TEST_REDIRECT_URL, false);
        captivePortalCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        final Intent signInIntent = startCaptivePortalApp(mWiFiAgent);
        final CaptivePortal captivePortal = signInIntent
                .getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);

        captivePortal.ignoreNetwork();
        waitForIdle();

        // Since network will disconnect, ensure no notification of response to NetworkMonitor
        verify(mWiFiAgent.mNetworkMonitor, never())
                .notifyCaptivePortalAppFinished(CaptivePortal.APP_RETURN_UNWANTED);

        // Report that the network is disconnected
        mWiFiAgent.expectDisconnected();
        mWiFiAgent.expectPreventReconnectReceived();
        verify(mWiFiAgent.mNetworkMonitor).notifyNetworkDisconnected();
        captivePortalCallback.expect(LOST, mWiFiAgent);

        mCm.unregisterNetworkCallback(captivePortalCallback);
    }

    @Test
    public void testAvoidOrIgnoreCaptivePortals() throws Exception {
        final TestNetworkCallback captivePortalCallback = new TestNetworkCallback();
        final NetworkRequest captivePortalRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL).build();
        mCm.registerNetworkCallback(captivePortalRequest, captivePortalCallback);

        final TestNetworkCallback validatedCallback = new TestNetworkCallback();
        final NetworkRequest validatedRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_VALIDATED).build();
        mCm.registerNetworkCallback(validatedRequest, validatedCallback);

        setCaptivePortalMode(ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_AVOID);
        // Bring up a network with a captive portal.
        // Expect it to fail to connect and not result in any callbacks.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        final String firstRedirectUrl = "http://example.com/firstPath";

        mWiFiAgent.connectWithCaptivePortal(firstRedirectUrl, false /* privateDnsProbeSent */);
        mWiFiAgent.expectDisconnected();
        mWiFiAgent.expectPreventReconnectReceived();

        assertNoCallbacks(captivePortalCallback, validatedCallback);
    }

    @Test
    public void testNoAvoidCaptivePortalOnWearProxy() throws Exception {
        // Bring up a BLUETOOTH network which is companion proxy on wear
        // then set captive portal.
        mockHasSystemFeature(PackageManager.FEATURE_WATCH, true);
        setCaptivePortalMode(ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_AVOID);
        TestNetworkAgentWrapper btAgent = new TestNetworkAgentWrapper(TRANSPORT_BLUETOOTH);
        final String firstRedirectUrl = "http://example.com/firstPath";

        btAgent.connectWithCaptivePortal(firstRedirectUrl, false /* privateDnsProbeSent */);
        btAgent.assertNotDisconnected(TIMEOUT_MS);
    }

    @Test
    public void testAvoidCaptivePortalOnBluetooth() throws Exception {
        // When not on Wear, BLUETOOTH is just regular network,
        // then set captive portal.
        mockHasSystemFeature(PackageManager.FEATURE_WATCH, false);
        setCaptivePortalMode(ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_AVOID);
        TestNetworkAgentWrapper btAgent = new TestNetworkAgentWrapper(TRANSPORT_BLUETOOTH);
        final String firstRedirectUrl = "http://example.com/firstPath";

        btAgent.connectWithCaptivePortal(firstRedirectUrl, false /* privateDnsProbeSent */);

        btAgent.expectDisconnected();
        btAgent.expectPreventReconnectReceived();
    }

    @Test
    public void testCaptivePortalApi() throws Exception {
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        final TestNetworkCallback captivePortalCallback = new TestNetworkCallback();
        final NetworkRequest captivePortalRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL).build();
        mCm.registerNetworkCallback(captivePortalRequest, captivePortalCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        final String redirectUrl = "http://example.com/firstPath";

        mWiFiAgent.connectWithCaptivePortal(redirectUrl,
                false /* privateDnsProbeSent */);
        captivePortalCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        final CaptivePortalData testData = new CaptivePortalData.Builder()
                .setUserPortalUrl(Uri.parse(redirectUrl))
                .setBytesRemaining(12345L)
                .build();

        mWiFiAgent.notifyCapportApiDataChanged(testData);

        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> testData.equals(cb.getLp().getCaptivePortalData()));

        final LinkProperties newLps = new LinkProperties();
        newLps.setMtu(1234);
        mWiFiAgent.sendLinkProperties(newLps);
        // CaptivePortalData is not lost and unchanged when LPs are received from the NetworkAgent
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> testData.equals(cb.getLp().getCaptivePortalData())
                        && cb.getLp().getMtu() == 1234);
    }

    private TestNetworkCallback setupNetworkCallbackAndConnectToWifi() throws Exception {
        // Grant NETWORK_SETTINGS permission to be able to receive LinkProperties change callbacks
        // with sensitive (captive portal) data
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        final TestNetworkCallback captivePortalCallback = new TestNetworkCallback();
        final NetworkRequest captivePortalRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL).build();
        mCm.registerNetworkCallback(captivePortalRequest, captivePortalCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);

        mWiFiAgent.connectWithCaptivePortal(TEST_REDIRECT_URL,
                false /* privateDnsProbeSent */);
        captivePortalCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        return captivePortalCallback;
    }

    private class CaptivePortalTestData {
        CaptivePortalTestData(CaptivePortalData naPasspointData, CaptivePortalData capportData,
                CaptivePortalData naOtherData, CaptivePortalData expectedMergedPasspointData,
                CaptivePortalData expectedMergedOtherData) {
            mNaPasspointData = naPasspointData;
            mCapportData = capportData;
            mNaOtherData = naOtherData;
            mExpectedMergedPasspointData = expectedMergedPasspointData;
            mExpectedMergedOtherData = expectedMergedOtherData;
        }

        public final CaptivePortalData mNaPasspointData;
        public final CaptivePortalData mCapportData;
        public final CaptivePortalData mNaOtherData;
        public final CaptivePortalData mExpectedMergedPasspointData;
        public final CaptivePortalData mExpectedMergedOtherData;

    }

    private CaptivePortalTestData setupCaptivePortalData() {
        final CaptivePortalData capportData = new CaptivePortalData.Builder()
                .setUserPortalUrl(Uri.parse(TEST_REDIRECT_URL))
                .setVenueInfoUrl(Uri.parse(TEST_VENUE_URL_CAPPORT))
                .setUserPortalUrl(Uri.parse(TEST_USER_PORTAL_API_URL_CAPPORT))
                .setExpiryTime(1000000L)
                .setBytesRemaining(12345L)
                .build();

        final CaptivePortalData naPasspointData = new CaptivePortalData.Builder()
                .setBytesRemaining(80802L)
                .setVenueInfoUrl(Uri.parse(TEST_VENUE_URL_NA_PASSPOINT),
                        CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT)
                .setUserPortalUrl(Uri.parse(TEST_TERMS_AND_CONDITIONS_URL_NA_PASSPOINT),
                        CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT)
                .setVenueFriendlyName(TEST_FRIENDLY_NAME).build();

        final CaptivePortalData naOtherData = new CaptivePortalData.Builder()
                .setBytesRemaining(80802L)
                .setVenueInfoUrl(Uri.parse(TEST_VENUE_URL_NA_OTHER),
                        CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_OTHER)
                .setUserPortalUrl(Uri.parse(TEST_TERMS_AND_CONDITIONS_URL_NA_OTHER),
                        CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_OTHER)
                .setVenueFriendlyName(TEST_FRIENDLY_NAME).build();

        final CaptivePortalData expectedMergedPasspointData = new CaptivePortalData.Builder()
                .setUserPortalUrl(Uri.parse(TEST_REDIRECT_URL))
                .setBytesRemaining(12345L)
                .setExpiryTime(1000000L)
                .setVenueInfoUrl(Uri.parse(TEST_VENUE_URL_NA_PASSPOINT),
                        CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT)
                .setUserPortalUrl(Uri.parse(TEST_TERMS_AND_CONDITIONS_URL_NA_PASSPOINT),
                        CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT)
                .setVenueFriendlyName(TEST_FRIENDLY_NAME).build();

        final CaptivePortalData expectedMergedOtherData = new CaptivePortalData.Builder()
                .setUserPortalUrl(Uri.parse(TEST_REDIRECT_URL))
                .setBytesRemaining(12345L)
                .setExpiryTime(1000000L)
                .setVenueInfoUrl(Uri.parse(TEST_VENUE_URL_CAPPORT))
                .setUserPortalUrl(Uri.parse(TEST_USER_PORTAL_API_URL_CAPPORT))
                .setVenueFriendlyName(TEST_FRIENDLY_NAME).build();
        return new CaptivePortalTestData(naPasspointData, capportData, naOtherData,
                expectedMergedPasspointData, expectedMergedOtherData);
    }

    @Test
    public void testMergeCaptivePortalApiWithFriendlyNameAndVenueUrl() throws Exception {
        final TestNetworkCallback captivePortalCallback = setupNetworkCallbackAndConnectToWifi();
        final CaptivePortalTestData captivePortalTestData = setupCaptivePortalData();

        // Baseline capport data
        mWiFiAgent.notifyCapportApiDataChanged(captivePortalTestData.mCapportData);

        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mCapportData.equals(cb.getLp().getCaptivePortalData()));

        // Venue URL, T&C URL and friendly name from Network agent with Passpoint source, confirm
        // that API data gets precedence on the bytes remaining.
        final LinkProperties linkProperties = new LinkProperties();
        linkProperties.setCaptivePortalData(captivePortalTestData.mNaPasspointData);
        mWiFiAgent.sendLinkProperties(linkProperties);

        // Make sure that the capport data is merged
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mExpectedMergedPasspointData.equals(
                        cb.getLp().getCaptivePortalData()));

        // Now send this information from non-Passpoint source, confirm that Capport data takes
        // precedence
        linkProperties.setCaptivePortalData(captivePortalTestData.mNaOtherData);
        mWiFiAgent.sendLinkProperties(linkProperties);

        // Make sure that the capport data is merged
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mExpectedMergedOtherData.equals(
                        cb.getLp().getCaptivePortalData()));

        // Create a new LP with no Network agent capport data
        final LinkProperties newLps = new LinkProperties();
        newLps.setMtu(1234);
        mWiFiAgent.sendLinkProperties(newLps);
        // CaptivePortalData is not lost and has the original values when LPs are received from the
        // NetworkAgent
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mCapportData.equals(cb.getLp().getCaptivePortalData())
                        && cb.getLp().getMtu() == 1234);

        // Now send capport data only from the Network agent
        mWiFiAgent.notifyCapportApiDataChanged(null);
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> cb.getLp().getCaptivePortalData() == null);

        newLps.setCaptivePortalData(captivePortalTestData.mNaPasspointData);
        mWiFiAgent.sendLinkProperties(newLps);

        // Make sure that only the network agent capport data is available
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mNaPasspointData.equals(
                        cb.getLp().getCaptivePortalData()));
    }

    @Test
    public void testMergeCaptivePortalDataFromNetworkAgentFirstThenCapport() throws Exception {
        final TestNetworkCallback captivePortalCallback = setupNetworkCallbackAndConnectToWifi();
        final CaptivePortalTestData captivePortalTestData = setupCaptivePortalData();

        // Venue URL and friendly name from Network agent, confirm that API data gets precedence
        // on the bytes remaining.
        final LinkProperties linkProperties = new LinkProperties();
        linkProperties.setCaptivePortalData(captivePortalTestData.mNaPasspointData);
        mWiFiAgent.sendLinkProperties(linkProperties);

        // Make sure that the data is saved correctly
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mNaPasspointData.equals(
                        cb.getLp().getCaptivePortalData()));

        // Expected merged data: Network agent data is preferred, and values that are not used by
        // it are merged from capport data
        mWiFiAgent.notifyCapportApiDataChanged(captivePortalTestData.mCapportData);

        // Make sure that the Capport data is merged correctly
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mExpectedMergedPasspointData.equals(
                        cb.getLp().getCaptivePortalData()));

        // Now set the naData to null
        linkProperties.setCaptivePortalData(null);
        mWiFiAgent.sendLinkProperties(linkProperties);

        // Make sure that the Capport data is retained correctly
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mCapportData.equals(cb.getLp().getCaptivePortalData()));
    }

    @Test
    public void testMergeCaptivePortalDataFromNetworkAgentOtherSourceFirstThenCapport()
            throws Exception {
        final TestNetworkCallback captivePortalCallback = setupNetworkCallbackAndConnectToWifi();
        final CaptivePortalTestData captivePortalTestData = setupCaptivePortalData();

        // Venue URL and friendly name from Network agent, confirm that API data gets precedence
        // on the bytes remaining.
        final LinkProperties linkProperties = new LinkProperties();
        linkProperties.setCaptivePortalData(captivePortalTestData.mNaOtherData);
        mWiFiAgent.sendLinkProperties(linkProperties);

        // Make sure that the data is saved correctly
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mNaOtherData.equals(cb.getLp().getCaptivePortalData()));

        // Expected merged data: Network agent data is preferred, and values that are not used by
        // it are merged from capport data
        mWiFiAgent.notifyCapportApiDataChanged(captivePortalTestData.mCapportData);

        // Make sure that the Capport data is merged correctly
        captivePortalCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                cb -> captivePortalTestData.mExpectedMergedOtherData.equals(
                        cb.getLp().getCaptivePortalData()));
    }

    private NetworkRequest.Builder newWifiRequestBuilder() {
        return new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI);
    }

    // A NetworkSpecifier subclass that matches all networks but must not be visible to apps.
    static class ConfidentialMatchAllNetworkSpecifier extends NetworkSpecifier implements
            Parcelable {
        public static final Parcelable.Creator<ConfidentialMatchAllNetworkSpecifier> CREATOR =
                new Parcelable.Creator<ConfidentialMatchAllNetworkSpecifier>() {
                    public ConfidentialMatchAllNetworkSpecifier createFromParcel(Parcel in) {
                        return new ConfidentialMatchAllNetworkSpecifier();
                    }

                    public ConfidentialMatchAllNetworkSpecifier[] newArray(int size) {
                        return new ConfidentialMatchAllNetworkSpecifier[size];
                    }
                };
        @Override
        public boolean canBeSatisfiedBy(NetworkSpecifier other) {
            return true;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {}

        @Override
        public NetworkSpecifier redact() {
            return null;
        }
    }

    // A network specifier that matches either another LocalNetworkSpecifier with the same
    // string or a ConfidentialMatchAllNetworkSpecifier, and can be passed to apps as is.
    static class LocalStringNetworkSpecifier extends NetworkSpecifier implements Parcelable {
        public static final Parcelable.Creator<LocalStringNetworkSpecifier> CREATOR =
                new Parcelable.Creator<LocalStringNetworkSpecifier>() {
                    public LocalStringNetworkSpecifier createFromParcel(Parcel in) {
                        return new LocalStringNetworkSpecifier(in);
                    }

                    public LocalStringNetworkSpecifier[] newArray(int size) {
                        return new LocalStringNetworkSpecifier[size];
                    }
                };
        private String mString;

        LocalStringNetworkSpecifier(String string) {
            mString = string;
        }

        LocalStringNetworkSpecifier(Parcel in) {
            mString = in.readString();
        }

        @Override
        public boolean canBeSatisfiedBy(NetworkSpecifier other) {
            if (other instanceof LocalStringNetworkSpecifier) {
                return TextUtils.equals(mString,
                        ((LocalStringNetworkSpecifier) other).mString);
            }
            if (other instanceof ConfidentialMatchAllNetworkSpecifier) return true;
            return false;
        }

        @Override
        public int describeContents() {
            return 0;
        }
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mString);
        }
    }

    /**
     * Verify request matching behavior with network specifiers.
     *
     * This test does not check updating the specifier on a live network because the specifier is
     * immutable and this triggers a WTF in
     * {@link ConnectivityService#mixInCapabilities(NetworkAgentInfo, NetworkCapabilities)}.
     */
    @Test
    public void testNetworkSpecifier() throws Exception {
        NetworkRequest rEmpty1 = newWifiRequestBuilder().build();
        NetworkRequest rEmpty2 = newWifiRequestBuilder().setNetworkSpecifier((String) null).build();
        NetworkRequest rEmpty3 = newWifiRequestBuilder().setNetworkSpecifier("").build();
        NetworkRequest rEmpty4 = newWifiRequestBuilder().setNetworkSpecifier(
            (NetworkSpecifier) null).build();
        NetworkRequest rFoo = newWifiRequestBuilder().setNetworkSpecifier(
                new LocalStringNetworkSpecifier("foo")).build();
        NetworkRequest rBar = newWifiRequestBuilder().setNetworkSpecifier(
                new LocalStringNetworkSpecifier("bar")).build();

        TestNetworkCallback cEmpty1 = new TestNetworkCallback();
        TestNetworkCallback cEmpty2 = new TestNetworkCallback();
        TestNetworkCallback cEmpty3 = new TestNetworkCallback();
        TestNetworkCallback cEmpty4 = new TestNetworkCallback();
        TestNetworkCallback cFoo = new TestNetworkCallback();
        TestNetworkCallback cBar = new TestNetworkCallback();
        TestNetworkCallback[] emptyCallbacks = new TestNetworkCallback[] {
                cEmpty1, cEmpty2, cEmpty3, cEmpty4 };

        mCm.registerNetworkCallback(rEmpty1, cEmpty1);
        mCm.registerNetworkCallback(rEmpty2, cEmpty2);
        mCm.registerNetworkCallback(rEmpty3, cEmpty3);
        mCm.registerNetworkCallback(rEmpty4, cEmpty4);
        mCm.registerNetworkCallback(rFoo, cFoo);
        mCm.registerNetworkCallback(rBar, cBar);

        LocalStringNetworkSpecifier nsFoo = new LocalStringNetworkSpecifier("foo");
        LocalStringNetworkSpecifier nsBar = new LocalStringNetworkSpecifier("bar");

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        expectAvailableCallbacksUnvalidatedWithSpecifier(mWiFiAgent, null /* specifier */,
                cEmpty1, cEmpty2, cEmpty3, cEmpty4);
        assertNoCallbacks(cFoo, cBar);

        mWiFiAgent.disconnect();
        expectOnLost(mWiFiAgent, cEmpty1, cEmpty2, cEmpty3, cEmpty4);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.setNetworkSpecifier(nsFoo);
        mWiFiAgent.connect(false);
        expectAvailableCallbacksUnvalidatedWithSpecifier(mWiFiAgent, nsFoo,
                cEmpty1, cEmpty2, cEmpty3, cEmpty4, cFoo);
        cBar.assertNoCallback();
        assertEquals(nsFoo,
                mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).getNetworkSpecifier());
        assertNoCallbacks(cEmpty1, cEmpty2, cEmpty3, cEmpty4, cFoo);

        mWiFiAgent.disconnect();
        expectOnLost(mWiFiAgent, cEmpty1, cEmpty2, cEmpty3, cEmpty4, cFoo);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.setNetworkSpecifier(nsBar);
        mWiFiAgent.connect(false);
        expectAvailableCallbacksUnvalidatedWithSpecifier(mWiFiAgent, nsBar,
                cEmpty1, cEmpty2, cEmpty3, cEmpty4, cBar);
        cFoo.assertNoCallback();
        assertEquals(nsBar,
                mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).getNetworkSpecifier());

        mWiFiAgent.disconnect();
        expectOnLost(mWiFiAgent, cEmpty1, cEmpty2, cEmpty3, cEmpty4, cBar);
        cFoo.assertNoCallback();

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.setNetworkSpecifier(new ConfidentialMatchAllNetworkSpecifier());
        mWiFiAgent.connect(false);
        expectAvailableCallbacksUnvalidatedWithSpecifier(mWiFiAgent, null /* specifier */,
                cEmpty1, cEmpty2, cEmpty3, cEmpty4, cFoo, cBar);
        assertNull(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).getNetworkSpecifier());

        mWiFiAgent.disconnect();
        expectOnLost(mWiFiAgent, cEmpty1, cEmpty2, cEmpty3, cEmpty4, cFoo, cBar);
    }

    /**
     * @return the context's attribution tag
     */
    private String getAttributionTag() {
        return mContext.getAttributionTag();
    }

    static class NonParcelableSpecifier extends NetworkSpecifier {
        @Override
        public boolean canBeSatisfiedBy(NetworkSpecifier other) {
            return false;
        }
    }
    static class ParcelableSpecifier extends NonParcelableSpecifier implements Parcelable {
        public static final Parcelable.Creator<NonParcelableSpecifier> CREATOR =
                new Parcelable.Creator<NonParcelableSpecifier>() {
                    public NonParcelableSpecifier createFromParcel(Parcel in) {
                        return new NonParcelableSpecifier();
                    }

                    public NonParcelableSpecifier[] newArray(int size) {
                        return new NonParcelableSpecifier[size];
                    }
                };
        @Override public int describeContents() {
            return 0;
        }
        @Override public void writeToParcel(Parcel p, int flags) {}
    }

    @Test
    public void testInvalidNetworkSpecifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.setNetworkSpecifier(new MatchAllNetworkSpecifier());
        });

        assertThrows(IllegalArgumentException.class, () -> {
            NetworkCapabilities networkCapabilities = new NetworkCapabilities();
            networkCapabilities.addTransportType(TRANSPORT_WIFI)
                    .setNetworkSpecifier(new MatchAllNetworkSpecifier());
            mService.requestNetwork(Process.INVALID_UID, networkCapabilities,
                    NetworkRequest.Type.REQUEST.ordinal(), null, 0, null,
                    ConnectivityManager.TYPE_WIFI, NetworkCallback.FLAG_NONE,
                    mContext.getPackageName(), getAttributionTag());
        });

        final NetworkRequest.Builder builder =
                new NetworkRequest.Builder().addTransportType(TRANSPORT_ETHERNET);
        assertThrows(ClassCastException.class, () -> {
            builder.setNetworkSpecifier(new NonParcelableSpecifier());
            Parcel parcelW = Parcel.obtain();
            builder.build().writeToParcel(parcelW, 0);
        });

        final NetworkRequest nr =
                new NetworkRequest.Builder().addTransportType(TRANSPORT_ETHERNET)
                .setNetworkSpecifier(new ParcelableSpecifier())
                .build();
        assertNotNull(nr);

        assertThrows(BadParcelableException.class, () -> {
            Parcel parcelW = Parcel.obtain();
            nr.writeToParcel(parcelW, 0);
            byte[] bytes = parcelW.marshall();
            parcelW.recycle();

            Parcel parcelR = Parcel.obtain();
            parcelR.unmarshall(bytes, 0, bytes.length);
            parcelR.setDataPosition(0);
            NetworkRequest rereadNr = NetworkRequest.CREATOR.createFromParcel(parcelR);
        });
    }

    @Test
    public void testNetworkRequestUidSpoofSecurityException() throws Exception {
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        NetworkRequest networkRequest = newWifiRequestBuilder().build();
        TestNetworkCallback networkCallback = new TestNetworkCallback();
        doThrow(new SecurityException()).when(mAppOpsManager).checkPackage(anyInt(), anyString());
        assertThrows(SecurityException.class, () -> {
            mCm.requestNetwork(networkRequest, networkCallback);
        });
    }

    @Test
    public void testInvalidSignalStrength() {
        NetworkRequest r = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_WIFI)
                .setSignalStrength(-75)
                .build();
        // Registering a NetworkCallback with signal strength but w/o NETWORK_SIGNAL_STRENGTH_WAKEUP
        // permission should get SecurityException.
        assertThrows(SecurityException.class, () ->
                mCm.registerNetworkCallback(r, new NetworkCallback()));

        assertThrows(SecurityException.class, () ->
                mCm.registerNetworkCallback(r, PendingIntent.getService(
                        mServiceContext, 0 /* requestCode */, new Intent(), FLAG_IMMUTABLE)));

        // Requesting a Network with signal strength should get IllegalArgumentException.
        assertThrows(IllegalArgumentException.class, () ->
                mCm.requestNetwork(r, new NetworkCallback()));

        assertThrows(IllegalArgumentException.class, () ->
                mCm.requestNetwork(r, PendingIntent.getService(
                        mServiceContext, 0 /* requestCode */, new Intent(), FLAG_IMMUTABLE)));
    }

    @Test
    public void testRegisterDefaultNetworkCallback() throws Exception {
        final TestNetworkCallback defaultNetworkCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultNetworkCallback);
        defaultNetworkCallback.assertNoCallback();

        final Handler handler = new Handler(ConnectivityThread.getInstanceLooper());
        final TestNetworkCallback systemDefaultCallback = new TestNetworkCallback();
        mCm.registerSystemDefaultNetworkCallback(systemDefaultCallback, handler);
        systemDefaultCallback.assertNoCallback();

        // Create a TRANSPORT_CELLULAR request to keep the mobile interface up
        // whenever Wi-Fi is up. Without this, the mobile network agent is
        // reaped before any other activity can take place.
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);
        cellNetworkCallback.assertNoCallback();

        // Bring up cell and expect CALLBACK_AVAILABLE.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        systemDefaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        assertEquals(systemDefaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Bring up wifi and expect CALLBACK_AVAILABLE.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        cellNetworkCallback.assertNoCallback();
        defaultNetworkCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        systemDefaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        assertEquals(systemDefaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Bring down cell. Expect no default network callback, since it wasn't the default.
        mCellAgent.disconnect();
        cellNetworkCallback.expect(LOST, mCellAgent);
        defaultNetworkCallback.assertNoCallback();
        systemDefaultCallback.assertNoCallback();
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        assertEquals(systemDefaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Bring up cell. Expect no default network callback, since it won't be the default.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultNetworkCallback.assertNoCallback();
        systemDefaultCallback.assertNoCallback();
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        assertEquals(systemDefaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        // Bring down wifi. Expect the default network callback to notified of LOST wifi
        // followed by AVAILABLE cell.
        mWiFiAgent.disconnect();
        cellNetworkCallback.assertNoCallback();
        defaultNetworkCallback.expect(LOST, mWiFiAgent);
        defaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        systemDefaultCallback.expect(LOST, mWiFiAgent);
        systemDefaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        mCellAgent.disconnect();
        cellNetworkCallback.expect(LOST, mCellAgent);
        defaultNetworkCallback.expect(LOST, mCellAgent);
        systemDefaultCallback.expect(LOST, mCellAgent);
        waitForIdle();
        assertEquals(null, mCm.getActiveNetwork());

        mMockVpn.establishForMyUid();
        assertUidRangesUpdatedForMyUid(true);
        defaultNetworkCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        systemDefaultCallback.assertNoCallback();
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        assertEquals(null, systemDefaultCallback.getLastAvailableNetwork());

        mMockVpn.disconnect();
        defaultNetworkCallback.expect(LOST, mMockVpn);
        systemDefaultCallback.assertNoCallback();
        waitForIdle();
        assertEquals(null, mCm.getActiveNetwork());
    }

    @Test
    public void testAdditionalStateCallbacks() throws Exception {
        // File a network request for mobile.
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);

        // Bring up the mobile network.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        // We should get onAvailable(), onCapabilitiesChanged(), and
        // onLinkPropertiesChanged() in rapid succession. Additionally, we
        // should get onCapabilitiesChanged() when the mobile network validates.
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        cellNetworkCallback.assertNoCallback();

        // Update LinkProperties.
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("foonet_data0");
        mCellAgent.sendLinkProperties(lp);
        // We should get onLinkPropertiesChanged().
        cellNetworkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.assertNoCallback();

        // Suspend the network.
        mCellAgent.suspend();
        cellNetworkCallback.expectCaps(mCellAgent,
                c -> !c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        cellNetworkCallback.expect(SUSPENDED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertEquals(NetworkInfo.State.SUSPENDED, mCm.getActiveNetworkInfo().getState());

        // Register a garden variety default network request.
        TestNetworkCallback dfltNetworkCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(dfltNetworkCallback);
        // We should get onAvailable(), onCapabilitiesChanged(), onLinkPropertiesChanged(),
        // as well as onNetworkSuspended() in rapid succession.
        dfltNetworkCallback.expectAvailableAndSuspendedCallbacks(mCellAgent, true);
        dfltNetworkCallback.assertNoCallback();
        mCm.unregisterNetworkCallback(dfltNetworkCallback);

        mCellAgent.resume();
        cellNetworkCallback.expectCaps(mCellAgent,
                c -> c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        cellNetworkCallback.expect(RESUMED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertEquals(NetworkInfo.State.CONNECTED, mCm.getActiveNetworkInfo().getState());

        dfltNetworkCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(dfltNetworkCallback);
        // This time onNetworkSuspended should not be called.
        dfltNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        dfltNetworkCallback.assertNoCallback();

        mCm.unregisterNetworkCallback(dfltNetworkCallback);
        mCm.unregisterNetworkCallback(cellNetworkCallback);
    }

    @Test
    public void testRegisterPrivilegedDefaultCallbacksRequirePermissions() throws Exception {
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false /* validated */);
        mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_DENIED);

        final Handler handler = new Handler(ConnectivityThread.getInstanceLooper());
        final TestNetworkCallback callback = new TestNetworkCallback();
        assertThrows(SecurityException.class,
                () -> mCm.registerSystemDefaultNetworkCallback(callback, handler));
        callback.assertNoCallback();
        assertThrows(SecurityException.class,
                () -> mCm.registerDefaultNetworkCallbackForUid(APP1_UID, callback, handler));
        callback.assertNoCallback();

        mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_GRANTED);
        mCm.registerSystemDefaultNetworkCallback(callback, handler);
        mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_DENIED);
        callback.expectAvailableCallbacksUnvalidated(mCellAgent);
        mCm.unregisterNetworkCallback(callback);

        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);
        mCm.registerSystemDefaultNetworkCallback(callback, handler);
        callback.expectAvailableCallbacksUnvalidated(mCellAgent);
        mCm.unregisterNetworkCallback(callback);

        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_DENIED);
        mServiceContext.setPermission(NETWORK_SETUP_WIZARD, PERMISSION_GRANTED);
        mCm.registerSystemDefaultNetworkCallback(callback, handler);
        callback.expectAvailableCallbacksUnvalidated(mCellAgent);
        mCm.unregisterNetworkCallback(callback);

        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);
        mCm.registerDefaultNetworkCallbackForUid(APP1_UID, callback, handler);
        callback.expectAvailableCallbacksUnvalidated(mCellAgent);
        mCm.unregisterNetworkCallback(callback);
    }

    @Test
    public void testNetworkCallbackWithNullUids() throws Exception {
        final NetworkRequest request = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        // Attempt to file a callback for networks applying to another UID. This does not actually
        // work, because this code does not currently have permission to do so. The callback behaves
        // exactly the same as the one registered just above.
        final int otherUid = UserHandle.getUid(RESTRICTED_USER, VPN_UID);
        final NetworkRequest otherUidRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .setUids(UidRange.toIntRanges(uidRangesForUids(otherUid)))
                .build();
        final TestNetworkCallback otherUidCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(otherUidRequest, otherUidCallback);

        final NetworkRequest includeOtherUidsRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .setIncludeOtherUidNetworks(true)
                .build();
        final TestNetworkCallback includeOtherUidsCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(includeOtherUidsRequest, includeOtherUidsCallback);

        // Both callbacks see a network with no specifier that applies to their UID.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false /* validated */);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        otherUidCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        includeOtherUidsCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        otherUidCallback.expect(LOST, mWiFiAgent);
        includeOtherUidsCallback.expect(LOST, mWiFiAgent);

        // Only the includeOtherUidsCallback sees a VPN that does not apply to its UID.
        final UidRange range = UidRange.createForUser(UserHandle.of(RESTRICTED_USER));
        final Set<UidRange> vpnRanges = Collections.singleton(range);
        mMockVpn.establish(new LinkProperties(), VPN_UID, vpnRanges);
        includeOtherUidsCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        callback.assertNoCallback();
        otherUidCallback.assertNoCallback();

        mMockVpn.disconnect();
        includeOtherUidsCallback.expect(LOST, mMockVpn);
        callback.assertNoCallback();
        otherUidCallback.assertNoCallback();
    }

    private static class RedactableNetworkSpecifier extends NetworkSpecifier {
        public static final int ID_INVALID = -1;

        public final int networkId;

        RedactableNetworkSpecifier(int networkId) {
            this.networkId = networkId;
        }

        @Override
        public boolean canBeSatisfiedBy(NetworkSpecifier other) {
            return other instanceof RedactableNetworkSpecifier
                    && this.networkId == ((RedactableNetworkSpecifier) other).networkId;
        }

        @Override
        public NetworkSpecifier redact() {
            return new RedactableNetworkSpecifier(ID_INVALID);
        }
    }

    @Test
    public void testNetworkCallbackWithNullUidsRedactsSpecifier() throws Exception {
        final RedactableNetworkSpecifier specifier = new RedactableNetworkSpecifier(42);
        final NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        // Attempt to file a callback for networks applying to another UID. This does not actually
        // work, because this code does not currently have permission to do so. The callback behaves
        // exactly the same as the one registered just above.
        final int otherUid = UserHandle.getUid(RESTRICTED_USER, VPN_UID);
        final NetworkRequest otherUidRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .setUids(UidRange.toIntRanges(uidRangesForUids(otherUid)))
                .build();
        final TestNetworkCallback otherUidCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(otherUidRequest, otherUidCallback);

        final NetworkRequest includeOtherUidsRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .setIncludeOtherUidNetworks(true)
                .build();
        final TestNetworkCallback includeOtherUidsCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(includeOtherUidsRequest, callback);

        // Only the regular callback sees the network, because callbacks filed with no UID have
        // their specifiers redacted.
        final LinkProperties emptyLp = new LinkProperties();
        final NetworkCapabilities ncTemplate = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, emptyLp, ncTemplate);
        mWiFiAgent.connect(false /* validated */);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        otherUidCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        includeOtherUidsCallback.assertNoCallback();
    }

    private void setCaptivePortalMode(int mode) {
        ContentResolver cr = mServiceContext.getContentResolver();
        Settings.Global.putInt(cr, ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE, mode);
    }

    private void setAlwaysOnNetworks(boolean enable) {
        ContentResolver cr = mServiceContext.getContentResolver();
        Settings.Global.putInt(cr, ConnectivitySettingsManager.MOBILE_DATA_ALWAYS_ON,
                enable ? 1 : 0);
        mService.updateAlwaysOnNetworks();
        waitForIdle();
    }

    private void setPrivateDnsSettings(int mode, String specifier) {
        ConnectivitySettingsManager.setPrivateDnsMode(mServiceContext, mode);
        ConnectivitySettingsManager.setPrivateDnsHostname(mServiceContext, specifier);
        mService.updatePrivateDnsSettings();
        waitForIdle();
    }

    private void setIngressRateLimit(int rateLimitInBytesPerSec) {
        ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond(mServiceContext,
                rateLimitInBytesPerSec);
        mService.updateIngressRateLimit();
        waitForIdle();
    }

    private boolean isForegroundNetwork(TestNetworkAgentWrapper network) {
        NetworkCapabilities nc = mCm.getNetworkCapabilities(network.getNetwork());
        assertNotNull(nc);
        return nc.hasCapability(NET_CAPABILITY_FOREGROUND);
    }

    @Test
    public void testBackgroundNetworks() throws Exception {
        // Create a cellular background request.
        grantUsingBackgroundNetworksPermissionForUid(Binder.getCallingUid());
        final TestNetworkCallback cellBgCallback = new TestNetworkCallback();
        mCm.requestBackgroundNetwork(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build(),
                cellBgCallback, mCsHandlerThread.getThreadHandler());

        // Make callbacks for monitoring.
        final NetworkRequest request = new NetworkRequest.Builder().build();
        final NetworkRequest fgRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_FOREGROUND).build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        final TestNetworkCallback fgCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);
        mCm.registerNetworkCallback(fgRequest, fgCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        fgCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertTrue(isForegroundNetwork(mCellAgent));

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);

        // When wifi connects, cell lingers.
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectLosing(mCellAgent);
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        fgCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        fgCallback.expectLosing(mCellAgent);
        fgCallback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        assertTrue(isForegroundNetwork(mCellAgent));
        assertTrue(isForegroundNetwork(mWiFiAgent));

        // When lingering is complete, cell is still there but is now in the background.
        waitForIdle();
        int timeoutMs = TEST_LINGER_DELAY_MS + TEST_LINGER_DELAY_MS / 4;
        fgCallback.expect(LOST, mCellAgent, timeoutMs);
        // Expect a network capabilities update sans FOREGROUND.
        callback.expectCaps(mCellAgent, c -> !c.hasCapability(NET_CAPABILITY_FOREGROUND));
        assertFalse(isForegroundNetwork(mCellAgent));
        assertTrue(isForegroundNetwork(mWiFiAgent));

        // File a cell request and check that cell comes into the foreground.
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        final TestNetworkCallback cellCallback = new TestNetworkCallback();
        mCm.requestNetwork(cellRequest, cellCallback);
        cellCallback.expectAvailableCallbacksValidated(mCellAgent);
        fgCallback.expectAvailableCallbacksValidated(mCellAgent);
        // Expect a network capabilities update with FOREGROUND, because the most recent
        // request causes its state to change.
        cellCallback.expectCaps(mCellAgent, c -> c.hasCapability(NET_CAPABILITY_FOREGROUND));
        callback.expectCaps(mCellAgent, c -> c.hasCapability(NET_CAPABILITY_FOREGROUND));
        assertTrue(isForegroundNetwork(mCellAgent));
        assertTrue(isForegroundNetwork(mWiFiAgent));

        // Release the request. The network immediately goes into the background, since it was not
        // lingering.
        mCm.unregisterNetworkCallback(cellCallback);
        fgCallback.expect(LOST, mCellAgent);
        // Expect a network capabilities update sans FOREGROUND.
        callback.expectCaps(mCellAgent, c -> !c.hasCapability(NET_CAPABILITY_FOREGROUND));
        assertFalse(isForegroundNetwork(mCellAgent));
        assertTrue(isForegroundNetwork(mWiFiAgent));

        // Disconnect wifi and check that cell is foreground again.
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        fgCallback.expect(LOST, mWiFiAgent);
        fgCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertTrue(isForegroundNetwork(mCellAgent));

        mCm.unregisterNetworkCallback(callback);
        mCm.unregisterNetworkCallback(fgCallback);
        mCm.unregisterNetworkCallback(cellBgCallback);
    }

    @Ignore // This test has instrinsic chances of spurious failures: ignore for continuous testing.
    public void benchmarkRequestRegistrationAndCallbackDispatch() throws Exception {
        // TODO: turn this unit test into a real benchmarking test.
        // Benchmarks connecting and switching performance in the presence of a large number of
        // NetworkRequests.
        // 1. File NUM_REQUESTS requests.
        // 2. Have a network connect. Wait for NUM_REQUESTS onAvailable callbacks to fire.
        // 3. Have a new network connect and outscore the previous. Wait for NUM_REQUESTS onLosing
        //    and NUM_REQUESTS onAvailable callbacks to fire.
        // See how long it took.
        final int NUM_REQUESTS = 90;
        final int REGISTER_TIME_LIMIT_MS = 200;
        final int CONNECT_TIME_LIMIT_MS = 60;
        final int SWITCH_TIME_LIMIT_MS = 60;
        final int UNREGISTER_TIME_LIMIT_MS = 20;

        final NetworkRequest request = new NetworkRequest.Builder().clearCapabilities().build();
        final NetworkCallback[] callbacks = new NetworkCallback[NUM_REQUESTS];
        final CountDownLatch availableLatch = new CountDownLatch(NUM_REQUESTS);
        final CountDownLatch losingLatch = new CountDownLatch(NUM_REQUESTS);

        for (int i = 0; i < NUM_REQUESTS; i++) {
            callbacks[i] = new NetworkCallback() {
                @Override public void onAvailable(Network n) { availableLatch.countDown(); }
                @Override public void onLosing(Network n, int t) { losingLatch.countDown(); }
            };
        }

        assertRunsInAtMost("Registering callbacks", REGISTER_TIME_LIMIT_MS, () -> {
            for (NetworkCallback cb : callbacks) {
                mCm.registerNetworkCallback(request, cb);
            }
        });

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        // Don't request that the network validate, because otherwise connect() will block until
        // the network gets NET_CAPABILITY_VALIDATED, after all the callbacks below have fired,
        // and we won't actually measure anything.
        mCellAgent.connect(false);

        long onAvailableDispatchingDuration = durationOf(() -> {
            await(availableLatch, 10 * CONNECT_TIME_LIMIT_MS);
        });
        Log.d(TAG, String.format("Dispatched %d of %d onAvailable callbacks in %dms",
                NUM_REQUESTS - availableLatch.getCount(), NUM_REQUESTS,
                onAvailableDispatchingDuration));
        assertTrue(String.format("Dispatching %d onAvailable callbacks in %dms, expected %dms",
                NUM_REQUESTS, onAvailableDispatchingDuration, CONNECT_TIME_LIMIT_MS),
                onAvailableDispatchingDuration <= CONNECT_TIME_LIMIT_MS);

        // Give wifi a high enough score that we'll linger cell when wifi comes up.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.adjustScore(40);
        mWiFiAgent.connect(false);

        long onLostDispatchingDuration = durationOf(() -> {
            await(losingLatch, 10 * SWITCH_TIME_LIMIT_MS);
        });
        Log.d(TAG, String.format("Dispatched %d of %d onLosing callbacks in %dms",
                NUM_REQUESTS - losingLatch.getCount(), NUM_REQUESTS, onLostDispatchingDuration));
        assertTrue(String.format("Dispatching %d onLosing callbacks in %dms, expected %dms",
                NUM_REQUESTS, onLostDispatchingDuration, SWITCH_TIME_LIMIT_MS),
                onLostDispatchingDuration <= SWITCH_TIME_LIMIT_MS);

        assertRunsInAtMost("Unregistering callbacks", UNREGISTER_TIME_LIMIT_MS, () -> {
            for (NetworkCallback cb : callbacks) {
                mCm.unregisterNetworkCallback(cb);
            }
        });
    }

    @Test
    public void testMobileDataAlwaysOn() throws Exception {
        grantUsingBackgroundNetworksPermissionForUid(Binder.getCallingUid());
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.registerNetworkCallback(cellRequest, cellNetworkCallback);

        final HandlerThread handlerThread = new HandlerThread("MobileDataAlwaysOnFactory");
        handlerThread.start();
        NetworkCapabilities filter = new NetworkCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .addCapability(NET_CAPABILITY_INTERNET);
        final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactory", filter, mCsHandlerThread);
        testFactory.setScoreFilter(40);

        // Register the factory and expect it to start looking for a network.
        testFactory.register();

        try {
            // Expect the factory to receive the default network request.
            testFactory.expectRequestAdd();
            testFactory.assertRequestCountEquals(1);
            assertTrue(testFactory.getMyStartRequested());

            // Bring up wifi. The factory stops looking for a network.
            mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
            // Score 60 - 40 penalty for not validated yet, then 60 when it validates
            mWiFiAgent.connect(true);
            // The network connects with a low score, so the offer can still beat it and
            // nothing happens. Then the network validates, and the offer with its filter score
            // of 40 can no longer beat it and the request is removed.
            testFactory.expectRequestRemove();
            testFactory.assertRequestCountEquals(0);

            assertFalse(testFactory.getMyStartRequested());

            // Turn on mobile data always on. This request will not match the wifi request, so
            // it will be sent to the test factory whose filters allow to see it.
            setAlwaysOnNetworks(true);
            testFactory.expectRequestAdd();
            testFactory.assertRequestCountEquals(1);

            assertTrue(testFactory.getMyStartRequested());

            // Bring up cell data and check that the factory stops looking.
            assertLength(1, mCm.getAllNetworks());
            mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
            mCellAgent.connect(false);
            cellNetworkCallback.expectAvailableCallbacks(mCellAgent, false, false, false,
                    TEST_CALLBACK_TIMEOUT_MS);
            // When cell connects, it will satisfy the "mobile always on request" right away
            // by virtue of being the only network that can satisfy the request. However, its
            // score is low (50 - 40 = 10) so the test factory can still hope to beat it.
            expectNoRequestChanged(testFactory);

            // Next, cell validates. This gives it a score of 50 and the test factory can't
            // hope to beat that according to its filters. It will see the message that its
            // offer is now unnecessary.
            mCellAgent.setNetworkValid(true);
            // Need a trigger point to let NetworkMonitor tell ConnectivityService that network is
            // validated – see testPartialConnectivity.
            mCm.reportNetworkConnectivity(mCellAgent.getNetwork(), true);
            cellNetworkCallback.expectCaps(mCellAgent,
                    c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
            testFactory.expectRequestRemove();
            testFactory.assertRequestCountEquals(0);
            // Accordingly, the factory shouldn't be started.
            assertFalse(testFactory.getMyStartRequested());

            // Check that cell data stays up.
            waitForIdle();
            verifyActiveNetwork(TRANSPORT_WIFI);
            assertLength(2, mCm.getAllNetworks());

            // Cell disconnects. There is still the "mobile data always on" request outstanding,
            // and the test factory should see it now that it isn't hopelessly outscored.
            mCellAgent.disconnect();
            cellNetworkCallback.expect(LOST, mCellAgent);
            // Wait for the network to be removed from internal structures before
            // calling synchronous getter
            waitForIdle();
            assertLength(1, mCm.getAllNetworks());
            testFactory.expectRequestAdd();
            testFactory.assertRequestCountEquals(1);

            // Reconnect cell validated, see the request disappear again. Then withdraw the
            // mobile always on request. This will tear down cell, and there shouldn't be a
            // blip where the test factory briefly sees the request or anything.
            mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
            mCellAgent.connect(true);
            cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
            waitForIdle();
            assertLength(2, mCm.getAllNetworks());
            testFactory.expectRequestRemove();
            testFactory.assertRequestCountEquals(0);
            setAlwaysOnNetworks(false);
            expectNoRequestChanged(testFactory);
            testFactory.assertRequestCountEquals(0);
            assertFalse(testFactory.getMyStartRequested());
            // ...  and cell data to be torn down immediately since it is no longer nascent.
            cellNetworkCallback.expect(LOST, mCellAgent);
            waitForIdle();
            assertLength(1, mCm.getAllNetworks());
            testFactory.terminate();
            testFactory.assertNoRequestChanged();
        } finally {
            mCm.unregisterNetworkCallback(cellNetworkCallback);
            handlerThread.quitSafely();
            handlerThread.join();
        }
    }

    @Test
    public void testSetAllowBadWifiUntil() throws Exception {
        runAsShell(NETWORK_SETTINGS,
                () -> mService.setTestAllowBadWifiUntil(System.currentTimeMillis() + 5_000L));
        waitForIdle();
        testAvoidBadWifiConfig_controlledBySettings();

        runAsShell(NETWORK_SETTINGS,
                () -> mService.setTestAllowBadWifiUntil(System.currentTimeMillis() - 5_000L));
        waitForIdle();
        testAvoidBadWifiConfig_ignoreSettings();
    }

    private void testAvoidBadWifiConfig_controlledBySettings() {
        final ContentResolver cr = mServiceContext.getContentResolver();
        final String settingName = ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI;

        Settings.Global.putString(cr, settingName, "0");
        mPolicyTracker.reevaluate();
        waitForIdle();
        assertFalse(mService.avoidBadWifi());
        assertFalse(mPolicyTracker.shouldNotifyWifiUnvalidated());

        Settings.Global.putString(cr, settingName, "1");
        mPolicyTracker.reevaluate();
        waitForIdle();
        assertTrue(mService.avoidBadWifi());
        assertFalse(mPolicyTracker.shouldNotifyWifiUnvalidated());

        Settings.Global.putString(cr, settingName, null);
        mPolicyTracker.reevaluate();
        waitForIdle();
        assertFalse(mService.avoidBadWifi());
        assertTrue(mPolicyTracker.shouldNotifyWifiUnvalidated());
    }

    private void testAvoidBadWifiConfig_ignoreSettings() {
        final ContentResolver cr = mServiceContext.getContentResolver();
        final String settingName = ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI;

        String[] values = new String[] {null, "0", "1"};
        for (int i = 0; i < values.length; i++) {
            Settings.Global.putString(cr, settingName, values[i]);
            mPolicyTracker.reevaluate();
            waitForIdle();
            String msg = String.format("config=false, setting=%s", values[i]);
            assertTrue(mService.avoidBadWifi());
            assertFalse(msg, mPolicyTracker.shouldNotifyWifiUnvalidated());
        }
    }

    @Test
    public void testAvoidBadWifiSetting() throws Exception {
        doReturn(1).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        testAvoidBadWifiConfig_ignoreSettings();

        doReturn(0).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        testAvoidBadWifiConfig_controlledBySettings();
    }

    @Test
    public void testActivelyPreferBadWifiSetting() throws Exception {
        doReturn(1).when(mResources).getInteger(R.integer.config_activelyPreferBadWifi);
        mPolicyTracker.reevaluate();
        waitForIdle();
        assertTrue(mService.mNetworkRanker.getConfiguration().activelyPreferBadWifi());

        doReturn(0).when(mResources).getInteger(R.integer.config_activelyPreferBadWifi);
        mPolicyTracker.reevaluate();
        waitForIdle();
        if (mDeps.isAtLeastU()) {
            // U+ ignore the setting and always actively prefers bad wifi
            assertTrue(mService.mNetworkRanker.getConfiguration().activelyPreferBadWifi());
        } else {
            assertFalse(mService.mNetworkRanker.getConfiguration().activelyPreferBadWifi());
        }
    }

    @Test
    public void testOffersAvoidsBadWifi() throws Exception {
        // Normal mode : the carrier doesn't restrict moving away from bad wifi.
        // This has getAvoidBadWifi return true.
        doReturn(1).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        // Don't request cell separately for the purposes of this test.
        setAlwaysOnNetworks(false);

        final NetworkProvider cellProvider = new NetworkProvider(mServiceContext,
                mCsHandlerThread.getLooper(), "Cell provider");
        final NetworkProvider wifiProvider = new NetworkProvider(mServiceContext,
                mCsHandlerThread.getLooper(), "Wifi provider");

        mCm.registerNetworkProvider(cellProvider);
        mCm.registerNetworkProvider(wifiProvider);

        final NetworkScore cellScore = new NetworkScore.Builder().build();
        final NetworkScore wifiScore = new NetworkScore.Builder().build();
        final NetworkCapabilities defaultCaps = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        final NetworkCapabilities cellCaps = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        final NetworkCapabilities wifiCaps = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        final TestableNetworkOfferCallback cellCallback = new TestableNetworkOfferCallback(
                TIMEOUT_MS /* timeout */, TEST_CALLBACK_TIMEOUT_MS /* noCallbackTimeout */);
        final TestableNetworkOfferCallback wifiCallback = new TestableNetworkOfferCallback(
                TIMEOUT_MS /* timeout */, TEST_CALLBACK_TIMEOUT_MS /* noCallbackTimeout */);

        // Offer callbacks will run on the CS handler thread in this test.
        cellProvider.registerNetworkOffer(cellScore, cellCaps, r -> r.run(), cellCallback);
        wifiProvider.registerNetworkOffer(wifiScore, wifiCaps, r -> r.run(), wifiCallback);

        // Both providers see the default request.
        cellCallback.expectOnNetworkNeeded(defaultCaps);
        wifiCallback.expectOnNetworkNeeded(defaultCaps);

        // Listen to cell and wifi to know when agents are finished processing
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.registerNetworkCallback(cellRequest, cellNetworkCallback);
        final TestNetworkCallback wifiNetworkCallback = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(wifiRequest, wifiNetworkCallback);

        // Cell connects and validates.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR,
                new LinkProperties(), null /* ncTemplate */, cellProvider);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        cellCallback.assertNoCallback();
        wifiCallback.assertNoCallback();

        // Bring up wifi. At first it's invalidated, so cell is still needed.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI,
                new LinkProperties(), null /* ncTemplate */, wifiProvider);
        mWiFiAgent.connect(false);
        wifiNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        cellCallback.assertNoCallback();
        wifiCallback.assertNoCallback();

        // Wifi validates. Cell is no longer needed, because it's outscored.
        mWiFiAgent.setNetworkValid(true /* privateDnsProbeSent */);
        // Have CS reconsider the network (see testPartialConnectivity)
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        wifiNetworkCallback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        cellCallback.expectOnNetworkUnneeded(defaultCaps);
        wifiCallback.assertNoCallback();

        // Wifi is no longer validated. Cell is needed again.
        mWiFiAgent.setNetworkInvalid(true /* invalidBecauseOfPrivateDns */);
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);
        wifiNetworkCallback.expectCaps(mWiFiAgent,
                c -> !c.hasCapability(NET_CAPABILITY_VALIDATED));
        cellCallback.expectOnNetworkNeeded(defaultCaps);
        wifiCallback.assertNoCallback();

        // Disconnect wifi and pretend the carrier restricts moving away from bad wifi.
        mWiFiAgent.disconnect();
        wifiNetworkCallback.expect(LOST, mWiFiAgent);
        // This has getAvoidBadWifi return false. This test doesn't change the value of the
        // associated setting.
        doReturn(0).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        mPolicyTracker.reevaluate();
        waitForIdle();

        // Connect wifi again, cell is needed until wifi validates.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI,
                new LinkProperties(), null /* ncTemplate */, wifiProvider);
        mWiFiAgent.connect(false);
        wifiNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        cellCallback.assertNoCallback();
        wifiCallback.assertNoCallback();
        mWiFiAgent.setNetworkValid(true /* privateDnsProbeSent */);
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
        wifiNetworkCallback.expectCaps(mWiFiAgent,
                c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        cellCallback.expectOnNetworkUnneeded(defaultCaps);
        wifiCallback.assertNoCallback();

        // Wifi loses validation. Because the device doesn't avoid bad wifis, cell is
        // not needed.
        mWiFiAgent.setNetworkInvalid(true /* invalidBecauseOfPrivateDns */);
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);
        wifiNetworkCallback.expectCaps(mWiFiAgent,
                c -> !c.hasCapability(NET_CAPABILITY_VALIDATED));
        cellCallback.assertNoCallback();
        wifiCallback.assertNoCallback();
    }

    public void doTestPreferBadWifi(final boolean avoidBadWifi,
            final boolean preferBadWifi, final boolean explicitlySelected,
            @NonNull Predicate<Long> checkUnvalidationTimeout) throws Exception {
        // Pretend we're on a carrier that restricts switching away from bad wifi, and
        // depending on the parameter one that may indeed prefer bad wifi.
        doReturn(avoidBadWifi ? 1 : 0).when(mResources)
                .getInteger(R.integer.config_networkAvoidBadWifi);
        doReturn(preferBadWifi ? 1 : 0).when(mResources)
                .getInteger(R.integer.config_activelyPreferBadWifi);
        mPolicyTracker.reevaluate();

        registerDefaultNetworkCallbacks();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        final TestNetworkCallback wifiCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(wifiRequest, wifiCallback);

        // Bring up validated cell and unvalidated wifi.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.explicitlySelected(explicitlySelected, false /* acceptUnvalidated */);
        mWiFiAgent.connect(false);
        wifiCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        assertNotNull(mDeps.mScheduledEvaluationTimeouts.poll(TIMEOUT_MS,
                t -> t.first == mWiFiAgent.getNetwork().netId
                        && checkUnvalidationTimeout.test(t.second)));

        if (!avoidBadWifi && preferBadWifi) {
            expectUnvalidationCheckWillNotify(mWiFiAgent, NotificationType.LOST_INTERNET);
            mDefaultNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        } else {
            expectUnvalidationCheckWillNotNotify(mWiFiAgent);
            mDefaultNetworkCallback.assertNoCallback();
        }
    }

    @Test
    public void testPreferBadWifi_doNotAvoid_doNotPrefer() throws Exception {
        // Starting with U this mode is no longer supported and can't actually be tested
        assumeFalse(mDeps.isAtLeastU());
        doTestPreferBadWifi(false /* avoidBadWifi */, false /* preferBadWifi */,
                false /* explicitlySelected */, timeout -> timeout < 14_000);
    }

    @Test
    public void testPreferBadWifi_doNotAvoid_doPrefer_notExplicit() throws Exception {
        doTestPreferBadWifi(false /* avoidBadWifi */, true /* preferBadWifi */,
                false /* explicitlySelected */, timeout -> timeout > 14_000);
    }

    @Test
    public void testPreferBadWifi_doNotAvoid_doPrefer_explicitlySelected() throws Exception {
        doTestPreferBadWifi(false /* avoidBadWifi */, true /* preferBadWifi */,
                true /* explicitlySelected */, timeout -> timeout < 14_000);
    }

    @Test
    public void testPreferBadWifi_doAvoid_doNotPrefer() throws Exception {
        // If avoidBadWifi=true, then preferBadWifi should be irrelevant. Test anyway.
        doTestPreferBadWifi(true /* avoidBadWifi */, false /* preferBadWifi */,
                false /* explicitlySelected */, timeout -> timeout < 14_000);
    }

    @Test
    public void testPreferBadWifi_doAvoid_doPrefer() throws Exception {
        // If avoidBadWifi=true, then preferBadWifi should be irrelevant. Test anyway.
        doTestPreferBadWifi(true /* avoidBadWifi */, true /* preferBadWifi */,
                false /* explicitlySelected */, timeout -> timeout < 14_000);
    }

    @Test
    public void testAvoidBadWifi() throws Exception {
        final ContentResolver cr = mServiceContext.getContentResolver();

        // Pretend we're on a carrier that restricts switching away from bad wifi.
        doReturn(0).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);

        // File a request for cell to ensure it doesn't go down.
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);

        TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        NetworkRequest validatedWifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_VALIDATED)
                .build();
        TestNetworkCallback validatedWifiCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(validatedWifiRequest, validatedWifiCallback);

        // Prompt mode, so notifications can be tested
        Settings.Global.putString(cr, ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI, null);
        mPolicyTracker.reevaluate();

        // Bring up validated cell.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        Network cellNetwork = mCellAgent.getNetwork();

        // Bring up validated wifi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        validatedWifiCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        Network wifiNetwork = mWiFiAgent.getNetwork();

        // Fail validation on wifi.
        mWiFiAgent.setNetworkInvalid(false /* invalidBecauseOfPrivateDns */);
        mCm.reportNetworkConnectivity(wifiNetwork, false);
        defaultCallback.expectCaps(mWiFiAgent, c -> !c.hasCapability(NET_CAPABILITY_VALIDATED));
        validatedWifiCallback.expect(LOST, mWiFiAgent);
        expectNotification(mWiFiAgent, NotificationType.LOST_INTERNET);

        // Because avoid bad wifi is off, we don't switch to cellular.
        defaultCallback.assertNoCallback();
        assertFalse(mCm.getNetworkCapabilities(wifiNetwork).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertTrue(mCm.getNetworkCapabilities(cellNetwork).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertEquals(mCm.getActiveNetwork(), wifiNetwork);

        // Simulate switching to a carrier that does not restrict avoiding bad wifi, and expect
        // that we switch back to cell.
        doReturn(1).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        mPolicyTracker.reevaluate();
        defaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertEquals(mCm.getActiveNetwork(), cellNetwork);
        expectClearNotification(mWiFiAgent, NotificationType.LOST_INTERNET);

        // Switch back to a restrictive carrier.
        doReturn(0).when(mResources).getInteger(R.integer.config_networkAvoidBadWifi);
        mPolicyTracker.reevaluate();
        defaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mCm.getActiveNetwork(), wifiNetwork);
        // A notification was already shown for this very network.
        expectNoNotification(mWiFiAgent);

        // Simulate the user selecting "switch" on the dialog, and check that we switch to cell.
        // In principle this is a little bit unrealistic because the switch to a less restrictive
        // carrier above should have remove the notification but this doesn't matter for the
        // purposes of this test.
        mCm.setAvoidUnvalidated(wifiNetwork);
        defaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertFalse(mCm.getNetworkCapabilities(wifiNetwork).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertTrue(mCm.getNetworkCapabilities(cellNetwork).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertEquals(mCm.getActiveNetwork(), cellNetwork);

        // Disconnect and reconnect wifi to clear the one-time switch above.
        mWiFiAgent.disconnect();
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        validatedWifiCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        wifiNetwork = mWiFiAgent.getNetwork();

        // Fail validation on wifi and expect the dialog to appear.
        mWiFiAgent.setNetworkInvalid(false /* invalidBecauseOfPrivateDns */);
        mCm.reportNetworkConnectivity(wifiNetwork, false);
        defaultCallback.expectCaps(mWiFiAgent, c -> !c.hasCapability(NET_CAPABILITY_VALIDATED));
        validatedWifiCallback.expect(LOST, mWiFiAgent);
        expectNotification(mWiFiAgent, NotificationType.LOST_INTERNET);

        // Simulate the user selecting "switch" and checking the don't ask again checkbox.
        Settings.Global.putInt(cr, ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI, 1);
        mPolicyTracker.reevaluate();

        // We now switch to cell.
        defaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertFalse(mCm.getNetworkCapabilities(wifiNetwork).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertTrue(mCm.getNetworkCapabilities(cellNetwork).hasCapability(
                NET_CAPABILITY_VALIDATED));
        assertEquals(mCm.getActiveNetwork(), cellNetwork);
        expectClearNotification(mWiFiAgent, NotificationType.LOST_INTERNET);

        // Simulate the user turning the cellular fallback setting off and then on.
        // We switch to wifi and then to cell.
        Settings.Global.putString(cr, ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI, null);
        mPolicyTracker.reevaluate();
        defaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mCm.getActiveNetwork(), wifiNetwork);
        // Notification is cleared again because CS doesn't particularly remember that it has
        // cleared it before, and if it hasn't cleared it before then it should do so now.
        expectClearNotification(mWiFiAgent, NotificationType.LOST_INTERNET);
        Settings.Global.putInt(cr, ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI, 1);
        mPolicyTracker.reevaluate();
        defaultCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertEquals(mCm.getActiveNetwork(), cellNetwork);

        // If cell goes down, we switch to wifi.
        mCellAgent.disconnect();
        defaultCallback.expect(LOST, mCellAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        validatedWifiCallback.assertNoCallback();
        // Notification is cleared yet again because the device switched to wifi.
        expectClearNotification(mWiFiAgent, NotificationType.LOST_INTERNET);

        mCm.unregisterNetworkCallback(cellNetworkCallback);
        mCm.unregisterNetworkCallback(validatedWifiCallback);
        mCm.unregisterNetworkCallback(defaultCallback);
    }

    @Test
    public void testMeteredMultipathPreferenceSetting() throws Exception {
        final ContentResolver cr = mServiceContext.getContentResolver();
        final String settingName = ConnectivitySettingsManager.NETWORK_METERED_MULTIPATH_PREFERENCE;

        for (int config : asList(0, 3, 2)) {
            for (String setting: asList(null, "0", "2", "1")) {
                mPolicyTracker.mConfigMeteredMultipathPreference = config;
                Settings.Global.putString(cr, settingName, setting);
                mPolicyTracker.reevaluate();
                waitForIdle();

                final int expected = (setting != null) ? Integer.parseInt(setting) : config;
                String msg = String.format("config=%d, setting=%s", config, setting);
                assertEquals(msg, expected, mCm.getMultipathPreference(null));
            }
        }
    }

    /**
     * Validate that a satisfied network request does not trigger onUnavailable() once the
     * time-out period expires.
     */
    @Test
    public void testSatisfiedNetworkRequestDoesNotTriggerOnUnavailable() throws Exception {
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.requestNetwork(nr, networkCallback, TEST_REQUEST_TIMEOUT_MS);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        networkCallback.expectAvailableCallbacks(mWiFiAgent, false, false, false,
                TEST_CALLBACK_TIMEOUT_MS);

        // pass timeout and validate that UNAVAILABLE is not called
        networkCallback.assertNoCallback();
    }

    /**
     * Validate that a satisfied network request followed by a disconnected (lost) network does
     * not trigger onUnavailable() once the time-out period expires.
     */
    @Test
    public void testSatisfiedThenLostNetworkRequestDoesNotTriggerOnUnavailable() throws Exception {
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.requestNetwork(nr, networkCallback, TEST_REQUEST_TIMEOUT_MS);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        networkCallback.expectAvailableCallbacks(mWiFiAgent, false, false, false,
                TEST_CALLBACK_TIMEOUT_MS);
        mWiFiAgent.disconnect();
        networkCallback.expect(LOST, mWiFiAgent);

        // Validate that UNAVAILABLE is not called
        networkCallback.assertNoCallback();
    }

    /**
     * Validate that when a time-out is specified for a network request the onUnavailable()
     * callback is called when time-out expires. Then validate that if network request is
     * (somehow) satisfied - the callback isn't called later.
     */
    @Test
    public void testTimedoutNetworkRequest() throws Exception {
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        final int timeoutMs = 10;
        mCm.requestNetwork(nr, networkCallback, timeoutMs);

        // pass timeout and validate that UNAVAILABLE is called
        networkCallback.expect(UNAVAILABLE);

        // create a network satisfying request - validate that request not triggered
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        networkCallback.assertNoCallback();
    }

    /**
     * Validate that when a network request is unregistered (cancelled), no posterior event can
     * trigger the callback.
     */
    @Test
    public void testNoCallbackAfterUnregisteredNetworkRequest() throws Exception {
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        final int timeoutMs = 10;

        mCm.requestNetwork(nr, networkCallback, timeoutMs);
        mCm.unregisterNetworkCallback(networkCallback);
        // Regardless of the timeout, unregistering the callback in ConnectivityManager ensures
        // that this callback will not be called.
        networkCallback.assertNoCallback();

        // create a network satisfying request - validate that request not triggered
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        networkCallback.assertNoCallback();
    }

    @Test
    public void testUnfulfillableNetworkRequest() throws Exception {
        runUnfulfillableNetworkRequest(false);
    }

    @Test
    public void testUnfulfillableNetworkRequestAfterUnregister() throws Exception {
        runUnfulfillableNetworkRequest(true);
    }

    /**
     * Validate the callback flow for a factory releasing a request as unfulfillable.
     */
    private void runUnfulfillableNetworkRequest(boolean preUnregister) throws Exception {
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();

        final HandlerThread handlerThread = new HandlerThread("testUnfulfillableNetworkRequest");
        handlerThread.start();
        NetworkCapabilities filter = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactory", filter, mCsHandlerThread);
        testFactory.setScoreFilter(40);

        // Register the factory and expect it to receive the default request.
        testFactory.register();
        testFactory.expectRequestAdd();

        try {
            // Now file the test request and expect it.
            mCm.requestNetwork(nr, networkCallback);
            final NetworkRequest newRequest = testFactory.expectRequestAdd().request;

            if (preUnregister) {
                mCm.unregisterNetworkCallback(networkCallback);

                // The request has been released : the factory should see it removed
                // immediately.
                testFactory.expectRequestRemove();

                // Simulate the factory releasing the request as unfulfillable: no-op since
                // the callback has already been unregistered (but a test that no exceptions are
                // thrown).
                testFactory.triggerUnfulfillable(newRequest);
            } else {
                // Simulate the factory releasing the request as unfulfillable and expect
                // onUnavailable!
                testFactory.triggerUnfulfillable(newRequest);

                networkCallback.expect(UNAVAILABLE);

                // Declaring a request unfulfillable releases it automatically.
                testFactory.expectRequestRemove();

                // unregister network callback - a no-op (since already freed by the
                // on-unavailable), but should not fail or throw exceptions.
                mCm.unregisterNetworkCallback(networkCallback);

                // The factory should not see any further removal, as this request has
                // already been removed.
            }
        } finally {
            testFactory.terminate();
            handlerThread.quitSafely();
            handlerThread.join();
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @DisableCompatChanges(ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION)
    public void testSelfCertifiedCapabilitiesDisabled()
            throws Exception {
        mDeps.enableCompatChangeCheck();
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                .build();
        final TestNetworkCallback cb = new TestNetworkCallback();
        mCm.requestNetwork(networkRequest, cb);
        mCm.unregisterNetworkCallback(cb);
    }

    /** Set the networkSliceResourceId to 0 will result in NameNotFoundException be thrown. */
    private void setupMockForNetworkCapabilitiesResources(int networkSliceResourceId)
            throws PackageManager.NameNotFoundException {
        if (networkSliceResourceId == 0) {
            doThrow(new PackageManager.NameNotFoundException()).when(mPackageManager).getProperty(
                    ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES,
                    mContext.getPackageName());
        } else {
            final PackageManager.Property property = new PackageManager.Property(
                    ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES,
                    networkSliceResourceId,
                    true /* isResource */,
                    mContext.getPackageName(),
                    "dummyClass"
            );
            doReturn(property).when(mPackageManager).getProperty(
                    ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES,
                    mContext.getPackageName());
            doReturn(mContext.getResources()).when(mPackageManager).getResourcesForApplication(
                    mContext.getPackageName());
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @EnableCompatChanges(ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION)
    public void requestNetwork_withoutPrioritizeBandwidthDeclaration_shouldThrowException()
            throws Exception {
        mDeps.enableCompatChangeCheck();
        setupMockForNetworkCapabilitiesResources(
                com.android.frameworks.tests.net.R.xml.self_certified_capabilities_latency);
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                .build();
        final TestNetworkCallback cb = new TestNetworkCallback();
        final Exception e = assertThrows(SecurityException.class,
                () -> mCm.requestNetwork(networkRequest, cb));
        assertThat(e.getMessage(),
                containsString(ApplicationSelfCertifiedNetworkCapabilities.PRIORITIZE_BANDWIDTH));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @EnableCompatChanges(ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION)
    public void requestNetwork_withoutPrioritizeLatencyDeclaration_shouldThrowException()
            throws Exception {
        mDeps.enableCompatChangeCheck();
        setupMockForNetworkCapabilitiesResources(
                com.android.frameworks.tests.net.R.xml.self_certified_capabilities_bandwidth);
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .build();
        final TestNetworkCallback cb = new TestNetworkCallback();
        final Exception e = assertThrows(SecurityException.class,
                () -> mCm.requestNetwork(networkRequest, cb));
        assertThat(e.getMessage(),
                containsString(ApplicationSelfCertifiedNetworkCapabilities.PRIORITIZE_LATENCY));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @EnableCompatChanges(ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION)
    public void requestNetwork_withoutNetworkSliceProperty_shouldThrowException() throws Exception {
        mDeps.enableCompatChangeCheck();
        setupMockForNetworkCapabilitiesResources(0 /* networkSliceResourceId */);
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                .build();
        final TestNetworkCallback cb = new TestNetworkCallback();
        final Exception e = assertThrows(SecurityException.class,
                () -> mCm.requestNetwork(networkRequest, cb));
        assertThat(e.getMessage(),
                containsString(ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @EnableCompatChanges(ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION)
    public void requestNetwork_withNetworkSliceDeclaration_shouldSucceed() throws Exception {
        mDeps.enableCompatChangeCheck();
        setupMockForNetworkCapabilitiesResources(
                com.android.frameworks.tests.net.R.xml.self_certified_capabilities_both);

        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                .build();
        final TestNetworkCallback cb = new TestNetworkCallback();
        mCm.requestNetwork(networkRequest, cb);
        mCm.unregisterNetworkCallback(cb);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @EnableCompatChanges(ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION)
    public void requestNetwork_withNetworkSliceDeclaration_shouldUseCache() throws Exception {
        mDeps.enableCompatChangeCheck();
        setupMockForNetworkCapabilitiesResources(
                com.android.frameworks.tests.net.R.xml.self_certified_capabilities_both);

        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                .build();
        final TestNetworkCallback cb = new TestNetworkCallback();
        mCm.requestNetwork(networkRequest, cb);
        mCm.unregisterNetworkCallback(cb);

        // Second call should use caches
        mCm.requestNetwork(networkRequest, cb);
        mCm.unregisterNetworkCallback(cb);

        // PackageManager's API only called once because the second call is using cache.
        verify(mPackageManager, times(1)).getProperty(
                ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES,
                mContext.getPackageName());
        verify(mPackageManager, times(1)).getResourcesForApplication(
                mContext.getPackageName());
    }

    /**
     * Validate the service throws if request with CBS but without carrier privilege.
     */
    @Test
    public void testCBSRequestWithoutCarrierPrivilege() throws Exception {
        final NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                TRANSPORT_CELLULAR).addCapability(NET_CAPABILITY_CBS).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();

        mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_DENIED);
        // Now file the test request and expect the service throws.
        assertThrows(SecurityException.class, () -> mCm.requestNetwork(nr, networkCallback));
    }

    private static class TestKeepaliveCallback extends PacketKeepaliveCallback {

        public enum CallbackType { ON_STARTED, ON_STOPPED, ON_ERROR }

        private class CallbackValue {
            public CallbackType callbackType;
            public int error;

            public CallbackValue(CallbackType type) {
                this.callbackType = type;
                this.error = PacketKeepalive.SUCCESS;
                assertTrue("onError callback must have error", type != CallbackType.ON_ERROR);
            }

            public CallbackValue(CallbackType type, int error) {
                this.callbackType = type;
                this.error = error;
                assertEquals("error can only be set for onError", type, CallbackType.ON_ERROR);
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CallbackValue &&
                        this.callbackType == ((CallbackValue) o).callbackType &&
                        this.error == ((CallbackValue) o).error;
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %d)", getClass().getSimpleName(), callbackType, error);
            }
        }

        private final LinkedBlockingQueue<CallbackValue> mCallbacks = new LinkedBlockingQueue<>();

        @Override
        public void onStarted() {
            mCallbacks.add(new CallbackValue(CallbackType.ON_STARTED));
        }

        @Override
        public void onStopped() {
            mCallbacks.add(new CallbackValue(CallbackType.ON_STOPPED));
        }

        @Override
        public void onError(int error) {
            mCallbacks.add(new CallbackValue(CallbackType.ON_ERROR, error));
        }

        private void expect(CallbackValue callbackValue) throws InterruptedException {
            assertEquals(callbackValue, mCallbacks.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }

        public void expectStarted() throws Exception {
            expect(new CallbackValue(CallbackType.ON_STARTED));
        }

        public void expectStopped() throws Exception {
            expect(new CallbackValue(CallbackType.ON_STOPPED));
        }

        public void expectError(int error) throws Exception {
            expect(new CallbackValue(CallbackType.ON_ERROR, error));
        }
    }

    private static class TestSocketKeepaliveCallback extends SocketKeepalive.Callback {

        public enum CallbackType { ON_STARTED, ON_STOPPED, ON_ERROR };

        private class CallbackValue {
            public CallbackType callbackType;
            public int error;

            CallbackValue(CallbackType type) {
                this.callbackType = type;
                this.error = SocketKeepalive.SUCCESS;
                assertTrue("onError callback must have error", type != CallbackType.ON_ERROR);
            }

            CallbackValue(CallbackType type, int error) {
                this.callbackType = type;
                this.error = error;
                assertEquals("error can only be set for onError", type, CallbackType.ON_ERROR);
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CallbackValue
                        && this.callbackType == ((CallbackValue) o).callbackType
                        && this.error == ((CallbackValue) o).error;
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %d)", getClass().getSimpleName(), callbackType,
                        error);
            }
        }

        private LinkedBlockingQueue<CallbackValue> mCallbacks = new LinkedBlockingQueue<>();
        private final Executor mExecutor;

        TestSocketKeepaliveCallback(@NonNull Executor executor) {
            mExecutor = executor;
        }

        @Override
        public void onStarted() {
            mCallbacks.add(new CallbackValue(CallbackType.ON_STARTED));
        }

        @Override
        public void onStopped() {
            mCallbacks.add(new CallbackValue(CallbackType.ON_STOPPED));
        }

        @Override
        public void onError(int error) {
            mCallbacks.add(new CallbackValue(CallbackType.ON_ERROR, error));
        }

        private void expect(CallbackValue callbackValue) throws InterruptedException {
            assertEquals(callbackValue, mCallbacks.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        }

        public void expectStarted() throws InterruptedException {
            expect(new CallbackValue(CallbackType.ON_STARTED));
        }

        public void expectStopped() throws InterruptedException {
            expect(new CallbackValue(CallbackType.ON_STOPPED));
        }

        public void expectError(int error) throws InterruptedException {
            expect(new CallbackValue(CallbackType.ON_ERROR, error));
        }

        public void assertNoCallback() {
            waitForIdleSerialExecutor(mExecutor, TIMEOUT_MS);
            CallbackValue cv = mCallbacks.peek();
            assertNull("Unexpected callback: " + cv, cv);
        }
    }

    private Network connectKeepaliveNetwork(LinkProperties lp) throws Exception {
        // Ensure the network is disconnected before anything else occurs
        if (mWiFiAgent != null) {
            assertNull(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()));
        }

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTED);
        mWiFiAgent.connect(true);
        b.expectBroadcast();
        verifyActiveNetwork(TRANSPORT_WIFI);
        mWiFiAgent.sendLinkProperties(lp);
        waitForIdle();
        return mWiFiAgent.getNetwork();
    }

    @Test
    public void testPacketKeepalives() throws Exception {
        final LinkAddress v4Addr = new LinkAddress("192.0.2.129/24");
        final InetAddress myIPv4 = v4Addr.getAddress();
        InetAddress notMyIPv4 = InetAddress.getByName("192.0.2.35");
        InetAddress myIPv6 = InetAddress.getByName("2001:db8::1");
        InetAddress dstIPv4 = InetAddress.getByName("8.8.8.8");
        InetAddress dstIPv6 = InetAddress.getByName("2001:4860:4860::8888");
        doReturn(getClatInterfaceConfigParcel(v4Addr)).when(mMockNetd)
                .interfaceGetCfg(CLAT_MOBILE_IFNAME);
        final int validKaInterval = 15;
        final int invalidKaInterval = 9;

        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(MOBILE_IFNAME);
        lp.addLinkAddress(new LinkAddress(myIPv6, 64));
        lp.addLinkAddress(new LinkAddress(myIPv4, 25));
        lp.addRoute(new RouteInfo(InetAddress.getByName("fe80::1234")));
        lp.addRoute(new RouteInfo(InetAddress.getByName("192.0.2.254")));

        Network notMyNet = new Network(61234);
        Network myNet = connectKeepaliveNetwork(lp);

        TestKeepaliveCallback callback = new TestKeepaliveCallback();
        PacketKeepalive ka;

        // Attempt to start keepalives with invalid parameters and check for errors.
        ka = mCm.startNattKeepalive(notMyNet, validKaInterval, callback, myIPv4, 1234, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_INVALID_NETWORK);

        ka = mCm.startNattKeepalive(myNet, invalidKaInterval, callback, myIPv4, 1234, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_INVALID_INTERVAL);

        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 1234, dstIPv6);
        callback.expectError(PacketKeepalive.ERROR_INVALID_IP_ADDRESS);

        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv6, 1234, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_INVALID_IP_ADDRESS);

        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 123456, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_INVALID_PORT);

        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 123456, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_INVALID_PORT);

        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 12345, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_HARDWARE_UNSUPPORTED);

        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 12345, dstIPv4);
        callback.expectError(PacketKeepalive.ERROR_HARDWARE_UNSUPPORTED);

        // Check that a started keepalive can be stopped.
        mWiFiAgent.setStartKeepaliveEvent(PacketKeepalive.SUCCESS);
        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 12345, dstIPv4);
        callback.expectStarted();
        mWiFiAgent.setStopKeepaliveEvent(PacketKeepalive.SUCCESS);
        ka.stop();
        callback.expectStopped();

        // Check that deleting the IP address stops the keepalive.
        LinkProperties bogusLp = new LinkProperties(lp);
        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 12345, dstIPv4);
        callback.expectStarted();
        bogusLp.removeLinkAddress(new LinkAddress(myIPv4, 25));
        bogusLp.addLinkAddress(new LinkAddress(notMyIPv4, 25));
        mWiFiAgent.sendLinkProperties(bogusLp);
        callback.expectError(PacketKeepalive.ERROR_INVALID_IP_ADDRESS);
        mWiFiAgent.sendLinkProperties(lp);

        // Check that a started keepalive is stopped correctly when the network disconnects.
        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 12345, dstIPv4);
        callback.expectStarted();
        mWiFiAgent.disconnect();
        mWiFiAgent.expectDisconnected();
        callback.expectError(PacketKeepalive.ERROR_INVALID_NETWORK);

        // ... and that stopping it after that has no adverse effects.
        waitForIdle();
        final Network myNetAlias = myNet;
        assertNull(mCm.getNetworkCapabilities(myNetAlias));
        ka.stop();

        // Reconnect.
        myNet = connectKeepaliveNetwork(lp);
        mWiFiAgent.setStartKeepaliveEvent(PacketKeepalive.SUCCESS);

        // Check that keepalive slots start from 1 and increment. The first one gets slot 1.
        mWiFiAgent.setExpectedKeepaliveSlot(1);
        ka = mCm.startNattKeepalive(myNet, validKaInterval, callback, myIPv4, 12345, dstIPv4);
        callback.expectStarted();

        // The second one gets slot 2.
        mWiFiAgent.setExpectedKeepaliveSlot(2);
        TestKeepaliveCallback callback2 = new TestKeepaliveCallback();
        PacketKeepalive ka2 = mCm.startNattKeepalive(
                myNet, validKaInterval, callback2, myIPv4, 6789, dstIPv4);
        callback2.expectStarted();

        // Now stop the first one and create a third. This also gets slot 1.
        ka.stop();
        callback.expectStopped();

        mWiFiAgent.setExpectedKeepaliveSlot(1);
        TestKeepaliveCallback callback3 = new TestKeepaliveCallback();
        PacketKeepalive ka3 = mCm.startNattKeepalive(
                myNet, validKaInterval, callback3, myIPv4, 9876, dstIPv4);
        callback3.expectStarted();

        ka2.stop();
        callback2.expectStopped();

        ka3.stop();
        callback3.expectStopped();
    }

    // Helper method to prepare the executor and run test
    private void runTestWithSerialExecutors(ThrowingConsumer<Executor> functor)
            throws Exception {
        final ExecutorService executorSingleThread = Executors.newSingleThreadExecutor();
        final Executor executorInline = (Runnable r) -> r.run();
        functor.accept(executorSingleThread);
        executorSingleThread.shutdown();
        functor.accept(executorInline);
    }

    @Test
    public void testNattSocketKeepalives() throws Exception {
        runTestWithSerialExecutors(executor -> doTestNattSocketKeepalivesWithExecutor(executor));
        runTestWithSerialExecutors(executor -> doTestNattSocketKeepalivesFdWithExecutor(executor));
    }

    private void doTestNattSocketKeepalivesWithExecutor(Executor executor) throws Exception {
        // TODO: 1. Move this outside of ConnectivityServiceTest.
        //       2. Make test to verify that Nat-T keepalive socket is created by IpSecService.
        //       3. Mock ipsec service.
        final InetAddress myIPv4 = InetAddress.getByName("192.0.2.129");
        final InetAddress notMyIPv4 = InetAddress.getByName("192.0.2.35");
        final InetAddress myIPv6 = InetAddress.getByName("2001:db8::1");
        final InetAddress dstIPv4 = InetAddress.getByName("8.8.8.8");
        final InetAddress dstIPv6 = InetAddress.getByName("2001:4860:4860::8888");

        final int validKaInterval = 15;
        final int invalidKaInterval = 9;

        final IpSecManager mIpSec = (IpSecManager) mContext.getSystemService(Context.IPSEC_SERVICE);
        final UdpEncapsulationSocket testSocket = mIpSec.openUdpEncapsulationSocket();
        final int srcPort = testSocket.getPort();

        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("wlan12");
        lp.addLinkAddress(new LinkAddress(myIPv6, 64));
        lp.addLinkAddress(new LinkAddress(myIPv4, 25));
        lp.addRoute(new RouteInfo(InetAddress.getByName("fe80::1234")));
        lp.addRoute(new RouteInfo(InetAddress.getByName("192.0.2.254")));

        Network notMyNet = new Network(61234);
        Network myNet = connectKeepaliveNetwork(lp);

        TestSocketKeepaliveCallback callback = new TestSocketKeepaliveCallback(executor);

        // Attempt to start keepalives with invalid parameters and check for errors.
        // Invalid network.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                notMyNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_NETWORK);
        }

        // Invalid interval.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(invalidKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_INTERVAL);
        }

        // Invalid destination.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv6, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
        }

        // Invalid source;
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv6, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
        }

        // Basic check before testing started keepalive.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_UNSUPPORTED);
        }

        // Check that a started keepalive can be stopped.
        mWiFiAgent.setStartKeepaliveEvent(SocketKeepalive.SUCCESS);
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectStarted();
            mWiFiAgent.setStopKeepaliveEvent(SocketKeepalive.SUCCESS);
            ka.stop();
            callback.expectStopped();

            // Check that keepalive could be restarted.
            ka.start(validKaInterval);
            callback.expectStarted();
            ka.stop();
            callback.expectStopped();

            // Check that keepalive can be restarted without waiting for callback.
            ka.start(validKaInterval);
            callback.expectStarted();
            ka.stop();
            ka.start(validKaInterval);
            callback.expectStopped();
            callback.expectStarted();
            ka.stop();
            callback.expectStopped();
        }

        // Check that deleting the IP address stops the keepalive.
        LinkProperties bogusLp = new LinkProperties(lp);
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectStarted();
            bogusLp.removeLinkAddress(new LinkAddress(myIPv4, 25));
            bogusLp.addLinkAddress(new LinkAddress(notMyIPv4, 25));
            mWiFiAgent.sendLinkProperties(bogusLp);
            callback.expectError(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
            mWiFiAgent.sendLinkProperties(lp);
        }

        // Check that a started keepalive is stopped correctly when the network disconnects.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectStarted();
            mWiFiAgent.disconnect();
            mWiFiAgent.expectDisconnected();
            callback.expectError(SocketKeepalive.ERROR_INVALID_NETWORK);

            // ... and that stopping it after that has no adverse effects.
            waitForIdle();
            assertNull(mCm.getNetworkCapabilities(myNet));
            ka.stop();
            callback.assertNoCallback();
        }

        // Reconnect.
        myNet = connectKeepaliveNetwork(lp);
        mWiFiAgent.setStartKeepaliveEvent(SocketKeepalive.SUCCESS);

        // Check that a stop followed by network disconnects does not result in crash.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectStarted();
            // Delay the response of keepalive events in networkAgent long enough to make sure
            // the follow-up network disconnection will be processed first.
            mWiFiAgent.setKeepaliveResponseDelay(3 * TIMEOUT_MS);
            ka.stop();
            // Call stop() twice shouldn't result in crash, b/182586681.
            ka.stop();

            // Make sure the stop has been processed. Wait for executor idle is needed to prevent
            // flaky since the actual stop call to the service is delegated to executor thread.
            waitForIdleSerialExecutor(executor, TIMEOUT_MS);
            waitForIdle();

            mWiFiAgent.disconnect();
            mWiFiAgent.expectDisconnected();
            callback.expectStopped();
            callback.assertNoCallback();
        }

        // Reconnect.
        waitForIdle();
        myNet = connectKeepaliveNetwork(lp);
        mWiFiAgent.setStartKeepaliveEvent(SocketKeepalive.SUCCESS);

        // Check that keepalive slots start from 1 and increment. The first one gets slot 1.
        mWiFiAgent.setExpectedKeepaliveSlot(1);
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
                myNet, testSocket, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectStarted();

            // The second one gets slot 2.
            mWiFiAgent.setExpectedKeepaliveSlot(2);
            final UdpEncapsulationSocket testSocket2 = mIpSec.openUdpEncapsulationSocket();
            TestSocketKeepaliveCallback callback2 = new TestSocketKeepaliveCallback(executor);
            try (SocketKeepalive ka2 = mCm.createSocketKeepalive(
                    myNet, testSocket2, myIPv4, dstIPv4, executor, callback2)) {
                ka2.start(validKaInterval);
                callback2.expectStarted();

                ka.stop();
                callback.expectStopped();

                ka2.stop();
                callback2.expectStopped();

                testSocket.close();
                testSocket2.close();
            }
        }

        // Check that there is no port leaked after all keepalives and sockets are closed.
        // TODO: enable this check after ensuring a valid free port. See b/129512753#comment7.
        // assertFalse(isUdpPortInUse(srcPort));
        // assertFalse(isUdpPortInUse(srcPort2));

        mWiFiAgent.disconnect();
        mWiFiAgent.expectDisconnected();
        mWiFiAgent = null;
    }

    @Test
    public void testTcpSocketKeepalives() throws Exception {
        runTestWithSerialExecutors(executor -> doTestTcpSocketKeepalivesWithExecutor(executor));
    }

    private void doTestTcpSocketKeepalivesWithExecutor(Executor executor) throws Exception {
        final int srcPortV4 = 12345;
        final int srcPortV6 = 23456;
        final InetAddress myIPv4 = InetAddress.getByName("127.0.0.1");
        final InetAddress myIPv6 = InetAddress.getByName("::1");

        final int validKaInterval = 15;

        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("wlan12");
        lp.addLinkAddress(new LinkAddress(myIPv6, 64));
        lp.addLinkAddress(new LinkAddress(myIPv4, 25));
        lp.addRoute(new RouteInfo(InetAddress.getByName("fe80::1234")));
        lp.addRoute(new RouteInfo(InetAddress.getByName("127.0.0.254")));

        final Network notMyNet = new Network(61234);
        final Network myNet = connectKeepaliveNetwork(lp);

        final Socket testSocketV4 = new Socket();
        final Socket testSocketV6 = new Socket();

        TestSocketKeepaliveCallback callback = new TestSocketKeepaliveCallback(executor);

        // Attempt to start Tcp keepalives with invalid parameters and check for errors.
        // Invalid network.
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
            notMyNet, testSocketV4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_NETWORK);
        }

        // Invalid Socket (socket is not bound with IPv4 address).
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
            myNet, testSocketV4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_SOCKET);
        }

        // Invalid Socket (socket is not bound with IPv6 address).
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
            myNet, testSocketV6, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_SOCKET);
        }

        // Bind the socket address
        testSocketV4.bind(new InetSocketAddress(myIPv4, srcPortV4));
        testSocketV6.bind(new InetSocketAddress(myIPv6, srcPortV6));

        // Invalid Socket (socket is bound with IPv4 address).
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
            myNet, testSocketV4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_SOCKET);
        }

        // Invalid Socket (socket is bound with IPv6 address).
        try (SocketKeepalive ka = mCm.createSocketKeepalive(
            myNet, testSocketV6, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectError(SocketKeepalive.ERROR_INVALID_SOCKET);
        }

        testSocketV4.close();
        testSocketV6.close();

        mWiFiAgent.disconnect();
        mWiFiAgent.expectDisconnected();
        mWiFiAgent = null;
    }

    private void doTestNattSocketKeepalivesFdWithExecutor(Executor executor) throws Exception {
        final InetAddress myIPv4 = InetAddress.getByName("192.0.2.129");
        final InetAddress anyIPv4 = InetAddress.getByName("0.0.0.0");
        final InetAddress dstIPv4 = InetAddress.getByName("8.8.8.8");
        final int validKaInterval = 15;

        // Prepare the target network.
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("wlan12");
        lp.addLinkAddress(new LinkAddress(myIPv4, 25));
        lp.addRoute(new RouteInfo(InetAddress.getByName("192.0.2.254")));
        Network myNet = connectKeepaliveNetwork(lp);
        mWiFiAgent.setStartKeepaliveEvent(SocketKeepalive.SUCCESS);
        mWiFiAgent.setStopKeepaliveEvent(SocketKeepalive.SUCCESS);

        TestSocketKeepaliveCallback callback = new TestSocketKeepaliveCallback(executor);

        // Prepare the target file descriptor, keep only one instance.
        final IpSecManager mIpSec = (IpSecManager) mContext.getSystemService(Context.IPSEC_SERVICE);
        final UdpEncapsulationSocket testSocket = mIpSec.openUdpEncapsulationSocket();
        final int srcPort = testSocket.getPort();
        final ParcelFileDescriptor testPfd =
                ParcelFileDescriptor.dup(testSocket.getFileDescriptor());
        testSocket.close();
        assertTrue(isUdpPortInUse(srcPort));

        // Start keepalive and explicit make the variable goes out of scope with try-with-resources
        // block.
        try (SocketKeepalive ka = mCm.createNattKeepalive(
                myNet, testPfd, myIPv4, dstIPv4, executor, callback)) {
            ka.start(validKaInterval);
            callback.expectStarted();
            ka.stop();
            callback.expectStopped();
        }

        // Check that the ParcelFileDescriptor is still valid after keepalive stopped,
        // ErrnoException with EBADF will be thrown if the socket is closed when checking local
        // address.
        assertTrue(isUdpPortInUse(srcPort));
        final InetSocketAddress sa =
                (InetSocketAddress) Os.getsockname(testPfd.getFileDescriptor());
        assertEquals(anyIPv4, sa.getAddress());

        testPfd.close();
        // TODO: enable this check after ensuring a valid free port. See b/129512753#comment7.
        // assertFalse(isUdpPortInUse(srcPort));

        mWiFiAgent.disconnect();
        mWiFiAgent.expectDisconnected();
        mWiFiAgent = null;
    }

    private static boolean isUdpPortInUse(int port) {
        try (DatagramSocket ignored = new DatagramSocket(port)) {
            return false;
        } catch (IOException alreadyInUse) {
            return true;
        }
    }

    @Test
    public void testGetCaptivePortalServerUrl() throws Exception {
        String url = mCm.getCaptivePortalServerUrl();
        assertEquals("http://connectivitycheck.gstatic.com/generate_204", url);
    }

    private static class TestNetworkPinner extends NetworkPinner {
        public static boolean awaitPin(int timeoutMs) throws InterruptedException {
            synchronized(sLock) {
                if (sNetwork == null) {
                    sLock.wait(timeoutMs);
                }
                return sNetwork != null;
            }
        }

        public static boolean awaitUnpin(int timeoutMs) throws InterruptedException {
            synchronized(sLock) {
                if (sNetwork != null) {
                    sLock.wait(timeoutMs);
                }
                return sNetwork == null;
            }
        }
    }

    private void assertPinnedToWifiWithCellDefault() {
        assertEquals(mWiFiAgent.getNetwork(), mCm.getBoundNetworkForProcess());
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
    }

    private void assertPinnedToWifiWithWifiDefault() {
        assertEquals(mWiFiAgent.getNetwork(), mCm.getBoundNetworkForProcess());
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
    }

    private void assertNotPinnedToWifi() {
        assertNull(mCm.getBoundNetworkForProcess());
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
    }

    @Test
    public void testNetworkPinner() throws Exception {
        NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        assertNull(mCm.getBoundNetworkForProcess());

        TestNetworkPinner.pin(mServiceContext, wifiRequest);
        assertNull(mCm.getBoundNetworkForProcess());

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);

        // When wi-fi connects, expect to be pinned.
        assertTrue(TestNetworkPinner.awaitPin(100));
        assertPinnedToWifiWithCellDefault();

        // Disconnect and expect the pin to drop.
        mWiFiAgent.disconnect();
        assertTrue(TestNetworkPinner.awaitUnpin(100));
        assertNotPinnedToWifi();

        // Reconnecting does not cause the pin to come back.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        assertFalse(TestNetworkPinner.awaitPin(100));
        assertNotPinnedToWifi();

        // Pinning while connected causes the pin to take effect immediately.
        TestNetworkPinner.pin(mServiceContext, wifiRequest);
        assertTrue(TestNetworkPinner.awaitPin(100));
        assertPinnedToWifiWithCellDefault();

        // Explicitly unpin and expect to use the default network again.
        TestNetworkPinner.unpin();
        assertNotPinnedToWifi();

        // Disconnect cell and wifi.
        ExpectedBroadcast b = expectConnectivityAction(3);  // cell down, wifi up, wifi down.
        mCellAgent.disconnect();
        mWiFiAgent.disconnect();
        b.expectBroadcast();

        // Pinning takes effect even if the pinned network is the default when the pin is set...
        TestNetworkPinner.pin(mServiceContext, wifiRequest);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        assertTrue(TestNetworkPinner.awaitPin(100));
        assertPinnedToWifiWithWifiDefault();

        // ... and is maintained even when that network is no longer the default.
        b = expectConnectivityAction(1);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mCellAgent.connect(true);
        b.expectBroadcast();
        assertPinnedToWifiWithCellDefault();
    }

    @Test
    public void testNetworkCallbackMaximum() throws Exception {
        final int MAX_REQUESTS = 100;
        final int CALLBACKS = 87;
        final int DIFF_INTENTS = 10;
        final int SAME_INTENTS = 10;
        final int SYSTEM_ONLY_MAX_REQUESTS = 250;
        // Assert 1 (Default request filed before testing) + CALLBACKS + DIFF_INTENTS +
        // 1 (same intent) = MAX_REQUESTS - 1, since the capacity is MAX_REQUEST - 1.
        assertEquals(MAX_REQUESTS - 1, 1 + CALLBACKS + DIFF_INTENTS + 1);

        NetworkRequest networkRequest = new NetworkRequest.Builder().build();
        ArrayList<Object> registered = new ArrayList<>();

        for (int j = 0; j < CALLBACKS; j++) {
            final NetworkCallback cb = new NetworkCallback();
            if (j < CALLBACKS / 2) {
                mCm.requestNetwork(networkRequest, cb);
            } else {
                mCm.registerNetworkCallback(networkRequest, cb);
            }
            registered.add(cb);
        }

        // Since ConnectivityService will de-duplicate the request with the same intent,
        // register multiple times does not really increase multiple requests.
        final PendingIntent same_pi = PendingIntent.getBroadcast(mContext, 0 /* requestCode */,
                new Intent("same"), FLAG_IMMUTABLE);
        for (int j = 0; j < SAME_INTENTS; j++) {
            mCm.registerNetworkCallback(networkRequest, same_pi);
            // Wait for the requests with the same intent to be de-duplicated. Because
            // ConnectivityService side incrementCountOrThrow in binder, decrementCount in handler
            // thread, waitForIdle is needed to ensure decrementCount being invoked for same intent
            // requests before doing further tests.
            waitForIdle();
        }
        for (int j = 0; j < SAME_INTENTS; j++) {
            mCm.requestNetwork(networkRequest, same_pi);
            // Wait for the requests with the same intent to be de-duplicated.
            // Refer to the reason above.
            waitForIdle();
        }
        registered.add(same_pi);

        for (int j = 0; j < DIFF_INTENTS; j++) {
            if (j < DIFF_INTENTS / 2) {
                final PendingIntent pi = PendingIntent.getBroadcast(mContext, 0 /* requestCode */,
                        new Intent("a" + j), FLAG_IMMUTABLE);
                mCm.requestNetwork(networkRequest, pi);
                registered.add(pi);
            } else {
                final PendingIntent pi = PendingIntent.getBroadcast(mContext, 0 /* requestCode */,
                        new Intent("b" + j), FLAG_IMMUTABLE);
                mCm.registerNetworkCallback(networkRequest, pi);
                registered.add(pi);
            }
        }

        // Test that the limit is enforced when MAX_REQUESTS simultaneous requests are added.
        assertThrows(TooManyRequestsException.class, () ->
                mCm.requestNetwork(networkRequest, new NetworkCallback())
        );
        assertThrows(TooManyRequestsException.class, () ->
                mCm.registerNetworkCallback(networkRequest, new NetworkCallback())
        );
        assertThrows(TooManyRequestsException.class, () ->
                mCm.requestNetwork(networkRequest,
                        PendingIntent.getBroadcast(mContext, 0 /* requestCode */,
                                new Intent("c"), FLAG_IMMUTABLE))
        );
        assertThrows(TooManyRequestsException.class, () ->
                mCm.registerNetworkCallback(networkRequest,
                        PendingIntent.getBroadcast(mContext, 0 /* requestCode */,
                                new Intent("d"), FLAG_IMMUTABLE))
        );

        // The system gets another SYSTEM_ONLY_MAX_REQUESTS slots.
        final Handler handler = new Handler(ConnectivityThread.getInstanceLooper());
        withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, () -> {
            ArrayList<NetworkCallback> systemRegistered = new ArrayList<>();
            for (int i = 0; i < SYSTEM_ONLY_MAX_REQUESTS - 1; i++) {
                NetworkCallback cb = new NetworkCallback();
                if (i % 2 == 0) {
                    mCm.registerDefaultNetworkCallbackForUid(1000000 + i, cb, handler);
                } else {
                    mCm.registerNetworkCallback(networkRequest, cb);
                }
                systemRegistered.add(cb);
            }
            waitForIdle();

            assertThrows(TooManyRequestsException.class, () ->
                    mCm.registerDefaultNetworkCallbackForUid(1001042, new NetworkCallback(),
                            handler));
            assertThrows(TooManyRequestsException.class, () ->
                    mCm.registerNetworkCallback(networkRequest, new NetworkCallback()));

            for (NetworkCallback callback : systemRegistered) {
                mCm.unregisterNetworkCallback(callback);
            }
            waitForIdle();  // Wait for requests to be unregistered before giving up the permission.
        });

        for (Object o : registered) {
            if (o instanceof NetworkCallback) {
                mCm.unregisterNetworkCallback((NetworkCallback) o);
            }
            if (o instanceof PendingIntent) {
                mCm.unregisterNetworkCallback((PendingIntent) o);
            }
        }
        waitForIdle();

        // Test that the limit is not hit when MAX_REQUESTS requests are added and removed.
        for (int i = 0; i < MAX_REQUESTS; i++) {
            NetworkCallback networkCallback = new NetworkCallback();
            mCm.requestNetwork(networkRequest, networkCallback);
            mCm.unregisterNetworkCallback(networkCallback);
            // While requestNetwork increases the count synchronously, unregister decreases it
            // asynchronously on a handler, so unregistering doesn't immediately free up
            // a slot : calling unregister-register when max requests are registered throws.
            // Potential fix : ConnectivityService catches TooManyRequestsException once when
            // creating NetworkRequestInfo and waits for handler thread (see
            // https://r.android.com/2707373 for impl). However, this complexity is not equal to
            // the issue ; the purpose of having "max requests" is only to help apps detect leaks.
            // Apps relying on exact enforcement or rapid request registration should reconsider.
            //
            // In this test, test thread registering all before handler thread decrements can cause
            // flakes. A single waitForIdle at (e.g.) MAX_REQUESTS / 2 processes decrements up to
            // that point, fixing the flake.
            if (MAX_REQUESTS / 2 == i) waitForIdle();
        }
        waitForIdle();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            NetworkCallback networkCallback = new NetworkCallback();
            mCm.registerNetworkCallback(networkRequest, networkCallback);
            mCm.unregisterNetworkCallback(networkCallback);
            // See comment above for the reasons for this wait.
            if (MAX_REQUESTS / 2 == i) waitForIdle();
        }
        waitForIdle();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            NetworkCallback networkCallback = new NetworkCallback();
            mCm.registerDefaultNetworkCallback(networkCallback);
            mCm.unregisterNetworkCallback(networkCallback);
            // See comment above for the reasons for this wait.
            if (MAX_REQUESTS / 2 == i) waitForIdle();
        }
        waitForIdle();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            NetworkCallback networkCallback = new NetworkCallback();
            mCm.registerDefaultNetworkCallback(networkCallback);
            mCm.unregisterNetworkCallback(networkCallback);
            // See comment above for the reasons for this wait.
            if (MAX_REQUESTS / 2 == i) waitForIdle();
        }
        waitForIdle();

        withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, () -> {
            for (int i = 0; i < MAX_REQUESTS; i++) {
                NetworkCallback networkCallback = new NetworkCallback();
                mCm.registerDefaultNetworkCallbackForUid(1000000 + i, networkCallback,
                        new Handler(ConnectivityThread.getInstanceLooper()));
                mCm.unregisterNetworkCallback(networkCallback);
                // See comment above for the reasons for this wait.
                if (MAX_REQUESTS / 2 == i) waitForIdle();
            }
        });
        waitForIdle();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    mContext, 0 /* requestCode */, new Intent("e" + i), FLAG_IMMUTABLE);
            mCm.requestNetwork(networkRequest, pendingIntent);
            mCm.unregisterNetworkCallback(pendingIntent);
            // See comment above for the reasons for this wait.
            if (MAX_REQUESTS / 2 == i) waitForIdle();
        }
        waitForIdle();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    mContext, 0 /* requestCode */, new Intent("f" + i), FLAG_IMMUTABLE);
            mCm.registerNetworkCallback(networkRequest, pendingIntent);
            mCm.unregisterNetworkCallback(pendingIntent);
            // See comment above for the reasons for this wait.
            if (MAX_REQUESTS / 2 == i) waitForIdle();
        }
    }

    @Test
    public void testNetworkInfoOfTypeNone() throws Exception {
        ExpectedBroadcast b = expectConnectivityAction(1);

        verifyNoNetwork();
        TestNetworkAgentWrapper wifiAware = new TestNetworkAgentWrapper(TRANSPORT_WIFI_AWARE);
        assertNull(mCm.getActiveNetworkInfo());

        Network[] allNetworks = mCm.getAllNetworks();
        assertLength(1, allNetworks);
        Network network = allNetworks[0];
        NetworkCapabilities capabilities = mCm.getNetworkCapabilities(network);
        assertTrue(capabilities.hasTransport(TRANSPORT_WIFI_AWARE));

        final NetworkRequest request =
                new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI_AWARE).build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        // Bring up wifi aware network.
        wifiAware.connect(false, false, false /* privateDnsProbeSent */);
        callback.expectAvailableCallbacksUnvalidated(wifiAware);

        assertNull(mCm.getActiveNetworkInfo());
        assertNull(mCm.getActiveNetwork());
        // TODO: getAllNetworkInfo is dirty and returns a non-empty array right from the start
        // of this test. Fix it and uncomment the assert below.
        //assertEmpty(mCm.getAllNetworkInfo());

        // Disconnect wifi aware network.
        wifiAware.disconnect();
        callback.expect(LOST, TIMEOUT_MS);
        mCm.unregisterNetworkCallback(callback);

        verifyNoNetwork();
        b.expectNoBroadcast(10);
    }

    @Test
    public void testDeprecatedAndUnsupportedOperations() throws Exception {
        final int TYPE_NONE = ConnectivityManager.TYPE_NONE;
        assertNull(mCm.getNetworkInfo(TYPE_NONE));
        assertNull(mCm.getNetworkForType(TYPE_NONE));
        assertNull(mCm.getLinkProperties(TYPE_NONE));
        assertFalse(mCm.isNetworkSupported(TYPE_NONE));

        assertThrows(IllegalArgumentException.class,
                () -> mCm.networkCapabilitiesForType(TYPE_NONE));

        Class<UnsupportedOperationException> unsupported = UnsupportedOperationException.class;
        assertThrows(unsupported, () -> mCm.startUsingNetworkFeature(TYPE_WIFI, ""));
        assertThrows(unsupported, () -> mCm.stopUsingNetworkFeature(TYPE_WIFI, ""));
        // TODO: let test context have configuration application target sdk version
        // and test that pre-M requesting for TYPE_NONE sends back APN_REQUEST_FAILED
        assertThrows(unsupported, () -> mCm.startUsingNetworkFeature(TYPE_NONE, ""));
        assertThrows(unsupported, () -> mCm.stopUsingNetworkFeature(TYPE_NONE, ""));
        assertThrows(unsupported, () -> mCm.requestRouteToHostAddress(TYPE_NONE, null));
    }

    @Test
    public void testLinkPropertiesEnsuresDirectlyConnectedRoutes() throws Exception {
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(networkRequest, networkCallback);

        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        LinkAddress myIpv4Address = new LinkAddress("192.168.12.3/24");
        RouteInfo myIpv4DefaultRoute = new RouteInfo((IpPrefix) null,
                InetAddresses.parseNumericAddress("192.168.12.1"), lp.getInterfaceName());
        lp.addLinkAddress(myIpv4Address);
        lp.addRoute(myIpv4DefaultRoute);

        // Verify direct routes are added when network agent is first registered in
        // ConnectivityService.
        TestNetworkAgentWrapper networkAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        networkAgent.connect(true);
        networkCallback.expect(AVAILABLE, networkAgent);
        networkCallback.expect(NETWORK_CAPS_UPDATED, networkAgent);
        CallbackEntry.LinkPropertiesChanged cbi =
                networkCallback.expect(LINK_PROPERTIES_CHANGED, networkAgent);
        networkCallback.expect(BLOCKED_STATUS, networkAgent);
        networkCallback.expectCaps(networkAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        networkCallback.assertNoCallback();
        checkDirectlyConnectedRoutes(cbi.getLp(), asList(myIpv4Address),
                asList(myIpv4DefaultRoute));
        checkDirectlyConnectedRoutes(mCm.getLinkProperties(networkAgent.getNetwork()),
                asList(myIpv4Address), asList(myIpv4DefaultRoute));

        // Verify direct routes are added during subsequent link properties updates.
        LinkProperties newLp = new LinkProperties(lp);
        LinkAddress myIpv6Address1 = new LinkAddress("fe80::cafe/64");
        LinkAddress myIpv6Address2 = new LinkAddress("2001:db8::2/64");
        newLp.addLinkAddress(myIpv6Address1);
        newLp.addLinkAddress(myIpv6Address2);
        networkAgent.sendLinkProperties(newLp);
        cbi = networkCallback.expect(LINK_PROPERTIES_CHANGED, networkAgent);
        networkCallback.assertNoCallback();
        checkDirectlyConnectedRoutes(cbi.getLp(),
                asList(myIpv4Address, myIpv6Address1, myIpv6Address2),
                asList(myIpv4DefaultRoute));
        mCm.unregisterNetworkCallback(networkCallback);
    }

    private void expectNotifyNetworkStatus(List<Network> allNetworks, List<Network> defaultNetworks,
            String defaultIface, Integer vpnUid, String vpnIfname, List<String> underlyingIfaces)
            throws Exception {
        ArgumentCaptor<List<Network>> defaultNetworksCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UnderlyingNetworkInfo>> vpnInfosCaptor =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<NetworkStateSnapshot>> snapshotsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mStatsManager, atLeastOnce()).notifyNetworkStatus(defaultNetworksCaptor.capture(),
                snapshotsCaptor.capture(), eq(defaultIface), vpnInfosCaptor.capture());

        assertSameElements(defaultNetworks, defaultNetworksCaptor.getValue());

        List<Network> snapshotNetworks = new ArrayList<Network>();
        for (NetworkStateSnapshot ns : snapshotsCaptor.getValue()) {
            snapshotNetworks.add(ns.getNetwork());
        }
        assertSameElements(allNetworks, snapshotNetworks);

        if (defaultIface != null) {
            assertNotNull(
                    "Did not find interface " + defaultIface + " in call to notifyNetworkStatus",
                    CollectionUtils.findFirst(snapshotsCaptor.getValue(), (ns) -> {
                        final LinkProperties lp = ns.getLinkProperties();
                        if (lp != null && TextUtils.equals(defaultIface, lp.getInterfaceName())) {
                            return true;
                        }
                        return false;
                    }));
        }

        List<UnderlyingNetworkInfo> infos = vpnInfosCaptor.getValue();
        if (vpnUid != null) {
            assertEquals("Should have exactly one VPN:", 1, infos.size());
            UnderlyingNetworkInfo info = infos.get(0);
            assertEquals("Unexpected VPN owner:", (int) vpnUid, info.getOwnerUid());
            assertEquals("Unexpected VPN interface:", vpnIfname, info.getInterface());
            assertSameElements(underlyingIfaces, info.getUnderlyingInterfaces());
        } else {
            assertEquals(0, infos.size());
            return;
        }
    }

    private void expectNotifyNetworkStatus(
            List<Network> allNetworks, List<Network> defaultNetworks, String defaultIface)
            throws Exception {
        expectNotifyNetworkStatus(allNetworks, defaultNetworks, defaultIface, null, null,
                List.of());
    }

    private List<Network> onlyCell() {
        return List.of(mCellAgent.getNetwork());
    }

    private List<Network> onlyWifi() {
        return List.of(mWiFiAgent.getNetwork());
    }

    private List<Network> cellAndWifi() {
        return List.of(mCellAgent.getNetwork(), mWiFiAgent.getNetwork());
    }

    @Test
    public void testStatsIfacesChanged() throws Exception {
        LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);

        // Simple connection with initial LP should have updated ifaces.
        mCellAgent.connect(false);
        waitForIdle();
        List<Network> allNetworks = mService.shouldCreateNetworksImmediately()
                ? cellAndWifi() : onlyCell();
        expectNotifyNetworkStatus(allNetworks, onlyCell(), MOBILE_IFNAME);
        reset(mStatsManager);

        // Verify change fields other than interfaces does not trigger a notification to NSS.
        cellLp.addLinkAddress(new LinkAddress("192.0.2.4/24"));
        cellLp.addRoute(new RouteInfo((IpPrefix) null, InetAddress.getByName("192.0.2.4"),
                MOBILE_IFNAME));
        cellLp.setDnsServers(List.of(InetAddress.getAllByName("8.8.8.8")));
        mCellAgent.sendLinkProperties(cellLp);
        verifyNoMoreInteractions(mStatsManager);
        reset(mStatsManager);

        // Default network switch should update ifaces.
        mWiFiAgent.connect(false);
        mWiFiAgent.sendLinkProperties(wifiLp);
        waitForIdle();
        assertEquals(wifiLp, mService.getActiveLinkProperties());
        expectNotifyNetworkStatus(cellAndWifi(), onlyWifi(), WIFI_IFNAME);
        reset(mStatsManager);

        // Disconnecting a network updates ifaces again. The soon-to-be disconnected interface is
        // still in the list to ensure that stats are counted on that interface.
        // TODO: this means that if anything else uses that interface for any other reason before
        // notifyNetworkStatus is called again, traffic on that interface will be accounted to the
        // disconnected network. This is likely a bug in ConnectivityService; it should probably
        // call notifyNetworkStatus again without the disconnected network.
        mCellAgent.disconnect();
        waitForIdle();
        expectNotifyNetworkStatus(cellAndWifi(), onlyWifi(), WIFI_IFNAME);
        verifyNoMoreInteractions(mStatsManager);
        reset(mStatsManager);

        // Connecting a network updates ifaces even if the network doesn't become default.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(false);
        waitForIdle();
        expectNotifyNetworkStatus(cellAndWifi(), onlyWifi(), WIFI_IFNAME);
        reset(mStatsManager);

        // Disconnect should update ifaces.
        mWiFiAgent.disconnect();
        waitForIdle();
        expectNotifyNetworkStatus(onlyCell(), onlyCell(), MOBILE_IFNAME);
        reset(mStatsManager);

        // Metered change should update ifaces
        mCellAgent.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        waitForIdle();
        expectNotifyNetworkStatus(onlyCell(), onlyCell(), MOBILE_IFNAME);
        reset(mStatsManager);

        mCellAgent.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        waitForIdle();
        expectNotifyNetworkStatus(onlyCell(), onlyCell(), MOBILE_IFNAME);
        reset(mStatsManager);

        // Temp metered change shouldn't update ifaces
        mCellAgent.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        waitForIdle();
        verify(mStatsManager, never()).notifyNetworkStatus(eq(onlyCell()),
                any(List.class), eq(MOBILE_IFNAME), any(List.class));
        reset(mStatsManager);

        // Congested change shouldn't update ifaces
        mCellAgent.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
        waitForIdle();
        verify(mStatsManager, never()).notifyNetworkStatus(eq(onlyCell()),
                any(List.class), eq(MOBILE_IFNAME), any(List.class));
        reset(mStatsManager);

        // Roaming change should update ifaces
        mCellAgent.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        waitForIdle();
        expectNotifyNetworkStatus(onlyCell(), onlyCell(), MOBILE_IFNAME);
        reset(mStatsManager);

        // Test VPNs.
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(VPN_IFNAME);

        mMockVpn.establishForMyUid(lp);
        assertUidRangesUpdatedForMyUid(true);

        final List<Network> cellAndVpn = List.of(mCellAgent.getNetwork(), mMockVpn.getNetwork());

        // A VPN with default (null) underlying networks sets the underlying network's interfaces...
        expectNotifyNetworkStatus(cellAndVpn, cellAndVpn, MOBILE_IFNAME, Process.myUid(),
                VPN_IFNAME, List.of(MOBILE_IFNAME));

        // ...and updates them as the default network switches.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        mWiFiAgent.sendLinkProperties(wifiLp);
        final Network[] onlyNull = new Network[]{null};
        final List<Network> wifiAndVpn = List.of(mWiFiAgent.getNetwork(), mMockVpn.getNetwork());
        final List<Network> cellWifiAndVpn = List.of(mCellAgent.getNetwork(),
                mWiFiAgent.getNetwork(), mMockVpn.getNetwork());
        final Network[] cellNullAndWifi =
                new Network[] { mCellAgent.getNetwork(), null, mWiFiAgent.getNetwork() };

        waitForIdle();
        assertEquals(wifiLp, mService.getActiveLinkProperties());
        expectNotifyNetworkStatus(cellWifiAndVpn, wifiAndVpn, WIFI_IFNAME, Process.myUid(),
                VPN_IFNAME, List.of(WIFI_IFNAME));
        reset(mStatsManager);

        // A VPN that sets its underlying networks passes the underlying interfaces, and influences
        // the default interface sent to NetworkStatsService by virtue of applying to the system
        // server UID (or, in this test, to the test's UID). This is the reason for sending
        // MOBILE_IFNAME even though the default network is wifi.
        // TODO: fix this to pass in the actual default network interface. Whether or not the VPN
        // applies to the system server UID should not have any bearing on network stats.
        mMockVpn.setUnderlyingNetworks(onlyCell().toArray(new Network[0]));
        waitForIdle();
        expectNotifyNetworkStatus(cellWifiAndVpn, wifiAndVpn, MOBILE_IFNAME, Process.myUid(),
                VPN_IFNAME, List.of(MOBILE_IFNAME));
        reset(mStatsManager);

        mMockVpn.setUnderlyingNetworks(cellAndWifi().toArray(new Network[0]));
        waitForIdle();
        expectNotifyNetworkStatus(cellWifiAndVpn, wifiAndVpn, MOBILE_IFNAME, Process.myUid(),
                VPN_IFNAME,  List.of(MOBILE_IFNAME, WIFI_IFNAME));
        reset(mStatsManager);

        // Null underlying networks are ignored.
        mMockVpn.setUnderlyingNetworks(cellNullAndWifi);
        waitForIdle();
        expectNotifyNetworkStatus(cellWifiAndVpn, wifiAndVpn, MOBILE_IFNAME, Process.myUid(),
                VPN_IFNAME,  List.of(MOBILE_IFNAME, WIFI_IFNAME));
        reset(mStatsManager);

        // If an underlying network disconnects, that interface should no longer be underlying.
        // This doesn't actually work because disconnectAndDestroyNetwork only notifies
        // NetworkStatsService before the underlying network is actually removed. So the underlying
        // network will only be removed if notifyIfacesChangedForNetworkStats is called again. This
        // could result in incorrect data usage measurements if the interface used by the
        // disconnected network is reused by a system component that does not register an agent for
        // it (e.g., tethering).
        mCellAgent.disconnect();
        waitForIdle();
        assertNull(mService.getLinkProperties(mCellAgent.getNetwork()));
        expectNotifyNetworkStatus(cellWifiAndVpn, wifiAndVpn, MOBILE_IFNAME, Process.myUid(),
                VPN_IFNAME, List.of(MOBILE_IFNAME, WIFI_IFNAME));

        // Confirm that we never tell NetworkStatsService that cell is no longer the underlying
        // network for the VPN...
        verify(mStatsManager, never()).notifyNetworkStatus(any(List.class),
                any(List.class), any() /* anyString() doesn't match null */,
                argThat(infos -> infos.get(0).getUnderlyingInterfaces().size() == 1
                        && WIFI_IFNAME.equals(infos.get(0).getUnderlyingInterfaces().get(0))));
        verifyNoMoreInteractions(mStatsManager);
        reset(mStatsManager);

        // ... but if something else happens that causes notifyIfacesChangedForNetworkStats to be
        // called again, it does. For example, connect Ethernet, but with a low score, such that it
        // does not become the default network.
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mEthernetAgent.setScore(
                new NetworkScore.Builder().setLegacyInt(30).setExiting(true).build());
        mEthernetAgent.connect(false);
        waitForIdle();
        verify(mStatsManager).notifyNetworkStatus(any(List.class),
                any(List.class), any() /* anyString() doesn't match null */,
                argThat(vpnInfos -> vpnInfos.get(0).getUnderlyingInterfaces().size() == 1
                        && WIFI_IFNAME.equals(vpnInfos.get(0).getUnderlyingInterfaces().get(0))));
        mEthernetAgent.disconnect();
        waitForIdle();
        reset(mStatsManager);

        // When a VPN declares no underlying networks (i.e., no connectivity), getAllVpnInfo
        // does not return the VPN, so CS does not pass it to NetworkStatsService. This causes
        // NetworkStatsFactory#adjustForTunAnd464Xlat not to attempt any VPN data migration, which
        // is probably a performance improvement (though it's very unlikely that a VPN would declare
        // no underlying networks).
        // Also, for the same reason as above, the active interface passed in is null.
        mMockVpn.setUnderlyingNetworks(new Network[0]);
        waitForIdle();
        expectNotifyNetworkStatus(wifiAndVpn, wifiAndVpn, null);
        reset(mStatsManager);

        // Specifying only a null underlying network is the same as no networks.
        mMockVpn.setUnderlyingNetworks(onlyNull);
        waitForIdle();
        expectNotifyNetworkStatus(wifiAndVpn, wifiAndVpn, null);
        reset(mStatsManager);

        // Specifying networks that are all disconnected is the same as specifying no networks.
        mMockVpn.setUnderlyingNetworks(onlyCell().toArray(new Network[0]));
        waitForIdle();
        expectNotifyNetworkStatus(wifiAndVpn, wifiAndVpn, null);
        reset(mStatsManager);

        // Passing in null again means follow the default network again.
        mMockVpn.setUnderlyingNetworks(null);
        waitForIdle();
        expectNotifyNetworkStatus(wifiAndVpn, wifiAndVpn, WIFI_IFNAME, Process.myUid(), VPN_IFNAME,
                List.of(WIFI_IFNAME));
        reset(mStatsManager);
    }

    @Test
    public void testAdminUidsRedacted() throws Exception {
        final int[] adminUids = new int[] {Process.myUid() + 1};
        final NetworkCapabilities ncTemplate = new NetworkCapabilities();
        ncTemplate.setAdministratorUids(adminUids);
        mCellAgent =
                new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, new LinkProperties(), ncTemplate);
        mCellAgent.connect(false /* validated */);

        // Verify case where caller has permission
        mServiceContext.setPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, PERMISSION_GRANTED);
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);
        callback.expect(AVAILABLE, mCellAgent);
        callback.expectCaps(mCellAgent, c -> Arrays.equals(adminUids, c.getAdministratorUids()));
        mCm.unregisterNetworkCallback(callback);

        // Verify case where caller does NOT have permission
        mServiceContext.setPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, PERMISSION_DENIED);
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);
        callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);
        callback.expect(AVAILABLE, mCellAgent);
        callback.expectCaps(mCellAgent, c -> c.getAdministratorUids().length == 0);
    }

    @Test
    public void testNonVpnUnderlyingNetworks() throws Exception {
        // Ensure wifi and cellular are not torn down.
        for (int transport : new int[]{TRANSPORT_CELLULAR, TRANSPORT_WIFI}) {
            final NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(transport)
                    .removeCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                    .build();
            mCm.requestNetwork(request, new NetworkCallback());
        }

        // Connect a VCN-managed wifi network.
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.removeCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true /* validated */);

        final List<Network> none = List.of();
        expectNotifyNetworkStatus(onlyWifi(), none, null);  // Wifi is not the default network

        // Create a virtual network based on the wifi network.
        final int ownerUid = 10042;
        NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .setOwnerUid(ownerUid)
                .setAdministratorUids(new int[]{ownerUid})
                .build();
        final String vcnIface = "ipsec42";
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(vcnIface);
        final TestNetworkAgentWrapper vcn = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, lp, nc);
        vcn.setUnderlyingNetworks(List.of(mWiFiAgent.getNetwork()));
        vcn.connect(false /* validated */);

        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);
        callback.expectAvailableCallbacksUnvalidated(vcn);

        // The underlying wifi network's capabilities are not propagated to the virtual network,
        // but NetworkStatsService is informed of the underlying interface.
        nc = mCm.getNetworkCapabilities(vcn.getNetwork());
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_METERED));
        final List<Network> onlyVcn = List.of(vcn.getNetwork());
        final List<Network> vcnAndWifi = List.of(vcn.getNetwork(), mWiFiAgent.getNetwork());
        expectNotifyNetworkStatus(vcnAndWifi, onlyVcn, vcnIface, ownerUid, vcnIface,
                List.of(WIFI_IFNAME));

        // Add NOT_METERED to the underlying network, check that it is not propagated.
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        callback.assertNoCallback();
        nc = mCm.getNetworkCapabilities(vcn.getNetwork());
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_METERED));

        // Switch underlying networks.
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.removeCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        mCellAgent.addCapability(NET_CAPABILITY_NOT_ROAMING);
        mCellAgent.connect(false /* validated */);
        vcn.setUnderlyingNetworks(List.of(mCellAgent.getNetwork()));

        // The underlying capability changes do not propagate to the virtual network, but
        // NetworkStatsService is informed of the new underlying interface.
        callback.assertNoCallback();
        nc = mCm.getNetworkCapabilities(vcn.getNetwork());
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_ROAMING));
        final List<Network> vcnWifiAndCell = List.of(vcn.getNetwork(),
                mWiFiAgent.getNetwork(), mCellAgent.getNetwork());
        expectNotifyNetworkStatus(vcnWifiAndCell, onlyVcn, vcnIface, ownerUid, vcnIface,
                List.of(MOBILE_IFNAME));
    }

    @Test
    public void testBasicDnsConfigurationPushed() throws Exception {
        setPrivateDnsSettings(PRIVATE_DNS_MODE_OPPORTUNISTIC, "ignored.example.com");

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        final int netId = mCellAgent.getNetwork().netId;
        waitForIdle();
        if (mService.shouldCreateNetworksImmediately()) {
            verify(mMockDnsResolver, times(1)).createNetworkCache(netId);
        } else {
            verify(mMockDnsResolver, never()).setResolverConfiguration(any());
        }

        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        // Add IPv4 and IPv6 default routes, because DNS-over-TLS code does
        // "is-reachable" testing in order to not program netd with unreachable
        // nameservers that it might try repeated to validate.
        cellLp.addLinkAddress(new LinkAddress("192.0.2.4/24"));
        cellLp.addRoute(new RouteInfo((IpPrefix) null, InetAddress.getByName("192.0.2.4"),
                MOBILE_IFNAME));
        cellLp.addLinkAddress(new LinkAddress("2001:db8:1::1/64"));
        cellLp.addRoute(new RouteInfo((IpPrefix) null, InetAddress.getByName("2001:db8:1::1"),
                MOBILE_IFNAME));
        mCellAgent.sendLinkProperties(cellLp);
        mCellAgent.connect(false);
        waitForIdle();
        if (!mService.shouldCreateNetworksImmediately()) {
            // CS tells dnsresolver about the empty DNS config for this network.
            verify(mMockDnsResolver, times(1)).createNetworkCache(netId);
        }
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(any());

        verifyNoMoreInteractions(mMockDnsResolver);
        reset(mMockDnsResolver);

        cellLp.addDnsServer(InetAddress.getByName("2001:db8::1"));
        mCellAgent.sendLinkProperties(cellLp);
        waitForIdle();
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        ResolverParamsParcel resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(1, resolvrParams.servers.length);
        assertTrue(CollectionUtils.contains(resolvrParams.servers, "2001:db8::1"));
        // Opportunistic mode.
        assertTrue(CollectionUtils.contains(resolvrParams.tlsServers, "2001:db8::1"));
        reset(mMockDnsResolver);

        cellLp.addDnsServer(InetAddress.getByName("192.0.2.1"));
        mCellAgent.sendLinkProperties(cellLp);
        waitForIdle();
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(2, resolvrParams.servers.length);
        assertTrue(new ArraySet<>(resolvrParams.servers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        // Opportunistic mode.
        assertEquals(2, resolvrParams.tlsServers.length);
        assertTrue(new ArraySet<>(resolvrParams.tlsServers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        reset(mMockDnsResolver);

        final String TLS_SPECIFIER = "tls.example.com";
        final String TLS_SERVER6 = "2001:db8:53::53";
        final InetAddress[] TLS_IPS = new InetAddress[]{ InetAddress.getByName(TLS_SERVER6) };
        final String[] TLS_SERVERS = new String[]{ TLS_SERVER6 };
        mCellAgent.mNmCallbacks.notifyPrivateDnsConfigResolved(
                new PrivateDnsConfig(TLS_SPECIFIER, TLS_IPS).toParcel());

        waitForIdle();
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(2, resolvrParams.servers.length);
        assertTrue(new ArraySet<>(resolvrParams.servers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        reset(mMockDnsResolver);
    }

    @Test
    public void testDnsConfigurationTransTypesPushed() throws Exception {
        final NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities().addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        verify(mMockDnsResolver, times(1)).createNetworkCache(eq(mWiFiAgent.getNetwork().netId));
        verify(mMockDnsResolver, times(2)).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        final ResolverParamsParcel resolverParams = mResolverParamsParcelCaptor.getValue();
        assertContainsExactly(resolverParams.transportTypes, TRANSPORT_WIFI);
        reset(mMockDnsResolver);
    }

    @Test
    public void testPrivateDnsNotification() throws Exception {
        NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities().addCapability(NET_CAPABILITY_INTERNET)
                .build();
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);
        // Bring up wifi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        // Private DNS resolution failed, checking if the notification will be shown or not.
        mWiFiAgent.setNetworkInvalid(true /* invalidBecauseOfPrivateDns */);
        mWiFiAgent.mNetworkMonitor.forceReevaluation(Process.myUid());
        waitForIdle();
        // If network validation failed, NetworkMonitor will re-evaluate the network.
        // ConnectivityService should filter the redundant notification. This part is trying to
        // simulate that situation and check if ConnectivityService could filter that case.
        mWiFiAgent.mNetworkMonitor.forceReevaluation(Process.myUid());
        waitForIdle();
        verify(mNotificationManager, timeout(TIMEOUT_MS).times(1)).notify(anyString(),
                eq(NotificationType.PRIVATE_DNS_BROKEN.eventId), any());
        // If private DNS resolution successful, the PRIVATE_DNS_BROKEN notification shouldn't be
        // shown.
        mWiFiAgent.setNetworkValid(true /* privateDnsProbeSent */);
        mWiFiAgent.mNetworkMonitor.forceReevaluation(Process.myUid());
        waitForIdle();
        verify(mNotificationManager, timeout(TIMEOUT_MS).times(1)).cancel(anyString(),
                eq(NotificationType.PRIVATE_DNS_BROKEN.eventId));
        // If private DNS resolution failed again, the PRIVATE_DNS_BROKEN notification should be
        // shown again.
        mWiFiAgent.setNetworkInvalid(true /* invalidBecauseOfPrivateDns */);
        mWiFiAgent.mNetworkMonitor.forceReevaluation(Process.myUid());
        waitForIdle();
        verify(mNotificationManager, timeout(TIMEOUT_MS).times(2)).notify(anyString(),
                eq(NotificationType.PRIVATE_DNS_BROKEN.eventId), any());
    }

    @Test
    public void testPrivateDnsSettingsChange() throws Exception {
        // The default on Android is opportunistic mode ("Automatic").
        setPrivateDnsSettings(PRIVATE_DNS_MODE_OPPORTUNISTIC, "ignored.example.com");

        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        final int netId = mCellAgent.getNetwork().netId;
        waitForIdle();
        if (mService.shouldCreateNetworksImmediately()) {
            verify(mMockDnsResolver, times(1)).createNetworkCache(netId);
        } else {
            verify(mMockDnsResolver, never()).setResolverConfiguration(any());
        }

        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        // Add IPv4 and IPv6 default routes, because DNS-over-TLS code does
        // "is-reachable" testing in order to not program netd with unreachable
        // nameservers that it might try repeated to validate.
        cellLp.addLinkAddress(new LinkAddress("192.0.2.4/24"));
        cellLp.addRoute(new RouteInfo((IpPrefix) null, InetAddress.getByName("192.0.2.4"),
                MOBILE_IFNAME));
        cellLp.addLinkAddress(new LinkAddress("2001:db8:1::1/64"));
        cellLp.addRoute(new RouteInfo((IpPrefix) null, InetAddress.getByName("2001:db8:1::1"),
                MOBILE_IFNAME));
        cellLp.addDnsServer(InetAddress.getByName("2001:db8::1"));
        cellLp.addDnsServer(InetAddress.getByName("192.0.2.1"));

        mCellAgent.sendLinkProperties(cellLp);
        mCellAgent.connect(false);
        waitForIdle();
        if (!mService.shouldCreateNetworksImmediately()) {
            verify(mMockDnsResolver, times(1)).createNetworkCache(netId);
        }
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        ResolverParamsParcel resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(2, resolvrParams.tlsServers.length);
        assertTrue(new ArraySet<>(resolvrParams.tlsServers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        // Opportunistic mode.
        assertEquals(2, resolvrParams.tlsServers.length);
        assertTrue(new ArraySet<>(resolvrParams.tlsServers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        verifyNoMoreInteractions(mMockDnsResolver);
        reset(mMockDnsResolver);
        cellNetworkCallback.expect(AVAILABLE, mCellAgent);
        cellNetworkCallback.expect(NETWORK_CAPS_UPDATED, mCellAgent);
        CallbackEntry.LinkPropertiesChanged cbi = cellNetworkCallback.expect(
                LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertFalse(cbi.getLp().isPrivateDnsActive());
        assertNull(cbi.getLp().getPrivateDnsServerName());

        setPrivateDnsSettings(PRIVATE_DNS_MODE_OFF, "ignored.example.com");
        verify(mMockDnsResolver, times(1)).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(2, resolvrParams.servers.length);
        assertTrue(new ArraySet<>(resolvrParams.servers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        reset(mMockDnsResolver);
        cellNetworkCallback.assertNoCallback();

        setPrivateDnsSettings(PRIVATE_DNS_MODE_OPPORTUNISTIC, "ignored.example.com");
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(2, resolvrParams.servers.length);
        assertTrue(new ArraySet<>(resolvrParams.servers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        assertEquals(2, resolvrParams.tlsServers.length);
        assertTrue(new ArraySet<>(resolvrParams.tlsServers).containsAll(
                asList("2001:db8::1", "192.0.2.1")));
        reset(mMockDnsResolver);
        cellNetworkCallback.assertNoCallback();

        setPrivateDnsSettings(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME, "strict.example.com");
        // Can't test dns configuration for strict mode without properly mocking
        // out the DNS lookups, but can test that LinkProperties is updated.
        cbi = cellNetworkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertTrue(cbi.getLp().isPrivateDnsActive());
        assertEquals("strict.example.com", cbi.getLp().getPrivateDnsServerName());
    }

    private PrivateDnsValidationEventParcel makePrivateDnsValidationEvent(
            final int netId, final String ipAddress, final String hostname, final int validation) {
        final PrivateDnsValidationEventParcel event = new PrivateDnsValidationEventParcel();
        event.netId = netId;
        event.ipAddress = ipAddress;
        event.hostname = hostname;
        event.validation = validation;
        return event;
    }

    @Test
    public void testLinkPropertiesWithPrivateDnsValidationEvents() throws Exception {
        // The default on Android is opportunistic mode ("Automatic").
        setPrivateDnsSettings(PRIVATE_DNS_MODE_OPPORTUNISTIC, "ignored.example.com");

        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        waitForIdle();
        LinkProperties lp = new LinkProperties();
        mCellAgent.sendLinkProperties(lp);
        mCellAgent.connect(false);
        waitForIdle();
        cellNetworkCallback.expect(AVAILABLE, mCellAgent);
        cellNetworkCallback.expect(NETWORK_CAPS_UPDATED, mCellAgent);
        CallbackEntry.LinkPropertiesChanged cbi = cellNetworkCallback.expect(
                LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertFalse(cbi.getLp().isPrivateDnsActive());
        assertNull(cbi.getLp().getPrivateDnsServerName());
        Set<InetAddress> dnsServers = new HashSet<>();
        checkDnsServers(cbi.getLp(), dnsServers);

        // Send a validation event for a server that is not part of the current
        // resolver config. The validation event should be ignored.
        mService.mResolverUnsolEventCallback.onPrivateDnsValidationEvent(
                makePrivateDnsValidationEvent(mCellAgent.getNetwork().netId, "",
                        "145.100.185.18", VALIDATION_RESULT_SUCCESS));
        cellNetworkCallback.assertNoCallback();

        // Add a dns server to the LinkProperties.
        LinkProperties lp2 = new LinkProperties(lp);
        lp2.addDnsServer(InetAddress.getByName("145.100.185.16"));
        mCellAgent.sendLinkProperties(lp2);
        cbi = cellNetworkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertFalse(cbi.getLp().isPrivateDnsActive());
        assertNull(cbi.getLp().getPrivateDnsServerName());
        dnsServers.add(InetAddress.getByName("145.100.185.16"));
        checkDnsServers(cbi.getLp(), dnsServers);

        // Send a validation event containing a hostname that is not part of
        // the current resolver config. The validation event should be ignored.
        mService.mResolverUnsolEventCallback.onPrivateDnsValidationEvent(
                makePrivateDnsValidationEvent(mCellAgent.getNetwork().netId,
                        "145.100.185.16", "hostname", VALIDATION_RESULT_SUCCESS));
        cellNetworkCallback.assertNoCallback();

        // Send a validation event where validation failed.
        mService.mResolverUnsolEventCallback.onPrivateDnsValidationEvent(
                makePrivateDnsValidationEvent(mCellAgent.getNetwork().netId,
                        "145.100.185.16", "", VALIDATION_RESULT_FAILURE));
        cellNetworkCallback.assertNoCallback();

        // Send a validation event where validation succeeded for a server in
        // the current resolver config. A LinkProperties callback with updated
        // private dns fields should be sent.
        mService.mResolverUnsolEventCallback.onPrivateDnsValidationEvent(
                makePrivateDnsValidationEvent(mCellAgent.getNetwork().netId,
                        "145.100.185.16", "", VALIDATION_RESULT_SUCCESS));
        cbi = cellNetworkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertTrue(cbi.getLp().isPrivateDnsActive());
        assertNull(cbi.getLp().getPrivateDnsServerName());
        checkDnsServers(cbi.getLp(), dnsServers);

        // The private dns fields in LinkProperties should be preserved when
        // the network agent sends unrelated changes.
        LinkProperties lp3 = new LinkProperties(lp2);
        lp3.setMtu(1300);
        mCellAgent.sendLinkProperties(lp3);
        cbi = cellNetworkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertTrue(cbi.getLp().isPrivateDnsActive());
        assertNull(cbi.getLp().getPrivateDnsServerName());
        checkDnsServers(cbi.getLp(), dnsServers);
        assertEquals(1300, cbi.getLp().getMtu());

        // Removing the only validated server should affect the private dns
        // fields in LinkProperties.
        LinkProperties lp4 = new LinkProperties(lp3);
        lp4.removeDnsServer(InetAddress.getByName("145.100.185.16"));
        mCellAgent.sendLinkProperties(lp4);
        cbi = cellNetworkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        cellNetworkCallback.assertNoCallback();
        assertFalse(cbi.getLp().isPrivateDnsActive());
        assertNull(cbi.getLp().getPrivateDnsServerName());
        dnsServers.remove(InetAddress.getByName("145.100.185.16"));
        checkDnsServers(cbi.getLp(), dnsServers);
        assertEquals(1300, cbi.getLp().getMtu());
    }

    private void checkDirectlyConnectedRoutes(Object callbackObj,
            Collection<LinkAddress> linkAddresses, Collection<RouteInfo> otherRoutes) {
        assertTrue(callbackObj instanceof LinkProperties);
        LinkProperties lp = (LinkProperties) callbackObj;

        Set<RouteInfo> expectedRoutes = new ArraySet<>();
        expectedRoutes.addAll(otherRoutes);
        for (LinkAddress address : linkAddresses) {
            RouteInfo localRoute = new RouteInfo(address, null, lp.getInterfaceName());
            // Duplicates in linkAddresses are considered failures
            assertTrue(expectedRoutes.add(localRoute));
        }
        List<RouteInfo> observedRoutes = lp.getRoutes();
        assertEquals(expectedRoutes.size(), observedRoutes.size());
        assertTrue(observedRoutes.containsAll(expectedRoutes));
    }

    private static void checkDnsServers(Object callbackObj, Set<InetAddress> dnsServers) {
        assertTrue(callbackObj instanceof LinkProperties);
        LinkProperties lp = (LinkProperties) callbackObj;
        assertEquals(dnsServers.size(), lp.getDnsServers().size());
        assertTrue(lp.getDnsServers().containsAll(dnsServers));
    }

    @Test
    public void testApplyUnderlyingCapabilities() throws Exception {
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mCellAgent.connect(false /* validated */);
        mWiFiAgent.connect(false /* validated */);

        final NetworkCapabilities cellNc = new NetworkCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_CONGESTED)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .setLinkDownstreamBandwidthKbps(10);
        final NetworkCapabilities wifiNc = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .addCapability(NET_CAPABILITY_NOT_ROAMING)
                .addCapability(NET_CAPABILITY_NOT_CONGESTED)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .setLinkUpstreamBandwidthKbps(20);
        mCellAgent.setNetworkCapabilities(cellNc, true /* sendToConnectivityService */);
        mWiFiAgent.setNetworkCapabilities(wifiNc, true /* sendToConnectivityService */);
        waitForIdle();

        final Network mobile = mCellAgent.getNetwork();
        final Network wifi = mWiFiAgent.getNetwork();

        final NetworkCapabilities initialCaps = new NetworkCapabilities();
        initialCaps.addTransportType(TRANSPORT_VPN);
        initialCaps.addCapability(NET_CAPABILITY_INTERNET);
        initialCaps.removeCapability(NET_CAPABILITY_NOT_VPN);
        final ArrayList<Network> emptyUnderlyingNetworks = new ArrayList<Network>();
        final ArrayList<Network> underlyingNetworksContainMobile = new ArrayList<Network>();
        underlyingNetworksContainMobile.add(mobile);
        final ArrayList<Network> underlyingNetworksContainWifi = new ArrayList<Network>();
        underlyingNetworksContainWifi.add(wifi);
        final ArrayList<Network> underlyingNetworksContainMobileAndMobile =
                new ArrayList<Network>();
        underlyingNetworksContainMobileAndMobile.add(mobile);
        underlyingNetworksContainMobileAndMobile.add(wifi);

        final NetworkCapabilities withNoUnderlying = new NetworkCapabilities();
        withNoUnderlying.addCapability(NET_CAPABILITY_INTERNET);
        withNoUnderlying.addCapability(NET_CAPABILITY_NOT_CONGESTED);
        withNoUnderlying.addCapability(NET_CAPABILITY_NOT_ROAMING);
        withNoUnderlying.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
        withNoUnderlying.addTransportType(TRANSPORT_VPN);
        withNoUnderlying.removeCapability(NET_CAPABILITY_NOT_VPN);
        withNoUnderlying.setUnderlyingNetworks(emptyUnderlyingNetworks);

        final NetworkCapabilities withMobileUnderlying = new NetworkCapabilities(withNoUnderlying);
        withMobileUnderlying.addTransportType(TRANSPORT_CELLULAR);
        withMobileUnderlying.removeCapability(NET_CAPABILITY_NOT_ROAMING);
        withMobileUnderlying.removeCapability(NET_CAPABILITY_NOT_SUSPENDED);
        withMobileUnderlying.setLinkDownstreamBandwidthKbps(10);
        withMobileUnderlying.setUnderlyingNetworks(underlyingNetworksContainMobile);

        final NetworkCapabilities withWifiUnderlying = new NetworkCapabilities(withNoUnderlying);
        withWifiUnderlying.addTransportType(TRANSPORT_WIFI);
        withWifiUnderlying.addCapability(NET_CAPABILITY_NOT_METERED);
        withWifiUnderlying.setLinkUpstreamBandwidthKbps(20);
        withWifiUnderlying.setUnderlyingNetworks(underlyingNetworksContainWifi);

        final NetworkCapabilities withWifiAndMobileUnderlying =
                new NetworkCapabilities(withNoUnderlying);
        withWifiAndMobileUnderlying.addTransportType(TRANSPORT_CELLULAR);
        withWifiAndMobileUnderlying.addTransportType(TRANSPORT_WIFI);
        withWifiAndMobileUnderlying.removeCapability(NET_CAPABILITY_NOT_METERED);
        withWifiAndMobileUnderlying.removeCapability(NET_CAPABILITY_NOT_ROAMING);
        withWifiAndMobileUnderlying.setLinkDownstreamBandwidthKbps(10);
        withWifiAndMobileUnderlying.setLinkUpstreamBandwidthKbps(20);
        withWifiAndMobileUnderlying.setUnderlyingNetworks(underlyingNetworksContainMobileAndMobile);

        final NetworkCapabilities initialCapsNotMetered = new NetworkCapabilities(initialCaps);
        initialCapsNotMetered.addCapability(NET_CAPABILITY_NOT_METERED);

        NetworkCapabilities caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{}, initialCapsNotMetered, caps);
        assertEquals(withNoUnderlying, caps);
        assertEquals(0, new ArrayList<>(caps.getUnderlyingNetworks()).size());

        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{null}, initialCapsNotMetered, caps);
        assertEquals(withNoUnderlying, caps);
        assertEquals(0, new ArrayList<>(caps.getUnderlyingNetworks()).size());

        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{mobile}, initialCapsNotMetered, caps);
        assertEquals(withMobileUnderlying, caps);
        assertEquals(1, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(mobile, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));

        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{wifi}, initialCapsNotMetered, caps);
        assertEquals(withWifiUnderlying, caps);
        assertEquals(1, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(wifi, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));

        withWifiUnderlying.removeCapability(NET_CAPABILITY_NOT_METERED);
        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{wifi}, initialCaps, caps);
        assertEquals(withWifiUnderlying, caps);
        assertEquals(1, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(wifi, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));

        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{mobile, wifi}, initialCaps, caps);
        assertEquals(withWifiAndMobileUnderlying, caps);
        assertEquals(2, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(mobile, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));
        assertEquals(wifi, new ArrayList<>(caps.getUnderlyingNetworks()).get(1));

        withWifiUnderlying.addCapability(NET_CAPABILITY_NOT_METERED);
        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{null, mobile, null, wifi},
                initialCapsNotMetered, caps);
        assertEquals(withWifiAndMobileUnderlying, caps);
        assertEquals(2, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(mobile, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));
        assertEquals(wifi, new ArrayList<>(caps.getUnderlyingNetworks()).get(1));

        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(new Network[]{null, mobile, null, wifi},
                initialCapsNotMetered, caps);
        assertEquals(withWifiAndMobileUnderlying, caps);
        assertEquals(2, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(mobile, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));
        assertEquals(wifi, new ArrayList<>(caps.getUnderlyingNetworks()).get(1));

        caps = new NetworkCapabilities(initialCaps);
        mService.applyUnderlyingCapabilities(null, initialCapsNotMetered, caps);
        assertEquals(withWifiUnderlying, caps);
        assertEquals(1, new ArrayList<>(caps.getUnderlyingNetworks()).size());
        assertEquals(wifi, new ArrayList<>(caps.getUnderlyingNetworks()).get(0));
    }

    @Test
    public void testVpnConnectDisconnectUnderlyingNetwork() throws Exception {
        final TestNetworkCallback callback = new TestNetworkCallback();
        final NetworkRequest request = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN).build();

        runAsShell(NETWORK_SETTINGS, () -> {
            mCm.registerNetworkCallback(request, callback);

            // Bring up a VPN that specifies an underlying network that does not exist yet.
            // Note: it's sort of meaningless for a VPN app to declare a network that doesn't exist
            // yet, (and doing so is difficult without using reflection) but it's good to test that
            // the code behaves approximately correctly.
            mMockVpn.establishForMyUid(false, true, false);
            callback.expectAvailableCallbacksUnvalidated(mMockVpn);
            assertUidRangesUpdatedForMyUid(true);
            final Network wifiNetwork = new Network(mNetIdManager.peekNextNetId());
            mMockVpn.setUnderlyingNetworks(new Network[]{wifiNetwork});
            // onCapabilitiesChanged() should be called because
            // NetworkCapabilities#mUnderlyingNetworks is updated.
            final NetworkCapabilities vpnNc1 = callback.expectCaps(mMockVpn);
            // Since the wifi network hasn't brought up,
            // ConnectivityService#applyUnderlyingCapabilities cannot find it. Update
            // NetworkCapabilities#mUnderlyingNetworks to an empty array, and it will be updated to
            // the correct underlying networks once the wifi network brings up. But this case
            // shouldn't happen in reality since no one could get the network which hasn't brought
            // up. For the empty array of underlying networks, it should be happened for 2 cases,
            // the first one is that the VPN app declares an empty array for its underlying
            // networks, the second one is that the underlying networks are torn down.
            //
            // It shouldn't be null since the null value means the underlying networks of this
            // network should follow the default network.
            final ArrayList<Network> underlyingNetwork = new ArrayList<>();
            assertEquals(underlyingNetwork, vpnNc1.getUnderlyingNetworks());
            // Since the wifi network isn't exist, applyUnderlyingCapabilities()
            assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                    .hasTransport(TRANSPORT_VPN));
            assertFalse(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                    .hasTransport(TRANSPORT_WIFI));

            // Make that underlying network connect, and expect to see its capabilities immediately
            // reflected in the VPN's capabilities.
            mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
            assertEquals(wifiNetwork, mWiFiAgent.getNetwork());
            mWiFiAgent.connect(false);
            // TODO: the callback for the VPN happens before any callbacks are called for the wifi
            // network that has just connected. There appear to be two issues here:
            // 1. The VPN code will accept an underlying network as soon as getNetworkCapabilities()
            //    for it returns non-null (which happens very early, during
            //    handleRegisterNetworkAgent).
            //    This is not correct because that that point the network is not connected and
            //    cannot pass any traffic.
            // 2. When a network connects, updateNetworkInfo propagates underlying network
            //    capabilities before rematching networks.
            // Given that this scenario can't really happen, this is probably fine for now.
            final NetworkCapabilities vpnNc2 = callback.expectCaps(mMockVpn);
            // The wifi network is brought up, NetworkCapabilities#mUnderlyingNetworks is updated to
            // it.
            underlyingNetwork.add(wifiNetwork);
            assertEquals(underlyingNetwork, vpnNc2.getUnderlyingNetworks());
            callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
            assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                    .hasTransport(TRANSPORT_VPN));
            assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                    .hasTransport(TRANSPORT_WIFI));

            // Disconnect the network, and expect to see the VPN capabilities change accordingly.
            mWiFiAgent.disconnect();
            callback.expect(LOST, mWiFiAgent);
            callback.expectCaps(mMockVpn, c -> c.getTransportTypes().length == 1
                            && c.hasTransport(TRANSPORT_VPN));

            mMockVpn.disconnect();
            mCm.unregisterNetworkCallback(callback);
        });
    }

    private void assertGetNetworkInfoOfGetActiveNetworkIsConnected(boolean expectedConnectivity) {
        // What Chromium used to do before https://chromium-review.googlesource.com/2605304
        assertEquals("Unexpected result for getActiveNetworkInfo(getActiveNetwork())",
                expectedConnectivity, mCm.getNetworkInfo(mCm.getActiveNetwork()).isConnected());
    }

    @Test
    public void testVpnUnderlyingNetworkSuspended() throws Exception {
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);

        // Connect a VPN.
        mMockVpn.establishForMyUid(false /* validated */, true /* hasInternet */,
                false /* privateDnsProbeSent */);
        callback.expectAvailableCallbacksUnvalidated(mMockVpn);

        // Connect cellular data.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false /* validated */);
        callback.expectCaps(mMockVpn, c -> c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_CELLULAR));
        callback.assertNoCallback();

        assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                .hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertGetNetworkInfoOfGetActiveNetworkIsConnected(true);

        // Suspend the cellular network and expect the VPN to be suspended.
        mCellAgent.suspend();
        callback.expectCaps(mMockVpn, c -> !c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_CELLULAR));
        callback.expect(SUSPENDED, mMockVpn);
        callback.assertNoCallback();

        assertFalse(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                .hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertNetworkInfo(TYPE_MOBILE, DetailedState.SUSPENDED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.SUSPENDED);
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.SUSPENDED);
        // VPN's main underlying network is suspended, so no connectivity.
        assertGetNetworkInfoOfGetActiveNetworkIsConnected(false);

        // Switch to another network. The VPN should no longer be suspended.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false /* validated */);
        callback.expectCaps(mMockVpn, c -> c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_WIFI));
        callback.expect(RESUMED, mMockVpn);
        callback.assertNoCallback();

        assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                .hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertGetNetworkInfoOfGetActiveNetworkIsConnected(true);

        // Unsuspend cellular and then switch back to it. The VPN remains not suspended.
        mCellAgent.resume();
        callback.assertNoCallback();
        mWiFiAgent.disconnect();
        callback.expectCaps(mMockVpn, c -> c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_CELLULAR));
        // Spurious double callback?
        callback.expectCaps(mMockVpn, c -> c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_CELLULAR));
        callback.assertNoCallback();

        assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                .hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertGetNetworkInfoOfGetActiveNetworkIsConnected(true);

        // Suspend cellular and expect no connectivity.
        mCellAgent.suspend();
        callback.expectCaps(mMockVpn, c -> !c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_CELLULAR));
        callback.expect(SUSPENDED, mMockVpn);
        callback.assertNoCallback();

        assertFalse(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                .hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertNetworkInfo(TYPE_MOBILE, DetailedState.SUSPENDED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.SUSPENDED);
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.SUSPENDED);
        assertGetNetworkInfoOfGetActiveNetworkIsConnected(false);

        // Resume cellular and expect that connectivity comes back.
        mCellAgent.resume();
        callback.expectCaps(mMockVpn, c -> c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED)
                && c.hasTransport(TRANSPORT_CELLULAR));
        callback.expect(RESUMED, mMockVpn);
        callback.assertNoCallback();

        assertTrue(mCm.getNetworkCapabilities(mMockVpn.getNetwork())
                .hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertGetNetworkInfoOfGetActiveNetworkIsConnected(true);
    }

    @Test
    public void testVpnNetworkActive() throws Exception {
        final int uid = Process.myUid();

        final TestNetworkCallback genericNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback genericNotVpnNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback wifiNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback vpnNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        final TestNetworkCallback systemDefaultCallback = new TestNetworkCallback();
        final NetworkRequest genericNotVpnRequest = new NetworkRequest.Builder().build();
        final NetworkRequest genericRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN).build();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        final NetworkRequest vpnNetworkRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .addTransportType(TRANSPORT_VPN).build();
        mCm.registerNetworkCallback(genericRequest, genericNetworkCallback);
        mCm.registerNetworkCallback(genericNotVpnRequest, genericNotVpnNetworkCallback);
        mCm.registerNetworkCallback(wifiRequest, wifiNetworkCallback);
        mCm.registerNetworkCallback(vpnNetworkRequest, vpnNetworkCallback);
        mCm.registerDefaultNetworkCallback(defaultCallback);
        mCm.registerSystemDefaultNetworkCallback(systemDefaultCallback,
                new Handler(ConnectivityThread.getInstanceLooper()));
        defaultCallback.assertNoCallback();

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false);

        genericNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        genericNotVpnNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        wifiNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        defaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        systemDefaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        vpnNetworkCallback.assertNoCallback();
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        final Set<UidRange> ranges = uidRangesForUids(uid);
        mMockVpn.registerAgent(ranges);
        mMockVpn.setUnderlyingNetworks(new Network[0]);

        // VPN networks do not satisfy the default request and are automatically validated
        // by NetworkMonitor
        assertFalse(NetworkMonitorUtils.isValidationRequired(
                false /* isDunValidationRequired */,
                NetworkAgentConfigShimImpl.newInstance(mMockVpn.getNetworkAgentConfig())
                        .isVpnValidationRequired(),
                mMockVpn.getAgent().getNetworkCapabilities()));
        mMockVpn.getAgent().setNetworkValid(false /* privateDnsProbeSent */);

        mMockVpn.connect(false);

        genericNetworkCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        genericNotVpnNetworkCallback.assertNoCallback();
        wifiNetworkCallback.assertNoCallback();
        vpnNetworkCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        defaultCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        systemDefaultCallback.assertNoCallback();
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());
        assertEquals(mWiFiAgent.getNetwork(), systemDefaultCallback.getLastAvailableNetwork());

        ranges.clear();
        mMockVpn.setUids(ranges);

        genericNetworkCallback.expect(LOST, mMockVpn);
        genericNotVpnNetworkCallback.assertNoCallback();
        wifiNetworkCallback.assertNoCallback();
        vpnNetworkCallback.expect(LOST, mMockVpn);

        // TODO : The default network callback should actually get a LOST call here (also see the
        // comment below for AVAILABLE). This is because ConnectivityService does not look at UID
        // ranges at all when determining whether a network should be rematched. In practice, VPNs
        // can't currently update their UIDs without disconnecting, so this does not matter too
        // much, but that is the reason the test here has to check for an update to the
        // capabilities instead of the expected LOST then AVAILABLE.
        defaultCallback.expectCaps(mMockVpn);
        systemDefaultCallback.assertNoCallback();

        ranges.add(new UidRange(uid, uid));
        mMockVpn.setUids(ranges);

        genericNetworkCallback.expectAvailableCallbacksValidated(mMockVpn);
        genericNotVpnNetworkCallback.assertNoCallback();
        wifiNetworkCallback.assertNoCallback();
        vpnNetworkCallback.expectAvailableCallbacksValidated(mMockVpn);
        // TODO : Here like above, AVAILABLE would be correct, but because this can't actually
        // happen outside of the test, ConnectivityService does not rematch callbacks.
        defaultCallback.expectCaps(mMockVpn);
        systemDefaultCallback.assertNoCallback();

        mWiFiAgent.disconnect();

        genericNetworkCallback.expect(LOST, mWiFiAgent);
        genericNotVpnNetworkCallback.expect(LOST, mWiFiAgent);
        wifiNetworkCallback.expect(LOST, mWiFiAgent);
        vpnNetworkCallback.assertNoCallback();
        defaultCallback.assertNoCallback();
        systemDefaultCallback.expect(LOST, mWiFiAgent);

        mMockVpn.disconnect();

        genericNetworkCallback.expect(LOST, mMockVpn);
        genericNotVpnNetworkCallback.assertNoCallback();
        wifiNetworkCallback.assertNoCallback();
        vpnNetworkCallback.expect(LOST, mMockVpn);
        defaultCallback.expect(LOST, mMockVpn);
        systemDefaultCallback.assertNoCallback();
        assertEquals(null, mCm.getActiveNetwork());

        mCm.unregisterNetworkCallback(genericNetworkCallback);
        mCm.unregisterNetworkCallback(wifiNetworkCallback);
        mCm.unregisterNetworkCallback(vpnNetworkCallback);
        mCm.unregisterNetworkCallback(defaultCallback);
        mCm.unregisterNetworkCallback(systemDefaultCallback);
    }

    @Test
    public void testVpnWithoutInternet() throws Exception {
        final int uid = Process.myUid();

        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);

        defaultCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mMockVpn.establishForMyUid(true /* validated */, false /* hasInternet */,
                false /* privateDnsProbeSent */);
        assertUidRangesUpdatedForMyUid(true);

        defaultCallback.assertNoCallback();
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mMockVpn.disconnect();
        defaultCallback.assertNoCallback();

        mCm.unregisterNetworkCallback(defaultCallback);
    }

    @Test
    public void testVpnWithInternet() throws Exception {
        final int uid = Process.myUid();

        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);

        defaultCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mMockVpn.establishForMyUid(true /* validated */, true /* hasInternet */,
                false /* privateDnsProbeSent */);
        assertUidRangesUpdatedForMyUid(true);

        defaultCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        assertEquals(defaultCallback.getLastAvailableNetwork(), mCm.getActiveNetwork());

        mMockVpn.disconnect();
        defaultCallback.expect(LOST, mMockVpn);
        defaultCallback.expectAvailableCallbacksValidated(mWiFiAgent);

        mCm.unregisterNetworkCallback(defaultCallback);
    }

    @Test
    public void testVpnUnvalidated() throws Exception {
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);

        // Bring up Ethernet.
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mEthernetAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mEthernetAgent);
        callback.assertNoCallback();

        // Bring up a VPN that has the INTERNET capability, initially unvalidated.
        mMockVpn.establishForMyUid(false /* validated */, true /* hasInternet */,
                false /* privateDnsProbeSent */);
        assertUidRangesUpdatedForMyUid(true);

        // Even though the VPN is unvalidated, it becomes the default network for our app.
        callback.expectAvailableCallbacksUnvalidated(mMockVpn);
        callback.assertNoCallback();

        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetwork());

        NetworkCapabilities nc = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        assertFalse(nc.hasCapability(NET_CAPABILITY_VALIDATED));
        assertTrue(nc.hasCapability(NET_CAPABILITY_INTERNET));

        assertFalse(NetworkMonitorUtils.isValidationRequired(
                false /* isDunValidationRequired */,
                NetworkAgentConfigShimImpl.newInstance(mMockVpn.getNetworkAgentConfig())
                        .isVpnValidationRequired(),
                mMockVpn.getAgent().getNetworkCapabilities()));
        assertTrue(NetworkMonitorUtils.isPrivateDnsValidationRequired(
                mMockVpn.getAgent().getNetworkCapabilities()));

        // Pretend that the VPN network validates.
        mMockVpn.getAgent().setNetworkValid(false /* privateDnsProbeSent */);
        mMockVpn.getAgent().mNetworkMonitor.forceReevaluation(Process.myUid());
        // Expect to see the validated capability, but no other changes, because the VPN is already
        // the default network for the app.
        callback.expectCaps(mMockVpn, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        callback.assertNoCallback();

        mMockVpn.disconnect();
        callback.expect(LOST, mMockVpn);
        callback.expectAvailableCallbacksValidated(mEthernetAgent);
    }

    @Test
    public void testVpnStartsWithUnderlyingCaps() throws Exception {
        final int uid = Process.myUid();

        final TestNetworkCallback vpnNetworkCallback = new TestNetworkCallback();
        final NetworkRequest vpnNetworkRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .addTransportType(TRANSPORT_VPN)
                .build();
        mCm.registerNetworkCallback(vpnNetworkRequest, vpnNetworkCallback);
        vpnNetworkCallback.assertNoCallback();

        // Connect cell. It will become the default network, and in the absence of setting
        // underlying networks explicitly it will become the sole underlying network for the vpn.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
        mCellAgent.connect(true);

        mMockVpn.establishForMyUid(true /* validated */, false /* hasInternet */,
                false /* privateDnsProbeSent */);
        assertUidRangesUpdatedForMyUid(true);

        vpnNetworkCallback.expectAvailableCallbacks(mMockVpn.getNetwork(),
                false /* suspended */, false /* validated */, false /* blocked */, TIMEOUT_MS);
        vpnNetworkCallback.expectCaps(mMockVpn.getNetwork(), TIMEOUT_MS,
                c -> c.hasCapability(NET_CAPABILITY_VALIDATED));

        final NetworkCapabilities nc = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        assertTrue(nc.hasTransport(TRANSPORT_VPN));
        assertTrue(nc.hasTransport(TRANSPORT_CELLULAR));
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        assertTrue(nc.hasCapability(NET_CAPABILITY_VALIDATED));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_METERED));
        assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));

        assertVpnTransportInfo(nc, VpnManager.TYPE_VPN_SERVICE);
    }

    private void assertDefaultNetworkCapabilities(int userId, NetworkAgentWrapper... networks) {
        final NetworkCapabilities[] defaultCaps = mService.getDefaultNetworkCapabilitiesForUser(
                userId, "com.android.calling.package", "com.test");
        final String defaultCapsString = Arrays.toString(defaultCaps);
        assertEquals(defaultCapsString, defaultCaps.length, networks.length);
        final Set<NetworkCapabilities> defaultCapsSet = new ArraySet<>(defaultCaps);
        for (NetworkAgentWrapper network : networks) {
            final NetworkCapabilities nc = mCm.getNetworkCapabilities(network.getNetwork());
            final String msg = "Did not find " + nc + " in " + Arrays.toString(defaultCaps);
            assertTrue(msg, defaultCapsSet.contains(nc));
        }
    }

    @Test
    public void testVpnSetUnderlyingNetworks() throws Exception {
        final TestNetworkCallback vpnNetworkCallback = new TestNetworkCallback();
        final NetworkRequest vpnNetworkRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .addTransportType(TRANSPORT_VPN)
                .build();
        NetworkCapabilities nc;
        mCm.registerNetworkCallback(vpnNetworkRequest, vpnNetworkCallback);
        vpnNetworkCallback.assertNoCallback();

        // Lingering timer is short and cell might be disconnected if the device is particularly
        // slow running the test, unless it's requested. Make sure the networks the test needs
        // are all requested.
        final NetworkCallback cellCallback = new NetworkCallback() {};
        final NetworkCallback wifiCallback = new NetworkCallback() {};
        mCm.requestNetwork(
                new NetworkRequest.Builder().addTransportType(TRANSPORT_CELLULAR).build(),
                cellCallback);
        mCm.requestNetwork(
                new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build(),
                wifiCallback);

        mMockVpn.establishForMyUid(true /* validated */, false /* hasInternet */,
                false /* privateDnsProbeSent */);
        assertUidRangesUpdatedForMyUid(true);

        vpnNetworkCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        nc = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        assertTrue(nc.hasTransport(TRANSPORT_VPN));
        assertFalse(nc.hasTransport(TRANSPORT_CELLULAR));
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        // For safety reasons a VPN without underlying networks is considered metered.
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_METERED));
        // A VPN without underlying networks is not suspended.
        assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertVpnTransportInfo(nc, VpnManager.TYPE_VPN_SERVICE);

        final int userId = UserHandle.getUserId(Process.myUid());
        assertDefaultNetworkCapabilities(userId /* no networks */);

        // Connect cell and use it as an underlying network.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
        mCellAgent.connect(true);

        mMockVpn.setUnderlyingNetworks(new Network[] { mCellAgent.getNetwork() });

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertDefaultNetworkCapabilities(userId, mCellAgent);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
        mWiFiAgent.connect(true);

        mMockVpn.setUnderlyingNetworks(
                new Network[] { mCellAgent.getNetwork(), mWiFiAgent.getNetwork() });

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertDefaultNetworkCapabilities(userId, mCellAgent, mWiFiAgent);

        // Don't disconnect, but note the VPN is not using wifi any more.
        mMockVpn.setUnderlyingNetworks(new Network[] { mCellAgent.getNetwork() });

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        // The return value of getDefaultNetworkCapabilitiesForUser always includes the default
        // network (wifi) as well as the underlying networks (cell).
        assertDefaultNetworkCapabilities(userId, mCellAgent, mWiFiAgent);

        // Remove NOT_SUSPENDED from the only network and observe VPN is now suspended.
        mCellAgent.removeCapability(NET_CAPABILITY_NOT_SUSPENDED);
        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && !c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        vpnNetworkCallback.expect(SUSPENDED, mMockVpn);

        // Add NOT_SUSPENDED again and observe VPN is no longer suspended.
        mCellAgent.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        vpnNetworkCallback.expect(RESUMED, mMockVpn);

        // Use Wifi but not cell. Note the VPN is now unmetered and not suspended.
        mMockVpn.setUnderlyingNetworks(new Network[] { mWiFiAgent.getNetwork() });

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && !c.hasTransport(TRANSPORT_CELLULAR)
                        && c.hasTransport(TRANSPORT_WIFI)
                        && c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertDefaultNetworkCapabilities(userId, mWiFiAgent);

        // Use both again.
        mMockVpn.setUnderlyingNetworks(
                new Network[] { mCellAgent.getNetwork(), mWiFiAgent.getNetwork() });

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        assertDefaultNetworkCapabilities(userId, mCellAgent, mWiFiAgent);

        // Cell is suspended again. As WiFi is not, this should not cause a callback.
        mCellAgent.removeCapability(NET_CAPABILITY_NOT_SUSPENDED);
        vpnNetworkCallback.assertNoCallback();

        // Stop using WiFi. The VPN is suspended again.
        mMockVpn.setUnderlyingNetworks(new Network[] { mCellAgent.getNetwork() });
        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && !c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        vpnNetworkCallback.expect(SUSPENDED, mMockVpn);
        assertDefaultNetworkCapabilities(userId, mCellAgent, mWiFiAgent);

        // Use both again.
        mMockVpn.setUnderlyingNetworks(
                new Network[] { mCellAgent.getNetwork(), mWiFiAgent.getNetwork() });

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED)
                        && c.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        vpnNetworkCallback.expect(RESUMED, mMockVpn);
        assertDefaultNetworkCapabilities(userId, mCellAgent, mWiFiAgent);

        // Disconnect cell. Receive update without even removing the dead network from the
        // underlying networks – it's dead anyway. Not metered any more.
        mCellAgent.disconnect();
        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && !c.hasTransport(TRANSPORT_CELLULAR)
                        && c.hasTransport(TRANSPORT_WIFI)
                        && c.hasCapability(NET_CAPABILITY_NOT_METERED));
        assertDefaultNetworkCapabilities(userId, mWiFiAgent);

        // Disconnect wifi too. No underlying networks means this is now metered.
        mWiFiAgent.disconnect();
        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && !c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED));
        // When a network disconnects, the callbacks are fired before all state is updated, so for a
        // short time, synchronous calls will behave as if the network is still connected. Wait for
        // things to settle.
        waitForIdle();
        assertDefaultNetworkCapabilities(userId /* no networks */);

        mMockVpn.disconnect();
        mCm.unregisterNetworkCallback(cellCallback);
        mCm.unregisterNetworkCallback(wifiCallback);
    }

    @Test
    public void testNullUnderlyingNetworks() throws Exception {
        final int uid = Process.myUid();

        final TestNetworkCallback vpnNetworkCallback = new TestNetworkCallback();
        final NetworkRequest vpnNetworkRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .addTransportType(TRANSPORT_VPN)
                .build();
        NetworkCapabilities nc;
        mCm.registerNetworkCallback(vpnNetworkRequest, vpnNetworkCallback);
        vpnNetworkCallback.assertNoCallback();

        mMockVpn.establishForMyUid(true /* validated */, false /* hasInternet */,
                false /* privateDnsProbeSent */);
        assertUidRangesUpdatedForMyUid(true);

        vpnNetworkCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        nc = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        assertTrue(nc.hasTransport(TRANSPORT_VPN));
        assertFalse(nc.hasTransport(TRANSPORT_CELLULAR));
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        // By default, VPN is set to track default network (i.e. its underlying networks is null).
        // In case of no default network, VPN is considered metered.
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_METERED));
        assertVpnTransportInfo(nc, VpnManager.TYPE_VPN_SERVICE);

        // Connect to Cell; Cell is the default network.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED));

        // Connect to WiFi; WiFi is the new default.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true);

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && !c.hasTransport(TRANSPORT_CELLULAR)
                        && c.hasTransport(TRANSPORT_WIFI)
                        && c.hasCapability(NET_CAPABILITY_NOT_METERED));

        // Disconnect Cell. The default network did not change, so there shouldn't be any changes in
        // the capabilities.
        mCellAgent.disconnect();

        // Disconnect wifi too. Now we have no default network.
        mWiFiAgent.disconnect();

        vpnNetworkCallback.expectCaps(mMockVpn,
                c -> c.hasTransport(TRANSPORT_VPN)
                        && !c.hasTransport(TRANSPORT_CELLULAR)
                        && !c.hasTransport(TRANSPORT_WIFI)
                        && !c.hasCapability(NET_CAPABILITY_NOT_METERED));

        mMockVpn.disconnect();
    }

    @Test
    public void testRestrictedProfileAffectsVpnUidRanges() throws Exception {
        // NETWORK_SETTINGS is necessary to see the UID ranges in NetworkCapabilities.
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        final NetworkRequest request = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        // File a VPN request to prevent VPN network being lingered.
        final NetworkRequest vpnRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_VPN)
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .build();
        final TestNetworkCallback vpnCallback = new TestNetworkCallback();
        mCm.requestNetwork(vpnRequest, vpnCallback);

        // Bring up a VPN
        mMockVpn.establishForMyUid();
        assertUidRangesUpdatedForMyUid(true);
        callback.expectAvailableThenValidatedCallbacks(mMockVpn);
        callback.assertNoCallback();

        final int uid = Process.myUid();
        NetworkCapabilities nc = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        assertNotNull("nc=" + nc, nc.getUids());
        assertEquals(nc.getUids(), UidRange.toIntRanges(uidRangesForUids(uid)));
        assertVpnTransportInfo(nc, VpnManager.TYPE_VPN_SERVICE);

        // Set an underlying network and expect to see the VPN transports change.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        callback.expectCaps(mMockVpn, c -> c.hasTransport(TRANSPORT_VPN)
                        && c.hasTransport(TRANSPORT_WIFI));
        callback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));

        // New user added, this updates the Vpn uids, coverage in VpnTest.
        // This is equivalent to `mMockVpn.onUserAdded(RESTRICTED_USER);`
        final Set<UidRange> ranges = uidRangesForUids(uid);
        ranges.add(RESTRICTED_USER_UIDRANGE);
        mMockVpn.setUids(ranges);

        // Expect that the VPN UID ranges contain both |uid| and the UID range for the newly-added
        // restricted user.
        final UidRange rRange = UidRange.createForUser(UserHandle.of(RESTRICTED_USER));
        final Range<Integer> restrictUidRange = new Range<>(rRange.start, rRange.stop);
        final Range<Integer> singleUidRange = new Range<>(uid, uid);
        callback.expectCaps(mMockVpn, c ->
                c.getUids().size() == 2
                && c.getUids().contains(singleUidRange)
                && c.getUids().contains(restrictUidRange)
                && c.hasTransport(TRANSPORT_VPN)
                && c.hasTransport(TRANSPORT_WIFI));

        // Change the VPN's capabilities somehow (specifically, disconnect wifi).
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        callback.expectCaps(mMockVpn, c ->
                c.getUids().size() == 2
                && c.getUids().contains(singleUidRange)
                && c.getUids().contains(restrictUidRange)
                && c.hasTransport(TRANSPORT_VPN)
                && !c.hasTransport(TRANSPORT_WIFI));

        // User removed and expect to lose the UID range for the restricted user.
        // This updates the Vpn uids, coverage in VpnTest.
        // This is equivalent to `mMockVpn.onUserRemoved(RESTRICTED_USER);`
        mMockVpn.setUids(uidRangesForUids(uid));

        // Expect that the VPN gains the UID range for the restricted user, and that the capability
        // change made just before that (i.e., loss of TRANSPORT_WIFI) is preserved.
        callback.expectCaps(mMockVpn, c ->
                c.getUids().size() == 1
                && c.getUids().contains(singleUidRange)
                && c.hasTransport(TRANSPORT_VPN)
                && !c.hasTransport(TRANSPORT_WIFI));

        mCm.unregisterNetworkCallback(callback);
        mCm.unregisterNetworkCallback(vpnCallback);
    }

    @Test
    public void testLockdownVpnWithRestrictedProfiles() throws Exception {
        // For ConnectivityService#setAlwaysOnVpnPackage.
        mServiceContext.setPermission(
                Manifest.permission.CONTROL_ALWAYS_ON_VPN, PERMISSION_GRANTED);
        // For call Vpn#setAlwaysOnPackage.
        mServiceContext.setPermission(
                Manifest.permission.CONTROL_VPN, PERMISSION_GRANTED);
        // Necessary to see the UID ranges in NetworkCapabilities.
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        final NetworkRequest request = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        final int uid = Process.myUid();

        // Connect wifi and check that UIDs in the main and restricted profiles have network access.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true /* validated */);
        final int restrictedUid = UserHandle.getUid(RESTRICTED_USER, 42 /* appId */);
        assertNotNull(mCm.getActiveNetworkForUid(uid));
        assertNotNull(mCm.getActiveNetworkForUid(restrictedUid));

        // Enable always-on VPN lockdown. The main user loses network access because no VPN is up.
        // Coverage in VpnTest.
        final List<Integer> excludedUids = new ArrayList<>();
        excludedUids.add(VPN_UID);
        if (mDeps.isAtLeastT()) {
            // On T onwards, the corresponding SDK sandbox UID should also be excluded
            excludedUids.add(toSdkSandboxUid(VPN_UID));
        }
        final List<Range<Integer>> primaryRanges = intRangesPrimaryExcludingUids(excludedUids);
        mCm.setRequireVpnForUids(true, primaryRanges);

        waitForIdle();
        assertNull(mCm.getActiveNetworkForUid(uid));
        // This is arguably overspecified: a UID that is not running doesn't have an active network.
        // But it's useful to check that non-default users do not lose network access, and to prove
        // that the loss of connectivity below is indeed due to the restricted profile coming up.
        assertNotNull(mCm.getActiveNetworkForUid(restrictedUid));

        // Start the restricted profile, and check that the UID within it loses network access.
        // TODO: check that VPN app within restricted profile still has access, etc.
        // Add a restricted user.
        // This is equivalent to `mMockVpn.onUserAdded(RESTRICTED_USER);`, coverage in VpnTest.
        final List<Range<Integer>> restrictedRanges =
                intRangesExcludingUids(RESTRICTED_USER, excludedUids);
        mCm.setRequireVpnForUids(true, restrictedRanges);
        waitForIdle();

        assertNull(mCm.getActiveNetworkForUid(uid));
        assertNull(mCm.getActiveNetworkForUid(restrictedUid));

        // Stop the restricted profile, and check that the UID within it has network access again.
        // Remove the restricted user.
        // This is equivalent to `mMockVpn.onUserRemoved(RESTRICTED_USER);`, coverage in VpnTest.
        mCm.setRequireVpnForUids(false, restrictedRanges);
        waitForIdle();

        assertNull(mCm.getActiveNetworkForUid(uid));
        assertNotNull(mCm.getActiveNetworkForUid(restrictedUid));

        mCm.setRequireVpnForUids(false, primaryRanges);

        waitForIdle();
    }

    @Test
    public void testIsActiveNetworkMeteredOverWifi() throws Exception {
        // Returns true by default when no network is available.
        assertTrue(mCm.isActiveNetworkMetered());
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true);
        waitForIdle();

        assertFalse(mCm.isActiveNetworkMetered());
    }

    @Test
    public void testIsActiveNetworkMeteredOverCell() throws Exception {
        // Returns true by default when no network is available.
        assertTrue(mCm.isActiveNetworkMetered());
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.removeCapability(NET_CAPABILITY_NOT_METERED);
        mCellAgent.connect(true);
        waitForIdle();

        assertTrue(mCm.isActiveNetworkMetered());
    }

    @Test
    public void testIsActiveNetworkMeteredOverVpnTrackingPlatformDefault() throws Exception {
        // Returns true by default when no network is available.
        assertTrue(mCm.isActiveNetworkMetered());
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.removeCapability(NET_CAPABILITY_NOT_METERED);
        mCellAgent.connect(true);
        waitForIdle();
        assertTrue(mCm.isActiveNetworkMetered());

        // Connect VPN network. By default it is using current default network (Cell).
        mMockVpn.establishForMyUid();
        assertUidRangesUpdatedForMyUid(true);

        // Ensure VPN is now the active network.
        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetwork());

        // Expect VPN to be metered.
        assertTrue(mCm.isActiveNetworkMetered());

        // Connect WiFi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true);
        waitForIdle();
        // VPN should still be the active network.
        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetwork());

        // Expect VPN to be unmetered as it should now be using WiFi (new default).
        assertFalse(mCm.isActiveNetworkMetered());

        // Disconnecting Cell should not affect VPN's meteredness.
        mCellAgent.disconnect();
        waitForIdle();

        assertFalse(mCm.isActiveNetworkMetered());

        // Disconnect WiFi; Now there is no platform default network.
        mWiFiAgent.disconnect();
        waitForIdle();

        // VPN without any underlying networks is treated as metered.
        assertTrue(mCm.isActiveNetworkMetered());

        mMockVpn.disconnect();
    }

    @Test
    public void testIsActiveNetworkMeteredOverVpnSpecifyingUnderlyingNetworks() throws Exception {
        // Returns true by default when no network is available.
        assertTrue(mCm.isActiveNetworkMetered());
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.removeCapability(NET_CAPABILITY_NOT_METERED);
        mCellAgent.connect(true);
        waitForIdle();
        assertTrue(mCm.isActiveNetworkMetered());

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true);
        waitForIdle();
        assertFalse(mCm.isActiveNetworkMetered());

        // Connect VPN network.
        mMockVpn.establishForMyUid();
        assertUidRangesUpdatedForMyUid(true);

        // Ensure VPN is now the active network.
        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetwork());
        // VPN is using Cell
        mMockVpn.setUnderlyingNetworks(new Network[] { mCellAgent.getNetwork() });
        waitForIdle();

        // Expect VPN to be metered.
        assertTrue(mCm.isActiveNetworkMetered());

        // VPN is now using WiFi
        mMockVpn.setUnderlyingNetworks(new Network[] { mWiFiAgent.getNetwork() });
        waitForIdle();

        // Expect VPN to be unmetered
        assertFalse(mCm.isActiveNetworkMetered());

        // VPN is using Cell | WiFi.
        mMockVpn.setUnderlyingNetworks(
                new Network[] { mCellAgent.getNetwork(), mWiFiAgent.getNetwork() });
        waitForIdle();

        // Expect VPN to be metered.
        assertTrue(mCm.isActiveNetworkMetered());

        // VPN is using WiFi | Cell.
        mMockVpn.setUnderlyingNetworks(
                new Network[] { mWiFiAgent.getNetwork(), mCellAgent.getNetwork() });
        waitForIdle();

        // Order should not matter and VPN should still be metered.
        assertTrue(mCm.isActiveNetworkMetered());

        // VPN is not using any underlying networks.
        mMockVpn.setUnderlyingNetworks(new Network[0]);
        waitForIdle();

        // VPN without underlying networks is treated as metered.
        assertTrue(mCm.isActiveNetworkMetered());

        mMockVpn.disconnect();
    }

    @Test
    public void testIsActiveNetworkMeteredOverAlwaysMeteredVpn() throws Exception {
        // Returns true by default when no network is available.
        assertTrue(mCm.isActiveNetworkMetered());
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true);
        waitForIdle();
        assertFalse(mCm.isActiveNetworkMetered());

        // Connect VPN network.
        mMockVpn.registerAgent(true /* isAlwaysMetered */, uidRangesForUids(Process.myUid()),
                new LinkProperties());
        mMockVpn.connect(true);
        waitForIdle();
        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetwork());

        // VPN is tracking current platform default (WiFi).
        mMockVpn.setUnderlyingNetworks(null);
        waitForIdle();

        // Despite VPN using WiFi (which is unmetered), VPN itself is marked as always metered.
        assertTrue(mCm.isActiveNetworkMetered());


        // VPN explicitly declares WiFi as its underlying network.
        mMockVpn.setUnderlyingNetworks(new Network[] { mWiFiAgent.getNetwork() });
        waitForIdle();

        // Doesn't really matter whether VPN declares its underlying networks explicitly.
        assertTrue(mCm.isActiveNetworkMetered());

        // With WiFi lost, VPN is basically without any underlying networks. And in that case it is
        // anyways suppose to be metered.
        mWiFiAgent.disconnect();
        waitForIdle();

        assertTrue(mCm.isActiveNetworkMetered());

        mMockVpn.disconnect();
    }

    private class DetailedBlockedStatusCallback extends TestNetworkCallback {
        public void expectAvailableThenValidatedCallbacks(HasNetwork n, int blockedStatus) {
            super.expectAvailableThenValidatedCallbacks(n.getNetwork(), blockedStatus, TIMEOUT_MS);
        }
        public void onBlockedStatusChanged(Network network, int blockedReasons) {
            getHistory().add(new CallbackEntry.BlockedStatusInt(network, blockedReasons));
        }
    }

    @Test
    public void testNetworkBlockedStatus() throws Exception {
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();
        mCm.registerNetworkCallback(cellRequest, cellNetworkCallback);
        final DetailedBlockedStatusCallback detailedCallback = new DetailedBlockedStatusCallback();
        mCm.registerNetworkCallback(cellRequest, detailedCallback);

        mockUidNetworkingBlocked();

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        detailedCallback.expectAvailableThenValidatedCallbacks(mCellAgent, BLOCKED_REASON_NONE);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);

        setBlockedReasonChanged(BLOCKED_REASON_BATTERY_SAVER);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_REASON_BATTERY_SAVER);
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertExtraInfoFromCmBlocked(mCellAgent);

        // If blocked state does not change but blocked reason does, the boolean callback is called.
        // TODO: investigate de-duplicating.
        setBlockedReasonChanged(BLOCKED_METERED_REASON_USER_RESTRICTED);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_METERED_REASON_USER_RESTRICTED);

        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> !cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_REASON_NONE);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);

        setBlockedReasonChanged(BLOCKED_METERED_REASON_DATA_SAVER);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_METERED_REASON_DATA_SAVER);
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertExtraInfoFromCmBlocked(mCellAgent);

        // Restrict the network based on UID rule and NOT_METERED capability change.
        mCellAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        cellNetworkCallback.expectCaps(mCellAgent,
                c -> c.hasCapability(NET_CAPABILITY_NOT_METERED));
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> !cb.getBlocked());
        detailedCallback.expectCaps(mCellAgent, c -> c.hasCapability(NET_CAPABILITY_NOT_METERED));
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_REASON_NONE);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);

        mCellAgent.removeCapability(NET_CAPABILITY_NOT_METERED);
        cellNetworkCallback.expectCaps(mCellAgent,
                c -> !c.hasCapability(NET_CAPABILITY_NOT_METERED));
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> cb.getBlocked());
        detailedCallback.expectCaps(mCellAgent,
                c -> !c.hasCapability(NET_CAPABILITY_NOT_METERED));
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_METERED_REASON_DATA_SAVER);
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertExtraInfoFromCmBlocked(mCellAgent);

        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> !cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_REASON_NONE);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);

        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        cellNetworkCallback.assertNoCallback();
        detailedCallback.assertNoCallback();

        // Restrict background data. Networking is not blocked because the network is unmetered.
        setBlockedReasonChanged(BLOCKED_METERED_REASON_DATA_SAVER);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_METERED_REASON_DATA_SAVER);
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertExtraInfoFromCmBlocked(mCellAgent);
        setBlockedReasonChanged(BLOCKED_METERED_REASON_DATA_SAVER);
        cellNetworkCallback.assertNoCallback();

        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        cellNetworkCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> !cb.getBlocked());
        detailedCallback.expect(BLOCKED_STATUS_INT, mCellAgent,
                cb -> cb.getReason() == BLOCKED_REASON_NONE);
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);

        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        cellNetworkCallback.assertNoCallback();
        detailedCallback.assertNoCallback();
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);

        mCm.unregisterNetworkCallback(cellNetworkCallback);
    }

    @Test
    public void testNetworkBlockedStatusBeforeAndAfterConnect() throws Exception {
        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);
        mockUidNetworkingBlocked();

        // No Networkcallbacks invoked before any network is active.
        setBlockedReasonChanged(BLOCKED_REASON_BATTERY_SAVER);
        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        setBlockedReasonChanged(BLOCKED_METERED_REASON_DATA_SAVER);
        defaultCallback.assertNoCallback();

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        defaultCallback.expectAvailableCallbacksUnvalidatedAndBlocked(mCellAgent);
        defaultCallback.expectCaps(mCellAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));

        // Allow to use the network after switching to NOT_METERED network.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.connect(true);
        defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);

        // Switch to METERED network. Restrict the use of the network.
        mWiFiAgent.disconnect();
        defaultCallback.expect(LOST, mWiFiAgent);
        defaultCallback.expectAvailableCallbacksValidatedAndBlocked(mCellAgent);

        // Network becomes NOT_METERED.
        mCellAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        defaultCallback.expectCaps(mCellAgent, c -> c.hasCapability(NET_CAPABILITY_NOT_METERED));
        defaultCallback.expect(BLOCKED_STATUS, mCellAgent, cb -> !cb.getBlocked());

        // Verify there's no Networkcallbacks invoked after data saver on/off.
        setBlockedReasonChanged(BLOCKED_METERED_REASON_DATA_SAVER);
        setBlockedReasonChanged(BLOCKED_REASON_NONE);
        defaultCallback.assertNoCallback();

        mCellAgent.disconnect();
        defaultCallback.expect(LOST, mCellAgent);
        defaultCallback.assertNoCallback();

        mCm.unregisterNetworkCallback(defaultCallback);
    }

    private void expectNetworkRejectNonSecureVpn(InOrder inOrder, boolean add,
            UidRangeParcel... expected) throws Exception {
        inOrder.verify(mMockNetd).networkRejectNonSecureVpn(eq(add), aryEq(expected));
    }

    private void checkNetworkInfo(NetworkInfo ni, int type, DetailedState state) {
        assertNotNull(ni);
        assertEquals(type, ni.getType());
        assertEquals(ConnectivityManager.getNetworkTypeName(type), state, ni.getDetailedState());
        if (state == DetailedState.CONNECTED || state == DetailedState.SUSPENDED) {
            assertNotNull(ni.getExtraInfo());
        } else {
            // Technically speaking, a network that's in CONNECTING state will generally have a
            // non-null extraInfo. This doesn't actually happen in this test because it never calls
            // a legacy API while a network is connecting. When a network is in CONNECTING state
            // because of legacy lockdown VPN, its extraInfo is always null.
            assertNull(ni.getExtraInfo());
        }
    }

    private void assertActiveNetworkInfo(int type, DetailedState state) {
        checkNetworkInfo(mCm.getActiveNetworkInfo(), type, state);
    }
    private void assertNetworkInfo(int type, DetailedState state) {
        checkNetworkInfo(mCm.getNetworkInfo(type), type, state);
    }

    private void assertExtraInfoFromCm(TestNetworkAgentWrapper network, boolean present) {
        final NetworkInfo niForNetwork = mCm.getNetworkInfo(network.getNetwork());
        final NetworkInfo niForType = mCm.getNetworkInfo(network.getLegacyType());
        if (present) {
            assertEquals(network.getExtraInfo(), niForNetwork.getExtraInfo());
            assertEquals(network.getExtraInfo(), niForType.getExtraInfo());
        } else {
            assertNull(niForNetwork.getExtraInfo());
            assertNull(niForType.getExtraInfo());
        }
    }

    private void assertExtraInfoFromCmBlocked(TestNetworkAgentWrapper network) {
        assertExtraInfoFromCm(network, false);
    }

    private void assertExtraInfoFromCmPresent(TestNetworkAgentWrapper network) {
        assertExtraInfoFromCm(network, true);
    }

    // Checks that each of the |agents| receive a blocked status change callback with the specified
    // |blocked| value, in any order. This is needed because when an event affects multiple
    // networks, ConnectivityService does not guarantee the order in which callbacks are fired.
    private void assertBlockedCallbackInAnyOrder(TestNetworkCallback callback, boolean blocked,
            TestNetworkAgentWrapper... agents) {
        final List<Network> expectedNetworks = asList(agents).stream()
                .map((agent) -> agent.getNetwork())
                .collect(Collectors.toList());

        // Expect exactly one blocked callback for each agent.
        for (int i = 0; i < agents.length; i++) {
            final CallbackEntry e = callback.expect(BLOCKED_STATUS, TIMEOUT_MS,
                    c -> c.getBlocked() == blocked);
            final Network network = e.getNetwork();
            assertTrue("Received unexpected blocked callback for network " + network,
                    expectedNetworks.remove(network));
        }
    }

    @Test
    public void testNetworkBlockedStatusAlwaysOnVpn() throws Exception {
        mServiceContext.setPermission(
                Manifest.permission.CONTROL_ALWAYS_ON_VPN, PERMISSION_GRANTED);
        mServiceContext.setPermission(
                Manifest.permission.CONTROL_VPN, PERMISSION_GRANTED);
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        final TestNetworkCallback callback = new TestNetworkCallback();
        final NetworkRequest request = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .build();
        mCm.registerNetworkCallback(request, callback);

        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        final TestNetworkCallback vpnUidCallback = new TestNetworkCallback();
        final NetworkRequest vpnUidRequest = new NetworkRequest.Builder().build();
        registerNetworkCallbackAsUid(vpnUidRequest, vpnUidCallback, VPN_UID);

        final TestNetworkCallback vpnUidDefaultCallback = new TestNetworkCallback();
        registerDefaultNetworkCallbackAsUid(vpnUidDefaultCallback, VPN_UID);

        final TestNetworkCallback vpnDefaultCallbackAsUid = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallbackForUid(VPN_UID, vpnDefaultCallbackAsUid,
                new Handler(ConnectivityThread.getInstanceLooper()));

        final int uid = Process.myUid();

        // Enable always-on VPN lockdown, coverage in VpnTest.
        final List<Integer> excludedUids = new ArrayList<Integer>();
        excludedUids.add(VPN_UID);
        if (mDeps.isAtLeastT()) {
            // On T onwards, the corresponding SDK sandbox UID should also be excluded
            excludedUids.add(toSdkSandboxUid(VPN_UID));
        }

        final List<Range<Integer>> primaryRanges = intRangesPrimaryExcludingUids(excludedUids);
        mCm.setRequireVpnForUids(true, primaryRanges);
        waitForIdle();

        final UidRangeParcel[] uidRangeParcels = intToUidRangeStableParcels(primaryRanges);
        InOrder inOrder = inOrder(mMockNetd);
        expectNetworkRejectNonSecureVpn(inOrder, true, uidRangeParcels);

        // Connect a network when lockdown is active, expect to see it blocked.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false /* validated */);
        callback.expectAvailableCallbacksUnvalidatedAndBlocked(mWiFiAgent);
        defaultCallback.expectAvailableCallbacksUnvalidatedAndBlocked(mWiFiAgent);
        vpnUidCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        vpnUidDefaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        vpnDefaultCallbackAsUid.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);
        // Mobile is BLOCKED even though it's not actually connected.
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);

        // Disable lockdown, expect to see the network unblocked.
        mCm.setRequireVpnForUids(false, primaryRanges);
        waitForIdle();
        callback.expect(BLOCKED_STATUS, mWiFiAgent, cb -> !cb.getBlocked());
        defaultCallback.expect(BLOCKED_STATUS, mWiFiAgent, cb -> !cb.getBlocked());
        vpnUidCallback.assertNoCallback();
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();
        expectNetworkRejectNonSecureVpn(inOrder, false, uidRangeParcels);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);

        // Add our UID to the allowlist, expect network is not blocked. Coverage in VpnTest.
        excludedUids.add(uid);
        if (mDeps.isAtLeastT()) {
            // On T onwards, the corresponding SDK sandbox UID should also be excluded
            excludedUids.add(toSdkSandboxUid(uid));
        }
        final List<Range<Integer>> primaryRangesExcludingUid =
                intRangesPrimaryExcludingUids(excludedUids);
        mCm.setRequireVpnForUids(true, primaryRangesExcludingUid);
        waitForIdle();

        callback.assertNoCallback();
        defaultCallback.assertNoCallback();
        vpnUidCallback.assertNoCallback();
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();

        final UidRangeParcel[] uidRangeParcelsAlsoExcludingUs =
                intToUidRangeStableParcels(primaryRangesExcludingUid);
        expectNetworkRejectNonSecureVpn(inOrder, true, uidRangeParcelsAlsoExcludingUs);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);

        // Connect a new network, expect it to be unblocked.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(false /* validated */);
        callback.expectAvailableCallbacksUnvalidated(mCellAgent);
        defaultCallback.assertNoCallback();
        vpnUidCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        // Cellular is DISCONNECTED because it's not the default and there are no requests for it.
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);

        // Disable lockdown
        mCm.setRequireVpnForUids(false, primaryRangesExcludingUid);
        waitForIdle();
        expectNetworkRejectNonSecureVpn(inOrder, false, uidRangeParcelsAlsoExcludingUs);
        // Remove our UID from the allowlist, and re-enable lockdown.
        mCm.setRequireVpnForUids(true, primaryRanges);
        waitForIdle();
        expectNetworkRejectNonSecureVpn(inOrder, true, uidRangeParcels);
        // Everything should now be blocked.
        defaultCallback.expect(BLOCKED_STATUS, mWiFiAgent, cb -> cb.getBlocked());
        assertBlockedCallbackInAnyOrder(callback, true, mWiFiAgent, mCellAgent);
        vpnUidCallback.assertNoCallback();
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);

        // Disable lockdown. Everything is unblocked.
        mCm.setRequireVpnForUids(false, primaryRanges);
        defaultCallback.expect(BLOCKED_STATUS, mWiFiAgent, cb -> !cb.getBlocked());
        assertBlockedCallbackInAnyOrder(callback, false, mWiFiAgent, mCellAgent);
        vpnUidCallback.assertNoCallback();
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);

        // Enable lockdown and connect a VPN. The VPN is not blocked.
        mCm.setRequireVpnForUids(true, primaryRanges);
        defaultCallback.expect(BLOCKED_STATUS, mWiFiAgent, cb -> cb.getBlocked());
        assertBlockedCallbackInAnyOrder(callback, true, mWiFiAgent, mCellAgent);
        vpnUidCallback.assertNoCallback();
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertNull(mCm.getActiveNetwork());
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);

        mMockVpn.establishForMyUid();
        assertUidRangesUpdatedForMyUid(true);
        defaultCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        vpnUidCallback.assertNoCallback();  // vpnUidCallback has NOT_VPN capability.
        vpnUidDefaultCallback.assertNoCallback();  // VPN does not apply to VPN_UID
        vpnDefaultCallbackAsUid.assertNoCallback();
        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetwork());
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(VPN_UID));
        assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);

        mMockVpn.disconnect();
        defaultCallback.expect(LOST, mMockVpn);
        defaultCallback.expectAvailableCallbacksUnvalidatedAndBlocked(mWiFiAgent);
        vpnUidCallback.assertNoCallback();
        vpnUidDefaultCallback.assertNoCallback();
        vpnDefaultCallbackAsUid.assertNoCallback();
        assertNull(mCm.getActiveNetwork());

        mCm.unregisterNetworkCallback(callback);
        mCm.unregisterNetworkCallback(defaultCallback);
        mCm.unregisterNetworkCallback(vpnUidCallback);
        mCm.unregisterNetworkCallback(vpnUidDefaultCallback);
        mCm.unregisterNetworkCallback(vpnDefaultCallbackAsUid);
    }

    @Test
    public void testVpnExcludesOwnUid() throws Exception {
        // required for registerDefaultNetworkCallbackForUid.
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        // Connect Wi-Fi.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true /* validated */);

        // Connect a VPN that excludes its UID from its UID ranges.
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(VPN_IFNAME);
        final int myUid = Process.myUid();
        final Set<UidRange> ranges = new ArraySet<>();
        ranges.add(new UidRange(0, myUid - 1));
        ranges.add(new UidRange(myUid + 1, UserHandle.PER_USER_RANGE - 1));
        mMockVpn.setUnderlyingNetworks(new Network[] { mWiFiAgent.getNetwork() });
        mMockVpn.establish(lp, myUid, ranges);

        // Wait for validation before registering callbacks.
        waitForIdle();

        final int otherUid = myUid + 1;
        final Handler h = new Handler(ConnectivityThread.getInstanceLooper());
        final TestNetworkCallback otherUidCb = new TestNetworkCallback();
        final TestNetworkCallback defaultCb = new TestNetworkCallback();
        final TestNetworkCallback perUidCb = new TestNetworkCallback();
        registerDefaultNetworkCallbackAsUid(otherUidCb, otherUid);
        mCm.registerDefaultNetworkCallback(defaultCb, h);
        doAsUid(Process.SYSTEM_UID,
                () -> mCm.registerDefaultNetworkCallbackForUid(myUid, perUidCb, h));

        otherUidCb.expectAvailableCallbacksValidated(mMockVpn);
        // BUG (b/195265065): the default network for the VPN app is actually Wi-Fi, not the VPN.
        defaultCb.expectAvailableCallbacksValidated(mMockVpn);
        perUidCb.expectAvailableCallbacksValidated(mMockVpn);
        // getActiveNetwork is not affected by this bug.
        assertEquals(mMockVpn.getNetwork(), mCm.getActiveNetworkForUid(myUid + 1));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(myUid));

        doAsUid(otherUid, () -> mCm.unregisterNetworkCallback(otherUidCb));
        mCm.unregisterNetworkCallback(defaultCb);
        doAsUid(Process.SYSTEM_UID, () -> mCm.unregisterNetworkCallback(perUidCb));
    }

    private VpnProfile setupLockdownVpn(int profileType) {
        final String profileName = "testVpnProfile";
        final byte[] profileTag = profileName.getBytes(StandardCharsets.UTF_8);
        doReturn(profileTag).when(mVpnProfileStore).get(Credentials.LOCKDOWN_VPN);

        final VpnProfile profile = new VpnProfile(profileName);
        profile.name = "My VPN";
        profile.server = "192.0.2.1";
        profile.dnsServers = "8.8.8.8";
        profile.ipsecIdentifier = "My ipsecIdentifier";
        profile.ipsecSecret = "My PSK";
        profile.type = profileType;
        final byte[] encodedProfile = profile.encode();
        doReturn(encodedProfile).when(mVpnProfileStore).get(Credentials.VPN + profileName);

        return profile;
    }

    private void establishLegacyLockdownVpn(Network underlying) throws Exception {
        // The legacy lockdown VPN only supports userId 0, and must have an underlying network.
        assertNotNull(underlying);
        mMockVpn.setVpnType(VpnManager.TYPE_VPN_LEGACY);
        // The legacy lockdown VPN only supports userId 0.
        final Set<UidRange> ranges = Collections.singleton(PRIMARY_UIDRANGE);
        mMockVpn.registerAgent(ranges);
        mMockVpn.setUnderlyingNetworks(new Network[]{underlying});
        mMockVpn.connect(true);
    }

    private void doTestLockdownVpn(VpnProfile profile, boolean expectSetVpnDefaultForUids)
            throws Exception {
        mServiceContext.setPermission(
                Manifest.permission.CONTROL_VPN, PERMISSION_GRANTED);

        final NetworkRequest request = new NetworkRequest.Builder().clearCapabilities().build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);

        final TestNetworkCallback systemDefaultCallback = new TestNetworkCallback();
        mCm.registerSystemDefaultNetworkCallback(systemDefaultCallback,
                new Handler(ConnectivityThread.getInstanceLooper()));

        // Init lockdown state to simulate LockdownVpnTracker behavior.
        mCm.setLegacyLockdownVpnEnabled(true);
        final List<Range<Integer>> ranges =
                intRangesPrimaryExcludingUids(Collections.EMPTY_LIST /* excludedeUids */);
        mCm.setRequireVpnForUids(true /* requireVpn */, ranges);

        // Bring up a network.
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName("rmnet0");
        cellLp.addLinkAddress(new LinkAddress("192.0.2.2/25"));
        cellLp.addRoute(new RouteInfo(new IpPrefix("0.0.0.0/0"), null, "rmnet0"));
        // When lockdown VPN is active, the NetworkInfo state in CONNECTIVITY_ACTION is overwritten
        // with the state of the VPN network. So expect a CONNECTING broadcast.
        final ExpectedBroadcast b = expectConnectivityAction(TYPE_MOBILE, DetailedState.CONNECTING);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(false /* validated */);
        callback.expectAvailableCallbacksUnvalidatedAndBlocked(mCellAgent);
        defaultCallback.expectAvailableCallbacksUnvalidatedAndBlocked(mCellAgent);
        systemDefaultCallback.expectAvailableCallbacksUnvalidatedAndBlocked(mCellAgent);
        b.expectBroadcast();
        // Simulate LockdownVpnTracker attempting to start the VPN since it received the
        // systemDefault callback.
        mMockVpn.startLegacyVpnPrivileged(profile);
        if (expectSetVpnDefaultForUids) {
            // setVpnDefaultForUids() releases the original network request and creates a VPN
            // request so LOST callback is received.
            defaultCallback.expect(LOST, mCellAgent);
            // Due to the VPN default request, getActiveNetworkInfo() gets the mNoServiceNetwork
            // as the network satisfier.
            assertNull(mCm.getActiveNetworkInfo());
        } else {
            assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        }
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_VPN, DetailedState.BLOCKED);
        assertExtraInfoFromCmBlocked(mCellAgent);

        final ExpectedBroadcast b2 = expectConnectivityAction(TYPE_VPN, DetailedState.CONNECTED);
        final ExpectedBroadcast b3 = expectConnectivityAction(TYPE_MOBILE, DetailedState.CONNECTED);
        establishLegacyLockdownVpn(mCellAgent.getNetwork());
        callback.expectAvailableThenValidatedCallbacks(mMockVpn);
        defaultCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        systemDefaultCallback.assertNoCallback();
        final NetworkCapabilities vpnNc = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        b2.expectBroadcast();
        b3.expectBroadcast();
        if (expectSetVpnDefaultForUids) {
            // Due to the VPN default request, getActiveNetworkInfo() gets the VPN network as the
            // network satisfier which has TYPE_VPN.
            assertActiveNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        } else {
            // LegacyVpnRunner does not call setVpnDefaultsForUids(), which means
            // getActiveNetworkInfo() can only return the info for the system-wide default instead.
            // This should be fixed, but LegacyVpnRunner will be removed soon anyway.
            assertActiveNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        }
        assertNetworkInfo(TYPE_MOBILE, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mCellAgent);
        assertTrue(vpnNc.hasTransport(TRANSPORT_VPN));
        assertTrue(vpnNc.hasTransport(TRANSPORT_CELLULAR));
        assertFalse(vpnNc.hasTransport(TRANSPORT_WIFI));
        assertFalse(vpnNc.hasCapability(NET_CAPABILITY_NOT_METERED));
        assertVpnTransportInfo(vpnNc, VpnManager.TYPE_VPN_LEGACY);

        // Switch default network from cell to wifi. Expect VPN to disconnect and reconnect.
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName("wlan0");
        wifiLp.addLinkAddress(new LinkAddress("192.0.2.163/25"));
        wifiLp.addRoute(new RouteInfo(new IpPrefix("0.0.0.0/0"), null, "wlan0"));
        final NetworkCapabilities wifiNc = new NetworkCapabilities();
        wifiNc.addTransportType(TRANSPORT_WIFI);
        wifiNc.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp, wifiNc);

        final ExpectedBroadcast b4 =
                expectConnectivityAction(TYPE_MOBILE, DetailedState.DISCONNECTED);
        // Wifi is CONNECTING because the VPN isn't up yet.
        final ExpectedBroadcast b5 = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTING);
        mWiFiAgent.connect(false /* validated */);
        // Wifi is not blocked since VPN network is still connected.
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        defaultCallback.assertNoCallback();
        systemDefaultCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        b4.expectBroadcast();
        b5.expectBroadcast();

        // Simulate LockdownVpnTracker restarting the VPN since it received the systemDefault
        // callback with different network.
        final ExpectedBroadcast b6 = expectConnectivityAction(TYPE_VPN, DetailedState.DISCONNECTED);
        mMockVpn.stopVpnRunnerPrivileged();
        mMockVpn.startLegacyVpnPrivileged(profile);
        // VPN network is disconnected (to restart)
        callback.expect(LOST, mMockVpn);
        defaultCallback.expect(LOST, mMockVpn);
        // The network preference is cleared when VPN is disconnected so it receives callbacks for
        // the system-wide default.
        defaultCallback.expectAvailableCallbacksUnvalidatedAndBlocked(mWiFiAgent);
        if (expectSetVpnDefaultForUids) {
            // setVpnDefaultForUids() releases the original network request and creates a VPN
            // request so LOST callback is received.
            defaultCallback.expect(LOST, mWiFiAgent);
        }
        systemDefaultCallback.assertNoCallback();
        b6.expectBroadcast();

        // While the VPN is reconnecting on the new network, everything is blocked.
        if (expectSetVpnDefaultForUids) {
            // Due to the VPN default request, getActiveNetworkInfo() gets the mNoServiceNetwork
            // as the network satisfier.
            assertNull(mCm.getActiveNetworkInfo());
        } else {
            assertActiveNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);
        }
        assertNetworkInfo(TYPE_MOBILE, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.BLOCKED);
        assertNetworkInfo(TYPE_VPN, DetailedState.BLOCKED);
        assertExtraInfoFromCmBlocked(mWiFiAgent);

        // The VPN comes up again on wifi.
        final ExpectedBroadcast b7 = expectConnectivityAction(TYPE_VPN, DetailedState.CONNECTED);
        final ExpectedBroadcast b8 = expectConnectivityAction(TYPE_WIFI, DetailedState.CONNECTED);
        establishLegacyLockdownVpn(mWiFiAgent.getNetwork());
        callback.expectAvailableThenValidatedCallbacks(mMockVpn);
        defaultCallback.expectAvailableThenValidatedCallbacks(mMockVpn);
        systemDefaultCallback.assertNoCallback();
        b7.expectBroadcast();
        b8.expectBroadcast();
        if (expectSetVpnDefaultForUids) {
            // Due to the VPN default request, getActiveNetworkInfo() gets the VPN network as the
            // network satisfier which has TYPE_VPN.
            assertActiveNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        } else {
            // LegacyVpnRunner does not call setVpnDefaultsForUids(), which means
            // getActiveNetworkInfo() can only return the info for the system-wide default instead.
            // This should be fixed, but LegacyVpnRunner will be removed soon anyway.
            assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        }
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mWiFiAgent);
        final NetworkCapabilities vpnNc2 = mCm.getNetworkCapabilities(mMockVpn.getNetwork());
        assertTrue(vpnNc2.hasTransport(TRANSPORT_VPN));
        assertTrue(vpnNc2.hasTransport(TRANSPORT_WIFI));
        assertFalse(vpnNc2.hasTransport(TRANSPORT_CELLULAR));
        assertTrue(vpnNc2.hasCapability(NET_CAPABILITY_NOT_METERED));

        // Disconnect cell. Nothing much happens since it's not the default network.
        mCellAgent.disconnect();
        callback.expect(LOST, mCellAgent);
        defaultCallback.assertNoCallback();
        systemDefaultCallback.assertNoCallback();

        if (expectSetVpnDefaultForUids) {
            // Due to the VPN default request, getActiveNetworkInfo() gets the VPN network as the
            // network satisfier which has TYPE_VPN.
            assertActiveNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        } else {
            // LegacyVpnRunner does not call setVpnDefaultsForUids(), which means
            // getActiveNetworkInfo() can only return the info for the system-wide default instead.
            // This should be fixed, but LegacyVpnRunner will be removed soon anyway.
            assertActiveNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        }
        assertNetworkInfo(TYPE_MOBILE, DetailedState.DISCONNECTED);
        assertNetworkInfo(TYPE_WIFI, DetailedState.CONNECTED);
        assertNetworkInfo(TYPE_VPN, DetailedState.CONNECTED);
        assertExtraInfoFromCmPresent(mWiFiAgent);

        final ExpectedBroadcast b9 =
                expectConnectivityAction(TYPE_WIFI, DetailedState.DISCONNECTED);
        final ExpectedBroadcast b10 =
                expectConnectivityAction(TYPE_VPN, DetailedState.DISCONNECTED);
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        callback.expectCaps(mMockVpn, c -> !c.hasTransport(TRANSPORT_WIFI));
        defaultCallback.expectCaps(mMockVpn, c -> !c.hasTransport(TRANSPORT_WIFI));
        systemDefaultCallback.expect(LOST, mWiFiAgent);
        // TODO: There should only be one LOST callback. Since the WIFI network is underlying a VPN
        // network, ConnectivityService#propagateUnderlyingNetworkCapabilities() causes a rematch to
        // occur. Notably, this happens before setting the satisfiers of its network requests to
        // null. Since the satisfiers are set to null in the rematch, an extra LOST callback is
        // called.
        systemDefaultCallback.expect(LOST, mWiFiAgent);
        b9.expectBroadcast();
        mMockVpn.stopVpnRunnerPrivileged();
        callback.expect(LOST, mMockVpn);
        defaultCallback.expect(LOST, mMockVpn);
        b10.expectBroadcast();

        assertNoCallbacks(callback, defaultCallback, systemDefaultCallback);
    }

    @Test
    public void testLockdownVpn_LegacyVpnRunner() throws Exception {
        final VpnProfile profile = setupLockdownVpn(VpnProfile.TYPE_IPSEC_XAUTH_PSK);
        doTestLockdownVpn(profile, false /* expectSetVpnDefaultForUids */);
    }

    @Test
    public void testLockdownVpn_Ikev2VpnRunner() throws Exception {
        final VpnProfile profile = setupLockdownVpn(VpnProfile.TYPE_IKEV2_IPSEC_PSK);
        doTestLockdownVpn(profile, true /* expectSetVpnDefaultForUids */);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testLockdownSetFirewallUidRule() throws Exception {
        final List<Range<Integer>> lockdownRange =
                intRangesPrimaryExcludingUids(Collections.EMPTY_LIST /* excludedeUids */);
        // Enable Lockdown
        mCm.setRequireVpnForUids(true /* requireVpn */, lockdownRange);
        waitForIdle();

        // Lockdown rule is set to apps uids
        verify(mBpfNetMaps, times(3)).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(APP1_UID, true /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(APP2_UID, true /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(VPN_UID, true /* add */);

        reset(mBpfNetMaps);

        // Disable lockdown
        mCm.setRequireVpnForUids(false /* requireVPN */, lockdownRange);
        waitForIdle();

        // Lockdown rule is removed from apps uids
        verify(mBpfNetMaps, times(3)).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(APP1_UID, false /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(APP2_UID, false /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(VPN_UID, false /* add */);

        // Interface rules are not changed by Lockdown mode enable/disable
        verify(mBpfNetMaps, never()).addUidInterfaceRules(any(), any());
        verify(mBpfNetMaps, never()).removeUidInterfaceRules(any());
    }

    private void doTestSetUidFirewallRule(final int chain, final int defaultRule) {
        final int uid = 1001;
        mCm.setUidFirewallRule(chain, uid, FIREWALL_RULE_ALLOW);
        verify(mBpfNetMaps).setUidRule(chain, uid, FIREWALL_RULE_ALLOW);
        reset(mBpfNetMaps);

        mCm.setUidFirewallRule(chain, uid, FIREWALL_RULE_DENY);
        verify(mBpfNetMaps).setUidRule(chain, uid, FIREWALL_RULE_DENY);
        reset(mBpfNetMaps);

        mCm.setUidFirewallRule(chain, uid, FIREWALL_RULE_DEFAULT);
        verify(mBpfNetMaps).setUidRule(chain, uid, defaultRule);
        reset(mBpfNetMaps);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testSetUidFirewallRule() throws Exception {
        doTestSetUidFirewallRule(FIREWALL_CHAIN_DOZABLE, FIREWALL_RULE_DENY);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_STANDBY, FIREWALL_RULE_ALLOW);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_POWERSAVE, FIREWALL_RULE_DENY);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_RESTRICTED, FIREWALL_RULE_DENY);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_LOW_POWER_STANDBY, FIREWALL_RULE_DENY);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, FIREWALL_RULE_DENY);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_OEM_DENY_1, FIREWALL_RULE_ALLOW);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_OEM_DENY_2, FIREWALL_RULE_ALLOW);
        doTestSetUidFirewallRule(FIREWALL_CHAIN_OEM_DENY_3, FIREWALL_RULE_ALLOW);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testSetFirewallChainEnabled() throws Exception {
        final List<Integer> firewallChains = Arrays.asList(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY,
                FIREWALL_CHAIN_POWERSAVE,
                FIREWALL_CHAIN_RESTRICTED,
                FIREWALL_CHAIN_LOW_POWER_STANDBY,
                FIREWALL_CHAIN_BACKGROUND,
                FIREWALL_CHAIN_OEM_DENY_1,
                FIREWALL_CHAIN_OEM_DENY_2,
                FIREWALL_CHAIN_OEM_DENY_3);
        for (final int chain: firewallChains) {
            mCm.setFirewallChainEnabled(chain, true /* enabled */);
            verify(mBpfNetMaps).setChildChain(chain, true /* enable */);
            reset(mBpfNetMaps);

            mCm.setFirewallChainEnabled(chain, false /* enabled */);
            verify(mBpfNetMaps).setChildChain(chain, false /* enable */);
            reset(mBpfNetMaps);
        }
    }

    private void doTestSetFirewallChainEnabledCloseSocket(final int chain,
            final boolean isAllowList) throws Exception {
        reset(mDestroySocketsWrapper);

        mCm.setFirewallChainEnabled(chain, true /* enabled */);
        final Set<Integer> uids =
                new ArraySet<>(List.of(TEST_PACKAGE_UID, TEST_PACKAGE_UID2));
        if (isAllowList) {
            final Set<Range<Integer>> range = new ArraySet<>(
                    List.of(new Range<>(Process.FIRST_APPLICATION_UID, Integer.MAX_VALUE)));
            verify(mDestroySocketsWrapper).destroyLiveTcpSockets(range, uids);
        } else {
            verify(mDestroySocketsWrapper).destroyLiveTcpSocketsByOwnerUids(uids);
        }

        mCm.setFirewallChainEnabled(chain, false /* enabled */);
        verifyNoMoreInteractions(mDestroySocketsWrapper);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testSetFirewallChainEnabledCloseSocket() throws Exception {
        doReturn(new ArraySet<>(Arrays.asList(TEST_PACKAGE_UID, TEST_PACKAGE_UID2)))
                .when(mBpfNetMaps)
                .getUidsWithDenyRuleOnDenyListChain(anyInt());
        doReturn(new ArraySet<>(Arrays.asList(TEST_PACKAGE_UID, TEST_PACKAGE_UID2)))
                .when(mBpfNetMaps)
                .getUidsWithAllowRuleOnAllowListChain(anyInt());

        final boolean allowlist = true;
        final boolean denylist = false;

        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_DOZABLE, allowlist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_POWERSAVE, allowlist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_RESTRICTED, allowlist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_LOW_POWER_STANDBY, allowlist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_BACKGROUND, allowlist);

        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_STANDBY, denylist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_OEM_DENY_1, denylist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_OEM_DENY_2, denylist);
        doTestSetFirewallChainEnabledCloseSocket(FIREWALL_CHAIN_OEM_DENY_3, denylist);
    }

    private void doTestReplaceFirewallChain(final int chain) {
        final int[] uids = new int[] {1001, 1002};
        mCm.replaceFirewallChain(chain, uids);
        verify(mBpfNetMaps).replaceUidChain(chain, uids);
        reset(mBpfNetMaps);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testReplaceFirewallChain() {
        doTestReplaceFirewallChain(FIREWALL_CHAIN_DOZABLE);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_STANDBY);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_POWERSAVE);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_RESTRICTED);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_LOW_POWER_STANDBY);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_BACKGROUND);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_OEM_DENY_1);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_OEM_DENY_2);
        doTestReplaceFirewallChain(FIREWALL_CHAIN_OEM_DENY_3);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testInvalidFirewallChain() throws Exception {
        final int uid = 1001;
        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected,
                () -> mCm.setUidFirewallRule(-1 /* chain */, uid, FIREWALL_RULE_ALLOW));
        assertThrows(expected,
                () -> mCm.setUidFirewallRule(100 /* chain */, uid, FIREWALL_RULE_ALLOW));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testInvalidFirewallRule() throws Exception {
        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected,
                () -> mCm.setUidFirewallRule(FIREWALL_CHAIN_DOZABLE,
                        1001 /* uid */, -1 /* rule */));
        assertThrows(expected,
                () -> mCm.setUidFirewallRule(FIREWALL_CHAIN_DOZABLE,
                        1001 /* uid */, 100 /* rule */));
    }

    /**
     * Test mutable and requestable network capabilities such as
     * {@link NetworkCapabilities#NET_CAPABILITY_TRUSTED} and
     * {@link NetworkCapabilities#NET_CAPABILITY_NOT_VCN_MANAGED}. Verify that the
     * {@code ConnectivityService} re-assign the networks accordingly.
     */
    @Test
    public final void testLoseMutableAndRequestableCaps() throws Exception {
        final int[] testCaps = new int [] {
                NET_CAPABILITY_TRUSTED,
                NET_CAPABILITY_NOT_VCN_MANAGED
        };
        for (final int testCap : testCaps) {
            // Create requests with and without the testing capability.
            final TestNetworkCallback callbackWithCap = new TestNetworkCallback();
            final TestNetworkCallback callbackWithoutCap = new TestNetworkCallback();
            mCm.requestNetwork(new NetworkRequest.Builder().addCapability(testCap).build(),
                    callbackWithCap);
            mCm.requestNetwork(new NetworkRequest.Builder().removeCapability(testCap).build(),
                    callbackWithoutCap);

            // Setup networks with testing capability and verify the default network changes.
            mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
            mCellAgent.addCapability(testCap);
            mCellAgent.connect(true);
            callbackWithCap.expectAvailableThenValidatedCallbacks(mCellAgent);
            callbackWithoutCap.expectAvailableThenValidatedCallbacks(mCellAgent);
            verify(mMockNetd).networkSetDefault(eq(mCellAgent.getNetwork().netId));
            reset(mMockNetd);

            mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
            mWiFiAgent.addCapability(testCap);
            mWiFiAgent.connect(true);
            callbackWithCap.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
            callbackWithoutCap.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
            verify(mMockNetd).networkSetDefault(eq(mWiFiAgent.getNetwork().netId));
            reset(mMockNetd);

            // Remove the testing capability on wifi, verify the callback and default network
            // changes back to cellular.
            mWiFiAgent.removeCapability(testCap);
            callbackWithCap.expectAvailableCallbacksValidated(mCellAgent);
            callbackWithoutCap.expectCaps(mWiFiAgent, c -> !c.hasCapability(testCap));
            verify(mMockNetd).networkSetDefault(eq(mCellAgent.getNetwork().netId));
            reset(mMockNetd);

            mCellAgent.removeCapability(testCap);
            callbackWithCap.expect(LOST, mCellAgent);
            callbackWithoutCap.assertNoCallback();
            verify(mMockNetd).networkClearDefault();

            mCm.unregisterNetworkCallback(callbackWithCap);
            mCm.unregisterNetworkCallback(callbackWithoutCap);
        }
    }

    @Test
    public final void testBatteryStatsNetworkType() throws Exception {
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName("cell0");
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(true);
        waitForIdle();
        final ArrayTrackRecord<ReportedInterfaces>.ReadHead readHead =
                mDeps.mReportedInterfaceHistory.newReadHead();
        assertNotNull(readHead.poll(TIMEOUT_MS, ri -> ri.contentEquals(mServiceContext,
                cellLp.getInterfaceName(),
                new int[] { TRANSPORT_CELLULAR })));

        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName("wifi0");
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);
        waitForIdle();
        assertNotNull(readHead.poll(TIMEOUT_MS, ri -> ri.contentEquals(mServiceContext,
                wifiLp.getInterfaceName(),
                new int[] { TRANSPORT_WIFI })));

        mCellAgent.disconnect();
        mWiFiAgent.disconnect();

        cellLp.setInterfaceName("wifi0");
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(true);
        waitForIdle();
        assertNotNull(readHead.poll(TIMEOUT_MS, ri -> ri.contentEquals(mServiceContext,
                cellLp.getInterfaceName(),
                new int[] { TRANSPORT_CELLULAR })));
        mCellAgent.disconnect();
    }

    /**
     * Make simulated InterfaceConfigParcel for Nat464Xlat to query clat lower layer info.
     */
    private InterfaceConfigurationParcel getClatInterfaceConfigParcel(LinkAddress la) {
        final InterfaceConfigurationParcel cfg = new InterfaceConfigurationParcel();
        cfg.hwAddr = "11:22:33:44:55:66";
        cfg.ipv4Addr = la.getAddress().getHostAddress();
        cfg.prefixLength = la.getPrefixLength();
        return cfg;
    }

    /**
     * Make expected stack link properties, copied from Nat464Xlat.
     */
    private LinkProperties makeClatLinkProperties(LinkAddress la) {
        LinkAddress clatAddress = la;
        LinkProperties stacked = new LinkProperties();
        stacked.setInterfaceName(CLAT_MOBILE_IFNAME);
        RouteInfo ipv4Default = new RouteInfo(
                new LinkAddress(Inet4Address.ANY, 0),
                clatAddress.getAddress(), CLAT_MOBILE_IFNAME);
        stacked.addRoute(ipv4Default);
        stacked.addLinkAddress(clatAddress);
        return stacked;
    }

    private Nat64PrefixEventParcel makeNat64PrefixEvent(final int netId, final int prefixOperation,
            final String prefixAddress, final int prefixLength) {
        final Nat64PrefixEventParcel event = new Nat64PrefixEventParcel();
        event.netId = netId;
        event.prefixOperation = prefixOperation;
        event.prefixAddress = prefixAddress;
        event.prefixLength = prefixLength;
        return event;
    }

    private void verifyWakeupModifyInterface(String iface, boolean add) throws RemoteException {
        if (add) {
            verify(mMockNetd).wakeupAddInterface(eq(iface), anyString(), anyInt(),
                    anyInt());
        } else {
            verify(mMockNetd).wakeupDelInterface(eq(iface), anyString(), anyInt(),
                    anyInt());
        }
    }

    private <T> T verifyWithOrder(@Nullable InOrder inOrder, @NonNull T t) {
        if (inOrder != null) {
            return inOrder.verify(t);
        } else {
            // times(1) for consistency with the above. InOrder#verify always implies times(1).
            return verify(t, times(1));
        }
    }

    private <T> T verifyNeverWithOrder(@Nullable InOrder inOrder, @NonNull T t) {
        if (inOrder != null) {
            return inOrder.verify(t, never());
        } else {
            return verify(t, never());
        }
    }

    private void verifyClatdStart(@Nullable InOrder inOrder, @NonNull String iface, int netId,
            @NonNull String nat64Prefix) throws Exception {
        if (mDeps.isAtLeastT()) {
            verifyWithOrder(inOrder, mClatCoordinator)
                .clatStart(eq(iface), eq(netId), eq(new IpPrefix(nat64Prefix)));
        } else {
            verifyWithOrder(inOrder, mMockNetd).clatdStart(eq(iface), eq(nat64Prefix));
        }
    }

    private void verifyNeverClatdStart(@Nullable InOrder inOrder, @NonNull String iface)
            throws Exception {
        if (mDeps.isAtLeastT()) {
            verifyNeverWithOrder(inOrder, mClatCoordinator).clatStart(eq(iface), anyInt(), any());
        } else {
            verifyNeverWithOrder(inOrder, mMockNetd).clatdStart(eq(iface), anyString());
        }
    }

    private void verifyClatdStop(@Nullable InOrder inOrder, @NonNull String iface)
            throws Exception {
        if (mDeps.isAtLeastT()) {
            verifyWithOrder(inOrder, mClatCoordinator).clatStop();
        } else {
            verifyWithOrder(inOrder, mMockNetd).clatdStop(eq(iface));
        }
    }

    private void verifyNeverClatdStop(@Nullable InOrder inOrder, @NonNull String iface)
            throws Exception {
        if (mDeps.isAtLeastT()) {
            verifyNeverWithOrder(inOrder, mClatCoordinator).clatStop();
        } else {
            verifyNeverWithOrder(inOrder, mMockNetd).clatdStop(eq(iface));
        }
    }

    private void expectNativeNetworkCreated(int netId, int permission, String iface,
            InOrder inOrder) throws Exception {
        verifyWithOrder(inOrder, mMockNetd).networkCreate(nativeNetworkConfigPhysical(netId,
                permission));
        verifyWithOrder(inOrder, mMockDnsResolver).createNetworkCache(eq(netId));
        if (iface != null) {
            verifyWithOrder(inOrder, mMockNetd).networkAddInterface(netId, iface);
        }
    }

    private void expectNativeNetworkCreated(int netId, int permission, String iface)
            throws Exception {
        expectNativeNetworkCreated(netId, permission, iface, null /* inOrder */);
    }

    private int getIdleTimerLabel(int netId, int transportType) {
        return ConnectivityService.LegacyNetworkActivityTracker.getIdleTimerLabel(
                mDeps.isAtLeastV(), netId, transportType);
    }

    @Test
    public void testStackedLinkProperties() throws Exception {
        final LinkAddress myIpv4 = new LinkAddress("1.2.3.4/24");
        final LinkAddress myIpv6 = new LinkAddress("2001:db8:1::1/64");
        final String kNat64PrefixString = "2001:db8:64:64:64:64::";
        final IpPrefix kNat64Prefix = new IpPrefix(InetAddress.getByName(kNat64PrefixString), 96);
        final String kOtherNat64PrefixString = "64:ff9b::";
        final IpPrefix kOtherNat64Prefix = new IpPrefix(
                InetAddress.getByName(kOtherNat64PrefixString), 96);
        final RouteInfo ipv6Default =
                new RouteInfo((IpPrefix) null, myIpv6.getAddress(), MOBILE_IFNAME);
        final RouteInfo ipv6Subnet = new RouteInfo(myIpv6, null, MOBILE_IFNAME);
        final RouteInfo ipv4Subnet = new RouteInfo(myIpv4, null, MOBILE_IFNAME);
        final RouteInfo stackedDefault =
                new RouteInfo((IpPrefix) null, myIpv4.getAddress(), CLAT_MOBILE_IFNAME);
        final BaseNetdUnsolicitedEventListener netdUnsolicitedListener =
                getRegisteredNetdUnsolicitedEventListener();

        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(networkRequest, networkCallback);

        // Prepare ipv6 only link properties.
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        cellLp.addLinkAddress(myIpv6);
        cellLp.addRoute(ipv6Default);
        cellLp.addRoute(ipv6Subnet);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        reset(mClatCoordinator);

        // Connect with ipv6 link properties. Expect prefix discovery to be started.
        mCellAgent.connect(true);
        int cellNetId = mCellAgent.getNetwork().netId;
        waitForIdle();

        expectNativeNetworkCreated(cellNetId, INetd.PERMISSION_NONE, MOBILE_IFNAME);
        assertRoutesAdded(cellNetId, ipv6Subnet, ipv6Default);
        final ArrayTrackRecord<ReportedInterfaces>.ReadHead readHead =
                mDeps.mReportedInterfaceHistory.newReadHead();
        assertNotNull(readHead.poll(TIMEOUT_MS, ri -> ri.contentEquals(mServiceContext,
                cellLp.getInterfaceName(),
                new int[] { TRANSPORT_CELLULAR })));

        networkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        verify(mMockDnsResolver, times(1)).startPrefix64Discovery(cellNetId);

        // Switching default network updates TCP buffer sizes.
        verifyTcpBufferSizeChange(ConnectivityService.DEFAULT_TCP_BUFFER_SIZES);
        // Add an IPv4 address. Expect prefix discovery to be stopped. Netd doesn't tell us that
        // the NAT64 prefix was removed because one was never discovered.
        cellLp.addLinkAddress(myIpv4);
        mCellAgent.sendLinkProperties(cellLp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        assertRoutesAdded(cellNetId, ipv4Subnet);
        verify(mMockDnsResolver, times(1)).stopPrefix64Discovery(cellNetId);
        verify(mMockDnsResolver, atLeastOnce()).setResolverConfiguration(any());

        // Make sure BatteryStats was not told about any v4- interfaces, as none should have
        // come online yet.
        waitForIdle();
        assertNull(readHead.poll(0 /* timeout */, ri -> mServiceContext.equals(ri.context)
                && ri.iface != null && ri.iface.startsWith("v4-")));

        verifyNoMoreInteractions(mMockNetd);
        verifyNoMoreInteractions(mClatCoordinator);
        verifyNoMoreInteractions(mMockDnsResolver);
        reset(mMockNetd);
        reset(mClatCoordinator);
        reset(mMockDnsResolver);
        doReturn(getClatInterfaceConfigParcel(myIpv4)).when(mMockNetd)
                .interfaceGetCfg(CLAT_MOBILE_IFNAME);

        // Remove IPv4 address. Expect prefix discovery to be started again.
        cellLp.removeLinkAddress(myIpv4);
        mCellAgent.sendLinkProperties(cellLp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        verify(mMockDnsResolver, times(1)).startPrefix64Discovery(cellNetId);
        assertRoutesRemoved(cellNetId, ipv4Subnet);

        // When NAT64 prefix discovery succeeds, LinkProperties are updated and clatd is started.
        assertNull(mCm.getLinkProperties(mCellAgent.getNetwork()).getNat64Prefix());
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(
                makeNat64PrefixEvent(cellNetId, PREFIX_OPERATION_ADDED, kNat64PrefixString, 96));
        LinkProperties lpBeforeClat = networkCallback.expect(
                LINK_PROPERTIES_CHANGED, mCellAgent).getLp();
        assertEquals(0, lpBeforeClat.getStackedLinks().size());
        assertEquals(kNat64Prefix, lpBeforeClat.getNat64Prefix());
        verifyClatdStart(null /* inOrder */, MOBILE_IFNAME, cellNetId, kNat64Prefix.toString());

        // Clat iface comes up. Expect stacked link to be added.
        netdUnsolicitedListener.onInterfaceLinkStateChanged(
                CLAT_MOBILE_IFNAME, true);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        List<LinkProperties> stackedLps = mCm.getLinkProperties(mCellAgent.getNetwork())
                .getStackedLinks();
        assertEquals(makeClatLinkProperties(myIpv4), stackedLps.get(0));
        assertRoutesAdded(cellNetId, stackedDefault);
        verify(mMockNetd, times(1)).networkAddInterface(cellNetId, CLAT_MOBILE_IFNAME);
        // Change trivial linkproperties and see if stacked link is preserved.
        cellLp.addDnsServer(InetAddress.getByName("8.8.8.8"));
        mCellAgent.sendLinkProperties(cellLp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);

        List<LinkProperties> stackedLpsAfterChange =
                mCm.getLinkProperties(mCellAgent.getNetwork()).getStackedLinks();
        assertNotEquals(stackedLpsAfterChange, Collections.EMPTY_LIST);
        assertEquals(makeClatLinkProperties(myIpv4), stackedLpsAfterChange.get(0));

        verify(mMockDnsResolver, times(1)).setResolverConfiguration(
                mResolverParamsParcelCaptor.capture());
        ResolverParamsParcel resolvrParams = mResolverParamsParcelCaptor.getValue();
        assertEquals(1, resolvrParams.servers.length);
        assertTrue(CollectionUtils.contains(resolvrParams.servers, "8.8.8.8"));

        for (final LinkProperties stackedLp : stackedLpsAfterChange) {
            assertNotNull(readHead.poll(TIMEOUT_MS, ri -> ri.contentEquals(mServiceContext,
                    stackedLp.getInterfaceName(),
                    new int[] { TRANSPORT_CELLULAR })));
        }
        reset(mMockNetd);
        reset(mClatCoordinator);
        doReturn(getClatInterfaceConfigParcel(myIpv4)).when(mMockNetd)
                .interfaceGetCfg(CLAT_MOBILE_IFNAME);
        // Change the NAT64 prefix without first removing it.
        // Expect clatd to be stopped and started with the new prefix.
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(makeNat64PrefixEvent(
                cellNetId, PREFIX_OPERATION_ADDED, kOtherNat64PrefixString, 96));
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getStackedLinks().size() == 0);
        verifyClatdStop(null /* inOrder */, MOBILE_IFNAME);
        assertRoutesRemoved(cellNetId, stackedDefault);
        verify(mMockNetd, times(1)).networkRemoveInterface(cellNetId, CLAT_MOBILE_IFNAME);

        verifyClatdStart(null /* inOrder */, MOBILE_IFNAME, cellNetId,
                kOtherNat64Prefix.toString());
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getNat64Prefix().equals(kOtherNat64Prefix));
        netdUnsolicitedListener.onInterfaceLinkStateChanged(CLAT_MOBILE_IFNAME, true);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getStackedLinks().size() == 1);
        assertRoutesAdded(cellNetId, stackedDefault);
        verify(mMockNetd, times(1)).networkAddInterface(cellNetId, CLAT_MOBILE_IFNAME);
        reset(mMockNetd);
        reset(mClatCoordinator);

        // Add ipv4 address, expect that clatd and prefix discovery are stopped and stacked
        // linkproperties are cleaned up.
        cellLp.addLinkAddress(myIpv4);
        cellLp.addRoute(ipv4Subnet);
        mCellAgent.sendLinkProperties(cellLp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        assertRoutesAdded(cellNetId, ipv4Subnet);
        verifyClatdStop(null /* inOrder */, MOBILE_IFNAME);
        verify(mMockDnsResolver, times(1)).stopPrefix64Discovery(cellNetId);

        // As soon as stop is called, the linkproperties lose the stacked interface.
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        LinkProperties actualLpAfterIpv4 = mCm.getLinkProperties(mCellAgent.getNetwork());
        LinkProperties expected = new LinkProperties(cellLp);
        expected.setNat64Prefix(kOtherNat64Prefix);
        assertEquals(expected, actualLpAfterIpv4);
        assertEquals(0, actualLpAfterIpv4.getStackedLinks().size());
        assertRoutesRemoved(cellNetId, stackedDefault);

        // The interface removed callback happens but has no effect after stop is called.
        netdUnsolicitedListener.onInterfaceRemoved(CLAT_MOBILE_IFNAME);
        networkCallback.assertNoCallback();
        verify(mMockNetd, times(1)).networkRemoveInterface(cellNetId, CLAT_MOBILE_IFNAME);

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(CLAT_MOBILE_IFNAME, false);
        }

        verifyNoMoreInteractions(mMockNetd);
        verifyNoMoreInteractions(mClatCoordinator);
        verifyNoMoreInteractions(mMockDnsResolver);
        reset(mMockNetd);
        reset(mClatCoordinator);
        reset(mMockDnsResolver);
        doReturn(getClatInterfaceConfigParcel(myIpv4)).when(mMockNetd)
                .interfaceGetCfg(CLAT_MOBILE_IFNAME);

        // Stopping prefix discovery causes netd to tell us that the NAT64 prefix is gone.
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(makeNat64PrefixEvent(
                cellNetId, PREFIX_OPERATION_REMOVED, kOtherNat64PrefixString, 96));
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getNat64Prefix() == null);

        // Remove IPv4 address and expect prefix discovery and clatd to be started again.
        cellLp.removeLinkAddress(myIpv4);
        cellLp.removeRoute(new RouteInfo(myIpv4, null, MOBILE_IFNAME));
        cellLp.removeDnsServer(InetAddress.getByName("8.8.8.8"));
        mCellAgent.sendLinkProperties(cellLp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        assertRoutesRemoved(cellNetId, ipv4Subnet);  // Directly-connected routes auto-added.
        verify(mMockDnsResolver, times(1)).startPrefix64Discovery(cellNetId);
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(makeNat64PrefixEvent(
                cellNetId, PREFIX_OPERATION_ADDED, kNat64PrefixString, 96));
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        verifyClatdStart(null /* inOrder */, MOBILE_IFNAME, cellNetId, kNat64Prefix.toString());

        // Clat iface comes up. Expect stacked link to be added.
        netdUnsolicitedListener.onInterfaceLinkStateChanged(CLAT_MOBILE_IFNAME, true);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getStackedLinks().size() == 1
                        && cb.getLp().getNat64Prefix() != null);
        assertRoutesAdded(cellNetId, stackedDefault);
        verify(mMockNetd, times(1)).networkAddInterface(cellNetId, CLAT_MOBILE_IFNAME);

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(CLAT_MOBILE_IFNAME, true);
        }

        // NAT64 prefix is removed. Expect that clat is stopped.
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(makeNat64PrefixEvent(
                cellNetId, PREFIX_OPERATION_REMOVED, kNat64PrefixString, 96));
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getStackedLinks().size() == 0
                        && cb.getLp().getNat64Prefix() == null);
        assertRoutesRemoved(cellNetId, ipv4Subnet, stackedDefault);

        // Stop has no effect because clat is already stopped.
        verifyClatdStop(null /* inOrder */, MOBILE_IFNAME);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getStackedLinks().size() == 0);
        verify(mMockNetd, times(1)).networkRemoveInterface(cellNetId, CLAT_MOBILE_IFNAME);
        verify(mMockNetd, times(1)).interfaceGetCfg(CLAT_MOBILE_IFNAME);

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(CLAT_MOBILE_IFNAME, false);
        }

        // Clean up.
        mCellAgent.disconnect();
        networkCallback.expect(LOST, mCellAgent);
        networkCallback.assertNoCallback();
        verify(mMockNetd, times(1)).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                eq(Integer.toString(getIdleTimerLabel(cellNetId, TRANSPORT_CELLULAR))));
        verify(mMockNetd).networkDestroy(cellNetId);
        if (mDeps.isAtLeastU()) {
            verify(mMockNetd).setNetworkAllowlist(any());
        } else {
            verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(MOBILE_IFNAME, false);
        }

        verifyNoMoreInteractions(mMockNetd);
        verifyNoMoreInteractions(mClatCoordinator);
        reset(mMockNetd);
        reset(mClatCoordinator);

        // Test disconnecting a network that is running 464xlat.

        // Connect a network with a NAT64 prefix.
        doReturn(getClatInterfaceConfigParcel(myIpv4)).when(mMockNetd)
                .interfaceGetCfg(CLAT_MOBILE_IFNAME);
        cellLp.setNat64Prefix(kNat64Prefix);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(false /* validated */);
        networkCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        cellNetId = mCellAgent.getNetwork().netId;
        verify(mMockNetd, times(1)).networkCreate(nativeNetworkConfigPhysical(cellNetId,
                INetd.PERMISSION_NONE));
        assertRoutesAdded(cellNetId, ipv6Subnet, ipv6Default);

        // Clatd is started and clat iface comes up. Expect stacked link to be added.
        verifyClatdStart(null /* inOrder */, MOBILE_IFNAME, cellNetId, kNat64Prefix.toString());
        netdUnsolicitedListener.onInterfaceLinkStateChanged(CLAT_MOBILE_IFNAME, true /* up */);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                cb -> cb.getLp().getStackedLinks().size() == 1
                        && cb.getLp().getNat64Prefix().equals(kNat64Prefix));
        verify(mMockNetd).networkAddInterface(cellNetId, CLAT_MOBILE_IFNAME);
        // assertRoutesAdded sees all calls since last mMockNetd reset, so expect IPv6 routes again.
        assertRoutesAdded(cellNetId, ipv6Subnet, ipv6Default, stackedDefault);

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(MOBILE_IFNAME, true);
        }

        reset(mMockNetd);
        reset(mClatCoordinator);

        // Disconnect the network. clat is stopped and the network is destroyed.
        mCellAgent.disconnect();
        networkCallback.expect(LOST, mCellAgent);
        networkCallback.assertNoCallback();
        verifyClatdStop(null /* inOrder */, MOBILE_IFNAME);

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(CLAT_MOBILE_IFNAME, false);
        }

        verify(mMockNetd).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                eq(Integer.toString(getIdleTimerLabel(cellNetId, TRANSPORT_CELLULAR))));
        verify(mMockNetd).networkDestroy(cellNetId);
        if (mDeps.isAtLeastU()) {
            verify(mMockNetd).setNetworkAllowlist(any());
        } else {
            verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        if (mDeps.isAtLeastU()) {
            verifyWakeupModifyInterface(MOBILE_IFNAME, false);
        }

        verifyNoMoreInteractions(mMockNetd);
        verifyNoMoreInteractions(mClatCoordinator);

        mCm.unregisterNetworkCallback(networkCallback);
    }

    private void expectNat64PrefixChange(TestNetworkCallback callback,
            TestNetworkAgentWrapper agent, IpPrefix prefix) {
        callback.expect(LINK_PROPERTIES_CHANGED, agent,
                x -> Objects.equals(x.getLp().getNat64Prefix(), prefix));
    }

    @Test
    public void testNat64PrefixMultipleSources() throws Exception {
        final String iface = "wlan0";
        final String pref64FromRaStr = "64:ff9b::";
        final String pref64FromDnsStr = "2001:db8:64::";
        final IpPrefix pref64FromRa = new IpPrefix(InetAddress.getByName(pref64FromRaStr), 96);
        final IpPrefix pref64FromDns = new IpPrefix(InetAddress.getByName(pref64FromDnsStr), 96);
        final IpPrefix newPref64FromRa = new IpPrefix("2001:db8:64:64:64:64::/96");

        final NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, callback);

        final LinkProperties baseLp = new LinkProperties();
        baseLp.setInterfaceName(iface);
        baseLp.addLinkAddress(new LinkAddress("2001:db8:1::1/64"));
        baseLp.addDnsServer(InetAddress.getByName("2001:4860:4860::6464"));

        reset(mMockNetd, mMockDnsResolver);
        InOrder inOrder = inOrder(mMockNetd, mMockDnsResolver, mClatCoordinator);

        // If a network already has a NAT64 prefix on connect, clatd is started immediately and
        // prefix discovery is never started.
        LinkProperties lp = new LinkProperties(baseLp);
        lp.setNat64Prefix(pref64FromRa);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        mWiFiAgent.connect(false);
        final Network network = mWiFiAgent.getNetwork();
        int netId = network.getNetId();
        callback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        verifyClatdStart(inOrder, iface, netId, pref64FromRa.toString());
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, pref64FromRa.toString());
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);
        callback.assertNoCallback();
        assertEquals(pref64FromRa, mCm.getLinkProperties(network).getNat64Prefix());

        // If the RA prefix is withdrawn, clatd is stopped and prefix discovery is started.
        lp.setNat64Prefix(null);
        mWiFiAgent.sendLinkProperties(lp);
        expectNat64PrefixChange(callback, mWiFiAgent, null);
        verifyClatdStop(inOrder, iface);
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, "");
        inOrder.verify(mMockDnsResolver).startPrefix64Discovery(netId);

        // If the RA prefix appears while DNS discovery is in progress, discovery is stopped and
        // clatd is started with the prefix from the RA.
        lp.setNat64Prefix(pref64FromRa);
        mWiFiAgent.sendLinkProperties(lp);
        expectNat64PrefixChange(callback, mWiFiAgent, pref64FromRa);
        verifyClatdStart(inOrder, iface, netId, pref64FromRa.toString());
        inOrder.verify(mMockDnsResolver).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, pref64FromRa.toString());

        // Withdraw the RA prefix so we can test the case where an RA prefix appears after DNS
        // discovery has succeeded.
        lp.setNat64Prefix(null);
        mWiFiAgent.sendLinkProperties(lp);
        expectNat64PrefixChange(callback, mWiFiAgent, null);
        verifyClatdStop(inOrder, iface);
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, "");
        inOrder.verify(mMockDnsResolver).startPrefix64Discovery(netId);

        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(
                makeNat64PrefixEvent(netId, PREFIX_OPERATION_ADDED, pref64FromDnsStr, 96));
        expectNat64PrefixChange(callback, mWiFiAgent, pref64FromDns);
        verifyClatdStart(inOrder, iface, netId, pref64FromDns.toString());

        // If an RA advertises the same prefix that was discovered by DNS, nothing happens: prefix
        // discovery is not stopped, and there are no callbacks.
        lp.setNat64Prefix(pref64FromDns);
        mWiFiAgent.sendLinkProperties(lp);
        callback.assertNoCallback();
        verifyNeverClatdStop(inOrder, iface);
        verifyNeverClatdStart(inOrder, iface);
        inOrder.verify(mMockDnsResolver, never()).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).setPrefix64(eq(netId), anyString());

        // If the RA is later withdrawn, nothing happens again.
        lp.setNat64Prefix(null);
        mWiFiAgent.sendLinkProperties(lp);
        callback.assertNoCallback();
        verifyNeverClatdStop(inOrder, iface);
        verifyNeverClatdStart(inOrder, iface);
        inOrder.verify(mMockDnsResolver, never()).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).setPrefix64(eq(netId), anyString());

        // If the RA prefix changes, clatd is restarted and prefix discovery is stopped.
        lp.setNat64Prefix(pref64FromRa);
        mWiFiAgent.sendLinkProperties(lp);
        expectNat64PrefixChange(callback, mWiFiAgent, pref64FromRa);
        verifyClatdStop(inOrder, iface);
        inOrder.verify(mMockDnsResolver).stopPrefix64Discovery(netId);

        // Stopping prefix discovery results in a prefix removed notification.
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(
                makeNat64PrefixEvent(netId, PREFIX_OPERATION_REMOVED, pref64FromDnsStr, 96));

        verifyClatdStart(inOrder, iface, netId, pref64FromRa.toString());
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, pref64FromRa.toString());
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);

        // If the RA prefix changes, clatd is restarted and prefix discovery is not started.
        lp.setNat64Prefix(newPref64FromRa);
        mWiFiAgent.sendLinkProperties(lp);
        expectNat64PrefixChange(callback, mWiFiAgent, newPref64FromRa);
        verifyClatdStop(inOrder, iface);
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, "");
        verifyClatdStart(inOrder, iface, netId, newPref64FromRa.toString());
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, newPref64FromRa.toString());
        inOrder.verify(mMockDnsResolver, never()).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);

        // If the RA prefix changes to the same value, nothing happens.
        lp.setNat64Prefix(newPref64FromRa);
        mWiFiAgent.sendLinkProperties(lp);
        callback.assertNoCallback();
        assertEquals(newPref64FromRa, mCm.getLinkProperties(network).getNat64Prefix());
        verifyNeverClatdStop(inOrder, iface);
        verifyNeverClatdStart(inOrder, iface);
        inOrder.verify(mMockDnsResolver, never()).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).setPrefix64(eq(netId), anyString());

        // The transition between no prefix and DNS prefix is tested in testStackedLinkProperties.

        // If the same prefix is learned first by DNS and then by RA, and clat is later stopped,
        // (e.g., because the network disconnects) setPrefix64(netid, "") is never called.
        lp.setNat64Prefix(null);
        mWiFiAgent.sendLinkProperties(lp);
        expectNat64PrefixChange(callback, mWiFiAgent, null);
        verifyClatdStop(inOrder, iface);
        inOrder.verify(mMockDnsResolver).setPrefix64(netId, "");
        inOrder.verify(mMockDnsResolver).startPrefix64Discovery(netId);
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(
                makeNat64PrefixEvent(netId, PREFIX_OPERATION_ADDED, pref64FromDnsStr, 96));
        expectNat64PrefixChange(callback, mWiFiAgent, pref64FromDns);
        verifyClatdStart(inOrder, iface, netId, pref64FromDns.toString());
        inOrder.verify(mMockDnsResolver, never()).setPrefix64(eq(netId), any());

        lp.setNat64Prefix(pref64FromDns);
        mWiFiAgent.sendLinkProperties(lp);
        callback.assertNoCallback();
        verifyNeverClatdStop(inOrder, iface);
        verifyNeverClatdStart(inOrder, iface);
        inOrder.verify(mMockDnsResolver, never()).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).startPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).setPrefix64(eq(netId), anyString());

        // When tearing down a network, clat state is only updated after CALLBACK_LOST is fired, but
        // before CONNECTIVITY_ACTION is sent. Wait for CONNECTIVITY_ACTION before verifying that
        // clat has been stopped, or the test will be flaky.
        ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.DISCONNECTED);
        mWiFiAgent.disconnect();
        callback.expect(LOST, mWiFiAgent);
        b.expectBroadcast();

        verifyClatdStop(inOrder, iface);
        inOrder.verify(mMockDnsResolver).stopPrefix64Discovery(netId);
        inOrder.verify(mMockDnsResolver, never()).setPrefix64(eq(netId), anyString());

        mCm.unregisterNetworkCallback(callback);
    }

    @Test
    public void testWith464XlatDisable() throws Exception {
        mDeps.setCellular464XlatEnabled(false);

        final TestNetworkCallback callback = new TestNetworkCallback();
        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        mCm.registerNetworkCallback(networkRequest, callback);
        mCm.registerDefaultNetworkCallback(defaultCallback);

        // Bring up validated cell.
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        cellLp.addLinkAddress(new LinkAddress("2001:db8:1::1/64"));
        cellLp.addRoute(new RouteInfo(new IpPrefix("::/0"), null, MOBILE_IFNAME));
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);

        mCellAgent.sendLinkProperties(cellLp);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        defaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        final int cellNetId = mCellAgent.getNetwork().netId;
        waitForIdle();

        verify(mMockDnsResolver, never()).startPrefix64Discovery(cellNetId);
        Nat464Xlat clat = getNat464Xlat(mCellAgent);
        assertTrue("Nat464Xlat was not IDLE", !clat.isStarted());

        // This cannot happen because prefix discovery cannot succeed if it is never started.
        mService.mResolverUnsolEventCallback.onNat64PrefixEvent(
                makeNat64PrefixEvent(cellNetId, PREFIX_OPERATION_ADDED, "64:ff9b::", 96));

        // ... but still, check that even if it did, clatd would not be started.
        verify(mMockNetd, never()).clatdStart(anyString(), anyString());
        assertTrue("Nat464Xlat was not IDLE", !clat.isStarted());
    }

    final String transportToTestIfaceName(int transport) {
        switch (transport) {
            case TRANSPORT_WIFI:
                return WIFI_IFNAME;
            case TRANSPORT_CELLULAR:
                return MOBILE_IFNAME;
            case TRANSPORT_ETHERNET:
                return ETHERNET_IFNAME;
            default:
                throw new AssertionError("Unsupported transport type");
        }
    }

    private void doTestInterfaceClassActivityChanged(final int transportType) throws Exception {
        final BaseNetdUnsolicitedEventListener netdUnsolicitedEventListener =
                getRegisteredNetdUnsolicitedEventListener();

        final int legacyType = transportToLegacyType(transportType);
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(transportToTestIfaceName(transportType));
        final TestNetworkAgentWrapper agent = new TestNetworkAgentWrapper(transportType, lp);

        final ConditionVariable onNetworkActiveCv = new ConditionVariable();
        final ConnectivityManager.OnNetworkActiveListener listener = onNetworkActiveCv::open;

        TestNetworkCallback defaultCallback = new TestNetworkCallback();

        testAndCleanup(() -> {
            mCm.registerDefaultNetworkCallback(defaultCallback);
            agent.connect(true);
            defaultCallback.expectAvailableThenValidatedCallbacks(agent);
            if (transportType == TRANSPORT_CELLULAR) {
                verify(mIBatteryStats).noteMobileRadioPowerState(eq(DC_POWER_STATE_HIGH),
                        anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID));
            } else if (transportType == TRANSPORT_WIFI) {
                verify(mIBatteryStats).noteWifiRadioPowerState(eq(DC_POWER_STATE_HIGH),
                        anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID));
            }
            clearInvocations(mIBatteryStats);
            final int idleTimerLabel = getIdleTimerLabel(agent.getNetwork().netId, transportType);

            // Network is considered active when the network becomes the default network.
            assertTrue(mCm.isDefaultNetworkActive());

            mCm.addDefaultNetworkActiveListener(listener);

            // Interface goes to inactive state
            netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                    idleTimerLabel, TIMESTAMP, NETWORK_ACTIVITY_NO_UID);
            mServiceContext.expectDataActivityBroadcast(legacyType, false /* isActive */,
                    TIMESTAMP);
            assertFalse(onNetworkActiveCv.block(TEST_CALLBACK_TIMEOUT_MS));
            assertFalse(mCm.isDefaultNetworkActive());
            if (mDeps.isAtLeastV()) {
                if (transportType == TRANSPORT_CELLULAR) {
                    verify(mIBatteryStats).noteMobileRadioPowerState(eq(DC_POWER_STATE_LOW),
                            anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID));
                } else if (transportType == TRANSPORT_WIFI) {
                    verify(mIBatteryStats).noteWifiRadioPowerState(eq(DC_POWER_STATE_LOW),
                            anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID));
                }
            } else {
                // If TrackMultiNetworks is disabled, LegacyNetworkActivityTracker does not call
                // BatteryStats API by the netd activity change callback since BatteryStatsService
                // listen to netd callback via NetworkManagementService and update battery stats by
                // itself.
                verify(mIBatteryStats, never())
                        .noteMobileRadioPowerState(anyInt(), anyLong(), anyInt());
                verify(mIBatteryStats, never())
                        .noteWifiRadioPowerState(anyInt(), anyLong(), anyInt());
            }

            // Interface goes to active state
            netdUnsolicitedEventListener.onInterfaceClassActivityChanged(true /* isActive */,
                    idleTimerLabel, TIMESTAMP, TEST_PACKAGE_UID);
            mServiceContext.expectDataActivityBroadcast(legacyType, true /* isActive */, TIMESTAMP);
            assertTrue(onNetworkActiveCv.block(TEST_CALLBACK_TIMEOUT_MS));
            assertTrue(mCm.isDefaultNetworkActive());
            if (mDeps.isAtLeastV()) {
                if (transportType == TRANSPORT_CELLULAR) {
                    verify(mIBatteryStats).noteMobileRadioPowerState(eq(DC_POWER_STATE_HIGH),
                            anyLong() /* timestampNs */, eq(TEST_PACKAGE_UID));
                } else if (transportType == TRANSPORT_WIFI) {
                    verify(mIBatteryStats).noteWifiRadioPowerState(eq(DC_POWER_STATE_HIGH),
                            anyLong() /* timestampNs */, eq(TEST_PACKAGE_UID));
                }
            } else {
                // If TrackMultiNetworks is disabled, LegacyNetworkActivityTracker does not call
                // BatteryStats API by the netd activity change callback since BatteryStatsService
                // listen to netd callback via NetworkManagementService and update battery stats by
                // itself.
                verify(mIBatteryStats, never())
                        .noteMobileRadioPowerState(anyInt(), anyLong(), anyInt());
                verify(mIBatteryStats, never())
                        .noteWifiRadioPowerState(anyInt(), anyLong(), anyInt());
            }
        }, () -> { // Cleanup
                mCm.unregisterNetworkCallback(defaultCallback);
            }, () -> { // Cleanup
                mCm.removeDefaultNetworkActiveListener(listener);
            }, () -> { // Cleanup
                agent.disconnect();
            });
    }

    @Test
    public void testInterfaceClassActivityChangedWifi() throws Exception {
        doTestInterfaceClassActivityChanged(TRANSPORT_WIFI);
    }

    @Test
    public void testInterfaceClassActivityChangedCellular() throws Exception {
        doTestInterfaceClassActivityChanged(TRANSPORT_CELLULAR);
    }

    private void doTestOnNetworkActive_NewNetworkConnects(int transportType, boolean expectCapChanged)
            throws Exception {
        final ConditionVariable onNetworkActiveCv = new ConditionVariable();
        final ConnectivityManager.OnNetworkActiveListener listener = onNetworkActiveCv::open;

        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(transportToTestIfaceName(transportType));
        final TestNetworkAgentWrapper agent = new TestNetworkAgentWrapper(transportType, lp);

        testAndCleanup(() -> {
            mCm.addDefaultNetworkActiveListener(listener);
            agent.connect(true);
            if (expectCapChanged) {
                assertTrue(onNetworkActiveCv.block(TEST_CALLBACK_TIMEOUT_MS));
            } else {
                assertFalse(onNetworkActiveCv.block(TEST_CALLBACK_TIMEOUT_MS));
            }
            assertTrue(mCm.isDefaultNetworkActive());
        }, () -> { // Cleanup
                mCm.removeDefaultNetworkActiveListener(listener);
            }, () -> { // Cleanup
                agent.disconnect();
            });
    }

    @Test
    public void testOnNetworkActive_NewCellConnects_CallbackCalled() throws Exception {
        doTestOnNetworkActive_NewNetworkConnects(TRANSPORT_CELLULAR, true /* expectCapChanged */);
    }

    @Test
    public void testOnNetworkActive_NewEthernetConnects_Callback() throws Exception {
        // On pre-V devices, LegacyNetworkActivityTracker calls onNetworkActive callback only for
        // networks that tracker adds the idle timer to. And the tracker does not set the idle timer
        // for the ethernet network.
        // So onNetworkActive is not called when the ethernet becomes the default network
        final boolean expectCapChanged = mDeps.isAtLeastV();
        doTestOnNetworkActive_NewNetworkConnects(TRANSPORT_ETHERNET, expectCapChanged);
    }

    @Test
    public void testIsDefaultNetworkActiveNoDefaultNetwork() throws Exception {
        // isDefaultNetworkActive returns true if there is no default network, which is known issue.
        assertTrue(mCm.isDefaultNetworkActive());

        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(true);
        // Network is considered active when the network becomes the default network.
        assertTrue(mCm.isDefaultNetworkActive());

        mCellAgent.disconnect();
        waitForIdle();

        assertTrue(mCm.isDefaultNetworkActive());
    }

    @Test
    public void testDataActivityTracking() throws Exception {
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        mCm.registerNetworkCallback(networkRequest, networkCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        final String cellIdleTimerLabel = Integer.toString(getIdleTimerLabel(
                mCellAgent.getNetwork().netId, TRANSPORT_CELLULAR));
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent.sendLinkProperties(cellLp);
        mCellAgent.connect(true);
        networkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        verify(mMockNetd, times(1)).idletimerAddInterface(eq(MOBILE_IFNAME), anyInt(),
                eq(cellIdleTimerLabel));

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        String wifiIdleTimerLabel = Integer.toString(getIdleTimerLabel(
                mWiFiAgent.getNetwork().netId, TRANSPORT_WIFI));
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent.sendLinkProperties(wifiLp);

        // Network switch
        mWiFiAgent.connect(true);
        networkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        networkCallback.expectLosing(mCellAgent);
        networkCallback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        verify(mMockNetd, times(1)).idletimerAddInterface(eq(WIFI_IFNAME), anyInt(),
                eq(wifiIdleTimerLabel));
        if (mDeps.isAtLeastV()) {
            // V+ devices add idleTimer when the network is first connected and remove when the
            // network is disconnected.
            verify(mMockNetd, never()).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(mCellAgent.getNetwork().netId)));
        } else {
            // pre V devices add idleTimer when the network becomes the default network and remove
            // when the network becomes no longer the default network.
            verify(mMockNetd, times(1)).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(TRANSPORT_CELLULAR)));
        }

        // Disconnect wifi and switch back to cell
        reset(mMockNetd);
        mWiFiAgent.disconnect();
        networkCallback.expect(LOST, mWiFiAgent);
        assertNoCallbacks(networkCallback);
        verify(mMockNetd, times(1)).idletimerRemoveInterface(eq(WIFI_IFNAME), anyInt(),
                eq(wifiIdleTimerLabel));
        if (mDeps.isAtLeastV()) {
            verify(mMockNetd, never()).idletimerAddInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(mCellAgent.getNetwork().netId)));
        } else {
            verify(mMockNetd, times(1)).idletimerAddInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(TRANSPORT_CELLULAR)));
        }

        // reconnect wifi
        reset(mMockNetd);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        wifiIdleTimerLabel = Integer.toString(getIdleTimerLabel(
                mWiFiAgent.getNetwork().netId, TRANSPORT_WIFI));
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent.sendLinkProperties(wifiLp);
        mWiFiAgent.connect(true);
        networkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        networkCallback.expectLosing(mCellAgent);
        networkCallback.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        verify(mMockNetd, times(1)).idletimerAddInterface(eq(WIFI_IFNAME), anyInt(),
                eq(wifiIdleTimerLabel));
        if (mDeps.isAtLeastV()) {
            verify(mMockNetd, never()).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(mCellAgent.getNetwork().netId)));
        } else {
            verify(mMockNetd, times(1)).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(TRANSPORT_CELLULAR)));
        }

        // Disconnect cell
        reset(mMockNetd);
        mCellAgent.disconnect();
        networkCallback.expect(LOST, mCellAgent);
        waitForIdle();
        if (mDeps.isAtLeastV()) {
            verify(mMockNetd, times(1)).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(mCellAgent.getNetwork().netId)));
        }  else {
            // LOST callback is triggered earlier than removing idle timer. Broadcast should also be
            // sent as network being switched. Ensure rule removal for cell will not be triggered
            // unexpectedly before network being removed.
            verify(mMockNetd, times(0)).idletimerRemoveInterface(eq(MOBILE_IFNAME), anyInt(),
                    eq(Integer.toString(TRANSPORT_CELLULAR)));
        }
        verify(mMockNetd, times(1)).networkDestroy(eq(mCellAgent.getNetwork().netId));
        verify(mMockDnsResolver, times(1)).destroyNetworkCache(eq(mCellAgent.getNetwork().netId));

        // Disconnect wifi
        ExpectedBroadcast b = expectConnectivityAction(TYPE_WIFI, DetailedState.DISCONNECTED);
        mWiFiAgent.disconnect();
        b.expectBroadcast();
        verify(mMockNetd, times(1)).idletimerRemoveInterface(eq(WIFI_IFNAME), anyInt(),
                eq(wifiIdleTimerLabel));

        // Clean up
        mCm.unregisterNetworkCallback(networkCallback);
    }

    @Test
    public void testDataActivityTracking_VpnNetwork() throws Exception {
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true /* validated */);
        mMockVpn.setUnderlyingNetworks(new Network[] { mWiFiAgent.getNetwork() });

        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(VPN_IFNAME);
        mMockVpn.establishForMyUid(lp);

        // NetworkActivityTracker should not track the VPN network since VPN can change the
        // underlying network without disconnect.
        verify(mMockNetd, never()).idletimerAddInterface(eq(VPN_IFNAME), anyInt(), any());
    }

    private void verifyTcpBufferSizeChange(String tcpBufferSizes) throws Exception {
        String[] values = tcpBufferSizes.split(",");
        String rmemValues = String.join(" ", values[0], values[1], values[2]);
        String wmemValues = String.join(" ", values[3], values[4], values[5]);
        verify(mMockNetd, atLeastOnce()).setTcpRWmemorySize(rmemValues, wmemValues);
        reset(mMockNetd);
    }

    @Test
    public void testTcpBufferReset() throws Exception {
        final String testTcpBufferSizes = "1,2,3,4,5,6";
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(networkRequest, networkCallback);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        reset(mMockNetd);
        // Switching default network updates TCP buffer sizes.
        mCellAgent.connect(false);
        networkCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        verifyTcpBufferSizeChange(ConnectivityService.DEFAULT_TCP_BUFFER_SIZES);
        // Change link Properties should have updated tcp buffer size.
        LinkProperties lp = new LinkProperties();
        lp.setTcpBufferSizes(testTcpBufferSizes);
        mCellAgent.sendLinkProperties(lp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent);
        verifyTcpBufferSizeChange(testTcpBufferSizes);
        // Clean up.
        mCellAgent.disconnect();
        networkCallback.expect(LOST, mCellAgent);
        networkCallback.assertNoCallback();
        mCm.unregisterNetworkCallback(networkCallback);
    }

    @Test
    public void testGetGlobalProxyForNetwork() throws Exception {
        final ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("test", 8888);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        final Network wifiNetwork = mWiFiAgent.getNetwork();
        mProxyTracker.setGlobalProxy(testProxyInfo);
        assertEquals(testProxyInfo, mService.getProxyForNetwork(wifiNetwork));
    }

    @Test
    public void testGetProxyForActiveNetwork() throws Exception {
        final ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("test", 8888);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        waitForIdle();
        assertNull(mService.getProxyForNetwork(null));

        final LinkProperties testLinkProperties = new LinkProperties();
        testLinkProperties.setHttpProxy(testProxyInfo);

        mWiFiAgent.sendLinkProperties(testLinkProperties);
        waitForIdle();

        assertEquals(testProxyInfo, mService.getProxyForNetwork(null));
    }

    /*
     * Note for maintainers about how PAC proxies are implemented in Android.
     *
     * Generally, a proxy is just a hostname and a port to which requests are sent, instead of
     * sending them directly. Most HTTP libraries know to use this protocol, and the Java
     * language has properties to help handling these :
     *   https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html
     * Unfortunately these properties are very old and do not take multi-networking into account.
     *
     * A PAC proxy consists of a javascript file stored on a server, and the client is expected to
     * download the file and interpret the javascript for each HTTP request to know where to direct
     * it. The device must therefore run code (namely, a javascript interpreter) to interpret the
     * PAC file correctly. Most HTTP libraries do not know how to do this, since they do not
     * embark a javascript interpreter (and it would be generally unreasonable for them to do
     * so). Some apps, notably browsers, do know how to do this, but they are the exception rather
     * than the rule.
     * So to support most apps, Android embarks the javascript interpreter. When a network is
     * configured to have a PAC proxy, Android will first set the ProxyInfo object to an object
     * that contains the PAC URL (to communicate that to apps that know how to use it), then
     * download the PAC file and start a native process which will open a server on localhost,
     * and uses the interpreter inside WebView to interpret the PAC file. This server takes
     * a little bit of time to start and will listen on a random port. When the port is known,
     * the framework receives a notification and it updates the ProxyInfo in LinkProperties
     * as well as send a broadcast to inform apps. This new ProxyInfo still contains the PAC URL,
     * but it also contains "localhost" as the host and the port that the server listens to as
     * the port. This will let HTTP libraries that don't have a javascript interpreter work,
     * because they'll send the requests to this server running on localhost on the correct port,
     * and this server will do what is appropriate with each request according to the PAC file.
     *
     * Note that at the time of this writing, Android does not support starting multiple local
     * PAC servers, though this would be possible in theory by just starting multiple instances
     * of this process running their server on different ports. As a stopgap measure, Android
     * keeps one local server which is always the one for the default network. If a non-default
     * network has a PAC proxy, it will have a LinkProperties with a port of -1, which means it
     * could still be used by apps that know how to use the PAC URL directly, but not by HTTP
     * libs that don't know how to do that. When a network with a PAC proxy becomes the default,
     * the local server is started. When a network without a PAC proxy becomes the default, the
     * local server is stopped if it is running (and the LP for the old default network should
     * be reset to have a port of -1).
     *
     * In principle, each network can be configured with a different proxy (typically in the
     * network settings for a Wi-Fi network). These end up exposed in the LinkProperties of the
     * relevant network.
     * Android also exposes ConnectivityManager#getDefaultProxy, which is simply the proxy for
     * the default network. This was retrofitted from a time where Android did not support multiple
     * concurrent networks, hence the difficult architecture.
     * Note that there is also a "global" proxy that can be set by the DPM. When this is set,
     * it overrides the proxy settings for every single network at the same time – that is, the
     * system behaves as if the global proxy is the configured proxy for each network.
     *
     * Generally there are four ways for apps to look up the proxy.
     * - Looking up the ProxyInfo in the LinkProperties for a network.
     * - Listening to the PROXY_CHANGED_ACTION broadcast
     * - Calling ConnectivityManager#getDefaultProxy, or ConnectivityManager#getProxyForNetwork
     *   which can be used to retrieve the proxy for any given network or the default network by
     *   passing null.
     * - Reading the standard JVM properties (http{s,}.proxy{Host,Port}). See the Java
     *   distribution documentation for details on how these are supposed to work :
     *    https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html
     *   In Android, these are set by ActivityThread in each process in response to the broadcast.
     *   Many apps actually use these, and it's important they work because it's part of the
     *   Java standard, meaning they need to be set for existing Java libs to work on Android.
     */
    @Test
    public void testPacProxy() throws Exception {
        final Uri pacUrl = Uri.parse("https://pac-url");

        final TestNetworkCallback cellCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        // Request cell to make sure it doesn't disconnect at an arbitrary point in the test,
        // which would make testing the callbacks on it difficult.
        mCm.requestNetwork(cellRequest, cellCallback);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        final TestNetworkCallback wifiCallback = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(wifiRequest, wifiCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        wifiCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        cellCallback.assertNoCallback();

        final ProxyInfo initialProxyInfo = ProxyInfo.buildPacProxy(pacUrl);
        final LinkProperties testLinkProperties = new LinkProperties();
        testLinkProperties.setHttpProxy(initialProxyInfo);
        mWiFiAgent.sendLinkProperties(testLinkProperties);
        wifiCallback.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, mWiFiAgent);
        cellCallback.assertNoCallback();

        // At first the local PAC proxy server is unstarted (see the description of what the local
        // server is in the comment at the top of this method). It will contain the PAC URL and a
        // port of -1 because it is unstarted. Check that all ways of getting that proxy info
        // returns the same object that was initially created.
        final ProxyInfo unstartedDefaultProxyInfo = mService.getProxyForNetwork(null);
        final ProxyInfo unstartedWifiProxyInfo = mService.getProxyForNetwork(
                mWiFiAgent.getNetwork());
        final LinkProperties unstartedLp =
                mService.getLinkProperties(mWiFiAgent.getNetwork());

        assertEquals(initialProxyInfo, unstartedDefaultProxyInfo);
        assertEquals(initialProxyInfo, unstartedWifiProxyInfo);
        assertEquals(initialProxyInfo, unstartedLp.getHttpProxy());

        // Make sure the cell network is unaffected. The LP are per-network and each network has
        // its own configuration. The default proxy and broadcast are system-wide, and the JVM
        // properties are per-process, and therefore can have only one value at any given time,
        // so the code sets them to the proxy of the default network (TODO : really, since the
        // default process is per-network, the JVM properties (http.proxyHost family – see
        // the comment at the top of the method for details about these) also should be per-network
        // and even the broadcast contents should be but none of this is implemented). The LP are
        // still per-network, and a process that wants to use a non-default network is supposed to
        // look up the proxy in its LP and it has to be correct.
        assertNull(mService.getLinkProperties(mCellAgent.getNetwork()).getHttpProxy());
        assertNull(mService.getProxyForNetwork(mCellAgent.getNetwork()));

        // Simulate PacManager sending the notification that the local server has started
        final ProxyInfo servingProxyInfo = new ProxyInfo(pacUrl, 2097);
        final ExpectedBroadcast servingProxyBroadcast = expectProxyChangeAction(servingProxyInfo);
        mService.simulateUpdateProxyInfo(mWiFiAgent.getNetwork(), servingProxyInfo);
        wifiCallback.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, mWiFiAgent);
        cellCallback.assertNoCallback();
        servingProxyBroadcast.expectBroadcast();

        final ProxyInfo startedDefaultProxyInfo = mService.getProxyForNetwork(null);
        final ProxyInfo startedWifiProxyInfo = mService.getProxyForNetwork(
                mWiFiAgent.getNetwork());
        final LinkProperties startedLp = mService.getLinkProperties(mWiFiAgent.getNetwork());
        assertEquals(servingProxyInfo, startedDefaultProxyInfo);
        assertEquals(servingProxyInfo, startedWifiProxyInfo);
        assertEquals(servingProxyInfo, startedLp.getHttpProxy());
        // Make sure the cell network is still unaffected
        assertNull(mService.getLinkProperties(mCellAgent.getNetwork()).getHttpProxy());
        assertNull(mService.getProxyForNetwork(mCellAgent.getNetwork()));

        // Start an ethernet network which will become the default, in order to test what happens
        // to the proxy of wifi while manipulating the proxy of ethernet.
        final Uri ethPacUrl = Uri.parse("https://ethernet-pac-url");
        final TestableNetworkCallback ethernetCallback = new TestableNetworkCallback();
        final NetworkRequest ethernetRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_ETHERNET).build();
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mCm.registerNetworkCallback(ethernetRequest, ethernetCallback);
        mEthernetAgent.connect(true);
        ethernetCallback.expectAvailableThenValidatedCallbacks(mEthernetAgent);

        // Wifi is no longer the default, so it should get a callback to LP changed with a PAC
        // proxy but a port of -1 (since the local proxy doesn't serve wifi now)
        wifiCallback.expect(LINK_PROPERTIES_CHANGED, mWiFiAgent,
                lp -> lp.getLp().getHttpProxy().getPort() == -1
                        && lp.getLp().getHttpProxy().isPacProxy());
        // Wifi is lingered as it was the default but is no longer serving any request.
        wifiCallback.expect(CallbackEntry.LOSING, mWiFiAgent);

        // Now arrange for Ethernet to have a PAC proxy.
        final ProxyInfo ethProxy = ProxyInfo.buildPacProxy(ethPacUrl);
        final LinkProperties ethLinkProperties = new LinkProperties();
        ethLinkProperties.setHttpProxy(ethProxy);
        mEthernetAgent.sendLinkProperties(ethLinkProperties);
        ethernetCallback.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, mEthernetAgent);
        // Default network is Ethernet
        assertEquals(ethProxy, mService.getProxyForNetwork(null));
        assertEquals(ethProxy, mService.getProxyForNetwork(mEthernetAgent.getNetwork()));
        // Proxy info for WiFi ideally should be the old one with the old server still running,
        // but as the PAC code only handles one server at any given time, this can't work. Wifi
        // having null as a proxy also won't work (apps using WiFi will try to access the network
        // without proxy) but is not as bad as having the new proxy (that would send requests
        // over the default network).
        assertEquals(initialProxyInfo, mService.getProxyForNetwork(mWiFiAgent.getNetwork()));
        assertNull(mService.getProxyForNetwork(mCellAgent.getNetwork()));

        // Expect the local PAC proxy server starts to serve the proxy on Ethernet. Use
        // simulateUpdateProxyInfo to simulate this, and check that the callback is called to
        // inform apps of the port and that the various networks have the expected proxies in
        // their link properties.
        final ProxyInfo servingEthProxy = new ProxyInfo(ethPacUrl, 2099);
        final ExpectedBroadcast servingEthProxyBroadcast = expectProxyChangeAction(servingEthProxy);
        final ExpectedBroadcast servingProxyBroadcast2 = expectProxyChangeAction(servingProxyInfo);
        mService.simulateUpdateProxyInfo(mEthernetAgent.getNetwork(), servingEthProxy);
        ethernetCallback.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, mEthernetAgent);
        assertEquals(servingEthProxy, mService.getProxyForNetwork(null));
        assertEquals(servingEthProxy, mService.getProxyForNetwork(mEthernetAgent.getNetwork()));
        assertEquals(initialProxyInfo, mService.getProxyForNetwork(mWiFiAgent.getNetwork()));
        assertNull(mService.getProxyForNetwork(mCellAgent.getNetwork()));
        servingEthProxyBroadcast.expectBroadcast();

        // Ethernet disconnects, back to WiFi
        mEthernetAgent.disconnect();
        ethernetCallback.expect(CallbackEntry.LOST, mEthernetAgent);

        // WiFi is now the default network again. However, the local proxy server does not serve
        // WiFi at this time, so at this time a proxy with port -1 is still the correct value.
        // In time, the local proxy server for ethernet will be downed and the local proxy server
        // for WiFi will be restarted, and WiFi will see an update to its LP with the new port,
        // but in this test this won't happen until the test simulates the local proxy starting
        // up for WiFi (which is done just a few lines below). This is therefore the perfect place
        // to check that WiFi is unaffected until the new local proxy starts up.
        wifiCallback.assertNoCallback();
        assertEquals(initialProxyInfo, mService.getProxyForNetwork(null));
        assertEquals(initialProxyInfo, mService.getProxyForNetwork(mWiFiAgent.getNetwork()));
        assertNull(mService.getProxyForNetwork(mCellAgent.getNetwork()));
        // Note : strictly speaking, for correctness here apps should expect a broadcast with a
        // port of -1, since that's the current default proxy. The code does not send this
        // broadcast. In practice though, not sending it is more useful since the new proxy will
        // start momentarily, so broadcasting and getting all apps to update and retry once now
        // and again in 250ms is kind of counter-productive, so don't fix this bug.

        // After a while the PAC file for wifi is resolved again and the local proxy server
        // starts up. This should cause a LP event to inform clients of the port to access the
        // proxy server for wifi.
        mService.simulateUpdateProxyInfo(mWiFiAgent.getNetwork(), servingProxyInfo);
        wifiCallback.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, mWiFiAgent);
        assertEquals(servingProxyInfo, mService.getProxyForNetwork(null));
        assertEquals(servingProxyInfo, mService.getProxyForNetwork(mWiFiAgent.getNetwork()));
        assertNull(mService.getProxyForNetwork(mCellAgent.getNetwork()));
        servingProxyBroadcast2.expectBroadcast();

        // Expect a broadcast for an empty proxy after wifi disconnected, because cell is now
        // the default network and it doesn't have a proxy. Whether "no proxy" is a null pointer
        // or a ProxyInfo with an empty host doesn't matter because both are correct, so this test
        // accepts both.
        final ExpectedBroadcast emptyProxyBroadcast = expectProxyChangeAction(
                proxy -> proxy == null || TextUtils.isEmpty(proxy.getHost()));
        mWiFiAgent.disconnect();
        emptyProxyBroadcast.expectBroadcast();
        wifiCallback.expect(CallbackEntry.LOST, mWiFiAgent);
        assertNull(mService.getProxyForNetwork(null));
        assertNull(mService.getLinkProperties(mCellAgent.getNetwork()).getHttpProxy());
        assertNull(mService.getGlobalProxy());

        mCm.unregisterNetworkCallback(cellCallback);
    }

    @Test
    public void testPacProxy_NetworkDisconnects_BroadcastSent() throws Exception {
        // Make a WiFi network with a PAC URI.
        final Uri pacUrl = Uri.parse("https://pac-url");
        final ProxyInfo initialProxyInfo = ProxyInfo.buildPacProxy(pacUrl);
        final LinkProperties testLinkProperties = new LinkProperties();
        testLinkProperties.setHttpProxy(initialProxyInfo);

        final TestNetworkCallback wifiCallback = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(wifiRequest, wifiCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, testLinkProperties);
        mWiFiAgent.connect(true);
        // Wifi is up, but the local proxy server hasn't started yet.
        wifiCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);

        // Simulate PacManager sending the notification that the local server has started
        final ProxyInfo servingProxyInfo = new ProxyInfo(pacUrl, 2097);
        final ExpectedBroadcast servingProxyBroadcast = expectProxyChangeAction(servingProxyInfo);
        mService.simulateUpdateProxyInfo(mWiFiAgent.getNetwork(), servingProxyInfo);
        wifiCallback.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, mWiFiAgent);
        servingProxyBroadcast.expectBroadcast();

        // Now disconnect Wi-Fi and make sure there is a broadcast for some empty proxy. Whether
        // the "empty" proxy is a null pointer or a ProxyInfo with an empty host doesn't matter
        // because both are correct, so this test accepts both.
        final ExpectedBroadcast emptyProxyBroadcast = expectProxyChangeAction(
                proxy -> proxy == null || TextUtils.isEmpty(proxy.getHost()));
        mWiFiAgent.disconnect();
        wifiCallback.expect(CallbackEntry.LOST, mWiFiAgent);
        emptyProxyBroadcast.expectBroadcast();
    }

    @Test
    public void testGetProxyForVPN() throws Exception {
        final ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("test", 8888);

        // Set up a WiFi network with no proxy
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        waitForIdle();
        assertNull(mService.getProxyForNetwork(null));

        // Connect a VPN network with a proxy.
        LinkProperties testLinkProperties = new LinkProperties();
        testLinkProperties.setHttpProxy(testProxyInfo);
        mMockVpn.establishForMyUid(testLinkProperties);
        assertUidRangesUpdatedForMyUid(true);

        // Test that the VPN network returns a proxy, and the WiFi does not.
        assertEquals(testProxyInfo, mService.getProxyForNetwork(mMockVpn.getNetwork()));
        assertEquals(testProxyInfo, mService.getProxyForNetwork(null));
        assertNull(mService.getProxyForNetwork(mWiFiAgent.getNetwork()));

        // Test that the VPN network returns no proxy when it is set to null.
        testLinkProperties.setHttpProxy(null);
        mMockVpn.sendLinkProperties(testLinkProperties);
        waitForIdle();
        assertNull(mService.getProxyForNetwork(mMockVpn.getNetwork()));
        assertNull(mService.getProxyForNetwork(null));

        // Set WiFi proxy and check that the vpn proxy is still null.
        testLinkProperties.setHttpProxy(testProxyInfo);
        mWiFiAgent.sendLinkProperties(testLinkProperties);
        waitForIdle();
        assertNull(mService.getProxyForNetwork(null));

        // Disconnect from VPN and check that the active network, which is now the WiFi, has the
        // correct proxy setting.
        mMockVpn.disconnect();
        waitForIdle();
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        assertEquals(testProxyInfo, mService.getProxyForNetwork(mWiFiAgent.getNetwork()));
        assertEquals(testProxyInfo, mService.getProxyForNetwork(null));
    }

    @Test
    public void testFullyRoutedVpnResultsInInterfaceFilteringRules() throws Exception {
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), RTN_UNREACHABLE));
        // The uid range needs to cover the test app so the network is visible to it.
        final Set<UidRange> vpnRange = Collections.singleton(PRIMARY_UIDRANGE);
        mMockVpn.establish(lp, VPN_UID, vpnRange);
        assertVpnUidRangesUpdated(true, vpnRange, VPN_UID);

        // A connected VPN should have interface rules set up. There are two expected invocations,
        // one during the VPN initial connection, one during the VPN LinkProperties update.
        ArgumentCaptor<int[]> uidCaptor = ArgumentCaptor.forClass(int[].class);
        verify(mBpfNetMaps, times(2)).addUidInterfaceRules(eq("tun0"), uidCaptor.capture());
        assertContainsExactly(uidCaptor.getAllValues().get(0), APP1_UID, APP2_UID);
        assertContainsExactly(uidCaptor.getAllValues().get(1), APP1_UID, APP2_UID);

        mMockVpn.disconnect();
        waitForIdle();

        // Disconnected VPN should have interface rules removed
        verify(mBpfNetMaps).removeUidInterfaceRules(uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);
    }

    private void checkInterfaceFilteringRuleWithNullInterface(final LinkProperties lp,
            final int uid) throws Exception {
        // The uid range needs to cover the test app so the network is visible to it.
        final Set<UidRange> vpnRange = Collections.singleton(PRIMARY_UIDRANGE);
        mMockVpn.establish(lp, uid, vpnRange);
        assertVpnUidRangesUpdated(true, vpnRange, uid);

        if (mDeps.isAtLeastT()) {
            // On T and above, VPN should have rules for null interface. Null Interface is a
            // wildcard and this accepts traffic from all the interfaces.
            // There are two expected invocations, one during the VPN initial
            // connection, one during the VPN LinkProperties update.
            ArgumentCaptor<int[]> uidCaptor = ArgumentCaptor.forClass(int[].class);
            verify(mBpfNetMaps, times(2)).addUidInterfaceRules(
                    eq(null) /* iface */, uidCaptor.capture());
            if (uid == VPN_UID) {
                assertContainsExactly(uidCaptor.getAllValues().get(0), APP1_UID, APP2_UID);
                assertContainsExactly(uidCaptor.getAllValues().get(1), APP1_UID, APP2_UID);
            } else {
                assertContainsExactly(uidCaptor.getAllValues().get(0), APP1_UID, APP2_UID, VPN_UID);
                assertContainsExactly(uidCaptor.getAllValues().get(1), APP1_UID, APP2_UID, VPN_UID);
            }

            mMockVpn.disconnect();
            waitForIdle();

            // Disconnected VPN should have interface rules removed
            verify(mBpfNetMaps).removeUidInterfaceRules(uidCaptor.capture());
            if (uid == VPN_UID) {
                assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);
            } else {
                assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID, VPN_UID);
            }
        } else {
            // Before T, rules are not configured for null interface.
            verify(mBpfNetMaps, never()).addUidInterfaceRules(any(), any());
        }
    }

    @Test
    public void testLegacyVpnInterfaceFilteringRule() throws Exception {
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null));
        // Legacy VPN should have interface filtering with null interface.
        checkInterfaceFilteringRuleWithNullInterface(lp, Process.SYSTEM_UID);
    }

    @Test
    public void testLocalIpv4OnlyVpnInterfaceFilteringRule() throws Exception {
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix("192.0.2.0/24"), null, "tun0"));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), RTN_UNREACHABLE));
        // VPN that does not provide a default route should have interface filtering with null
        // interface.
        checkInterfaceFilteringRuleWithNullInterface(lp, VPN_UID);
    }

    @Test
    public void testVpnHandoverChangesInterfaceFilteringRule() throws Exception {
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null));
        // The uid range needs to cover the test app so the network is visible to it.
        final Set<UidRange> vpnRange = Collections.singleton(PRIMARY_UIDRANGE);
        mMockVpn.establish(lp, VPN_UID, vpnRange);
        assertVpnUidRangesUpdated(true, vpnRange, VPN_UID);

        // Connected VPN should have interface rules set up. There are two expected invocations,
        // one during VPN uid update, one during VPN LinkProperties update
        ArgumentCaptor<int[]> uidCaptor = ArgumentCaptor.forClass(int[].class);
        verify(mBpfNetMaps, times(2)).addUidInterfaceRules(eq("tun0"), uidCaptor.capture());
        assertContainsExactly(uidCaptor.getAllValues().get(0), APP1_UID, APP2_UID);
        assertContainsExactly(uidCaptor.getAllValues().get(1), APP1_UID, APP2_UID);

        reset(mBpfNetMaps);
        InOrder inOrder = inOrder(mBpfNetMaps);
        lp.setInterfaceName("tun1");
        mMockVpn.sendLinkProperties(lp);
        waitForIdle();
        // VPN handover (switch to a new interface) should result in rules being updated (old rules
        // removed first, then new rules added)
        inOrder.verify(mBpfNetMaps).removeUidInterfaceRules(uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);
        inOrder.verify(mBpfNetMaps).addUidInterfaceRules(eq("tun1"), uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);

        reset(mBpfNetMaps);
        lp = new LinkProperties();
        lp.setInterfaceName("tun1");
        lp.addRoute(new RouteInfo(new IpPrefix("192.0.2.0/24"), null, "tun1"));
        mMockVpn.sendLinkProperties(lp);
        waitForIdle();
        // VPN not routing everything should no longer have interface filtering rules
        verify(mBpfNetMaps).removeUidInterfaceRules(uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);

        reset(mBpfNetMaps);
        lp = new LinkProperties();
        lp.setInterfaceName("tun1");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), RTN_UNREACHABLE));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null));
        mMockVpn.sendLinkProperties(lp);
        waitForIdle();
        // Back to routing all IPv6 traffic should have filtering rules
        verify(mBpfNetMaps).addUidInterfaceRules(eq("tun1"), uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);
    }

    @Test
    public void testUidUpdateChangesInterfaceFilteringRule() throws Exception {
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), RTN_UNREACHABLE));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null));
        // The uid range needs to cover the test app so the network is visible to it.
        final UidRange vpnRange = PRIMARY_UIDRANGE;
        final Set<UidRange> vpnRanges = Collections.singleton(vpnRange);
        mMockVpn.establish(lp, VPN_UID, vpnRanges);
        assertVpnUidRangesUpdated(true, vpnRanges, VPN_UID);

        reset(mBpfNetMaps);
        InOrder inOrder = inOrder(mBpfNetMaps);

        // Update to new range which is old range minus APP1, i.e. only APP2
        final Set<UidRange> newRanges = new HashSet<>(asList(
                new UidRange(vpnRange.start, APP1_UID - 1),
                new UidRange(APP1_UID + 1, vpnRange.stop)));
        mMockVpn.setUids(newRanges);
        waitForIdle();

        ArgumentCaptor<int[]> uidCaptor = ArgumentCaptor.forClass(int[].class);
        // Verify old rules are removed before new rules are added
        inOrder.verify(mBpfNetMaps).removeUidInterfaceRules(uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP1_UID, APP2_UID);
        inOrder.verify(mBpfNetMaps).addUidInterfaceRules(eq("tun0"), uidCaptor.capture());
        assertContainsExactly(uidCaptor.getValue(), APP2_UID);
    }

    @Test
    public void testLinkPropertiesWithWakeOnLanForActiveNetwork() throws Exception {
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);

        LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_WOL_IFNAME);
        wifiLp.setWakeOnLanSupported(false);

        // Default network switch should update ifaces.
        mWiFiAgent.connect(false);
        mWiFiAgent.sendLinkProperties(wifiLp);
        waitForIdle();

        // ConnectivityService should have changed the WakeOnLanSupported to true
        wifiLp.setWakeOnLanSupported(true);
        assertEquals(wifiLp, mService.getActiveLinkProperties());
    }

    @Test
    public void testLegacyExtraInfoSentToNetworkMonitor() throws Exception {
        class TestNetworkAgent extends NetworkAgent {
            TestNetworkAgent(Context context, Looper looper, NetworkAgentConfig config) {
                super(context, looper, "MockAgent", new NetworkCapabilities(),
                        new LinkProperties(), 40 , config, null /* provider */);
            }
        }
        final NetworkAgent naNoExtraInfo = new TestNetworkAgent(
                mServiceContext, mCsHandlerThread.getLooper(), new NetworkAgentConfig());
        naNoExtraInfo.register();
        verify(mNetworkStack).makeNetworkMonitor(any(), isNull(String.class), any());
        naNoExtraInfo.unregister();

        reset(mNetworkStack);
        final NetworkAgentConfig config =
                new NetworkAgentConfig.Builder().setLegacyExtraInfo("legacyinfo").build();
        final NetworkAgent naExtraInfo = new TestNetworkAgent(
                mServiceContext, mCsHandlerThread.getLooper(), config);
        naExtraInfo.register();
        verify(mNetworkStack).makeNetworkMonitor(any(), eq("legacyinfo"), any());
        naExtraInfo.unregister();
    }

    // To avoid granting location permission bypass.
    private void denyAllLocationPrivilegedPermissions() {
        mServiceContext.setPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                PERMISSION_DENIED);
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_DENIED);
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);
        mServiceContext.setPermission(Manifest.permission.NETWORK_SETUP_WIZARD,
                PERMISSION_DENIED);
    }

    private void setupLocationPermissions(
            int targetSdk, boolean locationToggle, String op, String perm) throws Exception {
        denyAllLocationPrivilegedPermissions();

        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = targetSdk;
        doReturn(applicationInfo).when(mPackageManager)
                .getApplicationInfoAsUser(anyString(), anyInt(), any());
        doReturn(targetSdk).when(mPackageManager).getTargetSdkVersion(any());

        doReturn(locationToggle).when(mLocationManager).isLocationEnabledForUser(any());

        if (op != null) {
            doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).noteOp(
                    eq(op), eq(Process.myUid()), eq(mContext.getPackageName()),
                    eq(getAttributionTag()), anyString());
        }

        if (perm != null) {
            mServiceContext.setPermission(perm, PERMISSION_GRANTED);
        }
    }

    private int getOwnerUidNetCapsPermission(int ownerUid, int callerUid,
            boolean includeLocationSensitiveInfo) {
        final NetworkCapabilities netCap = new NetworkCapabilities().setOwnerUid(ownerUid);

        return mService.createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                netCap, includeLocationSensitiveInfo, Process.myUid(), callerUid,
                mContext.getPackageName(), getAttributionTag())
                .getOwnerUid();
    }

    private void verifyTransportInfoCopyNetCapsPermission(
            int callerUid, boolean includeLocationSensitiveInfo,
            boolean shouldMakeCopyWithLocationSensitiveFieldsParcelable) {
        final TransportInfo transportInfo = mock(TransportInfo.class);
        doReturn(REDACT_FOR_ACCESS_FINE_LOCATION).when(transportInfo).getApplicableRedactions();
        final NetworkCapabilities netCap =
                new NetworkCapabilities().setTransportInfo(transportInfo);

        mService.createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                netCap, includeLocationSensitiveInfo, Process.myPid(), callerUid,
                mContext.getPackageName(), getAttributionTag());
        if (shouldMakeCopyWithLocationSensitiveFieldsParcelable) {
            verify(transportInfo).makeCopy(REDACT_NONE);
        } else {
            verify(transportInfo).makeCopy(REDACT_FOR_ACCESS_FINE_LOCATION);
        }
    }

    private void verifyOwnerUidAndTransportInfoNetCapsPermission(
            boolean shouldInclLocationSensitiveOwnerUidWithoutIncludeFlag,
            boolean shouldInclLocationSensitiveOwnerUidWithIncludeFlag,
            boolean shouldInclLocationSensitiveTransportInfoWithoutIncludeFlag,
            boolean shouldInclLocationSensitiveTransportInfoWithIncludeFlag) {
        final int myUid = Process.myUid();

        final int expectedOwnerUidWithoutIncludeFlag =
                shouldInclLocationSensitiveOwnerUidWithoutIncludeFlag
                        ? myUid : INVALID_UID;
        assertEquals(expectedOwnerUidWithoutIncludeFlag, getOwnerUidNetCapsPermission(
                myUid, myUid, false /* includeLocationSensitiveInfo */));

        final int expectedOwnerUidWithIncludeFlag =
                shouldInclLocationSensitiveOwnerUidWithIncludeFlag ? myUid : INVALID_UID;
        assertEquals(expectedOwnerUidWithIncludeFlag, getOwnerUidNetCapsPermission(
                myUid, myUid, true /* includeLocationSensitiveInfo */));

        verifyTransportInfoCopyNetCapsPermission(myUid,
                false, /* includeLocationSensitiveInfo */
                shouldInclLocationSensitiveTransportInfoWithoutIncludeFlag);

        verifyTransportInfoCopyNetCapsPermission(myUid,
                true, /* includeLocationSensitiveInfo */
                shouldInclLocationSensitiveTransportInfoWithIncludeFlag);

    }

    private void verifyOwnerUidAndTransportInfoNetCapsPermissionPreS() {
        verifyOwnerUidAndTransportInfoNetCapsPermission(
                // Ensure that owner uid is included even if the request asks to remove it (which is
                // the default) since the app has necessary permissions and targetSdk < S.
                true, /* shouldInclLocationSensitiveOwnerUidWithoutIncludeFlag */
                true, /* shouldInclLocationSensitiveOwnerUidWithIncludeFlag */
                // Ensure that location info is removed if the request asks to remove it even if the
                // app has necessary permissions.
                false, /* shouldInclLocationSensitiveTransportInfoWithoutIncludeFlag */
                true /* shouldInclLocationSensitiveTransportInfoWithIncludeFlag */
        );
    }

    @Test
    public void testCreateWithLocationInfoSanitizedWithFineLocationAfterQPreS()
            throws Exception {
        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsPermissionPreS();
    }

    @Test
    public void testCreateWithLocationInfoSanitizedWithFineLocationPreSWithAndWithoutCallbackFlag()
            throws Exception {
        setupLocationPermissions(Build.VERSION_CODES.R, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsPermissionPreS();
    }

    @Test
    public void
            testCreateWithLocationInfoSanitizedWithFineLocationAfterSWithAndWithoutCallbackFlag()
            throws Exception {
        setupLocationPermissions(Build.VERSION_CODES.S, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsPermission(
                // Ensure that the owner UID is removed if the request asks us to remove it even
                // if the app has necessary permissions since targetSdk >= S.
                false, /* shouldInclLocationSensitiveOwnerUidWithoutIncludeFlag */
                true, /* shouldInclLocationSensitiveOwnerUidWithIncludeFlag */
                // Ensure that location info is removed if the request asks to remove it even if the
                // app has necessary permissions.
                false, /* shouldInclLocationSensitiveTransportInfoWithoutIncludeFlag */
                true /* shouldInclLocationSensitiveTransportInfoWithIncludeFlag */
        );
    }

    @Test
    public void testCreateWithLocationInfoSanitizedWithCoarseLocationPreQ()
            throws Exception {
        setupLocationPermissions(Build.VERSION_CODES.P, true, AppOpsManager.OPSTR_COARSE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsPermissionPreS();
    }

    private void verifyOwnerUidAndTransportInfoNetCapsNotIncluded() {
        verifyOwnerUidAndTransportInfoNetCapsPermission(
                false, /* shouldInclLocationSensitiveOwnerUidWithoutIncludeFlag */
                false, /* shouldInclLocationSensitiveOwnerUidWithIncludeFlag */
                false, /* shouldInclLocationSensitiveTransportInfoWithoutIncludeFlag */
                false /* shouldInclLocationSensitiveTransportInfoWithIncludeFlag */
        );
    }

    @Test
    public void testCreateWithLocationInfoSanitizedLocationOff() throws Exception {
        // Test that even with fine location permission, and UIDs matching, the UID is sanitized.
        setupLocationPermissions(Build.VERSION_CODES.Q, false, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsNotIncluded();
    }

    @Test
    public void testCreateWithLocationInfoSanitizedWrongUid() throws Exception {
        // Test that even with fine location permission, not being the owner leads to sanitization.
        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        final int myUid = Process.myUid();
        assertEquals(Process.INVALID_UID,
                getOwnerUidNetCapsPermission(myUid + 1, myUid,
                        true /* includeLocationSensitiveInfo */));
    }

    @Test
    public void testCreateWithLocationInfoSanitizedWithCoarseLocationAfterQ()
            throws Exception {
        // Test that not having fine location permission leads to sanitization.
        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_COARSE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsNotIncluded();
    }

    @Test
    public void testCreateWithLocationInfoSanitizedWithCoarseLocationAfterS()
            throws Exception {
        // Test that not having fine location permission leads to sanitization.
        setupLocationPermissions(Build.VERSION_CODES.S, true, AppOpsManager.OPSTR_COARSE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        verifyOwnerUidAndTransportInfoNetCapsNotIncluded();
    }

    @Test
    public void testCreateForCallerWithLocalMacAddressSanitizedWithLocalMacAddressPermission()
            throws Exception {
        mServiceContext.setPermission(Manifest.permission.LOCAL_MAC_ADDRESS, PERMISSION_GRANTED);

        final TransportInfo transportInfo = mock(TransportInfo.class);
        doReturn(REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_LOCAL_MAC_ADDRESS)
                .when(transportInfo).getApplicableRedactions();
        final NetworkCapabilities netCap =
                new NetworkCapabilities().setTransportInfo(transportInfo);

        mService.createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                netCap, false /* includeLocationSensitiveInfoInTransportInfo */,
                Process.myPid(), Process.myUid(),
                mContext.getPackageName(), getAttributionTag());
        // don't redact MAC_ADDRESS fields, only location sensitive fields.
        verify(transportInfo).makeCopy(REDACT_FOR_ACCESS_FINE_LOCATION);
    }

    @Test
    public void testCreateForCallerWithLocalMacAddressSanitizedWithoutLocalMacAddressPermission()
            throws Exception {
        mServiceContext.setPermission(Manifest.permission.LOCAL_MAC_ADDRESS, PERMISSION_DENIED);

        final TransportInfo transportInfo = mock(TransportInfo.class);
        doReturn(REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_LOCAL_MAC_ADDRESS)
                .when(transportInfo).getApplicableRedactions();
        final NetworkCapabilities netCap =
                new NetworkCapabilities().setTransportInfo(transportInfo);

        mService.createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                netCap, false /* includeLocationSensitiveInfoInTransportInfo */,
                Process.myPid(), Process.myUid(),
                mContext.getPackageName(), getAttributionTag());
        // redact both MAC_ADDRESS & location sensitive fields.
        verify(transportInfo).makeCopy(REDACT_FOR_ACCESS_FINE_LOCATION
                | REDACT_FOR_LOCAL_MAC_ADDRESS);
    }

    @Test
    public void testCreateForCallerWithLocalMacAddressSanitizedWithSettingsPermission()
            throws Exception {
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_GRANTED);

        final TransportInfo transportInfo = mock(TransportInfo.class);
        doReturn(REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_NETWORK_SETTINGS)
                .when(transportInfo).getApplicableRedactions();
        final NetworkCapabilities netCap =
                new NetworkCapabilities().setTransportInfo(transportInfo);

        mService.createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                netCap, false /* includeLocationSensitiveInfoInTransportInfo */,
                Process.myPid(), Process.myUid(),
                mContext.getPackageName(), getAttributionTag());
        // don't redact NETWORK_SETTINGS fields, only location sensitive fields.
        verify(transportInfo).makeCopy(REDACT_FOR_ACCESS_FINE_LOCATION);
    }

    @Test
    public void testCreateForCallerWithLocalMacAddressSanitizedWithoutSettingsPermission()
            throws Exception {
        mServiceContext.setPermission(Manifest.permission.LOCAL_MAC_ADDRESS, PERMISSION_DENIED);

        final TransportInfo transportInfo = mock(TransportInfo.class);
        doReturn(REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_NETWORK_SETTINGS)
                .when(transportInfo).getApplicableRedactions();
        final NetworkCapabilities netCap =
                new NetworkCapabilities().setTransportInfo(transportInfo);

        mService.createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                netCap, false /* includeLocationSensitiveInfoInTransportInfo */,
                Process.myPid(), Process.myUid(),
                mContext.getPackageName(), getAttributionTag());
        // redact both NETWORK_SETTINGS & location sensitive fields.
        verify(transportInfo).makeCopy(
                REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_NETWORK_SETTINGS);
    }

    /**
     * Test TransportInfo to verify redaction mechanism.
     */
    private static class TestTransportInfo implements TransportInfo {
        public final boolean locationRedacted;
        public final boolean localMacAddressRedacted;
        public final boolean settingsRedacted;

        TestTransportInfo() {
            locationRedacted = false;
            localMacAddressRedacted = false;
            settingsRedacted = false;
        }

        TestTransportInfo(boolean locationRedacted, boolean localMacAddressRedacted,
                boolean settingsRedacted) {
            this.locationRedacted = locationRedacted;
            this.localMacAddressRedacted =
                    localMacAddressRedacted;
            this.settingsRedacted = settingsRedacted;
        }

        @Override
        public TransportInfo makeCopy(@NetworkCapabilities.RedactionType long redactions) {
            return new TestTransportInfo(
                    locationRedacted | (redactions & REDACT_FOR_ACCESS_FINE_LOCATION) != 0,
                    localMacAddressRedacted | (redactions & REDACT_FOR_LOCAL_MAC_ADDRESS) != 0,
                    settingsRedacted | (redactions & REDACT_FOR_NETWORK_SETTINGS) != 0
            );
        }

        @Override
        public @NetworkCapabilities.RedactionType long getApplicableRedactions() {
            return REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_LOCAL_MAC_ADDRESS
                    | REDACT_FOR_NETWORK_SETTINGS;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TestTransportInfo)) return false;
            TestTransportInfo that = (TestTransportInfo) other;
            return that.locationRedacted == this.locationRedacted
                    && that.localMacAddressRedacted == this.localMacAddressRedacted
                    && that.settingsRedacted == this.settingsRedacted;
        }

        @Override
        public int hashCode() {
            return Objects.hash(locationRedacted, localMacAddressRedacted, settingsRedacted);
        }

        @Override
        public String toString() {
            return String.format(
                    "TestTransportInfo{locationRedacted=%s macRedacted=%s settingsRedacted=%s}",
                    locationRedacted, localMacAddressRedacted, settingsRedacted);
        }
    }

    private TestTransportInfo getTestTransportInfo(NetworkCapabilities nc) {
        return (TestTransportInfo) nc.getTransportInfo();
    }

    private TestTransportInfo getTestTransportInfo(TestNetworkAgentWrapper n) {
        final NetworkCapabilities nc = mCm.getNetworkCapabilities(n.getNetwork());
        assertNotNull(nc);
        return getTestTransportInfo(nc);
    }


    private void verifyNetworkCallbackLocationDataInclusionUsingTransportInfoAndOwnerUidInNetCaps(
            @NonNull TestNetworkCallback wifiNetworkCallback, int actualOwnerUid,
            @NonNull TransportInfo actualTransportInfo, int expectedOwnerUid,
            @NonNull TransportInfo expectedTransportInfo) throws Exception {
        doReturn(Build.VERSION_CODES.S).when(mPackageManager).getTargetSdkVersion(anyString());
        final NetworkCapabilities ncTemplate =
                new NetworkCapabilities()
                        .addTransportType(TRANSPORT_WIFI)
                        .setOwnerUid(actualOwnerUid);

        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(wifiRequest, wifiNetworkCallback);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, new LinkProperties(),
                ncTemplate);
        mWiFiAgent.connect(false);

        wifiNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

        // Send network capabilities update with TransportInfo to trigger capabilities changed
        // callback.
        mWiFiAgent.setNetworkCapabilities(ncTemplate.setTransportInfo(actualTransportInfo), true);

        wifiNetworkCallback.expectCaps(mWiFiAgent,
                c -> Objects.equals(expectedOwnerUid, c.getOwnerUid())
                        && Objects.equals(expectedTransportInfo, c.getTransportInfo()));
    }

    @Test
    public void testVerifyLocationDataIsNotIncludedWhenInclFlagNotSet() throws Exception {
        final TestNetworkCallback wifiNetworkCallack = new TestNetworkCallback();
        final int ownerUid = Process.myUid();
        final TransportInfo transportInfo = new TestTransportInfo();
        // Even though the test uid holds privileged permissions, mask location fields since
        // the callback did not explicitly opt-in to get location data.
        final TransportInfo sanitizedTransportInfo = new TestTransportInfo(
                true, /* locationRedacted */
                true, /* localMacAddressRedacted */
                true /* settingsRedacted */
        );
        // Should not expect location data since the callback does not set the flag for including
        // location data.
        verifyNetworkCallbackLocationDataInclusionUsingTransportInfoAndOwnerUidInNetCaps(
                wifiNetworkCallack, ownerUid, transportInfo, INVALID_UID, sanitizedTransportInfo);
    }

    @Test
    public void testTransportInfoRedactionInSynchronousCalls() throws Exception {
        final NetworkCapabilities ncTemplate = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .setTransportInfo(new TestTransportInfo());

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, new LinkProperties(), ncTemplate);
        mWiFiAgent.connect(true /* validated; waits for callback */);

        // NETWORK_SETTINGS redaction is controlled by the NETWORK_SETTINGS permission
        assertTrue(getTestTransportInfo(mWiFiAgent).settingsRedacted);
        withPermission(NETWORK_SETTINGS, () -> {
            assertFalse(getTestTransportInfo(mWiFiAgent).settingsRedacted);
        });
        assertTrue(getTestTransportInfo(mWiFiAgent).settingsRedacted);

        // LOCAL_MAC_ADDRESS redaction is controlled by the LOCAL_MAC_ADDRESS permission
        assertTrue(getTestTransportInfo(mWiFiAgent).localMacAddressRedacted);
        withPermission(LOCAL_MAC_ADDRESS, () -> {
            assertFalse(getTestTransportInfo(mWiFiAgent).localMacAddressRedacted);
        });
        assertTrue(getTestTransportInfo(mWiFiAgent).localMacAddressRedacted);

        // Synchronous getNetworkCapabilities calls never return unredacted location-sensitive
        // information.
        assertTrue(getTestTransportInfo(mWiFiAgent).locationRedacted);
        setupLocationPermissions(Build.VERSION_CODES.S, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);
        assertTrue(getTestTransportInfo(mWiFiAgent).locationRedacted);
        denyAllLocationPrivilegedPermissions();
        assertTrue(getTestTransportInfo(mWiFiAgent).locationRedacted);
    }

    private void setupConnectionOwnerUid(int vpnOwnerUid, @VpnManager.VpnType int vpnType)
            throws Exception {
        final Set<UidRange> vpnRange = Collections.singleton(PRIMARY_UIDRANGE);
        mMockVpn.setVpnType(vpnType);
        mMockVpn.establish(new LinkProperties(), vpnOwnerUid, vpnRange);
        assertVpnUidRangesUpdated(true, vpnRange, vpnOwnerUid);

        mDeps.setConnectionOwnerUid(42);
    }

    private void setupConnectionOwnerUidAsVpnApp(int vpnOwnerUid, @VpnManager.VpnType int vpnType)
            throws Exception {
        setupConnectionOwnerUid(vpnOwnerUid, vpnType);

        // Test as VPN app
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);
        mServiceContext.setPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, PERMISSION_DENIED);
    }

    private ConnectionInfo getTestConnectionInfo() throws Exception {
        return new ConnectionInfo(
                IPPROTO_TCP,
                new InetSocketAddress(InetAddresses.parseNumericAddress("1.2.3.4"), 1234),
                new InetSocketAddress(InetAddresses.parseNumericAddress("2.3.4.5"), 2345));
    }

    @Test
    public void testGetConnectionOwnerUidPlatformVpn() throws Exception {
        final int myUid = Process.myUid();
        setupConnectionOwnerUidAsVpnApp(myUid, VpnManager.TYPE_VPN_PLATFORM);

        assertEquals(INVALID_UID, mService.getConnectionOwnerUid(getTestConnectionInfo()));
    }

    @Test
    public void testGetConnectionOwnerUidVpnServiceWrongUser() throws Exception {
        final int myUid = Process.myUid();
        setupConnectionOwnerUidAsVpnApp(myUid + 1, VpnManager.TYPE_VPN_SERVICE);

        assertEquals(INVALID_UID, mService.getConnectionOwnerUid(getTestConnectionInfo()));
    }

    @Test
    public void testGetConnectionOwnerUidVpnServiceDoesNotThrow() throws Exception {
        final int myUid = Process.myUid();
        setupConnectionOwnerUidAsVpnApp(myUid, VpnManager.TYPE_VPN_SERVICE);

        assertEquals(42, mService.getConnectionOwnerUid(getTestConnectionInfo()));
    }

    @Test
    public void testGetConnectionOwnerUidVpnServiceNetworkStackDoesNotThrow() throws Exception {
        final int myUid = Process.myUid();
        setupConnectionOwnerUid(myUid, VpnManager.TYPE_VPN_SERVICE);
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_GRANTED);

        assertEquals(42, mService.getConnectionOwnerUid(getTestConnectionInfo()));
    }

    @Test
    public void testGetConnectionOwnerUidVpnServiceMainlineNetworkStackDoesNotThrow()
            throws Exception {
        final int myUid = Process.myUid();
        setupConnectionOwnerUid(myUid, VpnManager.TYPE_VPN_SERVICE);
        mServiceContext.setPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, PERMISSION_GRANTED);

        assertEquals(42, mService.getConnectionOwnerUid(getTestConnectionInfo()));
    }

    private static PackageInfo buildPackageInfo(boolean hasSystemPermission, int uid) {
        final PackageInfo packageInfo = new PackageInfo();
        if (hasSystemPermission) {
            packageInfo.requestedPermissions = new String[] {
                    CHANGE_NETWORK_STATE, CONNECTIVITY_USE_RESTRICTED_NETWORKS };
            packageInfo.requestedPermissionsFlags = new int[] {
                    REQUESTED_PERMISSION_GRANTED, REQUESTED_PERMISSION_GRANTED };
        } else {
            packageInfo.requestedPermissions = new String[0];
        }
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.privateFlags = 0;
        packageInfo.applicationInfo.uid = UserHandle.getUid(UserHandle.USER_SYSTEM,
                UserHandle.getAppId(uid));
        return packageInfo;
    }

    @Test
    public void testRegisterConnectivityDiagnosticsCallbackInvalidRequest() throws Exception {
        final NetworkRequest request =
                new NetworkRequest(
                        new NetworkCapabilities(), TYPE_ETHERNET, 0, NetworkRequest.Type.NONE);
        try {
            mService.registerConnectivityDiagnosticsCallback(
                    mConnectivityDiagnosticsCallback, request, mContext.getPackageName());
            fail("registerConnectivityDiagnosticsCallback should throw on invalid NetworkRequest");
        } catch (IllegalArgumentException expected) {
        }
    }

    private void assertRouteInfoParcelMatches(RouteInfo route, RouteInfoParcel parcel) {
        assertEquals(route.getDestination().toString(), parcel.destination);
        assertEquals(route.getInterface(), parcel.ifName);
        assertEquals(route.getMtu(), parcel.mtu);

        switch (route.getType()) {
            case RouteInfo.RTN_UNICAST:
                if (route.hasGateway()) {
                    assertEquals(route.getGateway().getHostAddress(), parcel.nextHop);
                } else {
                    assertEquals(INetd.NEXTHOP_NONE, parcel.nextHop);
                }
                break;
            case RouteInfo.RTN_UNREACHABLE:
                assertEquals(INetd.NEXTHOP_UNREACHABLE, parcel.nextHop);
                break;
            case RouteInfo.RTN_THROW:
                assertEquals(INetd.NEXTHOP_THROW, parcel.nextHop);
                break;
            default:
                assertEquals(INetd.NEXTHOP_NONE, parcel.nextHop);
                break;
        }
    }

    private void assertRoutesAdded(int netId, RouteInfo... routes) throws Exception {
        // TODO: add @JavaDerive(equals=true) to RouteInfoParcel, use eq() directly, and delete
        // assertRouteInfoParcelMatches above.
        ArgumentCaptor<RouteInfoParcel> captor = ArgumentCaptor.forClass(RouteInfoParcel.class);
        verify(mMockNetd, times(routes.length)).networkAddRouteParcel(eq(netId), captor.capture());
        for (int i = 0; i < routes.length; i++) {
            assertRouteInfoParcelMatches(routes[i], captor.getAllValues().get(i));
        }
    }

    private void assertRoutesRemoved(int netId, RouteInfo... routes) throws Exception {
        ArgumentCaptor<RouteInfoParcel> captor = ArgumentCaptor.forClass(RouteInfoParcel.class);
        verify(mMockNetd, times(routes.length)).networkRemoveRouteParcel(eq(netId),
                captor.capture());
        for (int i = 0; i < routes.length; i++) {
            assertRouteInfoParcelMatches(routes[i], captor.getAllValues().get(i));
        }
    }

    @Test
    public void testRegisterUnregisterConnectivityDiagnosticsCallback() throws Exception {
        final NetworkRequest wifiRequest =
                new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build();
        doReturn(mIBinder).when(mConnectivityDiagnosticsCallback).asBinder();

        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback, wifiRequest, mContext.getPackageName());

        // Block until all other events are done processing.
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);

        verify(mIBinder).linkToDeath(any(ConnectivityDiagnosticsCallbackInfo.class), anyInt());
        verify(mConnectivityDiagnosticsCallback).asBinder();
        assertTrue(mService.mConnectivityDiagnosticsCallbacks.containsKey(mIBinder));

        mService.unregisterConnectivityDiagnosticsCallback(mConnectivityDiagnosticsCallback);
        verify(mIBinder, timeout(TIMEOUT_MS))
                .unlinkToDeath(any(ConnectivityDiagnosticsCallbackInfo.class), anyInt());
        assertFalse(mService.mConnectivityDiagnosticsCallbacks.containsKey(mIBinder));
        verify(mConnectivityDiagnosticsCallback, atLeastOnce()).asBinder();
    }

    @Test
    public void testRegisterDuplicateConnectivityDiagnosticsCallback() throws Exception {
        final NetworkRequest wifiRequest =
                new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build();
        doReturn(mIBinder).when(mConnectivityDiagnosticsCallback).asBinder();

        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback, wifiRequest, mContext.getPackageName());

        // Block until all other events are done processing.
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);

        verify(mIBinder).linkToDeath(any(ConnectivityDiagnosticsCallbackInfo.class), anyInt());
        verify(mConnectivityDiagnosticsCallback).asBinder();
        assertTrue(mService.mConnectivityDiagnosticsCallbacks.containsKey(mIBinder));

        // Register the same callback again
        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback, wifiRequest, mContext.getPackageName());

        // Block until all other events are done processing.
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);

        assertTrue(mService.mConnectivityDiagnosticsCallbacks.containsKey(mIBinder));
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterConnectivityDiagnosticsCallbackNullCallback() {
        mService.registerConnectivityDiagnosticsCallback(
                null /* callback */,
                new NetworkRequest.Builder().build(),
                mContext.getPackageName());
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterConnectivityDiagnosticsCallbackNullNetworkRequest() {
        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback,
                null /* request */,
                mContext.getPackageName());
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterConnectivityDiagnosticsCallbackNullPackageName() {
        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback,
                new NetworkRequest.Builder().build(),
                null /* callingPackageName */);
    }

    @Test(expected = NullPointerException.class)
    public void testUnregisterConnectivityDiagnosticsCallbackNullPackageName() {
        mService.unregisterConnectivityDiagnosticsCallback(null /* callback */);
    }

    public NetworkAgentInfo fakeMobileNai(NetworkCapabilities nc) {
        final NetworkCapabilities cellNc = new NetworkCapabilities.Builder(nc)
                .addTransportType(TRANSPORT_CELLULAR).build();
        final NetworkInfo info = new NetworkInfo(TYPE_MOBILE, TelephonyManager.NETWORK_TYPE_LTE,
                ConnectivityManager.getNetworkTypeName(TYPE_MOBILE),
                TelephonyManager.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_LTE));
        return fakeNai(cellNc, info);
    }

    private NetworkAgentInfo fakeWifiNai(NetworkCapabilities nc) {
        final NetworkCapabilities wifiNc = new NetworkCapabilities.Builder(nc)
                .addTransportType(TRANSPORT_WIFI).build();
        final NetworkInfo info = new NetworkInfo(TYPE_WIFI, 0 /* subtype */,
                ConnectivityManager.getNetworkTypeName(TYPE_WIFI), "" /* subtypeName */);
        return fakeNai(wifiNc, info);
    }

    private NetworkAgentInfo fakeVpnNai(NetworkCapabilities nc) {
        final NetworkCapabilities vpnNc = new NetworkCapabilities.Builder(nc)
                .addTransportType(TRANSPORT_VPN).build();
        final NetworkInfo info = new NetworkInfo(TYPE_VPN, 0 /* subtype */,
                ConnectivityManager.getNetworkTypeName(TYPE_VPN), "" /* subtypeName */);
        return fakeNai(vpnNc, info);
    }

    private NetworkAgentInfo fakeNai(NetworkCapabilities nc, NetworkInfo networkInfo) {
        return new NetworkAgentInfo(null, new Network(NET_ID), networkInfo, new LinkProperties(),
                nc, null /* localNetworkConfig */,
                new NetworkScore.Builder().setLegacyInt(0).build(),
                mServiceContext, null, new NetworkAgentConfig(), mService, null, null, 0,
                INVALID_UID, TEST_LINGER_DELAY_MS, mQosCallbackTracker,
                new ConnectivityService.Dependencies());
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsNetworkStack() throws Exception {
        final NetworkAgentInfo naiWithoutUid = fakeMobileNai(new NetworkCapabilities());

        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_GRANTED);
        assertTrue(
                "NetworkStack permission not applied",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid(), Process.myUid(), naiWithoutUid,
                        mContext.getOpPackageName()));
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsSysUi() throws Exception {
        final NetworkAgentInfo naiWithoutUid = fakeMobileNai(new NetworkCapabilities());

        mServiceContext.setPermission(STATUS_BAR_SERVICE, PERMISSION_GRANTED);
        assertTrue(
                "SysUi permission (STATUS_BAR_SERVICE) not applied",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid(), Process.myUid(), naiWithoutUid,
                        mContext.getOpPackageName()));
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsWrongUidPackageName() throws Exception {
        final int wrongUid = Process.myUid() + 1;

        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setAdministratorUids(new int[] {wrongUid});
        final NetworkAgentInfo naiWithUid = fakeWifiNai(nc);

        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);

        assertFalse(
                "Mismatched uid/package name should not pass the location permission check",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid() + 1, wrongUid, naiWithUid, mContext.getOpPackageName()));
    }

    private void verifyConnectivityDiagnosticsPermissionsWithNetworkAgentInfo(
            NetworkAgentInfo info, boolean expectPermission) {
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);

        assertEquals(
                "Unexpected ConnDiags permission",
                expectPermission,
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid(), Process.myUid(), info, mContext.getOpPackageName()));
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsCellularNoLocationPermission()
            throws Exception {
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setAdministratorUids(new int[] {Process.myUid()});
        final NetworkAgentInfo naiWithUid = fakeMobileNai(nc);

        verifyConnectivityDiagnosticsPermissionsWithNetworkAgentInfo(naiWithUid,
                true /* expectPermission */);
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsWifiNoLocationPermission()
            throws Exception {
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setAdministratorUids(new int[] {Process.myUid()});
        final NetworkAgentInfo naiWithUid = fakeWifiNai(nc);

        verifyConnectivityDiagnosticsPermissionsWithNetworkAgentInfo(naiWithUid,
                false /* expectPermission */);
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsActiveVpn() throws Exception {
        final NetworkAgentInfo naiWithoutUid = fakeMobileNai(new NetworkCapabilities());

        mMockVpn.establishForMyUid();
        assertUidRangesUpdatedForMyUid(true);

        // Wait for networks to connect and broadcasts to be sent before removing permissions.
        waitForIdle();
        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);

        assertTrue(mMockVpn.setUnderlyingNetworks(new Network[] {naiWithoutUid.network}));
        waitForIdle();
        assertTrue(
                "Active VPN permission not applied",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid(), Process.myUid(), naiWithoutUid,
                        mContext.getOpPackageName()));

        assertTrue(mMockVpn.setUnderlyingNetworks(null));
        waitForIdle();
        assertFalse(
                "VPN shouldn't receive callback on non-underlying network",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid(), Process.myUid(), naiWithoutUid,
                        mContext.getOpPackageName()));
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsNetworkAdministrator() throws Exception {
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setAdministratorUids(new int[] {Process.myUid()});
        final NetworkAgentInfo naiWithUid = fakeMobileNai(nc);

        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);

        assertTrue(
                "NetworkCapabilities administrator uid permission not applied",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid(), Process.myUid(), naiWithUid, mContext.getOpPackageName()));
    }

    @Test
    public void testCheckConnectivityDiagnosticsPermissionsFails() throws Exception {
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setOwnerUid(Process.myUid());
        nc.setAdministratorUids(new int[] {Process.myUid()});
        final NetworkAgentInfo naiWithUid = fakeMobileNai(nc);

        setupLocationPermissions(Build.VERSION_CODES.Q, true, AppOpsManager.OPSTR_FINE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION);
        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_DENIED);

        // Use wrong pid and uid
        assertFalse(
                "Permissions allowed when they shouldn't be granted",
                mService.hasConnectivityDiagnosticsPermissions(
                        Process.myPid() + 1, Process.myUid() + 1, naiWithUid,
                        mContext.getOpPackageName()));
    }

    @Test
    public void testUnderlyingNetworksWillBeSetInNetworkAgentInfoConstructor() throws Exception {
        assumeTrue(mDeps.isAtLeastT());
        final Network network1 = new Network(100);
        final Network network2 = new Network(101);
        final List<Network> underlyingNetworks = new ArrayList<>();
        final NetworkCapabilities ncWithEmptyUnderlyingNetworks = new NetworkCapabilities.Builder()
                .setUnderlyingNetworks(underlyingNetworks)
                .build();
        final NetworkAgentInfo vpnNaiWithEmptyUnderlyingNetworks =
                fakeVpnNai(ncWithEmptyUnderlyingNetworks);
        assertEquals(underlyingNetworks,
                Arrays.asList(vpnNaiWithEmptyUnderlyingNetworks.declaredUnderlyingNetworks));

        underlyingNetworks.add(network1);
        underlyingNetworks.add(network2);
        final NetworkCapabilities ncWithUnderlyingNetworks = new NetworkCapabilities.Builder()
                .setUnderlyingNetworks(underlyingNetworks)
                .build();
        final NetworkAgentInfo vpnNaiWithUnderlyingNetwokrs = fakeVpnNai(ncWithUnderlyingNetworks);
        assertEquals(underlyingNetworks,
                Arrays.asList(vpnNaiWithUnderlyingNetwokrs.declaredUnderlyingNetworks));

        final NetworkCapabilities ncWithoutUnderlyingNetworks = new NetworkCapabilities.Builder()
                .build();
        final NetworkAgentInfo vpnNaiWithoutUnderlyingNetwokrs =
                fakeVpnNai(ncWithoutUnderlyingNetworks);
        assertNull(vpnNaiWithoutUnderlyingNetwokrs.declaredUnderlyingNetworks);
    }

    @Test
    public void testRegisterConnectivityDiagnosticsCallbackCallsOnConnectivityReport()
            throws Exception {
        // Set up the Network, which leads to a ConnectivityReport being cached for the network.
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);
        final LinkProperties linkProperties = new LinkProperties();
        linkProperties.setInterfaceName(INTERFACE_NAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, linkProperties);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        callback.assertNoCallback();

        final NetworkRequest request = new NetworkRequest.Builder().build();
        doReturn(mIBinder).when(mConnectivityDiagnosticsCallback).asBinder();

        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_GRANTED);

        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback, request, mContext.getPackageName());

        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onConnectivityReportAvailable(argThat(report -> {
                    return INTERFACE_NAME.equals(report.getLinkProperties().getInterfaceName())
                            && report.getNetworkCapabilities().hasTransport(TRANSPORT_CELLULAR);
                }));
    }

    private void setUpConnectivityDiagnosticsCallback() throws Exception {
        final NetworkRequest request = new NetworkRequest.Builder().build();
        doReturn(mIBinder).when(mConnectivityDiagnosticsCallback).asBinder();

        mServiceContext.setPermission(NETWORK_STACK, PERMISSION_GRANTED);

        mService.registerConnectivityDiagnosticsCallback(
                mConnectivityDiagnosticsCallback, request, mContext.getPackageName());

        // Block until all other events are done processing.
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);

        // Connect the cell agent verify that it notifies TestNetworkCallback that it is available
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(callback);

        final NetworkCapabilities ncTemplate = new NetworkCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .setTransportInfo(new TestTransportInfo());
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, new LinkProperties(),
                ncTemplate);
        mCellAgent.connect(true);
        callback.expectAvailableThenValidatedCallbacks(mCellAgent);
        callback.assertNoCallback();

        // Make sure a report is sent and that the caps are suitably redacted.
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onConnectivityReportAvailable(argThat(report ->
                        areConnDiagCapsRedacted(report.getNetworkCapabilities())));
        reset(mConnectivityDiagnosticsCallback);
    }

    private boolean areConnDiagCapsRedacted(NetworkCapabilities nc) {
        TestTransportInfo ti = getTestTransportInfo(nc);
        return nc.getUids() == null
                && nc.getAdministratorUids().length == 0
                && nc.getOwnerUid() == Process.INVALID_UID
                && ti.locationRedacted
                && ti.localMacAddressRedacted
                && ti.settingsRedacted;
    }

    @Test
    public void testConnectivityDiagnosticsCallbackOnDataStallSuspected() throws Exception {
        setUpConnectivityDiagnosticsCallback();

        // Trigger notifyDataStallSuspected() on the INetworkMonitorCallbacks instance in the
        // cellular network agent
        mCellAgent.notifyDataStallSuspected();

        // Verify onDataStallSuspected fired
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS)).onDataStallSuspected(
                argThat(report -> areConnDiagCapsRedacted(report.getNetworkCapabilities())));
    }

    @Test
    public void testConnectivityDiagnosticsCallbackOnConnectivityReported() throws Exception {
        setUpConnectivityDiagnosticsCallback();

        final Network n = mCellAgent.getNetwork();
        final boolean hasConnectivity = true;
        mService.reportNetworkConnectivity(n, hasConnectivity);

        // Verify onNetworkConnectivityReported fired
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onNetworkConnectivityReported(eq(n), eq(hasConnectivity));
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onConnectivityReportAvailable(
                        argThat(report ->
                                areConnDiagCapsRedacted(report.getNetworkCapabilities())));

        final boolean noConnectivity = false;
        mService.reportNetworkConnectivity(n, noConnectivity);

        // Wait for onNetworkConnectivityReported to fire
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onNetworkConnectivityReported(eq(n), eq(noConnectivity));

        // Also expect a ConnectivityReport after NetworkMonitor asynchronously re-validates
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS).times(2))
                .onConnectivityReportAvailable(
                        argThat(report ->
                                areConnDiagCapsRedacted(report.getNetworkCapabilities())));
    }

    @Test
    public void testConnectivityDiagnosticsCallbackOnConnectivityReportedSeparateUid()
            throws Exception {
        setUpConnectivityDiagnosticsCallback();

        // report known Connectivity from a different uid. Verify that network is not re-validated
        // and this callback is not notified.
        final Network n = mCellAgent.getNetwork();
        final boolean hasConnectivity = true;
        doAsUid(Process.myUid() + 1, () -> mService.reportNetworkConnectivity(n, hasConnectivity));

        // Block until all other events are done processing.
        HandlerUtils.waitForIdle(mCsHandlerThread, TIMEOUT_MS);

        // Verify onNetworkConnectivityReported did not fire
        verify(mConnectivityDiagnosticsCallback, never())
                .onNetworkConnectivityReported(any(), anyBoolean());
        verify(mConnectivityDiagnosticsCallback, never())
                .onConnectivityReportAvailable(any());

        // report different Connectivity from a different uid. Verify that network is re-validated
        // and that this callback is notified.
        final boolean noConnectivity = false;
        doAsUid(Process.myUid() + 1, () -> mService.reportNetworkConnectivity(n, noConnectivity));

        // Wait for onNetworkConnectivityReported to fire
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onNetworkConnectivityReported(eq(n), eq(noConnectivity));

        // Also expect a ConnectivityReport after NetworkMonitor asynchronously re-validates
        verify(mConnectivityDiagnosticsCallback, timeout(TIMEOUT_MS))
                .onConnectivityReportAvailable(
                        argThat(report ->
                                areConnDiagCapsRedacted(report.getNetworkCapabilities())));
    }

    @Test(expected = NullPointerException.class)
    public void testSimulateDataStallNullNetwork() {
        mService.simulateDataStall(
                DataStallReport.DETECTION_METHOD_DNS_EVENTS,
                0L /* timestampMillis */,
                null /* network */,
                new PersistableBundle());
    }

    @Test(expected = NullPointerException.class)
    public void testSimulateDataStallNullPersistableBundle() {
        mService.simulateDataStall(
                DataStallReport.DETECTION_METHOD_DNS_EVENTS,
                0L /* timestampMillis */,
                mock(Network.class),
                null /* extras */);
    }

    @Test
    public void testRouteAddDeleteUpdate() throws Exception {
        final NetworkRequest request = new NetworkRequest.Builder().build();
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        mCm.registerNetworkCallback(request, networkCallback);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        reset(mMockNetd);
        mCellAgent.connect(false);
        networkCallback.expectAvailableCallbacksUnvalidated(mCellAgent);
        final int netId = mCellAgent.getNetwork().netId;

        final String iface = "rmnet_data0";
        final InetAddress gateway = InetAddress.getByName("fe80::5678");
        RouteInfo direct = RouteInfo.makeHostRoute(gateway, iface);
        RouteInfo rio1 = new RouteInfo(new IpPrefix("2001:db8:1::/48"), gateway, iface);
        RouteInfo rio2 = new RouteInfo(new IpPrefix("2001:db8:2::/48"), gateway, iface);
        RouteInfo defaultRoute = new RouteInfo((IpPrefix) null, gateway, iface);
        RouteInfo defaultWithMtu = new RouteInfo(null, gateway, iface, RouteInfo.RTN_UNICAST,
                                                 1280 /* mtu */);

        // Send LinkProperties and check that we ask netd to add routes.
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(iface);
        lp.addRoute(direct);
        lp.addRoute(rio1);
        lp.addRoute(defaultRoute);
        mCellAgent.sendLinkProperties(lp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                x -> x.getLp().getRoutes().size() == 3);

        assertRoutesAdded(netId, direct, rio1, defaultRoute);
        reset(mMockNetd);

        // Send updated LinkProperties and check that we ask netd to add, remove, update routes.
        assertTrue(lp.getRoutes().contains(defaultRoute));
        lp.removeRoute(rio1);
        lp.addRoute(rio2);
        lp.addRoute(defaultWithMtu);
        // Ensure adding the same route with a different MTU replaces the previous route.
        assertFalse(lp.getRoutes().contains(defaultRoute));
        assertTrue(lp.getRoutes().contains(defaultWithMtu));

        mCellAgent.sendLinkProperties(lp);
        networkCallback.expect(LINK_PROPERTIES_CHANGED, mCellAgent,
                x -> x.getLp().getRoutes().contains(rio2));

        assertRoutesRemoved(netId, rio1);
        assertRoutesAdded(netId, rio2);

        ArgumentCaptor<RouteInfoParcel> captor = ArgumentCaptor.forClass(RouteInfoParcel.class);
        verify(mMockNetd).networkUpdateRouteParcel(eq(netId), captor.capture());
        assertRouteInfoParcelMatches(defaultWithMtu, captor.getValue());


        mCm.unregisterNetworkCallback(networkCallback);
    }

    private void verifyDump(String[] args) {
        final StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), args);
        assertFalse(stringWriter.toString().isEmpty());
    }

    @Test
    public void testDumpDoesNotCrash() throws Exception {
        mServiceContext.setPermission(DUMP, PERMISSION_GRANTED);
        // Filing a couple requests prior to testing the dump.
        final TestNetworkCallback genericNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback wifiNetworkCallback = new TestNetworkCallback();
        final NetworkRequest genericRequest = new NetworkRequest.Builder()
                .clearCapabilities().build();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.registerNetworkCallback(genericRequest, genericNetworkCallback);
        mCm.registerNetworkCallback(wifiRequest, wifiNetworkCallback);

        // NetworkProvider
        final NetworkProvider wifiProvider = new NetworkProvider(mServiceContext,
                mCsHandlerThread.getLooper(), "Wifi provider");
        mCm.registerNetworkProvider(wifiProvider);

        // NetworkAgent
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);

        // NetworkOffer
        final NetworkScore wifiScore = new NetworkScore.Builder().build();
        final NetworkCapabilities wifiCaps = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        final TestableNetworkOfferCallback wifiCallback = new TestableNetworkOfferCallback(
                TIMEOUT_MS /* timeout */, TEST_CALLBACK_TIMEOUT_MS /* noCallbackTimeout */);
        wifiProvider.registerNetworkOffer(wifiScore, wifiCaps, r -> r.run(), wifiCallback);

        // Profile preferences
        final UserHandle testHandle = setupEnterpriseNetwork();
        final TestNetworkAgentWrapper workAgent = makeEnterpriseNetworkAgent();
        workAgent.connect(true);
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                null /* executor */, null /* listener */);

        // OEM preferences
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        setOemNetworkPreference(networkPref, TEST_PACKAGE_NAME);

        // Mobile data preferred UIDs
        setAndUpdateMobileDataPreferredUids(Set.of(TEST_PACKAGE_UID));

        verifyDump(new String[0]);

        // Verify dump with arguments.
        final String dumpPrio = "--dump-priority";
        final String[] dumpArgs = {dumpPrio};
        verifyDump(dumpArgs);

        final String[] highDumpArgs = {dumpPrio, "HIGH"};
        verifyDump(highDumpArgs);

        final String[] normalDumpArgs = {dumpPrio, "NORMAL"};
        verifyDump(normalDumpArgs);

        // Invalid args should do dumpNormal w/o exception
        final String[] unknownDumpArgs = {dumpPrio, "UNKNOWN"};
        verifyDump(unknownDumpArgs);

        final String[] invalidDumpArgs = {"UNKNOWN"};
        verifyDump(invalidDumpArgs);
    }

    @Test
    public void testRequestsSortedByIdSortsCorrectly() {
        final TestNetworkCallback genericNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback wifiNetworkCallback = new TestNetworkCallback();
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest genericRequest = new NetworkRequest.Builder()
                .clearCapabilities().build();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.registerNetworkCallback(genericRequest, genericNetworkCallback);
        mCm.registerNetworkCallback(wifiRequest, wifiNetworkCallback);
        mCm.registerNetworkCallback(cellRequest, cellNetworkCallback);
        waitForIdle();

        final NetworkRequestInfo[] nriOutput = mService.requestsSortedById();

        assertTrue(nriOutput.length > 1);
        for (int i = 0; i < nriOutput.length - 1; i++) {
            final boolean isRequestIdInOrder =
                    nriOutput[i].mRequests.get(0).requestId
                            < nriOutput[i + 1].mRequests.get(0).requestId;
            assertTrue(isRequestIdInOrder);
        }
    }

    private void assertUidRangesUpdatedForMyUid(boolean add) throws Exception {
        final int uid = Process.myUid();
        assertVpnUidRangesUpdated(add, uidRangesForUids(uid), uid);
    }

    private void assertVpnUidRangesUpdated(boolean add, Set<UidRange> vpnRanges, int exemptUid)
            throws Exception {
        InOrder inOrder = inOrder(mMockNetd, mDestroySocketsWrapper);
        final Set<Integer> exemptUidSet = new ArraySet<>(List.of(exemptUid, Process.VPN_UID));
        ArgumentCaptor<int[]> exemptUidCaptor = ArgumentCaptor.forClass(int[].class);

        if (mDeps.isAtLeastU()) {
            inOrder.verify(mDestroySocketsWrapper).destroyLiveTcpSockets(
                    UidRange.toIntRanges(vpnRanges), exemptUidSet);
        } else {
            inOrder.verify(mMockNetd).socketDestroy(eq(toUidRangeStableParcels(vpnRanges)),
                    exemptUidCaptor.capture());
            assertContainsExactly(exemptUidCaptor.getValue(), Process.VPN_UID, exemptUid);
        }

        if (add) {
            inOrder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(
                    new NativeUidRangeConfig(mMockVpn.getNetwork().getNetId(),
                            toUidRangeStableParcels(vpnRanges), PREFERENCE_ORDER_VPN));
        } else {
            inOrder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(
                    new NativeUidRangeConfig(mMockVpn.getNetwork().getNetId(),
                            toUidRangeStableParcels(vpnRanges), PREFERENCE_ORDER_VPN));
        }

        if (mDeps.isAtLeastU()) {
            inOrder.verify(mDestroySocketsWrapper).destroyLiveTcpSockets(
                    UidRange.toIntRanges(vpnRanges), exemptUidSet);
        } else {
            inOrder.verify(mMockNetd).socketDestroy(eq(toUidRangeStableParcels(vpnRanges)),
                    exemptUidCaptor.capture());
            assertContainsExactly(exemptUidCaptor.getValue(), Process.VPN_UID, exemptUid);
        }
    }

    @Test
    public void testVpnUidRangesUpdate() throws Exception {
        // Set up a WiFi network without proxy.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        assertNull(mService.getProxyForNetwork(null));
        assertNull(mCm.getDefaultProxy());

        final ExpectedBroadcast b1 = expectProxyChangeAction();
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null));
        final UidRange vpnRange = PRIMARY_UIDRANGE;
        final Set<UidRange> vpnRanges = Collections.singleton(vpnRange);
        mMockVpn.establish(lp, VPN_UID, vpnRanges);
        assertVpnUidRangesUpdated(true, vpnRanges, VPN_UID);
        // VPN is connected but proxy is not set, so there is no need to send proxy broadcast.
        b1.expectNoBroadcast(500);

        // Update to new range which is old range minus APP1, i.e. only APP2
        final ExpectedBroadcast b2 = expectProxyChangeAction();
        final Set<UidRange> newRanges = new HashSet<>(asList(
                new UidRange(vpnRange.start, APP1_UID - 1),
                new UidRange(APP1_UID + 1, vpnRange.stop)));
        mMockVpn.setUids(newRanges);
        waitForIdle();

        assertVpnUidRangesUpdated(true, newRanges, VPN_UID);
        assertVpnUidRangesUpdated(false, vpnRanges, VPN_UID);

        // Uid has changed but proxy is not set, so there is no need to send proxy broadcast.
        b2.expectNoBroadcast(500);

        final ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("test", 8888);
        final ExpectedBroadcast b3 = expectProxyChangeAction();
        lp.setHttpProxy(testProxyInfo);
        mMockVpn.sendLinkProperties(lp);
        waitForIdle();
        // Proxy is set, so send a proxy broadcast.
        b3.expectBroadcast();

        final ExpectedBroadcast b4 = expectProxyChangeAction();
        mMockVpn.setUids(vpnRanges);
        waitForIdle();
        // Uid has changed and proxy is already set, so send a proxy broadcast.
        b4.expectBroadcast();

        final ExpectedBroadcast b5 = expectProxyChangeAction();
        // Proxy is removed, send a proxy broadcast.
        lp.setHttpProxy(null);
        mMockVpn.sendLinkProperties(lp);
        waitForIdle();
        b5.expectBroadcast();

        // Proxy is added in WiFi(default network), setDefaultProxy will be called.
        final LinkProperties wifiLp = mCm.getLinkProperties(mWiFiAgent.getNetwork());
        assertNotNull(wifiLp);
        final ExpectedBroadcast b6 = expectProxyChangeAction(testProxyInfo);
        wifiLp.setHttpProxy(testProxyInfo);
        mWiFiAgent.sendLinkProperties(wifiLp);
        waitForIdle();
        b6.expectBroadcast();
    }

    @Test
    public void testProxyBroadcastWillBeSentWhenVpnHasProxyAndConnects() throws Exception {
        // Set up a WiFi network without proxy.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        assertNull(mService.getProxyForNetwork(null));
        assertNull(mCm.getDefaultProxy());

        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("tun0");
        lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null));
        lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null));
        final ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("test", 8888);
        lp.setHttpProxy(testProxyInfo);
        final UidRange vpnRange = PRIMARY_UIDRANGE;
        final Set<UidRange> vpnRanges = Collections.singleton(vpnRange);
        final ExpectedBroadcast b1 = expectProxyChangeAction();
        mMockVpn.setOwnerAndAdminUid(VPN_UID);
        mMockVpn.registerAgent(false, vpnRanges, lp);
        // In any case, the proxy broadcast won't be sent before VPN goes into CONNECTED state.
        // Otherwise, the app that calls ConnectivityManager#getDefaultProxy() when it receives the
        // proxy broadcast will get null.
        b1.expectNoBroadcast(500);

        final ExpectedBroadcast b2 = expectProxyChangeAction();
        mMockVpn.connect(true /* validated */, true /* hasInternet */,
                false /* privateDnsProbeSent */);
        waitForIdle();
        assertVpnUidRangesUpdated(true, vpnRanges, VPN_UID);
        // Vpn is connected with proxy, so the proxy broadcast will be sent to inform the apps to
        // update their proxy data.
        b2.expectBroadcast();
    }

    @Test
    public void testProxyBroadcastWillBeSentWhenTheProxyOfNonDefaultNetworkHasChanged()
            throws Exception {
        // Set up a CELLULAR network without proxy.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        assertNull(mService.getProxyForNetwork(null));
        assertNull(mCm.getDefaultProxy());
        // CELLULAR network should be the default network.
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());

        // Set up a WiFi network without proxy.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        assertNull(mService.getProxyForNetwork(null));
        assertNull(mCm.getDefaultProxy());
        // WiFi network should be the default network.
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetwork());
        // CELLULAR network is not the default network.
        assertNotEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());

        // CELLULAR network is not the system default network, but it might be a per-app default
        // network. The proxy broadcast should be sent once its proxy has changed.
        final LinkProperties cellularLp = new LinkProperties();
        cellularLp.setInterfaceName(MOBILE_IFNAME);
        final ProxyInfo testProxyInfo = ProxyInfo.buildDirectProxy("test", 8888);
        final ExpectedBroadcast b = expectProxyChangeAction();
        cellularLp.setHttpProxy(testProxyInfo);
        mCellAgent.sendLinkProperties(cellularLp);
        b.expectBroadcast();
    }

    @Test
    public void testInvalidRequestTypes() {
        final int[] invalidReqTypeInts = new int[]{-1, NetworkRequest.Type.NONE.ordinal(),
                NetworkRequest.Type.LISTEN.ordinal(), NetworkRequest.Type.values().length};
        final NetworkCapabilities nc = new NetworkCapabilities().addTransportType(TRANSPORT_WIFI);

        for (int reqTypeInt : invalidReqTypeInts) {
            assertThrows("Expect throws for invalid request type " + reqTypeInt,
                    IllegalArgumentException.class,
                    () -> mService.requestNetwork(Process.INVALID_UID, nc, reqTypeInt, null, 0,
                            null, ConnectivityManager.TYPE_NONE, NetworkCallback.FLAG_NONE,
                            mContext.getPackageName(), getAttributionTag())
            );
        }
    }

    @Test
    public void testKeepConnected() throws Exception {
        setAlwaysOnNetworks(false);
        registerDefaultNetworkCallbacks();
        final TestNetworkCallback allNetworksCb = new TestNetworkCallback();
        final NetworkRequest allNetworksRequest = new NetworkRequest.Builder().clearCapabilities()
                .build();
        mCm.registerNetworkCallback(allNetworksRequest, allNetworksCb);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true /* validated */);

        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        allNetworksCb.expectAvailableThenValidatedCallbacks(mCellAgent);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true /* validated */);

        mDefaultNetworkCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
        // While the default callback doesn't see the network before it's validated, the listen
        // sees the network come up and validate later
        allNetworksCb.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        allNetworksCb.expectLosing(mCellAgent);
        allNetworksCb.expectCaps(mWiFiAgent, c -> c.hasCapability(NET_CAPABILITY_VALIDATED));
        allNetworksCb.expect(LOST, mCellAgent, TEST_LINGER_DELAY_MS * 2);

        // The cell network has disconnected (see LOST above) because it was outscored and
        // had no requests (see setAlwaysOnNetworks(false) above)
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        final NetworkScore score = new NetworkScore.Builder().setLegacyInt(30).build();
        mCellAgent.setScore(score);
        mCellAgent.connect(false /* validated */);

        // The cell network gets torn down right away.
        allNetworksCb.expectAvailableCallbacksUnvalidated(mCellAgent);
        allNetworksCb.expect(LOST, mCellAgent, TEST_NASCENT_DELAY_MS * 2);
        allNetworksCb.assertNoCallback();

        // Now create a cell network with KEEP_CONNECTED_FOR_HANDOVER and make sure it's
        // not disconnected immediately when outscored.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        final NetworkScore scoreKeepup = new NetworkScore.Builder().setLegacyInt(30)
                .setKeepConnectedReason(KEEP_CONNECTED_FOR_HANDOVER).build();
        mCellAgent.setScore(scoreKeepup);
        mCellAgent.connect(true /* validated */);

        allNetworksCb.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.assertNoCallback();

        mWiFiAgent.disconnect();

        allNetworksCb.expect(LOST, mWiFiAgent);
        mDefaultNetworkCallback.expect(LOST, mWiFiAgent);
        mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);

        // Reconnect a WiFi network and make sure the cell network is still not torn down.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true /* validated */);

        allNetworksCb.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        mDefaultNetworkCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);

        // Now remove the reason to keep connected and make sure the network lingers and is
        // torn down.
        mCellAgent.setScore(new NetworkScore.Builder().setLegacyInt(30).build());
        allNetworksCb.expectLosing(mCellAgent, TEST_NASCENT_DELAY_MS * 2);
        allNetworksCb.expect(LOST, mCellAgent, TEST_LINGER_DELAY_MS * 2);
        mDefaultNetworkCallback.assertNoCallback();

        mCm.unregisterNetworkCallback(allNetworksCb);
        // mDefaultNetworkCallback will be unregistered by tearDown()
    }

    private class QosCallbackMockHelper {
        @NonNull public final QosFilter mFilter;
        @NonNull public final IQosCallback mCallback;
        @NonNull public final TestNetworkAgentWrapper mAgentWrapper;
        @NonNull private final List<IQosCallback> mCallbacks = new ArrayList();

        QosCallbackMockHelper() throws Exception {
            Log.d(TAG, "QosCallbackMockHelper: ");
            mFilter = mock(QosFilter.class);

            // Ensure the network is disconnected before anything else occurs
            assertNull(mCellAgent);

            mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
            mCellAgent.connect(true);

            verifyActiveNetwork(TRANSPORT_CELLULAR);
            waitForIdle();
            final Network network = mCellAgent.getNetwork();

            final Pair<IQosCallback, IBinder> pair = createQosCallback();
            mCallback = pair.first;

            doReturn(network).when(mFilter).getNetwork();
            doReturn(QosCallbackException.EX_TYPE_FILTER_NONE).when(mFilter).validate();
            mAgentWrapper = mCellAgent;
        }

        void registerQosCallback(@NonNull final QosFilter filter,
                @NonNull final IQosCallback callback) {
            mCallbacks.add(callback);
            final NetworkAgentInfo nai =
                    mService.getNetworkAgentInfoForNetwork(filter.getNetwork());
            mService.registerQosCallbackInternal(filter, callback, nai);
        }

        void tearDown() {
            for (int i = 0; i < mCallbacks.size(); i++) {
                mService.unregisterQosCallback(mCallbacks.get(i));
            }
        }
    }

    private Pair<IQosCallback, IBinder> createQosCallback() {
        final IQosCallback callback = mock(IQosCallback.class);
        final IBinder binder = mock(Binder.class);
        doReturn(binder).when(callback).asBinder();
        doReturn(true).when(binder).isBinderAlive();
        return new Pair<>(callback, binder);
    }


    @Test
    public void testQosCallbackRegistration() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();
        final NetworkAgentWrapper wrapper = mQosCallbackMockHelper.mAgentWrapper;

        doReturn(QosCallbackException.EX_TYPE_FILTER_NONE)
                .when(mQosCallbackMockHelper.mFilter).validate();
        mQosCallbackMockHelper.registerQosCallback(
                mQosCallbackMockHelper.mFilter, mQosCallbackMockHelper.mCallback);

        final OnQosCallbackRegister cbRegister1 =
                (OnQosCallbackRegister) wrapper.getCallbackHistory().poll(1000, x -> true);
        assertNotNull(cbRegister1);

        final int registerCallbackId = cbRegister1.mQosCallbackId;
        mService.unregisterQosCallback(mQosCallbackMockHelper.mCallback);
        final OnQosCallbackUnregister cbUnregister =
                (OnQosCallbackUnregister) wrapper.getCallbackHistory().poll(1000, x -> true);
        assertNotNull(cbUnregister);
        assertEquals(registerCallbackId, cbUnregister.mQosCallbackId);
        assertNull(wrapper.getCallbackHistory().poll(200, x -> true));
    }

    @Test
    public void testQosCallbackNoRegistrationOnValidationError() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();

        doReturn(QosCallbackException.EX_TYPE_FILTER_NETWORK_RELEASED)
                .when(mQosCallbackMockHelper.mFilter).validate();
        mQosCallbackMockHelper.registerQosCallback(
                mQosCallbackMockHelper.mFilter, mQosCallbackMockHelper.mCallback);
        waitForIdle();
        verify(mQosCallbackMockHelper.mCallback)
                .onError(eq(QosCallbackException.EX_TYPE_FILTER_NETWORK_RELEASED));
    }

    @Test
    public void testQosCallbackAvailableAndLost() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();
        final int sessionId = 10;
        final int qosCallbackId = 1;

        doReturn(QosCallbackException.EX_TYPE_FILTER_NONE)
                .when(mQosCallbackMockHelper.mFilter).validate();
        mQosCallbackMockHelper.registerQosCallback(
                mQosCallbackMockHelper.mFilter, mQosCallbackMockHelper.mCallback);
        waitForIdle();

        final EpsBearerQosSessionAttributes attributes = new EpsBearerQosSessionAttributes(
                1, 2, 3, 4, 5, new ArrayList<>());
        mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                .sendQosSessionAvailable(qosCallbackId, sessionId, attributes);
        waitForIdle();

        verify(mQosCallbackMockHelper.mCallback).onQosEpsBearerSessionAvailable(argThat(session ->
                session.getSessionId() == sessionId
                        && session.getSessionType() == QosSession.TYPE_EPS_BEARER), eq(attributes));

        mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                .sendQosSessionLost(qosCallbackId, sessionId, QosSession.TYPE_EPS_BEARER);
        waitForIdle();
        verify(mQosCallbackMockHelper.mCallback).onQosSessionLost(argThat(session ->
                session.getSessionId() == sessionId
                        && session.getSessionType() == QosSession.TYPE_EPS_BEARER));
    }

    @Test
    public void testNrQosCallbackAvailableAndLost() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();
        final int sessionId = 10;
        final int qosCallbackId = 1;

        doReturn(QosCallbackException.EX_TYPE_FILTER_NONE)
                .when(mQosCallbackMockHelper.mFilter).validate();
        mQosCallbackMockHelper.registerQosCallback(
                mQosCallbackMockHelper.mFilter, mQosCallbackMockHelper.mCallback);
        waitForIdle();

        final NrQosSessionAttributes attributes = new NrQosSessionAttributes(
                1, 2, 3, 4, 5, 6, 7, new ArrayList<>());
        mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                .sendQosSessionAvailable(qosCallbackId, sessionId, attributes);
        waitForIdle();

        verify(mQosCallbackMockHelper.mCallback).onNrQosSessionAvailable(argThat(session ->
                session.getSessionId() == sessionId
                        && session.getSessionType() == QosSession.TYPE_NR_BEARER), eq(attributes));

        mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                .sendQosSessionLost(qosCallbackId, sessionId, QosSession.TYPE_NR_BEARER);
        waitForIdle();
        verify(mQosCallbackMockHelper.mCallback).onQosSessionLost(argThat(session ->
                session.getSessionId() == sessionId
                        && session.getSessionType() == QosSession.TYPE_NR_BEARER));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testQosCallbackAvailableOnValidationError() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();
        final NetworkAgentWrapper wrapper = mQosCallbackMockHelper.mAgentWrapper;
        final int sessionId = 10;
        final int qosCallbackId = 1;

        doReturn(QosCallbackException.EX_TYPE_FILTER_NONE)
                .when(mQosCallbackMockHelper.mFilter).validate();
        mQosCallbackMockHelper.registerQosCallback(
                mQosCallbackMockHelper.mFilter, mQosCallbackMockHelper.mCallback);
        OnQosCallbackRegister cbRegister1 =
                (OnQosCallbackRegister) wrapper.getCallbackHistory().poll(1000, x -> true);
        assertNotNull(cbRegister1);
        final int registerCallbackId = cbRegister1.mQosCallbackId;

        waitForIdle();

        doReturn(QosCallbackException.EX_TYPE_FILTER_SOCKET_REMOTE_ADDRESS_CHANGED)
                .when(mQosCallbackMockHelper.mFilter).validate();
        final EpsBearerQosSessionAttributes attributes = new EpsBearerQosSessionAttributes(
                1, 2, 3, 4, 5, new ArrayList<>());
        mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                .sendQosSessionAvailable(qosCallbackId, sessionId, attributes);
        waitForIdle();

        final NetworkAgentWrapper.CallbackType.OnQosCallbackUnregister cbUnregister;
        cbUnregister = (NetworkAgentWrapper.CallbackType.OnQosCallbackUnregister)
                wrapper.getCallbackHistory().poll(1000, x -> true);
        assertNotNull(cbUnregister);
        assertEquals(registerCallbackId, cbUnregister.mQosCallbackId);
        waitForIdle();
        verify(mQosCallbackMockHelper.mCallback)
                .onError(eq(QosCallbackException.EX_TYPE_FILTER_SOCKET_REMOTE_ADDRESS_CHANGED));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testQosCallbackLostOnValidationError() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();
        final int sessionId = 10;
        final int qosCallbackId = 1;

        doReturn(QosCallbackException.EX_TYPE_FILTER_NONE)
                .when(mQosCallbackMockHelper.mFilter).validate();
        mQosCallbackMockHelper.registerQosCallback(
                mQosCallbackMockHelper.mFilter, mQosCallbackMockHelper.mCallback);
        waitForIdle();
        EpsBearerQosSessionAttributes attributes =
                sendQosSessionEvent(qosCallbackId, sessionId, true);
        waitForIdle();

        verify(mQosCallbackMockHelper.mCallback).onQosEpsBearerSessionAvailable(argThat(session ->
                session.getSessionId() == sessionId
                        && session.getSessionType() == QosSession.TYPE_EPS_BEARER), eq(attributes));

        doReturn(QosCallbackException.EX_TYPE_FILTER_SOCKET_REMOTE_ADDRESS_CHANGED)
                .when(mQosCallbackMockHelper.mFilter).validate();

        sendQosSessionEvent(qosCallbackId, sessionId, false);
        waitForIdle();
        verify(mQosCallbackMockHelper.mCallback)
                .onError(eq(QosCallbackException.EX_TYPE_FILTER_SOCKET_REMOTE_ADDRESS_CHANGED));
    }

    private EpsBearerQosSessionAttributes sendQosSessionEvent(
            int qosCallbackId, int sessionId, boolean available) {
        if (available) {
            final EpsBearerQosSessionAttributes attributes = new EpsBearerQosSessionAttributes(
                    1, 2, 3, 4, 5, new ArrayList<>());
            mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                    .sendQosSessionAvailable(qosCallbackId, sessionId, attributes);
            return attributes;
        } else {
            mQosCallbackMockHelper.mAgentWrapper.getNetworkAgent()
                    .sendQosSessionLost(qosCallbackId, sessionId, QosSession.TYPE_EPS_BEARER);
            return null;
        }

    }

    @Test
    public void testQosCallbackTooManyRequests() throws Exception {
        mQosCallbackMockHelper = new QosCallbackMockHelper();

        doReturn(QosCallbackException.EX_TYPE_FILTER_NONE)
                .when(mQosCallbackMockHelper.mFilter).validate();
        for (int i = 0; i < 100; i++) {
            final Pair<IQosCallback, IBinder> pair = createQosCallback();

            try {
                mQosCallbackMockHelper.registerQosCallback(
                        mQosCallbackMockHelper.mFilter, pair.first);
            } catch (ServiceSpecificException e) {
                assertEquals(e.errorCode, ConnectivityManager.Errors.TOO_MANY_REQUESTS);
                if (i < 50) {
                    fail("TOO_MANY_REQUESTS thrown too early, the count is " + i);
                }

                // As long as there is at least 50 requests, it is safe to assume it works.
                // Note: The count isn't being tested precisely against 100 because the counter
                // is shared with request network.
                return;
            }
        }
        fail("TOO_MANY_REQUESTS never thrown");
    }

    private void mockGetApplicationInfo(@NonNull final String packageName, final int uid) {
        mockGetApplicationInfo(packageName, uid, PRIMARY_USER_HANDLE);
    }

    private void mockGetApplicationInfo(@NonNull final String packageName, final int uid,
            @NonNull final UserHandle user) {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = uid;
        try {
            doReturn(applicationInfo).when(mPackageManager).getApplicationInfoAsUser(
                    eq(packageName), anyInt(), eq(user));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private void mockGetApplicationInfoThrowsNameNotFound(@NonNull final String packageName,
            @NonNull final UserHandle user)
            throws Exception {
        doThrow(new PackageManager.NameNotFoundException(packageName)).when(
                mPackageManager).getApplicationInfoAsUser(eq(packageName), anyInt(), eq(user));
    }

    private void mockHasSystemFeature(@NonNull final String featureName, final boolean hasFeature) {
        doReturn(hasFeature).when(mPackageManager).hasSystemFeature(eq(featureName));
    }

    private Range<Integer> getNriFirstUidRange(@NonNull final NetworkRequestInfo nri) {
        return nri.mRequests.get(0).networkCapabilities.getUids().iterator().next();
    }

    private OemNetworkPreferences createDefaultOemNetworkPreferences(
            @OemNetworkPreferences.OemNetworkPreference final int preference) {
        // Arrange PackageManager mocks
        mockGetApplicationInfo(TEST_PACKAGE_NAME, TEST_PACKAGE_UID);

        // Build OemNetworkPreferences object
        return new OemNetworkPreferences.Builder()
                .addNetworkPreference(TEST_PACKAGE_NAME, preference)
                .build();
    }

    @Test
    public void testOemNetworkRequestFactoryPreferenceUninitializedThrowsError() {
        @OemNetworkPreferences.OemNetworkPreference final int prefToTest =
                OEM_NETWORK_PREFERENCE_UNINITIALIZED;

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        assertThrows(IllegalArgumentException.class,
                () -> mService.new OemNetworkRequestFactory()
                        .createNrisFromOemNetworkPreferences(
                                createDefaultOemNetworkPreferences(prefToTest)));
    }

    @Test
    public void testOemNetworkRequestFactoryPreferenceOemPaid()
            throws Exception {
        // Expectations
        final int expectedNumOfNris = 1;
        final int expectedNumOfRequests = 3;

        @OemNetworkPreferences.OemNetworkPreference final int prefToTest =
                OEM_NETWORK_PREFERENCE_OEM_PAID;

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final ArraySet<NetworkRequestInfo> nris =
                mService.new OemNetworkRequestFactory()
                        .createNrisFromOemNetworkPreferences(
                                createDefaultOemNetworkPreferences(prefToTest));
        final NetworkRequestInfo nri = nris.iterator().next();
        assertEquals(PREFERENCE_ORDER_OEM, nri.mPreferenceOrder);
        final List<NetworkRequest> mRequests = nri.mRequests;
        assertEquals(expectedNumOfNris, nris.size());
        assertEquals(expectedNumOfRequests, mRequests.size());
        assertTrue(mRequests.get(0).isListen());
        assertTrue(mRequests.get(0).hasCapability(NET_CAPABILITY_NOT_METERED));
        assertTrue(mRequests.get(0).hasCapability(NET_CAPABILITY_VALIDATED));
        assertTrue(mRequests.get(1).isRequest());
        assertTrue(mRequests.get(1).hasCapability(NET_CAPABILITY_OEM_PAID));
        assertEquals(NetworkRequest.Type.TRACK_DEFAULT, mRequests.get(2).type);
        assertTrue(mService.getDefaultRequest().networkCapabilities.equalsNetCapabilities(
                mRequests.get(2).networkCapabilities));
    }

    @Test
    public void testOemNetworkRequestFactoryPreferenceOemPaidNoFallback()
            throws Exception {
        // Expectations
        final int expectedNumOfNris = 1;
        final int expectedNumOfRequests = 2;

        @OemNetworkPreferences.OemNetworkPreference final int prefToTest =
                OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final ArraySet<NetworkRequestInfo> nris =
                mService.new OemNetworkRequestFactory()
                        .createNrisFromOemNetworkPreferences(
                                createDefaultOemNetworkPreferences(prefToTest));
        final NetworkRequestInfo nri = nris.iterator().next();
        assertEquals(PREFERENCE_ORDER_OEM, nri.mPreferenceOrder);
        final List<NetworkRequest> mRequests = nri.mRequests;
        assertEquals(expectedNumOfNris, nris.size());
        assertEquals(expectedNumOfRequests, mRequests.size());
        assertTrue(mRequests.get(0).isListen());
        assertTrue(mRequests.get(0).hasCapability(NET_CAPABILITY_NOT_METERED));
        assertTrue(mRequests.get(0).hasCapability(NET_CAPABILITY_VALIDATED));
        assertTrue(mRequests.get(1).isRequest());
        assertTrue(mRequests.get(1).hasCapability(NET_CAPABILITY_OEM_PAID));
    }

    @Test
    public void testOemNetworkRequestFactoryPreferenceOemPaidOnly()
            throws Exception {
        // Expectations
        final int expectedNumOfNris = 1;
        final int expectedNumOfRequests = 1;

        @OemNetworkPreferences.OemNetworkPreference final int prefToTest =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final ArraySet<NetworkRequestInfo> nris =
                mService.new OemNetworkRequestFactory()
                        .createNrisFromOemNetworkPreferences(
                                createDefaultOemNetworkPreferences(prefToTest));
        final NetworkRequestInfo nri = nris.iterator().next();
        assertEquals(PREFERENCE_ORDER_OEM, nri.mPreferenceOrder);
        final List<NetworkRequest> mRequests = nri.mRequests;
        assertEquals(expectedNumOfNris, nris.size());
        assertEquals(expectedNumOfRequests, mRequests.size());
        assertTrue(mRequests.get(0).isRequest());
        assertTrue(mRequests.get(0).hasCapability(NET_CAPABILITY_OEM_PAID));
    }

    @Test
    public void testOemNetworkRequestFactoryPreferenceOemPrivateOnly()
            throws Exception {
        // Expectations
        final int expectedNumOfNris = 1;
        final int expectedNumOfRequests = 1;

        @OemNetworkPreferences.OemNetworkPreference final int prefToTest =
                OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final ArraySet<NetworkRequestInfo> nris =
                mService.new OemNetworkRequestFactory()
                        .createNrisFromOemNetworkPreferences(
                                createDefaultOemNetworkPreferences(prefToTest));
        final NetworkRequestInfo nri = nris.iterator().next();
        assertEquals(PREFERENCE_ORDER_OEM, nri.mPreferenceOrder);
        final List<NetworkRequest> mRequests = nri.mRequests;
        assertEquals(expectedNumOfNris, nris.size());
        assertEquals(expectedNumOfRequests, mRequests.size());
        assertTrue(mRequests.get(0).isRequest());
        assertTrue(mRequests.get(0).hasCapability(NET_CAPABILITY_OEM_PRIVATE));
        assertFalse(mRequests.get(0).hasCapability(NET_CAPABILITY_OEM_PAID));
    }

    @Test
    public void testOemNetworkRequestFactoryCreatesCorrectNumOfNris()
            throws Exception {
        // Expectations
        final int expectedNumOfNris = 2;

        // Arrange PackageManager mocks
        final String testPackageName2 = "com.google.apps.dialer";
        mockGetApplicationInfo(TEST_PACKAGE_NAME, TEST_PACKAGE_UID);
        mockGetApplicationInfo(testPackageName2, TEST_PACKAGE_UID);

        // Build OemNetworkPreferences object
        final int testOemPref = OEM_NETWORK_PREFERENCE_OEM_PAID;
        final int testOemPref2 = OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
        final OemNetworkPreferences pref = new OemNetworkPreferences.Builder()
                .addNetworkPreference(TEST_PACKAGE_NAME, testOemPref)
                .addNetworkPreference(testPackageName2, testOemPref2)
                .build();

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final ArraySet<NetworkRequestInfo> nris =
                mService.new OemNetworkRequestFactory().createNrisFromOemNetworkPreferences(pref);

        assertNotNull(nris);
        assertEquals(expectedNumOfNris, nris.size());
    }

    @Test
    public void testOemNetworkRequestFactoryMultiplePrefsCorrectlySetsUids()
            throws Exception {
        // Arrange PackageManager mocks
        final String testPackageName2 = "com.google.apps.dialer";
        final int testPackageNameUid2 = 456;
        mockGetApplicationInfo(TEST_PACKAGE_NAME, TEST_PACKAGE_UID);
        mockGetApplicationInfo(testPackageName2, testPackageNameUid2);

        // Build OemNetworkPreferences object
        final int testOemPref = OEM_NETWORK_PREFERENCE_OEM_PAID;
        final int testOemPref2 = OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
        final OemNetworkPreferences pref = new OemNetworkPreferences.Builder()
                .addNetworkPreference(TEST_PACKAGE_NAME, testOemPref)
                .addNetworkPreference(testPackageName2, testOemPref2)
                .build();

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final List<NetworkRequestInfo> nris =
                new ArrayList<>(
                        mService.new OemNetworkRequestFactory().createNrisFromOemNetworkPreferences(
                                pref));

        // Sort by uid to access nris by index
        nris.sort(Comparator.comparingInt(nri -> getNriFirstUidRange(nri).getLower()));
        assertEquals(TEST_PACKAGE_UID, (int) getNriFirstUidRange(nris.get(0)).getLower());
        assertEquals(TEST_PACKAGE_UID, (int) getNriFirstUidRange(nris.get(0)).getUpper());
        assertEquals(testPackageNameUid2, (int) getNriFirstUidRange(nris.get(1)).getLower());
        assertEquals(testPackageNameUid2, (int) getNriFirstUidRange(nris.get(1)).getUpper());
    }

    @Test
    public void testOemNetworkRequestFactoryMultipleUsersSetsUids()
            throws Exception {
        // Arrange users
        final int secondUserTestPackageUid = UserHandle.getUid(SECONDARY_USER, TEST_PACKAGE_UID);
        final int thirdUserTestPackageUid = UserHandle.getUid(TERTIARY_USER, TEST_PACKAGE_UID);
        doReturn(asList(PRIMARY_USER_HANDLE, SECONDARY_USER_HANDLE, TERTIARY_USER_HANDLE))
                .when(mUserManager).getUserHandles(anyBoolean());

        // Arrange PackageManager mocks testing for users who have and don't have a package.
        mockGetApplicationInfoThrowsNameNotFound(TEST_PACKAGE_NAME, PRIMARY_USER_HANDLE);
        mockGetApplicationInfo(TEST_PACKAGE_NAME, secondUserTestPackageUid, SECONDARY_USER_HANDLE);
        mockGetApplicationInfo(TEST_PACKAGE_NAME, thirdUserTestPackageUid, TERTIARY_USER_HANDLE);

        // Build OemNetworkPreferences object
        final int testOemPref = OEM_NETWORK_PREFERENCE_OEM_PAID;
        final OemNetworkPreferences pref = new OemNetworkPreferences.Builder()
                .addNetworkPreference(TEST_PACKAGE_NAME, testOemPref)
                .build();

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final List<NetworkRequestInfo> nris =
                new ArrayList<>(
                        mService.new OemNetworkRequestFactory().createNrisFromOemNetworkPreferences(
                                pref));

        // UIDs for users with installed packages should be present.
        // Three users exist, but only two have the test package installed.
        final int expectedUidSize = 2;
        final List<Range<Integer>> uids =
                new ArrayList<>(nris.get(0).mRequests.get(0).networkCapabilities.getUids());
        assertEquals(expectedUidSize, uids.size());

        // Sort by uid to access nris by index
        uids.sort(Comparator.comparingInt(uid -> uid.getLower()));
        assertEquals(secondUserTestPackageUid, (int) uids.get(0).getLower());
        assertEquals(secondUserTestPackageUid, (int) uids.get(0).getUpper());
        assertEquals(thirdUserTestPackageUid, (int) uids.get(1).getLower());
        assertEquals(thirdUserTestPackageUid, (int) uids.get(1).getUpper());
    }

    @Test
    public void testOemNetworkRequestFactoryAddsPackagesToCorrectPreference()
            throws Exception {
        // Expectations
        final int expectedNumOfNris = 1;
        final int expectedNumOfAppUids = 2;

        // Arrange PackageManager mocks
        final String testPackageName2 = "com.google.apps.dialer";
        final int testPackageNameUid2 = 456;
        mockGetApplicationInfo(TEST_PACKAGE_NAME, TEST_PACKAGE_UID);
        mockGetApplicationInfo(testPackageName2, testPackageNameUid2);

        // Build OemNetworkPreferences object
        final int testOemPref = OEM_NETWORK_PREFERENCE_OEM_PAID;
        final OemNetworkPreferences pref = new OemNetworkPreferences.Builder()
                .addNetworkPreference(TEST_PACKAGE_NAME, testOemPref)
                .addNetworkPreference(testPackageName2, testOemPref)
                .build();

        // Act on OemNetworkRequestFactory.createNrisFromOemNetworkPreferences()
        final ArraySet<NetworkRequestInfo> nris =
                mService.new OemNetworkRequestFactory().createNrisFromOemNetworkPreferences(pref);

        assertEquals(expectedNumOfNris, nris.size());
        assertEquals(expectedNumOfAppUids,
                nris.iterator().next().mRequests.get(0).networkCapabilities.getUids().size());
    }

    @Test
    public void testSetOemNetworkPreferenceNullListenerAndPrefParamThrowsNpe() {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);

        // Act on ConnectivityService.setOemNetworkPreference()
        assertThrows(NullPointerException.class,
                () -> mService.setOemNetworkPreference(
                        null,
                        null));
    }

    @Test
    public void testSetOemNetworkPreferenceFailsForNonAutomotive()
            throws Exception {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, false);
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;

        // Act on ConnectivityService.setOemNetworkPreference()
        assertThrows(UnsupportedOperationException.class,
                () -> mService.setOemNetworkPreference(
                        createDefaultOemNetworkPreferences(networkPref),
                        null));
    }

    @Test
    public void testSetOemNetworkPreferenceFailsForTestRequestWithoutPermission() {
        // Calling setOemNetworkPreference() with a test pref requires the permission
        // MANAGE_TEST_NETWORKS.
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, false);
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_TEST;

        // Act on ConnectivityService.setOemNetworkPreference()
        assertThrows(SecurityException.class,
                () -> mService.setOemNetworkPreference(
                        createDefaultOemNetworkPreferences(networkPref),
                        null));
    }

    @Test
    public void testSetOemNetworkPreferenceFailsForInvalidTestRequest() {
        assertSetOemNetworkPreferenceFailsForInvalidTestRequest(OEM_NETWORK_PREFERENCE_TEST);
    }

    @Test
    public void testSetOemNetworkPreferenceFailsForInvalidTestOnlyRequest() {
        assertSetOemNetworkPreferenceFailsForInvalidTestRequest(OEM_NETWORK_PREFERENCE_TEST_ONLY);
    }

    private void assertSetOemNetworkPreferenceFailsForInvalidTestRequest(
            @OemNetworkPreferences.OemNetworkPreference final int oemNetworkPreferenceForTest) {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);
        final String secondPackage = "does.not.matter";

        // A valid test request would only have a single mapping.
        final OemNetworkPreferences pref = new OemNetworkPreferences.Builder()
                .addNetworkPreference(TEST_PACKAGE_NAME, oemNetworkPreferenceForTest)
                .addNetworkPreference(secondPackage, oemNetworkPreferenceForTest)
                .build();

        // Act on ConnectivityService.setOemNetworkPreference()
        assertThrows(IllegalArgumentException.class,
                () -> mService.setOemNetworkPreference(pref, null));
    }

    private void setOemNetworkPreferenceAgentConnected(final int transportType,
            final boolean connectAgent) throws Exception {
        switch(transportType) {
            // Corresponds to a metered cellular network. Will be used for the default network.
            case TRANSPORT_CELLULAR:
                if (!connectAgent) {
                    mCellAgent.disconnect();
                    break;
                }
                mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
                mCellAgent.removeCapability(NET_CAPABILITY_NOT_METERED);
                mCellAgent.connect(true);
                break;
            // Corresponds to a restricted ethernet network with OEM_PAID/OEM_PRIVATE.
            case TRANSPORT_ETHERNET:
                if (!connectAgent) {
                    stopOemManagedNetwork();
                    break;
                }
                startOemManagedNetwork(true);
                break;
            // Corresponds to unmetered Wi-Fi.
            case TRANSPORT_WIFI:
                if (!connectAgent) {
                    mWiFiAgent.disconnect();
                    break;
                }
                mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
                mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
                mWiFiAgent.connect(true);
                break;
            default:
                throw new AssertionError("Unsupported transport type passed in.");

        }
        waitForIdle();
    }

    private void startOemManagedNetwork(final boolean isOemPaid) throws Exception {
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mEthernetAgent.addCapability(
                isOemPaid ? NET_CAPABILITY_OEM_PAID : NET_CAPABILITY_OEM_PRIVATE);
        mEthernetAgent.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        mEthernetAgent.connect(true);
    }

    private void stopOemManagedNetwork() {
        mEthernetAgent.disconnect();
        waitForIdle();
    }

    private void verifyMultipleDefaultNetworksTracksCorrectly(
            final int expectedOemRequestsSize,
            @NonNull final Network expectedDefaultNetwork,
            @NonNull final Network expectedPerAppNetwork) {
        // The current test setup assumes two tracked default network requests; one for the default
        // network and the other for the OEM network preference being tested. This will be validated
        // each time to confirm it doesn't change under test.
        final int expectedDefaultNetworkRequestsSize = 2;
        assertEquals(expectedDefaultNetworkRequestsSize, mService.mDefaultNetworkRequests.size());
        for (final NetworkRequestInfo defaultRequest : mService.mDefaultNetworkRequests) {
            final Network defaultNetwork = defaultRequest.getSatisfier() == null
                    ? null : defaultRequest.getSatisfier().network();
            // If this is the default request.
            if (defaultRequest == mService.mDefaultRequest) {
                assertEquals(
                        expectedDefaultNetwork,
                        defaultNetwork);
                // Make sure this value doesn't change.
                assertEquals(1, defaultRequest.mRequests.size());
                continue;
            }
            assertEquals(expectedPerAppNetwork, defaultNetwork);
            assertEquals(expectedOemRequestsSize, defaultRequest.mRequests.size());
        }
        verifyMultipleDefaultCallbacks(expectedDefaultNetwork, expectedPerAppNetwork);
    }

    /**
     * Verify default callbacks for 'available' fire as expected. This will only run if
     * registerDefaultNetworkCallbacks() was executed prior and will only be different if the
     * setOemNetworkPreference() per-app API was used for the current process.
     * @param expectedSystemDefault the expected network for the system default.
     * @param expectedPerAppDefault the expected network for the current process's default.
     */
    private void verifyMultipleDefaultCallbacks(
            @NonNull final Network expectedSystemDefault,
            @NonNull final Network expectedPerAppDefault) {
        if (null != mSystemDefaultNetworkCallback && null != expectedSystemDefault
                && mService.mNoServiceNetwork.network() != expectedSystemDefault) {
            // getLastAvailableNetwork() is used as this method can be called successively with
            // the same network to validate therefore expectAvailableThenValidatedCallbacks
            // can't be used.
            assertEquals(mSystemDefaultNetworkCallback.getLastAvailableNetwork(),
                    expectedSystemDefault);
        }
        if (null != mDefaultNetworkCallback && null != expectedPerAppDefault
                && mService.mNoServiceNetwork.network() != expectedPerAppDefault) {
            assertEquals(mDefaultNetworkCallback.getLastAvailableNetwork(),
                    expectedPerAppDefault);
        }
    }

    private void registerDefaultNetworkCallbacks() {
        if (mSystemDefaultNetworkCallback != null || mDefaultNetworkCallback != null
                || mProfileDefaultNetworkCallback != null
                || mProfileDefaultNetworkCallbackAsAppUid2 != null
                || mTestPackageDefaultNetworkCallback2 != null
                || mTestPackageDefaultNetworkCallback != null) {
            throw new IllegalStateException("Default network callbacks already registered");
        }

        mSystemDefaultNetworkCallback = new TestNetworkCallback();
        mDefaultNetworkCallback = new TestNetworkCallback();
        mProfileDefaultNetworkCallback = new TestNetworkCallback();
        mTestPackageDefaultNetworkCallback = new TestNetworkCallback();
        mProfileDefaultNetworkCallbackAsAppUid2 = new TestNetworkCallback();
        mTestPackageDefaultNetworkCallback2 = new TestNetworkCallback();
        mCm.registerSystemDefaultNetworkCallback(mSystemDefaultNetworkCallback,
                new Handler(ConnectivityThread.getInstanceLooper()));
        mCm.registerDefaultNetworkCallback(mDefaultNetworkCallback);
        registerDefaultNetworkCallbackAsUid(mProfileDefaultNetworkCallback,
                TEST_WORK_PROFILE_APP_UID);
        registerDefaultNetworkCallbackAsUid(mTestPackageDefaultNetworkCallback, TEST_PACKAGE_UID);
        registerDefaultNetworkCallbackAsUid(mProfileDefaultNetworkCallbackAsAppUid2,
                TEST_WORK_PROFILE_APP_UID_2);
        registerDefaultNetworkCallbackAsUid(mTestPackageDefaultNetworkCallback2,
                TEST_PACKAGE_UID2);
        // TODO: test using ConnectivityManager#registerDefaultNetworkCallbackAsUid as well.
        mServiceContext.setPermission(NETWORK_SETTINGS, PERMISSION_DENIED);
    }

    private void unregisterDefaultNetworkCallbacks() {
        if (null != mDefaultNetworkCallback) {
            mCm.unregisterNetworkCallback(mDefaultNetworkCallback);
        }
        if (null != mSystemDefaultNetworkCallback) {
            mCm.unregisterNetworkCallback(mSystemDefaultNetworkCallback);
        }
        if (null != mProfileDefaultNetworkCallback) {
            mCm.unregisterNetworkCallback(mProfileDefaultNetworkCallback);
        }
        if (null != mTestPackageDefaultNetworkCallback) {
            mCm.unregisterNetworkCallback(mTestPackageDefaultNetworkCallback);
        }
        if (null != mProfileDefaultNetworkCallbackAsAppUid2) {
            mCm.unregisterNetworkCallback(mProfileDefaultNetworkCallbackAsAppUid2);
        }
        if (null != mTestPackageDefaultNetworkCallback2) {
            mCm.unregisterNetworkCallback(mTestPackageDefaultNetworkCallback2);
        }
        mSystemDefaultNetworkCallback = null;
        mDefaultNetworkCallback = null;
        mProfileDefaultNetworkCallback = null;
        mTestPackageDefaultNetworkCallback = null;
        mProfileDefaultNetworkCallbackAsAppUid2 = null;
        mTestPackageDefaultNetworkCallback2 = null;
    }

    private void setupMultipleDefaultNetworksForOemNetworkPreferenceNotCurrentUidTest(
            @OemNetworkPreferences.OemNetworkPreference final int networkPrefToSetup)
            throws Exception {
        final int testPackageNameUid = TEST_PACKAGE_UID;
        final String testPackageName = "per.app.defaults.package";
        setupMultipleDefaultNetworksForOemNetworkPreferenceTest(
                networkPrefToSetup, testPackageNameUid, testPackageName);
    }

    private void setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(
            @OemNetworkPreferences.OemNetworkPreference final int networkPrefToSetup)
            throws Exception {
        final int testPackageNameUid = Process.myUid();
        final String testPackageName = "per.app.defaults.package";
        setupMultipleDefaultNetworksForOemNetworkPreferenceTest(
                networkPrefToSetup, testPackageNameUid, testPackageName);
    }

    private void setupMultipleDefaultNetworksForOemNetworkPreferenceTest(
            @OemNetworkPreferences.OemNetworkPreference final int networkPrefToSetup,
            final int testPackageUid, @NonNull final String testPackageName) throws Exception {
        // Only the default request should be included at start.
        assertEquals(1, mService.mDefaultNetworkRequests.size());

        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(testPackageUid));
        setupSetOemNetworkPreferenceForPreferenceTest(
                networkPrefToSetup, uidRanges, testPackageName);
    }

    private void setupSetOemNetworkPreferenceForPreferenceTest(
            @OemNetworkPreferences.OemNetworkPreference final int networkPrefToSetup,
            @NonNull final UidRangeParcel[] uidRanges,
            @NonNull final String testPackageName) throws Exception {
        setupSetOemNetworkPreferenceForPreferenceTest(networkPrefToSetup, uidRanges,
                testPackageName, PRIMARY_USER_HANDLE, true /* hasAutomotiveFeature */);
    }

    private void setupSetOemNetworkPreferenceForPreferenceTest(
            @OemNetworkPreferences.OemNetworkPreference final int networkPrefToSetup,
            @NonNull final UidRangeParcel[] uidRanges,
            @NonNull final String testPackageName,
            @NonNull final UserHandle user) throws Exception {
        setupSetOemNetworkPreferenceForPreferenceTest(networkPrefToSetup, uidRanges,
                testPackageName, user, true /* hasAutomotiveFeature */);
    }

    private void setupSetOemNetworkPreferenceForPreferenceTest(
            @OemNetworkPreferences.OemNetworkPreference final int networkPrefToSetup,
            @NonNull final UidRangeParcel[] uidRanges,
            @NonNull final String testPackageName,
            @NonNull final UserHandle user,
            final boolean hasAutomotiveFeature) throws Exception {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, hasAutomotiveFeature);

        // These tests work off a single UID therefore using 'start' is valid.
        mockGetApplicationInfo(testPackageName, uidRanges[0].start, user);

        setOemNetworkPreference(networkPrefToSetup, testPackageName);
    }

    private void setOemNetworkPreference(final int networkPrefToSetup,
            @NonNull final String... testPackageNames)
            throws Exception {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);

        // Build OemNetworkPreferences object
        final OemNetworkPreferences.Builder builder = new OemNetworkPreferences.Builder();
        for (final String packageName : testPackageNames) {
            builder.addNetworkPreference(packageName, networkPrefToSetup);
        }
        final OemNetworkPreferences pref = builder.build();

        // Act on ConnectivityService.setOemNetworkPreference()
        final TestOemListenerCallback oemPrefListener = new TestOemListenerCallback();
        mService.setOemNetworkPreference(pref, oemPrefListener);

        // Verify call returned successfully
        oemPrefListener.expectOnComplete();
    }

    private static class TestOemListenerCallback implements IOnCompleteListener {
        final CompletableFuture<Object> mDone = new CompletableFuture<>();

        @Override
        public void onComplete() {
            mDone.complete(new Object());
        }

        void expectOnComplete() {
            try {
                mDone.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                fail("Expected onComplete() not received after " + TIMEOUT_MS + " ms");
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    @Test
    public void testMultiDefaultGetActiveNetworkIsCorrect() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
        final int expectedOemPrefRequestSize = 1;
        registerDefaultNetworkCallbacks();

        // Setup the test process to use networkPref for their default network.
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        // The active network for the default should be null at this point as this is a retricted
        // network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        // Verify that the active network is correct
        verifyActiveNetwork(TRANSPORT_ETHERNET);
        // default NCs will be unregistered in tearDown
    }

    @Test
    public void testMultiDefaultIsActiveNetworkMeteredIsCorrect() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
        final int expectedOemPrefRequestSize = 1;
        registerDefaultNetworkCallbacks();

        // Setup the test process to use networkPref for their default network.
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);

        // Returns true by default when no network is available.
        assertTrue(mCm.isActiveNetworkMetered());

        // Connect to an unmetered restricted network that will only be available to the OEM pref.
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET);
        mEthernetAgent.addCapability(NET_CAPABILITY_OEM_PAID);
        mEthernetAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mEthernetAgent.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        mEthernetAgent.connect(true);
        waitForIdle();

        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        assertFalse(mCm.isActiveNetworkMetered());
        // default NCs will be unregistered in tearDown
    }

    @Test
    public void testPerAppDefaultRegisterDefaultNetworkCallback() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
        final int expectedOemPrefRequestSize = 1;
        final TestNetworkCallback defaultNetworkCallback = new TestNetworkCallback();

        // Register the default network callback before the pref is already set. This means that
        // the policy will be applied to the callback on setOemNetworkPreference().
        mCm.registerDefaultNetworkCallback(defaultNetworkCallback);
        defaultNetworkCallback.assertNoCallback();

        final TestNetworkCallback otherUidDefaultCallback = new TestNetworkCallback();
        withPermission(NETWORK_SETTINGS, () ->
                mCm.registerDefaultNetworkCallbackForUid(TEST_PACKAGE_UID, otherUidDefaultCallback,
                        new Handler(ConnectivityThread.getInstanceLooper())));

        // Setup the test process to use networkPref for their default network.
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        // The active nai for the default is null at this point as this is a restricted network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        // At this point with a restricted network used, the available callback should trigger.
        defaultNetworkCallback.expectAvailableThenValidatedCallbacks(mEthernetAgent);
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mEthernetAgent.getNetwork());
        otherUidDefaultCallback.assertNoCallback();

        // Now bring down the default network which should trigger a LOST callback.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);

        // At this point, with no network is available, the lost callback should trigger
        defaultNetworkCallback.expect(LOST, mEthernetAgent);
        otherUidDefaultCallback.assertNoCallback();

        // Confirm we can unregister without issues.
        mCm.unregisterNetworkCallback(defaultNetworkCallback);
        mCm.unregisterNetworkCallback(otherUidDefaultCallback);
    }

    @Test
    public void testPerAppDefaultRegisterDefaultNetworkCallbackAfterPrefSet() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
        final int expectedOemPrefRequestSize = 1;
        final TestNetworkCallback defaultNetworkCallback = new TestNetworkCallback();

        // Setup the test process to use networkPref for their default network.
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);

        // Register the default network callback after the pref is already set. This means that
        // the policy will be applied to the callback on requestNetwork().
        mCm.registerDefaultNetworkCallback(defaultNetworkCallback);
        defaultNetworkCallback.assertNoCallback();

        final TestNetworkCallback otherUidDefaultCallback = new TestNetworkCallback();
        withPermission(NETWORK_SETTINGS, () ->
                mCm.registerDefaultNetworkCallbackForUid(TEST_PACKAGE_UID, otherUidDefaultCallback,
                        new Handler(ConnectivityThread.getInstanceLooper())));

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        // The active nai for the default is null at this point as this is a restricted network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        // At this point with a restricted network used, the available callback should trigger
        defaultNetworkCallback.expectAvailableThenValidatedCallbacks(mEthernetAgent);
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mEthernetAgent.getNetwork());
        otherUidDefaultCallback.assertNoCallback();

        // Now bring down the default network which should trigger a LOST callback.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        otherUidDefaultCallback.assertNoCallback();

        // At this point, with no network is available, the lost callback should trigger
        defaultNetworkCallback.expect(LOST, mEthernetAgent);
        otherUidDefaultCallback.assertNoCallback();

        // Confirm we can unregister without issues.
        mCm.unregisterNetworkCallback(defaultNetworkCallback);
        mCm.unregisterNetworkCallback(otherUidDefaultCallback);
    }

    @Test
    public void testPerAppDefaultRegisterDefaultNetworkCallbackDoesNotFire() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
        final int expectedOemPrefRequestSize = 1;
        final TestNetworkCallback defaultNetworkCallback = new TestNetworkCallback();
        final int userId = UserHandle.getUserId(Process.myUid());

        mCm.registerDefaultNetworkCallback(defaultNetworkCallback);
        defaultNetworkCallback.assertNoCallback();

        final TestNetworkCallback otherUidDefaultCallback = new TestNetworkCallback();
        withPermission(NETWORK_SETTINGS, () ->
                mCm.registerDefaultNetworkCallbackForUid(TEST_PACKAGE_UID, otherUidDefaultCallback,
                        new Handler(ConnectivityThread.getInstanceLooper())));

        // Setup a process different than the test process to use the default network. This means
        // that the defaultNetworkCallback won't be tracked by the per-app policy.
        setupMultipleDefaultNetworksForOemNetworkPreferenceNotCurrentUidTest(networkPref);

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        // The active nai for the default is null at this point as this is a restricted network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        // As this callback does not have access to the OEM_PAID network, it will not fire.
        defaultNetworkCallback.assertNoCallback();
        assertDefaultNetworkCapabilities(userId /* no networks */);

        // The other UID does have access, and gets a callback.
        otherUidDefaultCallback.expectAvailableThenValidatedCallbacks(mEthernetAgent);

        // Bring up unrestricted cellular. This should now satisfy the default network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // At this point with an unrestricted network used, the available callback should trigger
        // The other UID is unaffected and remains on the paid network.
        defaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(defaultNetworkCallback.getLastAvailableNetwork(), mCellAgent.getNetwork());
        assertDefaultNetworkCapabilities(userId, mCellAgent);
        otherUidDefaultCallback.assertNoCallback();

        // Now bring down the per-app network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);

        // Since the callback didn't use the per-app network, only the other UID gets a callback.
        // Because the preference specifies no fallback, it does not switch to cellular.
        defaultNetworkCallback.assertNoCallback();
        otherUidDefaultCallback.expect(LOST, mEthernetAgent);

        // Now bring down the default network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);

        // As this callback was tracking the default, this should now trigger.
        defaultNetworkCallback.expect(LOST, mCellAgent);
        otherUidDefaultCallback.assertNoCallback();

        // Confirm we can unregister without issues.
        mCm.unregisterNetworkCallback(defaultNetworkCallback);
        mCm.unregisterNetworkCallback(otherUidDefaultCallback);
    }

    /**
     * This method assumes that the same uidRanges input will be used to verify that dependencies
     * are called as expected.
     */
    private void verifySetOemNetworkPreferenceForPreference(
            @NonNull final UidRangeParcel[] uidRanges,
            final int addUidRangesNetId,
            final int addUidRangesTimes,
            final int removeUidRangesNetId,
            final int removeUidRangesTimes,
            final boolean shouldDestroyNetwork) throws RemoteException {
        verifySetOemNetworkPreferenceForPreference(uidRanges, uidRanges,
                addUidRangesNetId, addUidRangesTimes, removeUidRangesNetId, removeUidRangesTimes,
                shouldDestroyNetwork);
    }

    private void verifySetOemNetworkPreferenceForPreference(
            @NonNull final UidRangeParcel[] addedUidRanges,
            @NonNull final UidRangeParcel[] removedUidRanges,
            final int addUidRangesNetId,
            final int addUidRangesTimes,
            final int removeUidRangesNetId,
            final int removeUidRangesTimes,
            final boolean shouldDestroyNetwork) throws RemoteException {
        final boolean useAnyIdForAdd = OEM_PREF_ANY_NET_ID == addUidRangesNetId;
        final boolean useAnyIdForRemove = OEM_PREF_ANY_NET_ID == removeUidRangesNetId;

        // Validate that add/remove uid range (with oem priority) to/from netd.
        verify(mMockNetd, times(addUidRangesTimes)).networkAddUidRangesParcel(argThat(config ->
                (useAnyIdForAdd ? true : addUidRangesNetId == config.netId)
                        && Arrays.equals(addedUidRanges, config.uidRanges)
                        && PREFERENCE_ORDER_OEM == config.subPriority));
        verify(mMockNetd, times(removeUidRangesTimes)).networkRemoveUidRangesParcel(
                argThat(config -> (useAnyIdForRemove ? true : removeUidRangesNetId == config.netId)
                        && Arrays.equals(removedUidRanges, config.uidRanges)
                        && PREFERENCE_ORDER_OEM == config.subPriority));
        if (shouldDestroyNetwork) {
            verify(mMockNetd, times(1))
                    .networkDestroy((useAnyIdForRemove ? anyInt() : eq(removeUidRangesNetId)));
        }
        reset(mMockNetd);
    }

    /**
     * Test the tracked default requests allows test requests without standard setup.
     */
    @Test
    public void testSetOemNetworkPreferenceAllowsValidTestRequestWithoutChecks() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference int networkPref =
                OEM_NETWORK_PREFERENCE_TEST;
        validateSetOemNetworkPreferenceAllowsValidTestPrefRequest(networkPref);
    }

    /**
     * Test the tracked default requests allows test only requests without standard setup.
     */
    @Test
    public void testSetOemNetworkPreferenceAllowsValidTestOnlyRequestWithoutChecks()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference int networkPref =
                OEM_NETWORK_PREFERENCE_TEST_ONLY;
        validateSetOemNetworkPreferenceAllowsValidTestPrefRequest(networkPref);
    }

    private void validateSetOemNetworkPreferenceAllowsValidTestPrefRequest(int networkPref)
            throws Exception {
        // The caller must have the MANAGE_TEST_NETWORKS permission.
        final int testPackageUid = 123;
        final String validTestPackageName = "does.not.matter";
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(testPackageUid));
        mServiceContext.setPermission(
                Manifest.permission.MANAGE_TEST_NETWORKS, PERMISSION_GRANTED);

        // Put the system into a state in which setOemNetworkPreference() would normally fail. This
        // will confirm that a valid test request can bypass these checks.
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, false);
        mServiceContext.setPermission(
                Manifest.permission.CONTROL_OEM_PAID_NETWORK_PREFERENCE, PERMISSION_DENIED);

        // Validate the starting requests only includes the system default request.
        assertEquals(1, mService.mDefaultNetworkRequests.size());

        // Add an OEM default network request to track.
        setupSetOemNetworkPreferenceForPreferenceTest(
                networkPref, uidRanges, validTestPackageName, PRIMARY_USER_HANDLE,
                false /* hasAutomotiveFeature */);

        // Two requests should now exist; the system default and the test request.
        assertEquals(2, mService.mDefaultNetworkRequests.size());
    }

    /**
     * Test the tracked default requests clear previous OEM requests on setOemNetworkPreference().
     */
    @Test
    public void testSetOemNetworkPreferenceClearPreviousOemValues() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;
        final int testPackageUid = 123;
        final String testPackageName = "com.google.apps.contacts";
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(testPackageUid));

        // Validate the starting requests only includes the system default request.
        assertEquals(1, mService.mDefaultNetworkRequests.size());

        // Add an OEM default network request to track.
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, testPackageName);

        // Two requests should exist, one for the fallback and one for the pref.
        assertEquals(2, mService.mDefaultNetworkRequests.size());

        networkPref = OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, testPackageName);

        // Two requests should still exist validating the previous per-app request was replaced.
        assertEquals(2, mService.mDefaultNetworkRequests.size());
    }

    /**
     * Test network priority for preference OEM_NETWORK_PREFERENCE_OEM_PAID in the following order:
     * NET_CAPABILITY_NOT_METERED -> NET_CAPABILITY_OEM_PAID -> fallback
     */
    @Test
    public void testMultilayerForPreferenceOemPaidEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;

        // Arrange PackageManager mocks
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);

        // Verify the starting state. No networks should be connected.
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Test lowest to highest priority requests.
        // Bring up metered cellular. This will satisfy the fallback network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mCellAgent.getNetwork().netId, 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                mCellAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up unmetered Wi-Fi. This will satisfy NET_CAPABILITY_NOT_METERED.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mWiFiAgent.getNetwork().netId, 1 /* times */,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Disconnecting OEM_PAID should have no effect as it is lower in priority then unmetered.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        // netd should not be called as default networks haven't changed.
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Disconnecting unmetered should put PANS on lowest priority fallback request.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mCellAgent.getNetwork().netId, 1 /* times */,
                mWiFiAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);

        // Disconnecting the fallback network should result in no connectivity.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                mCellAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);
    }

    /**
     * Test network priority for OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK in the following order:
     * NET_CAPABILITY_NOT_METERED -> NET_CAPABILITY_OEM_PAID
     */
    @Test
    public void testMultilayerForPreferenceOemPaidNoFallbackEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;

        // Arrange PackageManager mocks
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);

        // Verify the starting state. This preference doesn't support using the fallback network
        // therefore should be on the disconnected network as it has no networks to connect to.
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Test lowest to highest priority requests.
        // Bring up metered cellular. This will satisfy the fallback network.
        // This preference should not use this network as it doesn't support fallback usage.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up unmetered Wi-Fi. This will satisfy NET_CAPABILITY_NOT_METERED.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mWiFiAgent.getNetwork().netId, 1 /* times */,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Disconnecting unmetered should put PANS on OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                mWiFiAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);

        // Disconnecting OEM_PAID should result in no connectivity.
        // OEM_PAID_NO_FALLBACK not supporting a fallback now uses the disconnected network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                mEthernetAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);
    }

    /**
     * Test network priority for OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY in the following order:
     * NET_CAPABILITY_OEM_PAID
     * This preference should only apply to OEM_PAID networks.
     */
    @Test
    public void testMultilayerForPreferenceOemPaidOnlyEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;

        // Arrange PackageManager mocks
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);

        // Verify the starting state. This preference doesn't support using the fallback network
        // therefore should be on the disconnected network as it has no networks to connect to.
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up metered cellular. This should not apply to this preference.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up unmetered Wi-Fi. This should not apply to this preference.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Disconnecting OEM_PAID should result in no connectivity.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                mEthernetAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);
    }

    /**
     * Test network priority for OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY in the following order:
     * NET_CAPABILITY_OEM_PRIVATE
     * This preference should only apply to OEM_PRIVATE networks.
     */
    @Test
    public void testMultilayerForPreferenceOemPrivateOnlyEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;

        // Arrange PackageManager mocks
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);

        // Verify the starting state. This preference doesn't support using the fallback network
        // therefore should be on the disconnected network as it has no networks to connect to.
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up metered cellular. This should not apply to this preference.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up unmetered Wi-Fi. This should not apply to this preference.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Bring up ethernet with OEM_PRIVATE. This will satisfy NET_CAPABILITY_OEM_PRIVATE.
        startOemManagedNetwork(false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mEthernetAgent.getNetwork().netId, 1 /* times */,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Disconnecting OEM_PRIVATE should result in no connectivity.
        stopOemManagedNetwork();
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mService.mNoServiceNetwork.network.getNetId(), 1 /* times */,
                mEthernetAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);
    }

    @Test
    public void testMultilayerForMultipleUsersEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;

        // Arrange users
        final int secondUser = 10;
        final UserHandle secondUserHandle = new UserHandle(secondUser);
        doReturn(asList(PRIMARY_USER_HANDLE, secondUserHandle)).when(mUserManager)
                .getUserHandles(anyBoolean());

        // Arrange PackageManager mocks
        final int secondUserTestPackageUid = UserHandle.getUid(secondUser, TEST_PACKAGE_UID);
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(
                        uidRangesForUids(TEST_PACKAGE_UID, secondUserTestPackageUid));
        mockGetApplicationInfo(TEST_PACKAGE_NAME, secondUserTestPackageUid, secondUserHandle);
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);

        // Verify the starting state. No networks should be connected.
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Test that we correctly add the expected values for multiple users.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                mCellAgent.getNetwork().netId, 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Test that we correctly remove the expected values for multiple users.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);
        verifySetOemNetworkPreferenceForPreference(uidRanges,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                mCellAgent.getNetwork().netId, 0 /* times */,
                true /* shouldDestroyNetwork */);
    }

    @Test
    public void testMultilayerForBroadcastedUsersEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;

        // Arrange users
        final int secondUser = 10;
        final UserHandle secondUserHandle = new UserHandle(secondUser);
        doReturn(asList(PRIMARY_USER_HANDLE)).when(mUserManager).getUserHandles(anyBoolean());

        // Arrange PackageManager mocks
        final int secondUserTestPackageUid = UserHandle.getUid(secondUser, TEST_PACKAGE_UID);
        final UidRangeParcel[] uidRangesSingleUser =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));
        final UidRangeParcel[] uidRangesBothUsers =
                toUidRangeStableParcels(
                        uidRangesForUids(TEST_PACKAGE_UID, secondUserTestPackageUid));
        mockGetApplicationInfo(TEST_PACKAGE_NAME, secondUserTestPackageUid, secondUserHandle);
        setupSetOemNetworkPreferenceForPreferenceTest(
                networkPref, uidRangesSingleUser, TEST_PACKAGE_NAME);

        // Verify the starting state. No networks should be connected.
        verifySetOemNetworkPreferenceForPreference(uidRangesSingleUser,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Test that we correctly add the expected values for multiple users.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRangesSingleUser,
                mCellAgent.getNetwork().netId, 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Send a broadcast indicating a user was added.
        doReturn(asList(PRIMARY_USER_HANDLE, secondUserHandle)).when(mUserManager)
                .getUserHandles(anyBoolean());
        final Intent addedIntent = new Intent(ACTION_USER_ADDED);
        addedIntent.putExtra(Intent.EXTRA_USER, UserHandle.of(secondUser));
        processBroadcast(addedIntent);

        // Test that we correctly add values for all users and remove for the single user.
        verifySetOemNetworkPreferenceForPreference(uidRangesBothUsers, uidRangesSingleUser,
                mCellAgent.getNetwork().netId, 1 /* times */,
                mCellAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Send a broadcast indicating a user was removed.
        doReturn(asList(PRIMARY_USER_HANDLE)).when(mUserManager).getUserHandles(anyBoolean());
        final Intent removedIntent = new Intent(ACTION_USER_REMOVED);
        removedIntent.putExtra(Intent.EXTRA_USER, UserHandle.of(secondUser));
        processBroadcast(removedIntent);

        // Test that we correctly add values for the single user and remove for the all users.
        verifySetOemNetworkPreferenceForPreference(uidRangesSingleUser, uidRangesBothUsers,
                mCellAgent.getNetwork().netId, 1 /* times */,
                mCellAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);
    }

    @Test
    public void testMultilayerForPackageChangesEvaluatesCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;
        final String packageScheme = "package:";

        // Arrange PackageManager mocks
        final String packageToInstall = "package.to.install";
        final int packageToInstallUid = 81387;
        final UidRangeParcel[] uidRangesSinglePackage =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));
        mockGetApplicationInfo(TEST_PACKAGE_NAME, TEST_PACKAGE_UID);
        mockGetApplicationInfoThrowsNameNotFound(packageToInstall, PRIMARY_USER_HANDLE);
        setOemNetworkPreference(networkPref, TEST_PACKAGE_NAME, packageToInstall);
        grantUsingBackgroundNetworksPermissionForUid(Binder.getCallingUid(), packageToInstall);

        // Verify the starting state. No networks should be connected.
        verifySetOemNetworkPreferenceForPreference(uidRangesSinglePackage,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Test that we correctly add the expected values for installed packages.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifySetOemNetworkPreferenceForPreference(uidRangesSinglePackage,
                mCellAgent.getNetwork().netId, 1 /* times */,
                OEM_PREF_ANY_NET_ID, 0 /* times */,
                false /* shouldDestroyNetwork */);

        // Set the system to recognize the package to be installed
        mockGetApplicationInfo(packageToInstall, packageToInstallUid);
        final UidRangeParcel[] uidRangesAllPackages =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID, packageToInstallUid));

        // Send a broadcast indicating a package was installed.
        final Intent addedIntent = new Intent(ACTION_PACKAGE_ADDED);
        addedIntent.setData(Uri.parse(packageScheme + packageToInstall));
        processBroadcast(addedIntent);

        // Test the single package is removed and the combined packages are added.
        verifySetOemNetworkPreferenceForPreference(uidRangesAllPackages, uidRangesSinglePackage,
                mCellAgent.getNetwork().netId, 1 /* times */,
                mCellAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Set the system to no longer recognize the package to be installed
        mockGetApplicationInfoThrowsNameNotFound(packageToInstall, PRIMARY_USER_HANDLE);

        // Send a broadcast indicating a package was removed.
        final Intent removedIntent = new Intent(ACTION_PACKAGE_REMOVED);
        removedIntent.setData(Uri.parse(packageScheme + packageToInstall));
        processBroadcast(removedIntent);

        // Test the combined packages are removed and the single package is added.
        verifySetOemNetworkPreferenceForPreference(uidRangesSinglePackage, uidRangesAllPackages,
                mCellAgent.getNetwork().netId, 1 /* times */,
                mCellAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);

        // Set the system to change the installed package's uid
        final int replacedTestPackageUid = TEST_PACKAGE_UID + 1;
        mockGetApplicationInfo(TEST_PACKAGE_NAME, replacedTestPackageUid);
        final UidRangeParcel[] uidRangesReplacedPackage =
                toUidRangeStableParcels(uidRangesForUids(replacedTestPackageUid));

        // Send a broadcast indicating a package was replaced.
        final Intent replacedIntent = new Intent(ACTION_PACKAGE_REPLACED);
        replacedIntent.setData(Uri.parse(packageScheme + TEST_PACKAGE_NAME));
        processBroadcast(replacedIntent);

        // Test the original uid is removed and is replaced with the new uid.
        verifySetOemNetworkPreferenceForPreference(uidRangesReplacedPackage, uidRangesSinglePackage,
                mCellAgent.getNetwork().netId, 1 /* times */,
                mCellAgent.getNetwork().netId, 1 /* times */,
                false /* shouldDestroyNetwork */);
    }

    /**
     * Test network priority for preference OEM_NETWORK_PREFERENCE_OEM_PAID in the following order:
     * NET_CAPABILITY_NOT_METERED -> NET_CAPABILITY_OEM_PAID -> fallback
     */
    @Test
    public void testMultipleDefaultNetworksTracksOemNetworkPreferenceOemPaidCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);
        final int expectedDefaultRequestSize = 2;
        final int expectedOemPrefRequestSize = 3;
        registerDefaultNetworkCallbacks();

        // The fallback as well as the OEM preference should now be tracked.
        assertEquals(expectedDefaultRequestSize, mService.mDefaultNetworkRequests.size());

        // Test lowest to highest priority requests.
        // Bring up metered cellular. This will satisfy the fallback network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mCellAgent.getNetwork());

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Bring up unmetered Wi-Fi. This will satisfy NET_CAPABILITY_NOT_METERED.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mWiFiAgent.getNetwork(),
                mWiFiAgent.getNetwork());

        // Disconnecting unmetered Wi-Fi will put the pref on OEM_PAID and fallback on cellular.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Disconnecting cellular should keep OEM network on OEM_PAID and fallback will be null.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        // Disconnecting OEM_PAID will put both on null as it is the last network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                null);

        // default callbacks will be unregistered in tearDown
    }

    @Test
    public void testNetworkFactoryRequestsWithMultilayerRequest()
            throws Exception {
        // First use OEM_PAID preference to create a multi-layer request : 1. listen for
        // unmetered, 2. request network with cap OEM_PAID, 3, request the default network for
        // fallback.
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);

        final HandlerThread handlerThread = new HandlerThread("MockFactory");
        handlerThread.start();
        NetworkCapabilities internetFilter = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        final MockNetworkFactory internetFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "internetFactory", internetFilter, mCsHandlerThread);
        internetFactory.setScoreFilter(40);
        internetFactory.register();
        // Default internet request only. The unmetered request is never sent to factories (it's a
        // LISTEN, not requestable). The 3rd (fallback) request in OEM_PAID NRI is TRACK_DEFAULT
        // which is also not sent to factories. Finally, the OEM_PAID request doesn't match the
        // internetFactory filter.
        internetFactory.expectRequestAdds(1);
        internetFactory.assertRequestCountEquals(1);

        NetworkCapabilities oemPaidFilter = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_OEM_PAID)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        final MockNetworkFactory oemPaidFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "oemPaidFactory", oemPaidFilter, mCsHandlerThread);
        oemPaidFactory.setScoreFilter(40);
        oemPaidFactory.register();
        oemPaidFactory.expectRequestAdd(); // Because nobody satisfies the request

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        // A network connected that satisfies the default internet request. For the OEM_PAID
        // preference, this is not as good as an OEM_PAID network, so even if the score of
        // the network is better than the factory announced, it still should try to bring up
        // the network.
        expectNoRequestChanged(oemPaidFactory);
        oemPaidFactory.assertRequestCountEquals(1);
        // The internet factory however is outscored, and should lose its requests.
        internetFactory.expectRequestRemove();
        internetFactory.assertRequestCountEquals(0);

        final NetworkCapabilities oemPaidNc = new NetworkCapabilities();
        oemPaidNc.addCapability(NET_CAPABILITY_OEM_PAID);
        oemPaidNc.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        final TestNetworkAgentWrapper oemPaidAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR,
                new LinkProperties(), oemPaidNc);
        oemPaidAgent.connect(true);

        // The oemPaidAgent has score 50/cell transport, so it beats what the oemPaidFactory can
        // provide, therefore it loses the request.
        oemPaidFactory.expectRequestRemove();
        oemPaidFactory.assertRequestCountEquals(0);
        expectNoRequestChanged(internetFactory);
        internetFactory.assertRequestCountEquals(0);

        oemPaidAgent.setScore(new NetworkScore.Builder().setLegacyInt(20).setExiting(true).build());
        // Now the that the agent is weak, the oemPaidFactory can beat the existing network for the
        // OEM_PAID request. The internet factory however can't beat a network that has OEM_PAID
        // for the preference request, so it doesn't see the request.
        oemPaidFactory.expectRequestAdd();
        oemPaidFactory.assertRequestCountEquals(1);
        expectNoRequestChanged(internetFactory);
        internetFactory.assertRequestCountEquals(0);

        mCellAgent.disconnect();
        // The network satisfying the default internet request has disconnected, so the
        // internetFactory sees the default request again. However there is a network with OEM_PAID
        // connected, so the 2nd OEM_PAID req is already satisfied, so the oemPaidFactory doesn't
        // care about networks that don't have OEM_PAID.
        expectNoRequestChanged(oemPaidFactory);
        oemPaidFactory.assertRequestCountEquals(1);
        internetFactory.expectRequestAdd();
        internetFactory.assertRequestCountEquals(1);

        // Cell connects again, still with score 50. Back to the previous state.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        expectNoRequestChanged(oemPaidFactory);
        oemPaidFactory.assertRequestCountEquals(1);
        internetFactory.expectRequestRemove();
        internetFactory.assertRequestCountEquals(0);

        // Create a request that holds the upcoming wifi network.
        final TestNetworkCallback wifiCallback = new TestNetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build(),
                wifiCallback);

        // Now WiFi connects and it's unmetered, but it's weaker than cell.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.addCapability(NET_CAPABILITY_NOT_METERED);
        mWiFiAgent.setScore(new NetworkScore.Builder().setLegacyInt(30).setExiting(true)
                .build()); // Not the best Internet network, but unmetered
        mWiFiAgent.connect(true);

        // The OEM_PAID preference prefers an unmetered network to an OEM_PAID network, so
        // the oemPaidFactory can't beat wifi no matter how high its score.
        oemPaidFactory.expectRequestRemove();
        expectNoRequestChanged(internetFactory);

        mCellAgent.disconnect();
        // Now that the best internet network (cell, with its 50 score compared to 30 for WiFi
        // at this point), the default internet request is satisfied by a network worse than
        // the internetFactory announced, so it gets the request. However, there is still an
        // unmetered network, so the oemPaidNetworkFactory still can't beat this.
        expectNoRequestChanged(oemPaidFactory);
        internetFactory.expectRequestAdd();
        mCm.unregisterNetworkCallback(wifiCallback);
        handlerThread.quitSafely();
        handlerThread.join();
    }

    /**
     * Test network priority for OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK in the following order:
     * NET_CAPABILITY_NOT_METERED -> NET_CAPABILITY_OEM_PAID
     */
    @Test
    public void testMultipleDefaultNetworksTracksOemNetworkPreferenceOemPaidNoFallbackCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);
        final int expectedDefaultRequestSize = 2;
        final int expectedOemPrefRequestSize = 2;
        registerDefaultNetworkCallbacks();

        // The fallback as well as the OEM preference should now be tracked.
        assertEquals(expectedDefaultRequestSize, mService.mDefaultNetworkRequests.size());

        // Test lowest to highest priority requests.
        // Bring up metered cellular. This will satisfy the fallback network but not the pref.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mService.mNoServiceNetwork.network());

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Bring up unmetered Wi-Fi. This will satisfy NET_CAPABILITY_NOT_METERED.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mWiFiAgent.getNetwork(),
                mWiFiAgent.getNetwork());

        // Disconnecting unmetered Wi-Fi will put the OEM pref on OEM_PAID and fallback on cellular.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Disconnecting cellular should keep OEM network on OEM_PAID and fallback will be null.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mEthernetAgent.getNetwork());

        // Disconnecting OEM_PAID puts the fallback on null and the pref on the disconnected net.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mService.mNoServiceNetwork.network());

        // default callbacks will be unregistered in tearDown
    }

    /**
     * Test network priority for OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY in the following order:
     * NET_CAPABILITY_OEM_PAID
     * This preference should only apply to OEM_PAID networks.
     */
    @Test
    public void testMultipleDefaultNetworksTracksOemNetworkPreferenceOemPaidOnlyCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);
        final int expectedDefaultRequestSize = 2;
        final int expectedOemPrefRequestSize = 1;
        registerDefaultNetworkCallbacks();

        // The fallback as well as the OEM preference should now be tracked.
        assertEquals(expectedDefaultRequestSize, mService.mDefaultNetworkRequests.size());

        // Test lowest to highest priority requests.
        // Bring up metered cellular. This will satisfy the fallback network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mService.mNoServiceNetwork.network());

        // Bring up ethernet with OEM_PAID. This will satisfy NET_CAPABILITY_OEM_PAID.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Bring up unmetered Wi-Fi. The OEM network shouldn't change, the fallback will take Wi-Fi.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mWiFiAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Disconnecting unmetered Wi-Fi shouldn't change the OEM network with fallback on cellular.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Disconnecting OEM_PAID will keep the fallback on cellular and nothing for OEM_PAID.
        // OEM_PAID_ONLY not supporting a fallback now uses the disconnected network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_ETHERNET, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mService.mNoServiceNetwork.network());

        // Disconnecting cellular will put the fallback on null and the pref on disconnected.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mService.mNoServiceNetwork.network());

        // default callbacks will be unregistered in tearDown
    }

    /**
     * Test network priority for OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY in the following order:
     * NET_CAPABILITY_OEM_PRIVATE
     * This preference should only apply to OEM_PRIVATE networks.
     */
    @Test
    public void testMultipleDefaultNetworksTracksOemNetworkPreferenceOemPrivateOnlyCorrectly()
            throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;
        setupMultipleDefaultNetworksForOemNetworkPreferenceCurrentUidTest(networkPref);
        final int expectedDefaultRequestSize = 2;
        final int expectedOemPrefRequestSize = 1;
        registerDefaultNetworkCallbacks();

        // The fallback as well as the OEM preference should now be tracked.
        assertEquals(expectedDefaultRequestSize, mService.mDefaultNetworkRequests.size());

        // Test lowest to highest priority requests.
        // Bring up metered cellular. This will satisfy the fallback network.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mService.mNoServiceNetwork.network());

        // Bring up ethernet with OEM_PRIVATE. This will satisfy NET_CAPABILITY_OEM_PRIVATE.
        startOemManagedNetwork(false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Bring up unmetered Wi-Fi. The OEM network shouldn't change, the fallback will take Wi-Fi.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, true);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mWiFiAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Disconnecting unmetered Wi-Fi shouldn't change the OEM network with fallback on cellular.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_WIFI, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mEthernetAgent.getNetwork());

        // Disconnecting OEM_PRIVATE will keep the fallback on cellular.
        // OEM_PRIVATE_ONLY not supporting a fallback now uses to the disconnected network.
        stopOemManagedNetwork();
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                mCellAgent.getNetwork(),
                mService.mNoServiceNetwork.network());

        // Disconnecting cellular will put the fallback on null and pref on disconnected.
        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, false);
        verifyMultipleDefaultNetworksTracksCorrectly(expectedOemPrefRequestSize,
                null,
                mService.mNoServiceNetwork.network());

        // default callbacks will be unregistered in tearDown
    }

    @Test
    public void testCapabilityWithOemNetworkPreference() throws Exception {
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;
        setupMultipleDefaultNetworksForOemNetworkPreferenceNotCurrentUidTest(networkPref);
        registerDefaultNetworkCallbacks();

        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);

        mSystemDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        mCellAgent.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        mSystemDefaultNetworkCallback.expectCaps(mCellAgent,
                c -> c.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        mDefaultNetworkCallback.expectCaps(mCellAgent,
                c -> c.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        // default callbacks will be unregistered in tearDown
    }

    @Test
    public void testSetOemNetworkPreferenceLogsRequest() throws Exception {
        mServiceContext.setPermission(DUMP, PERMISSION_GRANTED);
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;
        final StringWriter stringWriter = new StringWriter();
        final String logIdentifier = "UPDATE INITIATED: OemNetworkPreferences";
        final Pattern pattern = Pattern.compile(logIdentifier);

        final int expectedNumLogs = 2;
        final UidRangeParcel[] uidRanges =
                toUidRangeStableParcels(uidRangesForUids(TEST_PACKAGE_UID));

        // Call twice to generate two logs.
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges, TEST_PACKAGE_NAME);
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);

        final String dumpOutput = stringWriter.toString();
        final Matcher matcher = pattern.matcher(dumpOutput);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(expectedNumLogs, count);
    }

    @Test
    public void testGetAllNetworkStateSnapshots() throws Exception {
        verifyNoNetwork();

        // Setup test cellular network with specified LinkProperties and NetworkCapabilities,
        // verify the content of the snapshot matches.
        final LinkProperties cellLp = new LinkProperties();
        final LinkAddress myIpv4Addr = new LinkAddress(InetAddress.getByName("192.0.2.129"), 25);
        final LinkAddress myIpv6Addr = new LinkAddress(InetAddress.getByName("2001:db8::1"), 64);
        cellLp.setInterfaceName("test01");
        cellLp.addLinkAddress(myIpv4Addr);
        cellLp.addLinkAddress(myIpv6Addr);
        cellLp.addRoute(new RouteInfo(InetAddress.getByName("fe80::1234")));
        cellLp.addRoute(new RouteInfo(InetAddress.getByName("192.0.2.254")));
        cellLp.addRoute(new RouteInfo(myIpv4Addr, null));
        cellLp.addRoute(new RouteInfo(myIpv6Addr, null));
        final NetworkCapabilities cellNcTemplate = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR).addCapability(NET_CAPABILITY_MMS).build();

        final TestNetworkCallback cellCb = new TestNetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder().addCapability(NET_CAPABILITY_MMS).build(),
                cellCb);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp, cellNcTemplate);
        mCellAgent.connect(true);
        cellCb.expectAvailableCallbacksUnvalidated(mCellAgent);
        List<NetworkStateSnapshot> snapshots = mCm.getAllNetworkStateSnapshots();
        assertLength(1, snapshots);

        // Compose the expected cellular snapshot for verification.
        final NetworkCapabilities cellNc =
                mCm.getNetworkCapabilities(mCellAgent.getNetwork());
        final NetworkStateSnapshot cellSnapshot = new NetworkStateSnapshot(
                mCellAgent.getNetwork(), cellNc, cellLp,
                null, ConnectivityManager.TYPE_MOBILE);
        assertEquals(cellSnapshot, snapshots.get(0));

        // Connect wifi and verify the snapshots.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        waitForIdle();
        // Compose the expected wifi snapshot for verification.
        final NetworkCapabilities wifiNc =
                mCm.getNetworkCapabilities(mWiFiAgent.getNetwork());
        final NetworkStateSnapshot wifiSnapshot = new NetworkStateSnapshot(
                mWiFiAgent.getNetwork(), wifiNc, new LinkProperties(), null,
                ConnectivityManager.TYPE_WIFI);

        snapshots = mCm.getAllNetworkStateSnapshots();
        assertLength(2, snapshots);
        assertContainsAll(snapshots, cellSnapshot, wifiSnapshot);

        // Set cellular as suspended, verify the snapshots will contain suspended networks.
        mCellAgent.suspend();
        waitForIdle();
        final NetworkCapabilities cellSuspendedNc =
                mCm.getNetworkCapabilities(mCellAgent.getNetwork());
        assertFalse(cellSuspendedNc.hasCapability(NET_CAPABILITY_NOT_SUSPENDED));
        final NetworkStateSnapshot cellSuspendedSnapshot = new NetworkStateSnapshot(
                mCellAgent.getNetwork(), cellSuspendedNc, cellLp,
                null, ConnectivityManager.TYPE_MOBILE);
        snapshots = mCm.getAllNetworkStateSnapshots();
        assertLength(2, snapshots);
        assertContainsAll(snapshots, cellSuspendedSnapshot, wifiSnapshot);

        // Disconnect wifi, verify the snapshots contain only cellular.
        mWiFiAgent.disconnect();
        waitForIdle();
        snapshots = mCm.getAllNetworkStateSnapshots();
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetwork());
        assertLength(1, snapshots);
        assertEquals(cellSuspendedSnapshot, snapshots.get(0));

        mCellAgent.resume();
        waitForIdle();
        snapshots = mCm.getAllNetworkStateSnapshots();
        assertLength(1, snapshots);
        assertEquals(cellSnapshot, snapshots.get(0));

        mCellAgent.disconnect();
        waitForIdle();
        verifyNoNetwork();
        mCm.unregisterNetworkCallback(cellCb);
    }

    // Cannot be part of MockNetworkFactory since it requires method of the test.
    private void expectNoRequestChanged(@NonNull MockNetworkFactory factory) {
        waitForIdle();
        factory.assertNoRequestChanged();
    }

    @Test
    public void testRegisterBestMatchingNetworkCallback_noIssueToFactory() throws Exception {
        // Prepare mock mms factory.
        final HandlerThread handlerThread = new HandlerThread("MockCellularFactory");
        handlerThread.start();
        NetworkCapabilities filter = new NetworkCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_MMS);
        final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactory", filter, mCsHandlerThread);
        testFactory.setScoreFilter(40);

        try {
            // Register the factory. It doesn't see the default request because its filter does
            // not include INTERNET.
            testFactory.register();
            expectNoRequestChanged(testFactory);
            testFactory.assertRequestCountEquals(0);
            // The factory won't try to start the network since the default request doesn't
            // match the filter (no INTERNET capability).
            assertFalse(testFactory.getMyStartRequested());

            // Register callback for listening best matching network. Verify that the request won't
            // be sent to factory.
            final TestNetworkCallback bestMatchingCb = new TestNetworkCallback();
            mCm.registerBestMatchingNetworkCallback(
                    new NetworkRequest.Builder().addCapability(NET_CAPABILITY_MMS).build(),
                    bestMatchingCb, mCsHandlerThread.getThreadHandler());
            bestMatchingCb.assertNoCallback();
            expectNoRequestChanged(testFactory);
            testFactory.assertRequestCountEquals(0);
            assertFalse(testFactory.getMyStartRequested());

            // Fire a normal mms request, verify the factory will only see the request.
            final TestNetworkCallback mmsNetworkCallback = new TestNetworkCallback();
            final NetworkRequest mmsRequest = new NetworkRequest.Builder()
                    .addCapability(NET_CAPABILITY_MMS).build();
            mCm.requestNetwork(mmsRequest, mmsNetworkCallback);
            testFactory.expectRequestAdd();
            testFactory.assertRequestCountEquals(1);
            assertTrue(testFactory.getMyStartRequested());

            // Unregister best matching callback, verify factory see no change.
            mCm.unregisterNetworkCallback(bestMatchingCb);
            expectNoRequestChanged(testFactory);
            testFactory.assertRequestCountEquals(1);
            assertTrue(testFactory.getMyStartRequested());
        } finally {
            testFactory.terminate();
            handlerThread.quitSafely();
            handlerThread.join();
        }
    }

    @Test
    public void testRegisterBestMatchingNetworkCallback_trackBestNetwork() throws Exception {
        final TestNetworkCallback bestMatchingCb = new TestNetworkCallback();
        mCm.registerBestMatchingNetworkCallback(
                new NetworkRequest.Builder().addCapability(NET_CAPABILITY_TRUSTED).build(),
                bestMatchingCb, mCsHandlerThread.getThreadHandler());

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        bestMatchingCb.expectAvailableThenValidatedCallbacks(mCellAgent);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        bestMatchingCb.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);

        // Change something on cellular to trigger capabilities changed, since the callback
        // only cares about the best network, verify it received nothing from cellular.
        mCellAgent.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        bestMatchingCb.assertNoCallback();

        // Make cellular the best network again, verify the callback now tracks cellular.
        mWiFiAgent.adjustScore(-50);
        bestMatchingCb.expectAvailableCallbacksValidated(mCellAgent);

        // Make cellular temporary non-trusted, which will not satisfying the request.
        // Verify the callback switch from/to the other network accordingly.
        mCellAgent.removeCapability(NET_CAPABILITY_TRUSTED);
        bestMatchingCb.expectAvailableCallbacksValidated(mWiFiAgent);
        mCellAgent.addCapability(NET_CAPABILITY_TRUSTED);
        bestMatchingCb.expectAvailableDoubleValidatedCallbacks(mCellAgent);

        // Verify the callback doesn't care about wifi disconnect.
        mWiFiAgent.disconnect();
        bestMatchingCb.assertNoCallback();
        mCellAgent.disconnect();
        bestMatchingCb.expect(LOST, mCellAgent);
    }

    private UidRangeParcel[] uidRangeFor(final UserHandle handle) {
        final UidRange range = UidRange.createForUser(handle);
        return new UidRangeParcel[] { new UidRangeParcel(range.start, range.stop) };
    }

    private UidRangeParcel[] uidRangeFor(final UserHandle handle,
            ProfileNetworkPreference profileNetworkPreference) {
        final Set<UidRange> uidRangeSet;
        UidRange range = UidRange.createForUser(handle);
        if (profileNetworkPreference.getIncludedUids().length != 0) {
            uidRangeSet = UidRangeUtils.convertArrayToUidRange(
                    profileNetworkPreference.getIncludedUids());

        } else if (profileNetworkPreference.getExcludedUids().length != 0)  {
            uidRangeSet = UidRangeUtils.removeRangeSetFromUidRange(
                    range, UidRangeUtils.convertArrayToUidRange(
                            profileNetworkPreference.getExcludedUids()));
        } else {
            uidRangeSet = new ArraySet<>();
            uidRangeSet.add(range);
        }
        UidRangeParcel[] uidRangeParcels = new UidRangeParcel[uidRangeSet.size()];
        int i = 0;
        for (UidRange range1 : uidRangeSet) {
            uidRangeParcels[i] = new UidRangeParcel(range1.start, range1.stop);
            i++;
        }
        return uidRangeParcels;
    }

    private static class TestOnCompleteListener implements Runnable {
        final class OnComplete {}
        final ArrayTrackRecord<OnComplete>.ReadHead mHistory =
                new ArrayTrackRecord<OnComplete>().newReadHead();

        @Override
        public void run() {
            mHistory.add(new OnComplete());
        }

        public void expectOnComplete() {
            assertNotNull(mHistory.poll(TIMEOUT_MS, it -> true));
        }
    }

    private TestNetworkAgentWrapper makeEnterpriseNetworkAgent() throws Exception {
        final NetworkCapabilities workNc = new NetworkCapabilities();
        workNc.addCapability(NET_CAPABILITY_ENTERPRISE);
        workNc.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        return new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, new LinkProperties(), workNc);
    }

    private TestNetworkAgentWrapper makeEnterpriseNetworkAgent(int enterpriseId) throws Exception {
        final NetworkCapabilities workNc = new NetworkCapabilities();
        workNc.addCapability(NET_CAPABILITY_ENTERPRISE);
        workNc.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        workNc.addEnterpriseId(enterpriseId);
        return new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, new LinkProperties(), workNc);
    }

    private TestNetworkCallback mEnterpriseCallback;
    private UserHandle setupEnterpriseNetwork() {
        final UserHandle userHandle = UserHandle.of(TEST_WORK_PROFILE_USER_ID);
        mServiceContext.setWorkProfile(userHandle, true);

        // File a request to avoid the enterprise network being disconnected as soon as the default
        // request goes away – it would make impossible to test that networkRemoveUidRanges
        // is called, as the network would disconnect first for lack of a request.
        mEnterpriseCallback = new TestNetworkCallback();
        final NetworkRequest keepUpRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .build();
        mCm.requestNetwork(keepUpRequest, mEnterpriseCallback);
        return userHandle;
    }

    private void maybeTearDownEnterpriseNetwork() {
        if (null != mEnterpriseCallback) {
            mCm.unregisterNetworkCallback(mEnterpriseCallback);
        }
    }

    /**
     * Make sure per profile network preferences behave as expected for a given
     * profile network preference.
     */
    private void doTestPreferenceForUserNetworkUpDownForGivenPreference(
            ProfileNetworkPreference profileNetworkPreference,
            boolean connectWorkProfileAgentAhead,
            UserHandle testHandle,
            TestNetworkCallback profileDefaultNetworkCallback,
            TestNetworkCallback disAllowProfileDefaultNetworkCallback) throws Exception {
        final InOrder inOrder = inOrder(mMockNetd, mMockDnsResolver);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        mSystemDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        profileDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        if (disAllowProfileDefaultNetworkCallback != null) {
            disAllowProfileDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        }
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));

        final TestNetworkAgentWrapper workAgent =
                makeEnterpriseNetworkAgent(profileNetworkPreference.getPreferenceEnterpriseId());
        if (mService.shouldCreateNetworksImmediately()) {
            expectNativeNetworkCreated(workAgent.getNetwork().netId, INetd.PERMISSION_SYSTEM,
                    null /* iface */, inOrder);
        }
        if (connectWorkProfileAgentAhead) {
            workAgent.connect(false);
            if (!mService.shouldCreateNetworksImmediately()) {
                expectNativeNetworkCreated(workAgent.getNetwork().netId, INetd.PERMISSION_SYSTEM,
                        null /* iface */, inOrder);
            }
        }

        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreferences(testHandle, List.of(profileNetworkPreference),
                r -> r.run(), listener);
        listener.expectOnComplete();
        boolean allowFallback = true;
        if (profileNetworkPreference.getPreference()
                == PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK) {
            allowFallback = false;
        }
        if (allowFallback && !connectWorkProfileAgentAhead) {
            // Setting a network preference for this user will create a new set of routing rules for
            // the UID range that corresponds to this user, inorder to define the default network
            // for these apps separately. This is true because the multi-layer request relevant to
            // this UID range contains a TRACK_DEFAULT, so the range will be moved through
            // UID-specific rules to the correct network – in this case the system default network.
            // The case where the default network for the profile happens to be the same as the
            // system default is not handled specially, the rules are always active as long as
            // a preference is set.
            inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                    mCellAgent.getNetwork().netId,
                    uidRangeFor(testHandle, profileNetworkPreference),
                    PREFERENCE_ORDER_PROFILE));
        }

        // The enterprise network is not ready yet.
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        if (allowFallback && !connectWorkProfileAgentAhead) {
            assertNoCallbacks(profileDefaultNetworkCallback);
        } else if (!connectWorkProfileAgentAhead) {
            profileDefaultNetworkCallback.expect(LOST, mCellAgent);
            if (disAllowProfileDefaultNetworkCallback != null) {
                assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
            }
        }

        if (!connectWorkProfileAgentAhead) {
            workAgent.connect(false);
            if (!mService.shouldCreateNetworksImmediately()) {
                inOrder.verify(mMockNetd).networkCreate(
                        nativeNetworkConfigPhysical(workAgent.getNetwork().netId,
                                INetd.PERMISSION_SYSTEM));
            }
        }

        profileDefaultNetworkCallback.expectAvailableCallbacksUnvalidated(workAgent);
        if (disAllowProfileDefaultNetworkCallback != null) {
            disAllowProfileDefaultNetworkCallback.assertNoCallback();
        }
        mSystemDefaultNetworkCallback.assertNoCallback();
        mDefaultNetworkCallback.assertNoCallback();
        inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreference),
                PREFERENCE_ORDER_PROFILE));

        if (allowFallback && !connectWorkProfileAgentAhead) {
            inOrder.verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                    mCellAgent.getNetwork().netId,
                    uidRangeFor(testHandle, profileNetworkPreference),
                    PREFERENCE_ORDER_PROFILE));
        }

        // Make sure changes to the work agent send callbacks to the app in the work profile, but
        // not to the other apps.
        workAgent.setNetworkValid(true /* privateDnsProbeSent */);
        workAgent.mNetworkMonitor.forceReevaluation(Process.myUid());
        profileDefaultNetworkCallback.expectCaps(workAgent,
                c -> c.hasCapability(NET_CAPABILITY_VALIDATED)
                        && c.hasCapability(NET_CAPABILITY_ENTERPRISE)
                        && c.hasEnterpriseId(profileNetworkPreference.getPreferenceEnterpriseId())
                        && c.getEnterpriseIds().length == 1);
        if (disAllowProfileDefaultNetworkCallback != null) {
            assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
        }
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);

        workAgent.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        profileDefaultNetworkCallback.expectCaps(workAgent,
                c -> c.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        if (disAllowProfileDefaultNetworkCallback != null) {
            assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
        }
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);

        // Conversely, change a capability on the system-wide default network and make sure
        // that only the apps outside of the work profile receive the callbacks.
        mCellAgent.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        mSystemDefaultNetworkCallback.expectCaps(mCellAgent,
                c -> c.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        mDefaultNetworkCallback.expectCaps(mCellAgent,
                c -> c.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        if (disAllowProfileDefaultNetworkCallback != null) {
            disAllowProfileDefaultNetworkCallback.expectCaps(mCellAgent,
                    c -> c.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        }
        profileDefaultNetworkCallback.assertNoCallback();

        // Disconnect and reconnect the system-wide default network and make sure that the
        // apps on this network see the appropriate callbacks, and the app on the work profile
        // doesn't because it continues to use the enterprise network.
        mCellAgent.disconnect();
        mSystemDefaultNetworkCallback.expect(LOST, mCellAgent);
        mDefaultNetworkCallback.expect(LOST, mCellAgent);
        if (disAllowProfileDefaultNetworkCallback != null) {
            disAllowProfileDefaultNetworkCallback.expect(LOST, mCellAgent);
        }
        profileDefaultNetworkCallback.assertNoCallback();
        inOrder.verify(mMockNetd).networkDestroy(mCellAgent.getNetwork().netId);

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        mSystemDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        if (disAllowProfileDefaultNetworkCallback != null) {
            disAllowProfileDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        }
        profileDefaultNetworkCallback.assertNoCallback();
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));

        // When the agent disconnects, test that the app on the work profile falls back to the
        // default network.
        workAgent.disconnect();
        profileDefaultNetworkCallback.expect(LOST, workAgent);
        if (allowFallback) {
            profileDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
            if (disAllowProfileDefaultNetworkCallback != null) {
                assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
            }
        }
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        if (allowFallback) {
            inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                    mCellAgent.getNetwork().netId,
                    uidRangeFor(testHandle, profileNetworkPreference),
                    PREFERENCE_ORDER_PROFILE));
        }
        inOrder.verify(mMockNetd).networkDestroy(workAgent.getNetwork().netId);

        mCellAgent.disconnect();
        mSystemDefaultNetworkCallback.expect(LOST, mCellAgent);
        mDefaultNetworkCallback.expect(LOST, mCellAgent);
        if (disAllowProfileDefaultNetworkCallback != null) {
            disAllowProfileDefaultNetworkCallback.expect(LOST, mCellAgent);
        }
        if (allowFallback) {
            profileDefaultNetworkCallback.expect(LOST, mCellAgent);
        }

        // Waiting for the handler to be idle before checking for networkDestroy is necessary
        // here because ConnectivityService calls onLost before the network is fully torn down.
        waitForIdle();
        inOrder.verify(mMockNetd).networkDestroy(mCellAgent.getNetwork().netId);

        // If the control comes here, callbacks seem to behave correctly in the presence of
        // a default network when the enterprise network goes up and down. Now, make sure they
        // also behave correctly in the absence of a system-wide default network.
        final TestNetworkAgentWrapper workAgent2 =
                makeEnterpriseNetworkAgent(profileNetworkPreference.getPreferenceEnterpriseId());
        workAgent2.connect(false);

        profileDefaultNetworkCallback.expectAvailableCallbacksUnvalidated(workAgent2);
        if (disAllowProfileDefaultNetworkCallback != null) {
            assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
        }
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent2.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent2.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreference), PREFERENCE_ORDER_PROFILE));

        workAgent2.setNetworkValid(true /* privateDnsProbeSent */);
        workAgent2.mNetworkMonitor.forceReevaluation(Process.myUid());
        profileDefaultNetworkCallback.expectCaps(workAgent2,
                c -> c.hasCapability(NET_CAPABILITY_ENTERPRISE)
                        && !c.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)
                        && c.hasEnterpriseId(profileNetworkPreference.getPreferenceEnterpriseId())
                        && c.getEnterpriseIds().length == 1);
        if (disAllowProfileDefaultNetworkCallback != null) {
            assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
        }
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        inOrder.verify(mMockNetd, never()).networkAddUidRangesParcel(any());

        // When the agent disconnects, test that the app on the work profile fall back to the
        // default network.
        workAgent2.disconnect();
        profileDefaultNetworkCallback.expect(LOST, workAgent2);
        if (disAllowProfileDefaultNetworkCallback != null) {
            assertNoCallbacks(disAllowProfileDefaultNetworkCallback);
        }
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        inOrder.verify(mMockNetd).networkDestroy(workAgent2.getNetwork().netId);

        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback,
                profileDefaultNetworkCallback);

        // Callbacks will be unregistered by tearDown()
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active. Make sure they behave as expected whether
     * there is a general default network or not.
     */
    @Test
    public void testPreferenceForUserNetworkUpDown() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        registerDefaultNetworkCallbacks();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), false,
                testHandle, mProfileDefaultNetworkCallback, null);
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active. Make sure they behave as expected whether
     * there is a general default network or not when configured to not fallback to default network.
     */
    @Test
    public void testPreferenceForUserNetworkUpDownWithNoFallback() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), false,
                testHandle, mProfileDefaultNetworkCallback, null);
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active. Make sure they behave as expected whether
     * there is a general default network or not when configured to not fallback to default network
     * along with already connected enterprise work agent
     */
    @Test
    public void testPreferenceForUserNetworkUpDownWithNoFallbackWithAlreadyConnectedWorkAgent()
            throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), true, testHandle,
                mProfileDefaultNetworkCallback, null);
    }

    /**
     * Make sure per-profile networking preference for specific uid of test handle
     * behaves as expected
     */
    @Test
    public void testPreferenceForDefaultUidOfTestHandle() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder.setIncludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID)});
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), false, testHandle,
                mProfileDefaultNetworkCallback, null);
    }

    /**
     * Make sure per-profile networking preference for specific uid of test handle
     * behaves as expected
     */
    @Test
    public void testPreferenceForSpecificUidOfOnlyOneApp() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder.setIncludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), false,
                testHandle, mProfileDefaultNetworkCallbackAsAppUid2, null);
    }

    /**
     * Make sure per-profile networking preference for specific uid of test handle
     * behaves as expected
     */
    @Test
    public void testPreferenceForDisallowSpecificUidOfApp() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder.setExcludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), false,
                testHandle, mProfileDefaultNetworkCallback,
                mProfileDefaultNetworkCallbackAsAppUid2);
    }

    /**
     * Make sure per-profile networking preference for specific uid of test handle
     * invalid uid inputs
     */
    @Test
    public void testPreferenceForInvalidUids() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder.setExcludedUids(
                new int[]{testHandle.getUid(0) - 1});
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        Assert.assertThrows(IllegalArgumentException.class, () -> mCm.setProfileNetworkPreferences(
                testHandle, List.of(profileNetworkPreferenceBuilder.build()),
                r -> r.run(), listener));

        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setIncludedUids(
                new int[]{testHandle.getUid(0) - 1});
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder.build()),
                        r -> r.run(), listener));


        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setIncludedUids(
                new int[]{testHandle.getUid(0) - 1});
        profileNetworkPreferenceBuilder.setExcludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder.build()),
                        r -> r.run(), listener));

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder2 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder2.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder2.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder2.setIncludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        profileNetworkPreferenceBuilder.setIncludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder.build(),
                                profileNetworkPreferenceBuilder2.build()),
                        r -> r.run(), listener));

        profileNetworkPreferenceBuilder2.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder2.setExcludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        profileNetworkPreferenceBuilder.setExcludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder.build(),
                                profileNetworkPreferenceBuilder2.build()),
                        r -> r.run(), listener));

        profileNetworkPreferenceBuilder2.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder2.setExcludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        profileNetworkPreferenceBuilder.setExcludedUids(
                new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID_2)});
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder.build(),
                                profileNetworkPreferenceBuilder2.build()),
                        r -> r.run(), listener));
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active. Make sure they behave as expected whether
     * there is a general default network or not when configured to fallback to default network
     * along with already connected enterprise work agent
     */
    @Test
    public void testPreferenceForUserNetworkUpDownWithFallbackWithAlreadyConnectedWorkAgent()
            throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), true,
                testHandle, mProfileDefaultNetworkCallback,
                null);
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active for a given enterprise identifier
     */
    @Test
    public void testPreferenceForUserNetworkUpDownWithDefaultEnterpriseId()
            throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), true,
                testHandle, mProfileDefaultNetworkCallback,
                null);
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active for a given enterprise identifier
     */
    @Test
    public void testPreferenceForUserNetworkUpDownWithId2()
            throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(
                NET_ENTERPRISE_ID_2);
        registerDefaultNetworkCallbacks();
        doTestPreferenceForUserNetworkUpDownForGivenPreference(
                profileNetworkPreferenceBuilder.build(), true,
                testHandle, mProfileDefaultNetworkCallback, null);
    }

    /**
     * Make sure per-profile networking preference behaves as expected when the enterprise network
     * goes up and down while the preference is active for a given enterprise identifier
     */
    @Test
    public void testPreferenceForUserNetworkUpDownWithInvalidId()
            throws Exception {
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(0);
        registerDefaultNetworkCallbacks();
        assertThrows("Should not be able to set invalid enterprise id",
                IllegalStateException.class, () -> profileNetworkPreferenceBuilder.build());
    }

    /**
     * Make sure per-profile networking preference throws exception when default preference
     * is set along with enterprise preference.
     */
    @Test
    public void testPreferenceWithInvalidPreferenceDefaultAndEnterpriseTogether()
            throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        mServiceContext.setWorkProfile(testHandle, true);

        final int testWorkProfileAppUid1 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID);
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder1 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder1.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder1.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder1.setIncludedUids(new int[]{testWorkProfileAppUid1});

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder2 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder2.setPreference(PROFILE_NETWORK_PREFERENCE_DEFAULT);
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder1.build(),
                                profileNetworkPreferenceBuilder2.build()),
                        r -> r.run(), listener));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(
                        testHandle, List.of(profileNetworkPreferenceBuilder2.build(),
                                profileNetworkPreferenceBuilder1.build()),
                        r -> r.run(), listener));
    }

    /**
     * Make sure per profile network preferences behave as expected when two slices with
     * two different apps within same user profile is configured
     * Make sure per profile network preferences overrides with latest preference when
     * same user preference is set twice
     */
    @Test
    public void testSetPreferenceWithOverridingPreference()
            throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        final UserHandle testHandle = setupEnterpriseNetwork();
        mServiceContext.setWorkProfile(testHandle, true);
        registerDefaultNetworkCallbacks();

        final TestNetworkCallback appCb1 = new TestNetworkCallback();
        final TestNetworkCallback appCb2 = new TestNetworkCallback();
        final TestNetworkCallback appCb3 = new TestNetworkCallback();

        final int testWorkProfileAppUid1 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID);
        final int testWorkProfileAppUid2 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID_2);
        final int testWorkProfileAppUid3 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID_3);

        registerDefaultNetworkCallbackAsUid(appCb1, testWorkProfileAppUid1);
        registerDefaultNetworkCallbackAsUid(appCb2, testWorkProfileAppUid2);
        registerDefaultNetworkCallbackAsUid(appCb3, testWorkProfileAppUid3);

        // Connect both a regular cell agent and an enterprise network first.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        final TestNetworkAgentWrapper workAgent1 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_1);
        final TestNetworkAgentWrapper workAgent2 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_2);
        workAgent1.connect(true);
        workAgent2.connect(true);

        mSystemDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        appCb1.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb2.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb3.expectAvailableThenValidatedCallbacks(mCellAgent);

        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent1.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent2.getNetwork().netId, INetd.PERMISSION_SYSTEM));

        final TestOnCompleteListener listener = new TestOnCompleteListener();

        // Set preferences for testHandle to map testWorkProfileAppUid1 to
        // NET_ENTERPRISE_ID_1 and testWorkProfileAppUid2 to NET_ENTERPRISE_ID_2.
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder1 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder1.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder1.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder1.setIncludedUids(new int[]{testWorkProfileAppUid1});

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder2 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder2.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder2.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_2);
        profileNetworkPreferenceBuilder2.setIncludedUids(new int[]{testWorkProfileAppUid2});

        mCm.setProfileNetworkPreferences(testHandle,
                List.of(profileNetworkPreferenceBuilder1.build(),
                        profileNetworkPreferenceBuilder2.build()),
                r -> r.run(), listener);
        listener.expectOnComplete();
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent2.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder2.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent1.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder1.build()),
                PREFERENCE_ORDER_PROFILE));

        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        appCb1.expectAvailableCallbacksValidated(workAgent1);
        appCb2.expectAvailableCallbacksValidated(workAgent2);

        // Set preferences for testHandle to map testWorkProfileAppUid3 to
        // to NET_ENTERPRISE_ID_1.
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder3 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder3.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder3.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder3.setIncludedUids(new int[]{testWorkProfileAppUid3});

        mCm.setProfileNetworkPreferences(testHandle,
                List.of(profileNetworkPreferenceBuilder3.build()),
                r -> r.run(), listener);
        listener.expectOnComplete();
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent1.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder3.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                workAgent2.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder2.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                workAgent1.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder1.build()),
                PREFERENCE_ORDER_PROFILE));

        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        appCb3.expectAvailableCallbacksValidated(workAgent1);
        appCb2.expectAvailableCallbacksValidated(mCellAgent);
        appCb1.expectAvailableCallbacksValidated(mCellAgent);

        // Set the preferences for testHandle to default.
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_DEFAULT);

        mCm.setProfileNetworkPreferences(testHandle,
                List.of(profileNetworkPreferenceBuilder.build()),
                r -> r.run(), listener);
        listener.expectOnComplete();
        verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                workAgent1.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder3.build()),
                PREFERENCE_ORDER_PROFILE));

        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback, appCb1, appCb2);
        appCb3.expectAvailableCallbacksValidated(mCellAgent);
        workAgent2.disconnect();
        mCellAgent.disconnect();

        mCm.unregisterNetworkCallback(appCb1);
        mCm.unregisterNetworkCallback(appCb2);
        mCm.unregisterNetworkCallback(appCb3);
        // Other callbacks will be unregistered by tearDown()
    }

    private NetworkCallback requestForEnterpriseId(@NetworkCapabilities.EnterpriseId final int id) {
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_ENTERPRISE).addEnterpriseId(id).build();
        final NetworkRequest req = new NetworkRequest.Builder().setCapabilities(nc).build();
        final NetworkCallback cb = new TestableNetworkCallback();
        mCm.requestNetwork(req, cb);
        return cb;
    }

    /**
     * Make sure per profile network preferences behave as expected when multiple slices with
     * multiple different apps within same user profile is configured.
     */
    @Test
    public void testSetPreferenceWithMultiplePreferences()
            throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        mServiceContext.setWorkProfile(testHandle, true);
        registerDefaultNetworkCallbacks();

        final TestNetworkCallback appCb1 = new TestNetworkCallback();
        final TestNetworkCallback appCb2 = new TestNetworkCallback();
        final TestNetworkCallback appCb3 = new TestNetworkCallback();
        final TestNetworkCallback appCb4 = new TestNetworkCallback();
        final TestNetworkCallback appCb5 = new TestNetworkCallback();

        final int testWorkProfileAppUid1 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID);
        final int testWorkProfileAppUid2 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID_2);
        final int testWorkProfileAppUid3 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID_3);
        final int testWorkProfileAppUid4 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID_4);
        final int testWorkProfileAppUid5 =
                UserHandle.getUid(testHandle.getIdentifier(), TEST_APP_ID_5);

        registerDefaultNetworkCallbackAsUid(appCb1, testWorkProfileAppUid1);
        registerDefaultNetworkCallbackAsUid(appCb2, testWorkProfileAppUid2);
        registerDefaultNetworkCallbackAsUid(appCb3, testWorkProfileAppUid3);
        registerDefaultNetworkCallbackAsUid(appCb4, testWorkProfileAppUid4);
        registerDefaultNetworkCallbackAsUid(appCb5, testWorkProfileAppUid5);

        // Connect both a regular cell agent and an enterprise network first.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        final TestNetworkAgentWrapper workAgent1 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_1);
        final TestNetworkAgentWrapper workAgent2 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_2);
        final TestNetworkAgentWrapper workAgent3 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_3);
        final TestNetworkAgentWrapper workAgent4 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_4);
        final TestNetworkAgentWrapper workAgent5 = makeEnterpriseNetworkAgent(NET_ENTERPRISE_ID_5);

        final NetworkCallback keepupCb1 = requestForEnterpriseId(NET_ENTERPRISE_ID_1);
        final NetworkCallback keepupCb2 = requestForEnterpriseId(NET_ENTERPRISE_ID_2);
        final NetworkCallback keepupCb3 = requestForEnterpriseId(NET_ENTERPRISE_ID_3);
        final NetworkCallback keepupCb4 = requestForEnterpriseId(NET_ENTERPRISE_ID_4);
        final NetworkCallback keepupCb5 = requestForEnterpriseId(NET_ENTERPRISE_ID_5);

        workAgent1.connect(true);
        workAgent2.connect(true);
        workAgent3.connect(true);
        workAgent4.connect(true);
        workAgent5.connect(true);

        mSystemDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb1.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb2.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb3.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb4.expectAvailableThenValidatedCallbacks(mCellAgent);
        appCb5.expectAvailableThenValidatedCallbacks(mCellAgent);

        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent1.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent2.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent3.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent4.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent5.getNetwork().netId, INetd.PERMISSION_SYSTEM));

        final TestOnCompleteListener listener = new TestOnCompleteListener();

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder1 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder1.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder1.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        profileNetworkPreferenceBuilder1.setIncludedUids(new int[]{testWorkProfileAppUid1});

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder2 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder2.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder2.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_2);
        profileNetworkPreferenceBuilder2.setIncludedUids(new int[]{testWorkProfileAppUid2});

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder3 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder3.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder3.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_3);
        profileNetworkPreferenceBuilder3.setIncludedUids(new int[]{testWorkProfileAppUid3});

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder4 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder4.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
        profileNetworkPreferenceBuilder4.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_4);
        profileNetworkPreferenceBuilder4.setIncludedUids(new int[]{testWorkProfileAppUid4});

        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder5 =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder5.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder5.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_5);
        profileNetworkPreferenceBuilder5.setIncludedUids(new int[]{testWorkProfileAppUid5});

        mCm.setProfileNetworkPreferences(testHandle,
                List.of(profileNetworkPreferenceBuilder1.build(),
                        profileNetworkPreferenceBuilder2.build(),
                        profileNetworkPreferenceBuilder3.build(),
                        profileNetworkPreferenceBuilder4.build(),
                        profileNetworkPreferenceBuilder5.build()),
                r -> r.run(), listener);

        listener.expectOnComplete();

        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent1.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder1.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent2.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder2.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent3.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder3.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent4.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder4.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent5.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder5.build()),
                PREFERENCE_ORDER_PROFILE));

        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        appCb1.expectAvailableCallbacksValidated(workAgent1);
        appCb2.expectAvailableCallbacksValidated(workAgent2);
        appCb3.expectAvailableCallbacksValidated(workAgent3);
        appCb4.expectAvailableCallbacksValidated(workAgent4);
        appCb5.expectAvailableCallbacksValidated(workAgent5);

        workAgent1.disconnect();
        workAgent2.disconnect();
        workAgent3.disconnect();
        workAgent4.disconnect();
        workAgent5.disconnect();

        appCb1.expect(LOST, workAgent1);
        appCb2.expect(LOST, workAgent2);
        appCb3.expect(LOST, workAgent3);
        appCb4.expect(LOST, workAgent4);
        appCb5.expect(LOST, workAgent5);

        appCb1.expectAvailableCallbacksValidated(mCellAgent);
        appCb2.assertNoCallback();
        appCb3.expectAvailableCallbacksValidated(mCellAgent);
        appCb4.assertNoCallback();
        appCb5.expectAvailableCallbacksValidated(mCellAgent);

        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder1.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd, never()).networkAddUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder2.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder3.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd, never()).networkAddUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder4.build()),
                PREFERENCE_ORDER_PROFILE));
        verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                uidRangeFor(testHandle, profileNetworkPreferenceBuilder5.build()),
                PREFERENCE_ORDER_PROFILE));

        mSystemDefaultNetworkCallback.assertNoCallback();
        mDefaultNetworkCallback.assertNoCallback();

        // Set the preferences for testHandle to default.
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_DEFAULT);

        mCm.setProfileNetworkPreferences(testHandle,
                List.of(profileNetworkPreferenceBuilder.build()),
                r -> r.run(), listener);
        listener.expectOnComplete();
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback, appCb1, appCb3,
                appCb5);
        appCb2.expectAvailableCallbacksValidated(mCellAgent);
        appCb4.expectAvailableCallbacksValidated(mCellAgent);
        mCellAgent.disconnect();

        mCm.unregisterNetworkCallback(keepupCb1);
        mCm.unregisterNetworkCallback(keepupCb2);
        mCm.unregisterNetworkCallback(keepupCb3);
        mCm.unregisterNetworkCallback(keepupCb4);
        mCm.unregisterNetworkCallback(keepupCb5);

        mCm.unregisterNetworkCallback(appCb1);
        mCm.unregisterNetworkCallback(appCb2);
        mCm.unregisterNetworkCallback(appCb3);
        mCm.unregisterNetworkCallback(appCb4);
        mCm.unregisterNetworkCallback(appCb5);
        // Other callbacks will be unregistered by tearDown()
    }

    /**
     * Test that, in a given networking context, calling setPreferenceForUser to set per-profile
     * defaults on then off works as expected.
     */
    @Test
    public void testSetPreferenceForUserOnOff() throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        final UserHandle testHandle = setupEnterpriseNetwork();

        // Connect both a regular cell agent and an enterprise network first.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        final TestNetworkAgentWrapper workAgent = makeEnterpriseNetworkAgent();
        workAgent.connect(true);

        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));
        inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent.getNetwork().netId, uidRangeFor(testHandle), PREFERENCE_ORDER_PROFILE));

        registerDefaultNetworkCallbacks();

        mSystemDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(workAgent);

        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_DEFAULT,
                r -> r.run(), listener);
        listener.expectOnComplete();

        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback);
        inOrder.verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                workAgent.getNetwork().netId, uidRangeFor(testHandle), PREFERENCE_ORDER_PROFILE));

        workAgent.disconnect();
        mCellAgent.disconnect();

        // Callbacks will be unregistered by tearDown()
    }

    /**
     * Test per-profile default networks for two different profiles concurrently.
     */
    @Test
    public void testSetPreferenceForTwoProfiles() throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        final UserHandle testHandle2 = setupEnterpriseNetwork();
        final UserHandle testHandle4 = UserHandle.of(TEST_WORK_PROFILE_USER_ID + 2);
        mServiceContext.setWorkProfile(testHandle4, true);
        registerDefaultNetworkCallbacks();

        final TestNetworkCallback app4Cb = new TestNetworkCallback();
        final int testWorkProfileAppUid4 =
                UserHandle.getUid(testHandle4.getIdentifier(), TEST_APP_ID);
        registerDefaultNetworkCallbackAsUid(app4Cb, testWorkProfileAppUid4);

        // Connect both a regular cell agent and an enterprise network first.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        final TestNetworkAgentWrapper workAgent = makeEnterpriseNetworkAgent();
        workAgent.connect(true);

        mSystemDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mProfileDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        app4Cb.expectAvailableThenValidatedCallbacks(mCellAgent);
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent.getNetwork().netId, INetd.PERMISSION_SYSTEM));

        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreference(testHandle2, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent.getNetwork().netId, uidRangeFor(testHandle2), PREFERENCE_ORDER_PROFILE));

        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(workAgent);
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback,
                app4Cb);

        mCm.setProfileNetworkPreference(testHandle4, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                workAgent.getNetwork().netId, uidRangeFor(testHandle4), PREFERENCE_ORDER_PROFILE));

        app4Cb.expectAvailableCallbacksValidated(workAgent);
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback,
                mProfileDefaultNetworkCallback);

        mCm.setProfileNetworkPreference(testHandle2, PROFILE_NETWORK_PREFERENCE_DEFAULT,
                r -> r.run(), listener);
        listener.expectOnComplete();
        inOrder.verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                workAgent.getNetwork().netId, uidRangeFor(testHandle2), PREFERENCE_ORDER_PROFILE));

        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertNoCallbacks(mSystemDefaultNetworkCallback, mDefaultNetworkCallback,
                app4Cb);

        workAgent.disconnect();
        mCellAgent.disconnect();

        mCm.unregisterNetworkCallback(app4Cb);
        // Other callbacks will be unregistered by tearDown()
    }

    @Test
    public void testProfilePreferenceRemovedUponUserRemoved() throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        final UserHandle testHandle = setupEnterpriseNetwork();

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        inOrder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                mCellAgent.getNetwork().netId, INetd.PERMISSION_NONE));
        inOrder.verify(mMockNetd).networkAddUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId, uidRangeFor(testHandle),
                PREFERENCE_ORDER_PROFILE));

        final Intent removedIntent = new Intent(ACTION_USER_REMOVED);
        removedIntent.putExtra(Intent.EXTRA_USER, testHandle);
        processBroadcast(removedIntent);

        inOrder.verify(mMockNetd).networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId, uidRangeFor(testHandle),
                PREFERENCE_ORDER_PROFILE));
    }

    @Test
    public void testProfileNetworkPreferenceBlocking_addUser() throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        doReturn(asList(PRIMARY_USER_HANDLE)).when(mUserManager).getUserHandles(anyBoolean());

        // Only one network
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        // Verify uid ranges 0~99999 are allowed
        final ArraySet<UidRange> allowedRanges = new ArraySet<>();
        allowedRanges.add(PRIMARY_UIDRANGE);
        final NativeUidRangeConfig config1User = new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                toUidRangeStableParcels(allowedRanges),
                0 /* subPriority */);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(new NativeUidRangeConfig[]{config1User});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        doReturn(asList(PRIMARY_USER_HANDLE, SECONDARY_USER_HANDLE))
                .when(mUserManager).getUserHandles(anyBoolean());
        final Intent addedIntent = new Intent(ACTION_USER_ADDED);
        addedIntent.putExtra(Intent.EXTRA_USER, UserHandle.of(SECONDARY_USER));
        processBroadcast(addedIntent);

        // Make sure the allow list has been updated.
        allowedRanges.add(UidRange.createForUser(SECONDARY_USER_HANDLE));
        final NativeUidRangeConfig config2Users = new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                toUidRangeStableParcels(allowedRanges),
                0 /* subPriority */);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(new NativeUidRangeConfig[]{config2Users});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }
    }

    @Test
    public void testProfileNetworkPreferenceBlocking_changePreference() throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        final UserHandle testHandle = setupEnterpriseNetwork();
        doReturn(asList(PRIMARY_USER_HANDLE, testHandle))
                .when(mUserManager).getUserHandles(anyBoolean());

        // Start with 1 default network and 1 enterprise network, both networks should
        // not be restricted since the blocking preference is not set yet.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        // Verify uid ranges 0~99999, 200000~299999 are all allowed for cellular.
        final UidRange profileUidRange =
                UidRange.createForUser(UserHandle.of(TEST_WORK_PROFILE_USER_ID));
        ArraySet<UidRange> allowedAllUidRanges = new ArraySet<>();
        allowedAllUidRanges.add(PRIMARY_UIDRANGE);
        allowedAllUidRanges.add(profileUidRange);
        final UidRangeParcel[] allowAllUidRangesParcel = toUidRangeStableParcels(
                allowedAllUidRanges);
        final NativeUidRangeConfig cellAllAllowedConfig = new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                allowAllUidRangesParcel,
                0 /* subPriority */);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(
                    new NativeUidRangeConfig[]{cellAllAllowedConfig});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Verify the same uid ranges are also applied for enterprise network.
        final TestNetworkAgentWrapper enterpriseAgent = makeEnterpriseNetworkAgent(
                NET_ENTERPRISE_ID_1);
        enterpriseAgent.connect(true);
        final NativeUidRangeConfig enterpriseAllAllowedConfig = new NativeUidRangeConfig(
                enterpriseAgent.getNetwork().netId,
                allowAllUidRangesParcel,
                0 /* subPriority */);
        // Network agents are stored in an ArraySet which does not guarantee the order and
        // making the order of the list undeterministic. Thus, verify this in order insensitive way.
        final ArgumentCaptor<NativeUidRangeConfig[]> configsCaptor = ArgumentCaptor.forClass(
                NativeUidRangeConfig[].class);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(configsCaptor.capture());
            assertContainsAll(List.of(configsCaptor.getValue()),
                    List.of(cellAllAllowedConfig, enterpriseAllAllowedConfig));
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Setup profile preference which only applies to test app uid on the managed profile.
        ProfileNetworkPreference.Builder prefBuilder = new ProfileNetworkPreference.Builder();
        prefBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING)
                .setIncludedUids(new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID)})
                .setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreferences(testHandle,
                List.of(prefBuilder.build()),
                r -> r.run(), listener);
        listener.expectOnComplete();

        // Verify Netd is called for the preferences changed.
        // Cell: 0~99999, 200000~TEST_APP_UID-1, TEST_APP_UID+1~299999
        // Enterprise: 0~99999, 200000~299999
        final ArraySet<UidRange> excludeAppRanges = new ArraySet<>();
        excludeAppRanges.add(PRIMARY_UIDRANGE);
        excludeAppRanges.addAll(UidRangeUtils.removeRangeSetFromUidRange(
                profileUidRange,
                new ArraySet(new UidRange[]{
                        (new UidRange(TEST_WORK_PROFILE_APP_UID, TEST_WORK_PROFILE_APP_UID))})
        ));
        final UidRangeParcel[] excludeAppRangesParcel = toUidRangeStableParcels(excludeAppRanges);
        final NativeUidRangeConfig cellExcludeAppConfig = new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                excludeAppRangesParcel,
                0 /* subPriority */);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(configsCaptor.capture());
            assertContainsAll(List.of(configsCaptor.getValue()),
                    List.of(cellExcludeAppConfig, enterpriseAllAllowedConfig));
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Verify unset by giving all allowed set for all users when the preference got removed.
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(configsCaptor.capture());
            assertContainsAll(List.of(configsCaptor.getValue()),
                    List.of(cellAllAllowedConfig, enterpriseAllAllowedConfig));
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Verify issuing with cellular set only when a network with enterprise capability
        // disconnects.
        enterpriseAgent.disconnect();
        waitForIdle();
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(
                    new NativeUidRangeConfig[]{cellAllAllowedConfig});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }
    }

    @Test
    public void testProfileNetworkPreferenceBlocking_networkChanges() throws Exception {
        final InOrder inOrder = inOrder(mMockNetd);
        final UserHandle testHandle = setupEnterpriseNetwork();
        doReturn(asList(PRIMARY_USER_HANDLE, testHandle))
                .when(mUserManager).getUserHandles(anyBoolean());

        // Setup profile preference which only applies to test app uid on the managed profile.
        ProfileNetworkPreference.Builder prefBuilder = new ProfileNetworkPreference.Builder();
        prefBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING)
                .setIncludedUids(new int[]{testHandle.getUid(TEST_WORK_PROFILE_APP_UID)})
                .setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreferences(testHandle,
                List.of(prefBuilder.build()),
                r -> r.run(), listener);
        listener.expectOnComplete();
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(new NativeUidRangeConfig[]{});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Start with 1 default network, which should be restricted since the blocking
        // preference is already set.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);

        // Verify cellular network applies to the allow list.
        // Cell: 0~99999, 200000~TEST_APP_UID-1, TEST_APP_UID+1~299999
        // Enterprise: 0~99999, 200000~299999
        final ArraySet<UidRange> excludeAppRanges = new ArraySet<>();
        final UidRange profileUidRange =
                UidRange.createForUser(UserHandle.of(TEST_WORK_PROFILE_USER_ID));
        excludeAppRanges.add(PRIMARY_UIDRANGE);
        excludeAppRanges.addAll(UidRangeUtils.removeRangeSetFromUidRange(
                profileUidRange,
                new ArraySet(new UidRange[]{
                        (new UidRange(TEST_WORK_PROFILE_APP_UID, TEST_WORK_PROFILE_APP_UID))})
        ));
        final UidRangeParcel[] excludeAppRangesParcel = toUidRangeStableParcels(excludeAppRanges);
        final NativeUidRangeConfig cellExcludeAppConfig = new NativeUidRangeConfig(
                mCellAgent.getNetwork().netId,
                excludeAppRangesParcel,
                0 /* subPriority */);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(
                    new NativeUidRangeConfig[]{cellExcludeAppConfig});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Verify enterprise network is not blocked for test app.
        final TestNetworkAgentWrapper enterpriseAgent = makeEnterpriseNetworkAgent(
                NET_ENTERPRISE_ID_1);
        enterpriseAgent.connect(true);
        ArraySet<UidRange> allowedAllUidRanges = new ArraySet<>();
        allowedAllUidRanges.add(PRIMARY_UIDRANGE);
        allowedAllUidRanges.add(profileUidRange);
        final UidRangeParcel[] allowAllUidRangesParcel = toUidRangeStableParcels(
                allowedAllUidRanges);
        final NativeUidRangeConfig enterpriseAllAllowedConfig = new NativeUidRangeConfig(
                enterpriseAgent.getNetwork().netId,
                allowAllUidRangesParcel,
                0 /* subPriority */);
        // Network agents are stored in an ArraySet which does not guarantee the order and
        // making the order of the list undeterministic. Thus, verify this in order insensitive way.
        final ArgumentCaptor<NativeUidRangeConfig[]> configsCaptor = ArgumentCaptor.forClass(
                NativeUidRangeConfig[].class);
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(configsCaptor.capture());
            assertContainsAll(List.of(configsCaptor.getValue()),
                    List.of(enterpriseAllAllowedConfig, cellExcludeAppConfig));
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        // Verify issuing with cellular set only when enterprise network disconnects.
        enterpriseAgent.disconnect();
        waitForIdle();
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(
                    new NativeUidRangeConfig[]{cellExcludeAppConfig});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }

        mCellAgent.disconnect();
        waitForIdle();
        if (mDeps.isAtLeastU()) {
            inOrder.verify(mMockNetd).setNetworkAllowlist(new NativeUidRangeConfig[]{});
        } else {
            inOrder.verify(mMockNetd, never()).setNetworkAllowlist(any());
        }
    }

    /**
     * Make sure wrong preferences for per-profile default networking are rejected.
     */
    @Test
    public void testProfileNetworkPrefWrongPreference() throws Exception {
        final UserHandle testHandle = UserHandle.of(TEST_WORK_PROFILE_USER_ID);
        mServiceContext.setWorkProfile(testHandle, true);
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(
                PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING + 1);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        assertThrows("Should not be able to set an illegal preference",
                IllegalArgumentException.class,
                () -> mCm.setProfileNetworkPreferences(testHandle,
                        List.of(profileNetworkPreferenceBuilder.build()),
                        null, null));
    }

    /**
     * Make sure requests for per-profile default networking for a non-work profile are
     * rejected
     */
    @Test
    public void testProfileNetworkPrefWrongProfile() throws Exception {
        final UserHandle testHandle = UserHandle.of(TEST_WORK_PROFILE_USER_ID);
        mServiceContext.setWorkProfile(testHandle, false);
        mServiceContext.setDeviceOwner(testHandle, null);
        assertThrows("Should not be able to set a user pref for a non-work profile "
                + "and non device owner",
                IllegalArgumentException.class , () ->
                        mCm.setProfileNetworkPreference(testHandle,
                                PROFILE_NETWORK_PREFERENCE_ENTERPRISE, null, null));
    }

    /**
     * Make sure requests for per-profile default networking for a device owner is
     * accepted on T and not accepted on S
     */
    @Test
    public void testProfileNetworkDeviceOwner() throws Exception {
        final UserHandle testHandle = UserHandle.of(TEST_WORK_PROFILE_USER_ID);
        mServiceContext.setWorkProfile(testHandle, false);
        mServiceContext.setDeviceOwner(testHandle, "deviceOwnerPackage");
        ProfileNetworkPreference.Builder profileNetworkPreferenceBuilder =
                new ProfileNetworkPreference.Builder();
        profileNetworkPreferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
        profileNetworkPreferenceBuilder.setPreferenceEnterpriseId(NET_ENTERPRISE_ID_1);
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        if (mDeps.isAtLeastT()) {
            mCm.setProfileNetworkPreferences(testHandle,
                    List.of(profileNetworkPreferenceBuilder.build()),
                    r -> r.run(), listener);
        } else {
            // S should not allow setting preference on device owner
            assertThrows("Should not be able to set a user pref for a non-work profile on S",
                    IllegalArgumentException.class , () ->
                            mCm.setProfileNetworkPreferences(testHandle,
                                    List.of(profileNetworkPreferenceBuilder.build()),
                                    r -> r.run(), listener));
        }
    }

    @Test
    public void testSubIdsClearedWithoutNetworkFactoryPermission() throws Exception {
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_DENIED);
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setSubscriptionIds(Collections.singleton(Process.myUid()));

        final NetworkCapabilities result =
                mService.networkCapabilitiesRestrictedForCallerPermissions(
                        nc, Process.myPid(), Process.myUid());
        assertTrue(result.getSubscriptionIds().isEmpty());
    }

    @Test
    public void testSubIdsExistWithNetworkFactoryPermission() throws Exception {
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_GRANTED);

        final Set<Integer> subIds = Collections.singleton(Process.myUid());
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setSubscriptionIds(subIds);

        final NetworkCapabilities result =
                mService.networkCapabilitiesRestrictedForCallerPermissions(
                        nc, Process.myPid(), Process.myUid());
        assertEquals(subIds, result.getSubscriptionIds());
    }

    private NetworkRequest getRequestWithSubIds() {
        return new NetworkRequest.Builder()
                .setSubscriptionIds(Collections.singleton(Process.myUid()))
                .build();
    }

    private NetworkRequest getRestrictedRequestForWifiWithSubIds() {
        return new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .setSubscriptionIds(Collections.singleton(TEST_SUBSCRIPTION_ID))
            .build();
    }

    @Test
    public void testNetworkRequestWithSubIdsWithNetworkFactoryPermission() throws Exception {
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_GRANTED);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0 /* requestCode */, new Intent("a"), FLAG_IMMUTABLE);
        final NetworkCallback networkCallback1 = new NetworkCallback();
        final NetworkCallback networkCallback2 = new NetworkCallback();

        mCm.requestNetwork(getRequestWithSubIds(), networkCallback1);
        mCm.requestNetwork(getRequestWithSubIds(), pendingIntent);
        mCm.registerNetworkCallback(getRequestWithSubIds(), networkCallback2);

        mCm.unregisterNetworkCallback(networkCallback1);
        mCm.releaseNetworkRequest(pendingIntent);
        mCm.unregisterNetworkCallback(networkCallback2);
    }

    @Test
    public void testNetworkRequestWithSubIdsWithoutNetworkFactoryPermission() throws Exception {
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_DENIED);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0 /* requestCode */, new Intent("a"), FLAG_IMMUTABLE);

        final Class<SecurityException> expected = SecurityException.class;
        assertThrows(
                expected, () -> mCm.requestNetwork(getRequestWithSubIds(), new NetworkCallback()));
        assertThrows(expected, () -> mCm.requestNetwork(getRequestWithSubIds(), pendingIntent));
        assertThrows(
                expected,
                () -> mCm.registerNetworkCallback(getRequestWithSubIds(), new NetworkCallback()));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testCarrierConfigAppSendNetworkRequestForRestrictedWifi() throws Exception {
        mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_DENIED);
        doReturn(true).when(mCarrierPrivilegeAuthenticator)
                .isCarrierServiceUidForNetworkCapabilities(anyInt(), any());
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0 /* requestCode */, new Intent("a"), FLAG_IMMUTABLE);
        final NetworkCallback networkCallback1 = new NetworkCallback();
        final NetworkCallback networkCallback2 = new NetworkCallback();

        mCm.requestNetwork(
                getRestrictedRequestForWifiWithSubIds(), networkCallback1);
        mCm.requestNetwork(
                getRestrictedRequestForWifiWithSubIds(), pendingIntent);
        mCm.registerNetworkCallback(
                getRestrictedRequestForWifiWithSubIds(), networkCallback2);

        mCm.unregisterNetworkCallback(networkCallback1);
        mCm.releaseNetworkRequest(pendingIntent);
        mCm.unregisterNetworkCallback(networkCallback2);
    }

    private void doTestNetworkRequestWithCarrierPrivilegesLost(
            boolean shouldGrantRestrictedNetworkPermission,
            int lostPrivilegeUid,
            int lostPrivilegeSubId,
            boolean expectUnavailable,
            boolean expectCapChanged) throws Exception {
        if (shouldGrantRestrictedNetworkPermission) {
            mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_GRANTED);
        } else {
            mServiceContext.setPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERMISSION_DENIED);
        }

        NetworkCapabilities filter =
                getRestrictedRequestForWifiWithSubIds().networkCapabilities;
        final HandlerThread handlerThread = new HandlerThread("testRestrictedFactoryRequests");
        handlerThread.start();

        final MockNetworkFactory testFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "testFactory", filter, mCsHandlerThread);
        testFactory.register();
        testFactory.assertRequestCountEquals(0);

        doReturn(true).when(mCarrierPrivilegeAuthenticator)
                .isCarrierServiceUidForNetworkCapabilities(eq(Process.myUid()), any());
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        final NetworkRequest networkrequest =
                getRestrictedRequestForWifiWithSubIds();
        mCm.requestNetwork(networkrequest, networkCallback);
        testFactory.expectRequestAdd();
        testFactory.assertRequestCountEquals(1);

        NetworkCapabilities nc = new NetworkCapabilities.Builder(filter)
                .setAllowedUids(Set.of(Process.myUid()))
                .build();
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, new LinkProperties(), nc);
        mWiFiAgent.connect(false);
        networkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);
        final NetworkAgentInfo nai = mService.getNetworkAgentInfoForNetwork(
                mWiFiAgent.getNetwork());

        doReturn(false).when(mCarrierPrivilegeAuthenticator)
                .isCarrierServiceUidForNetworkCapabilities(eq(Process.myUid()), any());
        doReturn(TEST_SUBSCRIPTION_ID).when(mCarrierPrivilegeAuthenticator)
                .getSubIdFromNetworkCapabilities(any());
        mService.onCarrierPrivilegesLost(lostPrivilegeUid, lostPrivilegeSubId);
        waitForIdle();

        if (expectCapChanged) {
            networkCallback.expect(NETWORK_CAPS_UPDATED);
        }
        if (expectUnavailable) {
            networkCallback.expect(UNAVAILABLE);
        }
        if (!expectCapChanged && !expectUnavailable) {
            networkCallback.assertNoCallback();
        }

        mWiFiAgent.disconnect();
        waitForIdle();

        if (expectUnavailable) {
            testFactory.assertRequestCountEquals(0);
        } else {
            testFactory.assertRequestCountEquals(1);
        }

        handlerThread.quitSafely();
        handlerThread.join();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testRestrictedRequestRemovedDueToCarrierPrivilegesLost() throws Exception {
        doTestNetworkRequestWithCarrierPrivilegesLost(
                false /* shouldGrantRestrictedNetworkPermission */,
                Process.myUid(),
                TEST_SUBSCRIPTION_ID,
                true /* expectUnavailable */,
                true /* expectCapChanged */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testRequestNotRemoved_MismatchSubId() throws Exception {
        doTestNetworkRequestWithCarrierPrivilegesLost(
                false /* shouldGrantRestrictedNetworkPermission */,
                Process.myUid(),
                TEST_SUBSCRIPTION_ID + 1,
                false /* expectUnavailable */,
                false /* expectCapChanged */);
    }
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testRequestNotRemoved_MismatchUid() throws Exception {
        doTestNetworkRequestWithCarrierPrivilegesLost(
                false /* shouldGrantRestrictedNetworkPermission */,
                Process.myUid() + 1,
                TEST_SUBSCRIPTION_ID,
                false /* expectUnavailable */,
                false /* expectCapChanged */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testRequestNotRemoved_HasRestrictedNetworkPermission() throws Exception {
        doTestNetworkRequestWithCarrierPrivilegesLost(
                true /* shouldGrantRestrictedNetworkPermission */,
                Process.myUid(),
                TEST_SUBSCRIPTION_ID,
                false /* expectUnavailable */,
                true /* expectCapChanged */);
    }
    @Test
    public void testAllowedUids() throws Exception {
        final int preferenceOrder =
                ConnectivityService.PREFERENCE_ORDER_IRRELEVANT_BECAUSE_NOT_DEFAULT;
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_GRANTED);
        mServiceContext.setPermission(MANAGE_TEST_NETWORKS, PERMISSION_GRANTED);
        final TestNetworkCallback cb = new TestNetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder()
                        .clearCapabilities()
                        .addTransportType(TRANSPORT_TEST)
                        .addTransportType(TRANSPORT_CELLULAR)
                        .build(),
                cb);

        final ArraySet<Integer> uids = new ArraySet<>();
        uids.add(200);
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_TEST)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .setAllowedUids(uids)
                .build();
        final TestNetworkAgentWrapper agent = new TestNetworkAgentWrapper(TRANSPORT_TEST,
                new LinkProperties(), nc);
        agent.connect(true);
        cb.expectAvailableThenValidatedCallbacks(agent);

        final InOrder inOrder = inOrder(mMockNetd);
        final NativeUidRangeConfig uids200Parcel = new NativeUidRangeConfig(
                agent.getNetwork().getNetId(),
                intToUidRangeStableParcels(uids),
                preferenceOrder);
        if (mDeps.isAtLeastT()) {
            inOrder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(uids200Parcel);
        }

        uids.add(300);
        uids.add(400);
        nc.setAllowedUids(uids);
        agent.setNetworkCapabilities(nc, true /* sendToConnectivityService */);
        if (mDeps.isAtLeastT()) {
            cb.expectCaps(agent, c -> c.getAllowedUids().equals(uids));
        } else {
            cb.assertNoCallback();
        }

        uids.remove(200);
        final NativeUidRangeConfig uids300400Parcel = new NativeUidRangeConfig(
                agent.getNetwork().getNetId(),
                intToUidRangeStableParcels(uids),
                preferenceOrder);
        if (mDeps.isAtLeastT()) {
            inOrder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(uids300400Parcel);
        }

        nc.setAllowedUids(uids);
        agent.setNetworkCapabilities(nc, true /* sendToConnectivityService */);
        if (mDeps.isAtLeastT()) {
            cb.expectCaps(agent, c -> c.getAllowedUids().equals(uids));
            inOrder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(uids200Parcel);
        } else {
            cb.assertNoCallback();
        }

        uids.clear();
        uids.add(600);
        nc.setAllowedUids(uids);
        agent.setNetworkCapabilities(nc, true /* sendToConnectivityService */);
        if (mDeps.isAtLeastT()) {
            cb.expectCaps(agent, c -> c.getAllowedUids().equals(uids));
        } else {
            cb.assertNoCallback();
        }
        final NativeUidRangeConfig uids600Parcel = new NativeUidRangeConfig(
                agent.getNetwork().getNetId(),
                intToUidRangeStableParcels(uids),
                preferenceOrder);
        if (mDeps.isAtLeastT()) {
            inOrder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(uids600Parcel);
            inOrder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(uids300400Parcel);
        }

        uids.clear();
        nc.setAllowedUids(uids);
        agent.setNetworkCapabilities(nc, true /* sendToConnectivityService */);
        if (mDeps.isAtLeastT()) {
            cb.expectCaps(agent, c -> c.getAllowedUids().isEmpty());
            inOrder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(uids600Parcel);
        } else {
            cb.assertNoCallback();
            verify(mMockNetd, never()).networkAddUidRangesParcel(any());
            verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());
        }

    }

    @Test
    public void testAutomotiveEthernetAllowedUids() throws Exception {
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_GRANTED);
        mServiceContext.setPermission(MANAGE_TEST_NETWORKS, PERMISSION_GRANTED);

        // Has automotive feature.
        validateAutomotiveEthernetAllowedUids(true);

        // No automotive feature.
        validateAutomotiveEthernetAllowedUids(false);
    }

    private void validateAutomotiveEthernetAllowedUids(final boolean hasAutomotiveFeature)
            throws Exception {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, hasAutomotiveFeature);

        // Simulate a restricted ethernet network.
        final NetworkCapabilities.Builder ncb = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_ETHERNET)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);

        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET,
                new LinkProperties(), ncb.build());

        final ArraySet<Integer> serviceUidSet = new ArraySet<>();
        serviceUidSet.add(TEST_PACKAGE_UID);

        final TestNetworkCallback cb = new TestNetworkCallback();

        mCm.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_ETHERNET)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .build(), cb);
        mEthernetAgent.connect(true);
        cb.expectAvailableThenValidatedCallbacks(mEthernetAgent);

        // Cell gets to set the service UID as access UID
        ncb.setAllowedUids(serviceUidSet);
        mEthernetAgent.setNetworkCapabilities(ncb.build(), true /* sendToCS */);
        if (mDeps.isAtLeastT() && hasAutomotiveFeature) {
            cb.expectCaps(mEthernetAgent, c -> c.getAllowedUids().equals(serviceUidSet));
        } else {
            // S and no automotive feature must ignore access UIDs.
            cb.assertNoCallback(TEST_CALLBACK_TIMEOUT_MS);
        }

        mEthernetAgent.disconnect();
        cb.expect(LOST, mEthernetAgent);
        mCm.unregisterNetworkCallback(cb);
    }

    @Test
    public void testCbsAllowedUids() throws Exception {
        mServiceContext.setPermission(NETWORK_FACTORY, PERMISSION_GRANTED);
        mServiceContext.setPermission(MANAGE_TEST_NETWORKS, PERMISSION_GRANTED);

        // In this test TEST_PACKAGE_UID will be the UID of the carrier service UID.
        doReturn(true).when(mCarrierPrivilegeAuthenticator)
                .isCarrierServiceUidForNetworkCapabilities(eq(TEST_PACKAGE_UID), any());

        // Simulate a restricted telephony network. The telephony factory is entitled to set
        // the access UID to the service package on any of its restricted networks.
        final NetworkCapabilities.Builder ncb = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier(1 /* subid */));

        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR,
                new LinkProperties(), ncb.build());

        final ArraySet<Integer> serviceUidSet = new ArraySet<>();
        serviceUidSet.add(TEST_PACKAGE_UID);
        final ArraySet<Integer> nonServiceUidSet = new ArraySet<>();
        nonServiceUidSet.add(TEST_PACKAGE_UID2);
        final ArraySet<Integer> serviceUidSetPlus = new ArraySet<>();
        serviceUidSetPlus.add(TEST_PACKAGE_UID);
        serviceUidSetPlus.add(TEST_PACKAGE_UID2);

        final TestNetworkCallback cb = new TestNetworkCallback();

        // Cell gets to set the service UID as access UID
        mCm.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .build(), cb);
        mCellAgent.connect(true);
        cb.expectAvailableThenValidatedCallbacks(mCellAgent);
        ncb.setAllowedUids(serviceUidSet);
        mCellAgent.setNetworkCapabilities(ncb.build(), true /* sendToCS */);
        if (mDeps.isAtLeastT()) {
            cb.expectCaps(mCellAgent, c -> c.getAllowedUids().equals(serviceUidSet));
        } else {
            // S must ignore access UIDs.
            cb.assertNoCallback(TEST_CALLBACK_TIMEOUT_MS);
        }

        // ...but not to some other UID. Rejection sets UIDs to the empty set
        ncb.setAllowedUids(nonServiceUidSet);
        mCellAgent.setNetworkCapabilities(ncb.build(), true /* sendToCS */);
        if (mDeps.isAtLeastT()) {
            cb.expectCaps(mCellAgent, c -> c.getAllowedUids().isEmpty());
        } else {
            // S must ignore access UIDs.
            cb.assertNoCallback(TEST_CALLBACK_TIMEOUT_MS);
        }

        // ...and also not to multiple UIDs even including the service UID
        ncb.setAllowedUids(serviceUidSetPlus);
        mCellAgent.setNetworkCapabilities(ncb.build(), true /* sendToCS */);
        cb.assertNoCallback(TEST_CALLBACK_TIMEOUT_MS);

        mCellAgent.disconnect();
        cb.expect(LOST, mCellAgent);
        mCm.unregisterNetworkCallback(cb);

        // Must be unset before touching the transports, because remove and add transport types
        // check the specifier on the builder immediately, contradicting normal builder semantics
        // TODO : fix the builder
        ncb.setNetworkSpecifier(null);
        ncb.removeTransportType(TRANSPORT_CELLULAR);
        ncb.addTransportType(TRANSPORT_BLUETOOTH);
        // Wifi does not get to set access UID, even to the correct UID
        mCm.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_BLUETOOTH)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .build(), cb);
        final TestNetworkAgentWrapper bluetoothAgent = new TestNetworkAgentWrapper(
                TRANSPORT_BLUETOOTH, new LinkProperties(), ncb.build());
        bluetoothAgent.connect(true);
        cb.expectAvailableThenValidatedCallbacks(bluetoothAgent);
        ncb.setAllowedUids(serviceUidSet);
        bluetoothAgent.setNetworkCapabilities(ncb.build(), true /* sendToCS */);
        cb.assertNoCallback(TEST_CALLBACK_TIMEOUT_MS);
        mCm.unregisterNetworkCallback(cb);
    }

    @Test
    public void testSanitizedCapabilitiesFromAgentDoesNotMutateArgument()
            throws Exception {
        // This NetworkCapabilities builds an usual object to maximize the chance that this requires
        // sanitization, so we have a high chance to detect any changes to the original.
        final NetworkCapabilities unsanitized = new NetworkCapabilities.Builder()
                .withoutDefaultCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .setOwnerUid(12345)
                .setAdministratorUids(new int[] {12345, 23456, 34567})
                .setLinkUpstreamBandwidthKbps(20)
                .setLinkDownstreamBandwidthKbps(10)
                .setNetworkSpecifier(new EthernetNetworkSpecifier("foobar"))
                .setTransportInfo(new WifiInfo.Builder().setBssid("AA:AA:AA:AA:AA:AA").build())
                .setSignalStrength(-75)
                .setSsid("SSID1")
                .setRequestorUid(98765)
                .setRequestorPackageName("TestPackage")
                .setSubscriptionIds(Collections.singleton(Process.myUid()))
                .setUids(UidRange.toIntRanges(uidRangesForUids(
                        UserHandle.getUid(PRIMARY_USER, 10100),
                        UserHandle.getUid(SECONDARY_USER, 10101),
                        UserHandle.getUid(TERTIARY_USER, 10043))))
                .setAllowedUids(Set.of(45678, 56789, 65432))
                .setUnderlyingNetworks(List.of(new Network(99999)))
                .build();
        final NetworkCapabilities copyOfUnsanitized = new NetworkCapabilities(unsanitized);
        final NetworkInfo info = new NetworkInfo(TYPE_MOBILE, TelephonyManager.NETWORK_TYPE_LTE,
                ConnectivityManager.getNetworkTypeName(TYPE_MOBILE),
                TelephonyManager.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_LTE));
        final NetworkAgentInfo agent = fakeNai(unsanitized, info);
        agent.setDeclaredCapabilities(unsanitized);
        final NetworkCapabilities sanitized = agent.getDeclaredCapabilitiesSanitized(
                null /* carrierPrivilegeAuthenticator */);
        assertEquals(copyOfUnsanitized, unsanitized);
        assertNotEquals(sanitized, unsanitized);
    }

    /**
     * Validate request counts are counted accurately on setProfileNetworkPreference on set/replace.
     */
    @Test
    public void testProfileNetworkPrefCountsRequestsCorrectlyOnSet() throws Exception {
        final UserHandle testHandle = setupEnterpriseNetwork();
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        // Leave one request available so the profile preference can be set.
        withRequestCountersAcquired(1 /* countToLeaveAvailable */, () -> {
            withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                    Process.myPid(), Process.myUid(), () -> {
                        // Set initially to test the limit prior to having existing requests.
                        mCm.setProfileNetworkPreference(testHandle,
                                PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                                Runnable::run, listener);
                    });
            listener.expectOnComplete();

            // Simulate filing requests as some app on the work profile
            final int otherAppUid = UserHandle.getUid(TEST_WORK_PROFILE_USER_ID,
                    UserHandle.getAppId(Process.myUid() + 1));
            final int remainingCount = ConnectivityService.MAX_NETWORK_REQUESTS_PER_UID
                    - mService.mNetworkRequestCounter.get(otherAppUid)
                    - 1;
            final NetworkCallback[] callbacks = new NetworkCallback[remainingCount];
            doAsUid(otherAppUid, () -> {
                for (int i = 0; i < remainingCount; ++i) {
                    callbacks[i] = new TestNetworkCallback();
                    mCm.registerDefaultNetworkCallback(callbacks[i]);
                }
            });

            withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                    Process.myPid(), Process.myUid(), () -> {
                        // re-set so as to test the limit as part of replacing existing requests.
                        mCm.setProfileNetworkPreference(testHandle,
                                PROFILE_NETWORK_PREFERENCE_ENTERPRISE, Runnable::run, listener);
                    });
            listener.expectOnComplete();

            doAsUid(otherAppUid, () -> {
                for (final NetworkCallback callback : callbacks) {
                    mCm.unregisterNetworkCallback(callback);
                }
            });
        });
    }

    /**
     * Validate request counts are counted accurately on setOemNetworkPreference on set/replace.
     */
    @Test
    public void testSetOemNetworkPreferenceCountsRequestsCorrectlyOnSet() throws Exception {
        mockHasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;
        // Leave one request available so the OEM preference can be set.
        withRequestCountersAcquired(1 /* countToLeaveAvailable */, () ->
                withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, () -> {
                    // Set initially to test the limit prior to having existing requests.
                    final TestOemListenerCallback listener = new TestOemListenerCallback();
                    mService.setOemNetworkPreference(
                            createDefaultOemNetworkPreferences(networkPref), listener);
                    listener.expectOnComplete();

                    // re-set so as to test the limit as part of replacing existing requests.
                    mService.setOemNetworkPreference(
                            createDefaultOemNetworkPreferences(networkPref), listener);
                    listener.expectOnComplete();
                }));
    }

    private void withRequestCountersAcquired(final int countToLeaveAvailable,
            @NonNull final ThrowingRunnable r) throws Exception {
        final ArraySet<TestNetworkCallback> callbacks = new ArraySet<>();
        try {
            final int requestCount = mService.mSystemNetworkRequestCounter.get(Process.myUid());
            // The limit is hit when total requests = limit - 1, and exceeded with a crash when
            // total requests >= limit.
            final int countToFile =
                    MAX_NETWORK_REQUESTS_PER_SYSTEM_UID - requestCount - countToLeaveAvailable;
            // Need permission so registerDefaultNetworkCallback uses mSystemNetworkRequestCounter
            withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, () -> {
                for (int i = 1; i < countToFile; i++) {
                    final TestNetworkCallback cb = new TestNetworkCallback();
                    mCm.registerDefaultNetworkCallback(cb);
                    callbacks.add(cb);
                }
                assertEquals(MAX_NETWORK_REQUESTS_PER_SYSTEM_UID - 1 - countToLeaveAvailable,
                        mService.mSystemNetworkRequestCounter.get(Process.myUid()));
            });
            // Code to run to check if it triggers a max request count limit error.
            r.run();
        } finally {
            for (final TestNetworkCallback cb : callbacks) {
                mCm.unregisterNetworkCallback(cb);
            }
        }
    }

    private void assertCreateNrisFromMobileDataPreferredUids(Set<Integer> uids) {
        final Set<NetworkRequestInfo> nris =
                mService.createNrisFromMobileDataPreferredUids(uids);
        final NetworkRequestInfo nri = nris.iterator().next();
        // Verify that one NRI is created with multilayer requests. Because one NRI can contain
        // multiple uid ranges, so it only need create one NRI here.
        assertEquals(1, nris.size());
        assertTrue(nri.isMultilayerRequest());
        assertEquals(nri.getUids(), uidRangesForUids(uids));
        assertEquals(PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED, nri.mPreferenceOrder);
    }

    /**
     * Test createNrisFromMobileDataPreferredUids returns correct NetworkRequestInfo.
     */
    @Test
    public void testCreateNrisFromMobileDataPreferredUids() {
        // Verify that empty uid set should not create any NRI for it.
        final Set<NetworkRequestInfo> nrisNoUid =
                mService.createNrisFromMobileDataPreferredUids(new ArraySet<>());
        assertEquals(0, nrisNoUid.size());

        final int uid1 = PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID);
        final int uid2 = PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID2);
        final int uid3 = SECONDARY_USER_HANDLE.getUid(TEST_PACKAGE_UID);
        assertCreateNrisFromMobileDataPreferredUids(Set.of(uid1));
        assertCreateNrisFromMobileDataPreferredUids(Set.of(uid1, uid3));
        assertCreateNrisFromMobileDataPreferredUids(Set.of(uid1, uid2));
    }

    private void setAndUpdateMobileDataPreferredUids(Set<Integer> uids) {
        ConnectivitySettingsManager.setMobileDataPreferredUids(mServiceContext, uids);
        mService.updateMobileDataPreferredUids();
        waitForIdle();
    }

    /**
     * Test that MOBILE_DATA_PREFERRED_UIDS changes will send correct net id and uid ranges to netd.
     */
    @Test
    public void testMobileDataPreferredUidsChanged() throws Exception {
        final InOrder inorder = inOrder(mMockNetd);
        registerDefaultNetworkCallbacks();
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mTestPackageDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

        final int cellNetId = mCellAgent.getNetwork().netId;
        inorder.verify(mMockNetd, times(1)).networkCreate(nativeNetworkConfigPhysical(
                cellNetId, INetd.PERMISSION_NONE));

        // Initial mobile data preferred uids status.
        setAndUpdateMobileDataPreferredUids(Set.of());
        inorder.verify(mMockNetd, never()).networkAddUidRangesParcel(any());
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());

        // Set MOBILE_DATA_PREFERRED_UIDS setting and verify that net id and uid ranges send to netd
        final Set<Integer> uids1 = Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID));
        final UidRangeParcel[] uidRanges1 = toUidRangeStableParcels(uidRangesForUids(uids1));
        final NativeUidRangeConfig config1 = new NativeUidRangeConfig(cellNetId, uidRanges1,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        setAndUpdateMobileDataPreferredUids(uids1);
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(config1);
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());

        // Set MOBILE_DATA_PREFERRED_UIDS setting again and verify that old rules are removed and
        // new rules are added.
        final Set<Integer> uids2 = Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID),
                PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID2),
                SECONDARY_USER_HANDLE.getUid(TEST_PACKAGE_UID));
        final UidRangeParcel[] uidRanges2 = toUidRangeStableParcels(uidRangesForUids(uids2));
        final NativeUidRangeConfig config2 = new NativeUidRangeConfig(cellNetId, uidRanges2,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        setAndUpdateMobileDataPreferredUids(uids2);
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(config1);
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(config2);

        // Clear MOBILE_DATA_PREFERRED_UIDS setting again and verify that old rules are removed and
        // new rules are not added.
        setAndUpdateMobileDataPreferredUids(Set.of());
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(config2);
        inorder.verify(mMockNetd, never()).networkAddUidRangesParcel(any());
    }

    /**
     * Make sure mobile data preferred uids feature behaves as expected when the mobile network
     * goes up and down while the uids is set. Make sure they behave as expected whether
     * there is a general default network or not.
     */
    @Test
    public void testMobileDataPreferenceForMobileNetworkUpDown() throws Exception {
        final InOrder inorder = inOrder(mMockNetd);
        // File a request for cell to ensure it doesn't go down.
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);
        cellNetworkCallback.assertNoCallback();

        registerDefaultNetworkCallbacks();
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        mTestPackageDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        final int wifiNetId = mWiFiAgent.getNetwork().netId;
        inorder.verify(mMockNetd, times(1)).networkCreate(nativeNetworkConfigPhysical(
                wifiNetId, INetd.PERMISSION_NONE));

        // Initial mobile data preferred uids status.
        setAndUpdateMobileDataPreferredUids(Set.of());
        inorder.verify(mMockNetd, never()).networkAddUidRangesParcel(any());
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());

        // Set MOBILE_DATA_PREFERRED_UIDS setting and verify that wifi net id and uid ranges send to
        // netd.
        final Set<Integer> uids = Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID));
        final UidRangeParcel[] uidRanges = toUidRangeStableParcels(uidRangesForUids(uids));
        final NativeUidRangeConfig wifiConfig = new NativeUidRangeConfig(wifiNetId, uidRanges,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        setAndUpdateMobileDataPreferredUids(uids);
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(wifiConfig);
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());

        // Cellular network connected. mTestPackageDefaultNetworkCallback should receive
        // callback with cellular network and net id and uid ranges should be updated to netd.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.assertNoCallback();
        mTestPackageDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        final int cellNetId = mCellAgent.getNetwork().netId;
        final NativeUidRangeConfig cellConfig = new NativeUidRangeConfig(cellNetId, uidRanges,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        inorder.verify(mMockNetd, times(1)).networkCreate(nativeNetworkConfigPhysical(
                cellNetId, INetd.PERMISSION_NONE));
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(cellConfig);
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(wifiConfig);

        // Cellular network disconnected. mTestPackageDefaultNetworkCallback should receive
        // callback with wifi network from fallback request.
        mCellAgent.disconnect();
        mDefaultNetworkCallback.assertNoCallback();
        cellNetworkCallback.expect(LOST, mCellAgent);
        mTestPackageDefaultNetworkCallback.expect(LOST, mCellAgent);
        mTestPackageDefaultNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(wifiConfig);
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());
        inorder.verify(mMockNetd).networkDestroy(cellNetId);

        // Cellular network comes back. mTestPackageDefaultNetworkCallback should receive
        // callback with cellular network.
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.assertNoCallback();
        mTestPackageDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        final int cellNetId2 = mCellAgent.getNetwork().netId;
        final NativeUidRangeConfig cellConfig2 = new NativeUidRangeConfig(cellNetId2, uidRanges,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        inorder.verify(mMockNetd, times(1)).networkCreate(nativeNetworkConfigPhysical(
                cellNetId2, INetd.PERMISSION_NONE));
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(cellConfig2);
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(wifiConfig);

        // Wifi network disconnected. mTestPackageDefaultNetworkCallback should not receive
        // any callback.
        mWiFiAgent.disconnect();
        mDefaultNetworkCallback.expect(LOST, mWiFiAgent);
        mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        mTestPackageDefaultNetworkCallback.assertNoCallback();
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));
        waitForIdle();
        inorder.verify(mMockNetd, never()).networkAddUidRangesParcel(any());
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());
        inorder.verify(mMockNetd).networkDestroy(wifiNetId);

        mCm.unregisterNetworkCallback(cellNetworkCallback);
    }

    @Test
    public void testMultilayerRequestsOfSetMobileDataPreferredUids() throws Exception {
        // First set mobile data preferred uid to create a multi-layer requests: 1. request for
        // cellular, 2. track the default network for fallback.
        setAndUpdateMobileDataPreferredUids(
                Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID)));

        final HandlerThread handlerThread = new HandlerThread("MockFactory");
        handlerThread.start();
        final NetworkCapabilities cellFilter = new NetworkCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        final MockNetworkFactory cellFactory = new MockNetworkFactory(handlerThread.getLooper(),
                mServiceContext, "cellFactory", cellFilter, mCsHandlerThread);
        cellFactory.setScoreFilter(40);

        try {
            cellFactory.register();
            // Default internet request and the mobile data preferred request.
            cellFactory.expectRequestAdds(2);
            cellFactory.assertRequestCountEquals(2);

            mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
            mWiFiAgent.connect(true);

            // The cellFactory however is outscored, and should lose default internet request.
            // But it should still see mobile data preferred request.
            cellFactory.expectRequestRemove();
            cellFactory.assertRequestCountEquals(1);

            mWiFiAgent.disconnect();
            // The network satisfying the default internet request has disconnected, so the
            // cellFactory sees the default internet requests again.
            cellFactory.expectRequestAdd();
            cellFactory.assertRequestCountEquals(2);
        } finally {
            cellFactory.terminate();
            handlerThread.quitSafely();
            handlerThread.join();
        }
    }

    /**
     * Validate request counts are counted accurately on MOBILE_DATA_PREFERRED_UIDS change
     * on set/replace.
     */
    @Test
    public void testMobileDataPreferredUidsChangedCountsRequestsCorrectlyOnSet() throws Exception {
        ConnectivitySettingsManager.setMobileDataPreferredUids(mServiceContext,
                Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID)));
        // Leave one request available so MDO preference set up above can be set.
        withRequestCountersAcquired(1 /* countToLeaveAvailable */, () ->
                withPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                        Process.myPid(), Process.myUid(), () -> {
                            // Set initially to test the limit prior to having existing requests.
                            mService.updateMobileDataPreferredUids();
                            waitForIdle();

                            // re-set so as to test the limit as part of replacing existing requests
                            mService.updateMobileDataPreferredUids();
                            waitForIdle();
                        }));
    }

    @Test
    public void testAllNetworkPreferencesCanCoexist()
            throws Exception {
        final InOrder inorder = inOrder(mMockNetd);
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;
        final UserHandle testHandle = setupEnterpriseNetwork();

        setOemNetworkPreferenceAgentConnected(TRANSPORT_CELLULAR, true);
        final int cellNetId = mCellAgent.getNetwork().netId;
        inorder.verify(mMockNetd, times(1)).networkCreate(nativeNetworkConfigPhysical(
                cellNetId, INetd.PERMISSION_NONE));

        // Set oem network preference
        final int[] uids1 = new int[] { PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID) };
        final UidRangeParcel[] uidRanges1 = toUidRangeStableParcels(uidRangesForUids(uids1));
        final NativeUidRangeConfig config1 = new NativeUidRangeConfig(cellNetId, uidRanges1,
                PREFERENCE_ORDER_OEM);
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges1, TEST_PACKAGE_NAME);
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(config1);
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());

        // Set user profile network preference
        final TestNetworkAgentWrapper workAgent = makeEnterpriseNetworkAgent();
        workAgent.connect(true);

        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        final NativeUidRangeConfig config2 = new NativeUidRangeConfig(workAgent.getNetwork().netId,
                uidRangeFor(testHandle), PREFERENCE_ORDER_PROFILE);
        inorder.verify(mMockNetd).networkCreate(nativeNetworkConfigPhysical(
                workAgent.getNetwork().netId, INetd.PERMISSION_SYSTEM));
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());
        inorder.verify(mMockNetd).networkAddUidRangesParcel(config2);

        // Set MOBILE_DATA_PREFERRED_UIDS setting
        final Set<Integer> uids2 = Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID2));
        final UidRangeParcel[] uidRanges2 = toUidRangeStableParcels(uidRangesForUids(uids2));
        final NativeUidRangeConfig config3 = new NativeUidRangeConfig(cellNetId, uidRanges2,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        setAndUpdateMobileDataPreferredUids(uids2);
        inorder.verify(mMockNetd, never()).networkRemoveUidRangesParcel(any());
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(config3);

        // Set oem network preference again with different uid.
        final Set<Integer> uids3 = Set.of(PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID3));
        final UidRangeParcel[] uidRanges3 = toUidRangeStableParcels(uidRangesForUids(uids3));
        final NativeUidRangeConfig config4 = new NativeUidRangeConfig(cellNetId, uidRanges3,
                PREFERENCE_ORDER_OEM);
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges3, "com.android.test");
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(config1);
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(config4);

        // Remove user profile network preference
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_DEFAULT,
                r -> r.run(), listener);
        listener.expectOnComplete();
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(config2);
        inorder.verify(mMockNetd, never()).networkAddUidRangesParcel(any());

        // Set MOBILE_DATA_PREFERRED_UIDS setting again with same uid as oem network preference.
        final NativeUidRangeConfig config6 = new NativeUidRangeConfig(cellNetId, uidRanges3,
                PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        setAndUpdateMobileDataPreferredUids(uids3);
        inorder.verify(mMockNetd, times(1)).networkRemoveUidRangesParcel(config3);
        inorder.verify(mMockNetd, times(1)).networkAddUidRangesParcel(config6);
    }

    @Test
    public void testNetworkCallbackAndActiveNetworkForUid_AllNetworkPreferencesEnabled()
            throws Exception {
        // File a request for cell to ensure it doesn't go down.
        final TestNetworkCallback cellNetworkCallback = new TestNetworkCallback();
        final NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        mCm.requestNetwork(cellRequest, cellNetworkCallback);
        cellNetworkCallback.assertNoCallback();

        // Register callbacks and have wifi network as default network.
        registerDefaultNetworkCallbacks();
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(true);
        mDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        mProfileDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        mTestPackageDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(),
                mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        // Set MOBILE_DATA_PREFERRED_UIDS setting with TEST_WORK_PROFILE_APP_UID and
        // TEST_PACKAGE_UID. Both mProfileDefaultNetworkCallback and
        // mTestPackageDefaultNetworkCallback should receive callback with cell network.
        setAndUpdateMobileDataPreferredUids(Set.of(TEST_WORK_PROFILE_APP_UID, TEST_PACKAGE_UID));
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        cellNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mDefaultNetworkCallback.assertNoCallback();
        mProfileDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        mTestPackageDefaultNetworkCallback.expectAvailableThenValidatedCallbacks(mCellAgent);
        assertEquals(mCellAgent.getNetwork(),
                mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        // Set user profile network preference with test profile. mProfileDefaultNetworkCallback
        // should receive callback with higher priority network preference (enterprise network).
        // The others should have no callbacks.
        final UserHandle testHandle = setupEnterpriseNetwork();
        final TestNetworkAgentWrapper workAgent = makeEnterpriseNetworkAgent();
        workAgent.connect(true);
        final TestOnCompleteListener listener = new TestOnCompleteListener();
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_ENTERPRISE,
                r -> r.run(), listener);
        listener.expectOnComplete();
        assertNoCallbacks(mDefaultNetworkCallback, mTestPackageDefaultNetworkCallback);
        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(workAgent);
        assertEquals(workAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        // Set oem network preference with TEST_PACKAGE_UID. mTestPackageDefaultNetworkCallback
        // should receive callback with higher priority network preference (current default network)
        // and the others should have no callbacks.
        @OemNetworkPreferences.OemNetworkPreference final int networkPref =
                OEM_NETWORK_PREFERENCE_OEM_PAID;
        final int[] uids1 = new int[] { TEST_PACKAGE_UID };
        final UidRangeParcel[] uidRanges1 = toUidRangeStableParcels(uidRangesForUids(uids1));
        setupSetOemNetworkPreferenceForPreferenceTest(networkPref, uidRanges1, TEST_PACKAGE_NAME);
        assertNoCallbacks(mDefaultNetworkCallback, mProfileDefaultNetworkCallback);
        mTestPackageDefaultNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        assertEquals(mWiFiAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));
        assertEquals(workAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));

        // Set oem network preference with TEST_WORK_PROFILE_APP_UID. Both
        // mProfileDefaultNetworkCallback and mTestPackageDefaultNetworkCallback should receive
        // callback.
        final int[] uids2 = new int[] { TEST_WORK_PROFILE_APP_UID };
        final UidRangeParcel[] uidRanges2 = toUidRangeStableParcels(uidRangesForUids(uids2));
        doReturn(Arrays.asList(testHandle)).when(mUserManager).getUserHandles(anyBoolean());
        setupSetOemNetworkPreferenceForPreferenceTest(
                networkPref, uidRanges2, "com.android.test", testHandle);
        mDefaultNetworkCallback.assertNoCallback();
        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        mTestPackageDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertEquals(mWiFiAgent.getNetwork(),
                mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        // Remove oem network preference, mProfileDefaultNetworkCallback should receive callback
        // with current highest priority network preference (enterprise network) and the others
        // should have no callbacks.
        final TestOemListenerCallback oemPrefListener = new TestOemListenerCallback();
        mService.setOemNetworkPreference(
                new OemNetworkPreferences.Builder().build(), oemPrefListener);
        oemPrefListener.expectOnComplete();
        assertNoCallbacks(mDefaultNetworkCallback, mTestPackageDefaultNetworkCallback);
        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(workAgent);
        assertEquals(workAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        // Remove user profile network preference.
        mCm.setProfileNetworkPreference(testHandle, PROFILE_NETWORK_PREFERENCE_DEFAULT,
                r -> r.run(), listener);
        listener.expectOnComplete();
        assertNoCallbacks(mDefaultNetworkCallback, mTestPackageDefaultNetworkCallback);
        mProfileDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        assertEquals(mCellAgent.getNetwork(),
                mCm.getActiveNetworkForUid(TEST_WORK_PROFILE_APP_UID));
        assertEquals(mCellAgent.getNetwork(), mCm.getActiveNetworkForUid(TEST_PACKAGE_UID));

        // Disconnect wifi
        mWiFiAgent.disconnect();
        assertNoCallbacks(mProfileDefaultNetworkCallback, mTestPackageDefaultNetworkCallback);
        mDefaultNetworkCallback.expect(LOST, mWiFiAgent);
        mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
    }

    @Test
    public void testRequestRouteToHostAddress_PackageDoesNotBelongToCaller() {
        assertThrows(SecurityException.class, () -> mService.requestRouteToHostAddress(
                ConnectivityManager.TYPE_NONE, null /* hostAddress */, "com.not.package.owner",
                null /* callingAttributionTag */));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testUpdateRateLimit_EnableDisable() throws Exception {
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);

        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(false);

        waitForIdle();

        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHeadWifi =
                mDeps.mRateLimitHistory.newReadHead();
        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHeadCell =
                mDeps.mRateLimitHistory.newReadHead();

        // set rate limit to 8MBit/s => 1MB/s
        final int rateLimitInBytesPerSec = 1 * 1000 * 1000;
        setIngressRateLimit(rateLimitInBytesPerSec);

        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName()
                        && it.second == rateLimitInBytesPerSec));
        assertNotNull(readHeadCell.poll(TIMEOUT_MS,
                it -> it.first == cellLp.getInterfaceName()
                        && it.second == rateLimitInBytesPerSec));

        // disable rate limiting
        setIngressRateLimit(-1);

        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName() && it.second == -1));
        assertNotNull(readHeadCell.poll(TIMEOUT_MS,
                it -> it.first == cellLp.getInterfaceName() && it.second == -1));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testUpdateRateLimit_WhenNewNetworkIsAdded() throws Exception {
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);

        waitForIdle();

        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHead =
                mDeps.mRateLimitHistory.newReadHead();

        // set rate limit to 8MBit/s => 1MB/s
        final int rateLimitInBytesPerSec = 1 * 1000 * 1000;
        setIngressRateLimit(rateLimitInBytesPerSec);
        assertNotNull(readHead.poll(TIMEOUT_MS, it -> it.first == wifiLp.getInterfaceName()
                && it.second == rateLimitInBytesPerSec));

        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(false);
        assertNotNull(readHead.poll(TIMEOUT_MS, it -> it.first == cellLp.getInterfaceName()
                && it.second == rateLimitInBytesPerSec));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testUpdateRateLimit_OnlyAffectsInternetCapableNetworks() throws Exception {
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connectWithoutInternet();

        waitForIdle();

        setIngressRateLimit(1000);
        setIngressRateLimit(-1);

        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHeadWifi =
                mDeps.mRateLimitHistory.newReadHead();
        assertNull(readHeadWifi.poll(TIMEOUT_MS, it -> it.first == wifiLp.getInterfaceName()));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testUpdateRateLimit_DisconnectingResetsRateLimit()
            throws Exception {
        // Steps:
        // - connect network
        // - set rate limit
        // - disconnect network (interface still exists)
        // - disable rate limit
        // - connect network
        // - ensure network interface is not rate limited
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);
        waitForIdle();

        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHeadWifi =
                mDeps.mRateLimitHistory.newReadHead();

        int rateLimitInBytesPerSec = 1000;
        setIngressRateLimit(rateLimitInBytesPerSec);
        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName()
                        && it.second == rateLimitInBytesPerSec));

        mWiFiAgent.disconnect();
        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName() && it.second == -1));

        setIngressRateLimit(-1);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);
        assertNull(readHeadWifi.poll(TIMEOUT_MS, it -> it.first == wifiLp.getInterfaceName()));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testUpdateRateLimit_UpdateExistingRateLimit() throws Exception {
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);
        waitForIdle();

        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHeadWifi =
                mDeps.mRateLimitHistory.newReadHead();

        // update an active ingress rate limit
        setIngressRateLimit(1000);
        setIngressRateLimit(2000);

        // verify the following order of execution:
        // 1. ingress rate limit set to 1000.
        // 2. ingress rate limit disabled (triggered by updating active rate limit).
        // 3. ingress rate limit set to 2000.
        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName()
                        && it.second == 1000));
        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName()
                        && it.second == -1));
        assertNotNull(readHeadWifi.poll(TIMEOUT_MS,
                it -> it.first == wifiLp.getInterfaceName()
                        && it.second == 2000));
    }

    @Test @IgnoreAfter(SC_V2)
    public void testUpdateRateLimit_DoesNothingBeforeT() throws Exception {
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(true);
        waitForIdle();

        final ArrayTrackRecord<Pair<String, Long>>.ReadHead readHead =
                mDeps.mRateLimitHistory.newReadHead();

        setIngressRateLimit(1000);
        waitForIdle();

        assertNull(readHead.poll(TEST_CALLBACK_TIMEOUT_MS, it -> true));
    }

    @Test
    public void testOfferNetwork_ChecksArgumentsOutsideOfHandler() throws Exception {
        final TestableNetworkOfferCallback callback = new TestableNetworkOfferCallback(
                TIMEOUT_MS /* timeout */, TEST_CALLBACK_TIMEOUT_MS /* noCallbackTimeout */);
        final NetworkProvider testProvider = new NetworkProvider(mServiceContext,
                mCsHandlerThread.getLooper(), "Test provider");
        final NetworkCapabilities caps = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();

        final NetworkScore score = new NetworkScore.Builder().build();
        testProvider.registerNetworkOffer(score, caps, r -> r.run(), callback);
        testProvider.unregisterNetworkOffer(callback);

        assertThrows(NullPointerException.class,
                () -> mService.offerNetwork(100, score, caps, null));
        assertThrows(NullPointerException.class, () -> mService.unofferNetwork(null));
    }

    public void doTestIgnoreValidationAfterRoam(int resValue, final boolean enabled)
            throws Exception {
        doReturn(resValue).when(mResources)
                .getInteger(R.integer.config_validationFailureAfterRoamIgnoreTimeMillis);

        final String bssid1 = "AA:AA:AA:AA:AA:AA";
        final String bssid2 = "BB:BB:BB:BB:BB:BB";
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR);
        mCellAgent.connect(true);
        NetworkCapabilities wifiNc1 = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VPN)
                .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NET_CAPABILITY_TRUSTED)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .addTransportType(TRANSPORT_WIFI)
                .setTransportInfo(new WifiInfo.Builder().setBssid(bssid1).build());
        NetworkCapabilities wifiNc2 = new NetworkCapabilities(wifiNc1)
                .setTransportInfo(new WifiInfo.Builder().setBssid(bssid2).build());
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp, wifiNc1);
        mWiFiAgent.connect(true);

        // The default network will be switching to Wi-Fi Network.
        final TestNetworkCallback wifiNetworkCallback = new TestNetworkCallback();
        final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).build();
        mCm.requestNetwork(wifiRequest, wifiNetworkCallback);
        wifiNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);
        registerDefaultNetworkCallbacks();
        mDefaultNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);

        // There is a bug in the current code where ignoring validation after roam will not
        // correctly change the default network if the result if the validation is partial or
        // captive portal. TODO : fix the bug and reinstate this code.
        if (false) {
            // Wi-Fi roaming from wifiNc1 to wifiNc2 but the network is now behind a captive portal.
            mWiFiAgent.setNetworkCapabilities(wifiNc2, true /* sendToConnectivityService */);
            // The only thing changed in this CAPS is the BSSID, which can't be tested for in this
            // test because it's redacted.
            wifiNetworkCallback.expectCaps(mWiFiAgent);
            mDefaultNetworkCallback.expectCaps(mWiFiAgent);
            mWiFiAgent.setNetworkPortal(TEST_REDIRECT_URL, false /* privateDnsProbeSent */);
            mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);
            // Wi-Fi is now detected to have a portal : cell should become the default network.
            mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
            wifiNetworkCallback.expectCaps(mWiFiAgent,
                    c -> !c.hasCapability(NET_CAPABILITY_VALIDATED));
            wifiNetworkCallback.expectCaps(mWiFiAgent,
                    c -> c.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL));

            // Wi-Fi becomes valid again. The default network goes back to Wi-Fi.
            mWiFiAgent.setNetworkValid(false /* privateDnsProbeSent */);
            mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
            mDefaultNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);
            wifiNetworkCallback.expectCaps(mWiFiAgent,
                    c -> !c.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL));

            // Wi-Fi roaming from wifiNc2 to wifiNc1, and the network now has partial connectivity.
            mWiFiAgent.setNetworkCapabilities(wifiNc1, true);
            wifiNetworkCallback.expectCaps(mWiFiAgent);
            mDefaultNetworkCallback.expectCaps(mWiFiAgent);
            mWiFiAgent.setNetworkPartial();
            mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);
            // Wi-Fi now only offers partial connectivity, so in the absence of accepting partial
            // connectivity explicitly for this network, it loses default status to cell.
            mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
            wifiNetworkCallback.expectCaps(mWiFiAgent,
                    c -> c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));

            // Wi-Fi becomes valid again. The default network goes back to Wi-Fi.
            mWiFiAgent.setNetworkValid(false /* privateDnsProbeSent */);
            mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), true);
            mDefaultNetworkCallback.expectAvailableCallbacksValidated(mWiFiAgent);
            wifiNetworkCallback.expectCaps(mWiFiAgent,
                    c -> !c.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY));
        }
        mCm.unregisterNetworkCallback(wifiNetworkCallback);

        // Wi-Fi roams from wifiNc1 to wifiNc2, and now becomes really invalid. If validation
        // failures after roam are not ignored, this will cause cell to become the default network.
        // If they are ignored, this will not cause a switch until later.
        mWiFiAgent.setNetworkCapabilities(wifiNc2, true);
        mDefaultNetworkCallback.expectCaps(mWiFiAgent);
        mWiFiAgent.setNetworkInvalid(false /* invalidBecauseOfPrivateDns */);
        mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);

        if (enabled) {
            // Network validation failed, but the result will be ignored.
            assertTrue(mCm.getNetworkCapabilities(mWiFiAgent.getNetwork()).hasCapability(
                    NET_CAPABILITY_VALIDATED));
            mWiFiAgent.setNetworkValid(false);

            // Behavior of after config_validationFailureAfterRoamIgnoreTimeMillis
            ConditionVariable waitForValidationBlock = new ConditionVariable();
            doReturn(50).when(mResources)
                    .getInteger(R.integer.config_validationFailureAfterRoamIgnoreTimeMillis);
            // Wi-Fi roaming from wifiNc2 to wifiNc1.
            mWiFiAgent.setNetworkCapabilities(wifiNc1, true);
            mWiFiAgent.setNetworkInvalid(false);
            waitForValidationBlock.block(150);
            mCm.reportNetworkConnectivity(mWiFiAgent.getNetwork(), false);
            mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        } else {
            mDefaultNetworkCallback.expectAvailableCallbacksValidated(mCellAgent);
        }

        // Wi-Fi is still connected and would become the default network if cell were to
        // disconnect. This assertion ensures that the switch to cellular was not caused by
        // Wi-Fi disconnecting (e.g., because the capability change to wifiNc2 caused it
        // to stop satisfying the default request).
        mCellAgent.disconnect();
        mDefaultNetworkCallback.expect(LOST, mCellAgent);
        mDefaultNetworkCallback.expectAvailableCallbacksUnvalidated(mWiFiAgent);

    }

    @Test
    public void testIgnoreValidationAfterRoamDisabled() throws Exception {
        doTestIgnoreValidationAfterRoam(-1, false /* enabled */);
    }

    @Test
    public void testIgnoreValidationAfterRoamEnabled() throws Exception {
        final boolean enabled = !mDeps.isAtLeastT();
        doTestIgnoreValidationAfterRoam(5_000, enabled);
    }

    @Test
    public void testShouldIgnoreValidationFailureAfterRoam() {
        // Always disabled on T+.
        assumeFalse(mDeps.isAtLeastT());

        NetworkAgentInfo nai = fakeWifiNai(new NetworkCapabilities());

        // Enabled, but never roamed.
        doReturn(5_000).when(mResources)
                .getInteger(R.integer.config_validationFailureAfterRoamIgnoreTimeMillis);
        assertEquals(0, nai.lastRoamTime);
        assertFalse(mService.shouldIgnoreValidationFailureAfterRoam(nai));

        // Roamed recently.
        nai.lastRoamTime = SystemClock.elapsedRealtime() - 500 /* ms */;
        assertTrue(mService.shouldIgnoreValidationFailureAfterRoam(nai));

        // Disabled due to invalid setting (maximum is 10 seconds).
        doReturn(15_000).when(mResources)
                .getInteger(R.integer.config_validationFailureAfterRoamIgnoreTimeMillis);
        assertFalse(mService.shouldIgnoreValidationFailureAfterRoam(nai));

        // Disabled.
        doReturn(-1).when(mResources)
                .getInteger(R.integer.config_validationFailureAfterRoamIgnoreTimeMillis);
        assertFalse(mService.shouldIgnoreValidationFailureAfterRoam(nai));
    }


    @Test
    public void testLegacyTetheringApiGuardWithProperPermission() throws Exception {
        final String testIface = "test0";
        mServiceContext.setPermission(ACCESS_NETWORK_STATE, PERMISSION_DENIED);
        assertThrows(SecurityException.class, () -> mService.getLastTetherError(testIface));
        assertThrows(SecurityException.class, () -> mService.getTetherableIfaces());
        assertThrows(SecurityException.class, () -> mService.getTetheredIfaces());
        assertThrows(SecurityException.class, () -> mService.getTetheringErroredIfaces());
        assertThrows(SecurityException.class, () -> mService.getTetherableUsbRegexs());
        assertThrows(SecurityException.class, () -> mService.getTetherableWifiRegexs());

        withPermission(ACCESS_NETWORK_STATE, () -> {
            mService.getLastTetherError(testIface);
            verify(mTetheringManager).getLastTetherError(testIface);

            mService.getTetherableIfaces();
            verify(mTetheringManager).getTetherableIfaces();

            mService.getTetheredIfaces();
            verify(mTetheringManager).getTetheredIfaces();

            mService.getTetheringErroredIfaces();
            verify(mTetheringManager).getTetheringErroredIfaces();

            mService.getTetherableUsbRegexs();
            verify(mTetheringManager).getTetherableUsbRegexs();

            mService.getTetherableWifiRegexs();
            verify(mTetheringManager).getTetherableWifiRegexs();
        });
    }

    private void verifyMtuSetOnWifiInterface(int mtu) throws Exception {
        verify(mMockNetd, times(1)).interfaceSetMtu(WIFI_IFNAME, mtu);
    }

    private void verifyMtuNeverSetOnWifiInterface() throws Exception {
        verify(mMockNetd, never()).interfaceSetMtu(eq(WIFI_IFNAME), anyInt());
    }

    private void verifyMtuSetOnWifiInterfaceOnlyUpToT(int mtu) throws Exception {
        if (!mService.shouldCreateNetworksImmediately()) {
            verify(mMockNetd, times(1)).interfaceSetMtu(WIFI_IFNAME, mtu);
        } else {
            verify(mMockNetd, never()).interfaceSetMtu(eq(WIFI_IFNAME), anyInt());
        }
    }

    private void verifyMtuSetOnWifiInterfaceOnlyStartingFromU(int mtu) throws Exception {
        if (mService.shouldCreateNetworksImmediately()) {
            verify(mMockNetd, times(1)).interfaceSetMtu(WIFI_IFNAME, mtu);
        } else {
            verify(mMockNetd, never()).interfaceSetMtu(eq(WIFI_IFNAME), anyInt());
        }
    }

    @Test
    public void testSendLinkPropertiesSetInterfaceMtuBeforeConnect() throws Exception {
        final int mtu = 1281;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.sendLinkProperties(lp);
        waitForIdle();
        verifyMtuSetOnWifiInterface(mtu);
        reset(mMockNetd);

        mWiFiAgent.connect(false /* validated */);
        // Before U, the MTU is always (re-)applied when the network connects.
        verifyMtuSetOnWifiInterfaceOnlyUpToT(mtu);
    }

    @Test
    public void testSendLinkPropertiesUpdateInterfaceMtuBeforeConnect() throws Exception {
        final int mtu = 1327;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        // Registering an agent with an MTU only sets the MTU on U+.
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        waitForIdle();
        verifyMtuSetOnWifiInterfaceOnlyStartingFromU(mtu);
        reset(mMockNetd);

        // Future updates with the same MTU don't set the MTU even on T when it's not set initially.
        mWiFiAgent.sendLinkProperties(lp);
        waitForIdle();
        verifyMtuNeverSetOnWifiInterface();

        // Updating with a different MTU does work.
        lp.setMtu(mtu + 1);
        mWiFiAgent.sendLinkProperties(lp);
        waitForIdle();
        verifyMtuSetOnWifiInterface(mtu + 1);
        reset(mMockNetd);

        mWiFiAgent.connect(false /* validated */);
        // Before U, the MTU is always (re-)applied when the network connects.
        verifyMtuSetOnWifiInterfaceOnlyUpToT(mtu + 1);
    }

    @Test
    public void testSendLinkPropertiesUpdateInterfaceMtuAfterConnect() throws Exception {
        final int mtu = 1327;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI);
        mWiFiAgent.connect(false /* validated */);
        verifyMtuNeverSetOnWifiInterface();

        mWiFiAgent.sendLinkProperties(lp);
        waitForIdle();
        // The MTU is always (re-)applied when the network connects.
        verifyMtuSetOnWifiInterface(mtu);
    }

    @Test
    public void testSendLinkPropertiesSetInterfaceMtu_DifferentMtu() throws Exception {
        final int mtu = 1328, mtu2 = 1500;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        mWiFiAgent.connect(false /* validated */);
        verifyMtuSetOnWifiInterface(mtu);
        reset(mMockNetd);

        LinkProperties lp2 = new LinkProperties(lp);
        lp2.setMtu(mtu2);
        mWiFiAgent.sendLinkProperties(lp2);
        waitForIdle();
        verifyMtuSetOnWifiInterface(mtu2);
    }

    @Test
    public void testSendLinkPropertiesSetInterfaceMtu_IdenticalMtuAndIface() throws Exception {
        final int mtu = 1329;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        mWiFiAgent.connect(false /* validated */);
        verifyMtuSetOnWifiInterface(mtu);
        reset(mMockNetd);

        mWiFiAgent.sendLinkProperties(new LinkProperties(lp));
        waitForIdle();
        verifyMtuNeverSetOnWifiInterface();
    }

    @Test
    public void testSendLinkPropertiesSetInterfaceMtu_IdenticalMtuAndNullIface() throws Exception {
        final int mtu = 1330;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        mWiFiAgent.connect(false /* validated */);
        verifyMtuSetOnWifiInterface(mtu);
        reset(mMockNetd);

        LinkProperties lp2 = new LinkProperties(lp);
        lp2.setInterfaceName(null);
        mWiFiAgent.sendLinkProperties(new LinkProperties(lp2));
        waitForIdle();
        verifyMtuNeverSetOnWifiInterface();
    }

    @Test
    public void testSendLinkPropertiesSetInterfaceMtu_IdenticalMtuDiffIface() throws Exception {
        final int mtu = 1331;
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(WIFI_IFNAME);
        lp.setMtu(mtu);

        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, lp);
        mWiFiAgent.connect(false /* validated */);
        verifyMtuSetOnWifiInterface(mtu);
        reset(mMockNetd);

        final String ifaceName2 = WIFI_IFNAME + "_2";
        LinkProperties lp2 = new LinkProperties(lp);
        lp2.setInterfaceName(ifaceName2);

        mWiFiAgent.sendLinkProperties(new LinkProperties(lp2));
        waitForIdle();
        verify(mMockNetd, times(1)).interfaceSetMtu(eq(ifaceName2), eq(mtu));
        verifyMtuNeverSetOnWifiInterface();
    }

    @Test
    public void testCreateDeliveryGroupKeyForConnectivityAction() throws Exception {
        final NetworkInfo info = new NetworkInfo(0 /* type */, 2 /* subtype */,
                "MOBILE" /* typeName */, "LTE" /* subtypeName */);
        assertEquals("0;2;null", createDeliveryGroupKeyForConnectivityAction(info));

        info.setExtraInfo("test_info");
        assertEquals("0;2;test_info", createDeliveryGroupKeyForConnectivityAction(info));
    }

    @Test
    public void testNetdWakeupAddInterfaceForWifiTransport() throws Exception {
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);
        mWiFiAgent.connect(false /* validated */);

        final String expectedPrefix = makeNflogPrefix(WIFI_IFNAME,
                mWiFiAgent.getNetwork().getNetworkHandle());
        verify(mMockNetd).wakeupAddInterface(WIFI_IFNAME, expectedPrefix, PACKET_WAKEUP_MARK_MASK,
                PACKET_WAKEUP_MARK_MASK);
    }

    @Test
    public void testNetdWakeupAddInterfaceForCellularTransport() throws Exception {
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        mCellAgent.connect(false /* validated */);

        if (mDeps.isAtLeastU()) {
            final String expectedPrefix = makeNflogPrefix(MOBILE_IFNAME,
                    mCellAgent.getNetwork().getNetworkHandle());
            verify(mMockNetd).wakeupAddInterface(MOBILE_IFNAME, expectedPrefix,
                    PACKET_WAKEUP_MARK_MASK, PACKET_WAKEUP_MARK_MASK);
        } else {
            verify(mMockNetd, never()).wakeupAddInterface(eq(MOBILE_IFNAME), anyString(), anyInt(),
                    anyInt());
        }
    }

    @Test
    public void testNetdWakeupAddInterfaceForEthernetTransport() throws Exception {
        final String ethernetIface = "eth42";

        final LinkProperties ethLp = new LinkProperties();
        ethLp.setInterfaceName(ethernetIface);
        mEthernetAgent = new TestNetworkAgentWrapper(TRANSPORT_ETHERNET, ethLp);
        mEthernetAgent.connect(false /* validated */);

        verify(mMockNetd, never()).wakeupAddInterface(eq(ethernetIface), anyString(), anyInt(),
                anyInt());
    }

    // UidFrozenStateChangedCallback is added in U API.
    // Returning UidFrozenStateChangedCallback directly makes the test fail on T- devices since
    // AndroidJUnit4ClassRunner iterates all declared methods and tries to resolve the return type.
    // Solve this by wrapping it in an AtomicReference. Because of erasure, this removes the
    // resolving problem as the type isn't seen dynamically.
    private AtomicReference<UidFrozenStateChangedCallback> getUidFrozenStateChangedCallback() {
        ArgumentCaptor<UidFrozenStateChangedCallback> activityManagerCallbackCaptor =
                ArgumentCaptor.forClass(UidFrozenStateChangedCallback.class);
        verify(mActivityManager).registerUidFrozenStateChangedCallback(any(),
                activityManagerCallbackCaptor.capture());
        return new AtomicReference<>(activityManagerCallbackCaptor.getValue());
    }

    private BaseNetdUnsolicitedEventListener getRegisteredNetdUnsolicitedEventListener()
            throws RemoteException {
        ArgumentCaptor<BaseNetdUnsolicitedEventListener> netdCallbackCaptor =
                ArgumentCaptor.forClass(BaseNetdUnsolicitedEventListener.class);
        verify(mMockNetd).registerUnsolicitedEventListener(netdCallbackCaptor.capture());
        return netdCallbackCaptor.getValue();
    }

    private static final int TEST_FROZEN_UID = 1000;
    private static final int TEST_UNFROZEN_UID = 2000;

    /**
     * Send a UidFrozenStateChanged message to ConnectivityService. Verify that only the frozen UID
     * gets passed to socketDestroy().
     */
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testFrozenUidSocketDestroy() throws Exception {
        final UidFrozenStateChangedCallback callback =
                getUidFrozenStateChangedCallback().get();

        final int[] uids = {TEST_FROZEN_UID, TEST_UNFROZEN_UID};
        final int[] frozenStates = {UID_FROZEN_STATE_FROZEN, UID_FROZEN_STATE_UNFROZEN};

        callback.onUidFrozenStateChanged(uids, frozenStates);

        waitForIdle();

        verify(mDestroySocketsWrapper).destroyLiveTcpSocketsByOwnerUids(Set.of(TEST_FROZEN_UID));
    }

    private void doTestDelayFrozenUidSocketDestroy(int transportType,
            boolean freezeWithNetworkInactive, boolean expectDelay) throws Exception {
        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(transportToTestIfaceName(transportType));
        final TestNetworkAgentWrapper agent = new TestNetworkAgentWrapper(transportType, lp);
        final int idleTimerLabel = getIdleTimerLabel(agent.getNetwork().netId, transportType);
        testAndCleanup(() -> {
            final UidFrozenStateChangedCallback uidFrozenStateChangedCallback =
                    getUidFrozenStateChangedCallback().get();
            final BaseNetdUnsolicitedEventListener netdUnsolicitedEventListener =
                    getRegisteredNetdUnsolicitedEventListener();

            mCm.registerDefaultNetworkCallback(defaultCallback);
            agent.connect(true);
            defaultCallback.expectAvailableThenValidatedCallbacks(agent);
            if (freezeWithNetworkInactive) {
                // Make network inactive
                netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                        idleTimerLabel, TIMESTAMP, NETWORK_ACTIVITY_NO_UID);
            }

            // Freeze TEST_FROZEN_UID and TEST_UNFROZEN_UID
            final int[] uids1 = {TEST_FROZEN_UID, TEST_UNFROZEN_UID};
            final int[] frozenStates1 = {UID_FROZEN_STATE_FROZEN, UID_FROZEN_STATE_FROZEN};
            uidFrozenStateChangedCallback.onUidFrozenStateChanged(uids1, frozenStates1);
            waitForIdle();

            if (expectDelay) {
                verify(mDestroySocketsWrapper, never()).destroyLiveTcpSocketsByOwnerUids(any());
            } else {
                verify(mDestroySocketsWrapper).destroyLiveTcpSocketsByOwnerUids(
                        Set.of(TEST_FROZEN_UID, TEST_UNFROZEN_UID));
                clearInvocations(mDestroySocketsWrapper);
            }

            // Unfreeze TEST_UNFROZEN_UID
            final int[] uids2 = {TEST_UNFROZEN_UID};
            final int[] frozenStates2 = {UID_FROZEN_STATE_UNFROZEN};
            uidFrozenStateChangedCallback.onUidFrozenStateChanged(uids2, frozenStates2);

            // Make network active
            netdUnsolicitedEventListener.onInterfaceClassActivityChanged(true /* isActive */,
                    idleTimerLabel, TIMESTAMP, TEST_PACKAGE_UID);
            waitForIdle();

            if (expectDelay) {
                verify(mDestroySocketsWrapper).destroyLiveTcpSocketsByOwnerUids(
                        Set.of(TEST_FROZEN_UID));
            } else {
                verify(mDestroySocketsWrapper, never()).destroyLiveTcpSocketsByOwnerUids(any());
            }
        }, () -> { // Cleanup
                agent.disconnect();
            }, () -> {
                mCm.unregisterNetworkCallback(defaultCallback);
            });
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testDelayFrozenUidSocketDestroy_ActiveCellular() throws Exception {
        doTestDelayFrozenUidSocketDestroy(TRANSPORT_CELLULAR, false /* freezeWithNetworkInactive */,
                false /* expectDelay */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testDelayFrozenUidSocketDestroy_InactiveCellular() throws Exception {
        // When the default network is cellular and cellular network is inactive, closing socket
        // is delayed.
        doTestDelayFrozenUidSocketDestroy(TRANSPORT_CELLULAR, true /* freezeWithNetworkInactive */,
                true /* expectDelay */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testDelayFrozenUidSocketDestroy_ActiveWifi() throws Exception {
        doTestDelayFrozenUidSocketDestroy(TRANSPORT_WIFI, false /* freezeWithNetworkInactive */,
                false /* expectDelay */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testDelayFrozenUidSocketDestroy_InactiveWifi() throws Exception {
        doTestDelayFrozenUidSocketDestroy(TRANSPORT_WIFI, true /* freezeWithNetworkInactive */,
                false /* expectDelay */);
    }

    /**
     * @param switchToWifi if true, simulate a migration of the default network to wifi
     *                     if false, simulate a cell disconnection
     */
    private void doTestLoseCellDefaultNetwork_ClosePendingFrozenSockets(final boolean switchToWifi)
            throws Exception {
        final UidFrozenStateChangedCallback uidFrozenStateChangedCallback =
                getUidFrozenStateChangedCallback().get();
        final BaseNetdUnsolicitedEventListener netdUnsolicitedEventListener =
                getRegisteredNetdUnsolicitedEventListener();

        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName(WIFI_IFNAME);
        mWiFiAgent = new TestNetworkAgentWrapper(TRANSPORT_WIFI, wifiLp);

        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName(MOBILE_IFNAME);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, cellLp);
        final int idleTimerLabel =
                getIdleTimerLabel(mCellAgent.getNetwork().netId, TRANSPORT_CELLULAR);

        final TestNetworkCallback defaultCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultCallback);
        try {
            mCellAgent.connect(true);
            defaultCallback.expectAvailableThenValidatedCallbacks(mCellAgent);

            // Make cell network inactive
            netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                    idleTimerLabel, TIMESTAMP, NETWORK_ACTIVITY_NO_UID);

            // Freeze TEST_FROZEN_UID
            final int[] uids = {TEST_FROZEN_UID};
            final int[] frozenStates = {UID_FROZEN_STATE_FROZEN};
            uidFrozenStateChangedCallback.onUidFrozenStateChanged(uids, frozenStates);
            waitForIdle();

            // Closing frozen sockets should be delayed since the default network is cellular
            // and cellular network is inactive.
            verify(mDestroySocketsWrapper, never()).destroyLiveTcpSocketsByOwnerUids(any());

            if (switchToWifi) {
                mWiFiAgent.connect(true);
                defaultCallback.expectAvailableDoubleValidatedCallbacks(mWiFiAgent);
            } else {
                mCellAgent.disconnect();
                waitForIdle();
            }

            // Pending frozen sockets should be closed since the cellular network is no longer the
            // default network.
            verify(mDestroySocketsWrapper)
                    .destroyLiveTcpSocketsByOwnerUids(Set.of(TEST_FROZEN_UID));
        } finally {
            mCm.unregisterNetworkCallback(defaultCallback);
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testLoseCellDefaultNetwork_SwitchToWifi_ClosePendingFrozenSockets()
            throws Exception {
        doTestLoseCellDefaultNetwork_ClosePendingFrozenSockets(true /* switchToWifi */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testLoseCellDefaultNetwork_NoDefaultNetwork_ClosePendingFrozenSockets()
            throws Exception {
        doTestLoseCellDefaultNetwork_ClosePendingFrozenSockets(false /* switchToWifi */);
    }

    @Test
    public void testDisconnectSuspendedNetworkStopClatd() throws Exception {
        final TestNetworkCallback networkCallback = new TestNetworkCallback();
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_DUN)
                .build();
        mCm.requestNetwork(networkRequest, networkCallback);

        final IpPrefix nat64Prefix = new IpPrefix(InetAddress.getByName("64:ff9b::"), 96);
        NetworkCapabilities nc = new NetworkCapabilities().addCapability(NET_CAPABILITY_DUN);
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(MOBILE_IFNAME);
        lp.addLinkAddress(new LinkAddress("2001:db8:1::1/64"));
        lp.setNat64Prefix(nat64Prefix);
        mCellAgent = new TestNetworkAgentWrapper(TRANSPORT_CELLULAR, lp, nc);
        mCellAgent.connect(true /* validated */, false /* hasInternet */,
                false /* privateDnsProbeSent */);

        verifyClatdStart(null /* inOrder */, MOBILE_IFNAME, mCellAgent.getNetwork().netId,
                nat64Prefix.toString());

        mCellAgent.suspend();
        mCm.unregisterNetworkCallback(networkCallback);
        mCellAgent.expectDisconnected();
        waitForIdle();

        verifyClatdStop(null /* inOrder */, MOBILE_IFNAME);
    }

    // Note : adding tests is ConnectivityServiceTest is deprecated, as it is too big for
    // maintenance. Please consider adding new tests in subclasses of CSTest instead.
}
