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

package com.android.federatedcompute.services.common;

import android.os.ParcelFileDescriptor;

import com.android.federatedcompute.internal.util.LogUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Utils related to {@link File} and {@link ParcelFileDescriptor}. */
public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();

    private static final int BUFFER_SIZE = 1024;

    /** Create {@link ParcelFileDescriptor} based on the input file. */
    public static ParcelFileDescriptor createTempFileDescriptor(String fileName, int mode) {
        ParcelFileDescriptor fileDescriptor;
        try {
            fileDescriptor = ParcelFileDescriptor.open(new File(fileName), mode);
        } catch (IOException e) {
            LogUtil.e(TAG, e, "Failed to createTempFileDescriptor %s", fileName);
            throw new RuntimeException(e);
        }
        return fileDescriptor;
    }

    /** Create a temporary file based on provided name and extension. */
    public static String createTempFile(String name, String extension) {
        String fileName;
        try {
            File tempFile = File.createTempFile(name, extension);
            fileName = tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileName;
    }

    /** Write the provided data to the file. */
    public static void writeToFile(String fileName, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(fileName);
        out.write(data);
        out.close();
    }

    /** Read the input file content to a byte array. */
    public static byte[] readFileAsByteArray(String filePath) throws IOException {
        File file = new File(filePath);
        long fileLength = file.length();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream((int) fileLength);
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int len = inputStream.read(buffer); len > 0; len = inputStream.read(buffer)) {
                outputStream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            LogUtil.e(TAG, e, "Failed to read the content of binary file %s", filePath);
            throw e;
        }
        return outputStream.toByteArray();
    }

    /** Read the content from a file descriptor to a byte array. */
    public static byte[] readFileDescriptorAsByteArray(ParcelFileDescriptor fd) {
        InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(fd);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
        } catch (IOException e) {
            LogUtil.e(TAG, e, "Failed to read the content of binary file %d", fd.getFd());
        }
        return outputStream.toByteArray();
    }
}
