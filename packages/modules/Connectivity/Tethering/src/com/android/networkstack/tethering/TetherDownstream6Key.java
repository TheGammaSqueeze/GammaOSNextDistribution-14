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
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/** The key of BpfMap which is used for bpf offload. */
public class TetherDownstream6Key extends Struct {
    @Field(order = 0, type = Type.S32)
    public final int iif; // The input interface index.

    @Field(order = 1, type = Type.EUI48, padding = 2)
    public final MacAddress dstMac; // Destination ethernet mac address (zeroed iff rawip ingress).

    @Field(order = 2, type = Type.ByteArray, arraysize = 16)
    public final byte[] neigh6; // The destination IPv6 address.

    public TetherDownstream6Key(final int iif, @NonNull final MacAddress dstMac,
            final byte[] neigh6) {
        Objects.requireNonNull(dstMac);

        try {
            final Inet6Address unused = (Inet6Address) InetAddress.getByAddress(neigh6);
        } catch (ClassCastException | UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 address: "
                    + Arrays.toString(neigh6));
        }
        this.iif = iif;
        this.dstMac = dstMac;
        this.neigh6 = neigh6;
    }

    @Override
    public String toString() {
        try {
            return String.format("iif: %d, dstMac: %s, neigh: %s", iif, dstMac,
                    Inet6Address.getByAddress(neigh6));
        } catch (UnknownHostException e) {
            // Should not happen because construtor already verify neigh6.
            throw new IllegalStateException("Invalid TetherDownstream6Key");
        }
    }
}
