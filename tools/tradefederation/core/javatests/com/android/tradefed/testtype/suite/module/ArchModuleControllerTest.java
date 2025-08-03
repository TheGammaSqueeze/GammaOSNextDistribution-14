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

import static org.junit.Assert.assertEquals;

import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.testtype.suite.module.IModuleController.RunStrategy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link ArchModuleController}. */
@RunWith(JUnit4.class)
public class ArchModuleControllerTest {
    private ArchModuleController mController;
    private IInvocationContext mContext;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mController = new ArchModuleController();
        mContext = new InvocationContext();
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_NAME, "module1");
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
    }

    @Test
    public void testMatchesArch() throws DeviceNotAvailableException, ConfigurationException {
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_ABI, "arm64-v8a");

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("arch", "arm64");
        setter.setOptionValue("arch", "x86_64");
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
    }

    @Test
    public void testMismatchesArch() throws DeviceNotAvailableException, ConfigurationException {
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_ABI, "arm64-v8a");

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("arch", "arm");
        setter.setOptionValue("arch", "x86");
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
    }
}
