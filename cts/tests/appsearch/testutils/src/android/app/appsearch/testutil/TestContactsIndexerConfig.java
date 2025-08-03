/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appsearch.testutil;

import com.android.server.appsearch.contactsindexer.ContactsIndexerConfig;

/**
 * Contacts Indexer configuration for testing.
 *
 * <p>It simply returns default values.
 */
public class TestContactsIndexerConfig implements ContactsIndexerConfig {
    @Override
    public boolean isContactsIndexerEnabled() {
        return DEFAULT_CONTACTS_INDEXER_ENABLED;
    }

    @Override
    public int getContactsFirstRunIndexingLimit() {
        return DEFAULT_CONTACTS_FIRST_RUN_INDEXING_LIMIT;
    }

    @Override
    public long getContactsFullUpdateIntervalMillis() {
        return DEFAULT_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS;
    }

    @Override
    public int getContactsFullUpdateLimit() {
        return DEFAULT_CONTACTS_FULL_UPDATE_INDEXING_LIMIT;
    }

    @Override
    public int getContactsDeltaUpdateLimit() {
        return DEFAULT_CONTACTS_DELTA_UPDATE_INDEXING_LIMIT;
    }

    @Override
    public boolean shouldIndexFirstMiddleAndLastNames() {
        return DEFAULT_CONTACTS_INDEX_FIRST_MIDDLE_AND_LAST_NAMES;
    }

    @Override
    public boolean shouldKeepUpdatingOnError() {
        return DEFAULT_CONTACTS_KEEP_UPDATING_ON_ERROR;
    }
}
