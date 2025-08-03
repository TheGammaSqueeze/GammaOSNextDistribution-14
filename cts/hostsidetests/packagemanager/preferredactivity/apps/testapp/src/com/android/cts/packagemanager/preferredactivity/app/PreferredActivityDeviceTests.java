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

package com.android.cts.packagemanager.preferredactivity.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PreferredActivityDeviceTests {
    private static final String ACTIVITY_ACTION_NAME = "android.intent.action.PMTEST";
    private static final String PACKAGE_NAME =
            "com.android.cts.packagemanager.preferredactivity.app";
    private static final String ACTIVITY_NAME = PACKAGE_NAME + ".MainActivity";
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();

    @Test
    public void testAddOnePreferredActivity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_PREFERRED_APPLICATIONS);
        final IntentFilter intentFilter = new IntentFilter(ACTIVITY_ACTION_NAME);
        final ComponentName[] componentName = {new ComponentName(PACKAGE_NAME, ACTIVITY_NAME)};
        try {
            mPackageManager.addPreferredActivity(intentFilter, IntentFilter.MATCH_CATEGORY_HOST,
                    componentName, componentName[0]);
        } catch (SecurityException e) {
            fail("addPreferredActivity failed: " + e.getMessage());
        }
    }

    @Test
    public void testHasPreferredActivities() {
        final int expectedNumPreferredActivities = Integer.parseInt(
                InstrumentationRegistry.getArguments().getString("numPreferredActivities"));
        final List<ComponentName> outActivities = new ArrayList<>();
        final List<IntentFilter> outFilters = new ArrayList<>();
        mPackageManager.getPreferredActivities(outFilters, outActivities, PACKAGE_NAME);
        assertEquals(expectedNumPreferredActivities, outActivities.size());
        assertEquals(expectedNumPreferredActivities, outFilters.size());
    }
}
