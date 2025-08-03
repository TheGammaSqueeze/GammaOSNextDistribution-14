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

package com.android.permission.safetylabel;

import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.permission.safetylabel.DataPurposeConstants.Purpose;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Data usage type representation. Types are specific to a {@link DataCategory} and contains
 * metadata related to the data usage purpose.
 */
public class DataType {
    @VisibleForTesting static final String KEY_PURPOSES = "purposes";
    @VisibleForTesting static final String KEY_IS_COLLECTION_OPTIONAL = "is_collection_optional";
    @VisibleForTesting static final String KEY_EPHEMERAL = "ephemeral";

    @Purpose private final Set<Integer> mPurposeSet;
    private final Boolean mIsCollectionOptional;
    private final Boolean mEphemeral;

    private DataType(
            @NonNull @Purpose Set<Integer> purposeSet,
            @Nullable Boolean isCollectionOptional,
            @Nullable Boolean ephemeral) {
        this.mPurposeSet = purposeSet;
        this.mIsCollectionOptional = isCollectionOptional;
        this.mEphemeral = ephemeral;
    }

    /**
     * Returns a {@link java.util.Collections.UnmodifiableMap} of String type key to {@link
     * DataType} created by parsing a {@link PersistableBundle}
     */
    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    static Map<String, DataType> getDataTypeMap(
            @Nullable PersistableBundle dataCategoryBundle,
            @NonNull String dataUsage,
            @NonNull String category) {
        if (dataCategoryBundle == null || dataCategoryBundle.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, DataType> dataTypeMap = new HashMap<>();
        Set<String> validDataTypesForCategory =
                DataTypeConstants.getValidDataTypesForCategory(category);
        for (String type : validDataTypesForCategory) {
            PersistableBundle dataTypeBundle = dataCategoryBundle.getPersistableBundle(type);
            DataType dataType = getDataType(dataTypeBundle, dataUsage);
            if (dataType != null) {
                dataTypeMap.put(type, dataType);
            }
        }
        return Collections.unmodifiableMap(dataTypeMap);
    }

    /**
     * Returns {@link DataType} created by parsing the {@link android.os.PersistableBundle}
     * representation of DataType
     */
    @Nullable
    @VisibleForTesting
    static DataType getDataType(
            @Nullable PersistableBundle dataTypeBundle, @NonNull String dataUsage) {
        if (dataTypeBundle == null || dataTypeBundle.isEmpty()) {
            return null;
        }

        // purposes are required, if not present, treat as invalid
        int[] purposeList = dataTypeBundle.getIntArray(KEY_PURPOSES);
        if (purposeList == null || purposeList.length == 0) {
            return null;
        }

        // Filter to set of valid purposes, and return invalid if empty
        Set<Integer> purposeSet = new HashSet<>();
        for (int purpose : purposeList) {
            if (DataPurposeConstants.getValidPurposes().contains(purpose)) {
                purposeSet.add(purpose);
            }
        }
        if (purposeSet.isEmpty()) {
            return null;
        }

        // Only set/expected for DATA COLLECTED. DATA SHARED should be null
        Boolean isCollectionOptional = null;
        Boolean ephemeral = null;
        if (DataLabelConstants.DATA_USAGE_COLLECTED.equals(dataUsage)) {
            isCollectionOptional =
                    dataTypeBundle.containsKey(KEY_IS_COLLECTION_OPTIONAL)
                            ? dataTypeBundle.getBoolean(KEY_IS_COLLECTION_OPTIONAL)
                            : null;
            ephemeral =
                    dataTypeBundle.containsKey(KEY_EPHEMERAL)
                            ? dataTypeBundle.getBoolean(KEY_EPHEMERAL)
                            : null;
        }

        return new DataType(purposeSet, isCollectionOptional, ephemeral);
    }

    /**
     * Returns {@link Set} of valid {@link Integer} purposes for using the associated data category
     * and type
     */
    @NonNull
    public Set<Integer> getPurposeSet() {
        return mPurposeSet;
    }

    /**
     * For data-collected, returns {@code true} if data usage is user optional and {@code false} if
     * data usage is required. Should return {@code null} for data-shared.
     */
    @Nullable
    public Boolean getIsCollectionOptional() {
        return mIsCollectionOptional;
    }

    /**
     * For data-collected, returns {@code true} if data usage is user optional and {@code false} if
     * data usage is processed ephemerally. Should return {@code null} for data-shared.
     */
    @Nullable
    public Boolean getEphemeral() {
        return mEphemeral;
    }
}
