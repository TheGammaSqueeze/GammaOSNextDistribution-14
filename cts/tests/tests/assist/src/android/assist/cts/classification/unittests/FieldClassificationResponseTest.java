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

import static android.assist.cts.classification.unittests.Helper.assertFieldClassificationResponseEquals;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.service.assist.classification.FieldClassification;
import android.service.assist.classification.FieldClassificationResponse;
import android.util.ArraySet;
import android.view.autofill.AutofillId;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class FieldClassificationResponseTest {

    private static AutofillId sAutofillId1 = new AutofillId(1);
    private static AutofillId sAutofillId2 = new AutofillId(2);

    @Test
    public void testGetters() {
        Set<String> hints1 = Set.of("creditCardNumber", "creditCardExpirationDate",
                "creditCardSecurityCode");
        Set<String> groupHints1 = Set.of("creditCardNumber");
        FieldClassification f1 = new FieldClassification(sAutofillId1, hints1, groupHints1);

        Set<String> hints2 = Set.of("personGivenName", "personFamilyName");
        Set<String> groupHints2 = Set.of("personName");
        FieldClassification f2 = new FieldClassification(sAutofillId2, hints2, groupHints2);

        Set<FieldClassification> fcSet = Set.of(f1, f2);
        FieldClassificationResponse response = new FieldClassificationResponse(fcSet);

        assertThat(response.getClassifications()).containsExactlyElementsIn(fcSet);
    }

    @Test
    public void testParcelingAndUnparceling() {
        Set<String> hints1 = new ArraySet<>(Set.of("creditCardNumber", "creditCardExpirationDate",
                "creditCardSecurityCode"));
        Set<String> groupHints1 = new ArraySet<>(Set.of("creditCardNumber"));
        FieldClassification f1 = new FieldClassification(sAutofillId1, hints1, groupHints1);

        Set<String> hints2 = new ArraySet<>(Set.of("personGivenName", "personFamilyName"));
        Set<String> groupHints2 = new ArraySet<>(Set.of("personName"));
        FieldClassification f2 = new FieldClassification(sAutofillId2, hints2, groupHints2);

        Set<FieldClassification> fcSet = new ArraySet<>(Set.of(f1, f2));
        FieldClassificationResponse response = new FieldClassificationResponse(fcSet);

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        FieldClassificationResponse created =
                FieldClassificationResponse.CREATOR.createFromParcel(parcel);

        assertFieldClassificationResponseEquals(created, response);
    }

}
