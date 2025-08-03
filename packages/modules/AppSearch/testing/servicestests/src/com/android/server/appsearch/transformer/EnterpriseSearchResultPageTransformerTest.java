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

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultPage;

import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class EnterpriseSearchResultPageTransformerTest {

    @Test
    public void testTransformSearchResultPage() {
        // Set up SearchResults
        GenericDocument document1 = new GenericDocument.Builder<>("namespace", "1",
                Person.SCHEMA_TYPE)
                .setPropertyString(Person.PERSON_PROPERTY_IMAGE_URI, "person1/imageUri").build();
        GenericDocument document2 = new GenericDocument.Builder<>("namespace", "2",
                Person.SCHEMA_TYPE)
                .setPropertyString(Person.PERSON_PROPERTY_IMAGE_URI, "person2/imageUri").build();

        SearchResult joinedResult1 = new SearchResult.Builder("android", "contacts")
                .setGenericDocument(document1).build();
        SearchResult joinedResult2 = new SearchResult.Builder("android", "contacts")
                .setGenericDocument(document2).build();

        SearchResult searchResult1 = new SearchResult.Builder("android", "contacts")
                .setGenericDocument(document1)
                .addJoinedResult(joinedResult1)
                .addJoinedResult(joinedResult2)
                .build();
        SearchResult searchResult2 = new SearchResult.Builder("android", "contacts")
                .setGenericDocument(document2)
                .addJoinedResult(joinedResult1)
                .addJoinedResult(joinedResult2)
                .build();

        SearchResultPage searchResultPage = new SearchResultPage(/*nextPageToken=*/ 0,
                Arrays.asList(searchResult1, searchResult2));

        // Transform SearchResultPage
        SearchResultPage transformedPage =
                EnterpriseSearchResultPageTransformer.transformSearchResultPage(searchResultPage);

        GenericDocument transformedDocument1 =
                PersonEnterpriseTransformer.transformDocument(document1);
        GenericDocument transformedDocument2 =
                PersonEnterpriseTransformer.transformDocument(document2);

        List<SearchResult> transformedResults = transformedPage.getResults();
        assertThat(transformedResults.size()).isEqualTo(2);
        SearchResult transformedResult1 = transformedResults.get(0);
        assertThat(transformedResult1.getGenericDocument()).isEqualTo(transformedDocument1);
        assertThat(transformedResult1.getJoinedResults()).hasSize(2);
        assertThat(transformedResult1.getJoinedResults().get(0).getGenericDocument()).isEqualTo(
                transformedDocument1);
        assertThat(transformedResult1.getJoinedResults().get(1).getGenericDocument()).isEqualTo(
                transformedDocument2);
        SearchResult transformedResult2 = transformedResults.get(1);
        assertThat(transformedResult2.getGenericDocument()).isEqualTo(transformedDocument2);
        assertThat(transformedResult2.getJoinedResults()).hasSize(2);
        assertThat(transformedResult2.getJoinedResults().get(0).getGenericDocument()).isEqualTo(
                transformedDocument1);
        assertThat(transformedResult2.getJoinedResults().get(1).getGenericDocument()).isEqualTo(
                transformedDocument2);
    }

    @Test
    public void testTransformSearchResult() {
        // Set up SearchResult
        GenericDocument document = new GenericDocument.Builder<>("namespace", "1",
                Person.SCHEMA_TYPE)
                .setPropertyString(Person.PERSON_PROPERTY_IMAGE_URI, "person1/imageUri").build();

        SearchResult joinedResult = new SearchResult.Builder("android", "contacts")
                .setGenericDocument(document).build();

        SearchResult searchResult = new SearchResult.Builder("android", "contacts")
                .setGenericDocument(document)
                .addJoinedResult(joinedResult)
                .addJoinedResult(joinedResult)
                .build();

        // Transform SearchResult
        SearchResult transformedResult =
                EnterpriseSearchResultPageTransformer.transformSearchResult(searchResult);

        GenericDocument transformedDocument =
                PersonEnterpriseTransformer.transformDocument(document);
        assertThat(transformedResult.getGenericDocument()).isEqualTo(transformedDocument);
        assertThat(transformedResult.getJoinedResults()).hasSize(2);
        assertThat(transformedResult.getJoinedResults().get(0).getGenericDocument()).isEqualTo(
                transformedDocument);
        assertThat(transformedResult.getJoinedResults().get(1).getGenericDocument()).isEqualTo(
                transformedDocument);
    }

    @Test
    public void testTransformSearchResultPage_emptyPageSucceeds() {
        // There was a bug previously when handling empty SearchResultPage
        SearchResultPage searchResultPage = new SearchResultPage();
        assertThat(EnterpriseSearchResultPageTransformer.transformSearchResultPage(
                searchResultPage)).isEqualTo(searchResultPage);
    }

    @Test
    public void testTransformDocument_transformsPersonSchemaType() {
        // Verify Person document is transformed with PersonEnterpriseDocumentTransformer
        GenericDocument document = new GenericDocument.Builder<>("namespace", "1",
                Person.SCHEMA_TYPE)
                .setPropertyString(Person.PERSON_PROPERTY_IMAGE_URI, "person1/imageUri").build();

        assertThat(EnterpriseSearchResultPageTransformer.transformDocument("android", "contacts",
                document)).isEqualTo(
                PersonEnterpriseTransformer.transformDocument(document));

        // Verify non-Person document is not transformed with PersonEnterpriseDocumentTransformer
        document = new GenericDocument.Builder<>("namespace", "1", "otherSchemaType")
                .setPropertyString(Person.PERSON_PROPERTY_IMAGE_URI, "person1/imageUri").build();

        assertThat(EnterpriseSearchResultPageTransformer.transformDocument("android", "contacts",
                document)).isNotEqualTo(
                PersonEnterpriseTransformer.transformDocument(document));
    }
}
