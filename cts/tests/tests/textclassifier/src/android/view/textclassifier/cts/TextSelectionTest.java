/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;

import androidx.core.os.BuildCompat;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextSelectionTest {
    private static final String BUNDLE_KEY = "key";
    private static final String BUNDLE_VALUE = "value";
    private static final Bundle BUNDLE = new Bundle();
    static {
        BUNDLE.putString(BUNDLE_KEY, BUNDLE_VALUE);
    }

    private static final float ACCEPTED_DELTA = 0.0000001f;
    private static final String TEXT = "abcdefghijklmnopqrstuvwxyz";
    private static final int START = 5;
    private static final int END = 20;
    private static final String ID = "id123";
    private static final LocaleList LOCALES = LocaleList.forLanguageTags("fr,en,de,es");
    private static final TextClassification TEXT_CLASSIFICATION =
            new TextClassification.Builder().setText(TEXT).build();

    @Test
    public void testTextSelection() {
        final float addressScore = 0.1f;
        final float emailScore = 0.9f;

        final TextSelection.Builder originalBuilder = new TextSelection.Builder(START, END)
                .setEntityType(TextClassifier.TYPE_ADDRESS, addressScore)
                .setEntityType(TextClassifier.TYPE_EMAIL, emailScore)
                .setId(ID)
                .setExtras(BUNDLE);
        if (BuildCompat.isAtLeastS()) {
            originalBuilder.setTextClassification(TEXT_CLASSIFICATION);
        }
        final TextSelection original = originalBuilder.build();

        TextSelection selection = parcelizeDeparcelize(original, TextSelection.CREATOR);

        assertThat(selection.getSelectionStartIndex()).isEqualTo(START);
        assertThat(selection.getSelectionEndIndex()).isEqualTo(END);
        assertThat(selection.getEntityCount()).isEqualTo(2);
        assertThat(selection.getEntity(0)).isEqualTo(TextClassifier.TYPE_EMAIL);
        assertThat(selection.getEntity(1)).isEqualTo(TextClassifier.TYPE_ADDRESS);
        assertThat(selection.getConfidenceScore(TextClassifier.TYPE_ADDRESS)).isWithin(
                ACCEPTED_DELTA).of(addressScore);
        assertThat(selection.getConfidenceScore(TextClassifier.TYPE_EMAIL)).isWithin(
                ACCEPTED_DELTA).of(emailScore);
        assertThat(selection.getConfidenceScore("random_type")).isEqualTo(0);
        assertThat(selection.getId()).isEqualTo(ID);
        assertThat(selection.getExtras().getString(BUNDLE_KEY)).isEqualTo(BUNDLE_VALUE);
        if (BuildCompat.isAtLeastS()) {
            assertThat(selection.getTextClassification().getText()).isEqualTo(TEXT);
        }
    }

    @Test
    public void testTextSelection_differentParams() {
        final int start = 0;
        final int end = 1;
        final float confidenceScore = 0.5f;
        final String id = "2hukwu3m3k44f1gb0";

        final TextSelection selection = new TextSelection.Builder(start, end)
                .setEntityType(TextClassifier.TYPE_URL, confidenceScore)
                .setId(id)
                .build();

        assertEquals(start, selection.getSelectionStartIndex());
        assertEquals(end, selection.getSelectionEndIndex());
        assertEquals(1, selection.getEntityCount());
        assertEquals(TextClassifier.TYPE_URL, selection.getEntity(0));
        assertEquals(confidenceScore, selection.getConfidenceScore(TextClassifier.TYPE_URL),
                ACCEPTED_DELTA);
        assertEquals(0, selection.getConfidenceScore("random_type"), ACCEPTED_DELTA);
        assertEquals(id, selection.getId());
    }

    @Test
    public void testTextSelection_defaultValues() {
        TextSelection original = new TextSelection.Builder(START, END).build();

        TextSelection selection = parcelizeDeparcelize(original, TextSelection.CREATOR);

        assertThat(selection.getEntityCount()).isEqualTo(0);
        assertThat(selection.getId()).isNull();
        assertThat(selection.getExtras().isEmpty()).isTrue();
        if (BuildCompat.isAtLeastS()) {
            assertThat(selection.getTextClassification()).isNull();
        }
    }

    @Test
    public void testTextSelection_prunedConfidenceScore() {
        final float phoneScore = -0.1f;
        final float prunedPhoneScore = 0f;
        final float otherScore = 1.5f;
        final float prunedOtherScore = 1.0f;

        final TextSelection selection = new TextSelection.Builder(START, END)
                .setEntityType(TextClassifier.TYPE_PHONE, phoneScore)
                .setEntityType(TextClassifier.TYPE_OTHER, otherScore)
                .build();

        assertEquals(prunedPhoneScore, selection.getConfidenceScore(TextClassifier.TYPE_PHONE),
                ACCEPTED_DELTA);
        assertEquals(prunedOtherScore, selection.getConfidenceScore(TextClassifier.TYPE_OTHER),
                ACCEPTED_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTextSelection_invalidStartParams() {
        new TextSelection.Builder(-1 /* start */, END)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTextSelection_invalidEndParams() {
        new TextSelection.Builder(START, 0 /* end */)
                .build();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testTextSelection_entityIndexOutOfBounds() {
        final TextSelection selection = new TextSelection.Builder(START, END).build();
        final int outOfBoundsIndex = selection.getEntityCount();
        selection.getEntity(outOfBoundsIndex);
    }

    @Test
    public void testTextSelectionRequest() {
        final TextSelection.Request.Builder originalBuilder =
                new TextSelection.Request.Builder(TEXT, START, END)
                .setDefaultLocales(LOCALES)
                .setExtras(BUNDLE);
        if (BuildCompat.isAtLeastS()) {
            originalBuilder.setIncludeTextClassification(true);
        }
        final TextSelection.Request original = originalBuilder.build();

        TextSelection.Request request =
                parcelizeDeparcelize(original, TextSelection.Request.CREATOR);

        assertThat(request.getText().toString()).isEqualTo(TEXT);
        assertThat(request.getStartIndex()).isEqualTo(START);
        assertThat(request.getEndIndex()).isEqualTo(END);
        assertThat(request.getDefaultLocales()).isEqualTo(LOCALES);
        assertThat(request.getExtras().getString(BUNDLE_KEY)).isEqualTo(BUNDLE_VALUE);
        if (BuildCompat.isAtLeastS()) {
            assertThat(request.shouldIncludeTextClassification()).isEqualTo(true);
        }
    }

    @Test
    public void testTextSelectionRequest_nullValues() {
        final TextSelection.Request original =
                new TextSelection.Request.Builder(TEXT, START, END)
                        .setDefaultLocales(null)
                        .build();

        TextSelection.Request request =
                parcelizeDeparcelize(original, TextSelection.Request.CREATOR);

        assertNull(request.getDefaultLocales());
    }

    @Test
    public void testTextSelectionRequest_defaultValues() {
        final TextSelection.Request original =
                new TextSelection.Request.Builder(TEXT, START, END).build();

        TextSelection.Request request =
                parcelizeDeparcelize(original, TextSelection.Request.CREATOR);

        assertThat(request.getDefaultLocales()).isNull();
        assertThat(request.getExtras().isEmpty()).isTrue();
        if (BuildCompat.isAtLeastS()) {
            assertThat(request.shouldIncludeTextClassification()).isFalse();
        }
    }

    private static <T extends Parcelable> T parcelizeDeparcelize(
            T parcelable, Parcelable.Creator<T> creator) {
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return creator.createFromParcel(parcel);
    }
}
