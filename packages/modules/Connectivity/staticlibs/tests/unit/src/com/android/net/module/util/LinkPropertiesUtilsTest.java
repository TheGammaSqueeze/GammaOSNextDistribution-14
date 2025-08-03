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

import static com.android.testutils.MiscAsserts.assertSameElements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.annotation.SuppressLint;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.LinkPropertiesUtils.CompareOrUpdateResult;
import com.android.net.module.util.LinkPropertiesUtils.CompareResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@RunWith(AndroidJUnit4.class)
public final class LinkPropertiesUtilsTest {
    @SuppressLint("NewApi")
    private static final IpPrefix PREFIX = new IpPrefix(toInetAddress("75.208.6.0"), 24);
    private static final InetAddress V4_ADDR = toInetAddress("75.208.6.1");
    private static final InetAddress V6_ADDR  = toInetAddress(
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    private static final InetAddress DNS1 = toInetAddress("75.208.7.1");
    private static final InetAddress DNS2 = toInetAddress("69.78.7.1");

    private static final InetAddress GATEWAY1 = toInetAddress("75.208.8.1");
    private static final InetAddress GATEWAY2 = toInetAddress("69.78.8.1");

    private static final String IF_NAME = "wlan0";
    private static final LinkAddress V4_LINKADDR = new LinkAddress(V4_ADDR, 32);
    private static final LinkAddress V6_LINKADDR = new LinkAddress(V6_ADDR, 128);
    private static final RouteInfo RT_INFO1 = new RouteInfo(PREFIX, GATEWAY1, IF_NAME);
    private static final RouteInfo RT_INFO2 = new RouteInfo(PREFIX, GATEWAY2, IF_NAME);
    private static final String TEST_DOMAIN = "link.properties.com";

    private static InetAddress toInetAddress(String addrString) {
        return InetAddresses.parseNumericAddress(addrString);
    }

    private LinkProperties createTestObject() {
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(IF_NAME);
        lp.addLinkAddress(V4_LINKADDR);
        lp.addLinkAddress(V6_LINKADDR);
        lp.addDnsServer(DNS1);
        lp.addDnsServer(DNS2);
        lp.setDomains(TEST_DOMAIN);
        lp.addRoute(RT_INFO1);
        lp.addRoute(RT_INFO2);
        lp.setHttpProxy(ProxyInfo.buildDirectProxy("test", 8888));
        return lp;
    }

    @Test
    public void testLinkPropertiesIdenticalEqual() {
        final LinkProperties source = createTestObject();
        final LinkProperties target = new LinkProperties(source);

        assertTrue(LinkPropertiesUtils.isIdenticalInterfaceName(source, target));
        assertTrue(LinkPropertiesUtils.isIdenticalInterfaceName(target, source));

        assertTrue(LinkPropertiesUtils.isIdenticalAddresses(source, target));
        assertTrue(LinkPropertiesUtils.isIdenticalAddresses(target, source));

        assertTrue(LinkPropertiesUtils.isIdenticalAllLinkAddresses(source, target));
        assertTrue(LinkPropertiesUtils.isIdenticalAllLinkAddresses(target, source));

        assertTrue(LinkPropertiesUtils.isIdenticalDnses(source, target));
        assertTrue(LinkPropertiesUtils.isIdenticalDnses(target, source));

        assertTrue(LinkPropertiesUtils.isIdenticalRoutes(source, target));
        assertTrue(LinkPropertiesUtils.isIdenticalRoutes(target, source));

        assertTrue(LinkPropertiesUtils.isIdenticalHttpProxy(source, target));
        assertTrue(LinkPropertiesUtils.isIdenticalHttpProxy(target, source));

        // Test different interface name.
        target.setInterfaceName("lo");
        assertFalse(LinkPropertiesUtils.isIdenticalInterfaceName(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalInterfaceName(target, source));
        // Restore interface name
        target.setInterfaceName(IF_NAME);

        // Compare addresses.size() not equals.
        final LinkAddress testLinkAddr = new LinkAddress(toInetAddress("75.208.6.2"), 32);
        target.addLinkAddress(testLinkAddr);
        assertFalse(LinkPropertiesUtils.isIdenticalAddresses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalAddresses(target, source));

        assertFalse(LinkPropertiesUtils.isIdenticalAllLinkAddresses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalAllLinkAddresses(target, source));

        // Currently, target contains V4_LINKADDR, V6_LINKADDR and testLinkAddr.
        // Compare addresses.size() equals but contains different address.
        target.removeLinkAddress(V4_LINKADDR);
        assertEquals(source.getAddresses().size(), target.getAddresses().size());
        assertFalse(LinkPropertiesUtils.isIdenticalAddresses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalAddresses(target, source));
        assertFalse(LinkPropertiesUtils.isIdenticalAllLinkAddresses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalAllLinkAddresses(target, source));
        // Restore link address
        target.addLinkAddress(V4_LINKADDR);
        target.removeLinkAddress(testLinkAddr);

        // Compare size not equals.
        target.addDnsServer(toInetAddress("75.208.10.1"));
        assertFalse(LinkPropertiesUtils.isIdenticalDnses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalDnses(target, source));

        // Compare the same servers but target has different domains.
        target.removeDnsServer(toInetAddress("75.208.10.1"));
        target.setDomains("test.com");
        assertFalse(LinkPropertiesUtils.isIdenticalDnses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalDnses(target, source));

        // Test null domain.
        target.setDomains(null);
        assertFalse(LinkPropertiesUtils.isIdenticalDnses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalDnses(target, source));
        // Restore domain
        target.setDomains(TEST_DOMAIN);

        // Compare size not equals.
        final RouteInfo testRoute = new RouteInfo(toInetAddress("75.208.7.1"));
        target.addRoute(testRoute);
        assertFalse(LinkPropertiesUtils.isIdenticalRoutes(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalRoutes(target, source));

        // Currently, target contains RT_INFO1, RT_INFO2 and testRoute.
        // Compare size equals but different routes.
        target.removeRoute(RT_INFO1);
        assertEquals(source.getRoutes().size(), target.getRoutes().size());
        assertFalse(LinkPropertiesUtils.isIdenticalRoutes(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalRoutes(target, source));
        // Restore route
        target.addRoute(RT_INFO1);
        target.removeRoute(testRoute);

        // Test different proxy.
        target.setHttpProxy(ProxyInfo.buildDirectProxy("hello", 8888));
        assertFalse(LinkPropertiesUtils.isIdenticalHttpProxy(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalHttpProxy(target, source));

        // Test null proxy.
        target.setHttpProxy(null);
        assertFalse(LinkPropertiesUtils.isIdenticalHttpProxy(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalHttpProxy(target, source));

        final LinkProperties stacked = new LinkProperties();
        stacked.setInterfaceName("v4-" + target.getInterfaceName());
        stacked.addLinkAddress(testLinkAddr);
        target.addStackedLink(stacked);
        assertFalse(LinkPropertiesUtils.isIdenticalAllLinkAddresses(source, target));
        assertFalse(LinkPropertiesUtils.isIdenticalAllLinkAddresses(target, source));
    }

    private <T> void compareResult(List<T> oldItems, List<T> newItems, List<T> expectRemoved,
            List<T> expectAdded) {
        CompareResult<T> result = new CompareResult<>(oldItems, newItems);
        assertEquals(new ArraySet<>(expectAdded), new ArraySet<>(result.added));
        assertEquals(new ArraySet<>(expectRemoved), (new ArraySet<>(result.removed)));
    }

    @Test
    public void testCompareResult() {
        // Either adding or removing items
        compareResult(Arrays.asList(1, 2, 3, 4), Arrays.asList(1),
                Arrays.asList(2, 3, 4), new ArrayList<>());
        compareResult(Arrays.asList(1, 2), Arrays.asList(3, 2, 1, 4),
                new ArrayList<>(), Arrays.asList(3, 4));


        // adding and removing items at the same time
        compareResult(Arrays.asList(1, 2, 3, 4), Arrays.asList(2, 3, 4, 5),
                Arrays.asList(1), Arrays.asList(5));
        compareResult(Arrays.asList(1, 2, 3), Arrays.asList(4, 5, 6),
                Arrays.asList(1, 2, 3), Arrays.asList(4, 5, 6));

        // null cases
        compareResult(Arrays.asList(1, 2, 3), null, Arrays.asList(1, 2, 3), new ArrayList<>());
        compareResult(null, Arrays.asList(3, 2, 1), new ArrayList<>(), Arrays.asList(1, 2, 3));
        compareResult(null, null, new ArrayList<>(), new ArrayList<>());

        // Some more tests with strings
        final ArrayList<String> list1 = new ArrayList<>();
        list1.add("string1");

        final ArrayList<String> list2 = new ArrayList<>(list1);
        final CompareResult<String> cr1 = new CompareResult<>(list1, list2);
        assertTrue(cr1.added.isEmpty());
        assertTrue(cr1.removed.isEmpty());

        list2.add("string2");
        final CompareResult<String> cr2 = new CompareResult<>(list1, list2);
        assertEquals(Arrays.asList("string2"), cr2.added);
        assertTrue(cr2.removed.isEmpty());

        list2.remove("string1");
        final CompareResult<String> cr3 = new CompareResult<>(list1, list2);
        assertEquals(Arrays.asList("string2"), cr3.added);
        assertEquals(Arrays.asList("string1"), cr3.removed);

        list1.add("string2");
        final CompareResult<String> cr4 = new CompareResult<>(list1, list2);
        assertTrue(cr4.added.isEmpty());
        assertEquals(Arrays.asList("string1"), cr3.removed);
    }

    @Test
    public void testCompareAddresses() {
        final LinkProperties source = createTestObject();
        final LinkProperties target = new LinkProperties(source);
        final InetAddress addr1 = toInetAddress("75.208.6.2");
        final LinkAddress linkAddr1 = new LinkAddress(addr1, 32);

        CompareResult<LinkAddress> results = LinkPropertiesUtils.compareAddresses(source, target);
        assertEquals(0, results.removed.size());
        assertEquals(0, results.added.size());

        source.addLinkAddress(linkAddr1);
        results = LinkPropertiesUtils.compareAddresses(source, target);
        assertEquals(1, results.removed.size());
        assertEquals(linkAddr1, results.removed.get(0));
        assertEquals(0, results.added.size());

        final InetAddress addr2 = toInetAddress("75.208.6.3");
        final LinkAddress linkAddr2 = new LinkAddress(addr2, 32);

        target.addLinkAddress(linkAddr2);
        results = LinkPropertiesUtils.compareAddresses(source, target);
        assertEquals(linkAddr1, results.removed.get(0));
        assertEquals(linkAddr2, results.added.get(0));
    }

    private void assertCompareOrUpdateResult(CompareOrUpdateResult result,
            List<String> expectedAdded, List<String> expectedRemoved,
            List<String> expectedUpdated) {
        assertSameElements(expectedAdded, result.added);
        assertSameElements(expectedRemoved, result.removed);
        assertSameElements(expectedUpdated, result.updated);
    }

    private List<String> strArray(String... strs) {
        return Arrays.asList(strs);
    }

    @Test
    public void testCompareOrUpdateResult() {
        // As the item type, use a simple string. An item is defined to be an update of another item
        // if the string starts with the same alphabetical characters.
        // Extracting the key from the object is just a regexp.
        Function<String, String> extractPrefix = (s) -> s.replaceFirst("^([a-z]+).*", "$1");
        assertEquals("goodbye", extractPrefix.apply("goodbye1234"));

        List<String> oldItems = strArray("hello123", "goodbye5678", "howareyou669");
        List<String> newItems = strArray("hello123", "goodbye000", "verywell");

        final List<String> emptyList = new ArrayList<>();

        // Items -> empty: everything removed.
        CompareOrUpdateResult<String, String> result =
                new CompareOrUpdateResult<String, String>(oldItems, emptyList, extractPrefix);
        assertCompareOrUpdateResult(result,
                emptyList, strArray("hello123", "howareyou669", "goodbye5678"), emptyList);

        // Empty -> items: everything added.
        result = new CompareOrUpdateResult<String, String>(emptyList, newItems, extractPrefix);
        assertCompareOrUpdateResult(result,
                strArray("hello123", "goodbye000", "verywell"), emptyList,  emptyList);

        // Empty -> empty: no change.
        result = new CompareOrUpdateResult<String, String>(newItems, newItems, extractPrefix);
        assertCompareOrUpdateResult(result,  emptyList,  emptyList, emptyList);

        // Added, removed, updated at the same time.
        result =  new CompareOrUpdateResult<>(oldItems, newItems, extractPrefix);
        assertCompareOrUpdateResult(result,
                strArray("verywell"), strArray("howareyou669"), strArray("goodbye000"));

        // Null -> items: everything added.
        result = new CompareOrUpdateResult<String, String>(null, newItems, extractPrefix);
        assertCompareOrUpdateResult(result,
                strArray("hello123", "goodbye000", "verywell"), emptyList,  emptyList);

        // Items -> null: everything removed.
        result = new CompareOrUpdateResult<String, String>(oldItems, null, extractPrefix);
        assertCompareOrUpdateResult(result,
                emptyList, strArray("hello123", "howareyou669", "goodbye5678"), emptyList);

        // Null -> null: all lists empty.
        result = new CompareOrUpdateResult<String, String>(null, null, extractPrefix);
        assertCompareOrUpdateResult(result, emptyList, emptyList, emptyList);
    }
}
