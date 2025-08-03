/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.result.error.ErrorIdentifier;

import java.lang.StackWalker.Option;

/**
 * Thrown when a device action did not results in the expected results.
 *
 * <p>For example: 'pm list users' is vastly expected to return the list of users, failure to do so
 * should be raised as a DeviceRuntimeException since something went very wrong.
 */
public class DeviceRuntimeException extends HarnessRuntimeException {
    private static final long serialVersionUID = -7928528651742852301L;

    /**
     * Creates a {@link DeviceRuntimeException}.
     *
     * @param msg a descriptive error message of the error.
     * @param errorId The {@link ErrorIdentifier} categorizing the exception.
     */
    public DeviceRuntimeException(String msg, ErrorIdentifier errorId) {
        super(msg, errorId);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }

    /**
     * Creates a {@link DeviceRuntimeException}.
     *
     * @param msg a descriptive error message of the error
     * @param t {@link Throwable} that should be wrapped in {@link DeviceRuntimeException}.
     * @param errorId The {@link ErrorIdentifier} categorizing the exception.
     */
    public DeviceRuntimeException(String msg, Throwable t, ErrorIdentifier errorId) {
        super(msg, t, errorId);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }
}
