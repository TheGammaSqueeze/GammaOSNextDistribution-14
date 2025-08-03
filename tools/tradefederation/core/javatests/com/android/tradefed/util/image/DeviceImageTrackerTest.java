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
package com.android.tradefed.util.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.image.DeviceImageTracker.FileCacheTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link DeviceImageTracker}. */
@RunWith(JUnit4.class)
public class DeviceImageTrackerTest {

    private DeviceImageTracker mTestableCache;

    @Before
    public void setUp() {
        mTestableCache = new DeviceImageTracker();
    }

    @After
    public void tearDown() {
        mTestableCache.cleanUp();
    }

    @Test
    public void testCache() throws Exception {
        assertNull(mTestableCache.getBaselineDeviceImage("not_cached"));
        // No lingering state after a query
        assertNull(mTestableCache.getBaselineDeviceImage("not_cached"));

        File deviceImage = FileUtil.createTempFile("cache-image", ".zip");
        FileUtil.writeToFile("content", deviceImage);
        File bootloader = FileUtil.createTempFile("cache-bootloader", ".zip");
        File baseband = FileUtil.createTempFile("cache-baseband", ".zip");
        try {
            mTestableCache.trackUpdatedDeviceImage(
                    "serial", deviceImage, bootloader, baseband, "8888", "branch", "flavor");
            FileCacheTracker tracker = mTestableCache.getBaselineDeviceImage("serial");
            assertNotNull(tracker);
            assertEquals("8888", tracker.buildId);
            assertEquals("content", FileUtil.readStringFromFile(tracker.zippedDeviceImage));
        } finally {
            FileUtil.deleteFile(deviceImage);
            FileUtil.deleteFile(bootloader);
            FileUtil.deleteFile(baseband);
        }
    }
}
