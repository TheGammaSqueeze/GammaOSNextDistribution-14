/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Vibrator;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.List;

/**
 * Methods to check device properties. They pass iff the check returns true.
 */
public class Checkers {
    private static final String TAG = Checkers.class.getSimpleName();

    @Test
    public void checkVibratorSupported() {
        Vibrator v = InstrumentationRegistry.getContext().getSystemService(Vibrator.class);
        assertThat(v.hasVibrator()).isTrue();
    }

    @Test
    public void checkWifiEnhancedPowerReportingSupported() {
        WifiManager wm = InstrumentationRegistry.getContext().getSystemService(WifiManager.class);
        assertThat(wm.isEnhancedPowerReportingSupported()).isTrue();
    }

    @Test
    public void checkValidLightSensor() {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        assertThat(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)).isTrue();
    }

    @Test
    public void checkBrightnessSliderPermission() {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        assertThat(
                    numberOfSystemAppsWithPermission(
                        Manifest.permission.BRIGHTNESS_SLIDER_USAGE, pm)).isGreaterThan(0);
    }

    @Test
    public void checkConfigShowUserSwitcher() {
        Resources resources = Resources.getSystem();
        int resourceId = resources.getIdentifier("config_showUserSwitcherByDefault",
                "bool", "android");
        assertThat(resources.getBoolean(resourceId)).isTrue();
    }

    /**
     * Returns the number of system apps with the given permission.
     */
    private int numberOfSystemAppsWithPermission(String permission, PackageManager pm) {
        List<PackageInfo> packages = pm.getPackagesHoldingPermissions(
                    new String[]{permission}, PackageManager.MATCH_SYSTEM_ONLY);
        packages.removeIf(packageInfo -> packageInfo.packageName.equals("com.android.shell"));
        return packages.size();
    }
}
