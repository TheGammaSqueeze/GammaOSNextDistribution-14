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

package com.android.telephony.tools.sats2;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/** Creates a Sat S2 file from the list of S2 cells. */
public final class CreateSatS2File {
    /**
     * Usage:
     * CreateSatS2File <[input] s2 cells file> <[input] s2 level of input data>
     *     <[input] whether s2 cells is an allowed list> <[output] sat s2 file>
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        String inputFile = arguments.inputFile;
        int s2Level = arguments.s2Level;
        String outputFile = arguments.outputFile;
        boolean isAllowedList = Arguments.getBooleanValue(arguments.isAllowedList);
        SatS2FileCreator.create(inputFile, s2Level, isAllowedList, outputFile);
    }

    private static class Arguments {
        @Parameter(names = "--input-file",
                description = "s2 cells file",
                required = true)
        public String inputFile;

        @Parameter(names = "--s2-level",
                description = "s2 level of input data",
                required = true)
        public int s2Level;

        @Parameter(names = "--is-allowed-list",
                description = "whether s2 cells file contains an allowed list of cells",
                required = true)
        public String isAllowedList;

        @Parameter(names = "--output-file",
                description = "sat s2 file",
                required = true)
        public String outputFile;

        public static Boolean getBooleanValue(String value) {
            if ("false".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            } else {
                throw new ParameterException("Invalid boolean string:" + value);
            }
        }
    }
}
