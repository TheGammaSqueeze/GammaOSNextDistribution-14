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
public class ShellCommandTest {

    private static final String LIST_USERS_COMMAND = "pm list users";
    private static final String LIST_USERS_EXPECTED_OUTPUT = "Users:";
    private static final String INVALID_COMMAND_LEGACY_OUTPUT = "pm list-users";
    private static final String INVALID_COMMAND_EXPECTED_LEGACY_OUTPUT = "Unknown command:";
    private static final String INVALID_COMMAND_CORRECT_OUTPUT = "pm set-harmful-app-warning --no";
    private static final String COMMAND_WITH_EMPTY_OUTPUT = "am stop-user 99999";
    private static final Function<String, Boolean> ALWAYS_PASS_OUTPUT_FILTER = (output) -> true;
    private static final Function<String, Boolean> ALWAYS_FAIL_OUTPUT_FILTER = (output) -> false;
    private static final String COMMAND = "pm list users";

    @Test
    public void constructBuilder_nullCommand_throwsException() {
        assertThrows(NullPointerException.class, () -> ShellCommand.builder(null));
    }

    @Test
    public void constructBuilder_constructs() {
        assertThat(ShellCommand.builder(/* command= */ COMMAND)).isNotNull();
    }

    @Test
    public void build_containsCommand() {
        assertThat(ShellCommand.builder(/* command= */ COMMAND).build()).isEqualTo(COMMAND);
    }

    @Test
    public void build_containsOption() {
        ShellCommand.Builder builder = ShellCommand.builder("command")
                .addOption("--optionkey", "optionvalue");


        assertThat(builder.build()).isEqualTo("command --optionkey optionvalue");
    }

    @Test
    public void build_containsOptions() {
        ShellCommand.Builder builder = ShellCommand.builder("command")
                .addOption("--optionkey", "optionvalue")
                .addOption("--optionkey2", "optionvalue2");


        assertThat(builder.build()).isEqualTo(
                "command --optionkey optionvalue --optionkey2 optionvalue2");
    }

    @Test
    public void build_containsOperand() {
        ShellCommand.Builder builder = ShellCommand.builder("command")
                .addOperand("operand");


        assertThat(builder.build()).isEqualTo("command operand");
    }

    @Test
    public void build_containsOperands() {
        ShellCommand.Builder builder = ShellCommand.builder("command")
                .addOperand("operand")
                .addOperand("operand2");


        assertThat(builder.build()).isEqualTo("command operand operand2");
    }

    @Test
    public void build_interleavesOptionsAndOperands() {
        ShellCommand.Builder builder = ShellCommand.builder("command")
                .addOperand("operand")
                .addOption("--optionkey", "optionvalue")
                .addOperand("operand2");


        assertThat(builder.build()).isEqualTo("command operand --optionkey optionvalue operand2");
    }

    @Test
    public void execute_returnsOutput() throws Exception {
        assertThat(ShellCommand.builder(LIST_USERS_COMMAND).execute())
                .contains(LIST_USERS_EXPECTED_OUTPUT);
    }

    @Test
    @Ignore("This behaviour is not implemented yet")
    public void execute_invalidCommand_legacyOutput_throwsException() {
        assumeTrue(
                "New behaviour is only supported on Android 11+", SDK_INT >= Build.VERSION_CODES.R);
        assertThrows(AdbException.class,
                () -> ShellCommand.builder(INVALID_COMMAND_LEGACY_OUTPUT).execute());
    }

    @Test
    public void execute_invalidCommand_legacyOutput_preAndroid11_throwsException()
            throws Exception {
        // This is currently still the default behaviour
        //assumeTrue("Legacy behaviour is only supported before 11",
        // SDK_INT < Build.VERSION_CODES.R);
        assumeTrue("This command's behaviour changed in Android P",
                SDK_INT >= Build.VERSION_CODES.P);
        assertThat(ShellCommand.builder(INVALID_COMMAND_LEGACY_OUTPUT).execute())
                .contains(INVALID_COMMAND_EXPECTED_LEGACY_OUTPUT);
    }

    @Test
    public void execute_validate_outputFilterMatched_returnsOutput() throws Exception {
        assertThat(
                ShellCommand.builder(LIST_USERS_COMMAND)
                        .validate(ALWAYS_PASS_OUTPUT_FILTER)
                        .execute())
                .isNotNull();
    }

    @Test
    public void executeAndValidateOutput_outputFilterNotMatched_throwsException() {
        assertThrows(AdbException.class,
                () -> ShellCommand.builder(LIST_USERS_COMMAND)
                        .validate(ALWAYS_FAIL_OUTPUT_FILTER)
                        .execute());
    }

    @Test
    public void execute_invalidCommand_correctOutput_throwsException() {
        assertThrows(AdbException.class,
                () -> ShellCommand.builder(INVALID_COMMAND_CORRECT_OUTPUT).execute());
    }

    @Test
    public void execute_allowEmptyOutput_commandHasEmptyOutput_returnsOutput()
            throws Exception {
        assertThat(
                ShellCommand.builder(COMMAND_WITH_EMPTY_OUTPUT)
                        .allowEmptyOutput(true)
                        .execute())
                .isEmpty();
    }

    @Test
    public void execute_allowEmptyOutput_commandHasNonEmptyOutput_returnsOutput()
            throws Exception {
        assertThat(ShellCommand.builder(LIST_USERS_COMMAND)
                .allowEmptyOutput(true)
                .execute())
                .contains(LIST_USERS_EXPECTED_OUTPUT);
    }

    @Test
    public void executeAndParse_parseSucceeds_returnsCorrectValue() throws Exception {
        assertThat((Integer) ShellCommand.builder(LIST_USERS_COMMAND)
                .executeAndParseOutput((output) -> 3)).isEqualTo(3);
    }

    @Test
    public void executeAndParse_parseFails_throwsException() {
        assertThrows(AdbException.class, () ->
                ShellCommand.builder(LIST_USERS_COMMAND)
                        .executeAndParseOutput((output) -> {
                            throw new IllegalStateException();
                        }));
    }

    @Test
    public void buildCommand_asRoot_commandStartsWithRootPrefix() {
        assertThat(ShellCommand.builder(LIST_USERS_COMMAND).asRoot(/* shouldRunAsRoot= */ true)
                .build()).startsWith("su root");
    }

    @Test
    public void buildCommand_notAsRoot_commandDoesNotStartWithRootPrefix() {
        assertThat(ShellCommand.builder(LIST_USERS_COMMAND).asRoot(/* shouldRunAsRoot= */ false)
                .build().startsWith("su root")).isFalse();
    }
}
