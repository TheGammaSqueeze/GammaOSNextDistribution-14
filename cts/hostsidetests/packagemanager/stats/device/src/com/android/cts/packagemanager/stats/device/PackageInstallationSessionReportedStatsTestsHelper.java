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

package com.android.cts.packagemanager.stats.device;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.UserManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class PackageInstallationSessionReportedStatsTestsHelper {
    private static final String USER_IDS_ARG = "userIds";
    private static final String USER_TYPES_ARG = "userTypes";
    // Instrumentation status code used to write resolution to metrics
    private static final int INST_STATUS_IN_PROGRESS = 2;

    @Test
    public void getUserTypeIntegers() {
        final Bundle testArgs = InstrumentationRegistry.getArguments();
        final String userIdsString = testArgs.getString(USER_IDS_ARG);
        assertThat(userIdsString).isNotNull();
        String[] userIds = userIdsString.split(",");
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final UserManager userManager = inst.getTargetContext().getSystemService(UserManager.class);
        assertThat(userManager).isNotNull();
        final UiAutomation uiAutomation = inst.getUiAutomation();

        final ArrayList<Integer> results = new ArrayList<>();
        try {
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.QUERY_USERS);
            for (String userId : userIds) {
                results.add(UserInfoUtil.getUserTypeForStatsd(
                        userManager.getUserInfo(Integer.parseInt(userId)).userType));
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        // Pass data to the host-side test
        Bundle bundle = new Bundle();
        bundle.putString(USER_TYPES_ARG,
                results.stream().map(Object::toString).collect(Collectors.joining(",")));
        inst.sendStatus(INST_STATUS_IN_PROGRESS, bundle);
    }
}
