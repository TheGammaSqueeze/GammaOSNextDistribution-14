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

package vogar.target.junit;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExcludeFilter extends Filter {

    private final Set<String> excludeFilters;

    public ExcludeFilter(String[] excludeFilters) {
        Set<String> set = new HashSet<>();
        if (excludeFilters != null) {
            set.addAll(List.of(excludeFilters));
        }
        this.excludeFilters = set;
    }

    @Override
    public boolean shouldRun(Description description) {
        return !description.getAnnotations().stream()
                .anyMatch(annotation ->
                        excludeFilters.contains(annotation.annotationType().getName()));
    }

    @Override
    public String describe() {
        return "ExcludeFilter[" + String.join(",", excludeFilters) + "]";
    }
}
