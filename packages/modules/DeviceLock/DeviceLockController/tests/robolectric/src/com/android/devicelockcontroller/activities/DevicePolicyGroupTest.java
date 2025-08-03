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

package com.android.devicelockcontroller.activities;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DevicePolicyGroupTest {

    @Test
    public void builder_buildWithoutParameters_usesDefaultValues() {
        DevicePolicyGroup group = new DevicePolicyGroup.Builder().build();
        assertThat(group.getGroupTitleTextId()).isEqualTo(0);
        assertThat(group.getDevicePolicyList()).hasSize(0);
    }

    @Test
    public void builder_buildWithParameters_usesGivenParameters() {
        DevicePolicyGroup group = new DevicePolicyGroup.Builder()
                .setTitleTextId(0)
                .addDevicePolicy(1, 2)
                .build();
        assertThat(group.getGroupTitleTextId()).isEqualTo(0);
        assertThat(group.getDevicePolicyList()).hasSize(1);
        assertThat(group.getDevicePolicyList().get(0)).isEqualTo(new DevicePolicy(1, 2));
    }

    @Test
    public void equals_withDifferentGroupTitleTextId_returnsFalse() {
        DevicePolicyGroup g1 = new DevicePolicyGroup.Builder().setTitleTextId(0).build();
        DevicePolicyGroup g2 = new DevicePolicyGroup.Builder().setTitleTextId(1).build();

        assertThat(g1.equals(g2)).isFalse();
    }

    @Test
    public void equals_withSameGroupTitleTextId_withEmptyDevicePolicyList_returnsTrue() {
        DevicePolicyGroup g1 = new DevicePolicyGroup.Builder().setTitleTextId(0).build();
        DevicePolicyGroup g2 = new DevicePolicyGroup.Builder().setTitleTextId(0).build();

        assertThat(g1.equals(g2)).isTrue();
    }

    @Test
    public void equals_withSameGroupTitleTextId_withSameDevicePolicyList_returnsTrue() {
        DevicePolicyGroup g1 = new DevicePolicyGroup.Builder()
                .setTitleTextId(0)
                .addDevicePolicy(1, 2)
                .build();
        DevicePolicyGroup g2 = new DevicePolicyGroup.Builder()
                .setTitleTextId(0)
                .addDevicePolicy(1, 2)
                .build();

        assertThat(g1.equals(g2)).isTrue();
    }

    @Test
    public void equals_withSameGroupTitleTextId_withDifferentDevicePolicyList_returnsFalse() {
        DevicePolicyGroup g1 = new DevicePolicyGroup.Builder()
                .setTitleTextId(0)
                .addDevicePolicy(1, 2)
                .build();
        DevicePolicyGroup g2 = new DevicePolicyGroup.Builder()
                .setTitleTextId(0)
                .addDevicePolicy(3, 4)
                .build();

        assertThat(g1.equals(g2)).isFalse();
    }
}
