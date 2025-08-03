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
package com.android.server.cts.device.statsd;

import static org.junit.Assert.fail;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Build, install and run tests with following command:
 * atest CtsStatsdHostTestCases
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RestrictedPermissionTests {

    /**
     * Verify that the {@link android.Manifest.permission#READ_RESTRICTED_STATS}
     * permission is only held by at most one package.
     */
    @Test
    @CddTest(requirements={"9.8.17/C-0-1"})
    public void testReadRestrictedStatsPermission() throws Exception {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[]{
                android.Manifest.permission.READ_RESTRICTED_STATS
        }, PackageManager.MATCH_ALL);

        int count = 0;
        String pkgNames = "";
        for (PackageInfo pkg : holding) {
            int uid = pm.getApplicationInfo(pkg.packageName, 0).uid;
            if (UserHandle.isApp(uid)) {
                pkgNames += pkg.packageName + "\n";
                count++;
            }
        }
        if (count > 1) {
            fail("Only one app may hold the READ_RESTRICTED_STATS permission; found packages: \n"
                    + pkgNames);
        }
    }
}
