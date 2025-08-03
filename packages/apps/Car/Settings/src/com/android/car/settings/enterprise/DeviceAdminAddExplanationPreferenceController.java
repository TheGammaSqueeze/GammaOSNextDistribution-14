/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.enterprise;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

/**
 * Controller for the (optional, set by intent extra ) explanation of why the admin is being added.
 */
public final class DeviceAdminAddExplanationPreferenceController
        extends BaseDeviceAdminAddPreferenceController<Preference> {

    @Nullable
    private CharSequence mExplanation;

    public DeviceAdminAddExplanationPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) return superStatus;

        return TextUtils.isEmpty(mExplanation) ? CONDITIONALLY_UNAVAILABLE : AVAILABLE;
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setTitle(mExplanation);
    }

    /**
     * Sets the explanation of why this admin is being added.
     */
    public DeviceAdminAddExplanationPreferenceController setExplanation(
            @Nullable CharSequence explanation) {
        mLogger.d("setExplanation(): " + explanation);
        if (explanation != null) {
            mExplanation = TextUtils.makeSafeForPresentation(
                    explanation.toString(), /* maxCharactersToConsider= */ 0,
                    /* ellipsizeDp= */ 0, TextUtils.SAFE_STRING_FLAG_TRIM
                    | TextUtils.SAFE_STRING_FLAG_FIRST_LINE);
        }
        return this;
    }
}
