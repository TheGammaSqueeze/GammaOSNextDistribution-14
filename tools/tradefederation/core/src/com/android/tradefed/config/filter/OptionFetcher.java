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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.TradefedFeatureClient;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.PartResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helper to get the test options from the parent process.
 */
public class OptionFetcher implements AutoCloseable {

    /** Set of options that should align with the parent process. */
    private static final Set<String> OPTION_TO_FETCH =
            ImmutableSet.of(
                    "retry-isolation-grade",
                    "avd-in-parent",
                    "enable-tracing",
                    "auto-collect",
                    "skip-retry-in-presubmit",
                    "skip-retrying-list",
                    "remote-dynamic-sharding");

    private TradefedFeatureClient mClient;

    public OptionFetcher() {
        this(new TradefedFeatureClient());
    }

    @VisibleForTesting
    OptionFetcher(TradefedFeatureClient client) {
        mClient = client;
    }

    /**
     * Fill some options from the child if it's a subprocess by matching the parent values.
     */
    public void fetchParentOptions(IConfiguration config) {
        // Skip if this is not a subprocess
        if (!TestInvocation.isSubprocess(config)) {
            return;
        }
        try {
            Map<String, String> args = new HashMap<>();
            args.put(CommandOptionsGetter.OPTION_NAME, Joiner.on(",").join(OPTION_TO_FETCH));
            FeatureResponse rep = mClient
                    .triggerFeature(CommandOptionsGetter.COMMAND_OPTIONS_GETTER, args);
            if (rep.hasErrorInfo()) {
                CLog.e("%s", rep.getErrorInfo());
                return;
            }
            if (rep.hasMultiPartResponse()) {
                for (PartResponse part : rep.getMultiPartResponse().getResponsePartList()) {
                    CLog.logAndDisplay(
                            LogLevel.DEBUG, "Fetched: %s=%s from parent.",
                            part.getKey(), part.getValue());
                    try {
                        config.injectOptionValue(part.getKey(), part.getValue());
                    } catch (ConfigurationException e) {
                        CLog.e(e);
                    }
                }
            } else if(!Strings.isNullOrEmpty(rep.getResponse())) {
                // When we have a single option, we fallback here
                try {
                    config.injectOptionValue("retry-isolation-grade", rep.getResponse().trim());
                } catch (ConfigurationException e) {
                    CLog.e(e);
                }
            }
        } catch (RuntimeException e) {
            CLog.e(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (mClient != null) {
            mClient.close();
        }
    }
}
