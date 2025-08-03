/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Main implements Runnable {
    static final int NUMBER_OF_THREADS = 5;
    static volatile int ops_per_thread = 1000;
    static AtomicInteger operations_completed = new AtomicInteger(0);
    static int[] progress = new int[NUMBER_OF_THREADS];
    static AtomicInteger totalStackFrames = new AtomicInteger(0);
    static final boolean printStats = false;  // True causes test to fail.
    int index;

    Main(int i) {
        index = i;
    }

    public static void main(String[] args) throws Exception {
        final Thread[] threads = new Thread[NUMBER_OF_THREADS];
        Thread watchdog = new Thread() {
            public void run() {
                try {
                    if (printStats) {
                        System.out.println("ops_per_thread = " + ops_per_thread);
                    }
                    Thread.sleep(10_000);
                    if (printStats) {
                        System.out.println("Ops completed after 10 seconds: " +
                                operations_completed.get());
                    }
                    if (operations_completed.get() < NUMBER_OF_THREADS * ops_per_thread / 2) {
                        // We're in some sort of "go slow" mode, probably gcstress. Finish early.
                        ops_per_thread /= 10;
                    }
                    if (printStats) {
                        System.out.println("ops_per_thread = " + ops_per_thread);
                    }
                    Thread.sleep(200_000);
                    System.out.print("Watchdog timed out: ");
                    for (int i = 0; i < NUMBER_OF_THREADS; ++i) {
                        System.out.print(progress[i] + ", ");
                    }
                    System.out.println("");
                    System.err.println("Watchdog thread timed out");
                    System.exit(1);
                } catch (InterruptedException e) {}
            }
        };
        watchdog.start();
        long start_millis = System.currentTimeMillis();
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(new Main(t));
            threads[t].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        if (printStats) {
            long elapsed_millis = System.currentTimeMillis() - start_millis;
            System.out.println("Captured " + totalStackFrames + " stack frames in " +
                    elapsed_millis + "msecs");
        }
        System.out.println("All joined");
        // Do this test after the other part to leave some time for the heap task daemon to start
        // up.
        test_getStackTraces();
        watchdog.interrupt();
        System.out.println("Finishing");
    }

    static Thread getHeapTaskDaemon() throws Exception {
        Field f = ThreadGroup.class.getDeclaredField("systemThreadGroup");
        f.setAccessible(true);
        ThreadGroup systemThreadGroup = (ThreadGroup) f.get(null);

        while (true) {
            int activeCount = systemThreadGroup.activeCount();
            Thread[] array = new Thread[activeCount];
            systemThreadGroup.enumerate(array);
            for (Thread thread : array) {
                if (thread.getName().equals("HeapTaskDaemon") &&
                    thread.getState() != Thread.State.NEW) {
                    return thread;
                }
            }
            // Yield to eventually get the daemon started.
            Thread.sleep(10);
        }
    }

    static void test_getStackTraces() throws Exception {
        Thread heapDaemon = getHeapTaskDaemon();

        // Force a GC to ensure the daemon truly started.
        Runtime.getRuntime().gc();
        // Check all the current threads for positive IDs.
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> pair : map.entrySet()) {
            Thread thread = pair.getKey();
            // Expect empty stack trace since we do not support suspending the GC thread for
            // obtaining stack traces. See b/28261069.
            if (thread == heapDaemon) {
                System.out.println(thread.getName() + " depth " + pair.getValue().length); 
            }
        }
    }

    public void test_getId() {
        if (Thread.currentThread().getId() <= 0) {
            System.out.println("current thread's ID is not positive");
        }
        // Check all the current threads for positive IDs.
        Map<Thread, StackTraceElement[]> stMap = Thread.getAllStackTraces();
        for (Thread thread : stMap.keySet()) {
            if (thread.getId() <= 0) {
                System.out.println("thread's ID is not positive: " + thread.getName());
            }
            totalStackFrames.addAndGet(stMap.get(thread).length);
        }
    }

    public void run() {
        for (int i = 1; i <= ops_per_thread; ++i) {
            test_getId();
            operations_completed.addAndGet(1);
            progress[index] = i;
        }
        System.out.println("Thread finished");
    }
}
