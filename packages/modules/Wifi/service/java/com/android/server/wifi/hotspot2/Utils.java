package com.android.server.wifi.hotspot2;

import static com.android.server.wifi.hotspot2.anqp.Constants.BYTE_MASK;
import static com.android.server.wifi.hotspot2.anqp.Constants.NIBBLE_MASK;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NONE;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_PASSPOINT;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_RCOI;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_FREE;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_SETTLED;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OTHERS;

import android.net.wifi.WifiConfiguration;

import com.android.server.wifi.hotspot2.anqp.Constants;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public abstract class Utils {

    public static final long UNSET_TIME = -1;

    private static final int EUI48Length = 6;
    private static final int EUI64Length = 8;
    private static final long EUI48Mask = 0xffffffffffffL;
    private static final String[] PLMNText = {"org", "3gppnetwork", "mcc*", "mnc*", "wlan" };

    /*
     * OpenRoaming defines the use of multiple RCOIs to facilitate the implementation of policies
     * across the federation. The currently defined RCOIs are:
     * OpenRoaming-Settled: BA-A2-D0-xx-xx
     * OpenRoaming-Settlement-Free: 5A-03-BA -xx-xx
     * Refer to "OpenRoaming Framework & Standard Arch v3.0.0".
     */
    private static final long RCOI_OPEN_ROAMING_SETTLED_PREFIX = 0xBAA2D0;
    private static final long RCOI_OPEN_ROAMING_FREE_PREFIX = 0x5A03BA;

    public static List<String> splitDomain(String domain) {

        if (domain.endsWith("."))
            domain = domain.substring(0, domain.length() - 1);
        int at = domain.indexOf('@');
        if (at >= 0)
            domain = domain.substring(at + 1);

        String[] labels = domain.toLowerCase().split("\\.");
        LinkedList<String> labelList = new LinkedList<String>();
        for (String label : labels) {
            labelList.addFirst(label);
        }

        return labelList;
    }

    public static long parseMac(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Null MAC adddress");
        }
        long mac = 0;
        int count = 0;
        for (int n = 0; n < s.length(); n++) {
            int nibble = Utils.fromHex(s.charAt(n), true);  // Set lenient to not blow up on ':'
            if (nibble >= 0) {                              // ... and use only legit hex.
                mac = (mac << 4) | nibble;
                count++;
            }
        }
        if (count < 12 || (count&1) == 1) {
            throw new IllegalArgumentException("Bad MAC address: '" + s + "'");
        }
        return mac;
    }

    /**
     * Convert from mac address as long to string in hex code, separated with colon.
     * @param mac The Mac address as long value.
     * @return String value of mac address.
     */
    public static String macToString(long mac) {
        int len = (mac & ~EUI48Mask) != 0 ? EUI64Length : EUI48Length;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int n = (len - 1) * Byte.SIZE; n >= 0; n -= Byte.SIZE) {
            if (first) {
                first = false;
            } else {
                sb.append(':');
            }
            long b = (mac >>> n) & Constants.BYTE_MASK;
            sb.append(b > 0xf ? Long.toHexString(b) : "0" + Long.toHexString(b));
        }
        return sb.toString();
    }

    public static String getMccMnc(List<String> domain) {
        if (domain.size() != PLMNText.length) {
            return null;
        }

        for (int n = 0; n < PLMNText.length; n++ ) {
            String expect = PLMNText[n];
            int len = expect.endsWith("*") ? expect.length() - 1 : expect.length();
            if (!domain.get(n).regionMatches(0, expect, 0, len)) {
                return null;
            }
        }

        String prefix = domain.get(2).substring(3) + domain.get(3).substring(3);
        for (int n = 0; n < prefix.length(); n++) {
            char ch = prefix.charAt(n);
            if (ch < '0' || ch > '9') {
                return null;
            }
        }
        return prefix;
    }

    public static String roamingConsortiumsToString(long[] ois) {
        if (ois == null) {
            return "null";
        }
        List<Long> list = new ArrayList<Long>(ois.length);
        for (long oi : ois) {
            list.add(oi);
        }
        return roamingConsortiumsToString(list);
    }

    public static String roamingConsortiumsToString(Collection<Long> ois) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (long oi : ois) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (Long.numberOfLeadingZeros(oi) > 40) {
                sb.append(String.format("%06x", oi));
            } else {
                sb.append(String.format("%010x", oi));
            }
        }
        return sb.toString();
    }

    public static String toUnicodeEscapedString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int n = 0; n < s.length(); n++) {
            char ch = s.charAt(n);
            if (ch>= ' ' && ch < 127) {
                sb.append(ch);
            }
            else {
                sb.append("\\u").append(String.format("%04x", (int)ch));
            }
        }
        return sb.toString();
    }

    public static String toHexString(byte[] data) {
        if (data == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(data.length * 3);

        boolean first = true;
        for (byte b : data) {
            if (first) {
                first = false;
            } else {
                sb.append(' ');
            }
            sb.append(String.format("%02x", b & BYTE_MASK));
        }
        return sb.toString();
    }

    public static String toHex(byte[] octets) {
        StringBuilder sb = new StringBuilder(octets.length * 2);
        for (byte o : octets) {
            sb.append(String.format("%02x", o & BYTE_MASK));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String text) {
        if ((text.length() & 1) == 1) {
            throw new NumberFormatException("Odd length hex string: " + text.length());
        }
        byte[] data = new byte[text.length() >> 1];
        int position = 0;
        for (int n = 0; n < text.length(); n += 2) {
            data[position] =
                    (byte) (((fromHex(text.charAt(n), false) & NIBBLE_MASK) << 4) |
                            (fromHex(text.charAt(n + 1), false) & NIBBLE_MASK));
            position++;
        }
        return data;
    }

    public static int fromHex(char ch, boolean lenient) throws NumberFormatException {
        if (ch <= '9' && ch >= '0') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch + 10 - 'a';
        } else if (ch <= 'F' && ch >= 'A') {
            return ch + 10 - 'A';
        } else if (lenient) {
            return -1;
        } else {
            throw new NumberFormatException("Bad hex-character: " + ch);
        }
    }

    private static char toAscii(int b) {
        return b >= ' ' && b < 0x7f ? (char) b : '.';
    }

    static boolean isDecimal(String s) {
        for (int n = 0; n < s.length(); n++) {
            char ch = s.charAt(n);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    public static <T extends Comparable> int compare(Comparable<T> c1, T c2) {
        if (c1 == null) {
            return c2 == null ? 0 : -1;
        }
        else if (c2 == null) {
            return 1;
        }
        else {
            return c1.compareTo(c2);
        }
    }

    public static String bytesToBingoCard(ByteBuffer data, int len) {
        ByteBuffer dup = data.duplicate();
        dup.limit(dup.position() + len);
        return bytesToBingoCard(dup);
    }

    public static String bytesToBingoCard(ByteBuffer data) {
        ByteBuffer dup = data.duplicate();
        StringBuilder sbx = new StringBuilder();
        while (dup.hasRemaining()) {
            sbx.append(String.format("%02x ", dup.get() & BYTE_MASK));
        }
        dup = data.duplicate();
        sbx.append(' ');
        while (dup.hasRemaining()) {
            sbx.append(String.format("%c", toAscii(dup.get() & BYTE_MASK)));
        }
        return sbx.toString();
    }

    public static String toHMS(long millis) {
        long time = millis >= 0 ? millis : -millis;
        long tmp = time / 1000L;
        long ms = time - tmp * 1000L;

        time = tmp;
        tmp /= 60L;
        long s = time - tmp * 60L;

        time = tmp;
        tmp /= 60L;
        long m = time - tmp * 60L;

        return String.format("%s%d:%02d:%02d.%03d", millis < 0 ? "-" : "", tmp, m, s, ms);
    }

    public static String toUTCString(long ms) {
        if (ms < 0) {
            return "unset";
        }
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms);
        return String.format("%4d/%02d/%02d %2d:%02d:%02dZ",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND));
    }

    public static String unquote(String s) {
        if (s == null) {
            return null;
        }
        else if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length()-1);
        }
        else {
            return s;
        }
    }

    /*
     * Gets the first three octets from the RCOI which has 3 or 5 octets.
     */
    private static long getRcoiPrefix(long rcoi) {
        rcoi &= 0xff_ffff_ffffL;
        if ((rcoi & 0xff_ff00_0000L) != 0) {
            // This is a 5-octet RCOI, pick the first 3 octets.
            rcoi >>= 16;
        }
        return rcoi;
    }

    /**
     * Checks whether the given RCOI is a free Openroaming RCOI.
     */
    public static boolean isFreeOpenRoaming(long rcoi) {
        // There are more than 5 octets in the rcoi
        if (Long.numberOfLeadingZeros(rcoi) < 24) return false;
        return getRcoiPrefix(rcoi) == RCOI_OPEN_ROAMING_FREE_PREFIX;
    }

    /**
     * Checks whether there is a free OpenRoaming RCOI.
     */
    public static boolean containsFreeOpenRoaming(long[] rcois) {
        for (int i = 0; i < rcois.length; i++) {
            if (isFreeOpenRoaming(rcois[i])) return true;
        }
        return false;
    }

    /**
     * Checks whether the given RCOI is a settled OpenRoaming RCOI.
     */
    public static boolean isSettledOpenRoaming(long rcoi) {
        // There are more than 5 octets in the rcoi
        if (Long.numberOfLeadingZeros(rcoi) < 24) return false;
        return getRcoiPrefix(rcoi) == RCOI_OPEN_ROAMING_SETTLED_PREFIX;
    }

    /**
     * Checks whether there is a settled OpenRoaming RCOI.
     */
    public static boolean containsSettledOpenRoaming(long[] rcois) {
        for (int i = 0; i < rcois.length; i++) {
            if (isSettledOpenRoaming(rcois[i])) return true;
        }
        return false;
    }

    /**
     * Gets the roaming type of the given WifiConfiguration.
     *
     * @param connectConfig the passpoint WifiConfuration which has been connected or is being
     *                      connecting.
     * @return ROAMING_NOT_PASSPOINT if the input WifiConfiguration is not for passpoint.
     *         ROAMING_NONE if it is a home network.
     *         ROAMING_UNKNOWN if there is no selected RCOI which is set during connection.
     *         ROAMING_OPENROAMING_FREE if it is a free OpenRoaming network.
     *         ROAMING_OPENROAMING_SETTLED if it is a settled OpenRoaming network.
     */
    public static int getRoamingType(WifiConfiguration connectConfig) {
        if (!connectConfig.isPasspoint()) {
            return WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_PASSPOINT;
        }
        if (connectConfig.isHomeProviderNetwork) {
            return WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NONE;
        }

        long selectedRcoi = connectConfig.enterpriseConfig.getSelectedRcoi();
        if (isFreeOpenRoaming(selectedRcoi)) {
            return WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_FREE;
        }
        if (isSettledOpenRoaming(selectedRcoi)) {
            return WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_SETTLED;
        }
        if (selectedRcoi != 0L) {
            return WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OTHERS;
        }

        return WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_RCOI;
    }
}
