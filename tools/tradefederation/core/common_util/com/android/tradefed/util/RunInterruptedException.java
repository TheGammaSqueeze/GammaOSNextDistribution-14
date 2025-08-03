/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tradefed.util;

import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.result.error.ErrorIdentifier;

/** Thrown when a run operation is interrupted by an external request. */
public class RunInterruptedException extends HarnessRuntimeException {

    private static final long serialVersionUID = 100L;

    /**
     * Creates a {@link RunInterruptedException}.
     *
     * @param message The message associated with the exception
     * @param errorId The {@link ErrorIdentifier} categorizing the exception.
     */
    public RunInterruptedException(String message, ErrorIdentifier errorId) {
        super(message, errorId);
    }

    /**
     * Creates a {@link RunInterruptedException}.
     *
     * @param message The message associated with the exception
     * @param cause The cause of the exception
     * @param errorId The {@link ErrorIdentifier} categorizing the exception.
     */
    public RunInterruptedException(String message, Throwable cause, ErrorIdentifier errorId) {
        super(message, cause, errorId);
    }
}
