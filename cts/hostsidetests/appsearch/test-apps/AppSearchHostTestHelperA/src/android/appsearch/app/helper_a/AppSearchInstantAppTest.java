/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.appsearch.app.helper_a;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public class AppSearchInstantAppTest {

    private static final String DB_NAME = "";

    @Test
    public void testInstantAppDoesntHaveAccess() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assertThat(context.getPackageManager().isInstantApp()).isTrue();

        int userId = context.getUserId();
        ExecutionException exception = expectThrows(ExecutionException.class, () ->
                AppSearchSessionShimImpl.createSearchSessionAsync(
                        new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                        userId).get());
        assertThat(exception.getMessage()).contains(
                "AppSearchResult is a failure: [FAILURE(8)]: SecurityException: Caller not allowed "
                        + "to create AppSearch session");
    }
}
