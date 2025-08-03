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

package com.android.net.module.util;

import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.DnsPacketUtils.DnsRecordParser;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Utilities for encoding/decoding the domain name or domain search list.
 *
 * @hide
 */
public final class DomainUtils {
    private static final String TAG = "DomainUtils";
    private static final int MAX_OPTION_LEN = 255;

    @NonNull
    private static String getSubstring(@NonNull final String string, @NonNull final String[] labels,
            int index) {
        int beginIndex = 0;
        for (int i = 0; i < index; i++) {
            beginIndex += labels[i].length() + 1; // include the dot
        }
        return string.substring(beginIndex);
    }

    /**
     * Encode the given single domain name to byte array, comply with RFC1035 section-3.1.
     *
     * @return null if the given domain string is invalid, otherwise, return a byte array
     *         wrapping the encoded domain, not including any padded octets, caller should
     *         pad zero octets at the end if needed.
     */
    @Nullable
    public static byte[] encode(@NonNull final String domain) {
        if (!DnsRecordParser.isHostName(domain)) return null;
        return encode(new String[]{ domain }, false /* compression */);
    }

    /**
     * Encode the given multiple domain names to byte array, comply with RFC1035 section-3.1
     * and section 4.1.4 (message compression) if enabled.
     *
     * @return Null if encode fails due to BufferOverflowException, otherwise, return a byte
     *         array wrapping the encoded domains, not including any padded octets, caller
     *         should pad zero octets at the end if needed. The byte array may be empty if
     *         the given domain strings are invalid.
     */
    @Nullable
    public static byte[] encode(@NonNull final String[] domains, boolean compression) {
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(MAX_OPTION_LEN);
            final ArrayMap<String, Integer> offsetMap = new ArrayMap<>();
            for (int i = 0; i < domains.length; i++) {
                if (!DnsRecordParser.isHostName(domains[i])) {
                    Log.e(TAG, "Skip invalid domain name " + domains[i]);
                    continue;
                }
                final String[] labels = domains[i].split("\\.");
                for (int j = 0; j < labels.length; j++) {
                    if (compression) {
                        final String suffix = getSubstring(domains[i], labels, j);
                        if (offsetMap.containsKey(suffix)) {
                            int offsetOfSuffix = offsetMap.get(suffix);
                            offsetOfSuffix |= 0xC000;
                            buffer.putShort((short) offsetOfSuffix);
                            break; // unnecessary to put the compressed string into map
                        } else {
                            offsetMap.put(suffix, buffer.position());
                        }
                    }
                    // encode the domain name string without compression when:
                    // - compression feature isn't enabled,
                    // - suffix does not match any string in the map.
                    final byte[] labelBytes = labels[j].getBytes(StandardCharsets.UTF_8);
                    buffer.put((byte) labelBytes.length);
                    buffer.put(labelBytes);
                    if (j == labels.length - 1) {
                        // Pad terminate label at the end of last label.
                        buffer.put((byte) 0);
                    }
                }
            }
            buffer.flip();
            final byte[] out = new byte[buffer.limit()];
            buffer.get(out);
            return out;
        } catch (BufferOverflowException e) {
            Log.e(TAG, "Fail to encode domain name and stop encoding", e);
            return null;
        }
    }

    /**
     * Decode domain name(s) from the given byteBuffer. Decode follows RFC1035 section 3.1 and
     * section 4.1.4(message compression).
     *
     * @return domain name(s) string array with space separated, or empty string if decode fails.
     */
    @NonNull
    public static ArrayList<String> decode(@NonNull final ByteBuffer buffer, boolean compression) {
        final ArrayList<String> domainList = new ArrayList<>();
        while (buffer.remaining() > 0) {
            try {
                // TODO: replace the recursion with loop in parseName and don't need to pass in the
                // maxLabelCount parameter to prevent recursion from overflowing stack.
                final String domain = DnsRecordParser.parseName(buffer, 0 /* depth */,
                        15 /* maxLabelCount */, compression);
                if (!DnsRecordParser.isHostName(domain)) continue;
                domainList.add(domain);
            } catch (BufferUnderflowException | DnsPacket.ParseException e) {
                Log.e(TAG, "Fail to parse domain name and stop parsing", e);
                break;
            }
        }
        return domainList;
    }
}
