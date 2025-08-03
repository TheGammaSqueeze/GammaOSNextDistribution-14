/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.config;

import com.android.tradefed.error.HarnessException;
import com.android.tradefed.result.error.ErrorIdentifier;

import java.lang.StackWalker.Option;

/** Thrown if configuration could not be loaded. */
public class ConfigurationException extends HarnessException {
    private static final long serialVersionUID = 7742154448569011969L;

    /**
     * Creates a {@link ConfigurationException}.
     *
     * @param msg a meaningful error message
     */
    public ConfigurationException(String msg) {
        super(msg, null);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }

    /**
     * Creates a {@link ConfigurationException}.
     *
     * @param msg a meaningful error message
     * @param error The {@link ErrorIdentifier} associated with the exception
     */
    public ConfigurationException(String msg, ErrorIdentifier error) {
        super(msg, error);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }

    /**
     * Creates a {@link ConfigurationException}.
     *
     * @param msg a meaningful error message
     * @param cause the {@link Throwable} that represents the original cause of the error
     */
    public ConfigurationException(String msg, Throwable cause) {
        super(msg, cause, null);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }

    /**
     * Creates a {@link ConfigurationException}.
     *
     * @param msg a meaningful error message
     * @param cause the {@link Throwable} that represents the original cause of the error
     * @param error The {@link ErrorIdentifier} associated with the exception
     */
    public ConfigurationException(String msg, Throwable cause, ErrorIdentifier error) {
        super(msg, cause, error);
        setCallerClass(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }
}

