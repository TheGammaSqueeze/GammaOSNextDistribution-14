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
package com.android.adservices.shared.testing.common;

import static com.android.adservices.shared.testing.common.GenericHelper.getUniqueId;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// TODO(b/309857141): add unit tests when move
/**
 * Fake implementation of {@link SharedPreferences}.
 *
 * <p><b>Note: </b>calls made to the {@link #edit() editor} are persisted right away, unless
 * disabled by calls to {@link #onCommitReturns(boolean) onCommitReturns(false)} or {@link
 * #disableApply}.
 *
 * <p>This class is not thread safe.
 */
public final class FakeSharedPreferences implements SharedPreferences {

    private static final String TAG = FakeSharedPreferences.class.getSimpleName();

    private static final IllegalStateException EDIT_DISABLED_EXCEPTION =
            new IllegalStateException("edit() is not available");

    private final FakeEditor mEditor = new FakeEditor();

    @Nullable private RuntimeException mEditException;

    /**
     * Changes behavior of {@link #edit()} so it throws an exception.
     *
     * @return the exception that will be thrown by {@link #edit()}.
     */
    public RuntimeException onEditThrows() {
        mEditException = EDIT_DISABLED_EXCEPTION;
        Log.v(TAG, "onEditThrows(): edit() will return " + mEditException);
        return mEditException;
    }

    /**
     * Sets the result of calls to {@link Editor#commit()}.
     *
     * <p><b>Note: </b>when called with {@code false}, calls made to the {@link #edit() editor}
     * after this call will be ignored (until it's called again with {@code true}).
     */
    public void onCommitReturns(boolean result) {
        mEditor.mCommitResult = result;
        Log.v(TAG, "onCommitReturns(): commit() will return " + mEditor.mCommitResult);
    }

    @Override
    public Map<String, ?> getAll() {
        return mEditor.mProps;
    }

    @Override
    public String getString(String key, String defValue) {
        return mEditor.get(key, String.class, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        @SuppressWarnings("unchecked")
        Set<String> value = mEditor.get(key, Set.class, defValues);
        return value;
    }

    @Override
    public int getInt(String key, int defValue) {
        return mEditor.get(key, Integer.class, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return mEditor.get(key, Long.class, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return mEditor.get(key, Float.class, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return mEditor.get(key, Boolean.class, defValue);
    }

    @Override
    public boolean contains(String key) {
        return mEditor.mProps.containsKey(key);
    }

    @Override
    public FakeEditor edit() {
        Log.v(TAG, "edit(): mEditException=" + mEditException);
        if (mEditException != null) {
            throw mEditException;
        }
        return mEditor;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        String id = getUniqueId(listener);
        Log.d(
                TAG,
                "registerOnSharedPreferenceChangeListener(): id=" + id + ", listener=" + listener);

        mEditor.mListeners.put(id, new WeakReference<>(listener));
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        String id = getUniqueId(listener);
        Log.d(
                TAG,
                "unregisterOnSharedPreferenceChangeListener(): id="
                        + id
                        + ", listener="
                        + listener);

        mEditor.mListeners.remove(id);
    }

    private final class FakeEditor implements Editor {

        private final Map<String, Object> mProps = new LinkedHashMap<>();
        private final Set<String> mKeysToNotify = new LinkedHashSet<>();
        private final Map<String, WeakReference<OnSharedPreferenceChangeListener>> mListeners =
                new LinkedHashMap<>();

        private boolean mCommitResult = true;

        @Override
        public Editor putString(String key, String value) {
            return put(key, value);
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            return put(key, values);
        }

        @Override
        public Editor putInt(String key, int value) {
            return put(key, value);
        }

        @Override
        public Editor putLong(String key, long value) {
            return put(key, value);
        }

        @Override
        public Editor putFloat(String key, float value) {
            return put(key, value);
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            return put(key, value);
        }

        @Override
        public Editor remove(String key) {
            Log.v(TAG, "remove(" + key + ")");
            mProps.remove(key);
            mKeysToNotify.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            Log.v(TAG, "clear()");
            mProps.clear();
            return this;
        }

        @Override
        public boolean commit() {
            Log.v(TAG, "commit(): mCommitResult=" + mCommitResult);
            try {
                return mCommitResult;
            } finally {
                notifyListeners();
            }
        }

        @Override
        public void apply() {
            Log.v(TAG, "apply(): mCommitResult=" + mCommitResult);
            notifyListeners();
        }

        private <T> T get(String key, Class<T> clazz, T defValue) {
            Object value = mProps.get(key);
            return value == null ? defValue : clazz.cast(value);
        }

        private FakeEditor put(String key, Object value) {
            Log.v(
                    TAG,
                    "put(): "
                            + key
                            + "="
                            + value
                            + " ("
                            + value.getClass().getSimpleName()
                            + "): mCommitResult="
                            + mCommitResult);
            if (mCommitResult) {
                mProps.put(key, value);
                mKeysToNotify.add(key);
            }
            return mEditor;
        }

        private void notifyListeners() {
            Log.d(TAG, "notifyListeners(): " + mListeners.size() + " listeners");
            for (WeakReference<OnSharedPreferenceChangeListener> ref : mListeners.values()) {
                OnSharedPreferenceChangeListener listener = ref.get();
                if (listener != null) {
                    for (String key : mKeysToNotify) {
                        Log.v(TAG, "Notifying key change (" + key + ") to " + listener);
                        listener.onSharedPreferenceChanged(FakeSharedPreferences.this, key);
                    }
                }
            }
            Log.v(TAG, "Clearing keys to notify (" + mKeysToNotify + ")");
            mKeysToNotify.clear();
        }
    }
}
