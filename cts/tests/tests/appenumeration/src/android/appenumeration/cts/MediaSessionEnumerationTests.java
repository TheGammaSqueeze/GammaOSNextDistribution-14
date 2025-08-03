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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.ACTION_MEDIA_SESSION_MANAGER_IS_TRUSTED_FOR_MEDIA_CONTROL;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_DUMMY_ACTIVITY;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Constants.TARGET_NO_API;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaSessionEnumerationTests extends AppEnumerationTestsBase {

    @Before
    public void onBefore() {
        setAllowGetActiveSession(TARGET_NO_API, true);
    }

    @After
    public void onAfter() {
        setAllowGetActiveSession(TARGET_NO_API, false);
    }

    @Test
    public void queriesPackage_isTrustedForMediaControl_canSeeTarget()
            throws Exception {
        assertThat(isTrustedForMediaControl(QUERIES_PACKAGE, TARGET_NO_API), is(true));
    }

    @Test
    public void queriesNothing_isTrustedForMediaControl_cannotSeeTarget()
            throws Exception {
        assertThat(isTrustedForMediaControl(QUERIES_NOTHING, TARGET_NO_API), is(false));
    }

    private boolean isTrustedForMediaControl(String sourcePackageName,
            String targetPackageName) throws Exception {
        final int targetUid = sPm.getPackageUid(
                targetPackageName, PackageManager.PackageInfoFlags.of(0));
        final Bundle bundle = sendCommand(
                sourcePackageName,
                targetPackageName,
                targetUid,
                null /* intentExtra */,
                ACTION_MEDIA_SESSION_MANAGER_IS_TRUSTED_FOR_MEDIA_CONTROL,
                false /* waitForReady */)
                .await();
        return bundle.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private static void setAllowGetActiveSession(String packageName, boolean allow) {
        final StringBuilder cmd = new StringBuilder("cmd notification ");
        if (allow) {
            cmd.append("allow_listener ");
        } else {
            cmd.append("disallow_listener ");
        }
        cmd.append(packageName).append("/").append(ACTIVITY_CLASS_DUMMY_ACTIVITY).append(" ");
        runShellCommand(cmd.toString());
    }
}
