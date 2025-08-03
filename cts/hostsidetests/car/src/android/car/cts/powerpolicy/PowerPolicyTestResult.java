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

import static com.google.common.truth.Truth.assertWithMessage;

public final class PowerPolicyTestResult {
    private final PowerPolicyTestAnalyzer mTestAnalyzer;
    private final TestResultTable mExpected = new TestResultTable();
    private TestResultTable mStartSnapshot;
    private TestResultTable mEndSnapshot;

    public PowerPolicyTestResult(PowerPolicyTestAnalyzer testAnalyzer) {
        mTestAnalyzer = testAnalyzer;
    }

    /**
     * Adds test passing criteria.
     *
     * <p> For multiple criteria, the order of adding them into this object matters.
     */
    public void addCriteria(String testcase, String action, String subject, String data)
            throws Exception {
        if (testcase == null || action == null) {
            throw new IllegalArgumentException("testcase and action should not be null");
        }
        if (data == null) {
            data = "null";
        }
        mExpected.add(testcase, action, subject, data);
    }

    public void takeStartSnapshot() throws Exception {
        if (mStartSnapshot != null) {
            return;
        }
        mStartSnapshot = mTestAnalyzer.snapshotTestResult();
    }

    public void takeEndSnapshot() throws Exception {
        if (mEndSnapshot != null) {
            return;
        }
        mEndSnapshot = mTestAnalyzer.snapshotTestResult();
    }

    public void checkFullTestResult() throws Exception {
        TestResultTable testResult;
        if (mStartSnapshot == null || mEndSnapshot == null) {
            throw new IllegalArgumentException("start snapshot or end snapshot is null");
        }
        testResult = mTestAnalyzer.getTailDiff(mStartSnapshot, mEndSnapshot);
        if (testResult == null) {
            throw new IllegalArgumentException("empty test result");
        }
        assertWithMessage("checkFullTestresult")
                .that(mTestAnalyzer.checkIfTestResultMatch(mExpected, testResult, false))
                .isTrue();
    }

    public void checkLastTestResultEntry(String testcase, String action,
            String subject, PowerPolicyDef policy) throws Exception {
        TestResultTable.RecordEntry lastEntry = mTestAnalyzer.snapshotTestResult().getLastEntry();
        assertWithMessage("checkLastTestEntry with policy data")
                .that(lastEntry.equalsWithPowerPolicyData(testcase, action, subject, policy))
                .isTrue();
    }

    public void checkLastTestResultEntry(String testcase, String action,
            String subject, String data) throws Exception {
        TestResultTable expected = new TestResultTable();
        expected.add(testcase, action, subject, data);
        TestResultTable.RecordEntry lastEntry1 = expected.getLastEntry();
        TestResultTable.RecordEntry lastEntry2 = mTestAnalyzer.snapshotTestResult().getLastEntry();
        assertWithMessage("checkLastTestEntry with string data")
                .that(lastEntry1.equals(lastEntry2)).isTrue();
    }

    public boolean checkLastTestResultEntryData(String testcase, String action,
            String subject, String data) throws Exception {
        TestResultTable expected = new TestResultTable();
        expected.add(testcase, action, subject, data);
        TestResultTable.RecordEntry lastEntry1 = expected.getLastEntry();
        TestResultTable.RecordEntry lastEntry2 = mTestAnalyzer.snapshotTestResult().getLastEntry();
        return lastEntry1.equalsIgnoreSubject(lastEntry2);
    }
}
