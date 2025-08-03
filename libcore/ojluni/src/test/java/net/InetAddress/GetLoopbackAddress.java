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
package test.java.net.InetAddress;

/**
 * @test
 * @bug 6376404 8201545
 * @summary InetAddress needs a getLoopbackAddress
 */
import java.net.*;
import org.testng.annotations.Test;
import org.testng.Assert;

public class GetLoopbackAddress
{
    static InetAddress IPv4Loopback;
    static InetAddress IPv6Loopback;

    static {
        try {
            IPv4Loopback = InetAddress.getByAddress(
                    new byte[] {0x7F,0x00,0x00,0x01});

            IPv6Loopback = InetAddress.getByAddress(
                    new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});
        } catch (UnknownHostException e) {
        }
    }

    @Test
    public void testLoopbackAddresses() throws Exception {
        InetAddress addr = InetAddress.getLoopbackAddress();

        if (!(addr.equals(IPv4Loopback) || addr.equals(IPv6Loopback))) {
            Assert.fail("Failed: getLoopbackAddress" +
                    " not returning a valid loopback address");
        }

        InetAddress addr2 = InetAddress.getLoopbackAddress();

        Assert.assertEquals(addr, addr2);

        InetAddress addrFromNullHost = InetAddress.getByName(null);
        Assert.assertTrue(addrFromNullHost.isLoopbackAddress());

        InetAddress addrFromEmptyHost = InetAddress.getByName("");
        Assert.assertTrue(addrFromEmptyHost.isLoopbackAddress());

        InetAddress[] addrsByNull = InetAddress.getAllByName(null);
        Assert.assertTrue(addrsByNull[0].isLoopbackAddress());
        InetAddress[] addrsByEmpty = InetAddress.getAllByName("");
        Assert.assertTrue(addrsByEmpty[0].isLoopbackAddress());
    }
}