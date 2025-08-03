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

package com.android.server.appsearch.transformer;

import android.annotation.NonNull;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultPage;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Transforms the retrieved documents in {@link SearchResult} for enterprise access.
 */
public final class EnterpriseSearchResultPageTransformer {

    private EnterpriseSearchResultPageTransformer() {
    }

    /**
     * Transforms a {@link SearchResultPage}, applying enterprise document transformations in the
     * {@link SearchResult}s where necessary.
     */
    @NonNull
    public static SearchResultPage transformSearchResultPage(
            @NonNull SearchResultPage searchResultPage) {
        Objects.requireNonNull(searchResultPage);
        if (!shouldTransformSearchResultPage(searchResultPage)) {
            return searchResultPage;
        }
        List<SearchResult> results = searchResultPage.getResults();
        List<SearchResult> transformedResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            transformedResults.add(transformSearchResult(results.get(i)));
        }
        return new SearchResultPage(searchResultPage.getNextPageToken(), transformedResults);
    }

    /**
     * Transforms a {@link SearchResult} and nested joined {@link SearchResult}s, applying
     * enterprise document transformations where necessary.
     */
    @VisibleForTesting
    @NonNull
    static SearchResult transformSearchResult(@NonNull SearchResult originalResult) {
        Objects.requireNonNull(originalResult);
        boolean shouldTransformDocument = shouldTransformDocument(originalResult.getPackageName(),
                originalResult.getDatabaseName(), originalResult.getGenericDocument());
        boolean shouldTransformJoinedResults = shouldTransformSearchResults(
                originalResult.getJoinedResults());
        // Split the transform check so we can avoid transforming both the original and joined
        // results when only one actually needs to be transformed.
        if (!shouldTransformDocument && !shouldTransformJoinedResults) {
            return originalResult;
        }
        SearchResult.Builder builder = new SearchResult.Builder(originalResult);
        if (shouldTransformDocument) {
            GenericDocument transformedDocument = transformDocument(originalResult.getPackageName(),
                    originalResult.getDatabaseName(), originalResult.getGenericDocument());
            builder.setGenericDocument(transformedDocument);
        }
        if (shouldTransformJoinedResults) {
            List<SearchResult> joinedResults = originalResult.getJoinedResults();
            builder.clearJoinedResults();
            for (int i = 0; i < joinedResults.size(); i++) {
                SearchResult transformedResult = transformSearchResult(joinedResults.get(i));
                builder.addJoinedResult(transformedResult);
            }
        }
        return builder.build();
    }

    /**
     * Transforms the given document specific to its schema type, package, and database or returns
     * the original document if the combination is not recognized.
     */
    @NonNull
    public static GenericDocument transformDocument(@NonNull String packageName,
            @NonNull String databaseName, @NonNull GenericDocument originalDocument) {
        if (PersonEnterpriseTransformer.shouldTransform(packageName, databaseName,
                originalDocument.getSchemaType())) {
            return PersonEnterpriseTransformer.transformDocument(originalDocument);
        }
        return originalDocument;
    }

    /** Checks if we need to transform the {@link SearchResultPage}. */
    private static boolean shouldTransformSearchResultPage(
            @NonNull SearchResultPage searchResultPage) {
        List<SearchResult> results = searchResultPage.getResults();
        for (int i = 0; i < results.size(); i++) {
            if (shouldTransformSearchResult(results.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Checks if we need to transform the {@link SearchResult}. */
    private static boolean shouldTransformSearchResult(@NonNull SearchResult searchResult) {
        return shouldTransformDocument(searchResult.getPackageName(),
                searchResult.getDatabaseName(), searchResult.getGenericDocument())
                || shouldTransformSearchResults(searchResult.getJoinedResults());
    }

    /** Checks if we need to transform the {@link SearchResult}s. */
    private static boolean shouldTransformSearchResults(@NonNull List<SearchResult> searchResults) {
        for (int i = 0; i < searchResults.size(); i++) {
            if (shouldTransformSearchResult(searchResults.get(i))) {
                return true;
            }
        }
        return false;
    }


    /** Checks if we need to transform the {@link GenericDocument}. */
    private static boolean shouldTransformDocument(@NonNull String packageName,
            @NonNull String databaseName, @NonNull GenericDocument document) {
        return PersonEnterpriseTransformer.shouldTransform(packageName, databaseName,
                document.getSchemaType());
    }
}
