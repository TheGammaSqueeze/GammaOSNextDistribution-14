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
package com.android.server.uwb.discovery.info;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * UWB Secure Component information according to FiRa BLE OOB v1.0 specification.
 */
public class SecureComponentInfo {
    private static final String LOG_TAG = SecureComponentInfo.class.getSimpleName();

    // The size of the full data
    private static final int SECURE_COMPONENT_INFO_SIZE = 2;

    private static final byte STATIC_INDICATION_BITMASK = (byte) 0x80;
    private static final byte SECID_BITMASK = 0x7F;
    private static final byte SECURE_COMPONENT_TYPE_BITMASK = (byte) 0xF0;
    private static final byte SECURE_COMPONENT_PROTOCOL_TYPE_BITMASK = 0x0F;

    // If this Secure Component is granted to be always available.
    public final boolean staticIndication;
    // SECID value (unsigned integer in the range 2..127, values 0 and 1 are reserved)
    @IntRange(from = 2, to = 127)
    public final int secid;

    /**
     * Type of Secure Component
     */
    public enum SecureComponentType {
        // As defined by GloblePlatform
        ESE_NONREMOVABLE(1),
        // As defined by ETSI
        UICC_REMOVABLE(2),
        // As defined by GSMA
        DISCRETE_EUICC_REMOVABLE(3),
        // As defined by GSMA
        DISCRETE_EUICC_NONREMOVABLE(4),
        // As defined by GSMA
        INTEGRATED_EUICC_NONREMOVABLE(5),
        // Software emulated SC (e.g. Android HCE)
        SW_EMULATED_SC(6),

        VENDOR_PROPRIETARY(15);

        private final int mValue;
        private static Map sMap = new HashMap<>();

        SecureComponentType(int value) {
            this.mValue = value;
        }

        static {
            for (SecureComponentType type : SecureComponentType.values()) {
                sMap.put(type.mValue, type);
            }
        }

        /**
         * Get the SecureComponentType based on the given value.
         *
         * @param value type value defined by FiRa.
         * @return {@link SecureComponentType} associated with the value, else null if invalid.
         */
        @Nullable
        public static SecureComponentType valueOf(int value) {
            return (SecureComponentType) sMap.get(value);
        }

        public int getValue() {
            return mValue;
        }
    }

    public final SecureComponentType secureComponentType;

    /**
     * Type of Secure Component Protocol
     */
    public enum SecureComponentProtocolType {
        // As defined by FiRa
        FIRA_OOB_ADMINISTRATIVE_PROTOCOL(1),
        // As defined by ISO/IEC 7816-4
        ISO_IEC_7816_4(2),

        VENDOR_PROPRIETARY(15);

        private final int mValue;
        private static Map sMap = new HashMap<>();

        SecureComponentProtocolType(int value) {
            this.mValue = value;
        }

        static {
            for (SecureComponentProtocolType type : SecureComponentProtocolType.values()) {
                sMap.put(type.mValue, type);
            }
        }

        /**
         * Get the SecureComponentProtocolType based on the given value.
         *
         * @param value type value defined by FiRa.
         * @return {@link SecureComponentProtocolType} associated with the value, else null if
         *     invalid.
         */
        @Nullable
        public static SecureComponentProtocolType valueOf(int value) {
            return (SecureComponentProtocolType) sMap.get(value);
        }

        public int getValue() {
            return mValue;
        }
    }

    public final SecureComponentProtocolType secureComponentProtocolType;

    /**
     * Generate the SecureComponentInfo from raw bytes array.
     *
     * @param bytes byte array containing the Secure Component info as part of the UWB indication
     *     data. Data encoding based on the FiRa specification.
     * @return decode bytes into {@link SecureComponentInfo}.
     */
    public static SecureComponentInfo fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            logw("Failed to convert empty into UWB Secure Component info.");
            return null;
        }

        if (bytes.length < SECURE_COMPONENT_INFO_SIZE) {
            logw(
                    "Failed to convert bytes into UWB Secure Component info due to invalid data"
                            + " size.");
            return null;
        }

        boolean staticIndication = (bytes[0] & STATIC_INDICATION_BITMASK) != 0;
        int secid = (int) (bytes[0] & SECID_BITMASK);
        if (secid < 2 || secid > 127) {
            logw("Failed to convert bytes into UWB Secure Component info due to invalid secid");
            return null;
        }
        SecureComponentType type =
                SecureComponentType.valueOf(
                        (int) ((bytes[1] & SECURE_COMPONENT_TYPE_BITMASK) >>> 4));
        if (type == null) {
            logw(
                    "Failed to convert bytes into UWB Secure Component info due to invalid Secure"
                            + " Component Type");
            return null;
        }
        SecureComponentProtocolType protocolType =
                SecureComponentProtocolType.valueOf(
                        (int) ((bytes[1] & SECURE_COMPONENT_PROTOCOL_TYPE_BITMASK)));
        if (protocolType == null) {
            logw(
                    "Failed to convert bytes into UWB Secure Component info due to invalid Secure"
                            + " Component Protocol Type");
            return null;
        }

        return new SecureComponentInfo(staticIndication, secid, type, protocolType);
    }

    /**
     * Generate raw bytes array from SecureComponentInfo.
     *
     * @param info the Secure Component info.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(@NonNull SecureComponentInfo info) {
        return new byte[] {
            (byte) (convertStaticIndication(info.staticIndication) | convertSedid(info.secid)),
            (byte)
                    (convertsecureComponentType(info.secureComponentType)
                            | convertSecureComponentProtocolType(info.secureComponentProtocolType))
        };
    }

    private static byte convertStaticIndication(boolean staticIndication) {
        return (byte) (((staticIndication ? 1 : 0) << 7) & STATIC_INDICATION_BITMASK);
    }

    private static byte convertSedid(int secid) {
        return (byte) (DataTypeConversionUtil.i32ToByteArray(secid)[3] & SECID_BITMASK);
    }

    private static byte convertsecureComponentType(SecureComponentType type) {
        return (byte)
                ((DataTypeConversionUtil.i32ToByteArray(type.getValue())[3] << 4)
                        & SECURE_COMPONENT_TYPE_BITMASK);
    }

    private static byte convertSecureComponentProtocolType(SecureComponentProtocolType type) {
        return (byte)
                (DataTypeConversionUtil.i32ToByteArray(type.getValue())[3]
                        & SECURE_COMPONENT_PROTOCOL_TYPE_BITMASK);
    }

    public SecureComponentInfo(
            boolean staticIndication,
            int secid,
            SecureComponentType secureComponentType,
            SecureComponentProtocolType secureComponentProtocolType) {
        this.staticIndication = staticIndication;
        this.secid = secid;
        this.secureComponentType = secureComponentType;
        this.secureComponentProtocolType = secureComponentProtocolType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SecureComponentInfo: staticIndication=")
                .append(staticIndication)
                .append(" secid=")
                .append(secid)
                .append(" secureComponentType=")
                .append(secureComponentType)
                .append(" secureComponentProtocolType=")
                .append(secureComponentProtocolType);
        return sb.toString();
    }

    /**
     * Get the raw byte size of the secure component info.
     *
     * @return The raw byte size of the secure component info.
     */
    public static int size() {
        return SECURE_COMPONENT_INFO_SIZE;
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
