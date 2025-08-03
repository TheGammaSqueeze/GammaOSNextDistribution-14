/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.net;

import com.android.net.module.util.Struct;

import java.util.Arrays;

/**
 * The value of bpf interface index map which is used for NetworkStatsService.
 */
public class InterfaceMapValue extends Struct {
    private static final int IF_NAME_SIZE = 16;

    @Field(order = 0, type = Type.ByteArray, arraysize = 16)
    public final byte[] interfaceName;

    public InterfaceMapValue(String iface) {
        // All array bytes after the interface name, if any, must be 0.
        interfaceName = Arrays.copyOf(iface.getBytes(), IF_NAME_SIZE);
    }

    /**
     * Constructor for Struct#parse. Build this struct from byte array of interface name.
     *
     * @param ifName Byte array of interface name, length is expected to be IF_NAME_SIZE(16).
     *               If longer or shorter, interface name will be truncated or padded with zeros.
     *               All array bytes after the interface name, if any, must be 0.
     */
    public InterfaceMapValue(final byte[] ifName) {
        interfaceName = Arrays.copyOf(ifName, IF_NAME_SIZE);
    }

    /** Returns the length of the null-terminated string. */
    private int strlen(byte[] str) {
        for (int i = 0; i < str.length; ++i) {
            if (str[i] == '\0') {
                return i;
            }
        }
        return str.length;
    }

    public String getInterfaceNameString() {
        return new String(interfaceName, 0 /* offset */,  strlen(interfaceName));
    }
}
