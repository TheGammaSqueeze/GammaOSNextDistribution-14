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

import android.net.Uri;

import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class PersonEnterpriseTransformerTest {

    @Test
    public void testTransform_setsImageUriIfItExists() {
        long contactId = 1234;
        Person person = new Person.Builder("namespace", String.valueOf(contactId),
                "Test Person").setImageUri(Uri.parse("person1/imageUri")).build();

        Person transformedPerson = new Person(
                PersonEnterpriseTransformer.transformDocument(person));

        String expectedImageUri = PersonEnterpriseTransformer.getCorpImageUri(contactId);
        assertThat(transformedPerson.getImageUri().toString()).isEqualTo(expectedImageUri);
    }

    @Test
    public void testTransform_doesNotSetImageUriIfItDoesNotExist() {
        Person person = new Person.Builder("namespace", "1234", "Test Person").build();
        Person transformedPerson = new Person(
                PersonEnterpriseTransformer.transformDocument(person));
        assertThat(transformedPerson.getImageUri()).isNull();
    }

    @Test
    public void testGetCorpImageUri() {
        assertThat(PersonEnterpriseTransformer.getCorpImageUri(123)).isEqualTo(
                "content://com.android.contacts/contacts_corp/123/photo");
    }

    @Test
    public void testTransform_setsExternalUriIfItExists() {
        String lookupUri = "content://com.android.contacts/contacts/lookup/key/1234";
        Person person = new Person.Builder("namespace", "1234", "Test Person").setExternalUri(
                Uri.parse(lookupUri)).build();

        Person transformedPerson = new Person(
                PersonEnterpriseTransformer.transformDocument(person));

        String expectedLookupUri = PersonEnterpriseTransformer.getCorpLookupUri(lookupUri);
        assertThat(transformedPerson.getExternalUri().toString()).isEqualTo(expectedLookupUri);
    }

    @Test
    public void testTransform_doesNotSetExternalUriIfItDoesNotExist() {
        Person person = new Person.Builder("namespace", "1234", "Test Person").build();
        Person transformedPerson = new Person(
                PersonEnterpriseTransformer.transformDocument(person));
        assertThat(transformedPerson.getExternalUri()).isNull();
    }

    @Test
    public void testGetCorpLookupUri_withId() {
        String lookupUri = "content://com.android.contacts/contacts/lookup/key/3";
        assertThat(PersonEnterpriseTransformer.getCorpLookupUri(lookupUri)).isEqualTo(
                "content://com.android.contacts/contacts/lookup/c-key/1000000003");
    }

    @Test
    public void testGetCorpLookupUri_withoutId() {
        String lookupUri = "content://com.android.contacts/contacts/lookup/key";
        assertThat(PersonEnterpriseTransformer.getCorpLookupUri(lookupUri)).isEqualTo(
                "content://com.android.contacts/contacts/lookup/c-key");
    }

    @Test
    public void testGetCorpLookupUri_invalidFormat() {
        assertThat(PersonEnterpriseTransformer.getCorpLookupUri("externalUri")).isEqualTo(
                "externalUri");
    }
}
