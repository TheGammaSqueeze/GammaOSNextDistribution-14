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

package com.android.interactive.steps;

import com.android.interactive.Nothing;
import com.android.interactive.Step;

/**
 * A {@link Step} where the user is asked to perform some action and confirm that it has been done.
 */
public abstract class ActAndConfirmStep extends Step<Nothing> {

    private final String mInstruction;

    protected ActAndConfirmStep(String instruction) {
        mInstruction = instruction;
    }

    @Override
    public void interact() {
        show(mInstruction);
        addButton("Done", this::pass);

        addFailButton();
        addSwapButton();
    }
}
