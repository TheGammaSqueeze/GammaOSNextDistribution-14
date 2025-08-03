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

package android.assist.cts.classification.unittests;

import static com.google.common.truth.Truth.assertThat;

import android.service.assist.classification.FieldClassification;
import android.service.assist.classification.FieldClassificationResponse;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class Helper {

    public static void assertFieldClassificationEquals(
            FieldClassification actual, FieldClassification expected) {
        assertThat(actual.getHints()).containsExactlyElementsIn(expected.getHints());
        assertThat(actual.getAutofillId()).isEqualTo(expected.getAutofillId());
        assertThat(actual.getGroupHints()).containsExactlyElementsIn(expected.getGroupHints());

    }

    public static void assertFieldClassificationResponseEquals(
            FieldClassificationResponse actual, FieldClassificationResponse expected) {
        // We expect the iteration order to be same
        List<FieldClassification> expectedClassifications =
                new ArrayList<>(expected.getClassifications());
        List<FieldClassification> actualClassifications =
                new ArrayList<>(actual.getClassifications());

        assertThat(actualClassifications.size()).isEqualTo(expectedClassifications.size());

        HashMap<AutofillId, FieldClassification> expectedClassificationMap = new HashMap<>();
        HashMap<AutofillId, FieldClassification> actualClassificationMap = new HashMap<>();

        // Populate hashmaps of viewId -> FieldClassifications
        for (int i = 0; i < actualClassifications.size(); i++) {
            expectedClassificationMap.put(
                    expectedClassifications.get(i).getAutofillId(),
                    expectedClassifications.get(i));
            actualClassificationMap.put(
                    actualClassifications.get(i).getAutofillId(),
                    actualClassifications.get(i));
        }

        // For each int, fetch the hashmap entry with same view id and compare them.
        for (int i = 0; i < actualClassifications.size(); i++) {
            AutofillId autofillId = expectedClassifications.get(i).getAutofillId();
            assertFieldClassificationEquals(
                    actualClassificationMap.get(autofillId),
                    expectedClassificationMap.get(autofillId));
        }
    }

    private Helper(){};
}
