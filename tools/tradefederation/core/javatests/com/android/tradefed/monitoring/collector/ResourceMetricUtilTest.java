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

package com.android.tradefed.monitoring.collector;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Tests for {@link ResourceMetricUtil} */
@RunWith(JUnit4.class)
public class ResourceMetricUtilTest {
    @Mock private IDeviceManager mDeviceManager;
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    /** Tests getting command response. */
    @Test
    public void testGetCommandResponse() {
        CommandResult mockResult =
                new CommandResult() {
                    @Override
                    public CommandStatus getStatus() {
                        return CommandStatus.SUCCESS;
                    }

                    @Override
                    public String getStdout() {
                        return "mock response";
                    }
                };
        when(mDeviceManager.executeCmdOnAvailableDevice("foo", "bar", 500, TimeUnit.MILLISECONDS))
                .thenReturn(mockResult);
        Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(mDeviceManager, "foo", "bar", 500);
        Assert.assertEquals(response.get(), "mock response");
    }

    /** Tests getting command response failed. */
    @Test
    public void testGetCommandResponse_failed() {
        CommandResult mockResult =
                new CommandResult() {
                    @Override
                    public CommandStatus getStatus() {
                        return CommandStatus.FAILED;
                    }
                };
        when(mDeviceManager.executeCmdOnAvailableDevice("foo", "bar", 500, TimeUnit.MILLISECONDS))
                .thenReturn(mockResult);
        Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(mDeviceManager, "foo", "bar", 500);
        Assert.assertFalse(response.isPresent());
    }

    /** Tests parsing metric value. */
    @Test
    public void testRoundedMetricValue() {
        Assert.assertEquals(3.34, ResourceMetricUtil.RoundedMetricValue("3.335"), 0.001);
        Assert.assertEquals(333.33, ResourceMetricUtil.ConvertedMetricValue("1000", 3.0f), 0.001);
    }
}
