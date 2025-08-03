/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.icu.test.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.icu.testsharding.MainTestShard;
import android.icu.text.IDNA;
import android.icu.text.StringPrepParseException;

import com.android.icu.text.ExtendedIDNA;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ExtendedIDNATest {

    @Test
    public void testConvertIDNToASCII() throws StringPrepParseException {
        try {
            toASCII("www.m\uE400kitorppa.edu", 0);
            fail("should throw StringPrepParseException");
        } catch (StringPrepParseException e) {
            // expected
        }

        try {
            toASCII("www.\u672C\uFE73\uFFFF.jp", 0);
            fail("should throw StringPrepParseException");
        } catch (StringPrepParseException e) {
            // expected
        }

        assertEquals("www.xn--gwtq9nb2a.jp", toASCII(
                "www.\u65E5\u672C\u5E73.jp", 0));
        assertEquals("www.xn--vckk7bxa0eza9ezc9d.com",
                toASCII("www.\u30CF\u30F3\u30C9\u30DC\u30FC\u30EB\u30B5\u30E0\u30BA.com",0));
        assertEquals("www.xn--frgbolaget-q5a.nu", toASCII(
                "www.f\u00E4rgbolaget.nu", IDNA.ALLOW_UNASSIGNED));
        assertEquals("www.xn--bcher-kva.de", toASCII("www.b\u00FCcher.de", IDNA.ALLOW_UNASSIGNED));
        assertEquals("www.google.com", toASCII("www.google\u002Ecom", IDNA.USE_STD3_RULES));
    }

    private static String toASCII(String str, int flags) throws StringPrepParseException {
        return ExtendedIDNA.convertIDNToASCII(str, flags).toString();
    }

    @Test
    public void testConvertIDNToUnicode() throws StringPrepParseException {
        try {
            ExtendedIDNA.convertIDNToUnicode(null, 0);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        assertEquals("", toUnicode("", 0));
        assertEquals("www.bcher.de", toUnicode("www.bcher.de", 0));
        assertEquals("www.b\u00FCcher.de", toUnicode("www.b\u00FCcher.de", 0));
        assertEquals("www.\u65E5\u672C\u5E73.jp", toUnicode("www.\u65E5\u672C\u5E73.jp", 0));
        assertEquals("www\uff0e\u65E5\u672C\u5E73\uff61jp",
                toUnicode("www\uFF0Exn--gwtq9nb2a\uFF61jp", 0));
        assertEquals("www.\u65E5\u672C\u5E73.jp", toUnicode("www.xn--gwtq9nb2a.jp", 0));
        assertEquals("", toUnicode("", IDNA.ALLOW_UNASSIGNED));
        assertEquals("www.f\u00E4rgbolaget.nu", toUnicode(
                "www.f\u00E4rgbolaget.nu", IDNA.USE_STD3_RULES));
        assertEquals("www.r\u00E4ksm\u00F6rg\u00E5s\u3002nu", toUnicode(
                "www.r\u00E4ksm\u00F6rg\u00E5s\u3002nu",
                IDNA.USE_STD3_RULES));
        // RI bug. It cannot parse "www.xn--gwtq9nb2a.jp" when
        // USE_STD3_ASCII_RULES is set.
        assertEquals("www\uff0e\u65E5\u672C\u5E73\uff61jp",
                toUnicode("www\uFF0Exn--gwtq9nb2a\uFF61jp", IDNA.USE_STD3_RULES));

    }

    private static String toUnicode(String str, int flags) throws StringPrepParseException {
        return ExtendedIDNA.convertIDNToUnicode(str, flags).toString();
    }
}
