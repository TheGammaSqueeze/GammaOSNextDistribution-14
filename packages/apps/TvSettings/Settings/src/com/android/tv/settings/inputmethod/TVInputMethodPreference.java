/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.inputmethod;

import android.annotation.UserIdInt;
import android.content.Context;
import android.view.inputmethod.InputMethodInfo;
import android.widget.CompoundButton;

import androidx.preference.Preference;

import com.android.settingslib.inputmethod.InputMethodPreference;

/**
 * Input method preference for Android TV.
 *
 * This preference handle the switch logic for TV.
 */
public class TVInputMethodPreference extends InputMethodPreference {
    public TVInputMethodPreference(final Context prefContext, final InputMethodInfo imi,
            final boolean isAllowedByOrganization,
            final InputMethodPreference.OnSavePreferenceListener onSaveListener,
            final @UserIdInt int userId) {
        super(prefContext, imi, isAllowedByOrganization, onSaveListener, userId);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        final CompoundButton switchWidget = getSwitch();
        if (!switchWidget.isEnabled()) {
            return true;
        }
        final boolean newValue = !isChecked();
        switchWidget.setChecked(isChecked());
        callChangeListener(newValue);
        return true;
    }
}
