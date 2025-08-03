/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogHelper;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** HDMI CEC test to verify that device ignores invalid messages (Section 12) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecInvalidMessagesTest extends BaseHdmiCecCtsTest {

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";

    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecKeyEventCapture";

    /** The command to launch the main activity. */
    private static final String START_COMMAND =
            String.format(
                    "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
                    PACKAGE, PACKAGE, CLASS);

    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    private LogicalAddress source;
    private LogicalAddress targetLogicalAddress;
    private LogicalAddress mNonLocalPlaybackAddress;

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    @Before
    public void setupLogicalAddresses() throws Exception {
        source = (hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV)) ? LogicalAddress.RECORDER_1
                                                                      : LogicalAddress.TV;
        targetLogicalAddress = getTargetLogicalAddress();
        mNonLocalPlaybackAddress =
                (targetLogicalAddress == LogicalAddress.PLAYBACK_1)
                        ? LogicalAddress.PLAYBACK_2
                        : LogicalAddress.PLAYBACK_1;
    }

    private int getUnusedPhysicalAddress(int usedValue) {
        return (usedValue == 0x2000) ? 0x3000 : 0x2000;
    }

    private void reportPhysicalAddress(LogicalAddress logicalAddress, int physicalAddress,
            int deviceType) throws Exception {
        String formattedPhysicalAddress = CecMessage.formatParams(physicalAddress,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String formattedDeviceType = CecMessage.formatParams(deviceType);
        hdmiCecClient.sendCecMessage(
                logicalAddress,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                formattedPhysicalAddress + formattedDeviceType
        );
    }

    /**
     * Test 12-1
     *
     * <p>Tests that the device ignores every broadcast only message that is received as directly
     * addressed.
     */
    @Test
    public void cect_12_1_BroadcastReceivedAsDirectlyAddressed() throws Exception {
        /* <Set Menu Language> */
        assumeTrue("Language should be editable for this test", isLanguageEditable());
        final String locale = getSystemLocale();
        final String originalLanguage = extractLanguage(locale);
        final String language = originalLanguage.equals("spa") ? "eng" : "spa";
        try {
            hdmiCecClient.sendCecMessage(
                    source,
                    CecOperand.SET_MENU_LANGUAGE,
                    CecMessage.convertStringToHexParams(language));
            assertThat(originalLanguage).isEqualTo(extractLanguage(getSystemLocale()));
        } finally {
            // If the language was incorrectly changed during the test, restore it.
            setSystemLocale(locale);
        }
    }

    /**
     * Test 12-2
     *
     * <p>Tests that the device ignores directly addressed message {@code <GET_CEC_VERSION>} if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_getCecVersion() throws Exception {
        hdmiCecClient.sendCecMessage(source, LogicalAddress.BROADCAST, CecOperand.GET_CEC_VERSION);
        hdmiCecClient.checkOutputDoesNotContainMessage(source, CecOperand.CEC_VERSION);
    }

    /**
     * Test 12-2
     *
     * <p>Tests that the device ignores directly addressed message {@code <GIVE_PHYSICAL_ADDRESS>}
     * if received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_givePhysicalAddress()
            throws Exception {
        hdmiCecClient.sendCecMessage(
                source, LogicalAddress.BROADCAST, CecOperand.GIVE_PHYSICAL_ADDRESS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST, CecOperand.REPORT_PHYSICAL_ADDRESS);
    }

    /**
     * Test 12-2
     *
     * <p>Tests that the device ignores directly addressed message {@code <GIVE_POWER_STATUS>} if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_givePowerStatus() throws Exception {
        hdmiCecClient.sendCecMessage(
                source, LogicalAddress.BROADCAST, CecOperand.GIVE_POWER_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(source, CecOperand.REPORT_POWER_STATUS);
    }

    /**
     * Test 12-2
     *
     * <p>Tests that the device ignores directly addressed message {@code <GIVE_DEVICE_VENDOR_ID>}
     * if received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveDeviceVendorId()
            throws Exception {
        hdmiCecClient.sendCecMessage(
                source, LogicalAddress.BROADCAST, CecOperand.GIVE_DEVICE_VENDOR_ID);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST, CecOperand.DEVICE_VENDOR_ID);
    }

    /**
     * Test 12-2
     *
     * <p>Tests that the device ignores directly addressed message {@code <GIVE_OSD_NAME>} if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveOsdName() throws Exception {
        hdmiCecClient.sendCecMessage(source, LogicalAddress.BROADCAST, CecOperand.GIVE_OSD_NAME);
        hdmiCecClient.checkOutputDoesNotContainMessage(source, CecOperand.SET_OSD_NAME);
    }

    /**
     * Test 12-2
     *
     * <p>Tests that the device ignores directly addressed message {@code <USER_CONTROL_PRESSED>} if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_userControlPressed()
            throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndRelease(
                source, LogicalAddress.BROADCAST, HdmiCecConstants.CEC_KEYCODE_UP, false);
        LogHelper.assertLogDoesNotContain(getDevice(), CLASS, "Short press KEYCODE_DPAD_UP");
    }

    /**
     * <p>Tests that the device ignores a directly addressed message {@code <GIVE_PHYSICAL_ADDRESS>}
     * if received as a broadcast message and its source is the device's logical address
     */
    @Test
    public void cect_IgnoreDirectlyAddressedFromSameSource()
            throws Exception {
        hdmiCecClient.sendCecMessage(
                targetLogicalAddress, targetLogicalAddress, CecOperand.GIVE_PHYSICAL_ADDRESS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                targetLogicalAddress, CecOperand.REPORT_PHYSICAL_ADDRESS);
    }

    /**
     * <p>Tests that the device ignores a broadcasted message {@code <REQUEST_ACTIVE_SOURCE>} if its
     * source has the logical address equal to device's logical address
     * Change the active source to another device (a new Playback) first.
     */
    @Test
    public void cect_IgnoreBroadcastedFromSameSource()
            throws Exception {
        String previousPowerStateChange = setPowerStateChangeOnActiveSourceLost(
                HdmiCecConstants.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_NONE);
        try {
            int dumpsysPhysicalAddress = getDumpsysPhysicalAddress();
            // Add a new playback device in the network.
            int playbackPhysicalAddress = getUnusedPhysicalAddress(dumpsysPhysicalAddress);
            reportPhysicalAddress(
                    mNonLocalPlaybackAddress,
                    playbackPhysicalAddress,
                    HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
            // Make the new Playback the active source.
            hdmiCecClient.broadcastActiveSource(mNonLocalPlaybackAddress, playbackPhysicalAddress);
            // Wait for the <Active Source> message to be processed by the DUT.
            TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);

            // Press Home key and the DUT shall broadcast an <Active Source> message.
            ITestDevice device = getDevice();
            device.executeShellCommand("input keyevent KEYCODE_HOME");
            hdmiCecClient.checkExpectedOutput(
                    LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
            // The DUT shouldn't send <Active Source> again.
            hdmiCecClient.sendCecMessage(
                    targetLogicalAddress, LogicalAddress.BROADCAST, CecOperand.REQUEST_ACTIVE_SOURCE);
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
        } finally {
            // Restore the previous power state change.
            setPowerStateChangeOnActiveSourceLost(previousPowerStateChange);
        }
    }

    /**
     * <p>Tests that the device ignores a directly addressed message {@code <GIVE_POWER_STATUS>} if
     * coming from the unregistered address F. This message should only be sent from a device with
     * an allocated logical address
     */
    @Test
    public void cect_IgnoreDirectlyAddressedFromUnknownAddress_giveDevicePowerStatus()
            throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.UNKNOWN, targetLogicalAddress, CecOperand.GIVE_POWER_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.UNKNOWN, CecOperand.REPORT_POWER_STATUS);
    }

    /**
     * <p>Tests that the device process a directly addressed message {@code <GIVE_PHYSICAL_ADDRESS>}
     * if coming from the unregistered address F
     */
    @Test
    public void cect_ProcessAddressedFromUnknownAddress_givePhysicalAddress()
            throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.UNKNOWN, targetLogicalAddress, CecOperand.GIVE_PHYSICAL_ADDRESS);
        hdmiCecClient.checkExpectedOutput(
                LogicalAddress.UNKNOWN, CecOperand.REPORT_PHYSICAL_ADDRESS);
    }
}
