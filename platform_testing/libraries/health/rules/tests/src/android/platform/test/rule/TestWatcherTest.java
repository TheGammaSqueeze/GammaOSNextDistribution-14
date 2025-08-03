/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

/** Unit test the logic for {@link KillAppsRule} */
@RunWith(JUnit4.class)
public class TestWatcherTest {
    /** Tests the order of operations for all calls being successful. */
    @Test
    public void testAllCallsSuccessful() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, false, false, false, false, false, false);
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations())
                .containsExactly("starting", "statement", "succeeded", "finished")
                .inOrder();
    }

    /** Tests the order of operations when the starting call throws. */
    @Test
    public void testCallsFail_starting() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(true, false, false, false, false, false, false);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "failed", "finished")
                    .inOrder();
        }
    }

    /** Tests the order of operations when the statement throws. */
    @Test
    public void testCallsFail_statement() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, true, false, false, false, false, false);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "statement", "failed", "finished")
                    .inOrder();
        }
    }

    /** Tests the order of operations when the succeeded call throws. */
    @Test
    public void testCallsFail_succeeded() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, false, true, false, false, false, false);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "statement", "succeeded", "failed", "finished")
                    .inOrder();
        }
    }

    /** Tests the order of operations when the statement is skipped by assumption. */
    @Test
    public void testStatementIsSkipped() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, false, false, false, false, false, true);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (AssumptionViolatedException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "statement", "skipped", "finished")
                    .inOrder();
        }
    }

    /** Tests the order of operations when the statement is skipped and the skipped call throws. */
    @Test
    public void testCallFails_skipped() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, false, false, true, false, false, true);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "statement", "skipped", "finished")
                    .inOrder();
            assertTrue(!(e instanceof AssumptionViolatedException));
        }
    }

    /** Tests the order of operations when the failed call throws. */
    @Test
    public void testCallsFail_failed() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, true, false, false, true, false, false);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "statement", "failed", "finished")
                    .inOrder();
        }
    }

    /** Tests the order of operations when the finished call throws. */
    @Test
    public void testCallsFail_finished() throws Throwable {
        TestableTestWatcher rule =
                new TestableTestWatcher(false, false, false, false, false, true, false);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            assertThat(rule.getOperations())
                    .containsExactly("starting", "statement", "succeeded", "finished")
                    .inOrder();
        }
    }

    private static class TestableTestWatcher extends TestWatcher {
        private List<String> mOperations = new ArrayList<>();

        private final boolean mFailStarting;
        private final boolean mFailStatement;
        private final boolean mFailSucceeded;
        private final boolean mFailSkipped;
        private final boolean mFailFailed;
        private final boolean mFailFinished;

        private final boolean mIsSkipped;

        public TestableTestWatcher(
                boolean failStarting,
                boolean failStatement,
                boolean failSucceeded,
                boolean failSkipped,
                boolean failFailed,
                boolean failFinished,
                boolean isSkipped) {
            mFailStarting = failStarting;
            mFailStatement = failStatement;
            mFailSucceeded = failSucceeded;
            mFailSkipped = failSkipped;
            mFailFailed = failFailed;
            mFailFinished = failFinished;

            mIsSkipped = isSkipped;
        }

        public List<String> getOperations() {
            return mOperations;
        }

        @Override
        protected void succeeded(Description description) {
            mOperations.add("succeeded");
            if (mFailSucceeded) {
                throw new RuntimeException("failed succeeded");
            }
        }

        @Override
        protected void failed(Throwable e, Description description) {
            mOperations.add("failed");
            if (mFailFailed) {
                throw new RuntimeException("failed failed");
            }
        }

        @Override
        protected void skipped(AssumptionViolatedException e, Description description) {
            mOperations.add("skipped");
            if (mFailSkipped) {
                throw new RuntimeException("failed skipped");
            }
        }

        @Override
        protected void starting(Description description) {
            mOperations.add("starting");
            if (mFailStarting) {
                throw new RuntimeException("failed starting");
            }
        }

        @Override
        protected void finished(Description description) {
            mOperations.add("finished");
            if (mFailFinished) {
                throw new RuntimeException("failed finished");
            }
        }

        public Statement getTestStatement() {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mOperations.add("statement");
                    if (mIsSkipped) {
                        throw new AssumptionViolatedException("skipped.");
                    } else if (mFailStatement) {
                        throw new RuntimeException("failed statement.");
                    }
                }
            };
        }
    }
}
