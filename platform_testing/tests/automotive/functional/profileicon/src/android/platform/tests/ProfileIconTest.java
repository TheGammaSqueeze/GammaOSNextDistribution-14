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

import android.content.pm.UserInfo;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoHomeHelper;
import android.platform.helpers.IAutoUserHelper;
import android.platform.helpers.MultiUserHelper;
import android.platform.scenario.multiuser.MultiUserConstants;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProfileIconTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private static final String USER_NAME = MultiUserConstants.GUEST_NAME;

    private final MultiUserHelper mMultiUserHelper = MultiUserHelper.getInstance();

    private HelperAccessor<IAutoUserHelper> mUsersHelper;

    private HelperAccessor<IAutoHomeHelper> mHomeHelper;

    public ProfileIconTest() {
        mHomeHelper = new HelperAccessor<>(IAutoHomeHelper.class);
        mUsersHelper = new HelperAccessor<>(IAutoUserHelper.class);
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testToVerifyGuestProfile() throws Exception {
        mUsersHelper.get().switchUser("Driver", USER_NAME);
        UserInfo guest = mMultiUserHelper.getCurrentForegroundUserInfo();
        mMultiUserHelper.switchAndWaitForStable(guest.id, MultiUserConstants.WAIT_FOR_IDLE_TIME_MS);
        assertTrue(
                "Failed to switch from current user to Guest Profile.",
                USER_NAME.equals(mHomeHelper.get().getUserProfileName()));
    }
}
