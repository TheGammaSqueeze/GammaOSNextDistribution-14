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

package android.app.cts;

import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ManageFsiTest {

    private static final String STUB_PACKAGE_NAME = "android.app.stubs";

    @Test
    public void testManageAppUseFsiIntent_ResolvesToActivity() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PackageManager pm = context.getPackageManager();
        assumeFalse("TV does not support fullscreen intents",
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
        assumeFalse("Watch does not support fullscreen intents",
                pm.hasSystemFeature(PackageManager.FEATURE_WATCH));

        final Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        intent.setData(Uri.parse("package:" + STUB_PACKAGE_NAME));
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, MATCH_DEFAULT_ONLY);
        assertNotNull(resolveInfo);
    }
}
