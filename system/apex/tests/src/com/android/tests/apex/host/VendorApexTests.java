/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tests.apex.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;
import android.platform.test.annotations.LargeTest;

import com.android.apex.ApexInfo;
import com.android.apex.XmlParser;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class VendorApexTests extends BaseHostJUnit4Test {

    private static final String TAG = "VendorApexTests";
    private static final String APEX_PACKAGE_NAME = "com.android.apex.vendor.foo";

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests("com.android.tests.vendorapex.app",
                "com.android.tests.apex.app.VendorApexTests",
                phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());

        // TODO(b/280155297) workaround to ensure RW. When partition.vendor.verified is still
        // set to something, then the device should be remount-ed again.
        getDevice().remountVendorWritable();
        String verity = getDevice().getProperty("partition.vendor.verified");
        if (verity != null && !verity.isEmpty()) {
            getDevice().remountVendorWritable();
        }
    }

    @After
    public void tearDown() throws Exception {
        deleteFiles("/vendor/apex/" + APEX_PACKAGE_NAME + "*apex",
                "/data/apex/active/" + APEX_PACKAGE_NAME + "*apex");
    }

    @Test
    @LargeTest
    public void testRebootlessUpdate() throws Exception {
        pushPreinstalledApex("com.android.apex.vendor.foo.apex");

        runPhase("testRebootlessUpdate");
    }

    @Test
    @LargeTest
    public void testGenerateLinkerConfigurationOnUpdate() throws Exception {
        pushPreinstalledApex("com.android.apex.vendor.foo.apex");
        runPhase("testGenerateLinkerConfigurationOnUpdate");
    }

    @Test
    @LargeTest
    public void testInstallAbortsWhenVndkVersionMismatches() throws Exception {
        pushPreinstalledApex("com.android.apex.vendor.foo.apex");
        runPhase("testInstallAbortsWhenVndkVersionMismatches");
        runPhase("testInstallAbortsWhenVndkVersionMismatches_Staged");
    }

    @Test
    @LargeTest
    public void testApexAllReady() throws Exception {
        pushPreinstalledApex("com.android.apex.vendor.foo.apex.all.ready.apex");
        assertThat(getDevice().getProperty("vendor.test.apex.all.ready")).isEqualTo("triggered");
    }

    @Test
    @LargeTest
    public void testRestartServiceAfterRebootlessUpdate() throws Exception {
        pushPreinstalledApex("com.android.apex.vendor.foo.v1_with_service.apex");
        runPhase("testRestartServiceAfterRebootlessUpdate");
    }

    @Test
    @LargeTest
    public void testVendorBootstrapApex() throws Exception {
        pushPreinstalledApex("com.android.apex.vendor.foo.bootstrap.apex");

        // Now there should be "com.android.apex.vendor.foo" activated as an
        // bootstrap apex, listed in apex-info-list in the bootstrap mount namespace.
        try (FileInputStream fis = new FileInputStream(
                getDevice().pullFile("/bootstrap-apex/apex-info-list.xml"))) {
            List<String> names = XmlParser.readApexInfoList(fis)
                    .getApexInfo()
                    .stream()
                    .map(ApexInfo::getModuleName)
                    .collect(toList());
            assertThat(names).contains("com.android.apex.vendor.foo");
        }

        // And also the `early_hal` service in the apex (apex_vendor_foo) should
        // be started in the bootstrap mount namespace.
        assertThat(getMountNamespaceFor("$(pidof apex_vendor_foo)"))
            .isEqualTo(getMountNamespaceFor("$(pidof vold)"));
    }

    private String getMountNamespaceFor(String proc) throws Exception {
        CommandResult result =
                getDevice().executeShellV2Command("readlink /proc/" + proc + "/ns/mnt");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new RuntimeException("failed to read namespace for " + proc);
        }
        return result.getStdout().trim();
    }

    private void pushPreinstalledApex(String fileName) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apex = buildHelper.getTestFile(fileName);
        assertTrue(getDevice().pushFile(apex, Paths.get("/vendor/apex", fileName).toString()));
        getDevice().reboot();
    }

    /**
     * Deletes files and reboots the device if necessary.
     * @param files the paths of files which might contain wildcards
     */
    private void deleteFiles(String... files) throws Exception {
        boolean found = false;
        for (String file : files) {
            CommandResult result = getDevice().executeShellV2Command("ls " + file);
            if (result.getStatus() == CommandStatus.SUCCESS) {
                found = true;
                break;
            }
        }

        if (found) {
            getDevice().remountVendorWritable();
            for (String file : files) {
                getDevice().executeShellCommand("rm -rf " + file);
            }
            getDevice().reboot();
        }
    }
}
