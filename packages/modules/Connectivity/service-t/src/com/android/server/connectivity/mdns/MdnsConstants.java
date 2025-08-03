/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity.mdns;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

/** mDNS-related constants. */
public final class MdnsConstants {
    public static final int MDNS_PORT = 5353;
    // Flags word format is:
    // 15 14 13 12 11 10 09 08 07 06 05 04 03 02 01 00
    // QR [ Opcode  ] AA TC RD RA  Z AD CD [  Rcode  ]
    // See http://www.networksorcery.com/enp/protocol/dns.htm
    // For responses, QR bit should be 1, AA - CD bits should be ignored, and all other bits
    // should be 0.
    public static final int FLAGS_QUERY = 0x0000;
    public static final int FLAGS_RESPONSE_MASK = 0xF80F;
    public static final int FLAGS_RESPONSE = 0x8000;
    public static final int FLAG_TRUNCATED = 0x0200;
    public static final int QCLASS_INTERNET = 0x0001;
    public static final int QCLASS_UNICAST = 0x8000;
    public static final int NO_PACKET = 0;
    public static final String SUBTYPE_LABEL = "_sub";
    public static final String SUBTYPE_PREFIX = "_";
    private static final String MDNS_IPV4_HOST_ADDRESS = "224.0.0.251";
    private static final String MDNS_IPV6_HOST_ADDRESS = "FF02::FB";
    public static final InetSocketAddress IPV6_SOCKET_ADDR = new InetSocketAddress(
            getMdnsIPv6Address(), MDNS_PORT);
    public static final InetSocketAddress IPV4_SOCKET_ADDR = new InetSocketAddress(
            getMdnsIPv4Address(), MDNS_PORT);
    private static InetAddress mdnsAddress;
    private MdnsConstants() {
    }

    public static InetAddress getMdnsIPv4Address() {
        synchronized (MdnsConstants.class) {
            InetAddress addr = null;
            try {
                addr = InetAddress.getByName(MDNS_IPV4_HOST_ADDRESS);
            } catch (UnknownHostException e) {
                /* won't happen */
            }
            mdnsAddress = addr;
            return mdnsAddress;
        }
    }

    public static InetAddress getMdnsIPv6Address() {
        synchronized (MdnsConstants.class) {
            InetAddress addr = null;
            try {
                addr = InetAddress.getByName(MDNS_IPV6_HOST_ADDRESS);
            } catch (UnknownHostException e) {
                /* won't happen */
            }
            mdnsAddress = addr;
            return mdnsAddress;
        }
    }

    public static Charset getUtf8Charset() {
        return UTF_8;
    }
}
