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

package com.android.intentresolver

import android.os.UserHandle

import com.google.common.truth.Truth.assertThat

import org.junit.Test

class AnnotatedUserHandlesTest {

    @Test
    fun testBasicProperties() {  // Fields that are reflected back w/o logic.
        val info = AnnotatedUserHandles.newBuilder()
            .setUserIdOfCallingApp(42)
            .setUserHandleSharesheetLaunchedAs(UserHandle.of(116))
            .setPersonalProfileUserHandle(UserHandle.of(117))
            .setWorkProfileUserHandle(UserHandle.of(118))
            .setCloneProfileUserHandle(UserHandle.of(119))
            .build()

        assertThat(info.userIdOfCallingApp).isEqualTo(42)
        assertThat(info.userHandleSharesheetLaunchedAs.identifier).isEqualTo(116)
        assertThat(info.personalProfileUserHandle.identifier).isEqualTo(117)
        assertThat(info.workProfileUserHandle?.identifier).isEqualTo(118)
        assertThat(info.cloneProfileUserHandle?.identifier).isEqualTo(119)
    }

    @Test
    fun testWorkTabInitiallySelectedWhenLaunchedFromWorkProfile() {
        val info = AnnotatedUserHandles.newBuilder()
            .setUserIdOfCallingApp(42)
            .setPersonalProfileUserHandle(UserHandle.of(101))
            .setWorkProfileUserHandle(UserHandle.of(202))
            .setUserHandleSharesheetLaunchedAs(UserHandle.of(202))
            .build()

        assertThat(info.tabOwnerUserHandleForLaunch.identifier).isEqualTo(202)
    }

    @Test
    fun testPersonalTabInitiallySelectedWhenLaunchedFromPersonalProfile() {
        val info = AnnotatedUserHandles.newBuilder()
            .setUserIdOfCallingApp(42)
            .setPersonalProfileUserHandle(UserHandle.of(101))
            .setWorkProfileUserHandle(UserHandle.of(202))
            .setUserHandleSharesheetLaunchedAs(UserHandle.of(101))
            .build()

        assertThat(info.tabOwnerUserHandleForLaunch.identifier).isEqualTo(101)
    }

    @Test
    fun testPersonalTabInitiallySelectedWhenLaunchedFromOtherProfile() {
        val info = AnnotatedUserHandles.newBuilder()
            .setUserIdOfCallingApp(42)
            .setPersonalProfileUserHandle(UserHandle.of(101))
            .setWorkProfileUserHandle(UserHandle.of(202))
            .setUserHandleSharesheetLaunchedAs(UserHandle.of(303))
            .build()

        assertThat(info.tabOwnerUserHandleForLaunch.identifier).isEqualTo(101)
    }
}
