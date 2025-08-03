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

import com.android.tests.odsign.annotation.CtsTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to check if CompOS works properly.
 *
 * @see ActivationTest for actual tests
 * @see OnDeviceSigningHostTest for the same test with compilation happens in early boot
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CompOsSigningHostTest extends ActivationTest {

    private static final String ORIGINAL_CHECKSUMS_KEY = "compos_test_orig_checksums";
    private static final String PENDING_CHECKSUMS_KEY = "compos_test_pending_checksums";
    private static final String TIMESTAMP_VM_START_KEY = "compos_test_timestamp_vm_start";
    private static final String TIMESTAMP_REBOOT_KEY = "compos_test_timestamp_reboot";

    @Rule public TestLogData mTestLogs = new TestLogData();

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        ITestDevice device = testInfo.getDevice();
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        CompOsTestUtils compOsTestUtils = new CompOsTestUtils(device);

        compOsTestUtils.assumeNotOnCuttlefish();
        compOsTestUtils.assumeCompOsPresent();

        testInfo.properties().put(ORIGINAL_CHECKSUMS_KEY,
                compOsTestUtils.checksumDirectoryContentPartial(
                        OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME));

        try {
            testUtils.installTestApex();

            testInfo.properties().put(TIMESTAMP_VM_START_KEY,
                    String.valueOf(testUtils.getCurrentTimeMs()));

            compOsTestUtils.runCompilationJobEarlyAndWait();

            testInfo.properties().put(PENDING_CHECKSUMS_KEY,
                    compOsTestUtils.checksumDirectoryContentPartial(PENDING_ARTIFACTS_DIR));
            testInfo.properties().put(TIMESTAMP_REBOOT_KEY,
                    String.valueOf(testUtils.getCurrentTimeMs()));
        } finally {
            // Make sure to reboot after installTestApex.
            testUtils.reboot();
        }
    }

    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        try {
            testUtils.uninstallTestApex();
        } finally {
            testUtils.reboot();  // must happen
        }
    }

    @Test
    @CtsTestCase
    public void checkFileChecksums() throws Exception {
        CompOsTestUtils compOsTestUtils = new CompOsTestUtils(getDevice());
        String actualChecksums = compOsTestUtils.checksumDirectoryContentPartial(
                OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME);

        String pendingChecksums = getTestInformation().properties().get(PENDING_CHECKSUMS_KEY);
        assertThat(actualChecksums).isEqualTo(pendingChecksums);

        // With test apex, the output should be different.
        String originalChecksums = getTestInformation().properties().get(ORIGINAL_CHECKSUMS_KEY);
        assertThat(actualChecksums).isNotEqualTo(originalChecksums);
    }

    @Test
    @CtsTestCase
    public void checkFileCreationTimeAfterVmStartAndBeforeReboot() throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(getTestInformation());

        // No files are created before our VM starts.
        int numFiles = testUtils.countFilesCreatedBeforeTime(
                OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME,
                Long.parseLong(getTestInformation().properties().get(TIMESTAMP_VM_START_KEY)));
        assertThat(numFiles).isEqualTo(0);

        // (All) Files are created after our VM starts.
        numFiles = testUtils.countFilesCreatedAfterTime(
                OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME,
                Long.parseLong(getTestInformation().properties().get(TIMESTAMP_VM_START_KEY)));
        assertThat(numFiles).isGreaterThan(0);

        // No files are created after reboot.
        numFiles = testUtils.countFilesCreatedAfterTime(
                OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME,
                Long.parseLong(getTestInformation().properties().get(TIMESTAMP_REBOOT_KEY)));
        assertThat(numFiles).isEqualTo(0);
    }
}
