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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_0046 extends StsExtraBusinessLogicTestCase {
    private String mSettingsPackageName;

    @AsbSecurityTest(cveBugId = 299441833)
    @Test
    public void testPocCVE_2024_0046() {
        try {
            final PackageManager packageManager = getApplicationContext().getPackageManager();
            runWithShellPermissionIdentity(
                    () -> {
                        // Retrieve settings package name.
                        mSettingsPackageName = "com.android.settings";
                        ResolveInfo info =
                                packageManager.resolveActivityAsUser(
                                        new Intent(Settings.ACTION_SETTINGS),
                                        PackageManager.MATCH_SYSTEM_ONLY,
                                        UserHandle.USER_SYSTEM);
                        if (info != null && info.activityInfo != null) {
                            mSettingsPackageName = info.activityInfo.packageName;
                        }

                        // Check if settings app is system app.
                        assume().that(
                                        (packageManager.getApplicationInfo(mSettingsPackageName, 0)
                                                        .flags
                                                & ApplicationInfo.FLAG_SYSTEM))
                                .isEqualTo(ApplicationInfo.FLAG_SYSTEM);

                        // Check if settings app is not an instant app.
                        assume().that(packageManager.isInstantApp(mSettingsPackageName)).isFalse();

                        try (AutoCloseable withSettingsAsInstantApp = withSettingsAsInstantApp()) {
                            assertWithMessage(
                                            "Device is vulnerable to b/299441833, System apps can"
                                                    + " be reinstalled as instant apps")
                                    .that(
                                            poll(
                                                    () -> {
                                                        return packageManager.isInstantApp(
                                                                mSettingsPackageName);
                                                    }))
                                    .isFalse();
                        }
                    });
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable withSettingsAsInstantApp() {
        runShellCommand("pm install-existing --instant " + mSettingsPackageName);
        return () -> {
            runShellCommand("pm install-existing --full " + mSettingsPackageName);
        };
    }
}
