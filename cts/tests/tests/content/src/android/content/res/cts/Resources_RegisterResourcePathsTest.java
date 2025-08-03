/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.res.cts;

import android.content.pm.ApplicationInfo;
import android.content.res.Flags;
import android.content.res.Resources;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class Resources_RegisterResourcePathsTest {
    private Resources mResources;

    @Before
    public void setUp() throws Exception {
        mResources = InstrumentationRegistry.getContext().getResources();
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testRegisterResourcePathsNotSupported() {
        String fakeUniqueId = "";
        ApplicationInfo fakeApplicationInfo = new ApplicationInfo();
        Assert.assertThrows(UnsupportedOperationException.class, () -> {
            mResources.registerResourcePaths(fakeUniqueId, fakeApplicationInfo);
        });
    }
}
