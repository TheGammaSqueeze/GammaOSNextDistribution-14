/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SdkSuppress;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for permission handling for sdk sandbox uid range.
 */
@AppModeFull(reason = "Instant apps can't access PermissionManager")
@RunWith(AndroidJUnit4ClassRunner.class)
public class SdkSandboxPermissionTest {

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    public void testSdkSandboxHasInternetPermission() throws Exception {
        final Context ctx = getInstrumentation().getContext();
        int ret = ctx.checkPermission(
                Manifest.permission.INTERNET,
                /* pid= */ -1 /* invalid pid */,
                Process.toSdkSandboxUid(19999));
        assertThat(ret).isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    public void testSdkSandboxDoesNotHaveFineLocationPermission() throws Exception {
        final Context ctx = getInstrumentation().getContext();
        int ret = ctx.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                /* pid= */ -1 /* invalid pid */,
                Process.toSdkSandboxUid(19999));
        assertThat(ret).isEqualTo(PackageManager.PERMISSION_DENIED);
    }
}
