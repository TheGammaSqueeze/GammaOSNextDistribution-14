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

package com.android.bedstead.nene.utils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collection of {@link String} values which can change behaviour of APIs.
 */
public final class Tags {

    public static final String USES_DEVICESTATE = "uses_devicestate";
    public static final String USES_NOTIFICATIONS = "uses_notifications";
    public static final String INSTANT_APP = "instant_app";
    public static final String ADB_ROOT = "adb-root";

    private Tags() {

    }

    private static final Set<String> sTags = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * {@code true} if the tag has been added.
     */
    public static boolean hasTag(String tag) {
        return sTags.contains(tag);
    }

    /**
     * Clear all added tags.
     */
    public static void clearTags() {
        sTags.clear();
    }

    /**
     * Add a tag.
     */
    public static void addTag(String tag) {
        sTags.add(tag);
    }
}
