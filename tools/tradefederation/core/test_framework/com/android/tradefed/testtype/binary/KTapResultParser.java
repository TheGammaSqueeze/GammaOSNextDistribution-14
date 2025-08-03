/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tradefed.testtype.binary;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads KTAP output as that produced by a KUnit test module and placed in a `results` file under
 * debugfs.
 *
 * <p>This implementation is based off of the official documentation, kunit_parser.py and specific
 * caveats found during testing. Additional logic needed:
 *
 * <ul>
 *   <li>Indentation is ignored because it's not consistent across usage.
 *   <li>Line starting with "# Subtest:" is required to properly nest into subtest groups. This
 *       approach was taken from kunit_parser.py.
 *   <li>Sometimes a "- " proceeds the test name and diagnostic data when a '#' isn't used. When
 *       it's encountered it's stripped off.
 *   <li>The test name can technically have any character besides '#'. This will probably become an
 *       issue when getting translated to TF test results. For now only post processing is to
 *       replace spaces with underscores.
 * </ul>
 *
 * @See <a href="The Kernel Test Anything Protocol (KTAP), version
 * 1">https://docs.kernel.org/dev-tools/ktap.html</a>
 */
public class KTapResultParser {

    public enum ParseResolution {
        INDIVIDUAL_LEAVES,
        AGGREGATED_TOP_LEVEL,
    }

    enum ResultDirective {
        NOTSET,
        SKIP,
        TODO,
        XFAIL,
        TIMEOUT,
        ERROR
    }

    @VisibleForTesting
    static class TestResult {
        public String version;
        public boolean expectSubtests;
        public int numberOfSubtests;
        public List<TestResult> subtests;
        public boolean isOk;
        public int testNum;
        public String name;
        public ResultDirective directive;
        public String diagnosticData;
        public List<String> diagnosticLines;
        public boolean isRoot;

        public TestResult() {
            numberOfSubtests = 0;
            subtests = new ArrayList<TestResult>();
            diagnosticLines = new ArrayList<String>();
        }

        public String createDiagnosticTrace() {
            return String.format("%s\n%s", String.join("\n", diagnosticLines), diagnosticData)
                    .trim();
        }

        public String toStringWithSubtests(String prefix) {
            StringBuilder builder = new StringBuilder();
            builder.append(prefix + name + System.lineSeparator());
            for (TestResult tr : subtests) {
                builder.append(tr.toStringWithSubtests(prefix + "   "));
            }
            return builder.toString();
        }
    }

    static final String DIR_OPTIONS =
            String.format(
                    "%s",
                    Arrays.toString(
                                    new ResultDirective[] {
                                        ResultDirective.SKIP,
                                        ResultDirective.TODO,
                                        ResultDirective.XFAIL,
                                        ResultDirective.TIMEOUT,
                                        ResultDirective.ERROR
                                    })
                            .replace("[", "")
                            .replace(", ", "|")
                            .replace("]", ""));

    // Regex Group Names
    static final String SUBTEST_NAME = "name";
    static final String SUBTEST_COUNT = "count";
    static final String OK_OR_NOT = "okOrNot";
    static final String TEST_NUM = "testNum";
    static final String TEST_NAME = "testName";
    static final String DIRECTIVE = "directive";
    static final String DIAGNOSTIC = "diagnosticData";

    static final Pattern VERSION_PATTERN = Pattern.compile("^\\s*K?TAP version.*");
    static final Pattern SUBTEST_PATTERN =
            Pattern.compile(String.format("^\\s*# Subtest: (?<%s>\\S+)", SUBTEST_NAME));
    static final Pattern PLAN_PATTERN =
            Pattern.compile(String.format("^\\s*1..(?<%s>\\d+)", SUBTEST_COUNT));
    static final Pattern TEST_CASE_RESULT_PATTERN =
            Pattern.compile(
                    String.format(
                            "^\\s*(?<%s>ok|not ok)\\s+(?<%s>\\d+)\\s+(-"
                                    + " )?(?<%s>[^#]+)?(\\s*#\\s+)?(?<%s>%s)?\\s*(?<%s>.*)?$",
                            OK_OR_NOT, TEST_NUM, TEST_NAME, DIRECTIVE, DIR_OPTIONS, DIAGNOSTIC));

    static final Predicate<String> IS_VERSION = VERSION_PATTERN.asPredicate();
    static final Predicate<String> IS_SUBTEST = SUBTEST_PATTERN.asPredicate();
    static final Predicate<String> IS_PLAN = PLAN_PATTERN.asPredicate();
    static final Predicate<String> IS_TEST_CASE_RESULT = TEST_CASE_RESULT_PATTERN.asPredicate();

    public static void applyKTapResultToListener(
            ITestInvocationListener listener,
            String testRunName,
            String ktapFileContent,
            ParseResolution resolution) {
        KTapResultParser parser = new KTapResultParser();
        TestResult root = parser.processResultsFileContent(ktapFileContent);
        parser.applyToListener(listener, testRunName, root, resolution);
    }

    @VisibleForTesting
    TestResult processResultsFileContent(String fileContent) {
        TestResult root = new TestResult();
        root.isRoot = true;
        root.expectSubtests = true;
        Iterator<String> lineIterator = fileContent.lines().iterator();
        processLines(root, lineIterator);
        return root;
    }

    private void processLines(TestResult currentTest, Iterator<String> lineIterator) {
        if (!lineIterator.hasNext()) {
            // Reached the end of file, confirm that the final test is in fact the root test
            if (!currentTest.isRoot) {
                throw new RuntimeException(
                        String.format("Incomplete KTap results. All tests not closed properly."));
            }
            return;
        }

        String line = lineIterator.next().trim();

        if (IS_VERSION.test(line)) {
            if (currentTest.isRoot && currentTest.version == null || !currentTest.expectSubtests) {
                currentTest.version = line;
            } else {
                TestResult subtest = new TestResult();
                subtest.version = line;
                currentTest.subtests.add(subtest);
                processLines(subtest, lineIterator);
            }
        } else if (IS_SUBTEST.test(line)) {
            Matcher matcher = SUBTEST_PATTERN.matcher(line);
            matcher.matches();

            String subtestName = matcher.group(SUBTEST_NAME);
            if (currentTest.isRoot
                    || (currentTest.expectSubtests && currentTest.numberOfSubtests > 0)) {
                // If we've already seen a PLAN line then this subtest line
                // belongs to a different test.
                TestResult subtest = new TestResult();
                subtest.expectSubtests = true;
                subtest.name = subtestName;
                currentTest.subtests.add(subtest);
                processLines(subtest, lineIterator);
            } else {
                currentTest.expectSubtests = true;
                currentTest.name = subtestName;
            }
        } else if (IS_PLAN.test(line)) {
            Matcher matcher = PLAN_PATTERN.matcher(line);
            matcher.matches();

            currentTest.expectSubtests = true;
            currentTest.numberOfSubtests = Integer.parseInt(matcher.group(SUBTEST_COUNT));
        } else if (IS_TEST_CASE_RESULT.test(line)) {
            Matcher matcher = TEST_CASE_RESULT_PATTERN.matcher(line);
            matcher.matches();

            boolean isOk = matcher.group(OK_OR_NOT).equals("ok");
            int testNum = Integer.parseInt(matcher.group(TEST_NUM));

            String name =
                    matcher.group(TEST_NAME) != null
                            ? matcher.group(TEST_NAME).trim().replace(" ", "_")
                            : String.format("unnamed_test_%d", testNum);
            ResultDirective directive =
                    matcher.group(DIRECTIVE) != null
                            ? ResultDirective.valueOf(matcher.group(DIRECTIVE))
                            : ResultDirective.NOTSET;
            String diagnosticData =
                    matcher.group(DIAGNOSTIC) != null ? matcher.group(DIAGNOSTIC) : "";

            if (name.equals(currentTest.name)
                    || (currentTest.name == null
                            && (currentTest.numberOfSubtests != 0
                                    && currentTest.subtests.size()
                                            == currentTest.numberOfSubtests))) {
                // This test with subtests has completed.
                currentTest.name = name;
                currentTest.isOk = isOk;
                currentTest.testNum = testNum;
                currentTest.directive = directive;
                currentTest.diagnosticData = diagnosticData;
                return; // No more recurse with this test.
            } else {
                // This is a subtest within currentTest.
                TestResult leafSubtest = new TestResult();
                leafSubtest.name = name;
                leafSubtest.isOk = isOk;

                // For situations where the number of subtests is known
                // validate that the testNum is not out of order.
                if (currentTest.numberOfSubtests != 0
                        && testNum != currentTest.subtests.size() + 1) {
                    throw new RuntimeException(
                            String.format(
                                    "Test encountered out of order expected '%d' but received '%d'."
                                            + " Line: '%s'",
                                    currentTest.subtests.size() + 1, testNum, line));
                }
                leafSubtest.testNum = testNum;
                leafSubtest.directive = directive;
                leafSubtest.diagnosticData = diagnosticData;

                // Any diagnostic lines encountered up until this point actually
                // belong to this leaf test. Copy over from the parent and then clear
                // out the parents lines.
                leafSubtest.diagnosticLines.addAll(currentTest.diagnosticLines);
                currentTest.diagnosticLines.clear();
                currentTest.subtests.add(leafSubtest);
            }
        } else if (!line.isEmpty()) {
            // Diagnostic lines or unknown lines.
            currentTest.diagnosticLines.add(line);
        }

        processLines(currentTest, lineIterator);
    }

    private void applyToListener(
            ITestInvocationListener listener,
            String testRunName,
            TestResult root,
            ParseResolution resolution) {
        if (resolution == ParseResolution.INDIVIDUAL_LEAVES) {
            applySubtestLeavesToListener(listener, testRunName, root, "");
        } else if (resolution == ParseResolution.AGGREGATED_TOP_LEVEL) {
            applyAggregatedTopLevelToListener(listener, testRunName, root, "");
        }
    }

    private void applySubtestLeavesToListener(
            ITestInvocationListener listener, String testRunName, TestResult test, String prefix) {
        String testName = prefix == null || prefix.isEmpty() ? test.name : prefix + "." + test.name;
        if (test.subtests.size() > 0) {
            for (TestResult subtest : test.subtests) {
                applySubtestLeavesToListener(listener, testRunName, subtest, testName);
            }
        } else {
            applyTestResultToListener(listener, testRunName, testName, test);
        }
    }

    private void applyAggregatedTopLevelToListener(
            ITestInvocationListener listener,
            String testRunName,
            TestResult root,
            String fullKTapResult) {
        // Here we want to apply a single test result based on the one or more top level KTAP
        // results. If there are more than one top level result, their names are concatenated
        // and pass/fail results are AND'd into a final value.

        if (root.subtests.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid test results in KTAP results. " + fullKTapResult);
        }

        String testName = root.subtests.get(0).name;
        boolean isOk = root.subtests.get(0).isOk;
        for (int i = 1; i < root.subtests.size(); ++i) {
            testName += "." + root.subtests.get(i).name;
            isOk &= root.subtests.get(i).isOk;
        }

        TestDescription testDescription = new TestDescription(testRunName, testName);
        listener.testStarted(testDescription);
        if (!isOk) {
            listener.testFailed(testDescription, fullKTapResult);
        }
        listener.testEnded(testDescription, new HashMap<String, Metric>());
    }

    private void applyTestResultToListener(
            ITestInvocationListener listener,
            String testRunName,
            String testName,
            TestResult test) {
        TestDescription testDescription = new TestDescription(testRunName, testName);
        listener.testStarted(testDescription);
        switch (test.directive) {
            case NOTSET:
                if (!test.isOk) {
                    listener.testFailed(testDescription, test.createDiagnosticTrace());
                }
                break;
            case TIMEOUT:
                // fall through
            case ERROR:
                listener.testFailed(testDescription, test.createDiagnosticTrace());
                if (!test.isOk) {
                    CLog.w(
                            "%s has directive '%s' but also shows 'ok', forcing 'not ok'",
                            testName, test.directive);
                }
                break;
            case SKIP:
                // fall through
            case TODO:
                // fall through
            case XFAIL:
                listener.testIgnored(testDescription);
                break;
        }
        listener.testEnded(testDescription, new HashMap<String, Metric>());
    }
}
