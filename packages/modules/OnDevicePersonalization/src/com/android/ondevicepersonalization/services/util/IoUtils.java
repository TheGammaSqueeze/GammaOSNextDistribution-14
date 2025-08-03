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

package com.android.ondevicepersonalization.services.util;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Contains static I/O-related utility methods. */
public class IoUtils {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = IoUtils.class.getSimpleName();

    private IoUtils() {}

    /** Get content as Bytebuffer by reading from {@link ParcelFileDescriptor}. */
    public static ByteBuffer getByteBufferFromFd(ParcelFileDescriptor fileDescriptor) {
        try {
            FileInputStream inputStream =
                    new ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor);
            FileChannel fileChannel = inputStream.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        } catch (IOException e) {
            sLogger.e(TAG + ": Failed to read file descriptor to ByteBuffer.", e);
            return null;
        }
    }

    /** Create {@link ParcelFileDescriptor} based on the input file. */
    public static ParcelFileDescriptor createFileDescriptor(String fileName, int mode) {
        ParcelFileDescriptor fileDescriptor;
        try {
            fileDescriptor = ParcelFileDescriptor.open(new File(fileName), mode);
        } catch (IOException e) {
            sLogger.e(TAG + ": Failed to createTempFileDescriptor %s", fileName);
            throw new RuntimeException(e);
        }
        return fileDescriptor;
    }

    /** Write the provided data to new created file. */
    public static String writeToFile(Context context, String fileName, byte[] data) {
        try {
            File file = new File(context.getDataDir(), fileName);
            String filePath = file.getAbsolutePath();
            FileOutputStream out = new FileOutputStream(file);
            out.write(data);
            out.close();
            return filePath;
        } catch (IOException e) {
            sLogger.e(e, TAG + e.getMessage());
            return null;
        }
    }

    /** Create a temporary file based on provided name and extension. */
    public static String writeToTempFile(String name, byte[] data) {
        try {
            File tempFile = File.createTempFile(name, "");
            String filePath = tempFile.getAbsolutePath();
            FileOutputStream out = new FileOutputStream(tempFile);
            out.write(data);
            out.close();
            return filePath;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
