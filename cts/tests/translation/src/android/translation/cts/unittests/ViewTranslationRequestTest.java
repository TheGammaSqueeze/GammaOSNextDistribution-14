/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.translation.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.view.View;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationRequestValue;
import android.view.translation.ViewTranslationRequest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ViewTranslationRequestTest {

    private final AutofillId mAutofillId = new AutofillId(17);

    @Test
    public void testBuilder_nullAutofillId() {
        assertThrows(NullPointerException.class, () -> new ViewTranslationRequest.Builder(null));
    }

    @Test
    public void testBuilderVirtualAutofillId_validSetText() {
        final ViewTranslationRequest request =
                new ViewTranslationRequest.Builder(mAutofillId, /* virtualChildId= */ 12345L)
                        .setValue("sample id", TranslationRequestValue.forText("sample text"))
                        .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(mAutofillId, 12345L, 0));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getKeys()).containsExactly("sample id");
        assertThat(request.getValue("sample id").getText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_validSetText() {
        final ViewTranslationRequest request = new ViewTranslationRequest.Builder(mAutofillId)
                .setValue("sample id",
                        TranslationRequestValue.forText("sample text"))
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getKeys()).containsExactly("sample id");
        assertThat(request.getValue("sample id").getText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_setTextTwice() {
        final ViewTranslationRequest request = new ViewTranslationRequest.Builder(mAutofillId)
                .setValue("sample id",
                        TranslationRequestValue.forText("sample text"))
                .setValue("sample id",
                        TranslationRequestValue.forText("text2"))
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getKeys()).containsExactly("sample id");
        assertThat(request.getValue("sample id").getText()).isEqualTo("text2");
    }

    @Test
    public void testGetValue_invalidId() {
        final ViewTranslationRequest request = new ViewTranslationRequest.Builder(mAutofillId)
                .setValue("sample id",
                        TranslationRequestValue.forText("sample text"))
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getKeys()).containsExactly("sample id");
        assertThat(request.getValue("sample id").getText()).isEqualTo("sample text");

        assertThrows(IllegalArgumentException.class, () -> request.getValue("something"));
        assertThrows(NullPointerException.class, () -> request.getValue(null));
    }

    @Test
    public void testBuilder_multipleTexts() {
        final ViewTranslationRequest request = new ViewTranslationRequest.Builder(mAutofillId)
                .setValue("sample id",
                        TranslationRequestValue.forText("sample text"))
                .setValue("id2",
                        TranslationRequestValue.forText("text2"))
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(2);
        assertThat(request.getKeys()).containsExactly("sample id", "id2");
        assertThat(request.getValue("sample id").getText()).isEqualTo("sample text");
        assertThat(request.getValue("id2").getText()).isEqualTo("text2");
    }
}
