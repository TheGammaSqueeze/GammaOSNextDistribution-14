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
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.external.localstorage.stats.CallStats;

import java.util.Map;
import java.util.Objects;

/**
 * Class containing configs for AppSearch task queue's rate limit.
 *
 * <p>Task queue total capacity is the total cost of tasks that AppSearch can accept onto its task
 * queue from all packages. This is configured with an integer value.
 *
 * <p>Task queue per-package capacity is the total cost of tasks that AppSearch can accept onto its
 * task queue from a single calling package. This config is passed in as a percentage of the total
 * capacity.
 *
 * <p>Each AppSearch API call has an associated integer cost that is configured by the API costs
 * string. API costs must be positive.
 * The API costs string uses API_ENTRY_DELIMITER (';') to separate API entries and has a string API
 * name followed by API_COST_DELIMITER (':') and the integer cost to define each entry.
 * If an API's cost is not specified in the string, its cost is set to DEFAULT_API_COST.
 * e.g. A valid API cost string: "putDocument:5;query:1;setSchema:10".
 *
 * <p>If an API call has a higher cost, this means that the API consumes more of the task queue
 * budget and fewer number of tasks can be placed on the task queue.
 * An incoming API call from a calling package is dropped when the rate limit is exceeded, which
 * happens when either:
 * 1. Total cost of all API calls currently on the task queue + cost of incoming API call >
 * task queue total capacity. OR
 * 2. Total cost of all API calls currently on the task queue from the calling package +
 * cost of incoming API call > task queue per-package capacity.
 */
public final class AppSearchRateLimitConfig {
    @VisibleForTesting
    public static final int DEFAULT_API_COST = 1;

    /**
     * Creates an instance of {@link AppSearchRateLimitConfig}.
     *
     * @param totalCapacity                configures total cost of tasks that AppSearch can accept
     *                                     onto its task queue from all packages.
     * @param perPackageCapacityPercentage configures total cost of tasks that AppSearch can accept
     *                                     onto its task queue from a single calling package, as a
     *                                     percentage of totalCapacity.
     * @param apiCostsString               configures costs for each {@link CallStats.CallType}. The
     *                                     string should use API_ENTRY_DELIMITER (';') to separate
     *                                     entries, with each entry defined by the string API name
     *                                     followed by API_COST_DELIMITER (':').
     *                                     e.g. "putDocument:5;query:1;setSchema:10"
     */
    public static AppSearchRateLimitConfig create(int totalCapacity,
            float perPackageCapacityPercentage, @NonNull String apiCostsString) {
        Objects.requireNonNull(apiCostsString);
        Map<Integer, Integer> apiCostsMap = createApiCostsMap(apiCostsString);
        return new AppSearchRateLimitConfig(totalCapacity, perPackageCapacityPercentage,
                apiCostsString, apiCostsMap);
    }

    // Truncated as logging tag is allowed to be at most 23 characters.
    private static final String TAG = "AppSearchRateLimitConfi";

    private static final String API_ENTRY_DELIMITER = ";";
    private static final String API_COST_DELIMITER = ":";

    private final int mTaskQueueTotalCapacity;
    private final int mTaskQueuePerPackageCapacity;
    private final String mApiCostsString;
    // Mapping of @CallStats.CallType -> cost
    private final Map<Integer, Integer> mTaskQueueApiCosts;

    private AppSearchRateLimitConfig(int totalCapacity, float perPackageCapacityPercentage,
            @NonNull String apiCostsString, @NonNull Map<Integer, Integer> apiCostsMap) {
        mTaskQueueTotalCapacity = totalCapacity;
        mTaskQueuePerPackageCapacity = (int) (totalCapacity * perPackageCapacityPercentage);
        mApiCostsString = Objects.requireNonNull(apiCostsString);
        mTaskQueueApiCosts = Objects.requireNonNull(apiCostsMap);
    }

    /**
     * Returns an AppSearchRateLimitConfig instance given the input capacities and ApiCosts.
     * This may be the same instance if there are no changes in these configs.
     *
     * @param totalCapacity                configures total cost of tasks that AppSearch can accept
     *                                     onto its task queue from all packages.
     * @param perPackageCapacityPercentage configures total cost of tasks that AppSearch can accept
     *                                     onto its task queue from a single calling package, as a
     *                                     percentage of totalCapacity.
     * @param apiCostsString               configures costs for each {@link CallStats.CallType}. The
     *                                     string should use API_ENTRY_DELIMITER (';') to separate
     *                                     entries, with each entry defined by the string API name
     *                                     followed by API_COST_DELIMITER (':').
     *                                     e.g. "putDocument:5;query:1;setSchema:10"
     */
    public AppSearchRateLimitConfig rebuildIfNecessary(int totalCapacity,
            float perPackageCapacityPercentage, @NonNull String apiCostsString) {
        int perPackageCapacity = (int) (totalCapacity * perPackageCapacityPercentage);
        if (totalCapacity != mTaskQueueTotalCapacity
                || perPackageCapacity != mTaskQueuePerPackageCapacity
                || !Objects.equals(apiCostsString, mApiCostsString)) {
            return AppSearchRateLimitConfig.create(totalCapacity, perPackageCapacityPercentage,
                    apiCostsString);
        }
        return this;
    }

    /**
     * Returns the task queue total capacity.
     */
    public int getTaskQueueTotalCapacity() {
        return mTaskQueueTotalCapacity;
    }


    /**
     * Returns the per-package task queue capacity.
     */
    public int getTaskQueuePerPackageCapacity() {
        return mTaskQueuePerPackageCapacity;
    }


    /**
     * Returns the cost of an API type.
     *
     * <p>The range of the cost should be [0, taskQueueTotalCapacity]. Default API cost of 1 will be
     * returned if the cost has not been configured for an API call.
     */
    public int getApiCost(@CallStats.CallType int apiType) {
        return mTaskQueueApiCosts.getOrDefault(apiType, DEFAULT_API_COST);
    }

    /**
     * Returns an API costs map based on apiCostsString.
     */
    private static Map<Integer, Integer> createApiCostsMap(@NonNull String apiCostsString) {
        if (TextUtils.getTrimmedLength(apiCostsString) == 0) {
            return new ArrayMap<>();
        }
        String[] entries = apiCostsString.split(API_ENTRY_DELIMITER);
        Map<Integer, Integer> apiCostsMap = new ArrayMap<>(entries.length);
        for (int i = 0; i < entries.length; ++i) {
            String entry = entries[i];
            int costDelimiterIndex = entry.indexOf(API_COST_DELIMITER);
            if (costDelimiterIndex < 0 || costDelimiterIndex >= entry.length() - 1) {
                Log.e(TAG, "No cost specified in entry: " + entry);
                continue;
            }
            String apiName = entry.substring(0, costDelimiterIndex);
            int apiCost;
            try {
                apiCost = Integer.parseInt(entry, costDelimiterIndex + 1,
                        entry.length(), /* radix= */10);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid cost for API cost entry: " + entry);
                continue;
            }
            if (apiCost < 0) {
                Log.e(TAG, "API cost must be positive. Invalid entry: " + entry);
                continue;
            }
            @CallStats.CallType int apiType = CallStats.getApiCallTypeFromName(apiName);
            if (apiType == CallStats.CALL_TYPE_UNKNOWN) {
                Log.e(TAG, "Invalid API name for entry: " + entry);
                continue;
            }
            apiCostsMap.put(apiType, apiCost);
        }
        return apiCostsMap;
    }
}
