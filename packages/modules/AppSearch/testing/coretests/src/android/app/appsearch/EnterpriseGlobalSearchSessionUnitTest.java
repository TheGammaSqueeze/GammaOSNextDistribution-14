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

package android.app.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class EnterpriseGlobalSearchSessionUnitTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AppSearchManager mAppSearchManager = mContext.getSystemService(
            AppSearchManager.class);
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Test
    public void testCreateEnterpriseGlobalSearchSession() throws Exception {
        AtomicReference<AppSearchResult<EnterpriseGlobalSearchSession>> resultRef =
                new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        mAppSearchManager.createEnterpriseGlobalSearchSession(mExecutor, appSearchResult -> {
            resultRef.set(appSearchResult);
            latch.countDown();
        });
        assertThat(latch.await(30000, TimeUnit.MILLISECONDS)).isTrue();
        AppSearchResult<EnterpriseGlobalSearchSession> result = resultRef.get();
        assertThat(result.isSuccess()).isTrue();
        EnterpriseGlobalSearchSession enterpriseGlobalSearchSession = result.getResultValue();
        assertThat(
                enterpriseGlobalSearchSession.isForEnterprise()).isTrue();
    }
}
