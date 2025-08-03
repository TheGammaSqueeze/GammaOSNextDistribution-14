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

/** AtomsTestInfo class */
public class AtomsPushedTestInfo extends AtomsPushed {

    /** atom #1 : test atom #1 */
    private int mTestAtom;

    /** Constructor of AtomsTestInfo */
    public AtomsPushedTestInfo() {}

    /**
     * Constructor of AtomsTestInfo
     *
     * @param testAtom initial value
     */
    public AtomsPushedTestInfo(int testAtom) {
        mTestAtom = testAtom;
    }

    /**
     * Copy Constructor of AtomsTestInfo
     *
     * @param info The info param to copy from.
     */
    public AtomsPushedTestInfo(AtomsPushedTestInfo info) {
        mTestAtom = info.mTestAtom;
    }

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    @Override
    public void build(StatsEvent.Builder builder) {
        builder.writeInt(mTestAtom); // atom #1
    }

    /** Return atom id defined in proto. */
    @Override
    public int getStatsId() {
        return 701;
    }

    /** Return copy of the AtomsTestInfo */
    @Override
    public AtomsPushed copy() {
        return new AtomsPushedTestInfo(this);
    }

    public int getTestAtom() {
        return mTestAtom;
    }

    public void setTestAtom(int testAtom) {
        mTestAtom = testAtom;
    }

    @Override
    public String toString() {
        return "AtomsPushedTestInfo{" + "mTestAtom=" + mTestAtom + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomsPushedTestInfo)) return false;
        AtomsPushedTestInfo that = (AtomsPushedTestInfo) o;
        return mTestAtom == that.mTestAtom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTestAtom);
    }
}
