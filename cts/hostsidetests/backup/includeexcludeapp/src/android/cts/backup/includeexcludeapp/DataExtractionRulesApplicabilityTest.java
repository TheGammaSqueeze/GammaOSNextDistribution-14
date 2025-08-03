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
 * limitations under the License
 */

package android.cts.backup.includeexcludeapp;

import static androidx.test.InstrumentationRegistry.getTargetContext;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Device side routines to be invoked by the host side FullbackupRulesHostSideTest. These are not
 * designed to be called in any other way, as they rely on state set up by the host side test.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class DataExtractionRulesApplicabilityTest {
    private static final String BACKUP_EXCLUDE_FILE = "backup_exclude";
    private static final String TRANSFER_EXCLUDE_FILE = "transfer_exclude";
    private static final String FULL_BACKUP_CONTENT_EXCLUDE_FILE = "fbc_exclude";

    private static final String[] TEST_FILES = new String[] {
            BACKUP_EXCLUDE_FILE,
            TRANSFER_EXCLUDE_FILE,
            FULL_BACKUP_CONTENT_EXCLUDE_FILE
    };

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = getTargetContext();
    }

    @Test
    public void createFiles() throws IOException {
        for (String fileName : TEST_FILES) {
            File testFile = new File(mContext.getFilesDir(), fileName);
            // Make sure the file is non-empty, otherwise the transport will ignore it.
            try (FileWriter fileWriter = new FileWriter(testFile)) {
                fileWriter.write("test");
                fileWriter.flush();
            }
        }
    }

    @Test
    public void deleteFilesAfterBackup() throws IOException {
        for (String fileName : TEST_FILES) {
            new File(fileName).delete();
        }
    }

    @Test
    public void testOnlyBackupDataExtractionRulesAreApplied() {
        ensureOnlyGivenFileIsExcluded(BACKUP_EXCLUDE_FILE);
    }

    @Test
    public void testOnlyDeviceTransferDataExtractionRulesAreApplied() {
        ensureOnlyGivenFileIsExcluded(TRANSFER_EXCLUDE_FILE);
    }

    private void ensureOnlyGivenFileIsExcluded(String excludedFile) {
        for (String fileName : TEST_FILES) {
            File file = new File(mContext.getFilesDir(), fileName);
            if (fileName.equals(excludedFile)) {
                assertThat(file.exists()).isFalse();
            } else {
                assertThat(file.exists()).isTrue();
            }
        }
    }
}
