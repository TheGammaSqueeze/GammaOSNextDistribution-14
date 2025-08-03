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

import com.android.car.power.CarPowerDumpProto.PolicyReaderProto;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.IdToPolicyGroup;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.IdToPolicyGroup.PolicyGroup;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.IdToPolicyGroup.PolicyGroup.StateToDefaultPolicy;
import com.android.tradefed.log.LogUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PowerPolicyGroups {
    private final HashMap<String, PowerPolicyGroupDef> mPolicyGroups = new HashMap<>();

    public PowerPolicyGroups() { }

    public PowerPolicyGroups(PowerPolicyGroupDef[] defs) {
        for (int i = 0; i < defs.length; i++) {
            mPolicyGroups.put(defs[i].mGroupId, defs[i]);
        }
    }

    /**
     * Add a policy group
     * @param id policy group ID
     * @param waitForVHALPolicy ID of default policy for wait for VHAL power state
     * @param onPolicy ID of default policy for on power state
     * @throws IllegalArgumentException if a policy group with {@code id} already exists
     */
    public void add(String id, String waitForVHALPolicy, String onPolicy)
            throws IllegalArgumentException {
        if (mPolicyGroups.containsKey(id)) {
            throw new IllegalArgumentException(id + " policy group already exists");
        }
        PowerPolicyGroupDef groupDef = new PowerPolicyGroupDef(id, waitForVHALPolicy, onPolicy);
        mPolicyGroups.put(id, groupDef);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Power policy groups:\n");
        mPolicyGroups.forEach((k, v) -> str.append(v.toString()));
        return str.toString();
    }

    @Override
    public int hashCode() {
        return mPolicyGroups.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PowerPolicyGroups peer = (PowerPolicyGroups) o;
        return mPolicyGroups.equals(peer.mPolicyGroups);
    }

    /**
     * Parse an array of strings representing power policy groups in order to define them
     * @param defStrs strings representing power policy group definitions
     * @return the {@code PowerPolicyGroups} object with the new power policy group definitions
     */
    public static PowerPolicyGroups parse(List<String> defStrs) {
        PowerPolicyGroups policyGroups = new PowerPolicyGroups();
        String groupId = null;
        String waitForVHALPolicy = null;
        String onPolicy = null;

        String groupDefDelimiter = "-->";
        for (int i = 0; i < defStrs.size(); ++i) {
            String line = defStrs.get(i);
            if (line.contains(groupDefDelimiter)) {
                // this is policy group definition
                if (line.contains("WaitForVHAL")) {
                    waitForVHALPolicy = parsePolicyGroupDef("WaitForVHAL", line);
                } else if (line.contains("On")) {
                    onPolicy = parsePolicyGroupDef("On", line);
                } else {
                    LogUtil.CLog.d("Policy group is ignored: " + line);
                }
            } else {
                // Found name, if name is not empty, another group was already found
                // add previous group to the policyGroups before proceeding with current one
                if (groupId != null) {
                    policyGroups.add(groupId, waitForVHALPolicy, onPolicy);
                    waitForVHALPolicy = null;
                    onPolicy = null;
                }
                groupId = line.trim();
            }
        }
        // If group wasn't saved (indicated by non-null values of policies), save it
        if (groupId != null && (waitForVHALPolicy != null || onPolicy != null)) {
            policyGroups.add(groupId, waitForVHALPolicy, onPolicy);
        }
        return policyGroups;
    }

    static PowerPolicyGroups parseProto(PolicyReaderProto policyReaderProto)
            throws Exception {
        PowerPolicyGroups policyGroups = new PowerPolicyGroups();
        int numPolicyGroups = policyReaderProto.getPowerPolicyGroupMappingsCount();
        for (int i = 0; i < numPolicyGroups; i++) {
            IdToPolicyGroup policyGroupMapping = policyReaderProto.getPowerPolicyGroupMappings(i);
            String policyGroupId = policyGroupMapping.getPolicyGroupId();
            PolicyGroup policyGroup = policyGroupMapping.getPolicyGroup();
            int numPolicies = policyGroup.getDefaultPolicyMappingsCount();
            String waitForVhalPolicy = null;
            String onPolicy = null;
            for (int j = 0; j < numPolicies; j++) {
                StateToDefaultPolicy policyMapping = policyGroup.getDefaultPolicyMappings(j);
                String state = policyMapping.getState();
                String policyId = policyMapping.getDefaultPolicyId();
                if (state.equals("WaitForVHAL") && waitForVhalPolicy == null) {
                    waitForVhalPolicy = policyId;
                } else if (state.equals("On") && onPolicy == null) {
                    onPolicy = policyId;
                } else {
                    String errMsg = "Incorrect power policy groups format\nPolicy reader proto:\n"
                            + "state: " + state + "\npolicyId: " + policyId
                            + "\nwaitForVHAL policy: " + waitForVhalPolicy + "\non policy: "
                            + onPolicy;
                    LogUtil.CLog.e(errMsg);
                    throw new IllegalArgumentException(errMsg);
                }
            }
            policyGroups.add(policyGroupId, waitForVhalPolicy, onPolicy);
        }
        return policyGroups;
    }

    private static String parsePolicyGroupDef(String stateName, String defStr) {
        String[] tokens = defStr.trim().split("(\\s*)(-{1,2})(>?)(\\s*)");
        if (tokens.length != 3) {
            throw new IllegalArgumentException("malformatted policy group def str: " + defStr);
        }

        if (!stateName.equals(tokens[1].trim())) {
            String errMsg = String.format("expected power state: %s but got: %s",
                    stateName, tokens[1]);
            throw new IllegalArgumentException(errMsg);
        }

        return tokens[2].trim();
    }

    public Set<String> getGroupIds() {
        return mPolicyGroups.keySet();
    }

    public PowerPolicyGroupDef getGroup(String groupId) {
        return mPolicyGroups.get(groupId);
    }

    public boolean containsGroup(String groupId, PowerPolicyGroupDef expectedGroupDef) {
        PowerPolicyGroupDef policyGroup = mPolicyGroups.get(groupId);
        if (policyGroup == null) {
            return false;
        }

        return policyGroup.equals(expectedGroupDef);
    }

    public static final class PowerPolicyGroupDef {
        private final String mGroupId;
        private final String mWaitForVHALStatePolicy;
        private final String mOnStatePolicy;

        private PowerPolicyGroupDef(String groupId, String waitForVHALPolicy, String onPolicy) {
            mGroupId = groupId;
            mWaitForVHALStatePolicy = waitForVHALPolicy;
            mOnStatePolicy = onPolicy;
        }

        public String getGroupId() {
            return mGroupId;
        }

        public String getWaitForVHALStatePolicy() {
            return mWaitForVHALStatePolicy;
        }

        public String getOnStatePolicy() {
            return mOnStatePolicy;
        }

        public String toShellCommandString() {
            return String.format("%s WaitForVHAL:%s On:%s", mGroupId,
                    mWaitForVHALStatePolicy, mOnStatePolicy);
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("  ").append(mGroupId).append('\n');
            str.append("    - WaitForVHAL --> ").append(mWaitForVHALStatePolicy).append('\n');
            str.append("    - On --> ").append(mOnStatePolicy).append('\n');
            return str.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PowerPolicyGroupDef that = (PowerPolicyGroupDef) o;
            return Objects.equals(mGroupId, that.mGroupId)
                    && Objects.equals(mWaitForVHALStatePolicy, that.mWaitForVHALStatePolicy)
                    && Objects.equals(mOnStatePolicy, that.mOnStatePolicy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mGroupId, mWaitForVHALStatePolicy, mOnStatePolicy);
        }
    }

    public static final class TestSet {
        public static final String GROUP_ID1 = "policy_group1";
        public static final String GROUP_ID2 = "policy_group2";

        public static final PowerPolicyGroupDef POLICY_GROUP_DEF1 =
                new PowerPolicyGroupDef(GROUP_ID1, PowerPolicyDef.IdSet.TEST1,
                    PowerPolicyDef.IdSet.TEST2);

        public static final PowerPolicyGroupDef POLICY_GROUP_DEF2 =
                new PowerPolicyGroupDef(GROUP_ID2, PowerPolicyDef.IdSet.TEST2,
                    PowerPolicyDef.IdSet.TEST1);

        public static final PowerPolicyGroups POLICY_GROUPS1 = new PowerPolicyGroups(
                new PowerPolicyGroupDef[]{POLICY_GROUP_DEF1, POLICY_GROUP_DEF2});

        private TestSet() { }
    }
}
