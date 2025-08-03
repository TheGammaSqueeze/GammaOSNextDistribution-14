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

package com.android.server.appsearch.transformer;

import static android.provider.ContactsContract.AUTHORITY_URI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.SearchSpec;
import android.content.ContentUris;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.contactsindexer.AppSearchHelper;
import com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contains various transforms for {@link Person} enterprise access.
 */
final class PersonEnterpriseTransformer {
    private static final String TAG = "AppSearchPersonEnterpri";

    // These constants are hidden in ContactsContract.Contacts
    private static final Uri CORP_CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
            "contacts_corp");
    private static final long ENTERPRISE_CONTACT_ID_BASE = 1000000000;
    private static final String ENTERPRISE_CONTACT_LOOKUP_PREFIX = "c-";

    // Person externalUri should begin with "content://com.android.contacts/contacts/lookup"
    private static final String CONTACTS_LOOKUP_URI_PREFIX = Contacts.CONTENT_LOOKUP_URI.toString();

    private static final List<String> PERSON_ACCESSIBLE_PROPERTIES = List.of(
            Person.PERSON_PROPERTY_NAME,
            Person.PERSON_PROPERTY_GIVEN_NAME,
            Person.PERSON_PROPERTY_MIDDLE_NAME,
            Person.PERSON_PROPERTY_FAMILY_NAME,
            Person.PERSON_PROPERTY_EXTERNAL_URI,
            Person.PERSON_PROPERTY_ADDITIONAL_NAME_TYPES,
            Person.PERSON_PROPERTY_ADDITIONAL_NAMES,
            Person.PERSON_PROPERTY_IMAGE_URI,
            Person.PERSON_PROPERTY_CONTACT_POINTS + "."
                    + ContactPoint.CONTACT_POINT_PROPERTY_LABEL,
            Person.PERSON_PROPERTY_CONTACT_POINTS + "."
                    + ContactPoint.CONTACT_POINT_PROPERTY_EMAIL,
            Person.PERSON_PROPERTY_CONTACT_POINTS + "."
                    + ContactPoint.CONTACT_POINT_PROPERTY_TELEPHONE);

    @VisibleForTesting
    static final Set<String> PERSON_ACCESSIBLE_PROPERTIES_SET = new ArraySet<>(
            PERSON_ACCESSIBLE_PROPERTIES);

    private PersonEnterpriseTransformer() {
    }

    /**
     * Returns whether or not a document of the given package, database, and schema type combination
     * should be transformed for enterprise.
     */
    static boolean shouldTransform(@NonNull String packageName, @NonNull String databaseName,
            @NonNull String schemaType) {
        return schemaType.equals(Person.SCHEMA_TYPE) && packageName.equals("android")
                && databaseName.equals(AppSearchHelper.DATABASE_NAME);
    }

    /**
     * Transforms the imageUri and externalUri properties of a Person document to their enterprise
     * versions which are the corp thumbnail uri and corp lookup uri respectively.
     * <p>
     * When contacts are accessed through CP2's enterprise uri, CP2 replaces the contact id with an
     * enterprise contact id (the original contact id plus a base enterprise id
     * {@link ContactsContract.Contacts#ENTERPRISE_CONTACT_ID_BASE}). The corp thumbnail uri keeps
     * the original contact id, but the corp lookup uri uses the enterprise contact id.
     * <p>
     * In this method, we only transform the imageUri and externalUri properties, and we leave the
     * document id untouched, since changing the document id would interfere with retrieving
     * documents by id.
     */
    @NonNull
    static GenericDocument transformDocument(@NonNull GenericDocument originalDocument) {
        Objects.requireNonNull(originalDocument);
        String imageUri = originalDocument.getPropertyString(Person.PERSON_PROPERTY_IMAGE_URI);
        String externalUri = originalDocument.getPropertyString(
                Person.PERSON_PROPERTY_EXTERNAL_URI);
        // Only transform the properties if they're present in the document. If neither property is
        // present, just return the original document
        if (imageUri == null && externalUri == null) {
            return originalDocument;
        }
        GenericDocument.Builder<GenericDocument.Builder<?>> transformedDocumentBuilder =
                new GenericDocument.Builder<>(originalDocument);
        if (imageUri != null) {
            try {
                long contactId = Long.parseLong(originalDocument.getId());
                transformedDocumentBuilder.setPropertyString(Person.PERSON_PROPERTY_IMAGE_URI,
                        getCorpImageUri(contactId));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to set imageUri property", e);
            }
        }
        if (externalUri != null) {
            transformedDocumentBuilder.setPropertyString(Person.PERSON_PROPERTY_EXTERNAL_URI,
                    getCorpLookupUri(externalUri));
        }
        return transformedDocumentBuilder.build();
    }

    /**
     * Returns the corp thumbnail uri for the given contact id. Note, the generated uri should
     * include the original contact id as opposed to the enterprise contact id returned by CP2 which
     * has {@link Contacts#ENTERPRISE_CONTACT_ID_BASE} added to it.
     */
    @VisibleForTesting
    @NonNull
    static String getCorpImageUri(long contactId) {
        // https://cs.android.com/android/platform/superproject/main/+/main:packages/providers/ContactsProvider/src/com/android/providers/contacts/enterprise/EnterpriseContactsCursorWrapper.java;l=178;drc=a9d2c06a03a653954629ff10070ebbe4ea87d526
        return ContentUris.appendId(CORP_CONTENT_URI.buildUpon(), contactId).appendPath(
                Contacts.Photo.CONTENT_DIRECTORY).toString();
    }

    /**
     * Transforms the given lookup uri to a corp lookup uri. This prepends an enterprise-specific
     * prefix to the lookup key segment and transforms the contact id segment (if present) to an
     * enterprise contact id, e.g. "content://com.android.contacts/contacts/lookup/key/123" would
     * become "content://com.android.contacts/contacts/lookup/c-key/1000000123".
     *
     * <p>Note, if the given lookup uri does not match a CP2 lookup uri, this just returns the
     * original string.
     */
    @VisibleForTesting
    @NonNull
    static String getCorpLookupUri(@NonNull String lookupUri) {
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/provider/ContactsContract.java;l=2269;drc=9b279fbc71a1908311c32c24b4c65e967598b288
        if (!lookupUri.startsWith(CONTACTS_LOOKUP_URI_PREFIX)) {
            return lookupUri;
        }
        // The indexed uri has four segments: "contacts", "lookup", the lookup key, and the contact
        // id (contact id is optional)
        List<String> pathSegments = Uri.parse(lookupUri).getPathSegments();
        if (pathSegments.size() < 3) {
            return lookupUri;
        }
        if (pathSegments.size() > 3) {
            try {
                long contactId = Long.parseLong(pathSegments.get(3));
                return getCorpLookupUriFromLookupKey(pathSegments.get(2), contactId);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to get contact id from lookup uri", e);
            }
        }
        return getCorpLookupUriFromLookupKey(pathSegments.get(2));
    }

    @NonNull
    private static String getCorpLookupUriFromLookupKey(@NonNull String lookupKey, long contactId) {
        return ContentUris.withAppendedId(Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                        ENTERPRISE_CONTACT_LOOKUP_PREFIX + lookupKey),
                ENTERPRISE_CONTACT_ID_BASE + contactId).toString();
    }

    @NonNull
    private static String getCorpLookupUriFromLookupKey(@NonNull String lookupKey) {
        return Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                ENTERPRISE_CONTACT_LOOKUP_PREFIX + lookupKey).toString();
    }

    /**
     * Transforms a {@link SearchSpec} through its builder, adding property filters and projections
     * that restrict the allowed properties for the {@link Person} schema type.
     */
    static void transformSearchSpec(@NonNull SearchSpec searchSpec,
            @NonNull SearchSpec.Builder builder) {
        Map<String, List<String>> projections = searchSpec.getProjections();
        Map<String, List<String>> filterProperties = searchSpec.getFilterProperties();
        builder.addProjection(Person.SCHEMA_TYPE,
                getAccessibleProperties(projections.get(Person.SCHEMA_TYPE)));
        builder.addFilterProperties(Person.SCHEMA_TYPE,
                getAccessibleProperties(filterProperties.get(Person.SCHEMA_TYPE)));
    }

    /**
     * Adds allowed properties to the map for each {@link Person} schema type. If properties already
     * exist in the map for {@link Person}, removes the unallowed properties, leaving an
     * intersection of the original properties and the allowed properties.
     */
    static void transformPropertiesMap(@NonNull Map<String, List<String>> propertiesMap) {
        propertiesMap.put(Person.SCHEMA_TYPE,
                getAccessibleProperties(propertiesMap.get(Person.SCHEMA_TYPE)));
    }

    /**
     * If properties is non-null, returns the intersection of properties with the enterprise
     * accessible properties for {@link Person}; otherwise simply returns the enterprise accessible
     * properties for {@link Person}.
     */
    private static List<String> getAccessibleProperties(@Nullable List<String> properties) {
        if (properties == null) {
            return PERSON_ACCESSIBLE_PROPERTIES;
        }
        List<String> filteredProperties = new ArrayList<>();
        for (int i = 0; i < properties.size(); i++) {
            if (PERSON_ACCESSIBLE_PROPERTIES_SET.contains(properties.get(i))) {
                filteredProperties.add(properties.get(i));
            }
        }
        return filteredProperties;
    }
}
