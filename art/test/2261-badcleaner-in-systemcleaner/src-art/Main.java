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
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Test SystemCleaner with a bad cleaning action.
 *
 * This test is inherently very slightly flaky. It assumes that the system will schedule the
 * finalizer daemon and finalizer watchdog daemon soon and often enough to reach the timeout and
 * throw the fatal exception before we time out. Since we build in a 100 second buffer, failures
 * should be very rare.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        CountDownLatch cleanerWait = new CountDownLatch(1);

        registerBadCleaner(cleanerWait);

        // Should have at least two iterations to trigger finalization, but just to make sure run
        // some more.
        for (int i = 0; i < 5; i++) {
            Runtime.getRuntime().gc();
        }

        // Now wait for the finalizer to start running. Give it a minute.
        cleanerWait.await(1, MINUTES);

        // Now fall asleep with a timeout. The timeout is large enough that we expect the
        // finalizer daemon to have killed the process before the deadline elapses.
        // The timeout is also large enough to cover the extra 5 seconds we wait
        // to dump threads, plus potentially substantial gcstress overhead.
        // Note: the timeout is here (instead of an infinite sleep) to protect the test
        //       environment (e.g., in case this is run without a timeout wrapper).
        final long timeout = 100 * 1000 + VMRuntime.getRuntime().getFinalizerTimeoutMs();
        long remainingWait = timeout;
        final long waitStart = System.currentTimeMillis();
        while (remainingWait > 0) {
            synchronized (args) {  // Just use an already existing object for simplicity...
                try {
                    args.wait(remainingWait);
                } catch (Exception e) {
                    System.out.println("UNEXPECTED EXCEPTION");
                }
            }
            remainingWait = timeout - (System.currentTimeMillis() - waitStart);
        }

        // We should not get here.
        System.out.println("UNREACHABLE");
        System.exit(0);
    }

    private static void registerBadCleaner(CountDownLatch cleanerWait) {
        Object obj = new Object();
        SystemCleaner.cleaner().register(obj, () -> neverEndingCleanup(cleanerWait));

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

    private static void neverEndingCleanup(CountDownLatch cleanerWait) {
        cleanerWait.countDown();

        System.out.println("Cleaner started and sleeping briefly...");

        long start, end;
        start = System.nanoTime();
        snooze(2000);
        end = System.nanoTime();
        System.out.println("Cleaner done snoozing.");

        System.out.println("Cleaner sleeping forever now.");
        while (true) {
            snooze(10000);
        }
    }
}
