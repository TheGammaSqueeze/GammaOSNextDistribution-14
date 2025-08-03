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
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to manage application update flow in Topics API.
 *
 * <p>It contains methods to handle app installation and uninstallation. App update will either be
 * regarded as the combination of app installation and uninstallation, or be handled in the next
 * epoch.
 *
 * <p>See go/rb-topics-app-update for details.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppUpdateManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final String EMPTY_SDK = "";
    private static AppUpdateManager sSingleton;

    // Tables that needs to be wiped out for application data
    // and its corresponding app column name.
    // Pair<Table Name, app Column Name>
    private static final Pair<String, String>[] TABLE_INFO_TO_ERASE_APP_DATA =
            new Pair[] {
                Pair.create(
                        TopicsTables.AppClassificationTopicsContract.TABLE,
                        TopicsTables.AppClassificationTopicsContract.APP),
                Pair.create(
                        TopicsTables.CallerCanLearnTopicsContract.TABLE,
                        TopicsTables.CallerCanLearnTopicsContract.CALLER),
                Pair.create(
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.APP),
                Pair.create(
                        TopicsTables.UsageHistoryContract.TABLE,
                        TopicsTables.UsageHistoryContract.APP),
                Pair.create(
                        TopicsTables.AppUsageHistoryContract.TABLE,
                        TopicsTables.AppUsageHistoryContract.APP),
                Pair.create(
                        TopicsTables.TopicContributorsContract.TABLE,
                        TopicsTables.TopicContributorsContract.APP)
            };

    private final DbHelper mDbHelper;
    private final TopicsDao mTopicsDao;
    private final Random mRandom;
    private final Flags mFlags;

    AppUpdateManager(
            @NonNull DbHelper dbHelper,
            @NonNull TopicsDao topicsDao,
            @NonNull Random random,
            @NonNull Flags flags) {
        mDbHelper = dbHelper;
        mTopicsDao = topicsDao;
        mRandom = random;
        mFlags = flags;
    }

    /**
     * Returns an instance of AppUpdateManager given a context
     *
     * @param context the context
     * @return an instance of AppUpdateManager
     */
    @NonNull
    public static AppUpdateManager getInstance(@NonNull Context context) {
        synchronized (AppUpdateManager.class) {
            if (sSingleton == null) {
                sSingleton =
                        new AppUpdateManager(
                                DbHelper.getInstance(context),
                                TopicsDao.getInstance(context),
                                new Random(),
                                FlagsFactory.getFlags());
            }
        }

        return sSingleton;
    }

    /**
     * Handle application uninstallation for Topics API.
     *
     * <ul>
     *   <li>Delete all derived data for an uninstalled app.
     *   <li>When the feature is enabled, remove a topic if it has the uninstalled app as the only
     *       contributor in an epoch.
     * </ul>
     *
     * @param packageUri The {@link Uri} got from Broadcast Intent
     * @param currentEpochId the epoch id of current Epoch
     */
    public void handleAppUninstallationInRealTime(@NonNull Uri packageUri, long currentEpochId) {
        String packageName = convertUriToAppName(packageUri);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            sLogger.e(
                    "Database is not available, Stop processing app uninstallation for %s!",
                    packageName);
            return;
        }

        // This cross db and java boundaries multiple times, so we need to have a db transaction.
        db.beginTransaction();

        try {
            handleTopTopicsWithoutContributors(currentEpochId, packageName);

            deleteAppDataFromTableByApps(List.of(packageName));

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            sLogger.d("End of processing app uninstallation for %s", packageName);
        }
    }

    /**
     * Handle application installation for Topics API.
     *
     * <p>Assign topics to past epochs for the installed app.
     *
     * @param packageUri The {@link Uri} got from Broadcast Intent
     * @param currentEpochId the epoch id of current Epoch
     */
    public void handleAppInstallationInRealTime(@NonNull Uri packageUri, long currentEpochId) {
        String packageName = convertUriToAppName(packageUri);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            sLogger.e(
                    "Database is not available, Stop processing app installation for %s",
                    packageName);
            return;
        }

        // This cross db and java boundaries multiple times, so we need to have a db transaction.
        db.beginTransaction();

        try {
            assignTopicsToNewlyInstalledApps(packageName, currentEpochId);

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            sLogger.d("End of processing app installation for %s", packageName);
        }
    }

    /**
     * Reconcile any mismatched data for application uninstallation.
     *
     * <p>Uninstallation: Wipe out data in all tables for an uninstalled application with data still
     * persisted in database.
     *
     * <ul>
     *   <li>Step 1: Get currently installed apps from Package Manager.
     *   <li>Step 2: Apps that have either usages or returned topics but are not installed are
     *       regarded as newly uninstalled apps.
     *   <li>Step 3: For each newly uninstalled app, wipe out its data from database.
     * </ul>
     *
     * @param context the context
     * @param currentEpochId epoch ID of current epoch
     */
    public void reconcileUninstalledApps(@NonNull Context context, long currentEpochId) {
        Set<String> currentInstalledApps = getCurrentInstalledApps(context);
        Set<String> unhandledUninstalledApps = getUnhandledUninstalledApps(currentInstalledApps);
        if (unhandledUninstalledApps.isEmpty()) {
            return;
        }

        sLogger.v(
                "Detect below unhandled mismatched applications: %s",
                unhandledUninstalledApps.toString());

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            sLogger.e("Database is not available, Stop reconciling app uninstallation in Topics!");
            return;
        }

        // This cross db and java boundaries multiple times, so we need to have a db transaction.
        db.beginTransaction();

        try {
            handleUninstalledAppsInReconciliation(unhandledUninstalledApps, currentEpochId);

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            sLogger.v("App uninstallation reconciliation in Topics is finished!");
        }
    }

    /**
     * Reconcile any mismatched data for application installation.
     *
     * <p>Installation: Assign a random top topic from last 3 epochs to app only.
     *
     * <ul>
     *   <li>Step 1: Get currently installed apps from Package Manager.
     *   <li>Step 2: Installed apps that don't have neither usages nor returned topics are regarded
     *       as newly installed apps.
     *   <li>Step 3: For each newly installed app, assign a random top topic from last epoch to it
     *       and persist in the database.
     * </ul>
     *
     * @param context the context
     * @param currentEpochId id of current epoch
     */
    public void reconcileInstalledApps(@NonNull Context context, long currentEpochId) {
        Set<String> currentInstalledApps = getCurrentInstalledApps(context);
        Set<String> unhandledInstalledApps = getUnhandledInstalledApps(currentInstalledApps);

        if (unhandledInstalledApps.isEmpty()) {
            return;
        }

        sLogger.v(
                "Detect below unhandled installed applications: %s",
                unhandledInstalledApps.toString());

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            sLogger.e("Database is not available, Stop reconciling app installation in Topics!");
            return;
        }

        // This cross db and java boundaries multiple times, so we need to have a db transaction.
        db.beginTransaction();

        try {
            handleInstalledAppsInReconciliation(unhandledInstalledApps, currentEpochId);

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            sLogger.v("App installation reconciliation in Topics is finished!");
        }
    }

    // TODO(b/256703300): Currently we handled app-sdk topic assignments in serving flow. Move the
    //                    logic back to app installation after we can get all SDKs when an app is
    //                    installed.
    /**
     * For a newly installed app, in case SDKs that this app uses are not known when the app is
     * installed, the returned topic for an SDK can only be assigned when user calls getTopic().
     *
     * <p>If an app calls Topics API via an SDK, and this app has a returned topic while SDK
     * doesn't, assign this topic to the SDK if it can learn this topic from past observable epochs.
     *
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the sdk == empty string.
     * @param currentEpochId the epoch id of current cycle
     * @return A {@link Boolean} that notes whether a topic has been assigned to the sdk, so that
     *     {@link CacheManager} needs to reload the cachedTopics
     */
    public boolean assignTopicsToSdkForAppInstallation(
            @NonNull String app, @NonNull String sdk, long currentEpochId) {
        // Don't do anything if app calls getTopics directly without an SDK.
        if (sdk.isEmpty()) {
            return false;
        }

        int numberOfLookBackEpochs = mFlags.getTopicsNumberOfLookBackEpochs();
        Pair<String, String> appOnlyCaller = Pair.create(app, EMPTY_SDK);
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        // Get ReturnedTopics for past epochs in [currentEpochId - numberOfLookBackEpochs,
        // currentEpochId - 1].
        // TODO(b/237436146): Create an object class for Returned Topics.
        Map<Long, Map<Pair<String, String>, Topic>> pastReturnedTopics =
                mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numberOfLookBackEpochs);
        for (Map<Pair<String, String>, Topic> returnedTopics : pastReturnedTopics.values()) {
            // If the SDK has a returned topic, this implies we have generated returned topics for
            // SDKs already. Exit early.
            if (returnedTopics.containsKey(appSdkCaller)) {
                return false;
            }
        }

        // Track whether a topic is assigned in order to know whether cache needs to be reloaded.
        boolean isAssigned = false;

        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfLookBackEpochs && epochId >= 0;
                epochId--) {
            // Validate for an app-sdk pair, whether it satisfies
            // 1) In current epoch, app as the single caller has a returned topic
            // 2) The sdk can learn this topic from last numberOfLookBackEpochs epochs
            // If so, the same topic should be assigned to the sdk.
            if (pastReturnedTopics.get(epochId) != null
                    && pastReturnedTopics.get(epochId).containsKey(appOnlyCaller)) {
                // This is the top Topic assigned to this app-only caller.
                Topic appReturnedTopic = pastReturnedTopics.get(epochId).get(appOnlyCaller);

                // For this epochId, check whether sdk can learn this topic for past
                // numberOfLookBackEpochs observed epochs, i.e.
                // [epochId - numberOfLookBackEpochs + 1, epochId]
                // pastCallerCanLearnTopicsMap = Map<Topic, Set<Caller>>. Caller = App or Sdk
                Map<Topic, Set<String>> pastCallerCanLearnTopicsMap =
                        mTopicsDao.retrieveCallerCanLearnTopicsMap(epochId, numberOfLookBackEpochs);
                List<Topic> pastTopTopic = mTopicsDao.retrieveTopTopics(epochId);

                if (EpochManager.isTopicLearnableByCaller(
                        appReturnedTopic,
                        sdk,
                        pastCallerCanLearnTopicsMap,
                        pastTopTopic,
                        mFlags.getTopicsNumberOfTopTopics())) {
                    mTopicsDao.persistReturnedAppTopicsMap(
                            epochId, Map.of(appSdkCaller, appReturnedTopic));
                    isAssigned = true;
                }
            }
        }

        return isAssigned;
    }

    /**
     * Generating a random topic from given top topic list
     *
     * @param regularTopics a {@link List} of non-random topics in current epoch, excluding those
     *     which have no contributors
     * @param randomTopics a {@link List} of random top topics
     * @param percentageForRandomTopic the probability to select random object
     * @return a selected {@link Topic} to be assigned to newly installed app. Return null if both
     *     lists are empty.
     */
    @VisibleForTesting
    @Nullable
    Topic selectAssignedTopicFromTopTopics(
            @NonNull List<Topic> regularTopics,
            @NonNull List<Topic> randomTopics,
            int percentageForRandomTopic) {
        // Return null if both lists are empty.
        if (regularTopics.isEmpty() && randomTopics.isEmpty()) {
            return null;
        }

        // If one of the list is empty, select from the other list.
        if (regularTopics.isEmpty()) {
            return randomTopics.get(mRandom.nextInt(randomTopics.size()));
        } else if (randomTopics.isEmpty()) {
            return regularTopics.get(mRandom.nextInt(regularTopics.size()));
        }

        // If both lists are not empty, make a draw to determine whether to pick a random topic.
        // If random number is in [0, randomPercentage - 1], a random topic will be selected.
        boolean shouldSelectRandomTopic = mRandom.nextInt(100) < percentageForRandomTopic;

        return shouldSelectRandomTopic
                ? randomTopics.get(mRandom.nextInt(randomTopics.size()))
                : regularTopics.get(mRandom.nextInt(regularTopics.size()));
    }

    /**
     * Delete application data for a specific application.
     *
     * <p>This method allows other usages besides daily maintenance job, such as real-time data
     * wiping for an app uninstallation.
     *
     * @param apps a {@link List} of applications to wipe data for
     */
    @VisibleForTesting
    void deleteAppDataFromTableByApps(@NonNull List<String> apps) {
        List<Pair<String, String>> tableToEraseData =
                Arrays.stream(TABLE_INFO_TO_ERASE_APP_DATA).collect(Collectors.toList());

        if (mFlags.getEnableDatabaseSchemaVersion9()) {
            // Remove the encrypted topic table as well.
            tableToEraseData.add(
                    Pair.create(
                            TopicsTables.ReturnedEncryptedTopicContract.TABLE,
                            TopicsTables.ReturnedEncryptedTopicContract.APP));
        }

        mTopicsDao.deleteFromTableByColumn(
                /* tableNamesAndColumnNamePairs */ tableToEraseData, /* valuesToDelete */ apps);

        sLogger.v("Have deleted data for application " + apps);
    }

    /**
     * Assign a top Topic for the newly installed app. This allows SDKs in the newly installed app
     * to get the past 3 epochs' topics if they did observe the topic in the past.
     *
     * <p>See more details in go/rb-topics-app-update
     *
     * @param app the app package name of newly installed application
     * @param currentEpochId current epoch id
     */
    @VisibleForTesting
    void assignTopicsToNewlyInstalledApps(@NonNull String app, long currentEpochId) {
        Objects.requireNonNull(app);

        final int numberOfEpochsToAssignTopics = mFlags.getTopicsNumberOfLookBackEpochs();
        final int numberOfTopTopics = mFlags.getTopicsNumberOfTopTopics();
        final int topicsPercentageForRandomTopic = mFlags.getTopicsPercentageForRandomTopic();

        Pair<String, String> appOnlyCaller = Pair.create(app, EMPTY_SDK);

        // For each past epoch, assign a random topic to this newly installed app.
        // The assigned topic should align the probability with rule to generate top topics.
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfEpochsToAssignTopics && epochId >= 0;
                epochId--) {
            List<Topic> topTopics = mTopicsDao.retrieveTopTopics(epochId);

            if (topTopics.isEmpty()) {
                sLogger.v(
                        "Empty top topic list in Epoch %d, do not assign topic to App %s.",
                        epochId, app);
                continue;
            }

            // Regular Topics are placed at the beginning of top topic list.
            List<Topic> regularTopics = topTopics.subList(0, numberOfTopTopics);
            regularTopics = filterRegularTopicsWithoutContributors(regularTopics, epochId);
            List<Topic> randomTopics = topTopics.subList(numberOfTopTopics, topTopics.size());

            Topic assignedTopic =
                    selectAssignedTopicFromTopTopics(
                            regularTopics, randomTopics, topicsPercentageForRandomTopic);

            if (assignedTopic == null) {
                sLogger.v(
                        "No topic is available to assign in Epoch %d, do not assign topic to App"
                                + " %s.",
                        epochId, app);
                continue;
            }

            // Persist this topic to database as returned topic in this epoch
            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, assignedTopic));

            sLogger.v(
                    "Topic %s has been assigned to newly installed App %s in Epoch %d",
                    assignedTopic.getTopic(), app, epochId);
        }
    }

    /**
     * When an app is uninstalled, we need to check whether any of its classified topics has no
     * contributors on epoch basis for past epochs to look back. Note in an epoch, an app is a
     * contributor to a topic if the app has called Topics API in this epoch and is classified to
     * the topic.
     *
     * <p>If such topic exists, remove this topic from ReturnedTopicsTable in the epoch. This method
     * is invoked before {@code deleteAppDataFromTableByApps}, so the uninstalled app will be
     * cleared in TopicContributors Table there.
     *
     * <p>NOTE: We are only interested in the epochs which will be used for getTopics(), i.e. past
     * numberOfLookBackEpochs epochs.
     *
     * @param currentEpochId the id of epoch when the method gets invoked
     * @param uninstalledApp the newly uninstalled app
     */
    @VisibleForTesting
    void handleTopTopicsWithoutContributors(long currentEpochId, @NonNull String uninstalledApp) {
        // This check is on epoch basis for past epochs to look back
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - mFlags.getTopicsNumberOfLookBackEpochs()
                        && epochId >= 0;
                epochId--) {
            Map<String, List<Topic>> appClassificationTopics =
                    mTopicsDao.retrieveAppClassificationTopics(epochId);
            List<Topic> topTopics = mTopicsDao.retrieveTopTopics(epochId);
            Map<Integer, Set<String>> topTopicsToContributorsMap =
                    mTopicsDao.retrieveTopicToContributorsMap(epochId);

            List<Topic> classifiedTopics =
                    appClassificationTopics.getOrDefault(uninstalledApp, new ArrayList<>());
            // Collect all top topics to delete to make only one Db Update
            List<String> topTopicsToDelete =
                    classifiedTopics.stream()
                            .filter(
                                    classifiedTopic ->
                                            topTopics.contains(classifiedTopic)
                                                    && topTopicsToContributorsMap.containsKey(
                                                            classifiedTopic.getTopic())
                                                    // Filter out the topic that has ONLY
                                                    // the uninstalled app as a contributor
                                                    && topTopicsToContributorsMap
                                                                    .get(classifiedTopic.getTopic())
                                                                    .size()
                                                            == 1
                                                    && topTopicsToContributorsMap
                                                            .get(classifiedTopic.getTopic())
                                                            .contains(uninstalledApp))
                            .map(Topic::getTopic)
                            .map(String::valueOf)
                            .collect(Collectors.toList());

            if (!topTopicsToDelete.isEmpty()) {
                sLogger.v(
                        "Topics %s will not have contributors at epoch %d. Delete them in"
                                + " epoch %d",
                        topTopicsToDelete, epochId, epochId);
            }

            mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                    List.of(
                            Pair.create(
                                    TopicsTables.ReturnedTopicContract.TABLE,
                                    TopicsTables.ReturnedTopicContract.TOPIC)),
                    topTopicsToDelete,
                    TopicsTables.ReturnedTopicContract.EPOCH_ID,
                    String.valueOf(epochId),
                    /* isStringEqualConditionColumnValue */ false);
        }
    }

    /**
     * Filter out regular topics without any contributors. Note in an epoch, an app is a contributor
     * to a topic if the app has called Topics API in this epoch and is classified to the topic.
     *
     * <p>For padded Topics (Classifier randomly pads top topics if they are not enough), as we put
     * {@link EpochManager#PADDED_TOP_TOPICS_STRING} into TopicContributors Map, padded topics
     * actually have "contributor" PADDED_TOP_TOPICS_STRING. Therefore, they won't be filtered out.
     *
     * @param regularTopics non-random top topics
     * @param epochId epochId of current epoch
     * @return the filtered regular topics
     */
    @NonNull
    @VisibleForTesting
    List<Topic> filterRegularTopicsWithoutContributors(
            @NonNull List<Topic> regularTopics, long epochId) {
        Map<Integer, Set<String>> topicToContributorMap =
                mTopicsDao.retrieveTopicToContributorsMap(epochId);
        return regularTopics.stream()
                .filter(
                        regularTopic ->
                                topicToContributorMap.containsKey(regularTopic.getTopic())
                                        && !topicToContributorMap
                                                .get(regularTopic.getTopic())
                                                .isEmpty())
                .collect(Collectors.toList());
    }

    // An app will be regarded as an unhandled uninstalled app if it has an entry in any epoch of
    // either usage table or returned topics table, but the app doesn't show up in package manager.
    //
    // This will be used in reconciliation process. See details in go/rb-topics-app-update.
    @NonNull
    @VisibleForTesting
    Set<String> getUnhandledUninstalledApps(@NonNull Set<String> currentInstalledApps) {
        Set<String> appsWithUsage =
                mTopicsDao.retrieveDistinctAppsFromTables(
                        List.of(TopicsTables.AppUsageHistoryContract.TABLE),
                        List.of(TopicsTables.AppUsageHistoryContract.APP));
        Set<String> appsWithReturnedTopics =
                mTopicsDao.retrieveDistinctAppsFromTables(
                        List.of(TopicsTables.ReturnedTopicContract.TABLE),
                        List.of(TopicsTables.ReturnedTopicContract.APP));

        // Combine sets of apps that have usage and returned topics
        appsWithUsage.addAll(appsWithReturnedTopics);

        // Exclude currently installed apps
        appsWithUsage.removeAll(currentInstalledApps);

        return appsWithUsage;
    }

    // TODO(b/234444036): Handle apps that don't have usages in last 3 epochs
    // An app will be regarded as an unhandled installed app if it shows up in package manager,
    // but doesn't have an entry in neither usage table or returned topic table.
    //
    // This will be used in reconciliation process. See details in go/rb-topics-app-update.
    @NonNull
    @VisibleForTesting
    Set<String> getUnhandledInstalledApps(@NonNull Set<String> currentInstalledApps) {
        // Make a copy of installed apps
        Set<String> installedApps = new HashSet<>(currentInstalledApps);

        // Get apps with usages or(and) returned topics
        Set<String> appsWithUsageOrReturnedTopics =
                mTopicsDao.retrieveDistinctAppsFromTables(
                        List.of(
                                TopicsTables.AppUsageHistoryContract.TABLE,
                                TopicsTables.ReturnedTopicContract.TABLE),
                        List.of(
                                TopicsTables.AppUsageHistoryContract.APP,
                                TopicsTables.ReturnedTopicContract.APP));

        // Remove apps with usage and returned topics from currently installed apps
        installedApps.removeAll(appsWithUsageOrReturnedTopics);

        return installedApps;
    }

    // Get current installed applications from package manager
    @NonNull
    @VisibleForTesting
    Set<String> getCurrentInstalledApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> appInfoList =
                PackageManagerCompatUtils.getInstalledApplications(
                        packageManager, PackageManager.GET_META_DATA);
        return appInfoList.stream().map(appInfo -> appInfo.packageName).collect(Collectors.toSet());
    }

    /**
     * Get App Package Name from a Uri.
     *
     * <p>Across PPAPI, package Uri is in the form of "package:com.example.adservices.sampleapp".
     * "package" is a scheme of Uri and "com.example.adservices.sampleapp" is the app package name.
     * Topics API persists app package name into database so this method extracts it from a Uri.
     *
     * @param packageUri the {@link Uri} of a package
     * @return the app package name
     */
    @VisibleForTesting
    @NonNull
    String convertUriToAppName(@NonNull Uri packageUri) {
        return packageUri.getSchemeSpecificPart();
    }

    // Handle Uninstalled applications that still have derived data in database
    //
    // 1) Delete all derived data for an uninstalled app.
    // 2) Remove a topic if it has the uninstalled app as the only contributor in an epoch. In an
    // epoch, an app is a contributor to a topic if the app has called Topics API in this epoch and
    // is classified to the topic.
    private void handleUninstalledAppsInReconciliation(
            @NonNull Set<String> newlyUninstalledApps, long currentEpochId) {
        for (String app : newlyUninstalledApps) {
            handleTopTopicsWithoutContributors(currentEpochId, app);

            deleteAppDataFromTableByApps(List.of(app));
        }
    }

    // Handle newly installed applications
    //
    // Assign topics as real-time service to the app only, if the app isn't assigned with topics.
    private void handleInstalledAppsInReconciliation(
            @NonNull Set<String> newlyInstalledApps, long currentEpochId) {
        for (String newlyInstalledApp : newlyInstalledApps) {
            assignTopicsToNewlyInstalledApps(newlyInstalledApp, currentEpochId);
        }
    }
}
