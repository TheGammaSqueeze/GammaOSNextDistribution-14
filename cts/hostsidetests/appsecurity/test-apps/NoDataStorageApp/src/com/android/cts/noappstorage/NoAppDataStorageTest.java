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

package com.android.cts.noappstorage;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Environment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Tests that exercise behaviour of an app without access to apps' data storage.
 */
// TODO(b/211761016): add tests for external storage.
@RunWith(JUnit4.class)
public class NoAppDataStorageTest {

    private final Context mCeContext = getInstrumentation().getContext();
    private final Context mDeContext = mCeContext.createDeviceProtectedStorageContext();

    @Test
    public void testNoInternalCeStorage() throws Exception {
        assertDirDoesNotExist(mCeContext.getDataDir());
        assertDirDoesNotExist(mCeContext.getFilesDir());
        assertDirDoesNotExist(mCeContext.getCacheDir());
        assertDirDoesNotExist(mCeContext.getCodeCacheDir());
    }

    @Test
    public void testNoInternalDeStorage() throws Exception {
        assertDirDoesNotExist(mDeContext.getDataDir());
        assertDirDoesNotExist(mDeContext.getFilesDir());
        assertDirDoesNotExist(mDeContext.getCacheDir());
        assertDirDoesNotExist(mDeContext.getCodeCacheDir());
    }

    @Test
    public void testNoExternalStorage() throws Exception {
        final String[] types = new String[] {
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                Environment.DIRECTORY_ALARMS,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_DOCUMENTS
        };
        for (String type : types) {
            File dir = mCeContext.getExternalFilesDir(type);
            assertThat(dir).isNull();
        }
    }

    private void assertDirDoesNotExist(File dir) throws Exception {
        assertThat(dir.exists()).isFalse();
        assertThat(dir.mkdirs()).isFalse();
    }
}
