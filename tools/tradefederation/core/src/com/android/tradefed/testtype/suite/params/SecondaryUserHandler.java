/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.testtype.suite.params;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.targetprep.CreateUserPreparer;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.RunCommandTargetPreparer;
import com.android.tradefed.targetprep.VisibleBackgroundUserPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestAnnotationFilterReceiver;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/** Handler for {@link ModuleParameters#SECONDARY_USER}. */
public class SecondaryUserHandler implements IModuleParameterHandler {

    @VisibleForTesting
    static final List<String> LOCATION_COMMANDS =
            Arrays.asList(
                    "settings put secure location_providers_allowed +network",
                    "settings put secure location_providers_allowed +gps");

    private final boolean mStartUserVisibleOnBackground;
    private final @Nullable Integer mDisplayId;

    public SecondaryUserHandler() {
        this(/*startUserVisibleOnBackground= */ false);
    }

    protected SecondaryUserHandler(boolean startUserVisibleOnBackground) {
        this(startUserVisibleOnBackground, /* displayId= */ null);
    }

    protected SecondaryUserHandler(boolean startUserVisibleOnBackground, Integer displayId) {
        mStartUserVisibleOnBackground = startUserVisibleOnBackground;
        mDisplayId = displayId;
    }

    @Override
    public String getParameterIdentifier() {
        return "secondary_user";
    }

    @Override
    public final void addParameterSpecificConfig(IConfiguration moduleConfiguration) {
        for (IDeviceConfiguration deviceConfig : moduleConfiguration.getDeviceConfig()) {
            List<ITargetPreparer> preparers = deviceConfig.getTargetPreparers();
            // The first things module will do is switch to a secondary user
            ITargetPreparer userPreparer;
            if (mStartUserVisibleOnBackground) {
                userPreparer = new VisibleBackgroundUserPreparer();
                if (mDisplayId != null) {
                    ((VisibleBackgroundUserPreparer) userPreparer).setDisplayId(mDisplayId);
                }
            } else {
                userPreparer = new CreateUserPreparer();
            }
            preparers.add(0, userPreparer);
            // Add a preparer to setup the location settings on the new user
            RunCommandTargetPreparer locationPreparer = new RunCommandTargetPreparer();
            LOCATION_COMMANDS.forEach(cmd -> locationPreparer.addRunCommand(cmd));
            preparers.add(1, locationPreparer);
        }
    }

    @Override
    public final void applySetup(IConfiguration moduleConfiguration) {
        // Add filter to exclude @SystemUserOnly
        for (IRemoteTest test : moduleConfiguration.getTests()) {
            if (test instanceof ITestAnnotationFilterReceiver) {
                ITestAnnotationFilterReceiver filterTest = (ITestAnnotationFilterReceiver) test;
                // Retrieve the current set of excludeAnnotations to maintain for after the
                // clearing/reset of the annotations.
                Set<String> excludeAnnotations = new HashSet<>(filterTest.getExcludeAnnotations());
                // Prevent system user only tests from running
                excludeAnnotations.add("android.platform.test.annotations.SystemUserOnly");
                // Reset the annotations of the tests
                filterTest.clearExcludeAnnotations();
                filterTest.addAllExcludeAnnotation(excludeAnnotations);
            }
        }
    }
}
