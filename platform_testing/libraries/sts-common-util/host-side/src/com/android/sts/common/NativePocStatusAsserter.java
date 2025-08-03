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

package com.android.sts.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tradefed.util.CommandResult;

public class NativePocStatusAsserter {
    private static final int VULNERABLE_EXIT_CODE = 113;

    /** Return a {@link NativePocAsserter} that makes sure PoC did not exit with given code. */
    public static NativePocAsserter assertNotExitCode(final int badExitCode) {
        return new NativePocAsserter() {
            @Override
            public void checkCmdResult(CommandResult result) {
                assertNotEquals(
                        "PoC exited with bad exit code.",
                        (long) badExitCode,
                        (long) result.getExitCode());
            }
        };
    }

    /** Return a {@link NativePocAsserter} that makes sure PoC did not exit with code 113. */
    public static NativePocAsserter assertNotVulnerableExitCode() {
        return assertNotExitCode(VULNERABLE_EXIT_CODE);
    }

    /** Return a {@link NativePocAsserter} that makes sure PoC exited with given code. */
    public static NativePocAsserter assertExitCode(final int exitCode) {
        return new NativePocAsserter() {
            @Override
            public void checkCmdResult(CommandResult result) {
                assertEquals(
                        "PoC did not exit with expected exit code.",
                        (long) exitCode,
                        (long) result.getExitCode());
            }
        };
    }
}
