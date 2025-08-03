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
package com.android.intentresolver.emptystate;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.AppGlobals;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.IPackageManager;

import com.android.intentresolver.IntentForwarderActivity;

import java.util.List;

/**
 * Utility class to check if there are cross profile intents, it is in a separate class so
 * it could be mocked in tests
 */
public class CrossProfileIntentsChecker {

    private final ContentResolver mContentResolver;
    private final IPackageManager mPackageManager;

    public CrossProfileIntentsChecker(@NonNull ContentResolver contentResolver) {
        this(contentResolver, AppGlobals.getPackageManager());
    }

    CrossProfileIntentsChecker(
            @NonNull ContentResolver contentResolver, IPackageManager packageManager) {
        mContentResolver = contentResolver;
        mPackageManager = packageManager;
    }

    /**
     * Returns {@code true} if at least one of the provided {@code intents} can be forwarded
     * from {@code source} (user id) to {@code target} (user id).
     */
    public boolean hasCrossProfileIntents(
            List<Intent> intents, @UserIdInt int source, @UserIdInt int target) {
        return intents.stream().anyMatch(intent ->
                null != IntentForwarderActivity.canForward(intent, source, target,
                        mPackageManager, mContentResolver));
    }
}

