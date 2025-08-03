/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts.playback;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC system information tests (Section 11.2.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemInformationTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    public HdmiCecSystemInformationTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    /**
     * Test 11.2.6-7
     * Tests that the device sends a <FEATURE_ABORT> in response to a <GET_MENU_LANGUAGE>
     */
    @Test
    public void cect_11_2_6_7_GetMenuLanguage() throws Exception {
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.GET_MENU_LANGUAGE);
        String message = hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.FEATURE_ABORT);
        int abortedOpcode = CecMessage.getParams(message,
                CecOperand.GET_MENU_LANGUAGE.toString().length());
        assertThat(CecOperand.getOperand(abortedOpcode)).isEqualTo(CecOperand.GET_MENU_LANGUAGE);
    }

    /**
     * Test 11.2.6-4
     * Tests that the device ignores a <SET_MENU_LANGUAGE> with an invalid language.
     */
    @Test
    public void cect_11_2_6_4_SetInvalidMenuLanguage() throws Exception {
        assumeTrue(isLanguageEditable());
        final String locale = getSystemLocale();
        final String originalLanguage = extractLanguage(locale);
        final String language = "spb";
        try {
            hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                    CecOperand.SET_MENU_LANGUAGE, CecMessage.convertStringToHexParams(language));
            assertThat(extractLanguage(getSystemLocale())).isEqualTo(originalLanguage);
        } finally {
            setSystemLocale(locale);
        }
    }

    /**
     * Test 11.2.6-5
     * Tests that the device ignores a <SET_MENU_LANGUAGE> with a valid language that comes from a
     * source device which is not TV.
     */
    @Test
    public void cect_11_2_6_5_SetValidMenuLanguageFromInvalidSource() throws Exception {
        assumeTrue(isLanguageEditable());
        final String locale = getSystemLocale();
        final String originalLanguage = extractLanguage(locale);
        final String language = originalLanguage.equals("spa") ? "eng" : "spa";
        try {
            hdmiCecClient.sendCecMessage(LogicalAddress.RECORDER_1, LogicalAddress.BROADCAST,
                    CecOperand.SET_MENU_LANGUAGE, CecMessage.convertStringToHexParams(language));
            assertThat(extractLanguage(getSystemLocale())).isEqualTo(originalLanguage);
        } finally {
            setSystemLocale(locale);
        }
    }

    /**
     * Test HF4-11-4 (CEC 2.0)
     *
     * <p>Tests that the DUT responds to {@code <Give Features>} with "Sink supports ARC Tx" bit not
     * set.
     */
    @Test
    public void cect_hf_4_11_4_SinkArcTxBitReset() throws Exception {
        setCec20();
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.GIVE_FEATURES);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
        int params = CecMessage.getParams(message, 6, 8);
        assertThat(params & HdmiCecConstants.FEATURES_SINK_SUPPORTS_ARC_TX_BIT).isEqualTo(0);
    }

    /**
     * Test HF4-11-5 (CEC 2.0)
     *
     * <p>Tests that the DUT responds to {@code <Give Features>} with "Sink supports ARC Tx" bit not
     * set and "Sink support ARC Rx" bit set/reset appropriately.
     */
    @Test
    public void cect_hf_4_11_5_CheckArcTxRxBits() throws Exception {
        setCec20();
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.GIVE_FEATURES);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
        int params = CecMessage.getParams(message, 6, 8);
        assertThat(params & HdmiCecConstants.FEATURES_SINK_SUPPORTS_ARC_TX_BIT).isEqualTo(0);

        boolean hasAudioSystem =
                getDevice()
                        .getProperty(HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY)
                        .contains(Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));
        boolean isArcSupported =
                getDevice().getBooleanProperty(HdmiCecConstants.PROPERTY_ARC_SUPPORT, false);
        if (hasAudioSystem && isArcSupported) {
            // This has an Audio System as well, so ARC Rx bit has to be set.
            assertThat(params & HdmiCecConstants.FEATURES_SINK_SUPPORTS_ARC_RX_BIT).isEqualTo(1);
        } else {
            // No Audio System, so ARC Rx bit has to be reset.
            assertThat(params & HdmiCecConstants.FEATURES_SINK_SUPPORTS_ARC_RX_BIT).isEqualTo(0);
        }
    }
}
