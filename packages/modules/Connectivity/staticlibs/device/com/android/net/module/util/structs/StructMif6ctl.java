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

package com.android.net.module.util.structs;

import com.android.net.module.util.Struct;

/*
 * Implements the mif6ctl structure which is used to add a multicast routing
 * interface, see /usr/include/linux/mroute6.h
 */
public class StructMif6ctl extends Struct {
    @Field(order = 0, type = Type.U16)
    public final int mif6cMifi; // Index of MIF
    @Field(order = 1, type = Type.U8)
    public final short mif6cFlags; // MIFF_ flags
    @Field(order = 2, type = Type.U8)
    public final short vifcThreshold; // ttl limit
    @Field(order = 3, type = Type.U16)
    public final int mif6cPifi; //the index of the physical IF
    @Field(order = 4, type = Type.U32, padding = 2)
    public final long vifcRateLimit; // Rate limiter values (NI)

    public StructMif6ctl(final int mif6cMifi, final short mif6cFlags, final short vifcThreshold,
            final int mif6cPifi, final long vifcRateLimit) {
        this.mif6cMifi = mif6cMifi;
        this.mif6cFlags = mif6cFlags;
        this.vifcThreshold = vifcThreshold;
        this.mif6cPifi = mif6cPifi;
        this.vifcRateLimit = vifcRateLimit;
    }
}

