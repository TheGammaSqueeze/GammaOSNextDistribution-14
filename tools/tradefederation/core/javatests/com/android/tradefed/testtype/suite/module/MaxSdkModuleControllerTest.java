/*
 * Copyright (C) 2023 The Android Open Source Project
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

/** Unit tests for {@link MaxSdkModuleController}. */
@RunWith(JUnit4.class)
public class MaxSdkModuleControllerTest {
    private static final String SDK_LEVEL_PROPERTY = "ro.build.version.sdk";
    private static final String MAX_SDK_LEVEL_OPTION_NAME = "max-sdk-level";
    private MaxSdkModuleController mController;
    private IInvocationContext mContext;
    @Mock ITestDevice mMockDevice;
    @Mock IDevice mMockIDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mController = new MaxSdkModuleController();

        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_ABI, "arm64-v8a");
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_NAME, "module1");
    }

    /** Test device api Level is higher than max-sdk-level */
    @Test
    public void testDeviceApiLevelHigherThanMaxSdkLevel()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.getProperty(SDK_LEVEL_PROPERTY)).thenReturn("31");

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue(MAX_SDK_LEVEL_OPTION_NAME, "29");
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
    }

    /** Test device api Level is lower than max-sdk-level */
    @Test
    public void testDeviceApiLevelLowerThanMaxSdkLevel()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.getProperty(SDK_LEVEL_PROPERTY)).thenReturn("27");

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue(MAX_SDK_LEVEL_OPTION_NAME, "28");
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
    }

    /** Test device api Level is equal to max-sdk-level */
    @Test
    public void testDeviceApiLevelEqualsMaxSdkLevel()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.getProperty(SDK_LEVEL_PROPERTY)).thenReturn("31");

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue(MAX_SDK_LEVEL_OPTION_NAME, "31");
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
    }

    /** Test Api Level cannot be found in the Device */
    @Test
    public void testDeviceApiLevelNotFound()
            throws DeviceNotAvailableException, ConfigurationException {
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.getProperty(SDK_LEVEL_PROPERTY)).thenReturn(null);

        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue(MAX_SDK_LEVEL_OPTION_NAME, "27");
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
    }
}
