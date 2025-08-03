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

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoLockScreenHelper;
import android.platform.helpers.IAutoLockScreenHelper.LockType;
import android.platform.helpers.IAutoSecuritySettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LockScreenTest {

    private static final String PASSWORD = "test4fun";
    private static final String PIN = "1234";

    private HelperAccessor<IAutoLockScreenHelper> mLockScreenHelper;
    private HelperAccessor<IAutoSecuritySettingsHelper> mSecuritySettingsHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public LockScreenTest() throws Exception {
        mSecuritySettingsHelper = new HelperAccessor<>(IAutoSecuritySettingsHelper.class);
        mLockScreenHelper = new HelperAccessor<>(IAutoLockScreenHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }

    @Before
    public void openSecuritySettingFacet() {
        mSettingHelper.get().openSetting(SettingsConstants.SECURITY_SETTINGS);
        assertTrue(
                "Security settings did not open",
                mSettingHelper.get().checkMenuExists("Profile lock"));
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    public void testLockUnlockScreenByPassword() {
        mLockScreenHelper.get().lockScreenBy(LockType.PASSWORD, PASSWORD);
        mLockScreenHelper.get().unlockScreenBy(LockType.PASSWORD, PASSWORD);
        assertTrue("Device is not locked", mSecuritySettingsHelper.get().isDeviceLocked());
        mSecuritySettingsHelper.get().unlockByPassword(PASSWORD);
        mSecuritySettingsHelper.get().removeLock();
        assertTrue(
                "Password has not been removed", !mSecuritySettingsHelper.get().isDeviceLocked());
    }
}
