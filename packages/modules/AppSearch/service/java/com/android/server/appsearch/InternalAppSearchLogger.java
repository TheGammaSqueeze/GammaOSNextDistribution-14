/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appsearch;

import android.annotation.NonNull;

import com.android.server.appsearch.external.localstorage.AppSearchLogger;
import com.android.server.appsearch.util.ApiCallRecord;

import java.util.List;

/**
 * A non-public interface for implementing AppSearch logging based operations stats.
 * @hide
 */
public interface InternalAppSearchLogger extends AppSearchLogger {

    /** Removes any cached data for the package when the app gets uninstalled. */
    void removeCacheForPackage(@NonNull String packageName);

    /** Returns a copy of the recorded {@link ApiCallRecord}. */
    List<ApiCallRecord> getLastCalledApis();
}
