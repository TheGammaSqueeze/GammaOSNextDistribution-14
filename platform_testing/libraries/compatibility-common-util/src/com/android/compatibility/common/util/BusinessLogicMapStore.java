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

package com.android.compatibility.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Business-Logic GCL-accessible utility for key-value stores. */
public class BusinessLogicMapStore {

    private static Map<String, Map<String, String>> maps = new HashMap<>();

    public boolean hasMap(String mapName) {
        return maps.containsKey(mapName);
    }

    public void putMap(String mapName, String separator, String... keyValuePairs) {
        Map<String, String> map = maps.get(mapName);
        if (map == null) {
            map = new HashMap<>();
            maps.put(mapName, map);
        }

        for (String keyValuePair : keyValuePairs) {
            String[] tmp = keyValuePair.split(separator, 2);
            if (tmp.length != 2) {
                throw new IllegalArgumentException(
                        "Can't split key-value pair for \"" + keyValuePair + "\"");
            }
            String key = tmp[0];
            String value = tmp[1];
            map.put(key, value);
        }
    }

    public static Map<String, String> getMap(String mapName) {
        Map<String, String> map = maps.get(mapName);
        if (map == null) {
            return null;
        }
        return Collections.unmodifiableMap(map);
    }
}
