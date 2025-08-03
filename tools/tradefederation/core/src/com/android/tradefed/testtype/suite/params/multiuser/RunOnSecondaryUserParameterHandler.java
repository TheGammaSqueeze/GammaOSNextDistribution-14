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

package com.android.tradefed.testtype.suite.params.multiuser;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.RunOnSecondaryUserTargetPreparer;
import com.android.tradefed.targetprep.RunOnSystemUserTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestAnnotationFilterReceiver;
import com.android.tradefed.testtype.suite.params.IModuleParameterHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunOnSecondaryUserParameterHandler implements IModuleParameterHandler {

    private static final String REQUIRE_RUN_ON_SECONDARY_USER_NAME =
            "com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser";

    @Override
    public String getParameterIdentifier() {
        return "run-on-secondary-user";
    }

    /** {@inheritDoc} */
    @Override
    public void addParameterSpecificConfig(IConfiguration moduleConfiguration) {
        for (IDeviceConfiguration deviceConfig : moduleConfiguration.getDeviceConfig()) {
            List<ITargetPreparer> preparers = deviceConfig.getTargetPreparers();
            // The first thing the module will do is run on a work profile
            preparers.add(0, new RunOnSecondaryUserTargetPreparer());

            // Remove the target preparer which forces onto system user
            preparers.removeIf(preparer -> preparer instanceof RunOnSystemUserTargetPreparer);
        }
    }

    @Override
    public void applySetup(IConfiguration moduleConfiguration) {
        // Add filter to include @RequireRunOnSecondaryUser
        for (IRemoteTest test : moduleConfiguration.getTests()) {
            if (test instanceof ITestAnnotationFilterReceiver) {
                ITestAnnotationFilterReceiver filterTest = (ITestAnnotationFilterReceiver) test;
                filterTest.clearIncludeAnnotations();
                filterTest.addIncludeAnnotation(REQUIRE_RUN_ON_SECONDARY_USER_NAME);

                Set<String> excludeAnnotations = new HashSet<>(filterTest.getExcludeAnnotations());
                excludeAnnotations.remove(REQUIRE_RUN_ON_SECONDARY_USER_NAME);
                filterTest.clearExcludeAnnotations();
                filterTest.addAllExcludeAnnotation(excludeAnnotations);
            }
        }
    }
}
