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

package android.hdmicec.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.HdmiCecConstants.CecDeviceType;
import android.hdmicec.cts.error.DumpsysParseException;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.rules.TestRule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base class for all HDMI CEC CTS tests. */
@OptionClass(alias = "hdmi-cec-client-cts-test")
public class BaseHdmiCecCtsTest extends BaseHostJUnit4Test {

    public static final String PROPERTY_LOCALE = "persist.sys.locale";
    private static final String POWER_CONTROL_MODE = "power_control_mode";
    private static final String POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST =
            "power_state_change_on_active_source_lost";
    private static final String SET_MENU_LANGUAGE = "set_menu_language";
    private static final String SET_MENU_LANGUAGE_ENABLED = "1";

    private static final String SOUNDBAR_MODE = "soundbar_mode";
    private static final String SETTING_VOLUME_CONTROL_ENABLED = "volume_control_enabled";

    /** Enum contains the list of possible address types. */
    private enum AddressType {
        DUMPSYS_AS_LOGICAL_ADDRESS("activeSourceLogicalAddress"),
        DUMPSYS_PHYSICAL_ADDRESS("physicalAddress");

        private String address;

        public String getAddressType() {
            return this.address;
        }

        private AddressType(String address) {
            this.address = address;
        }
    }

    public final HdmiCecClientWrapper hdmiCecClient;
    public List<LogicalAddress> mDutLogicalAddresses = new ArrayList<>();
    public @CecDeviceType int mTestDeviceType;

    /**
     * Constructor for BaseHdmiCecCtsTest.
     */
    public BaseHdmiCecCtsTest() {
        this(HdmiCecConstants.CEC_DEVICE_TYPE_UNKNOWN);
    }

    /**
     * Constructor for BaseHdmiCecCtsTest.
     *
     * @param clientParams Extra parameters to use when launching cec-client
     */
    public BaseHdmiCecCtsTest(String... clientParams) {
        this(HdmiCecConstants.CEC_DEVICE_TYPE_UNKNOWN, clientParams);
    }

    /**
     * Constructor for BaseHdmiCecCtsTest.
     *
     * @param testDeviceType The primary test device type. This is used to determine to which
     *                       logical address of the DUT messages should be sent.
     * @param clientParams   Extra parameters to use when launching cec-client
     */
    public BaseHdmiCecCtsTest(@CecDeviceType int testDeviceType, String... clientParams) {
        this.hdmiCecClient = new HdmiCecClientWrapper(clientParams);
        mTestDeviceType = testDeviceType;
    }

    @Before
    public void setUp() throws Exception {
        setCec14();

        mDutLogicalAddresses = getDumpsysLogicalAddresses();
        hdmiCecClient.setTargetLogicalAddress(getTargetLogicalAddress());
        boolean startAsTv = !hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV);
        hdmiCecClient.init(startAsTv, getDevice());
    }

    /** Class with predefined rules which can be used by HDMI CEC CTS tests. */
    public static class CecRules {

        public static TestRule requiresCec(BaseHostJUnit4Test testPointer) {
            return new RequiredFeatureRule(testPointer, HdmiCecConstants.HDMI_CEC_FEATURE);
        }

        public static TestRule requiresLeanback(BaseHostJUnit4Test testPointer) {
            return new RequiredFeatureRule(testPointer, HdmiCecConstants.LEANBACK_FEATURE);
        }

        public static TestRule requiresDeviceType(
                BaseHostJUnit4Test testPointer, @CecDeviceType int dutDeviceType) {
            return RequiredDeviceTypeRule.requiredDeviceType(
                    testPointer,
                    dutDeviceType);
        }

        public static TestRule requiresArcSupport(
                BaseHostJUnit4Test testPointer, boolean arcSupport) {
            return RequiredPropertyRule.asCsvContainsValue(
                    testPointer,
                    HdmiCecConstants.PROPERTY_ARC_SUPPORT,
                    Boolean.toString(arcSupport));
        }

        /** This rule will skip the test if the DUT belongs to the HDMI device type deviceType. */
        public static TestRule skipDeviceType(
                BaseHostJUnit4Test testPointer, @CecDeviceType int deviceType) {
            return RequiredDeviceTypeRule.invalidDeviceType(
                    testPointer,
                    deviceType);
        }

        /** This rule will skip the test if the DUT is an emulator. */
        public static TestRule requiresPhysicalDevice(BaseHostJUnit4Test testPointer) {
            return RequiredDeviceTypeRule.requiredPhysicalDevice(testPointer);
        }
    }

    /** @deprecated not used anymore **/
    @Deprecated
    @Option(name = HdmiCecConstants.PHYSICAL_ADDRESS_NAME,
            description = "HDMI CEC physical address of the DUT",
            mandatory = false)
    public static int dutPhysicalAddress = HdmiCecConstants.DEFAULT_PHYSICAL_ADDRESS;

    /** Gets the physical address of the DUT by parsing the dumpsys hdmi_control. */
    public int getDumpsysPhysicalAddress() throws DumpsysParseException {
        return getDumpsysPhysicalAddress(getDevice());
    }

    /** Gets the physical address of the specified device by parsing the dumpsys hdmi_control. */
    public static int getDumpsysPhysicalAddress(ITestDevice device) throws DumpsysParseException {
        return parseRequiredAddressFromDumpsys(device, AddressType.DUMPSYS_PHYSICAL_ADDRESS);
    }

    /** Gets the list of logical addresses of the DUT by parsing the dumpsys hdmi_control. */
    public List<LogicalAddress> getDumpsysLogicalAddresses() throws DumpsysParseException {
        return getDumpsysLogicalAddresses(getDevice());
    }

    /** Gets the list of logical addresses of the device by parsing the dumpsys hdmi_control. */
    public static List<LogicalAddress> getDumpsysLogicalAddresses(ITestDevice device)
            throws DumpsysParseException {
        List<LogicalAddress> logicalAddressList = new ArrayList<>();
        String line;
        String pattern =
                "(.*?)"
                        + "(mDeviceInfo:)(.*)(logical_address: )"
                        + "(?<"
                        + "logicalAddress"
                        + ">0x\\p{XDigit}{2})"
                        + "(.*?)";
        Pattern p = Pattern.compile(pattern);
        try {
            String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
            BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    int address = Integer.decode(m.group("logicalAddress"));
                    LogicalAddress logicalAddress = LogicalAddress.getLogicalAddress(address);
                    logicalAddressList.add(logicalAddress);
                }
            }
            if (!logicalAddressList.isEmpty()) {
                return logicalAddressList;
            }
        } catch (IOException | DeviceNotAvailableException e) {
            throw new DumpsysParseException(
                    "Could not parse logicalAddress from dumpsys.", e);
        }
        throw new DumpsysParseException(
                "Could not parse logicalAddress from dumpsys.");
    }

    /**
     * Gets the system audio mode status of the device by parsing the dumpsys hdmi_control. Returns
     * true when system audio mode is on and false when system audio mode is off
     */
    public boolean isSystemAudioModeOn(ITestDevice device) throws DumpsysParseException {
        List<LogicalAddress> logicalAddressList = new ArrayList<>();
        String line;
        String pattern =
                "(.*?)"
                        + "(mSystemAudioActivated: )"
                        + "(?<"
                        + "systemAudioModeStatus"
                        + ">[true|false])"
                        + "(.*?)";
        Pattern p = Pattern.compile(pattern);
        try {
            String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
            BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    return m.group("systemAudioModeStatus").equals("true");
                }
            }
        } catch (IOException | DeviceNotAvailableException e) {
            throw new DumpsysParseException("Could not parse system audio mode from dumpsys.", e);
        }
        throw new DumpsysParseException("Could not parse system audio mode from dumpsys.");
    }

    /** Gets the DUT's logical address to which messages should be sent */
    public LogicalAddress getTargetLogicalAddress() throws DumpsysParseException {
        return getTargetLogicalAddress(getDevice(), mTestDeviceType);
    }

    /** Gets the given device's logical address to which messages should be sent */
    public static LogicalAddress getTargetLogicalAddress(ITestDevice device)
            throws DumpsysParseException {
        return getTargetLogicalAddress(device, HdmiCecConstants.CEC_DEVICE_TYPE_UNKNOWN);
    }

    /**
     * Gets the given device's logical address to which messages should be sent, based on the test
     * device type.
     *
     * When the test doesn't specify a device type, or the device doesn't have a logical address
     * that matches the specified device type, use the first logical address.
     */
    public static LogicalAddress getTargetLogicalAddress(ITestDevice device, int testDeviceType)
            throws DumpsysParseException {
        List<LogicalAddress> logicalAddressList = getDumpsysLogicalAddresses(device);
        for (LogicalAddress address : logicalAddressList) {
            if (address.getDeviceType() == testDeviceType) {
                return address;
            }
        }
        return logicalAddressList.get(0);
    }

    /**
     * Parses the dumpsys hdmi_control to get the logical address of the current device registered
     * as active source.
     */
    public LogicalAddress getDumpsysActiveSourceLogicalAddress() throws DumpsysParseException {
        ITestDevice device = getDevice();
        int address =
                parseRequiredAddressFromDumpsys(device, AddressType.DUMPSYS_AS_LOGICAL_ADDRESS);
        return LogicalAddress.getLogicalAddress(address);
    }

    private static int parseRequiredAddressFromDumpsys(ITestDevice device, AddressType addressType)
            throws DumpsysParseException {
        Matcher m;
        String line;
        String pattern;
        switch (addressType) {
            case DUMPSYS_PHYSICAL_ADDRESS:
                pattern =
                        "(.*?)"
                                + "(physical_address: )"
                                + "(?<"
                                + addressType.getAddressType()
                                + ">0x\\p{XDigit}{4})"
                                + "(.*?)";
                break;
            case DUMPSYS_AS_LOGICAL_ADDRESS:
                pattern =
                        "(.*?)"
                                + "(mActiveSource: )"
                                + "(\\(0x)"
                                + "(?<"
                                + addressType.getAddressType()
                                + ">\\d+)"
                                + "(, )"
                                + "(0x)"
                                + "(?<physicalAddress>\\d+)"
                                + "(\\))"
                                + "(.*?)";
                break;
            default:
                throw new DumpsysParseException(
                        "Incorrect parameters", new IllegalArgumentException());
        }

        try {
            Pattern p = Pattern.compile(pattern);
            String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
            BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
            while ((line = reader.readLine()) != null) {
                m = p.matcher(line);
                if (m.matches()) {
                    int address = Integer.decode(m.group(addressType.getAddressType()));
                    return address;
                }
            }
        } catch (IOException | DeviceNotAvailableException e) {
            throw new DumpsysParseException(
                    "Could not parse " + addressType.getAddressType() + " from dumpsys.", e);
        }
        throw new DumpsysParseException(
                "Could not parse " + addressType.getAddressType() + " from dumpsys.");
    }

    public boolean hasDeviceType(@CecDeviceType int deviceType) {
        for (LogicalAddress address : mDutLogicalAddresses) {
            if (address.getDeviceType() == deviceType) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLogicalAddress(LogicalAddress address) {
        return mDutLogicalAddresses.contains(address);
    }

    private static void setCecVersion(ITestDevice device, int cecVersion) throws Exception {
        device.executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_version " +
                cecVersion);

        TimeUnit.SECONDS.sleep(HdmiCecConstants.TIMEOUT_CEC_REINIT_SECONDS);
    }

    /**
     * Configures the device to use CEC 2.0. Skips the test if the device does not support CEC 2.0.
     * @throws Exception
     */
    public void setCec20() throws Exception {
        setCecVersion(getDevice(), HdmiCecConstants.CEC_VERSION_2_0);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String reportCecVersion = hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.CEC_VERSION);
        boolean supportsCec2 = CecMessage.getParams(reportCecVersion)
                >= HdmiCecConstants.CEC_VERSION_2_0;

        // Device still reports a CEC version < 2.0.
        assumeTrue(supportsCec2);
    }

    public void setCec14() throws Exception {
        setCecVersion(getDevice(), HdmiCecConstants.CEC_VERSION_1_4);
    }

    public String getSystemLocale() throws Exception {
        ITestDevice device = getDevice();
        return device.executeShellCommand("getprop " + PROPERTY_LOCALE).trim();
    }

    public static String extractLanguage(String locale) {
        return locale.split("[^a-zA-Z]")[0];
    }

    public void setSystemLocale(String locale) throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("setprop " + PROPERTY_LOCALE + " " + locale);
    }

    public boolean isLanguageEditable() throws Exception {
        return getSettingsValue(SET_MENU_LANGUAGE).equals(SET_MENU_LANGUAGE_ENABLED);
    }

    public static String getSettingsValue(ITestDevice device, String setting) throws Exception {
        return device.executeShellCommand("cmd hdmi_control cec_setting get " + setting)
                .replace(setting + " = ", "").trim();
    }

    public String getSettingsValue(String setting) throws Exception {
        return getSettingsValue(getDevice(), setting);
    }

    public static String setSettingsValue(ITestDevice device, String setting, String value)
            throws Exception {
        String val = getSettingsValue(device, setting);
        device.executeShellCommand("cmd hdmi_control cec_setting set " + setting + " " +
                value);
        return val;
    }

    public String setSettingsValue(String setting, String value) throws Exception {
        return setSettingsValue(getDevice(), setting, value);
    }

    public String getDeviceList() throws Exception {
        return getDevice().executeShellCommand(
                "dumpsys hdmi_control | sed -n '/mDeviceInfos/,/mCecController/{//!p;}'");
    }

    public String getLocalDevicesList() throws Exception {
        return getDevice().executeShellCommand(
                "dumpsys hdmi_control | sed -n '/HdmiCecLocalDevice /p'\n");
    }

    public void sendDeviceToSleepAndValidate() throws Exception {
        sendDeviceToSleep();
        assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_ASLEEP);
    }

    public void waitForTransitionTo(int finalState) throws Exception {
        int powerStatus;
        int waitTimeSeconds = 0;
        LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
        int transitionState;
        if (finalState == HdmiCecConstants.CEC_POWER_STATUS_STANDBY) {
            transitionState = HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_STANDBY;
        } else if (finalState == HdmiCecConstants.CEC_POWER_STATUS_ON) {
            transitionState = HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_ON;
        } else {
            throw new Exception("Unsupported final power state!");
        }
        // We first give 2 seconds to enter the transition state, and then
        // MAX_SLEEP_TIME_SECONDS to go from the transition state to the final state.
        do {
            TimeUnit.SECONDS.sleep(HdmiCecConstants.SLEEP_TIMESTEP_SECONDS);
            waitTimeSeconds += HdmiCecConstants.SLEEP_TIMESTEP_SECONDS;
            hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
            powerStatus =
                    CecMessage.getParams(
                            hdmiCecClient.checkExpectedOutput(
                                    cecClientDevice, CecOperand.REPORT_POWER_STATUS));
            if (powerStatus == finalState) {
                return;
            }
        } while (waitTimeSeconds <= HdmiCecConstants.SLEEP_TIME_DELAY_SECONDS);
        do {
            TimeUnit.SECONDS.sleep(HdmiCecConstants.SLEEP_TIMESTEP_SECONDS);
            waitTimeSeconds += HdmiCecConstants.SLEEP_TIMESTEP_SECONDS;
            hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
            powerStatus =
                    CecMessage.getParams(
                            hdmiCecClient.checkExpectedOutput(
                                    cecClientDevice, CecOperand.REPORT_POWER_STATUS));
            if (powerStatus == finalState) {
                return;
            }
        } while (powerStatus == transitionState
                && waitTimeSeconds <= HdmiCecConstants.MAX_SLEEP_TIME_SECONDS);
        if (powerStatus != finalState) {
            // Transition not complete even after wait, throw an Exception.
            throw new Exception("Power status did not change to expected state.");
        }
    }

    public void sendDeviceToSleepWithoutWait() throws Exception {
        ITestDevice device = getDevice();
        WakeLockHelper.acquirePartialWakeLock(device);
        device.executeShellCommand("input keyevent KEYCODE_SLEEP");
    }

    public void sendDeviceToSleep() throws Exception {
        sendDeviceToSleepWithoutWait();
        assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_ASLEEP);
        waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
    }

    public void sendDeviceToSleepAndValidateUsingStandbyMessage(boolean directlyAddressed)
            throws Exception {
        ITestDevice device = getDevice();
        WakeLockHelper.acquirePartialWakeLock(device);
        LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
        if (directlyAddressed) {
            hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.STANDBY);
        } else {
            hdmiCecClient.sendCecMessage(
                    cecClientDevice, LogicalAddress.BROADCAST, CecOperand.STANDBY);
        }
        waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
    }

    public void wakeUpDevice() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_AWAKE);
        waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);
        WakeLockHelper.releasePartialWakeLock(device);
    }

    public void wakeUpDeviceWithoutWait() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_AWAKE);
        WakeLockHelper.releasePartialWakeLock(device);
    }

    public void checkStandbyAndWakeUp() throws Exception {
        assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_ASLEEP);
        wakeUpDevice();
    }

    public void assertDeviceWakefulness(String wakefulness) throws Exception {
        ITestDevice device = getDevice();
        String actualWakefulness;
        int waitTimeSeconds = 0;

        do {
            TimeUnit.SECONDS.sleep(HdmiCecConstants.SLEEP_TIMESTEP_SECONDS);
            waitTimeSeconds += HdmiCecConstants.SLEEP_TIMESTEP_SECONDS;
            actualWakefulness =
                    device.executeShellCommand("dumpsys power | grep mWakefulness=")
                            .trim().replace("mWakefulness=", "");
        } while (!actualWakefulness.equals(wakefulness)
                && waitTimeSeconds <= HdmiCecConstants.MAX_SLEEP_TIME_SECONDS);
        assertWithMessage(
                "Device wakefulness is "
                        + actualWakefulness
                        + " but expected to be "
                        + wakefulness)
                .that(actualWakefulness)
                .isEqualTo(wakefulness);
    }

    /**
     * Checks a given condition once every {@link HdmiCecConstants.SLEEP_TIMESTEP_SECONDS} seconds
     * until it is true, or {@link HdmiCecConstants.MAX_SLEEP_TIME_SECONDS} seconds have passed.
     * Triggers an assertion failure if the condition remains false after the time limit.
     * @param condition Callable that returns whether the condition is met
     * @param errorMessage The message to print if the condition is false
     */
    public void waitForCondition(Callable<Boolean> condition, String errorMessage)
            throws Exception {
        int waitTimeSeconds = 0;
        boolean conditionState;
        do {
            TimeUnit.SECONDS.sleep(HdmiCecConstants.SLEEP_TIMESTEP_SECONDS);
            waitTimeSeconds += HdmiCecConstants.SLEEP_TIMESTEP_SECONDS;
            conditionState = condition.call();
        } while (!conditionState && waitTimeSeconds <= HdmiCecConstants.MAX_SLEEP_TIME_SECONDS);
        assertWithMessage(errorMessage).that(conditionState).isTrue();
    }

    public void sendOtp() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("cmd hdmi_control onetouchplay");
    }

    public String setPowerControlMode(String valToSet) throws Exception {
        return setSettingsValue(POWER_CONTROL_MODE, valToSet);
    }

    public String setVolumeControlMode(String valToSet) throws Exception {
        return setSettingsValue(SETTING_VOLUME_CONTROL_ENABLED, valToSet);
    }

    public String setPowerStateChangeOnActiveSourceLost(String valToSet) throws Exception {
        return setSettingsValue(POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST, valToSet);
    }

    public String setSoundbarMode(String valToSet) throws Exception {
        return setSettingsValue(SOUNDBAR_MODE, valToSet);
    }

    public String getSoundbarMode() throws Exception {
        return getSettingsValue(SOUNDBAR_MODE);
    }

    public boolean isDeviceActiveSource(ITestDevice device) throws DumpsysParseException {
        final String activeSource = "activeSource";
        final String pattern =
                "(.*?)"
                        + "(isActiveSource\\(\\): )"
                        + "(?<"
                        + activeSource
                        + ">\\btrue\\b|\\bfalse\\b)"
                        + "(.*?)";
        try {
            Pattern p = Pattern.compile(pattern);
            String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
            BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = p.matcher(line);
                if (matcher.matches()) {
                    return matcher.group(activeSource).equals("true");
                }
            }
        } catch (IOException | DeviceNotAvailableException e) {
            throw new DumpsysParseException("Could not fetch 'dumpsys hdmi_control' output.", e);
        }
        throw new DumpsysParseException("Could not parse isActiveSource() from dumpsys.");
    }

    /**
     * For source devices, simulate that a sink is connected by responding to the
     * {@code Give Power Status} message that is sent when re-enabling CEC.
     * Validate that HdmiControlService#mIsCecAvailable is set to true as a result.
     */
    public void simulateCecSinkConnected(ITestDevice device, LogicalAddress source)
            throws Exception {
        hdmiCecClient.clearClientOutput();
        device.executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_enabled 0");
        waitForCondition(() -> !isCecAvailable(device), "Could not disable CEC");
        device.executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_enabled 1");
        // When a CEC device has just become available, the CEC adapter isn't able to send it
        // messages right away. Therefore we let the first <Give Power Status> message time-out, and
        // only respond to the retry.
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.GIVE_POWER_STATUS);
        hdmiCecClient.clearClientOutput();
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.GIVE_POWER_STATUS);
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, source, CecOperand.REPORT_POWER_STATUS,
                CecMessage.formatParams(HdmiCecConstants.CEC_POWER_STATUS_STANDBY));
        waitForCondition(() -> isCecAvailable(device),
                "Simulating that a sink is connected, failed.");
    }

    boolean isCecAvailable(ITestDevice device) throws Exception {
        return device.executeShellCommand("dumpsys hdmi_control | grep mIsCecAvailable:")
                .replace("mIsCecAvailable:", "").trim().equals("true");
    }

    boolean isCecEnabled(ITestDevice device) throws Exception {
        return device.executeShellCommand("dumpsys hdmi_control | grep hdmi_cec_enabled")
                .trim().startsWith("hdmi_cec_enabled (int): 1");
    }

    /**
     * Checks whether an audio output device is in the list output by an ADB shell command.
     * Intended for checking a device's volume behavior by looking at whether it appears in
     * a certain line of the audio dumpsys.
     */
    private boolean isAudioOutputDeviceInList(int audioOutputDevice,
            String deviceListAdbShellCommand) throws Exception {
        String[] splitLine = getDevice().executeShellCommand(deviceListAdbShellCommand).split("=");
        if (splitLine.length < 2) {
            // No devices are in the list
            return false;
        }
        String[] deviceStrings = splitLine[1].trim().split(",");
        for (String deviceString : deviceStrings) {
            try {
                if (Integer.decode(deviceString) == audioOutputDevice) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Ignore this device and continue
            }
        }
        return false;
    }

    /**
     * Returns whether a given audio output device is currently using full volume behavior
     */
    public boolean isFullVolumeDevice(int audioOutputDevice) throws Exception {
        return isAudioOutputDeviceInList(audioOutputDevice,
                "dumpsys audio | grep mFullVolumeDevices");
    }

    /**
     * Returns whether a given audio output device is currently using absolute volume behavior
     */
    public boolean isAbsoluteVolumeDevice(int audioOutputDevice) throws Exception {
        // Need to explicitly exclude the line for adjust-only absolute volume devices
        return isAudioOutputDeviceInList(audioOutputDevice,
                "dumpsys audio | grep 'absolute volume devices' | grep -v 'adjust-only'");
    }

    /**
     * Returns whether a given audio output device is currently using adjust-only absolute volume
     * behavior
     */
    public boolean isAdjustOnlyAbsoluteVolumeDevice(int audioOutputDevice) throws Exception {
        return isAudioOutputDeviceInList(audioOutputDevice,
                "dumpsys audio | grep 'adjust-only absolute volume devices'");
    }

    /**
     * On Google TV devices the only stream played is STREAM_MUSIC.
     * The method returns whether "Devices" in "STREAM_MUSIC" contains "hdmi" in audio dumpsys.
     * This is required by tests where the DUT has to redirect volume key events as CEC
     * <User Control Pressed> messages.
     * This method might return false, because the set-up contains an HDMI Stub.
     * See {@link android.media.AudioSystem#STREAM_MUSIC} and
     * {@link android.media.AudioSystem#DEVICE_OUT_HDMI}.
     */
    public boolean isPlayingStreamMusicOnHdmiOut() throws DeviceNotAvailableException {
        return getDevice().executeShellCommand("dumpsys audio | sed -n '/^- STREAM_MUSIC:/,/^$/p'"
                + " | grep \"Devices\"").contains("hdmi");
    }
}
