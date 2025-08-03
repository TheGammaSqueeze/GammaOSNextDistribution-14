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

import android.os.Bundle;
import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponseValue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class TranslationValueTest {

    @Test
    public void testTranslationRequestValue_forText() {
        final TranslationRequestValue value = TranslationRequestValue.forText("sample text");

        assertThat(value.getText()).isEqualTo("sample text");
    }

    @Test
    public void testTranslationResponseValue_validBuilder() {
        final TranslationResponseValue value = new TranslationResponseValue.Builder(STATUS_SUCCESS)
                .setText("sample text")
                .build();

        assertThat(value.getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(value.getText()).isEqualTo("sample text");
    }

    @Test
    public void testTranslationResponseValue_forError() {
        final TranslationResponseValue value = TranslationResponseValue.forError();

        assertThat(value.getStatusCode()).isEqualTo(TranslationResponseValue.STATUS_ERROR);
        assertThat(value.getText()).isNull();
    }

    @Test
    public void testTranslationResponseValue_validDictionary() {
        final Bundle definitions = new Bundle();
        definitions.putStringArrayList("noun",
                new ArrayList<>(Arrays.asList("def1", "def2")));
        definitions.putStringArrayList("verb",
                new ArrayList<>(Arrays.asList("def3", "def4")));
        final Bundle extras = new Bundle();
        extras.putBundle(TranslationResponseValue.EXTRA_DEFINITIONS, definitions);

        final TranslationResponseValue value = new TranslationResponseValue.Builder(STATUS_SUCCESS)
                .setExtras(extras)
                .build();

        assertThat(value.getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(value.getText()).isNull();
        assertThat(value.getTransliteration()).isNull();
        assertThat(value.getExtras()).isNotNull();

        final Bundle e = value.getExtras();
        assertThat(e).isNotNull();
        final Bundle defs = e.getBundle(TranslationResponseValue.EXTRA_DEFINITIONS);
        assertThat(defs).isNotNull();
        assertThat(defs.keySet()).containsExactly("noun", "verb");
        assertThat(defs.getStringArrayList("noun"))
                .containsExactly("def1", "def2");
        assertThat(defs.getStringArrayList("verb"))
                .containsExactly("def3", "def4");
    }

    @Test
    public void testTranslationResponseValue_extrasEqualityWhenSameInstance() {
        final Bundle extras = new Bundle();
        final TranslationResponseValue extras1 =
                new TranslationResponseValue.Builder(STATUS_SUCCESS).setExtras(extras).build();
        final TranslationResponseValue extras2 =
                new TranslationResponseValue.Builder(STATUS_SUCCESS).setExtras(extras).build();

        assertThat(extras1).isEqualTo(extras2);
    }

    @Test
    public void testTranslationResponseValue_extrasEqualityWhenEmpty() {
        final TranslationResponseValue defaultValue =
                new TranslationResponseValue.Builder(STATUS_SUCCESS).build();
        final Bundle extras = new Bundle();
        final TranslationResponseValue valueWithEmptyExtras =
                new TranslationResponseValue.Builder(STATUS_SUCCESS).setExtras(extras).build();

        assertThat(defaultValue).isEqualTo(valueWithEmptyExtras);
    }

    @Test
    public void testTranslationResponseValue_extrasEqualityWhenUnequal() {
        final TranslationResponseValue extras1 =
                new TranslationResponseValue.Builder(STATUS_SUCCESS).build();
        final Bundle extras = new Bundle();
        extras.putString("k", "v");
        final TranslationResponseValue extras2 =
                new TranslationResponseValue.Builder(STATUS_SUCCESS).setExtras(extras).build();

        assertThat(extras1).isNotEqualTo(extras2);
    }

    @Test
    public void testTranslationResponseValue_validTransliteration() {
        final TranslationResponseValue value = new TranslationResponseValue.Builder(STATUS_SUCCESS)
                .setTransliteration("pronunciation")
                .build();

        assertThat(value.getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(value.getText()).isNull();
        assertThat(value.getExtras()).isEqualTo(Bundle.EMPTY);
        assertThat(value.getTransliteration()).isEqualTo("pronunciation");
    }
}
