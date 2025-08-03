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
package com.android.adservices.common;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Abstraction for classes that need to interact with the device. */
abstract class AbstractDeviceGateway {

    // TODO(b/294423183): need to refactor it (or implementation) so it doesn't ignore errors.
    // For example, setSyncDisabledModeForTest() was calling set_sync_disabled_for_tests
    // instead of set_sync_disabled_for_test
    @FormatMethod
    protected String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        throw new UnsupportedOperationException(
                "Subclass must either implement this or the methods that use it");
    }
}
