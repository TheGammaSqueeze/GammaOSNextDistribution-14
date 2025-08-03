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

package com.android.interactive.steps.settings;

import com.android.interactive.steps.YesNoStep;

/**
 * Starting from the wifi settings screen, confirm if you are able to add a wifi config
 */
public class CanYouAddWifiConfigStep extends YesNoStep {
    public CanYouAddWifiConfigStep() {
        super("Are you able to add a wifi config?");
    }
}
