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

package android.platform.tests;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.pm.UserInfo;
import android.os.SystemClock;
import android.platform.helpers.AutomotiveConfigConstants;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoUserHelper;
import android.platform.helpers.MultiUserHelper;
import android.platform.helpers.SettingsConstants;
import android.platform.scenario.multiuser.MultiUserConstants;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/** This test will create user through API and delete the same user from UI */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
public class GrantPermissionsToNonAdminUserTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();
    private static final String USER_NAME = MultiUserConstants.SECONDARY_USER_NAME;
    private static final int WAIT_TIME = 10000;
    private final MultiUserHelper mMultiUserHelper = MultiUserHelper.getInstance();
    private HelperAccessor<IAutoUserHelper> mUsersHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    public int mTargetUserId;

    public GrantPermissionsToNonAdminUserTest() {
        mUsersHelper = new HelperAccessor<>(IAutoUserHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }

    @After
    public void goBackToHomeScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testCreateNewUser() throws Exception {
        // create new user
        mMultiUserHelper.createUser(USER_NAME, false);
        SystemClock.sleep(WAIT_TIME);
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testOpenPermissionsPageOfNonAdmin() throws Exception {
        mSettingHelper.get().openSetting(SettingsConstants.PROFILE_ACCOUNT_SETTINGS);
        SystemClock.sleep(WAIT_TIME);
        mUsersHelper.get().openPermissionsPage(USER_NAME);
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testToggleOffAllPermissionsAndCheck() throws Exception {
        assertTrue(
                (mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.CREATE_NEW_PROFILE_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.CREATE_NEW_PROFILE_SWITCH)));
        assertTrue(
                (mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.MAKE_PHONE_CALLS_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.MAKE_PHONE_CALLS_SWITCH)));
        assertTrue(
                (mUsersHelper
                                .get()
                                .isToggleOn(
                                        AutomotiveConfigConstants
                                                .MESSAGING_VIA_CARS_MOBILE_DATA_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(
                                        AutomotiveConfigConstants
                                                .MESSAGING_VIA_CARS_MOBILE_DATA_SWITCH)));
        assertTrue(
                (mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.INSTALL_NEW_APPS_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.INSTALL_NEW_APPS_SWITCH)));
        assertTrue(
                (mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.UNINSTALL_APPS_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.UNINSTALL_APPS_SWITCH)));
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testToggleOnAllPermissionsAndCheck() throws Exception {
        assertTrue(
                !(mUsersHelper
                                .get()
                                .isToggleOn(AutomotiveConfigConstants.CREATE_NEW_PROFILE_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.CREATE_NEW_PROFILE_SWITCH)));
        assertTrue(
                !(mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.MAKE_PHONE_CALLS_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.MAKE_PHONE_CALLS_SWITCH)));
        assertTrue(
                !(mUsersHelper
                                .get()
                                .isToggleOn(
                                        AutomotiveConfigConstants
                                                .MESSAGING_VIA_CARS_MOBILE_DATA_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(
                                        AutomotiveConfigConstants
                                                .MESSAGING_VIA_CARS_MOBILE_DATA_SWITCH)));
        assertTrue(
                !(mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.INSTALL_NEW_APPS_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.INSTALL_NEW_APPS_SWITCH)));
        assertTrue(
                !(mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.UNINSTALL_APPS_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.UNINSTALL_APPS_SWITCH)));
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testUnCheckCreateNewProfilesPermissionAndSwitchToNonAdminUser() throws Exception {
        assertTrue(
                (mUsersHelper.get().isToggleOn(AutomotiveConfigConstants.CREATE_NEW_PROFILE_SWITCH))
                        && (mUsersHelper
                                .get()
                                .toggle(AutomotiveConfigConstants.CREATE_NEW_PROFILE_SWITCH)));
        // Switches the user mode to secondary and opens it profile account settings
        UserInfo currentUser = mMultiUserHelper.getCurrentForegroundUserInfo();
        mUsersHelper.get().switchUser(currentUser.name, USER_NAME);
        SystemClock.sleep(WAIT_TIME);
        mSettingHelper.get().openSetting(SettingsConstants.PROFILE_ACCOUNT_SETTINGS);
        SystemClock.sleep(WAIT_TIME);

        // verifies the current user and the visibility of Add profile
        currentUser = mMultiUserHelper.getCurrentForegroundUserInfo();
        assertTrue(currentUser.name.equals(USER_NAME));
        assertFalse(mUsersHelper.get().isVisibleAddProfile());
    }
}
