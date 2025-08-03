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
package com.android.tradefed.result;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** Unit tests for {@link ReportPassedTests}. */
@RunWith(JUnit4.class)
public class ReportPassedTestsTest {

    private String mExpectedString;
    private boolean mTestLogCalled = false;
    private ITestLogger mLogger;

    private ReportPassedTests mReporter =
            new ReportPassedTests() {
                @Override
                void testLog(File toBeLogged) {
                    mTestLogCalled = true;
                    try {
                        Truth.assertThat(mExpectedString)
                                .isEqualTo(FileUtil.readStringFromFile(toBeLogged));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

    @Before
    public void setUp() {
        mLogger = Mockito.mock(ITestLogger.class);
        mReporter.setLogger(mLogger);
        mReporter.invocationStarted(null);
    }

    @Test
    public void testReport() {
        mExpectedString = "run-name\nrun-name2\n";
        mReporter.testRunStarted("run-name", 0);
        TestDescription tid = new TestDescription("class", "testName");
        mReporter.testStarted(tid);
        mReporter.testEnded(tid, Collections.emptyMap());
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testRunStarted("run-name2", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_withRunFailure() {
        mExpectedString = "run-name2\n";
        mReporter.testRunStarted("run-name", 0);
        mReporter.testRunFailed("failed");
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testRunStarted("run-name2", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_singleRunFailure() {
        mExpectedString = "";
        mReporter.testRunStarted("run-name", 20);
        mReporter.testRunFailed("test run failed");
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertFalse(mTestLogCalled);
    }

    @Test
    public void testReport_withTestFailure() {
        TestDescription testPass = new TestDescription("class", "testName1");
        mExpectedString = String.format("run-name %s\nrun-name2\n", testPass.toString());
        mReporter.testRunStarted("run-name", 2);
        TestDescription testFailed = new TestDescription("class", "testName0");
        mReporter.testStarted(testFailed);
        mReporter.testFailed(testFailed, "failed");
        mReporter.testEnded(testFailed, Collections.emptyMap());
        mReporter.testStarted(testPass);
        mReporter.testEnded(testPass, Collections.emptyMap());
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testRunStarted("run-name2", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_runFailure_withPassedTestCases() {
        TestDescription tid = new TestDescription("class", "testName");
        TestDescription tid2 = new TestDescription("class", "testName2");
        TestDescription failed = new TestDescription("class", "testName3");
        TestDescription assum = new TestDescription("class", "testName4");
        mExpectedString = "run-name class#testName\nrun-name class#testName2\n"
                + "run-name class#testName4\n";
        mReporter.testRunStarted("run-name", 20);
        mReporter.testStarted(tid);
        mReporter.testEnded(tid, Collections.emptyMap());
        mReporter.testStarted(tid2);
        mReporter.testEnded(tid2, Collections.emptyMap());
        mReporter.testStarted(failed);
        mReporter.testFailed(failed, "failed");
        mReporter.testEnded(failed, Collections.emptyMap());
        mReporter.testStarted(assum);
        mReporter.testAssumptionFailure(assum, "assum fail");
        mReporter.testEnded(assum, Collections.emptyMap());
        mReporter.testRunFailed("test run failed");
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_module() {
        mExpectedString = "x86 module1\nrun-name2\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();
        mReporter.testRunStarted("run-name2", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_module_shards() {
        Configuration config = new Configuration("name", "description");
        config.getCommandOptions().setShardIndex(1);
        mReporter.setConfiguration(config);
        mExpectedString = "shard_1 x86 module1\nshard_1 run-name2\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();
        mReporter.testRunStarted("run-name2", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_singleModuleFailure() {
        mExpectedString = "";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 20);
        mReporter.testRunFailed("test run failed");
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0L);
        assertFalse(mTestLogCalled);
    }

    @Test
    public void testReport_moduleFailure_withPassedTestCases_shards() {
        Configuration config = new Configuration("name", "description");
        config.getCommandOptions().setShardIndex(1);
        mReporter.setConfiguration(config);
        TestDescription tid = new TestDescription("class", "testName");
        TestDescription tid2 = new TestDescription("class", "testName2");
        TestDescription failed = new TestDescription("class", "testName3");
        TestDescription assum = new TestDescription("class", "testName4");
        mExpectedString =
             "shard_1 x86 module1 class#testName\nshard_1 x86 module1 class#testName2\n"
                + "shard_1 x86 module1 class#testName4\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 20);
        mReporter.testStarted(tid);
        mReporter.testEnded(tid, Collections.emptyMap());
        mReporter.testStarted(tid2);
        mReporter.testEnded(tid2, Collections.emptyMap());
        mReporter.testStarted(failed);
        mReporter.testFailed(failed, "failed");
        mReporter.testEnded(failed, Collections.emptyMap());
        mReporter.testStarted(assum);
        mReporter.testAssumptionFailure(assum, "assum fail");
        mReporter.testEnded(assum, Collections.emptyMap());
        mReporter.testRunFailed("test run failed");
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_moduleFailure_withPassedTestCases() {
        TestDescription tid = new TestDescription("class", "testName");
        TestDescription tid2 = new TestDescription("class", "testName2");
        TestDescription failed = new TestDescription("class", "testName3");
        TestDescription assum = new TestDescription("class", "testName4");
        mExpectedString = "x86 module1 class#testName\nx86 module1 class#testName2\n"
                + "x86 module1 class#testName4\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 20);
        mReporter.testStarted(tid);
        mReporter.testEnded(tid, Collections.emptyMap());
        mReporter.testStarted(tid2);
        mReporter.testEnded(tid2, Collections.emptyMap());
        mReporter.testStarted(failed);
        mReporter.testFailed(failed, "failed");
        mReporter.testEnded(failed, Collections.emptyMap());
        mReporter.testStarted(assum);
        mReporter.testAssumptionFailure(assum, "assum fail");
        mReporter.testEnded(assum, Collections.emptyMap());
        mReporter.testRunFailed("test run failed");
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_invocationFailed() {
        mExpectedString = "run-name\n";
        mReporter.testRunStarted("run-name", 0);
        TestDescription tid = new TestDescription("class", "testName");
        mReporter.testStarted(tid);
        mReporter.testEnded(tid, Collections.emptyMap());
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testRunStarted("run-name2", 0);
        mReporter.invocationFailed(FailureDescription.create("invoc failed"));
        mReporter.testRunEnded(0L, Collections.emptyMap());

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_module_invocationFailed() {
        mExpectedString = "x86 module1\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();
        mReporter.testModuleStarted(createModule("x86 module2"));
        mReporter.testRunStarted("run-name2", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.invocationFailed(FailureDescription.create("invoc failed"));
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_emptyModules() {
        mExpectedString = "x86 module1\nx86_64 module1\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();
        // Second empty module
        mReporter.testModuleStarted(createModule("x86_64 module1"));
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    @Test
    public void testReport_emptyFailedModules() {
        mExpectedString = "x86 module1\n";
        mReporter.testModuleStarted(createModule("x86 module1"));
        mReporter.testRunStarted("run-name", 0);
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();
        // Second empty module
        mReporter.testModuleStarted(createModule("x86_64 module1"));
        mReporter.testRunStarted("failed-name", 0);
        mReporter.testRunFailed(FailureDescription.create("placeholder failure"));
        mReporter.testRunEnded(0L, Collections.emptyMap());
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0L);
        assertTrue(mTestLogCalled);
    }

    private IInvocationContext createModule(String id) {
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_ID, id);
        return context;
    }
}
