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
import com.android.telephony.statslib.AtomsPulled;

import java.io.Serializable;
import java.util.Objects;

/** AtomsQnsRatPreferenceMismatchInfo class */
public class AtomsQnsRatPreferenceMismatchInfo extends AtomsPulled implements Serializable {

    private static final long serialVersionUID = 1913485192L; // 0x720D7788

    /** atom #1 : Net capability of this information. */
    private int mNetCapability;

    /** atom #2 : Count of handover failed. */
    private int mHandoverFailCount;

    /** atom #3 : Duration of this mismatch. */
    private int mDurationOfMismatch;

    /** atom #4 : Carrier Id */
    private int mCarrierId;

    /** atom #5 : Slot Index */
    private int mSlotIndex;

    /** Constructor of AtomsQnsRatPreferenceMismatchInfo */
    public AtomsQnsRatPreferenceMismatchInfo() {}

    /**
     * Constructor of AtomsQnsRatPreferenceMismatchInfo
     *
     * @param netCapability Net capability of this information.
     * @param handoverFailCount Count of handover failed.
     * @param durationOfMismatch Duration of this mismatch.
     * @param carrierId Carrier Id
     * @param slotIndex Index of sim slot
     */
    public AtomsQnsRatPreferenceMismatchInfo(
            int netCapability,
            int handoverFailCount,
            int durationOfMismatch,
            int carrierId,
            int slotIndex) {
        mNetCapability = netCapability;
        mHandoverFailCount = handoverFailCount;
        mDurationOfMismatch = durationOfMismatch;
        mCarrierId = carrierId;
        mSlotIndex = slotIndex;
    }

    /**
     * Copy Constructor of AtomsQnsRatPreferenceMismatchInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsQnsRatPreferenceMismatchInfo(AtomsQnsRatPreferenceMismatchInfo info) {
        mNetCapability = info.mNetCapability;
        mHandoverFailCount = info.mHandoverFailCount;
        mDurationOfMismatch = info.mDurationOfMismatch;
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
        builder.writeInt(mNetCapability); // atom #1
        builder.writeInt(mHandoverFailCount); // atom #2
        builder.writeInt(mDurationOfMismatch); // atom #3
        builder.writeInt(mCarrierId); // atom #4
        builder.writeInt(mSlotIndex); // atom #5
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return QnsStatsLog.QNS_RAT_PREFERENCE_MISMATCH_INFO;
    }

    /** Return copy of the AtomsQnsRatPreferenceMismatchInfo */
    @Override
    public AtomsPulled copy() {
        return new AtomsQnsRatPreferenceMismatchInfo(this);
    }

    @Override
    public String getDimension() {
        return mNetCapability + "_" + mCarrierId + "_" + mSlotIndex;
    }

    @Override
    public void accumulate(AtomsPulled info) {
        if (!(info instanceof AtomsQnsRatPreferenceMismatchInfo)) {
            return;
        }
        AtomsQnsRatPreferenceMismatchInfo atomsQnsRatPreferenceMismatchInfo =
                (AtomsQnsRatPreferenceMismatchInfo) info;
        this.mDurationOfMismatch += atomsQnsRatPreferenceMismatchInfo.getDurationOfMismatch();
        this.mHandoverFailCount += atomsQnsRatPreferenceMismatchInfo.getHandoverFailCount();
    }

    public int getNetCapability() {
        return mNetCapability;
    }

    public void setNetCapability(int netCapability) {
        mNetCapability = netCapability;
    }

    public int getHandoverFailCount() {
        return mHandoverFailCount;
    }

    public void setHandoverFailCount(int handoverFailCount) {
        mHandoverFailCount = handoverFailCount;
    }

    public int getDurationOfMismatch() {
        return mDurationOfMismatch;
    }

    public void setDurationOfMismatch(int durationOfMismatch) {
        mDurationOfMismatch = durationOfMismatch;
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
        return "AtomsQnsRatPreferenceMismatchInfo{"
                + "mNetCapability="
                + mNetCapability
                + ", mHandoverFailCount="
                + mHandoverFailCount
                + ", mDurationOfMismatch="
                + mDurationOfMismatch
                + ", mCarrierId="
                + mCarrierId
                + ", mSlotIndex="
                + mSlotIndex
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsQnsRatPreferenceMismatchInfo)) return false;
        AtomsQnsRatPreferenceMismatchInfo that = (AtomsQnsRatPreferenceMismatchInfo) o;
        return mNetCapability == that.mNetCapability
                && mHandoverFailCount == that.mHandoverFailCount
                && mDurationOfMismatch == that.mDurationOfMismatch
                && mCarrierId == that.mCarrierId
                && mSlotIndex == that.mSlotIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mNetCapability, mHandoverFailCount, mDurationOfMismatch, mCarrierId, mSlotIndex);
    }
}
