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

package android.linkerconfig.gts.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.linkerconfig.gts.utils.elements.Configuration;
import android.linkerconfig.gts.utils.elements.Section;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkerConfigParser {
    static final String REGEX_SECTION_NAME = "\\[\\s*(\\w+)\\s*\\]";
    static Pattern sSectionNamePattern = Pattern.compile(REGEX_SECTION_NAME);

    public static Configuration parseConfiguration(List<String> contents) {
        Configuration configuration = new Configuration();
        Section currentSection = null;

        for (String content : contents) {
            if (content.isEmpty()) {
                continue;
            }

            Matcher sectionNameMatcher = sSectionNamePattern.matcher(content);
            if (sectionNameMatcher.matches()) {
                String sectionName = sectionNameMatcher.group(1);
                assertTrue("Section should have been defined from dir to section map",
                        configuration.sections.containsKey(sectionName));
                currentSection = configuration.getSectionFromName(sectionName);
                continue;
            }

            if (currentSection == null && configuration.parseConfiguration(content)) {
                continue;
            }

            assertNotNull("This line cannot be parsed without section : " + content,
                    currentSection);

            currentSection.parseConfiguration(content);
        }

        return configuration;
    }
}
