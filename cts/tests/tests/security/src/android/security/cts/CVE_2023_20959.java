/**
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

import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeNoException;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
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
public class CVE_2023_20959 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 249057848)
    @Test
    public void testPocCVE_2023_20959() {
        UiAutomation uiAutomation = null;
        try {
            String settingsPackageName = "com.android.settings";
            Instrumentation instrumentation = getInstrumentation();
            PackageManager packageManager = instrumentation.getTargetContext().getPackageManager();
            uiAutomation = instrumentation.getUiAutomation();
            uiAutomation.adoptShellPermissionIdentity(
                    android.Manifest.permission.INTERACT_ACROSS_USERS);
            ResolveInfo info =
                    packageManager.resolveActivityAsUser(new Intent(Settings.ACTION_SETTINGS),
                            PackageManager.MATCH_SYSTEM_ONLY, UserHandle.USER_SYSTEM);
            if (info != null && info.activityInfo != null) {
                settingsPackageName = info.activityInfo.packageName;
            }
            assertNull(new Intent()
                    .setComponent(new ComponentName(settingsPackageName,
                            settingsPackageName + ".users.AddSupervisedUserActivity"))
                    .resolveActivityInfo(packageManager, PackageManager.MATCH_SYSTEM_ONLY));
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                uiAutomation.dropShellPermissionIdentity();
            } catch (Exception e) {
                // Ignore exceptions as the test has finished
            }
        }
    }
}
