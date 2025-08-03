/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.deletekeepdata.app;

import static android.content.Context.MODE_PRIVATE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class DeleteKeepDataDeviceTest {
    private static final String TAG = DeleteKeepDataDeviceTest.class.getName();
    private static final String TEST_FILE_NAME = "my_test_file";
    private static final String TEST_FILE_CONTENT = "testing";
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void testWriteData() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                mContext.openFileOutput(TEST_FILE_NAME, MODE_PRIVATE))) {
            // Write to /data/user/[user_id]/[package_name]/files/
            writer.write(TEST_FILE_CONTENT);
            final File internalFile = new File(mContext.getFilesDir(), TEST_FILE_NAME);
            assertTrue(internalFile.exists());
            Log.i(TAG, "Wrote file to " + internalFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write internal data", e);
        }
        // Verify that the file content was written successfully
        assertEquals(TEST_FILE_CONTENT, readDataUserFile());
        // Write to external storage
        final File externalFile = new File(mContext.getExternalFilesDir(null), TEST_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(externalFile)) {
            fos.write(TEST_FILE_CONTENT.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write external data", e);
        }
        // Verify that the file content was written successfully
        assertEquals(TEST_FILE_CONTENT, readExternalDataFile());
    }

    @Test
    public void testReadData() {
        assertEquals(TEST_FILE_CONTENT, readDataUserFile());
        assertEquals(TEST_FILE_CONTENT, readExternalDataFile());
    }

    @Test
    public void testReadDataFail() {
        assertNull(readDataUserFile());
        assertNull(readExternalDataFile());
    }

    private String readDataUserFile() {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        try (InputStream is = mContext.openFileInput(TEST_FILE_NAME)) {
            int len;
            while ((len = is.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read internal data", e);
            return null;
        }
        return byteBuffer.toString(StandardCharsets.UTF_8);
    }

    private String readExternalDataFile() {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(
                new File(mContext.getExternalFilesDir(null), TEST_FILE_NAME))) {
            int len;
            while ((len = fis.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read external data", e);
            return null;
        }
        return byteBuffer.toString(StandardCharsets.UTF_8);
    }
}
