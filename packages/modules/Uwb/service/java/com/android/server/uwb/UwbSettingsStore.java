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

package com.android.server.uwb;

import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.util.AtomicFile;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.util.FileUtils;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Store data for storing UWB settings. These are key (string) / value pairs that are stored in
 * UwbSettingsStore.xml file. The values allowed are those that can be serialized via
 * {@link android.os.PersistableBundle}.
 */
public class UwbSettingsStore {
    private static final String TAG = "UwbSettingsStore";
    /**
     * File name used for storing settings.
     */
    public static final String FILE_NAME = "UwbSettingsStore.xml";
    /**
     * Current config store data version. This will be incremented for any additions.
     */
    private static final int CURRENT_SETTINGS_STORE_DATA_VERSION = 1;
    /** This list of older versions will be used to restore data from older store versions. */
    /**
     * First version of the config store data format.
     */
    private static final int INITIAL_SETTINGS_STORE_VERSION = 1;

    /**
     * Store the version of the data. This can be used to handle migration of data if some
     * non-backward compatible change introduced.
     */
    private static final String VERSION_KEY = "version";

    /**
     * Constant copied over from {@link android.provider.Settings} since existing key is @hide.
     */
    @VisibleForTesting
    public static final String SETTINGS_TOGGLE_STATE_KEY_FOR_MIGRATION = "uwb_enabled";

    // List of all allowed keys.
    private static final ArrayList<Key> sKeys = new ArrayList<>();

    /******** Uwb shared pref keys ***************/
    /**
     * Store the UWB settings toggle state.
     */
    public static final Key<Boolean> SETTINGS_TOGGLE_STATE =
            new Key<>("settings_toggle", true);
    public static final Key<String> SETTINGS_LOG_MODE =
            new Key<>("settings_log_mode", UciLogModeStore.Mode.DISABLED.getMode());

    public static final Key<Boolean> SETTINGS_FIRST_TOGGLE_DONE =
            new Key<>("settings_first_toggle_done", false);

    public static final Key<String> SETTINGS_CACHED_COUNTRY_CODE =
            new Key<>("settings_cached_country_code", "");
    /******** Uwb shared pref keys ***************/

    private final Context mContext;
    private final Handler mHandler;
    private final AtomicFile mAtomicFile;
    private final UwbInjector mUwbInjector;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final PersistableBundle mSettings = new PersistableBundle();
    @GuardedBy("mLock")
    private final Map<String, Map<OnSettingsChangedListener, Handler>> mListeners =
            new HashMap<>();

    /**
     * Interface for a settings change listener.
     *
     * @param <T> Type of the value.
     */
    public interface OnSettingsChangedListener<T> {
        /**
         * Invoked when a particular key settings changes.
         *
         * @param key      Key that was changed.
         * @param newValue New value that was assigned to the key.
         */
        void onSettingsChanged(@NonNull Key<T> key, @Nullable T newValue);
    }

    public UwbSettingsStore(@NonNull Context context, @NonNull Handler handler, @NonNull
            AtomicFile atomicFile, UwbInjector uwbInjector) {
        mContext = context;
        mHandler = handler;
        mAtomicFile = atomicFile;
        mUwbInjector = uwbInjector;
    }

    /**
     * Initialize the settings store by triggering the store file read.
     */
    public void initialize() {
        Log.i(TAG, "Reading from store file: " + mAtomicFile.getBaseFile());
        readFromStoreFile();
        // Migrate toggle settings from Android 12 to Android 13.
        boolean isStoreEmpty;
        synchronized (mLock) {
            isStoreEmpty = mSettings.isEmpty();
        }
        if (isStoreEmpty) {
            try {
                boolean toggleEnabled =
                        mUwbInjector.getGlobalSettingsInt(SETTINGS_TOGGLE_STATE_KEY_FOR_MIGRATION)
                                == STATE_ENABLED_ACTIVE;
                Log.i(TAG, "Migrate settings toggle from older release: " + toggleEnabled);
                put(SETTINGS_TOGGLE_STATE, toggleEnabled);
            } catch (Settings.SettingNotFoundException e) {
                /* ignore */
            }
        }
        invokeAllListeners();
    }

    private void invokeAllListeners() {
        synchronized (mLock) {
            for (Key key : sKeys) {
                invokeListeners(key);
            }
        }
    }

    private <T> void invokeListeners(@NonNull Key<T> key) {
        synchronized (mLock) {
            if (!mSettings.containsKey(key.key)) return;
            Object newValue = mSettings.get(key.key);
            Map<OnSettingsChangedListener, Handler> listeners = mListeners.get(key.key);
            if (listeners == null || listeners.isEmpty()) return;
            for (Map.Entry<OnSettingsChangedListener, Handler> listener
                    : listeners.entrySet()) {
                // Trigger the callback in the appropriate handler.
                listener.getValue().post(() ->
                        listener.getKey().onSettingsChanged(key, newValue));
            }
        }
    }

    /**
     * Trigger config store writes and invoke listeners in the main service looper's handler.
     */
    private <T> void triggerSaveToStoreAndInvokeListeners(@NonNull Key<T> key) {
        mHandler.post(() -> {
            writeToStoreFile();
            invokeListeners(key);
        });
    }

    private void putObject(@NonNull String key, @Nullable Object value) {
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

    private <T> T getObject(@NonNull String key, T defaultValue) {
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
     * @param key   One of the settings keys.
     * @param value Value to be stored.
     */
    public <T> void put(@NonNull Key<T> key, @Nullable T value) {
        putObject(key.key, value);
        triggerSaveToStoreAndInvokeListeners(key);
    }

    /**
     * Retrieve a value from the stored settings.
     *
     * @param key One of the settings keys.
     * @return value stored in settings, defValue if the key does not exist.
     */
    public @Nullable <T> T get(@NonNull Key<T> key) {
        return getObject(key.key, key.defaultValue);
    }

    /**
     * Register for settings change listener.
     *
     * @param key      One of the settings keys.
     * @param listener Listener to be registered.
     * @param handler  Handler to post the listener
     */
    public <T> void registerChangeListener(@NonNull Key<T> key,
            @NonNull OnSettingsChangedListener<T> listener, @NonNull Handler handler) {
        synchronized (mLock) {
            mListeners.computeIfAbsent(
                    key.key, ignore -> new HashMap<>()).put(listener, handler);
        }
    }

    /**
     * Unregister for settings change listener.
     *
     * @param key      One of the settings keys.
     * @param listener Listener to be unregistered.
     */
    public <T> void unregisterChangeListener(@NonNull Key<T> key,
            @NonNull OnSettingsChangedListener<T> listener) {
        synchronized (mLock) {
            Map<OnSettingsChangedListener, Handler> listeners = mListeners.get(key.key);
            if (listeners == null || listeners.isEmpty()) {
                Log.e(TAG, "No listeners for " + key);
                return;
            }
            if (listeners.remove(listener) == null) {
                Log.e(TAG, "Unknown listener for " + key);
            }
        }
    }

    /**
     * Dump output for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of UwbSettingsStore ----");
        synchronized (mLock) {
            pw.println("Settings: " + mSettings);
        }
        pw.println("---- Dump of UwbSettingsStore ----");
    }

    /**
     * Base class to store string key and its default value.
     *
     * @param <T> Type of the value.
     */
    public static class Key<T> {
        public final String key;
        public final T defaultValue;

        private Key(@NonNull String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            sKeys.add(this);
        }

        @Override
        public String toString() {
            return "[Key " + key + ", DefaultValue: " + defaultValue + "]";
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
            FileUtils.writeToAtomicFile(mAtomicFile, outputStream.toByteArray());
        } catch (IOException e) {
            Log.e(TAG, "Write to store file failed", e);
        }
    }

    private void readFromStoreFile() {
        try {
            final byte[] readData = FileUtils.readFromAtomicFile(mAtomicFile);
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(readData);
            final PersistableBundle bundleRead = PersistableBundle.readFromStream(inputStream);
            // Version unused for now. May be needed in the future for handling migrations.
            bundleRead.remove(VERSION_KEY);
            synchronized (mLock) {
                mSettings.putAll(bundleRead);
            }
        } catch (FileNotFoundException e) {
            Log.w(TAG, "No store file to read");
        } catch (IOException e) {
            Log.e(TAG, "Read from store file failed", e);
        }
    }
}
