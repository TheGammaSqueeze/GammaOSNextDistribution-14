/*
 * Copyright 2020 The Android Open Source Project
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

package android.app.appsearch.cts.app;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.testutil.AppSearchEmail;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Set;

public class PutDocumentsRequestCtsTest {

    @Test
    public void addGenericDocument_byCollection() {
        Set<AppSearchEmail> emails =
                ImmutableSet.of(
                        new AppSearchEmail.Builder("namespace", "test1").build(),
                        new AppSearchEmail.Builder("namespace", "test2").build());
        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder().addGenericDocuments(emails).build();

        assertThat(request.getGenericDocuments().get(0).getId()).isEqualTo("test1");
        assertThat(request.getGenericDocuments().get(1).getId()).isEqualTo("test2");
    }

    @Test
    public void duplicateIdForNormalAndTakenActionGenericDocumentThrowsException()
            throws Exception {
        GenericDocument normalDocument =
                new GenericDocument.Builder<>("namespace", "id", "builtin:Thing").build();
        GenericDocument takenActionGenericDocument =
                new GenericDocument.Builder<>("namespace", "id", "builtin:TakenAction").build();

        PutDocumentsRequest.Builder builder =
                new PutDocumentsRequest.Builder()
                        .addGenericDocuments(normalDocument)
                        .addTakenActionGenericDocuments(takenActionGenericDocument);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> builder.build());
        assertThat(e.getMessage())
                .isEqualTo(
                        "Document id "
                                + takenActionGenericDocument.getId()
                                + " cannot exist in both taken action and normal document");
    }

    @Test
    public void addTakenActionGenericDocuments() throws Exception {
        GenericDocument takenActionGenericDocument1 =
                new GenericDocument.Builder<>("namespace", "id1", "builtin:TakenAction").build();
        GenericDocument takenActionGenericDocument2 =
                new GenericDocument.Builder<>("namespace", "id2", "builtin:TakenAction").build();

        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder()
                        .addTakenActionGenericDocuments(
                                takenActionGenericDocument1, takenActionGenericDocument2)
                        .build();

        // Generic documents should contain nothing.
        assertThat(request.getGenericDocuments()).isEmpty();

        // Taken action generic documents should contain correct taken action generic documents.
        assertThat(request.getTakenActionGenericDocuments()).hasSize(2);
        assertThat(request.getTakenActionGenericDocuments().get(0).getId()).isEqualTo("id1");
        assertThat(request.getTakenActionGenericDocuments().get(1).getId()).isEqualTo("id2");
    }

    @Test
    public void addTakenActionGenericDocuments_byCollection() throws Exception {
        Set<GenericDocument> takenActionGenericDocuments =
                ImmutableSet.of(
                        new GenericDocument.Builder<>("namespace", "id1", "builtin:TakenAction")
                                .build(),
                        new GenericDocument.Builder<>("namespace", "id2", "builtin:TakenAction")
                                .build());

        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder()
                        .addTakenActionGenericDocuments(takenActionGenericDocuments)
                        .build();

        // Generic documents should contain nothing.
        assertThat(request.getGenericDocuments()).isEmpty();

        // Taken action generic documents should contain correct taken action generic documents.
        assertThat(request.getTakenActionGenericDocuments()).hasSize(2);
        assertThat(request.getTakenActionGenericDocuments().get(0).getId()).isEqualTo("id1");
        assertThat(request.getTakenActionGenericDocuments().get(1).getId()).isEqualTo("id2");
    }
}
