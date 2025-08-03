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

import android.app.Instrumentation;
import android.device.collectors.BaseMetricListener;
import android.device.collectors.DataRecord;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

/** Tests for {@link TestMetricRule}. */
public class TestMetricRuleTest {
    // A static variable is used so that the metric listeners in this test can modify its state
    // while being static and able to be created via an empty constructor. This route is taken as
    // the metric collectors themselves are not directly observable due to being created via
    // reflection fron TestMetricRule.
    private static List<String> sLogs = new ArrayList<String>();

    private static final Description DESCRIPTION =
            Description.createTestDescription("class", "method");

    private static final String TEST_FAILURE_MESSAGE = "Failed!";

    private static final Statement PASSING_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {
                    sLogs.add("Test execution");
                }
            };

    private static final Statement FAILING_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {
                    sLogs.add("Test execution");
                    throw new RuntimeException(TEST_FAILURE_MESSAGE);
                }
            };

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        sLogs.clear();
    }

    @Test
    public void testValidListener_testPasses() throws Throwable {
        TestMetricRule rule =
                createWithMetricCollectorNames(
                        "android.platform.test.rule.TestMetricRuleTest$TestableCollector1");
        rule.apply(PASSING_STATEMENT, DESCRIPTION).evaluate();
        assertThat(sLogs)
                .containsExactly(
                        "TestableCollector1#setInstrumentation",
                        "TestableCollector1#setupAdditionalArgs",
                        "TestableCollector1#onSetUp",
                        String.format("Test %s: TestableCollector1#onTestStart", DESCRIPTION),
                        "Test execution",
                        String.format("Test %s: TestableCollector1#onTestEnd", DESCRIPTION),
                        "TestableCollector1#onCleanUp")
                .inOrder();
    }

    @Test
    public void testValidListener_testFails() throws Throwable {
        TestMetricRule rule =
                createWithMetricCollectorNames(
                        "android.platform.test.rule.TestMetricRuleTest$TestableCollector1");
        expectedException.expectMessage(TEST_FAILURE_MESSAGE);
        rule.apply(FAILING_STATEMENT, DESCRIPTION).evaluate();
        Failure failure = new Failure(DESCRIPTION, new RuntimeException(TEST_FAILURE_MESSAGE));
        assertThat(sLogs)
                .containsExactly(
                        "TestableCollector1#setInstrumentation",
                        "TestableCollector1#setupAdditionalArgs",
                        "TestableCollector1#onSetUp",
                        String.format("Test %s: TestableCollector1#onTestStart", DESCRIPTION),
                        "Test execution",
                        String.format(
                                "Test %s: TestableCollector1#onTestFail with failure %s",
                                DESCRIPTION,
                                new Failure(
                                        DESCRIPTION, new RuntimeException(TEST_FAILURE_MESSAGE))),
                        String.format("Test %s: TestableCollector1#onTestEnd", DESCRIPTION),
                        "TestableCollector1#onCleanUp")
                .inOrder();
    }

    @Test
    public void testMultipleListeners_allValid() throws Throwable {
        TestMetricRule rule =
                createWithMetricCollectorNames(
                        "android.platform.test.rule.TestMetricRuleTest$TestableCollector2",
                        "android.platform.test.rule.TestMetricRuleTest$TestableCollector1");
        expectedException.expectMessage(TEST_FAILURE_MESSAGE);
        rule.apply(FAILING_STATEMENT, DESCRIPTION).evaluate();
        Failure failure = new Failure(DESCRIPTION, new RuntimeException(TEST_FAILURE_MESSAGE));
        assertThat(sLogs)
                .containsExactly(
                        "TestableCollector1#setInstrumentation",
                        "TestableCollector2#setInstrumentation",
                        "TestableCollector1#setupAdditionalArgs",
                        "TestableCollector1#onSetUp",
                        "TestableCollector2#setupAdditionalArgs",
                        "TestableCollector2#onSetUp",
                        String.format("Test %s: TestableCollector1#onTestStart", DESCRIPTION),
                        String.format("Test %s: TestableCollector2#onTestStart", DESCRIPTION),
                        "Test execution",
                        String.format(
                                "Test %s: TestableCollector1#onTestFail with failure %s",
                                DESCRIPTION, failure),
                        String.format(
                                "Test %s: TestableCollector2#onTestFail with failure %s",
                                DESCRIPTION, failure),
                        String.format("Test %s: TestableCollector1#onTestEnd", DESCRIPTION),
                        String.format("Test %s: TestableCollector2#onTestEnd", DESCRIPTION),
                        "TestableCollector1#onCleanUp",
                        "TestableCollector2#onCleanUp")
                .inOrder();
    }

    @Test
    public void testMultipleListeners_lifoOrder() throws Throwable {
        Bundle args = new Bundle();
        args.putString(
                TestMetricRule.METRIC_COLLECTORS_OPTION,
                String.join(
                        ",",
                        new String[] {
                            "android.platform.test.rule.TestMetricRuleTest$TestableCollector1",
                            "android.platform.test.rule.TestMetricRuleTest$TestableCollector2"
                        }));
        args.putString(TestMetricRule.FIFO_ORDER_OPTION, "false");
        TestMetricRule rule = new TestMetricRule(args);
        expectedException.expectMessage(TEST_FAILURE_MESSAGE);
        rule.apply(FAILING_STATEMENT, DESCRIPTION).evaluate();
        Failure failure = new Failure(DESCRIPTION, new RuntimeException(TEST_FAILURE_MESSAGE));
        assertThat(sLogs)
                .containsExactly(
                        "TestableCollector1#setInstrumentation",
                        "TestableCollector2#setInstrumentation",
                        "TestableCollector1#setupAdditionalArgs",
                        "TestableCollector1#onSetUp",
                        "TestableCollector2#setupAdditionalArgs",
                        "TestableCollector2#onSetUp",
                        String.format("Test %s: TestableCollector1#onTestStart", DESCRIPTION),
                        String.format("Test %s: TestableCollector2#onTestStart", DESCRIPTION),
                        "Test execution",
                        String.format(
                                "Test %s: TestableCollector2#onTestFail with failure %s",
                                DESCRIPTION, failure),
                        String.format(
                                "Test %s: TestableCollector1#onTestFail with failure %s",
                                DESCRIPTION, failure),
                        String.format("Test %s: TestableCollector2#onTestEnd", DESCRIPTION),
                        String.format("Test %s: TestableCollector1#onTestEnd", DESCRIPTION),
                        "TestableCollector2#onCleanUp",
                        "TestableCollector1#onCleanUp")
                .inOrder();
    }

    @Test
    public void testInvalidListenerNameThrows() {
        String invalidName = "not.a.Collector";
        expectedException.expectMessage(
                String.format(
                        "Failed to dynamically load metric collector with fully qualified name %s.",
                        invalidName));
        // The creation should fail.
        TestMetricRule rule =
                createWithMetricCollectorNames(
                        "android.platform.test.rule.TestMetricRuleTest$TestableCollector1",
                        invalidName);
    }

    @Test
    public void testSimpleClassNameAttemptedWithKnownPackage() {
        String simpleName = "NonExistentCollector";
        // We can't validate real collectors here due to test logistics, so we proxy it by using
        // an invalid name, and checking that the full name we give in the exception.
        expectedException.expectMessage(
                String.format("%s.%s", TestMetricRule.METRIC_COLLECTORS_PACKAGE, simpleName));
        // The creation should fail.
        TestMetricRule rule = createWithMetricCollectorNames(simpleName);
    }

    @Test
    public void testInitWithDifferentOptionNames() throws Throwable {
        String listenerOptionName = "another-" + TestMetricRule.METRIC_COLLECTORS_OPTION;
        String fifoOptionName = "another-" + TestMetricRule.FIFO_ORDER_OPTION;

        Bundle args = new Bundle();
        args.putString(
                listenerOptionName,
                String.join(
                        ",",
                        new String[] {
                            "android.platform.test.rule.TestMetricRuleTest$TestableCollector1",
                            "android.platform.test.rule.TestMetricRuleTest$TestableCollector2"
                        }));
        args.putString(fifoOptionName, "false");
        TestMetricRule rule =
                new TestMetricRule(
                        args, new Instrumentation(), listenerOptionName, fifoOptionName, "log tag");

        rule.apply(PASSING_STATEMENT, DESCRIPTION).evaluate();
        assertThat(sLogs)
                .containsExactly(
                        "TestableCollector1#setInstrumentation",
                        "TestableCollector2#setInstrumentation",
                        "TestableCollector1#setupAdditionalArgs",
                        "TestableCollector1#onSetUp",
                        "TestableCollector2#setupAdditionalArgs",
                        "TestableCollector2#onSetUp",
                        String.format("Test %s: TestableCollector1#onTestStart", DESCRIPTION),
                        String.format("Test %s: TestableCollector2#onTestStart", DESCRIPTION),
                        "Test execution",
                        String.format("Test %s: TestableCollector2#onTestEnd", DESCRIPTION),
                        String.format("Test %s: TestableCollector1#onTestEnd", DESCRIPTION),
                        "TestableCollector2#onCleanUp",
                        "TestableCollector1#onCleanUp")
                .inOrder();
    }

    private TestMetricRule createWithMetricCollectorNames(String... names) {
        Bundle args = new Bundle();
        args.putString(TestMetricRule.METRIC_COLLECTORS_OPTION, String.join(",", names));
        return new TestMetricRule(args);
    }

    public static class BaseTestableCollector extends BaseMetricListener {
        private final String mName;

        public BaseTestableCollector(String name) {
            mName = name;
        }

        @Override
        public void setInstrumentation(Instrumentation instr) {
            sLogs.add(String.format("%s#%s", mName, "setInstrumentation"));
        }

        @Override
        public void setupAdditionalArgs() {
            sLogs.add(String.format("%s#%s", mName, "setupAdditionalArgs"));
        }

        @Override
        public void onSetUp() {
            sLogs.add(String.format("%s#%s", mName, "onSetUp"));
        }

        @Override
        public void onCleanUp() {
            sLogs.add(String.format("%s#%s", mName, "onCleanUp"));
        }

        @Override
        public void onTestStart(DataRecord testData, Description description) {
            sLogs.add(String.format("Test %s: %s#%s", description, mName, "onTestStart"));
        }

        @Override
        public void onTestEnd(DataRecord testData, Description description) {
            sLogs.add(String.format("Test %s: %s#%s", description, mName, "onTestEnd"));
        }

        @Override
        public void onTestFail(DataRecord testData, Description description, Failure failure) {
            sLogs.add(
                    String.format(
                            "Test %s: %s#%s with failure %s",
                            description, mName, "onTestFail", failure));
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
