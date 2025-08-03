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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.database.InstantConverter;
import com.android.rkpdapp.database.ProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.testutil.TestDatabase;
import com.android.rkpdapp.testutil.TestProvisionedKeyDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RkpdDatabaseTest {
    private static final String DB_NAME = "test_db";
    private static final String TEST_HAL_1 = "testIrpc";
    private static final String TEST_HAL_2 = "someOtherIrpc";
    private static final byte[] TEST_KEY_BLOB_1 = new byte[]{0x01, 0x02, 0x03};
    private static final byte[] TEST_KEY_BLOB_2 = new byte[]{0x11, 0x12, 0x13};
    private static final byte[] TEST_KEY_BLOB_3 = new byte[]{0x21, 0x22, 0x23};
    private static final Instant TEST_KEY_EXPIRY = Instant.now().plus(Duration.ofHours(1));
    private static final int FAKE_CLIENT_UID = 1;
    private static final int FAKE_CLIENT_UID_2 = 2;
    private static final int FAKE_KEY_ID = 1;
    private static final int FAKE_CLIENT_UID_3 = 3;
    private static final int FAKE_KEY_ID_2 = 2;
    private ProvisionedKey mProvisionedKey1;
    private ProvisionedKey mProvisionedKey2;

    private ProvisionedKeyDao mKeyDao;
    private RkpdDatabase mDatabase;
    private TestDatabase mTestDatabase;
    private TestProvisionedKeyDao mTestDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = Room.databaseBuilder(context, RkpdDatabase.class, DB_NAME).build();
        mKeyDao = mDatabase.provisionedKeyDao();
        mKeyDao.deleteAllKeys();
        mTestDatabase = Room.databaseBuilder(context, TestDatabase.class, DB_NAME).build();
        mTestDao = mTestDatabase.dao();
        mProvisionedKey1 = new ProvisionedKey(TEST_KEY_BLOB_1, TEST_HAL_1, TEST_KEY_BLOB_1,
                TEST_KEY_BLOB_1, TEST_KEY_EXPIRY);
        mProvisionedKey2 = new ProvisionedKey(TEST_KEY_BLOB_2, TEST_HAL_2, TEST_KEY_BLOB_2,
                TEST_KEY_BLOB_2, TEST_KEY_EXPIRY);
    }

    @After
    public void tearDown() {
        mDatabase.close();
        mTestDatabase.close();
    }

    @Test
    public void testWriteToTable() {
        mKeyDao.insertKeys(List.of(mProvisionedKey1));
        List<ProvisionedKey> keysInDatabase = mTestDao.getAllKeys();

        assertThat(keysInDatabase).containsExactly(mProvisionedKey1);
    }

    @Test
    public void testOverwriteConflict() {
        mProvisionedKey2.keyBlob = TEST_KEY_BLOB_1;
        try {
            mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));
            fail("Inserting keys with same keyBlob should throw SQLiteConstraintException.");
        } catch (SQLiteConstraintException ex) {
            assertThat(ex).hasMessageThat().contains("UNIQUE constraint failed");
        }

        List<ProvisionedKey> unassignedKeys = mTestDao.getAllKeys();
        assertThat(unassignedKeys).isEmpty();
    }

    @Test
    public void testRemovingExpiredKeyFromTable() {
        mProvisionedKey1.expirationTime = Instant.now().minus(1000, ChronoUnit.MINUTES);
        mProvisionedKey2.expirationTime = Instant.now().plus(1000, ChronoUnit.MINUTES);

        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        List<ProvisionedKey> keysInDatabase = mTestDao.getAllKeys();
        assertThat(keysInDatabase).hasSize(2);

        mKeyDao.deleteExpiringKeys(Instant.now());

        keysInDatabase = mTestDao.getAllKeys();
        assertThat(keysInDatabase).containsExactly(mProvisionedKey2);
    }

    @Test
    public void testAssignedKeysAreAlsoExpired() {
        mKeyDao.insertKeys(List.of(mProvisionedKey1));

        assertThat(mKeyDao.getOrAssignKey(TEST_HAL_1, Instant.now(), FAKE_CLIENT_UID, FAKE_KEY_ID))
                .isNotNull();
        assertThat(mKeyDao.getKeyForClientAndIrpc(TEST_HAL_1, FAKE_CLIENT_UID, FAKE_KEY_ID))
                .isNotNull();

        mKeyDao.deleteExpiringKeys(mProvisionedKey1.expirationTime.plusMillis(1));

        assertThat(mKeyDao.getKeyForClientAndIrpc(TEST_HAL_1, FAKE_CLIENT_UID, FAKE_KEY_ID))
                .isNull();
    }

    @Test
    public void testUpdate() {
        mKeyDao.insertKeys(List.of(mProvisionedKey1));

        List<ProvisionedKey> keysInDatabase = mTestDao.getAllKeys();
        ProvisionedKey key = keysInDatabase.get(0);
        assertThat(keysInDatabase).hasSize(1);
        assertThat(key.expirationTime).isEqualTo(
                mProvisionedKey1.expirationTime.truncatedTo(ChronoUnit.MILLIS));

        Instant expiredInstant = InstantConverter.fromTimestamp(System.currentTimeMillis())
                .minus(1000, ChronoUnit.MINUTES);
        key.expirationTime = expiredInstant;
        mKeyDao.updateKey(key);
        keysInDatabase = mTestDao.getAllKeys();
        assertThat(keysInDatabase).containsExactly(key);
        assertThat(keysInDatabase.get(0).expirationTime).isEqualTo(expiredInstant);
    }

    @Test
    public void testUpdateWithNonExistentKey() {
        mKeyDao.updateKey(mProvisionedKey1);

        assertThat(mTestDao.getAllKeys()).isEmpty();
    }

    @Test
    public void testDeleteAllKeys() {
        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        List<ProvisionedKey> keysInDatabase = mTestDao.getAllKeys();
        assertThat(keysInDatabase).hasSize(2);

        mKeyDao.deleteAllKeys();
        assertThat(mTestDao.getAllKeys()).isEmpty();
    }

    @Test
    public void testGetTotalExpiringKeysForIrpc() {
        final Instant past = Instant.now().minus(1000, ChronoUnit.MINUTES);
        final Instant future = Instant.now().plus(1000, ChronoUnit.MINUTES);

        ProvisionedKey key1 = new ProvisionedKey(TEST_KEY_BLOB_1, TEST_HAL_1, TEST_KEY_BLOB_1,
                TEST_KEY_BLOB_1, past);
        ProvisionedKey key2 = new ProvisionedKey(TEST_KEY_BLOB_2, TEST_HAL_1, TEST_KEY_BLOB_2,
                TEST_KEY_BLOB_2, future);
        ProvisionedKey key3 = new ProvisionedKey(TEST_KEY_BLOB_3, TEST_HAL_2, TEST_KEY_BLOB_3,
                TEST_KEY_BLOB_3, past);
        mKeyDao.insertKeys(List.of(key1, key2, key3));

        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_1, past)).isEqualTo(0);
        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_2, past)).isEqualTo(0);

        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_1, past.plusMillis(1)))
                .isEqualTo(1);
        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_2, past.plusMillis(1)))
                .isEqualTo(1);

        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_1, future)).isEqualTo(1);
        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_2, future)).isEqualTo(1);

        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_1, future.plusMillis(1)))
                .isEqualTo(2);
        assertThat(mKeyDao.getTotalExpiringKeysForIrpc(TEST_HAL_2, future.plusMillis(1)))
                .isEqualTo(1);
    }

    @Test
    public void testGetKeyForClientAndIrpc() {
        mProvisionedKey1.keyId = FAKE_KEY_ID;
        mProvisionedKey1.clientUid = FAKE_CLIENT_UID;
        mProvisionedKey2.irpcHal = TEST_HAL_1;
        mProvisionedKey2.keyId = FAKE_KEY_ID;
        mProvisionedKey2.clientUid = FAKE_CLIENT_UID_2;

        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        ProvisionedKey assignedKey = mKeyDao.getKeyForClientAndIrpc(TEST_HAL_1, FAKE_CLIENT_UID,
                FAKE_KEY_ID);
        assertThat(mProvisionedKey1).isEqualTo(assignedKey);

        assignedKey = mKeyDao.getKeyForClientAndIrpc(TEST_HAL_1, FAKE_CLIENT_UID_2, FAKE_KEY_ID);
        assertThat(mProvisionedKey2).isEqualTo(assignedKey);

        assignedKey = mKeyDao.getKeyForClientAndIrpc(TEST_HAL_1, FAKE_CLIENT_UID_3, FAKE_KEY_ID_2);
        assertThat(assignedKey).isNull();
    }

    @Test
    public void testUpgradeKeyBlob() {
        mProvisionedKey1.keyId = FAKE_KEY_ID;
        mProvisionedKey1.clientUid = FAKE_CLIENT_UID;
        mKeyDao.insertKeys(List.of(mProvisionedKey1));

        ProvisionedKey databaseKey = mTestDao.getAllKeys().get(0);
        assertThat(databaseKey.keyBlob).isEqualTo(TEST_KEY_BLOB_1);
        assertThat(mKeyDao.upgradeKeyBlob(FAKE_CLIENT_UID_2, TEST_KEY_BLOB_1, TEST_KEY_BLOB_2))
                .isEqualTo(0);
        assertThat(mKeyDao.upgradeKeyBlob(FAKE_CLIENT_UID, TEST_KEY_BLOB_1, TEST_KEY_BLOB_2))
                .isEqualTo(1);

        databaseKey = mTestDao.getAllKeys().get(0);
        assertThat(databaseKey.keyBlob).isEqualTo(TEST_KEY_BLOB_2);
    }

    @Test
    public void testCorrectClientUpgradesKeyBlob() {
        mProvisionedKey1.keyId = FAKE_KEY_ID;
        mProvisionedKey1.clientUid = FAKE_CLIENT_UID;
        mKeyDao.insertKeys(List.of(mProvisionedKey1));

        ProvisionedKey databaseKey = mTestDao.getAllKeys().get(0);
        assertThat(databaseKey.keyBlob).isEqualTo(TEST_KEY_BLOB_1);
        assertThat(mKeyDao.upgradeKeyBlob(FAKE_CLIENT_UID_2, TEST_KEY_BLOB_1, TEST_KEY_BLOB_2))
                .isEqualTo(0);

        databaseKey = mTestDao.getAllKeys().get(0);
        assertThat(databaseKey.keyBlob).isEqualTo(TEST_KEY_BLOB_1);
    }

    @Test
    public void testUpgradeNonExistentKeyBlob() {
        mProvisionedKey1.keyId = FAKE_KEY_ID;
        mProvisionedKey1.clientUid = FAKE_CLIENT_UID;
        mKeyDao.insertKeys(List.of(mProvisionedKey1));
        assertThat(mKeyDao.upgradeKeyBlob(FAKE_CLIENT_UID, TEST_KEY_BLOB_2, TEST_KEY_BLOB_3))
                .isEqualTo(0);
    }

    @Test
    public void testCountUnassignedKeys() {
        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));
        assertThat(mKeyDao.getTotalUnassignedKeysForIrpc(TEST_HAL_1)).isEqualTo(1);
        assertThat(mKeyDao.getTotalUnassignedKeysForIrpc(TEST_HAL_2)).isEqualTo(1);
        assertThat(mKeyDao.getTotalUnassignedKeysForIrpc("fakeHal")).isEqualTo(0);
    }

    @Test
    public void testAssignKey() throws RkpdException {
        mProvisionedKey2.irpcHal = TEST_HAL_1;
        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        List<ProvisionedKey> keysPersisted = mTestDao.getAllKeys();
        for (ProvisionedKey databaseKey : keysPersisted) {
            assertThat(databaseKey.keyId).isNull();
            assertThat(databaseKey.clientUid).isNull();
        }

        ProvisionedKey assignedKey = mKeyDao.getOrAssignKey(TEST_HAL_1, Instant.now(),
                FAKE_CLIENT_UID, FAKE_KEY_ID);

        assertThat(assignedKey.keyId).isEqualTo(FAKE_KEY_ID);
        assertThat(assignedKey.clientUid).isEqualTo(FAKE_CLIENT_UID);

        ProvisionedKey sameKey = mKeyDao.getOrAssignKey(TEST_HAL_1, Instant.now(), FAKE_CLIENT_UID,
                FAKE_KEY_ID);
        assertThat(sameKey).isEqualTo(assignedKey);
    }

    @Test
    public void testAssignKeyChoosesNonExpiredKey() throws RkpdException {
        mProvisionedKey1.expirationTime = Instant.now().minusMillis(1);
        mProvisionedKey2.irpcHal = TEST_HAL_1;
        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        ProvisionedKey assignedKey = mKeyDao.getOrAssignKey(TEST_HAL_1, Instant.now(),
                FAKE_CLIENT_UID, FAKE_KEY_ID);

        // The first key is expired, so it should not have been assigned
        assertThat(assignedKey.keyBlob).isNotEqualTo(mProvisionedKey1.publicKey);
        assertThat(assignedKey.keyBlob).isEqualTo(mProvisionedKey2.publicKey);
    }

    @Test
    public void testAssignKeyFailsIfAllKeysAreExpired() throws RkpdException {
        mProvisionedKey1.expirationTime = Instant.now().minusMillis(1);
        mProvisionedKey2.irpcHal = TEST_HAL_1;
        mProvisionedKey2.expirationTime = Instant.now().minusMillis(1);
        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        assertThat(mKeyDao.getOrAssignKey(TEST_HAL_1, Instant.now(), FAKE_CLIENT_UID,
                FAKE_KEY_ID)).isNull();
    }

    @Test
    public void testNoUnassignedKeyRemaining() {
        assertThat(mKeyDao.getOrAssignKey(TEST_HAL_1, Instant.now(), FAKE_CLIENT_UID,
                FAKE_KEY_ID)).isNull();
    }

    @Test
    public void testUpgradeWithNullKeyBlob() {
        mProvisionedKey1.keyId = FAKE_KEY_ID;
        mProvisionedKey1.clientUid = FAKE_CLIENT_UID;
        mKeyDao.insertKeys(List.of(mProvisionedKey1));

        try {
            mKeyDao.upgradeKeyBlob(FAKE_CLIENT_UID, TEST_KEY_BLOB_1, null);
            fail("UpgradeKeyBlob should fail for null keyblob.");
        } catch (SQLiteConstraintException ex) {
            assertThat(ex).hasMessageThat().contains("NOT NULL constraint failed");
        }
    }

    @Test
    public void testUpgradeWithDuplicateKeyBlob() {
        mProvisionedKey1.keyId = FAKE_KEY_ID;
        mProvisionedKey1.clientUid = FAKE_CLIENT_UID;
        mProvisionedKey2.keyId = FAKE_KEY_ID_2;
        mProvisionedKey2.clientUid = FAKE_CLIENT_UID;
        mKeyDao.insertKeys(List.of(mProvisionedKey1, mProvisionedKey2));

        try {
            mKeyDao.upgradeKeyBlob(FAKE_CLIENT_UID, TEST_KEY_BLOB_1, TEST_KEY_BLOB_2);
            fail("UpgradeKeyBlob should fail for duplicate keyblob.");
        } catch (SQLiteConstraintException ex) {
            assertThat(ex).hasMessageThat().contains("UNIQUE constraint failed");
        }
    }
}
