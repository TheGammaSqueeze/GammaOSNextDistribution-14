/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tests.apex;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tests.rollback.host.AbandonSessionsRule;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Base test to check if Apex can be staged, activated and uninstalled successfully.
 */
public abstract class ApexE2EBaseHostTest extends BaseHostJUnit4Test {

    private static final String OPTION_APEX_FILE_NAME = "apex_file_name";

    private static final Duration BOOT_COMPLETE_TIMEOUT = Duration.ofMinutes(2);

    // Protected so that derived tests can have access to test utils automatically
    protected final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    @Rule
    public AbandonSessionsRule mHostTestRule = new AbandonSessionsRule(this);

    @Option(name = OPTION_APEX_FILE_NAME,
            description = "The file name of the apex module.",
            importance = Importance.IF_UNSET,
            mandatory = true
    )
    protected String mApexFileName = null;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Updating APEX is not supported", mHostUtils.isApexUpdateSupported());
        uninstallAllApexes();
    }

    @After
    public void tearDown() throws Exception {
        assumeTrue("Updating APEX is not supported", mHostUtils.isApexUpdateSupported());
        uninstallAllApexes();
    }

    protected List<String> getAllApexFilenames() {
        return List.of(mApexFileName);
    }

    @Test
    public final void testStageActivateUninstallApexPackage()  throws Exception {
        stageActivateUninstallApexPackage();
    }

    private void stageActivateUninstallApexPackage()  throws Exception {
        ApexInfo apex = installApex(mApexFileName);

        getDevice().reboot(); // for install to take affect
        Set<ApexInfo> activatedApexes = getDevice().getActiveApexes();
        assertWithMessage("Failed to activate %s", apex).that(activatedApexes).contains(apex);

        additionalCheck();
    }

    private void uninstallAllApexes() throws Exception {
        for (String filename : getAllApexFilenames()) {
            ApexInfo apex = mHostUtils.getApexInfo(mHostUtils.getTestFile(filename));
            uninstallApex(apex.name);
        }
    }

    protected final ApexInfo installApex(String filename) throws Exception {
        File testAppFile = mHostUtils.getTestFile(filename);

        String installResult = mHostUtils.installStagedPackage(testAppFile);
        assertWithMessage("failed to install test app %s. Reason: %s", filename, installResult)
                .that(installResult).isNull();

        ApexInfo testApexInfo = mHostUtils.getApexInfo(testAppFile);
        Assert.assertNotNull(testApexInfo);
        return testApexInfo;
    }

    /**
     * Do some additional check, invoked by {@link #testStageActivateUninstallApexPackage()}.
     */
    public void additionalCheck() throws Exception {};

    protected final void uninstallApex(String apexName) throws Exception {
        String res = getDevice().uninstallPackage(apexName);
        if (res != null) {
            // Uninstall failed. Most likely this means that there were no apex installed. No need
            // to reboot.
            CLog.i("Uninstall of %s failed: %s, likely already on factory version", apexName, res);
        } else {
            // Uninstall succeeded. Need to reboot.
            getDevice().reboot(); // for the uninstall to take affect
        }
    }
}
