/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313887 7003155
 * @summary Unit test for java.nio.file.Path
 */
package test.java.nio.file.Path;

import java.nio.file.*;
import java.net.URI;
import java.net.URISyntaxException;
import org.testng.annotations.Test;
import org.testng.Assert;

public class UriImportExport {

    static int failures = 0;

    /**
     * Test Path -> URI -> Path
     */
    static void testPath(String s) {
        Path path = Paths.get(s);
        URI uri = path.toUri();
        Path result = Paths.get(uri);
        Assert.assertEquals(result, path.toAbsolutePath());
    }

    /**
     * Test Path -> (expected) URI -> Path
     */
    static void testPath(String s, String expectedUri) {
        Path path = Paths.get(s);
        URI uri = path.toUri();
        Assert.assertEquals(uri.toString(), expectedUri);
        Path result = Paths.get(uri);
        Assert.assertEquals(result, path.toAbsolutePath());
    }

    /**
     * Test URI -> Path -> URI
     */
    static void testUri(String s) throws Exception {
        URI uri = URI.create(s);
        Path path = Paths.get(uri);
        URI result = path.toUri();
        Assert.assertEquals(result, uri);
    }

    /**
     * Test URI -> Path fails with IllegalArgumentException
     */
    static void testBadUri(String s) {
        URI uri = URI.create(s);
        try {
            Path path = Paths.get(uri);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void main() throws Exception {
        testBadUri("file:foo");
        testBadUri("file:/foo?q");
        testBadUri("file:/foo#f");

        String osname = System.getProperty("os.name");
        if (osname.startsWith("Windows")) {
            testPath("C:\\doesnotexist");
            testPath("C:doesnotexist");
            testPath("\\\\server.nowhere.oracle.com\\share\\");
            testPath("\\\\fe80--203-baff-fe5a-749ds1.ipv6-literal.net\\share\\missing",
                    "file://[fe80::203:baff:fe5a:749d%1]/share/missing");
        } else {
            testPath("doesnotexist");
            testPath("/doesnotexist");
            testPath("/does not exist");
            testUri("file:///");
            testUri("file:///foo/bar/doesnotexist");
            testUri("file:/foo/bar/doesnotexist");

            // file:///foo/bar/\u0440\u0443\u0441\u0441\u043A\u0438\u0439 (Russian)
            testUri("file:///foo/bar/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9");

            // invalid
            testBadUri("file:foo");
            testBadUri("file://server/foo");
            testBadUri("file:///foo%00");
        }
    }
}