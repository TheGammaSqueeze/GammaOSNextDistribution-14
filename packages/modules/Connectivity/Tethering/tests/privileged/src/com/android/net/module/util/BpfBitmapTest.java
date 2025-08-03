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

package com.android.networkstack.tethering;

import static com.android.networkstack.tethering.util.TetheringUtils.getTetheringJniLibraryName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;

import com.android.net.module.util.BpfBitmap;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(DevSdkIgnoreRunner.class)
public final class BpfBitmapTest {
    private static final String TEST_BITMAP_PATH =
            "/sys/fs/bpf/tethering/map_test_bitmap";

    private static final int mTestData[] = {0,1,2,6,63,64,72};
    private BpfBitmap mTestBitmap;

    @Before
    public void setUp() throws Exception {
        mTestBitmap = new BpfBitmap(TEST_BITMAP_PATH);
        mTestBitmap.clear();
        assertTrue(mTestBitmap.isEmpty());
    }

    @Test
    public void testSet() throws Exception {
        for (int i : mTestData) {
            mTestBitmap.set(i);
            assertFalse(mTestBitmap.isEmpty());
            assertTrue(mTestBitmap.get(i));
            // Check that the next item in the bitmap is unset since test data is in
            // ascending order.
            assertFalse(mTestBitmap.get(i + 1));
        }
    }

    @Test
    public void testSetThenUnset() throws Exception {
        for (int i : mTestData) {
            mTestBitmap.set(i);
            assertFalse(mTestBitmap.isEmpty());
            assertTrue(mTestBitmap.get(i));
            // Since test unsets all test data during each iteration, ensure all other
            // bit are unset.
            for (int j = 0; j < 128; ++j) if (j != i) assertFalse(mTestBitmap.get(j));
            mTestBitmap.unset(i);
        }
    }

    @Test
    public void testSetAllThenUnsetAll() throws Exception {
        for (int i : mTestData) {
            mTestBitmap.set(i);
        }

        for (int i : mTestData) {
            mTestBitmap.unset(i);
            if (i < mTestData.length)
                assertFalse(mTestBitmap.isEmpty());
            assertFalse(mTestBitmap.get(i));
        }
        assertTrue(mTestBitmap.isEmpty());
    }

    @Test
    public void testClear() throws Exception {
        for (int i = 0; i < 128; ++i) {
            mTestBitmap.set(i);
        }
        assertFalse(mTestBitmap.isEmpty());
        mTestBitmap.clear();
        assertTrue(mTestBitmap.isEmpty());
    }
}
