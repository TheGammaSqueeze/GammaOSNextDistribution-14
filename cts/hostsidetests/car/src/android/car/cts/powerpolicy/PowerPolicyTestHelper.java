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

package android.car.cts.powerpolicy;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Set;

public final class PowerPolicyTestHelper {
    private final CpmsFrameworkLayerStateInfo mFrameCpms;
    private final CpmsSystemLayerStateInfo mSystemCpms;
    private final SilentModeInfo mSilentMode;
    private final String mStep;
    private final String mTestcase;

    public static final String CURRENT_STATE_ASSERT_MSG = "current state";
    public static final String CURRENT_POLICY_ASSERT_MSG = "current policy";
    public static final String CURRENT_POWER_COMPONENT_ASSERT_MSG = "current power components";
    public static final String REGISTERED_POLICY_ASSERT_MSG = "registered policy";
    public static final String SILENT_MODE_FULL_ASSERT_MSG = "silent mode in full";
    public static final String SILENT_MODE_STATUS_ASSERT_MSG = "silent mode status";
    public static final String PENDING_POLICY_ASSERT_MSG = "pending policy id";
    public static final String TOTAL_REGISTERED_POLICIES_ASSERT_MSG =
            "the total number of registered policies";

    public PowerPolicyTestHelper(String testcase, String step,
            CpmsFrameworkLayerStateInfo frameCpms, CpmsSystemLayerStateInfo sysCpms,
            SilentModeInfo silentMode) {
        mStep = step;
        mTestcase = testcase;
        mFrameCpms = frameCpms;
        mSystemCpms = sysCpms;
        mSilentMode = silentMode;
    }

    public void checkCurrentState(int expected) {
        String msg = CURRENT_STATE_ASSERT_MSG + "\nmFrameCpms:\n" + mFrameCpms;
        assertWithMessage(msg)
                .that(mFrameCpms.getCurrentState()).isEqualTo(expected);
    }

    public void checkCurrentPolicy(String expectedPolicyId) {
        boolean expected = expectedPolicyId.equals(mFrameCpms.getCurrentPolicyId());
        if (!expected) {
            CLog.d("expectedPolicyId: " + expectedPolicyId);
            CLog.d("currentPolicyId: " + mFrameCpms.getCurrentPolicyId());
        }
        assertWithMessage(CURRENT_POLICY_ASSERT_MSG).that(expected).isTrue();
    }

    public void checkSilentModeStatus(boolean expected) {
        assertWithMessage(SILENT_MODE_STATUS_ASSERT_MSG)
                .that(mFrameCpms.getForcedSilentMode() == expected).isTrue();
    }

    public void checkSilentModeFull(SilentModeInfo expected) {
        boolean status = expected.equals(mSilentMode);
        if (!status) {
            CLog.e("PowerPolicyTestHelper expected silent mode: %s", expected.toString());
            CLog.e("PowerPolicyTestHelper got tested silent mode: %s", mSilentMode.toString());
        }
        assertWithMessage(SILENT_MODE_FULL_ASSERT_MSG).that(status).isTrue();
    }

    public void checkRegisteredPolicy(PowerPolicyDef expectedPolicy) {
        boolean status = false;
        for (PowerPolicyDef def : mSystemCpms.getRegisteredPolicies()) {
            if (def.getPolicyId().equals(expectedPolicy.getPolicyId())) {
                status = expectedPolicy.equals(def);
                if (!status) {
                    CLog.e("PowerPolicyTestHelper expected policy: %s", expectedPolicy.toString());
                    CLog.e("PowerPolicyTestHelper got result policy: %s", def.toString());
                }
                break;
            }
        }
        assertWithMessage(REGISTERED_POLICY_ASSERT_MSG).that(status).isTrue();
    }

    public void checkTotalRegisteredPolicies(int totalNum) {
        ArrayList<PowerPolicyDef> policies = mSystemCpms.getRegisteredPolicies();
        String assertMsg = "registered policies: \n";
        for (int i = 0; i < policies.size(); i++) {
            assertMsg += policies.get(i).toString() + "\n";
        }
        assertWithMessage(assertMsg)
                .that(mSystemCpms.getRegisteredPolicies().size()).isEqualTo(totalNum);
    }

    /**
     * Checks if the given power policy is already defined.
     *
     * @param policyDef The definition of a power policy.
     * @return Whether the given power policy is defined.
     */
    public boolean isPowerPolicyIdDefined(PowerPolicyDef policyDef) {
        for (PowerPolicyDef def : mSystemCpms.getRegisteredPolicies()) {
            if (def.getPolicyId().equals(policyDef.getPolicyId())) {
                return true;
            }
        }
        return false;
    }

    public void checkCurrentPowerComponents(PowerPolicyDef expected) throws Exception {
        assertThat(mFrameCpms.getCurrentEnabledComponents()).asList()
                .containsExactlyElementsIn(expected.getEnables());
        assertThat(mFrameCpms.getCurrentDisabledComponents()).asList()
                .containsExactlyElementsIn(expected.getDisables());
    }

    /**
     * Check to see if the current power policy group is the expected one
     *
     * <p> If {@code useProtoDump} is true, a null expected policy group ID will be treated as an
     * empty string, since that's what proto parsing turns null policy group IDs into. If it is
     * false, meaning text dump is used, the expected policy group ID is "null" as a string.
     *
     * @param expected power policy group ID that is expected to be the current one
     * @param useProtoDump whether the method used to parse the policy group information was proto
     *                     dump or not
     */
    public void checkCurrentPolicyGroupId(String expected, boolean useProtoDump) {
        if (expected == null) {
            // differential treatment of null policy by text and proto parsing
            if (useProtoDump) {
                expected = "";
            } else {
                expected = "null";
            }
        }
        assertWithMessage(/* messageToPrepend = */ "Current policy group ID").that(
                mFrameCpms.getCurrentPolicyGroupId()).isEqualTo(expected);
    }

    public void checkPowerPolicyGroups(PowerPolicyGroups expected) {
        assertWithMessage(/* messageToPrepend = */ "Power policy groups").that(
                mFrameCpms.getPowerPolicyGroups()).isEqualTo(expected);
    }

    public int getNumberOfRegisteredPolicies() {
        return mSystemCpms.getTotalRegisteredPolicies();
    }

    public void checkPowerPolicyGroupsDefined(PowerPolicyGroups policyGroups) {
        assertWithMessage("Groups cannot be null").that(policyGroups).isNotNull();
        Set<String> groupIds = policyGroups.getGroupIds();
        for (String groupId : groupIds) {
            PowerPolicyGroups.PowerPolicyGroupDef groupDef = policyGroups.getGroup(groupId);
            assertWithMessage("Group definition cannot be null").that(groupDef).isNotNull();
            assertWithMessage("Group is not defined").that(
                    mFrameCpms.getPowerPolicyGroups().containsGroup(groupId, groupDef)).isTrue();
        }
    }

    public int getCurrentPowerState() {
        return mFrameCpms.getCurrentState();
    }
}
