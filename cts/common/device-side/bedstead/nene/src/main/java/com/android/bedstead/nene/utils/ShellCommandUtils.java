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

import static android.os.Build.VERSION_CODES.S;

import static java.time.temporal.ChronoUnit.SECONDS;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.compatibility.common.util.FileUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Utilities for interacting with adb shell commands.
 *
 * <p>To enable command logging use the adb command `adb shell settings put global nene_log 1`.
 */
public final class ShellCommandUtils {

    private static final String LOG_TAG = ShellCommandUtils.class.getSimpleName();

    private static final int OUT_DESCRIPTOR_INDEX = 0;
    private static final int IN_DESCRIPTOR_INDEX = 1;
    private static final int ERR_DESCRIPTOR_INDEX = 2;

    private static final TestApis sTestApis = new TestApis();

    private static final boolean SHOULD_LOG = shouldLog();

    private static boolean shouldLog() {
        try {
            return Settings.Global.getInt(
                    sTestApis.context().instrumentedContext().getContentResolver(),
                    "nene_log") == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    private ShellCommandUtils() { }

    private static Boolean sRootAvailable = null;

    /**
     * Execute an adb shell command.
     *
     * <p>When running on S and above, any failures in executing the command will result in an
     * {@link AdbException} being thrown. On earlier versions of Android, an {@link AdbException}
     * will be thrown when the command returns no output (indicating that there is an error on
     * stderr which cannot be read by this method) but some failures will return seemingly correctly
     * but with an error in the returned string.
     *
     * <p>Callers should be careful to check the command's output is valid.
     */
    static String executeCommand(String command) throws AdbException {
        return executeCommand(command, /* allowEmptyOutput=*/ false, /* stdInBytes= */ null);
    }

    /**
     * Wraps executeShellCommandRwe to suppress NewApi warning for this method in isolation.
     *
     * This method was changed from TestApi -> public for API 34, so it's safe to call back to
     * API 29, but the NewApi warning doesn't understand this.
     */
    @SuppressWarnings("NewApi") // executeShellCommandRwe was @TestApi back to API 29, now public
    private static ParcelFileDescriptor[] executeShellCommandRwe(String command) {
        return uiAutomation().executeShellCommandRwe(command);
    }

    /**
     * Execute a shell command and receive a stream of lines.
     *
     * <p>Note that this will not deal with errors in the output.
     *
     * <p>Make sure you close the returned {@link StreamingShellOutput} after reading.
     */
    public static StreamingShellOutput executeCommandForStream(String command, byte[] stdInBytes)
            throws AdbException {
        try {
            ParcelFileDescriptor[] fds = executeShellCommandRwe(command);
            ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdErr = fds[ERR_DESCRIPTOR_INDEX];

            writeStdInAndClose(fdIn, stdInBytes);
            fdErr.close();

            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdOut);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            return new StreamingShellOutput(fis, reader.lines());
        } catch (IOException e) {
            throw new AdbException("Error executing command", command, e);
        }
    }

    static String executeCommand(String command, boolean allowEmptyOutput, byte[] stdInBytes)
            throws AdbException {
        logCommand(command, allowEmptyOutput, stdInBytes);

        if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
            return executeCommandPreS(command, allowEmptyOutput, stdInBytes);
        }

        try {
            ParcelFileDescriptor[] fds = executeShellCommandRwe(command);
            ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdErr = fds[ERR_DESCRIPTOR_INDEX];

            writeStdInAndClose(fdIn, stdInBytes);

            String out = new String(readStreamAndClose(fdOut));
            String err = new String(readStreamAndClose(fdErr));

            if (!err.isEmpty()) {
                throw new AdbException("Error executing command", command, out, err);
            }

            if (SHOULD_LOG) {
                Log.d(LOG_TAG, "Command result: " + out);
            }

            return out;
        } catch (IOException e) {
            throw new AdbException("Error executing command", command, e);
        }
    }

    static byte[] executeCommandForBytes(String command) throws AdbException {
        return executeCommandForBytes(command, /* stdInBytes= */ null);
    }

    static byte[] executeCommandForBytes(String command, byte[] stdInBytes) throws AdbException {
        logCommand(command, /* allowEmptyOutput= */ false, stdInBytes);

        if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
            return executeCommandForBytesPreS(command, stdInBytes);
        }

        // TODO(scottjonathan): Add argument to force errors to stderr
        try {

            ParcelFileDescriptor[] fds = executeShellCommandRwe(command);
            ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdErr = fds[ERR_DESCRIPTOR_INDEX];

            writeStdInAndClose(fdIn, stdInBytes);

            byte[] out = readStreamAndClose(fdOut);
            String err = new String(readStreamAndClose(fdErr));

            if (!err.isEmpty()) {
                throw new AdbException("Error executing command", command, err);
            }

            return out;
        } catch (IOException e) {
            throw new AdbException("Error executing command", command, e);
        }
    }

    private static void logCommand(String command, boolean allowEmptyOutput, byte[] stdInBytes) {
        if (!SHOULD_LOG) {
            return;
        }

        StringBuilder logBuilder = new StringBuilder("Executing shell command ");
        logBuilder.append(command);
        if (allowEmptyOutput) {
            logBuilder.append(" (allow empty output)");
        }
        if (stdInBytes != null) {
            logBuilder.append(" (writing to stdIn)");
        }
        Log.d(LOG_TAG, logBuilder.toString());
    }

    /**
     * Execute an adb shell command and check that the output meets a given criteria.
     *
     * <p>On S and above, any output printed to standard error will result in an exception and the
     * {@code outputSuccessChecker} not being called. Empty output will still be processed.
     *
     * <p>Prior to S, if there is no output on standard out, regardless of if there is output on
     * standard error, {@code outputSuccessChecker} will not be called.
     *
     * <p>{@code outputSuccessChecker} should return {@code true} if the output indicates the
     * command executed successfully.
     */
    static String executeCommandAndValidateOutput(
            String command, Function<String, Boolean> outputSuccessChecker) throws AdbException {
        return executeCommandAndValidateOutput(command,
                /* allowEmptyOutput= */ false,
                /* stdInBytes= */ null,
                outputSuccessChecker);
    }

    static String executeCommandAndValidateOutput(
            String command,
            boolean allowEmptyOutput,
            byte[] stdInBytes,
            Function<String, Boolean> outputSuccessChecker) throws AdbException {
        String output = executeCommand(command, allowEmptyOutput, stdInBytes);
        if (!outputSuccessChecker.apply(output)) {
            throw new AdbException("Command did not meet success criteria", command, output);
        }
        return output;
    }

    /**
     * Return {@code true} if {@code output} starts with "success", case insensitive.
     */
    public static boolean startsWithSuccess(String output) {
        return output.toUpperCase().startsWith("SUCCESS");
    }

    /**
     * Return {@code true} if {@code output} does not start with "error", case insensitive.
     */
    public static boolean doesNotStartWithError(String output) {
        return !output.toUpperCase().startsWith("ERROR");
    }

    @SuppressWarnings("NewApi")
    private static String executeCommandPreS(
            String command, boolean allowEmptyOutput, byte[] stdIn) throws AdbException {
        ParcelFileDescriptor[] fds = uiAutomation().executeShellCommandRw(command);
        ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
        ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];

        try {
            writeStdInAndClose(fdIn, stdIn);

            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdOut)) {
                String out = new String(FileUtils.readInputStreamFully(fis));

                if (!allowEmptyOutput && out.isEmpty()) {
                    throw new AdbException(
                            "No output from command. There's likely an error on stderr",
                            command, out);
                }

                if (SHOULD_LOG) {
                    Log.d(LOG_TAG, "Command result: " + out);
                }

                return out;
            }
        } catch (IOException e) {
            throw new AdbException(
                    "Error reading command output", command, e);
        }
    }

    // This is warned for executeShellCommandRw which did exist as TestApi
    @SuppressWarnings("NewApi")
    private static byte[] executeCommandForBytesPreS(
            String command, byte[] stdInBytes) throws AdbException {
        ParcelFileDescriptor[] fds = uiAutomation().executeShellCommandRw(command);
        ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
        ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];

        try {
            writeStdInAndClose(fdIn, stdInBytes);

            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdOut)) {
                return FileUtils.readInputStreamFully(fis);
            }
        } catch (IOException e) {
            throw new AdbException(
                    "Error reading command output", command, e);
        }
    }

    private static void writeStdInAndClose(ParcelFileDescriptor fdIn, byte[] stdInBytes)
            throws IOException {
        if (stdInBytes != null) {
            try (FileOutputStream fos = new ParcelFileDescriptor.AutoCloseOutputStream(fdIn)) {
                fos.write(stdInBytes);
            }
        } else {
            fdIn.close();
        }
    }

    private static byte[] readStreamAndClose(ParcelFileDescriptor fd) throws IOException {
        try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            return FileUtils.readInputStreamFully(fis);
        }
    }

    /**
     * Get a {@link Instrumentation}.
     */
    public static Instrumentation instrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    /**
     * Get a {@link UiAutomation}.
     */
    public static UiAutomation uiAutomation() {
        return instrumentation().getUiAutomation();
    }

    /**
     * Check if the device can run commands as root.
     */
    public static boolean isRootAvailable() {
        if (sRootAvailable != null) {
            return sRootAvailable;
        }

        try {
            // We run a basic command to check if the device can run it as root.
            //TODO(b/301478821): Remove the timeout once b/303377922 is fixed.
            String output = ShellCommand.builder("echo hello").asRoot(true)
                    .withTimeout(Duration.of(1, SECONDS)).execute();
            if (output.contains("hello")) {
                sRootAvailable = true;
            }
        } catch (AdbException ignored) {
        }

        if (sRootAvailable == null) {
            Log.i(LOG_TAG,
                    "Unable to run the test as root as the device does not allow that. "
                            + "The device is of type: " + Build.TYPE);
            sRootAvailable = false;
        }

        return sRootAvailable;
    }

    /** Wrapper around {@link Stream} of lines output from a shell command. */
    public static final class StreamingShellOutput implements AutoCloseable {

        private final FileInputStream mFileInputStream;
        private final Stream<String> mStream;

        StreamingShellOutput(FileInputStream fileInputStream, Stream<String> stream) {
            mFileInputStream = fileInputStream;
            mStream = stream;
        }

        public Stream<String> stream() {
            return mStream;
        }


        @Override
        public void close() throws IOException {
            mFileInputStream.close();
            mStream.close();
        }
    }
}
