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
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/** This class represents the data access object for the consent data written to AppSearch. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchConsentDao extends AppSearchDao {
    /**
     * Identifier of the Consent Document; must be unique within the Document's `namespace`. This is
     * the row ID for consent data. It is a combination of user ID and api type.
     */
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /** Namespace of the Consent Document. Used to group documents during querying or deletion. */
    @Document.Namespace private final String mNamespace;

    /**
     * API type for this consent. Possible values are a) CONSENT, CONSENT-FLEDGE,
     * CONSENT-MEASUREMENT, CONSENT-TOPICS, b) DEFAULT_CONSENT, TOPICS_DEFAULT_CONSENT,
     * FLEDGE_DEFAULT_CONSENT, MEASUREMENT_DEFAULT_CONSENT and c) DEFAULT_AD_ID_STATE.
     */
    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mApiType;

    /** Consent bit for this user for this API type. */
    @Document.StringProperty private final String mConsent;

    // Column names used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";
    private static final String API_TYPE_COLNAME = "apiType";
    public static final String NAMESPACE = "consent";

    /**
     * Create an AppSearchConsentDao instance.
     *
     * @param id is a combination of the user ID and apiType
     * @param userId is the user ID for this user
     * @param namespace (required by AppSearch)
     * @param apiType is the apiType for which we are storing consent data
     * @param consent whether consent is granted
     */
    AppSearchConsentDao(
            String id, String userId, String namespace, String apiType, String consent) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mApiType = apiType;
        this.mConsent = consent;
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

    /**
     * Get whether consent is granted for this row.
     *
     * @return consented
     */
    public String getConsent() {
        return mConsent;
    }

    /** @return whether consent is granted for this row. */
    public boolean isConsented() {
        return mConsent.equals("true");
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
                + "; apiType="
                + mApiType
                + "; namespace="
                + mNamespace
                + "; consent="
                + mConsent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mUserId, mNamespace, mConsent, mApiType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchConsentDao)) return false;
        AppSearchConsentDao obj = (AppSearchConsentDao) o;
        return (Objects.equals(this.mId, obj.mId))
                && (Objects.equals(this.mUserId, obj.mUserId))
                && (Objects.equals(this.mApiType, obj.mApiType))
                && (Objects.equals(this.mNamespace, obj.mNamespace))
                && (Objects.equals(this.mConsent, obj.mConsent));
    }

    /**
     * Read the consent data from AppSearch.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @param apiType the API type for the query.
     * @return whether the row is consented for this user ID and apiType.
     */
    static boolean readConsentData(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String apiType,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(apiType);

        String query = getQuery(userId, apiType);
        AppSearchConsentDao dao =
                AppSearchDao.readConsentData(
                        AppSearchConsentDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch app consent data read: " + dao + " [ query: " + query + "]");
        if (dao == null) {
            return false;
        }
        return dao.isConsented();
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    @VisibleForTesting
    static String getQuery(String userId, String apiType) {
        return USER_ID_COLNAME + ":" + userId + " " + API_TYPE_COLNAME + ":" + apiType;
    }
}
