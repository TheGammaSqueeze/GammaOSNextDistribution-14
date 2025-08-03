/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertNotEquals;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class WeakHashMapTest extends TestCase {

    static Data[] data = new Data[100];

    public void test_replaceAll() {
        initializeData();
        WeakHashMap<Data, String> map = new WeakHashMap<>();
        for(int i = 0; i < data.length; i++) {
            map.put(data[i], "");
        }
        map.replaceAll((k, v) -> k.value);

        for(int i = 0; i < data.length; i++) {
            assertEquals(data[i].value, map.get(data[i]));
        }

        try {
            map.replaceAll(new java.util.function.BiFunction<Data, String, String>() {
                @Override
                public String apply(Data k, String v) {
                    map.put(new Data(), "");
                    return v;
                }
            });
            fail();
        } catch (ConcurrentModificationException expected) {
        }

        try {
            map.replaceAll(null);
            fail();
        } catch (NullPointerException expected) {
        }

        map.clear();
        for(int i = 0; i < data.length; i++) {
            map.put(data[i], data[i].value);
        }

        map.replaceAll((k, v) -> null);

        for(int i = 0; i < data.length; i++) {
            assertNull(map.get(data[i]));
        }
        assertEquals(data.length, map.size());
    }

    public void testContainsNullValue() {
        var map = new WeakHashMap<String, String>();

        assertFalse(map.containsValue(null));

        map.put("key", "value");

        assertFalse(map.containsValue(null));

        map.put("key", null);

        assertTrue(map.containsValue(null));
    }

    public void testEntrySet_removeMapping() {
        var map = new WeakHashMap<String, String>();
        assertFalse(map.entrySet().remove(new Object()));

        assertFalse(map.entrySet().remove(Map.entry("key", "value")));

        map.put("key", "value");

        assertTrue(map.entrySet().remove(Map.entry("key", "value")));
        assertTrue(map.isEmpty());
    }

    public void testEntrySet_clear() {
        var map = new WeakHashMap<String, String>();

        map.put("key", "value");

        map.entrySet().clear();

        assertTrue(map.isEmpty());
    }

    public void testEntrySet_entrySetValue() {
        var map = new WeakHashMap<String, String>();

        map.put("key", "value");

        var entry = map.entrySet().iterator().next();

        entry.setValue("new value");

        assertEquals("new value", map.get("key"));
    }

    public void testEntrySet_entryEquals() {
        var map = new WeakHashMap<String, String>();

        map.put("key", "value");

        var entry = map.entrySet().iterator().next();

        assertNotEquals(entry, new Object());
        assertNotEquals(entry, Map.entry("key", "another value"));
    }

    public void testKeySet_remove() {
        var map = new WeakHashMap<String, String>();

        map.put("key", "value");
        var keys = map.keySet();

        assertFalse(keys.remove(new Object()));
    }

    public void testKeySet_clear() {
        var map = new WeakHashMap<String, String>();

        map.put("key", "value");
        map.keySet().clear();

        assertTrue(map.isEmpty());
    }

    public void testValues_clear() {
        var map = new WeakHashMap<String, String>();

        map.put("key", "value");
        map.values().clear();

        assertTrue(map.isEmpty());
    }

    public void test_putAll() throws Throwable {
        var map = new WeakHashMap<String, String>();

        int threshold = threshold(map);
        var anotherMap = new HashMap<String, String>();

        for (int i = 0; i < 2 * threshold; ++i) {
            anotherMap.put(String.valueOf(i), "value");
        }

        // This should trigger resize.
        map.putAll(anotherMap);

        assertEquals(anotherMap, map);
    }

    private int threshold(WeakHashMap map) throws Exception {
        Field threshold = map.getClass().getDeclaredField("threshold");
        threshold.setAccessible(true);
        return (int) threshold.get(map);
    }

    private void initializeData() {
        for (int i = 0; i < data.length; i++) {
            data[i] = new Data();
            data[i].value = Integer.toString(i);
        }
    }

    private static class Data {
        public String value = "";
    }
}
