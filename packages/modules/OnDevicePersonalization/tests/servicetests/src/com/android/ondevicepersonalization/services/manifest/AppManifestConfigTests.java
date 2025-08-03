/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppManifestConfigTests {
    private static final String BASE_DOWNLOAD_URL =
            "android.resource://com.android.ondevicepersonalization.servicetests/raw/test_data1";

    private static final String FC_SERVER_URL =
            "https://google.com";
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testManifestContainsOdpSettings() throws PackageManager.NameNotFoundException {
        assertTrue(AppManifestConfigHelper.manifestContainsOdpSettings(
                mContext, mContext.getPackageName()));
    }

    @Test
    public void testManifestContainsOdpSettingsFalse() throws PackageManager.NameNotFoundException {
        assertFalse(AppManifestConfigHelper.manifestContainsOdpSettings(
                mContext, "nonExistentName"));
    }

    @Test
    public void testGetConfigParamsFromOdpSettings() throws PackageManager.NameNotFoundException {
        AppManifestConfig config =
                AppManifestConfigHelper.getAppManifestConfig(mContext, mContext.getPackageName());
        assertEquals(BASE_DOWNLOAD_URL, config.getDownloadUrl());
        assertEquals("com.test.TestPersonalizationService", config.getServiceName());
        assertEquals(FC_SERVER_URL, config.getFcRemoteServerUrl());
    }

    @Test
    public void testAppManifestConfigBadPackage() {
        assertThrows(IllegalArgumentException.class,
                () -> AppManifestConfigHelper.getAppManifestConfig(mContext, "badPackageName"));
    }
}
