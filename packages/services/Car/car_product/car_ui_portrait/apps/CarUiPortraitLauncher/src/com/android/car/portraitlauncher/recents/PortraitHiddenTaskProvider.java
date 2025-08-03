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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.car.carlauncher.recents.RecentTasksViewModel;
import com.android.car.portraitlauncher.R;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RecentTasksViewModel.HiddenTaskProvider} used to hide background activities  from Recents.
 */
public class PortraitHiddenTaskProvider implements RecentTasksViewModel.HiddenTaskProvider {
    private final Set<ComponentName> mBackgroundComponentSet;

    public PortraitHiddenTaskProvider(Context context) {
        // TODO(b/280647032): use TaskCategoryManager instead of accessing resources directly
        mBackgroundComponentSet = Arrays.stream(context.getApplicationContext()
                        .getResources().getStringArray(R.array.config_backgroundActivities))
                .map(ComponentName::unflattenFromString)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isTaskHiddenFromRecents(String packageName, String className,
            Intent baseIntent) {
        ComponentName componentName = baseIntent != null ? baseIntent.getComponent()
                : new ComponentName(packageName, className);
        return mBackgroundComponentSet.contains(componentName);
    }
}
