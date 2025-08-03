/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.core.runner.filter;

import android.os.Bundle;
import android.util.Log;

import com.google.common.base.Splitter;

import org.junit.runner.Description;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import vogar.expect.Expectation;
import vogar.expect.ExpectationStore;
import vogar.expect.ModeId;
import vogar.expect.Result;

/**
 * It reads the files given in the {@link #ARGUMENT_EXPECTATIONS} bundle, and parses
 * the JSON files by {@link ExpectationStore#readExpectation(JsonReader, ModeId)}. This filter
 * skips the test if the test is present in the JSON file and the expected result isn't
 * {@link Result#SUCCESS}.
 */
public class CoreExpectationFilter implements Predicate<Description> {

    static final String TAG = "CoreExpectationFilter";

    private static final String ARGUMENT_EXPECTATIONS = "core-expectations";

    private static final Splitter CLASS_LIST_SPLITTER = Splitter.on(',').trimResults();

    private final ExpectationStore mExpectationStore;

    private CoreExpectationFilter(ExpectationStore store) {
        mExpectationStore = store;
    }

    /**
     * Config this filter from the {@link #ARGUMENT_EXPECTATIONS} option.
     *
     * @see com.android.cts.core.runner.ExpectationBasedFilter#ExpectationBasedFilter(Bundle)
     */
    public static CoreExpectationFilter createInstance(Bundle args) {
        // Get the set of resource names containing the expectations.
        return createInstance(new LinkedHashSet<>(getExpectationResourcePaths(args)));
    }

    public static CoreExpectationFilter createInstance(Set<String> expectationResources) {
        ExpectationStore expectationStore = null;
        try {
            Log.i(TAG, "Loading expectations from: " + expectationResources);
            expectationStore = ExpectationStore.parseResources(
                    CoreExpectationFilter.class, expectationResources, ModeId.DEVICE);
        } catch (IOException e) {
            Log.e(TAG, "Could not initialize ExpectationStore: ", e);
        }

        return new CoreExpectationFilter(expectationStore);

    }

    private static List<String> getExpectationResourcePaths(Bundle args) {
        return CLASS_LIST_SPLITTER.splitToList(args.getString(ARGUMENT_EXPECTATIONS));
    }

    @Override
    public boolean test(Description description) {
        String className = description.getClassName();
        String methodName = description.getMethodName();
        String testName = className + "#" + methodName;

        if (mExpectationStore != null) {
            Expectation expectation = mExpectationStore.get(testName);
            if (expectation.getResult() != Result.SUCCESS) {
                Log.d(TAG, "Excluding test " + description
                        + " as it matches expectation: " + expectation);
                return false;
            }
        }

        return true;
    }

}
