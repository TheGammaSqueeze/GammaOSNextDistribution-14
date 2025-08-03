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

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityOptions;
import android.content.pm.ActivityInfo;
import android.view.Gravity;

import com.android.server.wm.LaunchParamsController.LaunchParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * This class contains unit tests for the {@link CalculateParams}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CalculateParamsTest {
    @Mock
    private Task mTask;
    private ActivityInfo.WindowLayout mLayout = new ActivityInfo.WindowLayout(
            /* width= */ 1280, /* widthFraction= */ 0.5f,
            /* height= */ 800, /* heightFraction= */ 1.0f,
            /* gravity= */ Gravity.CENTER, /* minWidth= */ 400, /* minHeight= */ 300);
    @Mock
    private ActivityRecord mActvity;
    @Mock
    private ActivityRecord mSource;
    private ActivityOptions mOptions = ActivityOptions.makeBasic();
    private ActivityStarter.Request mRequest = new ActivityStarter.Request();
    private int mPhase = LaunchParamsController.LaunchParamsModifier.PHASE_BOUNDS;
    private LaunchParams mCurrentParams = new LaunchParams();
    private LaunchParams mOutParms = new LaunchParams();
    boolean mSupportsMultiDisplay = true;

    @Test
    public void createReturnsCalculateParams() {
        mCurrentParams.mWindowingMode = WINDOWING_MODE_FULLSCREEN;
        mOutParms.mWindowingMode = WINDOWING_MODE_MULTI_WINDOW;
        CalculateParams params = CalculateParams.create(mTask,mLayout, mActvity, mSource, mOptions,
                mRequest, mPhase, mCurrentParams, mOutParms, mSupportsMultiDisplay);
        // Current toString() of Wrappers are using toString() of the underlying object
        // except LaunchParams.
        assertThat(params.getTask().toString()).isEqualTo(mTask.toString());
        assertThat(params.getWindowLayout().toString()).isEqualTo(mLayout.toString());
        assertThat(params.getActivity().getActivityRecord()).isSameInstanceAs(mActvity);
        assertThat(params.getSource().getActivityRecord()).isSameInstanceAs(mSource);
        assertThat(params.getCurrentParams().getWindowingMode())
                .isEqualTo(WINDOWING_MODE_FULLSCREEN);
        assertThat(params.getOutParams().getWindowingMode()).isEqualTo(WINDOWING_MODE_MULTI_WINDOW);
        assertThat(params.supportsMultiDisplay()).isEqualTo(mSupportsMultiDisplay);
    }
}
