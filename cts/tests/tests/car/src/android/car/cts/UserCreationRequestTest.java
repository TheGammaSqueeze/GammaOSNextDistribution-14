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

package android.car.cts;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.car.user.UserCreationRequest;

import org.junit.Test;

public final class UserCreationRequestTest extends AbstractExpectableTestCase {

    private static final String NAME = "testUser";

    @Test
    public void testUserCreationRequestName() {
        UserCreationRequest userCreationRequest = new UserCreationRequest.Builder().setName(NAME)
                .build();

        expectThat(userCreationRequest.getName()).isEqualTo(NAME);
        expectThat(userCreationRequest.isGuest()).isFalse();
        expectThat(userCreationRequest.isAdmin()).isFalse();
        expectThat(userCreationRequest.isEphemeral()).isFalse();
    }

    @Test
    public void testUserCreationRequestNameNull() {
        UserCreationRequest userCreationRequest = new UserCreationRequest.Builder().build();

        expectThat(userCreationRequest.getName()).isNull();
        expectThat(userCreationRequest.isGuest()).isFalse();
        expectThat(userCreationRequest.isAdmin()).isFalse();
        expectThat(userCreationRequest.isEphemeral()).isFalse();
    }

    @Test
    public void testUserCreationRequestGuest() {
        UserCreationRequest userCreationRequest = new UserCreationRequest.Builder()
                .setGuest().build();

        expectThat(userCreationRequest.getName()).isNull();
        expectThat(userCreationRequest.isGuest()).isTrue();
        expectThat(userCreationRequest.isAdmin()).isFalse();
        expectThat(userCreationRequest.isEphemeral()).isFalse();
    }

    @Test
    public void testUserCreationRequestAdmin() {
        UserCreationRequest userCreationRequest = new UserCreationRequest.Builder().setAdmin()
                .build();

        expectThat(userCreationRequest.getName()).isNull();
        expectThat(userCreationRequest.isGuest()).isFalse();
        expectThat(userCreationRequest.isAdmin()).isTrue();
        expectThat(userCreationRequest.isEphemeral()).isFalse();
    }

    @Test
    public void testUserCreationRequestGuestUser() {
        UserCreationRequest userCreationRequest = new UserCreationRequest.Builder().setEphemeral()
                .build();

        expectThat(userCreationRequest.getName()).isNull();
        expectThat(userCreationRequest.isGuest()).isFalse();
        expectThat(userCreationRequest.isAdmin()).isFalse();
        expectThat(userCreationRequest.isEphemeral()).isTrue();
    }

    @Test
    public void testUserCreationRequestGuestAndAdmin() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserCreationRequest.Builder().setGuest().setAdmin().build());
    }

    @Test
    public void testToString() {
        UserCreationRequest userCreationRequest = new UserCreationRequest.Builder().setName(
                NAME).setGuest().setEphemeral().build();

        expectWithMessage("userCreationRequest.toString()").that(
                userCreationRequest.toString()).containsMatch("name.*=.*testUser");
        expectWithMessage("userCreationRequest.toString()").that(
                userCreationRequest.toString()).containsMatch("guest.*=.*true");
        expectWithMessage("userCreationRequest.toString()").that(
                userCreationRequest.toString()).containsMatch("ephemeral.*=.*true");
    }
}
