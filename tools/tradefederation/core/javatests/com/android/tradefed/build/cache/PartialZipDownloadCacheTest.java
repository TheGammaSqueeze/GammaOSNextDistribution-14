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
package com.android.tradefed.build.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link PartialZipDownloadCache}. */
@RunWith(JUnit4.class)
public class PartialZipDownloadCacheTest {

    private PartialZipDownloadCache mCache;

    @Before
    public void setUp() {
        mCache = new PartialZipDownloadCache();
    }

    @After
    public void tearDown() {
        mCache.cleanUpCache();
    }

    @Test
    public void testcache() throws Exception {
        File parentDir = FileUtil.createTempDir("cache_unit_tests");
        File toCache = FileUtil.createTempFile("test-name", ".config", parentDir);
        FileUtil.writeToFile("<configuration></configuration>", toCache);
        String crc = Long.toString(FileUtil.calculateCrc32(toCache));
        File targetFile = FileUtil.createTempFile("test-name", ".config");
        targetFile.delete();

        boolean success =
                mCache.getCachedFile(targetFile, parentDir.getName() + "/test-name.config", crc);
        assertFalse(success); // Initial cache empty
        mCache.populateCacheFile(toCache, parentDir.getName() + "/test-name.config", crc);
        try {
            success =
                    mCache.getCachedFile(
                            targetFile, parentDir.getName() + "/test-name.config", crc);
            assertTrue(success);
            assertEquals(
                    "<configuration></configuration>", FileUtil.readStringFromFile(targetFile));
        } finally {
            FileUtil.deleteFile(toCache);
            FileUtil.deleteFile(targetFile);
            FileUtil.recursiveDelete(parentDir);
        }
    }
}
