/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.watchdog;

import static android.app.StatsManager.PULL_SKIP;
import static android.app.StatsManager.PULL_SUCCESS;
import static android.car.builtin.os.UserManagerHelper.USER_NULL;
import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;
import static android.car.watchdog.CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_CURRENT_DAY;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_PAST_15_DAYS;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_PAST_30_DAYS;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_PAST_3_DAYS;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_NEVER;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_NO;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_YES;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.os.Process.INVALID_UID;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import static com.android.car.CarServiceUtils.getContentResolverForUser;
import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_IO_OVERUSE_STATS_REPORTED;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__KILL_REASON__KILLED_ON_IO_OVERUSE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__GARAGE_MODE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_INTERACTION_MODE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__UID_STATE__UNKNOWN_UID_STATE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_UID_IO_USAGE_SUMMARY;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP;
import static com.android.car.watchdog.CarWatchdogService.DEBUG;
import static com.android.car.watchdog.CarWatchdogService.TAG;
import static com.android.car.watchdog.PackageInfoHandler.SHARED_PACKAGE_PREFIX;
import static com.android.car.watchdog.TimeSource.ZONE_OFFSET;
import static com.android.car.watchdog.WatchdogStorage.RETENTION_PERIOD;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.StatsManager;
import android.app.StatsManager.PullAtomMetadata;
import android.automotive.watchdog.internal.ApplicationCategoryType;
import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.GarageMode;
import android.automotive.watchdog.internal.IoUsageStats;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.PackageMetadata;
import android.automotive.watchdog.internal.PerStateIoOveruseThreshold;
import android.automotive.watchdog.internal.ResourceSpecificConfiguration;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.PackageKillableState.KillableState;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.TransactionTooLargeException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Pair;
import android.util.SparseArray;
import android.util.StatsEvent;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.BuiltinPackageDependency;
import com.android.car.CarLocalServices;
import com.android.car.CarStatsLog;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.NotificationHelperBase;
import com.android.car.internal.util.ConcurrentUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Handles system resource performance monitoring module.
 */
public final class WatchdogPerfHandler {
    public static final String INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS = "MAPS";
    public static final String INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA = "MEDIA";
    public static final String INTERNAL_APPLICATION_CATEGORY_TYPE_UNKNOWN = "UNKNOWN";

    static final String INTENT_EXTRA_NOTIFICATION_ID = "notification_id";
    static final String USER_PACKAGE_SEPARATOR = ":";
    static final String PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR = ";";

    private static final String METADATA_FILENAME = "metadata.json";
    private static final String SYSTEM_IO_USAGE_SUMMARY_REPORTED_DATE =
            "systemIoUsageSummaryReportedDate";
    private static final String UID_IO_USAGE_SUMMARY_REPORTED_DATE =
            "uidIoUsageSummaryReportedDate";
    private static final long OVERUSE_HANDLING_DELAY_MILLS = 10_000;
    static final long MAX_WAIT_TIME_MILLS = 3_000;

    private static final PullAtomMetadata PULL_ATOM_METADATA =
            new PullAtomMetadata.Builder()
                    // Summary atoms are populated only once a week, so a longer duration is
                    // tolerable. However, the cool down duration should be smaller than a short
                    // drive, so summary atoms can be pulled with short drives.
                    .setCoolDownMillis(TimeUnit.MILLISECONDS.convert(5L, TimeUnit.MINUTES))
                    // When summary atoms are populated once a week, watchdog needs additional time
                    // for reading from disk/DB.
                    .setTimeoutMillis(10_000)
                    .build();

    /**
     * Don't distract the user by sending user notifications/dialogs, killing foreground
     * applications, repeatedly killing persistent background services, or disabling any
     * application.
     */
    private static final int UX_STATE_NO_DISTRACTION = 1;
    /** The user can safely receive user notifications or dialogs. */
    private static final int UX_STATE_USER_NOTIFICATION = 2;
    /**
     * Any application or service can be safely killed/disabled. User notifications can be sent
     * only to the notification center.
     */
    private static final int UX_STATE_NO_INTERACTION = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"UX_STATE_"}, value = {
            UX_STATE_NO_DISTRACTION,
            UX_STATE_USER_NOTIFICATION,
            UX_STATE_NO_INTERACTION
    })
    private @interface UxStateType{}

    private final Context mContext;
    /**
     * Context of the builtin car service that hosts the permissions, resources, and external
     * facing services required for showing notifications.
     */
    private final Context mBuiltinPackageContext;
    private final CarWatchdogDaemonHelper mCarWatchdogDaemonHelper;
    private final PackageInfoHandler mPackageInfoHandler;
    private final Handler mMainHandler;
    private final Handler mServiceHandler;
    private final WatchdogStorage mWatchdogStorage;
    private final OveruseConfigurationCache mOveruseConfigurationCache;
    private final int mUidIoUsageSummaryTopCount;
    private final int mIoUsageSummaryMinSystemTotalWrittenBytes;
    private final int mPackageKillableStateResetDays;
    private final int mRecurringOverusePeriodInDays;
    private final int mRecurringOveruseTimes;
    private final int mResourceOveruseNotificationBaseId;
    private final int mResourceOveruseNotificationMaxOffset;
    private final TimeSource mTimeSource;
    private final Object mLock = new Object();
    /**
     * Tracks user packages' resource usage. When cache is updated, call
     * {@link WatchdogStorage#markDirty} to notify database is out of sync.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, PackageResourceUsage> mUsageByUserPackage = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SparseArray<ArrayList<ResourceOveruseListenerInfo>> mOveruseListenerInfosByUid =
            new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<ArrayList<ResourceOveruseListenerInfo>>
            mOveruseSystemListenerInfosByUid = new SparseArray<>();
    /**
     * Default killable state for packages. Updated only for {@link UserHandle#ALL} user handle.
     * When cache is updated, call {@link WatchdogStorage#markDirty} to notify database is out of
     * sync.
     */
    // TODO(b/235615155): Update database when a default not killable package is set to killable
    //  Also, changes to mDefaultNotKillableGenericPackages should be tracked by the last modified
    //  date. This date should be copied to any new user package settings that take the default
    //  value. When this date is beyond reset days, the settings here should be reset.
    @GuardedBy("mLock")
    private final ArraySet<String> mDefaultNotKillableGenericPackages = new ArraySet<>();
    /** Keys in {@link mUsageByUserPackage} for user notification on resource overuse. */
    @GuardedBy("mLock")
    private final ArraySet<String> mUserNotifiablePackages = new ArraySet<>();
    /** Values are the unique ids generated by {@code getUserPackageUniqueId}. */
    @GuardedBy("mLock")
    private final SparseArray<String> mActiveUserNotificationsByNotificationId =
            new SparseArray<>();
    /** Keys are the unique ids generated by {@code getUserPackageUniqueId}. */
    @GuardedBy("mLock")
    private final ArraySet<String> mActiveUserNotifications = new ArraySet<>();
    /**
     * Keys in {@link mUsageByUserPackage} that should be killed/disabled due to resource overuse.
     */
    @GuardedBy("mLock")
    private final ArraySet<String> mActionableUserPackages = new ArraySet<>();
    /**
     * Tracks user packages disabled due to resource overuse.
     */
    @GuardedBy("mLock")
    private final SparseArray<ArraySet<String>> mDisabledUserPackagesByUserId = new SparseArray<>();
    @GuardedBy("mLock")
    private ZonedDateTime mLatestStatsReportDate;
    @GuardedBy("mLock")
    private List<android.automotive.watchdog.internal.ResourceOveruseConfiguration>
            mPendingSetResourceOveruseConfigurationsRequest = null;
    @GuardedBy("mLock")
    private boolean mIsConnectedToDaemon;
    @GuardedBy("mLock")
    private @UxStateType int mCurrentUxState = UX_STATE_NO_DISTRACTION;
    @GuardedBy("mLock")
    private CarUxRestrictions mCurrentUxRestrictions;
    @GuardedBy("mLock")
    private boolean mIsHeadsUpNotificationSent;
    @GuardedBy("mLock")
    private int mCurrentOveruseNotificationIdOffset;
    @GuardedBy("mLock")
    private @GarageMode int mCurrentGarageMode = GarageMode.GARAGE_MODE_OFF;
    @GuardedBy("mLock")
    private long mOveruseHandlingDelayMills = OVERUSE_HANDLING_DELAY_MILLS;
    @GuardedBy("mLock")
    private ZonedDateTime mLastSystemIoUsageSummaryReportedDate;
    @GuardedBy("mLock")
    private ZonedDateTime mLastUidIoUsageSummaryReportedDate;

    private final ICarUxRestrictionsChangeListener mCarUxRestrictionsChangeListener =
            new ICarUxRestrictionsChangeListener.Stub() {
                @Override
                public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
                    synchronized (mLock) {
                        mCurrentUxRestrictions = new CarUxRestrictions(restrictions);
                        applyCurrentUxRestrictionsLocked();
                    }
                }
            };

    public WatchdogPerfHandler(Context context, Context builtinPackageContext,
            CarWatchdogDaemonHelper daemonHelper, PackageInfoHandler packageInfoHandler,
            WatchdogStorage watchdogStorage, TimeSource timeSource) {
        mContext = context;
        mBuiltinPackageContext = builtinPackageContext;
        mCarWatchdogDaemonHelper = daemonHelper;
        mPackageInfoHandler = packageInfoHandler;
        mMainHandler = new Handler(Looper.getMainLooper());
        mServiceHandler = new Handler(getHandlerThread(
                CarWatchdogService.class.getSimpleName()).getLooper());
        mWatchdogStorage = watchdogStorage;
        mOveruseConfigurationCache = new OveruseConfigurationCache();
        mTimeSource = timeSource;
        Resources resources = mContext.getResources();
        mUidIoUsageSummaryTopCount = resources.getInteger(R.integer.uidIoUsageSummaryTopCount);
        mIoUsageSummaryMinSystemTotalWrittenBytes =
                resources.getInteger(R.integer.ioUsageSummaryMinSystemTotalWrittenBytes);
        mPackageKillableStateResetDays =
                resources.getInteger(R.integer.watchdogUserPackageSettingsResetDays);
        mRecurringOverusePeriodInDays =
                resources.getInteger(R.integer.recurringResourceOverusePeriodInDays);
        mRecurringOveruseTimes = resources.getInteger(R.integer.recurringResourceOveruseTimes);
        mResourceOveruseNotificationBaseId =
                NotificationHelperBase.RESOURCE_OVERUSE_NOTIFICATION_BASE_ID;
        mResourceOveruseNotificationMaxOffset =
                NotificationHelperBase.RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
    }

    /** Initializes the handler. */
    public void init() {
        // First database read is expensive, so post it on a separate handler thread.
        mServiceHandler.post(() -> {
            readFromDatabase();
            // Set atom pull callbacks only after the internal datastructures are updated. When the
            // pull happens, the service is already initialized and ready to populate the pulled
            // atoms.
            StatsManager statsManager = mContext.getSystemService(StatsManager.class);
            statsManager.setPullAtomCallback(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                    PULL_ATOM_METADATA, ConcurrentUtils.DIRECT_EXECUTOR, this::onPullAtom);
            statsManager.setPullAtomCallback(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                    PULL_ATOM_METADATA, ConcurrentUtils.DIRECT_EXECUTOR, this::onPullAtom);
        });

        CarUxRestrictionsManagerService carUxRestrictionsManagerService =
                CarLocalServices.getService(CarUxRestrictionsManagerService.class);
        CarUxRestrictions uxRestrictions =
                carUxRestrictionsManagerService.getCurrentUxRestrictions();
        synchronized (mLock) {
            mCurrentUxRestrictions = uxRestrictions;
            applyCurrentUxRestrictionsLocked();
            syncDisabledUserPackagesLocked();
        }
        carUxRestrictionsManagerService.registerUxRestrictionsChangeListener(
                mCarUxRestrictionsChangeListener, Display.DEFAULT_DISPLAY);

        if (DEBUG) {
            Slogf.d(TAG, "WatchdogPerfHandler is initialized");
        }
    }

    /** Releases resources. */
    public void release() {
        CarLocalServices.getService(CarUxRestrictionsManagerService.class)
                .unregisterUxRestrictionsChangeListener(mCarUxRestrictionsChangeListener);
    }

    /** Dumps its state. */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        /*
         * TODO(b/183436216): Implement this method.
         */
        synchronized (mLock) {
            writer.println("Current UX state: " + toUxStateString(mCurrentUxState));
            writer.println("List of disabled packages per user due to resource overuse: "
                    + mDisabledUserPackagesByUserId);
        }
        mOveruseConfigurationCache.dump(writer);
    }

    /** Dumps its state in proto format */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            long performanceDumpToken = proto.start(CarWatchdogDumpProto.PERFORMANCE_DUMP);
            proto.write(PerformanceDump.CURRENT_UX_STATE, toProtoUxState(mCurrentUxState));
            for (int i = 0; i < mDisabledUserPackagesByUserId.size(); i++) {
                for (int j = 0; j < mDisabledUserPackagesByUserId.valueAt(i).size(); j++) {
                    long disabledUserPackagesToken = proto.start(
                            PerformanceDump.DISABLED_USER_PACKAGES);
                    proto.write(UserPackageInfo.USER_ID,
                            mDisabledUserPackagesByUserId.keyAt(i));
                    proto.write(UserPackageInfo.PACKAGE_NAME,
                            mDisabledUserPackagesByUserId.valueAt(i).valueAt(j));
                    proto.end(disabledUserPackagesToken);
                }
            }
            proto.write(PerformanceDump.UID_IO_USAGE_SUMMARY_TOP_COUNT, mUidIoUsageSummaryTopCount);
            proto.write(PerformanceDump.IO_USAGE_SUMMARY_MIN_SYSTEM_TOTAL_WRITTEN_BYTES,
                    mIoUsageSummaryMinSystemTotalWrittenBytes);
            proto.write(PerformanceDump.PACKAGE_KILLABLE_STATE_RESET_DAYS,
                    mPackageKillableStateResetDays);
            proto.write(PerformanceDump.RECURRING_OVERUSE_PERIOD_DAYS,
                    mRecurringOverusePeriodInDays);
            proto.write(PerformanceDump.RESOURCE_OVERUSE_NOTIFICATION_BASE_ID,
                    mResourceOveruseNotificationBaseId);
            proto.write(PerformanceDump.RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET,
                    mResourceOveruseNotificationMaxOffset);
            proto.write(PerformanceDump.IS_CONNECTED_TO_DAEMON, mIsConnectedToDaemon);
            proto.write(PerformanceDump.IS_HEADS_UP_NOTIFICATION_SENT, mIsHeadsUpNotificationSent);
            proto.write(PerformanceDump.CURRENT_OVERUSE_NOTIFICATION_ID_OFFSET,
                    mCurrentOveruseNotificationIdOffset);
            proto.write(PerformanceDump.IS_GARAGE_MODE_ACTIVE, mCurrentGarageMode);
            proto.write(PerformanceDump.OVERUSE_HANDLING_DELAY_MILLIS, mOveruseHandlingDelayMills);

            long systemDateTimeToken = proto.start(
                    PerformanceDump.LAST_SYSTEM_IO_USAGE_SUMMARY_REPORTED_UTC_DATETIME);
            long systemDateToken = proto.start(DateTime.DATE);
            proto.write(Date.YEAR, mLastSystemIoUsageSummaryReportedDate.getYear());
            proto.write(Date.MONTH, mLastSystemIoUsageSummaryReportedDate.getMonthValue());
            proto.write(Date.DAY, mLastSystemIoUsageSummaryReportedDate.getDayOfMonth());
            proto.end(systemDateToken);
            long systemTimeOfDayToken = proto.start(DateTime.TIME_OF_DAY);
            proto.write(TimeOfDay.HOURS, mLastSystemIoUsageSummaryReportedDate.getHour());
            proto.write(TimeOfDay.MINUTES, mLastSystemIoUsageSummaryReportedDate.getMinute());
            proto.write(TimeOfDay.SECONDS, mLastSystemIoUsageSummaryReportedDate.getSecond());
            proto.end(systemTimeOfDayToken);
            proto.end(systemDateTimeToken);

            long uidDateTimeToken = proto.start(
                    PerformanceDump.LAST_UID_IO_USAGE_SUMMARY_REPORTED_UTC_DATETIME);
            long uidDateToken = proto.start(DateTime.DATE);
            proto.write(Date.YEAR, mLastUidIoUsageSummaryReportedDate.getYear());
            proto.write(Date.MONTH, mLastUidIoUsageSummaryReportedDate.getMonthValue());
            proto.write(Date.DAY, mLastUidIoUsageSummaryReportedDate.getDayOfMonth());
            proto.end(uidDateToken);
            long uidTimeOfDayToken = proto.start(DateTime.TIME_OF_DAY);
            proto.write(TimeOfDay.HOURS, mLastUidIoUsageSummaryReportedDate.getHour());
            proto.write(TimeOfDay.MINUTES, mLastUidIoUsageSummaryReportedDate.getMinute());
            proto.write(TimeOfDay.SECONDS, mLastUidIoUsageSummaryReportedDate.getSecond());
            proto.end(uidTimeOfDayToken);
            proto.end(uidDateTimeToken);

            dumpUsageByUserPackageLocked(proto);

            dumpResourceOveruseListenerInfosLocked(mOveruseListenerInfosByUid,
                    PerformanceDump.OVERUSE_LISTENER_INFOS, proto);

            dumpResourceOveruseListenerInfosLocked(mOveruseSystemListenerInfosByUid,
                    PerformanceDump.SYSTEM_OVERUSE_LISTENER_INFOS, proto);

            for (int i = 0; i < mDefaultNotKillableGenericPackages.size(); i++) {
                proto.write(PerformanceDump.DEFAULT_NOT_KILLABLE_GENERIC_PACKAGES,
                        mDefaultNotKillableGenericPackages.valueAt(i));
            }

            dumpUserPackageInfo(mUserNotifiablePackages,
                    PerformanceDump.USER_NOTIFIABLE_PACKAGES, proto);

            dumpUserPackageInfo(mActiveUserNotifications,
                    PerformanceDump.ACTIVE_USER_NOTIFICATIONS, proto);

            dumpUserPackageInfo(mActionableUserPackages,
                    PerformanceDump.ACTIONABLE_USER_PACKAGES, proto);

            proto.write(PerformanceDump.IS_PENDING_RESOURCE_OVERUSE_CONFIGURATIONS_REQUEST,
                    mPendingSetResourceOveruseConfigurationsRequest != null);

            mOveruseConfigurationCache.dumpProto(proto);

            proto.end(performanceDumpToken);
        }
    }


    /** Retries any pending requests on re-connecting to the daemon */
    public void onDaemonConnectionChange(boolean isConnected) {
        boolean hasPendingRequest;
        synchronized (mLock) {
            mIsConnectedToDaemon = isConnected;
            hasPendingRequest = mPendingSetResourceOveruseConfigurationsRequest != null;
        }
        if (isConnected) {
            if (hasPendingRequest) {
                /*
                 * Retry pending set resource overuse configuration request before processing any
                 * new set/get requests. Thus notify the waiting requests only after the retry
                 * completes.
                 */
                retryPendingSetResourceOveruseConfigurations();
            } else {
                /* Start fetch/sync configs only when there are no pending set requests because the
                 * above retry starts fetch/sync configs on success. If the retry fails, the daemon
                 * has crashed and shouldn't start fetchAndSyncResourceOveruseConfigurations.
                 */
                mMainHandler.post(this::fetchAndSyncResourceOveruseConfigurations);
            }
        }
        synchronized (mLock) {
            mLock.notifyAll();
        }
    }

    /** Updates the current UX state based on the display state. */
    public void onDisplayStateChanged(boolean isEnabled) {
        synchronized (mLock) {
            if (isEnabled) {
                mCurrentUxState = UX_STATE_NO_DISTRACTION;
                applyCurrentUxRestrictionsLocked();
            } else {
                mCurrentUxState = UX_STATE_NO_INTERACTION;
                performOveruseHandlingLocked();
            }
        }
    }

    /** Handles garage mode change. */
    public void onGarageModeChange(@GarageMode int garageMode) {
        synchronized (mLock) {
            mCurrentGarageMode = garageMode;
            if (mCurrentGarageMode == GarageMode.GARAGE_MODE_ON) {
                mCurrentUxState = UX_STATE_NO_INTERACTION;
                performOveruseHandlingLocked();
            }
        }
    }

    /** Returns resource overuse stats for the calling package. */
    @NonNull
    public ResourceOveruseStats getResourceOveruseStats(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        Preconditions.checkArgument((maxStatsPeriod > 0),
                "Must provide valid maximum stats period");
        // When more resource stats are added, make this as optional.
        Preconditions.checkArgument((resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) != 0,
                "Must provide resource I/O overuse flag");
        int callingUid = Binder.getCallingUid();
        UserHandle callingUserHandle = Binder.getCallingUserHandle();
        int callingUserId = callingUserHandle.getIdentifier();
        String genericPackageName =
                mPackageInfoHandler.getNamesForUids(new int[]{callingUid})
                        .get(callingUid, null);
        if (genericPackageName == null) {
            Slogf.w(TAG, "Failed to fetch package info for uid %d", callingUid);
            return new ResourceOveruseStats.Builder("", callingUserHandle).build();
        }
        ResourceOveruseStats.Builder statsBuilder =
                new ResourceOveruseStats.Builder(genericPackageName, callingUserHandle);
        statsBuilder.setIoOveruseStats(
                getIoOveruseStatsForPeriod(callingUserId, genericPackageName, maxStatsPeriod));
        if (DEBUG) {
            Slogf.d(TAG, "Returning all resource overuse stats for calling uid %d [user %d and "
                            + "package '%s']", callingUid, callingUserId, genericPackageName);
        }
        return statsBuilder.build();
    }

    /** Returns resource overuse stats for all packages. */
    @NonNull
    public List<ResourceOveruseStats> getAllResourceOveruseStats(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @CarWatchdogManager.MinimumStatsFlag int minimumStatsFlag,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        Preconditions.checkArgument((maxStatsPeriod > 0),
                "Must provide valid maximum stats period");
        // When more resource types are added, make this as optional.
        Preconditions.checkArgument((resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) != 0,
                "Must provide resource I/O overuse flag");
        long minimumBytesWritten = getMinimumBytesWritten(minimumStatsFlag);
        List<ResourceOveruseStats> allStats = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mUsageByUserPackage.size(); ++i) {
                PackageResourceUsage usage = mUsageByUserPackage.valueAt(i);
                ResourceOveruseStats.Builder statsBuilder = usage.getResourceOveruseStatsBuilder();
                IoOveruseStats ioOveruseStats =
                        getIoOveruseStatsLocked(usage, minimumBytesWritten, maxStatsPeriod);
                if (ioOveruseStats == null) {
                    continue;
                }
                allStats.add(statsBuilder.setIoOveruseStats(ioOveruseStats).build());
            }
        }
        if (DEBUG) {
            Slogf.d(TAG, "Returning all resource overuse stats");
        }
        return allStats;
    }

    /** Returns resource overuse stats for the specified user package. */
    @NonNull
    public ResourceOveruseStats getResourceOveruseStatsForUserPackage(
            @NonNull String packageName, @NonNull UserHandle userHandle,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        Objects.requireNonNull(packageName, "Package name must be non-null");
        Objects.requireNonNull(userHandle, "User handle must be non-null");
        Preconditions.checkArgument(!userHandle.equals(UserHandle.ALL),
                "Must provide the user handle for a specific user");
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        Preconditions.checkArgument((maxStatsPeriod > 0),
                "Must provide valid maximum stats period");
        // When more resource types are added, make this as optional.
        Preconditions.checkArgument((resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) != 0,
                "Must provide resource I/O overuse flag");
        String genericPackageName =
                mPackageInfoHandler.getNameForUserPackage(packageName, userHandle.getIdentifier());
        if (genericPackageName == null) {
            throw new IllegalArgumentException("Package '" + packageName + "' not found");
        }
        ResourceOveruseStats.Builder statsBuilder =
                new ResourceOveruseStats.Builder(genericPackageName, userHandle);
        statsBuilder.setIoOveruseStats(getIoOveruseStatsForPeriod(userHandle.getIdentifier(),
                genericPackageName, maxStatsPeriod));
        if (DEBUG) {
            Slogf.d(TAG, "Returning resource overuse stats for user %d, package '%s', "
                    + "generic package '%s'", userHandle.getIdentifier(), packageName,
                    genericPackageName);
        }
        return statsBuilder.build();
    }

    /** Adds the resource overuse listener. */
    public void addResourceOveruseListener(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull IResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        synchronized (mLock) {
            addResourceOveruseListenerLocked(resourceOveruseFlag, listener,
                    mOveruseListenerInfosByUid);
        }
    }

    /** Removes the previously added resource overuse listener. */
    public void removeResourceOveruseListener(@NonNull IResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        synchronized (mLock) {
            removeResourceOveruseListenerLocked(listener, mOveruseListenerInfosByUid);
        }
    }

    /** Adds the resource overuse system listener. */
    public void addResourceOveruseListenerForSystem(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull IResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        synchronized (mLock) {
            addResourceOveruseListenerLocked(resourceOveruseFlag, listener,
                    mOveruseSystemListenerInfosByUid);
        }
    }

    /** Removes the previously added resource overuse system listener. */
    public void removeResourceOveruseListenerForSystem(@NonNull IResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        synchronized (mLock) {
            removeResourceOveruseListenerLocked(listener, mOveruseSystemListenerInfosByUid);
        }
    }

    /** Sets whether or not a package is killable on resource overuse. */
    public void setKillablePackageAsUser(String packageName, UserHandle userHandle,
            boolean isKillable) {
        Objects.requireNonNull(packageName, "Package name must be non-null");
        Objects.requireNonNull(userHandle, "User handle must be non-null");

        if (userHandle.equals(UserHandle.ALL)) {
            setPackageKillableStateForAllUsers(packageName, isKillable);
            return;
        }
        int userId = userHandle.getIdentifier();
        String genericPackageName = mPackageInfoHandler.getNameForUserPackage(packageName, userId);
        if (genericPackageName == null) {
            throw new IllegalArgumentException("Package '" + packageName + "' not found");
        }
        String key = getUserPackageUniqueId(userId, genericPackageName);
        PackageResourceUsage usage;
        synchronized (mLock) {
            // When the queried package is not cached in {@link mUsageByUserPackage}, the set API
            // will update the killable state even when the package should never be killed.
            // But the get API will return the correct killable state. This behavior is tolerable
            // because in production the set API should be called only after the get API.
            // For instance, when this case happens by mistake and the package overuses resource
            // between the set and the get API calls, the daemon will provide correct killable
            // state when pushing the latest stats. Ergo, the invalid killable state doesn't have
            // any effect.
            usage = mUsageByUserPackage.get(key);
            if (usage == null) {
                usage = new PackageResourceUsage(userId, genericPackageName,
                        getDefaultKillableStateLocked(genericPackageName));
            }
            if (!usage.verifyAndSetKillableState(isKillable, mTimeSource.getCurrentDate())) {
                Slogf.e(TAG, "User %d cannot set killable state for package '%s'",
                        userHandle.getIdentifier(), genericPackageName);
                throw new IllegalArgumentException("Package killable state is not updatable");
            }
            mUsageByUserPackage.put(key, usage);
        }
        if (!isKillable) {
            int uid = getOrFetchUid(usage, packageName);
            enablePackageForUser(uid, usage.genericPackageName);
        }
        mWatchdogStorage.markDirty();
        if (DEBUG) {
            Slogf.d(TAG, "Successfully set killable package state for user %d", userId);
        }
    }

    private void setPackageKillableStateForAllUsers(String packageName, boolean isKillable) {
        int[] userIds = getAliveUserIds();
        String genericPackageName = null;
        List<PackageResourceUsage> updatedUsages = new ArrayList<>(userIds.length);
        synchronized (mLock) {
            for (int i = 0; i < userIds.length; i++) {
                int userId = userIds[i];
                String name = mPackageInfoHandler.getNameForUserPackage(packageName, userId);
                if (name == null) {
                    continue;
                }
                genericPackageName = name;
                String key = getUserPackageUniqueId(userId, genericPackageName);
                PackageResourceUsage usage = mUsageByUserPackage.get(key);
                if (usage == null) {
                    continue;
                }
                if (!usage.verifyAndSetKillableState(isKillable, mTimeSource.getCurrentDate())) {
                    Slogf.e(TAG, "Cannot set killable state for package '%s'", packageName);
                    throw new IllegalArgumentException(
                            "Package killable state is not updatable");
                }
                updatedUsages.add(usage);
            }
            if (genericPackageName != null) {
                if (!isKillable) {
                    mDefaultNotKillableGenericPackages.add(genericPackageName);
                } else {
                    mDefaultNotKillableGenericPackages.remove(genericPackageName);
                }
                mWatchdogStorage.markDirty();
            }
        }
        // Enabling user packages requires accessing package manager which requires making binder
        // calls. Binder calls should not be made while holding a lock, given it might lead to
        // deadlock. Hence, enabling packages after the synchronized block.
        if (!isKillable) {
            for (int i = 0; i < updatedUsages.size(); i++) {
                PackageResourceUsage usage = updatedUsages.get(i);
                int uid = getOrFetchUid(usage, packageName);
                enablePackageForUser(uid, usage.genericPackageName);
            }
        }
        if (DEBUG) {
            Slogf.d(TAG, "Successfully set killable package state for all users");
        }
    }

    /** Returns the list of package killable states on resource overuse for the user. */
    @NonNull
    public List<PackageKillableState> getPackageKillableStatesAsUser(UserHandle userHandle) {
        Objects.requireNonNull(userHandle, "User handle must be non-null");
        PackageManager pm = mContext.getPackageManager();
        if (!userHandle.equals(UserHandle.ALL)) {
            if (DEBUG) {
                Slogf.d(TAG, "Returning all package killable states for user %d",
                        userHandle.getIdentifier());
            }
            return getPackageKillableStatesForUserId(userHandle.getIdentifier(), pm);
        }
        List<PackageKillableState> packageKillableStates = new ArrayList<>();
        int[] userIds = getAliveUserIds();
        for (int i = 0; i < userIds.length; ++i) {
            packageKillableStates.addAll(
                    getPackageKillableStatesForUserId(userIds[i], pm));
        }
        if (DEBUG) {
            Slogf.d(TAG, "Returning all package killable states for all users");
        }
        return packageKillableStates;
    }

    private List<PackageKillableState> getPackageKillableStatesForUserId(int userId,
            PackageManager pm) {
        List<PackageInfo> packageInfos = pm.getInstalledPackagesAsUser(/* flags= */ 0, userId);
        List<PackageKillableState> states = new ArrayList<>();
        synchronized (mLock) {
            ArrayMap<String, List<ApplicationInfo>> applicationInfosBySharedPackage =
                    new ArrayMap<>();
            for (int i = 0; i < packageInfos.size(); ++i) {
                PackageInfo packageInfo = packageInfos.get(i);
                String genericPackageName = mPackageInfoHandler.getNameForPackage(packageInfo);
                if (packageInfo.sharedUserId == null) {
                    int componentType = mPackageInfoHandler.getComponentType(
                            packageInfo.applicationInfo);
                    int killableState = getPackageKillableStateForUserPackageLocked(
                            userId, genericPackageName, componentType,
                            mOveruseConfigurationCache.isSafeToKill(
                                    genericPackageName, componentType, /* sharedPackages= */null));
                    states.add(new PackageKillableState(packageInfo.packageName, userId,
                            killableState));
                    continue;
                }
                List<ApplicationInfo> applicationInfos =
                        applicationInfosBySharedPackage.get(genericPackageName);
                if (applicationInfos == null) {
                    applicationInfos = new ArrayList<>();
                }
                applicationInfos.add(packageInfo.applicationInfo);
                applicationInfosBySharedPackage.put(genericPackageName, applicationInfos);
            }
            for (Map.Entry<String, List<ApplicationInfo>> entry :
                    applicationInfosBySharedPackage.entrySet()) {
                String genericPackageName = entry.getKey();
                List<ApplicationInfo> applicationInfos = entry.getValue();
                int componentType = mPackageInfoHandler.getSharedComponentType(
                        applicationInfos, genericPackageName);
                List<String> packageNames = new ArrayList<>(applicationInfos.size());
                for (int i = 0; i < applicationInfos.size(); ++i) {
                    packageNames.add(applicationInfos.get(i).packageName);
                }
                int killableState = getPackageKillableStateForUserPackageLocked(
                        userId, genericPackageName, componentType,
                        mOveruseConfigurationCache.isSafeToKill(
                                genericPackageName, componentType, packageNames));
                for (int i = 0; i < applicationInfos.size(); ++i) {
                    states.add(new PackageKillableState(
                            applicationInfos.get(i).packageName, userId, killableState));
                }
            }
        }
        if (DEBUG) {
            Slogf.d(TAG, "Returning the package killable states for user packages");
        }
        return states;
    }

    /** Sets the given resource overuse configurations. */
    @CarWatchdogManager.ReturnCode
    public int setResourceOveruseConfigurations(
            List<ResourceOveruseConfiguration> configurations,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag)
            throws RemoteException {
        Objects.requireNonNull(configurations, "Configurations must be non-null");
        Preconditions.checkArgument((configurations.size() > 0),
                "Must provide at least one configuration");
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        checkResourceOveruseConfigs(configurations, resourceOveruseFlag);
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> internalConfigs =
                new ArrayList<>();
        for (int i = 0; i < configurations.size(); ++i) {
            internalConfigs.add(toInternalResourceOveruseConfiguration(configurations.get(i),
                    resourceOveruseFlag));
        }
        synchronized (mLock) {
            if (!mIsConnectedToDaemon) {
                setPendingSetResourceOveruseConfigurationsRequestLocked(internalConfigs);
                return CarWatchdogManager.RETURN_CODE_SUCCESS;
            }
            /* Verify no pending request in progress. */
            setPendingSetResourceOveruseConfigurationsRequestLocked(null);
        }
        return setResourceOveruseConfigurationsInternal(internalConfigs,
                /* isPendingRequest= */ false);
    }

    /** Returns the available resource overuse configurations. */
    @NonNull
    public List<ResourceOveruseConfiguration> getResourceOveruseConfigurations(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag) {
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        if (!isConnectedToDaemon()) {
            throw new IllegalStateException("Car watchdog daemon is not connected");
        }
        synchronized (mLock) {
            /* Verify no pending request in progress. */
            setPendingSetResourceOveruseConfigurationsRequestLocked(null);
        }
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> internalConfigs =
                new ArrayList<>();
        try {
            internalConfigs = mCarWatchdogDaemonHelper.getResourceOveruseConfigurations();
        } catch (RemoteException | RuntimeException e) {
            Slogf.w(TAG, e, "Failed to fetch resource overuse configurations");
            throw new IllegalStateException(e);
        }
        List<ResourceOveruseConfiguration> configs = new ArrayList<>();
        for (int i = 0; i < internalConfigs.size(); ++i) {
            configs.add(
                    toResourceOveruseConfiguration(internalConfigs.get(i), resourceOveruseFlag));
        }
        if (DEBUG) {
            Slogf.d(TAG, "Returning the resource overuse configuration");
        }
        return configs;
    }

    /** Processes the latest I/O overuse stats */
    public void latestIoOveruseStats(List<PackageIoOveruseStats> packageIoOveruseStats) {
        // Long running operation, such as DB operations, must not be performed on binder threads,
        // even if they are one way binder call, because it may block other one way binder threads.
        // Hence, we handle the latest I/O overuse stats on the service handler thread.
        mServiceHandler.post(() -> latestIoOveruseStatsInternal(packageIoOveruseStats));
    }

    private void latestIoOveruseStatsInternal(List<PackageIoOveruseStats> packageIoOveruseStats) {
        int[] uids = new int[packageIoOveruseStats.size()];
        for (int i = 0; i < packageIoOveruseStats.size(); ++i) {
            uids[i] = packageIoOveruseStats.get(i).uid;
        }
        SparseArray<String> genericPackageNamesByUid = mPackageInfoHandler.getNamesForUids(uids);
        ArraySet<String> overusingUserPackageKeys = new ArraySet<>();
        checkAndHandleDateChange();
        if (genericPackageNamesByUid.size() > 0) {
            mWatchdogStorage.markDirty();
        }
        synchronized (mLock) {
            for (int i = 0; i < packageIoOveruseStats.size(); ++i) {
                PackageIoOveruseStats stats = packageIoOveruseStats.get(i);
                String genericPackageName = genericPackageNamesByUid.get(stats.uid);
                if (genericPackageName == null) {
                    continue;
                }
                PackageResourceUsage usage = cacheAndFetchUsageLocked(stats.uid, genericPackageName,
                        stats.ioOveruseStats, stats.forgivenWriteBytes);
                if (stats.shouldNotify) {
                    /*
                     * Packages that exceed the warn threshold percentage should be notified as well
                     * and only the daemon is aware of such packages. Thus the flag is used to
                     * indicate which packages should be notified.
                     */
                    ResourceOveruseStats resourceOveruseStats =
                            usage.getResourceOveruseStatsBuilder().setIoOveruseStats(
                                    usage.getIoOveruseStats()).build();
                    notifyResourceOveruseStatsLocked(stats.uid, resourceOveruseStats);
                }
                if (!usage.ioUsage.exceedsThreshold()) {
                    continue;
                }
                overusingUserPackageKeys.add(usage.getUniqueId());
                if (usage.getKillableState() == KILLABLE_STATE_NEVER) {
                    continue;
                }
                if (usage.ioUsage.getNotForgivenOveruses() > mRecurringOveruseTimes) {
                    String id = usage.getUniqueId();
                    mActionableUserPackages.add(id);
                    mUserNotifiablePackages.add(id);
                    usage.ioUsage.forgiveOveruses();
                }
            }
            if ((mCurrentUxState != UX_STATE_NO_DISTRACTION && !mUserNotifiablePackages.isEmpty())
                    // TODO(b/200599130): When resource overusing background apps are killed
                    //  immediately, update the below check to allow posting
                    //  {@code performOveruseHandlingLocked} immediately.
                    || (mCurrentUxState == UX_STATE_NO_INTERACTION
                    && !mActionableUserPackages.isEmpty())) {
                mMainHandler.postDelayed(() -> {
                    synchronized (mLock) {
                        performOveruseHandlingLocked();
                    }}, mOveruseHandlingDelayMills);
            }
        }
        if (!overusingUserPackageKeys.isEmpty()) {
            pushIoOveruseMetrics(overusingUserPackageKeys);
        }
        if (DEBUG) {
            Slogf.d(TAG, "Processed latest I/O overuse stats");
        }
    }

    /** Resets the resource overuse settings and stats for the given generic package names. */
    public void resetResourceOveruseStats(Set<String> genericPackageNames) {
        mServiceHandler.post(() -> {
            synchronized (mLock) {
                mIsHeadsUpNotificationSent = false;
                for (int i = 0; i < mUsageByUserPackage.size(); ++i) {
                    PackageResourceUsage usage = mUsageByUserPackage.valueAt(i);
                    if (!genericPackageNames.contains(usage.genericPackageName)) {
                        continue;
                    }
                    usage.resetStats();
                    usage.verifyAndSetKillableState(/* isKillable= */ true,
                            mTimeSource.getCurrentDate());
                    mWatchdogStorage.deleteUserPackage(usage.userId, usage.genericPackageName);
                    mActionableUserPackages.remove(usage.getUniqueId());
                    Slogf.i(TAG,
                            "Reset resource overuse settings and stats for user '%d' package '%s'",
                            usage.userId, usage.genericPackageName);
                    if (usage.isSharedPackage() && usage.getUid() == INVALID_UID) {
                        // Only enable packages that were disabled by the watchdog service. Ergo, if
                        // the usage doesn't have a valid UID, the package was not recently disabled
                        // by the watchdog service (unless the service crashed) and can be safely
                        // skipped.
                        Slogf.e(TAG, "Skipping enabling user %d's package %s", usage.userId,
                                usage.genericPackageName);
                        continue;
                    }
                    enablePackageForUser(usage.getUid(), usage.genericPackageName);
                }
            }
        });
    }

    /**
     * Asynchronously fetches today's I/O usage stats for all packages collected during the
     * previous boot and sends them to the CarWatchdog daemon.
     */
    public void asyncFetchTodayIoUsageStats() {
        mServiceHandler.post(() -> {
            List<UserPackageIoUsageStats> todayIoUsageStats = getTodayIoUsageStats();
            try {
                mCarWatchdogDaemonHelper.onTodayIoUsageStatsFetched(todayIoUsageStats);
            } catch (RemoteException e) {
                Slogf.w(TAG, e, "Failed to send today's I/O usage stats to daemon.");
            }
        });
    }

    /** Returns today's I/O usage stats for all packages collected during the previous boot. */
    public List<UserPackageIoUsageStats> getTodayIoUsageStats() {
        List<UserPackageIoUsageStats> userPackageIoUsageStats = new ArrayList<>();
        List<WatchdogStorage.IoUsageStatsEntry> entries = mWatchdogStorage.getTodayIoUsageStats();
        for (int i = 0; i < entries.size(); ++i) {
            WatchdogStorage.IoUsageStatsEntry entry = entries.get(i);
            UserPackageIoUsageStats stats = new UserPackageIoUsageStats();
            stats.userId = entry.userId;
            stats.packageName = entry.packageName;
            stats.ioUsageStats = new IoUsageStats();
            android.automotive.watchdog.IoOveruseStats internalIoUsage =
                    entry.ioUsage.getInternalIoOveruseStats();
            stats.ioUsageStats.writtenBytes = internalIoUsage.writtenBytes;
            stats.ioUsageStats.forgivenWriteBytes = entry.ioUsage.getForgivenWriteBytes();
            stats.ioUsageStats.totalOveruses = internalIoUsage.totalOveruses;
            userPackageIoUsageStats.add(stats);
        }
        return userPackageIoUsageStats;
    }

    /** Deletes all data for specific user. */
    public void deleteUser(@UserIdInt int userId) {
        synchronized (mLock) {
            for (int i = mUsageByUserPackage.size() - 1; i >= 0; --i) {
                if (userId == mUsageByUserPackage.valueAt(i).userId) {
                    mUsageByUserPackage.removeAt(i);
                }
            }
            mWatchdogStorage.syncUsers(getAliveUserIds());
        }
        if (DEBUG) {
            Slogf.d(TAG, "Resource usage for user id: %d was deleted.", userId);
        }
    }

    /** Handles intents from user notification actions. */
    public void processUserNotificationIntent(Intent intent) {
        String action = intent.getAction();
        String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
        int notificationId = intent.getIntExtra(INTENT_EXTRA_NOTIFICATION_ID, -1);
        if (packageName == null || packageName.isEmpty() || userHandle == null
                || userHandle.getIdentifier() < 0) {
            Slogf.w(TAG, "Invalid package '%s' or userHandle '%s' received in the intent",
                    packageName, userHandle);
            return;
        }
        switch (action) {
            case CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP:
                disablePackageForUser(packageName, userHandle.getIdentifier());
                if (DEBUG) {
                    Slogf.d(TAG,
                            "Handled user notification action to disable package %s for user %s",
                            packageName, userHandle);
                }
                break;
            case CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS:
                Intent settingsIntent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + packageName))
                        .setFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
                mBuiltinPackageContext.startActivityAsUser(settingsIntent, userHandle);
                if (DEBUG) {
                    Slogf.d(TAG, "Handled user notification action to launch settings app for "
                            + "package %s and user %s", packageName, userHandle);
                }
                break;
            case CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION:
                break;
            default:
                Slogf.e(TAG, "Skipping invalid user notification intent action: %s", action);
                return;
        }

        if (notificationId == -1) {
            Slogf.e(TAG, "Didn't received user notification id in action %s", action);
            return;
        }

        int maxNotificationId =
                mResourceOveruseNotificationBaseId + mResourceOveruseNotificationMaxOffset - 1;
        if (notificationId < mResourceOveruseNotificationBaseId
                || notificationId > maxNotificationId) {
            Slogf.e(TAG, "Notification id (%d) outside of reserved IDs (%d - %d) for car watchdog.",
                    notificationId, mResourceOveruseNotificationBaseId, maxNotificationId);
            return;
        }

        synchronized (mLock) {
            String uniqueUserPackageId = mActiveUserNotificationsByNotificationId.get(
                    notificationId);
            if (uniqueUserPackageId != null
                    && uniqueUserPackageId.equals(getUserPackageUniqueId(userHandle.getIdentifier(),
                    packageName))) {
                mActiveUserNotificationsByNotificationId.remove(notificationId);
                mActiveUserNotifications.remove(uniqueUserPackageId);
            }
        }

        cancelNotificationAsUser(notificationId, userHandle);
        if (DEBUG) {
            Slogf.d(TAG, "Successfully canceled notification id %d for user %s and package %s",
                    notificationId, userHandle, packageName);
        }
    }

    /** Handles when system broadcast package changed action */
    public void processPackageChangedIntent(Intent intent) {
        int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, USER_NULL);
        if (userId == USER_NULL) {
            Slogf.w(TAG, "Skipping package changed action with USER_NULL user");
            return;
        }
        String packageName = intent.getData().getSchemeSpecificPart();
        try {
            if (PackageManagerHelper.getApplicationEnabledSettingForUser(packageName, userId)
                    != COMPONENT_ENABLED_STATE_ENABLED) {
                return;
            }
        } catch (Exception e) {
            // Catch IllegalArgumentException thrown by PackageManager when the package
            // is not found. CarWatchdogService shouldn't crash when the package
            // no longer exists when the {@link ACTION_PACKAGE_CHANGED} broadcast is
            // handled.
            Slogf.e(TAG, e,
                    "Failed to verify enabled setting for user %d, package '%s'",
                    userId, packageName);
            return;
        }
        synchronized (mLock) {
            ArraySet<String> disabledPackages = mDisabledUserPackagesByUserId.get(userId);
            if (disabledPackages == null || !disabledPackages.contains(packageName)) {
                return;
            }
            removeFromDisabledPackagesSettingsStringLocked(packageName, userId);
            disabledPackages.remove(packageName);
            if (disabledPackages.isEmpty()) {
                mDisabledUserPackagesByUserId.remove(userId);
            }
        }
        if (DEBUG) {
            Slogf.d(TAG, "Successfully enabled package due to package changed action");
        }
    }

    /** Disables a package for specific user until used. */
    public boolean disablePackageForUser(String packageName, @UserIdInt int userId) {
        synchronized (mLock) {
            ArraySet<String> disabledPackages = mDisabledUserPackagesByUserId.get(userId);
            if (disabledPackages != null && disabledPackages.contains(packageName)) {
                return true;
            }
        }
        try {
            int currentEnabledState =
                    PackageManagerHelper.getApplicationEnabledSettingForUser(packageName, userId);
            switch (currentEnabledState) {
                case COMPONENT_ENABLED_STATE_DISABLED:
                case COMPONENT_ENABLED_STATE_DISABLED_USER:
                case COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                    Slogf.w(TAG, "Unable to disable application for user %d, package '%s' as the "
                            + "current enabled state is %s", userId, packageName,
                            toEnabledStateString(currentEnabledState));
                    return false;
                default:
                    // COMPONENT_ENABLED_STATE_DEFAULT or other non-disabled states.
                    break;
            }
            PackageManagerHelper.setApplicationEnabledSettingForUser(packageName,
                    COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, /* flags= */ 0, userId,
                    mContext.getPackageName());
            synchronized (mLock) {
                ArraySet<String> disabledPackages = mDisabledUserPackagesByUserId.get(userId);
                if (disabledPackages == null) {
                    disabledPackages = new ArraySet<>(1);
                }
                appendToDisabledPackagesSettingsString(packageName, userId);
                disabledPackages.add(packageName);
                mDisabledUserPackagesByUserId.put(userId, disabledPackages);
            }
            Slogf.i(TAG, "Disabled package '%s' on user %d until used due to resource overuse",
                    packageName, userId);
        } catch (Exception e) {
            Slogf.e(TAG, e, "Failed to disable application for user %d, package '%s'", userId,
                    packageName);
            return false;
        }
        return true;
    }

    /**
     * Sets the delay to handle resource overuse after the package is notified of resource overuse.
     */
    public void setOveruseHandlingDelay(long millis) {
        synchronized (mLock) {
            mOveruseHandlingDelayMills = millis;
        }
    }

    /** Writes to watchdog metadata file. */
    public void writeMetadataFile() {
        ZonedDateTime systemIoUsageSummaryReportDate;
        ZonedDateTime uidIoUsageSummaryReportDate;
        synchronized (mLock) {
            if (mLastSystemIoUsageSummaryReportedDate == null
                    && mLastUidIoUsageSummaryReportedDate == null) {
                return;
            }
            systemIoUsageSummaryReportDate = mLastSystemIoUsageSummaryReportedDate;
            uidIoUsageSummaryReportDate = mLastUidIoUsageSummaryReportedDate;
        }
        File file = getWatchdogMetadataFile();
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream fos = null;
        try {
            fos = atomicFile.startWrite();
            try (JsonWriter jsonWriter =
                         new JsonWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
                jsonWriter.beginObject();
                if (systemIoUsageSummaryReportDate != null) {
                    jsonWriter.name(SYSTEM_IO_USAGE_SUMMARY_REPORTED_DATE)
                            .value(systemIoUsageSummaryReportDate
                                    .format(DateTimeFormatter.ISO_DATE_TIME));
                }
                if (uidIoUsageSummaryReportDate != null) {
                    jsonWriter.name(UID_IO_USAGE_SUMMARY_REPORTED_DATE)
                            .value(uidIoUsageSummaryReportDate
                                    .format(DateTimeFormatter.ISO_DATE_TIME));
                }
                jsonWriter.endObject();
            }
            atomicFile.finishWrite(fos);
            if (DEBUG) {
                Slogf.e(TAG, "Successfully wrote watchdog metadata file '%s'",
                        file.getAbsoluteFile());
            }
        } catch (IOException e) {
            Slogf.e(TAG, e, "Failed to write watchdog metadata file '%s'", file.getAbsoluteFile());
            atomicFile.failWrite(fos);
        }
    }

    /** Fetches and syncs the resource overuse configurations from watchdog daemon. */
    private void fetchAndSyncResourceOveruseConfigurations() {
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> internalConfigs;
        try {
            internalConfigs = mCarWatchdogDaemonHelper.getResourceOveruseConfigurations();
        } catch (RemoteException | RuntimeException e) {
            Slogf.w(TAG, e, "Failed to fetch resource overuse configurations");
            return;
        }
        if (internalConfigs.isEmpty()) {
            Slogf.e(TAG, "Fetched resource overuse configurations are empty");
            return;
        }
        mOveruseConfigurationCache.set(internalConfigs);
        mPackageInfoHandler.setVendorPackagePrefixes(
                mOveruseConfigurationCache.getVendorPackagePrefixes());
        if (DEBUG) {
            Slogf.d(TAG, "Fetched and synced resource overuse configs.");
        }
    }

    private void readFromDatabase() {
        mWatchdogStorage.syncUsers(getAliveUserIds());
        List<WatchdogStorage.UserPackageSettingsEntry> settingsEntries =
                mWatchdogStorage.getUserPackageSettings();
        Slogf.i(TAG, "Read %d user package settings from database", settingsEntries.size());
        // Get date before |WatchdogStorage.getTodayIoUsageStats| such that if date changes between
        // call to database and caching of the date, future calls to |latestIoOveruseStats| will
        // catch the change and sync the database with the in-memory cache.
        ZonedDateTime curReportDate = mTimeSource.getCurrentDate();
        Instant killableStateResetDate =
                curReportDate.minusDays(mPackageKillableStateResetDays).toInstant();
        List<WatchdogStorage.IoUsageStatsEntry> ioStatsEntries =
                mWatchdogStorage.getTodayIoUsageStats();
        Slogf.i(TAG, "Read %d I/O usage stats from database", ioStatsEntries.size());
        synchronized (mLock) {
            for (int i = 0; i < settingsEntries.size(); i++) {
                WatchdogStorage.UserPackageSettingsEntry entry = settingsEntries.get(i);
                if (entry.userId == UserHandle.ALL.getIdentifier()) {
                    if (entry.killableState != KILLABLE_STATE_YES) {
                        mDefaultNotKillableGenericPackages.add(entry.packageName);
                    }
                    continue;
                }
                String key = getUserPackageUniqueId(entry.userId, entry.packageName);
                PackageResourceUsage usage = mUsageByUserPackage.get(key);
                if (usage == null) {
                    usage = new PackageResourceUsage(entry.userId, entry.packageName,
                            getDefaultKillableStateLocked(entry.packageName));
                }
                int killableState = entry.killableState;
                Instant lastModifiedDate =
                        Instant.ofEpochSecond(entry.killableStateLastModifiedEpochSeconds);
                ZonedDateTime usageModifiedDate = lastModifiedDate.atZone(ZONE_OFFSET);
                if (killableState == KILLABLE_STATE_NO
                        && lastModifiedDate.compareTo(killableStateResetDate) <= 0) {
                    killableState = KILLABLE_STATE_YES;
                    usageModifiedDate = curReportDate;
                    mWatchdogStorage.markDirty();
                    Slogf.i(TAG, "Reset killable state for package %s for user %d",
                            entry.packageName, entry.userId);
                }
                usage.setKillableState(killableState, usageModifiedDate);
                mUsageByUserPackage.put(key, usage);
            }
            for (int i = 0; i < ioStatsEntries.size(); ++i) {
                WatchdogStorage.IoUsageStatsEntry entry = ioStatsEntries.get(i);
                String key = getUserPackageUniqueId(entry.userId, entry.packageName);
                PackageResourceUsage usage = mUsageByUserPackage.get(key);
                if (usage == null) {
                    usage = new PackageResourceUsage(entry.userId, entry.packageName,
                            getDefaultKillableStateLocked(entry.packageName));
                }
                /* Overwrite in memory cache as the stats will be merged on the daemon side and
                 * pushed on the next latestIoOveruseStats call. This is tolerable because the next
                 * push should happen soon.
                 */
                usage.ioUsage.overwrite(entry.ioUsage);
                mUsageByUserPackage.put(key, usage);
            }
            mLatestStatsReportDate = curReportDate;
        }
        syncHistoricalNotForgivenOveruses();
    }

    /** Fetches all historical not forgiven overuses and syncs them with package I/O usages. */
    private void syncHistoricalNotForgivenOveruses() {
        List<WatchdogStorage.NotForgivenOverusesEntry> notForgivenOverusesEntries =
                mWatchdogStorage.getNotForgivenHistoricalIoOveruses(mRecurringOverusePeriodInDays);
        Slogf.i(TAG, "Read %d not forgiven overuse stats from database",
                notForgivenOverusesEntries.size());
        synchronized (mLock) {
            for (int i = 0; i < notForgivenOverusesEntries.size(); i++) {
                WatchdogStorage.NotForgivenOverusesEntry entry = notForgivenOverusesEntries.get(i);
                String key = getUserPackageUniqueId(entry.userId, entry.packageName);
                PackageResourceUsage usage = mUsageByUserPackage.get(key);
                if (usage == null) {
                    usage = new PackageResourceUsage(entry.userId, entry.packageName,
                            getDefaultKillableStateLocked(entry.packageName));
                }
                usage.ioUsage.setHistoricalNotForgivenOveruses(entry.notForgivenOveruses);
                mUsageByUserPackage.put(key, usage);
            }
        }
    }

    /**
     * Writes user package settings and stats to database. If database is marked as clean,
     * no writing is executed.
     */
    public void writeToDatabase() {
        if (!mWatchdogStorage.startWrite()) {
            return;
        }
        try {
            List<WatchdogStorage.UserPackageSettingsEntry> userPackageSettingsEntries =
                    new ArrayList<>();
            List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = new ArrayList<>();
            SparseArray<List<String>> forgivePackagesByUserId = new SparseArray<>();
            synchronized (mLock) {
                for (int i = 0; i < mUsageByUserPackage.size(); i++) {
                    PackageResourceUsage usage = mUsageByUserPackage.valueAt(i);
                    userPackageSettingsEntries.add(new WatchdogStorage.UserPackageSettingsEntry(
                            usage.userId, usage.genericPackageName, usage.getKillableState(),
                            usage.getKillableStateLastModifiedDate().toEpochSecond()));
                    if (!usage.ioUsage.hasUsage()) {
                        continue;
                    }
                    if (usage.ioUsage.shouldForgiveHistoricalOveruses()) {
                        List<String> packagesToForgive = forgivePackagesByUserId.get(usage.userId);
                        if (packagesToForgive == null) {
                            packagesToForgive = new ArrayList<>();
                        }
                        packagesToForgive.add(usage.genericPackageName);
                        forgivePackagesByUserId.put(usage.userId, packagesToForgive);
                    }
                    ioUsageStatsEntries.add(new WatchdogStorage.IoUsageStatsEntry(usage.userId,
                            usage.genericPackageName, usage.ioUsage));
                }
                for (String packageName : mDefaultNotKillableGenericPackages) {
                    // TODO(b/235615155): Update database when a default not killable package is
                    //  set to killable. Also, changes to mDefaultNotKillableGenericPackages should
                    //  be tracked by the last modified date and the date should be written to the
                    //  database.
                    userPackageSettingsEntries.add(new WatchdogStorage.UserPackageSettingsEntry(
                            UserHandle.ALL.getIdentifier(), packageName, KILLABLE_STATE_NO,
                            mTimeSource.getCurrentDate().toEpochSecond()));
                }
            }
            boolean userPackageSettingResult =
                    mWatchdogStorage.saveUserPackageSettings(userPackageSettingsEntries);
            if (!userPackageSettingResult) {
                Slogf.e(TAG, "Failed to write user package settings to database");
            } else {
                Slogf.i(TAG, "Successfully saved %d user package settings to database",
                        userPackageSettingsEntries.size());
            }
            if (writeStats(ioUsageStatsEntries, forgivePackagesByUserId)
                    && userPackageSettingResult) {
                mWatchdogStorage.markWriteSuccessful();
            }
        } finally {
            mWatchdogStorage.endWrite();
        }
    }

    @GuardedBy("mLock")
    private @KillableState int getDefaultKillableStateLocked(String genericPackageName) {
        return mDefaultNotKillableGenericPackages.contains(genericPackageName)
                ? KILLABLE_STATE_NO : KILLABLE_STATE_YES;
    }

    private boolean writeStats(List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries,
            SparseArray<List<String>> forgivePackagesByUserId) {
        // Forgive historical overuses before writing the latest stats to disk to avoid forgiving
        // the latest stats when the write is triggered after date change.
        if (forgivePackagesByUserId.size() != 0) {
            mWatchdogStorage.forgiveHistoricalOveruses(forgivePackagesByUserId,
                    mRecurringOverusePeriodInDays);
            Slogf.i(TAG, "Attempted to forgive historical overuses for %d users.",
                    forgivePackagesByUserId.size());
        }
        if (ioUsageStatsEntries.isEmpty()) {
            return true;
        }
        int result = mWatchdogStorage.saveIoUsageStats(ioUsageStatsEntries);
        if (result == WatchdogStorage.FAILED_TRANSACTION) {
            Slogf.e(TAG, "Failed to write %d I/O overuse stats to database",
                    ioUsageStatsEntries.size());
        } else {
            Slogf.i(TAG, "Successfully saved %d/%d I/O overuse stats to database",
                    result, ioUsageStatsEntries.size());
        }
        return result != WatchdogStorage.FAILED_TRANSACTION;
    }

    @GuardedBy("mLock")
    private void applyCurrentUxRestrictionsLocked() {
        if (mCurrentUxRestrictions == null
                || mCurrentUxRestrictions.isRequiresDistractionOptimization()) {
            mCurrentUxState = UX_STATE_NO_DISTRACTION;
            return;
        }
        if (mCurrentUxState == UX_STATE_NO_INTERACTION) {
            return;
        }
        mCurrentUxState = UX_STATE_USER_NOTIFICATION;
        performOveruseHandlingLocked();
    }

    @GuardedBy("mLock")
    private int getPackageKillableStateForUserPackageLocked(
            int userId, String genericPackageName, int componentType, boolean isSafeToKill) {
        String key = getUserPackageUniqueId(userId, genericPackageName);
        PackageResourceUsage usage = mUsageByUserPackage.get(key);
        int defaultKillableState = getDefaultKillableStateLocked(genericPackageName);
        if (usage == null) {
            usage = new PackageResourceUsage(userId, genericPackageName, defaultKillableState);
        }
        int killableState = usage.syncAndFetchKillableState(
                componentType, isSafeToKill, defaultKillableState);
        mUsageByUserPackage.put(key, usage);
        mWatchdogStorage.markDirty();
        return killableState;
    }

    @GuardedBy("mLock")
    private void checkAndResetUserPackageKillableStatesLocked() {
        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        Instant killableStateResetDate =
                currentDate.minusDays(mPackageKillableStateResetDays).toInstant();
        for (int i = 0; i < mUsageByUserPackage.size(); i++) {
            PackageResourceUsage usage = mUsageByUserPackage.valueAt(i);
            Instant lastModifiedDate =
                    usage.getKillableStateLastModifiedDate().toInstant();
            if (usage.getKillableState() != KILLABLE_STATE_NO
                    || lastModifiedDate.compareTo(killableStateResetDate) > 0) {
                continue;
            }
            usage.verifyAndSetKillableState(/* isKillable= */ true, currentDate);
            mWatchdogStorage.markDirty();
            Slogf.i(TAG, "Reset killable state for package %s for user %d",
                    usage.genericPackageName, usage.userId);
        }
    }

    @GuardedBy("mLock")
    private void notifyResourceOveruseStatsLocked(int uid,
            ResourceOveruseStats resourceOveruseStats) {
        String genericPackageName = resourceOveruseStats.getPackageName();
        ArrayList<ResourceOveruseListenerInfo> listenerInfos = mOveruseListenerInfosByUid.get(uid);
        if (listenerInfos != null) {
            for (int i = 0; i < listenerInfos.size(); ++i) {
                listenerInfos.get(i).notifyListener(
                        FLAG_RESOURCE_OVERUSE_IO, uid, genericPackageName, resourceOveruseStats);
            }
        }
        for (int i = 0; i < mOveruseSystemListenerInfosByUid.size(); ++i) {
            ArrayList<ResourceOveruseListenerInfo> systemListenerInfos =
                    mOveruseSystemListenerInfosByUid.valueAt(i);
            for (int j = 0; j < systemListenerInfos.size(); ++j) {
                systemListenerInfos.get(j).notifyListener(
                        FLAG_RESOURCE_OVERUSE_IO, uid, genericPackageName, resourceOveruseStats);
            }
        }
        if (DEBUG) {
            Slogf.d(TAG, "Notified resource overuse stats to listening applications");
        }
    }

    private void checkAndHandleDateChange() {
        synchronized (mLock) {
            ZonedDateTime currentDate = mTimeSource.getCurrentDate();
            if (currentDate.equals(mLatestStatsReportDate)) {
                return;
            }
            mLatestStatsReportDate = currentDate;
            checkAndResetUserPackageKillableStatesLocked();
        }
        writeToDatabase();
        synchronized (mLock) {
            for (int i = 0; i < mUsageByUserPackage.size(); i++) {
                mUsageByUserPackage.valueAt(i).resetStats();
            }
        }
        syncHistoricalNotForgivenOveruses();
        if (DEBUG) {
            Slogf.d(TAG, "Handled date change successfully");
        }
    }

    @GuardedBy("mLock")
    private PackageResourceUsage cacheAndFetchUsageLocked(int uid, String genericPackageName,
            android.automotive.watchdog.IoOveruseStats internalStats,
            android.automotive.watchdog.PerStateBytes forgivenWriteBytes) {
        int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
        String key = getUserPackageUniqueId(userId, genericPackageName);
        int defaultKillableState = getDefaultKillableStateLocked(genericPackageName);
        PackageResourceUsage usage = mUsageByUserPackage.get(key);
        if (usage == null) {
            usage = new PackageResourceUsage(userId, genericPackageName, defaultKillableState);
        }
        usage.update(uid, internalStats, forgivenWriteBytes, defaultKillableState);
        mUsageByUserPackage.put(key, usage);
        return usage;
    }

    private IoOveruseStats getIoOveruseStatsForPeriod(int userId, String genericPackageName,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        synchronized (mLock) {
            String key = getUserPackageUniqueId(userId, genericPackageName);
            PackageResourceUsage usage = mUsageByUserPackage.get(key);
            if (usage == null) {
                return null;
            }
            return getIoOveruseStatsLocked(usage, /* minimumBytesWritten= */ 0, maxStatsPeriod);
        }
    }

    @GuardedBy("mLock")
    private IoOveruseStats getIoOveruseStatsLocked(PackageResourceUsage usage,
            long minimumBytesWritten, @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        if (!usage.ioUsage.hasUsage()) {
            /* Return I/O overuse stats only when the package has usage for the current day.
             * Without the current day usage, the returned stats will contain zero remaining
             * bytes, which is incorrect.
             */
            return null;
        }
        IoOveruseStats currentStats = usage.getIoOveruseStats();
        long totalBytesWritten = currentStats.getTotalBytesWritten();
        int numDays = toNumDays(maxStatsPeriod);
        IoOveruseStats historyStats = null;
        if (numDays > 0) {
            historyStats = mWatchdogStorage.getHistoricalIoOveruseStats(
                    usage.userId, usage.genericPackageName, numDays - 1);
            totalBytesWritten += historyStats != null ? historyStats.getTotalBytesWritten() : 0;
        }
        if (totalBytesWritten < minimumBytesWritten) {
            return null;
        }
        if (historyStats == null) {
            return currentStats;
        }
        IoOveruseStats.Builder statsBuilder = new IoOveruseStats.Builder(
                historyStats.getStartTime(),
                historyStats.getDurationInSeconds() + currentStats.getDurationInSeconds());
        statsBuilder.setTotalTimesKilled(
                historyStats.getTotalTimesKilled() + currentStats.getTotalTimesKilled());
        statsBuilder.setTotalOveruses(
                historyStats.getTotalOveruses() + currentStats.getTotalOveruses());
        statsBuilder.setTotalBytesWritten(
                historyStats.getTotalBytesWritten() + currentStats.getTotalBytesWritten());
        statsBuilder.setKillableOnOveruse(currentStats.isKillableOnOveruse());
        statsBuilder.setRemainingWriteBytes(currentStats.getRemainingWriteBytes());
        return statsBuilder.build();
    }

    @GuardedBy("mLock")
    private void addResourceOveruseListenerLocked(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull IResourceOveruseListener listener,
            SparseArray<ArrayList<ResourceOveruseListenerInfo>> listenerInfosByUid) {
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        boolean isListenerForSystem = listenerInfosByUid == mOveruseSystemListenerInfosByUid;
        String listenerType = isListenerForSystem ? "resource overuse listener for system" :
                "resource overuse listener";

        IBinder binder = listener.asBinder();
        ArrayList<ResourceOveruseListenerInfo> listenerInfos = listenerInfosByUid.get(callingUid);
        if (listenerInfos == null) {
            listenerInfos = new ArrayList<>();
            listenerInfosByUid.put(callingUid, listenerInfos);
        }
        for (int i = 0; i < listenerInfos.size(); ++i) {
            if (listenerInfos.get(i).listener.asBinder() == binder) {
                throw new IllegalStateException(
                        "Cannot add " + listenerType + " as it is already added");
            }
        }

        ResourceOveruseListenerInfo listenerInfo = new ResourceOveruseListenerInfo(listener,
                resourceOveruseFlag, callingPid, callingUid, isListenerForSystem);
        try {
            listenerInfo.linkToDeath();
        } catch (RemoteException e) {
            Slogf.w(TAG, "Cannot add %s: linkToDeath to listener failed", listenerType);
            return;
        }
        listenerInfos.add(listenerInfo);
        if (DEBUG) {
            Slogf.d(TAG, "The %s (pid: %d, uid: %d) is added", listenerType,
                    callingPid, callingUid);
        }
    }

    @GuardedBy("mLock")
    private void removeResourceOveruseListenerLocked(@NonNull IResourceOveruseListener listener,
            SparseArray<ArrayList<ResourceOveruseListenerInfo>> listenerInfosByUid) {
        int callingUid = Binder.getCallingUid();
        String listenerType = listenerInfosByUid == mOveruseSystemListenerInfosByUid
                ? "resource overuse system listener" : "resource overuse listener";
        ArrayList<ResourceOveruseListenerInfo> listenerInfos = listenerInfosByUid.get(callingUid);
        if (listenerInfos == null) {
            Slogf.w(TAG, "Cannot remove the %s: it has not been registered before", listenerType);
            return;
        }
        IBinder binder = listener.asBinder();
        ResourceOveruseListenerInfo cachedListenerInfo = null;
        for (int i = 0; i < listenerInfos.size(); ++i) {
            if (listenerInfos.get(i).listener.asBinder() == binder) {
                cachedListenerInfo = listenerInfos.get(i);
                break;
            }
        }
        if (cachedListenerInfo == null) {
            Slogf.w(TAG, "Cannot remove the %s: it has not been registered before", listenerType);
            return;
        }
        cachedListenerInfo.unlinkToDeath();
        listenerInfos.remove(cachedListenerInfo);
        if (listenerInfos.isEmpty()) {
            listenerInfosByUid.remove(callingUid);
        }
        if (DEBUG) {
            Slogf.d(TAG, "The %s (pid: %d, uid: %d) is removed", listenerType,
                    cachedListenerInfo.pid, cachedListenerInfo.uid);
        }
    }

    @GuardedBy("mLock")
    private void setPendingSetResourceOveruseConfigurationsRequestLocked(
            List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> configs) {
        if (mPendingSetResourceOveruseConfigurationsRequest != null) {
            if (mPendingSetResourceOveruseConfigurationsRequest == configs) {
                return;
            }
            throw new IllegalStateException(
                    "Pending setResourceOveruseConfigurations request in progress");
        }
        mPendingSetResourceOveruseConfigurationsRequest = configs;
    }

    private void retryPendingSetResourceOveruseConfigurations() {
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> configs;
        synchronized (mLock) {
            if (mPendingSetResourceOveruseConfigurationsRequest == null) {
                return;
            }
            configs = mPendingSetResourceOveruseConfigurationsRequest;
        }
        try {
            int result = setResourceOveruseConfigurationsInternal(configs,
                    /* isPendingRequest= */ true);
            if (result != CarWatchdogManager.RETURN_CODE_SUCCESS) {
                Slogf.e(TAG, "Failed to set pending resource overuse configurations. Return code "
                        + "%d", result);
            }
        } catch (Exception e) {
            Slogf.e(TAG, e, "Exception on set pending resource overuse configurations");
        }
    }

    private int setResourceOveruseConfigurationsInternal(
            List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> configs,
            boolean isPendingRequest) throws RemoteException {
        boolean doClearPendingRequest = isPendingRequest;
        try {
            mCarWatchdogDaemonHelper.updateResourceOveruseConfigurations(configs);
            mMainHandler.post(this::fetchAndSyncResourceOveruseConfigurations);
        } catch (RemoteException e) {
            if (e instanceof TransactionTooLargeException) {
                throw e;
            }
            Slogf.e(TAG, e, "Remote exception on set resource overuse configuration");
            synchronized (mLock) {
                setPendingSetResourceOveruseConfigurationsRequestLocked(configs);
            }
            doClearPendingRequest = false;
            return CarWatchdogManager.RETURN_CODE_SUCCESS;
        } finally {
            if (doClearPendingRequest) {
                synchronized (mLock) {
                    mPendingSetResourceOveruseConfigurationsRequest = null;
                }
            }
        }
        if (DEBUG) {
            Slogf.d(TAG, "Set the resource overuse configuration successfully");
        }
        return CarWatchdogManager.RETURN_CODE_SUCCESS;
    }

    private boolean isConnectedToDaemon() {
        synchronized (mLock) {
            long startTimeMillis = SystemClock.uptimeMillis();
            long sleptDurationMillis = SystemClock.uptimeMillis() - startTimeMillis;
            while (!mIsConnectedToDaemon && sleptDurationMillis < MAX_WAIT_TIME_MILLS) {
                try {
                    mLock.wait(MAX_WAIT_TIME_MILLS - sleptDurationMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue;
                } finally {
                    sleptDurationMillis = SystemClock.uptimeMillis() - startTimeMillis;
                }
                break;
            }
            return mIsConnectedToDaemon;
        }
    }

    private int[] getAliveUserIds() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        List<UserHandle> aliveUsers = userManager.getUserHandles(/* excludeDying= */ true);
        int userSize = aliveUsers.size();
        int[] userIds = new int[userSize];
        for (int i = 0; i < userSize; ++i) {
            userIds[i] = aliveUsers.get(i).getIdentifier();
        }
        return userIds;
    }

    @GuardedBy("mLock")
    private void performOveruseHandlingLocked() {
        if (mCurrentUxState == UX_STATE_NO_DISTRACTION) {
            return;
        }
        if (!mUserNotifiablePackages.isEmpty()) {
            // Notifications are presented asynchronously, therefore the delay added by posting
            // to the handler should not affect the system behavior.
            mServiceHandler.post(this::notifyUserOnOveruse);
        }
        if (mActionableUserPackages.isEmpty() || mCurrentUxState != UX_STATE_NO_INTERACTION) {
            return;
        }
        ArraySet<String> killedUserPackageKeys = new ArraySet<>();
        for (int i = 0; i < mActionableUserPackages.size(); ++i) {
            PackageResourceUsage usage =
                    mUsageByUserPackage.get(mActionableUserPackages.valueAt(i));
            if (usage == null) {
                continue;
            }
            // Between detecting and handling the overuse, either the package killable state or
            // the resource overuse configuration was updated. So, verify the killable state
            // before proceeding.
            int killableState = usage.getKillableState();
            if (killableState != KILLABLE_STATE_YES) {
                continue;
            }
            List<String> packages;
            if (usage.isSharedPackage()) {
                packages = mPackageInfoHandler.getPackagesForUid(usage.getUid(),
                        usage.genericPackageName);
            } else {
                packages = Collections.singletonList(usage.genericPackageName);
            }
            boolean isKilled = false;
            for (int pkgIdx = 0; pkgIdx < packages.size(); pkgIdx++) {
                String packageName = packages.get(pkgIdx);
                isKilled |= disablePackageForUser(packageName, usage.userId);
            }
            if (isKilled) {
                usage.ioUsage.killed();
                killedUserPackageKeys.add(usage.getUniqueId());
            }
        }
        pushIoOveruseKillMetrics(killedUserPackageKeys);
        mActionableUserPackages.clear();
    }

    private void notifyUserOnOveruse() {
        SparseArray<String> headsUpNotificationPackagesByNotificationId = new SparseArray<>();
        SparseArray<String> notificationCenterPackagesByNotificationId = new SparseArray<>();
        int currentUserId = ActivityManager.getCurrentUser();
        synchronized (mLock) {
            for (int i = mUserNotifiablePackages.size() - 1; i >= 0; i--) {
                String uniqueId = mUserNotifiablePackages.valueAt(i);
                PackageResourceUsage usage = mUsageByUserPackage.get(uniqueId);
                if (usage == null || (usage.userId == currentUserId
                        && usage.getKillableState() != KILLABLE_STATE_YES)) {
                    mUserNotifiablePackages.removeAt(i);
                    continue;
                }
                if (usage.userId != currentUserId) {
                    Slogf.i(TAG, "Skipping notification for user %d and package %s because current"
                                    + " user %d is different", usage.userId,
                            usage.genericPackageName, currentUserId);
                    continue;
                }
                List<String> packages;
                if (usage.isSharedPackage()) {
                    packages = mPackageInfoHandler.getPackagesForUid(usage.getUid(),
                            usage.genericPackageName);
                } else {
                    packages = Collections.singletonList(usage.genericPackageName);
                }
                for (int pkgIdx = 0; pkgIdx < packages.size(); pkgIdx++) {
                    String packageName = packages.get(pkgIdx);
                    String userPackageUniqueId = getUserPackageUniqueId(currentUserId, packageName);
                    if (mActiveUserNotifications.contains(userPackageUniqueId)) {
                        Slogf.e(TAG, "Dropping notification for user %d and package %s as it has "
                                + "an active notification", currentUserId, packageName);
                        continue;
                    }
                    int notificationId = mResourceOveruseNotificationBaseId
                            + mCurrentOveruseNotificationIdOffset;
                    if (mCurrentUxState == UX_STATE_NO_INTERACTION || mIsHeadsUpNotificationSent) {
                        notificationCenterPackagesByNotificationId.put(notificationId, packageName);
                    } else {
                        headsUpNotificationPackagesByNotificationId.put(notificationId,
                                packageName);
                        mIsHeadsUpNotificationSent = true;
                    }
                    if (mActiveUserNotificationsByNotificationId.contains(notificationId)) {
                        mActiveUserNotifications.remove(
                                mActiveUserNotificationsByNotificationId.get(notificationId));
                    }
                    mActiveUserNotifications.add(userPackageUniqueId);
                    mActiveUserNotificationsByNotificationId.put(notificationId,
                            userPackageUniqueId);
                    mCurrentOveruseNotificationIdOffset = ++mCurrentOveruseNotificationIdOffset
                            % mResourceOveruseNotificationMaxOffset;
                }
                mUserNotifiablePackages.removeAt(i);
            }
        }
        sendResourceOveruseNotificationsAsUser(currentUserId,
                headsUpNotificationPackagesByNotificationId,
                notificationCenterPackagesByNotificationId);
        if (DEBUG) {
            Slogf.d(TAG, "Sent %d resource overuse notifications successfully",
                    headsUpNotificationPackagesByNotificationId.size()
                            + notificationCenterPackagesByNotificationId.size());
        }
    }

    private void enablePackageForUser(int uid, String genericPackageName) {
        int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
        synchronized (mLock) {
            ArraySet<String> disabledPackages = mDisabledUserPackagesByUserId.get(userId);
            if (disabledPackages == null) {
                return;
            }
        }
        List<String> packages;
        if (isSharedPackage(genericPackageName)) {
            packages = mPackageInfoHandler.getPackagesForUid(uid, genericPackageName);
        } else {
            packages = Collections.singletonList(genericPackageName);
        }
        for (int i = 0; i < packages.size(); i++) {
            String packageName = packages.get(i);
            try {
                if (PackageManagerHelper.getApplicationEnabledSettingForUser(packageName,
                        userId) != COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                    continue;
                }
                synchronized (mLock) {
                    ArraySet<String> disabledPackages = mDisabledUserPackagesByUserId.get(userId);
                    if (disabledPackages == null || !disabledPackages.contains(packageName)) {
                        continue;
                    }
                    removeFromDisabledPackagesSettingsStringLocked(packageName, userId);
                    disabledPackages.remove(packageName);
                    if (disabledPackages.isEmpty()) {
                        mDisabledUserPackagesByUserId.remove(userId);
                    }
                }
                PackageManagerHelper.setApplicationEnabledSettingForUser(
                        packageName, COMPONENT_ENABLED_STATE_ENABLED, /* flags= */ 0, userId,
                        mContext.getPackageName());
                Slogf.i(TAG, "Enabled user '%d' package '%s'", userId, packageName);
            } catch (RemoteException | IllegalArgumentException e) {
                Slogf.e(TAG, e, "Failed to verify and enable user %d, package '%s'", userId,
                        packageName);
            }
        }
    }

    private void sendResourceOveruseNotificationsAsUser(@UserIdInt int userId,
            SparseArray<String> headsUpNotificationPackagesById,
            SparseArray<String> notificationCenterPackagesById) {
        if (headsUpNotificationPackagesById.size() == 0
                && notificationCenterPackagesById.size() == 0) {
            return;
        }
        BuiltinPackageDependency.createNotificationHelper(mBuiltinPackageContext)
                .showResourceOveruseNotificationsAsUser(
                        UserHandle.of(userId),
                        headsUpNotificationPackagesById, notificationCenterPackagesById);
    }

    private void cancelNotificationAsUser(int notificationId, UserHandle userHandle) {
        BuiltinPackageDependency.createNotificationHelper(mBuiltinPackageContext)
                        .cancelNotificationAsUser(userHandle, notificationId);
    }

    private void appendToDisabledPackagesSettingsString(String packageName, @UserIdInt int userId) {
        ContentResolver contentResolverForUser = getContentResolverForUser(mContext, userId);
        // Appending and removing package names to/from the settings string
        // KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE is done only by this class. So, synchronize
        // these operations using the class wide lock.
        synchronized (mLock) {
            ArraySet<String> packages = extractPackages(
                    Settings.Secure.getString(contentResolverForUser,
                            KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE));
            if (!packages.add(packageName)) {
                return;
            }
            String settingsString = constructSettingsString(packages);
            Settings.Secure.putString(contentResolverForUser,
                    KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE, settingsString);
            if (DEBUG) {
                Slogf.d(TAG, "Appended %s to %s. New value is '%s'", packageName,
                        KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE, settingsString);
            }
        }
    }

    /**
     * Removes {@code packageName} from {@link KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE}
     * {@code Settings} of the given user.
     *
     * <p> Appending and removing package names to/from the settings string
     *     KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE is done only by this class. So, synchronize
     *     these operations using the class wide lock.
     */
    @GuardedBy("mLock")
    private void removeFromDisabledPackagesSettingsStringLocked(String packageName,
            @UserIdInt int userId) {
        ContentResolver contentResolverForUser = getContentResolverForUser(mContext, userId);
        ArraySet<String> packages = extractPackages(
                Settings.Secure.getString(contentResolverForUser,
                        KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE));
        if (!packages.remove(packageName)) {
            return;
        }
        String settingsString = constructSettingsString(packages);
        Settings.Secure.putString(contentResolverForUser,
                KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE, settingsString);
        if (DEBUG) {
            Slogf.d(TAG, "Removed %s from %s. New value is '%s'", packageName,
                    KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE, settingsString);
        }
    }

    /**
     * Syncs the {@link KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE} {@code Settings} of all users
     * with the internal cache.
     *
     * <p> Appending and removing package names to/from the settings string
     *     KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE is done only by this class. So, synchronize
     *     these operations using the class wide lock.
     */
    @GuardedBy("mLock")
    private void syncDisabledUserPackagesLocked() {
        int[] userIds = getAliveUserIds();
        for (int i = 0; i < userIds.length; i++) {
            int userId = userIds[i];
            ContentResolver contentResolverForUser = getContentResolverForUser(mContext, userId);
            ArraySet<String> packages = extractPackages(
                    Settings.Secure.getString(contentResolverForUser,
                            KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE));
            if (packages.isEmpty()) {
                continue;
            }
            mDisabledUserPackagesByUserId.put(userId, packages);
        }
        if (DEBUG) {
            Slogf.d(TAG, "Synced the %s settings to the disabled user packages cache.",
                    KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE);
        }
    }

    private static ArraySet<String> extractPackages(String settingsString) {
        return TextUtils.isEmpty(settingsString) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsString.split(
                        PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR)));
    }

    @Nullable
    private static String constructSettingsString(ArraySet<String> packages) {
        return packages.isEmpty() ? null :
                TextUtils.join(PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR, packages);
    }

    private void pushIoOveruseMetrics(ArraySet<String> userPackageKeys) {
        SparseArray<AtomsProto.CarWatchdogIoOveruseStats> statsByUid = new SparseArray<>();
        synchronized (mLock) {
            for (int i = 0; i < userPackageKeys.size(); ++i) {
                String key = userPackageKeys.valueAt(i);
                PackageResourceUsage usage = mUsageByUserPackage.get(key);
                if (usage == null) {
                    Slogf.w(TAG, "Missing usage stats for user package key %s", key);
                    continue;
                }
                statsByUid.put(usage.getUid(), constructCarWatchdogIoOveruseStatsLocked(usage));
            }
        }
        for (int i = 0; i < statsByUid.size(); ++i) {
            CarStatsLog.write(CAR_WATCHDOG_IO_OVERUSE_STATS_REPORTED, statsByUid.keyAt(i),
                    statsByUid.valueAt(i).toByteArray());
        }
    }

    private void pushIoOveruseKillMetrics(ArraySet<String> userPackageKeys) {
        int systemState;
        SparseArray<AtomsProto.CarWatchdogIoOveruseStats> statsByUid = new SparseArray<>();
        synchronized (mLock) {
            systemState = inferSystemStateLocked();
            for (int i = 0; i < userPackageKeys.size(); ++i) {
                String key = userPackageKeys.valueAt(i);
                PackageResourceUsage usage = mUsageByUserPackage.get(key);
                if (usage == null) {
                    Slogf.w(TAG, "Missing usage stats for user package key %s", key);
                    continue;
                }
                statsByUid.put(usage.getUid(), constructCarWatchdogIoOveruseStatsLocked(usage));
            }
        }
        for (int i = 0; i < statsByUid.size(); ++i) {
            // TODO(b/200598815): After watchdog can classify foreground vs background apps,
            //  report the correct uid state.
            CarStatsLog.write(CAR_WATCHDOG_KILL_STATS_REPORTED, statsByUid.keyAt(i),
                    CAR_WATCHDOG_KILL_STATS_REPORTED__UID_STATE__UNKNOWN_UID_STATE,
                    systemState,
                    CAR_WATCHDOG_KILL_STATS_REPORTED__KILL_REASON__KILLED_ON_IO_OVERUSE,
                    /* arg5= */ null, statsByUid.valueAt(i).toByteArray());
        }
    }

    @GuardedBy("mLock")
    private int inferSystemStateLocked() {
        if (mCurrentGarageMode == GarageMode.GARAGE_MODE_ON) {
            return CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__GARAGE_MODE;
        }
        return mCurrentUxState == UX_STATE_NO_INTERACTION
                ? CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE
                : CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_INTERACTION_MODE;
    }

    @GuardedBy("mLock")
    private AtomsProto.CarWatchdogIoOveruseStats constructCarWatchdogIoOveruseStatsLocked(
            PackageResourceUsage usage) {
        @ComponentType int componentType = mPackageInfoHandler.getComponentType(
                usage.getUid(), usage.genericPackageName);
        android.automotive.watchdog.PerStateBytes threshold =
                mOveruseConfigurationCache.fetchThreshold(usage.genericPackageName, componentType);
        android.automotive.watchdog.PerStateBytes writtenBytes =
                usage.ioUsage.getInternalIoOveruseStats().writtenBytes;
        return constructCarWatchdogIoOveruseStats(
                AtomsProto.CarWatchdogIoOveruseStats.Period.DAILY,
                constructCarWatchdogPerStateBytes(threshold.foregroundBytes,
                        threshold.backgroundBytes, threshold.garageModeBytes),
                constructCarWatchdogPerStateBytes(writtenBytes.foregroundBytes,
                        writtenBytes.backgroundBytes, writtenBytes.garageModeBytes));
    }

    private int onPullAtom(int atomTag, List<StatsEvent> data) {
        if (atomTag != CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY
                && atomTag != CAR_WATCHDOG_UID_IO_USAGE_SUMMARY) {
            Slogf.e(TAG, "Unexpected atom tag: %d", atomTag);
            return PULL_SKIP;
        }
        synchronized (mLock) {
            if (mLastSystemIoUsageSummaryReportedDate == null
                    || mLastUidIoUsageSummaryReportedDate == null) {
                readMetadataFileLocked();
            }
        }
        ZonedDateTime reportDate;
        switch (atomTag) {
            case CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY:
                synchronized (mLock) {
                    reportDate = mLastSystemIoUsageSummaryReportedDate;
                }
                pullAtomsForWeeklyPeriodsSinceReportedDate(reportDate, data,
                        this::pullSystemIoUsageSummaryStatsEvents);
                synchronized (mLock) {
                    mLastSystemIoUsageSummaryReportedDate = mTimeSource.getCurrentDate();
                }
                break;
            case CAR_WATCHDOG_UID_IO_USAGE_SUMMARY:
                synchronized (mLock) {
                    reportDate = mLastUidIoUsageSummaryReportedDate;
                }
                pullAtomsForWeeklyPeriodsSinceReportedDate(reportDate, data,
                        this::pullUidIoUsageSummaryStatsEvents);
                synchronized (mLock) {
                    mLastUidIoUsageSummaryReportedDate = mTimeSource.getCurrentDate();
                }
                break;
            default:
                Slogf.i(TAG, "Skipping pull atom request on invalid watchdog atom tag: %d",
                        atomTag);
        }
        return PULL_SUCCESS;
    }

    @GuardedBy("mLock")
    private void readMetadataFileLocked() {
        mLastSystemIoUsageSummaryReportedDate = mLastUidIoUsageSummaryReportedDate =
                mTimeSource.getCurrentDate().minus(RETENTION_PERIOD);
        File file = getWatchdogMetadataFile();
        if (!file.exists()) {
            Slogf.e(TAG, "Watchdog metadata file '%s' doesn't exist", file.getAbsoluteFile());
            return;
        }
        AtomicFile atomicFile = new AtomicFile(file);
        try (FileInputStream fis = atomicFile.openRead()) {
            JsonReader reader = new JsonReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case SYSTEM_IO_USAGE_SUMMARY_REPORTED_DATE:
                        mLastSystemIoUsageSummaryReportedDate =
                                ZonedDateTime.parse(reader.nextString(),
                                        DateTimeFormatter.ISO_DATE_TIME.withZone(ZONE_OFFSET));
                        break;
                    case UID_IO_USAGE_SUMMARY_REPORTED_DATE:
                        mLastUidIoUsageSummaryReportedDate =
                                ZonedDateTime.parse(reader.nextString(),
                                        DateTimeFormatter.ISO_DATE_TIME.withZone(ZONE_OFFSET));
                        break;
                    default:
                        Slogf.w(TAG, "Unrecognized key: %s", name);
                        reader.skipValue();
                }
            }
            reader.endObject();
            if (DEBUG) {
                Slogf.e(TAG, "Successfully read watchdog metadata file '%s'",
                        file.getAbsoluteFile());
            }
        } catch (IOException e) {
            Slogf.e(TAG, e, "Failed to read watchdog metadata file '%s'", file.getAbsoluteFile());
        } catch (NumberFormatException | IllegalStateException | DateTimeParseException e) {
            Slogf.e(TAG, e, "Unexpected format in watchdog metadata file '%s'",
                    file.getAbsoluteFile());
        }
    }

    private void pullAtomsForWeeklyPeriodsSinceReportedDate(ZonedDateTime reportedDate,
            List<StatsEvent> data, BiConsumer<Pair<ZonedDateTime, ZonedDateTime>,
            List<StatsEvent>> pullAtomCallback) {
        ZonedDateTime now = mTimeSource.getCurrentDate();
        ZonedDateTime nextReportWeekStartDate = reportedDate.with(ChronoField.DAY_OF_WEEK, 1)
                .truncatedTo(ChronoUnit.DAYS);
        while (ChronoUnit.WEEKS.between(nextReportWeekStartDate, now) > 0) {
            pullAtomCallback.accept(
                    new Pair<>(nextReportWeekStartDate, nextReportWeekStartDate.plusWeeks(1)),
                    data);
            nextReportWeekStartDate = nextReportWeekStartDate.plusWeeks(1);
        }
    }

    private void pullSystemIoUsageSummaryStatsEvents(Pair<ZonedDateTime, ZonedDateTime> period,
            List<StatsEvent> data) {
        List<AtomsProto.CarWatchdogDailyIoUsageSummary> dailyIoUsageSummaries =
                mWatchdogStorage.getDailySystemIoUsageSummaries(
                        mIoUsageSummaryMinSystemTotalWrittenBytes, period.first.toEpochSecond(),
                        period.second.toEpochSecond());
        if (dailyIoUsageSummaries == null) {
            Slogf.i(TAG, "No system I/O usage summary stats available to pull");
            return;
        }

        AtomsProto.CarWatchdogEventTimePeriod evenTimePeriod =
                AtomsProto.CarWatchdogEventTimePeriod.newBuilder()
                        .setPeriod(AtomsProto.CarWatchdogEventTimePeriod.Period.WEEKLY).build();
        data.add(CarStatsLog.buildStatsEvent(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                AtomsProto.CarWatchdogIoUsageSummary.newBuilder()
                        .setEventTimePeriod(evenTimePeriod)
                        .addAllDailyIoUsageSummary(dailyIoUsageSummaries).build()
                        .toByteArray(),
                period.first.toEpochSecond() * 1000));

        Slogf.i(TAG, "Successfully pulled system I/O usage summary stats");
    }

    private void pullUidIoUsageSummaryStatsEvents(Pair<ZonedDateTime, ZonedDateTime> period,
            List<StatsEvent> data) {
        // Fetch summaries for twice the top N user packages because if the UID cannot be resolved
        // for some user packages, the fetched summaries will still contain enough entries to pull.
        List<WatchdogStorage.UserPackageDailySummaries> topUsersDailyIoUsageSummaries =
                mWatchdogStorage.getTopUsersDailyIoUsageSummaries(mUidIoUsageSummaryTopCount * 2,
                        mIoUsageSummaryMinSystemTotalWrittenBytes,
                        period.first.toEpochSecond(), period.second.toEpochSecond());
        if (topUsersDailyIoUsageSummaries == null) {
            Slogf.i(TAG, "No top users' I/O usage summary stats available to pull");
            return;
        }

        SparseArray<List<String>> genericPackageNamesByUserId = new SparseArray<>();
        for (int i = 0; i < topUsersDailyIoUsageSummaries.size(); ++i) {
            WatchdogStorage.UserPackageDailySummaries entry =
                    topUsersDailyIoUsageSummaries.get(i);
            List<String> genericPackageNames = genericPackageNamesByUserId.get(entry.userId);
            if (genericPackageNames == null) {
                genericPackageNames = new ArrayList<>();
            }
            genericPackageNames.add(entry.packageName);
            genericPackageNamesByUserId.put(entry.userId, genericPackageNames);
        }

        SparseArray<Map<String, Integer>> packageUidsByUserId =
                getPackageUidsForUsers(genericPackageNamesByUserId);

        AtomsProto.CarWatchdogEventTimePeriod.Builder evenTimePeriodBuilder =
                AtomsProto.CarWatchdogEventTimePeriod.newBuilder()
                        .setPeriod(AtomsProto.CarWatchdogEventTimePeriod.Period.WEEKLY);

        long startEpochMillis = period.first.toEpochSecond() * 1000;
        int numPulledUidSummaryStats = 0;
        for (int i = 0; i < topUsersDailyIoUsageSummaries.size()
                && numPulledUidSummaryStats < mUidIoUsageSummaryTopCount; ++i) {
            WatchdogStorage.UserPackageDailySummaries entry = topUsersDailyIoUsageSummaries.get(i);
            Map<String, Integer> uidsByGenericPackageName = packageUidsByUserId.get(entry.userId);
            if (uidsByGenericPackageName == null
                    || !uidsByGenericPackageName.containsKey(entry.packageName)) {
                Slogf.e(TAG, "Failed to fetch uid for package %s and user %d. So, skipping "
                        + "reporting stats for this user package", entry.packageName, entry.userId);
                continue;
            }
            data.add(CarStatsLog.buildStatsEvent(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                    uidsByGenericPackageName.get(entry.packageName),
                    AtomsProto.CarWatchdogIoUsageSummary.newBuilder()
                            .setEventTimePeriod(evenTimePeriodBuilder)
                            .addAllDailyIoUsageSummary(entry.dailyIoUsageSummaries).build()
                            .toByteArray(),
                    startEpochMillis));
            ++numPulledUidSummaryStats;
        }

        Slogf.e(TAG, "Successfully pulled top %d users' I/O usage summary stats",
                numPulledUidSummaryStats);
    }

    /**
     * Gets the UID for the resource usage's user package.
     *
     * <p>If the package resource usage's UID is not valid, fetches the UID for the user package
     * from the package manager. Returns {@code INVALID_UID} if the package manager cannot find the
     * package. </p>
     */
    private int getOrFetchUid(PackageResourceUsage usage, String packageName) {
        int uid = usage.getUid();
        if (uid == INVALID_UID) {
            uid = getPackageUidAsUser(mContext.getPackageManager(), packageName,
                    usage.userId);
        }
        return uid;
    }

    private SparseArray<Map<String, Integer>> getPackageUidsForUsers(
            SparseArray<List<String>> genericPackageNamesByUserId) {
        PackageManager pm = mContext.getPackageManager();
        SparseArray<Map<String, Integer>> packageUidsByUserId = new SparseArray<>();
        for (int i = 0; i < genericPackageNamesByUserId.size(); ++i) {
            int userId = genericPackageNamesByUserId.keyAt(i);
            Map<String, Integer> uidsByGenericPackageName = getPackageUidsForUser(pm,
                    genericPackageNamesByUserId.valueAt(i), userId);
            if (!uidsByGenericPackageName.isEmpty()) {
                packageUidsByUserId.put(userId, uidsByGenericPackageName);
            }
        }
        return packageUidsByUserId;
    }

    /**
     * Returns UIDs for the given generic package names belonging to the given user.
     *
     * <p>{@code pm.getInstalledPackagesAsUser} call is expensive as it fetches all installed
     * packages for the given user. Thus this method should be called for all packages that requires
     * the UIDs to be resolved in a single call.
     */
    private Map<String, Integer> getPackageUidsForUser(PackageManager pm,
            List<String> genericPackageNames, int userId) {
        Map<String, Integer> uidsByGenericPackageNames = new ArrayMap<>();
        Set<String> resolveSharedUserIds = new ArraySet<>();
        for (int i = 0; i < genericPackageNames.size(); ++i) {
            String genericPackageName = genericPackageNames.get(i);
            PackageResourceUsage usage;
            synchronized (mLock) {
                usage = mUsageByUserPackage.get(getUserPackageUniqueId(userId,
                        genericPackageName));
            }
            if (usage != null && usage.getUid() != INVALID_UID) {
                uidsByGenericPackageNames.put(genericPackageName, usage.getUid());
                continue;
            }
            if (isSharedPackage(genericPackageName)) {
                resolveSharedUserIds.add(
                        genericPackageName.substring(SHARED_PACKAGE_PREFIX.length()));
                continue;
            }
            int uid = getPackageUidAsUser(pm, genericPackageName, userId);
            if (uid != INVALID_UID) {
                uidsByGenericPackageNames.put(genericPackageName, uid);
            }
        }
        if (resolveSharedUserIds.isEmpty()) {
            return uidsByGenericPackageNames;
        }
        List<PackageInfo> packageInfos = pm.getInstalledPackagesAsUser(/* flags= */ 0, userId);
        for (int i = 0; i < packageInfos.size() && !resolveSharedUserIds.isEmpty(); ++i) {
            PackageInfo packageInfo = packageInfos.get(i);
            if (packageInfo.sharedUserId == null
                    || !resolveSharedUserIds.contains(packageInfo.sharedUserId)) {
                continue;
            }
            int uid = getPackageUidAsUser(pm, packageInfo.packageName, userId);
            if (uid != INVALID_UID) {
                uidsByGenericPackageNames.put(SHARED_PACKAGE_PREFIX + packageInfo.sharedUserId,
                        uid);
            }
            resolveSharedUserIds.remove(packageInfo.sharedUserId);
        }
        return uidsByGenericPackageNames;
    }

    private int getPackageUidAsUser(PackageManager pm, String packageName, @UserIdInt int userId) {
        try {
            return PackageManagerHelper.getPackageUidAsUser(pm, packageName, userId);
        } catch (PackageManager.NameNotFoundException e) {
            Slogf.e(TAG, "Package %s for user %d is not found", packageName, userId);
            return INVALID_UID;
        }
    }

    @GuardedBy("mLock")
    private void dumpResourceOveruseListenerInfosLocked(
            SparseArray<ArrayList<ResourceOveruseListenerInfo>> overuseListenerInfos,
            long fieldId, ProtoOutputStream proto) {
        for (int i = 0; i < overuseListenerInfos.size(); i++) {
            ArrayList<ResourceOveruseListenerInfo> resourceOveruseListenerInfos =
                    overuseListenerInfos.valueAt(i);
            for (int j = 0; j < resourceOveruseListenerInfos.size(); j++) {
                long overuseListenerInfosToken = proto.start(fieldId);
                ResourceOveruseListenerInfo resourceOveruseListenerInfo =
                        resourceOveruseListenerInfos.get(j);
                proto.write(PerformanceDump.ResourceOveruseListenerInfo.FLAG,
                        resourceOveruseListenerInfo.flag);
                proto.write(PerformanceDump.ResourceOveruseListenerInfo.PID,
                        resourceOveruseListenerInfo.pid);
                long userPackageInfoToken = proto.start(
                        PerformanceDump.ResourceOveruseListenerInfo.USER_PACKAGE_INFO);
                int uid = overuseListenerInfos.keyAt(i);
                proto.write(UserPackageInfo.USER_ID,
                        UserHandle.getUserHandleForUid(uid).getIdentifier());
                proto.write(UserPackageInfo.PACKAGE_NAME,
                        mPackageInfoHandler.getNamesForUids(new int[]{uid}).get(uid, null));
                proto.end(userPackageInfoToken);
                proto.end(overuseListenerInfosToken);
            }
        }
    }

    @GuardedBy("mLock")
    private void dumpUsageByUserPackageLocked(ProtoOutputStream proto) {
        for (int i = 0; i < mUsageByUserPackage.size(); i++) {
            long usageByUserPackagesToken = proto.start(PerformanceDump.USAGE_BY_USER_PACKAGES);

            dumpUserPackageInfoFromUniqueId(mUsageByUserPackage.keyAt(i),
                    PerformanceDump.UsageByUserPackage.USER_PACKAGE_INFO, proto);

            PackageResourceUsage packageResourceUsage = mUsageByUserPackage.valueAt(i);
            PackageIoUsage packageIoUsage = packageResourceUsage.ioUsage;

            proto.write(PerformanceDump.UsageByUserPackage.KILLABLE_STATE,
                    toProtoKillableState(packageResourceUsage.mKillableState));

            long packageIoUsageToken = proto.start(
                    PerformanceDump.UsageByUserPackage.PACKAGE_IO_USAGE);

            long ioOveruseStatsToken = proto.start(
                    PerformanceDump.PackageIoUsage.IO_OVERUSE_STATS);
            proto.write(PerformanceDump.IoOveruseStats.KILLABLE_ON_OVERUSE,
                    packageIoUsage.mIoOveruseStats.killableOnOveruse);
            dumpPerStateBytes(packageIoUsage.mIoOveruseStats.remainingWriteBytes,
                    PerformanceDump.IoOveruseStats.REMAINING_WRITE_BYTES, proto);
            proto.write(PerformanceDump.IoOveruseStats.START_TIME,
                    packageIoUsage.mIoOveruseStats.startTime);
            proto.write(PerformanceDump.IoOveruseStats.DURATION,
                    packageIoUsage.mIoOveruseStats.durationInSeconds);

            dumpPerStateBytes(packageIoUsage.mIoOveruseStats.writtenBytes,
                    PerformanceDump.IoOveruseStats.WRITTEN_BYTES, proto);

            proto.write(PerformanceDump.IoOveruseStats.TOTAL_OVERUSES,
                    packageIoUsage.mIoOveruseStats.totalOveruses);
            proto.end(ioOveruseStatsToken);

            dumpPerStateBytes(packageIoUsage.mForgivenWriteBytes,
                    PerformanceDump.PackageIoUsage.FORGIVEN_WRITE_BYTES, proto);

            proto.write(PerformanceDump.PackageIoUsage.FORGIVEN_OVERUSES,
                    packageIoUsage.mForgivenOveruses);
            proto.write(PerformanceDump.PackageIoUsage.HISTORICAL_NOT_FORGIVEN_OVERUSES,
                    packageIoUsage.mHistoricalNotForgivenOveruses);
            proto.write(PerformanceDump.PackageIoUsage.TOTAL_TIMES_KILLED,
                    packageIoUsage.mTotalTimesKilled);

            proto.end(packageIoUsageToken);

            proto.end(usageByUserPackagesToken);
        }
    }

    private static void dumpUserPackageInfo(ArraySet<String> userPackageInfo, long fieldId,
            ProtoOutputStream proto) {
        for (int i = 0; i < userPackageInfo.size(); i++) {
            dumpUserPackageInfoFromUniqueId(userPackageInfo.valueAt(i), fieldId, proto);
        }
    }

    private static void dumpUserPackageInfoFromUniqueId(String uniqueId, long fieldId,
            ProtoOutputStream proto) {
        long fieldIdToken = proto.start(fieldId);
        proto.write(UserPackageInfo.USER_ID, getUserIdFromUniqueId(uniqueId));
        proto.write(UserPackageInfo.PACKAGE_NAME, getPackageNameFromUniqueId(uniqueId));
        proto.end(fieldIdToken);
    }

    private static void dumpPerStateBytes(android.automotive.watchdog.PerStateBytes perStateBytes,
            long fieldId, ProtoOutputStream proto) {
        long perStateBytesToken = proto.start(fieldId);
        proto.write(PerformanceDump.PerStateBytes.FOREGROUND_BYTES,
                perStateBytes.foregroundBytes);
        proto.write(PerformanceDump.PerStateBytes.BACKGROUND_BYTES,
                perStateBytes.backgroundBytes);
        proto.write(PerformanceDump.PerStateBytes.GARAGEMODE_BYTES,
                perStateBytes.garageModeBytes);
        proto.end(perStateBytesToken);
    }

    private static File getWatchdogMetadataFile() {
        return new File(CarWatchdogService.getWatchdogDirFile(), METADATA_FILENAME);
    }

    private static String getUserPackageUniqueId(@UserIdInt int userId, String genericPackageName) {
        return userId + USER_PACKAGE_SEPARATOR + genericPackageName;
    }

    private static String getPackageNameFromUniqueId(String uniqueId) {
        return uniqueId.split(USER_PACKAGE_SEPARATOR)[0];
    }

    private static String getUserIdFromUniqueId(String uniqueId) {
        return uniqueId.split(USER_PACKAGE_SEPARATOR)[1];
    }

    @VisibleForTesting
    static IoOveruseStats.Builder toIoOveruseStatsBuilder(
            android.automotive.watchdog.IoOveruseStats internalStats,
            int totalTimesKilled, boolean isKillableOnOveruses) {
        return new IoOveruseStats.Builder(internalStats.startTime, internalStats.durationInSeconds)
                .setTotalOveruses(internalStats.totalOveruses)
                .setTotalTimesKilled(totalTimesKilled)
                .setTotalBytesWritten(totalPerStateBytes(internalStats.writtenBytes))
                .setKillableOnOveruse(isKillableOnOveruses)
                .setRemainingWriteBytes(toPerStateBytes(internalStats.remainingWriteBytes));
    }

    private static PerStateBytes toPerStateBytes(
            android.automotive.watchdog.PerStateBytes internalPerStateBytes) {
        return new PerStateBytes(internalPerStateBytes.foregroundBytes,
                internalPerStateBytes.backgroundBytes, internalPerStateBytes.garageModeBytes);
    }

    private static long totalPerStateBytes(
            android.automotive.watchdog.PerStateBytes internalPerStateBytes) {
        BiFunction<Long, Long, Long> sum = (l, r) -> {
            return (Long.MAX_VALUE - l > r) ? l + r : Long.MAX_VALUE;
        };
        return sum.apply(sum.apply(internalPerStateBytes.foregroundBytes,
                internalPerStateBytes.backgroundBytes), internalPerStateBytes.garageModeBytes);
    }

    private static long getMinimumBytesWritten(
            @CarWatchdogManager.MinimumStatsFlag int minimumStatsIoFlag) {
        switch (minimumStatsIoFlag) {
            case 0:
                return 0;
            case CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB:
                return 1024 * 1024;
            case CarWatchdogManager.FLAG_MINIMUM_STATS_IO_100_MB:
                return 100 * 1024 * 1024;
            case CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_GB:
                return 1024 * 1024 * 1024;
            default:
                throw new IllegalArgumentException(
                        "Must provide valid minimum stats flag for I/O resource");
        }
    }

    private static android.automotive.watchdog.internal.ResourceOveruseConfiguration
            toInternalResourceOveruseConfiguration(ResourceOveruseConfiguration config,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag) {
        android.automotive.watchdog.internal.ResourceOveruseConfiguration internalConfig =
                new android.automotive.watchdog.internal.ResourceOveruseConfiguration();
        internalConfig.componentType = config.getComponentType();
        internalConfig.safeToKillPackages = config.getSafeToKillPackages();
        internalConfig.vendorPackagePrefixes = config.getVendorPackagePrefixes();
        internalConfig.packageMetadata = new ArrayList<>();
        for (Map.Entry<String, String> entry : config.getPackagesToAppCategoryTypes().entrySet()) {
            if (entry.getKey().isEmpty()) {
                continue;
            }
            PackageMetadata metadata = new PackageMetadata();
            metadata.packageName = entry.getKey();
            switch(entry.getValue()) {
                case ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS:
                    metadata.appCategoryType = ApplicationCategoryType.MAPS;
                    break;
                case ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA:
                    metadata.appCategoryType = ApplicationCategoryType.MEDIA;
                    break;
                default:
                    Slogf.i(TAG, "Invalid application category type: %s skipping package: %s",
                            entry.getValue(), metadata.packageName);
                    continue;
            }
            internalConfig.packageMetadata.add(metadata);
        }
        internalConfig.resourceSpecificConfigurations = new ArrayList<>();
        if ((resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) != 0
                && config.getIoOveruseConfiguration() != null) {
            internalConfig.resourceSpecificConfigurations.add(
                    toResourceSpecificConfiguration(config.getComponentType(),
                            config.getIoOveruseConfiguration()));
        }
        return internalConfig;
    }

    private static ResourceSpecificConfiguration
            toResourceSpecificConfiguration(int componentType, IoOveruseConfiguration config) {
        android.automotive.watchdog.internal.IoOveruseConfiguration internalConfig =
                new android.automotive.watchdog.internal.IoOveruseConfiguration();
        internalConfig.componentLevelThresholds = toPerStateIoOveruseThreshold(
                toComponentTypeStr(componentType), config.getComponentLevelThresholds());
        internalConfig.packageSpecificThresholds = toPerStateIoOveruseThresholds(
                config.getPackageSpecificThresholds());
        internalConfig.categorySpecificThresholds = toPerStateIoOveruseThresholds(
                config.getAppCategorySpecificThresholds());
        for (int i = 0; i < internalConfig.categorySpecificThresholds.size(); ++i) {
            PerStateIoOveruseThreshold threshold = internalConfig.categorySpecificThresholds.get(i);
            switch(threshold.name) {
                case ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS:
                    threshold.name = INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS;
                    break;
                case ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA:
                    threshold.name = INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA;
                    break;
                default:
                    threshold.name = INTERNAL_APPLICATION_CATEGORY_TYPE_UNKNOWN;
            }
        }
        internalConfig.systemWideThresholds = toInternalIoOveruseAlertThresholds(
                config.getSystemWideThresholds());

        ResourceSpecificConfiguration resourceSpecificConfig = new ResourceSpecificConfiguration();
        resourceSpecificConfig.setIoOveruseConfiguration(internalConfig);
        return resourceSpecificConfig;
    }

    @VisibleForTesting
    static String toComponentTypeStr(int componentType) {
        switch(componentType) {
            case ComponentType.SYSTEM:
                return "SYSTEM";
            case ComponentType.VENDOR:
                return "VENDOR";
            case ComponentType.THIRD_PARTY:
                return "THIRD_PARTY";
            default:
                return "UNKNOWN";
        }
    }

    private static List<PerStateIoOveruseThreshold> toPerStateIoOveruseThresholds(
            Map<String, PerStateBytes> thresholds) {
        List<PerStateIoOveruseThreshold> internalThresholds = new ArrayList<>();
        for (Map.Entry<String, PerStateBytes> entry : thresholds.entrySet()) {
            if (!thresholds.isEmpty()) {
                internalThresholds.add(toPerStateIoOveruseThreshold(entry.getKey(),
                        entry.getValue()));
            }
        }
        return internalThresholds;
    }

    private static PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(String name,
            PerStateBytes perStateBytes) {
        PerStateIoOveruseThreshold threshold = new PerStateIoOveruseThreshold();
        threshold.name = name;
        threshold.perStateWriteBytes = new android.automotive.watchdog.PerStateBytes();
        threshold.perStateWriteBytes.foregroundBytes = perStateBytes.getForegroundModeBytes();
        threshold.perStateWriteBytes.backgroundBytes = perStateBytes.getBackgroundModeBytes();
        threshold.perStateWriteBytes.garageModeBytes = perStateBytes.getGarageModeBytes();
        return threshold;
    }

    private static List<android.automotive.watchdog.internal.IoOveruseAlertThreshold>
            toInternalIoOveruseAlertThresholds(List<IoOveruseAlertThreshold> thresholds) {
        List<android.automotive.watchdog.internal.IoOveruseAlertThreshold> internalThresholds =
                new ArrayList<>();
        for (int i = 0; i < thresholds.size(); ++i) {
            if (thresholds.get(i).getDurationInSeconds() == 0
                    || thresholds.get(i).getWrittenBytesPerSecond() == 0) {
                continue;
            }
            android.automotive.watchdog.internal.IoOveruseAlertThreshold internalThreshold =
                    new android.automotive.watchdog.internal.IoOveruseAlertThreshold();
            internalThreshold.durationInSeconds = thresholds.get(i).getDurationInSeconds();
            internalThreshold.writtenBytesPerSecond = thresholds.get(i).getWrittenBytesPerSecond();
            internalThresholds.add(internalThreshold);
        }
        return internalThresholds;
    }

    private static ResourceOveruseConfiguration toResourceOveruseConfiguration(
            android.automotive.watchdog.internal.ResourceOveruseConfiguration internalConfig,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag) {
        ArrayMap<String, String> packagesToAppCategoryTypes = new ArrayMap<>();
        for (int i = 0; i < internalConfig.packageMetadata.size(); ++i) {
            String categoryTypeStr;
            switch (internalConfig.packageMetadata.get(i).appCategoryType) {
                case ApplicationCategoryType.MAPS:
                    categoryTypeStr = ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS;
                    break;
                case ApplicationCategoryType.MEDIA:
                    categoryTypeStr = ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA;
                    break;
                default:
                    continue;
            }
            packagesToAppCategoryTypes.put(
                    internalConfig.packageMetadata.get(i).packageName, categoryTypeStr);
        }
        ResourceOveruseConfiguration.Builder configBuilder =
                new ResourceOveruseConfiguration.Builder(
                internalConfig.componentType,
                internalConfig.safeToKillPackages,
                internalConfig.vendorPackagePrefixes,
                packagesToAppCategoryTypes);
        for (ResourceSpecificConfiguration resourceSpecificConfig :
                internalConfig.resourceSpecificConfigurations) {
            if (resourceSpecificConfig.getTag()
                    == ResourceSpecificConfiguration.ioOveruseConfiguration
                    && (resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) != 0) {
                configBuilder.setIoOveruseConfiguration(toIoOveruseConfiguration(
                        resourceSpecificConfig.getIoOveruseConfiguration()));
            }
        }
        return configBuilder.build();
    }

    private static IoOveruseConfiguration toIoOveruseConfiguration(
            android.automotive.watchdog.internal.IoOveruseConfiguration internalConfig) {
        PerStateBytes componentLevelThresholds =
                toPerStateBytes(internalConfig.componentLevelThresholds.perStateWriteBytes);
        ArrayMap<String, PerStateBytes> packageSpecificThresholds =
                toPerStateBytesMap(internalConfig.packageSpecificThresholds);
        ArrayMap<String, PerStateBytes> appCategorySpecificThresholds =
                toPerStateBytesMap(internalConfig.categorySpecificThresholds);
        replaceKey(appCategorySpecificThresholds, INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS,
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS);
        replaceKey(appCategorySpecificThresholds, INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA,
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA);
        List<IoOveruseAlertThreshold> systemWideThresholds =
                toIoOveruseAlertThresholds(internalConfig.systemWideThresholds);

        IoOveruseConfiguration.Builder configBuilder = new IoOveruseConfiguration.Builder(
                componentLevelThresholds, packageSpecificThresholds, appCategorySpecificThresholds,
                systemWideThresholds);
        return configBuilder.build();
    }

    private static ArrayMap<String, PerStateBytes> toPerStateBytesMap(
            List<PerStateIoOveruseThreshold> thresholds) {
        ArrayMap<String, PerStateBytes> thresholdsMap = new ArrayMap<>();
        for (int i = 0; i < thresholds.size(); ++i) {
            thresholdsMap.put(
                    thresholds.get(i).name, toPerStateBytes(thresholds.get(i).perStateWriteBytes));
        }
        return thresholdsMap;
    }

    private static List<IoOveruseAlertThreshold> toIoOveruseAlertThresholds(
            List<android.automotive.watchdog.internal.IoOveruseAlertThreshold> internalThresholds) {
        List<IoOveruseAlertThreshold> thresholds = new ArrayList<>();
        for (int i = 0; i < internalThresholds.size(); ++i) {
            thresholds.add(new IoOveruseAlertThreshold(internalThresholds.get(i).durationInSeconds,
                    internalThresholds.get(i).writtenBytesPerSecond));
        }
        return thresholds;
    }

    private static void checkResourceOveruseConfigs(
            List<ResourceOveruseConfiguration> configurations,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag) {
        ArraySet<Integer> seenComponentTypes = new ArraySet<>();
        for (int i = 0; i < configurations.size(); ++i) {
            ResourceOveruseConfiguration config = configurations.get(i);
            if (seenComponentTypes.contains(config.getComponentType())) {
                throw new IllegalArgumentException(
                        "Cannot provide duplicate configurations for the same component type");
            }
            checkResourceOveruseConfig(config, resourceOveruseFlag);
            seenComponentTypes.add(config.getComponentType());
        }
    }

    private static void checkResourceOveruseConfig(ResourceOveruseConfiguration config,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag) {
        int componentType = config.getComponentType();
        if (Objects.equals(toComponentTypeStr(componentType), "UNKNOWN")) {
            throw new IllegalArgumentException(
                    "Invalid component type in the configuration: " + componentType);
        }
        if ((resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) != 0
                && config.getIoOveruseConfiguration() == null) {
            throw new IllegalArgumentException("Must provide I/O overuse configuration");
        }
        checkIoOveruseConfig(config.getIoOveruseConfiguration(), componentType);
    }

    private static void checkIoOveruseConfig(IoOveruseConfiguration config, int componentType) {
        if (config.getComponentLevelThresholds().getBackgroundModeBytes() <= 0
                || config.getComponentLevelThresholds().getForegroundModeBytes() <= 0
                || config.getComponentLevelThresholds().getGarageModeBytes() <= 0) {
            throw new IllegalArgumentException(
                    "For component: " + toComponentTypeStr(componentType)
                            + " some thresholds are zero for: "
                            + config.getComponentLevelThresholds().toString());
        }
        if (componentType == ComponentType.SYSTEM) {
            List<IoOveruseAlertThreshold> systemThresholds = config.getSystemWideThresholds();
            if (systemThresholds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty system-wide alert thresholds provided in "
                                + toComponentTypeStr(componentType)
                                + " config.");
            }
            for (int i = 0; i < systemThresholds.size(); i++) {
                checkIoOveruseAlertThreshold(systemThresholds.get(i));
            }
        }
    }

    private static void checkIoOveruseAlertThreshold(
            IoOveruseAlertThreshold ioOveruseAlertThreshold) {
        if (ioOveruseAlertThreshold.getDurationInSeconds() <= 0) {
            throw new IllegalArgumentException(
                    "System wide threshold duration must be greater than zero for: "
                            + ioOveruseAlertThreshold);
        }
        if (ioOveruseAlertThreshold.getWrittenBytesPerSecond() <= 0) {
            throw new IllegalArgumentException(
                    "System wide threshold written bytes per second must be greater than zero for: "
                            + ioOveruseAlertThreshold);
        }
    }

    private static boolean isSharedPackage(String genericPackageName) {
        return genericPackageName.startsWith(SHARED_PACKAGE_PREFIX);
    }

    private static void replaceKey(Map<String, PerStateBytes> map, String oldKey, String newKey) {
        PerStateBytes perStateBytes = map.get(oldKey);
        if (perStateBytes != null) {
            map.put(newKey, perStateBytes);
            map.remove(oldKey);
        }
    }

    private static int toNumDays(@CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        switch(maxStatsPeriod) {
            case STATS_PERIOD_CURRENT_DAY:
                return 0;
            case STATS_PERIOD_PAST_3_DAYS:
                return 3;
            case STATS_PERIOD_PAST_7_DAYS:
                return 7;
            case STATS_PERIOD_PAST_15_DAYS:
                return 15;
            case STATS_PERIOD_PAST_30_DAYS:
                return 30;
            default:
                throw new IllegalArgumentException(
                        "Invalid max stats period provided: " + maxStatsPeriod);
        }
    }

    @VisibleForTesting
    static AtomsProto.CarWatchdogIoOveruseStats constructCarWatchdogIoOveruseStats(
            AtomsProto.CarWatchdogIoOveruseStats.Period period,
            AtomsProto.CarWatchdogPerStateBytes threshold,
            AtomsProto.CarWatchdogPerStateBytes writtenBytes) {
        // TODO(b/184310189): Report uptime once daemon pushes it to CarService.
        return AtomsProto.CarWatchdogIoOveruseStats.newBuilder()
                .setPeriod(period)
                .setThreshold(threshold)
                .setWrittenBytes(writtenBytes).build();
    }

    @VisibleForTesting
    static AtomsProto.CarWatchdogPerStateBytes constructCarWatchdogPerStateBytes(
            long foregroundBytes, long backgroundBytes, long garageModeBytes) {
        AtomsProto.CarWatchdogPerStateBytes.Builder perStateBytesBuilder =
                AtomsProto.CarWatchdogPerStateBytes.newBuilder();
        if (foregroundBytes != 0) {
            perStateBytesBuilder.setForegroundBytes(foregroundBytes);
        }
        if (backgroundBytes != 0) {
            perStateBytesBuilder.setBackgroundBytes(backgroundBytes);
        }
        if (garageModeBytes != 0) {
            perStateBytesBuilder.setGarageModeBytes(garageModeBytes);
        }
        return perStateBytesBuilder.build();
    }

    private static String toEnabledStateString(int enabledState) {
        switch (enabledState) {
            case COMPONENT_ENABLED_STATE_DEFAULT:
                return "COMPONENT_ENABLED_STATE_DEFAULT";
            case COMPONENT_ENABLED_STATE_ENABLED:
                return "COMPONENT_ENABLED_STATE_ENABLED";
            case COMPONENT_ENABLED_STATE_DISABLED:
                return "COMPONENT_ENABLED_STATE_DISABLED";
            case COMPONENT_ENABLED_STATE_DISABLED_USER:
                return "COMPONENT_ENABLED_STATE_DISABLED_USER";
            case COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return "COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED";
            default:
                return "UNKNOWN COMPONENT ENABLED STATE";
        }
    }

    private static String toUxStateString(@UxStateType int uxState) {
        switch (uxState) {
            case UX_STATE_NO_DISTRACTION:
                return "UX_STATE_NO_DISTRACTION";
            case UX_STATE_USER_NOTIFICATION:
                return "UX_STATE_USER_NOTIFICATION";
            case UX_STATE_NO_INTERACTION:
                return "UX_STATE_NO_INTERACTION";
            default:
                return "UNKNOWN UX STATE";
        }
    }

    private static int toProtoUxState(@UxStateType int uxState) {
        switch (uxState) {
            case UX_STATE_NO_DISTRACTION:
                return PerformanceDump.UX_STATE_NO_DISTRACTION;
            case UX_STATE_USER_NOTIFICATION:
                return PerformanceDump.UX_STATE_USER_NOTIFICATION;
            case UX_STATE_NO_INTERACTION:
                return PerformanceDump.UX_STATE_NO_INTERACTION;
            default:
                return PerformanceDump.UX_STATE_UNSPECIFIED;
        }
    }

    private static int toProtoKillableState(@KillableState int killableState) {
        switch (killableState) {
            case KILLABLE_STATE_YES:
                return PerformanceDump.KILLABLE_STATE_YES;
            case KILLABLE_STATE_NO:
                return PerformanceDump.KILLABLE_STATE_NO;
            case KILLABLE_STATE_NEVER:
                return PerformanceDump.KILLABLE_STATE_NEVER;
            default:
                return PerformanceDump.KILLABLE_STATE_UNSPECIFIED;
        }
    }

    private final class PackageResourceUsage {
        public final String genericPackageName;
        public @UserIdInt final int userId;
        public final PackageIoUsage ioUsage = new PackageIoUsage();
        private @KillableState int mKillableState;
        public ZonedDateTime mKillableStateLastModifiedDate;
        private int mUid;

        /** Must be called only after acquiring {@link mLock} */
        PackageResourceUsage(@UserIdInt int userId, String genericPackageName,
                @KillableState int defaultKillableState) {
            this.genericPackageName = genericPackageName;
            this.userId = userId;
            this.mKillableState = defaultKillableState;
            this.mKillableStateLastModifiedDate = mTimeSource.getCurrentDate();
            this.mUid = INVALID_UID;
        }

        public boolean isSharedPackage() {
            return this.genericPackageName.startsWith(SHARED_PACKAGE_PREFIX);
        }

        public String getUniqueId() {
            return getUserPackageUniqueId(userId, genericPackageName);
        }

        public int getUid() {
            return mUid;
        }

        public void update(int uid, android.automotive.watchdog.IoOveruseStats internalStats,
                android.automotive.watchdog.PerStateBytes forgivenWriteBytes,
                @KillableState int defaultKillableState) {
            // Package UID would change if it was re-installed, so keep it up-to-date.
            mUid = uid;
            if (!internalStats.killableOnOveruse) {
                /*
                 * Killable value specified in the internal stats is provided by the native daemon.
                 * This value reflects whether or not an application is safe-to-kill on overuse.
                 * This setting is from the I/O overuse configuration specified by the system and
                 * vendor services and doesn't reflect the user choices. Thus if the internal stats
                 * specify the application is not killable, the application is not safe-to-kill.
                 */
                mKillableState = KILLABLE_STATE_NEVER;
            } else if (mKillableState == KILLABLE_STATE_NEVER) {
                /*
                 * This case happens when a previously unsafe to kill system/vendor package was
                 * recently marked as safe-to-kill so update the old state to the default value.
                 */
                mKillableState = defaultKillableState;
            }
            ioUsage.update(internalStats, forgivenWriteBytes);
        }

        public ResourceOveruseStats.Builder getResourceOveruseStatsBuilder() {
            return new ResourceOveruseStats.Builder(genericPackageName, UserHandle.of(userId));
        }


        public IoOveruseStats getIoOveruseStats() {
            if (!ioUsage.hasUsage()) {
                return null;
            }
            return ioUsage.getIoOveruseStats(mKillableState != KILLABLE_STATE_NEVER);
        }

        public @KillableState int getKillableState() {
            return mKillableState;
        }

        public void setKillableState(@KillableState int killableState, ZonedDateTime modifiedDate) {
            mKillableState = killableState;
            mKillableStateLastModifiedDate = modifiedDate;
        }

        public boolean verifyAndSetKillableState(boolean isKillable, ZonedDateTime modifiedDate) {
            if (mKillableState == KILLABLE_STATE_NEVER) {
                return false;
            }
            mKillableState = isKillable ? KILLABLE_STATE_YES : KILLABLE_STATE_NO;
            mKillableStateLastModifiedDate = modifiedDate;
            return true;
        }

        public int syncAndFetchKillableState(int myComponentType, boolean isSafeToKill,
                @KillableState int defaultKillableState) {
            /*
             * The killable state goes out-of-sync:
             * 1. When the on-device safe-to-kill list was recently updated and the user package
             * didn't have any resource usage so the native daemon didn't update the killable state.
             * 2. When a package has no resource usage and is initialized outside of processing the
             * latest resource usage stats.
             */
            if (myComponentType != ComponentType.THIRD_PARTY && !isSafeToKill) {
                mKillableState = KILLABLE_STATE_NEVER;
            } else if (mKillableState == KILLABLE_STATE_NEVER) {
                mKillableState = defaultKillableState;
            }
            return mKillableState;
        }

        public ZonedDateTime getKillableStateLastModifiedDate() {
            return mKillableStateLastModifiedDate;
        }

        public void resetStats() {
            ioUsage.resetStats();
        }
    }

    /** Defines I/O usage fields for a package. */
    public static final class PackageIoUsage {
        private static final android.automotive.watchdog.PerStateBytes DEFAULT_PER_STATE_BYTES =
                new android.automotive.watchdog.PerStateBytes();
        private static final int MISSING_VALUE = -1;

        private android.automotive.watchdog.IoOveruseStats mIoOveruseStats;
        private android.automotive.watchdog.PerStateBytes mForgivenWriteBytes;
        private int mForgivenOveruses;
        private int mHistoricalNotForgivenOveruses;
        private int mTotalTimesKilled;

        private PackageIoUsage() {
            mForgivenWriteBytes = DEFAULT_PER_STATE_BYTES;
            mForgivenOveruses = 0;
            mHistoricalNotForgivenOveruses = MISSING_VALUE;
            mTotalTimesKilled = 0;
        }

        public PackageIoUsage(android.automotive.watchdog.IoOveruseStats ioOveruseStats,
                android.automotive.watchdog.PerStateBytes forgivenWriteBytes, int forgivenOveruses,
                int totalTimesKilled) {
            mIoOveruseStats = ioOveruseStats;
            mForgivenWriteBytes = forgivenWriteBytes;
            mForgivenOveruses = forgivenOveruses;
            mTotalTimesKilled = totalTimesKilled;
            mHistoricalNotForgivenOveruses = MISSING_VALUE;
        }

        /** Returns the I/O overuse stats related to the package. */
        public android.automotive.watchdog.IoOveruseStats getInternalIoOveruseStats() {
            return mIoOveruseStats;
        }

        /** Returns the forgiven write bytes. */
        public android.automotive.watchdog.PerStateBytes getForgivenWriteBytes() {
            return mForgivenWriteBytes;
        }

        /** Returns the number of forgiven overuses today. */
        public int getForgivenOveruses() {
            return mForgivenOveruses;
        }

        /**
         * Returns the number of not forgiven overuses. These are overuses that have not been
         * attributed previously to a package's recurring overuse.
         */
        public int getNotForgivenOveruses() {
            if (!hasUsage()) {
                return 0;
            }
            int historicalNotForgivenOveruses =
                    mHistoricalNotForgivenOveruses != MISSING_VALUE
                            ? mHistoricalNotForgivenOveruses : 0;
            return (mIoOveruseStats.totalOveruses - mForgivenOveruses)
                    + historicalNotForgivenOveruses;
        }

        /** Sets historical not forgiven overuses. */
        public void setHistoricalNotForgivenOveruses(int historicalNotForgivenOveruses) {
            mHistoricalNotForgivenOveruses = historicalNotForgivenOveruses;
        }

        /** Forgives all the I/O overuse stats' overuses. */
        public void forgiveOveruses() {
            if (!hasUsage()) {
                return;
            }
            mForgivenOveruses = mIoOveruseStats.totalOveruses;
            mHistoricalNotForgivenOveruses = 0;
        }

        /** Returns the total number of times the package was killed. */
        public int getTotalTimesKilled() {
            return mTotalTimesKilled;
        }

        boolean shouldForgiveHistoricalOveruses() {
            return mHistoricalNotForgivenOveruses != MISSING_VALUE;
        }

        boolean hasUsage() {
            return mIoOveruseStats != null;
        }

        void overwrite(PackageIoUsage ioUsage) {
            mIoOveruseStats = ioUsage.mIoOveruseStats;
            mForgivenWriteBytes = ioUsage.mForgivenWriteBytes;
            mTotalTimesKilled = ioUsage.mTotalTimesKilled;
            mHistoricalNotForgivenOveruses = ioUsage.mHistoricalNotForgivenOveruses;
        }

        void update(android.automotive.watchdog.IoOveruseStats internalStats,
                android.automotive.watchdog.PerStateBytes forgivenWriteBytes) {
            mIoOveruseStats = internalStats;
            mForgivenWriteBytes = forgivenWriteBytes;
        }

        IoOveruseStats getIoOveruseStats(boolean isKillable) {
            return toIoOveruseStatsBuilder(mIoOveruseStats, mTotalTimesKilled, isKillable).build();
        }

        boolean exceedsThreshold() {
            if (!hasUsage()) {
                return false;
            }
            android.automotive.watchdog.PerStateBytes remaining =
                    mIoOveruseStats.remainingWriteBytes;
            return remaining.foregroundBytes == 0 || remaining.backgroundBytes == 0
                    || remaining.garageModeBytes == 0;
        }

        void killed() {
            ++mTotalTimesKilled;
        }

        void resetStats() {
            mIoOveruseStats = null;
            mForgivenWriteBytes = DEFAULT_PER_STATE_BYTES;
            mForgivenOveruses = 0;
            mHistoricalNotForgivenOveruses = MISSING_VALUE;
            mTotalTimesKilled = 0;
        }
    }

    private final class ResourceOveruseListenerInfo implements IBinder.DeathRecipient {
        public final IResourceOveruseListener listener;
        public final @CarWatchdogManager.ResourceOveruseFlag int flag;
        public final int pid;
        public final int uid;
        public final boolean isListenerForSystem;

        ResourceOveruseListenerInfo(IResourceOveruseListener listener,
                @CarWatchdogManager.ResourceOveruseFlag int flag, int pid, int uid,
                boolean isListenerForSystem) {
            this.listener = listener;
            this.flag = flag;
            this.pid = pid;
            this.uid = uid;
            this.isListenerForSystem = isListenerForSystem;
        }

        @Override
        public void binderDied() {
            Slogf.w(TAG, "Resource overuse listener%s (pid: %d) died",
                    isListenerForSystem ? " for system" : "", pid);
            Consumer<SparseArray<ArrayList<ResourceOveruseListenerInfo>>> removeListenerInfo =
                    listenerInfosByUid -> {
                        ArrayList<ResourceOveruseListenerInfo> listenerInfos =
                                listenerInfosByUid.get(uid);
                        if (listenerInfos == null) {
                            return;
                        }
                        listenerInfos.remove(this);
                        if (listenerInfos.isEmpty()) {
                            listenerInfosByUid.remove(uid);
                        }
                    };
            synchronized (mLock) {
                if (isListenerForSystem) {
                    removeListenerInfo.accept(mOveruseSystemListenerInfosByUid);
                } else {
                    removeListenerInfo.accept(mOveruseListenerInfosByUid);
                }
            }
            unlinkToDeath();
        }

        public void notifyListener(@CarWatchdogManager.ResourceOveruseFlag int resourceType,
                int overusingUid, String overusingGenericPackageName,
                ResourceOveruseStats resourceOveruseStats) {
            if ((flag & resourceType) == 0) {
                return;
            }
            try {
                listener.onOveruse(resourceOveruseStats);
            } catch (RemoteException e) {
                Slogf.e(TAG, "Failed to notify %s (uid %d, pid: %d) of resource overuse by "
                                + "package(uid %d, generic package name '%s'): %s",
                        (isListenerForSystem ? "system listener" : "listener"), uid, pid,
                        overusingUid, overusingGenericPackageName, e);
            }
        }

        private void linkToDeath() throws RemoteException {
            listener.asBinder().linkToDeath(this, 0);
        }

        private void unlinkToDeath() {
            listener.asBinder().unlinkToDeath(this, 0);
        }
    }
}
