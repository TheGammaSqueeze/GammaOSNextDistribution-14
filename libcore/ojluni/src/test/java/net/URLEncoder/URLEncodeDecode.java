/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 4257115
 * @summary Test URL encoder and decoder on a string that contains
 * characters within and beyond the 8859-1 range.
 *
 */
package test.java.net.URLEncoder;

import java.io.*;
import java.net.*;
import org.testng.annotations.Test;
import org.testng.Assert;

public class URLEncodeDecode {

    static char chars[] = {'H', 'e', 'l', 'l', 'o',
            ' ', '+', '%',
            '-', '_', '.',       '!', '~', '*', '\'', '(',
            ')',
            '@',
            '\u00ae', '\u0101', '\u10a0'};

    static String str = new String(chars);

    static String correctEncodedUTF8 =
            "Hello+%2B%25-_.%21%7E*%27%28%29%40%C2%AE%C4%81%E1%82%A0";

    @Test
    public void testURLEncodeDecode() throws Exception {
        String enc = "UTF-8";
        String encoded;

        if (enc == null) {
            encoded = URLEncoder.encode(str);
        }
        else {
            encoded = URLEncoder.encode(str, enc);
        }
        Assert.assertEquals(encoded, correctEncodedUTF8);

        String decoded;

        if (enc == null)
            decoded = URLDecoder.decode(encoded);
        else
            decoded = URLDecoder.decode(encoded, enc);

        Assert.assertEquals(str, decoded);

    }


}