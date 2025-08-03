/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.retry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.InstalledInstrumentationsTest;
import com.android.tradefed.testtype.suite.ModuleDefinition;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Unit tests for {@link BaseRetryDecision}. */
@RunWith(JUnit4.class)
public class BaseRetryDecisionTest {

    private BaseRetryDecision mRetryDecision;
    private TestFilterableClass mTestClass;
    private InstalledInstrumentationsTest mAutoRetriableClass;
    private ITestDevice mMockDevice;

    private class TestFilterableClass implements IRemoteTest, ITestFilterReceiver {

        private Set<String> mIncludeFilters = new HashSet<>();
        private Set<String> mExcludeFilters = new HashSet<>();

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            // Do nothing
        }

        @Override
        public void addIncludeFilter(String filter) {
            mIncludeFilters.add(filter);
        }

        @Override
        public void addAllIncludeFilters(Set<String> filters) {
            mIncludeFilters.addAll(filters);
        }

        @Override
        public void addExcludeFilter(String filter) {
            mExcludeFilters.add(filter);
        }

        @Override
        public void addAllExcludeFilters(Set<String> filters) {
            mExcludeFilters.addAll(filters);
        }

        @Override
        public Set<String> getIncludeFilters() {
            return mIncludeFilters;
        }

        @Override
        public Set<String> getExcludeFilters() {
            return mExcludeFilters;
        }

        @Override
        public void clearIncludeFilters() {
            mIncludeFilters.clear();
        }

        @Override
        public void clearExcludeFilters() {
            mExcludeFilters.clear();
        }
    }

    @Before
    public void setUp() throws Exception {
        mMockDevice = Mockito.mock(ITestDevice.class);
        mAutoRetriableClass = new InstalledInstrumentationsTest();
        mTestClass = new TestFilterableClass();
        mRetryDecision = new BaseRetryDecision();

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("default", mMockDevice);
        mRetryDecision.setInvocationContext(context);
        OptionSetter setter = new OptionSetter(mRetryDecision);
        setter.setOptionValue("max-testcase-run-count", "3");
        setter.setOptionValue("retry-strategy", "RETRY_ANY_FAILURE");
    }

    @Test
    public void testShouldRetry() throws Exception {
        TestRunResult result = createResult(null, null);
        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result));
        assertFalse(res);
    }

    @Test
    public void testShouldRetry_failure() throws Exception {
        TestRunResult result = createResult(null, FailureDescription.create("failure2"));
        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result));
        assertTrue(res);
        assertEquals(1, mTestClass.getExcludeFilters().size());
        assertTrue(mTestClass.getExcludeFilters().contains("class#method"));
    }

    @Test
    public void testShouldRetry_failure_nonRetriable() throws Exception {
        TestRunResult result =
                createResult(
                        FailureDescription.create("failure"),
                        FailureDescription.create("failure2").setRetriable(false));
        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result));
        assertTrue(res);
        assertEquals(1, mTestClass.getExcludeFilters().size());
        assertTrue(mTestClass.getExcludeFilters().contains("class#method2"));
    }

    @Test
    public void testShouldRetry_success() throws Exception {
        TestRunResult result = createResult(null, FailureDescription.create("failure2"));
        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result));
        assertTrue(res);
        assertEquals(1, mTestClass.getExcludeFilters().size());
        assertTrue(mTestClass.getExcludeFilters().contains("class#method"));
        // Following retry is successful
        TestRunResult result2 = createResult(null, null);
        boolean res2 = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result2));
        assertFalse(res2);
    }

    @Test
    public void testShouldRetry_runFailure() throws Exception {
        FailureDescription failure = FailureDescription.create("run failure");
        TestRunResult result = createResult(null, null, failure);
        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result));
        assertTrue(res);
        assertEquals(0, mTestClass.getIncludeFilters().size());
        assertEquals(2, mTestClass.getExcludeFilters().size());
    }

    @Test
    public void testShouldRetry_runFailure_noFullRetry() throws Exception {
        FailureDescription failure = FailureDescription.create("run failure");
        failure.setFullRerun(false);
        TestRunResult result = createResult(FailureDescription.create("failure"), null, failure);
        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result));
        assertTrue(res);
        assertEquals(0, mTestClass.getIncludeFilters().size());
        // The one passed test case is excluded
        assertEquals(1, mTestClass.getExcludeFilters().size());
        assertTrue(mTestClass.getExcludeFilters().contains("class#method2"));
    }

    @Test
    public void testShouldRetry_runFailure_nonRetriable() throws Exception {
        FailureDescription failure = FailureDescription.create("run failure");
        failure.setRetriable(false);
        TestRunResult result = createResult(null, null, failure);

        FailureDescription failure2 = FailureDescription.create("run failure2");
        failure2.setRetriable(false);
        TestRunResult result2 = createResult(null, null, failure2);

        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result, result2));
        assertFalse(res);
        assertEquals(0, mTestClass.getIncludeFilters().size());
        assertEquals(0, mTestClass.getExcludeFilters().size());
    }

    @Test
    public void testShouldRetry_multi_runFailure_nonRetriable() throws Exception {
        FailureDescription failure = FailureDescription.create("run failure");
        failure.setRetriable(false);
        TestRunResult result = createResult(null, null, failure);

        FailureDescription failure2 = FailureDescription.create("run failure2");
        TestRunResult result2 = createResult(null, null, failure2);

        boolean res = mRetryDecision.shouldRetry(mTestClass, 0, Arrays.asList(result, result2));
        // Skip retry due to the non-retriable failure.
        assertFalse(res);
    }

    @Test
    public void testShouldRetry_skip_retrying_list() throws Exception {
        final String SKIP_RETRYING_LIST = "skip-retrying-list";
        final String moduleID1 = "x86 module1";
        final String moduleID2 = "x86 module2";
        TestRunResult result = createResult(null, FailureDescription.create("failure2"));
        // module1 in the skip-retrying-list, it should return false.
        ModuleDefinition module1 = Mockito.mock(ModuleDefinition.class);
        Mockito.when(module1.getId()).thenReturn(moduleID1);
        OptionSetter setter = new OptionSetter(mRetryDecision);
        setter.setOptionValue(SKIP_RETRYING_LIST, moduleID1);
        boolean res =
                mRetryDecision.shouldRetry(mTestClass, module1, 0, Arrays.asList(result), null);
        assertFalse(res);
        // module2 is not in the skip-retrying-list, it should return true.
        ModuleDefinition module2 = Mockito.mock(ModuleDefinition.class);
        Mockito.when(module2.getId()).thenReturn(moduleID2);
        boolean res2 =
                mRetryDecision.shouldRetry(mTestClass, module2, 0, Arrays.asList(result), null);
        assertTrue(res2);
    }

    @Test
    public void testShouldRetry_skip_retrying_list_test() throws Exception {
        final String SKIP_RETRYING_LIST = "skip-retrying-list";
        final String moduleID1 = "x86 module1";
        final String moduleID1_test = "x86 module1 class#method2";
        TestRunResult result1 =
                createResult(
                        FailureDescription.create("failure1"),
                        FailureDescription.create("failure2"));
        ModuleDefinition module1 = Mockito.mock(ModuleDefinition.class);
        Mockito.when(module1.getId()).thenReturn(moduleID1);
        OptionSetter setter = new OptionSetter(mRetryDecision);
        setter.setOptionValue(SKIP_RETRYING_LIST, moduleID1_test);
        boolean res =
                mRetryDecision.shouldRetry(mTestClass, module1, 0, Arrays.asList(result1), null);
        assertTrue(res);
        Truth.assertThat(mTestClass.getExcludeFilters()).containsExactly("class#method2");
    }

    @Test
    public void testShouldRetry_skip_retrying_list_test_no_abi() throws Exception {
        final String SKIP_RETRYING_LIST = "skip-retrying-list";
        final String moduleID1 = "x86 module1";
        final String moduleID1_test = "module1 class#method2"; // no abi
        TestRunResult result1 =
                createResult(
                        FailureDescription.create("failure1"),
                        FailureDescription.create("failure2"));
        ModuleDefinition module1 = Mockito.mock(ModuleDefinition.class);
        Mockito.when(module1.getId()).thenReturn(moduleID1);
        OptionSetter setter = new OptionSetter(mRetryDecision);
        setter.setOptionValue(SKIP_RETRYING_LIST, moduleID1_test);
        boolean res =
                mRetryDecision.shouldRetry(mTestClass, module1, 0, Arrays.asList(result1), null);
        assertTrue(res);
        Truth.assertThat(mTestClass.getExcludeFilters()).containsExactly("class#method2");
    }

    @Test
    public void testShouldRetry_autoRetriable() throws Exception {
        OptionSetter setter = new OptionSetter(mRetryDecision);
        setter.setOptionValue("reboot-at-last-retry", "true");
        when(mMockDevice.getIDevice()).thenReturn(Mockito.mock(IDevice.class));

        TestRunResult result = createResult(null, FailureDescription.create("failure2"));
        boolean res = mRetryDecision.shouldRetry(mAutoRetriableClass, 0, Arrays.asList(result));
        assertTrue(res);
        // No reboot on first retry.
        verify(mMockDevice, never()).reboot();

        res = mRetryDecision.shouldRetry(mAutoRetriableClass, 1, Arrays.asList(result));
        assertTrue(res);
        // Reboot on last retry.
        verify(mMockDevice).reboot();
    }

    @Test
    public void testShouldRetry_skip_retrying_list_test_no_module() throws Exception {
        final String SKIP_RETRYING_LIST = "skip-retrying-list";
        final String noModuleTest1 = "class#method2";
        TestRunResult result1 =
                createResult(
                        FailureDescription.create("failure1"),
                        FailureDescription.create("failure2"));
        OptionSetter setter = new OptionSetter(mRetryDecision);
        setter.setOptionValue(SKIP_RETRYING_LIST, noModuleTest1);
        boolean res = mRetryDecision.shouldRetry(mTestClass, null, 0, Arrays.asList(result1), null);
        assertTrue(res);
        Truth.assertThat(mTestClass.getExcludeFilters()).containsExactly("class#method2");
    }

    @Test
    public void shouldRetryPreparation_NOT_ISOLATED() throws Exception {
        ModuleDefinition module1 = Mockito.mock(ModuleDefinition.class);
        RetryPreparationDecision res = mRetryDecision.shouldRetryPreparation(module1, 0, 3);
        assertFalse(res.shouldRetry());
        assertTrue(res.shouldFailRun());
    }

    private TestRunResult createResult(FailureDescription failure1, FailureDescription failure2) {
        return createResult(failure1, failure2, null);
    }

    private TestRunResult createResult(
            FailureDescription failure1,
            FailureDescription failure2,
            FailureDescription runFailure) {
        TestRunResult result = new TestRunResult();
        result.testRunStarted("TEST", 2);
        if (runFailure != null) {
            result.testRunFailed(runFailure);
        }
        TestDescription test1 = new TestDescription("class", "method");
        result.testStarted(test1);
        if (failure1 != null) {
            result.testFailed(test1, failure1);
        }
        result.testEnded(test1, new HashMap<String, Metric>());
        TestDescription test2 = new TestDescription("class", "method2");
        result.testStarted(test2);
        if (failure2 != null) {
            result.testFailed(test2, failure2);
        }
        result.testEnded(test2, new HashMap<String, Metric>());
        result.testRunEnded(500, new HashMap<String, Metric>());
        return result;
    }
}
