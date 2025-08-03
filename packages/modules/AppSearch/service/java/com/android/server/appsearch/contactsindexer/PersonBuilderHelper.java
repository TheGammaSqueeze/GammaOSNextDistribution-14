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

package com.android.server.appsearch.contactsindexer;

import android.annotation.NonNull;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.util.IndentingStringBuilder;
import android.app.appsearch.util.LogUtil;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper class to help build the {@link Person}.
 *
 * <p>It takes a {@link Person.Builder} with a map to help handle and aggregate {@link
 * ContactPoint}s, and put them in the {@link Person} during the build.
 *
 * <p>This class is not thread safe.
 *
 * @hide
 */
public final class PersonBuilderHelper {
    static final String TAG = "PersonBuilderHelper";
    static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    static final int BASE_SCORE = 1;

    // We want to store id separately even if we do have it set in the builder, since we
    // can't get its value out of the builder, which will be used to fetch fingerprints.
    final private String mId;
    final private Person.Builder mBuilder;
    private long mCreationTimestampMillis = -1;
    private Map<String, ContactPointBuilderHelper> mContactPointBuilderHelpers = new ArrayMap<>();

    public PersonBuilderHelper(@NonNull String id, @NonNull Person.Builder builder) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(builder);
        mId = id;
        mBuilder = builder;
    }

    /**
     * Helper class to construct a {@link ContactPoint}.
     *
     * <p>In this helper, besides a {@link ContactPoint.Builder}, it contains a list of phone number
     * variants, so we can append those at the end of the final phone number list in {@link
     * #buildContactPoint()}.
     */
    private static class ContactPointBuilderHelper {
        final ContactPoint.Builder mBuilder;
        List<String> mPhoneNumberVariants = new ArrayList<>();

        ContactPointBuilderHelper(@NonNull ContactPoint.Builder builder) {
            mBuilder = Objects.requireNonNull(builder);
        }

        ContactPointBuilderHelper addPhoneNumberVariant(@NonNull String phoneNumberVariant) {
            mPhoneNumberVariants.add(Objects.requireNonNull(phoneNumberVariant));
            return this;
        }

        ContactPoint buildContactPoint() {
            // Append the phone number variants at the end of phone number list. So the original
            // phone numbers can appear first in the list.
            for (int i = 0; i < mPhoneNumberVariants.size(); ++i) {
                mBuilder.addPhone(mPhoneNumberVariants.get(i));
            }
            return mBuilder.build();
        }
    }

    /**
     * A {@link Person} is built and returned based on the current properties set in this helper.
     *
     * <p>A fingerprint is automatically generated and set.
     */
    @NonNull
    public Person buildPerson() {
        Preconditions.checkState(mCreationTimestampMillis >= 0,
                "creationTimestamp must be explicitly set in the PersonBuilderHelper.");

        for (ContactPointBuilderHelper builderHelper : mContactPointBuilderHelpers.values()) {
            // We don't need to reset it for generating fingerprint. But still set it 0 here to
            // avoid creationTimestamp automatically generated using current time. So our testing
            // could be easier.
            builderHelper.mBuilder.setCreationTimestampMillis(0);
            mBuilder.addContactPoint(builderHelper.buildContactPoint());
        }
        // Set the fingerprint and creationTimestamp to 0 to calculate the actual fingerprint.
        mBuilder.setScore(0);
        mBuilder.setFingerprint(EMPTY_BYTE_ARRAY);
        mBuilder.setCreationTimestampMillis(0);
        // Build a person for generating the fingerprint.
        Person contactForFingerPrint = mBuilder.build();
        try {
            byte[] fingerprint = generateFingerprintMD5(contactForFingerPrint);
            // This is an "a priori" document score that doesn't take any usage into account.
            // Hence, the heuristic that's used to assign the document score is to add the
            // presence or count of all the salient properties of the contact.
            int score = BASE_SCORE + contactForFingerPrint.getContactPoints().length
                    + contactForFingerPrint.getAdditionalNames().length;
            mBuilder.setScore(score);
            mBuilder.setFingerprint(fingerprint);
            mBuilder.setCreationTimestampMillis(mCreationTimestampMillis);
        } catch (NoSuchAlgorithmException e) {
            // debug logging here to avoid flooding the log.
            if (LogUtil.DEBUG) {
                Log.d(TAG,
                        "Failed to generate fingerprint for contact "
                                + contactForFingerPrint.getId(),
                        e);
            }
        }
        // Build a final person with fingerprint set.
        return mBuilder.build();
    }

    /** Gets the ID of this {@link Person}. */
    @NonNull
    String getId() {
        return mId;
    }

    @NonNull
    public Person.Builder getPersonBuilder() {
        return mBuilder;
    }

    @NonNull
    private ContactPointBuilderHelper getOrCreateContactPointBuilderHelper(@NonNull String label) {
        ContactPointBuilderHelper builderHelper = mContactPointBuilderHelpers.get(
                Objects.requireNonNull(label));
        if (builderHelper == null) {
            builderHelper = new ContactPointBuilderHelper(
                    new ContactPoint.Builder(AppSearchHelper.NAMESPACE_NAME,
                            /*id=*/"", // doesn't matter for this nested type.
                            label));
            mContactPointBuilderHelpers.put(label, builderHelper);
        }

        return builderHelper;
    }

    @NonNull
    public PersonBuilderHelper setCreationTimestampMillis(long creationTimestampMillis) {
        mCreationTimestampMillis = creationTimestampMillis;
        return this;
    }

    @NonNull
    public PersonBuilderHelper addAppIdToPerson(@NonNull String label, @NonNull String appId) {
        getOrCreateContactPointBuilderHelper(Objects.requireNonNull(label)).mBuilder
                .addAppId(Objects.requireNonNull(appId));
        return this;
    }

    public PersonBuilderHelper addEmailToPerson(@NonNull String label, @NonNull String email) {
        getOrCreateContactPointBuilderHelper(Objects.requireNonNull(label)).mBuilder
                .addEmail(Objects.requireNonNull(email));
        return this;
    }

    @NonNull
    public PersonBuilderHelper addAddressToPerson(@NonNull String label, @NonNull String address) {
        getOrCreateContactPointBuilderHelper(Objects.requireNonNull(label)).mBuilder
                .addAddress(Objects.requireNonNull(address));
        return this;
    }

    @NonNull
    public PersonBuilderHelper addPhoneToPerson(@NonNull String label, @NonNull String phone) {
        getOrCreateContactPointBuilderHelper(Objects.requireNonNull(label)).mBuilder
                .addPhone(Objects.requireNonNull(phone));
        return this;
    }

    @NonNull
    public PersonBuilderHelper addPhoneVariantToPerson(@NonNull String label,
            @NonNull String phoneVariant) {
        getOrCreateContactPointBuilderHelper(Objects.requireNonNull(label))
                .addPhoneNumberVariant(Objects.requireNonNull(phoneVariant));
        return this;
    }

    @NonNull
    static byte[] generateFingerprintMD5(@NonNull Person person) throws NoSuchAlgorithmException {
        Objects.requireNonNull(person);

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(generateFingerprintStringForPerson(person).getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }

    @VisibleForTesting
    /** Returns a string presentation of {@link Person} for fingerprinting. */
    static String generateFingerprintStringForPerson(@NonNull Person person) {
        Objects.requireNonNull(person);

        StringBuilder builder = new StringBuilder();
        appendGenericDocumentString(person, builder);
        return builder.toString();
    }

    /**
     * Appends string representation of a {@link GenericDocument} to the {@link StringBuilder}.
     *
     * <p>This is basically same as
     * {@link GenericDocument#appendGenericDocumentString(IndentingStringBuilder)}, but only keep
     * the properties part and use a normal {@link StringBuilder} to skip the indentation.
     */
    private static void appendGenericDocumentString(@NonNull GenericDocument doc,
            @NonNull StringBuilder builder) {
        Objects.requireNonNull(doc);
        Objects.requireNonNull(builder);

        builder.append("properties: {\n");
        String[] sortedProperties = doc.getPropertyNames().toArray(new String[0]);
        Arrays.sort(sortedProperties);
        for (int i = 0; i < sortedProperties.length; i++) {
            Object property = Objects.requireNonNull(doc.getProperty(sortedProperties[i]));
            appendPropertyString(sortedProperties[i], property, builder);
            if (i != sortedProperties.length - 1) {
                builder.append(",\n");
            }
        }
        builder.append("\n");
        builder.append("}");
    }

    /**
     * Appends string representation of a {@link GenericDocument}'s property to the
     * {@link StringBuilder}.
     *
     * <p>This is basically same as
     * {@link GenericDocument#appendPropertyString(String, Object, IndentingStringBuilder)}, but
     * use a normal {@link StringBuilder} to skip the indentation.
     *
     * <p>Here we still keep most of the formatting(e.g. '\n') to make sure we won't hit some
     * possible corner cases. E.g. We will have "someProperty1: some\n Property2:..." instead of
     * "someProperty1: someProperty2:". For latter, we can interpret it as empty string value for
     * "someProperty1", with a different property name "someProperty2". In this case, the content is
     * changed but fingerprint will remain same if we don't have that '\n'.
     *
     * <p>Plus, some basic formatting will make the testing more clear.
     */
    private static void appendPropertyString(
            @NonNull String propertyName,
            @NonNull Object property,
            @NonNull StringBuilder builder) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(property);
        Objects.requireNonNull(builder);

        builder.append("\"").append(propertyName).append("\": [");
        if (property instanceof GenericDocument[]) {
            GenericDocument[] documentValues = (GenericDocument[]) property;
            for (int i = 0; i < documentValues.length; ++i) {
                builder.append("\n");
                appendGenericDocumentString(documentValues[i], builder);
                if (i != documentValues.length - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("]");
        } else {
            int propertyArrLength = Array.getLength(property);
            for (int i = 0; i < propertyArrLength; i++) {
                Object propertyElement = Array.get(property, i);
                if (propertyElement instanceof String) {
                    builder.append("\"").append((String) propertyElement).append("\"");
                } else if (propertyElement instanceof byte[]) {
                    builder.append(Arrays.toString((byte[]) propertyElement));
                } else {
                    builder.append(propertyElement.toString());
                }
                if (i != propertyArrLength - 1) {
                    builder.append(", ");
                } else {
                    builder.append("]");
                }
            }
        }
    }
}
