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

package android.permission.cts;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.permission.PermissionManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link PermissionManager}
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot talk to permission manager")
public class PermissionManagerTest {
    private final Context mContext = InstrumentationRegistry.getTargetContext();

    @Test
    public void testRuntimePermissionsVersion() throws Exception {
        final PermissionManager permissionManager =
                mContext.getSystemService(PermissionManager.class);
        final int version = callWithShellPermissionIdentity(() ->
                permissionManager.getRuntimePermissionsVersion());
        assertThat(version).isAtLeast(0);
        runWithShellPermissionIdentity(() ->
                permissionManager.setRuntimePermissionsVersion(version));
    }
}
