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

package com.android.server.appsearch.contactsindexer.appsearchtypes;

import android.annotation.NonNull;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.GenericDocument;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a ContactPoint in AppSearch.
 *
 * @hide
 */
public final class ContactPoint extends GenericDocument {
    public static final String SCHEMA_TYPE = "builtin:ContactPoint";

    // Properties
    public static final String CONTACT_POINT_PROPERTY_LABEL = "label";
    public static final String CONTACT_POINT_PROPERTY_APP_ID = "appId";
    public static final String CONTACT_POINT_PROPERTY_ADDRESS = "address";
    public static final String CONTACT_POINT_PROPERTY_EMAIL = "email";
    public static final String CONTACT_POINT_PROPERTY_TELEPHONE = "telephone";

    // Schema
    public static final AppSearchSchema SCHEMA = new AppSearchSchema.Builder(
            SCHEMA_TYPE)
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_LABEL)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            // appIds
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_APP_ID)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .build())
            // address
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_ADDRESS)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            // email
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_EMAIL)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            // telephone
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_TELEPHONE)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            .build();

    /** Constructs a {@link ContactPoint}. */
    @VisibleForTesting
    public ContactPoint(@NonNull GenericDocument document) {
        super(document);
    }

    @NonNull
    public String getLabel() {
        return getPropertyString(CONTACT_POINT_PROPERTY_LABEL);
    }

    @NonNull
    public String[] getAppIds() {
        return getPropertyStringArray(CONTACT_POINT_PROPERTY_APP_ID);
    }

    @NonNull
    public String[] getAddresses() {
        return getPropertyStringArray(CONTACT_POINT_PROPERTY_ADDRESS);
    }

    @NonNull
    public String[] getEmails() {
        return getPropertyStringArray(CONTACT_POINT_PROPERTY_EMAIL);
    }

    @NonNull
    public String[] getPhones() {
        return getPropertyStringArray(CONTACT_POINT_PROPERTY_TELEPHONE);
    }

    /** Builder for {@link ContactPoint}. */
    public static final class Builder extends GenericDocument.Builder<Builder> {
        private List<String> mAppIds = new ArrayList<>();
        private List<String> mAddresses = new ArrayList<>();
        private List<String> mEmails = new ArrayList<>();
        private List<String> mTelephones = new ArrayList<>();

        /**
         * Creates a new {@link Builder}
         *
         * @param namespace The namespace for this document.
         * @param id        The id of this {@link ContactPoint}. It doesn't matter if it is used as
         *                  a nested documents in {@link Person}.
         * @param label     The label for this {@link ContactPoint}.
         */
        public Builder(@NonNull String namespace, @NonNull String id, @NonNull String label) {
            super(namespace, id, SCHEMA_TYPE);
            setLabel(label);
        }

        @NonNull
        private Builder setLabel(@NonNull String label) {
            setPropertyString(CONTACT_POINT_PROPERTY_LABEL, label);
            return this;
        }

        /**
         * Add a unique AppId for this {@link ContactPoint}.
         *
         * @param appId a unique identifier for the application.
         */
        @NonNull
        public Builder addAppId(@NonNull String appId) {
            Objects.requireNonNull(appId);
            mAppIds.add(appId);
            return this;
        }

        @NonNull
        public Builder addAddress(@NonNull String address) {
            Objects.requireNonNull(address);
            mAddresses.add(address);
            return this;
        }

        @NonNull
        public Builder addEmail(@NonNull String email) {
            Objects.requireNonNull(email);
            mEmails.add(email);
            return this;
        }

        @NonNull
        public Builder addPhone(@NonNull String phone) {
            Objects.requireNonNull(phone);
            mTelephones.add(phone);
            return this;
        }

        @NonNull
        public ContactPoint build() {
            setPropertyString(CONTACT_POINT_PROPERTY_APP_ID, mAppIds.toArray(new String[0]));
            setPropertyString(CONTACT_POINT_PROPERTY_EMAIL, mEmails.toArray(new String[0]));
            setPropertyString(CONTACT_POINT_PROPERTY_ADDRESS, mAddresses.toArray(new String[0]));
            setPropertyString(CONTACT_POINT_PROPERTY_TELEPHONE, mTelephones.toArray(new String[0]));
            return new ContactPoint(super.build());
        }
    }
}
