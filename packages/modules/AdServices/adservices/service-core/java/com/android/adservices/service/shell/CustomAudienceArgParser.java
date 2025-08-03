/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.internal.util.Preconditions.checkArgument;

import android.util.ArrayMap;
import android.util.Log;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Map;

// TODO(b/322395593): Refactor ArgParser to work better after M-04 release.
final class CustomAudienceArgParser {

    private final ImmutableList<String> mRequiredArgs;
    private final Map<String, String> mParsedArgs = new ArrayMap<>();

    CustomAudienceArgParser() {
        mRequiredArgs = ImmutableList.of();
    }

    CustomAudienceArgParser(String... requiredArgs) {
        mRequiredArgs = ImmutableList.copyOf(requiredArgs);
    }

    /**
     * Parses command line arguments with the {@code --key value} format.
     *
     * @param args list of command line args, where the first element is the invoked command.
     * @return map containing arguments.
     * @throws IllegalArgumentException if any command line argument doesn't match expected format.
     * @throws IllegalArgumentException if any required command line argument is missing.
     */
    Map<String, String> parse(String... args) {
        checkArgument(args.length > 0, "No argument was passed to ArgParser.");
        Log.v(TAG, "Parsing command line arguments: " + Arrays.toString(args));
        mParsedArgs.clear();
        for (int i = 2; i < args.length; i += 2) {
            checkArgument(
                    i + 1 < args.length,
                    "Required value for argument `%s` is not present",
                    args[i]);
            parseArgument(args[i], args[i + 1]);
        }
        verifyRequiredArgsArePresent();
        return mParsedArgs;
    }

    /**
     * Gets the value of a given command line argument by key.
     *
     * @param key Key of the argument, e.g. {@code --key value}
     * @return Value of the argument e.g. {@code --key value}
     */
    String getValue(String key) {
        checkArgument(
                mParsedArgs.containsKey(key),
                String.format("Required command line argument `%s` is not present", key));
        return mParsedArgs.get(key);
    }

    private void parseArgument(String key, String value) {
        checkArgument(
                key.startsWith("--") && !value.contains("--"),
                String.format(
                        "Command line arguments `%s %s` must use the syntax `--key value`",
                        key, value));
        key = key.substring(2); // Remove the "--".

        checkArgument(
                !Strings.isNullOrEmpty(key) && !Strings.isNullOrEmpty(value),
                String.format(
                        "Command line arguments `%s %s` must use the syntax `--key value`",
                        key, value));
        checkArgument(
                !mParsedArgs.containsKey(key),
                String.format(
                        "Command line argument with key `%s` is defined multiple times", key));
        mParsedArgs.put(key, value);
    }

    private void verifyRequiredArgsArePresent() {
        for (String arg : mRequiredArgs) {
            checkArgument(
                    mParsedArgs.containsKey(arg),
                    String.format("Required command line argument `%s` is not present", arg));
        }
    }
}
