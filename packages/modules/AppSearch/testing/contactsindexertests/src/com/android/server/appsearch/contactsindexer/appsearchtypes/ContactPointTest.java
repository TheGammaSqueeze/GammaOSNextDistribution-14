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

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.List;

public class ContactPointTest {
    @Test
    public void testBuilder() {
        String namespace = "";
        String id = "id";
        String label = "label";
        List<String> emails = ImmutableList.of("email1", "email2");
        List<String> addresses = ImmutableList.of("addr1", "addr2", "addr3");
        List<String> telephones = ImmutableList.of("phone1");
        List<String> appIds = ImmutableList.of("appId1", "appId2", "appId3");

        ContactPoint.Builder contactPointBuilder =
                new ContactPoint.Builder(namespace, id, label);
        for (String email : emails) {
            contactPointBuilder.addEmail(email);
        }
        for (String address : addresses) {
            contactPointBuilder.addAddress(address);
        }
        for (String telephone : telephones) {
            contactPointBuilder.addPhone(telephone);
        }
        for (String appId : appIds) {
            contactPointBuilder.addAppId(appId);
        }
        ContactPoint contactPoint = contactPointBuilder.build();

        assertThat(contactPoint.getId()).isEqualTo(id);
        assertThat(contactPoint.getLabel()).isEqualTo(label);
        assertThat(contactPoint.getEmails()).asList().isEqualTo(emails);
        assertThat(contactPoint.getAddresses()).asList().isEqualTo(addresses);
        assertThat(contactPoint.getPhones()).asList().isEqualTo(telephones);
        assertThat(contactPoint.getAppIds()).asList().isEqualTo(appIds);
    }
}