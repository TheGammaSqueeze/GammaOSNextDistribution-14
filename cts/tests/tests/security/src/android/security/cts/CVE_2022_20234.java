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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20234 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 225189301)
    @Test
    public void testPocCVE_2022_20234() {
        try {
            Context context = getApplicationContext();
            PackageManager pm = context.getPackageManager();

            // Skip test for non-automotive builds
            assume().withMessage("Skipping test: " + PackageManager.FEATURE_AUTOMOTIVE + " missing")
                    .that(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
                    .isTrue();

            // With fix, NotificationAccessConfirmationActivity is not exported
            final String activityName = ".notifications.NotificationAccessConfirmationActivity";
            final String pkgName = "com.android.car.settings";
            ComponentName component = new ComponentName(pkgName, pkgName + activityName);
            boolean exported = pm.getActivityInfo(component, 0 /* flags */).exported;
            assertWithMessage(
                            "Vulnerable to b/225189301!! "
                                    + pkgName
                                    + activityName
                                    + "can be started from outside system process")
                    .that(exported)
                    .isFalse();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
