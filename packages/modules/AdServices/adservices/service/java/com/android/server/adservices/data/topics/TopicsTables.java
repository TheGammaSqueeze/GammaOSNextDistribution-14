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

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * Container class for Topics API table definitions and constants.
 *
 * @hide
 */
public final class TopicsTables {
    /**
     * A dummy model version which is used where the real model version is not needed, for example
     * in the blocked topics.
     */
    public static final long DUMMY_MODEL_VERSION = 1L;

    /**
     * Table to store all blocked {@link android.adservices.topics.Topic}s. Blocked topics are
     * controlled by user.
     */
    public interface BlockedTopicsContract {
        String TABLE = "blocked_topics";
        String ID = "_id";
        String TAXONOMY_VERSION = "taxonomy_version";
        String TOPIC = "topic";
        String USER = "user";
    }

    /** Create Statement for the blocked topics table. */
    @VisibleForTesting
    public static final String CREATE_TABLE_BLOCKED_TOPICS =
            "CREATE TABLE "
                    + BlockedTopicsContract.TABLE
                    + "("
                    + BlockedTopicsContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + BlockedTopicsContract.USER
                    + " INTEGER, "
                    + BlockedTopicsContract.TAXONOMY_VERSION
                    + " INTEGER, "
                    + BlockedTopicsContract.TOPIC
                    + " INTEGER"
                    + ")";

    /** Consolidated list of create statements for all tables. */
    public static final List<String> CREATE_STATEMENTS = List.of(CREATE_TABLE_BLOCKED_TOPICS);

    // Private constructor to prevent instantiation.
    private TopicsTables() {}
}
