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
import android.car.user.UserSwitchRequest;
import android.os.UserHandle;

import org.junit.Test;

public final class UserSwitchRequestTest extends AbstractExpectableTestCase {

    private static final UserHandle USER_HANDLE = UserHandle.of(108);

    @Test
    public void testUserSwitchRequestUserhandle() {
        UserSwitchRequest userSwitchRequest = new UserSwitchRequest.Builder(USER_HANDLE).build();

        expectThat(userSwitchRequest.getUserHandle()).isEqualTo(USER_HANDLE);
    }

    @Test
    public void testUserSwitchRequestUserhandleNull() {
        assertThrows(NullPointerException.class, () -> new UserSwitchRequest.Builder(null));
    }
}
