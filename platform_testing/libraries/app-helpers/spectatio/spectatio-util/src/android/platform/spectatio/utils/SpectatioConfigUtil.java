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

package android.platform.spectatio.utils;

import android.platform.spectatio.configs.ScrollConfig;
import android.platform.spectatio.configs.SpectatioConfig;
import android.platform.spectatio.configs.UiElement;
import android.platform.spectatio.configs.WorkflowTask;
import android.platform.spectatio.configs.WorkflowTaskConfig;
import android.platform.spectatio.configs.validators.ValidateMapEntries;
import android.platform.spectatio.configs.validators.ValidateScrollConfig;
import android.platform.spectatio.configs.validators.ValidateSpectatioConfigForUnknownProperties;
import android.platform.spectatio.configs.validators.ValidateUiElement;
import android.platform.spectatio.configs.validators.ValidateWorkflowTask;
import android.platform.spectatio.configs.validators.ValidateWorkflowTaskConfig;
import android.util.Log;

import androidx.test.uiautomator.BySelector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SpectatioConfigUtil {
    private static final String LOG_TAG = SpectatioConfigUtil.class.getSimpleName();

    private static SpectatioConfigUtil sSpectatioConfigUtil = null;

    // Path for default configuration ( Loaded from Resources )
    private static final String DEFAULT_CONFIG_PATH = "/assets/defaultSpectatioConfig.json";

    // Path for gas default configuration ( Loaded from Resources )
    private static final String DEFAULT_GAS_CONFIG_PATH = "/assets/gasDefaultSpectatioConfig.json";

    // Path for runtime configuration ( Loaded from device Path )
    private static final String RUNTIME_CONFIG_PATH = "/data/local/tmp/runtimeSpectatioConfig.json";

    private SpectatioConfig mSpectatioConfig;

    private Gson mGson;

    private SpectatioConfigUtil() {
        mGson =
                new GsonBuilder()
                        .registerTypeAdapterFactory(
                                new ValidateSpectatioConfigForUnknownProperties())
                        .registerTypeAdapter(UiElement.class, new ValidateUiElement())
                        .registerTypeAdapter(
                                WorkflowTaskConfig.class, new ValidateWorkflowTaskConfig())
                        .registerTypeAdapter(ScrollConfig.class, new ValidateScrollConfig())
                        .registerTypeAdapter(WorkflowTask.class, new ValidateWorkflowTask())
                        .registerTypeAdapter(
                                new TypeToken<Map<String, String>>() {}.getType(),
                                new ValidateMapEntries())
                        .create();

        mSpectatioConfig = loadDefaultConfiguration(DEFAULT_CONFIG_PATH);

        if (mSpectatioConfig == null) {
            // Default config was not provided so skip loading gas/runtime config
            Log.w(LOG_TAG, String.format("Default config not present, so exiting"));
            return;
        }

        SpectatioConfig mGasSpectatioConfig = loadDefaultConfiguration(DEFAULT_GAS_CONFIG_PATH);
        // Load Gas Config
        if (mGasSpectatioConfig != null) {
            // If gas config is available, merge the gas config values to default
            mSpectatioConfig.updateConfig(mGasSpectatioConfig, /* throwErrorForNewKeys= */ false);
        }

        // Load Runtime Config ( if runtimeSpectatioConfig.json file exist on the device )
        SpectatioConfig runtimeSpectatioConfig = loadRuntimeConfiguration();

        if (runtimeSpectatioConfig != null) {
            // If runtime config is available, update default config with new values
            mSpectatioConfig.updateConfig(runtimeSpectatioConfig, /* throwErrorForNewKeys= */ true);
        }
    }

    public static SpectatioConfigUtil getInstance() {
        if (sSpectatioConfigUtil == null) {
            sSpectatioConfigUtil = new SpectatioConfigUtil();
        }
        return sSpectatioConfigUtil;
    }

    private SpectatioConfig loadDefaultConfiguration(String path) {
        Log.i(LOG_TAG, String.format("Loading default config: %s", path));

        // Read default config file
        String defaultConfigData;
        try {
            defaultConfigData = getResourceFileDataFromPath(path);
        } catch (IOException ex) {
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Unable to read default config: %s, Error: %s", path, ex.getMessage()));
            throw new RuntimeException(
                    String.format("Unable to read default config: %s", path), ex);
        }

        // Check if config file data is valid
        if (!isValidConfigFileData(defaultConfigData)) {
            // Ignore if config file is not provided
            Log.w(LOG_TAG, String.format("Default config not available: %s", path));
            return null;
        }

        SpectatioConfig defaultSpectatioConfig =
                mGson.fromJson(defaultConfigData, new TypeToken<SpectatioConfig>() {}.getType());

        Log.i(LOG_TAG, String.format("Done loading default config: %s", path));
        return defaultSpectatioConfig;
    }

    private SpectatioConfig loadRuntimeConfiguration() {
        Log.i(LOG_TAG, String.format("Loading runtime config: %s", RUNTIME_CONFIG_PATH));

        // Read runtime config file
        String runtimeConfigData = null;
        try {
            runtimeConfigData = readDataFromConfigFile(RUNTIME_CONFIG_PATH);
        } catch (IOException ex) {
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Unable to read runtime config: %s, Error: %s",
                            RUNTIME_CONFIG_PATH, ex.getMessage()));
            throw new RuntimeException(
                    String.format("Unable to read runtime config: %s", RUNTIME_CONFIG_PATH), ex);
        }

        // If file does not exist, data will be null. Skip Processing
        if (runtimeConfigData == null) {
            Log.i(
                    LOG_TAG,
                    String.format(
                            "Runtime Config %s Not Provided. Skip Loading.", RUNTIME_CONFIG_PATH));
            return null;
        }

        // Check if config file data is valid
        if (!isValidConfigFileData(runtimeConfigData)) {
            throw new RuntimeException(
                    String.format("Runtime config file %s is empty.", RUNTIME_CONFIG_PATH));
        }

        SpectatioConfig runtimeSpectatioConfig =
                mGson.fromJson(runtimeConfigData, new TypeToken<SpectatioConfig>() {}.getType());

        Log.i(LOG_TAG, String.format("Done loading runtime config: %s", RUNTIME_CONFIG_PATH));
        return runtimeSpectatioConfig;
    }

    /** Read data from runtime config file i.e. runtimeSpectatioConfig.json */
    private String readDataFromConfigFile(String configFilePath) throws IOException {
        File configFile = new File(configFilePath);
        if (configFile == null || !configFile.exists()) {
            Log.i(
                    LOG_TAG,
                    String.format(
                            "Skip loading Runtime config %s. Config not available.",
                            configFilePath));
            return null;
        }
        if (!configFile.canRead()) {
            throw new RuntimeException(
                    String.format("Cannot read runtime config %s", configFilePath));
        }
        return readResourceFileFromStream(new FileReader(configFile));
    }

    private boolean isValidConfigFileData(String configFileData) {
        return !(configFileData == null || configFileData.trim().isEmpty());
    }

    /** Get Resource File Data From Path i.e. defaultSpectatioConfig.json */
    private String getResourceFileDataFromPath(String resourceFilePath) throws IOException {
        try (InputStream resourceAsStream = getClass().getResourceAsStream(resourceFilePath)) {
            if (resourceAsStream == null) {
                // Ignore if default config is not provided
                Log.w(LOG_TAG, String.format("Resource file not available: %s", resourceFilePath));
                return "";
            }
            return readResourceFileFromStream(
                    new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8));
        }
    }

    private String readResourceFileFromStream(InputStreamReader inputStreamReader)
            throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            StringBuilder resourceFileData = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                resourceFileData.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            return resourceFileData.toString();
        }
    }

    private void validateSpectatioConfig() {
        if (mSpectatioConfig == null) {
            throw new RuntimeException("Spectatio Config must be provided to use this method.");
        }
    }

    public String getActionFromConfig(String actionName) {
        validateSpectatioConfig();
        return mSpectatioConfig.getActionFromConfig(actionName);
    }

    public String getCommandFromConfig(String commandName) {
        validateSpectatioConfig();
        return mSpectatioConfig.getCommandFromConfig(commandName);
    }

    public String getPackageFromConfig(String packageName) {
        validateSpectatioConfig();
        return mSpectatioConfig.getPackageFromConfig(packageName);
    }

    public BySelector getUiElementFromConfig(String uiElementName) {
        validateSpectatioConfig();
        UiElement uiElement = mSpectatioConfig.getUiElementFromConfig(uiElementName);
        return uiElement.getBySelectorForUiElement();
    }

    public void executeWorkflow(String workflowName, SpectatioUiUtil spectatioUiUtil) {
        validateSpectatioConfig();
        Log.i(LOG_TAG, String.format("Executing Workflow %s", workflowName));
        List<WorkflowTask> tasks = mSpectatioConfig.getWorkflowFromConfig(workflowName);
        for (WorkflowTask task : tasks) {
            task.executeTask(workflowName, spectatioUiUtil);
        }
        Log.i(LOG_TAG, String.format("Done Executing Workflow %s", workflowName));
    }
}
