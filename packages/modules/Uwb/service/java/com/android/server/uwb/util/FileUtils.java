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

package com.android.server.uwb.util;

import android.util.AtomicFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utils created for working with {@link AtomicFile}.
 */
public final class FileUtils {
    private FileUtils() {}

    /**
     * Read raw data from the atomic file.
     * Note: This is a copy of {@link AtomicFile#readFully()} modified to use the passed in
     * {@link InputStream} which was returned using {@link AtomicFile#openRead()}.
     */
    public static byte[] readFromAtomicFile(AtomicFile file) throws IOException {
        FileInputStream stream = null;
        try {
            stream = file.openRead();
            int pos = 0;
            int avail = stream.available();
            byte[] data = new byte[avail];
            while (true) {
                int amt = stream.read(data, pos, data.length - pos);
                if (amt <= 0) {
                    return data;
                }
                pos += amt;
                avail = stream.available();
                if (avail > data.length - pos) {
                    byte[] newData = new byte[pos + avail];
                    System.arraycopy(data, 0, newData, 0, pos);
                    data = newData;
                }
            }
        } finally {
            if (stream != null) stream.close();
        }
    }

    /**
     * Write the raw data to the atomic file.
     */
    public static void writeToAtomicFile(AtomicFile file, byte[] data) throws IOException {
        // Write the data to the atomic file.
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data);
            file.finishWrite(out);
        } catch (IOException e) {
            if (out != null) {
                file.failWrite(out);
            }
            throw e;
        }
    }
}
