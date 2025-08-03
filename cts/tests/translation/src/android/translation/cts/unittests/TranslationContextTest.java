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

import android.icu.util.ULocale;
import android.os.Parcel;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationSpec;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationContextTest {

    private final TranslationSpec sourceSpec =
            new TranslationSpec(ULocale.ENGLISH, TranslationSpec.DATA_FORMAT_TEXT);
    private final TranslationSpec targetSpec =
            new TranslationSpec(ULocale.FRENCH, TranslationSpec.DATA_FORMAT_TEXT);

    @Test
    public void testContext_nullSpecs() {
        assertThrows(NullPointerException.class,
                () -> new TranslationContext.Builder(null, targetSpec));
        assertThrows(NullPointerException.class,
                () -> new TranslationContext.Builder(sourceSpec, null));
    }

    @Test
    public void testContext_validContext() {
        final TranslationContext context =
                new TranslationContext.Builder(sourceSpec, targetSpec)
                .setTranslationFlags(TranslationContext.FLAG_DEFINITIONS)
                .build();

        assertThat(context.getTranslationFlags())
                .isEqualTo(TranslationContext.FLAG_DEFINITIONS);

        assertThat(context.getSourceSpec().getLocale()).isEqualTo(ULocale.ENGLISH);
        assertThat(context.getSourceSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(context.getTargetSpec().getLocale()).isEqualTo(ULocale.FRENCH);
        assertThat(context.getTargetSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
    }

    @Test
    public void testParceledContext() {
        final TranslationContext context = new TranslationContext.Builder(sourceSpec, targetSpec)
                .build();

        assertThat(context.getTranslationFlags()).isEqualTo(0);

        assertThat(context.getSourceSpec().getLocale()).isEqualTo(ULocale.ENGLISH);
        assertThat(context.getSourceSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(context.getTargetSpec().getLocale()).isEqualTo(ULocale.FRENCH);
        assertThat(context.getTargetSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        final Parcel parcel = Parcel.obtain();
        context.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationContext parceledContext =
                TranslationContext.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(parceledContext.getTranslationFlags()).isEqualTo(0);

        assertThat(parceledContext.getSourceSpec().getLocale()).isEqualTo(ULocale.ENGLISH);
        assertThat(parceledContext.getSourceSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(parceledContext.getTargetSpec().getLocale()).isEqualTo(ULocale.FRENCH);
        assertThat(parceledContext.getTargetSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
    }
}
