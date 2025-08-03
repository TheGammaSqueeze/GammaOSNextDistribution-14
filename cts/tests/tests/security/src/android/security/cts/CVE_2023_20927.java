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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_20927 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 244216503)
    @Test
    public void testPocCVE_2023_20927() {
        try {
            Context context = getApplicationContext();
            PackageManager pm = context.getPackageManager();

            // Skip test for non-automotive builds
            assumeTrue(
                    "Skipping test: " + PackageManager.FEATURE_AUTOMOTIVE + " missing",
                    pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

            // Permissions added in com.android.car package as a part of the fix
            List<String> missingPermissions = new ArrayList<String>();
            missingPermissions.add("android.car.permission.BIND_PROJECTION_SERVICE");
            missingPermissions.add("android.car.permission.BIND_VMS_CLIENT");
            missingPermissions.add(
                    "android.car.permission.BIND_INSTRUMENT_CLUSTER_RENDERER_SERVICE");
            missingPermissions.add("android.car.permission.BIND_CAR_INPUT_SERVICE");

            // Fetch the permission of com.android.car package
            final String pkgName = "com.android.car";
            PackageInfo info = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS);
            assumeTrue(
                    "Package info for " + pkgName + " not fetched properly!! ",
                    info.packageName.equals(pkgName) && info.coreApp);
            PermissionInfo[] permissionArray = info.permissions;
            if (permissionArray != null) {
                for (PermissionInfo perm : permissionArray) {
                    if (missingPermissions.contains(perm.name)) {
                        missingPermissions.remove(perm.name);
                    }
                }
            }

            // Fail if any of the 4 permissions is missing
            assertTrue(
                    "Vulnerable to b/244216503!"
                            + missingPermissions.toString()
                            + " missing in"
                            + " CarService.apk",
                    missingPermissions.size() == 0);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
