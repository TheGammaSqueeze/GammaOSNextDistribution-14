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

package com.android.server.thread;

import static com.android.net.module.util.DeviceConfigUtils.TETHERING_MODULE_NAME;

import android.annotation.Nullable;
import android.content.ApexEnvironment;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.AtomicFile;
import android.util.Log;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.connectivity.ConnectivityResources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Store persistent data for Thread network settings. These are key (string) / value pairs that are
 * stored in ThreadPersistentSetting.xml file. The values allowed are those that can be serialized
 * via {@link PersistableBundle}.
 */
public class ThreadPersistentSettings {
    private static final String TAG = "ThreadPersistentSettings";
    /** File name used for storing settings. */
    private static final String FILE_NAME = "ThreadPersistentSettings.xml";
    /** Current config store data version. This will be incremented for any additions. */
    private static final int CURRENT_SETTINGS_STORE_DATA_VERSION = 1;
    /**
     * Stores the version of the data. This can be used to handle migration of data if some
     * non-backward compatible change introduced.
     */
    private static final String VERSION_KEY = "version";

    /******** Thread persistent setting keys ***************/
    /** Stores the Thread feature toggle state, true for enabled and false for disabled. */
    public static final Key<Boolean> THREAD_ENABLED = new Key<>("Thread_enabled", true);

    /******** Thread persistent setting keys ***************/

    @GuardedBy("mLock")
    private final AtomicFile mAtomicFile;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final PersistableBundle mSettings = new PersistableBundle();

    private final ConnectivityResources mResources;

    public static ThreadPersistentSettings newInstance(Context context) {
        return new ThreadPersistentSettings(
                new AtomicFile(new File(getOrCreateThreadNetworkDir(), FILE_NAME)),
                new ConnectivityResources(context));
    }

    @VisibleForTesting
    ThreadPersistentSettings(AtomicFile atomicFile, ConnectivityResources resources) {
        mAtomicFile = atomicFile;
        mResources = resources;
    }

    /** Initialize the settings by reading from the settings file. */
    public void initialize() {
        readFromStoreFile();
        synchronized (mLock) {
            if (!mSettings.containsKey(THREAD_ENABLED.key)) {
                Log.i(TAG, "\"thread_enabled\" is missing in settings file, using default value");
                put(
                        THREAD_ENABLED.key,
                        mResources.get().getBoolean(R.bool.config_thread_default_enabled));
            }
        }
    }

    private void putObject(String key, @Nullable Object value) {
        synchronized (mLock) {
            if (value == null) {
                mSettings.putString(key, null);
            } else if (value instanceof Boolean) {
                mSettings.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                mSettings.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                mSettings.putLong(key, (Long) value);
            } else if (value instanceof Double) {
                mSettings.putDouble(key, (Double) value);
            } else if (value instanceof String) {
                mSettings.putString(key, (String) value);
            } else {
                throw new IllegalArgumentException("Unsupported type " + value.getClass());
            }
        }
    }

    private <T> T getObject(String key, T defaultValue) {
        Object value;
        synchronized (mLock) {
            if (defaultValue instanceof Boolean) {
                value = mSettings.getBoolean(key, (Boolean) defaultValue);
            } else if (defaultValue instanceof Integer) {
                value = mSettings.getInt(key, (Integer) defaultValue);
            } else if (defaultValue instanceof Long) {
                value = mSettings.getLong(key, (Long) defaultValue);
            } else if (defaultValue instanceof Double) {
                value = mSettings.getDouble(key, (Double) defaultValue);
            } else if (defaultValue instanceof String) {
                value = mSettings.getString(key, (String) defaultValue);
            } else {
                throw new IllegalArgumentException("Unsupported type " + defaultValue.getClass());
            }
        }
        return (T) value;
    }

    /**
     * Store a value to the stored settings.
     *
     * @param key One of the settings keys.
     * @param value Value to be stored.
     */
    public <T> void put(String key, @Nullable T value) {
        putObject(key, value);
        writeToStoreFile();
    }

    /**
     * Retrieve a value from the stored settings.
     *
     * @param key One of the settings keys.
     * @return value stored in settings, defValue if the key does not exist.
     */
    public <T> T get(Key<T> key) {
        return getObject(key.key, key.defaultValue);
    }

    /**
     * Base class to store string key and its default value.
     *
     * @param <T> Type of the value.
     */
    public static class Key<T> {
        public final String key;
        public final T defaultValue;

        private Key(String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        @Override
        public String toString() {
            return "[Key: " + key + ", DefaultValue: " + defaultValue + "]";
        }
    }

    private void writeToStoreFile() {
        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final PersistableBundle bundleToWrite;
            synchronized (mLock) {
                bundleToWrite = new PersistableBundle(mSettings);
            }
            bundleToWrite.putInt(VERSION_KEY, CURRENT_SETTINGS_STORE_DATA_VERSION);
            bundleToWrite.writeToStream(outputStream);
            synchronized (mLock) {
                writeToAtomicFile(mAtomicFile, outputStream.toByteArray());
            }
        } catch (IOException e) {
            Log.wtf(TAG, "Write to store file failed", e);
        }
    }

    private void readFromStoreFile() {
        try {
            final byte[] readData;
            synchronized (mLock) {
                Log.i(TAG, "Reading from store file: " + mAtomicFile.getBaseFile());
                readData = readFromAtomicFile(mAtomicFile);
            }
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(readData);
            final PersistableBundle bundleRead = PersistableBundle.readFromStream(inputStream);
            // Version unused for now. May be needed in the future for handling migrations.
            bundleRead.remove(VERSION_KEY);
            synchronized (mLock) {
                mSettings.putAll(bundleRead);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "No store file to read", e);
        } catch (IOException e) {
            Log.e(TAG, "Read from store file failed", e);
        }
    }

    /**
     * Read raw data from the atomic file. Note: This is a copy of {@link AtomicFile#readFully()}
     * modified to use the passed in {@link InputStream} which was returned using {@link
     * AtomicFile#openRead()}.
     */
    private static byte[] readFromAtomicFile(AtomicFile file) throws IOException {
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

    /** Write the raw data to the atomic file. */
    private static void writeToAtomicFile(AtomicFile file, byte[] data) throws IOException {
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

    /** Get device protected storage dir for the tethering apex. */
    private static File getOrCreateThreadNetworkDir() {
        final File threadnetworkDir;
        final File apexDataDir =
                ApexEnvironment.getApexEnvironment(TETHERING_MODULE_NAME)
                        .getDeviceProtectedDataDir();
        threadnetworkDir = new File(apexDataDir, "thread");

        if (threadnetworkDir.exists() || threadnetworkDir.mkdirs()) {
            return threadnetworkDir;
        }
        throw new IllegalStateException(
                "Cannot write into thread network data directory: " + threadnetworkDir);
    }
}
