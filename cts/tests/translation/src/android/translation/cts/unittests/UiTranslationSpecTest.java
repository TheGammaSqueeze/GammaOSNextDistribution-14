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

import android.os.Parcel;
import android.view.translation.UiTranslationSpec;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link UiTranslationSpec} related APIs.
 */
@RunWith(AndroidJUnit4.class)
public class UiTranslationSpecTest {

    @Test
    public void testUiTranslationSpec_defaultBuilder() {
        final UiTranslationSpec uiSpec = new UiTranslationSpec.Builder().build();

        assertThat(uiSpec.shouldPadContentForCompat()).isFalse();
    }

    @Test
    public void testUiTranslationSpec_builder_setShouldPadContentForCompat() {
        final UiTranslationSpec uiSpec = new UiTranslationSpec.Builder()
                .setShouldPadContentForCompat(true).build();

        assertThat(uiSpec.shouldPadContentForCompat()).isTrue();
    }

    @Test
    public void testParceledUiTranslationSpec() {
        final UiTranslationSpec uiSpec = new UiTranslationSpec.Builder()
                .setShouldPadContentForCompat(true).build();

        final Parcel parcel = Parcel.obtain();
        uiSpec.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final UiTranslationSpec parceledSpec = UiTranslationSpec.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(parceledSpec).isEqualTo(uiSpec);
    }
}
