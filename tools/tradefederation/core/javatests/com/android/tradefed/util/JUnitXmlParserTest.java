/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tradefed.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;

/** Simple unit tests for {@link JUnitXmlParser}. */
@RunWith(JUnit4.class)
public class JUnitXmlParserTest {
    private static final String TEST_PARSE_FILE = "JUnitXmlParserTest_testParse.xml";
    private static final String TEST_PARSE_FILE2 = "JUnitXmlParserTest_error.xml";
    private static final String TEST_PARSE_FILE3 = "JUnitXmlParserTest_error2.xml";
    private static final String BAZEL_SH_TEST_XML = "JUnitXmlParserTest_bazelShTest.xml";

    @Mock ITestInvocationListener mMockListener;
    @Captor ArgumentCaptor<FailureDescription> failureCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /** Test behavior when data to parse is empty */
    @Test
    public void testEmptyParse() {
        try {
            new JUnitXmlParser(mMockListener).parse(new ByteArrayInputStream(new byte[0]));
            fail("ParseException not thrown");
        } catch (ParseException e) {
            // expected
        }
    }

    /** Simple success test for xml parsing */
    @Test
    public void testParse() throws ParseException {
        TestDescription test1 = new TestDescription("PassTest", "testPass");
        TestDescription test2 = new TestDescription("PassTest", "testPass2");
        TestDescription test3 = new TestDescription("FailTest", "testFail");

        new JUnitXmlParser("runName", mMockListener).parse(extractTestXml(TEST_PARSE_FILE));

        verify(mMockListener).testRunStarted("runName", 3);
        verify(mMockListener).testStarted(test1);
        verify(mMockListener).testEnded(test1, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test2);
        verify(mMockListener).testEnded(test2, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test3);
        verify(mMockListener).testFailed(Mockito.eq(test3), failureCaptor.capture());
        FailureDescription failure = failureCaptor.getValue();
        assertTrue(failure.getErrorMessage().contains("java.lang.NullPointerException"));
        verify(mMockListener).testEnded(test3, new HashMap<String, Metric>());
        verify(mMockListener).testRunEnded(5000L, new HashMap<String, Metric>());
    }

    /** Test parsing the <error> and <skipped> tag in the junit xml. */
    @Test
    public void testParseErrorAndSkipped() throws ParseException {
        TestDescription test1 = new TestDescription("PassTest", "testPass");
        TestDescription test2 = new TestDescription("SkippedTest", "testSkip");
        TestDescription test3 = new TestDescription("ErrorTest", "testFail");

        new JUnitXmlParser(mMockListener).parse(extractTestXml(TEST_PARSE_FILE2));
        FailureDescription failure =
                FailureDescription.create(
                        "java.lang.NullPointerException\n    "
                                + "at FailTest.testFail:65\n        ",
                        FailureStatus.TEST_FAILURE);

        verify(mMockListener).testRunStarted("suiteName", 3);
        verify(mMockListener).testStarted(test1);
        verify(mMockListener).testEnded(test1, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test2);
        verify(mMockListener).testIgnored(test2);
        verify(mMockListener).testEnded(test2, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test3);
        verify(mMockListener).testFailed(Mockito.eq(test3), Mockito.eq(failure));
        verify(mMockListener).testEnded(test3, new HashMap<String, Metric>());
        verify(mMockListener).testRunEnded(918686L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseError_format() throws ParseException {
        TestDescription test1 = new TestDescription("JUnitXmlParser", "normal_integration_tests");

        new JUnitXmlParser(mMockListener).parse(extractTestXml(TEST_PARSE_FILE3));
        FailureDescription failure =
                FailureDescription.create(
                        "exited with error code 134.", FailureStatus.TEST_FAILURE);

        verify(mMockListener).testRunStarted("normal_integration_tests", 1);
        verify(mMockListener).testStarted(test1);
        verify(mMockListener).testFailed(Mockito.eq(test1), Mockito.eq(failure));
        verify(mMockListener).testEnded(test1, new HashMap<String, Metric>());
        verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
    }

    /** Test parsing the XML from an sh_test rule in Bazel. */
    @Test
    public void testParseBazelShTestXml() throws ParseException {
        TestDescription test =
                new TestDescription(
                        JUnitXmlParser.class
                                .getSimpleName(), // TODO(b/120500865): remove this kludge
                        "pkg/target");

        new JUnitXmlParser("//pkg:target", mMockListener).parse(extractTestXml(BAZEL_SH_TEST_XML));

        verify(mMockListener).testRunStarted("//pkg:target", 1);
        verify(mMockListener).testStarted(test);
        verify(mMockListener).testEnded(test, new HashMap<String, Metric>());
        verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
    }

    private InputStream extractTestXml(String fileName) {
        return getClass().getResourceAsStream(File.separator + "util" + File.separator + fileName);
    }
}
