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

/**
 * This class represents the data access object for the notification related data written to
 * AppSearch. This includes whether a notification was displayed (Beta or GA format).
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchNotificationDao extends AppSearchDao {
    /**
     * Identifier of the Consent Document; must be unique within the Document's `namespace`. This is
     * the row ID for consent data. It is the same as userId, but we store userId separately so that
     * this DAO can be extended if needed in the future.
     */
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /**
     * Namespace of the Notification Document. Used to group documents during querying or deletion.
     */
    @Document.Namespace private final String mNamespace;

    /** Whether beta UX notification was displayed to this user. */
    @Document.BooleanProperty private final boolean mWasNotificationDisplayed;

    /** Whether beta UX notification was displayed to this user. */
    @Document.BooleanProperty private final boolean mWasGaUxNotificationDisplayed;

    // Column name used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";
    public static final String NAMESPACE = "notifications";

    /**
     * Create an AppSearchNotificationDao instance.
     *
     * @param id is a combination of the user ID and apiType
     * @param userId is the user ID for this user
     * @param namespace (required by AppSearch)
     * @param wasNotificationDisplayed whether beta UX notification was displayed to this user
     * @param wasGaUxNotificationDisplayed whether GA UX notification was displayed to this user
     */
    AppSearchNotificationDao(
            String id,
            String userId,
            String namespace,
            boolean wasNotificationDisplayed,
            boolean wasGaUxNotificationDisplayed) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mWasNotificationDisplayed = wasNotificationDisplayed;
        this.mWasGaUxNotificationDisplayed = wasGaUxNotificationDisplayed;
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
     * Get the bit for whether beta UX notification was displayed.
     *
     * @return wasNotificationDisplayed
     */
    public Boolean getWasNotificationDisplayed() {
        return mWasNotificationDisplayed;
    }

    /**
     * Get the bit for whether GA UX notification was displayed.
     *
     * @return wasGaUxNotificationDisplayed
     */
    public Boolean getWasGaUxNotificationDisplayed() {
        return mWasGaUxNotificationDisplayed;
    }

    /** Returns the row ID that should be unique for the consent namespace. */
    public static String getRowId(@NonNull String uid) {
        return uid;
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
                + "; wasNotificationDisplayed="
                + mWasNotificationDisplayed
                + "; wasGaUxNotificationDisplayed="
                + mWasGaUxNotificationDisplayed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId, mUserId, mNamespace, mWasNotificationDisplayed, mWasGaUxNotificationDisplayed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchNotificationDao)) return false;
        AppSearchNotificationDao obj = (AppSearchNotificationDao) o;
        return (Objects.equals(this.mId, obj.getId()))
                && (Objects.equals(this.mUserId, obj.getUserId()))
                && (Objects.equals(
                        this.mWasNotificationDisplayed, obj.getWasNotificationDisplayed()))
                && (Objects.equals(this.mNamespace, obj.getNamespace()))
                && (Objects.equals(
                        this.mWasGaUxNotificationDisplayed, obj.getWasGaUxNotificationDisplayed()));
    }

    /**
     * Read the notification data from AppSearch.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @return whether we showed the beta UX notification to this user.
     */
    public static boolean wasNotificationDisplayed(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        String query = getQuery(userId);
        AppSearchNotificationDao dao =
                AppSearchDao.readConsentData(
                        AppSearchNotificationDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch notification data read: " + dao + " [ query: " + query + "]");
        if (dao == null) {
            return false;
        }
        return dao.getWasNotificationDisplayed();
    }

    /**
     * Read the GA UX notification data from AppSearch.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @return whether we showed the GA UX notification to this user.
     */
    public static boolean wasGaUxNotificationDisplayed(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        String query = getQuery(userId);
        AppSearchNotificationDao dao =
                AppSearchDao.readConsentData(
                        AppSearchNotificationDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch notification data read: " + dao + " [ query: " + query + "]");
        if (dao == null) {
            return false;
        }
        return dao.getWasGaUxNotificationDisplayed();
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    @VisibleForTesting
    static String getQuery(String userId) {
        return USER_ID_COLNAME + ":" + userId;
    }
}
