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
package com.android.tradefed.config.yaml;

import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.yaml.IDefaultObjectLoader.LoaderConfiguration;
import com.android.tradefed.config.yaml.YamlClassOptionsParser.ClassAndOptions;

import com.google.common.collect.ImmutableList;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.Set;

/** Parser for YAML style Tradefed configurations */
public final class ConfigurationYamlParser {

    private static final String DESCRIPTION_KEY = "description";
    public static final String PRE_SETUP_ACTION_KEY = "pre_setup_action";
    public static final String TARGET_PREPARERS_KEY = "target_preparers";
    public static final String DEPENDENCIES_KEY = "dependencies";
    public static final String TESTS_KEY = "tests";

    private static final List<String> REQUIRED_KEYS =
            ImmutableList.of(DESCRIPTION_KEY, DEPENDENCIES_KEY, TESTS_KEY);
    private Set<String> mSeenKeys = new HashSet<>();
    private boolean mCreatedAsModule = false;

    /**
     * Main entry point of the parser to parse a given YAML file into Trade Federation objects.
     *
     * @param configDef
     * @param source
     * @param yamlInput
     * @param createdAsModule
     */
    public void parse(
            ConfigurationDef configDef,
            String source,
            InputStream yamlInput,
            boolean createdAsModule)
            throws ConfigurationException {
        mCreatedAsModule = createdAsModule;
        // We don't support multi-device in YAML
        configDef.setMultiDeviceMode(false);
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try {
            configDef.addOptionDef(
                    CommandOptions.TEST_TAG_OPTION,
                    null,
                    source,
                    source,
                    Configuration.CMD_OPTIONS_TYPE_NAME);
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlObjects = (Map<String, Object>) yaml.load(yamlInput);
            translateYamlInTradefed(configDef, yamlObjects);
        } catch (YAMLException e) {
            throw new ConfigurationException(
                    String.format("Failed to parse yaml file: '%s'.", source), e);
        }
    }

    private void translateYamlInTradefed(
            ConfigurationDef configDef, Map<String, Object> yamlObjects)
            throws ConfigurationException {
        if (yamlObjects.containsKey(DESCRIPTION_KEY)) {
            configDef.setDescription((String) yamlObjects.get(DESCRIPTION_KEY));
            mSeenKeys.add(DESCRIPTION_KEY);
        }
        YamlClassOptionsParser presetupClassAndOptions = null;
        if (yamlObjects.containsKey(PRE_SETUP_ACTION_KEY)) {
            @SuppressWarnings("unchecked")
            List<Object> objList = (List<Object>) yamlObjects.get(PRE_SETUP_ACTION_KEY);
            presetupClassAndOptions = new YamlClassOptionsParser(objList);
            mSeenKeys.add(PRE_SETUP_ACTION_KEY);
        }
        Set<String> dependencyFiles = new LinkedHashSet<>();
        YamlTestDependencies testDeps = null;
        if (yamlObjects.containsKey(DEPENDENCIES_KEY)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> objList =
                    (List<Map<String, Object>>) yamlObjects.get(DEPENDENCIES_KEY);
            testDeps = new YamlTestDependencies(objList);
            dependencyFiles = collectDependencyFiles(testDeps);
            mSeenKeys.add(DEPENDENCIES_KEY);
        }
        YamlClassOptionsParser runnerInfo = null;
        if (yamlObjects.containsKey(TESTS_KEY)) {
            @SuppressWarnings("unchecked")
            List<Object> objList = (List<Object>) yamlObjects.get(TESTS_KEY);
            runnerInfo = new YamlClassOptionsParser(objList);
            mSeenKeys.add(TESTS_KEY);
        }
        YamlClassOptionsParser preparerInfo = null;
        if (yamlObjects.containsKey(TARGET_PREPARERS_KEY)) {
            @SuppressWarnings("unchecked")
            List<Object> objList = (List<Object>) yamlObjects.get(TARGET_PREPARERS_KEY);
            preparerInfo = new YamlClassOptionsParser(objList);
            mSeenKeys.add(TARGET_PREPARERS_KEY);
        }
        if (!mSeenKeys.containsAll(REQUIRED_KEYS)) {
            Set<String> missingKeys = new HashSet<>(REQUIRED_KEYS);
            missingKeys.removeAll(mSeenKeys);
            throw new ConfigurationException(
                    String.format("'%s' keys are required and were not found.", missingKeys));
        }

        // Add default configured objects first.
        LoaderConfiguration loadConfiguration = new LoaderConfiguration();
        loadConfiguration
                .setConfigurationDef(configDef)
                .addDependencies(dependencyFiles)
                .setCreatedAsModule(mCreatedAsModule);
        ServiceLoader<IDefaultObjectLoader> serviceLoader =
                ServiceLoader.load(IDefaultObjectLoader.class);
        for (IDefaultObjectLoader loader : serviceLoader) {
            loader.addDefaultObjects(loadConfiguration);
        }

        // Add objects.
        if (presetupClassAndOptions != null) {
            convertClassAndOptionsToObjects(
                    configDef,
                    presetupClassAndOptions.getClassesAndOptions(),
                    Configuration.TARGET_PREPARER_TYPE_NAME);
        }
        if (testDeps != null) {
            convertDependenciesToObjects(configDef, testDeps);
        }
        if (runnerInfo != null) {
            convertClassAndOptionsToObjects(
                    configDef, runnerInfo.getClassesAndOptions(), Configuration.TEST_TYPE_NAME);
        }
        if (preparerInfo != null) {
            convertClassAndOptionsToObjects(
                    configDef,
                    preparerInfo.getClassesAndOptions(),
                    Configuration.TARGET_PREPARER_TYPE_NAME);
        }
    }

    /**
     * Collect dependency files.
     *
     * @return returns a list of all the dependency files.
     */
    private Set<String> collectDependencyFiles(YamlTestDependencies testDeps) {
        Set<String> dependencies = new LinkedHashSet<>();

        // Add apks.
        List<String> apks = testDeps.apks();
        dependencies.addAll(apks);

        // Add device files.
        Map<String, String> deviceFiles = testDeps.deviceFiles();
        for (Entry<String, String> toPush : deviceFiles.entrySet()) {
            dependencies.add(toPush.getKey());
        }

        // Add the non-apk and non-device files.
        dependencies.addAll(testDeps.files());
        return dependencies;
    }

    /**
     * Converts the test dependencies into target_preparer objects.
     *
     * <p>TODO: Figure out a more robust way to map to target_preparers options.
     */
    private void convertDependenciesToObjects(ConfigurationDef def, YamlTestDependencies testDeps) {
        List<String> apks = testDeps.apks();
        if (!apks.isEmpty()) {
            String className = "com.android.tradefed.targetprep.suite.SuiteApkInstaller";
            int classCount =
                    def.addConfigObjectDef(Configuration.TARGET_PREPARER_TYPE_NAME, className);
            String optionName =
                    String.format(
                            "%s%c%d%c%s",
                            className,
                            OptionSetter.NAMESPACE_SEPARATOR,
                            classCount,
                            OptionSetter.NAMESPACE_SEPARATOR,
                            "test-file-name");
            for (String apk : apks) {
                def.addOptionDef(
                        optionName,
                        null,
                        apk,
                        def.getName(),
                        Configuration.TARGET_PREPARER_TYPE_NAME);
            }
        }

        Map<String, String> deviceFiles = testDeps.deviceFiles();
        if (!deviceFiles.isEmpty()) {
            String className = "com.android.tradefed.targetprep.PushFilePreparer";
            int classCount =
                    def.addConfigObjectDef(Configuration.TARGET_PREPARER_TYPE_NAME, className);
            String optionName =
                    String.format(
                            "%s%c%d%c%s",
                            className,
                            OptionSetter.NAMESPACE_SEPARATOR,
                            classCount,
                            OptionSetter.NAMESPACE_SEPARATOR,
                            "push-file");
            for (Entry<String, String> toPush : deviceFiles.entrySet()) {
                def.addOptionDef(
                        optionName,
                        toPush.getKey(),
                        toPush.getValue(),
                        def.getName(),
                        Configuration.TARGET_PREPARER_TYPE_NAME);
            }
        }
    }

    private void convertClassAndOptionsToObjects(
            ConfigurationDef def, List<ClassAndOptions> classAndOptionsList, String configObjType) {
        if (classAndOptionsList.isEmpty()) {
            return;
        }
        for (ClassAndOptions classOptions : classAndOptionsList) {
            String className = classOptions.mClass;
            int classCount = def.addConfigObjectDef(configObjType, className);
            for (Entry<String, String> options : classOptions.mOptions.entries()) {
                String optionName =
                        String.format(
                                "%s%c%d%c%s",
                                className,
                                OptionSetter.NAMESPACE_SEPARATOR,
                                classCount,
                                OptionSetter.NAMESPACE_SEPARATOR,
                                options.getKey());
                def.addOptionDef(
                        optionName, null, options.getValue(), def.getName(), configObjType);
            }
        }
    }
}
