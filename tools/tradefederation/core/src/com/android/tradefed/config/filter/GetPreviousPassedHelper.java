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
package com.android.tradefed.config.filter;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.testtype.suite.SuiteTestFilter;

import com.google.common.base.Strings;
import com.proto.tradefed.feature.FeatureResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper to get the previous passed test filters.
 */
public class GetPreviousPassedHelper {

    public GetPreviousPassedHelper() {}

    /**
     * Fetch previous passed tests if applicable from previous attempt.
     *
     * @param config The configuration to apply filter to
     * @return The list of tests that previously passed.
     */
    public Set<String> getPreviousPassedFilters(IConfiguration config) {
        Set<String> filters = new LinkedHashSet<>();
        List<SuiteTestFilter> previousPassedFilters = new ArrayList<>();
        if (config == null) {
            return filters;
        }
        if (!config.getCommandOptions().filterPreviousPassedTests()) {
            return filters;
        }
        // Build the query of previous passed test
        Map<String, String> args = new HashMap<>();
        Map<String, String> invocationData = config
                .getCommandOptions()
                .getInvocationData()
                .getUniqueMap();
        String invocationId = invocationData.get("invocation_id");
        if (!Strings.isNullOrEmpty(invocationId)) {
            args.put("invocation_id", invocationId);
        }
        if (args.isEmpty()) {
            return filters;
        }
        String attempt = invocationData.get("attempt_index");
        if ("0".equals(attempt)) {
            return filters;
        }
        // Include the shard_index in query to filter server side.
        if (config.getCommandOptions().getShardIndex() != null) {
            args.put("shard_index", Integer.toString(config.getCommandOptions().getShardIndex()));
        }
        try (TradefedFeatureClient client = new TradefedFeatureClient()) {
            FeatureResponse previousPassed = client.triggerFeature("getPreviousPassed", args);
            convertResponseToFilter(previousPassed, previousPassedFilters);
            boolean filterShards =
                    previousPassedFilters.removeIf(
                            f ->
                                    f.getShardIndex() != null
                                            && !f.getShardIndex()
                                                    .equals(
                                                            config
                                                                    .getCommandOptions()
                                                                    .getShardIndex()));
            if (filterShards) {
                CLog.d("Remaining filter for the shard: %s", previousPassedFilters);
            }
            previousPassedFilters.stream().forEach(f->filters.add(f.toString()));
        } catch (RuntimeException e) {
            CLog.e(e);
        }
        return filters;
    }

    private void convertResponseToFilter(
            FeatureResponse previousPassed, List<SuiteTestFilter> previousPassedFilters) {
        if (previousPassed.hasErrorInfo()) {
            return;
        }
        if (Strings.isNullOrEmpty(previousPassed.getResponse())) {
            return;
        }
        for (String line : previousPassed.getResponse().split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            previousPassedFilters.add(SuiteTestFilter.createFrom(line));
        }
    }
}
