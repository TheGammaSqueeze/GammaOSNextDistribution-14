/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.enrollment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.service.proto.PrivacySandboxApi;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** POJO for Adtech EnrollmentData, store the data download using MDD. */
public class EnrollmentData {
    @VisibleForTesting public static String SEPARATOR = " ";

    private String mEnrollmentId;
    private String mEnrolledAPIsString;
    // mEnrolled APIs are derived from mEnrolledAPIsString, they represent the same enrolledAPIs
    private List<PrivacySandboxApi> mEnrolledAPIs;
    private List<String> mSdkNames;
    private List<String> mAttributionSourceRegistrationUrl;
    private List<String> mAttributionTriggerRegistrationUrl;
    private List<String> mAttributionReportingUrl;
    private List<String> mRemarketingResponseBasedRegistrationUrl;
    private String mEncryptionKeyUrl;

    private EnrollmentData() {
        mEnrollmentId = null;
        mEnrolledAPIsString = null;
        mEnrolledAPIs = new ArrayList<PrivacySandboxApi>();
        mSdkNames = new ArrayList<>();
        mAttributionSourceRegistrationUrl = new ArrayList<>();
        mAttributionTriggerRegistrationUrl = new ArrayList<>();
        mAttributionReportingUrl = new ArrayList<>();
        mRemarketingResponseBasedRegistrationUrl = new ArrayList<>();
        mEncryptionKeyUrl = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EnrollmentData)) {
            return false;
        }
        EnrollmentData enrollmentData = (EnrollmentData) obj;
        return Objects.equals(mEnrollmentId, enrollmentData.mEnrollmentId)
                && Objects.equals(mEnrolledAPIsString, enrollmentData.mEnrolledAPIsString)
                && Objects.equals(mEnrolledAPIs, enrollmentData.mEnrolledAPIs)
                && Objects.equals(mSdkNames, enrollmentData.mSdkNames)
                && Objects.equals(
                        mAttributionSourceRegistrationUrl,
                        enrollmentData.mAttributionSourceRegistrationUrl)
                && Objects.equals(
                        mAttributionTriggerRegistrationUrl,
                        enrollmentData.mAttributionTriggerRegistrationUrl)
                && Objects.equals(mAttributionReportingUrl, enrollmentData.mAttributionReportingUrl)
                && Objects.equals(
                        mRemarketingResponseBasedRegistrationUrl,
                        enrollmentData.mRemarketingResponseBasedRegistrationUrl)
                && Objects.equals(mEncryptionKeyUrl, enrollmentData.mEncryptionKeyUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mEnrollmentId,
                mEnrolledAPIsString,
                mEnrolledAPIs,
                mSdkNames,
                mAttributionSourceRegistrationUrl,
                mAttributionTriggerRegistrationUrl,
                mAttributionReportingUrl,
                mRemarketingResponseBasedRegistrationUrl,
                mEncryptionKeyUrl);
    }

    /** Returns ID provided to the Adtech at the end of the enrollment process. */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** Return Enrolled APIs of given enrollment in string format */
    @Nullable
    public String getEnrolledAPIsString() {
        return mEnrolledAPIsString;
    }

    /** Return list of Enrolled APIs of given enrollment */
    @Nullable
    public List<PrivacySandboxApi> getEnrolledAPIs() {
        return mEnrolledAPIs;
    }

    /** List of SDKs belonging to the same enrollment. */
    public List<String> getSdkNames() {
        return mSdkNames;
    }

    /** Returns URLs used to register attribution sources for measurement. */
    public List<String> getAttributionSourceRegistrationUrl() {
        return mAttributionSourceRegistrationUrl;
    }

    /** Returns URLs used to register triggers for measurement. */
    public List<String> getAttributionTriggerRegistrationUrl() {
        return mAttributionTriggerRegistrationUrl;
    }

    /** Returns URLs that the Measurement module will send Attribution reports to. */
    public List<String> getAttributionReportingUrl() {
        return mAttributionReportingUrl;
    }

    /** Returns URLs used for response-based-registration for joinCustomAudience. */
    public List<String> getRemarketingResponseBasedRegistrationUrl() {
        return mRemarketingResponseBasedRegistrationUrl;
    }

    /** Returns URL used to fetch public/private keys for encrypting API requests. */
    @Nullable
    public String getEncryptionKeyUrl() {
        return mEncryptionKeyUrl;
    }

    /**
     * Returns the given {@code input} as a list of values split by the separator value used for all
     * enrollment data.
     */
    @NonNull
    public static List<String> splitEnrollmentInputToList(@Nullable String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.asList(input.trim().split(SEPARATOR));
    }

    /**
     * Returns the given {@code enrolledAPIs} as a list of {@link PrivacySandboxApi} enum values.
     */
    private static List<PrivacySandboxApi> enrolledApisToEnums(String enrolledAPIs) {
        List<PrivacySandboxApi> enrolledApiEnums = new ArrayList<PrivacySandboxApi>();
        if (enrolledAPIs == null || enrolledAPIs.trim().isEmpty()) {
            return enrolledApiEnums;
        }

        String[] enrolledAPIsList = enrolledAPIs.trim().split("\\s+");
        for (String enrolledApi : enrolledAPIsList) {
            enrolledApiEnums.add(enrolledApiToEnum(enrolledApi));
        }
        return enrolledApiEnums;
    }

    /**
     * Returns the given {@code enrolledAPI} to corresponding {@link PrivacySandboxApi} enum value
     */
    // LINT.IfChange(enrolledApiToEnum)
    private static PrivacySandboxApi enrolledApiToEnum(String enrolledAPI) {
        return switch (enrolledAPI) {
            case ("PRIVACY_SANDBOX_API_TOPICS") -> PrivacySandboxApi.PRIVACY_SANDBOX_API_TOPICS;
            case ("PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE") -> PrivacySandboxApi
                    .PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE;
            case ("PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION") -> PrivacySandboxApi
                    .PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION;
            case ("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING") -> PrivacySandboxApi
                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING;
            case ("PRIVACY_SANDBOX_API_SHARED_STORAGE") -> PrivacySandboxApi
                    .PRIVACY_SANDBOX_API_SHARED_STORAGE;
            case ("PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS") -> PrivacySandboxApi
                    .PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS;
            default -> PrivacySandboxApi.PRIVACY_SANDBOX_API_UNKNOWN;
        };
    }
    // LINT.ThenChange(/adservices/service-core/proto/rb_enrollment.proto:PrivacySandboxApi)

    /** Returns the builder for the instance */
    @NonNull
    public EnrollmentData.Builder cloneToBuilder() {
        return new EnrollmentData.Builder()
                .setEnrollmentId(this.mEnrollmentId)
                .setEnrolledAPIs(this.mEnrolledAPIsString)
                .setSdkNames(this.mSdkNames)
                .setAttributionSourceRegistrationUrl(this.mAttributionSourceRegistrationUrl)
                .setAttributionTriggerRegistrationUrl(this.mAttributionTriggerRegistrationUrl)
                .setAttributionReportingUrl(this.mAttributionReportingUrl)
                .setRemarketingResponseBasedRegistrationUrl(
                        this.mRemarketingResponseBasedRegistrationUrl)
                .setEncryptionKeyUrl(this.mEncryptionKeyUrl);
    }

    /** Builder for {@link EnrollmentData}. */
    public static final class Builder {
        private final EnrollmentData mBuilding;

        public Builder() {
            mBuilding = new EnrollmentData();
        }

        /** See {@link EnrollmentData#getEnrollmentId()}. */
        public Builder setEnrollmentId(String enrollmentId) {
            mBuilding.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link EnrollmentData#getEnrolledAPIs()}. */
        public Builder setEnrolledAPIs(String enrolledAPIs) {
            mBuilding.mEnrolledAPIsString = enrolledAPIs;
            mBuilding.mEnrolledAPIs = enrolledApisToEnums(enrolledAPIs);
            return this;
        }

        /** See {@link EnrollmentData#getSdkNames()} */
        public Builder setSdkNames(List<String> sdkNames) {
            mBuilding.mSdkNames = sdkNames;
            return this;
        }

        /** See {@link EnrollmentData#getSdkNames()} */
        public Builder setSdkNames(String sdkNames) {
            mBuilding.mSdkNames = splitEnrollmentInputToList(sdkNames);
            return this;
        }

        /** See {@link EnrollmentData#getAttributionSourceRegistrationUrl()}. */
        public Builder setAttributionSourceRegistrationUrl(
                List<String> attributionSourceRegistrationUrl) {
            mBuilding.mAttributionSourceRegistrationUrl = attributionSourceRegistrationUrl;
            return this;
        }

        /** See {@link EnrollmentData#getAttributionSourceRegistrationUrl()}. */
        public Builder setAttributionSourceRegistrationUrl(
                String attributionSourceRegistrationUrl) {
            mBuilding.mAttributionSourceRegistrationUrl =
                    splitEnrollmentInputToList(attributionSourceRegistrationUrl);
            return this;
        }

        /** See {@link EnrollmentData#getAttributionTriggerRegistrationUrl()}. */
        public Builder setAttributionTriggerRegistrationUrl(
                List<String> attributionTriggerRegistrationUrl) {
            mBuilding.mAttributionTriggerRegistrationUrl = attributionTriggerRegistrationUrl;
            return this;
        }

        /** See {@link EnrollmentData#getAttributionTriggerRegistrationUrl()}. */
        public Builder setAttributionTriggerRegistrationUrl(
                String attributionTriggerRegistrationUrl) {
            mBuilding.mAttributionTriggerRegistrationUrl =
                    splitEnrollmentInputToList(attributionTriggerRegistrationUrl);
            return this;
        }

        /** See {@link EnrollmentData#getAttributionReportingUrl()}. */
        public Builder setAttributionReportingUrl(List<String> attributionReportingUrl) {
            mBuilding.mAttributionReportingUrl = attributionReportingUrl;
            return this;
        }

        /** See {@link EnrollmentData#getAttributionReportingUrl()}. */
        public Builder setAttributionReportingUrl(String attributionReportingUrl) {
            mBuilding.mAttributionReportingUrl =
                    splitEnrollmentInputToList(attributionReportingUrl);
            return this;
        }

        /** See {@link EnrollmentData#getRemarketingResponseBasedRegistrationUrl()}. */
        public Builder setRemarketingResponseBasedRegistrationUrl(
                List<String> remarketingResponseBasedRegistrationUrl) {
            mBuilding.mRemarketingResponseBasedRegistrationUrl =
                    remarketingResponseBasedRegistrationUrl;
            return this;
        }

        /** See {@link EnrollmentData#getRemarketingResponseBasedRegistrationUrl()}. */
        public Builder setRemarketingResponseBasedRegistrationUrl(
                String remarketingResponseBasedRegistrationUrl) {
            mBuilding.mRemarketingResponseBasedRegistrationUrl =
                    splitEnrollmentInputToList(remarketingResponseBasedRegistrationUrl);
            return this;
        }

        /** See {@link EnrollmentData#getEncryptionKeyUrl()}. */
        public Builder setEncryptionKeyUrl(String encryptionKeyUrl) {
            mBuilding.mEncryptionKeyUrl = encryptionKeyUrl;
            return this;
        }

        /** Builder the {@link EnrollmentData}. */
        public EnrollmentData build() {
            return mBuilding;
        }
    }
}
