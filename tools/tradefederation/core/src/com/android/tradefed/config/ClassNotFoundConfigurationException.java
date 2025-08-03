/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tradefed.result.error.ErrorIdentifier;

import java.util.HashMap;
import java.util.Map;

/** {@link ConfigurationException} for when the class of an object is not found. */
public class ClassNotFoundConfigurationException extends ConfigurationException {

    private static final long serialVersionUID = 7742154448569011969L;
    private Map<String, String> mRejectedObjects;

    /**
     * Creates a {@link ClassNotFoundConfigurationException}.
     *
     * @param msg a meaningful error message
     * @param cause the {@link Throwable} that represents the original cause of the error
     * @param error The {@link ErrorIdentifier} associated with the exception
     * @param className the class of the object that was not found
     * @param objectType the Tradefed object type of the object that was not found
     */
    public ClassNotFoundConfigurationException(
            String msg,
            Throwable cause,
            ErrorIdentifier error,
            String className,
            String objectType) {
        super(msg, cause, error);
        mRejectedObjects = new HashMap<>();
        mRejectedObjects.put(className, objectType);
    }

    /**
     * Creates a {@link ClassNotFoundConfigurationException}.
     *
     * @param msg a meaningful error message
     * @param cause the {@link Throwable} that represents the original cause of the error
     * @param error The {@link ErrorIdentifier} associated with the exception
     * @param rejectedObjects The map of objects that failed loading.
     */
    public ClassNotFoundConfigurationException(
            String msg,
            Throwable cause,
            ErrorIdentifier error,
            Map<String, String> rejectedObjects) {
        super(msg, cause, error);
        mRejectedObjects = rejectedObjects;
    }

    /** Returns the map of object class that was rejected. */
    public Map<String, String> getRejectedObjects() {
        return mRejectedObjects;
    }
}
