/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.car.cts.app.PowerPolicyTestCommandStatus;
import android.car.cts.app.PowerPolicyTestCommandType;
import android.car.cts.powerpolicy.CpmsFrameworkLayerStateInfo;
import android.car.cts.powerpolicy.CpmsSystemLayerStateInfo;
import android.car.cts.powerpolicy.LocationInfo;
import android.car.cts.powerpolicy.PowerPolicyConstants;
import android.car.cts.powerpolicy.PowerPolicyDef;
import android.car.cts.powerpolicy.PowerPolicyGroups;
import android.car.cts.powerpolicy.PowerPolicyTestAnalyzer;
import android.car.cts.powerpolicy.PowerPolicyTestHelper;
import android.car.cts.powerpolicy.PowerPolicyTestResult;
import android.car.cts.powerpolicy.SilentModeInfo;
import android.car.cts.powerpolicy.SystemInfoParser;
import android.car.feature.Flags;
import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.car.power.CarPowerDumpProto;
import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class PowerPolicyHostTest extends CarHostJUnit4TestCase {
    private static final int DEFAULT_TIMEOUT_SEC = 20;
    private static final int BOOT_TIMEOUT_SEC = 60;
    private static final String ANDROID_CLIENT_PKG = "android.car.cts.app";
    private static final String ANDROID_CLIENT_ACTIVITY = ANDROID_CLIENT_PKG
            + "/.PowerPolicyTestActivity";
    private static final String TEST_COMMAND_HEADER =
            "am start -n " + ANDROID_CLIENT_ACTIVITY + " --es powerpolicy ";
    private static final String LEAVE_FORCED_SILENT_MODE_CMD =
            "cmd car_service silent-mode forced-non-silent";

    private static final String ENTER_NON_FORCED_SILENT_MODE_CMD =
            "cmd car_service silent-mode non-forced-silent-mode";
    private static final int[] DEFAULT_STATE_MACHINE_AT_ON_VHAL_REQS = {
            PowerPolicyConstants.VhalPowerStateReq.ON,
            PowerPolicyConstants.VhalPowerStateReq.CANCEL_SHUTDOWN,
            PowerPolicyConstants.VhalPowerStateReq.FINISHED
    };
    private static final String[] DEFAULT_STATE_MACHINE_AT_ON_STEP_NAMES = {
            "trigger VHAL ON event",
            "trigger VHAL CANCEL_SHUTDOWN",
            "trigger VHAL FINISHED"
    };

    private final PowerPolicyTestAnalyzer mTestAnalyzer = new PowerPolicyTestAnalyzer(this);
    private boolean mUseProtoDump;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void checkPrecondition() throws Exception {
        waitForOnState();
        PowerPolicyTestHelper testHelper = new PowerPolicyTestHelper(
                /* testcase= */ "pre-condition", /* step= */ "testStep1",
                /* frameCpms= */ getCpmsFrameworkLayerStateInfo(),
                /* sysCpms= */ getCpmsSystemLayerStateInfo(), /* silentMode= */ null);
        checkDefaultPowerPolicySet(testHelper);
    }

    @After
    public void restoreInitCondition() throws Exception {
        applyPowerPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testPowerPolicySilentMode_textDump() throws Exception {
        setUseProtoDump(false);
        testPowerPolicySilentMode();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testPowerPolicySilentMode_protoDump() throws Exception {
        setUseProtoDump(true);
        testPowerPolicySilentMode();
    }

    /**
     * Tests the error conditions for CPMS at the ON state.
     *
     * <p>All other VHAL events but {@code SHUTDOWN_PREPARE} shall not have any impact
     * to CPMS power state. Text dump is used to get device state.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testDefaultStateMachineAtONState_textDump() throws Exception {
        setUseProtoDump(false);
        testDefaultStateMachineAtONState();
    }

    /**
     * Tests the error conditions for CPMS at the ON state.
     *
     * <p>All other VHAL events but {@code SHUTDOWN_PREPARE} shall not have any impact
     * to CPMS power state. Proto dump is used to get device state.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testDefaultStateMachineAtONState_protoDump() throws Exception {
        setUseProtoDump(true);
        testDefaultStateMachineAtONState();
    }

    @FlakyTest(bugId = 327307932)
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testPowerPolicyChange_textDump() throws Exception {
        setUseProtoDump(false);
        testPowerPolicyChange();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testPowerPolicyChange_protoDump() throws Exception {
        setUseProtoDump(true);
        testPowerPolicyChange();
    }

    public String fetchActivityDumpsys() throws Exception {
        return executeCommand("dumpsys activity %s", ANDROID_CLIENT_ACTIVITY);
    }

    private void waitForOnState() throws Exception {
        CommonTestUtils.waitUntil("timed out (" + BOOT_TIMEOUT_SEC
                        + "s) waiting for ON state", BOOT_TIMEOUT_SEC,
                () -> {
                    CpmsFrameworkLayerStateInfo frameworkLayerStateInfo =
                            getCpmsFrameworkLayerStateInfo();
                    return frameworkLayerStateInfo != null
                            && frameworkLayerStateInfo.getCurrentState()
                            == PowerPolicyConstants.CarPowerState.ON;
                });
    }

    private void setUseProtoDump(boolean useProtoDump) {
        mUseProtoDump = useProtoDump;
    }

    private PowerPolicyTestHelper getTestHelper(String testcase, int stepNo, String stepName)
            throws Exception {
        CpmsFrameworkLayerStateInfo cpmsFrameworkInfo = getCpmsFrameworkLayerStateInfo();
        CpmsSystemLayerStateInfo cpmsSystemInfo = getCpmsSystemLayerStateInfo();
        String normalizedStepName = String.format("%d. %s", stepNo, stepName);
        return new PowerPolicyTestHelper(testcase, normalizedStepName, cpmsFrameworkInfo,
                cpmsSystemInfo, getSilentModeInfo());
    }

    private void triggerVhalPowerStateReq(int reqNo, int param) throws Exception {
        executeCommand("cmd car_service inject-vhal-event %d %d,%d",
                PowerPolicyConstants.VHAL_POWER_STATE_REQ_PROPERTY_ID, reqNo, param);
    }

    private SilentModeInfo getSilentModeInfo() throws Exception {
        return executeAndParseCommand(
                new SystemInfoParser<SilentModeInfo>(SilentModeInfo.class),
                SilentModeInfo.COMMAND);
    }

    private void checkSilentModeSupported() throws Exception {
        SilentModeInfo smInfo = getSilentModeInfo();
        Assume.assumeTrue("HW does not support silent mode. Skip the test",
                smInfo.getMonitoringHWStateSignal());
    }

    private CpmsFrameworkLayerStateInfo getCpmsFrameworkLayerStateInfo()
            throws Exception {
        if (mUseProtoDump) {
            CarPowerDumpProto proto = ProtoUtils.getProto(getDevice(), CarPowerDumpProto.parser(),
                    CpmsFrameworkLayerStateInfo.COMMAND_PROTO);
            return CpmsFrameworkLayerStateInfo.parseProto(proto);
        } else {
            return executeAndParseCommand(new SystemInfoParser<CpmsFrameworkLayerStateInfo>(
                    CpmsFrameworkLayerStateInfo.class), CpmsFrameworkLayerStateInfo.COMMAND);
        }
    }

    private CpmsSystemLayerStateInfo getCpmsSystemLayerStateInfo() throws Exception {
        return executeAndParseCommand(new SystemInfoParser<CpmsSystemLayerStateInfo>(
                CpmsSystemLayerStateInfo.class), CpmsSystemLayerStateInfo.COMMAND);
    }

    private void rebootDevice() throws Exception {
        executeCommand("svc power reboot");
        waitForDeviceAvailable();
    }

    private void enterForcedSilentMode() throws Exception {
        executeCommand("cmd car_service silent-mode forced-silent");
        waitUntilForcedSilentModeChangeTo(true);
    }

    private void checkForcedSilentMode(PowerPolicyTestHelper testHelper) throws Exception {
        testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.NO_USER_INTERACTION);
        testHelper.checkSilentModeStatus(true);
        testHelper.checkSilentModeFull(SilentModeInfo.FORCED_SILENT);
        testHelper.checkCurrentPowerComponents(PowerPolicyDef.PolicySet.NO_USER_INTERACT);
    }

    private void leaveForcedSilentMode() throws Exception {
        executeCommand(LEAVE_FORCED_SILENT_MODE_CMD);
        executeCommand(ENTER_NON_FORCED_SILENT_MODE_CMD);
        waitUntilForcedSilentModeChangeTo(false);
    }

    private void checkLeaveForcedSilentMode(PowerPolicyTestHelper testHelper) throws Exception {
        testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
        testHelper.checkSilentModeStatus(false);
        testHelper.checkSilentModeFull(SilentModeInfo.NO_SILENT);
        testHelper.checkCurrentPowerComponents(PowerPolicyDef.PolicySet.DEFAULT_ALL_ON);
    }

    private void definePowerPolicy(String policyStr) throws Exception {
        CLog.d("definePowerPolicy: %s", policyStr);
        executeCommand("cmd car_service define-power-policy %s", policyStr);
    }

    private void applyPowerPolicy(String policyId) throws Exception {
        executeCommand("cmd car_service apply-power-policy %s", policyId);
    }

    private void definePowerPolicyGroup(String policyGroupStr) throws Exception {
        executeCommand("cmd car_service define-power-policy-group %s", policyGroupStr);
    }

    private void setPowerPolicyGroup(String policyGroupId) throws Exception {
        executeCommand("cmd car_service set-power-policy-group %s", policyGroupId);
    }

    private void setClientTestcase(String testcase, int userId) throws Exception {
        executeCommand("%s settest,%s --user %d", TEST_COMMAND_HEADER, testcase, userId);
    }

    private void clearClientTestcase(int userId) throws Exception {
        executeCommand("%s cleartest --user %d", TEST_COMMAND_HEADER, userId);
    }

    private void registerPowerPolicyListener(String componentName, int userId) throws Exception {
        executeCommand("%s addlistener,%s --user %d", TEST_COMMAND_HEADER, componentName, userId);
    }

    private void unregisterPowerPolicyListener(String componentName, int userId) throws Exception {
        executeCommand("%s removelistener,%s --user %d",
                TEST_COMMAND_HEADER, componentName, userId);
    }

    private void dumpPowerPolicyListener(String componentName, int userId) throws Exception {
        executeCommand("%s dumplistener,%s --user %d", TEST_COMMAND_HEADER, componentName, userId);
    }

    private void waitPowerPolicyListenersUpdated(PowerPolicyTestResult testResult,
            String clientTestcase, String clientAction, String component, int userId)
            throws Exception {
        CommonTestUtils.waitUntil("timed out (" + DEFAULT_TIMEOUT_SEC
                + "s) waiting  policy listeners updated", DEFAULT_TIMEOUT_SEC,
                () -> {
                    return checkPowerPolicyListenersUpdated(testResult, clientTestcase,
                            clientAction, component, userId);
                });
    }

    private boolean checkPowerPolicyListenersUpdated(PowerPolicyTestResult testResult,
            String clientTestcase, String clientAction, String component, int userId)
            throws Exception {
        executeCommand("%s checklisteners --user %d", TEST_COMMAND_HEADER, userId);
        return testResult.checkLastTestResultEntryData(clientTestcase, clientAction,
                component, PowerPolicyTestCommandStatus.PROPAGATED);
    }

    private void resetPowerPolicyListeners(int userId) throws Exception {
        executeCommand("%s resetlisteners --user %d", TEST_COMMAND_HEADER, userId);
    }

    private void waitResetPowerPolicyListenersComplete(PowerPolicyTestResult testResult,
            String clientTestcase, String clientAction, String component) throws Exception {
        CommonTestUtils.waitUntil("timed out (" + DEFAULT_TIMEOUT_SEC
                + "s) waiting resetPowerPolicyListenersComplete", DEFAULT_TIMEOUT_SEC,
                () -> {
                    return testResult.checkLastTestResultEntryData(clientTestcase, clientAction,
                            component, PowerPolicyTestCommandStatus.SUCCEED);
                });
    }

    private int getNumberPolicyListeners() throws Exception {
        return getCpmsFrameworkLayerStateInfo().getNumberPolicyListeners();
    }

    private void waitUntilNumberPolicyListenersEquals(int numListeners) throws Exception {
        CommonTestUtils.waitUntil("timed out (" + DEFAULT_TIMEOUT_SEC
                        + "s) getting number policy listeners", DEFAULT_TIMEOUT_SEC,
                () -> (getNumberPolicyListeners() == numListeners));
    }

    private void testPowerPolicyListeners(String clientTestcase, String component,
            PowerPolicyTestResult testResult, String clientAction) throws Exception {
        setClientTestcase(clientTestcase, getTestRunningUserId());
        int currentNumberListeners = getNumberPolicyListeners();
        registerPowerPolicyListener(component, getTestRunningUserId());
        waitUntilNumberPolicyListenersEquals(++currentNumberListeners);

        resetPowerPolicyListeners(getTestRunningUserId());
        waitResetPowerPolicyListenersComplete(testResult, clientTestcase,
                PowerPolicyTestCommandType.RESET_LISTENERS.name(), component);
        applyPowerPolicy(PowerPolicyDef.IdSet.LISTENER_TEST);
        waitPowerPolicyListenersUpdated(testResult, clientTestcase,
                PowerPolicyTestCommandType.CHECK_LISTENERS.name(), component,
                getTestRunningUserId());

        dumpPowerPolicyListener(component, getTestRunningUserId());
        testResult.checkLastTestResultEntry(clientTestcase, clientAction,
                component, PowerPolicyDef.PolicySet.LISTENER_TEST);

        unregisterPowerPolicyListener(component, getTestRunningUserId());
        applyPowerPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
        waitPowerPolicyListenersUpdated(testResult, clientTestcase,
                PowerPolicyTestCommandType.CHECK_LISTENERS.name(), component,
                getTestRunningUserId());

        dumpPowerPolicyListener(component, getTestRunningUserId());
        testResult.checkLastTestResultEntry(clientTestcase, clientAction,
                component, "not_registered");
        clearClientTestcase(getTestRunningUserId());
    }

    private void waitUntilForcedSilentModeChangeTo(boolean expected) throws Exception {
        String timeoutMsg = String.format("timed out (%ds) waiting for forced silent mode "
                + "to be %b", DEFAULT_TIMEOUT_SEC, expected);
        CommonTestUtils.waitUntil(timeoutMsg, DEFAULT_TIMEOUT_SEC,
                () -> {
                    SilentModeInfo silentInfo = getSilentModeInfo();
                    CpmsFrameworkLayerStateInfo cpmsInfo = getCpmsFrameworkLayerStateInfo();
                    return (silentInfo.getForcedSilentMode() == expected)
                            && (cpmsInfo.getForcedSilentMode() == expected);
                });
    }

    private void waitForDeviceAvailable() throws Exception {
        try {
            getDevice().waitForDeviceAvailable();
        } catch (Exception e) {
            CLog.w("device is not available, trying one more time");
            getDevice().waitForDeviceAvailable();
        }
    }

    private void checkDefaultPowerPolicySet(PowerPolicyTestHelper testHelper) throws Exception {
        testHelper.checkRegisteredPolicy(PowerPolicyDef.PolicySet.INITIAL_ALL_ON);
        testHelper.checkRegisteredPolicy(PowerPolicyDef.PolicySet.DEFAULT_ALL_ON);
    }

    private void defineAndCheckPolicy(PowerPolicyDef policyDef, String testcase, int stepNo,
            int expectedTotalPolicies) throws Exception {
        String teststep = stepNo + ". define a new power policy with ID(" + policyDef.getPolicyId()
                + ")";
        PowerPolicyTestHelper testHelper = getTestHelper(testcase, stepNo, teststep);
        if (testHelper.isPowerPolicyIdDefined(policyDef)) {
            CLog.i("Policy(ID: " + policyDef.getPolicyId() + ") is already defined");
            return;
        }
        definePowerPolicy(policyDef.toString());
        testHelper = getTestHelper(testcase, stepNo, teststep);
        testHelper.checkRegisteredPolicy(policyDef);
        testHelper.checkTotalRegisteredPolicies(expectedTotalPolicies);
    }

    private void testPowerPolicyAndComponentUserSetting() throws Exception {
        ComponentTestHelper[] testHelpers = {
            new ComponentTestHelper<LocationInfo>(this, "LOCATION", LocationInfo.class),
        };

        for (int i = 0; i < testHelpers.length; i++) {
            testComponent(testHelpers[i]);
        }
    }

    private void testPowerPolicySilentMode() throws Exception {
        checkSilentModeSupported();
        String testcase = "testPowerPolicySilentModeFull:";

        String teststep = "switch to forced silent";
        enterForcedSilentMode();
        PowerPolicyTestHelper testHelper = getTestHelper(testcase, 1, teststep);
        // Test starts in ON state, state shouldn't change between test start and this check
        checkForcedSilentMode(testHelper);

        teststep = "restore to normal mode";
        leaveForcedSilentMode();
        testHelper = getTestHelper(testcase, 2, teststep);
        // Test starts in ON state, state shouldn't change between test start and this check
        checkLeaveForcedSilentMode(testHelper);
    }

    private void testDefaultStateMachineAtONState() throws Exception {
        String testcase = "testDefaultStateMachineAtONState:";

        for (int i = 0; i < DEFAULT_STATE_MACHINE_AT_ON_STEP_NAMES.length; i++) {
            triggerVhalPowerStateReq(DEFAULT_STATE_MACHINE_AT_ON_VHAL_REQS[i],
                    PowerPolicyConstants.ShutdownParam.NOT_USED);
            PowerPolicyTestHelper testHelper = getTestHelper(testcase, i + 1,
                    DEFAULT_STATE_MACHINE_AT_ON_STEP_NAMES[i]);
            // power state shouldn't change
            testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
            testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
        }
    }

    private void testPowerPolicyChange() throws Exception {
        String testcase = "testPowerPolicyChange:";
        int stepNo = 0;

        String teststep = "check the initial power policies";
        PowerPolicyTestHelper testHelper = getTestHelper(testcase, stepNo++, teststep);
        // Test starts in ON state, state shouldn't change between test start and this check
        testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
        // save number of device power policies
        int registeredPoliciesNumber = testHelper.getNumberOfRegisteredPolicies();
        int expectedTotalPolicies = registeredPoliciesNumber;

        // create two power policies, test1 and test2, for power policy change test
        defineAndCheckPolicy(PowerPolicyDef.PolicySet.TEST1, testcase, stepNo++,
                ++expectedTotalPolicies);
        defineAndCheckPolicy(PowerPolicyDef.PolicySet.TEST2, testcase, stepNo++,
                ++expectedTotalPolicies);

        teststep = "apply power policy test1";
        applyPowerPolicy(PowerPolicyDef.IdSet.TEST1);
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.TEST1);

        teststep = "apply power policy test2";
        applyPowerPolicy(PowerPolicyDef.IdSet.TEST2);
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.TEST2);

        teststep = "revert power policy back to the default";
        applyPowerPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);

        // add "test power policy listener" here so that one reboot clears all
        defineAndCheckPolicy(PowerPolicyDef.PolicySet.LISTENER_TEST, testcase, stepNo++,
                ++expectedTotalPolicies);
        String clientTestcase = "PowerPolicyListenerTest";
        String component = "AUDIO";
        PowerPolicyTestResult testResult = new PowerPolicyTestResult(mTestAnalyzer);
        String clientAction = PowerPolicyTestCommandType.DUMP_LISTENER.name();

        testPowerPolicyListeners(clientTestcase, component, testResult, clientAction);

        // add respect to user setting test case here to utilize a single device reboot
        testPowerPolicyAndComponentUserSetting();

        // add power policy group test here to utilize added test1 and test2 policies
        teststep = "check default power policy group";
        PowerPolicyGroups emptyGroups = new PowerPolicyGroups();
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        testHelper.checkCurrentPolicyGroupId(null, mUseProtoDump);
        testHelper.checkPowerPolicyGroups(emptyGroups);

        teststep = "define power policy group";
        definePowerPolicyGroup(PowerPolicyGroups.TestSet.POLICY_GROUP_DEF1.toShellCommandString());
        definePowerPolicyGroup(PowerPolicyGroups.TestSet.POLICY_GROUP_DEF2.toShellCommandString());
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        // check that device policy groups, include just added groups as well
        testHelper.checkPowerPolicyGroupsDefined(PowerPolicyGroups.TestSet.POLICY_GROUPS1);

        teststep = "set power policy group";
        setPowerPolicyGroup(PowerPolicyGroups.TestSet.GROUP_ID1);
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        testHelper.checkCurrentPolicyGroupId(PowerPolicyGroups.TestSet.GROUP_ID1, mUseProtoDump);

        // reboot device to clear created TEST1 and TEST2 test cases.
        // need to find a way to move reboot device into AfterAll
        rebootDevice();
        teststep = "reboot to clear added test power policies";
        // device was restarted, need to wait for ON state
        waitForOnState();
        testHelper = getTestHelper(testcase, stepNo++, teststep);
        testHelper.checkTotalRegisteredPolicies(registeredPoliciesNumber);
    }

    private static final class ComponentTestHelper<T> {
        private final PowerPolicyHostTest mHostTest;
        private final String mComponentName;
        private final String mInfoRetrieveCommand;
        private final String[] mEnableCommands;
        private final String[] mDisableCommands;
        private final Class mTypeClass;

        ComponentTestHelper(PowerPolicyHostTest hostTest, String componentName, Class typeClass)
                throws Exception {
            mHostTest = hostTest;
            mComponentName = componentName;
            mTypeClass = typeClass;
            mInfoRetrieveCommand = (String) mTypeClass.getField("COMMAND").get(null);
            mEnableCommands = (String[]) mTypeClass.getField("ENABLE_COMMANDS").get(null);
            mDisableCommands = (String[]) mTypeClass.getField("DISABLE_COMMANDS").get(null);
        }

        private void turnOnComponent() throws Exception {
            for (int i = 0; i < mEnableCommands.length; i++) {
                mHostTest.executeCommand(mEnableCommands[i]);
            }
        }

        private void turnOffComponent() throws Exception {
            for (int i = 0; i < mDisableCommands.length; i++) {
                mHostTest.executeCommand(mDisableCommands[i]);
            }
        }

        private T tryGetComponentInfo() throws Exception {
            return mHostTest.executeAndParseCommand(new SystemInfoParser<T>(mTypeClass),
                    mInfoRetrieveCommand);
        }

        private void waitUntilPowerStateChangeTo(boolean expected) throws Exception {
            CommonTestUtils.waitUntil("timed out with " + mComponentName
                    + "enabled expected: " + expected, DEFAULT_TIMEOUT_SEC,
                    () -> {
                        T info = tryGetComponentInfo();
                        if (info != null) {
                            Method isPowerOn = mTypeClass.getMethod("isPowerOn");
                            Boolean result = (Boolean) isPowerOn.invoke(info);
                            return result.booleanValue() == expected;
                        }
                        return false;
                    });
        }
    }

    private void testComponent(ComponentTestHelper testHelper)
            throws Exception {
        PowerPolicyDef powerOffPolicy =
                PowerPolicyDef.createWithComponentOff(testHelper.mComponentName);
        definePowerPolicy(powerOffPolicy.toString());

        testHelper.turnOffComponent();
        testHelper.waitUntilPowerStateChangeTo(false);

        testHelper.turnOnComponent();
        testHelper.waitUntilPowerStateChangeTo(true);

        applyPowerPolicy(powerOffPolicy.getPolicyId());
        testHelper.turnOnComponent();
        testHelper.waitUntilPowerStateChangeTo(true);

        testHelper.turnOffComponent();
        testHelper.waitUntilPowerStateChangeTo(false);
    }
}
