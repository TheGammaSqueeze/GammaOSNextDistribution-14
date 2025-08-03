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

package com.android.adservices.data.kanon;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/** Dao to manage access to entities in Client parameters table. */
@Dao
public abstract class KAnonMessageDao {

    /** Inserts the given KAnonMessage in table. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertKAnonMessage(DBKAnonMessage kAnonMessage);

    /** Inserts the given KAnonMessages in the table. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract long[] insertAllKAnonMessages(List<DBKAnonMessage> messages);

    /** Returns latest N messages with given status. */
    @Query("SELECT * FROM kanon_messages WHERE status = :status ORDER BY created_at LIMIT :count")
    public abstract List<DBKAnonMessage> getNLatestKAnonMessagesWithStatus(
            int count, @KAnonMessageConstants.MessageStatus int status);

    /** Return the kanon message with the given kanon hash set. */
    @Query("SELECT * FROM kanon_messages WHERE kanon_hash_set = :hashSetToSearch")
    public abstract List<DBKAnonMessage> getKAnonMessagesWithMessage(String hashSetToSearch);

    /** Deletes all the knaon messages with the ids in the given list of ids. */
    @Query("DELETE FROM kanon_messages where message_id IN (:idsToDelete)")
    public abstract void deleteKAnonMessagesWithIds(List<Long> idsToDelete);

    /** Deletes all the kanon messages. */
    @Query("DELETE FROM kanon_messages")
    public abstract void deleteAllKAnonMessages();

    /** Updates the status for the messages with the given messages ids. */
    @Query("UPDATE kanon_messages SET status = :status WHERE message_id IN (:idsToUpdate)")
    public abstract void updateMessagesStatus(
            List<Long> idsToUpdate, @KAnonMessageConstants.MessageStatus int status);

    @Query(
            "DELETE from kanon_messages WHERE expiry_instant < :currentTime OR"
                    + " (corresponding_client_parameters_expiry_instant is NOT NULL AND"
                    + " corresponding_client_parameters_expiry_instant < :currentTime)")
    public abstract void removeExpiredEntities(Instant currentTime);
}
