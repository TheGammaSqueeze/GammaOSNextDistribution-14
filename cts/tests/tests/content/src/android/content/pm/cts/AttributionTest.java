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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Attribution}.
 */
@AppModeFull // TODO(Instant) Figure out which APIs should work.
@RunWith(AndroidJUnit4.class)
public class AttributionTest {

    private static final String PACKAGE_NAME = "android.content.cts";
    private static final String[] TAGS =
            new String[] { "attribution_tag_one", "attribution_tag_two" };
    private static final String[] LABELS =
            new String[] { "attribution label one", "attribution label two" };
    private static final boolean[] SHOULD_SHOW = new boolean[] { false, true };
    private static final int NUM_ATTRIBUTIONS = 2;

    private static Context sContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    public void dontGetAttributions() throws Exception {
        PackageInfo packageInfo = sContext.getPackageManager().getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0));
        assertNotNull(packageInfo);
        assertNull(packageInfo.attributions);
    }

    @Test
    public void getAttributionsAndVerify() throws Exception {
        PackageInfo packageInfo = sContext.getPackageManager().getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ATTRIBUTIONS_LONG));
        assertNotNull(packageInfo);
        assertNotNull(packageInfo.attributions);
        assertEquals(packageInfo.attributions.length, NUM_ATTRIBUTIONS);
        for (int i = 0; i < NUM_ATTRIBUTIONS; i++) {
            assertEquals(packageInfo.attributions[i].getTag(), TAGS[i]);
            assertEquals(sContext.getString(packageInfo.attributions[i].getLabel()), LABELS[i]);
        }
    }
}
