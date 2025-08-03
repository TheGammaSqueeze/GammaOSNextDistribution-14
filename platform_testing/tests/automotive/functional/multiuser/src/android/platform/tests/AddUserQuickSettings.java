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
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoUserHelper;
import android.platform.helpers.MultiUserHelper;
import android.platform.helpers.SettingsConstants;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test will create user through API and delete the same user from UI
 * <p> Set system property to run MU test: adb shell setprop fw.stop_bg_users_on_switch 0
 */
@RunWith(AndroidJUnit4.class)
public class AddUserQuickSettings {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private final MultiUserHelper mMultiUserHelper = MultiUserHelper.getInstance();
    // private static final String userName = MultiUserConstants.SECONDARY_USER_NAME;
    private HelperAccessor<IAutoUserHelper> mUsersHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public AddUserQuickSettings() {
        mUsersHelper = new HelperAccessor<>(IAutoUserHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }

    @After
    public void goBackToHomeScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testAddNonAdminUser() throws Exception {
        // create new user quick settings
        UserInfo initialUser = mMultiUserHelper.getCurrentForegroundUserInfo();
        mUsersHelper.get().addUserQuickSettings(initialUser.name);
        // switched to new user
        UserInfo newUser = mMultiUserHelper.getCurrentForegroundUserInfo();
        // switch from new user to initial user
        mUsersHelper.get().switchUser(newUser.name, initialUser.name);
        // verify new user is seen in list of users
        assertTrue(mMultiUserHelper.getUserByName(newUser.name) != null);
        // Verify new user is non-admin Profile
        mSettingHelper.get().openSetting(SettingsConstants.PROFILE_ACCOUNT_SETTINGS);
        assertFalse("New user has Admin Access", mUsersHelper.get().isNewUserAnAdmin(newUser.name));
        // remove new user
        mMultiUserHelper.removeUser(newUser);
    }
}
