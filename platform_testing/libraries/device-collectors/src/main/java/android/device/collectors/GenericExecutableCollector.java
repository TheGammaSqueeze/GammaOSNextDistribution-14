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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;

import com.android.helpers.GenericExecutableCollectorHelper;

/**
 * A {@link GenericExecutableCollector} a generic metric collector that collects metrics from
 * arbitrary executables that output metrics in CSV format to handle all types of executable file
 * execution and extract the metrics into a standard format. For more details: go/generic-collector
 */
@OptionClass(alias = "generic-executable-collector")
public class GenericExecutableCollector extends BaseCollectionListener<String> {
    private static final String TAG = GenericExecutableCollector.class.getSimpleName();

    static final String EXECUTABLE_DIR = "executable-dir";
    static final String DEFAULT_EXECUTABLE_DIR = "/data/generic_executable_collector/";

    private GenericExecutableCollectorHelper mGenericExecutableCollectorHelper =
            new GenericExecutableCollectorHelper();

    public GenericExecutableCollector() {
        createHelperInstance(mGenericExecutableCollectorHelper);
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        String executableDir = args.getString(EXECUTABLE_DIR, DEFAULT_EXECUTABLE_DIR);
        mGenericExecutableCollectorHelper.setUp(executableDir);
    }
}
