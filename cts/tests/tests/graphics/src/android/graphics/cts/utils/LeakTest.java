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

package android.graphics.cts.utils;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.os.Debug;
import android.system.SystemCleaner;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LeakTest {
    private static File sProcSelfFd = new File("/proc/self/fd");

    private static void runGcAndFinalizersSync() {
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();

        final CountDownLatch fence = new CountDownLatch(2);
        SystemCleaner.cleaner().register(new Object() {
            @Override
            protected void finalize() throws Throwable {
                try {
                    fence.countDown();
                } finally {
                    super.finalize();
                }
            }
        }, fence::countDown);
        try {
            do {
                Runtime.getRuntime().gc();
                Runtime.getRuntime().runFinalization();
            } while (!fence.await(100, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static int getFdCount() {
        return sProcSelfFd.listFiles().length;
    }

    private static boolean isMaybeLeaking(int byteLimit,
            Debug.MemoryInfo start, Debug.MemoryInfo end) {
        Debug.getMemoryInfo(end);
        assertNotEquals(0, start.getTotalPss());
        assertNotEquals(0, end.getTotalPss());
        if (end.getTotalPss() - start.getTotalPss() > (byteLimit * .75)) {
            runGcAndFinalizersSync();
            Debug.getMemoryInfo(end);
            return end.getTotalPss() - start.getTotalPss() > byteLimit;
        }
        return false;
    }

    /**
     * Runs the given test repeatedly verifying that it doesn't leak memory or file descriptors
     */
    public static void runNotLeakingTest(Runnable test) {
        final int maxMemoryDeviation = 7000; // kB
        final int iterationCount = 2000;
        final int maxRerunAttempts = 3;

        Debug.MemoryInfo meminfoStart = new Debug.MemoryInfo();
        Debug.MemoryInfo meminfoEnd = new Debug.MemoryInfo();
        int fdCount = -1;
        // Do a warmup to reach steady-state memory usage
        for (int i = 0; i < 50; i++) {
            test.run();
        }

        int runAttempt = 1;

        // Now run the test
        for (int i = 0; i <= iterationCount; i++) {
            if (i == 0) {
                runGcAndFinalizersSync();
                Debug.getMemoryInfo(meminfoStart);
                fdCount = getFdCount();
            }

            test.run();

            if (i % 100 == 5 || i == iterationCount) {
                // If we're maybe leaking memory, try restarting from the beginning re-baselining
                // against the current usage. There may have been a fluke stair-step increase.
                // If however we've already retried from 0 several times, then it's time to
                // fail the test
                if (isMaybeLeaking(maxMemoryDeviation, meminfoStart, meminfoEnd)) {
                    if (runAttempt > maxRerunAttempts) {
                        fail(String.format(
                                "Potentially leaked memory after %d iterations:"
                                        + "expected=%d +/- %d; got=%d",
                                i, meminfoStart.getTotalPss(), maxMemoryDeviation,
                                meminfoEnd.getTotalPss()));
                    } else {
                        i = -1;
                        runAttempt++;
                    }
                }
                final int curFdCount = getFdCount();
                if (curFdCount - fdCount > 10) {
                    fail(String.format("FDs leaked. Expected=%d, current=%d, iteration=%d",
                            fdCount, curFdCount, i));
                }
            }
        }
        runGcAndFinalizersSync();
        final int curFdCount = getFdCount();
        if (curFdCount - fdCount > 10) {
            fail(String.format("FDs leaked. Expected=%d, current=%d", fdCount, curFdCount));
        }
    }
}
