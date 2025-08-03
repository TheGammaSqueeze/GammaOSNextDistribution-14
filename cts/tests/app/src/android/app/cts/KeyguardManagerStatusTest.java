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

package android.app.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNotNull;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class KeyguardManagerStatusTest {

    private static final String TAG = "KeyguardManagerStatusTest";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    @ApiTest(apis = {"android.app.KeyguardManager#isDeviceLocked"})
    @RequiresFlagsEnabled(android.multiuser.Flags.FLAG_SUPPORT_COMMUNAL_PROFILE)
    @Test
    public void testCommunalProfileIsConsideredUnlocked() throws Exception {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                android.Manifest.permission.QUERY_USERS);
        try {
            final Context instContext = mInstrumentation.getContext();

            final UserManager um = instContext.getSystemService(UserManager.class);
            final UserHandle communalUser = um.getCommunalProfile();

            // Not all devices will have a communal profile on it. If they don't, bypass the test.
            assumeNotNull(communalUser);

            final KeyguardManager kmOfCommunal =
                    instContext.createPackageContextAsUser("android", 0, communalUser)
                            .getSystemService(KeyguardManager.class);

            assertFalse(kmOfCommunal.isDeviceLocked());
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
