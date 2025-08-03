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
package com.android.tradefed.device;

import com.android.tradefed.build.BuildSerializedVersion;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.result.error.ErrorIdentifier;

import java.lang.StackWalker.Option;

/** Thrown when there's no device to execute a given command. */
public class NoDeviceException extends HarnessRuntimeException {
    private static final long serialVersionUID = BuildSerializedVersion.VERSION;

    /**
     * Creates a {@link NoDeviceException}.
     *
     * @param msg a descriptive message.
     * @param errorId The {@link ErrorIdentifier} categorizing the exception.
     */
    public NoDeviceException(String msg, ErrorIdentifier errorId) {
        super(msg, errorId);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }
}
