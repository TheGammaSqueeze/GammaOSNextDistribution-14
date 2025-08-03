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

import static com.android.car.watchdog.CarWatchdogService.DEBUG;
import static com.android.car.watchdog.TimeSource.ZONE_OFFSET;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.automotive.watchdog.PerStateBytes;
import android.car.builtin.util.Slogf;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.PackageKillableState.KillableState;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.internal.util.IntArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Defines the database to store/retrieve system resource stats history from local storage.
 */
public final class WatchdogStorage {
    private static final String TAG = CarLog.tagFor(WatchdogStorage.class);
    private static final int RETENTION_PERIOD_IN_DAYS = 30;
    private static final int CLOSE_DB_HELPER_DELAY_MS = 3000;

    /**
     * The database is clean when it is synchronized with the in-memory cache. Cannot start a
     * write while in this state.
     */
    private static final int DB_STATE_CLEAN = 1;

    /**
     * The database is dirty when it is not synchronized with the in-memory cache. When the
     * database is in this state, no write is in progress.
     */
    private static final int DB_STATE_DIRTY = 2;

    /**
     * Database write in progress. Cannot start a new write when the database is in this state.
     */
    private static final int DB_STATE_WRITE_IN_PROGRESS = 3;

    /**
     * The database enters this state when the database is marked dirty while a write is in
     * progress.
     */
    private static final int DB_STATE_WRITE_IN_PROGRESS_DIRTY = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"DB_STATE_"}, value = {
            DB_STATE_CLEAN,
            DB_STATE_DIRTY,
            DB_STATE_WRITE_IN_PROGRESS,
            DB_STATE_WRITE_IN_PROGRESS_DIRTY
    })
    private @interface DatabaseStateType{}

    public static final int FAILED_TRANSACTION = -1;
    /* Stats are stored on a daily basis. */
    public static final TemporalUnit STATS_TEMPORAL_UNIT = ChronoUnit.DAYS;
    /* Number of days to retain the stats in local storage. */
    public static final Period RETENTION_PERIOD =
            Period.ofDays(RETENTION_PERIOD_IN_DAYS).normalized();
    public static final String ZONE_MODIFIER = "utc";
    public static final String DATE_MODIFIER = "unixepoch";

    private final Handler mMainHandler;
    private final WatchdogDbHelper mDbHelper;
    private final ArrayMap<String, UserPackage> mUserPackagesByKey = new ArrayMap<>();
    private final ArrayMap<String, UserPackage> mUserPackagesById = new ArrayMap<>();
    private TimeSource mTimeSource;
    private final Object mLock = new Object();
    // Cache of today's I/O overuse stats collected during the previous boot. The data contained in
    // the cache won't change until the next boot, so it is safe to cache the data in memory.
    @GuardedBy("mLock")
    private final List<IoUsageStatsEntry> mTodayIoUsageStatsEntries = new ArrayList<>();
    @GuardedBy("mLock")
    private @DatabaseStateType int mCurrentDbState = DB_STATE_CLEAN;

    private final Runnable mCloseDbHelperRunnable = new Runnable() {
        @Override
        public void run() {
            mDbHelper.close();
        }
    };

    public WatchdogStorage(Context context, TimeSource timeSource) {
        this(context, /* useDataSystemCarDir= */ true, timeSource);
    }

    @VisibleForTesting
    WatchdogStorage(Context context, boolean useDataSystemCarDir, TimeSource timeSource) {
        mTimeSource = timeSource;
        mDbHelper = new WatchdogDbHelper(context, useDataSystemCarDir, mTimeSource);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /** Releases resources. */
    public void release() {
        mDbHelper.terminate();
    }

    /** Handles database shrink. */
    public void shrinkDatabase() {
        mDbHelper.onShrink(getDatabase(/* isWritable= */ true));
    }

    /**
     * Marks the database as dirty. The database is dirty when it is not synchronized with the
     * memory cache.
     */
    public void markDirty() {
        synchronized (mLock) {
            mCurrentDbState = mCurrentDbState == DB_STATE_WRITE_IN_PROGRESS
                    ? DB_STATE_WRITE_IN_PROGRESS_DIRTY : DB_STATE_DIRTY;
        }
        if (DEBUG) {
            Slogf.d(TAG, "Database marked dirty.");
        }
    }

    /**
     * Starts write to database only if database is dirty and no writing is in progress.
     *
     * @return {@code true} if start was successful, otherwise {@code false}.
     */
    public boolean startWrite() {
        synchronized (mLock) {
            if (mCurrentDbState != DB_STATE_DIRTY) {
                Slogf.e(TAG, "Cannot start a new write while the DB state is %s",
                        toDbStateString(mCurrentDbState));
                return false;
            }
            mCurrentDbState = DB_STATE_WRITE_IN_PROGRESS;
            return true;
        }
    }

    /** Ends write to database if write is in progress. */
    public void endWrite() {
        synchronized (mLock) {
            mCurrentDbState = mCurrentDbState == DB_STATE_CLEAN ? DB_STATE_CLEAN : DB_STATE_DIRTY;
        }
    }

    /** Marks the database as clean during an in progress database write. */
    public void markWriteSuccessful() {
        synchronized (mLock) {
            if (mCurrentDbState != DB_STATE_WRITE_IN_PROGRESS) {
                Slogf.e(TAG, "Failed to mark write successful as the current db state is %s",
                        toDbStateString(mCurrentDbState));
                return;
            }
            mCurrentDbState = DB_STATE_CLEAN;
        }
    }

    /** Saves the given user package settings entries and returns whether the change succeeded. */
    public boolean saveUserPackageSettings(List<UserPackageSettingsEntry> entries) {
        ArraySet<Integer> usersWithMissingIds = new ArraySet<>();
        boolean isWriteSuccessful = false;
        SQLiteDatabase db = getDatabase(/* isWritable= */ true);
        try {
            db.beginTransaction();
            for (int i = 0; i < entries.size(); ++i) {
                UserPackageSettingsEntry entry = entries.get(i);
                // Note: DO NOT replace existing entries in the UserPackageSettingsTable because
                // the replace operation deletes the old entry and inserts a new entry in the
                // table. This deletes the entries (in other tables) that are associated with
                // the old userPackageId. And also the userPackageId is auto-incremented.
                if (mUserPackagesByKey.get(UserPackage.getKey(entry.userId, entry.packageName))
                        != null && UserPackageSettingsTable.updateEntry(db, entry)) {
                    continue;
                }
                usersWithMissingIds.add(entry.userId);
                if (!UserPackageSettingsTable.replaceEntry(db, entry)) {
                    return false;
                }
            }
            db.setTransactionSuccessful();
            isWriteSuccessful = true;
        } finally {
            db.endTransaction();
        }
        populateUserPackages(db, usersWithMissingIds);
        return isWriteSuccessful;
    }

    /** Returns the user package setting entries. */
    public List<UserPackageSettingsEntry> getUserPackageSettings() {
        ArrayMap<String, UserPackageSettingsEntry> entriesById =
                UserPackageSettingsTable.querySettings(getDatabase(/* isWritable= */ false));
        List<UserPackageSettingsEntry> entries = new ArrayList<>(entriesById.size());
        for (int i = 0; i < entriesById.size(); ++i) {
            String userPackageId = entriesById.keyAt(i);
            UserPackageSettingsEntry entry = entriesById.valueAt(i);
            UserPackage userPackage = new UserPackage(userPackageId, entry.userId,
                    entry.packageName);
            mUserPackagesByKey.put(userPackage.getKey(), userPackage);
            mUserPackagesById.put(userPackage.userPackageId, userPackage);
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Saves the given I/O usage stats.
     *
     * @return the number of saved entries, on success. Otherwise, returns
     *     {@code FAILED_TRANSACTION}
     */
    public int saveIoUsageStats(List<IoUsageStatsEntry> entries) {
        return saveIoUsageStats(entries, /* shouldCheckRetention= */ true);
    }

    /** Returns the saved I/O usage stats for the current day. */
    public List<IoUsageStatsEntry> getTodayIoUsageStats() {
        synchronized (mLock) {
            if (!mTodayIoUsageStatsEntries.isEmpty()) {
                return new ArrayList<>(mTodayIoUsageStatsEntries);
            }
            long includingStartEpochSeconds = mTimeSource.getCurrentDate().toEpochSecond();
            long excludingEndEpochSeconds = mTimeSource.getCurrentDateTime().toEpochSecond();
            ArrayMap<String, WatchdogPerfHandler.PackageIoUsage> ioUsagesById;
            ioUsagesById = IoUsageStatsTable.queryStats(getDatabase(/* isWritable= */ false),
                    includingStartEpochSeconds, excludingEndEpochSeconds);
            for (int i = 0; i < ioUsagesById.size(); ++i) {
                String userPackageId = ioUsagesById.keyAt(i);
                UserPackage userPackage = mUserPackagesById.get(userPackageId);
                if (userPackage == null) {
                    Slogf.w(TAG,
                            "Failed to find user id and package name for user package id: '%s'",
                            userPackageId);
                    continue;
                }
                mTodayIoUsageStatsEntries.add(new IoUsageStatsEntry(
                        userPackage.userId, userPackage.packageName,
                        ioUsagesById.valueAt(i)));
            }
            return new ArrayList<>(mTodayIoUsageStatsEntries);
        }
    }

    /** Deletes user package settings and resource overuse stats. */
    public void deleteUserPackage(@UserIdInt int userId, String packageName) {
        UserPackage userPackage = mUserPackagesByKey.get(UserPackage.getKey(userId, packageName));
        if (userPackage == null) {
            Slogf.e(TAG, "Failed to find user package id for user id '%d' and package '%s",
                    userId, packageName);
            return;
        }
        mUserPackagesByKey.remove(userPackage.getKey());
        mUserPackagesById.remove(userPackage.userPackageId);
        UserPackageSettingsTable.deleteUserPackage(getDatabase(/* isWritable= */ true), userId,
                    packageName);
    }

    /**
     * Returns the aggregated historical I/O overuse stats for the given user package or
     * {@code null} when stats are not available.
     */
    @Nullable
    public IoOveruseStats getHistoricalIoOveruseStats(@UserIdInt int userId, String packageName,
            int numDaysAgo) {
        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        long includingStartEpochSeconds = currentDate.minusDays(numDaysAgo).toEpochSecond();
        long excludingEndEpochSeconds = currentDate.toEpochSecond();
        UserPackage userPackage = mUserPackagesByKey.get(UserPackage.getKey(userId, packageName));
        if (userPackage == null) {
            /* Packages without historical stats don't have userPackage entry. */
            return null;
        }
        return IoUsageStatsTable.queryIoOveruseStatsForUserPackageId(
                getDatabase(/* isWritable= */ false), userPackage.userPackageId,
                includingStartEpochSeconds, excludingEndEpochSeconds);
    }

    /**
     * Returns daily system-level I/O usage summaries for the given period or {@code null} when
     * summaries are not available.
     */
    public @Nullable List<AtomsProto.CarWatchdogDailyIoUsageSummary> getDailySystemIoUsageSummaries(
            long minSystemTotalWrittenBytes, long includingStartEpochSeconds,
            long excludingEndEpochSeconds) {
        List<AtomsProto.CarWatchdogDailyIoUsageSummary> dailyIoUsageSummaries =
                IoUsageStatsTable.queryDailySystemIoUsageSummaries(
                        getDatabase(/* isWritable= */ false),
                        includingStartEpochSeconds, excludingEndEpochSeconds);
        if (dailyIoUsageSummaries == null) {
            return null;
        }
        long systemTotalWrittenBytes = 0;
        for (int i = 0; i < dailyIoUsageSummaries.size(); i++) {
            AtomsProto.CarWatchdogPerStateBytes writtenBytes =
                    dailyIoUsageSummaries.get(i).getWrittenBytes();
            systemTotalWrittenBytes += writtenBytes.getForegroundBytes()
                    + writtenBytes.getBackgroundBytes() + writtenBytes.getGarageModeBytes();
        }
        if (systemTotalWrittenBytes < minSystemTotalWrittenBytes) {
            return null;
        }
        return dailyIoUsageSummaries;
    }

    /**
     * Returns top N disk I/O users' daily I/O usage summaries for the given period or {@code null}
     * when summaries are not available.
     */
    public @Nullable List<UserPackageDailySummaries> getTopUsersDailyIoUsageSummaries(
            int numTopUsers, long minSystemTotalWrittenBytes, long includingStartEpochSeconds,
            long excludingEndEpochSeconds) {
        ArrayMap<String, List<AtomsProto.CarWatchdogDailyIoUsageSummary>> summariesById;
        SQLiteDatabase db = getDatabase(/* isWritable= */ false);
        long systemTotalWrittenBytes = IoUsageStatsTable.querySystemTotalWrittenBytes(db,
                includingStartEpochSeconds, excludingEndEpochSeconds);
        if (systemTotalWrittenBytes < minSystemTotalWrittenBytes) {
            return null;
        }
        summariesById = IoUsageStatsTable.queryTopUsersDailyIoUsageSummaries(db,
                numTopUsers, includingStartEpochSeconds, excludingEndEpochSeconds);
        if (summariesById == null) {
            return null;
        }
        ArrayList<UserPackageDailySummaries> userPackageDailySummaries = new ArrayList<>();
        for (int i = 0; i < summariesById.size(); ++i) {
            String id = summariesById.keyAt(i);
            UserPackage userPackage = mUserPackagesById.get(id);
            if (userPackage == null) {
                Slogf.w(TAG,
                        "Failed to find user id and package name for user package id: '%s'",
                        id);
                continue;
            }
            userPackageDailySummaries.add(new UserPackageDailySummaries(userPackage.userId,
                    userPackage.packageName, summariesById.valueAt(i)));
        }
        userPackageDailySummaries
                .sort(Comparator.comparingLong(UserPackageDailySummaries::getTotalWrittenBytes)
                        .reversed());
        return userPackageDailySummaries;
    }

    /**
     * Returns the aggregated historical overuses minus the forgiven overuses for all saved
     * packages. Forgiven overuses are overuses that have been attributed previously to a package's
     * recurring overuse.
     */
    public List<NotForgivenOverusesEntry> getNotForgivenHistoricalIoOveruses(int numDaysAgo) {
        ZonedDateTime currentDate =
                mTimeSource.now().atZone(ZONE_OFFSET).truncatedTo(STATS_TEMPORAL_UNIT);
        long includingStartEpochSeconds = currentDate.minusDays(numDaysAgo).toEpochSecond();
        long excludingEndEpochSeconds = currentDate.toEpochSecond();
        ArrayMap<String, Integer> notForgivenOverusesById;
        notForgivenOverusesById =
                IoUsageStatsTable.queryNotForgivenHistoricalOveruses(
                        getDatabase(/* isWritable= */ false), includingStartEpochSeconds,
                        excludingEndEpochSeconds);
        List<NotForgivenOverusesEntry> notForgivenOverusesEntries = new ArrayList<>();
        for (int i = 0; i < notForgivenOverusesById.size(); i++) {
            String id = notForgivenOverusesById.keyAt(i);
            UserPackage userPackage = mUserPackagesById.get(id);
            if (userPackage == null) {
                Slogf.w(TAG,
                        "Failed to find user id and package name for user package id: '%s'",
                        id);
                continue;
            }
            notForgivenOverusesEntries.add(new NotForgivenOverusesEntry(userPackage.userId,
                    userPackage.packageName, notForgivenOverusesById.valueAt(i)));
        }
        return notForgivenOverusesEntries;
    }

    /**
     * Forgives all historical overuses between yesterday and {@code numDaysAgo}
     * for a list of specific {@code userIds} and {@code packageNames}.
     */
    public void forgiveHistoricalOveruses(SparseArray<List<String>> packagesByUserId,
            int numDaysAgo) {
        if (packagesByUserId.size() == 0) {
            Slogf.w(TAG, "No I/O usage stats provided to forgive historical overuses.");
            return;
        }
        ZonedDateTime currentDate =
                mTimeSource.now().atZone(ZONE_OFFSET).truncatedTo(STATS_TEMPORAL_UNIT);
        long includingStartEpochSeconds = currentDate.minusDays(numDaysAgo).toEpochSecond();
        long excludingEndEpochSeconds = currentDate.toEpochSecond();
        List<String> userPackageIds = new ArrayList<>();
        for (int i = 0; i < packagesByUserId.size(); i++) {
            int userId = packagesByUserId.keyAt(i);
            List<String> packages = packagesByUserId.valueAt(i);
            for (int pkgIdx = 0; pkgIdx < packages.size(); pkgIdx++) {
                UserPackage userPackage =
                        mUserPackagesByKey.get(UserPackage.getKey(userId, packages.get(pkgIdx)));
                if (userPackage == null) {
                    // Packages without historical stats don't have userPackage entry.
                    continue;
                }
                userPackageIds.add(userPackage.userPackageId);
            }
        }
        IoUsageStatsTable.forgiveHistoricalOverusesForPackage(getDatabase(/* isWritable= */ true),
                userPackageIds, includingStartEpochSeconds, excludingEndEpochSeconds);
    }

    /**
     * Deletes all user package settings and resource stats for all non-alive users.
     *
     * @param aliveUserIds Array of alive user ids.
     */
    public void syncUsers(int[] aliveUserIds) {
        IntArray aliveUsers = IntArray.wrap(aliveUserIds);
        for (int i = mUserPackagesByKey.size() - 1; i >= 0; --i) {
            UserPackage userPackage = mUserPackagesByKey.valueAt(i);
            if (aliveUsers.indexOf(userPackage.userId) == -1) {
                mUserPackagesByKey.removeAt(i);
                mUserPackagesById.remove(userPackage.userPackageId);
            }
        }
        UserPackageSettingsTable.syncUserPackagesWithAliveUsers(getDatabase(/* isWritable= */ true),
                    aliveUsers);
    }

    @VisibleForTesting
    int saveIoUsageStats(List<IoUsageStatsEntry> entries, boolean shouldCheckRetention) {
        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        List<ContentValues> rows = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); ++i) {
            IoUsageStatsEntry entry = entries.get(i);
            UserPackage userPackage = mUserPackagesByKey.get(
                    UserPackage.getKey(entry.userId, entry.packageName));
            if (userPackage == null) {
                Slogf.e(TAG, "Failed to find user package id for user id '%d' and package '%s",
                        entry.userId, entry.packageName);
                continue;
            }
            android.automotive.watchdog.IoOveruseStats ioOveruseStats =
                    entry.ioUsage.getInternalIoOveruseStats();
            ZonedDateTime statsDate = Instant.ofEpochSecond(ioOveruseStats.startTime)
                    .atZone(ZONE_OFFSET).truncatedTo(STATS_TEMPORAL_UNIT);
            if (shouldCheckRetention && STATS_TEMPORAL_UNIT.between(statsDate, currentDate)
                    >= RETENTION_PERIOD.get(STATS_TEMPORAL_UNIT)) {
                continue;
            }
            long statsDateEpochSeconds = statsDate.toEpochSecond();
            rows.add(IoUsageStatsTable.getContentValues(
                    userPackage.userPackageId, entry, statsDateEpochSeconds));
        }
        return atomicReplaceEntries(getDatabase(/*isWritable=*/ true),
                    IoUsageStatsTable.TABLE_NAME, rows);
    }

    @VisibleForTesting
    boolean hasPendingCloseDbHelperMessage() {
        return mMainHandler.hasCallbacks(mCloseDbHelperRunnable);
    }

    private void populateUserPackages(SQLiteDatabase db, ArraySet<Integer> users) {
        List<UserPackage> userPackages = UserPackageSettingsTable.queryUserPackages(db, users);
        if (userPackages == null) {
            return;
        }
        for (int i = 0; i < userPackages.size(); ++i) {
            UserPackage userPackage = userPackages.get(i);
            mUserPackagesByKey.put(userPackage.getKey(), userPackage);
            mUserPackagesById.put(userPackage.userPackageId, userPackage);
        }
    }

    private SQLiteDatabase getDatabase(boolean isWritable) {
        mMainHandler.removeCallbacks(mCloseDbHelperRunnable);
        mMainHandler.postDelayed(mCloseDbHelperRunnable, CLOSE_DB_HELPER_DELAY_MS);
        return isWritable ? mDbHelper.getWritableDatabase() : mDbHelper.getReadableDatabase();
    }

    /**
     * Atomically replace rows in a database table.
     *
     * @return the number of replaced entries, on success. Otherwise, returns
     *     {@code FAILED_TRANSACTION}
     */
    private static int atomicReplaceEntries(SQLiteDatabase db, String tableName,
            List<ContentValues> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        try {
            db.beginTransaction();
            for (int i = 0; i < rows.size(); ++i) {
                try {
                    if (db.replaceOrThrow(tableName, null, rows.get(i)) == -1) {
                        Slogf.e(TAG, "Failed to insert %s entry [%s]", tableName, rows.get(i));
                        return FAILED_TRANSACTION;
                    }
                } catch (SQLException e) {
                    Slogf.e(TAG, e, "Failed to insert %s entry [%s]", tableName, rows.get(i));
                    return FAILED_TRANSACTION;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return rows.size();
    }

    private static String toDbStateString(int dbState) {
        switch (dbState) {
            case DB_STATE_CLEAN:
                return "DB_STATE_CLEAN";
            case DB_STATE_DIRTY:
                return "DB_STATE_DIRTY";
            case DB_STATE_WRITE_IN_PROGRESS:
                return "DB_STATE_WRITE_IN_PROGRESS";
            case DB_STATE_WRITE_IN_PROGRESS_DIRTY:
                return "DB_STATE_WRITE_IN_PROGRESS_DIRTY";
            default:
                return "UNKNOWN";
        }
    }

    /** Defines the user package settings entry stored in the UserPackageSettingsTable. */
    static final class UserPackageSettingsEntry {
        public final @UserIdInt int userId;
        public final String packageName;
        public final @KillableState int killableState;
        public final long killableStateLastModifiedEpochSeconds;

        UserPackageSettingsEntry(@UserIdInt int userId, String packageName,
                @KillableState int killableState, long killableStateLastModifiedEpochSeconds) {
            this.userId = userId;
            this.packageName = packageName;
            this.killableState = killableState;
            this.killableStateLastModifiedEpochSeconds = killableStateLastModifiedEpochSeconds;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof UserPackageSettingsEntry)) {
                return false;
            }
            UserPackageSettingsEntry other = (UserPackageSettingsEntry) obj;
            return userId == other.userId && packageName.equals(other.packageName)
                    && killableState == other.killableState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, packageName, killableState);
        }

        @Override
        public String toString() {
            return new StringBuilder().append("UserPackageSettingsEntry{userId: ").append(userId)
                    .append(", packageName: ").append(packageName)
                    .append(", killableState: ").append(killableState).append('}')
                    .toString();
        }
    }

    /** Defines the daily summaries for user packages. */
    static final class UserPackageDailySummaries {
        public final @UserIdInt int userId;
        public final String packageName;
        public final List<AtomsProto.CarWatchdogDailyIoUsageSummary> dailyIoUsageSummaries;
        private final long mTotalWrittenBytes;

        UserPackageDailySummaries(@UserIdInt int userId, String packageName,
                List<AtomsProto.CarWatchdogDailyIoUsageSummary> dailyIoUsageSummaries) {
            this.userId = userId;
            this.packageName = packageName;
            this.dailyIoUsageSummaries = dailyIoUsageSummaries;
            this.mTotalWrittenBytes = computeTotalWrittenBytes();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof UserPackageDailySummaries)) {
                return false;
            }
            UserPackageDailySummaries other = (UserPackageDailySummaries) obj;
            return userId == other.userId && packageName.equals(other.packageName)
                    && dailyIoUsageSummaries.equals(other.dailyIoUsageSummaries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, packageName, dailyIoUsageSummaries, mTotalWrittenBytes);
        }

        @Override
        public String toString() {
            return new StringBuilder().append("UserPackageDailySummaries{userId: ").append(userId)
                    .append(", packageName: ").append(packageName)
                    .append(", dailyIoUsageSummaries: ").append(dailyIoUsageSummaries).append('}')
                    .toString();
        }

        long getTotalWrittenBytes() {
            return mTotalWrittenBytes;
        }

        long computeTotalWrittenBytes() {
            long totalBytes = 0;
            for (int i = 0; i < dailyIoUsageSummaries.size(); ++i) {
                AtomsProto.CarWatchdogPerStateBytes writtenBytes =
                        dailyIoUsageSummaries.get(i).getWrittenBytes();
                if (writtenBytes.hasForegroundBytes()) {
                    totalBytes += writtenBytes.getForegroundBytes();
                }
                if (writtenBytes.hasBackgroundBytes()) {
                    totalBytes += writtenBytes.getBackgroundBytes();
                }
                if (writtenBytes.hasGarageModeBytes()) {
                    totalBytes += writtenBytes.getGarageModeBytes();
                }
            }
            return totalBytes;
        }
    }

    /**
     * Defines the contents and queries for the user package settings table.
     */
    static final class UserPackageSettingsTable {
        public static final String TABLE_NAME = "user_package_settings";
        public static final String COLUMN_USER_PACKAGE_ID = "user_package_id";
        public static final String COLUMN_PACKAGE_NAME = "package_name";
        public static final String COLUMN_USER_ID = "user_id";
        public static final String COLUMN_KILLABLE_STATE = "killable_state";
        public static final String COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH =
                "killable_state_last_modified_epoch";

        public static void createTable(SQLiteDatabase db) {
            StringBuilder createCommand = new StringBuilder();
            createCommand.append("CREATE TABLE ").append(TABLE_NAME).append(" (")
                    // Maximum value for COLUMN_USER_PACKAGE_ID is the max integer size supported by
                    // the database. Thus, the number of entries that can be inserted into this
                    // table is bound by this upper limit for the lifetime of the device
                    // (i.e., Even when a userId is reused, the previous user_package_ids for
                    // the corresponding userId won't be reused). When the IDs are exhausted,
                    // any new insert operation will result in the error "database or disk is full".
                    .append(COLUMN_USER_PACKAGE_ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ")
                    .append(COLUMN_PACKAGE_NAME).append(" TEXT NOT NULL, ")
                    .append(COLUMN_USER_ID).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_KILLABLE_STATE).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH).append(" INTEGER NOT NULL, ")
                    .append("UNIQUE(").append(COLUMN_PACKAGE_NAME)
                    .append(", ").append(COLUMN_USER_ID).append("))");
            db.execSQL(createCommand.toString());
            Slogf.i(TAG, "Successfully created the %s table in the %s database version %d",
                    TABLE_NAME, WatchdogDbHelper.DATABASE_NAME, WatchdogDbHelper.DATABASE_VERSION);
        }

        public static boolean updateEntry(SQLiteDatabase db, UserPackageSettingsEntry entry) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_KILLABLE_STATE, entry.killableState);
            values.put(COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH,
                    entry.killableStateLastModifiedEpochSeconds);

            StringBuilder whereClause = new StringBuilder(COLUMN_PACKAGE_NAME).append(" = ? AND ")
                            .append(COLUMN_USER_ID).append(" = ?");
            String[] whereArgs = new String[]{entry.packageName, String.valueOf(entry.userId)};

            if (db.update(TABLE_NAME, values, whereClause.toString(), whereArgs) < 1) {
                Slogf.e(TAG, "Failed to update %d entry with package name: %s and user id: %d",
                        TABLE_NAME, entry.packageName, entry.userId);
                return false;
            }
            return true;
        }

        public static boolean replaceEntry(SQLiteDatabase db, UserPackageSettingsEntry entry) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_USER_ID, entry.userId);
            values.put(COLUMN_PACKAGE_NAME, entry.packageName);
            values.put(COLUMN_KILLABLE_STATE, entry.killableState);
            values.put(COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH,
                    entry.killableStateLastModifiedEpochSeconds);

            if (db.replaceOrThrow(UserPackageSettingsTable.TABLE_NAME, null, values) == -1) {
                Slogf.e(TAG, "Failed to replace %s entry [%s]", TABLE_NAME, values);
                return false;
            }
            return true;
        }

        public static ArrayMap<String, UserPackageSettingsEntry> querySettings(SQLiteDatabase db) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT ")
                    .append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append(COLUMN_USER_ID).append(", ")
                    .append(COLUMN_PACKAGE_NAME).append(", ")
                    .append(COLUMN_KILLABLE_STATE).append(", ")
                    .append(COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH)
                    .append(" FROM ").append(TABLE_NAME);

            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), new String[]{})) {
                ArrayMap<String, UserPackageSettingsEntry> entriesById = new ArrayMap<>(
                        cursor.getCount());
                while (cursor.moveToNext()) {
                    entriesById.put(cursor.getString(0), new UserPackageSettingsEntry(
                            cursor.getInt(1), cursor.getString(2), cursor.getInt(3),
                            cursor.getInt(4)));
                }
                return entriesById;
            }
        }

        /**
         * Returns the UserPackage entries for the given users. When no users are provided or no
         * data returned by the DB query, returns null.
         */
        @Nullable
        public static List<UserPackage> queryUserPackages(SQLiteDatabase db,
                ArraySet<Integer> users) {
            int numUsers = users.size();
            if (numUsers == 0) {
                return null;
            }
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT ")
                    .append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append(COLUMN_USER_ID).append(", ")
                    .append(COLUMN_PACKAGE_NAME)
                    .append(" FROM ").append(TABLE_NAME)
                    .append(" WHERE ").append(COLUMN_USER_ID).append(" IN (");
            for (int i = 0; i < numUsers; ++i) {
                queryBuilder.append(users.valueAt(i));
                if (i < numUsers - 1) {
                    queryBuilder.append(", ");
                }
            }
            queryBuilder.append(")");
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), new String[]{})) {
                if (cursor.getCount() == 0) {
                    return null;
                }
                List<UserPackage> userPackages = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    userPackages.add(new UserPackage(
                            cursor.getString(0), cursor.getInt(1), cursor.getString(2)));
                }
                return userPackages;
            }
        }

        public static void deleteUserPackage(SQLiteDatabase db, @UserIdInt int userId,
                String packageName) {
            String whereClause = COLUMN_USER_ID + "= ? and " + COLUMN_PACKAGE_NAME + "= ?";
            String[] whereArgs = new String[]{String.valueOf(userId), packageName};
            int deletedRows = db.delete(TABLE_NAME, whereClause, whereArgs);
            Slogf.i(TAG, "Deleted %d user package settings db rows for user %d and package %s",
                    deletedRows, userId, packageName);
        }

        public static void syncUserPackagesWithAliveUsers(SQLiteDatabase db, IntArray aliveUsers) {
            StringBuilder queryBuilder = new StringBuilder();
            for (int i = 0; i < aliveUsers.size(); ++i) {
                if (i == 0) {
                    queryBuilder.append(COLUMN_USER_ID).append(" NOT IN (");
                } else {
                    queryBuilder.append(", ");
                }
                queryBuilder.append(aliveUsers.get(i));
                if (i == aliveUsers.size() - 1) {
                    queryBuilder.append(")");
                }
            }
            int deletedRows = db.delete(TABLE_NAME, queryBuilder.toString(), new String[]{});
            Slogf.i(TAG, "Deleted %d user package settings db rows while syncing with alive users",
                    deletedRows);
        }
    }

    /** Defines the I/O usage entry stored in the IoUsageStatsTable. */
    static final class IoUsageStatsEntry {
        public final @UserIdInt int userId;
        public final String packageName;
        public final WatchdogPerfHandler.PackageIoUsage ioUsage;

        IoUsageStatsEntry(@UserIdInt int userId,
                String packageName, WatchdogPerfHandler.PackageIoUsage ioUsage) {
            this.userId = userId;
            this.packageName = packageName;
            this.ioUsage = ioUsage;
        }
    }

    /** Defines the not forgiven overuses stored in the IoUsageStatsTable. */
    static final class NotForgivenOverusesEntry {
        public final @UserIdInt int userId;
        public final String packageName;
        public final int notForgivenOveruses;

        NotForgivenOverusesEntry(@UserIdInt int userId,
                String packageName, int notForgivenOveruses) {
            this.userId = userId;
            this.packageName = packageName;
            this.notForgivenOveruses = notForgivenOveruses;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof NotForgivenOverusesEntry)) {
                return false;
            }
            NotForgivenOverusesEntry other = (NotForgivenOverusesEntry) obj;
            return userId == other.userId
                    && packageName.equals(other.packageName)
                    && notForgivenOveruses == other.notForgivenOveruses;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, packageName, notForgivenOveruses);
        }

        @Override
        public String toString() {
            return "NotForgivenOverusesEntry {UserId: " + userId
                    + ", Package name: " + packageName
                    + ", Not forgiven overuses: " + notForgivenOveruses + "}";
        }
    }

    /**
     * Defines the contents and queries for the I/O usage stats table.
     */
    static final class IoUsageStatsTable {
        public static final String TABLE_NAME = "io_usage_stats";
        public static final String COLUMN_USER_PACKAGE_ID = "user_package_id";
        public static final String COLUMN_DATE_EPOCH = "date_epoch";
        public static final String COLUMN_NUM_OVERUSES = "num_overuses";
        public static final String COLUMN_NUM_FORGIVEN_OVERUSES =  "num_forgiven_overuses";
        public static final String COLUMN_NUM_TIMES_KILLED = "num_times_killed";
        public static final String COLUMN_WRITTEN_FOREGROUND_BYTES = "written_foreground_bytes";
        public static final String COLUMN_WRITTEN_BACKGROUND_BYTES = "written_background_bytes";
        public static final String COLUMN_WRITTEN_GARAGE_MODE_BYTES = "written_garage_mode_bytes";
        /* Below columns will be null for historical stats i.e., when the date != current date. */
        public static final String COLUMN_REMAINING_FOREGROUND_WRITE_BYTES =
                "remaining_foreground_write_bytes";
        public static final String COLUMN_REMAINING_BACKGROUND_WRITE_BYTES =
                "remaining_background_write_bytes";
        public static final String COLUMN_REMAINING_GARAGE_MODE_WRITE_BYTES =
                "remaining_garage_mode_write_bytes";
        public static final String COLUMN_FORGIVEN_FOREGROUND_WRITE_BYTES =
                "forgiven_foreground_write_bytes";
        public static final String COLUMN_FORGIVEN_BACKGROUND_WRITE_BYTES =
                "forgiven_background_write_bytes";
        public static final String COLUMN_FORGIVEN_GARAGE_MODE_WRITE_BYTES =
                "forgiven_garage_mode_write_bytes";

        public static void createTable(SQLiteDatabase db) {
            StringBuilder createCommand = new StringBuilder();
            createCommand.append("CREATE TABLE ").append(TABLE_NAME).append(" (")
                    .append(COLUMN_USER_PACKAGE_ID).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_DATE_EPOCH).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_NUM_OVERUSES).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_NUM_FORGIVEN_OVERUSES).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_NUM_TIMES_KILLED).append(" INTEGER NOT NULL, ")
                    .append(COLUMN_WRITTEN_FOREGROUND_BYTES).append(" INTEGER, ")
                    .append(COLUMN_WRITTEN_BACKGROUND_BYTES).append(" INTEGER, ")
                    .append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append(" INTEGER, ")
                    .append(COLUMN_REMAINING_FOREGROUND_WRITE_BYTES).append(" INTEGER, ")
                    .append(COLUMN_REMAINING_BACKGROUND_WRITE_BYTES).append(" INTEGER, ")
                    .append(COLUMN_REMAINING_GARAGE_MODE_WRITE_BYTES).append(" INTEGER, ")
                    .append(COLUMN_FORGIVEN_FOREGROUND_WRITE_BYTES).append(" INTEGER, ")
                    .append(COLUMN_FORGIVEN_BACKGROUND_WRITE_BYTES).append(" INTEGER, ")
                    .append(COLUMN_FORGIVEN_GARAGE_MODE_WRITE_BYTES).append(" INTEGER, ")
                    .append("PRIMARY KEY (").append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append(COLUMN_DATE_EPOCH).append("), FOREIGN KEY (")
                    .append(COLUMN_USER_PACKAGE_ID).append(") REFERENCES ")
                    .append(UserPackageSettingsTable.TABLE_NAME).append(" (")
                    .append(UserPackageSettingsTable.COLUMN_USER_PACKAGE_ID)
                    .append(") ON DELETE CASCADE)");
            db.execSQL(createCommand.toString());
            Slogf.i(TAG, "Successfully created the %s table in the %s database version %d",
                    TABLE_NAME, WatchdogDbHelper.DATABASE_NAME, WatchdogDbHelper.DATABASE_VERSION);
        }

        public static ContentValues getContentValues(
                String userPackageId, IoUsageStatsEntry entry, long statsDateEpochSeconds) {
            android.automotive.watchdog.IoOveruseStats ioOveruseStats =
                    entry.ioUsage.getInternalIoOveruseStats();
            ContentValues values = new ContentValues();
            values.put(COLUMN_USER_PACKAGE_ID, userPackageId);
            values.put(COLUMN_DATE_EPOCH, statsDateEpochSeconds);
            values.put(COLUMN_NUM_OVERUSES, ioOveruseStats.totalOveruses);
            values.put(COLUMN_NUM_FORGIVEN_OVERUSES, entry.ioUsage.getForgivenOveruses());
            values.put(COLUMN_NUM_TIMES_KILLED, entry.ioUsage.getTotalTimesKilled());
            values.put(
                    COLUMN_WRITTEN_FOREGROUND_BYTES, ioOveruseStats.writtenBytes.foregroundBytes);
            values.put(
                    COLUMN_WRITTEN_BACKGROUND_BYTES, ioOveruseStats.writtenBytes.backgroundBytes);
            values.put(
                    COLUMN_WRITTEN_GARAGE_MODE_BYTES, ioOveruseStats.writtenBytes.garageModeBytes);
            values.put(COLUMN_REMAINING_FOREGROUND_WRITE_BYTES,
                    ioOveruseStats.remainingWriteBytes.foregroundBytes);
            values.put(COLUMN_REMAINING_BACKGROUND_WRITE_BYTES,
                    ioOveruseStats.remainingWriteBytes.backgroundBytes);
            values.put(COLUMN_REMAINING_GARAGE_MODE_WRITE_BYTES,
                    ioOveruseStats.remainingWriteBytes.garageModeBytes);
            android.automotive.watchdog.PerStateBytes forgivenWriteBytes =
                    entry.ioUsage.getForgivenWriteBytes();
            values.put(COLUMN_FORGIVEN_FOREGROUND_WRITE_BYTES, forgivenWriteBytes.foregroundBytes);
            values.put(COLUMN_FORGIVEN_BACKGROUND_WRITE_BYTES, forgivenWriteBytes.backgroundBytes);
            values.put(COLUMN_FORGIVEN_GARAGE_MODE_WRITE_BYTES, forgivenWriteBytes.garageModeBytes);
            return values;
        }

        public static ArrayMap<String, WatchdogPerfHandler.PackageIoUsage> queryStats(
                SQLiteDatabase db, long includingStartEpochSeconds, long excludingEndEpochSeconds) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT ")
                    .append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append("MIN(").append(COLUMN_DATE_EPOCH).append("), ")
                    .append("SUM(").append(COLUMN_NUM_OVERUSES).append("), ")
                    .append("SUM(").append(COLUMN_NUM_FORGIVEN_OVERUSES).append("), ")
                    .append("SUM(").append(COLUMN_NUM_TIMES_KILLED).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_BACKGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_REMAINING_FOREGROUND_WRITE_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_REMAINING_BACKGROUND_WRITE_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_REMAINING_GARAGE_MODE_WRITE_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_FORGIVEN_FOREGROUND_WRITE_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_FORGIVEN_BACKGROUND_WRITE_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_FORGIVEN_GARAGE_MODE_WRITE_BYTES).append(") ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(">= ? and ")
                    .append(COLUMN_DATE_EPOCH).append("< ? GROUP BY ")
                    .append(COLUMN_USER_PACKAGE_ID);
            String[] selectionArgs = new String[]{String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds)};

            ArrayMap<String, WatchdogPerfHandler.PackageIoUsage> ioUsageById = new ArrayMap<>();
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), selectionArgs)) {
                while (cursor.moveToNext()) {
                    android.automotive.watchdog.IoOveruseStats ioOveruseStats =
                            new android.automotive.watchdog.IoOveruseStats();
                    ioOveruseStats.startTime = cursor.getLong(1);
                    ioOveruseStats.durationInSeconds =
                            excludingEndEpochSeconds - includingStartEpochSeconds;
                    ioOveruseStats.totalOveruses = cursor.getInt(2);
                    ioOveruseStats.writtenBytes = new PerStateBytes();
                    ioOveruseStats.writtenBytes.foregroundBytes = cursor.getLong(5);
                    ioOveruseStats.writtenBytes.backgroundBytes = cursor.getLong(6);
                    ioOveruseStats.writtenBytes.garageModeBytes = cursor.getLong(7);
                    ioOveruseStats.remainingWriteBytes = new PerStateBytes();
                    ioOveruseStats.remainingWriteBytes.foregroundBytes = cursor.getLong(8);
                    ioOveruseStats.remainingWriteBytes.backgroundBytes = cursor.getLong(9);
                    ioOveruseStats.remainingWriteBytes.garageModeBytes = cursor.getLong(10);
                    PerStateBytes forgivenWriteBytes = new PerStateBytes();
                    forgivenWriteBytes.foregroundBytes = cursor.getLong(11);
                    forgivenWriteBytes.backgroundBytes = cursor.getLong(12);
                    forgivenWriteBytes.garageModeBytes = cursor.getLong(13);

                    ioUsageById.put(cursor.getString(0), new WatchdogPerfHandler.PackageIoUsage(
                            ioOveruseStats, forgivenWriteBytes,
                            /* forgivenOveruses= */ cursor.getInt(3),
                            /* totalTimesKilled= */ cursor.getInt(4)));
                }
            }
            return ioUsageById;
        }

        public static @Nullable IoOveruseStats queryIoOveruseStatsForUserPackageId(
                SQLiteDatabase db, String userPackageId, long includingStartEpochSeconds,
                long excludingEndEpochSeconds) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT SUM(").append(COLUMN_NUM_OVERUSES).append("), ")
                    .append("SUM(").append(COLUMN_NUM_TIMES_KILLED).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_BACKGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append("), ")
                    .append("MIN(").append(COLUMN_DATE_EPOCH).append(") ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_USER_PACKAGE_ID).append("=? and ")
                    .append(COLUMN_DATE_EPOCH).append(" >= ? and ")
                    .append(COLUMN_DATE_EPOCH).append("< ?");
            String[] selectionArgs = new String[]{userPackageId,
                    String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds)};
            long totalOveruses = 0;
            long totalTimesKilled = 0;
            long totalBytesWritten = 0;
            long earliestEpochSecond = excludingEndEpochSeconds;
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), selectionArgs)) {
                if (cursor.getCount() == 0) {
                    return null;
                }
                while (cursor.moveToNext()) {
                    totalOveruses += cursor.getLong(0);
                    totalTimesKilled += cursor.getLong(1);
                    totalBytesWritten += cursor.getLong(2) + cursor.getLong(3) + cursor.getLong(4);
                    earliestEpochSecond = Math.min(cursor.getLong(5), earliestEpochSecond);
                }
            }
            if (totalBytesWritten == 0) {
                return null;
            }
            long durationInSeconds = excludingEndEpochSeconds - earliestEpochSecond;
            IoOveruseStats.Builder statsBuilder = new IoOveruseStats.Builder(
                    earliestEpochSecond, durationInSeconds);
            statsBuilder.setTotalOveruses(totalOveruses);
            statsBuilder.setTotalTimesKilled(totalTimesKilled);
            statsBuilder.setTotalBytesWritten(totalBytesWritten);
            return statsBuilder.build();
        }

        public static ArrayMap<String, Integer> queryNotForgivenHistoricalOveruses(
                SQLiteDatabase db, long includingStartEpochSeconds, long excludingEndEpochSeconds) {
            StringBuilder queryBuilder = new StringBuilder("SELECT ")
                    .append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append("SUM(").append(COLUMN_NUM_OVERUSES).append("), ")
                    .append("SUM(").append(COLUMN_NUM_FORGIVEN_OVERUSES).append(") ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(" >= ? and ")
                    .append(COLUMN_DATE_EPOCH).append("< ? GROUP BY ")
                    .append(COLUMN_USER_PACKAGE_ID);
            String[] selectionArgs = new String[]{String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds)};
            ArrayMap<String, Integer> notForgivenOverusesById = new ArrayMap<>();
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), selectionArgs)) {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(1) <= cursor.getInt(2)) {
                        continue;
                    }
                    notForgivenOverusesById.put(cursor.getString(0),
                            cursor.getInt(1) - cursor.getInt(2));
                }
            }
            return notForgivenOverusesById;
        }

        public static void forgiveHistoricalOverusesForPackage(SQLiteDatabase db,
                List<String> userPackageIds, long includingStartEpochSeconds,
                long excludingEndEpochSeconds) {
            if (userPackageIds.isEmpty()) {
                Slogf.e(TAG, "No user package ids provided to forgive historical overuses.");
                return;
            }
            StringBuilder updateQueryBuilder = new StringBuilder("UPDATE ").append(TABLE_NAME)
                    .append(" SET ")
                    .append(COLUMN_NUM_FORGIVEN_OVERUSES).append("=").append(COLUMN_NUM_OVERUSES)
                    .append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(">= ").append(includingStartEpochSeconds)
                    .append(" and ")
                    .append(COLUMN_DATE_EPOCH).append("< ").append(excludingEndEpochSeconds);
            for (int i = 0; i < userPackageIds.size(); i++) {
                if (i == 0) {
                    updateQueryBuilder.append(" and ").append(COLUMN_USER_PACKAGE_ID)
                            .append(" IN (");
                } else {
                    updateQueryBuilder.append(", ");
                }
                updateQueryBuilder.append(userPackageIds.get(i));
                if (i == userPackageIds.size() - 1) {
                    updateQueryBuilder.append(")");
                }
            }

            db.execSQL(updateQueryBuilder.toString());
            Slogf.i(TAG, "Attempted to forgive overuses for I/O usage stats entries on pid %d",
                    Process.myPid());
        }

        public static @Nullable List<AtomsProto.CarWatchdogDailyIoUsageSummary>
                queryDailySystemIoUsageSummaries(SQLiteDatabase db, long includingStartEpochSeconds,
                long excludingEndEpochSeconds) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT SUM(").append(COLUMN_NUM_OVERUSES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_BACKGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append("), ")
                    .append("date(").append(COLUMN_DATE_EPOCH).append(", '").append(DATE_MODIFIER)
                    .append("', '").append(ZONE_MODIFIER).append("') as stats_date_epoch ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(" >= ? and ")
                    .append(COLUMN_DATE_EPOCH).append(" < ? ")
                    .append("GROUP BY stats_date_epoch ")
                    .append("HAVING SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append(" + ")
                    .append(COLUMN_WRITTEN_BACKGROUND_BYTES).append(" + ")
                    .append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append(") > 0 ")
                    .append("ORDER BY stats_date_epoch ASC");

            String[] selectionArgs = new String[]{String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds)};
            List<AtomsProto.CarWatchdogDailyIoUsageSummary> summaries = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), selectionArgs)) {
                if (cursor.getCount() == 0) {
                    return null;
                }
                while (cursor.moveToNext()) {
                    summaries.add(AtomsProto.CarWatchdogDailyIoUsageSummary.newBuilder()
                            .setWrittenBytes(WatchdogPerfHandler.constructCarWatchdogPerStateBytes(
                                    /* foregroundBytes= */ cursor.getLong(1),
                                    /* backgroundBytes= */ cursor.getLong(2),
                                    /* garageModeBytes= */ cursor.getLong(3)))
                            .setOveruseCount(cursor.getInt(0))
                            .build());
                }
            }
            return summaries;
        }

        public static long querySystemTotalWrittenBytes(SQLiteDatabase db,
                long includingStartEpochSeconds, long excludingEndEpochSeconds) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append(" + ")
                    .append(COLUMN_WRITTEN_BACKGROUND_BYTES).append(" + ")
                    .append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append(") ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(" >= ? and ")
                    .append(COLUMN_DATE_EPOCH).append(" < ? ");

            String[] selectionArgs = new String[]{String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds)};
            long totalWrittenBytes = 0;
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), selectionArgs)) {
                while (cursor.moveToNext()) {
                    totalWrittenBytes += cursor.getLong(0);
                }
            }
            return totalWrittenBytes;
        }

        public static @Nullable ArrayMap<String, List<AtomsProto.CarWatchdogDailyIoUsageSummary>>
                queryTopUsersDailyIoUsageSummaries(SQLiteDatabase db, int numTopUsers,
                long includingStartEpochSeconds, long excludingEndEpochSeconds) {
            StringBuilder innerQueryBuilder = new StringBuilder();
            innerQueryBuilder.append("SELECT ").append(COLUMN_USER_PACKAGE_ID)
                    .append(" FROM (SELECT ").append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append("SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append(" + ")
                    .append(COLUMN_WRITTEN_BACKGROUND_BYTES).append(" + ")
                    .append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append(") AS total_written_bytes ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(" >= ? and ")
                    .append(COLUMN_DATE_EPOCH).append(" < ?")
                    .append(" GROUP BY ").append(COLUMN_USER_PACKAGE_ID)
                    .append(" ORDER BY total_written_bytes DESC LIMIT ").append(numTopUsers)
                    .append(')');

            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT ").append(COLUMN_USER_PACKAGE_ID).append(", ")
                    .append("SUM(").append(COLUMN_NUM_OVERUSES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_FOREGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_BACKGROUND_BYTES).append("), ")
                    .append("SUM(").append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append("), ")
                    .append("date(").append(COLUMN_DATE_EPOCH).append(", '").append(DATE_MODIFIER)
                    .append("', '").append(ZONE_MODIFIER).append("') as stats_date_epoch ")
                    .append("FROM ").append(TABLE_NAME).append(" WHERE ")
                    .append(COLUMN_DATE_EPOCH).append(" >= ? and ")
                    .append(COLUMN_DATE_EPOCH).append(" < ? and (")
                    .append(COLUMN_WRITTEN_FOREGROUND_BYTES).append(" > 0 or ")
                    .append(COLUMN_WRITTEN_BACKGROUND_BYTES).append(" > 0 or ")
                    .append(COLUMN_WRITTEN_GARAGE_MODE_BYTES).append(" > 0) and ")
                    .append(COLUMN_USER_PACKAGE_ID)
                    .append(" in (").append(innerQueryBuilder)
                    .append(") GROUP BY stats_date_epoch, ").append(COLUMN_USER_PACKAGE_ID)
                    .append(" ORDER BY ").append(COLUMN_USER_PACKAGE_ID)
                    .append(", stats_date_epoch ASC");

            String[] selectionArgs = new String[]{
                    // Outer query selection arguments.
                    String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds),
                    // Inner query selection arguments.
                    String.valueOf(includingStartEpochSeconds),
                    String.valueOf(excludingEndEpochSeconds)};

            ArrayMap<String, List<AtomsProto.CarWatchdogDailyIoUsageSummary>> summariesById =
                    new ArrayMap<>();
            try (Cursor cursor = db.rawQuery(queryBuilder.toString(), selectionArgs)) {
                if (cursor.getCount() == 0) {
                    return null;
                }
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    List<AtomsProto.CarWatchdogDailyIoUsageSummary> summaries =
                            summariesById.get(id);
                    if (summaries == null) {
                        summaries = new ArrayList<>();
                    }
                    summaries.add(AtomsProto.CarWatchdogDailyIoUsageSummary.newBuilder()
                            .setWrittenBytes(WatchdogPerfHandler.constructCarWatchdogPerStateBytes(
                                    /* foregroundBytes= */ cursor.getLong(2),
                                    /* backgroundBytes= */ cursor.getLong(3),
                                    /* garageModeBytes= */ cursor.getLong(4)))
                            .setOveruseCount(cursor.getInt(1))
                            .build());
                    summariesById.put(id, summaries);
                }
            }
            return summariesById;
        }

        public static void truncateToDate(SQLiteDatabase db, ZonedDateTime latestTruncateDate) {
            String selection = COLUMN_DATE_EPOCH + " <= ?";
            String[] selectionArgs = { String.valueOf(latestTruncateDate.toEpochSecond()) };

            int rows = db.delete(TABLE_NAME, selection, selectionArgs);
            Slogf.i(TAG, "Truncated %d I/O usage stats entries on pid %d", rows, Process.myPid());
        }

        public static void trimHistoricalStats(SQLiteDatabase db, ZonedDateTime currentDate) {
            ContentValues values = new ContentValues();
            values.putNull(COLUMN_REMAINING_FOREGROUND_WRITE_BYTES);
            values.putNull(COLUMN_REMAINING_BACKGROUND_WRITE_BYTES);
            values.putNull(COLUMN_REMAINING_GARAGE_MODE_WRITE_BYTES);
            values.putNull(COLUMN_FORGIVEN_FOREGROUND_WRITE_BYTES);
            values.putNull(COLUMN_FORGIVEN_BACKGROUND_WRITE_BYTES);
            values.putNull(COLUMN_FORGIVEN_GARAGE_MODE_WRITE_BYTES);

            String selection = COLUMN_DATE_EPOCH + " < ?";
            String[] selectionArgs = { String.valueOf(currentDate.toEpochSecond()) };

            int rows = db.update(TABLE_NAME, values, selection, selectionArgs);
            Slogf.i(TAG, "Trimmed %d I/O usage stats entries on pid %d", rows, Process.myPid());
        }
    }

    /**
     * Defines the Watchdog database and database level operations.
     */
    static final class WatchdogDbHelper extends SQLiteOpenHelper {
        public static final String DATABASE_NAME = "car_watchdog.db";

        private static final int DATABASE_VERSION = 3;

        private ZonedDateTime mLatestShrinkDate;
        private TimeSource mTimeSource;

        WatchdogDbHelper(Context context, boolean useDataSystemCarDir, TimeSource timeSource) {
            /* Use device protected storage because CarService may need to access the database
             * before the user has authenticated.
             */
            super(context.createDeviceProtectedStorageContext(), useDataSystemCarDir
                            ? new File(CarWatchdogService.getWatchdogDirFile(), DATABASE_NAME)
                                    .getAbsolutePath()
                            : DATABASE_NAME,
                    /* name= */ null, DATABASE_VERSION);
            mTimeSource = timeSource;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            UserPackageSettingsTable.createTable(db);
            IoUsageStatsTable.createTable(db);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            db.setForeignKeyConstraintsEnabled(true);
        }

        public synchronized void terminate() {
            close();
            mLatestShrinkDate = null;
        }

        public void onShrink(SQLiteDatabase db) {
            ZonedDateTime currentDate = mTimeSource.getCurrentDate();
            if (currentDate.equals(mLatestShrinkDate)) {
                return;
            }
            IoUsageStatsTable.truncateToDate(db, currentDate.minus(RETENTION_PERIOD));
            IoUsageStatsTable.trimHistoricalStats(db, currentDate);
            mLatestShrinkDate = currentDate;
            Slogf.i(TAG, "Shrunk watchdog database for the date '%s'", mLatestShrinkDate);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            if (oldVersion < 1 || oldVersion > 2) {
                return;
            }
            // Upgrade logic from version 1 to 3.
            int upgradeVersion = oldVersion;
            db.beginTransaction();
            try {
                while (upgradeVersion < currentVersion) {
                    switch (upgradeVersion) {
                        case 1:
                            upgradeToVersion2(db);
                            break;
                        case 2:
                            upgradeToVersion3(db);
                            break;
                        default:
                            String errorMsg = "Tried upgrading to an invalid database version: "
                                    + upgradeVersion + " (current version: " + currentVersion + ")";
                            throw new IllegalStateException(errorMsg);
                    }
                    upgradeVersion++;
                }
                db.setTransactionSuccessful();
                Slogf.i(TAG, "Successfully upgraded database from version %d to %d", oldVersion,
                        upgradeVersion);
            } finally {
                db.endTransaction();
            }
            if (upgradeVersion != currentVersion) {
                Slogf.i(TAG, "Failed to upgrade database from version %d to %d. "
                        + "Attempting to recreate database.", oldVersion, currentVersion);
                recreateDatabase(db);
            }
        }

        /**
         * Upgrades the given {@code db} to version {@code 3}.
         *
         * <p>Entries from {@link UserPackageSettingsTable} and {@link IoUsageStatsTable} are
         * migrated to version 3. The {@code killable_sate_modified_date} column is initialized with
         * the epoch seconds at {@code UserPackageSettingTable} table creation.
         */
        private void upgradeToVersion3(SQLiteDatabase db) {
            Slogf.i(TAG, "Upgrading car watchdog database to version 3.");
            String oldUserPackageSettingsTable = UserPackageSettingsTable.TABLE_NAME + "_old_v2";
            StringBuilder execSql = new StringBuilder("ALTER TABLE ")
                    .append(UserPackageSettingsTable.TABLE_NAME)
                    .append(" RENAME TO ").append(oldUserPackageSettingsTable);
            db.execSQL(execSql.toString());

            String oldIoUsageStatsTable = IoUsageStatsTable.TABLE_NAME + "_old_v2";
            execSql = new StringBuilder("ALTER TABLE ")
                    .append(IoUsageStatsTable.TABLE_NAME)
                    .append(" RENAME TO ").append(oldIoUsageStatsTable);
            db.execSQL(execSql.toString());

            UserPackageSettingsTable.createTable(db);
            IoUsageStatsTable.createTable(db);

            // The COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH takes on the epoch seconds at which the
            // migration occurs.
            execSql = new StringBuilder("INSERT INTO ").append(UserPackageSettingsTable.TABLE_NAME)
                    .append(" (")
                    .append(UserPackageSettingsTable.COLUMN_USER_PACKAGE_ID).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_PACKAGE_NAME).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_USER_ID).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_KILLABLE_STATE).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_KILLABLE_STATE_LAST_MODIFIED_EPOCH)
                    .append(") ")
                    .append("SELECT ").append(UserPackageSettingsTable.COLUMN_USER_PACKAGE_ID)
                    .append(", ").append(UserPackageSettingsTable.COLUMN_PACKAGE_NAME)
                    .append(", ").append(UserPackageSettingsTable.COLUMN_USER_ID).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_KILLABLE_STATE).append(", ")
                    .append(mTimeSource.getCurrentDate().toEpochSecond()).append(" FROM ")
                    .append(oldUserPackageSettingsTable);
            db.execSQL(execSql.toString());

            execSql = new StringBuilder("DROP TABLE IF EXISTS ")
                    .append(oldUserPackageSettingsTable);
            db.execSQL(execSql.toString());

            execSql = new StringBuilder("INSERT INTO ").append(IoUsageStatsTable.TABLE_NAME)
                    .append(" SELECT * FROM ").append(oldIoUsageStatsTable);
            db.execSQL(execSql.toString());

            execSql = new StringBuilder("DROP TABLE IF EXISTS ")
                    .append(oldIoUsageStatsTable);
            db.execSQL(execSql.toString());
            Slogf.i(TAG, "Successfully upgraded car watchdog database to version 3.");
        }

        /**
         * Upgrades the given {@code db} to version 2.
         *
         * <p>Database version 2 replaces the primary key in {@link UserPackageSettingsTable} with
         * an auto-incrementing integer ID and uses the ID (instead of its rowid) as one of
         * the primary keys in {@link IoUsageStatsTable} along with a foreign key dependency.
         *
         * <p>Only the entries from {@link UserPackageSettingsTable} are migrated to the version 2
         * database because in version 1 only the current day's entries in {@link IoUsageStatsTable}
         * are mappable to the former table and dropping these entries is tolerable.
         */
        private void upgradeToVersion2(SQLiteDatabase db) {
            String oldUserPackageSettingsTable = UserPackageSettingsTable.TABLE_NAME + "_old_v1";
            StringBuilder execSql = new StringBuilder("ALTER TABLE ")
                    .append(UserPackageSettingsTable.TABLE_NAME)
                    .append(" RENAME TO ").append(oldUserPackageSettingsTable);
            db.execSQL(execSql.toString());

            execSql = new StringBuilder("DROP TABLE IF EXISTS ")
                    .append(IoUsageStatsTable.TABLE_NAME);
            db.execSQL(execSql.toString());

            createUserPackageSettingsTableV2(db);
            IoUsageStatsTable.createTable(db);

            execSql = new StringBuilder("INSERT INTO ").append(UserPackageSettingsTable.TABLE_NAME)
                    .append(" (").append(UserPackageSettingsTable.COLUMN_PACKAGE_NAME).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_USER_ID).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_KILLABLE_STATE).append(") ")
                    .append("SELECT ").append(UserPackageSettingsTable.COLUMN_PACKAGE_NAME)
                    .append(", ").append(UserPackageSettingsTable.COLUMN_USER_ID).append(", ")
                    .append(UserPackageSettingsTable.COLUMN_KILLABLE_STATE).append(" FROM ")
                    .append(oldUserPackageSettingsTable);
            db.execSQL(execSql.toString());

            execSql = new StringBuilder("DROP TABLE IF EXISTS ")
                    .append(oldUserPackageSettingsTable);
            db.execSQL(execSql.toString());
        }

        public static void createUserPackageSettingsTableV2(SQLiteDatabase db) {
            StringBuilder createCommand = new StringBuilder();
            createCommand.append("CREATE TABLE ").append(UserPackageSettingsTable.TABLE_NAME)
                    .append(" (")
                    .append(UserPackageSettingsTable.COLUMN_USER_PACKAGE_ID)
                    .append(" INTEGER PRIMARY KEY AUTOINCREMENT, ")
                    .append(UserPackageSettingsTable.COLUMN_PACKAGE_NAME)
                    .append(" TEXT NOT NULL, ")
                    .append(UserPackageSettingsTable.COLUMN_USER_ID)
                    .append(" INTEGER NOT NULL, ")
                    .append(UserPackageSettingsTable.COLUMN_KILLABLE_STATE)
                    .append(" INTEGER NOT NULL, ")
                    .append("UNIQUE(").append(UserPackageSettingsTable.COLUMN_PACKAGE_NAME)
                    .append(", ").append(UserPackageSettingsTable.COLUMN_USER_ID).append("))");
            db.execSQL(createCommand.toString());
            Slogf.i(TAG, "Successfully created the %s table in the %s database version %d",
                    UserPackageSettingsTable.TABLE_NAME, WatchdogDbHelper.DATABASE_NAME, 2);
        }

        private void recreateDatabase(SQLiteDatabase db) {
            db.execSQL(new StringBuilder("DROP TABLE IF EXISTS ")
                    .append(UserPackageSettingsTable.TABLE_NAME).toString());
            db.execSQL(new StringBuilder("DROP TABLE IF EXISTS ")
                    .append(IoUsageStatsTable.TABLE_NAME).toString());

            onCreate(db);
            Slogf.e(TAG, "Successfully recreated database version %d", DATABASE_VERSION);
        }
    }


    private static final class UserPackage {
        public final String userPackageId;
        public final @UserIdInt int userId;
        public final String packageName;

        UserPackage(String userPackageId, @UserIdInt int userId, String packageName) {
            this.userPackageId = userPackageId;
            this.userId = userId;
            this.packageName = packageName;
        }

        public String getKey() {
            return getKey(userId, packageName);
        }

        public static String getKey(int userId, String packageName) {
            return String.format(Locale.ENGLISH, "%d:%s", userId, packageName);
        }

        @Override
        public String toString() {
            return new StringBuilder("UserPackage{userPackageId: ").append(userPackageId)
                    .append(", userId: ").append(userId)
                    .append(", packageName: ").append(packageName).append("}").toString();
        }
    }
}
