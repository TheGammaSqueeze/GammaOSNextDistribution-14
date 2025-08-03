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

package com.android.cts.storagestatsapp;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.android.cts.storageapp.Utils.MB_IN_BYTES;
import static com.android.cts.storageapp.Utils.assertMostlyEquals;
import static com.android.cts.storageapp.Utils.useWrite;

import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Environment;
import android.os.UserHandle;
import android.provider.MediaStore;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

@RunWith(JUnit4.class)
public class StorageStatsTest{

    private Context mContext;
    private StorageStatsManager mStorageStatsManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mStorageStatsManager = mContext.getSystemService(StorageStatsManager.class);
    }

    /**
     * Create some external files of specific media types and ensure that
     * they're tracked correctly.
     */
    @Test
    public void testVerifyStatsExternal() throws Exception {
        final int uid = android.os.Process.myUid();
        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        final ExternalStorageStats before = mStorageStatsManager
                .queryExternalStatsForUser(UUID_DEFAULT, user);

        final File dir = Environment.getExternalStorageDirectory();
        final File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        downloadsDir.mkdirs();

        final File image = new File(dir, System.nanoTime() + ".jpg");
        final File video = new File(downloadsDir, System.nanoTime() + ".MP4");
        final File audio = new File(dir, System.nanoTime() + ".png.WaV");
        final File internal = new File(
                mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "test.jpg");

        useWrite(image, 2 * MB_IN_BYTES);
        useWrite(video, 3 * MB_IN_BYTES);
        useWrite(audio, 5 * MB_IN_BYTES);
        useWrite(internal, 7 * MB_IN_BYTES);

        // Wait for MediaProvider to finish quota assignment
        MediaStore.waitForIdle(mContext.getContentResolver());

        ExternalStorageStats afterInit = mStorageStatsManager
                .queryExternalStatsForUser(UUID_DEFAULT, user);

        assertMostlyEquals(17 * MB_IN_BYTES, afterInit.getTotalBytes() - before.getTotalBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterInit.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(3 * MB_IN_BYTES, afterInit.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(2 * MB_IN_BYTES, afterInit.getImageBytes() - before.getImageBytes());
        assertMostlyEquals(7 * MB_IN_BYTES, afterInit.getAppBytes() - before.getAppBytes());

        // Rename to ensure that stats are updated
        video.renameTo(new File(dir, System.nanoTime() + ".PnG"));

        // Since we have MANAGE_EXTERNAL_STORAGE, need to ask for a re-scan
        MediaStore.scanFile(mContext.getContentResolver(), dir);
        MediaStore.scanFile(mContext.getContentResolver(), downloadsDir);
        MediaStore.waitForIdle(mContext.getContentResolver());

        final ExternalStorageStats afterRename = mStorageStatsManager
                .queryExternalStatsForUser(UUID_DEFAULT, user);

        assertMostlyEquals(17 * MB_IN_BYTES, afterRename.getTotalBytes() - before.getTotalBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterRename.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(0 * MB_IN_BYTES, afterRename.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterRename.getImageBytes() - before.getImageBytes());
        assertMostlyEquals(7 * MB_IN_BYTES, afterRename.getAppBytes() - before.getAppBytes());
    }
}
