/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.appsearch.app.helper_a;

import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;
import static android.app.appsearch.testutil.AppSearchTestUtils.convertSearchResultsToDocuments;

import static com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint.CONTACT_POINT_PROPERTY_ADDRESS;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint.CONTACT_POINT_PROPERTY_EMAIL;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint.CONTACT_POINT_PROPERTY_LABEL;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint.CONTACT_POINT_PROPERTY_TELEPHONE;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_ADDITIONAL_NAMES;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_ADDITIONAL_NAME_TYPES;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_AFFILIATIONS;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_CONTACT_POINTS;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_EXTERNAL_URI;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_FAMILY_NAME;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_GIVEN_NAME;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_IMAGE_URI;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_MIDDLE_NAME;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_NAME;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.PERSON_PROPERTY_NOTES;
import static com.android.server.appsearch.contactsindexer.appsearchtypes.Person.TYPE_NICKNAME;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.EnterpriseGlobalSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.EnterpriseGlobalSearchSessionShimImpl;
import android.app.appsearch.testutil.TestContactsIndexerConfig;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class EnterpriseContactsDeviceTest {
    private static final String PACKAGE_NAME =
            ApplicationProvider.getApplicationContext().getPackageName();
    private static final String DATABASE_NAME = "contacts";

    // This constant is hidden in SetSchemaRequest
    private static final int ENTERPRISE_ACCESS = 7;

    private EnterpriseGlobalSearchSessionShim mEnterpriseSession;

    @Before
    public void setUp() throws Exception {
        mEnterpriseSession = EnterpriseGlobalSearchSessionShimImpl
                .createEnterpriseGlobalSearchSessionAsync().get();
    }

    private Person.Builder createPersonBuilder(String namespace, String id, String name) {
        return new Person.Builder(namespace, id, name)
                .setGivenName("givenName")
                .setMiddleName("middleName")
                .setFamilyName("familyName")
                .setExternalUri(Uri.parse("externalUri"))
                .setImageUri(Uri.parse("imageUri"))
                .setIsImportant(true)
                .setIsBot(true)
                .addAdditionalName(TYPE_NICKNAME, "nickname")
                .addAffiliation("affiliation")
                .addRelation("relation")
                .addNote("note");
    }

    private void setUpEnterpriseContactsWithPermissions(Set<Integer> permissions) throws Exception {
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(
                        DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(ContactPoint.SCHEMA,
                        Person.getSchema(new TestContactsIndexerConfig()))
                .addRequiredPermissionsForSchemaTypeVisibility(Person.SCHEMA_TYPE, permissions)
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();
        // Index document
        Person person1 = createPersonBuilder("namespace", "123", "Sam1 Curran")
                .addContactPoint(new ContactPoint
                        .Builder("namespace", "cp1", "contact1")
                        .addEmail("person1@email.com")
                        .addPhone("123456")
                        .addAppId("appId1")
                        .addAddress("address1")
                        .build())
                .build();
        Person person2 = createPersonBuilder("namespace", "1234", "Sam2 Curran")
                .addContactPoint(new ContactPoint
                        .Builder("namespace", "cp2", "contact2")
                        .addEmail("person2@email.com")
                        .addPhone("1234567")
                        .addAppId("appId2")
                        .addAddress("address2")
                        .build())
                .build();
        Person person3 = createPersonBuilder("namespace", "12345", "Sam3 Curran")
                .addContactPoint(new ContactPoint
                        .Builder("namespace", "cp3", "contact3")
                        .addEmail("person3@email.com")
                        .addPhone("12345678")
                        .addAppId("appId3")
                        .addAddress("address3")
                        .build())
                .build();
        checkIsBatchResultSuccess(db.putAsync(
                new PutDocumentsRequest.Builder().addGenericDocuments(person1, person2,
                        person3).build()));
    }

    @Test
    public void setUpEnterpriseContacts() throws Exception {
        setUpEnterpriseContactsWithPermissions(ImmutableSet.of(SetSchemaRequest.READ_CONTACTS,
                ENTERPRISE_ACCESS));
    }

    @Test
    public void setUpEnterpriseContactsWithoutEnterprisePermissions() throws Exception {
        setUpEnterpriseContactsWithPermissions(ImmutableSet.of(SetSchemaRequest.READ_CONTACTS));
    }

    @Test
    public void testHasEnterpriseAccess() throws Exception {
        // Verify we can get the schema from the enterprise session
        GetSchemaResponse getSchemaResponse = mEnterpriseSession.getSchemaAsync(PACKAGE_NAME,
                DATABASE_NAME).get();
        Set<AppSearchSchema> schemas = getSchemaResponse.getSchemas();
        assertThat(schemas).hasSize(1);
        assertThat(schemas.iterator().next().getSchemaType()).isEqualTo(Person.SCHEMA_TYPE);

        // Searching with enterprise session returns documents
        SearchSpec spec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                .addFilterNamespaces("namespace")
                .build();
        SearchResultsShim searchResults = mEnterpriseSession.search("", spec);
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).isNotEmpty();
    }

    @Test
    public void testDoesNotHaveEnterpriseAccess() throws Exception {
        // Verify we cannot get the schema from the enterprise session
        GetSchemaResponse getSchemaResponse = mEnterpriseSession.getSchemaAsync(PACKAGE_NAME,
                DATABASE_NAME).get();
        assertThat(getSchemaResponse.getSchemas()).isEmpty();

        // Searching with enterprise session doesn't return any documents
        SearchSpec spec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                .addFilterNamespaces("namespace")
                .build();
        SearchResultsShim searchResults = mEnterpriseSession.search("", spec);
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).isEmpty();
    }

    @Test
    public void testGetEnterpriseContact() throws Exception {
        GetByDocumentIdRequest getDocumentRequest = new GetByDocumentIdRequest.Builder(
                "namespace").addIds("123").build();

        AppSearchBatchResult<String, GenericDocument> getResult =
                mEnterpriseSession.getByDocumentIdAsync(
                        ApplicationProvider.getApplicationContext().getPackageName(),
                        DATABASE_NAME, getDocumentRequest).get();
        assertThat(getResult.isSuccess()).isTrue();
        GenericDocument document = getResult.getSuccesses().get("123");
        assertThat(document.getPropertyNames()).containsAtLeast(PERSON_PROPERTY_NAME,
                PERSON_PROPERTY_GIVEN_NAME, PERSON_PROPERTY_MIDDLE_NAME,
                PERSON_PROPERTY_FAMILY_NAME, PERSON_PROPERTY_EXTERNAL_URI,
                PERSON_PROPERTY_ADDITIONAL_NAME_TYPES, PERSON_PROPERTY_ADDITIONAL_NAMES,
                PERSON_PROPERTY_IMAGE_URI, PERSON_PROPERTY_CONTACT_POINTS);
        assertThat(document.getPropertyNames()).doesNotContain(PERSON_PROPERTY_NOTES);
        assertThat(document.getPropertyString(PERSON_PROPERTY_NAME)).isEqualTo("Sam1 Curran");
        assertThat(document.getPropertyString(PERSON_PROPERTY_GIVEN_NAME)).isEqualTo("givenName");
        assertThat(document.getPropertyString(PERSON_PROPERTY_MIDDLE_NAME)).isEqualTo("middleName");
        assertThat(document.getPropertyString(PERSON_PROPERTY_FAMILY_NAME)).isEqualTo("familyName");
        assertThat(document.getPropertyString(PERSON_PROPERTY_EXTERNAL_URI)).isEqualTo(
                "externalUri");
        assertThat(document.getPropertyLongArray(
                PERSON_PROPERTY_ADDITIONAL_NAME_TYPES)).asList().containsExactly(
                (long) TYPE_NICKNAME);
        assertThat(document.getPropertyStringArray(
                PERSON_PROPERTY_ADDITIONAL_NAMES)).asList().containsExactly("nickname");
        // The imageUri property will not be rewritten by EnterpriseSearchResultPageTransformer
        // since this document does not come from the actual AppSearch contacts corpus
        assertThat(document.getPropertyString(PERSON_PROPERTY_IMAGE_URI)).isEqualTo("imageUri");
        GenericDocument contactPoint = document.getPropertyDocumentArray(
                PERSON_PROPERTY_CONTACT_POINTS)[0];
        assertThat(contactPoint.getPropertyNames()).containsAtLeast(CONTACT_POINT_PROPERTY_LABEL,
                CONTACT_POINT_PROPERTY_EMAIL, CONTACT_POINT_PROPERTY_TELEPHONE);
        assertThat(contactPoint.getPropertyNames()).doesNotContain(CONTACT_POINT_PROPERTY_ADDRESS);
        assertThat(contactPoint.getPropertyString(CONTACT_POINT_PROPERTY_LABEL)).isEqualTo(
                "contact1");
        assertThat(contactPoint.getPropertyString(CONTACT_POINT_PROPERTY_EMAIL)).isEqualTo(
                "person1@email.com");
        assertThat(contactPoint.getPropertyString(CONTACT_POINT_PROPERTY_TELEPHONE)).isEqualTo(
                "123456");

        // Check projections were not overwritten across Binder
        assertThat(getDocumentRequest.getProjections()).isEmpty();
    }

    @Test
    public void testGetEnterpriseContact_withProjection() throws Exception {
        GetByDocumentIdRequest getDocumentRequest = new GetByDocumentIdRequest.Builder(
                "namespace").addIds("123").addProjection(Person.SCHEMA_TYPE,
                Arrays.asList(PERSON_PROPERTY_NAME, PERSON_PROPERTY_ADDITIONAL_NAMES,
                        PERSON_PROPERTY_CONTACT_POINTS + "." + CONTACT_POINT_PROPERTY_ADDRESS,
                        PERSON_PROPERTY_CONTACT_POINTS + "."
                                + CONTACT_POINT_PROPERTY_EMAIL)).build();
        Map<String, List<String>> projectionsCopy = getDocumentRequest.getProjections();

        AppSearchBatchResult<String, GenericDocument> getResult =
                mEnterpriseSession.getByDocumentIdAsync(
                        ApplicationProvider.getApplicationContext().getPackageName(),
                        DATABASE_NAME, getDocumentRequest).get();
        assertThat(getResult.isSuccess()).isTrue();
        GenericDocument document = getResult.getSuccesses().get("123");
        assertThat(document.getPropertyNames()).containsExactly(PERSON_PROPERTY_NAME,
                PERSON_PROPERTY_CONTACT_POINTS, PERSON_PROPERTY_ADDITIONAL_NAMES);
        assertThat(document.getPropertyString(PERSON_PROPERTY_NAME)).isEqualTo("Sam1 Curran");
        assertThat(document.getPropertyStringArray(
                PERSON_PROPERTY_ADDITIONAL_NAMES)).asList().containsExactly("nickname");
        GenericDocument contactPoint = document.getPropertyDocumentArray(
                PERSON_PROPERTY_CONTACT_POINTS)[0];
        assertThat(contactPoint.getPropertyNames()).containsExactly(CONTACT_POINT_PROPERTY_EMAIL);
        assertThat(contactPoint.getPropertyString(CONTACT_POINT_PROPERTY_EMAIL)).isEqualTo(
                "person1@email.com");
        // CONTACT_POINT_PROPERTY_ADDRESS is not an accessible property
        assertThat(contactPoint.getPropertyString(CONTACT_POINT_PROPERTY_ADDRESS)).isNull();

        // Check projections were not overwritten across Binder
        assertThat(getDocumentRequest.getProjections()).isEqualTo(projectionsCopy);
    }

    @Test
    public void testSearchEnterpriseContacts() throws Exception {
        SearchSpec spec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                .addFilterNamespaces("namespace")
                .build();

        SearchResultsShim searchResults = mEnterpriseSession.search("", spec);
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(3);
        for (GenericDocument document : documents) {
            assertThat(document.getPropertyNames()).containsAtLeast(PERSON_PROPERTY_NAME,
                    PERSON_PROPERTY_GIVEN_NAME, PERSON_PROPERTY_MIDDLE_NAME,
                    PERSON_PROPERTY_FAMILY_NAME, PERSON_PROPERTY_EXTERNAL_URI,
                    PERSON_PROPERTY_ADDITIONAL_NAME_TYPES, PERSON_PROPERTY_ADDITIONAL_NAMES,
                    PERSON_PROPERTY_IMAGE_URI, PERSON_PROPERTY_CONTACT_POINTS);
            assertThat(document.getPropertyNames()).doesNotContain(PERSON_PROPERTY_NOTES);
            GenericDocument contactPoint = document.getPropertyDocumentArray(
                    PERSON_PROPERTY_CONTACT_POINTS)[0];
            assertThat(contactPoint.getPropertyNames()).containsAtLeast(
                    CONTACT_POINT_PROPERTY_LABEL, CONTACT_POINT_PROPERTY_EMAIL,
                    CONTACT_POINT_PROPERTY_TELEPHONE);
            assertThat(contactPoint.getPropertyNames()).doesNotContain(
                    CONTACT_POINT_PROPERTY_ADDRESS);
        }

        // Searching by indexed but inaccessible properties returns nothing
        searchResults = mEnterpriseSession.search("affiliation OR note OR address", spec);
        documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).isEmpty();
    }

    @Test
    public void testSearchEnterpriseContacts_withProjection() throws Exception {
        SearchSpec spec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                .addFilterNamespaces("namespace")
                .addProjection(Person.SCHEMA_TYPE,
                        Arrays.asList(PERSON_PROPERTY_NAME, PERSON_PROPERTY_ADDITIONAL_NAMES,
                                PERSON_PROPERTY_CONTACT_POINTS + "."
                                        + CONTACT_POINT_PROPERTY_ADDRESS,
                                PERSON_PROPERTY_CONTACT_POINTS + "."
                                        + CONTACT_POINT_PROPERTY_EMAIL))
                .build();

        SearchResultsShim searchResults = mEnterpriseSession.search("", spec);
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(3);
        for (GenericDocument document : documents) {
            assertThat(document.getPropertyNames()).containsExactly(PERSON_PROPERTY_NAME,
                    PERSON_PROPERTY_CONTACT_POINTS, PERSON_PROPERTY_ADDITIONAL_NAMES);
            assertThat(document.getPropertyStringArray(
                    PERSON_PROPERTY_ADDITIONAL_NAMES)).asList().containsExactly("nickname");
            GenericDocument contactPoint = document.getPropertyDocumentArray(
                    PERSON_PROPERTY_CONTACT_POINTS)[0];
            assertThat(contactPoint.getPropertyNames()).containsExactly(
                    CONTACT_POINT_PROPERTY_EMAIL);
            // CONTACT_POINT_PROPERTY_ADDRESS is not an accessible property
            assertThat(contactPoint.getPropertyString(CONTACT_POINT_PROPERTY_ADDRESS)).isNull();
        }

        // Searching by indexed but inaccessible properties returns nothing
        searchResults = mEnterpriseSession.search("affiliation OR note OR address", spec);
        documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).isEmpty();
    }

    @Test
    public void testSearchEnterpriseContacts_withFilter() throws Exception {
        SearchSpec spec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                .addFilterNamespaces("namespace")
                .addFilterProperties(Person.SCHEMA_TYPE,
                        Arrays.asList(PERSON_PROPERTY_NAME, PERSON_PROPERTY_ADDITIONAL_NAMES,
                                PERSON_PROPERTY_AFFILIATIONS, PERSON_PROPERTY_NOTES))
                .build();

        // Searching by name and nickname returns results
        SearchResultsShim searchResults = mEnterpriseSession.search("Sam AND nickname", spec);
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(3);
        for (GenericDocument document : documents) {
            assertThat(document.getPropertyNames()).containsAtLeast(PERSON_PROPERTY_NAME,
                    PERSON_PROPERTY_GIVEN_NAME, PERSON_PROPERTY_MIDDLE_NAME,
                    PERSON_PROPERTY_FAMILY_NAME, PERSON_PROPERTY_EXTERNAL_URI,
                    PERSON_PROPERTY_ADDITIONAL_NAME_TYPES, PERSON_PROPERTY_ADDITIONAL_NAMES,
                    PERSON_PROPERTY_IMAGE_URI, PERSON_PROPERTY_CONTACT_POINTS);
            assertThat(document.getPropertyNames()).doesNotContain(PERSON_PROPERTY_NOTES);
            GenericDocument contactPoint = document.getPropertyDocumentArray(
                    PERSON_PROPERTY_CONTACT_POINTS)[0];
            assertThat(contactPoint.getPropertyNames()).containsAtLeast(
                    CONTACT_POINT_PROPERTY_LABEL, CONTACT_POINT_PROPERTY_EMAIL,
                    CONTACT_POINT_PROPERTY_TELEPHONE);
            assertThat(contactPoint.getPropertyNames()).doesNotContain(
                    CONTACT_POINT_PROPERTY_ADDRESS);
        }

        // Searching by the filtered properties that are still inaccessible even when explicitly
        // set returns nothing
        searchResults = mEnterpriseSession.search("affiliation OR note OR address", spec);
        documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).isEmpty();
    }
}
