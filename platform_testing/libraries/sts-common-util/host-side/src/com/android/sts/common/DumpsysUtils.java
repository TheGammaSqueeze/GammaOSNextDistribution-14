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

package com.android.sts.common;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Util to parse dumpsys */
public class DumpsysUtils {

    /**
     * Fetch dumpsys for the service
     *
     * @param device the device {@link ITestDevice} to use.
     * @param args the arguments {@link String} to filter output
     * @return the raw output without newline character
     * @throws Exception
     */
    public static String getRawDumpsys(ITestDevice device, String args) throws Exception {
        CommandResult output = device.executeShellV2Command("dumpsys " + args);
        if (output.getStatus() != CommandStatus.SUCCESS) {
            throw new IllegalStateException(
                    String.format(
                            "Failed to get dumpsys for %s details, due to : %s",
                            args, output.toString()));
        }
        return output.getStdout();
    }

    /**
     * Parse the dumpsys for the service using pattern
     *
     * @param device the device {@link ITestDevice} to use.
     * @param service the service name {@link String} to check status.
     * @param args the argument {@link Map} to filter output
     * @param pattern the pattern {@link String} to parse the dumpsys output
     * @param matcherFlag the flags {@link String} to look while parsing the dumpsys output
     * @return the required value.
     * @throws Exception
     */
    public static Matcher getParsedDumpsys(
            ITestDevice device,
            String service,
            Map<String, String> args,
            String pattern,
            int matcherFlag)
            throws Exception {
        String arguments =
                args == null
                        ? ""
                        : args.entrySet().stream()
                                .map(arg -> String.format("%s %s", arg.getKey(), arg.getValue()))
                                .collect(Collectors.joining(" "));
        String rawOutput = getRawDumpsys(device, String.format("%s %s", service, arguments));
        rawOutput =
                String.join(
                        "",
                        Arrays.stream(rawOutput.split("\n"))
                                .map(e -> e.trim())
                                .toArray(String[]::new));
        return Pattern.compile(pattern, matcherFlag).matcher(rawOutput);
    }

    /**
     * Parse the dumpsys for the service using pattern
     *
     * @param device the device {@link ITestDevice} to use.
     * @param service the service name {@link String} to check status.
     * @param pattern the pattern {@link String} to parse the dumpsys output
     * @param matcherFlag the flags {@link String} to look while parsing the dumpsys output
     * @return the required value.
     * @throws Exception
     */
    public static Matcher getParsedDumpsys(
            ITestDevice device, String service, String pattern, int matcherFlag) throws Exception {
        return getParsedDumpsys(device, service, null /* args */, pattern, matcherFlag);
    }

    /**
     * Check if output contains mResumed=true for the activity
     *
     * @param device the device {@link ITestDevice} to use.
     * @param activityName the activity name {@link String} to check status.
     * @return true, if mResumed=true. Else false.
     * @throws Exception
     */
    public static boolean hasActivityResumed(ITestDevice device, String activityName)
            throws Exception {
        return getParsedDumpsys(
                        device,
                        "activity" /* service */,
                        Map.of("-a", activityName) /* args */,
                        "mResumed=true" /* pattern */,
                        Pattern.CASE_INSENSITIVE /* matcherFlag */)
                .find();
    }

    /**
     * Check if output contains mVisible=true for the activity
     *
     * @param device the device {@link ITestDevice} to use.
     * @param activityName the activity name {@link String} to check status.
     * @return true, if mVisible=true. Else false.
     * @throws Exception
     */
    public static boolean isActivityVisible(ITestDevice device, String activityName)
            throws Exception {
        return getParsedDumpsys(
                        device,
                        "activity" /* service */,
                        Map.of("-a", activityName) /* args */,
                        "mVisible=true" /* pattern */,
                        Pattern.CASE_INSENSITIVE /* matcherFlag */)
                .find();
    }

    /**
     * Fetch the role-holder-name for the role-name under the userid
     *
     * @param device the device {@link ITestDevice} to use.
     * @param roleName the role name {@link String} to fetch role holder's name.
     * @param userId the userid {@link int} to fetch role holder's name for the user.
     * @return holder name, if exits. Else null.
     * @throws Exception
     */
    public static String getRoleHolder(ITestDevice device, String roleName, int userId)
            throws Exception {
        // Fetch roles for the user
        Matcher rolesMatcher =
                getParsedDumpsys(
                        device,
                        "role" /* service */,
                        String.format("user_id=%d.+?roles=(?<roles>\\[.+?])", userId) /* pattern */,
                        Pattern.CASE_INSENSITIVE);
        if (!rolesMatcher.find()) {
            return null;
        }

        // Fetch the holder's name for the role
        Matcher holderMatcher =
                Pattern.compile(
                                String.format("\\{name=%sholders=(?<holders>.+?)}", roleName),
                                Pattern.CASE_INSENSITIVE)
                        .matcher(rolesMatcher.group("roles"));
        if (!holderMatcher.find()) {
            return null;
        }
        return holderMatcher.group("holders").trim();
    }

    /**
     * Fetch the role-holder-name for the role-name
     *
     * @param device the device {@link ITestDevice} to use.
     * @param roleName the role name {@link String} to fetch role holder's name for the current
     *     user.
     * @return holder name, if exits. Else null.
     * @throws Exception
     */
    public static String getRoleHolder(ITestDevice device, String roleName) throws Exception {
        return getRoleHolder(device, roleName, device.getCurrentUser());
    }
}
