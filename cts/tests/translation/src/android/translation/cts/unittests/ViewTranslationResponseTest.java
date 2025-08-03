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

import static android.view.translation.TranslationResponseValue.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.view.autofill.AutofillId;
import android.view.translation.TranslationResponseValue;
import android.view.translation.ViewTranslationResponse;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ViewTranslationResponseTest {

    private final AutofillId mAutofillId = new AutofillId(17);

    @Test
    public void testBuilder_nullAutofillId() {
        assertThrows(NullPointerException.class, () -> new ViewTranslationResponse.Builder(null));
    }

    @Test
    public void testBuilder_validAddText() {
        final ViewTranslationResponse request = new ViewTranslationResponse.Builder(mAutofillId)
                .setValue("sample id",
                        new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                .setText("sample text")
                                .build())
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getValue("sample id").getText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_validAddError() {
        final ViewTranslationResponse request = new ViewTranslationResponse.Builder(mAutofillId)
                .setValue("sample id", TranslationResponseValue.forError())
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getValue("sample id").getStatusCode())
                .isEqualTo(TranslationResponseValue.STATUS_ERROR);
    }

    @Test
    public void testGetValue_invalidId() {
        final ViewTranslationResponse request = new ViewTranslationResponse.Builder(mAutofillId)
                .setValue("sample id",
                        new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                .setText("sample text")
                                .build())
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(1);
        assertThat(request.getValue("sample id").getText().toString()).isEqualTo("sample text");

        assertThrows(IllegalArgumentException.class, () -> request.getValue("something"));
        assertThrows(NullPointerException.class, () -> request.getValue(null));
    }

    @Test
    public void testBuilder_multipleResults() {
        final ViewTranslationResponse request = new ViewTranslationResponse.Builder(mAutofillId)
                .setValue("sample id",
                        new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                .setText("sample text")
                                .build())
                .setValue("id2",
                        new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                .setText("text2")
                                .build())
                .setValue("id3", TranslationResponseValue.forError())
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getKeys().size()).isEqualTo(3);
        assertThat(request.getValue("sample id").getText().toString()).isEqualTo("sample text");
        assertThat(request.getValue("id2").getText().toString()).isEqualTo("text2");
        assertThat(request.getValue("id3").getStatusCode())
                .isEqualTo(TranslationResponseValue.STATUS_ERROR);
    }
}
