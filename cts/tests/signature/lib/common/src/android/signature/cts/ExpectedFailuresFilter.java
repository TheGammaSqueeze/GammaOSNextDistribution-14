/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.signature.cts;

import android.signature.cts.FailureType;
import android.signature.cts.ResultObserver;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filters out any expected failures.
 */
public class ExpectedFailuresFilter implements ResultObserver {

    private final ResultObserver delegate;

    private final Set<String> expected;

    /**
     * Initialize.
     *
     * @param delegate the {@link ResultObserver} to which unexpected failures will be delegated.
     * @param expected a collection of expected failures in the form {@code <failure-type>:<name>}
     * where {@code <failure-type>} is the lower case name of a {@link FailureType} and
     * {@code <name>} is the name of the class, field, constructor or method.
     */
    public ExpectedFailuresFilter(ResultObserver delegate, Collection<String> expected) {
        this.delegate = delegate;
        this.expected = new HashSet<>(expected);
    }

    @Override
    public void notifyFailure(FailureType type, String name, String errorMessage, Throwable throwable) {
        String key = type.toString().toLowerCase() + ":" + name;
        if (expected.contains(key)) {
            return;
        }

        delegate.notifyFailure(type, name, errorMessage, throwable);
    }
}
