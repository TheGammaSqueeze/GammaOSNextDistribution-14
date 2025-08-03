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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.car.feature.Flags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.car.power.CarPowerDumpProto;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.StreamUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarPowerHostTest extends CarHostJUnit4TestCase {
    private static final long TIMEOUT_MS = 5_000;
    private static final int SUSPEND_SEC = 3;
    private static final long WAIT_FOR_SUSPEND_MS = SUSPEND_SEC * 1000 + 2000;
    private static final String POWER_ON = "ON";
    private static final String POWER_STATE_PATTERN =
            "mCurrentState:.*CpmsState=([A-Z_]+)\\(\\d+\\)";
    private static final String CMD_DUMPSYS_POWER =
            "dumpsys car_service --services CarPowerManagementService";
    private static final String CMD_DUMPSYS_POWER_PROTO =
            "dumpsys car_service --services CarPowerManagementService --proto";
    private static final String ANDROID_CLIENT_SERVICE = "android.car.cts.app/.CarPowerTestService";
    private static final String TEST_COMMAND_HEADER =
            "am start-foreground-service -n " + ANDROID_CLIENT_SERVICE + " --es power ";

    // messages to wait to see printed out in logcat; having some issues reliably receiving these
    // in suspend tests, so unused for now, but may become useful for to-do below
    private static final String LISTENER_WOC_SET_MSG = "Listener without completion set";
    private static final String LISTENER_WC_SET_MSG = "Listener with completion set";
    private static final String S2R_WAKEUP_MSG = "Exit Deep Sleep simulation";
    private static final String S2D_WAKEUP_MSG = "Exit Hibernation simulation";
    private boolean mUseProtoDump;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        // Clear the previous logs
        executeCommand("logcat -c");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testPowerStateOnAfterBootUp_protoDump() throws Exception {
        setUseProtoDump(true);
        rebootDevice();

        PollingCheck.check("Power state is not ON", TIMEOUT_MS,
                () -> getPowerState().equals(POWER_ON));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testPowerStateOnAfterBootUp_textDump() throws Exception {
        setUseProtoDump(false);
        rebootDevice();

        PollingCheck.check("Power state is not ON", TIMEOUT_MS,
                () -> getPowerState().equals(POWER_ON));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithoutCompletion_suspendToRam_protoDump() throws Exception {
        setUseProtoDump(true);
        testSetListenerInternal(/* listenerName= */ "listener-s2r", /* suspendType= */ "s2r",
                /* completionType= */ "without-completion",
                /* isSuspendAvailable= */ () -> isSuspendToRamAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToRam();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithoutCompletion_suspendToRam_textDump() throws Exception {
        setUseProtoDump(false);
        testSetListenerInternal(/* listenerName= */ "listener-s2r", /* suspendType= */ "s2r",
                /* completionType= */ "without-completion",
                /* isSuspendAvailable= */ () -> isSuspendToRamAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToRam();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithoutCompletion_suspendToDisk_protoDump() throws Exception {
        setUseProtoDump(true);
        testSetListenerInternal(/* listenerName= */ "listener-s2d", /* suspendType= */ "s2d",
                /* completionType= */ "without-completion",
                /* isSuspendAvailable= */ () -> isSuspendToDiskAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToDisk();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithoutCompletion_suspendToDisk_textDump() throws Exception {
        setUseProtoDump(false);
        testSetListenerInternal(/* listenerName= */ "listener-s2d", /* suspendType= */ "s2d",
                /* completionType= */ "without-completion",
                /* isSuspendAvailable= */ () -> isSuspendToDiskAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToDisk();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithCompletion_suspendToRam_protoDump() throws Exception {
        setUseProtoDump(true);
        testSetListenerInternal(/* listenerName= */ "listener-wc-s2r", /* suspendType= */ "s2r",
                /* completionType= */ "with-completion",
                /* isSuspendAvailable= */ () -> isSuspendToRamAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToRam();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithCompletion_suspendToRam_textDump() throws Exception {
        setUseProtoDump(false);
        testSetListenerInternal(/* listenerName= */ "listener-wc-s2r", /* suspendType= */ "s2r",
                /* completionType= */ "with-completion",
                /* isSuspendAvailable= */ () -> isSuspendToRamAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToRam();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithCompletion_suspendToDisk_protoDump() throws Exception {
        setUseProtoDump(true);
        testSetListenerInternal(/* listenerName= */ "listener-wc-s2d", /* suspendType= */ "s2d",
                /* completionType= */ "with-completion",
                /* isSuspendAvailable= */ () -> isSuspendToDiskAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToDisk();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetListenerWithCompletion_suspendToDisk_textDump() throws Exception {
        setUseProtoDump(false);
        testSetListenerInternal(/* listenerName= */ "listener-wc-s2d", /* suspendType= */ "s2d",
                /* completionType= */ "with-completion",
                /* isSuspendAvailable= */ () -> isSuspendToDiskAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToDisk();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void setUseProtoDump(boolean useProtoDump) {
        mUseProtoDump = useProtoDump;
    }

    private void rebootDevice() throws Exception {
        executeCommand("svc power reboot");
        getDevice().waitForDeviceAvailable();
    }

    @SuppressWarnings("LiteProtoToString")
    private String getPowerState() throws Exception {
        if (mUseProtoDump) {
            CarPowerDumpProto carPowerDump = ProtoUtils.getProto(getDevice(),
                    CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER_PROTO);
            boolean hasPowerState = carPowerDump.getCurrentState().hasStateName();
            if (hasPowerState) {
                return carPowerDump.getCurrentState().getStateName();
            }
            throw new IllegalStateException(
                    "Proto doesn't have current_state.state_name field\n proto:" + carPowerDump);
        } else {
            Pattern pattern = Pattern.compile(POWER_STATE_PATTERN);
            String cpmsDump =
                    executeCommand("dumpsys car_service --services CarPowerManagementService");
            String[] lines = cpmsDump.split("\\r?\\n");

            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            throw new IllegalStateException("Power state is not found:\n" + cpmsDump);
        }
    }

    private void testSetListenerInternal(String listenerName, String suspendType,
            String completionType, Callable<Boolean> isSuspendAvailable, Runnable suspendDevice)
            throws Exception {
        assumeTrue(isSuspendAvailable.call());
        clearListener();
        setPowerStateListener(listenerName, completionType, suspendType);
        suspendDevice.run();
        // TODO(b/300515548): Replace sleep with a check that device has resumed from suspend
        sleep(WAIT_FOR_SUSPEND_MS);

        boolean statesMatchExpected =
                listenerStatesMatchExpected(listenerName, completionType, suspendType);

        assertWithMessage("Listener " + completionType + " power states match expected after "
                + suspendType).that(statesMatchExpected).isTrue();
    }

    private void waitForLogcatMsg(String logcatMsg) throws Exception {
        PollingCheck.check("Wait for logcat message '" + logcatMsg + "' timed out",
                TIMEOUT_MS, () -> {
                try (InputStreamSource logcatOutput = getDevice().getLogcat()) {
                    String logcat = StreamUtil.getStringFromSource(logcatOutput);
                    CLog.d("Found msg: " + logcatMsg + " - " + logcat.contains(logcatMsg));
                    return logcat.contains(logcatMsg);
                }
            });
    }

    private boolean isSuspendSupported(String suspendType) throws Exception {
        String suspendDumpHeader;
        if (suspendType.equals("S2R")) {
            suspendDumpHeader = "kernel support S2R: ";
        } else if (suspendType.equals("S2D")) {
            suspendDumpHeader = "kernel support S2D: ";
        } else {
            throw new IllegalArgumentException(
                    "Suspend type %s" + suspendType + " is not supported");
        }
        String cpmsDump = executeCommand(CMD_DUMPSYS_POWER);
        String[] lines = cpmsDump.split("\n");
        for (String line : lines) {
            if (line.contains(suspendDumpHeader)) {
                return line.split(":")[1].trim().equals("true");
            }
        }
        return false;
    }

    private boolean isSuspendToRamAvailable() throws Exception {
        if (mUseProtoDump) {
            CarPowerDumpProto carPowerDump = ProtoUtils.getProto(getDevice(),
                    CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER_PROTO);
            return carPowerDump.getKernelSupportsDeepSleep();
        } else {
            return isSuspendSupported("S2R");
        }
    }

    private boolean isSuspendToDiskAvailable() throws Exception {
        if (mUseProtoDump) {
            CarPowerDumpProto carPowerDump = ProtoUtils.getProto(getDevice(),
                    CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER_PROTO);
            return carPowerDump.getKernelSupportsHibernation();
        } else {
            return isSuspendSupported("S2D");
        }
    }

    private void setPowerStateListener(String listenerName, String completionType,
            String suspendType) throws Exception {
        executeCommand("%s set-listener,%s,%s,%s", TEST_COMMAND_HEADER, listenerName,
                completionType, suspendType);
        waitForLogcatMsg("Listener set for " + listenerName);
    }

    private void clearListener() throws Exception {
        executeCommand("%s clear-listener", TEST_COMMAND_HEADER);
        waitForLogcatMsg("Listener cleared");
    }

    private boolean listenerStatesMatchExpected(String listenerName, String completionType,
            String suspendType) throws Exception {
        executeCommand("%s get-listener-states-results,%s,%s,%s", TEST_COMMAND_HEADER,
                listenerName, completionType, suspendType);
        String dump = fetchServiceDumpsys();
        CLog.d("Service dump: " + dump);
        return dump.contains("true");
    }

    private void suspendDeviceToRam() throws Exception {
        executeCommand("cmd car_service suspend --simulate --skip-garagemode --wakeup-after "
                + SUSPEND_SEC);
    }

    private void suspendDeviceToDisk() throws Exception {
        executeCommand("cmd car_service hibernate --simulate --skip-garagemode --wakeup-after "
                + SUSPEND_SEC);
    }

    public String fetchServiceDumpsys() throws Exception {
        return executeCommand("dumpsys activity service %s", ANDROID_CLIENT_SERVICE);
    }
}
