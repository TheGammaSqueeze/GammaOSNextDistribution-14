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

package android.multiuser.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

//TODO: move to common code
/**
 * Rule used to run and log Shell commands (especially on failure).
 */
public final class LogShellCommandRule implements TestRule {

    private static final String TAG = LogShellCommandRule.class.getSimpleName();

    private final List<String> mCommands = new ArrayList<>();

    /**
     * Adds a Shell command to be logged if the test case fails (or if explicitly asked by
     * {@link #runAndLogAll(String)})
     */
    public void addCommand(String commandFormat, Object... commandArgs) {
        String command = String.format(commandFormat, commandArgs);
        Log.d(TAG, "Adding '" + command + "'");
        mCommands.add(command);
    }

    /**
     * Runs and logs the specific Shell command.
     */
    public void runAndLog(String reason, String command) {
        Log.i(TAG, "Running '" + command + "'. Reason: " + reason);
        String output = runShellCommand(command);
        Log.w(TAG, "Output: " + output);
    }

    /**
     * Run all commands added by {@link #addCommand(String, Object...)}
     */
    public void runAndLogAll(String reason) {
        if (mCommands.isEmpty()) {
            return;
        }
        Log.w(TAG, "Running " + mCommands.size() + " commands(s). Reason: " + reason);
        mCommands.forEach(c -> runAndLog(reason, c));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    runAndLogAll("failure of " + description.getMethodName());
                    throw t;
                }
            }
        };
    }
}
