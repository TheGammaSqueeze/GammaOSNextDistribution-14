/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.spectatio.configs;

import android.platform.spectatio.constants.JsonConfigConstants;

import com.google.common.collect.Sets;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpectatioConfig {
    @SerializedName("COMMANDS")
    private Map<String, String> mCommands = new HashMap<String, String>();

    @SerializedName("PACKAGES")
    private Map<String, String> mPackages = new HashMap<String, String>();

    @SerializedName("ACTIONS")
    private Map<String, String> mActions = new HashMap<String, String>();

    @SerializedName("UI_ELEMENTS")
    private Map<String, UiElement> mUiElements = new HashMap<String, UiElement>();

    @SerializedName("WORKFLOWS")
    private Map<String, List<WorkflowTask>> mWorkflows = new HashMap<String, List<WorkflowTask>>();

    public Map<String, String> getCommands() {
        return mCommands;
    }

    public Map<String, String> getPackages() {
        return mPackages;
    }

    public Map<String, String> getActions() {
        return mActions;
    }

    public Map<String, UiElement> getUiElements() {
        return mUiElements;
    }

    public Map<String, List<WorkflowTask>> getWorkflows() {
        return mWorkflows;
    }

    /**
     * Update config values for runtime and gas jsons. ThrowErrorForNewKeys should be true for
     * runtime jsons
     */
    public void updateConfig(SpectatioConfig newSpectatioConfig, boolean throwErrorForNewKeys) {
        // Update Config with values from Runtime/GAS Json Config
        updateConfigValues(mCommands, newSpectatioConfig.getCommands(), throwErrorForNewKeys);

        // Update Config with values from Runtime/GAS Json Config
        updateConfigValues(mPackages, newSpectatioConfig.getPackages(), throwErrorForNewKeys);

        // Update Config with values from Runtime/GAS Json Config
        updateConfigValues(mActions, newSpectatioConfig.getActions(), throwErrorForNewKeys);

        // Update Config with values from Runtime/GAS Json Config
        updateConfigValues(mUiElements, newSpectatioConfig.getUiElements(), throwErrorForNewKeys);

        // Update Config with values from Runtime/GAS Json Config
        updateConfigValues(mWorkflows, newSpectatioConfig.getWorkflows(), throwErrorForNewKeys);
    }

    private <T> void updateConfigValues(
            Map<String, T> currentConfigMap,
            Map<String, T> newConfigMap,
            boolean throwErrorForNewKeys) {
        if (currentConfigMap == null || newConfigMap == null) {
            throw new RuntimeException(
                    "Validate Spectatio Json Config as it does not allow null values.");
        }
        if (throwErrorForNewKeys) {
            Sets.SetView<String> newKeys =
                    Sets.difference(newConfigMap.keySet(), currentConfigMap.keySet());
            if (!newKeys.isEmpty()) {
                throw new RuntimeException(
                        String.format(
                                "Unknown keys in runtime config: %s.",
                                newKeys.stream().collect(Collectors.joining(", "))));
            }
        }
        currentConfigMap.putAll(newConfigMap);
    }

    private <T> T getValueFromConfig(Map<String, T> config, String name, String type) {
        if (config == null || !config.containsKey(name) || config.get(name) == null) {
            throw new RuntimeException(
                    String.format(
                            "Validate Spectatio JSON config as it does not have %s with key %s.",
                            type, name));
        }
        return config.get(name);
    }

    public String getActionFromConfig(String actionName) {
        return getValueFromConfig(mActions, actionName, JsonConfigConstants.ACTIONS);
    }

    public String getCommandFromConfig(String commandName) {
        return getValueFromConfig(mCommands, commandName, JsonConfigConstants.COMMANDS);
    }

    public String getPackageFromConfig(String packageName) {
        return getValueFromConfig(mPackages, packageName, JsonConfigConstants.PACKAGES);
    }

    /** Instantiate a UI element specifier from the config values at the specified key */
    public UiElement getUiElementFromConfig(String uiElementName) {
        return getValueFromConfig(mUiElements, uiElementName, JsonConfigConstants.UI_ELEMENTS);
    }

    public List<WorkflowTask> getWorkflowFromConfig(String workflowName) {
        return getValueFromConfig(mWorkflows, workflowName, JsonConfigConstants.WORKFLOWS);
    }
}
