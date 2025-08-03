/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect.migration;

import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_ALLOWED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_COMPLETE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED;

import static com.android.server.healthconnect.migration.MigrationConstants.ALLOWED_STATE_START_TIME_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.CURRENT_STATE_START_TIME_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.HAVE_RESET_MIGRATION_STATE_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.HC_PACKAGE_NAME_CONFIG_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.HC_RELEASE_CERT_CONFIG_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.IDLE_TIMEOUT_REACHED_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.IN_PROGRESS_TIMEOUT_REACHED_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_COMPLETE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_PAUSE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_STARTS_COUNT_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_STATE_PREFERENCE_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.PREMATURE_MIGRATION_TIMEOUT_DATE;
import static com.android.server.healthconnect.migration.MigrationUtils.filterIntent;
import static com.android.server.healthconnect.migration.MigrationUtils.filterPermissions;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.health.connect.Constants;
import android.health.connect.HealthConnectDataState;
import android.health.connect.HealthConnectManager;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectThreadScheduler;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A database operations helper for migration states management.
 *
 * @hide
 */
public final class MigrationStateManager {
    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    @GuardedBy("sInstanceLock")
    private static MigrationStateManager sMigrationStateManager;

    private static final Object sInstanceLock = new Object();
    private static final String TAG = "MigrationStateManager";
    private final HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager =
            HealthConnectDeviceConfigManager.getInitialisedInstance();

    @GuardedBy("mLock")
    private final Set<StateChangedListener> mStateChangedListeners = new CopyOnWriteArraySet<>();

    private final Object mLock = new Object();
    private volatile MigrationBroadcastScheduler mMigrationBroadcastScheduler;
    private int mUserId;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private MigrationStateManager(@UserIdInt int userId) {
        mUserId = userId;
    }

    /**
     * Initialises {@link MigrationStateManager} with the provided arguments and returns the
     * instance.
     */
    @NonNull
    public static MigrationStateManager initializeInstance(@UserIdInt int userId) {
        synchronized (sInstanceLock) {
            if (Objects.isNull(sMigrationStateManager)) {
                sMigrationStateManager = new MigrationStateManager(userId);
            }

            return sMigrationStateManager;
        }
    }

    /** Re-initialize this class instance with the new user */
    public void onUserSwitching(@NonNull Context context, @UserIdInt int userId) {
        synchronized (mLock) {
            MigrationStateChangeJob.cancelAllJobs(context);
            mUserId = userId;
        }
    }

    /** Returns initialised instance of this class. */
    @NonNull
    public static MigrationStateManager getInitialisedInstance() {
        synchronized (sInstanceLock) {
            Objects.requireNonNull(sMigrationStateManager);
            return sMigrationStateManager;
        }
    }

    /**
     * Clears the initialized instance such that {@link #initializeInstance} will create a new
     * instance, for use in tests.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @VisibleForTesting
    public static void resetInitializedInstanceForTest() {
        synchronized (sInstanceLock) {
            sMigrationStateManager = null;
        }
    }

    /** Registers {@link StateChangedListener} for observing migration state changes. */
    public void addStateChangedListener(@NonNull StateChangedListener listener) {
        synchronized (mLock) {
            mStateChangedListeners.add(listener);
        }
    }

    public void setMigrationBroadcastScheduler(
            MigrationBroadcastScheduler migrationBroadcastScheduler) {
        mMigrationBroadcastScheduler = migrationBroadcastScheduler;
    }

    /**
     * Adds the min data migration sdk and updates the migration state to pending.
     *
     * @param minVersion the desired sdk version.
     */
    public void setMinDataMigrationSdkExtensionVersion(@NonNull Context context, int minVersion) {
        synchronized (mLock) {
            if (minVersion <= getUdcSdkExtensionVersion()) {
                updateMigrationState(context, MIGRATION_STATE_ALLOWED);
                return;
            }
            PreferenceHelper.getInstance()
                    .insertOrReplacePreference(
                            MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY,
                            String.valueOf(minVersion));
            updateMigrationState(context, MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        }
    }

    /**
     * @return true when the migration state is in_progress.
     */
    public boolean isMigrationInProgress() {
        return getMigrationState() == MIGRATION_STATE_IN_PROGRESS;
    }

    @HealthConnectDataState.DataMigrationState
    public int getMigrationState() {
        String migrationState =
                PreferenceHelper.getInstance().getPreference(MIGRATION_STATE_PREFERENCE_KEY);
        if (Objects.isNull(migrationState)) {
            return MIGRATION_STATE_IDLE;
        }

        return Integer.parseInt(migrationState);
    }

    public void switchToSetupForUser(@NonNull Context context) {
        synchronized (mLock) {
            resetMigrationStateIfNeeded(context);
            MigrationStateChangeJob.cancelAllJobs(context);
            reconcilePackageChangesWithStates(context);
            reconcileStateChangeJob(context);
        }
    }

    /** Updates the migration state. */
    public void updateMigrationState(
            @NonNull Context context, @HealthConnectDataState.DataMigrationState int state) {
        synchronized (mLock) {
            updateMigrationStateGuarded(context, state, false);
        }
    }

    /**
     * Updates the migration state and the timeout reached.
     *
     * @param timeoutReached Whether the previous state has timed out.
     */
    void updateMigrationState(
            @NonNull Context context,
            @HealthConnectDataState.DataMigrationState int state,
            boolean timeoutReached) {
        synchronized (mLock) {
            updateMigrationStateGuarded(context, state, timeoutReached);
        }
    }

    /**
     * Atomically updates the migration state and the timeout reached.
     *
     * @param timeoutReached Whether the previous state has timed out.
     */
    @GuardedBy("mLock")
    private void updateMigrationStateGuarded(
            @NonNull Context context,
            @HealthConnectDataState.DataMigrationState int state,
            boolean timeoutReached) {

        if (state == getMigrationState()) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "The new state same as the current state.");
            }
            return;
        }

        switch (state) {
            case MIGRATION_STATE_IDLE:
            case MIGRATION_STATE_APP_UPGRADE_REQUIRED:
            case MIGRATION_STATE_MODULE_UPGRADE_REQUIRED:
                MigrationStateChangeJob.cancelAllJobs(context);
                updateMigrationStatePreference(context, state, timeoutReached);
                MigrationStateChangeJob.scheduleMigrationCompletionJob(context, mUserId);
                return;
            case MIGRATION_STATE_IN_PROGRESS:
                MigrationStateChangeJob.cancelAllJobs(context);
                updateMigrationStatePreference(
                        context, MIGRATION_STATE_IN_PROGRESS, timeoutReached);
                MigrationStateChangeJob.scheduleMigrationPauseJob(context, mUserId);
                updateMigrationStartsCount();
                return;
            case MIGRATION_STATE_ALLOWED:
                if (hasAllowedStateTimedOut()
                        || getStartMigrationCount()
                                >= mHealthConnectDeviceConfigManager.getMaxStartMigrationCalls()) {
                    updateMigrationState(context, MIGRATION_STATE_COMPLETE);
                    return;
                }
                MigrationStateChangeJob.cancelAllJobs(context);
                updateMigrationStatePreference(context, MIGRATION_STATE_ALLOWED, timeoutReached);
                MigrationStateChangeJob.scheduleMigrationCompletionJob(context, mUserId);
                return;
            case MIGRATION_STATE_COMPLETE:
                updateMigrationStatePreference(context, MIGRATION_STATE_COMPLETE, timeoutReached);
                MigrationStateChangeJob.cancelAllJobs(context);
                return;
            default:
                throw new IllegalArgumentException(
                        "Cannot updated migration state. Unknown state: " + state);
        }
    }

    public void clearCaches(@NonNull Context context) {
        synchronized (mLock) {
            PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
            updateMigrationStatePreference(context, MIGRATION_STATE_IDLE, false);
            preferenceHelper.insertOrReplacePreference(
                    MIGRATION_STARTS_COUNT_KEY, String.valueOf(0));
            preferenceHelper.removeKey(ALLOWED_STATE_START_TIME_KEY);
        }
    }

    /** Thrown when an illegal migration state is detected. */
    public static final class IllegalMigrationStateException extends Exception {
        public IllegalMigrationStateException(String message) {
            super(message);
        }
    }

    /**
     * Throws {@link IllegalMigrationStateException} if the migration can not be started in the
     * current state. If migration can be started, it will change the state to
     * MIGRATION_STATE_IN_PROGRESS
     */
    public void startMigration(@NonNull Context context) throws IllegalMigrationStateException {
        synchronized (mLock) {
            validateStartMigrationGuarded();
            updateMigrationStateGuarded(context, MIGRATION_STATE_IN_PROGRESS, false);
        }
    }

    @GuardedBy("mLock")
    private void validateStartMigrationGuarded() throws IllegalMigrationStateException {
        throwIfMigrationIsComplete();
    }

    /** Returns the number of times migration has started. */
    public int getMigrationStartsCount() {
        synchronized (mLock) {
            PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
            int res =
                    Integer.parseInt(
                            Optional.ofNullable(
                                            preferenceHelper.getPreference(
                                                    MIGRATION_STARTS_COUNT_KEY))
                                    .orElse("0"));
            return res;
        }
    }

    /**
     * Throws {@link IllegalMigrationStateException} if the migration can not be finished in the
     * current state. If migration can be finished, it will change the state to
     * MIGRATION_STATE_COMPLETE
     */
    public void finishMigration(@NonNull Context context) throws IllegalMigrationStateException {
        synchronized (mLock) {
            throwIfMigrationIsComplete();
            if (getMigrationState() != MIGRATION_STATE_IN_PROGRESS
                    && getMigrationState() != MIGRATION_STATE_ALLOWED) {
                throw new IllegalMigrationStateException("Migration is not started.");
            }
            updateMigrationStateGuarded(context, MIGRATION_STATE_COMPLETE, false);
        }
    }

    /**
     * Throws {@link IllegalMigrationStateException} if the migration can not be performed in the
     * current state.
     */
    public void validateWriteMigrationData() throws IllegalMigrationStateException {
        synchronized (mLock) {
            throwIfMigrationIsComplete();
            if (getMigrationState() != MIGRATION_STATE_IN_PROGRESS) {
                throw new IllegalMigrationStateException("Migration is not started.");
            }
        }
    }

    /**
     * Throws {@link IllegalMigrationStateException} if the sdk extension version can not be set in
     * the current state.
     */
    public void validateSetMinSdkVersion() throws IllegalMigrationStateException {
        synchronized (mLock) {
            throwIfMigrationIsComplete();
            if (getMigrationState() == MIGRATION_STATE_IN_PROGRESS) {
                throw new IllegalMigrationStateException(
                        "Cannot set the sdk extension version. Migration already in progress.");
            }
        }
    }

    void onPackageInstalledOrChanged(@NonNull Context context, @NonNull String packageName) {
        synchronized (mLock) {
            onPackageInstalledOrChangedGuarded(context, packageName);
        }
    }

    @GuardedBy("mLock")
    private void onPackageInstalledOrChangedGuarded(
            @NonNull Context context, @NonNull String packageName) {

        String hcMigratorPackage = getDataMigratorPackageName(context);
        if (!Objects.equals(hcMigratorPackage, packageName)) {
            return;
        }

        int migrationState = getMigrationState();
        if ((migrationState == MIGRATION_STATE_IDLE
                        || migrationState == MIGRATION_STATE_APP_UPGRADE_REQUIRED)
                && isMigrationAware(context, packageName)) {

            updateMigrationState(context, MIGRATION_STATE_ALLOWED);
            return;
        }

        if (migrationState == MIGRATION_STATE_IDLE
                && hasMigratorPackageKnownSignerSignature(context, packageName)
                && !MigrationUtils.isPackageStub(context, packageName)) {
            // apk needs to upgrade
            updateMigrationState(context, MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        }

        if (migrationState == MIGRATION_STATE_ALLOWED) {
            for (StateChangedListener listener : mStateChangedListeners) {
                listener.onChanged(migrationState);
            }
        }
    }

    void onPackageRemoved(@NonNull Context context, @NonNull String packageName) {
        synchronized (mLock) {
            onPackageRemovedGuarded(context, packageName);
        }
    }

    @GuardedBy("mLock")
    private void onPackageRemovedGuarded(@NonNull Context context, @NonNull String packageName) {
        String hcMigratorPackage = getDataMigratorPackageName(context);
        if (!Objects.equals(hcMigratorPackage, packageName)) {
            return;
        }

        if (getMigrationState() != MIGRATION_STATE_COMPLETE) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "Migrator package uninstalled. Marking migration complete.");
            }

            updateMigrationState(context, MIGRATION_STATE_COMPLETE);
        }
    }

    /**
     * Updates the migration state preference and the timeout reached preferences.
     *
     * @param timeoutReached Whether the previous state has timed out.
     */
    @GuardedBy("mLock")
    private void updateMigrationStatePreference(
            @NonNull Context context,
            @HealthConnectDataState.DataMigrationState int migrationState,
            boolean timeoutReached) {

        @HealthConnectDataState.DataMigrationState int previousMigrationState = getMigrationState();

        HashMap<String, String> preferences =
                new HashMap<>(
                        Map.of(
                                MIGRATION_STATE_PREFERENCE_KEY,
                                String.valueOf(migrationState),
                                CURRENT_STATE_START_TIME_KEY,
                                Instant.now().toString()));

        if (migrationState == MIGRATION_STATE_IN_PROGRESS) {
            // Reset the in progress timeout key reached if we move to In Progress
            preferences.put(IN_PROGRESS_TIMEOUT_REACHED_KEY, String.valueOf(false));
        }

        if (migrationState == MIGRATION_STATE_ALLOWED && timeoutReached) {
            preferences.put(IN_PROGRESS_TIMEOUT_REACHED_KEY, String.valueOf(true));
        }

        if (migrationState == MIGRATION_STATE_COMPLETE
                && previousMigrationState == MIGRATION_STATE_IDLE
                && timeoutReached) {
            preferences.put(IDLE_TIMEOUT_REACHED_KEY, String.valueOf(true));
        }

        // If we are setting the migration state to ALLOWED for the first time.
        if (migrationState == MIGRATION_STATE_ALLOWED && Objects.isNull(getAllowedStateTimeout())) {
            preferences.put(ALLOWED_STATE_START_TIME_KEY, Instant.now().toString());
        }
        PreferenceHelper.getInstance().insertOrReplacePreferencesTransaction(preferences);

        if (mMigrationBroadcastScheduler != null) {
            //noinspection Convert2Lambda
            HealthConnectThreadScheduler.scheduleInternalTask(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mMigrationBroadcastScheduler.scheduleNewJobs(context);
                            } catch (Exception e) {
                                Slog.e(TAG, "Migration broadcast schedule failed", e);
                            }
                        }
                    });
        } else if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Unable to schedule migration broadcasts: "
                            + "MigrationBroadcastScheduler object is null");
        }

        for (StateChangedListener listener : mStateChangedListeners) {
            listener.onChanged(migrationState);
        }
    }

    /**
     * Checks if the original {@link MIGRATION_STATE_ALLOWED} timeout period has passed. We do not
     * want to reset the ALLOWED_STATE timeout everytime state changes to this state, hence
     * persisting the original timeout time.
     */
    boolean hasAllowedStateTimedOut() {
        String allowedStateTimeout = getAllowedStateTimeout();
        if (!Objects.isNull(allowedStateTimeout)
                && Instant.now().isAfter(Instant.parse(allowedStateTimeout))) {
            Slog.e(TAG, "Allowed state period has timed out.");
            return true;
        }
        return false;
    }

    /** Checks if the IN_PROGRESS_TIMEOUT has passed. */
    boolean hasInProgressStateTimedOut() {
        synchronized (mLock) {
            String inProgressTimeoutReached =
                    PreferenceHelper.getInstance().getPreference(IN_PROGRESS_TIMEOUT_REACHED_KEY);

            if (!Objects.isNull(inProgressTimeoutReached)) {
                return Boolean.parseBoolean(inProgressTimeoutReached);
            }
            return false;
        }
    }

    /** Checks if the IDLE state has timed out. */
    boolean hasIdleStateTimedOut() {
        synchronized (mLock) {
            String idleStateTimeoutReached =
                    PreferenceHelper.getInstance().getPreference(IDLE_TIMEOUT_REACHED_KEY);

            if (!Objects.isNull(idleStateTimeoutReached)) {
                return Boolean.parseBoolean(idleStateTimeoutReached);
            }
            return false;
        }
    }

    /**
     * Reconcile migration state to the current migrator package status in case we missed a package
     * change broadcast.
     */
    @GuardedBy("mLock")
    private void reconcilePackageChangesWithStates(Context context) {
        int migrationState = getMigrationState();
        if (migrationState == MIGRATION_STATE_APP_UPGRADE_REQUIRED
                && existsMigrationAwarePackage(context)) {
            updateMigrationState(context, MIGRATION_STATE_ALLOWED);
            return;
        }

        if (migrationState == MIGRATION_STATE_IDLE) {
            if (existsMigrationAwarePackage(context)) {
                updateMigrationState(context, MIGRATION_STATE_ALLOWED);
                return;
            }

            if (existsMigratorPackage(context)
                    && !MigrationUtils.isPackageStub(
                            context, getDataMigratorPackageName(context))) {
                updateMigrationState(context, MIGRATION_STATE_APP_UPGRADE_REQUIRED);
                return;
            }
        }
        if (migrationState != MIGRATION_STATE_IDLE && migrationState != MIGRATION_STATE_COMPLETE) {
            completeMigrationIfNoMigratorPackageAvailable(context);
        }
    }

    /** Reconcile the current state with its appropriate state change job. */
    @GuardedBy("mLock")
    private void reconcileStateChangeJob(@NonNull Context context) {
        switch (getMigrationState()) {
            case MIGRATION_STATE_IDLE:
            case MIGRATION_STATE_APP_UPGRADE_REQUIRED:
            case MIGRATION_STATE_ALLOWED:
                if (!MigrationStateChangeJob.existsAStateChangeJob(
                        context, MIGRATION_COMPLETE_JOB_NAME)) {
                    MigrationStateChangeJob.scheduleMigrationCompletionJob(context, mUserId);
                }
                return;
            case MIGRATION_STATE_MODULE_UPGRADE_REQUIRED:
                handleIsUpgradeStillRequired(context);
                return;

            case MIGRATION_STATE_IN_PROGRESS:
                if (!MigrationStateChangeJob.existsAStateChangeJob(
                        context, MIGRATION_PAUSE_JOB_NAME)) {
                    MigrationStateChangeJob.scheduleMigrationPauseJob(context, mUserId);
                }
                return;

            case MIGRATION_STATE_COMPLETE:
                MigrationStateChangeJob.cancelAllJobs(context);
        }
    }

    /**
     * Checks if the version set by the migrator apk is the current module version and send a {@link
     * HealthConnectManager.ACTION_HEALTH_CONNECT_MIGRATION_READY intent. If not, re-sync the state
     * update job.}
     */
    @GuardedBy("mLock")
    private void handleIsUpgradeStillRequired(@NonNull Context context) {
        if (Integer.parseInt(
                        PreferenceHelper.getInstance()
                                .getPreference(MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY))
                <= getUdcSdkExtensionVersion()) {
            updateMigrationState(context, MIGRATION_STATE_ALLOWED);
            return;
        }
        if (!MigrationStateChangeJob.existsAStateChangeJob(context, MIGRATION_COMPLETE_JOB_NAME)) {
            MigrationStateChangeJob.scheduleMigrationCompletionJob(context, mUserId);
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    String getAllowedStateTimeout() {
        String allowedStateStartTime =
                PreferenceHelper.getInstance().getPreference(ALLOWED_STATE_START_TIME_KEY);
        if (allowedStateStartTime != null) {
            return Instant.parse(allowedStateStartTime)
                    .plusMillis(
                            mHealthConnectDeviceConfigManager
                                    .getNonIdleStateTimeoutPeriod()
                                    .toMillis())
                    .toString();
        }
        return null;
    }

    private void throwIfMigrationIsComplete() throws IllegalMigrationStateException {
        if (getMigrationState() == MIGRATION_STATE_COMPLETE) {
            throw new IllegalMigrationStateException("Migration already marked complete.");
        }
    }

    /**
     * Tracks the number of times migration is started from {@link MIGRATION_STATE_ALLOWED}. If more
     * than 3 times, the migration is marked as complete
     */
    @GuardedBy("mLock")
    private void updateMigrationStartsCount() {
        PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
        String migrationStartsCount =
                Optional.ofNullable(preferenceHelper.getPreference(MIGRATION_STARTS_COUNT_KEY))
                        .orElse("0");

        preferenceHelper.insertOrReplacePreference(
                MIGRATION_STARTS_COUNT_KEY,
                String.valueOf(Integer.parseInt(migrationStartsCount) + 1));
    }

    private String getDataMigratorPackageName(@NonNull Context context) {
        return context.getString(
                context.getResources().getIdentifier(HC_PACKAGE_NAME_CONFIG_NAME, null, null));
    }

    private void completeMigrationIfNoMigratorPackageAvailable(@NonNull Context context) {
        if (existsMigrationAwarePackage(context)) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "There is a migration aware package.");
            }
            return;
        }

        if (existsMigratorPackage(context)) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "There is a package with migration known signers certificate.");
            }
            return;
        }

        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "There is no migration aware package or any package with migration known "
                            + "signers certificate. Marking migration as complete.");
        }
        updateMigrationState(context, MIGRATION_STATE_COMPLETE);
    }

    /** Returns whether there exists a package that is aware of migration. */
    public boolean existsMigrationAwarePackage(@NonNull Context context) {
        List<String> filteredPackages =
                filterIntent(
                        context,
                        filterPermissions(context),
                        PackageManager.MATCH_ALL | PackageManager.MATCH_DISABLED_COMPONENTS);
        String dataMigratorPackageName = getDataMigratorPackageName(context);
        List<String> filteredDataMigratorPackageNames =
                filteredPackages.stream()
                        .filter(packageName -> packageName.equals(dataMigratorPackageName))
                        .toList();

        return filteredDataMigratorPackageNames.size() != 0;
    }

    /**
     * Returns whether there exists a package that is signed with the correct signatures for
     * migration.
     */
    public boolean existsMigratorPackage(@NonNull Context context) {
        // Search through all packages by known signer certificate.
        List<PackageInfo> allPackages =
                context.getPackageManager()
                        .getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES);
        String[] knownSignerCerts = getMigrationKnownSignerCertificates(context);

        for (PackageInfo packageInfo : allPackages) {
            if (hasMatchingSignatures(getPackageSignatures(packageInfo), knownSignerCerts)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMigrationAware(@NonNull Context context, @NonNull String packageName) {
        List<String> permissionFilteredPackages = filterPermissions(context);
        List<String> filteredPackages =
                filterIntent(
                        context,
                        permissionFilteredPackages,
                        PackageManager.MATCH_ALL | PackageManager.MATCH_DISABLED_COMPONENTS);
        int numPackages = filteredPackages.size();

        if (numPackages == 0) {
            Slog.i(TAG, "There are no migration aware apps");
        } else if (numPackages == 1) {
            return Objects.equals(filteredPackages.get(0), packageName);
        }
        return false;
    }

    /** Checks whether the APK migration flag is on. */
    boolean doesMigratorHandleInfoIntent(@NonNull Context context) {
        String packageName = getDataMigratorPackageName(context);
        Intent intent =
                new Intent(HealthConnectManager.ACTION_SHOW_MIGRATION_INFO).setPackage(packageName);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> allComponents =
                pm.queryIntentActivities(
                        intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        return !allComponents.isEmpty();
    }

    private static boolean hasMigratorPackageKnownSignerSignature(
            @NonNull Context context, @NonNull String packageName) {
        List<String> stringSignatures;
        try {
            stringSignatures =
                    getPackageSignatures(
                            context.getPackageManager()
                                    .getPackageInfo(
                                            packageName, PackageManager.GET_SIGNING_CERTIFICATES));

        } catch (PackageManager.NameNotFoundException e) {
            Slog.i(TAG, "Could not get package signatures. Package not found");
            return false;
        }

        if (stringSignatures.isEmpty()) {
            return false;
        }
        return hasMatchingSignatures(
                stringSignatures, getMigrationKnownSignerCertificates(context));
    }

    private static boolean hasMatchingSignatures(
            List<String> stringSignatures, String[] migrationKnownSignerCertificates) {

        return !Collections.disjoint(
                stringSignatures.stream().map(String::toLowerCase).toList(),
                Arrays.stream(migrationKnownSignerCertificates).map(String::toLowerCase).toList());
    }

    private static String[] getMigrationKnownSignerCertificates(Context context) {
        return context.getResources()
                .getStringArray(
                        Resources.getSystem()
                                .getIdentifier(HC_RELEASE_CERT_CONFIG_NAME, null, null));
    }

    private static List<String> getPackageSignatures(PackageInfo packageInfo) {
        return Arrays.stream(packageInfo.signingInfo.getApkContentsSigners())
                .map(signature -> MigrationUtils.computeSha256DigestBytes(signature.toByteArray()))
                .filter(signature -> signature != null)
                .toList();
    }

    private int getUdcSdkExtensionVersion() {
        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
    }

    private int getStartMigrationCount() {
        return Integer.parseInt(
                Optional.ofNullable(
                                PreferenceHelper.getInstance()
                                        .getPreference(MIGRATION_STARTS_COUNT_KEY))
                        .orElse("0"));
    }

    /**
     * Resets migration state to IDLE state for early users whose migration might have timed out
     * before they migrate data.
     */
    void resetMigrationStateIfNeeded(@NonNull Context context) {
        PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();

        if (!Boolean.parseBoolean(preferenceHelper.getPreference(HAVE_RESET_MIGRATION_STATE_KEY))
                && hasMigrationTimedOutPrematurely()) {
            updateMigrationState(context, MIGRATION_STATE_IDLE);
            preferenceHelper.insertOrReplacePreference(
                    HAVE_RESET_MIGRATION_STATE_KEY, String.valueOf(true));
        }
    }

    private boolean hasMigrationTimedOutPrematurely() {
        String currentStateStartTime =
                PreferenceHelper.getInstance().getPreference(CURRENT_STATE_START_TIME_KEY);

        if (!Objects.isNull(currentStateStartTime)) {
            return getMigrationState() == MIGRATION_STATE_COMPLETE
                    && LocalDate.ofInstant(Instant.parse(currentStateStartTime), ZoneOffset.MIN)
                            .isBefore(PREMATURE_MIGRATION_TIMEOUT_DATE);
        }
        return false;
    }

    /**
     * A listener for observing migration state changes.
     *
     * @see MigrationStateManager#addStateChangedListener(StateChangedListener)
     */
    public interface StateChangedListener {

        /**
         * Called on every migration state change.
         *
         * @param state the new migration state.
         */
        void onChanged(@HealthConnectDataState.DataMigrationState int state);
    }
}
