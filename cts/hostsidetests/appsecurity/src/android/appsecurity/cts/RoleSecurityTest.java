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

package android.appsecurity.cts;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.StsExtraBusinessLogicHostTestBase;

import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class RoleSecurityTest extends StsExtraBusinessLogicHostTestBase {
    private static final String ROLE_SECURITY_TEST_APP_APK = "CtsRoleSecurityTestApp.apk";
    private static final String ROLE_SECURITY_TEST_APP_PACKAGE = "com.android.cts.rolesecuritytest";

    private CompatibilityBuildHelper mBuildHelper;

    @Before
    public void setUp() {
        mBuildHelper = new CompatibilityBuildHelper(getBuild());
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access role manager")
    @AsbSecurityTest(cveBugId = 177927831)
    public void cannotGetSmsRoleHolderForAnotherUser() throws Exception {
        final int[] userIds = Utils.prepareMultipleUsers(getDevice(), 2);
        assumeTrue(userIds.length == 2);

        final int initialUserId = getDevice().getCurrentUser();
        final int secondaryUserId = userIds[1];
        assumeTrue("Unable to switch user", getDevice().switchUser(secondaryUserId));
        try {
            uninstallApp(ROLE_SECURITY_TEST_APP_PACKAGE);
            try {
                installApp(ROLE_SECURITY_TEST_APP_APK);

                runDeviceTestsAsUser(ROLE_SECURITY_TEST_APP_PACKAGE,
                        ROLE_SECURITY_TEST_APP_PACKAGE + ".DeviceTest",
                        "cannotGetSmsRoleHolderForAnotherUser", secondaryUserId);
            } finally {
                uninstallApp(ROLE_SECURITY_TEST_APP_PACKAGE);
            }
        } finally {
            getDevice().switchUser(initialUserId);
        }
    }

    private void installApp(String fileName) throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(fileName), false));
    }

    private void uninstallApp(String packageName) throws Exception {
        getDevice().uninstallPackage(packageName);
    }

    private void runDeviceTestsAsUser(String packageName, String testClassName,
            String testMethodName, int userId) throws Exception {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId);
    }
}
