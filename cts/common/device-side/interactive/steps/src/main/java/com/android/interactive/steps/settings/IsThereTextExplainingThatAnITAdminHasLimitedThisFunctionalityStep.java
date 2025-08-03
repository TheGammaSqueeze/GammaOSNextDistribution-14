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
 * This step can be used anywhere and is quite generic about checking if there is an explanation
 * about an admin limitation.
 */
public final class IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep extends
        YesNoStep {
    public IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep() {
        super("Is there text explaining that an IT admin has limited this functionality?");
    }
}
