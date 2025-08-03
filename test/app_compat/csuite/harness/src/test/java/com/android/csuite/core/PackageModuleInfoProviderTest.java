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

package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public final class PackageModuleInfoProviderTest {
    private static final String PACKAGE_NAME_1 = "a";
    private static final String PACKAGE_NAME_2 = "b";

    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void get_templateContainsPlaceholders_replacesPlaceholdersInOutput() throws Exception {
        final String content = "hello placeholder%s%s world";
        PackageModuleInfoProvider provider =
                new ProviderBuilder().addPackage(PACKAGE_NAME_1).addPackage(PACKAGE_NAME_2).build();
        IConfiguration config =
                createIConfigWithTemplate(
                        String.format(
                                content,
                                PackagesFileModuleInfoProvider.PACKAGE_PLACEHOLDER,
                                PackagesFileModuleInfoProvider.PACKAGE_PLACEHOLDER));

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(config);

        assertThat(collectModuleContentStrings(modulesInfo))
                .containsExactly(
                        String.format(content, PACKAGE_NAME_1, PACKAGE_NAME_1),
                        String.format(content, PACKAGE_NAME_2, PACKAGE_NAME_2));
    }

    @Test
    public void get_containsDuplicatedPackageNames_ignoreDuplicates() throws Exception {
        PackageModuleInfoProvider provider =
                new ProviderBuilder()
                        .addPackage(PACKAGE_NAME_1)
                        .addPackage(PACKAGE_NAME_1)
                        .addPackage(PACKAGE_NAME_2)
                        .build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(PACKAGE_NAME_1, PACKAGE_NAME_2);
    }

    @Test
    public void get_containsDuplicatedAltPackageNamesAndUseAlt_ignoreDuplicates() throws Exception {
        PackageModuleInfoProvider provider =
                new ProviderBuilder()
                        .setUseAltPackage(true)
                        .addAltPackage(PACKAGE_NAME_1)
                        .addAltPackage(PACKAGE_NAME_1)
                        .addAltPackage(PACKAGE_NAME_2)
                        .build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(PACKAGE_NAME_1, PACKAGE_NAME_2);
    }

    @Test
    public void get_packageNamesProvided_returnsPackageNames() throws Exception {
        PackageModuleInfoProvider provider =
                new ProviderBuilder().addPackage(PACKAGE_NAME_1).addPackage(PACKAGE_NAME_2).build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(PACKAGE_NAME_1, PACKAGE_NAME_2);
    }

    @Test
    public void get_bothPackageNamesAndAltPackageNamesProvidedAndUseAlt_returnsAltPackageNames()
            throws Exception {
        PackageModuleInfoProvider provider =
                new ProviderBuilder()
                        .setUseAltPackage(true)
                        .addPackage(PACKAGE_NAME_1)
                        .addAltPackage(PACKAGE_NAME_2)
                        .build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(PACKAGE_NAME_2);
    }

    @Test
    public void get_bothPackageNamesAndAltPackageNamesProvided_returnsPackageNames()
            throws Exception {
        PackageModuleInfoProvider provider =
                new ProviderBuilder()
                        .addPackage(PACKAGE_NAME_1)
                        .addAltPackage(PACKAGE_NAME_2)
                        .build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(PACKAGE_NAME_1);
    }

    @Test
    public void get_altPackageNamesProvidedAndUseAlt_returnsAltPackageNames() throws Exception {
        PackageModuleInfoProvider provider =
                new ProviderBuilder()
                        .setUseAltPackage(true)
                        .addAltPackage(PACKAGE_NAME_1)
                        .addAltPackage(PACKAGE_NAME_2)
                        .build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(PACKAGE_NAME_1, PACKAGE_NAME_2);
    }

    private List<String> collectModuleContentStrings(
            Stream<ModuleInfoProvider.ModuleInfo> modulesInfo) {
        return modulesInfo
                .map(ModuleInfoProvider.ModuleInfo::getContent)
                .collect(Collectors.toList());
    }

    private List<String> collectModuleNames(Stream<ModuleInfoProvider.ModuleInfo> modulesInfo) {
        return modulesInfo.map(ModuleInfoProvider.ModuleInfo::getName).collect(Collectors.toList());
    }

    private static final class ProviderBuilder {
        private final Set<String> mPackages = new HashSet<>();
        private final Set<String> mAltPackages = new HashSet<>();
        private boolean mUseAltPackage = false;

        ProviderBuilder addPackage(String packageName) {
            mPackages.add(packageName);
            return this;
        }

        ProviderBuilder addAltPackage(String packageName) {
            mAltPackages.add(packageName);
            return this;
        }

        ProviderBuilder setUseAltPackage(boolean useAltPackage) {
            this.mUseAltPackage = useAltPackage;
            return this;
        }

        PackageModuleInfoProvider build() throws Exception {
            // Creates a new instance for each build() call.
            PackageModuleInfoProvider provider = new PackageModuleInfoProvider();
            OptionSetter optionSetter = new OptionSetter(provider);
            if (mUseAltPackage) {
                optionSetter.setOptionValue(
                        PackageModuleInfoProvider.USE_ALT_PACKAGE_OPTION, "true");
            }
            for (String p : mAltPackages) {
                optionSetter.setOptionValue(PackageModuleInfoProvider.ALT_PACKAGE_OPTION, p);
            }
            for (String p : mPackages) {
                optionSetter.setOptionValue(PackageModuleInfoProvider.PACKAGE_OPTION, p);
            }
            return provider;
        }
    }

    private IConfiguration createIConfig() throws ConfigurationException {
        return createIConfigWithTemplate(MODULE_TEMPLATE_CONTENT);
    }

    private IConfiguration createIConfigWithTemplate(String template)
            throws ConfigurationException {
        IConfiguration configuration = new Configuration("name", "description");
        configuration.setConfigurationObject(
                ModuleTemplate.MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE,
                createModuleTemplate(template));
        return configuration;
    }

    private ModuleTemplate createModuleTemplate(String template) throws ConfigurationException {
        ModuleTemplate moduleTemplate = new ModuleTemplate(resource -> template);
        new OptionSetter(moduleTemplate)
                .setOptionValue(ModuleTemplate.DEFAULT_TEMPLATE_OPTION, "path.xml.template");
        new OptionSetter(moduleTemplate).setOptionValue(ModuleTemplate.TEMPLATE_ROOT_OPTION, "");
        return moduleTemplate;
    }

    private static final String MODULE_TEMPLATE_CONTENT =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<configuration description=\"description\">\n"
                    + "    <option name=\"package-name\" value=\"{package}\"/>\n"
                    + "    <target_generator class=\"some.generator.class\">\n"
                    + "        <option name=\"test-file-name\" value=\"app://{package}\"/>\n"
                    + "    </target_generator>\n"
                    + "    <test class=\"some.test.class\"/>\n"
                    + "</configuration>";
}
