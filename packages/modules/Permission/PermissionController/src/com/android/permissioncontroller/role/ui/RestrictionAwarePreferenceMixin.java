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

package com.android.permissioncontroller.role.ui;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Mixin for implementing {@link RestrictionAwarePreference}.
 */
public class RestrictionAwarePreferenceMixin {

    private static final String LOG_TAG = RestrictionAwarePreferenceMixin.class.getSimpleName();

    @NonNull
    private final Preference mPreference;

    @Nullable
    private Intent mRestrictionIntent;

    public RestrictionAwarePreferenceMixin(@NonNull Preference preference) {
        mPreference = preference;
    }

    /**
     * Implementation for {@link RestrictionAwarePreference#setRestrictionIntent}.
     */
    public void setRestrictionIntent(@Nullable Intent restrictionIntent) {
        mRestrictionIntent = restrictionIntent;
        mPreference.setEnabled(mRestrictionIntent == null);
    }

    /**
     * Call after {@link Preference#onBindViewHolder} to apply blocking effects.
     */
    public void onAfterBindViewHolder(@NonNull PreferenceViewHolder holder) {
        if (mRestrictionIntent != null) {
            // We set the item view to enabled to make the preference row clickable.
            // Normal disabled preferences have the whole view hierarchy disabled, so by making only
            // the top-level itemView enabled, we don't change the fact that the whole preference
            // still "looks" disabled (see Preference.onBindViewHolder).
            // Preference.onBindViewHolder sets the onClickListener as well on each preference, so
            // we don't need to unset the listener here (we wouldn't know the correct one anyway).
            // This approach is used already by com.android.settingslib.RestrictedPreferenceHelper.
            holder.itemView.setEnabled(true);
            holder.itemView.setOnClickListener(
                    view -> view.getContext().startActivity(mRestrictionIntent));
        }
    }
}
