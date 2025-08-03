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

import com.android.ide.common.rendering.api.ILayoutLog;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.Choreographer.FrameCallback;

import java.util.ArrayList;

public class ChoreographerCallbacksTest {

    private static class ValidatingLogger implements ILayoutLog {
        @Override
        public void error(@Nullable String tag, @NonNull String message,
                @Nullable Object viewCookie, @Nullable Object data) {
            errorMessages.add(message);
        }

        @Override
        public void error(@Nullable String tag, @NonNull String message,
                @Nullable Throwable throwable, @Nullable Object viewCookie, @Nullable Object data) {
            errorMessages.add(message);
        }

        private final ArrayList<String> errorMessages = new ArrayList<>();
    }

    private final ValidatingLogger logger = new ValidatingLogger();

    @Before
    public void setUp() {
        logger.errorMessages.clear();
    }

    @Test
    public void testAddAndExecuteInOrder() {
        ChoreographerCallbacks callbacks = new ChoreographerCallbacks();
        ArrayList<Integer> order = new ArrayList<>();

        callbacks.add((Runnable) () -> order.add(2), null, 200);
        callbacks.add((FrameCallback) frameTimeNanos -> order.add(1), null, 100);
        callbacks.execute(200, logger);

        Assert.assertArrayEquals(order.toArray(), new Object[] { 1, 2 });
        Assert.assertTrue(logger.errorMessages.isEmpty());
    }

    @Test
    public void testAddAndExecuteOnlyDue() {
        ChoreographerCallbacks callbacks = new ChoreographerCallbacks();
        ArrayList<Integer> order = new ArrayList<>();

        callbacks.add((Runnable) () -> order.add(2), null, 200);
        callbacks.add((FrameCallback) frameTimeNanos -> order.add(1), null, 100);
        callbacks.execute(100, logger);

        Assert.assertArrayEquals(order.toArray(), new Object[] { 1 });
        Assert.assertTrue(logger.errorMessages.isEmpty());
    }

    @Test
    public void testRemove() {
        ChoreographerCallbacks callbacks = new ChoreographerCallbacks();
        ArrayList<Integer> order = new ArrayList<>();

        Runnable runnable = () -> order.add(2);
        callbacks.add(runnable, null, 200);
        callbacks.add((FrameCallback) frameTimeNanos -> order.add(1), null, 100);
        callbacks.remove(runnable, null);
        callbacks.execute(200, logger);

        Assert.assertArrayEquals(order.toArray(), new Object[] { 1 });
        Assert.assertTrue(logger.errorMessages.isEmpty());
    }

    @Test
    public void testErrorIfUnknownCallbackType() {
        ChoreographerCallbacks callbacks = new ChoreographerCallbacks();

        callbacks.add(new Object(), null, 100);
        callbacks.execute(200, logger);

        Assert.assertFalse(logger.errorMessages.isEmpty());
        Assert.assertEquals(logger.errorMessages.get(0), "Unexpected action as Choreographer callback");
    }

    @Test
    public void testRemoveNullAction() {
        ChoreographerCallbacks callbacks = new ChoreographerCallbacks();
        ArrayList<Integer> order = new ArrayList<>();

        Object token1 = new Object();
        Object token2 = new Object();
        callbacks.add((Runnable) () -> order.add(2), token1, 200);
        callbacks.add((FrameCallback) frameTimeNanos -> order.add(1), token1, 100);
        callbacks.add((Runnable) () -> order.add(3), token2, 100);
        callbacks.add((Runnable) () -> order.add(4), null, 200);
        callbacks.remove(null, token1);
        callbacks.execute(200, logger);

        Assert.assertArrayEquals(order.toArray(), new Object[] { 3, 4 });
        Assert.assertTrue(logger.errorMessages.isEmpty());
    }
}
