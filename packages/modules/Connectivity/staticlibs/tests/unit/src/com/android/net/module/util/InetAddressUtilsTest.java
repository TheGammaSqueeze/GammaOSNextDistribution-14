/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.InetAddresses;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InetAddressUtilsTest {

    private InetAddress parcelUnparcelAddress(InetAddress addr) {
        Parcel p = Parcel.obtain();
        InetAddressUtils.parcelInetAddress(p, addr, 0 /* flags */);
        p.setDataPosition(0);
        byte[] marshalled = p.marshall();
        p.recycle();
        p = Parcel.obtain();
        p.unmarshall(marshalled, 0, marshalled.length);
        p.setDataPosition(0);
        InetAddress out = InetAddressUtils.unparcelInetAddress(p);
        p.recycle();
        return out;
    }

    @Test
    public void testParcelUnparcelIpv4Address() throws Exception {
        InetAddress ipv4 = InetAddress.getByName("192.0.2.1");
        assertEquals(ipv4, parcelUnparcelAddress(ipv4));
    }

    @Test
    public void testParcelUnparcelIpv6Address() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("2001:db8::1");
        assertEquals(ipv6, parcelUnparcelAddress(ipv6));
    }

    @Test
    public void testParcelUnparcelScopedIpv6Address() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("fe80::1%42");
        assertEquals(42, ((Inet6Address) ipv6).getScopeId());
        Inet6Address out = (Inet6Address) parcelUnparcelAddress(ipv6);
        assertEquals(ipv6, out);
        assertEquals(42, out.getScopeId());
    }

    @Test
    public void testWithScopeId() {
        final int scopeId = 999;

        final String globalAddrStr = "2401:fa00:49c:484:dc41:e6ff:fefd:f180";
        final Inet6Address globalAddr = (Inet6Address) InetAddresses
                .parseNumericAddress(globalAddrStr);
        final Inet6Address updatedGlobalAddr = InetAddressUtils.withScopeId(globalAddr, scopeId);
        assertFalse(updatedGlobalAddr.isLinkLocalAddress());
        assertEquals(globalAddrStr, updatedGlobalAddr.getHostAddress());
        assertEquals(0, updatedGlobalAddr.getScopeId());

        final String localAddrStr = "fe80::4735:9628:d038:2087";
        final Inet6Address localAddr = (Inet6Address) InetAddresses
                .parseNumericAddress(localAddrStr);
        final Inet6Address updatedLocalAddr = InetAddressUtils.withScopeId(localAddr, scopeId);
        assertTrue(updatedLocalAddr.isLinkLocalAddress());
        assertEquals(localAddrStr + "%" + scopeId, updatedLocalAddr.getHostAddress());
        assertEquals(scopeId, updatedLocalAddr.getScopeId());
    }

    @Test
    public void testV4MappedV6Address() throws Exception {
        final Inet4Address v4Addr = (Inet4Address) InetAddress.getByName("192.0.2.1");
        final Inet6Address v4MappedV6Address = InetAddressUtils.v4MappedV6Address(v4Addr);
        final byte[] expectedAddrBytes = new byte[]{
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
        };
        assertArrayEquals(expectedAddrBytes, v4MappedV6Address.getAddress());
    }
}
