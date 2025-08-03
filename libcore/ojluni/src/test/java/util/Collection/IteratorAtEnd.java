/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6529795
 * @summary next() does not change iterator state if throws NoSuchElementException
 * @author Martin Buchholz
 */
package test.java.util.Collection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import org.testng.Assert;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class IteratorAtEnd {

    private static final int SIZE = 6;

    @Test
    public void testArrayList() {
        testCollection(new ArrayList());
    }

    @Test
    public void testVector() {
        testCollection(new Vector());
    }

    @Test
    public void testLinkedList() {
        testCollection(new LinkedList());
    }

    @Test
    public void testArrayDeque() {
        testCollection(new ArrayDeque());
    }

    @Test
    public void testTreeSet() {
        testCollection(new TreeSet());
    }

    @Test
    public void testCopyOnWriteArrayList() {
        testCollection(new CopyOnWriteArrayList());
    }

    @Test
    public void testCopyOnWriteArraySet() {
        testCollection(new CopyOnWriteArraySet());
    }

    @Test
    public void testConcurrentSkipListSet() {
        testCollection(new ConcurrentSkipListSet());
    }

    @Test
    public void testPriorityQueue() {
        testCollection(new PriorityQueue());
    }

    @Test
    public void testLinkedBlockingQueue() {
        testCollection(new LinkedBlockingQueue());
    }

    @Test
    public void testArrayBlockingQueue() {
        testCollection(new ArrayBlockingQueue(100));
    }

    @Test
    public void testConcurrentLinkedDeque() {
        testCollection(new ConcurrentLinkedDeque());
    }

    @Test
    public void testConcurrentLinkedQueue() {
        testCollection(new ConcurrentLinkedQueue());
    }

    @Test
    public void testLinkedTransferQueue() {
        testCollection(new LinkedTransferQueue());
    }

    @Test
    public void testHashMap() {
        testMap(new HashMap());
    }

    @Test
    public void testHashtable() {
        testMap(new Hashtable());
    }

    @Test
    public void testLinkedHashMap() {
        testMap(new LinkedHashMap());
    }

    @Test
    public void testWeakHashMap() {
        testMap(new WeakHashMap());
    }

    @Test
    public void testIdentityHashMap() {
        testMap(new IdentityHashMap());
    }

    @Test
    public void testConcurrentHashMap() {
        testMap(new ConcurrentHashMap());
    }

    @Test
    public void testConcurrentSkipListMap() {
        testMap(new ConcurrentSkipListMap());
    }

    @Test
    public void testTreeMap() {
        testMap(new TreeMap());
    }

    static void testCollection(Collection c) {
        try {
            for (int i = 0; i < SIZE; i++) {
                c.add(i);
            }
            test(c);
        } catch (Throwable t) {
            Assert.fail("Unexpected exception: " + t.getMessage());
        }
    }

    static void testMap(Map m) {
        try {
            for (int i = 0; i < 3 * SIZE; i++) {
                m.put(i, i);
            }
            test(m.values());
            test(m.keySet());
            test(m.entrySet());
        } catch (Throwable t) {
            Assert.fail("Unexpected exception: " + t.getMessage());
        }
    }

    static void test(Collection c) {
        try {
            final Iterator it = c.iterator();
            THROWS(NoSuchElementException.class,
                    () -> {
                        while (true) {
                            it.next();
                        }
                    });
            try {
                it.remove();
            } catch (UnsupportedOperationException exc) {
                return;
            }
        } catch (Throwable t) {
            Assert.fail("Unexpected exception: " + t.getMessage());
        }

        if (c instanceof List) {
            final List list = (List) c;
            try {
                final ListIterator it = list.listIterator(0);
                it.next();
                final Object x = it.previous();
                THROWS(NoSuchElementException.class, it::previous);
                try {
                    it.remove();
                } catch (UnsupportedOperationException exc) {
                    return;
                }
                Assert.assertFalse(list.get(0).equals(x));
            } catch (Throwable t) {
                Assert.fail("Unexpected exception: " + t.getMessage());
            }

            try {
                final ListIterator it = list.listIterator(list.size());
                it.previous();
                final Object x = it.next();
                THROWS(NoSuchElementException.class, it::next);
                try {
                    it.remove();
                } catch (UnsupportedOperationException exc) {
                    return;
                }
                Assert.assertFalse(list.get(list.size() - 1).equals(x));
            } catch (Throwable t) {
                Assert.fail("Unexpected exception: " + t.getMessage());
            }
        }
    }

    interface Fun {

        void f() throws Throwable;
    }

    static void THROWS(Class<? extends Throwable> k, Fun... fs) {
        for (Fun f : fs) {
            try {
                f.f();
                Assert.fail("Expected " + k.getName() + " not thrown");
            } catch (Throwable t) {
                if (!k.isAssignableFrom(t.getClass())) {
                    Assert.fail("Unexpected exception: " + t.getMessage());
                }
            }
        }
    }
}
