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

package android.bluetooth;

import static com.google.common.io.BaseEncoding.base16;

import com.google.protobuf.ByteString;

import java.util.Locale;

public final class Utils {
    // From bumble_config.json
    public static final String BUMBLE_RANDOM_ADDRESS = "51:F7:A8:75:AC:5E";
    public static final byte[] BUMBLE_IRK = base16().decode("1F66F4B5F0C742F807DD0DDBF64E9213");

    public static String addressStringFromByteString(ByteString bs) {
        StringBuilder refAddrBuilder = new StringBuilder();
        for (int i = 0; i < bs.size(); i++) {
            if (i != 0) {
                refAddrBuilder.append(':');
            }
            refAddrBuilder.append(String.format("%02X", bs.byteAt(i)));
        }
        return refAddrBuilder.toString();
    }

    /**
     * @param address String representing Bluetooth address (case insensitive).
     * @return Decoded address.
     */
    public static byte[] addressBytesFromString(String address) {
        return base16().upperCase().withSeparator(":", 2).decode(address.toUpperCase(Locale.US));
    }
}
