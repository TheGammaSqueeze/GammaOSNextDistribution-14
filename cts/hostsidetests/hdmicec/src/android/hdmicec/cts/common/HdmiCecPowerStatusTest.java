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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.WakeLockHelper;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HDMI CEC tests verifying power status related messages of the device (CEC 2.0 CTS Section 7.6)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPowerStatusTest extends BaseHdmiCecCtsTest {

    private static final int ON = 0x0;
    private static final int OFF = 0x1;
    private static final int IN_TRANSITION_TO_STANDBY = 0x3;

    private static final int SLEEP_TIMESTEP_SECONDS = 1;

    @Rule
    public RuleChain mRuleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    /**
     * Test HF4-6-20
     *
     * Verifies that {@code <Report Power Status>} message is broadcast when the device transitions
     * from standby to on.
     */
    @Test
    public void cect_hf4_6_20_broadcastsWhenTurningOn() throws Exception {
        ITestDevice device = getDevice();
        setCec20();

        // Move device to standby
        sendDeviceToSleep();

        // Turn device on
        wakeUpDeviceWithoutWait();

        String reportPowerStatus = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                CecOperand.REPORT_POWER_STATUS);

        if (CecMessage.getParams(reportPowerStatus) == HdmiCecConstants.CEC_POWER_STATUS_STANDBY) {
            // Received the "turning off" broadcast, check for the next broadcast message
            reportPowerStatus = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                    CecOperand.REPORT_POWER_STATUS);
        }

        assertThat(CecMessage.getParams(reportPowerStatus)).isEqualTo(
                HdmiCecConstants.CEC_POWER_STATUS_ON);
    }

    /**
     * Test HF4-6-21
     *
     * Verifies that {@code <Report Power Status>} message is broadcast when the device transitions
     * from on to standby.
     */
    @Test
    public void cect_hf4_6_21_broadcastsWhenGoingToStandby() throws Exception {
        ITestDevice device = getDevice();
        setCec20();

        try {
            // Turn device on
            wakeUpDevice();

            // Move device to standby
            sendDeviceToSleepWithoutWait();

            String reportPowerStatus =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.BROADCAST, CecOperand.REPORT_POWER_STATUS);

            if (CecMessage.getParams(reportPowerStatus) == HdmiCecConstants.CEC_POWER_STATUS_ON) {
                // Received the "wake up" broadcast, check for the next broadcast message
                reportPowerStatus =
                        hdmiCecClient.checkExpectedOutput(
                                LogicalAddress.BROADCAST, CecOperand.REPORT_POWER_STATUS);
            }

            assertThat(CecMessage.getParams(reportPowerStatus))
                    .isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test HF4-6-22 (CEC 2.0)
     *
     * <p>Verifies that the DUT notifies the transition back to Standby state if an ongoing
     * transition from standby state to power on state is interrupted.
     */
    @Test
    public void cect_hf4_6_22_interruptedPowerOn() throws Exception {
        ITestDevice device = getDevice();
        setCec20();

        try {
            // Turn device off
            sendDeviceToSleep();

            device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
            TimeUnit.MILLISECONDS.sleep(200);
            device.executeShellCommand("input keyevent KEYCODE_SLEEP");

            String reportPowerStatus =
                    hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);

            switch (CecMessage.getParams(reportPowerStatus)) {
                case HdmiCecConstants.CEC_POWER_STATUS_STANDBY:
                    // No further messages are expected, check for 5s outside the switch case.
                    break;
                case HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_STANDBY:
                    reportPowerStatus =
                            hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);
                    assertThat(CecMessage.getParams(reportPowerStatus))
                            .isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
                    break;
                case HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_ON:
                case HdmiCecConstants.CEC_POWER_STATUS_ON:
                    reportPowerStatus =
                            hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);
                    int powerState = CecMessage.getParams(reportPowerStatus);
                    if (powerState == HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_STANDBY) {
                        // If it is in transition, wait for another <Report Power Status>[Power Off]
                        reportPowerStatus =
                                hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);
                        powerState = CecMessage.getParams(reportPowerStatus);
                    }
                    // If no <Report Power Status>[Power Off] is received, fail the test
                    assertThat(powerState).isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
                    break;
            }
            // Make sure there are no further <Report Power Status> for 5s
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.BROADCAST, CecOperand.REPORT_POWER_STATUS, 5000);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test HF4-6-23 (CEC 2.0)
     *
     * <p>Verifies that the DUT notifies the transition back to On state if an ongoing transition
     * from On state to Standby state is interrupted.
     */
    @Test
    public void cect_hf4_6_23_interruptedStandby() throws Exception {
        ITestDevice device = getDevice();
        setCec20();

        try {
            // Turn device off
            wakeUpDevice();
            WakeLockHelper.acquirePartialWakeLock(getDevice());

            device.executeShellCommand("input keyevent KEYCODE_SLEEP");
            TimeUnit.MILLISECONDS.sleep(200);
            device.executeShellCommand("input keyevent KEYCODE_WAKEUP");

            String reportPowerStatus =
                    hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);

            switch (CecMessage.getParams(reportPowerStatus)) {
                case HdmiCecConstants.CEC_POWER_STATUS_ON:
                    // No further messages are expected, check for 5s outside the switch case.
                    break;
                case HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_ON:
                    reportPowerStatus =
                            hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);
                    assertThat(CecMessage.getParams(reportPowerStatus))
                            .isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_ON);
                    break;
                case HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_STANDBY:
                case HdmiCecConstants.CEC_POWER_STATUS_STANDBY:
                    reportPowerStatus =
                            hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);
                    int powerState = CecMessage.getParams(reportPowerStatus);
                    if (powerState == HdmiCecConstants.CEC_POWER_STATUS_IN_TRANSITION_TO_ON) {
                        // If it is in transition, wait for another <Report Power Status>[Power On]
                        reportPowerStatus =
                                hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_POWER_STATUS);
                        powerState = CecMessage.getParams(reportPowerStatus);
                    }
                    // If no <Report Power Status>[Power On] is received, fail the test
                    assertThat(powerState).isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_ON);
                    break;
            }
            // Make sure there are no further <Report Power Status> for 5s
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.BROADCAST, CecOperand.REPORT_POWER_STATUS, 5000);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test 11.1.14-1, 11.2.14-1
     *
     * <p>Tests that the device sends a {@code <REPORT_POWER_STATUS>} with params 0x0 when the
     * device is powered on.
     */
    @Test
    public void cect_PowerStatusWhenOn() throws Exception {
        ITestDevice device = getDevice();
        /* Make sure the device is not booting up/in standby */
        device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        wakeUpDevice();

        LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
        hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
        String message =
                hdmiCecClient.checkExpectedOutput(cecClientDevice, CecOperand.REPORT_POWER_STATUS);
        assertThat(CecMessage.getParams(message)).isEqualTo(ON);
    }

    /**
     * Test 11.2.14-1, 11.2.14-2
     *
     * <p>Tests that the device sends a {@code <REPORT_POWER_STATUS>} with params 0x1 when the
     * device is powered off.
     */
    @Test
    public void cect_PowerStatusWhenOff() throws Exception {
        ITestDevice device = getDevice();
        try {
            /* Make sure the device is not booting up/in standby */
            device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
            /* The sleep below could send some devices into a deep suspend state. */
            sendDeviceToSleep();
            int waitTimeSeconds = HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS;
            int powerStatus;
            LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
            do {
                TimeUnit.SECONDS.sleep(SLEEP_TIMESTEP_SECONDS);
                waitTimeSeconds += SLEEP_TIMESTEP_SECONDS;
                hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
                powerStatus =
                        CecMessage.getParams(
                                hdmiCecClient.checkExpectedOutput(
                                        cecClientDevice, CecOperand.REPORT_POWER_STATUS));
            } while (powerStatus == IN_TRANSITION_TO_STANDBY &&
                    waitTimeSeconds <= HdmiCecConstants.MAX_SLEEP_TIME_SECONDS);
            assertThat(powerStatus).isEqualTo(OFF);
        } finally {
            /* Wake up the device */
            wakeUpDevice();
        }
    }

    /**
     * Test HF4-6-8
     *
     * Tests that a device comes out of the standby state when it receives a {@code <User Control
     * Pressed>} message with power related operands.
     */
    @Test
    public void cect_hf4_6_8_userControlPressed_powerOn() throws Exception {
        ITestDevice device = getDevice();
        List<Integer> powerControlOperands = Arrays.asList(HdmiCecConstants.CEC_KEYCODE_POWER,
                HdmiCecConstants.CEC_KEYCODE_POWER_ON_FUNCTION,
                HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION);

        LogicalAddress source = hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV)
                ? LogicalAddress.PLAYBACK_1
                : LogicalAddress.TV;

        for (Integer operand : powerControlOperands) {
            try {
                sendDeviceToSleep();
                hdmiCecClient.sendUserControlPressAndRelease(source, operand, false);
                TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
                assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_AWAKE);
            } finally {
                wakeUpDevice();
            }
        }
    }

    /**
     * Test HF4-6-10
     *
     * Tests that a device comes enters the standby state when it receives a {@code <User Control
     * Pressed>} message with power related operands.
     */
    @Test
    public void cect_hf4_6_10_userControlPressed_powerOff() throws Exception {
        ITestDevice device = getDevice();
        List<Integer> powerControlOperands = Arrays.asList(
                HdmiCecConstants.CEC_KEYCODE_POWER_OFF_FUNCTION,
                HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION);

        LogicalAddress source = hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV)
                ? LogicalAddress.PLAYBACK_1
                : LogicalAddress.TV;

        for (Integer operand : powerControlOperands) {
            try {
                wakeUpDevice();
                WakeLockHelper.acquirePartialWakeLock(device);
                hdmiCecClient.sendUserControlPressAndRelease(source, operand, false);
                TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
                assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_ASLEEP);
            } finally {
                wakeUpDevice();
            }
        }
    }

    /**
     * Test HF4-6-26
     *
     * <p> Verify that, when a Source device is put to Standby by the user, it does not broadcast a
     * system {@code <Standby>} message unless explicitly requested by the user.
     */
    @Test
    public void cect_hf4_6_26_standby_noBroadcast_20() throws Exception {
        ITestDevice device = getDevice();
        setCec20();
        String previousPowerControlMode =
                setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_NONE);
        try {
            sendDeviceToSleep();
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.BROADCAST, CecOperand.STANDBY);
        } finally {
            wakeUpDevice();
            setPowerControlMode(previousPowerControlMode);
        }
    }

    /**
     * Test HF4-6-27 (CEC 2.0)
     *
     * <p>Verify that action of a {@code <Standby>} message can only be used to send a device to
     * the standby state.
     */
    @Test
    public void cect_hf4_6_27_standby_action_20() throws Exception {
        ITestDevice device = getDevice();
        /* Make sure the device is not booting up/in standby */
        device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        setCec20();
        try {
            // Directly addressed standby message to DUT.
            sendDeviceToSleepAndValidateUsingStandbyMessage(true);
            // Resending Standby message and validate device stays in standby mode
            sendDeviceToSleepAndValidateUsingStandbyMessage(true);
            wakeUpDevice();

            // Broadcast standby message.
            sendDeviceToSleepAndValidateUsingStandbyMessage(false);
            // Resending Standby message and validate device stays in standby mode
            sendDeviceToSleepAndValidateUsingStandbyMessage(false);
        } finally {
            wakeUpDevice();
        }
    }

    /*
     * Test HF4-6-28
     *
     * <p>Tests that the DUT handles {@code <User Control Pressed>} of any power related buttons
     * correctly
     */
    @Test
    public void cect_hf_4_6_28_testPowerUcpHandling() throws Exception {
        setCec20();
        int waitSeconds = 10;
        ITestDevice device = getDevice();
        // Ensure device is awake.
        wakeUpDevice();

        // Acquire the wakelock.
        WakeLockHelper.acquirePartialWakeLock(device);
        try {
            // All <UCP> commands will be sent from cec client device.
            LogicalAddress source = hdmiCecClient.getSelfDevice();
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);

            // Toggle power again, DUT should wakeup.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);

            // Send <UCP>[Power On]. DUT should remain in ON state, check for 10s.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER_ON_FUNCTION, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);
            TimeUnit.SECONDS.sleep(waitSeconds);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);

            // Send <UCP>[Power Off]. DUT should got to OFF state, check for 10s.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER_OFF_FUNCTION, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
            TimeUnit.SECONDS.sleep(waitSeconds);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);

            // Send <UCP>[Power Off] again. DUT should stay in OFF state, check for 10s.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER_OFF_FUNCTION, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
            TimeUnit.SECONDS.sleep(waitSeconds);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);

            // Send <UCP>[Power On]. DUT should go to ON state, check for 10s.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER_ON_FUNCTION, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);
            TimeUnit.SECONDS.sleep(waitSeconds);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);

            // Send <UCP> [Power]. DUT should remain in ON state.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);

            sendDeviceToSleep();

            // Send <UCP> [Power]. DUT should wakeup.
            hdmiCecClient.sendUserControlPressAndRelease(
                    source, HdmiCecConstants.CEC_KEYCODE_POWER, false);
            waitForTransitionTo(HdmiCecConstants.CEC_POWER_STATUS_ON);
        } finally {
            // Wake up the device. This will also release the wakelock.
            wakeUpDevice();
        }
    }
}
