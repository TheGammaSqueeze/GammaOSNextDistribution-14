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

import static org.junit.Assume.assumeNoException;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2020_0500 extends StsExtraBusinessLogicTestCase {

    // b/154913391
    // Vulnerable Library : services.jar
    // Vulnerable Module  : Not applicable
    // Is Play managed    : No
    @AsbSecurityTest(cveBugId = 154913391)
    @Test
    public void testPocCVE_2020_0500() {
        Instrumentation instrumentation = null;
        try {
            instrumentation = InstrumentationRegistry.getInstrumentation();
            Context context = instrumentation.getContext();
            ActivityManager activityManager = context.getSystemService(ActivityManager.class);
            ComponentName componentName =
                    ComponentName.unflattenFromString("com.android.inputmethod.latin/.LatinIME");
            PendingIntent pi = activityManager.getRunningServiceControlPanel(componentName);

            Intent intent = new Intent();
            intent.setPackage(context.getPackageName());
            try {
                pi.send(context, 0, intent, null, null);
            } catch (PendingIntent.CanceledException e) {
                // If PendingIntent is mutable, it indicates vulnerable behaviour.
                throw new AssertionError("Vulnerable to b/154913391 !! "
                        + "PendingIntent from InputMethodManagerService is mutable");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }  finally {
            try {
                // Required to remove UI activity launching due to pi.send in case of without fix.
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_HOME");
            } catch (Exception e) {
                /// ignore
            }
        }
    }
}
