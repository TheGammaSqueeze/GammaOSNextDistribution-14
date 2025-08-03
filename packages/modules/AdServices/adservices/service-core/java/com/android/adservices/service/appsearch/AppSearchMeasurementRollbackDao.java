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
import android.app.adservices.AdServicesManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.app.AppSearchSession;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This class represents the data access object for the information stored in AppSearch regarding
 * whether the user deleted some data from the measurement db.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchMeasurementRollbackDao extends AppSearchDao {
    static final String NAMESPACE = "measurementRollback";

    private static final String ROW_ID_PREFIX = "Measurement_Rollback_";

    // Column name used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";

    /**
     * Identifier of the Rollback Document; must be unique within the Document's `namespace`. This
     * is the row ID for measurement rollback data, and is a combination of user ID and deletion api
     * type. Since we might want to change it later, we're not using it directly.
     */
    @Document.Id private final String mId;

    @Document.Namespace private final String mNamespace;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    @Document.LongProperty private final long mApexVersion;

    AppSearchMeasurementRollbackDao(
            @NonNull String id,
            @NonNull String namespace,
            @NonNull String userId,
            long apexVersion) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(userId);

        mId = id;
        mNamespace = namespace;
        mUserId = userId;
        mApexVersion = apexVersion;
    }

    /**
     * Get the row id for this row
     *
     * @return the row id for this row
     */
    public String getId() {
        return mId;
    }

    /**
     * Return the apex version stored in the row
     *
     * @return the stored apex version
     */
    public Long getApexVersion() {
        return mApexVersion;
    }

    /**
     * Return the namespace that this document belongs to
     *
     * @return the namespace of the document
     */
    public String getNamespace() {
        return mNamespace;
    }

    /**
     * Return the id of the user that created this row
     *
     * @return the user id
     */
    public String getUserId() {
        return mUserId;
    }

    /**
     * Converts the DAO to a string.
     *
     * @return string representing the DAO.
     */
    public String toString() {
        return "id="
                + mId
                + "; userId="
                + mUserId
                + "; namespace="
                + mNamespace
                + "; apexVersion="
                + mApexVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mUserId, mNamespace, mApexVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchMeasurementRollbackDao)) return false;
        AppSearchMeasurementRollbackDao obj = (AppSearchMeasurementRollbackDao) o;
        return (Objects.equals(this.mId, obj.getId()))
                && (Objects.equals(this.mUserId, obj.getUserId()))
                && (Objects.equals(this.mNamespace, obj.getNamespace()))
                && (Objects.equals(this.mApexVersion, obj.getApexVersion()));
    }

    /**
     * Return the row id based on the user id and the deletion type
     *
     * @param userId the id of the user creating the row
     * @param deletionApiType the deletion api type
     * @return the row id of the document
     */
    public static String getRowId(
            @NonNull String userId, @AdServicesManager.DeletionApiType int deletionApiType) {
        Objects.requireNonNull(userId);
        return ROW_ID_PREFIX + userId + "_" + deletionApiType;
    }

    /**
     * Queries AppSearch for the document storing this user id.
     *
     * @return the document cast into an object of {@link AppSearchMeasurementRollbackDao} if found,
     *     null otherwise.
     */
    static AppSearchMeasurementRollbackDao readDocument(
            @NonNull ListenableFuture<AppSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        if (userId.isEmpty()) {
            return null;
        }

        String query = getQuery(userId);
        return AppSearchDao.readAppSearchSessionData(
                AppSearchMeasurementRollbackDao.class,
                searchSession,
                executor,
                NAMESPACE,
                query,
                adServicesPackageName);
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    @VisibleForTesting
    static String getQuery(String userId) {
        return USER_ID_COLNAME + ":" + userId;
    }
}
