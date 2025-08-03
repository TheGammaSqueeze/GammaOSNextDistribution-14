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
package com.android.tradefed.targetprep;

import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;

/**
 * An {@link ITargetPreparer} to marks that tests should run in the sdk sandbox. See
 * https://developer.android.com/design-for-safety/privacy-sandbox/sdk-runtime
 */
@OptionClass(alias = "run-on-sdk-sandbox")
public class RunOnSdkSandboxTargetPreparer extends BaseTargetPreparer {

    public static final String RUN_TESTS_ON_SDK_SANDBOX = "RUN_TESTS_ON_SDK_SANDBOX";

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        testInformation.properties().put(RUN_TESTS_ON_SDK_SANDBOX, Boolean.TRUE.toString());
    }
}
