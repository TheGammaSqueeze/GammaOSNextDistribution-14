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

/** AtomsQualifiedRatListChangedInfo class */
public class AtomsQualifiedRatListChangedInfo extends AtomsPushed {

    /** atom #1 : NetCapability of this Qualified RAT update */
    private int mNetCapability;
    /** atom #2 : The most preferred qualified RAT */
    private int mFirstQualifiedRat;
    /** atom #3 : Second preferred qualified RAT */
    private int mSecondQualifiedRat;
    /** atom #4 : Current actual transport type of Data session for this NetCapability */
    private int mCurrentTransportType;
    /** atom #5 : Indicates whether WFC is enabled */
    private boolean mWfcEnabled;
    /** atom #6 : Indicates the user's WFC mode */
    private int mWfcMode;
    /** atom #7 : Current Cellular AccessNetwork Type */
    private int mCellularNetworkType;
    /** atom #8 : Available IWLAN AccessNetwork */
    private int mIwlanNetworkType;
    /** atom #9 : Bit mask of restrictions on WWAN */
    private int mRestrictionsOnWwan;
    /** atom #10 : Bit mask of restrictions on WLAN */
    private int mRestrictionsOnWlan;
    /** atom #11 : Cellular network signal strength {e.g. SSRSRP in NR, RSRP in LTE} */
    private int mSignalStrength;
    /** atom #12 : Cellular network signal quality {e.g. SSRSRQ in NR, RSRQ in LTE} */
    private int mSignalQuality;
    /** atom #13 : Cellular network signal noise ratio {e.g. SSSINR in NR, RSSNR in LTE} */
    private int mSignalNoise;
    /** atom #14 : Iwlan network signal strength (Wi-Fi RSSI) */
    private int mIwlanSignalStrength;
    /** atom #15 : Reason for preferred RAT update */
    private int mUpdateReason;
    /** atom #16: IMS Call Type */
    private int mImsCallType;
    /** atom #17 : IMS Call Quality */
    private int mImsCallQuality;
    /** atom #18 : Slot Index */
    private int mSlotIndex;

    /** Constructor of AtomsQualifiedRatListChangedInfo */
    public AtomsQualifiedRatListChangedInfo() {}

    /**
     * Constructor of AtomsQualifiedRatListChangedInfo
     *
     * @param netCapability NetCapability of this Qualified RAT update
     * @param firstQualifiedRat The most preferred qualified RAT
     * @param secondQualifiedRat Second preferred qualified RAT
     * @param currentTransportType Current actual transport type of Data session for this
     *     NetCapability
     * @param wfcEnabled Indicates whether WFC is enabled
     * @param wfcMode Indicates the user's WFC mode
     * @param cellularNetworkType Current Cellular AccessNetwork Type
     * @param iwlanNetworkType Available IWLAN AccessNetwork
     * @param restrictionsOnWwan Bit mask of restrictions on WWAN
     * @param restrictionsOnWlan Bit mask of restrictions on WLAN
     * @param signalStrength Cellular network signal strength {e.g. SSRSRP in NR, RSRP in LTE, RSCP
     *     in UMTS}
     * @param signalQuality Cellular network signal quality {e.g. SSRSRQ in NR, RSRQ in LTE}
     * @param signalNoise Cellular network signal noise ratio {e.g. SSSINR in NR, RSSNR in LTE}
     * @param iwlanSignalStrength Iwlan network signal strength (Wi-Fi RSSI)
     * @param updateReason Reason for preferred RAT update
     * @param imsCallType Ims Call Type {e.g. IDLE, VOICE, VIDEO, E-CALL}
     * @param imsCallQuality Ims Call Quality
     * @param slotIndex Index of sim slot
     */
    public AtomsQualifiedRatListChangedInfo(
            int netCapability,
            int firstQualifiedRat,
            int secondQualifiedRat,
            int currentTransportType,
            boolean wfcEnabled,
            int wfcMode,
            int cellularNetworkType,
            int iwlanNetworkType,
            int restrictionsOnWwan,
            int restrictionsOnWlan,
            int signalStrength,
            int signalQuality,
            int signalNoise,
            int iwlanSignalStrength,
            int updateReason,
            int imsCallType,
            int imsCallQuality,
            int slotIndex) {
        mNetCapability = netCapability;
        mFirstQualifiedRat = firstQualifiedRat;
        mSecondQualifiedRat = secondQualifiedRat;
        mCurrentTransportType = currentTransportType;
        mWfcEnabled = wfcEnabled;
        mWfcMode = wfcMode;
        mCellularNetworkType = cellularNetworkType;
        mIwlanNetworkType = iwlanNetworkType;
        mRestrictionsOnWwan = restrictionsOnWwan;
        mRestrictionsOnWlan = restrictionsOnWlan;
        mSignalStrength = signalStrength;
        mSignalQuality = signalQuality;
        mSignalNoise = signalNoise;
        mIwlanSignalStrength = iwlanSignalStrength;
        mUpdateReason = updateReason;
        mImsCallType = imsCallType;
        mImsCallQuality = imsCallQuality;
        mSlotIndex = slotIndex;
    }

    /**
     * Copy Constructor of AtomsQualifiedRatListChangedInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsQualifiedRatListChangedInfo(AtomsQualifiedRatListChangedInfo info) {
        mNetCapability = info.mNetCapability;
        mFirstQualifiedRat = info.mFirstQualifiedRat;
        mSecondQualifiedRat = info.mSecondQualifiedRat;
        mCurrentTransportType = info.mCurrentTransportType;
        mWfcEnabled = info.mWfcEnabled;
        mWfcMode = info.mWfcMode;
        mCellularNetworkType = info.mCellularNetworkType;
        mIwlanNetworkType = info.mIwlanNetworkType;
        mRestrictionsOnWwan = info.mRestrictionsOnWwan;
        mRestrictionsOnWlan = info.mRestrictionsOnWlan;
        mSignalStrength = info.mSignalStrength;
        mSignalQuality = info.mSignalQuality;
        mSignalNoise = info.mSignalNoise;
        mIwlanSignalStrength = info.mIwlanSignalStrength;
        mUpdateReason = info.mUpdateReason;
        mImsCallType = info.mImsCallType;
        mImsCallQuality = info.mImsCallQuality;
        mSlotIndex = info.mSlotIndex;
    }

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    @Override
    public void build(StatsEvent.Builder builder) {
        builder.writeInt(mNetCapability); // atom #1
        builder.writeInt(mFirstQualifiedRat); // atom #2
        builder.writeInt(mSecondQualifiedRat); // atom #3
        builder.writeInt(mCurrentTransportType); // atom #4
        builder.writeBoolean(mWfcEnabled); // atom #5
        builder.writeInt(mWfcMode); // atom #6
        builder.writeInt(mCellularNetworkType); // atom #7
        builder.writeInt(mIwlanNetworkType); // atom #8
        builder.writeInt(mRestrictionsOnWwan); // atom #9
        builder.writeInt(mRestrictionsOnWlan); // atom #10
        builder.writeInt(mSignalStrength); // atom #11
        builder.writeInt(mSignalQuality); // atom #12
        builder.writeInt(mSignalNoise); // atom #13
        builder.writeInt(mIwlanSignalStrength); // atom #14
        builder.writeInt(mUpdateReason); // atom #15
        builder.writeInt(mImsCallType); // atom #16
        builder.writeInt(mImsCallQuality); // atom #17
        builder.writeInt(mSlotIndex); // atom #18
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return QnsStatsLog.QUALIFIED_RAT_LIST_CHANGED;
    }

    /** Return copy of the AtomsQualifiedRatListChangedInfo */
    @Override
    public AtomsPushed copy() {
        return new AtomsQualifiedRatListChangedInfo(this);
    }

    public int getNetCapability() {
        return mNetCapability;
    }

    public void setNetCapability(int netCapability) {
        mNetCapability = netCapability;
    }

    public int getFirstQualifiedRat() {
        return mFirstQualifiedRat;
    }

    public void setFirstQualifiedRat(int firstQualifiedRat) {
        mFirstQualifiedRat = firstQualifiedRat;
    }

    public int getSecondQualifiedRat() {
        return mSecondQualifiedRat;
    }

    public void setSecondQualifiedRat(int secondQualifiedRat) {
        mSecondQualifiedRat = secondQualifiedRat;
    }

    public int getCurrentTransportType() {
        return mCurrentTransportType;
    }

    public void setCurrentTransportType(int currentTransportType) {
        mCurrentTransportType = currentTransportType;
    }

    public boolean getWfcEnabled() {
        return mWfcEnabled;
    }

    public void setWfcEnabled(boolean wfcEnabled) {
        mWfcEnabled = wfcEnabled;
    }

    public int getWfcMode() {
        return mWfcMode;
    }

    public void setWfcMode(int wfcMode) {
        mWfcMode = wfcMode;
    }

    public int getCellularNetworkType() {
        return mCellularNetworkType;
    }

    public void setCellularNetworkType(int cellularNetworkType) {
        mCellularNetworkType = cellularNetworkType;
    }

    public int getIwlanNetworkType() {
        return mIwlanNetworkType;
    }

    public void setIwlanNetworkType(int iwlanNetworkType) {
        mIwlanNetworkType = iwlanNetworkType;
    }

    public int getRestrictionsOnWwan() {
        return mRestrictionsOnWwan;
    }

    public void setRestrictionsOnWwan(int restrictionsOnWwan) {
        mRestrictionsOnWwan = restrictionsOnWwan;
    }

    public int getRestrictionsOnWlan() {
        return mRestrictionsOnWlan;
    }

    public void setRestrictionsOnWlan(int restrictionsOnWlan) {
        mRestrictionsOnWlan = restrictionsOnWlan;
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

    public int getUpdateReason() {
        return mUpdateReason;
    }

    public void setUpdateReason(int updateReason) {
        mUpdateReason = updateReason;
    }

    public int getImsCallType() {
        return mImsCallType;
    }

    public void setImsCallType(int imsCallType) {
        mImsCallType = imsCallType;
    }

    public int getImsCallQuality() {
        return mImsCallQuality;
    }

    public void setImsCallQuality(int imsCallQuality) {
        mImsCallQuality = imsCallQuality;
    }

    public int getSlotIndex() {
        return mSlotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        mSlotIndex = slotIndex;
    }

    @Override
    public String toString() {
        return "AtomsQualifiedRatListChangedInfo{"
                + "mNetCapability="
                + mNetCapability
                + ", mFirstQualifiedRat="
                + mFirstQualifiedRat
                + ", mSecondQualifiedRat="
                + mSecondQualifiedRat
                + ", mCurrentTransportType="
                + mCurrentTransportType
                + ", mWfcEnabled="
                + mWfcEnabled
                + ", mWfcMode="
                + mWfcMode
                + ", mCellularNetworkType="
                + mCellularNetworkType
                + ", mIwlanNetworkType="
                + mIwlanNetworkType
                + ", mRestrictionsOnWwan="
                + mRestrictionsOnWwan
                + ", mRestrictionsOnWlan="
                + mRestrictionsOnWlan
                + ", mSignalStrength="
                + mSignalStrength
                + ", mSignalQuality="
                + mSignalQuality
                + ", mSignalNoise="
                + mSignalNoise
                + ", mIwlanSignalStrength="
                + mIwlanSignalStrength
                + ", mUpdateReason="
                + mUpdateReason
                + ", mImsCallType="
                + mImsCallType
                + ", mImsCallQuality="
                + mImsCallQuality
                + ", mSlotIndex="
                + mSlotIndex
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsQualifiedRatListChangedInfo)) return false;
        AtomsQualifiedRatListChangedInfo that = (AtomsQualifiedRatListChangedInfo) o;
        return mNetCapability == that.mNetCapability
                && mFirstQualifiedRat == that.mFirstQualifiedRat
                && mSecondQualifiedRat == that.mSecondQualifiedRat
                && mCurrentTransportType == that.mCurrentTransportType
                && mWfcEnabled == that.mWfcEnabled
                && mWfcMode == that.mWfcMode
                && mCellularNetworkType == that.mCellularNetworkType
                && mIwlanNetworkType == that.mIwlanNetworkType
                && mRestrictionsOnWwan == that.mRestrictionsOnWwan
                && mRestrictionsOnWlan == that.mRestrictionsOnWlan
                && mSignalStrength == that.mSignalStrength
                && mSignalQuality == that.mSignalQuality
                && mSignalNoise == that.mSignalNoise
                && mIwlanSignalStrength == that.mIwlanSignalStrength
                && mUpdateReason == that.mUpdateReason
                && mImsCallType == that.mImsCallType
                && mImsCallQuality == that.mImsCallQuality
                && mSlotIndex == that.mSlotIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mNetCapability,
                mFirstQualifiedRat,
                mSecondQualifiedRat,
                mCurrentTransportType,
                mWfcEnabled,
                mWfcMode,
                mCellularNetworkType,
                mIwlanNetworkType,
                mRestrictionsOnWwan,
                mRestrictionsOnWlan,
                mSignalStrength,
                mSignalQuality,
                mSignalNoise,
                mIwlanSignalStrength,
                mUpdateReason,
                mImsCallType,
                mImsCallQuality,
                mSlotIndex);
    }
}
