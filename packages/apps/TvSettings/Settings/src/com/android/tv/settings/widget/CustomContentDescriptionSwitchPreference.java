/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

import com.android.tv.twopanelsettings.slices.CustomContentDescriptionUtil;
import com.android.tv.twopanelsettings.slices.HasCustomContentDescription;

/**
 * Preference that allows external classes to set its view's content description for accessibility
 * purposes.
 */
public class CustomContentDescriptionSwitchPreference extends SwitchWithSoundPreference implements
        HasCustomContentDescription {

    private String mContentDescription;

    public CustomContentDescriptionSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (!TextUtils.isEmpty(mContentDescription)) {
            holder.itemView.setContentDescription(
                    CustomContentDescriptionUtil.getFullSwitchContentDescription(
                            getContext(), mContentDescription, this.isChecked()));
        }
    }

    /**
     * Sets the accessibility content description that will be read to the TalkBack users when they
     * focus on this preference.
     */
    public void setContentDescription(String contentDescription) {
        this.mContentDescription = contentDescription;
    }

    public String getContentDescription() {
        return mContentDescription;
    }
}
