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
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
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

/** Unit tests for {@link DeviceFeatureModuleController}. */
@RunWith(JUnit4.class)
public class DeviceFeatureModuleControllerTest {
    private static final String FEATURE = "required.feature";
    private DeviceFeatureModuleController mController;
    private IInvocationContext mContext;
    @Mock ITestDevice mMockDevice;
    @Mock IDevice mMockIDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mController = new DeviceFeatureModuleController();

        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_ABI, "arm64-v8a");
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_NAME, "module1");
    }

    /** Test device has the required feature. */
    @Test
    public void testDeviceHasRequiredFeature()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.hasFeature(FEATURE)).thenReturn(true);

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("required-feature", FEATURE);
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
    }

    /** Test device does not have the required feature. */
    @Test
    public void testDeviceHasNoRequiredFeature()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.hasFeature(FEATURE)).thenReturn(false);

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("required-feature", FEATURE);
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
    }

    /** Test device has the forbidden feature. */
    @Test
    public void testDeviceHasForbiddenFeature()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.hasFeature(FEATURE)).thenReturn(true);

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("forbidden-feature", FEATURE);
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
    }

    /** Test device does not have the forbidden feature. */
    @Test
    public void testDeviceHasNoForbiddenFeature()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.hasFeature(FEATURE)).thenReturn(false);

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("forbidden-feature", FEATURE);
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
    }
}
