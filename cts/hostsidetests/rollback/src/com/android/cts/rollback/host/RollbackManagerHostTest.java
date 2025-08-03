/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.rollback.host;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeThat;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS host tests for RollbackManager APIs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class RollbackManagerHostTest extends BaseHostJUnit4Test {

    private static final String TAG = "RollbackManagerHostTest";
    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    /**
     * Runs the helper app test method on device.
     * Throws an exception if the test method fails.
     * <p>
     * For example, <code>run("testApkOnlyEnableRollback");</code>
     */
    private void run(String method) throws Exception {
        assertThat(runDeviceTests("com.android.cts.rollback.host.app",
                "com.android.cts.rollback.host.app.HostTestHelper",
                method)).isTrue();
    }

    /**
     * Runs the helper app test method on device targeted for
     * com.android.cts.rollback.host.app2.HostTestHelper.
     */
    private void run2(String method) throws Exception {
        assertThat(runDeviceTests("com.android.cts.rollback.host.app2",
                "com.android.cts.rollback.host.app2.HostTestHelper",
                method)).isTrue();
    }

    /**
     * Uninstalls any version greater than 1 of shim apex and reboots the device if necessary
     * to complete the uninstall.
     *
     * <p>This is needed because the apex cannot be deleted using PackageInstaller API.
     *
     * Also abandon sessions left by previous tests so staged-installs won't fail.
     */
    @Before
    @After
    public void cleanUp() throws Exception {
        getDevice().executeShellCommand("for i in $(pm list staged-sessions --only-sessionid "
                + "--only-parent); do pm install-abandon $i; done");
        getDevice().executeShellCommand("pm uninstall com.android.cts.install.lib.testapp.A");
        getDevice().executeShellCommand("pm uninstall com.android.cts.install.lib.testapp.B");
        getDevice().executeShellCommand("pm uninstall com.android.cts.install.lib.testapp.C");
        run("cleanUp");
        mHostUtils.uninstallShimApexIfNecessary();
    }

    /**
     * Tests staged rollbacks involving only apks.
     */
    @Test
    public void testApkOnlyStagedRollback() throws Exception {
        run("testApkOnlyStagedRollback_Phase1_Install");
        getDevice().reboot();
        run("testApkOnlyStagedRollback_Phase2_RollBack");
        getDevice().reboot();
        run("testApkOnlyStagedRollback_Phase3_Confirm");
    }

    /**
     * Tests multiple staged rollbacks involving only apks.
     */
    @Test
    public void testApkOnlyMultipleStagedRollback() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        run("testApkOnlyMultipleStagedRollback_Phase1_Install");
        getDevice().reboot();
        run("testApkOnlyMultipleStagedRollback_Phase2_RollBack");
        getDevice().reboot();
        run("testApkOnlyMultipleStagedRollback_Phase3_Confirm");
    }

    /**
     * Tests multiple staged partial rollbacks involving only apks.
     */
    @Test
    public void testApkOnlyMultipleStagedPartialRollback() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        run("testApkOnlyMultipleStagedPartialRollback_Phase1_Install");
        getDevice().reboot();
        run("testApkOnlyMultipleStagedPartialRollback_Phase2_RollBack");
        getDevice().reboot();
        run("testApkOnlyMultipleStagedPartialRollback_Phase3_Confirm");
    }

    /**
     * Tests staged rollbacks involving only apex.
     */
    @Test
    public void testApexOnlyStagedRollback() throws Exception {
        run("testApexOnlyStagedRollback_Phase1_InstallFirst");
        getDevice().reboot();
        run("testApexOnlyStagedRollback_Phase2_InstallSecond");
        getDevice().reboot();
        run("testApexOnlyStagedRollback_Phase3_RollBack");
        getDevice().reboot();
        run("testApexOnlyStagedRollback_Phase4_Confirm");
    }

    /**
     * Tests staged rollbacks to system version involving only apex.
     */
    @Test
    public void testApexOnlySystemVersionStagedRollback() throws Exception {
        run("testApexOnlySystemVersionStagedRollback_Phase1_Install");
        getDevice().reboot();
        run("testApexOnlySystemVersionStagedRollback_Phase2_RollBack");
        getDevice().reboot();
        run("testApexOnlySystemVersionStagedRollback_Phase3_Confirm");
    }

    /**
     * Tests staged rollbacks involving apex and apk.
     */
    @Test
    public void testApexAndApkStagedRollback() throws Exception {
        assumeSystemUser();

        run("testApexAndApkStagedRollback_Phase1_InstallFirst");
        getDevice().reboot();
        run("testApexAndApkStagedRollback_Phase2_InstallSecond");
        getDevice().reboot();
        run("testApexAndApkStagedRollback_Phase3_RollBack");
        getDevice().reboot();
        run("testApexAndApkStagedRollback_Phase4_Confirm");
    }

    private void assumeSystemUser() throws Exception {
        String systemUser = "0";
        assumeThat("Current user is not system user",
                getDevice().executeShellCommand("am get-current-user").trim(), equalTo(systemUser));
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     */
    @Test
    public void testApexRollbackExpiration() throws Exception {
        run("testApexRollbackExpiration_Phase1_InstallFirst");
        getDevice().reboot();
        run("testApexRollbackExpiration_Phase2_InstallSecond");
        getDevice().reboot();
        run("testApexRollbackExpiration_Phase3_Confirm");
    }

    /**
     * Tests staged rollbacks involving apex with rotated keys.
     */
    @Test
    public void testApexKeyRotationStagedRollback() throws Exception {
        run("testApexKeyRotationStagedRollback_Phase1_Install");
        getDevice().reboot();
        run("testApexKeyRotationStagedRollback_Phase2_RollBack");
        getDevice().reboot();
        run("testApexKeyRotationStagedRollback_Phase3_Confirm");
    }

    /**
     * Tests installer B can't rollback a package installed by A.
     */
    @Test
    public void testApkRollbackByAnotherInstaller() throws Exception {
        run("testApkRollbackByAnotherInstaller_Phase1_FirstInstaller");
        run2("testApkRollbackByAnotherInstaller_Phase2_SecondInstaller");
    }

    /**
     * Tests that existing staged sessions are failed when rollback is committed
     */
    @Test
    public void testRollbackFailsOtherSessions() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        run("testRollbackFailsOtherSessions_Phase1_Install");
        getDevice().reboot();
        run("testRollbackFailsOtherSessions_Phase2_RollBack");
        getDevice().reboot();
        run("testRollbackFailsOtherSessions_Phase3_Confirm");
    }

    /**
     * Tests that simultaneous rollbacks both succeed - neither causes the other to fail.
     */
    @Test
    public void testSimultaneousRollbacksBothSucceed() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        run("testSimultaneousRollbacksBothSucceed_Phase1_Install");
        getDevice().reboot();
        run("testSimultaneousRollbacksBothSucceed_Phase2_RollBack");
        getDevice().reboot();
        run("testSimultaneousRollbacksBothSucceed_Phase3_Confirm");
    }

    /**
     * Tests that rollbacks are invalidated upon fingerprint changes.
     */
    @Test
    public void testFingerprintChange() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), not(endsWith("-user")));

        try {
            getDevice().executeShellCommand("setprop persist.pm.mock-upgrade true");

            run("testFingerprintChange_Phase1_Install");
            getDevice().reboot();
            run("testFingerprintChange_Phase2_Confirm");
        } finally {
            getDevice().executeShellCommand("setprop persist.pm.mock-upgrade false");
        }
    }
}
