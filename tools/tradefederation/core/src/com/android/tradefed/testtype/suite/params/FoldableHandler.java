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
package com.android.tradefed.testtype.suite.params;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.suite.FoldableModePreparer;

import java.util.List;

/**
 * Generic foldable handler that can take the foldable parameters to create a specialized module.
 */
public class FoldableHandler implements IModuleParameterHandler {

    private final String mStateName;
    private final long mIdentifier;

    public FoldableHandler(String stateName, long identifier) {
        mStateName = stateName;
        mIdentifier = identifier;
    }

    @Override
    public String getParameterIdentifier() {
        return mStateName;
    }

    /** {@inheritDoc} */
    @Override
    public void addParameterSpecificConfig(IConfiguration moduleConfiguration) {
        for (IDeviceConfiguration deviceConfig : moduleConfiguration.getDeviceConfig()) {
            List<ITargetPreparer> preparers = deviceConfig.getTargetPreparers();
            // The last things module will do is switch to the foldable mode
            preparers.add(new FoldableModePreparer(mIdentifier));
        }
    }

    @Override
    public void applySetup(IConfiguration moduleConfiguration) {
        // Empty on purpose currently. If needed set the annotation filters.
    }
}
