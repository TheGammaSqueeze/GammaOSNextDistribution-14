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

package com.android.tests.loadingprogress.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * atest com.android.tests.loadingprogress.host.NonIncrementalLoadingProgressTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class NonIncrementalLoadingProgressTest extends BaseHostJUnit4Test {
    private static final String DEVICE_TEST_PACKAGE_NAME =
            "com.android.tests.loadingprogress.device";
    private static final String TEST_APK = "CtsInstalledLoadingProgressTestsApp.apk";
    private static final String TEST_APP_PACKAGE_NAME = "com.android.tests.loadingprogress.app";

    @Before
    public void setUp() throws Exception {
        assertFalse(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(TEST_APK);
        assertNotNull(apk);
        assertNull(getDevice().installPackage(apk, false, "-t"));
        assertTrue(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_APP_PACKAGE_NAME);
        assertFalse(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
    }

    @Test
    public void testGetLoadingProgressSuccess() throws Exception {
        // Loading progress of non-incremental apps should always be 1
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME,
                DEVICE_TEST_PACKAGE_NAME + ".LoadingProgressTest",
                "testGetFullLoadingProgress"));
    }
}
