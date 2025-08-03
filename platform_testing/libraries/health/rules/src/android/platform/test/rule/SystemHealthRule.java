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
package android.platform.test.rule;

import android.content.Context;
import android.os.DropBoxManager;

import androidx.test.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Date;

/**
 * Test rule that diagnoses system health issues that happened during the test run. It pulls from
 * Dropbox service crashes that happened during the test run and attaches them to the "test failed"
 * exception.
 */
public class SystemHealthRule implements TestRule {
    long mTestStartTime;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mTestStartTime = System.currentTimeMillis();
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    tryRethrowingWithSystemHealth(t);
                    throw t;
                }
            }

            // If the test failed, and there was a system health issue while the test was
            // running, add this information to the diags.
            private void tryRethrowingWithSystemHealth(Throwable cause) {
                final String systemHealthMessage =
                        getSystemHealthMessage(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                mTestStartTime);
                if (systemHealthMessage != null) {
                    throw new AssertionError(
                            "There was a system health problem while test was running:\n"
                                    + systemHealthMessage,
                            cause);
                }
            }
        };
    }

    private static String truncateCrash(String text, int maxLines) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < maxLines && i < lines.length; i++) {
            ret.append(lines[i]);
            ret.append('\n');
        }
        if (lines.length > maxLines) {
            ret.append("... ");
            ret.append(lines.length - maxLines);
            ret.append(" more lines truncated ...\n");
        }
        return ret.toString();
    }

    private static String checkCrash(Context context, String label, long startTime) {
        DropBoxManager dropbox = context.getSystemService(DropBoxManager.class);
        Assert.assertNotNull("Unable access the DropBoxManager service", dropbox);

        long timestamp = startTime;
        DropBoxManager.Entry entry;
        StringBuilder errorDetails = new StringBuilder();
        while (null != (entry = dropbox.getNextEntry(label, timestamp))) {
            errorDetails.append("------------------------------\n");
            timestamp = entry.getTimeMillis();
            errorDetails.append(new Date(timestamp));
            errorDetails.append(": ");
            errorDetails.append(entry.getTag());
            errorDetails.append(": ");
            final String dropboxSnippet = entry.getText(4096);
            if (dropboxSnippet != null) errorDetails.append(truncateCrash(dropboxSnippet, 40));
            errorDetails.append("    ...\n");
            entry.close();
        }
        return errorDetails.length() != 0 ? errorDetails.toString() : null;
    }

    public static String getSystemHealthMessage(Context context, long startTime) {
        try {
            StringBuilder errors = new StringBuilder();

            final String[] labels = {
                "system_app_anr",
                "system_app_crash",
                "system_app_native_crash",
                "system_server_anr",
                "system_server_crash",
                "system_server_native_crash",
                "system_server_watchdog",
            };

            for (String label : labels) {
                final String crash = checkCrash(context, label, startTime);
                if (crash != null) errors.append(crash);
            }

            return errors.length() != 0
                    ? "Current time: " + new Date(System.currentTimeMillis()) + "\n" + errors
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void resetStartTime() {
        mTestStartTime = System.currentTimeMillis();
    }
}
