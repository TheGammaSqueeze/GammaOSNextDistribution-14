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


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.android.adservices.LogUtil;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An entry that can be cached, for this class it contains a url and its response body */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "http_cache",
        indices = {@Index(value = {"cache_url"})})
@TypeConverters({DBCacheEntry.Converters.class})
public abstract class DBCacheEntry {

    /** @return Provides the URL which is the primary key for cached entry */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "cache_url")
    @NonNull
    @PrimaryKey
    public abstract String getUrl();

    /** @return the response body corresponding to the cached url */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "response_body")
    public abstract String getResponseBody();

    /** @return the response headers corresponding to the cached url */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "response_headers")
    public abstract ImmutableMap<String, List<String>> getResponseHeaders();

    /** @return the timestamp at which this entry was cached */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_timestamp")
    public abstract Instant getCreationTimestamp();

    /** @return max time in second for which this entry should be considered fresh */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "max_age")
    public abstract long getMaxAgeSeconds();

    /**
     * Creates an entry that can be persisted in the cache storage
     *
     * @param url for which the request needs to be cached
     * @param responseBody response for the url request made
     * @param responseHeaders headers for the response corresponding to the request made by url
     * @param creationTimestamp time at which the request is persisted
     * @param maxAgeSeconds time for which this cache entry is considered fresh
     * @return an instance or created {@link DBCacheEntry}
     */
    public static DBCacheEntry create(
            @NonNull String url,
            String responseBody,
            ImmutableMap<String, List<String>> responseHeaders,
            Instant creationTimestamp,
            long maxAgeSeconds) {
        return builder()
                .setUrl(url)
                .setResponseBody(responseBody)
                .setResponseHeaders(responseHeaders)
                .setCreationTimestamp(creationTimestamp)
                .setMaxAgeSeconds(maxAgeSeconds)
                .build();
    }

    /** @return a builder to construct an instance of {@link DBCacheEntry} */
    public static DBCacheEntry.Builder builder() {
        return new AutoValue_DBCacheEntry.Builder().setResponseHeaders(ImmutableMap.of());
    }

    /** Provides a builder for creating a {@link DBCacheEntry} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the Url for which the entry is cached */
        public abstract DBCacheEntry.Builder setUrl(String url);

        /** sets the response body corresponding to the URL */
        public abstract DBCacheEntry.Builder setResponseBody(String responseBody);

        /** sets the response headers corresponding to the URL */
        public abstract DBCacheEntry.Builder setResponseHeaders(
                ImmutableMap<String, List<String>> responseHeaders);

        /** Sets the creation timestamp of the cached entry */
        public abstract DBCacheEntry.Builder setCreationTimestamp(Instant creationTimestamp);

        /** Sets the maxAge in seconds for which the entry is considered fresh */
        public abstract DBCacheEntry.Builder setMaxAgeSeconds(long maxAgeSeconds);

        /**
         * Returns a {@link com.android.adservices.service.common.cache.DBCacheEntry} build with the
         * information provided in this builder *
         */
        public abstract DBCacheEntry build();
    }

    /**
     * Converters to help serialize and deserialize objects from them to be persisted and retrieved
     * from DB.
     */
    public static class Converters {
        private static final String KEY_VALUE_SEPARATOR = "=";
        private static final String VALUES_SEPARATOR = ",";
        private static final String ENTRIES_SEPARATOR = ";";

        private Converters() {}

        /**
         * @param responseHeaders a map of response headers for a web request
         * @return serialized version of response headers
         */
        @TypeConverter
        public static String serializeResponseHeaders(
                @Nullable ImmutableMap<String, List<String>> responseHeaders) {
            if (responseHeaders == null || responseHeaders.isEmpty()) {
                return "";
            }
            try {
                List<String> serializedHeaders = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                        sb.append(entry.getKey() + KEY_VALUE_SEPARATOR);
                        sb.append(String.join(VALUES_SEPARATOR, entry.getValue()).trim());
                        serializedHeaders.add(sb.toString());
                        sb.setLength(0);
                    }
                }
                return String.join(ENTRIES_SEPARATOR, serializedHeaders);
            } catch (Exception e) {
                LogUtil.e(e, "Failed to serialize response headers");
            }
            return "";
        }

        /**
         * @param responseHeadersString serialized version of response headers
         * @return a map of response headers for a web request
         */
        @TypeConverter
        public static ImmutableMap<String, List<String>> deserializeResponseHeaders(
                @Nullable String responseHeadersString) {
            ImmutableMap.Builder<String, List<String>> responseHeadersBuilder =
                    ImmutableMap.builder();
            if (responseHeadersString == null || responseHeadersString.isEmpty()) {
                return responseHeadersBuilder.build();
            }
            try {
                String[] deserializedHeaders = responseHeadersString.split(ENTRIES_SEPARATOR);
                for (String entry : deserializedHeaders) {
                    String[] keyValuePair = entry.split(KEY_VALUE_SEPARATOR);
                    if (!keyValuePair[0].isEmpty() && !keyValuePair[1].isEmpty()) {
                        List<String> list = new ArrayList<>();
                        for (String x : keyValuePair[1].split(VALUES_SEPARATOR)) {
                            String s = x.trim();
                            if (!s.isEmpty()) {
                                list.add(s);
                            }
                        }
                        responseHeadersBuilder.put(keyValuePair[0], list);
                    }
                }
            } catch (Exception e) {
                LogUtil.e(e, "Failed to deserialize response headers");
            }
            return responseHeadersBuilder.build();
        }
    }
}
