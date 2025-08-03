/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Bundle;

import com.android.helpers.GarbageCollectionHelper;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

/**
 * Unit test the logic for {@link GarbageCollectRule}
 */
@RunWith(JUnit4.class)
public class GarbageCollectRuleTest {
    private final GarbageCollectionHelper mGcHelper = mock(GarbageCollectionHelper.class);

    /**
     * Tests that this rule will not call gc before the test if an app not supplied.
     */
    @Test
    public void testNoGcBeforeTest() throws Throwable {
        GarbageCollectRule rule = new TestableGarbageCollectRule();
        Statement testStatement = new Statement() {
            @Override
            public void evaluate() throws Throwable {
            }
        };

        rule.apply(testStatement,
                Description.createTestDescription("clzz", "mthd")).evaluate();
        verify(mGcHelper, times(0)).garbageCollect();
    }


    /**
     * Tests that this rule will gc before the test if an app is supplied.
     */
    @Test
    public void testCallsGcBeforeTest() throws Throwable {
        GarbageCollectRule rule = new TestableGarbageCollectRule("package.name1");
        Statement testStatement =
                new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        // Assert that garbage collection was called before the test.
                        verify(mGcHelper).setUp("package.name1");
                        verify(mGcHelper)
                                .garbageCollect(
                                        GarbageCollectionHelper.DEFAULT_POST_GC_WAIT_TIME_MS);
                    }
                };

        rule.apply(testStatement,
                Description.createTestDescription("clzz", "mthd")).evaluate();
    }

    /**
     * Tests that this rule will gc before and after the test if an app is supplied.
     */
    @Test
    public void testCallsGcBeforeAndAfterTest() throws Throwable {
        Bundle gcEndBundle = new Bundle();
        gcEndBundle.putString(GarbageCollectRule.GC_RULE_END, "true");
        GarbageCollectRule rule = new TestableGarbageCollectRule(gcEndBundle, "package.name1");
        Statement testStatement = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // Assert that garbage collection was called before the test.
                verify(mGcHelper).setUp("package.name1");
            }
        };

        rule.apply(testStatement,
                Description.createTestDescription("clzz", "mthd")).evaluate();
        verify(mGcHelper, times(2))
                .garbageCollect(GarbageCollectionHelper.DEFAULT_POST_GC_WAIT_TIME_MS);
    }

    /**
     * Tests that this rule will gc only before the test if an app is supplied.
     */
    @Test
    public void testCallsGcOnlyBeforeTest() throws Throwable {
        Bundle gcEndBundle = new Bundle();
        gcEndBundle.putString(GarbageCollectRule.GC_RULE_END, "false");
        GarbageCollectRule rule = new TestableGarbageCollectRule(gcEndBundle, "package.name1");
        Statement testStatement = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // Assert that garbage collection was called before the test.
                verify(mGcHelper).setUp("package.name1");
            }
        };

        rule.apply(testStatement,
                Description.createTestDescription("clzz", "mthd")).evaluate();
        verify(mGcHelper, times(1))
                .garbageCollect(GarbageCollectionHelper.DEFAULT_POST_GC_WAIT_TIME_MS);
    }

    /** Tests that this rule will gc only before the test if an app is supplied. */
    @Test
    public void testExtendWaitTime() throws Throwable {
        Bundle gcEndBundle = new Bundle();
        gcEndBundle.putString(GarbageCollectRule.GC_RULE_END, "false");
        gcEndBundle.putString(GarbageCollectRule.GC_WAIT_TIME_MS, "5000");
        GarbageCollectRule rule = new TestableGarbageCollectRule(gcEndBundle, "package.name1");
        Statement testStatement =
                new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        // Assert that garbage collection was called before the test.
                        verify(mGcHelper).setUp("package.name1");
                    }
                };

        rule.apply(testStatement, Description.createTestDescription("clzz", "mthd")).evaluate();
        verify(mGcHelper, times(1)).garbageCollect(5000L);
    }

    private class TestableGarbageCollectRule extends GarbageCollectRule {
        private Bundle mBundle;

        public TestableGarbageCollectRule() {
            super();
        }

        public TestableGarbageCollectRule(Bundle bundle, String app) {
            super(app);
            mBundle = bundle;
        }

        public TestableGarbageCollectRule(String app) {
            super(app);
        }

        @Override
        protected Bundle getArguments() {
            return mBundle;
        }

        @Override
        GarbageCollectionHelper getGcHelper() {
            return mGcHelper;
        }
    }
}
