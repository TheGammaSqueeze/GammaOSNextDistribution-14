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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;

public class IntentCompat {
    private final Intent mIntent;

    public static final String EXTRA_USER_ID = Intent.EXTRA_USER_ID;

    public static final String EXTRA_USER_HANDLE = Intent.EXTRA_USER_HANDLE;

    public static final String ACTION_QUERY_PACKAGE_RESTART = Intent.ACTION_QUERY_PACKAGE_RESTART;

    public static final String EXTRA_INSTALL_RESULT = Intent.EXTRA_INSTALL_RESULT;

    public static final String EXTRA_PACKAGES = Intent.EXTRA_PACKAGES;

    public static final String EXTRA_UNINSTALL_ALL_USERS = Intent.EXTRA_UNINSTALL_ALL_USERS;

    public static final int EXTRA_TIME_PREF_VALUE_USE_12_HOUR =
            Intent.EXTRA_TIME_PREF_VALUE_USE_12_HOUR;

    public static final int EXTRA_TIME_PREF_VALUE_USE_24_HOUR =
            Intent.EXTRA_TIME_PREF_VALUE_USE_24_HOUR;

    public static final String EXTRA_TIME_PREF_24_HOUR_FORMAT =
            Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT;

    public IntentCompat(Intent intent) {
        mIntent = intent;
    }

    public @Nullable
    ComponentName resolveSystemService(@NonNull PackageManager pm, int flags) {
        return mIntent.resolveSystemService(pm, flags);
    }
}
