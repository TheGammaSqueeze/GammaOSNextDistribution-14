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

package com.android.systemui.car.userpicker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.view.Display;

import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.DisplayTracker;

public class UserPickerBaseTestActivity extends UserPickerActivity {
    private final boolean mTestIsDriver;
    private final boolean mMockUserPickerAdapter;
    boolean mCalledFinished;

    public UserPickerBaseTestActivity(boolean isDriver) {
        this(isDriver, true);
    }

    public UserPickerBaseTestActivity(boolean isDriver, boolean mockUserPickerAdapter) {
        super();
        mController = mock(UserPickerController.class);
        mDialogManager = mock(DialogManager.class);
        mSnackbarManager = mock(SnackbarManager.class);
        mCarSystemBarElementInitializer = mock(CarSystemBarElementInitializer.class);
        mDisplayTracker = mock(DisplayTracker.class);
        mDumpManager = mock(DumpManager.class);
        mTestIsDriver = isDriver;
        mMockUserPickerAdapter = mockUserPickerAdapter;
        if (mMockUserPickerAdapter) {
            mAdapter = mock(UserPickerAdapter.class);
            when(mAdapter.getStateRestorationPolicy()).thenReturn(
                    RecyclerView.Adapter.StateRestorationPolicy.ALLOW);
        }
        when(mDisplayTracker.getDefaultDisplayId()).thenReturn(Display.DEFAULT_DISPLAY);
    }

    @Override
    boolean shouldStartAsSystemUser() {
        // return false to allow starting the activity as the current test user
        return false;
    }

    @Override
    boolean getIsDriver() {
        return mTestIsDriver;
    }

    @Override
    UserPickerAdapter createUserPickerAdapter() {
        if (mMockUserPickerAdapter) {
            return mAdapter;
        }
        return super.createUserPickerAdapter();
    }

    @Override
    public void finishAndRemoveTask() {
        mCalledFinished = true;
    }
}
