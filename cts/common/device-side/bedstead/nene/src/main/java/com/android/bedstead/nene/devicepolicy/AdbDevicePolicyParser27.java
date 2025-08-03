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

package com.android.bedstead.nene.devicepolicy;

import android.content.ComponentName;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ParserUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parser of `adb shell dumpsys device_policy` for Android 27+.
 */
public class AdbDevicePolicyParser27 implements AdbDevicePolicyParser {

    static final int DEFAULT_INDENTATION = 2;

    AdbDevicePolicyParser27() {
    }

    @Override
    public ParseResult parse(String dumpsysDevicePolicyOutput) throws AdbParseException {
        ParseResult parseResult = new ParseResult();

        Set<String> sections = extractDevicePolicySections(dumpsysDevicePolicyOutput);

        parseResult.mDeviceOwner = parseDeviceOwner(sections);
        parseResult.mProfileOwners = parseProfileOwners(sections);
        return parseResult;
    }

    private Set<String> extractDevicePolicySections(String dumpsysDevicePolicyOutput)
            throws AdbParseException {
        // Remove first line ("Current Device Policy Manager state:")
        dumpsysDevicePolicyOutput = dumpsysDevicePolicyOutput.split("\n", 2)[1];

        return ParserUtils.extractIndentedSections(dumpsysDevicePolicyOutput, DEFAULT_INDENTATION);
    }

    DeviceOwner parseDeviceOwner(Set<String> devicePolicySections) {
        String deviceOwnerSection = getDeviceOwnerSection(devicePolicySections);
        if (deviceOwnerSection == null) {
            return null;
        }

        ComponentName componentName = ComponentName.unflattenFromString(
                deviceOwnerSection.split(
                        "ComponentInfo\\{", 2)[1].split("\\}", 2)[0]);
        int userId = Integer.parseInt(deviceOwnerSection.split(
                "User ID: ", 2)[1].split("\n", 2)[0]);
        return new DeviceOwner(TestApis.users().find(userId),
                TestApis.packages().find(componentName.getPackageName()), componentName);
    }

    String getDeviceOwnerSection(Set<String> devicePolicySections) {
        for (String section : devicePolicySections) {
            if (section.startsWith("Device Owner:")) {
                return section;
            }
        }

        return null;
    }

    Map<UserReference, ProfileOwner> parseProfileOwners(Set<String> devicePolicySections) {
        Set<String> profileOwnerSections = getProfileOwnerSections(devicePolicySections);
        Map<UserReference, ProfileOwner> profileOwners = new HashMap<>();

        for (String profileOwnerSection : profileOwnerSections) {
            ProfileOwner profileOwner = extractProfileOwner(profileOwnerSection);
            profileOwners.put(profileOwner.user(), profileOwner);
        }

        return profileOwners;
    }

    Set<String> getProfileOwnerSections(Set<String> devicePolicySections) {
        Set<String> sections = new HashSet<>();
        for (String section : devicePolicySections) {
            if (section.startsWith("Profile Owner (User ")) {
                sections.add(section);
            }
        }

        return sections;
    }

    ProfileOwner extractProfileOwner(String profileOwnerSection) {
        ComponentName componentName = ComponentName.unflattenFromString(
                profileOwnerSection.split(
                        "ComponentInfo\\{", 2)[1].split("\\}", 2)[0]);
        int userId = Integer.parseInt(
                profileOwnerSection.split("\\(User ", 2)[1].split("\\)", 2)[0]);
        return new ProfileOwner(TestApis.users().find(userId),
                TestApis.packages().find(componentName.getPackageName()), componentName);
    }
}
