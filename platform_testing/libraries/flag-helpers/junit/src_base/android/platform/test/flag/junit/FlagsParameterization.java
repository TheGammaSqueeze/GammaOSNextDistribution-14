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

package android.platform.test.flag.junit;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nonnull;

/** An object which holds aconfig flags values, and can be used for parameterized testing. */
public final class FlagsParameterization {
    public final Map<String, Boolean> mOverrides;

    /** Construct a values wrapper class */
    public FlagsParameterization(Map<String, Boolean> overrides) {
        mOverrides = Map.copyOf(overrides);
    }

    @Override
    public String toString() {
        if (mOverrides.isEmpty()) {
            return "EMPTY";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : new TreeMap<>(mOverrides).entrySet()) {
            if (sb.length() != 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Determines whether the dependency <code>alpha dependsOn beta</code> is met for the defined
     * values.
     *
     * @param alpha a flag which must be defined in this object
     * @param beta a flag which must be defined in this object
     * @return true in all cases except when alpha is enabled but beta is disabled.
     */
    public boolean isDependencyMet(String alpha, String beta) {
        boolean alphaEnabled = requireNonNull(mOverrides.get(alpha), alpha + " is not defined");
        boolean betaEnabled = requireNonNull(mOverrides.get(beta), beta + " is not defined");
        return betaEnabled || !alphaEnabled;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof FlagsParameterization)) return false;
        return mOverrides.equals(((FlagsParameterization) other).mOverrides);
    }

    @Override
    public int hashCode() {
        return mOverrides.hashCode();
    }

    /**
     * Produces a list containing every combination of boolean values for the given flags.
     *
     * @return a list of size 2^N for N provided flags.
     */
    @Nonnull
    public static List<FlagsParameterization> allCombinationsOf(@Nonnull String... flagNames) {
        List<Map<String, Boolean>> currentList = List.of(new HashMap<>());
        for (String flagName : flagNames) {
            List<Map<String, Boolean>> next = new ArrayList<>(currentList.size() * 2);
            for (Map<String, Boolean> current : currentList) {
                // copy the current map and add this flag as disabled
                Map<String, Boolean> plusDisabled = new HashMap<>(current);
                plusDisabled.put(flagName, false);
                next.add(plusDisabled);
                // re-use the current map and add this flag as enabled
                current.put(flagName, true);
                next.add(current);
            }
            currentList = next;
        }
        List<FlagsParameterization> result = new ArrayList<>();
        for (Map<String, Boolean> valuesMap : currentList) {
            result.add(new FlagsParameterization(valuesMap));
        }
        return result;
    }

    /**
     * Produces a list containing the flag parameterizations where each flag is turned on in the
     * given sequence.
     *
     * <p><code>progressionOf("a", "b", "c")</code> produces the following parameterizations:
     *
     * <ul>
     *   <li><code>{"a": false, "b": false, "c": false}</code>
     *   <li><code>{"a": true, "b": false, "c": false}</code>
     *   <li><code>{"a": true, "b": true, "c": false}</code>
     *   <li><code>{"a": true, "b": true, "c": true}</code>
     * </ul>
     *
     * @return a list of size N+1 for N provided flags.
     */
    @Nonnull
    public static List<FlagsParameterization> progressionOf(@Nonnull String... flagNames) {
        final List<FlagsParameterization> result = new ArrayList<>();
        final Map<String, Boolean> currentMap = new HashMap<>();
        for (String flagName : flagNames) {
            currentMap.put(flagName, false);
        }
        result.add(new FlagsParameterization(currentMap));
        for (String flagName : flagNames) {
            currentMap.put(flagName, true);
            result.add(new FlagsParameterization(currentMap));
        }
        return result;
    }
}
