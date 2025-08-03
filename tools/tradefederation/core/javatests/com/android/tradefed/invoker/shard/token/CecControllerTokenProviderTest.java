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
package com.android.tradefed.invoker.shard.token;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link CecControllerTokenProvider}. */
@RunWith(JUnit4.class)
public class CecControllerTokenProviderTest {

    private CecControllerTokenProvider provider;
    private ITestDevice mDevice;
    private boolean mIsAdapterConnected = false;

    @Before
    // The stubbed method hasFeature throws DeviceNotAvailableException
    public void setUp() throws DeviceNotAvailableException {
        provider =
                new CecControllerTokenProvider() {
                    @Override
                    boolean isCecAdapterConnected(ITestDevice device) {
                        return mIsAdapterConnected;
                    }
                };
        mDevice = Mockito.mock(ITestDevice.class);
        Mockito.when(mDevice.hasFeature("feature:android.hardware.hdmi.cec")).thenReturn(true);
        Mockito.when(mDevice.hasFeature("feature:android.software.leanback")).thenReturn(true);
    }

    @Test
    public void testAdapterConnected() {
        mIsAdapterConnected = true;
        assertTrue(provider.hasToken(mDevice, TokenProperty.CEC_TEST_CONTROLLER));
    }

    @Test
    public void testAdapterDisconnected() {
        mIsAdapterConnected = false;
        assertFalse(provider.hasToken(mDevice, TokenProperty.CEC_TEST_CONTROLLER));
    }

    @Test
    public void testNonHdmiDevice() throws DeviceNotAvailableException {
        mIsAdapterConnected = false;
        Mockito.when(mDevice.hasFeature("feature:android.hardware.hdmi.cec")).thenReturn(false);
        assertTrue(provider.hasToken(mDevice, TokenProperty.CEC_TEST_CONTROLLER));
    }

    @Test
    public void testNonLeanbackDevice() throws DeviceNotAvailableException {
        mIsAdapterConnected = false;
        Mockito.when(mDevice.hasFeature("feature:android.software.leanback")).thenReturn(false);
        assertTrue(provider.hasToken(mDevice, TokenProperty.CEC_TEST_CONTROLLER));
    }

    @Test
    public void testIncorrectToken() {
        mIsAdapterConnected = true;
        assertFalse(provider.hasToken(mDevice, TokenProperty.SIM_CARD));
    }

    @Test
    public void testConsoleOutput() {
        String consoleOutput =
                "test console output\n"
                        + "40:00:01:00\n"
                        + "\n"
                        + "waiting for input\n"
                        + "4089747576\n"
                        + "40:89:74:65:73:74";
        try (BufferedReader br = new BufferedReader(new StringReader(consoleOutput))) {
            assertTrue(provider.checkConsoleOutput("waiting for input", 250, br));
            assertTrue(
                    provider.checkConsoleOutput(
                            provider.convertStringToHexParams("test"), 250, br));
            assertFalse(provider.checkConsoleOutput("wait for input", 250, br));
            assertFalse(provider.checkConsoleOutput("74:75:76", 250, br));
            assertFalse(
                    provider.checkConsoleOutput(
                            provider.convertStringToHexParams("testing"), 250, br));
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException!", e);
        }
    }
}
