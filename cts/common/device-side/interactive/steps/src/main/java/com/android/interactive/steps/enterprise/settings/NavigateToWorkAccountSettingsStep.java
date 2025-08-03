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

package com.android.interactive.steps.enterprise.settings;

import com.android.interactive.steps.CompositionStep;
import com.android.interactive.steps.settings.NavigateToAccountSettingsStep;

import java.util.Arrays;

/**
 * Starting from anywhere, navigate to the account settings section of the work settings app.
 *
 * <p>This will be the only settings app if there is no separate work settings app.
 */
public final class NavigateToWorkAccountSettingsStep extends CompositionStep {
    public NavigateToWorkAccountSettingsStep() {
        super(Arrays.asList(LaunchWorkSettingsStep.class, NavigateToAccountSettingsStep.class));
    }
}
