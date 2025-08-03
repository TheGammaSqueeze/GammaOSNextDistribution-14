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

package com.android.adservices.service.common.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;

/** A Dao for handling the queries related to {@link CacheDatabase} */
@Dao
public interface CacheEntryDao {

    /**
     * @param cacheEntry an entry that needs to cached
     * @return the count of entries persisted, ideally 1 if succeeded
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long persistCacheEntry(DBCacheEntry cacheEntry);

    /**
     * Should only return entries that are fresh
     *
     * @param url which was use do cache an entry in the persistence layer
     * @param curTime the current clock time
     * @return the cached entry corresponding to the url key
     */
    @Query(
            "SELECT http_cache.cache_url AS cache_url, http_cache.response_body as response_body,"
                    + " http_cache.response_headers as response_headers, http_cache"
                    + ".creation_timestamp as creation_timestamp, http_cache.max_age as"
                    + " max_age FROM http_cache WHERE (max_age * 1000) + creation_timestamp > "
                    + " :curTime AND cache_url = :url")
    DBCacheEntry getCacheEntry(String url, Instant curTime);

    /**
     * Deletes the rows in the persistence layer which are expired or are older than the cache's
     * max-age.
     *
     * @param defaultMaxAgeSeconds cache enforced max age for which entries should be considered
     *     fresh
     * @param curTime the current clock time
     */
    @Query(
            "DELETE FROM http_cache WHERE (max_age * 1000) + creation_timestamp < :curTime"
                    + " OR (:defaultMaxAgeSeconds * 1000) + creation_timestamp < :curTime")
    void deleteStaleRows(long defaultMaxAgeSeconds, Instant curTime);

    /** @return num of entries in the DB */
    @Query("SELECT COUNT(cache_url) FROM http_cache")
    long getDBEntriesCount();

    /** Deletes all the entries from cache */
    @Query("DELETE FROM http_cache")
    void deleteAll();

    /**
     * Prunes the cache so that it does not increase beyond a max permissible size. Uses FIFO
     * strategy to delete the oldest records first.
     *
     * @param maxCacheEntries max allowed size of cache, eventually imposed after pruning
     */
    @Query(
            "DELETE FROM http_cache WHERE cache_url IN (SELECT cache_url FROM http_cache ORDER BY"
                    + " creation_timestamp DESC LIMIT -1 OFFSET :maxCacheEntries)")
    void prune(long maxCacheEntries);
}
