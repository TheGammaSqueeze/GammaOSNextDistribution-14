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

package com.android.tv.settings.util;

import android.content.Intent;

/**
 * Utilities to sanitize external intents
 */
public final class SafeIntents {
    private static final int UNSAFE_GRANT_FLAGS =
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            | Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    /**
     * Returns an intent safe to callback to the app that supplied it.
     */
    public static Intent forCallback(Intent intent) {
        Intent safeIntent = new Intent(intent);
        safeIntent.removeFlags(UNSAFE_GRANT_FLAGS);
        return safeIntent;
    }

    private SafeIntents() {}
}
