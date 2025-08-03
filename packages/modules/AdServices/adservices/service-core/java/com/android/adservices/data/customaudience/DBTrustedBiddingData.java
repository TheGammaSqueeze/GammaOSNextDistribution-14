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

package com.android.adservices.data.customaudience;

import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An ad-tech can define what data needs to be fetched from a trusted server (trusted_bidding_keys)
 * and where it should be fetched from (trusted_bidding_uri).
 */
@TypeConverters(DBTrustedBiddingData.Converters.class)
public class DBTrustedBiddingData {

    public static final String QUERY_PARAM_KEYS = "keys";

    @ColumnInfo(name = "uri")
    @NonNull
    private final Uri mUri;

    @ColumnInfo(name = "keys")
    @NonNull
    private final List<String> mKeys;

    public DBTrustedBiddingData(@NonNull Uri uri, @NonNull List<String> keys) {
        Objects.requireNonNull(uri, "Uri must be provided.");
        Objects.requireNonNull(keys, "Keys must be provided.");

        this.mUri = uri;
        this.mKeys = keys;
    }

    /**
     * Parse parcelable {@link TrustedBiddingData} to storage model {@link DBTrustedBiddingData}.
     *
     * @param parcelable the service model.
     * @return storage model
     */
    @Nullable
    public static DBTrustedBiddingData fromServiceObject(@Nullable TrustedBiddingData parcelable) {
        if (parcelable == null) {
            return null;
        }
        return new DBTrustedBiddingData.Builder()
                .setUri(parcelable.getTrustedBiddingUri())
                .setKeys(parcelable.getTrustedBiddingKeys())
                .build();
    }

    /** Returns the estimated size, in bytes, of the strings contained in this object. */
    public int size() {
        int totalSize = mUri.toString().getBytes().length;
        for (String key : mKeys) {
            totalSize += key.getBytes().length;
        }

        return totalSize;
    }

    /** The URI to use to request the data. */
    @NonNull
    public Uri getUri() {
        return mUri;
    }

    /**
     * The IDs of the information items we need to collect.
     * <p>They will be passed using a keys' query argument as for example:
     * <p>https://www.kv-server.example/getvalues?keys=key1,key2
     */
    @NonNull
    public List<String> getKeys() {
        return mKeys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DBTrustedBiddingData)) return false;
        DBTrustedBiddingData that = (DBTrustedBiddingData) o;
        return mUri.equals(that.mUri) && mKeys.equals(that.mKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUri, mKeys);
    }

    @Override
    public String toString() {
        return "DBTrustedBiddingData{" + "mUri=" + mUri + ", mKeys=" + mKeys + '}';
    }

    /**
     * Builder to construct a {@link DBTrustedBiddingData}.
     */
    public static class Builder {
        private Uri mUri;
        private List<String> mKeys;

        public Builder() {
        }

        public Builder(@NonNull DBTrustedBiddingData trustedBiddingData) {
            Objects.requireNonNull(trustedBiddingData, "trust bidding data must be provided.");
            mUri = trustedBiddingData.getUri();
            mKeys = trustedBiddingData.getKeys();
        }

        /** See {@link #getUri()} for detail. */
        public Builder setUri(@NonNull Uri uri) {
            this.mUri = uri;
            return this;
        }

        /**
         * See {@link #getKeys()} for detail.
         */
        public Builder setKeys(@NonNull List<String> keys) {
            this.mKeys = keys;
            return this;
        }

        /**
         * Build the {@link DBTrustedBiddingData}.
         *
         * @return the built {@link DBTrustedBiddingData}.
         */
        public DBTrustedBiddingData build() {
            return new DBTrustedBiddingData(mUri, mKeys);
        }
    }

    /**
     * Room DB type converters.
     * <p>Register custom type converters here.
     * <p>{@link TypeConverter} registered here only apply to data access with {@link
     * DBTrustedBiddingData}
     */
    public static class Converters {
        /**
         * Serialize {@link List<String>} to String.
         */
        @TypeConverter
        @Nullable
        public static String serializeStringList(@Nullable List<String> stringList) {
            return Optional.ofNullable(stringList)
                    .map(JSONArray::new)
                    .map(JSONArray::toString)
                    .orElse(null);
        }

        /**
         * Deserialize {@link List<String>} from String.
         */
        @TypeConverter
        @Nullable
        public static List<String> deserializeStringList(@Nullable String stringList) {
            try {
                if (Objects.isNull(stringList)) {
                    return null;
                }
                JSONArray jsonArray = new JSONArray(stringList);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    result.add(jsonArray.getString(i));
                }
                return result;
            } catch (JSONException e) {
                throw new RuntimeException("Error deserialize List<String>.", e);
            }
        }
    }
}
