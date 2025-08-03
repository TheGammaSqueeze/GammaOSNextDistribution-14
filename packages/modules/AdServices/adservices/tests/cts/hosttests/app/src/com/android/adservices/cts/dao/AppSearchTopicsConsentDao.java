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

package com.android.adservices.cts.dao;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class represents the data access object for the Topics that the user opts out of. By
 * default, all topics are opted in.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Document
public class AppSearchTopicsConsentDao {
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /** Namespace of the Topics Document. Used to group documents during querying or deletion. */
    @Document.Namespace private final String mNamespace;

    /** List of Topics that the user has opted out of. */
    @Document.LongProperty private final List<Integer> mBlockedTopics = new ArrayList<>();

    /** The taxonomy versions of Topics that the user has opted out of. */
    @Document.LongProperty
    private final List<Long> mBlockedTopicsTaxonomyVersions = new ArrayList<>();

    /** The model versions of Topics that the user has opted out of. */
    @Document.LongProperty private final List<Long> mBlockedTopicsModelVersions = new ArrayList<>();

    public static final String NAMESPACE = "blockedTopics";

    /**
     * Create an AppSearchTopicsConsentDao instance.
     *
     * @param id is the user ID for this user
     * @param userId is the user ID for this user
     * @param namespace (required by AppSearch)
     * @param blockedTopics list of blockedTopics by ID
     * @param blockedTopicsTaxonomyVersions list of taxonomy versions for the blocked topics
     * @param blockedTopicsModelVersions list of model versions for the blocked topics
     */
    public AppSearchTopicsConsentDao(
            @NonNull String id,
            @NonNull String userId,
            @NonNull String namespace,
            @Nullable List<Integer> blockedTopics,
            @Nullable List<Long> blockedTopicsTaxonomyVersions,
            @Nullable List<Long> blockedTopicsModelVersions) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        mBlockedTopics.addAll(blockedTopics != null ? blockedTopics : List.of());
        mBlockedTopicsTaxonomyVersions.addAll(
                blockedTopicsTaxonomyVersions != null ? blockedTopicsTaxonomyVersions : List.of());
        mBlockedTopicsModelVersions.addAll(
                blockedTopicsModelVersions != null ? blockedTopicsModelVersions : List.of());
    }

    /**
     * Get the row ID for this row.
     *
     * @return ID
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Get the user ID for this row.
     *
     * @return user ID
     */
    @NonNull
    public String getUserId() {
        return mUserId;
    }

    /**
     * Get the namespace for this row.
     *
     * @return namespace
     */
    @NonNull
    public String getNamespace() {
        return mNamespace;
    }

    /**
     * Get the list of blocked topics (by topic ID).
     *
     * @return blockedTopics
     */
    @NonNull
    public List<Integer> getBlockedTopics() {
        return mBlockedTopics;
    }

    /**
     * Get the list of taxonomy versions for blocked topics.
     *
     * @return blockedTopicsTaxonomyVersions
     */
    @NonNull
    public List<Long> getBlockedTopicsTaxonomyVersions() {
        return mBlockedTopicsTaxonomyVersions;
    }

    /**
     * Get the list of model versions for blocked topics.
     *
     * @return blockedTopicsModelVersions
     */
    @NonNull
    public List<Long> getBlockedTopicsModelVersions() {
        return mBlockedTopicsModelVersions;
    }

    /**
     * Converts the DAO to a string.
     *
     * @return string representing the DAO.
     */
    @NonNull
    public String toString() {
        String blockedTopics = Arrays.toString(mBlockedTopics.toArray());
        String blockedTopicsTaxonomyVersions =
                Arrays.toString(mBlockedTopicsTaxonomyVersions.toArray());
        String blockedTopicsModelVersions = Arrays.toString(mBlockedTopicsModelVersions.toArray());
        return "id="
                + mId
                + "; userId="
                + mUserId
                + "; namespace="
                + mNamespace
                + "; blockedTopics="
                + blockedTopics
                + "; blockedTopicsTaxonomyVersions="
                + blockedTopicsTaxonomyVersions
                + "; blockedTopicsModelVersions="
                + blockedTopicsModelVersions;
    }
}
