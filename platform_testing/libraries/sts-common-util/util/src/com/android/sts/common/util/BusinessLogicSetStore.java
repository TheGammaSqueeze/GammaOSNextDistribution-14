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

package com.android.sts.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Business-Logic GCL-accessible utility for sets. */
public class BusinessLogicSetStore {

    private static Map<String, Set<String>> sets = new HashMap<>();

    public boolean hasSet(String setName) {
        return sets.containsKey(setName);
    }

    public void putSet(String setName, String... elements) {
        Set<String> set = sets.get(setName);
        if (set == null) {
            set = new HashSet<>();
            sets.put(setName, set);
        }

        for (String element : elements) {
            set.add(element);
        }
    }

    public static Set<String> getSet(String setName) {
        Set<String> set = sets.get(setName);
        if (set == null) {
            return null;
        }
        return Collections.unmodifiableSet(set);
    }
}
