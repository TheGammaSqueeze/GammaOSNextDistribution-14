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

package com.android.bedstead.nene.instrumentation;

import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Set;

/** Access to Instrumentation Arguments. */
public final class Arguments {

    public static final Arguments sInstance = new Arguments();

    private final Bundle mArguments = InstrumentationRegistry.getArguments();

    private Arguments() {
    }

    /** Gets int instrumentation arg. */
    public int getInt(String key) {
        return mArguments.getInt(key);
    }

    /** Gets int instrumentation arg. */
    public int getInt(String key, int defaultValue) {
        return mArguments.getInt(key, defaultValue);
    }

    /** Gets boolean instrumentation arg. */
    public boolean getBoolean(String key) {
        String stringValue = getString(key);
        if (stringValue == null) {
            return mArguments.getBoolean(key);
        }

        return stringValue.equalsIgnoreCase("true");
    }

    /** Gets boolean instrumentation arg. */
    public boolean getBoolean(String key, boolean defaultValue) {
        String stringValue = getString(key);
        if (stringValue == null) {
            return mArguments.getBoolean(key, defaultValue);
        }

        return stringValue.equalsIgnoreCase("true");
    }

    /** Gets string instrumentation arg. */
    public String getString(String key) {
        return mArguments.getString(key);
    }

    /** Gets string instrumentation arg. */
    public String getString(String key, String defaultValue) {
        return mArguments.getString(key, defaultValue);
    }

    /** Gets the keys of passed arguments. */
    public Set<String> keys() {
        return mArguments.keySet();
    }

}
