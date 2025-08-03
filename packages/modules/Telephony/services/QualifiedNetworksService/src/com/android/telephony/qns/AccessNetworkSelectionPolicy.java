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

package com.android.telephony.qns;

import android.telephony.AccessNetworkConstants;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

class AccessNetworkSelectionPolicy {
    private static final AtomicInteger sAid = new AtomicInteger();

    private final int mPolicyId;
    private final int mNetCapability;
    private final int mTargetTransportType; // AccessNetworkConstants WWAN or WLAN
    private final PreCondition mPreCondition;
    private final PostCondition mPostCondition;
    private final String mLogTag;
    private ThresholdGroup mLastMatchedThresholdGroup;

    AccessNetworkSelectionPolicy(
            int netCapability,
            int targetTransportType,
            PreCondition preCondition,
            List<ThresholdGroup> thgroups) {
        mPolicyId = sAid.getAndIncrement();
        mLogTag =
                QnsConstants.QNS_TAG
                        + "_"
                        + AccessNetworkSelectionPolicy.class.getSimpleName()
                        + "_"
                        + mPolicyId
                        + "_"
                        + QnsUtils.getNameOfNetCapability(netCapability);
        mNetCapability = netCapability;
        mTargetTransportType = targetTransportType;
        mPreCondition = preCondition;
        mPostCondition = new PostCondition(thgroups);
    }

    @Override
    public String toString() {
        return "[AnsPolicy"
                + mPolicyId
                + ":"
                + QnsUtils.getNameOfNetCapability(mNetCapability)
                + "]"
                + QnsConstants.transportTypeToString(mTargetTransportType)
                + ","
                + mPreCondition.toString()
                + mPostCondition.toString();
    }

    int getTargetTransportType() {
        return mTargetTransportType;
    }

    boolean satisfyPrecondition(PreCondition preCondition) {
        return mPreCondition.satisfied(preCondition);
    }

    boolean satisfiedByThreshold(
            QualityMonitor wifiMonitor,
            QualityMonitor cellMonitor,
            boolean iwlanAvailable,
            boolean cellAvailable,
            int cellularAccessNetworkType) {
        if (wifiMonitor == null || cellMonitor == null) {
            return false;
        }
        return mPostCondition.satisfiedByThreshold(
                wifiMonitor, cellMonitor, iwlanAvailable, cellAvailable, cellularAccessNetworkType);
    }

    List<Threshold> findUnmatchedThresholds(
            QualityMonitor wifiMonitor, QualityMonitor cellMonitor) {
        if (wifiMonitor == null || cellMonitor == null) {
            return null;
        }
        return mPostCondition.findUnmatchedThresholds(wifiMonitor, cellMonitor);
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    boolean hasWifiThresholdWithoutCellularCondition() {
        return mPostCondition.hasWifiThresholdWithoutCellularCondition();
    }

    boolean satisfiedWithWifiLowSignalStrength() {
        List<Threshold> thList =
                mLastMatchedThresholdGroup.getThresholds(
                        AccessNetworkConstants.AccessNetworkType.IWLAN);
        if (thList != null) {
            for (Threshold th : thList) {
                if (th.getMatchType() == QnsConstants.THRESHOLD_EQUAL_OR_SMALLER) {
                    log("satisfiedWithWifiLowSignalStrength");
                    return true;
                }
            }
        }
        return false;
    }

    PreCondition getPreCondition() {
        return mPreCondition;
    }

    protected static class PreCondition {
        @QnsConstants.QnsCallType private final int mCallType;
        @QnsConstants.WfcModePreference private final int mPreference;
        @QnsConstants.CellularCoverage private final int mCoverage;

        protected PreCondition(
                @QnsConstants.QnsCallType int callType,
                @QnsConstants.WfcModePreference int preference,
                @QnsConstants.CellularCoverage int coverage) {
            mCallType = callType;
            mCoverage = coverage;
            mPreference = preference;
        }

        boolean satisfied(PreCondition preCondition) {
            return (mCallType == preCondition.mCallType)
                    && (mCoverage == preCondition.mCoverage)
                    && (mPreference == preCondition.mPreference);
        }

        int getCallType() {
            return mCallType;
        }

        int getPreference() {
            return mPreference;
        }

        int getCoverage() {
            return mCoverage;
        }

        @Override
        public String toString() {
            return QnsConstants.callTypeToString(mCallType)
                    + ","
                    + QnsConstants.preferenceToString(mPreference)
                    + ","
                    + QnsConstants.coverageToString(mCoverage)
                    + ",";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PreCondition)) return false;
            PreCondition that = (PreCondition) o;
            return mCallType == that.mCallType
                    && mPreference == that.mPreference
                    && mCoverage == that.mCoverage;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mCallType, mPreference, mCoverage);
        }
    }

    protected static class GuardingPreCondition extends PreCondition {
        @QnsConstants.QnsGuarding private final int mGuarding;

        protected GuardingPreCondition(
                @QnsConstants.QnsCallType int callType,
                @QnsConstants.WfcModePreference int preference,
                @QnsConstants.CellularCoverage int coverage,
                @QnsConstants.QnsGuarding int guarding) {
            super(callType, preference, coverage);
            mGuarding = guarding;
        }

        boolean satisfied(PreCondition preCondition) {
            if (preCondition instanceof GuardingPreCondition) {
                return super.satisfied(preCondition)
                        && mGuarding == ((GuardingPreCondition) preCondition).mGuarding;
            }
            return super.satisfied(preCondition);
        }

        int getGuarding() {
            return mGuarding;
        }

        @Override
        public String toString() {
            return QnsConstants.guardingToString(mGuarding) + "," + super.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GuardingPreCondition)) return false;
            if (!super.equals(o)) return false;
            GuardingPreCondition that = (GuardingPreCondition) o;
            return mGuarding == that.mGuarding;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), mGuarding);
        }
    }

    protected class PostCondition {
        private final List<ThresholdGroup> mThresholdGroups;

        protected PostCondition(List<ThresholdGroup> thGroups) {
            mThresholdGroups = thGroups;
        }

        protected boolean satisfiedByThreshold(
                QualityMonitor wifiMonitor,
                QualityMonitor cellMonitor,
                boolean iwlanAvailable,
                boolean cellAvailable,
                int cellularAccessNetworkType) {
            // if one of thresholdgroup satisfies, return true;
            if (mThresholdGroups != null) {
                for (ThresholdGroup thgroup : mThresholdGroups) {
                    if (thgroup.satisfiedByThreshold(
                            wifiMonitor,
                            cellMonitor,
                            iwlanAvailable,
                            cellAvailable,
                            cellularAccessNetworkType)) {
                        mLastMatchedThresholdGroup = thgroup;
                        return true;
                    }
                }
            }
            return false;
        }

        protected List<Threshold> findUnmatchedThresholds(
                QualityMonitor wifiMonitor, QualityMonitor cellMonitor) {
            List<Threshold> unmatchedThresholds = new ArrayList<>();
            if (mThresholdGroups != null) {
                for (ThresholdGroup thgroup : mThresholdGroups) {
                    unmatchedThresholds.addAll(
                            thgroup.findUnmatchedThresholds(wifiMonitor, cellMonitor));
                }
            }
            return unmatchedThresholds;
        }

        boolean hasWifiThresholdWithoutCellularCondition() {
            if (mThresholdGroups == null) {
                return false;
            }
            for (ThresholdGroup thresholdGroup : mThresholdGroups) {
                if (thresholdGroup.hasWifiThresholdWithoutCellularCondition()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (mThresholdGroups != null && mThresholdGroups.size() > 0) {
                for (ThresholdGroup thgroup : mThresholdGroups) {
                    sb.append(thgroup.toShortString()).append(",");
                }
                sb.deleteCharAt(sb.lastIndexOf(","));
            }
            return sb.toString();
        }
    }
}
