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

package com.android.permissioncontroller.role.ui.handheld;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.role.ui.RestrictionAwarePreferenceMixin;
import com.android.permissioncontroller.role.ui.RoleApplicationPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

/**
 * Preference used to represent apps that can be picked as a default app.
 */
public class HandheldRadioPreference extends SelectorWithWidgetPreference implements
        RoleApplicationPreference {

    private final RestrictionAwarePreferenceMixin mRestrictionAwarePreferenceMixin =
            new RestrictionAwarePreferenceMixin(this);

    public HandheldRadioPreference(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public HandheldRadioPreference(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HandheldRadioPreference(@NonNull Context context, boolean isCheckbox) {
        super(context, isCheckbox);
    }

    public HandheldRadioPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void setRestrictionIntent(@Nullable Intent restrictionIntent) {
        mRestrictionAwarePreferenceMixin.setRestrictionIntent(restrictionIntent);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mRestrictionAwarePreferenceMixin.onAfterBindViewHolder(holder);
    }

    @NonNull
    @Override
    public HandheldRadioPreference asTwoStatePreference() {
        return this;
    }
}
