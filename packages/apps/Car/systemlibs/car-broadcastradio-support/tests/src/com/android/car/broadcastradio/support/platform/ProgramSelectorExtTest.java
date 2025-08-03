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

import static org.junit.Assert.assertThrows;

import android.hardware.radio.ProgramSelector;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

import java.util.Comparator;

public final class ProgramSelectorExtTest {

    private static final long AM_FREQUENCY_VALUE = 700;
    private static final long FM_FREQUENCY_VALUE = 88_500;
    private static final long HD_FREQUENCY_VALUE = 97_100;
    private static final int HD_SUBCHANNEL_VALUE = 1;
    private static final int HD_STATION_ID_VALUE = 0x01;
    private static final long DAB_FREQUENCY_VALUE = 220_352;
    private static final long DAB_ENSEMBLE_VALUE = 0x1001;
    private static final long DAB_SID_VALUE = 0x112;
    private static final int DAB_ECC_VALUE = 0xA0;
    private static final int DAB_SCIDS_VALUE = 1;
    private static final ProgramSelector.Identifier FM_IDENTIFIER = new ProgramSelector.Identifier(
            ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY, FM_FREQUENCY_VALUE);
    private static final ProgramSelector.Identifier HD_STATION_EXT_IDENTIFIER =
            new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_HD_STATION_ID_EXT,
                    (HD_FREQUENCY_VALUE << 36) | ((long) HD_SUBCHANNEL_VALUE << 32)
                            | HD_STATION_ID_VALUE);
    private static final ProgramSelector.Identifier DAB_DMB_SID_EXT_IDENTIFIER =
            createDabSidExtId(DAB_SID_VALUE, DAB_ECC_VALUE, DAB_SCIDS_VALUE);
    private static final ProgramSelector.Identifier DAB_ENSEMBLE_IDENTIFIER =
            new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_DAB_ENSEMBLE,
                    DAB_ENSEMBLE_VALUE);
    private static final ProgramSelector.Identifier DAB_FREQUENCY_IDENTIFIER =
            new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_DAB_FREQUENCY,
                    DAB_FREQUENCY_VALUE);
    private static final ProgramSelector FM_SELECTOR = new ProgramSelector(
            ProgramSelector.PROGRAM_TYPE_FM, FM_IDENTIFIER, /* secondaryIds= */ null,
            /* vendorIds= */ null);
    private static final ProgramSelector HD_SELECTOR = new ProgramSelector(
            ProgramSelector.PROGRAM_TYPE_FM_HD, HD_STATION_EXT_IDENTIFIER,
            new ProgramSelector.Identifier[]{}, /* vendorIds= */ null);
    private static final ProgramSelector DAB_SELECTOR = new ProgramSelector(
            ProgramSelector.PROGRAM_TYPE_DAB, DAB_DMB_SID_EXT_IDENTIFIER,
            new ProgramSelector.Identifier[]{DAB_FREQUENCY_IDENTIFIER, DAB_ENSEMBLE_IDENTIFIER},
            /* vendorIds= */ null);
    private static final Comparator<ProgramSelector> SELECTOR_COMPARATOR =
            new ProgramSelectorExt.ProgramSelectorComparator();

    @Rule
    public final Expect mExpect = Expect.create();

    @Test
    public void isAmFrequency_withAmFrequency() {
        mExpect.withMessage("AM frequency")
                .that(ProgramSelectorExt.isAmFrequency(AM_FREQUENCY_VALUE)).isTrue();
    }

    @Test
    public void isAmFrequency_withFmFrequency() {
        mExpect.withMessage("Non-AM frequency")
                .that(ProgramSelectorExt.isAmFrequency(FM_FREQUENCY_VALUE)).isFalse();
    }

    @Test
    public void isFmFrequency_withAmFrequency() {
        mExpect.withMessage("Non-FM frequency")
                .that(ProgramSelectorExt.isFmFrequency(AM_FREQUENCY_VALUE)).isFalse();
    }

    @Test
    public void isFmFrequency_withFmFrequency() {
        mExpect.withMessage("FM frequency")
                .that(ProgramSelectorExt.isFmFrequency(FM_FREQUENCY_VALUE)).isTrue();
    }

    @Test
    public void createAmFmSelector() {
        mExpect.withMessage("FM selector").that(ProgramSelectorExt.createAmFmSelector(
                FM_FREQUENCY_VALUE)).isEqualTo(FM_SELECTOR);
    }

    @Test
    public void createAmFmSelector_withInvalidFrequency_throwsException() {
        long invalidFrequency = -1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            ProgramSelectorExt.createAmFmSelector(invalidFrequency);
        });

        mExpect.withMessage("Invalid frequency exception").that(thrown).hasMessageThat()
                .contains("illegal frequency value");
    }

    @Test
    public void hasId_forSelectorWithIdAsPrimaryId() {
        mExpect.withMessage("AM/FM id in selector").that(ProgramSelectorExt.hasId(FM_SELECTOR,
                ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)).isTrue();
    }

    @Test
    public void hasId_forSelectorWithIdInSecondaryIds() {
        mExpect.withMessage("DAB ensemble id in selector").that(ProgramSelectorExt.hasId(
                DAB_SELECTOR, ProgramSelector.IDENTIFIER_TYPE_DAB_FREQUENCY)).isTrue();
    }

    @Test
    public void hasId_forSelectorWithoutId() {
        mExpect.withMessage("Id type beyond selector ids").that(ProgramSelectorExt.hasId(
                HD_SELECTOR, ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)).isFalse();
    }

    @Test
    public void isAmFmProgram_withAmFmSelector() {
        mExpect.withMessage("FM selector of AM/FM type program")
                .that(ProgramSelectorExt.isAmFmProgram(FM_SELECTOR)).isTrue();
    }

    @Test
    public void isAmFmProgram_withHdSelector() {
        mExpect.withMessage("HD selector of AM/FM type program")
                .that(ProgramSelectorExt.isAmFmProgram(HD_SELECTOR)).isTrue();
    }

    @Test
    public void isAmFmProgram_withDabSelector() {
        mExpect.withMessage("DAB selector of non-AM/FM type program")
                .that(ProgramSelectorExt.isAmFmProgram(DAB_SELECTOR)).isFalse();
    }

    @Test
    public void getFrequency_withAmFmSelector() {
        mExpect.withMessage("Frequency of FM selector")
                .that(ProgramSelectorExt.getFrequency(FM_SELECTOR)).isEqualTo(FM_FREQUENCY_VALUE);
    }

    @Test
    public void getFrequency_withHdSelector() {
        mExpect.withMessage("Frequency of HD selector")
                .that(ProgramSelectorExt.getFrequency(HD_SELECTOR)).isEqualTo(HD_FREQUENCY_VALUE);
    }

    @Test
    public void getFrequency_withDabSelector() {
        mExpect.withMessage("Frequency of DAB selector")
                .that(ProgramSelectorExt.getFrequency(DAB_SELECTOR)).isEqualTo(DAB_FREQUENCY_VALUE);
    }

    @Test
    public void getFrequency_withSelectorWithoutFrequency() {
        ProgramSelector dabSelectorWithoutFrequency = new ProgramSelector(
                ProgramSelector.PROGRAM_TYPE_DAB, DAB_DMB_SID_EXT_IDENTIFIER,
                new ProgramSelector.Identifier[]{DAB_ENSEMBLE_IDENTIFIER}, /* vendorIds= */ null);

        mExpect.withMessage("Frequency of a selector without frequency")
                .that(ProgramSelectorExt.getFrequency(dabSelectorWithoutFrequency))
                .isEqualTo(ProgramSelectorExt.INVALID_IDENTIFIER_VALUE);
    }

    @Test
    public void getDabEnsemble_withDabSelector() {
        mExpect.withMessage("Ensemble of DAB selector").that(
                ProgramSelectorExt.getDabEnsemble(DAB_SELECTOR)).isEqualTo(DAB_ENSEMBLE_VALUE);
    }

    @Test
    public void getDabEnsemble_withNonDabSelector() {
        mExpect.withMessage("Ensemble of FM selector")
                .that(ProgramSelectorExt.getDabEnsemble(FM_SELECTOR))
                .isEqualTo(ProgramSelectorExt.INVALID_IDENTIFIER_VALUE);
    }

    @Test
    public void asHdPrimary_withNonHdId() {
        mExpect.withMessage("HD Primary identifier extension for FM id")
                .that(ProgramSelectorExt.IdentifierExt.asHdPrimary(FM_IDENTIFIER)).isNull();
    }

    @Test
    public void getStationId_forHdPrimary() {
        mExpect.withMessage("Station id of HD primary identifier extension")
                .that(ProgramSelectorExt.IdentifierExt.asHdPrimary(HD_STATION_EXT_IDENTIFIER)
                        .getStationId()).isEqualTo(HD_STATION_ID_VALUE);
    }

    @Test
    public void getSubchannel_forHdPrimary() {
        mExpect.withMessage("Sub-channel of HD primary identifier extension")
                .that(ProgramSelectorExt.IdentifierExt.asHdPrimary(HD_STATION_EXT_IDENTIFIER)
                        .getSubchannel()).isEqualTo(HD_SUBCHANNEL_VALUE);
    }

    @Test
    public void getFrequency_forHdPrimary() {
        mExpect.withMessage("Frequency of HD primary identifier extension")
                .that(ProgramSelectorExt.IdentifierExt.asHdPrimary(HD_STATION_EXT_IDENTIFIER)
                        .getFrequency()).isEqualTo(HD_FREQUENCY_VALUE);
    }

    @Test
    public void asDabPrimary_withNonDabId() {
        mExpect.withMessage("DAB Primary identifier extension for FM id")
                .that(ProgramSelectorExt.IdentifierExt.asDabPrimary(FM_IDENTIFIER)).isNull();
    }
    @Test
    public void getSId_forDabPrimary() {
        mExpect.withMessage("Station Id of DAB primary identifier extension")
                .that(ProgramSelectorExt.IdentifierExt.asDabPrimary(DAB_DMB_SID_EXT_IDENTIFIER)
                        .getSId()).isEqualTo(DAB_SID_VALUE);
    }

    @Test
    public void getEcc_forDabPrimary() {
        mExpect.withMessage("ECC of DAB primary identifier extension")
                .that(ProgramSelectorExt.IdentifierExt.asDabPrimary(DAB_DMB_SID_EXT_IDENTIFIER)
                        .getEcc()).isEqualTo(DAB_ECC_VALUE);
    }

    @Test
    public void getSCIdS_forDabPrimary() {
        mExpect.withMessage("SCIdS of DAB primary identifier extension")
                .that(ProgramSelectorExt.IdentifierExt.asDabPrimary(DAB_DMB_SID_EXT_IDENTIFIER)
                        .getSCIdS()).isEqualTo(DAB_SCIDS_VALUE);
    }

    @Test
    public void compare_withTwoFmSelectorsForProgramSelectorComparator() {
        ProgramSelector fmSel2 = ProgramSelectorExt.createAmFmSelector(FM_FREQUENCY_VALUE + 200);

        mExpect.withMessage("Comparison between two FM selectors")
                .that(SELECTOR_COMPARATOR.compare(FM_SELECTOR, fmSel2)).isEqualTo(-1);
    }

    @Test
    public void compare_withFmAndHdSelectors() {
        mExpect.withMessage("Comparison between FM and HD selectors")
                .that(SELECTOR_COMPARATOR.compare(FM_SELECTOR, HD_SELECTOR)).isEqualTo(-1);
    }

    @Test
    public void compare_withHdSelectorsWithDifferentFrequncies() {
        ProgramSelector.Identifier hdId = new ProgramSelector.Identifier(
                ProgramSelector.IDENTIFIER_TYPE_HD_STATION_ID_EXT,
                ((HD_FREQUENCY_VALUE + 200) << 36) | HD_STATION_ID_VALUE);
        ProgramSelector hdSel = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_FM_HD, hdId,
                new ProgramSelector.Identifier[]{}, /* vendorIds= */ null);

        mExpect.withMessage("Comparison between HD selectors with different frequencies")
                .that(SELECTOR_COMPARATOR.compare(hdSel, HD_SELECTOR)).isEqualTo(1);
    }

    @Test
    public void compare_withHdSelectorsWithDifferentSubchannels() {
        ProgramSelector.Identifier hdId2 = new ProgramSelector.Identifier(
                ProgramSelector.IDENTIFIER_TYPE_HD_STATION_ID_EXT,
                (HD_FREQUENCY_VALUE << 36) | HD_STATION_ID_VALUE);
        ProgramSelector hdSel2 = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_FM_HD, hdId2,
                new ProgramSelector.Identifier[]{}, /* vendorIds= */ null);

        mExpect.withMessage("Comparison between HD selectors with different subchannels")
                .that(SELECTOR_COMPARATOR.compare(hdSel2, HD_SELECTOR)).isEqualTo(-1);
    }

    @Test
    public void compare_withDabSelectorsWithDifferentFrequencies() {
        ProgramSelector.Identifier dabFrequencyId2 = new ProgramSelector.Identifier(
                ProgramSelector.IDENTIFIER_TYPE_DAB_FREQUENCY, 174_928);
        ProgramSelector.Identifier dabEnsembleId2 = new ProgramSelector.Identifier(
                ProgramSelector.IDENTIFIER_TYPE_DAB_ENSEMBLE, DAB_ENSEMBLE_VALUE + 1);
        ProgramSelector dabSel2 = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_DAB,
                DAB_DMB_SID_EXT_IDENTIFIER,
                new ProgramSelector.Identifier[]{dabFrequencyId2, dabEnsembleId2},
                /* vendorIds= */ null);

        mExpect.withMessage("Comparison between DAB selectors with different frequencies")
                .that(SELECTOR_COMPARATOR.compare(dabSel2, DAB_SELECTOR)).isEqualTo(-1);
    }

    @Test
    public void compare_withDabSelectorsWithDifferentEccs() {
        ProgramSelector.Identifier dabExtId2 = createDabSidExtId(DAB_SID_VALUE - 1,
                DAB_ECC_VALUE + 1, DAB_SCIDS_VALUE + 1);
        ProgramSelector dabSel2 = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_DAB,
                dabExtId2, new ProgramSelector.Identifier[]{DAB_FREQUENCY_IDENTIFIER,
                        DAB_ENSEMBLE_IDENTIFIER}, /* vendorIds= */ null);

        mExpect.withMessage("Comparison between DAB selectors with different ECC values")
                .that(SELECTOR_COMPARATOR.compare(dabSel2, DAB_SELECTOR)).isEqualTo(1);
    }

    @Test
    public void compare_withDabSelectorsWithDifferentSIds() {
        ProgramSelector.Identifier dabExtId2 = createDabSidExtId(DAB_SID_VALUE - 1,
                DAB_ECC_VALUE, DAB_SCIDS_VALUE + 1);
        ProgramSelector dabSel2 = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_DAB,
                dabExtId2, new ProgramSelector.Identifier[]{DAB_FREQUENCY_IDENTIFIER,
                        DAB_ENSEMBLE_IDENTIFIER}, /* vendorIds= */ null);

        mExpect.withMessage("Comparison between DAB selectors with different SId values")
                .that(SELECTOR_COMPARATOR.compare(dabSel2, DAB_SELECTOR)).isEqualTo(-1);
    }

    @Test
    public void compare_withDabSelectorsWithDifferentSCIds() {
        ProgramSelector.Identifier dabExtId2 = createDabSidExtId(DAB_SID_VALUE,
                DAB_ECC_VALUE, DAB_SCIDS_VALUE + 1);
        ProgramSelector dabSel2 = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_DAB,
                dabExtId2, new ProgramSelector.Identifier[]{DAB_FREQUENCY_IDENTIFIER,
                        DAB_ENSEMBLE_IDENTIFIER}, /* vendorIds= */ null);

        mExpect.withMessage("Comparison between DAB selectors with different SCId values")
                .that(SELECTOR_COMPARATOR.compare(dabSel2, DAB_SELECTOR)).isEqualTo(1);
    }

    @Test
    public void compare_withDabSelectorsWithDifferentEnsembles() {
        ProgramSelector.Identifier dabEnsembleId2 = new ProgramSelector.Identifier(
                ProgramSelector.IDENTIFIER_TYPE_DAB_ENSEMBLE, DAB_ENSEMBLE_VALUE + 1);
        ProgramSelector dabSel2 = new ProgramSelector(ProgramSelector.PROGRAM_TYPE_DAB,
                DAB_DMB_SID_EXT_IDENTIFIER, new ProgramSelector.Identifier[]{
                        DAB_FREQUENCY_IDENTIFIER, dabEnsembleId2}, /* vendorIds= */ null);

        mExpect.withMessage("Comparison between DAB selectors with different ensembles")
                .that(SELECTOR_COMPARATOR.compare(dabSel2, DAB_SELECTOR)).isEqualTo(1);
    }

    @Test
    public void compare_withSelectorsOfDifferentTypes() {
        mExpect.withMessage("Comparison between HD and DAB selectors with different types")
                .that(SELECTOR_COMPARATOR.compare(HD_SELECTOR, DAB_SELECTOR)).isEqualTo(-1);
    }

    private static ProgramSelector.Identifier createDabSidExtId(long sid, int ecc, int sCIdS) {
        return new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_DAB_DMB_SID_EXT,
                ((long) sCIdS << 40) | ((long) ecc << 32) | sid);
    }
}
