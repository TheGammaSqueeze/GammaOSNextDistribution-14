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

import java.util.ArrayList;
import java.util.Objects;

public final class CpmsSystemLayerStateInfo {
    private static final int STRING_BUILDER_BUF_SIZE = 4096;

    public static final String COMMAND = "dumpsys "
            + "android.frameworks.automotive.powerpolicy.ICarPowerPolicyServer/default";
    public static final String CURRENT_POLICY_ID_HDR = "Current power policy:";
    public static final String PENDING_POLICY_ID_HDR = "Pending power policy ID:";
    public static final String CURRENT_POLICY_GROUP_ID_HDR = "Current power policy group ID:";
    public static final String REGISTERED_POLICIES_HDR = "Registered power policies:";
    public static final String POLICY_GROUPS_HDR = "Power policy groups:";
    public static final String NO_USER_INTERACTION_POLICY_HDR =
            "No user interaction power policy:";
    public static final String CURRENT_POWER_COMPONENTS_HDR =
            "Current state of power components:";
    public static final String FORCED_SILENT_MODE_HDR = "Forced silent mode:";

    private final ArrayList<String> mPolicyGroups = new ArrayList<String>();
    private final ArrayList<PowerPolicyDef> mRegisteredPolicies;

    private final PowerPolicyDef mNoUserInteractionPolicy;
    private final PowerPolicyDef mCurrentComponentStates;
    private final String mCurrentPolicyId;
    private final String mPendingPolicyId;
    private final String mCurrentPolicyGroupId;
    private final boolean mForcedSilentMode;

    private CpmsSystemLayerStateInfo(String currentPolicyId, String pendingPolicyId,
            String currentPolicyGroupId, boolean forcedSilentMode, PowerPolicyDef noUserInteract,
            PowerPolicyDef currentComponents, ArrayList<PowerPolicyDef> registeredPolicies) {
        mCurrentPolicyId = currentPolicyId;
        mPendingPolicyId = pendingPolicyId;
        mCurrentPolicyGroupId = currentPolicyGroupId;
        mForcedSilentMode = forcedSilentMode;
        mNoUserInteractionPolicy = noUserInteract;
        mCurrentComponentStates = currentComponents;
        mRegisteredPolicies = registeredPolicies;
    }

    public String getCurrentPolicyId() {
        return mCurrentPolicyId;
    }

    public boolean getForcedSilentMode() {
        return mForcedSilentMode;
    }

    public String getPendingPolicyId() {
        return mPendingPolicyId;
    }

    public int getTotalRegisteredPolicies() {
        return mRegisteredPolicies.size();
    }

    public ArrayList<PowerPolicyDef> getRegisteredPolicies() {
        return mRegisteredPolicies;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(STRING_BUILDER_BUF_SIZE);
        sb.append("CpmsSystemLayerStateInfo:\n");
        sb.append(CURRENT_POLICY_ID_HDR).append(' ').append(mCurrentPolicyId).append('\n');
        sb.append(PENDING_POLICY_ID_HDR).append(' ').append(mPendingPolicyId).append('\n');
        sb.append(CURRENT_POLICY_GROUP_ID_HDR).append(' ');
        sb.append(mCurrentPolicyGroupId).append('\n');
        sb.append(REGISTERED_POLICIES_HDR).append('\n');
        mRegisteredPolicies.forEach(p->sb.append(p).append('\n'));
        sb.append(NO_USER_INTERACTION_POLICY_HDR).append(' ');
        sb.append(mNoUserInteractionPolicy).append('\n');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpmsSystemLayerStateInfo that = (CpmsSystemLayerStateInfo) o;
        return mForcedSilentMode == that.mForcedSilentMode
                && mRegisteredPolicies.equals(that.mRegisteredPolicies)
                && mPolicyGroups.equals(that.mPolicyGroups)
                && Objects.equals(mNoUserInteractionPolicy, that.mNoUserInteractionPolicy)
                && Objects.equals(mCurrentComponentStates, that.mCurrentComponentStates)
                && Objects.equals(mCurrentPolicyId, that.mCurrentPolicyId)
                && Objects.equals(mPendingPolicyId, that.mPendingPolicyId)
                && Objects.equals(mCurrentPolicyGroupId, that.mCurrentPolicyGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRegisteredPolicies, mPolicyGroups, mNoUserInteractionPolicy,
                mCurrentComponentStates, mCurrentPolicyId, mPendingPolicyId, mCurrentPolicyGroupId,
                mForcedSilentMode);
    }

    public static CpmsSystemLayerStateInfo parse(String cmdOutput) throws Exception {
        String[] lines = cmdOutput.split("\n");
        CpmsSystemLayerStateInfoData infoData = new CpmsSystemLayerStateInfoData(lines);

        String currentPolicyId = infoData.getStringData(CURRENT_POLICY_ID_HDR);
        String pendingPolicyId = infoData.getStringData(PENDING_POLICY_ID_HDR);
        String currentPolicyGroupId = infoData.getStringData(CURRENT_POLICY_GROUP_ID_HDR);
        ArrayList<PowerPolicyDef> registeredPolicies =
                infoData.getArrayPolicyData(REGISTERED_POLICIES_HDR, POLICY_GROUPS_HDR);
        PowerPolicyDef noUserInteractionPolicy =
                infoData.getSinglePolicyData(NO_USER_INTERACTION_POLICY_HDR);
        PowerPolicyDef currentComponentStates =
                infoData.getComponentStateData(CURRENT_POWER_COMPONENTS_HDR);
        boolean forcedSilentMode = infoData.getBooleanData(FORCED_SILENT_MODE_HDR);

        return new CpmsSystemLayerStateInfo(currentPolicyId, pendingPolicyId, currentPolicyGroupId,
                forcedSilentMode, noUserInteractionPolicy, currentComponentStates,
                registeredPolicies);
    }

    private static final class CpmsSystemLayerStateInfoData {
        private final String[] mLines;
        private int mIdx = 0;

        private CpmsSystemLayerStateInfoData(String[]  lines) {
            mLines = lines;
        }

        private String getStringData(String hdr) throws Exception {
            String val;
            searchHeader(hdr);
            if (mLines[mIdx].trim().length() == hdr.length()) {
                val = null;
            } else {
                val = mLines[mIdx].trim().substring(hdr.length()).trim();
            }
            return val;
        }

        private boolean getBooleanData(String hdr) throws Exception {
            searchHeader(hdr);
            return Boolean.parseBoolean(mLines[mIdx].trim().substring(hdr.length()).trim());
        }

        private ArrayList<PowerPolicyDef> getArrayPolicyData(String startHdr, String endHdr)
                throws Exception {
            ArrayList<PowerPolicyDef> policies = new ArrayList<PowerPolicyDef>();
            searchHeader(startHdr);
            while (!mLines[++mIdx].contains(endHdr)) {
                policies.add(PowerPolicyDef.parse(mLines[mIdx], true, 1));
            }
            return policies;
        }

        private PowerPolicyDef getSinglePolicyData(String hdr) throws Exception {
            searchHeader(hdr);
            return PowerPolicyDef.parse(mLines[mIdx].trim().substring(hdr.length()).trim(),
                    true, 0);
        }

        private PowerPolicyDef getComponentStateData(String hdr) throws Exception {
            searchHeader(hdr);
            return PowerPolicyDef.parse(mLines[++mIdx] + " " + mLines[++mIdx], false, 0);
        }

        private void searchHeader(String header) throws Exception {
            while (mIdx < mLines.length && !mLines[mIdx].contains(header)) {
                mIdx++;
            }
            if (mIdx == mLines.length) {
                throw new IllegalArgumentException(String.format(
                        "CPMS dumpsys output (total %d lines) misses header: %s", mIdx, header));
            }
        }
    }
}
