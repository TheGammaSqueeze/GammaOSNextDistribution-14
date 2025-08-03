/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
package test.java.net.InetSocketAddress;

/*
 * @test
 * @bug 6469803
 * @summary Socket creation on Windows takes a long time if web proxy does not have a DNS
 */

import java.net.*;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

public class B6469803 {

    @Test
    public void testLocalHost() {
        InetSocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        String s = addr.getHostString();
        assertTrue(s.equals("192.168.1.1"));
        addr = new InetSocketAddress("localhost", 12345);
        s = addr.getHostString();
        assertTrue(s.equals("localhost"));
    }
}