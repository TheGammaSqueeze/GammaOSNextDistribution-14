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

package android.hdmicec.cts.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.BaseHdmiCecCtsTest.CecRules;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests to see that a valid HDMI CEC device type is declared by the device. */
@RunWith(DeviceJUnit4ClassRunner.class)
@OptionClass(alias="hdmi-cec-cts-test")
public final class HdmiCecDeviceTypeTest extends BaseHostJUnit4Test {

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(CecRules.requiresCec(this))
            .around(CecRules.requiresLeanback(this))
            .around(CecRules.requiresPhysicalDevice(this));


    /** @deprecated not used anymore **/
    @Deprecated
    @Option(name = HdmiCecConstants.PHYSICAL_ADDRESS_NAME,
        description = "HDMI CEC physical address of the DUT",
        mandatory = false)
    /*
     * This variable is defined only to enable the physical address to be passed as a CLI argument
     * to the module, it won't be used.
     */
    public static int dutPhysicalAddress = HdmiCecConstants.DEFAULT_PHYSICAL_ADDRESS;

    int deviceTvOnly = setBit(HdmiCecConstants.CEC_DEVICE_TYPE_TV);
    int devicePlaybackOnly = setBit(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    int deviceComboTvSwitch =
            setBit(HdmiCecConstants.CEC_DEVICE_TYPE_TV)
                    | setBit(HdmiCecConstants.CEC_DEVICE_TYPE_SWITCH);
    int deviceComboPlaybackAudioSystem =
            setBit(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE)
                    | setBit(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
    int deviceComboPlaybackAudioSystemSwitch =
            setBit(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE)
                    | setBit(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM)
                    | setBit(HdmiCecConstants.CEC_DEVICE_TYPE_SWITCH);

    private final List<Integer> allowedDeviceCombos =
            new ArrayList<>(
                    Arrays.asList(
                            deviceTvOnly,
                            devicePlaybackOnly,
                            deviceComboTvSwitch,
                            deviceComboPlaybackAudioSystem,
                            deviceComboPlaybackAudioSystemSwitch));

    /**
     * Tests that the device declares a valid HDMI CEC device type.
     */
    @Test
    public void checkHdmiCecDeviceType() throws Exception {
        int deviceTypes = getAllDeviceTypes(getDevice(), true);

        if (deviceTypes == 0) {
            // If enums are not populated, try to get the device types from the int property
            deviceTypes = getAllDeviceTypes(getDevice(), false);
        }

        assertWithMessage("Incorrect device combination")
                .that(deviceTypes)
                .isIn(allowedDeviceCombos);
    }

    private int getAllDeviceTypes(ITestDevice device, boolean fromEnum) {
        int deviceTypes = 0;
        String deviceType = "";
        try {
            if (fromEnum) {
                deviceType = device.executeShellCommand("getprop ro.hdmi.cec_device_types").trim();
            } else {
                deviceType = device.executeShellCommand("getprop ro.hdmi.device_type").trim();
            }
        } catch (DeviceNotAvailableException dnae) {
            return 0;
        }

        String[] cecDevices = deviceType.split(",");
        for (String cecDevice : cecDevices) {
            if (!cecDevice.equals("")) {
                if (fromEnum) {
                    deviceTypes |= setBit(stringToIntDeviceType(cecDevice));
                } else {
                    deviceTypes |= setBit(Integer.parseInt(cecDevice));
                }
            }
        }

        return deviceTypes;
    }

    private int setBit(int value) {
        return (1 << value);
    }

    private int stringToIntDeviceType(String value) {
        switch (value) {
            case "tv":
                return HdmiCecConstants.CEC_DEVICE_TYPE_TV;
            case "recording_device":
                return HdmiCecConstants.CEC_DEVICE_TYPE_RECORDER;
            case "reserved":
                return HdmiCecConstants.CEC_DEVICE_TYPE_RESERVED;
            case "tuner":
                return HdmiCecConstants.CEC_DEVICE_TYPE_TUNER;
            case "playback_device":
                return HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE;
            case "audio_system":
                return HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM;
            case "pure_cec_switch":
                return HdmiCecConstants.CEC_DEVICE_TYPE_SWITCH;
            case "video_processor":
                return HdmiCecConstants.CEC_DEVICE_TYPE_VIDEO_PROCESSOR;
            default:
                fail("Unrecognized device type: " + value);
                return 0; // Prevent compiler error
        }
    }

    /** Tests that the cec_device_types enum, if set, is equivalent to the int device_type. */
    @Test
    public void cecDeviceTypesSameAsDeviceType() throws Exception {
        ITestDevice device = getDevice();
        int deviceType = getAllDeviceTypes(device, false);
        int cecDeviceTypes = getAllDeviceTypes(device, true);
        assumeTrue(
                "Looks like ro.hdmi.device_type property is not set/DUT is not an HDMI device.",
                deviceType != 0);
        assertWithMessage("Enum and integer device types mismatch")
                .that(deviceType)
                .isEqualTo(cecDeviceTypes);
    }
}
