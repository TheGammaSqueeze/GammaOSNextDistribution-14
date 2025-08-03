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

import android.net.MacAddress;

import androidx.annotation.NonNull;

import com.android.net.module.util.Struct;

import java.util.Objects;

/** Key type for upstream IPv6 forwarding map. */
public class TetherUpstream6Key extends Struct {
    @Field(order = 0, type = Type.S32)
    public final int iif; // The input interface index.

    @Field(order = 1, type = Type.EUI48, padding = 6)
    public final MacAddress dstMac; // Destination ethernet mac address (zeroed iff rawip ingress).

    @Field(order = 2, type = Type.ByteArray, arraysize = 8)
    public final byte[] src64; // The top 64-bits of the source ip.

    public TetherUpstream6Key(int iif, @NonNull final MacAddress dstMac, final byte[] src64) {
        Objects.requireNonNull(dstMac);

        this.iif = iif;
        this.dstMac = dstMac;
        this.src64 = src64;
    }
}
