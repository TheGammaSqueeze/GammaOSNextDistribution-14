/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.accounts;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AccountsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @EnsureHasAccount
    public void allOnDevice_includesAccountOnInstrumentedUser() {
        assertThat(TestApis.accounts().allOnDevice())
                .contains(sDeviceState.account());
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasAccount(onUser = ADDITIONAL_USER)
    @Ignore("/b271540793")
    public void allOnDevice_includesAccountOnOtherUser() {
        assertThat(TestApis.accounts().all(sDeviceState.additionalUser()))
                .contains(sDeviceState.account());
    }
}
