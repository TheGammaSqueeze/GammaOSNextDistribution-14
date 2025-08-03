/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.i18n.test.timezone.internal;

import android.icu.testsharding.MainTestShard;
import com.android.i18n.timezone.internal.BasicLruCache;
import junit.framework.TestCase;

@MainTestShard
public final class BasicLruCacheTest extends TestCase {

    public void testCreateOnCacheMiss() {
        BasicLruCache<String, String> cache = newCreatingCache();
        String created = cache.get("aa");
        assertEquals("created-aa", created);
    }

    public void testNoCreateOnCacheHit() {
        BasicLruCache<String, String> cache = newCreatingCache();
        cache.put("aa", "put-aa");
        assertEquals("put-aa", cache.get("aa"));
    }

    public void testConstructorDoesNotAllowZeroCacheSize() {
        try {
            new BasicLruCache<String, String>(0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testCannotPutNullKey() {
        BasicLruCache<String, String> cache = new BasicLruCache<String, String>(3);
        try {
            cache.put(null, "A");
            fail();
        } catch (NullPointerException expected) {
        }
    }

    public void testCannotPutNullValue() {
        BasicLruCache<String, String> cache = new BasicLruCache<String, String>(3);
        try {
            cache.put("a", null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    public void testEvictionWithSingletonCache() {
        BasicLruCache<String, String> cache = new BasicLruCache<String, String>(1);
        cache.put("a", "A");
        cache.put("b", "B");
    }


    private BasicLruCache<String, String> newCreatingCache() {
        return new BasicLruCache<String, String>(3) {
            @Override protected String create(String key) {
                return (key.length() > 1) ? ("created-" + key) : null;
            }
        };
    }
}
