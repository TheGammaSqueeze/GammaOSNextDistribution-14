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

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.topics.classifier.Classifier;
import com.android.adservices.service.topics.classifier.ClassifierManager;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** A class to manage Epoch computation. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class EpochManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    // The tables to do garbage collection for old epochs
    // and its corresponding epoch_id column name.
    // Pair<Table Name, Column Name>
    private static final Pair<String, String>[] TABLE_INFO_FOR_EPOCH_GARBAGE_COLLECTION =
            new Pair[] {
                Pair.create(
                        TopicsTables.AppClassificationTopicsContract.TABLE,
                        TopicsTables.AppClassificationTopicsContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.TopTopicsContract.TABLE,
                        TopicsTables.TopTopicsContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.UsageHistoryContract.TABLE,
                        TopicsTables.UsageHistoryContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.AppUsageHistoryContract.TABLE,
                        TopicsTables.AppUsageHistoryContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.TopicContributorsContract.TABLE,
                        TopicsTables.TopicContributorsContract.EPOCH_ID)
            };

    /**
     * The string to annotate that the topic is a padded topic in {@code TopicContributors} table.
     * After the computation of {@code TopicContributors} table, if there is a top topic without
     * contributors, it must be a padded topic. Persist {@code Entry{Topic,
     * PADDED_TOP_TOPICS_STRING}} into {@code TopicContributors} table.
     *
     * <p>The reason to persist {@code Entry{Topic, PADDED_TOP_TOPICS_STRING}} is because topics
     * need to be assigned to newly installed app. Moreover, non-random top topics without
     * contributors, due to app uninstallations, are filtered out as candidate topics to assign
     * with. Generally, a padded topic should have no contributors, but it should NOT be filtered
     * out as a non-random top topics without contributors. Based on these facts, {@code
     * Entry{Topic, PADDED_TOP_TOPICS_STRING}} is persisted to annotate that do NOT remove this
     * padded topic though it has no contributors.
     *
     * <p>Put a "!" at last to avoid a spoof app to name itself with {@code
     * PADDED_TOP_TOPICS_STRING}. Refer to
     * https://developer.android.com/studio/build/configure-app-module, application name can only
     * contain [a-zA-Z0-9_].
     */
    @VisibleForTesting
    public static final String PADDED_TOP_TOPICS_STRING = "no_contributors_due_to_padding!";

    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static EpochManager sSingleton;

    private final TopicsDao mTopicsDao;
    private final DbHelper mDbHelper;
    private final Random mRandom;
    private final Classifier mClassifier;
    private final Flags mFlags;
    // Use Clock.getInstance() except in unit tests, which pass in a local instance of Clock to
    // mock.
    private final Clock mClock;
    private final ClassifierManager mClassifierManager;
    private final EncryptionManager mEncryptionManager;

    @VisibleForTesting
    EpochManager(
            @NonNull TopicsDao topicsDao,
            @NonNull DbHelper dbHelper,
            @NonNull Random random,
            @NonNull Classifier classifier,
            Flags flags,
            @NonNull Clock clock,
            @NonNull ClassifierManager classifierManager,
            @NonNull EncryptionManager encryptionManager) {
        mTopicsDao = topicsDao;
        mDbHelper = dbHelper;
        mRandom = random;
        mClassifier = classifier;
        mFlags = flags;
        mClock = clock;
        mClassifierManager = classifierManager;
        mEncryptionManager = encryptionManager;
    }

    /** Returns an instance of the EpochManager given a context. */
    @NonNull
    public static EpochManager getInstance(@NonNull Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new EpochManager(
                                TopicsDao.getInstance(context),
                                DbHelper.getInstance(context),
                                new Random(),
                                ClassifierManager.getInstance(context),
                                FlagsFactory.getFlags(),
                                Clock.getInstance(),
                                ClassifierManager.getInstance(context),
                                EncryptionManager.getInstance(context));
            }
            return sSingleton;
        }
    }

    /** Offline Epoch Processing. For more details, see go/rb-topics-epoch-computation */
    public void processEpoch() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // This cross db and java boundaries multiple times, so we need to have a db transaction.
        sLogger.d("Start of Epoch Computation");
        db.beginTransaction();

        long currentEpochId = getCurrentEpochId();
        sLogger.d("EpochManager.processEpoch for the current epochId %d", currentEpochId);

        try {
            // Step 0: erase outdated epoch's data, i.e. epoch earlier than
            // (currentEpoch - numberOfLookBackEpochs) (inclusive)
            garbageCollectOutdatedEpochData(currentEpochId);

            // Step 1: Compute the UsageMap from the UsageHistory table.
            // appSdksUsageMap = Map<App, List<SDK>> has the app and its SDKs that called Topics API
            // in the current Epoch.
            Map<String, List<String>> appSdksUsageMap =
                    mTopicsDao.retrieveAppSdksUsageMap(currentEpochId);
            sLogger.v("appSdksUsageMap size is  %d", appSdksUsageMap.size());

            // Step 2: Compute the Map from App to its classification topics.
            // Only produce for apps that called the Topics API in the current Epoch.
            // appClassificationTopicsMap = Map<App, List<Topics>>
            Map<String, List<Topic>> appClassificationTopicsMap =
                    mClassifier.classify(appSdksUsageMap.keySet());
            sLogger.v("appClassificationTopicsMap size is %d", appClassificationTopicsMap.size());

            // Then save app-topics Map into DB
            mTopicsDao.persistAppClassificationTopics(currentEpochId, appClassificationTopicsMap);

            // Step 3: Compute the Callers can learn map for this epoch.
            // This is similar to the Callers Can Learn table in the explainer.
            Map<Topic, Set<String>> callersCanLearnThisEpochMap =
                    computeCallersCanLearnMap(appSdksUsageMap, appClassificationTopicsMap);
            sLogger.v(
                    "callersCanLearnThisEpochMap size is  %d", callersCanLearnThisEpochMap.size());

            // And then save this CallersCanLearnMap to DB.
            mTopicsDao.persistCallerCanLearnTopics(currentEpochId, callersCanLearnThisEpochMap);

            // Step 4: For each topic, retrieve the callers (App or SDK) that can learn about that
            // topic. We look at last 3 epochs. More specifically, epochs in
            // [currentEpochId - 2, currentEpochId]. (inclusive)
            // Return callersCanLearnMap = Map<Topic, Set<Caller>>  where Caller = App or Sdk.
            Map<Topic, Set<String>> callersCanLearnMap =
                    mTopicsDao.retrieveCallerCanLearnTopicsMap(
                            currentEpochId, mFlags.getTopicsNumberOfLookBackEpochs());
            sLogger.v("callersCanLearnMap size is %d", callersCanLearnMap.size());

            // Step 5: Retrieve the Top Topics. This will return a list of 5 top topics and
            // the 6th topic which is selected randomly. We can refer this 6th topic as the
            // random-topic.
            List<Topic> topTopics =
                    mClassifier.getTopTopics(
                            appClassificationTopicsMap,
                            mFlags.getTopicsNumberOfTopTopics(),
                            mFlags.getTopicsNumberOfRandomTopics());
            // Abort the computation if empty list of top topics is returned from classifier.
            // This could happen if there is no usage of the Topics API in the last epoch.
            if (topTopics.isEmpty()) {
                sLogger.w(
                        "Empty list of top topics is returned from classifier. Aborting the"
                                + " computation!");
                db.setTransactionSuccessful();
                return;
            }
            sLogger.v("topTopics are  %s", topTopics.toString());

            // Then save Top Topics into DB
            mTopicsDao.persistTopTopics(currentEpochId, topTopics);

            // Compute TopicToContributors mapping for top topics. In an epoch, an app is a
            // contributor to a topic if the app has called Topics API in this epoch and is
            // classified to the topic.
            // Do this only when feature is enabled.
            Map<Integer, Set<String>> topTopicsToContributorsMap =
                    computeTopTopicsToContributorsMap(appClassificationTopicsMap, topTopics);
            // Then save Topic Contributors into DB
            mTopicsDao.persistTopicContributors(currentEpochId, topTopicsToContributorsMap);

            // Step 6: Assign topics to apps and SDK from the global top topics.
            // Currently, hard-code the taxonomyVersion and the modelVersion.
            // Return returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
            Map<Pair<String, String>, Topic> returnedAppSdkTopics =
                    computeReturnedAppSdkTopics(callersCanLearnMap, appSdksUsageMap, topTopics);
            sLogger.v("returnedAppSdkTopics size is  %d", returnedAppSdkTopics.size());

            // And persist the map to DB so that we can reuse later.
            mTopicsDao.persistReturnedAppTopicsMap(currentEpochId, returnedAppSdkTopics);

            // Encrypt and store encrypted topics if the feature is enabled and the version 9 db
            // is available.
            if (mFlags.getTopicsEncryptionEnabled() && mFlags.getEnableDatabaseSchemaVersion9()) {
                // encryptedTopicMapTopics = Map<Pair<App, Sdk>, EncryptedTopic>
                Map<Pair<String, String>, EncryptedTopic> encryptedTopicMapTopics =
                        encryptTopicsMap(returnedAppSdkTopics);
                sLogger.v("encryptedTopicMapTopics size is  %d", returnedAppSdkTopics.size());

                mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                        currentEpochId, encryptedTopicMapTopics);
            }

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            sLogger.d("End of Epoch Computation");
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
    public void recordUsageHistory(String app, String sdk) {
        long epochId = getCurrentEpochId();
        // TODO(b/223159123): Do we need to filter out this log in prod build?
        sLogger.v(
                "EpochManager.recordUsageHistory for current EpochId = %d for %s, %s",
                epochId, app, sdk);
        mTopicsDao.recordUsageHistory(epochId, app, sdk);
        mTopicsDao.recordAppUsageHistory(epochId, app);
    }

    /**
     * Determine the learn-ability of a topic to a certain caller.
     *
     * @param topic the topic to check the learn-ability
     * @param caller the caller to check whether it can learn the given topic
     * @param callersCanLearnMap the map that stores topic->caller mapping which shows a topic can
     *     be learnt by a caller
     * @param topTopics a {@link List} of top topics
     * @param numberOfTopTopics number of regular topics in top topics
     * @return a {@code boolean} that indicates if the caller can learn the topic
     */
    // TODO(b/236834213): Create a class for Top Topics
    public static boolean isTopicLearnableByCaller(
            @NonNull Topic topic,
            @NonNull String caller,
            @NonNull Map<Topic, Set<String>> callersCanLearnMap,
            @NonNull List<Topic> topTopics,
            int numberOfTopTopics) {
        // If a topic is the random topic in top topic list, it can be learnt by any caller.
        int index = topTopics.lastIndexOf(topic);
        // Regular top topics are placed in the front of the list. Topics after are random topics.
        if (index >= numberOfTopTopics) {
            return true;
        }

        return callersCanLearnMap.containsKey(topic)
                && callersCanLearnMap.get(topic).contains(caller);
    }

    /**
     * Get the ID of current epoch.
     *
     * <p>The origin's timestamp is saved in the database. If the origin doesn't exist, it means the
     * user never calls Topics API and the origin will be returned with -1. In this case, set
     * current time as origin and persist it into database.
     *
     * @return a non-negative epoch ID of current epoch.
     */
    // TODO(b/237119788): Cache origin in cache manager.
    // TODO(b/237119790): Set origin to sometime after midnight to get better maintenance timing.
    public long getCurrentEpochId() {
        long origin = mTopicsDao.retrieveEpochOrigin();
        long currentTimeStamp = mClock.currentTimeMillis();
        long epochJobPeriodsMs = mFlags.getTopicsEpochJobPeriodMs();

        // If origin doesn't exist in database, set current timestamp as origin.
        if (origin == -1) {
            origin = currentTimeStamp;
            mTopicsDao.persistEpochOrigin(origin);
            sLogger.d(
                    "Origin isn't found! Set current time %s as origin.",
                    Instant.ofEpochMilli(origin).toString());
        }

        sLogger.v("Epoch length is  %d", epochJobPeriodsMs);
        return (long) Math.floor((currentTimeStamp - origin) / (double) epochJobPeriodsMs);
    }

    // Return a Map from Topic to set of App or Sdk that can learn about that topic.
    // This is similar to the table Can Learn Topic in the explainer.
    // Return Map<Topic, Set<Caller>>  where Caller = App or Sdk.
    @VisibleForTesting
    @NonNull
    static Map<Topic, Set<String>> computeCallersCanLearnMap(
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull Map<String, List<Topic>> appClassificationTopicsMap) {
        Objects.requireNonNull(appSdksUsageMap);
        Objects.requireNonNull(appClassificationTopicsMap);

        // Map from Topic to set of App or Sdk that can learn about that topic.
        // This is similar to the table Can Learn Topic in the explainer.
        // Map<Topic, Set<Caller>>  where Caller = App or Sdk.
        Map<Topic, Set<String>> callersCanLearnMap = new HashMap<>();

        for (Map.Entry<String, List<Topic>> entry : appClassificationTopicsMap.entrySet()) {
            String app = entry.getKey();
            List<Topic> appTopics = entry.getValue();
            if (appTopics == null) {
                sLogger.e("Can't find the Classification Topics for app = " + app);
                continue;
            }

            for (Topic topic : appTopics) {
                if (!callersCanLearnMap.containsKey(topic)) {
                    callersCanLearnMap.put(topic, new HashSet<>());
                }

                // All SDKs in the app can learn this topic too.
                for (String sdk : appSdksUsageMap.get(app)) {
                    if (TextUtils.isEmpty(sdk)) {
                        // Empty sdk means the app called the Topics API directly.
                        // Caller = app
                        // Then the app can learn its topic.
                        callersCanLearnMap.get(topic).add(app);
                    } else {
                        // Caller = sdk
                        callersCanLearnMap.get(topic).add(sdk);
                    }
                }
            }
        }

        return callersCanLearnMap;
    }

    // Encrypts Topic to corresponding EncryptedTopic.
    // Map<Pair<App, Sdk>, EncryptedTopic>
    private Map<Pair<String, String>, EncryptedTopic> encryptTopicsMap(
            Map<Pair<String, String>, Topic> unencryptedMap) {
        Map<Pair<String, String>, EncryptedTopic> encryptedTopicMap = new HashMap<>();
        for (Map.Entry<Pair<String, String>, Topic> entry : unencryptedMap.entrySet()) {
            Optional<EncryptedTopic> optionalEncryptedTopic =
                    mEncryptionManager.encryptTopic(
                            /* Topic */ entry.getValue(), /* sdkName */ entry.getKey().second);
            if (optionalEncryptedTopic.isPresent()) {
                encryptedTopicMap.put(entry.getKey(), optionalEncryptedTopic.get());
            } else {
                ErrorLogUtil.e(
                        AdServicesStatsLog
                                .AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_FAILURE,
                        AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                sLogger.d(
                        "Failed to encrypt %s for (%s, %s) caller.",
                        entry.getValue(), entry.getKey().first, entry.getKey().second);
            }
        }
        return encryptedTopicMap;
    }

    // Inputs:
    // callersCanLearnMap = Map<Topic, Set<Caller>> map from topic to set of callers that can learn
    // about the topic. Caller = App or Sdk.
    // appSdksUsageMap = Map<App, List<SDK>> has the app and its SDKs that called Topics API
    // in the current Epoch.
    // topTopics = List<Topic> list of top 5 topics and 1 random topic.
    //
    // Return returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
    @VisibleForTesting
    @NonNull
    Map<Pair<String, String>, Topic> computeReturnedAppSdkTopics(
            @NonNull Map<Topic, Set<String>> callersCanLearnMap,
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull List<Topic> topTopics) {
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();

        for (Map.Entry<String, List<String>> appSdks : appSdksUsageMap.entrySet()) {
            Topic returnedTopic = selectRandomTopic(topTopics);
            if(mFlags.getEnableLoggedTopic()
                    && mTopicsDao.supportsLoggedTopicInReturnedTopicTable()) {
                returnedTopic =
                        Topic.create(
                                returnedTopic.getTopic(),
                                returnedTopic.getTaxonomyVersion(),
                                returnedTopic.getModelVersion(),
                                getTopicIdForLogging(returnedTopic));
            }

            String app = appSdks.getKey();

            // Check if the app can learn this topic.
            if (isTopicLearnableByCaller(
                    returnedTopic,
                    app,
                    callersCanLearnMap,
                    topTopics,
                    mFlags.getTopicsNumberOfTopTopics())) {
                // The app calls Topics API directly. In this case, we set the sdk == empty string.
                returnedAppSdkTopics.put(Pair.create(app, /* empty Sdk */ ""), returnedTopic);
                // TODO(b/223159123): Do we need to filter out this log in prod build?
                sLogger.v(
                        "CacheManager.computeReturnedAppSdkTopics. Topic %s is returned for"
                                + " %s",
                        returnedTopic, app);
            }

            // Then check all SDKs of the app.
            for (String sdk : appSdks.getValue()) {
                if (isTopicLearnableByCaller(
                        returnedTopic,
                        sdk,
                        callersCanLearnMap,
                        topTopics,
                        mFlags.getTopicsNumberOfTopTopics())) {
                    returnedAppSdkTopics.put(Pair.create(app, sdk), returnedTopic);
                    // TODO(b/223159123): Do we need to filter out this log in prod build?
                    sLogger.v(
                            "CacheManager.computeReturnedAppSdkTopics. Topic %s is returned"
                                    + " for %s, %s",
                            returnedTopic, app, sdk);
                }
            }
        }

        return returnedAppSdkTopics;
    }

    // Return a random topics from the Top Topics.
    // The Top Topics include the Top 5 Topics and one random topic from the Taxonomy.
    @VisibleForTesting
    @NonNull
    Topic selectRandomTopic(List<Topic> topTopics) {
        Preconditions.checkArgument(
                topTopics.size()
                        == mFlags.getTopicsNumberOfTopTopics()
                                + mFlags.getTopicsNumberOfRandomTopics());
        int random = mRandom.nextInt(100);

        // First random topic would be after numberOfTopTopics.
        int randomTopicIndex = mFlags.getTopicsNumberOfTopTopics();
        // For 5%, get the random topic.
        if (random < mFlags.getTopicsPercentageForRandomTopic()) {
            // The random topic is the last one on the list.
            return topTopics.get(randomTopicIndex);
        }

        // For 95%, pick randomly one out of first n top topics.
        return topTopics.get(random % randomTopicIndex);
    }

    // To garbage collect data for old epochs.
    @VisibleForTesting
    void garbageCollectOutdatedEpochData(long currentEpochID) {
        int epochLookBackNumberForGarbageCollection = mFlags.getNumberOfEpochsToKeepInHistory();
        // Assume current Epoch is T, and the earliest epoch should be kept is T-3
        // Then any epoch data older than T-3-1 = T-4, including T-4 should be deleted.
        long epochToDeleteFrom = currentEpochID - epochLookBackNumberForGarbageCollection - 1;
        // To do garbage collection for each table
        for (Pair<String, String> tableColumnPair : TABLE_INFO_FOR_EPOCH_GARBAGE_COLLECTION) {
            mTopicsDao.deleteDataOfOldEpochs(
                    tableColumnPair.first, tableColumnPair.second, epochToDeleteFrom);
        }

        if (mFlags.getEnableDatabaseSchemaVersion9()) {
            // Delete data from ReturnedEncryptedTopic table.
            mTopicsDao.deleteDataOfOldEpochs(
                    TopicsTables.ReturnedEncryptedTopicContract.TABLE,
                    TopicsTables.ReturnedEncryptedTopicContract.EPOCH_ID,
                    epochToDeleteFrom);
        }

        // In app installation, we need to assign topics to newly installed app-sdk caller. In order
        // to check topic learnability of the sdk, CallerCanLearnTopicsContract needs to persist
        // numberOfLookBackEpochs more epochs. For example, assume current epoch is T. In app
        // installation, topics will be assigned to T-1, T-2 and T-3. In order to check learnability
        // at Epoch T-3, we need to check CallerCanLearnTopicsContract of epoch T-4, T-5 and T-6.
        long epochToDeleteFromForCallerCanLearn =
                currentEpochID - epochLookBackNumberForGarbageCollection * 2L - 1;
        mTopicsDao.deleteDataOfOldEpochs(
                TopicsTables.CallerCanLearnTopicsContract.TABLE,
                TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID,
                epochToDeleteFromForCallerCanLearn);
    }

    // Compute the mapping of topic to its contributor apps. In an epoch, an app is a contributor to
    // a topic if the app has called Topics API in this epoch and is classified to the topic. Only
    // computed for top topics.
    @VisibleForTesting
    Map<Integer, Set<String>> computeTopTopicsToContributorsMap(
            @NonNull Map<String, List<Topic>> appClassificationTopicsMap,
            @NonNull List<Topic> topTopics) {
        Map<Integer, Set<String>> topicToContributorMap = new HashMap<>();

        for (Map.Entry<String, List<Topic>> appTopics : appClassificationTopicsMap.entrySet()) {
            String app = appTopics.getKey();

            for (Topic topic : appTopics.getValue()) {
                // Only compute for top topics.
                if (topTopics.contains(topic)) {
                    int topicId = topic.getTopic();
                    topicToContributorMap.putIfAbsent(topicId, new HashSet<>());
                    topicToContributorMap.get(topicId).add(app);
                }
            }
        }

        // At last, check whether there is any top topics without contributors. If so, annotate it
        // with PADDED_TOP_TOPICS_STRING in the map. See PADDED_TOP_TOPICS_STRING for more details.
        for (int i = 0; i < mFlags.getTopicsNumberOfTopTopics(); i++) {
            Topic topTopic = topTopics.get(i);
            topicToContributorMap.putIfAbsent(
                    topTopic.getTopic(), Set.of(PADDED_TOP_TOPICS_STRING));
        }

        return topicToContributorMap;
    }

    public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
        writer.println("==== EpochManager Dump ====");
        long epochId = getCurrentEpochId();
        writer.println(String.format("Current epochId is %d", epochId));
    }

    // Gets a topic ID for logging from the given topic using randomized response mechanism.
    @VisibleForTesting
    int getTopicIdForLogging(Topic topic) {
        List<Integer> topicsTaxonomy = mClassifierManager.getTopicsTaxonomy();

        // Probability of logging real vs. random topic:
        // Real topic: e^privacyBudget / (e^privacyBudget + taxonomySize - 1)
        // Random topic: (taxonomySize - 1) / (e^privacyBudget + taxonomySize - 1)
        double expPrivacyBudget = Math.exp(mFlags.getTopicsPrivacyBudgetForTopicIdDistribution());
        int taxonomySize = topicsTaxonomy.size();
        double pRandomization =
                expPrivacyBudget == Double.POSITIVE_INFINITY
                        ? 0d
                        : (taxonomySize - 1d) / (expPrivacyBudget + taxonomySize - 1d);

        int topicId = topic.getTopic();

        // In order to prevent select a random topic from being stuck in a loop,
        // return the topic directly if taxonomy size is 0 or 1.
        if (taxonomySize <= 1) {
            return topicId;
        }

        if (mRandom.nextDouble() < pRandomization) {
            // Log a random topic ID other than the real one.
            int randomTopicId = topicId;

            // Set a maximum attempts to prevent select a random topic from being stuck in the loop.
            int maxAttempts = 5;

            while (randomTopicId == topicId && maxAttempts-- > 0) {
                randomTopicId = topicsTaxonomy.get(mRandom.nextInt(taxonomySize));
            }

            return randomTopicId;
        } else {
            return topicId;
        }
    }
}
