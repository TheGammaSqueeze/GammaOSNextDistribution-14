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

package com.android.bedstead.nene;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestApisTest {

    @Test
    public void users_returnsInstance() {
        Truth.assertThat(TestApis.users()).isNotNull();
    }

    @Test
    public void users_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.users()).isEqualTo(TestApis.users());
    }

    @Test
    public void packages_returnsInstance() {
        Truth.assertThat(TestApis.packages()).isNotNull();
    }

    @Test
    public void packages_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.packages()).isEqualTo(TestApis.packages());
    }

    @Test
    public void devicePolicy_returnsInstance() {
        Truth.assertThat(TestApis.devicePolicy()).isNotNull();
    }

    @Test
    public void devicePolicy_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.devicePolicy()).isEqualTo(TestApis.devicePolicy());
    }

    @Test
    public void permissions_returnsInstance() {
        Truth.assertThat(TestApis.permissions()).isNotNull();
    }

    @Test
    public void permissions_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.permissions()).isEqualTo(TestApis.permissions());
    }

    @Test
    public void context_returnsInstance() {
        Truth.assertThat(TestApis.context()).isNotNull();
    }

    @Test
    public void context_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.context()).isEqualTo(TestApis.context());
    }

    @Test
    public void settings_returnsInstance() {
        Truth.assertThat(TestApis.settings()).isNotNull();
    }

    @Test
    public void settings_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.settings()).isEqualTo(TestApis.settings());
    }

    @Test
    public void systemProperties_returnsInstance() {
        Truth.assertThat(TestApis.systemProperties()).isNotNull();
    }

    @Test
    public void systemProperties_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.systemProperties()).isEqualTo(TestApis.systemProperties());
    }

    @Test
    public void activities_returnsInstance() {
        Truth.assertThat(TestApis.activities()).isNotNull();
    }

    @Test
    public void activities_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.activities()).isEqualTo(TestApis.activities());
    }

    @Test
    public void notifications_returnsInstance() {
        Truth.assertThat(TestApis.notifications()).isNotNull();
    }

    @Test
    public void notifications_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.notifications()).isEqualTo(TestApis.notifications());
    }

    @Test
    public void device_returnsInstance() {
        Truth.assertThat(TestApis.device()).isNotNull();
    }

    @Test
    public void device_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.device()).isEqualTo(TestApis.device());
    }

    @Test
    public void location_returnsInstance() {
        Truth.assertThat(TestApis.location()).isNotNull();
    }

    @Test
    public void location_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.location()).isEqualTo(TestApis.location());
    }

    @Test
    public void accessibility_returnsInstance() {
        Truth.assertThat(TestApis.accessibility()).isNotNull();
    }

    @Test
    public void accessibility_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.accessibility()).isEqualTo(TestApis.accessibility());
    }

    @Test
    public void bluetooth_returnsInstance() {
        Truth.assertThat(TestApis.bluetooth()).isNotNull();
    }

    @Test
    public void bluetooth_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.bluetooth()).isEqualTo(TestApis.bluetooth());
    }

    @Test
    public void inputMethods_returnsInstance() {
        Truth.assertThat(TestApis.inputMethods()).isNotNull();
    }

    @Test
    public void inputMethods_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.inputMethods()).isEqualTo(TestApis.inputMethods());
    }

    @Test
    public void instrumentation_returnsInstance() {
        Truth.assertThat(TestApis.instrumentation()).isNotNull();
    }

    @Test
    public void instrumentation_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.instrumentation()).isEqualTo(TestApis.instrumentation());
    }

    @Test
    public void roles_returnsInstance() {
        Truth.assertThat(TestApis.roles()).isNotNull();
    }

    @Test
    public void roles_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.roles()).isEqualTo(TestApis.roles());
    }

    @Test
    public void accounts_returnsInstance() {
        Truth.assertThat(TestApis.accounts()).isNotNull();
    }

    @Test
    public void accounts_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.accounts()).isEqualTo(TestApis.accounts());
    }

    @Test
    public void ui_returnsInstance() {
        Truth.assertThat(TestApis.ui()).isNotNull();
    }

    @Test
    public void ui_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.ui()).isEqualTo(TestApis.ui());
    }

    @Test
    public void flags_returnsInstance() {
        Truth.assertThat(TestApis.flags()).isNotNull();
    }

    @Test
    public void flags_multipleCalls_returnsSameInstance() {
        Truth.assertThat(TestApis.flags()).isEqualTo(TestApis.flags());
    }
}
