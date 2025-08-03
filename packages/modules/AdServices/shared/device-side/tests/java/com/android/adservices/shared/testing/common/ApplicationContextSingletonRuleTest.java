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

package com.android.adservices.shared.testing.common;

import static com.android.adservices.shared.util.LogUtil.DEBUG;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.content.Context;

import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.util.LogUtil;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class ApplicationContextSingletonRuleTest {
    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    private static final boolean RESTORE_PREVIOUS = true;
    private static final boolean DONT_RESTORE_PREVIOUS = false;

    private Context mPreviousContext;

    public @Rule final Expect expect = Expect.create();

    @Before
    public void savePreviousContext() {
        mPreviousContext = ApplicationContextSingleton.getForTests();
        if (DEBUG) {
            LogUtil.d("@Before: save context as %s", mPreviousContext);
        }
    }

    @After
    public void restorePreviousContext() {
        if (DEBUG) {
            LogUtil.d("@After: restoring context as %s", mPreviousContext);
        }
        ApplicationContextSingleton.setForTests(mPreviousContext);
    }

    @Test
    public void testNullConstructor() {
        assertThrows(
                NullPointerException.class,
                () -> new ApplicationContextSingletonRule(null, RESTORE_PREVIOUS));
    }

    @Test
    public void testDefaultConstructorSetsNonNullContext() throws Throwable {
        ApplicationContextSingletonRule rule =
                new ApplicationContextSingletonRule(RESTORE_PREVIOUS);

        runTestWithRule(
                rule,
                () ->
                        expect.withMessage("ApplicationContextSingleton.get() during test")
                                .that(ApplicationContextSingleton.get())
                                .isNotNull());
    }

    @Test
    public void testApplicationContextSetDuringTestAndResetAtTheEnd() throws Throwable {
        Context contextBefore = mock(Context.class, "contextBefore");
        Context ruleContext = mock(Context.class, "ruleContext");
        ApplicationContextSingleton.setForTests(contextBefore);
        ApplicationContextSingletonRule rule =
                new ApplicationContextSingletonRule(ruleContext, RESTORE_PREVIOUS);

        runTestWithRule(
                rule,
                () -> {
                    expect.withMessage("ApplicationContextSingleton.get() during test")
                            .that(ApplicationContextSingleton.get())
                            .isSameInstanceAs(ruleContext);
                    expect.withMessage("rule.get() during test")
                            .that(rule.get())
                            .isSameInstanceAs(ruleContext);
                });

        expect.withMessage("ApplicationContextSingleton.get() after test")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(contextBefore);
    }

    @Test
    public void testApplicationContextSetDuringTestAndResetAtTheEndEvenWhenTestFails()
            throws Throwable {
        Context contextBefore = mock(Context.class, "contextBefore");
        Context ruleContext = mock(Context.class, "ruleContext");
        ApplicationContextSingleton.setForTests(contextBefore);
        ApplicationContextSingletonRule rule =
                new ApplicationContextSingletonRule(ruleContext, RESTORE_PREVIOUS);
        RuntimeException testFailure = new RuntimeException("D'OH!");

        RuntimeException actualFailure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                runTestWithRule(
                                        rule,
                                        () -> {
                                            expect.withMessage(
                                                            "ApplicationContextSingleton.get()"
                                                                    + " during test")
                                                    .that(ApplicationContextSingleton.get())
                                                    .isSameInstanceAs(ruleContext);
                                            expect.withMessage("rule.get() during test")
                                                    .that(rule.get())
                                                    .isSameInstanceAs(ruleContext);
                                            throw testFailure;
                                        }));

        expect.withMessage("test exception").that(actualFailure).isSameInstanceAs(testFailure);
        expect.withMessage("ApplicationContextSingleton.get() after test")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(contextBefore);
    }

    @Test
    public void testApplicationContextSetDuringTestAndDontResetAtTheEnd() throws Throwable {
        Context contextBefore = mock(Context.class, "contextBefore");
        Context ruleContext = mock(Context.class, "ruleContext");
        ApplicationContextSingleton.setForTests(contextBefore);
        ApplicationContextSingletonRule rule =
                new ApplicationContextSingletonRule(ruleContext, DONT_RESTORE_PREVIOUS);

        runTestWithRule(
                rule,
                () -> {
                    expect.withMessage("ApplicationContextSingleton.get() during test")
                            .that(ApplicationContextSingleton.get())
                            .isSameInstanceAs(ruleContext);
                    expect.withMessage("rule.get() during test")
                            .that(rule.get())
                            .isSameInstanceAs(ruleContext);
                });

        expect.withMessage("ApplicationContextSingleton.get() after test")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(ruleContext);
    }

    @Test
    public void testApplicationContextSetDuringTestAndDontResetAtTheEndEvenWhenTestFails()
            throws Throwable {
        Context contextBefore = mock(Context.class, "contextBefore");
        Context ruleContext = mock(Context.class, "ruleContext");
        ApplicationContextSingleton.setForTests(contextBefore);
        ApplicationContextSingletonRule rule =
                new ApplicationContextSingletonRule(ruleContext, DONT_RESTORE_PREVIOUS);
        RuntimeException testFailure = new RuntimeException("D'OH!");

        RuntimeException actualFailure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                runTestWithRule(
                                        rule,
                                        () -> {
                                            expect.withMessage(
                                                            "ApplicationContextSingleton.get()"
                                                                    + " during test")
                                                    .that(ApplicationContextSingleton.get())
                                                    .isSameInstanceAs(ruleContext);
                                            expect.withMessage("rule.get() during test")
                                                    .that(rule.get())
                                                    .isSameInstanceAs(ruleContext);
                                            throw testFailure;
                                        }));

        expect.withMessage("test exception").that(actualFailure).isSameInstanceAs(testFailure);
        expect.withMessage("ApplicationContextSingleton.get() after test")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(ruleContext);
    }

    @Test
    public void testSet() throws Throwable {
        Context ruleContext = mock(Context.class, "ruleContext");
        Context setContext = mock(Context.class, "setContext");
        ApplicationContextSingletonRule rule =
                new ApplicationContextSingletonRule(ruleContext, RESTORE_PREVIOUS);

        runTestWithRule(
                rule,
                () -> {
                    rule.set(setContext);
                    expect.withMessage("ApplicationContextSingleton.get() after set()")
                            .that(ApplicationContextSingleton.get())
                            .isSameInstanceAs(setContext);
                    expect.withMessage("rule.get() after set()")
                            .that(rule.get())
                            .isSameInstanceAs(setContext);
                });
    }

    private void runTestWithRule(ApplicationContextSingletonRule rule, Runnable test)
            throws Throwable {
        RunnableStatement statement = new RunnableStatement(test);
        rule.apply(statement, newTestMethod()).evaluate();
        statement.assertEvaluated();
    }

    private static Description newTestMethod() {
        return Description.createTestDescription(
                ApplicationContextSingletonRuleTest.class, TEST_METHOD_BEING_EXECUTED);
    }

    // TODO(b/306522832): move to shared place (same package as SimpleStatement)
    private static final class RunnableStatement extends Statement {

        private final Runnable mRunnable;
        private boolean mEvaluated;

        RunnableStatement(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void evaluate() throws Throwable {
            mEvaluated = true;
            if (DEBUG) {
                LogUtil.d(
                        "RunnableStatement: before run(), ApplicationContextSingleton is %s",
                        ApplicationContextSingleton.getForTests());
            }
            mRunnable.run();
            if (DEBUG) {
                LogUtil.d(
                        "RunnableStatement: after run(), ApplicationContextSingleton is %s",
                        ApplicationContextSingleton.getForTests());
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": " + mRunnable;
        }

        public void assertEvaluated() {
            if (!mEvaluated) {
                throw new AssertionError("test statement was not evaluated");
            }
        }
    }
}
