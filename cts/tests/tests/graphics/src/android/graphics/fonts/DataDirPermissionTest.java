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

package android.graphics.fonts;

import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.S_IRWXU;

import static com.google.common.truth.Truth.assertThat;

import android.system.ErrnoException;
import android.system.Os;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DataDirPermissionTest {
    private static final String FONT_DATA_DIR = "/data/fonts";
    private static final String FONT_DATA_FILES_DIR = "/data/fonts/files";

    @Test
    public void testDataFontDirPermission() {
        File file = new File(FONT_DATA_DIR);
        assertThat(file.isDirectory()).isTrue();
        assertThat(file.canRead()).isFalse();
        assertThat(file.canWrite()).isFalse();
    }

    @Test(expected = ErrnoException.class)
    public void testDataFontDirNotOpenableForWrite() throws Exception {
        Os.open(FONT_DATA_DIR + "/test.txt", O_CREAT | O_RDWR, S_IRWXU);
    }

    @Test
    public void testDataFontFilesDirPermission() {
        File file = new File(FONT_DATA_FILES_DIR);
        assertThat(file.isDirectory()).isTrue();
        assertThat(file.canRead()).isFalse();
        assertThat(file.canWrite()).isFalse();
    }

    @Test(expected = ErrnoException.class)
    public void testDataFontFilesDirNotOpenableForWrite() throws Exception {
        Os.open(FONT_DATA_FILES_DIR + "/test.txt", O_CREAT | O_RDWR, S_IRWXU);
    }
}
