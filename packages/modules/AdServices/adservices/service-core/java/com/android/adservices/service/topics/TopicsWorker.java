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

package com.android.adservices.service.topics;

import static com.android.adservices.ResultCode.RESULT_OK;

import android.adservices.topics.GetTopicsResult;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.topics.CombinedTopic;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle Topics API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@ThreadSafe
@WorkerThread
public class TopicsWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final Object SINGLETON_LOCK = new Object();

    // Singleton instance of the TopicsWorker.
    @GuardedBy("SINGLETON_LOCK")
    private static volatile TopicsWorker sTopicsWorker;

    // Lock for concurrent Read and Write processing in TopicsWorker.
    // Read-only API will only need to acquire Read Lock.
    // Write API (can update data) will need to acquire Write Lock.
    // This lock allows concurrent Read API and exclusive Write API.
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    private final EpochManager mEpochManager;
    private final CacheManager mCacheManager;
    private final BlockedTopicsManager mBlockedTopicsManager;
    private final AppUpdateManager mAppUpdateManager;
    private final Flags mFlags;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public TopicsWorker(
            @NonNull EpochManager epochManager,
            @NonNull CacheManager cacheManager,
            @NonNull BlockedTopicsManager blockedTopicsManager,
            @NonNull AppUpdateManager appUpdateManager,
            Flags flags) {
        mEpochManager = epochManager;
        mCacheManager = cacheManager;
        mBlockedTopicsManager = blockedTopicsManager;
        mAppUpdateManager = appUpdateManager;
        mFlags = flags;
    }

    /**
     * Gets an instance of TopicsWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static TopicsWorker getInstance(Context context) {
        if (sTopicsWorker == null) {
            synchronized (SINGLETON_LOCK) {
                if (sTopicsWorker == null) {
                    sTopicsWorker =
                            new TopicsWorker(
                                    EpochManager.getInstance(context),
                                    CacheManager.getInstance(context),
                                    BlockedTopicsManager.getInstance(context),
                                    AppUpdateManager.getInstance(context),
                                    FlagsFactory.getFlags());
                }
            }
        }
        return sTopicsWorker;
    }

    /**
     * Returns a list of all topics that could be returned to the {@link TopicsWorker} client.
     *
     * @return The list of Topics.
     */
    @NonNull
    public ImmutableList<Topic> getKnownTopicsWithConsent() {
        sLogger.v("TopicsWorker.getKnownTopicsWithConsent");
        mReadWriteLock.readLock().lock();
        try {
            return mCacheManager.getKnownTopicsWithConsent(mEpochManager.getCurrentEpochId());
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Returns a list of all topics that were blocked by the user.
     *
     * @return The list of Topics.
     */
    @NonNull
    public ImmutableList<Topic> getTopicsWithRevokedConsent() {
        sLogger.v("TopicsWorker.getTopicsWithRevokedConsent");
        mReadWriteLock.readLock().lock();
        try {
            return ImmutableList.copyOf(mBlockedTopicsManager.retrieveAllBlockedTopics());
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Revoke consent for provided {@link Topic} (block topic). This topic will not be returned by
     * any of the {@link TopicsWorker} methods.
     *
     * @param topic {@link Topic} to block.
     */
    public void revokeConsentForTopic(@NonNull Topic topic) {
        sLogger.v("TopicsWorker.revokeConsentForTopic");
        mReadWriteLock.writeLock().lock();
        try {
            mBlockedTopicsManager.blockTopic(topic);
        } finally {
            // TODO(b/234978199): optimize it - implement loading only blocked topics, not whole
            // cache
            loadCache();
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Restore consent for provided {@link Topic} (unblock the topic). This topic can be returned by
     * any of the {@link TopicsWorker} methods.
     *
     * @param topic {@link Topic} to restore consent for.
     */
    public void restoreConsentForTopic(@NonNull Topic topic) {
        sLogger.v("TopicsWorker.restoreConsentForTopic");
        mReadWriteLock.writeLock().lock();
        try {
            mBlockedTopicsManager.unblockTopic(topic);
        } finally {
            // TODO(b/234978199): optimize it - implement loading only blocked topics, not whole
            // cache
            loadCache();
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Get topics for the specified app and sdk.
     *
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the skd == empty string.
     * @return the Topics Response.
     */
    @NonNull
    public GetTopicsResult getTopics(@NonNull String app, @NonNull String sdk) {
        sLogger.v("TopicsWorker.getTopics for %s, %s", app, sdk);

        // We will generally handle the App and SDK topics assignment through
        // PackageChangedReceiver. However, this is to catch the case we miss the broadcast.
        handleSdkTopicsAssignment(app, sdk);

        mReadWriteLock.readLock().lock();
        try {
            List<CombinedTopic> combinedTopics =
                    mCacheManager.getTopics(
                            mFlags.getTopicsNumberOfLookBackEpochs(),
                            mEpochManager.getCurrentEpochId(),
                            app,
                            sdk);

            List<Long> taxonomyVersions = new ArrayList<>(combinedTopics.size());
            List<Long> modelVersions = new ArrayList<>(combinedTopics.size());
            List<Integer> topicIds = new ArrayList<>(combinedTopics.size());
            List<byte[]> encryptedTopics = new ArrayList<>(combinedTopics.size());
            List<String> keyIdentifiers = new ArrayList<>(combinedTopics.size());
            List<byte[]> encapsulatedKeys = new ArrayList<>(combinedTopics.size());

            for (CombinedTopic combinedTopic : combinedTopics) {
                if (!mFlags.getTopicsDisablePlaintextResponse()) {
                    // Set plaintext unencrypted topics only when flag is false.
                    taxonomyVersions.add(combinedTopic.getTopic().getTaxonomyVersion());
                    modelVersions.add(combinedTopic.getTopic().getModelVersion());
                    topicIds.add(combinedTopic.getTopic().getTopic());
                }

                if (!combinedTopic
                        .getEncryptedTopic()
                        .equals(EncryptedTopic.getDefaultInstance())) {
                    encryptedTopics.add(combinedTopic.getEncryptedTopic().getEncryptedTopic());
                    keyIdentifiers.add(combinedTopic.getEncryptedTopic().getKeyIdentifier());
                    encapsulatedKeys.add(combinedTopic.getEncryptedTopic().getEncapsulatedKey());
                }
            }

            GetTopicsResult result =
                    new GetTopicsResult.Builder()
                            .setResultCode(RESULT_OK)
                            .setTaxonomyVersions(taxonomyVersions)
                            .setModelVersions(modelVersions)
                            .setTopics(topicIds)
                            .setEncryptedTopics(encryptedTopics)
                            .setEncryptionKeys(keyIdentifiers)
                            .setEncapsulatedKeys(encapsulatedKeys)
                            .build();
            sLogger.v(
                    "The result of TopicsWorker.getTopics for %s, %s is %s",
                    app, sdk, result.toString());
            return result;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Record the call from App and Sdk to usage history. This UsageHistory will be used to
     * determine if a caller (app or sdk) has observed a topic before.
     *
     * @param app the app
     * @param sdk the sdk of the app. In case the app calls the Topics API directly, the sdk ==
     *     empty string.
     */
    @NonNull
    public void recordUsage(@NonNull String app, @NonNull String sdk) {
        mReadWriteLock.readLock().lock();
        try {
            mEpochManager.recordUsageHistory(app, sdk);
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /** Load the Topics Cache from DB. */
    @NonNull
    public void loadCache() {
        // This loadCache happens when the TopicsService is created. The Cache is empty at that
        // time. Since the load happens async, clients can call getTopics API during the cache load.
        // Here we use Write lock to block Read during that loading time.
        mReadWriteLock.writeLock().lock();
        try {
            mCacheManager.loadCache(mEpochManager.getCurrentEpochId());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Compute Epoch algorithm. If the computation succeed, it will reload the cache. */
    @NonNull
    public void computeEpoch() {
        // This computeEpoch happens in the EpochJobService which happens every epoch. Since the
        // epoch computation happens async, clients can call getTopics API during the epoch
        // computation. Here we use Write lock to block Read during that computation time.
        mReadWriteLock.writeLock().lock();
        try {
            mEpochManager.processEpoch();

            // TODO(b/227179955): Handle error in mEpochManager.processEpoch and only reload Cache
            // when the computation succeeded.
            loadCache();
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Delete all data generated by Topics API, except for tables in the exclusion list.
     *
     * @param tablesToExclude an {@link ArrayList} of tables that won't be deleted.
     */
    public void clearAllTopicsData(@NonNull ArrayList<String> tablesToExclude) {
        // Here we use Write lock to block Read during that computation time.
        mReadWriteLock.writeLock().lock();
        try {
            // Do not clear encrypted topics table if the v9 db flag has not been ramped up.
            if (!mFlags.getEnableDatabaseSchemaVersion9()) {
                tablesToExclude.add(TopicsTables.ReturnedEncryptedTopicContract.TABLE);
            }
            mCacheManager.clearAllTopicsData(tablesToExclude);

            // If clearing all Topics data, clear preserved blocked topics in system server.
            if (!tablesToExclude.contains(TopicsTables.BlockedTopicsContract.TABLE)) {
                mBlockedTopicsManager.clearAllBlockedTopics();
            }

            loadCache();
            sLogger.v(
                    "All derived data are cleaned for Topics API except: %s",
                    tablesToExclude.toString());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Reconcile unhandled app update in real-time service.
     *
     * <p>Uninstallation: Wipe out data in all tables for an uninstalled application with data still
     * persisted in database.
     *
     * <p>Installation: Assign a random top topic from last 3 epochs to app only.
     *
     * @param context the context
     */
    public void reconcileApplicationUpdate(Context context) {
        mReadWriteLock.writeLock().lock();
        try {
            mAppUpdateManager.reconcileUninstalledApps(context, mEpochManager.getCurrentEpochId());
            mAppUpdateManager.reconcileInstalledApps(context, mEpochManager.getCurrentEpochId());

            loadCache();
        } finally {
            mReadWriteLock.writeLock().unlock();
            sLogger.d("App Update Reconciliation is done!");
        }
    }

    /**
     * Handle application uninstallation for Topics API.
     *
     * @param packageUri The {@link Uri} got from Broadcast Intent
     */
    public void handleAppUninstallation(@NonNull Uri packageUri) {
        mReadWriteLock.writeLock().lock();
        try {
            mAppUpdateManager.handleAppUninstallationInRealTime(
                    packageUri, mEpochManager.getCurrentEpochId());

            loadCache();
            sLogger.v("Derived data is cleared for %s", packageUri.toString());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Handle application installation for Topics API
     *
     * @param packageUri The {@link Uri} got from Broadcast Intent
     */
    public void handleAppInstallation(@NonNull Uri packageUri) {
        mReadWriteLock.writeLock().lock();
        try {
            mAppUpdateManager.handleAppInstallationInRealTime(
                    packageUri, mEpochManager.getCurrentEpochId());

            loadCache();
            sLogger.v(
                    "Topics have been assigned to newly installed %s and cache" + "is reloaded",
                    packageUri);
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    // Handle topic assignment to SDK for newly installed applications. Cached topics need to be
    // reloaded if any topic assignment happens.
    private void handleSdkTopicsAssignment(@NonNull String app, @NonNull String sdk) {
        // Return if any topic has been assigned to this app-sdk.
        List<Topic> existingTopics = getExistingTopicsForAppSdk(app, sdk);
        if (!existingTopics.isEmpty()) {
            return;
        }

        mReadWriteLock.writeLock().lock();
        try {
            if (mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                    app, sdk, mEpochManager.getCurrentEpochId())) {
                loadCache();
                sLogger.v(
                        "Topics have been assigned to sdk %s as app %s is newly installed in"
                                + " current epoch",
                        sdk, app);
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    // Get all existing topics from cache for a pair of app and sdk.
    // The epoch range is [currentEpochId - numberOfLookBackEpochs, currentEpochId].
    @NonNull
    private List<Topic> getExistingTopicsForAppSdk(@NonNull String app, @NonNull String sdk) {
        List<Topic> existingTopics;

        mReadWriteLock.readLock().lock();
        // Get existing returned topics map for last 3 epochs and current epoch.
        try {
            long currentEpochId = mEpochManager.getCurrentEpochId();
            existingTopics =
                    mCacheManager.getTopicsInEpochRange(
                            currentEpochId - mFlags.getTopicsNumberOfLookBackEpochs(),
                            currentEpochId,
                            app,
                            sdk);
        } finally {
            mReadWriteLock.readLock().unlock();
        }

        return existingTopics == null ? new ArrayList<>() : existingTopics;
    }
}
