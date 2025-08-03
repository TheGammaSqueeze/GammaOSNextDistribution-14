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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.FileUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class FileUtilUnitTest {

    private String mTestDir;

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTestDir = context.getDir("test_dir", Context.MODE_PRIVATE).getPath();
    }

    @After
    public void teardown() throws Exception {
        FileUtils.deleteContents(new File(mTestDir));
    }

    @Test
    public void testGetStorageInformationForPaths_noFiles() {
        assertThat(FileUtil.getStorageInKbForPaths(Arrays.asList(mTestDir))).isEqualTo(0);
    }

    @Test
    public void testGetStorageInformationForPaths_withFiles() throws IOException {
        String filePath = mTestDir + "/data/0/misc_de/foo";
        List<String> files = Arrays.asList("test1.txt", "test2.txt");
        Files.createDirectories(Paths.get(filePath));

        for (int i = 0; i < 2; i++) {
            createFile(filePath, files.get(i), /*sizeInMb=*/ 1);
        }

        assertThat(FileUtil.getStorageInKbForPaths(Arrays.asList(filePath))).isEqualTo(2048);
    }

    private void createFile(String path, String filename, int sizeInMb) throws IOException {
        final Path filePath = Paths.get(path, filename);

        Files.createFile(filePath);
        final byte[] buffer = new byte[sizeInMb * 1024 * 1024];
        Files.write(filePath, buffer);
    }
}
