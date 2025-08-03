/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4245809 8029795
 * @summary Basic test for LinkedHashMap.  (Based on MapBash)
 */
package test.java.util.LinkedHashMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

import org.testng.Assert;
import org.testng.annotations.Test;

public class Basic {
    static final Random rnd = new Random(666);
    static final Integer nil = new Integer(0);

    @Test
    public void testBasic() {
        int numItr =  500;
        int mapSize = 500;

        // Linked List testk
        for (int i=0; i<numItr; i++) {
            Map<Integer,Integer> m = new LinkedHashMap();
            Integer head = nil;

            for (int j=0; j<mapSize; j++) {
                Integer newHead;
                do {
                    newHead = new Integer(rnd.nextInt());
                } while (m.containsKey(newHead));
                m.put(newHead, head);
                head = newHead;
            }
            Assert.assertEquals(m.size(), mapSize);

            Assert.assertEquals(new HashMap(m).hashCode(), m.hashCode());

            Map<Integer,Integer> m2 = new LinkedHashMap(); m2.putAll(m);
            m2.values().removeAll(m.keySet());
            Assert.assertFalse(m2.size()!= 1 || !m2.containsValue(nil));

            int j=0;
            while (head != nil) {
                Assert.assertTrue(m.containsKey(head));
                Integer newHead = m.get(head);
                Assert.assertTrue(newHead != null);
                m.remove(head);
                head = newHead;
                j++;
            }
            Assert.assertTrue(m.isEmpty());
            Assert.assertEquals(j, mapSize);
        }

        Map<Integer,Integer> m = new LinkedHashMap();
        for (int i=0; i<mapSize; i++)
            Assert.assertEquals(m.put(new Integer(i), new Integer(2*i)), null);
        for (int i=0; i<2*mapSize; i++)
            Assert.assertEquals(m.containsValue(new Integer(i)), (i%2==0));
        Assert.assertFalse(m.put(nil, nil) == null);
        Map<Integer,Integer> m2 = new LinkedHashMap(); m2.putAll(m);
        Assert.assertTrue(m.equals(m2));
        Assert.assertTrue(m2.equals(m));
        Set<Map.Entry<Integer,Integer>> s = m.entrySet(), s2 = m2.entrySet();
        Assert.assertTrue(s.equals(s2));
        Assert.assertTrue(s2.equals(s));
        Assert.assertTrue(s.containsAll(s2));
        Assert.assertTrue(s2.containsAll(s));

        m2 = serClone(m);
        Assert.assertTrue(m.equals(m2));
        Assert.assertTrue(m2.equals(m));
        s = m.entrySet(); s2 = m2.entrySet();
        Assert.assertTrue(s.equals(s2));
        Assert.assertTrue(s2.equals(s));
        Assert.assertTrue(s.containsAll(s2));
        Assert.assertTrue(s2.containsAll(s));

        s2.removeAll(s);
        Assert.assertTrue(m2.isEmpty());

        m2.putAll(m);
        m2.clear();
        Assert.assertTrue(m2.isEmpty());

        Iterator it = m.entrySet().iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
        Assert.assertTrue(m.isEmpty());

        // Test ordering properties with insert order
        m = new LinkedHashMap();
        List<Integer> l = new ArrayList(mapSize);
        for (int i=0; i<mapSize; i++) {
            Integer x = new Integer(i);
            m.put(x, x);
            l.add(x);
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l));
        for (int i=mapSize-1; i>=0; i--) {
            Integer x = (Integer) l.get(i);
            Assert.assertTrue(m.get(x).equals(x));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l));

        for (int i=mapSize-1; i>=0; i--) {
            Integer x = (Integer) l.get(i);
            m.put(x, x);
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l));

        m2 = (Map) ((LinkedHashMap)m).clone();
        Assert.assertTrue(m.equals(m2));

        List<Integer> l2 = new ArrayList(l);
        Collections.shuffle(l2);
        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(m2.get(x).equals(x));
        }
        Assert.assertTrue(new ArrayList(m2.keySet()).equals(l));

        // Test ordering properties with access order
        m = new LinkedHashMap(2*mapSize, .75f, true);
        for (int i=0; i<mapSize; i++) {
            Integer x = new Integer(i);
            m.put(x, x);
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(m.get(x).equals(x));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(m.getOrDefault(x, new Integer(i + 1000)).equals(x));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(m.replace(x, x).equals(x));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(m.replace(x, x, x));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        BiFunction<Integer,Integer,Integer> f = (Integer y, Integer z) -> {
            Assert.assertTrue(Objects.equals(y,z));
            return new Integer(z);
        };

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(x.equals(m.merge(x, x, f)));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(x.equals(m.compute(x, f)));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(x.equals(m.remove(x)));
            Assert.assertTrue(x.equals(m.computeIfAbsent(x, Integer::valueOf)));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l2.get(i);
            Assert.assertTrue(x.equals(m.computeIfPresent(x, f)));
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l2));

        for (int i=0; i<mapSize; i++) {
            Integer x = new Integer(i);
            m.put(x, x);
        }
        Assert.assertTrue(new ArrayList(m.keySet()).equals(l));

        m2 = (Map) ((LinkedHashMap)m).clone();
        Assert.assertTrue(m.equals(m2));
        for (int i=0; i<mapSize; i++) {
            Integer x = (Integer) l.get(i);
            Assert.assertTrue(m2.get(x).equals(x));
        }
        Assert.assertTrue(new ArrayList(m2.keySet()).equals(l));

    }

    private static Map serClone(Map m) {
        Map result = null;
        try {
            // Serialize
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(m);
            out.flush();

            // Deserialize
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            out.close();
            ObjectInputStream in = new ObjectInputStream(bis);
            result = (Map)in.readObject();
            in.close();
        } catch (Exception e) {
            Assert.fail();
        }
        return result;
    }
}