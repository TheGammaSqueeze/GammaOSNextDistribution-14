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
package com.android.tradefed.testtype.suite.params;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.RunOnSdkSandboxTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestAnnotationFilterReceiver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Handler for {@link ModuleParameters#RUN_ON_SDK_SANDBOX}. */
public class RunOnSdkSandboxHandler implements IModuleParameterHandler {

    /** {@inheritDoc} */
    @Override
    public String getParameterIdentifier() {
        return "run-on-sdk-sandbox";
    }

    /** {@inheritDoc} */
    @Override
    public void addParameterSpecificConfig(IConfiguration moduleConfiguration) {
        for (IDeviceConfiguration deviceConfig : moduleConfiguration.getDeviceConfig()) {
            List<ITargetPreparer> preparers = deviceConfig.getTargetPreparers();
            preparers.add(new RunOnSdkSandboxTargetPreparer());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void applySetup(IConfiguration moduleConfiguration) {
        for (IRemoteTest test : moduleConfiguration.getTests()) {
            if (test instanceof ITestAnnotationFilterReceiver) {
                ITestAnnotationFilterReceiver filterTest = (ITestAnnotationFilterReceiver) test;
                Set<String> includeAnnotations = new HashSet<>(filterTest.getIncludeAnnotations());
                // Only run tests explicitly marked to run in the SDK sandbox.
                includeAnnotations.add("android.platform.test.annotations.AppModeSdkSandbox");
                Set<String> excludeAnnotations = new HashSet<>(filterTest.getExcludeAnnotations());
                // The sandbox is more restrictive than instant apps, ignore @AppModeFull tests.
                excludeAnnotations.add("android.platform.test.annotations.AppModeFull");
                // Ignore tests that are not applicable to the sandbox.
                excludeAnnotations.add("android.platform.test.annotations.AppModeNonSdkSandbox");

                // Reset the annotations of the tests.
                filterTest.clearIncludeAnnotations();
                filterTest.addAllIncludeAnnotation(includeAnnotations);
                filterTest.clearExcludeAnnotations();
                filterTest.addAllExcludeAnnotation(excludeAnnotations);
            }
        }
    }
}
