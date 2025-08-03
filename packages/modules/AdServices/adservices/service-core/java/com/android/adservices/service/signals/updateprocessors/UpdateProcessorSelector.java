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

package com.android.adservices.service.signals.updateprocessors;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Selector class for getting the appropriate update processor */
public class UpdateProcessorSelector {

    private static final List<UpdateProcessor> PROCESSORS =
            Arrays.asList(
                    new Append(),
                    new Put(),
                    new PutIfNotPresent(),
                    new Remove(),
                    new UpdateEncoder());
    private final Map<String, UpdateProcessor> mProcessorMap;

    public UpdateProcessorSelector() {
        mProcessorMap =
                PROCESSORS.stream().collect(Collectors.toMap(UpdateProcessor::getName, p -> p));
    }

    /**
     * Get the appropriate processor given a String taken from the signals update JSON top level
     * keys.
     *
     * @param key The key representing the processor
     * @return The appropriate processor.
     */
    public UpdateProcessor getUpdateProcessor(String key) {
        if (!mProcessorMap.containsKey(key)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid signal update command, valid commands are %s",
                            mProcessorMap.keySet()));
        }
        return mProcessorMap.get(key);
    }
}
