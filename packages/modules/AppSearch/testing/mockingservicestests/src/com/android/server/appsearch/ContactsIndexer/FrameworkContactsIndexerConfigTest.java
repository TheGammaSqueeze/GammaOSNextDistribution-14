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

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Rule;
import org.junit.Test;

public class FrameworkContactsIndexerConfigTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule
            mDeviceConfigRule = new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void testDefaultValues() {
        ContactsIndexerConfig contactsIndexerConfig = new FrameworkContactsIndexerConfig();

        assertThat(contactsIndexerConfig.isContactsIndexerEnabled()).isTrue();
        assertThat(contactsIndexerConfig.getContactsFirstRunIndexingLimit()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_FIRST_RUN_INDEXING_LIMIT);
        assertThat(contactsIndexerConfig.getContactsFullUpdateIntervalMillis()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS);
        assertThat(contactsIndexerConfig.getContactsFullUpdateLimit()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_FULL_UPDATE_INDEXING_LIMIT);
        assertThat(contactsIndexerConfig.getContactsDeltaUpdateLimit()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_DELTA_UPDATE_INDEXING_LIMIT);
        assertThat(contactsIndexerConfig.shouldIndexFirstMiddleAndLastNames()).isFalse();
    }

    @Test
    public void testCustomizedValues() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkContactsIndexerConfig.KEY_CONTACTS_INDEXER_ENABLED,
                Boolean.toString(false),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkContactsIndexerConfig.KEY_CONTACTS_INSTANT_INDEXING_LIMIT,
                Integer.toString(
                        ContactsIndexerConfig.DEFAULT_CONTACTS_FIRST_RUN_INDEXING_LIMIT + 1),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkContactsIndexerConfig.KEY_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS,
                Long.toString(
                        ContactsIndexerConfig.DEFAULT_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS + 1),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkContactsIndexerConfig.KEY_CONTACTS_FULL_UPDATE_LIMIT,
                Long.toString(
                        ContactsIndexerConfig.DEFAULT_CONTACTS_FULL_UPDATE_INDEXING_LIMIT + 1),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkContactsIndexerConfig.KEY_CONTACTS_DELTA_UPDATE_LIMIT,
                Long.toString(
                        ContactsIndexerConfig.DEFAULT_CONTACTS_DELTA_UPDATE_INDEXING_LIMIT + 1),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkContactsIndexerConfig.KEY_CONTACTS_INDEX_FIRST_MIDDLE_AND_LAST_NAMES,
                Boolean.toString(false),
                false);

        ContactsIndexerConfig contactsIndexerConfig = new FrameworkContactsIndexerConfig();

        assertThat(contactsIndexerConfig.isContactsIndexerEnabled()).isFalse();
        assertThat(contactsIndexerConfig.getContactsFirstRunIndexingLimit()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_FIRST_RUN_INDEXING_LIMIT + 1);
        assertThat(contactsIndexerConfig.getContactsFullUpdateIntervalMillis()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_FULL_UPDATE_INTERVAL_MILLIS + 1);
        assertThat(contactsIndexerConfig.getContactsFullUpdateLimit()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_FULL_UPDATE_INDEXING_LIMIT + 1);
        assertThat(contactsIndexerConfig.getContactsDeltaUpdateLimit()).isEqualTo(
                ContactsIndexerConfig.DEFAULT_CONTACTS_DELTA_UPDATE_INDEXING_LIMIT + 1);
        assertThat(contactsIndexerConfig.shouldIndexFirstMiddleAndLastNames()).isFalse();
    }
}
