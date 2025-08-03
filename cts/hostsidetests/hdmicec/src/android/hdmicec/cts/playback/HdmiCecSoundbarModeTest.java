/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecSoundbarModeTest extends BaseHdmiCecCtsTest {
    public String prevSoundbarModeValue;

    public HdmiCecSoundbarModeTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresArcSupport(this, true))
                    .around(CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    @Before
    public void initialTestSetup() throws Exception {
        prevSoundbarModeValue = getSettingsValue(HdmiCecConstants.SETTING_SOUNDBAR_MODE_ENABLED);
    }

    @After
    public void resetSoundbarModeStatus() throws Exception {
        setSettingsValue(
                HdmiCecConstants.SETTING_SOUNDBAR_MODE_ENABLED,
                prevSoundbarModeValue);
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
    }

    /**
     * Tests that the audio system sends a {@code <Initiate ARC>} message when the Soundbar mode
     * is turned on, indicating the local device was added in the network.
     */
    @Test
    public void cectSoundbarModeEnabled() throws Exception {
        setSettingsValue(
                HdmiCecConstants.SETTING_SOUNDBAR_MODE_ENABLED,
                HdmiCecConstants.SOUNDBAR_MODE_ENABLED);
        hdmiCecClient.checkExpectedOutput(
                LogicalAddress.AUDIO_SYSTEM,
                LogicalAddress.TV,
                CecOperand.INITIATE_ARC);
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
        String localDevicesList = getLocalDevicesList();
        assertThat(localDevicesList).contains(
                Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));
    }

    /**
     * Tests that the audio system is removed from the network when the Soundbar mode is turned off.
     */
    @Test
    public void cectSoundbarModeDisabled() throws Exception {
        setSettingsValue(
                HdmiCecConstants.SETTING_SOUNDBAR_MODE_ENABLED,
                HdmiCecConstants.SOUNDBAR_MODE_ENABLED);
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);

        setSettingsValue(
                HdmiCecConstants.SETTING_SOUNDBAR_MODE_ENABLED,
                HdmiCecConstants.SOUNDBAR_MODE_DISABLED);
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
        String localDevicesList = getLocalDevicesList();
        assertThat(localDevicesList).doesNotContain(
                Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));
    }
}
