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

package com.android.devicelockcontroller.util;

import android.content.Context;
import android.icu.text.MessageFormat;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;

/**
 * A utility class handling Strings.
 */
public final class StringUtil {

    private StringUtil() {}

    /**
     * Returns a plural string from a ICU format string template, which takes "count" as an
     * argument.
     */
    public static String getPluralString(Context context, int count, @StringRes int resId) {
        MessageFormat icuCountFormat = new MessageFormat(
                context.getResources().getString(resId),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap<>();
        args.put("count", count);
        return icuCountFormat.format(args);
    }
}
