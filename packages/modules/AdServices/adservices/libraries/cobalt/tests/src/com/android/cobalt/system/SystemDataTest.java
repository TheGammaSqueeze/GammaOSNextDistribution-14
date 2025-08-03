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

package com.android.cobalt.system;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.runner.AndroidJUnit4;

import com.google.cobalt.ReportDefinition;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.SystemProfileField;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SystemDataTest {
    private static final String APP_VERSION = "1.2.3.4";

    private final SystemData mSystemData = new SystemData(APP_VERSION);

    @Test
    public void filteredSystemProfile_onlyAppVersion_succeeds() throws Exception {
        assertThat(
                        mSystemData.filteredSystemProfile(
                                ReportDefinition.newBuilder()
                                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                                        .build()))
                .isEqualTo(SystemProfile.newBuilder().setAppVersion(APP_VERSION).build());
    }

    @Test
    public void filteredSystemProfile_appVersionMissing_succeeds() throws Exception {
        SystemData systemData = new SystemData();
        assertThat(
                        systemData.filteredSystemProfile(
                                ReportDefinition.newBuilder()
                                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                                        .build()))
                .isEqualTo(SystemProfile.getDefaultInstance());
    }

    @Test
    public void filteredSystemProfile_appVersionNull_succeeds() throws Exception {
        SystemData systemData = new SystemData(null);
        assertThat(
                        systemData.filteredSystemProfile(
                                ReportDefinition.newBuilder()
                                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                                        .build()))
                .isEqualTo(SystemProfile.getDefaultInstance());
    }

    @Test
    public void filteredSystemProfile_allNonBuildFields_succeeds() throws Exception {
        // ARCH, BOARD_NAME and SYSTEM_VERSION are dependent on the build of the test, and so
        // can change.
        assertThat(
                        mSystemData.filteredSystemProfile(
                                ReportDefinition.newBuilder()
                                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                                        .addSystemProfileField(SystemProfileField.BUILD_TYPE)
                                        .addSystemProfileField(SystemProfileField.CHANNEL)
                                        .addSystemProfileField(SystemProfileField.EXPERIMENT_IDS)
                                        .addSystemProfileField(SystemProfileField.OS)
                                        .addSystemProfileField(SystemProfileField.PRODUCT_NAME)
                                        .build()))
                .isEqualTo(
                        SystemProfile.newBuilder()
                                .setAppVersion(APP_VERSION)
                                .setOs(SystemProfile.OS.ANDROID)
                                .build());
    }

    @Test
    public void filteredSystemProfile_unrecognized_throwsException() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mSystemData.filteredSystemProfile(
                                ReportDefinition.newBuilder()
                                        .addSystemProfileFieldValue(1234546678)
                                        .build()));
    }

    @Test
    public void filteredSystemProfile_noExperimentIds_succeeds() throws Exception {
        assertThat(
                        mSystemData.filteredSystemProfile(
                                ReportDefinition.newBuilder()
                                        .addSystemProfileField(SystemProfileField.EXPERIMENT_IDS)
                                        .build()))
                .isEqualTo(SystemProfile.getDefaultInstance());
    }
}
