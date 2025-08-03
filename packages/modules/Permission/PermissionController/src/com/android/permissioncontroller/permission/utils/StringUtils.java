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

package com.android.permissioncontroller.permission.utils;

import android.content.Context;
import android.icu.text.MessageFormat;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Utils related to strings and string formatting */
public class StringUtils {

    /**
     * Returns plural string formatted using the provided resource id with icu syntax and count
     *
     * @param context Context used to get string resource
     * @param stringResId String resource with icu syntax to be used for formatting
     * @param count Count used to format the plural string
     * @param formatArgs String arguments used for substitution
     */
    public static String getIcuPluralsString(@NonNull Context context, @StringRes int stringResId,
            int count, Object... formatArgs) {
        MessageFormat msgFormat = new MessageFormat(
                context.getResources().getString(stringResId, formatArgs),
                Locale.getDefault());
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", count);
        return msgFormat.format(arguments);
    }
}
