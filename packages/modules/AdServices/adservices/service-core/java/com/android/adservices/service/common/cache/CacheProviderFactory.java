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

import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;

import java.net.URL;
import java.util.List;
import java.util.Map;

/** A factory that creates an implementation of {@link HttpCache} as needed */
public class CacheProviderFactory {

    /**
     * @param context Application context
     * @param flags Phenotype flags
     * @return an implementation of {@link HttpCache} as needed based on the flags
     */
    public static HttpCache create(Context context, Flags flags) {
        CacheEntryDao cacheEntryDao = CacheDatabase.getInstance(context).getCacheEntryDao();
        if (BinderFlagReader.readFlag(flags::getFledgeHttpCachingEnabled)
                && cacheEntryDao != null) {
            return new FledgeHttpCache(
                    cacheEntryDao,
                    flags.getFledgeHttpCacheMaxAgeSeconds(),
                    flags.getFledgeHttpCacheMaxEntries());
        } else {
            return new NoOpCache();
        }
    }

    /** @return a {@link NoOpCache} version of {@link HttpCache} */
    public static HttpCache createNoOpCache() {
        return new NoOpCache();
    }

    /**
     * This cache is intended to be no-op and empty and used in scenarios where is caching is not
     * really needed. This cache can be plugged into clients and can be swapped by specific
     * implementation of {@link HttpCache} based on the caching use case.
     */
    static class NoOpCache implements HttpCache {
        /** gets nothing from cache, null */
        @Override
        public DBCacheEntry get(URL url) {
            return null;
        }

        /** puts nothing into the cache */
        @Override
        public void put(
                URL url,
                String body,
                Map<String, List<String>> requestPropertiesMap,
                Map<String, List<String>> responseHeaders) {}

        /** @return 0 */
        @Override
        public long getCachedEntriesCount() {
            return 0;
        }

        /** @return 0 */
        @Override
        public long getHitCount() {
            return 0;
        }

        /** @return 0 */
        @Override
        public long getRequestCount() {
            return 0;
        }

        /** deletes nothing as there is nothing to delete */
        @Override
        public void delete() {}

        /** cleans up nothing as there is nothing to delete */
        @Override
        public void cleanUp() {}

        /** no observers needed */
        @Override
        public void addObserver(CacheObserver observer) {}

        /** no observers need to be notified */
        @Override
        public void notifyObservers(CacheEventType cacheEvent) {}
    }
}
