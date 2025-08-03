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

package android.app.appsearch;

import android.app.appsearch.aidl.AppSearchAttributionSource;
import android.app.appsearch.aidl.IAppSearchManager;
import android.app.appsearch.exceptions.AppSearchException;
import android.content.Context;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import android.os.SystemClock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created to make a test where callingPackageName is directly passed in
 */
public class GlobalSearchSessionUnitTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    AppSearchManager mAppSearch = mContext.getSystemService(AppSearchManager.class);
    private final Executor mExecutor = mContext.getMainExecutor();
    private GlobalSearchSession mGlobalSearchSession;
    private AppSearchSession mDb;

    @Before
    public void setUp() throws Exception {
        //mglobal
        // Remove all documents from any instances that may have been created in the tests.
        Objects.requireNonNull(mAppSearch);
        SettableFuture<AppSearchResult<GlobalSearchSession>> future = SettableFuture.create();
        ExecutorService executor = Executors.newCachedThreadPool();
        mAppSearch.createGlobalSearchSession(executor, future::set);
        mGlobalSearchSession = future.get().getResultValue();
        assertThat(mGlobalSearchSession).isNotNull();
        assertThat(mGlobalSearchSession.isForEnterprise()).isFalse();

        //mdb
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder("testDb").build();
        CompletableFuture<AppSearchResult<AppSearchSession>> future2 = new CompletableFuture<>();
        mAppSearch.createSearchSession(searchContext, mExecutor, future2::complete);
        mDb = future2.get().getResultValue();

        CompletableFuture<AppSearchResult<SetSchemaResponse>> schemaFuture =
                new CompletableFuture<>();
        mDb.setSchema(
                new SetSchemaRequest.Builder().setForceOverride(true).build(), mExecutor, mExecutor,
                schemaFuture::complete);

        schemaFuture.get().getResultValue();
    }

    /**
     * Calling package param doesn't match the uid
     */
    @Test
    public void testGlobalGetByDocId_invalidCallingPackageParam() throws Exception {
        IAppSearchManager service = mGlobalSearchSession.getService();

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        String invalidPackageName = "not_this_package";

        service.getDocuments(
                new AppSearchAttributionSource(invalidPackageName,
                        android.os.Process.myUid()),
                /*targetPackageName=*/ mContext.getPackageName(),
                /*databaseName*/ "testDb",
                /*namespace=*/ "namespace",
                /*ids=*/ ImmutableList.of("uri1"),
                /*typePropertyPaths=*/ ImmutableMap.of(),
                android.os.Process.myUserHandle(),
                SystemClock.elapsedRealtime(),
                /*isForEnterprise=*/ false,
                SearchSessionUtil.createGetDocumentCallback(mExecutor,
                        new BatchResultCallback<String, GenericDocument>() {
                            @Override
                            public void onResult(
                                    @NonNull AppSearchBatchResult<String,
                                            GenericDocument> result) { }

                            @Override
                            public void onSystemError(@Nullable Throwable throwable) {
                                errorFuture.complete(throwable);
                            }
                        }));

        Throwable error = errorFuture.get();
        assertThat(error).isInstanceOf(AppSearchException.class);
        String expectedErrorMessage =
                "SecurityException: Specified calling package [" + invalidPackageName +
                        "] does not match the calling uid " + android.os.Process.myUid();
        assertThat(error.getMessage()).isEqualTo(expectedErrorMessage);
    }
}

