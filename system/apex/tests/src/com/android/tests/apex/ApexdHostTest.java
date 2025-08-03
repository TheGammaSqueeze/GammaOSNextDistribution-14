/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.apex.ApexInfo;
import com.android.apex.XmlParser;
import com.android.tests.rollback.host.AbandonSessionsRule;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Host side integration tests for apexd.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ApexdHostTest extends BaseHostJUnit4Test  {

    private static final String SHIM_APEX_PATH = "/system/apex/com.android.apex.cts.shim.apex";

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    @Rule
    public AbandonSessionsRule mHostTestRule = new AbandonSessionsRule(this);

    private boolean mWasAdbRoot = false;

    @Before
    public void setUp() throws Exception {
        mHostUtils.uninstallShimApexIfNecessary();
        mWasAdbRoot = getDevice().isAdbRoot();
        if (!mWasAdbRoot) {
            assumeTrue("Device requires root", getDevice().enableAdbRoot());
        }
    }

    @After
    public void tearDown() throws Exception {
        mHostUtils.uninstallShimApexIfNecessary();
        if (!mWasAdbRoot) {
            getDevice().disableAdbRoot();
        }
    }

    private boolean deviceHasActiveApex(String apexName) throws Exception {
        return getDevice().getActiveApexes().stream().anyMatch(
                apex -> apex.name.equals(apexName));
    }

    @Test
    public void testOrphanedApexIsNotActivated() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        try {
            assertThat(getDevice().pushFile(mHostUtils.getTestFile("apex.apexd_test_v2.apex"),
                    "/data/apex/active/apexd_test_v2.apex")).isTrue();
            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();
            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            ITestDevice.ApexInfo testApex = new ITestDevice.ApexInfo(
                    "com.android.apex.test_package", 2L);
            assertThat(activeApexes).doesNotContain(testApex);
            mHostUtils.waitForFileDeleted("/data/apex/active/apexd_test_v2.apex",
                    Duration.ofMinutes(3));
        } finally {
            getDevice().executeShellV2Command("rm /data/apex/active/apexd_test_v2.apex");
        }
    }
    @Test
    public void testApexWithoutPbIsNotActivated() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        final String testApexFile = "com.android.apex.cts.shim.v2_no_pb.apex";
        try {
            assertThat(getDevice().pushFile(mHostUtils.getTestFile(testApexFile),
                    "/data/apex/active/" + testApexFile)).isTrue();
            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();
            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            ITestDevice.ApexInfo testApex = new ITestDevice.ApexInfo(
                    "com.android.apex.cts.shim", 2L);
            assertThat(activeApexes).doesNotContain(testApex);
            mHostUtils.waitForFileDeleted("/data/apex/active/" + testApexFile,
                    Duration.ofMinutes(3));
        } finally {
            getDevice().executeShellV2Command("rm /data/apex/active/" + testApexFile);
        }
    }

    @Test
    public void testRemountApex() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        final File oldFile = getDevice().pullFile(SHIM_APEX_PATH);
        try {
            getDevice().remountSystemWritable();
            // In case remount requires a reboot, wait for boot to complete.
            getDevice().waitForBootComplete(Duration.ofMinutes(3).toMillis());
            final File newFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");
            // Stop framework
            getDevice().executeShellV2Command("stop");
            // Push new shim APEX. This simulates adb sync.
            getDevice().pushFile(newFile, SHIM_APEX_PATH);
            // Ask apexd to remount packages
            getDevice().executeShellV2Command("cmd -w apexservice remountPackages");
            // Start framework
            getDevice().executeShellV2Command("start");
            // Give enough time for system_server to boot.
            Thread.sleep(Duration.ofSeconds(15).toMillis());
            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            ITestDevice.ApexInfo testApex = new ITestDevice.ApexInfo(
                    "com.android.apex.cts.shim", 2L);
            assertThat(activeApexes).contains(testApex);
        } finally {
            getDevice().pushFile(oldFile, SHIM_APEX_PATH);
            getDevice().reboot();
        }
    }

    @Test
    public void testApexWithoutPbIsNotActivated_ProductPartitionHasOlderVersion()
            throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        try {
            getDevice().remountSystemWritable();
            // In case remount requires a reboot, wait for boot to complete.
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            final File v1 = mHostUtils.getTestFile("apex.apexd_test.apex");
            getDevice().pushFile(v1, "/product/apex/apex.apexd_test.apex");

            final File v2_no_pb = mHostUtils.getTestFile("apex.apexd_test_v2_no_pb.apex");
            getDevice().pushFile(v2_no_pb, "/data/apex/active/apex.apexd_test_v2_no_pb.apex");

            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            assertThat(activeApexes).contains(new ITestDevice.ApexInfo(
                    "com.android.apex.test_package", 1L));
            assertThat(activeApexes).doesNotContain(new ITestDevice.ApexInfo(
                    "com.android.apex.test_package", 2L));

            // v2_no_pb should be deleted
            mHostUtils.waitForFileDeleted("/data/apex/active/apex.apexd_test_v2_no_pb.apex",
                    Duration.ofMinutes(3));
        } finally {
            getDevice().remountSystemWritable();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            getDevice().executeShellV2Command("rm /product/apex/apex.apexd_test.apex");
            getDevice().executeShellV2Command("rm /data/apex/active/apex.apexd_test_v2_no_pb.apex");
        }
    }

    @Test
    public void testApexWithoutPbIsNotActivated_ProductPartitionHasNewerVersion()
            throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        try {
            getDevice().remountSystemWritable();
            // In case remount requires a reboot, wait for boot to complete.
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            final File v3 = mHostUtils.getTestFile("apex.apexd_test_v3.apex");
            getDevice().pushFile(v3, "/product/apex/apex.apexd_test_v3.apex");

            final File v2_no_pb = mHostUtils.getTestFile("apex.apexd_test_v2_no_pb.apex");
            getDevice().pushFile(v2_no_pb, "/data/apex/active/apex.apexd_test_v2_no_pb.apex");

            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            assertThat(activeApexes).contains(new ITestDevice.ApexInfo(
                    "com.android.apex.test_package", 3L));
            assertThat(activeApexes).doesNotContain(new ITestDevice.ApexInfo(
                    "com.android.apex.test_package", 2L));

            // v2_no_pb should be deleted
            mHostUtils.waitForFileDeleted("/data/apex/active/apex.apexd_test_v2_no_pb.apex",
                    Duration.ofMinutes(3));
        } finally {
            getDevice().remountSystemWritable();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            getDevice().executeShellV2Command("rm /product/apex/apex.apexd_test_v3.apex");
            getDevice().executeShellV2Command("rm /data/apex/active/apex.apexd_test_v2_no_pb.apex");
        }
    }

    @Test
    public void testApexInfoListIsValid() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        try (FileInputStream fis = new FileInputStream(
                getDevice().pullFile("/apex/apex-info-list.xml"))) {
            // #1 Data got from apexd via binder
            Set<ITestDevice.ApexInfo> fromApexd = getDevice().getActiveApexes();
            // #2 Data got from the xml file (key is the path)
            Map<String, ApexInfo> fromXml = XmlParser.readApexInfoList(fis).getApexInfo().stream()
                    .collect(Collectors.toMap(ApexInfo::getModulePath, ai -> ai));

            // Make sure that all items in #1 are also in #2 and they are identical
            for (ITestDevice.ApexInfo ai : fromApexd) {
                ApexInfo apexFromXml = fromXml.get(ai.sourceDir);
                assertWithMessage("APEX (" + ai.toString() + ") is not found in the list")
                        .that(apexFromXml).isNotNull();
                assertWithMessage("Version mismatch for APEX (" + ai.toString() + ")")
                        .that(ai.versionCode).isEqualTo(apexFromXml.getVersionCode());
                assertWithMessage("APEX (" + ai.toString() + ") is not active")
                        .that(apexFromXml.getIsActive()).isTrue();
            }
        }
    }

    /**
     * Test to verify that the state of a staged session does not change if apexd is stopped and
     * restarted while a session is staged.
     */
    @Test
    public void testApexSessionStateUnchangedBeforeReboot() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        File apexFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();
        String sessionId = getDevice().executeShellCommand(
                "pm get-stagedsessions --only-ready --only-parent --only-sessionid").trim();
        assertThat(sessionId).isNotEmpty();
        String sessionStateCmd = "cmd -w apexservice getStagedSessionInfo " + sessionId;
        String initialState = getDevice().executeShellV2Command(sessionStateCmd).getStdout();
        assertThat(initialState).isNotEmpty();

        // Kill apexd. This means apexd will perform its start logic when the second install
        // is staged.
        getDevice().executeShellV2Command("kill `pidof apexd`");

        // Verify that the session state remains consistent after apexd has restarted.
        String updatedState = getDevice().executeShellV2Command(sessionStateCmd).getStdout();
        assertThat(updatedState).isEqualTo(initialState);
    }

    /**
     * Verifies that content of {@code /data/apex/sessions/} is migrated to the {@code
     * /metadata/apex/sessions}.
     */
    @Test
    public void testSessionsDirMigrationToMetadata() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        try {
            getDevice().executeShellV2Command("mkdir -p /data/apex/sessions/1543");
            File file = File.createTempFile("foo", "bar");
            getDevice().pushFile(file, "/data/apex/sessions/1543/file");

            // During boot sequence apexd will move /data/apex/sessions/1543/file to
            // /metadata/apex/sessions/1543/file.
            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            assertThat(getDevice().doesFileExist("/metadata/apex/sessions/1543/file")).isTrue();
            assertThat(getDevice().doesFileExist("/data/apex/sessions/1543/file")).isFalse();
        } finally {
            getDevice().executeShellV2Command("rm -R /data/apex/sessions/1543");
            getDevice().executeShellV2Command("rm -R /metadata/apex/sessions/1543");
        }
    }

    @Test
    public void testFailsToActivateApexOnDataFallbacksToPreInstalled() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        try {
            final File file =
                    mHostUtils.getTestFile("com.android.apex.cts.shim.v2_additional_file.apex");
            getDevice().pushFile(file, "/data/apex/active/com.android.apex.cts.shim@2.apex");

            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

            // After reboot pre-installed version of shim apex should be activated, and corrupted
            // version on /data should be deleted.
            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            ITestDevice.ApexInfo testApex = new ITestDevice.ApexInfo(
                    "com.android.apex.cts.shim", 1L);
            assertThat(activeApexes).contains(testApex);
            assertThat(
                    getDevice()
                            .doesFileExist("/data/apex/active/com.android.apex.cts.shim@2.apex"))
                    .isFalse();
        } finally {
            getDevice().deleteFile("/data/apex/active/com.android.apex.cts.shim@2.apex");
        }
    }

    /**
     * Test to verify that apexd will reject a vendor apex update with an
     *     updatable-via-apex value that doesn't match the apex's interface.
     *     Install method is reboot-needing (staged) installation.
     */
    @Test
    public void testRejectsStagedApexWithIncorrectUpdatableViaApexValue() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.bad1.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get an error
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNotNull();
        // Verify error text involves the device manifest (Note the actual
        //   parser error is visible in the log, but it doesn't get passed
        //   up through libvintf, so we end up with a manifest-related error)
        assertThat(error).contains("No device manifest");
    }

    /**
     * Test to verify that apexd will reject a vendor apex update with a
     *     vintf fragment containing syntax-invalid XML. Staged installation.
     */
    @Test
    public void testRejectsStagedApexWithInvalidSyntaxVintfFragment() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.bad2.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get our device manifest error
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNotNull();
        assertThat(error).contains("No device manifest");
    }

    /**
     * Test to verify that apexd will reject a vendor apex that tries to
     *     update an unrelated hardware interface.  Staged installation.
     */
    @Test
    public void testRejectsStagedApexThatUpdatesUnrelatedHardware() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.bad3.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get a manifest/matix compatibility error
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNotNull();
        assertThat(error).contains(
                "Device manifest and framework compatibility matrix are incompatible");
    }

    /**
     * Test to verify that apexd will accept a good vendor apex update
     *     Install method is immediate (rebootless) (non-staged) installation.
     */
    @Test
    public void testAcceptsGoodRebootlessApex() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.good1.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get an error
        String error = mHostUtils.installRebootlessPackage(apexFile);
        assertThat(error).isNull();
    }

    /**
     * Test to verify that apexd will reject a vendor apex update with an
     *     updatable-via-apex value that doesn't match the apex's interface.
     *     Install method is immediate (rebootless) (non-staged) installation.
     */
    @Test
    public void testRejectsRebootlessApexWithIncorrectUpdatableViaApexValue() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.bad1.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get an error
        String error = mHostUtils.installRebootlessPackage(apexFile);
        assertThat(error).isNotNull();
        // Verify error text involves the device manifest (Note the actual
        //   parser error is visible in the log, but it doesn't get passed
        //   up through libvintf, so we end up with a manifest-related error)
        assertThat(error).contains("No device manifest");
    }

    /**
     * Test to verify that apexd will reject a vendor apex update with a
     *     vintf fragment containing syntax-invalid XML.
     */
    @Test
    public void testRejectsRebootlessApexWithInvalidSyntaxVintfFragment() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.bad2.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get our device manifest error
        String error = mHostUtils.installRebootlessPackage(apexFile);
        assertThat(error).isNotNull();
        assertThat(error).contains("No device manifest");
    }

    /**
     * Test to verify that apexd will reject a vendor apex tries to
     *     update an unrelated hardware interface.
     */
    @Test
    public void testRejectsRebootlessApexThatUpdatesUnrelatedHardware() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        assumeTrue("Device test requires wifi hardware",
                getDevice().hasFeature("android.hardware.wifi"));
        assumeTrue("Device test requires an active wifi apex",
                deviceHasActiveApex("com.android.hardware.wifi"));

        String apex_filename = "test.bad3.com.android.hardware.wifi.apex";

        File apexFile = mHostUtils.getTestFile(apex_filename);

        // Try to install it, we should get a manifest/matix compatibility error
        String error = mHostUtils.installRebootlessPackage(apexFile);
        assertThat(error).isNotNull();
        assertThat(error).contains(
                "Device manifest and framework compatibility matrix are incompatible");
    }
}
