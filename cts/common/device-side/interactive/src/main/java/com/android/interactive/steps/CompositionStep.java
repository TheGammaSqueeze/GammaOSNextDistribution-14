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

import java.util.List;
import java.util.Optional;

/**
 * A step which is a composition of other steps.
 *
 * <p>This can be useful if it's easier to automate the composite step, but several steps are
 * appropriate for manual interaction.
 */
public abstract class CompositionStep extends Step<Nothing> {

    private final List<Class<? extends Step<Nothing>>> mSteps;
    private boolean mPassed = false;

    protected CompositionStep(List<Class<? extends Step<Nothing>>> steps) {
        mSteps = steps;
    }

    @Override
    public Optional<Nothing> getValue() {
        return mPassed ? Optional.of(Nothing.NOTHING) : Optional.empty();
    }

    @Override
    public boolean hasFailed() {
        return false;
    }

    @Override
    public void interact() {
        for (Class<? extends Step<Nothing>> step : mSteps) {
            try {
                Step.execute(step);
            } catch (RuntimeException e) {
                throw(e);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Error while executing " + step.getCanonicalName()
                                + " as part of composition", e);
            }
        }
        mPassed = true;
    }
}
