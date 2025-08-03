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
package com.android.tradefed.testtype.suite.module;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;

/** Base class for a module controller to not run tests on HWASan builds. */
public class SkipHWASanModuleController extends BaseModuleController {
    private static final String PRODUCT_NAME_PROP = "ro.product.name";

    @Override
    public RunStrategy shouldRun(IInvocationContext context) throws DeviceNotAvailableException {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            String productNameString = device.getProperty(PRODUCT_NAME_PROP);
            if (productNameString.contains("_hwasan")) {
                return RunStrategy.FULL_MODULE_BYPASS;
            }
        }
        return RunStrategy.RUN;
    }
}
