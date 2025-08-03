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

package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.AutomotiveConfigConstants;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoAppInfoSettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoUISettingsHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppInfoVerifyUIElementsTest {
    private HelperAccessor<IAutoAppInfoSettingsHelper> mAppInfoSettingsHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoUISettingsHelper> mSettingsUIHelper;

    private static final String CALENDAR_APP = "Calendar";

    public AppInfoVerifyUIElementsTest() throws Exception {
        mAppInfoSettingsHelper = new HelperAccessor<>(IAutoAppInfoSettingsHelper.class);
        mSettingsUIHelper = new HelperAccessor<>(IAutoUISettingsHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }

    @Before
    public void openAppInfoFacet() {
        mSettingHelper.get().openSetting(SettingsConstants.APPS_SETTINGS);
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
        mSettingHelper.get().exit();
    }

    @Test
    public void testVerifyAppsPermissionUIElements() {
        assertTrue(
                "Apps setting did not open.",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.RECENTLY_OPENED_UI_ELEMENT));
        mAppInfoSettingsHelper.get().showAllApps();
        mAppInfoSettingsHelper.get().selectApp(CALENDAR_APP);
        assertTrue(
                "Stop app Button is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.STOP_APP_UI_ELEMENT));
        assertTrue(
                "Notification Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.NOTIFICATIONS_UI_ELEMENT));
        assertTrue(
                "Permissions Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.PERMISSIONS_UI_ELEMENT));
        assertTrue(
                "Storage and Cache Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.STORAGE_CACHE_UI_ELEMENT));
    }

    @Test
    public void testVerifyAppsInfoUIElements() {
        assertTrue(
                "Permission manager Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.PERMISSION_MANAGER_UI_ELEMENT));
        assertTrue(
                "Default apps Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.DEFAULT_APPS_UI_ELEMENT));
        assertTrue(
                "Unused apps Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.UNUSED_APPS_UI_ELEMENT));
        assertTrue(
                "Performance-impacting apps Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(
                                AutomotiveConfigConstants.PERFORMANCE_IMPACTING_APPS_UI_ELEMENT));
        assertTrue(
                "Special app access Option is not displayed",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.SPECIAL_APPS_UI_ELEMENT));
    }
}

