/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SystemFontsUniqueNameTest {

    @Test
    public void uniquePostScript() throws IOException {
        Set<String> set = new HashSet<>();
        Set<File> seenFile = new HashSet<>();

        for (Font font : SystemFonts.getAvailableFonts()) {
            if (seenFile.contains(font.getFile())) {
                continue;
            }
            String psName = FontFileTestUtil.getPostScriptName(font.getFile());
            assertThat(psName).isNotNull();
            assertThat(set).doesNotContain(psName);

            seenFile.add(font.getFile());
            set.add(psName);
        }
    }
}
