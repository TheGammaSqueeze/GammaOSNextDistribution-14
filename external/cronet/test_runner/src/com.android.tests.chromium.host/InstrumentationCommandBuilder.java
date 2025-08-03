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

package com.android.tests.chromium.host;

import com.android.tradefed.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class InstrumentationCommandBuilder {
    private final List<Pair<String, String>> arguments;
    private final String activityName;
    // Instrument and wait until execution has finished before returning
    private static final String BASE_CMD = "am instrument -w ";

    public InstrumentationCommandBuilder(String activity) {
        this.activityName = activity;
        this.arguments = new ArrayList<>();
    }

    public InstrumentationCommandBuilder addArgument(String key, String value) {
        arguments.add(new Pair<>(key, value));
        return this;
    }


    private void appendTupleToCommand(StringBuilder cmd, String key, String value) {
        cmd.append("-e ");
        cmd.append(key).append(" ").append(value).append(" ");
    }

    public String build() {
        StringBuilder commandAsString = new StringBuilder(BASE_CMD);
        for (Pair<String, String> arg : arguments) {
            appendTupleToCommand(commandAsString, arg.first, arg.second);
        }
        commandAsString.append(activityName);
        return commandAsString.toString();
    }
}

