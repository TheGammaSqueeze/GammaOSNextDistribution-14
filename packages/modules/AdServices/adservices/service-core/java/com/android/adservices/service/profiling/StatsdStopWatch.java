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

package com.android.adservices.service.profiling;

import com.android.adservices.service.stats.AdServicesStatsLog;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/** StopWatch implementation that writes metrics to statsd */
public class StatsdStopWatch implements StopWatch {
    private static final Map<String, Integer> sStatsdCodeMap =
            ImmutableMap.of(
                    JSScriptEngineLogConstants.SANDBOX_INIT_TIME,
                    AdServicesStatsLog.JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__SANDBOX_INIT,
                    JSScriptEngineLogConstants.ISOLATE_CREATE_TIME,
                    AdServicesStatsLog.JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__ISOLATE_CREATE,
                    JSScriptEngineLogConstants.JAVA_EXECUTION_TIME,
                    AdServicesStatsLog
                            .JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__JAVA_PROCESS_EXECUTION,
                    JSScriptEngineLogConstants.WEBVIEW_EXECUTION_TIME,
                    AdServicesStatsLog
                            .JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__WEBVIEW_PROCESS_EXECUTION);

    private String mName;

    private long mStartTime = System.currentTimeMillis();
    private long mEndTime;
    private boolean mStopped;

    public StatsdStopWatch(String name) {
        mName = name;
    }

    @Override
    public void stop() {
        if (mStopped) {
            return;
        }

        mStopped = true;
        mEndTime = System.currentTimeMillis() - mStartTime;
        int code = sStatsdCodeMap.get(mName);
        AdServicesStatsLog.write(
                AdServicesStatsLog.JSSCRIPTENGINE_LATENCY_REPORTED, code, mEndTime);
    }
}
