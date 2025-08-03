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

package com.android.bedstead.nene.systemproperties;

import android.util.Log;

import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;

import java.lang.reflect.InvocationTargetException;

/** Test APIs related to setting and getting {@link android.os.SystemProperties}. */
@Experimental
public final class SystemProperties {

    private static final String LOG_TAG = "SystemProperties";

    public static final SystemProperties sInstance = new SystemProperties();

    private SystemProperties() {

    }

    /** See {@link android.os.SystemProperties#set(java.lang.String, java.lang.String)}. */
    public void set(String key, String value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }

        if (value.equals(get(key))) {
            Log.i(LOG_TAG, "Setting SystemProperty " + key + " to " + value + " but already set");
            return;
        }

        try {
            // TODO(b/203749299): Remove reflection
            android.os.SystemProperties.class.getMethod("set", String.class, String.class)
                    .invoke(null, key, value);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new NeneException("Error setting system property " + key + " to " + value, e);
        }
    }

    /** See {@link android.os.SystemProperties#get(java.lang.String)}. */
    public String get(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return android.os.SystemProperties.get(key);
    }

    /** See {@link android.os.SystemProperties#get(java.lang.String, java.lang.String)}. */
    public String get(String key, String def) {
        if (key == null) {
            throw new NullPointerException();
        }
        return android.os.SystemProperties.get(key, def);
    }

    /** See {@link android.os.SystemProperties#getInt(java.lang.String, int)}. */
    public int getInt(String key, int def) {
        if (key == null) {
            throw new NullPointerException();
        }
        return android.os.SystemProperties.getInt(key, def);
    }

    /** See {@link android.os.SystemProperties#getLong(java.lang.String, long)}. */
    public long getLong(String key, long def) {
        if (key == null) {
            throw new NullPointerException();
        }
        return android.os.SystemProperties.getLong(key, def);
    }

    /** See {@link android.os.SystemProperties#getBoolean(java.lang.String, boolean)}. */
    public boolean getBoolean(String key, boolean def) {
        if (key == null) {
            throw new NullPointerException();
        }
        return android.os.SystemProperties.getBoolean(key, def);
    }
}
