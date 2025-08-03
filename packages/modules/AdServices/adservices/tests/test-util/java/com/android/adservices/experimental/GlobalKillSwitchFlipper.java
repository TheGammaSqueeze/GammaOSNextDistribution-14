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

package com.android.adservices.experimental;

import org.junit.runners.model.InitializationError;

/**
 * A JUnit runner that will run every test twice by default, setting the value of the AdService's
 * kill-switch flag value to {@code false} and {@code true}.
 *
 * <p>By default it would run every test twice, but tests that don't need to be run when the kill
 * switch is enabled can be annotated with {@link RequiresGlobalKillSwitchDisabled} (and tests that
 * don't need to run when the kill switch is disabled can be annotated with
 * RequiresGlobalKillSwitchEnabled}).
 *
 * <p><b>NOTE: </b>to disable it (and run each test just once), set the Android System property
 * {@value #PROP_DISABLED} to {@code true} - this is mostly using during development so you can use
 * {@code atest} to run just a specific test method.
 */
public final class GlobalKillSwitchFlipper extends AbstractGlobalKillSwitchFlipper {

    public GlobalKillSwitchFlipper(Class<?> testClass) throws InitializationError {
        super(testClass);
    }
}
