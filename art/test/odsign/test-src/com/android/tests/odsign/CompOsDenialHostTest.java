/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.tests.odsign;

import static com.android.tests.odsign.CompOsTestUtils.PENDING_ARTIFACTS_DIR;
import static com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.stream.Stream;

/** Test to check if bad CompOS pending artifacts can be denied by odsign. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CompOsDenialHostTest extends BaseHostJUnit4Test {

    private static final String PENDING_ARTIFACTS_BACKUP_DIR =
            "/data/misc/apexdata/com.android.art/compos-pending-backup";

    private static final String TIMESTAMP_COMPOS_COMPILED_KEY = "compos_test_timestamp_compiled";

    private OdsignTestUtils mTestUtils;
    private String mFirstArch;

    @Rule public TestLogData mTestLogs = new TestLogData();

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        ITestDevice device = testInfo.getDevice();
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        CompOsTestUtils compOsTestUtils = new CompOsTestUtils(device);

        compOsTestUtils.assumeNotOnCuttlefish();
        compOsTestUtils.assumeCompOsPresent();

        testUtils.installTestApex();

        // Compile once, then backup the compiled artifacts to be reused by each test case just to
        // reduce testing time.
        compOsTestUtils.runCompilationJobEarlyAndWait();
        testInfo.properties().put(TIMESTAMP_COMPOS_COMPILED_KEY,
                String.valueOf(testUtils.getCurrentTimeMs()));
        testUtils.assertCommandSucceeds(
                "mv " + PENDING_ARTIFACTS_DIR + " " + PENDING_ARTIFACTS_BACKUP_DIR);
    }

    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);

        try {
            // Remove all test states.
            testInfo.getDevice().executeShellV2Command("rm -rf " + PENDING_ARTIFACTS_DIR);
            testInfo.getDevice().executeShellV2Command("rm -rf " + PENDING_ARTIFACTS_BACKUP_DIR);
            testUtils.removeCompilationLogToAvoidBackoff();
            testUtils.uninstallTestApex();
        } finally {
            // Reboot should restore the device back to a good state.
            testUtils.reboot();
        }
    }

    @Before
    public void setUp() throws Exception {
        mTestUtils = new OdsignTestUtils(getTestInformation());

        mFirstArch = mTestUtils.assertCommandSucceeds("getprop ro.bionic.arch");

        // Restore the pending artifacts for each test to mess up with.
        mTestUtils.assertCommandSucceeds("rm -rf " + PENDING_ARTIFACTS_DIR);
        mTestUtils.assertCommandSucceeds("cp -rp " + PENDING_ARTIFACTS_BACKUP_DIR + " " +
                PENDING_ARTIFACTS_DIR);
    }

    @Test
    public void denyDueToInconsistentFileName() throws Exception {
        // Attack emulation: swap file names
        String[] paths = getAllPendingOdexPaths();
        assertThat(paths.length).isGreaterThan(1);
        String odex1 = paths[0];
        String odex2 = paths[1];
        String temp = PENDING_ARTIFACTS_DIR + "/temp";
        mTestUtils.assertCommandSucceeds(
                "mv " + odex1 + " " + temp + " && " +
                "mv " + odex2 + " " + odex1 + " && " +
                "mv " + temp + " " + odex2);

        // Expect the pending artifacts to be denied by odsign during the reboot.
        mTestUtils.reboot();
        expectNoCurrentFilesFromCompOs();
    }

    @Test
    public void denyDueToMissingFile() throws Exception {
        // Attack emulation: delete a file
        String[] paths = getAllPendingOdexPaths();
        assertThat(paths.length).isGreaterThan(0);
        getDevice().deleteFile(paths[0]);

        // Expect the pending artifacts to be denied by odsign during the reboot.
        mTestUtils.reboot();
        expectNoCurrentFilesFromCompOs();
    }

    @Test
    public void denyDueToSignatureMismatch() throws Exception {
        // Attack emulation: tamper with the compos.info file or its signature (which could allow
        // a modified artifact to be accepted).

        // The signature file will always be 64 bytes, just overwrite with randomness.
        // (Which has ~ 1 in 2^250 chance of being a valid signature at all.)
        mTestUtils.assertCommandSucceeds("dd if=/dev/urandom"
                + " of=" + PENDING_ARTIFACTS_DIR + "/compos.info.signature"
                + " ibs=64 count=1");

        // Expect the pending artifacts to be denied by odsign during the reboot.
        mTestUtils.reboot();
        expectNoCurrentFilesFromCompOs();
    }

    private void expectNoCurrentFilesFromCompOs() throws DeviceNotAvailableException {
        // None of the files should have a timestamp earlier than the first reboot.
        long timestamp = Long.parseLong(getTestInformation().properties().get(
                    TIMESTAMP_COMPOS_COMPILED_KEY));
        int numFiles = mTestUtils.countFilesCreatedBeforeTime(
                OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME,
                timestamp);
        assertThat(numFiles).isEqualTo(0);

        // odsign should have deleted the pending directory.
        assertThat(getDevice().isDirectory(PENDING_ARTIFACTS_DIR)).isFalse();
    }

    private String[] getAllPendingOdexPaths() throws DeviceNotAvailableException {
        String dir = PENDING_ARTIFACTS_DIR + "/" + mFirstArch;
        return Stream.of(getDevice().getChildren(dir))
                .filter(name -> name.endsWith(".odex"))
                .map(name -> dir + "/" + name)
                .toArray(String[]::new);
    }
}
