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
import android.view.translation.TranslationSpec;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationSpecTest {

    @Test
    public void testSpec_nullLanguage() {
        assertThrows(NullPointerException.class, () -> {
            final TranslationSpec spec =
                    new TranslationSpec((ULocale) null, TranslationSpec.DATA_FORMAT_TEXT);
        });
    }

    @Test
    public void testSpec_validSpec() {
        final TranslationSpec spec = new TranslationSpec(ULocale.ENGLISH, TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(spec.getDataFormat()).isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
        assertThat(spec.getLocale()).isEqualTo(ULocale.ENGLISH);
    }

    @Test
    public void testParceledSpec() {
        final TranslationSpec spec = new TranslationSpec(ULocale.ENGLISH, TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(spec.getDataFormat()).isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
        assertThat(spec.getLocale()).isEqualTo(ULocale.ENGLISH);

        final Parcel parcel = Parcel.obtain();
        spec.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationSpec parceledSpec = TranslationSpec.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(parceledSpec.getDataFormat()).isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
        assertThat(parceledSpec.getLocale()).isEqualTo(ULocale.ENGLISH);
    }

}