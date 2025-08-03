/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.networkstack.tethering;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

/** The value of BpfMap which is used for tethering per-interface limit. */
public class TetherLimitValue extends Struct {
    // Use the signed long variable to store the int64 limit on limit BPF map.
    // S64 is enough for each interface limit even at 5Gbps for ~468 years.
    // 2^63 / (5 * 1000 * 1000 * 1000) * 8 / 86400 / 365 = 468.
    // Note that QUOTA_UNLIMITED (-1) indicates there is no limit.
    @Field(order = 0, type = Type.S64)
    public final long limit;

    public TetherLimitValue(final long limit) {
        this.limit = limit;
    }
}
