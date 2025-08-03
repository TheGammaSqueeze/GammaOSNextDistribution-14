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

package com.android.permissioncontroller.role.ui.specialappaccess.handheld;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.role.ui.RestrictionAwarePreferenceMixin;
import com.android.permissioncontroller.role.ui.RoleApplicationPreference;
import com.android.settingslib.widget.AppSwitchPreference;

/** {@link AppSwitchPreference} that is a role application preference. */
public class HandheldSwitchPreference extends AppSwitchPreference
        implements RoleApplicationPreference {

    private RestrictionAwarePreferenceMixin mRestrictionAwarePreferenceMixin =
            new RestrictionAwarePreferenceMixin(this);

    public HandheldSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public HandheldSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HandheldSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HandheldSwitchPreference(@NonNull Context context) {
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
    public HandheldSwitchPreference asTwoStatePreference() {
        return this;
    }
}
