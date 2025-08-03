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

package com.android.car.settings.system;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Build;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

@RunWith(AndroidJUnit4.class)
public class HardwareInfoPreferenceControllerTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private HardwareInfoPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private FutureTask<String> mMsvSuffixTask;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new Preference(mContext);
        mPreferenceController = new TestHardwareInfoPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_setsMsvSuffixSummary() throws ExecutionException, InterruptedException {
        String msvSuffix = "msv_summary";
        doNothing().when(mMsvSuffixTask).run();
        doReturn(msvSuffix).when(mMsvSuffixTask).get();

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.hardware_info_summary, Build.MODEL + msvSuffix));
    }

    @Test
    public void onCreate_executionException_usesBuildModelSummary()
            throws ExecutionException, InterruptedException {
        doNothing().when(mMsvSuffixTask).run();
        doThrow(ExecutionException.class).when(mMsvSuffixTask).get();

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.hardware_info_summary, Build.MODEL));
    }

    private class TestHardwareInfoPreferenceController extends HardwareInfoPreferenceController {
        TestHardwareInfoPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        FutureTask<String> createMsvSuffixTask() {
            return mMsvSuffixTask;
        }
    }
}
