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

package com.android.tradefed.targetprep;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInformation;

import java.util.HashSet;
import java.util.Set;

/** Target preparer to skip retrying known failure. */
@OptionClass(alias = "known-failure-preparer")
public class KnownFailurePreparer extends BaseTargetPreparer implements IConfigurationReceiver {

    @Option(
            name = "known-failure-list",
            description = "A collection of known failing tests to skip retrying.")
    private Set<String> mKnownFailureList = new HashSet<>();

    private IConfiguration mConfig;
    private static final String SKIP_RETRYING_LIST = "skip-retrying-list";

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (!mKnownFailureList.isEmpty()) {
            try {
                for (String knownFailureModule : mKnownFailureList) {
                    mConfig.injectOptionValue(SKIP_RETRYING_LIST, knownFailureModule);
                }
            } catch (ConfigurationException e) {
                throw new HarnessRuntimeException(e.getMessage(), e);
            }
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }
}
