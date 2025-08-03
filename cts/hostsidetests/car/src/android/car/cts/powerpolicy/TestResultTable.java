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

import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;

/**
 * TestResultTable consists of a list of RecordEntry records
 *
 * <p>Each record represents one entry line in the device data file,
 * {@code /storage/emulated/obb/PowerPolicyData.txt}, which records the power
 * state and policy behavior.
 */
public final class TestResultTable {
    private final ArrayList<RecordEntry> mTestResults = new ArrayList<RecordEntry>();

    public int size() {
        return mTestResults.size();
    }

    public RecordEntry get(int i) throws IndexOutOfBoundsException {
        return mTestResults.get(i);
    }

    public RecordEntry getLastEntry() {
        if (mTestResults.isEmpty()) {
            return null;
        }
        return mTestResults.get(mTestResults.size() - 1);
    }

    public void add(RecordEntry entry) {
        mTestResults.add(entry);
    }

    public void add(String testcase, String action, String subject, String data)
            throws Exception {
        if (testcase == null || action == null || data == null) {
            throw new IllegalArgumentException("testcase, action or data can not be null");
        }

        add(new RecordEntry(testcase, action, subject, data));
    }

    @Override
    public String toString() {
        StringBuilder strBuilder = new StringBuilder();
        mTestResults.forEach(l -> strBuilder.append(l).append('\n'));
        return strBuilder.toString();
    }

    static final class RecordEntry {
        private final String mTestcase;
        private final String mAction;
        private final String mSubject;
        private final String mData;

        private RecordEntry(String testcase, String action, String subject, String data) {
            mTestcase = testcase;
            mAction = action;
            mSubject = subject;
            mData = data;
        }

        @Override
        public boolean equals(Object obj) {
            RecordEntry peerEntry;
            if (!(obj instanceof RecordEntry)) {
                return false;
            }
            peerEntry = (RecordEntry) obj;
            if ((mSubject == null && null != peerEntry.mSubject)
                    || (mSubject != null && !mSubject.equals(peerEntry.mSubject))) {
                return false;
            }
            if (!mTestcase.equals(peerEntry.mTestcase)
                    || !mAction.equals(peerEntry.mAction)
                    || !mData.equals(peerEntry.mData)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int code = mTestcase.hashCode() + mAction.hashCode() + mData.hashCode();
            code += mSubject != null ? mSubject.hashCode() : 0;
            return code;
        }

        @Override
        public String toString() {
            StringBuilder strBuilder = (new StringBuilder())
                    .append(mTestcase).append(": ").append(mAction).append(": ");
            if (mSubject != null) {
                strBuilder.append(mSubject).append(": ");
            }
            return strBuilder.append(mData).toString();
        }

        public boolean equalsIgnoreSubject(RecordEntry peerEntry) {
            return mTestcase.equals(peerEntry.mTestcase)
                    && mAction.equals(peerEntry.mAction)
                    && mData.equals(peerEntry.mData);
        }

        public boolean equalsWithPowerPolicyData(RecordEntry peerEntry) {
            PowerPolicyDef peerPolicy;
            try {
                peerPolicy = PowerPolicyDef.parse(/* policyDefStr= */ peerEntry.mData,
                        /* hasPolicyId= */ true, /* offset= */ 0);
            } catch (Exception e) {
                CLog.wtf("failed to parse policy string: " + peerEntry.mData, e);
                return false;
            }
            return equalsWithPowerPolicyData(peerEntry.mTestcase, peerEntry.mAction,
                    peerEntry.mSubject, peerPolicy);
        }

        public boolean equalsWithPowerPolicyData(String testcase, String action,
                String subject, PowerPolicyDef policy) {
            if ((mSubject == null && null != subject)
                    || (mSubject != null && !mSubject.equals(subject))
                    || !mTestcase.equals(testcase)
                    || !mAction.equals(action)) {
                return false;
            }

            try {
                PowerPolicyDef myPolicy = PowerPolicyDef.parse(/* policyDefStr= */ mData,
                        /* hasPolicyId= */ true, /* offset= */ 0);
                if (!myPolicy.equals(policy)) {
                    return false;
                }
            } catch (Exception e) {
                CLog.wtf("failed to parse policy string: " + mData, e);
                return false;
            }

            return true;
        }
    }
}
