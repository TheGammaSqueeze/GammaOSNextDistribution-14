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

import android.provider.DeviceConfig;

/**
 * Implementation of {@link ContactsIndexerConfig} using {@link DeviceConfig}.
 *
 * <p>It contains all the keys for flags related to Contacts Indexer.
 *
 * <p>This class is thread-safe.
 *
 * @hide
 */
public class FrameworkContactsIndexerConfig implements ContactsIndexerConfig {
    static final String KEY_CONTACTS_INDEXER_ENABLED = "contacts_indexer_enabled";
    static final String KEY_CONTACTS_INSTANT_INDEXING_LIMIT = "contacts_instant_indexing_limit";
    static final String KEY_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS =
            "contacts_full_update_interval_millis";
    static final String KEY_CONTACTS_FULL_UPDATE_LIMIT = "contacts_indexer_full_update_limit";
    static final String KEY_CONTACTS_DELTA_UPDATE_LIMIT = "contacts_indexer_delta_update_limit";
    public static final String KEY_CONTACTS_INDEX_FIRST_MIDDLE_AND_LAST_NAMES =
            "contacts_index_first_middle_and_last_names";
    static final String KEY_CONTACTS_KEEP_UPDATING_ON_ERROR =
            "contacts_keep_updating_on_error";

    @Override
    public boolean isContactsIndexerEnabled() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_INDEXER_ENABLED,
                DEFAULT_CONTACTS_INDEXER_ENABLED);
    }

    @Override
    public int getContactsFirstRunIndexingLimit() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_INSTANT_INDEXING_LIMIT, DEFAULT_CONTACTS_FIRST_RUN_INDEXING_LIMIT);
    }

    @Override
    public long getContactsFullUpdateIntervalMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS,
                DEFAULT_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS);
    }

    @Override
    public int getContactsFullUpdateLimit() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_FULL_UPDATE_LIMIT,
                DEFAULT_CONTACTS_FULL_UPDATE_INDEXING_LIMIT);
    }

    @Override
    public int getContactsDeltaUpdateLimit() {
        // TODO(b/227419499) Based on the metrics, we can tweak this number. Right now it is same
        //  as the instant indexing limit, which is 1,000. From our stats in GMSCore, 95th
        //  percentile for number of contacts on the device is around 2000 contacts.
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_DELTA_UPDATE_LIMIT,
                DEFAULT_CONTACTS_DELTA_UPDATE_INDEXING_LIMIT);
    }

    @Override
    public boolean shouldIndexFirstMiddleAndLastNames() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_INDEX_FIRST_MIDDLE_AND_LAST_NAMES,
                DEFAULT_CONTACTS_INDEX_FIRST_MIDDLE_AND_LAST_NAMES);
    }

    @Override
    public boolean shouldKeepUpdatingOnError() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_CONTACTS_KEEP_UPDATING_ON_ERROR,
                DEFAULT_CONTACTS_KEEP_UPDATING_ON_ERROR);
    }
}
