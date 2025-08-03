/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.power;

import static android.car.hardware.power.PowerComponentUtil.FIRST_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.INVALID_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.LAST_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.powerComponentToString;
import static android.car.hardware.power.PowerComponentUtil.toPowerComponent;
import static android.frameworks.automotive.powerpolicy.PowerComponent.MINIMUM_CUSTOM_COMPONENT_VALUE;

import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.PowerComponent;
import android.hardware.automotive.vehicle.VehicleApPowerStateReport;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.Xml;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.Lists;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.ComponentNameToValue;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.IdToPolicyGroup;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.IdToPolicyGroup.PolicyGroup;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.IdToPolicyGroup.PolicyGroup.StateToDefaultPolicy;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto.PowerPolicy;
import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class to read and manage vendor power policies.
 *
 * <p>{@code CarPowerManagementService} manages power policies through {@code PolicyReader}. This
 * class is not thread-safe, and must be used in the main thread or with additional serialization.
 */
public final class PolicyReader {
    public static final String POWER_STATE_WAIT_FOR_VHAL = "WaitForVHAL";
    public static final String POWER_STATE_ON = "On";

    // TODO(b/286303350): Remove all system power policy definitions after refactor is complete
    static final String SYSTEM_POWER_POLICY_PREFIX = "system_power_policy_";
    // Preemptive system power policy used for disabling user interaction in Silent Mode or Garage
    // Mode.
    static final String POWER_POLICY_ID_NO_USER_INTERACTION = SYSTEM_POWER_POLICY_PREFIX
            + "no_user_interaction";
    // Preemptive system power policy used for preparing Suspend-to-RAM.
    static final String POWER_POLICY_ID_SUSPEND_PREP = SYSTEM_POWER_POLICY_PREFIX
            + "suspend_prep";
    // Non-preemptive system power policy used for turning all components on.
    static final String POWER_POLICY_ID_ALL_ON = SYSTEM_POWER_POLICY_PREFIX + "all_on";
    // Non-preemptive system power policy used to represent minimal on state.
    static final String POWER_POLICY_ID_INITIAL_ON = SYSTEM_POWER_POLICY_PREFIX + "initial_on";

    static final int INVALID_POWER_STATE = -1;

    private static final String TAG = CarLog.tagFor(PolicyReader.class);
    private static final String VENDOR_POLICY_PATH = "/vendor/etc/automotive/power_policy.xml";

    private static final String NAMESPACE = null;
    private static final Set<String> VALID_VERSIONS = new ArraySet<>(Arrays.asList("1.0"));
    private static final String TAG_POWER_POLICY = "powerPolicy";
    private static final String TAG_POLICY_GROUPS = "policyGroups";
    private static final String TAG_POLICY_GROUP = "policyGroup";
    private static final String TAG_DEFAULT_POLICY = "defaultPolicy";
    private static final String TAG_NO_DEFAULT_POLICY = "noDefaultPolicy";
    private static final String TAG_POLICIES = "policies";
    private static final String TAG_POLICY = "policy";
    private static final String TAG_OTHER_COMPONENTS = "otherComponents";
    private static final String TAG_COMPONENT = "component";
    private static final String TAG_SYSTEM_POLICY_OVERRIDES = "systemPolicyOverrides";
    private static final String TAG_CUSTOM_COMPONENTS = "customComponents";
    private static final String TAG_CUSTOM_COMPONENT = "customComponent";
    private static final String ATTR_DEFAULT_POLICY_GROUP = "defaultPolicyGroup";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_ID = "id";
    private static final String ATTR_STATE = "state";
    private static final String ATTR_BEHAVIOR = "behavior";
    private static final String POWER_ONOFF_ON = "on";
    private static final String POWER_ONOFF_OFF = "off";
    private static final String POWER_ONOFF_UNTOUCHED = "untouched";

    private static final int[] ALL_COMPONENTS;
    private static final int[] NO_COMPONENTS = EMPTY_INT_ARRAY;
    private static final int[] INITIAL_ON_COMPONENTS = {
            PowerComponent.AUDIO, PowerComponent.DISPLAY, PowerComponent.CPU
    };
    private static final int[] NO_USER_INTERACTION_ENABLED_COMPONENTS = {
            PowerComponent.WIFI, PowerComponent.CELLULAR,
            PowerComponent.ETHERNET, PowerComponent.TRUSTED_DEVICE_DETECTION, PowerComponent.CPU
    };
    private static final int[] NO_USER_INTERACTION_DISABLED_COMPONENTS = {
            PowerComponent.AUDIO, PowerComponent.MEDIA, PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH, PowerComponent.PROJECTION, PowerComponent.NFC,
            PowerComponent.INPUT, PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION, PowerComponent.LOCATION, PowerComponent.MICROPHONE
    };
    private static final Set<Integer> SYSTEM_POLICY_CONFIGURABLE_COMPONENTS =
            new ArraySet<>(Arrays.asList(PowerComponent.BLUETOOTH, PowerComponent.NFC,
            PowerComponent.TRUSTED_DEVICE_DETECTION));
    private static final int[] SUSPEND_PREP_DISABLED_COMPONENTS = {
            PowerComponent.AUDIO, PowerComponent.BLUETOOTH, PowerComponent.WIFI,
            PowerComponent.LOCATION, PowerComponent.MICROPHONE, PowerComponent.CPU
    };
    private static final CarPowerPolicy POWER_POLICY_ALL_ON;
    private static final CarPowerPolicy POWER_POLICY_INITIAL_ON;
    private static final CarPowerPolicy POWER_POLICY_SUSPEND_PREP;

    static {
        int allCount = LAST_POWER_COMPONENT - FIRST_POWER_COMPONENT + 1;
        ALL_COMPONENTS = new int[allCount];
        int[] initialOnDisabledComponents = new int[allCount - INITIAL_ON_COMPONENTS.length];
        int pos = 0;
        for (int c = FIRST_POWER_COMPONENT; c <= LAST_POWER_COMPONENT; c++) {
            ALL_COMPONENTS[c - FIRST_POWER_COMPONENT] = c;
            if (!containsComponent(INITIAL_ON_COMPONENTS, c)) {
                initialOnDisabledComponents[pos++] = c;
            }
        }

        POWER_POLICY_ALL_ON = new CarPowerPolicy(POWER_POLICY_ID_ALL_ON, ALL_COMPONENTS.clone(),
                NO_COMPONENTS.clone());
        POWER_POLICY_INITIAL_ON = new CarPowerPolicy(POWER_POLICY_ID_INITIAL_ON,
                INITIAL_ON_COMPONENTS.clone(), initialOnDisabledComponents);
        POWER_POLICY_SUSPEND_PREP = new CarPowerPolicy(POWER_POLICY_ID_SUSPEND_PREP,
                NO_COMPONENTS.clone(), SUSPEND_PREP_DISABLED_COMPONENTS.clone());
    }
    // Allows for injecting mock feature flag values during testing
    private FeatureFlags mFeatureFlags;

    private ArrayMap<String, CarPowerPolicy> mRegisteredPowerPolicies;
    private ArrayMap<String, SparseArray<String>> mPolicyGroups;
    // TODO(b/286303350): remove once power policy refactor complete
    private ArrayMap<String, CarPowerPolicy> mPreemptivePowerPolicies;
    // TODO(b/286303350): remove once power policy refactor complete
    private String mDefaultPolicyGroupId;
    private ArrayMap<String, Integer> mCustomComponents = new ArrayMap<>();

    /**
     * Gets {@code CarPowerPolicy} corresponding to the given policy ID.
     */
    @Nullable
    CarPowerPolicy getPowerPolicy(String policyId) {
        return mRegisteredPowerPolicies.get(policyId);
    }

    /**
     * Gets {@code CarPowerPolicy} corresponding to the given power state in the given power
     * policy group.
     */
    @Nullable
    CarPowerPolicy getDefaultPowerPolicyForState(String groupId, int state) {
        SparseArray<String> group = mPolicyGroups.get(
                (groupId == null || groupId.isEmpty()) ? mDefaultPolicyGroupId : groupId);
        if (group == null) {
            return null;
        }
        String policyId = group.get(state);
        if (policyId == null) {
            return null;
        }
        return mRegisteredPowerPolicies.get(policyId);
    }

    /**
     * Gets the preemptive power policy corresponding to the given policy ID.
     *
     * <p> When a preemptive power policy is the current power policy, applying a regular power
     * policy is deferred until the preemptive power policy is released.
     */
    @Nullable
    CarPowerPolicy getPreemptivePowerPolicy(String policyId) {
        return mPreemptivePowerPolicies.get(policyId);
    }

    boolean isPowerPolicyGroupAvailable(String groupId) {
        return mPolicyGroups.containsKey(groupId);
    }

    boolean isPreemptivePowerPolicy(String policyId) {
        return mPreemptivePowerPolicies.containsKey(policyId);
    }

    /**
     * Gets default power policy group ID.
     *
     * @return {@code String} containing power policy group ID or {@code null} if it is not defined
     */
    @Nullable
    String getDefaultPowerPolicyGroup() {
        return mDefaultPolicyGroupId;
    }

    void init(FeatureFlags fakeFeatureFlags) {
        mFeatureFlags = fakeFeatureFlags;
        Slogf.d(TAG, "PolicyReader is initializing, carPowerPolicyRefactoring = "
                + mFeatureFlags.carPowerPolicyRefactoring());
        initPolicies();
        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            readPowerPolicyConfiguration();
        }
    }

    /**
     * Creates and registers a new power policy.
     *
     * @return {@code PolicyOperationStatus.OK}, if successful. Otherwise, the other values.
     */
    @PolicyOperationStatus.ErrorCode
    int definePowerPolicy(String policyId, String[] enabledComponents,
            String[] disabledComponents) {
        // policyId cannot be empty or null
        if (policyId == null || policyId.length() == 0) {
            int error = PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID;
            Slogf.w(TAG,
                    PolicyOperationStatus.errorCodeToString(error, "policyId cannot be empty"));
            return error;
        }
        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            if (isSystemPowerPolicy(policyId)) {
                int error = PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID;
                Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error,
                        "policyId should not start with " + SYSTEM_POWER_POLICY_PREFIX));
                return error;
            }
        }
        if (mRegisteredPowerPolicies.containsKey(policyId)) {
            int error = PolicyOperationStatus.ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID;
            Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyId));
            return error;
        }
        SparseBooleanArray components = new SparseBooleanArray();
        int status = parseComponents(enabledComponents, true, components);
        if (status != PolicyOperationStatus.OK) {
            return status;
        }
        status = parseComponents(disabledComponents, false, components);
        if (status != PolicyOperationStatus.OK) {
            return status;
        }
        CarPowerPolicy policy = new CarPowerPolicy(policyId, toIntArray(components, true),
                toIntArray(components, false));
        mRegisteredPowerPolicies.put(policyId, policy);
        return PolicyOperationStatus.OK;
    }

    /**
     * Defines and registers a new power policy group.
     *
     * @return {@code PolicyOperationStatus.OK}, if successful. Otherwise, the other values.
     */
    @PolicyOperationStatus.ErrorCode
    int definePowerPolicyGroup(String policyGroupId, SparseArray<String> defaultPolicyPerState) {
        if (policyGroupId == null) {
            return PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_GROUP_ID;
        }
        if (mPolicyGroups.containsKey(policyGroupId)) {
            int error = PolicyOperationStatus.ERROR_DOUBLE_REGISTERED_POWER_POLICY_GROUP_ID;
            Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyGroupId));
            return error;
        }
        for (int i = 0; i < defaultPolicyPerState.size(); i++) {
            int state = defaultPolicyPerState.keyAt(i);
            String policyId = defaultPolicyPerState.valueAt(i);
            if (!mRegisteredPowerPolicies.containsKey(policyId)) {
                int error = PolicyOperationStatus.ERROR_NOT_REGISTERED_POWER_POLICY_ID;
                Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyId + " for "
                        + vhalPowerStateToString(state)));
                return error;
            }
        }
        mPolicyGroups.put(policyGroupId, defaultPolicyPerState);
        return PolicyOperationStatus.OK;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        int size = mCustomComponents.size();
        writer.printf("Registered custom components:");
        if (size == 0) {
            writer.printf(" none\n");
        } else {
            writer.printf("\n");
            writer.increaseIndent();
            for (int i = 0; i < size; i++) {
                Object key = mCustomComponents.keyAt(i);
                writer.printf("Component name: %s, value: %s\n", key, mCustomComponents.get(key));
            }
            writer.decreaseIndent();
        }

        size = mRegisteredPowerPolicies.size();
        writer.printf("Registered power policies:");
        if (size == 0) {
            writer.printf(" none\n");
        } else {
            writer.printf("\n");
            writer.increaseIndent();
            for (int i = 0; i < size; i++) {
                writer.println(mRegisteredPowerPolicies.valueAt(i).toString());
            }
            writer.decreaseIndent();
        }

        size = mPolicyGroups.size();
        writer.printf("Power policy groups:");
        if (size == 0) {
            writer.printf(" none\n");
        } else {
            writer.printf("\n");
            writer.increaseIndent();
            for (int i = 0; i < size; i++) {
                String key = mPolicyGroups.keyAt(i);
                writer.println(key);
                writer.increaseIndent();
                SparseArray<String> group = mPolicyGroups.get(key);
                for (int j = 0; j < group.size(); j++) {
                    writer.printf("- %s --> %s\n", vhalPowerStateToString(group.keyAt(j)),
                            group.valueAt(j));
                }
                writer.decreaseIndent();
            }
            writer.decreaseIndent();
        }
        writer.println("Preemptive power policy:");
        writer.increaseIndent();

        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            writer.println("Preemptive power policies not supported w/refactored power policy");
        } else {
            for (int i = 0; i < mPreemptivePowerPolicies.size(); i++) {
                writer.println(mPreemptivePowerPolicies.valueAt(i).toString());
            }
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProtoPowerPolicies(
            ProtoOutputStream proto, long fieldNumber, ArrayMap<String, CarPowerPolicy> policies) {
        for (int i = 0; i < policies.size(); i++) {
            long policiesToken = proto.start(fieldNumber);
            CarPowerPolicy powerPolicy = policies.valueAt(i);
            proto.write(PowerPolicy.POLICY_ID, powerPolicy.getPolicyId());
            int[] enabledComponents = powerPolicy.getEnabledComponents();
            for (int j = 0; j < enabledComponents.length; j++) {
                proto.write(PowerPolicy.ENABLED_COMPONENTS,
                        powerComponentToString(enabledComponents[j]));
            }
            int[] disabledComponents = powerPolicy.getDisabledComponents();
            for (int j = 0; j < disabledComponents.length; j++) {
                proto.write(PowerPolicy.DISABLED_COMPONENTS,
                        powerComponentToString(disabledComponents[j]));
            }
            proto.end(policiesToken);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long policyReaderToken = proto.start(CarPowerDumpProto.POLICY_READER);

        for (int i = 0; i < mCustomComponents.size(); i++) {
            long customComponentMappingsToken = proto.start(
                    PolicyReaderProto.CUSTOM_COMPONENT_MAPPINGS);
            Object key = mCustomComponents.keyAt(i);
            proto.write(ComponentNameToValue.COMPONENT_NAME, key.toString());
            proto.write(ComponentNameToValue.COMPONENT_VALUE,
                        mCustomComponents.get(key).intValue());
            proto.end(customComponentMappingsToken);
        }

        dumpProtoPowerPolicies(
                proto, PolicyReaderProto.REGISTERED_POWER_POLICIES, mRegisteredPowerPolicies);

        for (int i = 0; i < mPolicyGroups.size(); i++) {
            long powerPolicyGroupMappingsToken = proto.start(
                    PolicyReaderProto.POWER_POLICY_GROUP_MAPPINGS);
            String policyGroupId = mPolicyGroups.keyAt(i);
            proto.write(IdToPolicyGroup.POLICY_GROUP_ID, policyGroupId);
            SparseArray<String> group = mPolicyGroups.get(policyGroupId);
            long policyGroupMappingsToken = proto.start(IdToPolicyGroup.POLICY_GROUP);
            for (int j = 0; j < group.size(); j++) {
                long defaultPolicyMappingsToken = proto.start(PolicyGroup.DEFAULT_POLICY_MAPPINGS);
                proto.write(StateToDefaultPolicy.STATE, vhalPowerStateToString(group.keyAt(j)));
                proto.write(StateToDefaultPolicy.DEFAULT_POLICY_ID, group.valueAt(j));
                proto.end(defaultPolicyMappingsToken);
            }
            proto.end(policyGroupMappingsToken);
            proto.end(powerPolicyGroupMappingsToken);
        }

        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            dumpProtoPowerPolicies(
                    proto, PolicyReaderProto.PREEMPTIVE_POWER_POLICIES, mPreemptivePowerPolicies);
        }

        proto.end(policyReaderToken);
    }

    @VisibleForTesting
    void initPolicies() {
        mRegisteredPowerPolicies = new ArrayMap<>();
        mPolicyGroups = new ArrayMap<>();

        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            registerBasicPowerPolicies();
            mPreemptivePowerPolicies = new ArrayMap<>();
            mPreemptivePowerPolicies.put(POWER_POLICY_ID_NO_USER_INTERACTION,
                    new CarPowerPolicy(POWER_POLICY_ID_NO_USER_INTERACTION,
                            NO_USER_INTERACTION_ENABLED_COMPONENTS.clone(),
                            NO_USER_INTERACTION_DISABLED_COMPONENTS.clone()));
            mPreemptivePowerPolicies.put(POWER_POLICY_ID_SUSPEND_PREP, POWER_POLICY_SUSPEND_PREP);
        }
    }

    private void readPowerPolicyConfiguration() {
        try (InputStream inputStream = new FileInputStream(VENDOR_POLICY_PATH)) {
            readPowerPolicyFromXml(inputStream);
        } catch (IOException | XmlPullParserException | PolicyXmlException e) {
            Slogf.w(TAG, "Proceed without registered policies: failed to parse %s: %s",
                    VENDOR_POLICY_PATH, e);
        }
    }

    @VisibleForTesting
    void readPowerPolicyFromXml(InputStream stream) throws PolicyXmlException,
            XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, NAMESPACE != null);
        parser.setInput(stream, null);

        // Ensure <powerPolicy> is the root
        parser.nextTag();
        parser.require(START_TAG, NAMESPACE, TAG_POWER_POLICY);
        // Check version
        String version = parser.getAttributeValue(NAMESPACE, ATTR_VERSION);
        if (!VALID_VERSIONS.contains(version)) {
            throw new PolicyXmlException("invalid XML version: " + version);
        }

        ArrayMap<String, CarPowerPolicy> registeredPolicies;
        List<IntermediateCarPowerPolicy> intermediateCarPowerPolicies = new ArrayList<>();
        ArrayMap<String, SparseArray<String>> policyGroups = new ArrayMap<>();
        List<IntermediateCarPowerPolicy> intermediateSystemPolicyOverride = new ArrayList<>();
        ArrayMap<String, Integer> customComponents = new ArrayMap<>();
        CarPowerPolicy systemPolicyOverride;
        String defaultGroupPolicyId = null;

        int type;
        while ((type = parser.next()) != END_DOCUMENT && type != END_TAG) {
            if (type != START_TAG) continue;
            switch (parser.getName()) {
                case TAG_POLICIES:
                    intermediateCarPowerPolicies.addAll(parsePolicies(parser, true));
                    break;
                case TAG_POLICY_GROUPS:
                    defaultGroupPolicyId = parser.getAttributeValue(NAMESPACE,
                            ATTR_DEFAULT_POLICY_GROUP);
                    policyGroups = parsePolicyGroups(parser);
                    break;
                case TAG_SYSTEM_POLICY_OVERRIDES:
                    intermediateSystemPolicyOverride.addAll(parseSystemPolicyOverrides(parser));
                    break;
                case TAG_CUSTOM_COMPONENTS:
                    customComponents = parseCustomComponents(parser);
                    break;
                default:
                    throw new PolicyXmlException("unknown tag: " + parser.getName() + " under "
                            + TAG_POWER_POLICY);
            }
        }
        registeredPolicies = validatePowerPolicies(intermediateCarPowerPolicies, customComponents);
        systemPolicyOverride = validateSystemOverrides(intermediateSystemPolicyOverride,
                customComponents);
        validatePolicyGroups(policyGroups, registeredPolicies, defaultGroupPolicyId);

        mCustomComponents = customComponents;
        mDefaultPolicyGroupId = defaultGroupPolicyId;
        mRegisteredPowerPolicies = registeredPolicies;
        registerBasicPowerPolicies();
        mPolicyGroups = policyGroups;
        reconstructSystemPowerPolicy(systemPolicyOverride);
    }

    private ArrayMap<String, Integer> parseCustomComponents(XmlPullParser parser)
            throws XmlPullParserException, IOException, PolicyXmlException {
        ArrayMap<String, Integer> customComponentsMap = new ArrayMap<>();
        int type;
        while ((type = parser.next()) != END_DOCUMENT && type != END_TAG) {
            if (type != START_TAG) continue;
            if (TAG_CUSTOM_COMPONENT.equals(parser.getName())) {
                int componentValue = Integer.parseInt(parser.getAttributeValue(NAMESPACE, "value"));
                String componentName = getText(parser);
                customComponentsMap.put(componentName, componentValue);

                if (componentValue < MINIMUM_CUSTOM_COMPONENT_VALUE) {
                    throw new PolicyXmlException(
                            "Invalid custom component value " + componentValue + " "
                                    + componentName);
                }
                skip(parser);
            } else {
                throw new PolicyXmlException(
                        "unknown tag: " + parser.getName() + " under " + TAG_POLICIES);
            }
        }
        return customComponentsMap;
    }

    private List<IntermediateCarPowerPolicy> parsePolicies(XmlPullParser parser,
            boolean includeOtherComponents)
            throws PolicyXmlException, XmlPullParserException, IOException {
        List<IntermediateCarPowerPolicy> policies = new ArrayList<>();
        int type;
        while ((type = parser.next()) != END_DOCUMENT && type != END_TAG) {
            if (type != START_TAG) continue;
            if (TAG_POLICY.equals(parser.getName())) {
                String policyId = parser.getAttributeValue(NAMESPACE, ATTR_ID);
                if (policyId == null || policyId.equals("")) {
                    throw new PolicyXmlException("no |" + ATTR_ID + "| attribute of |" + TAG_POLICY
                            + "| tag");
                }
                if (includeOtherComponents && isSystemPowerPolicy(policyId)) {
                    throw new PolicyXmlException("Policy ID should not start with "
                            + SYSTEM_POWER_POLICY_PREFIX);
                }
                policies.add(parsePolicy(parser, policyId, includeOtherComponents));
            } else {
                throw new PolicyXmlException("unknown tag: " + parser.getName() + " under "
                        + TAG_POLICIES);
            }
        }
        return policies;
    }

    private ArrayMap<String, SparseArray<String>> parsePolicyGroups(XmlPullParser parser) throws
            PolicyXmlException, XmlPullParserException, IOException {
        ArrayMap<String, SparseArray<String>> policyGroups = new ArrayMap<>();
        int type;
        while ((type = parser.next()) != END_DOCUMENT && type != END_TAG) {
            if (type != START_TAG) continue;
            if (TAG_POLICY_GROUP.equals(parser.getName())) {
                String groupId = parser.getAttributeValue(NAMESPACE, ATTR_ID);
                if (groupId == null || groupId.equals("")) {
                    throw new PolicyXmlException("no |" + ATTR_ID + "| attribute of |"
                            + TAG_POLICY_GROUP + "| tag");
                }
                policyGroups.put(groupId, parsePolicyGroup(parser));
            } else {
                throw new PolicyXmlException("unknown tag: " + parser.getName() + " under "
                        + TAG_POLICY_GROUPS);
            }
        }
        return policyGroups;
    }

    private List<IntermediateCarPowerPolicy> parseSystemPolicyOverrides(XmlPullParser parser) throws
            PolicyXmlException, XmlPullParserException, IOException {
        return parsePolicies(/* parser= */ parser, /* includeOtherComponents= */ false);
    }
    @Nullable
    private CarPowerPolicy validateSystemOverrides(
            List<IntermediateCarPowerPolicy> systemPolicyOverrideIntermediate,
            ArrayMap<String, Integer> customComponents) throws PolicyXmlException {
        int numOverrides = systemPolicyOverrideIntermediate.size();
        if (numOverrides == 0) {
            return null;
        }
        if (numOverrides > 1) {
            throw new PolicyXmlException("only one system power policy is supported: "
                    + numOverrides + " system policies exist");
        }
        if (!systemPolicyOverrideIntermediate.get(0).policyId.equals(
                POWER_POLICY_ID_NO_USER_INTERACTION)) {
            throw new PolicyXmlException("system power policy id should be "
                    + POWER_POLICY_ID_NO_USER_INTERACTION);
        }

        CarPowerPolicy policyOverride =
                toCarPowerPolicy(systemPolicyOverrideIntermediate.get(0), customComponents);

        Set<Integer> visited = new ArraySet<>();
        checkSystemPowerPolicyComponents(policyOverride.getEnabledComponents(), visited);
        checkSystemPowerPolicyComponents(policyOverride.getDisabledComponents(), visited);
        return policyOverride;
    }

    private IntermediateCarPowerPolicy parsePolicy(XmlPullParser parser, String policyId,
            boolean includeOtherComponents)
            throws PolicyXmlException, IOException, XmlPullParserException {
        ArrayMap<String, Boolean> components = new ArrayMap<>();
        String behavior = POWER_ONOFF_UNTOUCHED;
        boolean otherComponentsProcessed = false;
        int type;
        while ((type = parser.next()) != END_DOCUMENT && type != END_TAG) {
            if (type != START_TAG) continue;
            if (TAG_COMPONENT.equals(parser.getName())) {
                String powerComponent = parser.getAttributeValue(NAMESPACE, ATTR_ID);
                String state = getText(parser);
                switch (state) {
                    case POWER_ONOFF_ON:
                        components.put(powerComponent, true);
                        break;
                    case POWER_ONOFF_OFF:
                        components.put(powerComponent, false);
                        break;
                    default:
                        throw new PolicyXmlException(
                                "target state(" + state + ") for " + powerComponent
                                        + " is not valid");
                }
                skip(parser);
            } else if (TAG_OTHER_COMPONENTS.equals(parser.getName())) {
                if (!includeOtherComponents) {
                    throw new PolicyXmlException("|" + TAG_OTHER_COMPONENTS
                            + "| tag is not expected");
                }
                if (otherComponentsProcessed) {
                    throw new PolicyXmlException("more than one |" + TAG_OTHER_COMPONENTS
                            + "| tag");
                }
                otherComponentsProcessed = true;
                behavior = parser.getAttributeValue(NAMESPACE, ATTR_BEHAVIOR);
                if (behavior == null) {
                    throw new PolicyXmlException("no |" + ATTR_BEHAVIOR + "| attribute of |"
                            + TAG_OTHER_COMPONENTS + "| tag");
                }
                switch (behavior) {
                    case POWER_ONOFF_ON:
                    case POWER_ONOFF_OFF:
                    case POWER_ONOFF_UNTOUCHED:
                        break;
                    default:
                        throw new PolicyXmlException("invalid value(" + behavior + ") in |"
                                + ATTR_BEHAVIOR + "| attribute of |" + TAG_OTHER_COMPONENTS
                                + "| tag");
                }
                skip(parser);
            } else {
                throw new PolicyXmlException("unknown tag: " + parser.getName() + " under "
                        + TAG_POLICY);
            }
        }
        return new IntermediateCarPowerPolicy(policyId, components, behavior);
    }

    private CarPowerPolicy toCarPowerPolicy(IntermediateCarPowerPolicy intermediatePolicy,
            ArrayMap<String, Integer> customComponents)
            throws PolicyXmlException {
        SparseBooleanArray components = new SparseBooleanArray();

        // Convert string values of IntermediateCarPowerPolicy to a CarPowerPolicy
        ArrayMap<String, Boolean> intermediatePolicyComponents = intermediatePolicy.components;
        for (int i = 0; i < intermediatePolicyComponents.size(); i++) {
            String componentId = intermediatePolicyComponents.keyAt(i);

            int powerComponent = toPowerComponent(componentId, true);
            if (powerComponent == INVALID_POWER_COMPONENT) {
                powerComponent = toCustomPowerComponentId(componentId, customComponents);
            }
            if (powerComponent == INVALID_POWER_COMPONENT) {
                throw new PolicyXmlException(" Unknown component id : " + componentId);
            }

            if (components.indexOfKey(powerComponent) >= 0) {
                throw new PolicyXmlException(
                        "invalid value(" + componentId + ") in |" + ATTR_ID + "| attribute of |"
                                + TAG_COMPONENT
                                + "| tag");
            }
            components.put(powerComponent, intermediatePolicyComponents.valueAt(i));
        }

        boolean enabled;
        boolean untouched = false;

        if (POWER_ONOFF_ON.equals(intermediatePolicy.otherBehavior)) {
            enabled = true;
        } else if (POWER_ONOFF_OFF.equals(intermediatePolicy.otherBehavior)) {
            enabled = false;
        } else {
            enabled = false;
            untouched = true;
        }
        if (!untouched) {
            for (int component = FIRST_POWER_COMPONENT;
                    component <= LAST_POWER_COMPONENT; component++) {
                if (components.indexOfKey(component) >= 0) continue;
                components.put(component, enabled);
            }
            for (int i = 0; i < customComponents.size(); ++i) {
                int componentId = customComponents.valueAt(i);
                if (components.indexOfKey(componentId) < 0) { // key not found
                    components.put(componentId, enabled);
                }
            }
        }
        return new CarPowerPolicy(intermediatePolicy.policyId, toIntArray(components, true),
                toIntArray(components, false));
    }

    private int toCustomPowerComponentId(String id, ArrayMap<String, Integer> customComponents) {
        return customComponents.getOrDefault(id, INVALID_POWER_COMPONENT);
    }

    private SparseArray<String> parsePolicyGroup(XmlPullParser parser) throws PolicyXmlException,
            XmlPullParserException, IOException {
        SparseArray<String> policyGroup = new SparseArray<>();
        int type;
        Set<Integer> visited = new ArraySet<>();
        while ((type = parser.next()) != END_DOCUMENT && type != END_TAG) {
            if (type != START_TAG) continue;
            if (TAG_DEFAULT_POLICY.equals(parser.getName())) {
                String id = parser.getAttributeValue(NAMESPACE, ATTR_ID);
                if (id == null || id.isEmpty()) {
                    throw new PolicyXmlException("no |" + ATTR_ID + "| attribute of |"
                            + TAG_DEFAULT_POLICY + "| tag");
                }
                String state = parser.getAttributeValue(NAMESPACE, ATTR_STATE);
                int powerState = toPowerState(state);
                if (powerState == INVALID_POWER_STATE) {
                    throw new PolicyXmlException("invalid value(" + state + ") in |" + ATTR_STATE
                            + "| attribute of |" + TAG_DEFAULT_POLICY + "| tag");
                }
                if (visited.contains(powerState)) {
                    throw new PolicyXmlException("power state(" + state
                            + ") is specified more than once");
                }
                policyGroup.put(powerState, id);
                visited.add(powerState);
                skip(parser);
            } else if (TAG_NO_DEFAULT_POLICY.equals(parser.getName())) {
                String state = parser.getAttributeValue(NAMESPACE, ATTR_STATE);
                int powerState = toPowerState(state);
                if (powerState == INVALID_POWER_STATE) {
                    throw new PolicyXmlException("invalid value(" + state + ") in |" + ATTR_STATE
                            + "| attribute of |" + TAG_DEFAULT_POLICY + "| tag");
                }
                if (visited.contains(powerState)) {
                    throw new PolicyXmlException("power state(" + state
                            + ") is specified more than once");
                }
                visited.add(powerState);
                skip(parser);
            } else {
                throw new PolicyXmlException("unknown tag: " + parser.getName() + " under "
                        + TAG_POLICY_GROUP);
            }
        }
        return policyGroup;
    }

    private ArrayMap<String, CarPowerPolicy> validatePowerPolicies(
            List<IntermediateCarPowerPolicy> intermediateCarPowerPolicies,
            ArrayMap<String, Integer> customComponents) throws PolicyXmlException {
        ArrayMap<String, CarPowerPolicy> powerPolicies = new ArrayMap<>();
        for (int index = 0; index < intermediateCarPowerPolicies.size(); ++index) {
            IntermediateCarPowerPolicy intermediateCarPowerPolicy =
                    intermediateCarPowerPolicies.get(index);
            powerPolicies.put(intermediateCarPowerPolicy.policyId,
                    toCarPowerPolicy(intermediateCarPowerPolicy, customComponents));
        }
        return powerPolicies;
    }

    private void validatePolicyGroups(ArrayMap<String, SparseArray<String>> policyGroups,
            ArrayMap<String, CarPowerPolicy> registeredPolicies, String defaultGroupPolicyId)
            throws PolicyXmlException {
        for (Map.Entry<String, SparseArray<String>> entry : policyGroups.entrySet()) {
            SparseArray<String> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                String policyId = group.valueAt(i);
                if (!registeredPolicies.containsKey(group.valueAt(i))) {
                    throw new PolicyXmlException("group(id: " + entry.getKey()
                            + ") contains invalid policy(id: " + policyId + ")");
                }
            }
        }

        if ((defaultGroupPolicyId == null || defaultGroupPolicyId.isEmpty())
                && !policyGroups.isEmpty()) {
            Log.w(TAG, "No defaultGroupPolicyId is defined");
        }

        if (defaultGroupPolicyId != null && !policyGroups.containsKey(defaultGroupPolicyId)) {
            throw new PolicyXmlException(
                    "defaultGroupPolicyId is defined, but group with this ID doesn't exist ");
        }
    }

    private void reconstructSystemPowerPolicy(@Nullable CarPowerPolicy policyOverride) {
        if (policyOverride == null) return;

        List<Integer> enabledComponents = Arrays.stream(NO_USER_INTERACTION_ENABLED_COMPONENTS)
                .boxed().collect(Collectors.toList());
        List<Integer> disabledComponents = Arrays.stream(NO_USER_INTERACTION_DISABLED_COMPONENTS)
                .boxed().collect(Collectors.toList());
        int[] overrideEnabledComponents = policyOverride.getEnabledComponents();
        int[] overrideDisabledComponents = policyOverride.getDisabledComponents();
        for (int i = 0; i < overrideEnabledComponents.length; i++) {
            removeComponent(disabledComponents, overrideEnabledComponents[i]);
            addComponent(enabledComponents, overrideEnabledComponents[i]);
        }
        for (int i = 0; i < overrideDisabledComponents.length; i++) {
            removeComponent(enabledComponents, overrideDisabledComponents[i]);
            addComponent(disabledComponents, overrideDisabledComponents[i]);
        }
        mPreemptivePowerPolicies.put(POWER_POLICY_ID_NO_USER_INTERACTION,
                new CarPowerPolicy(POWER_POLICY_ID_NO_USER_INTERACTION,
                        CarServiceUtils.toIntArray(enabledComponents),
                        CarServiceUtils.toIntArray(disabledComponents)));
    }

    private void registerBasicPowerPolicies() {
        mRegisteredPowerPolicies.put(POWER_POLICY_ID_ALL_ON, POWER_POLICY_ALL_ON);
        mRegisteredPowerPolicies.put(POWER_POLICY_ID_INITIAL_ON, POWER_POLICY_INITIAL_ON);
    }

    private void removeComponent(List<Integer> components, int component) {
        int index = components.lastIndexOf(component);
        if (index != -1) {
            components.remove(index);
        }
    }

    private void addComponent(List<Integer> components, int component) {
        int index = components.lastIndexOf(component);
        if (index == -1) {
            components.add(component);
        }
    }

    private String getText(XmlPullParser parser) throws PolicyXmlException, XmlPullParserException,
            IOException {
        if (parser.getEventType() != START_TAG) {
            throw new PolicyXmlException("tag pair doesn't match");
        }
        parser.next();
        if (parser.getEventType() != TEXT) {
            throw new PolicyXmlException("tag value is not found");
        }
        return parser.getText();
    }

    private void skip(XmlPullParser parser) throws PolicyXmlException, XmlPullParserException,
            IOException {
        int type = parser.getEventType();
        if (type != START_TAG && type != TEXT) {
            throw new PolicyXmlException("tag pair doesn't match");
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case END_TAG:
                    depth--;
                    break;
                case START_TAG:
                    depth++;
                    break;
                default:
                    break;
            }
        }
    }

    void checkSystemPowerPolicyComponents(int[] components, Set<Integer> visited) throws
            PolicyXmlException {
        for (int i = 0; i < components.length; i++) {
            int component = components[i];
            if (!isOverridableComponent(component)) {
                throw new PolicyXmlException("Power component(" + powerComponentToString(component)
                        + ") cannot be overridden");
            }
            if (visited.contains(component)) {
                throw new PolicyXmlException("Power component(" + powerComponentToString(component)
                        + ") is specified more than once");
            }
            visited.add(component);
        }
    }

    boolean isOverridableComponent(int component) {
        return component >= MINIMUM_CUSTOM_COMPONENT_VALUE // custom components are overridable
            || SYSTEM_POLICY_CONFIGURABLE_COMPONENTS.contains(component);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private String componentsToString(int[] components) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) buffer.append(", ");
            buffer.append(powerComponentToString(components[i]));
        }
        return buffer.toString();
    }

    @PolicyOperationStatus.ErrorCode
    int parseComponents(String[] componentArr, boolean enabled, SparseBooleanArray components) {
        ArrayList<Integer> customComponentIds = new ArrayList<>();
        for (int i = 0; i < componentArr.length; i++) {
            int component = toPowerComponent(componentArr[i], false);
            if (component == INVALID_POWER_COMPONENT) {
                try {
                    component = Integer.parseInt(componentArr[i]);
                } catch (NumberFormatException e) {
                    Slogf.e(TAG, "Error parsing component ID " + e.toString());
                    return PolicyOperationStatus.ERROR_INVALID_POWER_COMPONENT;
                }

                if (component < MINIMUM_CUSTOM_COMPONENT_VALUE) {
                    int error = PolicyOperationStatus.ERROR_INVALID_POWER_COMPONENT;
                    Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, componentArr[i]));
                    return error;
                }
            }
            if (components.indexOfKey(component) >= 0) {
                int error = PolicyOperationStatus.ERROR_DUPLICATED_POWER_COMPONENT;
                Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, componentArr[i]));
                return error;
            }
            components.put(component, enabled);
            customComponentIds.add(component);
        }
        for (int i = 0; i < customComponentIds.size(); ++i) {
            int componentId = customComponentIds.get(i);
            // Add only new components
            if (!mCustomComponents.containsValue(componentId)) {
                mCustomComponents.put(String.valueOf(componentId), componentId);
            }
        }
        return PolicyOperationStatus.OK;
    }

    static int toPowerState(String state) {
        if (state == null) {
            return INVALID_POWER_STATE;
        }
        switch (state) {
            case POWER_STATE_WAIT_FOR_VHAL:
                return VehicleApPowerStateReport.WAIT_FOR_VHAL;
            case POWER_STATE_ON:
                return VehicleApPowerStateReport.ON;
            default:
                return INVALID_POWER_STATE;
        }
    }

    static String vhalPowerStateToString(int state) {
        switch (state) {
            case VehicleApPowerStateReport.WAIT_FOR_VHAL:
                return POWER_STATE_WAIT_FOR_VHAL;
            case VehicleApPowerStateReport.ON:
                return POWER_STATE_ON;
            default:
                return "unknown power state";
        }
    }

    static boolean isSystemPowerPolicy(String policyId) {
        return policyId == null ? false : policyId.startsWith(SYSTEM_POWER_POLICY_PREFIX);
    }

    private static int[] toIntArray(SparseBooleanArray array, boolean value) {
        int arraySize = array.size();
        int returnSize = 0;
        for (int i = 0; i < arraySize; i++) {
            if (array.valueAt(i) == value) returnSize++;
        }
        int[] ret = new int[returnSize];
        int count = 0;
        for (int i = 0; i < arraySize; i++) {
            if (array.valueAt(i) == value) {
                ret[count++] = array.keyAt(i);
            }
        }
        return ret;
    }

    private static boolean containsComponent(int[] arr, int component) {
        for (int element : arr) {
            if (element == component) return true;
        }
        return false;
    }

    ArrayMap<String, Integer> getCustomComponents() {
        return mCustomComponents;
    }

    @VisibleForTesting
    Set<Integer> getAllComponents() {
        Set<Integer> allComponents = new ArraySet<>(Lists.asImmutableList(ALL_COMPONENTS));
        allComponents.addAll(mCustomComponents.values());
        return allComponents;
    }

    @VisibleForTesting
    static final class PolicyXmlException extends Exception {
        PolicyXmlException(String message) {
            super(message);
        }
    }

    private static final class IntermediateCarPowerPolicy {
        public final String policyId;
        public final ArrayMap<String, Boolean> components;
        public final String otherBehavior;

        IntermediateCarPowerPolicy(String policyId, ArrayMap<String, Boolean> components,
                String behavior) {
            this.policyId = policyId;
            this.components = components;
            this.otherBehavior = behavior;
        }
    }
}
