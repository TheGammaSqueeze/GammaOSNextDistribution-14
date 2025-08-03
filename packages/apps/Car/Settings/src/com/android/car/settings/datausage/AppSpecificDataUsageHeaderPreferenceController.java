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

package com.android.car.settings.datausage;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.car.settings.common.EntityHeaderPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller used to update the title and the icon for the header.
 */
public class AppSpecificDataUsageHeaderPreferenceController extends
        PreferenceController<EntityHeaderPreference> {

    private String mTitle;
    private Drawable mIcon;

    public AppSpecificDataUsageHeaderPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<EntityHeaderPreference> getPreferenceType() {
        return EntityHeaderPreference.class;
    }

    /** Sets the title. */
    public AppSpecificDataUsageHeaderPreferenceController setTitle(String title) {
        mTitle = title;
        return this;
    }

    /** Sets the icon. */
    public AppSpecificDataUsageHeaderPreferenceController setIcon(Drawable icon) {
        mIcon = icon;
        return this;
    }

    @Override
    protected void checkInitialized() {
        if (mTitle == null || mIcon == null) {
            throw new IllegalStateException(
                    "Title and Icon should be set before calling this function");
        }
    }

    @Override
    protected void updateState(EntityHeaderPreference preference) {
        preference.setTitle(mTitle);
        preference.setIcon(mIcon);
    }
}
