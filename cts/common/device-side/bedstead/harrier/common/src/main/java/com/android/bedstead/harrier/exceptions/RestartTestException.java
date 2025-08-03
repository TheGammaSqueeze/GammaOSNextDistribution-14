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

package com.android.bedstead.harrier.exceptions;

/**
 * An exception thrown when the test should be restarted - including completing the teardown of
 * the current run of the test and setup of the new run.
 *
 * <p>This will still be reported as a single run - and the combined run is subject to the normal
 * test timeout limits, etc.
 *
 * <p>For use with {@code BedsteadJUnit4}
 */
public class RestartTestException extends RuntimeException {
    public RestartTestException(String reason) {
        super(reason);
    }
}
