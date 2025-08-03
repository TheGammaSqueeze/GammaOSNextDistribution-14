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

package com.android.federatedcompute.services.data;

import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.ENCRYPTION_KEY_TABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.FederatedComputeEncryptionColumns;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class FederatedComputeEncryptionKeyDaoTest {
    private static final String KEY_ID = "0962201a-5abd-4e25-a486-2c7bd1ee1887";
    private static final String PUBLICKEY = "GOcMAnY4WkDYp6R3WSw8IpYK6eVe2RGZ9Z0OBb3EbjQ\\u003d";
    private static final int KEY_TYPE = FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION;
    private static final long NOW = 1698193647L;
    private static final long TTL = 100L;

    private FederatedComputeEncryptionKeyDao mEncryptionKeyDao;
    private Context mContext;

    private final Clock mClock = MonotonicClock.getInstance();

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mEncryptionKeyDao = FederatedComputeEncryptionKeyDao.getInstanceForTest(mContext);
    }

    @After
    public void cleanUp() throws Exception {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testInsertEncryptionKey_success() throws Exception {
        FederatedComputeEncryptionKey key1 = createRandomPublicKeyWithConstantTTL(3600);
        FederatedComputeEncryptionKey key2 = createRandomPublicKeyWithConstantTTL(3600);

        assertTrue(mEncryptionKeyDao.insertEncryptionKey(key1));
        assertTrue(mEncryptionKeyDao.insertEncryptionKey(key2));

        SQLiteDatabase db =
                FederatedComputeDbHelper.getInstanceForTest(mContext).getReadableDatabase();

        assertThat(DatabaseUtils.queryNumEntries(db, ENCRYPTION_KEY_TABLE)).isEqualTo(2);
    }

    @Test
    public void testInsertDuplicateEncryptionKey_success() {
        FederatedComputeEncryptionKey key1 = createRandomPublicKeyWithConstantTTL(3600);

        assertTrue(mEncryptionKeyDao.insertEncryptionKey(key1));

        FederatedComputeEncryptionKey key2 =
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier(key1.getKeyIdentifier())
                        .setPublicKey(key1.getPublicKey())
                        .setKeyType(key1.getKeyType())
                        .setCreationTime(key1.getCreationTime())
                        .setExpiryTime(key1.getExpiryTime() + 10000L).build();

        assertTrue(mEncryptionKeyDao.insertEncryptionKey(key2));

        List<FederatedComputeEncryptionKey> keyList = mEncryptionKeyDao
                .getLatestExpiryNKeys(2);

        assertThat(keyList.size()).isEqualTo(1);
        assertThat(keyList.get(0)).isEqualTo(key2);
    }

    @Test
    public void testInsertNullPublicKeyFieldThrows() {
        assertThrows(NullPointerException.class, () -> insertNullFieldEncryptionKey());
    }

    @Test
    public void testQueryKeys_success() {
        List<FederatedComputeEncryptionKey> keyList0 =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        "" /* selection= */, new String[0] /* selectionArgs= */,
                        "" /* orderBy= */, 5);

        assertThat(keyList0.size()).isEqualTo(0);

        FederatedComputeEncryptionKey key1 = createFixedPublicKey();
        mEncryptionKeyDao.insertEncryptionKey(key1);

        List<FederatedComputeEncryptionKey> keyList1 =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        "" /* selection= */,
                        new String[0] /* selectionArgs= */,
                        FederatedComputeEncryptionColumns.EXPIRY_TIME + " DESC",
                        1);

        assertThat(keyList1.get(0)).isEqualTo(key1);

        // with selection args
        String selection =
                FederatedComputeEncryptionKeyContract.FederatedComputeEncryptionColumns
                                .KEY_IDENTIFIER
                        + " = ? ";
        String[] selectionArgs = {KEY_ID};

        List<FederatedComputeEncryptionKey> keyList2 =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        selection,
                        selectionArgs,
                        FederatedComputeEncryptionKeyContract.FederatedComputeEncryptionColumns
                                        .EXPIRY_TIME
                                + " DESC",
                        1);

        assertThat(keyList2.size()).isEqualTo(1);
        assertThat(keyList2.get(0)).isEqualTo(key1);
    }

    @Test
    public void findExpiryKeys_success() {
        FederatedComputeEncryptionKey key1 = createRandomPublicKeyWithConstantTTL(1000000L);
        FederatedComputeEncryptionKey key2 = createRandomPublicKeyWithConstantTTL(2000000L);
        FederatedComputeEncryptionKey key3 = createRandomPublicKeyWithConstantTTL(3000000L);
        mEncryptionKeyDao.insertEncryptionKey(key1);
        mEncryptionKeyDao.insertEncryptionKey(key2);
        mEncryptionKeyDao.insertEncryptionKey(key3);

        List<FederatedComputeEncryptionKey> keyList = mEncryptionKeyDao.getLatestExpiryNKeys(3);

        assertThat(keyList.size()).isEqualTo(3);
        assertThat(keyList.get(0)).isEqualTo(key3);
        assertThat(keyList.get(1)).isEqualTo(key2);
        assertThat(keyList.get(2)).isEqualTo(key1);
    }

    @Test
    public void findExpiryKeysWithlimit_success() {
        FederatedComputeEncryptionKey key1 = createRandomPublicKeyWithConstantTTL(1000000L);
        FederatedComputeEncryptionKey key2 = createRandomPublicKeyWithConstantTTL(2000000L);
        FederatedComputeEncryptionKey key3 = createRandomPublicKeyWithConstantTTL(3000000L);
        mEncryptionKeyDao.insertEncryptionKey(key1);
        mEncryptionKeyDao.insertEncryptionKey(key2);
        mEncryptionKeyDao.insertEncryptionKey(key3);

        List<FederatedComputeEncryptionKey> keyList = mEncryptionKeyDao.getLatestExpiryNKeys(2);

        assertThat(keyList.size()).isEqualTo(2);
        assertThat(keyList.get(0)).isEqualTo(key3);
        assertThat(keyList.get(1)).isEqualTo(key2);

        // limit = 0
        List<FederatedComputeEncryptionKey> keyList0 = mEncryptionKeyDao.getLatestExpiryNKeys(0);
        assertThat(keyList0.size()).isEqualTo(0);
    }

    @Test
    public void findExpiryKeys_empty_success() {
        List<FederatedComputeEncryptionKey> keyList = mEncryptionKeyDao.getLatestExpiryNKeys(3);

        assertThat(keyList.size()).isEqualTo(0);

        List<FederatedComputeEncryptionKey> keyList0 = mEncryptionKeyDao.getLatestExpiryNKeys(0);

        assertThat(keyList0.size()).isEqualTo(0);
    }

    @Test
    public void deleteExpiredKeys_success() throws Exception {
        FederatedComputeEncryptionKey key1 = createRandomPublicKeyWithConstantTTL(0);
        mEncryptionKeyDao.insertEncryptionKey(key1);

        int deletedRows = mEncryptionKeyDao.deleteExpiredKeys();

        assertThat(deletedRows).isEqualTo(1);

        // check current number of rows
        List<FederatedComputeEncryptionKey> keyList = mEncryptionKeyDao.getLatestExpiryNKeys(3);

        assertThat(keyList.size()).isEqualTo(0);
    }

    @Test
    public void deleteNoKeys_success() {
        int deletedRows = mEncryptionKeyDao.deleteExpiredKeys();
        assertThat(deletedRows).isEqualTo(0);
    }

    private void insertNullFieldEncryptionKey() throws Exception {
        FederatedComputeEncryptionKey key1 =
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier(UUID.randomUUID().toString())
                        .setKeyType(FederatedComputeEncryptionKey.KEY_TYPE_UNDEFINED)
                        .setCreationTime(mClock.currentTimeMillis())
                        .setExpiryTime(mClock.currentTimeMillis() + TTL)
                        .build();

        mEncryptionKeyDao.insertEncryptionKey(key1);
    }

    private FederatedComputeEncryptionKey createRandomPublicKeyWithConstantTTL(long ttl) {
        byte[] bytes = new byte[32];
        Random generator = new Random();
        generator.nextBytes(bytes);
        return new FederatedComputeEncryptionKey.Builder()
                .setKeyIdentifier(UUID.randomUUID().toString())
                .setPublicKey(new String(bytes, 0, bytes.length))
                .setKeyType(FederatedComputeEncryptionKey.KEY_TYPE_UNDEFINED)
                .setCreationTime(mClock.currentTimeMillis())
                .setExpiryTime(mClock.currentTimeMillis() + ttl)
                .build();
    }

    private FederatedComputeEncryptionKey createFixedPublicKey() {
        return new FederatedComputeEncryptionKey.Builder()
                .setKeyIdentifier(KEY_ID)
                .setPublicKey(PUBLICKEY)
                .setKeyType(KEY_TYPE)
                .setCreationTime(NOW)
                .setExpiryTime(NOW + TTL)
                .build();
    }
}
