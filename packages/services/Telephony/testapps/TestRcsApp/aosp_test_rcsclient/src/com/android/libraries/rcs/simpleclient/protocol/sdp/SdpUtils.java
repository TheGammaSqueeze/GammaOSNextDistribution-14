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

package com.android.libraries.rcs.simpleclient.protocol.sdp;

import static com.android.libraries.rcs.simpleclient.protocol.sip.SipUtils.isIPv6Address;

import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

/** Collections of utility functions for SDP */
public final class SdpUtils {
    public static final String SDP_CONTENT_TYPE = "application";
    public static final String SDP_CONTENT_SUB_TYPE = "sdp";

    private static final ImmutableSet<String> DEFAULT_ACCEPT_TYPES =
            ImmutableSet.of("message/cpim", "application/im-iscomposing+xml");
    private static final ImmutableSet<String> DEFAULT_ACCEPT_WRAPPED_TYPES =
            ImmutableSet.of(
                    "text/plain",
                    "message/imdn+xml",
                    "application/vnd.gsma.rcs-ft-http+xml",
                    "application/vnd.gsma.rcspushlocation+xml");

    private static final String DEFAULT_NAME = "message";
    private static final String DEFAULT_SETUP = "active";
    private static final String DEFAULT_DIRECTION = "sendrecv";
    private static final int DEFAULT_MSRP_PORT = 9;
    private static final String PROTOCOL_TCP_MSRP = "TCP/MSRP";
    private static final String PROTOCOL_TLS_MSRP = "TCP/TLS/MSRP";
    private static final String DEFAULT_FORMAT = "*";

    private static final String ATTRIBUTE_PATH = "path";
    private static final String ATTRIBUTE_SETUP = "setup";
    private static final String ATTRIBUTE_ACCEPT_TYPES = "accept-types";
    private static final String ATTRIBUTE_ACCEPT_WRAPPED_TYPES = "accept-wrapped-types";

    private SdpUtils() {
    }

    /**
     * Create a simple SDP message for MSRP. Most attributes except address and transport type
     * will be
     * generated automatically.
     *
     * @param address The local IP address of the MSRP connection.
     * @param isTls   True if the MSRP connection uses TLS.
     */
    public static SimpleSdpMessage createSdpForMsrp(String address, boolean isTls) {
        return SimpleSdpMessage.newBuilder()
                .setVersion("0")
                .setOrigin(generateOrigin(address))
                .setSession("-")
                .setConnection(generateConnection(address))
                .setTime("0 0")
                .addMedia(createSdpMediaForMsrp(address, isTls))
                .build();
    }

    private static String generateOrigin(String address) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("TestRcsClient ")
                .append(System.currentTimeMillis())
                .append(" ")
                .append(System.currentTimeMillis())
                .append(" IN ")
                .append(isIPv6Address(address) ? "IP6 " : "IP4 ")
                .append(address);

        return builder.toString();
    }

    private static String generateConnection(String address) {
        return "IN " + (isIPv6Address(address) ? "IP6 " : "IP4 ") + address;
    }

    /**
     * Create a media part of the SDP message for MSRP. Most attributes except address and transport
     * type will be generated automatically.
     *
     * @param address The local IP address of the MSRP connection.
     * @param isTls   True if the MSRP connection uses TLS.
     */
    public static SdpMedia createSdpMediaForMsrp(String address, boolean isTls) {
        return SdpMedia.newBuilder()
                .setName(DEFAULT_NAME)
                .setPort(DEFAULT_MSRP_PORT)
                .setProtocol(isTls ? PROTOCOL_TLS_MSRP : PROTOCOL_TCP_MSRP)
                .setFormat(DEFAULT_FORMAT)
                .addAttribute(ATTRIBUTE_PATH,
                        MsrpUtils.generatePath(address, DEFAULT_MSRP_PORT, isTls))
                .addAttribute(ATTRIBUTE_SETUP, DEFAULT_SETUP)
                .addAttribute(ATTRIBUTE_ACCEPT_TYPES, Joiner.on(" ").join(DEFAULT_ACCEPT_TYPES))
                .addAttribute(
                        ATTRIBUTE_ACCEPT_WRAPPED_TYPES,
                        Joiner.on(" ").join(DEFAULT_ACCEPT_WRAPPED_TYPES))
                .addAttribute(DEFAULT_DIRECTION)
                .build();
    }
}
