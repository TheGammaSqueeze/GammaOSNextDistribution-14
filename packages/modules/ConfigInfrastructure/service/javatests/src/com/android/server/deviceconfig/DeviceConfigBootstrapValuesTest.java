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

package com.android.server.deviceconfig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.provider.DeviceConfig;

import com.android.modules.utils.build.SdkLevel;

import com.android.server.deviceconfig.DeviceConfigBootstrapValues;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceConfigBootstrapValuesTest {
    private static final String WRITE_DEVICE_CONFIG_PERMISSION =
            "android.permission.WRITE_DEVICE_CONFIG";

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private static final String PATH_1 = "file:///data/local/tmp/deviceconfig/bootstrap1.txt";

    @Test
    public void assertParsesFiles() throws IOException {
        assumeTrue(SdkLevel.isAtLeastV());
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                WRITE_DEVICE_CONFIG_PERMISSION, READ_DEVICE_CONFIG_PERMISSION);

        DeviceConfigBootstrapValues values = new DeviceConfigBootstrapValues(PATH_1);
        values.applyValuesIfNeeded();

        assertTrue(DeviceConfig.getBoolean("a.a.a", "b.b.b", false));
        assertFalse(DeviceConfig.getBoolean("a.a.a", "b.b", true));
        assertTrue(DeviceConfig.getBoolean("b.b.b", "c.c", false));
        assertEquals(2,  DeviceConfig.getProperties("a.a.a").getKeyset().size());
        assertEquals(1,  DeviceConfig.getProperties("b.b.b").getKeyset().size());
    }
}
