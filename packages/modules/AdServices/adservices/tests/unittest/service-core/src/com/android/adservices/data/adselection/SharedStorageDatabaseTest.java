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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class SharedStorageDatabaseTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetInstance() {
        SharedStorageDatabase instance1 = SharedStorageDatabase.getInstance(CONTEXT);
        SharedStorageDatabase instance2 = SharedStorageDatabase.getInstance(CONTEXT);
        assertSame(instance1, instance2);
    }

    @Test
    public void testAppInstallDao() {
        SharedStorageDatabase instance = SharedStorageDatabase.getInstance(CONTEXT);
        assertNotNull(instance.appInstallDao());
    }
}
