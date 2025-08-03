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

import static org.testng.Assert.assertThrows;

import android.content.pm.PackageInstaller;
import android.platform.test.annotations.AppModeFull;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

@AppModeFull(reason = "PackageInstaller cannot be accessed by instant apps")
public class NoRollbackPermissionTest {
    @Test
    public void testCreateInstallSessionWithReasonRollbackFails() throws Exception {
        // The INSTALL_REASON_ROLLBACK allows an APK to be rolled back to a previous signing key
        // without setting the ROLLBACK capability in the lineage. Since only signature|privileged
        // apps can hold the necessary permission to initiate a rollback ensure apps without this
        // permission cannot set rollback as the install reason.
        PackageInstaller packageInstaller =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager()
                        .getPackageInstaller();
        PackageInstaller.SessionParams parentParams = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        parentParams.setRequestDowngrade(true);
        parentParams.setMultiPackage();
        // The constant PackageManager.INSTALL_REASON_ROLLBACK is hidden from apps, but an app can
        // still use its constant value.
        parentParams.setInstallReason(5);
        assertThrows(SecurityException.class, () -> packageInstaller.createSession(parentParams));
    }
}
