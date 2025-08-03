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

import android.system.SystemCleaner;
import dalvik.system.VMRuntime;
import java.lang.ref.Cleaner;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Test SystemCleaner with a bad cleaning action.
 *
 * This test is inherently very slightly flaky. It assumes that the system will schedule the
 * finalizer daemon and finalizer watchdog daemon soon and often enough to reach the timeout and
 * throw the fatal exception before we time out. Since we build in a 20 second buffer, failures
 * should be very rare.
 */
public class Main {
    static volatile Thread throwingThread;
    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (t != throwingThread) {
                  System.out.println("Exception from wrong thread");
                }
                if (!(e instanceof AssertionError)) {
                  System.out.println("Unexpected uncaught exception");
                }
                System.out.println("Handling uncaught exception");
                System.exit(2);
            }
        });

        CountDownLatch cleanerWait1 = new CountDownLatch(1);
        registerBadCleaner(Cleaner.create(), cleanerWait1);
        gcRepeatedly();

        // Now wait for the finalizer to start running. Give it a minute.
        cleanerWait1.await(1, MINUTES);

        // Give the cleaner Runnable a chance to finish running. That should do nothing,
        // since exceptions from standard Cleaners are ignored.
        snooze(10000);

        System.out.println("Good: Survived first exception");
        // Now repeat with SystemCleaner.
        CountDownLatch cleanerWait2 = new CountDownLatch(1);
        registerBadCleaner(SystemCleaner.cleaner(), cleanerWait2);
        gcRepeatedly();
        cleanerWait2.await(1, MINUTES);

        // Now fall asleep. The timeout is large enough that we expect the
        // FinalizerDaemon to have been killed by the exception.
        // The timeout is also large enough to cover the extra 5 seconds we wait
        // to dump on termination, plus potentially substantial gcstress overhead.
        // Note: the timeout is here (instead of an infinite sleep) to protect the test
        //       environment (e.g., in case this is run without a timeout wrapper).
        snooze(20_000);

        // We should not get here.
        System.out.println("Bad: Unexpectedly survived second exception");
        System.exit(0);
    }

    private static void gcRepeatedly() {
        // Should have at least two iterations to trigger finalization, but just to make sure run
        // some more.
        for (int i = 0; i < 5; i++) {
            Runtime.getRuntime().gc();
        }
    }

    private static void registerBadCleaner(Cleaner cleaner, CountDownLatch cleanerWait) {
        Object obj = new Object();
        cleaner.register(obj, () -> throwingCleanup(cleanerWait));
        System.out.println("About to null reference.");
        obj = null;  // Not that this would make a difference, could be eliminated earlier.
    }

    public static void snooze(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            System.out.println("Unexpected interrupt");
        }
    }

    private static void throwingCleanup(CountDownLatch cleanerWait) {
        throwingThread = Thread.currentThread();
        cleanerWait.countDown();
        System.out.println("Cleaner started and sleeping briefly...");
        snooze(100);
        throw new AssertionError("Process-killing exception");
    }
}
