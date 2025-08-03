/**
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.broadcastradio.support.platform;

import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioMetadata;
import android.support.v4.media.MediaMetadataCompat;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Comparator;

@RunWith(MockitoJUnitRunner.class)
public final class ProgramInfoExtTest {

    private static final long FM_FREQUENCY = 88_500;
    private static final String RDS_VALUE = "TestRds";
    private static final String PROGRAM_NAME_VALUE = "TestProgramName";
    private static final String TITLE_VALUE = "TestTitle";
    private static final String ARTIST_VALUE = "TestArtist";
    private static final String ALBUM_VALUE = "TestAlbum";
    private static final int ART_VALUE = 1;
    private static final ProgramSelector.Identifier FM_IDENTIFIER = new ProgramSelector.Identifier(
            ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY, FM_FREQUENCY);
    private static final ProgramSelector FM_SELECTOR = new ProgramSelector(
            ProgramSelector.PROGRAM_TYPE_FM, FM_IDENTIFIER, /* secondaryIds= */ null,
            /* vendorIds= */ null);
    private static final ProgramSelector.Identifier DAB_DMB_SID_EXT_IDENTIFIER =
            new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_DAB_DMB_SID_EXT,
                    0xA000000111L);
    private static final ProgramSelector.Identifier DAB_ENSEMBLE_IDENTIFIER =
            new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_DAB_ENSEMBLE,
                    /* value= */ 0x1001);
    private static final ProgramSelector.Identifier DAB_FREQUENCY_IDENTIFIER =
            new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_DAB_FREQUENCY,
                    /* value= */ 220_352);
    private static final ProgramSelector DAB_SELECTOR = new ProgramSelector(
            ProgramSelector.PROGRAM_TYPE_DAB, DAB_DMB_SID_EXT_IDENTIFIER,
            new ProgramSelector.Identifier[]{DAB_FREQUENCY_IDENTIFIER, DAB_ENSEMBLE_IDENTIFIER},
            /* vendorIds= */ null);
    private static final RadioMetadata RADIO_METADATA = new RadioMetadata.Builder()
            .putString(RadioMetadata.METADATA_KEY_RDS_PS, RDS_VALUE)
            .putString(RadioMetadata.METADATA_KEY_PROGRAM_NAME, PROGRAM_NAME_VALUE)
            .putString(RadioMetadata.METADATA_KEY_TITLE, TITLE_VALUE)
            .putString(RadioMetadata.METADATA_KEY_ARTIST, ARTIST_VALUE)
            .putString(RadioMetadata.METADATA_KEY_ALBUM, ALBUM_VALUE)
            .putInt(RadioMetadata.METADATA_KEY_ART, ART_VALUE).build();
    private static final RadioMetadata EMPTY_RADIO_METADATA = new RadioMetadata.Builder().build();
    private static final RadioManager.ProgramInfo FM_INFO = new RadioManager.ProgramInfo(
            FM_SELECTOR, FM_IDENTIFIER, FM_IDENTIFIER, /* relatedContents= */ null,
            /* infoFlags= */ 0, /* signalQuality= */ 1, RADIO_METADATA,
            /* vendorInfo= */ null);
    private static final RadioManager.ProgramInfo FM_INFO_WITH_EMPTY_METADATA =
            new RadioManager.ProgramInfo(FM_SELECTOR, FM_IDENTIFIER, FM_IDENTIFIER,
                    /* relatedContents= */ null, /* infoFlags= */ 0, /* signalQuality= */ 1,
                    EMPTY_RADIO_METADATA, /* vendorInfo= */ null);
    private static final RadioManager.ProgramInfo DAB_INFO_WITH_EMPTY_METADATA =
            new RadioManager.ProgramInfo(DAB_SELECTOR, DAB_DMB_SID_EXT_IDENTIFIER,
                    DAB_FREQUENCY_IDENTIFIER, /* relatedContents= */ null, /* infoFlags= */ 0,
                    /* signalQuality= */ 1, EMPTY_RADIO_METADATA, /* vendorInfo= */ null);

    private static final Comparator<ProgramSelector> SELECTOR_COMPARATOR =
            new ProgramSelectorExt.ProgramSelectorComparator();
    private static final Comparator<RadioManager.ProgramInfo> PROGRAM_INFO_COMPARATOR =
            new ProgramInfoExt.ProgramInfoComparator();

    @Rule
    public final Expect mExpect = Expect.create();

    @Test
    public void getProgramName() {
        mExpect.withMessage("Program name")
                .that(ProgramInfoExt.getProgramName(FM_INFO, /* flags= */ 0))
                .isEqualTo(PROGRAM_NAME_VALUE);
    }

    @Test
    public void getProgramName_withProgramNameOrder() {
        String[] programNameOrder = new String[] {RadioMetadata.METADATA_KEY_RDS_PS,
                RadioMetadata.METADATA_KEY_PROGRAM_NAME};

        mExpect.withMessage("Program name with self-defined program name order")
                .that(ProgramInfoExt.getProgramName(FM_INFO, /* flags= */ 0, programNameOrder))
                .isEqualTo(RDS_VALUE);
    }

    @Test
    public void getProgramName_forFmSelectorWithoutProgramNameMetadata() {
        String expectedName = ProgramSelectorExt.formatAmFmFrequency(
                FM_INFO_WITH_EMPTY_METADATA.getPhysicallyTunedTo().getValue(), /* flags= */ 0);

        mExpect.withMessage("FM Program name without program name metadata")
                .that(ProgramInfoExt.getProgramName(FM_INFO_WITH_EMPTY_METADATA, /* flags= */ 0))
                .isEqualTo(expectedName);
    }

    @Test
    public void getProgramName_forDabProgramWithoutProgramNameMetadata() {
        String expectedName = ProgramSelectorExt.getDisplayName(
                DAB_INFO_WITH_EMPTY_METADATA.getSelector(), /* flags= */ 0);

        mExpect.withMessage("DAB Program name without program name metadata")
                .that(ProgramInfoExt.getProgramName(DAB_INFO_WITH_EMPTY_METADATA, /* flags= */ 0))
                .isEqualTo(expectedName);
    }

    @Test
    public void getMetadata() {
        mExpect.withMessage("FM radio metadata")
                .that(ProgramInfoExt.getMetadata(FM_INFO)).isEqualTo(RADIO_METADATA);
    }

    @Test
    public void getMetadata_withNullMetadata() {
        RadioManager.ProgramInfo infoWithNullMetadata = new RadioManager.ProgramInfo(
                FM_SELECTOR, FM_IDENTIFIER, FM_IDENTIFIER, /* relatedContents= */ null,
                /* infoFlags= */ 0, /* signalQuality= */ 1, /* metadata= */ null,
                /* vendorInfo= */ null);

        mExpect.withMessage("FM radio metadata with null metadata")
                .that(ProgramInfoExt.getMetadata(infoWithNullMetadata))
                .isEqualTo(EMPTY_RADIO_METADATA);
    }

    @Test
    public void toMediaDisplayMetadata() {
        String[] programNameOrder = new String[] {RadioMetadata.METADATA_KEY_RDS_PS,
                RadioMetadata.METADATA_KEY_PROGRAM_NAME};
        Bitmap bitmapMock = Mockito.mock(Bitmap.class);
        ImageResolver imageResolver = Mockito.mock(ImageResolver.class);
        when(imageResolver.resolve(ART_VALUE)).thenReturn(bitmapMock);

        MediaMetadataCompat mediaDisplayMetadata = ProgramInfoExt.toMediaDisplayMetadata(FM_INFO,
                /* isFavorite= */ true, imageResolver, programNameOrder);

        mExpect.withMessage("Media display title in media display metadata")
                .that(mediaDisplayMetadata.getString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
                .isEqualTo(ProgramSelectorExt.getDisplayName(FM_INFO.getSelector(),
                        FM_INFO.getChannel()));
        mExpect.withMessage("Media display subtitle in media display metadata")
                .that(mediaDisplayMetadata.getString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE))
                .isEqualTo(ProgramInfoExt.getProgramName(FM_INFO, /* flags= */ 0,
                        programNameOrder));
        mExpect.withMessage("Album art in media display metadata")
                .that(mediaDisplayMetadata.getBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART)).isEqualTo(bitmapMock);
    }

    @Test
    public void toMediaMetadata() {
        MediaMetadataCompat mediaMetadata = ProgramInfoExt.toMediaMetadata(FM_INFO,
                /* isFavorite= */ true, /* imageResolver= */ null);

        mExpect.withMessage("Media display title").that(mediaMetadata
                        .getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
                .isEqualTo(ProgramInfoExt.getProgramName(FM_INFO, /* flags= */ 0));
        mExpect.withMessage("Media title").that(mediaMetadata.getString(
                MediaMetadataCompat.METADATA_KEY_TITLE)).isEqualTo(TITLE_VALUE);
        mExpect.withMessage("Media artist").that(mediaMetadata.getString(
                MediaMetadataCompat.METADATA_KEY_ARTIST)).isEqualTo(ARTIST_VALUE);
        mExpect.withMessage("Media album").that(mediaMetadata.getString(
                MediaMetadataCompat.METADATA_KEY_ALBUM)).isEqualTo(ALBUM_VALUE);
    }

    @Test
    public void compare_withSelectorsOfDifferentTypes() {
        ProgramSelector fmSel2 = ProgramSelectorExt.createAmFmSelector(FM_FREQUENCY + 200);
        RadioManager.ProgramInfo fmInfo2 = new RadioManager.ProgramInfo(fmSel2,
                fmSel2.getPrimaryId(), fmSel2.getPrimaryId(), /* relatedContents= */ null,
                /* infoFlags= */ 1, /* signalQuality= */ 0, new RadioMetadata.Builder().build(),
                /* vendorInfo= */ null);
        int expectedResult = SELECTOR_COMPARATOR.compare(fmSel2, FM_SELECTOR);

        mExpect.withMessage("Comparison between FM stations")
                .that(PROGRAM_INFO_COMPARATOR.compare(fmInfo2, FM_INFO)).isEqualTo(expectedResult);
    }
}
