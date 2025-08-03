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

package com.android.server.appsearch;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.util.PrefixUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A class for checking if an API should be denied for a given package, package + database, or
 * database name.
 *
 * <p>The denylist is initialized from a denylist string which consists of one or more denylist
 * entries joined by semicolons. For example:
 *
 * <p>"pkg=com.android.appsearch&db=someDatabase&apis=localPutDocuments;pkg=com.android.appsearch
 * &apis=globalSearch"
 *
 * <p>Each entry can only contain the following keys in the format of URL parameters. Unknown keys
 * invalidate the entry in which they're found but do not invalidate other entries.
 * <ul>
 * <li>pkg - a calling package name
 * <li>db - a calling database name
 * <li>apis - a non-empty, comma-separated list of apis to deny
 * </ul>
 *
 * <p>At least one of pkg or db must be specified, and consequently, the listed apis will be
 * denied either by calling package, calling database, or the combination of both. Note that
 * a key that is present without a value (i.e. "pkg&..." or "pkg=&...") is not a missing key. For
 * example,
 * <ul>
 * <li>"pkg=foo&apis=localSetSchema,globalSearch" denies for calling package "foo" and any
 * calling database since db is missing
 * <li>"db=bar&apis=localGetSchema,localGetDocuments" denies for calling database "bar" and
 * any calling package since pkg is missing
 * <li>"pkg=foo&db=bar&apis=localPutDocuments,localSearch" denies only if the calling package is
 * "foo" and the calling database is "bar"
 * <li>"pkg&db=&apis=localReportUsage" denies only if the calling package is "" and the calling
 * database is ""
 * </ul>
 *
 * <p>The full list of apis is:
 * <ul>
 * <li>initialize
 * <li>localSetSchema
 * <li>localPutDocuments
 * <li>globalGetDocuments
 * <li>localGetDocuments
 * <li>localRemoveByDocumentId
 * <li>localRemoveBySearch
 * <li>globalSearch
 * <li>localSearch
 * <li>flush
 * <li>globalGetSchema
 * <li>localGetSchema
 * <li>localGetNamespaces
 * <li>globalGetNextPage
 * <li>localGetNextPage
 * <li>invalidateNextPageToken
 * <li>localWriteSearchResultsToFile
 * <li>localPutDocumentsFromFile
 * <li>localSearchSuggestion
 * <li>globalReportUsage
 * <li>localReportUsage
 * <li>localGetStorageInfo
 * <li>globalRegisterObserverCallback
 * <li>globalUnregisterObserverCallback
 * </ul>
 *
 * <p>Note, the denylist string is case-sensitive, and whitespace is not trimmed during parsing.
 * Ampersand (&), equals (=), and semicolon (;) must be url-encoded when used in a value as %26,
 * %3D, and %3B respectively.
 *
 * <p>See go/appsearch-denylist
 */
public final class Denylist {
    public static final Denylist EMPTY_INSTANCE = new Denylist();

    private static final String TAG = "AppSearchDenylist";
    // These delimiters are not special regex characters and can be used in String#split as is.
    // Only these characters must be url-encoded when not used as separators in the denylist string.
    private static final String ENTRY_DELIMITER = ";";
    private static final String VALUE_DELIMITER = ",";
    private static final String QUERY_PREFIX = "?";

    // These keys are case-sensitive. If we see any unknown key, the entry will be dropped.
    private static final String KEY_PACKAGE = "pkg";
    private static final String KEY_DATABASE = "db";
    private static final String KEY_APIS = "apis";
    private static final Set<String> KNOWN_KEYS = new ArraySet<>(
            Arrays.asList(KEY_PACKAGE, KEY_DATABASE, KEY_APIS));

    private final Map<String, Set<Integer>> deniedPackages = new ArrayMap<>();
    private final Map<String, Set<Integer>> deniedDatabases = new ArrayMap<>();
    private final Map<String, Set<Integer>> deniedPrefixes = new ArrayMap<>();

    private Denylist() {
    }

    /**
     * Creates an instance of {@link Denylist}.
     */
    @NonNull
    public static Denylist create(@NonNull String denylistString) {
        Objects.requireNonNull(denylistString);
        Denylist denylist = new Denylist();
        denylist.initialize(denylistString);
        return denylist;
    }

    /**
     * Initializes the {@link Denylist}.
     *
     * @param denylistString A string representing a denylist with url-encoded parameters.
     */
    private void initialize(@NonNull String denylistString) {
        String[] entries = denylistString.split(ENTRY_DELIMITER);
        for (int i = 0; i < entries.length; ++i) {
            String entry = entries[i];
            // Prepend with '?' to parse entry as url query parameters
            Uri uri = Uri.parse(QUERY_PREFIX + entry);
            Set<String> keys = uri.getQueryParameterNames();
            if (!KNOWN_KEYS.containsAll(keys)) {
                Log.e(TAG, "An unknown key(s) was found in this entry: " + entry);
                continue;
            }
            String packageName = uri.getQueryParameter(KEY_PACKAGE);
            String databaseName = uri.getQueryParameter(KEY_DATABASE);
            if (packageName == null && databaseName == null) {
                Log.e(TAG, "The parameters 'pkg' and 'db' were both missing for this entry: "
                        + entry);
                continue;
            }
            if (!keys.contains(KEY_APIS)) {
                Log.e(TAG, "The parameter 'apis' was missing for this entry: " + entry);
                continue;
            }
            String[] apis = uri.getQueryParameter(KEY_APIS).split(VALUE_DELIMITER);
            Set<Integer> apiTypes = retrieveApiTypes(apis);
            if (apiTypes.isEmpty()) {
                Log.e(TAG, "There were no valid api types for this entry: " + entry);
                continue;
            }
            addEntry(packageName, databaseName, apiTypes);
        }
    }

    @NonNull
    private Set<Integer> retrieveApiTypes(@NonNull String[] apis) {
        Set<Integer> apiTypes = new ArraySet<>(apis.length);
        for (int i = 0; i < apis.length; ++i) {
            @CallStats.CallType int apiType = CallStats.getApiCallTypeFromName(apis[i]);
            if (apiType != CallStats.CALL_TYPE_UNKNOWN) {
                apiTypes.add(apiType);
            }
        }
        return apiTypes;
    }

    private void addEntry(@Nullable String packageName, @Nullable String databaseName,
            @NonNull Set<Integer> apiTypes) {
        if (packageName != null && databaseName != null) {
            String prefix = PrefixUtil.createPrefix(packageName, databaseName);
            Set<Integer> deniedApiTypes =
                    deniedPrefixes.computeIfAbsent(prefix, k -> new ArraySet<>());
            deniedApiTypes.addAll(apiTypes);
        } else if (packageName != null) {
            Set<Integer> deniedApiTypes = deniedPackages.computeIfAbsent(packageName,
                    k -> new ArraySet<>());
            deniedApiTypes.addAll(apiTypes);
        } else if (databaseName != null) {
            Set<Integer> deniedApiTypes = deniedDatabases.computeIfAbsent(databaseName,
                    k -> new ArraySet<>());
            deniedApiTypes.addAll(apiTypes);
        }
    }

    /**
     * Checks whether the specified api is denied for the given package-database pair under any of
     * the denylist rules.
     *
     * @param packageName the name of the calling package.
     * @param databaseName the name of the target database.
     * @param apiType the api type to check for denial.
     * @return true if the api is denied for the given package-database pair.
     */
    public boolean checkDeniedPackageDatabase(@NonNull String packageName,
            @NonNull String databaseName, @CallStats.CallType int apiType) {
        if (checkDeniedPackage(packageName, apiType) || checkDeniedDatabase(databaseName,
                apiType)) {
            return true;
        }
        if (deniedPrefixes.isEmpty()) {
            return false;
        }
        String prefix = PrefixUtil.createPrefix(packageName, databaseName);
        Set<Integer> deniedApiTypes = deniedPrefixes.get(prefix);
        return deniedApiTypes != null && deniedApiTypes.contains(apiType);
    }

    /**
     * Checks whether the specified api is denied for the given package name under any of the
     * denylist rules.
     *
     * @param packageName the name of the calling package.
     * @param apiType the api type to check for denial.
     * @return true if the api is denied for the given package name.
     */
    public boolean checkDeniedPackage(@NonNull String packageName,
            @CallStats.CallType int apiType) {
        Set<Integer> deniedApiTypes = deniedPackages.get(packageName);
        return deniedApiTypes != null && deniedApiTypes.contains(apiType);
    }

    /**
     * Checks whether the specified api is denied for the given database name under any of the
     * denylist rules.
     *
     * @param databaseName the name of the target database.
     * @param apiType the api type to check for denial.
     * @return true if the api is denied for the given database name.
     */
    private boolean checkDeniedDatabase(@NonNull String databaseName,
            @CallStats.CallType int apiType) {
        Set<Integer> deniedApiTypes = deniedDatabases.get(databaseName);
        return deniedApiTypes != null && deniedApiTypes.contains(apiType);
    }
}
