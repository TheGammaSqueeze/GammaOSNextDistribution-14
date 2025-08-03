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

package com.google.uwb.support.dltdoa;

import android.os.PersistableBundle;
import android.uwb.RangingMeasurement;

import androidx.annotation.Nullable;

/**
 * DlTDoA measurement values
 *
 * <p> This is passed as a bundle with RangingMeasurement for mRangingReportMetadata
 * {@link RangingMeasurement#getRangingMeasurementMetadata()} This will be passed for sessions
 * with DL-TDoA measurements only. For other sessions, the metadata will contain something else
 */
public class DlTDoAMeasurement {
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

    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String MESSAGE_TYPE = "message_type";
    public static final String MESSAGE_CONTROL = "message_control";
    public static final String BLOCK_INDEX = "block_index";
    public static final String ROUND_INDEX = "round_index";
    public static final String NLOS = "nlos";
    public static final String RSSI = "rssi";
    public static final String TX_TIMESTAMP = "tx_timestamp";
    public static final String RX_TIMESTAMP = "rx_timestamp";
    public static final String ANCHOR_CFO = "anchor_cfo";
    public static final String CFO = "cfo";
    public static final String INITIATOR_REPLY_TIME = "initiator_reply_time";
    public static final String RESPONDER_REPLY_TIME = "responder_reply_time";
    public static final String INITIATOR_RESPONDER_TOF = "initiator_responder_time";
    public static final String ANCHOR_LOCATION = "anchor_location";
    public static final String ACTIVE_RANGING_ROUNDS = "active_ranging_rounds";

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    public DlTDoAMeasurement(int messageType, int messageControl, int blockIndex, int roundIndex,
            int NLoS, long txTimestamp, long rxTimestamp, float anchorCfo, float cfo,
            long initiatorReplyTime, long responderReplyTime, int initiatorResponderTof,
            byte[] anchorLocation, byte[] activeRangingRounds) {
        mMessageType = messageType;
        mMessageControl = messageControl;
        mBlockIndex = blockIndex;
        mRoundIndex = roundIndex;
        mNLoS = NLoS;
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

    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
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

    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (bytes[i]);
        }
        return values;
    }

    @Nullable
    private static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    public static boolean isDlTDoAMeasurement(PersistableBundle bundle) {
        return bundle.containsKey(ANCHOR_LOCATION) && bundle.containsKey(ACTIVE_RANGING_ROUNDS);
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, BUNDLE_VERSION_CURRENT);
        bundle.putInt(MESSAGE_TYPE, mMessageType);
        bundle.putInt(MESSAGE_CONTROL, mMessageControl);
        bundle.putInt(BLOCK_INDEX, mBlockIndex);
        bundle.putInt(ROUND_INDEX, mRoundIndex);
        bundle.putInt(NLOS, mNLoS);
        bundle.putLong(TX_TIMESTAMP, mTxTimestamp);
        bundle.putLong(RX_TIMESTAMP, mRxTimestamp);
        bundle.putDouble(ANCHOR_CFO, mAnchorCfo);
        bundle.putDouble(CFO, mCfo);
        bundle.putLong(INITIATOR_REPLY_TIME, mInitiatorReplyTime);
        bundle.putLong(RESPONDER_REPLY_TIME, mResponderReplyTime);
        bundle.putInt(INITIATOR_RESPONDER_TOF, mInitiatorResponderTof);
        bundle.putIntArray(ANCHOR_LOCATION, byteArrayToIntArray(mAnchorLocation));
        bundle.putIntArray(ACTIVE_RANGING_ROUNDS, byteArrayToIntArray(mActiveRangingRounds));
        return bundle;
    }

    public static DlTDoAMeasurement fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static DlTDoAMeasurement parseVersion1(PersistableBundle bundle) {
        return new DlTDoAMeasurement.Builder()
                .setMessageType(bundle.getInt(MESSAGE_TYPE))
                .setMessageControl(bundle.getInt(MESSAGE_CONTROL))
                .setBlockIndex(bundle.getInt(BLOCK_INDEX))
                .setRoundIndex(bundle.getInt(ROUND_INDEX))
                .setNLoS(bundle.getInt(NLOS))
                .setTxTimestamp(bundle.getLong(TX_TIMESTAMP))
                .setRxTimestamp(bundle.getLong(RX_TIMESTAMP))
                .setAnchorCfo((float) bundle.getDouble(ANCHOR_CFO))
                .setCfo((float) bundle.getDouble(CFO))
                .setInitiatorReplyTime(bundle.getLong(INITIATOR_REPLY_TIME))
                .setResponderReplyTime(bundle.getLong(RESPONDER_REPLY_TIME))
                .setInitiatorResponderTof(bundle.getInt(INITIATOR_RESPONDER_TOF))
                .setAnchorLocation(intArrayToByteArray(bundle.getIntArray(ANCHOR_LOCATION)))
                .setActiveRangingRounds(
                        intArrayToByteArray(bundle.getIntArray(ACTIVE_RANGING_ROUNDS)))
                .build();
    }

    /** Builder */
    public static class Builder {
        private int mMessageType;
        private int mMessageControl;
        private int mBlockIndex;
        private int mRoundIndex;
        private int mNLoS;
        private long mTxTimestamp;
        private long mRxTimestamp;
        private float mAnchorCfo;
        private float mCfo;
        private long mInitiatorReplyTime;
        private long mResponderReplyTime;
        private int mInitiatorResponderTof;
        private byte[] mAnchorLocation;
        private byte[] mActiveRangingRounds;

        public DlTDoAMeasurement.Builder setMessageType(int messageType) {
            mMessageType = messageType;
            return this;
        }

        public DlTDoAMeasurement.Builder setMessageControl(int messageControl) {
            mMessageControl = messageControl;
            return this;
        }

        public DlTDoAMeasurement.Builder setBlockIndex(int blockIndex) {
            mBlockIndex = blockIndex;
            return this;
        }

        public DlTDoAMeasurement.Builder setRoundIndex(int roundIndex) {
            mRoundIndex = roundIndex;
            return this;
        }

        public DlTDoAMeasurement.Builder setNLoS(int nLoS) {
            mNLoS = nLoS;
            return this;
        }

        public DlTDoAMeasurement.Builder setTxTimestamp(long txTimestamp) {
            mTxTimestamp = txTimestamp;
            return this;
        }

        public DlTDoAMeasurement.Builder setRxTimestamp(long rxTimestamp) {
            mRxTimestamp = rxTimestamp;
            return this;
        }

        public DlTDoAMeasurement.Builder setAnchorCfo(float anchorCfo) {
            mAnchorCfo = anchorCfo;
            return this;
        }

        public DlTDoAMeasurement.Builder setCfo(float cfo) {
            mCfo = cfo;
            return this;
        }

        public DlTDoAMeasurement.Builder setInitiatorReplyTime(long initiatorReplyTime) {
            mInitiatorReplyTime = initiatorReplyTime;
            return this;
        }

        public DlTDoAMeasurement.Builder setResponderReplyTime(long responderReplyTime) {
            mResponderReplyTime = responderReplyTime;
            return this;
        }

        public DlTDoAMeasurement.Builder setInitiatorResponderTof(int initiatorResponderTof) {
            mInitiatorResponderTof = initiatorResponderTof;
            return this;
        }

        public DlTDoAMeasurement.Builder setAnchorLocation(byte[] anchorLocation) {
            mAnchorLocation = anchorLocation;
            return this;
        }

        public DlTDoAMeasurement.Builder setActiveRangingRounds(byte[] activeRangingRounds) {
            mActiveRangingRounds = activeRangingRounds;
            return this;
        }

        public DlTDoAMeasurement build() {
            return new DlTDoAMeasurement(
                    mMessageType,
                    mMessageControl,
                    mBlockIndex,
                    mRoundIndex,
                    mNLoS,
                    mTxTimestamp,
                    mRxTimestamp,
                    mAnchorCfo,
                    mCfo,
                    mInitiatorReplyTime,
                    mResponderReplyTime,
                    mInitiatorResponderTof,
                    mAnchorLocation,
                    mActiveRangingRounds);
        }
    }
}
