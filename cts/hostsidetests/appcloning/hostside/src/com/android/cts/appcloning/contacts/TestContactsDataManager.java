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

package com.android.cts.appcloning.contacts;

import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.getDeleteContactCommand;
import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.getInsertTestContactCommand;
import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.getQueryTestContactsCommand;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.util.ArrayList;
import java.util.List;

final class TestContactsDataManager {

    private static final String RAW_CONTACTS_ENDPOINT = "raw_contacts";
    private static final String DATA_ENDPOINT = "data";
    private static final String TEST_ACCOUNT_NAME = "test@test.com";
    private static final String TEST_CUSTOM_RINGTONE = "custom_1";
    private static final String TEST_CONTACT_PHONE_NUMBER = "+123456789";
    private static final String TEST_CONTACT_PHONE_NUMBER_MIMETYPE =
            "vnd.android.cursor.item/phone_v2";
    private static final String ACCOUNT_NAME_COLUMN = "account_name";
    private static final String ACCOUNT_TYPE_COLUMN = "account_type";
    private static final String CUSTOM_RINGTONE_COLUMN = "custom_ringtone";
    private static final String RAW_CONTACT_ID_COLUMN = "raw_contact_id";
    private static final String MIMETYPE_COLUMN = "mimetype";
    private static final String DATA1_COLUMN = "data1";

    private final ITestDevice mTestDevice;

    TestContactsDataManager(ITestDevice device) {
        this.mTestDevice = device;
    }

    static class TestRawContact {
        public final String accountName;
        public final String accountType;
        public final String customRingtone;

        TestRawContact(String accountName, String accountType, String customRingtone) {
            this.accountName = accountName;
            this.accountType = accountType;
            this.customRingtone = customRingtone;
        }
    }

    static class TestRawContactData {
        public final String rawContactId;
        public final String mimeType;
        public final String data1;

        TestRawContactData(String rawContactId, String mimeType, String data1) {
            this.rawContactId = rawContactId;
            this.mimeType = mimeType;
            this.data1 = data1;
        }
    }

    static class TestContact {
        public final TestRawContact rawContact;
        public final TestRawContactData[] rawContactDataList;

        TestContact(TestRawContact rawContact, TestRawContactData[] rawContactDataList) {
            this.rawContact = rawContact;
            this.rawContactDataList = rawContactDataList;
        }
    }

    private List<ContactsShellCommandHelper.ColumnBindings> getTestRawContactContentValues(
            String testAccountType, String testAccountName, String customRingtone) {
        List<ContactsShellCommandHelper.ColumnBindings> testContactBindings = new ArrayList<>();
        testContactBindings.add(new ContactsShellCommandHelper.ColumnBindings(ACCOUNT_NAME_COLUMN,
                testAccountName, ContactsShellCommandHelper.ColumnBindings.Type.STRING));
        testContactBindings.add(new ContactsShellCommandHelper.ColumnBindings(ACCOUNT_TYPE_COLUMN,
                testAccountType, ContactsShellCommandHelper.ColumnBindings.Type.STRING));
        testContactBindings.add(new ContactsShellCommandHelper.ColumnBindings(
                CUSTOM_RINGTONE_COLUMN, customRingtone,
                ContactsShellCommandHelper.ColumnBindings.Type.STRING));
        return testContactBindings;
    }

    private List<ContactsShellCommandHelper.ColumnBindings> getTestContactDataValues(
            String rawContactId, String mimeType, String data1) {
        List<ContactsShellCommandHelper.ColumnBindings> testContactBindings = new ArrayList<>();
        testContactBindings.add(new ContactsShellCommandHelper.ColumnBindings(RAW_CONTACT_ID_COLUMN,
                rawContactId, ContactsShellCommandHelper.ColumnBindings.Type.INT));
        testContactBindings.add(new ContactsShellCommandHelper.ColumnBindings(MIMETYPE_COLUMN,
                mimeType, ContactsShellCommandHelper.ColumnBindings.Type.STRING));
        testContactBindings.add(new ContactsShellCommandHelper.ColumnBindings(DATA1_COLUMN,
                data1, ContactsShellCommandHelper.ColumnBindings.Type.STRING));
        return testContactBindings;
    }

    private String getRawContactIdFromQueryResult(String queryResult) {
        String[] tokens = queryResult.split(", ");
        for (String token : tokens) {
            if (token.startsWith("_id=")) {
                return token.substring("_id=".length());
            }
        }
        return null;
    }

    private TestRawContactData insertDataForRawContact(ITestDevice device, String userId,
            String rawContactId) throws DeviceNotAvailableException {
        // Insert a test raw contact through the CP2 corresponding to the userId
        device.executeShellCommand(getInsertTestContactCommand(DATA_ENDPOINT,
                getTestContactDataValues(
                        rawContactId,
                        TEST_CONTACT_PHONE_NUMBER_MIMETYPE,
                        TEST_CONTACT_PHONE_NUMBER),
                userId));
        return new TestRawContactData(rawContactId, TEST_CONTACT_PHONE_NUMBER_MIMETYPE,
                TEST_CONTACT_PHONE_NUMBER);
    }

    private String queryRawContactForTestAccount(ITestDevice device, String userId,
            String testAccountType)
            throws DeviceNotAvailableException {
        return device.executeShellCommand(getQueryTestContactsCommand(RAW_CONTACTS_ENDPOINT,
                        String.format("\"%s='%s'\"", ACCOUNT_TYPE_COLUMN, testAccountType),
                        userId));
    }

    private void assertTestContactsAreCleanedUp(String userId, String testAccountType)
            throws DeviceNotAvailableException {
        String queryResult =
                mTestDevice.executeShellCommand(getQueryTestContactsCommand(RAW_CONTACTS_ENDPOINT,
                        String.format("\"%s='%s'\"", ACCOUNT_TYPE_COLUMN, testAccountType),
                        userId));
        assertThat(queryResult).startsWith("No result found.\n");
    }

    public TestRawContact insertRawContactForTestAccount(String userId, String testAccountType)
            throws DeviceNotAvailableException {
        // Insert a test raw contact through the CP2 corresponding to the userId
        mTestDevice.executeShellCommand(getInsertTestContactCommand(RAW_CONTACTS_ENDPOINT,
                getTestRawContactContentValues(testAccountType,
                        TEST_ACCOUNT_NAME,
                        TEST_CUSTOM_RINGTONE
                ), userId));
        return new TestRawContact(TEST_ACCOUNT_NAME, testAccountType, TEST_CUSTOM_RINGTONE);
    }

    public TestContact insertTestContactForManagedProfile(String managedProfileUserId,
            String testAccountType) throws DeviceNotAvailableException {
        // Insert a raw contact in the managed profile contacts database corresponding to the
        // test account type provided
        TestRawContact testRawContact =
                insertRawContactForTestAccount(managedProfileUserId, testAccountType);

        // Fetch the id of the raw contact row added above
        String queryResult = queryRawContactForTestAccount(mTestDevice, managedProfileUserId,
                testAccountType);
        String rawContactId = getRawContactIdFromQueryResult(queryResult);
        assertThat(rawContactId).isNotNull();
        assertThat(rawContactId).isNotEmpty();

        // Insert a row in the data table corresponding to the raw contact added above
        TestRawContactData testRawContactData =
                insertDataForRawContact(mTestDevice, managedProfileUserId, rawContactId);
        TestRawContactData[] testRawContactDataList = new TestRawContactData[]{testRawContactData};
        return new TestContact(testRawContact, testRawContactDataList);
    }

    public void cleanupTestContacts(String userId, String testAccountType)
            throws DeviceNotAvailableException {
        // Delete the test contact inserted earlier through CP2 corresponding to the userId
        mTestDevice.executeShellCommand(getDeleteContactCommand(RAW_CONTACTS_ENDPOINT,
                String.format("\"%s='%s'\"", ACCOUNT_TYPE_COLUMN, testAccountType),
                userId));
        assertTestContactsAreCleanedUp(userId, testAccountType);
    }
}

