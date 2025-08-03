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

package android.telephony.cts.util;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class TelephonyUtils {

    /**
     * See {@link TelecomManager#ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION}
     */
    public static final String ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING =
            "ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION";

    /**
     * See com.android.services.telephony.rcs.DelegateStateTracker#
     * SUPPORT_REGISTERING_DELEGATE_STATE
     */
    public static final String SUPPORT_REGISTERING_DELEGATE_STATE_STRING =
            "SUPPORT_REGISTERING_DELEGATE_STATE";
    /**
     * See com.android.services.telephony.rcs.DelegateStateTracker#
     * SUPPORT_DEREGISTERING_LOSING_PDN_STATE
     */
    public static final String SUPPORT_DEREGISTERING_LOSING_PDN_STATE_STRING =
            "SUPPORT_DEREGISTERING_LOSING_PDN_STATE";

    /**
     * A map of {@link AccessNetworkConstants.RadioAccessNetworkType}s and its associated bands.
     */
    public static final Map<Integer, List<Integer>> ALL_BANDS = Map.of(
            AccessNetworkConstants.AccessNetworkType.GERAN, List.of(
                    AccessNetworkConstants.GeranBand.BAND_T380,
                    AccessNetworkConstants.GeranBand.BAND_T410,
                    AccessNetworkConstants.GeranBand.BAND_450,
                    AccessNetworkConstants.GeranBand.BAND_480,
                    AccessNetworkConstants.GeranBand.BAND_710,
                    AccessNetworkConstants.GeranBand.BAND_750,
                    AccessNetworkConstants.GeranBand.BAND_T810,
                    AccessNetworkConstants.GeranBand.BAND_850,
                    AccessNetworkConstants.GeranBand.BAND_P900,
                    AccessNetworkConstants.GeranBand.BAND_E900,
                    AccessNetworkConstants.GeranBand.BAND_R900,
                    AccessNetworkConstants.GeranBand.BAND_DCS1800,
                    AccessNetworkConstants.GeranBand.BAND_PCS1900,
                    AccessNetworkConstants.GeranBand.BAND_ER900),
            AccessNetworkConstants.AccessNetworkType.UTRAN, List.of(
                    AccessNetworkConstants.UtranBand.BAND_1,
                    AccessNetworkConstants.UtranBand.BAND_2,
                    AccessNetworkConstants.UtranBand.BAND_3,
                    AccessNetworkConstants.UtranBand.BAND_4,
                    AccessNetworkConstants.UtranBand.BAND_5,
                    AccessNetworkConstants.UtranBand.BAND_6,
                    AccessNetworkConstants.UtranBand.BAND_7,
                    AccessNetworkConstants.UtranBand.BAND_8,
                    AccessNetworkConstants.UtranBand.BAND_9,
                    AccessNetworkConstants.UtranBand.BAND_10,
                    AccessNetworkConstants.UtranBand.BAND_11,
                    AccessNetworkConstants.UtranBand.BAND_12,
                    AccessNetworkConstants.UtranBand.BAND_13,
                    AccessNetworkConstants.UtranBand.BAND_14,
                    AccessNetworkConstants.UtranBand.BAND_19,
                    AccessNetworkConstants.UtranBand.BAND_20,
                    AccessNetworkConstants.UtranBand.BAND_21,
                    AccessNetworkConstants.UtranBand.BAND_22,
                    AccessNetworkConstants.UtranBand.BAND_25,
                    AccessNetworkConstants.UtranBand.BAND_26,
                    AccessNetworkConstants.UtranBand.BAND_A,
                    AccessNetworkConstants.UtranBand.BAND_B,
                    AccessNetworkConstants.UtranBand.BAND_C,
                    AccessNetworkConstants.UtranBand.BAND_D,
                    AccessNetworkConstants.UtranBand.BAND_E,
                    AccessNetworkConstants.UtranBand.BAND_F),
            AccessNetworkConstants.AccessNetworkType.EUTRAN, List.of(
                    AccessNetworkConstants.EutranBand.BAND_1,
                    AccessNetworkConstants.EutranBand.BAND_2,
                    AccessNetworkConstants.EutranBand.BAND_3,
                    AccessNetworkConstants.EutranBand.BAND_4,
                    AccessNetworkConstants.EutranBand.BAND_5,
                    AccessNetworkConstants.EutranBand.BAND_6,
                    AccessNetworkConstants.EutranBand.BAND_7,
                    AccessNetworkConstants.EutranBand.BAND_8,
                    AccessNetworkConstants.EutranBand.BAND_9,
                    AccessNetworkConstants.EutranBand.BAND_10,
                    AccessNetworkConstants.EutranBand.BAND_11,
                    AccessNetworkConstants.EutranBand.BAND_12,
                    AccessNetworkConstants.EutranBand.BAND_13,
                    AccessNetworkConstants.EutranBand.BAND_14,
                    AccessNetworkConstants.EutranBand.BAND_17,
                    AccessNetworkConstants.EutranBand.BAND_18,
                    AccessNetworkConstants.EutranBand.BAND_19,
                    AccessNetworkConstants.EutranBand.BAND_20,
                    AccessNetworkConstants.EutranBand.BAND_21,
                    AccessNetworkConstants.EutranBand.BAND_22,
                    AccessNetworkConstants.EutranBand.BAND_23,
                    AccessNetworkConstants.EutranBand.BAND_24,
                    AccessNetworkConstants.EutranBand.BAND_25,
                    AccessNetworkConstants.EutranBand.BAND_26,
                    AccessNetworkConstants.EutranBand.BAND_27,
                    AccessNetworkConstants.EutranBand.BAND_28,
                    AccessNetworkConstants.EutranBand.BAND_30,
                    AccessNetworkConstants.EutranBand.BAND_31,
                    AccessNetworkConstants.EutranBand.BAND_33,
                    AccessNetworkConstants.EutranBand.BAND_34,
                    AccessNetworkConstants.EutranBand.BAND_35,
                    AccessNetworkConstants.EutranBand.BAND_36,
                    AccessNetworkConstants.EutranBand.BAND_37,
                    AccessNetworkConstants.EutranBand.BAND_38,
                    AccessNetworkConstants.EutranBand.BAND_39,
                    AccessNetworkConstants.EutranBand.BAND_40,
                    AccessNetworkConstants.EutranBand.BAND_41,
                    AccessNetworkConstants.EutranBand.BAND_42,
                    AccessNetworkConstants.EutranBand.BAND_43,
                    AccessNetworkConstants.EutranBand.BAND_44,
                    AccessNetworkConstants.EutranBand.BAND_45,
                    AccessNetworkConstants.EutranBand.BAND_46,
                    AccessNetworkConstants.EutranBand.BAND_47,
                    AccessNetworkConstants.EutranBand.BAND_48,
                    AccessNetworkConstants.EutranBand.BAND_49,
                    AccessNetworkConstants.EutranBand.BAND_50,
                    AccessNetworkConstants.EutranBand.BAND_51,
                    AccessNetworkConstants.EutranBand.BAND_52,
                    AccessNetworkConstants.EutranBand.BAND_53,
                    AccessNetworkConstants.EutranBand.BAND_65,
                    AccessNetworkConstants.EutranBand.BAND_66,
                    AccessNetworkConstants.EutranBand.BAND_68,
                    AccessNetworkConstants.EutranBand.BAND_70,
                    AccessNetworkConstants.EutranBand.BAND_71,
                    AccessNetworkConstants.EutranBand.BAND_72,
                    AccessNetworkConstants.EutranBand.BAND_73,
                    AccessNetworkConstants.EutranBand.BAND_74,
                    AccessNetworkConstants.EutranBand.BAND_85,
                    AccessNetworkConstants.EutranBand.BAND_87,
                    AccessNetworkConstants.EutranBand.BAND_88),
            AccessNetworkConstants.AccessNetworkType.NGRAN, List.of(
                    AccessNetworkConstants.NgranBands.BAND_1,
                    AccessNetworkConstants.NgranBands.BAND_2,
                    AccessNetworkConstants.NgranBands.BAND_3,
                    AccessNetworkConstants.NgranBands.BAND_5,
                    AccessNetworkConstants.NgranBands.BAND_7,
                    AccessNetworkConstants.NgranBands.BAND_8,
                    AccessNetworkConstants.NgranBands.BAND_12,
                    AccessNetworkConstants.NgranBands.BAND_14,
                    AccessNetworkConstants.NgranBands.BAND_18,
                    AccessNetworkConstants.NgranBands.BAND_20,
                    AccessNetworkConstants.NgranBands.BAND_25,
                    AccessNetworkConstants.NgranBands.BAND_26,
                    AccessNetworkConstants.NgranBands.BAND_28,
                    AccessNetworkConstants.NgranBands.BAND_29,
                    AccessNetworkConstants.NgranBands.BAND_30,
                    AccessNetworkConstants.NgranBands.BAND_34,
                    AccessNetworkConstants.NgranBands.BAND_38,
                    AccessNetworkConstants.NgranBands.BAND_39,
                    AccessNetworkConstants.NgranBands.BAND_40,
                    AccessNetworkConstants.NgranBands.BAND_41,
                    AccessNetworkConstants.NgranBands.BAND_46,
                    AccessNetworkConstants.NgranBands.BAND_48,
                    AccessNetworkConstants.NgranBands.BAND_50,
                    AccessNetworkConstants.NgranBands.BAND_51,
                    AccessNetworkConstants.NgranBands.BAND_53,
                    AccessNetworkConstants.NgranBands.BAND_65,
                    AccessNetworkConstants.NgranBands.BAND_66,
                    AccessNetworkConstants.NgranBands.BAND_70,
                    AccessNetworkConstants.NgranBands.BAND_71,
                    AccessNetworkConstants.NgranBands.BAND_74,
                    AccessNetworkConstants.NgranBands.BAND_75,
                    AccessNetworkConstants.NgranBands.BAND_76,
                    AccessNetworkConstants.NgranBands.BAND_77,
                    AccessNetworkConstants.NgranBands.BAND_78,
                    AccessNetworkConstants.NgranBands.BAND_79,
                    AccessNetworkConstants.NgranBands.BAND_80,
                    AccessNetworkConstants.NgranBands.BAND_81,
                    AccessNetworkConstants.NgranBands.BAND_82,
                    AccessNetworkConstants.NgranBands.BAND_83,
                    AccessNetworkConstants.NgranBands.BAND_84,
                    AccessNetworkConstants.NgranBands.BAND_86,
                    AccessNetworkConstants.NgranBands.BAND_89,
                    AccessNetworkConstants.NgranBands.BAND_90,
                    AccessNetworkConstants.NgranBands.BAND_91,
                    AccessNetworkConstants.NgranBands.BAND_92,
                    AccessNetworkConstants.NgranBands.BAND_93,
                    AccessNetworkConstants.NgranBands.BAND_94,
                    AccessNetworkConstants.NgranBands.BAND_95,
                    AccessNetworkConstants.NgranBands.BAND_96,
                    AccessNetworkConstants.NgranBands.BAND_257,
                    AccessNetworkConstants.NgranBands.BAND_258,
                    AccessNetworkConstants.NgranBands.BAND_260,
                    AccessNetworkConstants.NgranBands.BAND_261));

    private static final String COMMAND_ADD_TEST_EMERGENCY_NUMBER =
            "cmd phone emergency-number-test-mode -a ";

    private static final String COMMAND_REMOVE_TEST_EMERGENCY_NUMBER =
            "cmd phone emergency-number-test-mode -r ";

    private static final String COMMAND_END_BLOCK_SUPPRESSION = "cmd phone end-block-suppression";

    private static final String COMMAND_FLUSH_TELEPHONY_METRICS =
            "/system/bin/dumpsys activity service TelephonyDebugService --metricsproto";

    private static final String COMMAND_AM_COMPAT = "am compat ";

    public static final String CTS_APP_PACKAGE = "android.telephony.cts";
    public static final String CTS_APP_PACKAGE2 = "android.telephony2.cts";

    private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F' };

    private static final String COMMAND_CARRIER_RESTRICTION_STATUS =
            "cmd phone carrier_restriction_status_test --";

    public static void addTestEmergencyNumber(Instrumentation instr, String testNumber)
            throws Exception {
        executeShellCommand(instr, COMMAND_ADD_TEST_EMERGENCY_NUMBER + testNumber);
    }

    public static void removeTestEmergencyNumber(Instrumentation instr, String testNumber)
            throws Exception {
        executeShellCommand(instr, COMMAND_REMOVE_TEST_EMERGENCY_NUMBER + testNumber);
    }

    public static void endBlockSuppression(Instrumentation instr) throws Exception {
        executeShellCommand(instr, COMMAND_END_BLOCK_SUPPRESSION);
    }

    public static void flushTelephonyMetrics(Instrumentation instr) throws Exception {
        executeShellCommand(instr, COMMAND_FLUSH_TELEPHONY_METRICS);
    }

    public static void enableCompatCommand(Instrumentation instr, String pkgName,
            String commandName) throws Exception {
        executeShellCommand(instr, COMMAND_AM_COMPAT + "enable  --no-kill " + commandName + " "
                + pkgName);
    }

    public static void disableCompatCommand(Instrumentation instr, String pkgName,
            String commandName) throws Exception {
        executeShellCommand(instr, COMMAND_AM_COMPAT + "disable  --no-kill " + commandName + " "
                + pkgName);
    }

    public static void resetCompatCommand(Instrumentation instr, String pkgName,
            String commandName) throws Exception {
        executeShellCommand(instr, COMMAND_AM_COMPAT + "reset  --no-kill " + commandName + " "
                + pkgName);
    }

    public static boolean isSkt(TelephonyManager telephonyManager) {
        return isOperator(telephonyManager, "45005");
    }

    public static boolean isKt(TelephonyManager telephonyManager) {
        return isOperator(telephonyManager, "45002")
                || isOperator(telephonyManager, "45004")
                || isOperator(telephonyManager, "45008");
    }

    private static boolean isOperator(TelephonyManager telephonyManager, String operator) {
        String simOperator = telephonyManager.getSimOperator();
        return simOperator != null && simOperator.equals(operator);
    }

    public static String parseErrorCodeToString(int errorCode,
            Class<?> containingClass, String prefix) {
        for (Field field : containingClass.getDeclaredFields()) {
            if (field.getName().startsWith(prefix)) {
                if (field.getType() == Integer.TYPE) {
                    field.setAccessible(true);
                    try {
                        if (field.getInt(null) == errorCode) {
                            return field.getName();
                        }
                    } catch (IllegalAccessException e) {
                        continue;
                    }
                }
            }
        }
        return String.format("??%d??", errorCode);
    }

    /**
     * Executes the given shell command and returns the output in a string. Note that even
     * if we don't care about the output, we have to read the stream completely to make the
     * command execute.
     */
    public static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                closeQuietly(br);
            }
            closeQuietly(pfd);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean pollUntilTrue(BooleanSupplier s, int times, int timeoutMs) {
        boolean successful = false;
        for (int i = 0; i < times; i++) {
            successful = s.getAsBoolean();
            if (successful) break;
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) { }
        }
        return successful;
    }

    public static String toHexString(byte[] array) {
        int length = array.length;
        char[] buf = new char[length * 2];

        int bufIndex = 0;
        for (byte b : array) {
            buf[bufIndex++] = HEX_DIGITS[(b >>> 4) & 0x0F];
            buf[bufIndex++] = HEX_DIGITS[b & 0x0F];
        }

        return new String(buf);
    }

    private static int toByte(char c) {
        if (c >= '0' && c <= '9') return (c - '0');
        if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
        if (c >= 'a' && c <= 'f') return (c - 'a' + 10);

        throw new RuntimeException("Invalid hex char '" + c + "'");
    }

    public static byte[] hexStringToByteArray(String hexString) {
        int length = hexString.length();
        byte[] buffer = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            buffer[i / 2] =
                    (byte) ((toByte(hexString.charAt(i)) << 4) | toByte(hexString.charAt(i + 1)));
        }

        return buffer;
    }

    /**
     * Sets the allowlist package to the Json parsed data in CarrierAllowListInfo.java
     *
     * @param instr       Test Instrumentation
     * @param packageName caller packageName
     * @param carrierId   carrierId to match with
     * @param shaId       shaId of the caller
     */
    public static void addCarrierRestrictionStatusAllowList(Instrumentation instr,
            String packageName, int carrierId, String shaId) throws Exception {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(shaId)) {
            return;
        }
        String allowList = "package:" + packageName + "," + "carrierId:" + String.valueOf(carrierId)
                + "," + "callerSHA1Id:" + shaId;
        executeShellCommand(instr, COMMAND_CARRIER_RESTRICTION_STATUS + allowList);
    }

    public static void resetCarrierRestrictionStatusAllowList(Instrumentation instr)
            throws Exception {
        executeShellCommand(instr, COMMAND_CARRIER_RESTRICTION_STATUS);
    }
}
