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

package com.android.tests.apex.art;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.invoker.TestInformation;

import java.io.File;
import java.time.Duration;

/**
 * Utilities to install and uninstall the "broken" (test) ART APEX (which is expected to trigger a
 * rollback during the next boot).
 */
public class ArtApexTestUtils {

    private static final String APEX_FILENAME = "test_broken_com.android.art.apex";
    private static final Duration BOOT_COMPLETE_TIMEOUT = Duration.ofMinutes(2);

    private final InstallUtilsHost mInstallUtils;
    private final TestInformation mTestInfo;

    public ArtApexTestUtils(TestInformation testInfo) throws Exception {
        assertNotNull(testInfo.getDevice());
        mInstallUtils = new InstallUtilsHost(testInfo);
        mTestInfo = testInfo;
    }

    public void installTestApex() throws Exception {
        assumeTrue("Updating APEX is not supported", mInstallUtils.isApexUpdateSupported());

        // Use `InstallUtilsHost.installStagedPackage` instead of `InstallUtilsHost.installApexes`,
        // as the latter does not enable us to check whether the APEX was successfully installed
        // (staged).
        File apexFile = mInstallUtils.getTestFile(APEX_FILENAME);
        String error = mInstallUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();
        reboot();
    }

    public void uninstallTestApex() throws Exception {
        ApexInfo apex = mInstallUtils.getApexInfo(mInstallUtils.getTestFile(APEX_FILENAME));
        mTestInfo.getDevice().uninstallPackage(apex.name);
        reboot();
    }

    public void reboot() throws Exception {
        mTestInfo.getDevice().reboot();
        boolean success =
                mTestInfo.getDevice().waitForBootComplete(BOOT_COMPLETE_TIMEOUT.toMillis());
        assertWithMessage("Device didn't boot in %s", BOOT_COMPLETE_TIMEOUT).that(success).isTrue();
    }
}
