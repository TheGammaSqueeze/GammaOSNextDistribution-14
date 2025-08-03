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

package com.android.bedstead.remoteaccountauthenticator;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.nene.accounts.AccountReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class RemoteAccountAuthenticatorTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void install_appIsInstalled() {
        try (RemoteAccountAuthenticator authenticator = RemoteAccountAuthenticator.install()) {
            assertThat(authenticator.testApp().pkg().installedOnUser()).isTrue();
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_differentUser_appIsInstalled() {
        try (RemoteAccountAuthenticator authenticator =
                     RemoteAccountAuthenticator.install(sDeviceState.additionalUser())) {
            assertThat(authenticator.testApp().pkg().installedOnUser(
                    sDeviceState.additionalUser())).isTrue();
        }
    }

    @Test
    public void autoclose_appIsUninstalled() {
        RemoteAccountAuthenticator authenticator = RemoteAccountAuthenticator.install();
        try (authenticator) {
            // Just using autoclose
        }

        assertThat(authenticator.testApp().pkg().installedOnUser()).isFalse();
    }

    @Test
    public void addAccount_accountIsAdded() throws Exception {
        try (RemoteAccountAuthenticator authenticator =
                     RemoteAccountAuthenticator.install()) {
            AccountReference account = authenticator.addAccount().add();

            assertThat(authenticator.allAccounts()).contains(account);
        }
    }

    @Test
    public void remove_accountIsRemoved() throws Exception {
        try (RemoteAccountAuthenticator authenticator =
                     RemoteAccountAuthenticator.install()) {
            AccountReference account = authenticator.addAccount().add();
            account.remove();

            assertThat(authenticator.allAccounts()).doesNotContain(account);
        }
    }

    @Test
    public void accountAutoclose_accountIsRemoved() throws Exception {
        try (RemoteAccountAuthenticator authenticator =
                     RemoteAccountAuthenticator.install()) {
            AccountReference account = authenticator.addAccount().add();
            try (account) {
                // Just using autoclose
            }

            assertThat(authenticator.allAccounts()).doesNotContain(account);
        }
    }
}
