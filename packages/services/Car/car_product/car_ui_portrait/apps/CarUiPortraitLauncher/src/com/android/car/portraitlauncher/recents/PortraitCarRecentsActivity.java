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

package com.android.car.portraitlauncher.recents;

import android.os.Bundle;

import com.android.car.carlauncher.recents.CarRecentsActivity;
import com.android.car.carlauncher.recents.RecentTasksViewModel;

/**
 * Recents activity to display list of recent tasks in Car.
 */
public class PortraitCarRecentsActivity extends CarRecentsActivity {
    private RecentTasksViewModel mRecentTasksViewModel;
    private PortraitHiddenTaskProvider mPortraitHiddenTaskProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRecentTasksViewModel = RecentTasksViewModel.getInstance();
        mPortraitHiddenTaskProvider = new PortraitHiddenTaskProvider(this);
        mRecentTasksViewModel.addHiddenTaskProvider(mPortraitHiddenTaskProvider);
    }

    @Override
    protected void onDestroy() {
        mRecentTasksViewModel.removeHiddenTaskProvider(mPortraitHiddenTaskProvider);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        if (OPEN_RECENT_TASK_ACTION.equals(getIntent().getAction())) {
            // no-op: This action results in collapsing the panel displaying Recents which is
            // handled by SystemUI.
            return;
        }
        super.onResume();
    }
}
