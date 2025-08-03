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

import java.net.URL;
import java.util.List;
import java.util.Map;

/** Interface for generic cache */
public interface HttpCache {

    /** Gets a cached entry */
    DBCacheEntry get(URL url);

    /**
     * Saves a cache entry
     *
     * @param url for which the request was made
     * @param body response for the url
     * @param requestPropertiesMap original connection's properties
     */
    void put(
            URL url,
            String body,
            Map<String, List<String>> requestPropertiesMap,
            Map<String, List<String>> responseHeaders);

    /** @return no of entries cached */
    long getCachedEntriesCount();

    /** @return no of entries taken from cache, saving network call */
    long getHitCount();

    /** @return no of cache look-ups attempts made */
    long getRequestCount();

    /** Delete the cache */
    void delete();

    /** Clean up the cache */
    void cleanUp();

    /** Possible observable events for Cache */
    enum CacheEventType {
        GET,
        PUT,
        DELETE,
        CLEANUP
    }

    /**
     * Add observers to observe this cache's events
     *
     * @param observer that gets updated on events
     */
    void addObserver(CacheObserver observer);

    /**
     * Notify all the registered observers
     *
     * @param cacheEvent type of caching event
     */
    void notifyObservers(CacheEventType cacheEvent);

    /** An observer that can help get updates from inside the cache events */
    interface CacheObserver {
        void update(CacheEventType cacheEvent);
    }
}
