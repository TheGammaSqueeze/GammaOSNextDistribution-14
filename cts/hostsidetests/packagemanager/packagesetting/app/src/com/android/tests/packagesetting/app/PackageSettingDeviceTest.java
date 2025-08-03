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

package com.android.tests.packagesetting.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PackageSettingDeviceTest {
    @Test
    public void testCodePathMatchesExpected() throws Exception {
        String expectedCodePath =
                InstrumentationRegistry.getArguments().getString("expectedCodePath");
        String packageName =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        PackageInfo pi = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().getPackageInfo(packageName, 0);
        String apkPath = pi.applicationInfo.sourceDir;
        Assert.assertTrue(apkPath.startsWith(expectedCodePath));
    }

    @Test
    public void testFirstInstallTimeMatchesExpected() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String packageName = context.getPackageName();
        String expectedFirstInstallTimeStr = InstrumentationRegistry.getArguments().getString(
                "expectedFirstInstallTime");
        final long expectedFirstInstallTime = Long.parseLong(expectedFirstInstallTimeStr);
        final PackageManager packageManager = context.getPackageManager();
        PackageInfo pi = packageManager.getPackageInfo(packageName,
                PackageManager.PackageInfoFlags.of(0));
        Assert.assertTrue(Math.abs(expectedFirstInstallTime - pi.firstInstallTime) < 1000 /* ms */);
    }
}
