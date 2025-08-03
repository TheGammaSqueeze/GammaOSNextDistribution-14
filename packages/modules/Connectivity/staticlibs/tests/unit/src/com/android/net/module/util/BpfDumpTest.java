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

package com.android.net.module.util;

import static android.system.OsConstants.EPERM;
import static android.system.OsConstants.R_OK;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Pair;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.TestBpfMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoSession;

import java.io.PrintWriter;
import java.io.StringWriter;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BpfDumpTest {
    private static final int TEST_KEY = 123;
    private static final String TEST_KEY_BASE64 = "ewAAAA==";
    private static final int TEST_VAL = 456;
    private static final String TEST_VAL_BASE64 = "yAEAAA==";
    private static final String BASE64_DELIMITER = ",";
    private static final String TEST_KEY_VAL_BASE64 =
            TEST_KEY_BASE64 + BASE64_DELIMITER + TEST_VAL_BASE64;
    private static final String INVALID_BASE64_STRING = "Map is null";

    @Test
    public void testToBase64EncodedString() {
        final Struct.S32 key = new Struct.S32(TEST_KEY);
        final Struct.S32 value = new Struct.S32(TEST_VAL);

        // Verified in python:
        //   import base64
        //   print(base64.b64encode(b'\x7b\x00\x00\x00')) # key: ewAAAA== (TEST_KEY_BASE64)
        //   print(base64.b64encode(b'\xc8\x01\x00\x00')) # value: yAEAAA== (TEST_VAL_BASE64)
        assertEquals("7B000000", HexDump.toHexString(key.writeToBytes()));
        assertEquals("C8010000", HexDump.toHexString(value.writeToBytes()));
        assertEquals(TEST_KEY_VAL_BASE64, BpfDump.toBase64EncodedString(key, value));
    }

    @Test
    public void testFromBase64EncodedString() {
        Pair<Struct.S32, Struct.S32> decodedKeyValue = BpfDump.fromBase64EncodedString(
                Struct.S32.class, Struct.S32.class, TEST_KEY_VAL_BASE64);
        assertEquals(TEST_KEY, decodedKeyValue.first.val);
        assertEquals(TEST_VAL, decodedKeyValue.second.val);
    }

    private void assertThrowsIllegalArgumentException(final String testStr) {
        assertThrows(IllegalArgumentException.class,
                () -> BpfDump.fromBase64EncodedString(Struct.S32.class, Struct.S32.class, testStr));
    }

    @Test
    public void testFromBase64EncodedStringInvalidString() {
        assertThrowsIllegalArgumentException(INVALID_BASE64_STRING);
        assertThrowsIllegalArgumentException(TEST_KEY_BASE64);
        assertThrowsIllegalArgumentException(
                TEST_KEY_BASE64 + BASE64_DELIMITER + INVALID_BASE64_STRING);
        assertThrowsIllegalArgumentException(
                INVALID_BASE64_STRING + BASE64_DELIMITER + TEST_VAL_BASE64);
        assertThrowsIllegalArgumentException(
                INVALID_BASE64_STRING + BASE64_DELIMITER + INVALID_BASE64_STRING);
        assertThrowsIllegalArgumentException(
                TEST_KEY_VAL_BASE64 + BASE64_DELIMITER + TEST_KEY_BASE64);
    }

    private String getDumpMap(final IBpfMap<Struct.S32, Struct.S32> map) {
        final StringWriter sw = new StringWriter();
        BpfDump.dumpMap(map, new PrintWriter(sw), "mapName", "header",
                (key, val) -> "key=" + key.val + ", val=" + val.val);
        return sw.toString();
    }

    @Test
    public void testDumpMap() throws Exception {
        final IBpfMap<Struct.S32, Struct.S32> map =
                new TestBpfMap<>(Struct.S32.class, Struct.S32.class);
        map.updateEntry(new Struct.S32(123), new Struct.S32(456));

        final String dump = getDumpMap(map);
        assertEquals(dump, "mapName:\n"
                + "  header\n"
                + "  key=123, val=456\n");
    }

    @Test
    public void testDumpMapMultipleEntries() throws Exception {
        final IBpfMap<Struct.S32, Struct.S32> map =
                new TestBpfMap<>(Struct.S32.class, Struct.S32.class);
        map.updateEntry(new Struct.S32(123), new Struct.S32(456));
        map.updateEntry(new Struct.S32(789), new Struct.S32(123));

        final String dump = getDumpMap(map);
        assertTrue(dump.contains("mapName:"));
        assertTrue(dump.contains("header"));
        assertTrue(dump.contains("key=123, val=456"));
        assertTrue(dump.contains("key=789, val=123"));
    }

    private String getDumpMapStatus(final IBpfMap<Struct.S32, Struct.S32> map) {
        final StringWriter sw = new StringWriter();
        BpfDump.dumpMapStatus(map, new PrintWriter(sw), "mapName", "mapPath");
        return sw.toString();
    }

    @Test
    public void testGetMapStatus() {
        final IBpfMap<Struct.S32, Struct.S32> map =
                new TestBpfMap<>(Struct.S32.class, Struct.S32.class);
        assertEquals("mapName: OK\n", getDumpMapStatus(map));
    }

    @Test
    public void testGetMapStatusNull() {
        final MockitoSession session = mockitoSession()
                .spyStatic(Os.class)
                .startMocking();
        try {
            // Os.access succeeds
            doReturn(true).when(() -> Os.access("mapPath", R_OK));
            assertEquals("mapName: NULL(map is pinned to mapPath)\n", getDumpMapStatus(null));

            // Os.access throws EPERM
            doThrow(new ErrnoException("", EPERM)).when(() -> Os.access("mapPath", R_OK));
            assertEquals("mapName: NULL(map is not pinned to mapPath: Operation not permitted)\n",
                    getDumpMapStatus(null));
        } finally {
            session.finishMocking();
        }
    }
}
