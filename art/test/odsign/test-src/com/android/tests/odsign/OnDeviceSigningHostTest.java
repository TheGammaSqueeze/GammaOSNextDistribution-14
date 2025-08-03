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

package com.android.tests.odsign;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to check if the early boot compilation works properly.
 *
 * @see ActivationTest for actual tests
 * @see CompOsSigningHostTest for the same test with compilation happens in the CompOS VM
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class OnDeviceSigningHostTest extends ActivationTest {
    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        testUtils.installTestApex();
        testUtils.reboot();
    }

    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        testUtils.uninstallTestApex();
        testUtils.reboot();
    }

    @Test
    public void verifyCompilationLogGenerated() throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(getTestInformation());

        // Check there is a compilation log, we expect compilation to have occurred.
        assertTrue("Compilation log not found", testUtils.haveCompilationLog());
    }
}
