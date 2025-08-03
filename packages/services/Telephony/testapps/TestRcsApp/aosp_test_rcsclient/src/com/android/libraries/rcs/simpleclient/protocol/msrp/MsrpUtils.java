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

package com.android.libraries.rcs.simpleclient.protocol.msrp;

import com.android.libraries.rcs.simpleclient.protocol.sip.SipUtils;
import java.security.SecureRandom;

/** Collections of utility functions for MSRP */
public final class MsrpUtils {

    private static final SecureRandom random = new SecureRandom();

    private MsrpUtils() {
    }

    /** Generate a path attribute defined in RFC 4975 for the given address, port. */
    public static String generatePath(String address, int port, boolean isSecure) {
        StringBuilder builder = new StringBuilder();

        if (SipUtils.isIPv6Address(address)) {
            address = "[" + address + "]";
        }

        builder
                .append(isSecure ? "msrps" : "msrp")
                .append("://")
                .append(address)
                .append(":")
                .append(port)
                .append("/")
                .append(System.currentTimeMillis())
                .append(";tcp");

        return builder.toString();
    }

    public static String generateRandomId() {
        byte[] randomBytes = new byte[8];
        random.nextBytes(randomBytes);
        String hex = "";
        for (byte b : randomBytes) {
            hex = hex + String.format("%02x", b);
        }
        return hex;
    }
}
