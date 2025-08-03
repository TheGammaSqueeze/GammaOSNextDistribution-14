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

package com.android.car.developeroptions;

import com.android.settings.SettingsActivity;

/**
 * Main activity for CarDeveloperOptions.
 */
public class CarDevelopmentSettingsDashboardActivity extends SettingsActivity {
    private static final String CAR_DEVELOPMENT_SETTINGS_FRAGMENT =
            "com.android.car.developeroptions.CarDevelopmentSettingsDashboardFragment";

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return CAR_DEVELOPMENT_SETTINGS_FRAGMENT.equals(fragmentName);
    }

    @Override
    protected boolean isToolbarEnabled() {
        // Disable the default Settings toolbar in favor of a chassis toolbar.
        return false;
    }
}
