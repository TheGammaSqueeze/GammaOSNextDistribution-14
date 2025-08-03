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

package com.android.sts.common;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;

/** Interface for an asserter to use with {@link NativePoc#asserter}. */
public interface NativePocAsserter {
    /** Called before a PoC runs, returns an AutoCloseable that closes after the PoC finishes */
    public default AutoCloseable withAutoCloseable(NativePoc nativePoc, ITestDevice device)
            throws Exception {
        return new AutoCloseable() {
            @Override
            public void close() {}
        };
    }

    /** Called after the PoC finishes */
    public default void checkCmdResult(CommandResult result) {}
}
