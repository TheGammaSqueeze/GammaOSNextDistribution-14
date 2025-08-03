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

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoSystemSettingsHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;

@RunWith(AndroidJUnit4.class)
public class SystemSettingTest {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoSystemSettingsHelper> mSystemSettingsHelper;

    public SystemSettingTest() throws Exception {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mSystemSettingsHelper = new HelperAccessor<>(IAutoSystemSettingsHelper.class);
    }


    @Before
    public void openSystemFacet() {
        mSettingHelper.get().openSetting(SettingsConstants.SYSTEM_SETTINGS);
        assertTrue(
                "System settings did not open",
                mSettingHelper.get().checkMenuExists("Languages & input"));
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    public void testDeviceModel() {
        String model = android.os.Build.MODEL;
        assertTrue(
                "Model from API and Model from UI are not the same",
                mSystemSettingsHelper.get().getDeviceModel().endsWith(model));
    }

    @Test
    public void testAndroidVersion() {
        String androidVersion = android.os.Build.VERSION.RELEASE;
        assertTrue(
                "Android Version from API and Android Version from UI are not the same",
                mSystemSettingsHelper.get().getAndroidVersion().endsWith(androidVersion));
    }

    @Test
    public void testAndroidSecurityPatchLevel() {
        String day = android.os.Build.VERSION.SECURITY_PATCH;
        String[] arr = day.split("-");
        Date date =
                new Date(
                        Integer.valueOf(arr[0]),
                        Integer.valueOf(arr[1]) - 1,
                        Integer.valueOf(arr[2]));
        assertTrue(
                "security patch from API and security patch from UI are not the same",
                date.equals(mSystemSettingsHelper.get().getAndroidSecurityPatchLevel()));
    }

    @Test
    public void testKernelVersion() {
        String kernelVersion = System.getProperty("os.version");
        assertTrue(
                "kernel version from API and kernel from UI are not the same",
                mSystemSettingsHelper.get().getKernelVersion().startsWith(kernelVersion));
    }

    @Test
    public void testBuildNumber() {
        String buildNumber = android.os.Build.DISPLAY;
        assertTrue(
                "Build number from API and Build number from UI are not the same",
                buildNumber.equals(mSystemSettingsHelper.get().getBuildNumber()));
    }
}
