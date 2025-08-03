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

package com.android.adservices.data.adselection;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

@Dao
public abstract class ProtectedServersEncryptionConfigDao {
    /**
     * Returns the EncryptionKey of given key type and fetched from the given coordinator url with
     * the latest expiry instant.
     *
     * @param encryptionKeyType Type of Key to query
     * @return Returns EncryptionKey with latest expiry instant.
     */
    @Query(
            "SELECT * FROM protected_servers_encryption_config "
                    + "WHERE encryption_key_type = :encryptionKeyType "
                    + "AND coordinator_url = :coordinatorUrl "
                    + "ORDER BY expiry_instant DESC "
                    + "LIMIT :count ")
    public abstract List<DBProtectedServersEncryptionConfig> getLatestExpiryNKeys(
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType,
            String coordinatorUrl,
            int count);

    /**
     * Fetches N number of non-expired EncryptionKey of given key type and given coordinator url.
     *
     * @param encryptionKeyType Type of EncryptionKey to Query
     * @param coordinatorUrl The coordinator url from which the key was fetched
     * @param now expiry Instant should be greater than this given instant.
     * @param count Number of keys to return.
     * @return
     */
    @Query(
            "SELECT * FROM protected_servers_encryption_config "
                    + "WHERE encryption_key_type = :encryptionKeyType "
                    + "AND expiry_instant >= :now "
                    + "AND coordinator_url = :coordinatorUrl "
                    + "ORDER BY expiry_instant DESC "
                    + "LIMIT :count ")
    public abstract List<DBProtectedServersEncryptionConfig> getLatestExpiryNActiveKeys(
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType,
            String coordinatorUrl,
            Instant now,
            int count);

    /**
     * Fetches expired keys of given key type and coordinator URL. A key is considered expired with
     * its expiryInstant is lower than the given instant.
     *
     * @param type Type of EncryptionKey to Query.
     * @param coordinatorUrl The coordinator url from which the key was fetched
     * @param now Upper bound instant for expiry determination.
     * @return Returns expired keys of given key type.
     */
    @Query(
            "SELECT * "
                    + " FROM protected_servers_encryption_config "
                    + "WHERE expiry_instant < :now "
                    + "AND encryption_key_type = :type "
                    + "AND coordinator_url = :coordinatorUrl ")
    public abstract List<DBProtectedServersEncryptionConfig> getExpiredKeys(
            @EncryptionKeyConstants.EncryptionKeyType int type, String coordinatorUrl, Instant now);

    /**
     * Returns expired keys in the table.
     *
     * @param now A keys is considered expired if key's expiryInstant is lower than this given
     *     instant.
     * @return Returns expired keys keyed by key type.
     */
    @Query("SELECT * FROM protected_servers_encryption_config " + "WHERE expiry_instant < :now ")
    public abstract List<DBProtectedServersEncryptionConfig> getAllExpiredKeys(Instant now);

    /** Deletes expired keys of the given encryption key type and coordinatorUrl. */
    @Query(
            "DELETE FROM protected_servers_encryption_config WHERE expiry_instant < :now "
                    + "AND encryption_key_type = :type "
                    + "AND coordinator_url = :coordinatorUrl")
    public abstract int deleteExpiredRows(
            @EncryptionKeyConstants.EncryptionKeyType int type, String coordinatorUrl, Instant now);

    /** Delete all keys from the table. */
    @Query("DELETE FROM protected_servers_encryption_config")
    public abstract int deleteAllEncryptionKeys();

    /** Insert into the table the given protected servers encryption configs. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertKeys(List<DBProtectedServersEncryptionConfig> keys);
}
