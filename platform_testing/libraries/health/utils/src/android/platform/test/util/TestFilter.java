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
package android.platform.test.util;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

/** Contains a method for filtering specific tests to run. Expects the format class#method. */
public final class TestFilter {

    private static final String LOG_TAG = TestFilter.class.getSimpleName();
    @VisibleForTesting static final String OPTION_NAME = "filter-tests";

    private TestFilter() {}

    public static boolean isFilteredOrUnspecified(Bundle arguments, Description description) {
        String testFilters = arguments.getString(OPTION_NAME);
        // If the argument is unspecified, always return true.
        if (testFilters == null) {
            return true;
        }

        String displayName = description.getDisplayName();

        // Test the display name against all filter arguments.
        for (String testFilter : testFilters.split(",")) {
            String[] filterComponents = testFilter.split("#");
            if (filterComponents.length != 2) {
                Log.e(
                        LOG_TAG,
                        String.format(
                                "Invalid filter-tests instrumentation argument supplied, %s.",
                                testFilter));
                continue;
            }

            String displayNameFilter =
                    String.format(
                            "%s(%s)",
                            // method
                            filterComponents[1],
                            // class
                            filterComponents[0]);
            if (displayNameFilter.equals(displayName)) {
                return true;
            }
        }
        // If the argument is specified and no matches, return false.
        return false;
    }
}
