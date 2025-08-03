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

/** AtomsQnsHandoverPingPongInfo class */
public class AtomsQnsHandoverPingPongInfo extends AtomsPulled implements Serializable {

    private static final long serialVersionUID = 815898959L; // 0x30A1A14F

    /** atom #1 : Count of handover ping-pong */
    private int mCountHandoverPingPong;

    /** atom #2 : Carrier Id */
    private int mCarrierId;

    /** atom #3 : Slot Index */
    private int mSlotIndex;

    /** Constructor of AtomsQnsHandoverPingPongInfo */
    public AtomsQnsHandoverPingPongInfo() {}

    /**
     * Constructor of AtomsQnsHandoverPingPongInfo
     *
     * @param countHandoverPingPong count of Handover Ping-Pong
     * @param carrierId Carrier Id
     * @param slotIndex Index of sim slot
     */
    public AtomsQnsHandoverPingPongInfo(int countHandoverPingPong, int carrierId, int slotIndex) {
        mCountHandoverPingPong = countHandoverPingPong;
        mCarrierId = carrierId;
        mSlotIndex = slotIndex;
    }

    /**
     * Copy Constructor of AtomsQnsHandoverPingPongInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsQnsHandoverPingPongInfo(AtomsQnsHandoverPingPongInfo info) {
        mCountHandoverPingPong = info.mCountHandoverPingPong;
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
        builder.writeInt(mCountHandoverPingPong); // atom #1
        builder.writeInt(mCarrierId); // atom #2
        builder.writeInt(mSlotIndex); // atom #3
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return QnsStatsLog.QNS_HANDOVER_PINGPONG;
    }

    /** Return copy of the AtomsQnsHandoverPingPongInfo */
    @Override
    public AtomsPulled copy() {
        return new AtomsQnsHandoverPingPongInfo(this);
    }

    @Override
    public String getDimension() {
        return mCarrierId + "_" + mSlotIndex;
    }

    @Override
    public void accumulate(AtomsPulled info) {
        if (!(info instanceof AtomsQnsHandoverPingPongInfo)) {
            return;
        }
        AtomsQnsHandoverPingPongInfo atomsQnsHandoverPingPongInfo =
                (AtomsQnsHandoverPingPongInfo) info;
        this.mCountHandoverPingPong += atomsQnsHandoverPingPongInfo.getCountHandoverPingPong();
    }

    public int getCountHandoverPingPong() {
        return mCountHandoverPingPong;
    }

    public void setCountHandoverPingPong(int countHandoverPingPong) {
        mCountHandoverPingPong = countHandoverPingPong;
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
        return "AtomsQnsHandoverPingPongInfo{"
                + "mCountHandoverPingPong="
                + mCountHandoverPingPong
                + ", mCarrierId="
                + mCarrierId
                + ", mSlotIndex="
                + mSlotIndex
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsQnsHandoverPingPongInfo)) return false;
        AtomsQnsHandoverPingPongInfo that = (AtomsQnsHandoverPingPongInfo) o;
        return mCountHandoverPingPong == that.mCountHandoverPingPong
                && mCarrierId == that.mCarrierId
                && mSlotIndex == that.mSlotIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCountHandoverPingPong, mCarrierId, mSlotIndex);
    }

    public static long PING_PONG_TIME_IN_MILLIS = 5000L;
}
