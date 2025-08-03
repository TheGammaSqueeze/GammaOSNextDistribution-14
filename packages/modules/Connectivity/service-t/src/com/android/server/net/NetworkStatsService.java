/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.net;

import static android.Manifest.permission.NETWORK_STATS_PROVIDER;
import static android.Manifest.permission.READ_NETWORK_USAGE_HISTORY;
import static android.Manifest.permission.UPDATE_DEVICE_STATS;
import static android.app.usage.NetworkStatsManager.PREFIX_DEV;
import static android.content.Intent.ACTION_SHUTDOWN;
import static android.content.Intent.ACTION_UID_REMOVED;
import static android.content.Intent.ACTION_USER_REMOVED;
import static android.content.Intent.EXTRA_UID;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkStats.DEFAULT_NETWORK_ALL;
import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.IFACE_ALL;
import static android.net.NetworkStats.IFACE_VT;
import static android.net.NetworkStats.INTERFACES_ALL;
import static android.net.NetworkStats.METERED_ALL;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkStats.ROAMING_ALL;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_ALL;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.SET_FOREGROUND;
import static android.net.NetworkStats.STATS_PER_IFACE;
import static android.net.NetworkStats.STATS_PER_UID;
import static android.net.NetworkStats.TAG_ALL;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStatsHistory.FIELD_ALL;
import static android.net.NetworkTemplate.MATCH_MOBILE;
import static android.net.NetworkTemplate.MATCH_TEST;
import static android.net.NetworkTemplate.MATCH_WIFI;
import static android.net.TrafficStats.KB_IN_BYTES;
import static android.net.TrafficStats.MB_IN_BYTES;
import static android.net.TrafficStats.UID_TETHERING;
import static android.net.TrafficStats.UNSUPPORTED;
import static android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_UID;
import static android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_UID_TAG;
import static android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_XT;
import static android.os.Trace.TRACE_TAG_NETWORK;
import static android.system.OsConstants.ENOENT;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PRIVATE;
import static com.android.net.module.util.NetworkCapabilitiesUtils.getDisplayTransport;
import static com.android.net.module.util.NetworkStatsUtils.LIMIT_GLOBAL_ALERT;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_PERIODIC;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_DUMPSYS;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_FORCE_UPDATE;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_GLOBAL_ALERT;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_NETWORK_STATUS_CHANGED;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_OPEN_SESSION;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_RAT_CHANGED;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_REG_CALLBACK;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_REMOVE_UIDS;
import static com.android.server.net.NetworkStatsEventLogger.POLL_REASON_UPSTREAM_CHANGED;
import static com.android.server.net.NetworkStatsEventLogger.PollEvent;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.BroadcastOptions;
import android.app.PendingIntent;
import android.app.usage.NetworkStatsManager;
import android.content.ApexEnvironment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.DataUsageRequest;
import android.net.INetd;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkIdentity;
import android.net.NetworkIdentitySet;
import android.net.NetworkPolicyManager;
import android.net.NetworkSpecifier;
import android.net.NetworkStack;
import android.net.NetworkStateSnapshot;
import android.net.NetworkStats;
import android.net.NetworkStats.NonMonotonicObserver;
import android.net.NetworkStatsAccess;
import android.net.NetworkStatsCollection;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TelephonyNetworkSpecifier;
import android.net.TetherStatsParcel;
import android.net.TetheringManager;
import android.net.TrafficStats;
import android.net.TransportInfo;
import android.net.UnderlyingNetworkInfo;
import android.net.Uri;
import android.net.netstats.IUsageCallback;
import android.net.netstats.NetworkStatsDataMigrationUtils;
import android.net.netstats.provider.INetworkStatsProvider;
import android.net.netstats.provider.INetworkStatsProviderCallback;
import android.net.netstats.provider.NetworkStatsProvider;
import android.net.wifi.WifiInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.NetworkInterfaceProto;
import android.service.NetworkStatsServiceDumpProto;
import android.system.ErrnoException;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionPlan;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.EventLog;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FileRotator;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.BestClock;
import com.android.net.module.util.BinderUtils;
import com.android.net.module.util.BpfDump;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.LocationPermissionChecker;
import com.android.net.module.util.NetworkStatsUtils;
import com.android.net.module.util.PermissionUtils;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.S32;
import com.android.net.module.util.Struct.U8;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;
import com.android.networkstack.apishim.BroadcastOptionsShimImpl;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.server.BpfNetMaps;
import com.android.server.connectivity.ConnectivityResources;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Collect and persist detailed network statistics, and provide this data to
 * other system services.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class NetworkStatsService extends INetworkStatsService.Stub {
    static {
        System.loadLibrary("service-connectivity");
    }

    static final String TAG = "NetworkStats";
    static final boolean LOGD = Log.isLoggable(TAG, Log.DEBUG);
    static final boolean LOGV = Log.isLoggable(TAG, Log.VERBOSE);

    // Perform polling and persist all (FLAG_PERSIST_ALL).
    private static final int MSG_PERFORM_POLL = 1;
    // Perform polling, persist network, and register the global alert again.
    private static final int MSG_PERFORM_POLL_REGISTER_ALERT = 2;
    private static final int MSG_NOTIFY_NETWORK_STATUS = 3;
    // A message for broadcasting ACTION_NETWORK_STATS_UPDATED in handler thread to prevent
    // deadlock.
    private static final int MSG_BROADCAST_NETWORK_STATS_UPDATED = 4;

    /** Flags to control detail level of poll event. */
    private static final int FLAG_PERSIST_NETWORK = 0x1;
    private static final int FLAG_PERSIST_UID = 0x2;
    private static final int FLAG_PERSIST_ALL = FLAG_PERSIST_NETWORK | FLAG_PERSIST_UID;
    private static final int FLAG_PERSIST_FORCE = 0x100;

    /**
     * When global alert quota is high, wait for this delay before processing each polling,
     * and do not schedule further polls once there is already one queued.
     * This avoids firing the global alert too often on devices with high transfer speeds and
     * high quota.
     */
    private static final int DEFAULT_PERFORM_POLL_DELAY_MS = 1000;

    private static final String TAG_NETSTATS_ERROR = "netstats_error";

    /**
     * EventLog tags used when logging into the event log. Note the values must be sync with
     * frameworks/base/services/core/java/com/android/server/EventLogTags.logtags to get correct
     * name translation.
      */
    private static final int LOG_TAG_NETSTATS_MOBILE_SAMPLE = 51100;
    private static final int LOG_TAG_NETSTATS_WIFI_SAMPLE = 51101;

    // TODO: Replace the hardcoded string and move it into ConnectivitySettingsManager.
    private static final String NETSTATS_COMBINE_SUBTYPE_ENABLED =
            "netstats_combine_subtype_enabled";

    private static final String UID_COUNTERSET_MAP_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_uid_counterset_map";
    private static final String COOKIE_TAG_MAP_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_cookie_tag_map";
    private static final String APP_UID_STATS_MAP_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_app_uid_stats_map";
    private static final String STATS_MAP_A_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_stats_map_A";
    private static final String STATS_MAP_B_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_stats_map_B";
    private static final String IFACE_STATS_MAP_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_iface_stats_map";

    /**
     * DeviceConfig flag used to indicate whether the files should be stored in the apex data
     * directory.
     */
    static final String NETSTATS_STORE_FILES_IN_APEXDATA = "netstats_store_files_in_apexdata";
    /**
     * DeviceConfig flag is used to indicate whether the legacy files need to be imported, and
     * retry count before giving up. Only valid when {@link #NETSTATS_STORE_FILES_IN_APEXDATA}
     * set to true. Note that the value gets rollback when the mainline module gets rollback.
     */
    static final String NETSTATS_IMPORT_LEGACY_TARGET_ATTEMPTS =
            "netstats_import_legacy_target_attempts";
    static final int DEFAULT_NETSTATS_IMPORT_LEGACY_TARGET_ATTEMPTS = 1;
    static final String NETSTATS_IMPORT_ATTEMPTS_COUNTER_NAME = "import.attempts";
    static final String NETSTATS_IMPORT_SUCCESSES_COUNTER_NAME = "import.successes";
    static final String NETSTATS_IMPORT_FALLBACKS_COUNTER_NAME = "import.fallbacks";
    static final String CONFIG_ENABLE_NETWORK_STATS_EVENT_LOGGER =
            "enable_network_stats_event_logger";

    static final String NETSTATS_FASTDATAINPUT_TARGET_ATTEMPTS =
            "netstats_fastdatainput_target_attempts";
    static final String NETSTATS_FASTDATAINPUT_SUCCESSES_COUNTER_NAME = "fastdatainput.successes";
    static final String NETSTATS_FASTDATAINPUT_FALLBACKS_COUNTER_NAME = "fastdatainput.fallbacks";

    private final Context mContext;
    private final NetworkStatsFactory mStatsFactory;
    private final AlarmManager mAlarmManager;
    private final Clock mClock;
    private final NetworkStatsSettings mSettings;
    private final NetworkStatsObservers mStatsObservers;

    private final File mStatsDir;

    private final PowerManager.WakeLock mWakeLock;

    private final ContentObserver mContentObserver;
    private final ContentResolver mContentResolver;

    protected INetd mNetd;
    private final AlertObserver mAlertObserver = new AlertObserver();

    // Persistent counters that backed by AtomicFile which stored in the data directory as a file,
    // to track attempts/successes/fallbacks count across reboot. Note that these counter values
    // will be rollback as the module rollbacks.
    private PersistentInt mImportLegacyAttemptsCounter = null;
    private PersistentInt mImportLegacySuccessesCounter = null;
    private PersistentInt mImportLegacyFallbacksCounter = null;
    private PersistentInt mFastDataInputSuccessesCounter = null;
    private PersistentInt mFastDataInputFallbacksCounter = null;

    @VisibleForTesting
    public static final String ACTION_NETWORK_STATS_POLL =
            "com.android.server.action.NETWORK_STATS_POLL";
    public static final String ACTION_NETWORK_STATS_UPDATED =
            "com.android.server.action.NETWORK_STATS_UPDATED";

    private PendingIntent mPollIntent;

    /**
     * Settings that can be changed externally.
     */
    public interface NetworkStatsSettings {
        long getPollInterval();
        long getPollDelay();
        boolean getSampleEnabled();
        boolean getAugmentEnabled();
        /**
         * When enabled, all mobile data is reported under {@link NetworkTemplate#NETWORK_TYPE_ALL}.
         * When disabled, mobile data is broken down by a granular ratType representative of the
         * actual ratType. See {@link android.app.usage.NetworkStatsManager#getCollapsedRatType}.
         * Enabling this decreases the level of detail but saves performance, disk space and
         * amount of data logged.
         */
        boolean getCombineSubtypeEnabled();

        class Config {
            public final long bucketDuration;
            public final long rotateAgeMillis;
            public final long deleteAgeMillis;

            public Config(long bucketDuration, long rotateAgeMillis, long deleteAgeMillis) {
                this.bucketDuration = bucketDuration;
                this.rotateAgeMillis = rotateAgeMillis;
                this.deleteAgeMillis = deleteAgeMillis;
            }
        }

        Config getXtConfig();
        Config getUidConfig();
        Config getUidTagConfig();

        long getGlobalAlertBytes(long def);
        long getXtPersistBytes(long def);
        long getUidPersistBytes(long def);
        long getUidTagPersistBytes(long def);
    }

    private final Object mStatsLock = new Object();

    /** Set of currently active ifaces. */
    @GuardedBy("mStatsLock")
    private final ArrayMap<String, NetworkIdentitySet> mActiveIfaces = new ArrayMap<>();

    /** Set of currently active ifaces for UID stats. */
    @GuardedBy("mStatsLock")
    private final ArrayMap<String, NetworkIdentitySet> mActiveUidIfaces = new ArrayMap<>();

    /** Current default active iface. */
    @GuardedBy("mStatsLock")
    private String mActiveIface;

    /** Set of all ifaces currently associated with mobile networks. */
    private volatile String[] mMobileIfaces = new String[0];

    /* A set of all interfaces that have ever been associated with mobile networks since boot. */
    @GuardedBy("mStatsLock")
    private final Set<String> mAllMobileIfacesSinceBoot = new ArraySet<>();

    /* A set of all interfaces that have ever been associated with wifi networks since boot. */
    @GuardedBy("mStatsLock")
    private final Set<String> mAllWifiIfacesSinceBoot = new ArraySet<>();

    /** Set of all ifaces currently used by traffic that does not explicitly specify a Network. */
    @GuardedBy("mStatsLock")
    private Network[] mDefaultNetworks = new Network[0];

    /** Last states of all networks sent from ConnectivityService. */
    @GuardedBy("mStatsLock")
    @Nullable
    private NetworkStateSnapshot[] mLastNetworkStateSnapshots = null;

    private final DropBoxNonMonotonicObserver mNonMonotonicObserver =
            new DropBoxNonMonotonicObserver();

    private static final int MAX_STATS_PROVIDER_POLL_WAIT_TIME_MS = 100;
    private final CopyOnWriteArrayList<NetworkStatsProviderCallbackImpl> mStatsProviderCbList =
            new CopyOnWriteArrayList<>();
    /** Semaphore used to wait for stats provider to respond to request stats update. */
    private final Semaphore mStatsProviderSem = new Semaphore(0, true);

    @GuardedBy("mStatsLock")
    private NetworkStatsRecorder mXtRecorder;
    @GuardedBy("mStatsLock")
    private NetworkStatsRecorder mUidRecorder;
    @GuardedBy("mStatsLock")
    private NetworkStatsRecorder mUidTagRecorder;

    /** Cached {@link #mXtRecorder} stats. */
    @GuardedBy("mStatsLock")
    private NetworkStatsCollection mXtStatsCached;

    /**
     * Current counter sets for each UID.
     * TODO: maybe remove mActiveUidCounterSet and read UidCouneterSet value from mUidCounterSetMap
     * directly ? But if mActiveUidCounterSet would be accessed very frequently, maybe keep
     * mActiveUidCounterSet to avoid accessing kernel too frequently.
     */
    private SparseIntArray mActiveUidCounterSet = new SparseIntArray();
    private IBpfMap<S32, U8> mUidCounterSetMap = null;
    private IBpfMap<CookieTagMapKey, CookieTagMapValue> mCookieTagMap = null;
    private IBpfMap<StatsMapKey, StatsMapValue> mStatsMapA = null;
    private IBpfMap<StatsMapKey, StatsMapValue> mStatsMapB = null;
    private IBpfMap<UidStatsMapKey, StatsMapValue> mAppUidStatsMap = null;
    private IBpfMap<S32, StatsMapValue> mIfaceStatsMap = null;

    /** Data layer operation counters for splicing into other structures. */
    private NetworkStats mUidOperations = new NetworkStats(0L, 10);

    @NonNull
    private final Handler mHandler;

    private volatile boolean mSystemReady;
    private long mPersistThreshold = 2 * MB_IN_BYTES;
    private long mGlobalAlertBytes;

    private static final long POLL_RATE_LIMIT_MS = 15_000;

    private long mLastStatsSessionPoll;

    private final Object mOpenSessionCallsLock = new Object();

    /**
     * Map from key {@code OpenSessionKey} to count of opened sessions. This is for recording
     * the caller of open session and it is only for debugging.
     */
    // TODO: Move to NetworkStatsEventLogger to centralize event logging.
    @GuardedBy("mOpenSessionCallsLock")
    private final HashMap<OpenSessionKey, Integer> mOpenSessionCallsPerCaller = new HashMap<>();

    private final static int DUMP_STATS_SESSION_COUNT = 20;

    @NonNull
    private final Dependencies mDeps;

    @NonNull
    private final NetworkStatsSubscriptionsMonitor mNetworkStatsSubscriptionsMonitor;

    @NonNull
    private final LocationPermissionChecker mLocationPermissionChecker;

    @NonNull
    private final BpfInterfaceMapHelper mInterfaceMapHelper;

    @Nullable
    private final SkDestroyListener mSkDestroyListener;

    private static final int MAX_SOCKET_DESTROY_LISTENER_LOGS = 20;

    private static @NonNull Clock getDefaultClock() {
        return new BestClock(ZoneOffset.UTC, SystemClock.currentNetworkTimeClock(),
                Clock.systemUTC());
    }

    /**
     * This class is a key that used in {@code mOpenSessionCallsPerCaller} to identify the count of
     * the caller.
     */
    private static class OpenSessionKey {
        public final int uid;
        @Nullable
        public final String packageName;

        OpenSessionKey(int uid, @Nullable String packageName) {
            this.uid = uid;
            this.packageName = packageName;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("uid=").append(uid).append(",");
            sb.append("package=").append(packageName);
            sb.append("}");
            return sb.toString();
        }

        @Override
        public boolean equals(@NonNull Object o) {
            if (this == o) return true;
            if (o.getClass() != getClass()) return false;

            final OpenSessionKey key = (OpenSessionKey) o;
            return this.uid == key.uid && TextUtils.equals(this.packageName, key.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uid, packageName);
        }
    }

    private final class NetworkStatsHandler extends Handler {
        NetworkStatsHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PERFORM_POLL: {
                    performPoll(FLAG_PERSIST_ALL, maybeCreatePollEvent((int) msg.obj));
                    break;
                }
                case MSG_NOTIFY_NETWORK_STATUS: {
                    synchronized (mStatsLock) {
                        // If no cached states, ignore.
                        if (mLastNetworkStateSnapshots == null) break;
                        handleNotifyNetworkStatus(mDefaultNetworks, mLastNetworkStateSnapshots,
                                mActiveIface, maybeCreatePollEvent((int) msg.obj));
                    }
                    break;
                }
                case MSG_PERFORM_POLL_REGISTER_ALERT: {
                    performPoll(FLAG_PERSIST_NETWORK,
                            maybeCreatePollEvent(POLL_REASON_GLOBAL_ALERT));
                    registerGlobalAlert();
                    break;
                }
                case MSG_BROADCAST_NETWORK_STATS_UPDATED: {
                    final Intent updatedIntent = new Intent(ACTION_NETWORK_STATS_UPDATED);
                    updatedIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                    Bundle opts = null;
                    if (SdkLevel.isAtLeastU()) {
                        try {
                            // This allows us to discard older broadcasts still waiting to
                            // be delivered.
                            opts = BroadcastOptionsShimImpl.newInstance(
                                    BroadcastOptions.makeBasic())
                                    .setDeliveryGroupPolicy(
                                            ConstantsShim.DELIVERY_GROUP_POLICY_MOST_RECENT)
                                    .setDeferralPolicy(
                                            ConstantsShim.DEFERRAL_POLICY_UNTIL_ACTIVE)
                                    .toBundle();
                        } catch (UnsupportedApiLevelException e) {
                            Log.wtf(TAG, "Using unsupported API" + e);
                        }
                    }
                    mContext.sendBroadcastAsUser(updatedIntent, UserHandle.ALL,
                            READ_NETWORK_USAGE_HISTORY, opts);
                    break;
                }
            }
        }
    }

    /** Creates a new NetworkStatsService */
    public static NetworkStatsService create(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        final INetd netd = INetd.Stub.asInterface(
                (IBinder) context.getSystemService(Context.NETD_SERVICE));
        final NetworkStatsService service = new NetworkStatsService(context,
                INetd.Stub.asInterface((IBinder) context.getSystemService(Context.NETD_SERVICE)),
                alarmManager, wakeLock, getDefaultClock(),
                new DefaultNetworkStatsSettings(), new NetworkStatsFactory(context),
                new NetworkStatsObservers(), new Dependencies());

        return service;
    }

    // This must not be called outside of tests, even within the same package, as this constructor
    // does not register the local service. Use the create() helper above.
    @VisibleForTesting
    NetworkStatsService(Context context, INetd netd, AlarmManager alarmManager,
            PowerManager.WakeLock wakeLock, Clock clock, NetworkStatsSettings settings,
            NetworkStatsFactory factory, NetworkStatsObservers statsObservers,
            @NonNull Dependencies deps) {
        mContext = Objects.requireNonNull(context, "missing Context");
        mNetd = Objects.requireNonNull(netd, "missing Netd");
        mAlarmManager = Objects.requireNonNull(alarmManager, "missing AlarmManager");
        mClock = Objects.requireNonNull(clock, "missing Clock");
        mSettings = Objects.requireNonNull(settings, "missing NetworkStatsSettings");
        mWakeLock = Objects.requireNonNull(wakeLock, "missing WakeLock");
        mStatsFactory = Objects.requireNonNull(factory, "missing factory");
        mStatsObservers = Objects.requireNonNull(statsObservers, "missing NetworkStatsObservers");
        mDeps = Objects.requireNonNull(deps, "missing Dependencies");
        mStatsDir = mDeps.getOrCreateStatsDir();
        if (!mStatsDir.exists()) {
            throw new IllegalStateException("Persist data directory does not exist: " + mStatsDir);
        }

        final HandlerThread handlerThread = mDeps.makeHandlerThread();
        handlerThread.start();
        mHandler = new NetworkStatsHandler(handlerThread.getLooper());
        mNetworkStatsSubscriptionsMonitor = deps.makeSubscriptionsMonitor(mContext,
                (command) -> mHandler.post(command) , this);
        mContentResolver = mContext.getContentResolver();
        mContentObserver = mDeps.makeContentObserver(mHandler, mSettings,
                mNetworkStatsSubscriptionsMonitor);
        mLocationPermissionChecker = mDeps.makeLocationPermissionChecker(mContext);
        mInterfaceMapHelper = mDeps.makeBpfInterfaceMapHelper();
        try {
            mUidCounterSetMap = mDeps.getUidCounterSetMap();
            mCookieTagMap = mDeps.getCookieTagMap();
            mStatsMapA = mDeps.getStatsMapA();
            mStatsMapB = mDeps.getStatsMapB();
            mAppUidStatsMap = mDeps.getAppUidStatsMap();
            mIfaceStatsMap = mDeps.getIfaceStatsMap();
        } catch(Throwable t) {
            android.util.Log.e("PHH", "Failed creating bpf maps", t);
        }
        // To prevent any possible races, the flag is not allowed to change until rebooting.
        mSupportEventLogger = mDeps.supportEventLogger(mContext);
        if (mSupportEventLogger) {
            mEventLogger = new NetworkStatsEventLogger();
        } else {
            mEventLogger = null;
        }

        // TODO: Remove bpfNetMaps creation and always start SkDestroyListener
        // Following code is for the experiment to verify the SkDestroyListener refactoring. Based
        // on the experiment flag, BpfNetMaps starts C SkDestroyListener (existing code) or
        // NetworkStatsService starts Java SkDestroyListener (new code).
        final BpfNetMaps bpfNetMaps = mDeps.makeBpfNetMaps(mContext);
        mSkDestroyListener = mDeps.makeSkDestroyListener(mCookieTagMap, mHandler);
        mHandler.post(mSkDestroyListener::start);
    }

    /**
     * Dependencies of NetworkStatsService, for injection in tests.
     */
    // TODO: Move more stuff into dependencies object.
    @VisibleForTesting
    public static class Dependencies {
        /**
         * Get legacy platform stats directory.
         */
        @NonNull
        public File getLegacyStatsDir() {
            final File systemDataDir = new File(Environment.getDataDirectory(), "system");
            return new File(systemDataDir, "netstats");
        }

        /**
         * Get or create the directory that stores the persisted data usage.
         */
        @NonNull
        public File getOrCreateStatsDir() {
            final boolean storeInApexDataDir = getStoreFilesInApexData();

            final File statsDataDir;
            if (storeInApexDataDir) {
                final File apexDataDir = ApexEnvironment
                        .getApexEnvironment(DeviceConfigUtils.TETHERING_MODULE_NAME)
                        .getDeviceProtectedDataDir();
                statsDataDir = new File(apexDataDir, "netstats");

            } else {
                statsDataDir = getLegacyStatsDir();
            }

            if (statsDataDir.exists() || statsDataDir.mkdirs()) {
                return statsDataDir;
            }
            throw new IllegalStateException("Cannot write into stats data directory: "
                    + statsDataDir);
        }

        /**
         * Get the count of import legacy target attempts.
         */
        public int getImportLegacyTargetAttempts() {
            return DeviceConfigUtils.getDeviceConfigPropertyInt(
                    DeviceConfig.NAMESPACE_TETHERING,
                    NETSTATS_IMPORT_LEGACY_TARGET_ATTEMPTS,
                    DEFAULT_NETSTATS_IMPORT_LEGACY_TARGET_ATTEMPTS);
        }

        /**
         * Get the count of using FastDataInput target attempts.
         */
        public int getUseFastDataInputTargetAttempts() {
            return DeviceConfigUtils.getDeviceConfigPropertyInt(
                    DeviceConfig.NAMESPACE_TETHERING,
                    NETSTATS_FASTDATAINPUT_TARGET_ATTEMPTS, 0);
        }

        /**
         * Compare two {@link NetworkStatsCollection} instances and returning a human-readable
         * string description of difference for debugging purpose.
         */
        public String compareStats(@NonNull NetworkStatsCollection a,
                                   @NonNull NetworkStatsCollection b, boolean allowKeyChange) {
            return NetworkStatsCollection.compareStats(a, b, allowKeyChange);
        }

        /**
         * Create a persistent counter for given directory and name.
         */
        public PersistentInt createPersistentCounter(@NonNull Path dir, @NonNull String name)
                throws IOException {
            // TODO: Modify PersistentInt to call setStartTime every time a write is made.
            //  Create and pass a real logger here.
            final String path = dir.resolve(name).toString();
            return new PersistentInt(path, null /* logger */);
        }

        /**
         * Get the flag of storing files in the apex data directory.
         * @return whether to store files in the apex data directory.
         */
        public boolean getStoreFilesInApexData() {
            return DeviceConfigUtils.getDeviceConfigPropertyBoolean(
                    DeviceConfig.NAMESPACE_TETHERING,
                    NETSTATS_STORE_FILES_IN_APEXDATA, true);
        }

        /**
         * Read legacy persisted network stats from disk.
         */
        @NonNull
        public NetworkStatsCollection readPlatformCollection(
                @NonNull String prefix, long bucketDuration) throws IOException {
            return NetworkStatsDataMigrationUtils.readPlatformCollection(prefix, bucketDuration);
        }

        /**
         * Create a HandlerThread to use in NetworkStatsService.
         */
        @NonNull
        public HandlerThread makeHandlerThread() {
            return new HandlerThread(TAG);
        }

        /**
         * Create a {@link NetworkStatsSubscriptionsMonitor}, can be used to monitor RAT change
         * event in NetworkStatsService.
         */
        @NonNull
        public NetworkStatsSubscriptionsMonitor makeSubscriptionsMonitor(@NonNull Context context,
                @NonNull Executor executor, @NonNull NetworkStatsService service) {
            // TODO: Update RatType passively in NSS, instead of querying into the monitor
            //  when notifyNetworkStatus.
            return new NetworkStatsSubscriptionsMonitor(context, executor,
                    (subscriberId, type) -> service.handleOnCollapsedRatTypeChanged());
        }

        /**
         * Create a ContentObserver instance which is used to observe settings changes,
         * and dispatch onChange events on handler thread.
         */
        public @NonNull ContentObserver makeContentObserver(@NonNull Handler handler,
                @NonNull NetworkStatsSettings settings,
                @NonNull NetworkStatsSubscriptionsMonitor monitor) {
            return new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange, @NonNull Uri uri) {
                    if (!settings.getCombineSubtypeEnabled()) {
                        monitor.start();
                    } else {
                        monitor.stop();
                    }
                }
            };
        }

        /**
         * @see LocationPermissionChecker
         */
        public LocationPermissionChecker makeLocationPermissionChecker(final Context context) {
            return new LocationPermissionChecker(context);
        }

        /** Create BpfInterfaceMapHelper to update bpf interface map. */
        @NonNull
        public BpfInterfaceMapHelper makeBpfInterfaceMapHelper() {
            return new BpfInterfaceMapHelper();
        }

        /** Get counter sets map for each UID. */
        public IBpfMap<S32, U8> getUidCounterSetMap() {
            try {
                return new BpfMap<>(UID_COUNTERSET_MAP_PATH, S32.class, U8.class);
            } catch (ErrnoException e) {
                Log.wtf(TAG, "Cannot open uid counter set map: " + e);
                return null;
            }
        }

        /** Gets the cookie tag map */
        public IBpfMap<CookieTagMapKey, CookieTagMapValue> getCookieTagMap() {
            try {
                return new BpfMap<>(COOKIE_TAG_MAP_PATH,
                        CookieTagMapKey.class, CookieTagMapValue.class);
            } catch (ErrnoException e) {
                Log.wtf(TAG, "Cannot open cookie tag map: " + e);
                return null;
            }
        }

        /** Gets stats map A */
        public IBpfMap<StatsMapKey, StatsMapValue> getStatsMapA() {
            try {
                return new BpfMap<>(STATS_MAP_A_PATH, StatsMapKey.class, StatsMapValue.class);
            } catch (ErrnoException e) {
                Log.wtf(TAG, "Cannot open stats map A: " + e);
                return null;
            }
        }

        /** Gets stats map B */
        public IBpfMap<StatsMapKey, StatsMapValue> getStatsMapB() {
            try {
                return new BpfMap<>(STATS_MAP_B_PATH, StatsMapKey.class, StatsMapValue.class);
            } catch (ErrnoException e) {
                Log.wtf(TAG, "Cannot open stats map B: " + e);
                return null;
            }
        }

        /** Gets the uid stats map */
        public IBpfMap<UidStatsMapKey, StatsMapValue> getAppUidStatsMap() {
            try {
                return new BpfMap<>(APP_UID_STATS_MAP_PATH,
                        UidStatsMapKey.class, StatsMapValue.class);
            } catch (ErrnoException e) {
                Log.wtf(TAG, "Cannot open app uid stats map: " + e);
                return null;
            }
        }

        /** Gets interface stats map */
        public IBpfMap<S32, StatsMapValue> getIfaceStatsMap() {
            try {
                return new BpfMap<>(IFACE_STATS_MAP_PATH, S32.class, StatsMapValue.class);
            } catch (ErrnoException e) {
                throw new IllegalStateException("Failed to open interface stats map", e);
            }
        }

        /** Gets whether the build is userdebug. */
        public boolean isDebuggable() {
            return Build.isDebuggable();
        }

        /** Create a new BpfNetMaps. */
        public BpfNetMaps makeBpfNetMaps(Context ctx) {
            return new BpfNetMaps(ctx);
        }

        /** Create a new SkDestroyListener. */
        public SkDestroyListener makeSkDestroyListener(
                IBpfMap<CookieTagMapKey, CookieTagMapValue> cookieTagMap, Handler handler) {
            return new SkDestroyListener(
                    cookieTagMap, handler, new SharedLog(MAX_SOCKET_DESTROY_LISTENER_LOGS, TAG));
        }

        /**
         * Get whether event logger feature is supported.
         */
        public boolean supportEventLogger(Context ctx) {
            return DeviceConfigUtils.isTetheringFeatureNotChickenedOut(
                    ctx, CONFIG_ENABLE_NETWORK_STATS_EVENT_LOGGER);
        }
    }

    /**
     * Observer that watches for {@link INetdUnsolicitedEventListener} alerts.
     */
    @VisibleForTesting
    public class AlertObserver extends BaseNetdUnsolicitedEventListener {
        @Override
        public void onQuotaLimitReached(@NonNull String alertName, @NonNull String ifName) {
            PermissionUtils.enforceNetworkStackPermission(mContext);

            if (LIMIT_GLOBAL_ALERT.equals(alertName)) {
                // kick off background poll to collect network stats unless there is already
                // such a call pending; UID stats are handled during normal polling interval.
                if (!mHandler.hasMessages(MSG_PERFORM_POLL_REGISTER_ALERT)) {
                    mHandler.sendEmptyMessageDelayed(MSG_PERFORM_POLL_REGISTER_ALERT,
                            mSettings.getPollDelay());
                }
            }
        }
    }

    public void systemReady() {
        synchronized (mStatsLock) {
            mSystemReady = true;

            makeRecordersLocked();

            updatePersistThresholdsLocked();

            // upgrade any legacy stats
            maybeUpgradeLegacyStatsLocked();

            // read historical network stats from disk, since policy service
            // might need them right away.
            mXtStatsCached = mXtRecorder.getOrLoadCompleteLocked();

            // bootstrap initial stats to prevent double-counting later
            bootstrapStatsLocked();
        }

        // watch for tethering changes
        final TetheringManager tetheringManager = mContext.getSystemService(TetheringManager.class);
        tetheringManager.registerTetheringEventCallback(
                (command) -> mHandler.post(command), mTetherListener);

        // listen for periodic polling events
        final IntentFilter pollFilter = new IntentFilter(ACTION_NETWORK_STATS_POLL);
        mContext.registerReceiver(mPollReceiver, pollFilter, READ_NETWORK_USAGE_HISTORY, mHandler);

        // listen for uid removal to clean stats
        final IntentFilter removedFilter = new IntentFilter(ACTION_UID_REMOVED);
        mContext.registerReceiver(mRemovedReceiver, removedFilter, null, mHandler);

        // listen for user changes to clean stats
        final IntentFilter userFilter = new IntentFilter(ACTION_USER_REMOVED);
        mContext.registerReceiver(mUserReceiver, userFilter, null, mHandler);

        // persist stats during clean shutdown
        final IntentFilter shutdownFilter = new IntentFilter(ACTION_SHUTDOWN);
        mContext.registerReceiver(mShutdownReceiver, shutdownFilter);

        try {
            mNetd.registerUnsolicitedEventListener(mAlertObserver);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.wtf(TAG, "Error registering event listener :", e);
        }

        //  schedule periodic pall alarm based on {@link NetworkStatsSettings#getPollInterval()}.
        final PendingIntent pollIntent =
                PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_NETWORK_STATS_POLL),
                        PendingIntent.FLAG_IMMUTABLE);

        final long currentRealtime = SystemClock.elapsedRealtime();
        mAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, currentRealtime,
                mSettings.getPollInterval(), pollIntent);

        mContentResolver.registerContentObserver(Settings.Global
                .getUriFor(NETSTATS_COMBINE_SUBTYPE_ENABLED),
                        false /* notifyForDescendants */, mContentObserver);

        // Post a runnable on handler thread to call onChange(). It's for getting current value of
        // NETSTATS_COMBINE_SUBTYPE_ENABLED to decide start or stop monitoring RAT type changes.
        mHandler.post(() -> mContentObserver.onChange(false, Settings.Global
                .getUriFor(NETSTATS_COMBINE_SUBTYPE_ENABLED)));

        registerGlobalAlert();
    }

    private NetworkStatsRecorder buildRecorder(
            String prefix, NetworkStatsSettings.Config config, boolean includeTags,
            File baseDir, boolean wipeOnError, boolean useFastDataInput) {
        final DropBoxManager dropBox = (DropBoxManager) mContext.getSystemService(
                Context.DROPBOX_SERVICE);
        return new NetworkStatsRecorder(new FileRotator(
                baseDir, prefix, config.rotateAgeMillis, config.deleteAgeMillis),
                mNonMonotonicObserver, dropBox, prefix, config.bucketDuration, includeTags,
                wipeOnError, useFastDataInput, baseDir);
    }

    @GuardedBy("mStatsLock")
    private void makeRecordersLocked() {
        boolean useFastDataInput = true;
        try {
            mFastDataInputSuccessesCounter = mDeps.createPersistentCounter(mStatsDir.toPath(),
                    NETSTATS_FASTDATAINPUT_SUCCESSES_COUNTER_NAME);
            mFastDataInputFallbacksCounter = mDeps.createPersistentCounter(mStatsDir.toPath(),
                    NETSTATS_FASTDATAINPUT_FALLBACKS_COUNTER_NAME);
        } catch (IOException e) {
            Log.wtf(TAG, "Failed to create persistent counters, skip.", e);
            useFastDataInput = false;
        }

        final int targetAttempts = mDeps.getUseFastDataInputTargetAttempts();
        int successes = 0;
        int fallbacks = 0;
        try {
            successes = mFastDataInputSuccessesCounter.get();
            // Fallbacks counter would be set to non-zero value to indicate the reading was
            // not successful.
            fallbacks = mFastDataInputFallbacksCounter.get();
        } catch (IOException e) {
            Log.wtf(TAG, "Failed to read counters, skip.", e);
            useFastDataInput = false;
        }

        final boolean doComparison;
        if (useFastDataInput) {
            // Use FastDataInput if it needs to be evaluated or at least one success.
            doComparison = targetAttempts > successes + fallbacks;
            // Set target attempt to -1 as the kill switch to disable the feature.
            useFastDataInput = targetAttempts >= 0 && (doComparison || successes > 0);
        } else {
            // useFastDataInput is false due to previous failures.
            doComparison = false;
        }

        // create data recorders along with historical rotators.
        // Don't wipe on error if comparison is needed.
        mXtRecorder = buildRecorder(PREFIX_XT, mSettings.getXtConfig(), false, mStatsDir,
                !doComparison /* wipeOnError */, useFastDataInput);
        mUidRecorder = buildRecorder(PREFIX_UID, mSettings.getUidConfig(), false, mStatsDir,
                !doComparison /* wipeOnError */, useFastDataInput);
        mUidTagRecorder = buildRecorder(PREFIX_UID_TAG, mSettings.getUidTagConfig(), true,
                mStatsDir, !doComparison /* wipeOnError */, useFastDataInput);

        if (!doComparison) return;

        final MigrationInfo[] migrations = new MigrationInfo[]{
                new MigrationInfo(mXtRecorder),
                new MigrationInfo(mUidRecorder),
                new MigrationInfo(mUidTagRecorder)
        };
        // Set wipeOnError flag false so the recorder won't damage persistent data if reads
        // failed and calling deleteAll.
        final NetworkStatsRecorder[] legacyRecorders = new NetworkStatsRecorder[]{
                buildRecorder(PREFIX_XT, mSettings.getXtConfig(), false, mStatsDir,
                        false /* wipeOnError */, false /* useFastDataInput */),
                buildRecorder(PREFIX_UID, mSettings.getUidConfig(), false, mStatsDir,
                        false /* wipeOnError */, false /* useFastDataInput */),
                buildRecorder(PREFIX_UID_TAG, mSettings.getUidTagConfig(), true, mStatsDir,
                        false /* wipeOnError */, false /* useFastDataInput */)};
        boolean success = true;
        for (int i = 0; i < migrations.length; i++) {
            try {
                migrations[i].collection = migrations[i].recorder.getOrLoadCompleteLocked();
            } catch (Throwable t) {
                Log.wtf(TAG, "Failed to load collection, skip.", t);
                success = false;
                break;
            }
            if (!compareImportedToLegacyStats(migrations[i], legacyRecorders[i],
                    false /* allowKeyChange */)) {
                success = false;
                break;
            }
        }

        try {
            if (success) {
                mFastDataInputSuccessesCounter.set(successes + 1);
            } else {
                // Fallback.
                mXtRecorder = legacyRecorders[0];
                mUidRecorder = legacyRecorders[1];
                mUidTagRecorder = legacyRecorders[2];
                mFastDataInputFallbacksCounter.set(fallbacks + 1);
            }
        } catch (IOException e) {
            Log.wtf(TAG, "Failed to update counters. success = " + success, e);
        }
    }

    @GuardedBy("mStatsLock")
    private void shutdownLocked() {
        final TetheringManager tetheringManager = mContext.getSystemService(TetheringManager.class);
        try {
            tetheringManager.unregisterTetheringEventCallback(mTetherListener);
        } catch (IllegalStateException e) {
            Log.i(TAG, "shutdownLocked: error when unregister tethering, ignored. e=" + e);
        }
        mContext.unregisterReceiver(mPollReceiver);
        mContext.unregisterReceiver(mRemovedReceiver);
        mContext.unregisterReceiver(mUserReceiver);
        mContext.unregisterReceiver(mShutdownReceiver);

        if (!mSettings.getCombineSubtypeEnabled()) {
            mNetworkStatsSubscriptionsMonitor.stop();
        }

        mContentResolver.unregisterContentObserver(mContentObserver);

        final long currentTime = mClock.millis();

        // persist any pending stats
        mXtRecorder.forcePersistLocked(currentTime);
        mUidRecorder.forcePersistLocked(currentTime);
        mUidTagRecorder.forcePersistLocked(currentTime);

        mSystemReady = false;
    }

    private static class MigrationInfo {
        public final NetworkStatsRecorder recorder;
        public NetworkStatsCollection collection;
        public boolean imported;
        MigrationInfo(@NonNull final NetworkStatsRecorder recorder) {
            this.recorder = recorder;
            collection = null;
            imported = false;
        }
    }

    @GuardedBy("mStatsLock")
    private void maybeUpgradeLegacyStatsLocked() {
        final boolean storeFilesInApexData = mDeps.getStoreFilesInApexData();
        if (!storeFilesInApexData) {
            return;
        }
        try {
            mImportLegacyAttemptsCounter = mDeps.createPersistentCounter(mStatsDir.toPath(),
                    NETSTATS_IMPORT_ATTEMPTS_COUNTER_NAME);
            mImportLegacySuccessesCounter = mDeps.createPersistentCounter(mStatsDir.toPath(),
                    NETSTATS_IMPORT_SUCCESSES_COUNTER_NAME);
            mImportLegacyFallbacksCounter = mDeps.createPersistentCounter(mStatsDir.toPath(),
                    NETSTATS_IMPORT_FALLBACKS_COUNTER_NAME);
        } catch (IOException e) {
            Log.wtf(TAG, "Failed to create persistent counters, skip.", e);
            return;
        }

        final int targetAttempts = mDeps.getImportLegacyTargetAttempts();
        final int attempts;
        final int fallbacks;
        final boolean runComparison;
        try {
            attempts = mImportLegacyAttemptsCounter.get();
            // Fallbacks counter would be set to non-zero value to indicate the migration was
            // not successful.
            fallbacks = mImportLegacyFallbacksCounter.get();
            runComparison = shouldRunComparison();
        } catch (IOException e) {
            Log.wtf(TAG, "Failed to read counters, skip.", e);
            return;
        }

        // If the target number of attempts are reached, don't import any data.
        // However, if comparison is requested, still read the legacy data and compare
        // it to the importer output. This allows OEMs to debug issues with the
        // importer code and to collect signals from the field.
        final boolean dryRunImportOnly =
                fallbacks != 0 && runComparison && (attempts >= targetAttempts);
        // Return if target attempts are reached and there is no need to dry run.
        if (attempts >= targetAttempts && !dryRunImportOnly) return;

        if (dryRunImportOnly) {
            Log.i(TAG, "Starting import : only perform read");
        } else {
            Log.i(TAG, "Starting import : attempts " + attempts + "/" + targetAttempts);
        }

        // Still create a legacy dev recorder locally but the service doesn't really use it.
        // This is for backward compatibility where the OEMs might call readPlatformCollection to
        // perform proprietary operations and relying on the side-effects to complete the follow-up
        // import process.
        final NetworkStatsSettings.Config devConfig =
                new NetworkStatsSettings.Config(HOUR_IN_MILLIS,
                15 * DAY_IN_MILLIS, 90 * DAY_IN_MILLIS);
        final NetworkStatsRecorder devRecorder = buildRecorder(PREFIX_DEV, devConfig,
                false, mStatsDir, true /* wipeOnError */, false /* useFastDataInput */);
        final MigrationInfo[] migrations = new MigrationInfo[]{
                new MigrationInfo(devRecorder), new MigrationInfo(mXtRecorder),
                new MigrationInfo(mUidRecorder), new MigrationInfo(mUidTagRecorder)
        };

        // Legacy directories will be created by recorders if they do not exist
        final NetworkStatsRecorder[] legacyRecorders;
        if (runComparison) {
            final File legacyBaseDir = mDeps.getLegacyStatsDir();
            // Set wipeOnError flag false so the recorder won't damage persistent data if reads
            // failed and calling deleteAll.
            // Set DEV legacy recorder as null since the DEV recorder has been removed.
            // Thus it doesn't need to build DEV legacy recorder for comparing with imported data.
            legacyRecorders = new NetworkStatsRecorder[]{
                null /* dev Recorder */,
                buildRecorder(PREFIX_XT, mSettings.getXtConfig(), false, legacyBaseDir,
                        false /* wipeOnError */, false /* useFastDataInput */),
                buildRecorder(PREFIX_UID, mSettings.getUidConfig(), false, legacyBaseDir,
                        false /* wipeOnError */, false /* useFastDataInput */),
                buildRecorder(PREFIX_UID_TAG, mSettings.getUidTagConfig(), true, legacyBaseDir,
                        false /* wipeOnError */, false /* useFastDataInput */)};
        } else {
            legacyRecorders = null;
        }

        long migrationEndTime = Long.MIN_VALUE;
        try {
            // First, read all legacy collections. This is OEM code and it can throw. Don't
            // commit any data to disk until all are read.
            for (int i = 0; i < migrations.length; i++) {
                final MigrationInfo migration = migrations[i];
                // Read the collection from platform code, and set fallbacks counter if throws
                // for better debugging.
                try {
                    migration.collection = readPlatformCollectionForRecorder(migration.recorder);
                } catch (Throwable e) {
                    if (dryRunImportOnly) {
                        Log.wtf(TAG, "Platform data read failed. ", e);
                        return;
                    } else {
                        // Data is not imported successfully, set fallbacks counter to non-zero
                        // value to trigger dry run every later boot when the runComparison is
                        // true, in order to make it easier to debug issues.
                        tryIncrementLegacyFallbacksCounter();
                        // Re-throw for error handling. This will increase attempts counter.
                        throw e;
                    }
                }

                if (runComparison) {
                    final boolean success =
                            compareImportedToLegacyStats(migration, legacyRecorders[i],
                                    true /* allowKeyChange */);
                    if (!success && !dryRunImportOnly) {
                        tryIncrementLegacyFallbacksCounter();
                    }
                }
            }

            // For cases where the fallbacks are not zero but target attempts counts reached,
            // only perform reads above and return here.
            if (dryRunImportOnly) return;

            // Find the latest end time.
            for (final MigrationInfo migration : migrations) {
                if (PREFIX_DEV.equals(migration.recorder.getCookie())) continue;
                final long migrationEnd = migration.collection.getEndMillis();
                if (migrationEnd > migrationEndTime) migrationEndTime = migrationEnd;
            }

            // Reading all collections from legacy data has succeeded. At this point it is
            // safe to start overwriting the files on disk. The next step is to remove all
            // data in the new location that overlaps with imported data. This ensures that
            // any data in the new location that was created by a previous failed import is
            // ignored. After that, write the imported data into the recorder. The code
            // below can still possibly throw (disk error or OutOfMemory for example), but
            // does not depend on code from non-mainline code.
            Log.i(TAG, "Rewriting data with imported collections with cutoff "
                    + Instant.ofEpochMilli(migrationEndTime));
            for (final MigrationInfo migration : migrations) {
                migration.imported = true;
                migration.recorder.removeDataBefore(migrationEndTime);
                if (migration.collection.isEmpty()
                        || PREFIX_DEV.equals(migration.recorder.getCookie())) continue;
                migration.recorder.importCollectionLocked(migration.collection);
            }

            // Success normally or uses fallback method.
        } catch (Throwable e) {
            // The code above calls OEM code that may behave differently across devices.
            // It can throw any exception including RuntimeExceptions and
            // OutOfMemoryErrors. Try to recover anyway.
            Log.wtf(TAG, "Platform data import failed. Remaining tries "
                    + (targetAttempts - attempts), e);

            // Failed this time around : try again next time unless we're out of tries.
            try {
                mImportLegacyAttemptsCounter.set(attempts + 1);
            } catch (IOException ex) {
                Log.wtf(TAG, "Failed to update attempts counter.", ex);
            }

            // Try to remove any data from the failed import.
            if (migrationEndTime > Long.MIN_VALUE) {
                try {
                    for (final MigrationInfo migration : migrations) {
                        if (PREFIX_DEV.equals(migration.recorder.getCookie())) continue;
                        if (migration.imported) {
                            migration.recorder.removeDataBefore(migrationEndTime);
                        }
                    }
                } catch (Throwable f) {
                    // If rollback still throws, there isn't much left to do. Try nuking
                    // all data, since that's the last stop. If nuking still throws, the
                    // framework will reboot, and if there are remaining tries, the migration
                    // process will retry, which is fine because it's idempotent.
                    for (final MigrationInfo migration : migrations) {
                        if (PREFIX_DEV.equals(migration.recorder.getCookie())) continue;
                        migration.recorder.recoverAndDeleteData();
                    }
                }
            }

            return;
        }

        // Success ! No need to import again next time.
        try {
            mImportLegacyAttemptsCounter.set(targetAttempts);
            Log.i(TAG, "Successfully imported platform collections");
            // The successes counter is only for debugging. Hence, the synchronization
            // between successes counter and attempts counter are not very critical.
            final int successCount = mImportLegacySuccessesCounter.get();
            mImportLegacySuccessesCounter.set(successCount + 1);
        } catch (IOException e) {
            Log.wtf(TAG, "Succeed but failed to update counters.", e);
        }
    }

    void tryIncrementLegacyFallbacksCounter() {
        try {
            final int fallbacks = mImportLegacyFallbacksCounter.get();
            mImportLegacyFallbacksCounter.set(fallbacks + 1);
        } catch (IOException e) {
            Log.wtf(TAG, "Failed to update fallback counter.", e);
        }
    }

    @VisibleForTesting
    boolean shouldRunComparison() {
        final ConnectivityResources resources = new ConnectivityResources(mContext);
        // 0 if id not found.
        Boolean overlayValue = null;
        try {
            switch (resources.get().getInteger(R.integer.config_netstats_validate_import)) {
                case 1:
                    overlayValue = Boolean.TRUE;
                    break;
                case 0:
                    overlayValue = Boolean.FALSE;
                    break;
            }
        } catch (Resources.NotFoundException e) {
            // Overlay value is not defined.
        }
        return overlayValue != null ? overlayValue : mDeps.isDebuggable();
    }

    /**
     * Compare imported data with the data returned by legacy recorders.
     *
     * @return true if the data matches or if {@code legacyRecorder} is null, false if the data
     * does not match or throw with exceptions.
     */
    private boolean compareImportedToLegacyStats(@NonNull MigrationInfo migration,
            @Nullable NetworkStatsRecorder legacyRecorder, boolean allowKeyChange) {
        final NetworkStatsCollection legacyStats;
        // Skip the recorder that doesn't need to be compared.
        if (legacyRecorder == null) return true;
        try {
            legacyStats = legacyRecorder.getOrLoadCompleteLocked();
        } catch (Throwable e) {
            Log.wtf(TAG, "Failed to read stats with legacy method for recorder "
                    + legacyRecorder.getCookie(), e);
            // Cannot read data from legacy method, skip comparison.
            return false;
        }

        // The result of comparison is only for logging.
        try {
            final String error = mDeps.compareStats(migration.collection, legacyStats,
                    allowKeyChange);
            if (error != null) {
                Log.wtf(TAG, "Unexpected comparison result for recorder "
                        + legacyRecorder.getCookie() + ": " + error);
                return false;
            }
        } catch (Throwable e) {
            Log.wtf(TAG, "Failed to compare migrated stats with legacy stats for recorder "
                    + legacyRecorder.getCookie(), e);
            return false;
        }
        return true;
    }

    @GuardedBy("mStatsLock")
    @NonNull
    private NetworkStatsCollection readPlatformCollectionForRecorder(
            @NonNull final NetworkStatsRecorder rec) throws IOException {
        final String prefix = rec.getCookie();
        Log.i(TAG, "Importing platform collection for prefix " + prefix);
        final NetworkStatsCollection collection = Objects.requireNonNull(
                mDeps.readPlatformCollection(prefix, rec.getBucketDuration()),
                "Imported platform collection for prefix " + prefix + " must not be null");

        final long bootTimestamp = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (!collection.isEmpty() && bootTimestamp < collection.getStartMillis()) {
            throw new IllegalArgumentException("Platform collection for prefix " + prefix
                    + " contains data that could not possibly come from the previous boot "
                    + "(start timestamp = " + Instant.ofEpochMilli(collection.getStartMillis())
                    + ", last booted at " + Instant.ofEpochMilli(bootTimestamp));
        }

        Log.i(TAG, "Successfully read platform collection spanning from "
                // Instant uses ISO-8601 for toString()
                + Instant.ofEpochMilli(collection.getStartMillis()).toString() + " to "
                + Instant.ofEpochMilli(collection.getEndMillis()).toString());
        return collection;
    }

    /**
     * Register for a global alert that is delivered through {@link AlertObserver}
     * or {@link NetworkStatsProviderCallback#onAlertReached()} once a threshold amount of data has
     * been transferred.
     */
    private void registerGlobalAlert() {
        try {
            mNetd.bandwidthSetGlobalAlert(mGlobalAlertBytes);
        } catch (IllegalStateException e) {
            Log.w(TAG, "problem registering for global alert: " + e);
        } catch (RemoteException | ServiceSpecificException e) {
            // ignored; service lives in system_server
        }
        invokeForAllStatsProviderCallbacks((cb) -> cb.mProvider.onSetAlert(mGlobalAlertBytes));
    }

    @Override
    public INetworkStatsSession openSession() {
        return openSessionInternal(NetworkStatsManager.FLAG_AUGMENT_WITH_SUBSCRIPTION_PLAN, null);
    }

    @Override
    public INetworkStatsSession openSessionForUsageStats(
            int flags, @NonNull String callingPackage) {
        Objects.requireNonNull(callingPackage);
        PermissionUtils.enforcePackageNameMatchesUid(
                mContext, Binder.getCallingUid(), callingPackage);
        return openSessionInternal(flags, callingPackage);
    }

    private boolean isRateLimitedForPoll(@NonNull OpenSessionKey key) {
        final long lastCallTime;
        final long now = SystemClock.elapsedRealtime();

        synchronized (mOpenSessionCallsLock) {
            Integer callsPerCaller = mOpenSessionCallsPerCaller.get(key);
            if (callsPerCaller == null) {
                mOpenSessionCallsPerCaller.put((key), 1);
            } else {
                mOpenSessionCallsPerCaller.put(key, Integer.sum(callsPerCaller, 1));
            }

            if (key.uid == android.os.Process.SYSTEM_UID) {
                return false;
            }

            // To avoid a non-system user to be rate-limited after system users open sessions,
            // so update mLastStatsSessionPoll after checked if the uid is SYSTEM_UID.
            lastCallTime = mLastStatsSessionPoll;
            mLastStatsSessionPoll = now;
        }

        return now - lastCallTime < POLL_RATE_LIMIT_MS;
    }

    private int restrictFlagsForCaller(int flags, @Nullable String callingPackage) {
        // All non-privileged callers are not allowed to turn off POLL_ON_OPEN.
        final boolean isPrivileged = PermissionUtils.hasAnyPermissionOf(mContext,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                android.Manifest.permission.NETWORK_STACK);
        if (!isPrivileged) {
            flags |= NetworkStatsManager.FLAG_POLL_ON_OPEN;
        }
        // Non-system uids are rate limited for POLL_ON_OPEN.
        final int callingUid = Binder.getCallingUid();
        final OpenSessionKey key = new OpenSessionKey(callingUid, callingPackage);
        flags = isRateLimitedForPoll(key)
                ? flags & (~NetworkStatsManager.FLAG_POLL_ON_OPEN)
                : flags;
        return flags;
    }

    private INetworkStatsSession openSessionInternal(
            final int flags, @Nullable final String callingPackage) {
        final int restrictedFlags = restrictFlagsForCaller(flags, callingPackage);
        if ((restrictedFlags & (NetworkStatsManager.FLAG_POLL_ON_OPEN
                | NetworkStatsManager.FLAG_POLL_FORCE)) != 0) {
            final long ident = Binder.clearCallingIdentity();
            try {
                performPoll(FLAG_PERSIST_ALL, maybeCreatePollEvent(POLL_REASON_OPEN_SESSION));
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        // return an IBinder which holds strong references to any loaded stats
        // for its lifetime; when caller closes only weak references remain.

        return new INetworkStatsSession.Stub() {
            private final int mCallingUid = Binder.getCallingUid();
            @Nullable
            private final String mCallingPackage = callingPackage;
            private final @NetworkStatsAccess.Level int mAccessLevel = checkAccessLevel(
                    callingPackage);

            private NetworkStatsCollection mUidComplete;
            private NetworkStatsCollection mUidTagComplete;

            private NetworkStatsCollection getUidComplete() {
                synchronized (mStatsLock) {
                    if (mUidComplete == null) {
                        mUidComplete = mUidRecorder.getOrLoadCompleteLocked();
                    }
                    return mUidComplete;
                }
            }

            private NetworkStatsCollection getUidTagComplete() {
                synchronized (mStatsLock) {
                    if (mUidTagComplete == null) {
                        mUidTagComplete = mUidTagRecorder.getOrLoadCompleteLocked();
                    }
                    return mUidTagComplete;
                }
            }

            @Override
            public int[] getRelevantUids() {
                return getUidComplete().getRelevantUids(mAccessLevel);
            }

            @Override
            public NetworkStats getDeviceSummaryForNetwork(
                    NetworkTemplate template, long start, long end) {
                enforceTemplatePermissions(template, callingPackage);
                return internalGetSummaryForNetwork(template, restrictedFlags, start, end,
                        mAccessLevel, mCallingUid);
            }

            @Override
            public NetworkStats getSummaryForNetwork(
                    NetworkTemplate template, long start, long end) {
                enforceTemplatePermissions(template, callingPackage);
                return internalGetSummaryForNetwork(template, restrictedFlags, start, end,
                        mAccessLevel, mCallingUid);
            }

            // TODO: Remove this after all callers are removed.
            @Override
            public NetworkStatsHistory getHistoryForNetwork(NetworkTemplate template, int fields) {
                enforceTemplatePermissions(template, callingPackage);
                return internalGetHistoryForNetwork(template, restrictedFlags, fields,
                        mAccessLevel, mCallingUid, Long.MIN_VALUE, Long.MAX_VALUE);
            }

            @Override
            public NetworkStatsHistory getHistoryIntervalForNetwork(NetworkTemplate template,
                    int fields, long start, long end) {
                enforceTemplatePermissions(template, callingPackage);
                // TODO(b/200768422): Redact returned history if the template is location
                //  sensitive but the caller is not privileged.
                return internalGetHistoryForNetwork(template, restrictedFlags, fields,
                        mAccessLevel, mCallingUid, start, end);
            }

            @Override
            public NetworkStats getSummaryForAllUid(
                    NetworkTemplate template, long start, long end, boolean includeTags) {
                enforceTemplatePermissions(template, callingPackage);
                try {
                    final NetworkStats stats = getUidComplete()
                            .getSummary(template, start, end, mAccessLevel, mCallingUid);
                    if (includeTags) {
                        final NetworkStats tagStats = getUidTagComplete()
                                .getSummary(template, start, end, mAccessLevel, mCallingUid);
                        stats.combineAllValues(tagStats);
                    }
                    return stats;
                } catch (NullPointerException e) {
                    throw e;
                }
            }

            @Override
            public NetworkStats getTaggedSummaryForAllUid(
                    NetworkTemplate template, long start, long end) {
                enforceTemplatePermissions(template, callingPackage);
                try {
                    final NetworkStats tagStats = getUidTagComplete()
                            .getSummary(template, start, end, mAccessLevel, mCallingUid);
                    return tagStats;
                } catch (NullPointerException e) {
                    throw e;
                }
            }

            @Override
            public NetworkStatsHistory getHistoryForUid(
                    NetworkTemplate template, int uid, int set, int tag, int fields) {
                enforceTemplatePermissions(template, callingPackage);
                // NOTE: We don't augment UID-level statistics
                if (tag == TAG_NONE) {
                    return getUidComplete().getHistory(template, null, uid, set, tag, fields,
                            Long.MIN_VALUE, Long.MAX_VALUE, mAccessLevel, mCallingUid);
                } else {
                    return getUidTagComplete().getHistory(template, null, uid, set, tag, fields,
                            Long.MIN_VALUE, Long.MAX_VALUE, mAccessLevel, mCallingUid);
                }
            }

            @Override
            public NetworkStatsHistory getHistoryIntervalForUid(
                    NetworkTemplate template, int uid, int set, int tag, int fields,
                    long start, long end) {
                enforceTemplatePermissions(template, callingPackage);
                // TODO(b/200768422): Redact returned history if the template is location
                //  sensitive but the caller is not privileged.
                // NOTE: We don't augment UID-level statistics
                if (tag == TAG_NONE) {
                    return getUidComplete().getHistory(template, null, uid, set, tag, fields,
                            start, end, mAccessLevel, mCallingUid);
                } else if (uid == Binder.getCallingUid()) {
                    return getUidTagComplete().getHistory(template, null, uid, set, tag, fields,
                            start, end, mAccessLevel, mCallingUid);
                } else {
                    throw new SecurityException("Calling package " + mCallingPackage
                            + " cannot access tag information from a different uid");
                }
            }

            @Override
            public void close() {
                mUidComplete = null;
                mUidTagComplete = null;
            }
        };
    }

    private void enforceTemplatePermissions(@NonNull NetworkTemplate template,
            @Nullable String callingPackage) {
        // For a template with wifi network keys, it is possible for a malicious
        // client to track the user locations via querying data usage. Thus, enforce
        // fine location permission check.
        // For a template with MATCH_TEST, since the wifi network key is just a placeholder
        // to identify a specific test network, it is not related to track user location.
        if (!template.getWifiNetworkKeys().isEmpty() && template.getMatchRule() != MATCH_TEST) {
            final boolean canAccessFineLocation = mLocationPermissionChecker
                    .checkCallersLocationPermission(callingPackage,
                    null /* featureId */,
                            Binder.getCallingUid(),
                            false /* coarseForTargetSdkLessThanQ */,
                            null /* message */);
            if (!canAccessFineLocation) {
                throw new SecurityException("Access fine location is required when querying"
                        + " with wifi network keys, make sure the app has the necessary"
                        + "permissions and the location toggle is on.");
            }
        }
    }

    private @NetworkStatsAccess.Level int checkAccessLevel(@Nullable String callingPackage) {
        return NetworkStatsAccess.checkAccessLevel(
                mContext, Binder.getCallingPid(), Binder.getCallingUid(), callingPackage);
    }

    /**
     * Find the most relevant {@link SubscriptionPlan} for the given
     * {@link NetworkTemplate} and flags. This is typically used to augment
     * local measurement results to match a known anchor from the carrier.
     */
    private SubscriptionPlan resolveSubscriptionPlan(NetworkTemplate template, int flags) {
        SubscriptionPlan plan = null;
        if ((flags & NetworkStatsManager.FLAG_AUGMENT_WITH_SUBSCRIPTION_PLAN) != 0
                && mSettings.getAugmentEnabled()) {
            if (LOGD) Log.d(TAG, "Resolving plan for " + template);
            final long token = Binder.clearCallingIdentity();
            try {
                plan = mContext.getSystemService(NetworkPolicyManager.class)
                        .getSubscriptionPlan(template);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            if (LOGD) Log.d(TAG, "Resolved to plan " + plan);
        }
        return plan;
    }

    /**
     * Return network summary, splicing between DEV and XT stats when
     * appropriate.
     */
    private NetworkStats internalGetSummaryForNetwork(NetworkTemplate template, int flags,
            long start, long end, @NetworkStatsAccess.Level int accessLevel, int callingUid) {
        // We've been using pure XT stats long enough that we no longer need to
        // splice DEV and XT together.
        final NetworkStatsHistory history = internalGetHistoryForNetwork(template, flags, FIELD_ALL,
                accessLevel, callingUid, Long.MIN_VALUE, Long.MAX_VALUE);

        final long now = mClock.millis();
        final NetworkStatsHistory.Entry entry = history.getValues(start, end, now, null);

        final NetworkStats stats = new NetworkStats(end - start, 1);
        stats.insertEntry(new NetworkStats.Entry(IFACE_ALL, UID_ALL, SET_ALL, TAG_NONE,
                METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, entry.rxBytes, entry.rxPackets,
                entry.txBytes, entry.txPackets, entry.operations));
        return stats;
    }

    /**
     * Return network history, splicing between DEV and XT stats when
     * appropriate.
     */
    private NetworkStatsHistory internalGetHistoryForNetwork(NetworkTemplate template,
            int flags, int fields, @NetworkStatsAccess.Level int accessLevel, int callingUid,
            long start, long end) {
        // We've been using pure XT stats long enough that we no longer need to
        // splice DEV and XT together.
        final SubscriptionPlan augmentPlan = resolveSubscriptionPlan(template, flags);
        synchronized (mStatsLock) {
            return mXtStatsCached.getHistory(template, augmentPlan,
                    UID_ALL, SET_ALL, TAG_NONE, fields, start, end, accessLevel, callingUid);
        }
    }

    private long getNetworkTotalBytes(NetworkTemplate template, long start, long end) {
        assertSystemReady();

        return internalGetSummaryForNetwork(template,
                NetworkStatsManager.FLAG_AUGMENT_WITH_SUBSCRIPTION_PLAN, start, end,
                NetworkStatsAccess.Level.DEVICE, Binder.getCallingUid()).getTotalBytes();
    }

    private NetworkStats getNetworkUidBytes(NetworkTemplate template, long start, long end) {
        assertSystemReady();

        final NetworkStatsCollection uidComplete;
        synchronized (mStatsLock) {
            uidComplete = mUidRecorder.getOrLoadCompleteLocked();
        }
        return uidComplete.getSummary(template, start, end, NetworkStatsAccess.Level.DEVICE,
                android.os.Process.SYSTEM_UID);
    }

    @Override
    public NetworkStats getDataLayerSnapshotForUid(int uid) throws RemoteException {
        if (Binder.getCallingUid() != uid) {
            Log.w(TAG, "Snapshots only available for calling UID");
            return new NetworkStats(SystemClock.elapsedRealtime(), 0);
        }

        // TODO: switch to data layer stats once kernel exports
        // for now, read network layer stats and flatten across all ifaces.
        // This function is used to query NeworkStats for calle's uid. The only caller method
        // TrafficStats#getDataLayerSnapshotForUid alrady claim no special permission to query
        // its own NetworkStats.
        final long ident = Binder.clearCallingIdentity();
        final NetworkStats networkLayer;
        try {
            networkLayer = readNetworkStatsUidDetail(uid, INTERFACES_ALL, TAG_ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        // splice in operation counts
        networkLayer.spliceOperationsFrom(mUidOperations);

        final NetworkStats dataLayer = new NetworkStats(
                networkLayer.getElapsedRealtime(), networkLayer.size());

        NetworkStats.Entry entry = null;
        for (int i = 0; i < networkLayer.size(); i++) {
            entry = networkLayer.getValues(i, entry);
            entry.iface = IFACE_ALL;
            dataLayer.combineValues(entry);
        }

        return dataLayer;
    }

    private String[] getAllIfacesSinceBoot(int transport) {
        synchronized (mStatsLock) {
            final Set<String> ifaceSet;
            if (transport == TRANSPORT_WIFI) {
                ifaceSet = mAllWifiIfacesSinceBoot;
            } else if (transport == TRANSPORT_CELLULAR) {
                ifaceSet = mAllMobileIfacesSinceBoot;
            } else {
                throw new IllegalArgumentException("Invalid transport " + transport);
            }

            return ifaceSet.toArray(new String[0]);
        }
    }

    @Override
    public NetworkStats getUidStatsForTransport(int transport) {
        PermissionUtils.enforceNetworkStackPermission(mContext);
        try {
            final String[] ifaceArray = getAllIfacesSinceBoot(transport);
            final NetworkStats stats = getNetworkStatsUidDetail(ifaceArray);
            // Clear the interfaces of the stats before returning, so callers won't get this
            // information. This is because no caller needs this information for now, and it
            // makes it easier to change the implementation later by using the histories in the
            // recorder.
            return stats.withoutInterfaces();
        } catch (RemoteException e) {
            Log.wtf(TAG, "Error compiling UID stats", e);
            return new NetworkStats(0L, 0);
        }
    }

    @Override
    public String[] getMobileIfaces() {
        return mMobileIfaces.clone();
    }

    @Override
    public void incrementOperationCount(int uid, int tag, int operationCount) {
        if (Binder.getCallingUid() != uid) {
            mContext.enforceCallingOrSelfPermission(UPDATE_DEVICE_STATS, TAG);
        }

        if (operationCount < 0) {
            throw new IllegalArgumentException("operation count can only be incremented");
        }
        if (tag == TAG_NONE) {
            throw new IllegalArgumentException("operation count must have specific tag");
        }

        synchronized (mStatsLock) {
            final int set = mActiveUidCounterSet.get(uid, SET_DEFAULT);
            mUidOperations.combineValues(
                    mActiveIface, uid, set, tag, 0L, 0L, 0L, 0L, operationCount);
            mUidOperations.combineValues(
                    mActiveIface, uid, set, TAG_NONE, 0L, 0L, 0L, 0L, operationCount);
        }
    }

    private void setKernelCounterSet(int uid, int set) {
        if (mUidCounterSetMap == null) {
            Log.wtf(TAG, "Fail to set UidCounterSet: Null bpf map");
            return;
        }

        if (set == SET_DEFAULT) {
            try {
                mUidCounterSetMap.deleteEntry(new S32(uid));
            } catch (ErrnoException e) {
                Log.w(TAG, "UidCounterSetMap.deleteEntry(" + uid + ") failed with errno: " + e);
            }
            return;
        }

        try {
            mUidCounterSetMap.updateEntry(new S32(uid), new U8((short) set));
        } catch (ErrnoException e) {
            Log.w(TAG, "UidCounterSetMap.updateEntry(" + uid + ", " + set
                    + ") failed with errno: " + e);
        }
    }

    @VisibleForTesting
    public void noteUidForeground(int uid, boolean uidForeground) {
        PermissionUtils.enforceNetworkStackPermission(mContext);
        synchronized (mStatsLock) {
            final int set = uidForeground ? SET_FOREGROUND : SET_DEFAULT;
            final int oldSet = mActiveUidCounterSet.get(uid, SET_DEFAULT);
            if (oldSet != set) {
                mActiveUidCounterSet.put(uid, set);
                setKernelCounterSet(uid, set);
            }
        }
    }

    /**
     * Notify {@code NetworkStatsService} about network status changed.
     */
    public void notifyNetworkStatus(
            @NonNull Network[] defaultNetworks,
            @NonNull NetworkStateSnapshot[] networkStates,
            @Nullable String activeIface,
            @NonNull UnderlyingNetworkInfo[] underlyingNetworkInfos) {
        PermissionUtils.enforceNetworkStackPermission(mContext);

        final long token = Binder.clearCallingIdentity();
        try {
            handleNotifyNetworkStatus(defaultNetworks, networkStates, activeIface,
                    maybeCreatePollEvent(POLL_REASON_NETWORK_STATUS_CHANGED));
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        // Update the VPN underlying interfaces only after the poll is made and tun data has been
        // migrated. Otherwise the migration would use the new interfaces instead of the ones that
        // were current when the polled data was transferred.
        mStatsFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);
    }

    @Override
    public void forceUpdate() {
        PermissionUtils.enforceNetworkStackPermission(mContext);

        final long token = Binder.clearCallingIdentity();
        try {
            // TODO: Log callstack for system server callers.
            performPoll(FLAG_PERSIST_ALL, maybeCreatePollEvent(POLL_REASON_FORCE_UPDATE));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** Advise persistence threshold; may be overridden internally. */
    public void advisePersistThreshold(long thresholdBytes) {
        PermissionUtils.enforceNetworkStackPermission(mContext);
        // clamp threshold into safe range
        mPersistThreshold = NetworkStatsUtils.constrain(thresholdBytes,
                128 * KB_IN_BYTES, 2 * MB_IN_BYTES);
        if (LOGV) {
            Log.v(TAG, "advisePersistThreshold() given " + thresholdBytes + ", clamped to "
                    + mPersistThreshold);
        }

        final long oldGlobalAlertBytes = mGlobalAlertBytes;

        // update and persist if beyond new thresholds
        final long currentTime = mClock.millis();
        synchronized (mStatsLock) {
            if (!mSystemReady) return;

            updatePersistThresholdsLocked();

            mXtRecorder.maybePersistLocked(currentTime);
            mUidRecorder.maybePersistLocked(currentTime);
            mUidTagRecorder.maybePersistLocked(currentTime);
        }

        if (oldGlobalAlertBytes != mGlobalAlertBytes) {
            registerGlobalAlert();
        }
    }

    @Override
    public DataUsageRequest registerUsageCallback(@NonNull String callingPackage,
                @NonNull DataUsageRequest request, @NonNull IUsageCallback callback) {
        Objects.requireNonNull(callingPackage, "calling package is null");
        Objects.requireNonNull(request, "DataUsageRequest is null");
        Objects.requireNonNull(request.template, "NetworkTemplate is null");
        Objects.requireNonNull(callback, "callback is null");

        final int callingPid = Binder.getCallingPid();
        final int callingUid = Binder.getCallingUid();
        PermissionUtils.enforcePackageNameMatchesUid(mContext, callingUid, callingPackage);
        @NetworkStatsAccess.Level int accessLevel = checkAccessLevel(callingPackage);
        DataUsageRequest normalizedRequest;
        final long token = Binder.clearCallingIdentity();
        try {
            normalizedRequest = mStatsObservers.register(mContext,
                    request, callback, callingPid, callingUid, callingPackage, accessLevel);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        // Create baseline stats
        mHandler.sendMessage(mHandler.obtainMessage(MSG_PERFORM_POLL,
                POLL_REASON_REG_CALLBACK));

        return normalizedRequest;
   }

    @Override
    public void unregisterUsageRequest(DataUsageRequest request) {
        Objects.requireNonNull(request, "DataUsageRequest is null");

        int callingUid = Binder.getCallingUid();
        final long token = Binder.clearCallingIdentity();
        try {
            mStatsObservers.unregister(request, callingUid);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public long getUidStats(int uid, int type) {
        final int callingUid = Binder.getCallingUid();
        if (callingUid != android.os.Process.SYSTEM_UID && callingUid != uid) {
            return UNSUPPORTED;
        }
        return getEntryValueForType(nativeGetUidStat(uid), type);
    }

    @Override
    public long getIfaceStats(@NonNull String iface, int type) {
        Objects.requireNonNull(iface);
        final NetworkStats.Entry entry = nativeGetIfaceStat(iface);
        final long value = getEntryValueForType(entry, type);
        if (value == UNSUPPORTED) {
            return UNSUPPORTED;
        } else {
            // When tethering offload is in use, nativeIfaceStats does not contain usage from
            // offload, add it back here. Note that the included statistics might be stale
            // since polling newest stats from hardware might impact system health and not
            // suitable for TrafficStats API use cases.
            entry.add(getProviderIfaceStats(iface));
            return getEntryValueForType(entry, type);
        }
    }

    private long getEntryValueForType(@Nullable NetworkStats.Entry entry, int type) {
        if (entry == null) return UNSUPPORTED;
        switch (type) {
            case TrafficStats.TYPE_RX_BYTES:
                return entry.rxBytes;
            case TrafficStats.TYPE_TX_BYTES:
                return entry.txBytes;
            case TrafficStats.TYPE_RX_PACKETS:
                return entry.rxPackets;
            case TrafficStats.TYPE_TX_PACKETS:
                return entry.txPackets;
            default:
                return UNSUPPORTED;
        }
    }

    @Override
    public long getTotalStats(int type) {
        final NetworkStats.Entry entry = nativeGetTotalStat();
        final long value = getEntryValueForType(entry, type);
        if (value == UNSUPPORTED) {
            return UNSUPPORTED;
        } else {
            // Refer to comment in getIfaceStats
            entry.add(getProviderIfaceStats(IFACE_ALL));
            return getEntryValueForType(entry, type);
        }
    }

    private NetworkStats.Entry getProviderIfaceStats(@Nullable String iface) {
        final NetworkStats providerSnapshot = getNetworkStatsFromProviders(STATS_PER_IFACE);
        final HashSet<String> limitIfaces;
        if (iface == IFACE_ALL) {
            limitIfaces = null;
        } else {
            limitIfaces = new HashSet<>();
            limitIfaces.add(iface);
        }
        return providerSnapshot.getTotal(null, limitIfaces);
    }

    /**
     * Update {@link NetworkStatsRecorder} and {@link #mGlobalAlertBytes} to
     * reflect current {@link #mPersistThreshold} value. Always defers to
     * {@link Global} values when defined.
     */
    @GuardedBy("mStatsLock")
    private void updatePersistThresholdsLocked() {
        mXtRecorder.setPersistThreshold(mSettings.getXtPersistBytes(mPersistThreshold));
        mUidRecorder.setPersistThreshold(mSettings.getUidPersistBytes(mPersistThreshold));
        mUidTagRecorder.setPersistThreshold(mSettings.getUidTagPersistBytes(mPersistThreshold));
        mGlobalAlertBytes = mSettings.getGlobalAlertBytes(mPersistThreshold);
    }

    /**
     * Listener that watches for {@link TetheringManager} to claim interface pairs.
     */
    private final TetheringManager.TetheringEventCallback mTetherListener =
            new TetheringManager.TetheringEventCallback() {
                @Override
                public void onUpstreamChanged(@Nullable Network network) {
                    performPoll(FLAG_PERSIST_NETWORK,
                            maybeCreatePollEvent(POLL_REASON_UPSTREAM_CHANGED));
                }
            };

    private BroadcastReceiver mPollReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and verified UPDATE_DEVICE_STATS
            // permission above.
            performPoll(FLAG_PERSIST_ALL, maybeCreatePollEvent(POLL_REASON_PERIODIC));

            // verify that we're watching global alert
            registerGlobalAlert();
        }
    };

    private BroadcastReceiver mRemovedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and UID_REMOVED is protected
            // broadcast.

            final int uid = intent.getIntExtra(EXTRA_UID, -1);
            if (uid == -1) return;

            synchronized (mStatsLock) {
                mWakeLock.acquire();
                try {
                    removeUidsLocked(uid);
                } finally {
                    mWakeLock.release();
                }
            }
        }
    };

    private BroadcastReceiver mUserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // On background handler thread, and USER_REMOVED is protected
            // broadcast.

            final UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
            if (userHandle == null) return;

            synchronized (mStatsLock) {
                mWakeLock.acquire();
                try {
                    removeUserLocked(userHandle);
                } finally {
                    mWakeLock.release();
                }
            }
        }
    };

    private BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // SHUTDOWN is protected broadcast.
            synchronized (mStatsLock) {
                shutdownLocked();
            }
        }
    };

    /**
     * Handle collapsed RAT type changed event.
     */
    @VisibleForTesting
    public void handleOnCollapsedRatTypeChanged() {
        // Protect service from frequently updating. Remove pending messages if any.
        mHandler.removeMessages(MSG_NOTIFY_NETWORK_STATUS);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_NOTIFY_NETWORK_STATUS,
                        POLL_REASON_RAT_CHANGED), mSettings.getPollDelay());
    }

    private void handleNotifyNetworkStatus(
            Network[] defaultNetworks,
            NetworkStateSnapshot[] snapshots,
            String activeIface,
            @Nullable PollEvent event) {
        synchronized (mStatsLock) {
            mWakeLock.acquire();
            try {
                mActiveIface = activeIface;
                handleNotifyNetworkStatusLocked(defaultNetworks, snapshots, event);
            } finally {
                mWakeLock.release();
            }
        }
    }

    /**
     * Inspect all current {@link NetworkStateSnapshot}s to derive mapping from {@code iface} to
     * {@link NetworkStatsHistory}. When multiple networks are active on a single {@code iface},
     * they are combined under a single {@link NetworkIdentitySet}.
     */
    @GuardedBy("mStatsLock")
    private void handleNotifyNetworkStatusLocked(@NonNull Network[] defaultNetworks,
            @NonNull NetworkStateSnapshot[] snapshots, @Nullable PollEvent event) {
        if (!mSystemReady) return;
        if (LOGV) Log.v(TAG, "handleNotifyNetworkStatusLocked()");

        // take one last stats snapshot before updating iface mapping. this
        // isn't perfect, since the kernel may already be counting traffic from
        // the updated network.

        // poll, but only persist network stats to keep codepath fast. UID stats
        // will be persisted during next alarm poll event.
        performPollLocked(FLAG_PERSIST_NETWORK, event);

        // Rebuild active interfaces based on connected networks
        mActiveIfaces.clear();
        mActiveUidIfaces.clear();
        // Update the list of default networks.
        mDefaultNetworks = defaultNetworks;

        mLastNetworkStateSnapshots = snapshots;

        final boolean combineSubtypeEnabled = mSettings.getCombineSubtypeEnabled();
        final ArraySet<String> mobileIfaces = new ArraySet<>();
        for (NetworkStateSnapshot snapshot : snapshots) {
            final int displayTransport =
                    getDisplayTransport(snapshot.getNetworkCapabilities().getTransportTypes());
            final boolean isMobile = (NetworkCapabilities.TRANSPORT_CELLULAR == displayTransport);
            final boolean isWifi = (NetworkCapabilities.TRANSPORT_WIFI == displayTransport);
            final boolean isDefault = CollectionUtils.contains(
                    mDefaultNetworks, snapshot.getNetwork());
            final int ratType = combineSubtypeEnabled ? NetworkTemplate.NETWORK_TYPE_ALL
                    : getRatTypeForStateSnapshot(snapshot);
            final NetworkIdentity ident = NetworkIdentity.buildNetworkIdentity(mContext, snapshot,
                    isDefault, ratType);

            // If WifiInfo contains a null network key then this identity should not be added into
            // the network identity set. See b/266598304.
            final TransportInfo transportInfo = snapshot.getNetworkCapabilities()
                    .getTransportInfo();
            if (transportInfo instanceof WifiInfo) {
                final WifiInfo info = (WifiInfo) transportInfo;
                if (info.getNetworkKey() == null) continue;
            }
            // Traffic occurring on the base interface is always counted for
            // both total usage and UID details.
            final String baseIface = snapshot.getLinkProperties().getInterfaceName();
            if (baseIface != null) {
                nativeRegisterIface(baseIface);
                findOrCreateNetworkIdentitySet(mActiveIfaces, baseIface).add(ident);
                findOrCreateNetworkIdentitySet(mActiveUidIfaces, baseIface).add(ident);

                // Build a separate virtual interface for VT (Video Telephony) data usage.
                // Only do this when IMS is not metered, but VT is metered.
                // If IMS is metered, then the IMS network usage has already included VT usage.
                // VT is considered always metered in framework's layer. If VT is not metered
                // per carrier's policy, modem will report 0 usage for VT calls.
                if (snapshot.getNetworkCapabilities().hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_IMS) && !ident.isMetered()) {

                    // Copy the identify from IMS one but mark it as metered.
                    NetworkIdentity vtIdent = new NetworkIdentity.Builder()
                            .setType(ident.getType())
                            .setRatType(ident.getRatType())
                            .setSubscriberId(ident.getSubscriberId())
                            .setWifiNetworkKey(ident.getWifiNetworkKey())
                            .setRoaming(ident.isRoaming()).setMetered(true)
                            .setDefaultNetwork(true)
                            .setOemManaged(ident.getOemManaged())
                            .setSubId(ident.getSubId()).build();
                    final String ifaceVt = IFACE_VT + getSubIdForCellularOrSatellite(snapshot);
                    findOrCreateNetworkIdentitySet(mActiveIfaces, ifaceVt).add(vtIdent);
                    findOrCreateNetworkIdentitySet(mActiveUidIfaces, ifaceVt).add(vtIdent);
                }

                if (isMobile) {
                    mobileIfaces.add(baseIface);
                    // If the interface name was present in the wifi set, the interface won't
                    // be removed from it to prevent stats from getting rollback.
                    mAllMobileIfacesSinceBoot.add(baseIface);
                }
                if (isWifi) {
                    mAllWifiIfacesSinceBoot.add(baseIface);
                }
            }

            // Traffic occurring on stacked interfaces is usually clatd.
            //
            // UID stats are always counted on the stacked interface and never on the base
            // interface, because the packets on the base interface do not actually match
            // application sockets (they're not IPv4) and thus the app uid is not known.
            // For receive this is obvious: packets must be translated from IPv6 to IPv4
            // before the application socket can be found.
            // For transmit: either they go through the clat daemon which by virtue of going
            // through userspace strips the original socket association during the IPv4 to
            // IPv6 translation process, or they are offloaded by eBPF, which doesn't:
            // However, on an ebpf device the accounting is done in cgroup ebpf hooks,
            // which don't trigger again post ebpf translation.
            // (as such stats accounted to the clat uid are ignored)
            //
            // Interface stats are more complicated.
            //
            // eBPF offloaded 464xlat'ed packets never hit base interface ip6tables, and thus
            // *all* statistics are collected by iptables on the stacked v4-* interface.
            //
            // Additionally for ingress all packets bound for the clat IPv6 address are dropped
            // in ip6tables raw prerouting and thus even non-offloaded packets are only
            // accounted for on the stacked interface.
            //
            // For egress, packets subject to eBPF offload never appear on the base interface
            // and only appear on the stacked interface. Thus to ensure packets increment
            // interface stats, we must collate data from stacked interfaces. For xt_qtaguid
            // (or non eBPF offloaded) TX they would appear on both, however egress interface
            // accounting is explicitly bypassed for traffic from the clat uid.
            //
            // TODO: This code might be combined to above code.
            for (String iface : snapshot.getLinkProperties().getAllInterfaceNames()) {
                // baseIface has been handled, so ignore it.
                if (TextUtils.equals(baseIface, iface)) continue;
                if (iface != null) {
                    nativeRegisterIface(iface);
                    findOrCreateNetworkIdentitySet(mActiveIfaces, iface).add(ident);
                    findOrCreateNetworkIdentitySet(mActiveUidIfaces, iface).add(ident);
                    if (isMobile) {
                        mobileIfaces.add(iface);
                        mAllMobileIfacesSinceBoot.add(iface);
                    }
                    if (isWifi) {
                        mAllWifiIfacesSinceBoot.add(iface);
                    }

                    mStatsFactory.noteStackedIface(iface, baseIface);
                }
            }
        }

        mMobileIfaces = mobileIfaces.toArray(new String[0]);
    }

    private static int getSubIdForCellularOrSatellite(@NonNull NetworkStateSnapshot state) {
        if (!state.getNetworkCapabilities().hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                // Both cellular and satellite are 2 different network transport at Mobile using
                // same telephony network specifier. So adding satellite transport to consider
                // for, when satellite network is active at mobile.
                && !state.getNetworkCapabilities().hasTransport(
                NetworkCapabilities.TRANSPORT_SATELLITE)) {
            throw new IllegalArgumentException(
                    "Mobile state need capability TRANSPORT_CELLULAR or TRANSPORT_SATELLITE");
        }

        final NetworkSpecifier spec = state.getNetworkCapabilities().getNetworkSpecifier();
        if (spec instanceof TelephonyNetworkSpecifier) {
             return ((TelephonyNetworkSpecifier) spec).getSubscriptionId();
        } else {
            Log.wtf(TAG, "getSubIdForState invalid NetworkSpecifier");
            return INVALID_SUBSCRIPTION_ID;
        }
    }

    /**
     * For networks with {@code TRANSPORT_CELLULAR}, get ratType that was obtained through
     * {@link PhoneStateListener}. Otherwise, return 0 given that other networks with different
     * transport types do not actually fill this value.
     */
    private int getRatTypeForStateSnapshot(@NonNull NetworkStateSnapshot state) {
        if (!state.getNetworkCapabilities().hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return 0;
        }

        return mNetworkStatsSubscriptionsMonitor.getRatTypeForSubscriberId(state.getSubscriberId());
    }

    private static <K> NetworkIdentitySet findOrCreateNetworkIdentitySet(
            ArrayMap<K, NetworkIdentitySet> map, K key) {
        NetworkIdentitySet ident = map.get(key);
        if (ident == null) {
            ident = new NetworkIdentitySet();
            map.put(key, ident);
        }
        return ident;
    }

    @GuardedBy("mStatsLock")
    private void recordSnapshotLocked(long currentTime) throws RemoteException {
        // snapshot and record current counters; read UID stats first to
        // avoid over counting xt stats.
        Trace.traceBegin(TRACE_TAG_NETWORK, "snapshotUid");
        final NetworkStats uidSnapshot = getNetworkStatsUidDetail(INTERFACES_ALL);
        Trace.traceEnd(TRACE_TAG_NETWORK);
        Trace.traceBegin(TRACE_TAG_NETWORK, "snapshotXt");
        final NetworkStats xtSnapshot = readNetworkStatsSummaryXt();
        Trace.traceEnd(TRACE_TAG_NETWORK);

        // Snapshot for xt stats from all custom stats providers. Counts per-interface data
        // from stats providers that isn't already counted by XT stats.
        Trace.traceBegin(TRACE_TAG_NETWORK, "snapshotStatsProvider");
        final NetworkStats providersnapshot = getNetworkStatsFromProviders(STATS_PER_IFACE);
        Trace.traceEnd(TRACE_TAG_NETWORK);
        xtSnapshot.combineAllValues(providersnapshot);

        // For xt, we pass a null VPN array because usage is aggregated by UID, so VPN traffic
        // can't be reattributed to responsible apps.
        Trace.traceBegin(TRACE_TAG_NETWORK, "recordXt");
        mXtRecorder.recordSnapshotLocked(xtSnapshot, mActiveIfaces, currentTime);
        Trace.traceEnd(TRACE_TAG_NETWORK);

        // For per-UID stats, pass the VPN info so VPN traffic is reattributed to responsible apps.
        Trace.traceBegin(TRACE_TAG_NETWORK, "recordUid");
        mUidRecorder.recordSnapshotLocked(uidSnapshot, mActiveUidIfaces, currentTime);
        Trace.traceEnd(TRACE_TAG_NETWORK);
        Trace.traceBegin(TRACE_TAG_NETWORK, "recordUidTag");
        mUidTagRecorder.recordSnapshotLocked(uidSnapshot, mActiveUidIfaces, currentTime);
        Trace.traceEnd(TRACE_TAG_NETWORK);

        // We need to make copies of member fields that are sent to the observer to avoid
        // a race condition between the service handler thread and the observer's
        mStatsObservers.updateStats(xtSnapshot, uidSnapshot, new ArrayMap<>(mActiveIfaces),
                new ArrayMap<>(mActiveUidIfaces), currentTime);
    }

    /**
     * Bootstrap initial stats snapshot, usually during {@link #systemReady()}
     * so we have baseline values without double-counting.
     */
    @GuardedBy("mStatsLock")
    private void bootstrapStatsLocked() {
        final long currentTime = mClock.millis();

        try {
            recordSnapshotLocked(currentTime);
        } catch (IllegalStateException e) {
            Log.w(TAG, "problem reading network stats: " + e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private void performPoll(int flags, @Nullable PollEvent event) {
        synchronized (mStatsLock) {
            mWakeLock.acquire();

            try {
                performPollLocked(flags, event);
            } finally {
                mWakeLock.release();
            }
        }
    }

    /**
     * Periodic poll operation, reading current statistics and recording into
     * {@link NetworkStatsHistory}.
     */
    @GuardedBy("mStatsLock")
    private void performPollLocked(int flags, @Nullable PollEvent event) {
        if (!mSystemReady) return;
        if (LOGV) Log.v(TAG, "performPollLocked(flags=0x" + Integer.toHexString(flags) + ")");
        Trace.traceBegin(TRACE_TAG_NETWORK, "performPollLocked");

        if (mSupportEventLogger) {
            mEventLogger.logPollEvent(flags, event);
        }

        final boolean persistNetwork = (flags & FLAG_PERSIST_NETWORK) != 0;
        final boolean persistUid = (flags & FLAG_PERSIST_UID) != 0;
        final boolean persistForce = (flags & FLAG_PERSIST_FORCE) != 0;

        performPollFromProvidersLocked();

        // TODO: consider marking "untrusted" times in historical stats
        final long currentTime = mClock.millis();

        try {
            recordSnapshotLocked(currentTime);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem reading network stats", e);
            return;
        } catch (RemoteException e) {
            // ignored; service lives in system_server
            return;
        }

        // persist any pending data depending on requested flags
        Trace.traceBegin(TRACE_TAG_NETWORK, "[persisting]");
        if (persistForce) {
            mXtRecorder.forcePersistLocked(currentTime);
            mUidRecorder.forcePersistLocked(currentTime);
            mUidTagRecorder.forcePersistLocked(currentTime);
        } else {
            if (persistNetwork) {
                mXtRecorder.maybePersistLocked(currentTime);
            }
            if (persistUid) {
                mUidRecorder.maybePersistLocked(currentTime);
                mUidTagRecorder.maybePersistLocked(currentTime);
            }
        }
        Trace.traceEnd(TRACE_TAG_NETWORK);

        if (mSettings.getSampleEnabled()) {
            // sample stats after each full poll
            performSampleLocked();
        }

        // finally, dispatch updated event to any listeners
        mHandler.sendMessage(mHandler.obtainMessage(MSG_BROADCAST_NETWORK_STATS_UPDATED));

        Trace.traceEnd(TRACE_TAG_NETWORK);
    }

    @GuardedBy("mStatsLock")
    private void performPollFromProvidersLocked() {
        // Request asynchronous stats update from all providers for next poll. And wait a bit of
        // time to allow providers report-in given that normally binder call should be fast. Note
        // that size of list might be changed because addition/removing at the same time. For
        // addition, the stats of the missed provider can only be collected in next poll;
        // for removal, wait might take up to MAX_STATS_PROVIDER_POLL_WAIT_TIME_MS
        // once that happened.
        // TODO: request with a valid token.
        Trace.traceBegin(TRACE_TAG_NETWORK, "provider.requestStatsUpdate");
        final int registeredCallbackCount = mStatsProviderCbList.size();
        mStatsProviderSem.drainPermits();
        invokeForAllStatsProviderCallbacks(
                (cb) -> cb.mProvider.onRequestStatsUpdate(0 /* unused */));
        try {
            mStatsProviderSem.tryAcquire(registeredCallbackCount,
                    MAX_STATS_PROVIDER_POLL_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Strictly speaking it's possible a provider happened to deliver between the timeout
            // and the log, and that doesn't matter too much as this is just a debug log.
            Log.d(TAG, "requestStatsUpdate - providers responded "
                    + mStatsProviderSem.availablePermits()
                    + "/" + registeredCallbackCount + " : " + e);
        }
        Trace.traceEnd(TRACE_TAG_NETWORK);
    }

    /**
     * Sample recent statistics summary into {@link EventLog}.
     */
    @GuardedBy("mStatsLock")
    private void performSampleLocked() {
        // TODO: migrate trustedtime fixes to separate binary log events
        final long currentTime = mClock.millis();

        NetworkTemplate template;
        NetworkStats.Entry xtTotal;
        NetworkStats.Entry uidTotal;

        // collect mobile sample
        template = new NetworkTemplate.Builder(MATCH_MOBILE).setMeteredness(METERED_YES).build();
        xtTotal = mXtRecorder.getTotalSinceBootLocked(template);
        uidTotal = mUidRecorder.getTotalSinceBootLocked(template);

        if (SdkLevel.isAtLeastU()) {
            EventLog.writeEvent(LOG_TAG_NETSTATS_MOBILE_SAMPLE,
                    xtTotal.rxBytes, xtTotal.txBytes, xtTotal.rxPackets, xtTotal.txPackets,
                    uidTotal.rxBytes, uidTotal.txBytes, uidTotal.rxPackets, uidTotal.txPackets,
                    currentTime);
        } else {
            // To keep the format of event log, here replaces the value of DevRecorder with the
            // value of XtRecorder because they have the same content in old design.
            EventLog.writeEvent(LOG_TAG_NETSTATS_MOBILE_SAMPLE,
                    xtTotal.rxBytes, xtTotal.rxPackets, xtTotal.txBytes, xtTotal.txPackets,
                    xtTotal.rxBytes, xtTotal.rxPackets, xtTotal.txBytes, xtTotal.txPackets,
                    uidTotal.rxBytes, uidTotal.rxPackets, uidTotal.txBytes, uidTotal.txPackets,
                    currentTime);
        }

        // collect wifi sample
        template = new NetworkTemplate.Builder(MATCH_WIFI).build();
        xtTotal = mXtRecorder.getTotalSinceBootLocked(template);
        uidTotal = mUidRecorder.getTotalSinceBootLocked(template);

        if (SdkLevel.isAtLeastU()) {
            EventLog.writeEvent(LOG_TAG_NETSTATS_WIFI_SAMPLE,
                    xtTotal.rxBytes, xtTotal.txBytes, xtTotal.rxPackets, xtTotal.txPackets,
                    uidTotal.rxBytes, uidTotal.txBytes, uidTotal.rxPackets, uidTotal.txPackets,
                    currentTime);
        } else {
            // To keep the format of event log, here replaces the value of DevRecorder with the
            // value of XtRecorder because they have the same content in old design.
            EventLog.writeEvent(LOG_TAG_NETSTATS_WIFI_SAMPLE,
                    xtTotal.rxBytes, xtTotal.rxPackets, xtTotal.txBytes, xtTotal.txPackets,
                    xtTotal.rxBytes, xtTotal.rxPackets, xtTotal.txBytes, xtTotal.txPackets,
                    uidTotal.rxBytes, uidTotal.rxPackets, uidTotal.txBytes, uidTotal.txPackets,
                    currentTime);

        }
    }

    // deleteKernelTagData can ignore ENOENT; otherwise we should log an error
    private void logErrorIfNotErrNoent(final ErrnoException e, final String msg) {
        if (e.errno != ENOENT) Log.e(TAG, msg, e);
    }

    private <K extends StatsMapKey, V extends StatsMapValue> void deleteStatsMapTagData(
            IBpfMap<K, V> statsMap, int uid) {
        try {
            statsMap.forEach((key, value) -> {
                if (key.uid == uid) {
                    try {
                        statsMap.deleteEntry(key);
                    } catch (ErrnoException e) {
                        logErrorIfNotErrNoent(e, "Failed to delete data(uid = " + key.uid + ")");
                    }
                }
            });
        } catch (ErrnoException e) {
            Log.e(TAG, "FAILED to delete tag data from stats map", e);
        }
    }

    /**
     * Deletes uid tag data from CookieTagMap, StatsMapA, StatsMapB, and UidStatsMap
     * @param uid
     */
    private void deleteKernelTagData(int uid) {
        if(mCookieTagMap == null) return;
        try {
            mCookieTagMap.forEach((key, value) -> {
                // If SkDestroyListener deletes the socket tag while this code is running,
                // forEach will either restart iteration from the beginning or return null,
                // depending on when the deletion happens.
                // If it returns null, continue iteration to delete the data and in fact it would
                // just iterate from first key because BpfMap#getNextKey would return first key
                // if the current key is not exist.
                if (value != null && value.uid == uid) {
                    try {
                        mCookieTagMap.deleteEntry(key);
                    } catch (ErrnoException e) {
                        logErrorIfNotErrNoent(e, "Failed to delete data(cookie = " + key + ")");
                    }
                }
            });
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to delete tag data from cookie tag map", e);
        }

        deleteStatsMapTagData(mStatsMapA, uid);
        deleteStatsMapTagData(mStatsMapB, uid);

        try {
            mUidCounterSetMap.deleteEntry(new S32(uid));
        } catch (ErrnoException e) {
            logErrorIfNotErrNoent(e, "Failed to delete tag data from uid counter set map");
        }

        try {
            mAppUidStatsMap.deleteEntry(new UidStatsMapKey(uid));
        } catch (ErrnoException e) {
            logErrorIfNotErrNoent(e, "Failed to delete tag data from app uid stats map");
        }
    }

    /**
     * Clean up {@link #mUidRecorder} after UID is removed.
     */
    @GuardedBy("mStatsLock")
    private void removeUidsLocked(int... uids) {
        if (LOGV) Log.v(TAG, "removeUidsLocked() for UIDs " + Arrays.toString(uids));

        // Perform one last poll before removing
        performPollLocked(FLAG_PERSIST_ALL, maybeCreatePollEvent(POLL_REASON_REMOVE_UIDS));

        mUidRecorder.removeUidsLocked(uids);
        mUidTagRecorder.removeUidsLocked(uids);

        mStatsFactory.removeUidsLocked(uids);
        // Clear kernel stats associated with UID
        for (int uid : uids) {
            deleteKernelTagData(uid);
        }
        // TODO: Remove the UID's entries from mOpenSessionCallsPerCaller.
    }

    /**
     * Clean up {@link #mUidRecorder} after user is removed.
     */
    @GuardedBy("mStatsLock")
    private void removeUserLocked(@NonNull UserHandle userHandle) {
        if (LOGV) Log.v(TAG, "removeUserLocked() for UserHandle=" + userHandle);

        // Build list of UIDs that we should clean up
        final ArrayList<Integer> uids = new ArrayList<>();
        final List<ApplicationInfo> apps = mContext.getPackageManager().getInstalledApplications(
                PackageManager.MATCH_ANY_USER
                | PackageManager.MATCH_DISABLED_COMPONENTS);
        for (ApplicationInfo app : apps) {
            final int uid = userHandle.getUid(app.uid);
            uids.add(uid);
        }

        removeUidsLocked(CollectionUtils.toIntArray(uids));
    }

    /**
     * Set the warning and limit to all registered custom network stats providers.
     * Note that invocation of any interface will be sent to all providers.
     */
    public void setStatsProviderWarningAndLimitAsync(
            @NonNull String iface, long warning, long limit) {
        PermissionUtils.enforceNetworkStackPermission(mContext);
        if (LOGV) {
            Log.v(TAG, "setStatsProviderWarningAndLimitAsync("
                    + iface + "," + warning + "," + limit + ")");
        }
        invokeForAllStatsProviderCallbacks((cb) -> cb.mProvider.onSetWarningAndLimit(iface,
                warning, limit));
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter rawWriter, String[] args) {
        if (!PermissionUtils.hasDumpPermission(mContext, TAG, rawWriter)) return;

        long duration = DateUtils.DAY_IN_MILLIS;
        final HashSet<String> argSet = new HashSet<String>();
        for (String arg : args) {
            argSet.add(arg);

            if (arg.startsWith("--duration=")) {
                try {
                    duration = Long.parseLong(arg.substring(11));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // usage: dumpsys netstats --full --uid --tag --poll --checkin
        final boolean poll = argSet.contains("--poll") || argSet.contains("poll");
        final boolean checkin = argSet.contains("--checkin");
        final boolean bpfRawMap = argSet.contains("--bpfRawMap");
        final boolean fullHistory = argSet.contains("--full") || argSet.contains("full");
        final boolean includeUid = argSet.contains("--uid") || argSet.contains("detail");
        final boolean includeTag = argSet.contains("--tag") || argSet.contains("detail");

        final IndentingPrintWriter pw = new IndentingPrintWriter(rawWriter, "  ");

        synchronized (mStatsLock) {
            if (args.length > 0 && "--proto".equals(args[0])) {
                // In this case ignore all other arguments.
                dumpProtoLocked(fd);
                return;
            }

            if (poll) {
                performPollLocked(FLAG_PERSIST_ALL | FLAG_PERSIST_FORCE,
                        maybeCreatePollEvent(POLL_REASON_DUMPSYS));
                pw.println("Forced poll");
                return;
            }

            if (checkin) {
                final long end = System.currentTimeMillis();
                final long start = end - duration;

                pw.print("v1,");
                pw.print(start / SECOND_IN_MILLIS); pw.print(',');
                pw.print(end / SECOND_IN_MILLIS); pw.println();

                pw.println("xt");
                mXtRecorder.dumpCheckin(rawWriter, start, end);

                if (includeUid) {
                    pw.println("uid");
                    mUidRecorder.dumpCheckin(rawWriter, start, end);
                }
                if (includeTag) {
                    pw.println("tag");
                    mUidTagRecorder.dumpCheckin(rawWriter, start, end);
                }
                return;
            }

            if (bpfRawMap) {
                dumpRawMapLocked(pw, args);
                return;
            }

            pw.println("Directory:");
            pw.increaseIndent();
            pw.println(mStatsDir);
            pw.decreaseIndent();

            pw.println("Configs:");
            pw.increaseIndent();
            pw.print(NETSTATS_COMBINE_SUBTYPE_ENABLED, mSettings.getCombineSubtypeEnabled());
            pw.println();
            pw.print(NETSTATS_STORE_FILES_IN_APEXDATA, mDeps.getStoreFilesInApexData());
            pw.println();
            pw.print(NETSTATS_IMPORT_LEGACY_TARGET_ATTEMPTS, mDeps.getImportLegacyTargetAttempts());
            pw.println();
            if (mDeps.getStoreFilesInApexData()) {
                try {
                    pw.print("platform legacy stats import attempts count",
                            mImportLegacyAttemptsCounter.get());
                    pw.println();
                    pw.print("platform legacy stats import successes count",
                            mImportLegacySuccessesCounter.get());
                    pw.println();
                    pw.print("platform legacy stats import fallbacks count",
                            mImportLegacyFallbacksCounter.get());
                    pw.println();
                } catch (IOException e) {
                    pw.println("(failed to dump platform legacy stats import counters)");
                }
            }
            pw.println(CONFIG_ENABLE_NETWORK_STATS_EVENT_LOGGER + ": " + mSupportEventLogger);
            pw.print(NETSTATS_FASTDATAINPUT_TARGET_ATTEMPTS,
                    mDeps.getUseFastDataInputTargetAttempts());
            pw.println();
            try {
                pw.print("FastDataInput successes", mFastDataInputSuccessesCounter.get());
                pw.println();
                pw.print("FastDataInput fallbacks", mFastDataInputFallbacksCounter.get());
                pw.println();
            } catch (IOException e) {
                pw.println("(failed to dump FastDataInput counters)");
            }

            pw.decreaseIndent();

            pw.println("Active interfaces:");
            pw.increaseIndent();
            for (int i = 0; i < mActiveIfaces.size(); i++) {
                pw.print("iface", mActiveIfaces.keyAt(i));
                pw.print("ident", mActiveIfaces.valueAt(i));
                pw.println();
            }
            pw.decreaseIndent();

            pw.println("Active UID interfaces:");
            pw.increaseIndent();
            for (int i = 0; i < mActiveUidIfaces.size(); i++) {
                pw.print("iface", mActiveUidIfaces.keyAt(i));
                pw.print("ident", mActiveUidIfaces.valueAt(i));
                pw.println();
            }
            pw.decreaseIndent();

            pw.println("All wifi interfaces:");
            pw.increaseIndent();
            for (String iface : mAllWifiIfacesSinceBoot) {
                pw.print(iface + " ");
            }
            pw.println();
            pw.decreaseIndent();

            pw.println("All mobile interfaces:");
            pw.increaseIndent();
            for (String iface : mAllMobileIfacesSinceBoot) {
                pw.print(iface + " ");
            }
            pw.println();
            pw.decreaseIndent();

            // Get the top openSession callers
            final HashMap calls;
            synchronized (mOpenSessionCallsLock) {
                calls = new HashMap<>(mOpenSessionCallsPerCaller);
            }
            final List<Map.Entry<OpenSessionKey, Integer>> list = new ArrayList<>(calls.entrySet());
            Collections.sort(list,
                    (left, right) -> Integer.compare(left.getValue(), right.getValue()));
            final int num = list.size();
            final int end = Math.max(0, num - DUMP_STATS_SESSION_COUNT);
            pw.println("Top openSession callers:");
            pw.increaseIndent();
            for (int j = num - 1; j >= end; j--) {
                final Map.Entry<OpenSessionKey, Integer> entry = list.get(j);
                pw.print(entry.getKey()); pw.print("="); pw.println(entry.getValue());

            }
            pw.decreaseIndent();
            pw.println();

            if (mSupportEventLogger) {
                mEventLogger.dump(pw);
            }

            pw.println("Stats Providers:");
            pw.increaseIndent();
            invokeForAllStatsProviderCallbacks((cb) -> {
                pw.println(cb.mTag + " Xt:");
                pw.increaseIndent();
                pw.print(cb.getCachedStats(STATS_PER_IFACE).toString());
                pw.decreaseIndent();
                if (includeUid) {
                    pw.println(cb.mTag + " Uid:");
                    pw.increaseIndent();
                    pw.print(cb.getCachedStats(STATS_PER_UID).toString());
                    pw.decreaseIndent();
                }
            });
            pw.decreaseIndent();
            pw.println();

            pw.println("Stats Observers:");
            pw.increaseIndent();
            mStatsObservers.dump(pw);
            pw.decreaseIndent();
            pw.println();

            pw.println("Dev stats:");
            pw.increaseIndent();
            pw.println("Pending bytes: ");
            if (fullHistory) {
                pw.println("Complete history:");
            } else {
                pw.println("History since boot:");
            }
            pw.decreaseIndent();

            pw.println("Xt stats:");
            pw.increaseIndent();
            mXtRecorder.dumpLocked(pw, fullHistory);
            pw.decreaseIndent();

            if (includeUid) {
                pw.println("UID stats:");
                pw.increaseIndent();
                mUidRecorder.dumpLocked(pw, fullHistory);
                pw.decreaseIndent();
            }

            if (includeTag) {
                pw.println("UID tag stats:");
                pw.increaseIndent();
                mUidTagRecorder.dumpLocked(pw, fullHistory);
                pw.decreaseIndent();
            }

            pw.println();
            pw.println("InterfaceMapHelper:");
            pw.increaseIndent();
            mInterfaceMapHelper.dump(pw);
            pw.decreaseIndent();

            pw.println();
            pw.println("BPF map status:");
            pw.increaseIndent();
            dumpMapStatus(pw);
            pw.decreaseIndent();
            pw.println();

            // Following BPF map content dump contains uid and tag regardless of the flags because
            // following dumps are moved from TrafficController and bug report already contains this
            // information.
            pw.println("BPF map content:");
            pw.increaseIndent();
            dumpCookieTagMapLocked(pw);
            dumpUidCounterSetMapLocked(pw);
            dumpAppUidStatsMapLocked(pw);
            dumpStatsMapLocked(mStatsMapA, pw, "mStatsMapA");
            dumpStatsMapLocked(mStatsMapB, pw, "mStatsMapB");
            dumpIfaceStatsMapLocked(pw);
            pw.decreaseIndent();

            pw.println();
            pw.println("SkDestroyListener logs:");
            pw.increaseIndent();
            mSkDestroyListener.dump(pw);
            pw.decreaseIndent();
        }
    }

    @GuardedBy("mStatsLock")
    private void dumpProtoLocked(FileDescriptor fd) {
        final ProtoOutputStream proto = new ProtoOutputStream(new FileOutputStream(fd));

        // TODO Right now it writes all history.  Should it limit to the "since-boot" log?

        dumpInterfaces(proto, NetworkStatsServiceDumpProto.ACTIVE_INTERFACES,
                mActiveIfaces);
        dumpInterfaces(proto, NetworkStatsServiceDumpProto.ACTIVE_UID_INTERFACES,
                mActiveUidIfaces);
        mXtRecorder.dumpDebugLocked(proto, NetworkStatsServiceDumpProto.XT_STATS);
        mUidRecorder.dumpDebugLocked(proto, NetworkStatsServiceDumpProto.UID_STATS);
        mUidTagRecorder.dumpDebugLocked(proto,
                NetworkStatsServiceDumpProto.UID_TAG_STATS);

        proto.flush();
    }

    private <K extends Struct, V extends Struct> void dumpRawMap(IBpfMap<K, V> map,
            IndentingPrintWriter pw) throws ErrnoException {
        if (map == null) {
            pw.println("Map is null");
            return;
        }
        if (map.isEmpty()) {
            pw.println("");
            return;
        }
        // If there is a concurrent entry deletion, value could be null. http://b/220084230.
        // Also, map.forEach could restart iteration from the beginning and dump could contain
        // duplicated entries. User of this dump needs to take care of the duplicated entries.
        map.forEach((k, v) -> {
            if (v != null) {
                pw.println(BpfDump.toBase64EncodedString(k, v));
            }
        });
    }

    @GuardedBy("mStatsLock")
    private void dumpRawMapLocked(final IndentingPrintWriter pw, final String[] args) {
        if (CollectionUtils.contains(args, "--cookieTagMap")) {
            try {
                dumpRawMap(mCookieTagMap, pw);
            } catch (ErrnoException e) {
                pw.println("Error dumping cookieTag map: " + e);
            }
            return;
        }
    }

    private static void dumpInterfaces(ProtoOutputStream proto, long tag,
            ArrayMap<String, NetworkIdentitySet> ifaces) {
        for (int i = 0; i < ifaces.size(); i++) {
            final long start = proto.start(tag);

            proto.write(NetworkInterfaceProto.INTERFACE, ifaces.keyAt(i));
            ifaces.valueAt(i).dumpDebug(proto, NetworkInterfaceProto.IDENTITIES);

            proto.end(start);
        }
    }

    private void dumpMapStatus(final IndentingPrintWriter pw) {
        BpfDump.dumpMapStatus(mCookieTagMap, pw, "mCookieTagMap", COOKIE_TAG_MAP_PATH);
        BpfDump.dumpMapStatus(mUidCounterSetMap, pw, "mUidCounterSetMap", UID_COUNTERSET_MAP_PATH);
        BpfDump.dumpMapStatus(mAppUidStatsMap, pw, "mAppUidStatsMap", APP_UID_STATS_MAP_PATH);
        BpfDump.dumpMapStatus(mStatsMapA, pw, "mStatsMapA", STATS_MAP_A_PATH);
        BpfDump.dumpMapStatus(mStatsMapB, pw, "mStatsMapB", STATS_MAP_B_PATH);
        // mIfaceStatsMap is always not null but dump status to be consistent with other maps.
        BpfDump.dumpMapStatus(mIfaceStatsMap, pw, "mIfaceStatsMap", IFACE_STATS_MAP_PATH);
    }

    @GuardedBy("mStatsLock")
    private void dumpCookieTagMapLocked(final IndentingPrintWriter pw) {
        if (mCookieTagMap == null) {
            return;
        }
        BpfDump.dumpMap(mCookieTagMap, pw, "mCookieTagMap",
                (key, value) -> "cookie=" + key.socketCookie
                        + " tag=0x" + Long.toHexString(value.tag)
                        + " uid=" + value.uid);
    }

    @GuardedBy("mStatsLock")
    private void dumpUidCounterSetMapLocked(final IndentingPrintWriter pw) {
        if (mUidCounterSetMap == null) {
            return;
        }
        BpfDump.dumpMap(mUidCounterSetMap, pw, "mUidCounterSetMap",
                (uid, set) -> "uid=" + uid.val + " set=" + set.val);
    }

    @GuardedBy("mStatsLock")
    private void dumpAppUidStatsMapLocked(final IndentingPrintWriter pw) {
        if (mAppUidStatsMap == null) {
            return;
        }
        BpfDump.dumpMap(mAppUidStatsMap, pw, "mAppUidStatsMap",
                "uid rxBytes rxPackets txBytes txPackets",
                (key, value) -> key.uid + " "
                        + value.rxBytes + " "
                        + value.rxPackets + " "
                        + value.txBytes + " "
                        + value.txPackets);
    }

    @GuardedBy("mStatsLock")
    private void dumpStatsMapLocked(final IBpfMap<StatsMapKey, StatsMapValue> statsMap,
            final IndentingPrintWriter pw, final String mapName) {
        if (statsMap == null) {
            return;
        }

        BpfDump.dumpMap(statsMap, pw, mapName,
                "ifaceIndex ifaceName tag_hex uid_int cnt_set rxBytes rxPackets txBytes txPackets",
                (key, value) -> {
                    final String ifName = mInterfaceMapHelper.getIfNameByIndex(key.ifaceIndex);
                    return key.ifaceIndex + " "
                            + (ifName != null ? ifName : "unknown") + " "
                            + "0x" + Long.toHexString(key.tag) + " "
                            + key.uid + " "
                            + key.counterSet + " "
                            + value.rxBytes + " "
                            + value.rxPackets + " "
                            + value.txBytes + " "
                            + value.txPackets;
                });
    }

    @GuardedBy("mStatsLock")
    private void dumpIfaceStatsMapLocked(final IndentingPrintWriter pw) {
        BpfDump.dumpMap(mIfaceStatsMap, pw, "mIfaceStatsMap",
                "ifaceIndex ifaceName rxBytes rxPackets txBytes txPackets",
                (key, value) -> {
                    final String ifName = mInterfaceMapHelper.getIfNameByIndex(key.val);
                    return key.val + " "
                            + (ifName != null ? ifName : "unknown") + " "
                            + value.rxBytes + " "
                            + value.rxPackets + " "
                            + value.txBytes + " "
                            + value.txPackets;
                });
    }

    private NetworkStats readNetworkStatsSummaryXt() {
        try {
            return mStatsFactory.readNetworkStatsSummaryXt();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private NetworkStats readNetworkStatsUidDetail(int uid, String[] ifaces, int tag) {
        try {
            return mStatsFactory.readNetworkStatsDetail(uid, ifaces, tag);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Return snapshot of current UID statistics, including any
     * {@link TrafficStats#UID_TETHERING}, video calling data usage, and {@link #mUidOperations}
     * values.
     *
     * @param ifaces A list of interfaces the stats should be restricted to, or
     *               {@link NetworkStats#INTERFACES_ALL}.
     */
    private NetworkStats getNetworkStatsUidDetail(String[] ifaces)
            throws RemoteException {
        final NetworkStats uidSnapshot = readNetworkStatsUidDetail(UID_ALL, ifaces, TAG_ALL);

        // fold tethering stats and operations into uid snapshot
        final NetworkStats tetherSnapshot = getNetworkStatsTethering(STATS_PER_UID);
        tetherSnapshot.filter(UID_ALL, ifaces, TAG_ALL);
        mStatsFactory.apply464xlatAdjustments(uidSnapshot, tetherSnapshot);
        uidSnapshot.combineAllValues(tetherSnapshot);

        // get a stale copy of uid stats snapshot provided by providers.
        final NetworkStats providerStats = getNetworkStatsFromProviders(STATS_PER_UID);
        providerStats.filter(UID_ALL, ifaces, TAG_ALL);
        mStatsFactory.apply464xlatAdjustments(uidSnapshot, providerStats);
        uidSnapshot.combineAllValues(providerStats);

        uidSnapshot.combineAllValues(mUidOperations);
        uidSnapshot.filter(UID_ALL, ifaces, TAG_ALL);

        return uidSnapshot;
    }

    /**
     * Return snapshot of current non-offloaded tethering statistics. Will return empty
     * {@link NetworkStats} if any problems are encountered, or queried by {@code STATS_PER_IFACE}
     * since it is already included by {@link #nativeGetIfaceStat}.
     * See {@code OffloadTetheringStatsProvider} for offloaded tethering stats.
     */
    // TODO: Remove this by implementing {@link NetworkStatsProvider} for non-offloaded
    //  tethering stats.
    private @NonNull NetworkStats getNetworkStatsTethering(int how) throws RemoteException {
         // We only need to return per-UID stats. Per-device stats are already counted by
        // interface counters.
        if (how != STATS_PER_UID) {
            return new NetworkStats(SystemClock.elapsedRealtime(), 0);
        }

        final NetworkStats stats = new NetworkStats(SystemClock.elapsedRealtime(), 1);
        try {
            final TetherStatsParcel[] tetherStatsParcels = mNetd.tetherGetStats();
            for (TetherStatsParcel tetherStats : tetherStatsParcels) {
                try {
                    stats.combineValues(new NetworkStats.Entry(tetherStats.iface, UID_TETHERING,
                            SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO,
                            tetherStats.rxBytes, tetherStats.rxPackets,
                            tetherStats.txBytes, tetherStats.txPackets, 0L));
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new IllegalStateException("invalid tethering stats " + e);
                }
            }
        } catch (IllegalStateException | ServiceSpecificException e) {
            Log.wtf(TAG, "problem reading network stats", e);
        }
        return stats;
    }

    /**
     * Registers a custom provider of {@link android.net.NetworkStats} to combine the network
     * statistics that cannot be seen by the kernel to system. To unregister, invoke the
     * {@code unregister()} of the returned callback.
     *
     * @param tag a human readable identifier of the custom network stats provider.
     * @param provider the {@link INetworkStatsProvider} binder corresponding to the
     *                 {@link NetworkStatsProvider} to be registered.
     *
     * @return a {@link INetworkStatsProviderCallback} binder
     *         interface, which can be used to report events to the system.
     */
    public @NonNull INetworkStatsProviderCallback registerNetworkStatsProvider(
            @NonNull String tag, @NonNull INetworkStatsProvider provider) {
        PermissionUtils.enforceAnyPermissionOf(mContext, NETWORK_STATS_PROVIDER,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
        Objects.requireNonNull(provider, "provider is null");
        Objects.requireNonNull(tag, "tag is null");
        final NetworkPolicyManager netPolicyManager = mContext
                .getSystemService(NetworkPolicyManager.class);
        try {
            NetworkStatsProviderCallbackImpl callback = new NetworkStatsProviderCallbackImpl(
                    tag, provider, mStatsProviderSem, mAlertObserver,
                    mStatsProviderCbList, netPolicyManager);
            mStatsProviderCbList.add(callback);
            Log.d(TAG, "registerNetworkStatsProvider from " + callback.mTag + " uid/pid="
                    + getCallingUid() + "/" + getCallingPid());
            return callback;
        } catch (RemoteException e) {
            Log.e(TAG, "registerNetworkStatsProvider failed", e);
        }
        return null;
    }

    // Collect stats from local cache of providers.
    private @NonNull NetworkStats getNetworkStatsFromProviders(int how) {
        final NetworkStats ret = new NetworkStats(0L, 0);
        invokeForAllStatsProviderCallbacks((cb) -> ret.combineAllValues(cb.getCachedStats(how)));
        return ret;
    }

    @FunctionalInterface
    private interface ThrowingConsumer<S, T extends Throwable> {
        void accept(S s) throws T;
    }

    private void invokeForAllStatsProviderCallbacks(
            @NonNull ThrowingConsumer<NetworkStatsProviderCallbackImpl, RemoteException> task) {
        for (final NetworkStatsProviderCallbackImpl cb : mStatsProviderCbList) {
            try {
                task.accept(cb);
            } catch (RemoteException e) {
                Log.e(TAG, "Fail to broadcast to provider: " + cb.mTag, e);
            }
        }
    }

    private static class NetworkStatsProviderCallbackImpl extends INetworkStatsProviderCallback.Stub
            implements IBinder.DeathRecipient {
        @NonNull final String mTag;

        @NonNull final INetworkStatsProvider mProvider;
        @NonNull private final Semaphore mSemaphore;
        @NonNull final AlertObserver mAlertObserver;
        @NonNull final CopyOnWriteArrayList<NetworkStatsProviderCallbackImpl> mStatsProviderCbList;
        @NonNull final NetworkPolicyManager mNetworkPolicyManager;

        @NonNull private final Object mProviderStatsLock = new Object();

        @GuardedBy("mProviderStatsLock")
        // Track STATS_PER_IFACE and STATS_PER_UID separately.
        private final NetworkStats mIfaceStats = new NetworkStats(0L, 0);
        @GuardedBy("mProviderStatsLock")
        private final NetworkStats mUidStats = new NetworkStats(0L, 0);

        NetworkStatsProviderCallbackImpl(
                @NonNull String tag, @NonNull INetworkStatsProvider provider,
                @NonNull Semaphore semaphore,
                @NonNull AlertObserver alertObserver,
                @NonNull CopyOnWriteArrayList<NetworkStatsProviderCallbackImpl> cbList,
                @NonNull NetworkPolicyManager networkPolicyManager)
                throws RemoteException {
            mTag = tag;
            mProvider = provider;
            mProvider.asBinder().linkToDeath(this, 0);
            mSemaphore = semaphore;
            mAlertObserver = alertObserver;
            mStatsProviderCbList = cbList;
            mNetworkPolicyManager = networkPolicyManager;
        }

        @NonNull
        public NetworkStats getCachedStats(int how) {
            synchronized (mProviderStatsLock) {
                NetworkStats stats;
                switch (how) {
                    case STATS_PER_IFACE:
                        stats = mIfaceStats;
                        break;
                    case STATS_PER_UID:
                        stats = mUidStats;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid type: " + how);
                }
                // Callers might be able to mutate the returned object. Return a defensive copy
                // instead of local reference.
                return stats.clone();
            }
        }

        @Override
        public void notifyStatsUpdated(int token, @Nullable NetworkStats ifaceStats,
                @Nullable NetworkStats uidStats) {
            // TODO: 1. Use token to map ifaces to correct NetworkIdentity.
            //       2. Store the difference and store it directly to the recorder.
            synchronized (mProviderStatsLock) {
                if (ifaceStats != null) mIfaceStats.combineAllValues(ifaceStats);
                if (uidStats != null) mUidStats.combineAllValues(uidStats);
            }
            mSemaphore.release();
        }

        @Override
        public void notifyAlertReached() throws RemoteException {
            // This binder object can only have been obtained by a process that holds
            // NETWORK_STATS_PROVIDER. Thus, no additional permission check is required.
            BinderUtils.withCleanCallingIdentity(() ->
                    mAlertObserver.onQuotaLimitReached(LIMIT_GLOBAL_ALERT, null /* unused */));
        }

        @Override
        public void notifyWarningReached() {
            Log.d(TAG, mTag + ": notifyWarningReached");
            BinderUtils.withCleanCallingIdentity(() ->
                    mNetworkPolicyManager.notifyStatsProviderWarningReached());
        }

        @Override
        public void notifyLimitReached() {
            Log.d(TAG, mTag + ": notifyLimitReached");
            BinderUtils.withCleanCallingIdentity(() ->
                    mNetworkPolicyManager.notifyStatsProviderLimitReached());
        }

        @Override
        public void binderDied() {
            Log.d(TAG, mTag + ": binderDied");
            mStatsProviderCbList.remove(this);
        }

        @Override
        public void unregister() {
            Log.d(TAG, mTag + ": unregister");
            mStatsProviderCbList.remove(this);
        }

    }

    private void assertSystemReady() {
        if (!mSystemReady) {
            throw new IllegalStateException("System not ready");
        }
    }

    private final boolean mSupportEventLogger;
    @GuardedBy("mStatsLock")
    @Nullable
    private final NetworkStatsEventLogger mEventLogger;

    /**
     * Create a PollEvent instance if the feature is enabled.
     */
    @Nullable
    public PollEvent maybeCreatePollEvent(@NetworkStatsEventLogger.PollReason int reason) {
        if (mSupportEventLogger) {
            return new PollEvent(reason);
        }
        return null;
    }

    private class DropBoxNonMonotonicObserver implements NonMonotonicObserver<String> {
        @Override
        public void foundNonMonotonic(NetworkStats left, int leftIndex, NetworkStats right,
                int rightIndex, String cookie) {
            Log.w(TAG, "Found non-monotonic values; saving to dropbox");

            // record error for debugging
            final StringBuilder builder = new StringBuilder();
            builder.append("found non-monotonic " + cookie + " values at left[" + leftIndex
                    + "] - right[" + rightIndex + "]\n");
            builder.append("left=").append(left).append('\n');
            builder.append("right=").append(right).append('\n');

            mContext.getSystemService(DropBoxManager.class).addText(TAG_NETSTATS_ERROR,
                    builder.toString());
        }

        @Override
        public void foundNonMonotonic(
                NetworkStats stats, int statsIndex, String cookie) {
            Log.w(TAG, "Found non-monotonic values; saving to dropbox");

            final StringBuilder builder = new StringBuilder();
            builder.append("Found non-monotonic " + cookie + " values at [" + statsIndex + "]\n");
            builder.append("stats=").append(stats).append('\n');

            mContext.getSystemService(DropBoxManager.class).addText(TAG_NETSTATS_ERROR,
                    builder.toString());
        }
    }

    /**
     * Default external settings that read from
     * {@link android.provider.Settings.Global}.
     */
    @VisibleForTesting(visibility = PRIVATE)
    static class DefaultNetworkStatsSettings implements NetworkStatsSettings {
        DefaultNetworkStatsSettings() {}

        @Override
        public long getPollInterval() {
            return 30 * MINUTE_IN_MILLIS;
        }
        @Override
        public long getPollDelay() {
            return DEFAULT_PERFORM_POLL_DELAY_MS;
        }
        @Override
        public long getGlobalAlertBytes(long def) {
            return def;
        }
        @Override
        public boolean getSampleEnabled() {
            return true;
        }
        @Override
        public boolean getAugmentEnabled() {
            return true;
        }
        @Override
        public boolean getCombineSubtypeEnabled() {
            return false;
        }
        @Override
        public Config getXtConfig() {
            return new Config(HOUR_IN_MILLIS, 15 * DAY_IN_MILLIS, 90 * DAY_IN_MILLIS);
        }
        @Override
        public Config getUidConfig() {
            return new Config(2 * HOUR_IN_MILLIS, 15 * DAY_IN_MILLIS, 90 * DAY_IN_MILLIS);
        }
        @Override
        public Config getUidTagConfig() {
            return new Config(2 * HOUR_IN_MILLIS, 5 * DAY_IN_MILLIS, 15 * DAY_IN_MILLIS);
        }
        @Override
        public long getXtPersistBytes(long def) {
            return def;
        }
        @Override
        public long getUidPersistBytes(long def) {
            return def;
        }
        @Override
        public long getUidTagPersistBytes(long def) {
            return def;
        }
    }

    // TODO: Read stats by using BpfNetMapsReader.
    private static native void nativeRegisterIface(String iface);
    @Nullable
    private static native NetworkStats.Entry nativeGetTotalStat();
    @Nullable
    private static native NetworkStats.Entry nativeGetIfaceStat(String iface);
    @Nullable
    private static native NetworkStats.Entry nativeGetUidStat(int uid);

    /** Initializes and registers the Perfetto Network Trace data source */
    public static native void nativeInitNetworkTracing();
}
