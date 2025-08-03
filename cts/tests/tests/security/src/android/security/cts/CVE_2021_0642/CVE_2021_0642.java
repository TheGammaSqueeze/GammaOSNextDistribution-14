/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts.CVE_2021_0642;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;
import android.telephony.TelephonyManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0642 extends StsExtraBusinessLogicTestCase {

    // b/185126149
    // Vulnerable app    : TeleService.apk
    // Vulnerable module : com.android.phone
    // Is Play managed   : No
    @AsbSecurityTest(cveBugId = 185126149)
    @Test
    public void testCVE_2021_0642() {
        try {
            // This test requires the device to have Telephony feature.
            Context context = getInstrumentation().getTargetContext();
            PackageManager pm = context.getPackageManager();
            assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

            // Get ResolverActivity's name and package name
            Intent customIntent = new Intent(context.getString(R.string.cve_2021_0642_action));
            ResolveInfo riCustomAction =
                    pm.resolveActivity(customIntent, PackageManager.MATCH_DEFAULT_ONLY);
            assumeTrue(context.getString(R.string.cve_2021_0642_msgResolveErrorPocAction),
                    !riCustomAction.activityInfo.packageName.equals(context.getPackageName()));
            final String resolverPkgName = riCustomAction.activityInfo.packageName;
            final String resolverActivityName = riCustomAction.activityInfo.name;

            // Resolving intent with action "ACTION_CONFIGURE_VOICEMAIL"
            Intent intent = new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
            ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            final String resolvedPkgName = ri.activityInfo.packageName;
            final String resolvedActivityName = ri.activityInfo.name;

            // Check if intent resolves to either VoicemailActivity or ResolverActivity
            boolean isVoicemailActivity =
                    resolvedPkgName.equals(context.getString(R.string.cve_2021_0642_pkgPhone));
            boolean isResolverActivity = resolvedPkgName.equals(resolverPkgName)
                    && resolvedActivityName.equals(resolverActivityName);

            assumeTrue(context.getString(R.string.cve_2021_0642_msgResolveErrorVoicemail),
                    isVoicemailActivity || isResolverActivity);

            // If vulnerability is present, the intent with action ACTION_CONFIGURE_VOICEMAIL
            // would resolve to the IntentResolver i.e. ResolverActivity, the test would fail in
            // this case.
            assertFalse(context.getString(R.string.cve_2021_0642_failMsg), isResolverActivity);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
