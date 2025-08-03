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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_39738 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 216190509)
    @Test
    public void testPocCVE_2021_39738() {
        try {
            Context context = getApplicationContext();
            PackageManager pm = context.getPackageManager();

            // Skip test for non-automotive builds
            assumeTrue(
                    "Skipping test: " + PackageManager.FEATURE_AUTOMOTIVE + " missing",
                    pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

            // BluetoothPairingDialog activity should require BLUETOOTH_PRIVILEGED permission
            final String pkgName = "com.android.car.settings";
            ComponentName component =
                    new ComponentName(pkgName, pkgName + ".bluetooth.BluetoothPairingDialog");
            String permission = pm.getActivityInfo(component, 0 /* flags */).permission;
            assertTrue(
                    "Vulnerable to b/216190509",
                    permission.contains(android.Manifest.permission.BLUETOOTH_PRIVILEGED));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
