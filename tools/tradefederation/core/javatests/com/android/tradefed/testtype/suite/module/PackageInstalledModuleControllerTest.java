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
package com.android.tradefed.testtype.suite.module;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.testtype.suite.module.IModuleController.RunStrategy;
import com.android.tradefed.testtype.suite.ModuleDefinition;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Set;

/** Unit tests for {@link PackageInstalledModuleController}. */
@RunWith(JUnit4.class)
public class PackageInstalledModuleControllerTest {
    private static final String REQUIRED_PACKAGE = "required.package";
    private static final String DIFFERENT_PACKAGE = "different.package";
    private PackageInstalledModuleController mController;
    private IInvocationContext mContext;
    private ITestDevice mMockDevice = Mockito.mock(ITestDevice.class);
    private IDevice mMockIDevice = Mockito.mock(IDevice.class);

    @Before
    public void setUp() {
        mController = new PackageInstalledModuleController();
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_NAME, "module1");
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
    }

    /** Test device has the required package. */
    @Test
    public void testDeviceHasRequiredPackage()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getInstalledPackageNames()).thenReturn(Set.of(REQUIRED_PACKAGE));
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("required-package", REQUIRED_PACKAGE);

        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
    }

    /** Test device does not have the required package. */
    @Test
    public void testDeviceDoesNotHaveRequiredPackage()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getInstalledPackageNames()).thenReturn(Set.of(DIFFERENT_PACKAGE));
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("required-package", REQUIRED_PACKAGE);

        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
    }
}
