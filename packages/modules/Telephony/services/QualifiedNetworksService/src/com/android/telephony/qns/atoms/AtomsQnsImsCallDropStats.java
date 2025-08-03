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

package com.android.telephony.qns.atoms;

import android.util.StatsEvent;

import com.android.telephony.qns.stats.QnsStatsLog;
import com.android.telephony.statslib.AtomsPushed;

import java.util.Objects;

/** AtomsQnsImsCallDropStats class */
public class AtomsQnsImsCallDropStats extends AtomsPushed {

    /** atom #1 : Transport type in where IMS call drop occurred. */
    private int mTransportTypeCallDropped;

    /** atom #2 : RTP threshold breached event occurred. */
    private boolean mRtpThresholdBreached;

    /** atom #3 : Bit mask of restrictions on another transport type */
    private int mRestrictionsOnOtherTransportType;

    /** atom #4 : Cellular network signal strength {e.g. SSRSRP in NR, RSRP in LTE} */
    private int mSignalStrength;

    /** atom #5 : Cellular network signal quality {e.g. SSRSRQ in NR, RSRQ in LTE} */
    private int mSignalQuality;

    /** atom #6 : Cellular network signal noise ratio {e.g. SSSINR in NR, RSSNR in LTE} */
    private int mSignalNoise;

    /** atom #7 : Iwlan network signal strength (Wi-Fi RSSI) */
    private int mIwlanSignalStrength;

    /** atom #8 : Slot Index */
    private int mSlotIndex;

    /** atom #9 : cellular access network type. */
    private int mCellularNetworkType;

    /** Constructor of AtomsQnsImsCallDropStats */
    public AtomsQnsImsCallDropStats() {}

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    @Override
    public void build(StatsEvent.Builder builder) {
        builder.writeInt(mTransportTypeCallDropped); // atom #1
        builder.writeBoolean(mRtpThresholdBreached); // atom #2
        builder.writeInt(mRestrictionsOnOtherTransportType); // atom #3
        builder.writeInt(mSignalStrength); // atom #4
        builder.writeInt(mSignalQuality); // atom #5
        builder.writeInt(mSignalNoise); // atom #6
        builder.writeInt(mIwlanSignalStrength); // atom #7
        builder.writeInt(mSlotIndex); // atom #8
        builder.writeInt(mCellularNetworkType); // atom #9
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return QnsStatsLog.QNS_IMS_CALL_DROP_STATS;
    }

    /** Return copy of the AtomsQnsImsCallDropStats */
    @Override
    public AtomsPushed copy() {
        return new AtomsQnsImsCallDropStats(this);
    }

    /**
     * Constructor of AtomsQnsImsCallDropStats
     *
     * @param transportTypeCallDropped Transport type in where IMS call drop occurred.
     * @param rtpThresholdBreached RTP threshold breached event occurred.
     * @param restrictionsOnOtherTransportType Bit mask of restrictions on another transport type.
     * @param signalStrength Cellular network signal strength.
     * @param signalQuality Cellular network signal quality.
     * @param signalNoise Cellular network signal noise ratio.
     * @param iwlanSignalStrength Wi-Fi network signal strength.
     * @param slotIndex Index of sim slot.
     * @param cellularNetworkType cellular access network type.
     */
    public AtomsQnsImsCallDropStats(
            int transportTypeCallDropped,
            boolean rtpThresholdBreached,
            int restrictionsOnOtherTransportType,
            int signalStrength,
            int signalQuality,
            int signalNoise,
            int iwlanSignalStrength,
            int slotIndex,
            int cellularNetworkType) {
        mTransportTypeCallDropped = transportTypeCallDropped;
        mRtpThresholdBreached = rtpThresholdBreached;
        mRestrictionsOnOtherTransportType = restrictionsOnOtherTransportType;
        mSignalStrength = signalStrength;
        mSignalQuality = signalQuality;
        mSignalNoise = signalNoise;
        mIwlanSignalStrength = iwlanSignalStrength;
        mSlotIndex = slotIndex;
        mCellularNetworkType = cellularNetworkType;
    }

    /**
     * Copy Constructor of AtomsQnsImsCallDropStats
     *
     * @param info The info param to copy from.
     */
    public AtomsQnsImsCallDropStats(AtomsQnsImsCallDropStats info) {
        mTransportTypeCallDropped = info.mTransportTypeCallDropped;
        mRtpThresholdBreached = info.mRtpThresholdBreached;
        mRestrictionsOnOtherTransportType = info.mRestrictionsOnOtherTransportType;
        mSignalStrength = info.mSignalStrength;
        mSignalQuality = info.mSignalQuality;
        mSignalNoise = info.mSignalNoise;
        mIwlanSignalStrength = info.mIwlanSignalStrength;
        mSlotIndex = info.mSlotIndex;
        mCellularNetworkType = info.mCellularNetworkType;
    }

    public int getTransportTypeCallDropped() {
        return mTransportTypeCallDropped;
    }

    public void setTransportTypeCallDropped(int transportTypeCallDropped) {
        mTransportTypeCallDropped = transportTypeCallDropped;
    }

    public boolean getRtpThresholdBreached() {
        return mRtpThresholdBreached;
    }

    public void setRtpThresholdBreached(boolean rtpThresholdBreached) {
        mRtpThresholdBreached = rtpThresholdBreached;
    }

    public int getRestrictionsOnOtherTransportType() {
        return mRestrictionsOnOtherTransportType;
    }

    public void setRestrictionsOnOtherTransportType(int restrictionsOnOtherTransportType) {
        mRestrictionsOnOtherTransportType = restrictionsOnOtherTransportType;
    }

    public int getSignalStrength() {
        return mSignalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        mSignalStrength = signalStrength;
    }

    public int getSignalQuality() {
        return mSignalQuality;
    }

    public void setSignalQuality(int signalQuality) {
        mSignalQuality = signalQuality;
    }

    public int getSignalNoise() {
        return mSignalNoise;
    }

    public void setSignalNoise(int signalNoise) {
        mSignalNoise = signalNoise;
    }

    public int getIwlanSignalStrength() {
        return mIwlanSignalStrength;
    }

    public void setIwlanSignalStrength(int iwlanSignalStrength) {
        mIwlanSignalStrength = iwlanSignalStrength;
    }

    public int getSlotIndex() {
        return mSlotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        mSlotIndex = slotIndex;
    }

    public int getCellularNetworkType() {
        return mCellularNetworkType;
    }

    public void setCellularNetworkType(int cellularNetworkType) {
        mCellularNetworkType = cellularNetworkType;
    }

    @Override
    public String toString() {
        return "AtomsQnsImsCallDropStats{"
                + "mTransportTypeCallDropped="
                + mTransportTypeCallDropped
                + ", mRtpThresholdBreached="
                + mRtpThresholdBreached
                + ", mRestrictionsOnOtherTransportType="
                + mRestrictionsOnOtherTransportType
                + ", mSignalStrength="
                + mSignalStrength
                + ", mSignalQuality="
                + mSignalQuality
                + ", mSignalNoise="
                + mSignalNoise
                + ", mIwlanSignalStrength="
                + mIwlanSignalStrength
                + ", mSlotIndex="
                + mSlotIndex
                + ", mCellularNetworkType="
                + mCellularNetworkType
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsQnsImsCallDropStats)) return false;
        AtomsQnsImsCallDropStats that = (AtomsQnsImsCallDropStats) o;
        return mTransportTypeCallDropped == that.mTransportTypeCallDropped
                && mRtpThresholdBreached == that.mRtpThresholdBreached
                && mRestrictionsOnOtherTransportType == that.mRestrictionsOnOtherTransportType
                && mSignalStrength == that.mSignalStrength
                && mSignalQuality == that.mSignalQuality
                && mSignalNoise == that.mSignalNoise
                && mIwlanSignalStrength == that.mIwlanSignalStrength
                && mSlotIndex == that.mSlotIndex
                && mCellularNetworkType == that.mCellularNetworkType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTransportTypeCallDropped,
                mRtpThresholdBreached,
                mRestrictionsOnOtherTransportType,
                mSignalStrength,
                mSignalQuality,
                mSignalNoise,
                mIwlanSignalStrength,
                mSlotIndex,
                mCellularNetworkType);
    }
}
