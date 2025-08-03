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

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.annotations.VisibleForTesting;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * A custom Tradefed reporter for Bazel XML result reporting.
 *
 * <p>This custom result reporter generates a test.xml file. The file contains detailed test case
 * results and is written to the location provided in the Bazel XML_OUTPUT_FILE environment
 * variable. The file is required for reporting detailed test results to AnTS via Bazel's BES
 * protocol. The XML schema is based on the JUnit test result schema. See
 * https://windyroad.com.au/dl/Open%20Source/JUnit.xsd for more details.
 */
@OptionClass(alias = "bazel-xml-result-reporter")
public final class BazelXmlResultReporter implements ITestInvocationListener {
    private final FileSystem mFileSystem;
    private TestRunResult mTestRunResult = new TestRunResult();

    // This is not a File object in order to use an in-memory FileSystem in tests.
    // Using Path would have been more appropriate but Tradefed does not support
    // option fields of that type.
    @Option(name = "file", mandatory = true, description = "Bazel XML file")
    private String mXmlFile;

    @VisibleForTesting
    BazelXmlResultReporter(FileSystem fs) {
        this.mFileSystem = fs;
    }

    public BazelXmlResultReporter() {
        this(FileSystems.getDefault());
    }

    @Override
    public void testRunStarted(String name, int numTests) {
        testRunStarted(name, numTests, 0);
    }

    @Override
    public void testRunStarted(String name, int numTests, int attemptNumber) {
        testRunStarted(name, numTests, attemptNumber, System.currentTimeMillis());
    }

    @Override
    public void testRunStarted(String name, int numTests, int attemptNumber, long startTime) {
        mTestRunResult.testRunStarted(name, numTests, startTime);
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        mTestRunResult.testRunEnded(elapsedTime, runMetrics);
    }

    @Override
    public void testRunFailed(String errorMessage) {
        mTestRunResult.testRunFailed(errorMessage);
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        mTestRunResult.testRunFailed(failure);
    }

    @Override
    public void testRunStopped(long elapsedTime) {
        mTestRunResult.testRunStopped(elapsedTime);
    }

    @Override
    public void testStarted(TestDescription test) {
        testStarted(test, System.currentTimeMillis());
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        mTestRunResult.testStarted(test, startTime);
    }

    @Override
    public void testEnded(TestDescription test, HashMap<String, Metric> testMetrics) {
        testEnded(test, System.currentTimeMillis(), testMetrics);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        mTestRunResult.testEnded(test, endTime, testMetrics);
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        mTestRunResult.testFailed(test, trace);
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        mTestRunResult.testFailed(test, failure);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        mTestRunResult.testAssumptionFailure(test, trace);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        mTestRunResult.testAssumptionFailure(test, failure);
    }

    @Override
    public void testIgnored(TestDescription test) {
        mTestRunResult.testIgnored(test);
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        writeXmlFile();
    }

    private void writeXmlFile() {
        try (OutputStream os = createOutputStream(); ) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            doc.setXmlStandalone(true);
            // Pretty print XML file with indentation.
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            writeTestResult(doc, mTestRunResult);

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(os);
            transformer.transform(source, result);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write test.xml file", e);
        } catch (TransformerException | ParserConfigurationException e) {
            throw new RuntimeException("Failed to write test.xml file", e);
        }

        CLog.logAndDisplay(LogLevel.INFO, "Test XML file generated at %s.", mXmlFile);
    }

    private OutputStream createOutputStream() throws IOException {
        Path path = mFileSystem.getPath(mXmlFile);
        Files.createDirectories(path.getParent());
        return Files.newOutputStream(path);
    }

    private void writeTestResult(Document doc, TestRunResult testRunResult) {
        // There should be only one top-level testsuites element.
        Element testSuites = writeTestSuites(doc, testRunResult);
        doc.appendChild(testSuites);

        Element testSuite = writeTestSuite(doc, testRunResult);
        testSuites.appendChild(testSuite);
        // We use a TreeMap to iterate over entries for deterministic output.
        Map<TestDescription, TestResult> testResults =
                new TreeMap<TestDescription, TestResult>(testRunResult.getTestResults());

        for (Map.Entry<TestDescription, TestResult> testEntry : testResults.entrySet()) {
            if (testEntry.getValue().getStatus().equals(TestStatus.IGNORED)) {
                continue;
            }
            testSuite.appendChild(writeTestCase(doc, testEntry.getKey(), testEntry.getValue()));
        }
    }

    private Element writeTestSuites(Document doc, TestRunResult testRunResult) {
        Element testSuites = doc.createElementNS(null, "testsuites");

        writeStringAttribute(testSuites, "name", testRunResult.getName());
        writeTimestampAttribute(testSuites, "timestamp", testRunResult.getStartTime());

        return testSuites;
    }

    private Element writeTestSuite(Document doc, TestRunResult testRunResult) {
        Element testSuite = doc.createElementNS(null, "testsuite");

        writeStringAttribute(testSuite, "name", testRunResult.getName());
        writeTimestampAttribute(testSuite, "timestamp", testRunResult.getStartTime());

        writeIntAttribute(testSuite, "tests", testRunResult.getNumTests());
        writeIntAttribute(
                testSuite, "failures", testRunResult.getNumTestsInState(TestStatus.FAILURE));
        // The tests were not run to completion because the tests decided that they should
        // not be run(example: due to a failed assumption in a JUnit4-style tests). Some per-test
        // setup or tear down may or may not have occurred for tests with this result.
        writeIntAttribute(
                testSuite,
                "skipped",
                testRunResult.getNumTestsInState(TestStatus.ASSUMPTION_FAILURE));
        // The tests were disabled with DISABLED_ (gUnit) or @Ignore (JUnit).
        writeIntAttribute(
                testSuite, "disabled", testRunResult.getNumTestsInState(TestStatus.IGNORED));

        writeDurationAttribute(testSuite, "time", testRunResult.getElapsedTime());

        return testSuite;
    }

    private Element writeTestCase(Document doc, TestDescription description, TestResult result) {
        TestStatus status = result.getStatus();
        Element testCase = doc.createElement("testcase");

        writeStringAttribute(testCase, "name", description.getTestName());
        writeStringAttribute(testCase, "classname", description.getClassName());
        writeDurationAttribute(testCase, "time", result.getEndTime() - result.getStartTime());

        writeStringAttribute(testCase, "status", "run");
        writeStringAttribute(testCase, "result", status.toString().toLowerCase());

        if (status.equals(TestStatus.FAILURE)) {
            testCase.appendChild(writeStackTraceTag(doc, "failure", result.getStackTrace()));
        } else if (status.equals(TestStatus.ASSUMPTION_FAILURE)) {
            testCase.appendChild(writeStackTraceTag(doc, "skipped", result.getStackTrace()));
        }

        return testCase;
    }

    private static Node writeStackTraceTag(Document doc, String tag, String stackTrace) {
        Element node = doc.createElement(tag);
        CDATASection cdata = doc.createCDATASection(stackTrace);
        node.appendChild(cdata);
        return node;
    }

    private static void writeStringAttribute(
            Element element, String attributeName, String attributeValue) {
        element.setAttribute(attributeName, attributeValue);
    }

    private static void writeIntAttribute(
            Element element, String attributeName, int attributeValue) {
        element.setAttribute(attributeName, String.valueOf(attributeValue));
    }

    private static void writeTimestampAttribute(
            Element element, String attributeName, long timestampInMillis) {
        element.setAttribute(attributeName, formatTimestamp(timestampInMillis));
    }

    private static void writeDurationAttribute(Element element, String attributeName, long millis) {
        element.setAttribute(attributeName, formatRunTime(millis));
    }

    private static String formatRunTime(Long runTimeInMillis) {
        return String.valueOf(runTimeInMillis / 1000.0D);
    }

    // Return an ISO 8601 combined date and time string for a specified timestamp.
    private static String formatTimestamp(Long timestampInMillis) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(new Date(timestampInMillis));
    }
}
