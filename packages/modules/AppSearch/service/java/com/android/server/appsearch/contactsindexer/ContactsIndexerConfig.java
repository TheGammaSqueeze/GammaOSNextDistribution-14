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

package com.android.server.appsearch.contactsindexer;

import java.util.concurrent.TimeUnit;

/**
 * An interface which exposes config flags to Contacts Indexer.
 *
 * <p>Implementations of this interface must be thread-safe.
 *
 * @hide
 */
public interface ContactsIndexerConfig {
    boolean DEFAULT_CONTACTS_INDEXER_ENABLED = true;
    int DEFAULT_CONTACTS_FIRST_RUN_INDEXING_LIMIT = 1000;
    long DEFAULT_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(30); // 30 days.
    int DEFAULT_CONTACTS_FULL_UPDATE_INDEXING_LIMIT = 10_000;
    int DEFAULT_CONTACTS_DELTA_UPDATE_INDEXING_LIMIT = 1000;
    boolean DEFAULT_CONTACTS_INDEX_FIRST_MIDDLE_AND_LAST_NAMES = false;
    boolean DEFAULT_CONTACTS_KEEP_UPDATING_ON_ERROR = true;

    /** Returns whether Contacts Indexer is enabled. */
    boolean isContactsIndexerEnabled();

    /**
     * Returns the maximum number of CP2 contacts indexed during first run.
     *
     * <p>This value will limit the amount of processing performed when the device upgrades from
     * Android S to T with Contacts Indexer enabled.
     */
    int getContactsFirstRunIndexingLimit();

    /**
     * Returns the minimum internal in millis for two consecutive full update. This is only checked
     * once after reach boot.
     */
    long getContactsFullUpdateIntervalMillis();

    /**
     * Returns the maximum number of CP2 contacts indexed during a full update.
     *
     * <p>The value will be used as a LIMIT for querying CP2 during full update.
     */
    int getContactsFullUpdateLimit();

    /**
     * Returns the maximum number of CP2 contacts indexed during a delta update.
     *
     * <p>The value will be used as a LIMIT for querying CP2 during the delta update.
     */
    int getContactsDeltaUpdateLimit();

    /**
     * Returns whether the first, middle and last names of a contact should be indexed in addition
     * to the full name.
     */
    boolean shouldIndexFirstMiddleAndLastNames();

    /**
     * Returns whether full and delta updates should continue on error.
     */
    boolean shouldKeepUpdatingOnError();
}
