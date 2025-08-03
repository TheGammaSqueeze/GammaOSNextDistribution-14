/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.targetprep;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * Unit tests for {@link PropertyCheck}.
 */
@RunWith(JUnit4.class)
public class PropertyCheckTest {

    private PropertyCheck mPropertyCheck;
    private IDeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;
    private TestInformation mTestInfo;

    private static final String PROPERTY = "ro.mock.property";
    private static final String ACTUAL_VALUE = "mock_actual_value";
    private static final String BAD_VALUE = "mock_bad_value";

    @Before
    public void setUp() throws Exception {
        mPropertyCheck = new PropertyCheck();
        mMockDevice = Mockito.mock(ITestDevice.class);
        mMockBuildInfo = new DeviceBuildInfo("0", "");
        mOptionSetter = new OptionSetter(mPropertyCheck);
        when(mMockDevice.getProperty(PROPERTY)).thenReturn(ACTUAL_VALUE);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testWarningMatch() throws Exception {
        mOptionSetter.setOptionValue("property-name", PROPERTY);
        mOptionSetter.setOptionValue("expected-value", ACTUAL_VALUE);
        mOptionSetter.setOptionValue("throw-error", "false");

        mPropertyCheck.run(mTestInfo); // no warnings or errors
    }

    @Test
    public void testWarningMismatch() throws Exception {
        mOptionSetter.setOptionValue("property-name", PROPERTY);
        mOptionSetter.setOptionValue("expected-value", BAD_VALUE);
        mOptionSetter.setOptionValue("throw-error", "false");

        mPropertyCheck.run(mTestInfo); // should only print a warning
    }

    @Test
    public void testErrorMatch() throws Exception {
        mOptionSetter.setOptionValue("property-name", PROPERTY);
        mOptionSetter.setOptionValue("expected-value", ACTUAL_VALUE);
        mOptionSetter.setOptionValue("throw-error", "true");

        mPropertyCheck.run(mTestInfo); // no warnings or errors
    }

    @Test
    public void testErrorMismatch() throws Exception {
        mOptionSetter.setOptionValue("property-name", PROPERTY);
        mOptionSetter.setOptionValue("expected-value", BAD_VALUE);
        mOptionSetter.setOptionValue("throw-error", "true");

        try {
            mPropertyCheck.run(mTestInfo); // expecting TargetSetupError
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

}
