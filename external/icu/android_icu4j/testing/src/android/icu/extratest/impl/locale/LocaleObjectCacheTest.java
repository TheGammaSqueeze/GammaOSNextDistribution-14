/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.icu.extratest.impl.locale;

import android.icu.impl.locale.BaseLocale;
import android.icu.impl.locale.LocaleObjectCache;
import android.icu.testsharding.MainTestShard;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@MainTestShard
@RunWith(JUnit4.class)
public class LocaleObjectCacheTest {

    /**
     * Test that {@link dalvik.system.ZygoteHooks#gcAndFinalize()} invokes
     * {@link LocaleObjectCache#cleanStaleEntries()}, i.e.  when referent is GC-ed, the
     * {@link LocaleObjectCache.CacheEntry} object, aka {@link SoftReference} object, should
     * be cleared from the {@link LocaleObjectCache#_queue} and removed itself from
     * {@link LocaleObjectCache#_map}.
     *
     * This test uses reflection to access the fields and methods in ICU to avoid patches and
     * unnecessary extra test methods in the production code.
     */
    @Test
    public void testGcAndFinalize_verifyClearReferenceQueue() throws Exception {
        invokeGcAndFinalize();
        assertEquals(0, getBaseLocaleCountAndClear());
        // If this assertion assertNotFoundByKey() fails, please update the values of
        // LANG and REGION to other BaseLocale that don't exist in the cache / any other tests.
        assertNotFoundByKey();

        createLocaleAndAssertCache();
        // insertKeyIntoQueue() pretends to be the garbage collector and inserts a stale CacheEntry
        // into the reference queue. GC doesn't have to collect softly-referenced objects, and thus
        // we artificially set up this scenario to test the gcAndFinalized() call.
        // For this test, we only care if the LocaleObjectCache.cleanStaleEntries() is invoked
        // by gcAndFinalized(), and verify the expected outcome. Whether the GC collects softly-
        // referenced objects is not part of the scope of this test.
        insertKeyIntoQueue();

        invokeGcAndFinalize();
        assertEquals(0, getBaseLocaleCountAndClear());
        assertNotFoundByKey();
    }

    private static void invokeGcAndFinalize() throws ReflectiveOperationException {
        // dalvik.system.ZygoteHooks#gcAndFinalize() is a stable API, but we don't compile ICU4J
        // against @SystemAPI stub today.
        Class clazz = Class.forName("dalvik.system.ZygoteHooks");
        Method m = clazz.getMethod("gcAndFinalize");
        m.invoke(null);
    }


    private static final String LANG = "zyx";
    // "ZY" is not a known region in ISO 3166-1 yet.
    private static final String REGION = "ZY";

    private static void createLocaleAndAssertCache() throws ReflectiveOperationException {
        assertNotFoundByKey();

        BaseLocale baseLocale = BaseLocale.getInstance(LANG, null, REGION, null);

        SoftReference value = getFromCache();
        assertNotNull(value);
        assertNotNull(value.get());

        // Keep the reference to locale for a while. Just use the reference by checking the lang.
        assertEquals(LANG, baseLocale.getLanguage());
    }

    private static void insertKeyIntoQueue() throws ReflectiveOperationException {
        Object key = createKey();
        ReferenceQueue<SoftReference> queue = getQueue();
        Class entryClass = Class.forName(LocaleObjectCache.class.getName() + "$CacheEntry");
        Constructor<SoftReference> constructor = entryClass.getDeclaredConstructor(
                Object.class, Object.class, ReferenceQueue.class);
        constructor.setAccessible(true);
        SoftReference entry = constructor.newInstance(key, /* referent= */null, queue);
        entry.enqueue();
    }

    private static void assertNotFoundByKey() throws ReflectiveOperationException {
        SoftReference value = getFromCache();
        // Before checking null value and failing the test, it's good to know that
        // the referent is null.
        if (value != null) {
            assertNull(value.get());
        }
        assertNull(value);
    }

    private static SoftReference getFromCache() throws ReflectiveOperationException {
        LocaleObjectCache cache = getLocaleObjectCache();
        Field mapField = LocaleObjectCache.class.getDeclaredField("_map");
        mapField.setAccessible(true);
        ConcurrentMap<Object, SoftReference> map = (ConcurrentMap) mapField.get(cache);
        Object key = createKey();
        return map.get(key);
    }

    private static Object createKey() throws ReflectiveOperationException {
        Class keyClass = Class.forName(BaseLocale.class.getName() + "$Key");
        Constructor constructor = keyClass.getDeclaredConstructor(String.class, String.class,
                String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(LANG, "", REGION, "");
    }

    private static int getBaseLocaleCountAndClear() throws ReflectiveOperationException {
        ReferenceQueue queue = getQueue();
        int count = 0;
        while(queue.poll() != null) {
            count++;
        }
        return count;
    }

    private static ReferenceQueue<SoftReference> getQueue() throws ReflectiveOperationException {
        LocaleObjectCache cache = getLocaleObjectCache();
        Field queueField = LocaleObjectCache.class.getDeclaredField("_queue");
        queueField.setAccessible(true);
        return (ReferenceQueue) queueField.get(cache);
    }

    private static LocaleObjectCache getLocaleObjectCache() throws ReflectiveOperationException {
        Field f = BaseLocale.class.getDeclaredField("CACHE");
        f.setAccessible(true);
        return (LocaleObjectCache) f.get(null);
    }

}
