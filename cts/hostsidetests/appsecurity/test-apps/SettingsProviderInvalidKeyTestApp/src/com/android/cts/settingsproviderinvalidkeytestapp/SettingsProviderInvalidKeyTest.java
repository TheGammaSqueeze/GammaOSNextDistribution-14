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

package com.android.cts.settingsproviderinvalidkeytestapp;

import static org.testng.Assert.expectThrows;

import android.content.ContentResolver;
import android.provider.Settings;
import android.test.AndroidTestCase;

import com.google.common.base.Strings;

public final class SettingsProviderInvalidKeyTest extends AndroidTestCase {
    public void testLongKeysAreRejected() {
        final ContentResolver resolver = getContext().getContentResolver();
        IllegalStateException thrown = expectThrows(IllegalStateException.class,
                () -> Settings.System.putString(resolver, Strings.repeat("A", 30001), ""));
        assertTrue(thrown.getMessage().contains("adding too many system settings"));
        // Repeated calls should throw as well
        expectThrows(IllegalStateException.class,
                () -> Settings.System.putString(resolver, Strings.repeat("A", 30001), ""));
    }
}
