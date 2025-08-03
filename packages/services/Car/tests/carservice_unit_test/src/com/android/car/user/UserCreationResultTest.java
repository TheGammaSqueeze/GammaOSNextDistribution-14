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
package com.android.car.user;

import static android.car.user.UserCreationResult.STATUS_ANDROID_FAILURE;
import static android.car.user.UserCreationResult.STATUS_SUCCESSFUL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.car.user.UserCreationResult;
import android.os.UserHandle;

import org.junit.Test;

public final class UserCreationResultTest extends AbstractExpectableTestCase {

    @Test
    public void testConstructor_invalidStatus() {
        assertThrows(IllegalArgumentException.class, ()-> new UserCreationResult(42));
    }

    @Test
    public void testConstructor_statusOnly() {
        UserCreationResult result = new UserCreationResult(STATUS_SUCCESSFUL);

        assertThat(result.getStatus()).isEqualTo(STATUS_SUCCESSFUL);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();
    }

    @Test
    public void testConstructor_statusAndUserOnly() {
        UserHandle user = UserHandle.of(108);

        UserCreationResult result = new UserCreationResult(STATUS_SUCCESSFUL, user);

        assertThat(result.getStatus()).isEqualTo(STATUS_SUCCESSFUL);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();
    }

    @Test
    public void testToString() {
        UserHandle user = UserHandle.of(108);

        UserCreationResult result = new UserCreationResult(STATUS_ANDROID_FAILURE, 6, user,
                "string1",
                "string2");
        UserCreationResult nullResult = new UserCreationResult(STATUS_ANDROID_FAILURE, null, null,
                null, null);
        String msg = result.toString();
        String nullMessage = nullResult.toString();

        expectWithMessage("status = STATUS_ANDROID_FAILURE").that(
                msg.contains("status = STATUS_ANDROID_FAILURE")).isTrue();
        expectWithMessage("androidFailureStatus = 6 (ERROR_MAX_USERS)").that(
                msg.contains("androidFailureStatus = 6 (ERROR_MAX_USERS)")).isTrue();
        expectWithMessage("user = UserHandle{108}").that(
                msg.contains("user = UserHandle{108}")).isTrue();
        expectWithMessage("errorMessage = string1").that(
                msg.contains("errorMessage = string1")).isTrue();
        expectWithMessage("internalErrorMessage = string2").that(
                msg.contains("internalErrorMessage = string2")).isTrue();

        expectWithMessage("status = STATUS_ANDROID_FAILURE").that(
                nullMessage.contains("status = STATUS_ANDROID_FAILURE")).isTrue();
        expectWithMessage("androidFailureStatus = null").that(
                nullMessage.contains("androidFailureStatus = null")).isTrue();
        expectWithMessage("user = null").that(nullMessage.contains("user = null")).isTrue();
        expectWithMessage("errorMessage = null").that(
                nullMessage.contains("errorMessage = null")).isTrue();
        expectWithMessage("internalErrorMessage = null").that(
                nullMessage.contains("internalErrorMessage = null")).isTrue();

    }
}
