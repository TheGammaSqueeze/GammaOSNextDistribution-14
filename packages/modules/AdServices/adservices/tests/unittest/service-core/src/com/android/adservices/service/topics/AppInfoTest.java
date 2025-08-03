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
package com.android.adservices.service.topics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link AppInfo}
 */
@SmallTest
public final class AppInfoTest {

    private static final String APP_NAME = "appName";
    private static final String APP_DESCRIPTION = "appDescription";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testCreation() throws Exception {
        AppInfo appInfo = new AppInfo(APP_NAME, APP_DESCRIPTION);
        assertEquals(APP_NAME, appInfo.getAppName());
        assertEquals(APP_DESCRIPTION, appInfo.getAppDescription());
    }

    @Test
    public void testNullInput() throws Exception {
        assertThrows(
            NullPointerException.class,
            () -> {
                new AppInfo(/* app name */ null, APP_DESCRIPTION);
            });

        assertThrows(
            NullPointerException.class,
            () -> {
                new AppInfo(APP_NAME, /* app description */ null);
            });

        assertThrows(
            NullPointerException.class,
            () -> {
                new AppInfo(/* app name */ null, /* app description */ null);
            });
    }
}
