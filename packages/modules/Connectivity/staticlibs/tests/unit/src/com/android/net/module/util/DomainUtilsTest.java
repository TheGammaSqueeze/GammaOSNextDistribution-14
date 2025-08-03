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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import libcore.util.HexEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DomainUtilsTest {
    @Test
    public void testEncodeInvalidDomain() {
        byte[] buffer = DomainUtils.encode(".google.com");
        assertNull(buffer);

        buffer = DomainUtils.encode("google.com.");
        assertNull(buffer);

        buffer = DomainUtils.encode("-google.com");
        assertNull(buffer);

        buffer = DomainUtils.encode("google.com-");
        assertNull(buffer);

        buffer = DomainUtils.encode("google..com");
        assertNull(buffer);

        buffer = DomainUtils.encode("google!.com");
        assertNull(buffer);

        buffer = DomainUtils.encode("google.o");
        assertNull(buffer);

        buffer = DomainUtils.encode("google,com");
        assertNull(buffer);
    }

    @Test
    public void testEncodeValidDomainNamesWithoutCompression() {
        // Single domain: "google.com"
        String suffix = "06676F6F676C6503636F6D00";
        byte[] buffer = DomainUtils.encode("google.com");
        //assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // Single domain: "google-guest.com"
        suffix = "0C676F6F676C652D677565737403636F6D00";
        buffer = DomainUtils.encode("google-guest.com");
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "example.corp.google.com", "corp.google.com", "google.com"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00" // example.corp.google.com
                + "04636F727006676F6F676C6503636F6D00"                // corp.google.com
                + "06676F6F676C6503636F6D00";                         // google.com
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "corp.google.com", "google.com"},
                false /* compression */);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));


        // domain search list: "example.corp.google.com", "corp..google.com"(invalid domain),
        // "google.com"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00" // example.corp.google.com
                + "06676F6F676C6503636F6D00";                         // google.com
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "corp..google.com", "google.com"},
                false /* compression */);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // Invalid domain search list: "corp..google.com", "..google.com"
        buffer = DomainUtils.encode(new String[] {"corp..google.com", "..google.com"},
                false /* compression */);
        assertEquals(0, buffer.length);
    }

    @Test
    public void testEncodeValidDomainNamesWithCompression() {
        // domain search list: "example.corp.google.com", "corp.google.com", "google.com"
        String suffix =
                "076578616D706C6504636F727006676F6F676C6503636F6D00"  // example.corp.google.com
                + "C008"                                              // corp.google.com
                + "C00D";                                             // google.com
        byte[] buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "corp.google.com", "google.com"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "example.corp.google.com", "a.example.corp.google.com", "google.com"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00" // example.corp.google.com
                + "0161C000"                                          // a.example.corp.google.com
                + "C00D";                                             // google.com
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "a.example.corp.google.com", "google.com"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "example.corp.google.com", "google.com", "gle.com"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00" // example.corp.google.com
                + "C00D"                                              // google.com
                + "03676C65C014";                                     // gle.com
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "google.com", "gle.com"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "example.corp.google.com", "google.com", "google"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00" // example.corp.google.com
                + "C00D";                                              // google.com
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "google.com", "google"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "example.corp.google.com", "..google.com"(invalid domain), "google"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00"; // example.corp.google.com
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "..google.com", "google"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "example.corp.google.com", "suffix.example.edu.cn", "edu.cn"
        suffix = "076578616D706C6504636F727006676F6F676C6503636F6D00" // example.corp.google.com
                + "06737566666978076578616D706C650365647502636E00"    // suffix.example.edu.cn
                + "C028";                                             // edu.cn
        buffer = DomainUtils.encode(new String[] {
                "example.corp.google.com", "suffix.example.edu.cn", "edu.cn"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));

        // domain search list: "google.com", "example.com", "sub.example.com"
        suffix = "06676F6F676C6503636F6D00"                           // google.com
                + "076578616D706C65C007"                              // example.com
                + "03737562C00C";                                     // sub.example.com
        buffer = DomainUtils.encode(new String[] {
                "google.com", "example.com", "sub.example.com"}, true);
        assertNotNull(buffer);
        assertEquals(suffix, HexEncoding.encodeToString(buffer));
    }

    @Test
    public void testDecodeDomainNames() {
        ArrayList<String> suffixStringList;
        String suffixes = "06676F6F676C6503636F6D00" // google.com
                + "076578616D706C6503636F6D00"       // example.com
                + "06676F6F676C6500";                // google
        List<String> expected = Arrays.asList("google.com", "example.com");
        ByteBuffer buffer = ByteBuffer.wrap(HexEncoding.decode(suffixes));
        suffixStringList = DomainUtils.decode(buffer, false /* compression */);
        assertEquals(expected, suffixStringList);

        // include suffix with invalid length: 64
        suffixes = "06676F6F676C6503636F6D00"        // google.com
                + "406578616D706C6503636F6D00"       // example.com(length=64)
                + "06676F6F676C6500";                // google
        expected = Arrays.asList("google.com");
        buffer = ByteBuffer.wrap(HexEncoding.decode(suffixes));
        suffixStringList = DomainUtils.decode(buffer, false /* compression */);
        assertEquals(expected, suffixStringList);

        // include suffix with invalid length: 0
        suffixes = "06676F6F676C6503636F6D00"         // google.com
                + "076578616D706C6503636F6D00"        // example.com
                + "00676F6F676C6500";                 // google(length=0)
        expected = Arrays.asList("google.com", "example.com");
        buffer = ByteBuffer.wrap(HexEncoding.decode(suffixes));
        suffixStringList = DomainUtils.decode(buffer, false /* compression */);
        assertEquals(expected, suffixStringList);

        suffixes =
                "076578616D706C6504636F727006676F6F676C6503636F6D00"  // example.corp.google.com
                + "C008"                                              // corp.google.com
                + "C00D";                                             // google.com
        expected = Arrays.asList("example.corp.google.com", "corp.google.com", "google.com");
        buffer = ByteBuffer.wrap(HexEncoding.decode(suffixes));
        suffixStringList = DomainUtils.decode(buffer, true /* compression */);
        assertEquals(expected, suffixStringList);
    }
}
