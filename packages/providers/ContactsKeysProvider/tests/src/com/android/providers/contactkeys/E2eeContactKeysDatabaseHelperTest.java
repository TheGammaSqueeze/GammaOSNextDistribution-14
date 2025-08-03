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

package com.android.providers.contactkeys;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.E2eeContactKeysManager;
import android.provider.Flags;
import android.test.ProviderTestCase2;

import com.google.common.collect.ImmutableList;

import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_USER_KEYS)
public class E2eeContactKeysDatabaseHelperTest extends ProviderTestCase2<E2eeContactKeysProvider> {

    private static final String LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    private static final String LOOKUP_KEY_2 = "0r2-8T03DE4644502A2E50";
    private static final String DEVICE_ID = "device_id_value";
    private static final String DEVICE_ID_2 = "device_id_value_2";
    private static final String ACCOUNT_ID = "+1 (555) 555-1234";
    private static final String ACCOUNT_ID_2 = "+1 (123) 456-7895";
    private static final byte[] KEY_VALUE = new byte[] { (byte) 0xba, (byte) 0x8a };
    private static final byte[] KEY_VALUE_2 = new byte[] { (byte) 0x5c, (byte) 0xab };
    private static final byte[] KEY_VALUE_3 = new byte[] { (byte) 0x4a, (byte) 0xa7 };
    private static final String OWNER_PACKAGE_NAME = "com.package.app1";
    private static final String OWNER_PACKAGE_NAME_2 = "com.package.app2";
    private static final long TIME_UPDATED = 1701689265;
    private static final String DISPLAY_NAME = "John";
    private static final String PHONE_NUMBER = "(555) 555-1234";
    private static final String EMAIL_ADDRESS = "example@gmail.com";

    private E2eeContactKeysDatabaseHelper mDbHelper;

    public E2eeContactKeysDatabaseHelperTest() {
        super(E2eeContactKeysProvider.class, E2eeContactKeysProvider.AUTHORITY);
    }

    /**
     * Sets up the test environment before each test method.
     */
    public void setUp() throws Exception {
        super.setUp();
        mDbHelper = ((E2eeContactKeysProvider) getProvider()).mDbHelper;
        mDbHelper.getWritableDatabase().execSQL("DROP TABLE IF EXISTS contactkeys");
        mDbHelper.getWritableDatabase().execSQL("DROP TABLE IF EXISTS selfkeys");
        mDbHelper.onCreate(mDbHelper.getWritableDatabase());
    }

    /**
     *  This method is called after each test method, to clean up the current fixture.
     */
    public void tearDown() throws Exception {
        super.tearDown();
        mDbHelper.removeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);
        mDbHelper.removeContactKey(LOOKUP_KEY, DEVICE_ID_2, ACCOUNT_ID, OWNER_PACKAGE_NAME);
        mDbHelper.removeContactKey(LOOKUP_KEY, DEVICE_ID_2, ACCOUNT_ID, OWNER_PACKAGE_NAME_2);
        mDbHelper.removeContactKey(LOOKUP_KEY_2, DEVICE_ID_2, ACCOUNT_ID_2, OWNER_PACKAGE_NAME);
        mDbHelper.removeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);
    }

    public void testInsertAndGetContactKey() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);
        E2eeContactKeysManager.E2eeContactKey contactKey =
                mDbHelper.getContactKey(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID);
        E2eeContactKeysManager.E2eeContactKey expectedContactKey =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED, DISPLAY_NAME,
                        PHONE_NUMBER, EMAIL_ADDRESS);

        assertEquals(expectedContactKey, contactKey);
    }

    public void testInsertContactKeyNoBasicContactInfo() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, null, null, null);
        E2eeContactKeysManager.E2eeContactKey contactKey =
                mDbHelper.getContactKey(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID);
        E2eeContactKeysManager.E2eeContactKey expectedContactKey =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED, null, null, null);

        assertEquals(expectedContactKey, contactKey);
    }

    public void testUpdateContactKey() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);
        int localVerificationState = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        int remoteVerificationState = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        mDbHelper.updateContactKeyLocalVerificationState(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID,
                ACCOUNT_ID, localVerificationState, TIME_UPDATED + 1);
        mDbHelper.updateContactKeyRemoteVerificationState(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID,
                ACCOUNT_ID, remoteVerificationState, TIME_UPDATED + 2);

        long timeUpdated = TIME_UPDATED + 3;
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE_2, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, timeUpdated, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);
        E2eeContactKeysManager.E2eeContactKey contactKey =
                mDbHelper.getContactKey(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID);

        E2eeContactKeysManager.E2eeContactKey expectedContactKey =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        timeUpdated, KEY_VALUE_2, localVerificationState, remoteVerificationState,
                        DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);

        assertEquals(expectedContactKey, contactKey);
    }

    public void testRemoveContactKey() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);

        int rowsRemoved = mDbHelper.removeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME);

        assertEquals(1, rowsRemoved);

        E2eeContactKeysManager.E2eeContactKey contactKey =
                mDbHelper.getContactKey(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID);

        assertNull(contactKey);
    }

    public void testUpdateLocalVerificationStateContactKey() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);

        int updatedLvs = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        mDbHelper.updateContactKeyLocalVerificationState(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID,
                ACCOUNT_ID, updatedLvs, TIME_UPDATED);
        E2eeContactKeysManager.E2eeContactKey contactKey =
                mDbHelper.getContactKey(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID);

        E2eeContactKeysManager.E2eeContactKey expectedContactKey =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE, updatedLvs,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED, DISPLAY_NAME,
                        PHONE_NUMBER, EMAIL_ADDRESS);

        assertEquals(expectedContactKey, contactKey);
    }

    public void testUpdateRemoteVerificationStateContactKey() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);

        int updatedRvs = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        mDbHelper.updateContactKeyRemoteVerificationState(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID,
                ACCOUNT_ID, updatedRvs, TIME_UPDATED);
        E2eeContactKeysManager.E2eeContactKey contactKey =
                mDbHelper.getContactKey(LOOKUP_KEY, OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID);

        E2eeContactKeysManager.E2eeContactKey expectedContactKey =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED, updatedRvs,
                        DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);

        assertEquals(expectedContactKey, contactKey);
    }

    public void testGetAllContactKeys() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE_2, DEVICE_ID_2, ACCOUNT_ID,
                OWNER_PACKAGE_NAME_2,
                TIME_UPDATED, DISPLAY_NAME, null, null);
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY_2, KEY_VALUE_3, DEVICE_ID_2, ACCOUNT_ID_2,
                OWNER_PACKAGE_NAME, TIME_UPDATED, "Bob", null, null);

        List<E2eeContactKeysManager.E2eeContactKey> contactKeys =
                mDbHelper.getAllContactKeys(LOOKUP_KEY);

        E2eeContactKeysManager.E2eeContactKey expectedContactKey1 =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED, DISPLAY_NAME,
                        PHONE_NUMBER, EMAIL_ADDRESS);
        E2eeContactKeysManager.E2eeContactKey expectedContactKey2 =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID_2, ACCOUNT_ID,
                        OWNER_PACKAGE_NAME_2, TIME_UPDATED, KEY_VALUE_2,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        DISPLAY_NAME, null, null);
        List<E2eeContactKeysManager.E2eeContactKey> expectedContactKeys = ImmutableList.of(
                expectedContactKey1, expectedContactKey2);

        assertEquals(expectedContactKeys.size(), contactKeys.size());
        assertTrue(expectedContactKeys.containsAll(contactKeys));
    }

    public void testGetContactKeysForOwnerPackageName() {
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE, DEVICE_ID, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE_2, DEVICE_ID_2, ACCOUNT_ID,
                OWNER_PACKAGE_NAME, TIME_UPDATED, DISPLAY_NAME, null, null);
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY, KEY_VALUE_3, DEVICE_ID_2, ACCOUNT_ID,
                OWNER_PACKAGE_NAME_2, TIME_UPDATED, "Bob", null, null);
        mDbHelper.updateOrInsertContactKey(LOOKUP_KEY_2, KEY_VALUE_3, DEVICE_ID_2, ACCOUNT_ID_2,
                OWNER_PACKAGE_NAME, TIME_UPDATED, "Charlie", null, null);

        List<E2eeContactKeysManager.E2eeContactKey> contactKeys =
                mDbHelper.getContactKeysForOwnerPackageName(LOOKUP_KEY, OWNER_PACKAGE_NAME);

        E2eeContactKeysManager.E2eeContactKey expectedContactKey1 =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        DISPLAY_NAME, PHONE_NUMBER, EMAIL_ADDRESS);
        E2eeContactKeysManager.E2eeContactKey expectedContactKey2 =
                new E2eeContactKeysManager.E2eeContactKey(DEVICE_ID_2, ACCOUNT_ID,
                        OWNER_PACKAGE_NAME, TIME_UPDATED, KEY_VALUE_2,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED,
                        DISPLAY_NAME, null, null);
        List<E2eeContactKeysManager.E2eeContactKey> expectedContactKeys = ImmutableList.of(
                expectedContactKey1, expectedContactKey2);

        assertEquals(expectedContactKeys.size(), contactKeys.size());
        assertTrue(expectedContactKeys.containsAll(contactKeys));
    }

    public void testInsertAndGetSelfKey() {
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                TIME_UPDATED);
        E2eeContactKeysManager.E2eeSelfKey selfKey =
                mDbHelper.getSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);
        E2eeContactKeysManager.E2eeSelfKey expectedSelfKey =
                new E2eeContactKeysManager.E2eeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);

        assertEquals(expectedSelfKey, selfKey);
    }

    public void testGetAllSelfKeys() {
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                TIME_UPDATED);
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE_2, DEVICE_ID_2, ACCOUNT_ID_2,
                OWNER_PACKAGE_NAME_2, TIME_UPDATED);

        List<E2eeContactKeysManager.E2eeSelfKey> selfKeys =
                mDbHelper.getAllSelfKeys();

        E2eeContactKeysManager.E2eeSelfKey expectedSelfKey1 =
                new E2eeContactKeysManager.E2eeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        E2eeContactKeysManager.E2eeSelfKey expectedSelfKey2 =
                new E2eeContactKeysManager.E2eeSelfKey(DEVICE_ID_2, ACCOUNT_ID_2,
                        OWNER_PACKAGE_NAME_2, TIME_UPDATED, KEY_VALUE_2,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        List<E2eeContactKeysManager.E2eeSelfKey> expectedSelfKeys = ImmutableList.of(
                expectedSelfKey1, expectedSelfKey2);

        assertEquals(expectedSelfKeys.size(), selfKeys.size());
        assertTrue(expectedSelfKeys.containsAll(selfKeys));
    }

    public void testGetSelfKeysForOwnerPackageName() {
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                TIME_UPDATED);
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE_2, DEVICE_ID_2, ACCOUNT_ID_2,
                OWNER_PACKAGE_NAME_2, TIME_UPDATED);

        List<E2eeContactKeysManager.E2eeSelfKey> selfKeys =
                mDbHelper.getSelfKeysForOwnerPackageName(OWNER_PACKAGE_NAME);

        E2eeContactKeysManager.E2eeSelfKey expectedSelfKey1 =
                new E2eeContactKeysManager.E2eeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        TIME_UPDATED, KEY_VALUE,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        List<E2eeContactKeysManager.E2eeSelfKey> expectedSelfKeys =
                ImmutableList.of(expectedSelfKey1);

        assertEquals(expectedSelfKeys.size(), selfKeys.size());
        assertTrue(expectedSelfKeys.containsAll(selfKeys));
    }

    public void testUpdateSelfKey() {
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                TIME_UPDATED);

        long timeUpdated = TIME_UPDATED + 1;
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE_2, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                timeUpdated);
        E2eeContactKeysManager.E2eeSelfKey selfKey =
                mDbHelper.getSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);

        E2eeContactKeysManager.E2eeSelfKey expectedSelfKey =
                new E2eeContactKeysManager.E2eeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        timeUpdated, KEY_VALUE_2,
                        E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);

        assertEquals(expectedSelfKey, selfKey);
    }

    public void testUpdateRemoteVerificationStateSelfKey() {
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                TIME_UPDATED);

        long timeUpdated = TIME_UPDATED + 1;
        int updatedRvs = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        mDbHelper.updateSelfKeyRemoteVerificationState(OWNER_PACKAGE_NAME, DEVICE_ID, ACCOUNT_ID,
                updatedRvs, timeUpdated);
        E2eeContactKeysManager.E2eeSelfKey selfKey =
                mDbHelper.getSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);

        E2eeContactKeysManager.E2eeSelfKey expectedSelfKey =
                new E2eeContactKeysManager.E2eeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                        timeUpdated, KEY_VALUE, updatedRvs);

        assertEquals(expectedSelfKey, selfKey);
    }

    public void testRemoveSelfKey() {
        mDbHelper.updateOrInsertSelfKey(KEY_VALUE, DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME,
                TIME_UPDATED);

        mDbHelper.removeSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);
        E2eeContactKeysManager.E2eeSelfKey selfKey =
                mDbHelper.getSelfKey(DEVICE_ID, ACCOUNT_ID, OWNER_PACKAGE_NAME);

        assertNull(selfKey);
    }
}
