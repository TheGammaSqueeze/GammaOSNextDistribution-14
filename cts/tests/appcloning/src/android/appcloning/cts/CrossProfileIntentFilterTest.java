/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.appcloning.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Throwables;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CrossProfileIntentFilterTest extends AppCloningDeviceTestBase {
    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mPackageManager = mContext.getPackageManager();

        assumeTrue(SdkLevel.isAtLeastU());
        assumeTrue(isHardwareSupported());
    }


    /**
     * This test would create clone profile, as clone profile have CrossProfileIntentFilter access
     * control as SYSTEM. For SYSTEM, only system user should be able to add/modify
     * CrossProfileIntentFilter , non-system user would face SecurityException.
     */
    @Test
    @RequireMultiUserSupport
    @ApiTest(apis = {"android.content.pm.PackageManager#addCrossProfileIntentFilter"})
    public void addCrossProfileIntentFilterForSystemAccessTest() {

        //Creating clone user as it has SYSTEM Access Control
        int cloneUserId = createAndStartUser("testCloneUser",
                UserManager.USER_TYPE_PROFILE_CLONE, "0");
        try {
            Throwable thrown = assertThrows(RuntimeException.class, () -> {
                SystemUtil.runWithShellPermissionIdentity(() ->
                        mPackageManager
                                .addCrossProfileIntentFilter(new IntentFilter(Intent.ACTION_SEND),
                                        0, cloneUserId, 0));
            });
            assertThat(thrown).hasCauseThat().isInstanceOf(SecurityException.class);
            Throwable cause = Throwables.getCauseAs(thrown, SecurityException.class);
            assertThat(cause).hasMessageThat().contains("CrossProfileIntentFilter cannot be "
                    + "accessed by user");
        } finally {
            removeUser(cloneUserId);
        }
    }

    /**
     * This test would create new user which have default CrossProfileIntentFilter access
     * control as ALL. For ALL, any user can add/remove CrossProfileIntentFilter.
     */
    @Test
    @RequireMultiUserSupport
    @ApiTest(apis = {"android.content.pm.PackageManager#addCrossProfileIntentFilter"})
    public void addCrossProfileIntentFilterForAllAccessTest() {
        int userId = createAndStartUser("testUser",
                null, null);
        try {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mPackageManager
                            .addCrossProfileIntentFilter(new IntentFilter(Intent.ACTION_SEND),
                                    0, userId, 0));
        } finally {
            removeUser(userId);
        }
    }

    private boolean isHardwareSupported() {
        // Clone profiles are not supported on all form factors, only on handheld devices.
        PackageManager pm = mPackageManager;
        return !pm.hasSystemFeature(pm.FEATURE_EMBEDDED)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

}
