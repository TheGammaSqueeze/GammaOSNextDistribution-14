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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.GenericDocument;
import android.net.Uri;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.appsearch.contactsindexer.ContactsIndexerConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Person in AppSearch.
 *
 * @hide
 */
public class Person extends GenericDocument {
    public static final String SCHEMA_TYPE = "builtin:Person";

    /**
     * The type of the name stored in additionalNames list. We have two parallel lists to store
     * different names, like nicknames and phonetic names as searchable field in additionalNames.
     *
     * <p>Having this type for each name stored in additionalNames, so clients can distinguish the
     * type of those names in the search result.
     *
     * @hide
     */
    @IntDef(
            value = {
                    TYPE_UNKNOWN,
                    TYPE_NICKNAME,
                    TYPE_PHONETIC_NAME,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NameType {
    }

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_NICKNAME = 1;
    public static final int TYPE_PHONETIC_NAME = 2;

    // Properties
    public static final String PERSON_PROPERTY_NAME = "name";
    public static final String PERSON_PROPERTY_GIVEN_NAME = "givenName";
    public static final String PERSON_PROPERTY_MIDDLE_NAME = "middleName";
    public static final String PERSON_PROPERTY_FAMILY_NAME = "familyName";
    public static final String PERSON_PROPERTY_EXTERNAL_URI = "externalUri";
    public static final String PERSON_PROPERTY_ADDITIONAL_NAME_TYPES = "additionalNameTypes";
    public static final String PERSON_PROPERTY_ADDITIONAL_NAMES = "additionalNames";
    public static final String PERSON_PROPERTY_IS_IMPORTANT = "isImportant";
    public static final String PERSON_PROPERTY_IS_BOT = "isBot";
    public static final String PERSON_PROPERTY_IMAGE_URI = "imageUri";
    public static final String PERSON_PROPERTY_CONTACT_POINTS = "contactPoints";
    public static final String PERSON_PROPERTY_AFFILIATIONS = "affiliations";
    public static final String PERSON_PROPERTY_RELATIONS = "relations";
    public static final String PERSON_PROPERTY_NOTES = "notes";
    public static final String PERSON_PROPERTY_FINGERPRINT = "fingerprint";

    private static AppSearchSchema createSchema(boolean indexFirstMiddleAndLastNames) {
        AppSearchSchema.Builder builder = new AppSearchSchema.Builder(SCHEMA_TYPE)
                // full display name
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(PERSON_PROPERTY_NAME)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build());

        if (indexFirstMiddleAndLastNames) {
            builder
                    // given name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_GIVEN_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(AppSearchSchema.StringPropertyConfig
                                    .INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(AppSearchSchema.StringPropertyConfig
                                    .TOKENIZER_TYPE_PLAIN)
                            .build())
                    // middle name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_MIDDLE_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(AppSearchSchema.StringPropertyConfig
                                    .INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(AppSearchSchema.StringPropertyConfig
                                    .TOKENIZER_TYPE_PLAIN)
                            .build())
                    // family name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_FAMILY_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(AppSearchSchema.StringPropertyConfig
                                    .INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(AppSearchSchema.StringPropertyConfig
                                    .TOKENIZER_TYPE_PLAIN)
                            .build());
        } else {
            builder
                    // given name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_GIVEN_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // middle name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_MIDDLE_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // family name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_FAMILY_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build());
        }

        builder
                // lookup uri from CP2
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                        PERSON_PROPERTY_EXTERNAL_URI)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .build())
                // corresponding name types for the names stored in additional names below.
                .addProperty(new AppSearchSchema.LongPropertyConfig.Builder(
                        PERSON_PROPERTY_ADDITIONAL_NAME_TYPES)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .build())
                // additional names e.g. nick names and phonetic names.
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                        PERSON_PROPERTY_ADDITIONAL_NAMES)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build())
                // isImportant. It could be used to store isStarred from CP2.
                .addProperty(new AppSearchSchema.BooleanPropertyConfig.Builder(
                        PERSON_PROPERTY_IS_IMPORTANT)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .build())
                // isBot
                .addProperty(new AppSearchSchema.BooleanPropertyConfig.Builder(
                        PERSON_PROPERTY_IS_BOT)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .build())
                // imageUri
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                        PERSON_PROPERTY_IMAGE_URI)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .build())
                // ContactPoint
                .addProperty(new AppSearchSchema.DocumentPropertyConfig.Builder(
                        PERSON_PROPERTY_CONTACT_POINTS,
                        ContactPoint.SCHEMA.getSchemaType())
                        .setShouldIndexNestedProperties(true)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .build())
                // Affiliations
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                        PERSON_PROPERTY_AFFILIATIONS)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build())
                // Relations
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                        PERSON_PROPERTY_RELATIONS)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .build())
                // Notes
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(PERSON_PROPERTY_NOTES)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build())
                //
                // Following fields are internal to ContactsIndexer.
                //
                // Fingerprint for detecting significant changes
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                        PERSON_PROPERTY_FINGERPRINT)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .build());
        return builder.build();
    }

    /***
     * Returns Person schema based on current value of flag
     * 'contacts_index_first_middle_and_last_names'. If the flag value changes after the initial
     * schema fetch, the schema returned will be different than the original schema that was set
     * for the Person corpus.
     */
    public static AppSearchSchema getSchema(ContactsIndexerConfig config) {
        return createSchema(config.shouldIndexFirstMiddleAndLastNames());
    }

    /** Constructs a {@link Person}. */
    @VisibleForTesting
    public Person(@NonNull GenericDocument document) {
        super(document);
    }

    @NonNull
    public String getName() {
        return getPropertyString(PERSON_PROPERTY_NAME);
    }

    @Nullable
    public String getGivenName() {
        return getPropertyString(PERSON_PROPERTY_GIVEN_NAME);
    }

    @Nullable
    public String getMiddleName() {
        return getPropertyString(PERSON_PROPERTY_MIDDLE_NAME);
    }

    @Nullable
    public String getFamilyName() {
        return getPropertyString(PERSON_PROPERTY_FAMILY_NAME);
    }

    @Nullable
    public Uri getExternalUri() {
        String uriStr = getPropertyString(PERSON_PROPERTY_EXTERNAL_URI);
        if (uriStr == null) {
            return null;
        }
        return Uri.parse(uriStr);
    }

    @Nullable
    public Uri getImageUri() {
        String uriStr = getPropertyString(PERSON_PROPERTY_IMAGE_URI);
        if (uriStr == null) {
            return null;
        }
        return Uri.parse(uriStr);
    }

    public boolean isImportant() {
        return getPropertyBoolean(PERSON_PROPERTY_IS_IMPORTANT);
    }

    public boolean isBot() {
        return getPropertyBoolean(PERSON_PROPERTY_IS_BOT);
    }

    @NonNull
    @NameType
    public long[] getAdditionalNameTypes() {
        return getPropertyLongArray(PERSON_PROPERTY_ADDITIONAL_NAME_TYPES);
    }

    @NonNull
    public String[] getAdditionalNames() {
        return getPropertyStringArray(PERSON_PROPERTY_ADDITIONAL_NAMES);
    }

    @NonNull
    public String[] getAffiliations() {
        return getPropertyStringArray(PERSON_PROPERTY_AFFILIATIONS);
    }

    @NonNull
    public String[] getRelations() {
        return getPropertyStringArray(PERSON_PROPERTY_RELATIONS);
    }

    @Nullable
    public String[] getNotes() {
        return getPropertyStringArray(PERSON_PROPERTY_NOTES);
    }

    // This method is expensive, and is intended to be used in tests only.
    @NonNull
    public ContactPoint[] getContactPoints() {
        GenericDocument[] docs = getPropertyDocumentArray(PERSON_PROPERTY_CONTACT_POINTS);
        ContactPoint[] contactPoints = new ContactPoint[docs.length];
        for (int i = 0; i < contactPoints.length; ++i) {
            contactPoints[i] = new ContactPoint(docs[i]);
        }
        return contactPoints;
    }

    /**
     * Gets a byte array for the fingerprint.
     */
    @NonNull
    public byte[] getFingerprint() {
        return getPropertyBytes(PERSON_PROPERTY_FINGERPRINT);
    }

    /** Builder for {@link Person}. */
    public static final class Builder extends GenericDocument.Builder<Builder> {
        @NameType
        private final List<Long> mAdditionalNameTypes = new ArrayList<>();
        private final List<String> mAdditionalNames = new ArrayList<>();
        private final List<String> mAffiliations = new ArrayList<>();
        private final List<String> mRelations = new ArrayList<>();
        private final List<String> mNotes = new ArrayList<>();
        private final List<ContactPoint> mContactPoints = new ArrayList<>();

        /**
         * Creates a new {@link ContactPoint.Builder}
         *
         * @param namespace The namespace of the Email.
         * @param id        The ID of the Email.
         * @param name      The name of the {@link Person}.
         */
        public Builder(@NonNull String namespace, @NonNull String id, @NonNull String name) {
            super(namespace, id, SCHEMA_TYPE);
            setName(name);
        }

        /** Sets the full display name. */
        @NonNull
        private Builder setName(@NonNull String name) {
            setPropertyString(PERSON_PROPERTY_NAME, name);
            return this;
        }

        @NonNull
        public Builder setGivenName(@NonNull String givenName) {
            setPropertyString(PERSON_PROPERTY_GIVEN_NAME, givenName);
            return this;
        }

        @NonNull
        public Builder setMiddleName(@NonNull String middleName) {
            setPropertyString(PERSON_PROPERTY_MIDDLE_NAME, middleName);
            return this;
        }

        @NonNull
        public Builder setFamilyName(@NonNull String familyName) {
            setPropertyString(PERSON_PROPERTY_FAMILY_NAME, familyName);
            return this;
        }

        @NonNull
        public Builder setExternalUri(@NonNull Uri externalUri) {
            setPropertyString(PERSON_PROPERTY_EXTERNAL_URI,
                    Objects.requireNonNull(externalUri).toString());
            return this;
        }

        @NonNull
        public Builder setImageUri(@NonNull Uri imageUri) {
            setPropertyString(PERSON_PROPERTY_IMAGE_URI,
                    Objects.requireNonNull(imageUri).toString());
            return this;
        }

        @NonNull
        public Builder setIsImportant(boolean isImportant) {
            setPropertyBoolean(PERSON_PROPERTY_IS_IMPORTANT, isImportant);
            return this;
        }

        @NonNull
        public Builder setIsBot(boolean isBot) {
            setPropertyBoolean(PERSON_PROPERTY_IS_BOT, isBot);
            return this;
        }

        @NonNull
        public Builder addAdditionalName(@NameType long nameType, @NonNull String name) {
            mAdditionalNameTypes.add(nameType);
            mAdditionalNames.add(Objects.requireNonNull(name));
            return this;
        }

        /**
         * Adds an affiliation for the {@link Person}, like a company name as an employee, or a
         * university name as a student.
         */
        @NonNull
        public Builder addAffiliation(@NonNull String affiliation) {
            mAffiliations.add(Objects.requireNonNull(affiliation));
            return this;
        }

        /** Adds a relation to this {@link Person}. Like "spouse", "father", etc. */
        @NonNull
        public Builder addRelation(@NonNull String relation) {
            mRelations.add(Objects.requireNonNull(relation));
            return this;
        }

        /** Adds a note about this {@link Person}. */
        @NonNull
        public Builder addNote(@NonNull String note) {
            mNotes.add(Objects.requireNonNull(note));
            return this;
        }

        @NonNull
        public Builder addContactPoint(@NonNull ContactPoint contactPoint) {
            Objects.requireNonNull(contactPoint);
            mContactPoints.add(contactPoint);
            return this;
        }

        /**
         * Sets the fingerprint for this {@link Person}
         *
         * @param fingerprint byte array for the fingerprint. The size depends on the algorithm
         *                    being used. Right now we are using md5 and generating a 16-byte
         *                    fingerprint.
         */
        @NonNull
        public Builder setFingerprint(@NonNull byte[] fingerprint) {
            setPropertyBytes(PERSON_PROPERTY_FINGERPRINT, Objects.requireNonNull(fingerprint));
            return this;
        }

        @NonNull
        public Person build() {
            Preconditions.checkState(
                    mAdditionalNameTypes.size() == mAdditionalNames.size());
            long[] primitiveNameTypes = new long[mAdditionalNameTypes.size()];
            for (int i = 0; i < mAdditionalNameTypes.size(); i++) {
                primitiveNameTypes[i] = mAdditionalNameTypes.get(i).longValue();
            }
            setPropertyLong(PERSON_PROPERTY_ADDITIONAL_NAME_TYPES, primitiveNameTypes);
            setPropertyString(PERSON_PROPERTY_ADDITIONAL_NAMES,
                    mAdditionalNames.toArray(new String[0]));
            setPropertyString(PERSON_PROPERTY_AFFILIATIONS,
                    mAffiliations.toArray(new String[0]));
            setPropertyString(PERSON_PROPERTY_RELATIONS,
                    mRelations.toArray(new String[0]));
            setPropertyString(PERSON_PROPERTY_NOTES,
                    mNotes.toArray(new String[0]));
            setPropertyDocument(PERSON_PROPERTY_CONTACT_POINTS,
                    mContactPoints.toArray(new ContactPoint[0]));
            // TODO(b/203605504) calculate score here.
            return new Person(super.build());
        }
    }
}
