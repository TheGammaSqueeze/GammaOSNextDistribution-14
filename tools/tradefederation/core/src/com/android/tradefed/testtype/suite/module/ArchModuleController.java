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
package com.android.tradefed.testtype.suite.module;

import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.AbiUtils;

import java.util.HashSet;
import java.util.Set;

/** Module controller to not run tests when it doesn't match any given architectures. */
public class ArchModuleController extends BaseModuleController {

    @Option(
            name = "arch",
            description =
                    "The architecture names that should run for this module."
                            + "This should be like arm64, arm, x86_64, x86.",
            mandatory = true)
    private Set<String> mArches = new HashSet<>();

    @Override
    public RunStrategy shouldRun(IInvocationContext context) {
        // This should return arm64-v8a or armeabi-v7a
        String moduleAbiName = getModuleAbi().getName();
        // Use AbiUtils to get the actual architecture name.
        // If moduleAbiName is arm64-v8a then the moduleArchName will be arm64
        // If moduleAbiName is armeabi-v7a then the moduleArchName will be arm
        String moduleArchName = AbiUtils.getArchForAbi(moduleAbiName);

        if (mArches.contains(moduleArchName)) {
            return RunStrategy.RUN;
        }
        CLog.d(
                "Skipping module %s running on abi %s, which doesn't match any required setting "
                        + "of %s.",
                getModuleName(), moduleAbiName, mArches);
        return RunStrategy.FULL_MODULE_BYPASS;
    }
}
