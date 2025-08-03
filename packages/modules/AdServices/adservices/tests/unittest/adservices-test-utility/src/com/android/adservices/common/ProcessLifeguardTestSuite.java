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
package com.android.adservices.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.android.adservices.common.AbstractProcessLifeguardRule.UncaughtBackgroundException;
import com.android.adservices.common.ProcessLifeguardTestSuite.Test1ThrowsInBg;
import com.android.adservices.common.ProcessLifeguardTestSuite.Test2RuleCatchesIt;
import com.android.adservices.common.ProcessLifeguardTestSuite.Test3MakesSureProcessDidntCrash;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Suite;
import org.junit.runners.model.Statement;

// TODO(b/302757068): these tests doesn't seem to working anymore - if the rule is created with
// IGNORE mode, they still pass

/**
 * Test suite that asserts an exception thrown in the background in one thread doesn't crash another
 * test.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    Test1ThrowsInBg.class,
    Test2RuleCatchesIt.class,
    Test3MakesSureProcessDidntCrash.class
})
public final class ProcessLifeguardTestSuite {

    private static final String TAG = ProcessLifeguardTestSuite.class.getSimpleName();

    private static Handler sHandler;
    private static HandlerThread sHandlerThread =
            new HandlerThread("ProcessLifeguardTestSuiteThread");

    /** Called before first test of the suite. */
    @BeforeClass
    public static void startHandler() {
        sHandlerThread = new HandlerThread("ProcessLifeguardTestSuiteThread");
        sHandlerThread.start();
        sHandler = new Handler(sHandlerThread.getLooper());

        Log.i(TAG, "Started" + sHandler + " on " + sHandlerThread);
    }

    /** Called after last test of the suite. */
    @AfterClass
    public static void finishHandler() {
        if (sHandlerThread == null) {
            Log.e(TAG, "No sHandlerThread at the end of the suite");
            return;
        }
        sHandlerThread.quitSafely();
    }

    private static final int NAP_TIME_MS = 1_000;
    private static final int SELF_DESTROY_TIMEOUT_MS = 200;
    private static final int WAITING_TIMEOUT_MS = SELF_DESTROY_TIMEOUT_MS * 2 + NAP_TIME_MS;

    private static final SecurityException SELF_DESTROYING_EXCEPTION =
            new SecurityException("BG THREAD, Y U NO SURVIVE?");

    // Callback used to make sure the exception from step1 was caught by the rule.
    private static final MySyncCallback sCallback = new MySyncCallback();

    @RunWith(JUnit4.class)
    public static final class Test1ThrowsInBg {

        @Rule
        public final ProcessLifeguardRule rule =
                new ProcessLifeguardRule(ProcessLifeguardRule.Mode.FORWARD);

        @Test
        public void doIt() throws Exception {
            Log.i(
                    TAG,
                    "Test1ThrowsInBg: Posting self-destroying (in "
                            + SELF_DESTROY_TIMEOUT_MS
                            + "ms) while running on thread "
                            + Thread.currentThread());
            sHandler.postDelayed(
                    () -> {
                        Log.d(
                                TAG,
                                "Posting another delayed runnable to inject the result in "
                                        + SELF_DESTROY_TIMEOUT_MS
                                        + " ms");
                        sHandler.postDelayed(
                                () -> sCallback.injectResult(new Object()),
                                SELF_DESTROY_TIMEOUT_MS);
                        Log.i(
                                TAG,
                                "Throwing "
                                        + SELF_DESTROYING_EXCEPTION
                                        + " on "
                                        + Thread.currentThread());
                        throw SELF_DESTROYING_EXCEPTION;
                    },
                    SELF_DESTROY_TIMEOUT_MS);
            Log.i(TAG, "Test1ThrowsInBg: leaving");
        }
    }

    @RunWith(JUnit4.class)
    public static final class Test2RuleCatchesIt {

        // Rule used to make sure ProcessLifeguardRule failed  with the exception throwing by test1
        @Rule(order = 0)
        public final UncaughtBackgroundExceptionCheckerRule uncaughtBackgroundExceptionChecker =
                new UncaughtBackgroundExceptionCheckerRule();

        @Rule(order = 1)
        public final ProcessLifeguardRule rule =
                new ProcessLifeguardRule(ProcessLifeguardRule.Mode.FORWARD);

        @Test
        public void doIt() throws Exception {
            Log.i(TAG, "Test2RuleCatchesIt: callback=" + sCallback);
            sCallback.assertReceived();
            // Need to sleep a little to make sure the rule caught it.
            sleep(NAP_TIME_MS);
            Log.i(TAG, "Test2RuleCatchesIt(): leaving");
        }
    }

    @RunWith(JUnit4.class)
    public static final class Test3MakesSureProcessDidntCrash {

        @Rule
        public final ProcessLifeguardRule rule =
                new ProcessLifeguardRule(ProcessLifeguardRule.Mode.FORWARD);

        @Test
        public void doIt() {
            Log.i(TAG, "Test3MakesSureProcessDidntCrash: Good News, Everyone! Process is alive");
            assertWithMessage("Thread's dead baby, thread's dead!")
                    .that(sHandlerThread.isAlive())
                    .isFalse();
        }
    }

    private static void sleep(int napTimeMs) {
        Log.i(TAG, "Sleeping " + napTimeMs + "ms on thread " + Thread.currentThread());
        SystemClock.sleep(napTimeMs);
        Log.i(TAG, "Little Susie woke up");
    }

    private static class MySyncCallback extends SyncCallback<Object, Object> {

        MySyncCallback() {
            super(WAITING_TIMEOUT_MS);
        }
    }

    private static final class UncaughtBackgroundExceptionCheckerRule implements TestRule {

        private static final String TAG =
                UncaughtBackgroundExceptionCheckerRule.class.getSimpleName();

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    try {
                        Log.d(TAG, "Calling " + description);
                        base.evaluate();
                        fail("Test should have thrown a " + UncaughtBackgroundException.class);
                    } catch (UncaughtBackgroundException t) {
                        Log.d(TAG, "Caught '" + t + "' as expected");
                        assertWithMessage("%s", UncaughtBackgroundException.class.getSimpleName())
                                .that(t)
                                .hasCauseThat()
                                .isSameInstanceAs(SELF_DESTROYING_EXCEPTION);
                    } catch (Throwable t) {
                        Log.e(TAG, "Caught unexpected exception: " + t);
                        throw t;
                    }
                }
            };
        }
    }
}
