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
package com.android.tradefed.testtype.rust;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.ArrayUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;

/** Unit tests for {@link RustTestResultParser}. */
@RunWith(JUnit4.class)
public class RustTestResultParserTest extends RustParserTestBase {

    private RustTestResultParser mParser;
    @Mock ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mParser = new RustTestResultParser(ArrayUtil.list(mMockListener), "test");
    }

    @Test
    public void testRegexTestCase() {
        String[] goodPatterns = {
            "test some_test ... ok <0.000s>",
            "test some_test ... ok",
            "test some_test  ... ok <0.000s>",
            "test some_test  ... ok",
            "test some_test - should panic ... ok <0.000s>",
            "test some_test - should panic  ... ok <0.000s>",
            "test some_test ... pass <0.000s>",
            "test some_test ... pass",
            "test other-test ... fail <0.000s>",
            "test other-test ... fail",
            "test any ... FAIL <0.000s>",
            "test some_test ... ignored",
            "test some_test  ... ignored",
            "test some_test ... other_strings <0.000s>",
            "test some_test ... ok <0.001s>",
            "test some_test ... ok <42.42s>",
        };
        String[] wrongPatterns = {
            " test some_test ... ok <0.000s>",
            "test some_test  - should panic ... ok <0.000s>",
            "test some_test -  should panic ... ok <0.000s>",
            "test some_test - something else ... ok <0.000s>",
            "test  other-test ... fail <0.000s>",
            "test any ...  FAIL",
            "test some_test .. other",
            "test some_test ... other strings",
            "test some_test .... ok",
            "test some_test ... ok <42s>",
            "test some_test ... ok <42>",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.RUST_ONE_LINE_RESULT.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.RUST_ONE_LINE_RESULT.matcher(s).matches());
        }
    }

    @Test
    public void testRegexRunSummary() {
        String[] goodPatterns = {
            "test result: some_test 0 passed; 0 failed; 15 ignored; ...",
            "test result: some_test 10 passed; 21 failed; 0 ignored;",
            "test result: any string here 2 passed; 0 failed; 0 ignored;...",
        };
        String[] wrongPatterns = {
            "test result: some_test 0 passed 0 failed 15 ignored; ...",
            "test result: some_test 10 passed; 21 failed;",
            "test some_test 0 passed; 0 failed; 15 ignored; ...",
            "  test result: some_test 10 passed; 21 failed;",
            "test result here 2 passed; 0 failed; 0 ignored...",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.COMPLETE_PATTERN.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.COMPLETE_PATTERN.matcher(s).matches());
        }
    }

    @Test
    public void testRegexRunning() {
        String[] goodPatterns = {
            "running 1 test", "running 42 tests",
        };
        String[] wrongPatterns = {
            "  running 42 tests", "running tests",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.RUNNING_PATTERN.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.RUNNING_PATTERN.matcher(s).matches());
        }
    }

    @Test
    public void testRegexTestFail() {
        String[] goodPatterns = {
            "---- tests::test_foo stdout ----", "---- test_foo stdout ----",
        };
        String[] wrongPatterns = {
            "--- tests::test_foo stdout ---",
            "tests::test_foo stdout",
            "--- tests::test_foo stderr ---",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.TEST_FAIL_PATTERN.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.TEST_FAIL_PATTERN.matcher(s).matches());
        }
    }

    @Test
    public void testRegexFailures() {
        String[] goodPatterns = {
            "failures:",
        };
        String[] wrongPatterns = {
            "not failures:", " failures:", " failures: something",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.FAILURES_PATTERN.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.FAILURES_PATTERN.matcher(s).matches());
        }
    }

    @Test
    public void testParseRealOutput() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_1);

        mParser.processNewLines(contents);
        mParser.done();

        verify(mMockListener, times(10)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(10))
                .testEnded(
                        Mockito.any(), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testParseRealOutput2() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_2);
        String identsTrace =
                String.join(
                        "\n",
                        "thread 'idents' panicked at 'assertion failed: `(left == right)`",
                        "  left: `\"_\"`,",
                        " right: `\"_abc\"`', external/rust/crates/proc-macro2/tests/test.rs:13:5");

        String literalStringTrace =
                String.join(
                        "\n",
                        "thread 'literal_string' panicked at 'assertion failed: `(left == right)`",
                        "  left: `\"\\\"didn\\'t\\\"\"`,",
                        " right: `\"fake\"`', external/rust/crates/proc-macro2/tests/test.rs:86:5");

        mParser.processNewLines(contents);
        mParser.done();

        verify(mMockListener, times(23)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(23))
                .testEnded(
                        Mockito.any(), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(new TestDescription("test", "idents")), Mockito.eq(identsTrace));
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(new TestDescription("test", "literal_string")),
                        Mockito.eq(literalStringTrace));
    }

    @Test
    public void testParseRealOutput3() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_3);

        mParser.processNewLines(contents);
        mParser.done();

        verify(mMockListener).testStarted(Mockito.any());
        verify(mMockListener).testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testIgnored(Mockito.eq(new TestDescription("test", "make_sure_no_proc_macro")));
    }

    /**
     * Tests may not return all their output in a single call to processNewLines. This tests that we
     * properly parse output split across several calls.
     */
    @Test
    public void testParsePartialOutput() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_1);

        mParser.processNewLines(Arrays.copyOfRange(contents, 0, 4));
        mParser.processNewLines(Arrays.copyOfRange(contents, 4, 7));
        mParser.processNewLines(Arrays.copyOfRange(contents, 4, contents.length));
        mParser.done();

        verify(mMockListener, times(10)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(10))
                .testEnded(
                        Mockito.any(), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testParseLinkError() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_4);

        mParser.processNewLines(contents);
        mParser.done();

        verify(mMockListener)
                .testRunFailed(
                        Mockito.eq(
                                "test did not report any run:\n"
                                    + "CANNOT LINK EXECUTABLE"
                                    + " \"/data/local/tmp/keystore2_test/x86_64/keystore2_test\":"
                                    + " library \"libkeystore2_crypto.so\" not found: needed by"
                                    + " main executable"));
    }

    @Test
    public void testParseTimeout() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_5);

        mParser.processNewLines(contents);
        mParser.done();

        verify(mMockListener, times(9)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(9))
                .testEnded(
                        Mockito.any(), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunFailed(Mockito.eq("Test run incomplete. Started 10 tests, finished 9"));
    }
}
