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

package com.android.adservices.service.appsearch;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.app.GlobalSearchSession;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This class represents the data access object for the Topics that the user opts out of. By default
 * all topics are opted in.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchTopicsConsentDao extends AppSearchDao {
    /**
     * Identifier of the Consent Document; must be unique within the Document's `namespace`. This is
     * the row ID for consent data. It is the same as userId, but we store userId separately so that
     * this DAO can be extended if needed in the future.
     */
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /** Namespace of the Topics Document. Used to group documents during querying or deletion. */
    @Document.Namespace private final String mNamespace;

    /** List of Topics that the user has opted out of. */
    @Document.LongProperty private List<Integer> mBlockedTopics = new ArrayList<>();

    /** The taxonomy versions of Topics that the user has opted out of. */
    @Document.LongProperty private List<Long> mBlockedTopicsTaxonomyVersions = new ArrayList<>();

    /** The model versions of Topics that the user has opted out of. */
    @Document.LongProperty private List<Long> mBlockedTopicsModelVersions = new ArrayList<>();

    // Column name used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";
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
    AppSearchTopicsConsentDao(
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
     * @return nameespace
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

    /** Returns the row ID that should be unique for the consent namespace. */
    @NonNull
    public static String getRowId(@NonNull String uid) {
        return uid;
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

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mUserId,
                mNamespace,
                mBlockedTopics,
                mBlockedTopicsModelVersions,
                mBlockedTopicsModelVersions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchTopicsConsentDao)) return false;
        AppSearchTopicsConsentDao obj = (AppSearchTopicsConsentDao) o;
        return (Objects.equals(this.mId, obj.getId()))
                && (Objects.equals(this.mUserId, obj.getUserId()))
                && (Objects.equals(this.getBlockedTopics(), obj.getBlockedTopics()))
                && (Objects.equals(this.mNamespace, obj.getNamespace()))
                && (Objects.equals(
                        this.getBlockedTopicsTaxonomyVersions(),
                        obj.getBlockedTopicsTaxonomyVersions()))
                && (Objects.equals(
                        this.getBlockedTopicsModelVersions(), obj.getBlockedTopicsModelVersions()));
    }

    /** Reads the topics consent data for this user. */
    public static AppSearchTopicsConsentDao readConsentData(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);
        return readConsentData(
                AppSearchTopicsConsentDao.class,
                searchSession,
                executor,
                NAMESPACE,
                getQuery(userId),
                adServicesPackageName);
    }

    /** Adds a blocked topic to the list of blocked topics. */
    public void addBlockedTopic(@NonNull Topic topic) {
        Objects.requireNonNull(topic);

        // Only add the topic if it doesn't exist.
        for (int i = 0; i < mBlockedTopics.size(); i++) {
            if (mBlockedTopics.get(i).equals(topic.getTopic())
                    && mBlockedTopicsTaxonomyVersions.get(i).equals(topic.getTaxonomyVersion())
                    && mBlockedTopicsModelVersions.get(i).equals(topic.getModelVersion())) {
                return;
            }
        }
        mBlockedTopics.add(topic.getTopic());
        mBlockedTopicsTaxonomyVersions.add(topic.getTaxonomyVersion());
        mBlockedTopicsModelVersions.add(topic.getModelVersion());
    }

    /** Removes a blocked topic from the list of blocked topics. */
    public void removeBlockedTopic(@NonNull Topic topic) {
        Objects.requireNonNull(topic);
        mBlockedTopics = mBlockedTopics == null ? List.of() : mBlockedTopics;
        if (!mBlockedTopics.contains(topic.getTopic())) {
            return;
        }
        mBlockedTopicsTaxonomyVersions =
                mBlockedTopicsTaxonomyVersions == null ? List.of() : mBlockedTopicsTaxonomyVersions;
        mBlockedTopicsModelVersions =
                mBlockedTopicsModelVersions == null ? List.of() : mBlockedTopicsModelVersions;
        // AppSearch does not support Maps, so we associate the IDs via the index of each element.
        // We delete the entries in the stored lists that correspond to the given topic along all
        // three dimensions - topic ID, taxonomy version and model version because it is safer to
        // not assume that topic IDs will not repeat with new taxonomy or model versions.
        int indexToRemove = -1;
        for (int i = 0; i < mBlockedTopics.size(); i++) {
            if (mBlockedTopics.get(i).equals(topic.getTopic())
                    && mBlockedTopicsTaxonomyVersions.get(i).equals(topic.getTaxonomyVersion())
                    && mBlockedTopicsModelVersions.get(i).equals(topic.getModelVersion())) {
                indexToRemove = i;
            }
        }
        mBlockedTopics.remove(indexToRemove);
        mBlockedTopicsTaxonomyVersions.remove(indexToRemove);
        mBlockedTopicsModelVersions.remove(indexToRemove);
    }

    /**
     * Read the Topics consent data from AppSearch.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @return list of blocked topics.
     */
    public static List<Topic> getBlockedTopics(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        String query = getQuery(userId);
        AppSearchTopicsConsentDao dao =
                AppSearchDao.readConsentData(
                        AppSearchTopicsConsentDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch topics data read: " + dao + " [ query: " + query + "]");
        if (dao == null) {
            return List.of();
        }
        return convertToTopics(dao);
    }

    @NonNull
    private static List<Topic> convertToTopics(AppSearchTopicsConsentDao dao) {
        if (dao == null || dao.getBlockedTopics() == null) {
            return List.of();
        }
        if (dao.getBlockedTopics().size() != dao.getBlockedTopicsTaxonomyVersions().size()
                || dao.getBlockedTopics().size() != dao.getBlockedTopicsModelVersions().size()) {
            LogUtil.e("Incorrect blocked topics data stored in AppSearch");
            return List.of();
        }
        List<Topic> result = new ArrayList<>();
        for (int i = 0; i < dao.getBlockedTopics().size(); ++i) {
            result.add(
                    Topic.create(
                            dao.getBlockedTopics().get(i),
                            dao.getBlockedTopicsTaxonomyVersions().get(i),
                            dao.getBlockedTopicsModelVersions().get(i)));
        }
        return result;
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    static String getQuery(String userId) {
        return USER_ID_COLNAME + ":" + userId;
    }
}
