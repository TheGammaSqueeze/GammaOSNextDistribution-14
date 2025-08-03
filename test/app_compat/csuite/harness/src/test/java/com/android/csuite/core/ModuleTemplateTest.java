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

package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.csuite.core.ModuleTemplate.ResourceLoader;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public final class ModuleTemplateTest {
    @Test
    public void substitute_multipleReplacementPairs_replaceAll() throws Exception {
        String template = "-ab";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("a", "c", "b", "d"));

        assertThat(content).isEqualTo("-cd");
    }

    @Test
    public void substitute_replacementKeyNotInTemplate_doesNotReplace() throws Exception {
        String template = "-a";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("b", ""));

        assertThat(content).isEqualTo(template);
    }

    @Test
    public void substitute_multipleReplacementKeyInTemplate_replaceTheKeys() throws Exception {
        String template = "-aba";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("a", "c"));

        assertThat(content).isEqualTo("-cbc");
    }

    @Test
    public void substitute_noReplacementPairs_returnTemplate() throws Exception {
        String template = "-a";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of());

        assertThat(content).isEqualTo(template);
    }

    @Test
    public void substitute_templateContentIsEmpty_returnEmptyString() throws Exception {
        String template = "";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("a", "b"));

        assertThat(content).isEqualTo(template);
    }

    @Test
    public void substitute_templateMapsSpecified_useTemplateMaps() throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(Map.of("module1", "template1"))
                        .addExtraTemplatePath("template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .setExtraTemplateContent(
                                "template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION, "a")
                        .build();
        ModuleTemplate sut = ModuleTemplate.loadFrom(config);

        String content = sut.substitute("module1", Map.of("a", "b"));

        assertThat(content).isEqualTo("b");
    }

    @Test
    public void substitute_templateFileIsInADirectory_canFindTheTemplates() throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(Map.of("module1", "dir/template1"))
                        .addExtraTemplatePath(
                                "dir/template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .setExtraTemplateContent(
                                "dir/template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION, "a")
                        .build();
        ModuleTemplate sut = ModuleTemplate.loadFrom(config);

        String content = sut.substitute("module1", Map.of("a", "b"));

        assertThat(content).isEqualTo("b");
    }

    @Test
    public void loadFrom_templateMappingContainsNonexistTemplates_throwsException()
            throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(Map.of("module1", "template1"))
                        .build();

        assertThrows(IllegalArgumentException.class, () -> ModuleTemplate.loadFrom(config));
    }

    @Test
    public void loadFrom_templateMappingContainsExistingExtraTemplates_doesNotThrow()
            throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(Map.of("module1", "template1"))
                        .addExtraTemplatePath("template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .setExtraTemplateContent(
                                "template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION, "")
                        .build();

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_templateMappingContainsXmlExtension_doesNotThrow() throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(
                                Map.of(
                                        "module1",
                                        "template1"
                                                + ModuleTemplate.XML_FILE_EXTENSION.toUpperCase()))
                        .addExtraTemplatePath(
                                "template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION.toLowerCase())
                        .setExtraTemplateContent(
                                "template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION.toLowerCase(),
                                "")
                        .build();

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_templateMappingContainsCaseMismatchingXmlExtension_doesNotThrow()
            throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(
                                Map.of("module1", "template1" + ModuleTemplate.XML_FILE_EXTENSION))
                        .addExtraTemplatePath("template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .setExtraTemplateContent(
                                "template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION, "")
                        .build();

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_templateMappingContainsDefaultTemplate_doesNotThrow() throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(Map.of("module1", "default_template"))
                        .build();

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_duplicateTemplateMappingEntries_throwsException() throws Exception {
        IConfiguration config =
                new ConfigurationBuilder(
                                "", "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .addTemplateMappings(Map.of("module1", "template1"))
                        .addTemplateMappings(Map.of("module1", "template1"))
                        .addExtraTemplatePath("template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .setExtraTemplateContent(
                                "template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION, "")
                        .build();

        assertThrows(IllegalArgumentException.class, () -> ModuleTemplate.loadFrom(config));
    }

    private static ModuleTemplate createTestSubject(String defaultTemplate)
            throws IOException, ConfigurationException {
        return ModuleTemplate.loadFrom(
                new ConfigurationBuilder(
                                defaultTemplate,
                                "any_path" + ModuleTemplate.TEMPLATE_FILE_EXTENSION)
                        .build());
    }

    private static final class ConfigurationBuilder {
        private final String mDefaultTemplateContent;
        private final String mDefaultTemplatePath;
        private String mTemplateRoot = "";
        private Map<String, String> mExtraTemplateContents = new HashMap<>();
        private List<String> mExtraTemplatePaths = new ArrayList<>();
        private List<Map<String, String>> mTemplateMappings = new ArrayList<>();

        ConfigurationBuilder(String defaultTemplateContent, String defaultTemplatePath) {
            mDefaultTemplateContent = defaultTemplateContent;
            mDefaultTemplatePath = defaultTemplatePath;
        }

        ConfigurationBuilder setExtraTemplateContent(String path, String content) {
            mExtraTemplateContents.put(path, content);
            return this;
        }

        ConfigurationBuilder addExtraTemplatePath(String path) {
            mExtraTemplatePaths.add(path);
            return this;
        }

        ConfigurationBuilder addTemplateMappings(Map<String, String> map) {
            mTemplateMappings.add(map);
            return this;
        }

        IConfiguration build() throws ConfigurationException {
            IConfiguration configuration = new Configuration("name", "description");

            ResourceLoader resourceLoader =
                    path -> {
                        if (mExtraTemplateContents.containsKey(path)) {
                            return mExtraTemplateContents.get(path);
                        }
                        return mDefaultTemplateContent;
                    };

            ModuleTemplate moduleTemplate = new ModuleTemplate(resourceLoader);
            OptionSetter optionSetter = new OptionSetter(moduleTemplate);
            optionSetter.setOptionValue(
                    ModuleTemplate.DEFAULT_TEMPLATE_OPTION, mDefaultTemplatePath);
            optionSetter.setOptionValue(ModuleTemplate.TEMPLATE_ROOT_OPTION, mTemplateRoot);
            for (String extraTemplate : mExtraTemplatePaths) {
                optionSetter.setOptionValue(ModuleTemplate.EXTRA_TEMPLATES_OPTION, extraTemplate);
            }
            configuration.setConfigurationObject(
                    ModuleTemplate.MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE, moduleTemplate);

            if (!mTemplateMappings.isEmpty()) {
                List<TemplateMappingProvider> list = new ArrayList<>();
                mTemplateMappings.forEach(map -> list.add(() -> map.entrySet().stream()));
                configuration.setConfigurationObjectList(
                        TemplateMappingProvider.TEMPLATE_MAPPING_PROVIDER_OBJECT_TYPE, list);
            }

            return configuration;
        }
    }
}
