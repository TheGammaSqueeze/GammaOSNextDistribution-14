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
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Arrays;

public final class PowerPolicyListenerImpl implements
        CarPowerManager.CarPowerPolicyListener {
    private static final String TAG = PowerPolicyListenerImpl.class.getSimpleName();

    private final PowerPolicyTestClient mTestClient;
    private final String mComponentName;
    private CarPowerPolicy mCurrentPolicy;

    PowerPolicyListenerImpl(PowerPolicyTestClient testClient, String compName) {
        mTestClient = testClient;
        mComponentName = compName;
    }

    @Override
    public void onPolicyChanged(CarPowerPolicy policy) {
        Log.d(TAG, "a new policy has been received by component: " + mComponentName);
        if (policy == null) {
            Log.e(TAG, "onPolicyChanged: null policy");
        }
        mCurrentPolicy = policy;
        mTestClient.printResultHeader("PowerPolicyListener " + mComponentName);
        mTestClient.printlnResult(getPolicyString(policy));
    }

    @Nullable
    public CarPowerPolicy getCurrentPolicy() {
        return mCurrentPolicy;
    }

    public void reset() {
        mCurrentPolicy = null;
    }

    public static String getPolicyString(CarPowerPolicy policy) {
        String[] enables = Arrays.stream(policy.getEnabledComponents())
                .mapToObj(PowerComponentUtil::componentToString).toArray(String[]::new);
        String[] disables = Arrays.stream(policy.getDisabledComponents())
                .mapToObj(PowerComponentUtil::componentToString).toArray(String[]::new);

        StringBuilder policyStr = new StringBuilder();
        policyStr.append(policy.getPolicyId()).append(" (enabledComponents: ");
        if (enables.length == 0) {
            policyStr.append("none");
        } else {
            policyStr.append(String.join(",", enables));
        }

        policyStr.append(" disabledComponents: ");
        if (disables.length == 0) {
            policyStr.append("none");
        } else {
            policyStr.append(String.join(",", disables));
        }
        policyStr.append(')');

        return policyStr.toString();
    }
}
