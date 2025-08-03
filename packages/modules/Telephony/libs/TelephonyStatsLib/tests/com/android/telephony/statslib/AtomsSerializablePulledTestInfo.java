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

import java.io.Serializable;
import java.util.Objects;

/** AtomsSerializablePulledTestInfo class */
public class AtomsSerializablePulledTestInfo extends AtomsPulled implements Serializable {

    private static final long serialVersionUID = 804402117L; // 0x2FF233C5

    /** atom #1 : test atom #1 type */
    private int mType;

    /** atom #2 : test atom #2 count */
    private int mCount;

    /** transient variable which will not be persisted */
    private transient int mTransient;

    /** Constructor of AtomsTestInfo */
    public AtomsSerializablePulledTestInfo() {}

    /** Constructor of AtomsSerializablePulledTestInfo */
    public AtomsSerializablePulledTestInfo(int type, int count) {
        mType = type;
        mCount = count;
    }

    /**
     * Copy Constructor of AtomsSerializablePulledTestInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsSerializablePulledTestInfo(AtomsSerializablePulledTestInfo info) {
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
        return 702;
    }

    /** Return copy of the AtomsTestInfo */
    @Override
    public AtomsPulled copy() {
        return new AtomsSerializablePulledTestInfo(this);
    }

    @Override
    public String getDimension() {
        return Integer.toString(mType);
    }

    public void accumulate(AtomsPulled info) {
        if (!(info instanceof AtomsSerializablePulledTestInfo)) {
            return;
        }
        AtomsSerializablePulledTestInfo atomsSerializablePulledTestInfo =
                (AtomsSerializablePulledTestInfo) info;
        this.mCount += atomsSerializablePulledTestInfo.getCount();
        this.mTransient += atomsSerializablePulledTestInfo.getTransient();
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

    public void setTransient(int aTransient) {
        mTransient = aTransient;
    }

    public int getTransient() {
        return mTransient;
    }

    @Override
    public String toString() {
        return "AtomsSerializablePulledTestInfo{"
                + "mType="
                + mType
                + ", mCount="
                + mCount
                + ", mTransient="
                + mTransient
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsSerializablePulledTestInfo)) return false;
        AtomsSerializablePulledTestInfo that = (AtomsSerializablePulledTestInfo) o;
        return mType == that.mType && mCount == that.mCount && mTransient == that.mTransient;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mCount, mTransient);
    }
}
