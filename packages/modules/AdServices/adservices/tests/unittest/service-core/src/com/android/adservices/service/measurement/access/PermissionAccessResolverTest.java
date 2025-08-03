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

package com.android.adservices.service.measurement.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

public class PermissionAccessResolverTest {

    private static final String ERROR_MESSAGE = "Unauthorized caller. Permission not requested.";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private PermissionAccessResolver mClassUnderTest;

    @Test
    public void isAllowedNoPermissionFails() {
        mClassUnderTest = new PermissionAccessResolver(false);
        assertFalse(mClassUnderTest.getAccessInfo(sContext).isAllowedAccess());
    }

    @Test
    public void isAllowedSuccess() {
        mClassUnderTest = new PermissionAccessResolver(true);
        assertTrue(mClassUnderTest.getAccessInfo(sContext).isAllowedAccess());
    }

    @Test
    public void getErrorMessage() {
        mClassUnderTest = new PermissionAccessResolver(false);
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }
}
