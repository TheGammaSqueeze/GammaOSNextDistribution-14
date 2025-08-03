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

/** AtomsQnsHandoverTimeMillisInfo class */
public class AtomsQnsHandoverTimeMillisInfo extends AtomsPulled implements Serializable {

    private static final long serialVersionUID = 1379441656L; // 0x52389BF8

    /** atom #1 : Time in milliseconds from QNS RAT update to successful HO completion */
    private int mTimeForHoSuccess;

    /** atom #2 : Slot Index */
    private int mSlotIndex;

    /** Constructor of AtomsQnsHandoverTimeMillisInfo */
    public AtomsQnsHandoverTimeMillisInfo() {}

    /**
     * Constructor of AtomsQnsHandoverTimeMillisInfo
     *
     * @param timeForHoSuccess Time in milliseconds from QNS RAT update to successful HO completion
     * @param slotIndex Index of sim slot
     */
    public AtomsQnsHandoverTimeMillisInfo(int timeForHoSuccess, int slotIndex) {
        mTimeForHoSuccess = timeForHoSuccess;
        mSlotIndex = slotIndex;
    }

    /**
     * Copy Constructor of AtomsQnsHandoverTimeMillisInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsQnsHandoverTimeMillisInfo(AtomsQnsHandoverTimeMillisInfo info) {
        mTimeForHoSuccess = info.mTimeForHoSuccess;
        mSlotIndex = info.mSlotIndex;
    }

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    @Override
    public void build(StatsEvent.Builder builder) {
        builder.writeInt(mTimeForHoSuccess); // atom #1
        builder.writeInt(mSlotIndex); // atom #2
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return QnsStatsLog.QNS_HANDOVER_TIME_MILLIS;
    }

    /** Return copy of the AtomsQnsHandoverTimeMillisInfo */
    @Override
    public AtomsPulled copy() {
        return new AtomsQnsHandoverTimeMillisInfo(this);
    }

    @Override
    public String getDimension() {
        return Integer.toString(mSlotIndex);
    }

    @Override
    public void accumulate(AtomsPulled info) {
        if (!(info instanceof AtomsQnsRatPreferenceMismatchInfo)) {
            return;
        }
        AtomsQnsHandoverTimeMillisInfo atomsQnsHandoverTimeMillisInfo =
                (AtomsQnsHandoverTimeMillisInfo) info;
        this.mTimeForHoSuccess += atomsQnsHandoverTimeMillisInfo.getTimeForHoSuccess();
    }

    public int getTimeForHoSuccess() {
        return mTimeForHoSuccess;
    }

    public void setTimeForHoSuccess(int timeForHoSuccess) {
        mTimeForHoSuccess = timeForHoSuccess;
    }

    public int getSlotIndex() {
        return mSlotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        mSlotIndex = slotIndex;
    }

    @Override
    public String toString() {
        return "AtomsQnsHandoverTimeMillisInfo{" + "mTimeForHoSuccess=" + mTimeForHoSuccess + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsQnsHandoverTimeMillisInfo)) return false;
        AtomsQnsHandoverTimeMillisInfo that = (AtomsQnsHandoverTimeMillisInfo) o;
        return mTimeForHoSuccess == that.mTimeForHoSuccess && mSlotIndex == that.mSlotIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTimeForHoSuccess, mSlotIndex);
    }
}
