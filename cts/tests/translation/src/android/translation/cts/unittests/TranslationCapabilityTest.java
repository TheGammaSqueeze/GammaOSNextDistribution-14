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
import android.view.translation.TranslationCapability;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationSpec;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationCapabilityTest {

    private final TranslationSpec mSourceSpec =
            new TranslationSpec(ULocale.ENGLISH, TranslationSpec.DATA_FORMAT_TEXT);
    private final TranslationSpec mTargetSpec =
            new TranslationSpec(ULocale.FRENCH, TranslationSpec.DATA_FORMAT_TEXT);

    @Test
    public void testCapability_nullSpecs() {
        assertThrows(NullPointerException.class,
                () -> new TranslationCapability(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD,
                        null, mTargetSpec, /* uiTranslationEnabled= */ true,
                        /* supportedTranslationFlags= */ 0));
        assertThrows(NullPointerException.class,
                () -> new TranslationCapability(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD,
                        mSourceSpec, null,/* uiTranslationEnabled= */ true,
                        /* supportedTranslationFlags= */ 0));
    }

    @Test
    public void testCapability_validCapability() {
        final TranslationCapability capability =
                new TranslationCapability(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD,
                        mSourceSpec, mTargetSpec,/* uiTranslationEnabled= */ true,
                        TranslationContext.FLAG_TRANSLITERATION);

        assertThat(capability.getState())
                .isEqualTo(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD);
        assertThat(capability.getSupportedTranslationFlags())
                .isEqualTo(TranslationContext.FLAG_TRANSLITERATION);
        assertThat(capability.isUiTranslationEnabled()).isTrue();

        assertThat(capability.getSourceSpec().getLocale()).isEqualTo(ULocale.ENGLISH);
        assertThat(capability.getSourceSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(capability.getTargetSpec().getLocale()).isEqualTo(ULocale.FRENCH);
        assertThat(capability.getTargetSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
    }

    @Test
    public void testParceledCapability() {
        final TranslationCapability capability =
                new TranslationCapability(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD,
                        mSourceSpec, mTargetSpec,/* uiTranslationEnabled= */ true,
                        TranslationContext.FLAG_TRANSLITERATION);

        assertThat(capability.getState())
                .isEqualTo(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD);
        assertThat(capability.getSupportedTranslationFlags())
                .isEqualTo(TranslationContext.FLAG_TRANSLITERATION);
        assertThat(capability.isUiTranslationEnabled()).isTrue();

        assertThat(capability.getSourceSpec().getLocale()).isEqualTo(ULocale.ENGLISH);
        assertThat(capability.getSourceSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(capability.getTargetSpec().getLocale()).isEqualTo(ULocale.FRENCH);
        assertThat(capability.getTargetSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        final Parcel parcel = Parcel.obtain();
        capability.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationCapability parceledCapability =
                TranslationCapability.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(parceledCapability.getState())
                .isEqualTo(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD);
        assertThat(parceledCapability.getSupportedTranslationFlags())
                .isEqualTo(TranslationContext.FLAG_TRANSLITERATION);
        assertThat(parceledCapability.isUiTranslationEnabled()).isTrue();

        assertThat(parceledCapability.getSourceSpec().getLocale()).isEqualTo(ULocale.ENGLISH);
        assertThat(parceledCapability.getSourceSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);

        assertThat(parceledCapability.getTargetSpec().getLocale()).isEqualTo(ULocale.FRENCH);
        assertThat(parceledCapability.getTargetSpec().getDataFormat())
                .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
    }

    @Test
    public void testCapability_allModelStates() {
        new TranslationCapability(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD,
                mSourceSpec, mTargetSpec, /* uiTranslationEnabled= */ true,
                /* supportedTranslationFlags= */ 0);
        new TranslationCapability(TranslationCapability.STATE_DOWNLOADING,
                mSourceSpec, mTargetSpec,/* uiTranslationEnabled= */ true,
                /* supportedTranslationFlags= */ 0);
        new TranslationCapability(TranslationCapability.STATE_ON_DEVICE,
                mSourceSpec, mTargetSpec, /* uiTranslationEnabled= */ true,
                /* supportedTranslationFlags= */ 0);
        new TranslationCapability(TranslationCapability.STATE_NOT_AVAILABLE,
                mSourceSpec, mTargetSpec,/* uiTranslationEnabled= */ true,
                /* supportedTranslationFlags= */ 0);

        // TODO: Remove magic constant and expose to test api later
        new TranslationCapability(/* STATE_REMOVED_AND_AVAILABLE= */ 1000,
                mSourceSpec, mTargetSpec, /* uiTranslationEnabled= */ true,
                /* supportedTranslationFlags= */ 0);
    }
}
