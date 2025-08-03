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

package com.android.permissioncontroller.role.ui.handheld;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.role.ui.RestrictionAwarePreferenceMixin;
import com.android.permissioncontroller.role.ui.RolePreference;
import com.android.settingslib.widget.TwoTargetPreference;

/**
 * {@link Preference} with a settings button.
 *
 * @see com.android.settings.widget.GearPreference
 */
// Made public for com.android.permissioncontroller.role.ui.specialappaccess.handheld
public class HandheldRolePreference extends TwoTargetPreference implements RolePreference {

    private final RestrictionAwarePreferenceMixin mRestrictionAwarePreferenceMixin =
            new RestrictionAwarePreferenceMixin(this);

    @Nullable
    private OnSecondTargetClickListener mOnSecondTargetClickListener;

    public HandheldRolePreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    public HandheldRolePreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    public HandheldRolePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public HandheldRolePreference(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        setIconSize(ICON_SIZE_MEDIUM);
    }

    @Override
    protected int getSecondTargetResId() {
        return R.layout.settings_button_preference_widget;
    }

    @Override
    protected boolean shouldHideSecondTarget() {
        return mOnSecondTargetClickListener == null;
    }

    @Override
    public void setOnSecondTargetClickListener(@Nullable OnSecondTargetClickListener listener) {
        mOnSecondTargetClickListener = listener;
        notifyChanged();
    }

    @Override
    public void setRestrictionIntent(@Nullable Intent restrictionIntent) {
        mRestrictionAwarePreferenceMixin.setRestrictionIntent(restrictionIntent);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View settingsButton = holder.findViewById(R.id.settings_button);
        if (mOnSecondTargetClickListener != null) {
            settingsButton.setVisibility(View.VISIBLE);
            settingsButton.setOnClickListener(view ->
                    mOnSecondTargetClickListener.onSecondTargetClick(this));
        } else {
            settingsButton.setVisibility(View.GONE);
            settingsButton.setOnClickListener(null);
        }
        // Make the settings button enabled even if the preference itself is disabled.
        settingsButton.setEnabled(true);

        mRestrictionAwarePreferenceMixin.onAfterBindViewHolder(holder);
    }

    @NonNull
    @Override
    public HandheldRolePreference asPreference() {
        return this;
    }
}
