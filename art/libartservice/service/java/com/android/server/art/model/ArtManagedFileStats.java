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

package com.android.server.art.model;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;

import com.android.internal.annotations.Immutable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Map;

/**
 * Statistics of the files managed by ART of a package.
 *
 * @hide
 */
@SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@Immutable
public class ArtManagedFileStats {
    /**
     * Dexopt artifacts and runtime artifacts, excluding stale ones.
     *
     * Dexopt artifacts become stale when one of their dependencies (such as bootclasspath jars and
     * share libraries) has changed. Those stale files are excluded from the statistics. They may be
     * cleaned up or replaced by ART Service at any time.
     *
     * For a preload app, this type includes dexopt artifacts on readonly partitions if they are
     * up-to-date.
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    public static final int TYPE_DEXOPT_ARTIFACT = 0;
    /**
     * Reference profiles.
     *
     * They are the ones used during the last profile-guided dexopt.
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    public static final int TYPE_REF_PROFILE = 1;
    /**
     * Current profiles.
     *
     * They may be used during the next profile-guided dexopt.
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    public static final int TYPE_CUR_PROFILE = 2;

    /** @hide */
    // clang-format off
    @IntDef(prefix = {"TYPE_"}, value = {
        TYPE_DEXOPT_ARTIFACT,
        TYPE_REF_PROFILE,
        TYPE_CUR_PROFILE,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface FileTypes {}

    private @NonNull Map<Integer, Long> mTotalSizesBytes;

    /** @hide */
    public ArtManagedFileStats(long artifactsSize, long refProfilesSize, long curProfilesSize) {
        mTotalSizesBytes = Map.of(TYPE_DEXOPT_ARTIFACT, artifactsSize, TYPE_REF_PROFILE,
                refProfilesSize, TYPE_CUR_PROFILE, curProfilesSize);
    }
    /**
     * Returns the total size, in bytes, of the files of the given type.
     *
     * @throws IllegalArgumentException if {@code fileType} is not one of those defined in {@link
     *         FileTypes}.
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    public long getTotalSizeBytesByType(@FileTypes int fileType) {
        Long value = mTotalSizesBytes.get(fileType);
        if (value == null) {
            throw new IllegalArgumentException("Unknown file type " + fileType);
        }
        return value;
    }
}
