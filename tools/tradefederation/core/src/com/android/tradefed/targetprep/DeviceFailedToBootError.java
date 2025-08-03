/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.targetprep;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.result.error.ErrorIdentifier;

/**
 * Thrown if a device fails to boot after being flashed with a build.
 */
public class DeviceFailedToBootError extends BuildError {

    private static final long serialVersionUID = -6539557027017640715L;

    /**
     * Constructs a new (@link DeviceFailedToBootError} with a detailed error message.
     *
     * @param reason an error message giving more details about the boot failure
     * @param descriptor the descriptor of the device concerned by the exception
     * @deprecated Use {@link #DeviceFailedToBootError(String, DeviceDescriptor, ErrorIdentifier)}
     *     instead
     */
    @Deprecated
    public DeviceFailedToBootError(String reason, DeviceDescriptor descriptor) {
        super(reason, descriptor);
    }

    /**
     * Constructs a new (@link DeviceFailedToBootError} with a detailed error message.
     *
     * @param reason an error message giving more details about the boot failure
     * @param descriptor the descriptor of the device concerned by the exception
     * @param errorId the error identifier for this error.
     */
    public DeviceFailedToBootError(
            String reason, DeviceDescriptor descriptor, ErrorIdentifier errorId) {
        super(reason, descriptor, errorId);
    }

    /**
     * Constructs a new (@link DeviceFailedToBootError} with a detailed error message.
     *
     * @param reason an error message giving more details about the boot failure
     * @param descriptor the descriptor of the device concerned by the exception
     * @param cause The original cause of the exception
     * @param errorId the error identifier for this error.
     */
    public DeviceFailedToBootError(
            String reason, DeviceDescriptor descriptor, Throwable cause, ErrorIdentifier errorId) {
        super(reason, descriptor, cause, errorId);
    }
}
