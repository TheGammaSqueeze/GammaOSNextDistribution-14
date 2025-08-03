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

package com.android.adservices.service.common;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

/**
 * This Interface generates a validator.
 *
 * @param <T> is the type name of the object instance to be validated.
 */
public interface Validator<T> {
    String EXCEPTION_MESSAGE_FORMAT = "Invalid object of type %s. The violations are: %s";

    /**
     * Validate the object instance of type T.
     *
     * @param object is the Object instance to be validated.
     * @throws IllegalArgumentException with all the validation violations presented in a list of
     *     strings in the messages and return nothing is the object is valid.
     */
    default void validate(T object) throws IllegalArgumentException {
        Collection<String> violations = getValidationViolations(object);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            EXCEPTION_MESSAGE_FORMAT, object.getClass().getName(), violations));
        }
    }

    /**
     * Validates the object and returns a collection of violations if any.
     *
     * @param object is the Object instance to be validated.
     * @return an empty collection if the object is valid or a collection of strings describing all
     *     the encountered violations.
     */
    default Collection<String> getValidationViolations(T object) {
        ImmutableCollection.Builder<String> violations = new ImmutableList.Builder<>();
        addValidation(object, violations);
        return violations.build();
    }

    /** Validates the object and populate the violations. */
    void addValidation(T object, ImmutableCollection.Builder<String> violations);
}
