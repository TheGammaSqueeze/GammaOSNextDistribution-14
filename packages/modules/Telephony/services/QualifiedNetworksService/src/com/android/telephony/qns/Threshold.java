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

import static android.telephony.SignalStrength.INVALID;

import android.telephony.SignalThresholdInfo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class defines the threshold information that is processed in QualityMonitor to match the
 * thresholds criteria for different access networks and measurement types.
 */
class Threshold {
    private static final AtomicInteger sTid = new AtomicInteger();

    private int mThresholdId;
    private int mGroupId;
    private int mAccessNetwork;
    private int mMeasurementType;
    private int mThreshold;
    private int mMatchType;
    private int mWaitTime;

    Threshold(int accessNetwork, int measurementType, int threshold, int matchType, int waitTime) {
        this.mThresholdId = sTid.getAndIncrement();
        this.mGroupId = QnsConstants.INVALID_ID;
        this.mAccessNetwork = accessNetwork;
        this.mMeasurementType = measurementType;
        this.mThreshold = threshold;
        this.mMatchType = matchType;
        this.mWaitTime = waitTime;
    }

    Threshold(Threshold t) {
        this.mThresholdId = sTid.getAndIncrement();
        this.mGroupId = QnsConstants.INVALID_ID;
        this.mAccessNetwork = t.mAccessNetwork;
        this.mMeasurementType = t.mMeasurementType;
        this.mThreshold = t.mThreshold;
        this.mMatchType = t.mMatchType;
        this.mWaitTime = t.mWaitTime;
    }

    private Threshold(int tid, int gid, Threshold t) {
        this.mThresholdId = tid;
        this.mGroupId = gid;
        this.mAccessNetwork = t.mAccessNetwork;
        this.mMeasurementType = t.mMeasurementType;
        this.mThreshold = t.mThreshold;
        this.mMatchType = t.mMatchType;
        this.mWaitTime = t.mWaitTime;
    }

    Threshold copy() {
        return new Threshold(mThresholdId, mGroupId, this);
    }

    int getThresholdId() {
        return mThresholdId;
    }

    void setThresholdId(int thresholdId) {
        this.mThresholdId = thresholdId;
    }

    int getGroupId() {
        return mGroupId;
    }

    void setGroupId(int groupId) {
        this.mGroupId = groupId;
    }

    int getAccessNetwork() {
        return mAccessNetwork;
    }

    void setAccessNetwork(int accessNetwork) {
        this.mAccessNetwork = accessNetwork;
    }

    int getMeasurementType() {
        return mMeasurementType;
    }

    void setMeasurementType(int measurementType) {
        this.mMeasurementType = measurementType;
    }

    int getThreshold() {
        return mThreshold;
    }

    void setThreshold(int threshold) {
        this.mThreshold = threshold;
    }

    int getMatchType() {
        return mMatchType;
    }

    void setMatchType(int matchType) {
        this.mMatchType = matchType;
    }

    int getWaitTime() {
        return mWaitTime;
    }

    void setWaitTime(int waitTime) {
        mWaitTime = waitTime;
    }

    /** Method is used to match the Threshold Criteria before Notify to ANE. */
    boolean isMatching(int threshold) {
        if (threshold != INVALID) {
            switch (this.mMatchType) {
                case QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO:
                    return this.mThreshold == threshold;
                case QnsConstants.THRESHOLD_EQUAL_OR_LARGER:
                    return this.mThreshold <= threshold;
                case QnsConstants.THRESHOLD_EQUAL_OR_SMALLER:
                    return this.mThreshold >= threshold;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Threshold)) return false;
        Threshold th = (Threshold) o;
        return this.mThresholdId == th.mThresholdId
                && this.mGroupId == th.mGroupId
                && this.mAccessNetwork == th.mAccessNetwork
                && this.mMeasurementType == th.mMeasurementType
                && this.mThreshold == th.mThreshold
                && this.mMatchType == th.mMatchType
                && this.mWaitTime == th.mWaitTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mThresholdId,
                mGroupId,
                mAccessNetwork,
                mMeasurementType,
                mThreshold,
                mMatchType,
                mWaitTime);
    }

    @Override
    public String toString() {
        return "Threshold {"
                + "thresholdId="
                + mThresholdId
                + ", groupId="
                + mGroupId
                + ", accessNetwork="
                + mAccessNetwork
                + ", measurementType="
                + mMeasurementType
                + ", threshold="
                + mThreshold
                + ", matchType="
                + mMatchType
                + ", waitTime="
                + mWaitTime
                + '}';
    }

    String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(QnsConstants.accessNetworkTypeToString(mAccessNetwork)).append(".");
        switch (mMeasurementType) {
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_UNKNOWN:
                sb.append("UNKNOWN");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI:
                sb.append("RSSI");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP:
                sb.append("RSSCP");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP:
                sb.append("RSRP");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ:
                sb.append("RSRQ");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR:
                sb.append("RSSNR");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP:
                sb.append("SSRSRP");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ:
                sb.append("SSRSRQ");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR:
                sb.append("SSSINR");
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO:
                sb.append("ECNO");
                break;
            case QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY:
                sb.append("AVAIL");
        }
        if (mMatchType == QnsConstants.THRESHOLD_EQUAL_OR_LARGER) {
            sb.append(">=");
        } else if (mMatchType == QnsConstants.THRESHOLD_EQUAL_OR_SMALLER) {
            sb.append("<=");
        } else if (mMatchType == QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO) {
            sb.append("==");
        }
        sb.append(mThreshold);
        return sb.toString();
    }

    boolean identicalThreshold(Threshold o) {
        if (this == o) return true;
        if (o == null) return false;
        return this.mAccessNetwork == o.mAccessNetwork
                && this.mMeasurementType == o.mMeasurementType
                && this.mThreshold == o.mThreshold
                && this.mMatchType == o.mMatchType
                && this.mWaitTime == o.mWaitTime;
    }
}
