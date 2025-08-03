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

import static java.util.stream.Collectors.toList;

import com.android.csuite.core.ModuleInfoProvider.ModuleInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import com.google.common.truth.Correspondence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public final class DirectoryBasedModuleInfoProviderTest {
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void get_directoryUnset_returnsEmptyStream() throws Exception {
        DirectoryBasedModuleInfoProvider provider = createProvider();

        Stream<ModuleInfo> modules = provider.get(createIConfig());

        assertThat(modules.collect(toList())).isEmpty();
    }

    @Test
    public void get_directoryIsEmpty_returnsEmptyStream() throws Exception {
        DirectoryBasedModuleInfoProvider provider = createProvider(createDirectoryWithApkFiles());

        Stream<ModuleInfo> modules = provider.get(createIConfig());

        assertThat(modules.collect(toList())).isEmpty();
    }

    @Test
    public void get_directoryContainsApks_returnsModule() throws Exception {
        DirectoryBasedModuleInfoProvider provider =
                createProvider(createDirectoryWithApkFiles("package1.apk", "package2.apk"));

        Stream<ModuleInfo> modules = provider.get(createIConfig());

        assertThat(modules.collect(toList()))
                .comparingElementsUsing(MODULE_NAME_CORRESPONDENCE)
                .containsExactly("package1.apk", "package2.apk");
    }

    @Test
    public void get_directoryContainsNonApk_ignoreNonApk() throws Exception {
        DirectoryBasedModuleInfoProvider provider =
                createProvider(createDirectoryWithApkFiles("package.apk", "not_package.not_apk"));

        Stream<ModuleInfo> modules = provider.get(createIConfig());

        assertThat(modules.collect(toList()))
                .comparingElementsUsing(MODULE_NAME_CORRESPONDENCE)
                .containsExactly("package.apk");
    }

    @Test
    public void get_directoryContainsApk_packageInstallFilePlaceholderIsSubstituted()
            throws Exception {
        String apkFileName = "package.apk";
        Path apkDir = createDirectoryWithApkFiles(apkFileName);
        DirectoryBasedModuleInfoProvider provider = createProvider(apkDir);
        IConfiguration config =
                createIConfigWithTemplate(
                        DirectoryBasedModuleInfoProvider.PACKAGE_INSTALL_FILE_PLACEHOLDER);

        Stream<ModuleInfo> modules = provider.get(config);

        assertThat(modules.collect(toList()))
                .comparingElementsUsing(MODULE_CONTENT_CORRESPONDENCE)
                .containsExactly(apkDir.resolve(apkFileName).toString());
    }

    @Test
    public void get_directoryContainsApk_packagePlaceholderIsSubstituted() throws Exception {
        String packageName = "package.name";
        DirectoryBasedModuleInfoProvider provider =
                createProviderWithParser(
                        apk -> packageName, createDirectoryWithApkFiles("package.apk"));
        IConfiguration config =
                createIConfigWithTemplate(DirectoryBasedModuleInfoProvider.PACKAGE_PLACEHOLDER);

        Stream<ModuleInfo> modules = provider.get(config);

        assertThat(modules.collect(toList()))
                .comparingElementsUsing(MODULE_CONTENT_CORRESPONDENCE)
                .containsExactly(packageName);
    }

    private DirectoryBasedModuleInfoProvider createProvider(Path... paths)
            throws ConfigurationException {
        return createProviderWithParser(apk -> "package.name", paths);
    }

    private DirectoryBasedModuleInfoProvider createProviderWithParser(
            DirectoryBasedModuleInfoProvider.PackageNameParser parser, Path... paths)
            throws ConfigurationException {
        DirectoryBasedModuleInfoProvider provider = new DirectoryBasedModuleInfoProvider(parser);
        OptionSetter optionSetter = new OptionSetter(provider);
        for (Path dir : paths) {
            optionSetter.setOptionValue(
                    DirectoryBasedModuleInfoProvider.DIRECTORY_OPTION, dir.toString());
        }
        return provider;
    }

    private Path createDirectoryWithApkFiles(String... apkFileNames) throws IOException {
        Path tempDir = Files.createTempDirectory(tempFolder.getRoot().toPath(), "apks");
        for (String apkFileName : apkFileNames) {
            Files.createFile(tempDir.resolve(apkFileName));
        }
        return tempDir;
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

    private static final Correspondence<ModuleInfo, String> MODULE_NAME_CORRESPONDENCE =
            Correspondence.transforming(ModuleInfo::getName, "module name");
    private static final Correspondence<ModuleInfo, String> MODULE_CONTENT_CORRESPONDENCE =
            Correspondence.transforming(ModuleInfo::getContent, "module name");

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
