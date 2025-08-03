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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.AutomotiveConfigConstants;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoPrivacySettingsHelper;
import android.platform.helpers.IAutoPrivacySettingsHelper.Permission;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoUISettingsHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrivacyPermissionManagerTest {
    private HelperAccessor<IAutoUISettingsHelper> mSettingUIHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoPrivacySettingsHelper> mPrivacyHelper;

    public PrivacyPermissionManagerTest() throws Exception {
        mSettingUIHelper = new HelperAccessor<>(IAutoUISettingsHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mPrivacyHelper = new HelperAccessor<>(IAutoPrivacySettingsHelper.class);
    }

    @Before
    public void openPrivacyAppPermissions() {
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        mSettingHelper.get().openMenuWith("App permissions");
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
        mSettingHelper.get().exit();
    }

    @Test
    public void testVerifyUIAppPermissions() {
        assertTrue(
                "Privacy dashboard is not Present",
                mSettingUIHelper.get().hasUIElement(AutomotiveConfigConstants.PRIVACY_DASHBOARD));
        assertTrue(
                "Permission manager is not Present",
                mSettingUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.PRIVACY_PERMISSION_MANAGER));
        assertTrue(
                "Recent permission decisions is not Present",
                mSettingUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.RECENT_PERMISSION_DECISIONS));
    }

    @Test
    public void testPermissionManagerRecentDecisionAllow() {
        mSettingUIHelper.get().openUIOptions(AutomotiveConfigConstants.PRIVACY_PERMISSION_MANAGER);
        mPrivacyHelper
                .get()
                .privacyDashboardPermissionPage(AutomotiveConfigConstants.CALENDAR_PERMISSION);
        mPrivacyHelper.get().changePermissions(Permission.ALLOW);
        goBackToSettingsScreen();
        openPrivacyAppPermissions();
        assertEquals(
                "You gave Calendar access to calendar",
                mPrivacyHelper
                        .get()
                        .getRecentPermissionDecisionMessage(
                                AutomotiveConfigConstants.PRIVACY_CALENDAR));
    }

    @Test
    public void testPermissionManagerRecentDecisionDontAllow() {
        mSettingUIHelper.get().openUIOptions(AutomotiveConfigConstants.PRIVACY_PERMISSION_MANAGER);
        mPrivacyHelper
                .get()
                .privacyDashboardPermissionPage(AutomotiveConfigConstants.CALENDAR_PERMISSION);
        mPrivacyHelper.get().changePermissions(Permission.DONT_ALLOW);
        goBackToSettingsScreen();
        openPrivacyAppPermissions();
        assertEquals(
                "You denied Calendar access to calendar",
                mPrivacyHelper
                        .get()
                        .getRecentPermissionDecisionMessage(
                                AutomotiveConfigConstants.PRIVACY_CALENDAR));
    }
}
