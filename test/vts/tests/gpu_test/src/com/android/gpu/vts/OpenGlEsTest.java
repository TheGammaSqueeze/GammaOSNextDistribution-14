/*
 * Copyright (C) 2023 Google LLC.
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
package com.android.gpu.vts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.VsrTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * VTS test for OpenGL ES requirements.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class OpenGlEsTest extends BaseHostJUnit4Test {
    /**
     * Verify that FEATURE_OPENGLES_DEQP_LEVEL (feature:android.software.opengles.deqp.level) has a
     * sufficiently high version in relation to the vendor and first product API level.
     */
    @VsrTest(requirements = {"VSR-5.1-001", "VSR-5.1-003", "VSR-5.1-004"})
    @Test
    public void checkOpenGlEsDeqpLevelIsHighEnough() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for API level lower than S", apiLevel >= Build.SC);

        // Map from API level to required dEQP level.
        final int requiredOpenGlEsDeqpLevel;
        switch (apiLevel) {
            case Build.SC:
            case Build.SC_V2:
                requiredOpenGlEsDeqpLevel = VulkanTest.DEQP_LEVEL_FOR_S;
                break;
            case Build.TM:
                requiredOpenGlEsDeqpLevel = VulkanTest.DEQP_LEVEL_FOR_T;
                break;
            case Build.UDC:
                requiredOpenGlEsDeqpLevel = VulkanTest.DEQP_LEVEL_FOR_T;
                break;
            case Build.VIC:
                requiredOpenGlEsDeqpLevel = VulkanTest.DEQP_LEVEL_FOR_V;
                break;
            default:
                fail("Test should only run for API levels: S, Sv2, T, UDC, VIC...");
                return;
        }

        // Check that the feature flag is present and its value is at least the required dEQP level.
        final String output = getDevice().executeShellCommand(
                String.format("pm has-feature android.software.opengles.deqp.level %d",
                        requiredOpenGlEsDeqpLevel));

        if (!output.trim().equals("true")) {
            final String message = String.format(
                    "Advertised GLES dEQP level feature is too low or does not exist.\n"
                            + "Expected:\nfeature:android.software.opengles.deqp.level>=%d\n"
                            + "Actual:\n%s",
                    requiredOpenGlEsDeqpLevel,
                    getDevice().executeShellCommand("pm list features | grep deqp"));

            fail(message);
        }
    }
}
