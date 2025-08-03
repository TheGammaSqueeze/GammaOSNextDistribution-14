/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.cts.delegate;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

/**
 * Superclass for {@code JUnit 3} based tests in this package.
 */
// TODO(b/30839080): remove this class once tests are refactored to use JUnit4
abstract class BaseJUnit3TestCase extends InstrumentationTestCase {

    private static final String TAG = BaseJUnit3TestCase.class.getSimpleName();

    protected UiDevice mDevice;
    protected DevicePolicyManager mDpm;
    protected Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = getInstrumentation().getContext();
        mDpm = BaseTestCase.getDpm(mContext);
        Log.d(TAG, getClass().getName() + ".setup(): dpm=" + mDpm);
    }
}
