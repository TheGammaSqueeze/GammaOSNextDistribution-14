/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.permissionpolicy.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.R;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import com.google.common.base.Strings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@AppModeFull(reason = "Instant apps cannot read the system servers permission")
@RunWith(Parameterized.class)
public class IntelligenceRolesPolicyTest {
    private final int mConfigKey;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Parameterized.Parameters(name = "{0}")
    public static Collection intelligenceRoles() {
        return Arrays.asList(new Object[][]{
                {"systemUiIntelligence", R.string.config_systemUiIntelligence},
                {"systemAmbientAudioIntelligence", R.string.config_systemAmbientAudioIntelligence},
                {"systemAudioIntelligence", R.string.config_systemAudioIntelligence},
                {"systemNotificationIntelligence", R.string.config_systemNotificationIntelligence},
                {"systemTextIntelligence", R.string.config_systemTextIntelligence},
                {"systemVisualIntelligence", R.string.config_systemVisualIntelligence},
        });
    }

    public IntelligenceRolesPolicyTest(String unusedName, int configKey) {
        mConfigKey = configKey;
    }

    @Test
    public void testNoInternetPermissionRequested() throws Exception {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S));

        String packageName = sContext.getResources().getString(mConfigKey);
        assumeTrue(!Strings.isNullOrEmpty(packageName));

        List<String> requestedPermissions;

        try {
            requestedPermissions = getRequestedPermissions(sContext, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // A package is not found, despite overlay config pointing to it. Strictly speaking that
            // means that the policy for being an intelligence role is fulfilled.
            return;
        }

        assertWithMessage("Package " + packageName + "MUST NOT request INTERNET permission. "
                + "Instead packages MUST access the internet through well-defined APIs in an open "
                + "source project.")
                .that(requestedPermissions)
                .doesNotContain(android.Manifest.permission.INTERNET);
    }

    private static List<String> getRequestedPermissions(Context context, String pkg)
            throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);

        return Arrays.asList(packageInfo.requestedPermissions);
    }
}
