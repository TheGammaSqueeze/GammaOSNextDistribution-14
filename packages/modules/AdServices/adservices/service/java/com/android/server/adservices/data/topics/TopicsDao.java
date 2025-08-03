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

package com.android.server.adservices.data.topics;

import static com.android.server.adservices.data.topics.TopicsTables.DUMMY_MODEL_VERSION;

import android.adservices.topics.Topic;
import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.LogUtil;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Data Access Object for the Topics API in adservices system service.
 *
 * @hide
 */
public class TopicsDao {
    private static final Object LOCK = new Object();
    private static TopicsDao sSingleton;

    private final TopicsDbHelper mTopicsDbHelper;

    /**
     * It's only public to unit test.
     *
     * @param topicsDbHelper The database to query
     */
    @VisibleForTesting
    public TopicsDao(TopicsDbHelper topicsDbHelper) {
        mTopicsDbHelper = topicsDbHelper;
    }

    /** Returns an instance of the TopicsDAO given a context. */
    @NonNull
    public static TopicsDao getInstance(@NonNull Context context) {
        synchronized (LOCK) {
            if (sSingleton == null) {
                sSingleton = new TopicsDao(TopicsDbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    /**
     * Record {@link Topic} which should be blocked to a specific user.
     *
     * @param topics {@link Topic}s to block.
     * @param userIdentifier the user id to record the blocked topic
     */
    public void recordBlockedTopic(@NonNull List<Topic> topics, int userIdentifier) {
        Objects.requireNonNull(topics);
        SQLiteDatabase db = mTopicsDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Topic topic : topics) {
            // Create a new map of values, where column names are the keys
            ContentValues values = new ContentValues();
            values.put(TopicsTables.BlockedTopicsContract.TOPIC, topic.getTopicId());
            values.put(
                    TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION,
                    topic.getTaxonomyVersion());
            values.put(TopicsTables.BlockedTopicsContract.USER, userIdentifier);

            try {
                db.insert(
                        TopicsTables.BlockedTopicsContract.TABLE, /* nullColumnHack */
                        null,
                        values);
            } catch (SQLException e) {
                LogUtil.e("Failed to record blocked topic." + e.getMessage());
            }
        }
    }

    /**
     * Remove blocked {@link Topic}.
     *
     * @param topic blocked {@link Topic} to remove.
     * @param userIdentifier the user id to remove the blocked topic
     */
    public void removeBlockedTopic(@NonNull Topic topic, int userIdentifier) {
        Objects.requireNonNull(topic);
        SQLiteDatabase db = mTopicsDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Where statement for: topics, taxonomyVersion
        String whereClause =
                TopicsTables.BlockedTopicsContract.USER
                        + " = ?"
                        + " AND "
                        + TopicsTables.BlockedTopicsContract.TOPIC
                        + " = ?"
                        + " AND "
                        + TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION
                        + " = ?";
        String[] whereArgs = {
            String.valueOf(userIdentifier),
            String.valueOf(topic.getTopicId()),
            String.valueOf(topic.getTaxonomyVersion())
        };

        try {
            db.delete(TopicsTables.BlockedTopicsContract.TABLE, whereClause, whereArgs);
        } catch (SQLException e) {
            LogUtil.e("Failed to remove blocked topic." + e.getMessage());
        }
    }

    /**
     * Get a {@link List} of {@link Topic}s which are blocked.
     *
     * @param userIdentifier the user id to get all blocked topics
     * @return {@link List} a {@link List} of blocked {@link Topic}s.
     */
    @NonNull
    public Set<Topic> retrieveAllBlockedTopics(int userIdentifier) {
        SQLiteDatabase db = mTopicsDbHelper.safeGetReadableDatabase();
        Set<Topic> blockedTopics = new HashSet<>();
        if (db == null) {
            return blockedTopics;
        }

        String selection = TopicsTables.BlockedTopicsContract.USER + " = ?";
        String[] selectionArgs = {String.valueOf(userIdentifier)};

        try (Cursor cursor =
                db.query(
                        /* distinct= */ true,
                        TopicsTables.BlockedTopicsContract.TABLE, // The table to query
                        null, // Get all columns (null for all)
                        selection,
                        selectionArgs,
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
                int topicInt =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.TOPIC));
                Topic topic = new Topic(taxonomyVersion, DUMMY_MODEL_VERSION, topicInt);

                blockedTopics.add(topic);
            }
        }

        return blockedTopics;
    }

    /**
     * Delete all blocked topics that belongs to a user.
     *
     * @param userIdentifier the user id to delete data for
     */
    public void clearAllBlockedTopicsOfUser(int userIdentifier) {
        SQLiteDatabase db = mTopicsDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        String whereClause = TopicsTables.BlockedTopicsContract.USER + " = ?";
        String[] whereArgs = {
            String.valueOf(userIdentifier),
        };

        try {
            db.delete(TopicsTables.BlockedTopicsContract.TABLE, whereClause, whereArgs);
        } catch (SQLException e) {
            LogUtil.e("Failed to remove all blocked topics." + e.getMessage());
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix, String[] args) {
        mTopicsDbHelper.dump(writer, prefix, args);
    }
}
