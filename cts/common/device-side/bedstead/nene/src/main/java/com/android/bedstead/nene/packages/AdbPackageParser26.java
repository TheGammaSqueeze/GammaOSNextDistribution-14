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

package com.android.bedstead.nene.packages;

import static com.android.bedstead.nene.utils.ParserUtils.extractIndentedSections;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.users.UserReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for `adb dumpsys package` on Android O+.
 *
 * <p>This class is structured so that future changes in ADB output can be dealt with by extending
 * this class and overriding the appropriate section parsers.
 */
@RequiresApi(Build.VERSION_CODES.O)
public class AdbPackageParser26 implements AdbPackageParser {

    private static final int PACKAGE_LIST_BASE_INDENTATION = 2;

    AdbPackageParser26() {
    }

    @Override
    public ParseResult parse(String dumpsysPackageOutput) throws AdbParseException {
        ParseResult parseResult = new ParseResult();
        parseResult.mFeatures = parseFeatures(dumpsysPackageOutput);
        parseResult.mPackages = parsePackages(dumpsysPackageOutput);
        return parseResult;
    }

    Set<String> parseFeatures(String dumpsysPackageOutput) throws AdbParseException {
        String featuresList = extractFeaturesList(dumpsysPackageOutput);
        Set<String> features = new HashSet<>();
        for (String featureLine : featuresList.split("\n")) {
            features.add(featureLine.trim());
        }
        return features;
    }

    String extractFeaturesList(String dumpsysPackageOutput) throws AdbParseException {
        try {
            return dumpsysPackageOutput.split("Features:\n", 2)[1].split("\n\n", 2)[0];
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error extracting features list", dumpsysPackageOutput, e);
        }
    }

    Map<String, AdbPackage> parsePackages(String dumpsysUsersOutput) throws AdbParseException {
        String packagesList = extractPackagesList(dumpsysUsersOutput);

        Set<String> packageStrings = extractPackageStrings(packagesList);
        Map<String, AdbPackage> packages = new HashMap<>();
        for (String packageString : packageStrings) {
            AdbPackage pkg = new AdbPackage(parsePackage(packageString));
            packages.put(pkg.packageName(), pkg);
        }
        return packages;
    }

    String extractPackagesList(String dumpsysPackageOutput) throws AdbParseException {
        try {
            return dumpsysPackageOutput.split("\nPackages:\n", 2)[1].split("\n\n", 2)[0];
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error extracting packages list", dumpsysPackageOutput, e);
        }
    }

    Set<String> extractPackageStrings(String packagesList) throws AdbParseException {
        return extractIndentedSections(packagesList, PACKAGE_LIST_BASE_INDENTATION);
    }

    private static final Pattern USER_INSTALLED_PATTERN =
            Pattern.compile("User (\\d+):.*?installed=(\\w+)");

    AdbPackage.MutablePackage parsePackage(String packageString) throws AdbParseException {
        try {
            String packageName = packageString.split("\\[", 2)[1].split("]", 2)[0];
            AdbPackage.MutablePackage pkg = new AdbPackage.MutablePackage();
            pkg.mPackageName = packageName;
            pkg.mInstalledOnUsers = new HashMap<>();
            pkg.mInstallPermissions = new HashSet<>();

            Set<String> sections = extractIndentedSections(
                    packageString.split("\n", 2)[1], // Remove first line (package name)
                    /* baseIndentation= */ 4);

            for (String section : sections) {
                if (section.startsWith("install permissions")) {
                    parseInstallPermissions(section, pkg);
                } else if (section.startsWith("User ")) {
                    parseUser(section, pkg);
                }
            }

            return pkg;
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new AdbParseException("Error parsing package", packageString, e);
        }
    }

    void parseInstallPermissions(String section, AdbPackage.MutablePackage pkg) {
        String list = section.split("\n", 2)[1]; // remove header
        for (String item : list.split("\n")) {
            String[] trimmed = item.trim().split(":", 2);
            String permissionName = trimmed[0];

            if (trimmed[1].contains("granted=true")) {
                pkg.mInstallPermissions.add(permissionName);
            }
        }
    }

    void parseUser(String section, AdbPackage.MutablePackage pkg) throws AdbParseException {
        Matcher userInstalledMatcher = USER_INSTALLED_PATTERN.matcher(section);
        if (!userInstalledMatcher.find()) {
            throw new AdbParseException("Error parsing user section in package", section);
        }
        int userId = Integer.parseInt(userInstalledMatcher.group(1));
        boolean isInstalled = Boolean.parseBoolean(userInstalledMatcher.group(2));

        if (!isInstalled) {
            return;
        }

        UserReference user = TestApis.users().find(userId);
        AdbPackage.MutableUserPackage userPackage = new AdbPackage.MutableUserPackage();
        userPackage.mGrantedPermissions = new HashSet<>();
        pkg.mInstalledOnUsers.put(user, userPackage);


        try {
            String[] sectionParts = section.split("\n", 2); // remove header

            if (sectionParts.length < 2) {
                return; // No content - just the header
            }

            Set<String> userSections = extractIndentedSections(
                    sectionParts[1], // Remove first line (user name)
                    /* baseIndentation= */ 6);

            for (String userSection : userSections) {
                if (userSection.startsWith("runtime permissions:")) {
                    String list = userSection.split("\n", 2)[1]; // remove header
                    for (String item : list.split("\n")) {
                        String[] trimmed = item.trim().split(":", 2);
                        String permissionName = trimmed[0];

                        if (trimmed[1].contains("granted=true")) {
                            userPackage.mGrantedPermissions.add(permissionName);
                        }
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error parsing user section", section, e);
        }
    }
}
