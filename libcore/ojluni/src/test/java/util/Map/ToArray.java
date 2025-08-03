/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8008785
 * @summary Ensure toArray() implementations return correct results.
 * @author Mike Duigou
 */
package test.java.util.Map;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.testng.annotations.Test;
import org.testng.Assert;

public class ToArray {

    /**
     * Number of elements per map.
     */
    private static final int TEST_SIZE = 5000;

    @Test
    public void testToArray() throws Throwable {
        Map<Integer, Long>[] maps = (Map<Integer, Long>[]) new Map[]{
                new HashMap<>(),
                new Hashtable<>(),
                new IdentityHashMap<>(),
                new LinkedHashMap<>(),
                new TreeMap<>(),
                new WeakHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentSkipListMap<>()
        };

        // for each map type.
        for (Map<Integer, Long> map : maps) {
            testMap(map);
        }
    }

    private static final Integer[] KEYS = new Integer[TEST_SIZE];

    private static final Long[] VALUES = new Long[TEST_SIZE];

    static {
        for (int each = 0; each < TEST_SIZE; each++) {
            KEYS[each]   = Integer.valueOf(each);
            VALUES[each] = Long.valueOf(each + TEST_SIZE);
        }
    }


    private static void testMap(Map<Integer, Long> map) {

        // Fill the map
        for (int each = 0; each < TEST_SIZE; each++) {
            map.put(KEYS[each], VALUES[each]);
        }

        // check the keys
        Object[] keys = map.keySet().toArray();
        Arrays.sort(keys);

        for(int each = 0; each < TEST_SIZE; each++) {
            Assert.assertTrue( keys[each] == KEYS[each]);
        }

        // check the values
        Object[] values = map.values().toArray();
        Arrays.sort(values);

        for(int each = 0; each < TEST_SIZE; each++) {
            Assert.assertTrue( values[each] == VALUES[each]);
        }

        // check the entries
        Map.Entry<Integer,Long>[] entries = map.entrySet().toArray(new Map.Entry[TEST_SIZE]);
        Arrays.sort( entries,new Comparator<Map.Entry<Integer,Long>>() {
            public int compare(Map.Entry<Integer,Long> o1, Map.Entry<Integer,Long> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }});

        for(int each = 0; each < TEST_SIZE; each++) {
            Assert.assertTrue( entries[each].getKey() == KEYS[each] && entries[each].getValue() == VALUES[each]);
        }
    }
}