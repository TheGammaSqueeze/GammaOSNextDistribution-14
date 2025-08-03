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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/** Dao to persist, access and delete encoded signals payload for buyers */
@Dao
public interface EncodedPayloadDao {

    /**
     * @param logic an entry for encoded payload
     * @return the rowId of the entry persisted
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long persistEncodedPayload(DBEncodedPayload logic);

    /**
     * @param buyer Ad-tech owner for the encoded payload
     * @return an instance of {@link DBEncodedPayload} if present
     */
    @Query("SELECT * FROM encoded_payload WHERE buyer = :buyer")
    DBEncodedPayload getEncodedPayload(AdTechIdentifier buyer);

    /**
     * @return an list of all {@link DBEncodedPayload} stored
     */
    @Query("SELECT * FROM encoded_payload")
    List<DBEncodedPayload> getAllEncodedPayloads();

    /**
     * @param buyer Ad-tech owner for the encoded payload
     * @return true if the encoded payload for the buyer exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM encoded_payload WHERE buyer = :buyer)")
    boolean doesEncodedPayloadExist(AdTechIdentifier buyer);

    /**
     * @param buyer Ad-tech identifier whose encoded payload we want to delete
     */
    @Query("DELETE FROM encoded_payload WHERE buyer = :buyer")
    void deleteEncodedPayload(AdTechIdentifier buyer);

    /** Deletes all persisted encoded payloads */
    @Query("DELETE FROM encoded_payload")
    void deleteAllEncodedPayloads();

    /**
     * @return list of all the buyers that have encoded payloads
     */
    @Query("SELECT DISTINCT buyer FROM encoded_payload")
    List<AdTechIdentifier> getAllBuyersWithEncodedPayloads();

    /**
     * Deletes encoded payload before the {@code expiryTime}.
     *
     * @return the number of deleted payloads
     */
    @Query("DELETE FROM encoded_payload WHERE creation_time < :expiryTime")
    int deleteEncodedPayloadsBeforeTime(@NonNull Instant expiryTime);
}
