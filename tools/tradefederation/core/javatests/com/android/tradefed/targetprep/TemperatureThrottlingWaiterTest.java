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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link TemperatureThrottlingWaiter}. */
@RunWith(JUnit4.class)
public final class TemperatureThrottlingWaiterTest {
    @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock private TestInformation mTestInfo;
    @Mock private ITestDevice mTestDevice;
    @Mock private IRunUtil mMockRunUtil;

    private TemperatureThrottlingWaiter mTempWaiter;
    private OptionSetter mOptionSetter;

    @Before
    public void setUp() throws Exception {
        when(mTestInfo.getDevice()).thenReturn(mTestDevice);

        mTempWaiter =
                new TemperatureThrottlingWaiter() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mOptionSetter = new OptionSetter(mTempWaiter);
    }

    @Test
    public void testGetTemperatureFromFile_rawFormat_success() throws Exception {
        when(mTestDevice.executeShellCommand("cat /foo/bar/tmp")).thenReturn("Result:30 Raw:7f6f");
        mOptionSetter.setOptionValue("device-temperature-file-path", "/foo/bar/tmp");
        mOptionSetter.setOptionValue("target-temperature", "30");

        mTempWaiter.setUp(mTestInfo);
    }

    @Test
    public void testGetTemperatureFromFile_noFile_throws() throws Exception {
        when(mTestDevice.executeShellCommand("cat /foo/bar/tmp"))
                .thenReturn("No such file or directory");
        mOptionSetter.setOptionValue("device-temperature-file-path", "/foo/bar/tmp");

        assertThrows(TargetSetupError.class, () -> mTempWaiter.setUp(mTestInfo));
    }

    @Test
    public void testGetTemperatureFromFile_format_success() throws Exception {
        when(mTestDevice.executeShellCommand("cat /foo/bar/tmp")).thenReturn("30");
        mOptionSetter.setOptionValue("device-temperature-file-path", "/foo/bar/tmp");
        mOptionSetter.setOptionValue("target-temperature", "30");

        mTempWaiter.setUp(mTestInfo);
    }

    @Test
    public void testGetTemperatureFromCommand_skinTemp_success() throws Exception {
        when(mTestDevice.executeShellCommand(
                        "dumpsys thermalservice | grep 'mType=3' | grep Temperature | awk"
                                + " 'END{print}'"))
                .thenReturn(
                        "Temperature{mValue=28.787504, mType=3, mName=VIRTUAL-SKIN, mStatus=0}");
        mOptionSetter.setOptionValue(
                "device-temperature-command",
                "dumpsys thermalservice | grep 'mType=3' | grep Temperature | awk 'END{print}'");
        mOptionSetter.setOptionValue("target-temperature", "30");

        mTempWaiter.setUp(mTestInfo);
    }
}
