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

package com.android.adservices.data.topics.migration;

import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_APP_CLASSIFICATION_TOPICS;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_APP_USAGE_HISTORY;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_BLOCKED_TOPICS;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_CALLER_CAN_LEARN_TOPICS;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_EPOCH_ORIGIN;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_TOPICS_TAXONOMY;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_TOPIC_CONTRIBUTORS;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_TOP_TOPICS;
import static com.android.adservices.data.topics.TopicsTables.CREATE_TABLE_USAGE_HISTORY;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.TopicsTables;

import java.io.File;
import java.util.List;

/**
 * Class to get the state of Topics' database on version 7 for test purpose. V7 doesn't include
 * logged_topic column in ReturnedTopic table.
 */
public class TopicsDbHelperV7 extends DbHelper {
    private static final int CURRENT_DATABASE_VERSION = 7;
    // TODO (b/255964885): Consolidate DB Migrator Class across Rubidium
    private static final String DATABASE_NAME_TOPICS_MIGRATION = "adservices_topics_migration.db";
    private static TopicsDbHelperV7 sSingleton = null;

    TopicsDbHelperV7(Context context, String dbName, int dbVersion) {
        super(context, dbName, dbVersion);
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static TopicsDbHelperV7 getInstance(@NonNull Context context) {
        synchronized (TopicsDbHelperV7.class) {
            if (sSingleton == null) {
                clearDatabase(context);
                sSingleton =
                        new TopicsDbHelperV7(
                                context, DATABASE_NAME_TOPICS_MIGRATION, CURRENT_DATABASE_VERSION);
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : TOPICS_CREATE_STATEMENT_V7) {
            db.execSQL(sql);
        }
    }

    // Clear the database. Ensure there is no stale database with a different version existed.
    private static void clearDatabase(@NonNull Context context) {
        File databaseFile = context.getDatabasePath(DATABASE_NAME_TOPICS_MIGRATION);
        if (databaseFile.exists()) {
            assertThat(databaseFile.delete()).isTrue();
        }
    }

    // ReturnedTopic column schema as of V7.
    private static final String CREATE_TABLE_RETURNED_TOPIC_V7 =
            "CREATE TABLE "
                    + TopicsTables.ReturnedTopicContract.TABLE
                    + "("
                    + TopicsTables.ReturnedTopicContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + TopicsTables.ReturnedTopicContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + TopicsTables.ReturnedTopicContract.APP
                    + " TEXT NOT NULL, "
                    + TopicsTables.ReturnedTopicContract.SDK
                    + " TEXT NOT NULL, "
                    + TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + TopicsTables.ReturnedTopicContract.MODEL_VERSION
                    + " INTEGER NOT NULL, "
                    + TopicsTables.ReturnedTopicContract.TOPIC
                    + " INTEGER NOT NULL"
                    + ")";

    // This will create tables for DB V7.
    private static final List<String> TOPICS_CREATE_STATEMENT_V7 =
            List.of(
                    CREATE_TABLE_TOPICS_TAXONOMY, CREATE_TABLE_APP_CLASSIFICATION_TOPICS,
                    CREATE_TABLE_TOP_TOPICS, CREATE_TABLE_RETURNED_TOPIC_V7,
                    CREATE_TABLE_USAGE_HISTORY, CREATE_TABLE_APP_USAGE_HISTORY,
                    CREATE_TABLE_CALLER_CAN_LEARN_TOPICS, CREATE_TABLE_BLOCKED_TOPICS,
                    CREATE_TABLE_EPOCH_ORIGIN, CREATE_TABLE_TOPIC_CONTRIBUTORS);
}
