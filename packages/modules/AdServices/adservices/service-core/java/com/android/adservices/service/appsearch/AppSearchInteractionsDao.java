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
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.app.GlobalSearchSession;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This class represents the data access object for the manual interactions recorded for the
 * notification shown to the user and the feature type.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchInteractionsDao extends AppSearchDao {
    enum ManualInteractions {
        NO_MANUAL_INTERACTIONS_RECORDED,
        UNKNOWN,
        MANUAL_INTERACTIONS_RECORDED,
    }
    /**
     * Identifier of the Consent Document; must be unique within the Document's `namespace`. This is
     * the row ID for consent data. It is a combination of user ID and api type.
     */
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /**
     * Namespace of the Notification Document. Used to group documents during querying or deletion.
     */
    @Document.Namespace private final String mNamespace;

    /**
     * API type for this row. Possible values are: PRIVACY_SANDBOX_FEATURE_TYPE and INTERACTIONS.
     */
    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mApiType;

    /**
     * Value for this row. This corresponds to the feature or interactions recorded for this user.
     * If the apiType is PRIVACY_SANDBOX_FEATURE_TYPE, the possible values are as per {@link
     * com.android.adservices.service.common.feature.PrivacySandboxFeatureType}. If the apiType is
     * INTERACTIONS, the possible values are as per {@link ManualInteractions}.
     */
    @Document.LongProperty private final int mValue;

    // Column names used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";
    private static final String API_TYPE_COLNAME = "apiType";
    public static final String NAMESPACE = "interactions";
    public static final String API_TYPE_PRIVACY_SANDBOX_FEATURE = "FEATURE_TYPE";
    public static final String API_TYPE_INTERACTIONS = "INTERACTIONS";

    /**
     * Create an AppSearchInteractionsDao instance.
     *
     * @param id is a combination of the user ID and apiType
     * @param userId is the user ID for this user
     * @param namespace (required by AppSearch)
     * @param apiType the apiType
     * @param value the value of the type of information we store in this row
     */
    AppSearchInteractionsDao(
            String id, String userId, String namespace, String apiType, int value) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mApiType = apiType;
        this.mValue = value;
    }

    /**
     * Get the row ID for this row.
     *
     * @return ID
     */
    public String getId() {
        return mId;
    }

    /**
     * Get the user ID for this row.
     *
     * @return user ID
     */
    public String getUserId() {
        return mUserId;
    }

    /**
     * Get the namespace for this row.
     *
     * @return nameespace
     */
    public String getNamespace() {
        return mNamespace;
    }

    /**
     * Get the apiType for this row.
     *
     * @return apiType
     */
    public String getApiType() {
        return mApiType;
    }

    /** Get the value for this apiType. */
    public int getValue() {
        return mValue;
    }

    /** Returns the row ID that should be unique for the consent namespace. */
    public static String getRowId(@NonNull String uid, @NonNull String apiType) {
        return uid + "_" + apiType;
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
                + "; apiType="
                + mApiType
                + "; value="
                + mValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mUserId, mNamespace, mApiType, mValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchInteractionsDao)) return false;
        AppSearchInteractionsDao obj = (AppSearchInteractionsDao) o;
        return (Objects.equals(this.mId, obj.getId()))
                && (Objects.equals(this.mUserId, obj.getUserId()))
                && (Objects.equals(this.mApiType, obj.getApiType()))
                && (Objects.equals(this.mNamespace, obj.getNamespace()))
                && (Objects.equals(this.mValue, obj.getValue()));
    }

    /**
     * Get the Privacy Sandbox feature type.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @return the value for this apiType.
     */
    public static PrivacySandboxFeatureType getPrivacySandboxFeatureType(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        String query = getQuery(userId, API_TYPE_PRIVACY_SANDBOX_FEATURE);
        AppSearchInteractionsDao dao =
                AppSearchDao.readConsentData(
                        AppSearchInteractionsDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch interactions data read: " + dao + " [ query: " + query + "]");
        if (dao == null) {
            return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
        }
        return PrivacySandboxFeatureType.values()[dao.getValue()];
    }

    /**
     * Get the manual interactions recorded for this user.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @return the value for this apiType.
     */
    public static @ConsentManager.UserManualInteraction int getManualInteractions(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        String query = getQuery(userId, API_TYPE_INTERACTIONS);
        AppSearchInteractionsDao dao =
                AppSearchDao.readConsentData(
                        AppSearchInteractionsDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch interactions data read: " + dao + " [ query: " + query + "]");
        if (dao == null) {
            return ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
        }
        return dao.getValue();
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    @VisibleForTesting
    static String getQuery(String userId, String apiType) {
        return USER_ID_COLNAME + ":" + userId + " " + API_TYPE_COLNAME + ":" + apiType;
    }
}
