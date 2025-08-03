/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.test.longevity.concurrent;

import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/** Common Utility class for longevity concurrent framework. */
final class SuiteUtils {

    private SuiteUtils() {
        throw new AssertionError(
                "Invoking constructor for the utility class which has static methods.");
    }

    /**
     * Takes a {@link Bundle} and maps all String K/V pairs into a {@link Map <String, String>}.
     *
     * @param bundle the input arguments to return in a {@link Map}
     * @return a {@code Map<String, String>} of all key, value pairs in {@code bundle}.
     * @throws IllegalArgumentException if value does not exist for the key in the bundle.
     */
    static Map<String, String> bundleToMap(Bundle bundle) {
        Map<String, String> result = new HashMap<>();
        for (String key : bundle.keySet()) {
            if (!bundle.containsKey(key)) {
                throw new IllegalArgumentException(
                        String.format(
                                "Couldn't find value for option: %s in the key set %s",
                                key, bundle.keySet()));
            } else {
                // Arguments are assumed String <-> String
                result.put(key, bundle.getString(key));
            }
        }
        return result;
    }

    /**
     * Converts {@link Bundle} into String format for logging purpose.
     *
     * @param bundle the input arguments
     * @return a {@link String} of all key, value pairs in {@code bundle}.
     */
    static String bundleToString(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (String key : bundle.keySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            Object value = bundle.get(key);
            sb.append(key).append("=").append(value);
        }
        sb.append('}');
        return sb.toString();
    }
}
