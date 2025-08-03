/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.uiautomation.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * Tests for the UiAutomation executeShellCommand*** APIs.
 *
 * For which shell commands are available, see:
 * https://android.googlesource.com/platform/system/core/+/master/shell_and_utilities/README.md
 */
@SuppressWarnings("TextBlockMigration") // suppress Intellij suggestion not supported by build
@RunWith(AndroidJUnit4.class)
public class UiAutomationShellTest {
    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private void writeString(
            @NonNull ParcelFileDescriptor descriptor,
            @NonNull String string
    ) throws IOException {
        try (AutoCloseOutputStream stream = new AutoCloseOutputStream(descriptor)) {
            stream.write(string.getBytes());
        }
    }
    private String readString(@NonNull ParcelFileDescriptor descriptor) throws IOException {
        try (AutoCloseInputStream stream = new AutoCloseInputStream(descriptor)) {
            return new String(stream.readAllBytes(), Charset.defaultCharset());
        }
    }

    private void validateShellResults(
            @NonNull ParcelFileDescriptor[] fds,
            @NonNull String stdin,
            @NonNull String expectedStdout,
            @Nullable Consumer<String> stderrValidator
    ) throws IOException {
        assertThat(fds.length).isAtLeast(1);
        assertThat(fds.length).isAtMost(3);
        if (fds.length > 1) {
            writeString(fds[1], stdin);
        }

        assertThat(readString(fds[0])).isEqualTo(expectedStdout);
        if (fds.length > 2) {
            String stderr = readString(fds[2]);
            if (stderrValidator == null) {
                assertTrue("Expected no stderr, observed stderr" + stderr, stderr.isBlank());
            } else {
                stderrValidator.accept(stderr);
            }
        }
    }

    private void validateShellCommand(
            @NonNull String shellCommand,
            @NonNull String stdin,
            @NonNull String expectedStdout,
            @Nullable Consumer<String> stderrValidator
    ) throws IOException {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        if (stdin.equals("")) {
            // skip validating command on variant which doesn't support stdin if stdin is used
            validateShellResults(new ParcelFileDescriptor[]{
                    uiAutomation.executeShellCommand(shellCommand)
            }, stdin, expectedStdout, stderrValidator);
        }
        validateShellResults(uiAutomation.executeShellCommandRw(shellCommand), stdin,
                expectedStdout, stderrValidator);
        validateShellResults(uiAutomation.executeShellCommandRwe(shellCommand), stdin,
                expectedStdout, stderrValidator);
    }


    @Test
    public void testExecuteShellCommand_echo() throws IOException {
        validateShellCommand(
                /* shellCommand = */ "echo foo",
                /* stdin = */ "",
                /* expectedStdout = */ "foo\n",
                /* stderrValidator = */ stderr -> assertThat(stderr).isEmpty()
        );
    }

    @Test
    public void testExecuteShellCommand_stdin() throws IOException {
        validateShellCommand(
                /* shellCommand = */ "/system/bin/sh",
                /* stdin = */ "echo foo",
                /* expectedStdout = */ "foo\n",
                /* stderrValidator = */ stderr -> assertThat(stderr).isEmpty()
        );
    }

    @Test
    public void testExecuteShellCommand_stderr() throws IOException {
        // couple simple ways to trigger stderr
        validateShellCommand(
                /* shellCommand = */ "ls /notapath",
                /* stdin = */ "",
                /* expectedStdout = */ "",
                /* stderrValidator = */ stderr -> {
                    assertThat(stderr).contains("No such file or directory");
                }
        );
    }

    @Test
    public void testExecuteShellCommand_stderrInvalidCommand() throws IOException {
        // This is just a simple way to trigger stderr
        // Note - we can't invoke a missing command directly, as this will cause an exception in the
        // remote process, which isn't conveyed back to test but is visible to logcat
        validateShellCommand(
                /* shellCommand = */ "/system/bin/sh",
                /* stdin = */ "invalidCommandDoesntExist",
                /* expectedStdout = */ "",
                /* stderrValidator = */ stderr -> {
                    assertThat(stderr).contains("invalidCommandDoesntExist");
                    assertThat(stderr).contains("not found");
                }
        );
    }

    @Test
    public void testExecuteShellCommand_script() throws IOException {
        // validates a script with stdout redirect in it
        // (which would not work if passed directly as the shell command)
        validateShellCommand(
                /* shellCommand = */ "/system/bin/sh",
                /* stdin = */ "rm -f /data/local/tmp/foofile # ensure file not present\n"
                        + "echo testscriptoutput > /data/local/tmp/testExecuteShellCommand_script\n"
                        + "cat /data/local/tmp/testExecuteShellCommand_script\n"
                        + "rm /data/local/tmp/testExecuteShellCommand_script\n",
                /* expectedStdout = */ "testscriptoutput\n",
                /* stderrValidator = */ stderr -> assertThat(stderr).isEmpty()
        );
    }

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
