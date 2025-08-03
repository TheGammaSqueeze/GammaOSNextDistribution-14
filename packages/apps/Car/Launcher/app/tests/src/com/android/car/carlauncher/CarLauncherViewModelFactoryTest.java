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

package com.android.car.carlauncher;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;

import static com.android.car.carlauncher.CarLauncherViewModel.CarLauncherViewModelFactory;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.carlauncher.CarLauncherViewModelTest.TestActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class CarLauncherViewModelFactoryTest extends AbstractExtendedMockitoTestCase {
    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Mock
    private Context mContext;
    @Mock
    private Intent mIntent;

    private CarLauncherViewModelFactory mCarLauncherViewModelFactory;
    private TestActivity mActivity;

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        Context windowContext = mActivity
                .createWindowContext(TYPE_APPLICATION_STARTING, /* options */ null);
        when(mContext.createWindowContext(eq(WindowManager.LayoutParams.TYPE_APPLICATION_STARTING),
                any())).thenReturn(windowContext);
        mCarLauncherViewModelFactory = new CarLauncherViewModelFactory(mContext, mIntent);
    }

    @After
    public void tearDown() throws InterruptedException {
        mCarLauncherViewModelFactory = null;
        mActivityRule.getScenario().close();
        mActivity.finishCompletely();
    }

    @Test
    public void testCreate_instanceNotNull() {
        CarLauncherViewModel carLauncherViewModel =
                mCarLauncherViewModelFactory.create(CarLauncherViewModel.class);
        assertThat(carLauncherViewModel).isNotNull();
    }
}
