/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.ondevicepersonalization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class BooleanFileDataStoreTest {
    private static final Context APPLICATION_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String FILENAME = "BooleanFileDatastoreTest";
    private static final String TEST_KEY = "key";
    private static final int TEST_KEY_COUNT = 10;

    private BooleanFileDataStore mDataStore;

    @Before
    public void setup() throws IOException {
        mDataStore = new BooleanFileDataStore(
                        APPLICATION_CONTEXT.getFilesDir().getAbsolutePath(), FILENAME);
        mDataStore.initialize();
    }

    @Test
    public void testInitializeEmptyBooleanFileDatastore() {
        assertTrue(mDataStore.keySet().isEmpty());
    }

    @Test
    public void testNullOrEmptyKeyFails() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mDataStore.put(null, true);
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mDataStore.put("", true);
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    mDataStore.get(null);
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mDataStore.get("");
                });
    }

    @Test
    public void testPutGetUpdate() throws IOException {
        // Empty
        assertNull(mDataStore.get(TEST_KEY));

        // Put
        mDataStore.put(TEST_KEY, false);

        // Get
        Boolean readValue = mDataStore.get(TEST_KEY);
        assertEquals(false, readValue);

        // Update
        mDataStore.put(TEST_KEY, true);
        readValue = mDataStore.get(TEST_KEY);
        assertEquals(true, readValue);

        // Test overwrite
        Set<String> keys = mDataStore.keySet();
        assertEquals(keys.size(), 1);
        assertTrue(keys.contains(TEST_KEY));
    }

    @Test
    public void testClearAll() throws IOException {
        for (int i = 0; i < TEST_KEY_COUNT; ++i) {
            mDataStore.put(TEST_KEY + i, true);
        }
        assertEquals(TEST_KEY_COUNT, mDataStore.keySet().size());
        mDataStore.clear();
        mDataStore.initialize();
        assertTrue(mDataStore.keySet().isEmpty());
    }

    @Test
    public void testReinitializeFromDisk() throws IOException {
        for (int i = 0; i < TEST_KEY_COUNT; ++i) {
            mDataStore.put(TEST_KEY + i, true);
        }
        assertEquals(TEST_KEY_COUNT, mDataStore.keySet().size());

        // Mock memory crash
        mDataStore.clearLocalMapForTesting();
        assertTrue(mDataStore.keySet().isEmpty());

        // Re-initialize from the file and still be able to recover
        mDataStore.initialize();
        assertEquals(TEST_KEY_COUNT, mDataStore.keySet().size());
        for (int i = 0; i < TEST_KEY_COUNT; ++i) {
            Boolean readValue = mDataStore.get(TEST_KEY + i);
            assertEquals(true, readValue);
        }
    }

    @After
    public void tearDown() {
        mDataStore.tearDownForTesting();
    }
}
