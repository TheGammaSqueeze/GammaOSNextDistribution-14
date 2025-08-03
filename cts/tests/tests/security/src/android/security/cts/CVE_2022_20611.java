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

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class CVE_2022_20611 extends StsExtraBusinessLogicTestCase {
    /**
     * CVE-2022-20611
     */
    @AsbSecurityTest(cveBugId = 242996180)
    @Test
    public void testPocCVE_2022_20611() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int provisioningAppId = context.getResources().getIdentifier(
                "config_deviceProvisioningPackage", "string", "android");
        assumeTrue("config_deviceProvisioningPackage not found.", provisioningAppId > 0);

        String protectedPkg = context.getResources().getString(provisioningAppId);
        assumeFalse("config_deviceProvisioningPackage is not set", protectedPkg.isEmpty());

        String res = runShellCommand("pm list packages " + protectedPkg);
        assumeTrue(protectedPkg + " is not installed.", res.contains(protectedPkg));

        res = runShellCommand("pm uninstall -k --user 0 " + protectedPkg);
        if (!res.contains("DELETE_FAILED_INTERNAL_ERROR")) {
            runShellCommand("pm install-existing --user 0 " + protectedPkg);
            fail(
                    "Protected package '" + protectedPkg + "' could be uninstalled. "
                    + "Vulnerable to b/242994180.");
        }
    }
}
