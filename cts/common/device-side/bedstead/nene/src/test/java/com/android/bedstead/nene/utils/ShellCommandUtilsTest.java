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

package com.android.bedstead.nene.utils;

import static android.os.Build.VERSION.SDK_INT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.os.Build;

import com.android.bedstead.nene.exceptions.AdbException;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.function.Function;

@RunWith(JUnit4.class)
public class ShellCommandUtilsTest {

    private static final String LIST_USERS_COMMAND = "pm list users";
    private static final String LIST_USERS_EXPECTED_OUTPUT = "Users:";
    private static final String INVALID_COMMAND_LEGACY_OUTPUT = "pm list-users";
    private static final String INVALID_COMMAND_EXPECTED_LEGACY_OUTPUT = "Unknown command:";
    private static final String INVALID_COMMAND_CORRECT_OUTPUT = "pm set-harmful-app-warning --no";
    private static final Function<String, Boolean> ALWAYS_PASS_OUTPUT_FILTER = (output) -> true;
    private static final Function<String, Boolean> ALWAYS_FAIL_OUTPUT_FILTER = (output) -> false;

    @Test
    public void executeCommand_returnsOutput() throws Exception {
        assertThat(ShellCommandUtils.executeCommand(LIST_USERS_COMMAND))
                .contains(LIST_USERS_EXPECTED_OUTPUT);
    }

    @Test
    @Ignore("This behaviour is not implemented yet")
    public void executeCommand_invalidCommand_legacyOutput_throwsException() {
        assumeTrue(
                "New behaviour is only supported on Android 11+", SDK_INT >= Build.VERSION_CODES.R);
        assertThrows(AdbException.class,
                () -> ShellCommandUtils.executeCommand(INVALID_COMMAND_LEGACY_OUTPUT));
    }

    @Test
    public void executeCommand_invalidCommand_legacyOutput_preAndroid11_throwsException()
            throws Exception {
        // This is currently still the default behaviour
        //assumeTrue("Legacy behaviour is only supported before 11",
        // SDK_INT < Build.VERSION_CODES.R);
        assumeTrue("This command's behaviour changed in Android P",
                SDK_INT >= Build.VERSION_CODES.P);
        assertThat(ShellCommandUtils.executeCommand(INVALID_COMMAND_LEGACY_OUTPUT))
                .contains(INVALID_COMMAND_EXPECTED_LEGACY_OUTPUT);
    }

    @Test
    public void executeCommandAndValidateOutput_outputFilterMatched_returnsOutput()
            throws Exception {
        assertThat(
                ShellCommandUtils.executeCommandAndValidateOutput(
                        LIST_USERS_COMMAND, ALWAYS_PASS_OUTPUT_FILTER))
                .isNotNull();
    }

    @Test
    public void executeCommandAndValidateOutput_outputFilterNotMatched_throwsException() {
        assertThrows(AdbException.class,
                () -> ShellCommandUtils.executeCommandAndValidateOutput(
                        LIST_USERS_COMMAND, ALWAYS_FAIL_OUTPUT_FILTER));
    }

    @Test
    public void executeCommand_invalidCommand_correctOutput_throwsException() {
        assertThrows(AdbException.class,
                () -> ShellCommandUtils.executeCommand(INVALID_COMMAND_CORRECT_OUTPUT));
    }

    @Test
    public void startsWithSuccess_doesStartWithSuccess_returnsTrue() {
        assertThat(ShellCommandUtils.startsWithSuccess("suCceSs: ...")).isTrue();
    }

    @Test
    public void startsWithSuccess_equalsSuccess_returnsTrue() {
        assertThat(ShellCommandUtils.startsWithSuccess("success")).isTrue();
    }

    @Test
    public void startsWithSuccess_doesNotStartWithSuccess_returnsFalse() {
        assertThat(ShellCommandUtils.startsWithSuccess("not success...")).isFalse();
    }
}
