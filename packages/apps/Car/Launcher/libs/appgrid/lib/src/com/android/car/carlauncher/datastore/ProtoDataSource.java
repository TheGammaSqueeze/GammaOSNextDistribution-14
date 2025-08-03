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

package com.android.car.carlauncher.datastore;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.protobuf.MessageLite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class level abstraction representing a proto file holding app data.
 *
 * Only a single controller should hold reference to this class. All methods that perform read or
 * write operations must be thread safe and idempotent.
 *
 * @param <T> the proto object type that this data file is holding
 */
public abstract class ProtoDataSource<T extends MessageLite> {
    private final File mFile;
    private static final String TAG = "ProtoDataSource";
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    public ProtoDataSource(File dataFileDirectory, String dataFileName) {
        mFile = new File(dataFileDirectory, dataFileName);
    }

    /**
     * @return true if the file exists on disk, and false otherwise.
     */
    public boolean exists() {
        return mFile.exists();
    }

    /**
     * Used by subclasses to access the mFile object.
     */
    protected File getDataFile() {
        return mFile;
    }

    /**
     * Writes the {@link MessageLite} subclass T to the file represented by this object.
     */
    public void writeToFile(T data) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try {
                if (mOutputStream == null) {
                    mOutputStream = new FileOutputStream(getDataFile(), false);
                }
                writeDelimitedTo(data, mOutputStream);
            } catch (IOException e) {
                Log.e(TAG, "Launcher item list not written to file successfully.");
            } finally {
                try {
                    if (mOutputStream != null) {
                        mOutputStream.flush();
                        mOutputStream.getFD().sync();
                        mOutputStream.close();
                        mOutputStream = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close output stream. ");
                }
            }
            executorService.shutdown();
        });
    }

    /**
     * Reads the {@link MessageLite} subclass T from the file represented by this object.
     */
    @Nullable
    public T readFromFile() {
        if (!exists()) {
            Log.e(TAG, "File does not exist. Cannot read from file.");
            return null;
        }
        T result = null;
        try {
            if (mInputStream == null) {
                mInputStream = new FileInputStream(getDataFile());
            }
            result = parseDelimitedFrom(mInputStream);
        } catch (IOException e) {
            Log.e(TAG, "Read from input stream not successfully");
        } finally {
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                    mInputStream = null;
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close input stream");
                }
            }
        }
        return result;
    }

    /**
     * This method will be called by {@link ProtoDataSource#readFromFile}.
     *
     * Implementation is left to subclass since {@link MessageLite.parseDelimitedFrom(InputStream)}
     * requires a defined class at compile time. Subclasses should implement this method by directly
     * calling YourMessageType.parseDelimitedFrom(inputStream) here.
     *
     * @param inputStream the input stream to be which the data source should read from.
     * @return the object T written to this file.
     * @throws IOException an IOException for when reading from proto fails.
     */
    @Nullable
    protected abstract T parseDelimitedFrom(InputStream inputStream) throws IOException;

    /**
     * This method will be called by {@link ProtoDataSource#writeToFile(MessageLite)}.
     *
     * Implementation is left to subclass since {@link MessageLite#writeDelimitedTo(OutputStream)}
     * requires a defined class at compile time. Subclasses should implement this method by directly
     * calling T.writeDelimitedTo(outputStream) here.
     *
     * @param outputData the output data T to be written to the file.
     * @param outputStream the output stream which the data should be written to.
     * @throws IOException an IO Exception for when writing to proto fails.
     */
    protected abstract void writeDelimitedTo(T outputData, OutputStream outputStream)
            throws IOException;
}
