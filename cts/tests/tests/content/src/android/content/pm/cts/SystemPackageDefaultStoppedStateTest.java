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

package android.content.pm.cts;

import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@AppModeNonSdkSandbox
@RunWith(BedsteadJUnit4.class)
public class SystemPackageDefaultStoppedStateTest {

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private static final String CTS_SHIM_PKG_NAME = "com.android.cts.ctsshim";
    private UserReference mUser;

    @Before
    public void setupUser() {
        mUser = TestApis.users().createUser().name("Test User").create().start();
    }

    @Test
    @Ignore // TODO(269129704) - this is currently unreliable because:
            // 1. The shim app doesn't have a launcher activity, and therefore won't be stopped
            // 2. Even if it were stopped, side effects of other CTS tests might interfere.
    public void testSystemAppOnNewUser_isInDefaultStoppedState() {
        // Get com.android.internal.R.bool.config_stopSystemPackagesByDefault
        final int resId = mContext.getResources().getIdentifier(
                "config_stopSystemPackagesByDefault", "bool", "android");
        boolean isStoppedByDefault = mContext.getResources().getBoolean(resId);

        // ACTION_SEND is one of the intent filter actions for CTS_SHIM_PKG_NAME.
        // Get only that packages that can resolve ACTION_SEND, and that are in the
        // non-stopped state.
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            // Since we need only system apps, pass the flag MATCH_SYSTEM_ONLY
            List<ResolveInfo> packages = mContext.getPackageManager()
                    .queryIntentActivitiesAsUser(intent, PackageManager.ResolveInfoFlags.of(
                            MATCH_SYSTEM_ONLY), mUser.id());

            boolean packageFound = !packages.isEmpty() && packages.stream().anyMatch(pkg ->
                    pkg.activityInfo.packageName != null
                            && pkg.activityInfo.packageName.equals(CTS_SHIM_PKG_NAME));

            // Since we are querying only for non-stopped system packages, if the default stopped
            // state of system apps is stopped=true, then we should not find our requested package.
            // Else, we should find that package.
            assertWithMessage("Package not in correct state")
                    .that(isStoppedByDefault).isNotEqualTo(packageFound);
        });
    }

    @After
    public void deleteUser() throws NeneException {
        mUser.remove();
    }
}
