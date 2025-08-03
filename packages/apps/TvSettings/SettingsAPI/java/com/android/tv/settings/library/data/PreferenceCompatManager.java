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

package com.android.tv.settings.library.data;

import android.util.ArrayMap;

import com.android.tv.settings.library.PreferenceCompat;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Manage the creation and removal of {@link PreferenceCompat} for a state. */
public class PreferenceCompatManager {
    private final Map<String, PreferenceCompat> mPrefCompats = new ArrayMap<>();

    public PreferenceCompat getOrCreatePrefCompat(String key) {
        return getOrCreatePrefCompat(new String[]{key});
    }

    /**
     * Get or create the preferenceCompat with the specified key.
     *
     * @param key key of the preferenceCompat
     * @return preferenceCompat with the specified key.
     */
    public PreferenceCompat getOrCreatePrefCompat(String[] key) {
        if (key == null) {
            return null;
        }
        String compoundKey = getKey(key);
        if (!mPrefCompats.containsKey(compoundKey)) {
            mPrefCompats.put(compoundKey, new PreferenceCompat(key));
        }
        return mPrefCompats.get(compoundKey);
    }

    /**
     * Get the preferenceCompat, used in
     * {@link PreferenceControllerState#onPreferenceChange(String[],
     * Object)}
     * or {@link PreferenceControllerState#onPreferenceTreeClick(String[], boolean)}
     *
     * @param key key of the preferenceCompat
     * @return preferenceCompat with the specified key, or null if does not exist.
     */
    public PreferenceCompat getPrefCompat(String[] key) {
        if (key == null) {
            return null;
        }
        String compoundKey = getKey(key);
        return mPrefCompats.get(compoundKey);
    }

    public static String getKey(String[] key) {
        return Stream.of(key).collect(Collectors.joining(" "));
    }
}
