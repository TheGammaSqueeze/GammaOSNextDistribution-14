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


package com.android.car.bluetooth;

import static com.android.car.bluetooth.FastPairAccountKeyStorage.AccountKey;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

/**
 * Unit tests for {@link FastPairAccountKeyStorage}
 *
 * Run: atest FastPairAccountKeyStorageTest
 */
@RunWith(MockitoJUnitRunner.class)
public class FastPairAccountKeyStorageTest {
    private static final String KEY_NUM_ACCOUNT_KEYS = "AccountKeysCount";

    private static final int TEST_SIZE = 5;
    private static final int TEST_SIZE_TOO_SMALL = 3;
    private static final byte[] TEST_ACCOUNT_KEY_1 = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
            0x77, (byte) 0x88, (byte) 0x99, 0x00, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
            (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};
    private static final byte[] TEST_ACCOUNT_KEY_2 = new byte[]{0x11, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};
    private static final byte[] TEST_ACCOUNT_KEY_3 = new byte[]{0x04, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};
    private static final byte[] TEST_ACCOUNT_KEY_4 = new byte[]{0x05, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};
    private static final byte[] TEST_ACCOUNT_KEY_5 = new byte[]{0x06, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};
    private static final byte[] TEST_ACCOUNT_KEY_6 = new byte[]{0x07, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};

    @Mock Context mMockContext;
    @Mock UserManager mMockUserManager;
    @Mock SharedPreferences mMockSharedPreferences;
    @Mock SharedPreferences.Editor mMockSharedPreferencesEditor;

    private int mSharedPreferencesContentCount = 0;
    private Map<String, String> mSharedPreferencesContent;

    private FastPairAccountKeyStorage mFastPairAccountKeyStorage;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mMockSharedPreferences);
        when(mMockSharedPreferences.edit()).thenReturn(mMockSharedPreferencesEditor);

        // Mock out Shared Preferences and route calls to our internal map and int variable
        mSharedPreferencesContentCount = 0;
        mSharedPreferencesContent = new HashMap<>();

        // SharedPreferencesEditor.putInt
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            int value = (Integer) invocation.getArgument(1);
            if (KEY_NUM_ACCOUNT_KEYS.equals(key)) {
                mSharedPreferencesContentCount = value;
            }
            return invocation.getMock();
        }).when(mMockSharedPreferencesEditor).putInt(anyString(), anyInt());

        // SharedPreferencesEditor.putString
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            String value = (String) invocation.getArgument(1);
            mSharedPreferencesContent.put(key, value);
            return invocation.getMock();
        }).when(mMockSharedPreferencesEditor).putString(anyString(), anyString());

        // SharedPreferencesEditor.remove
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            mSharedPreferencesContent.remove(key);
            return invocation.getMock();
        }).when(mMockSharedPreferencesEditor).remove(anyString());

        // SharedPreferences.getInt
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            int defaultValue = (Integer) invocation.getArgument(1);
            if (KEY_NUM_ACCOUNT_KEYS.equals(key)) {
                return mSharedPreferencesContentCount;
            }
            return defaultValue;
        }).when(mMockSharedPreferences).getInt(anyString(), anyInt());

        // SharedPreferences.getString
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            String defaultValue = (String) invocation.getArgument(1);
            return mSharedPreferencesContent.getOrDefault(key, defaultValue);
        }).when(mMockSharedPreferences).getString(anyString(), nullable(String.class));
    }

    private void setUserUnlocked(boolean state) {
        when(mMockUserManager.isUserUnlocked()).thenReturn(state);
    }

    private void setPersistedKeys(List<AccountKey> keys) {
        assertThat(keys).isNotNull();
        mSharedPreferencesContent.clear();

        mSharedPreferencesContentCount = keys.size();
        int i = 0;
        for (AccountKey key : keys) {
            String keyString = new BigInteger(key.toBytes()).toString();
            String index = Integer.toString(i++);
            mSharedPreferencesContent.put(index, keyString);
        }
    }

    private void assertPersistedKeys(List<AccountKey> expected) {
        assertThat(expected).isNotNull();
        assertThat(mSharedPreferencesContentCount).isEqualTo(expected.size());
        assertThat(mSharedPreferencesContent.size()).isEqualTo(expected.size());
        int i = 0;
        for (AccountKey key : expected) {
            String keyExpected = new BigInteger(key.toBytes()).toString();
            String keyActual = mSharedPreferencesContent.getOrDefault(Integer.toString(i++), null);
            assertThat(keyActual).isNotNull();
            assertThat(keyActual).isEqualTo(keyExpected);
        }
    }

    @Test
    public void testAccountKeyCreateFromBytes_succeeds() {
        AccountKey key = new AccountKey(TEST_ACCOUNT_KEY_1);
        assertThat(key).isNotNull();
        assertThat(key.toBytes()).isEqualTo(TEST_ACCOUNT_KEY_1);
        assertThat(key.getKeySpec()).isEqualTo(new SecretKeySpec(TEST_ACCOUNT_KEY_1, "AES"));
        assertThat(key.toString()).isNotNull();
    }

    @Test
    public void testAccountKeyCreateFromString_succeeds() {
        AccountKey key = new AccountKey(new BigInteger(TEST_ACCOUNT_KEY_1).toString());
        assertThat(key).isNotNull();
        assertThat(key.toBytes()).isEqualTo(TEST_ACCOUNT_KEY_1);
        assertThat(key.getKeySpec()).isEqualTo(new SecretKeySpec(TEST_ACCOUNT_KEY_1, "AES"));
        assertThat(key.toString()).isNotNull();
    }

    @Test
    public void testAccountKeyHashCodeSameKey_hashCodeMatches() {
        AccountKey key = new AccountKey(TEST_ACCOUNT_KEY_1);
        AccountKey keyCopy = new AccountKey(TEST_ACCOUNT_KEY_1);
        assertThat(key.hashCode()).isEqualTo(keyCopy.hashCode());
    }

    @Test
    public void testAccountKeyHashCodeDifferentKey_hashCodeDoesntMatch() {
        AccountKey key = new AccountKey(TEST_ACCOUNT_KEY_1);
        AccountKey keyDifferent = new AccountKey(TEST_ACCOUNT_KEY_2);
        assertThat(key.hashCode()).isNotEqualTo(keyDifferent.hashCode());
    }

    @Test
    public void testAccountKeyEqualsSameKey_keysAreEqual() {
        AccountKey key = new AccountKey(TEST_ACCOUNT_KEY_1);
        AccountKey keyCopy = new AccountKey(TEST_ACCOUNT_KEY_1);
        assertThat(key.equals(keyCopy)).isTrue();
    }

    @Test
    public void testAccountKeyEqualsDifferentKey_keysAreNotEqual() {
        AccountKey key = new AccountKey(TEST_ACCOUNT_KEY_1);
        AccountKey keyDifferent = new AccountKey(TEST_ACCOUNT_KEY_2);
        assertThat(key.equals(keyDifferent)).isFalse();
    }

    @Test
    public void testAccountKeyEqualsDifferentObjectType_keysAreNotEqual() {
        AccountKey key = new AccountKey(TEST_ACCOUNT_KEY_1);
        Object otherObj = new Object();
        assertThat(key.equals(otherObj)).isFalse();
    }

    @Test
    public void testCreateWithValidSize_storageCreated() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);
        assertThat(mFastPairAccountKeyStorage).isNotNull();
        assertThat(mFastPairAccountKeyStorage.capacity()).isEqualTo(TEST_SIZE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithSizeTooSmall_throwsException() {
        mFastPairAccountKeyStorage =
                new FastPairAccountKeyStorage(mMockContext, TEST_SIZE_TOO_SMALL);
    }

    @Test
    public void testAddKeyWhileStorageEmpty_keyAdded() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));

        ArrayList<AccountKey> expectedKeys =
                new ArrayList<AccountKey>(List.of(new AccountKey(TEST_ACCOUNT_KEY_1)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testAddKeyWhileStorageAtMax_keyAddedAndLruDropped() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_4));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_5));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_6));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3),
                new AccountKey(TEST_ACCOUNT_KEY_4),
                new AccountKey(TEST_ACCOUNT_KEY_5),
                new AccountKey(TEST_ACCOUNT_KEY_6)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testAddKeyAlreadyInStorage_KeysLruPositionReset() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_4));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_5));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_4),
                new AccountKey(TEST_ACCOUNT_KEY_5),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testAddNullKey_nothingHappens() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_4));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_5));
        mFastPairAccountKeyStorage.add(null);

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3),
                new AccountKey(TEST_ACCOUNT_KEY_4),
                new AccountKey(TEST_ACCOUNT_KEY_5)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testRemoveKeyInStorage_keyRemoved() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);

        mFastPairAccountKeyStorage.remove(new AccountKey(TEST_ACCOUNT_KEY_2));

        ArrayList<AccountKey> expectedKeys2 = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys2);
        assertPersistedKeys(expectedKeys2);
    }

    @Test
    public void testRemoveKeyNotInStorage_keysUnchanged() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);

        mFastPairAccountKeyStorage.remove(new AccountKey(TEST_ACCOUNT_KEY_4));

        returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testRemoveNullKey_nothingHappens() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);

        mFastPairAccountKeyStorage.remove(null);

        returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testClearKeys_keysEmpty() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);

        mFastPairAccountKeyStorage.clear();

        returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(new ArrayList<AccountKey>());
        assertPersistedKeys(new ArrayList<AccountKey>());
    }

    @Test
    public void testGetAllAccountKeys_returnsOrderKeySet() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);
        ArrayList<AccountKey> keys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3),
                new AccountKey(TEST_ACCOUNT_KEY_4),
                new AccountKey(TEST_ACCOUNT_KEY_5)));

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(keys.get(0));
        mFastPairAccountKeyStorage.add(keys.get(1));
        mFastPairAccountKeyStorage.add(keys.get(2));
        mFastPairAccountKeyStorage.add(keys.get(3));
        mFastPairAccountKeyStorage.add(keys.get(4));

        List<AccountKey> returnedKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
        assertThat(returnedKeys).isEqualTo(keys);
    }

    @Test
    public void testLoadWhileUserLocked_loadFails() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        List<AccountKey> persistedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        setPersistedKeys(persistedKeys);
        setUserUnlocked(false);
        mFastPairAccountKeyStorage.load();

        assertThat(mFastPairAccountKeyStorage.getAllAccountKeys()).isNotNull();
        assertThat(mFastPairAccountKeyStorage.getAllAccountKeys()).isEmpty();
        assertPersistedKeys(persistedKeys);
    }

    @Test
    public void testLoadWhileUserUnlocked_loadSucceeds() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        setPersistedKeys(expectedKeys);
        setUserUnlocked(true);
        mFastPairAccountKeyStorage.load();

        assertThat(mFastPairAccountKeyStorage.getAllAccountKeys()).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testCommitWhileUserLocked_commitFails() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(false);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));

        assertPersistedKeys(new ArrayList<AccountKey>());
        verifyNoMoreInteractions(mMockSharedPreferences);
        verifyNoMoreInteractions(mMockSharedPreferencesEditor);
    }

    @Test
    public void testCommitWhileUserUnlocked_commitSucceeds() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);

        setUserUnlocked(true);
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_1));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_2));
        mFastPairAccountKeyStorage.add(new AccountKey(TEST_ACCOUNT_KEY_3));

        ArrayList<AccountKey> expectedKeys = new ArrayList<AccountKey>(List.of(
                new AccountKey(TEST_ACCOUNT_KEY_1),
                new AccountKey(TEST_ACCOUNT_KEY_2),
                new AccountKey(TEST_ACCOUNT_KEY_3)));
        assertThat(mFastPairAccountKeyStorage.getAllAccountKeys()).isEqualTo(expectedKeys);
        assertPersistedKeys(expectedKeys);
    }

    @Test
    public void testToString_isNotNull() {
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mMockContext, TEST_SIZE);
        assertThat(mFastPairAccountKeyStorage.toString()).isNotNull();
    }
}
