/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6306829
 * @summary Verify assertions in get() javadocs
 * @author Martin Buchholz
 */
package test.java.util.Map;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.testng.annotations.Test;
import org.testng.Assert;

public class Get {

    @Test
    public void testGet() {
        testMap(new Hashtable<>());
        testMap(new HashMap<>());
        testMap(new IdentityHashMap<>());
        testMap(new LinkedHashMap<>());
        testMap(new ConcurrentHashMap<>());
        testMap(new WeakHashMap<>());
        testMap(new TreeMap<>());
        testMap(new ConcurrentSkipListMap<>());
    }

    private static void put(Map<Character,Boolean> m,
            Character key, Boolean value,
            Boolean oldValue) {
        if (oldValue != null) {
            Assert.assertTrue(m.containsValue(oldValue));
            Assert.assertTrue(m.values().contains(oldValue));
        }
        Assert.assertEquals(m.put(key, value), oldValue);
        Assert.assertEquals(m.get(key), value);
        Assert.assertTrue(m.containsKey(key));
        Assert.assertTrue(m.keySet().contains(key));
        Assert.assertTrue(m.containsValue(value));
        Assert.assertTrue(m.values().contains(value));
        Assert.assertTrue(! m.isEmpty());
    }

    private static void testMap(Map<Character,Boolean> m) {
        // We verify following assertions in get(Object) method javadocs
        boolean permitsNullKeys = (! (m instanceof ConcurrentMap ||
                m instanceof Hashtable     ||
                m instanceof SortedMap));
        boolean permitsNullValues = (! (m instanceof ConcurrentMap ||
                m instanceof Hashtable));
        boolean usesIdentity = m instanceof IdentityHashMap;

        System.err.println(m.getClass());
        put(m, 'A', true,  null);
        put(m, 'A', false, true);       // Guaranteed identical by JLS
        put(m, 'B', true,  null);
        put(m, new Character('A'), false, usesIdentity ? null : false);
        if (permitsNullKeys) {
            try {
                put(m, null, true,  null);
                put(m, null, false, true);
            }
            catch (Throwable t) { Assert.fail(); }
        } else {
            try { m.get(null); Assert.fail(m.getClass().getName() + " did not reject null key"); }
            catch (NullPointerException e) {}
            catch (Throwable t) { Assert.fail(); }

            try { m.put(null, true); Assert.fail(m.getClass().getName() + " did not reject null key"); }
            catch (NullPointerException e) {}
            catch (Throwable t) { Assert.fail(); }
        }
        if (permitsNullValues) {
            try {
                put(m, 'C', null, null);
                put(m, 'C', true, null);
                put(m, 'C', null, true);
            }
            catch (Throwable t) { Assert.fail(); }
        } else {
            try { m.put('A', null); Assert.fail(m.getClass().getName() + " did not reject null key"); }
            catch (NullPointerException e) {}
            catch (Throwable t) { Assert.fail(); }

            try { m.put('C', null); Assert.fail(m.getClass().getName() + " did not reject null key"); }
            catch (NullPointerException e) {}
            catch (Throwable t) { Assert.fail(); }
        }
    }
}