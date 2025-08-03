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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.MustBeClosed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates TradeFed suite modules during runtime.
 *
 * <p>This class generates module config files into TradeFed's test directory at runtime using a
 * template. Since the content of the test directory relies on what is being generated in a test
 * run, there can only be one instance executing at a given time.
 *
 * <p>The intention of this class is to generate test modules at the beginning of a test run and
 * cleans up after all tests finish, which resembles a target preparer. However, a target preparer
 * is executed after the sharding process has finished. The only way to make the generated modules
 * available for sharding without making changes to TradeFed's core code is to disguise this module
 * generator as an instance of IShardableTest and declare it separately in test plan config. This is
 * hacky, and in the long term a TradeFed centered solution is desired. For more details, see
 * go/sharding-hack-for-module-gen. Note that since the generate step is executed as a test instance
 * and cleanup step is executed as a target preparer, there should be no saved states between
 * generating and cleaning up module files.
 *
 * <p>This module generator collects modules' info from all ModuleInfoProvider objects specified in
 * the test plan config.
 *
 * <h2>Syntax and usage</h2>
 *
 * <p>References to module info providers in TradeFed test plan config must have the following
 * syntax:
 *
 * <blockquote>
 *
 * <b>&lt;object type="MODULE_INFO_PROVIDER" class="</b><i>provider_class_name</i><b>"/&gt;</b>
 *
 * </blockquote>
 *
 * where <i>provider_class_name</i> is the fully-qualified class name of an ModuleInfoProvider
 * implementation class.
 */
public final class ModuleGenerator
        implements IRemoteTest,
                IShardableTest,
                IBuildReceiver,
                IConfigurationReceiver,
                ITargetPreparer {
    @VisibleForTesting static final String MODULE_FILE_NAME_EXTENSION = ".config";

    @VisibleForTesting
    static final String OPTION_PRESERVE_EXISTING_MODULES = "preserve-existing-modules";

    @Option(
            name = OPTION_PRESERVE_EXISTING_MODULES,
            description =
                    "Whether to preserve non-generated modules existing in the csuite jar build. By"
                        + " default, these modules will be deleted before test to prevent confusion"
                        + " with the generated modules unless this option is set true.",
            importance = Importance.NEVER)
    private boolean mPreserveExistingModules = false;

    private static final Collection<IRemoteTest> NOT_SPLITTABLE = null;

    private final TestDirectoryProvider mTestDirectoryProvider;
    private IBuildInfo mBuildInfo;
    private IConfiguration mConfiguration;

    public ModuleGenerator() {
        this(buildInfo -> new CompatibilityBuildHelper(buildInfo).getTestsDir().toPath());
    }

    @VisibleForTesting
    ModuleGenerator(TestDirectoryProvider testDirectoryProvider) {
        mTestDirectoryProvider = testDirectoryProvider;
    }

    @Override
    public void setUp(TestInformation testInfo) {
        // Do not add cleanup code here as this method is executed after the split method.
    }

    /**
     * Cleans up the generated test modules files.
     *
     * <p>Note that this method does not execute from the same instance of this class that generates
     * the modules so be careful when using any class fields.
     */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        // Gets build info from test info as when the class is executed as a ITargetPreparer
        // preparer, it is not considered as a IBuildReceiver instance.
        mBuildInfo = testInfo.getBuildInfo();

        try {
            deleteModuleFiles();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private void deleteModuleFiles() throws IOException {
        Files.list(mTestDirectoryProvider.get(mBuildInfo))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(MODULE_FILE_NAME_EXTENSION))
                .filter(
                        path -> {
                            if (!mPreserveExistingModules) {
                                return true;
                            }
                            try {
                                return Files.readString(path).contains(GENERATED_MODULE_NOTE);
                            } catch (IOException ioException) {
                                throw new UncheckedIOException(ioException);
                            }
                        })
                .forEach(
                        path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException ioException) {
                                throw new UncheckedIOException(ioException);
                            }
                        });
    }

    private void generateModules() throws IOException {
        deleteModuleFiles();
        try (Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = getModulesInfo()) {
            Set<String> moduleNames = new HashSet<>();
            modulesInfo.forEachOrdered(
                    moduleInfo -> {
                        String moduleName = moduleInfo.getName().trim();
                        if (moduleName.isEmpty()) {
                            throw new IllegalArgumentException("Module name cannot be empty.");
                        }

                        if (moduleNames.contains(moduleName)) {
                            throw new IllegalArgumentException(
                                    "Duplicated module name: " + moduleName);
                        }

                        try {
                            Files.write(
                                    getModulePath(moduleName),
                                    (moduleInfo.getContent() + GENERATED_MODULE_NOTE).getBytes());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }

                        moduleNames.add(moduleName);
                    });
        }
    }

    @MustBeClosed
    @SuppressWarnings("MustBeClosedChecker")
    private Stream<ModuleInfoProvider.ModuleInfo> getModulesInfo() {
        List<?> configurations =
                mConfiguration.getConfigurationObjectList(
                        ModuleInfoProvider.MODULE_INFO_PROVIDER_OBJECT_TYPE);
        Preconditions.checkNotNull(
                configurations, "Missing " + ModuleInfoProvider.MODULE_INFO_PROVIDER_OBJECT_TYPE);
        return configurations.stream()
                .map(obj -> (ModuleInfoProvider) obj)
                .flatMap(
                        info -> {
                            try {
                                return info.get(mConfiguration);
                            } catch (IOException ioException) {
                                throw new UncheckedIOException(ioException);
                            }
                        });
    }

    /**
     * Generates test modules. Note that the implementation of this method is not related to
     * sharding in any way.
     */
    @Override
    public Collection<IRemoteTest> split() {
        try {
            generateModules();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
        return NOT_SPLITTABLE;
    }

    private Path getModulePath(String moduleName) throws IOException {
        return mTestDirectoryProvider
                .get(mBuildInfo)
                .resolve(moduleName + MODULE_FILE_NAME_EXTENSION);
    }

    @Override
    public void run(final TestInformation testInfo, final ITestInvocationListener listener) {
        // Intentionally left blank since this class is not really a test.
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    @VisibleForTesting
    interface TestDirectoryProvider {
        Path get(IBuildInfo buildInfo) throws IOException;
    }

    @VisibleForTesting
    static final String GENERATED_MODULE_NOTE =
            "<!-- Note: The content of this module is auto generated from a template. Please do"
                    + " not modify manually. -->\n";
}
