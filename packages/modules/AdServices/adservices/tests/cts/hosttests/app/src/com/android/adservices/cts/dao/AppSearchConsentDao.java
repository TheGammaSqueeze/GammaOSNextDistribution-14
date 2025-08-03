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
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;

import java.util.Objects;

@RequiresApi(Build.VERSION_CODES.S)
@Document
public class AppSearchConsentDao {
    @Document.Id private final String mId;
    @Document.Namespace private final String mNamespace;
    @Document.StringProperty private final String mConsent;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /**
     * API type for this consent. Possible values are a) CONSENT, CONSENT-FLEDGE,
     * CONSENT-MEASUREMENT, CONSENT-TOPICS, b) DEFAULT_CONSENT, TOPICS_DEFAULT_CONSENT,
     * FLEDGE_DEFAULT_CONSENT, MEASUREMENT_DEFAULT_CONSENT and c) DEFAULT_AD_ID_STATE.
     */
    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mApiType;

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
    public AppSearchConsentDao(
            String id, String userId, String namespace, String apiType, String consent) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mApiType = apiType;
        this.mConsent = consent;
    }

    public String getId() {
        return mId;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getNamespace() {
        return mNamespace;
    }

    public String getApiType() {
        return mApiType;
    }

    public String getConsent() {
        return mConsent;
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
}
