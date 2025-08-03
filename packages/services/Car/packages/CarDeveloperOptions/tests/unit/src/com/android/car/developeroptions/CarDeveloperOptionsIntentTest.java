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

package com.android.car.developeroptions;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarDeveloperOptionsIntentTest {
    private static final List<String> ACTIVITY_ALLOWLIST = Arrays.asList(
            "com.android.car.developeroptions.CarDevelopmentSettingsDashboardActivity",
            "com.android.settings.development.DevelopmentSettingsDisabledActivity");

    private Context mContext = ApplicationProvider.getApplicationContext();
    private PackageManager mPm;

    @Before
    public void setup() {
        mPm = mContext.getPackageManager();
    }

    @Test
    public void testAvailableIntentActivities_onlyAllowlisted() {
        List<ResolveInfo> results = mPm.queryIntentActivities(createSettingsPackageIntent(),
                PackageManager.MATCH_ALL);
        List<String> nonMatchingItems = new ArrayList<>();
        for (ResolveInfo resolved : results) {
            if (!ACTIVITY_ALLOWLIST.contains(resolved.getComponentInfo().name)) {
                nonMatchingItems.add(resolved.getComponentInfo().name);
            }
        }
        assertWithMessage("Unexpected activities found: " + nonMatchingItems.toString())
                .that(nonMatchingItems.size()).isEqualTo(0);
    }

    @Test
    public void testAvailableIntentServices_returnsZero() {
        List<ResolveInfo> results = mPm.queryIntentServices(createSettingsPackageIntent(),
                PackageManager.MATCH_ALL);
        List<String> nonMatchingItems = new ArrayList<>();
        for (ResolveInfo resolved : results) {
            nonMatchingItems.add(resolved.getComponentInfo().name);
        }
        assertWithMessage("Unexpected services found: " + nonMatchingItems.toString())
                .that(nonMatchingItems.size()).isEqualTo(0);
    }

    @Test
    public void testAvailableBroadcastReceivers_returnsZero() {
        List<ResolveInfo> results = mPm.queryBroadcastReceivers(createSettingsPackageIntent(),
                PackageManager.MATCH_ALL);
        List<String> nonMatchingItems = new ArrayList<>();
        for (ResolveInfo resolved : results) {
            nonMatchingItems.add(resolved.getComponentInfo().name);
        }
        assertWithMessage("Unexpected broadcast receivers found: " + nonMatchingItems.toString())
                .that(nonMatchingItems.size()).isEqualTo(0);
    }

    @Test
    public void testAvailableContentProviders_returnsZero() {
        List<ResolveInfo> results = mPm.queryIntentContentProviders(createSettingsPackageIntent(),
                PackageManager.MATCH_ALL);
        List<String> nonMatchingItems = new ArrayList<>();
        for (ResolveInfo resolved : results) {
            nonMatchingItems.add(resolved.getComponentInfo().name);
        }
        assertWithMessage("Unexpected content providers found: " + nonMatchingItems.toString())
                .that(nonMatchingItems.size()).isEqualTo(0);
    }

    private Intent createSettingsPackageIntent() {
        Intent intent = new Intent();
        intent.setPackage("com.android.car.developeroptions");
        return intent;
    }
}
