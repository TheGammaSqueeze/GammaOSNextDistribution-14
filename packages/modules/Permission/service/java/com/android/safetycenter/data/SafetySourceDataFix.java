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

package com.android.safetycenter.data;

import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetySourceData;

import androidx.annotation.Nullable;

import com.android.safetycenter.PendingIntentFactory;
import com.android.safetycenter.SafetyCenterConfigReader;

/**
 * Applies various workarounds and fixes to {@link SafetySourceData} as it's received.
 *
 * @hide
 */
public final class SafetySourceDataFix {

    private final DefaultActionOverrideFix mDefaultActionOverrideFix;
    private Context mContext;

    public SafetySourceDataFix(
            Context context,
            PendingIntentFactory pendingIntentFactory,
            SafetyCenterConfigReader safetyCenterConfigReader) {
        mContext = context;
        mDefaultActionOverrideFix =
                new DefaultActionOverrideFix(
                        context, pendingIntentFactory, safetyCenterConfigReader);
    }

    /**
     * Potentially overrides the {@link SafetySourceData}.
     *
     * <p>Should be called when the data is received from a source and before it's stored by Safety
     * Center.
     */
    @Nullable
    public SafetySourceData maybeOverrideSafetySourceData(
            String sourceId,
            @Nullable SafetySourceData safetySourceData,
            String packageName,
            @UserIdInt int userId) {
        if (safetySourceData == null) {
            return null;
        }

        if (AndroidLockScreenFix.shouldApplyFix(sourceId)) {
            safetySourceData = AndroidLockScreenFix.applyFix(mContext, safetySourceData);
        }

        if (DefaultActionOverrideFix.shouldApplyFix(sourceId)) {
            safetySourceData =
                    mDefaultActionOverrideFix.applyFix(
                            sourceId, safetySourceData, packageName, userId);
        }

        return safetySourceData;
    }
}
