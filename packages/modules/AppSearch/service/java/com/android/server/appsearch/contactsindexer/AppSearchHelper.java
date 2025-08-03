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

package com.android.server.appsearch.contactsindexer;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSession;
import android.app.appsearch.BatchResultCallback;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.RemoveByDocumentIdRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResults;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.exceptions.AppSearchException;
import android.app.appsearch.util.LogUtil;
import android.content.Context;
import android.util.AndroidRuntimeException;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Helper class to manage the Person corpus in AppSearch.
 *
 * <p>It wraps AppSearch API calls using {@link CompletableFuture}, which is easier to use.
 *
 * <p>Note that, most of those methods are async. And some of them, like {@link
 * #indexContactsAsync(Collection, ContactsUpdateStats)}, accepts a collection of contacts. The
 * caller can modify the collection after the async method returns. There is no need for the
 * CompletableFuture that's returned to be completed.
 *
 * <p>This class is thread-safe.
 *
 * @hide
 */
public class AppSearchHelper {
    static final String TAG = "ContactsIndexerAppSearc";

    public static final String DATABASE_NAME = "contacts";
    // Namespace needed to be used for ContactsIndexer to index the contacts
    public static final String NAMESPACE_NAME = "";

    private static final int GET_CONTACT_IDS_PAGE_SIZE = 500;

    private final Context mContext;
    private final Executor mExecutor;
    private final ContactsIndexerConfig mContactsIndexerConfig;
    // Holds the result of an asynchronous operation to create an AppSearchSession
    // and set the builtin:Person schema in it.
    private volatile CompletableFuture<AppSearchSession> mAppSearchSessionFuture;
    private final CompletableFuture<Boolean> mDataLikelyWipedDuringInitFuture =
            new CompletableFuture<>();

    /**
     * Creates an initialized {@link AppSearchHelper}.
     *
     * @param executor Executor used to handle result callbacks from AppSearch.
     */
    @NonNull
    public static AppSearchHelper createAppSearchHelper(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull ContactsIndexerConfig contactsIndexerConfig) {
        AppSearchHelper appSearchHelper = new AppSearchHelper(context, executor,
                contactsIndexerConfig);
        appSearchHelper.initializeAsync();
        return appSearchHelper;
    }

    @VisibleForTesting
    AppSearchHelper(@NonNull Context context, @NonNull Executor executor,
            @NonNull ContactsIndexerConfig contactsIndexerConfig) {
        mContext = Objects.requireNonNull(context);
        mExecutor = Objects.requireNonNull(executor);
        mContactsIndexerConfig = Objects.requireNonNull(contactsIndexerConfig);
    }

    /**
     * Initializes {@link AppSearchHelper} asynchronously.
     *
     * <p>Chains {@link CompletableFuture}s to create an {@link AppSearchSession} and
     * set builtin:Person schema.
     */
    private void initializeAsync() {
        AppSearchManager appSearchManager = mContext.getSystemService(AppSearchManager.class);
        if (appSearchManager == null) {
            throw new AndroidRuntimeException(
                    "Can't get AppSearchManager to initialize AppSearchHelper.");
        }

        CompletableFuture<AppSearchSession> createSessionFuture =
                createAppSearchSessionAsync(appSearchManager);
        mAppSearchSessionFuture = createSessionFuture.thenCompose(appSearchSession -> {
            // set the schema with forceOverride false first. And if it fails, we will set the
            // schema with forceOverride true. This way, we know when the data is wiped due to an
            // incompatible schema change, which is the main cause for the 1st setSchema to fail.
            return setPersonSchemaAsync(appSearchSession, /*forceOverride=*/ false)
                    .handle((x, e) -> {
                        boolean firstSetSchemaFailed = false;
                        if (e != null) {
                            Log.w(TAG, "Error while setting schema with forceOverride false.", e);
                            firstSetSchemaFailed = true;
                        }
                        return firstSetSchemaFailed;
                    }).thenCompose(firstSetSchemaFailed -> {
                        mDataLikelyWipedDuringInitFuture.complete(firstSetSchemaFailed);
                        if (firstSetSchemaFailed) {
                            // Try setSchema with forceOverride true.
                            // If it succeeds, we know the data is likely to be wiped due to an
                            // incompatible schema change.
                            // If if fails, we don't know the state of that corpus in AppSearch.
                            return setPersonSchemaAsync(appSearchSession, /*forceOverride=*/ true);
                        }
                        return CompletableFuture.completedFuture(appSearchSession);
                    });
        });
    }

    /**
     * Creates the {@link AppSearchSession}.
     *
     * <p>It returns {@link CompletableFuture} so caller can wait for a valid AppSearchSession
     * created, which must be done before ContactsIndexer starts handling CP2 changes.
     */
    private CompletableFuture<AppSearchSession> createAppSearchSessionAsync(
            @NonNull AppSearchManager appSearchManager) {
        Objects.requireNonNull(appSearchManager);

        CompletableFuture<AppSearchSession> future = new CompletableFuture<>();
        final AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(DATABASE_NAME).build();
        appSearchManager.createSearchSession(searchContext, mExecutor, result -> {
            if (result.isSuccess()) {
                future.complete(result.getResultValue());
            } else {
                Log.e(TAG, "Failed to create an AppSearchSession - code: " + result.getResultCode()
                        + " errorMessage: " + result.getErrorMessage());
                future.completeExceptionally(
                        new AppSearchException(result.getResultCode(), result.getErrorMessage()));
            }
        });

        return future;
    }

    /**
     * Sets the Person schemas for the {@link AppSearchSession}.
     *
     * <p>It returns {@link CompletableFuture} so caller can wait for valid schemas set, which must
     * be done before ContactsIndexer starts handling CP2 changes.
     *
     * @param session       {@link AppSearchSession} created before.
     * @param forceOverride whether the incompatible schemas should be overridden.
     */
    @NonNull
    private CompletableFuture<AppSearchSession> setPersonSchemaAsync(
            @NonNull AppSearchSession session, boolean forceOverride) {
        Objects.requireNonNull(session);

        CompletableFuture<AppSearchSession> future = new CompletableFuture<>();
        SetSchemaRequest.Builder schemaBuilder = new SetSchemaRequest.Builder()
                .addSchemas(ContactPoint.SCHEMA, Person.getSchema(mContactsIndexerConfig))
                .addRequiredPermissionsForSchemaTypeVisibility(Person.SCHEMA_TYPE,
                        Collections.singleton(SetSchemaRequest.READ_CONTACTS))
                // Adds a permission set that allows the Person schema to be read by an enterprise
                // session. The set contains ENTERPRISE_ACCESS which makes it visible to enterprise
                // sessions and unsatisfiable for regular sessions. The set also requires the caller
                // to have regular read contacts access and managed profile contacts access.
                .addRequiredPermissionsForSchemaTypeVisibility(Person.SCHEMA_TYPE,
                        new ArraySet<>(Arrays.asList(
                                SetSchemaRequest.ENTERPRISE_ACCESS,
                                SetSchemaRequest.READ_CONTACTS,
                                SetSchemaRequest.MANAGED_PROFILE_CONTACTS_ACCESS
                        )))
                .setForceOverride(forceOverride);
        session.setSchema(schemaBuilder.build(), mExecutor, mExecutor,
                result -> {
                    if (result.isSuccess()) {
                        future.complete(session);
                    } else {
                        Log.e(TAG, "SetSchema failed: code " + result.getResultCode() + " message:"
                                + result.getErrorMessage());
                        future.completeExceptionally(new AppSearchException(result.getResultCode(),
                                result.getErrorMessage()));
                    }
                });
        return future;
    }

    @WorkerThread
    @VisibleForTesting
    @Nullable
    AppSearchSession getSession() throws ExecutionException, InterruptedException {
        return mAppSearchSessionFuture.get();
    }

    @VisibleForTesting
    void setAppSearchSessionFutureForTesting(
            CompletableFuture<AppSearchSession> appSearchSessionFuture) {
        mAppSearchSessionFuture = appSearchSessionFuture;
    }

    /**
     * Returns if the data is likely being wiped during initialization of this {@link
     * AppSearchHelper}.
     *
     * <p>The Person corpus in AppSearch can be wiped during setSchema, and this indicates if it
     * happens:
     * <li>If the value is {@code false}, we are sure there is NO data loss.
     * <li>If the value is {@code true}, it is very likely the data loss happens, or the whole
     * initialization fails and the data state is unknown. Callers need to query AppSearch to
     * confirm.
     */
    @NonNull
    public CompletableFuture<Boolean> isDataLikelyWipedDuringInitAsync() {
        // Internally, it indicates whether the first setSchema with forceOverride false fails or
        // not.
        return mDataLikelyWipedDuringInitFuture;
    }

    /**
     * Indexes contacts into AppSearch
     *
     * @param contacts    a collection of contacts. AppSearch batch put will be used to send the
     *                    documents over in one call. So the size of this collection can't be too
     *                    big, otherwise binder {@link android.os.TransactionTooLargeException} will
     *                    be thrown.
     * @param updateStats to hold the counters for the update.
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors. When true, the
     *                                  returned future completes normally even when contacts have
     *                                  failed to be added. AppSearchResult#RESULT_OUT_OF_SPACE
     *                                  failures are an exception to this however and will still
     *                                  complete exceptionally.
     */
    @NonNull
    public CompletableFuture<Void> indexContactsAsync(@NonNull Collection<Person> contacts,
            @NonNull ContactsUpdateStats updateStats,
            boolean shouldKeepUpdatingOnError) {
        Objects.requireNonNull(contacts);
        Objects.requireNonNull(updateStats);

        if (LogUtil.DEBUG) {
            Log.v(TAG, "Indexing " + contacts.size() + " contacts into AppSearch");
        }
        PutDocumentsRequest request = new PutDocumentsRequest.Builder()
                .addGenericDocuments(contacts)
                .build();
        return mAppSearchSessionFuture.thenCompose(appSearchSession -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            appSearchSession.put(request, mExecutor, new BatchResultCallback<>() {
                @Override
                public void onResult(AppSearchBatchResult<String, Void> result) {
                    int numDocsSucceeded = result.getSuccesses().size();
                    int numDocsFailed = result.getFailures().size();
                    updateStats.mContactsUpdateSucceededCount += numDocsSucceeded;
                    if (result.isSuccess()) {
                        if (LogUtil.DEBUG) {
                            Log.v(TAG, numDocsSucceeded
                                    + " documents successfully added in AppSearch.");
                        }
                        future.complete(null);
                    } else {
                        Map<String, AppSearchResult<Void>> failures = result.getFailures();
                        AppSearchResult<Void> firstFailure = null;
                        for (AppSearchResult<Void> failure : failures.values()) {
                            int errorCode = failure.getResultCode();
                            if (firstFailure == null) {
                                if (shouldKeepUpdatingOnError) {
                                    // Still complete exceptionally (and abort further indexing) if
                                    // AppSearchResult#RESULT_OUT_OF_SPACE
                                    if (errorCode == AppSearchResult.RESULT_OUT_OF_SPACE) {
                                        firstFailure = failure;
                                    }
                                } else {
                                    firstFailure = failure;
                                }
                            }
                            updateStats.mUpdateStatuses.add(errorCode);
                        }
                        if (firstFailure == null) {
                            future.complete(null);
                        } else {
                            Log.w(TAG,
                                    numDocsFailed + " documents failed to be added in AppSearch.");
                            future.completeExceptionally(
                                    new AppSearchException(firstFailure.getResultCode(),
                                            firstFailure.getErrorMessage()));
                        }
                    }
                }

                @Override
                public void onSystemError(Throwable throwable) {
                    Log.e(TAG, "Failed to add contacts", throwable);
                    // Log a combined status code; ranges of the codes do not overlap 10100 + 0-99
                    updateStats.mUpdateStatuses.add(
                            ContactsUpdateStats.ERROR_CODE_APP_SEARCH_SYSTEM_ERROR
                                    + AppSearchResult.throwableToFailedResult(
                                    throwable).getResultCode());
                    if (shouldKeepUpdatingOnError) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(throwable);
                    }
                }
            });
            return future;
        });
    }

    /**
     * Remove contacts from AppSearch
     *
     * @param ids         a collection of contact ids. AppSearch batch remove will be used to send
     *                    the ids over in one call. So the size of this collection can't be too
     *                    big, otherwise binder {@link android.os.TransactionTooLargeException}
     *                    will be thrown.
     * @param updateStats to hold the counters for the update.
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors. When enabled,
     *                                  the returned future completes normally even when contacts
     *                                  have failed to be removed.
     */
    @NonNull
    public CompletableFuture<Void> removeContactsByIdAsync(@NonNull Collection<String> ids,
            @NonNull ContactsUpdateStats updateStats,
            boolean shouldKeepUpdatingOnError) {
        Objects.requireNonNull(ids);
        Objects.requireNonNull(updateStats);

        if (LogUtil.DEBUG) {
            Log.v(TAG, "Removing " + ids.size() + " contacts from AppSearch");
        }
        RemoveByDocumentIdRequest request = new RemoveByDocumentIdRequest.Builder(NAMESPACE_NAME)
                .addIds(ids)
                .build();
        return mAppSearchSessionFuture.thenCompose(appSearchSession -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            appSearchSession.remove(request, mExecutor, new BatchResultCallback<>() {
                @Override
                public void onResult(AppSearchBatchResult<String, Void> result) {
                    int numSuccesses = result.getSuccesses().size();
                    int numFailures = result.getFailures().size();
                    int numNotFound = 0;
                    updateStats.mContactsDeleteSucceededCount += numSuccesses;
                    if (result.isSuccess()) {
                        if (LogUtil.DEBUG) {
                            Log.v(TAG, numSuccesses
                                    + " documents successfully deleted from AppSearch.");
                        }
                        future.complete(null);
                    } else {
                        AppSearchResult<Void> firstFailure = null;
                        for (AppSearchResult<Void> failedResult : result.getFailures().values()) {
                            // Ignore failures if the error code is AppSearchResult#RESULT_NOT_FOUND
                            // or if shouldKeepUpdatingOnError is true
                            int errorCode = failedResult.getResultCode();
                            if (errorCode == AppSearchResult.RESULT_NOT_FOUND) {
                                numNotFound++;
                            } else if (firstFailure == null
                                    && !shouldKeepUpdatingOnError) {
                                firstFailure = failedResult;
                            }
                            updateStats.mDeleteStatuses.add(errorCode);
                        }
                        updateStats.mContactsDeleteNotFoundCount += numNotFound;
                        if (firstFailure == null) {
                            future.complete(null);
                        } else {
                            Log.w(TAG,
                                    "Failed to delete " + numFailures + " contacts from AppSearch");
                            future.completeExceptionally(
                                    new AppSearchException(firstFailure.getResultCode(),
                                            firstFailure.getErrorMessage()));
                        }
                    }
                }

                @Override
                public void onSystemError(Throwable throwable) {
                    Log.e(TAG, "Failed to delete contacts", throwable);
                    // Log a combined status code; ranges of the codes do not overlap 10100 + 0-99
                    updateStats.mDeleteStatuses.add(
                            ContactsUpdateStats.ERROR_CODE_APP_SEARCH_SYSTEM_ERROR
                                    + AppSearchResult.throwableToFailedResult(
                                    throwable).getResultCode());
                    if (shouldKeepUpdatingOnError) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(throwable);
                    }
                }
            });
            return future;
        });
    }

    /**
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors. When enabled, the
     *                                  returned future completes normally even when contacts could
     *                                  not be retrieved.
     */
    @NonNull
    private CompletableFuture<AppSearchBatchResult> getContactsByIdAsync(
            @NonNull GetByDocumentIdRequest request, boolean shouldKeepUpdatingOnError,
            @NonNull ContactsUpdateStats updateStats) {
        Objects.requireNonNull(request);
        return mAppSearchSessionFuture.thenCompose(appSearchSession -> {
            CompletableFuture<AppSearchBatchResult> future = new CompletableFuture<>();
            appSearchSession.getByDocumentId(request, mExecutor,
                    new BatchResultCallback<>() {
                        @Override
                        public void onResult(AppSearchBatchResult<String, GenericDocument> result) {
                            future.complete(result);
                        }

                        @Override
                        public void onSystemError(Throwable throwable) {
                            Log.e(TAG, "Failed to get contacts", throwable);
                            // Log a combined status code; ranges of the codes do not overlap
                            // 10100 + 0-99
                            updateStats.mUpdateStatuses.add(
                                    ContactsUpdateStats.ERROR_CODE_APP_SEARCH_SYSTEM_ERROR
                                            + AppSearchResult.throwableToFailedResult(
                                            throwable).getResultCode());
                            if (shouldKeepUpdatingOnError) {
                                future.complete(new AppSearchBatchResult.Builder<>().build());
                            } else {
                                future.completeExceptionally(throwable);
                            }
                        }
                    });
            return future;
        });
    }

    /**
     * Returns IDs of all contacts indexed in AppSearch
     *
     * <p>Issues an empty query with an empty projection and pages through all results, collecting
     * the document IDs to return to the caller.
     */
    @NonNull
    public CompletableFuture<List<String>> getAllContactIdsAsync() {
        return mAppSearchSessionFuture.thenCompose(appSearchSession -> {
            SearchSpec allDocumentIdsSpec = new SearchSpec.Builder()
                    .addFilterNamespaces(NAMESPACE_NAME)
                    .addFilterSchemas(Person.SCHEMA_TYPE)
                    .addProjection(Person.SCHEMA_TYPE, /*propertyPaths=*/ Collections.emptyList())
                    .setResultCountPerPage(GET_CONTACT_IDS_PAGE_SIZE)
                    .build();
            SearchResults results =
                    appSearchSession.search(/*queryExpression=*/ "", allDocumentIdsSpec);
            List<String> allContactIds = new ArrayList<>();
            return collectDocumentIdsFromAllPagesAsync(results, allContactIds)
                    .thenCompose(unused -> {
                        results.close();
                        return CompletableFuture.supplyAsync(() -> allContactIds);
                    });
        });
    }

    /**
     * Gets {@link GenericDocument}s with only fingerprints projected for the requested contact ids.
     *
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors.
     * @return A list containing the corresponding {@link GenericDocument} for the requested contact
     *         ids in order. The entry is {@code null} if the requested contact id is not found in
     *         AppSearch.
     */
    @NonNull
    public CompletableFuture<List<GenericDocument>> getContactsWithFingerprintsAsync(
            @NonNull List<String> ids, boolean shouldKeepUpdatingOnError,
            @NonNull ContactsUpdateStats updateStats) {
        Objects.requireNonNull(ids);
        GetByDocumentIdRequest request = new GetByDocumentIdRequest.Builder(
                AppSearchHelper.NAMESPACE_NAME)
                .addProjection(Person.SCHEMA_TYPE,
                        Collections.singletonList(Person.PERSON_PROPERTY_FINGERPRINT))
                .addIds(ids)
                .build();
        return getContactsByIdAsync(request, shouldKeepUpdatingOnError,
                updateStats).thenCompose(appSearchBatchResult -> {
            Map<String, GenericDocument> contactsExistInAppSearch =
                    appSearchBatchResult.getSuccesses();
            List<GenericDocument> docsWithFingerprints = new ArrayList<>(ids.size());
            for (int i = 0; i < ids.size(); ++i) {
                docsWithFingerprints.add(contactsExistInAppSearch.get(ids.get(i)));
            }
            return CompletableFuture.completedFuture(docsWithFingerprints);
        });
    }

    /**
     * Recursively pages through all search results and collects document IDs into given list.
     *
     * @param results    Iterator for paging through the search results.
     * @param contactIds List for collecting and returning document IDs.
     * @return A future indicating if more results might be available.
     */
    private CompletableFuture<Boolean> collectDocumentIdsFromAllPagesAsync(
            @NonNull SearchResults results,
            @NonNull List<String> contactIds) {
        Objects.requireNonNull(results);
        Objects.requireNonNull(contactIds);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        results.getNextPage(mExecutor, callback -> {
            if (!callback.isSuccess()) {
                future.completeExceptionally(new AppSearchException(callback.getResultCode(),
                        callback.getErrorMessage()));
                return;
            }
            List<SearchResult> resultList = callback.getResultValue();
            for (int i = 0; i < resultList.size(); i++) {
                SearchResult result = resultList.get(i);
                contactIds.add(result.getGenericDocument().getId());
            }
            future.complete(!resultList.isEmpty());
        });
        return future.thenCompose(moreResults -> {
            // Recurse if there might be more results to page through.
            if (moreResults) {
                return collectDocumentIdsFromAllPagesAsync(results, contactIds);
            }
            return CompletableFuture.supplyAsync(() -> false);
        });
    }
}
