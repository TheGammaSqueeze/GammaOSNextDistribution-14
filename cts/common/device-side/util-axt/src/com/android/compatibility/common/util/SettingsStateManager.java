/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static com.android.compatibility.common.util.UserSettings.Namespace;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Manages the state of a preference backed by {@link Settings}.
 */
public class SettingsStateManager implements StateManager<String> {

    private final UserSettings mUserSettings;

    private final String mKey;

    /**
     * Constructor for the {@link Namespace#SECURE} namespace.
     *
     * @param context context used to retrieve the {@link Settings} provider.
     * @param namespace settings namespace.
     * @param key preference key.
     */
    public SettingsStateManager(Context context, String key) {
        this(context, Namespace.SECURE, key);
    }

    /**
     * Full-attributes constructor.
     *
     * @param context context used to retrieve the {@link Settings} provider.
     * @param namespace settings namespace.
     * @param key preference key.
     */
    public SettingsStateManager(Context context, Namespace namespace, String key) {
        mUserSettings = new UserSettings(Objects.requireNonNull(context),
                Objects.requireNonNull(namespace));
        mKey = Objects.requireNonNull(key);
    }

    @Override
    public void set(@Nullable String value) {
        mUserSettings.syncSet(mKey, value);
    }

    @Override
    @Nullable
    public String get() {
        return mUserSettings.get(mKey);
    }

    @Override
    public String toString() {
        return "SettingsStateManager[" + mUserSettings + ", key=" + mKey + "]";
    }
}
