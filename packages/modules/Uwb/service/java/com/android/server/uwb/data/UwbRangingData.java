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
package com.android.server.uwb.data;

import java.util.Arrays;

public class UwbRangingData {
    public long mSeqCounter;
    public long mSessionId;
    public int mRcrIndication;
    public long mCurrRangingInterval;
    public int mRangingMeasuresType;
    public int mMacAddressMode;
    public int mNoOfRangingMeasures;
    public UwbTwoWayMeasurement[] mRangingTwoWayMeasures;
    public byte[] mRawNtfData;
    public UwbOwrAoaMeasurement mRangingOwrAoaMeasure;
    public UwbDlTDoAMeasurement[] mUwbDlTDoAMeasurements;

    public UwbRangingData(long seqCounter, long sessionId, int rcrIndication,
            long currRangingInterval, int rangingMeasuresType, int macAddressMode,
            int noOfRangingMeasures, UwbTwoWayMeasurement[] rangingTwoWayMeasures,
            byte[] rawNtfData) {
        this.mSeqCounter = seqCounter;
        this.mSessionId = sessionId;
        this.mRcrIndication = rcrIndication;
        this.mCurrRangingInterval = currRangingInterval;
        this.mRangingMeasuresType = rangingMeasuresType;
        this.mMacAddressMode = macAddressMode;
        this.mNoOfRangingMeasures = noOfRangingMeasures;
        this.mRangingTwoWayMeasures = rangingTwoWayMeasures;
        this.mRawNtfData = rawNtfData;
    }

    public UwbRangingData(long seqCounter, long sessionId, int rcrIndication,
            long currRangingInterval, int rangingMeasuresType, int macAddressMode,
            int noOfRangingMeasures, UwbOwrAoaMeasurement rangingOwrAoaMeasure,
            byte[] rawNtfData) {
        this.mSeqCounter = seqCounter;
        this.mSessionId = sessionId;
        this.mRcrIndication = rcrIndication;
        this.mCurrRangingInterval = currRangingInterval;
        this.mRangingMeasuresType = rangingMeasuresType;
        this.mMacAddressMode = macAddressMode;
        this.mNoOfRangingMeasures = noOfRangingMeasures;
        this.mRangingOwrAoaMeasure = rangingOwrAoaMeasure;
        this.mRawNtfData = rawNtfData;
    }

    public UwbRangingData(long seqCounter, long sessionId, int rcrIndication,
            long currRangingInterval, int rangingMeasuresType, int macAddressMode,
            int noOfRangingMeasures, UwbDlTDoAMeasurement[] uwbDlTDoAMeasurements,
            byte[] rawNtfData) {
        this.mSeqCounter = seqCounter;
        this.mSessionId = sessionId;
        this.mRcrIndication = rcrIndication;
        this.mCurrRangingInterval = currRangingInterval;
        this.mRangingMeasuresType = rangingMeasuresType;
        this.mMacAddressMode = macAddressMode;
        this.mNoOfRangingMeasures = noOfRangingMeasures;
        this.mUwbDlTDoAMeasurements = uwbDlTDoAMeasurements;
        this.mRawNtfData = rawNtfData;
    }

    public long getSequenceCounter() {
        return mSeqCounter;
    }

    public long getSessionId() {
        return mSessionId;
    }

    public int getRcrIndication() {
        return mRcrIndication;
    }

    public long getCurrRangingInterval() {
        return mCurrRangingInterval;
    }

    public int getRangingMeasuresType() {
        return mRangingMeasuresType;
    }

    public int getMacAddressMode() {
        return mMacAddressMode;
    }

    public int getNoOfRangingMeasures() {
        return mNoOfRangingMeasures;
    }

    public UwbTwoWayMeasurement[] getRangingTwoWayMeasures() {
        return mRangingTwoWayMeasures;
    }

    public byte[] getRawNtfData() {
        return mRawNtfData;
    }

    public UwbOwrAoaMeasurement getRangingOwrAoaMeasure() {
        return mRangingOwrAoaMeasure;
    }

    public UwbDlTDoAMeasurement[] getUwbDlTDoAMeasurements() {
        return mUwbDlTDoAMeasurements;
    }

    public String toString() {
        if (mRangingMeasuresType == UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY) {
            return "UwbRangingData { "
                    + " SeqCounter = " + mSeqCounter
                    + ", SessionId = " + mSessionId
                    + ", RcrIndication = " + mRcrIndication
                    + ", CurrRangingInterval = " + mCurrRangingInterval
                    + ", RangingMeasuresType = " + mRangingMeasuresType
                    + ", MacAddressMode = " + mMacAddressMode
                    + ", NoOfRangingMeasures = " + mNoOfRangingMeasures
                    + ", RangingTwoWayMeasures = " + Arrays.toString(mRangingTwoWayMeasures)
                    + ", RawNotificationData = " + Arrays.toString(mRawNtfData)
                    + '}';
        } else if (mRangingMeasuresType == UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA) {
            return "UwbRangingData { "
                    + " SeqCounter = " + mSeqCounter
                    + ", SessionId = " + mSessionId
                    + ", RcrIndication = " + mRcrIndication
                    + ", CurrRangingInterval = " + mCurrRangingInterval
                    + ", RangingMeasuresType = " + mRangingMeasuresType
                    + ", MacAddressMode = " + mMacAddressMode
                    + ", NoOfRangingMeasures = " + mNoOfRangingMeasures
                    + ", RangingOwrAoaMeasure = " + mRangingOwrAoaMeasure.toString()
                    + ", RawNotificationData = " + Arrays.toString(mRawNtfData)
                    + '}';
        } else if (mRangingMeasuresType == UwbUciConstants.RANGING_MEASUREMENT_TYPE_DL_TDOA) {
            return "UwbRangingData { "
                    + " SeqCounter = " + mSeqCounter
                    + ", SessionId = " + mSessionId
                    + ", RcrIndication = " + mRcrIndication
                    + ", CurrRangingInterval = " + mCurrRangingInterval
                    + ", RangingMeasuresType = " + mRangingMeasuresType
                    + ", MacAddressMode = " + mMacAddressMode
                    + ", NoOfRangingMeasures = " + mNoOfRangingMeasures
                    + ", RangingDlTDoAMeasure = " + Arrays.toString(mUwbDlTDoAMeasurements)
                    + ", RawNotificationData = " + Arrays.toString(mRawNtfData)
                    + '}';
        } else {
            // TODO(jh0.jang) : ONE WAY RANGING(TDOA)?
            return null;
        }
    }
}
