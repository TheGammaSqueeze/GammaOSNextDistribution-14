/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.helpers.features.common;

import static android.platform.helpers.CommonUtils.launchApp;
import static android.provider.Settings.ACTION_SETTINGS;

import android.platform.helpers.CommonUtils;
import android.platform.helpers.features.Page;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;

/**
 * Helper class for Settings Home page. This contains the all the possible helper methods for the
 * page.
 * HSV: https://hsv.googleplex.com/5014201858785280
 */
public class SettingsPage implements Page {
    private static final String SETTINGS_PAGE_IDENTIFIER =
            "com.android.settings:id/settings_homepage_container";
    private static final String SETTINGS_PAGE_NAME = "Settings";

    /**
     * To get page selector used for determining the given page
     *
     * @return an instance of given page selector identifier.
     */
    @Override
    public BySelector getPageTitleSelector() {
        return By.res(SETTINGS_PAGE_IDENTIFIER);
    }

    /**
     * To get the name of the given page.
     *
     * @return the name of the given page
     */
    @Override
    public String getPageName() {
        return SETTINGS_PAGE_NAME;
    }

    /**
     * Action required to open the app or page otherwise it will remain empty.
     */
    @Override
    public void open() {
        launchApp(CommonUtils.LaunchAppWith.ACTIVITY, ACTION_SETTINGS, SETTINGS_PAGE_NAME);
    }
}
