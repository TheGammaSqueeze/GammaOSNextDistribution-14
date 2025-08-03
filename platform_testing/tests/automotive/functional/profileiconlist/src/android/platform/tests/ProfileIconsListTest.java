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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoHomeHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.MultiUserHelper;
import android.platform.scenario.multiuser.MultiUserConstants;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ProfileIconsListTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private static final String USER_NAME = MultiUserConstants.SECONDARY_USER_NAME;

    public static final String GUEST_NAME = "Guest";

    private final MultiUserHelper mMultiUserHelper = MultiUserHelper.getInstance();
    private HelperAccessor<IAutoHomeHelper> mHomeHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public ProfileIconsListTest() {
        mHomeHelper = new HelperAccessor<>(IAutoHomeHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }

    /**
     * Setup expectations: Comparing the profiles based on position.
     *
     * <p>This method is used to compare profiles based on position.
     */
    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testListOfProfiles() throws Exception {
        mMultiUserHelper.createUser(USER_NAME, false);
        mHomeHelper.get().openStatusBarProfiles();
        List<String> list = mHomeHelper.get().getUserProfileNames();
        assertFalse("newUser at index first position", USER_NAME.equals(list.get(0)));
        int position = list.size() - 1;
        assertTrue("Guest profile not at last position", GUEST_NAME.equals(list.get(position)));
        assertTrue(
                "Add a Profile option is not displayed",
                mSettingHelper.get().checkMenuExists("Add a profile"));
        assertTrue(
                "Profiles and Accounts option is not displayed",
                mSettingHelper.get().checkMenuExists("Profiles & accounts settings"));
        // Currently logged user highlighted in status bar
        // This test step is already covered in the Guest profile test case
    }
}
