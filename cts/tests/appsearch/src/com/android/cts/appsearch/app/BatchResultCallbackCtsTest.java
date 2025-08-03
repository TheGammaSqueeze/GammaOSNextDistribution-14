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

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.BatchResultCallback;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.Set;

public class BatchResultCallbackCtsTest {

    @Test
    public void testCallback() {
        Set<String> resultSet = new ArraySet<>();
        BatchResultCallback<String, Void> callback = new BatchResultCallback<String, Void>() {
            @Override
            public void onResult(@NonNull AppSearchBatchResult<String, Void> result) {
                resultSet.add("Got success result");
            }

            @Override
            public void onSystemError(@Nullable Throwable throwable) {
                resultSet.add("Got System Error");
            }
        };

        callback.onResult(new AppSearchBatchResult.Builder<String, Void>().build());
        assertThat(resultSet).containsExactly("Got success result");
        callback.onSystemError(new RuntimeException());
        assertThat(resultSet).containsExactly("Got success result", "Got System Error");
    }
}
