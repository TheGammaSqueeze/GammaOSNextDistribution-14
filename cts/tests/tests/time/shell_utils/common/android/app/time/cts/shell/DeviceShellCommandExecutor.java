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
package android.app.time.cts.shell;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** An abstraction for running shell commands. */
public abstract class DeviceShellCommandExecutor {

    /** Executes a command, logs and returns a string from stdout. */
    @NonNull
    public final String executeToString(@NonNull String command) throws Exception {
        byte[] bytes = executeToBytesInternal(command);
        String result = parseBytesAsString(bytes);
        log("Executed '" + command + "': Result='" + result + "'");
        return result;
    }

    /** Executes a command, logs and returns a trimmed string from stdout. */
    @NonNull
    public final String executeToTrimmedString(@NonNull String command) throws Exception {
        byte[] bytes = executeToBytesInternal(command);
        String result = parseBytesAsString(bytes).trim();
        log("Executed '" + command + "': Result='" + result + "'");
        return result;
    }

    /**
     * Executes a command, logs and returns a boolean based on whether "true" or "false" was
     * printed to from stdout.
     */
    @NonNull
    public final boolean executeToBoolean(@NonNull String command) throws Exception {
        byte[] bytes = executeToBytesInternal(command);
        boolean result = parseBytesAsBoolean(bytes);
        log("Executed '" + command + "': Result='" + result + "'");
        return result;
    }

    /** Executes a command, logs and returns a byte array from stdout. */
    @NonNull
    public final byte[] executeToBytes(@NonNull String command) throws Exception {
        byte[] bytes = executeToBytesInternal(command);
        log("Executed '" + command + "': Result='" + Arrays.toString(bytes) + "'");
        return bytes;
    }

    /** The raw execution method for subclasses to implement. */
    @NonNull
    protected abstract byte[] executeToBytesInternal(@NonNull String command) throws Exception;

    /** The logging method for subclasses to implement. */
    protected abstract void log(@NonNull String msg);

    /**
     * Ignoring leading/trainling whitespace, matches "true" or "false" or throws an exception.
     */
    public static boolean parseBytesAsBoolean(@NonNull byte[] result) {
        String resultString = parseBytesAsString(result).trim();
        if (resultString.equals("true")) {
            return true;
        } else if (resultString.equals("false")) {
            return false;
        } else {
            throw new AssertionError("Command returned unexpected result: " + resultString);
        }
    }

    /**
     * Converts command line bytes to a String.
     */
    protected static String parseBytesAsString(byte[] result) {
        return new String(result, 0, result.length, StandardCharsets.UTF_8);
    }
}
