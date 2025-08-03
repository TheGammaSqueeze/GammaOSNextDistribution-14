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

import com.android.interactive.Automation;
import com.android.interactive.Nothing;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.enterprise.settings.NavigateToWorkAccountSettingsStep")
public final class NavigateToWorkAccountSettingsStepAutomation implements Automation<Nothing> {
    @Override
    public Nothing automate() {
        // There is only one Settings app
        new NavigateToPersonalAccountSettingsStepAutomation().automate();

        return Nothing.NOTHING;
    }
}
