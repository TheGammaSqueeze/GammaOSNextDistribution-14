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

package androidx.core.uwb.backend.impl.internal;

import java.util.Arrays;
import java.util.Locale;

/** Downlink-TDoA measurements */
public class DlTdoaMeasurement {
    private final int mMessageType;
    private final int mMessageControl;
    private final int mBlockIndex;
    private final int mRoundIndex;
    private final int mNLoS;
    private final long mTxTimestamp;
    private final long mRxTimestamp;
    private final float mAnchorCfo;
    private final float mCfo;
    private final long mInitiatorReplyTime;
    private final long mResponderReplyTime;
    private final int mInitiatorResponderTof;
    private final byte[] mAnchorLocation;
    private final byte[] mActiveRangingRounds;

    public DlTdoaMeasurement(int messageType, int messageControl, int blockIndex, int roundIndex,
            int nLoS, long txTimestamp, long rxTimestamp, float anchorCfo, float cfo,
            long initiatorReplyTime, long responderReplyTime, int initiatorResponderTof,
            byte[] anchorLocation, byte[] activeRangingRounds) {
        mMessageType = messageType;
        mMessageControl = messageControl;
        mBlockIndex = blockIndex;
        mRoundIndex = roundIndex;
        mNLoS = nLoS;
        mTxTimestamp = txTimestamp;
        mRxTimestamp = rxTimestamp;
        mAnchorCfo = anchorCfo;
        mCfo = cfo;
        mInitiatorReplyTime = initiatorReplyTime;
        mResponderReplyTime = responderReplyTime;
        mInitiatorResponderTof = initiatorResponderTof;
        mAnchorLocation = anchorLocation;
        mActiveRangingRounds = activeRangingRounds;
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


    @Override
    public String toString() {
        return String.format(Locale.US, " | messageType : %d", mMessageType)
                + String.format(Locale.US, " | messageControl : %d", mMessageControl)
                + String.format(Locale.US, " | blockIndex : %d", mBlockIndex)
                + String.format(Locale.US, " | roundIndex : %d", mRoundIndex)
                + String.format(Locale.US, " | nLoS : %d", mNLoS)
                + String.format(Locale.US, " | txTimestamp : %d", mTxTimestamp)
                + String.format(Locale.US, " | rxTimestamp : %d", mRxTimestamp)
                + String.format(Locale.US, " | anchorCfo : %f", mAnchorCfo)
                + String.format(Locale.US, " | cfo : %f", mCfo)
                + String.format(Locale.US, " | initiatorReplyTime : %d", mInitiatorReplyTime)
                + String.format(Locale.US, " | responderReplyTime : %d", mResponderReplyTime)
                + String.format(Locale.US, " | initiatorResponderTof : %d",
                                mInitiatorResponderTof)
                + String.format(Locale.US, " | anchorLocation : %s",
                Arrays.toString(mAnchorLocation))
                + String.format(
                Locale.US, " | activeRangingRounds : %s",
                                Arrays.toString(mActiveRangingRounds));
    }
}
