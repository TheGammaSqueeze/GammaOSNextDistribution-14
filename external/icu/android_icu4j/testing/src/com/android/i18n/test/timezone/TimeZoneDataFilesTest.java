/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.i18n.test.timezone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.icu.platform.AndroidDataFiles;
import android.icu.testsharding.MainTestShard;
import com.android.i18n.timezone.TimeZoneDataFiles;
import org.junit.Test;

@MainTestShard
public class TimeZoneDataFilesTest {

    private static final String ANDROID_TZDATA_ROOT_ENV = "ANDROID_TZDATA_ROOT";
    private static final String ANDROID_I18N_ROOT_ENV = "ANDROID_I18N_ROOT";

    @Test
    public void expectedEnvironmentVariables() {
        // These environment variables are required to locate data files used by libcore / ICU.
        assertNotNull(System.getenv(ANDROID_TZDATA_ROOT_ENV));
        assertNotNull(System.getenv(ANDROID_I18N_ROOT_ENV));
    }

    @Test
    public void getTimeZoneFilePaths() {
        String[] paths = TimeZoneDataFiles.getTimeZoneFilePaths("foo");
        assertEquals(1, paths.length);

        assertTrue(paths[0].startsWith(System.getenv(ANDROID_TZDATA_ROOT_ENV)));
        assertTrue(paths[0].endsWith("/foo"));
    }

    // http://b/34867424
    @Test
    public void generateIcuDataPath_includesTimeZoneOverride() {
        String icuDataPath = AndroidDataFiles.generateIcuDataPath();

        String[] paths = icuDataPath.split(":");
        assertEquals(2, paths.length);

        String tzdataModulePath = paths[0];
        assertTrue(tzdataModulePath + " invalid",
                tzdataModulePath.startsWith(System.getenv(ANDROID_TZDATA_ROOT_ENV)));

        String runtimeModulePath = paths[1];
        assertTrue(runtimeModulePath + " invalid",
                runtimeModulePath.startsWith(System.getenv(ANDROID_I18N_ROOT_ENV)));
        assertTrue(runtimeModulePath + " invalid", runtimeModulePath.contains("/etc/icu"));
    }
}
