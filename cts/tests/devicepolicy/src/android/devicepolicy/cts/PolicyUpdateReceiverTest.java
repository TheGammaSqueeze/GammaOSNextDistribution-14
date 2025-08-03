/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.app.admin.PolicyUpdateReceiver.ACTION_DEVICE_POLICY_CHANGED;
import static android.app.admin.PolicyUpdateReceiver.ACTION_DEVICE_POLICY_SET_RESULT;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_BUNDLE_KEY;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_KEY;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_TARGET_USER_ID;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_UPDATE_RESULT_KEY;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.PolicyUpdateReceiver;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.TargetUser;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PolicyUpdateReceiverTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final String POLICY_KEY = "policyKey";

    private static final int TARGET_USER_ID = TargetUser.LOCAL_USER_ID;

    private static final TargetUser TARGET_USER = TargetUser.LOCAL_USER;

    private static final int POLICY_UPDATE_RESULT_CODE = PolicyUpdateResult.RESULT_POLICY_SET;

    private static final Bundle STRING_BUNDLE = Bundle.forPair("key", "value");


    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {
            "android.app.admin.PolicyUpdateReceiver#onReceive",
            "android.app.admin.PolicyUpdateReceiver#onPolicySetResult",
    })
    public void onReceive_devicePolicySetResultReceived_callsOnPolicySetResultWithCorrectParams() {
        Intent intent = createPolicyUpdateIntent(ACTION_DEVICE_POLICY_SET_RESULT);
        PolicyUpdateReceiverImpl receiver = new PolicyUpdateReceiverImpl();

        receiver.onReceive(sContext, intent);

        assertThat(receiver.mPolicySetResultCalled).isTrue();
        assertThat(receiver.mPolicyChangedCalled).isFalse();
        assertPolicyUpdateCallbackParams(receiver);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {
            "android.app.admin.PolicyUpdateReceiver#onReceive",
            "android.app.admin.PolicyUpdateReceiver#onPolicyChanged",
    })
    public void onReceive_DevicePolicyChangedReceived_callsOnPolicyChangedWithCorrectParams() {
        Intent intent = createPolicyUpdateIntent(ACTION_DEVICE_POLICY_CHANGED);
        PolicyUpdateReceiverImpl receiver = new PolicyUpdateReceiverImpl();

        receiver.onReceive(sContext, intent);

        assertThat(receiver.mPolicyChangedCalled).isTrue();
        assertThat(receiver.mPolicySetResultCalled).isFalse();
        assertPolicyUpdateCallbackParams(receiver);
    }

    private Intent createPolicyUpdateIntent(String action) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_POLICY_KEY, POLICY_KEY);
        intent.putExtra(EXTRA_POLICY_TARGET_USER_ID, TARGET_USER_ID);
        intent.putExtra(EXTRA_POLICY_UPDATE_RESULT_KEY, POLICY_UPDATE_RESULT_CODE);
        intent.putExtra(EXTRA_POLICY_BUNDLE_KEY, STRING_BUNDLE);
        return intent;
    }

    private void assertPolicyUpdateCallbackParams(PolicyUpdateReceiverImpl receiver) {
        assertThat(receiver.mPolicyIdentifier).isEqualTo(POLICY_KEY);
        assertThat(receiver.mTargetUser).isEqualTo(TARGET_USER);
        assertThat(receiver.mPolicyUpdateResult.getResultCode()).isEqualTo(
                POLICY_UPDATE_RESULT_CODE);
        for (String key : STRING_BUNDLE.keySet()) {
            assertThat(receiver.mAdditionalParams.getString(key))
                    .isEqualTo(STRING_BUNDLE.getString(key));
        }
    }

    private static final class PolicyUpdateReceiverImpl extends PolicyUpdateReceiver {
        boolean mPolicySetResultCalled = false;
        boolean mPolicyChangedCalled = false;
        String mPolicyIdentifier;
        Bundle mAdditionalParams;
        TargetUser mTargetUser;
        PolicyUpdateResult mPolicyUpdateResult;

        @Override
        public void onPolicySetResult(
                Context context, String policyIdentifier, Bundle additionalPolicyParams,
                TargetUser targetUser, PolicyUpdateResult policyUpdateResult) {
            mPolicySetResultCalled = true;
            mPolicyIdentifier = policyIdentifier;
            mAdditionalParams = additionalPolicyParams;
            mTargetUser = targetUser;
            mPolicyUpdateResult = policyUpdateResult;
        }

        @Override
        public void onPolicyChanged(
                Context context, String policyIdentifier, Bundle additionalPolicyParams,
                TargetUser targetUser, PolicyUpdateResult policyUpdateResult) {
            mPolicyChangedCalled = true;
            mPolicyIdentifier = policyIdentifier;
            mAdditionalParams = additionalPolicyParams;
            mTargetUser = targetUser;
            mPolicyUpdateResult = policyUpdateResult;
        }
    }
}
