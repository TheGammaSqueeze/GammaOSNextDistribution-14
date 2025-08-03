/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class FakeTestTest {

    private FakeTest mTest = null;
    @Mock ITestInvocationListener mListener;
    private OptionSetter mOption = null;
    private TestInformation mTestInfo;

    @Rule public TemporaryFolder mTempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTest = new FakeTest();
        mOption = new OptionSetter(mTest);

        mTestInfo = TestInformation.newBuilder().build();
    }

    @Test
    public void testIntOrDefault() throws IllegalArgumentException {
        assertEquals(55, mTest.toIntOrDefault(null, 55));
        assertEquals(45, mTest.toIntOrDefault("45", 55));
        assertEquals(-13, mTest.toIntOrDefault("-13", 55));
        try {
            mTest.toIntOrDefault("thirteen", 55);
            fail("No IllegalArgumentException thrown for input \"thirteen\"");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDecodeRle() throws IllegalArgumentException {
        // testcases: input -> output
        final Map<String, String> t = new HashMap<String, String>();
        t.put("PFAI", "PFAI");
        t.put("P1F1A1", "PFA");
        t.put("P2F2A2", "PPFFAA");
        t.put("P3F2A1", "PPPFFA");
        t.put("", "");
        for (Map.Entry<String, String> testcase : t.entrySet()) {
            final String input = testcase.getKey();
            final String output = testcase.getValue();
            assertEquals(output, mTest.decodeRle(input));
        }

        final String[] errors = {"X", "X1", "P0", "P2F1E0", "2PF", "2PF1", " "};
        for (String error : errors) {
            try {
                mTest.decodeRle(error);
                fail(String.format("No IllegalArgumentException thrown for input \"%s\"", error));
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void testDecode() throws IllegalArgumentException {
        // testcases: input -> output
        final Map<String, String> t = new HashMap<String, String>();
        // Valid input for decodeRle should be valid for decode
        t.put("PFA", "PFA");
        t.put("P1F1A1", "PFA");
        t.put("P2F2A2", "PPFFAA");
        t.put("P3F2A1", "PPPFFA");
        t.put("", "");

        // decode should also handle parens and lowercase input
        t.put("(PFA)", "PFA");
        t.put("pfa", "PFA");
        t.put("(PFA)2", "PFAPFA");
        t.put("((PF)2)2A3", "PFPFPFPFAAA");
        t.put("()PFA", "PFA");
        t.put("PFA()", "PFA");
        t.put("(PF)(FA)", "PFFA");
        t.put("(PF()FA)", "PFFA");

        for (Map.Entry<String, String> testcase : t.entrySet()) {
            final String input = testcase.getKey();
            final String output = testcase.getValue();
            try {
                assertEquals(output, mTest.decode(input));
            } catch (IllegalArgumentException e) {
                // Add input to error message to make debugging easier
                final Exception cause =
                        new IllegalArgumentException(
                                String.format(
                                        "Encountered exception for input \"%s\" with expected"
                                                + " output \"%s\"",
                                        input, output));
                throw new IllegalArgumentException(e.getMessage(), cause);
            }
        }

        final String[] errors = {
            "X", "X1", "P0", "P2F1E0", "2PF", "2PF1", " ", // common decodeRle errors
            "(", "(PFE()", "(PFE))", "2(PFE)", "2(PFE)2", "(PF))((FE)", "(PFE)0"
        };
        for (String error : errors) {
            try {
                mTest.decode(error);
                fail(String.format("No IllegalArgumentException thrown for input \"%s\"", error));
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void testRun_empty() throws Exception {
        final String name = "com.moo.cow";

        mOption.setOptionValue("run", name, "");
        mTest.run(mTestInfo, mListener);
        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(0));
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRun_simplePass() throws Exception {
        final String name = "com.moo.cow";
        mOption.setOptionValue("run", name, "P");

        mTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(1));
        testPassExpectations(mListener, name, 1, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRun_simpleFail() throws Exception {
        final String name = "com.moo.cow";
        mOption.setOptionValue("run", name, "F");

        mTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(1));
        testFailExpectations(mListener, name, 1, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRun_basicSequence() throws Exception {
        final String name = "com.moo.cow";
        mOption.setOptionValue("run", name, "PFPAI");

        mTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(5));
        int i = 1;
        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);
        testPassExpectations(mListener, name, i++, inOrder);
        testAssumptionExpectations(mListener, name, i++, inOrder);
        testIgnoredExpectations(mListener, name, i++, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRun_withLogging() throws Exception {
        File testLog = mTempFolder.newFile("test.log");
        File testRunLog = mTempFolder.newFile("test-run.log");
        File invocationLog = mTempFolder.newFile("invocation.log");

        final String name = "com.moo.cow";

        mOption.setOptionValue("run", name, "P");
        mOption.setOptionValue("test-log", testLog.getAbsolutePath());
        mOption.setOptionValue("test-run-log", testRunLog.getAbsolutePath());
        mOption.setOptionValue("test-invocation-log", invocationLog.getAbsolutePath());
        mTest.run(mTestInfo, mListener);
        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(1));
        testPassExpectations(mListener, name, 1, testLog.getName(), inOrder);
        inOrder.verify(mListener)
                .testLog(
                        Mockito.eq(testRunLog.getName()),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.any());
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mListener)
                .testLog(
                        Mockito.eq(invocationLog.getName()),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.any());
    }

    @Test
    public void testRun_basicParens() throws Exception {
        final String name = "com.moo.cow";
        mOption.setOptionValue("run", name, "(PF)2");

        mTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(4));
        int i = 1;
        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);
        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRun_recursiveParens() throws Exception {
        final String name = "com.moo.cow";
        mOption.setOptionValue("run", name, "((PF)2)2");

        mTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted(Mockito.eq(name), Mockito.eq(8));
        int i = 1;
        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);

        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);

        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);

        testPassExpectations(mListener, name, i++, inOrder);
        testFailExpectations(mListener, name, i++, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testMultiRun() throws Exception {
        final String name1 = "com.moo.cow";
        final String name2 = "com.quack.duck";
        final String name3 = "com.oink.pig";
        mOption.setOptionValue("run", name1, "PF");
        mOption.setOptionValue("run", name2, "FP");
        mOption.setOptionValue("run", name3, "");

        mTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);

        inOrder.verify(mListener).testRunStarted(Mockito.eq(name1), Mockito.eq(2));
        int i = 1;
        testPassExpectations(mListener, name1, i++, inOrder);
        testFailExpectations(mListener, name1, i++, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        inOrder.verify(mListener).testRunStarted(Mockito.eq(name2), Mockito.eq(2));
        i = 1;
        testFailExpectations(mListener, name2, i++, inOrder);
        testPassExpectations(mListener, name2, i++, inOrder);
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        inOrder.verify(mListener).testRunStarted(Mockito.eq(name3), Mockito.eq(0));
        i = 1;
        // empty run
        inOrder.verify(mListener)
                .testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());
    }

    private void testPassExpectations(
            ITestInvocationListener l, String klass, int idx, InOrder io) {
        final String name = String.format("testMethod%d", idx);
        final TestDescription test = new TestDescription(klass, name);
        io.verify(l).testStarted(test);
        io.verify(l).testEnded(Mockito.eq(test), Mockito.<HashMap<String, Metric>>any());
    }

    private void testPassExpectations(
            ITestInvocationListener l, String klass, int idx, String log, InOrder io) {
        final String name = String.format("testMethod%d", idx);
        final TestDescription test = new TestDescription(klass, name);
        io.verify(l).testStarted(test);
        io.verify(l).testLog(Mockito.eq(log), Mockito.eq(LogDataType.UNKNOWN), Mockito.any());
        io.verify(l).testEnded(Mockito.eq(test), Mockito.<HashMap<String, Metric>>any());
    }

    private void testFailExpectations(
            ITestInvocationListener l, String klass, int idx, InOrder io) {
        final String name = String.format("testMethod%d", idx);
        final TestDescription test = new TestDescription(klass, name);
        io.verify(l).testStarted(test);
        io.verify(l).testFailed(Mockito.eq(test), Mockito.<String>any());
        io.verify(l).testEnded(Mockito.eq(test), Mockito.<HashMap<String, Metric>>any());
    }

    private void testAssumptionExpectations(
            ITestInvocationListener l, String klass, int idx, InOrder io) {
        final String name = String.format("testMethod%d", idx);
        final TestDescription test = new TestDescription(klass, name);
        io.verify(l).testStarted(test);
        io.verify(l).testAssumptionFailure(Mockito.eq(test), Mockito.<String>any());
        io.verify(l).testEnded(Mockito.eq(test), Mockito.<HashMap<String, Metric>>any());
    }

    private void testIgnoredExpectations(
            ITestInvocationListener l, String klass, int idx, InOrder io) {
        final String name = String.format("testMethod%d", idx);
        final TestDescription test = new TestDescription(klass, name);
        io.verify(l).testStarted(test);
        io.verify(l).testIgnored(Mockito.eq(test));
        io.verify(l).testEnded(Mockito.eq(test), Mockito.<HashMap<String, Metric>>any());
    }
}
