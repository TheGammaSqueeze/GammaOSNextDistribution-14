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

package com.android.tradefed.result;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.jimfs.Jimfs;
import com.google.common.truth.StringSubject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@RunWith(JUnit4.class)
public final class BazelXmlResultReporterTest {

    private static final IInvocationContext DEFAULT_CONTEXT = createContext();
    private static final TestDescription TEST_ID = new TestDescription("FooTest", "testFoo");
    private static final String STACK_TRACE = "this is a trace";

    private final FileSystem mFileSystem = Jimfs.newFileSystem();
    private final HashMap<String, Metric> mEmptyMap = new HashMap<>();

    @Test
    public void writeResultPassed_testPassed() throws Exception {
        Path xmlFile = createXmlFilePath();
        BazelXmlResultReporter reporter = createReporter(xmlFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID, 0L);
        reporter.testEnded(TEST_ID, 10L, mEmptyMap);
        reporter.testRunEnded(20L, mEmptyMap);
        reporter.invocationEnded(30L);

        assertXmlFileContainsTagWithAttribute(xmlFile, "testcase", "result", "passed");
    }

    @Test
    public void writeStackTrace_testFailed() throws Exception {
        Path xmlFile = createXmlFilePath();
        BazelXmlResultReporter reporter = createReporter(xmlFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID, 0L);
        reporter.testFailed(TEST_ID, "this is a trace");
        reporter.testEnded(TEST_ID, 10L, mEmptyMap);
        reporter.testRunEnded(20L, mEmptyMap);
        reporter.invocationEnded(30L);

        assertThatFileContents(xmlFile).contains("<![CDATA[this is a trace]]>");
    }

    @Test
    public void noWriteTestCase_testIgnored() throws Exception {
        Path xmlFile = createXmlFilePath();
        BazelXmlResultReporter reporter = createReporter(xmlFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID, 0L);
        reporter.testIgnored(TEST_ID);
        reporter.testEnded(TEST_ID, 10L, mEmptyMap);
        reporter.testRunEnded(20L, mEmptyMap);
        reporter.invocationEnded(30L);

        assertThatFileContents(xmlFile).doesNotContain("<testcase");
    }

    @Test
    public void writeTestCaseResultIncomplete_runFailed() throws Exception {
        Path xmlFile = createXmlFilePath();
        BazelXmlResultReporter reporter = createReporter(xmlFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID, 0L);
        reporter.testRunFailed("Error Message");
        reporter.invocationEnded(30L);

        assertXmlFileContainsTagWithAttribute(xmlFile, "testcase", "result", "incomplete");
    }

    @Test
    public void writeSkipped_testAssumptionFailure() throws Exception {
        Path xmlFile = createXmlFilePath();
        BazelXmlResultReporter reporter = createReporter(xmlFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID, 0L);
        reporter.testAssumptionFailure(TEST_ID, "Error Message");
        reporter.testEnded(TEST_ID, 10L, mEmptyMap);
        reporter.testRunEnded(20L, mEmptyMap);
        reporter.invocationEnded(30L);

        assertThatFileContents(xmlFile).contains("<skipped");
    }

    @Test
    public void writeTestCount_multipleTests() throws Exception {
        Path xmlFile = createXmlFilePath();
        BazelXmlResultReporter reporter = createReporter(xmlFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 3);
        // A failed test.
        reporter.testStarted(TEST_ID, 0L);
        reporter.testFailed(TEST_ID, "this is a trace");
        reporter.testEnded(TEST_ID, 10L, mEmptyMap);
        // A skipped test.
        TestDescription skippedTest = new TestDescription("FooTest", "testSkipped");
        reporter.testStarted(skippedTest, 10L);
        reporter.testAssumptionFailure(skippedTest, "Error Message");
        reporter.testEnded(skippedTest, 20L, mEmptyMap);
        // An ignored test.
        TestDescription ignoredTest = new TestDescription("FooTest", "testIgnored");
        reporter.testStarted(ignoredTest, 20L);
        reporter.testIgnored(ignoredTest);
        reporter.testEnded(ignoredTest, 30L, mEmptyMap);
        reporter.testRunEnded(30L, mEmptyMap);
        reporter.invocationEnded(30L);

        assertXmlFileContainsTagWithAttribute(xmlFile, "testsuite", "tests", "3");
        assertXmlFileContainsTagWithAttribute(xmlFile, "testsuite", "failures", "1");
        assertXmlFileContainsTagWithAttribute(xmlFile, "testsuite", "skipped", "1");
        assertXmlFileContainsTagWithAttribute(xmlFile, "testsuite", "disabled", "1");
    }

    private static IInvocationContext createContext() {
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo("1", "test"));
        context.setTestTag("test");
        return context;
    }

    private static StringSubject assertThatFileContents(Path filePath) throws IOException {
        return assertThat(Files.readString(filePath));
    }

    private static void assertXmlFileContainsTagWithAttribute(
            Path filePath, String tagName, String attributeName, String attributeValue)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = factory.newDocumentBuilder();
        Document doc = dBuilder.parse(Files.newInputStream(filePath));
        doc.getDocumentElement().normalize();
        assertNodeContainsAttribute(
                doc.getElementsByTagName(tagName).item(0), attributeName, attributeValue);
    }

    private static void assertNodeContainsAttribute(
            Node node, String attributeName, String attributeValue) {
        assertEquals(
                node.getAttributes().getNamedItem(attributeName).getNodeValue(), attributeValue);
    }

    private Path createXmlFilePath() {
        return mFileSystem.getPath("/tmp/test.xml");
    }

    private BazelXmlResultReporter createReporter(Path path) throws Exception {
        BazelXmlResultReporter reporter = new BazelXmlResultReporter(mFileSystem);
        OptionSetter setter = new OptionSetter(reporter);
        setter.setOptionValue("file", path.toString());
        return reporter;
    }
}
