/*
 * Copyright (C) 2020 Google LLC.
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

package android.linkerconfig.gts.utils.elements;


import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Configuration {
    private static final String REGEX_DIR_TO_SECTION = "dir\\.(\\w+)\\s*=\\s([\\w.\\-/]+)";
    private Pattern mDirToSectionPattern = Pattern.compile(REGEX_DIR_TO_SECTION);

    public Map<String, Section> sections = new HashMap<>();
    public Map<String, Section> dirToSections = new HashMap<>();

    public Section getSectionFromName(String name) {
        if (sections.containsKey(name)) {
            return sections.get(name);
        }

        Section s = new Section();
        s.name = name;
        sections.put(name, s);

        return s;
    }

    public boolean parseConfiguration(String dirDefinition) {
        Matcher dirToSectionMatcher = mDirToSectionPattern.matcher(dirDefinition);

        if (!dirToSectionMatcher.matches()) {
            return false;
        }

        String sectionName = dirToSectionMatcher.group(1);
        String dirPath = dirToSectionMatcher.group(2);
        Section section = getSectionFromName(sectionName);
        dirToSections.put(dirPath, section);

        return true;
    }
}
