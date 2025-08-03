/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tradefed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for the {@link AbiFormatter} utility class */
@RunWith(JUnit4.class)
public class AbiFormatterTest {
    /** Verify that {@link AbiFormatter#formatCmdForAbi} works as expected. */
    @Test
    public void testFormatCmdForAbi() throws Exception {
        String a = "test foo|#ABI#| bar|#ABI32#| foobar|#ABI64#|";
        // if abi is null, remove all place holders.
        assertEquals("test foo bar foobar", AbiFormatter.formatCmdForAbi(a, null));
        // if abi is "", remove all place holders.
        assertEquals("test foo bar foobar", AbiFormatter.formatCmdForAbi(a, ""));
        // if abi is 32
        assertEquals("test foo32 bar foobar32", AbiFormatter.formatCmdForAbi(a, "32"));
        // if abi is 64
        assertEquals("test foo64 bar64 foobar", AbiFormatter.formatCmdForAbi(a, "64"));
        // test null input string
        assertNull(AbiFormatter.formatCmdForAbi(null, "32"));
    }

    /** Verify that {@link AbiFormatter#getDefaultAbi} works as expected. */
    @Test
    public void testGetDefaultAbi() throws Exception {
        ITestDevice device = mock(ITestDevice.class);

        when(device.getProperty("ro.product.cpu.abilist32")).thenReturn(null);
        when(device.getProperty("ro.product.cpu.abi")).thenReturn(null);

        assertEquals(null, AbiFormatter.getDefaultAbi(device, "32"));

        Mockito.reset(device);
        when(device.getProperty(Mockito.eq("ro.product.cpu.abilist32")))
                .thenReturn("abi,abi2,abi3");

        assertEquals("abi", AbiFormatter.getDefaultAbi(device, "32"));

        Mockito.reset(device);
        when(device.getProperty(Mockito.eq("ro.product.cpu.abilist64"))).thenReturn("");
        when(device.getProperty("ro.product.cpu.abi")).thenReturn(null);

        assertEquals(null, AbiFormatter.getDefaultAbi(device, "64"));
    }

    /** Verify that {@link AbiFormatter#getSupportedAbis} works as expected. */
    @Test
    public void testGetSupportedAbis() throws Exception {
        ITestDevice device = mock(ITestDevice.class);

        when(device.getProperty("ro.product.cpu.abilist32")).thenReturn("abi1,abi2");

        String[] supportedAbiArray = AbiFormatter.getSupportedAbis(device, "32");
        assertEquals("abi1", supportedAbiArray[0]);
        assertEquals("abi2", supportedAbiArray[1]);

        Mockito.reset(device);
        when(device.getProperty("ro.product.cpu.abilist32")).thenReturn(null);
        when(device.getProperty("ro.product.cpu.abi")).thenReturn("abi");

        supportedAbiArray = AbiFormatter.getSupportedAbis(device, "32");
        assertEquals("abi", supportedAbiArray[0]);

        Mockito.reset(device);
        when(device.getProperty("ro.product.cpu.abilist")).thenReturn("");
        when(device.getProperty("ro.product.cpu.abi")).thenReturn("abi");

        supportedAbiArray = AbiFormatter.getSupportedAbis(device, "");
        assertEquals("abi", supportedAbiArray[0]);
    }

    /** Verify that {@link AbiFormatter#getSupportedAbis} works as expected. */
    @Test
    public void testGetSupportedAbis_null() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getProperty("ro.product.cpu.abilist")).thenReturn(null);
        when(device.getProperty("ro.product.cpu.abi")).thenReturn(null);

        String[] supportedAbiArray = AbiFormatter.getSupportedAbis(device, "");

        assertEquals(0, supportedAbiArray.length);
    }
}
