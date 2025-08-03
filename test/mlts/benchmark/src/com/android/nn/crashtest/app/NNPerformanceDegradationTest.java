/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.nn.crashtest.app;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import androidx.test.InstrumentationRegistry;

import com.android.nn.benchmark.app.AcceleratorSpecificTestSupport;
import com.android.nn.benchmark.app.BenchmarkTestBase;
import com.android.nn.crashtest.core.test.PerformanceDegradationTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collections;

@RunWith(Parameterized.class)
public class NNPerformanceDegradationTest extends
        ActivityInstrumentationTestCase2<NNPerformanceDegradationTestActivity> implements
    AcceleratorSpecificTestSupport {
    public static final String TAG = PerformanceDegradationTest.TAG;


    @Parameters(name = "Threshold {1} % and {0} extra compiling threads on accelerator {2}")
    public static Iterable<Object[]> testConfiguration() {
        return AcceleratorSpecificTestSupport.perAcceleratorTestConfig(
                Collections.singletonList(
                        new Object[]{/*thread count*/1, /*max degradation percent*/50}));
    }

    public NNPerformanceDegradationTest(int threadCount,
            int maxPerformanceDegradationPercent, String acceleratorName) {
        super(NNPerformanceDegradationTestActivity.class);
        mAcceleratorName = acceleratorName;
        mThreadCount = threadCount;
        mMaxPerformanceDegradationPercent = maxPerformanceDegradationPercent;
    }


    @Rule
    public TestName mTestName = new TestName();

    private static final float WARM_UP_TIME_SECONDS = 5;
    private static final float RUN_TIME_SECONDS = 20;

    private final String mAcceleratorName;
    private final int mThreadCount;
    private final int mMaxPerformanceDegradationPercent;

    protected static Intent getTestMaxPerfDegradationOfModelWIthThreads(String testName,
            String acceleratorName, int threadCount,
            int maxPerformanceDegradationPercent) {
        Intent result = new Intent();
        PerformanceDegradationTest.intentInitializer(WARM_UP_TIME_SECONDS, RUN_TIME_SECONDS,
                acceleratorName, threadCount, maxPerformanceDegradationPercent,
                testName).addIntentParams(result);
        return result;
    }

    @Test
    @UiThreadTest
    public void shouldNotDegradePerformanceOverThreshold() {
        CrashTestStatus.TestResult testResult = getActivity().testResult();
        assertEquals("Test didn't complete successfully", CrashTestStatus.TestResult.SUCCESS,
                testResult);
    }

    @Before
    @Override
    public void setUp() {
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        BenchmarkTestBase.waitUntilCharged(getInstrumentation().getTargetContext(), 60);
        setActivityIntent(getTestMaxPerfDegradationOfModelWIthThreads(mTestName.getMethodName(),
                mAcceleratorName,
                mThreadCount,
                mMaxPerformanceDegradationPercent));
    }


}
