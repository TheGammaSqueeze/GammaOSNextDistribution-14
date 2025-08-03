/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.cellbroadcastreceiver.unit;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class to create fake SharedPreferences.
 */
public class FakeSharedPreferences implements SharedPreferences, SharedPreferences.Editor {
    private HashMap<String, Object> mValues = new HashMap<String, Object>();

    public int getValueCount() {
        return mValues.size();
    }

    @Override
    public Editor edit() {
        return this;
    }

    @Override
    public boolean contains(String key) {
        return mValues.containsKey(key);
    }

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<String, Object>(mValues);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mValues.containsKey(key)) {
            return ((Boolean) mValues.get(key)).booleanValue();
        }
        return defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (mValues.containsKey(key)) {
            return ((Float) mValues.get(key)).floatValue();
        }
        return defValue;
    }

    @Override
    public int getInt(String key, int defValue) {
        if (mValues.containsKey(key)) {
            return ((Integer) mValues.get(key)).intValue();
        }
        return defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        if (mValues.containsKey(key)) {
            return ((Long) mValues.get(key)).longValue();
        }
        return defValue;
    }

    @Override
    public String getString(String key, String defValue) {
        if (mValues.containsKey(key)) return (String) mValues.get(key);
        else return defValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (mValues.containsKey(key)) {
            return (Set<String>) mValues.get(key);
        }
        return defValues;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Editor putBoolean(String key, boolean value) {
        mValues.put(key, Boolean.valueOf(value));
        return this;
    }

    @Override
    public Editor putFloat(String key, float value) {
        mValues.put(key, value);
        return this;
    }

    @Override
    public Editor putInt(String key, int value) {
        mValues.put(key, value);
        return this;
    }

    @Override
    public Editor putLong(String key, long value) {
        mValues.put(key, value);
        return this;
    }

    @Override
    public Editor putString(String key, String value) {
        mValues.put(key, value);
        return this;
    }

    @Override
    public Editor putStringSet(String key, Set<String> values) {
        mValues.put(key, values);
        return this;
    }

    @Override
    public Editor remove(String key) {
        mValues.remove(key);
        return this;
    }

    @Override
    public Editor clear() {
        mValues.clear();
        return this;
    }

    @Override
    public boolean commit() {
        return true;
    }

    @Override
    public void apply() {
        commit();
    }
}
