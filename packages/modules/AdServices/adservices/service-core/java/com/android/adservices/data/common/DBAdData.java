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

package com.android.adservices.data.common;

import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Represents data specific to an ad that is necessary for ad selection and rendering. */
public class DBAdData {
    @NonNull private final Uri mRenderUri;
    @NonNull private final String mMetadata;
    @NonNull private final Set<Integer> mAdCounterKeys;
    @Nullable private final AdFilters mAdFilters;
    @Nullable private final String mAdRenderId;

    public DBAdData(
            @NonNull Uri renderUri,
            @NonNull String metadata,
            @NonNull Set<Integer> adCounterKeys,
            @Nullable AdFilters adFilters,
            @Nullable String adRenderId) {
        Objects.requireNonNull(renderUri);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(adCounterKeys);
        mRenderUri = renderUri;
        mMetadata = metadata;
        mAdCounterKeys = adCounterKeys;
        mAdFilters = adFilters;
        mAdRenderId = adRenderId;
    }

    /** Returns the estimated size, in bytes, of the components of this object. */
    public int size() {
        int totalSize = mRenderUri.toString().getBytes().length + mMetadata.getBytes().length;
        // Each int should cost about 4 bytes
        totalSize += 4 * mAdCounterKeys.size();
        if (mAdFilters != null) {
            totalSize += mAdFilters.getSizeInBytes();
        }
        if (mAdRenderId != null) {
            totalSize += mAdRenderId.getBytes().length;
        }
        return totalSize;
    }

    /** See {@link AdData#getRenderUri()} */
    @NonNull
    public Uri getRenderUri() {
        return mRenderUri;
    }

    /** See {@link AdData#getMetadata()} */
    @NonNull
    public String getMetadata() {
        return mMetadata;
    }

    /** See {@link AdData#getAdCounterKeys()} */
    @NonNull
    public Set<Integer> getAdCounterKeys() {
        return mAdCounterKeys;
    }

    /** See {@link AdData#getAdFilters()} */
    @Nullable
    public AdFilters getAdFilters() {
        return mAdFilters;
    }

    /** See {@link AdData#getAdRenderId()} */
    @Nullable
    public String getAdRenderId() {
        return mAdRenderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DBAdData)) return false;
        DBAdData adData = (DBAdData) o;
        return mRenderUri.equals(adData.mRenderUri)
                && mMetadata.equals(adData.mMetadata)
                && mAdCounterKeys.equals(adData.getAdCounterKeys())
                && Objects.equals(mAdFilters, adData.getAdFilters())
                && Objects.equals(mAdRenderId, adData.getAdRenderId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRenderUri, mMetadata, mAdCounterKeys, mAdFilters, mAdRenderId);
    }

    @Override
    public String toString() {
        return "DBAdData{"
                + "mRenderUri="
                + mRenderUri
                + ", mMetadata='"
                + mMetadata
                + '\''
                + ", mAdCounterKeys="
                + mAdCounterKeys
                + ", mAdFilters="
                + mAdFilters
                + ", mAdRenderId='"
                + mAdRenderId
                + '\''
                + '}';
    }

    /** Builder to construct a {@link DBAdData}. */
    public static class Builder {
        @Nullable private Uri mRenderUri;
        @Nullable private String mMetadata;
        @NonNull private Set<Integer> mAdCounterKeys = new HashSet<>();
        @Nullable private AdFilters mAdFilters;
        @Nullable private String mAdRenderId;

        public Builder() {}

        public Builder(AdData copyFrom) {
            setRenderUri(copyFrom.getRenderUri());
            setAdFilters(copyFrom.getAdFilters());
            setMetadata(copyFrom.getMetadata());
            setAdCounterKeys(copyFrom.getAdCounterKeys());
            setAdRenderId(copyFrom.getAdRenderId());
        }

        /** See {@link AdData#getRenderUri()} for details. */
        public Builder setRenderUri(@NonNull Uri renderUri) {
            this.mRenderUri = renderUri;
            return this;
        }

        /** See {@link AdData#getMetadata()} for details. */
        public Builder setMetadata(@NonNull String metadata) {
            this.mMetadata = metadata;
            return this;
        }

        /** See {@link AdData#getAdCounterKeys()} for details. */
        public Builder setAdCounterKeys(@NonNull Set<Integer> adCounterKeys) {
            this.mAdCounterKeys = adCounterKeys;
            return this;
        }

        /** See {@link AdData#getAdFilters()} for details. */
        public Builder setAdFilters(@Nullable AdFilters adFilters) {
            this.mAdFilters = adFilters;
            return this;
        }

        /** See {@link AdData#getAdRenderId()} for details. */
        @NonNull
        public Builder setAdRenderId(@Nullable String adRenderId) {
            this.mAdRenderId = adRenderId;
            return this;
        }

        /**
         * Build the {@link DBAdData}.
         *
         * @return the built {@link DBAdData}.
         */
        public DBAdData build() {
            return new DBAdData(mRenderUri, mMetadata, mAdCounterKeys, mAdFilters, mAdRenderId);
        }
    }
}
