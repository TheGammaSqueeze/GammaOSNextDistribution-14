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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.V2_0.UserFlags;

import org.junit.Test;

public final class DebugUtilsUnitTest {

    @Test
    public void aidlConstantToString() {
        assertThat(DebugUtils.constantToString(StatusCode.class, StatusCode.OK)).isEqualTo("OK");
        assertThat(DebugUtils.constantToString(StatusCode.class, StatusCode.TRY_AGAIN))
                .isEqualTo("TRY_AGAIN");
        assertThat(DebugUtils.constantToString(StatusCode.class, StatusCode.NOT_AVAILABLE))
                .isEqualTo("NOT_AVAILABLE");
        assertThat(DebugUtils.constantToString(StatusCode.class, StatusCode.ACCESS_DENIED))
                .isEqualTo("ACCESS_DENIED");
        assertThat(DebugUtils.constantToString(StatusCode.class, StatusCode.INTERNAL_ERROR))
                .isEqualTo("INTERNAL_ERROR");
    }

    @Test
    public void aidlFlagsToString() {
        assertThat(DebugUtils.flagsToString(UserInfo.class, "USER_FLAG_", 0)).isEqualTo("0");
        assertThat(
                DebugUtils.flagsToString(UserInfo.class, "USER_FLAG_", UserInfo.USER_FLAG_SYSTEM))
                .isEqualTo("SYSTEM");
        assertThat(
                DebugUtils.flagsToString(
                        UserInfo.class, "USER_FLAG_",
                        UserInfo.USER_FLAG_SYSTEM | UserInfo.USER_FLAG_ADMIN))
                .isEqualTo("ADMIN|SYSTEM");
    }

    @Test
    public void hidlConstantToString() {
        assertThat(
                DebugUtils.constantToString(
                        android.hardware.automotive.vehicle.V2_0.StatusCode.class,
                        android.hardware.automotive.vehicle.V2_0.StatusCode.OK))
                .isEqualTo("OK");
        assertThat(
                DebugUtils.constantToString(
                        android.hardware.automotive.vehicle.V2_0.StatusCode.class,
                        android.hardware.automotive.vehicle.V2_0.StatusCode.TRY_AGAIN))
                .isEqualTo("TRY_AGAIN");
        assertThat(
                DebugUtils.constantToString(
                        android.hardware.automotive.vehicle.V2_0.StatusCode.class,
                        android.hardware.automotive.vehicle.V2_0.StatusCode.NOT_AVAILABLE))
                .isEqualTo("NOT_AVAILABLE");
        assertThat(
                DebugUtils.constantToString(
                        android.hardware.automotive.vehicle.V2_0.StatusCode.class,
                        android.hardware.automotive.vehicle.V2_0.StatusCode.ACCESS_DENIED))
                .isEqualTo("ACCESS_DENIED");
        assertThat(
                DebugUtils.constantToString(
                        android.hardware.automotive.vehicle.V2_0.StatusCode.class,
                        android.hardware.automotive.vehicle.V2_0.StatusCode.INTERNAL_ERROR))
                .isEqualTo("INTERNAL_ERROR");
    }

    @Test
    public void hidlFlagsToString() {
        assertThat(DebugUtils.flagsToString(UserFlags.class, "USER_FLAG_", 0)).isEqualTo("0");
        assertThat(
                DebugUtils.flagsToString(UserFlags.class, "", UserFlags.SYSTEM))
                .isEqualTo("SYSTEM");
        assertThat(
                DebugUtils.flagsToString(
                        UserFlags.class, "",
                        UserFlags.SYSTEM | UserFlags.ADMIN))
                .isEqualTo("ADMIN|SYSTEM");
    }
}
