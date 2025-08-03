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
package vogar.target.testng;

import org.testng.TestNG;

import vogar.monitor.TargetMonitor;
import vogar.target.TargetRunner;
import vogar.target.TestEnvironment;

import java.util.concurrent.atomic.AtomicReference;

public class TestNgTargetRunner implements TargetRunner {

    private final TargetMonitor monitor;
    private final Class<?> testClass;
    private final TestEnvironment testEnvironment;
    private final String qualification;
    private final TestNgAnnotationTransformer transformer;
    private final String[] args;

    public TestNgTargetRunner(
            TargetMonitor monitor,
            AtomicReference<String> skipPastReference,
            TestEnvironment testEnvironment,
            int timeoutSeconds,
            Class<?> testClass,
            String qualification,
            String[] excludeFilters,
            String[] args) {
        this.monitor = monitor;
        this.testClass = testClass;
        this.testEnvironment = testEnvironment;
        this.qualification = qualification;
        this.args = args;
        this.transformer =
                new TestNgAnnotationTransformer(
                        timeoutSeconds, skipPastReference, qualification, args);
    }

    @Override
    public boolean run() {
        // Set up TestNg core infrastructure.
        TestNG testng = new TestNG(false);

        // This transformer handles test timeout and overrides it if vogar was run
        // with specific timeout parameter (see  --timeout option).
        testng.setAnnotationTransformer(transformer);

        // Disable parallel execution of tests using @DataProvider's.
        // If parallel execution is enabled with @DataProvider(..., parallel = true) then it
        // would break vogar.monitor.HostMonitor#followProcess protocol which relies on receiving
        // control messages in specific order. It expects "{outcome: ...}" json-object
        // followed by another "{result: ...}" json-object for each test run; whereas with parallel
        // execution enabled for @DataProvider, HostMonitor#followProcess would receive all
        // "outcome" objects, and then all "result" objects, in random order.
        // The easiest way of preventing this behavior would be to disable parallel execution
        // for data providers.
        testng.setDataProviderThreadCount(1);

        // Make TestNG less noisy.
        testng.setVerbose(0);
        // Proxy to pass TestNg test lifecycle calls to vogar.
        TestNgListenerAdapter adapter = new TestNgListenerAdapter(monitor);
        testng.addListener(adapter);
        // Listener that resets environment for each test.
        testng.addListener(new TestEnvironmentListener(testEnvironment));

        testng.setTestClasses(new Class[] {testClass});

        testng.run();
        return true;
    }
}
