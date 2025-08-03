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

package com.android.tests.hostside.silentupdate;

import com.android.tradefed.util.RunUtil;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SilentUpdateHostsideTests extends BaseHostJUnit4Test {
    private static final String INSTALL_APP_OP_COMMAND =
            "appops set com.android.tests.silentupdate android:request_install_packages ";
    private static final String TEST_PKG = "com.android.tests.silentupdate";
    private static final String TEST_CLS = "com.android.tests.silentupdate.SilentUpdateTests";
    private static final String CURRENT_APK = "SilentInstallCurrent.apk";
    private static final String R_APK = "SilentInstallR.apk";
    private static final String S_APK = "SilentInstallS.apk";

    @Before
    public void installAppOpAllowed() throws Exception {
        getDevice().executeShellCommand(INSTALL_APP_OP_COMMAND + "allow");
    }

    public void install(String apk, String installerPackageName) throws Exception {
        getDevice().installPackage(
                new CompatibilityBuildHelper(getBuild()).getTestFile(apk),
                true /* reinstall */,
                installerPackageName == null
                        ? new String[]{}
                        : new String[]{"-i", installerPackageName});
    }

    @After
    public void installAppOpDefault() throws Exception {
        getDevice().executeShellCommand(INSTALL_APP_OP_COMMAND + "default");
    }

    @Before
    @After
    public void uninstallTestApp() throws Exception {
        uninstallPackage("android.cts.silentupdate.app");
    }

    @Test
    public void newInstall_RequiresUserAction() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLS, "newInstall_RequiresUserAction");
    }

    @Test
    public void updateWithUnknownSourcesDisabled_RequiresUserAction() throws Exception {
        install(CURRENT_APK, TEST_PKG);
        installAppOpDefault();
        runDeviceTests(TEST_PKG, TEST_CLS, "updateWithUnknownSourcesDisabled_RequiresUserAction");
    }

    @Test
    public void updateAsNonInstallerOfRecord_RequiresUserAction() throws Exception {
        install(CURRENT_APK, null);
        installAppOpAllowed();
        runDeviceTests(TEST_PKG, TEST_CLS, "updateAsNonInstallerOfRecord_RequiresUserAction");
    }

    @Test
    public void updatedInstall_RequiresNoUserAction() throws Exception {
        install(CURRENT_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS, "updatedInstall_RequiresNoUserAction");
    }

    @Test
    public void updatePreSApp_RequiresUserAction() throws Exception {
        install(R_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS, "updatePreSApp_RequiresUserAction");
    }

    @Test
    public void updateSApp_RequiresNoUserAction() throws Exception {
        install(S_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS, "updateSApp_RequiresNoUserAction");
    }

    @Test
    public void selfUpdate_RequiresNoUserAction() throws Exception {
        String originalCodePath = getCodePath(TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS, "selfUpdate_RequiresNoUserAction");
        // To avoid arbitrary wait, we'll monitor the code path change before we interrogate again
        waitForPathChange(TEST_PKG, originalCodePath, TimeUnit.SECONDS.toMillis(20));
        runDeviceTests(TEST_PKG, TEST_CLS, "selfUpdate_updateApplied");
    }

    @Test
    public void setRequireUserAction_throwsOnIllegalArgument() throws Exception {
        install(S_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS, "setRequireUserAction_throwsOnIllegalArgument");
    }

    @Test
    public void silentInstallRepeatedly_RequiresUserAction() throws Exception {
        install(CURRENT_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS, "silentInstallRepeatedly_RequiresUserAction");
    }

    @Test
    public void silentInstallRepeatedly_withUnlimitedSilentUpdates_succeed() throws Exception {
        install(CURRENT_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS,
                "silentInstallRepeatedly_withUnlimitedSilentUpdates_succeed");
    }

    @Test
    public void silentInstallRepeatedly_waitForThrottleTime_succeed() throws Exception {
        install(CURRENT_APK, TEST_PKG);
        runDeviceTests(TEST_PKG, TEST_CLS,
                "silentInstallRepeatedly_waitForThrottleTime_succeed");
    }

    @Test
    public void newInstall_withInstallDpcPermission_requiresUserAction() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_CLS, "newInstall_withInstallDpcPermission_requiresUserAction");
    }

    @Test
    public void newInstallDpc_withInstallDpcPermission_requiresNoUserAction() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_CLS, "newInstallDpc_withInstallDpcPermission_requiresNoUserAction");
    }

    @Test
    public void newInstallDpc_withoutInstallDpcPermission_requiresUserAction() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_CLS,
                "newInstallDpc_withoutInstallDpcPermission_requiresUserAction");
    }

    private void waitForPathChange(String packageName, String originalCodePath, long timeout)
            throws DeviceNotAvailableException, InterruptedException {
                long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout
                && originalCodePath.equals(getCodePath(packageName))) {
            RunUtil.getDefault().sleep(100);
        }
    }

    private String getCodePath(String packageName) throws DeviceNotAvailableException {
        return getDevice().getAppPackageInfo(packageName).getCodePath();
    }

}
