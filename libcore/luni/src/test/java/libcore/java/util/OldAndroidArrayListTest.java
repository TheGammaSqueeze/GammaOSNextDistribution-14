/*
 * Copyright (C) 2008 The Android Open Source Project
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

package libcore.java.util;

import junit.framework.TestCase;

import java.util.ArrayList;

/**
 * This test case tests several often used functionality of ArrayLists.
 */
public class OldAndroidArrayListTest extends TestCase {

    @SuppressWarnings("unchecked")
    public void testArrayList() throws Exception {
        ArrayList array = new ArrayList();
        assertEquals(0, array.size());
        assertTrue(array.isEmpty());

        array.add(Integer.valueOf(0));
        array.add(0, Integer.valueOf(1));
        array.add(1, Integer.valueOf(2));
        array.add(Integer.valueOf(3));
        array.add(Integer.valueOf(1));

        assertEquals(5, array.size());
        assertFalse(array.isEmpty());

        assertEquals(1, ((Integer) array.get(0)).intValue());
        assertEquals(2, ((Integer) array.get(1)).intValue());
        assertEquals(0, ((Integer) array.get(2)).intValue());
        assertEquals(3, ((Integer) array.get(3)).intValue());
        assertEquals(1, ((Integer) array.get(4)).intValue());

        assertFalse(array.contains(null));
        assertTrue(array.contains(Integer.valueOf(2)));
        assertEquals(0, array.indexOf(Integer.valueOf(1)));
        assertEquals(4, array.lastIndexOf(Integer.valueOf(1)));
        assertTrue(array.indexOf(Integer.valueOf(5)) < 0);
        assertTrue(array.lastIndexOf(Integer.valueOf(5)) < 0);

        array.remove(1);
        array.remove(1);

        assertEquals(3, array.size());
        assertFalse(array.isEmpty());
        assertEquals(1, ((Integer) array.get(0)).intValue());
        assertEquals(3, ((Integer) array.get(1)).intValue());
        assertEquals(1, ((Integer) array.get(2)).intValue());

        assertFalse(array.contains(null));
        assertFalse(array.contains(Integer.valueOf(2)));
        assertEquals(0, array.indexOf(Integer.valueOf(1)));
        assertEquals(2, array.lastIndexOf(Integer.valueOf(1)));
        assertTrue(array.indexOf(Integer.valueOf(5)) < 0);
        assertTrue(array.lastIndexOf(Integer.valueOf(5)) < 0);

        array.clear();

        assertEquals(0, array.size());
        assertTrue(array.isEmpty());
        assertTrue(array.indexOf(Integer.valueOf(5)) < 0);
        assertTrue(array.lastIndexOf(Integer.valueOf(5)) < 0);

        ArrayList al = new ArrayList();

        assertFalse(al.remove(null));
        assertFalse(al.remove("string"));

        al.add("string");
        al.add(null);

        assertTrue(al.remove(null));
        assertTrue(al.remove("string"));
    }
}

