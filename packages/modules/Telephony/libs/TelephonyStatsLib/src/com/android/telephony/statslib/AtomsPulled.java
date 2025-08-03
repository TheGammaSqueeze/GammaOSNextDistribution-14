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

/** AtomsPulled class */
public abstract class AtomsPulled {

    /** Constructor of AtomsPulled */
    public AtomsPulled() {}

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    public abstract void build(StatsEvent.Builder builder);

    /** Return atom id defined in proto. */
    public abstract int getStatsId();

    /** Return copy of the AtomsPulled */
    public abstract AtomsPulled copy();

    /**
     * Return dimension string for pulled atoms.
     *
     * <p>Used for Pulled Atoms only. Pulled atoms should report the accumulated data at the time of
     * the callback. The same type of information should be reported at once. (e.g. one per
     * NetCapability for CountHandoverFailure)
     *
     * @return key string of atoms dimension
     */
    public abstract String getDimension();

    /**
     * Accumulate info to this. Used for Pulled Atoms only.
     *
     * @param info atoms to be accumulated to
     */
    public abstract void accumulate(AtomsPulled info);
}
