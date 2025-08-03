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

package com.android.adservices.data.adselection;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/** Dao to manage access to entities in the Auction Server Encryption Key table. */
@Dao
public abstract class EncryptionKeyDao {
    /**
     * Returns the EncryptionKey of given key type with the latest expiry instant.
     *
     * @param encryptionKeyType Type of Key to query
     * @return Returns EncryptionKey with latest expiry instant.
     */
    @Query(
            "SELECT * FROM encryption_key "
                    + "WHERE encryption_key_type = :encryptionKeyType "
                    + "ORDER BY expiry_instant DESC "
                    + "LIMIT :count ")
    public abstract List<DBEncryptionKey> getLatestExpiryNKeysOfType(
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType, int count);

    /**
     * Fetches N number of non-expired EncryptionKey of given key type.
     *
     * @param encryptionKeyType Type of EncryptionKey to Query
     * @param now expiry Instant should be greater than this given instant.
     * @param count Number of keys to return.
     * @return
     */
    @Query(
            "SELECT * FROM encryption_key "
                    + "WHERE encryption_key_type = :encryptionKeyType "
                    + "AND expiry_instant >= :now "
                    + "ORDER BY expiry_instant DESC "
                    + "LIMIT :count ")
    public abstract List<DBEncryptionKey> getLatestExpiryNActiveKeysOfType(
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType,
            Instant now,
            int count);

    /**
     * Fetches expired keys of given key type. A key is considered expired with its expiryInstant is
     * lower than the given instant.
     *
     * @param type Type of EncryptionKey to Query.
     * @param now Upper bound instant for expiry determination.
     * @return Returns expired keys of given key type.
     */
    @Query(
            "SELECT * "
                    + " FROM encryption_key "
                    + "WHERE expiry_instant < :now AND "
                    + "encryption_key_type = :type")
    public abstract List<DBEncryptionKey> getExpiredKeysForType(
            @EncryptionKeyConstants.EncryptionKeyType int type, Instant now);

    /**
     * Returns expired keys in the table.
     *
     * @param now A keys is considered expired if key's expiryInstant is lower than this given
     *     instant.
     * @return Returns expired keys keyed by key type.
     */
    @Query("SELECT * FROM encryption_key " + "WHERE expiry_instant < :now ")
    public abstract List<DBEncryptionKey> getExpiredKeys(Instant now);

    /** Deletes expired keys of the given encryption key type. */
    @Query("DELETE FROM encryption_key WHERE expiry_instant < :now AND encryption_key_type = :type")
    public abstract int deleteExpiredRowsByType(
            @EncryptionKeyConstants.EncryptionKeyType int type, Instant now);

    /** Delete all keys from the table. */
    @Query("DELETE FROM encryption_key")
    public abstract int deleteAllEncryptionKeys();

    /** Insert into the table all the given EnryptionKeys. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAllKeys(List<DBEncryptionKey> keys);
}
