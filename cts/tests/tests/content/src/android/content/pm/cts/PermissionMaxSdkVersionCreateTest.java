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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

@AppModeFull(reason = "Instant apps only support a limited number of permissions")
@AppModeNonSdkSandbox(reason = "Sandboxed SDKs cannot declare permissions")
public class PermissionMaxSdkVersionCreateTest {
    private static final String MAX_SDK_IGNORE = "android.content.cts.permission.MAX_SDK_IGNORE";
    private static final String MAX_SDK_CREATE = "android.content.cts.permission.MAX_SDK_CREATE";
    private static final String NO_MAX_SDK = "android.content.cts.permission.NO_MAX_SDK";
    private static final String CURRENT_MAX_SDK = "android.content.cts.permission.CURRENT_MAX_SDK";

    private final PackageManager mPm =
            ApplicationProvider.getApplicationContext().getPackageManager();

    @Test
    public void testPermissionNoMaxSdkVersionCreated() {
        try {
            PermissionInfo permissionInfo = mPm.getPermissionInfo(NO_MAX_SDK, 0);
            assertEquals(permissionInfo.name, NO_MAX_SDK);
        } catch (NameNotFoundException e) {
            fail("Permission " + NO_MAX_SDK + " not found");
        }
    }

    @Test
    public void testPermissionCurrentMaxSdkVersionCreated() {
        try {
            PermissionInfo permissionInfo = mPm.getPermissionInfo(CURRENT_MAX_SDK, 0);
            assertEquals(permissionInfo.name, CURRENT_MAX_SDK);
        } catch (NameNotFoundException e) {
            fail("Permission " + NO_MAX_SDK + " not found");
        }
    }

    @Test
    public void testPermissionMaxSdkVersionCreated() {
        try {
            PermissionInfo permissionInfo = mPm.getPermissionInfo(MAX_SDK_CREATE, 0);
            assertEquals(permissionInfo.name, MAX_SDK_CREATE);
        } catch (NameNotFoundException e) {
            fail("Permission " + MAX_SDK_CREATE + " not found");
        }
    }

    @Test(expected = NameNotFoundException.class)
    public void testPermissionMaxSdkVersionIgnored() throws NameNotFoundException {
        mPm.getPermissionInfo(MAX_SDK_IGNORE, 0);
    }
}
