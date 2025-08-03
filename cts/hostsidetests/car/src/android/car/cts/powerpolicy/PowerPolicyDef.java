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

import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

public final class PowerPolicyDef {
    public static final String[] ENABLED_HEADERS =
            {"enabledComponents", "Enabled components"};
    public static final String[] DISABLED_HEADERS =
            {"disabledComponents", "Disabled components"};
    public static final int STRING_BUILDER_BUF_SIZE = 1024;

    private final String mPolicyId;
    private final PowerComponent[] mEnables;
    private final PowerComponent[] mDisables;

    private PowerPolicyDef(String policyId, PowerComponent[] enables, PowerComponent[] disables) {
        mPolicyId = policyId;
        mEnables = enables;
        mDisables = disables;
    }

    public String getPolicyId() {
        return mPolicyId;
    }

    public PowerComponent[] getEnables() {
        return mEnables;
    }

    public PowerComponent[] getDisables() {
        return mDisables;
    }

    @Override
    public String toString() {
        String[] enables = Arrays.stream(mEnables).map(PowerComponent::getValue)
                .toArray(String[]::new);
        String[] disables = Arrays.stream(mDisables).map(PowerComponent::getValue)
                .toArray(String[]::new);
        StringBuilder str = new StringBuilder();
        str.append(mPolicyId);
        if (enables.length > 0) {
            str.append(" --enable ").append(String.join(",", enables));
        }
        if (disables.length > 0) {
            str.append(" --disable ").append(String.join(",", disables));
        }
        return str.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PowerPolicyDef that = (PowerPolicyDef) o;
        return Objects.equals(mPolicyId, that.mPolicyId)
                && Arrays.equals(mEnables, that.mEnables)
                && Arrays.equals(mDisables, that.mDisables);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mPolicyId);
        result = 31 * result + Arrays.hashCode(mEnables);
        result = 31 * result + Arrays.hashCode(mDisables);
        return result;
    }

    public static PowerPolicyDef parse(String policyDefStr, boolean hasPolicyId, int offset)
            throws Exception {
        if (policyDefStr == null) {
            throw new IllegalArgumentException("null policyDefStr parameter");
        }
        CLog.d("policyDefStr: " + policyDefStr);

        StringTokenizer tokens = new StringTokenizer(policyDefStr, "():");
        String policyId = hasPolicyId
                ? tokens.nextToken().trim().substring(offset).trim() : IdSet.NONE;

        if (!search(ENABLED_HEADERS, tokens.nextToken().trim())) {
            throw new IllegalArgumentException("malformatted enabled headers string: "
                    + policyDefStr);
        }

        int idx = 0;
        String[] enables = null;
        String tmpStr = tokens.nextToken().trim();
        CLog.d("enables: " + tmpStr);
        for (String hdr : DISABLED_HEADERS) {
            idx = tmpStr.indexOf(hdr);
            if (idx >= 0) {
                tmpStr = tmpStr.substring(0, idx).trim();
                if (!tmpStr.isEmpty()) {
                    enables = tmpStr.split(",\\s*");
                }
                break;
            }
        }
        if (idx < 0) {
            throw new IllegalArgumentException("malformatted disabled headers string: "
                    + policyDefStr);
        }
        PowerComponent[] enabledComps = PowerComponent.asComponentArray(enables);

        String[] disables = null;
        tmpStr = tokens.nextToken().trim();
        CLog.d("disables: " + tmpStr);
        if (!tmpStr.isEmpty()) {
            disables = tmpStr.split(",\\s*");
        }
        PowerComponent[] disabledComps = PowerComponent.asComponentArray(disables);

        return new PowerPolicyDef(policyId, enabledComps, disabledComps);
    }

    public static PowerPolicyDef createWithComponentOff(String component)
            throws Exception {
        PowerComponent removingComp = PowerComponent.valueOf(component);

        ArrayList<PowerComponent> enableList = new ArrayList<PowerComponent>(
                Arrays.asList(ComponentList.ALL_COMPONENTS));
        if (!enableList.remove(removingComp)) {
            throw new IllegalArgumentException(component + " is not in the all list");
        }

        PowerComponent[] enables = enableList.toArray(new PowerComponent[0]);
        PowerComponent[] disables = {removingComp};
        String policyId = component + "_disable";

        return new PowerPolicyDef(policyId, enables, disables);
    }

    private static boolean search(String[] strList, String str) {
        return Arrays.stream(strList).anyMatch(s -> str.contains(s));
    }

    public static final class IdSet {
        public static final String DEFAULT_ALL_ON = "system_power_policy_all_on";
        public static final String INITIAL_ALL_ON = "system_power_policy_initial_on";
        public static final String NO_USER_INTERACTION = "system_power_policy_no_user_interaction";
        public static final String NONE = "none";
        public static final String TEST1 = "test1_only_for_cts";
        public static final String TEST2 = "test2_only_for_cts";
        public static final String ERROR_TEST1 = "error_test1_only_for_cts";
        public static final String ERROR_TEST2 = "error_test2_only_for_cts";
        public static final String LISTENER_TEST = "listener_test_only_for_cts";
    }

    public enum PowerComponent {
        NONE("none"),
        UNKNOWN("UNKNOWN"),
        AUDIO("AUDIO"),
        MEDIA("MEDIA"),
        DISPLAY("DISPLAY"),
        BLUETOOTH("BLUETOOTH"),
        WIFI("WIFI"),
        CELLULAR("CELLULAR"),
        ETHERNET("ETHERNET"),
        PROJECTION("PROJECTION"),
        NFC("NFC"),
        INPUT("INPUT"),
        VOICE_INTERACTION("VOICE_INTERACTION"),
        VISUAL_INTERACTION("VISUAL_INTERACTION"),
        TRUSTED_DEVICE_DETECTION("TRUSTED_DEVICE_DETECTION"),
        LOCATION("LOCATION"),
        MICROPHONE("MICROPHONE"),
        CPU("CPU");

        private final String mValue;

        PowerComponent(String v) {
            mValue = v;
        }

        public String getValue() {
            return mValue;
        }

        public static PowerComponent[] asComponentArray(String[] componentNames) {
            if (componentNames == null) {
                return new PowerComponent[0];
            }
            normalizeComponentName(componentNames);
            PowerComponent[] compArray = Arrays.stream(componentNames).map(PowerComponent::valueOf)
                    .filter(e -> e != NONE).toArray(PowerComponent[]::new);
            Arrays.sort(compArray);
            return compArray;
        }

        public static PowerComponent[] asComponentArray(List<String> nameList) {
            if (nameList == null) {
                return new PowerComponent[0];
            }
            return asComponentArray(nameList.toArray(new String[0]));
        }

        private static void normalizeComponentName(String[] comps) {
            for (int i = 0; i < comps.length; i++) {
                try {
                    PowerComponent.valueOf(comps[i]);
                } catch (Exception e) {
                    if (comps[i] != null && comps[i].equals("none")) {
                        comps[i] = "NONE";
                    }
                }
            }
        }
    }

    private static final class ComponentList {
        static final PowerComponent[] ALL_COMPONENTS = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE,
            PowerComponent.CPU
        };

        static final PowerComponent[] INIT_ALL_ON_ENABLE = {
            PowerComponent.AUDIO,
            PowerComponent.DISPLAY,
            PowerComponent.CPU
        };

        static final PowerComponent[] INIT_ALL_ON_DISABLE = {
            PowerComponent.MEDIA,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE
        };

        static final PowerComponent[] DEFAULT_ALL_ON_ENABLE = ALL_COMPONENTS;
        static final PowerComponent[] DEFAULT_ALL_ON_DISABLE = {};

        static final PowerComponent[] NO_USER_INTERACT_ENABLE = {
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.CPU
        };

        static final PowerComponent[] NO_USER_INTERACT_DISABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE
        };

        static final PowerComponent[] TEST1_ENABLE =  ALL_COMPONENTS;
        static final PowerComponent[] TEST1_DISABLE = {};

        static final PowerComponent[] TEST2_ENABLE = {};
        static final PowerComponent[] TEST2_DISABLE = {
            PowerComponent.LOCATION
        };

        static final PowerComponent[] ERROR_TEST1_ENABLE = ALL_COMPONENTS;
        static final PowerComponent[] ERROR_TEST1_DISABLE = {PowerComponent.UNKNOWN};

        static final PowerComponent[] ERROR_TEST2_ENABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.UNKNOWN,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE,
            PowerComponent.CPU
        };
        static final PowerComponent[] ERROR_TEST2_DISABLE = {};

        static final PowerComponent[] RUNTIME_DEFAULT_ENABLE = ALL_COMPONENTS;
        static final PowerComponent[] RUNTIME_DEFAULT_DISABLE = {};

        static final PowerComponent[] RUNTIME_SILENT_ENABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE,
            PowerComponent.CPU
        };
        static final PowerComponent[] RUNTIME_SILENT_DISABLE = {};

        static final PowerComponent[] LISTENER_TEST_ENABLE = {
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.CPU
        };

        static final PowerComponent[] LISTENER_TEST_DISABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MICROPHONE
        };
    }

    public static final class PolicySet {
        public static final int TOTAL_DEFAULT_REGISTERED_POLICIES = 2;

        public static final PowerPolicyDef
                INITIAL_ALL_ON = new PowerPolicyDef(IdSet.INITIAL_ALL_ON,
                ComponentList.INIT_ALL_ON_ENABLE, ComponentList.INIT_ALL_ON_DISABLE);

        public static final PowerPolicyDef
                DEFAULT_ALL_ON = new PowerPolicyDef(IdSet.DEFAULT_ALL_ON,
                ComponentList.DEFAULT_ALL_ON_ENABLE, ComponentList.DEFAULT_ALL_ON_DISABLE);

        public static final PowerPolicyDef
                NO_USER_INTERACT = new PowerPolicyDef(IdSet.NO_USER_INTERACTION,
                ComponentList.NO_USER_INTERACT_ENABLE, ComponentList.NO_USER_INTERACT_DISABLE);

        public static final PowerPolicyDef TEST1 = new PowerPolicyDef(IdSet.TEST1,
                ComponentList.TEST1_ENABLE, ComponentList.TEST1_DISABLE);

        public static final PowerPolicyDef TEST2 = new PowerPolicyDef(IdSet.TEST2,
                ComponentList.TEST2_ENABLE, ComponentList.TEST2_DISABLE);

        public static final PowerPolicyDef ERROR_TEST1 = new PowerPolicyDef(IdSet.ERROR_TEST1,
                ComponentList.ERROR_TEST1_ENABLE, ComponentList.ERROR_TEST1_DISABLE);

        public static final PowerPolicyDef ERROR_TEST2 = new PowerPolicyDef(IdSet.ERROR_TEST2,
                ComponentList.ERROR_TEST2_ENABLE, ComponentList.ERROR_TEST2_DISABLE);

        public static final PowerPolicyDef LISTENER_TEST = new PowerPolicyDef(IdSet.LISTENER_TEST,
                ComponentList.LISTENER_TEST_ENABLE, ComponentList.LISTENER_TEST_DISABLE);
    }

    public static final class ComponentSet {
        public static final PowerPolicyDef RUNTIME_DEFAULT = new PowerPolicyDef(null,
                ComponentList.RUNTIME_DEFAULT_ENABLE, ComponentList.RUNTIME_DEFAULT_DISABLE);

        public static final PowerPolicyDef RUNTIME_SILENT = new PowerPolicyDef(null,
                ComponentList.RUNTIME_SILENT_ENABLE, ComponentList.RUNTIME_SILENT_DISABLE);
    }
}
