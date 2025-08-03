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

import static android.assist.cts.classification.unittests.Helper.assertFieldClassificationEquals;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.service.assist.classification.FieldClassification;
import android.view.autofill.AutofillId;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class FieldClassificationTest {

    private static AutofillId sAutofillId = new AutofillId(1);

    @Test
    public void testGetters() {
        Set<String> hints = Set.of("creditCardNumber", "creditCardExpirationDate",
                "creditCardSecurityCode", "personGivenName", "personFamilyName");
        Set<String> groupHints = Set.of("creditCardNumber", "personName");

        // Check constructor with groupHints
        FieldClassification f = new FieldClassification(sAutofillId, hints, groupHints);
        assertThat(f.getHints()).containsExactlyElementsIn(hints);
        assertThat(f.getAutofillId()).isEqualTo(sAutofillId);
        assertThat(f.getGroupHints()).containsExactlyElementsIn(groupHints);


        // Check constructor without groupHints
        f = new FieldClassification(sAutofillId, hints);
        assertThat(f.getHints()).containsExactlyElementsIn(hints);
        assertThat(f.getAutofillId()).isEqualTo(sAutofillId);
        assertThat(f.getGroupHints()).isEmpty();
    }

    @Test
    public void testParcelingAndUnparceling() {
        Set<String> hints = Set.of("creditCardNumber", "creditCardExpirationDate",
                "creditCardSecurityCode", "personGivenName", "personFamilyName");
        Set<String> groupHints = Set.of("creditCardNumber", "personName");

        FieldClassification f = new FieldClassification(sAutofillId, hints, groupHints);
        Parcel parcel = Parcel.obtain();
        f.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        FieldClassification created = FieldClassification.CREATOR.createFromParcel(parcel);

        assertFieldClassificationEquals(created, f);
    }

}
