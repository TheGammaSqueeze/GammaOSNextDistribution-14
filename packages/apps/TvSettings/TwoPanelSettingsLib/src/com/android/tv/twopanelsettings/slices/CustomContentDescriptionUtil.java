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

package com.android.tv.twopanelsettings.slices;

import static com.android.tv.twopanelsettings.slices.HasCustomContentDescription.CONTENT_DESCRIPTION_SEPARATOR;

import android.content.Context;

import com.android.tv.twopanelsettings.R;

/**
 * Generate fallback content descriptions for when custom content descriptions are not specified.
 */
public final class CustomContentDescriptionUtil {

    /**
     * Generate fallback content description for Switch Preferences
     * @param context
     * @param contentDescription to generate first portion of full content description
     * @param isChecked is the toggle state of SwitchPreference
     * @return the full content description for switch preferences
     */
    public static String getFullSwitchContentDescription(
            Context context, String contentDescription, Boolean isChecked) {
        StringBuilder switchContentDescription = new StringBuilder();
        switchContentDescription.append(contentDescription);
        switchContentDescription.append(CONTENT_DESCRIPTION_SEPARATOR);
        if (isChecked) {
            switchContentDescription.append(context.getString(R.string.switch_on));
            switchContentDescription.append(CONTENT_DESCRIPTION_SEPARATOR);
        } else {
            switchContentDescription.append(context.getString(R.string.switch_off));
            switchContentDescription.append(CONTENT_DESCRIPTION_SEPARATOR);
        }
        switchContentDescription
                .append(context.getString(R.string.switch_talkback_identifier));
        return switchContentDescription.toString();
    }
}
