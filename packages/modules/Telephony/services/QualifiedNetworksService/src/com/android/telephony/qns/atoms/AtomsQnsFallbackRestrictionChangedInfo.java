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

/** AtomsQnsFallbackRestrictionChangedInfo class */
public class AtomsQnsFallbackRestrictionChangedInfo extends AtomsPushed {

    /** atom #1 : Restriction on WLAN caused by RTP threshold breached */
    private boolean mRestrictionOnWlanByRtpThresholdBreached;

    /** atom #2 : Restriction on WWAN caused by RTP threshold breached */
    private boolean mRestrictionOnWwanByRtpThresholdBreached;

    /** atom #3 : Restriction on WLAN caused by IMS registration fail */
    private boolean mRestrictionOnWlanByImsRegistrationFailed;

    /** atom #4 : Restriction on WLAN caused by Wi-Fi backhaul problem. */
    private boolean mRestrictionOnWlanByWifiBackhaulProblem;

    /** atom #5 : Carrier Id */
    private int mCarrierId;

    /** atom #6 : Slot Index */
    private int mSlotIndex;

    /** Constructor of AtomsQnsFallbackRestrictionChangedInfo */
    public AtomsQnsFallbackRestrictionChangedInfo() {}

    /**
     * Constructor of AtomsQnsFallbackRestrictionChangedInfo
     *
     * @param restrictionOnWlanByRtpThresholdBreached Restriction on wlan caused by RTP threshold
     *     breached
     * @param restrictionOnWwanByRtpThresholdBreached Restriction on wwan caused by RTP threshold
     *     breached
     * @param restrictionOnWlanByImsRegistrationFailed Restriction on wlan caused by IMS
     *     registration fail
     * @param restrictionOnWlanByWifiBackhaulProblem Restriction on wlan caused by Wifi backhaul
     *     problem.
     * @param carrierId Carrier Id
     * @param slotIndex Index of sim slot
     */
    public AtomsQnsFallbackRestrictionChangedInfo(
            boolean restrictionOnWlanByRtpThresholdBreached,
            boolean restrictionOnWwanByRtpThresholdBreached,
            boolean restrictionOnWlanByImsRegistrationFailed,
            boolean restrictionOnWlanByWifiBackhaulProblem,
            int carrierId,
            int slotIndex) {
        mRestrictionOnWlanByRtpThresholdBreached = restrictionOnWlanByRtpThresholdBreached;
        mRestrictionOnWwanByRtpThresholdBreached = restrictionOnWwanByRtpThresholdBreached;
        mRestrictionOnWlanByImsRegistrationFailed = restrictionOnWlanByImsRegistrationFailed;
        mRestrictionOnWlanByWifiBackhaulProblem = restrictionOnWlanByWifiBackhaulProblem;
        mCarrierId = carrierId;
        mSlotIndex = slotIndex;
    }

    /**
     * Copy Constructor of AtomsQnsFallbackRestrictionChangedInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsQnsFallbackRestrictionChangedInfo(AtomsQnsFallbackRestrictionChangedInfo info) {
        mRestrictionOnWlanByRtpThresholdBreached = info.mRestrictionOnWlanByRtpThresholdBreached;
        mRestrictionOnWwanByRtpThresholdBreached = info.mRestrictionOnWwanByRtpThresholdBreached;
        mRestrictionOnWlanByImsRegistrationFailed = info.mRestrictionOnWlanByImsRegistrationFailed;
        mRestrictionOnWlanByWifiBackhaulProblem = info.mRestrictionOnWlanByWifiBackhaulProblem;
        mCarrierId = info.mCarrierId;
        mSlotIndex = info.mSlotIndex;
    }

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    @Override
    public void build(StatsEvent.Builder builder) {
        builder.writeBoolean(mRestrictionOnWlanByRtpThresholdBreached); // atom #1
        builder.writeBoolean(mRestrictionOnWwanByRtpThresholdBreached); // atom #2
        builder.writeBoolean(mRestrictionOnWlanByImsRegistrationFailed); // atom #3
        builder.writeBoolean(mRestrictionOnWlanByWifiBackhaulProblem); // atom #4
        builder.writeInt(mCarrierId); // atom #5
        builder.writeInt(mSlotIndex); // atom #6
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return QnsStatsLog.QNS_FALLBACK_RESTRICTION_CHANGED;
    }

    /** Return copy of the AtomsQnsFallbackRestrictionChangedInfo */
    @Override
    public AtomsPushed copy() {
        return new AtomsQnsFallbackRestrictionChangedInfo(this);
    }

    public boolean getRestrictionOnWlanByRtpThresholdBreached() {
        return mRestrictionOnWlanByRtpThresholdBreached;
    }

    public void setRestrictionOnWlanByRtpThresholdBreached(
            boolean restrictionOnWlanByRtpThresholdBreached) {
        mRestrictionOnWlanByRtpThresholdBreached = restrictionOnWlanByRtpThresholdBreached;
    }

    public boolean getRestrictionOnWwanByRtpThresholdBreached() {
        return mRestrictionOnWwanByRtpThresholdBreached;
    }

    public void setRestrictionOnWwanByRtpThresholdBreached(
            boolean restrictionOnWwanByRtpThresholdBreached) {
        mRestrictionOnWwanByRtpThresholdBreached = restrictionOnWwanByRtpThresholdBreached;
    }

    public boolean getRestrictionOnWlanByImsRegistrationFailed() {
        return mRestrictionOnWlanByImsRegistrationFailed;
    }

    public void setRestrictionOnWlanByImsRegistrationFailed(
            boolean restrictionOnWlanByImsRegistrationFailed) {
        mRestrictionOnWlanByImsRegistrationFailed = restrictionOnWlanByImsRegistrationFailed;
    }

    public boolean getRestrictionOnWlanByWifiBackhaulProblem() {
        return mRestrictionOnWlanByWifiBackhaulProblem;
    }

    public void setRestrictionOnWlanByWifiBackhaulProblem(
            boolean restrictionOnWlanByWifiBackhaulProblem) {
        mRestrictionOnWlanByWifiBackhaulProblem = restrictionOnWlanByWifiBackhaulProblem;
    }

    public int getCarrierId() {
        return mCarrierId;
    }

    public void setCarrierId(int carrierId) {
        mCarrierId = carrierId;
    }

    public int getSlotIndex() {
        return mSlotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        mSlotIndex = slotIndex;
    }

    @Override
    public String toString() {
        return "AtomsQnsFallbackRestrictionChangedInfo{"
                + "mRestrictionWlanRtpThresholdBreached="
                + mRestrictionOnWlanByRtpThresholdBreached
                + ", mRestrictionWwanRtpThresholdBreached="
                + mRestrictionOnWwanByRtpThresholdBreached
                + ", mRestrictionWwanImsRegiFail="
                + mRestrictionOnWlanByImsRegistrationFailed
                + ", mRestrictionWwanWifiBackhaulProblem="
                + mRestrictionOnWlanByWifiBackhaulProblem
                + ", mCarrierId="
                + mCarrierId
                + ", mSlotIndex="
                + mSlotIndex
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsQnsFallbackRestrictionChangedInfo)) return false;
        AtomsQnsFallbackRestrictionChangedInfo that = (AtomsQnsFallbackRestrictionChangedInfo) o;
        return mRestrictionOnWlanByRtpThresholdBreached
                        == that.mRestrictionOnWlanByRtpThresholdBreached
                && mRestrictionOnWwanByRtpThresholdBreached
                        == that.mRestrictionOnWwanByRtpThresholdBreached
                && mRestrictionOnWlanByImsRegistrationFailed
                        == that.mRestrictionOnWlanByImsRegistrationFailed
                && mRestrictionOnWlanByWifiBackhaulProblem
                        == that.mRestrictionOnWlanByWifiBackhaulProblem
                && mCarrierId == that.mCarrierId
                && mSlotIndex == that.mSlotIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mRestrictionOnWlanByRtpThresholdBreached,
                mRestrictionOnWwanByRtpThresholdBreached,
                mRestrictionOnWlanByImsRegistrationFailed,
                mRestrictionOnWlanByWifiBackhaulProblem,
                mCarrierId,
                mSlotIndex);
    }
}
