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

import android.car.hardware.power.CarPowerPolicy;
import android.util.Log;

import java.util.Arrays;


public abstract class PowerPolicyTestCommand {
    private static final String TAG = PowerPolicyTestCommand.class.getSimpleName();
    private static final String NO_POLICY = "null";

    private final PowerPolicyTestCommandType mType;
    protected final PowerPolicyTestClient mTestClient;
    protected final String mData;

    PowerPolicyTestCommand(PowerPolicyTestClient testClient, String data,
            PowerPolicyTestCommandType type) {
        mTestClient = testClient;
        mData = data;
        mType = type;
    }

    PowerPolicyTestCommandType getType() {
        return mType;
    }

    public abstract void execute();

    public String getData() {
        return mData;
    }

    public PowerPolicyTestClient getTestClient() {
        return mTestClient;
    }

    static final class SetTestCommand extends PowerPolicyTestCommand {
        SetTestCommand(PowerPolicyTestClient testClient, String data) {
            super(testClient, data, PowerPolicyTestCommandType.SET_TEST);
        }

        @Override
        public void execute() {
            mTestClient.printResultHeader(getType().name());
            mTestClient.printlnResult(mData);
            mTestClient.setTestcase(mData);
            Log.d(TAG, "setTestcase: " + mData);
        }
    }

    static final class ClearTestCommand extends PowerPolicyTestCommand {
        ClearTestCommand(PowerPolicyTestClient testClient) {
            super(testClient, /* data = */ null, PowerPolicyTestCommandType.CLEAR_TEST);
        }

        @Override
        public void execute() {
            mTestClient.clearTestcase();
            mTestClient.printResultHeader(getType().name());
            mTestClient.printlnResult();
            Log.d(TAG, "clearTestcase: " + mTestClient.getTestcase());
        }
    }

    static final class DumpPolicyCommand extends PowerPolicyTestCommand {
        DumpPolicyCommand(PowerPolicyTestClient testClient) {
            super(testClient, /* data = */ null, PowerPolicyTestCommandType.DUMP_POLICY);
        }

        @Override
        public void execute() {
            CarPowerPolicy cpp = mTestClient.getPowerManager().getCurrentPowerPolicy();
            if (cpp == null) {
                Log.d(TAG, "null current power policy");
                return;
            }

            String policyId = cpp.getPolicyId();
            if (policyId == null) {
                policyId = NO_POLICY;
            }
            String[] enables = Arrays.stream(cpp.getEnabledComponents())
                    .mapToObj(PowerComponentUtil::componentToString)
                    .toArray(String[]::new);
            String[] disables = Arrays.stream(cpp.getDisabledComponents())
                    .mapToObj(PowerComponentUtil::componentToString)
                    .toArray(String[]::new);
            mTestClient.printResultHeader(getType().name());
            mTestClient.printfResult("%s (", policyId);
            mTestClient.printfResult("enabledComponents:%s ", String.join(",", enables));
            mTestClient.printfResult("disabledComponents:%s)\n", String.join(",", disables));

            Log.d(TAG, "dump power policy " + policyId);
        }
    }

    static final class AddListenerCommand extends PowerPolicyTestCommand {
        AddListenerCommand(PowerPolicyTestClient testClient, String compName) {
            super(testClient, compName, PowerPolicyTestCommandType.ADD_LISTENER);
        }

        @Override
        public void execute() {
            Log.d(TAG, "addListener: " + mTestClient.getTestcase());
            mTestClient.printResultHeader(getType().name());
            try {
                mTestClient.registerPowerPolicyListener(mData);
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.SUCCEED);
            } catch (Exception e) {
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.FAILED);
                Log.e(TAG, "failed to addListener", e);
            }
        }
    }

    static final class RemoveListenerCommand extends PowerPolicyTestCommand {
        RemoveListenerCommand(PowerPolicyTestClient testClient, String compName) {
            super(testClient, compName, PowerPolicyTestCommandType.REMOVE_LISTENER);
        }

        @Override
        public void execute() {
            Log.d(TAG, "removeListener: " + mTestClient.getTestcase());
            mTestClient.printResultHeader(getType().name());
            try {
                mTestClient.unregisterPowerPolicyListener(mData);
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.SUCCEED);
            } catch (Exception e) {
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.FAILED);
                Log.e(TAG, "failed to removeListener", e);
            }
        }
    }

    static final class DumpListenerCommand extends PowerPolicyTestCommand {
        DumpListenerCommand(PowerPolicyTestClient testClient, String compName) {
            super(testClient, compName, PowerPolicyTestCommandType.DUMP_LISTENER);
        }

        @Override
        public void execute() {
            Log.d(TAG, "dumpListener: " + mTestClient.getTestcase());
            mTestClient.printResultHeader(getType().name() + ": " + mData);
            try {
                CarPowerPolicy policy = mTestClient.getListenerCurrentPolicy(mData);
                String policyStr = NO_POLICY;
                if (policy != null) {
                    policyStr = PowerPolicyListenerImpl.getPolicyString(policy);
                }
                mTestClient.printlnResult(policyStr);
                Log.d(TAG, "received power policy: " + policyStr);
            } catch (Exception e) {
                mTestClient.printlnResult("not_registered");
                Log.d(TAG, "failed to find registered policy " + mData, e);
            }
        }
    }

    static final class CheckListenersCommand extends PowerPolicyTestCommand {
        CheckListenersCommand(PowerPolicyTestClient testClient) {
            super(testClient, /* data = */ null, PowerPolicyTestCommandType.CHECK_LISTENERS);
        }

        @Override
        public void execute() {
            Log.d(TAG, "waitListeners: " + mTestClient.getTestcase());
            mTestClient.printResultHeader(getType().name());
            if (mTestClient.arePowerPolicyListenersUpdated()) {
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.PROPAGATED);
                Log.d(TAG, "policy change is propagated");
            } else {
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.IN_PROGRESS);
                Log.d(TAG, "policy change is in-progress");
            }
        }
    }

    static final class ResetListenersCommand extends PowerPolicyTestCommand {
        ResetListenersCommand(PowerPolicyTestClient testClient) {
            super(testClient, /* data = */ null, PowerPolicyTestCommandType.RESET_LISTENERS);
        }

        @Override
        public void execute() {
            Log.d(TAG, "resetListeners: " + mTestClient.getTestcase());
            mTestClient.printResultHeader(getType().name());
            try {
                mTestClient.resetPowerPolicyListeners();
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.SUCCEED);
            } catch (Exception e) {
                mTestClient.printlnResult(PowerPolicyTestCommandStatus.FAILED);
            }
        }
    }
}
