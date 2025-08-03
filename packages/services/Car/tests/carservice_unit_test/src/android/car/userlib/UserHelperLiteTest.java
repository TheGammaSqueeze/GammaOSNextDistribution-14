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

package android.car.userlib;

import static com.google.common.truth.Truth.assertThat;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.internal.common.UserHelperLite;

import org.junit.Test;

public final class UserHelperLiteTest extends AbstractExtendedMockitoTestCase {

    public UserHelperLiteTest() {
        super(NO_LOG_TAGS);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(UserManager.class);
    }

    @Test
    public void testSafeName_nullCheck() {
        assertThat(UserHelperLite.safeName(null)).isNull();
    }

    @Test
    public void testSafeName() {
        String safe = UserHelperLite.safeName("UnsafeIam");

        assertThat(safe).isNotNull();
        assertThat(safe).doesNotContain("UnsafeIAm");
    }

    @Test
    public void testIsHeadlessSystemUser_system_headlessMode() {
        mockIsHeadlessSystemUserMode(true);

        assertThat(UserHelperLite.isHeadlessSystemUser(UserHandle.USER_SYSTEM)).isTrue();
    }

    @Test
    public void testIsHeadlessSystemUser_system_nonHeadlessMode() {
        mockIsHeadlessSystemUserMode(false);

        assertThat(UserHelperLite.isHeadlessSystemUser(UserHandle.USER_SYSTEM)).isFalse();
    }

    @Test
    public void testIsHeadlessSystemUser_nonSystem_headlessMode() {
        mockIsHeadlessSystemUserMode(true);

        assertThat(UserHelperLite.isHeadlessSystemUser(10)).isFalse();
    }

    @Test
    public void testIsHeadlessSystemUser_nonSystem_nonHeadlessMode() {
        mockIsHeadlessSystemUserMode(false);

        assertThat(UserHelperLite.isHeadlessSystemUser(10)).isFalse();
    }
}
