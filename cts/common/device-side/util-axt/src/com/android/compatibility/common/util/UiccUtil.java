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

package com.android.compatibility.common.util;

import android.Manifest;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.test.InstrumentationRegistry;

import java.util.List;

/** Utility class for common UICC- and SIM-related operations. */
public final class UiccUtil {

    // A table mapping from a number to a hex character for fast encoding hex strings.
    private static final char[] HEX_CHARS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Data class representing a single APDU transmission.
     *
     * <p>Constants are defined in TS 102 221 Section 10.1.2.
     */
    public static final class ApduCommand {
        public static final int INS_GET_RESPONSE = 0xC0;

        public final int cla;
        public final int ins;
        public final int p1;
        public final int p2;
        public final int p3;
        @Nullable public final String data;

        public ApduCommand(int cla, int ins, int p1, int p2, int p3, @Nullable String data) {
            this.cla = cla;
            this.ins = ins;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.data = data;
        }

        @Override
        public String toString() {
            return "cla=0x"
                    + Integer.toHexString(cla)
                    + ", ins=0x"
                    + Integer.toHexString(ins)
                    + ", p1=0x"
                    + Integer.toHexString(p1)
                    + ", p2=0x"
                    + Integer.toHexString(p2)
                    + ", p3=0x"
                    + Integer.toHexString(p3)
                    + ", data="
                    + data;
        }
    }

    /** Various APDU status words and their meanings, as defined in TS 102 221 Section 10.2.1 */
    public static final class ApduResponse {
        public static final String SW1_MORE_RESPONSE = "61";

        public static final String SW1_SW2_OK = "9000";
        public static final String SW1_OK_PROACTIVE_COMMAND = "91";
    }

    /**
     * The hashes of all supported CTS UICC test keys and their corresponding specification.
     *
     * <p>For up-to-date information about the CTS SIM specification, please see
     * https://source.android.com/devices/tech/config/uicc#validation.
     */
    @StringDef({UiccCertificate.CTS_UICC_LEGACY, UiccCertificate.CTS_UICC_2021})
    public @interface UiccCertificate {

        /**
         * Indicates compliance with the "legacy" CTS UICC specification (prior to 2021).
         *
         * <p>Corresponding certificate: {@code aosp-testkey}.
         *
         * @deprecated as of 2021, and no longer supported as of 2022.
         */
        @Deprecated String CTS_UICC_LEGACY = "61ED377E85D386A8DFEE6B864BD85B0BFAA5AF81";

        /**
         * Indicates compliance with the 2021 CTS UICC specification.
         *
         * <p>Strongly recommended as of 2021, required as of 2022.
         *
         * <p>Corresponding certificate: {@code cts-uicc-2021-testkey}.
         */
        String CTS_UICC_2021 = "CE7B2B47AE2B7552C8F92CC29124279883041FB623A5F194A82C9BF15D492AA0";
    }

    /**
     * A simple check for use with {@link org.junit.Assume#assumeTrue}. Checks the carrier privilege
     * certificates stored on the SIM and returns {@code true} if {@code requiredCert} is present.
     *
     * <p>Can be used either in the {@code #setUp} method if an entire class requires a particular
     * UICC, or at the top of a specific {@code @Test} method.
     *
     * <p>If we had JUnit 5, we could create a much cooler {@code @RequiresUiccCertificate}
     * annotation using {@code ExtendWith} and {@code ExecutionCondition}, but that isn't available
     * to us yet.
     */
    public static boolean uiccHasCertificate(@UiccCertificate String requiredCert) {
        TelephonyManager tm =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(TelephonyManager.class);
        List<String> uiccCerts =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        tm,
                        TelephonyManager::getCertsFromCarrierPrivilegeAccessRules,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        return uiccCerts == null ? false : uiccCerts.contains(requiredCert);
    }

    /**
     * Converts a byte array into a String of hexadecimal characters.
     *
     * @param bytes an array of bytes
     * @return hex string representation of bytes array
     */
    @Nullable
    public static String bytesToHexString(@Nullable byte[] bytes) {
        if (bytes == null) return null;

        StringBuilder ret = new StringBuilder(2 * bytes.length);

        for (int i = 0; i < bytes.length; i++) {
            int b;
            b = 0x0f & (bytes[i] >> 4);
            ret.append(HEX_CHARS[b]);
            b = 0x0f & bytes[i];
            ret.append(HEX_CHARS[b]);
        }

        return ret.toString();
    }
}
