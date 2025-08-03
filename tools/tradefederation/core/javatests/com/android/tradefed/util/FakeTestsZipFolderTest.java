/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tradefed.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.android.tradefed.util.FakeTestsZipFolder.ItemType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Test {@link FakeTestsZipFolder} */
@RunWith(JUnit4.class)
public class FakeTestsZipFolderTest {

    private Map<String, ItemType> mFiles;

    @Before
    public void setUp() throws Exception {

        mFiles = new HashMap<String, ItemType>();
        mFiles.put("app/AndroidCommonTests.apk", ItemType.FILE);
        mFiles.put("app/GalleryTests.apk", ItemType.FILE);
        mFiles.put("testinfo", ItemType.DIRECTORY);
    }

    @Test
    public void testFakeTestsZipFolder() throws IOException {
        FakeTestsZipFolder fakeStuff = new FakeTestsZipFolder(mFiles);
        assertTrue(fakeStuff.createItems());
        // verify items
        File base = fakeStuff.getBasePath();
        // covering bases
        assertTrue(base.exists() && base.isDirectory());
        File data = fakeStuff.getDataFolder();
        for (String fileName : mFiles.keySet()) {
            File file = new File(data, fileName);
            ItemType type = mFiles.get(fileName);
            switch (type) {
                case FILE:
                    assertTrue(file.isFile());
                    break;
                case DIRECTORY:
                    assertTrue(file.isDirectory());
                    break;
                default:
                    fail(String.format("Unexpected file type: %s", type.toString()));
            }
        }
        fakeStuff.cleanUp();
        assertFalse(base.exists());
    }
}
