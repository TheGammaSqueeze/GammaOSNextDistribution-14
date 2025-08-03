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

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.topics.TopicsTables;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Migrator to perform DB schema change to version 8 in Topics API. Version 8 is to add logged_topic
 * column to ReturnedTopic table.
 */
public class TopicsDbMigratorV8 extends AbstractTopicsDbMigrator {
    private static final int DATABASE_VERSION_V8 = 8;

    // A temporary table to store old version ReturnedTopic Table during upgrading.
    private static final String TEMP_RETURNED_TOPIC_TABLE_NAME = "temp_returned_topic_table";

    // Selects V7 ReturnedTopic table's columns during upgrading.
    private static final String RETURNED_TOPIC_TABLE_COLUMNS_V7 =
            "_id, epoch_id, app, sdk, taxonomy_version, model_version, topic";

    // Following go/gmscore-flagging-best-practices, we should create the column
    // if it doesn't exist and clear it if it already exists when upgrading and
    // do nothing when downgrading.
    private static final String QUERY_TO_DROP_LOGGED_TOPIC_COLUMN =
            String.format(
                    "ALTER TABLE %1$s DROP COLUMN %2$s;",
                    TopicsTables.ReturnedTopicContract.TABLE,
                    TopicsTables.ReturnedTopicContract.LOGGED_TOPIC);
    private static final String QUERY_TO_ADD_LOGGED_TOPIC_COLUMN =
            String.format(
                    "ALTER TABLE %1$s ADD COLUMN %2$s INTEGER;",
                    TopicsTables.ReturnedTopicContract.TABLE,
                    TopicsTables.ReturnedTopicContract.LOGGED_TOPIC);
    private static final String QUERY_TO_CLEAN_LOGGED_TOPIC_COLUMN =
            String.format(
                    "UPDATE %1$s SET %2$s = NULL;",
                    TopicsTables.ReturnedTopicContract.TABLE,
                    TopicsTables.ReturnedTopicContract.LOGGED_TOPIC);

    public TopicsDbMigratorV8() {
        super(DATABASE_VERSION_V8);
    }

    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public void performMigration(SQLiteDatabase db) {
        // Drop column logged_topic to ReturnedTopic table if exists.
        try {
            db.execSQL(QUERY_TO_DROP_LOGGED_TOPIC_COLUMN);
        } catch (SQLException sqlException) {
            if (sqlException.getMessage().contains("no such column: \"logged_topic\"")) {
                // Do nothing, because logged_topic has been cleared.
            }
        }

        // Add column logged_topic to ReturnedTopic table if not exists.
        try {
            db.execSQL(QUERY_TO_ADD_LOGGED_TOPIC_COLUMN);
        } catch (SQLException sqlException) {
            if (sqlException.getMessage().contains("duplicate column name: logged_topic")) {
                // Clean column logged_topic.
                db.execSQL(QUERY_TO_CLEAN_LOGGED_TOPIC_COLUMN);
            }
        }
    }
}
