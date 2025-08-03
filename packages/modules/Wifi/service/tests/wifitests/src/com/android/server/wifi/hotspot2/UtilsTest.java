/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi.hotspot2;

import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NONE;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_PASSPOINT;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_RCOI;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_FREE;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_SETTLED;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OTHERS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.wifi.WifiConfiguration;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiConfigurationTestUtil;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.Utils}.
 */
public class UtilsTest extends WifiBaseTest {
    @Test
    public void testRoamingConsortiumsToStringLong() {
        assertEquals("null", Utils.roamingConsortiumsToString((long[]) null));

        long[] ois = new long[]{1L, 2L, 1L << (63 - 40)};
        String expected = "000001, 000002, 0000800000";
        String result = Utils.roamingConsortiumsToString(ois);
        assertEquals(expected, result);
    }

    @Test
    public void testRoamingConsortiumsToStringCollection() {
        ArrayList<Long> ois = new ArrayList<>();
        assertEquals("", Utils.roamingConsortiumsToString(ois));

        ois.add(1L);
        ois.add(2L);
        ois.add((1L << (63 - 40)));
        String expected = "000001, 000002, 0000800000";
        String result = Utils.roamingConsortiumsToString(ois);
        assertEquals(expected, result);
    }

    @Test
    public void testToUnicodeEscapedString() {
        assertEquals("", Utils.toUnicodeEscapedString(""));

        StringBuilder unescapedStringBuilder = new StringBuilder();
        StringBuilder escapedStringBuilder = new StringBuilder();
        for (int c = 0; c < 128; c++) {
            unescapedStringBuilder.append((char) c);
            if (c >= ' ' && c < 127) {
                escapedStringBuilder.append((char) c);
            } else {
                escapedStringBuilder.append("\\u").append(String.format("%04x", c));
            }
        }
        assertEquals(escapedStringBuilder.toString(),
                Utils.toUnicodeEscapedString(unescapedStringBuilder.toString()));
    }

    @Test
    public void testToHexString() {
        assertEquals("null", Utils.toHexString(null));

        byte[] bytes = {(byte) 0xab, (byte) 0xcd, (byte) 0xef};
        String expected = "ab cd ef";
        String result = Utils.toHexString(bytes);
        assertEquals(expected, result.toLowerCase());
    }

    @Test
    public void testToHex() {
        assertEquals("", Utils.toHex(new byte[0]));

        byte[] bytes = {(byte) 0xab, (byte) 0xcd, (byte) 0xef};
        String expected = "abcdef";
        String result = Utils.toHex(bytes);
        assertEquals(expected, result.toLowerCase());

    }

    @Test
    public void testHexToBytes() {
        assertArrayEquals(new byte[0], Utils.hexToBytes(""));

        String hexString = "abcd";
        byte[] expected = {(byte) 0xab, (byte) 0xcd};
        byte[] result = Utils.hexToBytes(hexString);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testFromHex() {
        int i = 0;
        for (char c : "0123456789abcdef".toCharArray()) {
            assertEquals(i, Utils.fromHex(c, true));
            assertEquals(i, Utils.fromHex(c, false));
            assertEquals(i, Utils.fromHex(Character.toUpperCase(c), true));
            assertEquals(i, Utils.fromHex(Character.toUpperCase(c), false));
            i++;
        }

        assertEquals(-1, Utils.fromHex('q', true));

        try {
            Utils.fromHex('q', false);
            fail("Exception should be thrown!");
        } catch (NumberFormatException e) {
            // expected
        }
    }

    @Test
    public void testCompare() {
        assertEquals(-1, Utils.compare(-1, 1));
        assertEquals(0, Utils.compare(0, 0));
        assertEquals(1, Utils.compare(1, -1));

        assertEquals(-1, Utils.compare(null, 0));
        assertEquals(0, Utils.compare(null, null));
        assertEquals(1, Utils.compare(0, null));
    }

    @Test
    public void testToHMS() {
        long hours = 12;
        long minutes = 34;
        long millis = 56789;

        long time = (((hours * 60) + minutes) * 60) * 1000 + millis;

        String expected = "12:34:56.789";
        String result = Utils.toHMS(time);
        assertEquals(expected, result);

        expected = "-12:34:56.789";
        result = Utils.toHMS(-time);
        assertEquals(expected, result);
    }

    @Test
    public void testToUTCString() {
        long millis = 832077296000L;

        String expected = "1996/05/14 12:34:56Z";
        String result = Utils.toUTCString(millis);
        assertEquals(expected, result);
    }

    @Test
    public void testUnquote() {
        assertEquals(null, Utils.unquote(null));

        String unquoted = "This is a wug.";
        String quoted = "\"This is a wug.\"";
        String twiceQuoted = "\"\"This is a wug.\"\"";
        String unclosedQuoted = "\"This is a wug.";
        String quotedUnclosedQuoted = "\"\"This is a wug.\"";

        assertEquals(unquoted, Utils.unquote(quoted));
        assertEquals(quoted, Utils.unquote(twiceQuoted));
        assertEquals(unclosedQuoted, Utils.unquote(unclosedQuoted));
        assertEquals(unclosedQuoted, Utils.unquote(quotedUnclosedQuoted));
    }

    @Test
    public void testIsFreeOpenRoaming() {
        // 3 octets
        assertFalse(Utils.isFreeOpenRoaming(0xABCDEFL));
        assertTrue(Utils.isFreeOpenRoaming(0x5A03BAL));

        // 4 octets
        assertFalse(Utils.isFreeOpenRoaming(0xFF5A03BAL));
        assertFalse(Utils.isFreeOpenRoaming(0x5A03BAFFL));

        // 5 octets
        assertFalse(Utils.isFreeOpenRoaming(0xEEFF5A03BAL));
        assertTrue(Utils.isFreeOpenRoaming(0x5A03BA0000L));
        assertTrue(Utils.isFreeOpenRoaming(0x5A03BAEEFFL));

        // 6 octets
        assertFalse(Utils.isFreeOpenRoaming(0x5A03BAEEFFFFL));
        assertFalse(Utils.isFreeOpenRoaming(0xFF5A03BAEEFFL));
    }

    @Test
    public void testContainsFreeOpenRoaming() {
        assertFalse(Utils.containsFreeOpenRoaming(new long[]{0xABCDEFL}));
        assertTrue(Utils.containsFreeOpenRoaming(new long[]{0x5A03BAL}));
        assertTrue(Utils.containsFreeOpenRoaming(new long[]{0xABCDEFL, 0x5A03BAFFFFL, 0xABCDEFL}));
    }

    @Test
    public void testIsSettledOpenRoaming() {
        // 3 octets
        assertFalse(Utils.isSettledOpenRoaming(0xABCDEFL));
        assertTrue(Utils.isSettledOpenRoaming(0xBAA2D0L));

        // 4 octets
        assertFalse(Utils.isSettledOpenRoaming(0xBAA2D0FFL));
        assertFalse(Utils.isSettledOpenRoaming(0xFFBAA2D0L));

        // 5 octets
        assertFalse(Utils.isSettledOpenRoaming(0xEEFFBAA2D0L));
        assertTrue(Utils.isSettledOpenRoaming(0xBAA2D00000L));
        assertTrue(Utils.isSettledOpenRoaming(0xBAA2D0EEFFL));

        // 6 octets
        assertFalse(Utils.isSettledOpenRoaming(0xBAA2D0EEFFFFL));
        assertFalse(Utils.isSettledOpenRoaming(0xFFBAA2D0EEFFL));
    }

    @Test
    public void testContainsSettledOpenRoaming() {
        assertFalse(Utils.containsSettledOpenRoaming(new long[]{0xABCDEFL}));
        assertTrue(Utils.containsSettledOpenRoaming(new long[]{0xBAA2D0L}));
        assertTrue(
                Utils.containsSettledOpenRoaming(new long[]{0xABCDEFL, 0xBAA2D0EEFFL, 0xABCDEFL}));
    }

    @Test
    public void testGetRoamingType() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        assertEquals(Utils.getRoamingType(config),
                WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_PASSPOINT);

        config = WifiConfigurationTestUtil.createPasspointNetwork();
        assertEquals(Utils.getRoamingType(config),
                WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NOT_RCOI);

        config.enterpriseConfig.setSelectedRcoi(0xABCDEFL);
        assertEquals(Utils.getRoamingType(config),
                WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OTHERS);

        config.enterpriseConfig.setSelectedRcoi(0x5A03BAFFFFL);
        assertEquals(Utils.getRoamingType(config),
                WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_FREE);

        config.enterpriseConfig.setSelectedRcoi(0xBAA2D0FFFFL);
        assertEquals(Utils.getRoamingType(config),
                WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_SETTLED);

        config.isHomeProviderNetwork = true;
        assertEquals(Utils.getRoamingType(config),
                WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_NONE);
    }
}


