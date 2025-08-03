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

import static android.content.pm.PackageInstaller.SessionParams;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_0044 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 307532206)
    @Test
    public void testPocCVE_2024_0044() {
        try {
            final Context context = getApplicationContext();

            // Set vulnerable 'appPackageName' and 'installerPackageName'
            // for 'SessionParams' instance
            final String vulnPackageName =
                    context.getPackageName() + "\n" + context.getPackageName();
            final SessionParams params = new SessionParams(MODE_FULL_INSTALL);
            params.setAppPackageName(vulnPackageName);
            params.setInstallerPackageName(vulnPackageName);

            final List<String> vulnerableFields = new ArrayList<String>();
            runWithShellPermissionIdentity(
                    () -> {
                        // Create session using 'SessionParams' instance, get 'appPackageName' and
                        // 'installerPackageName' corresponding to session and abandon session later
                        final PackageInstaller packageInstaller =
                                context.getPackageManager().getPackageInstaller();
                        final int sessionId = packageInstaller.createSession(params);
                        final String vulnerableAppPackageName =
                                packageInstaller.getSessionInfo(sessionId).getAppPackageName();
                        final String vulnerableInstallerPackageName =
                                packageInstaller
                                        .getSessionInfo(sessionId)
                                        .getInstallerPackageName();
                        packageInstaller.abandonSession(sessionId);

                        // Without fix, 'appPackageName' and 'installerPackageName' does not undergo
                        // internal validation and are set to 'vulnPackageName' which contain '\n'
                        if (vulnerableAppPackageName != null
                                && vulnerableAppPackageName.contains("\n")) {
                            vulnerableFields.add("'SessionParams.appPackageName'");
                        }
                        if (vulnerableInstallerPackageName != null
                                && vulnerableInstallerPackageName.contains("\n")) {
                            vulnerableFields.add("'SessionParams.installerPackageName'");
                        }
                    });

            String errorMessage =
                    "Device is vulnerable to b/307532206 !!"
                            + " packages.list newline injection allows"
                            + " run-as as any app from ADB"
                            + " Due to : Fix is not present for ";
            assertWithMessage(errorMessage.concat(String.join(" , ", vulnerableFields)))
                    .that(vulnerableFields)
                    .isEmpty();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
