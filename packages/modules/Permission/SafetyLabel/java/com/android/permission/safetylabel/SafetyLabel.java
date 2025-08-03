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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;

/** Safety Label representation containing zero or more {@link DataCategory} for data shared */
public class SafetyLabel {
    private static final String TAG = "SafetyLabel";
    @VisibleForTesting static final String KEY_SAFETY_LABEL = "safety_labels";
    public static final String KEY_VERSION = "version";
    private final DataLabel mDataLabel;

    private SafetyLabel(@NonNull DataLabel dataLabel) {
        this.mDataLabel = dataLabel;
    }

    /** Returns {@link SafetyLabel} created by parsing a metadata {@link PersistableBundle} */
    @Nullable
    public static SafetyLabel getSafetyLabelFromMetadata(@Nullable PersistableBundle bundle) {
        if (bundle == null || bundle.isEmpty()) {
            return null;
        }

        if (bundle.getLong(KEY_VERSION, 0L) <= 0) {
            Log.w(TAG, String.format(
                    Locale.US, "The top level metadata bundle have an invalid version %d",
                    bundle.getLong(KEY_VERSION, 0L)));
            return null;
        }

        PersistableBundle safetyLabelBundle = bundle.getPersistableBundle(KEY_SAFETY_LABEL);
        if (safetyLabelBundle == null) {
            return null;
        }

        long safetyLabelsVersion = safetyLabelBundle.getLong(KEY_VERSION, 0L);
        if (safetyLabelsVersion <= 0) {
            Log.w(TAG, String.format(Locale.US,
                    "The safety labels have an invalid version %d", safetyLabelsVersion));
            return null;
        }

        DataLabel dataLabel = DataLabel.getDataLabel(safetyLabelBundle);
        if (dataLabel == null) {
            return null;
        }
        return new SafetyLabel(dataLabel);
    }

    /** Returns the data label for the safety label */
    @NonNull
    public DataLabel getDataLabel() {
        return mDataLabel;
    }
}
