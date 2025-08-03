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
package android.platform.test.rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Instrumentation;
import android.device.collectors.BaseMetricListener;
import android.device.collectors.DataRecord;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.List;

/**
 * Tests for {@link ClassMetricRule}.
 *
 * <p>This test will focus on testing that collectors are loaded with the correct argument, and that
 * they are reporting their results as run metrics. All the other logic has been tested in {@link
 * TestMetricRuleTest}.
 */
public class ClassMetricRuleTest {

    private static final Description DESCRIPTION =
            Description.createTestDescription("class", "method");

    private static final Statement TEST_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {}
            };

    @Mock private Instrumentation mMockInstrumentation;

    @Captor private ArgumentCaptor<Bundle> addResultsCaptor;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testRunsSpecifiedCollectorsAndReportRunMetrics() throws Throwable {
        ClassMetricRule rule =
                createWithMetricCollectorNames(
                        "android.platform.test.rule.ClassMetricRuleTest$TestableCollector2",
                        "android.platform.test.rule.ClassMetricRuleTest$TestableCollector1");
        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        // We have two metric collectors, hence results are reported two times.
        verify(mMockInstrumentation, times(2)).addResults(addResultsCaptor.capture());
        List<Bundle> results = addResultsCaptor.getAllValues();
        boolean hasCollector1 = false, hasCollector2 = false;
        for (Bundle result : results) {
            if (result.containsKey("TestableCollector1-test")) {
                hasCollector1 = true;
            } else if (result.containsKey("TestableCollector2-test")) {
                hasCollector2 = true;
            }
        }
        assertTrue(hasCollector1);
        assertTrue(hasCollector2);
    }

    @Test
    public void testUsesTestCallbackRatherThanRunCallback() throws Throwable {
        ClassMetricRule rule =
                createWithMetricCollectorNames(
                        "android.platform.test.rule.ClassMetricRuleTest$TestableCollector1");
        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        // We have one metric collector, hence results are reported a single time.
        verify(mMockInstrumentation, times(1)).addResults(addResultsCaptor.capture());
        Bundle result = addResultsCaptor.getValue();
        assertTrue(result.containsKey("TestableCollector1-test"));
        assertFalse(result.containsKey("TestableCollector1-run"));
    }

    private ClassMetricRule createWithMetricCollectorNames(String... names) {
        Bundle args = new Bundle();
        args.putString(ClassMetricRule.METRIC_COLLECTORS_OPTION, String.join(",", names));

        return new ClassMetricRule(args, mMockInstrumentation);
    }

    public static class BaseTestableCollector extends BaseMetricListener {
        private final String mName;

        public BaseTestableCollector(String name) {
            mName = name;
        }

        @Override
        public void onTestEnd(DataRecord testData, Description description) {
            testData.addStringMetric(mName + "-test", "value");
        }

        // This method should never be used by the rule.
        @Override
        public void onTestRunEnd(DataRecord runData, Result result) {
            runData.addStringMetric(mName + "-run", "value");
        }
    }

    public static class TestableCollector1 extends BaseTestableCollector {
        public TestableCollector1() {
            super(TestableCollector1.class.getSimpleName());
        }
    }

    public static class TestableCollector2 extends BaseTestableCollector {
        public TestableCollector2() {
            super(TestableCollector2.class.getSimpleName());
        }
    }
}
