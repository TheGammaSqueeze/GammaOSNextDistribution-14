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

package com.android.i18n.test.system;

import static org.junit.Assert.assertNull;

import android.icu.testsharding.MainTestShard;

import com.android.i18n.system.AppSpecializationHooks;
import com.android.i18n.system.ZygoteHooks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@MainTestShard
@RunWith(JUnit4.class)
public class AppSpecializationHooksTest {

    @Test
    public void testDisableCompatChangesBeforeAppStart() {
        assertSystemPropBinaryDataPath_null();

        // The value should stay null after the app startup or after invoking the same method
        // repeatedly.
        AppSpecializationHooks.handleCompatChangesBeforeBindingApplication();
        assertSystemPropBinaryDataPath_null();
    }

    /**
     * CtsIcuTestCases use current SDK, and thus the property value should be null.
     */
    private static void assertSystemPropBinaryDataPath_null() {
        assertNull(ZygoteHooks.PROP_ICUBINARY_DATA_PATH + " property is not null.",
                System.getProperty(ZygoteHooks.PROP_ICUBINARY_DATA_PATH));
    }
}
