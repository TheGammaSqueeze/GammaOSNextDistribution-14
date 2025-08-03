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

package android.security.cts.CVE_2023_20953;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.AsbSecurityTest;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_20953 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 251778420)
    @Test
    public void testCliboardIfUserSetupNotComplete() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            Context context = instrumentation.getContext();
            UiDevice device = UiDevice.getInstance(instrumentation);

            // Change the setting 'user_setup_complete' to 0.
            try (AutoCloseable withSettingCloseable =
                    SystemUtil.withSetting(instrumentation, "secure", "user_setup_complete", "0")) {
                // Launch the PocActivity which shows a basic view to edit some text.
                final String pkgName = context.getPackageName();
                Intent intent = new Intent();
                intent.setClassName(pkgName, pkgName + ".CVE_2023_20953.PocActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                // Look for UI with text "CVE-2023-20955" and select it. Doing so will show some
                // clipboard options such as 'copy', 'select all', 'cut'.
                final String uiNotFoundMsg = "UI not found with selector %s";
                BySelector selector = By.text("CVE-2023-20955");
                final long timeoutMs = 5000;
                boolean uiFound = device.wait(Until.findObject(selector), timeoutMs) != null;
                assumeTrue(String.format(uiNotFoundMsg, selector), uiFound);
                UiObject2 object = device.findObject(selector);
                device.drag(object.getVisibleBounds().left, object.getVisibleCenter().y,
                        object.getVisibleBounds().right, object.getVisibleCenter().y, 1);

                // Click on 'Copy' option.
                selector = By.desc(Pattern.compile(".*copy.*", Pattern.CASE_INSENSITIVE));
                uiFound = device.wait(Until.findObject(selector), timeoutMs) != null;
                assumeTrue(String.format(uiNotFoundMsg, selector), uiFound);
                device.findObject(selector).click();

                // Retrieve System UI package name dynamically.
                String systemUiPkgName = "com.android.systemui";
                intent = new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG);
                PackageManager pm = context.getPackageManager();
                ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_SYSTEM_ONLY);
                if (info != null && info.activityInfo != null
                        && info.activityInfo.packageName != null) {
                    systemUiPkgName = info.activityInfo.packageName;
                }

                // On vulnerable device, user whose setup is not yet complete will be able to share
                // the copied text, in which case test fails, else it passes.
                // Look for UI with resource = com.android.systemui:id/share_chip, if found test
                // will fail.
                final String shareBtnResName = "%s:id/share_chip";
                selector = By.res(String.format(shareBtnResName, systemUiPkgName));
                assertFalse("Vulnerable to b/251778420 !!",
                        device.wait(Until.hasObject(selector), timeoutMs));
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
