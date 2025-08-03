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

package com.android.interactive.steps;


import com.android.interactive.Nothing;
import com.android.interactive.Step;

/**
 * Step to be used only for debugging - this step should never be automated and just used during
 * development and debugging to allow the developer to pause and see the state of the device.
 */
public final class PauseStep extends Step<Nothing> {

    @Override
    public void interact() {
        show("Pause");
        addButton("Continue", this::pass);
    }
}
