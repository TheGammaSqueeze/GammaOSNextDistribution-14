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

package android.settings.cts;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * For testing whether all the Settings intents can be triggered without crashing Settings app in
 * work profile.
 */
@RunWith(BedsteadJUnit4.class)
public class SettingsIntentsInWorkProfileTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @RequireRunOnWorkProfile
    public void settingActivity_launchIgnoreBatteryOptimizationIntent_shouldNotCrash() {
        final Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        startActivity(intent);
    }

    @Test
    @RequireRunOnWorkProfile
    public void settingActivity_launchManageWriteSettingsIntent_shouldNotCrash() {
        final Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:com.android.vending"));
        startActivity(intent);
    }

    @Test
    @RequireRunOnWorkProfile
    public void settingActivity_launchAppUsageIntent_shouldNotCrash() {
        final Intent intent = new Intent(Settings.ACTION_APP_USAGE_SETTINGS);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, "package:com.android.vending");
        startActivity(intent);
    }

    @Test
    @RequireRunOnWorkProfile
    public void settingActivity_launchApplicationDetailsIntent_shouldNotCrash() {
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:com.android.vending"));
        startActivity(intent);
    }

    @Test
    @RequireRunOnWorkProfile
    public void settingActivity_launchDeviceInfoSetting_shouldNotCrash() {
        final Intent intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
        startActivity(intent);
    }

    private void startActivity(Intent intent) {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = context.getPackageManager();

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo ri = packageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(ri);
        targetContext.startActivity(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
