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

import static com.android.permission.safetylabel.DataLabelConstants.DATA_USAGE_COLLECTED;
import static com.android.permission.safetylabel.DataLabelConstants.DATA_USAGE_SHARED;

import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Map;

/**
 * Data label representation with data shared and data collected maps containing zero or more
 * {@link DataCategory}
 */
public class DataLabel {
    @VisibleForTesting static final String KEY_DATA_LABEL = "data_labels";
    private final Map<String, DataCategory> mDataCollected;
    private final Map<String, DataCategory> mDataShared;

    public DataLabel(
            @NonNull Map<String, DataCategory> dataCollected,
            @NonNull Map<String, DataCategory> dataShared) {
        mDataCollected = dataCollected;
        mDataShared = dataShared;
    }

    /** Returns a {@link DataLabel} created by parsing a SafetyLabel {@link PersistableBundle} */
    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static DataLabel getDataLabel(@Nullable PersistableBundle safetyLabelBundle) {
        if (safetyLabelBundle == null) {
            return null;
        }

        PersistableBundle dataLabelBundle = safetyLabelBundle.getPersistableBundle(KEY_DATA_LABEL);
        if (dataLabelBundle == null) {
            return null;
        }

        Map<String, DataCategory> dataCollectedCategoryMap =
                DataCategory.getDataCategoryMap(dataLabelBundle, DATA_USAGE_COLLECTED);
        Map<String, DataCategory> dataSharedCategoryMap =
                DataCategory.getDataCategoryMap(dataLabelBundle, DATA_USAGE_SHARED);
        return new DataLabel(dataCollectedCategoryMap, dataSharedCategoryMap);
    }

    /**
     * Returns the data collected {@link Map} of {@link
     * com.android.permission.safetylabel.DataCategoryConstants.Category} to {@link DataCategory}
     */
    @NonNull
    public Map<String, DataCategory> getDataCollected() {
        return mDataCollected;
    }

    /**
     * Returns the data shared {@link Map} of {@link
     * com.android.permission.safetylabel.DataCategoryConstants.Category} to {@link DataCategory}
     */
    @NonNull
    public Map<String, DataCategory> getDataShared() {
        return mDataShared;
    }
}
