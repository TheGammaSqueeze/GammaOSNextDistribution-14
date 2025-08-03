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

package com.android.internal.car;

import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.car.apitest.CarApiTestBase;
import android.os.Build;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.io.IOException;

public final class CarServiceHelperServiceSystemTest extends CarApiTestBase {

    @Test
    @ApiTest(apis = {"com.android.internal.car.CarServiceHelperInterface#setSafetyMode(boolean)"})
    public void testCarServiceHelperServiceDump_safeMode() throws Exception {
        assumeSystemServerDumpSupported();
        assumeUserDebugBuild();

        // Should be parked already, but it doesn't hurt to make sure
        executeShellCommand("cmd car_service emulate-driving-state park");

        eventually(()-> assertWithMessage("CarServiceHelperService dump")
                .that(dumpCarServiceHelper())
                .contains("Safe to run device policy operations: true"));
    }

    @Test
    @ApiTest(apis = {"com.android.internal.car.CarServiceHelperInterface#setSafetyMode(boolean)"})
    public void testCarServiceHelperServiceDump_unsafeMode() throws Exception {
        assumeSystemServerDumpSupported();
        assumeUserDebugBuild();
        try {
            executeShellCommand("cmd car_service emulate-driving-state drive");

            eventually(() -> assertWithMessage("CarServiceHelperService dump")
                    .that(dumpCarServiceHelper())
                    .contains("Safe to run device policy operations: false"));
        } finally {
            executeShellCommand("cmd car_service emulate-driving-state park");
            // Assert that state is changed back
            eventually(()-> assertWithMessage("CarServiceHelperService dump")
                    .that(dumpCarServiceHelper())
                    .contains("Safe to run device policy operations: true"));
        }
    }

    @Test
    @ApiTest(apis = {"com.android.internal.car.CarServiceHelperService#dump(PrintWriter,String[])"})
    public void testCarServiceHelperServiceDump_safeOperation() throws Exception {
        assumeSystemServerDumpSupported();
        assumeUserDebugBuild();
        // Should be parked already, but it doesn't hurt to make sure
        executeShellCommand("cmd car_service emulate-driving-state park");

        eventually(()-> assertWithMessage("CarServiceHelperService dump")
                .that(dumpCarServiceHelper("--is-operation-safe", "7"))
                .contains("Operation REBOOT is SAFE. Reason: NONE"));
    }

    @Test
    @ApiTest(apis = {"com.android.internal.car.CarServiceHelperService#dump(PrintWriter,String[])"})
    public void testCarServiceHelperServiceDump_unsafeOperation() throws Exception {
        assumeSystemServerDumpSupported();
        assumeUserDebugBuild();
        try {
            executeShellCommand("cmd car_service emulate-driving-state drive");

            eventually(()-> assertWithMessage("CarServiceHelperService dump")
                    .that(dumpCarServiceHelper("--is-operation-safe", "7"))
                    .contains("Operation REBOOT is UNSAFE. Reason: DRIVING_DISTRACTION"));
        } finally {
            executeShellCommand("cmd car_service emulate-driving-state park");
            // Assert that state is changed back
            eventually(()-> assertWithMessage("CarServiceHelperService dump")
                    .that(dumpCarServiceHelper("--is-operation-safe", "7"))
                    .contains("Operation REBOOT is SAFE. Reason: NONE"));
        }
    }

    private static void assumeSystemServerDumpSupported() throws IOException {
        assumeThat("System_server_dumper not implemented.",
                executeShellCommand("service check system_server_dumper"),
                containsStringIgnoringCase("system_server_dumper: found"));
    }

    private static void assumeUserDebugBuild() {
        assumeTrue("Not a user debug build", !Build.TYPE.equalsIgnoreCase("user"));
    }

    private String dumpCarServiceHelper(String...args) throws IOException {
        StringBuilder cmd = new StringBuilder(
                "dumpsys system_server_dumper --name CarServiceHelper");
        for (String arg : args) {
            cmd.append(' ').append(arg);
        }
        return executeShellCommand(cmd.toString());
    }
}
