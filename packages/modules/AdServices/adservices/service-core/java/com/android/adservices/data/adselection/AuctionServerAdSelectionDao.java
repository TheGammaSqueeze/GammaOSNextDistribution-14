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

import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

/** DAO to manage access to entities in Auction Server Ad Selection Table */
@Dao
public abstract class AuctionServerAdSelectionDao {
    /**
     * Inserts the given {@link DBAuctionServerAdSelection} in table.
     *
     * @throws SQLiteConstraintException if the ad selection id already exists
     */
    @Insert
    public abstract void insertAuctionServerAdSelection(
            DBAuctionServerAdSelection serverAdSelection);

    /**
     * Updates the given {@link DBAuctionServerAdSelection} in table.
     *
     * @return number of rows updated
     */
    @Update
    public abstract int updateAuctionServerAdSelection(
            DBAuctionServerAdSelection serverAdSelection);

    /** Returns the {@link DBAuctionServerAdSelection} of given ad selection id if it exists. */
    @Query("SELECT * FROM auction_server_ad_selection WHERE ad_selection_id = :adSelectionId")
    public abstract DBAuctionServerAdSelection getAuctionServerAdSelection(long adSelectionId);

    /** Delets the {@link DBAuctionServerAdSelection} of the givein ad selection id */
    @Query("DELETE FROM auction_server_ad_selection WHERE ad_selection_id = :adSelectionId")
    public abstract void removeAdSelectionById(long adSelectionId);
}
