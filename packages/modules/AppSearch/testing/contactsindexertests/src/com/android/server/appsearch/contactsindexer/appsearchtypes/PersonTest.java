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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.List;

public class PersonTest {
    @Test
    public void testBuilder() {
        long creationTimestamp = 12345L;
        String namespace = "namespace";
        String id = "id";
        int score = 3;
        String name = "name";
        String givenName = "givenName";
        String middleName = "middleName";
        String lastName = "lastName";
        Uri externalUri = Uri.parse("http://external.com");
        Uri imageUri = Uri.parse("http://image.com");
        byte[] fingerprint = "Hello world!".getBytes();
        List<String> affiliations = ImmutableList.of("Org1", "Org2", "Org3");
        List<String> relations = ImmutableList.of("relation1", "relation2");
        boolean isImportant = true;
        boolean isBot = true;
        String note1 = "note";
        String note2 = "note2";
        ContactPoint contact1 = new ContactPoint.Builder(namespace, id + "1", "Home")
                .addAddress("addr1")
                .addPhone("phone1")
                .addEmail("email1")
                .addAppId("appId1")
                .build();
        ContactPoint contact2 = new ContactPoint.Builder(namespace, id + "2", "Work")
                .addAddress("addr2")
                .addPhone("phone2")
                .addEmail("email2")
                .addAppId("appId2")
                .build();
        ContactPoint contact3 = new ContactPoint.Builder(namespace, id + "3", "Other")
                .addAddress("addr3")
                .addPhone("phone3")
                .addEmail("email3")
                .addAppId("appId3")
                .build();
        List<String> additionalNames = ImmutableList.of("nickname", "phoneticName");
        @Person.NameType
        List<Long> additionalNameTypes = ImmutableList.of((long) Person.TYPE_NICKNAME,
                (long) Person.TYPE_PHONETIC_NAME);

        Person person = new Person.Builder(namespace, id, name)
                .setCreationTimestampMillis(creationTimestamp)
                .setScore(score)
                .setGivenName(givenName)
                .setMiddleName(middleName)
                .setFamilyName(lastName)
                .setExternalUri(externalUri)
                .setImageUri(imageUri)
                .addAdditionalName(additionalNameTypes.get(0), additionalNames.get(0))
                .addAdditionalName(additionalNameTypes.get(1), additionalNames.get(1))
                .addAffiliation(affiliations.get(0))
                .addAffiliation(affiliations.get(1))
                .addAffiliation(affiliations.get(2))
                .addRelation(relations.get(0))
                .addRelation(relations.get(1))
                .setIsImportant(isImportant)
                .setIsBot(isBot)
                .addNote(note1)
                .addNote(note2)
                .setFingerprint(fingerprint)
                .addContactPoint(contact1)
                .addContactPoint(contact2)
                .addContactPoint(contact3)
                .build();

        assertThat(person.getCreationTimestampMillis()).isEqualTo(creationTimestamp);
        assertThat(person.getScore()).isEqualTo(score);
        assertThat(person.getNamespace()).isEqualTo(namespace);
        assertThat(person.getId()).isEqualTo(id);
        assertThat(person.getName()).isEqualTo(name);
        assertThat(person.getGivenName()).isEqualTo(givenName);
        assertThat(person.getMiddleName()).isEqualTo(middleName);
        assertThat(person.getFamilyName()).isEqualTo(lastName);
        assertThat(person.getExternalUri().toString()).isEqualTo(externalUri.toString());
        assertThat(person.getImageUri().toString()).isEqualTo(imageUri.toString());
        assertThat(person.getNotes()).asList().containsExactly(note1, note2);
        assertThat(person.isBot()).isEqualTo(isBot);
        assertThat(person.isImportant()).isEqualTo(isImportant);
        assertThat(person.getFingerprint()).isEqualTo(fingerprint);
        assertThat(person.getAdditionalNames()).asList().isEqualTo(additionalNames);
        assertThat(person.getAdditionalNameTypes()).asList().isEqualTo(additionalNameTypes);
        assertThat(person.getAffiliations()).asList().isEqualTo(affiliations);
        assertThat(person.getRelations()).asList().isEqualTo(relations);
        assertThat(person.getContactPoints()).asList().containsExactly(contact1, contact2,
                contact3);
    }
}