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

import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Runs tests to validate the concurrency guarantees of VarHandle.
 *
 * The tests involve having a lot of tasks and significantly fewer threads. The tasks are stored on
 * a queue and each thread tries to grab a task from the queue using operations like
 * VarHandle.compareAndSet(). If the operation works as specified, then each task would only be
 * handled in a single thread, exactly once.
 *
 * The tasks just add atomically a specified integer to a total. If the total is different from the
 * expected one, then either some tasks were run multiple times (on multiple threads), or some task
 * were not run at all (skipped by all threads).
 */
public class Main {
    private static final VarHandle QA;
    static {
        QA = MethodHandles.arrayElementVarHandle(TestTask[].class);
    }

    private static final int TASK_COUNT = 10000;
    private static final int THREAD_COUNT = 20;
    /* Each test may need several retries before a concurrent failure is seen. In the past, for a
     * known bug, between 5 and 10 retries were sufficient. Use RETRIES to configure how many
     * iterations to retry for each test scenario. However, to avoid the test running for too long,
     * for example with gcstress, set a cap duration in MAX_RETRIES_DURATION. With this at least one
     * iteration would run, but there could be fewer retries if each of them takes too long. */
    private static final int RETRIES = 50;
    // b/235431387: timeout reduced from 1 minute
    private static final Duration MAX_RETRIES_DURATION = Duration.ofSeconds(15);

    public static void main(String[] args) throws Throwable {
        testConcurrentProcessing(new CompareAndExchangeRunnerFactory(), "compareAndExchange");
        testConcurrentProcessing(new CompareAndSetRunnerFactory(), "compareAndSet");
        testConcurrentProcessing(new WeakCompareAndSetRunnerFactory(), "weakCompareAndSet");
    }

    private static void testConcurrentProcessing(RunnerFactory factory, String testName)
            throws Throwable {
        final Duration startTs = Duration.ofNanos(System.nanoTime());
        final Duration endTs = startTs.plus(MAX_RETRIES_DURATION);
        for (int i = 0; i < RETRIES; ++i) {
            concurrentProcessingTestIteration(factory, i, testName);
            Duration now = Duration.ofNanos(System.nanoTime());
            if (0 < now.compareTo(endTs)) {
                break;
            }
        }
    }

    private static void concurrentProcessingTestIteration(
            RunnerFactory factory, int iteration, String testName) throws Throwable {
        final TestTask[] tasks = new TestTask[TASK_COUNT];
        final AtomicInteger result = new AtomicInteger();

        for (int i = 0; i < TASK_COUNT; ++i) {
            tasks[i] = new TestTask(Integer.valueOf(i + 1), result::addAndGet);
        }

        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; ++i) {
            threads[i] = factory.createRunner(tasks);
        }

        for (int i = 0; i < THREAD_COUNT; ++i) {
            threads[i].start();
        }

        for (int i = 0; i < THREAD_COUNT; ++i) {
            threads[i].join();
        }

        check(result.get(),
              TASK_COUNT * (TASK_COUNT + 1) / 2,
              testName + " test result not as expected",
              iteration);
    }

    /**
     * Processes the task queue until there are no tasks left.
     *
     * The actual task-grabbing mechanism is implemented in subclasses through grabTask().
     * This allows testing various mechanisms, like compareAndSet() and compareAndExchange().
     */
    private static abstract class TaskRunner extends Thread {

        protected final TestTask[] tasks;

        TaskRunner(TestTask[] tasks) {
            this.tasks = tasks;
        }

        @Override
        public void run() {
            int i = 0;
            while (i < TASK_COUNT) {
                TestTask t = (TestTask) QA.get(tasks, i);
                if (t == null) {
                    ++i;
                    continue;
                }
                if (!grabTask(t, i)) {
                    continue;
                }
                ++i;
                VarHandle.releaseFence();
                t.exec();
            }
        }

        /**
         * Grabs the next task from the queue in an atomic way.
         *
         * Once a task is retrieved successfully, the queue should no longer hold a reference to it.
         * This would be done, for example, by swapping the task with a null value.
         *
         * @param t The task to get from the queue
         * @param i The index where the task is found
         *
         * @return {@code true} if the task has been retrieved and is not available to any other
         * threads. Otherwise {@code false}. If {@code false} is returned, then either the task was
         * no longer present on the queue due to another thread grabbing it, or, in case of spurious
         * failure, the task is still available and no other thread managed to grab it.
         */
        protected abstract boolean grabTask(TestTask t, int i);
    }

    private static class TaskRunnerWithCompareAndExchange extends TaskRunner {
        TaskRunnerWithCompareAndExchange(TestTask[] tasks) {
            super(tasks);
        }

        @Override
        protected boolean grabTask(TestTask t, int i) {
            return (t == QA.compareAndExchange(tasks, i, t, null));
        }
    }

    private static class TaskRunnerWithCompareAndSet extends TaskRunner {
        TaskRunnerWithCompareAndSet(TestTask[] tasks) {
            super(tasks);
        }

        @Override
        protected boolean grabTask(TestTask t, int i) {
            return QA.compareAndSet(tasks, i, t, null);
        }
    }

    private static class TaskRunnerWithWeakCompareAndSet extends TaskRunner {
        TaskRunnerWithWeakCompareAndSet(TestTask[] tasks) {
            super(tasks);
        }

        @Override
        protected boolean grabTask(TestTask t, int i) {
            return QA.weakCompareAndSet(tasks, i, t, null);
        }
    }


    private interface RunnerFactory {
        Thread createRunner(TestTask[] tasks);
    }

    private static class CompareAndExchangeRunnerFactory implements RunnerFactory {
        @Override
        public Thread createRunner(TestTask[] tasks) {
            return new TaskRunnerWithCompareAndExchange(tasks);
        }
    }

    private static class CompareAndSetRunnerFactory implements RunnerFactory {
        @Override
        public Thread createRunner(TestTask[] tasks) {
            return new TaskRunnerWithCompareAndSet(tasks);
        }
    }

    private static class WeakCompareAndSetRunnerFactory implements RunnerFactory {
        @Override
        public Thread createRunner(TestTask[] tasks) {
            return new TaskRunnerWithWeakCompareAndSet(tasks);
        }
    }

    private static class TestTask {
        private final Integer ord;
        private final Consumer<Integer> action;

        TestTask(Integer ord, Consumer<Integer> action) {
            this.ord = ord;
            this.action = action;
        }

        public void exec() {
            action.accept(ord);
        }
    }

    private static void check(int actual, int expected, String msg, int iteration) {
        if (actual != expected) {
            System.err.println(String.format(
                    "[iteration %d] %s : %d != %d", iteration, msg, actual, expected));
            System.exit(1);
        }
    }
}
