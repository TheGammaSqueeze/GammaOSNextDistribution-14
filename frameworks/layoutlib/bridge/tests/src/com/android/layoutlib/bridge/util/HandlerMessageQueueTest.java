/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.layoutlib.bridge.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import android.os.Handler;
import android.os.Looper;
import android.os.Looper_Accessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HandlerMessageQueueTest {

    @Before
    public void setUp() {
        Looper.prepareMainLooper();
    }

    @Test
    public void testSingleHandler_SingleRunnable() {
        HandlerMessageQueue q = new HandlerMessageQueue();
        Handler h1 = new Handler();
        Runnable r1 = () -> {};

        assertFalse(q.isNotEmpty());

        q.add(h1, 100, r1);

        assertTrue(q.isNotEmpty());
        assertNull(q.extractFirst(0));
        assertEquals(q.extractFirst(100), r1);
        assertFalse(q.isNotEmpty());
        assertNull(q.extractFirst(100));
    }

    @Test
    public void testSingleHandler_MultipleRunnables() {
        HandlerMessageQueue q = new HandlerMessageQueue();
        Handler h1 = new Handler();
        Runnable r1 = () -> {};
        Runnable r2 = () -> {};
        Runnable r3 = () -> {};

        q.add(h1, 100, r1);
        q.add(h1, 100, r2);
        q.add(h1, 50, r3);

        assertEquals(q.extractFirst(100), r3);
        assertEquals(q.extractFirst(100), r1);
        assertTrue(q.isNotEmpty());
        assertEquals(q.extractFirst(100), r2);
        assertFalse(q.isNotEmpty());
    }

    @Test
    public void testMultipleHandlers() {
        HandlerMessageQueue q = new HandlerMessageQueue();
        Handler h1 = new Handler();
        Handler h2 = new Handler();
        Runnable r1 = () -> {};
        Runnable r2 = () -> {};
        Runnable r3 = () -> {};

        q.add(h1, 200, r1);
        q.add(h2, 100, r2);
        q.add(h1, 50, r3);

        assertEquals(q.extractFirst(70), r3);
        assertEquals(q.extractFirst(300), r2);
        assertEquals(q.extractFirst(300), r1);
        assertFalse(q.isNotEmpty());
        assertNull(q.extractFirst(500));

        q.add(h1, 400, r1);

        assertTrue(q.isNotEmpty());
        assertEquals(q.extractFirst(500), r1);
        assertFalse(q.isNotEmpty());
    }

    @Test
    public void testMultipleHandlers_Clear() {
        HandlerMessageQueue q = new HandlerMessageQueue();
        Handler h1 = new Handler();
        Handler h2 = new Handler();
        Runnable r1 = () -> {};
        Runnable r2 = () -> {};
        Runnable r3 = () -> {};

        q.add(h1, 200, r1);
        q.add(h2, 100, r2);
        q.add(h1, 50, r3);

        q.clear();

        assertFalse(q.isNotEmpty());
        assertNull(q.extractFirst(200));
    }

    @After
    public void tearDown() {
        Looper_Accessor.cleanupThread();
    }
}
