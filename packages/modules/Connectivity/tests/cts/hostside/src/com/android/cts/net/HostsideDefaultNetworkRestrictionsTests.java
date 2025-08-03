/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.net;

import static com.android.cts.net.arguments.InstrumentationArguments.ARG_WAIVE_BIND_PRIORITY;

import com.android.testutils.SkipPresubmit;
import com.android.tradefed.device.DeviceNotAvailableException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

// TODO(b/321848487): Annotate with @RequiresFlagsEnabled to mirror the device-side tests.
@SkipPresubmit(reason = "Monitoring for flakiness")
public class HostsideDefaultNetworkRestrictionsTests extends HostsideNetworkTestCase {
    private static final String METERED_TEST_CLASS = TEST_PKG + ".DefaultRestrictionsMeteredTest";
    private static final String NON_METERED_TEST_CLASS =
            TEST_PKG + ".DefaultRestrictionsNonMeteredTest";

    @Before
    public void setUp() throws Exception {
        uninstallPackage(TEST_APP2_PKG, false);
        installPackage(TEST_APP2_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP2_PKG, true);
    }

    private void runMeteredTest(String methodName) throws DeviceNotAvailableException {
        runDeviceTestsWithArgs(TEST_PKG, METERED_TEST_CLASS, methodName,
                Map.of(ARG_WAIVE_BIND_PRIORITY, "true"));
    }

    private void runNonMeteredTest(String methodName) throws DeviceNotAvailableException {
        runDeviceTestsWithArgs(TEST_PKG, NON_METERED_TEST_CLASS, methodName,
                Map.of(ARG_WAIVE_BIND_PRIORITY, "true"));
    }

    @Test
    public void testMeteredNetworkAccess_defaultRestrictions_testActivityNetworkAccess()
            throws Exception {
        runMeteredTest("testActivityNetworkAccess");
    }

    @Test
    public void testMeteredNetworkAccess_defaultRestrictions_testFgsNetworkAccess()
            throws Exception {
        runMeteredTest("testFgsNetworkAccess");
    }

    @Test
    public void testMeteredNetworkAccess_defaultRestrictions_inFullAllowlist() throws Exception {
        runMeteredTest("testBackgroundNetworkAccess_inFullAllowlist");
    }

    @Test
    public void testMeteredNetworkAccess_defaultRestrictions_inExceptIdleAllowlist()
            throws Exception {
        runMeteredTest("testBackgroundNetworkAccess_inExceptIdleAllowlist");
    }

    @Test
    public void testNonMeteredNetworkAccess_defaultRestrictions_testActivityNetworkAccess()
            throws Exception {
        runNonMeteredTest("testActivityNetworkAccess");
    }

    @Test
    public void testNonMeteredNetworkAccess_defaultRestrictions_testFgsNetworkAccess()
            throws Exception {
        runNonMeteredTest("testFgsNetworkAccess");
    }

    @Test
    public void testNonMeteredNetworkAccess_defaultRestrictions_inFullAllowlist() throws Exception {
        runNonMeteredTest("testBackgroundNetworkAccess_inFullAllowlist");
    }

    @Test
    public void testNonMeteredNetworkAccess_defaultRestrictions_inExceptIdleAllowlist()
            throws Exception {
        runNonMeteredTest("testBackgroundNetworkAccess_inExceptIdleAllowlist");
    }
}
