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
package com.android.net.module.util.netlink.xfrm;

import static com.android.net.module.util.NetworkStackConstants.IPV4_ADDR_LEN;

import android.system.OsConstants;

import androidx.annotation.NonNull;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Struct xfrm_address_t
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * typedef union {
 *      __be32 a4;
 *      __be32 a6[4];
 *      struct in6_addr in6;
 * } xfrm_address_t;
 * </pre>
 *
 * @hide
 */
public class StructXfrmAddressT extends Struct {
    public static final int STRUCT_SIZE = 16;

    @Field(order = 0, type = Type.ByteArray, arraysize = STRUCT_SIZE)
    public final byte[] address;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmAddressT(@NonNull final byte[] address) {
        this.address = address.clone();
    }

    // Constructor to build a new message
    public StructXfrmAddressT(@NonNull final InetAddress inetAddress) {
        this.address = new byte[STRUCT_SIZE];
        final byte[] addressBytes = inetAddress.getAddress();
        System.arraycopy(addressBytes, 0, address, 0, addressBytes.length);
    }

    /** Return the address in InetAddress */
    public InetAddress getAddress(int family) {
        final byte[] addressBytes;
        if (family == OsConstants.AF_INET6) {
            addressBytes = this.address;
        } else if (family == OsConstants.AF_INET) {
            addressBytes = new byte[IPV4_ADDR_LEN];
            System.arraycopy(this.address, 0, addressBytes, 0, addressBytes.length);
        } else {
            throw new IllegalArgumentException("Invalid IP family " + family);
        }

        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            // This should never happen
            throw new IllegalArgumentException(
                    "Illegal length of IP address " + addressBytes.length, e);
        }
    }
}
