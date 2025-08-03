/*
 * Copyright (C) 2007 The Android Open Source Project
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

import dalvik.system.VMRuntime;
import java.util.concurrent.CountDownLatch;
import libcore.util.NativeAllocationRegistry;
import java.util.concurrent.TimeoutException;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Test a class with a bad finalizer.
 *
 * This test is inherently flaky. It assumes that the system will schedule the finalizer daemon
 * and finalizer watchdog daemon enough to reach the timeout and throwing the fatal exception.
 * This uses somewhat simpler logic than 2041-bad-cleaner, since the handshake implemented there
 * is harder to replicate here. We bump up the timeout below a bit to compensate.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.loadLibrary(args[0]);
        ClassLoader cl = Main.class.getClassLoader();
        NativeAllocationRegistry registry =
            NativeAllocationRegistry.createNonmalloced(cl, getBadFreeFunction(), 666);
        // Replace the global uncaught exception handler, so the exception shows up on stdout.
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable e) {
                if (e instanceof TimeoutException) {
                    System.out.println("TimeoutException on "
                        + thread.getName() + ":" + e.getMessage());
                } else {
                    System.out.println("Unexpected exception " + e);
                }
                System.exit(2);
            }
        });
        // A separate method to ensure no dex register keeps the object alive.
        createBadCleanable(registry);

        // Should have at least two iterations to trigger finalization, but just to make sure run
        // some more.
        for (int i = 0; i < 5; i++) {
            Runtime.getRuntime().gc();
        }

        // Now fall asleep with a timeout. The timeout is large enough that we expect the
        // finalizer daemon to have killed the process before the deadline elapses.
        // The timeout is also large enough to cover the extra 5 seconds we wait
        // to dump threads, plus potentially substantial gcstress overhead.
        // The RQ timeout is currently effectively 5 * the finalizer timeout.
        // Note: the timeout is here (instead of an infinite sleep) to protect the test
        //       environment (e.g., in case this is run without a timeout wrapper).
        final long timeout = 150 * 1000 + 5 * VMRuntime.getRuntime().getFinalizerTimeoutMs();
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

    private static void createBadCleanable(NativeAllocationRegistry registry) {
        Object badCleanable = new Object();
        long nativeObj = getNativeObj();
        registry.registerNativeAllocation(badCleanable, nativeObj);

        System.out.println("About to null reference.");
        badCleanable = null;  // Not that this would make a difference, could be eliminated earlier.
    }

    private static native long getNativeObj();
    private static native long getBadFreeFunction();

}
