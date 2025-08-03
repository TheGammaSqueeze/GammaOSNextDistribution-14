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

import com.android.permission.safetylabel.DataLabelConstants.DataUsage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Data usage category representation containing one or more {@link DataType}. Valid category keys
 * are defined in {@link DataCategoryConstants}, each category has a valid set of types {@link
 * DataType}, which are mapped in {@link DataTypeConstants}
 */
public class DataCategory {
    private final Map<String, DataType> mDataTypes;

    private DataCategory(@NonNull Map<String, DataType> dataTypes) {
        this.mDataTypes = dataTypes;
    }

    /**
     * Returns a {@link java.util.Collections.UnmodifiableMap} of {@link String} category to {@link
     * DataCategory} created by parsing a {@link PersistableBundle}
     */
    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    static Map<String, DataCategory> getDataCategoryMap(
            @Nullable PersistableBundle dataLabelBundle, @DataUsage @NonNull String dataUsage) {
        if (dataLabelBundle == null) {
            return Collections.emptyMap();
        }

        PersistableBundle dataCategoryMapBundle = dataLabelBundle.getPersistableBundle(dataUsage);
        if (dataCategoryMapBundle == null) {
            return Collections.emptyMap();
        }

        Map<String, DataCategory> dataCategoryMap = new HashMap<>();
        for (String category : DataCategoryConstants.VALID_CATEGORIES) {
            DataCategory dataCategory = getDataCategory(dataCategoryMapBundle, dataUsage, category);
            if (dataCategory != null) {
                dataCategoryMap.put(category, dataCategory);
            }
        }
        return Collections.unmodifiableMap(dataCategoryMap);
    }

    /**
     * Returns a {@link DataCategory} created by parsing a {@link PersistableBundle}, or {@code
     * null} if parsing results in an invalid or empty DataCategory
     */
    @Nullable
    @VisibleForTesting
    static DataCategory getDataCategory(
            @Nullable PersistableBundle dataCategoryMapBundle,
            @NonNull String dataUsage,
            @NonNull String category) {
        if (dataCategoryMapBundle == null) {
            return null;
        }

        PersistableBundle dataCategoryBundle = dataCategoryMapBundle.getPersistableBundle(category);

        Map<String, DataType> dataTypeMap =
                DataType.getDataTypeMap(dataCategoryBundle, dataUsage, category);
        if (dataTypeMap.isEmpty()) {
            return null;
        }

        return new DataCategory(dataTypeMap);
    }

    /** Return the type {@link Map} of String type key to {@link DataType} */
    @NonNull
    public Map<String, DataType> getDataTypes() {
        return mDataTypes;
    }
}
