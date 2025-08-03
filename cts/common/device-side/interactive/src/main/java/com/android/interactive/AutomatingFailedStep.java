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

package com.android.interactive;

/**
 * A {@link Step} used when automation has failed.
 */
final class AutomatingFailedStep extends Step<Integer> {

    public static final int CONTINUE_MANUALLY = 1;
    public static final int RETRY = 2;
    public static final int RESTART_MANUALLY = 3;
    public static final int FAIL = 4;
    public static final int RESTART = 5;

    private final String mInstruction;

    public AutomatingFailedStep(String instruction) {
        mInstruction = instruction;
    }

    @Override
    public void interact() {
        show(mInstruction);

        addButton("Retry", () -> {
            pass(RETRY);
            close();
        });

        addButton("Restart", () -> {
            pass(RESTART);
            close();
        });

        addButton("Continue Manually", () -> {
            pass(CONTINUE_MANUALLY);
            close();
        });

        addButton("Restart Manually", () -> {
            pass(RESTART_MANUALLY);
            close();
        });

        addButton("Fail", () -> {
            pass(FAIL);
            close();
        });
    }
}
