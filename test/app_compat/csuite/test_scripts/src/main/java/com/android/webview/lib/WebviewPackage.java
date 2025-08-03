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

package com.android.webview.tests;

import com.android.tradefed.util.AaptParser;

import org.junit.Assert;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebviewPackage implements Comparable<WebviewPackage> {
    private final String mPackageName;
    private final String mVersion;
    private final long mVersionCode;
    private Path mApkPath;

    public WebviewPackage(String packageName, String version, long versionCode, Path apkPath) {
        this(packageName, version, versionCode);
        mApkPath = apkPath;
    }

    public WebviewPackage(String packageName, String version, long versionCode) {
        mPackageName = packageName;
        mVersion = version;
        mVersionCode = versionCode;
    }

    public static WebviewPackage buildFromApk(Path apkPath) {
        AaptParser aaptParser = AaptParser.parse(apkPath.toFile());
        return new WebviewPackage(
                aaptParser.getPackageName(),
                aaptParser.getVersionName(),
                Long.parseLong(aaptParser.getVersionCode()),
                apkPath);
    }

    public static WebviewPackage buildFromDumpsys(String dumpsys) {
        String regexPattern = "Current WebView package \\(name, version\\): \\((.*), (.*)\\)";
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(dumpsys);
        Assert.assertTrue(
                String.format(
                        "Cannot find a sub string matching the regex in the dumpsys\n%s", dumpsys),
                matcher.find());
        return buildFromDumpsys(matcher.group(1), dumpsys);
    }

    public static WebviewPackage buildFromDumpsys(String webviewPackage, String dumpsys) {
        String regexPattern =
                String.format(
                        "Valid package %s \\(versionName: (.*), versionCode: (\\d+),"
                                + " targetSdkVersion: (\\d+)\\)",
                        webviewPackage.replace(".", "\\."));
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(dumpsys);
        Assert.assertTrue(
                String.format(
                        "Cannot find a sub string matching the regex in the dumpsys\n%s", dumpsys),
                matcher.find());
        return new WebviewPackage(
                webviewPackage, matcher.group(1), Long.parseLong(matcher.group(2)));
    }

    public Path getPath() {
        Assert.assertTrue(
                "The apk path was not set for this WebviewPackage instance", mApkPath != null);
        return mApkPath;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getVersion() {
        return mVersion;
    }

    public long getVersionCode() {
        return mVersionCode;
    }

    @Override
    public int compareTo(WebviewPackage otherWebviewPkg) {
        return Long.compare(this.getVersionCode(), otherWebviewPkg.getVersionCode());
    }

    @Override
    public boolean equals(final Object obj) {
        final WebviewPackage otherWebviewPkg = (WebviewPackage) obj;
        return this.getPackageName().equals(otherWebviewPkg.getPackageName())
                && this.getVersion().equals(otherWebviewPkg.getVersion());
    }
}
