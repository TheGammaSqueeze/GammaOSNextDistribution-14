/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.permission;

import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_CURRENT;
import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_STAGED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.Constants;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.healthconnect.HealthConnectThreadScheduler;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manager class of the health permissions first grant time.
 *
 * @hide
 */
public class FirstGrantTimeManager implements PackageManager.OnPermissionsChangedListener {
    private static final String TAG = "HealthFirstGrantTimeMan";
    private static final int CURRENT_VERSION = 1;

    private final PackageManager mPackageManager;
    private final UserManager mUserManager;
    private final HealthPermissionIntentAppsTracker mTracker;

    private final ReentrantReadWriteLock mGrantTimeLock = new ReentrantReadWriteLock();

    @GuardedBy("mGrantTimeLock")
    private final FirstGrantTimeDatastore mDatastore;

    @GuardedBy("mGrantTimeLock")
    private final UidToGrantTimeCache mUidToGrantTimeCache;

    @GuardedBy("mGrantTimeLock")
    private final Set<Integer> mRestoredAndValidatedUsers = new ArraySet<>();

    private final PackageInfoUtils mPackageInfoHelper;
    private final Context mContext;

    public FirstGrantTimeManager(
            @NonNull Context context,
            @NonNull HealthPermissionIntentAppsTracker tracker,
            @NonNull FirstGrantTimeDatastore datastore) {
        mTracker = tracker;
        mDatastore = datastore;
        mPackageManager = context.getPackageManager();
        mUserManager = context.getSystemService(UserManager.class);
        mUidToGrantTimeCache = new UidToGrantTimeCache();
        mContext = context;
        mPackageInfoHelper = PackageInfoUtils.getInstance();
        mPackageManager.addOnPermissionsChangeListener(this);
    }

    /** Get the date when the first health permission was granted. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Nullable
    public Instant getFirstGrantTime(@NonNull String packageName, @NonNull UserHandle user)
            throws IllegalArgumentException {

        Integer uid = mPackageInfoHelper.getPackageUid(packageName, user, getUserContext(user));
        if (uid == null) {
            throw new IllegalArgumentException(
                    "Package name "
                            + packageName
                            + " of user "
                            + user.getIdentifier()
                            + " not found.");
        }
        initAndValidateUserStateIfNeedLocked(user);

        Instant grantTimeDate = getGrantTimeReadLocked(uid);
        if (grantTimeDate == null) {
            // Check and update the state in case health permission has been granted before
            // onPermissionsChanged callback was propagated.
            updateFirstGrantTimesFromPermissionState(uid, true);
            grantTimeDate = getGrantTimeReadLocked(uid);
        }

        return grantTimeDate;
    }

    /** Sets the provided first grant time for the given {@code packageName}. */
    public void setFirstGrantTime(
            @NonNull String packageName, @NonNull Instant time, @NonNull UserHandle user) {
        final Integer uid =
                mPackageInfoHelper.getPackageUid(packageName, user, getUserContext(user));
        if (uid == null) {
            throw new IllegalArgumentException(
                    "Package name "
                            + packageName
                            + " of user "
                            + user.getIdentifier()
                            + " not found.");
        }
        initAndValidateUserStateIfNeedLocked(user);

        mGrantTimeLock.writeLock().lock();
        try {
            mUidToGrantTimeCache.put(uid, time);
            mDatastore.writeForUser(
                    mUidToGrantTimeCache.extractUserGrantTimeState(user), user, DATA_TYPE_CURRENT);
        } finally {
            mGrantTimeLock.writeLock().unlock();
        }
    }

    @Override
    public void onPermissionsChanged(int uid) {
        updateFirstGrantTimesFromPermissionState(uid, false);
    }

    /**
     * Checks whether the {@code uid} is mapped to valid package names of valid health apps before
     * updating first grant times from the current permission state. The update can be perform in
     * the same thread where this method is called if {@code sync} is set to {@code true}, another
     * background thread otherwise.
     */
    private void updateFirstGrantTimesFromPermissionState(int uid, boolean sync) {
        if (!mUserManager.isUserUnlocked()) {
            // this method is called in onPermissionsChanged(uid) which is called as soon as the
            // system boots up, even before the user has unlock the device for the first time.
            // Side note: onPermissionsChanged() is also called on both primary user and work
            // profile user.
            return;
        }

        final String[] packageNames = mPackageManager.getPackagesForUid(uid);
        if (packageNames == null) {
            Log.w(TAG, "onPermissionsChanged: no known packages for UID: " + uid);
            return;
        }

        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        if (!checkSupportPermissionsUsageIntent(packageNames, user)) {
            logIfInDebugMode("Cannot find health intent declaration in ", packageNames[0]);
            return;
        }

        if (sync) {
            updateFirstGrantTimesFromPermissionState(uid, user, packageNames);
        } else {
            try {
                HealthConnectThreadScheduler.scheduleInternalTask(
                        () -> updateFirstGrantTimesFromPermissionState(uid, user, packageNames));
            } catch (RejectedExecutionException executionException) {
                Log.e(
                        TAG,
                        "Can't queue internal task in #onPermissionsChanged for uid=" + uid,
                        executionException);
            }
        }
    }

    /**
     * Checks permission states for {@code uid} and updates first grant times accordingly.
     *
     * <p><b>Note:</b>This method must only be called from a non-main thread.
     */
    @WorkerThread
    private void updateFirstGrantTimesFromPermissionState(
            int uid, UserHandle user, String[] packageNames) {
        // call this method after `checkSupportPermissionsUsageIntent` so we are sure that we are
        // not initializing user state when onPermissionsChanged(uid) is called for non HC client
        // apps.
        initAndValidateUserStateIfNeedLocked(user);

        mGrantTimeLock.writeLock().lock();
        try {
            boolean anyHealthPermissionGranted =
                    mPackageInfoHelper.hasGrantedHealthPermissions(
                            packageNames, user, getUserContext(user));

            boolean grantTimeRecorded = (getGrantTimeReadLocked(uid) != null);
            if (grantTimeRecorded != anyHealthPermissionGranted) {
                if (grantTimeRecorded) {
                    // An app doesn't have health permissions anymore, reset its grant time.
                    mUidToGrantTimeCache.remove(uid);
                    // Update priority table only if migration is not in progress as it should
                    // already take care of merging permissions.
                    if (!MigrationStateManager.getInitialisedInstance().isMigrationInProgress()) {
                        HealthConnectThreadScheduler.scheduleInternalTask(
                                () -> removeAppsFromPriorityList(packageNames));
                    }
                } else {
                    // An app got new health permission, set current time as it's first grant
                    // time if we can't update state from the staged data.
                    if (!tryUpdateGrantTimeFromStagedDataLocked(user, uid)) {
                        mUidToGrantTimeCache.put(uid, Instant.now());
                    }
                }

                UserGrantTimeState updatedState =
                        mUidToGrantTimeCache.extractUserGrantTimeState(user);
                logIfInDebugMode("State after onPermissionsChanged :", updatedState);
                mDatastore.writeForUser(updatedState, user, DATA_TYPE_CURRENT);
            } else {
                // Update priority table only if migration is not in progress as it should already
                // take care of merging permissions
                if (!MigrationStateManager.getInitialisedInstance().isMigrationInProgress()) {
                    HealthConnectThreadScheduler.scheduleInternalTask(
                            () ->
                                    HealthDataCategoryPriorityHelper.getInstance()
                                            .updateHealthDataPriority(
                                                    packageNames, user, getUserContext(user)));
                }
            }
        } finally {
            mGrantTimeLock.writeLock().unlock();
        }
    }

    // TODO(b/277063776): move two methods below to b&r classes.

    /** Returns the state which should be backed up. */
    public UserGrantTimeState createBackupState(UserHandle user) {
        initAndValidateUserStateIfNeedLocked(user);
        return mUidToGrantTimeCache.extractUserBackupGrantTimeState(user);
    }

    /**
     * Callback which should be called when backup grant time data is available. Triggers merge of
     * current and backup grant time data. All grant times from backup state which are not merged
     * with the current state (e.g. because an app is not installed) will be staged until app gets
     * health permission.
     *
     * @param userId user for which the data is available.
     * @param state backup state to apply.
     */
    public void applyAndStageBackupDataForUser(UserHandle userId, UserGrantTimeState state) {
        initAndValidateUserStateIfNeedLocked(userId);

        mGrantTimeLock.writeLock().lock();
        try {
            // Write the state into the disk as staged data so that it can be merged.
            mDatastore.writeForUser(state, userId, DATA_TYPE_STAGED);
            updateGrantTimesWithStagedDataLocked(userId);
        } finally {
            mGrantTimeLock.writeLock().unlock();
        }
    }

    /** Returns file with grant times data. */
    public File getFile(UserHandle userHandle) {
        return mDatastore.getFile(userHandle, DATA_TYPE_CURRENT);
    }

    void onPackageRemoved(
            @NonNull String packageName, int removedPackageUid, @NonNull UserHandle userHandle) {
        String[] leftSharedUidPackages =
                mPackageInfoHelper.getPackagesForUid(
                        removedPackageUid, userHandle, getUserContext(userHandle));
        if (leftSharedUidPackages != null && leftSharedUidPackages.length > 0) {
            // There are installed packages left with given UID,
            // don't need to update grant time state.
            return;
        }

        initAndValidateUserStateIfNeedLocked(userHandle);

        if (getGrantTimeReadLocked(removedPackageUid) != null) {
            mGrantTimeLock.writeLock().lock();
            try {
                mUidToGrantTimeCache.remove(removedPackageUid);
                UserGrantTimeState updatedState =
                        mUidToGrantTimeCache.extractUserGrantTimeState(userHandle);
                logIfInDebugMode("State after package " + packageName + " removed: ", updatedState);
                mDatastore.writeForUser(updatedState, userHandle, DATA_TYPE_CURRENT);
            } finally {
                mGrantTimeLock.writeLock().unlock();
            }
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @GuardedBy("mGrantTimeLock")
    private Instant getGrantTimeReadLocked(Integer uid) {
        mGrantTimeLock.readLock().lock();
        try {
            return mUidToGrantTimeCache.get(uid);
        } finally {
            mGrantTimeLock.readLock().unlock();
        }
    }

    @GuardedBy("mGrantTimeLock")
    private void updateGrantTimesWithStagedDataLocked(UserHandle user) {
        boolean stateChanged = false;
        for (Integer uid : mUidToGrantTimeCache.mUidToGrantTime.keySet()) {
            if (!UserHandle.getUserHandleForUid(uid).equals(user)) {
                continue;
            }

            stateChanged |= tryUpdateGrantTimeFromStagedDataLocked(user, uid);
        }

        if (stateChanged) {
            mDatastore.writeForUser(
                    mUidToGrantTimeCache.extractUserGrantTimeState(user), user, DATA_TYPE_CURRENT);
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @GuardedBy("mGrantTimeLock")
    private boolean tryUpdateGrantTimeFromStagedDataLocked(UserHandle user, Integer uid) {
        UserGrantTimeState backupState = mDatastore.readForUser(user, DATA_TYPE_STAGED);
        if (backupState == null) {
            return false;
        }

        Instant stagedTime = null;
        for (String packageName : mPackageInfoHelper.getPackageNamesForUid(uid)) {
            if (stagedTime == null) {
                stagedTime = backupState.getPackageGrantTimes().get(packageName);
            }
        }

        if (stagedTime == null) {
            return false;
        }
        if (mUidToGrantTimeCache.containsKey(uid)
                && mUidToGrantTimeCache.get(uid).isBefore(stagedTime)) {
            Log.w(
                    TAG,
                    "Backup grant time is later than currently stored grant time, "
                            + "skip restoring grant time for"
                            + " uid "
                            + uid);
            return false;
        }

        mUidToGrantTimeCache.put(uid, stagedTime);
        for (String packageName : mPackageInfoHelper.getPackageNamesForUid(uid)) {
            backupState.getPackageGrantTimes().remove(packageName);
        }
        mDatastore.writeForUser(backupState, user, DATA_TYPE_STAGED);
        return true;
    }

    /** Initialize first grant time state for given user. */
    private void initAndValidateUserStateIfNeedLocked(UserHandle user) {
        if (!mUserManager.isUserUnlocked()) {
            // only init first grant time state when device is unlocked, because before that, we
            // cannot access any files, which leads to `mUidToGrantTimeCache` being empty and never
            // get re-initialized.
            return;
        }

        if (userStateIsInitializedReadLocked(user)) {
            // This user state is already inited and validated
            return;
        }

        mGrantTimeLock.writeLock().lock();
        try {
            Log.i(
                    TAG,
                    "State for user: "
                            + user.getIdentifier()
                            + " has not been restored and validated.");
            UserGrantTimeState restoredState = restoreCurrentUserStateLocked(user);

            List<PackageInfo> validHealthApps =
                    mPackageInfoHelper.getPackagesHoldingHealthPermissions(
                            user, getUserContext(user));
            logIfInDebugMode(
                    "Packages holding health perms of user " + user + " :", validHealthApps);

            validateAndCorrectRecordedStateForUser(restoredState, validHealthApps, user);

            // TODO(b/260691599): consider removing mapping when getUidForSharedUser is
            Map<String, Set<Integer>> sharedUserNamesToUid =
                    mPackageInfoHelper.collectSharedUserNameToUidsMappingForUser(
                            validHealthApps, user);

            mUidToGrantTimeCache.populateFromUserGrantTimeState(
                    restoredState, sharedUserNamesToUid, user);

            mRestoredAndValidatedUsers.add(user.getIdentifier());
            logIfInDebugMode("State after init: ", restoredState);
            logIfInDebugMode("Cache after init: ", mUidToGrantTimeCache);
        } finally {
            mGrantTimeLock.writeLock().unlock();
        }
    }

    private boolean userStateIsInitializedReadLocked(UserHandle user) {
        mGrantTimeLock.readLock().lock();
        try {
            return mRestoredAndValidatedUsers.contains(user.getIdentifier());
        } finally {
            mGrantTimeLock.readLock().unlock();
        }
    }

    @GuardedBy("mGrantTimeLock")
    private UserGrantTimeState restoreCurrentUserStateLocked(UserHandle userHandle) {
        try {
            UserGrantTimeState restoredState =
                    mDatastore.readForUser(userHandle, DATA_TYPE_CURRENT);
            if (restoredState == null) {
                restoredState = new UserGrantTimeState(CURRENT_VERSION);
            }
            return restoredState;
        } catch (Exception e) {
            Log.e(TAG, "Error while reading from datastore: " + e);
            return new UserGrantTimeState(CURRENT_VERSION);
        }
    }

    /**
     * Validate current state and remove apps which are not present / hold health permissions, set
     * new grant time to apps which doesn't have grant time but installed and hold health
     * permissions. It should mitigate situation e.g. when permission mainline module did roll-back
     * and some health permissions got granted/revoked without onPermissionsChanged callback.
     *
     * @param recordedState restored state
     * @param healthPackagesInfos packageInfos of apps which currently hold health permissions
     * @param user UserHandle for whom to perform validation
     */
    @GuardedBy("mGrantTimeLock")
    private void validateAndCorrectRecordedStateForUser(
            @NonNull UserGrantTimeState recordedState,
            @NonNull List<PackageInfo> healthPackagesInfos,
            @NonNull UserHandle user) {
        Set<String> validPackagesPerUser = new ArraySet<>();
        Set<String> validSharedUsersPerUser = new ArraySet<>();

        boolean stateChanged = false;
        logIfInDebugMode("Valid apps for " + user + ": ", healthPackagesInfos);

        // If package holds health permissions and supports health permission intent
        // but doesn't have recorded grant time (e.g. because of permissions rollback),
        // set current time as the first grant time.
        for (PackageInfo info : healthPackagesInfos) {
            if (!mTracker.supportsPermissionUsageIntent(info.packageName, user)) {
                continue;
            }

            if (info.sharedUserId == null) {
                stateChanged |= setPackageGrantTimeIfNotRecorded(recordedState, info.packageName);
                validPackagesPerUser.add(info.packageName);
            } else {
                stateChanged |=
                        setSharedUserGrantTimeIfNotRecorded(recordedState, info.sharedUserId);
                validSharedUsersPerUser.add(info.sharedUserId);
            }
        }

        // If package is not installed / doesn't hold health permissions
        // but has recorded first grant time, remove it from grant time state.
        stateChanged |=
                removeInvalidPackagesFromGrantTimeStateForUser(recordedState, validPackagesPerUser);

        stateChanged |=
                removeInvalidSharedUsersFromGrantTimeStateForUser(
                        recordedState, validSharedUsersPerUser);

        if (stateChanged) {
            logIfInDebugMode("Changed state after validation for " + user + ": ", recordedState);
            mDatastore.writeForUser(recordedState, user, DATA_TYPE_CURRENT);
        }
    }

    @GuardedBy("mGrantTimeLock")
    private boolean setPackageGrantTimeIfNotRecorded(
            @NonNull UserGrantTimeState grantTimeState, @NonNull String packageName) {
        if (!grantTimeState.containsPackageGrantTime(packageName)) {
            Log.w(
                    TAG,
                    "No recorded grant time for package:"
                            + packageName
                            + ". Assigning current time as the first grant time.");
            grantTimeState.setPackageGrantTime(packageName, Instant.now());
            return true;
        }
        return false;
    }

    @GuardedBy("mGrantTimeLock")
    private boolean setSharedUserGrantTimeIfNotRecorded(
            @NonNull UserGrantTimeState grantTimeState, @NonNull String sharedUserIdName) {
        if (!grantTimeState.containsSharedUserGrantTime(sharedUserIdName)) {
            Log.w(
                    TAG,
                    "No recorded grant time for shared user:"
                            + sharedUserIdName
                            + ". Assigning current time as first grant time.");
            grantTimeState.setSharedUserGrantTime(sharedUserIdName, Instant.now());
            return true;
        }
        return false;
    }

    @GuardedBy("mGrantTimeLock")
    private boolean removeInvalidPackagesFromGrantTimeStateForUser(
            @NonNull UserGrantTimeState recordedState, @NonNull Set<String> validApps) {
        Set<String> recordedButNotValid =
                new ArraySet<>(recordedState.getPackageGrantTimes().keySet());
        if (validApps != null) {
            recordedButNotValid.removeAll(validApps);
        }

        if (!recordedButNotValid.isEmpty()) {
            Log.w(
                    TAG,
                    "Packages "
                            + recordedButNotValid
                            + " have recorded  grant times, but not installed or hold health "
                            + "permissions anymore. Removing them from the grant time state.");
            recordedState.getPackageGrantTimes().keySet().removeAll(recordedButNotValid);
            return true;
        }
        return false;
    }

    @GuardedBy("mGrantTimeLock")
    private boolean removeInvalidSharedUsersFromGrantTimeStateForUser(
            @NonNull UserGrantTimeState recordedState, @NonNull Set<String> validSharedUsers) {
        Set<String> recordedButNotValid =
                new ArraySet<>(recordedState.getSharedUserGrantTimes().keySet());
        if (validSharedUsers != null) {
            recordedButNotValid.removeAll(validSharedUsers);
        }

        if (!recordedButNotValid.isEmpty()) {
            Log.w(
                    TAG,
                    "Shared users "
                            + recordedButNotValid
                            + " have recorded  grant times, but not installed or hold health "
                            + "permissions anymore. Removing them from the grant time state.");
            recordedState.getSharedUserGrantTimes().keySet().removeAll(recordedButNotValid);
            return true;
        }
        return false;
    }

    private boolean checkSupportPermissionsUsageIntent(
            @NonNull String[] names, @NonNull UserHandle user) {
        for (String packageName : names) {
            if (mTracker.supportsPermissionUsageIntent(packageName, user)) {
                return true;
            }
        }
        return false;
    }

    private void logIfInDebugMode(@NonNull String prefixMessage, @NonNull Object objectToLog) {
        if (Constants.DEBUG) {
            Log.d(TAG, prefixMessage + objectToLog);
        }
    }

    private class UidToGrantTimeCache {
        private final Map<Integer, Instant> mUidToGrantTime;

        UidToGrantTimeCache() {
            mUidToGrantTime = new ArrayMap<>();
        }

        @Override
        public String toString() {
            return mUidToGrantTime.toString();
        }

        @Nullable
        Instant remove(@Nullable Integer uid) {
            if (uid == null) {
                return null;
            }
            return mUidToGrantTime.remove(uid);
        }

        @Nullable
        Instant get(Integer uid) {
            return mUidToGrantTime.get(uid);
        }

        boolean containsKey(@Nullable Integer uid) {
            if (uid == null) {
                return false;
            }
            return mUidToGrantTime.containsKey(uid);
        }

        @Nullable
        Instant put(@NonNull Integer uid, @NonNull Instant time) {
            return mUidToGrantTime.put(uid, time);
        }

        @NonNull
        UserGrantTimeState extractUserGrantTimeState(@NonNull UserHandle user) {
            Map<String, Instant> sharedUserToGrantTime = new ArrayMap<>();
            Map<String, Instant> packageNameToGrantTime = new ArrayMap<>();

            for (Map.Entry<Integer, Instant> entry : mUidToGrantTime.entrySet()) {
                Integer uid = entry.getKey();
                Instant time = entry.getValue();

                if (!UserHandle.getUserHandleForUid(uid).equals(user)) {
                    continue;
                }

                String sharedUserName =
                        mPackageInfoHelper.getSharedUserNameFromUid(uid, getUserContext(user));
                if (sharedUserName != null) {
                    sharedUserToGrantTime.put(sharedUserName, time);
                } else {
                    String packageName = mPackageInfoHelper.getPackageNameFromUid(uid);
                    if (packageName != null) {
                        packageNameToGrantTime.put(packageName, time);
                    }
                }
            }

            return new UserGrantTimeState(
                    packageNameToGrantTime, sharedUserToGrantTime, CURRENT_VERSION);
        }

        @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
        @NonNull
        UserGrantTimeState extractUserBackupGrantTimeState(@NonNull UserHandle user) {
            Map<String, Instant> sharedUserToGrantTime = new ArrayMap<>();
            Map<String, Instant> packageNameToGrantTime = new ArrayMap<>();

            for (Map.Entry<Integer, Instant> entry : mUidToGrantTime.entrySet()) {
                Integer uid = entry.getKey();
                Instant time = entry.getValue();

                if (!UserHandle.getUserHandleForUid(uid).equals(user)) {
                    continue;
                }

                for (String packageName : mPackageInfoHelper.getPackageNamesForUid(uid)) {
                    packageNameToGrantTime.put(packageName, time);
                }
            }

            return new UserGrantTimeState(
                    packageNameToGrantTime, sharedUserToGrantTime, CURRENT_VERSION);
        }

        void populateFromUserGrantTimeState(
                @Nullable UserGrantTimeState grantTimeState,
                @NonNull Map<String, Set<Integer>> sharedUserNameToUids,
                @NonNull UserHandle user) {
            if (grantTimeState == null) {
                return;
            }

            for (Map.Entry<String, Instant> entry :
                    grantTimeState.getSharedUserGrantTimes().entrySet()) {
                String sharedUserName = entry.getKey();
                Instant time = entry.getValue();

                if (sharedUserNameToUids.get(sharedUserName) == null) {
                    continue;
                }

                for (Integer uid : sharedUserNameToUids.get(sharedUserName)) {
                    put(uid, time);
                }
            }

            for (Map.Entry<String, Instant> entry :
                    grantTimeState.getPackageGrantTimes().entrySet()) {
                String packageName = entry.getKey();
                Instant time = entry.getValue();

                Integer uid =
                        mPackageInfoHelper.getPackageUid(packageName, user, getUserContext(user));
                if (uid != null) {
                    put(uid, time);
                }
            }
        }
    }

    private void removeAppsFromPriorityList(String[] packageNames) {
        for (String packageName : packageNames) {
            HealthDataCategoryPriorityHelper.getInstance()
                    .maybeRemoveAppWithoutWritePermissionsFromPriorityList(packageName);
        }
    }

    @NonNull
    private Context getUserContext(UserHandle userHandle) {
        return mContext.createContextAsUser(userHandle, /*flags*/ 0);
    }
}
