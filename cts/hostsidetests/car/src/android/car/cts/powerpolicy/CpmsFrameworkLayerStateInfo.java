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

import com.android.car.power.CarPowerDumpProto;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto;
import com.android.car.power.CarPowerDumpProto.PowerComponentHandlerProto;
import com.android.car.power.CarPowerDumpProto.PowerComponentHandlerProto.PowerComponentToState;
import com.android.car.power.CarPowerDumpProto.SilentModeHandlerProto;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CpmsFrameworkLayerStateInfo {
    private static final int STRING_BUILDER_BUF_SIZE = 1024;
    public static final String COMMAND = "dumpsys car_service --services"
            + " CarPowerManagementService";
    public static final String COMMAND_PROTO = COMMAND + " --proto";
    public static final String CURRENT_STATE_HDR = "mCurrentState:";
    public static final String CURRENT_POLICY_ID_HDR = "mCurrentPowerPolicyId:";
    public static final String PENDING_POLICY_ID_HDR = "mPendingPowerPolicyId:";
    public static final String CURRENT_POLICY_GROUP_ID_HDR = "mCurrentPowerPolicyGroupId:";
    public static final String NUMBER_POLICY_LISTENERS_HDR = "# of power policy change listener:";
    public static final String POWER_POLICY_GROUPS_HDR = "Power policy groups:";
    public static final String PREEMPTIVE_POWER_POLICY_HDR = "Preemptive power policy:";
    public static final String COMPONENT_STATE_HDR = "Power components state:";
    public static final String COMPONENT_CONTROLLED_HDR =
            "Components powered off by power policy:";
    public static final String COMPONENT_CHANGED_HDR = "Components changed by the last policy:";
    public static final String MONITORING_HW_HDR = "Monitoring HW state signal:";
    public static final String SILENT_MODE_BY_HW_HDR = "Silent mode by HW state signal:";
    public static final String FORCED_SILENT_MODE_HDR = "Forced silent mode:";

    private static final String[] COMPONENT_LIST = {"AUDIO", "MEDIA", "DISPLAY", "BLUETOOTH",
            "WIFI", "CELLULAR", "ETHERNET", "PROJECTION", "NFC", "INPUT", "VOICE_INTERACTION",
            "VISUAL_INTERACTION", "TRUSTED_DEVICE_DETECTION", "LOCATION", "MICROPHONE", "CPU"};
    private static final HashSet COMPONENT_SET = new HashSet(Arrays.asList(COMPONENT_LIST));

    private final List<String> mEnables;
    private final List<String> mDisables;
    private final List<String> mControlledEnables;
    private final List<String> mControlledDisables;
    private final String[] mChangedComponents;
    private final PowerPolicyGroups mPowerPolicyGroups;
    private final String mCurrentPolicyId;
    private final String mPendingPolicyId;
    private final String mCurrentPolicyGroupId;
    private final int mNumberPolicyListeners;
    private final boolean mMonitoringHw;
    private final boolean mSilentModeByHw;
    private final boolean mForcedSilentMode;
    private final int mCurrentState;

    private CpmsFrameworkLayerStateInfo(String currentPolicyId, String pendingPolicyId,
            String currentPolicyGroupId, int numberPolicyListeners, String[] changedComponents,
            List<String> enables, List<String> disables, PowerPolicyGroups policyGroups,
            List<String> controlledEnables, List<String> controlledDisables, boolean monitoringHw,
            boolean silentModeByHw, boolean forcedSilentMode,
            int currentState) {
        mEnables = enables;
        mDisables = disables;
        mControlledEnables = controlledEnables;
        mControlledDisables = controlledDisables;
        mChangedComponents = changedComponents;
        mPowerPolicyGroups = policyGroups;
        mCurrentPolicyId = currentPolicyId;
        mPendingPolicyId = pendingPolicyId;
        mCurrentPolicyGroupId = currentPolicyGroupId;
        mNumberPolicyListeners = numberPolicyListeners;
        mMonitoringHw = monitoringHw;
        mSilentModeByHw = silentModeByHw;
        mForcedSilentMode = forcedSilentMode;
        mCurrentState = currentState;
    }

    public String getCurrentPolicyId() {
        return mCurrentPolicyId;
    }

    public String getPendingPolicyId() {
        return mPendingPolicyId;
    }

    public int getCurrentState() {
        return mCurrentState;
    }

    public boolean getForcedSilentMode() {
        return mForcedSilentMode;
    }

    public PowerPolicyDef.PowerComponent[] getCurrentEnabledComponents() {
        return PowerPolicyDef.PowerComponent.asComponentArray(mEnables);
    }

    public PowerPolicyDef.PowerComponent[] getCurrentDisabledComponents() {
        return PowerPolicyDef.PowerComponent.asComponentArray(mDisables);
    }

    public String getCurrentPolicyGroupId() {
        return mCurrentPolicyGroupId;
    }

    public PowerPolicyGroups getPowerPolicyGroups() {
        return mPowerPolicyGroups;
    }

    public int getNumberPolicyListeners() {
        return mNumberPolicyListeners;
    }

    public boolean isComponentOn(String component) {
        return mEnables.contains(component);
    }

    public boolean isComponentOff(String component) {
        return mDisables.contains(component);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(STRING_BUILDER_BUF_SIZE);
        sb.append("mCurrentState=").append(mCurrentState).append(' ');
        sb.append("mCurrentPolicyId=").append(mCurrentPolicyId).append(' ');
        sb.append("mPendingPolicyId=").append(mPendingPolicyId).append(' ');
        sb.append("mCurrentPolicyGroupId=").append(mCurrentPolicyGroupId).append(' ');
        sb.append("mNumberPolicyListeners=").append(mNumberPolicyListeners).append(' ');
        sb.append("silentmode=").append(mMonitoringHw).append(',');
        sb.append(mSilentModeByHw).append(',').append(mForcedSilentMode).append(' ');
        sb.append("enables=").append(String.join(",", mEnables)).append(' ');
        sb.append("disables=").append(String.join(",", mDisables)).append(' ');
        sb.append("controlledEnables=").append(String.join(",", mControlledEnables))
                .append(' ');
        sb.append("controlledDisables=").append(String.join(",", mControlledDisables))
                .append(' ');
        sb.append("changedComponents=").append(String.join(",", mChangedComponents));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpmsFrameworkLayerStateInfo that = (CpmsFrameworkLayerStateInfo) o;
        return mCurrentState == that.mCurrentState
                && mMonitoringHw == that.mMonitoringHw
                && mSilentModeByHw == that.mSilentModeByHw
                && mForcedSilentMode == that.mForcedSilentMode
                && mNumberPolicyListeners == that.mNumberPolicyListeners
                && mEnables.equals(that.mEnables)
                && mDisables.equals(that.mDisables)
                && mPowerPolicyGroups.equals(that.mPowerPolicyGroups)
                && mControlledDisables.equals(that.mControlledDisables)
                && Arrays.equals(mChangedComponents, that.mChangedComponents)
                && Objects.equals(mCurrentPolicyId, that.mCurrentPolicyId)
                && Objects.equals(mPendingPolicyId, that.mPendingPolicyId)
                && Objects.equals(mCurrentPolicyGroupId, that.mCurrentPolicyGroupId)
                && mControlledEnables.equals(that.mControlledEnables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEnables, mDisables, mControlledEnables, mControlledDisables,
                Arrays.hashCode(mChangedComponents), mPowerPolicyGroups, mCurrentPolicyId,
                mPendingPolicyId, mCurrentPolicyGroupId, mCurrentState, mMonitoringHw,
                mSilentModeByHw, mForcedSilentMode, mNumberPolicyListeners);
    }

    /**
     * Parse text dump output
     * @param cmdOutput Text dump output
     * @return CpmsFrameworkLayerStateInfo representing the information contained within the dump.
     * @throws IllegalArgumentException if the text dump is incorrectly formatted
     */
    public static CpmsFrameworkLayerStateInfo parse(String cmdOutput)
            throws IllegalArgumentException {
        int currentState = -1;
        String currentPolicyId = null;
        String pendingPolicyId = null;
        String currentPolicyGroupId = null;
        List<String> enables = null;
        List<String> disables = null;
        List<String> controlledEnables = null;
        List<String> controlledDisables = null;
        String[] changedComponents = null;
        PowerPolicyGroups policyGroups = null;
        boolean monitoringHw = false;
        boolean silentModeByHw = false;
        boolean forcedSilentMode = false;
        int numberPolicyListeners = 0;

        String[] lines = cmdOutput.split("\n");
        StateInfoParser parser = new StateInfoParser(lines);
        HashSet<String> headerCounter = new HashSet<String>();
        String header;
        while ((header = parser.searchHeader()) != null) {
            switch (header) {
                case CURRENT_STATE_HDR:
                    currentState = parser.getIntData(CURRENT_STATE_HDR);
                    break;
                case CURRENT_POLICY_ID_HDR:
                    currentPolicyId = parser.getStringData(CURRENT_POLICY_ID_HDR);
                    break;
                case PENDING_POLICY_ID_HDR:
                    pendingPolicyId = parser.getStringData(PENDING_POLICY_ID_HDR);
                    break;
                case CURRENT_POLICY_GROUP_ID_HDR:
                    currentPolicyGroupId = parser.getStringData(CURRENT_POLICY_GROUP_ID_HDR);
                    break;
                case POWER_POLICY_GROUPS_HDR:
                    List<String> groupList = parser.getStringArray(POWER_POLICY_GROUPS_HDR,
                            PREEMPTIVE_POWER_POLICY_HDR);
                    policyGroups = PowerPolicyGroups.parse(groupList);
                    break;
                case COMPONENT_STATE_HDR:
                    parser.parseComponentStates(COMPONENT_STATE_HDR,
                            COMPONENT_CONTROLLED_HDR, true);
                    enables = parser.getEnables();
                    disables = parser.getDisables();
                    Collections.sort(enables);
                    Collections.sort(disables);
                    break;
                case COMPONENT_CONTROLLED_HDR:
                    parser.parseComponentStates(COMPONENT_CONTROLLED_HDR,
                            COMPONENT_CHANGED_HDR, false);
                    controlledEnables = parser.getEnables();
                    controlledDisables = parser.getDisables();
                    Collections.sort(controlledEnables);
                    Collections.sort(controlledDisables);
                    break;
                case COMPONENT_CHANGED_HDR:
                    changedComponents = parser.getChangedComponents(COMPONENT_CHANGED_HDR,
                            MONITORING_HW_HDR);
                    break;
                case MONITORING_HW_HDR:
                    monitoringHw = parser.getBooleanData(MONITORING_HW_HDR);
                    break;
                case SILENT_MODE_BY_HW_HDR:
                    silentModeByHw = parser.getBooleanData(SILENT_MODE_BY_HW_HDR);
                    break;
                case FORCED_SILENT_MODE_HDR:
                    forcedSilentMode = parser.getBooleanData(FORCED_SILENT_MODE_HDR);
                    break;
                case NUMBER_POLICY_LISTENERS_HDR:
                    numberPolicyListeners = parser.getIntData(NUMBER_POLICY_LISTENERS_HDR);
                    break;
                default:
                    throw new IllegalArgumentException("parser header mismatch: " + header);
            }
            headerCounter.add(header);
        }

        if (headerCounter.size() != StateInfoParser.HEADERS.length) {
            String errMsg = "miss headers. got: " + headerCounter + " expected: "
                    + String.join(",", StateInfoParser.HEADERS);
            throw new IllegalArgumentException(errMsg);
        }

        return new CpmsFrameworkLayerStateInfo(currentPolicyId, pendingPolicyId,
                currentPolicyGroupId, numberPolicyListeners, changedComponents, enables,
                disables, policyGroups, controlledEnables, controlledDisables, monitoringHw,
                silentModeByHw, forcedSilentMode, currentState);
    }

    private static final class StateInfoParser {
        private static final String[] HEADERS = {
                CURRENT_STATE_HDR,
                CURRENT_POLICY_ID_HDR,
                PENDING_POLICY_ID_HDR,
                CURRENT_POLICY_GROUP_ID_HDR,
                NUMBER_POLICY_LISTENERS_HDR,
                COMPONENT_STATE_HDR,
                COMPONENT_CONTROLLED_HDR,
                POWER_POLICY_GROUPS_HDR,
                COMPONENT_CHANGED_HDR,
                MONITORING_HW_HDR,
                SILENT_MODE_BY_HW_HDR,
                FORCED_SILENT_MODE_HDR
        };
        private final String[] mLines;
        private List<String> mEnables;
        private List<String> mDisables;
        private int mIdx = 0;

        private StateInfoParser(String[] lines) {
            mLines = lines;
        }

        private List<String> getEnables() {
            return mEnables;
        }

        private List<String> getDisables() {
            return mDisables;
        }

        private int getIntData(String header) {
            int val = 0;
            switch (header) {
                case CURRENT_STATE_HDR:
                    Pattern pattern = Pattern.compile("mCurrentState: CpmsState "
                            + "[^\\n]*carPowerStateListenerState=(\\d+)");
                    Matcher matcher = pattern.matcher(mLines[mIdx]);
                    if (!matcher.find()) {
                        throw new IllegalArgumentException("malformatted mCurrentState: "
                                + mLines[mIdx]);
                    }
                    val = Integer.parseInt(matcher.group(1));
                    break;
                case NUMBER_POLICY_LISTENERS_HDR:
                    int strLen = mLines[mIdx].length();
                    val = Integer.parseInt(mLines[mIdx].substring(strLen - 1).trim());
                    break;
                default:
                    break;
            }
            return val;
        }

        private String getStringData(String header) {
            String val = null;
            if (mLines[mIdx].trim().length() != header.length()) {
                val = mLines[mIdx].trim().substring(header.length()).trim();
            }
            return val;
        }

        private List<String> getStringArray(String startHdr, String endHdr) {
            if (!mLines[mIdx].contains(startHdr)) {
                String errMsg = String.format("expected start header %s at line %d : %s",
                        startHdr, mIdx, mLines[mIdx]);
                throw new IllegalArgumentException(errMsg);
            }

            ArrayList<String> strArray = new ArrayList<>();
            while (++mIdx < mLines.length && !mLines[mIdx].contains(endHdr)) {
                strArray.add(mLines[mIdx]);
            }
            mIdx--;

            if (mIdx == (mLines.length - 1)) {
                throw new IllegalArgumentException("reaches the end while get " + startHdr);
            }
            return strArray;
        }

        private void parseComponentStates(String startHdr, String endHdr, boolean hasStateInfo) {
            mEnables = new ArrayList<>();
            mDisables = new ArrayList<>();
            while (mIdx < (mLines.length - 1) && !mLines[++mIdx].contains(endHdr)) {
                String stateStr = mLines[mIdx].trim();
                String[] vals = stateStr.split(":\\s");
                if (hasStateInfo && vals.length != 2) {
                    String errMsg = String.format("wrong format at %d in: %s ", mIdx, stateStr);
                    CLog.e(errMsg);
                    throw new IllegalArgumentException(errMsg);
                }

                for (int i = 0; i < vals.length; i++) {
                    vals[i] = vals[i].trim();
                }

                if (!COMPONENT_SET.contains(vals[0])) {
                    String errMsg = String.format("invalid component at %d with %s in: %s",
                            mIdx, vals[0], stateStr);
                    CLog.e(errMsg);
                    throw new IllegalArgumentException(errMsg);
                }

                if (hasStateInfo) {
                    if (vals[1].startsWith("on")) {
                        mEnables.add(vals[0]);
                    } else if (vals[1].startsWith("off")) {
                        mDisables.add(vals[0]);
                    } else {
                        String errMsg =
                                String.format("wrong state value at %d with (%s, %s) in: %s",
                                        mIdx, vals[0], vals[1], stateStr);
                        CLog.e(errMsg);
                        throw new IllegalArgumentException(errMsg);
                    }
                } else {
                    mDisables.add(vals[0]);
                }
            }
            mIdx--;

            if (mIdx == (mLines.length - 1)) {
                throw new IllegalArgumentException("reaches the end while parse " + startHdr);
            }
        }

        private String[] getChangedComponents(String startHdr, String endHdr) {
            int idx = mLines[mIdx].indexOf(endHdr);
            String compStr;
            if (idx < 0) {
                compStr = mLines[mIdx].substring(startHdr.length());
            } else {
                compStr = mLines[mIdx].substring(startHdr.length(), idx);
                mLines[mIdx] = mLines[mIdx].substring(idx);
                mIdx--;
            }
            return compStr.split(",\\s*");
        }

        private boolean getBooleanData(String header) {
            return Boolean.parseBoolean(mLines[mIdx].trim().substring(header.length()).trim());
        }

        private String searchHeader() {
            String header = null;
            for (mIdx++; mIdx < mLines.length; mIdx++) {
                if (mLines[mIdx].trim().isEmpty()) {
                    continue;
                }

                int firstHdrPos = mLines[mIdx].length() + 1;
                for (int i = 0; i < HEADERS.length; i++) {
                    int tempHdrPos = mLines[mIdx].indexOf(HEADERS[i]);
                    if (tempHdrPos >= 0 && (firstHdrPos > tempHdrPos)) {
                        firstHdrPos = tempHdrPos;
                        header = HEADERS[i];
                    }
                }
                if (header != null) {
                    break;
                }
            }

            return header;
        }
    }

    private static boolean powerComponentIsValid(String component) {
        if (!COMPONENT_SET.contains(component)) {
            CLog.e("invalid component " + component);
            return false;
        }
        return true;
    }

    public static CpmsFrameworkLayerStateInfo parseProto(CarPowerDumpProto proto) throws Exception {
        int currentState = proto.getCurrentState().getCarPowerManagerState();
        String currentPolicyId = proto.getCurrentPowerPolicyId();
        String pendingPolicyId = proto.getPendingPowerPolicyId();
        String currentPolicyGroupId = proto.getCurrentPowerPolicyGroupId();
        PowerComponentHandlerProto componentHandlerProto = proto.getPowerComponentHandler();
        List<String> enables = new ArrayList<>();
        List<String> disables = new ArrayList<>();

        int numComponents = componentHandlerProto.getPowerComponentStateMappingsCount();
        for (int i = 0; i < numComponents; i++) {
            PowerComponentToState componentStateMapping =
                    componentHandlerProto.getPowerComponentStateMappings(i);
            String powerComponent = componentStateMapping.getPowerComponent();
            if (powerComponentIsValid(powerComponent)) {
                if (componentStateMapping.getState()) {
                    enables.add(powerComponent);
                } else {
                    disables.add(powerComponent);
                }
            }
        }
        Collections.sort(enables);
        Collections.sort(disables);
        List<String> controlledDisables = new ArrayList<>();

        int numComponentsOffByPolicy = componentHandlerProto.getComponentsOffByPolicyCount();
        for (int i = 0; i < numComponentsOffByPolicy; i++) {
            String powerComponent = componentHandlerProto.getComponentsOffByPolicy(i);
            controlledDisables.add(powerComponent);
        }

        Collections.sort(controlledDisables);
        String[] changedComponents =
                componentHandlerProto.getLastModifiedComponents().split(",\\s");
        PolicyReaderProto policyReaderProto = proto.getPolicyReader();
        CLog.i("policy reader proto exists: " + proto.hasPolicyReader());
        PowerPolicyGroups policyGroups = PowerPolicyGroups.parseProto(policyReaderProto);
        SilentModeHandlerProto silentModeProto = proto.getSilentModeHandler();
        boolean monitoringHw = silentModeProto.getIsMonitoringHwStateSignal();
        boolean silentModeByHw = silentModeProto.getSilentModeByHwState();
        boolean forcedSilentMode = silentModeProto.getForcedSilentMode();
        int numberPolicyListeners = proto.getPowerPolicyListeners();

        return new CpmsFrameworkLayerStateInfo(currentPolicyId, pendingPolicyId,
                currentPolicyGroupId, numberPolicyListeners, changedComponents, enables,
                disables, policyGroups, /* controlledEnables= */ new ArrayList<>(),
                controlledDisables, monitoringHw, silentModeByHw, forcedSilentMode, currentState);
    }
}
