/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.config.yaml;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Helper to parse test runner information from the YAML Tradefed Configuration. */
public class YamlClassOptionsParser {

    private static final String OPTIONS_KEY = "options";

    class ClassAndOptions {
        public String mClass;
        public Multimap<String, String> mOptions = LinkedListMultimap.create();
    }

    private List<ClassAndOptions> mListClassAndOptions = new ArrayList<>();

    public YamlClassOptionsParser(List<Object> objList) {
        for (Object obj : objList) {
            if (obj instanceof String) {
                ClassAndOptions classOptions = new ClassAndOptions();
                mListClassAndOptions.add(classOptions);
                classOptions.mClass = (String) obj;
            } else if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> objEntry = (Map<String, Object>) obj;
                for (String classString : objEntry.keySet()) {
                    ClassAndOptions classOptions = new ClassAndOptions();
                    mListClassAndOptions.add(classOptions);
                    classOptions.mClass = classString;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) objEntry.get(classString);
                    // If there are no option the map is null
                    if (map == null) {
                        continue;
                    }
                    for (Entry<String, Object> entry : map.entrySet()) {
                        if (OPTIONS_KEY.equals(entry.getKey())) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> optionMapList =
                                    (List<Map<String, Object>>) entry.getValue();
                            for (Map<String, Object> optionMap : optionMapList) {
                                for (Entry<String, Object> optionVal : optionMap.entrySet()) {
                                    // TODO: Support map option
                                    classOptions.mOptions.put(
                                            optionVal.getKey(), optionVal.getValue().toString());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public List<ClassAndOptions> getClassesAndOptions() {
        return mListClassAndOptions;
    }
}
