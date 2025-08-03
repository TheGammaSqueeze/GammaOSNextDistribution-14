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

package com.android.server.uwb.data;

import com.android.server.uwb.util.UwbUtil;

import java.util.Arrays;

public class UwbDlTDoAMeasurement {
    public byte[] mMacAddress;
    public int mStatus;
    public int mMessageType;
    public int mMessageControl;
    public int mBlockIndex;
    public int mRoundIndex;
    public int mNLoS;
    public float mAoaAzimuth;
    public int mAoaAzimuthFom;
    public float mAoaElevation;
    public int mAoaElevationFom;
    public int mRssi;
    public long mTxTimestamp;
    public long mRxTimestamp;
    public float mAnchorCfo;
    public float mCfo;
    public long mInitiatorReplyTime;
    public long mResponderReplyTime;
    public int mInitiatorResponderTof;
    public byte[] mAnchorLocation;
    public byte[] mActiveRangingRounds;

    public UwbDlTDoAMeasurement(byte[] macAddress, int status, int messageType, int messageControl,
            int blockIndex, int roundIndex, int nLoS, int aoaAzimuth, int aoaAzimuthFom,
            int aoaElevation, int aoaElevationFom, int rssi, long txTimestamp, long rxTimestamp,
            int anchorCfo, int cfo, long initiatorReplyTime, long responderReplyTime,
            int initiatorResponderTof, byte[] anchorLocation, byte[] activeRangingRounds) {
        mMacAddress = macAddress;
        mStatus = status;
        mMessageType = messageType;
        mMessageControl = messageControl;
        mBlockIndex = blockIndex;
        mRoundIndex = roundIndex;
        mNLoS = nLoS;
        mAoaAzimuth = toFloatFromQ9_7_Format(aoaAzimuth);
        mAoaAzimuthFom = aoaAzimuthFom;
        mAoaElevation = toFloatFromQ9_7_Format(aoaElevation);
        mAoaElevationFom = aoaElevationFom;
        mRssi = rssi;
        mTxTimestamp = txTimestamp;
        mRxTimestamp = rxTimestamp;
        mAnchorCfo = toFloatFromQ6_10_Format(anchorCfo);
        mCfo = toFloatFromQ6_10_Format(cfo);
        mInitiatorReplyTime = initiatorReplyTime;
        mResponderReplyTime = responderReplyTime;
        mInitiatorResponderTof = initiatorResponderTof;
        mAnchorLocation = anchorLocation;
        mActiveRangingRounds = activeRangingRounds;
    }

    public byte[] getMacAddress() {
        return mMacAddress;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getMessageType() {
        return mMessageType;
    }

    public int getMessageControl() {
        return mMessageControl;
    }

    public int getBlockIndex() {
        return mBlockIndex;
    }

    public int getRoundIndex() {
        return mRoundIndex;
    }

    public int getNLoS() {
        return mNLoS;
    }

    public float getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public int getAoaAzimuthFom() {
        return mAoaAzimuthFom;
    }

    public float getAoaElevation() {
        return mAoaElevation;
    }

    public int getAoaElevationFom() {
        return mAoaElevationFom;
    }

    public int getRssi() {
        return mRssi;
    }

    public long getTxTimestamp() {
        return mTxTimestamp;
    }

    public long getRxTimestamp() {
        return mRxTimestamp;
    }

    public float getAnchorCfo() {
        return mAnchorCfo;
    }

    public float getCfo() {
        return mCfo;
    }

    public long getInitiatorReplyTime() {
        return mInitiatorReplyTime;
    }

    public long getResponderReplyTime() {
        return mResponderReplyTime;
    }

    public int getInitiatorResponderTof() {
        return mInitiatorResponderTof;
    }

    public byte[] getAnchorLocation() {
        return mAnchorLocation;
    }

    public byte[] getActiveRangingRounds() {
        return mActiveRangingRounds;
    }

    private float toFloatFromQ9_7_Format(int value) {
        return UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(value, 16),
                9, 7);
    }

    private float toFloatFromQ6_10_Format(int value) {
        return UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(value, 16),
                6, 10);
    }

    @Override
    public String toString() {
        return "UwbDLTDoAMeasurement{" +
                "MacAddress=" + Arrays.toString(mMacAddress) +
                ", Status=" + mStatus +
                ", MessageType=" + mMessageType +
                ", MessageControl=" + mMessageControl +
                ", BlockIndex=" + mBlockIndex +
                ", RoundIndex=" + mRoundIndex +
                ", NLos=" + mNLoS +
                ", AoaAzimuth=" + mAoaAzimuth +
                ", AoaAzimuthFom=" + mAoaAzimuthFom +
                ", AoaElevation=" + mAoaElevation +
                ", AoaElevationFom=" + mAoaElevationFom +
                ", Rssi=" + mRssi +
                ", TxTimestamp=" + mTxTimestamp +
                ", RxTimestamp=" + mRxTimestamp +
                ", AnchorCfo=" + mAnchorCfo +
                ", Cfo=" + mCfo +
                ", InitiatorReplyTime=" + mInitiatorReplyTime +
                ", ResponderReplyTime=" + mResponderReplyTime +
                ", InitiatorResponderTof=" + mInitiatorResponderTof +
                ", AnchorLocation=" + Arrays.toString(mAnchorLocation) +
                ", ActiveRangingRounds=" + Arrays.toString(mActiveRangingRounds) +
                '}';
    }
}
