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
package com.android.tradefed.config.proxy;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.proto.FileProtoResultReporter;
import com.android.tradefed.result.proto.StreamProtoResultReporter;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.util.List;

/**
 * Class that defines the mapping from Tradefed automated reporters.
 *
 * <p>TODO: Formalize how to expose the list of supported automation.
 */
public class AutomatedReporters {

    public static final String PROTO_REPORTING_PORT = "PROTO_REPORTING_PORT";
    public static final String PROTO_REPORTING_FILE = "PROTO_REPORTING_FILE";
    public static final String PROTO_REPORTING_FILE_GRANULAR = "PROTO_REPORTING_FILE_GRANULAR";
    public static final ImmutableSet<String> REPORTER_MAPPING =
            ImmutableSet.of(
                    PROTO_REPORTING_PORT, PROTO_REPORTING_FILE, PROTO_REPORTING_FILE_GRANULAR);

    /**
     * Complete the listeners based on the environment.
     *
     * @param configuration The configuration to complete
     */
    public void applyAutomatedReporters(IConfiguration configuration) {
        for (String key : REPORTER_MAPPING) {
            String envValue = getEnv(key);
            if (envValue == null) {
                continue;
            }
            switch (key) {
                case PROTO_REPORTING_PORT:
                    StreamProtoResultReporter streamProto = new StreamProtoResultReporter();
                    try {
                        streamProto.setProtoReportPort(Integer.parseInt(envValue));
                        List<ITestInvocationListener> listeners =
                                configuration.getTestInvocationListeners();
                        listeners.add(streamProto);
                        configuration.setTestInvocationListeners(listeners);
                    } catch (NumberFormatException e) {
                        CLog.e(e);
                    }
                    break;
                case PROTO_REPORTING_FILE:
                    FileProtoResultReporter fileReporter = new FileProtoResultReporter();
                    fileReporter.setOutputFile(new File(envValue));
                    fileReporter.setDelimitedOutput(false);
                    // Aggregate results for this option
                    fileReporter.setGranularResults(false);
                    addToReporters(configuration, fileReporter);
                    break;
                case PROTO_REPORTING_FILE_GRANULAR:
                    FileProtoResultReporter fileReporterGranular = new FileProtoResultReporter();
                    fileReporterGranular.setOutputFile(new File(envValue));
                    fileReporterGranular.setDelimitedOutput(false);
                    fileReporterGranular.setGranularResults(true);
                    addToReporters(configuration, fileReporterGranular);
                    break;
                default:
                    break;
            }
        }
    }

    @VisibleForTesting
    protected String getEnv(String key) {
        return System.getenv(key);
    }

    private void addToReporters(IConfiguration configuration, ITestInvocationListener reporter) {
        List<ITestInvocationListener> listeners = configuration.getTestInvocationListeners();
        listeners.add(reporter);
        configuration.setTestInvocationListeners(listeners);
    }
}
