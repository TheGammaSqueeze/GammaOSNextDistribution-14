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

package com.android.cts.appcompat;

import static com.google.common.truth.Truth.assertThat;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for the {@link android.app.compat.CompatChanges} SystemApi.
 */

public class CompatChangesSelinuxTest extends CompatChangeGatingTestCase {

    protected static final String Q_TEST_APK = "CtsSelinuxQCompatApp.apk";
    protected static final String R_TEST_APK = "CtsSelinuxRCompatApp.apk";

    protected static final String TEST_PKG = "com.android.cts.appcompat.selinux_app";

    private static final long SELINUX_LATEST_CHANGES = 143539591L;
    private static final long SELINUX_R_CHANGES = 168782947L;

    private static final Pattern PS_ENTRY_PATTERN = Pattern.compile("^(?<label>\\S+)\\s+(?<name>\\S+)");


    public void testTargetSdkQAppIsInQDomainByDefault() throws Exception {
        installPackage(Q_TEST_APK, false);
        try {
            startApp();
            Map<String, String> packageToDomain = getPackageToDomain();

            assertThat(packageToDomain).containsEntry(TEST_PKG, "untrusted_app_29");
        } finally {
            uninstallPackage(TEST_PKG, true);
        }
    }

    public void testTargetSdkQAppIsInLatestDomainWithLatestOptin() throws Exception {
        final Set<Long> enabledChanges = ImmutableSet.of(SELINUX_LATEST_CHANGES);
        final Set<Long> disabledChanges = ImmutableSet.of();
        final long configId = getClass().getCanonicalName().hashCode();

        installPackage(Q_TEST_APK, false);
        Thread.currentThread().sleep(200);
        setCompatConfig(enabledChanges, disabledChanges, TEST_PKG);

        try {
            startApp();
            Map<String, String> packageToDomain = getPackageToDomain();

            assertThat(packageToDomain).containsEntry(TEST_PKG, "untrusted_app");

        } finally {
            resetCompatConfig(TEST_PKG, enabledChanges, disabledChanges);
            uninstallPackage(TEST_PKG, true);
        }
    }

    public void testTargetSdkQAppIsInRDomainWithROptin() throws Exception {
        final Set<Long> enabledChanges = ImmutableSet.of(SELINUX_R_CHANGES);
        final Set<Long> disabledChanges = ImmutableSet.of();

        installPackage(Q_TEST_APK, false);
        Thread.currentThread().sleep(200);
        setCompatConfig(enabledChanges, disabledChanges, TEST_PKG);

        try {
            startApp();
            Map<String, String> packageToDomain = getPackageToDomain();
            assertThat(packageToDomain).containsEntry(TEST_PKG, "untrusted_app_30");

        } finally {
            resetCompatConfig(TEST_PKG, enabledChanges, disabledChanges);
            uninstallPackage(TEST_PKG, true);
        }
    }

    public void testTargetSdkRAppIsInRDomainByDefault() throws Exception {
        installPackage(R_TEST_APK, false);
        try {
            startApp();
            Map<String, String> packageToDomain = getPackageToDomain();

            assertThat(packageToDomain).containsEntry(TEST_PKG, "untrusted_app_30");
        } finally {
            uninstallPackage(TEST_PKG, true);
        }
    }

    public void testTargetSdkRAppIsInLatestDomainWithLatestOptin() throws Exception {
        final Set<Long> enabledChanges = ImmutableSet.of(SELINUX_LATEST_CHANGES);
        final Set<Long> disabledChanges = ImmutableSet.of();
        installPackage(R_TEST_APK, false);
        Thread.currentThread().sleep(200);
        setCompatConfig(enabledChanges, disabledChanges, TEST_PKG);

        try {
            startApp();
            Map<String, String> packageToDomain = getPackageToDomain();
            assertThat(packageToDomain).containsEntry(TEST_PKG, "untrusted_app");
        } finally {
            resetCompatConfig(TEST_PKG, enabledChanges, disabledChanges);
            uninstallPackage(TEST_PKG, true);
        }
    }

    private Map<String, String> getPackageToDomain() throws Exception {
        Map<String, String> packageToDomain = new HashMap<>();
        String output = getDevice().executeShellCommand("ps -e -o LABEL,NAME");
        String[] lines = output.split("\n");
        for (int i = 1; i < lines.length; ++i) {
            String line = lines[i];
            Matcher matcher = PS_ENTRY_PATTERN.matcher(line);
            if (!matcher.matches())
                continue;
            String label = matcher.group("label");
            String domain = label.split(":")[2];
            String packageName = matcher.group("name");
            packageToDomain.put(packageName, domain);
        }
        return packageToDomain;
    }

    private void startApp() throws Exception {
        runCommand("am start-activity -W -n " + TEST_PKG + "/" + TEST_PKG + ".Empty");
    }


}
