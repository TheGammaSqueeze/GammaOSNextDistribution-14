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

package com.android.server.appsearch.contactsindexer;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.AppSearchEnvironmentFactory;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.util.LogUtil;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.stats.AppSearchStatsLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Contacts Indexer for a single user.
 *
 * <p>It reads the updated/newly-inserted/deleted contacts from CP2, and sync the changes into
 * AppSearch.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
public final class ContactsIndexerUserInstance {

    private static final String TAG = "ContactsIndexerUserInst";

    private final Context mContext;
    private final File mDataDir;
    private final ContactsIndexerSettings mSettings;
    private final ContactsObserver mContactsObserver;

    // Those two booleans below are used for batching/throttling the contact change
    // notification so we won't schedule too many delta updates.
    private final Object mDeltaUpdateLock = new Object();
    // Whether a delta update has been scheduled or run. Now we only allow one delta update being
    // run at a time.
    @GuardedBy("mDeltaUpdateLock")
    private boolean mDeltaUpdateScheduled = false;
    // Whether we are receiving notifications from CP2.
    @GuardedBy("mDeltaUpdateLock")
    private boolean mCp2ChangePending = false;

    private final AppSearchHelper mAppSearchHelper;
    private final ContactsIndexerImpl mContactsIndexerImpl;
    private final ContactsIndexerConfig mContactsIndexerConfig;

    /**
     * Single threaded executor to make sure there is only one active sync for this {@link
     * ContactsIndexerUserInstance}. Background tasks should be scheduled using {@link
     * #executeOnSingleThreadedExecutor(Runnable)} which ensures that they are not executed if the
     * executor is shutdown during {@link #shutdown()}.
     *
     * <p>Note that this executor is used as both work and callback executors by {@link
     * #mAppSearchHelper} which is fine because AppSearch should be able to handle exceptions
     * thrown by them.
     */
    private final ExecutorService mSingleThreadedExecutor;

    /**
     * Constructs and initializes a {@link ContactsIndexerUserInstance}.
     *
     * <p>Heavy operations such as connecting to AppSearch are performed asynchronously.
     *
     * @param contactsDir data directory for ContactsIndexer.
     */
    @NonNull
    public static ContactsIndexerUserInstance createInstance(@NonNull Context userContext,
            @NonNull File contactsDir, @NonNull ContactsIndexerConfig contactsIndexerConfig) {
        Objects.requireNonNull(userContext);
        Objects.requireNonNull(contactsDir);
        Objects.requireNonNull(contactsIndexerConfig);

        ExecutorService singleThreadedExecutor = AppSearchEnvironmentFactory
                .getEnvironmentInstance().createSingleThreadExecutor();
        return createInstance(userContext, contactsDir, contactsIndexerConfig,
                singleThreadedExecutor);
    }

    @VisibleForTesting
    @NonNull
    /*package*/ static ContactsIndexerUserInstance createInstance(@NonNull Context context,
            @NonNull File contactsDir, @NonNull ContactsIndexerConfig contactsIndexerConfig,
            @NonNull ExecutorService executorService) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(contactsDir);
        Objects.requireNonNull(contactsIndexerConfig);
        Objects.requireNonNull(executorService);

        AppSearchHelper appSearchHelper = AppSearchHelper.createAppSearchHelper(context,
                executorService, contactsIndexerConfig);
        ContactsIndexerUserInstance indexer = new ContactsIndexerUserInstance(context,
                contactsDir, appSearchHelper, contactsIndexerConfig, executorService);
        indexer.loadSettingsAsync();

        return indexer;
    }

    /**
     * Constructs a {@link ContactsIndexerUserInstance}.
     *
     * @param context                Context object passed from
     *                               {@link ContactsIndexerManagerService}
     * @param dataDir                data directory for storing contacts indexer state.
     * @param contactsIndexerConfig  configuration for the Contacts Indexer.
     * @param singleThreadedExecutor an {@link ExecutorService} with at most one thread to ensure
     *                               the thread safety of this class.
     */
    private ContactsIndexerUserInstance(@NonNull Context context, @NonNull File dataDir,
            @NonNull AppSearchHelper appSearchHelper,
            @NonNull ContactsIndexerConfig contactsIndexerConfig,
            @NonNull ExecutorService singleThreadedExecutor) {
        mContext = Objects.requireNonNull(context);
        mDataDir = Objects.requireNonNull(dataDir);
        mContactsIndexerConfig = Objects.requireNonNull(contactsIndexerConfig);
        mSettings = new ContactsIndexerSettings(mDataDir);
        mAppSearchHelper = Objects.requireNonNull(appSearchHelper);
        mSingleThreadedExecutor = Objects.requireNonNull(singleThreadedExecutor);
        mContactsObserver = new ContactsObserver();
        mContactsIndexerImpl = new ContactsIndexerImpl(context, appSearchHelper);
    }

    public void startAsync() {
        if (LogUtil.DEBUG) {
            Log.d(TAG, "Registering ContactsObserver for " + mContext.getUser());
        }
        mContext.getContentResolver()
                .registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI,
                        /*notifyForDescendants=*/ true,
                        mContactsObserver);

        executeOnSingleThreadedExecutor(() -> {
            mAppSearchHelper.isDataLikelyWipedDuringInitAsync().thenCompose(
                    isDataLikelyWipedDuringInit -> {
                        if (isDataLikelyWipedDuringInit) {
                            mSettings.reset();
                            // Persist the settings right away just in case there is a crash later.
                            // In this case, the full update still need to be run during the next
                            // boot to reindex the data.
                            persistSettings();
                        }
                        doCp2SyncFirstRun();
                        // This value won't be used anymore, so return null here.
                        return CompletableFuture.completedFuture(null);
                    }).exceptionally(e -> Log.w(TAG, "Got exception in startAsync", e));
        });
    }

    public void shutdown() throws InterruptedException {
        if (LogUtil.DEBUG) {
            Log.d(TAG, "Unregistering ContactsObserver for " + mContext.getUser());
        }
        mContext.getContentResolver().unregisterContentObserver(mContactsObserver);

        ContactsIndexerMaintenanceService.cancelFullUpdateJobIfScheduled(mContext,
                mContext.getUser());
        synchronized (mSingleThreadedExecutor) {
            mSingleThreadedExecutor.shutdown();
        }
        boolean unused = mSingleThreadedExecutor.awaitTermination(30L, TimeUnit.SECONDS);
    }

    private class ContactsObserver extends ContentObserver {
        public ContactsObserver() {
            super(/*handler=*/ null);
        }

        @Override
        public void onChange(boolean selfChange, @NonNull Collection<Uri> uris, int flags) {
            if (!selfChange) {
                executeOnSingleThreadedExecutor(
                        ContactsIndexerUserInstance.this::handleDeltaUpdate);
            }
        }
    }

    /**
     * Performs a one-time sync of CP2 contacts into AppSearch.
     *
     * <p>This handles the scenario where this contacts indexer instance has been started for the
     * current device user for the first time or the background full update job is not scheduled.
     * The full-update job which syncs all CP2 contacts is scheduled to run when the device is idle
     * and its battery is not low. It can take several minutes or hours for these constraints to be
     * met. Additionally, the delta-update job which runs on each CP2 change notification is
     * designed to sync only the changed contacts because the user might be actively using the
     * device at that time.
     *
     * <p>Schedules a one-off full update job to sync all CP2 contacts when the device is idle.
     * Also syncs a configurable number of CP2 contacts into the AppSearch Person corpus so that
     * it's nominally functional.
     */
    private void doCp2SyncFirstRun() {
        // If this is not the first run of contacts indexer (lastFullUpdateTimestampMillis is not 0)
        // for the given user and a full update job is scheduled, this means that contacts indexer
        // has been running recently and contacts should be up to date. The initial sync can be
        // skipped in this case.
        // If the job is not scheduled but lastFullUpdateTimestampMillis is not 0, the contacts
        // indexer was disabled before. We need to reschedule the job and run a limited delta update
        // to bring latest contact change in AppSearch right away, after it is re-enabled.
        if (mSettings.getLastFullUpdateTimestampMillis() != 0 &&
                ContactsIndexerMaintenanceService.isFullUpdateJobScheduled(mContext,
                        mContext.getUser().getIdentifier())) {
            return;
        }
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext,
                mContext.getUser().getIdentifier(), /*periodic=*/ false, /*intervalMillis=*/ -1);
        // TODO(b/222126568): refactor doDeltaUpdateAsync() to return a future value of
        // ContactsUpdateStats so that it can be checked and logged here, instead of the
        // placeholder exceptionally() block that only logs to the console.
        doDeltaUpdateAsync(mContactsIndexerConfig.getContactsFirstRunIndexingLimit(),
                new ContactsUpdateStats()).exceptionally(t -> {
            if (LogUtil.DEBUG) {
                Log.d(TAG, "Failed to bootstrap Person corpus with CP2 contacts", t);
            }
            return null;
        });
    }

    /**
     * Performs a full sync of CP2 contacts to AppSearch builtin:Person corpus.
     *
     * @param signal Used to indicate if the full update task should be cancelled.
     */
    public void doFullUpdateAsync(@Nullable CancellationSignal signal) {
        executeOnSingleThreadedExecutor(() -> {
            ContactsUpdateStats updateStats = new ContactsUpdateStats();
            doFullUpdateInternalAsync(signal, updateStats);
            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext,
                    mContext.getUser().getIdentifier(), /*periodic=*/ true,
                    mContactsIndexerConfig.getContactsFullUpdateIntervalMillis());
        });
    }

    /** Dumps the internal state of this {@link ContactsIndexerUserInstance}. */
    public void dump(@NonNull PrintWriter pw, boolean verbose) {
        // Those timestamps are not protected by any lock since in ContactsIndexerUserInstance
        // we only have one thread to handle all the updates. It is possible we might run into
        // race condition if there is an update running while those numbers are being printed.
        // This is acceptable though for debug purpose, so still no lock here.
        pw.println(
                "last_full_update_timestamp_millis: " +
                        mSettings.getLastFullUpdateTimestampMillis());
        pw.println(
                "last_delta_update_timestamp_millis: " +
                        mSettings.getLastDeltaUpdateTimestampMillis());
        pw.println(
                "last_contact_update_timestamp_millis: " +
                        mSettings.getLastContactUpdateTimestampMillis());
        pw.println(
                "last_contact_delete_timestamp_millis: " +
                        mSettings.getLastContactDeleteTimestampMillis());
    }

    @VisibleForTesting
    CompletableFuture<Void> doFullUpdateInternalAsync(
            @Nullable CancellationSignal signal, @NonNull ContactsUpdateStats updateStats) {
        // TODO(b/203605504): handle cancellation signal to abort the job.
        long currentTimeMillis = System.currentTimeMillis();
        updateStats.mUpdateType = ContactsUpdateStats.FULL_UPDATE;
        updateStats.mUpdateAndDeleteStartTimeMillis = currentTimeMillis;
        updateStats.mLastFullUpdateStartTimeMillis = mSettings.getLastFullUpdateTimestampMillis();
        updateStats.mLastDeltaUpdateStartTimeMillis = mSettings.getLastDeltaUpdateTimestampMillis();

        List<String> cp2ContactIds = new ArrayList<>();
        // Get a list of all contact IDs from CP2
        updateStats.mLastContactUpdatedTimeMillis = ContactsProviderUtil.getUpdatedContactIds(
                mContext, /*sinceFilter=*/ 0, mContactsIndexerConfig.getContactsFullUpdateLimit(),
                cp2ContactIds, updateStats);
        updateStats.mPreviousLastContactUpdatedTimeMillis =
                mSettings.getLastContactUpdateTimestampMillis();
        return mAppSearchHelper.getAllContactIdsAsync()
                .thenCompose(appsearchContactIds -> {
                    // all_contacts_from_AppSearch - all_contacts_from_cp2 =
                    // contacts_needs_to_be_removed_from_AppSearch.
                    appsearchContactIds.removeAll(cp2ContactIds);
                    // Full update doesn't happen very often. In normal cases, it is scheduled to
                    // be run every 15-30 days.
                    // One-off full update can be scheduled if
                    // 1) during startup, full update has never been run.
                    // 2) or we get OUT_OF_SPACE from AppSearch.
                    // So print a message once in 15-30 days should be acceptable.
                    Log.i(TAG, "Performing a full sync (updated:" + cp2ContactIds.size()
                            + ", deleted:" + appsearchContactIds.size()
                            + ") of CP2 contacts in AppSearch");
                    return mContactsIndexerImpl.updatePersonCorpusAsync(/*wantedContactIds=*/
                            cp2ContactIds, /*unwantedContactIds=*/ appsearchContactIds,
                            updateStats, mContactsIndexerConfig.shouldKeepUpdatingOnError());
                }).handle((x, t) -> {
                    if (t != null) {
                        Log.w(TAG, "Failed to perform full update", t);
                        if (updateStats.mUpdateStatuses.isEmpty()
                                && updateStats.mDeleteStatuses.isEmpty()) {
                            // Somehow this error is not reflected in the stats, and
                            // unfortunately we don't know what part is wrong. Just add an error
                            // code for the update.
                            updateStats.mUpdateStatuses.add(
                                    ContactsUpdateStats.ERROR_CODE_CONTACTS_INDEXER_UNKNOWN_ERROR);
                        }
                    }

                    // Always persist the current timestamps for full update for both success and
                    // failure. Right now we are taking the best effort to keep CP2 and AppSearch
                    // in sync, without any retry in case of failure. We don't want an unexpected
                    // error, like a bad document, prevent the timestamps getting updated, which
                    // will make the indexer fetch a lot of contacts for EACH delta update.
                    // TODO(b/226078966) Also finding the update timestamps for last success is
                    //  not trivial, and we should think more about how to do that correctly.
                    mSettings.setLastFullUpdateTimestampMillis(currentTimeMillis);
                    mSettings.setLastContactUpdateTimestampMillis(currentTimeMillis);
                    mSettings.setLastContactDeleteTimestampMillis(currentTimeMillis);
                    persistSettings();
                    logStats(updateStats);
                    return null;
                });
    }

    /**
     * Does the delta/instant update to sync the contacts from CP2 to AppSearch.
     *
     * <p>{@link #mDeltaUpdateScheduled} is being used to avoid scheduling any update BEFORE an
     * active update finishes.
     *
     * <p>{@link #mSingleThreadedExecutor} is being used to make sure there is one and only one
     * delta update can be scheduled and run.
     */
    @VisibleForTesting
    /*package*/ void handleDeltaUpdate() {
        if (!ContentResolver.getCurrentSyncs().isEmpty()) {
            // TODO(b/221905367): make sure that the delta update is scheduled as soon
            //  as the current sync is completed.
            if (LogUtil.DEBUG) {
                Log.v(TAG, "Deferring delta updates until the current sync is complete");
            }
            return;
        }

        synchronized (mDeltaUpdateLock) {
            // Record that a CP2 change notification has been received, and will be handled
            // by the next delta update task.
            mCp2ChangePending = true;
            scheduleDeltaUpdateLocked();
        }
    }

    /**
     * Schedule a delta update. No new delta update can be scheduled if there is one delta update
     * already scheduled or currently being run.
     *
     * <p>ATTENTION!!! This function needs to be light weight since it is being called by CP2 with a
     * lock.
     */
    @GuardedBy("mDeltaUpdateLock")
    private void scheduleDeltaUpdateLocked() {
        if (mDeltaUpdateScheduled) {
            return;
        }
        mDeltaUpdateScheduled = true;
        executeOnSingleThreadedExecutor(() -> {
            ContactsUpdateStats updateStats = new ContactsUpdateStats();
            // TODO(b/226489369): apply instant indexing limit on CP2 changes also?
            // TODO(b/222126568): refactor doDeltaUpdateAsync() to return a future value of
            //  ContactsUpdateStats so that it can be checked and logged here, instead of the
            //  placeholder exceptionally() block that only logs to the console.
            doDeltaUpdateAsync(mContactsIndexerConfig.getContactsDeltaUpdateLimit(),
                    updateStats).exceptionally(t -> {
                if (LogUtil.DEBUG) {
                    Log.d(TAG, "Failed to index CP2 change", t);
                }
                return null;
            });
        });
    }

    /**
     * Does the delta update. It also resets
     * {@link ContactsIndexerUserInstance#mDeltaUpdateScheduled} to false.
     */
    @VisibleForTesting
    /*package*/ CompletableFuture<Void> doDeltaUpdateAsync(
            int indexingLimit, @NonNull ContactsUpdateStats updateStats) {
        synchronized (mDeltaUpdateLock) {
            // Record that the CP2 change notification is being handled by this delta update task.
            mCp2ChangePending = false;
        }

        long currentTimeMillis = System.currentTimeMillis();
        updateStats.mUpdateType = ContactsUpdateStats.DELTA_UPDATE;
        updateStats.mUpdateAndDeleteStartTimeMillis = currentTimeMillis;
        updateStats.mLastFullUpdateStartTimeMillis = mSettings.getLastFullUpdateTimestampMillis();
        updateStats.mLastDeltaUpdateStartTimeMillis = mSettings.getLastDeltaUpdateTimestampMillis();
        long lastContactUpdateTimestampMillis = mSettings.getLastContactUpdateTimestampMillis();
        long lastContactDeleteTimestampMillis = mSettings.getLastContactDeleteTimestampMillis();
        if (LogUtil.DEBUG) {
            Log.d(TAG, "previous timestamps --"
                    + " lastContactUpdateTimestampMillis: " + lastContactUpdateTimestampMillis
                    + " lastContactDeleteTimestampMillis: " + lastContactDeleteTimestampMillis);
        }

        List<String> wantedIds = new ArrayList<>();
        List<String> unWantedIds = new ArrayList<>();
        long mostRecentContactUpdatedTimestampMillis =
                ContactsProviderUtil.getUpdatedContactIds(mContext,
                        lastContactUpdateTimestampMillis, indexingLimit, wantedIds, updateStats);
        long mostRecentContactDeletedTimestampMillis =
                ContactsProviderUtil.getDeletedContactIds(mContext,
                        lastContactDeleteTimestampMillis, unWantedIds, updateStats);
        updateStats.mLastContactUpdatedTimeMillis = mostRecentContactUpdatedTimestampMillis;
        updateStats.mLastContactDeletedTimeMillis = mostRecentContactDeletedTimestampMillis;

        // Update the person corpus in AppSearch based on the changed contact
        // information we get from CP2. At this point mUpdateScheduled has been
        // reset, so a new task is allowed to catch any new changes in CP2.
        // TODO(b/203605504) for future improvement. Report errors here and persist the right
        //  timestamps for last successful deletion and update. This requires the ids from CP2
        //  are sorted in last_update_timestamp ascending order, and the code would become a
        //  little complicated.
        return mContactsIndexerImpl.updatePersonCorpusAsync(wantedIds, unWantedIds,
                        updateStats, mContactsIndexerConfig.shouldKeepUpdatingOnError())
                .handle((x, t) -> {
                    try {
                        if (t != null) {
                            Log.w(TAG, "Failed to perform delta update", t);
                            if (updateStats.mUpdateStatuses.isEmpty()
                                    && updateStats.mDeleteStatuses.isEmpty()) {
                                // Somehow this error is not reflected in the stats, and
                                // unfortunately we don't know which part is wrong. Just add an
                                // error code for the update.
                                updateStats.mUpdateStatuses.add(
                                        ContactsUpdateStats
                                                .ERROR_CODE_CONTACTS_INDEXER_UNKNOWN_ERROR);
                            }
                        }
                        // Persisting timestamping and logging, no matter if update succeeds or not.
                        if (LogUtil.DEBUG) {
                            Log.d(TAG, "updated timestamps --"
                                    + " lastContactUpdateTimestampMillis: "
                                    + mostRecentContactUpdatedTimestampMillis
                                    + " lastContactDeleteTimestampMillis: "
                                    + mostRecentContactDeletedTimestampMillis);
                        }
                        mSettings.setLastContactUpdateTimestampMillis(
                                mostRecentContactUpdatedTimestampMillis);
                        mSettings.setLastContactDeleteTimestampMillis(
                                mostRecentContactDeletedTimestampMillis);
                        mSettings.setLastDeltaUpdateTimestampMillis(currentTimeMillis);
                        persistSettings();
                        logStats(updateStats);
                        if (updateStats.mUpdateStatuses.contains(
                                AppSearchResult.RESULT_OUT_OF_SPACE)) {
                            // Some indexing failed due to OUT_OF_SPACE from AppSearch. We can
                            // simply schedule a full update so we can trim the Person corpus in
                            // AppSearch to make some room for delta update. We need to monitor
                            // the failure count and reasons for indexing during full update to
                            // see if that limit (10,000) is too big right now, considering we
                            // are sharing this limit with any AppSearch clients, e.g.
                            // ShortcutManager, in the system server.
                            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext,
                                    mContext.getUser().getIdentifier(), /*periodic=*/ false,
                                    /*intervalMillis=*/ -1);
                        }

                        return null;
                    } finally {
                        synchronized (mDeltaUpdateLock) {
                            // The current delta update is done. Reset the flag so new delta
                            // update can be scheduled and run.
                            mDeltaUpdateScheduled = false;
                            // If another CP2 change notifications were received while this delta
                            // update task was running, schedule it again.
                            if (mCp2ChangePending) {
                                scheduleDeltaUpdateLocked();
                            }
                        }
                    }
                });
    }

    // Logs the stats to statsd.
    @VisibleForTesting
    void logStats(@NonNull ContactsUpdateStats updateStats) {
        int totalUpdateLatency =
                (int) (System.currentTimeMillis()
                        - updateStats.mUpdateAndDeleteStartTimeMillis);
        // Finalize status code for update and delete.
        if (updateStats.mUpdateStatuses.isEmpty()) {
            // SUCCESS if no error found.
            updateStats.mUpdateStatuses.add(AppSearchResult.RESULT_OK);
        }
        if (updateStats.mDeleteStatuses.isEmpty()) {
            // SUCCESS if no error found.
            updateStats.mDeleteStatuses.add(AppSearchResult.RESULT_OK);
        }

        // Get the accurate count for failed cases. The current failed count doesn't include
        // the contacts skipped due to failures in previous batches. Once a batch fails, all the
        // following batches will be skipped. The contacts in those batches should be counted as
        // failure as well.
        updateStats.mContactsUpdateFailedCount =
                updateStats.mTotalContactsToBeUpdated - updateStats.mContactsUpdateSucceededCount
                        - updateStats.mContactsUpdateSkippedCount;
        updateStats.mContactsDeleteFailedCount =
                updateStats.mTotalContactsToBeDeleted - updateStats.mContactsDeleteSucceededCount;

        int[] updateStatusArr = new int[updateStats.mUpdateStatuses.size()];
        int[] deleteStatusArr = new int[updateStats.mDeleteStatuses.size()];
        int updateIdx = 0;
        int deleteIdx = 0;
        for (int updateStatus : updateStats.mUpdateStatuses) {
            updateStatusArr[updateIdx] = updateStatus;
            ++updateIdx;
        }
        for (int deleteStatus : updateStats.mDeleteStatuses) {
            deleteStatusArr[deleteIdx] = deleteStatus;
            ++deleteIdx;
        }
        AppSearchStatsLog.write(
                AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED,
                updateStats.mUpdateType,
                totalUpdateLatency,
                updateStatusArr,
                deleteStatusArr,
                updateStats.mNewContactsToBeUpdated,
                updateStats.mContactsUpdateSucceededCount,
                updateStats.mContactsDeleteSucceededCount,
                updateStats.mContactsUpdateSkippedCount,
                updateStats.mContactsUpdateFailedCount,
                updateStats.mContactsDeleteFailedCount,
                updateStats.mContactsDeleteNotFoundCount,
                updateStats.mUpdateAndDeleteStartTimeMillis,
                updateStats.mLastFullUpdateStartTimeMillis,
                updateStats.mLastDeltaUpdateStartTimeMillis,
                updateStats.mLastContactUpdatedTimeMillis,
                updateStats.mLastContactDeletedTimeMillis,
                updateStats.mPreviousLastContactUpdatedTimeMillis);
    }

    /**
     * Loads the persisted data from disk.
     *
     * <p>It doesn't throw here. If it fails to load file, ContactsIndexer would always use the
     * timestamps persisted in the memory.
     */
    private void loadSettingsAsync() {
        executeOnSingleThreadedExecutor(() -> {
            boolean unused = mDataDir.mkdirs();
            try {
                mSettings.load();
            } catch (IOException e) {
                // Ignore file not found errors (bootstrap case)
                if (!(e instanceof FileNotFoundException)) {
                    Log.w(TAG, "Failed to load settings from disk", e);
                }
            }
        });
    }

    private void persistSettings() {
        try {
            mSettings.persist();
        } catch (IOException e) {
            Log.w(TAG, "Failed to save settings to disk", e);
        }
    }

    /**
     * Executes the given command on {@link  #mSingleThreadedExecutor} if it is still alive.
     *
     * <p>If the {@link #mSingleThreadedExecutor} has been shutdown, this method doesn't execute
     * the given command, and returns silently. Specifically, it does not throw
     * {@link java.util.concurrent.RejectedExecutionException}.
     *
     * @param command the runnable task
     */
    private void executeOnSingleThreadedExecutor(Runnable command) {
        synchronized (mSingleThreadedExecutor) {
            if (mSingleThreadedExecutor.isShutdown()) {
                Log.w(TAG, "Executor is shutdown, not executing task");
                return;
            }
            mSingleThreadedExecutor.execute(
                    () -> {
                        try {
                            command.run();
                        } catch (RuntimeException e) {
                            Slog.wtf(TAG, "ContactsIndexerUserInstance"
                                    + ".executeOnSingleThreadedExecutor() failed ", e);
                        }
                    }
            );
        }
    }
}
