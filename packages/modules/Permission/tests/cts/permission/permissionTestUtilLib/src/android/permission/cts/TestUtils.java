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

package android.permission.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import android.app.UiAutomation;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;

/** Common test utilities */
public class TestUtils {
    private static final String LOG_TAG = TestUtils.class.getSimpleName();

    /**
     * A {@link java.util.concurrent.Callable} that can throw a {@link Throwable}
     */
    public interface ThrowingCallable<T> {
        T call() throws Throwable;
    }

    /**
     * A {@link Runnable} that can throw a {@link Throwable}
     */
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Make sure that a {@link ThrowingRunnable} finishes without throwing a {@link
     * Exception}.
     *
     * @param r       The {@link ThrowingRunnable} to run.
     * @param timeout the maximum time to wait
     */
    public static void ensure(@NonNull ThrowingRunnable r, long timeout) throws Throwable {
        ensure(() -> {
            r.run();
            return 0;
        }, timeout);
    }

    /**
     * Make sure that a {@link ThrowingCallable} finishes without throwing a {@link
     * Exception}.
     *
     * @param r       The {@link ThrowingCallable} to run.
     * @param timeout the maximum time to wait
     * @return the return value from the callable
     * @throws NullPointerException If the return value never becomes non-null
     */
    public static <T> T ensure(@NonNull ThrowingCallable<T> r, long timeout) throws Throwable {
        long start = System.currentTimeMillis();

        while (true) {
            T res = r.call();
            if (res == null) {
                throw new NullPointerException("No result");
            }

            if (System.currentTimeMillis() - start < timeout) {
                Thread.sleep(500);
            } else {
                return res;
            }
        }
    }

    /**
     * Make sure that a {@link ThrowingRunnable} eventually finishes without throwing a {@link
     * Exception}.
     *
     * @param r       The {@link ThrowingRunnable} to run.
     * @param timeout the maximum time to wait
     */
    public static void eventually(@NonNull ThrowingRunnable r, long timeout) throws Throwable {
        eventually(() -> {
            r.run();
            return 0;
        }, timeout);
    }

    /**
     * Make sure that a {@link ThrowingCallable} eventually finishes without throwing a {@link
     * Exception}.
     *
     * @param r       The {@link ThrowingCallable} to run.
     * @param timeout the maximum time to wait
     * @return the return value from the callable
     * @throws NullPointerException If the return value never becomes non-null
     */
    public static <T> T eventually(@NonNull ThrowingCallable<T> r, long timeout) throws Throwable {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                T res = r.call();
                if (res == null) {
                    throw new NullPointerException("No result");
                }

                return res;
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start < timeout) {
                    Log.d(LOG_TAG, "Ignoring exception, occurred within valid wait time", e);

                    Thread.sleep(500);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Run the job and then wait for completion
     */
    public static void runJobAndWaitUntilCompleted(
            String packageName,
            int jobId, long timeout) {
        runJobAndWaitUntilCompleted(packageName, jobId, timeout,
                InstrumentationRegistry.getInstrumentation().getUiAutomation());
    }

    /**
     * Run the job and then wait for completion
     */
    public static void runJobAndWaitUntilCompleted(
            String packageName,
            int jobId,
            long timeout,
            UiAutomation automation) {
        String runJobCmd = "cmd jobscheduler run -u " + Process.myUserHandle().getIdentifier()
                + " -f " + packageName + " " + jobId;
        try {
            String result = runShellCommand(automation, runJobCmd);
            Log.v(LOG_TAG, "jobscheduler run job command output: " + result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        // waiting state is expected after completion for the periodic jobs.
        awaitJobUntilRequestedState(packageName, jobId, timeout, automation, "waiting");
    }

    public static void awaitJobUntilRequestedState(
            String packageName,
            int jobId,
            long timeout,
            UiAutomation automation,
            String requestedState) {
        String statusCmd = "cmd jobscheduler get-job-state -u "
                + Process.myUserHandle().getIdentifier() + " " + packageName + " " + jobId;
        try {
            eventually(() -> {
                String jobState = runShellCommand(automation, statusCmd).trim();
                Assert.assertTrue("The job doesn't have requested state " + requestedState
                        + " yet, current state: " + jobState, jobState.startsWith(requestedState));
            }, timeout);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void awaitJobUntilRequestedState(
            String packageName,
            int jobId,
            long timeout,
            UiAutomation automation,
            String requestedState1,
            String requestedState2) {
        String statusCmd = "cmd jobscheduler get-job-state -u "
                + Process.myUserHandle().getIdentifier() + " " + packageName + " " + jobId;
        try {
            eventually(() -> {
                String jobState = runShellCommand(automation, statusCmd).trim();
                boolean jobInEitherRequestedState = jobState.startsWith(requestedState1)
                        || jobState.startsWith(requestedState2);
                Assert.assertTrue("The job doesn't have requested state "
                        + "(" + requestedState1 + " or " + requestedState2 + ")"
                        + " yet, current state: " + jobState, jobInEitherRequestedState);
            }, timeout);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
