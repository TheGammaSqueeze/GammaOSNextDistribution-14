/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.apps;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class LaunchItemTest {

    private LaunchItem createTestLaunchItem(String packageName, String name, Drawable banner,
            Drawable icon, CharSequence label, long priority) {
        final ActivityInfo activityInfo = mock(ActivityInfo.class);
        when(activityInfo.loadBanner(any(PackageManager.class))).thenReturn(banner);
        activityInfo.packageName = packageName;
        activityInfo.name = name;

        final ResolveInfo resolveInfo = mock(ResolveInfo.class);
        when(resolveInfo.loadIcon(any(PackageManager.class))).thenReturn(icon);
        when(resolveInfo.loadLabel(any(PackageManager.class))).thenReturn(label);
        resolveInfo.activityInfo = activityInfo;

        final Context context = mock(Context.class);
        when(context.getPackageManager()).thenReturn(null);

        return new LaunchItem(context, resolveInfo, priority);
    }

    @Test
    public void testGetIntent() throws Exception {
        final LaunchItem item =
                createTestLaunchItem("com.example.testPackage", "com.example.testName",
                        null, null, null, 0);
        assertNotNull(item.getIntent());
        assertNotNull(item.getIntent().getComponent());
        assertEquals("com.example.testPackage", item.getIntent().getComponent().getPackageName());
        assertEquals("com.example.testName", item.getIntent().getComponent().getClassName());
    }

    @Test
    public void testCompareTo() throws Exception {
        final LaunchItem item = createTestLaunchItem("", "", null, null, "A label", 0);
        final LaunchItem priorityItem = createTestLaunchItem("", "", null, null, "Z label", 1);
        final LaunchItem alphaItem = createTestLaunchItem("", "", null, null, "Z label", 0);

        assertTrue("priorityItem should sort before item", item.compareTo(priorityItem) > 0);
        assertTrue("alphaItem should sort after item", item.compareTo(alphaItem) < 0);
        assertTrue("item should sort equal to item", item.compareTo(item) == 0);
    }
}
