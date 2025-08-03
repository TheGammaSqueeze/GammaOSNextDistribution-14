/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0922 extends StsExtraBusinessLogicTestCase {

    private Instrumentation mInstrumentation;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    /**
     * b/195630721
     */
    @AsbSecurityTest(cveBugId = 195630721)
    @Test
    public void testPocCVE_2021_0922() throws Exception {
        String packageName = "com.android.managedprovisioning";
        try {
            PackageInfo packageInfo = mInstrumentation.getContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            boolean isPermissionPresent = false;
            for (int i = 0; i < packageInfo.requestedPermissions.length; ++i) {
                if ((packageInfo.requestedPermissionsFlags[i]
                        & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    String permission = packageInfo.requestedPermissions[i];
                    if (permission.equals(android.Manifest.permission.MANAGE_APP_OPS_MODES)) {
                        isPermissionPresent = true;
                        break;
                    }
                }
            }
            assertTrue(isPermissionPresent);
        } catch (PackageManager.NameNotFoundException e) {
            assumeNoException(e);
        }
    }
}
