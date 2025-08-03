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

package com.android.server.healthconnect.permission;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.time.Instant;

public class UserGrantTimeStateTest {

    @Test
    public void testVersion_getReturnsInitedVersion() {
        UserGrantTimeState state = new UserGrantTimeState(1);
        assertThat(state.getVersion()).isEqualTo(1);
    }

    @Test
    public void testSharedUser_setSharedUserTime_containsSharedUser() {
        String sharedName = "shared";
        UserGrantTimeState state = new UserGrantTimeState(1);
        state.setSharedUserGrantTime(sharedName, Instant.EPOCH);
        assertThat(state.containsSharedUserGrantTime(sharedName)).isTrue();
        assertThat(state.getSharedUserGrantTimes()).hasSize(1);
        assertThat(state.getSharedUserGrantTimes().get(sharedName)).isEqualTo(Instant.EPOCH);
    }

    @Test
    public void testPackage_setPackage_containsSetPackageTime() {
        String packageName = "package";
        UserGrantTimeState state = new UserGrantTimeState(1);
        state.setPackageGrantTime(packageName, Instant.EPOCH);
        assertThat(state.containsPackageGrantTime(packageName)).isTrue();
        assertThat(state.getPackageGrantTimes()).hasSize(1);
        assertThat(state.getPackageGrantTimes().get(packageName)).isEqualTo(Instant.EPOCH);
    }
}
