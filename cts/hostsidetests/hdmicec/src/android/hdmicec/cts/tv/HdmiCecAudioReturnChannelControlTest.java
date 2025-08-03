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

package android.hdmicec.cts.tv;

import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;

/** HDMI CEC test to test audio return channel control (Section 11.2.17) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecAudioReturnChannelControlTest extends BaseHdmiCecCtsTest {

    public HdmiCecAudioReturnChannelControlTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "a");
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    /**
     * Test 11.1.17-1
     *
     * <p>Tests that the DUT sends a directly addressed {@code <Request ARC Initiation>} message.
     */
    @Ignore("b/187168483")
    @Test
    public void cect_11_1_17_1_DutSendsRequestArcInitiation() throws Exception {
        // Ensure that ARC is off.
        changeArcState(false);
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.AUDIO_SYSTEM);
        assertWithMessage("DUT does not send a <Request ARC Initiation> message.")
                .that(changeArcState(true))
                .isTrue();
    }

    /**
     * Test 11.1.17-2
     *
     * <p>Tests that the DUT responds with a directly addressed {@code <Report ARC initiated>}
     * message to the Audio System when ARC is initiated.
     */
    @Ignore("b/174813656")
    @Test
    public void cect_11_1_17_2_ReportArcInitiated() throws Exception {
        String params =
                String.format(
                        "%04d%02d",
                        hdmiCecClient.getPhysicalAddress(),
                        HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
        hdmiCecClient.sendCecMessage(
                LogicalAddress.AUDIO_SYSTEM,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecMessage.formatParams(params));
        hdmiCecClient.sendCecMessage(
                LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.INITIATE_ARC);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.AUDIO_SYSTEM, CecOperand.ARC_INITIATED);
    }

    /**
     * Test 11.1.17-3
     *
     * <p>Tests that the DUT sends a directly addressed {@code <Request ARC Termination>} message.
     */
    @Ignore("b/187168483")
    @Test
    public void cect_11_1_17_3_DutSendsRequestArcTermination() throws Exception {
        // Ensure that ARC is on.
        changeArcState(true);
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.AUDIO_SYSTEM);
        assertWithMessage("DUT does not send a <Request ARC Termination> message.")
                .that(changeArcState(false))
                .isTrue();
    }

    /**
     * Test 11.1.17-2,4
     *
     * <p>Tests that the DUT responds with a directly addressed {@code <Report ARC terminated>}
     * message to the Audio System when ARC is terminated.
     */
    @Ignore("b/174813656")
    @Test
    public void cect_11_1_17_2_4_ReportArcInitiatedTerminated() throws Exception {
        /* We need to initiate ARC, so call the Initiate ARC test first */
        cect_11_1_17_2_ReportArcInitiated();
        hdmiCecClient.sendCecMessage(
                LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.TERMINATE_ARC);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.AUDIO_SYSTEM, CecOperand.ARC_TERMINATED);
    }

    /**
     * Test 11.1.17-5
     *
     * <p>Tests that the DUT does not respond with any directly addressed {@code <Report ARC
     * initiated>} message to a non-adjacent device
     */
    @Test
    public void cect_11_1_17_5_NonAdjacentDeviceArcInitiation() throws Exception {
        int originalPhyAdd = hdmiCecClient.getPhysicalAddress();
        try {
            int nonAdjacentPhyAdd = 0x1100;
            String params =
                    CecMessage.formatParams(nonAdjacentPhyAdd)
                            + CecMessage.formatParams(
                                    HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
            /* Take physical address 1.1.0.0 */
            hdmiCecClient.setPhysicalAddress(nonAdjacentPhyAdd);
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.AUDIO_SYSTEM,
                    LogicalAddress.BROADCAST,
                    CecOperand.REPORT_PHYSICAL_ADDRESS,
                    params);
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.INITIATE_ARC);
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.AUDIO_SYSTEM, CecOperand.ARC_INITIATED);
        } finally {
            /* Restore physical address */
            hdmiCecClient.setPhysicalAddress(originalPhyAdd);
        }
    }

    /**
     * Test Short Audio Descriptor feature
     *
     * <p>Enables ARC and validates that the DUT sends {@code <Request Short Audio Descriptor>}
     * messages for the codecs it was configured to query.
     */
    @Ignore("b/174813656")
    @Test
    public void shortAudioDescriptorsRequested() throws Exception {
        String previousQuerySadLpcm = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_LPCM, HdmiCecConstants.QUERY_SAD_ENABLED);
        String previousQuerySadDd = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_DD, HdmiCecConstants.QUERY_SAD_ENABLED);
        String previousQuerySadMpeg1 = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_MPEG1, HdmiCecConstants.QUERY_SAD_ENABLED);
        String previousQuerySadMp3 = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_MP3, HdmiCecConstants.QUERY_SAD_ENABLED);
        String previousQuerySadMpeg2 = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_MPEG2, HdmiCecConstants.QUERY_SAD_ENABLED);
        String previousQuerySadAac = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_AAC, HdmiCecConstants.QUERY_SAD_ENABLED);
        String previousQuerySadDts = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_DTS, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadAtrac = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_ATRAC, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadOneBitAudio = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_ONEBITAUDIO, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadDdp = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_DDP, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadDtshd = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_DTSHD, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadTruehd = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_TRUEHD, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadDst = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_DST, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadWmapro = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_WMAPRO, HdmiCecConstants.QUERY_SAD_DISABLED);
        String previousQuerySadMax = setSettingsValue(
                HdmiCecConstants.QUERY_SAD_MAX, HdmiCecConstants.QUERY_SAD_DISABLED);
        HashSet<Integer> expectedCodecs = new HashSet<>(Arrays.asList(
                        HdmiCecConstants.AUDIO_CODEC_LPCM,
                        HdmiCecConstants.AUDIO_CODEC_DD,
                        HdmiCecConstants.AUDIO_CODEC_MPEG1,
                        HdmiCecConstants.AUDIO_CODEC_MP3,
                        HdmiCecConstants.AUDIO_CODEC_MPEG2,
                        HdmiCecConstants.AUDIO_CODEC_AAC));
        try {
            String params =
                    String.format(
                            "%04d%02d",
                            hdmiCecClient.getPhysicalAddress(),
                            HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.AUDIO_SYSTEM,
                    LogicalAddress.BROADCAST,
                    CecOperand.REPORT_PHYSICAL_ADDRESS,
                    CecMessage.formatParams(params));
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.INITIATE_ARC);
            String requestSad1 = hdmiCecClient.checkExpectedOutput(LogicalAddress.AUDIO_SYSTEM,
                    CecOperand.REQUEST_SHORT_AUDIO_DESCRIPTOR);
            HashSet<Integer> codecs = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                codecs.add(CecMessage.getParams(requestSad1, 2 * i, 2 * i + 2));
            }
            // The first SAD query needs to be replied to, in order for the second query to be sent
            // as well.
            hdmiCecClient.sendCecMessage(LogicalAddress.AUDIO_SYSTEM, CecOperand.FEATURE_ABORT,
                    CecMessage.formatParams("A403"));
            String requestSad2 = hdmiCecClient.checkExpectedOutput(LogicalAddress.AUDIO_SYSTEM,
                    CecOperand.REQUEST_SHORT_AUDIO_DESCRIPTOR);
            for (int i = 0; i < 2; i++) {
                codecs.add(CecMessage.getParams(requestSad2, 2 * i, 2 * i + 2));
            }
            assertWithMessage(
                    "Requested codecs are " + codecs + " but expected to be " + expectedCodecs)
                    .that(codecs)
                    .isEqualTo(expectedCodecs);
        } finally {
            setSettingsValue(HdmiCecConstants.QUERY_SAD_LPCM, previousQuerySadLpcm);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_DD, previousQuerySadDd);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_MPEG1, previousQuerySadMpeg1);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_MP3, previousQuerySadMp3);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_MPEG2, previousQuerySadMpeg2);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_AAC, previousQuerySadAac);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_DTS, previousQuerySadDts);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_ATRAC, previousQuerySadAtrac);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_ONEBITAUDIO, previousQuerySadOneBitAudio);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_DDP, previousQuerySadDdp);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_DTSHD, previousQuerySadDtshd);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_TRUEHD, previousQuerySadTruehd);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_DST, previousQuerySadDst);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_WMAPRO, previousQuerySadWmapro);
            setSettingsValue(HdmiCecConstants.QUERY_SAD_MAX, previousQuerySadMax);
        }
    }

    /**
     * This method will turn on/off the ARC and ensure that it is processed successfully.
     *
     * @param enabled boolean value. Value true to turn ARC on.
     * @return {@code true} if ARC process was successful.
     */
    private boolean changeArcState(boolean enabled) throws Exception {
        getDevice().executeShellCommand("cmd hdmi_control setarc " + (enabled ? "on" : "off"));
        try {
            hdmiCecClient.checkExpectedOutput(
                    LogicalAddress.AUDIO_SYSTEM,
                    enabled
                            ? CecOperand.REQUEST_ARC_INITIATION
                            : CecOperand.REQUEST_ARC_TERMINATION);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
