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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** This class represents the data access object for the app consent data written to AppSearch. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchAppConsentDao extends AppSearchDao {
    /**
     * Identifier of the Consent Document; must be unique within the Document's `namespace`. This is
     * the row ID for consent data. It is a combination of user ID and consent type.
     */
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /** Namespace of the Consent Document. Used to group documents during querying or deletion. */
    @Document.Namespace private final String mNamespace;

    /**
     * Consent type for this table. Possible values are: APPS_WITH_CONSENT,
     * APPS_WITH_REVOKED_CONSENT, APPS_WITH_FLEDGE_CONSENT and APPS_WITH_FLEDGE_REVOKED_CONSENT.
     */
    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mConsentType;

    /** List of apps. */
    @Document.StringProperty private List<String> mApps;

    // Column names used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";
    private static final String CONSENT_TYPE_COLNAME = "consentType";
    public static final String NAMESPACE = "appConsent";

    // Consent types we store with this DAO.
    public static final String APPS_WITH_CONSENT = "APPS_WITH_CONSENT";
    public static final String APPS_WITH_REVOKED_CONSENT = "APPS_WITH_REVOKED_CONSENT";

    /**
     * Create an AppSearchConsentDao instance.
     *
     * @param id is a combination of the user ID and apiType
     * @param userId is the user ID for this user
     * @param namespace (required by AppSearch)
     * @param consentType is the consentType for which we are storing consent data
     * @param apps list of apps
     */
    AppSearchAppConsentDao(
            String id, String userId, String namespace, String consentType, List<String> apps) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mConsentType = consentType;
        this.mApps = apps;
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
    public String getConsentType() {
        return mConsentType;
    }

    /**
     * Gets the list of apps.
     *
     * @return List of app package names.
     */
    public List<String> getApps() {
        return mApps;
    }

    /** Sets the apps. */
    public void setApps(List<String> apps) {
        mApps = apps;
    }

    /** Returns the row ID that should be unique for the consent namespace. */
    public static String getRowId(@NonNull String uid, @NonNull String consentType) {
        Objects.requireNonNull(uid);
        Objects.requireNonNull(consentType);
        return uid + "_" + consentType;
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
                + "; consentType="
                + mConsentType
                + "; namespace="
                + mNamespace
                + "; apps="
                + (mApps == null ? "null" : Arrays.toString(mApps.toArray()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mUserId, mNamespace, mApps, mConsentType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchAppConsentDao)) return false;
        AppSearchAppConsentDao obj = (AppSearchAppConsentDao) o;
        return (Objects.equals(this.mId, obj.mId))
                && (Objects.equals(this.mUserId, obj.mUserId))
                && (Objects.equals(this.mConsentType, obj.mConsentType))
                && (Objects.equals(this.mNamespace, obj.mNamespace))
                && (Objects.equals(this.mApps, obj.mApps));
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
    static AppSearchAppConsentDao readConsentData(
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
        AppSearchAppConsentDao dao =
                AppSearchDao.readConsentData(
                        AppSearchAppConsentDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch app consent data read: " + dao + " [ query: " + query + "]");
        return dao;
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    @VisibleForTesting
    static String getQuery(String userId, String consentType) {
        return USER_ID_COLNAME + ":" + userId + " " + CONSENT_TYPE_COLNAME + ":" + consentType;
    }
}
