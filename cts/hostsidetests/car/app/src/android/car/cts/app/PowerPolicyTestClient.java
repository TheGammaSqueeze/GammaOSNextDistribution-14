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

package android.car.cts.app;

import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class PowerPolicyTestClient {
    private static final String TAG = PowerPolicyTestClient.class.getSimpleName();
    private static final String TEST_RESULT_HEADER = "PowerPolicyTestResult: ";

    private static final String POWERPOLICY_TEST_CMD_IDENTIFIER = "powerpolicy";
    private static final String TEST_CMD_SET_TEST = "settest";
    private static final String TEST_CMD_CLEAR_TEST = "cleartest";
    private static final String TEST_CMD_DUMP_POLICY = "dumppolicy";
    private static final String TEST_CMD_ADD_POLICY_LISTENER = "addlistener";
    private static final String TEST_CMD_REMOVE_POLICY_LISTENER = "removelistener";
    private static final String TEST_CMD_DUMP_POLICY_LISTENER = "dumplistener";
    private static final String TEST_CMD_CHECK_POLICY_LISTENERS = "checklisteners";
    private static final String TEST_CMD_RESET_POLICY_LISTENERS = "resetlisteners";
    private static final int MAX_THREAD_POOL_SIZE = 1;

    private final HashMap<String, PowerPolicyListenerImpl> mListenerMap = new HashMap<>();
    private final Executor mExecutor = Executors.newFixedThreadPool(MAX_THREAD_POOL_SIZE);
    private final PrintWriter mResultLog;
    private String mCurrentTestcase = "Unknown";
    private CarPowerManager mPowerManager;

    PowerPolicyTestClient(PrintWriter resultLog) {
        mResultLog = resultLog;
    }

    public void printResult(String msg) {
        if (msg != null) {
            mResultLog.print(msg);
        }
    }

    public void printlnResult() {
        mResultLog.println();
    }

    public void printlnResult(@Nullable String msg) {
        if (msg == null) {
            mResultLog.println();
            return;
        }
        mResultLog.println(msg);
    }

    public void printfResult(String format, Object... args) {
        mResultLog.printf(format, args);
    }

    public void printResultHeader(String msg) {
        mResultLog.printf("%s%s: %s ## ", TEST_RESULT_HEADER, mCurrentTestcase, msg);
    }

    @Nullable
    public PowerPolicyTestCommand parseCommand(Bundle intentExtras) {
        PowerPolicyTestCommand cmd = null;
        String cmdStr = intentExtras.getString(POWERPOLICY_TEST_CMD_IDENTIFIER);
        if (cmdStr == null) {
            Log.d(TAG, "empty power test command");
            return cmd;
        }
        Log.d(TAG, "parseCommand with: " + cmdStr);

        String[] tokens = cmdStr.split(",");
        assertNumberOfArgs(tokens);
        switch (tokens[0]) {
            case TEST_CMD_SET_TEST:
                cmd = new PowerPolicyTestCommand.SetTestCommand(this, tokens[1]);
                break;
            case TEST_CMD_CLEAR_TEST:
                cmd = new PowerPolicyTestCommand.ClearTestCommand(this);
                break;
            case TEST_CMD_DUMP_POLICY:
                cmd = new PowerPolicyTestCommand.DumpPolicyCommand(this);
                break;
            case TEST_CMD_ADD_POLICY_LISTENER:
                cmd = new PowerPolicyTestCommand.AddListenerCommand(this, tokens[1]);
                break;
            case TEST_CMD_REMOVE_POLICY_LISTENER:
                cmd = new PowerPolicyTestCommand.RemoveListenerCommand(this, tokens[1]);
                break;
            case TEST_CMD_DUMP_POLICY_LISTENER:
                cmd = new PowerPolicyTestCommand.DumpListenerCommand(this, tokens[1]);
                break;
            case TEST_CMD_CHECK_POLICY_LISTENERS:
                cmd = new PowerPolicyTestCommand.CheckListenersCommand(this);
                break;
            case TEST_CMD_RESET_POLICY_LISTENERS:
                cmd = new PowerPolicyTestCommand.ResetListenersCommand(this);
                break;
            default:
                throw new IllegalArgumentException("invalid power policy test command: "
                    + cmdStr);
        }
        return cmd;
    }

    public void setPowerManager(CarPowerManager pm) {
        mPowerManager = pm;
    }

    public CarPowerManager getPowerManager() {
        return mPowerManager;
    }

    public void setTestcase(String testcase) {
        mCurrentTestcase = testcase;
    }

    public void clearTestcase() {
        mCurrentTestcase = "Unknown";
    }

    public String getTestcase() {
        return mCurrentTestcase;
    }

    public void cleanup() {
        //TODO(b/183134882): add any necessary cleanup activities here
    }

    public void registerPowerPolicyListener(String compName) throws Exception {
        int compId = PowerComponentUtil.toPowerComponent(compName);
        if (compId == PowerComponentUtil.INVALID_POWER_COMPONENT) {
            throw new IllegalArgumentException("invalid power component: " + compName);
        }

        if (mListenerMap.containsKey(compName)) {
            throw new IllegalArgumentException("duplicated power component listener: " + compName);
        }

        PowerPolicyListenerImpl listener = new PowerPolicyListenerImpl(this, compName);
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(compId).build();
        mPowerManager.addPowerPolicyListener(mExecutor, filter, listener);
        mListenerMap.put(compName, listener);
        Log.d(TAG, "registered policy listener: " + compName);
    }

    public void unregisterPowerPolicyListener(String compName) throws Exception {
        PowerPolicyListenerImpl listener = mListenerMap.remove(compName);
        if (listener == null) {
            throw new IllegalArgumentException("no power component listener: " + compName);
        }
        mPowerManager.removePowerPolicyListener(listener);
        Log.d(TAG, "unregistered policy listener: " + compName);
    }

    @Nullable
    public CarPowerPolicy getListenerCurrentPolicy(String compName) throws Exception {
        PowerPolicyListenerImpl listener = mListenerMap.get(compName);
        if (listener == null) {
            throw new IllegalArgumentException("no power component listener: " + compName);
        }
        return listener.getCurrentPolicy();
    }

    public void resetPowerPolicyListeners() {
        mListenerMap.values().stream().forEach(l -> l.reset());
    }

    public boolean arePowerPolicyListenersUpdated() {
        return mListenerMap.values().stream().allMatch(l -> l.getCurrentPolicy() != null);
    }

    private void assertNumberOfArgs(String[] tokens) {
        if (tokens.length < 1) {
            throw new IllegalArgumentException("expects at least one token");
        }

        int expectedParamCount = 1;
        switch (tokens[0]) {
            case TEST_CMD_SET_TEST:
            case TEST_CMD_ADD_POLICY_LISTENER:
            case TEST_CMD_REMOVE_POLICY_LISTENER:
            case TEST_CMD_DUMP_POLICY_LISTENER:
                expectedParamCount = 2;
                break;
        }

        if (expectedParamCount != tokens.length) {
            String msg = String.format("%s expects %d params but received %d",
                    tokens[0], expectedParamCount, tokens.length);
            throw new IllegalArgumentException(msg);
        }
    }
}
