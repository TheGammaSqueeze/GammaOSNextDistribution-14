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

package com.android.adservices.cts;

import android.content.pm.Signature;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.app.PutDocumentsRequest;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.exceptions.AppSearchException;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class AppSearchDaoWriter {
    private static final String TAG = "AppSearchWriterActivity";
    private static final String ERROR_APPSEARCH_FAILURE = "Failed writing data to AppSearch";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final int TIMEOUT_MS = 2000;

    private final List<PackageIdentifier> mPackageIdentifiers;

    AppSearchDaoWriter(String adServicesPackageName) {
        Objects.requireNonNull(adServicesPackageName);
        mPackageIdentifiers = computePackageIdentifiers(adServicesPackageName);
    }

    <T> void writeToAppSearch(T doc, ListenableFuture<AppSearchSession> session, String dataType) {
        try {
            writeData(doc, session).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Log.d(TAG, "Wrote " + dataType + " to AppSearch: " + doc);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            Log.e(TAG, "Failed to write " + dataType + " data to AppSearch ", e);
        }
    }

    private static List<PackageIdentifier> computePackageIdentifiers(String adServicesPackageName) {
        List<String> certs =
                List.of(
                        // AdServices debug-signed apk certificate sha
                        "686d5c450e00ebe600f979300a29234644eade42f24ede07a073f2bc6b94a3a2",
                        // AdServices release-signed apk certificate sha
                        "80f8fbb9a026807f58d98dbc28bf70724d8f66bbfcec997c6bdc0102c3230dee",
                        // AOSP AdServices debug-signed apk certificate sha
                        "ccc51124be4ab8f81bca7d32f6b3eb1ae04770e5ddfcc73a0108e1f94ec9f5f2");
        return certs.stream()
                .map(
                        s ->
                                new PackageIdentifier(
                                        adServicesPackageName, new Signature(s).toByteArray()))
                .collect(Collectors.toList());
    }

    private <T> FluentFuture<AppSearchBatchResult<String, Void>> writeData(
            @NonNull T doc, @NonNull ListenableFuture<AppSearchSession> appSearchSession) {
        Objects.requireNonNull(appSearchSession);
        Objects.requireNonNull(CALLBACK_EXECUTOR);

        try {
            SetSchemaRequest.Builder setSchemaRequestBuilder = new SetSchemaRequest.Builder();
            setSchemaRequestBuilder.addDocumentClasses(doc.getClass());

            for (PackageIdentifier packageIdentifier : mPackageIdentifiers) {
                setSchemaRequestBuilder.setSchemaTypeVisibilityForPackage(
                        doc.getClass().getSimpleName(), true, packageIdentifier);
            }

            SetSchemaRequest setSchemaRequest = setSchemaRequestBuilder.build();
            PutDocumentsRequest putRequest =
                    new PutDocumentsRequest.Builder().addDocuments(doc).build();
            return FluentFuture.from(appSearchSession)
                    .transformAsync(
                            session -> session.setSchemaAsync(setSchemaRequest),
                            AppSearchDaoWriter.CALLBACK_EXECUTOR)
                    .transformAsync(
                            setSchemaResponse -> {
                                // If we get failures in schemaResponse then we cannot try
                                // to write.
                                if (!setSchemaResponse.getMigrationFailures().isEmpty()) {
                                    Log.e(
                                            TAG,
                                            "SetSchemaResponse migration failure: "
                                                    + setSchemaResponse
                                                            .getMigrationFailures()
                                                            .get(0));
                                    throw new RuntimeException(ERROR_APPSEARCH_FAILURE);
                                }
                                // The database knows about this schemaType and write can
                                // occur.
                                return Futures.transformAsync(
                                        appSearchSession,
                                        session -> session.putAsync(putRequest),
                                        AppSearchDaoWriter.CALLBACK_EXECUTOR);
                            },
                            AppSearchDaoWriter.CALLBACK_EXECUTOR);
        } catch (AppSearchException e) {
            Log.e(TAG, "Cannot instantiate AppSearch database: " + e.getMessage());
        }

        return FluentFuture.from(
                Futures.immediateFailedFuture(new RuntimeException(ERROR_APPSEARCH_FAILURE)));
    }
}
