/*
 * Copyright (C) 2024 The Android Open Source Project
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

package libcore.android34.java.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

@RunWith(JUnit4.class)
public class ListTest {

    public static class MyList implements List<String> {

        private final ArrayList<String> l = new ArrayList<>();
        @Override
        public int size() {
            return l.size();
        }

        @Override
        public boolean isEmpty() {
            return l.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return l.contains(o);
        }

        @Override
        public Iterator<String> iterator() {
            return l.iterator();
        }

        @Override
        public Object[] toArray() {
            return l.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return l.toArray(a);
        }

        @Override
        public boolean add(String s) {
            return l.add(s);
        }

        @Override
        public boolean remove(Object o) {
            return l.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return l.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            return l.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends String> c) {
            return l.addAll(index, c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return l.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return l.retainAll(c);
        }

        @Override
        public void clear() {
            l.clear();
        }

        @Override
        public String get(int index) {
            return l.get(index);
        }

        @Override
        public String set(int index, String s) {
            return l.set(index, s);
        }

        @Override
        public void add(int index, String s) {
            l.add(index, s);
        }

        @Override
        public String remove(int index) {
            return l.remove(index);
        }

        @Override
        public int indexOf(Object o) {
            return l.indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return l.lastIndexOf(o);
        }

        @Override
        public ListIterator<String> listIterator() {
            return l.listIterator();
        }

        @Override
        public ListIterator<String> listIterator(int index) {
            return l.listIterator(index);
        }

        @Override
        public List<String> subList(int fromIndex, int toIndex) {
            return l.subList(fromIndex, toIndex);
        }

        /* New methods in OpenJDK 21 begins */

        /** vs void addFirst(E). A build should fail if compiling the latest SDK. */
        public MyList addFirst(String s) {
            l.add(0, s);
            return this;
        }
        /** vs void addLast(E). A build should fail if compiling the latest SDK. */
        public MyList addLast(String s) {
            l.add(s);
            return this;
        }
        /** vs E getFirst(). A build should fail if compiling the latest SDK. */
        public Optional<String> getFirst() {
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
        /** vs E getLast(). A build should fail if compiling the latest SDK. */
        public Optional<String> getLast() {
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(size() - 1));
        }

        /** vs String removeFirst(E). A build should fail if compiling the latest SDK. */
        public boolean removeFirst() {
            if (l.isEmpty()) {
                return false;
            } else {
                l.remove(0);
                return true;
            }
        }
        /** vs String removeLast(E). A build should fail if compiling the latest SDK. */
        public boolean removeLast() {
            if (l.isEmpty()) {
                return false;
            } else {
                l.remove(size() - 1);
                return true;
            }
        }
    }

    private MyList myList;

    @Before
    public void setUp() {
        myList = new MyList();
    }

    @Test
    public void testAddFirst() {
        myList.addFirst("c");
        myList.addFirst("b");
        assertEquals(myList, myList.addFirst("a"));
        assertEquals("a", myList.getFirst().get());
        assertEquals("c", myList.getLast().get());
        assertTrue(myList.removeFirst());
        assertEquals("b", myList.getFirst().get());
        assertTrue(myList.removeFirst());
        assertTrue(myList.removeFirst());
        assertFalse(myList.removeFirst()); // False because the list is empty.
    }

    @Test
    public void testAddLast() {
        myList.addLast("a");
        myList.addLast("b");
        assertEquals(myList, myList.addLast("c"));
        assertEquals("a", myList.getFirst().get());
        assertEquals("c", myList.getLast().get());
        assertTrue(myList.removeLast());
        assertEquals("b", myList.getLast().get());
        assertTrue(myList.removeLast());
        assertTrue(myList.removeLast());
        assertFalse(myList.removeFirst()); // False because the list is empty.
    }
}
