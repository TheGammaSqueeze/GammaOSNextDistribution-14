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

package com.android.server.net;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.AtomicFile;
import android.util.SystemConfigFileCommitEventLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A simple integer backed by an on-disk {@link AtomicFile}. Not thread-safe.
 */
public class PersistentInt {
    private final String mPath;
    private final AtomicFile mFile;

    /**
     * Constructs a new {@code PersistentInt}. The counter is set to 0 if the file does not exist.
     * Before returning, the constructor checks that the file is readable and writable. This
     * indicates that in the future {@link #get} and {@link #set} are likely to succeed,
     * though other events (data corruption, other code deleting the file, etc.) may cause these
     * calls to fail in the future.
     *
     * @param path the path of the file to use.
     * @param logger the logger
     * @throws IOException the counter could not be read or written
     */
    public PersistentInt(@NonNull String path, @Nullable SystemConfigFileCommitEventLogger logger)
            throws IOException {
        mPath = path;
        mFile = new AtomicFile(new File(path), logger);
        checkReadWrite();
    }

    private void checkReadWrite() throws IOException {
        int value;
        try {
            value = get();
        } catch (FileNotFoundException e) {
            // Counter does not exist. Attempt to initialize to 0.
            // Note that we cannot tell here if the file does not exist or if opening it failed,
            // because in Java both of those throw FileNotFoundException.
            value = 0;
        }
        set(value);
        get();
        // No exceptions? Good.
    }

    /**
      * Gets the current value.
      *
      * @return the current value of the counter.
      * @throws IOException if reading the value failed.
      */
    public int get() throws IOException {
        try (FileInputStream fin = mFile.openRead();
             DataInputStream din = new DataInputStream(fin)) {
            return din.readInt();
        }
    }

    /**
     * Sets the current value.
     * @param value the value to set
     * @throws IOException if writing the value failed.
     */
    public void set(int value) throws IOException {
        FileOutputStream fout = null;
        try {
            fout = mFile.startWrite();
            DataOutputStream dout = new DataOutputStream(fout);
            dout.writeInt(value);
            mFile.finishWrite(fout);
        } catch (IOException e) {
            if (fout != null) {
                mFile.failWrite(fout);
            }
            throw e;
        }
    }

    public String getPath() {
        return mPath;
    }
}
