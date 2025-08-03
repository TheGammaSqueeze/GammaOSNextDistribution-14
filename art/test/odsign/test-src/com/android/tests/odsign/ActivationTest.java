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

package com.android.tests.odsign;

import static org.junit.Assert.assertTrue;

import com.android.tests.odsign.annotation.CtsTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.Set;

/**
 * Test to check if odrefresh, odsign, fs-verity, and ART runtime work together properly.
 */
@Ignore("See derived classes, each produces the file for running the following verification")
abstract class ActivationTest extends BaseHostJUnit4Test {
    private static final String TEST_APP_PACKAGE_NAME = "com.android.tests.odsign";
    private static final String TEST_APP_APK = "odsign_e2e_test_app.apk";

    private OdsignTestUtils mTestUtils;

    @Before
    public void setUp() throws Exception {
        mTestUtils = new OdsignTestUtils(getTestInformation());
    }

    @Test
    @CtsTestCase
    public void verifyArtUpgradeSignsFiles() throws Exception {
        installPackage(TEST_APP_APK);
        DeviceTestRunOptions options = new DeviceTestRunOptions(TEST_APP_PACKAGE_NAME);
        options.setTestClassName(TEST_APP_PACKAGE_NAME + ".ArtifactsSignedTest");
        options.setTestMethodName("testArtArtifactsHaveFsverity");
        runDeviceTests(options);
    }

    @Test
    @CtsTestCase
    public void verifyArtUpgradeGeneratesAnyArtifacts() throws Exception {
        installPackage(TEST_APP_APK);
        DeviceTestRunOptions options = new DeviceTestRunOptions(TEST_APP_PACKAGE_NAME);
        options.setTestClassName(TEST_APP_PACKAGE_NAME + ".ArtifactsSignedTest");
        options.setTestMethodName("testGeneratesAnyArtArtifacts");
        runDeviceTests(options);
    }

    @Test
    public void verifyArtUpgradeGeneratesRequiredArtifacts() throws Exception {
        installPackage(TEST_APP_APK);
        DeviceTestRunOptions options = new DeviceTestRunOptions(TEST_APP_PACKAGE_NAME);
        options.setTestClassName(TEST_APP_PACKAGE_NAME + ".ArtifactsSignedTest");
        options.setTestMethodName("testGeneratesRequiredArtArtifacts");
        runDeviceTests(options);
    }

    @Test
    public void verifyGeneratedArtifactsLoaded() throws Exception {
        // Check both zygote and system_server processes to see that they have loaded the
        // artifacts compiled and signed by odrefresh and odsign. We check both here rather than
        // having a separate test because the device reboots between each @Test method and
        // that is an expensive use of time.
        mTestUtils.verifyZygotesLoadedPrimaryBootImage();
        mTestUtils.verifyZygotesLoadedBootImageMainlineExtension();
        mTestUtils.verifySystemServerLoadedArtifacts();
    }

    @Test
    public void verifyGeneratedArtifactsLoadedAfterReboot() throws Exception {
        mTestUtils.reboot();
        verifyGeneratedArtifactsLoaded();
    }

    @Test
    public void verifyGeneratedArtifactsLoadedAfterPartialCompilation() throws Exception {
        Set<String> mappedArtifacts = mTestUtils.getSystemServerLoadedArtifacts();
        // Delete an arbitrary artifact.
        getDevice().deleteFile(mappedArtifacts.iterator().next());
        mTestUtils.removeCompilationLogToAvoidBackoff();
        mTestUtils.reboot();
        verifyGeneratedArtifactsLoaded();
    }
}
