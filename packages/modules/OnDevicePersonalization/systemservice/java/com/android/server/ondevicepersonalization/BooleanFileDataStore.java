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

package com.android.server.ondevicepersonalization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.util.AtomicFile;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A generic key-value datastore utilizing {@link android.util.AtomicFile} and {@link
 * android.os.PersistableBundle} to read/write a simple key/value map to file.
 * This class is thread-safe.
 * @hide
 */
public class BooleanFileDataStore {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "BooleanFileDataStore";
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final Lock mReadLock = mReadWriteLock.readLock();
    private final Lock mWriteLock = mReadWriteLock.writeLock();

    private final AtomicFile mAtomicFile;
    private final Map<String, Boolean> mLocalMap = new HashMap<>();

    // TODO (b/300993651): make the datastore access singleton.
    // TODO (b/301131410): add version history feature.
    public BooleanFileDataStore(@NonNull String parentPath, @NonNull String filename) {
        Objects.requireNonNull(parentPath);
        Objects.requireNonNull(filename);
        Preconditions.checkStringNotEmpty(parentPath);
        Preconditions.checkStringNotEmpty(filename);
        mAtomicFile = new AtomicFile(new File(parentPath, filename));
    }

    /**
     * Loads data from the datastore file on disk.
     * @throws IOException if file read fails.
     */
    public void initialize() throws IOException {
        sLogger.d(TAG + ": reading from file " + mAtomicFile.getBaseFile());
        mReadLock.lock();
        try {
            readFromFile();
        } finally {
            mReadLock.unlock();
        }
    }

    /**
     * Stores a value to the datastore file, which is immediately committed.
     * @param key A non-null, non-empty String to store the {@code value}.
     * @param value A boolean to be stored.
     * @throws IOException if file write fails.
     * @throws NullPointerException if {@code key} is null.
     * @throws IllegalArgumentException if (@code key) is an empty string.
     */
    public void put(@NonNull String key, boolean value) throws IOException {
        Objects.requireNonNull(key);
        Preconditions.checkStringNotEmpty(key, "Key must not be empty.");

        mWriteLock.lock();
        try {
            mLocalMap.put(key, value);
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Retrieves a boolean value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from.
     * @return The boolean value stored against a {@code key}, or null if it doesn't exist.
     * @throws IllegalArgumentException if {@code key} is an empty string.
     * @throws NullPointerException if {@code key} is null.
     */
    @Nullable
    public Boolean get(@NonNull String key) {
        Objects.requireNonNull(key);
        Preconditions.checkStringNotEmpty(key);

        mReadLock.lock();
        try {
            return mLocalMap.get(key);
        } finally {
            mReadLock.unlock();
        }
    }

    /**
     * Retrieves a {@link Set} of all keys loaded from the datastore file.
     *
     * @return A {@link Set} of {@link String} keys currently in the loaded datastore
     */
    @NonNull
    public Set<String> keySet() {
        mReadLock.lock();
        try {
            return Set.copyOf(mLocalMap.keySet());
        } finally {
            mReadLock.unlock();
        }
    }

    /**
     * Clears all entries from the datastore file and committed immediately.
     *
     * @throws IOException if file write fails.
     */
    public void clear() throws IOException {
        sLogger.d(TAG + ": clearing all entries from datastore");

        mWriteLock.lock();
        try {
            mLocalMap.clear();
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    @GuardedBy("mWriteLock")
    private void writeToFile() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final PersistableBundle persistableBundle = new PersistableBundle();
        for (Map.Entry<String, Boolean> entry: mLocalMap.entrySet()) {
            persistableBundle.putBoolean(entry.getKey(), entry.getValue());
        }

        persistableBundle.writeToStream(outputStream);

        FileOutputStream out = null;
        try {
            out = mAtomicFile.startWrite();
            out.write(outputStream.toByteArray());
            mAtomicFile.finishWrite(out);
        } catch (IOException e) {
            mAtomicFile.failWrite(out);
            sLogger.e(TAG + ": write to file " + mAtomicFile.getBaseFile() + " failed.");
            throw e;
        }
    }

    @GuardedBy("mReadLock")
    private void readFromFile() throws IOException {
        try {
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                            mAtomicFile.readFully());
            final PersistableBundle persistableBundle = PersistableBundle.readFromStream(
                            inputStream);

            mLocalMap.clear();
            for (String key: persistableBundle.keySet()) {
                mLocalMap.put(key, persistableBundle.getBoolean(key));
            }
        } catch (FileNotFoundException e) {
            sLogger.d(TAG + ": file not found exception.");
            mLocalMap.clear();
        } catch (IOException e) {
            sLogger.e(TAG + ": read from " + mAtomicFile.getBaseFile() + " failed");
            throw e;
        }
    }

    /**
     * Delete the datastore file for testing.
     */
    @VisibleForTesting
    public void tearDownForTesting() {
        mWriteLock.lock();
        try {
            mAtomicFile.delete();
            mLocalMap.clear();
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Clear the loaded content from local map for testing.
     */
    @VisibleForTesting
    public void clearLocalMapForTesting() {
        mWriteLock.lock();
        mLocalMap.clear();
        mWriteLock.unlock();
    }
}
