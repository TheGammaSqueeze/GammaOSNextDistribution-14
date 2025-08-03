/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4395472
 * @summary  java.net.URLEncode.encode(s, enc) treats an empty encoding name as "UTF-8"
 */
package test.java.net.URLEncoder;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import org.testng.annotations.Test;
import org.testng.Assert;

public class URLEncoderEncodeArgs {

    @Test
    public void testEncodeNull() throws Exception {
        try {
            String s1 = URLEncoder.encode ("Hello World", (String) null);
            Assert.fail("null reference was accepted as encoding name");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testEncodeEmpty() {
        try {
            String s2 = URLEncoder.encode ("Hello World", "");
            Assert.fail("empty string was accepted as encoding name");
        } catch (UnsupportedEncodingException ee) {
            return;
        }
    }
}