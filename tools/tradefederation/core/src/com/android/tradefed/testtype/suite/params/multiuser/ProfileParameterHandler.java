/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tradefed.testtype.suite.params.multiuser;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.ProfileTargetPreparer;
import com.android.tradefed.targetprep.RunOnSystemUserTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestAnnotationFilterReceiver;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.testtype.suite.module.IModuleController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Base parameter handler for any profile user. */
public abstract class ProfileParameterHandler {

    private final String mRequireRunOnProfileAnnotation;
    private final ProfileTargetPreparer mProfileTargetPreparer;
    private final List<IModuleController> mModuleControllers;

    ProfileParameterHandler(
            String requireRunOnProfileAnnotation, ProfileTargetPreparer profileTargetPreparer) {
        this(requireRunOnProfileAnnotation, profileTargetPreparer, new ArrayList<>());
    }

    ProfileParameterHandler(
            String requireRunOnProfileAnnotation,
            ProfileTargetPreparer profileTargetPreparer,
            List<IModuleController> moduleControllers) {
        mRequireRunOnProfileAnnotation = requireRunOnProfileAnnotation;
        mProfileTargetPreparer = profileTargetPreparer;
        mModuleControllers = moduleControllers;
    }

    public void addParameterSpecificConfig(IConfiguration moduleConfiguration) {
        for (IDeviceConfiguration deviceConfig : moduleConfiguration.getDeviceConfig()) {
            List<ITargetPreparer> preparers = deviceConfig.getTargetPreparers();
            // The first thing the module will do is run on a given profile
            preparers.add(0, mProfileTargetPreparer);

            // Remove the target preparer which forces onto system user
            preparers.removeIf(preparer -> preparer instanceof RunOnSystemUserTargetPreparer);
        }
    }

    public void applySetup(IConfiguration moduleConfiguration) {
        addModuleControllersToConfiguration(moduleConfiguration, mModuleControllers);

        // Add filter to include @RequireRunOnXXXProfile
        for (IRemoteTest test : moduleConfiguration.getTests()) {
            if (test instanceof ITestAnnotationFilterReceiver) {
                ITestAnnotationFilterReceiver filterTest = (ITestAnnotationFilterReceiver) test;
                filterTest.clearIncludeAnnotations();
                filterTest.addIncludeAnnotation(mRequireRunOnProfileAnnotation);

                Set<String> excludeAnnotations = new HashSet<>(filterTest.getExcludeAnnotations());
                excludeAnnotations.remove(mRequireRunOnProfileAnnotation);
                filterTest.clearExcludeAnnotations();
                filterTest.addAllExcludeAnnotation(excludeAnnotations);
            }
        }
    }

    private void addModuleControllersToConfiguration(
            IConfiguration moduleConfiguration, List<IModuleController> moduleControllers) {
        if (moduleControllers == null || moduleControllers.isEmpty()) {
            return;
        }

        List<IModuleController> ctrlObjectList =
                (List<IModuleController>)
                        moduleConfiguration.getConfigurationObjectList(
                                ModuleDefinition.MODULE_CONTROLLER);

        if (ctrlObjectList == null) {
            ctrlObjectList = new ArrayList<>();
        }

        Set<IModuleController> ctrlObjectSet = new HashSet<>(ctrlObjectList);

        for (IModuleController moduleController : moduleControllers) {
            ctrlObjectSet.add(moduleController);
        }

        try {
            moduleConfiguration.setConfigurationObjectList(
                    ModuleDefinition.MODULE_CONTROLLER, new ArrayList<>(ctrlObjectSet));
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
