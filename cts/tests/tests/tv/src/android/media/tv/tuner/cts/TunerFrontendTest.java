/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.TunerVersionChecker;
import android.media.tv.tuner.frontend.AnalogFrontendCapabilities;
import android.media.tv.tuner.frontend.AnalogFrontendSettings;
import android.media.tv.tuner.frontend.Atsc3FrontendCapabilities;
import android.media.tv.tuner.frontend.Atsc3FrontendSettings;
import android.media.tv.tuner.frontend.Atsc3PlpSettings;
import android.media.tv.tuner.frontend.AtscFrontendCapabilities;
import android.media.tv.tuner.frontend.AtscFrontendSettings;
import android.media.tv.tuner.frontend.DtmbFrontendCapabilities;
import android.media.tv.tuner.frontend.DtmbFrontendSettings;
import android.media.tv.tuner.frontend.DvbcFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbcFrontendSettings;
import android.media.tv.tuner.frontend.DvbsCodeRate;
import android.media.tv.tuner.frontend.DvbsFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbsFrontendSettings;
import android.media.tv.tuner.frontend.DvbtFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.media.tv.tuner.frontend.FrontendCapabilities;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.media.tv.tuner.frontend.FrontendSettings;
import android.media.tv.tuner.frontend.IptvFrontendCapabilities;
import android.media.tv.tuner.frontend.IptvFrontendSettings;
import android.media.tv.tuner.frontend.IptvFrontendSettingsFec;
import android.media.tv.tuner.frontend.Isdbs3FrontendCapabilities;
import android.media.tv.tuner.frontend.Isdbs3FrontendSettings;
import android.media.tv.tuner.frontend.IsdbsFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbsFrontendSettings;
import android.media.tv.tuner.frontend.IsdbtFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbtFrontendSettings;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerFrontendTest {
    private static final String TAG = "MediaTunerFrontendTest";

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_TUNER);

    private Context mContext;
    private Tuner mTuner;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mTuner = new Tuner(mContext, null, 100);
    }

    @After
    public void tearDown() {
        if (mTuner != null) {
          mTuner.close();
          mTuner = null;
        }
    }

    @Test
    public void testAnalogFrontendSettingsWithIntFrequency() throws Exception {
        AnalogFrontendSettings settings =
                AnalogFrontendSettings
                        .builder()
                        .setFrequency(1)
                        .setSignalType(AnalogFrontendSettings.SIGNAL_TYPE_NTSC)
                        .setSifStandard(AnalogFrontendSettings.SIF_BG_NICAM)
                        .setAftFlag(AnalogFrontendSettings.AFT_FLAG_TRUE)
                        .build();

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
            settings.setEndFrequency(100);
        } else {
            settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
            settings.setEndFrequency(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY);
        }

        assertEquals(FrontendSettings.TYPE_ANALOG, settings.getType());
        assertEquals(1, settings.getFrequency());
        assertEquals(AnalogFrontendSettings.SIGNAL_TYPE_NTSC, settings.getSignalType());
        assertEquals(AnalogFrontendSettings.SIF_BG_NICAM, settings.getSifStandard());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(AnalogFrontendSettings.AFT_FLAG_TRUE, settings.getAftFlag());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(AnalogFrontendSettings.AFT_FLAG_UNDEFINED, settings.getAftFlag());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }
    }

    @Test
    public void testAtsc3FrontendSettingsWithIntFrequency() throws Exception {
        Atsc3PlpSettings plp1 =
                Atsc3PlpSettings
                        .builder()
                        .setPlpId(1)
                        .setModulation(Atsc3FrontendSettings.MODULATION_MOD_QPSK)
                        .setInterleaveMode(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_AUTO)
                        .setCodeRate(Atsc3FrontendSettings.CODERATE_6_15)
                        .setFec(Atsc3FrontendSettings.FEC_BCH_LDPC_64K)
                        .build();

        Atsc3PlpSettings plp2 =
                Atsc3PlpSettings
                        .builder()
                        .setPlpId(2)
                        .setModulation(Atsc3FrontendSettings.MODULATION_MOD_QPSK)
                        .setInterleaveMode(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_HTI)
                        .setCodeRate(Atsc3FrontendSettings.CODERATE_UNDEFINED)
                        .setFec(Atsc3FrontendSettings.FEC_LDPC_16K)
                        .build();

        Atsc3FrontendSettings settings =
                Atsc3FrontendSettings
                        .builder()
                        .setFrequency(2)
                        .setBandwidth(Atsc3FrontendSettings.BANDWIDTH_BANDWIDTH_6MHZ)
                        .setDemodOutputFormat(Atsc3FrontendSettings.MODULATION_MOD_QPSK)
                        .setPlpSettings(new Atsc3PlpSettings[] {plp1, plp2})
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_ATSC3, settings.getType());
        assertEquals(2, settings.getFrequency());
        assertEquals(Atsc3FrontendSettings.BANDWIDTH_BANDWIDTH_6MHZ, settings.getBandwidth());
        assertEquals(Atsc3FrontendSettings.MODULATION_MOD_QPSK, settings.getDemodOutputFormat());

        Atsc3PlpSettings[] plps = settings.getPlpSettings();
        assertEquals(2, plps.length);

        assertEquals(1, plps[0].getPlpId());
        assertEquals(Atsc3FrontendSettings.MODULATION_MOD_QPSK, plps[0].getModulation());
        assertEquals(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_AUTO, plps[0].getInterleaveMode());
        assertEquals(Atsc3FrontendSettings.CODERATE_6_15, plps[0].getCodeRate());
        assertEquals(Atsc3FrontendSettings.FEC_BCH_LDPC_64K, plps[0].getFec());

        assertEquals(2, plps[1].getPlpId());
        assertEquals(Atsc3FrontendSettings.MODULATION_MOD_QPSK, plps[1].getModulation());
        assertEquals(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_HTI, plps[1].getInterleaveMode());
        assertEquals(Atsc3FrontendSettings.CODERATE_UNDEFINED, plps[1].getCodeRate());
        assertEquals(Atsc3FrontendSettings.FEC_LDPC_16K, plps[1].getFec());

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }
    }

    @Test
    public void testAtscFrontendSettingsWithIntFrequency() throws Exception {
        AtscFrontendSettings settings =
                AtscFrontendSettings
                        .builder()
                        .setFrequency(3)
                        .setModulation(AtscFrontendSettings.MODULATION_MOD_8VSB)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_ATSC, settings.getType());
        assertEquals(3, settings.getFrequency());
        assertEquals(AtscFrontendSettings.MODULATION_MOD_8VSB, settings.getModulation());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }
    }

    @Test
    public void testDvbcFrontendSettingsWithIntFrequency() throws Exception {
        DvbcFrontendSettings settings =
                DvbcFrontendSettings
                        .builder()
                        .setFrequency(4)
                        .setModulation(DvbcFrontendSettings.MODULATION_MOD_32QAM)
                        .setInnerFec(FrontendSettings.FEC_8_15)
                        .setSymbolRate(3)
                        .setOuterFec(DvbcFrontendSettings.OUTER_FEC_OUTER_FEC_RS)
                        .setAnnex(DvbcFrontendSettings.ANNEX_B)
                        .setTimeInterleaveMode(DvbcFrontendSettings.TIME_INTERLEAVE_MODE_AUTO)
                        .setBandwidth(DvbcFrontendSettings.BANDWIDTH_5MHZ)
                        // DvbcFrontendSettings.SpectralInversion is deprecated in Android 12. Use
                        // FrontendSettings.FrontendSpectralInversion instead.
                        .setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL)
                        .build();

        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_DVBC, settings.getType());
        assertEquals(4, settings.getFrequency());
        assertEquals(DvbcFrontendSettings.MODULATION_MOD_32QAM, settings.getModulation());
        assertEquals(FrontendSettings.FEC_8_15, settings.getInnerFec());
        assertEquals(3, settings.getSymbolRate());
        assertEquals(DvbcFrontendSettings.OUTER_FEC_OUTER_FEC_RS, settings.getOuterFec());
        assertEquals(DvbcFrontendSettings.ANNEX_B, settings.getAnnex());
        assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                settings.getSpectralInversion());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(DvbcFrontendSettings.TIME_INTERLEAVE_MODE_AUTO,
                settings.getTimeInterleaveMode());
            assertEquals(100, settings.getEndFrequency());
            assertEquals(DvbcFrontendSettings.BANDWIDTH_5MHZ, settings.getBandwidth());
        } else {
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
            assertEquals(DvbcFrontendSettings.BANDWIDTH_UNDEFINED, settings.getBandwidth());
        }
    }

    @Test
    public void testDvbsFrontendSettingsWithIntFrequency() throws Exception {
        DvbsCodeRate codeRate =
                DvbsCodeRate
                        .builder()
                        .setInnerFec(FrontendSettings.FEC_9_10)
                        .setLinear(true)
                        .setShortFrameEnabled(false)
                        .setBitsPer1000Symbol(55)
                        .build();

        DvbsFrontendSettings settings =
                DvbsFrontendSettings
                        .builder()
                        .setFrequency(5)
                        .setModulation(DvbsFrontendSettings.MODULATION_MOD_ACM)
                        .setCodeRate(codeRate)
                        .setSymbolRate(3)
                        .setRolloff(DvbsFrontendSettings.ROLLOFF_0_15)
                        .setPilot(DvbsFrontendSettings.PILOT_OFF)
                        .setInputStreamId(1)
                        .setStandard(DvbsFrontendSettings.STANDARD_S2)
                        .setVcmMode(DvbsFrontendSettings.VCM_MODE_MANUAL)
                        .setScanType(DvbsFrontendSettings.SCAN_TYPE_DIRECT)
                        .setCanHandleDiseqcRxMessage(true)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_DVBS, settings.getType());
        assertEquals(5, settings.getFrequency());
        assertEquals(DvbsFrontendSettings.MODULATION_MOD_ACM, settings.getModulation());
        assertEquals(3, settings.getSymbolRate());
        assertEquals(DvbsFrontendSettings.ROLLOFF_0_15, settings.getRolloff());
        assertEquals(DvbsFrontendSettings.PILOT_OFF, settings.getPilot());
        assertEquals(1, settings.getInputStreamId());
        assertEquals(DvbsFrontendSettings.STANDARD_S2, settings.getStandard());
        assertEquals(DvbsFrontendSettings.VCM_MODE_MANUAL, settings.getVcmMode());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(DvbsFrontendSettings.SCAN_TYPE_DIRECT, settings.getScanType());
            assertTrue(settings.canHandleDiseqcRxMessage());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(DvbsFrontendSettings.SCAN_TYPE_UNDEFINED, settings.getScanType());
            assertFalse(settings.canHandleDiseqcRxMessage());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }

        DvbsCodeRate cr = settings.getCodeRate();
        assertNotNull(cr);
        assertEquals(FrontendSettings.FEC_9_10, cr.getInnerFec());
        assertEquals(true, cr.isLinear());
        assertEquals(false, cr.isShortFrameEnabled());
        assertEquals(55, cr.getBitsPer1000Symbol());
    }

    @Test
    public void testDvbtFrontendSettingsWithIntFrequency() throws Exception {
        DvbtFrontendSettings settings =
                DvbtFrontendSettings
                        .builder()
                        .setFrequency(6)
                        .setTransmissionMode(DvbtFrontendSettings.TRANSMISSION_MODE_EXTENDED_32K)
                        .setBandwidth(DvbtFrontendSettings.BANDWIDTH_1_7MHZ)
                        .setConstellation(DvbtFrontendSettings.CONSTELLATION_16QAM_R)
                        .setHierarchy(DvbtFrontendSettings.HIERARCHY_4_NATIVE)
                        .setHighPriorityCodeRate(DvbtFrontendSettings.CODERATE_6_7)
                        .setLowPriorityCodeRate(DvbtFrontendSettings.CODERATE_2_3)
                        .setGuardInterval(DvbtFrontendSettings.GUARD_INTERVAL_19_128)
                        .setHighPriority(true)
                        .setStandard(DvbtFrontendSettings.STANDARD_T2)
                        .setMiso(false)
                        .setPlpMode(DvbtFrontendSettings.PLP_MODE_MANUAL)
                        .setPlpId(333)
                        .setPlpGroupId(777)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_DVBT, settings.getType());
        assertEquals(6, settings.getFrequency());
        assertEquals(DvbtFrontendSettings.BANDWIDTH_1_7MHZ, settings.getBandwidth());
        assertEquals(DvbtFrontendSettings.HIERARCHY_4_NATIVE, settings.getHierarchy());
        assertEquals(DvbtFrontendSettings.CODERATE_6_7, settings.getHighPriorityCodeRate());
        assertEquals(DvbtFrontendSettings.CODERATE_2_3, settings.getLowPriorityCodeRate());
        assertEquals(DvbtFrontendSettings.GUARD_INTERVAL_19_128, settings.getGuardInterval());
        assertEquals(true, settings.isHighPriority());
        assertEquals(DvbtFrontendSettings.STANDARD_T2, settings.getStandard());
        assertEquals(false, settings.isMiso());
        assertEquals(DvbtFrontendSettings.PLP_MODE_MANUAL, settings.getPlpMode());
        assertEquals(333, settings.getPlpId());
        assertEquals(777, settings.getPlpGroupId());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(DvbtFrontendSettings.TRANSMISSION_MODE_EXTENDED_32K,
                    settings.getTransmissionMode());
            assertEquals(DvbtFrontendSettings.CONSTELLATION_16QAM_R, settings.getConstellation());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(DvbtFrontendSettings.TRANSMISSION_MODE_UNDEFINED,
                    settings.getTransmissionMode());
            assertEquals(DvbtFrontendSettings.CONSTELLATION_UNDEFINED, settings.getConstellation());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }
    }

    @Test
    public void testIsdbs3FrontendSettingsWithIntFrequency() throws Exception {
        Isdbs3FrontendSettings settings =
                Isdbs3FrontendSettings
                        .builder()
                        .setFrequency(7)
                        .setStreamId(2)
                        .setStreamIdType(IsdbsFrontendSettings.STREAM_ID_TYPE_ID)
                        .setModulation(Isdbs3FrontendSettings.MODULATION_MOD_BPSK)
                        .setCodeRate(Isdbs3FrontendSettings.CODERATE_1_3)
                        .setSymbolRate(555)
                        .setRolloff(Isdbs3FrontendSettings.ROLLOFF_0_03)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_ISDBS3, settings.getType());
        assertEquals(7, settings.getFrequency());
        assertEquals(2, settings.getStreamId());
        assertEquals(IsdbsFrontendSettings.STREAM_ID_TYPE_ID, settings.getStreamIdType());
        assertEquals(Isdbs3FrontendSettings.MODULATION_MOD_BPSK, settings.getModulation());
        assertEquals(Isdbs3FrontendSettings.CODERATE_1_3, settings.getCodeRate());
        assertEquals(555, settings.getSymbolRate());
        assertEquals(Isdbs3FrontendSettings.ROLLOFF_0_03, settings.getRolloff());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }
    }

    @Test
    public void testIsdbsFrontendSettingsWithIntFrequency() throws Exception {
        IsdbsFrontendSettings settings =
                IsdbsFrontendSettings
                        .builder()
                        .setFrequency(8)
                        .setStreamId(3)
                        .setStreamIdType(IsdbsFrontendSettings.STREAM_ID_TYPE_RELATIVE_NUMBER)
                        .setModulation(IsdbsFrontendSettings.MODULATION_AUTO)
                        .setCodeRate(IsdbsFrontendSettings.CODERATE_3_4)
                        .setSymbolRate(667)
                        .setRolloff(IsdbsFrontendSettings.ROLLOFF_0_35)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_ISDBS, settings.getType());
        assertEquals(8, settings.getFrequency());
        assertEquals(3, settings.getStreamId());
        assertEquals(
                IsdbsFrontendSettings.STREAM_ID_TYPE_RELATIVE_NUMBER, settings.getStreamIdType());
        assertEquals(IsdbsFrontendSettings.MODULATION_AUTO, settings.getModulation());
        assertEquals(IsdbsFrontendSettings.CODERATE_3_4, settings.getCodeRate());
        assertEquals(667, settings.getSymbolRate());
        assertEquals(IsdbsFrontendSettings.ROLLOFF_0_35, settings.getRolloff());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }
    }

    @Test
    public void testIsdbtFrontendSettingsWithIntFrequency() throws Exception {
        IsdbtFrontendSettings.Builder builder = IsdbtFrontendSettings.builder();
        builder.setFrequency(9);
        builder.setModulation(IsdbtFrontendSettings.MODULATION_MOD_64QAM);
        builder.setBandwidth(IsdbtFrontendSettings.BANDWIDTH_8MHZ);
        builder.setMode(IsdbtFrontendSettings.MODE_2);
        builder.setCodeRate(DvbtFrontendSettings.CODERATE_7_8);
        builder.setGuardInterval(DvbtFrontendSettings.GUARD_INTERVAL_1_4);
        builder.setServiceAreaId(10);

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            IsdbtFrontendSettings.IsdbtLayerSettings.Builder layerBuilder =
                    IsdbtFrontendSettings.IsdbtLayerSettings.builder();
            layerBuilder.setTimeInterleaveMode(IsdbtFrontendSettings.TIME_INTERLEAVE_MODE_AUTO);
            layerBuilder.setModulation(IsdbtFrontendSettings.MODULATION_MOD_16QAM);
            layerBuilder.setCodeRate(DvbtFrontendSettings.CODERATE_5_6);
            layerBuilder.setNumberOfSegments(0xFF);
            IsdbtFrontendSettings.IsdbtLayerSettings layer = layerBuilder.build();
            builder.setLayerSettings(new IsdbtFrontendSettings.IsdbtLayerSettings[] {layer, layer});
            builder.setPartialReceptionFlag(IsdbtFrontendSettings.PARTIAL_RECEPTION_FLAG_TRUE);
        }

        IsdbtFrontendSettings settings = builder.build();
        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequency(100);

        assertEquals(FrontendSettings.TYPE_ISDBT, settings.getType());
        assertEquals(9, settings.getFrequency());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(IsdbtFrontendSettings.MODULATION_UNDEFINED, settings.getModulation());
        } else {
            assertEquals(IsdbtFrontendSettings.MODULATION_MOD_64QAM, settings.getModulation());
        }
        assertEquals(IsdbtFrontendSettings.BANDWIDTH_8MHZ, settings.getBandwidth());
        assertEquals(IsdbtFrontendSettings.MODE_2, settings.getMode());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(DvbtFrontendSettings.CODERATE_UNDEFINED, settings.getCodeRate());
        } else {
            assertEquals(DvbtFrontendSettings.CODERATE_7_8, settings.getCodeRate());
        }
        assertEquals(DvbtFrontendSettings.GUARD_INTERVAL_1_4, settings.getGuardInterval());
        assertEquals(10, settings.getServiceAreaId());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequency());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequency());
        }

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            IsdbtFrontendSettings.IsdbtLayerSettings[] layers = settings.getLayerSettings();
            assertEquals(layers.length, 2);
            assertEquals(layers[0].getModulation(), IsdbtFrontendSettings.MODULATION_MOD_16QAM);
            assertEquals(layers[0].getCodeRate(), DvbtFrontendSettings.CODERATE_5_6);
            assertEquals(layers[0].getTimeInterleaveMode(),
                    IsdbtFrontendSettings.TIME_INTERLEAVE_MODE_AUTO);
            assertEquals(layers[0].getNumberOfSegments(), 0xFF);
            assertEquals(layers[1].getModulation(), IsdbtFrontendSettings.MODULATION_MOD_16QAM);
            assertEquals(layers[1].getCodeRate(), DvbtFrontendSettings.CODERATE_5_6);
            assertEquals(layers[1].getTimeInterleaveMode(),
                    IsdbtFrontendSettings.TIME_INTERLEAVE_MODE_AUTO);
            assertEquals(layers[1].getNumberOfSegments(), 0xFF);
            assertEquals(settings.getPartialReceptionFlag(),
                    IsdbtFrontendSettings.PARTIAL_RECEPTION_FLAG_TRUE);
        }
    }

    @Test
    public void testDtmbFrontendSettingsWithIntFrequency() throws Exception {
        if (!TunerVersionChecker.checkHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1,
                TAG + ": testDtmbFrontendSettings")) {
            return;
        }
        DtmbFrontendSettings settings =
                DtmbFrontendSettings
                        .builder()
                        .setFrequency(6)
                        .setModulation(DtmbFrontendSettings.MODULATION_CONSTELLATION_4QAM)
                        .setCodeRate(DtmbFrontendSettings.CODERATE_2_5)
                        .setTransmissionMode(DtmbFrontendSettings.TRANSMISSION_MODE_C1)
                        .setBandwidth(DtmbFrontendSettings.BANDWIDTH_8MHZ)
                        .setTimeInterleaveMode(
                                DtmbFrontendSettings.TIME_INTERLEAVE_MODE_TIMER_INT_240)
                        .setGuardInterval(DtmbFrontendSettings.GUARD_INTERVAL_PN_945_VARIOUS)
                        .build();
        assertEquals(FrontendSettings.TYPE_DTMB, settings.getType());
        assertEquals(6, settings.getFrequency());
        assertEquals(DtmbFrontendSettings.TRANSMISSION_MODE_C1, settings.getTransmissionMode());
        assertEquals(DtmbFrontendSettings.BANDWIDTH_8MHZ, settings.getBandwidth());
        assertEquals(DtmbFrontendSettings.MODULATION_CONSTELLATION_4QAM, settings.getModulation());
        assertEquals(DtmbFrontendSettings.TIME_INTERLEAVE_MODE_TIMER_INT_240,
                settings.getTimeInterleaveMode());
        assertEquals(DtmbFrontendSettings.CODERATE_2_5, settings.getCodeRate());
        assertEquals(DtmbFrontendSettings.GUARD_INTERVAL_PN_945_VARIOUS,
                settings.getGuardInterval());
    }

    @Test
    public void testFrontendInfoWithIntFrequency() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        List<FrontendInfo> infos = mTuner.getAvailableFrontendInfos();
        Map<Integer, FrontendInfo> infoMap = new HashMap<>();
        for (FrontendInfo info : infos) {
            infoMap.put(info.getId(), info);
        }
        for (int id : ids) {
            FrontendInfo info = mTuner.getFrontendInfoById(id);
            FrontendInfo infoFromMap = infoMap.get(id);
            assertNotNull(info);
            assertThat(info).isEqualTo(infoFromMap);
            assertEquals(id, info.getId());
            assertTrue(info.getFrequencyRange().getLower() > 0);
            assertTrue(info.getSymbolRateRange().getLower() >= 0);
            assertTrue(info.getAcquireRange() > 0);
            info.getExclusiveGroupId();
            info.getStatusCapabilities();

            FrontendCapabilities caps = info.getFrontendCapabilities();
            if (info.getType() <= FrontendSettings.TYPE_ISDBT) {
                assertNotNull(caps);
            }
            switch(info.getType()) {
                case FrontendSettings.TYPE_ANALOG:
                    testAnalogFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ATSC3:
                    testAtsc3FrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ATSC:
                    testAtscFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DVBC:
                    testDvbcFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DVBS:
                    testDvbsFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DVBT:
                    testDvbtFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ISDBS3:
                    testIsdbs3FrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ISDBS:
                    testIsdbsFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ISDBT:
                    testIsdbtFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DTMB:
                    testDtmbFrontendCapabilities(caps);
                    break;
                default:
                    break;
            }
            infoMap.remove(id);
        }
        assertTrue(infoMap.isEmpty());
    }

    @Test
    public void testAnalogFrontendSettingsWithLongFrequency() throws Exception {
        AnalogFrontendSettings settings =
                AnalogFrontendSettings
                        .builder()
                        .setFrequencyLong(1)
                        .setSignalType(AnalogFrontendSettings.SIGNAL_TYPE_NTSC)
                        .setSifStandard(AnalogFrontendSettings.SIF_BG_NICAM)
                        .setAftFlag(AnalogFrontendSettings.AFT_FLAG_TRUE)
                        .build();

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
            settings.setEndFrequencyLong(100);
        } else {
            settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
            settings.setEndFrequencyLong(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY);
        }

        assertEquals(FrontendSettings.TYPE_ANALOG, settings.getType());
        assertEquals(1, settings.getFrequencyLong());
        assertEquals(AnalogFrontendSettings.SIGNAL_TYPE_NTSC, settings.getSignalType());
        assertEquals(AnalogFrontendSettings.SIF_BG_NICAM, settings.getSifStandard());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(AnalogFrontendSettings.AFT_FLAG_TRUE, settings.getAftFlag());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(AnalogFrontendSettings.AFT_FLAG_UNDEFINED, settings.getAftFlag());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }
    }

    @Test
    public void testAtsc3FrontendSettingsWithLongFrequency() throws Exception {
        Atsc3PlpSettings plp1 =
                Atsc3PlpSettings
                        .builder()
                        .setPlpId(1)
                        .setModulation(Atsc3FrontendSettings.MODULATION_MOD_QPSK)
                        .setInterleaveMode(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_AUTO)
                        .setCodeRate(Atsc3FrontendSettings.CODERATE_6_15)
                        .setFec(Atsc3FrontendSettings.FEC_BCH_LDPC_64K)
                        .build();

        Atsc3PlpSettings plp2 =
                Atsc3PlpSettings
                        .builder()
                        .setPlpId(2)
                        .setModulation(Atsc3FrontendSettings.MODULATION_MOD_QPSK)
                        .setInterleaveMode(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_HTI)
                        .setCodeRate(Atsc3FrontendSettings.CODERATE_UNDEFINED)
                        .setFec(Atsc3FrontendSettings.FEC_LDPC_16K)
                        .build();

        Atsc3FrontendSettings settings =
                Atsc3FrontendSettings
                        .builder()
                        .setFrequencyLong(2)
                        .setBandwidth(Atsc3FrontendSettings.BANDWIDTH_BANDWIDTH_6MHZ)
                        .setDemodOutputFormat(Atsc3FrontendSettings.MODULATION_MOD_QPSK)
                        .setPlpSettings(new Atsc3PlpSettings[] {plp1, plp2})
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_ATSC3, settings.getType());
        assertEquals(2, settings.getFrequencyLong());
        assertEquals(Atsc3FrontendSettings.BANDWIDTH_BANDWIDTH_6MHZ, settings.getBandwidth());
        assertEquals(Atsc3FrontendSettings.MODULATION_MOD_QPSK, settings.getDemodOutputFormat());

        Atsc3PlpSettings[] plps = settings.getPlpSettings();
        assertEquals(2, plps.length);

        assertEquals(1, plps[0].getPlpId());
        assertEquals(Atsc3FrontendSettings.MODULATION_MOD_QPSK, plps[0].getModulation());
        assertEquals(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_AUTO, plps[0].getInterleaveMode());
        assertEquals(Atsc3FrontendSettings.CODERATE_6_15, plps[0].getCodeRate());
        assertEquals(Atsc3FrontendSettings.FEC_BCH_LDPC_64K, plps[0].getFec());

        assertEquals(2, plps[1].getPlpId());
        assertEquals(Atsc3FrontendSettings.MODULATION_MOD_QPSK, plps[1].getModulation());
        assertEquals(Atsc3FrontendSettings.TIME_INTERLEAVE_MODE_HTI, plps[1].getInterleaveMode());
        assertEquals(Atsc3FrontendSettings.CODERATE_UNDEFINED, plps[1].getCodeRate());
        assertEquals(Atsc3FrontendSettings.FEC_LDPC_16K, plps[1].getFec());

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }
    }

    @Test
    public void testAtscFrontendSettingsWithLongFrequency() throws Exception {
        AtscFrontendSettings settings =
                AtscFrontendSettings
                        .builder()
                        .setFrequencyLong(3)
                        .setModulation(AtscFrontendSettings.MODULATION_MOD_8VSB)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_ATSC, settings.getType());
        assertEquals(3, settings.getFrequencyLong());
        assertEquals(AtscFrontendSettings.MODULATION_MOD_8VSB, settings.getModulation());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }
    }

    @Test
    public void testDvbcFrontendSettingsWithLongFrequency() throws Exception {
        DvbcFrontendSettings settings =
                DvbcFrontendSettings
                        .builder()
                        .setFrequencyLong(4)
                        .setModulation(DvbcFrontendSettings.MODULATION_MOD_32QAM)
                        .setInnerFec(FrontendSettings.FEC_8_15)
                        .setSymbolRate(3)
                        .setOuterFec(DvbcFrontendSettings.OUTER_FEC_OUTER_FEC_RS)
                        .setAnnex(DvbcFrontendSettings.ANNEX_B)
                        .setTimeInterleaveMode(DvbcFrontendSettings.TIME_INTERLEAVE_MODE_AUTO)
                        .setBandwidth(DvbcFrontendSettings.BANDWIDTH_5MHZ)
                        // DvbcFrontendSettings.SpectralInversion is deprecated in Android 12. Use
                        // FrontendSettings.FrontendSpectralInversion instead.
                        .setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL)
                        .build();

        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_DVBC, settings.getType());
        assertEquals(4, settings.getFrequencyLong());
        assertEquals(DvbcFrontendSettings.MODULATION_MOD_32QAM, settings.getModulation());
        assertEquals(FrontendSettings.FEC_8_15, settings.getInnerFec());
        assertEquals(3, settings.getSymbolRate());
        assertEquals(DvbcFrontendSettings.OUTER_FEC_OUTER_FEC_RS, settings.getOuterFec());
        assertEquals(DvbcFrontendSettings.ANNEX_B, settings.getAnnex());
        assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                settings.getSpectralInversion());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(DvbcFrontendSettings.TIME_INTERLEAVE_MODE_AUTO,
                    settings.getTimeInterleaveMode());
            assertEquals(100, settings.getEndFrequencyLong());
            assertEquals(DvbcFrontendSettings.BANDWIDTH_5MHZ, settings.getBandwidth());
        } else {
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
            assertEquals(DvbcFrontendSettings.BANDWIDTH_UNDEFINED, settings.getBandwidth());
        }
    }

    @Test
    public void testDvbsFrontendSettingsWithLongFrequency() throws Exception {
        DvbsCodeRate codeRate =
                DvbsCodeRate
                        .builder()
                        .setInnerFec(FrontendSettings.FEC_9_10)
                        .setLinear(true)
                        .setShortFrameEnabled(false)
                        .setBitsPer1000Symbol(55)
                        .build();

        DvbsFrontendSettings settings =
                DvbsFrontendSettings
                        .builder()
                        .setFrequencyLong(5)
                        .setModulation(DvbsFrontendSettings.MODULATION_MOD_ACM)
                        .setCodeRate(codeRate)
                        .setSymbolRate(3)
                        .setRolloff(DvbsFrontendSettings.ROLLOFF_0_15)
                        .setPilot(DvbsFrontendSettings.PILOT_OFF)
                        .setInputStreamId(1)
                        .setStandard(DvbsFrontendSettings.STANDARD_S2)
                        .setVcmMode(DvbsFrontendSettings.VCM_MODE_MANUAL)
                        .setScanType(DvbsFrontendSettings.SCAN_TYPE_DIRECT)
                        .setCanHandleDiseqcRxMessage(true)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_DVBS, settings.getType());
        assertEquals(5, settings.getFrequencyLong());
        assertEquals(DvbsFrontendSettings.MODULATION_MOD_ACM, settings.getModulation());
        assertEquals(3, settings.getSymbolRate());
        assertEquals(DvbsFrontendSettings.ROLLOFF_0_15, settings.getRolloff());
        assertEquals(DvbsFrontendSettings.PILOT_OFF, settings.getPilot());
        assertEquals(1, settings.getInputStreamId());
        assertEquals(DvbsFrontendSettings.STANDARD_S2, settings.getStandard());
        assertEquals(DvbsFrontendSettings.VCM_MODE_MANUAL, settings.getVcmMode());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(DvbsFrontendSettings.SCAN_TYPE_DIRECT, settings.getScanType());
            assertTrue(settings.canHandleDiseqcRxMessage());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(DvbsFrontendSettings.SCAN_TYPE_UNDEFINED, settings.getScanType());
            assertFalse(settings.canHandleDiseqcRxMessage());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }

        DvbsCodeRate cr = settings.getCodeRate();
        assertNotNull(cr);
        assertEquals(FrontendSettings.FEC_9_10, cr.getInnerFec());
        assertEquals(true, cr.isLinear());
        assertEquals(false, cr.isShortFrameEnabled());
        assertEquals(55, cr.getBitsPer1000Symbol());
    }

    @Test
    public void testDvbtFrontendSettingsWithLongFrequency() throws Exception {
        DvbtFrontendSettings settings =
                DvbtFrontendSettings
                        .builder()
                        .setFrequencyLong(6)
                        .setTransmissionMode(DvbtFrontendSettings.TRANSMISSION_MODE_EXTENDED_32K)
                        .setBandwidth(DvbtFrontendSettings.BANDWIDTH_1_7MHZ)
                        .setConstellation(DvbtFrontendSettings.CONSTELLATION_16QAM_R)
                        .setHierarchy(DvbtFrontendSettings.HIERARCHY_4_NATIVE)
                        .setHighPriorityCodeRate(DvbtFrontendSettings.CODERATE_6_7)
                        .setLowPriorityCodeRate(DvbtFrontendSettings.CODERATE_2_3)
                        .setGuardInterval(DvbtFrontendSettings.GUARD_INTERVAL_19_128)
                        .setHighPriority(true)
                        .setStandard(DvbtFrontendSettings.STANDARD_T2)
                        .setMiso(false)
                        .setPlpMode(DvbtFrontendSettings.PLP_MODE_MANUAL)
                        .setPlpId(333)
                        .setPlpGroupId(777)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_DVBT, settings.getType());
        assertEquals(6, settings.getFrequencyLong());
        assertEquals(DvbtFrontendSettings.BANDWIDTH_1_7MHZ, settings.getBandwidth());
        assertEquals(DvbtFrontendSettings.HIERARCHY_4_NATIVE, settings.getHierarchy());
        assertEquals(DvbtFrontendSettings.CODERATE_6_7, settings.getHighPriorityCodeRate());
        assertEquals(DvbtFrontendSettings.CODERATE_2_3, settings.getLowPriorityCodeRate());
        assertEquals(DvbtFrontendSettings.GUARD_INTERVAL_19_128, settings.getGuardInterval());
        assertEquals(true, settings.isHighPriority());
        assertEquals(DvbtFrontendSettings.STANDARD_T2, settings.getStandard());
        assertEquals(false, settings.isMiso());
        assertEquals(DvbtFrontendSettings.PLP_MODE_MANUAL, settings.getPlpMode());
        assertEquals(333, settings.getPlpId());
        assertEquals(777, settings.getPlpGroupId());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(DvbtFrontendSettings.TRANSMISSION_MODE_EXTENDED_32K,
                    settings.getTransmissionMode());
            assertEquals(DvbtFrontendSettings.CONSTELLATION_16QAM_R, settings.getConstellation());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(DvbtFrontendSettings.TRANSMISSION_MODE_UNDEFINED,
                    settings.getTransmissionMode());
            assertEquals(DvbtFrontendSettings.CONSTELLATION_UNDEFINED, settings.getConstellation());
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }
    }

    @Test
    public void testIsdbs3FrontendSettingsWithLongFrequency() throws Exception {
        Isdbs3FrontendSettings settings =
                Isdbs3FrontendSettings
                        .builder()
                        .setFrequencyLong(7)
                        .setStreamId(2)
                        .setStreamIdType(IsdbsFrontendSettings.STREAM_ID_TYPE_ID)
                        .setModulation(Isdbs3FrontendSettings.MODULATION_MOD_BPSK)
                        .setCodeRate(Isdbs3FrontendSettings.CODERATE_1_3)
                        .setSymbolRate(555)
                        .setRolloff(Isdbs3FrontendSettings.ROLLOFF_0_03)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_ISDBS3, settings.getType());
        assertEquals(7, settings.getFrequencyLong());
        assertEquals(2, settings.getStreamId());
        assertEquals(IsdbsFrontendSettings.STREAM_ID_TYPE_ID, settings.getStreamIdType());
        assertEquals(Isdbs3FrontendSettings.MODULATION_MOD_BPSK, settings.getModulation());
        assertEquals(Isdbs3FrontendSettings.CODERATE_1_3, settings.getCodeRate());
        assertEquals(555, settings.getSymbolRate());
        assertEquals(Isdbs3FrontendSettings.ROLLOFF_0_03, settings.getRolloff());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }
    }

    @Test
    public void testIsdbsFrontendSettingsWithLongFrequency() throws Exception {
        IsdbsFrontendSettings settings =
                IsdbsFrontendSettings
                        .builder()
                        .setFrequencyLong(8)
                        .setStreamId(3)
                        .setStreamIdType(IsdbsFrontendSettings.STREAM_ID_TYPE_RELATIVE_NUMBER)
                        .setModulation(IsdbsFrontendSettings.MODULATION_AUTO)
                        .setCodeRate(IsdbsFrontendSettings.CODERATE_3_4)
                        .setSymbolRate(667)
                        .setRolloff(IsdbsFrontendSettings.ROLLOFF_0_35)
                        .build();

        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_ISDBS, settings.getType());
        assertEquals(8, settings.getFrequencyLong());
        assertEquals(3, settings.getStreamId());
        assertEquals(
                IsdbsFrontendSettings.STREAM_ID_TYPE_RELATIVE_NUMBER, settings.getStreamIdType());
        assertEquals(IsdbsFrontendSettings.MODULATION_AUTO, settings.getModulation());
        assertEquals(IsdbsFrontendSettings.CODERATE_3_4, settings.getCodeRate());
        assertEquals(667, settings.getSymbolRate());
        assertEquals(IsdbsFrontendSettings.ROLLOFF_0_35, settings.getRolloff());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }
    }

    @Test
    public void testIsdbtFrontendSettingsWithLongFrequency() throws Exception {
        IsdbtFrontendSettings.Builder builder = IsdbtFrontendSettings.builder();
        builder.setFrequencyLong(9);
        builder.setModulation(IsdbtFrontendSettings.MODULATION_MOD_64QAM);
        builder.setBandwidth(IsdbtFrontendSettings.BANDWIDTH_8MHZ);
        builder.setMode(IsdbtFrontendSettings.MODE_2);
        builder.setCodeRate(DvbtFrontendSettings.CODERATE_7_8);
        builder.setGuardInterval(DvbtFrontendSettings.GUARD_INTERVAL_1_4);
        builder.setServiceAreaId(10);

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            IsdbtFrontendSettings.IsdbtLayerSettings.Builder layerBuilder =
                    IsdbtFrontendSettings.IsdbtLayerSettings.builder();
            layerBuilder.setTimeInterleaveMode(IsdbtFrontendSettings.TIME_INTERLEAVE_MODE_AUTO);
            layerBuilder.setModulation(IsdbtFrontendSettings.MODULATION_MOD_16QAM);
            layerBuilder.setCodeRate(DvbtFrontendSettings.CODERATE_5_6);
            layerBuilder.setNumberOfSegments(0xFF);
            IsdbtFrontendSettings.IsdbtLayerSettings layer = layerBuilder.build();
            builder.setLayerSettings(new IsdbtFrontendSettings.IsdbtLayerSettings[] {layer, layer});
            builder.setPartialReceptionFlag(IsdbtFrontendSettings.PARTIAL_RECEPTION_FLAG_TRUE);
        }

        IsdbtFrontendSettings settings = builder.build();
        settings.setSpectralInversion(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL);
        settings.setEndFrequencyLong(100);

        assertEquals(FrontendSettings.TYPE_ISDBT, settings.getType());
        assertEquals(9, settings.getFrequencyLong());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(IsdbtFrontendSettings.MODULATION_UNDEFINED, settings.getModulation());
        } else {
            assertEquals(IsdbtFrontendSettings.MODULATION_MOD_64QAM, settings.getModulation());
        }
        assertEquals(IsdbtFrontendSettings.BANDWIDTH_8MHZ, settings.getBandwidth());
        assertEquals(IsdbtFrontendSettings.MODE_2, settings.getMode());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(DvbtFrontendSettings.CODERATE_UNDEFINED, settings.getCodeRate());
        } else {
            assertEquals(DvbtFrontendSettings.CODERATE_7_8, settings.getCodeRate());
        }
        assertEquals(DvbtFrontendSettings.GUARD_INTERVAL_1_4, settings.getGuardInterval());
        assertEquals(10, settings.getServiceAreaId());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_NORMAL,
                    settings.getFrontendSpectralInversion());
            assertEquals(100, settings.getEndFrequencyLong());
        } else {
            assertEquals(FrontendSettings.FRONTEND_SPECTRAL_INVERSION_UNDEFINED,
                    settings.getFrontendSpectralInversion());
            assertEquals(Tuner.INVALID_FRONTEND_SETTING_FREQUENCY, settings.getEndFrequencyLong());
        }

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            IsdbtFrontendSettings.IsdbtLayerSettings[] layers = settings.getLayerSettings();
            assertEquals(layers.length, 2);
            assertEquals(layers[0].getModulation(), IsdbtFrontendSettings.MODULATION_MOD_16QAM);
            assertEquals(layers[0].getCodeRate(), DvbtFrontendSettings.CODERATE_5_6);
            assertEquals(layers[0].getTimeInterleaveMode(),
                    IsdbtFrontendSettings.TIME_INTERLEAVE_MODE_AUTO);
            assertEquals(layers[0].getNumberOfSegments(), 0xFF);
            assertEquals(layers[1].getModulation(), IsdbtFrontendSettings.MODULATION_MOD_16QAM);
            assertEquals(layers[1].getCodeRate(), DvbtFrontendSettings.CODERATE_5_6);
            assertEquals(layers[1].getTimeInterleaveMode(),
                    IsdbtFrontendSettings.TIME_INTERLEAVE_MODE_AUTO);
            assertEquals(layers[1].getNumberOfSegments(), 0xFF);
            assertEquals(settings.getPartialReceptionFlag(),
                    IsdbtFrontendSettings.PARTIAL_RECEPTION_FLAG_TRUE);
        }
    }

    @Test
    public void testDtmbFrontendSettingsWithLongFrequency() throws Exception {
        if (!TunerVersionChecker.checkHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1,
                TAG + ": testDtmbFrontendSettings")) {
            return;
        }
        DtmbFrontendSettings settings =
                DtmbFrontendSettings
                        .builder()
                        .setFrequencyLong(6)
                        .setModulation(DtmbFrontendSettings.MODULATION_CONSTELLATION_4QAM)
                        .setCodeRate(DtmbFrontendSettings.CODERATE_2_5)
                        .setTransmissionMode(DtmbFrontendSettings.TRANSMISSION_MODE_C1)
                        .setBandwidth(DtmbFrontendSettings.BANDWIDTH_8MHZ)
                        .setTimeInterleaveMode(
                                DtmbFrontendSettings.TIME_INTERLEAVE_MODE_TIMER_INT_240)
                        .setGuardInterval(DtmbFrontendSettings.GUARD_INTERVAL_PN_945_VARIOUS)
                        .build();
        assertEquals(FrontendSettings.TYPE_DTMB, settings.getType());
        assertEquals(6, settings.getFrequencyLong());
        assertEquals(DtmbFrontendSettings.TRANSMISSION_MODE_C1, settings.getTransmissionMode());
        assertEquals(DtmbFrontendSettings.BANDWIDTH_8MHZ, settings.getBandwidth());
        assertEquals(DtmbFrontendSettings.MODULATION_CONSTELLATION_4QAM, settings.getModulation());
        assertEquals(DtmbFrontendSettings.TIME_INTERLEAVE_MODE_TIMER_INT_240,
                settings.getTimeInterleaveMode());
        assertEquals(DtmbFrontendSettings.CODERATE_2_5, settings.getCodeRate());
        assertEquals(DtmbFrontendSettings.GUARD_INTERVAL_PN_945_VARIOUS,
                settings.getGuardInterval());
    }

    @Test
    public void testIptvFrontendSettings() throws Exception {
        if (!TunerVersionChecker.checkHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_3_0,
                TAG + ": testIptvFrontendSettings")) {
            return;
        }
        IptvFrontendSettingsFec fec =
                new IptvFrontendSettingsFec
                        .Builder()
                        .setFecType(IptvFrontendSettingsFec.FEC_TYPE_ROW)
                        .setFecColNum(19)
                        .setFecRowNum(26)
                        .build();
        IptvFrontendSettings settings =
                new IptvFrontendSettings
                        .Builder()
                        .setSrcIpAddress(new byte[]{2, 3, 4, 5})
                        .setDstIpAddress(new byte[]{2, 3, 4, 5})
                        .setSrcPort(8000)
                        .setDstPort(9000)
                        .setFec(fec)
                        .setProtocol(IptvFrontendSettings.PROTOCOL_UDP)
                        .setIgmp(IptvFrontendSettings.IGMP_V2)
                        .setBitrate(1000)
                        .setContentUrl("contentUrl")
                        .build();
        assertEquals(FrontendSettings.TYPE_IPTV, settings.getType());
        assertArrayEquals(new byte[]{2, 3, 4, 5}, settings.getSrcIpAddress());
        assertArrayEquals(new byte[]{2, 3, 4, 5}, settings.getDstIpAddress());
        assertEquals(8000, settings.getSrcPort());
        assertEquals(9000, settings.getDstPort());
        assertEquals(IptvFrontendSettingsFec.FEC_TYPE_ROW, settings.getFec().getFecType());
        assertEquals(26, settings.getFec().getFecRowNum());
        assertEquals(19, settings.getFec().getFecColNum());
        assertEquals(IptvFrontendSettings.PROTOCOL_UDP, settings.getProtocol());
        assertEquals(IptvFrontendSettings.IGMP_V2, settings.getIgmp());
        assertEquals(1000, settings.getBitrate());
        assertEquals("contentUrl", settings.getContentUrl());
    }


    @Test
    public void testFrontendInfoWithLongFrequency() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        List<FrontendInfo> infos = mTuner.getAvailableFrontendInfos();
        Map<Integer, FrontendInfo> infoMap = new HashMap<>();
        for (FrontendInfo info : infos) {
            infoMap.put(info.getId(), info);
        }
        for (int id : ids) {
            FrontendInfo info = mTuner.getFrontendInfoById(id);
            FrontendInfo infoFromMap = infoMap.get(id);
            assertNotNull(info);
            assertThat(info).isEqualTo(infoFromMap);
            assertEquals(id, info.getId());
            assertTrue(info.getFrequencyRangeLong().getLower() > 0);
            assertTrue(info.getSymbolRateRange().getLower() >= 0);
            assertTrue(info.getAcquireRangeLong() > 0);
            info.getExclusiveGroupId();
            info.getStatusCapabilities();

            FrontendCapabilities caps = info.getFrontendCapabilities();
            if (info.getType() <= FrontendSettings.TYPE_ISDBT) {
                assertNotNull(caps);
            }
            switch(info.getType()) {
                case FrontendSettings.TYPE_ANALOG:
                    testAnalogFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ATSC3:
                    testAtsc3FrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ATSC:
                    testAtscFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DVBC:
                    testDvbcFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DVBS:
                    testDvbsFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DVBT:
                    testDvbtFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ISDBS3:
                    testIsdbs3FrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ISDBS:
                    testIsdbsFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_ISDBT:
                    testIsdbtFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_DTMB:
                    testDtmbFrontendCapabilities(caps);
                    break;
                case FrontendSettings.TYPE_IPTV:
                    testIptvFrontendCapabilities(caps);
                default:
                    break;
            }
            infoMap.remove(id);
        }
        assertTrue(infoMap.isEmpty());
    }

    private void testAnalogFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof AnalogFrontendCapabilities);
        AnalogFrontendCapabilities analogCaps = (AnalogFrontendCapabilities) caps;
        analogCaps.getSignalTypeCapability();
        analogCaps.getSifStandardCapability();
    }

    private void testAtsc3FrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof Atsc3FrontendCapabilities);
        Atsc3FrontendCapabilities atsc3Caps = (Atsc3FrontendCapabilities) caps;
        atsc3Caps.getBandwidthCapability();
        atsc3Caps.getModulationCapability();
        atsc3Caps.getTimeInterleaveModeCapability();
        atsc3Caps.getPlpCodeRateCapability();
        atsc3Caps.getFecCapability();
        atsc3Caps.getDemodOutputFormatCapability();
    }

    private void testAtscFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof AtscFrontendCapabilities);
        AtscFrontendCapabilities atscCaps = (AtscFrontendCapabilities) caps;
        atscCaps.getModulationCapability();
    }

    private void testDvbcFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof DvbcFrontendCapabilities);
        DvbcFrontendCapabilities dvbcCaps = (DvbcFrontendCapabilities) caps;
        dvbcCaps.getModulationCapability();
        // getFecCapability is deprecated starting Android 12. Use getCodeRateCapability instead.
        dvbcCaps.getCodeRateCapability();
        dvbcCaps.getFecCapability();
        dvbcCaps.getAnnexCapability();
    }

    private void testDvbsFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof DvbsFrontendCapabilities);
        DvbsFrontendCapabilities dvbsCaps = (DvbsFrontendCapabilities) caps;
        dvbsCaps.getModulationCapability();
        dvbsCaps.getInnerFecCapability();
        dvbsCaps.getStandardCapability();
    }

    private void testDvbtFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof DvbtFrontendCapabilities);
        DvbtFrontendCapabilities dvbtCaps = (DvbtFrontendCapabilities) caps;
        dvbtCaps.getTransmissionModeCapability();
        dvbtCaps.getBandwidthCapability();
        dvbtCaps.getConstellationCapability();
        dvbtCaps.getCodeRateCapability();
        dvbtCaps.getHierarchyCapability();
        dvbtCaps.getGuardIntervalCapability();
        dvbtCaps.isT2Supported();
        dvbtCaps.isMisoSupported();
    }

    private void testIsdbs3FrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof Isdbs3FrontendCapabilities);
        Isdbs3FrontendCapabilities isdbs3Caps = (Isdbs3FrontendCapabilities) caps;
        isdbs3Caps.getModulationCapability();
        isdbs3Caps.getCodeRateCapability();
    }

    private void testIsdbsFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof IsdbsFrontendCapabilities);
        IsdbsFrontendCapabilities isdbsCaps = (IsdbsFrontendCapabilities) caps;
        isdbsCaps.getModulationCapability();
        isdbsCaps.getCodeRateCapability();
    }

    private void testIsdbtFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof IsdbtFrontendCapabilities);
        IsdbtFrontendCapabilities isdbtCaps = (IsdbtFrontendCapabilities) caps;
        isdbtCaps.getModeCapability();
        isdbtCaps.getBandwidthCapability();
        isdbtCaps.getModulationCapability();
        isdbtCaps.getCodeRateCapability();
        isdbtCaps.getGuardIntervalCapability();
        isdbtCaps.getTimeInterleaveModeCapability();
        isdbtCaps.isSegmentAutoSupported();
        isdbtCaps.isFullSegmentSupported();
    }

    private void testDtmbFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof DtmbFrontendCapabilities);
        DtmbFrontendCapabilities dtmbCaps = (DtmbFrontendCapabilities) caps;
        dtmbCaps.getTimeInterleaveModeCapability();
        dtmbCaps.getBandwidthCapability();
        dtmbCaps.getModulationCapability();
        dtmbCaps.getCodeRateCapability();
        dtmbCaps.getTransmissionModeCapability();
        dtmbCaps.getGuardIntervalCapability();
    }

    private void testIptvFrontendCapabilities(FrontendCapabilities caps) throws Exception {
        assertTrue(caps instanceof IptvFrontendCapabilities);
        IptvFrontendCapabilities iptvCaps = (IptvFrontendCapabilities) caps;
        iptvCaps.getProtocolCapability();
    }

    private boolean hasTuner() {
        return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
    }
}
