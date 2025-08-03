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

/** AtomsPushed class */
public abstract class AtomsPushed {

    /** Constructor of AtomsPushed */
    public AtomsPushed() {}

    /**
     * Write the atom information to be recorded to the builder according to the type in order.
     *
     * @param builder Builder class for StatsEvent Builder object.
     */
    public abstract void build(StatsEvent.Builder builder);

    /** Return atom id defined in proto. */
    public abstract int getStatsId();

    /** Return copy of the AtomsPushed */
    public abstract AtomsPushed copy();
}
