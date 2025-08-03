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

package com.android.tv.settings.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.SystemSoundsPlayer;
import com.android.tv.settings.TvSettingsApplication;

/**
 * SwitchPreference that plays custom select / deselect sound effects
 */
public class SwitchWithSoundPreference extends SwitchPreference {
    private final SystemSoundsPlayer mSystemSoundsPlayer;

    public SwitchWithSoundPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TvSettingsApplication app = (TvSettingsApplication) context.getApplicationContext();
        mSystemSoundsPlayer = app.getSystemSoundsPlayer();
    }


    public SwitchWithSoundPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TvSettingsApplication app = (TvSettingsApplication) context.getApplicationContext();
        mSystemSoundsPlayer = app.getSystemSoundsPlayer();
    }

    public SwitchWithSoundPreference(Context context) {
        super(context);
        TvSettingsApplication app = (TvSettingsApplication) context.getApplicationContext();
        mSystemSoundsPlayer = app.getSystemSoundsPlayer();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (mSystemSoundsPlayer == null) {
            return;
        }
        // disable default click sound effect played by android.view.View#performClick()
        holder.itemView.setSoundEffectsEnabled(false);
    }

    @Override
    protected void onClick() {
        boolean checkedBeforeClick = isChecked();
        super.onClick();
        if (mSystemSoundsPlayer == null) {
            return;
        }
        if (isChecked() != checkedBeforeClick) {
            if (isChecked()) {
                mSystemSoundsPlayer.playSoundEffect(SystemSoundsPlayer.FX_SELECT);
            } else {
                mSystemSoundsPlayer.playSoundEffect(SystemSoundsPlayer.FX_DESELECT);
            }
        }
    }
}
