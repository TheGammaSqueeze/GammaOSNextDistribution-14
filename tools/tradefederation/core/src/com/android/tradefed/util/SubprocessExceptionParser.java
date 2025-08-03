/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.util;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.sandbox.TradefedSandboxRunner;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helper to handle the exception output from standard Tradefed command runners. */
public class SubprocessExceptionParser {

    public static final String EVENT_THREAD_JOIN = "Event receiver thread did not complete.";

    /** Extract the file path of the serialized exception. */
    public static String getPathFromStderr(String stderr) {
        String patternString = String.format(".*%s.*", TradefedSandboxRunner.EXCEPTION_KEY);
        Pattern pattern = Pattern.compile(patternString);
        for (String line : stderr.split("\n")) {
            Matcher m = pattern.matcher(line);
            if (m.matches()) {
                try {
                    JSONObject json = new JSONObject(line);
                    String filePath = json.getString(TradefedSandboxRunner.EXCEPTION_KEY);
                    return filePath;
                } catch (JSONException e) {
                    // Ignore
                    CLog.w("Could not parse the stderr as a particular exception.");
                }
            } else {
                CLog.w("'%s' doesn't match pattern '%s'", line, patternString);
            }
        }
        return null;
    }

    /** Attempt to extract a proper exception from stderr, if not stick to RuntimeException. */
    public static void handleStderrException(CommandResult result)
            throws DeviceNotAvailableException {
        String stderr = result.getStderr();
        String filePath = getPathFromStderr(stderr);
        Integer exitCode = result.getExitCode();
        String message =
                String.format(
                        "Subprocess finished with error exit code: %s.\nStderr: %s",
                        exitCode, stderr);
        if (filePath != null) {
            try {
                File exception = new File(filePath);
                Throwable obj = (Throwable) SerializationUtil.deserialize(exception, true);
                if (obj instanceof DeviceNotAvailableException) {
                    throw (DeviceNotAvailableException) obj;
                }
                if (obj instanceof IHarnessException) {
                    throw new HarnessRuntimeException(message, (IHarnessException) obj);
                }
                throw new HarnessRuntimeException(message, obj, InfraErrorIdentifier.UNDETERMINED);
            } catch (IOException e) {
                // Ignore
                CLog.w(
                        "Could not parse the stderr as a particular exception. "
                                + "Using HarnessRuntimeException instead.");
            }
        }
        ErrorIdentifier id = InfraErrorIdentifier.UNDETERMINED;
        if (CommandStatus.TIMED_OUT.equals(result.getStatus())) {
            id = InfraErrorIdentifier.INVOCATION_TIMEOUT;
        }
        // If we reach here and it's an event issue, set the id
        if (stderr.startsWith(EVENT_THREAD_JOIN)) {
            id = InfraErrorIdentifier.EVENT_PROCESSING_TIMEOUT;
        }
        throw new HarnessRuntimeException(message, id);
    }
}
