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
package android.app.appsearch.cts.app;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;

public class AppSearchSessionCtsTest extends AppSearchSessionCtsTestBase {
    protected ListenableFuture<AppSearchSessionShim> createSearchSessionAsync(
            @NonNull String dbName) {
        return AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder(dbName).build());
    }

    protected ListenableFuture<AppSearchSessionShim> createSearchSessionAsync(
            @NonNull String dbName, @NonNull ExecutorService executor) {
        Context context = ApplicationProvider.getApplicationContext();
        return AppSearchSessionShimImpl.createSearchSessionAsync(context,
                new AppSearchManager.SearchContext.Builder(dbName).build(), executor);
    }
}
