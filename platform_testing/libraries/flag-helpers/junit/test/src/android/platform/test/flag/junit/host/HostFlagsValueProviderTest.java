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

package android.platform.test.flag.junit.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.aconfig.Aconfig;
import android.aconfig.Aconfig.parsed_flags;
import android.platform.test.flag.junit.IFlagsValueProvider;
import android.platform.test.flag.util.FlagReadException;

import com.android.tradefed.device.ITestDevice;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(JUnit4.class)
public class HostFlagsValueProviderTest {
    private static final String DEVICE_CONFIG_LIST =
            "namespace1/flag1=true\nnamespace1/flag2=false\nnamespace2/flag1=abc";
    private static final parsed_flags ACONFIG_FLAGS =
            parsed_flags
                    .newBuilder()
                    .addParsedFlag(
                            Aconfig.parsed_flag
                                    .newBuilder()
                                    .setPackage("com.android.flags")
                                    .setName("my_flag")
                                    .setNamespace("cts")
                                    .setDescription("A sample flag")
                                    .addBug("12345678")
                                    .setState(Aconfig.flag_state.DISABLED)
                                    .setPermission(Aconfig.flag_permission.READ_WRITE))
                    .build();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Rule public final TemporaryFolder testFolder = new TemporaryFolder();

    @Mock private ITestDevice mTestDevice;

    private HostFlagsValueProvider mHostFlagsValueProvider;

    @Before
    public void initHostFlagsValueProvider() throws Exception {
        File aconfigFlagsPbFile = testFolder.newFile();
        ACONFIG_FLAGS.writeTo(new FileOutputStream(aconfigFlagsPbFile));

        File aconfigFlagsEmptyPbFile = testFolder.newFile();
        parsed_flags.getDefaultInstance().writeTo(new FileOutputStream(aconfigFlagsEmptyPbFile));
        when(mTestDevice.executeShellCommand(eq("device_config list")))
                .thenReturn(DEVICE_CONFIG_LIST);
        when(mTestDevice.getSerialNumber()).thenReturn("123456");
        when(mTestDevice.doesFileExist("/system/etc/aconfig_flags.pb")).thenReturn(true);
        when(mTestDevice.doesFileExist("/product/etc/aconfig_flags.pb")).thenReturn(true);
        when(mTestDevice.doesFileExist("/system_ext/etc/aconfig_flags.pb")).thenReturn(false);
        when(mTestDevice.doesFileExist("/vendor/etc/aconfig_flags.pb")).thenReturn(false);
        when(mTestDevice.pullFile("/system/etc/aconfig_flags.pb")).thenReturn(aconfigFlagsPbFile);
        when(mTestDevice.pullFile("/product/etc/aconfig_flags.pb"))
                .thenReturn(aconfigFlagsEmptyPbFile);
        mHostFlagsValueProvider =
                new HostFlagsValueProvider(
                        () -> {
                            return mTestDevice;
                        });
        mHostFlagsValueProvider.setUp();
    }

    @Test
    public void getBoolean_flagNotExist_throwException() throws Exception {
        assertThrows(
                FlagReadException.class,
                () -> mHostFlagsValueProvider.getBoolean("flag_not_exist"));
    }

    @Test
    public void getBoolean_flagNotBoolean_throwException() throws Exception {
        assertThrows(
                FlagReadException.class,
                () -> mHostFlagsValueProvider.getBoolean("namespace2/flag1"));
    }

    @Test
    public void getBoolean_verify() throws Exception {
        assertTrue(mHostFlagsValueProvider.getBoolean("namespace1/flag1"));
        assertFalse(mHostFlagsValueProvider.getBoolean("namespace1/flag2"));
        assertFalse(mHostFlagsValueProvider.getBoolean("cts/com.android.flags.my_flag"));
    }

    @Test
    public void getBoolean_afterRefresh() throws Exception {
        when(mTestDevice.executeShellCommand(eq("device_config list")))
                .thenReturn("namespace1/flag1=false");
        HostFlagsValueProvider.refreshFlagsCache("123456");

        assertFalse(mHostFlagsValueProvider.getBoolean("namespace1/flag1"));
    }

    @Test
    public void isBoolean_verify() {
        assertTrue(IFlagsValueProvider.isBooleanValue("true"));
        assertTrue(IFlagsValueProvider.isBooleanValue("false"));
        assertFalse(IFlagsValueProvider.isBooleanValue("True"));
        assertFalse(IFlagsValueProvider.isBooleanValue("False"));
        assertFalse(IFlagsValueProvider.isBooleanValue(""));
        assertFalse(IFlagsValueProvider.isBooleanValue("abc"));
    }
}
