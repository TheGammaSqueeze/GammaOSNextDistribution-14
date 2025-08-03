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

package com.android.adservices.data.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_ALL_ENTRIES_IN_TABLE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_BLOCKED_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_COLUMN_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_OLD_EPOCH_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_TABLE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_PERSIST_CLASSIFIED_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_PERSIST_TOPICS_CONTRIBUTORS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_PERSIST_TOP_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_APP_SDK_USAGE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_APP_USAGE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_BLOCKED_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_CAN_LEARN_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_RETURNED_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Data Access Object for the Topics API. */
public class TopicsDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static TopicsDao sSingleton;
    private static final Object SINGLETON_LOCK = new Object();

    // Defined constants for error codes which have very long names
    private static final int TOPICS_PERSIST_CLASSIFIED_TOPICS_FAILURE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_PERSIST_CLASSIFIED_TOPICS_FAILURE;
    private static final int TOPICS_RECORD_CAN_LEARN_TOPICS_FAILURE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_CAN_LEARN_TOPICS_FAILURE;
    private static final int TOPICS_RECORD_RETURNED_TOPICS_FAILURE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_RETURNED_TOPICS_FAILURE;
    private static final int TOPICS_PERSIST_TOPICS_CONTRIBUTORS_FAILURE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_PERSIST_TOPICS_CONTRIBUTORS_FAILURE;
    private static final int TOPICS_DELETE_ALL_ENTRIES_IN_TABLE_FAILURE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_ALL_ENTRIES_IN_TABLE_FAILURE;

    private static final String[] ALL_TOPICS_TABLES = {
        TopicsTables.TaxonomyContract.TABLE,
        TopicsTables.AppClassificationTopicsContract.TABLE,
        TopicsTables.AppUsageHistoryContract.TABLE,
        TopicsTables.UsageHistoryContract.TABLE,
        TopicsTables.CallerCanLearnTopicsContract.TABLE,
        TopicsTables.ReturnedTopicContract.TABLE,
        TopicsTables.ReturnedEncryptedTopicContract.TABLE,
        TopicsTables.TopTopicsContract.TABLE,
        TopicsTables.BlockedTopicsContract.TABLE,
        TopicsTables.EpochOriginContract.TABLE,
        TopicsTables.TopicContributorsContract.TABLE
    };

    private final DbHelper mDbHelper; // Used in tests.

    /**
     * It's only public to unit test.
     *
     * @param dbHelper The database to query
     */
    @VisibleForTesting
    public TopicsDao(DbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /** Returns an instance of the TopicsDAO given a context. */
    @NonNull
    public static TopicsDao getInstance(@NonNull Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = new TopicsDao(DbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    /**
     * Persist the apps and their classification topics.
     *
     * @param epochId the epoch ID to persist
     * @param appClassificationTopicsMap Map of app -> classified topics
     */
    public void persistAppClassificationTopics(
            long epochId, @NonNull Map<String, List<Topic>> appClassificationTopicsMap) {
        Objects.requireNonNull(appClassificationTopicsMap);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<String, List<Topic>> entry : appClassificationTopicsMap.entrySet()) {
            String app = entry.getKey();

            // save each topic in the list by app -> topic mapping in the DB
            for (Topic topic : entry.getValue()) {
                ContentValues values = new ContentValues();
                values.put(TopicsTables.AppClassificationTopicsContract.EPOCH_ID, epochId);
                values.put(TopicsTables.AppClassificationTopicsContract.APP, app);
                values.put(
                        TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION,
                        topic.getTaxonomyVersion());
                values.put(
                        TopicsTables.AppClassificationTopicsContract.MODEL_VERSION,
                        topic.getModelVersion());
                values.put(TopicsTables.AppClassificationTopicsContract.TOPIC, topic.getTopic());

                try {
                    db.insert(
                            TopicsTables.AppClassificationTopicsContract.TABLE,
                            /* nullColumnHack */ null,
                            values);
                } catch (SQLException e) {
                    sLogger.e("Failed to persist classified Topics. Exception : " + e.getMessage());
                    ErrorLogUtil.e(
                            e,
                            TOPICS_PERSIST_CLASSIFIED_TOPICS_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                }
            }
        }
    }

    /**
     * Get the map of apps and their classification topics.
     *
     * @param epochId the epoch ID to retrieve
     * @return {@link Map} a map of app -> topics
     */
    @NonNull
    public Map<String, List<Topic>> retrieveAppClassificationTopics(long epochId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        Map<String, List<Topic>> appTopicsMap = new HashMap<>();
        if (db == null) {
            return appTopicsMap;
        }

        String[] projection = {
            TopicsTables.AppClassificationTopicsContract.APP,
            TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION,
            TopicsTables.AppClassificationTopicsContract.MODEL_VERSION,
            TopicsTables.AppClassificationTopicsContract.TOPIC,
        };

        String selection = TopicsTables.AppClassificationTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (Cursor cursor =
                db.query(
                        TopicsTables.AppClassificationTopicsContract.TABLE, // The table to query
                        projection, // The array of columns to return (pass null to get all)
                        selection, // The columns for the WHERE clause
                        selectionArgs, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        )) {
            while (cursor.moveToNext()) {
                String app =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.AppClassificationTopicsContract.APP));
                long taxonomyVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.AppClassificationTopicsContract
                                                .TAXONOMY_VERSION));
                long modelVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.AppClassificationTopicsContract
                                                .MODEL_VERSION));
                int topicId =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.AppClassificationTopicsContract.TOPIC));
                Topic topic = Topic.create(topicId, taxonomyVersion, modelVersion);

                List<Topic> list = appTopicsMap.getOrDefault(app, new ArrayList<>());
                list.add(topic);
                appTopicsMap.put(app, list);
            }
        }

        return appTopicsMap;
    }

    /**
     * Persist the list of Top Topics in this epoch to DB.
     *
     * @param epochId ID of current epoch
     * @param topTopics the topics list to persist into DB
     */
    public void persistTopTopics(long epochId, @NonNull List<Topic> topTopics) {
        // topTopics the Top Topics: a list of 5 top topics and the 6th topic
        // which was selected randomly. We can refer this 6th topic as the random-topic.
        Objects.requireNonNull(topTopics);
        Preconditions.checkArgument(topTopics.size() == 6);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(TopicsTables.TopTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.TopTopicsContract.TOPIC1, topTopics.get(0).getTopic());
        values.put(TopicsTables.TopTopicsContract.TOPIC2, topTopics.get(1).getTopic());
        values.put(TopicsTables.TopTopicsContract.TOPIC3, topTopics.get(2).getTopic());
        values.put(TopicsTables.TopTopicsContract.TOPIC4, topTopics.get(3).getTopic());
        values.put(TopicsTables.TopTopicsContract.TOPIC5, topTopics.get(4).getTopic());
        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, topTopics.get(5).getTopic());
        // Taxonomy version and model version of all top topics should be the same.
        // Therefore, get it from the first top topic.
        values.put(
                TopicsTables.TopTopicsContract.TAXONOMY_VERSION,
                topTopics.get(0).getTaxonomyVersion());
        values.put(
                TopicsTables.TopTopicsContract.MODEL_VERSION, topTopics.get(0).getModelVersion());

        try {
            db.insert(TopicsTables.TopTopicsContract.TABLE, /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            sLogger.e("Failed to persist Top Topics. Exception : " + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_PERSIST_TOP_TOPICS_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    /**
     * Return the Top Topics. This will retrieve a list of 5 top topics and the 6th random topic
     * from DB.
     *
     * @param epochId the epochId to retrieve the top topics.
     * @return {@link List} a {@link List} of {@link Topic}
     */
    @NonNull
    public List<Topic> retrieveTopTopics(long epochId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return new ArrayList<>();
        }

        String[] projection = {
            TopicsTables.TopTopicsContract.TOPIC1,
            TopicsTables.TopTopicsContract.TOPIC2,
            TopicsTables.TopTopicsContract.TOPIC3,
            TopicsTables.TopTopicsContract.TOPIC4,
            TopicsTables.TopTopicsContract.TOPIC5,
            TopicsTables.TopTopicsContract.RANDOM_TOPIC,
            TopicsTables.TopTopicsContract.TAXONOMY_VERSION,
            TopicsTables.TopTopicsContract.MODEL_VERSION
        };

        String selection = TopicsTables.AppClassificationTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (Cursor cursor =
                db.query(
                        TopicsTables.TopTopicsContract.TABLE, // The table to query
                        projection, // The array of columns to return (pass null to get all)
                        selection, // The columns for the WHERE clause
                        selectionArgs, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        )) {
            if (cursor.moveToNext()) {
                int topicId1 =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.TOPIC1));
                int topicId2 =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.TOPIC2));
                int topicId3 =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.TOPIC3));
                int topicId4 =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.TOPIC4));
                int topicId5 =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.TOPIC5));
                int randomTopicId =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.RANDOM_TOPIC));
                long taxonomyVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.TAXONOMY_VERSION));
                long modelVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopTopicsContract.MODEL_VERSION));
                Topic topic1 = Topic.create(topicId1, taxonomyVersion, modelVersion);
                Topic topic2 = Topic.create(topicId2, taxonomyVersion, modelVersion);
                Topic topic3 = Topic.create(topicId3, taxonomyVersion, modelVersion);
                Topic topic4 = Topic.create(topicId4, taxonomyVersion, modelVersion);
                Topic topic5 = Topic.create(topicId5, taxonomyVersion, modelVersion);
                Topic randomTopic = Topic.create(randomTopicId, taxonomyVersion, modelVersion);
                return Arrays.asList(topic1, topic2, topic3, topic4, topic5, randomTopic);
            }
        }

        return new ArrayList<>();
    }

    /**
     * Record the App and SDK into the Usage History table.
     *
     * @param epochId epochId epoch id to record
     * @param app app name
     * @param sdk sdk name
     */
    public void recordUsageHistory(long epochId, @NonNull String app, @NonNull String sdk) {
        Objects.requireNonNull(app);
        Objects.requireNonNull(sdk);
        Preconditions.checkStringNotEmpty(app);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(TopicsTables.UsageHistoryContract.APP, app);
        values.put(TopicsTables.UsageHistoryContract.SDK, sdk);
        values.put(TopicsTables.UsageHistoryContract.EPOCH_ID, epochId);

        try {
            db.insert(TopicsTables.UsageHistoryContract.TABLE, /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            sLogger.e("Failed to record App-Sdk usage history." + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_APP_SDK_USAGE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }

    }

    /**
     * Record the usage history for app only
     *
     * @param epochId epoch id to record
     * @param app app name
     */
    public void recordAppUsageHistory(long epochId, @NonNull String app) {
        Objects.requireNonNull(app);
        Preconditions.checkStringNotEmpty(app);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(TopicsTables.AppUsageHistoryContract.APP, app);
        values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, epochId);

        try {
            db.insert(
                    TopicsTables.AppUsageHistoryContract.TABLE, /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            sLogger.e("Failed to record App Only usage history." + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_APP_USAGE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    /**
     * Return all apps and their SDKs that called Topics API in the epoch.
     *
     * @param epochId the epoch to retrieve the app and sdk usage for.
     * @return Return Map<App, List<SDK>>.
     */
    @NonNull
    public Map<String, List<String>> retrieveAppSdksUsageMap(long epochId) {
        Map<String, List<String>> appSdksUsageMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return appSdksUsageMap;
        }

        String[] projection = {
            TopicsTables.UsageHistoryContract.APP, TopicsTables.UsageHistoryContract.SDK,
        };

        String selection = TopicsTables.UsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (Cursor cursor =
                db.query(
                        /* distinct= */ true,
                        TopicsTables.UsageHistoryContract.TABLE,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                String app =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.UsageHistoryContract.APP));
                String sdk =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.UsageHistoryContract.SDK));
                if (!appSdksUsageMap.containsKey(app)) {
                    appSdksUsageMap.put(app, new ArrayList<>());
                }
                appSdksUsageMap.get(app).add(sdk);
            }
        }

        return appSdksUsageMap;
    }

    /**
     * Get topic api usage of an app in an epoch.
     *
     * @param epochId the epoch to retrieve the app usage for.
     * @return Map<App, UsageCount>, how many times an app called topics API in this epoch
     */
    @NonNull
    public Map<String, Integer> retrieveAppUsageMap(long epochId) {
        Map<String, Integer> appUsageMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return appUsageMap;
        }

        String[] projection = {
            TopicsTables.AppUsageHistoryContract.APP,
        };

        String selection = TopicsTables.AppUsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (Cursor cursor =
                db.query(
                        TopicsTables.AppUsageHistoryContract.TABLE,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                String app =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.AppUsageHistoryContract.APP));
                appUsageMap.put(app, appUsageMap.getOrDefault(app, 0) + 1);
            }
        }

        return appUsageMap;
    }

    /**
     * Get a union set of distinct apps among tables.
     *
     * @param tableNames a {@link List} of table names
     * @param appColumnNames a {@link List} of app Column names for given tables
     * @return a {@link Set} of unique apps in the table
     * @throws IllegalArgumentException if {@code tableNames} and {@code appColumnNames} have
     *     different sizes.
     */
    @NonNull
    public Set<String> retrieveDistinctAppsFromTables(
            @NonNull List<String> tableNames, @NonNull List<String> appColumnNames) {
        Preconditions.checkArgument(tableNames.size() == appColumnNames.size());

        Set<String> apps = new HashSet<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return apps;
        }

        for (int index = 0; index < tableNames.size(); index++) {
            String[] projection = {appColumnNames.get(index)};

            try (Cursor cursor =
                    db.query(
                            /* distinct */ true,
                            tableNames.get(index),
                            projection,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null)) {
                while (cursor.moveToNext()) {
                    String app =
                            cursor.getString(
                                    cursor.getColumnIndexOrThrow(appColumnNames.get(index)));
                    apps.add(app);
                }
            }
        }

        return apps;
    }

    // TODO(b/236764602): Create a Caller Class.
    /**
     * Persist the Callers can learn topic map to DB.
     *
     * @param epochId the epoch ID.
     * @param callerCanLearnMap callerCanLearnMap = {@code Map<Topic, Set<Caller>>} This is a Map
     *     from Topic to set of App or Sdk (Caller = App or Sdk) that can learn about that topic.
     *     This is similar to the table Can Learn Topic in the explainer.
     */
    public void persistCallerCanLearnTopics(
            long epochId, @NonNull Map<Topic, Set<String>> callerCanLearnMap) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<Topic, Set<String>> entry : callerCanLearnMap.entrySet()) {
            Topic topic = entry.getKey();
            Set<String> callers = entry.getValue();

            for (String caller : callers) {
                ContentValues values = new ContentValues();
                values.put(TopicsTables.CallerCanLearnTopicsContract.CALLER, caller);
                values.put(TopicsTables.CallerCanLearnTopicsContract.TOPIC, topic.getTopic());
                values.put(TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID, epochId);
                values.put(
                        TopicsTables.CallerCanLearnTopicsContract.TAXONOMY_VERSION,
                        topic.getTaxonomyVersion());
                values.put(
                        TopicsTables.CallerCanLearnTopicsContract.MODEL_VERSION,
                        topic.getModelVersion());

                try {
                    db.insert(
                            TopicsTables.CallerCanLearnTopicsContract.TABLE,
                            /* nullColumnHack */ null,
                            values);
                } catch (SQLException e) {
                    sLogger.e(e, "Failed to record can learn topic.");
                    ErrorLogUtil.e(
                            e,
                            TOPICS_RECORD_CAN_LEARN_TOPICS_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                }
            }
        }
    }

    /**
     * Retrieve the CallersCanLearnTopicsMap This is a Map from Topic to set of App or Sdk (Caller =
     * App or Sdk) that can learn about that topic. This is similar to the table Can Learn Topic in
     * the explainer. We will look back numberOfLookBackEpochs epochs. The current explainer uses 3
     * past epochs. Basically we select epochId between [epochId - numberOfLookBackEpochs + 1,
     * epochId]
     *
     * @param epochId the epochId
     * @param numberOfLookBackEpochs Look back numberOfLookBackEpochs.
     * @return {@link Map} a Map<Topic, Set<Caller>> where Caller = App or Sdk.
     */
    @NonNull
    public Map<Topic, Set<String>> retrieveCallerCanLearnTopicsMap(
            long epochId, int numberOfLookBackEpochs) {
        Preconditions.checkArgumentPositive(
                numberOfLookBackEpochs, "numberOfLookBackEpochs must be positive!");

        Map<Topic, Set<String>> callerCanLearnMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return callerCanLearnMap;
        }

        String[] projection = {
            TopicsTables.CallerCanLearnTopicsContract.CALLER,
            TopicsTables.CallerCanLearnTopicsContract.TOPIC,
            TopicsTables.CallerCanLearnTopicsContract.TAXONOMY_VERSION,
            TopicsTables.CallerCanLearnTopicsContract.MODEL_VERSION,
        };

        // Select epochId between [epochId - numberOfLookBackEpochs + 1, epochId]
        String selection =
                " ? <= "
                        + TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID
                        + " AND "
                        + TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID
                        + " <= ?";
        String[] selectionArgs = {
            String.valueOf(epochId - numberOfLookBackEpochs + 1), String.valueOf(epochId)
        };

        try (Cursor cursor =
                db.query(
                        /* distinct= */ true,
                        TopicsTables.CallerCanLearnTopicsContract.TABLE,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null) {
                return callerCanLearnMap;
            }

            while (cursor.moveToNext()) {
                String caller =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.CallerCanLearnTopicsContract.CALLER));
                int topicId =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.CallerCanLearnTopicsContract.TOPIC));
                long taxonomyVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.CallerCanLearnTopicsContract
                                                .TAXONOMY_VERSION));
                long modelVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.CallerCanLearnTopicsContract.MODEL_VERSION));
                Topic topic = Topic.create(topicId, taxonomyVersion, modelVersion);
                if (!callerCanLearnMap.containsKey(topic)) {
                    callerCanLearnMap.put(topic, new HashSet<>());
                }
                callerCanLearnMap.get(topic).add(caller);
            }
        }

        return callerCanLearnMap;
    }

    // TODO(b/236759629): Add a validation to ensure same topic for an app.

    /**
     * Persist the Apps, Sdks returned topics to DB.
     *
     * @param epochId the epoch ID
     * @param returnedAppSdkTopics {@link Map} a Map<Pair<app, sdk>, Topic>
     */
    public void persistReturnedAppTopicsMap(
            long epochId,
            @NonNull Map<Pair<String, String>, Topic> returnedAppSdkTopics) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<Pair<String, String>, Topic> app : returnedAppSdkTopics.entrySet()) {
            // Entry: Key = <Pair<App, Sdk>, Value = Topic.
            ContentValues values = new ContentValues();
            values.put(TopicsTables.ReturnedTopicContract.EPOCH_ID, epochId);
            values.put(TopicsTables.ReturnedTopicContract.APP, app.getKey().first);
            values.put(TopicsTables.ReturnedTopicContract.SDK, app.getKey().second);
            values.put(TopicsTables.ReturnedTopicContract.TOPIC, app.getValue().getTopic());
            values.put(
                    TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION,
                    app.getValue().getTaxonomyVersion());
            values.put(
                    TopicsTables.ReturnedTopicContract.MODEL_VERSION,
                    app.getValue().getModelVersion());
            // Persist the logged topic to DB if ENABLE_LOGGED_TOPIC is true.
            if (supportsLoggedTopicInReturnedTopicTable()) {
                values.put(TopicsTables.ReturnedTopicContract.LOGGED_TOPIC,
                        app.getValue().getLoggedTopic());
            }

            try {
                db.insert(
                        TopicsTables.ReturnedTopicContract.TABLE,
                        /* nullColumnHack */ null,
                        values);
            } catch (SQLException e) {
                sLogger.e(e, "Failed to record returned topic.");
                ErrorLogUtil.e(
                        e,
                        TOPICS_RECORD_RETURNED_TOPICS_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
        }
    }

    /**
     * Persist the Apps, Sdks returned topics to DB.
     *
     * @param epochId the epoch ID
     * @param returnedAppSdkEncryptedTopics {@link Map} a Map< Pair< app, sdk>, EncryptedTopic>
     */
    public void persistReturnedAppEncryptedTopicsMap(
            long epochId,
            @NonNull Map<Pair<String, String>, EncryptedTopic> returnedAppSdkEncryptedTopics) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<Pair<String, String>, EncryptedTopic> app :
                returnedAppSdkEncryptedTopics.entrySet()) {
            // Entry: Key = <Pair<App, Sdk>, Value = Topic.
            ContentValues values = new ContentValues();
            values.put(TopicsTables.ReturnedEncryptedTopicContract.EPOCH_ID, epochId);
            values.put(TopicsTables.ReturnedEncryptedTopicContract.APP, app.getKey().first);
            values.put(TopicsTables.ReturnedEncryptedTopicContract.SDK, app.getKey().second);
            values.put(
                    TopicsTables.ReturnedEncryptedTopicContract.ENCRYPTED_TOPIC,
                    app.getValue().getEncryptedTopic());
            values.put(
                    TopicsTables.ReturnedEncryptedTopicContract.KEY_IDENTIFIER,
                    app.getValue().getKeyIdentifier());
            values.put(
                    TopicsTables.ReturnedEncryptedTopicContract.ENCAPSULATED_KEY,
                    app.getValue().getEncapsulatedKey());
            try {
                db.insert(
                        TopicsTables.ReturnedEncryptedTopicContract.TABLE,
                        /* nullColumnHack */ null,
                        values);
            } catch (SQLException e) {
                sLogger.e(e, "Failed to record encrypted returned topic.");
                // TODO(b/307816452): Add client error log util here.
            }
        }
    }

    /**
     * Retrieve from the Topics ReturnedTopics Table and populate into the map. Will return topics
     * for epoch with epochId in [epochId - numberOfLookBackEpochs + 1, epochId]
     *
     * @param epochId the current epochId
     * @param numberOfLookBackEpochs How many epoch to look back. The current explainer uses 3
     *     epochs
     * @return a {@link Map} in type {@code Map<EpochId, Map < Pair < App, Sdk>, Topic>}
     */
    @NonNull
    public Map<Long, Map<Pair<String, String>, Topic>> retrieveReturnedTopics(
            long epochId, int numberOfLookBackEpochs) {
        Map<Long, Map<Pair<String, String>, Topic>> topicsMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return topicsMap;
        }

        String[] projection = {
            TopicsTables.ReturnedTopicContract.EPOCH_ID,
            TopicsTables.ReturnedTopicContract.APP,
            TopicsTables.ReturnedTopicContract.SDK,
            TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION,
            TopicsTables.ReturnedTopicContract.MODEL_VERSION,
            TopicsTables.ReturnedTopicContract.TOPIC,
        };
        // Add LOGGED_TOPIC in the projection if flag ENABLE_LOGGED_TOPIC is true.
        if (supportsLoggedTopicInReturnedTopicTable()) {
            projection = new String[] {
                    TopicsTables.ReturnedTopicContract.EPOCH_ID,
                    TopicsTables.ReturnedTopicContract.APP,
                    TopicsTables.ReturnedTopicContract.SDK,
                    TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION,
                    TopicsTables.ReturnedTopicContract.MODEL_VERSION,
                    TopicsTables.ReturnedTopicContract.TOPIC,
                    TopicsTables.ReturnedTopicContract.LOGGED_TOPIC,
            };
        }

        // Select epochId between [epochId - numberOfLookBackEpochs + 1, epochId]
        String selection =
                " ? <= "
                        + TopicsTables.ReturnedTopicContract.EPOCH_ID
                        + " AND "
                        + TopicsTables.ReturnedTopicContract.EPOCH_ID
                        + " <= ?";
        String[] selectionArgs = {
            String.valueOf(epochId - numberOfLookBackEpochs + 1), String.valueOf(epochId)
        };

        try (Cursor cursor =
                db.query(
                        TopicsTables.ReturnedTopicContract.TABLE, // The table to query
                        projection, // The array of columns to return (pass null to get all)
                        selection, // The columns for the WHERE clause
                        selectionArgs, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        )) {
            if (cursor == null) {
                return topicsMap;
            }

            while (cursor.moveToNext()) {
                long cursorEpochId =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedTopicContract.EPOCH_ID));
                String app =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedTopicContract.APP));
                String sdk =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedTopicContract.SDK));
                long taxonomyVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION));
                long modelVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedTopicContract.MODEL_VERSION));
                int topicId =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedTopicContract.TOPIC));

                // Building Map<EpochId, Map<Pair<AppId, AdTechId>, Topic>
                if (!topicsMap.containsKey(cursorEpochId)) {
                    topicsMap.put(cursorEpochId, new HashMap<>());
                }

                Topic topic = Topic.create(topicId, taxonomyVersion, modelVersion);
                // Add logged topic id to topic if flag ENABLE_LOGGED_TOPIC is true.
                if (supportsLoggedTopicInReturnedTopicTable()) {
                    int loggedTopicId =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            TopicsTables.ReturnedTopicContract.LOGGED_TOPIC));
                    topic = Topic.create(
                            topicId, taxonomyVersion, modelVersion, loggedTopicId);
                }

                topicsMap.get(cursorEpochId).put(Pair.create(app, sdk), topic);
            }
        }

        return topicsMap;
    }

    /**
     * Retrieve from the ReturnedEncryptedTopic Table and populate into the map. Will return
     * encrypted topics for epoch with epochId in [epochId - numberOfLookBackEpochs + 1, epochId]
     *
     * @param epochId the current epochId
     * @param numberOfLookBackEpochs How many epoch to look back. The current explainer uses 3
     *     epochs
     * @return a {@link Map} in type {@code Map<EpochId, Map < Pair < App, Sdk>, EncryptedTopic>}
     */
    @NonNull
    public Map<Long, Map<Pair<String, String>, EncryptedTopic>> retrieveReturnedEncryptedTopics(
            long epochId, int numberOfLookBackEpochs) {
        Map<Long, Map<Pair<String, String>, EncryptedTopic>> encryptedTopicsMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return encryptedTopicsMap;
        }

        String[] projection = {
            TopicsTables.ReturnedEncryptedTopicContract.EPOCH_ID,
            TopicsTables.ReturnedEncryptedTopicContract.APP,
            TopicsTables.ReturnedEncryptedTopicContract.SDK,
            TopicsTables.ReturnedEncryptedTopicContract.ENCRYPTED_TOPIC,
            TopicsTables.ReturnedEncryptedTopicContract.KEY_IDENTIFIER,
            TopicsTables.ReturnedEncryptedTopicContract.ENCAPSULATED_KEY,
        };

        // Select epochId between [epochId - numberOfLookBackEpochs + 1, epochId]
        String selection =
                " ? <= "
                        + TopicsTables.ReturnedEncryptedTopicContract.EPOCH_ID
                        + " AND "
                        + TopicsTables.ReturnedEncryptedTopicContract.EPOCH_ID
                        + " <= ?";
        String[] selectionArgs = {
            String.valueOf(epochId - numberOfLookBackEpochs + 1), String.valueOf(epochId)
        };

        try (Cursor cursor =
                db.query(
                        TopicsTables.ReturnedEncryptedTopicContract.TABLE, // The table to query
                        projection, // The array of columns to return (pass null to get all)
                        selection, // The columns for the WHERE clause
                        selectionArgs, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        )) {
            if (cursor == null) {
                return encryptedTopicsMap;
            }

            while (cursor.moveToNext()) {
                long cursorEpochId =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedEncryptedTopicContract.EPOCH_ID));
                String app =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedEncryptedTopicContract.APP));
                String sdk =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedEncryptedTopicContract.SDK));
                byte[] encryptedTopic =
                        cursor.getBlob(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedEncryptedTopicContract
                                                .ENCRYPTED_TOPIC));
                String keyIdentifier =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedEncryptedTopicContract
                                                .KEY_IDENTIFIER));
                byte[] encapsulatedKey =
                        cursor.getBlob(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.ReturnedEncryptedTopicContract
                                                .ENCAPSULATED_KEY));

                // Building Map<EpochId, Map<Pair<AppId, AdTechId>, Topic>
                if (!encryptedTopicsMap.containsKey(cursorEpochId)) {
                    encryptedTopicsMap.put(cursorEpochId, new HashMap<>());
                }

                EncryptedTopic topic =
                        EncryptedTopic.create(encryptedTopic, keyIdentifier, encapsulatedKey);

                encryptedTopicsMap.get(cursorEpochId).put(Pair.create(app, sdk), topic);
            }
        }

        return encryptedTopicsMap;
    }

    /**
     * Record {@link Topic} which should be blocked.
     *
     * @param topic {@link Topic} to block.
     */
    public void recordBlockedTopic(@NonNull Topic topic) {
        Objects.requireNonNull(topic);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }
        // Create a new map of values, where column names are the keys
        ContentValues values = getContentValuesForBlockedTopic(topic);

        try {
            db.insert(TopicsTables.BlockedTopicsContract.TABLE, /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            sLogger.e("Failed to record blocked topic." + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_BLOCKED_TOPICS_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    @NonNull
    private ContentValues getContentValuesForBlockedTopic(@NonNull Topic topic) {
        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(TopicsTables.BlockedTopicsContract.TOPIC, topic.getTopic());
        values.put(TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION, topic.getTaxonomyVersion());
        values.put(TopicsTables.BlockedTopicsContract.MODEL_VERSION, topic.getModelVersion());
        return values;
    }

    /**
     * Remove blocked {@link Topic}.
     *
     * @param topic blocked {@link Topic} to remove.
     */
    public void removeBlockedTopic(@NonNull Topic topic) {
        Objects.requireNonNull(topic);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Where statement for triplet: topics, taxonomyVersion, modelVersion
        String whereClause =
                " ? = "
                        + TopicsTables.BlockedTopicsContract.TOPIC
                        + " AND "
                        + TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION
                        + " = ?"
                        + " AND "
                        + TopicsTables.BlockedTopicsContract.MODEL_VERSION
                        + " = ?";
        String[] whereArgs = {
            String.valueOf(topic.getTopic()),
            String.valueOf(topic.getTaxonomyVersion()),
            String.valueOf(topic.getModelVersion())
        };

        try {
            db.delete(TopicsTables.BlockedTopicsContract.TABLE, whereClause, whereArgs);
        } catch (SQLException e) {
            sLogger.e("Failed to delete blocked topic." + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_BLOCKED_TOPICS_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    /**
     * Get a {@link List} of {@link Topic}s which are blocked.
     *
     * @return {@link List} a {@link List} of blocked {@link Topic}s.s
     */
    @NonNull
    public List<Topic> retrieveAllBlockedTopics() {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        List<Topic> blockedTopics = new ArrayList<>();
        if (db == null) {
            return blockedTopics;
        }

        try (Cursor cursor =
                db.query(
                        /* distinct= */ true,
                        TopicsTables.BlockedTopicsContract.TABLE, // The table to query
                        null, // Get all columns (null for all)
                        null, // Select all columns (null for all)
                        null, // Select all columns (null for all)
                        null, // Don't group the rows
                        null, // Don't filter by row groups
                        null, // don't sort
                        null // don't limit
                        )) {
            while (cursor.moveToNext()) {
                long taxonomyVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION));
                long modelVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.MODEL_VERSION));
                int topicInt =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.TOPIC));
                Topic topic = Topic.create(topicInt, taxonomyVersion, modelVersion);

                blockedTopics.add(topic);
            }
        }

        return blockedTopics;
    }

    /**
     * Delete from epoch-related tables for data older than/equal to certain epoch in DB.
     *
     * @param tableName the table to delete data from
     * @param epochColumnName epoch Column name for given table
     * @param epochToDeleteFrom the epoch to delete starting from (inclusive)
     */
    public void deleteDataOfOldEpochs(
            @NonNull String tableName, @NonNull String epochColumnName, long epochToDeleteFrom) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Delete epochId before epochToDeleteFrom (including epochToDeleteFrom)
        String deletion = " " + epochColumnName + " <= ?";
        String[] deletionArgs = {String.valueOf(epochToDeleteFrom)};

        try {
            db.delete(tableName, deletion, deletionArgs);
        } catch (SQLException e) {
            sLogger.e(e, "Failed to delete old epochs' data.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_OLD_EPOCH_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    /**
     * Delete all data generated by Topics API, except for tables in the exclusion list.
     *
     * @param tablesToExclude a {@link List} of tables that won't be deleted.
     */
    public void deleteAllTopicsTables(@NonNull List<String> tablesToExclude) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Handle this in a transaction.
        db.beginTransaction();

        try {
            for (String table : ALL_TOPICS_TABLES) {
                if (!tablesToExclude.contains(table)) {
                    try {
                        db.delete(table, /* whereClause= */ null, /* whereArgs= */ null);
                    } catch (SQLException e) {
                        sLogger.e(
                                "Failed to delete %s table for Topics. Error: %s",
                                table, e.getMessage());
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_TABLE_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                    }
                }
            }

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Delete by column for the given values. Allow passing in multiple tables with their
     * corresponding column names to delete by.
     *
     * @param tableNamesAndColumnNamePairs the tables and corresponding column names to remove
     *     entries from
     * @param valuesToDelete a {@link List} of values to delete if the entry has such value in
     *     {@code columnNameToDeleteFrom}
     */
    public void deleteFromTableByColumn(
            @NonNull List<Pair<String, String>> tableNamesAndColumnNamePairs,
            @NonNull List<String> valuesToDelete) {
        Objects.requireNonNull(tableNamesAndColumnNamePairs);
        Objects.requireNonNull(valuesToDelete);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        // If valuesToDelete is empty, do nothing.
        if (db == null || valuesToDelete.isEmpty()) {
            return;
        }

        for (Pair<String, String> tableAndColumnNamePair : tableNamesAndColumnNamePairs) {
            String tableName = tableAndColumnNamePair.first;
            String columnNameToDeleteFrom = tableAndColumnNamePair.second;

            // Construct the "IN" part of SQL Query
            StringBuilder whereClauseBuilder = new StringBuilder();
            whereClauseBuilder.append("(?");
            for (int i = 0; i < valuesToDelete.size() - 1; i++) {
                whereClauseBuilder.append(",?");
            }
            whereClauseBuilder.append(')');

            String whereClause = columnNameToDeleteFrom + " IN " + whereClauseBuilder;
            String[] whereArgs = valuesToDelete.toArray(new String[0]);

            try {
                db.delete(tableName, whereClause, whereArgs);
            } catch (SQLException e) {
                sLogger.e(
                        e,
                        String.format(
                                "Failed to delete %s in table %s.", valuesToDelete, tableName));
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_COLUMN_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
        }
    }

    /**
     * Delete an entry from tables if the value in the column of this entry exists in the given
     * values.
     *
     * <p>Similar to deleteEntriesFromTableByColumn but only delete entries that satisfy the equal
     * condition.
     *
     * @param tableNamesAndColumnNamePairs the tables and corresponding column names to remove
     *     entries from
     * @param valuesToDelete a {@link List} of values to delete if the entry has such value in
     *     {@code columnNameToDeleteFrom}
     * @param equalConditionColumnName the column name of the equal condition
     * @param equalConditionColumnValue the value in {@code equalConditionColumnName} of the equal
     *     condition
     * @param isStringEqualConditionColumnValue whether the value of {@code
     *     equalConditionColumnValue} is a string
     */
    public void deleteEntriesFromTableByColumnWithEqualCondition(
            @NonNull List<Pair<String, String>> tableNamesAndColumnNamePairs,
            @NonNull List<String> valuesToDelete,
            @NonNull String equalConditionColumnName,
            @NonNull String equalConditionColumnValue,
            boolean isStringEqualConditionColumnValue) {
        Objects.requireNonNull(tableNamesAndColumnNamePairs);
        Objects.requireNonNull(valuesToDelete);
        Objects.requireNonNull(equalConditionColumnName);
        Objects.requireNonNull(equalConditionColumnValue);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        // If valuesToDelete is empty, do nothing.
        if (db == null || valuesToDelete.isEmpty()) {
            return;
        }

        for (Pair<String, String> tableAndColumnNamePair : tableNamesAndColumnNamePairs) {
            String tableName = tableAndColumnNamePair.first;
            String columnNameToDeleteFrom = tableAndColumnNamePair.second;

            // Construct the "IN" part of SQL Query
            StringBuilder whereClauseBuilder = new StringBuilder();
            whereClauseBuilder.append("(?");
            for (int i = 0; i < valuesToDelete.size() - 1; i++) {
                whereClauseBuilder.append(",?");
            }
            whereClauseBuilder.append(')');

            // Add equal condition to sql query. If the value is a string, bound it with single
            // quotes.
            String whereClause =
                    columnNameToDeleteFrom
                            + " IN "
                            + whereClauseBuilder
                            + " AND "
                            + equalConditionColumnName
                            + " = ";
            if (isStringEqualConditionColumnValue) {
                whereClause += "'" + equalConditionColumnValue + "'";
            } else {
                whereClause += equalConditionColumnValue;
            }

            try {
                db.delete(tableName, whereClause, valuesToDelete.toArray(new String[0]));
            } catch (SQLException e) {
                sLogger.e(
                        e,
                        String.format(
                                "Failed to delete %s in table %s.", valuesToDelete, tableName));
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_COLUMN_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
        }
    }

    /**
     * Persist the origin's timestamp of epoch service in milliseconds into database.
     *
     * @param originTimestampMs the timestamp user first calls Topics API
     */
    public void persistEpochOrigin(long originTimestampMs) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(TopicsTables.EpochOriginContract.ORIGIN, originTimestampMs);

        try {
            db.insert(TopicsTables.EpochOriginContract.TABLE, /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            sLogger.e("Failed to persist epoch origin." + e.getMessage());
        }
    }

    /**
     * Retrieve origin's timestamp of epoch service in milliseconds. If there is no origin persisted
     * in database, return -1;
     *
     * @return the origin's timestamp of epoch service in milliseconds. Return -1 if no origin is
     *     persisted.
     */
    public long retrieveEpochOrigin() {
        long origin = -1L;

        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return origin;
        }

        String[] projection = {
            TopicsTables.EpochOriginContract.ORIGIN,
        };

        try (Cursor cursor =
                db.query(
                        TopicsTables.EpochOriginContract.TABLE, // The table to query
                        projection, // The array of columns to return (pass null to get all)
                        null, // The columns for the WHERE clause
                        null, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        )) {
            // Return the only entry in this table if existed.
            if (cursor.moveToNext()) {
                origin =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.EpochOriginContract.ORIGIN));
            }
        }

        return origin;
    }

    /**
     * Persist topic to contributor mappings to the database. In an epoch, an app is a contributor
     * to a topic if the app has called Topics API in this epoch and is classified to the topic.
     *
     * @param epochId the epochId
     * @param topicToContributorsMap a {@link Map} of topic to a @{@link Set} of its contributor
     *     apps.
     */
    public void persistTopicContributors(
            long epochId, @NonNull Map<Integer, Set<String>> topicToContributorsMap) {
        Objects.requireNonNull(topicToContributorsMap);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<Integer, Set<String>> topicToContributors :
                topicToContributorsMap.entrySet()) {
            Integer topicId = topicToContributors.getKey();

            for (String app : topicToContributors.getValue()) {
                ContentValues values = new ContentValues();
                values.put(TopicsTables.TopicContributorsContract.EPOCH_ID, epochId);
                values.put(TopicsTables.TopicContributorsContract.TOPIC, topicId);
                values.put(TopicsTables.TopicContributorsContract.APP, app);

                try {
                    db.insert(
                            TopicsTables.TopicContributorsContract.TABLE,
                            /* nullColumnHack */ null,
                            values);
                } catch (SQLException e) {
                    sLogger.e(e, "Failed to persist topic contributors.");
                    ErrorLogUtil.e(
                            e,
                            TOPICS_PERSIST_TOPICS_CONTRIBUTORS_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                }
            }
        }
    }

    /**
     * Retrieve topic to contributor mappings from database. In an epoch, an app is a contributor to
     * a topic if the app has called Topics API in this epoch and is classified to the topic.
     *
     * @param epochId the epochId
     * @return a {@link Map} of topic to its contributors
     */
    @NonNull
    public Map<Integer, Set<String>> retrieveTopicToContributorsMap(long epochId) {
        Map<Integer, Set<String>> topicToContributorsMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return topicToContributorsMap;
        }

        String[] projection = {
            TopicsTables.TopicContributorsContract.EPOCH_ID,
            TopicsTables.TopicContributorsContract.TOPIC,
            TopicsTables.TopicContributorsContract.APP
        };

        String selection = TopicsTables.TopicContributorsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (Cursor cursor =
                db.query(
                        TopicsTables.TopicContributorsContract.TABLE, // The table to query
                        projection, // The array of columns to return (pass null to get all)
                        selection, // The columns for the WHERE clause
                        selectionArgs, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        )) {
            if (cursor == null) {
                return topicToContributorsMap;
            }

            while (cursor.moveToNext()) {
                String app =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopicContributorsContract.APP));
                int topicId =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.TopicContributorsContract.TOPIC));

                topicToContributorsMap.putIfAbsent(topicId, new HashSet<>());
                topicToContributorsMap.get(topicId).add(app);
            }
        }

        return topicToContributorsMap;
    }

    /**
     * Delete all entries from a table.
     *
     * @param tableName the table to delete entries from
     */
    public void deleteAllEntriesFromTable(@NonNull String tableName) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        try {
            db.delete(tableName, /* whereClause */ "", /* whereArgs */ new String[0]);
        } catch (SQLException e) {
            sLogger.e(
                    "Failed to delete all entries from table %s. Error: %s",
                    tableName, e.getMessage());
            ErrorLogUtil.e(
                    e,
                    TOPICS_DELETE_ALL_ENTRIES_IN_TABLE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    /** Check whether Logged Topic is supported in ReturnedTopic Table . */
    public boolean supportsLoggedTopicInReturnedTopicTable() {
        return mDbHelper.supportsLoggedTopicInReturnedTopicTable();
    }
}
