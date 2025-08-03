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

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.support.test.uiautomator.UiDevice;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_20960 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 250589026)
    @Test
    public void testPocCVE_2023_20960() {
        UiAutomation uiAutomation = null;
        UiDevice uiDevice = null;
        try {
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            uiAutomation = instrumentation.getUiAutomation();
            uiDevice = UiDevice.getInstance(instrumentation);
            Context context = instrumentation.getContext();
            final String installCaCertificateWarningActivity =
                    context.getString(R.string.cve_2023_20960_unexportedActivity);
            String settingsPackageName =
                    context.getString(R.string.cve_2023_20960_settingsPackageName);

            // Retrieve Settings app's package name
            ResolveInfo info =
                    context.getPackageManager()
                            .resolveActivityAsUser(
                                    new Intent(Settings.ACTION_SETTINGS),
                                    PackageManager.MATCH_SYSTEM_ONLY,
                                    UserHandle.USER_SYSTEM);
            if (info != null && info.activityInfo != null) {
                settingsPackageName = info.activityInfo.packageName;
            }

            // Attempt to launch unexported Activity using Settings app
            uiAutomation.adoptShellPermissionIdentity(
                    android.Manifest.permission.LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK,
                    android.Manifest.permission.INTERACT_ACROSS_USERS);
            context.startActivity(
                    new Intent(Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY)
                            .putExtra(
                                    Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                                    context.getString(R.string.cve_2023_20960_uriStringIntent)
                                            + context.getString(
                                                    R.string.cve_2023_20960_uriStringComponent)
                                            + settingsPackageName
                                            + context.getString(
                                                    R.string.cve_2023_20960_forwardSlash)
                                            + installCaCertificateWarningActivity
                                            + context.getString(
                                                    R.string.cve_2023_20960_uriStringSuffix))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Check if unexported activity 'InstallCaCertificateWarning' is launched
            String activityDump =
                    uiDevice.executeShellCommand(
                            context.getString(
                                    R.string.cve_2023_20960_dumpsysActivityCmd,
                                    settingsPackageName + installCaCertificateWarningActivity));

            Pattern resumedTruePattern =
                    Pattern.compile(
                            context.getString(R.string.cve_2023_20960_mResumedTruePattern),
                            Pattern.CASE_INSENSITIVE);

            // Wait for dumpsys result to update
            int iteration = 0;
            while (!resumedTruePattern.matcher(activityDump).find() && iteration <= 5) {
                Thread.sleep(100);
                activityDump =
                        uiDevice.executeShellCommand(
                                context.getString(
                                        R.string.cve_2023_20960_dumpsysActivityCmd,
                                        settingsPackageName + installCaCertificateWarningActivity));
                iteration++;
            }

            assertFalse(
                    context.getString(R.string.cve_2023_20960_failMsg),
                    resumedTruePattern.matcher(activityDump).find());
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                uiAutomation.dropShellPermissionIdentity();
                uiDevice.pressHome();
            } catch (Exception e) {
                // Ignore exceptions as the test has finished
            }
        }
    }
}
