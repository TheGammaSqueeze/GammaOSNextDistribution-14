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

import android.os.Parcel;
import android.util.SparseArray;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.ViewTranslationResponse;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class TranslationResponseTest {

    private final TranslationResponseValue mValue =
            new TranslationResponseValue.Builder(STATUS_SUCCESS)
                    .setText("hello")
                    .build();

    private final ViewTranslationResponse mViewResponse = new ViewTranslationResponse
            .Builder(new AutofillId(17))
            .setValue("sample id",
                    new TranslationResponseValue.Builder(STATUS_SUCCESS)
                            .setText("sample text")
                            .build())
            .build();

    @Test
    public void testBuilder_validViewTranslationResponse() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, mViewResponse)
                        .build();

        assertThat(response.isFinalResponse()).isTrue();
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(0);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewResponse =
                response.getViewTranslationResponses().get(0);
        assertThat(viewResponse.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewResponse.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse.getValue("sample id").getText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_errorViewTranslationResponse() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, new ViewTranslationResponse
                                .Builder(new AutofillId(42))
                                .setValue("id2",
                                        TranslationResponseValue.forError())
                                .build())
                        .build();

        assertThat(response.isFinalResponse()).isTrue();
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(0);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewResponse =
                response.getViewTranslationResponses().get(0);
        assertThat(viewResponse.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(viewResponse.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse.getValue("id2").getStatusCode())
                .isEqualTo(TranslationResponseValue.STATUS_ERROR);
    }

    @Test
    public void testBuilder_validTranslationResponseValue() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setFinalResponse(false)
                        .setTranslationResponseValue(0, mValue)
                        .build();

        assertThat(response.isFinalResponse()).isFalse();
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(0);

        final TranslationResponseValue value =
                response.getTranslationResponseValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testParceledResponse_validTranslationResponseValues() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setTranslationResponseValue(0, mValue)
                        .setTranslationResponseValue(2,
                                new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                        .setText("world")
                                        .build())
                        .build();

        final Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationResponse parceledResponse =
                TranslationResponse.CREATOR.createFromParcel(parcel);

        assertThat(parceledResponse.isFinalResponse()).isTrue();
        assertThat(parceledResponse.getTranslationResponseValues().size()).isEqualTo(2);
        assertThat(parceledResponse.getViewTranslationResponses().size()).isEqualTo(0);

        final TranslationResponseValue value1 =
                parceledResponse.getTranslationResponseValues().get(0);
        assertThat(value1.getText()).isEqualTo("hello");

        final TranslationResponseValue value2 =
                parceledResponse.getTranslationResponseValues().get(2);
        assertThat(value2.getText()).isEqualTo("world");
    }

    @Test
    public void testBuilder_mixingAdders() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, mViewResponse)
                        .setTranslationResponseValue(0, mValue)
                        .build();

        assertThat(response.isFinalResponse()).isTrue();
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewResponse =
                response.getViewTranslationResponses().get(0);
        assertThat(viewResponse.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewResponse.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse.getValue("sample id").getText()).isEqualTo("sample text");

        final TranslationResponseValue value =
                response.getTranslationResponseValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testBuilder_mixingSetters() {
        final SparseArray<TranslationResponseValue> values = new SparseArray<>();
        values.set(0, mValue);
        final SparseArray<ViewTranslationResponse> responses = new SparseArray<>();
        responses.set(0, mViewResponse);

        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponses(responses)
                        .setTranslationResponseValues(values)
                        .build();

        assertThat(response.isFinalResponse()).isTrue();
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewResponse =
                response.getViewTranslationResponses().get(0);
        assertThat(viewResponse.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewResponse.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse.getValue("sample id").getText()).isEqualTo("sample text");

        final TranslationResponseValue value =
                response.getTranslationResponseValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testParceledResponse_validViewTranslationResponses() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, mViewResponse)
                        .setViewTranslationResponse(2, new ViewTranslationResponse
                                .Builder(new AutofillId(42))
                                .setValue("id2",
                                        new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                                .setText("test")
                                                .build())
                                .build())
                        .build();

        final Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationResponse parceledResponse =
                TranslationResponse.CREATOR.createFromParcel(parcel);

        assertThat(parceledResponse.isFinalResponse()).isTrue();
        assertThat(parceledResponse.getTranslationResponseValues().size()).isEqualTo(0);
        assertThat(parceledResponse.getViewTranslationResponses().size()).isEqualTo(2);

        final ViewTranslationResponse viewResponse1 =
                parceledResponse.getViewTranslationResponses().get(0);
        assertThat(viewResponse1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewResponse1.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse1.getValue("sample id").getText()).isEqualTo("sample text");

        final ViewTranslationResponse viewResponse2 =
                parceledResponse.getViewTranslationResponses().get(2);
        assertThat(viewResponse2.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(viewResponse2.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse2.getValue("id2").getText()).isEqualTo("test");
    }

}