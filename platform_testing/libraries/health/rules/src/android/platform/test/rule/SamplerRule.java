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

import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A rule that generates a file that helps diagnosing cases when the test process was terminated
 * because the test execution took too long, and tests that ran for too long even without being
 * terminated. If the process was terminated or the test was long, the test leaves an artifact with
 * stack traces of all threads, every second. This will help understanding where we stuck.
 */
public class SamplerRule extends TestWatcher {
    private static final String TAG = SamplerRule.class.getSimpleName();
    private static final int TOO_LONG_TEST_MS = 60000;
    private static boolean sEnabled;

    public static void enable(boolean enabled) {
        // The rule need to be explicitly enabled to avoid slowing down performance tests.
        sEnabled = enabled;
    }

    public static Thread startThread(Description description) {
        Thread thread =
                new Thread() {
                    @Override
                    public void run() {
                        // Write all-threads stack stace every second while the test is running.
                        // After the test finishes, delete that file. If the test process is
                        // terminated due to timeout, the trace file won't be deleted.
                        final File file = getFile();

                        final long startTime = SystemClock.elapsedRealtime();
                        try (final OutputStreamWriter outputStreamWriter =
                                new OutputStreamWriter(
                                        new BufferedOutputStream(new FileOutputStream(file)))) {
                            writeSampleEverySecond(outputStreamWriter);
                        } catch (IOException | InterruptedException e) {
                            // Simply suppressing the exceptions, nothing to do here.
                        } finally {
                            // If the process is not killed, then there was no test timeout, and
                            // we are not interested in the trace file, unless the test ran too
                            // long.
                            if (SystemClock.elapsedRealtime() - startTime < TOO_LONG_TEST_MS) {
                                file.delete();
                            } else {
                                Log.d(
                                        TAG,
                                        "Test execution is too long, generating sample file "
                                                + file);
                            }
                        }
                    }

                    private File getFile() {
                        final String strDate = new SimpleDateFormat("HH:mm:ss").format(new Date());

                        final String descStr = description.getTestClass().getSimpleName();
                        return ArtifactSaver.artifactFile(
                                "ThreadStackSamples-" + strDate + "-" + descStr + ".txt");
                    }

                    private void writeSampleEverySecond(OutputStreamWriter writer)
                            throws IOException, InterruptedException {
                        int count = 0;
                        while (true) {
                            Trace.beginSection("SamplerRule#writeSampleEverySecond");
                            try {
                                writer.write(
                                        "#"
                                                + (count++)
                                                + " =========================================\r\n");
                                for (StackTraceElement[] stack : getAllStackTraces().values()) {
                                    writer.write("---------------------\r\n");
                                    for (StackTraceElement frame : stack) {
                                        writer.write(frame.toString() + "\r\n");
                                    }
                                }
                                writer.flush();
                            } finally {
                                Trace.endSection();
                            }

                            sleep(1000);
                        }
                    }
                };

        thread.start();
        return thread;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (!sEnabled) return base;

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final Thread traceThread = startThread(description);
                try {
                    SamplerRule.super.apply(base, description).evaluate();
                } finally {
                    traceThread.interrupt();
                    traceThread.join();
                }
            }
        };
    }
}
