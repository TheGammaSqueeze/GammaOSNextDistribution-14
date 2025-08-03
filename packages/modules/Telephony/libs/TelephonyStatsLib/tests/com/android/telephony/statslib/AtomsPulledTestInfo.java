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

package com.android.telephony.statslib;

import android.util.StatsEvent;

import java.util.Objects;

/** AtomsPulledTestInfo class */
public class AtomsPulledTestInfo extends AtomsPulled {

    /** atom #1 : test atom #1 type */
    private int mType;

    /** atom #2 : test atom #2 count */
    private int mCount;

    /** Constructor of AtomsTestInfo */
    public AtomsPulledTestInfo() {}

    /** Constructor of AtomsPulledTestInfo */
    public AtomsPulledTestInfo(int type, int count) {
        mType = type;
        mCount = count;
    }

    /**
     * Copy Constructor of AtomsPulledTestInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsPulledTestInfo(AtomsPulledTestInfo info) {
        mType = info.mType;
        mCount = info.mCount;
    }

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    @Override
    public void build(StatsEvent.Builder builder) {
        builder.writeInt(mType); // atom #1
        builder.writeInt(mCount); // atom #2
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return 700;
    }

    /** Return copy of the AtomsTestInfo */
    @Override
    public AtomsPulled copy() {
        return new AtomsPulledTestInfo(this);
    }

    @Override
    public String getDimension() {
        return Integer.toString(mType);
    }

    public void accumulate(AtomsPulled info) {
        if (!(info instanceof AtomsPulledTestInfo)) {
            return;
        }
        AtomsPulledTestInfo atomsPulledTestInfo = (AtomsPulledTestInfo) info;
        this.mCount += atomsPulledTestInfo.getCount();
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public int getCount() {
        return mCount;
    }

    public void setCount(int count) {
        mCount = count;
    }

    @Override
    public String toString() {
        return "AtomsPulledTestInfo{" + "mType=" + mType + ", mCount=" + mCount + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsPulledTestInfo)) return false;
        AtomsPulledTestInfo that = (AtomsPulledTestInfo) o;
        return mType == that.mType && mCount == that.mCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mCount);
    }
}
