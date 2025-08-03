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

package com.android.permissioncontroller.role.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

/**
 * {@link androidx.preference.Preference} with the widget layout as a separate target.
 *
 * This has to be an interface because we may need to extend from either the SettingsLib
 * {@link com.android.settingslib.widget.TwoTargetPreference} or the AndroidX
 * {@link androidx.preference.Preference}.
 *
 * @see com.android.settingslib.widget.TwoTargetPreference
 */
public interface TwoTargetPreference {

    /**
     * Set the listener for second target click.
     *
     * @param listener the listener
     */
    void setOnSecondTargetClickListener(@Nullable OnSecondTargetClickListener listener);

    /**
     * Return this preference as {@link Preference}.
     */
    @NonNull
    Preference asPreference();

    /**
     * Listener for second target click.
     */
    interface OnSecondTargetClickListener {

        /**
         * Callback when the second target is clicked.
         *
         * @param preference the {@link TwoTargetPreference} that was clicked
         */
        void onSecondTargetClick(@NonNull TwoTargetPreference preference);
    }
}
