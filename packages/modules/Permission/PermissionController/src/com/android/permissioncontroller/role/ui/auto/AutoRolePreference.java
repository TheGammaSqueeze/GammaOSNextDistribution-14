/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.auto;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.role.ui.RestrictionAwarePreferenceMixin;
import com.android.permissioncontroller.role.ui.RolePreference;
import com.android.permissioncontroller.role.ui.TwoTargetPreference;

/**
 * Preference for use in auto lists. Extends {@link TwoTargetPreference} in order to make sure of
 * shared logic between phone and auto settings UI.
 */
public class AutoRolePreference extends Preference implements RolePreference {

    private RestrictionAwarePreferenceMixin mRestrictionAwarePreferenceMixin =
            new RestrictionAwarePreferenceMixin(this);

    public AutoRolePreference(@NonNull Context context,
            @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public AutoRolePreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AutoRolePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoRolePreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void setOnSecondTargetClickListener(@Nullable OnSecondTargetClickListener listener) {}

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
    public AutoRolePreference asPreference() {
        return this;
    }
}
