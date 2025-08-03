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

package android.car.cts.powerpolicy;

import android.car.cts.PowerPolicyHostTest;

public final class PowerPolicyTestAnalyzer {
    private static final String TEST_RESULT_PREFIX = "PowerPolicyTestResult";
    private final PowerPolicyHostTest mHostTest;

    public PowerPolicyTestAnalyzer(PowerPolicyHostTest hostTest) {
        mHostTest = hostTest;
    }

    /**
     * Compares results.
     */
    public static boolean checkIfTestResultMatch(TestResultTable result1,
            TestResultTable result2, boolean withPowerPolicyData) {
        if ((result1 == null || result2 == null)) {
            return result1 == result2;
        }

        int size = result1.size();
        if (size != result2.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            TestResultTable.RecordEntry entry1 = result1.get(i);
            TestResultTable.RecordEntry entry2 = result2.get(i);
            boolean status = withPowerPolicyData
                    ? entry1.equalsWithPowerPolicyData(entry2) : entry1.equals(entry2);
            if (!status) {
                return false;
            }
        }
        return true;
    }

    public TestResultTable snapshotTestResult() throws Exception {
        String shellOutput = mHostTest.fetchActivityDumpsys();
        return getTestResults(shellOutput, false);
    }

    public TestResultTable getLatestTestResultEntry() throws Exception {
        String shellOutput = mHostTest.fetchActivityDumpsys();
        return getTestResults(shellOutput, true);
    }

    private TestResultTable getTestResults(String shellOutput, boolean onlyLastEntry)
            throws Exception {
        TestResultTable snapshot = new TestResultTable();
        String[] lines = shellOutput.split("\n");

        int i = 0;
        if (onlyLastEntry) {
            i = lines.length == 0 ? 0 : lines.length - 1;
        }
        for (; i < lines.length; i++) {
            parseAndAddTestEntry(snapshot, lines[i]);
        }

        return snapshot;
    }

    private void parseAndAddTestEntry(TestResultTable results, String line) throws Exception {
        if (!line.trim().startsWith(TEST_RESULT_PREFIX)) {
            return;
        }

        String[] tokens = line.split("##");
        if (tokens.length != 2) {
            throw new IllegalArgumentException("malformatted result entry: " + line);
        }
        String header = tokens[0].trim();
        String data = tokens[1].trim();
        String[] hdrTokens = header.split(":\\s*");
        if (hdrTokens.length != 3 && hdrTokens.length != 4) {
            throw new IllegalArgumentException("malformatted result header: " + line);
        }

        String subject = hdrTokens.length == 3 ? null : hdrTokens[3];
        results.add(hdrTokens[1], hdrTokens[2], subject, data);
    }

    /**
     * Subtract the common front TestResultEntry items.
     */
    public static TestResultTable getDiff(TestResultTable result1, TestResultTable result2) {
        TestResultTable diff;

        if (result1 != null && result2 != null) {
            TestResultTable longResult = result1;
            TestResultTable shortResult = result2;
            if (longResult.size() < shortResult.size()) {
                longResult = result2;
                shortResult = result1;
            }
            int shortSize = shortResult.size();
            int longSize = longResult.size();
            int idx = 0;
            diff = new TestResultTable();
            for (; idx < shortSize; idx++) {
                if (!shortResult.get(idx).equals(longResult.get(idx))) {
                    break;
                }
            }
            for (; idx < longSize; idx++) {
                diff.add(longResult.get(idx));
            }
        } else if (result1 == null) {
            diff = result2;
        } else {
            diff = result1;
        }
        return diff;
    }

    public TestResultTable getTailDiff(TestResultTable result1, TestResultTable result2) {
        TestResultTable diff;

        if (result1 != null && result2 != null) {
            TestResultTable longResult = result1;
            TestResultTable shortResult = result2;
            if (longResult.size() < shortResult.size()) {
                longResult = result2;
                shortResult = result1;
            }
            int shortSize = shortResult.size();
            int longSize = longResult.size();
            diff = new TestResultTable();
            for (int idx = shortSize; idx < longSize; idx++) {
                diff.add(longResult.get(idx));
            }
        } else if (result1 == null) {
            diff = result2;
        } else {
            diff = result1;
        }
        return diff;
    }
}
