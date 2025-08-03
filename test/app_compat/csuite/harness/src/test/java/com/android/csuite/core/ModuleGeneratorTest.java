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

import static org.testng.Assert.assertThrows;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public final class ModuleGeneratorTest {
    private static final String TEST_PACKAGE_NAME1 = "test.package.name1";
    private static final String TEST_PACKAGE_NAME2 = "test.package.name2";
    private static final String TEST_PACKAGE_NAME3 = "test.package.name3";
    private static final Exception NO_EXCEPTION = null;

    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());

    @Test
    public void tearDown_nonModuleFilesExist_doesNotDelete() throws Exception {
        Path testsDir = createTestsDir();
        Path nonModule = Files.createFile(testsDir.resolve("a"));
        ModuleGenerator generator = new GeneratorBuilder().setTestsDir(testsDir).build();

        generator.tearDown(createTestInfo(), NO_EXCEPTION);

        assertThatListDirectory(testsDir).containsExactly(nonModule);
    }

    @Test
    public void tearDown_preserveNonGeneratedModules_doesNotDelete() throws Exception {
        Path testsDir = createTestsDir();
        Path nonGeneratedModule =
                Files.createFile(
                        testsDir.resolve("b" + ModuleGenerator.MODULE_FILE_NAME_EXTENSION));
        ModuleGenerator sut = new GeneratorBuilder().setTestsDir(testsDir).build();
        OptionSetter option = new OptionSetter(sut);
        option.setOptionValue(ModuleGenerator.OPTION_PRESERVE_EXISTING_MODULES, "true");

        sut.tearDown(createTestInfo(), NO_EXCEPTION);

        assertThatListDirectory(testsDir).containsExactly(nonGeneratedModule);
    }

    @Test
    public void tearDown_doesNotPreserveNonGeneratedModules_delete() throws Exception {
        Path testsDir = createTestsDir();
        Path nonGeneratedModule =
                Files.createFile(
                        testsDir.resolve("b" + ModuleGenerator.MODULE_FILE_NAME_EXTENSION));
        ModuleGenerator sut = new GeneratorBuilder().setTestsDir(testsDir).build();
        OptionSetter option = new OptionSetter(sut);
        option.setOptionValue(ModuleGenerator.OPTION_PRESERVE_EXISTING_MODULES, "false");

        sut.tearDown(createTestInfo(), NO_EXCEPTION);

        assertThatListDirectory(testsDir).doesNotContain(nonGeneratedModule);
    }

    @Test
    public void tearDown_packageNamesProvided_deletesGeneratedModules() throws Exception {
        Path testsDir = createTestsDir();
        ModuleGenerator generator1 =
                new GeneratorBuilder()
                        .setTestsDir(testsDir)
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME1, "")))
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME2, "")))
                        .build();
        generator1.split();
        ModuleGenerator generator2 = new GeneratorBuilder().setTestsDir(testsDir).build();

        generator2.tearDown(createTestInfo(), NO_EXCEPTION);

        assertThatListDirectory(testsDir).isEmpty();
    }

    @Test
    public void tearDown_moduleInfoNotProvided_doesNotThrowError() throws Exception {
        ModuleGenerator generator = new GeneratorBuilder().setTestsDir(createTestsDir()).build();
        generator.split();

        generator.tearDown(createTestInfo(), NO_EXCEPTION);
    }

    @Test
    public void split_moduleInfoStreamProvided_streamIsClosed() throws Exception {
        AtomicBoolean wasClosed = new AtomicBoolean(false);
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(createTestsDir())
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                        new ModuleInfoProvider.ModuleInfo(
                                                                TEST_PACKAGE_NAME1, ""))
                                                .onClose(() -> wasClosed.set(true)))
                        .build();

        generator.split();

        assertThat(wasClosed.get()).isTrue();
    }

    @Test
    public void split_moduleInfoProvidersSpecified_contentIsWritten() throws Exception {
        Path testsDir = createTestsDir();
        String content1 = "a";
        String content2 = "b";
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(testsDir)
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME1, content1)))
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME2, content2)))
                        .build();

        generator.split();

        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME1).contains(content1);
        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME2).contains(content2);
    }

    @Test
    public void split_emptyModuleNameProvided_throwsException() throws Exception {
        Path testsDir = createTestsDir();
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(testsDir)
                        .addModuleInfoProvider(
                                config -> Stream.of(new ModuleInfoProvider.ModuleInfo(" ", "a")))
                        .build();

        assertThrows(IllegalArgumentException.class, () -> generator.split());
    }

    @Test
    public void split_duplicatedModuleNamesProvided_throwsException() throws Exception {
        Path testsDir = createTestsDir();
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(testsDir)
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME1, "a")))
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME1, "b")))
                        .build();

        assertThrows(IllegalArgumentException.class, () -> generator.split());
    }

    @Test
    public void split_moduleInfoProvidersSpecified_generateModulesForAll() throws Exception {
        Path testsDir = createTestsDir();
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(testsDir)
                        .addModuleInfoProvider(
                                config ->
                                        Arrays.asList(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME1, ""),
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME2, ""))
                                                .stream())
                        .addModuleInfoProvider(
                                config ->
                                        Stream.of(
                                                new ModuleInfoProvider.ModuleInfo(
                                                        TEST_PACKAGE_NAME3, "")))
                        .build();

        generator.split();

        assertThatListDirectory(testsDir)
                .containsExactly(
                        getModuleConfigFile(testsDir, TEST_PACKAGE_NAME1),
                        getModuleConfigFile(testsDir, TEST_PACKAGE_NAME2),
                        getModuleConfigFile(testsDir, TEST_PACKAGE_NAME3));
    }

    @Test
    public void split_streamThrowsException_throwsException() throws Exception {
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(createTestsDir())
                        .addModuleInfoProvider(
                                config ->
                                        Arrays.stream(new String[] {"a"})
                                                .map(
                                                        i -> {
                                                            throw new UncheckedIOException(
                                                                    new IOException());
                                                        }))
                        .build();

        assertThrows(UncheckedIOException.class, () -> generator.split());
    }

    @Test
    public void split_providerThrowsException_throwsException() throws Exception {
        ModuleGenerator generator =
                new GeneratorBuilder()
                        .setTestsDir(createTestsDir())
                        .addModuleInfoProvider(
                                config -> {
                                    throw new UncheckedIOException(new IOException());
                                })
                        .build();

        assertThrows(UncheckedIOException.class, () -> generator.split());
    }

    @Test
    public void split_noProviders_doesNotGenerate() throws Exception {
        Path testsDir = createTestsDir();
        ModuleGenerator generator = new GeneratorBuilder().setTestsDir(testsDir).build();

        generator.split();

        assertThatListDirectory(testsDir).isEmpty();
    }

    private static StringSubject assertThatModuleConfigFileContent(
            Path testsDir, String packageName) throws IOException {
        return assertThat(
                new String(Files.readAllBytes(getModuleConfigFile(testsDir, packageName))));
    }

    private static IterableSubject assertThatListDirectory(Path dir) throws IOException {
        // Convert stream to list because com.google.common.truth.Truth8 is not available.
        return assertThat(
                Files.walk(dir)
                        .filter(p -> !p.equals(dir))
                        .collect(ImmutableList.toImmutableList()));
    }

    private static Path getModuleConfigFile(Path baseDir, String packageName) {
        return baseDir.resolve(packageName + ".config");
    }

    private Path createTestsDir() throws IOException {
        Path rootPath = mFileSystem.getPath("csuite");
        Files.createDirectories(rootPath);
        return Files.createTempDirectory(rootPath, "testDir");
    }

    private static final class GeneratorBuilder {
        private final List<ModuleInfoProvider> mModuleInfoProviders = new ArrayList<>();
        private Path mTestsDir;

        GeneratorBuilder addModuleInfoProvider(ModuleInfoProvider moduleInfoProviders) {
            mModuleInfoProviders.add(moduleInfoProviders);
            return this;
        }

        GeneratorBuilder setTestsDir(Path testsDir) {
            mTestsDir = testsDir;
            return this;
        }

        ModuleGenerator build() throws Exception {
            ModuleGenerator generator = new ModuleGenerator(buildInfo -> mTestsDir);

            IConfiguration configuration = new Configuration("name", "description");
            configuration.setConfigurationObjectList(
                    ModuleInfoProvider.MODULE_INFO_PROVIDER_OBJECT_TYPE, mModuleInfoProviders);
            generator.setConfiguration(configuration);

            return generator;
        }
    }

    private static TestInformation createTestInfo() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", Mockito.mock(ITestDevice.class));
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }
}
