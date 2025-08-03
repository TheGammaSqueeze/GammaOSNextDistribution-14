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

package com.android.net.module.util;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.RouteInfo;

import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class NetUtilsTest {
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final InetAddress V4_ADDR1 = toInetAddress("75.208.7.1");
    private static final InetAddress V4_ADDR2 = toInetAddress("75.208.7.2");
    private static final InetAddress V6_ADDR1 = toInetAddress("2001:0db8:85a3::8a2e:0370:7334");
    private static final InetAddress V6_ADDR2 = toInetAddress("2001:0db8:85a3::8a2e:0370:7335");

    private static final InetAddress V4_GATEWAY = toInetAddress("75.208.8.1");
    private static final InetAddress V6_GATEWAY = toInetAddress("fe80::6:0000:613");

    private static final InetAddress V4_DEST = toInetAddress("75.208.8.15");
    private static final InetAddress V6_DEST = toInetAddress("2001:db8:cafe::123");

    private static final RouteInfo V4_EXPECTED = new RouteInfo(new IpPrefix("75.208.8.0/24"),
            V4_GATEWAY, "wlan0");
    private static final RouteInfo V6_EXPECTED = new RouteInfo(new IpPrefix("2001:db8:cafe::/64"),
            V6_GATEWAY, "wlan0");

    private static InetAddress toInetAddress(String addr) {
        return InetAddresses.parseNumericAddress(addr);
    }

    @Test
    public void testAddressTypeMatches() {
        assertTrue(NetUtils.addressTypeMatches(V4_ADDR1, V4_ADDR2));
        assertTrue(NetUtils.addressTypeMatches(V6_ADDR1, V6_ADDR2));
        assertFalse(NetUtils.addressTypeMatches(V4_ADDR1, V6_ADDR1));
        assertFalse(NetUtils.addressTypeMatches(V6_ADDR1, V4_ADDR1));
    }

    @Test
    public void testSelectBestRoute() {
        final List<RouteInfo> routes = new ArrayList<>();

        RouteInfo route = NetUtils.selectBestRoute(null, V4_DEST);
        assertNull(route);
        route = NetUtils.selectBestRoute(routes, null);
        assertNull(route);

        route = NetUtils.selectBestRoute(routes, V4_DEST);
        assertNull(route);

        routes.add(V4_EXPECTED);
        // "75.208.0.0/16" is not an expected result since it is not the longest prefix.
        routes.add(new RouteInfo(new IpPrefix("75.208.0.0/16"), V4_GATEWAY, "wlan0"));
        routes.add(new RouteInfo(new IpPrefix("75.208.7.0/24"), V4_GATEWAY, "wlan0"));

        routes.add(V6_EXPECTED);
        // "2001:db8::/32" is not an expected result since it is not the longest prefix.
        routes.add(new RouteInfo(new IpPrefix("2001:db8::/32"), V6_GATEWAY, "wlan0"));
        routes.add(new RouteInfo(new IpPrefix("2001:db8:beef::/64"), V6_GATEWAY, "wlan0"));

        // Verify expected v4 route is selected
        route = NetUtils.selectBestRoute(routes, V4_DEST);
        assertEquals(V4_EXPECTED, route);

        // Verify expected v6 route is selected
        route = NetUtils.selectBestRoute(routes, V6_DEST);
        assertEquals(V6_EXPECTED, route);

        // Remove expected v4 route
        routes.remove(V4_EXPECTED);
        route = NetUtils.selectBestRoute(routes, V4_DEST);
        assertNotEquals(V4_EXPECTED, route);

        // Remove expected v6 route
        routes.remove(V6_EXPECTED);
        route = NetUtils.selectBestRoute(routes, V4_DEST);
        assertNotEquals(V6_EXPECTED, route);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testSelectBestRouteWithExcludedRoutes() {
        final List<RouteInfo> routes = new ArrayList<>();

        routes.add(V4_EXPECTED);
        routes.add(new RouteInfo(new IpPrefix("75.208.0.0/16"), V4_GATEWAY, "wlan0"));
        routes.add(new RouteInfo(new IpPrefix("75.208.7.0/24"), V4_GATEWAY, "wlan0"));

        routes.add(V6_EXPECTED);
        routes.add(new RouteInfo(new IpPrefix("2001:db8::/32"), V6_GATEWAY, "wlan0"));
        routes.add(new RouteInfo(new IpPrefix("2001:db8:beef::/64"), V6_GATEWAY, "wlan0"));

        // After adding excluded v4 route with longer prefix, expected result is null.
        routes.add(new RouteInfo(new IpPrefix("75.208.8.0/28"), null /* gateway */, "wlan0",
                RouteInfo.RTN_THROW));
        RouteInfo route = NetUtils.selectBestRoute(routes, V4_DEST);
        assertNull(route);

        // After adding excluded v6 route with longer prefix, expected result is null.
        routes.add(new RouteInfo(new IpPrefix("2001:db8:cafe::/96"), null /* gateway */, "wlan0",
                RouteInfo.RTN_THROW));
        route = NetUtils.selectBestRoute(routes, V6_DEST);
        assertNull(route);
    }
}

